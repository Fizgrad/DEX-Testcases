package com.art.tests.runner;

import com.art.tests.bytebuffer.ByteBufferTest;
import com.art.tests.bytecode.BytecodePlayground;
import com.art.tests.bytecode.BytecodePlaygroundJit;
import com.art.tests.gc.GcReferenceSuite;
import com.art.tests.hash.HashCodeStabilityTest;
import com.art.tests.heap.HeapStressSuite;
import com.art.tests.hello.HelloWorldSample;
import com.art.tests.icu.ICUTestSuite;
import com.art.tests.invoke.InvokeShapeTest;
import com.art.tests.locale.LocalePrintfRepro;
import com.art.tests.longrun.LongRunningAppSim;
import com.art.tests.nativeinterop.ArtNativeTest;
import com.art.tests.nativeio.NativeIOSmoke;
import com.art.tests.nullbytecode.NullBytecodeSamples;
import com.art.tests.random.RandomObjectChaosTest;
import com.art.tests.stringbuilder.StringBuilderIntrinsicTest;
import com.art.tests.stringequals.StringEqualsTest;
import com.art.tests.common.TestKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AllTests {
  private static final class Entry {
    final String name;
    final TestKind kind;
    final Runner runner;
    final String[] shortArgs;
    final String[] fullArgs;
    final Set<String> keys;

    Entry(String name, TestKind kind, Runner runner, String[] shortArgs,
          String[] fullArgs, String[] aliases) {
      this.name = name;
      this.kind = kind;
      this.runner = runner;
      this.shortArgs = shortArgs;
      this.fullArgs = fullArgs;
      this.keys = buildKeySet(name, aliases);
    }
  }

  private interface Runner {
    void run(String[] args) throws Throwable;
  }

  private static final class Options {
    final boolean includeStress;
    final boolean shortMode;
    final boolean failFast;
    final boolean listOnly;
    final Set<String> only;
    final Set<String> skip;

    Options(boolean includeStress, boolean shortMode, boolean failFast,
            boolean listOnly, Set<String> only, Set<String> skip) {
      this.includeStress = includeStress;
      this.shortMode = shortMode;
      this.failFast = failFast;
      this.listOnly = listOnly;
      this.only = only;
      this.skip = skip;
    }

    static Options parse(String[] args) {
      boolean includeStress = false;
      boolean shortMode = true;
      boolean failFast = false;
      boolean listOnly = false;
      Set<String> only = new HashSet<>();
      Set<String> skip = new HashSet<>();

      for (String s : args) {
        if ("--include=stress".equals(s) || "--include=all".equals(s)) {
          includeStress = true;
        } else if ("--full".equals(s) || "--long".equals(s)) {
          shortMode = false;
        } else if ("--short".equals(s)) {
          shortMode = true;
        } else if ("--failFast".equals(s)) {
          failFast = true;
        } else if ("--list".equals(s)) {
          listOnly = true;
        } else if (s.startsWith("--only=")) {
          only.addAll(splitList(s.substring(s.indexOf('=') + 1)));
        } else if (s.startsWith("--skip=")) {
          skip.addAll(splitList(s.substring(s.indexOf('=') + 1)));
        }
      }
      return new Options(includeStress, shortMode, failFast, listOnly,
                         lowerSet(only), lowerSet(skip));
    }
  }

  private AllTests() {}

  public static void main(String[] args) {
    Options opt = Options.parse(args);
    List<Entry> entries = buildEntries();
    if (opt.listOnly) {
      for (Entry e : entries) {
        System.out.println(e.name + " [" + e.kind + "]");
      }
      return;
    }

    int pass = 0;
    int fail = 0;
    for (Entry e : entries) {
      if (!shouldRun(opt, e))
        continue;
      String[] runArgs = opt.shortMode ? e.shortArgs : e.fullArgs;
      System.out.println("== RUN " + e.name + " (" + e.kind + ") ==");
      try {
        e.runner.run(runArgs);
        pass++;
      } catch (Throwable t) {
        fail++;
        System.err.println("FAIL " + e.name + ": " + t);
        t.printStackTrace();
        if (opt.failFast)
          break;
      }
      System.out.println();
    }

    System.out.println("== AllTests DONE: PASS=" + pass + " FAIL=" + fail);
    if (fail != 0)
      System.exit(1);
  }

  private static List<Entry> buildEntries() {
    List<Entry> list = new ArrayList<>();
    list.add(entry("HelloWorld", TestKind.SMOKE, HelloWorldSample::main,
                   "HelloWorldSample"));
    list.add(entry("ByteBuffer", TestKind.SMOKE, ByteBufferTest::main,
                   "ByteBufferTest"));
    list.add(entry("StringEquals", TestKind.SMOKE, StringEqualsTest::main,
                   "StringEqualsTest"));
    list.add(entry("StringBuilder", TestKind.SMOKE,
                   StringBuilderIntrinsicTest::main,
                   "StringBuilderIntrinsicTest"));
    list.add(entry("InvokeShape", TestKind.SMOKE,
                   args -> InvokeShapeTest.main(args),
                   "InvokeShapeTest"));
    list.add(entry("LocalePrintf", TestKind.SMOKE, LocalePrintfRepro::main,
                   "LocalePrintfRepro"));
    list.add(entry("NullBytecode", TestKind.SMOKE,
                   NullBytecodeSamples::main,
                   "NullBytecodeSamples"));
    list.add(entry("GcRefs", TestKind.SMOKE, args -> GcReferenceSuite.main(args),
                   "GcReferenceSuite"));
    list.add(entry("NativeIO", TestKind.SMOKE, NativeIOSmoke::main,
                   "NativeIOSmoke"));
    list.add(entry("ICU", TestKind.SMOKE, ICUTestSuite::main, "ICUTestSuite"));
    list.add(entryWithArgs("Bytecode", TestKind.SMOKE,
                           args -> BytecodePlayground.main(args),
                           new String[] {"--noSoak"}, new String[] {},
                           "BytecodePlayground"));
    list.add(entry("BytecodeJit", TestKind.SMOKE,
                   BytecodePlaygroundJit::main,
                   "BytecodePlaygroundJit"));

    list.add(entryWithArgs("HashCode", TestKind.STRESS,
                           HashCodeStabilityTest::main,
                           new String[] {"--seconds=2"}, new String[] {},
                           "HashCodeStabilityTest"));
    list.add(entryWithArgs("HeapStress", TestKind.STRESS,
                           HeapStressSuite::main,
                           new String[] {"--short"}, new String[] {},
                           "HeapStressSuite"));
    list.add(entryWithArgs("LongRun", TestKind.STRESS,
                           LongRunningAppSim::main,
                           new String[] {"--seconds=2"}, new String[] {},
                           "LongRunningAppSim"));
    list.add(entryWithArgs("RandomChaos", TestKind.STRESS,
                           RandomObjectChaosTest::main,
                           new String[] {"--maxAllocs=2000"}, new String[] {},
                           "RandomObjectChaosTest"));
    list.add(entry("NativeInterop", TestKind.STRESS, ArtNativeTest::main,
                   "NativeInteropTest"));

    return list;
  }

  private static Entry entry(String name, TestKind kind, Runner runner,
                             String... aliases) {
    return new Entry(name, kind, runner, new String[] {}, new String[] {},
                     aliases);
  }

  private static Entry entryWithArgs(String name, TestKind kind, Runner runner,
                                     String[] shortArgs, String[] fullArgs,
                                     String... aliases) {
    return new Entry(name, kind, runner, shortArgs, fullArgs, aliases);
  }

  private static boolean shouldRun(Options opt, Entry e) {
    if (e.kind == TestKind.STRESS && !opt.includeStress)
      return false;
    if (!opt.only.isEmpty() && !intersects(opt.only, e.keys))
      return false;
    if (intersects(opt.skip, e.keys))
      return false;
    return true;
  }

  private static Set<String> splitList(String raw) {
    String[] parts = raw.split(",");
    Set<String> out = new HashSet<>();
    for (String p : parts) {
      String v = p.trim();
      if (!v.isEmpty())
        out.add(v);
    }
    return out;
  }

  private static Set<String> lowerSet(Set<String> in) {
    Set<String> out = new HashSet<>();
    for (String s : in) {
      out.add(s.toLowerCase(Locale.ROOT));
    }
    return out;
  }

  private static Set<String> buildKeySet(String name, String[] aliases) {
    Set<String> keys = new HashSet<>();
    keys.add(name.toLowerCase(Locale.ROOT));
    if (aliases != null) {
      for (String a : aliases) {
        if (a != null && !a.isEmpty())
          keys.add(a.toLowerCase(Locale.ROOT));
      }
    }
    return keys;
  }

  private static boolean intersects(Set<String> left, Set<String> right) {
    for (String s : left) {
      if (right.contains(s))
        return true;
    }
    return false;
  }
}
