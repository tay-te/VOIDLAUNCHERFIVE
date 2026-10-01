#!/usr/bin/env python3
"""Regenerate every VOID Expanse structure: NBT templates, template pools,
jigsaw structures, structure sets, processor lists, loot tables and biome
tags. Output is deterministic (seeded designs, gzip mtime 0, sorted JSON).

    python3 tools/structures/gen_structures.py              # write + validate
    python3 tools/structures/gen_structures.py --preview DIR  # also render PNGs
    python3 tools/structures/gen_structures.py --only lighthouse --preview DIR

Template conventions (see builder.py): template layer y=0 is the ground
course - jigsaw placement puts it on the topmost surface block and
beard_thin levels the surrounding terrain to it; unset cells are structure
void; interiors are explicit air. Pools use single_pool_element (the legacy
element would skip air and leave terrain inside rooms).
"""
import argparse
import importlib
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True  # keep the source tree free of __pycache__

from builder import floating_report  # noqa: E402
from loot import TABLES  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "expanse")

SALT0 = 1847261


def S(name, biomes, spacing, separation, processors=None, heightmap="WORLD_SURFACE_WG",
      terrain="beard_thin", start_height=0):
    return dict(name=name, biomes=["expanse:" + b for b in biomes], spacing=spacing, separation=separation,
                processors=processors, heightmap=heightmap, terrain=terrain, start_height=start_height)


STRUCTURES = [
    S("stone_circle", ["heather_moor"], 28, 10, processors="mossy_stones"),
    S("ruined_watchtower", ["verdant_peaks", "prismatic_peaks", "heather_moor"], 34, 11, processors="mossy_stones"),
    S("woodland_lodge", ["redwood_giants", "wisteria_vale"], 30, 10, processors="mossy_stones"),
    S("sun_shrine", ["opal_dunes"], 32, 11, processors="weathered_sandstone"),
    S("stilt_hamlet", ["willow_bayou"], 30, 10, heightmap="OCEAN_FLOOR_WG"),
    S("karst_pagoda", ["jade_karst"], 34, 11),
    S("lighthouse", ["palm_coast"], 36, 12),
    S("lumen_shrine", ["lumen_grove"], 26, 9, processors="mossy_stones"),
    S("frost_outpost", ["frostbloom_tundra"], 30, 10, processors="mossy_stones"),
    S("nomad_camp", ["amber_steppe"], 26, 9),
    S("cloud_monastery", ["cloud_forest"], 36, 12, processors="mossy_stones"),
]


def rule(block, prob, out):
    return {"input_predicate": {"block": block, "predicate_type": "minecraft:random_block_match", "probability": prob},
            "location_predicate": {"predicate_type": "minecraft:always_true"},
            "output_state": out}


# Per-instance weathering on top of the variation baked into each template.
PROCESSOR_LISTS = {
    "mossy_stones": {"processors": [{"processor_type": "minecraft:rule", "rules": [
        rule("minecraft:stone_bricks", 0.12, "minecraft:mossy_stone_bricks"),
        rule("minecraft:stone_bricks", 0.06, "minecraft:cracked_stone_bricks"),
        rule("minecraft:cobblestone", 0.2, "minecraft:mossy_cobblestone"),
        rule("expanse:limestone_bricks", 0.1, "expanse:mossy_limestone"),
    ]}]},
    "weathered_sandstone": {"processors": [{"processor_type": "minecraft:rule", "rules": [
        rule("expanse:smooth_opal_sandstone", 0.12, "expanse:opal_sandstone"),
        rule("expanse:cut_opal_sandstone", 0.08, "expanse:opal_sandstone"),
        rule("expanse:smooth_opal_sandstone", 0.03, "expanse:opal_sand"),
    ]}]},
}


