"""Abandoned places: the traces of folk who left, for worlds where VOID Hearth peoples the land.

With Hearth installed the Expanse's peopled places stand down (Hearth builds living ones); these take their
place in the wild, rarer than the wild sights, and only with Hearth (SitedJigsawStructure "abandoned"; played
alone, the Expanse has the living places themselves). Each is a peopled structure's own pieces, run through a
ruin: beds, workstations, lights, glass and doors gone, the roofs fallen in more the higher they stood, tents
rotted away, moss on the boards; the burnt one charred and holed as well. Nobody is left: no beds, no working
blocks, no animals (pieces that carry any are copied without them).

Derived from the generated data of the structure it ruins, so gen_structures.py writes these after the base
structures (also runnable alone: python abandoned.py):
  structure/<name>/          copies of the base pieces that had animals in them (the rest are shared)
  worldgen/template_pool/<name>/<pool>.json   the base pools, with each piece's processors ruined
  worldgen/processor_list/abandoned/<name>/<list>.json   the base list followed by the ruin
  worldgen/structure/<name>.json   the base structure, starting from the ruined pools, its other pools aliased
  worldgen/structure_set/abandoned.json, tags/worldgen/biome/has_structure/<name>.json
"""
import copy
import json
import os

import nbt
from blocks import B
from processors import ALWAYS, STONE_WEATHER, _state_json, axes, rule

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "expanse"))

# name: the structure it ruins, how (ruin / burnt), and the heights over a piece's base where its roofs and upper
# floors stand (boards there fall in, more the higher they are)
ABANDONED = {
    "abandoned_mine_camp": dict(base="miners_camp", kind="ruin", roof=(3, 9)),
    "abandoned_mine_camp_mesa": dict(base="miners_camp_mesa", kind="ruin", roof=(3, 9)),
    "collapsed_hamlet": dict(base="stilt_hamlet", kind="ruin", roof=(9, 15), rotten_decks=True),
    "burnt_logging_camp": dict(base="logging_camp", kind="burnt", roof=(3, 10)),
}
# rarer than the wild sights (biome_sights: 12 chunks)
SET = dict(spacing=40, separation=12, salt=930417443)

WORKSTATIONS = ["crafting_table", "furnace", "smoker", "blast_furnace", "lectern", "composter", "stonecutter", "loom",
                "cartography_table", "fletching_table", "smithing_table", "grindstone", "brewing_stand", "cauldron",
                "water_cauldron", "lava_cauldron", "bell", "barrel"]
LIGHTS = ["lantern", "soul_lantern", "torch", "wall_torch", "candle", "white_candle", "sea_lantern", "glowstone"]
# what may stand in a piece that is not alive
STILL = {"minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting", "minecraft:armor_stand",
         "minecraft:minecart", "minecraft:chest_minecart", "minecraft:leash_knot", "minecraft:marker",
         "minecraft:block_display", "minecraft:item_display", "minecraft:text_display", "minecraft:interaction"}


def chance(p):
    """A position predicate passing with probability p wherever the block is."""
    return {"predicate_type": "minecraft:axis_aligned_linear_pos", "axis": "y", "min_chance": p, "max_chance": p,
            "min_dist": 0, "max_dist": 1}


def rising(lo, hi, top):
    """A position predicate passing never at `lo` blocks over the piece's base, rising to `top` at `hi`."""
    return {"predicate_type": "minecraft:axis_aligned_linear_pos", "axis": "y", "min_chance": 0.0, "max_chance": top,
            "min_dist": lo, "max_dist": hi}


def tag(t, out, pos=None):
    r = {"input_predicate": {"predicate_type": "minecraft:tag_match", "tag": t}, "location_predicate": ALWAYS,
         "output_state": _state_json(out)}
    if pos:
        r["position_predicate"] = pos
    return r


def ruin_rules(spec):
    lo, hi = spec["roof"]
    rs = []
    if spec["kind"] == "burnt":
        for log in ("spruce_log", "stripped_spruce_log", "expanse:redwood_log", "expanse:stripped_redwood_log",
                    "expanse:redwood_wood", "oak_log", "dark_oak_log"):
            rs += axes(log, "basalt", 0.4)                       # charred beams
        rs += [tag("minecraft:logs", "coal_block", chance(0.08)),
               tag("minecraft:planks", "air", chance(0.25)), tag("minecraft:planks", "blackstone", chance(0.12)),
               tag("minecraft:wooden_stairs", "air", chance(0.3)), tag("minecraft:wooden_slabs", "air", chance(0.3)),
               tag("minecraft:wooden_fences", "air", chance(0.5)), tag("minecraft:leaves", "air"),
               rule("hay_block", 1.0, "air"), rule("grass_block", 0.4, "coarse_dirt"), rule("podzol", 0.4, "coarse_dirt"),
               rule("dirt_path", 0.3, "coarse_dirt"), rule("short_grass", 0.6, "air"), rule("fern", 0.6, "air"),
               rule("moss_carpet", 1.0, "air")]
    rs += [tag("minecraft:beds", "air")]
    rs += [rule(w, 1.0, "air") for w in WORKSTATIONS]
    rs += [rule("anvil", 1.0, "damaged_anvil"), rule("campfire", 1.0, B("campfire", lit=False))]
    rs += [rule(l, 1.0, "air") for l in LIGHTS]
    rs += [rule("glass_pane", 1.0, "air"), rule("glass", 1.0, "air"), rule("flower_pot", 1.0, "air"),
           tag("minecraft:wooden_doors", "air", chance(0.85)),
           tag("minecraft:wool", "air", chance(0.8)), tag("minecraft:wool_carpets", "air", chance(0.6)),
           rule("light_gray_wool_stairs", 0.8, "air")]
    for t in ("minecraft:planks", "minecraft:wooden_slabs", "minecraft:wooden_stairs"):   # the roofs fall in
        rs.append(tag(t, "air", rising(lo, hi, 0.6)))
    rs += [tag("minecraft:wooden_trapdoors", "air", chance(0.35)), tag("minecraft:wooden_fences", "air", chance(0.25)),
           rule("rail", 0.4, "air"), rule("ladder", 0.3, "air")]
    if spec.get("rotten_decks"):
        rs.append(tag("minecraft:planks", "air", chance(0.07)))
    rs.append(tag("minecraft:planks", "moss_block", chance(0.08)))
    if spec["kind"] == "ruin" and not spec.get("rotten_decks"):     # moss creeping over the floors
        rs.append({"input_predicate": {"predicate_type": "minecraft:block_match", "block": "minecraft:air"},
                   "location_predicate": ALWAYS, "output_state": "minecraft:moss_carpet",
                   "position_predicate": {"predicate_type": "minecraft:axis_aligned_linear_pos", "axis": "y",
                                          "min_chance": 0.12, "max_chance": 0.0, "min_dist": 1, "max_dist": 2}})
    return [{"processor_type": "minecraft:rule", "rules": rs + STONE_WEATHER}]


