package com.art.tests.nativeinterop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ArtNativeTest {

  static {
    // 优先用绝对路径，避免 linker namespace 限制；失败则回落到 loadLibrary
    try {
      System.load("/data/local/tmp/libartnativetest.so");
      logJava("Loaded via System.load absolute path");
    } catch (UnsatisfiedLinkError e) {
      System.loadLibrary("artnativetest");
      logJava("Loaded via System.loadLibrary");
    }
  }

  // native 声明：与 C++ 原型一一对应
  public static native long nativeProbe(long ptr, long themePtr,
                                        int defStyleAttr, int defStyleResId,
                                        long xmlParserPtr, int[] javaAttrs,
                                        long outValuesPtr, long outIndicesPtr);

  public static native boolean nativeBooleanEcho(boolean value);
  public static native byte nativeByteEcho(byte value);
  public static native char nativeCharEcho(char value);
  public static native short nativeShortEcho(short value);
  public static native int nativeIntEcho(int value);
  public static native long nativeLongEcho(long value);
  public static native float nativeFloatEcho(float value);
  public static native double nativeDoubleEcho(double value);
  public static native String nativeStringEcho(String value);
  public static native byte[] nativeByteArrayEcho(byte[] value);
  public static native int[] nativeIntArrayEcho(int[] value);
  public static native long[] nativeLongArrayEcho(long[] value);
  public static native boolean[] nativeBooleanArrayEcho(boolean[] value);
  public static native long
  nativeMultiPrimitiveChecksum(boolean boolVal, byte byteVal, char charVal,
                               short shortVal, int intVal, long longVal,
                               float floatVal, double doubleVal);
  public static native long
  nativeMixedReferenceChecksum(boolean flag, String text, int[] ints,
                               long[] longs, boolean[] bools);
  public static native long nativeComboChecksum3(int a, long b, double c);
  public static native long nativeComboChecksum5(boolean flag, byte b, short s,
                                                 int i, long l);
  public static native long nativeComboChecksum7(float f1, double d1, long l1,
                                                 int i1, short s1, byte b1,
                                                 boolean flag);
  public static native long nativeComboChecksum9(int a1, int a2, long l1,
                                                 long l2, float f1, float f2,
                                                 double d1, double d2,
                                                 boolean flag);
  public static native long nativeComboChecksum13(long l1, long l2, long l3,
                                                  long l4, long l5, int i1,
                                                  int i2, int i3, short s1,
                                                  short s2, byte b1,
                                                  boolean flag, double d1);
  public static native long
  nativeComboChecksum20(long p1, long p2, long p3, long p4, long p5, long p6,
                        long p7, long p8, long p9, long p10, int i1, int i2,
                        int i3, int i4, int i5, short s1, short s2, byte b1,
                        byte b2, boolean flag);

  private static final long FNV_OFFSET = 0xcbf29ce484222325L;

  // 与 native 相同的 FNV-like 校验函数（Java 侧）
  private static long checksum(long ptr, long themePtr, int defStyleAttr,
                               int defStyleResId, long xmlParserPtr,
                               int[] javaAttrs, long outValuesPtr,
                               long outIndicesPtr) {
    long h = FNV_OFFSET; // FNV offset basis for 64-bit
    h = fnvMix(h, ptr);
    h = fnvMix(h, themePtr);
    h = fnvMix(h, (long)defStyleAttr);
    h = fnvMix(h, (long)defStyleResId);
    h = fnvMix(h, xmlParserPtr);
    h = fnvMix(h, outValuesPtr);
    h = fnvMix(h, outIndicesPtr);
    int len = (javaAttrs == null) ? 0 : javaAttrs.length;
    h = fnvMix(h, (long)len);
    if (javaAttrs != null) {
      for (int v : javaAttrs) {
        h = fnvMix(h, v & 0xffffffffL);
      }
    }
    return h;
  }

  private static long fnvMix(long h, long v) {
    h ^= v;
    h *= 0x100000001b3L; // FNV prime
    return h;
  }

  private static final List<String> ERRORS = new ArrayList<>();

  private static void expect(boolean condition, String message) {
    if (!condition) {
      ERRORS.add(message);
      logJava("ERROR recorded: %s", message);
    }
  }

  private static void logJava(String format, Object... args) {
    System.out.printf(Locale.ROOT, "[java] : " + format + "%n", args);
  }

  public static void main(String[] args) {
    runPointerChecksumTest();
    runPrimitiveRoundTripTests();
    runArrayRoundTripTests();
    runStringRoundTripTest();
    runMultiParameterTests();
    runComboCountTests();
    printSummary();
  }

  private static void runPointerChecksumTest() {
    System.out.println();
    logJava("=== Pointer checksum test ===");
    long ptr = 0x1111111122222222L;
    long themePtr = 0x3333333344444444L;
    int defAttr = 123;
    int defResId = 456;
    long xmlPtr = 0x5555555566666666L;
    int[] attrs = new int[] {1, 2, 100, 0x7fffffff, -1};
    long outVals = 0x7777777788888888L;
    long outIdx = 0x99999999aaaabbbbL;

    logJava("ptr=0x%016x theme=0x%016x defAttr=%d defRes=%d xml=0x%016x " +
            "outVals=0x%016x outIdx=0x%016x attrs=%s",
            ptr, themePtr, defAttr, defResId, xmlPtr, outVals, outIdx,
            Arrays.toString(attrs));

    long expect = checksum(ptr, themePtr, defAttr, defResId, xmlPtr, attrs,
                           outVals, outIdx);
    logJava("checksum(java)=0x%016x", expect);

    long got = nativeProbe(ptr, themePtr, defAttr, defResId, xmlPtr, attrs,
                           outVals, outIdx);
    logJava("checksum(native)=0x%016x", got);

    if (expect == got) {
      logJava("OK argument passing identical");
    } else {
      logJava("FAIL argument checksum mismatch");
    }
  }

  private static void runPrimitiveRoundTripTests() {
    System.out.println();
    logJava("=== Primitive round-trip tests ===");

    boolean boolVal = true;
    logJava("boolean send=%b", boolVal);
    boolean boolNative = nativeBooleanEcho(boolVal);
    logJava("boolean native=%b", boolNative);
    expect(boolNative == boolVal, "boolean mismatch");

    byte byteVal = (byte)0x7a;
    logJava("byte send=%d", byteVal);
    byte byteNative = nativeByteEcho(byteVal);
    logJava("byte native=%d", byteNative);
    expect(byteNative == byteVal, "byte mismatch");

    char charVal = '@';
    logJava("char send='%c'(0x%04x)", charVal, (int)charVal);
    char charNative = nativeCharEcho(charVal);
    logJava("char native='%c'(0x%04x)", charNative, (int)charNative);
    expect(charNative == charVal, "char mismatch");

    short shortVal = (short)-1234;
    logJava("short send=%d", shortVal);
    short shortNative = nativeShortEcho(shortVal);
    logJava("short native=%d", shortNative);
    expect(shortNative == shortVal, "short mismatch");

    int intVal = 0x1234abcd;
    logJava("int send=0x%08x", intVal);
    int intNative = nativeIntEcho(intVal);
    logJava("int native=0x%08x", intNative);
    expect(intNative == intVal, "int mismatch");

    long longVal = 0x0102030405060708L;
    logJava("long send=0x%016x", longVal);
    long longNative = nativeLongEcho(longVal);
    logJava("long native=0x%016x", longNative);
    expect(longNative == longVal, "long mismatch");

    float floatVal = -123.5f;
    logJava("float send=%.3f", floatVal);
    float floatNative = nativeFloatEcho(floatVal);
    logJava("float native=%.3f", floatNative);
    expect(Float.compare(floatNative, floatVal) == 0, "float mismatch");

    double doubleVal = Math.PI;
    logJava("double send=%.6f", doubleVal);
    double doubleNative = nativeDoubleEcho(doubleVal);
    logJava("double native=%.6f", doubleNative);
    expect(Double.compare(doubleNative, doubleVal) == 0, "double mismatch");
  }

  private static void runArrayRoundTripTests() {
    System.out.println();
    logJava("=== Array round-trip tests ===");

    byte[] bytes = new byte[] {1, 2, 3, -1};
    logJava("byte[] send=%s", Arrays.toString(bytes));
    byte[] bytesNative = nativeByteArrayEcho(bytes);
    logJava("byte[] native=%s", Arrays.toString(bytesNative));
    expect(Arrays.equals(bytes, bytesNative), "byte[] mismatch");

    int[] ints = new int[] {-100, 0, 42, 0x7fffffff};
    logJava("int[] send=%s", Arrays.toString(ints));
    int[] intsNative = nativeIntArrayEcho(ints);
    logJava("int[] native=%s", Arrays.toString(intsNative));
    expect(Arrays.equals(ints, intsNative), "int[] mismatch");

    long[] longs = new long[] {0L, 1L, -1L, 0x00ff00ff00ff00ffL};
    logJava("long[] send=%s", Arrays.toString(longs));
    long[] longsNative = nativeLongArrayEcho(longs);
    logJava("long[] native=%s", Arrays.toString(longsNative));
    expect(Arrays.equals(longs, longsNative), "long[] mismatch");

    boolean[] bools = new boolean[] {true, false, true};
    logJava("boolean[] send=%s", Arrays.toString(bools));
    boolean[] boolsNative = nativeBooleanArrayEcho(bools);
    logJava("boolean[] native=%s", Arrays.toString(boolsNative));
    expect(Arrays.equals(bools, boolsNative), "boolean[] mismatch");
  }

  private static void runStringRoundTripTest() {
    System.out.println();
    logJava("=== Reference (String) round-trip test ===");

    String text = "JNI round-trip check";
    logJava("String send=\"%s\"", text);
    String textNative = nativeStringEcho(text);
    logJava("String native=\"%s\"", textNative);
    expect(text.equals(textNative), "String mismatch");
  }

  private static void runMultiParameterTests() {
    System.out.println();
    logJava("=== Multi-parameter checksum tests ===");

    boolean boolVal = false;
    byte byteVal = (byte)0xfe;
    char charVal = 'Z';
    short shortVal = (short)0x1337;
    int intVal = -2024;
    long longVal = 0xfedcba9876543210L;
    float floatVal = 42.25f;
    double doubleVal = -0.125;

    logJava("multi primitives -> bool=%b byte=%d char='%c'(0x%04x) short=%d " +
            "int=%d long=0x%016x float=%.3f double=%.3f",
            boolVal, byteVal, charVal, (int)charVal, shortVal, intVal, longVal,
            floatVal, doubleVal);

    long expectMulti =
        checksumMultiPrimitive(boolVal, byteVal, charVal, shortVal, intVal,
                               longVal, floatVal, doubleVal);
    long gotMulti =
        nativeMultiPrimitiveChecksum(boolVal, byteVal, charVal, shortVal,
                                     intVal, longVal, floatVal, doubleVal);
    logJava("checksum multi primitives java=0x%016x native=0x%016x",
            expectMulti, gotMulti);
    expect(expectMulti == gotMulti, "multi primitive checksum mismatch");

    boolean flag = true;
    String text = "Multi-param JNI";
    int[] ints = new int[] {10, -10, 1000};
    long[] longs = new long[] {1L, 2L, 3L, 4L};
    boolean[] bools = new boolean[] {false, true};

    logJava("mixed refs -> flag=%b text=\"%s\" ints=%s longs=%s bools=%s", flag,
            text, Arrays.toString(ints), Arrays.toString(longs),
            Arrays.toString(bools));

    long expectMixed = checksumMixedReference(flag, text, ints, longs, bools);
    long gotMixed =
        nativeMixedReferenceChecksum(flag, text, ints, longs, bools);
    logJava("checksum mixed java=0x%016x native=0x%016x", expectMixed,
            gotMixed);
    expect(expectMixed == gotMixed, "mixed reference checksum mismatch");
  }

  private static void runComboCountTests() {
    System.out.println();
    logJava("=== Parameter-count stress tests ===");

    int combo3A = 0x11111111;
    long combo3B = 0x2222222233333333L;
    double combo3C = 1.5d;
    logJava("combo3 inputs -> a=0x%08x b=0x%016x c=%.4f", combo3A, combo3B,
            combo3C);
    long combo3Expect = checksumCombo3(combo3A, combo3B, combo3C);
    long combo3Got = nativeComboChecksum3(combo3A, combo3B, combo3C);
    logCombo("combo3", combo3Expect, combo3Got);

    boolean combo5Flag = true;
    byte combo5B = (byte)0xaa;
    short combo5S = (short)0x7f7f;
    int combo5I = -77;
    long combo5L = 0x4444444455555555L;
    logJava("combo5 inputs -> flag=%b byte=%d short=0x%04x int=%d long=0x%016x",
            combo5Flag, combo5B, combo5S & 0xffff, combo5I, combo5L);
    long combo5Expect =
        checksumCombo5(combo5Flag, combo5B, combo5S, combo5I, combo5L);
    long combo5Got =
        nativeComboChecksum5(combo5Flag, combo5B, combo5S, combo5I, combo5L);
    logCombo("combo5", combo5Expect, combo5Got);

    float combo7F1 = 0.25f;
    double combo7D1 = -1234.75;
    long combo7L1 = 0x6666666677777777L;
    int combo7I1 = 314159;
    short combo7S1 = (short)-200;
    byte combo7B1 = (byte)0x7c;
    boolean combo7Flag = false;
    logJava(
        "combo7 inputs -> f1=%.3f d1=%.3f l1=0x%016x i1=%d s1=%d b1=%d flag=%b",
        combo7F1, combo7D1, combo7L1, combo7I1, combo7S1, combo7B1, combo7Flag);
    long combo7Expect = checksumCombo7(combo7F1, combo7D1, combo7L1, combo7I1,
                                       combo7S1, combo7B1, combo7Flag);
    long combo7Got = nativeComboChecksum7(
        combo7F1, combo7D1, combo7L1, combo7I1, combo7S1, combo7B1, combo7Flag);
    logCombo("combo7", combo7Expect, combo7Got);

    int combo9A1 = 10;
    int combo9A2 = -10;
    long combo9L1 = 0x0101010101010101L;
    long combo9L2 = 0x0202020202020202L;
    float combo9F1 = 5.5f;
    float combo9F2 = -9.25f;
    double combo9D1 = 0.0001d;
    double combo9D2 = -0.0002d;
    boolean combo9Flag = true;
    logJava("combo9 inputs -> ints=[%d,%d] longs=[0x%016x,0x%016x] " +
            "floats=[%.3f,%.3f] doubles=[%.6f,%.6f] flag=%b",
            combo9A1, combo9A2, combo9L1, combo9L2, combo9F1, combo9F2,
            combo9D1, combo9D2, combo9Flag);
    long combo9Expect =
        checksumCombo9(combo9A1, combo9A2, combo9L1, combo9L2, combo9F1,
                       combo9F2, combo9D1, combo9D2, combo9Flag);
    long combo9Got =
        nativeComboChecksum9(combo9A1, combo9A2, combo9L1, combo9L2, combo9F1,
                             combo9F2, combo9D1, combo9D2, combo9Flag);
    logCombo("combo9", combo9Expect, combo9Got);

    long combo13L1 = 1L;
    long combo13L2 = 2L;
    long combo13L3 = 3L;
    long combo13L4 = 4L;
    long combo13L5 = 5L;
    int combo13I1 = 100;
    int combo13I2 = 200;
    int combo13I3 = 300;
    short combo13S1 = (short)400;
    short combo13S2 = (short)500;
    byte combo13B1 = (byte)0x33;
    boolean combo13Flag = false;
    double combo13D1 = 123.456;
    logJava("combo13 inputs -> longs=%s ints=%s shorts=[%d,%d] byte=%d " +
            "flag=%b double=%.3f",
            Arrays.toString(new long[] {combo13L1, combo13L2, combo13L3,
                                        combo13L4, combo13L5}),
            Arrays.toString(new int[] {combo13I1, combo13I2, combo13I3}),
            combo13S1, combo13S2, combo13B1, combo13Flag, combo13D1);
    long combo13Expect =
        checksumCombo13(combo13L1, combo13L2, combo13L3, combo13L4, combo13L5,
                        combo13I1, combo13I2, combo13I3, combo13S1, combo13S2,
                        combo13B1, combo13Flag, combo13D1);
    long combo13Got = nativeComboChecksum13(
        combo13L1, combo13L2, combo13L3, combo13L4, combo13L5, combo13I1,
        combo13I2, combo13I3, combo13S1, combo13S2, combo13B1, combo13Flag,
        combo13D1);
    logCombo("combo13", combo13Expect, combo13Got);

    long[] combo20Longs =
        new long[] {0x1L, 0x2L, 0x3L, 0x4L, 0x5L, 0x6L, 0x7L, 0x8L, 0x9L, 0xAL};
    int[] combo20Ints = new int[] {10, 11, 12, 13, 14};
    short combo20S1 = (short)15;
    short combo20S2 = (short)16;
    byte combo20B1 = (byte)17;
    byte combo20B2 = (byte)18;
    boolean combo20Flag = true;
    logJava("combo20 inputs -> longs=%s ints=%s shorts=[%d,%d] bytes=[%d,%d] " +
            "flag=%b",
            Arrays.toString(combo20Longs), Arrays.toString(combo20Ints),
            combo20S1, combo20S2, combo20B1, combo20B2, combo20Flag);
    long combo20Expect = checksumCombo20(
        combo20Longs[0], combo20Longs[1], combo20Longs[2], combo20Longs[3],
        combo20Longs[4], combo20Longs[5], combo20Longs[6], combo20Longs[7],
        combo20Longs[8], combo20Longs[9], combo20Ints[0], combo20Ints[1],
        combo20Ints[2], combo20Ints[3], combo20Ints[4], combo20S1, combo20S2,
        combo20B1, combo20B2, combo20Flag);
    long combo20Got = nativeComboChecksum20(
        combo20Longs[0], combo20Longs[1], combo20Longs[2], combo20Longs[3],
        combo20Longs[4], combo20Longs[5], combo20Longs[6], combo20Longs[7],
        combo20Longs[8], combo20Longs[9], combo20Ints[0], combo20Ints[1],
        combo20Ints[2], combo20Ints[3], combo20Ints[4], combo20S1, combo20S2,
        combo20B1, combo20B2, combo20Flag);
    logCombo("combo20", combo20Expect, combo20Got);
  }

  private static long checksumMultiPrimitive(boolean boolVal, byte byteVal,
                                             char charVal, short shortVal,
                                             int intVal, long longVal,
                                             float floatVal, double doubleVal) {
    long h = FNV_OFFSET;
    h = fnvMix(h, boolVal ? 1 : 0);
    h = fnvMix(h, byteVal & 0xffL);
    h = fnvMix(h, charVal);
    h = fnvMix(h, shortVal & 0xffffL);
    h = fnvMix(h, intVal & 0xffffffffL);
    h = fnvMix(h, longVal);
    h = fnvMix(h, Float.floatToIntBits(floatVal) & 0xffffffffL);
    h = fnvMix(h, Double.doubleToLongBits(doubleVal));
    return h;
  }

  private static long checksumMixedReference(boolean flag, String text,
                                             int[] ints, long[] longs,
                                             boolean[] bools) {
    long h = FNV_OFFSET;
    h = fnvMix(h, flag ? 1 : 0);
    int textLen = (text == null) ? 0 : text.length();
    h = fnvMix(h, textLen);
    if (text != null) {
      for (int i = 0; i < text.length(); ++i) {
        h = fnvMix(h, text.charAt(i));
      }
    }
    int intsLen = (ints == null) ? 0 : ints.length;
    h = fnvMix(h, intsLen);
    if (ints != null) {
      for (int value : ints) {
        h = fnvMix(h, value & 0xffffffffL);
      }
    }
    int longsLen = (longs == null) ? 0 : longs.length;
    h = fnvMix(h, longsLen);
    if (longs != null) {
      for (long value : longs) {
        h = fnvMix(h, value);
      }
    }
    int boolsLen = (bools == null) ? 0 : bools.length;
    h = fnvMix(h, boolsLen);
    if (bools != null) {
      for (boolean value : bools) {
        h = fnvMix(h, value ? 1 : 0);
      }
    }
    return h;
  }

  private static void logCombo(String name, long expect, long got) {
    logJava("%s checksum java=0x%016x native=0x%016x", name, expect, got);
    expect(expect == got, name + " checksum mismatch");
  }

  private static void printSummary() {
    System.out.println();
    logJava("=== Test summary ===");
    if (ERRORS.isEmpty()) {
      logJava("All tests passed");
    } else {
      logJava("Total failures: %d", ERRORS.size());
      for (String message : ERRORS) {
        logJava(" - %s", message);
      }
    }
  }

  private static long checksumCombo3(int a, long b, double c) {
    long h = FNV_OFFSET;
    h = fnvMix(h, a & 0xffffffffL);
    h = fnvMix(h, b);
    h = fnvMix(h, Double.doubleToLongBits(c));
    return h;
  }

  private static long checksumCombo5(boolean flag, byte b, short s, int i,
                                     long l) {
    long h = FNV_OFFSET;
    h = fnvMix(h, flag ? 1 : 0);
    h = fnvMix(h, b & 0xffL);
    h = fnvMix(h, s & 0xffffL);
    h = fnvMix(h, i & 0xffffffffL);
    h = fnvMix(h, l);
    return h;
  }

  private static long checksumCombo7(float f1, double d1, long l1, int i1,
                                     short s1, byte b1, boolean flag) {
    long h = FNV_OFFSET;
    h = fnvMix(h, Float.floatToIntBits(f1) & 0xffffffffL);
    h = fnvMix(h, Double.doubleToLongBits(d1));
    h = fnvMix(h, l1);
    h = fnvMix(h, i1 & 0xffffffffL);
    h = fnvMix(h, s1 & 0xffffL);
    h = fnvMix(h, b1 & 0xffL);
    h = fnvMix(h, flag ? 1 : 0);
    return h;
  }

  private static long checksumCombo9(int a1, int a2, long l1, long l2, float f1,
                                     float f2, double d1, double d2,
                                     boolean flag) {
    long h = FNV_OFFSET;
    h = fnvMix(h, a1 & 0xffffffffL);
    h = fnvMix(h, a2 & 0xffffffffL);
    h = fnvMix(h, l1);
    h = fnvMix(h, l2);
    h = fnvMix(h, Float.floatToIntBits(f1) & 0xffffffffL);
    h = fnvMix(h, Float.floatToIntBits(f2) & 0xffffffffL);
    h = fnvMix(h, Double.doubleToLongBits(d1));
    h = fnvMix(h, Double.doubleToLongBits(d2));
    h = fnvMix(h, flag ? 1 : 0);
    return h;
  }

  private static long checksumCombo13(long l1, long l2, long l3, long l4,
                                      long l5, int i1, int i2, int i3, short s1,
                                      short s2, byte b1, boolean flag,
                                      double d1) {
    long h = FNV_OFFSET;
    h = fnvMix(h, l1);
    h = fnvMix(h, l2);
    h = fnvMix(h, l3);
    h = fnvMix(h, l4);
    h = fnvMix(h, l5);
    h = fnvMix(h, i1 & 0xffffffffL);
    h = fnvMix(h, i2 & 0xffffffffL);
    h = fnvMix(h, i3 & 0xffffffffL);
    h = fnvMix(h, s1 & 0xffffL);
    h = fnvMix(h, s2 & 0xffffL);
    h = fnvMix(h, b1 & 0xffL);
    h = fnvMix(h, flag ? 1 : 0);
    h = fnvMix(h, Double.doubleToLongBits(d1));
    return h;
  }

  private static long checksumCombo20(long p1, long p2, long p3, long p4,
                                      long p5, long p6, long p7, long p8,
                                      long p9, long p10, int i1, int i2, int i3,
                                      int i4, int i5, short s1, short s2,
                                      byte b1, byte b2, boolean flag) {
    long h = FNV_OFFSET;
    h = fnvMix(h, p1);
    h = fnvMix(h, p2);
    h = fnvMix(h, p3);
    h = fnvMix(h, p4);
    h = fnvMix(h, p5);
    h = fnvMix(h, p6);
    h = fnvMix(h, p7);
    h = fnvMix(h, p8);
    h = fnvMix(h, p9);
    h = fnvMix(h, p10);
    h = fnvMix(h, i1 & 0xffffffffL);
    h = fnvMix(h, i2 & 0xffffffffL);
    h = fnvMix(h, i3 & 0xffffffffL);
    h = fnvMix(h, i4 & 0xffffffffL);
    h = fnvMix(h, i5 & 0xffffffffL);
    h = fnvMix(h, s1 & 0xffffL);
    h = fnvMix(h, s2 & 0xffffL);
    h = fnvMix(h, b1 & 0xffL);
    h = fnvMix(h, b2 & 0xffL);
    h = fnvMix(h, flag ? 1 : 0);
    return h;
  }
}
