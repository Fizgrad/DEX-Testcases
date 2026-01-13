 

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.CRC32;

public final class IntrinsicsTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();

  private static final int DEFAULT_ITERS = 20000;

  private IntrinsicsTest() {}

  public static void main(String[] args) {
    System.out.println("=== IntrinsicsTest starting ===");
    int iters = DEFAULT_ITERS;
    for (String s : args) {
      if ("--short".equals(s)) {
        iters = 4000;
      } else if ("--full".equals(s)) {
        iters = 100000;
      } else if (s.startsWith("--iters=")) {
        iters = parseInt(s.substring(s.indexOf('=') + 1), iters);
      }
    }
    if (iters < 1)
      iters = 1;

    JitSupport.requestJitCompilation(IntrinsicsTest.class);
    warmUp(2000);

    testIntegerIntrinsics(iters);
    testLongIntrinsics(iters);
    testMathIntrinsics(iters);
    testFloatDoubleIntrinsics(iters);
    testStringIntrinsics(iters);
    testArrayCopyIntrinsics(iters);
    testThreadIntrinsics();
    testCrc32Intrinsics(iters);
    testMethodHandlePolymorphicOptional();

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

  private static void testCrc32Intrinsics(int iters) {
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

  private static void testMethodHandlePolymorphicOptional() {
    try {
      MethodHandles.Lookup l = MethodHandles.lookup();
      MethodHandle mh = l.findStatic(IntrinsicsTest.class,
                                     "mhAdd3",
                                     MethodType.methodType(int.class, int.class, int.class));
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

  private static int mhAdd3(int a, int b, int c) {
    return a + b + c;
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
    TestSupport.checkEq(name, got, exp, CTR);
  }

  private static void checkEq(String name, long got, long exp) {
    TestSupport.checkEq(name, got, exp, CTR);
  }

  private static void checkTrue(String name, boolean ok) {
    TestSupport.checkTrue(name, ok, CTR);
  }

  private static void checkApprox(String name, double got, double exp, double eps) {
    TestSupport.checkApprox(name, got, exp, eps, CTR);
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
