 

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class RandomObjectChaosTest {

  private static final Random RAND = new Random();
  private static final ReferenceQueue<Validatable> REF_QUEUE = new ReferenceQueue<>();

  public static void main(String[] args) {
    System.out.println("=== RandomObjectChaosTest starting ===");
    int maxAllocs = -1;
    for (String s : args) {
      if (s.startsWith("--maxAllocs=")) {
        maxAllocs = Integer.parseInt(s.substring(s.indexOf('=') + 1));
      } else if ("--short".equals(s)) {
        maxAllocs = 2000;
      } else if (s.matches("\\d+")) {
        maxAllocs = Integer.parseInt(s);
      }
    }
    runPhase(1, maxAllocs);
    forceGc("after phase 1");
    runPhase(2, maxAllocs);
    forceGc("after phase 2");
    System.out.println("=== RandomObjectChaosTest finished ===");
  }

  private static void runPhase(int phase, int maxAllocs) {
    System.out.println("-- Phase " + phase + " begin --");
    List<Validatable> strong = new ArrayList<>();
    List<Reference<? extends Validatable>> refs = new ArrayList<>();
    long approxBytes = 0;
    int seq = 0;
    try {
      while (true) {
        if (maxAllocs > 0 && seq >= maxAllocs) {
          System.out.printf(Locale.ROOT,
                            "Phase %d stopped after %d allocations (~%.1f MB)%n",
                            phase, seq, bytesToMB(approxBytes));
          break;
        }
        Validatable payload = allocateRandomPayload(seq);
        payload.verify();
        approxBytes += payload.approxBytes();
        storeWithRandomReference(payload, strong, refs);
        if (!strong.isEmpty() && (strong.size() & 511) == 0) {
          Validatable sample = strong.get(RAND.nextInt(strong.size()));
          sample.verify();
        }
        if ((seq & 1023) == 0) {
          reportProgress(phase, seq, approxBytes, strong.size(), refs.size());
        }
        seq++;
      }
    } catch (OutOfMemoryError oom) {
      System.out.printf(Locale.ROOT,
                        "Phase %d reached OOM after %d allocations (~%.1f MB)\n",
                        phase, seq, bytesToMB(approxBytes));
    } finally {
      System.out.println("Phase " + phase + " clearing references...");
      strong.clear();
      refs.clear();
    }
  }

  private static void storeWithRandomReference(Validatable payload,
                                               List<Validatable> strong,
                                               List<Reference<? extends Validatable>> refs) {
    int dice = RAND.nextInt(10);
    if (dice < 7) {
      strong.add(payload);
    } else if (dice < 8) {
      refs.add(new WeakReference<>(payload, REF_QUEUE));
    } else if (dice < 9) {
      refs.add(new SoftReference<>(payload, REF_QUEUE));
    } else {
      refs.add(new PhantomReference<>(payload, REF_QUEUE));
    }
    pollReferenceQueue();
  }

  private static Validatable allocateRandomPayload(int seq) {
    switch (RAND.nextInt(4)) {
    case 0:
      return new ByteBox(64 * 1024 + RAND.nextInt(512 * 1024), (byte)(seq & 0xFF));
    case 1:
      return new IntBox(512 + RAND.nextInt(32 * 1024), seq);
    case 2:
      return new StringBox(seq, 256 + RAND.nextInt(2048));
    default:
      return new GraphBox(seq, 8 + RAND.nextInt(64));
    }
  }

  private static void reportProgress(int phase, int seq, long bytes, int strongCount,
                                     int otherCount) {
    System.out.printf(Locale.ROOT,
                      "[phase=%d seq=%d] approxAllocated=%.1f MB, strong=%d, otherRefs=%d%n",
                      phase, seq, bytesToMB(bytes), strongCount, otherCount);
  }

  private static void forceGc(String reason) {
    System.out.println("Requesting GC (" + reason + ")...");
    System.gc();
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    pollReferenceQueue();
  }

  private static void pollReferenceQueue() {
    Reference<? extends Validatable> ref;
    while ((ref = REF_QUEUE.poll()) != null) {
      ref.clear();
    }
  }

  private static double bytesToMB(long bytes) {
    return bytes / (1024.0 * 1024.0);
  }

  // ======== Payload implementations ========

  private interface Validatable {
    void verify();
    long approxBytes();
  }

  private static final class ByteBox implements Validatable {
    private final byte[] data;
    private final byte pattern;

    ByteBox(int size, byte pattern) {
      this.data = new byte[size];
      this.pattern = pattern;
      Arrays.fill(this.data, pattern);
    }

    @Override
    public void verify() {
      if (data.length == 0 || data[0] != pattern || data[data.length - 1] != pattern) {
        throw new AssertionError("ByteBox corrupted (pattern=" + pattern + ")");
      }
    }

    @Override
    public long approxBytes() {
      return data.length;
    }
  }

  private static final class IntBox implements Validatable {
    private final int[] data;
    private final int base;

    IntBox(int len, int base) {
      this.data = new int[len];
      this.base = base;
      for (int i = 0; i < data.length; i++) {
        data[i] = base + i;
      }
    }

    @Override
    public void verify() {
      if (data.length == 0)
        throw new AssertionError("IntBox empty");
      int idx = RAND.nextInt(data.length);
      if (data[idx] != base + idx) {
        throw new AssertionError("IntBox mismatch at " + idx);
      }
    }

    @Override
    public long approxBytes() {
      return data.length * Integer.BYTES;
    }
  }

  private static final class StringBox implements Validatable {
    private final String marker;
    private final String value;

    StringBox(int seq, int len) {
      this.marker = "SB-" + seq;
      this.value = marker + ':' + randomAlpha(len);
    }

    @Override
    public void verify() {
      if (!value.startsWith(marker + ':'))
        throw new AssertionError("StringBox marker mismatch");
      if (value.length() < marker.length() + 2)
        throw new AssertionError("StringBox too short");
    }

    @Override
    public long approxBytes() {
      return value.length() * 2L + 40;
    }
  }

  private static final class GraphBox implements Validatable {
    private final Node head;
    private final long token;
    private final int depth;

    GraphBox(int seq, int depth) {
      this.token = (seq * 2654435761L) ^ System.nanoTime();
      this.depth = depth;
      this.head = build(depth, token);
    }

    private static Node build(int depth, long tokenBase) {
      Node curr = null;
      for (int i = 0; i < depth; i++) {
        curr = new Node(tokenBase + i, curr);
      }
      return curr;
    }

    @Override
    public void verify() {
      Node curr = head;
      long expected = token + depth - 1;
      int count = 0;
      while (curr != null) {
        if (curr.token != expected) {
          throw new AssertionError("GraphBox token mismatch");
        }
        expected--;
        curr = curr.next;
        count++;
      }
      if (count != depth)
        throw new AssertionError("GraphBox depth mismatch");
    }

    @Override
    public long approxBytes() {
      return depth * 32L;
    }

    private static final class Node {
      final long token;
      final Node next;

      Node(long token, Node next) {
        this.token = token;
        this.next = next;
      }
    }
  }

  private static String randomAlpha(int len) {
    char[] chars = new char[len];
    for (int i = 0; i < len; i++) {
      chars[i] = (char)('a' + RAND.nextInt(26));
    }
    return new String(chars);
  }
}
