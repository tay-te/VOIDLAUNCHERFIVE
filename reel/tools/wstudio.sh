#!/usr/bin/env bash
# The VOID Welkin studio: a Minecraft that stays open (Gloam, VOID LOD, Expanse, Iris) with the Welkin pack junctioned
# in live from a worktree, so a shader fix is checked as edit -> reload -> shoot in seconds. Docs: docs/WELKIN_STUDIO.md
#
#   wstudio.sh start [A|B] [pack worktree] [minutes] [-Pkey=value ...]   launch; returns once the world and LOD are ready
#   wstudio.sh [A|B] reload                           reload the pack (Iris re-reads the files); prints compile errors
#   wstudio.sh [A|B] shoot <view> [hour] [name]       time set + tp to a bench view, one 1600x900 frame; prints its path
#   wstudio.sh [A|B] shoot at "<tp @a x y z yaw pitch>" <tick> [name]          any eye (y = the feet), any absolute tick
#   wstudio.sh [A|B] opt KEY=value ...  |  opt --reset                         pack options (Welkin.txt) and reload
#   wstudio.sh [A|B] pack <worktree>                 point the running studio at another worktree's welkin/ and reload
#   wstudio.sh [A|B] hold on|off                      world ticks frozen between shots (on: clouds/sway stand still)
#   wstudio.sh [A|B] views | status | log [n] | cmd "<server command>" | quit
#
# A or B (default A, or $WSTUDIO): studio A uses control port 7351, B 7352; each holds one of the machine's two game
# slots (C:/Users/bitto/VOID/.tools/locks/game1|game2) while it runs. The pack worktree (default
# C:/Users/bitto/VOID/.claude/worktrees/welkin) is any checkout whose welkin/ holds shaders/.
# Frames: reel/renders/welkin/<A|B>/<name>.png. Views: $WSTUDIO_VIEWS (default the scratchpad's studio_tools/views.json),
# else the built-in table in tools/wviews.py.
set -u
HERE="$(cd "$(dirname "$0")/.." && pwd)"
L=/c/Users/bitto/VOID/.tools/locks
JDK=C:/Users/bitto/VOID/.tools/jdk-25
DEFAULT_PACK=/c/Users/bitto/VOID/.claude/worktrees/welkin
VIEWS="${WSTUDIO_VIEWS:-/c/Users/bitto/AppData/Local/Temp/claude/C--Users-bitto-VOID/1fdbc860-dccf-4219-b4cc-97a4fdec7993/scratchpad/studio_tools/views.json}"
MODS="build/set/void-gloam-0.1.0.jar;build/set/void-lod-0.1.0.jar;build/set/void-expanse-0.1.0.jar"

studio="${WSTUDIO:-A}"
case "${1:-}" in A|B) studio="$1"; shift ;; esac
verb="${1:-status}"; shift || true
if [ "$verb" = start ]; then case "${1:-}" in A|B) studio="$1"; shift ;; esac; fi
case "$studio" in A) PORT=7351 ;; B) PORT=7352 ;; *) echo "studio must be A or B"; exit 2 ;; esac
BASE="http://127.0.0.1:$PORT"
RUN="$HERE/build/run/studio$studio"
LOG="$HERE/build/wstudio-$studio.log"
FRAMES="$HERE/renders/welkin/$studio"
SLOTFILE="$HERE/build/wstudio-$studio.slot"

alive() { curl -s -m 3 "$BASE/studio" 2>/dev/null | grep -q '"state"'; }
winpath() { cygpath -w "$1"; }

# Removes a junction (only the link, never what it points at); refuses a real folder.
unjunction() {
	local p="$1"
	[ -e "$p" ] || [ -L "$p" ] || return 0
	if MSYS_NO_PATHCONV=1 fsutil reparsepoint query "$(winpath "$p")" >/dev/null 2>&1; then
		MSYS_NO_PATHCONV=1 cmd /c rmdir "$(winpath "$p")" >/dev/null
	else
		echo "$p is a real folder, not a junction: move it away by hand"; exit 1
	fi
}

hold() {
	# the background holder: runs the game, keeps the slot fresh (stale after 3 hours), releases it when the game ends
	HOLD_SLOT="$1"; shift
	local slot="$HOLD_SLOT"
	trap 'rm -rf "$HOLD_SLOT" "$SLOTFILE" 2>/dev/null' EXIT
	( while [ -d "$slot" ]; do sleep 600; [ -d "$slot" ] && { rm -f "$slot/owner"; echo "welkin-studio $studio $(date +%T) (port $PORT)" > "$slot/owner"; }; done ) &
	local beat=$!
	export JAVA_HOME="$JDK"; export PATH="$JAVA_HOME/bin:$PATH"
	cd "$HERE" || exit 1
	./gradlew runClientGameTest --console=plain "$@"
	local code=$?
	kill $beat 2>/dev/null
	rm -rf "$RUN/saves"
	rm -rf "$slot" "$SLOTFILE"
	echo "[wstudio] game exited $code at $(date +%T); slot $(basename "$slot") released"
}

