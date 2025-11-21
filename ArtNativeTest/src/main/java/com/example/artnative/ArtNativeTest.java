package com.example.artnative;

import java.util.Arrays;
import java.util.Locale;

public class ArtNativeTest {

    static {
        // 优先用绝对路径，避免 linker namespace 限制；失败则回落到 loadLibrary
        try {
            System.load("/data/local/tmp/libartnativetest.so");
            System.out.println("[JNI] Loaded via System.load absolute path");
        } catch (UnsatisfiedLinkError e) {
            System.loadLibrary("artnativetest");
            System.out.println("[JNI] Loaded via System.loadLibrary");
        }
    }

    // native 声明：与 C++ 原型一一对应
    public static native long nativeProbe(
            long ptr,
            long themePtr,
            int defStyleAttr,
            int defStyleResId,
            long xmlParserPtr,
            int[] javaAttrs,
            long outValuesPtr,
            long outIndicesPtr
    );

    // 与 native 相同的 FNV-like 校验函数（Java 侧）
    private static long checksum(long ptr, long themePtr, int defStyleAttr, int defStyleResId,
                                 long xmlParserPtr, int[] javaAttrs, long outValuesPtr, long outIndicesPtr) {
        long h = 0xcbf29ce484222325L; // FNV offset basis for 64-bit
        h = fnvMix(h, ptr);
        h = fnvMix(h, themePtr);
        h = fnvMix(h, (long) defStyleAttr);
        h = fnvMix(h, (long) defStyleResId);
        h = fnvMix(h, xmlParserPtr);
        h = fnvMix(h, outValuesPtr);
        h = fnvMix(h, outIndicesPtr);
        int len = (javaAttrs == null) ? 0 : javaAttrs.length;
        h = fnvMix(h, (long) len);
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

    public static void main(String[] args) {
        // 构造一组“看起来像指针”的 64-bit 值（仅用于传参压力校验）
        long ptr       = 0x1111111122222222L;
        long themePtr  = 0x3333333344444444L;
        int  defAttr   = 123;
        int  defResId  = 456;
        long xmlPtr    = 0x5555555566666666L;
        int[] attrs    = new int[]{1, 2, 100, 0x7fffffff, -1};
        long outVals   = 0x7777777788888888L;
        long outIdx    = 0x99999999aaaabbbbL;

        // Java 侧打印即将传入的全部参数
        System.out.printf(Locale.ROOT,
                "[Java] ptr=0x%016x theme=0x%016x defAttr=%d defRes=%d xml=0x%016x outVals=0x%016x outIdx=0x%016x attrs=%s%n",
                ptr, themePtr, defAttr, defResId, xmlPtr, outVals, outIdx, Arrays.toString(attrs));

        long expect = checksum(ptr, themePtr, defAttr, defResId, xmlPtr, attrs, outVals, outIdx);
        System.out.printf(Locale.ROOT, "[Java] checksum(java)=0x%016x%n", expect);

        long got = nativeProbe(ptr, themePtr, defAttr, defResId, xmlPtr, attrs, outVals, outIdx);
        System.out.printf(Locale.ROOT, "[Java] checksum(native)=0x%016x%n", got);

        if (expect == got) {
            System.out.println("OK   argument passing identical");
        } else {
            System.out.println("FAIL argument checksum mismatch");
        }
    }
}
