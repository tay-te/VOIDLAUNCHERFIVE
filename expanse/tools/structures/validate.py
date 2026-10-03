#!/usr/bin/env python3
"""Validate the generated structure data against vanilla 26.3 ground truth.

Templates are re-read with our own NBT reader and checked for: root layout,
DataVersion, size, palette indices, positions, block ids (vanilla blockstate
files / the mod's allowed list), full property sets (vs. the palettes of all
vanilla templates, the block-family analogue and the blockstate files), and
every block entity and entity we write (ids from BlockEntityTypes /
EntityTypeIds, loot tables, written books, sign text, spawner data, banner
patterns, painting variants, item ids, jigsaw pools and final states).

JSON checks: every file parses, structures / sets / pools / processor lists /
loot tables / tags mirror vanilla schemas, all references resolve, every
jigsaw can connect to every element of its pool, and an offline jigsaw
assembly (assemble.py) of each structure over many seeds places pieces.

Vanilla extracted resources are read from $MC_RES (default /root/mc/res),
decompiled code from $MC_SRC (default /root/mc/src).
"""
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True

import blocks as BL  # noqa: E402
import nbt  # noqa: E402

MC_RES = os.environ.get("MC_RES", "/root/mc/res")
MC_SRC = os.environ.get("MC_SRC", "/root/mc/src")
VDATA = os.path.join(MC_RES, "data", "minecraft")
VANILLA_BS = os.path.join(MC_RES, "assets", "minecraft", "blockstates")
VANILLA_ITEMS = os.path.join(MC_RES, "assets", "minecraft", "items")
VANILLA_TEMPLATES = os.path.join(VDATA, "structure")
MOD_ASSETS = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "expanse"))
DATA_VERSION = 5023
MOD_BIOMES = {"expanse:" + b for b in ("frostbloom_tundra heather_moor wisteria_vale redwood_giants lumen_grove "
                                        "willow_bayou amber_steppe opal_dunes jade_karst cloud_forest verdant_peaks "
                                        "prismatic_peaks palm_coast").split()}
DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
        "blue", "brown", "green", "red", "black"}

FAMILY_REP = {id(BL.STAIRS): "minecraft:oak_stairs", id(BL.SLAB): "minecraft:oak_slab",
              id(BL.WALL): "minecraft:cobblestone_wall", id(BL.CROSS): "minecraft:oak_fence",
              id(BL.FENCE_GATE): "minecraft:spruce_fence_gate", id(BL.DOOR): "minecraft:oak_door",
              id(BL.TRAPDOOR): "minecraft:oak_trapdoor", id(BL.AXIS): "minecraft:oak_log",
              id(BL.LEAVES): "minecraft:oak_leaves", id(BL.BED): "minecraft:red_bed",
              id(BL.CANDLE): "minecraft:candle", id(BL.LANTERN): "minecraft:lantern",
              id(BL.CAMPFIRE): "minecraft:campfire", id(BL.CHEST): "minecraft:chest",
              id(BL.FURNACE): "minecraft:furnace", id(BL.HALF2): "minecraft:tall_grass",
              id(BL.SNOWY): "minecraft:grass_block"}


def expected_be(short):
    """Block-entity type id for a block (net.minecraft...BlockEntityTypes), or None."""
    fixed = {"chest": "chest", "trapped_chest": "trapped_chest", "barrel": "barrel", "bell": "bell",
             "lectern": "lectern", "campfire": "campfire", "soul_campfire": "campfire", "spawner": "mob_spawner",
             "suspicious_sand": "brushable_block", "suspicious_gravel": "brushable_block",
             "decorated_pot": "decorated_pot", "chiseled_bookshelf": "chiseled_bookshelf", "jigsaw": "jigsaw",
             "furnace": "furnace", "smoker": "smoker", "blast_furnace": "blast_furnace"}
    fixed.update({"bee_nest": "beehive", "beehive": "beehive"})
    if short in fixed:
        return "minecraft:" + fixed[short]
    if short.endswith("_hanging_sign"):
        return "minecraft:hanging_sign"
    if short.endswith("_sign"):
        return "minecraft:sign"
    if short.endswith("_banner"):
        return "minecraft:banner"
    if short.endswith("_shelf") and short != "chiseled_bookshelf":
        return "minecraft:shelf"
    return None


def _ids_from_java(path, pattern=r'create\("([a-z0-9_]+)"\)'):
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return set(re.findall(pattern, f.read()))


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


def _names(d, ext=".json"):
    return {f[:-len(ext)] for f in os.listdir(d)} if os.path.isdir(d) else None