case "$verb" in
start)
	pack="${1:-$DEFAULT_PACK}"; [ $# -gt 0 ] && shift
	minutes="${1:-180}"; [ $# -gt 0 ] && shift
	pack="$(cd "$pack" 2>/dev/null && pwd)" || { echo "no such pack worktree"; exit 1; }
	[ -d "$pack/welkin/shaders" ] || { echo "$pack/welkin/shaders does not exist"; exit 1; }
	if alive; then echo "studio $studio already runs on $BASE"; curl -s "$BASE/studio"; echo; exit 1; fi
	slot=""
	while [ -z "$slot" ]; do
		find "$L" -maxdepth 1 -name 'game[0-9]' -type d -mmin +180 -exec rm -rf {} + 2>/dev/null
		for i in 1 2; do
			if mkdir "$L/game$i" 2>/dev/null; then slot="$L/game$i"; echo "welkin-studio $studio $(date +%T) (port $PORT)" > "$slot/owner"; break; fi
		done
		[ -z "$slot" ] && { echo "waiting for a game slot ($(cat $L/game*/owner 2>/dev/null | tr '\n' ';'))"; sleep 20; }
	done
	echo "$slot" > "$SLOTFILE"
	# the run folder: config as the bench had it, the pack a junction to the worktree's welkin/, a fresh world
	mkdir -p "$RUN/config" "$RUN/shaderpacks" "$FRAMES"
	for f in gloam.json void_lod.json sodium-options.json; do
		[ -f "$RUN/config/$f" ] || cp "$HERE/tools/wstudio-config/$f" "$RUN/config/$f"
	done
	unjunction "$RUN/shaderpacks/Welkin"
	MSYS_NO_PATHCONV=1 cmd /c mklink /J "$(winpath "$RUN/shaderpacks/Welkin")" "$(winpath "$pack/welkin")" >/dev/null || { rm -rf "$slot"; echo "mklink failed"; exit 1; }
	rm -f "$RUN/shaderpacks/Welkin.txt"
	cat > "$RUN/config/iris.properties" <<-EOF
	allowUnknownShaders=false
	colorSpace=SRGB
	disableUpdateMessage=true
	enableDebugOptions=false
	enableShaders=true
	maxShadowRenderDistance=32
	shaderPack=Welkin
	EOF
	rm -rf "$RUN/saves"
	echo "$pack" > "$RUN/pack.txt"
	start=$(date +%s)
	nohup bash "$0" "$studio" _hold "$slot" -Pwelkin.studio=true -Preel.control=$PORT -Pstudio.run=studio$studio -Piris "-Pmods=$MODS" \
		-Preel.width=1600 -Preel.height=900 -Pwelkin.minutes=$minutes -Pgloam.test.front=0.38 -Pgloam.test.bgThreads=1 "$@" > "$LOG" 2>&1 < /dev/null &
	disown
	echo "studio $studio: launching on $BASE (slot $(basename "$slot"), pack $pack, $minutes min, log $LOG)"
	while :; do
		s=$(curl -s -m 3 "$BASE/studio" 2>/dev/null)
		case "$s" in *'"state":"ready"'*) break ;; esac
		if [ ! -d "$slot" ] || grep -q "^\[wstudio\] game exited" "$LOG" 2>/dev/null; then
			echo "the studio stopped before it was ready:"; grep -iE "\[welkin\]|error|exception|FAIL" "$LOG" | grep -v "^\s*at " | tail -20; exit 1
		fi
		sleep 3
	done
	echo "studio $studio ready in $(( $(date +%s) - start )) s: $s"
	grep "\[welkin\] studio settled" "$LOG" | tail -1
	echo "frames: $FRAMES"
	;;
_hold) hold "$@" ;;
reload)
	n=$(grep -c "\[welkin\] reload [0-9]* begins" "$LOG" 2>/dev/null); n=$((n + 1))
	t0=$(date +%s%N)
	r=$(curl -s -X POST -m 120 "$BASE/reload"); echo "$r"
	sleep 0.3
	errs=$(awk -v b="[welkin] reload $n begins" 'index($0,b){on=1} on' "$LOG" | grep -iE "error|fail|exception|could not|unable" | grep -v "^\s*at " | head -30)
	echo "reload took $(( ($(date +%s%N) - t0) / 1000000 )) ms"
	if [ -n "$errs" ] || ! echo "$r" | grep -q '"error":""'; then echo "--- errors:"; echo "$errs"; exit 1; fi
	;;
