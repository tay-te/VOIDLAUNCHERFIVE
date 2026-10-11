#!/usr/bin/env bash
# VOID Welkin: (re)writes the thin per-dimension wrappers. Every program lives once in shaders/program/<name>.glsl;
# each dimension folder (world0 overworld, world-1 nether, world1 end; dimension.properties maps them) gets a .vsh
# and .fsh per program that only sets the version, the dimension and the stage, and includes it.
#
#   tools/wrappers.sh            after adding or removing a program in shaders/program/
set -eu
here="$(cd "$(dirname "$0")/.." && pwd)"
shaders="$here/shaders"

declare -A dims=([world0]=WORLD_OVERWORLD [world-1]=WORLD_NETHER [world1]=WORLD_END)

programs=()
for f in "$shaders"/program/*.glsl; do
	name="$(basename "$f" .glsl)"
	# shared bodies, not programs of their own
	case "$name" in *_common) continue ;; esac
	programs+=("$name")
done

for dim in "${!dims[@]}"; do
	dir="$shaders/$dim"
	rm -rf "$dir"
	mkdir -p "$dir"
	for name in "${programs[@]}"; do
		for stage in vsh fsh; do
			[ "$stage" = vsh ] && define=VERTEX_SHADER || define=FRAGMENT_SHADER
			printf '#version 330 compatibility\n#define %s\n#define %s\n#include "/program/%s.glsl"\n' \
				"${dims[$dim]}" "$define" "$name" > "$dir/$name.$stage"
		done
	done
done
echo "wrappers: ${#programs[@]} programs x ${#dims[@]} dimensions"
