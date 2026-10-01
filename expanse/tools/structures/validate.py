#!/usr/bin/env python3
"""Validate the generated structure data against vanilla 26.3 ground truth.

Checks every template (.nbt) by re-reading it with our own reader: root
layout, DataVersion, size, palette indices, positions, block ids (vanilla
blockstate files / the mod's allowed list), full property sets (vs. the
palettes of all vanilla templates, the block-family analogue and the
blockstate files), block-entity ids and loot references. Then checks every
JSON file: parses, schema mirrors vanilla, and all cross references resolve.

Vanilla extracted resources are read from $MC_RES (default /root/mc/res).
"""
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import blocks as BL  # noqa: E402
import nbt  # noqa: E402

MC_RES = os.environ.get("MC_RES", "/root/mc/res")
VANILLA_BS = os.path.join(MC_RES, "assets", "minecraft", "blockstates")
VANILLA_ITEMS = os.path.join(MC_RES, "assets", "minecraft", "items")
VANILLA_TEMPLATES = os.path.join(MC_RES, "data", "minecraft", "structure")
MOD_ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "expanse"))
DATA_VERSION = 5023
MOD_BIOMES = {"expanse:" + b for b in ("frostbloom_tundra heather_moor wisteria_vale redwood_giants lumen_grove "
                                        "willow_bayou amber_steppe opal_dunes jade_karst cloud_forest verdant_peaks "
                                        "prismatic_peaks palm_coast").split()}

# representative vanilla block (present in vanilla template palettes) for each schema family
FAMILY_REP = {id(BL.STAIRS): "minecraft:oak_stairs", id(BL.SLAB): "minecraft:oak_slab",
              id(BL.WALL): "minecraft:cobblestone_wall", id(BL.CROSS): "minecraft:oak_fence",
              id(BL.FENCE_GATE): "minecraft:spruce_fence_gate", id(BL.DOOR): "minecraft:oak_door",
              id(BL.TRAPDOOR): "minecraft:oak_trapdoor", id(BL.AXIS): "minecraft:oak_log",
              id(BL.LEAVES): "minecraft:oak_leaves", id(BL.BED): "minecraft:red_bed",
              id(BL.CANDLE): "minecraft:candle", id(BL.LANTERN): "minecraft:lantern",
              id(BL.CAMPFIRE): "minecraft:campfire", id(BL.CHEST): "minecraft:chest",
              id(BL.FURNACE): "minecraft:furnace", id(BL.HALF2): "minecraft:tall_grass",
              id(BL.SNOWY): "minecraft:grass_block"}


def _vanilla_corpus():
    props = {}
    for f in glob.glob(os.path.join(VANILLA_TEMPLATES, "**", "*.nbt"), recursive=True):
        _, r = nbt.load(f)
        pals = [r["palette"]] if "palette" in r else list(r.get("palettes", []))
        for pal in pals:
            for p in pal:
                d = props.setdefault(p["id"], {})
                for k, v in p.get("properties", {}).items():
                    d.setdefault(k, set()).add(v)
    return props


def _blockstate_props(path):
    """{prop: set(values)} mentioned in a blockstate file."""
    with open(path) as f:
        d = json.load(f)
    out = {"__multipart__": "multipart" in d}
    if "variants" in d:
        for key in d["variants"]:
            if not key:
                continue
            for kv in key.split(","):
                k, v = kv.split("=")
                out.setdefault(k, set()).add(v)
    for part in d.get("multipart", []):
        w = part.get("when", {})
        conds = w.get("OR") or w.get("AND") or [w]
        for c in conds:
            for k, v in c.items():
                out.setdefault(k, set()).update(str(v).split("|"))
    return out


