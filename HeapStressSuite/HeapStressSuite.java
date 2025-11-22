import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class HeapStressSuite {

  // —— 长生命周期：模拟“老年代/长期可达” —— //
  private static List<Object> staticHolder = new ArrayList<>();

  // 统计/监控
  private static final AtomicLong bytesAllocated = new AtomicLong();
  private static final Random R = new Random(2025);

  // 引用队列与计数（观察 GC 引用处理）
  private static final ReferenceQueue<Object> REF_Q = new ReferenceQueue<>();
  private static final List<WeakReference<Object>> WEAKS =
      Collections.synchronizedList(new ArrayList<>());
  private static final List<SoftReference<Object>> SOFTS =
      Collections.synchronizedList(new ArrayList<>());
  private static final List<PhantomReference<Object>> PHANTOMS =
      Collections.synchronizedList(new ArrayList<>());
  private static final AtomicLong weakCleared = new AtomicLong();
  private static final AtomicLong phantomEnq = new AtomicLong();

  private static void testVarietyWarmup() {
    System.out.println("\n========== 预热: 多类型短命对象 ==========");
    printMemory("开始前");
    List<Object> ring = new ArrayList<>(4096);
    for (int i = 0; i < 2000; i++) {
      switch (i & 7) {
      case 0:
        ring.add(new byte[512 + (i & 1023)]);
        break; // 小 byte[]
      case 1:
        ring.add(new int[64 + (i & 1023)]);
        break; // int[]
      case 2:
        ring.add(new Object[16 + (i & 255)]);
        break; // Object[]
      case 3:
        ring.add(("S" + i).toCharArray());
        break; // char[]
      case 4:
        ring.add(new String(("K" + i)));
        break; // String
      case 5:
        ring.add(new byte[16 * 1024 + (i & 2047)]);
        break; // >16KB，跨 LOS 阈值
      case 6:
        ring.add(new Integer(i));
        break; // 装箱
      default:
        ring.add(new Object());
        break; // 空对象
      }
      if (ring.size() > 4096)
        ring.set(i & 4095, null); // 环形覆盖，保证短命
      if ((i % 20000) == 0)
        printMemory("进度 i=" + i);
    }
    printMemory("预热后");
    forceGc();
    printMemory("预热回收后");
  }

  // 小对象类型
  static final class Blob {
    int a, b, c, d;
    byte[] payload; // 可调大小
    Blob(int sz) { this.payload = new byte[sz]; }
  }
  static final class Node {
    Node next;
    byte[] chunk;
    Node(int sz) { this.chunk = new byte[sz]; }
  }

  public static void main(String[] args) {
    System.out.println("========== Heap Allocation Test ==========");
    testVarietyWarmup();
    System.out.println("========== 测试场景0: 运行时信息 ==========");
    printRuntimeInfo();

    System.out.println(
        "\n========== 测试场景1: 短命小对象冲刷（minor GC 友好） ==========");
    testShortLivedChurn();

    System.out.println(
        "\n========== 测试场景2: 混合对象图 + 晋升（部分保留） ==========");
    testMixedObjectGraphPromotion();

    System.out.println(
        "\n========== 测试场景3: 巨对象/大数组（大对象空间压力） ==========");
    testLargeObjects();

    System.out.println("\n========== 测试场景4: "
                       + "容器扩容（ArrayList/HashMap）+ 释放 ==========");
    testCollectionsChurn();

    System.out.println(
        "\n========== 测试场景5: 不同引用语义（Weak/Soft/Phantom） ==========");
    testJavaReferences();

    System.out.println(
        "\n========== 测试场景6: ThreadLocal 缓冲区分配/替换 ==========");
    testThreadLocalChurn();

    System.out.println(
        "\n========== 测试场景7: 字符串驻留/去重压力（intern） ==========");
    testStringInterns();

    System.out.println(
        "\n========== 测试场景8: 多线程分配（并发/停留少量引用） ==========");
    testMultithreadedAllocation(/*seconds*/ 10, /*ringSize*/ 512);

    System.out.println(
        "\n========== 测试场景9: 大量分配后回收（你的原始场景1） ==========");
    testMassiveAllocationWithGC();

    System.out.println(
        "\n========== 测试场景10: 大量分配不回收（你的原始场景2） ==========");
    testMassiveAllocationWithoutGC();

    // 汇总
    System.out.println("\n========== 汇总 ==========");
    System.out.printf(
        Locale.ROOT,
        "bytesAllocated≈%.1f MB, weakCleared=%d, phantomEnqueued=%d%n",
        bytesAllocated.get() / (1024.0 * 1024.0), weakCleared.get(),
        phantomEnq.get());
  }

  // ========== 场景实现 ==========

  /** 场景1：大量短命小对象，观察 minor GC 行为 */
  private static void testShortLivedChurn() {
    printMemory("开始前");
    final int iters = 200_000;
    for (int i = 1; i <= iters; i++) {
      // 混合不同类型且不保留引用（短命）
      allocateOneEphemeral(256 + R.nextInt(1024)); // byte[]
      allocateIntArray(64 + R.nextInt(512));       // int[]
      allocateObjectArray(16 + R.nextInt(64));     // Object[]
      if ((i % 10) == 0)
        new Blob(64 + R.nextInt(512)); // 小对象
      if ((i % 20) == 0)
        newString(16 + R.nextInt(64)); // String
      if (i % 20_000 == 0) {
        printMemory("短命进度 i=" + i);
      }
    }
    printMemory("分配后");
    forceGc();
    printMemory("回收后");
  }

  /** 场景2：构造部分保留的对象图，触发晋升与存活复制/标记-整理 */
  private static void testMixedObjectGraphPromotion() {
    printMemory("开始前");

    List<Node> roots = new ArrayList<>();
    // 生成若干链表，每段链上保留前 30%，其余丢弃
    for (int r = 0; r < 256; r++) {
      Node head = null;
      for (int i = 0; i < 512; i++) {
        Node n = new Node(256 + R.nextInt(1024));
        n.next = head;
        head = n;
        bytesAllocated.addAndGet(n.chunk.length);
      }
      roots.add(head);
    }
    printMemory("构造对象图后");

    // 保留 30% 的根，其余丢弃 → 局部老化/晋升
    int keep = (int)(roots.size() * 0.3);
    staticHolder.addAll(roots.subList(0, keep));
    roots = null;

    forceGc();
    printMemory("一次 GC 后（应仍有保留存活）");

    // 释放保留，让它们成为垃圾
    staticHolder.clear();
    forceGc();
    printMemory("全部释放后");
  }

  /** 场景3：大对象/大数组，对应大对象空间/直入老年代的策略 */
  private static void testLargeObjects() {
    printMemory("开始前");
    List<Object> bigs = new ArrayList<>();
    final long max = Runtime.getRuntime().maxMemory();
    // 目标压力 ≈ 可用最大堆的 1/4，分块 1MB
    long target = Math.max(32L * 1024 * 1024, max / 4);
    long acc = 0;
    try {
      while (acc < target) {
        byte[] block = new byte[1 * 1024 * 1024]; // 1MB
        bigs.add(block);
        acc += block.length;
        bytesAllocated.addAndGet(block.length);
        if (bigs.size() % 16 == 0)
          printMemory("已分配大块: " + (bigs.size()) + "MB");
      }
    } catch (OutOfMemoryError oom) {
      System.out.println(
          "（提示）大对象发生 OOME（可忽略，本场景是有意逼近上限）");
    }
    printMemory("大对象分配后");

    // 释放一半，再 GC，观察回收与碎片情况
    int half = bigs.size() / 2;
    for (int i = 0; i < half; i++)
      bigs.set(i, null);
    forceGc();
    printMemory("释放一半后");

    bigs.clear();
    forceGc();
    printMemory("全部释放后");
  }

  /** 场景4：容器扩容与缩容（大量装箱、小对象、rehash） */
  private static void testCollectionsChurn() {
    printMemory("开始前");
    List<List<Blob>> lists = new ArrayList<>();
    List<Map<Integer, Blob>> maps = new ArrayList<>();

    for (int i = 0; i < 64; i++) {
      List<Blob> l = new ArrayList<>();
      Map<Integer, Blob> m = new HashMap<>();
      for (int k = 0; k < 10_000; k++) {
        Blob b = new Blob(64 + (k % 256));
        if ((k & 3) == 0)
          l.add(b); // 部分进入 list
        if ((k & 7) == 0)
          m.put(k, b); // 部分进入 map（装箱、rehash）
        bytesAllocated.addAndGet(b.payload.length);
      }
      lists.add(l);
      maps.add(m);
      if ((i & 7) == 0)
        printMemory("容器批次 i=" + i);
    }
    printMemory("容器构建后");

    // 清理，提示 GC 回收散落对象
    lists.clear();
    maps.clear();
    forceGc();
    printMemory("容器释放后");
  }

  /** 场景5：Weak/Soft/Phantom 引用行为 */
  private static void testJavaReferences() {
    printMemory("开始前");

    // 准备一些弱/软/幻引用，马上丢掉强引用
    for (int i = 0; i < 10_000; i++) {
      Object strong = new byte[8 * 1024];                  // 8KB
      WEAKS.add(new WeakReference<>(strong));              // 立刻可回收
      SOFTS.add(new SoftReference<>(new byte[64 * 1024])); // 64KB
      PHANTOMS.add(new PhantomReference<>(new Object(), REF_Q));
      strong = null;
    }
    // 压力：再分配一批，触发 soft 清理（是否清理取决于实现/压力）
    List<byte[]> press = new ArrayList<>();
    for (int i = 0; i < 2000; i++)
      press.add(new byte[64 * 1024]);

    forceGc();
    pollRefQueue();

    printMemory("一次 GC 后（弱应清、软视压力、幻入队）");

    press = null;
    forceGc();
    pollRefQueue();
    printMemory("二次 GC 后");
  }

  /** 场景6：ThreadLocal 缓冲区分配与替换 */
  private static void testThreadLocalChurn() {
    printMemory("开始前");
    final ThreadLocal<byte[]> TL = new ThreadLocal<>();
    for (int i = 0; i < 2000; i++) {
      byte[] buf = new byte[8 * 1024 + R.nextInt(8 * 1024)];
      TL.set(buf); // 替换旧值
      bytesAllocated.addAndGet(buf.length);
      if ((i & 255) == 0)
        printMemory("TL 迭代 i=" + i);
    }
    TL.remove(); // 断开引用
    forceGc();
    printMemory("回收后");
  }

  /** 场景7：字符串驻留（intern） */
  private static void testStringInterns() {
    printMemory("开始前");
    List<String> pool = new ArrayList<>();
    for (int i = 0; i < 200_000; i++) {
      String s = ("K" + i + ":" + (i * 2654435761L));
      pool.add(s.intern()); // 驻留
      if ((i & 8191) == 0)
        printMemory("intern 进度 i=" + i);
    }
    printMemory("驻留后");
    pool.clear();
    forceGc();
    printMemory("释放后");
  }

  /** 场景8：多线程分配，短暂保留在每线程环形缓冲，逼真促发并发 GC */
  private static void testMultithreadedAllocation(int seconds, int ringSize) {
    printMemory("开始前");
    final int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
    final CountDownLatch stop = new CountDownLatch(1);
    ExecutorService es = Executors.newFixedThreadPool(threads);
    for (int t = 0; t < threads; t++) {
      es.submit(() -> workerLoop(stop, ringSize));
    }
    sleepMs(seconds * 1000L);
    stop.countDown();
    es.shutdownNow();
    try {
      es.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
    }

    forceGc();
    printMemory("并发压力结束后");
  }

  /** 你的原始场景1：大量分配 -> 释放 -> GC -> 再来一轮 */
  private static void testMassiveAllocationWithGC() {
    printMemory("开始前");
    int count = 200000;
    List<byte[]> tempList = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      byte[] b = new byte[1024];
      tempList.add(b);
      bytesAllocated.addAndGet(b.length);
      if (i % 1000 == 0)
        printMemory("i=" + i);
    }
    printMemory("分配后");
    tempList = null;
    forceGc();
    printMemory("回收后");

    tempList = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      byte[] b = new byte[1024];
      tempList.add(b);
      bytesAllocated.addAndGet(b.length);
      if (i % 1000 == 0)
        printMemory("i=" + i);
    }
    printMemory("分配后(第二轮)");
    tempList = null;
    forceGc();
    printMemory("回收后(第二轮)");
  }

  /** 你的原始场景2：不释放直到 OOME */
  private static void testMassiveAllocationWithoutGC() {
    printMemory("开始前");
    try {
      while (true) {
        byte[] b = new byte[1024];
        staticHolder.add(b); // 持有引用
        bytesAllocated.addAndGet(b.length);
        if (staticHolder.size() % 500 == 0)
          printMemory("已分配对象数: " + staticHolder.size());
      }
    } catch (OutOfMemoryError e) {
      staticHolder = null; // 释放引用
      forceGc();
      printMemory("溢出回收后");
    }
  }

  // ========== 分配/工作线程/工具函数 ==========

  private static void workerLoop(CountDownLatch stop, int ringSize) {
    Object[] ring = new Object[ringSize];
    int idx = 0;
    while (stop.getCount() > 0) {
      // 混合分配：byte[] / int[] / Object[] / String / Blob
      switch (R.nextInt(5)) {
      case 0:
        ring[idx] = new byte[256 + R.nextInt(16 * 1024)];
        break;
      case 1:
        ring[idx] = new int[64 + R.nextInt(4096)];
        break;
      case 2:
        ring[idx] = new Object[16 + R.nextInt(1024)];
        break;
      case 3:
        ring[idx] = newString(32 + R.nextInt(128));
        break;
      default:
        ring[idx] = new Blob(64 + R.nextInt(1024));
      }
      // 少量保留（环形覆盖），模拟“存活少量、绝大多数短命”
      int sz = sizeOf(ring[idx]);
      bytesAllocated.addAndGet(sz);
      idx = (idx + 1) % ring.length;

      // 偶尔制造弱/幻引用（随后即可被清理）
      if ((idx & 255) == 0) {
        Object o = new Object();
        WEAKS.add(new WeakReference<>(o));
        PHANTOMS.add(new PhantomReference<>(new Object(), REF_Q));
      }
      if ((idx & 1023) == 0)
        pollRefQueue();

      if ((bytesAllocated.get() & 0xFFFF) == 0)
        Thread.yield();
    }
  }

  private static void allocateOneEphemeral(int byteSize) {
    byte[] b = new byte[byteSize];
    bytesAllocated.addAndGet(b.length);
  }

  private static int[] allocateIntArray(int n) {
    int[] a = new int[n];
    bytesAllocated.addAndGet(n * 4L);
    return a;
  }

  private static Object[] allocateObjectArray(int n) {
    Object[] a = new Object[n];
    bytesAllocated.addAndGet(n * 8L);
    return a;
  }

  private static String newString(int len) {
    char[] cs = new char[len];
    for (int i = 0; i < len; i++)
      cs[i] = (char)('a' + (i % 26));
    String s = new String(cs); // 不 intern，短命
    bytesAllocated.addAndGet(40 + cs.length * 2L);
    return s;
  }

  private static int sizeOf(Object o) {
    if (o instanceof byte[])
      return ((byte[])o).length;
    if (o instanceof int[])
      return ((int[])o).length * 4;
    if (o instanceof Object[])
      return ((Object[])o).length * 8;
    if (o instanceof String)
      return 40 + ((String)o).length() * 2;
    if (o instanceof Blob)
      return 16 + ((Blob)o).payload.length;
    return 64; // 估算
  }

  /** 轮询处理引用队列，记录清理计数 */
  private static void pollRefQueue() {
    Reference<?> ref;
    while ((ref = REF_Q.poll()) != null) {
      if (ref instanceof PhantomReference)
        phantomEnq.incrementAndGet();
    }
    // 粗略统计 weak 清理（弱引用 get() 返回 null）
    for (Iterator<WeakReference<Object>> it = WEAKS.iterator(); it.hasNext();) {
      WeakReference<Object> w = it.next();
      if (w.get() == null) {
        weakCleared.incrementAndGet();
        it.remove();
      }
    }
  }

  /** 强制执行 GC 并稍等一会（便于观察） */
  private static void forceGc() {
    System.out.println("\n触发垃圾回收...");
    System.gc();
    sleepMs(800); // 给 GC 一点时间（可按需调整）
    pollRefQueue();
  }

  private static void printRuntimeInfo() {
    System.out.printf(Locale.ROOT, "availableProcessors=%d, maxMemory=%.1fMB%n",
                      Runtime.getRuntime().availableProcessors(),
                      bytesToMB(Runtime.getRuntime().maxMemory()));
    printMemory("初始");
  }

  /** 打印内存状态（free/total/max） */
  private static void printMemory(String phase) {
    Runtime runtime = Runtime.getRuntime();
    long free = runtime.freeMemory();
    long total = runtime.totalMemory();
    long max = runtime.maxMemory();
    System.out.printf(
        Locale.ROOT, "[%s] 内存状态: 可用=%.1fMB, 已分配=%.1fMB, 最大=%.1fMB%n",
        phase, bytesToMB(free), bytesToMB(total), bytesToMB(max));
  }

  private static double bytesToMB(long bytes) {
    return bytes / (1024.0 * 1024.0);
  }

  private static void sleepMs(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