class Ctx:
    def __init__(self, data):
        self.data = data
        self.problems = []
        self.notes = []
        have = os.path.isdir(VANILLA_TEMPLATES)
        self.corpus = _vanilla_corpus() if have else None
        self.vanilla_blocks = _names(VANILLA_BS)
        self.vanilla_items = _names(VANILLA_ITEMS)
        self.mod_items = _names(os.path.join(MOD_ASSETS, "items"))
        self.entity_ids = _ids_from_java(os.path.join(MC_SRC, "net/minecraft/world/entity/EntityTypeIds.java"))
        self.paintings = _names(os.path.join(VDATA, "painting_variant"))
        self.banner_patterns = _names(os.path.join(VDATA, "banner_pattern"))
        self.placed_features = _names(os.path.join(VDATA, "worldgen", "placed_feature"))
        self._bs_cache = {}
        if not have:
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

    def item_ok(self, name):
        ns, n = name.split(":") if ":" in name else ("minecraft", name)
        if ns == "minecraft":
            return self.vanilla_items is None or n in self.vanilla_items
        return ns == "expanse" and (self.mod_items is None or n in self.mod_items)

    def res(self, rid, folder, ext=".json"):
        ns, p = rid.split(":")
        if ns == "minecraft":                 # vanilla's own (village pools and chests): its extracted data
            return os.path.join(VDATA, folder, p + ext)
        if ns != "expanse":
            return None
        return os.path.join(self.data, folder, p + ext)

    def loot_exists(self, rid, kind=None):
        p = self.res(rid, "loot_table")
        if not p or not os.path.exists(p):
            return False
        if kind:
            with open(p) as f:
                return json.load(f).get("type") == f"minecraft:{kind}"
        return True


# ------------------------------------------------------------------ block states
def check_state(ctx, where, entry):
    bid = entry.get("id")
    props = dict(entry.get("properties", {}))
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


def parse_state_string(s):
    m = re.match(r"^([a-z0-9_]+:[a-z0-9_/.]+)(?:\[(.*)\])?$", s)
    if not m:
        return None
    props = {}
    if m.group(2):
        for kv in m.group(2).split(","):
            k, _, v = kv.partition("=")
            props[k] = v
    return {"id": m.group(1), "properties": props}


# ------------------------------------------------------------------ items / text
def check_item_stack(ctx, where, st):
    if not isinstance(st, dict) or not st:
        ctx.err(f"{where}: empty item stack")
        return
    if not isinstance(st.get("id"), str) or not ctx.item_ok(st["id"]):
        ctx.err(f"{where}: unknown item {st.get('id')!r}")
    if not isinstance(st.get("count"), nbt.Int) or not (1 <= st["count"].v <= 99):
        ctx.err(f"{where}: bad count {st.get('count')!r}")
    comps = st.get("components", {})
    if "minecraft:written_book_content" in comps:
        c = comps["minecraft:written_book_content"]
        t = c.get("title")
        if not isinstance(t, str) or not (1 <= len(t) <= 32):
            ctx.err(f"{where}: book title must be a 1..32 char string")
        if not isinstance(c.get("author"), str):
            ctx.err(f"{where}: book author missing")
        pages = c.get("pages")
        if not isinstance(pages, nbt.List) or not pages or any(not isinstance(p, str) or len(p) > 1024 for p in pages):
            ctx.err(f"{where}: book pages must be a non-empty list of short strings")
        g = c.get("generation")
        if not isinstance(g, nbt.Int) or not 0 <= g.v <= 3:
            ctx.err(f"{where}: book generation")
        if st["id"] != "minecraft:written_book":
            ctx.err(f"{where}: written_book_content on {st['id']}")
    for k in comps:
        if k != "minecraft:written_book_content":
            ctx.err(f"{where}: unexpected component {k}")


def check_sign_text(ctx, where, t):
    msgs = t.get("messages") if isinstance(t, dict) else None
    if not isinstance(msgs, nbt.List) or len(msgs) != 4:
        ctx.err(f"{where}: sign text needs exactly 4 messages")
        return
    for m in msgs:
        if not (isinstance(m, str) or (isinstance(m, dict) and isinstance(m.get("text"), str))):
            ctx.err(f"{where}: bad sign message {m!r}")
        txt = m if isinstance(m, str) else m.get("text", "")
        if len(txt) > 15:
            ctx.notes.append(f"{where}: sign line may overflow: {txt!r}")
    if t.get("color") not in DYES:
        ctx.err(f"{where}: sign colour {t.get('color')}")


