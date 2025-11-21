-include ../config.mk

APP := BytecodeExercise
SRC := $(APP).java

OUT_DIR := out
CLASSES_DIR := $(OUT_DIR)/classes
JAR_BASENAME ?= bytecode-exercise
JAR_FILE := $(OUT_DIR)/$(JAR_BASENAME).jar
DEX_DIR := dex
DEX_FILE := $(DEX_DIR)/$(APP).dex
MIN_API ?= 21

JAVA ?= javac
JAVAC_FLAGS := -encoding UTF-8 -source 1.8 -target 1.8
JAR ?= jar
D8 ?= $(SDK_D8)

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
