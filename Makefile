-include config.mk

SRC_DIR_DEFAULT := src

# Shared sources that are safe to compile everywhere (no Android dependencies).
SRC_COMMON := src/TestSupport.java src/JitSupport.java src/TestKind.java

# Modules whose main class does not match `<Module>.java`.
MAIN_NativeInteropTest := src/ArtNativeTest.java

# Optional per-module extras:
#   SRC_EXTRA_<Module> := src/Foo.java src/Bar.java
# Optional per-module full override:
#   SRC_OVERRIDE_<Module> := <complete list>
ANDROID_STUB_JAR ?= $(if $(ANDROID_SDK),$(ANDROID_SDK)/platforms/$(PLATFORM_API)/android.jar,)
ANDROID_JAR ?= $(ANDROID_STUB_JAR)
JAVAC_FLAGS_ANDROID := -encoding UTF-8 -source 1.8 -target 1.8 -cp $(ANDROID_STUB_JAR)

JAVA_MODULES := AllTests \
	ByteBufferTest \
	BytecodePlayground \
	BytecodePlaygroundJit \
	GcReferenceSuite \
	GcRootStackMapTest \
	HashCodeStabilityTest \
	HeapStressSuite \
	HelloWorldSample \
	ICUTestSuite \
	IntrinsicsTest \
	InvokeShapeTest \
	LocalePrintfRepro \
	LongRunningAppSim \
	NativeIOSmoke \
	NullBytecodeSamples \
	RandomObjectChaosTest \
	ReferencePhiMergeTest \
	RegAllocMoveStressTest \
	SimdSpillSlotTest \
	StackMapConstTest \
	StringBuilderIntrinsicTest \
	StringEqualsTest \
	WriteBarrierStressTest

MODULES := $(JAVA_MODULES) NativeInteropTest

define module_src
$(strip \
  $(if $(SRC_OVERRIDE_$1),$(SRC_OVERRIDE_$1), \
    $(if $(filter AllTests,$1),$(sort $(wildcard $(SRC_DIR_DEFAULT)/*.java)), \
      $(if $(MAIN_$1),$(MAIN_$1),$(SRC_DIR_DEFAULT)/$1.java) $(SRC_COMMON) $(SRC_EXTRA_$1))))
endef

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

MIN_API_IntrinsicsTest := 26

ADB ?= adb
LIB_REMOTE ?= /data/local/tmp/libartnativetest.so
NATIVE_SRC_DIR := src/native/NativeInteropTest
NATIVE_BUILD_DIR := out/NativeInteropTest/native
NDK ?= $(ANDROID_NDK)
ANDROID_ABI ?= arm64-v8a
ANDROID_PLATFORM ?= android-29

define module_args
APP=$1 \
SRC_DIRS="$(SRC_DIR_DEFAULT)" \
SRC="$(call module_src,$1)" \
COMMON_SRC_DIR= \
$(if $(MIN_API_$1),MIN_API="$(MIN_API_$1)") \
$(if $(JAVAC_FLAGS_$1),JAVAC_FLAGS="$(JAVAC_FLAGS_$1)") \
$(if $(D8_LIB_$1),D8_LIB="$(D8_LIB_$1)") \
$(if $(D8_FLAGS_$1),D8_FLAGS="$(D8_FLAGS_$1)")
endef

define run_module
	$(MAKE) -f module.mk $(call module_args,$1) $(2)
endef

.PHONY: all push clean \
	$(JAVA_MODULES) $(JAVA_MODULES:%=%-push) $(JAVA_MODULES:%=%-clean) \
	NativeInteropTest NativeInteropTest-push NativeInteropTest-clean \
	check-android-jar check-native-env

all: $(JAVA_MODULES) NativeInteropTest

clean: $(JAVA_MODULES:%=%-clean) NativeInteropTest-clean

push: all $(JAVA_MODULES:%=%-push) NativeInteropTest-push

AllTests AllTests-push ICUTestSuite ICUTestSuite-push: check-android-jar

define build_nativeinterop_so
	cmake -S $(NATIVE_SRC_DIR) -B $(NATIVE_BUILD_DIR) \
		-D CMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
		-D ANDROID_ABI=$(ANDROID_ABI) \
		-D ANDROID_PLATFORM=$(ANDROID_PLATFORM)
	cmake --build $(NATIVE_BUILD_DIR) -j
endef

AllTests-push: check-native-env
	$(call run_module,AllTests,push)
	$(build_nativeinterop_so)
	$(ADB) push $(NATIVE_BUILD_DIR)/libartnativetest.so $(LIB_REMOTE)

$(JAVA_MODULES):
	$(call run_module,$@,)

$(filter-out AllTests-push,$(JAVA_MODULES:%=%-push)):
	$(call run_module,$(@:%-push=%),push)

$(JAVA_MODULES:%=%-clean):
	$(call run_module,$(@:%-clean=%),clean)

NativeInteropTest: check-native-env
	$(call run_module,NativeInteropTest,)
	$(build_nativeinterop_so)

NativeInteropTest-push: check-native-env NativeInteropTest
	$(call run_module,NativeInteropTest,push)
	$(ADB) push $(NATIVE_BUILD_DIR)/libartnativetest.so $(LIB_REMOTE)

NativeInteropTest-clean:
	$(call run_module,NativeInteropTest,clean)
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
