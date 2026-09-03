#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# void-pvp — run every local check, in order, with one pass/fail summary.
#
#   scripts/verify-all.sh                every check; stops at the first failure
#   scripts/verify-all.sh --keep-going   run them all, report at the end
#   scripts/verify-all.sh --with-native  also cmake-build mod/native (downloads
#                                        the Ultralight SDK on a cold machine)
#   scripts/verify-all.sh --help
#
# What it does NOT do, on purpose:
#   * nothing here needs a display, so no OpenGL path is exercised. The GL
#     GPUDriver, the menu backdrop blur and the crosshair are only provable with
#     a real game on a real machine — see docs/TESTING.md, step 4.
#   * it never downloads Minecraft and never launches a JVM.
# ---------------------------------------------------------------------------
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
pvp="$(dirname "$here")"
cd "$pvp"

keep_going=0
with_native=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep-going) keep_going=1 ;;
    --with-native) with_native=1 ;;
    -h|--help) sed -n '3,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

if [[ -t 1 ]]; then
  B=$'\033[1m'; R=$'\033[31m'; G=$'\033[32m'; Y=$'\033[33m'; D=$'\033[2m'; N=$'\033[0m'
else
  B=""; R=""; G=""; Y=""; D=""; N=""
fi

names=(); results=(); durations=()
failed=0
step_no=0

section() {
  step_no=$((step_no + 1))
  printf '\n%s══ %d. %s %s\n' "$B" "$step_no" "$1" "$N"
}

# run <label> <command...>
run() {
  local label="$1"; shift
  local start end elapsed status
  printf '%s$ %s%s\n' "$D" "$*" "$N"
  start=$SECONDS
  "$@"
  status=$?
  end=$SECONDS
  elapsed=$((end - start))
  names+=("$label"); durations+=("${elapsed}s")
  if [[ $status -eq 0 ]]; then
    results+=("PASS")
    printf '%s  ✓ %s (%ss)%s\n' "$G" "$label" "$elapsed" "$N"
  else
    results+=("FAIL")
    failed=$((failed + 1))
    printf '%s  ✗ %s (exit %d)%s\n' "$R" "$label" "$status" "$N"
    if [[ $keep_going -eq 0 ]]; then
      summary
      exit 1
    fi
  fi
  return 0
}

skip() {
  local label="$1" why="$2"
  names+=("$label"); results+=("SKIP"); durations+=("-")
  printf '%s  – %s skipped: %s%s\n' "$Y" "$label" "$why" "$N"
}

summary() {
  printf '\n%s══ summary %s\n' "$B" "$N"
  local i colour
  for i in "${!names[@]}"; do
    case "${results[$i]}" in
      PASS) colour="$G" ;;
      FAIL) colour="$R" ;;
      *)    colour="$Y" ;;
    esac
    printf '%s%-5s%s %-46s %6s\n' "$colour" "${results[$i]}" "$N" "${names[$i]}" "${durations[$i]}"
  done
  if [[ $failed -eq 0 ]]; then
    printf '\n%sAll checks passed.%s\n' "$G" "$N"
  else
    printf '\n%s%d check(s) failed.%s\n' "$R" "$failed" "$N"
  fi
}

have() { command -v "$1" >/dev/null 2>&1; }

# ---------------------------------------------------------------------------
section "toolchain"
# ---------------------------------------------------------------------------
missing=0
for tool in cargo node pnpm java; do
  if have "$tool"; then
    printf '  %-6s %s\n' "$tool" "$("$tool" --version 2>&1 | head -1)"
  else
    printf '%s  %-6s MISSING%s\n' "$R" "$tool" "$N"
    missing=1
  fi
done
have cmake && printf '  %-6s %s\n' "cmake" "$(cmake --version | head -1)"
if [[ $missing -eq 1 ]]; then
  echo "Install the missing tools first — see pvp/README.md, Prerequisites." >&2
  exit 1
fi
if [[ ! -d node_modules ]]; then
  run "pnpm install" pnpm install --frozen-lockfile
fi

# ---------------------------------------------------------------------------
section "schema — the contracts (schema/validate.mjs)"
# ---------------------------------------------------------------------------
if node --input-type=module -e "await import.meta.resolve('ajv')" >/dev/null 2>&1; then
  run "schema examples + cross-checks" bash -c 'cd schema && node validate.mjs'
else
  skip "schema examples + cross-checks" "ajv not resolvable — run 'cd schema && npm i --no-save ajv'"
fi

# ---------------------------------------------------------------------------
section "rust — crates/ (void-core, void-bridge, void-loadout)"
# ---------------------------------------------------------------------------
run "cargo build --workspace"  cargo build --workspace
run "cargo test --workspace"   cargo test --workspace
run "cargo clippy -D warnings" cargo clippy --workspace --all-targets -- -D warnings

# ---------------------------------------------------------------------------
section "rust — apps/desktop/src-tauri (its own workspace)"
# ---------------------------------------------------------------------------
run "desktop cargo check"           bash -c 'cd apps/desktop/src-tauri && cargo check --all-targets'
run "desktop cargo check (no-deps)" bash -c 'cd apps/desktop/src-tauri && cargo check --no-default-features'
run "desktop cargo test"            bash -c 'cd apps/desktop/src-tauri && cargo test'

# ---------------------------------------------------------------------------
section "web — protocol, ui, ingame, desktop"
# ---------------------------------------------------------------------------
run "pnpm -r typecheck" pnpm -r typecheck
run "pnpm -r test"      pnpm -r test
run "pnpm -r build"     pnpm -r build
run "ingame Ultralight guard" pnpm --filter @void/ingame lint:ultralight
run "ingame size budget"      pnpm --filter @void/ingame size

# ---------------------------------------------------------------------------
section "mod — Legacy Fabric / Java 8 bytecode"
# ---------------------------------------------------------------------------
# `java -version` prints to stderr and some environments prepend a
# JAVA_TOOL_OPTIONS banner, so pull the first quoted version out of the whole
# output rather than trusting line 1.
java_major="$(java -version 2>&1 | grep -Eo 'version "[0-9]+' | head -1 | grep -Eo '[0-9]+$')"
if [[ "$java_major" =~ ^[0-9]+$ ]] && [[ "$java_major" -ge 17 ]]; then
  run "gradlew build (compile + JUnit suite)" bash -c 'cd mod && ./gradlew build'
else
  skip "gradlew build" "needs a JDK 17+ to run Gradle (found ${java_major:-none})"
fi

# ---------------------------------------------------------------------------
section "native — the Ultralight JNI binding (CPU renderer only)"
# ---------------------------------------------------------------------------
native_build="mod/native/build"
if [[ $with_native -eq 1 ]]; then
  run "native cmake build" bash -c 'cd mod/native && scripts/build.sh'
fi
if have cmake && [[ -f "$native_build/CMakeCache.txt" ]]; then
  # CpuRenderTest: 21 checks against the real engine, no display needed.
  # Nothing here touches OpenGL — that is the M1 gate, on a real machine.
  run "ctest cpu_render" ctest --test-dir "$native_build" --output-on-failure
else
  skip "ctest cpu_render" "no configured build in mod/native/build — run scripts/verify-all.sh --with-native"
fi

summary
[[ $failed -eq 0 ]] || exit 1
