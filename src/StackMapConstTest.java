// StackMapConstTest.java
// Exercises stack map constant vregs (all primitive types + null) with inlining.
 

public final class StackMapConstTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();
  private static volatile long BLACKHOLE;

  private static final int DEFAULT_ITERS = 20000;
  private static final int DEFAULT_ROUNDS = 3;
  private static final int DEFAULT_THROW_STRIDE = 257;

  private static final boolean CONST_BOOL = true;
  private static final byte CONST_BYTE = (byte)0x5A;
  private static final short CONST_SHORT = (short)0x1234;
  private static final char CONST_CHAR = 'Z';
  private static final int CONST_INT = 0x13579BDF;
  private static final long CONST_LONG = 0x1122334455667788L;
  private static final float CONST_FLOAT = 1.25f;
  private static final double CONST_DOUBLE = -2.5d;
  private static final Object CONST_NULL = null;
  private static final long THROW_FLAG = 0x8000000000000000L;
  private static final long VALUE_MASK = 0x7fffffffffffffffL;

  private static final class TestException extends RuntimeException {
    final long tag;

    TestException(long tag) { this.tag = tag; }
  }

  private StackMapConstTest() {}

  public static void main(String[] args) {
    CTR.reset();
    BLACKHOLE = 0;
    System.out.println("=== StackMapConstTest starting ===");
    int iters = DEFAULT_ITERS;
    int rounds = DEFAULT_ROUNDS;
    int throwStride = DEFAULT_THROW_STRIDE;

    for (String s : args) {
      if (s.startsWith("--iters=")) {
        iters = parseInt(s.substring(s.indexOf('=') + 1), iters);
      } else if (s.startsWith("--rounds=")) {
        rounds = parseInt(s.substring(s.indexOf('=') + 1), rounds);
      } else if (s.startsWith("--throwStride=")) {
        throwStride = parseInt(s.substring(s.indexOf('=') + 1), throwStride);
      } else if ("--short".equals(s)) {
        iters = 4000;
        rounds = 2;
        throwStride = 127;
      } else if ("--full".equals(s)) {
        iters = 60000;
        rounds = 6;
        throwStride = 509;
      }
    }

    if (iters < 1)
      iters = 1;
    if (rounds < 1)
      rounds = 1;
    if (throwStride < 2)
      throwStride = 2;

    long expected = runReference(iters, throwStride);
    long expectedLd = runLongDoubleReference(iters, throwStride);
    JitSupport.requestJitCompilation(StackMapConstTest.class);
    warmUp(Math.max(1, iters / 4), throwStride);

    for (int r = 0; r < rounds; r++) {
      long got = runHot(iters, throwStride);
      long gotLd = runLongDoubleHot(iters, throwStride);
      TestSupport.checkEq("stackmap.const.r" + r, got, expected, CTR);
      TestSupport.checkEq("stackmap.ldCatch.r" + r, gotLd, expectedLd, CTR);
    }

    TestSupport.summary("StackMapConstTest", CTR);
    if (CTR.getFail() != 0)
      System.exit(1);
  }

  private static void warmUp(int iters, int throwStride) {
    runHot(iters, throwStride);
    runHot(Math.max(1, iters / 2), throwStride);
    runLongDoubleHot(iters, throwStride);
  }

  private static long runReference(int iters, int throwStride) {
    boolean cBool = CONST_BOOL;
    byte cByte = CONST_BYTE;
    short cShort = CONST_SHORT;
    char cChar = CONST_CHAR;
    int cInt = CONST_INT;
    long cLong = CONST_LONG;
    float cFloat = CONST_FLOAT;
    double cDouble = CONST_DOUBLE;
    Object cNull = CONST_NULL;

    long sum = 0;
    for (int i = 0; i < iters; i++) {
      long tag = inlineConstFrame(i);
      sum ^= tag;
      if (shouldThrow(i, throwStride)) {
        sum ^= foldConstants(cBool, cByte, cShort, cChar, cInt, cLong, cFloat,
                             cDouble, cNull, i);
        sum ^= throwTag(tag, i);
      } else {
        sum ^= inlineMix(i, tag);
      }
      sum ^= inlineChainReference(i, throwStride);
    }
    BLACKHOLE ^= sum;
    return sum;
  }

  private static long runHot(int iters, int throwStride) {
    boolean cBool = CONST_BOOL;
    byte cByte = CONST_BYTE;
    short cShort = CONST_SHORT;
    char cChar = CONST_CHAR;
    int cInt = CONST_INT;
    long cLong = CONST_LONG;
    float cFloat = CONST_FLOAT;
    double cDouble = CONST_DOUBLE;
    Object cNull = CONST_NULL;

    long sum = 0;
    for (int i = 0; i < iters; i++) {
      long tag = inlineConstFrame(i);
      sum ^= tag;
      try {
        inlineMaybeThrow(i, throwStride, tag);
        sum ^= inlineMix(i, tag);
      } catch (TestException e) {
        sum ^= foldConstants(cBool, cByte, cShort, cChar, cInt, cLong, cFloat,
                             cDouble, cNull, i);
        sum ^= e.tag;
      }
      sum ^= inlineChainHot(i, throwStride);
    }
    BLACKHOLE ^= sum;
    return sum;
  }

  private static long runLongDoubleReference(int iters, int throwStride) {
    long cLong = CONST_LONG;
    double cDouble = CONST_DOUBLE;
    long sum = 0;
    for (int i = 0; i < iters; i++) {
      long tag = inlineLdConstFrame(i);
      if (shouldThrow(i, throwStride)) {
        long exTag = throwLdTag(tag, cLong, cDouble, i);
        sum ^= catchLdMix(cLong, cDouble, exTag, i);
      } else {
        sum ^= inlineLdMix(tag, cLong, cDouble, i);
      }
      sum ^= inlineLdChainReference(i, throwStride);
    }
    BLACKHOLE ^= sum;
    return sum;
  }

  private static long runLongDoubleHot(int iters, int throwStride) {
    long cLong = CONST_LONG;
    double cDouble = CONST_DOUBLE;
    long sum = 0;
    for (int i = 0; i < iters; i++) {
      long tag = inlineLdConstFrame(i);
      try {
        inlineThrowLongDouble(i, throwStride, tag, cLong, cDouble);
        sum ^= inlineLdMix(tag, cLong, cDouble, i);
      } catch (TestException e) {
        sum ^= catchLdMix(cLong, cDouble, e.tag, i);
      }
      sum ^= inlineLdChainHot(i, throwStride);
    }
    BLACKHOLE ^= sum;
    return sum;
  }

  // Inline-friendly: local constants of all primitive types + null.
  private static long inlineConstFrame(int iter) {
    boolean cBool = CONST_BOOL;
    byte cByte = CONST_BYTE;
    short cShort = CONST_SHORT;
    char cChar = CONST_CHAR;
    int cInt = CONST_INT;
    long cLong = CONST_LONG;
    float cFloat = CONST_FLOAT;
    double cDouble = CONST_DOUBLE;
    Object cNull = CONST_NULL;
    long sum = foldConstants(cBool, cByte, cShort, cChar, cInt, cLong, cFloat,
                             cDouble, cNull, iter);
    if ((iter & 3) == 0)
      sum ^= (long)cInt << 2;
    return sum;
  }

  private static void inlineMaybeThrow(int iter, int throwStride, long tag) {
    if (shouldThrow(iter, throwStride)) {
      throw new TestException(throwTag(tag, iter));
    }
    BLACKHOLE ^= tag;
  }

  private static long inlineMix(int iter, long tag) {
    long v = tag ^ ((long)iter * 31L);
    v ^= (v << 7) ^ (v >>> 3);
    return v;
  }

  private static long inlineLdConstFrame(int iter) {
    long l = CONST_LONG;
    double d = CONST_DOUBLE;
    long v = l ^ Double.doubleToLongBits(d);
    v ^= (long)iter * 17L;
    if ((iter & 1) == 0)
      v ^= l << 1;
    return v;
  }

  private static void inlineThrowLongDouble(int iter, int throwStride, long tag,
                                            long cLong, double cDouble) {
    if (shouldThrow(iter, throwStride)) {
      throw new TestException(throwLdTag(tag, cLong, cDouble, iter));
    }
    BLACKHOLE ^= tag ^ cLong ^ (long)cDouble;
  }

  private static long throwLdTag(long tag, long cLong, double cDouble,
                                 int iter) {
    long v = tag ^ cLong ^ Double.doubleToLongBits(cDouble);
    v ^= (long)iter * 131L;
    return v;
  }

  private static long inlineLdMix(long tag, long cLong, double cDouble,
                                  int iter) {
    long v = tag ^ cLong;
    v ^= Double.doubleToLongBits(cDouble);
    v ^= (long)iter * 13L;
    v ^= (v << 5) ^ (v >>> 2);
    return v;
  }

  private static long catchLdMix(long cLong, double cDouble, long exTag,
                                 int iter) {
    long v = exTag ^ (cLong << 1);
    v ^= Double.doubleToLongBits(cDouble);
    v ^= (long)iter * 29L;
    v ^= (v << 3) ^ (v >>> 7);
    return v;
  }

  private static long inlineChainReference(int iter, int throwStride) {
    return frameARef(iter, throwStride);
  }

  private static long inlineChainHot(int iter, int throwStride) {
    return frameAHot(iter, throwStride);
  }

  private static long frameARef(int iter, int throwStride) {
    boolean aBool = CONST_BOOL;
    byte aByte = CONST_BYTE;
    short aShort = CONST_SHORT;
    char aChar = (char)(CONST_CHAR + (iter & 3));
    int aInt = CONST_INT ^ iter;
    long aLong = CONST_LONG;
    float aFloat = CONST_FLOAT + (iter & 7) * 0.125f;
    double aDouble = CONST_DOUBLE - (iter & 7) * 0.25d;
    String aStr = ((iter & 1) == 0) ? "alpha" : "beta";
    Object aObj = ((iter & 2) == 0) ? aStr : null;
    int aLen = aStr.length();
    long base = ((long)(aByte & 0xff) << 8) ^ ((long)aShort << 16);
    base ^= ((long)aChar << 24) ^ ((long)aInt & 0xffffffffL);
    base ^= aLong ^ Double.doubleToLongBits(aDouble);
    base ^= (long)Float.floatToIntBits(aFloat) << 2;
    base ^= aBool ? 0x33L : 0x55L;
    if (aObj == null)
      base ^= 0x7777L;
    base ^= (long)aLen << 3;

    long res = frameBRef(iter, throwStride, base, aInt, aLong, aDouble, aFloat,
                         aChar, aLen);
    if (isThrown(res)) {
      long v = catchA(thrownValue(res), aInt, aLong, aDouble, aLen, iter);
      return maskValue(v);
    }
    return maskValue(mixA(res, base, aInt, aLong, aDouble, aLen, iter));
  }

  private static long frameAHot(int iter, int throwStride) {
    boolean aBool = CONST_BOOL;
    byte aByte = CONST_BYTE;
    short aShort = CONST_SHORT;
    char aChar = (char)(CONST_CHAR + (iter & 3));
    int aInt = CONST_INT ^ iter;
    long aLong = CONST_LONG;
    float aFloat = CONST_FLOAT + (iter & 7) * 0.125f;
    double aDouble = CONST_DOUBLE - (iter & 7) * 0.25d;
    String aStr = ((iter & 1) == 0) ? "alpha" : "beta";
    Object aObj = ((iter & 2) == 0) ? aStr : null;
    int aLen = aStr.length();
    long base = ((long)(aByte & 0xff) << 8) ^ ((long)aShort << 16);
    base ^= ((long)aChar << 24) ^ ((long)aInt & 0xffffffffL);
    base ^= aLong ^ Double.doubleToLongBits(aDouble);
    base ^= (long)Float.floatToIntBits(aFloat) << 2;
    base ^= aBool ? 0x33L : 0x55L;
    if (aObj == null)
      base ^= 0x7777L;
    base ^= (long)aLen << 3;

    try {
      long res = frameBHot(iter, throwStride, base, aInt, aLong, aDouble, aFloat,
                           aChar, aLen);
      return maskValue(mixA(res, base, aInt, aLong, aDouble, aLen, iter));
    } catch (TestException e) {
      long v = catchA(e.tag, aInt, aLong, aDouble, aLen, iter);
      return maskValue(v);
    }
  }

  private static long frameBRef(int iter, int throwStride, long base, int aInt,
                                long aLong, double aDouble, float aFloat,
                                char aChar, int aLen) {
    int bInt = aInt ^ (iter * 31);
    long bLong = aLong + base + bInt;
    double bDouble = aDouble + (double)(bInt & 0xff);
    String bStr = ((iter & 4) == 0) ? "b" + (iter & 7) : "bb";
    int bLen = bStr.length() + aLen;
    long res = frameCRef(iter, throwStride, base, bLong, bDouble, bStr, bLen,
                         aFloat, aChar, aInt);
    if (isThrown(res)) {
      long v = catchB(thrownValue(res), base, bLong, bDouble, bInt, bLen, iter);
      if ((iter & 7) == 0)
        return markThrown(v ^ 0x5a5a5a5aL);
      return maskValue(v);
    }
    return maskValue(mixB(res, base, bLong, bDouble, bInt, bLen, iter));
  }

  private static long frameBHot(int iter, int throwStride, long base, int aInt,
                                long aLong, double aDouble, float aFloat,
                                char aChar, int aLen) {
    int bInt = aInt ^ (iter * 31);
    long bLong = aLong + base + bInt;
    double bDouble = aDouble + (double)(bInt & 0xff);
    String bStr = ((iter & 4) == 0) ? "b" + (iter & 7) : "bb";
    int bLen = bStr.length() + aLen;
    try {
      long res = frameCHot(iter, throwStride, base, bLong, bDouble, bStr, bLen,
                           aFloat, aChar, aInt);
      return maskValue(mixB(res, base, bLong, bDouble, bInt, bLen, iter));
    } catch (TestException e) {
      long v = catchB(e.tag, base, bLong, bDouble, bInt, bLen, iter);
      if ((iter & 7) == 0)
        throw new TestException(maskValue(v ^ 0x5a5a5a5aL));
      return maskValue(v);
    }
  }

  private static long frameCRef(int iter, int throwStride, long base, long bLong,
                                double bDouble, String bStr, int bLen,
                                float aFloat, char aChar, int aInt) {
    boolean cBool = CONST_BOOL;
    byte cByte = (byte)(CONST_BYTE + iter);
    short cShort = (short)(CONST_SHORT ^ iter);
    char cChar = (char)(CONST_CHAR + (iter & 7));
    int cInt = aInt + iter;
    long cLong = CONST_LONG ^ bLong;
    float cFloat = aFloat + (iter & 3) * 0.25f;
    double cDouble = CONST_DOUBLE + bDouble;
    Object cObj = ((iter & 1) == 0) ? bStr : ((iter & 2) == 0 ? "gamma" : null);
    int aux = bLen + cInt + bStr.length() + aChar;
    long tag = base ^ cLong ^ Double.doubleToLongBits(cDouble) ^ aux;

    if (shouldThrow(iter, throwStride)) {
      long thrown = maskValue(throwerTagAll(tag, cLong, cDouble, cInt, cChar,
                                            cShort, cByte, cBool, cObj, iter));
      long v = catchC(thrown, cBool, cByte, cShort, cChar, cInt, cLong, cFloat,
                      cDouble, cObj, aux, iter);
      if ((iter & 3) == 0)
        return markThrown(v ^ 0x33333333L);
      return maskValue(v);
    }
    long v = mixC(tag, cBool, cByte, cShort, cChar, cInt, cLong, cFloat,
                  cDouble, cObj, aux, iter);
    return maskValue(v);
  }

  private static long frameCHot(int iter, int throwStride, long base, long bLong,
                                double bDouble, String bStr, int bLen,
                                float aFloat, char aChar, int aInt) {
    boolean cBool = CONST_BOOL;
    byte cByte = (byte)(CONST_BYTE + iter);
    short cShort = (short)(CONST_SHORT ^ iter);
    char cChar = (char)(CONST_CHAR + (iter & 7));
    int cInt = aInt + iter;
    long cLong = CONST_LONG ^ bLong;
    float cFloat = aFloat + (iter & 3) * 0.25f;
    double cDouble = CONST_DOUBLE + bDouble;
    Object cObj = ((iter & 1) == 0) ? bStr : ((iter & 2) == 0 ? "gamma" : null);
    int aux = bLen + cInt + bStr.length() + aChar;
    long tag = base ^ cLong ^ Double.doubleToLongBits(cDouble) ^ aux;

    try {
      inlineThrowerAll(iter, throwStride, tag, cLong, cDouble, cInt, cChar,
                       cShort, cByte, cBool, cObj);
      long v = mixC(tag, cBool, cByte, cShort, cChar, cInt, cLong, cFloat,
                    cDouble, cObj, aux, iter);
      return maskValue(v);
    } catch (TestException e) {
      long v = catchC(e.tag, cBool, cByte, cShort, cChar, cInt, cLong, cFloat,
                      cDouble, cObj, aux, iter);
      if ((iter & 3) == 0)
        throw new TestException(maskValue(v ^ 0x33333333L));
      return maskValue(v);
    }
  }

  private static void inlineThrowerAll(int iter, int throwStride, long tag,
                                       long cLong, double cDouble, int cInt,
                                       char cChar, short cShort, byte cByte,
                                       boolean cBool, Object cObj) {
    if (shouldThrow(iter, throwStride)) {
      long v = throwerTagAll(tag, cLong, cDouble, cInt, cChar, cShort, cByte,
                             cBool, cObj, iter);
      throw new TestException(maskValue(v));
    }
    BLACKHOLE ^= tag ^ cLong ^ (long)cDouble ^ cInt;
  }

  private static long throwerTagAll(long tag, long cLong, double cDouble,
                                    int cInt, char cChar, short cShort,
                                    byte cByte, boolean cBool, Object cObj,
                                    int iter) {
    long v = tag ^ cLong ^ Double.doubleToLongBits(cDouble);
    v ^= (long)cInt << 1;
    v ^= (long)cChar << 2;
    v ^= (long)cShort << 3;
    v ^= (long)cByte << 4;
    v ^= cBool ? 0x11L : 0x22L;
    if (cObj == null)
      v ^= 0x33L;
    v ^= (long)iter * 7L;
    return v;
  }

  private static long mixA(long res, long base, int aInt, long aLong,
                           double aDouble, int aLen, int iter) {
    long v = res ^ base ^ aLong;
    v ^= Double.doubleToLongBits(aDouble);
    v ^= ((long)aInt & 0xffffffffL) << 1;
    v ^= (long)aLen << 3;
    v ^= (long)iter * 19L;
    return v;
  }

  private static long catchA(long tag, int aInt, long aLong, double aDouble,
                             int aLen, int iter) {
    long v = tag ^ aLong;
    v ^= Double.doubleToLongBits(aDouble);
    v ^= ((long)aInt & 0xffffffffL) << 2;
    v ^= (long)aLen << 4;
    v ^= (long)iter * 23L;
    return v;
  }

  private static long mixB(long res, long base, long bLong, double bDouble,
                           int bInt, int bLen, int iter) {
    long v = res ^ base ^ bLong;
    v ^= Double.doubleToLongBits(bDouble);
    v ^= ((long)bInt & 0xffffffffL) << 1;
    v ^= (long)bLen << 5;
    v ^= (long)iter * 13L;
    return v;
  }

  private static long catchB(long tag, long base, long bLong, double bDouble,
                             int bInt, int bLen, int iter) {
    long v = tag ^ base ^ bLong;
    v ^= Double.doubleToLongBits(bDouble);
    v ^= ((long)bInt & 0xffffffffL) << 3;
    v ^= (long)bLen << 2;
    v ^= (long)iter * 31L;
    return v;
  }

  private static long mixC(long tag, boolean cBool, byte cByte, short cShort,
                           char cChar, int cInt, long cLong, float cFloat,
                           double cDouble, Object cObj, int aux, int iter) {
    long v = tag ^ cLong ^ Double.doubleToLongBits(cDouble);
    v ^= (long)Float.floatToIntBits(cFloat) << 1;
    v ^= (long)(cByte & 0xff) << 8;
    v ^= (long)(cShort & 0xffff) << 16;
    v ^= (long)cChar << 24;
    v ^= ((long)cInt & 0xffffffffL) << 2;
    v ^= cBool ? 0x55L : 0x66L;
    if (cObj instanceof String)
      v ^= ((String)cObj).length() << 3;
    else if (cObj == null)
      v ^= 0x77L;
    v ^= (long)aux << 2;
    v ^= (long)iter * 11L;
    return v;
  }

  private static long catchC(long tag, boolean cBool, byte cByte, short cShort,
                             char cChar, int cInt, long cLong, float cFloat,
                             double cDouble, Object cObj, int aux, int iter) {
    long v = tag ^ cLong ^ Double.doubleToLongBits(cDouble);
    v ^= (long)Float.floatToIntBits(cFloat) << 3;
    v ^= (long)(cByte & 0xff) << 6;
    v ^= (long)(cShort & 0xffff) << 10;
    v ^= (long)cChar << 14;
    v ^= ((long)cInt & 0xffffffffL) << 4;
    v ^= cBool ? 0x22L : 0x44L;
    if (cObj instanceof String)
      v ^= ((String)cObj).length() << 1;
    else if (cObj == null)
      v ^= 0x99L;
    v ^= (long)aux << 1;
    v ^= (long)iter * 37L;
    return v;
  }

  private static long inlineLdChainReference(int iter, int throwStride) {
    return frameLdARef(iter, throwStride);
  }

  private static long inlineLdChainHot(int iter, int throwStride) {
    return frameLdAHot(iter, throwStride);
  }

  private static long frameLdARef(int iter, int throwStride) {
    long l1 = CONST_LONG;
    double d1 = CONST_DOUBLE;
    long base = l1 ^ Double.doubleToLongBits(d1) ^ (long)iter * 7L;
    long res = frameLdBRef(iter, throwStride, base, l1, d1);
    if (isThrown(res)) {
      long v = catchLdA(thrownValue(res), l1, d1, iter);
      return maskValue(v);
    }
    return maskValue(mixLdA(res, l1, d1, iter));
  }

  private static long frameLdAHot(int iter, int throwStride) {
    long l1 = CONST_LONG;
    double d1 = CONST_DOUBLE;
    long base = l1 ^ Double.doubleToLongBits(d1) ^ (long)iter * 7L;
    try {
      long res = frameLdBHot(iter, throwStride, base, l1, d1);
      return maskValue(mixLdA(res, l1, d1, iter));
    } catch (TestException e) {
      long v = catchLdA(e.tag, l1, d1, iter);
      return maskValue(v);
    }
  }

  private static long frameLdBRef(int iter, int throwStride, long base, long l1,
                                  double d1) {
    long l2 = l1 + (long)iter * 13L;
    double d2 = d1 - (iter & 7) * 0.5d;
    long res = frameLdCRef(iter, throwStride, base, l2, d2);
    if (isThrown(res)) {
      long v = catchLdB(thrownValue(res), l2, d2, iter);
      if ((iter & 5) == 0)
        return markThrown(v ^ 0x4444L);
      return maskValue(v);
    }
    return maskValue(mixLdB(res, l2, d2, iter));
  }

  private static long frameLdBHot(int iter, int throwStride, long base, long l1,
                                  double d1) {
    long l2 = l1 + (long)iter * 13L;
    double d2 = d1 - (iter & 7) * 0.5d;
    try {
      long res = frameLdCHot(iter, throwStride, base, l2, d2);
      return maskValue(mixLdB(res, l2, d2, iter));
    } catch (TestException e) {
      long v = catchLdB(e.tag, l2, d2, iter);
      if ((iter & 5) == 0)
        throw new TestException(maskValue(v ^ 0x4444L));
      return maskValue(v);
    }
  }

  private static long frameLdCRef(int iter, int throwStride, long base, long l2,
                                  double d2) {
    long l3 = l2 ^ 0x0f0f0f0f0f0f0f0fL;
    double d3 = d2 + 1.25d;
    long tag = base ^ l3 ^ Double.doubleToLongBits(d3);
    if (shouldThrow(iter, throwStride)) {
      long thrown = maskValue(throwerTagLd(tag, l3, d3, iter));
      long v = catchLdC(thrown, l3, d3, iter);
      if ((iter & 3) == 0)
        return markThrown(v ^ 0x2222L);
      return maskValue(v);
    }
    long v = mixLdC(tag, l3, d3, iter);
    return maskValue(v);
  }

  private static long frameLdCHot(int iter, int throwStride, long base, long l2,
                                  double d2) {
    long l3 = l2 ^ 0x0f0f0f0f0f0f0f0fL;
    double d3 = d2 + 1.25d;
    long tag = base ^ l3 ^ Double.doubleToLongBits(d3);
    try {
      inlineThrowerLd(iter, throwStride, tag, l3, d3);
      long v = mixLdC(tag, l3, d3, iter);
      return maskValue(v);
    } catch (TestException e) {
      long v = catchLdC(e.tag, l3, d3, iter);
      if ((iter & 3) == 0)
        throw new TestException(maskValue(v ^ 0x2222L));
      return maskValue(v);
    }
  }

  private static void inlineThrowerLd(int iter, int throwStride, long tag,
                                      long l3, double d3) {
    if (shouldThrow(iter, throwStride)) {
      long v = throwerTagLd(tag, l3, d3, iter);
      throw new TestException(maskValue(v));
    }
    BLACKHOLE ^= tag ^ l3 ^ (long)d3;
  }

  private static long throwerTagLd(long tag, long l3, double d3, int iter) {
    long v = tag ^ l3 ^ Double.doubleToLongBits(d3);
    v ^= (long)iter * 19L;
    return v;
  }

  private static long mixLdA(long res, long l1, double d1, int iter) {
    long v = res ^ l1 ^ Double.doubleToLongBits(d1);
    v ^= (long)iter * 23L;
    return v;
  }

  private static long catchLdA(long tag, long l1, double d1, int iter) {
    long v = tag ^ l1 ^ Double.doubleToLongBits(d1);
    v ^= (long)iter * 31L;
    return v;
  }

  private static long mixLdB(long res, long l2, double d2, int iter) {
    long v = res ^ l2 ^ Double.doubleToLongBits(d2);
    v ^= (long)iter * 29L;
    return v;
  }

  private static long catchLdB(long tag, long l2, double d2, int iter) {
    long v = tag ^ l2 ^ Double.doubleToLongBits(d2);
    v ^= (long)iter * 37L;
    return v;
  }

  private static long mixLdC(long tag, long l3, double d3, int iter) {
    long v = tag ^ l3 ^ Double.doubleToLongBits(d3);
    v ^= (long)iter * 17L;
    return v;
  }

  private static long catchLdC(long tag, long l3, double d3, int iter) {
    long v = tag ^ l3 ^ Double.doubleToLongBits(d3);
    v ^= (long)iter * 41L;
    return v;
  }

  private static long maskValue(long v) { return v & VALUE_MASK; }
  private static long markThrown(long v) { return maskValue(v) | THROW_FLAG; }
  private static boolean isThrown(long v) { return (v & THROW_FLAG) != 0; }
  private static long thrownValue(long v) { return v & VALUE_MASK; }

  private static boolean shouldThrow(int iter, int throwStride) {
    return (iter % throwStride) == 0;
  }

  private static long throwTag(long tag, int iter) {
    return tag ^ (iter * 131L);
  }

  private static long foldConstants(boolean cBool, byte cByte, short cShort,
                                    char cChar, int cInt, long cLong,
                                    float cFloat, double cDouble, Object cNull,
                                    int iter) {
    long sum = 0;
    sum ^= cBool ? 0x1L : 0x2L;
    sum ^= (long)(cByte & 0xff) << 8;
    sum ^= (long)(cShort & 0xffff) << 16;
    sum ^= (long)cChar << 24;
    sum ^= ((long)cInt & 0xffffffffL) << 1;
    sum ^= cLong;
    sum ^= (long)Float.floatToIntBits(cFloat) << 2;
    sum ^= Double.doubleToLongBits(cDouble);
    sum ^= (cNull == null) ? 0x55aa55aaL : 0xaa55aa55L;
    sum ^= (long)iter * 17L;
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
