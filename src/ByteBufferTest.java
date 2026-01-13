// ByteBufferTest.java
// Smoke tests for heap/direct buffers, views, ordering, slicing, duplication, compact, and read-only paths.
 

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

public final class ByteBufferTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();

  private ByteBufferTest() {}

  public static void main(String[] args) {
    System.out.println("== ByteBuffer tests ==");
    try {
      testHeapVsDirect();
      testOrderAndPrimitives();
      testSliceAndDuplicate();
      testMarkReset();
      testCompact();
      testArrayWrapSharing();
      testReadOnly();
      TestSupport.summary("ByteBufferTest", CTR);
    } catch (Throwable t) {
      t.printStackTrace();
      System.out.println("FAIL=" + (CTR.getFail() + 1));
      System.exit(1);
    }
  }

  private static void testHeapVsDirect() {
    ByteBuffer heap = ByteBuffer.allocate(32);
    ByteBuffer direct = ByteBuffer.allocateDirect(32);
    checkTrue("heap.isDirect", !heap.isDirect());
    checkTrue("direct.isDirect", direct.isDirect());
    checkEq("heap.capacity", heap.capacity(), 32);
    checkEq("direct.capacity", direct.capacity(), 32);
  }

  private static void testOrderAndPrimitives() {
    ByteBuffer buf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
    buf.putInt(0x11223344);
    buf.putShort((short)0x5566);
    buf.putChar('A');
    buf.putLong(0x778899AABBCCDDL);
    buf.putFloat(1.25f);
    buf.putDouble(-2.5d);
    buf.flip();
    checkEq("int.big", buf.getInt(), 0x11223344);
    checkEq("short.big", buf.getShort(), (short)0x5566);
    checkEq("char.big", buf.getChar(), (int)'A');
    checkEq("long.big", buf.getLong(), 0x778899AABBCCDDL);
    checkApprox("float.big", buf.getFloat(), 1.25f, 1e-6f);
    checkApprox("double.big", buf.getDouble(), -2.5d, 1e-9);

    buf.clear().order(ByteOrder.LITTLE_ENDIAN);
    buf.putInt(0x11223344).putShort((short)0x5566).putChar('B');
    buf.flip();
    checkEq("int.little", buf.getInt(), 0x11223344);
    checkEq("short.little", buf.getShort(), (short)0x5566);
    checkEq("char.little", buf.getChar(), (int)'B');
  }

  private static void testSliceAndDuplicate() {
    ByteBuffer buf = ByteBuffer.allocate(16);
    for (int i = 0; i < 8; i++)
      buf.put((byte)i);
    buf.position(2).limit(6);
    ByteBuffer slice = buf.slice();
    checkEq("slice.capacity", slice.capacity(), 4);
    slice.put(0, (byte)99);
    checkEq("slice.share", buf.get(2), (byte)99);

    ByteBuffer dup = buf.duplicate();
    dup.position(0);
    checkEq("dup.read", dup.get(), (byte)0);
    dup.put(1, (byte)77);
    checkEq("dup.share", buf.get(1), (byte)77);
  }

  private static void testMarkReset() {
    ByteBuffer buf = ByteBuffer.allocate(8);
    buf.put((byte)1).put((byte)2).put((byte)3);
    buf.flip();
    buf.get(); // position 1
    buf.mark();
    checkEq("mark.pos1", buf.get(), (byte)2);
    buf.get(); // move to 3
    buf.reset();
    checkEq("reset.back", buf.get(), (byte)2);
  }

  private static void testCompact() {
    ByteBuffer buf = ByteBuffer.allocate(6);
    buf.put(new byte[] {1, 2, 3, 4, 5, 6});
    buf.flip();
    buf.get(); // consume one
    buf.get(); // consume second
    buf.compact(); // shift remaining 4 bytes to front
    checkEq("compact.pos", buf.position(), 4);
    buf.flip();
    byte[] rest = new byte[4];
    buf.get(rest);
    checkTrue("compact.remaining", rest[0] == 3 && rest[3] == 6);
  }

  private static void testArrayWrapSharing() {
    byte[] arr = {10, 20, 30, 40};
    ByteBuffer buf = ByteBuffer.wrap(arr);
    buf.put(1, (byte)99);
    checkEq("array.share1", arr[1], (byte)99);
    arr[2] = 77;
    checkEq("array.share2", buf.get(2), (byte)77);
  }

  private static void testReadOnly() {
    ByteBuffer buf = ByteBuffer.allocate(4);
    ByteBuffer ro = buf.asReadOnlyBuffer();
    checkTrue("readonly.flag", ro.isReadOnly());
    boolean threw = false;
    try {
      ro.put((byte)1);
    } catch (ReadOnlyBufferException e) {
      threw = true;
    }
    checkTrue("readonly.throw", threw);
  }

  private static void checkEq(String name, int got, int exp) {
    TestSupport.checkEq(name, got, exp, CTR);
  }

  private static void checkEq(String name, long got, long exp) {
    TestSupport.checkEq(name, got, exp, CTR);
  }

  private static void checkEq(String name, byte got, byte exp) {
    TestSupport.checkEq(name, (long)got, (long)exp, CTR);
  }

  private static void checkTrue(String name, boolean ok) {
    TestSupport.checkTrue(name, ok, CTR);
  }

  private static void checkApprox(String name, double got, double exp,
                                  double eps) {
    TestSupport.checkApprox(name, got, exp, eps, CTR);
  }
}