# ------------------------------------------------------------------ block entities
def check_block_entity(ctx, where, pal_entry, n, pools_seen):
    bid = pal_entry["id"]
    short = bid.split(":")[1]
    want = expected_be(short)
    if want is None:
        ctx.err(f"{where}: {bid} carries block-entity NBT but has no block entity")
        return
    if n.get("id") != want:
        ctx.err(f"{where}: {bid} block entity id {n.get('id')!r}, expected {want!r}")
    props = pal_entry.get("properties", {})
    if "LootTable" in n:
        kind = "archaeology" if want == "minecraft:brushable_block" else None
        if not ctx.loot_exists(n["LootTable"], kind):
            ctx.err(f"{where}: loot table {n['LootTable']} missing or of the wrong type")
    if want == "minecraft:brushable_block" and "LootTable" not in n:
        ctx.err(f"{where}: suspicious block without LootTable")
    if want == "minecraft:banner":
        for p in n.get("patterns", []):
            pat = p.get("pattern", "")
            if not pat.startswith("minecraft:") or (ctx.banner_patterns is not None and
                                                    pat.split(":")[1] not in ctx.banner_patterns):
                ctx.err(f"{where}: unknown banner pattern {pat}")
            if p.get("color") not in DYES:
                ctx.err(f"{where}: banner colour {p.get('color')}")
    if want == "minecraft:decorated_pot":
        for side in ("back", "front", "left", "right"):
            s = n.get("sherds", {}).get(side, {}).get("id", "")
            if not ctx.item_ok(s):
                ctx.err(f"{where}: unknown sherd {s}")
    if want == "minecraft:lectern":
        has = props.get("has_book") == "true"
        if has != ("Book" in n):
            ctx.err(f"{where}: lectern has_book={props.get('has_book')} but Book {'present' if 'Book' in n else 'absent'}")
        if "Book" in n:
            check_item_stack(ctx, where + " Book", n["Book"])
    if want in ("minecraft:sign", "minecraft:hanging_sign"):
        for side in ("front_text", "back_text"):
            check_sign_text(ctx, f"{where} {side}", n.get(side))
    if want == "minecraft:mob_spawner":
        e = n.get("SpawnData", {}).get("entity", {}).get("id", "")
        if ctx.entity_ids is not None and e.split(":")[-1] not in ctx.entity_ids:
            ctx.err(f"{where}: spawner entity {e}")
        for k in ("MaxNearbyEntities", "RequiredPlayerRange", "SpawnCount", "MaxSpawnDelay", "SpawnRange", "Delay",
                  "MinSpawnDelay"):
            if not isinstance(n.get(k), nbt.Short):
                ctx.err(f"{where}: spawner {k} must be a short")
        for sp in n.get("SpawnPotentials", []):
            if sp.get("data", {}).get("entity", {}).get("id") != e:
                ctx.err(f"{where}: spawn potential differs from SpawnData")
    if want == "minecraft:beehive":                    # BeehiveBlockEntity.Occupant: {entity_data{id}, ticks}
        bees = n.get("bees", [])
        if not isinstance(bees, nbt.List) or len(bees) > 3:
            ctx.err(f"{where}: bees must be a list of at most 3 (BeehiveBlockEntity.MAX_OCCUPANTS)")
        for bee in bees:
            e = bee.get("entity_data", {}).get("id", "")
            if ctx.entity_ids is not None and e.split(":")[-1] not in ctx.entity_ids:
                ctx.err(f"{where}: bee entity {e}")
            for k in ("ticks_in_hive", "min_ticks_in_hive"):
                if not isinstance(bee.get(k), nbt.Int):
                    ctx.err(f"{where}: bee {k} must be an int")
    if want == "minecraft:chiseled_bookshelf":
        occ = {i for i in range(6) if props.get(f"slot_{i}_occupied") == "true"}
        slots = {it["Slot"].v for it in n.get("Items", [])}
        if occ != slots:
            ctx.err(f"{where}: bookshelf occupied slots {sorted(occ)} != items {sorted(slots)}")
        for it in n.get("Items", []):
            check_item_stack(ctx, where, {k: v for k, v in it.items() if k != "Slot"})
    if want == "minecraft:shelf":
        for it in n.get("Items", []):
            if not 0 <= it["Slot"].v <= 2:
                ctx.err(f"{where}: shelf slot")
            check_item_stack(ctx, where, {k: v for k, v in it.items() if k != "Slot"})
    if want == "minecraft:jigsaw":
        for k in ("name", "target", "pool", "final_state", "joint"):
            if not isinstance(n.get(k), str):
                ctx.err(f"{where}: jigsaw {k} missing")
        if n.get("joint") not in ("rollable", "aligned"):
            ctx.err(f"{where}: jigsaw joint {n.get('joint')}")
        fs = parse_state_string(n.get("final_state", ""))
        if fs is None:
            ctx.err(f"{where}: jigsaw final_state {n.get('final_state')!r} does not parse")
        elif fs["id"] != "minecraft:structure_void":
            try:
                sch = BL.schema(fs["id"])
            except KeyError:
                sch = {}
            full = {k: fs["properties"].get(k, d) for k, (vals, d) in sch.items()}
            check_state(ctx, where + " final_state", {"id": fs["id"], "properties": full})
            for k in fs["properties"]:
                if k not in sch:
                    ctx.err(f"{where}: final_state property {k} unknown")
        pool = n.get("pool", "")
        if pool != "minecraft:empty":
            p = ctx.res(pool, "worldgen/template_pool")
            if not p or not os.path.exists(p):
                ctx.err(f"{where}: jigsaw pool {pool} missing")
            pools_seen.add(pool)


