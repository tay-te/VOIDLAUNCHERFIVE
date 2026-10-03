#!/usr/bin/env python3
"""Regenerate every VOID Expanse structure: NBT templates, template pools,
jigsaw structures, structure sets, processor lists, loot tables, biome and
structure tags. Output is deterministic (seeded designs, gzip mtime 0,
sorted JSON).

    python3 tools/structures/gen_structures.py                 # write + validate
    python3 tools/structures/gen_structures.py --preview DIR   # also render PNGs (3 assembled seeds each)
    python3 tools/structures/gen_structures.py --only lighthouse --preview DIR --pieces

Each designs/<name>.py exposes STRUCTURE (placement settings) and pools()
-> {pool: [Piece | Empty | Feature]}. Pool `start` is the start pool; jigsaw
blocks in the templates refer to pools as expanse:<name>/<pool>.

Template conventions (see builder.py): layer y=0 of a rigid piece is the
ground course - jigsaw placement puts the start piece's layer 0 on the
topmost surface block and beard_thin levels the terrain to it; unset cells
are structure void; interiors are explicit air. Elements use
single_pool_element (the legacy element would skip air).
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
from pieces import Empty, Feature, Piece  # noqa: E402
from processors import PROCESSOR_LISTS  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "expanse")
SALT0 = 1847261
MODULES = ["stone_circle", "ruined_watchtower", "woodland_lodge", "sun_shrine", "stilt_hamlet", "karst_pagoda",
           "lighthouse", "lumen_shrine", "frost_outpost", "nomad_camp", "cloud_monastery"]
# small structures (appended so the salts above stay put); they join the SHARED_SETS below
MODULES += ["wayside_shrine", "wayside_shrine_karst", "wayside_shrine_sand", "campsite", "campsite_snow",
            "campsite_steppe", "campsite_bamboo", "shepherd_bothy", "mammoth_dig", "tea_garden", "treehouse",
            "fairy_ring", "bayou_shack", "windpump", "sand_colossus", "terrace_farm", "pilgrim_shrine",
            "miners_camp", "beach_hut", "summit_cairn", "old_well", "old_well_limestone", "old_well_sandstone",
            "old_well_mud", "graveyard"]
# settlement landmarks (appended last so every salt above stays put)
MODULES += ["palm_harbour", "logging_camp", "wisteria_manor", "caravanserai"]
# underground: they stand on the floors of the deep cavern halls (shared set cavern_halls)
MODULES += ["cavern_mine_camp", "deepstone_ruins", "cavern_landing"]
# villages: vanilla's village machinery in each style's materials (villagekit.py, village_styles.py); they
# share the "villages" set below and are #minecraft:village
MODULES += ["village_wisteria", "village_redwood", "village_coast", "village_steppe", "village_moor", "village_karst",
            "village_tundra"]
# Shared random_spread sets: a design joins one with STRUCTURE["set"] (and optional "weight"). Vanilla tries a
# set's structures in weighted random order until one suits the biome (cf. minecraft:abandoned_camp), so each
# grid cell gets at most one of them. The wayside set keeps 2 chunks clear of the biome-sights grid.
SHARED_SETS = {
    "biome_sights": dict(spacing=12, separation=5, salt=930417361),
    "wayside": dict(spacing=14, separation=6, salt=930417383, exclusion=("biome_sights", 2)),
    # underground: one structure at most per cavern hall, in the chunk at the hall's centre
    # (world/terrain/CavernHallPlacement.java); a design joins with cavern=True (and set="cavern_halls")
    "cavern_halls": dict(placement="expanse:cavern_halls", salt=930417401, frequency=0.6),
}
# villages: vanilla's spacing (structure_set/villages: 34/8) with a salt of our own. avoid_radius makes the
# small sights keep that far from a grid point where a village could stand, as they do from big structures.
SHARED_SETS["villages"] = dict(spacing=34, separation=8, salt=930417419, avoid_radius=112)

FILL = {"stone", "dirt", "andesite", "tuff", "granite", "diorite", "gravel", "coarse_dirt", "sand", "sandstone",
        "deepslate", "cobblestone", "snow_block", "packed_ice", "clay", "mud", "rooted_dirt"}
LEGACY_FILES = ["worldgen/processor_list/mossy_stones.json", "worldgen/processor_list/weathered_sandstone.json"]

STRUCTURE_TAGS = {
    # exploration maps from the lighthouse map drawer point at the nearest of these
    "on_lighthouse_maps": ["expanse:karst_pagoda", "expanse:sun_shrine"],
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


def structure_json(name, s):
    return {
        # expanse:sited_jigsaw is minecraft:jigsaw that picks its site like a builder: not under water,
        # not on a cliff edge or in a pit, and (small sights) not where a big structure could stand
        # (world/SitedJigsawStructure.java). Structures that belong on water opt out of the water check
        # with sited=False, as does anything placed on the sea floor.
        "type": "expanse:sited_jigsaw",
        "biomes": f"#expanse:has_structure/{name}",
        "max_distance_from_center": s.get("max_distance", 80),
        **({} if s.get("cavern") else {"project_start_to_heightmap": s.get("heightmap", "WORLD_SURFACE_WG")}),
        "size": s.get("size", 1),
        "spawn_overrides": {},
        "start_height": {"absolute": s.get("start_height", 0)},
        "start_pool": f"expanse:{name}/start",
        "step": "surface_structures",
        "terrain_adaptation": s.get("terrain", "beard_box" if s.get("cavern") else "beard_thin"),
        "use_expansion_hack": s.get("expansion_hack", False),   # villages: as vanilla's (houses in a street's lot)
        **({"max_relief": s["max_relief"]} if "max_relief" in s else {}),
        **({} if sited(s) else {"allow_water": True}),
        **({"avoid": [{"other_set": f"expanse:{n}", "radius": min(256, avoid_radius(S) + s.get("avoid_extra", 0))}
                      for n, S in big_structures()] + shared_avoid(s)}
           if "set" in s and not s.get("cavern") else {}),
        # cavern structures stand on the floor of a deep hall, wherever the hall is (CavernModel)
        **({"in_cavern": True} if s.get("cavern") else {}),
    }


def sited(s):
    return s.get("sited", s.get("heightmap") != "OCEAN_FLOOR_WG")


def structure_set_json(name, s, salt):
    placement = {"type": "minecraft:random_spread", "salt": salt, "separation": s["separation"],
                 "spacing": s["spacing"]}
    if s.get("exclusion"):
        placement["exclusion_zone"] = {"other_set": f"expanse:{s['exclusion'][0]}", "chunk_count": s["exclusion"][1]}
    return {"placement": placement, "structures": [{"structure": f"expanse:{name}", "weight": 1}]}


def shared_set_json(cfg, members):
    """A SHARED_SETS entry with its member structures [(name, weight)]."""
    if cfg.get("placement") == "expanse:cavern_halls":
        pl = {"type": "expanse:cavern_halls", "salt": cfg["salt"], "frequency": cfg["frequency"]}
        return {"placement": pl, "structures": [{"structure": f"expanse:{n}", "weight": w} for n, w in members]}
    pl = {"type": "minecraft:random_spread", "salt": cfg["salt"], "separation": cfg["separation"],
          "spacing": cfg["spacing"]}
    if cfg.get("exclusion"):
        pl["exclusion_zone"] = {"other_set": f"expanse:{cfg['exclusion'][0]}", "chunk_count": cfg["exclusion"][1]}
    return {"placement": pl, "structures": [{"structure": f"expanse:{n}", "weight": w} for n, w in members]}


def big_structures():
    """[(name, STRUCTURE)] of the structures with a set of their own."""
    out = []
    for name in MODULES:
        S = getattr(importlib.import_module(f"designs.{name}"), "STRUCTURE", {})
        if "set" not in S:
            out.append((name, S))
    return out


def avoid_radius(S):
    """How far (blocks) small sights keep from a big structure's grid point: its reach plus a margin."""
    return S.get("max_distance", 80) + 16


