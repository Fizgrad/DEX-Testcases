// BytecodePlaygroundJit.java
// 独立版：不依赖其他模块，方法按功能拆分并在调用前预热 JIT。
 

import java.lang.reflect.Method;

public final class BytecodePlaygroundJit {
  private static final int WARM_ITERS = 4096;
  private static final int WARM_SYNC_ITERS = 1024;
  private static final int WARM_EXCEPTION_ITERS = 256;

  private static final TestSupport.Counter CTR = new TestSupport.Counter();

  private BytecodePlaygroundJit() {}

  private static void log(String msg) { TestSupport.log(msg); }

  // ====== 数据模型 ======
  private interface Foo {
    int foo(int x);
  }

  private static class A implements Foo {
    @Override
    public int foo(int x) {
      return x + 1;
    }
    int self() { return 42; }
  }

  private static final class B extends A {
    @Override
    public int foo(int x) {
      return x + 2;
    }
    int callSuper() { return super.self(); }
    static int staticValue() { return 7; }
  }

  private static final class StateBox {
    static int sI = 1;
    static Object sRef;
    int fI = 10;
    Object ref;

    int syncBlock() {
      synchronized (this) {
        fI++;
        return fI;
      }
    }

    synchronized int syncMethod() { return ++fI; }

    static synchronized int staticSyncInc() { return ++sI; }

    int fields() {
      sI += 1;
      fI += sI;
      return fI;
    }

    static void resetStatic() { sI = 1; }
    static void setStaticRef(Object o) { sRef = o; }
    static Object getStaticRef() { return sRef; }
    void resetFields() { fI = 10; }
    void setRef(Object o) { ref = o; }
    Object getRef() { return ref; }
  }

  // ====== 简化断言 ======
  private static void checkEq(String name, int got, int exp) {
    TestSupport.checkEq(name, got, exp, CTR);
  }

  private static void checkEq(String name, long got, long exp) {
    TestSupport.checkEq(name, got, exp, CTR);
  }

  private static void checkEq(String name, Object got, Object exp) {
    TestSupport.checkEq(name, got, exp, CTR);
  }

  private static void checkEq(String name, float got, float exp, float eps) {
    TestSupport.checkApprox(name, got, exp, eps, CTR);
  }

  private static void checkEq(String name, double got, double exp, double eps) {
    TestSupport.checkApprox(name, got, exp, eps, CTR);
  }

  private static void checkTrue(String name, boolean ok) {
    TestSupport.checkTrue(name, ok, CTR);
  }

  // ====== JIT 预热辅助 ======
  private static void requestJitCompilation() {
    try {
      Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
      Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
      Method startJit = vmRuntime.getDeclaredMethod("startJitCompilation");
      getRuntime.setAccessible(true);
      startJit.setAccessible(true);
      Object rt = getRuntime.invoke(null);
      startJit.invoke(rt);
    } catch (Throwable ignored) {
      // ART only; quietly continue on other VMs.
    }
    try {
      Class<?> compiler = Class.forName("java.lang.Compiler");
      Method compileClass =
          compiler.getDeclaredMethod("compileClass", Class.class);
      compileClass.invoke(null, BytecodePlaygroundJit.class);
    } catch (Throwable ignored) {
      // java.lang.Compiler may be absent or disabled.
    }
  }

  private static void warmUp(int iterations, Runnable r) {
    for (int i = 0; i < iterations; i++)
      r.run();
  }

  // ====== 纯计算函数 ======
  private static int polymorphicResult() {
    A a = new A();
    B b = new B();
    return a.foo(5) + b.foo(5) + b.callSuper() + B.staticValue();
  }

  private static int syncBlockOnce(StateBox box) { return box.syncBlock(); }

  private static int syncMethodOnce(StateBox box) { return box.syncMethod(); }

  private static int exceptionValue() {
    int v = 0;
    try {
      throwIf(true);
      v = 1;
    } catch (Exception e) {
      v = 2;
    } finally {
      v += 10;
    }
    return v;
  }

  private static void throwIf(boolean flag) throws Exception {
    if (flag)
      throw new Exception("boom");
  }

  private static int computeInts(int a, int b) {
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
    return (int)d;
  }

  private static long computeLongs(long a, long b) {
    long x = a * b;
    x = x + 1;
    x = x - 2;
    x = x ^ 3;
    return (x << 3) ^ (x >> 2) ^ (x >>> 1);
  }

