PROJECT := tree-sitter
WASI_SDK_VERSION := 33
BINARYEN_VERSION := 130
PWD := $(CURDIR)

DETECTED_OS := $(shell uname -s | tr '[:upper:]' '[:lower:]')
DETECTED_ARCH := $(shell uname -m)

# Map OS name to standard keywords
ifeq ($(findstring linux,$(DETECTED_OS)),linux)
    PLATFORM := linux
else ifeq ($(findstring darwin,$(DETECTED_OS)),darwin)
    PLATFORM := macos
else ifeq ($(findstring msys,$(DETECTED_OS)),msys)
    PLATFORM := windows
else ifeq ($(findstring mingw,$(DETECTED_OS)),mingw)
    PLATFORM := windows
else
    PLATFORM := unknown
endif

# Map Architecture to standard keywords
ifeq ($(DETECTED_ARCH),x86_64)
    ARCH := x86_64
else ifeq ($(DETECTED_ARCH),amd64)
    ARCH := x86_64
else ifeq ($(DETECTED_ARCH),arm64)
    ARCH := arm64
else ifeq ($(DETECTED_ARCH),aarch64)
    ARCH := arm64
else
    ARCH := unknown
endif

# Binaryen archive names follow the format: binaryen-version_XXX-ARCH-PLATFORM.tar.gz
BINARYEN_TAR := binaryen-version_$(BINARYEN_VERSION)-$(ARCH)-$(PLATFORM).tar.gz
BINARYEN_DIR := binaryen-version_$(BINARYEN_VERSION)

# =========================================================================
# RECIPES / TARGETS
# =========================================================================
.PHONY: clean
clean:
	cargo clean
	rm -rf wasm

.PHONY: fmt
fmt:
	cargo fmt

.PHONY: info
info:
	@echo "--- System Diagnostics ---"
	@echo "Mapped Platform:   $(PLATFORM)"
	@echo "Mapped Arch:       $(ARCH)"
	@echo "Binaryen Target:   $(BINARYEN_TAR)"
	@echo "--------------------------"

.PHONY: get-wasisdk
get-wasisdk: info
	@if [ "$(PLATFORM)" = "unknown" ] || [ "$(ARCH)" = "unknown" ]; then \
	   echo "❌ Cannot install: Unsupported platform."; exit 1; \
	fi
	@if [ ! -d "$(PWD)/wasi-sdk" ]; then \
	   TAR_FILE="wasi-sdk-${WASI_SDK_VERSION}.0-$(ARCH)-$(PLATFORM).tar.gz"; \
	   DIR_NAME="wasi-sdk-${WASI_SDK_VERSION}.0"; \
	   DOWNLOAD_URL="https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${WASI_SDK_VERSION}/$${TAR_FILE}"; \
	   \
	   echo "Downloading wasi-sdk from: $${DOWNLOAD_URL}"; \
	   wget -q "$${DOWNLOAD_URL}" && \
	   tar -xzf "$${TAR_FILE}" && \
	   rm "$${TAR_FILE}" && \
	   mv wasi-sdk-${WASI_SDK_VERSION}.0* "$(PWD)/wasi-sdk" && \
	   echo "✅ wasi-sdk successfully installed!"; \
	fi

.PHONY: get-binaryen
get-binaryen: info
	@if [ "$(PLATFORM)" = "unknown" ] || [ "$(ARCH)" = "unknown" ]; then \
	   echo "❌ Cannot install: Unsupported platform."; exit 1; \
	fi
	@if [ ! -d "$(PWD)/binaryen" ]; then \
	   DOWNLOAD_URL="https://github.com/WebAssembly/binaryen/releases/download/version_$(BINARYEN_VERSION)/$(BINARYEN_TAR)"; \
	   \
	   echo "Downloading binaryen from: $${DOWNLOAD_URL}"; \
	   wget -q "$${DOWNLOAD_URL}" && \
	   tar -xzf "$(BINARYEN_TAR)" && \
	   rm "$(BINARYEN_TAR)" && \
	   mv "$(BINARYEN_DIR)" "$(PWD)/binaryen" && \
	   echo "✅ binaryen successfully installed to $(PWD)/binaryen"; \
	fi


.PHONY: build
build:
	WASI_SDK_PATH=$(PWD)/wasi-sdk \
	CC_wasm32_wasip1=$(PWD)/wasi-sdk/bin/clang \
	CFLAGS_wasm32_wasip1="--sysroot=$(PWD)/wasi-sdk/share/wasi-sysroot" \
	cargo build --target wasm32-wasip1 --release

.PHONY: optimize
optimize:
	${PWD}/binaryen/bin/wasm-opt -Oz --strip-debug --enable-bulk-memory --enable-nontrapping-float-to-int \
	  target/wasm32-wasip1/release/tree_sitter_wasm.wasm \
	  -o target/wasm32-wasip1/release/tree_sitter-opt.wasm

.PHONY: release
release:
	mkdir -p wasm
	@if [ -f target/wasm32-wasip1/release/tree_sitter_wasm-opt.wasm ]; then \
		cp target/wasm32-wasip1/release/tree_sitter_wasm-opt.wasm wasm/$(PROJECT).wasm; \
	else \
		cp target/wasm32-wasip1/release/tree_sitter_wasm.wasm wasm/$(PROJECT).wasm; \
	fi
	@echo "Built wasm/$(PROJECT).wasm ($$(wc -c < wasm/$(PROJECT).wasm) bytes)"

.PHONY: local
local: build optimize release

.PHONY: all
all: get-wasisdk get-binaryen local
