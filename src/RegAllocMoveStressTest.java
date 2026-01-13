// RegAllocMoveStressTest.java
// Exercises register pressure + swap cycles across mixed types.
 

public final class RegAllocMoveStressTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();
  private static volatile long BLACKHOLE;

  private static final int DEFAULT_ITERS = 20000;
  private static final int DEFAULT_ROUNDS = 5;

  private RegAllocMoveStressTest() {}

  public static void main(String[] args) {
    System.out.println("=== RegAllocMoveStressTest starting ===");
    int iters = DEFAULT_ITERS;
    int rounds = DEFAULT_ROUNDS;

    for (String s : args) {
      if (s.startsWith("--iters=")) {
        iters = parseInt(s.substring(s.indexOf('=') + 1), iters);
      } else if (s.startsWith("--rounds=")) {
        rounds = parseInt(s.substring(s.indexOf('=') + 1), rounds);
      } else if ("--short".equals(s)) {
        iters = 4096;
        rounds = 3;
      } else if ("--full".equals(s)) {
        iters = 80000;
        rounds = 8;
      }
    }

    if (iters < 1)
      iters = 1;
    if (rounds < 1)
      rounds = 1;

    JitSupport.requestJitCompilation(RegAllocMoveStressTest.class);
    warmUp(Math.max(1, iters / 4));

    for (int r = 0; r < rounds; r++) {
      Object a = new Object();
      Object b = new Object();
      Object c = new Object();
      long l1 = 0x1111222233334444L;
      long l2 = 0x5555666677778888L;
      long l3 = 0x9999AAAABBBBCCCCL;
      double d1 = 1.25;
      double d2 = 2.5;
      double d3 = 3.75;
      int i1 = 7;
      int i2 = 11;
      int i3 = 13;

      long expected =
          expectedChecksum(a, b, c, l1, l2, l3, d1, d2, d3, i1, i2, i3, iters);
      long got =
          regPressureAndSwaps(a, b, c, l1, l2, l3, d1, d2, d3, i1, i2, i3,
                              iters);
      TestSupport.checkEq("regalloc.checksum.r" + r, got, expected, CTR);
    }

    TestSupport.summary("RegAllocMoveStressTest", CTR);
    if (CTR.getFail() != 0)
      System.exit(1);
  }

  private static void warmUp(int iters) {
    Object a = new Object();
    Object b = new Object();
    Object c = new Object();
    regPressureAndSwaps(a, b, c, 3L, 5L, 7L, 0.5, 1.5, 2.5, 1, 2, 3, iters);
  }

  private static long regPressureAndSwaps(Object a, Object b, Object c, long l1,
                                          long l2, long l3, double d1,
                                          double d2, double d3, int i1,
                                          int i2, int i3, int iters) {
    Object o1 = a;
    Object o2 = b;
    Object o3 = c;
    Object o4 = new Object();
    Object o5 = new Object();
    Object o6 = new Object();
    Object p1 = new Object();
    Object p2 = new Object();
    Object p3 = new Object();
    Object p4 = new Object();
    Object p5 = new Object();
    Object[] bank = new Object[] {o1, o2, o3, p1, p2, p3};
    long l4 = 0x13579BDF2468ACE0L;
    long l5 = 0x0FEDCBA987654321L;
    long l6 = 0x00FF00FF00FF00FFL;
    long m1 = 0x0A0B0C0D0E0F1011L;
    long m2 = 0x1112131415161718L;
    double f4 = 4.125;
    double f5 = 8.25;
    double f6 = 16.5;
    double e1 = 0.75;
    double e2 = 1.5;
    double e3 = 3.0;
    float g1 = 0.5f;
    float g2 = 1.25f;
    float g3 = 2.5f;
    int j1 = 17;
    int j2 = 19;
    int j3 = 23;
    int k1 = 29;
    int k2 = 31;
    int k3 = 37;
    int k4 = 41;

    long acc = 0;
    for (int i = 0; i < iters; i++) {
      Object tmp = o1;
      o1 = o2;
      o2 = o3;
      o3 = tmp;

      long tl = l1;
      l1 = l2;
      l2 = l3;
      l3 = tl;

      double td = d1;
      d1 = d2;
      d2 = d3;
      d3 = td;

      int ti = i1;
      i1 = i2;
      i2 = i3;
      i3 = ti;

      Object pt = p1;
      p1 = p2;
      p2 = p3;
      p3 = p4;
      p4 = p5;
      p5 = pt;

      long tm = m1;
      m1 = m2;
      m2 = tm;

      double te = e1;
      e1 = e2;
      e2 = e3;
      e3 = te;

      float tg = g1;
      g1 = g2;
      g2 = g3;
      g3 = tg;

      int tk = k1;
      k1 = k2;
      k2 = k3;
      k3 = k4;
      k4 = tk;

      bank[i % bank.length] = (i & 1) == 0 ? p1 : o1;
      bank[(i + 1) % bank.length] = (i & 2) == 0 ? p2 : o2;

      if ((i & 127) == 0) {
        acc ^= touchExtra(bank, p1, p2, p3, p4, p5, m1, m2, e1, e2, e3,
                          g1, g2, g3, k1, k2, k3, k4, i);
      }
      if ((i & 255) == 0) {
        try {
          acc ^= callSitePressure(o1, o2, o3, p1, p2, p3, p4, l1, l2, l3,
                                  m1, m2, d1, d2, d3, e1, e2, e3, g1, g2, g3,
                                  i1, i2, i3, k1, k2, k3, k4, i);
        } finally {
          acc ^= (long)(k1 + k2 + k3 + k4);
        }
      }

      if ((i & 1023) == 0) {
        acc ^= touch(o4, o5, o6, l4, l5, l6, f4, f5, f6, j1, j2, j3, i);
      }
    }
    BLACKHOLE ^= acc;
    return checksum(o1, o2, o3, l1, l2, l3, d1, d2, d3, i1, i2, i3);
  }

  private static long checksum(Object o1, Object o2, Object o3, long l1, long l2,
                               long l3, double d1, double d2, double d3,
                               int i1, int i2, int i3) {
    long sum = 0;
    sum ^= (long)System.identityHashCode(o1);
    sum ^= ((long)System.identityHashCode(o2)) << 1;
    sum ^= ((long)System.identityHashCode(o3)) << 2;
    sum ^= l1 ^ (l2 << 3) ^ (l3 << 5);
    sum ^= Double.doubleToLongBits(d1);
    sum ^= Double.doubleToLongBits(d2) << 1;
    sum ^= Double.doubleToLongBits(d3) << 2;
    sum ^= ((long)i1 << 32) ^ ((long)i2 << 16) ^ i3;
    return sum;
  }

  private static long expectedChecksum(Object a, Object b, Object c, long l1,
                                       long l2, long l3, double d1, double d2,
                                       double d3, int i1, int i2, int i3,
                                       int iters) {
    int mod = iters % 3;
    Object e1;
    Object e2;
    Object e3;
    long el1;
    long el2;
    long el3;
    double ed1;
    double ed2;
    double ed3;
    int ei1;
    int ei2;
    int ei3;
    if (mod == 0) {
      e1 = a;
      e2 = b;
      e3 = c;
      el1 = l1;
      el2 = l2;
      el3 = l3;
      ed1 = d1;
      ed2 = d2;
      ed3 = d3;
      ei1 = i1;
      ei2 = i2;
      ei3 = i3;
    } else if (mod == 1) {
      e1 = b;
      e2 = c;
      e3 = a;
      el1 = l2;
      el2 = l3;
      el3 = l1;
      ed1 = d2;
      ed2 = d3;
      ed3 = d1;
      ei1 = i2;
      ei2 = i3;
      ei3 = i1;
    } else {
      e1 = c;
      e2 = a;
      e3 = b;
      el1 = l3;
      el2 = l1;
      el3 = l2;
      ed1 = d3;
      ed2 = d1;
      ed3 = d2;
      ei1 = i3;
      ei2 = i1;
      ei3 = i2;
    }
    return checksum(e1, e2, e3, el1, el2, el3, ed1, ed2, ed3, ei1, ei2,
                    ei3);
  }

  private static long touch(Object o1, Object o2, Object o3, long l1, long l2,
                            long l3, double d1, double d2, double d3, int i1,
                            int i2, int i3, int iter) {
    long sum = 0;
    sum ^= System.identityHashCode(o1);
    sum ^= System.identityHashCode(o2) << 1;
    sum ^= System.identityHashCode(o3) << 2;
    sum ^= l1 ^ (l2 << 2) ^ (l3 << 3);
    sum ^= Double.doubleToLongBits(d1);
    sum ^= Double.doubleToLongBits(d2) << 1;
    sum ^= Double.doubleToLongBits(d3) << 2;
    sum ^= ((long)i1 << 32) ^ ((long)i2 << 16) ^ i3;
    sum ^= iter * 31L;
    return sum;
  }

  private static long touchExtra(Object[] bank, Object p1, Object p2, Object p3,
                                 Object p4, Object p5, long m1, long m2,
                                 double e1, double e2, double e3, float g1,
                                 float g2, float g3, int k1, int k2, int k3,
                                 int k4, int iter) {
    long sum = 0;
    sum ^= System.identityHashCode(p1);
    sum ^= System.identityHashCode(p2) << 1;
    sum ^= System.identityHashCode(p3) << 2;
    sum ^= System.identityHashCode(p4) << 3;
    sum ^= System.identityHashCode(p5) << 4;
    sum ^= m1 ^ (m2 << 2);
    sum ^= Double.doubleToLongBits(e1);
    sum ^= Double.doubleToLongBits(e2) << 1;
    sum ^= Double.doubleToLongBits(e3) << 2;
    sum ^= (long)Float.floatToIntBits(g1);
    sum ^= (long)Float.floatToIntBits(g2) << 1;
    sum ^= (long)Float.floatToIntBits(g3) << 2;
    sum ^= ((long)k1 << 48) ^ ((long)k2 << 32) ^ ((long)k3 << 16) ^ k4;
    sum ^= (long)bank.length;
    Object b = bank[iter % bank.length];
    if (b != null)
      sum ^= (long)System.identityHashCode(b) << 7;
    sum ^= iter * 17L;
    return sum;
  }

  private static long callSitePressure(Object a, Object b, Object c, Object d,
                                       Object e, Object f, Object g, long l1,
                                       long l2, long l3, long m1, long m2,
                                       double d1, double d2, double d3,
                                       double e1, double e2, double e3,
                                       float f1, float f2, float f3, int i1,
                                       int i2, int i3, int k1, int k2, int k3,
                                       int k4, int iter) {
    long sum = 0;
    sum ^= System.identityHashCode(a);
    sum ^= System.identityHashCode(b) << 1;
    sum ^= System.identityHashCode(c) << 2;
    sum ^= System.identityHashCode(d) << 3;
    sum ^= System.identityHashCode(e) << 4;
    sum ^= System.identityHashCode(f) << 5;
    sum ^= System.identityHashCode(g) << 6;
    sum ^= l1 ^ (l2 << 3) ^ (l3 << 5);
    sum ^= m1 ^ (m2 << 7);
    sum ^= Double.doubleToLongBits(d1);
    sum ^= Double.doubleToLongBits(d2) << 1;
    sum ^= Double.doubleToLongBits(d3) << 2;
    sum ^= Double.doubleToLongBits(e1) << 3;
    sum ^= Double.doubleToLongBits(e2) << 4;
    sum ^= Double.doubleToLongBits(e3) << 5;
    sum ^= (long)Float.floatToIntBits(f1);
    sum ^= (long)Float.floatToIntBits(f2) << 1;
    sum ^= (long)Float.floatToIntBits(f3) << 2;
    sum ^= ((long)i1 << 32) ^ ((long)i2 << 16) ^ i3;
    sum ^= ((long)k1 << 48) ^ ((long)k2 << 32) ^ ((long)k3 << 16) ^ k4;
    sum ^= iter * 13L;
    return sum;
  }

  private static int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
