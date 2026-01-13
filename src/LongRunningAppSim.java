 

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class LongRunningAppSim {

  private static final Random RAND = new Random(202501);
  private static final ReferenceQueue<Payload> REF_Q = new ReferenceQueue<>();

  public static void main(String[] args) {
    int runSeconds = 24 * 60 * 60;
    for (String s : args) {
      if (s.startsWith("--seconds=")) {
        runSeconds = Integer.parseInt(s.substring(s.indexOf('=') + 1));
      } else if ("--short".equals(s)) {
        runSeconds = 2;
      } else if (s.matches("\\d+")) {
        runSeconds = Integer.parseInt(s);
      }
    }
    System.out.println("=== LongRunningAppSim start (" + runSeconds + "s) ===");
    runSimulation(runSeconds);
    System.out.println("=== LongRunningAppSim end ===");
  }

  private static void runSimulation(int runSeconds) {
    long max = Runtime.getRuntime().maxMemory();
    long targetBytes = Math.max(256L * 1024 * 1024, (long)(max * 0.6));
    long deadline = System.currentTimeMillis() + runSeconds * 1000L;

    List<Payload> retained = new ArrayList<>();
    List<StickyEntry> sticky = new ArrayList<>(); // 长期保留，促发 OOM
    List<WeakReference<Payload>> watchers = new ArrayList<>();
    Map<String, String> lruStrings = new HashMap<>();
    Deque<int[]> rollingInts = new ArrayDeque<>();
    IntegrityMonitor monitor = new IntegrityMonitor();
    long approxBytes = 0;
    int iter = 0;
    Object[] smallRing = new Object[1024];
    int smallIdx = 0;

    try {
      while (System.currentTimeMillis() < deadline) {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory();
        long total = rt.totalMemory();
        long maxHeap = rt.maxMemory();
        long used = total - free;
        double usage = used / (double)maxHeap;
        Payload payload = newPayload(iter);
        payload.touch();
        retained.add(payload);
        approxBytes += payload.footprint();
        watchers.add(new WeakReference<>(payload, REF_Q));
        monitor.track(payload);

        // 小对象分配/驻留，增加碎片化压力
        smallRing[smallIdx & (smallRing.length - 1)] = newSmallObject(iter);
        smallIdx++;

        // 额外长期保留对象，不释放，模拟慢性泄漏/堆持续增长（强/弱策略分离）
        if ((iter & 31) == 0) {
          boolean canGrowSticky = usage < 0.95;
          Payload stickyPayload = newStickyPayload(iter);
          stickyPayload.touch();
          boolean strongHold = RAND.nextBoolean();

          if (strongHold && !canGrowSticky && !sticky.isEmpty()) {
            // 若已逼近上限且本次想要强引用，则弹出旧 sticky，避免立即 OOM
            StickyEntry evicted = sticky.remove(0);
            approxBytes = Math.max(0, approxBytes - evicted.footprint);
          }

          if (strongHold && (canGrowSticky || !sticky.isEmpty())) {
            long cs = stickyPayload.checksum();
            sticky.add(new StickyEntry(stickyPayload, cs)); // 强引用保留 + 缓存 checksum
            approxBytes += stickyPayload.footprint();
          }

          // 无论强弱，都放入弱引用监视，便于检测意外 GC
          watchers.add(new WeakReference<>(stickyPayload, REF_Q));
          monitor.track(stickyPayload);

          if (!strongHold) {
            // 仅弱持有：粗略计入占用（可能被回收，不调整 approxBytes 后续）
            approxBytes += stickyPayload.footprint();
          }
        }

        if ((iter & 63) == 0) {
          simulateStringWork(iter, lruStrings);
          maintainInts(iter, rollingInts);
        }

        if ((iter & 255) == 0) {
          pollQueue(watchers);
          System.out.printf(
              Locale.ROOT,
              "[iter=%d] approxRetained=%.1f MB, strong=%d, weak=%d%n", iter,
              approxBytes / (1024.0 * 1024.0), retained.size(),
              watchers.size());
          if (retained.size() > 4000) {
            int drop = retained.size() / 4;
            for (int i = 0; i < drop; i++)
              retained.remove(0);
            approxBytes = Math.max(0, approxBytes - drop * 16_384);
          }
        }

        if ((iter & 4095) == 0) {
          useObjects(retained, lruStrings, rollingInts, monitor);
          monitor.verify();
          verifySticky(sticky, monitor);
        }

        iter++;
      }
    } finally {
      System.out.println("Simulation reached limit, releasing resources...");
      retained.clear();
      sticky.clear();
      watchers.clear();
      lruStrings.clear();
      rollingInts.clear();
      monitor.clear();
      System.gc();
    }
  }

  private static Payload newPayload(int seq) {
    switch (RAND.nextInt(8)) {
    case 0:
      return new BytePayload(32 * 1024 + RAND.nextInt(64 * 1024),
                             (byte)(seq & 0xFF));
    case 1:
      return new IntPayload(4 * 1024 + RAND.nextInt(32 * 1024), seq);
    case 2:
      return new StringPayload(seq, 128 + RAND.nextInt(512));
    case 3:
      return new GraphPayload(seq, 8 + RAND.nextInt(48));
    case 4:
      return new MapPayload(seq, 6 + RAND.nextInt(18));
    case 5:
      return new SessionPayload(seq, 3 + RAND.nextInt(6));
    case 6:
      return new BufferPayload(8 * 1024 + RAND.nextInt(8 * 1024), seq);
    default:
      return new MessageBatchPayload(seq, 4 + RAND.nextInt(8));
    }
  }

  private static Payload newStickyPayload(int seq) {
    switch (RAND.nextInt(6)) {
    case 0:
      return new BytePayload(2 * 1024 * 1024 + RAND.nextInt(2 * 1024 * 1024),
                             (byte)(seq & 0xFF));
    case 1:
      return new IntPayload(128 * 1024 + RAND.nextInt(128 * 1024), seq);
    case 2:
      return new StringPayload(seq, 1024 + RAND.nextInt(2048));
    case 3:
      return new MapPayload(seq, 64 + RAND.nextInt(96));
    case 4:
      return new SessionPayload(seq, 10 + RAND.nextInt(12));
    default:
      // 混合大/小对象：偶尔用小图或小 byte[] 让压力粒度更丰富，并引入层级关系
      if (RAND.nextInt(3) == 0) {
        List<Payload> children = new ArrayList<>();
        children.add(new BytePayload(8 * 1024 + RAND.nextInt(32 * 1024),
                                     (byte)(seq & 0x7F)));
        children.add(new GraphPayload(seq, 8 + RAND.nextInt(24)));
        children.add(new StringPayload(seq, 256 + RAND.nextInt(512)));
        children.add(new MessageBatchPayload(seq, 8 + RAND.nextInt(12)));
        return new CompositePayload(children);
      }
      if (RAND.nextBoolean()) {
        return new BufferPayload(64 * 1024 + RAND.nextInt(128 * 1024), seq);
      } else if (RAND.nextBoolean()) {
        return new GraphPayload(seq, 8 + RAND.nextInt(24));
      } else {
        return new BytePayload(4 * 1024 + RAND.nextInt(16 * 1024),
                               (byte)(seq & 0x7F));
      }
    }
  }

  private static Object newSmallObject(int seq) {
    switch (seq & 3) {
    case 0:
      return new byte[512 + RAND.nextInt(512)];
    case 1:
      return new TinyPojo("tiny-" + seq, seq ^ 0x5A5A5A);
    case 2:
      return LocalDateTime.ofEpochSecond(1_700_000_000L + (seq & 1023), 0,
                                         ZoneOffset.UTC);
    default:
      return deterministicUuid("ring-" + seq);
    }
  }

  private static final class TinyPojo {
    final String name;
    final int code;
    final long created;

    TinyPojo(String name, int code) {
      this.name = name;
      this.code = code;
      this.created = System.nanoTime();
    }
  }

  private static UUID deterministicUuid(String key) {
    long hi = 0x9E3779B97F4A7C15L;
    long lo = 0xC0FEBABEDEADBEEFL;
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      hi ^= (long)c * 0x5bd1e995L;
      hi = Long.rotateLeft(hi, 5) + 0x27d4eb2fL;
      lo ^= (long)c * 0x27d4eb2fL;
      lo = Long.rotateLeft(lo, 7) + 0x165667b1L;
    }
    return new UUID(hi, lo);
  }

  private static void simulateStringWork(int iter, Map<String, String> cache) {
    String base = "WORK-" + iter;
    StringBuilder sb = new StringBuilder(base);
    for (int i = 0; i < 5; i++) {
      sb.append('#').append(Integer.toHexString(RAND.nextInt()));
    }
    String value = sb.toString();
    cache.put(base, value);
    // 实际使用字符串：计算 hash / 子串，避免仅存储
    int hash = value.hashCode();
    if ((hash & 3) == 0 && value.length() > 8)
      cache.put(base + "-slice", value.substring(0, 8));
    if (cache.size() > 2048) {
      cache.remove("WORK-" + (iter - 2048));
    }
  }

  private static void maintainInts(int iter, Deque<int[]> ints) {
    int[] arr = new int[256];
    int sum = 0;
    for (int i = 0; i < arr.length; i++) {
      arr[i] = iter + i;
      sum += arr[i];
    }
    ints.addLast(arr);
    if ((sum & 1) == 0 && !ints.isEmpty()) {
      int[] peek = ints.peekFirst();
      if (peek != null)
        sum += peek[0];
    }
    if (ints.size() > 512)
      ints.removeFirst();
  }

  private static void pollQueue(List<WeakReference<Payload>> watchers) {
    Reference<? extends Payload> ref;
    while ((ref = REF_Q.poll()) != null) {
      ref.clear();
    }
    for (int i = watchers.size() - 1; i >= 0; i--) {
      if (watchers.get(i).get() == null)
        watchers.remove(i);
    }
  }

  private static void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void useObjects(List<Payload> retained,
                                 Map<String, String> cache, Deque<int[]> ints,
                                 IntegrityMonitor monitor) {
    if (!retained.isEmpty()) {
      Payload p = retained.get(RAND.nextInt(retained.size()));
      p.touch();
      if (p instanceof StringPayload) {
        StringPayload sp = (StringPayload)p;
        cache.put("touch-" + sp.marker(), sp.headSlice());
      } else if (p instanceof MapPayload) {
        MapPayload mp = (MapPayload)p;
        cache.put("map-anchor", mp.anchorKey());
        cache.put("map-anchor-val", Long.toString(mp.anchorValue()));
      } else if (p instanceof SessionPayload) {
        SessionPayload sp = (SessionPayload)p;
        cache.put("session-user", sp.userTag());
        cache.put("session-count", Integer.toString(sp.activityCount()));
      } else if (p instanceof MessageBatchPayload) {
        MessageBatchPayload mbp = (MessageBatchPayload)p;
        cache.put("msg-topic", mbp.topic());
        cache.put("msg-size", Integer.toString(mbp.batchSize()));
      } else if (p instanceof BufferPayload) {
        BufferPayload bp = (BufferPayload)p;
        cache.put("buf-cap", Integer.toString(bp.capacity()));
        cache.put("buf-ints", Integer.toString(bp.intCount()));
      }
      monitor.update(p); // 对采样对象的任何修改/访问后刷新校验和
    }
    if (!ints.isEmpty()) {
      int[] arr = ints.peekLast();
      if (arr != null)
        cache.put("int-head", "" + arr[0]);
    }
  }

  private interface Payload {
    void touch();
    long footprint();
    long checksum();
  }

  private static final class BytePayload implements Payload {
    private final byte[] data;
    private final byte pattern;
    BytePayload(int size, byte pattern) {
      this.data = new byte[size];
      this.pattern = pattern;
      java.util.Arrays.fill(this.data, pattern);
    }
    @Override
    public void touch() {
      if (data.length == 0 || data[0] != pattern ||
          data[data.length - 1] != pattern)
        throw new AssertionError("BytePayload corrupted");
    }
    @Override
    public long footprint() {
      return data.length;
    }

    @Override
    public long checksum() {
      int mid = data.length / 2;
      int q = data.length / 4;
      int last = data.length - 1;
      return ((long)data.length << 32) ^ ((long)(data[0] & 0xFF) << 24) ^
          ((long)(data[mid] & 0xFF) << 16) ^ ((long)(data[q] & 0xFF) << 8) ^
          (data[last] & 0xFF) ^ pattern;
    }
  }

  private static final class IntPayload implements Payload {
    private final int[] ints;
    private final int base;
    IntPayload(int len, int base) {
      this.ints = new int[len];
      this.base = base;
      for (int i = 0; i < len; i++)
        this.ints[i] = base + i;
    }
    @Override
    public void touch() {
      int idx = RAND.nextInt(ints.length);
      if (ints[idx] != base + idx)
        throw new AssertionError("IntPayload mismatch");
    }
    @Override
    public long footprint() {
      return ints.length * Integer.BYTES;
    }

    @Override
    public long checksum() {
      long hash = ints.length;
      int step = Math.max(1, ints.length / 8);
      for (int i = 0; i < ints.length; i += step) {
        hash = (hash * 1315423911L) ^ ints[i];
      }
      return hash ^ base;
    }
  }

  private static final class StringPayload implements Payload {
    private final String marker;
    private final String value;
    StringPayload(int seq, int len) {
      this.marker = "S" + seq;
      char[] chars = new char[len];
      for (int i = 0; i < len; i++)
        chars[i] = (char)('a' + RAND.nextInt(26));
      this.value = marker + new String(chars);
    }
    @Override
    public void touch() {
      if (!value.startsWith(marker))
        throw new AssertionError("StringPayload lost marker");
    }
    @Override
    public long footprint() {
      return value.length() * 2L + 40;
    }

    @Override
    public long checksum() {
      return value.hashCode() ^ marker.hashCode();
    }

    String marker() { return marker; }
    String headSlice() {
      return value.length() > 4 ? value.substring(0, 4) : value;
    }
  }

  private static final class GraphPayload implements Payload {
    private final Node head;
    private final int depth;
    GraphPayload(int seq, int depth) {
      this.depth = depth;
      this.head = build(depth, seq);
    }
    private static Node build(int depth, int seed) {
      Node curr = null;
      for (int i = 0; i < depth; i++)
        curr = new Node(seed + i, curr);
      return curr;
    }
    @Override
    public void touch() {
      Node curr = head;
      int count = 0;
      while (curr != null) {
        curr = curr.next;
        count++;
      }
      if (count != depth)
        throw new AssertionError("GraphPayload depth mismatch");
    }
    @Override
    public long footprint() {
      return depth * 48L;
    }

    @Override
    public long checksum() {
      long hash = depth;
      Node curr = head;
      while (curr != null) {
        hash = (hash * 1664525L) + curr.token;
        curr = curr.next;
      }
      return hash;
    }

    private static final class Node {
      final int token;
      final Node next;
      Node(int token, Node next) {
        this.token = token;
        this.next = next;
      }
    }
  }

  private static final class CompositePayload implements Payload {
    private final List<Payload> children;
    CompositePayload(List<Payload> children) { this.children = children; }

    @Override
    public void touch() {
      for (Payload p : children)
        p.touch();
    }

    @Override
    public long footprint() {
      long total = 0;
      for (Payload p : children)
        total += p.footprint();
      return total + 64; // 粗略容器开销
    }

    @Override
    public long checksum() {
      long hash = 0xC0FFEEL;
      for (Payload p : children)
        hash = (hash * 1315423911L) ^ p.checksum();
      return hash;
    }

    @Override
    public String toString() {
      return "CompositePayload[size=" + children.size() + "]";
    }
  }

  private static final class MapPayload implements Payload {
    private final LinkedHashMap<String, Long> metrics;
    private final int expectedSize;
    private final long seed;
    private final String anchorKey;

    MapPayload(int seq, int size) {
      this.seed = seq * 2654435761L;
      this.expectedSize = size;
      this.metrics = new LinkedHashMap<>(size * 2);
      this.anchorKey = "m" + seq + "-0";
      for (int i = 0; i < size; i++) {
        long value = seed ^ (0x9E3779B97F4A7C15L * i);
        metrics.put("m" + seq + "-" + i, value);
      }
    }

    @Override
    public void touch() {
      if (metrics.size() != expectedSize)
        throw new AssertionError("MapPayload size drift");
      Long anchor = metrics.get(anchorKey);
      long expected = seed;
      if (anchor == null || anchor != expected)
        throw new AssertionError("MapPayload anchor missing");
    }

    @Override
    public long footprint() {
      return expectedSize * 96L + 128;
    }

    @Override
    public long checksum() {
      long hash = seed ^ expectedSize;
      for (Map.Entry<String, Long> entry : metrics.entrySet()) {
        hash = (hash * 1315423911L) ^ entry.getKey().hashCode();
        hash = (hash * 1315423911L) ^ entry.getValue();
      }
      return hash;
    }

    String anchorKey() { return anchorKey; }
    long anchorValue() { return seed; }
    int size() { return expectedSize; }

    @Override
    public String toString() {
      return "MapPayload[size=" + expectedSize + "]";
    }
  }

  private static final class SessionPayload implements Payload {
    private final UserSession session;
    private final int expectedCount;
    private final int baseCode;

    SessionPayload(int seq, int activityCount) {
      this.expectedCount = activityCount;
      this.baseCode = seq;
      long created = 1_700_000_000L + seq;
      UUID id = deterministicUuid("sess-" + seq);
      List<Activity> activities = new ArrayList<>(activityCount);
      for (int i = 0; i < activityCount; i++) {
        activities.add(new Activity("evt-" + (seq & 0xFF) + "-" + i,
                                    baseCode + i, created + i * 5L));
      }
      this.session = new UserSession(id, "user-" + (seq & 0xFFFF), created,
                                     activities, (seq & 1) == 0);
    }

    @Override
    public void touch() {
      if (!session.user.startsWith("user-") ||
          session.activities.size() != expectedCount)
        throw new AssertionError("SessionPayload corrupted header");
      Activity first = session.activities.get(0);
      if (first.code != baseCode || !first.type.endsWith("-0"))
        throw new AssertionError("SessionPayload activity drift");
    }

    @Override
    public long footprint() {
      return 192L + expectedCount * 64L + session.user.length() * 2L;
    }

    @Override
    public long checksum() {
      long hash = session.id.getMostSignificantBits() ^
          session.id.getLeastSignificantBits();
      hash = mix(hash, session.user.hashCode());
      hash = mix(hash, session.createdEpochSeconds);
      hash = mix(hash, session.active ? 1 : 0);
      for (Activity a : session.activities) {
        hash = mix(hash, a.type.hashCode());
        hash = mix(hash, a.code);
        hash = mix(hash, a.at);
      }
      hash = mix(hash, expectedCount);
      return mix(hash, baseCode);
    }

    private static long mix(long acc, long value) {
      return (acc * 0x9E3779B97F4A7C15L) ^ value;
    }

    String userTag() { return session.user; }
    int activityCount() { return expectedCount; }

    @Override
    public String toString() {
      return "SessionPayload[user=" + session.user +
          ",activities=" + expectedCount + "]";
    }

    private static final class UserSession {
      final UUID id;
      final String user;
      final long createdEpochSeconds;
      final List<Activity> activities;
      final boolean active;
      UserSession(UUID id, String user, long createdEpochSeconds,
                  List<Activity> activities, boolean active) {
        this.id = id;
        this.user = user;
        this.createdEpochSeconds = createdEpochSeconds;
        this.activities = activities;
        this.active = active;
      }
    }

    private static final class Activity {
      final String type;
      final int code;
      final long at;
      Activity(String type, int code, long at) {
        this.type = type;
        this.code = code;
        this.at = at;
      }
    }
  }

  private static final class BufferPayload implements Payload {
    private final ByteBuffer buffer;
    private final int writtenInts;
    private final int salt;
    private final StringBuilder trail;
    private final int capacity;

    BufferPayload(int capacity, int salt) {
      this.capacity = capacity;
      this.salt = salt;
      this.buffer = ByteBuffer.allocate(capacity);
      this.writtenInts = Math.max(1, Math.min(capacity / Integer.BYTES, 256));
      for (int i = 0; i < writtenInts; i++)
        buffer.putInt(expectedValue(i));
      buffer.flip();
      this.trail = new StringBuilder("buf-")
                       .append(salt & 0xFF)
                       .append('-')
                       .append(writtenInts);
    }

    private int expectedValue(int idx) {
      return salt ^ (idx * 0x45D9F3B);
    }

    @Override
    public void touch() {
      ByteBuffer read = buffer.asReadOnlyBuffer();
      if (read.capacity() != capacity)
        throw new AssertionError("BufferPayload capacity mismatch");
      for (int i = 0; i < writtenInts; i++) {
        if (read.remaining() < Integer.BYTES)
          throw new AssertionError("BufferPayload truncated");
        int v = read.getInt();
        if (v != expectedValue(i))
          throw new AssertionError("BufferPayload data mismatch");
      }
    }

    @Override
    public long footprint() {
      return capacity + trail.length() * 2L + 64;
    }

    @Override
    public long checksum() {
      ByteBuffer read = buffer.asReadOnlyBuffer();
      long hash = capacity ^ writtenInts ^ salt;
      while (read.remaining() >= Integer.BYTES) {
        hash = (hash * 1664525L) ^ read.getInt();
      }
      return (hash * 1664525L) ^ trail.toString().hashCode();
    }

    int capacity() { return capacity; }
    int intCount() { return writtenInts; }

    @Override
    public String toString() {
      return "BufferPayload[cap=" + capacity + ",ints=" + writtenInts + "]";
    }
  }

  private static final class MessageBatchPayload implements Payload {
    private final ArrayDeque<Message> queue;
    private final String topic;
    private final int expectedCount;

    MessageBatchPayload(int seq, int count) {
      this.topic = "topic-" + (seq & 0xFF);
      this.expectedCount = count;
      this.queue = new ArrayDeque<>(count);
      for (int i = 0; i < count; i++) {
        queue.add(new Message(topic, seq + i, "body-" + (seq ^ i)));
      }
    }

    @Override
    public void touch() {
      if (queue.size() != expectedCount)
        throw new AssertionError("MessageBatch size mismatch");
      Message head = queue.peekFirst();
      Message tail = queue.peekLast();
      if (head == null || tail == null ||
          !Objects.equals(head.topic, topic) ||
          !Objects.equals(tail.topic, topic))
        throw new AssertionError("MessageBatch topic mismatch");
    }

    @Override
    public long footprint() {
      return expectedCount * 128L + topic.length() * 2L + 64;
    }

    @Override
    public long checksum() {
      long hash = topic.hashCode() ^ expectedCount;
      for (Message m : queue) {
        hash = (hash * 1315423911L) ^ m.body.hashCode();
        hash = (hash * 1315423911L) ^ m.code;
      }
      return hash;
    }

    String topic() { return topic; }
    int batchSize() { return expectedCount; }

    @Override
    public String toString() {
      return "MessageBatchPayload[topic=" + topic + ",size=" + expectedCount +
          "]";
    }

    private static final class Message {
      final String topic;
      final int code;
      final String body;
      Message(String topic, int code, String body) {
        this.topic = topic;
        this.code = code;
        this.body = body;
      }
    }
  }

  private static final class IntegrityMonitor {
    private static final class Record {
      final WeakReference<Payload> ref;
      long checksum;
      Record(Payload payload, long checksum) {
        this.ref = new WeakReference<>(payload);
        this.checksum = checksum;
      }
    }

    private final List<Record> records = new ArrayList<>();

    void track(Payload payload) {
      records.add(new Record(payload, payload.checksum()));
    }

    void update(Payload payload) {
      for (Record r : records) {
        Payload p = r.ref.get();
        if (p == payload) {
          r.checksum = payload.checksum();
          break;
        }
      }
    }

    void verify() {
      Runtime rt = Runtime.getRuntime();
      long free = rt.freeMemory();
      long total = rt.totalMemory();
      long max = rt.maxMemory();
      System.out.printf(Locale.ROOT,
                        "[IntegrityVerify] free=%.1fMB, total=%.1fMB, "
                            + "max=%.1fMB, tracked=%d%n",
                        free / (1024.0 * 1024.0), total / (1024.0 * 1024.0),
                        max / (1024.0 * 1024.0), records.size());
      for (int i = records.size() - 1; i >= 0; i--) {
        if (records.get(i).ref.get() == null)
          records.remove(i);
      }
      long expectedCombined = 0;
      long actualCombined = 0;
      for (Record r : records) {
        Payload payload = r.ref.get();
        if (payload == null) {
          continue; // 已被 GC
        }
        expectedCombined = foldChecksum(expectedCombined, r.checksum);
        long current = payload.checksum();
        actualCombined = foldChecksum(actualCombined, current);
        if (current != r.checksum) {
          System.err.println("[IntegrityMismatch] ref=" + payload +
                             " details=" + describePayload(payload) +
                             " expected=" + r.checksum +
                             " actual=" + current);
          throw new AssertionError("Payload mutated unexpectedly");
        }
      }
      if (expectedCombined != actualCombined) {
        System.err.println("[IntegrityMismatchAggregate] expectedCombined=" +
                           expectedCombined + " actualCombined=" +
                           actualCombined);
        throw new AssertionError("Aggregate checksum drift");
      }
    }

    void verifyOne(Payload payload) {
      for (Record r : records) {
        Payload p = r.ref.get();
        if (p == payload) {
          long current = payload.checksum();
          if (current != r.checksum) {
            System.err.println("[IntegrityMismatch] ref=" + payload +
                               " details=" + describePayload(payload) +
                               " expected=" + r.checksum +
                               " actual=" + current);
            throw new AssertionError("Payload mutated unexpectedly");
          }
          return;
        }
      }
      // 如果没有记录，补充一条，保证后续可监控
      track(payload);
    }

    void clear() { records.clear(); }

    private static long foldChecksum(long acc, long value) {
      return (acc * 0x9E3779B97F4A7C15L) ^ value;
    }
  }

  private static String describePayload(Payload p) {
    if (p instanceof BytePayload) {
      BytePayload bp = (BytePayload)p;
      return "BytePayload[len=" + bp.data.length + ",pattern=" + bp.pattern +
          "]";
    }
    if (p instanceof IntPayload) {
      IntPayload ip = (IntPayload)p;
      return "IntPayload[len=" + ip.ints.length + ",base=" + ip.base + "]";
    }
    if (p instanceof StringPayload) {
      StringPayload sp = (StringPayload)p;
      return "StringPayload[marker=" + sp.marker() +
          ",len=" + sp.value.length() + "]";
    }
    if (p instanceof GraphPayload) {
      GraphPayload gp = (GraphPayload)p;
      return "GraphPayload[depth=" + gp.depth + "]";
    }
    if (p instanceof MapPayload || p instanceof SessionPayload ||
        p instanceof BufferPayload || p instanceof MessageBatchPayload) {
      return p.toString();
    }
    if (p instanceof CompositePayload) {
      return p.toString();
    }
    return String.valueOf(p);
  }

  private static void verifySticky(List<StickyEntry> sticky, IntegrityMonitor monitor) {
    for (StickyEntry entry : sticky) {
      Payload p = entry.payload;
      p.touch();
      long current = p.checksum();
      if (current != entry.footprintChecksum) {
        System.err.println("[StickyMismatch] ref=" + p + " details=" + describePayload(p)
                           + " expected=" + entry.footprintChecksum
                           + " actual=" + current);
        throw new AssertionError("Sticky payload mutated unexpectedly");
      }
      // 也让 monitor 跟踪，以便弱引用 GC 可见
      monitor.verifyOne(p);
    }
  }

  private static final class StickyEntry {
    final Payload payload;
    final long footprint;
    long footprintChecksum;

    StickyEntry(Payload payload, long checksum) {
      this.payload = payload;
      this.footprint = payload.footprint();
      this.footprintChecksum = checksum;
    }
  }
}
