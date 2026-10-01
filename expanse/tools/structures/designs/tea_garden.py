"""Tea gardens of the wisteria vale.

start pool (3 variants)
  pavilion : an open tea pavilion on a stone platform, hip roof hung with wisteria,
             the tea things still on the table, cushions round it, a koi pond with
             stepping stones and a stone lantern; a hatch in the floor under the
             host's cushion hides the tea chest (hidden chest)
  moongate : a white garden wall with a round moon gate, wisteria spilling over the
             coping, raked gravel and rocks behind, a bench and a bonsai; a stash
             under the stone bench
  bridge   : a red arched bridge over a lily stream, lanterns at its ends, a boat
             tied under it, a fisher's stool; a chest tucked beneath the bridge foot,
             reachable from the water
"""
import math

from blocks import AIR, B
from builder import Build, item, slab, stairs, trapdoor
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, hip_cap, pick, plant, ring, small_tree, stepping_stones, trail

NAME = "tea_garden"
STRUCTURE = dict(biomes=["wisteria_vale"], size=1, max_distance=32, set="biome_sights")
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
W = "expanse:wisteria"
GROUND = ["grass_block", "grass_block", "moss_block"]
STONES = [("stone_bricks", 3), ("mossy_stone_bricks", 2), ("andesite", 1)]
FLOWERS = ["allium", "pink_tulip", "azure_bluet", "lily_of_the_valley", "white_tulip"]
BLOSSOM = "expanse:wisteria_blossoms"


def stone_lantern(b, x, z, y=1, mat="stone_brick", block="stone_bricks"):
    """Tōrō: stepped base, wall shaft, lantern box, cap."""
    b.set(x, y, z, B(block))
    b.set(x, y + 1, z, B(f"{mat}_wall"))
    b.set(x, y + 2, z, B("lantern"))
    b.set(x, y + 3, z, slab(mat, "bottom"))
    if b.get(x, y - 1, z) is None:
        b.set(x, y - 1, z, B(block))


def pond(b, rng, cx, cz, r, lilies=0.25):
    cells = ring(cx, cz, 0, r, (b.size[0], b.size[2]))
    for (x, z) in cells:
        b.set(x, 0, z, B("water", level=0))
        b.set(x, 1, z, AIR)
        if rng.random() < lilies:
            b.set(x, 1, z, B("lily_pad"))
    for (x, z) in ring(cx, cz, r, r + 1.2, (b.size[0], b.size[2])):
        if b.get(x, 0, z) is None:
            b.set(x, 0, z, pick(rng, [("mossy_cobblestone", 2), ("andesite", 2), ("gravel", 1), ("grass_block", 2)]))
            if rng.random() < 0.2 and b.get(x, 1, z) is None and b.get(x, 2, z) is None:
                b.tall_plant(x, 1, z, "expanse:cattail")
    return cells


