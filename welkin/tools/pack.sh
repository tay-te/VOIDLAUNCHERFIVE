#!/usr/bin/env bash
# VOID Welkin: zips shaders/ into a game run's shaderpacks folder as Welkin.zip and makes it that run's pack.
#
#   tools/pack.sh <shaderpacks dir> [OPTION=value ...]
#
# The run's config/iris.properties (beside shaderpacks/) gets shaderPack=Welkin.zip and enableShaders=true. Options
# given after the folder (e.g. DEBUG_VIEW=1 SOFT_BANDS=0, or a bare DEBUG_JOIN for DEBUG_JOIN=true) are written to
# Welkin.zip.txt, Iris's per-pack settings file; with none given that file is removed, so the pack runs on its defaults (the MEDIUM profile's values).
#
# Examples:
#   tools/pack.sh <bench>/lod/build/run/clientGameTest/shaderpacks
#   tools/pack.sh <gloam>/build/run/clientGameTest/shaderpacks DEBUG_VIEW=1
set -eu
if [ $# -lt 1 ]; then
	echo "usage: $0 <shaderpacks dir> [OPTION=value ...]" >&2
	exit 2
fi
here="$(cd "$(dirname "$0")/.." && pwd)"
packs="$1"; shift
mkdir -p "$packs"
packs="$(cd "$packs" && pwd)"
config="$(dirname "$packs")/config"
mkdir -p "$config"

# a zip with shaders/ at its root; the JDK's jar writes plain zips (M: no manifest)
jar="${JAVA_HOME:-C:/Users/bitto/VOID/.tools/jdk-25}/bin/jar"
[ -x "$jar" ] || [ -x "$jar.exe" ] || jar="C:/Users/bitto/VOID/.tools/jdk-25/bin/jar"
tmp="$packs/Welkin.zip.tmp"
rm -f "$tmp"
(cd "$here" && "$jar" --create --no-manifest --file "$tmp" shaders)
mv -f "$tmp" "$packs/Welkin.zip"

if [ $# -gt 0 ]; then
	# a bare NAME switches that option on (NAME=true)
	for o in "$@"; do
		case "$o" in *=*) printf '%s\n' "$o" ;; *) printf '%s=true\n' "$o" ;; esac
	done > "$packs/Welkin.zip.txt"
else
	rm -f "$packs/Welkin.zip.txt"
fi

props="$config/iris.properties"
touch "$props"
set_prop() {
	if grep -q "^$1=" "$props"; then
		sed -i "s|^$1=.*|$1=$2|" "$props"
	else
		printf '%s=%s\n' "$1" "$2" >> "$props"
	fi
}
set_prop shaderPack Welkin.zip
set_prop enableShaders true
echo "Welkin.zip -> $packs ($(wc -c < "$packs/Welkin.zip") bytes)${*:+, options: $*}"
