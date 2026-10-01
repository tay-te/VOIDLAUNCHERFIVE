"""Processor lists: per-instance weathering, overgrowth and archaeology.

Mirrors vanilla 26.3 processor lists (mossify_*, trail_ruins_*_archaeology,
ancient_city_*_degradation): `minecraft:rule` processors with
random_block_match / random_blockstate_match inputs, `minecraft:block_rot`
with an explicit rottable block list, and `minecraft:capped` delegates with
an `append_loot` block-entity modifier for suspicious blocks.

Rules are evaluated per block with a position-seeded random, so the same
template looks different everywhere it generates.
"""
from blocks import B, schema, ns

ALWAYS = {"predicate_type": "minecraft:always_true"}


def _state_json(st):
    st = B(st) if isinstance(st, str) else st
    return {"id": st.id, "properties": dict(st.props)} if st.props else st.id


def rule(block, prob, out):
    """Replace `block` (any state) with `out` with probability `prob`."""
    return {"input_predicate": {"block": ns(block), "predicate_type": "minecraft:random_block_match",
                                "probability": prob},
            "location_predicate": ALWAYS, "output_state": _state_json(out)}


def rule_state(st, prob, out):
    """Replace one exact state (e.g. a log with a given axis) keeping orientation by hand."""
    return {"input_predicate": {"block_state": _state_json(st), "predicate_type": "minecraft:random_blockstate_match",
                                "probability": prob},
            "location_predicate": ALWAYS, "output_state": _state_json(out)}


def rules(*rs):
    return {"processor_type": "minecraft:rule", "rules": list(rs)}


def rot(integrity, blocks):
    return {"integrity": integrity, "processor_type": "minecraft:block_rot",
            "rottable_blocks": [ns(b) for b in blocks]}


def archaeology(marker, out, loot, limit):
    """Up to `limit` marker blocks become suspicious blocks carrying `loot` (trail_ruins style)."""
    return {"delegate": {"processor_type": "minecraft:rule", "rules": [{
        "block_entity_modifier": {"type": "minecraft:append_loot", "loot_table": loot},
        "input_predicate": {"block": ns(marker), "predicate_type": "minecraft:block_match"},
        "location_predicate": ALWAYS, "output_state": _state_json(out)}]},
        "limit": limit, "processor_type": "minecraft:capped"}


def axes(src, dst, prob):
    """random_blockstate_match rules for every axis of a pillar block."""
    return [rule_state(B(src, axis=a), prob, B(dst, axis=a)) for a in ("x", "y", "z")]


# ---------------------------------------------------------------- fragments
STONE_WEATHER = [
    rule("stone_bricks", 0.14, "mossy_stone_bricks"), rule("stone_bricks", 0.08, "cracked_stone_bricks"),
    rule("stone_bricks", 0.01, "chiseled_stone_bricks"),
    rule("cobblestone", 0.22, "mossy_cobblestone"), rule("stone", 0.06, "andesite"),
    rule("expanse:limestone_bricks", 0.12, "expanse:mossy_limestone"),
    rule("expanse:limestone_bricks", 0.05, "expanse:limestone"),
    rule("expanse:polished_limestone", 0.05, "expanse:limestone"),
]
RUIN_ROT = rot(0.9, ["mossy_stone_bricks", "cracked_stone_bricks", "mossy_cobblestone", "expanse:mossy_limestone",
                     "cobblestone", "gravel"])
OVERGROWTH = [
    rule("short_grass", 0.2, "fern"), rule("short_grass", 0.25, "air"), rule("short_grass", 0.06, "poppy"),
    rule("short_grass", 0.05, "dandelion"), rule("short_grass", 0.04, "bush"),
    rule("vine", 0.35, "air"), rule("glow_lichen", 0.35, "air"), rule("moss_carpet", 0.25, "air"),
    rule("cobweb", 0.4, "air"),
]


def flowers(*opts):
    """short_grass markers become the given (block, probability) flowers."""
    return [rule("short_grass", p, f) for f, p in opts]


def aged_wood(*woods):
    out = []
    for w in woods:
        nsp = "expanse:" if w in ("redwood", "willow", "palm", "lumen", "baobab", "wisteria") else "minecraft:"
        out += axes(f"{nsp}{w}_log", f"{nsp}stripped_{w}_log", 0.12)
    return out


def plist(*parts):
    """Assemble a processor list from rule fragments (lists of rules) and whole processors (dicts)."""
    procs = []
    pending = []
    for p in parts:
        if isinstance(p, list):
            pending += p
        else:
            if pending:
                procs.append(rules(*pending))
                pending = []
            procs.append(p)
    if pending:
        procs.append(rules(*pending))
    return {"processors": procs}