class Ctx:
    def __init__(self, data):
        self.data = data
        self.problems = []
        self.corpus = _vanilla_corpus() if os.path.isdir(VANILLA_TEMPLATES) else None
        self.vanilla_blocks = {f[:-5] for f in os.listdir(VANILLA_BS)} if os.path.isdir(VANILLA_BS) else None
        self.vanilla_items = {f[:-5] for f in os.listdir(VANILLA_ITEMS)} if os.path.isdir(VANILLA_ITEMS) else None
        mi = os.path.join(MOD_ASSETS, "items")
        self.mod_items = {f[:-5] for f in os.listdir(mi)} if os.path.isdir(mi) else None
        self._bs_cache = {}
        if self.corpus is None or self.vanilla_blocks is None:
            self.problems.append(f"vanilla resources not found under {MC_RES}; vanilla checks skipped")

    def err(self, msg):
        self.problems.append(msg)

    def bs_props(self, block_id):
        if block_id not in self._bs_cache:
            ns, name = block_id.split(":")
            base = VANILLA_BS if ns == "minecraft" else os.path.join(MOD_ASSETS, "blockstates")
            p = os.path.join(base, name + ".json")
            self._bs_cache[block_id] = _blockstate_props(p) if os.path.exists(p) else None
        return self._bs_cache[block_id]


def check_state(ctx, where, entry):
    bid = entry.get("id")
    props = {k: v for k, v in entry.get("properties", {}).items()}
    if not isinstance(bid, str) or ":" not in bid:
        ctx.err(f"{where}: bad block id {bid!r}")
        return
    if any(not isinstance(v, str) for v in props.values()):
        ctx.err(f"{where}: {bid} non-string property values")
    ns, name = bid.split(":")
    if ns == "minecraft":
        if ctx.vanilla_blocks is not None and name not in ctx.vanilla_blocks:
            ctx.err(f"{where}: unknown vanilla block {bid}")
    elif ns == "expanse":
        if bid not in BL.MOD_ALLOWED:
            ctx.err(f"{where}: mod block {bid} is not in the allowed list")
        if not os.path.exists(os.path.join(MOD_ASSETS, "blockstates", name + ".json")):
            ctx.err(f"{where}: mod block {bid} has no blockstate asset")
    else:
        ctx.err(f"{where}: foreign namespace {bid}")
        return
    try:
        sch = BL.schema(bid)
    except KeyError as e:
        ctx.err(f"{where}: {e}")
        return
    if set(props) != set(sch):
        ctx.err(f"{where}: {bid} properties {sorted(props)} != schema {sorted(sch)}")
    for k, v in props.items():
        if k in sch and v not in sch[k][0]:
            ctx.err(f"{where}: {bid} {k}={v} not in {sch[k][0]}")
    if ctx.corpus is not None:
        if bid in ctx.corpus and set(ctx.corpus[bid]) != set(props):
            ctx.err(f"{where}: {bid} properties {sorted(props)} != vanilla templates {sorted(ctx.corpus[bid])}")
        rep = FAMILY_REP.get(id(sch))
        if rep and rep in ctx.corpus and set(ctx.corpus[rep]) != set(props):
            ctx.err(f"{where}: {bid} properties differ from family analogue {rep}")
    bsp = ctx.bs_props(bid)
    if bsp is not None:
        multipart = bsp["__multipart__"]
        for k, vals in bsp.items():
            if k == "__multipart__":
                continue
            if k not in props:
                ctx.err(f"{where}: {bid} blockstate file uses property {k} missing from palette")
            elif not multipart and props[k] not in vals:
                ctx.err(f"{where}: {bid} {k}={props[k]} never used by its blockstate file {sorted(vals)}")


BE_IDS = {"chest": "minecraft:chest", "trapped_chest": "minecraft:trapped_chest", "barrel": "minecraft:barrel",
          "campfire": "minecraft:campfire", "soul_campfire": "minecraft:soul_campfire", "bell": "minecraft:bell",
          "decorated_pot": "minecraft:decorated_pot"}


