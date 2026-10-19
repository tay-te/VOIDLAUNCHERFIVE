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
#   deferred   deferred_ao        the occlusion's raw term (colortex9, half resolution)
#   deferred1  deferred_aoresolve the occlusion's resolve (colortex12, half resolution; the balance step, 2026-10-13)
#   deferred2  deferred_shadow    the shadow's raw terms (colortex10)
#   deferred3  deferred_resolve   the shadow's resolve (colortex11)
#   deferred4  deferred           the light
#   deferred5  deferred_shafts    the sun shafts' raw term (colortex13, half resolution; workstream D)
#   prepare3   prepare_terrain   the terrain's light (colortex15; prepare1/2 are the clouds')
# Programs that run as several passes, each with a define of its own (overworld only; written after the loop below):
#   composite5..7  composite_rays  RAY_PASS 1..3, the light shafts' three radial blurs (HDR stage C)
declare -A passes=([deferred_ao]=deferred [deferred_aoresolve]=deferred1 [deferred_shadow]=deferred2
	[deferred_resolve]=deferred3 [deferred]=deferred4 [deferred_shafts]=deferred5
	[prepare_terrain]=prepare3)

programs=()
for f in "$shaders"/program/*.glsl; do
	name="$(basename "$f" .glsl)"
	# shared bodies, not programs of their own
	case "$name" in *_common) continue ;; esac
	# multi-pass programs, written below with their pass define
	case "$name" in composite_rays) continue ;; esac
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
# the light shafts' blurs: composite5..7 = composite_rays with RAY_PASS 1..3, overworld only
for pass in 1 2 3; do
	for stage in vsh fsh; do
		[ "$stage" = vsh ] && define=VERTEX_SHADER || define=FRAGMENT_SHADER
		printf '#version 330 compatibility
#define %s
#define %s
#define RAY_PASS %s
#include "/program/composite_rays.glsl"
' 			WORLD_OVERWORLD "$define" "$pass" > "$shaders/world0/composite$((pass + 4)).$stage"
	done
done
echo "wrappers: ${#programs[@]} programs x ${#dims[@]} dimensions"
