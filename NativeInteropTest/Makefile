-include ../config.mk

APP := NativeInteropTest

SRC_DIR := src/main/java
SOURCES := $(shell find $(SRC_DIR) -name '*.java')

OUT_DIR := out
CLASSES_DIR := $(OUT_DIR)/classes
JAR_FILE := $(OUT_DIR)/$(APP).jar
DEX_DIR := $(OUT_DIR)/dex
DEX_FILE := $(DEX_DIR)/$(APP).dex

BUILD_DIR := build
NDK ?= $(ANDROID_NDK)
ANDROID_ABI ?= arm64-v8a
ANDROID_PLATFORM ?= android-29

SDK_D8 ?= $(ANDROID_SDK)/build-tools/$(BUILD_TOOLS_VERSION)/d8
D8 ?= $(SDK_D8)
ANDROID_JAR ?= $(if $(ANDROID_SDK),$(ANDROID_SDK)/platforms/$(PLATFORM_API)/android.jar,)

JAVA ?= javac
JAVAC_FLAGS := -encoding UTF-8 -source 1.8 -target 1.8
JAR ?= jar
ADB ?= adb
ADB_REMOTE ?= /data/local/tmp/$(APP).dex
LIB_REMOTE ?= /data/local/tmp/libartnativetest.so

all: $(DEX_FILE) $(BUILD_DIR)/libartnativetest.so

ifeq ($(strip $(NDK)),)
$(error ANDROID_NDK is not set. Configure it in ../config.mk or export ANDROID_NDK)
endif
ifeq ($(strip $(ANDROID_SDK)),)
$(error ANDROID_SDK is not set. Configure it in ../config.mk or export ANDROID_SDK)
endif
ifeq ($(strip $(ANDROID_JAR)),)
$(error ANDROID_JAR is not set. Configure ANDROID_SDK/PLATFORM_API so we can locate android.jar)
endif

$(BUILD_DIR)/CMakeCache.txt:
	cmake -S . -B $(BUILD_DIR) \
		-D CMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
		-D ANDROID_ABI=$(ANDROID_ABI) \
		-D ANDROID_PLATFORM=$(ANDROID_PLATFORM)

$(BUILD_DIR)/libartnativetest.so: $(BUILD_DIR)/CMakeCache.txt
	cmake --build $(BUILD_DIR) -j

$(CLASSES_DIR)/.compiled: $(SOURCES) | $(CLASSES_DIR)
	$(JAVA) $(JAVAC_FLAGS) -d $(CLASSES_DIR) $(SOURCES)
	touch $@

$(JAR_FILE): $(CLASSES_DIR)/.compiled
	$(JAR) cf $@ -C $(CLASSES_DIR) .

$(DEX_FILE): $(JAR_FILE) | $(DEX_DIR)
	$(D8) --release --lib $(ANDROID_JAR) --output $(DEX_DIR) $(JAR_FILE)
	mv $(DEX_DIR)/classes.dex $@

push: $(DEX_FILE) $(BUILD_DIR)/libartnativetest.so
	$(ADB) push $(DEX_FILE) $(ADB_REMOTE)
	$(ADB) push $(BUILD_DIR)/libartnativetest.so $(LIB_REMOTE)

clean:
	rm -rf $(OUT_DIR)

clean-native:
	rm -rf $(BUILD_DIR)

$(CLASSES_DIR) $(DEX_DIR):
	mkdir -p $@

.PHONY: all clean clean-native push