# ------------------------------------------------------------------ entities
def check_entity(ctx, where, e, size):
    n = e.get("nbt", {})
    eid = n.get("id", "")
    if ctx.entity_ids is not None and eid.split(":")[-1] not in ctx.entity_ids:
        ctx.err(f"{where}: unknown entity {eid}")
    bp = [v.v for v in e.get("blockPos", [])]
    pos = [v.v for v in e.get("pos", [])]
    if len(bp) != 3 or len(pos) != 3:
        ctx.err(f"{where}: entity needs blockPos and pos")
        return
    if not all(0 <= bp[i] < size[i] for i in range(3)) or not all(0 <= pos[i] <= size[i] for i in range(3)):
        ctx.err(f"{where}: entity outside template {bp} {pos}")
    short = eid.split(":")[-1]
    if short in ("painting", "item_frame", "glow_item_frame", "cushion"):
        b = list(n.get("block_pos", []))
        if b != bp:
            ctx.err(f"{where}: {short} block_pos {b} != blockPos {bp}")
        if [int(v // 1) for v in pos] != bp:
            ctx.err(f"{where}: {short} pos {pos} must lie inside its block {bp} (BlockAttachedEntity.setPos)")
    if short == "painting":
        v = n.get("variant", "")
        if not v.startswith("minecraft:") or (ctx.paintings is not None and v.split(":")[1] not in ctx.paintings):
            ctx.err(f"{where}: unknown painting {v}")
        if not isinstance(n.get("facing"), nbt.Byte) or not 0 <= n["facing"].v <= 3:
            ctx.err(f"{where}: painting facing")
    if short in ("item_frame", "glow_item_frame"):
        if not isinstance(n.get("Facing"), nbt.Byte) or not 0 <= n["Facing"].v <= 5:
            ctx.err(f"{where}: frame Facing")
        if "Item" in n:
            check_item_stack(ctx, where, n["Item"])
    if short == "armor_stand":
        for slot, st in n.get("equipment", {}).items():
            if slot not in ("head", "chest", "legs", "feet", "mainhand", "offhand"):
                ctx.err(f"{where}: equipment slot {slot}")
            check_item_stack(ctx, where, st)
    if short == "cushion" and n.get("color") not in DYES:
        ctx.err(f"{where}: cushion colour")
    if short in ("villager", "zombie_villager"):     # VillagerData: a VillagerType, a VillagerProfession, level
        vd = n.get("VillagerData", {})
        if vd.get("type") not in {f"minecraft:{t}" for t in ("desert", "jungle", "plains", "savanna", "snow",
                                                               "swamp", "taiga")}:
            ctx.err(f"{where}: villager type {vd.get('type')}")
        if vd.get("profession") not in {f"minecraft:{p}" for p in (
                "none armorer butcher cartographer cleric farmer fisherman fletcher leatherworker librarian mason "
                "nitwit shepherd toolsmith weaponsmith").split()}:
            ctx.err(f"{where}: villager profession {vd.get('profession')}")
        if not isinstance(vd.get("level"), nbt.Int) or not 1 <= vd["level"].v <= 5:
            ctx.err(f"{where}: villager level")
    if "UUID" in n and not isinstance(n["UUID"], nbt.IntArray):
        ctx.err(f"{where}: UUID must be an int array")


# ------------------------------------------------------------------ templates
def check_template(ctx, path, pools_seen):
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
    if sx > 64 or sz > 64 or sy > 64:
        ctx.err(f"{rel}: suspiciously large {sx}x{sy}x{sz}")
    elif sx > 48 or sz > 48 or sy > 48:
        # StructureTemplate.load has no size cap (48 is only the structure-block editing limit,
        # StructureBlockEntity.MAX_SIZE_PER_AXIS); landmark pieces may exceed it
        ctx.notes.append(f"{rel}: {sx}x{sy}x{sz} exceeds the 48-block structure-block editing limit")
    pal = r["palette"]
    for i, e in enumerate(pal):
        check_state(ctx, f"{rel} palette[{i}]", e)
    used = [0] * len(pal)
    seen = set()
    info = {"chests": [], "jigsaws": [], "size": (sx, sy, sz), "cells": {}}
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
        entry = pal[st.v]
        info["cells"][p] = (entry, blk.get("nbt"))
        short = entry["id"].split(":")[1]
        if "nbt" in blk:
            check_block_entity(ctx, f"{rel} {short}@{p}", entry, blk["nbt"], pools_seen)
            if short in ("chest", "trapped_chest", "barrel") and "LootTable" in blk["nbt"]:
                info["chests"].append(blk["nbt"]["LootTable"])
            if short == "jigsaw":
                info["jigsaws"].append((p, entry["properties"]["orientation"], blk["nbt"]))
        elif expected_be(short) in ("minecraft:lectern", "minecraft:mob_spawner", "minecraft:brushable_block",
                                    "minecraft:jigsaw", "minecraft:chiseled_bookshelf", "minecraft:sign",
                                    "minecraft:hanging_sign", "minecraft:banner", "minecraft:shelf"):
            if short != "lectern" and not (short.endswith("_shelf") or short == "chiseled_bookshelf"):
                ctx.err(f"{rel}: {short}@{p} needs block-entity NBT")
            if short == "chiseled_bookshelf" and any(entry.get("properties", {}).get(f"slot_{i}_occupied") == "true"
                                                     for i in range(6)):
                ctx.err(f"{rel}: {short}@{p} shows books but has no Items")
    for i, u in enumerate(used):
        if not u:
            ctx.err(f"{rel}: palette entry {i} unused")
    # ChestBlock.isChestBlockedAt: a chest cannot be opened under a redstone-conducting (full) block
    from builder import is_full
    from blocks import BlockState
    for p, (entry, _) in info["cells"].items():
        if entry["id"] not in ("minecraft:chest", "minecraft:trapped_chest"):
            continue
        above = info["cells"].get((p[0], p[1] + 1, p[2]))
        if above is None:
            continue
        aid = above[0]["id"]
        try:
            full = is_full(BlockState(aid, **above[0].get("properties", {})))
        except Exception:  # noqa: BLE001 - unknown states are reported by check_state
            full = False
        if full and not aid.endswith(("_leaves", "glass")) and aid.split(":")[1] not in (
                "glass", "ice", "packed_ice", "blue_ice", "glowstone", "sea_lantern", "beacon", "tinted_glass"):
            ctx.notes.append(f"{rel}: chest@{p} cannot be opened under {aid} until it is dug out "
                             f"(fine for a buried cache, a bug anywhere else)")
    ents = r["entities"]
    if not isinstance(ents, nbt.List):
        ctx.err(f"{rel}: entities not a list")
    for k, e in enumerate(ents):
        check_entity(ctx, f"{rel} entity[{k}]", e, (sx, sy, sz))
    return info


# ------------------------------------------------------------------ JSON helpers
def load_json(ctx, path):
    try:
        with open(path) as f:
            return json.load(f)
    except Exception as e:  # noqa: BLE001
        ctx.err(f"{os.path.relpath(path, ctx.data)}: invalid JSON ({e})")
        return None


def check_processor_list(ctx, path):
    pl = load_json(ctx, path)
    rel = os.path.relpath(path, ctx.data)
    if not pl:
        return
    known = {"minecraft:rule", "minecraft:block_rot", "minecraft:capped", "minecraft:protected_blocks"}

    def block_ok(b):
        ns, n = b.split(":")
        return (b in BL.MOD_ALLOWED) if ns == "expanse" else (ctx.vanilla_blocks is None or n in ctx.vanilla_blocks)

    def state_ok(where, s):
        if isinstance(s, str):
            if not block_ok(s):
                ctx.err(f"{where}: unknown block {s}")
            elif BL.schema(s):
                ctx.err(f"{where}: {s} has properties; give a full state object")
        else:
            check_state(ctx, where, s)

    def proc(p, where):
        t = p.get("processor_type")
        if t not in known:
            ctx.err(f"{where}: processor type {t}")
        if t == "minecraft:capped":
            if not isinstance(p.get("limit"), int) or p["limit"] < 1:
                ctx.err(f"{where}: capped limit")
            proc(p["delegate"], where + " delegate")
        if t == "minecraft:block_rot":
            if not 0 <= p.get("integrity", -1) <= 1:
                ctx.err(f"{where}: integrity")
            for b in p.get("rottable_blocks", []):
                if not block_ok(b):
                    ctx.err(f"{where}: rottable block {b}")
        for r in p.get("rules", []):
            ip = r["input_predicate"]
            pt = ip.get("predicate_type")
            if pt not in ("minecraft:random_block_match", "minecraft:block_match", "minecraft:random_blockstate_match",
                          "minecraft:blockstate_match", "minecraft:tag_match", "minecraft:always_true"):
                ctx.err(f"{where}: predicate {pt}")
            if "block" in ip and not block_ok(ip["block"]):
                ctx.err(f"{where}: input block {ip['block']}")
            if "block_state" in ip:
                state_ok(where + " input", ip["block_state"])
            if "probability" in ip and not 0 < ip["probability"] <= 1:
                ctx.err(f"{where}: probability")
            lp = r.get("location_predicate", {})
            if lp.get("predicate_type") == "minecraft:block_match":   # e.g. a street's path over water
                if not block_ok(lp.get("block", "x:x")):
                    ctx.err(f"{where}: location block {lp.get('block')}")
            elif lp.get("predicate_type") != "minecraft:always_true":
                ctx.err(f"{where}: location predicate")
            state_ok(where + " output", r["output_state"])
            m = r.get("block_entity_modifier")
            if m:
                if m.get("type") != "minecraft:append_loot" or not ctx.loot_exists(m.get("loot_table", "x:x"),
                                                                                   "archaeology"):
                    ctx.err(f"{where}: block_entity_modifier {m}")

    for i, p in enumerate(pl.get("processors", [])):
        proc(p, f"{rel}[{i}]")


def check_loot(ctx, path):
    lt = load_json(ctx, path)
    rel = os.path.relpath(path, ctx.data)
    if not lt:
        return
    name = os.path.relpath(path, os.path.join(ctx.data, "loot_table"))[:-5]
    if lt.get("type") not in ("minecraft:chest", "minecraft:archaeology"):
        ctx.err(f"{rel}: type {lt.get('type')}")
    if lt.get("random_sequence") != f"expanse:{name}":
        ctx.err(f"{rel}: random_sequence {lt.get('random_sequence')}")
    for pool in lt.get("pools", []):
        if "rolls" not in pool or not pool.get("entries"):
            ctx.err(f"{rel}: pool without rolls/entries")
        for e in pool["entries"]:
            if e["type"] == "minecraft:item":
                if not ctx.item_ok(e["name"]):
                    ctx.err(f"{rel}: unknown item {e['name']}")
                mods = e.get("modifier", [])
                for m in (mods if isinstance(mods, list) else [mods]):
                    t = m.get("type")
                    if t == "minecraft:exploration_map":
                        dest = m.get("destination", "")
                        tp = ctx.res(dest.lstrip("#"), "tags/worldgen/structure")
                        if not dest.startswith("#") or not tp or not os.path.exists(tp):
                            ctx.err(f"{rel}: exploration map destination {dest}")
                        if e["name"] != "minecraft:map":
                            ctx.err(f"{rel}: exploration map on {e['name']}")
                    elif t == "minecraft:set_written_book_pages":
                        if m.get("mode") != "replace_all" or not m.get("pages") or any(
                                not isinstance(p, str) for p in m["pages"]):
                            ctx.err(f"{rel}: set_written_book_pages")
                        if e["name"] != "minecraft:written_book":
                            ctx.err(f"{rel}: pages on {e['name']}")
                    elif t == "minecraft:set_book_cover":
                        if len(m.get("title", "")) > 32 or not m.get("author"):
                            ctx.err(f"{rel}: set_book_cover")
                    elif t == "minecraft:enchant_randomly":
                        if m.get("options") != "#minecraft:on_random_loot":
                            ctx.err(f"{rel}: enchant options")
                    elif t not in ("minecraft:set_count", "minecraft:set_damage", "minecraft:filtered"):
                        ctx.err(f"{rel}: modifier {t}")
            elif e["type"] != "minecraft:empty":
                ctx.err(f"{rel}: entry type {e['type']}")


# ------------------------------------------------------------------ assembly from files
class _FileBuild:
    """Just enough of builder.Build for assemble.py, read back from a template file."""

    def __init__(self, name, info):
        from blocks import BlockState
        self.name = name
        self.size = info["size"]
        self.entities = []
        self.cells = {}
        for p, (entry, n) in info["cells"].items():
            sch = BL.schema(entry["id"])
            props = dict(entry.get("properties", {}))
            self.cells[p] = (BlockState(entry["id"], **props) if sch else BlockState(entry["id"]), n)


def run(data):
    ctx = Ctx(data)
    pools_seen = set()
    infos = {}
    for path in sorted(glob.glob(os.path.join(data, "structure", "**", "*.nbt"), recursive=True)):
        loc = "expanse:" + os.path.relpath(path, os.path.join(data, "structure"))[:-4].replace(os.sep, "/")
        infos[loc] = check_template(ctx, path, pools_seen)

    # ---- pools
    pools = {}
    fallbacks = {}
    for path in sorted(glob.glob(os.path.join(data, "worldgen", "template_pool", "**", "*.json"), recursive=True)):
        pid = "expanse:" + os.path.relpath(path, os.path.join(data, "worldgen", "template_pool"))[:-5].replace(
            os.sep, "/")
        rel = os.path.relpath(path, data)
        pool = load_json(ctx, path)
        if not pool:
            continue
        if pool.get("fallback") != "minecraft:empty":   # a fallback pool (villages: streets -> terminators)
            fb = ctx.res(pool.get("fallback", "x:x"), "worldgen/template_pool")
            if not fb or not os.path.exists(fb):
                ctx.err(f"{rel}: fallback {pool.get('fallback')}")
            fallbacks[pid] = pool.get("fallback")
        elems = []
        for e in pool.get("elements", []):
            el = e.get("element", {})
            t = el.get("element_type")
            w = e.get("weight")
            if not isinstance(w, int) or w < 1:
                ctx.err(f"{rel}: bad weight")
            if t == "minecraft:empty_pool_element":
                elems.append((None, w))
            elif t == "minecraft:feature_pool_element":
                f = el.get("feature", "")
                if not f.startswith("minecraft:") or (ctx.placed_features is not None and
                                                     f.split(":")[1] not in ctx.placed_features):
                    ctx.err(f"{rel}: unknown placed feature {f}")
                elems.append((("feature", f), w))
            elif t == "minecraft:single_pool_element":
                if el.get("projection") not in ("rigid", "terrain_matching"):
                    ctx.err(f"{rel}: projection {el.get('projection')}")
                loc = el.get("location", "")
                if loc not in infos or infos[loc] is None:
                    ctx.err(f"{rel}: template {loc} missing")
                else:
                    elems.append((_FileBuild(loc.split("/")[-1], infos[loc]), w))
                proc = el.get("processors")
                if isinstance(proc, str):
                    pl = ctx.res(proc, "worldgen/processor_list")
                    if not pl or not os.path.exists(pl):
                        ctx.err(f"{rel}: processor list {proc} missing")
                elif not (isinstance(proc, dict) and isinstance(proc.get("processors"), list)):
                    ctx.err(f"{rel}: bad processors {proc!r}")
            else:
                ctx.err(f"{rel}: element_type {t}")
        pools[pid] = elems

    # ---- jigsaw compatibility: every element of a referenced pool must accept the source jigsaw
    from builder import OPP
    for loc, info in infos.items():
        if not info:
            continue
        for p, orient, n in info["jigsaws"]:
            pool = n["pool"]
            if pool == "minecraft:empty" or pool not in pools:
                continue
            front = orient.split("_")[0]
            for el, w in pools[pool]:
                if el is None:
                    continue
                if isinstance(el, tuple):
                    if front != "up":
                        ctx.err(f"{loc} jigsaw@{p}: feature element in {pool} needs an upward jigsaw")
                    continue
                tinfo = infos.get(f"{loc.rsplit('/', 1)[0]}/{el.name}") or next(
                    (i for k, i in infos.items() if k.endswith('/' + el.name)), None)
                ok = False
                for tp, torient, tn in (tinfo or {}).get("jigsaws", []):
                    tfront = torient.split("_")[0]
                    if tn["name"] != n["target"]:
                        continue
                    if front in ("up", "down"):
                        ok = ok or tfront == OPP[front]
                    else:
                        ok = ok or tfront in ("north", "south", "east", "west")
                if not ok:
                    ctx.err(f"{loc} jigsaw@{p} (target {n['target']}) cannot attach to {el.name} in {pool}")

    # ---- structures
    vtag = os.path.join(data, "..", "minecraft", "tags", "worldgen", "structure", "village.json")
    villages = set((load_json(ctx, vtag) or {}).get("values", [])) if os.path.exists(vtag) else set()
    for v in sorted(villages):                     # our additions to vanilla's #minecraft:village
        if not os.path.exists(ctx.res(v, "worldgen/structure") or ""):
            ctx.err(f"tags/worldgen/structure/village.json (minecraft): unknown structure {v}")
    salts = {}
    shared = {}     # structure id -> shared set file (sets listing structures other than their namesake)
    for sp_ in sorted(glob.glob(os.path.join(data, "worldgen", "structure_set", "*.json"))):
        ids_ = [x.get("structure") for x in (load_json(ctx, sp_) or {}).get("structures", [])]
        if ids_ != ["expanse:" + os.path.basename(sp_)[:-5]]:
            for sid in ids_:
                shared[sid] = sp_
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
        if s.get("type") == "expanse:sited_jigsaw":
            extra -= {"max_relief", "allow_water", "avoid", "in_cavern"}
            for z in s.get("avoid", []):
                if not 1 <= z.get("radius", 0) <= 256 or not os.path.exists(
                        ctx.res(z.get("other_set", "x:x"), "worldgen/structure_set") or ""):
                    ctx.err(f"{rel}: bad avoid entry {z}")
        if missing or extra:
            ctx.err(f"{rel}: missing {sorted(missing)} extra {sorted(extra)}")
        if s.get("type") not in ("minecraft:jigsaw", "expanse:sited_jigsaw"):
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
                if b not in MOD_BIOMES and b != "#minecraft:is_overworld":
                    ctx.err(f"{os.path.relpath(tag, data)}: unknown biome {b}")
        sp = s.get("start_pool", "x:x")
        if sp not in pools:
            ctx.err(f"{rel}: start_pool {sp} missing")
        # every structure must offer a public and a hidden loot chest somewhere in its templates
        tables = set()
        for loc, info in infos.items():
            if info and loc.startswith(f"expanse:{name}/"):
                tables.update(info["chests"])
        village = f"expanse:{name}" in villages            # villages keep vanilla's chests, none hidden
        for want in (f"expanse:chests/{name}",) + (() if village else (f"expanse:chests/{name}_hidden",)):
            if want not in tables:
                ctx.err(f"{rel}: no container uses {want}")
        # offline assembly over many seeds: the start must place and its pools must be reachable
        if sp in pools:
            import assemble
            placed = {}
            for seed in range(40):
                try:
                    ps, _ = assemble.assemble(pools, sp, s.get("size", 1), seed, s.get("max_distance_from_center", 80),
                                              **({"fallbacks": fallbacks, "expansion_hack": True}
                                                 if s.get("use_expansion_hack") else {}))
                except Exception as e:  # noqa: BLE001
                    ctx.err(f"{rel}: assembly failed ({e})")
                    break
                for pc in ps:
                    placed[pc.build.name] = placed.get(pc.build.name, 0) + 1
            for pid, elems in pools.items():
                if not pid.startswith(f"expanse:{name}/"):
                    continue
                for el, w in elems:
                    if el is not None and not isinstance(el, tuple) and el.name not in placed:
                        ctx.notes.append(f"{name}: piece {el.name} ({pid}) never placed in 40 seeds")
            ctx.notes.append(f"{name}: avg pieces {sum(placed.values()) / 40:.1f}")
        ssp = os.path.join(data, "worldgen", "structure_set", name + ".json")
        ss = load_json(ctx, ssp) if os.path.exists(ssp) else None
        if f"expanse:{name}" in shared:               # placed by a shared set (checked below)
            if ss is not None:
                ctx.err(f"{rel}: in shared set {os.path.basename(shared['expanse:' + name])} and its own set")
        elif ss is None:
            ctx.err(f"{rel}: structure set missing")
        else:
            pl = ss.get("placement", {})
            if pl.get("type") != "minecraft:random_spread" or not (0 <= pl.get("separation", -1) < pl.get("spacing", 0)
                                                                    <= 4096):
                ctx.err(f"{os.path.relpath(ssp, data)}: bad placement {pl}")
            ez = pl.get("exclusion_zone")
            if ez is not None and (not 1 <= ez.get("chunk_count", 0) <= 16 or not os.path.exists(
                    ctx.res(ez.get("other_set", "x:x"), "worldgen/structure_set") or "")):
                ctx.err(f"{os.path.relpath(ssp, data)}: bad exclusion_zone {ez}")
            salt = pl.get("salt")
            if salt in salts:
                ctx.err(f"{os.path.relpath(ssp, data)}: salt {salt} reused by {salts[salt]}")
            salts[salt] = name
            if [x.get("structure") for x in ss.get("structures", [])] != [f"expanse:{name}"]:
                ctx.err(f"{os.path.relpath(ssp, data)}: structures {ss.get('structures')}")
    for sp_ in sorted(set(shared.values())):          # shared sets (small structures)
        rel_ = os.path.relpath(sp_, data)
        ss_ = load_json(ctx, sp_) or {}
        pl = ss_.get("placement", {})
        if pl.get("type") == "expanse:cavern_halls":
            if not isinstance(pl.get("salt"), int) or not 0 < pl.get("frequency", 1) <= 1:
                ctx.err(f"{rel_}: bad placement {pl}")
        elif pl.get("type") != "minecraft:random_spread" or not (0 <= pl.get("separation", -1) < pl.get("spacing", 0)
                                                                  <= 4096):
            ctx.err(f"{rel_}: bad placement {pl}")
        if pl.get("salt") in salts:
            ctx.err(f"{rel_}: salt {pl.get('salt')} reused by {salts[pl.get('salt')]}")
        salts[pl.get("salt")] = rel_
        ez = pl.get("exclusion_zone")
        if ez is not None and (not 1 <= ez.get("chunk_count", 0) <= 16 or not os.path.exists(
                ctx.res(ez.get("other_set", "x:x"), "worldgen/structure_set") or "")):
            ctx.err(f"{rel_}: bad exclusion_zone {ez}")
        for x in ss_.get("structures", []):
            if not isinstance(x.get("weight"), int) or x["weight"] < 1:
                ctx.err(f"{rel_}: bad weight {x}")
            if not os.path.exists(ctx.res(x.get("structure", "x:x"), "worldgen/structure") or ""):
                ctx.err(f"{rel_}: unknown structure {x.get('structure')}")
    for pool in pools_seen:
        if pool not in pools and not (pool.startswith("minecraft:") and
                                      os.path.exists(ctx.res(pool, "worldgen/template_pool"))):
            ctx.err(f"jigsaw pool {pool} has no file")

    # ---- processor lists, loot, structure tags
    for path in sorted(glob.glob(os.path.join(data, "worldgen", "processor_list", "*.json"))):
        check_processor_list(ctx, path)
    for path in sorted(glob.glob(os.path.join(data, "loot_table", "chests", "**", "*.json"), recursive=True)):
        check_loot(ctx, path)
    for path in sorted(glob.glob(os.path.join(data, "tags", "worldgen", "structure", "*.json"))):
        t = load_json(ctx, path)
        for v in (t or {}).get("values", []):
            if not os.path.exists(ctx.res(v, "worldgen/structure") or ""):
                ctx.err(f"{os.path.relpath(path, data)}: unknown structure {v}")
    for path in glob.glob(os.path.join(data, "**", "*.json"), recursive=True):
        load_json(ctx, path)
    return ctx.problems, ctx.notes


if __name__ == "__main__":
    data = sys.argv[1] if len(sys.argv) > 1 else os.path.normpath(
        os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "expanse"))
    probs, notes = run(data)
    for n in notes:
        print("  note:", n)
    for p in probs:
        print("-", p)
    print("OK" if not probs else f"{len(probs)} problem(s)")
    sys.exit(1 if probs else 0)
