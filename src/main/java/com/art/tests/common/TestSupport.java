// TestSupport.java
// Shared lightweight assertions and counters for simple test modules.
package com.art.tests.common;

public final class TestSupport {
  public static final class Counter {
    private int pass;
    private int fail;

    public void incPass() { pass++; }
    public void incFail() { fail++; }
    public int getPass() { return pass; }
    public int getFail() { return fail; }
    public void reset() { pass = 0; fail = 0; }
  }

  private TestSupport() {}

  public static void log(String msg) { System.out.println(msg); }

  public static void checkEq(String name, int got, int exp, Counter c) {
    if (got != exp) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      c.incFail();
    } else {
      System.out.println("OK   " + name + ": " + got);
      c.incPass();
    }
  }

  public static void checkEq(String name, long got, long exp, Counter c) {
    if (got != exp) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      c.incFail();
    } else {
      System.out.println("OK   " + name + ": " + got);
      c.incPass();
    }
  }

  public static void checkEq(String name, Object got, Object exp, Counter c) {
    boolean ok = (exp == null ? got == null : exp.equals(got));
    if (!ok) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      c.incFail();
    } else {
      System.out.println("OK   " + name + ": " + got);
      c.incPass();
    }
  }

  public static void checkApprox(String name, double got, double exp,
                                 double eps, Counter c) {
    if (Math.abs(got - exp) > eps) {
      System.out.println("FAIL " + name + ": got=" + got + " exp=" + exp);
      c.incFail();
    } else {
      System.out.println("OK   " + name + ": " + got);
      c.incPass();
    }
  }

  public static void checkTrue(String name, boolean ok, Counter c) {
    if (!ok) {
      System.out.println("FAIL " + name);
      c.incFail();
    } else {
      System.out.println("OK   " + name);
      c.incPass();
    }
  }

  public static void summary(String name, Counter c) {
    System.out.println(name + " PASS=" + c.getPass() + " FAIL=" + c.getFail());
  }
}
