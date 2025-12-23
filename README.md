# ART test snippets

Small collection of Java snippets used to probe Android Runtime behaviour and bytecode generation quirks.

## Building (all modules)

All Java sources live under `src/main/java`; native sources live under `src/native/NativeInteropTest`.
Build outputs land under `out/<Module>/` and `dex/<Module>/`.
Typical use:

1. `make <Module>` (builds `.jar` + `.dex`; `make NativeInteropTest` also builds the native `.so`)
2. Optional: `make <Module>-push` to send the dex (and native lib, if present) to a device via `adb`.
3. Optional: `make <Module>-clean` to drop generated outputs; `make clean-native` cleans native artifacts.

Top-level helper: run `make` from the repo root to build all modules; `make clean` calls `<Module>-clean` for each.

The entire process is like:

```bash
TARGET=YOUR_JAVA_PROGRAM_NAME_HERE
D8_PATH=~/nvme/aosp15/out/host/linux-x86/bin/d8

javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/${TARGET}/classes ${TARGET}.java


jar cf out/${TARGET}/${TARGET}.jar -C out/${TARGET}/classes .


${D8_PATH} --release --min-api 21 --output dex/${TARGET} ./out/${TARGET}/${TARGET}.jar

mv dex/${TARGET}/classes.dex dex/${TARGET}/${TARGET}.dex
adb push dex/${TARGET}/${TARGET}.dex /data/local/tmp/
```

### Env

- `D8`: Path to `d8`, e.g., `android-sdk/build-tools/*/d8` (defaults to `SDK_D8` / `d8` on PATH).
- `ADB_REMOTE`: Push destination, defaults to `/data/local/tmp/<module>.dex`.

### Config (SDK/NDK paths)

- Edit `config.mk` in the repo root to set `ANDROID_SDK`, `ANDROID_NDK`, `SDK_D8`, `BUILD_TOOLS_VERSION`, and `PLATFORM_API` (defaults are empty or `d8` on PATH).
- You can still override per-invocation: `make D8=/path/to/d8`, `make ANDROID_SDK=...`.

## Modules

- `AllTests`: Single-dex unified runner (`com.art.tests.runner.AllTests`).

- `ByteBufferTest`: Heap vs direct buffers, order/primitives, slice/duplicate sharing, mark/reset, compact, read-only behaviour.
- `BytecodePlayground`: Mixed bytecode/stack shape experiments and soak workload.
- `BytecodePlaygroundJit`: Standalone JIT-prewarmed self-check of bytecode shapes (polymorphism/sync/arithmetic/arrays/returns).
- `GcReferenceSuite`: GC/reference/ReferenceQueue behaviours.
- `HashCodeStabilityTest`: Object identity hash stability exercises.
- `HeapStressSuite`: Heap pressure + allocation/GC monitoring.
- `HelloWorldSample`: Minimal hello-world sanity check.
- `ICUTestSuite`: Exercises `android.icu` (ULocale, Number/Currency/CompactDecimal formats, calendars, time zones, collation, BreakIterator, Transliterator, UnicodeSet, normalization/casing, MessageFormat/PluralRules, MeasureFormat, RelativeDateTimeFormatter, VersionInfo).
- `InvokeShapeTest`: invoke-* shape coverage (static/instance/interface).
- `LocalePrintfRepro`: Locale printf formatting / NPE repro.
- `LongRunningAppSim`: Simulated long-running workload shape.
- `NativeIOSmoke`: mmap/UTF-8/Normalizer/LockSupport smoke checks.
- `NativeInteropTest`: JNI checksum/probe (main class `com.art.tests.nativeinterop.ArtNativeTest`).
- `NullBytecodeSamples`: Null writes in fields/arrays/locals to inspect bytecode.
- `RandomObjectChaosTest`: Randomized object graph fuzzing.
- `StringBuilderIntrinsicTest`: StringBuilder intrinsic/arg shape checks (wide args, mixed types, buffer growth).
- `StringEqualsTest`: Exhaustive `String.equals` path coverage (self/null/type/length/mismatch/case).

## Running on device ART

After pushing a `.dex` to the device, you can run it with Dalvik/ART:

```bash
dalvikvm64 -Xuse-stderr-logger -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/<dex-file>.dex <fully.qualified.MainClass> main
```

Example for HeapStressSuite:

```bash
dalvikvm64 -Xuse-stderr-logger -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/HeapStressSuite.dex com.art.tests.heap.HeapStressSuite main
```

Example for unified single-dex run:

```bash
dalvikvm64 -Xuse-stderr-logger -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/AllTests.dex com.art.tests.runner.AllTests --short
```

Runner flags:
- `--short` (default) or `--full` to control runtime profile
- `--include=stress` to include stress/native-heavy suites
- `--only=Name1,Name2` / `--skip=Name1,Name2` / `--list` (short names; legacy long names still work)

Helper scripts:
- `run_all_device_tests.sh`: builds `AllTests`, pushes it, and runs on device.
- `dalvikvm64.sh`: curated example invocations for manual device runs.
