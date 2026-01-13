// GcRootStackMapTest.java
// Stresses stack map / GC root liveness with local-only references.
 

public final class GcRootStackMapTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();
  private static volatile Object SINK;
  private static volatile int BLACKHOLE;

  private static final int DEFAULT_ITERS = 20000;
  private static final int DEFAULT_ROUNDS = 5;
  private static final int DEFAULT_ALLOC_SIZE = 1024;
  private static final int DEFAULT_GC_STRIDE = 256;
  private static final int DEFAULT_CHAIN_LEN = 16;

  private static final class Marker {
    final int id;
    final byte[] data;
    final int expected;

    Marker(int id, int size) {
      this.id = id;
      int len = Math.max(16, size);
      this.data = new byte[len];
      for (int i = 0; i < len; i += Math.max(1, len / 8)) {
        data[i] = (byte)(id + i);
      }
      this.expected = computeChecksum();
    }

    int computeChecksum() {
      int sum = id ^ data.length;
      sum ^= data[0] << 8;
      sum ^= data[data.length - 1] << 16;
      return sum;
    }

    boolean verify() { return computeChecksum() == expected; }

    int touch(int iter) {
      int idx = (data.length == 0) ? 0 : (iter % data.length);
      int v = expected ^ id ^ (data.length << 1);
      v ^= data[idx];
      return v + iter;
    }
  }

  private static final class Node {
    final int tag;
    Object payload;
    Node next;
    Node alt;

    Node(int tag, Object payload) {
      this.tag = tag;
      this.payload = payload;
    }

    int walk(int steps) {
      Node n = this;
      int acc = 0;
      for (int i = 0; i < steps; i++) {
        if (n == null)
          return Integer.MIN_VALUE;
        acc ^= n.tag;
        Object p = n.payload;
        if (p != null)
          acc ^= p.hashCode();
        n = ((i & 1) == 0) ? n.next : n.alt;
      }
      return acc;
    }
  }

  private static final class RootResult {
    final Object keep;
    final Marker primary;
    final Marker secondary;
    final Node head;
    final int checksum;

    RootResult(Object keep, Marker primary, Marker secondary, Node head,
               int checksum) {
      this.keep = keep;
      this.primary = primary;
      this.secondary = secondary;
      this.head = head;
      this.checksum = checksum;
    }
  }

  private GcRootStackMapTest() {}

  public static void main(String[] args) {
    System.out.println("=== GcRootStackMapTest starting ===");
    int iters = DEFAULT_ITERS;
    int rounds = DEFAULT_ROUNDS;
    int allocSize = DEFAULT_ALLOC_SIZE;
    int gcStride = DEFAULT_GC_STRIDE;
    int chainLen = DEFAULT_CHAIN_LEN;

    for (String s : args) {
      if (s.startsWith("--iters=")) {
        iters = parseInt(s.substring(s.indexOf('=') + 1), iters);
      } else if (s.startsWith("--rounds=")) {
        rounds = parseInt(s.substring(s.indexOf('=') + 1), rounds);
      } else if (s.startsWith("--allocSize=")) {
        allocSize = parseInt(s.substring(s.indexOf('=') + 1), allocSize);
      } else if (s.startsWith("--gcStride=")) {
        gcStride = parseInt(s.substring(s.indexOf('=') + 1), gcStride);
      } else if (s.startsWith("--chainLen=")) {
        chainLen = parseInt(s.substring(s.indexOf('=') + 1), chainLen);
      } else if ("--short".equals(s)) {
        iters = 5000;
        rounds = 2;
        allocSize = 1024;
        gcStride = 256;
        chainLen = 8;
      } else if ("--full".equals(s)) {
        iters = 60000;
        rounds = 8;
        allocSize = 2048;
        gcStride = 128;
        chainLen = 48;
      }
    }

    if (iters < 1)
      iters = 1;
    if (rounds < 1)
      rounds = 1;
    if (allocSize < 32)
      allocSize = 32;
    if (gcStride < 0)
      gcStride = 0;
    if (chainLen < 4)
      chainLen = 4;

    JitSupport.requestJitCompilation(GcRootStackMapTest.class);
    warmUp(Math.max(1, iters / 4), gcStride, allocSize, chainLen);

    for (int r = 0; r < rounds; r++) {
      RootResult res = hotRoot(iters, gcStride, allocSize, chainLen);
      SINK = res;
      TestSupport.checkTrue("root.keep.nonNull.r" + r,
                            res != null && res.keep != null, CTR);
      if (res != null && res.keep != null) {
        int id = System.identityHashCode(res.keep);
        TestSupport.checkTrue("root.keep.idStable.r" + r,
                              id == System.identityHashCode(res.keep), CTR);
        TestSupport.checkTrue("root.keep.class.r" + r,
                              res.keep.getClass() == Object.class, CTR);
      }
      TestSupport.checkTrue("root.marker.primary.r" + r,
                            res != null && res.primary != null &&
                                res.primary.verify(),
                            CTR);
      TestSupport.checkTrue("root.marker.secondary.r" + r,
                            res != null && res.secondary != null &&
                                res.secondary.verify(),
                            CTR);
      int walk = (res == null || res.head == null)
          ? Integer.MIN_VALUE
          : res.head.walk(Math.min(chainLen, 8));
      TestSupport.checkTrue("root.graph.walk.r" + r,
                            walk != Integer.MIN_VALUE, CTR);
      TestSupport.checkTrue("root.graph.alt.r" + r,
                            res != null && res.head != null &&
                                res.head.alt != null,
                            CTR);
    }

    TestSupport.summary("GcRootStackMapTest", CTR);
    if (CTR.getFail() != 0)
      System.exit(1);
  }

  private static void warmUp(int iters, int gcStride, int allocSize,
                             int chainLen) {
    for (int i = 0; i < 2; i++) {
      hotRoot(iters, gcStride, allocSize, chainLen);
    }
  }

  private static RootResult hotRoot(int iters, int gcStride, int allocSize,
                                    int chainLen) {
    Object keep = new Object();
    int primarySize = Math.max(64, allocSize / 2);
    int secondarySize = Math.max(32, allocSize / 3);
    Marker primary = new Marker(0x1234, primarySize);
    Marker secondary = new Marker(0x5678, secondarySize);
    Node head = buildRing(chainLen, primary, secondary);
    Object[] roots = new Object[] {keep, primary, secondary, head, head.next};
    int sum = 0;
    for (int i = 0; i < iters; i++) {
      byte[] junk = new byte[allocSize];
      sum ^= junk.length;
      if ((i & 15) == 0) {
        Object[] tmp = new Object[4];
        tmp[0] = keep;
        tmp[1] = primary;
        tmp[2] = secondary;
        tmp[3] = head;
        sum ^= tmp.length;
      }
      sum ^= mixRoots(roots, head, primary, secondary, keep, i);
      if ((i & 31) == 0) {
        sum ^= head.walk((i & 7) + 1);
      }
      if (gcStride > 0 && (i % gcStride) == 0) {
        System.gc();
      }
    }
    BLACKHOLE ^= sum;
    return new RootResult(keep, primary, secondary, head, sum);
  }

  private static Node buildRing(int len, Marker primary, Marker secondary) {
    int count = Math.max(4, len);
    Node head = new Node(0, primary);
    Node prev = head;
    for (int i = 1; i < count; i++) {
      Object payload = ((i & 1) == 0) ? primary : secondary;
      if ((i & 7) == 0)
        payload = new Object();
      Node n = new Node(i, payload);
      prev.next = n;
      prev = n;
    }
    prev.next = head;
    Node cur = head;
    for (int i = 0; i < count; i++) {
      int skip = (i & 3) + 1;
      Node alt = cur;
      for (int s = 0; s < skip; s++)
        alt = alt.next;
      cur.alt = alt;
      cur = cur.next;
    }
    return head;
  }

  private static int mixRoots(Object[] roots, Node head, Marker primary,
                              Marker secondary, Object keep, int iter) {
    int acc = 0;
    try {
      acc ^= primary.touch(iter);
      acc ^= secondary.touch(iter ^ 0x5a5a);
      acc ^= head.walk((iter & 3) + 1);
      switch (iter & 3) {
      case 0:
        roots[0] = keep;
        break;
      case 1:
        roots[1] = primary;
        break;
      case 2:
        roots[2] = secondary;
        break;
      default:
        roots[3] = head;
        break;
      }
    } finally {
      acc ^= keep.hashCode();
      acc ^= roots.length;
    }
    if ((iter & 7) == 0) {
      Node n = ((iter & 1) == 0) ? head : head.alt;
      if (n != null)
        acc ^= n.tag;
    }
    return acc;
  }

  private static int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
