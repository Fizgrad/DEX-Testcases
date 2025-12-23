-include config.mk

COMMON_SRC_DIR := src/main/java/com/art/tests/common
ANDROID_STUB_JAR ?= $(if $(ANDROID_SDK),$(ANDROID_SDK)/platforms/$(PLATFORM_API)/android.jar,)
ANDROID_JAR ?= $(ANDROID_STUB_JAR)
JAVAC_FLAGS_ANDROID := -encoding UTF-8 -source 1.8 -target 1.8 -cp $(ANDROID_STUB_JAR)

JAVA_MODULES := AllTests \
	ByteBufferTest \
	BytecodePlayground \
	BytecodePlaygroundJit \
	GcReferenceSuite \
	HashCodeStabilityTest \
	HeapStressSuite \
	HelloWorldSample \
	ICUTestSuite \
	InvokeShapeTest \
	LocalePrintfRepro \
	LongRunningAppSim \
	NativeIOSmoke \
	NullBytecodeSamples \
	RandomObjectChaosTest \
	StringBuilderIntrinsicTest \
	StringEqualsTest

MODULES := $(JAVA_MODULES) NativeInteropTest

SRC_DIRS_AllTests := src/main/java
SRC_DIRS_ByteBufferTest := src/main/java/com/art/tests/bytebuffer
SRC_DIRS_BytecodePlayground := src/main/java/com/art/tests/bytecode
SRC_DIRS_BytecodePlaygroundJit := src/main/java/com/art/tests/bytecode
SRC_DIRS_GcReferenceSuite := src/main/java/com/art/tests/gc
SRC_DIRS_HashCodeStabilityTest := src/main/java/com/art/tests/hash
SRC_DIRS_HeapStressSuite := src/main/java/com/art/tests/heap
SRC_DIRS_HelloWorldSample := src/main/java/com/art/tests/hello
SRC_DIRS_ICUTestSuite := src/main/java/com/art/tests/icu
SRC_DIRS_InvokeShapeTest := src/main/java/com/art/tests/invoke
SRC_DIRS_LocalePrintfRepro := src/main/java/com/art/tests/locale
SRC_DIRS_LongRunningAppSim := src/main/java/com/art/tests/longrun
SRC_DIRS_NativeIOSmoke := src/main/java/com/art/tests/nativeio
SRC_DIRS_NativeInteropTest := src/main/java/com/art/tests/nativeinterop
SRC_DIRS_NullBytecodeSamples := src/main/java/com/art/tests/nullbytecode
SRC_DIRS_RandomObjectChaosTest := src/main/java/com/art/tests/random
SRC_DIRS_StringBuilderIntrinsicTest := src/main/java/com/art/tests/stringbuilder
SRC_DIRS_StringEqualsTest := src/main/java/com/art/tests/stringequals

NO_COMMON_AllTests := 1

MIN_API_AllTests := 35
JAVAC_FLAGS_AllTests := $(JAVAC_FLAGS_ANDROID)
D8_LIB_AllTests := --lib $(ANDROID_STUB_JAR)

MIN_API_ICUTestSuite := 24
JAVAC_FLAGS_ICUTestSuite := $(JAVAC_FLAGS_ANDROID)
D8_LIB_ICUTestSuite := --lib $(ANDROID_STUB_JAR)

MIN_API_HashCodeStabilityTest := 35
D8_LIB_HashCodeStabilityTest := $(if $(wildcard $(ANDROID_JAR)),--lib $(ANDROID_JAR),)

MIN_API_InvokeShapeTest := 35

D8_LIB_NativeInteropTest := --lib $(ANDROID_JAR)

ADB ?= adb
LIB_REMOTE ?= /data/local/tmp/libartnativetest.so
NATIVE_SRC_DIR := src/native/NativeInteropTest
NATIVE_BUILD_DIR := out/NativeInteropTest/native
NDK ?= $(ANDROID_NDK)
ANDROID_ABI ?= arm64-v8a
ANDROID_PLATFORM ?= android-29

