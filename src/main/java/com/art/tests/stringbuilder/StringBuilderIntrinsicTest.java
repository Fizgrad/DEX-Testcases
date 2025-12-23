package com.art.tests.stringbuilder;

import java.nio.ByteBuffer;

public final class StringBuilderIntrinsicTest {

  private StringBuilderIntrinsicTest() {}

  public static void main(String[] args) {

    // Direct ByteBuffer（native 分配）
    ByteBuffer bb = ByteBuffer.allocateDirect(1024);
    bb.putInt(0, 0xCAFEBABE);
    bb.putInt(0, 889275714);

    System.out.println("Starting StringBuilder 64-bit Intrinsic Tests...");
    System.out.println(bb.getInt(0));
    System.out.println(bb.getInt(1));
    try {
      testBasicTypes();
      testReferenceTypes();
      testWideTypesAlignment();
      testFloatingPointConversion();
      testComplexMix();
      testPathConstruction(); // 模拟导致 ICU Crash 的场景
      testLargeStringConcats();
      testBuilderReuseAndSlices();
      // testStringFloodUntilOom();

      System.out.println("SUCCESS: All StringBuilder tests passed!");
    } catch (Throwable t) {
      System.err.println("FAILURE: Test failed!");
      t.printStackTrace();
      System.exit(1);
    }
  }

  private static void assertEquals(String expected, String actual) {
    System.out.println("Asserting: expected='" + expected + "', actual='" +
                       actual + "'");
    if (!expected.equals(actual)) {
      throw new RuntimeException("Assertion failed!\nExpected: '" + expected +
                                 "'\nActual:   '" + actual + "'");
    }
  }

  // 1. 测试基础类型 (int, char, boolean) - 验证 64位 vreg 读取低位/转换是否正常
  private static void testBasicTypes() {
    System.out.println("Test 1: Basic Types");
    StringBuilder sb = new StringBuilder();
    sb.append(0xCAFEBABE);
    sb.append('A');
    sb.append(true);
    sb.append(false);

    String result = sb.toString();
    assertEquals("-889275714Atruefalse", result);
  }

  // 2. 测试引用类型 (String) - 验证 64位 指针读取 (reinterpret_cast 修复验证)
  private static void testReferenceTypes() {
    System.out.println("Test 2: Reference Types");
    String s1 = "Hello";
    String s2 = "World";
    String s3 = null; // 测试 null 处理

    StringBuilder sb = new StringBuilder();
    sb.append(s1);
    sb.append(" "); // 字面量
    sb.append(s2);
    sb.append(s3);

    String result = sb.toString();
    assertEquals("Hello Worldnull", result);
  }

  // 3. 测试宽类型对齐 (Long) - 核心测试点
  // 如果 C++ 代码中没有正确跳过 Padding vreg，后面的参数会读错
  private static void testWideTypesAlignment() {
    System.out.println("Test 3: Wide Types Alignment (Long)");
    long l1 = 0x1234567890ABCDEFL; // 大数，确保占用 64位
    int i1 = 999;
    long l2 = -1L;
    char c1 = 'Z';

    StringBuilder sb = new StringBuilder();
    // 链式调用，参数在栈上应该是 [Long, Padding, Int, Long, Padding, Char]
    sb.append(l1).append(i1).append(l2).append(c1);

    String result = sb.toString();
    // 如果 Long 没跳过 Padding，i1 可能会读到 l1 的高位或垃圾数据
    assertEquals("1311768467294899695999-1Z", result);
  }

  // 4. 测试浮点数 (Float/Double) - 验证 bit_cast 和 ConvertFpArgs 逻辑
  private static void testFloatingPointConversion() {
    System.out.println("Test 4: Floating Point");
    double d1 = 3.14159;
    float f1 = 1.23f;
    double d2 = -0.005;

    StringBuilder sb = new StringBuilder();
    // 栈布局: [Double, Padding, Float, Double, Padding]
    sb.append(d1).append(f1).append(d2);

    String result = sb.toString();
    assertEquals("3.141591.23-0.005", result);
  }

  // 5. 复杂混合测试 - 模拟极端情况，确保指针偏移在长序列中不漂移
  private static void testComplexMix() {
    System.out.println("Test 5: Complex Mix");
    boolean bool = true;
    int i = 42;
    long l = 10000000000L;
    double d = 99.99;
    String s = "End";

    StringBuilder sb = new StringBuilder();
    // 顺序: Bool(1), Int(1), Long(2), Double(2), String(1) -> 总共 7 个
    // 64位槽位
    sb.append("Start:")
        .append(bool) // true
        .append("-")
        .append(i) // 42
        .append("-")
        .append(l) // 10000000000
        .append("-")
        .append(d) // 99.99
        .append("-")
        .append(s); // End

    String result = sb.toString();
    assertEquals("Start:true-42-10000000000-99.99-End", result);
  }

