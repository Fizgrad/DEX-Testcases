// NativeAltSmoke.java
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class NativeAltSmoke {
    // --- 断言/统计 ---
    static int PASS = 0, FAIL = 0;
    static void log(String s){ System.out.println(s); }
    static void ok(String name){ System.out.println("OK   " + name); PASS++; }
    static void fail(String name, String msg){ System.out.println("FAIL " + name + ": " + msg); FAIL++; }
    static void checkTrue(String name, boolean cond){ if(cond) ok(name); else fail(name, "false"); }
    static void checkEq(String name, long got, long exp){ if(got==exp) ok(name + ": " + got); else fail(name, "got="+got+" exp="+exp); }
    static void checkEq(String name, int got, int exp){ if(got==exp) ok(name + ": " + got); else fail(name, "got="+got+" exp="+exp); }
    static void checkEq(String name, String got, String exp){ if((exp==null? got==null: exp.equals(got))) ok(name + ": " + got); else fail(name, "got="+got+" exp="+exp); }

    public static void main(String[] args) {
        try {
            testMmap();
            testUtf8();
            testNormalizer();
            testLockSupport();
        } catch (Throwable t) {
            t.printStackTrace();
            fail("UNCAUGHT", t.toString());
        }
        System.out.println("SUMMARY PASS=" + PASS + " FAIL=" + FAIL);
        if (FAIL != 0) System.exit(1);
    }

    // =============== 1) mmap: FileChannel.map(READ_WRITE) ===============
    static void testMmap() throws Exception {
        log("== mmap / MappedByteBuffer ==");
        File f = createTempUnderDataTmp("mmap-smoke", ".bin");
        final int SIZE = 4096;  // 4KB，足够放内容
        // 1) 生成文件并预设长度（native: ftruncate + mmap）
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw");
             FileChannel ch = raf.getChannel()) {
            raf.setLength(SIZE);
            MappedByteBuffer mbb = ch.map(FileChannel.MapMode.READ_WRITE, 0, SIZE);
            checkTrue("mmap.isDirect", mbb.isDirect());

            // 2) 写入 UTF-8 文本与一个 int（native: memcpy/putInt）
            String text = "hello mmap! 你好";
            byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
            mbb.position(0);
            mbb.put(utf8);
            mbb.put((byte)0); // C风格结束符，便于调试
            mbb.putInt(256, 0xCAFEBABE);
            mbb.force();     // sync 到页缓存
            ch.force(true);  // fsync
        }

        // 3) 重新打开并读取验证（native: mmap + get）
        try (RandomAccessFile raf = new RandomAccessFile(f, "r");
             FileChannel ch = raf.getChannel()) {
            MappedByteBuffer ro = ch.map(FileChannel.MapMode.READ_ONLY, 0, SIZE);
            // 读取到 0 之前的字节
            int len = 0; while (len < SIZE && ro.get(len) != 0) len++;
            byte[] out = new byte[len];
            ro.position(0); ro.get(out);
            String got = new String(out, StandardCharsets.UTF_8);
            checkEq("mmap.text", got, "hello mmap! 你好");
            int iv = ro.getInt(256);
            checkEq("mmap.int", iv, 0xCAFEBABE);
        } finally {
            // 4) 清理文件
            boolean del = f.delete();
            checkTrue("mmap.cleanup", del || !f.exists());
        }
    }

    // =============== 2) UTF-8 编解码（多字节/代理对） ===============
    static void testUtf8() throws Exception {
        log("== UTF-8 encode/decode ==");
        String s = "UTF-8: 你好, мир, مرحبا, 🌏🚀";
        byte[] b1 = s.getBytes(StandardCharsets.UTF_8);
        String s2 = new String(b1, StandardCharsets.UTF_8);
        checkEq("utf8.roundtrip", s2, s);

        // Decoder 严格模式：遇到非法序列必须报错
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        // 构造一个非法 UTF-8：单个 0xC0
        ByteBuffer bad = ByteBuffer.wrap(new byte[]{(byte)0xC0});
        boolean threw = false;
        try { dec.decode(bad); }
        catch (CharacterCodingException e){ threw = true; }
        checkTrue("utf8.malformed.detected", threw);
    }

    // =============== 3) Normalizer（ICU/本地实现） ===============
    static void testNormalizer() {
        log("== Normalizer NFC/NFD ==");
        // "é" = 'e' + 组合重音（U+0065 + U+0301）
        String nfd = "e\u0301";
        // "é" = 预组合（U+00E9）
        String nfc = "\u00E9";

        String toNFC = Normalizer.normalize(nfd, Normalizer.Form.NFC);
        String toNFD = Normalizer.normalize(nfc, Normalizer.Form.NFD);

        checkEq("normalizer.nfd->nfc", toNFC, nfc);
        checkEq("normalizer.nfc->nfd.len", toNFD.length(), 2);
        // 再加一个包含多语种与 emoji 的串，确保不会崩与 ICU 数据正常
        String rich = "Ångström café — 𝛑 π 你好 🌈";
        String richNFC = Normalizer.normalize(rich, Normalizer.Form.NFC);
        String richNFD = Normalizer.normalize(rich, Normalizer.Form.NFD);
        // 规范化后再回到 NFC 应当稳定
        checkEq("normalizer.idempotent", Normalizer.normalize(richNFD, Normalizer.Form.NFC), richNFC);
    }

    // =============== 4) LockSupport park/unpark（内核等待） ===============
    static void testLockSupport() throws Exception {
        log("== LockSupport park/unpark ==");
        // 场景 A：先 unpark 再 park，应当立即返回
        Thread t1 = new Thread(() -> {
            long t0 = System.nanoTime();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50)); // permit 已发，立刻返回
            long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            // 允许有少量调度延迟
            checkTrue("lockspt.unpark-before-park.fast", dtMs < 10);
        }, "lockspt-A");
        LockSupport.unpark(t1); // 先发 permit
        t1.start();
        t1.join();

        // 场景 B：park 等待，主线程稍后 unpark
        AtomicBoolean parked = new AtomicBoolean(false);
        AtomicBoolean woke = new AtomicBoolean(false);
        Thread t2 = new Thread(() -> {
            parked.set(true);
            LockSupport.park(); // 等主线程 unpark
            woke.set(true);
        }, "lockspt-B");
        t2.start();
        // 等待线程进入 park 状态
        long wait = System.currentTimeMillis() + 1000;
        while (!parked.get() && System.currentTimeMillis() < wait) Thread.yield();
        Thread.sleep(30);
        LockSupport.unpark(t2);
        t2.join(1000);
        checkTrue("lockspt.park-then-unpark", woke.get());

        // 场景 C：超时唤醒
        long t0 = System.nanoTime();
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        long dt = System.nanoTime() - t0;
        checkTrue("lockspt.timeout", TimeUnit.NANOSECONDS.toMillis(dt) >= 15);
    }

    // =============== 工具 ===============
    private static File createTempUnderDataTmp(String prefix, String suffix) throws IOException {
        // 先用系统 tmpdir，失败再退到 /data/local/tmp（adb shell 可写）
        try {
            File f = File.createTempFile(prefix, suffix);
            f.deleteOnExit();
            return f;
        } catch (Throwable ignored) {
            File dir = new File("/data/local/tmp");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, prefix + "-" + System.nanoTime() + suffix);
            if (!f.exists()) f.createNewFile();
            f.deleteOnExit();
            return f;
        }
    }
}
