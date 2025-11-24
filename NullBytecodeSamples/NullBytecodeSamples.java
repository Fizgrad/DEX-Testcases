public final class NullBytecodeSamples {
  // 用 volatile 增强可见性与副作用，使写入更难被移除
  private static volatile Object sField;
  // 数组写入路径会生成 aput-object null
  private static final Object[] BOX = new Object[1];

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

  // 标记为 synchronized 通常会抑制激进内联，保持调用边界（Debug 构建尤甚）
  private static synchronized Object identity(Object v) { return v; }

  public static void main(String[] args) {
    System.out.println("storeNullToStaticField: " + storeNullToStaticField());
    System.out.println("storeNullToArray: " + storeNullToArray());
    System.out.println("localBecomesNullThenEscapes: " +
                       localBecomesNullThenEscapes());
  }
}
