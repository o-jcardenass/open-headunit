#!/usr/bin/env bash
# Builds ohu_pico_usb_stand_in.uf2 into $BUILD_DIR (default: ~/.cache/ohu-pico-build).
# Needs PICO_SDK_PATH (pico-sdk 2.1.1 with lib/tinyusb), cmake 3.13+ and arm-none-eabi-gcc on PATH.
set -euo pipefail
src="$(cd "$(dirname "$0")" && pwd)"
build="${BUILD_DIR:-$HOME/.cache/ohu-pico-build}"
: "${PICO_SDK_PATH:?set PICO_SDK_PATH to the pico-sdk checkout}"
cmake -S "$src" -B "$build" -DPICO_BOARD=pico "$@"
cmake --build "$build" -j"$(nproc)"
ls -l "$build/ohu_pico_usb_stand_in.uf2"
