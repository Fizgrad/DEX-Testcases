package com.art.tests.hash;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public class HashCodeStabilityTest {

  // ---- 小工具：编码参数，方便做校验 ----
  static final class Box {
    final int value;
    Box(int v) { this.value = v; }
  }

  private static long encInt(int v) { return 31L * v; }
  private static long encLong(long v) { return 131L * v; }
  private static long encFloat(float v) {
    return 7L * (long)Float.floatToIntBits(v);
  }
  private static long encDouble(double v) {
    return 17L * Double.doubleToLongBits(v);
  }
  private static long encBox(Box b) { return (b == null) ? 0L : 97L * b.value; }

  private static void assertEquals(String name, long expect, long actual) {
    if (expect != actual) {
      System.out.println("FAIL: " + name + " expect=" + expect +
                         " actual=" + actual);
      throw new AssertionError(name + " failed");
    } else {
      System.out.println("OK  : " + name + " = " + actual);
    }
  }

  // =========================================================
  // 1. 静态调用：NterpCommonInvokeStatic / StaticRange
  // =========================================================

  // 期望生成：invoke-static（非 range，<=5 参数）
  private static long staticTargetNonRange(long a, long b, double c,
                                           Box primary, Box secondary) {
    return 10L ^ encLong(a) ^ encLong(b) ^ encDouble(c) ^
        encBox(primary) ^ encBox(secondary);
  }

  // 期望生成：invoke-static/range（>5 参数）
  private static long staticTargetRange(long a, long b, double c, Box primary,
                                        Box secondary, double extraWide,
                                        Box tail) {
    return 20L ^ encLong(a) ^ encLong(b) ^ encDouble(c) ^
        encBox(primary) ^ encBox(secondary) ^ encDouble(extraWide) ^
        (encBox(tail) * 13L);
  }

  private static void testInvokeStatic() {
    Box primary = new Box(5);
    Box secondary = new Box(7);
    Box tail = new Box(11);
    long a = 1L;
    long b = 2L;
    double c = 4.5;
    double extraWide = 9.25;

    long r1 = staticTargetNonRange(a, b, c, primary, secondary);
    long expected1 = 4697817361300849357L;
    assertEquals("invoke-static (non-range)", expected1, r1);

    long r2 = staticTargetRange(a, b, c, primary, secondary, extraWide, tail);
    long expected2 = 250090516807431420L;
    assertEquals("invoke-static/range", expected2, r2);
  }

  // =========================================================
  // 2. 实例调用：NterpCommonInvokeInstance / InstanceRange
  // =========================================================

  static class InstanceTarget {
    final int id;
    InstanceTarget(int id) { this.id = id; }

    // 期望：invoke-virtual（非 range，总参数 this+5 = 6）
    long instanceNonRange(long a, double b, long c, Box first, Box second) {
      return 30L ^ encInt(id) ^ encLong(a) ^ encDouble(b) ^ encLong(c) ^
          encBox(first) ^ encBox(second);
    }

    // 期望：invoke-virtual/range（this+7 = 8）
    long instanceRange(long a, double b, long c, Box first, Box second,
                       double bonus, Box tail) {
      return 40L ^ encInt(id) ^ encLong(a) ^ encDouble(b) ^ encLong(c) ^
          encBox(first) ^ encBox(second) ^ encDouble(bonus) ^
          (encBox(tail) * 17L);
    }
  }

  private static void testInvokeInstance() {
    InstanceTarget t = new InstanceTarget(7);
    Box first = new Box(9);
    Box second = new Box(11);
    Box tail = new Box(13);
    long a = 3L;
    double b = 1.25;
    long c = 4L;
    double bonus = 6.5;

    long r1 = t.instanceNonRange(a, b, c, first, second);
    long expected1 = 4554265123178415104L;
    assertEquals("invoke-instance (non-range)", expected1, r1);

    long r2 = t.instanceRange(a, b, c, first, second, bonus, tail);
    long expected2 = 9119226295471855499L;
    assertEquals("invoke-instance/range", expected2, r2);
  }

  // =========================================================
  // 3. 接口调用：NterpCommonInvokeInterface / InterfaceRange
  // =========================================================

  interface MyInterface {
    long ifaceNonRange(long a, double b, long c, Box first, Box second);
    long ifaceRange(long a, double b, long c, Box first, Box second,
                    double bonus, Box tail);
  }

  static class InterfaceImpl implements MyInterface {
    final int id;
    InterfaceImpl(int id) { this.id = id; }

    @Override
    public long ifaceNonRange(long a, double b, long c, Box first,
                              Box second) {
      return 50L ^ encInt(id) ^ encLong(a) ^ encDouble(b) ^ encLong(c) ^
          encBox(first) ^ encBox(second);
    }

    @Override
    public long ifaceRange(long a, double b, long c, Box first, Box second,
                           double bonus, Box tail) {
      return 60L ^ encInt(id) ^ encLong(a) ^ encDouble(b) ^ encLong(c) ^
          encBox(first) ^ encBox(second) ^ encDouble(bonus) ^
          (encBox(tail) * 19L);
    }
  }

  private static void testInvokeInterface() {
    MyInterface iface = new InterfaceImpl(13);
    Box first = new Box(6);
    Box second = new Box(8);
    Box tail = new Box(15);
    long a = 8L;
    double b = 123.0;
    long c = 9L;
    double bonus = 77.5;

    // 静态类型是接口 → 生成 invoke-interface / invoke-interface-range
    long r1 = iface.ifaceNonRange(a, b, c, first, second);
    long expected1 = 5065071837164077164L;
    assertEquals("invoke-interface (non-range)", expected1, r1);

    long r2 = iface.ifaceRange(a, b, c, first, second, bonus, tail);
    long expected2 = 271236324432833439L;
    assertEquals("invoke-interface/range", expected2, r2);
  }

  // =========================================================
  // 4. 方法句柄：NterpCommonInvokePolymorphic / Range
  //    （invoke-polymorphic / invoke-polymorphic/range）
  // =========================================================

  // 目标静态方法：对应 invoke-polymorphic（非 range）
  private static long polyTargetNonRange(long a, double b, Box first, long c,
                                         Box second) {
    long r = 0;
    r ^= 0x0100000000000000L * (a & 0xff); // 低 8bit，避免太大
    r ^= 0x0200000000000000L * (c & 0xff);
    r ^= Double.doubleToLongBits(b);
    r ^= (long)(first != null ? first.value & 0xff : 0) << 40;
    r ^= (long)(second != null ? second.value & 0xff : 0) << 20;
    return r;
  }

  // 目标静态方法：对应 invoke-polymorphic/range
  private static long polyTargetRange(long a, double b, Box first, long c,
                                      Box second, double extra, Box third) {
    return 80L ^ encLong(a) ^ encDouble(b) ^ encLong(c) ^ encBox(first) ^
        encBox(second) ^ encDouble(extra) ^ (encBox(third) * 23L);
  }

  private static void testInvokePolymorphic() throws Throwable {
    Box first = new Box(10);
    Box second = new Box(12);
    Box third = new Box(14);
    long a = 5L;
    double b = 8.0;
    long c = 6L;
    double extra = 99.5;

    // ---- 非 range：MethodHandle + 5 动态参数 ----
    MethodType mt1 = MethodType.methodType(long.class, long.class, double.class,
                                           Box.class, long.class, Box.class);
    MethodHandle mh1 = MethodHandles.lookup().findStatic(
        HashCodeStabilityTest.class, "polyTargetNonRange", mt1);

    long expected1 = 5269222559152340992L;
    long r1 = (long)mh1.invokeExact(a, b, first, c, second);
    assertEquals("invoke-polymorphic (non-range)", expected1, r1);

    // ---- range：MethodHandle + 7 动态参数 ----
    MethodType mt2 =
        MethodType.methodType(long.class, long.class, double.class, Box.class,
                              long.class, Box.class, double.class, Box.class);
    MethodHandle mh2 = MethodHandles.lookup().findStatic(
        HashCodeStabilityTest.class, "polyTargetRange", mt2);

    long expected2 = 560381494258859145L;
    long r2 = (long)mh2.invokeExact(a, b, first, c, second, extra, third);
    assertEquals("invoke-polymorphic/range", expected2, r2);
  }

  // =========================================================
  // 5. invoke-custom / invoke-custom-range（lambda）
  //    NterpCommonInvokeCustom / CustomRange
  // =========================================================

  private static long customSinkNonRange(long a, double b, Box first, long c,
                                         Box second) {
    return 90L ^ encLong(a) ^ encDouble(b) ^ encBox(first) ^ encLong(c) ^
        encBox(second);
  }

  private static long customSinkRange(long a, double b, Box first, long c,
                                      Box second, double extra, Box extraBox) {
    return 100L ^ encLong(a) ^ encDouble(b) ^ encBox(first) ^ encLong(c) ^
        encBox(second) ^ encDouble(extra) ^ (encBox(extraBox) * 29L);
  }

  private static void testInvokeCustom() {
    final long a = 1L;
    final double b = 2.0;
    final Box first = new Box(7);
    final long c = 5L;
    final Box second = new Box(9);
    final double extra = 11.5;
    final Box extraBox = new Box(13);

    // 捕获 5 个变量 → invoke-custom（非 range）
    LongSupplier sup1 = () -> customSinkNonRange(a, b, first, c, second);
    long expected1 = 4611686018427388824L;
    long r1 = sup1.getAsLong();
    assertEquals("invoke-custom (non-range)", expected1, r1);

    // 捕获 7 个变量 → invoke-custom/range
    LongSupplier sup2 =
        () -> customSinkRange(a, b, first, c, second, extra, extraBox);
    long expected2 = 186617909559201151L;
    long r2 = sup2.getAsLong();
    assertEquals("invoke-custom/range", expected2, r2);
  }

  // =========================================================
  // 6. String 构造：NterpHandleStringInit / StringInitRange
  // =========================================================

  // 普通 String 构造（this + 1 实参，肯定是非 range）
  private static void testStringInitNonRange() {
    String src = "HelloNterp";
    String s =
        new String(src); // invoke-direct String.<init>(Ljava/lang/String;)V
    if (!"HelloNterp".equals(s)) {
      throw new AssertionError("stringInit non-range failed");
    }
  }

  // 尝试制造一个寄存器很多的场景，让 D8 倾向于使用 invoke-direct/range
  // 如果最终还是 non-range，你可以用 smali 手动把这条指令改成 /range，
  // 继续用相同 Java 逻辑测试 NterpHandleStringInitRange 的传参。
  private static String makeStringWithManyLocals(char[] chars) {
    // 一堆 dummy 局部变量，保证在 new String 之后还会被用到，
    // 迫使编译器分配更多 vreg，增加使用 /range 的概率。
    int l0 = 0, l1 = 1, l2 = 2, l3 = 3, l4 = 4, l5 = 5, l6 = 6, l7 = 7;
    int l8 = 8, l9 = 9, l10 = 10, l11 = 11, l12 = 12, l13 = 13, l14 = 14,
        l15 = 15;

    String s = new String(chars, 1, 3); // 期望调用 <init>([CII)V

    // 使用这些变量，避免被优化掉。
    int sum = l0 + l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + l10 + l11 +
              l12 + l13 + l14 + l15;
    if (sum == -1) {
      // 永远不会走到这，但能让变量保持 live。
      System.out.println("impossible: " + sum);
    }
    return s;
  }

  private static void testStringInitRange() {
    char[] chars = new char[] {'A', 'B', 'C', 'D', 'E', 'F'};
    String s = makeStringWithManyLocals(chars);
    if (!"BCD".equals(s)) {
      throw new AssertionError("stringInit range-ish test failed");
    }
  }

  private static final Random RAND = new Random(20250315L);
  private static final int MAX_TRACKED = (0x7FFFFFFF);
  private static final long VERIFY_INTERVAL_MS = 15000L;
  private static final long REPORT_INTERVAL_MS = 40000L;
  private static final long INVOKE_INTERVAL_MS = 40000L;

  private static int lastVerifiedCount = 0;

  public static void main(String[] args) {
    int runSeconds = 18;
    for (String s : args) {
      if (s.startsWith("--seconds=")) {
        runSeconds = Integer.parseInt(s.substring(s.indexOf('=') + 1));
      } else if ("--short".equals(s)) {
        runSeconds = 2;
      } else if (s.matches("\\d+")) {
        runSeconds = Integer.parseInt(s);
      }
    }
    System.out.println("=== HashCodeStabilityTest start (" + runSeconds +
                       "s) ===");
    run(runSeconds);
    System.out.println("=== HashCodeStabilityTest end ===");
  }

  private static void run(int runSeconds) {
    long deadline = System.currentTimeMillis() + runSeconds * 1000L;
    long nextVerify =
        System.currentTimeMillis() + VERIFY_INTERVAL_MS + 60 * 1000L;
    long nextReport =
        System.currentTimeMillis() + REPORT_INTERVAL_MS + 60 * 1000L;
    long invokeTest =
        System.currentTimeMillis() + INVOKE_INTERVAL_MS + 60 * 1000L;

    List<HashRecord> tracked = new ArrayList<>();
    StatCounter statCounter = new StatCounter();
    int CHUNK_NUM = 1024 * 1024 * 48;
    List<Object> keep = new ArrayList<>(CHUNK_NUM);

    for (int i = CHUNK_NUM; i > 0; i--) {
      byte[] slab = new byte[RAND.nextInt(256)];
      keep.add(slab);
    }

    long seq = 0;
    long approxBytes = 0;
    try {
      while (System.currentTimeMillis() < deadline) {
        Generated g = generate(seq);
        tracked.add(new HashRecord(g.value, g.kind, g.value.hashCode(),
                                   System.identityHashCode(g.value), seq));
        approxBytes += g.approxBytes;
        statCounter.record(g.kind);

        if (tracked.size() > MAX_TRACKED) {
          int drop = tracked.size() - MAX_TRACKED;
          tracked.subList(0, drop).clear();
        }

        long now = System.currentTimeMillis();
        if (now >= nextVerify) {
          lastVerifiedCount = verify(tracked);
          nextVerify = now + VERIFY_INTERVAL_MS;
        }
        if (now >= nextReport) {
          report(seq, tracked.size(), approxBytes, statCounter);
          nextReport = now + REPORT_INTERVAL_MS;
        }
        if (now >= invokeTest) {
          testInvokeStatic();
          testInvokeInstance();
          testInvokeInterface();
          try {
            testInvokePolymorphic();
          } catch (Throwable t) {
            throw new RuntimeException("invoke-polymorphic test failed", t);
          }
          testInvokeCustom();
          testStringInitNonRange();
          testStringInitRange();
          System.out.println("All nterp invoke tests passed (Java side).");
        }
        seq++;
      }
    } catch (OutOfMemoryError oom) {
      System.err.println("OutOfMemory after " + seq + " allocations");
      throw oom;
    } finally {
      lastVerifiedCount = verify(tracked);
    }
  }

  private static Generated generate(long seq) {
    switch (RAND.nextInt(12)) {
    case 0: {
      String text = "S-" + seq + "-" + randomAlpha(48);
      return new Generated(text, "String", text.length() * 2L + 40);
    }
    case 1: {
      byte[] data = new byte[1024 + RAND.nextInt(16 * 1024)];
      Arrays.fill(data, (byte)(seq & 0x7F));
      return new Generated(data, "byte[]", data.length);
    }
    case 2: {
      int[] ints = new int[128 + RAND.nextInt(4096)];
      for (int i = 0; i < ints.length; i++)
        ints[i] = (int)seq + i;
      return new Generated(ints, "int[]", ints.length * Integer.BYTES);
    }
    case 3: {
      List<String> list = new ArrayList<>();
      int count = 6 + RAND.nextInt(12);
      for (int i = 0; i < count; i++)
        list.add(randomAlpha(10) + "-" + (seq & 0xFF));
      return new Generated(Collections.unmodifiableList(list), "List<String>",
                           count * 24L);
    }
    case 4: {
      Map<String, Long> map = new LinkedHashMap<>();
      int size = 6 + RAND.nextInt(10);
      for (int i = 0; i < size; i++) {
        map.put("m-" + seq + "-" + i, seq ^ (0x9E3779B97F4A7C15L * i));
      }
      return new Generated(Collections.unmodifiableMap(map), "Map<String,Long>",
                           size * 96L + 64);
    }
    case 5: {
      StablePojo pojo =
          new StablePojo("P-" + randomAlpha(6), seq, RAND.nextLong());
      return new Generated(pojo, "StablePojo", 128);
    }
    case 6: {
      ByteBuffer buf = ByteBuffer.allocate(512 + RAND.nextInt(4096));
      while (buf.remaining() >= Integer.BYTES)
        buf.putInt((int)(seq ^ buf.position()));
      buf.flip();
      ByteBuffer ro = buf.asReadOnlyBuffer();
      return new Generated(ro, "ByteBuffer", ro.capacity() + 64);
    }
    case 7: {
      Object[] mixed = new Object[4 + RAND.nextInt(12)];
      for (int i = 0; i < mixed.length; i++) {
        mixed[i] =
            (i & 1) == 0 ? randomAlpha(6 + RAND.nextInt(4)) : seq + i * 17;
      }
      return new Generated(mixed, "Object[]", mixed.length * 24L);
    }
    case 8: {
      UUID uuid = deterministicUuid("uuid-" + seq + "-" + RAND.nextInt(10_000));
      return new Generated(uuid, "UUID", 32);
    }
    case 9: {
      LocalDateTime ts = LocalDateTime.ofEpochSecond(
          1_700_000_000L + (seq & 0xFFFF), 0, ZoneOffset.UTC);
      return new Generated(ts, "LocalDateTime", 48);
    }
    case 10: {
      DeepBundle bundle =
          new DeepBundle(randomAlpha(5), randomNumbers(6 + RAND.nextInt(10)),
                         RAND.nextBoolean());
      return new Generated(bundle, "DeepBundle", bundle.approxBytes());
    }
    default: {
      HashSet<String> set = new HashSet<>();
      int size = 3 + RAND.nextInt(8);
      for (int i = 0; i < size; i++)
        set.add("v-" + seq + "-" + randomAlpha(4));
      return new Generated(Collections.unmodifiableSet(set), "Set<String>",
                           size * 24L);
    }
    }
  }

  private static void report(long seq, int trackedSize, long approxBytes,
                             StatCounter statCounter) {
    Runtime rt = Runtime.getRuntime();
    long used = rt.totalMemory() - rt.freeMemory();
    System.out.printf(Locale.ROOT,
                      "[alloc=%d tracked=%d verified=%d] "
                          +
                          "approxAllocated=%.1f MB, heapUsed=%.1f MB, top=%s%n",
                      seq + 1, trackedSize, lastVerifiedCount,
                      approxBytes / (1024.0 * 1024.0), used / (1024.0 * 1024.0),
                      statCounter.topCounts(4));
  }

  private static int verify(List<HashRecord> tracked) {
    int mismatches = 0;
    int checked = 0;
    for (HashRecord r : tracked) {
      Object ref = r.ref;
      int current = ref.hashCode();
      if (current != r.expectedHash) {
        mismatches++;
        System.err.println("[HashDrift] kind=" + r.kind +
                           " desc=" + describe(ref) + " idx=" + r.allocIndex +
                           " expected=" + r.expectedHash +
                           " actual=" + current +
                           " identity=" + System.identityHashCode(ref));
      }
      int identityNow = System.identityHashCode(ref);
      if (identityNow != r.identityHash) {
        mismatches++;
        System.err.println(
            "[IdentityDrift] kind=" + r.kind + " desc=" + describe(ref) +
            " expectedId=" + r.identityHash + " actualId=" + identityNow);
      }
      checked++;
    }
    if (mismatches > 0)
      throw new AssertionError("Detected " + mismatches + " hash mismatches");
    return checked;
  }

  private static String describe(Object obj) {
    if (obj instanceof byte[])
      return "byte[len=" + ((byte[])obj).length + "]";
    if (obj instanceof int[])
      return "int[len=" + ((int[])obj).length + "]";
    if (obj instanceof List)
      return "List[size=" + ((List<?>)obj).size() + "]";
    if (obj instanceof Map)
      return "Map[size=" + ((Map<?, ?>)obj).size() + "]";
    if (obj instanceof ByteBuffer) {
      ByteBuffer buf = (ByteBuffer)obj;
      return "ByteBuffer[pos=" + buf.position() + ",lim=" + buf.limit() +
          ",cap=" + buf.capacity() + "]";
    }
    if (obj instanceof Object[])
      return "Object[len=" + ((Object[])obj).length + "]";
    if (obj instanceof Set)
      return "Set[size=" + ((Set<?>)obj).size() + "]";
    return obj.getClass().getSimpleName();
  }

  private static String randomAlpha(int len) {
    char[] chars = new char[len];
    for (int i = 0; i < len; i++)
      chars[i] = (char)('a' + RAND.nextInt(26));
    return new String(chars);
  }

  private static List<Integer> randomNumbers(int count) {
    List<Integer> list = new ArrayList<>(count);
    for (int i = 0; i < count; i++)
      list.add(RAND.nextInt(100_000));
    return list;
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

  private static final class HashRecord {
    final Object ref;
    final String kind;
    final int expectedHash;
    final int identityHash;
    final long allocIndex;
    HashRecord(Object ref, String kind, int expectedHash, int identityHash,
               long allocIndex) {
      this.ref = ref;
      this.kind = kind;
      this.expectedHash = expectedHash;
      this.identityHash = identityHash;
      this.allocIndex = allocIndex;
    }
  }

  private static final class Generated {
    final Object value;
    final String kind;
    final long approxBytes;
    Generated(Object value, String kind, long approxBytes) {
      this.value = value;
      this.kind = kind;
      this.approxBytes = approxBytes;
    }
  }

  private static final class StablePojo {
    private final String name;
    private final long code;
    private final int salt;
    private final int cachedHash;

    StablePojo(String name, long code, long salt) {
      this.name = name;
      this.code = code ^ 0x5bd1e995L;
      this.salt = (int)(salt & 0x7fffffff);
      this.cachedHash =
          Objects.hash(this.name, this.code, this.salt, (salt & 1) == 0);
    }

    @Override
    public int hashCode() {
      return cachedHash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof StablePojo))
        return false;
      StablePojo that = (StablePojo)o;
      return code == that.code && salt == that.salt &&
          Objects.equals(name, that.name);
    }

    @Override
    public String toString() {
      return "StablePojo[name=" + name + ",code=" + code + "]";
    }
  }

  private static final class DeepBundle {
    private final String tag;
    private final List<Integer> numbers;
    private final boolean flag;
    private final int cachedHash;

    DeepBundle(String tag, List<Integer> numbers, boolean flag) {
      this.tag = tag;
      this.numbers = Collections.unmodifiableList(new ArrayList<>(numbers));
      this.flag = flag;
      this.cachedHash = computeHash();
    }

    private int computeHash() {
      int h = tag.hashCode();
      h = 31 * h + numbers.hashCode();
      h = 31 * h + (flag ? 1 : 0);
      return h;
    }

    long approxBytes() {
      return 64L + tag.length() * 2L + numbers.size() * Integer.BYTES;
    }

    @Override
    public int hashCode() {
      return cachedHash;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (!(o instanceof DeepBundle))
        return false;
      DeepBundle that = (DeepBundle)o;
      return flag == that.flag && Objects.equals(tag, that.tag) &&
          Objects.equals(numbers, that.numbers);
    }

    @Override
    public String toString() {
      return "DeepBundle[tag=" + tag + ",size=" + numbers.size() + "]";
    }
  }

  private static final class StatCounter {
    private final LinkedHashMap<String, Long> counts = new LinkedHashMap<>();

    void record(String kind) { counts.merge(kind, 1L, Long::sum); }

    String topCounts(int limit) {
      if (counts.isEmpty())
        return "";
      List<Map.Entry<String, Long>> entries =
          new ArrayList<>(counts.entrySet());
      entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
      int max = Math.min(limit, entries.size());
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < max; i++) {
        if (i > 0)
          sb.append(", ");
        Map.Entry<String, Long> e = entries.get(i);
        sb.append(e.getKey()).append('=').append(e.getValue());
      }
      return sb.toString();
    }
  }
}
