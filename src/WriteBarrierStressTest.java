// WriteBarrierStressTest.java
// Tries to expose old->young barrier issues by clearing local refs.

public final class WriteBarrierStressTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();
  private static volatile long BLACKHOLE;
  private static volatile Holder HOLDER_SINK;
  private static volatile Object OBJECT_SINK;
  private static volatile Object STATIC_REF;

  private static final int DEFAULT_ITERS = 8000;
  private static final int DEFAULT_ROUNDS = 3;
  private static final int DEFAULT_ALLOC_SIZE = 1024;
  private static final int DEFAULT_YOUNG_SIZE = 4096;
  private static final int DEFAULT_ALLOC_BURST = 64;
  private static final int DEFAULT_AGE_CYCLES = 3;
  private static final int DEFAULT_AGE_ALLOCS = 8000;
  private static final int DEFAULT_GC_STRIDE = 0;
  private static final int DEFAULT_HOLDERS = 8;
  private static final int DEFAULT_SLOTS = 16;

  private static final class Wrapper {
    Object ref;
    final int tag;

    Wrapper(Object ref, int tag) {
      this.ref = ref;
      this.tag = tag;
    }
  }

  private static final class Holder {
    final int id;
    Object f;
    Object g;
    Object[] slots;
    Wrapper wrap;
    Holder next;

    Holder(int id, int slots) {
      this.id = id;
      this.slots = new Object[Math.max(2, slots)];
    }
  }

  private WriteBarrierStressTest() {}

  public static void main(String[] args) {
    CTR.reset();
    BLACKHOLE = 0;
    HOLDER_SINK = null;
    OBJECT_SINK = null;
    STATIC_REF = null;
    System.out.println("=== WriteBarrierStressTest starting ===");
    int iters = DEFAULT_ITERS;
    int rounds = DEFAULT_ROUNDS;
    int allocSize = DEFAULT_ALLOC_SIZE;
    int youngSize = DEFAULT_YOUNG_SIZE;
    int allocBurst = DEFAULT_ALLOC_BURST;
    int ageCycles = DEFAULT_AGE_CYCLES;
    int ageAllocs = DEFAULT_AGE_ALLOCS;
    int gcStride = DEFAULT_GC_STRIDE;
    int holders = DEFAULT_HOLDERS;
    int slots = DEFAULT_SLOTS;

    for (String s : args) {
      if (s.startsWith("--iters=")) {
        iters = parseInt(s.substring(s.indexOf('=') + 1), iters);
      } else if (s.startsWith("--rounds=")) {
        rounds = parseInt(s.substring(s.indexOf('=') + 1), rounds);
      } else if (s.startsWith("--allocSize=")) {
        allocSize = parseInt(s.substring(s.indexOf('=') + 1), allocSize);
      } else if (s.startsWith("--youngSize=")) {
        youngSize = parseInt(s.substring(s.indexOf('=') + 1), youngSize);
      } else if (s.startsWith("--allocBurst=")) {
        allocBurst = parseInt(s.substring(s.indexOf('=') + 1), allocBurst);
      } else if (s.startsWith("--ageCycles=")) {
        ageCycles = parseInt(s.substring(s.indexOf('=') + 1), ageCycles);
      } else if (s.startsWith("--ageAllocs=")) {
        ageAllocs = parseInt(s.substring(s.indexOf('=') + 1), ageAllocs);
      } else if (s.startsWith("--gcStride=")) {
        gcStride = parseInt(s.substring(s.indexOf('=') + 1), gcStride);
      } else if (s.startsWith("--holders=")) {
        holders = parseInt(s.substring(s.indexOf('=') + 1), holders);
      } else if (s.startsWith("--slots=")) {
        slots = parseInt(s.substring(s.indexOf('=') + 1), slots);
      } else if ("--short".equals(s)) {
        iters = 2000;
        rounds = 2;
        allocSize = 1024;
        youngSize = 2048;
        allocBurst = 16;
        ageCycles = 2;
        ageAllocs = 2000;
        gcStride = 0;
        holders = 4;
        slots = 8;
      } else if ("--full".equals(s)) {
        iters = 20000;
        rounds = 5;
        allocSize = 2048;
        youngSize = 8192;
        allocBurst = 128;
        ageCycles = 5;
        ageAllocs = 20000;
        gcStride = 64;
        holders = 16;
        slots = 32;
      }
    }

    if (iters < 1)
      iters = 1;
    if (rounds < 1)
      rounds = 1;
    if (allocSize < 32)
      allocSize = 32;
    if (youngSize < 128)
      youngSize = 128;
    if (allocBurst < 1)
      allocBurst = 1;
    if (ageCycles < 0)
      ageCycles = 0;
    if (ageAllocs < 0)
      ageAllocs = 0;
    if (gcStride < 0)
      gcStride = 0;
    if (holders < 1)
      holders = 1;
    if (slots < 2)
      slots = 2;

    JitSupport.requestJitCompilation(WriteBarrierStressTest.class);
    warmUp(Math.max(1, iters / 4), allocBurst, allocSize, youngSize, holders,
           slots);

    for (int r = 0; r < rounds; r++) {
      Holder[] holderSet =
          ageHolders(ageCycles, ageAllocs, allocSize, holders, slots);
      int status = runOnce(iters, holderSet, slots, allocBurst, allocSize,
                           youngSize, gcStride);
      TestSupport.checkTrue("writeBarrier.r" + r, status == 0, CTR);
    }

    TestSupport.summary("WriteBarrierStressTest", CTR);
    if (CTR.getFail() != 0)
      System.exit(1);
  }

  private static void warmUp(int iters, int allocBurst, int allocSize,
                             int youngSize, int holders, int slots) {
    Holder[] holderSet = initHolders(holders, slots);
    HOLDER_SINK = holderSet[0];
    OBJECT_SINK = holderSet;
    runOnce(iters, holderSet, slots, Math.max(1, allocBurst / 4), allocSize,
            youngSize, 0);
  }

  private static Holder[] ageHolders(int cycles, int allocs, int allocSize,
                                     int holders, int slots) {
    Holder[] holderSet = initHolders(holders, slots);
    HOLDER_SINK = holderSet[0];
    OBJECT_SINK = holderSet;
    for (int i = 0; i < cycles; i++) {
      if (allocs > 0)
        allocBurst(allocs, allocSize);
      System.gc();
    }
    return holderSet;
  }

  private static Holder[] initHolders(int count, int slots) {
    Holder[] holders = new Holder[Math.max(1, count)];
    for (int i = 0; i < holders.length; i++) {
      holders[i] = new Holder(i, slots);
    }
    for (int i = 0; i < holders.length; i++) {
      holders[i].next = holders[(i + 1) % holders.length];
    }
    return holders;
  }

  private static int runOnce(int iters, Holder[] holderSet, int slots,
                             int allocBurst, int allocSize, int youngSize,
                             int gcStride) {
    int holderCount = holderSet.length;
    int slotCount = Math.max(2, slots);
    for (int i = 0; i < iters; i++) {
      Holder holder = holderSet[i % holderCount];
      int slot = i % slotCount;
      int slot2 = (slot + 1) % slotCount;
      Object young = new byte[youngSize];
      int id = System.identityHashCode(young);
      Wrapper wrap = new Wrapper(young, i);
      holder.f = young;
      holder.g = wrap;
      holder.wrap = wrap;
      if (holder.slots != null) {
        holder.slots[slot] = young;
        holder.slots[slot2] = wrap;
      }
      STATIC_REF = young;
      if ((i & 7) == 0 && holder.next != null) {
        holder.next.f = young;
        if (holder.next.slots != null)
          holder.next.slots[slot] = young;
      }
      young = null;

      allocBurst(allocBurst, allocSize);
      if (gcStride > 0 && (i % gcStride) == 0) {
        System.gc();
      }

      int status = validate(holder, slot, slot2, id, i);
      if (status != 0)
        return status;
      OBJECT_SINK = holder;
    }
    return 0;
  }

  private static int validate(Holder holder, int slot, int slot2, int id,
                              int iter) {
    Object got = holder.f;
    if (got == null) {
      System.out.println("FAIL: field cleared at iter " + iter);
      return -1;
    }
    if (System.identityHashCode(got) != id) {
      System.out.println("FAIL: id mismatch at iter " + iter);
      return -2;
    }
    if (holder.slots == null || holder.slots.length == 0) {
      System.out.println("FAIL: slots missing at iter " + iter);
      return -3;
    }
    Object slotObj = holder.slots[slot];
    if (slotObj == null) {
      System.out.println("FAIL: slot cleared at iter " + iter);
      return -4;
    }
    if (System.identityHashCode(slotObj) != id) {
      System.out.println("FAIL: slot id mismatch at iter " + iter);
      return -5;
    }
    Object slotWrap = holder.slots[slot2];
    if (!(slotWrap instanceof Wrapper)) {
      System.out.println("FAIL: slot wrap missing at iter " + iter);
      return -6;
    }
    Wrapper wrap = (Wrapper)holder.g;
    if (wrap == null || wrap.ref == null) {
      System.out.println("FAIL: wrapper cleared at iter " + iter);
      return -7;
    }
    if (wrap.ref != got) {
      System.out.println("FAIL: wrapper ref mismatch at iter " + iter);
      return -8;
    }
    if (slotWrap != wrap) {
      System.out.println("FAIL: wrapper slot mismatch at iter " + iter);
      return -9;
    }
    if (STATIC_REF == null) {
      System.out.println("FAIL: static cleared at iter " + iter);
      return -10;
    }
    if (System.identityHashCode(STATIC_REF) != id) {
      System.out.println("FAIL: static id mismatch at iter " + iter);
      return -11;
    }
    return 0;
  }

  private static void allocBurst(int count, int size) {
    for (int i = 0; i < count; i++) {
      byte[] junk = new byte[size];
      BLACKHOLE ^= junk.length;
      if ((i & 7) == 0) {
        Object[] objs = new Object[8];
        objs[0] = junk;
        objs[1] = new Object();
        BLACKHOLE ^= objs.length;
      }
    }
  }

  private static int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
