AFLPP_DIR ?= ../AFLplusplus
AFL_CC ?= $(AFLPP_DIR)/afl-clang-fast
HOST_CC ?= cc
PYTHON ?= python3

CFLAGS ?= -O2 -g -Wall -Wextra -Wpedantic
LDFLAGS ?=

BUILD_DIR := build
TARGET := $(BUILD_DIR)/target_dsl
TARGET_CC := $(BUILD_DIR)/target_dsl_cc
MUTATOR := $(BUILD_DIR)/afl_llm_mutator.so

.PHONY: all check check-afl dirs mutator target smoke ipc-smoke clean

all: check-afl dirs $(TARGET) $(MUTATOR)

check: all smoke ipc-smoke

check-afl:
	@[ -x "$(AFL_CC)" ] || { \
		echo "error: $(AFL_CC) not found"; \
		echo "build AFL++ first, for example:"; \
		echo "  cd $(AFLPP_DIR) && make source-only"; \
		exit 1; \
	}

dirs:
	@mkdir -p "$(BUILD_DIR)" output runtime

$(MUTATOR): afl_llm_mutator.c | dirs
	$(HOST_CC) $(CFLAGS) -shared -fPIC -o "$@" "$<" $(LDFLAGS)

$(TARGET): target_dsl.c | dirs
	$(AFL_CC) -O2 -g -Wall -Wextra -o "$@" "$<"

$(TARGET_CC): target_dsl.c | dirs
	$(HOST_CC) $(CFLAGS) -o "$@" "$<" $(LDFLAGS)

smoke: dirs $(TARGET_CC) $(MUTATOR)

ipc-smoke:
	$(PYTHON) ipc_smoke.py

clean:
	rm -rf "$(BUILD_DIR)" output runtime/afl_llm_mutator.sock runtime/discovered
