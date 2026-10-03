"""Shore life on the palm coast.

start pool (3 variants)
  hut      : a frond-thatched hut on palm posts above the tide line, steps down to the
             sand, a hammock slung between posts, shells on the walls, a bamboo raft
             drawn up; driftwood crossed in an X on the sand marks where the beachcomber
             buried her finds (hidden chest)
  nets     : a net-mender's open shed - nets drying on frames, crab pots, a boat turned
             over on trestles, a gutting table with the catch; the takings are in a
             chest under the upturned boat
  castaway : S O S laid out in driftwood, a lean-to of planks and fronds, a signal fire
             smoking on a hay bale, a message in a bottle; the castaway's sea chest sits
             in the lean-to, his savings buried at the foot of the signal fire
"""
from blocks import B
from builder import Build, item, slab, stairs
from pieces import Piece
from small_data import BOOKS, data_for
from smallkit import book, clear, flora, ground_item, hip_cap, meal_fire, pick, raft, trail

NAME = "beach_hut"
STRUCTURE = dict(biomes=["palm_coast"], size=1, max_distance=32, set="biome_sights", sited=False)
LOOT = f"expanse:chests/{NAME}"
HIDDEN = f"expanse:chests/{NAME}_hidden"
P = "expanse:palm"
GROUND = ["sand", "sand", "coarse_dirt"]
PATH = [("sand", 3), ("gravel", 1), ("coarse_dirt", 1)]
FRONDS = B(f"{P}_leaves", persistent=True, distance=1)
DRIFT = B("expanse:stripped_palm_log", axis="x")


def palm_post(b, x, z, top):
    b.set(x, 0, z, B("sandstone"))
    for y in range(1, top + 1):
        b.set(x, y, z, B(f"{P}_log", axis="y"))


def frond_roof(b, x0, z0, x1, z1, y):
    """Hip roof of palm slabs/stairs with fronds laid over it."""
    hip_cap(b, x0, z0, x1, z1, y, P, cap=B(f"{P}_planks"))
    for (x, yy, z), (st, _) in list(b.cells.items()):
        if yy >= y and st.id.startswith(f"{P}_") and st.short.endswith(("_stairs", "_slab", "_planks")):
            if b.inb(x, yy + 1, z) and b.get(x, yy + 1, z) is None and (x + z + yy) % 3:
                b.set(x, yy + 1, z, FRONDS)


def hut():
    b = Build(f"{NAME}_hut", 15, 11, 15, seed=1907511)
    rng = b.rng
    x0, z0, x1, z1 = 3, 2, 8, 7
    D = 2
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (x0, 5), (x1, 4)):
        palm_post(b, x, z, D + 3)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if b.get(x, D, z) is None:
                b.set(x, D, z, B(f"{P}_planks"))
    for x in range(x0 + 1, x1):                       # back wall (north), side railings, open front
        for y in (D + 1, D + 2, D + 3):
            b.set(x, y, z0, B(f"{P}_planks") if y < D + 3 else B(f"{P}_fence"))
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            if b.get(x, D + 1, z) is None:
                b.set(x, D + 1, z, B(f"{P}_fence"))
    b.air(x0 + 1, D + 1, z0 + 1, x1 - 1, D + 3, z1)
    frond_roof(b, x0 - 1, z0 - 1, x1 + 1, z1 + 1, D + 4)
    for x in (5, 6):                                  # steps down to the sand
        b.set(x, D, z1 + 1, stairs(P, "north"))
        b.set(x, 1, z1 + 2, stairs(P, "north"))
        b.set(x, 0, z1 + 2, B("sandstone"))
        b.set(x, D - 1, z1 + 1, B(f"{P}_planks"))
        b.set(x, 0, z1 + 1, B("sandstone"))
    # inside: sleeping mat, sea chest, shells and a turtle shell on the wall, a lantern
    b.bed(x0 + 1, D + 1, z0 + 2, "north", "light_blue")
    b.barrel(x1 - 1, D + 1, z0 + 1, "west", LOOT)
    b.pot(x1 - 1, D + 1, z0 + 2, "west", ("brick", "angler_pottery_sherd", "brick", "brick"))
    b.item_frame(4, D + 2, z0 + 1, "south", item("nautilus_shell"))
    b.item_frame(6, D + 2, z0 + 1, "south", item("turtle_helmet"))
    b.item_frame(7, D + 3, z0 + 1, "south", item("fishing_rod"), rotation=1)
    for x in range(x0 + 1, x1):
        b.set(x, D + 4, 4, B("expanse:stripped_palm_log", axis="x"))
    b.set(5, D + 3, 4, B("lantern", hanging=True))
    b.cushion(6, D + 1, 5, color="cyan")
    ground_item(b, 4, D + 1, 6, book(BOOKS[NAME]), rotation=1)
    # hammock between two posts to the east
    for z in (3, 6):
        palm_post(b, 12, z, 2)
    b.set(12, 2, 4, slab("white_wool", "bottom"))
    b.set(12, 2, 5, slab("white_wool", "bottom"))
    # the raft on the sand, the X of driftwood over the buried finds
    raft(b, 11, 1, 11, yaw=30.0)
    for i in range(-1, 2):
        b.set(2 + i, 1, 12 + i, B("expanse:stripped_palm_log", axis="x"))
        if i:
            b.set(2 - i, 1, 12 + i, B("expanse:stripped_palm_log", axis="z"))
    b.chest(2, 0, 12, "north", HIDDEN)
    b.set(2, 1, 12, B("expanse:stripped_palm_log", axis="y"))
    ground_item(b, 4, 1, 12, item("iron_shovel"), rotation=2)
    clear(b, 0, 1, 9, 14, 2, 14)
    flora(b, [(x, z) for x in range(15) for z in range(15)], rng, 0.1, GROUND)
    return b


