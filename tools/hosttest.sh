#!/usr/bin/env bash
# Build + run the native scene/motion detector on the host (no Android toolchain).
set -euo pipefail
cd "$(dirname "$0")/.."

CPP=analysis/src/main/cpp
OUT=$(mktemp -d)/ht

c++ -std=c++17 -O2 -I"$CPP" \
  analysis/src/hosttest/cpp/host_test.cpp \
  "$CPP/scene_motion.cpp" \
  -o "$OUT"

"$OUT"
