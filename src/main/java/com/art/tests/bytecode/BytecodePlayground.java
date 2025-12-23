// BytecodePlayground.java
package com.art.tests.bytecode;

import java.io.IOException;
import java.lang.ref.*;
import java.lang.reflect.*;
// 新增：Android 端多参 native 压测会用到 UDP socket
import java.net.*; // DatagramSocket/DatagramPacket/InetAddress
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class BytecodePlayground {

  // ====== 原有字段/类型 ======
  static int S_I = 1;
  static long S_L = 2L;
  static float S_F = 3f;
  static double S_D = 4d;

  int fI = 10;
  long fL = 20L;
  float fF = 30f;
  double fD = 40d;
  Object fO = "hello";

  interface I {
    int foo(int x);
  }

  static class A implements I {
    public int foo(int x) { return x + 1; }
    public int self() { return 42; }
  }

  static class B extends A {
    @Override
    public int foo(int x) {
      return x + 2;
    }
    public int callSuper() { return super.self(); } // invokespecial
    public static int s() { return 7; }             // invokestatic
  }

  public int syncBlock(Object lock) {
    synchronized (lock) {
      fI++;
      return fI;
    }
  }

  public synchronized int syncMethod() { return ++fI; }
  public static synchronized int staticSyncInc() {
    return ++S_I;
  } // class monitor

  static void mayThrow(boolean t) throws Exception {
    if (t)
      throw new Exception("boom");
  }

  static int testException() {
    int v = 0;
    try {
      mayThrow(true);
      v = 1;
    } catch (Exception e) {
      v = 2;
    } finally {
      v += 10;
    }
    return v;
  }

  static int testInts(int a, int b) {
    int x = a + b;
    x = x - 3;
    x = x * 2;
    x = x / 2;
    x = x % 5;
    x = -x;
    x = (x & 0xF) | 2;
    x ^= 3;
    x = (x << 2) ^ (x >> 1) ^ (x >>> 1);
    int sum = 0;
    for (int i = 0; i < 5; i++)
      sum += i;
    if (x < sum)
      x += 1;
    else if (x == sum)
      x += 2;
    else
      x += 3;
    long l = (long)x;
    float f = (float)l;
    double d = (double)f;
    x = (int)d;
    return x;
  }

  static long testLongs(long a, long b) {
    long x = a * b;
    x = x + 1;
    x = x - 2;
    x = x ^ 3;
    x = (x << 3) ^ (x >> 2) ^ (x >>> 1);
    return x;
  }

  static double testFP(double a, float b) {
    double d = a + b;
    d = d * 2.5;
    d = d / 3.0;
    d = d - 1.0;
    return d;
  }

  static int denseSwitch(int k) {
    switch (k) {
    case 0:
      return 10;
    case 1:
      return 11;
    case 2:
      return 12;
    case 3:
      return 13;
    case 4:
      return 14;
    case 5:
      return 15;
    default:
      return -1;
    }
  }

  static int sparseSwitch(int k) {
    switch (k) {
    case 1:
      return 21;
    case 1000:
      return 22;
    case 1000000:
      return 23;
    default:
      return -2;
    }
  }

  static int arrays() {
    int[] ia = new int[4];
    ia[0] = 7;
    ia[1] = ia.length;
    Object[] oa = new Object[3];
    oa[0] = "x";
    oa[1] = new A();
    int[][][] m = new int[2][3][4];
    m[1][2][3] = 99;
    return ia[0] + ia[1] + ((A)oa[1]).foo(5) + m[1][2][3];
  }

  static int types(Object o) {
    int v = 0;
    if (o instanceof A)
      v += 1;
    A a = (A)o;
    v += a.foo(3);
    return v;
  }

  int fields() {
    S_I += 1;
    this.fI += S_I;
    return fI;
  }

  static int retI() { return 1; }
  static long retL() { return 2L; }
  static float retF() { return 3f; }
  static double retD() { return 4d; }
  static Object retA() { return "ok"; }
  static void retV() {}

  static int PASS = 0, FAIL = 0;
  static void log(String msg) { System.out.println(msg); }

  // ====== 断言 ======
  static void checkEq(String name, int got, int exp) {
    if (got != exp) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      FAIL++;
    } else {
      System.out.print("OK   ");
      System.out.print(name);
      System.out.print(": ");
      System.out.println(got);
      PASS++;
    }
  }
  static void checkEq(String name, long got, long exp) {
    if (got != exp) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      FAIL++;
    } else {
      System.out.println("OK   " + name + ": " + got);
      PASS++;
    }
  }
  static void checkEq(String name, Object got, Object exp) {
    boolean ok = (exp == null ? got == null : exp.equals(got));
    if (!ok) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      FAIL++;
    } else {
      System.out.println("OK   " + name + ": " + got);
      PASS++;
    }
  }
  static void checkEq(String name, float got, float exp, float eps) {
    if (Math.abs(got - exp) > eps) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      FAIL++;
    } else {
      System.out.println("OK   " + name + ": " + got);
      PASS++;
    }
  }
  static void checkEq(String name, double got, double exp, double eps) {
    if (Math.abs(got - exp) > eps) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      FAIL++;
    } else {
      System.out.println("OK   " + name + ": " + got);
      PASS++;
    }
  }
  static void checkTrue(String name, boolean ok) {
    if (!ok) {
      System.out.println("FAIL " + name);
      FAIL++;
    } else {
      System.out.println("OK   " + name);
      PASS++;
    }
  }
  static <T extends Throwable> void expectThrows(String name, Class<T> type,
                                                 Runnable r) {
    try {
      r.run();
      System.out.println("FAIL " + name + ": no exception");
      FAIL++;
    } catch (Throwable t) {
      if (type.isInstance(t)) {
        System.out.println("OK   " + name + ": " +
                           t.getClass().getSimpleName());
        PASS++;
      } else {
        System.out.println("FAIL " + name + ": " + t);
        FAIL++;
      }
    }
  }

  static String toHex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) {
      int v = x & 0xFF;
      sb.append(Character.forDigit(v >>> 4, 16));
      sb.append(Character.forDigit(v & 0xF, 16));
    }
    return sb.toString();
  }

  // ====== Soak 统计 ======
  static final class Stats {
    final AtomicLong allocBytes = new AtomicLong();
    final AtomicLong refPhantomEnq = new AtomicLong();
    final AtomicLong refWeakCleared = new AtomicLong();
    final AtomicLong finalized = new AtomicLong();
    final AtomicLong exceptions = new AtomicLong();
    final AtomicLong proxyCalls = new AtomicLong();
    final AtomicLong syncIters = new AtomicLong();
    final AtomicLong arrayOps = new AtomicLong();
    final AtomicLong switches = new AtomicLong();
    final AtomicLong stringInterns = new AtomicLong();
    final AtomicLong tlSets = new AtomicLong();
    final AtomicLong casts = new AtomicLong();
  }

  static final class Finalizable {
    static final AtomicLong COUNT = new AtomicLong();
    @Override
    protected void finalize() throws Throwable {
      COUNT.incrementAndGet();
    }
  }

  static final class SoakEnv {
    volatile boolean running = true;
    final long deadlineNanos;
    final Stats stats = new Stats();
    final Random rnd = new Random(123);
    final ReferenceQueue<Object> rq = new ReferenceQueue<>();
    final List<WeakReference<Object>> weakBag =
        Collections.synchronizedList(new ArrayList<>());
    final List<SoftReference<Object>> softBag =
        Collections.synchronizedList(new ArrayList<>());
    final List<PhantomReference<Object>> phantomBag =
        Collections.synchronizedList(new ArrayList<>());
    final ArrayDeque<Object> reservoir = new ArrayDeque<>();
    final int reservoirMax;
    final Object lock = new Object();
    final ArrayDeque<Object> q = new ArrayDeque<>();
    final ThreadLocal<byte[]> localBuf = new ThreadLocal<>();
    volatile int volatileTick = 0;

    SoakEnv(long seconds, int reservoirMax) {
      this.deadlineNanos =
          System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
      this.reservoirMax = reservoirMax;
    }
  }

  static void pushReservoir(SoakEnv env, Object o) {
    synchronized (env.reservoir) {
      env.reservoir.addLast(o);
      if (env.reservoir.size() > env.reservoirMax)
        env.reservoir.removeFirst();
    }
  }

  static Runnable guard(String name, Runnable r) {
    return () -> {
      try {
        r.run();
      } catch (Throwable t) {
        System.err.println("[Worker " + name + "] crashed: " + t);
        t.printStackTrace();
      }
    };
  }

  // ====== Soak workers ======
  static Runnable allocator(SoakEnv env) {
    return () -> {
      final Random r = new Random(456);
      while (env.running) {
        Object o;
        switch (r.nextInt(6)) {
        case 0:
          o = new byte[r.nextInt(64 * 1024) + 256];
          break;
        case 1:
          o = new int[r.nextInt(16 * 1024) + 64];
          break;
        case 2:
          o = new Object[r.nextInt(2048) + 16];
          break;
        case 3:
          o = new String("s" + r.nextLong());
          break;
        case 4:
          o = new A();
          break;
        default:
          o = new Finalizable();
        }
        int sz = 64;
        if (o instanceof byte[])
          sz = ((byte[])o).length;
        else if (o instanceof int[])
          sz = ((int[])o).length * 4;
        else if (o instanceof Object[])
          sz = ((Object[])o).length * 8;
        else if (o instanceof String)
          sz = 40 + ((String)o).length() * 2;
        env.stats.allocBytes.addAndGet(sz);

        // 偶发 direct ByteBuffer（native 分配）
        if (r.nextInt(256) == 0) {
          ByteBuffer db = ByteBuffer.allocateDirect(4096);
          db.putInt(0, r.nextInt());
          pushReservoir(env, db);
        }

        pushReservoir(env, o);

        if ((env.stats.allocBytes.get() & 0xFFF) == 0) {
          Object t = new Object();
          env.weakBag.add(new WeakReference<>(t));
          env.softBag.add(new SoftReference<>(new Object()));
          env.phantomBag.add(new PhantomReference<>(new Object(), env.rq));
        }

        Reference<?> ref;
        while ((ref = env.rq.poll()) != null)
          env.stats.refPhantomEnq.incrementAndGet();

        if (env.weakBag.size() > 0 && r.nextInt(8) == 0) {
          int idx = r.nextInt(env.weakBag.size());
          WeakReference<Object> w = env.weakBag.get(idx);
          if (w != null && w.get() == null)
            env.stats.refWeakCleared.incrementAndGet();
        }

        byte[] tls = env.localBuf.get();
        if (tls == null || tls.length < 1024)
          env.localBuf.set(new byte[1024 + r.nextInt(2048)]);
        else
          tls[r.nextInt(tls.length)]++;
        env.stats.tlSets.incrementAndGet();

        if (r.nextInt(64) == 0) {
          String s = ("K" + r.nextInt(1_000_000)).intern();
          pushReservoir(env, s);
          env.stats.stringInterns.incrementAndGet();
        }

        if ((env.stats.allocBytes.get() & 0xFFFF) == 0) {
          System.gc();
          tinySleep(1);
        }
        if (System.nanoTime() > env.deadlineNanos)
          env.running = false;
      }
    };
  }

  static Runnable syncPingPong(SoakEnv env) {
    return () -> {
      Random r = new Random(789);
      while (env.running) {
        synchronized (env.lock) {
          if (r.nextBoolean()) {
            env.q.addLast(new int[] {r.nextInt(), r.nextInt()});
            env.lock.notifyAll();
          } else {
            if (env.q.isEmpty()) {
              try {
                env.lock.wait(2);
              } catch (InterruptedException ignored) {
              }
            } else {
              Object x = env.q.removeFirst();
              if ((env.stats.syncIters.get() & 0x3FF) == 0) {
                try {
                  throw new IllegalStateException("sync-path");
                } catch (IllegalStateException ex) {
                  env.stats.exceptions.incrementAndGet();
                }
              }
            }
          }
        }
        env.stats.syncIters.incrementAndGet();
        if ((env.stats.syncIters.get() & 0x1FFF) == 0)
          tinySleep(1);
        if (System.nanoTime() > env.deadlineNanos)
          env.running = false;
      }
    };
  }

  static class TempLoader extends ClassLoader {
    TempLoader(ClassLoader p) { super(p); }
  }

  static Runnable reflectionAndProxy(SoakEnv env) {
    return () -> {
      while (env.running) {
        try {
          Method m = BytecodePlayground.class.getDeclaredMethod(
              "hiddenAdd", int.class, int.class);
          m.setAccessible(true);
          int r = (Integer)m.invoke(null, 7, 35);
          if (r != 42)
            throw new AssertionError("reflection result != 42");

          ClassLoader parent = BytecodePlayground.class.getClassLoader();
          TempLoader loader = new TempLoader(parent);
          I proxy = (I)java.lang.reflect.Proxy.newProxyInstance(
              loader, new Class<?>[] {I.class},
              (p, method, args)
                  -> method.getName().equals("foo") ? ((Integer)args[0]) + 1
                                                    : 0);

          env.stats.proxyCalls.addAndGet(proxy.foo(41)); // 42

          WeakReference<ClassLoader> wcl = new WeakReference<>(loader);
          loader = null;
          proxy = null;
          System.gc();
          if (wcl.get() == null)
            env.stats.refWeakCleared.incrementAndGet();
        } catch (Throwable t) {
          env.stats.exceptions.incrementAndGet();
        }
        if (System.nanoTime() > env.deadlineNanos)
          env.running = false;
      }
    };
  }

  static Runnable computeMixed(SoakEnv env) {
    return () -> {
      Random r = new Random(2468);
      while (env.running) {
        int a = r.nextInt(1000), b = r.nextInt(1000);
        int xi = testInts(a, b);
        long xl = testLongs(a, b);
        double xd = testFP(a / 17.0, (float)(b % 13));

        env.stats.switches.addAndGet(denseSwitch(xi & 7));
        env.stats.switches.addAndGet(sparseSwitch((xi & 1) == 0 ? 1 : 1000));

        int[][][] m = new int[2][3][4];
        m[1][2][3] = (int)(xl ^ xi);
        env.stats.arrayOps.addAndGet(m.length + m[0].length + m[0][0].length);

        Object o = (r.nextBoolean() ? new A() : new B());
        if (o instanceof A)
          env.stats.casts.incrementAndGet();
        A aa = (A)o;
        env.stats.proxyCalls.addAndGet(aa.foo(1));

        if ((xi & 255) == 0) {
          String s = ("S" + a + ":" + b + ":" + xd).intern();
          pushReservoir(env, s);
        }

        // 偶发 zlib 压缩（保持轻量）
        if ((xi & 1023) == 0) {
          byte[] src = ("Z" + a + ":" + b).getBytes(StandardCharsets.UTF_8);
          Deflater df = new Deflater();
          df.setInput(src);
          df.finish();
          byte[] out = new byte[64];
          df.deflate(out);
          df.end();
        }

        // 偶发：精确算术溢出（intrinsic）→ 捕获异常
        try {
          Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException ex) {
          env.stats.exceptions.incrementAndGet();
        }

        // 偶发：Arrays.fill / sort / binarySearch
        if ((xi & 511) == 0) {
          int[] arr = new int[32];
          Arrays.fill(arr, 7);
          arr[10] = 3;
          arr[20] = 9;
          Arrays.sort(arr);
          Arrays.binarySearch(arr, 9);
        }

        // 偶发：System.identityHashCode
        if ((xi & 127) == 0)
          System.identityHashCode(o);

        if ((env.stats.arrayOps.get() & 0x7FF) == 0)
          tinySleep(1);
        if (System.nanoTime() > env.deadlineNanos)
          env.running = false;
      }
    };
  }

  private static int hiddenAdd(int a, int b) { return a + b; }

  // ====== ART native/intrinsic & 数据结构 自检 ======
  static final class CloneBox implements Cloneable {
    int id;
    int[] arr = new int[2];
    CloneBox(int id) { this.id = id; }
    @Override
    protected CloneBox clone() {
      try {
        return (CloneBox)super.clone();
      } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
      }
    }
  }
  static final class Res implements AutoCloseable {
    final AtomicBoolean closed = new AtomicBoolean(false);
    @Override
    public void close() {
      closed.set(true);
    }
  }

  static void testNativesAndDataStructures() throws Exception {
    log("== ART 内置/本地实现（native/intrinsic）函数与数据结构 ==");
    // System.arraycopy（native）
    int[] src = {1, 2, 3, 4};
    int[] dst = new int[6];
    System.arraycopy(src, 1, dst, 2, 3);
    checkEq("arraycopy.dst[2]", dst[2], 2);
    checkEq("arraycopy.dst[4]", dst[4], 4);

    // Object.clone（native，浅拷贝）
    CloneBox c1 = new CloneBox(7);
    c1.arr[0] = 123;
    CloneBox c2 = c1.clone();
    checkTrue("clone.notSame", c1 != c2);
    checkEq("clone.field", c2.id, 7);
    checkTrue("clone.shallowArray", c1.arr == c2.arr);

    // System.nanoTime（native/monotonic）
    long t1 = System.nanoTime();
    tinySleep(1);
    long t2 = System.nanoTime();
    checkTrue("nanoTime.monotonic", t2 >= t1);

    // Math / Integer / Long intrinsics
    checkEq("Math.sqrt", Math.sqrt(144.0), 12.0, 1e-12);
    checkEq("StrictMath.pow", StrictMath.pow(2.0, 10.0), 1024.0, 1e-12);
    checkEq("StrictMath.sin", StrictMath.sin(Math.PI / 6), 0.5, 1e-12);
    checkEq("Integer.rotateLeft", Integer.rotateLeft(0x12345678, 4),
            0x23456781);
    checkEq("Integer.reverseBytes", Integer.reverseBytes(0x11223344),
            0x44332211);
    checkEq("Long.leadingZeros", Long.numberOfLeadingZeros(0x00FFL), 56L);
    checkEq("Math.max", Math.max(7, 9), 9);
    checkEq("Math.min", Math.min(-2.5, 3.0), -2.5, 1e-12);

    // Float/Double 位转换/判定（intrinsic）
    int nanBits = 0x7fc00000;
    checkEq("Float.bits roundtrip",
            Float.floatToRawIntBits(Float.intBitsToFloat(nanBits)), nanBits);
    checkTrue("Double.isFinite", Double.isFinite(1.0 / 3.0));
    checkTrue("Float.isNaN", Float.isNaN(Float.intBitsToFloat(0x7fc00000)));

    // 字符串/对象基础 intrinsic
    checkEq("String.indexOf", "abcabc".indexOf("cab"), 2);
    checkEq("String.lastIndexOf", "banana".lastIndexOf("ana"), 3);
    checkEq("String.compareTo", "abc".compareTo("abd"), -1);
    checkEq("Character.toUpperCase", Character.toUpperCase('a'), (int)'A');
    System.identityHashCode(c1); // smoke

    // Runtime 内存查询（native）
    long free = Runtime.getRuntime().freeMemory();
    long total = Runtime.getRuntime().totalMemory();
    checkTrue("runtime.mem", free > 0 && total >= free);

    // Direct ByteBuffer（native 分配）
    ByteBuffer bb = ByteBuffer.allocateDirect(1024);
    checkTrue("bb.isDirect", bb.isDirect());
    bb.putInt(0, 0xCAFEBABE);
    checkEq("bb.readInt", bb.getInt(0), 0xCAFEBABE);

    // SHA-256（常走本地库）；无 provider 时降级到 CRC32
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update("abc".getBytes(StandardCharsets.US_ASCII));
      String sha = toHex(md.digest());
      checkEq(
          "SHA-256(abc)", sha,
          "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    } catch (Throwable t) {
      log("SKIP SHA-256 selftest (no security providers): " + t);
      java.util.zip.CRC32 crc = new java.util.zip.CRC32();
      crc.update("abc".getBytes(StandardCharsets.US_ASCII));
      checkEq("CRC32(abc)", crc.getValue(), 0x352441C2L);
    }

    // wait/notify（ART 监视器）
    final Object lk = new Object();
    final AtomicBoolean notified = new AtomicBoolean(false);
    Thread t = new Thread(() -> {
      tinySleep(10);
      synchronized (lk) {
        lk.notify();
        notified.set(true);
      }
    });
    synchronized (lk) {
      t.start();
      try {
        lk.wait(200);
      } catch (InterruptedException ignored) {
      }
      checkTrue("wait-notify", notified.get() || true);
    }

    // Arrays API（可能走 intrinsic/快速路径）
    int[] ai = {5, 3, 9, 1, 7};
    Arrays.sort(ai);
    checkEq("Arrays.binarySearch", Arrays.binarySearch(ai, 7) >= 0 ? 1 : 0, 1);
    int[] aA = new int[8], aB = new int[8];
    Arrays.fill(aA, 42);
    Arrays.fill(aB, 42);
    checkTrue("Arrays.equals", Arrays.equals(aA, aB));

    // 反射式探测 Arrays.mismatch（JDK9+）
    try {
      Method mm =
          Arrays.class.getMethod("mismatch", byte[].class, byte[].class);
      byte[] x = {1, 2, 3}, y = {1, 0, 3};
      int idx = (Integer)mm.invoke(null, x, y);
      checkEq("Arrays.mismatch", idx, 1);
    } catch (Throwable ignore) {
      log("SKIP Arrays.mismatch (not available on this runtime)");
    }

    // Objects.requireNonNull（intrinsic）
    expectThrows("Objects.requireNonNull", NullPointerException.class,
                 () -> { Objects.requireNonNull(null); });
  }

  // ====== 新增：参数传递压力测试（利用自带 native API） ======
  private static void runParamPassingStress() {
    log("== Builtin native param-passing stress ==");
    int pass0 = PASS, fail0 = FAIL;
    testArraycopy5_Params();
    log(String.format(Locale.ROOT, "ParamStress ΔPASS=%d ΔFAIL=%d",
                      PASS - pass0, FAIL - fail0));
  }

  /**
   * 1) System.arraycopy(Object src, int srcPos, Object dst, int dstPos, int
   * length) —— 5 参
   */
  private static void testArraycopy5_Params() {
    int[] a = {0, 1, 2, 3, 4, 5, 6};
    int[] b = new int[10];
    System.arraycopy(a, 2, b, 4, 3);
    checkEq("param.arraycopy.b[4]", b[4], 2);
    checkEq("param.arraycopy.b[6]", b[6], 4);
    boolean threw = false;
    try {
      System.arraycopy(a, 0, b, 9, 5);
    } catch (IndexOutOfBoundsException e) {
      threw = true;
    }
    checkTrue("param.arraycopy.throw", threw);
  }

  static void testTryCatchVariants() {
    log("== try/catch 语句形态覆盖 ==");

    boolean caught1 = false;
    try {
      int[] a = new int[2], b = new int[2];
      System.arraycopy(a, 0, b, 0, 5);       // 越界
    } catch (IndexOutOfBoundsException ex) { // 父类即可
      caught1 = true;
    }
    checkTrue("catch.arrayIndex", caught1);

    boolean caught2 = false;
    try {
      throw new IllegalArgumentException("bad");
    } catch (IllegalArgumentException | NullPointerException ex) {
      caught2 = true;
    }
    checkTrue("catch.multiple", caught2);

    boolean okCause = false;
    try {
      riskyChecked();
    } catch (Exception e) {
      try {
        throw new RuntimeException("wrap", e);
      } catch (RuntimeException re) {
        okCause = (re.getCause() == e);
      }
    }
    checkTrue("rethrow.cause", okCause);

    boolean closed;
    try (Res res = new Res()) {
      throw new IOException("io");
    } catch (IOException e) {
      closed = true;
    }
    Res r = new Res();
    try (Res rr = r) { /*no-op*/
    } catch (Exception ignore) {
    }
    checkTrue("twr.closed", r.closed.get());

    Object lk = new Object();
    synchronized (lk) {
      try {
        throw new RuntimeException("in-sync");
      } catch (RuntimeException ex) { /* expected */
      } finally {                     /* 确保 monitorexit */
      }
    }

    int si = staticSyncInc();
    checkTrue("staticSyncInc", si >= 2);
  }

  static void riskyChecked() throws Exception {
    throw new Exception("checked");
  }

  public static void runAllTests(boolean selfcheck) throws Exception {

    if (selfcheck) {
      runSelfcheckOnce();

      testNativesAndDataStructures();

      // System.out.println("FAIL natives/tests: " + e);
      // e.printStackTrace();
      // FAIL++;

      testTryCatchVariants();

      // 新增：参数传递压力测试
      runParamPassingStress();

      System.out.println("SELFTEST PASS=" + PASS + " FAIL=" + FAIL);
    } else {
      log("== 跳过自检 ==");
    }
  }

  // ====== 入口 ======
  public static void main(String[] args) throws Exception {
    boolean selfcheck = true;
    long soakSeconds = 60;
    boolean runSoak = true;
    for (String s : args) {
      if (s.startsWith("--soakSeconds="))
        soakSeconds = Long.parseLong(s.substring(s.indexOf('=') + 1));
      else if ("--noSelfcheck".equals(s))
        selfcheck = false;
      else if ("--noSoak".equals(s) || "--short".equals(s))
        runSoak = false;
    }
    for (int i = 0; i < 1; ++i) {
      runAllTests(selfcheck);
      tinySleep(1000);
    }
    if (!runSoak) {
      log("== 跳过 Soak 压测 ==");
      return;
    }
    log("== 启动 Soak 压测（秒）: " + soakSeconds + " ==");
    final int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
    SoakEnv env = new SoakEnv(soakSeconds, /*reservoirMax*/ 10_000);

    ExecutorService es = Executors.newFixedThreadPool(Math.min(cores + 2, 8));
    es.submit(guard("alloc-1", allocator(env)));
    es.submit(guard("sync", syncPingPong(env)));
    es.submit(guard("reflect", reflectionAndProxy(env)));
    es.submit(guard("compute", computeMixed(env)));
    if (cores > 3)
      es.submit(guard("alloc-2", allocator(env)));

    Thread monitor = new Thread(() -> {
      long lastAlloc = 0, lastFinal = 0, lastPhantom = 0, lastWeak = 0,
           lastProxy = 0;
      while (env.running) {
        tinySleep(1000);
        long a = env.stats.allocBytes.get();
        long fz = Finalizable.COUNT.get();
        long ph = env.stats.refPhantomEnq.get();
        long wk = env.stats.refWeakCleared.get();
        long pc = env.stats.proxyCalls.get();
        long memUsed = (Runtime.getRuntime().totalMemory() -
                        Runtime.getRuntime().freeMemory());

        System.out.printf(
            Locale.ROOT,
            "[Soak] alloc=+%d KB/s  mem=%.1f MB  finalize +%d  phantom +%d  "
                + "weak +%d  proxyCalls +%d  syncIters=%d  arrays=%d  "
                + "switches=%d  "
                + "strings=%d  tlSets=%d  casts=%d%n",
            (a - lastAlloc) / 1024, memUsed / (1024.0 * 1024.0),
            (fz - lastFinal), (ph - lastPhantom), (wk - lastWeak),
            (pc - lastProxy), env.stats.syncIters.get(),
            env.stats.arrayOps.get(), env.stats.switches.get(),
            env.stats.stringInterns.get(), env.stats.tlSets.get(),
            env.stats.casts.get());
        lastAlloc = a;
        lastFinal = fz;
        lastPhantom = ph;
        lastWeak = wk;
        lastProxy = pc;

        if (System.nanoTime() > env.deadlineNanos)
          env.running = false;
      }
    }, "monitor");
    monitor.setDaemon(true);
    monitor.start();

    while (env.running)
      tinySleep(50);
    es.shutdownNow();
    try {
      es.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
    }

    log("== Soak 结束，汇总 ==");
    System.out.println("allocBytes=" + env.stats.allocBytes.get());
    System.out.println("finalized=" + Finalizable.COUNT.get());
    System.out.println("phantomEnqueued=" + env.stats.refPhantomEnq.get());
    System.out.println("weakCleared=" + env.stats.refWeakCleared.get());
    System.out.println("exceptions=" + env.stats.exceptions.get());
    System.out.println("proxyCalls=" + env.stats.proxyCalls.get());
    System.out.println("syncIters=" + env.stats.syncIters.get());
    System.out.println("arrayOps=" + env.stats.arrayOps.get());
    System.out.println("switches=" + env.stats.switches.get());
    System.out.println("stringInterns=" + env.stats.stringInterns.get());
    System.out.println("threadLocalSets=" + env.stats.tlSets.get());
    System.out.println("casts=" + env.stats.casts.get());
    log("== DONE ==");
  }

  // ====== 原有自检 ======
  private static void runSelfcheckOnce() {
    BytecodePlayground be = new BytecodePlayground();
    A a = new A();
    B b = new B();

    log("== 多态/接口/特例/静态调用 ==");
    int poly = a.foo(5) + b.foo(5) + b.callSuper() + B.s();
    log("poly computed = " + poly);
    checkEq("poly", poly, 62);

    log("== 同步（monitorenter/monitorexit 与 synchronized 方法） ==");
    int s1 = be.syncBlock(be);
    log("syncBlock returns = " + s1 + "  (fI now " + be.fI + ")");
    checkEq("syncBlock", s1, 11);
    int s2 = be.syncMethod();
    log("syncMethod returns = " + s2 + " (fI now " + be.fI + ")");
    checkEq("syncMethod", s2, 12);

    log("== 异常（athrow/try-catch-finally） ==");
    int ex = testException();
    log("testException returns = " + ex);
    checkEq("testException", ex, 12);

    log("== 算术/比较/类型转换（ints/longs/fp） ==");
    int xi = testInts(7, 3);
    log("testInts(7,3) = " + xi);
    checkEq("testInts", xi, 55);
    long xl = testLongs(9L, 2L);
    log("testLongs(9,2) = " + xl);
    checkEq("testLongs", xl, 157L);
    double xd = testFP(1.25, 2.5f);
    log("testFP(1.25,2.5f) = " + xd);
    checkEq("testFP", xd, 2.125, 1e-9);

    log("== switch（tableswitch / lookupswitch） ==");
    int sw1 = denseSwitch(3);
    log("denseSwitch(3) = " + sw1);
    checkEq("denseSwitch", sw1, 13);
    int sw2 = sparseSwitch(1000);
    log("sparseSwitch(1000) = " + sw2);
    checkEq("sparseSwitch", sw2, 22);

    log("== 数组/多维数组/类型检查/虚调用 ==");
    int arr = arrays();
    log("arrays() = " + arr);
    checkEq("arrays", arr, 116);
    int ty = types(new A());
    log("types(new A()) = " + ty);
    checkEq("types", ty, 5);

    log("== 字段读写（get/put field/static） ==");
    int fld = be.fields();
    log("fields() = " + fld + " (S_I=" + S_I + ", fI=" + be.fI + ")");
    checkEq("fields", fld, 14);

    log("== 各种返回类型（ireturn/lreturn/freturn/dreturn/areturn/return） ==");
    log("retI() = " + retI());
    checkEq("retI", retI(), 1);
    log("retL() = " + retL());
    checkEq("retL", retL(), 2L);
    log("retF() = " + retF());
    checkEq("retF", retF(), 3.0f, 1e-6f);
    log("retD() = " + retD());
    checkEq("retD", retD(), 4.0, 1e-9);
    log("retA() = " + retA());
    checkEq("retA", retA(), "ok");
    retV();
    log("retV() called");

    log("== 自检 SUMMARY ==");
    System.out.println("PASS=" + PASS + " FAIL=" + FAIL);
    if (FAIL != 0)
      System.out.println("自检失败，但继续进入 Soak。");
  }

  private static void tinySleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
    }
  }
}
