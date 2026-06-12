PROJECT := tree-sitter
WASI_SDK_VERSION := 33
PWD := $(CURDIR)

# Ensure cargo (installed via rustup) is on PATH
export PATH := $(HOME)/.cargo/bin:$(PATH)

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

WASI_SYSROOT := $(PWD)/wasi-sdk/share/wasi-sysroot

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

.PHONY: get-rust
get-rust:
	@if ! command -v cargo >/dev/null 2>&1; then \
	   echo "Rust/Cargo not found. Installing via rustup..."; \
	   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y && \
	   . "$(HOME)/.cargo/env" && \
	   echo "✅ Rust successfully installed!"; \
	else \
	   echo "✅ Rust/Cargo already installed: $$(cargo --version)"; \
	fi
	@if ! rustup target list --installed | grep -q wasm32-unknown-unknown; then \
	   echo "Adding wasm32-unknown-unknown target..."; \
	   rustup target add wasm32-unknown-unknown && \
	   echo "✅ wasm32-unknown-unknown target added!"; \
	else \
	   echo "✅ wasm32-unknown-unknown target already installed"; \
	fi

.PHONY: build
build:
	WASI_SDK_PATH=$(PWD)/wasi-sdk \
	CC_wasm32_unknown_unknown=$(PWD)/wasi-sdk/bin/clang \
	CFLAGS_wasm32_unknown_unknown="-isystem $(WASI_SYSROOT)/include/wasm32-wasip1 -Wno-incompatible-pointer-types -Wno-implicit-function-declaration" \
	CARGO_TARGET_WASM32_UNKNOWN_UNKNOWN_RUSTFLAGS="-C link-arg=--allow-multiple-definition" \
	cargo build --target wasm32-unknown-unknown --release

.PHONY: release
release:
	mkdir -p wasm
	cp target/wasm32-unknown-unknown/release/tree_sitter_wasm.wasm wasm/$(PROJECT).wasm
	@echo "Built wasm/$(PROJECT).wasm ($$(wc -c < wasm/$(PROJECT).wasm) bytes)"

.PHONY: local
local: build release

.PHONY: all
all: get-rust get-wasisdk local