def vanilla_order(o):
    """'type' first, remaining keys sorted - the layout vanilla's data generator writes."""
    if isinstance(o, dict):
        keys = sorted(o, key=lambda k: (k != "type", k))
        return {k: vanilla_order(o[k]) for k in keys}
    if isinstance(o, list):
        return [vanilla_order(v) for v in o]
    return o


def write_json(rel, obj):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(json.dumps(vanilla_order(obj), indent=2) + "\n")
    return path


def structure_json(s):
    return {
        "type": "minecraft:jigsaw",
        "biomes": f"#expanse:has_structure/{s['name']}",
        "max_distance_from_center": 80,
        "project_start_to_heightmap": s["heightmap"],
        "size": 1,
        "spawn_overrides": {},
        "start_height": {"absolute": s["start_height"]},
        "start_pool": f"expanse:{s['name']}/start",
        "step": "surface_structures",
        "terrain_adaptation": s["terrain"],
        "use_expansion_hack": False,
    }


def structure_set_json(s, salt):
    return {
        "placement": {"type": "minecraft:random_spread", "salt": salt, "separation": s["separation"],
                      "spacing": s["spacing"]},
        "structures": [{"structure": f"expanse:{s['name']}", "weight": 1}],
    }


def pool_json(s, template_names):
    proc = f"expanse:{s['processors']}" if s["processors"] else {"processors": []}
    return {
        "elements": [{"element": {"element_type": "minecraft:single_pool_element",
                                  "location": f"expanse:{s['name']}/{t}",
                                  "processors": proc,
                                  "projection": "rigid"},
                      "weight": 1} for t in template_names],
        "fallback": "minecraft:empty",
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", help="structure names to regenerate")
    ap.add_argument("--preview", metavar="DIR", help="render preview PNGs into DIR")
    ap.add_argument("--no-validate", action="store_true")
    args = ap.parse_args()

    written = []
    for i, s in enumerate(STRUCTURES):
        name = s["name"]
        if args.only and name not in args.only:
            continue
        mod = importlib.import_module(f"designs.{name}")
        builds = mod.build()
        tdir = os.path.join(DATA, "structure", name)
        os.makedirs(tdir, exist_ok=True)
        for f in os.listdir(tdir):           # drop stale variants of this structure
            if f.endswith(".nbt"):
                os.remove(os.path.join(tdir, f))
        names = []
        sheets = []
        for b in builds:
            b.finalize()
            for pos, st, why in floating_report(b):
                print(f"  warn {b.name}: {why}: {st} @ {pos}")
            path = os.path.join(tdir, b.name + ".nbt")
            b.save(path)
            names.append(b.name)
            written.append(path)
            if args.preview:
                import preview
                sheets.append(preview.sheet_image(b, cuts=[(min(b.size[1] - 1, 4), 0)], tile=18))
        if args.preview:
            preview.stack(sheets, os.path.join(args.preview, name + ".png"))
        written.append(write_json(f"worldgen/template_pool/{name}/start.json", pool_json(s, names)))
        written.append(write_json(f"worldgen/structure/{name}.json", structure_json(s)))
        written.append(write_json(f"worldgen/structure_set/{name}.json", structure_set_json(s, SALT0 + i)))
        written.append(write_json(f"tags/worldgen/biome/has_structure/{name}.json", {"values": s["biomes"]}))
        written.append(write_json(f"loot_table/chests/{name}.json", TABLES[name]))
        print(f"{name}: {len(names)} template(s) {', '.join(names)}")
    for pname, obj in PROCESSOR_LISTS.items():
        written.append(write_json(f"worldgen/processor_list/{pname}.json", obj))
    print(f"wrote {len(written)} files under {os.path.relpath(DATA, ROOT)}")

    if not args.no_validate:
        import validate
        problems = validate.run(DATA)
        if problems:
            print(f"VALIDATION FAILED ({len(problems)} problems)")
            for p in problems:
                print("  -", p)
            sys.exit(1)
        print("validation OK")


if __name__ == "__main__":
    main()