def nets():
    b = Build(f"{NAME}_nets", 15, 8, 13, seed=1907513)
    rng = b.rng
    x0, z0, x1, z1 = 2, 2, 8, 6
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (5, z0), (5, z1)):
        palm_post(b, x, z, 3)
    frond_roof(b, x0 - 1, z0 - 1, x1 + 1, z1 + 1, 4)
    clear(b, x0, 1, z0, x1, 3, z1)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if b.get(x, 0, z) is None:
                b.set(x, 0, z, pick(rng, [("sand", 3), ("sandstone", 1), ("coarse_dirt", 1)]))
    # nets drying on frames (cobweb mesh on fences) along the west, and inside
    for z in range(8, 12):
        b.set(1, 1, z, B(f"{P}_fence"))
        b.set(1, 2, z, B("cobweb") if z in (9, 10) else B(f"{P}_fence"))
        b.set(1, 3, z, B(f"{P}_fence"))
    for x in (3, 4):
        b.set(x, 3, z0 + 1, B("cobweb"))
    # crab pots, gutting table with the catch, barrels, the market bell
    for (x, z) in ((7, 3), (7, 4), (6, 3)):
        b.set(x, 1, z, B("composter", level=rng.randint(0, 4)))
    b.set(4, 1, 5, slab(P, "top"))
    b.set(3, 1, 5, slab(P, "top"))
    ground_item(b, 4, 2, 5, item("cod"), rotation=1)
    ground_item(b, 3, 2, 5, item("salmon"), rotation=3)
    b.barrel(x0 + 1, 1, z0 + 1, "up", LOOT)
    b.bell(5, 3, z1 + 1, attachment="ceiling", facing="east")    # rung when the catch is in
    ground_item(b, 3, 1, 3, book(BOOKS[f"{NAME}_nets"]), rotation=0)
    # the boat turned over on trestles, the takings underneath
    bx, bz = 10, 8
    for i in range(4):
        for j in range(2):
            f = "north" if j == 0 else "south"
            b.set(bx + i, 2, bz + j, stairs("spruce", f, "top") if i not in (0, 3) else slab("spruce", "top"))
    for (x, z) in ((bx, bz), (bx + 3, bz + 1)):
        b.set(x, 1, z, B(f"{P}_fence"))
    b.chest(bx + 1, 1, bz, "south", HIDDEN)
    trail(b, [(5, z1 + 1), (6, 10), (6, 12)], rng, PATH, width=2, fade=0.5)
    flora(b, [(x, z) for x in range(15) for z in range(13)], rng, 0.08, GROUND)
    return b


def castaway():
    b = Build(f"{NAME}_castaway", 17, 7, 13, seed=1907515)
    rng = b.rng
    # S O S in driftwood on the sand (each letter 3 wide x 5 tall, laid along x)
    S = ["###", "#..", "###", "..#", "###"]
    O = ["###", "#.#", "#.#", "#.#", "###"]
    for k, letter in enumerate((S, O, S)):
        for r, row in enumerate(letter):
            for c, ch in enumerate(row):
                if ch == "#":
                    x, z = 2 + k * 4 + c, 6 + r
                    b.set(x, 1, z, B("spruce_log" if (x + z) % 3 else "dark_oak_log",
                                     axis="x" if r in (0, 2, 4) else "z"))   # dark driftwood shows on sand
    # lean-to of planks and fronds against two posts, the sea chest inside
    for (x, z) in ((3, 1), (7, 1)):
        palm_post(b, x, z, 2)
    for x in range(2, 9):
        b.set(x, 3, 1, slab(P, "bottom"))
        b.set(x, 2, 2, slab(P, "top"))
        b.set(x, 3, 0, FRONDS)
    clear(b, 3, 1, 1, 7, 1, 2)
    b.barrel(5, 1, 1, "south", LOOT)
    b.set(6, 1, 1, B("light_blue_carpet"))
    b.set(4, 1, 1, B("light_blue_carpet"))
    # the signal fire on a hay bale, savings buried at its foot; tally marks on a post
    b.set(13, 0, 3, B("hay_block", axis="y"))
    b.campfire(13, 1, 3, lit=True)
    for (x, z) in ((12, 3), (14, 3), (13, 2), (13, 4)):
        b.set(x, 0, z, B("cobblestone"))
    b.chest(12, 0, 4, "north", HIDDEN)
    b.set(12, 1, 4, B("sand"))
    for y in (1, 2):
        b.set(15, y, 6, B(f"{P}_fence"))
    b.sign(15, 2, 7, "jungle", ["|||| |||| ||||", "|||| |||| ||||", "|||| |||", "day 37"], wall_facing="south")
    # the message in a bottle, half in the wet sand; a fish skeleton
    b.set(10, 0, 11, B("sand"))
    ground_item(b, 10, 1, 11, book(BOOKS[f"{NAME}_castaway"]), rotation=1)
    ground_item(b, 11, 1, 12, item("glass_bottle"), rotation=1)
    b.set(4, 1, 11, B("bone_block", axis="x"))
    b.set(5, 1, 11, B("bone_block", axis="x"))
    meal_fire(b, 9, 1, 3, ["cod"], lit=False, done=(200, 0, 0, 0))
    flora(b, [(x, z) for x in range(17) for z in range(13)], rng, 0.06, GROUND)
    return b


def pools():
    return {"start": [Piece(hut(), 3, "small_coast"), Piece(nets(), 2, "small_coast"),
                      Piece(castaway(), 2, "small_coast")]}


DATA = data_for(NAME, ["small_coast"])