shoot)
	if [ "${1:-}" = at ]; then
		tp="$2"; tick="$3"; name="${4:-at_$tick}"; fov=70; weather=clear; front="-"; label="t$tick"
	else
		view="${1:?shoot <view> [hour] [name]}"
		line=$(python -I "$HERE/tools/wviews.py" "$view" "${2:-}" "$VIEWS") || exit 1
		IFS=$'\t' read -r tp tick fov weather front label <<< "$line"
		name="${3:-${view}_$label}"
	fi
	[ "$front" != "-" ] && [ "$front" != "0.38" ] && echo "note: the bench shot this view at gloam.test.front=$front; this studio runs its own front (start with -Pgloam.test.front=$front to match)"
	t0=$(date +%s%N)
	r=$(curl -s -m 900 -X POST -G "$BASE/shoot" --data-urlencode "tp=$tp" --data-urlencode "time=$tick" --data-urlencode "fov=$fov" \
		--data-urlencode "weather=$weather" --data-urlencode "name=$name" --data-urlencode "out=$(winpath "$FRAMES")" --data-urlencode "w=${W:-1600}" --data-urlencode "h=${H:-900}")
	ms=$(( ($(date +%s%N) - t0) / 1000000 ))
	echo "$r" | python -I -c "
import json,sys
d=json.load(sys.stdin)
if 'error' in d: print('error:', d['error']); sys.exit(1)
rep=d.get('report') or {}
loading=rep.get('framesFilmedLoading') or []
print(f\"{d['file']}\n  {d['seconds']:.1f} s (settle {d['settleMs']/1000:.1f} s, frame {d['frameSeconds']:.1f} s), tick {d['time']}, eye {d['eye']} yaw {d['yaw']} pitch {d['pitch']}\" + (f', LOADING: {loading}' if loading else ''))
" || exit 1
	echo "  wall ${ms} ms"
	;;
opt)
	if [ "${1:-}" = --reset ]; then
		rm -f "$RUN/shaderpacks/Welkin.txt"; echo "options back to the pack's defaults"; exec bash "$0" "$studio" reload
	fi
	[ $# -gt 0 ] || { cat "$RUN/shaderpacks/Welkin.txt" 2>/dev/null || echo "(defaults)"; exit 0; }
	q=""; for kv in "$@"; do q="$q&$kv"; done
	curl -s -X POST "$BASE/pack?${q#&}"; echo
	;;
pack)
	# point the running studio at another worktree's welkin/ (the junction is relinked) and reload
	if [ $# -eq 0 ]; then echo "pack: $(cat "$RUN/pack.txt" 2>/dev/null)"; exit 0; fi
	pack="$(cd "$1" 2>/dev/null && pwd)" || { echo "no such pack worktree"; exit 1; }
	[ -d "$pack/welkin/shaders" ] || { echo "$pack/welkin/shaders does not exist"; exit 1; }
	unjunction "$RUN/shaderpacks/Welkin"
	MSYS_NO_PATHCONV=1 cmd /c mklink /J "$(winpath "$RUN/shaderpacks/Welkin")" "$(winpath "$pack/welkin")" >/dev/null || { echo "mklink failed"; exit 1; }
	echo "$pack" > "$RUN/pack.txt"
	echo "pack: $pack"
	alive && exec bash "$0" "$studio" reload
	;;
hold) curl -s -X POST "$BASE/hold?on=$([ "${1:-on}" = off ] && echo false || echo true)"; echo ;;
views) python -I "$HERE/tools/wviews.py" --list "$VIEWS" ;;
status)
	if alive; then curl -s "$BASE/studio"; echo; curl -s "$BASE/status"; echo; else echo "studio $studio is not running"; fi
	echo "pack: $(cat "$RUN/pack.txt" 2>/dev/null); slot: $(cat "$SLOTFILE" 2>/dev/null || echo none); log: $LOG; frames: $FRAMES"
	;;
log) tail -n "${1:-40}" "$LOG" ;;
cmd) curl -s -X POST --data "$*" "$BASE/cmd"; echo ;;
quit)
	if ! alive; then echo "studio $studio is not running"; exit 0; fi
	curl -s -X POST "$BASE/quit"; echo
	slot=$(cat "$SLOTFILE" 2>/dev/null)
	for i in $(seq 1 90); do
		grep -q "^\[wstudio\] game exited" "$LOG" 2>/dev/null && break
		sleep 2
	done
	tail -n 1 "$LOG"
	[ -n "$slot" ] && [ -d "$slot" ] && echo "warning: slot $slot still held"
	;;
*) sed -n '2,16p' "$0"; exit 2 ;;
esac