def check_template(ctx, path):
    rel = os.path.relpath(path, ctx.data)
    try:
        _, r = nbt.load(path)
    except Exception as e:  # noqa: BLE001
        ctx.err(f"{rel}: unreadable ({e})")
        return None
    if list(r.keys()) != ["size", "entities", "blocks", "palette", "DataVersion"]:
        ctx.err(f"{rel}: root keys {list(r.keys())}")
    if not isinstance(r.get("DataVersion"), nbt.Int) or r["DataVersion"].v != DATA_VERSION:
        ctx.err(f"{rel}: DataVersion {r.get('DataVersion')!r}")
    size = r["size"]
    if not (isinstance(size, nbt.List) and len(size) == 3 and all(isinstance(v, nbt.Int) and v.v > 0 for v in size)):
        ctx.err(f"{rel}: bad size {size!r}")
        return None
    sx, sy, sz = (v.v for v in size)
    if sx > 48 or sz > 48 or sy > 48:
        ctx.err(f"{rel}: suspiciously large {sx}x{sy}x{sz}")
    pal = r["palette"]
    if not isinstance(pal, nbt.List) or not pal:
        ctx.err(f"{rel}: empty palette")
        return None
    for i, e in enumerate(pal):
        check_state(ctx, f"{rel} palette[{i}]", e)
    used = [0] * len(pal)
    seen = set()
    chests = []
    for bi, blk in enumerate(r["blocks"]):
        pos = blk.get("pos")
        st = blk.get("state")
        if not (isinstance(pos, nbt.List) and len(pos) == 3 and all(isinstance(v, nbt.Int) for v in pos)):
            ctx.err(f"{rel}: block {bi} bad pos")
            continue
        p = tuple(v.v for v in pos)
        if not (0 <= p[0] < sx and 0 <= p[1] < sy and 0 <= p[2] < sz):
            ctx.err(f"{rel}: block {bi} pos {p} outside size")
        if p in seen:
            ctx.err(f"{rel}: duplicate position {p}")
        seen.add(p)
        if not isinstance(st, nbt.Int) or not (0 <= st.v < len(pal)):
            ctx.err(f"{rel}: block {bi} palette index {st!r} out of range")
            continue
        used[st.v] += 1
        bid = pal[st.v]["id"]
        short = bid.split(":")[1]
        if "nbt" in blk:
            n = blk["nbt"]
            want = BE_IDS.get(short) or ("minecraft:banner" if short.endswith("_banner") else None)
            if n.get("id") != want:
                ctx.err(f"{rel}: {bid} at {p} has block entity id {n.get('id')!r}, expected {want!r}")
            if "LootTable" in n:
                lt = n["LootTable"]
                lns, lpath = lt.split(":")
                if lns != "expanse" or not os.path.exists(os.path.join(ctx.data, "loot_table", lpath + ".json")):
                    ctx.err(f"{rel}: loot table {lt} does not exist")
                if short == "chest":
                    chests.append(lt)
        elif short in ("chest", "barrel"):
            pass  # empty decorative container: the block creates its own block entity
    for i, u in enumerate(used):
        if not u:
            ctx.err(f"{rel}: palette entry {i} unused")
    if r["entities"] and not isinstance(r["entities"], nbt.List):
        ctx.err(f"{rel}: entities not a list")
    return chests


def load_json(ctx, path):
    try:
        with open(path) as f:
            return json.load(f)
    except Exception as e:  # noqa: BLE001
        ctx.err(f"{os.path.relpath(path, ctx.data)}: invalid JSON ({e})")
        return None


def res_path(ctx, rid, folder, ext):
    ns, p = rid.split(":")
    if ns != "expanse":
        return None
    return os.path.join(ctx.data, folder, p + ext)


def check_item(ctx, where, name):
    ns, n = name.split(":")
    if ns == "minecraft" and ctx.vanilla_items is not None and n not in ctx.vanilla_items:
        ctx.err(f"{where}: unknown item {name}")
    if ns == "expanse" and ctx.mod_items is not None and n not in ctx.mod_items:
        ctx.err(f"{where}: unknown mod item {name}")


