#!/usr/bin/env bash
# Builds voidultralight for the host platform (Linux / macOS).
#
#   scripts/build.sh                       release build for the host arch
#   scripts/build.sh --debug               debug build
#   scripts/build.sh --arch arm64          macOS: cross-configure for Apple Silicon
#   scripts/build.sh --arch x86_64         macOS: cross-configure for Intel / Rosetta
#   scripts/build.sh --clean               wipe the build directory first
#   scripts/build.sh --no-java             C++ only
#
# On macOS both arches are buildable from either machine — Ultralight ships separate mac-x64 and
# mac-arm64 SDKs (no fat binaries), so run it once per arch and ship both trees.
#
# The Ultralight SDK is downloaded and cached under mod/native/sdk/ by the CMake step; nothing
# needs to be installed for that (CMake's bundled libarchive reads .7z).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
native="$(dirname "$here")"

build_type=Release
arch=""
clean=0
java=ON

while [[ $# -gt 0 ]]; do
  case "$1" in
    --debug)    build_type=Debug ;;
    --release)  build_type=Release ;;
    --arch)     arch="$2"; shift ;;
    --arch=*)   arch="${1#*=}" ;;
    --clean)    clean=1 ;;
    --no-java)  java=OFF ;;
    -h|--help)  sed -n '2,18p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

build_dir="$native/build"
if [[ -n "$arch" ]]; then
  build_dir="$native/build-$arch"
fi

if [[ $clean -eq 1 ]]; then
  rm -rf "$build_dir"
fi

cmake_args=(
  -S "$native" -B "$build_dir"
  -DCMAKE_BUILD_TYPE="$build_type"
  -DVOIDUL_BUILD_JAVA="$java"
)

if [[ -n "$arch" ]]; then
  if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "--arch is macOS-only" >&2
    exit 2
  fi
  cmake_args+=(-DCMAKE_OSX_ARCHITECTURES="$arch")
  # 1.8.9 runs on an x64 JVM; 10.14 is the floor Ultralight 1.4 supports.
  cmake_args+=(-DCMAKE_OSX_DEPLOYMENT_TARGET=10.14)
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home)"
    export JAVA_HOME
  fi
fi

cmake "${cmake_args[@]}"
cmake --build "$build_dir" --config "$build_type" -j "$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"

echo
echo "natives staged in: $build_dir/natives/"
ls -1 "$build_dir"/natives/*/ 2>/dev/null || true
