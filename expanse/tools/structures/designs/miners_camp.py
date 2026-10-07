"""Prospectors' claims. One design, two palettes (separate structures):
  miners_camp       spruce timber, a snowy knoll of stone and calcite, copper and iron at the face
                    - prismatic peaks and the high stony, jagged and frozen peaks, snowy slopes
  miners_camp_mesa  dark oak timber, a knoll of banded terracotta and red sand, gold at the face
                    - the badlands, painted canyons, saguaro flats and the outback

start pool (3 variants)
  adit     : a timbered adit driven into a knoll, rails running out of it to the
             ore bin and spoil heap, ore glittering at the face, the claim sign,
             tent and sorting bench; a side gallery boarded up with a DANGER sign hides
             the strongbox
  sluice   : a plank sluice on trestles above gravel tailings, the prospector's pan and
             rocker, tent and fire; the tailings still hold a few things (archaeology)
             and the pay-dirt box is buried under the rocker
  collapse : an adit that fell in - fallen timbers, rubble spilling over the rails, a
             helmet on a stake, a warning sign; the crew's box is under the rubble
"""
import math

from blocks import AIR, B
from builder import Build, item, trapdoor
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import a_tent, book, clear, flora, ground_item, meal_fire, pick, trail

PALETTES = {
    "peaks": dict(
        name="miners_camp", procs=("small_mine", "small_mine_dig"), seed=0,
        biomes=["dolomite_peaks", "minecraft:stony_peaks", "minecraft:jagged_peaks", "minecraft:frozen_peaks",
                "minecraft:snowy_slopes", "minecraft:windswept_gravelly_hills"],
        rock=[("stone", 4), ("calcite", 3), ("andesite", 2), ("cobblestone", 1)], cap="snow_block",
        ground=["calcite", "snow_block", "snow_block", "dirt"],
        path=[("gravel", 4), ("coarse_dirt", 2), ("snow_block", 2)],
        spoil=[("gravel", 4), ("cobblestone", 2), ("calcite", 2), ("andesite", 1)],
        face=[("copper_ore", 2), ("iron_ore", 2), ("stone", 2), ("calcite", 1)],
        cluster=None, bed_rock="stone", wood="spruce", foods=["potato", "cod"],
        books=("miners_camp", "miners_camp_sluice", "miners_camp_collapse")),
    "mesa": dict(
        name="miners_camp_mesa", procs=("small_mesa", "small_mesa_dig"), seed=50,
        biomes=["minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "painted_canyons",
                "saguaro_flats", "ghost_gum_outback"],
        rock=[("terracotta", 4), ("orange_terracotta", 2), ("red_sandstone", 2), ("brown_terracotta", 1),
              ("white_terracotta", 1)], cap="red_sand",
        ground=["coarse_dirt", "coarse_dirt", "red_sand"],
        path=[("coarse_dirt", 3), ("red_sand", 2), ("gravel", 2)],
        spoil=[("gravel", 3), ("red_sand", 2), ("terracotta", 2), ("cobblestone", 1)],
        face=[("gold_ore", 2), ("terracotta", 2), ("red_sandstone", 1), ("iron_ore", 1)],
        cluster=None, bed_rock="terracotta", wood="dark_oak", foods=["potato", "beef"],
        books=("miners_camp_mesa", "miners_camp_sluice", "miners_camp_collapse")),
}


def knoll(b, rng, p, cx, cz, rx, rz, h):
    """A rocky knoll (ellipse rx x rz, height h) capped with snow or drifted sand."""
    for x in range(b.size[0]):
        for z in range(b.size[2]):
            d = math.hypot((x - cx) / rx, (z - cz) / rz)
            if d > 1:
                continue
            top = int(round(h * math.cos(d * math.pi / 2) + rng.random() * 0.7))
            for y in range(0, top + 1):
                b.set(x, y, z, pick(rng, p["rock"]) if y < top else
                      B(p["cap"]) if rng.random() < 0.6 else pick(rng, p["rock"]))


def adit(b, rng, p, x0, z_face, z_mouth, frames=True):
    """Tunnel x0..x0+2, y 1..3 from the face to the mouth, timber sets every other block."""
    w = p["wood"]
    for z in range(z_face, z_mouth + 1):
        for x in range(x0, x0 + 3):
            b.set(x, 0, z, B("gravel") if x != x0 + 1 else B(p["bed_rock"]))
            for y in (1, 2, 3):
                b.set(x, y, z, AIR)
        if frames and (z - z_face) % 2 == 1:
            for x in (x0 - 1, x0 + 3):
                for y in (1, 2, 3):
                    b.set(x, y, z, B(f"{w}_log", axis="y"))
            for x in range(x0 - 1, x0 + 4):
                b.set(x, 4, z, B(f"stripped_{w}_log", axis="x"))


