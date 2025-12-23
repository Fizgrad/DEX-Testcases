// StringEqualsTest.java
// Cover String.equals paths: self, null, non-String, length mismatch,
// early/late char mismatch, empty, and different backing instances.

package com.art.tests.stringequals;

import com.art.tests.common.TestSupport;

public final class StringEqualsTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();

  private static final int DEFAULT_REPEAT = 1;
  private static final int DEFAULT_SLEEP_MS = 0;

  private StringEqualsTest() {}

  private static void log(String msg) { System.out.println(msg); }

  private static void checkTrue(String name, boolean ok) {
    TestSupport.checkTrue(name, ok, CTR);
  }

  private static void checkFalse(String name, boolean ok) {
    checkTrue(name, !ok);
  }

  private static void testSelfAndNull() {
    log("== self/null/non-string ==");
    String a = "abc";
    checkTrue("self", a.equals(a));
    checkFalse("null", a.equals(null));
    checkFalse("otherType", a.equals(new Object()));
  }

  private static void testDifferentLengths() {
    log("== length mismatch ==");
    checkFalse("len.short-vs-long", "a".equals("ab"));
    checkFalse("len.long-vs-short", "hello".equals("he"));
  }

  private static void testEarlyMismatch() {
    log("== first char mismatch ==");
    checkFalse("firstChar.diff", "abc".equals("xbc"));
  }

  private static void testLateMismatch() {
    log("== late char mismatch ==");
    checkFalse("middleChar.diff", "abcd".equals("abxd"));
    checkFalse("lastChar.diff", "abcd".equals("abce"));
  }

  private static void testEqualContentDifferentObjects() {
    log("== equal content different instances ==");
    String literal = "hello world";
    String fromNew = new String("hello world");
    String fromBuilder =
        new StringBuilder().append("hello").append(" world").toString();
    checkTrue("equal.new", literal.equals(fromNew));
    checkTrue("equal.builder", literal.equals(fromBuilder));
  }

  private static void testEmptyAndSingleChar() {
    log("== empty and single char ==");
    checkTrue("empty", "".equals(""));
    checkTrue("single.same", "x".equals("x"));
    checkFalse("single.diff", "x".equals("y"));
  }

  private static void testMixedCase() {
    log("== mixed case ==");
    checkFalse("case.diff", "abc".equals("Abc"));
    checkTrue("case.same", "ABC".equals("ABC"));
  }

  public static void runAllTests() {
    log("== String.equals coverage ==");
    CTR.reset();
    testSelfAndNull();
    testDifferentLengths();
    testEarlyMismatch();
    testLateMismatch();
    testEqualContentDifferentObjects();
    testEmptyAndSingleChar();
    testMixedCase();
    log("== DONE ==");
    TestSupport.summary("StringEqualsTest", CTR);
  }

  public static void main(String[] args) {
    int repeat = DEFAULT_REPEAT;
    int sleepMs = DEFAULT_SLEEP_MS;
    for (String s : args) {
      if (s.startsWith("--repeat=")) {
        repeat = Integer.parseInt(s.substring(s.indexOf('=') + 1));
      } else if (s.startsWith("--sleepMs=")) {
        sleepMs = Integer.parseInt(s.substring(s.indexOf('=') + 1));
      } else if ("--short".equals(s)) {
        repeat = 1;
        sleepMs = 0;
      } else if (s.matches("\\d+")) {
        repeat = Integer.parseInt(s);
      }
    }
    for (int i = 0; i < repeat; i++) {
      try {
        runAllTests();
        if (sleepMs > 0)
          Thread.sleep(sleepMs);
      } catch (Exception e) {
        System.out.println("Exception: " + e);
      }
    }
  }
}
