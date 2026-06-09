PROJECT := tree-sitter
PWD := $(CURDIR)
WASI_SDK_PATH ?= $(HOME)/wasi-sdk

.PHONY: clean ensure-target build optimize release all

clean:
	cargo clean
	rm -rf wasm

ensure-target:
	@rustup target add wasm32-wasip1 2>/dev/null || true

build: ensure-target
	WASI_SDK_PATH=$(WASI_SDK_PATH) \
	CC_wasm32_wasip1=$(WASI_SDK_PATH)/bin/clang \
	CFLAGS_wasm32_wasip1="--sysroot=$(WASI_SDK_PATH)/share/wasi-sysroot" \
	cargo build --target wasm32-wasip1 --release

optimize: build
	@which wasm-opt > /dev/null 2>&1 && \
		wasm-opt -Oz --strip-debug --enable-bulk-memory --enable-nontrapping-float-to-int \
			target/wasm32-wasip1/release/tree_sitter_wasm.wasm \
			-o target/wasm32-wasip1/release/tree_sitter_wasm-opt.wasm || \
		echo "wasm-opt not found, skipping optimization"

release: optimize
	mkdir -p wasm
	@if [ -f target/wasm32-wasip1/release/tree_sitter_wasm-opt.wasm ]; then \
		cp target/wasm32-wasip1/release/tree_sitter_wasm-opt.wasm wasm/$(PROJECT).wasm; \
	else \
		cp target/wasm32-wasip1/release/tree_sitter_wasm.wasm wasm/$(PROJECT).wasm; \
	fi
	@echo "Built wasm/$(PROJECT).wasm ($$(wc -c < wasm/$(PROJECT).wasm) bytes)"

all: release