  // 6. 模拟 ICU Crash 场景 - 路径拼接
  // Crash 是因为 ICUResourceBundle 拿到了错误的 String key。
  // 这通常发生在 append(String).append(char).append(String) 这种模式下。
  // 如果中间的 char 或 int 处理导致指针错位，后面的 String
  // 指针就会读成垃圾值，导致 NPE 或 Segfault。
  private static void testPathConstruction() {
    System.out.println("Test 6: Path Construction (ICU Simulation)");
    String packageBase = "android.icu.impl";
    String name = "ICUResourceBundle";
    String ext = "OpenType";
    int version = 1;

    StringBuilder sb = new StringBuilder();
    // 模拟类似: base + "." + name + "$" + ext + version
    sb.append(packageBase);
    sb.append('.');     // char (64-bit vreg)
    sb.append(name);    // String (64-bit ref)
    sb.append('$');     // char
    sb.append(ext);     // String
    sb.append(version); // int

    String result = sb.toString();
    assertEquals("android.icu.impl.ICUResourceBundle$OpenType1", result);

    // 验证结果是否真的可用（不包含不可见垃圾字符）

    System.out.println(result.charAt(result.indexOf('$')));
  }

  // 7. 大量字符串拼接，用于拉伸 StringBuilder 内部缓冲区增长策略
  private static void testLargeStringConcats() {
    System.out.println("Test 7: Large String Concats");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 20_000; i++) {
      sb.append("segment-")
          .append(i)
          .append('#')
          .append((char)('a' + (i % 26)))
          .append('|');
    }
    String result = sb.toString();
    if (!result.startsWith("segment-0") || !result.contains("segment-19999")) {
      throw new AssertionError("Large concat content mismatch");
    }
    for (int probe : new int[] {0, 1234, 19999}) {
      char letter = (char)('a' + (probe % 26));
      String pattern = "segment-" + probe + "#" + letter + "|";
      if (!result.contains(pattern))
        throw new AssertionError("Missing block pattern: " + pattern);
    }
    System.out.println("Large concat total length=" + result.length());
  }

  // 8. 反复复用 StringBuilder 并切片，验证 capacity/length 调整逻辑
  private static void testBuilderReuseAndSlices() {
    System.out.println("Test 8: Builder Reuse & Slices");
    String base = "0123456789ABCDEF";
    String reverse = new StringBuilder(base).reverse().toString();
    StringBuilder sb = new StringBuilder(256);
    for (int i = 0; i < 10_000; i++) {
      sb.setLength(0);
      sb.append(base)
          .append('-')
          .append(i)
          .append('-')
          .append(reverse)
          .append(':')
          .append(base.substring(i % base.length()));
      String snapshot = sb.toString();
      if (!snapshot.startsWith(base))
        throw new AssertionError("Iteration " + i + " missing base prefix");
      if (!snapshot.contains("-" + i + "-"))
        throw new AssertionError("Iteration " + i + " lost numeric payload");
      String suffix = base.substring(i % base.length());
      if (!snapshot.endsWith(suffix))
        throw new AssertionError("Iteration " + i + " wrong suffix slice");
    }
    System.out.println("Builder reuse iterations completed");
  }

  // 9. 极端情况：持续生成并持有大字符串，直至触发 OOM，以验证
  //    StringBuilder/String 对堆的影响（用户需求）
  private static void testStringFloodUntilOom() {
    System.out.println("Test 9: String Flood (expect OOM)");
    java.util.List<String> holder = new java.util.ArrayList<>();
    long totalChars = 0;
    int strings = 0;
    try {
      while (true) {
        StringBuilder sb = new StringBuilder(256 * 1024);
        for (int i = 0; i < 2048; i++) {
          sb.append("payload-")
              .append(strings)
              .append('-')
              .append(i)
              .append(':');
        }
        String s = sb.toString();
        String sentinel = "payload-" + strings + "-2047:";
        if (!s.contains(sentinel))
          throw new AssertionError("Flood string missing sentinel " + sentinel);
        holder.add(s);
        totalChars += s.length();
        strings++;
        if ((strings & 63) == 0) {
          System.out.println("strings=" + strings + ", total chars≈" +
                             (totalChars / 1024) + " KiB");
          String sample = holder.get(holder.size() / 2);
          if (!sample.startsWith("payload-"))
            throw new AssertionError(
                "Sample corrupted; prefix=" +
                sample.substring(0, Math.min(16, sample.length())));
        }
      }
    } catch (OutOfMemoryError oom) {
      System.out.println("OOM triggered after holding " + strings +
                         " strings (~" + (totalChars / (1024 * 1024)) +
                         " MiB of char data)");
    } finally {
      holder.clear();
      // 尽力释放堆，方便后续测试/诊断
      holder = null;
      System.gc();
    }
  }
}