HEATHER = flowers(("expanse:heather", 0.35), ("expanse:edelweiss", 0.06), ("fern", 0.12), ("air", 0.12))
TUNDRA = flowers(("expanse:frostbloom", 0.25), ("air", 0.35), ("fern", 0.1))
STEPPE = flowers(("short_dry_grass", 0.3), ("tall_dry_grass", 0.15), ("dandelion", 0.05), ("air", 0.2))
LUMEN = flowers(("expanse:glowcap", 0.3), ("fern", 0.2), ("air", 0.2))
COAST = flowers(("short_dry_grass", 0.2), ("fern", 0.1), ("air", 0.3))
CLOUD = flowers(("fern", 0.3), ("lily_of_the_valley", 0.1), ("azure_bluet", 0.08), ("air", 0.15))
FOREST = flowers(("fern", 0.3), ("expanse:heather", 0.04), ("poppy", 0.04), ("air", 0.15))
BAYOU = flowers(("fern", 0.3), ("blue_orchid", 0.08), ("air", 0.2))
KARST = flowers(("fern", 0.25), ("azure_bluet", 0.06), ("air", 0.2))
DUNES = flowers(("dead_bush", 0.2), ("short_dry_grass", 0.25), ("air", 0.35))

PROCESSOR_LISTS = {
    "stone_circle_ring": plist(STONE_WEATHER, HEATHER, OVERGROWTH,
                               archaeology("gravel", "suspicious_gravel", "expanse:chests/stone_circle_archaeology", 3)),
    "stone_circle_crypt": plist(STONE_WEATHER, [rule("cobweb", 0.3, "air"), rule("candle", 0.3, "air")]),
    "watchtower": plist(STONE_WEATHER, aged_wood("spruce"), HEATHER, OVERGROWTH, RUIN_ROT,
                        archaeology("gravel", "suspicious_gravel", "expanse:chests/ruined_watchtower_archaeology", 4)),
    "lodge": plist(aged_wood("redwood", "spruce", "dark_oak"), STONE_WEATHER, FOREST,
                   OVERGROWTH),
    "sun_shrine": plist([rule("expanse:smooth_opal_sandstone", 0.1, "expanse:opal_sandstone"),
                         rule("expanse:cut_opal_sandstone", 0.06, "expanse:opal_sandstone"),
                         rule("expanse:chiseled_opal_sandstone", 0.05, "expanse:cut_opal_sandstone"),
                         rule("orange_terracotta", 0.08, "expanse:smooth_opal_sandstone"),
                         rule("light_blue_terracotta", 0.08, "expanse:smooth_opal_sandstone")], DUNES,
                        # vanilla-sand patches left in the ruins/dunes are the archaeology candidates
                        archaeology("sand", "suspicious_sand", "expanse:chests/sun_shrine_archaeology", 4)),
    "stilt_hamlet": plist(aged_wood("willow", "spruce"), BAYOU, [rule("moss_carpet", 0.3, "air")]),
    "pagoda": plist(STONE_WEATHER, aged_wood("redwood", "dark_oak"), KARST, [rule("moss_carpet", 0.3, "air")]),
    "lighthouse": plist(STONE_WEATHER, aged_wood("palm", "spruce"), COAST),
    "lumen_shrine": plist(STONE_WEATHER, aged_wood("lumen"), LUMEN, OVERGROWTH, RUIN_ROT,
                          archaeology("gravel", "suspicious_gravel", "expanse:chests/lumen_shrine_archaeology", 2)),
    "frost_outpost": plist(STONE_WEATHER, aged_wood("spruce"), TUNDRA,
                           [rule("deepslate_bricks", 0.1, "cracked_deepslate_bricks"),
                            rule("deepslate_bricks", 0.05, "cobbled_deepslate"),
                            rule("polished_deepslate", 0.06, "deepslate_tiles"),
                            rule("snow", 0.25, "air"), rule("gravel", 0.15, "snow_block")]),
    "nomad_camp": plist(aged_wood("acacia", "baobab", "spruce"), STEPPE),
    "cloud_monastery": plist(STONE_WEATHER, aged_wood("spruce", "dark_oak"), CLOUD, OVERGROWTH),
}


def referenced_blocks(obj):
    """Every block id mentioned by a processor list (for validation)."""
    out = []
    for proc in obj["processors"]:
        stack = [proc]
        while stack:
            p = stack.pop()
            if "delegate" in p:
                stack.append(p["delegate"])
            for r in p.get("rules", []):
                ip = r["input_predicate"]
                if "block" in ip:
                    out.append(ip["block"])
                if "block_state" in ip:
                    out.append(ip["block_state"]["id"] if isinstance(ip["block_state"], dict) else ip["block_state"])
                o = r["output_state"]
                out.append(o["id"] if isinstance(o, dict) else o)
            out += p.get("rottable_blocks", [])
    return out
