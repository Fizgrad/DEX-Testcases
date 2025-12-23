package com.art.tests.nullbytecode;

public final class NullBytecodeSamples {
  // 用 volatile 增强可见性与副作用，使写入更难被移除
  private static volatile Object sField;
  // 数组写入路径会生成 aput-object null
  private static final Object[] BOX = new Object[1];

  private static void assertEquals(String name, int expect, int actual) {
    if (expect != actual) {
      throw new AssertionError(name + " expect=" + expect +
                               " actual=" + actual);
    }
  }

  /** 场景1：把引用写入静态字段后，再写 null（期望 sput-object null） */
  public static int storeNullToStaticField() {
    Object tmp = new Object();
    sField = tmp;

    // 轻量“黑洞”，让 tmp 被认为仍活着，防止过度优化
    if (System.nanoTime() == 0)
      System.out.println(tmp);

    sField = null;                   // <-- 这里会生成对字段写入 null 的字节码
    return (sField == null) ? 1 : 0; // 读取以保留上面的写入语义
  }

  /** 场景2：把引用写入数组，再写回 null（期望 aput-object null） */
  public static Object storeNullToArray() {
    Object x = new Object();
    BOX[0] = x;

    // 再把同一个槽位设为 null，确保出现 aput-object null
    BOX[0] = null; // <-- 这里会生成 aput-object null
    return BOX[0]; // 读出以保留副作用
  }

  /** 场景3：局部变量被置 null 并通过调用“逃逸”，保留 move/const null */
  public static Object localBecomesNullThenEscapes() {
    Object o = new Object();
    // 使控制流稍复杂，保证 o 活到置空处
    if (System.currentTimeMillis() < 0) {
      return o;
    }
    o = null;           // <-- 这里会生成把局部设为 null 的指令
    return identity(o); // 让 o 逃逸，避免死代码消除
  }

  public Object getObject() {
    Object c = new Object[1];
    return c;
  }

  // 标记为 synchronized 通常会抑制激进内联，保持调用边界（Debug 构建尤甚）
  private static synchronized Object identity(Object v) { return v; }

  private static int compareTwoRefs(Object a, Object b) {
    return (a == b) ? 1 : 0;
  }

  private static int compareRefToZero(Object ref) {
    return ref == null ? 1 : 0;
  }

  private static Object maybeNullThroughCall(Object anchor, boolean forceNull) {
    Object alias = identity(anchor); // 保留一次调用，覆盖 invoke 路径
    if (forceNull) {
      return null;
    }
    sField = alias; // volatile 写，保证 alias 被视为存活
    return alias;
  }

  private static int compareAfterCall(Object anchor, boolean forceNull) {
    Object fromCall = maybeNullThroughCall(anchor, forceNull);
    if (fromCall == null)
      return 1;
    return (fromCall == anchor) ? 2 : 3;
  }

  private static int phiMerge(int mode, Object shared) {
    Object candidate;
    switch (mode) {
    case 0:
      candidate = null;
      break;
    case 1:
      candidate = shared;
      break;
    default:
      candidate = new Object();
    }
    if (candidate == null)
      return 5;
    return candidate == shared ? 7 : 11;
  }

  private static void testDex2OatRefComparisons(String[] args) {
    Object shared = new Object();
    assertEquals("ref equality", 1, compareTwoRefs(shared, shared));
    assertEquals("ref inequality", 0, compareTwoRefs(shared, new Object()));

    assertEquals("null check", 1, compareRefToZero(null));
    assertEquals("nonnull check", 0, compareRefToZero(shared));

    assertEquals("call returns null", 1, compareAfterCall(shared, true));
    assertEquals("call keeps alias", 2, compareAfterCall(shared, false));

    int mode = args.length % 3; // 非编译期常量，强制 SSA 里生成 Phi
    int expected = (mode == 0) ? 5 : (mode == 1 ? 7 : 11);
    assertEquals("phi merge mode=" + mode, expected, phiMerge(mode, shared));

    System.out.println("dex2oat ref-compare tests passed (mode=" + mode + ")");
  }

  static void run(String[] args) {
    int a = -1;
    if (args.length > 0) {
      System.out.println(a);
    }
    NullBytecodeSamples test = new NullBytecodeSamples();
    Object c = test.getObject();
    Object d = new Object();
    if (c == d) {
      System.out.println(a);
    }
    System.out.println("storeNullToStaticField: " + storeNullToStaticField());
    System.out.println("storeNullToArray: " + storeNullToArray());
    System.out.println("localBecomesNullThenEscapes: " +
                       localBecomesNullThenEscapes());
    testDex2OatRefComparisons(args);
  }

  public static void main(String[] args) {
    run(args);
    run(args);
    run(args);
  }
}
