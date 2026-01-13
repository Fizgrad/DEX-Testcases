 

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.LongSupplier;

public class InvokeShapeTest {

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
  private static long staticTargetNonRange(int a, long b, float c, double d,
                                           Box box) {
    return 10L ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^ encDouble(d) ^
        encBox(box);
  }

  // 期望生成：invoke-static/range（>5 参数）
  private static long staticTargetRange(int a, long b, float c, double d,
                                        Box box, int extra) {
    return 20L ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^ encDouble(d) ^
        encBox(box) ^ (encInt(extra) * 13L);
  }

  private static void testInvokeStatic() {
    Box box = new Box(5);
    int a = 1;
    long b = 2L;
    float c = 3.5f;
    double d = 4.5;
    int extra = 42;

    long r1 = staticTargetNonRange(a, b, c, d, box);
    long expected1 = 4697817368861081846L;
    assertEquals("invoke-static (non-range)", expected1, r1);

    long r2 = staticTargetRange(a, b, c, d, box, extra);
    long expected2 = 4697817368861098742L;
    assertEquals("invoke-static/range", expected2, r2);
  }

  // =========================================================
  // 2. 实例调用：NterpCommonInvokeInstance / InstanceRange
  // =========================================================

  static class InstanceTarget {
    final int id;
    InstanceTarget(int id) { this.id = id; }

    // 期望：invoke-virtual（非 range，总参数 this+4 = 5）
    long instanceNonRange(int a, long b, float c, double d, Box box) {
      return 30L ^ encInt(id) ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^
          encDouble(d) ^ encBox(box);
    }

    // 期望：invoke-virtual/range（this+5 = 6）
    long instanceRange(int a, long b, float c, double d, Box box, int extra) {
      return 40L ^ encInt(id) ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^
          encDouble(d) ^ encBox(box) ^ (encInt(extra) * 17L);
    }
  }

  private static void testInvokeInstance() {
    InstanceTarget t = new InstanceTarget(7);
    Box box = new Box(9);
    int a = 3;
    long b = 4L;
    float c = 1.25f;
    double d = 2.75;
    int extra = 11;

    long r1 = t.instanceNonRange(a, b, c, d, box);
    long expected1 = 4640396473524027903L;
    assertEquals("invoke-instance (non-range)", expected1, r1);

    long r2 = t.instanceRange(a, b, c, d, box, extra);
    long expected2 = 4640396473524033388L;
    assertEquals("invoke-instance/range", expected2, r2);
  }

  // =========================================================
  // 3. 接口调用：NterpCommonInvokeInterface / InterfaceRange
  // =========================================================

  interface MyInterface {
    long ifaceNonRange(int a, long b, float c, double d, Box box);
    long ifaceRange(int a, long b, float c, double d, Box box, int extra);
  }

  static class InterfaceImpl implements MyInterface {
    final int id;
    InterfaceImpl(int id) { this.id = id; }

    @Override
    public long ifaceNonRange(int a, long b, float c, double d, Box box) {
      return 50L ^ encInt(id) ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^
          encDouble(d) ^ encBox(box);
    }

    @Override
    public long ifaceRange(int a, long b, float c, double d, Box box,
                           int extra) {
      return 60L ^ encInt(id) ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^
          encDouble(d) ^ encBox(box) ^ (encInt(extra) * 19L);
    }
  }

  private static void testInvokeInterface() {
    MyInterface iface = new InterfaceImpl(13);
    Box box = new Box(6);
    int a = 8;
    long b = 9L;
    float c = 0.5f;
    double d = 123.0;
    int extra = 77;

    // 静态类型是接口 → 生成 invoke-interface / invoke-interface-range
    long r1 = iface.ifaceNonRange(a, b, c, d, box);
    long expected1 = 5065071844562831236L;
    assertEquals("invoke-interface (non-range)", expected1, r1);

    long r2 = iface.ifaceRange(a, b, c, d, box, extra);
    long expected2 = 5065071844562876067L;
    assertEquals("invoke-interface/range", expected2, r2);
  }

  // =========================================================
  // 4. 方法句柄：NterpCommonInvokePolymorphic / Range
  //    （invoke-polymorphic / invoke-polymorphic/range）
  // =========================================================

