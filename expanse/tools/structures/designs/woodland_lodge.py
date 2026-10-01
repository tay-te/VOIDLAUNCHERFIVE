"""Woodland lodge: a cosy redwood log cabin with a cobblestone plinth, dark oak
gable roof, covered porch, two rooms (bedroom + kitchen/living room), an
inside hearth and a stone chimney with a campfire smoking on top."""
from blocks import AIR, B
from builder import Build, log, stairs, trapdoor
from common import gable_roof

NAME = "woodland_lodge"
LOOT = "expanse:chests/woodland_lodge"
W = "expanse:redwood"


def build_variant(variant):
    b = Build(f"{NAME}_{variant}", 15, 13, 14, seed=1847331 + variant)
    rng = b.rng
    roof = "dark_oak" if variant == 1 else "spruce"
    x0, x1, z0, z1 = 1, 11, 1, 7          # cabin outer walls

    # ---- foundation and floors
    b.fill(x0, 0, z0, x1, 0, 10, "cobblestone")
    b.fill(x0, 1, z0, x1, 1, z1, "cobblestone")
    b.fill(x0 + 1, 1, z0 + 1, x1 - 1, 1, z1 - 1, f"{W}_planks")
    b.replace(["cobblestone"], "mossy_cobblestone", prob=0.25)
    # porch deck
    b.fill(x0, 1, 8, x1, 1, 10, B("spruce_planks"))
    for x in range(x0, x1 + 1):
        b.set(x, 1, 10, B("expanse:stripped_redwood_log", axis="x"))

    # ---- log walls
    b.fill(x0 + 1, 2, z0 + 1, x1 - 1, 9, z1 - 1, AIR)
    for y in range(2, 6):
        for x in range(x0, x1 + 1):
            b.set(x, y, z0, log(W, "x"))
            b.set(x, y, z1, log(W, "x"))
        for z in range(z0, z1 + 1):
            b.set(x0, y, z, log(W, "z"))
            b.set(x1, y, z, log(W, "z"))
            b.set(6, y, z, B(f"{W}_planks"))  # partition
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1), (6, z0), (6, z1)):
        b.column(x, z, 1, 6, log(W, "y"))
    for x in range(x0, x1 + 1):
        b.set(x, 6, z0, B("expanse:stripped_redwood_log", axis="x"))
        b.set(x, 6, z1, B("expanse:stripped_redwood_log", axis="x"))
    for z in range(z0 + 1, z1):
        b.set(x0, 6, z, B("expanse:stripped_redwood_log", axis="z"))
        b.set(x1, 6, z, B("expanse:stripped_redwood_log", axis="z"))
    # ceiling tie beams carrying lanterns
    for x in range(x0 + 1, x1):
        b.set(x, 6, 4, B("expanse:stripped_redwood_log", axis="x"))

    # ---- roof (ridge along x) + lean-to porch roof
    gable_roof(b, x0, x1, z0, z1, 6, roof, B(f"{W}_planks"), overhang=1,
               ridge=B(f"{roof}_slab", type="bottom"))
    # interior of the roof space is open (vaulted): carve under the slopes
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            for y in range(7, 11):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, AIR)
    for x in range(x0 - 1, x1 + 2):
        b.set(x, 5, 9, stairs(roof, "north"))
        b.set(x, 4, 10, stairs(roof, "north"))
    for x in (x0, 6, x1):
        b.column(x, 10, 2, 3, B("expanse:redwood_fence"))
    # porch railing (gap for the steps)
    for x in range(x0 + 1, x1):
        if x not in (7, 8, 9) and x != 6:
            b.set(x, 2, 10, B("expanse:redwood_fence"))
    for z in (8, 9):
        b.set(x0, 2, z, B("expanse:redwood_fence"))
        b.set(x1, 2, z, B("expanse:redwood_fence"))
    for x in (7, 8, 9):
        b.set(x, 1, 11, stairs("spruce", "north"))
        b.set(x, 0, 11, B("cobblestone"))
    b.air(x0, 2, 8, x1, 3, 9, only_void=True)
    b.air(x0 + 1, 2, 10, x1 - 1, 3, 10, only_void=True)

    # gable windows
    for gx in (x0, x1):
        b.set(gx, 8, 4, B("glass_pane"))
        b.set(gx, 7, 3, B(f"{W}_planks"))

    # ---- doors and windows
    b.door(8, 2, z1, W, "south", hinge="left")
    b.door(6, 2, 4, W, "east", hinge="left")
    for (x, z) in ((3, z1), (4, z1), (10, z1), (3, z0), (9, z0)):
        b.set(x, 3, z, B("glass_pane"))
        b.set(x, 4, z, B("glass_pane"))
    b.set(x0, 3, 4, B("glass_pane"))
    b.set(x0, 4, 4, B("glass_pane"))
    # shutters (open trapdoors hugging the wall)
    for (x, z, f) in ((2, z1 + 1, "south"), (5, z1 + 1, "south"), (2, z0 - 1, "north"), (4, z0 - 1, "north"),
                      (8, z0 - 1, "north"), (10, z0 - 1, "north")):
        for y in (3, 4):
            b.set(x, y, z, trapdoor(W, f, "bottom" if y == 3 else "top", True))
    for (x, z, f) in ((x0 - 1, 3, "west"), (x0 - 1, 5, "west")):
        for y in (3, 4):
            b.set(x, y, z, trapdoor(W, f, "bottom" if y == 3 else "top", True))
    # window boxes on the porch
    for x in (3, 4):
        b.set(x, 2, 8, trapdoor("spruce", "south", "top", False))

    # ---- chimney (east) with hearth and a campfire smoking on top
    for y in range(0, 6):
        for x in (12, 13):
            for z in (3, 4, 5):
                b.set(x, y, z, rng.choice(["cobblestone", "stone_bricks", "mossy_cobblestone", "cobblestone"]))
    for z in (3, 5):
        b.set(13, 6, z, stairs("cobblestone", "west"))
    b.set(13, 6, 4, stairs("stone_brick", "west"))
    for y in range(6, 11):
        for z in (3, 4, 5):
            b.set(12, y, z, rng.choice(["stone_bricks", "cobblestone", "stone_bricks"]))
    b.set(12, 11, 3, B("cobblestone_wall"))
    b.set(12, 11, 5, B("cobblestone_wall"))
    b.campfire(12, 11, 4)
    # hearth: opening in the east wall, campfire inside the chimney base
    b.set(x1, 2, 4, AIR)
    b.set(x1, 3, 4, AIR)
    b.set(x1, 4, 4, stairs("stone_brick", "west", "top"))
    b.set(x1, 2, 3, B("stone_bricks"))
    b.set(x1, 2, 5, B("stone_bricks"))
    b.campfire(12, 2, 4, facing="west")
    b.set(12, 3, 4, AIR)
    b.set(12, 1, 4, B("stone_bricks"))
    # woodpile against the chimney
    for z in (6, 7):
        for y in (1, 2):
            b.set(13, y, z, log(W, "x"))
        b.set(12, 1, z, log(W, "x"))
    b.set(12, 2, 6, log(W, "x"))
    b.set(13, 1, 9, B("expanse:stripped_redwood_log", axis="y"))
    b.set(13, 0, 9, B("dirt"))

    # ---- bedroom (west)
    b.bed(2, 2, 3, "north", "red")
    b.chest(3, 2, 2, "south", LOOT)
    b.set(3, 3, 2, B("lantern"))
    b.set(5, 2, 2, B("bookshelf"))
    b.set(5, 3, 2, B("bookshelf"))
    b.set(5, 2, 6, B("barrel", facing="up"))
    b.set(5, 3, 6, B("expanse:potted_redwood_sapling"))
    b.set(2, 2, 6, B("chiseled_bookshelf", facing="east", slot_1_occupied=True, slot_2_occupied=True,
                     slot_5_occupied=True))
    for x in (3, 4):
        for z in (4, 5):
            b.set(x, 2, z, B("red_carpet"))
    b.set(4, 5, 4, B("lantern", hanging=True))

    # ---- living room / kitchen (east)
    b.set(7, 2, 2, B("crafting_table"))
    b.set(8, 2, 2, B("furnace", facing="south"))
    b.set(9, 2, 2, B("smoker", facing="south"))
    b.set(10, 2, 2, B("barrel", facing="up"))
    b.set(10, 3, 2, B("expanse:potted_heather" if variant == 2 else "potted_red_tulip"))
    b.set(10, 2, 6, B("barrel", facing="north"))
    b.set(9, 2, 6, B("spruce_fence"))
    b.set(9, 3, 6, B("spruce_pressure_plate"))
    b.set(9, 2, 5, stairs("spruce", "south"))
    for x, z in ((10, 3), (10, 4), (10, 5)):
        b.set(x, 2, z, B("orange_carpet"))
    b.set(9, 5, 4, B("lantern", hanging=True))
    b.set(7, 2, 6, B("water_cauldron", level=3))

    # ---- porch furniture
    b.set(2, 2, 8, B("barrel", facing="up"))
    b.set(4, 2, 9, stairs("spruce", "north"))
    b.set(5, 2, 9, stairs("spruce", "north"))
    b.set(10, 2, 8, B("composter", level=0))
    b.set(7, 4, 9, B("lantern", hanging=True))
    b.set(3, 4, 9, B("lantern", hanging=True))

    # path from the steps
    for z in (12, 13):
        for x in (7, 8, 9):
            if x == 8 or rng.random() < 0.5:
                b.set(x, 0, z, B("dirt_path" if rng.random() < 0.8 else "coarse_dirt"))
    return b


def build():
    return [build_variant(1), build_variant(2)]
