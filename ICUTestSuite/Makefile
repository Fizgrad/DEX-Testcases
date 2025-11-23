-include ../config.mk

APP := ICUTestSuite
SRC := ICUTestSuite.java

OUT_DIR := out
CLASSES_DIR := $(OUT_DIR)/classes
JAR_FILE := $(OUT_DIR)/$(APP).jar
DEX_DIR := dex
DEX_FILE := $(DEX_DIR)/$(APP).dex
MIN_API ?= 24

JAVA ?= javac
JAR ?= jar
D8 ?= $(SDK_D8)

ANDROID_STUB_JAR ?= $(if $(ANDROID_SDK),$(ANDROID_SDK)/platforms/$(PLATFORM_API)/android.jar,)
ifeq ($(ANDROID_STUB_JAR),)
$(error Set ANDROID_SDK or ANDROID_STUB_JAR to compile ICU tests.)
endif
ifeq ($(wildcard $(ANDROID_STUB_JAR)),)
$(error Android stub jar '$(ANDROID_STUB_JAR)' not found. Adjust ANDROID_SDK or PLATFORM_API.)
endif

JAVAC_FLAGS := -encoding UTF-8 -source 1.8 -target 1.8 -cp $(ANDROID_STUB_JAR)

ADB ?= adb
ADB_REMOTE ?= /data/local/tmp/$(APP).dex

all: $(DEX_FILE)

$(CLASSES_DIR)/$(APP).class: $(SRC) | $(CLASSES_DIR)
	$(JAVA) $(JAVAC_FLAGS) -d $(CLASSES_DIR) $<

$(JAR_FILE): $(CLASSES_DIR)/$(APP).class
	$(JAR) cf $@ -C $(CLASSES_DIR) .

$(DEX_FILE): $(JAR_FILE) | $(DEX_DIR)
	$(D8) --release --min-api $(MIN_API) --output $(DEX_DIR) $(JAR_FILE)
	mv $(DEX_DIR)/classes.dex $@

push: $(DEX_FILE)
	$(ADB) push $< $(ADB_REMOTE)

clean:
	rm -rf $(OUT_DIR) $(DEX_DIR)

$(CLASSES_DIR) $(DEX_DIR):
	mkdir -p $@

.PHONY: all clean push