  // 目标静态方法：对应 invoke-polymorphic（非 range）
  private static long polyTargetNonRange(int a, long b, float c, double d,
                                         Box box) {
    System.out.println("polyTargetNonRange d = " + d + " bits=" +
                       Long.toHexString(Double.doubleToLongBits(d)));
    long r = 0;
    r ^= 0x0100000000000000L * a;
    r ^= 0x0200000000000000L * (b & 0xff); // 只看低 8 bit 防止太大
    r ^= 0x0400000000000000L * (box != null ? box.value & 0xff : 0);
    r ^= Double.doubleToLongBits(d);          // 直接用 double 的 bit
    r ^= (long)Float.floatToIntBits(c) << 32; // 把 float bits 放到高 32 bit
    return r;
  }

  // 目标静态方法：对应 invoke-polymorphic/range
  private static long polyTargetRange(int a, long b, float c, double d, Box box,
                                      int extra) {
    return 80L ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^ encDouble(d) ^
        encBox(box) ^ (encInt(extra) * 23L);
  }

  private static void testInvokePolymorphic() throws Throwable {
    Box box = new Box(10);
    int a = 5;
    long b = 6L;
    float c = 7.0f;
    double d = 8.0;
    int extra = 99;

    // ---- 非 range：MethodHandle + 5 动态参数（共 6，“this+4”）----
    MethodType mt1 =
        MethodType.methodType(long.class, int.class, long.class, float.class,
                              double.class, Box.class);
    MethodHandle mh1 = MethodHandles.lookup().findStatic(
        InvokeShapeTest.class, "polyTargetNonRange", mt1);

    long expected1 = 2431943798780067840L;
    long r1 = (long)mh1.invokeExact(a, b, c, d, box);
    assertEquals("invoke-polymorphic (non-range)", expected1, r1);

    // ---- range：MethodHandle + 6 动态参数（共 7，“this+5”）----
    MethodType mt2 =
        MethodType.methodType(long.class, int.class, long.class, float.class,
                              double.class, Box.class, int.class);
    MethodHandle mh2 = MethodHandles.lookup().findStatic(
        InvokeShapeTest.class, "polyTargetRange", mt2);

    long expected2 = 4764808413377008552L;
    long r2 = (long)mh2.invokeExact(a, b, c, d, box, extra);
    assertEquals("invoke-polymorphic/range", expected2, r2);
  }

  // =========================================================
  // 5. invoke-custom / invoke-custom-range（lambda）
  //    NterpCommonInvokeCustom / CustomRange
  // =========================================================

  private static long customSinkNonRange(int a, long b, float c, double d,
                                         Box box) {
    return 90L ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^ encDouble(d) ^
        encBox(box);
  }

  private static long customSinkRange(int a, long b, float c, double d, Box box,
                                      int extra1, int extra2) {
    return 100L ^ encInt(a) ^ encLong(b) ^ encFloat(c) ^ encDouble(d) ^
        encBox(box) ^ (encInt(extra1) * 29L) ^ (encInt(extra2) * 31L);
  }

  private static void testInvokeCustom() {
    final int a = 1;
    final long b = 2L;
    final float c = 3.0f;
    final double d = 4.0;
    final Box box = new Box(7);
    final int extra1 = 33;
    final int extra2 = 44;

    // 捕获 5 个变量 → invoke-custom（非 range）
    LongSupplier sup1 = () -> customSinkNonRange(a, b, c, d, box);
    long expected1 = 4688247219638240228L;
    long r1 = sup1.getAsLong();
    assertEquals("invoke-custom (non-range)", expected1, r1);

    // 捕获 7 个变量 → invoke-custom/range
    LongSupplier sup2 = () -> customSinkRange(a, b, c, d, box, extra1, extra2);
    long expected2 = 4688247219638293781L;
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

  // =========================================================
  // 入口：把所有测试跑一遍
  // =========================================================

  public static void main(String[] args) throws Throwable {
    testInvokeStatic();
    testInvokeInstance();
    testInvokeInterface();
    testInvokePolymorphic();
    testInvokeCustom();
    testStringInitNonRange();
    testStringInitRange();
    System.out.println("All nterp invoke tests passed (Java side).");
  }
}
