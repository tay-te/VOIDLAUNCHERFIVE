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
# Programs that run as an Iris pass of another name (the shared pass layout, TECHNIQUES 3.0; workstream A keeps it):
#   deferred   deferred_ao       the occlusion's raw term (colortex9)
#   deferred1  deferred_shadow   the shadow's raw terms (colortex10)
#   deferred2  deferred_resolve  the resolve (colortex11, and the occlusion's history)
#   deferred3  deferred          the light
#   prepare3   prepare_terrain   the terrain's light (colortex15; prepare1/2 are the clouds')
declare -A passes=([deferred_ao]=deferred [deferred_shadow]=deferred1 [deferred_resolve]=deferred2 [deferred]=deferred3
	[prepare_terrain]=prepare3)

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
				"${dims[$dim]}" "$define" "$name" > "$dir/${passes[$name]:-$name}.$stage"
		done
	done
done
echo "wrappers: ${#programs[@]} programs x ${#dims[@]} dimensions"