def run(data):
    ctx = Ctx(data)
    # ---- templates
    chests_by_structure = {}
    for path in sorted(glob.glob(os.path.join(data, "structure", "**", "*.nbt"), recursive=True)):
        c = check_template(ctx, path)
        sname = os.path.relpath(path, os.path.join(data, "structure")).split(os.sep)[0]
        chests_by_structure.setdefault(sname, []).append(c or [])
    # ---- structures
    salts = {}
    for path in sorted(glob.glob(os.path.join(data, "worldgen", "structure", "*.json"))):
        name = os.path.basename(path)[:-5]
        rel = os.path.relpath(path, data)
        s = load_json(ctx, path)
        if s is None:
            continue
        need = {"type", "biomes", "max_distance_from_center", "size", "spawn_overrides", "start_height",
                "start_pool", "step", "terrain_adaptation", "use_expansion_hack"}
        missing = need - set(s)
        extra = set(s) - need - {"project_start_to_heightmap", "start_jigsaw_name", "pool_aliases",
                                 "dimension_padding", "liquid_settings"}
        if missing or extra:
            ctx.err(f"{rel}: missing {sorted(missing)} extra {sorted(extra)}")
        if s.get("type") != "minecraft:jigsaw":
            ctx.err(f"{rel}: type {s.get('type')}")
        if not (1 <= s.get("size", 0) <= 20):
            ctx.err(f"{rel}: size must be 1..20 (0 places nothing)")
        if s.get("step") != "surface_structures":
            ctx.err(f"{rel}: step {s.get('step')}")
        if s.get("terrain_adaptation") not in ("none", "beard_thin", "beard_box", "bury", "encapsulate"):
            ctx.err(f"{rel}: terrain_adaptation {s.get('terrain_adaptation')}")
        if s.get("project_start_to_heightmap") not in (None, "WORLD_SURFACE_WG", "OCEAN_FLOOR_WG"):
            ctx.err(f"{rel}: heightmap {s.get('project_start_to_heightmap')}")
        if s.get("max_distance_from_center", 999) + 12 > 128:
            ctx.err(f"{rel}: max_distance_from_center too large for terrain adaptation")
        if s.get("biomes") != f"#expanse:has_structure/{name}":
            ctx.err(f"{rel}: biomes {s.get('biomes')}")
        tag = os.path.join(data, "tags", "worldgen", "biome", "has_structure", name + ".json")
        t = load_json(ctx, tag) if os.path.exists(tag) else None
        if t is None:
            ctx.err(f"{rel}: biome tag missing")
        else:
            for b in t.get("values", []):
                if b not in MOD_BIOMES:
                    ctx.err(f"{os.path.relpath(tag, data)}: unknown biome {b}")
        pp = res_path(ctx, s.get("start_pool", "x:x"), "worldgen/template_pool", ".json")
        if not pp or not os.path.exists(pp):
            ctx.err(f"{rel}: start_pool {s.get('start_pool')} missing")
        else:
            pool = load_json(ctx, pp)
            if pool:
                if pool.get("fallback") != "minecraft:empty":
                    ctx.err(f"{os.path.relpath(pp, data)}: fallback {pool.get('fallback')}")
                for e in pool.get("elements", []):
                    el = e.get("element", {})
                    if el.get("element_type") != "minecraft:single_pool_element":
                        ctx.err(f"{os.path.relpath(pp, data)}: element_type {el.get('element_type')}")
                    if el.get("projection") != "rigid":
                        ctx.err(f"{os.path.relpath(pp, data)}: projection {el.get('projection')}")
                    loc = res_path(ctx, el.get("location", "x:x"), "structure", ".nbt")
                    if not loc or not os.path.exists(loc):
                        ctx.err(f"{os.path.relpath(pp, data)}: template {el.get('location')} missing")
                    proc = el.get("processors")
                    if isinstance(proc, str):
                        pl = res_path(ctx, proc, "worldgen/processor_list", ".json")
                        if not pl or not os.path.exists(pl):
                            ctx.err(f"{os.path.relpath(pp, data)}: processor list {proc} missing")
                    elif not (isinstance(proc, dict) and isinstance(proc.get("processors"), list)):
                        ctx.err(f"{os.path.relpath(pp, data)}: bad processors {proc!r}")
                    if not isinstance(e.get("weight"), int) or e["weight"] < 1:
                        ctx.err(f"{os.path.relpath(pp, data)}: bad weight")
        lists = chests_by_structure.get(name)
        if not lists:
            ctx.err(f"{rel}: no templates")
        else:
            for i, c in enumerate(lists):
                if f"expanse:chests/{name}" not in c:
                    ctx.err(f"structure/{name} template #{i}: no chest with loot table expanse:chests/{name}")
        ssp = os.path.join(data, "worldgen", "structure_set", name + ".json")
        ss = load_json(ctx, ssp) if os.path.exists(ssp) else None
        if ss is None:
            ctx.err(f"{rel}: structure set missing")
        else:
            pl = ss.get("placement", {})
            if pl.get("type") != "minecraft:random_spread" or not (0 <= pl.get("separation", -1) < pl.get("spacing", 0)
                                                                    <= 4096):
                ctx.err(f"{os.path.relpath(ssp, data)}: bad placement {pl}")
            salt = pl.get("salt")
            if salt in salts:
                ctx.err(f"{os.path.relpath(ssp, data)}: salt {salt} reused by {salts[salt]}")
            salts[salt] = name
            if [x.get("structure") for x in ss.get("structures", [])] != [f"expanse:{name}"]:
                ctx.err(f"{os.path.relpath(ssp, data)}: structures {ss.get('structures')}")
    # ---- processor lists
    for path in sorted(glob.glob(os.path.join(data, "worldgen", "processor_list", "*.json"))):
        pl = load_json(ctx, path)
        if not pl:
            continue
        for proc in pl.get("processors", []):
            if proc.get("processor_type") != "minecraft:rule":
                continue
            for rule in proc.get("rules", []):
                out = rule["output_state"]
                for b in (rule["input_predicate"].get("block"), out if isinstance(out, str) else out.get("id")):
                    ns, n = b.split(":")
                    ok = (b in BL.MOD_ALLOWED) if ns == "expanse" else (
                        ctx.vanilla_blocks is None or n in ctx.vanilla_blocks)
                    if not ok:
                        ctx.err(f"{os.path.relpath(path, data)}: unknown block {b}")
                    elif BL.schema(b):
                        ctx.err(f"{os.path.relpath(path, data)}: rule block {b} has properties; give a full state")
    # ---- loot tables
    for path in sorted(glob.glob(os.path.join(data, "loot_table", "chests", "*.json"))):
        lt = load_json(ctx, path)
        rel = os.path.relpath(path, data)
        if not lt:
            continue
        if lt.get("type") != "minecraft:chest" or lt.get("random_sequence") != "expanse:chests/" + os.path.basename(
                path)[:-5]:
            ctx.err(f"{rel}: type/random_sequence")
        for pool in lt.get("pools", []):
            if "rolls" not in pool or not pool.get("entries"):
                ctx.err(f"{rel}: pool without rolls/entries")
            for e in pool["entries"]:
                if e["type"] == "minecraft:item":
                    check_item(ctx, rel, e["name"])
                    mods = e.get("modifier", [])
                    for m in (mods if isinstance(mods, list) else [mods]):
                        if m.get("type") not in ("minecraft:set_count", "minecraft:set_damage",
                                                 "minecraft:enchant_randomly"):
                            ctx.err(f"{rel}: modifier {m.get('type')}")
                elif e["type"] != "minecraft:empty":
                    ctx.err(f"{rel}: entry type {e['type']}")
    # ---- every JSON parses
    for path in glob.glob(os.path.join(data, "**", "*.json"), recursive=True):
        load_json(ctx, path)
    return ctx.problems


if __name__ == "__main__":
    data = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(
        os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "expanse"))
    probs = run(data)
    for p in probs:
        print("-", p)
    print("OK" if not probs else f"{len(probs)} problem(s)")
    sys.exit(1 if probs else 0)