def rails(b, x, z0, z1, y=1):
    for z in range(z0, z1 + 1):
        b.set(x, y, z, B("rail", shape="north_south"))
        if b.get(x, y - 1, z) is None or b.get(x, y - 1, z).short == "air":
            b.set(x, y - 1, z, B("gravel"))


def claim_sign(b, p, x, z, text, facing_rot=0):
    b.set(x, 0, z, B("cobblestone"))
    b.set(x, 1, z, B(f"{p['wood']}_fence"))
    b.sign(x, 2, z, p["wood"], text, rotation=facing_rot)


def adit_camp(p):
    name, w = p["name"], p["wood"]
    b = Build(f"{name}_adit", 16, 10, 16, seed=1907501 + p["seed"])
    rng = b.rng
    knoll(b, rng, p, 7.5, 3, 7.5, 5.5, 7)
    adit(b, rng, p, 6, 2, 8)
    # the face: ore, a pick on the wall, a lantern
    for x in (6, 7, 8):
        for y in (1, 2, 3):
            b.set(x, y, 1, pick(rng, p["face"]))
    if p["cluster"]:
        b.set(6, 1, 2, B(p["cluster"], facing="up"))
        b.set(8, 3, 2, B(p["cluster"], facing="down"))
    b.set(8, 1, 2, B("barrel", facing="up"))
    b.item_frame(6, 2, 2, "south", item("iron_pickaxe"), rotation=1)
    b.set(8, 3, 3, B("lantern", hanging=True))
    # a side gallery boarded up: DANGER sign, the strongbox behind
    for z in (4,):
        for y in (1, 2):
            b.set(5, y, z, B(f"{w}_planks") if y == 1 else B(f"{w}_fence"))
        b.air(3, 1, z, 4, 2, z)
        b.chest(3, 1, z, "east", f"expanse:chests/{name}_hidden")
    b.sign(6, 2, 4, w, ["", "DANGER", "bad air", ""], wall_facing="east")
    # rails out of the mouth to the ore bin
    rails(b, 7, 2, 12)
    b.barrel(7, 1, 13, "north", f"expanse:chests/{name}")
    b.set(7, 0, 13, B("cobblestone"))
    # spoil heap to the east, tent to the west, sorting bench, claim sign, fire
    for (x, z) in [(x, z) for x in range(10, 16) for z in range(9, 15)]:
        d = math.hypot(x - 12.5, z - 11.5)
        if d < 3:
            top = 2 if d < 1.4 else 1
            for y in range(0, top + 1):
                b.set(x, y, z, pick(rng, p["spoil"]))
    a_tent(b, 0, 10, 4, "z", rng.choice(["white", "light_gray"]), wide=True)
    b.bed(2, 1, 12, "north", "brown")
    b.set(1, 1, 11, B("lantern"))
    b.set(5, 1, 10, B("crafting_table"))
    b.set(5, 1, 11, B("stonecutter", facing="east"))
    b.set(5, 1, 12, B("grindstone", face="floor", facing="east"))
    ground_item(b, 5, 2, 10, book(BOOKS[p["books"][0]]), rotation=1)
    claim_sign(b, p, 10, 8, ["", "CLAIM No 7", "KEEP OUT", ""], 0)
    meal_fire(b, 8, 1, 14, p["foods"], lit=True, done=(100, 0, 0, 0))
    for (x, z) in ((9, 14), (7, 14), (8, 15), (8, 13)):
        b.set(x, 0, z, pick(rng, p["rock"]))
    clear(b, 3, 1, 9, 9, 3, 15)
    trail(b, [(7, 14), (6, 15)], rng, p["path"], width=1, fade=0.5)
    flora(b, [(x, z) for x in range(16) for z in range(9, 16)], rng, 0.15, p["ground"])
    return b


