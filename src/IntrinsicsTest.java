 

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

public final class IntrinsicsTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();

  private static final int DEFAULT_ITERS = 20000;
  private static final int QUIET_ITERS_THRESHOLD = 50000;
  private static final double EPS_D = 1e-9;
  private static final float EPS_F = 1e-5f;
  private static boolean QUIET = false;

  private IntrinsicsTest() {}

  public static void main(String[] args) {
    CTR.reset();
    System.out.println("=== IntrinsicsTest starting ===");
    int iters = DEFAULT_ITERS;
    boolean forceQuiet = false;
    boolean forceVerbose = false;
    for (String s : args) {
      if ("--short".equals(s)) {
        iters = 4000;
      } else if ("--full".equals(s)) {
        iters = 100000;
        forceQuiet = true;
      } else if (s.startsWith("--iters=")) {
        iters = parseInt(s.substring(s.indexOf('=') + 1), iters);
      } else if ("--quiet".equals(s)) {
        forceQuiet = true;
      } else if ("--verbose".equals(s)) {
        forceVerbose = true;
      }
    }
    if (iters < 1)
      iters = 1;

    QUIET = forceVerbose ? false : (forceQuiet || iters >= QUIET_ITERS_THRESHOLD);

    JitSupport.requestJitCompilation(IntrinsicsTest.class);
    warmUp(2000);

    testIntegerIntrinsics(iters);
    testLongIntrinsics(iters);
    testMathIntrinsics(iters);
    testMathTranscendentals(iters);
    testMathFmaAndMultiplyHigh(iters);
    testFloatDoubleIntrinsics(iters);
    testStringIntrinsics(iters);
    testStringExtraIntrinsics();
    testArrayCopyIntrinsics(iters);
    testArrayCopyEdgeCases();
    testObjectArrayOperations();
    testThreadIntrinsics();
    testCrc32IntrinsicsNoLog(iters);
    testCrc32ByteBufferNoLog();
    testCrc32Intrinsics(iters);
    testCrc32ByteBuffer();
    testMethodHandlePolymorphicOptional();
    testVarHandleIntrinsicsOptional();
    testUnsafeIntrinsicsOptional();
    testReachabilityFenceOptional();

    TestSupport.summary("IntrinsicsTest", CTR);
    if (CTR.getFail() != 0)
      System.exit(1);
  }

  private static void warmUp(int iters) {
    long h = 0;
    for (int i = 0; i < iters; i++) {
      h ^= Integer.rotateLeft(i * 0x9e3779b9, i & 31);
      h ^= Long.rotateRight(((long)i << 33) ^ 0x9E3779B97F4A7C15L, i & 63);
      h ^= Double.doubleToRawLongBits(i * 1.25);
    }
    if (h == 0x1234)
      System.out.println("warmup: " + h);
  }

  private static void testIntegerIntrinsics(int iters) {
    for (int i = 0; i < iters; i++) {
      int x = mix32(i);
      int y = mix32(i ^ 0x5a5a5a5a);
      int sh = i & 31;

      checkEq("Integer.compare.r" + (i & 1),
              Integer.compare(x, y),
              cmpRef(x, y));

      checkEq("Integer.rotateLeft.r" + (i & 1),
              Integer.rotateLeft(x, sh),
              rotlRef(x, sh));
      checkEq("Integer.rotateRight.r" + (i & 1),
              Integer.rotateRight(x, sh),
              rotrRef(x, sh));

      checkEq("Integer.signum.r" + (i & 1),
              Integer.signum(x),
              signumRef(x));

      checkEq("Integer.reverse.r" + (i & 1),
              Integer.reverse(x),
              reverseBits32Ref(x));
      checkEq("Integer.reverseBytes.r" + (i & 1),
              Integer.reverseBytes(x),
              reverseBytes32Ref(x));

      checkEq("Integer.bitCount.r" + (i & 1),
              Integer.bitCount(x),
              bitCount32Ref(x));

      checkEq("Integer.highestOneBit.r" + (i & 1),
              Integer.highestOneBit(x),
              highestOneBit32Ref(x));
      checkEq("Integer.lowestOneBit.r" + (i & 1),
              Integer.lowestOneBit(x),
              lowestOneBit32Ref(x));

      checkEq("Integer.leadingZeros.r" + (i & 1),
              Integer.numberOfLeadingZeros(x),
              leadingZeros32Ref(x));
      checkEq("Integer.trailingZeros.r" + (i & 1),
              Integer.numberOfTrailingZeros(x),
              trailingZeros32Ref(x));

      int div = (y | 1);
      long ux = x & 0xffffffffL;
      long udiv = div & 0xffffffffL;
      int expDiv = (int)(ux / udiv);
      int expRem = (int)(ux % udiv);
      checkEq("Integer.divideUnsigned.r" + (i & 1),
              Integer.divideUnsigned(x, div),
              expDiv);
      checkEq("Integer.remainderUnsigned.r" + (i & 1),
              Integer.remainderUnsigned(x, div),
              expRem);
    }
  }

  private static void testLongIntrinsics(int iters) {
    for (int i = 0; i < iters; i++) {
      long x = mix64(i);
      long y = mix64(i ^ 0x1337);
      int sh = i & 63;

      checkEq("Long.compare.r" + (i & 1),
              Long.compare(x, y),
              cmpRef(x, y));

      checkEq("Long.rotateLeft.r" + (i & 1),
              Long.rotateLeft(x, sh),
              rotlRef(x, sh));
      checkEq("Long.rotateRight.r" + (i & 1),
              Long.rotateRight(x, sh),
              rotrRef(x, sh));

      checkEq("Long.signum.r" + (i & 1),
              Long.signum(x),
              signumRef(x));

      checkEq("Long.reverse.r" + (i & 1),
              Long.reverse(x),
              reverseBits64Ref(x));
      checkEq("Long.reverseBytes.r" + (i & 1),
              Long.reverseBytes(x),
              reverseBytes64Ref(x));

      checkEq("Long.bitCount.r" + (i & 1),
              Long.bitCount(x),
              bitCount64Ref(x));

      checkEq("Long.highestOneBit.r" + (i & 1),
              Long.highestOneBit(x),
              highestOneBit64Ref(x));
      checkEq("Long.lowestOneBit.r" + (i & 1),
              Long.lowestOneBit(x),
              lowestOneBit64Ref(x));

      checkEq("Long.leadingZeros.r" + (i & 1),
              Long.numberOfLeadingZeros(x),
              leadingZeros64Ref(x));
      checkEq("Long.trailingZeros.r" + (i & 1),
              Long.numberOfTrailingZeros(x),
              trailingZeros64Ref(x));

      long div = (y | 1L);
      long expDiv = unsignedDiv64Ref(x, div);
      long expRem = unsignedRem64Ref(x, div);
      checkEq("Long.divideUnsigned.r" + (i & 1),
              Long.divideUnsigned(x, div),
              expDiv);
      checkEq("Long.remainderUnsigned.r" + (i & 1),
              Long.remainderUnsigned(x, div),
              expRem);
    }
  }

  private static void testMathIntrinsics(int iters) {
    for (int i = 0; i < iters; i++) {
      int x = mix32(i);
      int y = mix32(~i);
      long lx = mix64(i);
      long ly = mix64(~i);

      int expAbsI = (x < 0) ? (x == Integer.MIN_VALUE ? Integer.MIN_VALUE : -x) : x;
      long expAbsL = (lx < 0) ? (lx == Long.MIN_VALUE ? Long.MIN_VALUE : -lx) : lx;
      float fx = (float)((x & 0xff) - 127) / 3.0f;
      double dx = (double)((x & 0xffff) - 32768) / 7.0;

      checkEq("Math.abs.int.r" + (i & 1), Math.abs(x), expAbsI);
      checkEq("Math.abs.long.r" + (i & 1), Math.abs(lx), expAbsL);
      checkApprox("Math.abs.float.r" + (i & 1), Math.abs(fx), absRef(fx), 0f);
      checkApprox("Math.abs.double.r" + (i & 1), Math.abs(dx), absRef(dx), 0d);

      checkEq("Math.min.int.r" + (i & 1), Math.min(x, y), (x < y) ? x : y);
      checkEq("Math.max.int.r" + (i & 1), Math.max(x, y), (x > y) ? x : y);
      checkEq("Math.min.long.r" + (i & 1), Math.min(lx, ly), (lx < ly) ? lx : ly);
      checkEq("Math.max.long.r" + (i & 1), Math.max(lx, ly), (lx > ly) ? lx : ly);

      float fy = (float)((y & 0xff) - 127) / 5.0f;
      double dy = (double)((y & 0xffff) - 32768) / 9.0;
      checkApprox("Math.min.float.r" + (i & 1), Math.min(fx, fy), (fx < fy) ? fx : fy, 0f);
      checkApprox("Math.max.float.r" + (i & 1), Math.max(fx, fy), (fx > fy) ? fx : fy, 0f);
      checkApprox("Math.min.double.r" + (i & 1), Math.min(dx, dy), (dx < dy) ? dx : dy, 0d);
      checkApprox("Math.max.double.r" + (i & 1), Math.max(dx, dy), (dx > dy) ? dx : dy, 0d);

      double sdx = (i & 1) == 0 ? dx : -dx;
      double sdy = (i & 2) == 0 ? dy : -dy;
      float sfx = (i & 4) == 0 ? fx : -fx;
      float sfy = (i & 8) == 0 ? fy : -fy;

      checkApprox("Math.copySign.double.r" + (i & 1),
                  Math.copySign(sdx, sdy),
                  copySignRef(sdx, sdy),
                  0d);
      checkApprox("Math.copySign.float.r" + (i & 1),
                  Math.copySign(sfx, sfy),
                  copySignRef(sfx, sfy),
                  0f);

      checkApprox("Math.signum.double.r" + (i & 1), Math.signum(sdx), signumRef(sdx), 0d);
      checkApprox("Math.signum.float.r" + (i & 1), Math.signum(sfx), signumRef(sfx), 0f);

      checkEq("Math.round.double.r" + (i & 1),
              Math.round(sdx),
              roundDoubleRef(sdx));
      checkEq("Math.round.float.r" + (i & 1),
              Math.round(sfx),
              roundFloatRef(sfx));
    }
  }

  private static void testMathTranscendentals(int iters) {
    int loops = Math.max(8, iters / 2048);
    for (int i = 0; i < loops; i++) {
      double x = (i - (loops / 2)) / 7.0;
      double y = (i + 1) / 9.0;
      double tx = x / 3.0;
      double ty = y / 3.0;
      double p = Math.abs(x) + 0.125;
      double bounded = Math.max(-0.999, Math.min(0.999, x / 3.0));

      checkApprox("Math.sqrt.r" + (i & 1), Math.sqrt(p), StrictMath.sqrt(p), EPS_D);
      checkApprox("Math.cbrt.r" + (i & 1), Math.cbrt(x), StrictMath.cbrt(x), EPS_D);
      checkApprox("Math.floor.r" + (i & 1), Math.floor(x), StrictMath.floor(x), 0d);
      checkApprox("Math.ceil.r" + (i & 1), Math.ceil(x), StrictMath.ceil(x), 0d);
      checkApprox("Math.rint.r" + (i & 1), Math.rint(x), StrictMath.rint(x), 0d);

      checkApprox("Math.sin.r" + (i & 1), Math.sin(tx), StrictMath.sin(tx), EPS_D);
      checkApprox("Math.cos.r" + (i & 1), Math.cos(tx), StrictMath.cos(tx), EPS_D);
      checkApprox("Math.tan.r" + (i & 1), Math.tan(tx), StrictMath.tan(tx), EPS_D);
      checkApprox("Math.asin.r" + (i & 1), Math.asin(bounded), StrictMath.asin(bounded), EPS_D);
      checkApprox("Math.acos.r" + (i & 1), Math.acos(bounded), StrictMath.acos(bounded), EPS_D);
      checkApprox("Math.atan.r" + (i & 1), Math.atan(tx), StrictMath.atan(tx), EPS_D);
      checkApprox("Math.atan2.r" + (i & 1), Math.atan2(tx, ty), StrictMath.atan2(tx, ty), EPS_D);

      checkApprox("Math.exp.r" + (i & 1), Math.exp(x / 4.0), StrictMath.exp(x / 4.0), EPS_D);
      checkApprox("Math.expm1.r" + (i & 1), Math.expm1(x / 4.0), StrictMath.expm1(x / 4.0), EPS_D);
      checkApprox("Math.log.r" + (i & 1), Math.log(p), StrictMath.log(p), EPS_D);
      checkApprox("Math.log10.r" + (i & 1), Math.log10(p), StrictMath.log10(p), EPS_D);
      checkApprox("Math.sinh.r" + (i & 1), Math.sinh(tx), StrictMath.sinh(tx), EPS_D);
      checkApprox("Math.cosh.r" + (i & 1), Math.cosh(tx), StrictMath.cosh(tx), EPS_D);
      checkApprox("Math.tanh.r" + (i & 1), Math.tanh(tx), StrictMath.tanh(tx), EPS_D);
      checkApprox("Math.hypot.r" + (i & 1), Math.hypot(tx, ty), StrictMath.hypot(tx, ty), EPS_D);
      checkApprox("Math.pow.r" + (i & 1), Math.pow(p, 1.75), StrictMath.pow(p, 1.75), EPS_D);

      double na = Math.nextAfter(x, y);
      double nb = StrictMath.nextAfter(x, y);
      checkEq("Math.nextAfter.r" + (i & 1),
              Double.doubleToRawLongBits(na),
              Double.doubleToRawLongBits(nb));
    }
  }

  private static void testMathFmaAndMultiplyHigh(int iters) {
    int loops = Math.max(1, iters / 4096);
    for (int i = 0; i < loops; i++) {
      double a = (i + 1) * 0.25;
      double b = (i - 3) * -0.5;
      double c = (i & 1) == 0 ? 1e-3 : -1e-3;
      checkApprox("Math.fma.double.r" + (i & 1),
                  Math.fma(a, b, c),
                  StrictMath.fma(a, b, c),
                  EPS_D);

      float fa = (float)(a * 0.75);
      float fb = (float)(b * -0.5);
      float fc = (i & 2) == 0 ? 1e-2f : -1e-2f;
      checkApprox("Math.fma.float.r" + (i & 1),
                  Math.fma(fa, fb, fc),
                  StrictMath.fma(fa, fb, fc),
                  EPS_F);
    }

    Method multiplyHigh = findMethod(Math.class, "multiplyHigh",
                                     long.class, long.class);
    if (multiplyHigh == null) {
      System.out.println("SKIP Math.multiplyHigh: NoSuchMethodException");
      return;
    }
    try {
      for (int i = 0; i < loops; i++) {
        long x = mix64(i * 17L + 1);
        long y = mix64(i * 31L + 7);
        long got = asLong(invoke(multiplyHigh, null, x, y));
        BigInteger prod = BigInteger.valueOf(x).multiply(BigInteger.valueOf(y));
        long exp = prod.shiftRight(64).longValue();
        checkEq("Math.multiplyHigh.r" + (i & 1), got, exp);
      }
    } catch (Throwable t) {
      System.out.println("SKIP Math.multiplyHigh: " + t.getClass().getSimpleName());
    }
  }

  private static void testFloatDoubleIntrinsics(int iters) {
    for (int i = 0; i < iters; i++) {
      float f = (i & 1) == 0 ? (i * 1.25f) : Float.intBitsToFloat(0x7fc00000 | (i & 0x1fff));
      double d = (i & 1) == 0 ? (i * -2.5) : Double.longBitsToDouble(0x7ff8000000000000L | (i & 0x1ffffL));

      boolean expFNaN = (f != f);
      boolean expDNaN = (d != d);
      checkTrue("Float.isNaN.r" + (i & 1), Float.isNaN(f) == expFNaN);
      checkTrue("Double.isNaN.r" + (i & 1), Double.isNaN(d) == expDNaN);

      boolean expFInf = (f == Float.POSITIVE_INFINITY || f == Float.NEGATIVE_INFINITY);
      boolean expDInf = (d == Double.POSITIVE_INFINITY || d == Double.NEGATIVE_INFINITY);
      checkTrue("Float.isInfinite.r" + (i & 1), Float.isInfinite(f) == expFInf);
      checkTrue("Double.isInfinite.r" + (i & 1), Double.isInfinite(d) == expDInf);

      int fb = Float.floatToRawIntBits(f);
      float fr = Float.intBitsToFloat(fb);
      checkTrue("Float.bits.roundtrip.r" + (i & 1),
                Float.floatToRawIntBits(fr) == fb);

      long db = Double.doubleToRawLongBits(d);
      double dr = Double.longBitsToDouble(db);
      checkTrue("Double.bits.roundtrip.r" + (i & 1),
                Double.doubleToRawLongBits(dr) == db);
    }
  }

  private static void testStringIntrinsics(int iters) {
    String base = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    for (int i = 0; i < iters; i++) {
      int len = 1 + (i % base.length());
      String s = base.substring(0, len);
      String t = (i & 1) == 0 ? s : new String(s);
      String u = base.substring(base.length() - len);

      checkEq("String.length.r" + (i & 1), s.length(), len);
      checkTrue("String.isEmpty.r" + (i & 1), s.isEmpty() == (len == 0));
      checkEq("String.charAt.r" + (i & 1), s.charAt(0), base.charAt(0));

      checkTrue("String.equals.self.r" + (i & 1), s.equals(s));
      checkTrue("String.equals.same.r" + (i & 1), s.equals(t));
      checkTrue("String.equals.diff.r" + (i & 1), s.equals(u) == stringEqualsRef(s, u));

      checkEq("String.compareTo.r" + (i & 1), s.compareTo(u), stringCompareToRef(s, u));

      int idx = i % s.length();
      int ch = s.charAt(idx);
      checkEq("String.indexOf.ch.r" + (i & 1), s.indexOf(ch), indexOfCharRef(s, ch, 0));
      checkEq("String.indexOf.chFrom.r" + (i & 1),
              s.indexOf(ch, idx),
              indexOfCharRef(s, ch, idx));

      String needle = s.substring(Math.max(0, idx - 1), Math.min(s.length(), idx + 1));
      checkEq("String.indexOf.str.r" + (i & 1),
              s.indexOf(needle),
              indexOfStringRef(s, needle, 0));
      checkEq("String.indexOf.strFrom.r" + (i & 1),
              s.indexOf(needle, idx),
              indexOfStringRef(s, needle, idx));
    }
  }

  private static void testStringExtraIntrinsics() {
    String ascii = "intrinsics-ASCII-12345";
    char[] out = new char[ascii.length()];
    ascii.getChars(0, ascii.length(), out, 0);
    checkTrue("String.getChars.ascii", ascii.equals(new String(out)));

    String unicode = "ab\u4f60\u597d\u03c0";
    char[] out2 = new char[unicode.length()];
    unicode.getChars(0, unicode.length(), out2, 0);
    checkTrue("String.getChars.unicode", unicode.equals(new String(out2)));
    checkEq("String.indexOf.unicode", unicode.indexOf('\u4f60'), 2);
  }

  private static void testArrayCopyIntrinsics(int iters) {
    byte[] b1 = new byte[256];
    byte[] b2 = new byte[256];
    char[] c1 = new char[128];
    char[] c2 = new char[128];
    int[] i1 = new int[128];
    int[] i2 = new int[128];
    Object[] o1 = new Object[64];
    Object[] o2 = new Object[64];

    for (int i = 0; i < b1.length; i++)
      b1[i] = (byte)i;
    for (int i = 0; i < c1.length; i++)
      c1[i] = (char)('A' + (i % 26));
    for (int i = 0; i < i1.length; i++)
      i1[i] = i * 3;
    for (int i = 0; i < o1.length; i++)
      o1[i] = "v" + i;

    for (int r = 0; r < Math.max(1, iters / 256); r++) {
      System.arraycopy(b1, 0, b2, 0, b1.length);
      System.arraycopy(c1, 0, c2, 0, c1.length);
      System.arraycopy(i1, 0, i2, 0, i1.length);
      System.arraycopy(o1, 0, o2, 0, o1.length);

      checkTrue("arraycopy.byte.r" + (r & 1), eqArray(b1, b2));
      checkTrue("arraycopy.char.r" + (r & 1), eqArray(c1, c2));
      checkTrue("arraycopy.int.r" + (r & 1), eqArray(i1, i2));
      checkTrue("arraycopy.obj.r" + (r & 1), eqArray(o1, o2));
    }
  }

  private static void testArrayCopyEdgeCases() {
    char[] overlap1 = "abcdef".toCharArray();
    System.arraycopy(overlap1, 0, overlap1, 2, 4);
    checkTrue("arraycopy.char.overlap.fwd", "ababcd".equals(new String(overlap1)));

    char[] overlap2 = "abcdef".toCharArray();
    System.arraycopy(overlap2, 2, overlap2, 0, 4);
    checkTrue("arraycopy.char.overlap.rev", "cdefef".equals(new String(overlap2)));

    char[] big = new char[256];
    char[] big2 = new char[256];
    for (int i = 0; i < big.length; i++) {
      big[i] = (char)('A' + (i % 26));
    }
    System.arraycopy(big, 0, big2, 0, big.length);
    checkTrue("arraycopy.char.big", eqArray(big, big2));

    boolean threw = false;
    try {
      System.arraycopy(new int[2], 0, new int[1], 0, 2);
    } catch (ArrayIndexOutOfBoundsException expected) {
      threw = true;
    }
    checkTrue("arraycopy.bounds", threw);

    boolean typeFail = false;
    try {
      Object[] src = new String[] {"a", "b"};
      Object[] dst = new Integer[2];
      System.arraycopy(src, 0, dst, 0, 2);
    } catch (ArrayStoreException expected) {
      typeFail = true;
    }
    checkTrue("arraycopy.typemismatch", typeFail);
  }

  private static void testObjectArrayOperations() {
    Object[] base = new Object[] {"a", Integer.valueOf(1), null, "b"};
    Object[] clone = base.clone();
    checkTrue("objarray.clone.eq", eqArray(base, clone));

    String[] s = new String[] {"x", "y", null};
    Object[] o = s;
    checkTrue("objarray.covariant.read", "x".equals(o[0]));
    boolean storeFail = false;
    try {
      o[1] = Integer.valueOf(7);
    } catch (ArrayStoreException expected) {
      storeFail = true;
    }
    checkTrue("objarray.covariant.store.fail", storeFail);
    checkTrue("objarray.covariant.store.nochange", "y".equals(o[1]));

    Object[] dstO = new Object[3];
    System.arraycopy(s, 0, dstO, 0, s.length);
    checkTrue("objarray.arraycopy.widen", eqArray(s, dstO));

    Object[] srcOk = new Object[] {"a", "b"};
    String[] dstS = new String[2];
    System.arraycopy(srcOk, 0, dstS, 0, 2);
    checkTrue("objarray.arraycopy.narrow.ok", "a".equals(dstS[0]) && "b".equals(dstS[1]));

    Object[] srcBad = new Object[] {"ok", Integer.valueOf(3)};
    String[] dstS2 = new String[2];
    boolean narrowFail = false;
    try {
      System.arraycopy(srcBad, 0, dstS2, 0, 2);
    } catch (ArrayStoreException expected) {
      narrowFail = true;
    }
    checkTrue("objarray.arraycopy.narrow.fail", narrowFail);
    checkTrue("objarray.arraycopy.narrow.partial",
              "ok".equals(dstS2[0]) && dstS2[1] == null);

    Object[] ov1 = new Object[] {"a", "b", "c", "d", "e"};
    System.arraycopy(ov1, 0, ov1, 1, 4);
    checkTrue("objarray.arraycopy.overlap.fwd",
              eqArray(ov1, new Object[] {"a", "a", "b", "c", "d"}));
    Object[] ov2 = new Object[] {"a", "b", "c", "d", "e"};
    System.arraycopy(ov2, 1, ov2, 0, 4);
    checkTrue("objarray.arraycopy.overlap.rev",
              eqArray(ov2, new Object[] {"b", "c", "d", "e", "e"}));

    expectThrows("objarray.arraycopy.nullsrc", NullPointerException.class,
                 () -> System.arraycopy(null, 0, new Object[1], 0, 1));
    expectThrows("objarray.arraycopy.nulldst", NullPointerException.class,
                 () -> System.arraycopy(new Object[1], 0, null, 0, 1));
    expectThrows("objarray.arraycopy.negsrc", ArrayIndexOutOfBoundsException.class,
                 () -> System.arraycopy(new Object[1], -1, new Object[1], 0, 1));
    expectThrows("objarray.arraycopy.neglen", ArrayIndexOutOfBoundsException.class,
                 () -> System.arraycopy(new Object[1], 0, new Object[1], 0, -1));
    expectThrows("objarray.arraycopy.oob", ArrayIndexOutOfBoundsException.class,
                 () -> System.arraycopy(new Object[1], 0, new Object[1], 0, 2));

    expectThrows("objarray.arraycopy.primToObj", ArrayStoreException.class,
                 () -> System.arraycopy(new int[] {1}, 0, new Object[1], 0, 1));
    expectThrows("objarray.arraycopy.objToPrim", ArrayStoreException.class,
                 () -> System.arraycopy(new Object[] {"x"}, 0, new int[1], 0, 1));

    CharSequence[] cs = new CharSequence[2];
    String[] ss = new String[] {"p", "q"};
    System.arraycopy(ss, 0, cs, 0, 2);
    checkTrue("objarray.arraycopy.interface",
              "p".contentEquals(cs[0]) && "q".contentEquals(cs[1]));
  }

  private static void testThreadIntrinsics() {
    Thread t = Thread.currentThread();
    checkTrue("Thread.currentThread.nonNull", t != null);
    checkTrue("Thread.currentThread.name.nonEmpty", t.getName() != null && !t.getName().isEmpty());

    boolean wasInterrupted = Thread.interrupted();
    Thread.currentThread().interrupt();
    boolean nowInterrupted = Thread.interrupted();
    boolean cleared = Thread.interrupted();
    checkTrue("Thread.interrupted.clears", (!wasInterrupted) && nowInterrupted && !cleared);
  }

  private static void testCrc32IntrinsicsNoLog(int iters) {
    byte[] data = ("intrinsics-crc32-" + iters).getBytes(StandardCharsets.UTF_8);
    long exp = crc32Ref(data);
    long got = 0;
    CRC32 crc = new CRC32();
    for (int r = 0; r < Math.max(1, iters / 1024); r++) {
      crc.reset();
      crc.update(data, 0, data.length);
      got = crc.getValue();
    }
    checkEq("CRC32.updateBytes", got, exp);
  }

  private static void testCrc32ByteBufferNoLog() {
    byte[] data = "crc32-bytebuffer-test".getBytes(StandardCharsets.UTF_8);
    long exp = crc32Ref(data);
    CRC32 crc = new CRC32();

    ByteBuffer heap = ByteBuffer.wrap(data);
    crc.update(heap);
    checkEq("CRC32.updateByteBuffer.heap", crc.getValue(), exp);

    crc.reset();
    ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
    direct.put(data);
    direct.flip();
    crc.update(direct);
    checkEq("CRC32.updateByteBuffer.direct", crc.getValue(), exp);

    crc.reset();
    crc.update(0x42);
    long single = crc.getValue();
    crc.reset();
    crc.update(new byte[] {0x42});
    checkEq("CRC32.update.singleByte", single, crc.getValue());
  }

  private static void testCrc32Intrinsics(int iters) {
    byte[] data = ("intrinsics-crc32-" + iters).getBytes(StandardCharsets.UTF_8);
    long exp = crc32Ref(data);
    long got = 0;
    CRC32 crc = new CRC32();
    System.out.println("CRC32.updateBytes input len=" + data.length +
                       " exp=" + hex32(exp));
    for (int r = 0; r < Math.max(1, iters / 1024); r++) {
      crc.reset();
      crc.update(data, 0, data.length);
      got = crc.getValue();
    }
    System.out.println("CRC32.updateBytes got=" + hex32(got));
    checkEq("CRC32.updateBytes.log", got, exp);
  }

  private static void testCrc32ByteBuffer() {
    byte[] data = "crc32-bytebuffer-test".getBytes(StandardCharsets.UTF_8);
    long exp = crc32Ref(data);
    CRC32 crc = new CRC32();
    System.out.println("CRC32.ByteBuffer input len=" + data.length +
                       " exp=" + hex32(exp));

    ByteBuffer heap = ByteBuffer.wrap(data);
    crc.update(heap);
    System.out.println("CRC32.updateByteBuffer.heap got=" + hex32(crc.getValue()));
    checkEq("CRC32.updateByteBuffer.heap.log", crc.getValue(), exp);

    crc.reset();
    ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
    direct.put(data);
    direct.flip();
    crc.update(direct);
    System.out.println("CRC32.updateByteBuffer.direct got=" + hex32(crc.getValue()));
    checkEq("CRC32.updateByteBuffer.direct.log", crc.getValue(), exp);

    crc.reset();
    int singleByte = 0x42;
    crc.update(singleByte);
    long single = crc.getValue();
    long expSingle = crc32Ref(new byte[] {(byte) singleByte});
    System.out.println("CRC32.update.singleByte byte=0x42 got=" + hex32(single) +
                       " exp=" + hex32(expSingle));
    checkEq("CRC32.update.singleByte.log", single, expSingle);
  }

  private static void testMethodHandlePolymorphicOptional() {
    try {
      MethodHandles.Lookup l = MethodHandles.lookup();
      MethodHandle mh = l.findStatic(IntrinsicsTest.class,
                                     "mhAdd3",
                                     MethodType.methodType(int.class, int.class,
                                                           int.class, int.class));
      int got = (int) mh.invokeExact(1, 2, 3);
      checkEq("MethodHandle.invokeExact", got, 6);
    } catch (Throwable t) {
      System.out.println("SKIP MethodHandle.invokeExact: " + t.getClass().getSimpleName());
    }

    try {
      Method m = Class.forName("java.lang.invoke.VarHandle").getDeclaredMethod("fullFence");
      m.invoke(null);
      System.out.println("OK   VarHandle.fullFence");
      CTR.incPass();
    } catch (Throwable t) {
      System.out.println("SKIP VarHandle.fullFence: " + t.getClass().getSimpleName());
    }
  }

  private static void testVarHandleIntrinsicsOptional() {
    try {
      MethodHandles.Lookup l = MethodHandles.lookup();
      VarHandle vhI = l.findVarHandle(VarHandleHolder.class, "i", int.class);
      VarHandle vhL = l.findVarHandle(VarHandleHolder.class, "l", long.class);
      VarHandle vhO = l.findVarHandle(VarHandleHolder.class, "o", Object.class);
      VarHandle vhSI = l.findStaticVarHandle(VarHandleHolder.class, "sInt", int.class);
      VarHandle vhSL = l.findStaticVarHandle(VarHandleHolder.class, "sLong", long.class);
      VarHandle vhSO = l.findStaticVarHandle(VarHandleHolder.class, "sObj", Object.class);
      VarHandle vhiArr = MethodHandles.arrayElementVarHandle(int[].class);
      VarHandle vhlArr = MethodHandles.arrayElementVarHandle(long[].class);
      VarHandle vhoArr = MethodHandles.arrayElementVarHandle(Object[].class);

      VarHandleHolder h = new VarHandleHolder();

      vhI.set(h, 1);
      checkEq("VarHandle.inst.int.get", asInt(vhI.get(h)), 1);
      vhI.setVolatile(h, 2);
      checkEq("VarHandle.inst.int.getVolatile", asInt(vhI.getVolatile(h)), 2);
      checkTrue("VarHandle.inst.int.cas",
                asBool(vhI.compareAndSet(h, 2, 3)));
      int prev = asInt(vhI.getAndAdd(h, 5));
      checkEq("VarHandle.inst.int.getAndAdd.prev", prev, 3);
      checkEq("VarHandle.inst.int.getAndAdd.new", asInt(vhI.get(h)), 8);
      int prevEx = asInt(vhI.compareAndExchange(h, 8, 9));
      checkEq("VarHandle.inst.int.cae", prevEx, 8);
      checkEq("VarHandle.inst.int.cae.new", asInt(vhI.get(h)), 9);

      vhL.set(h, 10L);
      checkEq("VarHandle.inst.long.get", asLong(vhL.get(h)), 10L);
      long lPrev = asLong(vhL.getAndAdd(h, 5L));
      checkEq("VarHandle.inst.long.getAndAdd.prev", lPrev, 10L);
      checkEq("VarHandle.inst.long.getAndAdd.new", asLong(vhL.get(h)), 15L);

      vhO.set(h, "a");
      checkTrue("VarHandle.inst.obj.cas",
                asBool(vhO.compareAndSet(h, "a", "b")));
      checkTrue("VarHandle.inst.obj.get",
                "b".equals((String) vhO.get(h)));

      vhSI.set(7);
      checkEq("VarHandle.static.int.get", asInt(vhSI.get()), 7);
      vhSL.set(11L);
      checkEq("VarHandle.static.long.get", asLong(vhSL.get()), 11L);
      vhSO.set("s");
      checkTrue("VarHandle.static.obj.get", "s".equals((String) vhSO.get()));

      int[] ia = new int[4];
      vhiArr.set(ia, 2, 42);
      checkEq("VarHandle.array.int.get", asInt(vhiArr.get(ia, 2)), 42);
      int aPrev = asInt(vhiArr.getAndAdd(ia, 2, 3));
      checkEq("VarHandle.array.int.getAndAdd.prev", aPrev, 42);
      checkEq("VarHandle.array.int.getAndAdd.new", asInt(vhiArr.get(ia, 2)), 45);

      long[] la = new long[3];
      vhlArr.set(la, 1, 100L);
      checkEq("VarHandle.array.long.get", asLong(vhlArr.get(la, 1)), 100L);

      Object[] oa = new Object[2];
      vhoArr.set(oa, 0, "x");
      boolean ok = asBool(vhoArr.compareAndSet(oa, 0, "x", "y"));
      checkTrue("VarHandle.array.obj.cas", ok);
      checkTrue("VarHandle.array.obj.get", "y".equals((String) vhoArr.get(oa, 0)));
    } catch (Throwable t) {
      System.out.println("SKIP VarHandle.*: " + t.getClass().getSimpleName());
    }
  }

  private static void testUnsafeIntrinsicsOptional() {
    try {
      Object unsafe = getUnsafe();
      Class<?> uc = unsafe.getClass();
      UnsafeHolder h = new UnsafeHolder();

      Method objectFieldOffset = requireMethod(uc, "objectFieldOffset", Field.class);
      Field fi = UnsafeHolder.class.getDeclaredField("i");
      Field fl = UnsafeHolder.class.getDeclaredField("l");
      Field fo = UnsafeHolder.class.getDeclaredField("o");
      long offI = asLong(invoke(objectFieldOffset, unsafe, fi));
      long offL = asLong(invoke(objectFieldOffset, unsafe, fl));
      long offO = asLong(invoke(objectFieldOffset, unsafe, fo));

      Method putInt = requireMethod(uc, "putInt", Object.class, long.class, int.class);
      Method getInt = requireMethod(uc, "getInt", Object.class, long.class);
      int curI = 123;
      invoke(putInt, unsafe, h, offI, curI);
      checkEq("Unsafe.getInt", asInt(invoke(getInt, unsafe, h, offI)), curI);

      Method putLong = requireMethod(uc, "putLong", Object.class, long.class, long.class);
      Method getLong = requireMethod(uc, "getLong", Object.class, long.class);
      invoke(putLong, unsafe, h, offL, 0x1234567890L);
      checkEq("Unsafe.getLong", asLong(invoke(getLong, unsafe, h, offL)), 0x1234567890L);

      Method putObject = requireMethod(uc, "putObject", Object.class, long.class, Object.class);
      Method getObject = requireMethod(uc, "getObject", Object.class, long.class);
      invoke(putObject, unsafe, h, offO, "u");
      checkTrue("Unsafe.getObject", "u".equals((String) invoke(getObject, unsafe, h, offO)));

      Method putIntVolatile = findMethod(uc, "putIntVolatile", Object.class, long.class, int.class);
      Method getIntVolatile = findMethod(uc, "getIntVolatile", Object.class, long.class);
      if (putIntVolatile != null && getIntVolatile != null) {
        curI = 321;
        invoke(putIntVolatile, unsafe, h, offI, curI);
        checkEq("Unsafe.getIntVolatile", asInt(invoke(getIntVolatile, unsafe, h, offI)), curI);
      }

      Method casInt = firstMethod(uc, "compareAndSwapInt", "compareAndSetInt",
                                  Object.class, long.class, int.class, int.class);
      if (casInt != null) {
        boolean ok = asBool(invoke(casInt, unsafe, h, offI, curI, curI + 1));
        checkTrue("Unsafe.compareAndSetInt", ok);
        if (ok) {
          curI = curI + 1;
        }
      }

      Method casObj = firstMethod(uc, "compareAndSwapObject", "compareAndSetObject",
                                  Object.class, long.class, Object.class, Object.class);
      if (casObj != null) {
        boolean ok = asBool(invoke(casObj, unsafe, h, offO, "u", "v"));
        checkTrue("Unsafe.compareAndSetObject", ok);
      }

      Method getAndAddInt = findMethod(uc, "getAndAddInt", Object.class, long.class, int.class);
      if (getAndAddInt != null) {
        int prev = asInt(invoke(getAndAddInt, unsafe, h, offI, 5));
        checkEq("Unsafe.getAndAddInt.prev", prev, curI);
        curI = curI + 5;
      }

      Method getAndSetInt = findMethod(uc, "getAndSetInt", Object.class, long.class, int.class);
      if (getAndSetInt != null) {
        int prev = asInt(invoke(getAndSetInt, unsafe, h, offI, 777));
        checkEq("Unsafe.getAndSetInt.prev", prev, curI);
        curI = 777;
        checkEq("Unsafe.getAndSetInt.new", asInt(invoke(getInt, unsafe, h, offI)), curI);
      }
    } catch (Throwable t) {
      System.out.println("SKIP Unsafe.*: " + t.getClass().getSimpleName());
    }
  }

  private static void testReachabilityFenceOptional() {
    try {
      Method m = Class.forName("java.lang.ref.Reference")
                      .getDeclaredMethod("reachabilityFence", Object.class);
      m.invoke(null, new Object());
      System.out.println("OK   Reference.reachabilityFence");
      CTR.incPass();
    } catch (Throwable t) {
      System.out.println("SKIP Reference.reachabilityFence: " + t.getClass().getSimpleName());
    }
  }

  private static int mhAdd3(int a, int b, int c) {
    return a + b + c;
  }

  private static final class VarHandleHolder {
    static int sInt;
    static long sLong;
    static Object sObj;
    int i;
    long l;
    Object o;
  }

  private static final class UnsafeHolder {
    int i;
    long l;
    Object o;
  }

  // ===== Helpers / reference implementations =====

  private static int parseInt(String s, int def) {
    try {
      return Integer.parseInt(s.trim());
    } catch (Throwable t) {
      return def;
    }
  }

  private static void checkEq(String name, int got, int exp) {
    if (QUIET) {
      if (got != exp) {
        System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
        CTR.incFail();
      } else {
        CTR.incPass();
      }
    } else {
      TestSupport.checkEq(name, got, exp, CTR);
    }
  }

  private static void checkEq(String name, long got, long exp) {
    if (QUIET) {
      if (got != exp) {
        System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
        CTR.incFail();
      } else {
        CTR.incPass();
      }
    } else {
      TestSupport.checkEq(name, got, exp, CTR);
    }
  }

  private static void checkTrue(String name, boolean ok) {
    if (QUIET) {
      if (!ok) {
        System.out.println("FAIL " + name);
        CTR.incFail();
      } else {
        CTR.incPass();
      }
    } else {
      TestSupport.checkTrue(name, ok, CTR);
    }
  }

  private static void checkApprox(String name, double got, double exp, double eps) {
    if (QUIET) {
      if (Math.abs(got - exp) > eps) {
        System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
        CTR.incFail();
      } else {
        CTR.incPass();
      }
    } else {
      TestSupport.checkApprox(name, got, exp, eps, CTR);
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
    v = v ^ (v >>> 31);
    return v;
  }

  private static int cmpRef(int a, int b) { return (a < b) ? -1 : (a > b) ? 1 : 0; }
  private static int cmpRef(long a, long b) { return (a < b) ? -1 : (a > b) ? 1 : 0; }

  private static int rotlRef(int x, int s) {
    int n = s & 31;
    return (x << n) | (x >>> (32 - n));
  }

  private static int rotrRef(int x, int s) {
    int n = s & 31;
    return (x >>> n) | (x << (32 - n));
  }

  private static long rotlRef(long x, int s) {
    int n = s & 63;
    return (x << n) | (x >>> (64 - n));
  }

  private static long rotrRef(long x, int s) {
    int n = s & 63;
    return (x >>> n) | (x << (64 - n));
  }

  private static int signumRef(int x) { return (x > 0) ? 1 : (x < 0) ? -1 : 0; }
  private static int signumRef(long x) { return (x > 0) ? 1 : (x < 0) ? -1 : 0; }

  private static float absRef(float x) { return x < 0f ? -x : x; }
  private static double absRef(double x) { return x < 0d ? -x : x; }

  private static double copySignRef(double mag, double sign) {
    long m = Double.doubleToRawLongBits(mag);
    long s = Double.doubleToRawLongBits(sign);
    m &= 0x7fffffffffffffffL;
    m |= (s & 0x8000000000000000L);
    return Double.longBitsToDouble(m);
  }

  private static float copySignRef(float mag, float sign) {
    int m = Float.floatToRawIntBits(mag);
    int s = Float.floatToRawIntBits(sign);
    m &= 0x7fffffff;
    m |= (s & 0x80000000);
    return Float.intBitsToFloat(m);
  }

  private static double signumRef(double x) {
    if (x != x)
      return Double.NaN;
    if (x == 0d)
      return x;
    return x > 0d ? 1d : -1d;
  }

  private static float signumRef(float x) {
    if (x != x)
      return Float.NaN;
    if (x == 0f)
      return x;
    return x > 0f ? 1f : -1f;
  }

  private static long roundDoubleRef(double x) {
    if (x != x)
      return 0L;
    if (x >= Long.MAX_VALUE)
      return Long.MAX_VALUE;
    if (x <= Long.MIN_VALUE)
      return Long.MIN_VALUE;
    return (long) (x + (x >= 0 ? 0.5d : -0.5d));
  }

  private static int roundFloatRef(float x) {
    if (x != x)
      return 0;
    if (x >= Integer.MAX_VALUE)
      return Integer.MAX_VALUE;
    if (x <= Integer.MIN_VALUE)
      return Integer.MIN_VALUE;
    return (int) (x + (x >= 0 ? 0.5f : -0.5f));
  }

  private static int reverseBytes32Ref(int x) {
    return ((x >>> 24) & 0xff) |
           ((x >>> 8) & 0xff00) |
           ((x << 8) & 0xff0000) |
           ((x << 24));
  }

  private static long reverseBytes64Ref(long x) {
    return ((x >>> 56) & 0xffL) |
           ((x >>> 40) & 0xff00L) |
           ((x >>> 24) & 0xff0000L) |
           ((x >>> 8) & 0xff000000L) |
           ((x << 8) & 0xff00000000L) |
           ((x << 24) & 0xff0000000000L) |
           ((x << 40) & 0xff000000000000L) |
           ((x << 56));
  }

  private static int reverseBits32Ref(int x) {
    int v = x;
    int r = 0;
    for (int i = 0; i < 32; i++) {
      r = (r << 1) | (v & 1);
      v >>>= 1;
    }
    return r;
  }

  private static long reverseBits64Ref(long x) {
    long v = x;
    long r = 0;
    for (int i = 0; i < 64; i++) {
      r = (r << 1) | (v & 1L);
      v >>>= 1;
    }
    return r;
  }

  private static int bitCount32Ref(int x) {
    int v = x;
    int c = 0;
    while (v != 0) {
      c += (v & 1);
      v >>>= 1;
    }
    return c;
  }

  private static int bitCount64Ref(long x) {
    long v = x;
    int c = 0;
    while (v != 0) {
      c += (int)(v & 1L);
      v >>>= 1;
    }
    return c;
  }

  private static int highestOneBit32Ref(int x) {
    int v = x;
    if (v == 0)
      return 0;
    int r = 1;
    while ((v >>>= 1) != 0)
      r <<= 1;
    return r;
  }

  private static int lowestOneBit32Ref(int x) { return x & -x; }

  private static long highestOneBit64Ref(long x) {
    long v = x;
    if (v == 0L)
      return 0L;
    long r = 1L;
    while ((v >>>= 1) != 0L)
      r <<= 1;
    return r;
  }

  private static long lowestOneBit64Ref(long x) { return x & -x; }

  private static int leadingZeros32Ref(int x) {
    if (x == 0)
      return 32;
    int n = 0;
    int v = x;
    while ((v & 0x80000000) == 0) {
      n++;
      v <<= 1;
    }
    return n;
  }

  private static int trailingZeros32Ref(int x) {
    if (x == 0)
      return 32;
    int n = 0;
    int v = x;
    while ((v & 1) == 0) {
      n++;
      v >>>= 1;
    }
    return n;
  }

  private static int leadingZeros64Ref(long x) {
    if (x == 0L)
      return 64;
    int n = 0;
    long v = x;
    while ((v & 0x8000000000000000L) == 0L) {
      n++;
      v <<= 1;
    }
    return n;
  }

  private static int trailingZeros64Ref(long x) {
    if (x == 0L)
      return 64;
    int n = 0;
    long v = x;
    while ((v & 1L) == 0L) {
      n++;
      v >>>= 1;
    }
    return n;
  }

  private static long unsignedDiv64Ref(long x, long y) {
    if (y < 0L) {
      return (Long.compareUnsigned(x, y) < 0) ? 0L : 1L;
    }
    if (x >= 0L) {
      return x / y;
    }
    long q = ((x >>> 1) / y) << 1;
    long r = x - q * y;
    if (Long.compareUnsigned(r, y) >= 0) {
      q++;
    }
    return q;
  }

  private static long unsignedRem64Ref(long x, long y) {
    long q = unsignedDiv64Ref(x, y);
    return x - q * y;
  }

  private static boolean stringEqualsRef(String a, String b) {
    if (a == b)
      return true;
    if (a == null || b == null)
      return false;
    if (a.length() != b.length())
      return false;
    for (int i = 0; i < a.length(); i++) {
      if (a.charAt(i) != b.charAt(i))
        return false;
    }
    return true;
  }

  private static int stringCompareToRef(String a, String b) {
    int n = Math.min(a.length(), b.length());
    for (int i = 0; i < n; i++) {
      char ca = a.charAt(i);
      char cb = b.charAt(i);
      if (ca != cb)
        return ca - cb;
    }
    return a.length() - b.length();
  }

  private static int indexOfCharRef(String s, int ch, int from) {
    for (int i = Math.max(0, from); i < s.length(); i++) {
      if (s.charAt(i) == ch)
        return i;
    }
    return -1;
  }

  private static int indexOfStringRef(String s, String needle, int from) {
    if (needle.length() == 0)
      return Math.max(0, from);
    int start = Math.max(0, from);
    int limit = s.length() - needle.length();
    for (int i = start; i <= limit; i++) {
      boolean ok = true;
      for (int j = 0; j < needle.length(); j++) {
        if (s.charAt(i + j) != needle.charAt(j)) {
          ok = false;
          break;
        }
      }
      if (ok)
        return i;
    }
    return -1;
  }

  private static boolean eqArray(byte[] a, byte[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i])
        return false;
    }
    return true;
  }

  private static boolean eqArray(char[] a, char[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i])
        return false;
    }
    return true;
  }

  private static boolean eqArray(int[] a, int[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i])
        return false;
    }
    return true;
  }

  private static boolean eqArray(Object[] a, Object[] b) {
    if (a.length != b.length)
      return false;
    for (int i = 0; i < a.length; i++) {
      Object ea = a[i];
      Object eb = b[i];
      if (ea == null ? eb != null : !ea.equals(eb))
        return false;
    }
    return true;
  }

  private static Object getUnsafe() throws Throwable {
    Throwable last = null;
    for (String name : new String[] {"jdk.internal.misc.Unsafe", "sun.misc.Unsafe"}) {
      try {
        Class<?> c = Class.forName(name);
        Field f = c.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return f.get(null);
      } catch (Throwable t) {
        last = t;
      }
    }
    if (last != null)
      throw last;
    throw new RuntimeException("Unsafe not found");
  }

  private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
    try {
      Method m = cls.getDeclaredMethod(name, params);
      m.setAccessible(true);
      return m;
    } catch (Throwable ignored) {
    }
    try {
      Method m = cls.getMethod(name, params);
      m.setAccessible(true);
      return m;
    } catch (Throwable ignored) {
    }
    return null;
  }

  private static Method requireMethod(Class<?> cls, String name, Class<?>... params)
      throws NoSuchMethodException {
    Method m = findMethod(cls, name, params);
    if (m == null)
      throw new NoSuchMethodException(name);
    return m;
  }

  private static Method firstMethod(Class<?> cls, String name1, String name2,
                                    Class<?>... params) {
    Method m = findMethod(cls, name1, params);
    if (m != null)
      return m;
    return findMethod(cls, name2, params);
  }

  private static Object invoke(Method m, Object target, Object... args) throws Throwable {
    try {
      return m.invoke(target, args);
    } catch (java.lang.reflect.InvocationTargetException ite) {
      throw ite.getCause();
    }
  }

  private static int asInt(Object v) {
    return ((Number) v).intValue();
  }

  private static long asLong(Object v) {
    return ((Number) v).longValue();
  }

  private static boolean asBool(Object v) {
    return ((Boolean) v).booleanValue();
  }

  private static void expectThrows(String name,
                                   Class<? extends Throwable> ex,
                                   Runnable r) {
    boolean ok = false;
    try {
      r.run();
    } catch (Throwable t) {
      ok = ex.isInstance(t);
    }
    checkTrue(name, ok);
  }

  private static String hex32(long v) {
    return "0x" + Long.toHexString(v & 0xffffffffL);
  }

  private static long crc32Ref(byte[] data) {
    int crc = 0xffffffff;
    for (byte b : data) {
      crc ^= (b & 0xff);
      for (int i = 0; i < 8; i++) {
        int mask = -(crc & 1);
        crc = (crc >>> 1) ^ (0xedb88320 & mask);
      }
    }
    return ((long)crc ^ 0xffffffffL) & 0xffffffffL;
  }
}