def pavilion():
    b = Build(f"{NAME}_pavilion", 16, 11, 16, seed=1907421)
    rng = b.rng
    x0, z0, x1, z1 = 3, 3, 9, 9                       # platform
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            b.set(x, 0, z, pick(rng, STONES))
            b.set(x, 1, z, B("smooth_stone") if (x in (x0, x1) or z in (z0, z1)) else B(f"{W}_planks"))
    for x in (5, 6, 7):                               # steps down to the pond side
        b.set(x, 1, z1 + 1, stairs("stone_brick", "north"))
        b.set(x, 0, z1 + 1, pick(rng, STONES))
    clear(b, x0, 2, z0, x1, 5, z1)
    # posts, beams with wisteria hanging off them, the hip roof with upturned corners
    for (x, z) in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        for y in (2, 3, 4):
            b.set(x, y, z, B(f"{W}_log", axis="y"))
    for x in range(x0 + 1, x1):
        b.set(x, 5, z0 + 1, B(f"expanse:stripped_wisteria_log", axis="x"))
        b.set(x, 5, z1 - 1, B(f"expanse:stripped_wisteria_log", axis="x"))
    for z in range(z0 + 2, z1 - 1):
        b.set(x0 + 1, 5, z, B(f"expanse:stripped_wisteria_log", axis="z"))
        b.set(x1 - 1, 5, z, B(f"expanse:stripped_wisteria_log", axis="z"))
    hip_cap(b, x0, z0, x1, z1, 6, "dark_oak", cap=B("dark_oak_planks"), upturn=True)
    b.set(6, 10, 6, B("lantern"))                     # finial
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if rng.random() < 0.45 and b.get(x, 5, z) is None:
                b.hang_moss(x, 5, z, rng.randint(1, 3), block=BLOSSOM)
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            if rng.random() < 0.45 and b.get(x, 5, z) is None:
                b.hang_moss(x, 5, z, rng.randint(1, 3), block=BLOSSOM)
    for x in range(x0 + 1, x1):                       # cross-beam carrying the lamp chain
        b.set(x, 6, 6, B("expanse:stripped_wisteria_log", axis="x"))
    b.set(6, 4, 6, B("lantern", hanging=True))
    b.set(6, 5, 6, B("iron_chain", axis="y"))
    # the tea table and its things, cushions round it, the host's cushion over the floor hatch
    b.set(6, 2, 6, slab("dark_oak", "top"))
    b.set(7, 2, 6, slab("dark_oak", "top"))
    b.set(6, 3, 6, B("white_candle", candles=2, lit=True))
    ground_item(b, 7, 3, 6, item("honey_bottle"), rotation=1)
    b.pot(5, 2, 5, "south", ("brick", "flow_pottery_sherd", "brick", "brick"))
    b.set(8, 2, 5, B("potted_flowering_azalea_bush"))
    for (x, z) in ((6, 7), (7, 7), (5, 6), (8, 6)):
        b.cushion(x, 2, z, color=rng.choice(["pink", "magenta", "purple", "white"]))
    b.set(6, 1, 5, trapdoor(f"{W}", "south", "top", False))
    b.chest(6, 0, 5, "south", HIDDEN)
    b.cushion(6, 2, 5, color="purple")
    b.barrel(4, 2, 7, "up", LOOT)
    ground_item(b, 8, 2, 7, book(BOOKS[NAME]), rotation=2)
    # the koi pond, stepping stones across the lawn, a stone lantern and a wisteria
    cells = pond(b, rng, 11.5, 12.5, 2.4)
    stone_lantern(b, 9, 13)
    stepping_stones(b, [(6, 11), (6, 12), (7, 13), (7, 14), (8, 15)], rng,
                    [("stone_bricks", 1), ("andesite", 1), ("mossy_stone_bricks", 1)], every=1)
    small_tree(b, 13, 4, rng, f"{W}_log", f"{W}_leaves", h=4, lean="west", r=2.4, blossoms=BLOSSOM)
    for (x, z) in ((2, 11), (1, 6), (11, 9), (12, 8)):
        plant(b, x, 1, z, rng.choice(FLOWERS), "grass_block")
    flora(b, [(x, z) for x in range(16) for z in range(16) if not (x0 <= x <= x1 and z0 <= z <= z1)], rng, 0.18,
          GROUND)
    return b


