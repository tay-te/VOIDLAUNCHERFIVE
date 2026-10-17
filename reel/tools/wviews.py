"""Resolves a Welkin studio view and hour to the bench's tp command and absolute day tick.

    python -I tools/wviews.py <view> [hour] [views.json]     -> one line: tp<TAB>ticks<TAB>fov<TAB>weather<TAB>front<TAB>label
    python -I tools/wviews.py --list [views.json]            -> the views and their hours

Views come from the views catalogue (views.json, made from GloamShowcase's bench places) when it exists, else from
the small table below. Hours: a key of the view's own hours (0530 0800 1200 1630 1830 night, the vistas' 11000 /
500 / 10800), HH:MM or HHMM on bench day 6 (16:30 = 154500), night (210000, the full moon of day 8), or tNNNNNN for
an absolute tick.
"""
import json
import os
import sys

DAY6 = 6 * 24000
NIGHT = 8 * 24000 + 18000
BENCH_HOURS = {"0530": DAY6 - 500, "0800": DAY6 + 2000, "1200": DAY6 + 6000, "1630": DAY6 + 10500, "1830": DAY6 + 12500, "night": NIGHT}

# used only when views.json is missing: the bench's fixed eyes (GloamShowcase.BENCH_PLACES, y = the feet) and the vistas
BUILTIN = [
    ("plain", "tp @a -1009 148 -1386 180.0 6.0", BENCH_HOURS),
    ("forest", "tp @a -1109 138 -1530 135.0 2.0", BENCH_HOURS),
    ("village", "tp @a 2256 148 -480 180.0 28.0", BENCH_HOURS),
    ("valley", "tp @a 3200 100 488 0.0 12.0", BENCH_HOURS),
    ("shore", "tp @a 14140 66 10239 105.0 10.0", BENCH_HOURS),
    ("torch", "tp @a 2251 114 -557 338.0 4.0", {"night": NIGHT, "1200": DAY6 + 6000}),
    ("grove", "tp @a 3192 97 548 315.0 2.0", BENCH_HOURS),
    ("crowns", "tp @a 3252 137 512 180.0 30.0", {k: BENCH_HOURS[k] for k in ("1200", "1630", "1830")}),
    ("steppe", "tp @a 2256 180 -480 150.0 8.0", {"11000": DAY6 + 11000}),
    ("countryside", "tp @a 2256 190 -480 200.0 6.0", {"500": DAY6 + 500}),
    ("mesa", "tp @a 3200 215 -344 180.0 6.0", {"10800": DAY6 + 10800}),
]


def load(path):
    if path and os.path.isfile(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        return {v["name"]: v for v in data["views"]}, path
    return {n: {"name": n, "tp": tp, "hours": h, "fov": 70, "weather": "clear"} for n, tp, h in BUILTIN}, "built-in table"


def ticks(view, hour):
    hours = view.get("hours", {})
    if hour is None or hour == "":
        if len(hours) == 1:
            label = next(iter(hours))
            return hours[label], label
        raise SystemExit(f"{view['name']}: give an hour ({' '.join(hours)}, HH:MM, night or tNNNNNN)")
    h = hour.strip().lower()
    if h in hours:
        return hours[h], h
    if h.startswith("t") and h[1:].isdigit():
        return int(h[1:]), h
    if h == "night":
        return NIGHT, h
    clock = h.replace(":", "") if ":" in h else h
    if ":" in h:
        hh, mm = h.split(":", 1)
        clock = f"{int(hh):02d}{int(mm):02d}"
    if clock.isdigit() and len(clock) == 4 and int(clock[:2]) < 24 and int(clock[2:]) < 60:
        # Minecraft's tick 0 is 06:00
        return DAY6 + (int(clock[:2]) - 6) * 1000 + round(int(clock[2:]) * 1000 / 60), clock
    if h.isdigit():
        return DAY6 + int(h), h  # the bench's own rule for other numbers (benchTime)
    raise SystemExit(f"unknown hour {hour!r}")


def main(argv):
    if argv and argv[0] == "--list":
        views, source = load(argv[1] if len(argv) > 1 else None)
        print(f"views from {source}:")
        for v in views.values():
            print(f"  {v['name']:<12} {' '.join(v.get('hours', {}))}   ({v['tp']}{', fov ' + str(v['fov']) if v.get('fov', 70) != 70 else ''})")
        return
    name = argv[0]
    hour = argv[1] if len(argv) > 1 else None
    views, source = load(argv[2] if len(argv) > 2 else None)
    if name not in views:
        raise SystemExit(f"unknown view {name!r} (from {source}): {' '.join(views)}")
    v = views[name]
    t, label = ticks(v, hour)
    print("\t".join([v["tp"], str(t), str(v.get("fov", 70)), v.get("weather", "clear"), str(v.get("front") or "-"), label]))


if __name__ == "__main__":
    main(sys.argv[1:])
