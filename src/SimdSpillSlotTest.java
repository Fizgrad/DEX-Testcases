// SimdSpillSlotTest.java
// Best-effort SIMD spill/stack-slot coverage for ART Optimizing on ARM64.

public final class SimdSpillSlotTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();
  private static volatile long BLACKHOLE;

  private static final int DEFAULT_LEN = 8192;
  private static final int DEFAULT_ROUNDS = 6;
  private static final int DEFAULT_WARM_ROUNDS = 8;

  private SimdSpillSlotTest() {}

  public static void main(String[] args) {
    CTR.reset();
    BLACKHOLE = 0;
    System.out.println("=== SimdSpillSlotTest starting ===");

    int len = DEFAULT_LEN;
    int rounds = DEFAULT_ROUNDS;
    int warmRounds = DEFAULT_WARM_ROUNDS;
    for (String s : args) {
      if ("--short".equals(s)) {
        len = 2048;
        rounds = 3;
        warmRounds = 4;
      } else if ("--full".equals(s)) {
        len = 65536;
        rounds = 10;
        warmRounds = 16;
      } else if (s.startsWith("--len=")) {
        len = parseInt(s.substring(s.indexOf('=') + 1), len);
      } else if (s.startsWith("--rounds=")) {
        rounds = parseInt(s.substring(s.indexOf('=') + 1), rounds);
      } else if (s.startsWith("--warm=")) {
        warmRounds = parseInt(s.substring(s.indexOf('=') + 1), warmRounds);
      }
    }

    if (len < 128)
      len = 128;
    if (rounds < 1)
      rounds = 1;
    if (warmRounds < 1)
      warmRounds = 1;
    len = (len + 31) & ~31;

    int[] idxMap = new int[len];
    for (int i = 0; i < len; i++) {
      idxMap[i] = i;
    }

    int[] ia0 = new int[len];
    int[] ia1 = new int[len];
    int[] ia2 = new int[len];
    int[] ia3 = new int[len];
    int[] ia4 = new int[len];
    int[] ia5 = new int[len];
    int[] ia6 = new int[len];
    int[] ia7 = new int[len];
    int[] ia8 = new int[len];
    int[] ia9 = new int[len];
    int[] ia10 = new int[len];
    int[] ia11 = new int[len];
    int[] intOut0 = new int[len];
    int[] intOut1 = new int[len];
    int[] intPredOut = new int[len];
    int[] intExp0 = new int[len];
    int[] intExp1 = new int[len];
    int[] intPredExp = new int[len];

    long[] la0 = new long[len];
    long[] la1 = new long[len];
    long[] la2 = new long[len];
    long[] la3 = new long[len];
    long[] la4 = new long[len];
    long[] la5 = new long[len];
    long[] longOut = new long[len];
    long[] longExp = new long[len];

    float[] fa0 = new float[len];
    float[] fa1 = new float[len];
    float[] fa2 = new float[len];
    float[] fa3 = new float[len];
    float[] fa4 = new float[len];
    float[] fa5 = new float[len];
    float[] floatOut = new float[len];
    float[] floatExp = new float[len];

    double[] da0 = new double[len];
    double[] da1 = new double[len];
    double[] da2 = new double[len];
    double[] da3 = new double[len];
    double[] da4 = new double[len];
    double[] da5 = new double[len];
    double[] doubleOut = new double[len];
    double[] doubleExp = new double[len];

    fillInputs(len,
               ia0, ia1, ia2, ia3, ia4, ia5, ia6, ia7, ia8, ia9, ia10, ia11,
               la0, la1, la2, la3, la4, la5,
               fa0, fa1, fa2, fa3, fa4, fa5,
               da0, da1, da2, da3, da4, da5);

    long expIntPressure = kernelIntPressureRef(
        idxMap, ia0, ia1, ia2, ia3, ia4, ia5, ia6, ia7, ia8, ia9, ia10, ia11, intExp0, intExp1, len);
    long expIntPred = kernelIntPredicateRef(idxMap, ia0, ia1, ia2, ia3, intPredExp, len);
    long expLong = kernelLongBitwiseRef(idxMap, la0, la1, la2, la3, la4, la5, longExp, len);
    long expFloat = kernelFloatBlendRef(idxMap, fa0, fa1, fa2, fa3, fa4, fa5, floatExp, len);
    long expDouble = kernelDoubleBlendRef(idxMap, da0, da1, da2, da3, da4, da5, doubleExp, len);
    long expReduceInt = kernelReduceIntRef(idxMap, ia6, ia7, len);

    JitSupport.requestJitCompilation(SimdSpillSlotTest.class);
    for (int r = 0; r < warmRounds; r++) {
      kernelIntPressure(ia0, ia1, ia2, ia3, ia4, ia5, ia6, ia7, ia8, ia9, ia10, ia11,
                        intOut0, intOut1, len);
      kernelIntPredicate(ia0, ia1, ia2, ia3, intPredOut, len);
      kernelLongBitwise(la0, la1, la2, la3, la4, la5, longOut, len);
      kernelFloatBlend(fa0, fa1, fa2, fa3, fa4, fa5, floatOut, len);
      kernelDoubleBlend(da0, da1, da2, da3, da4, da5, doubleOut, len);
      kernelReduceInt(ia6, ia7, len);
    }

    for (int r = 0; r < rounds; r++) {
      long gotIntPressure = kernelIntPressure(
          ia0, ia1, ia2, ia3, ia4, ia5, ia6, ia7, ia8, ia9, ia10, ia11, intOut0, intOut1, len);
      long gotIntPred = kernelIntPredicate(ia0, ia1, ia2, ia3, intPredOut, len);
      long gotLong = kernelLongBitwise(la0, la1, la2, la3, la4, la5, longOut, len);
      long gotFloat = kernelFloatBlend(fa0, fa1, fa2, fa3, fa4, fa5, floatOut, len);
      long gotDouble = kernelDoubleBlend(da0, da1, da2, da3, da4, da5, doubleOut, len);
      long gotReduceInt = kernelReduceInt(ia6, ia7, len);

      TestSupport.checkEq("simd.intPressure.sum.r" + r, gotIntPressure, expIntPressure, CTR);
      TestSupport.checkEq("simd.intPredicate.sum.r" + r, gotIntPred, expIntPred, CTR);
      TestSupport.checkEq("simd.longBitwise.sum.r" + r, gotLong, expLong, CTR);
      TestSupport.checkEq("simd.floatBlend.sum.r" + r, gotFloat, expFloat, CTR);
      TestSupport.checkEq("simd.doubleBlend.sum.r" + r, gotDouble, expDouble, CTR);
      TestSupport.checkEq("simd.reduceInt.sum.r" + r, gotReduceInt, expReduceInt, CTR);

      TestSupport.checkTrue("simd.intPressure.arr.r" + r,
                            eqIntArray(intOut0, intExp0) && eqIntArray(intOut1, intExp1), CTR);
      TestSupport.checkTrue("simd.intPredicate.arr.r" + r,
                            eqIntArray(intPredOut, intPredExp), CTR);
      TestSupport.checkTrue("simd.longBitwise.arr.r" + r,
                            eqLongArray(longOut, longExp), CTR);
      TestSupport.checkTrue("simd.floatBlend.arr.r" + r,
                            eqFloatArray(floatOut, floatExp), CTR);
      TestSupport.checkTrue("simd.doubleBlend.arr.r" + r,
                            eqDoubleArray(doubleOut, doubleExp), CTR);
    }

    TestSupport.summary("SimdSpillSlotTest", CTR);
    if (CTR.getFail() != 0)
      System.exit(1);
  }

  private static void fillInputs(
      int len,
      int[] ia0, int[] ia1, int[] ia2, int[] ia3, int[] ia4, int[] ia5,
      int[] ia6, int[] ia7, int[] ia8, int[] ia9, int[] ia10, int[] ia11,
      long[] la0, long[] la1, long[] la2, long[] la3, long[] la4, long[] la5,
      float[] fa0, float[] fa1, float[] fa2, float[] fa3, float[] fa4, float[] fa5,
      double[] da0, double[] da1, double[] da2, double[] da3, double[] da4, double[] da5) {
    for (int i = 0; i < len; i++) {
      ia0[i] = mix32(i * 17 + 1);
      ia1[i] = mix32(i * 19 + 3);
      ia2[i] = mix32(i * 23 + 5);
      ia3[i] = mix32(i * 29 + 7);
      ia4[i] = mix32(i * 31 + 11);
      ia5[i] = mix32(i * 37 + 13);
      ia6[i] = mix32(i * 41 + 17);
      ia7[i] = mix32(i * 43 + 19);
      ia8[i] = mix32(i * 47 + 23);
      ia9[i] = mix32(i * 53 + 29);
      ia10[i] = mix32(i * 59 + 31);
      ia11[i] = mix32(i * 61 + 37);

      la0[i] = mix64(i * 67L + 1L);
      la1[i] = mix64(i * 71L + 3L);
      la2[i] = mix64(i * 73L + 5L);
      la3[i] = mix64(i * 79L + 7L);
      la4[i] = mix64(i * 83L + 11L);
      la5[i] = mix64(i * 89L + 13L);

      fa0[i] = ((mix32(i * 7 + 1) & 0x1fff) - 4096) / 257.0f;
      fa1[i] = ((mix32(i * 9 + 3) & 0x1fff) - 4096) / 193.0f;
      fa2[i] = ((mix32(i * 11 + 5) & 0x1fff) - 4096) / 181.0f;
      fa3[i] = ((mix32(i * 13 + 7) & 0x1fff) - 4096) / 149.0f;
      fa4[i] = ((mix32(i * 15 + 9) & 0x1fff) - 4096) / 127.0f;
      fa5[i] = ((mix32(i * 17 + 11) & 0x1fff) - 4096) / 113.0f;

      da0[i] = ((mix64(i * 7L + 1L) & 0x3ffffL) - 131072.0) / 257.0;
      da1[i] = ((mix64(i * 9L + 3L) & 0x3ffffL) - 131072.0) / 241.0;
      da2[i] = ((mix64(i * 11L + 5L) & 0x3ffffL) - 131072.0) / 211.0;
      da3[i] = ((mix64(i * 13L + 7L) & 0x3ffffL) - 131072.0) / 199.0;
      da4[i] = ((mix64(i * 15L + 9L) & 0x3ffffL) - 131072.0) / 181.0;
      da5[i] = ((mix64(i * 17L + 11L) & 0x3ffffL) - 131072.0) / 173.0;
    }
  }

  private static long kernelIntPressure(
      int[] a0, int[] a1, int[] a2, int[] a3, int[] a4, int[] a5,
      int[] a6, int[] a7, int[] a8, int[] a9, int[] a10, int[] a11,
      int[] out0, int[] out1, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      int p0 = a0[i] + a1[i];
      int p1 = a2[i] - a3[i];
      int p2 = a4[i] ^ a5[i];
      int p3 = a6[i] + a7[i];
      int p4 = a8[i] - a9[i];
      int p5 = a10[i] ^ a11[i];

      int q0 = (p0 << 1) + (p1 >>> 1);
      int q1 = (p2 << 2) - (p3 >>> 2);
      int q2 = (p4 << 3) ^ (p5 >>> 3);

      int abs1 = (p1 >= 0) ? p1 : -p1;
      int abs4 = (p4 >= 0) ? p4 : -p4;
      int r0 = (q0 > q1) ? q0 : q1;
      int r1 = (q1 < q2) ? q1 : q2;

      int o0 = (r0 ^ r1) + abs1 + abs4;
      int o1 = (o0 << 1) ^ (q2 + p0 - p5);

      out0[i] = o0;
      out1[i] = o1;
      acc += (long)(o0 & 0xffff) + (long)(o1 & 0xffff);
    }
    BLACKHOLE ^= acc;
    return acc;
  }

  private static long kernelIntPressureRef(
      int[] idx, int[] a0, int[] a1, int[] a2, int[] a3, int[] a4, int[] a5,
      int[] a6, int[] a7, int[] a8, int[] a9, int[] a10, int[] a11,
      int[] out0, int[] out1, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      int j = idx[i];
      int p0 = a0[j] + a1[j];
      int p1 = a2[j] - a3[j];
      int p2 = a4[j] ^ a5[j];
      int p3 = a6[j] + a7[j];
      int p4 = a8[j] - a9[j];
      int p5 = a10[j] ^ a11[j];

      int q0 = (p0 << 1) + (p1 >>> 1);
      int q1 = (p2 << 2) - (p3 >>> 2);
      int q2 = (p4 << 3) ^ (p5 >>> 3);

      int abs1 = (p1 >= 0) ? p1 : -p1;
      int abs4 = (p4 >= 0) ? p4 : -p4;
      int r0 = (q0 > q1) ? q0 : q1;
      int r1 = (q1 < q2) ? q1 : q2;

      int o0 = (r0 ^ r1) + abs1 + abs4;
      int o1 = (o0 << 1) ^ (q2 + p0 - p5);

      out0[j] = o0;
      out1[j] = o1;
      acc += (long)(o0 & 0xffff) + (long)(o1 & 0xffff);
    }
    return acc;
  }

  private static long kernelIntPredicate(int[] a0, int[] a1, int[] a2, int[] a3,
                                         int[] out, int n) {
    long acc = 0x9e3779b97f4a7c15L;
    for (int i = 0; i < n; i++) {
      int x = a0[i] - a1[i];
      int y = a2[i] + a3[i];
      int hi = (x > y) ? x : y;
      int lo = (x < y) ? x : y;
      int d = hi - lo;
      int abs = (d >= 0) ? d : -d;
      int mixed = ((x & 1) == 0) ? (abs ^ hi) : (abs ^ lo);
      out[i] = mixed;
      acc = (acc << 7) ^ (acc >>> 3) ^ ((long)mixed + i);
    }
    BLACKHOLE ^= acc;
    return acc;
  }

  private static long kernelIntPredicateRef(int[] idx, int[] a0, int[] a1, int[] a2, int[] a3,
                                            int[] out, int n) {
    long acc = 0x9e3779b97f4a7c15L;
    for (int i = 0; i < n; i++) {
      int j = idx[i];
      int x = a0[j] - a1[j];
      int y = a2[j] + a3[j];
      int hi = (x > y) ? x : y;
      int lo = (x < y) ? x : y;
      int d = hi - lo;
      int abs = (d >= 0) ? d : -d;
      int mixed = ((x & 1) == 0) ? (abs ^ hi) : (abs ^ lo);
      out[j] = mixed;
      acc = (acc << 7) ^ (acc >>> 3) ^ ((long)mixed + j);
    }
    return acc;
  }

  private static long kernelLongBitwise(long[] a0, long[] a1, long[] a2,
                                        long[] a3, long[] a4, long[] a5,
                                        long[] out, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      long t0 = a0[i] + a1[i];
      long t1 = a2[i] - a3[i];
      long t2 = a4[i] ^ a5[i];
      long u0 = (t0 << 3) ^ (t1 >>> 5);
      long u1 = (t1 << 2) ^ (t2 >>> 7);
      long u2 = (t2 << 1) ^ (t0 >>> 11);
      long o = u0 + u1 - u2;
      out[i] = o;
      acc ^= (o + (acc << 1));
    }
    BLACKHOLE ^= acc;
    return acc;
  }

  private static long kernelLongBitwiseRef(int[] idx, long[] a0, long[] a1, long[] a2,
                                           long[] a3, long[] a4, long[] a5,
                                           long[] out, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      int j = idx[i];
      long t0 = a0[j] + a1[j];
      long t1 = a2[j] - a3[j];
      long t2 = a4[j] ^ a5[j];
      long u0 = (t0 << 3) ^ (t1 >>> 5);
      long u1 = (t1 << 2) ^ (t2 >>> 7);
      long u2 = (t2 << 1) ^ (t0 >>> 11);
      long o = u0 + u1 - u2;
      out[j] = o;
      acc ^= (o + (acc << 1));
    }
    return acc;
  }

  private static long kernelFloatBlend(float[] a0, float[] a1, float[] a2,
                                       float[] a3, float[] a4, float[] a5,
                                       float[] out, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      float x0 = a0[i] * a1[i] + a2[i];
      float x1 = a3[i] - a4[i];
      float hi = (x0 > x1) ? x0 : x1;
      float lo = (x0 < x1) ? x0 : x1;
      float abs = (x1 >= 0f) ? x1 : -x1;
      float o = (hi - lo) + abs + (a5[i] * 0.125f);
      out[i] = o;
      acc ^= (long)Float.floatToRawIntBits(o) & 0xffffffffL;
    }
    BLACKHOLE ^= acc;
    return acc;
  }

  private static long kernelFloatBlendRef(int[] idx, float[] a0, float[] a1, float[] a2,
                                          float[] a3, float[] a4, float[] a5,
                                          float[] out, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      int j = idx[i];
      float x0 = a0[j] * a1[j] + a2[j];
      float x1 = a3[j] - a4[j];
      float hi = (x0 > x1) ? x0 : x1;
      float lo = (x0 < x1) ? x0 : x1;
      float abs = (x1 >= 0f) ? x1 : -x1;
      float o = (hi - lo) + abs + (a5[j] * 0.125f);
      out[j] = o;
      acc ^= (long)Float.floatToRawIntBits(o) & 0xffffffffL;
    }
    return acc;
  }

  private static long kernelDoubleBlend(double[] a0, double[] a1, double[] a2,
                                        double[] a3, double[] a4, double[] a5,
                                        double[] out, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      double x0 = (a0[i] * 0.5) + a1[i] + a2[i];
      double x1 = (a3[i] * 0.25) - a4[i] + a5[i];
      double hi = (x0 > x1) ? x0 : x1;
      double lo = (x0 < x1) ? x0 : x1;
      double diff = hi - lo;
      double abs = (x1 >= 0d) ? x1 : -x1;
      double o = diff + abs;
      out[i] = o;
      acc ^= Double.doubleToRawLongBits(o);
    }
    BLACKHOLE ^= acc;
    return acc;
  }

  private static long kernelDoubleBlendRef(int[] idx, double[] a0, double[] a1, double[] a2,
                                           double[] a3, double[] a4, double[] a5,
                                           double[] out, int n) {
    long acc = 0;
    for (int i = 0; i < n; i++) {
      int j = idx[i];
      double x0 = (a0[j] * 0.5) + a1[j] + a2[j];
      double x1 = (a3[j] * 0.25) - a4[j] + a5[j];
      double hi = (x0 > x1) ? x0 : x1;
      double lo = (x0 < x1) ? x0 : x1;
      double diff = hi - lo;
      double abs = (x1 >= 0d) ? x1 : -x1;
      double o = diff + abs;
      out[j] = o;
      acc ^= Double.doubleToRawLongBits(o);
    }
    return acc;
  }

  private static long kernelReduceInt(int[] a0, int[] a1, int n) {
    long sum = 0;
    for (int i = 0; i < n; i++) {
      sum += (a0[i] ^ i) + (a1[i] << 1);
    }
    BLACKHOLE ^= sum;
    return sum;
  }

  private static long kernelReduceIntRef(int[] idx, int[] a0, int[] a1, int n) {
    long sum = 0;
    for (int i = 0; i < n; i++) {
      int j = idx[i];
      sum += (a0[j] ^ j) + (a1[j] << 1);
    }
    return sum;
  }

  private static boolean eqIntArray(int[] a, int[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i])
        return false;
    }
    return true;
  }

  private static boolean eqLongArray(long[] a, long[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i])
        return false;
    }
    return true;
  }

  private static boolean eqFloatArray(float[] a, float[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i]))
        return false;
    }
    return true;
  }

  private static boolean eqDoubleArray(double[] a, double[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      if (Double.doubleToRawLongBits(a[i]) != Double.doubleToRawLongBits(b[i]))
        return false;
    }
    return true;
  }

  private static int parseInt(String s, int def) {
    try {
      return Integer.parseInt(s.trim());
    } catch (Throwable t) {
      return def;
    }
  }

  private static int mix32(int x) {
    int v = x * 0x9e3779b9;
    v ^= v >>> 16;
    v *= 0x85ebca6b;
    v ^= v >>> 13;
    v *= 0xc2b2ae35;
    v ^= v >>> 16;
    return v;
  }

  private static long mix64(long x) {
    long v = x + 0x9e3779b97f4a7c15L;
    v = (v ^ (v >>> 30)) * 0xbf58476d1ce4e5b9L;
    v = (v ^ (v >>> 27)) * 0x94d049bb133111ebL;
    return v ^ (v >>> 31);
  }
}

