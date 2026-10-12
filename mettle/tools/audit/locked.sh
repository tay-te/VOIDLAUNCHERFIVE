#!/usr/bin/env bash
# Runs a command while holding game slots: locked.sh <n slots: 1|2> <command...>
# Slots are C:/Users/bitto/VOID/.tools/locks/game1 and game2 (polled every 20 s), released by a trap.
L=/c/Users/bitto/VOID/.tools/locks
N=$1; shift
HELD=()
release() { for d in "${HELD[@]}"; do rm -rf "$d"; done; }
trap release EXIT
trap 'exit 130' INT TERM
for want in $(seq 1 "$N"); do
  got=
  until [ -n "$got" ]; do
    for i in 1 2; do
      if [[ " ${HELD[*]} " != *" $L/game$i "* ]] && mkdir "$L/game$i" 2>/dev/null; then
        echo "mettle-audit-server $(date +%T)" > "$L/game$i/owner"
        HELD+=("$L/game$i"); got=1; break
      fi
    done
    [ -n "$got" ] || sleep 20
  done
done
echo "locks: ${HELD[*]}"
"$@"
