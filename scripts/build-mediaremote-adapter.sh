#!/bin/sh
set -eu

REV=73f14ab1568371e6e3c44063f21c34c5e2712c4d
OUT="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/assets/evergreenhud/media/macos"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone https://github.com/ungive/mediaremote-adapter.git "$WORK/src"
git -C "$WORK/src" checkout "$REV"
cmake -S "$WORK/src" -B "$WORK/build"
cmake --build "$WORK/build"

ditto -c -k --keepParent "$WORK/build/MediaRemoteAdapter.framework" "$OUT/MediaRemoteAdapter.framework.zip"
