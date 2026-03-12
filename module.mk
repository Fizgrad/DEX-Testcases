-include config.mk

OUT_DIR := out/$(APP)
CLASSES_DIR := $(OUT_DIR)/classes
JAR_FILE := $(OUT_DIR)/$(APP).jar
DEX_DIR := dex/$(APP)
DEX_FILE := $(DEX_DIR)/$(APP).dex
MIN_API ?= 21

SRC_DIR ?= src
COMMON_SRC_DIR ?=
SRC_DIRS ?= $(SRC_DIR)
ifneq ($(strip $(COMMON_SRC_DIR)),)
ifeq ($(filter $(COMMON_SRC_DIR),$(SRC_DIRS)),)
override SRC_DIRS += $(COMMON_SRC_DIR)
endif
endif
SRC ?= $(shell find $(SRC_DIRS) -name "*.java")

JAVA ?= javac
JAVAC_FLAGS ?= -encoding UTF-8 -source 1.8 -target 1.8
JAR ?= jar
D8 ?= $(SDK_D8)

ADB ?= adb
ADB_REMOTE ?= /data/local/tmp/$(APP).dex

D8_FLAGS ?= --release --min-api $(MIN_API)
D8_LIB ?=

all: $(DEX_FILE)

$(CLASSES_DIR)/.compiled: $(SRC) | $(CLASSES_DIR)
	$(JAVA) $(JAVAC_FLAGS) -d $(CLASSES_DIR) $(SRC)
	touch $@

$(JAR_FILE): $(CLASSES_DIR)/.compiled
	$(JAR) cf $@ -C $(CLASSES_DIR) .

$(DEX_FILE): $(JAR_FILE) | $(DEX_DIR)
	$(D8) $(D8_FLAGS) $(D8_LIB) --output $(DEX_DIR) $(JAR_FILE)
	mv $(DEX_DIR)/classes.dex $@

push: $(DEX_FILE)
	$(ADB) push $< $(ADB_REMOTE)

clean:
	rm -rf $(OUT_DIR) $(DEX_DIR)

$(CLASSES_DIR) $(DEX_DIR):
	mkdir -p $@

.PHONY: all clean push