def sluice(p):
    name, w = p["name"], p["wood"]
    b = Build(f"{name}_sluice", 16, 8, 12, seed=1907503 + p["seed"])
    rng = b.rng
    # the sluice: a plank box on trestles, stepping down west to east, water in it
    for i, x in enumerate(range(2, 13)):
        y = 3 if x < 6 else 2 if x < 10 else 1
        b.set(x, y, 5, B(f"{w}_planks"))
        b.set(x, y + 1, 5, B("water", level=0))
        b.set(x, y + 1, 4, trapdoor(w, "north", "bottom", True))
        b.set(x, y + 1, 6, trapdoor(w, "south", "bottom", True))
        if x % 3 == 2:
            for yy in range(1, y):
                b.set(x, yy, 4, B(f"{w}_fence"))
                b.set(x, yy, 6, B(f"{w}_fence"))
            b.set(x, 0, 4, B("cobblestone"))
            b.set(x, 0, 6, B("cobblestone"))
    b.set(1, 4, 5, B(f"{w}_planks"))                   # the head box
    b.set(1, 3, 5, B(f"{w}_planks"))
    for y in (1, 2):
        b.set(1, y, 5, B(f"{w}_log", axis="y"))
    # tailings fanning out below the sluice (gravel: brush it)
    for x in range(10, 16):
        for z in range(2, 10):
            d = math.hypot(x - 13, z - 5.5)
            if d < 3.4:
                b.set(x, 0, z, B("gravel"))
                if d < 1.8:
                    b.set(x, 1, z, B("gravel"))
    # the rocker and the pan, the pay-dirt box under the rocker
    b.set(6, 1, 9, B("composter", level=3))
    b.chest(6, 0, 9, "north", f"expanse:chests/{name}_hidden")
    ground_item(b, 8, 1, 8, item("bowl"), rotation=1)
    ground_item(b, 8, 1, 9, item("gold_nugget"), rotation=3)
    # tent, fire, the claim sign
    a_tent(b, 1, 8, 3, "x", "light_gray")
    b.bed(2, 1, 9, "west", "brown")
    meal_fire(b, 5, 1, 11, p["foods"][:1], lit=False, done=(300, 0, 0, 0))
    b.barrel(4, 1, 8, "up", f"expanse:chests/{name}")
    ground_item(b, 4, 2, 8, book(BOOKS[p["books"][1]]), rotation=0)
    claim_sign(b, p, 14, 10, ["", "CLAIM", "T. Brack", ""], 8)
    clear(b, 2, 1, 7, 9, 2, 11)
    flora(b, [(x, z) for x in range(16) for z in range(12)], rng, 0.12, p["ground"])
    return b


def collapse(p):
    name, w = p["name"], p["wood"]
    b = Build(f"{name}_collapse", 14, 9, 14, seed=1907505 + p["seed"])
    rng = b.rng
    knoll(b, rng, p, 7, 3, 6.5, 5, 6)
    adit(b, rng, p, 5, 4, 8)
    # the fall: rubble filling the mouth and spilling out, beams down across it
    for z in range(5, 11):
        for x in range(4, 10):
            spill = 3 - abs(x - 6) * 0.8 - max(0, z - 8) * 0.9
            for y in range(1, int(spill) + 1):
                if b.inb(x, y, z):
                    b.set(x, y, z, pick(rng, p["spoil"]))
    b.set(5, 2, 9, B(f"stripped_{w}_log", axis="z"))
    b.set(7, 1, 10, B(f"stripped_{w}_log", axis="x"))
    b.set(8, 1, 10, B(f"stripped_{w}_log", axis="x"))
    b.chest(6, 0, 10, "north", f"expanse:chests/{name}_hidden")
    b.set(6, 1, 10, B("gravel"))
    rails(b, 6, 11, 13)
    # a helmet on a stake, a warning sign, a dropped lantern
    b.set(10, 0, 11, B("cobblestone"))
    b.set(10, 1, 11, B(f"{w}_fence"))
    b.set(10, 2, 11, B(f"{w}_fence"))
    b.item_frame(11, 2, 11, "east", item("iron_helmet"))
    b.sign(10, 1, 12, w, ["", "ADIT CLOSED", "", ""],
           wall_facing="south")
    b.set(4, 1, 11, B("lantern"))
    b.barrel(3, 1, 12, "up", f"expanse:chests/{name}")
    ground_item(b, 4, 1, 12, book(BOOKS[p["books"][2]]), rotation=2)
    trail(b, [(6, 13), (7, 13)], rng, p["path"], width=1, fade=0.5)
    flora(b, [(x, z) for x in range(14) for z in range(9, 14)], rng, 0.15, p["ground"])
    return b


def variant_module(key):
    p = PALETTES[key]
    name = p["name"]
    camp, dig = p["procs"]
    structure = dict(biomes=p["biomes"], size=1, max_distance=32, set="biome_sights", max_relief=20)

    def pools():
        return {"start": [Piece(adit_camp(p), 3, camp), Piece(sluice(p), 2, dig), Piece(collapse(p), 2, camp)]}
    return name, structure, data_for(name, [camp, dig]), pools


NAME, STRUCTURE, DATA, pools = variant_module("peaks")