def shared_avoid(s):
    """avoid entries for the shared sets with an avoid_radius (the villages), for structures not in them."""
    return [{"other_set": f"expanse:{n}", "radius": c["avoid_radius"]} for n, c in SHARED_SETS.items()
            if c.get("avoid_radius") and s.get("set") != n and shared_set_members(n)]


def shared_set_members(set_name):
    """[(name, weight)] of the MODULES whose STRUCTURE joins `set_name` (modules that fail to import are skipped)."""
    out = []
    for name in MODULES:
        try:
            S = getattr(importlib.import_module(f"designs.{name}"), "STRUCTURE", {})
        except Exception as e:  # noqa: BLE001 - another design mid-edit must not block this one
            print(f"  warn: {name} not importable for set {set_name}: {e}")
            continue
        if S.get("set") == set_name:
            out.append((name, S.get("weight", 1)))
    return out


def element_json(name, e):
    if isinstance(e, Empty):
        return {"element": {"element_type": "minecraft:empty_pool_element"}, "weight": e.weight}
    if isinstance(e, Feature):
        return {"element": {"element_type": "minecraft:feature_pool_element", "feature": e.feature,
                            "projection": "rigid"}, "weight": e.weight}
    proc = f"expanse:{e.processors}" if e.processors else {"processors": []}
    return {"element": {"element_type": "minecraft:single_pool_element",
                        "location": f"expanse:{name}/{e.build.name}", "processors": proc,
                        "projection": e.projection}, "weight": e.weight}