def moongate():
    b = Build(f"{NAME}_moongate", 15, 9, 11, seed=1907423)
    rng = b.rng
    wz = 4
    for x in range(1, 14):                            # plaster wall on a stone plinth, tile coping
        h = 6 if 4 <= x <= 10 else 5 if 2 <= x <= 12 else 3
        b.set(x, 0, wz, pick(rng, STONES))
        b.set(x, 1, wz, B("stone_bricks"))
        for y in range(2, h + 1):
            b.set(x, y, wz, B("white_terracotta"))
        b.set(x, h + 1, wz - 1, stairs("deepslate_tile", "south"))
        b.set(x, h + 1, wz + 1, stairs("deepslate_tile", "north"))
        b.set(x, h + 1, wz, B("deepslate_tiles"))
        b.set(x, h + 2, wz, slab("deepslate_tile"))
    for x in (1, 13):                                 # end posts
        for y in range(1, 5):
            b.set(x, y, wz, B("stone_bricks"))
    gx, gy, r = 7, 3.0, 2.6                           # the moon gate (a full circle, threshold at y=0)
    for x in range(3, 12):
        for y in range(1, 7):
            d = math.hypot(x - gx, y - gy)
            if d <= r:
                b.set(x, y, wz, AIR)
            elif d <= r + 0.9 and b.get(x, y, wz) is not None and y > 1:
                b.set(x, y, wz, B("smooth_stone"))
    b.set(gx, 0, wz, B("smooth_stone"))
    # wisteria spilling over the coping
    for x in range(2, 13):
        if rng.random() < 0.4:
            side = rng.choice([-1, 1])
            h = 6 if 4 <= x <= 10 else 5                # under the coping stair of this stretch
            if rng.random() < 0.6:
                b.set(x, h + 2, wz + side, B(f"{W}_leaves", persistent=True, distance=1))
            if b.get(x, h, wz + side) is None:
                b.hang_moss(x, h, wz + side, rng.randint(1, 3), block=BLOSSOM)
    small_tree(b, 12, 8, rng, f"{W}_log", f"{W}_leaves", h=4, lean="west", r=2.0, blossoms=BLOSSOM)
    # front (south): path through the gate, potted bonsai either side, stone lantern, bench
    trail(b, [(gx, wz + 1), (gx, 8), (gx + 1, 10)], rng, [("gravel", 2), ("stone_bricks", 1), ("andesite", 1)],
          width=2, fade=0.6)
    for x in (gx - 2, gx + 2):
        b.set(x, 1, wz + 1, B("potted_azalea_bush"))
    stone_lantern(b, 3, 7)
    # behind (north): raked gravel, rocks with moss, the bench with the stash under it
    for x in range(2, 13):
        for z in range(0, wz):
            b.set(x, 0, z, B("gravel") if (z + (x // 4)) % 2 else B("sand"))
            b.set(x, 1, z, AIR)
    for (x, z, h) in ((4, 1, 2), (10, 2, 1), (5, 2, 1)):
        for y in range(1, h + 1):
            b.set(x, y, z, pick(rng, [("mossy_cobblestone", 2), ("andesite", 2), ("stone", 1)]))
        b.set(x, h + 1, z, B("moss_carpet"))
    b.set(gx, 0, 1, B("smooth_stone"))
    b.set(gx, 0, 2, B("smooth_stone"))
    b.set(gx, 0, 3, B("smooth_stone"))
    for x in (9, 10, 11):
        b.set(x, 1, 0, slab("smooth_stone", "top"))
    b.chest(10, 0, 0, "south", HIDDEN)
    b.set(10, 0, 1, B("gravel"))
    b.pot(12, 1, 1, "west", ("brick", "heart_pottery_sherd", "brick", "friend_pottery_sherd"), loot=LOOT)
    ground_item(b, 9, 2, 0, book(BOOKS[f"{NAME}_moongate"]), rotation=1)
    flora(b, [(x, z) for x in range(15) for z in range(5, 11)], rng, 0.2, GROUND)
    return b


def bridge():
    b = Build(f"{NAME}_bridge", 15, 8, 13, seed=1907425)
    rng = b.rng
    R = "expanse:redwood"
    # the stream: water three wide, reeds and lilies, mossy stones on the banks
    reeds = []
    for x in range(0, 15):
        wob = int(round(math.sin(x * 0.5) * 0.8))
        for z in range(5 + wob, 8 + wob):
            b.set(x, 0, z, B("water", level=0))
            b.set(x, 1, z, B("lily_pad") if rng.random() < 0.12 else AIR)
        for z in (4 + wob, 8 + wob):
            if rng.random() < 0.6:
                b.set(x, 0, z, pick(rng, [("mossy_cobblestone", 2), ("gravel", 2), ("mud", 1)]))
            if rng.random() < 0.18 and not 4 <= x <= 10:
                reeds.append((x, z))
    # the arched bridge (red lacquered redwood) along z at x=6..8
    prof = {2: (1, "stairs"), 3: (2, "slab"), 4: (2, "full"), 5: (3, "slab"), 6: (3, "full"), 7: (3, "slab"),
            8: (2, "full"), 9: (2, "slab"), 10: (1, "stairs")}
    for z, (y, kind) in prof.items():
        for x in (6, 7, 8):
            if kind == "stairs":
                b.set(x, y, z, stairs(R, "south" if z == 2 else "north"))
            elif kind == "slab":
                b.set(x, y, z, slab(R, "bottom"))
            else:
                b.set(x, y, z, B(f"{R}_planks"))
            if kind == "stairs" and b.get(x, 0, z) is None:
                b.set(x, 0, z, pick(rng, STONES))
        for x in (5, 9):                               # railings
            ry = y + (1 if kind != "slab" else 0)
            b.set(x, ry, z, B(f"{R}_fence"))
            if kind != "stairs":
                b.set(x, ry - 1, z, B(f"{R}_planks"))
    for (x, z) in ((5, 2), (9, 2), (5, 10), (9, 10)):  # lantern posts at the ends
        for y in (1, 2, 3):
            b.set(x, y, z, B(f"{R}_fence"))
        b.set(x, 4, z, B("lantern"))
        b.set(x, 0, z, pick(rng, STONES))
    for z in range(2, 11):
        b.air(6, prof[z][0] + 1, z, 8, prof[z][0] + 2, z, only_void=True)
    # under the bridge: a boat tied up, and a chest tucked in at the bridge foot
    wob7 = int(round(math.sin(7 * 0.5) * 0.8))
    b.boat(11, 1, 5 + int(round(math.sin(11 * 0.5) * 0.8)) + 1, wood="cherry", yaw=90.0)
    b.chest(7, 0, 4 + wob7, "south", HIDDEN)           # under the bridge foot, open to the water
    b.set(7, 1, 4 + wob7, AIR)
    # the fisher's spot on the south bank
    b.set(2, 1, 10, stairs("spruce", "south"))
    b.barrel(3, 1, 10, "up", LOOT)
    b.set(1, 1, 10, B("spruce_fence"))
    b.item_frame(1, 1, 11, "south", item("fishing_rod"))
    ground_item(b, 2, 1, 11, item("cod"), 1)
    ground_item(b, 4, 1, 11, book(BOOKS[f"{NAME}_bridge"]), rotation=2)
    small_tree(b, 12, 11, rng, f"{W}_log", f"{W}_leaves", h=4, lean="north", r=2.2, blossoms=BLOSSOM)
    for (x, z) in reeds:
        if b.get(x, 1, z) is None and b.get(x, 2, z) is None:
            b.set(x, 0, z, B("mud"))
            b.tall_plant(x, 1, z, "expanse:cattail")
    trail(b, [(7, 1), (7, 0)], rng, [("gravel", 2), ("dirt_path", 2)], width=2, fade=0.9)
    trail(b, [(7, 11), (6, 12)], rng, [("gravel", 2), ("dirt_path", 2)], width=2, fade=0.9)
    flora(b, [(x, z) for x in range(15) for z in range(13)], rng, 0.18, GROUND)
    return b


def pools():
    return {"start": [Piece(pavilion(), 3, "small_vale"), Piece(moongate(), 2, "small_vale"),
                      Piece(bridge(), 2, "small_vale")]}


DATA = data_for(NAME, ["small_vale"])
