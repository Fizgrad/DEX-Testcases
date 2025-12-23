package com.art.tests.gc;

import java.lang.ref.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class GcReferenceSuite {

    // --- 小工具 ---
    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError("Assertion failed: " + msg);
    }

    private static void tinySleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    // --- 1) 对象分配 & GC & Finalize & 引用队列 ---
    static final class Finalizable {
        static final AtomicInteger FINALIZED = new AtomicInteger(0);
        @Override protected void finalize() throws Throwable {
            FINALIZED.incrementAndGet();
        }
    }

    private static void testAllocAndGC() {
        System.out.println("[1] Allocation + GC + finalize + ReferenceQueue");

        // 制造一些堆压力
        List<byte[]> keeper = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            keeper.add(new byte[1024]);   // ~2MB
        }

        // 验证 finalize（不保证即时，但通常能看到有数量>0）
        for (int i = 0; i < 2000; i++) {
            new Finalizable();
        }

        // ReferenceQueue + PhantomReference
        ReferenceQueue<Object> rq = new ReferenceQueue<>();
        Object target = new Object();
        PhantomReference<Object> pr = new PhantomReference<>(target, rq);
        assertTrue(pr.get() == null, "PhantomReference.get() must be null");

        target = null;
        keeper = null; // 释放强引用
        System.gc();
        System.runFinalization();

        // 等待 Phantom 入队
        boolean enqueued = false;
        for (int i = 0; i < 200; i++) {
            Reference<?> r = rq.poll();
            if (r != null) { enqueued = true; break; }
            System.gc();
            tinySleep(10);
        }
        System.out.println("  phantom enqueued: " + enqueued);
        System.out.println("  finalized count (>=0): " + Finalizable.FINALIZED.get());
    }

    // --- 2) 软/弱/幻 引用 ---
    private static void testReferences() {
        System.out.println("[2] Soft/Weak/Phantom references");

        // SoftReference：在低内存下才会被回收，这里只验证可用性
        SoftReference<Object> sr = new SoftReference<>(new Object());
        assertTrue(sr.get() != null, "SoftReference should be alive initially");

        // WeakReference：清理通常比较积极
        Object w = new Object();
        WeakReference<Object> wr = new WeakReference<>(w);
        assertTrue(wr.get() != null, "WeakReference should initially point to object");
        w = null;
        for (int i = 0; i < 200; i++) {
            System.gc();
            if (wr.get() == null) break;
            tinySleep(5);
        }
        System.out.println("  weak cleared: " + (wr.get() == null));
    }

    // --- 3) 线程与同步（monitor enter/exit） ---
    private static void testThreadsAndSync() throws InterruptedException {
        System.out.println("[3] Threads + synchronized monitors");
        final Object lock = new Object();
        final int threads = 4;
        final int loopsPerThread = 100_000;
        final int[] counter = {0};
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                for (int i = 0; i < loopsPerThread; i++) {
                    synchronized (lock) {
                        counter[0]++;
                    }
                }
                latch.countDown();
            }).start();
        }

        latch.await();
        System.out.println("  counter = " + counter[0]);
        assertTrue(counter[0] == threads * loopsPerThread, "counter must match");
    }

    // --- 4) 异常与栈追踪 ---
    private static void testExceptions() {
        System.out.println("[4] Exceptions + stack traces");
        try {
            causeDeep(5);
            assertTrue(false, "should not reach here");
        } catch (IllegalStateException ex) {
            StackTraceElement[] st = ex.getStackTrace();
            System.out.println("  caught: " + ex.getClass().getSimpleName()
                    + ", stack depth=" + st.length);
            assertTrue(st.length > 0, "stack trace not empty");
        }
    }

    private static void causeDeep(int n) {
        if (n == 0) throw new IllegalStateException("boom");
        causeDeep(n - 1);
    }

    // --- 5) 反射 ---
    private static void testReflection() throws Exception {
        System.out.println("[5] Reflection invoke");
        Method m = GcReferenceSuite.class.getDeclaredMethod("hiddenAdd", int.class, int.class);
        m.setAccessible(true);
        Object r = m.invoke(null, 7, 35);
        System.out.println("  hiddenAdd(7,35) = " + r);
        assertTrue(((Integer) r) == 42, "reflection result must be 42");
    }

    private static int hiddenAdd(int a, int b) { return a + b; }

    // --- 6) 数组与字符串（常见 dex 查找项：字段/方法/字符串） ---
    private static void testArraysAndStrings() {
        System.out.println("[6] Arrays + Strings");
        int[] arr = new int[8];
        for (int i = 0; i < arr.length; i++) arr[i] = i * i;
        assertTrue(arr[4] == 16, "array write/read");

        Object[] objs = new Object[] { "A", "B", "C" };
        assertTrue("B".equals(objs[1]), "object array");

        String s1 = new String("hello");  // 放入常量池
        String s2 = "hello";
        String s3 = s1.intern();
        assertTrue(s2 == s3, "intern must join to same literal");
        System.out.println("  interned literal equality: " + (s2 == s3));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== ART VM Self-Test START ===");
        testAllocAndGC();
        testReferences();
        testThreadsAndSync();
        testExceptions();
        testReflection();
        testArraysAndStrings();
        System.out.println("=== ART VM Self-Test DONE ===");
    }
}