def assembly_pools(name, pools):
    """Pools in the shape assemble.assemble() expects."""
    out = {}
    for pname, elems in pools.items():
        lst = []
        for e in elems:
            if isinstance(e, Empty):
                lst.append((None, e.weight))
            elif isinstance(e, Feature):
                lst.append((("feature", e.feature), e.weight))
            else:
                e.build._procs = e.processors
                e.build._proj = e.projection
                lst.append((e.build, e.weight))
        out[f"expanse:{name}/{pname}"] = lst
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", help="structure names to regenerate")
    ap.add_argument("--preview", metavar="DIR", help="render preview PNGs into DIR")
    ap.add_argument("--pieces", action="store_true", help="with --preview: also render every piece")
    ap.add_argument("--seeds", type=int, default=3, help="assembled previews per structure")
    ap.add_argument("--no-validate", action="store_true")
    args = ap.parse_args()

    written = []
    for i, name in enumerate(MODULES):
        if args.only and name not in args.only:
            continue
        mod = importlib.import_module(f"designs.{name}")
        S = mod.STRUCTURE
        pools = mod.pools()
        builds = {}
        for elems in pools.values():
            for e in elems:
                if isinstance(e, Piece):
                    if e.build.name in builds and builds[e.build.name] is not e.build:
                        raise ValueError(f"{name}: duplicate piece name {e.build.name}")
                    builds[e.build.name] = e.build
        tdir = os.path.join(DATA, "structure", name)
        pdir = os.path.join(DATA, "worldgen", "template_pool", name)
        for d, ext in ((tdir, ".nbt"), (pdir, ".json")):
            if os.path.isdir(d):
                for f in os.listdir(d):
                    if f.endswith(ext):
                        os.remove(os.path.join(d, f))
        os.makedirs(tdir, exist_ok=True)
        for b in builds.values():
            b.finalize()
            for pos, st, why in floating_report(b):
                print(f"  warn {b.name}: {why}: {st} @ {pos}")
            path = os.path.join(tdir, b.name + ".nbt")
            b.save(path)
            written.append(path)
        for pname, elems in pools.items():
            written.append(write_json(f"worldgen/template_pool/{name}/{pname}.json",
                                      {"elements": [element_json(name, e) for e in elems],
                                       "fallback": getattr(mod, "FALLBACKS", {}).get(pname, "minecraft:empty")}))
        written.append(write_json(f"worldgen/structure/{name}.json", structure_json(name, S)))
        if "set" not in S:                             # shared-set members are written after the loop
            written.append(write_json(f"worldgen/structure_set/{name}.json", structure_set_json(name, S, SALT0 + i)))
        for folder, objs in getattr(mod, "DATA", {}).items():   # data a design owns (small structures)
            for fname, obj in objs.items():
                written.append(write_json(f"{folder}/{fname}.json", obj))
            if folder == "worldgen/processor_list":
                PROCESSOR_LISTS.update(objs)           # so the previews apply them too
        written.append(write_json(f"tags/worldgen/biome/has_structure/{name}.json",
                                  {"values": [b if ":" in b else "expanse:" + b for b in S["biomes"]]}))
        print(f"{name}: {len(builds)} template(s), pools {', '.join(pools)}")
        if args.preview:
            import assemble
            import preview
            ap_ = assembly_pools(name, pools)
            panels = []
            for seed in range(1, args.seeds + 1):
                ps, feats = assemble.assemble(ap_, f"expanse:{name}/start", S.get("size", 1), seed * 7919,
                                              S.get("max_distance", 80),
                                              **({"fallbacks": {f"expanse:{name}/{k}": v for k, v in mod.FALLBACKS.items()},
                                                  "expansion_hack": True}
                                                 if S.get("expansion_hack") else {}))
                cells, ents = assemble.combined(ps, PROCESSOR_LISTS, seed)
                title = f"{name} seed {seed}: {len(ps)} pieces " + ",".join(p.build.name.replace(name + '_', '')
                                                                           for p in ps)
                above = {q: st for q, st in cells.items() if q[1] >= 0}
                below = {q: st for q, st in cells.items() if q[1] < 0 and st.short not in FILL}
                panels.append(preview.render(above, seed % 4, 14, title=title[:110],
                                             entities=[e for e in ents if e["pos"][1] >= 0]))
                panels.append(preview.plan(above, px=6))
                if below:
                    panels.append(preview.render(below, seed % 4, 14, title="underground (natural fill hidden)",
                                                 entities=[e for e in ents if e["pos"][1] < 0]))
                    panels.append(preview.render(below, seed % 4, 14, cut_y=3, title="underground cut"))
            preview.stack([preview.grid(panels, 2)], os.path.join(args.preview, name + ".png"))
            if args.pieces:
                for b in builds.values():
                    preview.stack([preview.sheet_image(b, cuts=[(min(b.size[1] - 1, 4), 0)], tile=16)],
                                  os.path.join(args.preview, "pieces", b.name + ".png"))
    for sname, cfg in SHARED_SETS.items():
        members = shared_set_members(sname)
        if members and (not args.only or any(n in args.only for n, _ in members)):
            written.append(write_json(f"worldgen/structure_set/{sname}.json", shared_set_json(cfg, members)))
    if not args.only:
        for stale in LEGACY_FILES:                    # files written by earlier versions of this script
            p = os.path.join(DATA, stale)
            if os.path.exists(p):
                os.remove(p)
        for pname, obj in PROCESSOR_LISTS.items():
            written.append(write_json(f"worldgen/processor_list/{pname}.json", obj))
        for tname, obj in TABLES.items():
            written.append(write_json(f"loot_table/chests/{tname}.json", obj))
        for tname, vals in STRUCTURE_TAGS.items():
            written.append(write_json(f"tags/worldgen/structure/{tname}.json", {"values": vals}))
    # the villages join vanilla's #minecraft:village (/locate, maps and the rest treat them as villages)
    villages = [f"expanse:{n}" for n in MODULES
                if getattr(importlib.import_module(f"designs.{n}"), "STRUCTURE", {}).get("village")]
    written.append(write_json(os.path.join("..", "minecraft", "tags", "worldgen", "structure", "village.json"),
                              {"replace": False, "values": villages}))
    print(f"wrote {len(written)} files under {os.path.relpath(DATA, ROOT)}")

    if not args.no_validate:
        import validate
        problems, notes = validate.run(DATA)
        for n in notes:
            print("  note:", n)
        if problems:
            print(f"VALIDATION FAILED ({len(problems)} problems)")
            for p in problems:
                print("  -", p)
            sys.exit(1)
        print("validation OK")


if __name__ == "__main__":
    main()
