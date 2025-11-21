# ART test snippets

Small collection of Java snippets used to probe Android Runtime behaviour and bytecode generation quirks.

## Building (all modules)

Each subdirectory ships a Makefile. Typical use:

1. `cd <module>`
2. `make` (builds `.jar` and `.dex`; ArtNativeTest also builds the native `.so`)
3. Optional: `make push` to send the dex (and native lib, if present) to a device via `adb`.
4. Optional: `make clean` to drop generated Java outputs; some modules also have `clean-native`.

Top-level helper: run `make` from the repo root to build every module Makefile discovered under subdirectories; `make clean` will call each module's `clean`.

The entire precess is like:

```bash
TARGET=YOUR_JAVA_PROGRAM_NAME_HERE
D8_PATH=~/nvme/aosp15/out/host/linux-x86/bin/d8

javac -encoding UTF-8 -source 1.8 -target 1.8 -d out/classes ${TARGET}.java


jar cf out/${TARGET}.jar -C out/classes .


${D8_PATH} --release --min-api 21 --output dex ./out/${TARGET}.jar

cd dex
mv classes.dex ${TARGET}.dex
adb push ${TARGET}.dex /data/local/tmp/
```

### Env

- `D8`: Path to `d8`, e.g., `android-sdk/build-tools/*/d8` (defaults to `d8` on PATH if not set).
- `ADB_REMOTE`: Push destination, defaults to `/data/local/tmp/<module>.dex`.

### Config (SDK/NDK paths)

- Edit `config.mk` in the repo root to set `ANDROID_SDK`, `ANDROID_NDK`, `SDK_D8`, `BUILD_TOOLS_VERSION`, and `PLATFORM_API` (defaults are empty or `d8` on PATH). Every module Makefile does `-include ../config.mk`, so these settings flow everywhere.
- You can still override per-invocation: `make D8=/path/to/d8`, `make ANDROID_SDK=...`.

## Running on device ART

After pushing a `.dex` to the device (e.g., `/data/local/tmp/NullBytecodeSamples.dex`), you can run it with Dalvik/ART:

```bash
dalvikvm64 -Xuse-stderr-logger -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/<dex-file>.dex <fully.qualified.MainClass> main
```

Example:

```bash
dalvikvm64 -Xuse-stderr-logger -Xbootclasspath:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -Xbootclasspath-locations:/apex/com.android.art/javalib/core-oj.jar:/apex/com.android.art/javalib/core-libart.jar:/apex/com.android.art/javalib/okhttp.jar:/apex/com.android.art/javalib/bouncycastle.jar:/apex/com.android.art/javalib/apache-xml.jar:/apex/com.android.art/javalib/service-art.jar:/apex/com.android.art/javalib/core-icu4j.jar -cp /data/local/tmp/HeapAllocationTest.dex HeapAllocationTest main
```
