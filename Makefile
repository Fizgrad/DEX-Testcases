SUBDIR_MAKEFILES := $(filter-out build/%,$(wildcard */Makefile))
MODULES := $(patsubst %/Makefile,%,$(SUBDIR_MAKEFILES))

.PHONY: all modules $(MODULES) clean clean-native push

all: modules

modules: $(MODULES)

$(MODULES):
	$(MAKE) -C $@

clean:
	@for d in $(MODULES); do \
		$(MAKE) -C $$d clean || exit $$?; \
	done

push:
	@for d in $(MODULES); do \
		$(MAKE) -C $$d push || exit $$?; \
	done

# For modules that define a clean-native target (e.g., ArtNativeTest).
clean-native:
	@for d in $(MODULES); do \
		$(MAKE) -C $$d clean-native >/dev/null 2>&1 || true; \
	done