define module_args
APP=$1 \
SRC_DIRS="$(SRC_DIRS_$1)" \
$(if $(NO_COMMON_$1),COMMON_SRC_DIR=,COMMON_SRC_DIR="$(COMMON_SRC_DIR)") \
$(if $(MIN_API_$1),MIN_API="$(MIN_API_$1)") \
$(if $(JAVAC_FLAGS_$1),JAVAC_FLAGS="$(JAVAC_FLAGS_$1)") \
$(if $(D8_LIB_$1),D8_LIB="$(D8_LIB_$1)") \
$(if $(D8_FLAGS_$1),D8_FLAGS="$(D8_FLAGS_$1)")
endef

define run_module
	$(MAKE) -f module.mk $(call module_args,$1) $(2)
endef

.PHONY: all modules clean clean-native push \
	$(JAVA_MODULES) $(JAVA_MODULES:%=%-push) $(JAVA_MODULES:%=%-clean) \
	NativeInteropTest NativeInteropTest-java NativeInteropTest-native \
	NativeInteropTest-push NativeInteropTest-clean NativeInteropTest-clean-native \
	check-android-jar check-native-env

all: modules

modules: $(JAVA_MODULES) NativeInteropTest

clean: $(JAVA_MODULES:%=%-clean) NativeInteropTest-clean NativeInteropTest-clean-native

push: $(JAVA_MODULES:%=%-push) NativeInteropTest-push

clean-native: NativeInteropTest-clean-native

AllTests AllTests-push ICUTestSuite ICUTestSuite-push: check-android-jar

$(JAVA_MODULES):
	$(call run_module,$@,)

$(JAVA_MODULES:%=%-push):
	$(call run_module,$(@:%-push=%),push)

$(JAVA_MODULES:%=%-clean):
	$(call run_module,$(@:%-clean=%),clean)

NativeInteropTest: check-native-env NativeInteropTest-java NativeInteropTest-native

NativeInteropTest-java: check-native-env
	$(call run_module,NativeInteropTest,)

NativeInteropTest-native: check-native-env
	cmake -S $(NATIVE_SRC_DIR) -B $(NATIVE_BUILD_DIR) \
		-D CMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
		-D ANDROID_ABI=$(ANDROID_ABI) \
		-D ANDROID_PLATFORM=$(ANDROID_PLATFORM)
	cmake --build $(NATIVE_BUILD_DIR) -j

NativeInteropTest-push: check-native-env NativeInteropTest-java NativeInteropTest-native
	$(call run_module,NativeInteropTest,push)
	$(ADB) push $(NATIVE_BUILD_DIR)/libartnativetest.so $(LIB_REMOTE)

NativeInteropTest-clean:
	$(call run_module,NativeInteropTest,clean)

NativeInteropTest-clean-native:
	rm -rf $(NATIVE_BUILD_DIR)

check-android-jar:
	@if [ -z "$(ANDROID_STUB_JAR)" ]; then \
		echo "Set ANDROID_SDK or ANDROID_STUB_JAR to compile ICU/AllTests."; \
		exit 1; \
	fi
	@if [ ! -f "$(ANDROID_STUB_JAR)" ]; then \
		echo "Android stub jar '$(ANDROID_STUB_JAR)' not found. Adjust ANDROID_SDK or PLATFORM_API."; \
		exit 1; \
	fi

check-native-env:
	@if [ -z "$(NDK)" ]; then \
		echo "ANDROID_NDK is not set. Configure it in config.mk or export ANDROID_NDK."; \
		exit 1; \
	fi
	@if [ -z "$(ANDROID_SDK)" ]; then \
		echo "ANDROID_SDK is not set. Configure it in config.mk or export ANDROID_SDK."; \
		exit 1; \
	fi
	@if [ -z "$(ANDROID_JAR)" ] || [ ! -f "$(ANDROID_JAR)" ]; then \
		echo "ANDROID_JAR not found. Configure ANDROID_SDK/PLATFORM_API."; \
		exit 1; \
	fi