def read(rel):
    with open(os.path.join(DATA, rel), encoding="utf-8") as f:
        return json.load(f)


def write(rel, obj, written):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(json.dumps(_order(obj), indent=2) + "\n")
    written.append(path)


def _order(o):
    if isinstance(o, dict):
        return {k: _order(o[k]) for k in sorted(o, key=lambda k: (k != "type", k))}
    if isinstance(o, list):
        return [_order(v) for v in o]
    return o


def _clear(rel, ext):
    d = os.path.join(DATA, rel)
    if os.path.isdir(d):
        for f in os.listdir(d):
            if f.endswith(ext):
                os.remove(os.path.join(d, f))


def unpeopled(base, piece, name, written):
    """The piece's location: the base's own, or a copy of it without its animals."""
    path = os.path.join(DATA, "structure", base, piece + ".nbt")
    root_name, root = nbt.load(path)
    ents = root.get("entities", [])
    keep = [e for e in ents if str(nbt.to_py(e)["nbt"]["id"]) in STILL or str(nbt.to_py(e)["nbt"]["id"]).endswith(("boat", "raft"))]
    if len(keep) == len(ents):
        return f"expanse:{base}/{piece}"
    root["entities"] = nbt.List(ents.elem_tag if keep else nbt.TAG_END, keep)
    out = os.path.join(DATA, "structure", name, piece + ".nbt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    nbt.dump(root, out, root_name)
    written.append(out)
    return f"expanse:{name}/{piece}"


def derive(name, spec, written):
    base = spec["base"]
    ruin = ruin_rules(spec)
    for rel, ext in ((f"structure/{name}", ".nbt"), (f"worldgen/template_pool/{name}", ".json"),
                     (f"worldgen/processor_list/abandoned/{name}", ".json")):
        _clear(rel, ext)
    lists = {}

    def ruined(procs):
        if isinstance(procs, str):
            key = procs.split(":")[-1].replace("/", "_")
            if key not in lists:
                parts = [] if procs == "minecraft:empty" else read(f"worldgen/processor_list/{procs.split(':')[-1]}.json")["processors"]
                lists[key] = {"processors": parts + ruin}
                write(f"worldgen/processor_list/abandoned/{name}/{key}.json", lists[key], written)
            return f"expanse:abandoned/{name}/{key}"
        parts = procs.get("processors", []) if isinstance(procs, dict) else list(procs)
        return {"processors": parts + ruin}

    aliases = []
    pdir = os.path.join(DATA, "worldgen", "template_pool", base)
    for f in sorted(os.listdir(pdir)):
        pname = f[:-5]
        pool = read(f"worldgen/template_pool/{base}/{f}")
        for e in pool["elements"]:
            el = e["element"]
            if "processors" in el:
                el["processors"] = ruined(el["processors"])
            if "location" in el and el["location"].startswith(f"expanse:{base}/"):
                el["location"] = unpeopled(base, el["location"].split("/", 1)[1], name, written)
        if pool.get("fallback", "").startswith(f"expanse:{base}/"):
            pool["fallback"] = f"expanse:{name}/" + pool["fallback"].split("/", 1)[1]
        write(f"worldgen/template_pool/{name}/{f}", pool, written)
        if pname != "start":
            aliases.append({"type": "minecraft:direct", "alias": f"expanse:{base}/{pname}", "target": f"expanse:{name}/{pname}"})

    s = read(f"worldgen/structure/{base}.json")
    s.pop("peopled", None)
    s["abandoned"] = True
    s["start_pool"] = f"expanse:{name}/start"
    s["biomes"] = f"#expanse:has_structure/{name}"
    if aliases:
        s["pool_aliases"] = aliases
    write(f"worldgen/structure/{name}.json", s, written)
    write(f"tags/worldgen/biome/has_structure/{name}.json", read(f"tags/worldgen/biome/has_structure/{base}.json"), written)


def write_all():
    written = []
    for name, spec in ABANDONED.items():
        derive(name, spec, written)
    write("worldgen/structure_set/abandoned.json", {
        "placement": {"type": "minecraft:random_spread", "salt": SET["salt"], "separation": SET["separation"],
                      "spacing": SET["spacing"]},
        "structures": [{"structure": f"expanse:{n}", "weight": 1} for n in ABANDONED]}, written)
    return written


if __name__ == "__main__":
    print(f"abandoned: wrote {len(write_all())} files")
