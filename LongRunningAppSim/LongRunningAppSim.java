import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class LongRunningAppSim {

  private static final Random RAND = new Random(202501);
  private static final ReferenceQueue<Payload> REF_Q = new ReferenceQueue<>();

  public static void main(String[] args) {
    int runSeconds = args.length > 0 ? Integer.parseInt(args[0]) : 20;
    System.out.println("=== LongRunningAppSim start (" + runSeconds + "s) ===");
    runSimulation(runSeconds);
    System.out.println("=== LongRunningAppSim end ===");
  }

  private static void runSimulation(int runSeconds) {
    long max = Runtime.getRuntime().maxMemory();
    long targetBytes = Math.max(256L * 1024 * 1024, (long)(max * 0.6));
    long deadline = System.currentTimeMillis() + runSeconds * 1000L;

    List<Payload> retained = new ArrayList<>();
    List<WeakReference<Payload>> watchers = new ArrayList<>();
    Map<String, String> lruStrings = new HashMap<>();
    Deque<int[]> rollingInts = new ArrayDeque<>();
    IntegrityMonitor monitor = new IntegrityMonitor();
    long approxBytes = 0;
    int iter = 0;

    try {
      while (System.currentTimeMillis() < deadline && approxBytes < targetBytes) {
        Payload payload = newPayload(iter);
        payload.touch();
        retained.add(payload);
        approxBytes += payload.footprint();
        watchers.add(new WeakReference<>(payload, REF_Q));
        monitor.track(payload);

        if ((iter & 63) == 0) {
          simulateStringWork(iter, lruStrings);
          maintainInts(iter, rollingInts);
        }

        if ((iter & 255) == 0) {
          pollQueue(watchers);
          System.out.printf(Locale.ROOT,
                            "[iter=%d] approxRetained=%.1f MB, strong=%d, weak=%d%n",
                            iter, approxBytes / (1024.0 * 1024.0), retained.size(),
                            watchers.size());
          if (retained.size() > 4000) {
            int drop = retained.size() / 4;
            for (int i = 0; i < drop; i++)
              retained.remove(0);
            approxBytes = Math.max(0, approxBytes - drop * 16_384);
          }
        }

        if ((iter & 127) == 0) {
          useObjects(retained, lruStrings, rollingInts);
          monitor.verify();
        }

        iter++;
      }
    } finally {
      System.out.println("Simulation reached limit, releasing resources...");
      retained.clear();
      watchers.clear();
      lruStrings.clear();
      rollingInts.clear();
      monitor.clear();
      System.gc();
    }
  }

  private static Payload newPayload(int seq) {
    switch (RAND.nextInt(4)) {
    case 0:
      return new BytePayload(32 * 1024 + RAND.nextInt(64 * 1024), (byte)(seq & 0xFF));
    case 1:
      return new IntPayload(4 * 1024 + RAND.nextInt(32 * 1024), seq);
    case 2:
      return new StringPayload(seq, 128 + RAND.nextInt(512));
    default:
      return new GraphPayload(seq, 8 + RAND.nextInt(48));
    }
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
    watchers.removeIf(r -> r.get() == null);
  }

  private static void useObjects(List<Payload> retained, Map<String, String> cache,
                                 Deque<int[]> ints) {
    if (!retained.isEmpty()) {
      Payload p = retained.get(RAND.nextInt(retained.size()));
      p.touch();
      if (p instanceof StringPayload) {
        StringPayload sp = (StringPayload)p;
        cache.put("touch-" + sp.marker(), sp.headSlice());
      }
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
      if (data.length == 0 || data[0] != pattern || data[data.length - 1] != pattern)
        throw new AssertionError("BytePayload corrupted");
    }
    @Override
    public long footprint() { return data.length; }

    @Override
    public long checksum() {
      int mid = data.length / 2;
      int q = data.length / 4;
      int last = data.length - 1;
      return ((long)data.length << 32) ^ ((long)(data[0] & 0xFF) << 24)
          ^ ((long)(data[mid] & 0xFF) << 16)
          ^ ((long)(data[q] & 0xFF) << 8) ^ (data[last] & 0xFF) ^ pattern;
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
    public long footprint() { return ints.length * Integer.BYTES; }

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
    public long footprint() { return value.length() * 2L + 40; }

    @Override
    public long checksum() {
      return value.hashCode() ^ marker.hashCode();
    }

    String marker() { return marker; }
    String headSlice() { return value.length() > 4 ? value.substring(0, 4) : value; }
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
    public long footprint() { return depth * 48L; }

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

  private static final class IntegrityMonitor {
    private static final class Record {
      final WeakReference<Payload> ref;
      final long checksum;
      Record(Payload payload, long checksum) {
        this.ref = new WeakReference<>(payload);
        this.checksum = checksum;
      }
    }

    private final List<Record> records = new ArrayList<>();

    void track(Payload payload) {
      records.add(new Record(payload, payload.checksum()));
      if (records.size() > 4096)
        records.remove(0);
    }

    void verify() {
      records.removeIf(r -> r.ref.get() == null);
      for (Record r : records) {
        Payload payload = r.ref.get();
        if (payload == null)
          continue;
        long current = payload.checksum();
        if (current != r.checksum) {
          throw new AssertionError("Payload mutated unexpectedly: " + payload);
        }
      }
    }

    void clear() { records.clear(); }
  }
}