  private static double computeFP(double a, float b) {
    double d = a + b;
    d = d * 2.5;
    d = d / 3.0;
    return d - 1.0;
  }

  private static int parameterSpill(String a, String b, long l1, long l2,
                                    double d1, double d2, int x, int y, int z,
                                    int w, String c, long l3, double d3) {
    int refSum = a.length() + b.length() + c.length();
    int intSum = x + y + z + w;
    long longSum = l1 + l2 + l3;
    double doubleSum = d1 + d2 + d3;
    return refSum + intSum + (int)longSum + (int)Math.round(doubleSum);
  }

  private static long registerSpillComputation(int base, long l, double d,
                                               String s) {
    long a = l;
    long b = l * 2;
    double c = d + 1.0;
    double e = c * 2.0;
    int i1 = base + 1;
    int i2 = i1 + base;
    int len = s.length();
    long acc = a + b + i1 + i2 + len + Math.round(e);
    long x1 = acc + 3;
    long x2 = x1 * 2;
    double x3 = e + acc;
    long x4 = (long)x3;
    long x5 = x2 + x4;
    return x5;
  }

  private static int denseSwitch(int k) {
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

  private static int sparseSwitch(int k) {
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

  private static int arrayResult() {
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

  private static int typeResult() {
    Object o = new A();
    int v = 0;
    if (o instanceof A)
      v += 1;
    A a = (A)o;
    v += a.foo(3);
    return v;
  }

  private static int fieldResult(StateBox box) { return box.fields(); }

  private static void ConsumeReferenceHelper(Object obj) {
    if (obj != null)
      System.out.println(obj.hashCode());
  }

  private static Object phiReference(boolean flag) {
    Object ref;
    if (flag) {
      ref = new A();
    } else {
      ref = null;
    }
    ConsumeReferenceHelper(ref);
    return ref;
  }

  private static int retI() { return 1; }
  private static long retL() { return 2L; }
  private static float retF() { return 3f; }
  private static double retD() { return 4d; }
  private static Object retA() { return "ok"; }
  private static void retV() {}

  // ====== 具体测试 ======
  private static void testPolymorphism() {
    log("== JIT 多态/接口/静态调用 ==");
    warmUp(WARM_ITERS, BytecodePlaygroundJit::polymorphicResult);
    checkEq("poly.jit", polymorphicResult(), 62);
  }

  private static void testSync(StateBox state) {
    log("== JIT 同步 ==");
    warmUp(WARM_SYNC_ITERS, () -> syncBlockOnce(state));
    warmUp(WARM_SYNC_ITERS, () -> syncMethodOnce(state));
    int s1 = syncBlockOnce(state);
    int s2 = syncMethodOnce(state);
    checkEq("syncMethod.jit", s2, s1 + 1);
  }

  private static void testException() {
    log("== JIT 异常 ==");
    warmUp(WARM_EXCEPTION_ITERS, BytecodePlaygroundJit::exceptionValue);
    checkEq("testException.jit", exceptionValue(), 12);
  }

  private static void testArithmetic() {
    log("== JIT 算术/类型转换 ==");
    warmUp(WARM_ITERS, () -> computeInts(7, 3));
    checkEq("testInts.jit", computeInts(7, 3), 55);

    warmUp(WARM_ITERS, () -> computeLongs(9L, 2L));
    checkEq("testLongs.jit", computeLongs(9L, 2L), 157L);

    warmUp(WARM_ITERS, () -> computeFP(1.25, 2.5f));
    checkEq("testFP.jit", computeFP(1.25, 2.5f), 2.125, 1e-9);
  }

  private static void testSwitches() {
    log("== JIT switch 语句 ==");
    warmUp(WARM_ITERS, () -> denseSwitch(3));
    checkEq("denseSwitch.jit", denseSwitch(3), 13);

    warmUp(WARM_ITERS, () -> sparseSwitch(1000));
    checkEq("sparseSwitch.jit", sparseSwitch(1000), 22);
  }

  private static void testArraysAndTypes() {
    log("== JIT 数组/类型检查/虚调用 ==");
    warmUp(WARM_ITERS, BytecodePlaygroundJit::arrayResult);
    checkEq("arrays.jit", arrayResult(), 116);

    warmUp(WARM_ITERS, BytecodePlaygroundJit::typeResult);
    checkEq("types.jit", typeResult(), 5);
  }

  private static void testPhiMerge() {
    log("== JIT Phi 合并 (int32 + 引用) ==");
    warmUp(WARM_ITERS / 2, () -> phiReference(true));
    warmUp(WARM_ITERS / 2, () -> phiReference(false));
    Object r1 = phiReference(true);
    Object r2 = phiReference(false);
    checkTrue("phi.ref.nonNull", r1 != null);
    checkTrue("phi.ref.null", r2 == null);
  }

  private static void testFields(StateBox state) {
    log("== JIT 字段读写 ==");
    StateBox.resetStatic();
    state.resetFields();
    warmUp(WARM_ITERS / 2, () -> fieldResult(state));
    StateBox.resetStatic();
    state.resetFields();
    int fld = fieldResult(state);
    checkEq("fields.jit", fld, 12);

    warmUp(WARM_ITERS / 4, () -> state.setRef(new Object()));
    warmUp(WARM_ITERS / 4, () -> StateBox.setStaticRef(new Object()));

    Object marker = new Object();
    state.setRef(marker);
    StateBox.setStaticRef(marker);
    checkTrue("instanceRef.same", state.getRef() == marker);
    checkTrue("staticRef.same", StateBox.getStaticRef() == marker);

    Object other = new Object();
    checkTrue("instanceRef.notOther", state.getRef() != other);
    checkTrue("staticRef.notOther", StateBox.getStaticRef() != other);

    state.setRef(null);
    StateBox.setStaticRef(null);
    checkTrue("instanceRef.null", state.getRef() == null);
    checkTrue("staticRef.null", StateBox.getStaticRef() == null);
  }

  private static void testParameterSpill() {
    log("== JIT 参数溢出栈槽 ==");
    warmUp(WARM_ITERS / 2,
           ()
               -> parameterSpill("aa", "bbb", 7L, 9L, 1.5, 2.25, 5, 6, 7, 8,
                                 "z", 11L, -0.75));
    int res = parameterSpill("aa", "bbb", 7L, 9L, 1.5, 2.25, 5, 6, 7, 8, "z",
                             11L, -0.75);
    checkEq("paramSpill.jit", res, 62);
  }

  private static void testRegisterSpill() {
    log("== JIT 寄存器溢出栈槽 ==");
    warmUp(WARM_ITERS / 2, () -> registerSpillComputation(5, 7L, 1.5, "spill"));
    long res = registerSpillComputation(5, 7L, 1.5, "spill");
    checkEq("regSpill.jit", res, 155L);
  }

  private static void testReturns() {
    log("== JIT 返回类型 ==");
    warmUp(WARM_ITERS / 4, BytecodePlaygroundJit::retI);
    checkEq("retI.jit", retI(), 1);

    warmUp(WARM_ITERS / 4, BytecodePlaygroundJit::retL);
    checkEq("retL.jit", retL(), 2L);

    warmUp(WARM_ITERS / 4, BytecodePlaygroundJit::retF);
    checkEq("retF.jit", retF(), 3.0f, 1e-6f);

    warmUp(WARM_ITERS / 4, BytecodePlaygroundJit::retD);
    checkEq("retD.jit", retD(), 4.0, 1e-9);

    warmUp(WARM_ITERS / 4, BytecodePlaygroundJit::retA);
    checkEq("retA.jit", retA(), "ok");

    warmUp(WARM_ITERS / 4, BytecodePlaygroundJit::retV);
    retV();
    checkTrue("retV.jit", true);
  }

  public static void testReferencesEqual(Object a, Object b) {
    if (a == b) {
      log("References are equal");
    }
    a = new A();
    if (a != b) {
      log("References are not equal");
    }
  }

  private static void logSummary() {
    TestSupport.summary("BytecodePlaygroundJit", CTR);
  }

  public static void main(String[] args) {
    System.out.println("== JIT 预热自检 (独立版) ==");
    requestJitCompilation();
    StateBox state = new StateBox();
    A ref = new A();
    testReferencesEqual(state, ref);
    testPolymorphism();
    testSync(state);
    testException();
    testArithmetic();
    testSwitches();
    testArraysAndTypes();
    testPhiMerge();
    testFields(state);
    testParameterSpill();
    testRegisterSpill();
    testReturns();
    System.out.println("== DONE ==");
    logSummary();
  }
}
