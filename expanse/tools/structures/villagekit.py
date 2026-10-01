"""Villages for the Expanse biomes, built the way vanilla 26.3 builds minecraft:village_plains & co.

Every style (designs/village_<style>.py, palettes in village_styles.py) is one jigsaw structure whose
pools have vanilla's shape and use vanilla's jigsaw names, so everything the game does in a village
works there too:

  start        town centres (rigid, layer 0 = the ground course): the bell (the MEETING point),
               villagers, an iron golem and cats, a street jigsaw on each side. 1 in 50 is the
               zombie village's.
  streets      terrain_matching lots with a 3-wide path, as vanilla's own: street jigsaws (y=1) on the
               box edge pointing out, building_entrance jigsaws (y=1) on the path edge pointing into the
               lot, decor jigsaws (y=0, up) in the lot. Falls back to terminators.
  houses       rigid; the entrance jigsaw at (0, 0, z) faces west, so the floor is one block above the
               path with a stair step at the door, as vanilla's small houses. Houses of every profession
               with their job-site block and vanilla's chest for the trade, beds (HOME), farms with a
               composter (farmer), animal pens, a second meeting point, the style's own pieces. Falls
               back to terminators; 10 empties as vanilla.
  decor        lamp posts, the style's own bits, vanilla placed features (hay piles, flowers).
  villagers    unemployed / nitwit / baby villager entities.
  terminators  short path ends.
  z_*          the zombie village: the same templates with their jigsaws pointed at the z_ pools, zombie
               villagers, no iron golem, and vanilla's zombie_* rules (doors and torches gone, cobwebs).

Iron golems, cats and farm animals come from vanilla's own village/common pools. The structure uses
vanilla's expansion hack (a house is placed inside its street's lot, whose box is raised to fit it).
"""
import math

import nbt
from blocks import AIR, B
from builder import DIRS, Build, slab, stairs, trapdoor
from pieces import Empty, Feature, Piece

J_STREET = "minecraft:street"
J_ENTRANCE = "minecraft:building_entrance"
J_BOTTOM = "minecraft:bottom"
V_GOLEM = "minecraft:village/common/iron_golem"
V_CATS = "minecraft:village/common/cats"
V_ANIMALS = "minecraft:village/common/animals"
V_BUTCHER_ANIMALS = "minecraft:village/common/butcher_animals"
V_SHEEP = "minecraft:village/common/sheep"
VLOOT = "minecraft:chests/village/village_"
# profession -> (job-site block (PoiTypes), vanilla chest table or None)
JOB = {
    "armorer": ("blast_furnace", "armorer"), "butcher": ("smoker", "butcher"),
    "cartographer": ("cartography_table", "cartographer"), "cleric": ("brewing_stand", "temple"),
    "farmer": ("composter", None), "fisherman": ("barrel", "fisher"), "fletcher": ("fletching_table", "fletcher"),
    "leatherworker": ("cauldron", "tannery"), "librarian": ("lectern", None), "mason": ("stonecutter", "mason"),
    "shepherd": ("loom", "shepherd"), "toolsmith": ("smithing_table", "toolsmith"),
    "weaponsmith": ("grindstone", "weaponsmith"),
}
WORKSHOPS = ["armorer", "butcher", "cartographer", "fisherman", "fletcher", "leatherworker", "mason", "shepherd",
             "toolsmith", "weaponsmith"]


# ------------------------------------------------------------------ materials
def wid(wood, kind):
    """Block id of a wood family member: wid("spruce", "planks"), wid("expanse:palm", "stripped_log")."""
    ns, base = wood.split(":") if ":" in wood else ("minecraft", wood)
    if kind.startswith("stripped_"):
        return f"{ns}:stripped_{base}_{kind[len('stripped_'):]}"
    return f"{ns}:{base}_{kind}"


class Style:
    """A village style: the materials and the few regional habits that set it apart from the plains."""

    DEFAULTS = dict(
        walls="timber",            # timber | cabin | stone | plaster | bamboo
        roof_shape="gable",        # gable | hip
        wall_h=3,
        ridge_slab=True,
        path="dirt_path", ground="grass_block", path_wear=0.1, street_mix=(),
        base=("cobblestone", "cobblestone", "cobblestone", "mossy_cobblestone"),
        stone=("cobblestone",), plinth="cobblestone", wall="white_terracotta",
        corner=None,               # corner post block (default: the frame log)
        floor=None,                # default: the planks of `wood`
        gable_fill=None,           # default: roof_fill
        beds=("white", "red", "yellow"), carpet="red", glass="glass_pane", chapel_glass="white_stained_glass_pane",
        flowers=("poppy", "dandelion"), pot="potted_poppy",
        crops=(("carrots", 0.3), ("potatoes", 0.2), ("beetroots", 0.1)),
        lamp="lantern", stilts=0, snow=False, round_houses=False,
        features=("minecraft:pile_hay", "minecraft:flower_plain"),
        animals=V_ANIMALS, loot_items=(), extras=None,
    )

    def __init__(self, **kw):
        d = dict(self.DEFAULTS)
        d.update(kw)
        self.__dict__.update(d)
        self.module = f"village_{self.name}"
        self.loot = f"expanse:chests/{self.module}"
        self.floor = self.floor or self.W("planks")
        self.gable_fill = self.gable_fill or self.roof_fill

    def W(self, kind):          # the house wood (floors, doors, furniture)
        return wid(self.wood, kind)

    def F(self, kind):          # the frame wood (posts, beams, farm frames)
        return wid(self.frame, kind)

    def pool(self, p, zombie=False):
        return f"expanse:{self.module}/{'z_' if zombie else ''}{p}"


def log(id_, axis="y"):
    return B(id_, axis=axis)


def pick(rng, opts):
    o = rng.choice(opts)
    return B(o) if isinstance(o, str) else o


def state_str(st):
    st = B(st) if isinstance(st, str) else st
    if not st.props:
        return st.id
    return st.id + "[" + ",".join(f"{k}={v}" for k, v in st.props.items()) + "]"


def step_state(P, facing="east"):
    """The stair step outside a door, as vanilla's entrance jigsaw final_state."""
    return state_str(stairs(P.wood, facing))


def hash_seed(name):
    return sum((i + 1) * ord(c) for i, c in enumerate(name)) * 7919


# ------------------------------------------------------------------ jigsaws
def j_entrance(b, x, y, z, final, front="west"):
    b.jigsaw(x, y, z, front, name=J_ENTRANCE, target=J_ENTRANCE, pool="minecraft:empty", final=final,
             joint="aligned")


def j_up(b, x, y, z, pool, final):
    b.jigsaw(x, y, z, "up", name=J_BOTTOM, target=J_BOTTOM, pool=pool, final=final, joint="rollable")


def j_down(b, x, z, final):
    b.jigsaw(x, 0, z, "down", name=J_BOTTOM, target=J_BOTTOM, pool="minecraft:empty", final=final,
             joint="rollable", top="south")


# ------------------------------------------------------------------ walls
def wall_cell(P, rng, x, y, z, x0, z0, x1, z1, h):
    """The state of one wall cell (course y = 1..h) of the rectangle x0..x1 / z0..z1."""
    corner = x in (x0, x1) and z in (z0, z1)
    along = "x" if z in (z0, z1) else "z"          # the direction the wall runs
    k = (x - x0) if along == "x" else (z - z0)
    if corner:
        return B(P.corner) if P.corner else log(P.F("log"))
    if P.walls == "timber":                        # frame posts, plaster infill, a wall plate on top
        if y == h:
            return log(P.F("log"), along)
        n = (x1 - x0) if along == "x" else (z1 - z0)
        return log(P.F("log")) if k % 4 == 0 and 0 < k < n - 1 else B(P.wall)
    if P.walls == "cabin":                         # horizontal logs
        return log(P.F("log"), along)
    if P.walls == "stone":                         # rubble under a timber wall plate
        return log(P.F("log"), along) if y == h else pick(rng, P.stone)
    if P.walls == "plaster":                       # plaster on a plinth course, a timber plate on top
        if y == h:
            return log(P.F("log"), along)
        return B(P.wall if y > 1 else P.plinth)
    if P.walls == "bamboo":                        # a stone sill, bamboo walls, a bamboo plate
        if y == 1:
            return B(P.plinth)
        return B("bamboo_block", axis=along) if y == h else B(P.wall)
    raise ValueError(P.walls)


def shell(b, P, x0, z0, x1, z1, rng, door_z, h=None, windows=(), floor=None, y0=0, torches=True):
    """Walls round x0..x1 / z0..z1 on a floor at y0 (wall courses y0+1..y0+h), the door in the front wall
    x=x0 at z=door_z, glass in the `windows` cells [(x, course, z)], air inside, wall torches either side
    of the door outside (vanilla's small houses)."""
    h = h or P.wall_h
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            b.set(x, y0, z, pick(rng, P.base) if edge else B(floor or P.floor))
            for y in range(1, h + 1):
                b.set(x, y0 + y, z, wall_cell(P, rng, x, y, z, x0, z0, x1, z1, h) if edge else AIR)
    for (x, y, z) in windows:
        b.set(x, y0 + y, z, B(P.glass))
    b.door(x0, y0 + 1, door_z, P.wood, "east", hinge="right")
    if torches:
        for tz in (door_z - 1, door_z + 1):
            if b.inb(x0 - 1, y0 + 2, tz) and b.get(x0 - 1, y0 + 2, tz) is None and \
                    b.get(x0, y0 + 2, tz) is not None and b.get(x0, y0 + 2, tz).short not in ("air", P.glass):
                b.set(x0 - 1, y0 + 2, tz, B("wall_torch", facing="west"))
    if P.snow:
        snow_banks(b, x0, z0, x1, z1, rng, door_z, y0)


def snow_banks(b, x0, z0, x1, z1, rng, door_z, y0=0):
    """Snow drifted against the walls (the tundra's houses stand half in it)."""
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if x0 <= x <= x1 and z0 <= z <= z1 or not b.inb(x, y0, z) or b.get(x, y0, z) is not None:
                continue
            if x == x0 - 1 and abs(z - door_z) <= 1:
                continue
            b.set(x, y0, z, B("snow_block"))
            y = y0 + 1
            if x0 <= x <= x1 or z0 <= z <= z1:         # deeper against the walls than at the corners
                if rng.random() < 0.55 and b.inb(x, y, z) and b.get(x, y, z) is None:
                    b.set(x, y, z, B("snow_block"))
                    y += 1
            if b.inb(x, y, z) and b.get(x, y, z) is None and rng.random() < 0.8:
                b.set(x, y, z, B("snow", layers=rng.choice((2, 3, 4, 5))))


# ------------------------------------------------------------------ roofs
def gable(b, P, x0, z0, x1, z1, y, axis="x", over=1):
    """A 45-degree gable roof over the walls x0..x1 / z0..z1 from height y (the eave course sits on the
    overhang), ridge along `axis`, the gable ends and the space under the roof filled solid as in
    vanilla's small houses (no dark attic for mobs). Returns the ridge height."""
    fill = B(P.roof_fill)
    gfill = B(P.gable_fill)
    if axis == "x":
        a0, a1, lo, hi, w0, w1, i0, i1 = x0 - over, x1 + over, z0 - over, z1 + over, x0, x1, z0, z1
    else:
        a0, a1, lo, hi, w0, w1, i0, i1 = z0 - over, z1 + over, x0 - over, x1 + over, z0, z1, x0, x1

    def at(a, c, yy):
        return (a, yy, c) if axis == "x" else (c, yy, a)

    k = 0
    while lo + k <= hi - k:
        p, q, yy = lo + k, hi - k, y + k
        for a in range(a0, a1 + 1):
            if p == q:
                b.set(*at(a, p, yy), slab(P.roof) if P.ridge_slab else B(P.ridge), clip=True)
                continue
            b.set(*at(a, p, yy), stairs(P.roof, "south" if axis == "x" else "east"), clip=True)
            b.set(*at(a, q, yy), stairs(P.roof, "north" if axis == "x" else "west"), clip=True)
            if w0 <= a <= w1:
                for c in range(max(p + 1, i0), min(q - 1, i1) + 1):
                    b.set(*at(a, c, yy), gfill if a in (w0, w1) else fill)
        k += 1
    return y + k - 1


def hip(b, P, x0, z0, x1, z1, y, over=1):
    """A hip roof: rings of stairs stepping in, filled underneath. Returns the top height."""
    fill = B(P.roof_fill)
    a0, a1, c0, c1 = x0 - over, x1 + over, z0 - over, z1 + over
    k = 0
    while a0 + k <= a1 - k and c0 + k <= c1 - k:
        xa, xb, za, zb, yy = a0 + k, a1 - k, c0 + k, c1 - k, y + k
        if xa == xb or za == zb:
            for xx in range(xa, xb + 1):
                for zz in range(za, zb + 1):
                    b.set(xx, yy, zz, slab(P.roof) if P.ridge_slab else B(P.ridge), clip=True)
            return yy
        for xx in range(xa, xb + 1):
            for zz in range(za, zb + 1):
                if xx in (xa, xb) or zz in (za, zb):
                    f = "south" if zz == za else "north" if zz == zb else "east" if xx == xa else "west"
                    b.set(xx, yy, zz, stairs(P.roof, f), clip=True)
                elif x0 <= xx <= x1 and z0 <= zz <= z1:
                    b.set(xx, yy, zz, fill)
        k += 1
    return y + k - 1


def roof(b, P, x0, z0, x1, z1, y, axis=None, shape=None):
    shape = shape or P.roof_shape
    if axis is None:
        axis = "x" if (x1 - x0) >= (z1 - z0) else "z"
    if shape == "hip":
        return hip(b, P, x0, z0, x1, z1, y)
    return gable(b, P, x0, z0, x1, z1, y, axis)


# ------------------------------------------------------------------ round houses (octagons)
def octagon(cx, cz, r, cut):
    return {(cx + dx, cz + dz) for dx in range(-r, r + 1) for dz in range(-r, r + 1)
            if abs(dx) + abs(dz) <= 2 * r - cut}


def boundary(cells):
    return {(x, z) for (x, z) in cells if any((x + dx, z + dz) not in cells
                                              for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}


def round_shell(b, P, cx, cz, r, cut, rng, h=None, windows=(), y0=0):
    """Round (octagonal) walls of radius r: a plinth course, plaster above, log posts on the diagonals."""
    h = h or P.wall_h
    cells = octagon(cx, cz, r, cut)
    ring = boundary(cells)
    for (x, z) in sorted(cells):
        b.set(x, y0, z, pick(rng, P.base) if (x, z) in ring else B(P.floor))
        for y in range(1, h + 1):
            if (x, z) not in ring:
                b.set(x, y0 + y, z, AIR)
                continue
            angle = abs(x - cx) != r and abs(z - cz) != r    # posts on the diagonals, plaster on the flats
            if angle:
                b.set(x, y0 + y, z, log(P.F("log")))
            else:
                b.set(x, y0 + y, z, B(P.wall if y > 1 else P.plinth))
    for (x, y, z) in windows:
        b.set(x, y0 + y, z, B(P.glass))
    return cells


def cone(b, P, cx, cz, r, cut, y):
    """A cone of stairs over a round house: octagon rings stepping in from a one-block overhang."""
    k = 0
    while True:
        rr = r + 1 - k
        cells = octagon(cx, cz, rr, cut) if rr > 0 else {(cx, cz)}
        if len(cells) <= 1 or rr <= 0:
            b.set(cx, y + k, cz, slab(P.roof) if P.ridge_slab else B(P.ridge))
            return y + k
        ring = boundary(cells)
        for (x, z) in sorted(cells):
            if (x, z) in ring:
                # face away from the open side (the stair shapes make the corners of the diagonals)
                outs = [d for d, (ox, oz) in (("west", (-1, 0)), ("east", (1, 0)), ("north", (0, -1)),
                                              ("south", (0, 1))) if (x + ox, z + oz) not in cells]
                f = {"west": "east", "east": "west", "north": "south", "south": "north"}[outs[0]]
                b.set(x, y + k, z, stairs(P.roof, f), clip=True)
            else:
                b.set(x, y + k, z, B(P.roof_fill))
        k += 1


# ------------------------------------------------------------------ furniture
TALL = ("lilac", "peony", "rose_bush", "sunflower", "tall_grass", "large_fern")


def flower(b, x, y, z, name):
    """A flower (two blocks for the tall ones)."""
    if name in TALL:
        b.tall_plant(x, y, z, name)
    else:
        b.set(x, y, z, B(name))


def wall_torch(b, x, y, z, facing):
    """A torch on the wall behind (x, y, z), pointing `facing` (into the room)."""
    b.set(x, y, z, B("wall_torch", facing=facing))


def table(b, P, x, y, z, top=None):
    b.set(x, y, z, B(P.W("fence")))
    b.set(x, y + 1, z, B(top or P.W("pressure_plate")))


def chair(b, P, x, y, z, facing):
    """A stair seat whose back is toward `facing`."""
    b.set(x, y, z, stairs(P.wood, facing))


def job(b, x, y, z, prof, facing="south"):
    blk = JOB[prof][0]
    if blk in ("blast_furnace", "smoker", "loom", "stonecutter"):
        b.set(x, y, z, B(blk, facing=facing))
    elif blk == "barrel":
        b.barrel(x, y, z, facing="up")
    elif blk == "lectern":
        b.lectern(x, y, z, facing)
    elif blk == "grindstone":
        b.set(x, y, z, B("grindstone", face="floor", facing=facing))
    else:
        b.set(x, y, z, B(blk))


# ------------------------------------------------------------------ entities
def villager(b, x, y, z, vtype, profession="none", baby=False, zombie=False, yaw=0.0):
    """A villager (or zombie villager) entity as vanilla's village/*/villagers templates store it. As there,
    no VillagerDataFinalized flag: Villager.finalizeSpawn sets the type from the biome it stands in
    (VillagerType.BY_BIOME, which world/ExpanseVillagers.java fills in for the Expanse biomes)."""
    ex = {"Health": nbt.Float(20.0), "HurtTime": nbt.Short(0), "HurtByTimestamp": nbt.Int(0),
          "DeathTime": nbt.Short(0), "AbsorptionAmount": nbt.Float(0.0), "FallFlying": nbt.Byte(0),
          "attributes": nbt.List(nbt.TAG_END, []), "CanPickUpLoot": nbt.Byte(1), "LeftHanded": nbt.Byte(0),
          "PersistenceRequired": nbt.Byte(1 if zombie else 0), "Age": nbt.Int(-21359 if baby else 0),
          "ForcedAge": nbt.Int(0), "Gossips": nbt.List(nbt.TAG_END, []), "Inventory": nbt.List(nbt.TAG_END, []),
          "VillagerData": {"profession": f"minecraft:{profession}", "level": nbt.Int(1),
                           "type": f"minecraft:{vtype}"},
          "Xp": nbt.Int(0), "food_level": nbt.Byte(0), "lastRestock": nbt.Long(0)}
    n = b._entity("zombie_villager" if zombie else "villager", (x + 0.72, y, z + 0.63), (x, y, z), ex, yaw=yaw)
    n["OnGround"] = nbt.Byte(1)
    n["Fire"] = nbt.Short(-1)
    return n


def villager_piece(P, name, profession="none", baby=False, zombie=False):
    """A 1x3x1 template (1x2x1 for a baby): the down-facing 'bottom' jigsaw, air, the villager on top."""
    b = Build(name, 1, 2 if baby else 3, 1, seed=hash_seed(name))
    j_down(b, 0, 0, "minecraft:structure_void")
    for y in range(1, b.size[1]):
        b.set(0, y, 0, AIR)
    villager(b, 0, 1, 0, P.vtype, profession, baby, zombie, yaw=48.8)
    return b


def animal_piece(name, mobs):
    """A 1x3x1 animal template like vanilla's village/common/animals/*: down jigsaw, the animals on top
    (persistent; the game finalizes them, so variants are rolled per placement)."""
    b = Build(name, 1, 3, 1, seed=hash_seed(name))
    j_down(b, 0, 0, "minecraft:structure_void")
    b.set(0, 1, 0, AIR)
    b.set(0, 2, 0, AIR)
    for i, m in enumerate(mobs):
        n = b.mob(0, 1, 0, m, yaw=float(i * 137 % 360))
        p = (0.08 + 0.84 * (i % 2), 1.0, 0.08 + 0.84 * ((i // 2) % 2))
        n["Pos"] = nbt.List(nbt.TAG_DOUBLE, [nbt.Double(v) for v in p])
        b.entities[-1]["pos"] = p
    return b


# ------------------------------------------------------------------ streets
# Vanilla's street lots (village/plains/streets), redrawn: '=' path, '^ v < >' street jigsaw on the box
# edge, 'N S E W' building entrance on the path edge pointing into the lot, 'o' decor jigsaw in the lot.
STREETS = {
    "straight_lane": (4, [
        "......=^=.......",
        "......===.......",
        "......===.......",
        "......===.......",
        "......===...o...",
        "......===.......",
        "......===..o....",
        "....o.===.......",
        "......===.......",
        "......===.......",
        "......===.......",
        "......===.......",
        "......===.......",
        "..o...===..o....",
        "......===.......",
        "......=v=......."]),
    "straight_lot": (4, [
        "=^=.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "==E.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "=v=............."]),
    "straight_lots": (7, [
        "=^=..........",
        "===..........",
        "===..........",
        "==E..........",
        "==E..........",
        "==E..........",
        "==E..........",
        "==E..........",
        "===..........",
        "===..........",
        "=v=.........."]),
    "straight_short": (7, [
        "=^=........",
        "===........",
        "===........",
        "===........",
        "==E........",
        "===........",
        "===........",
        "===........",
        "=v=........"]),
    "straight_long": (3, [
        "=^=.................",
        "===.................",
        "===.................",
        "===.................",
        "===.................",
        "===.................",
        "===.................",
        "==E.................",
        "==E.................",
        "==E.................",
        "==E.................",
        "===.................",
        "===.................",
        "===.................",
        "===.................",
        "===.................",
        "=v=................."]),
    "straight_both": (4, [
        ".......=^=...........",
        ".......===...........",
        ".......==E...........",
        "......W=E............",
        "......W=E............",
        ".......===...........",
        "........===..........",
        "........===..........",
        ".......W==E..........",
        ".......W==E..........",
        "........==E..........",
        "........===..........",
        "........===..........",
        "........===..........",
        ".......W==E..........",
        ".......W==E..........",
        "........===..........",
        "........=v=.........."]),
    "corner": (2, [
        "=^=.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===.............",
        "===......N......",
        "================",
        "===============>",
        ".==============="]),
    "corner_small": (2, [
        "..==",
        ".==>",
        "====",
        "=v=."]),
    "crossroad": (2, [
        ".......=^=......",
        ".....o.===......",
        ".......===....o.",
        ".......===......",
        ".......===......",
        "...o...===.o....",
        ".......===......",
        "================",
        "<==============>",
        "================",
        ".......===......",
        ".......===......",
        ".......===...o..",
        ".......===......",
        ".......===......",
        ".......=v=......"]),
    "crossroad_t": (2, [
        "...=^=..........",
        "...===..........",
        "...===..........",
        "...===..........",
        "...===..........",
        "...===..........",
        "...===..........",
        "==========N=====",
        "<==============>",
        "================",
        "..........===...",
        "........o.===...",
        "..........===...",
        "...o......===...",
        "..........===...",
        "..........=v=..."]),
    "cross_small": (2, [
        ".=^=.",
        "=====",
        "<===>",
        "=====",
        ".=v=."]),
    "t_small": (2, [
        ".=^=",
        "====",
        "<===",
        "====",
        ".=v="]),
    "turn": (3, [
        "....=^=...........",
        ".....===..........",
        "......===.........",
        ".......===........",
        ".......==E........",
        ".......===........",
        "......W===........",
        ".......=v=........"]),
}
TERMINATORS = {"end_a": ["==", "=>", "=="], "end_b": ["=", ">", "="]}
ARROW = {"^": "north", "v": "south", "<": "west", ">": "east"}
LETTER = {"N": "north", "S": "south", "E": "east", "W": "west"}


def street(P, name, rows, terminator=False):
    sx, sz = max(len(r) for r in rows), len(rows)
    b = Build(name, sx, 3, sz, seed=hash_seed(name))
    for z, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in "=^v<>NSEW":
                b.set(x, 0, z, B(P.path))
                b.set(x, 1, z, AIR)
                b.set(x, 2, z, AIR)
            if c in ARROW:
                b.jigsaw(x, 1, z, ARROW[c], name=J_STREET, target=J_STREET,
                         pool="minecraft:empty" if terminator else P.pool("streets"), final="minecraft:air",
                         joint="aligned")
            elif c in LETTER:
                b.jigsaw(x, 1, z, LETTER[c], name=J_ENTRANCE, target=J_ENTRANCE, pool=P.pool("houses"),
                         final="minecraft:air", joint="aligned")
            elif c == "o":
                j_up(b, x, 0, z, P.pool("decor"), state_str(P.ground))
    return b


# ------------------------------------------------------------------ town centres
def plaza(b, P, rng, cx, cz, r, pave=None):
    """Paving round a centre with a ragged edge, at the ground course, the air above it cleared."""
    for x in range(b.size[0]):
        for z in range(b.size[2]):
            d = max(abs(x - cx), abs(z - cz)) + 0.5 * min(abs(x - cx), abs(z - cz))
            if d <= r or (d <= r + 1 and rng.random() < 0.5):
                b.set(x, 0, z, B(pave or P.path))
                for y in (1, 2):
                    if b.get(x, y, z) is None:
                        b.set(x, y, z, AIR)


def centre_jigsaws(b, P, cells):
    """Street jigsaws (y=1) in the middle of the four box edges, and the villagers, golem and cats."""
    sx, _, sz = b.size
    mx, mz = sx // 2, sz // 2
    for (x, z, f) in ((mx, 0, "north"), (mx, sz - 1, "south"), (0, mz, "west"), (sx - 1, mz, "east")):
        for dx, dz in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):     # path to every edge
            xx, zz = x + dx, z + dz
            if 0 <= xx < sx and 0 <= zz < sz:
                b.set(xx, 0, zz, B(P.path))
                b.set(xx, 1, zz, AIR)
        b.jigsaw(x, 1, z, f, name=J_STREET, target=J_STREET, pool=P.pool("streets"), final="minecraft:air",
                 joint="aligned")
    for kind, (x, z) in cells:
        under = state_str(b.get(x, 0, z) or B(P.path))
        pool = {"villager": P.pool("villagers"), "golem": V_GOLEM, "cat": V_CATS}[kind]
        j_up(b, x, 0, z, pool, under)


def bell_frame(b, P, x, z, y=1):
    """A bell hung under a little gabled frame on two posts (attachment: ceiling)."""
    for zz in (z - 1, z + 1):
        for yy in range(y, y + 3):
            b.set(x, yy, zz, log(P.F("log")))
    for zz in (z - 1, z, z + 1):
        b.set(x, y + 3, zz, log(P.F("log"), "z"))
    b.set(x, y + 4, z - 1, stairs(P.roof, "south"))
    b.set(x, y + 4, z + 1, stairs(P.roof, "north"))
    b.set(x, y + 4, z, slab(P.roof))
    b.set(x, y + 3, z - 2, stairs(P.roof, "north", "top"))
    b.set(x, y + 3, z + 2, stairs(P.roof, "south", "top"))
    b.bell(x, y + 2, z, attachment="ceiling", facing="east")
    b.set(x, y + 1, z, AIR)
    b.set(x, y, z, AIR)


def lamp_post(b, P, x, z, y=1, h=2):
    for k in range(h):
        b.set(x, y + k, z, B(P.W("fence")))
    b.set(x, y + h, z, B("lantern"))


def centre_green(P, name):
    """Town centre: a paved square, the bell under its frame on a stone dais, benches facing it, lamps."""
    b = Build(name, 11, 7, 11, seed=hash_seed(name))
    rng = b.rng
    plaza(b, P, rng, 5, 5, 4.6)
    for x in range(3, 8):
        for z in range(3, 8):
            b.set(x, 0, z, pick(rng, P.base) if x in (3, 7) or z in (3, 7) else B(P.path))
            for y in range(1, 6):
                b.set(x, y, z, AIR)
    bell_frame(b, P, 5, 5)
    for (x, z, f) in ((3, 4, "west"), (3, 6, "west"), (7, 4, "east"), (7, 6, "east")):
        b.set(x, 1, z, stairs(P.wood, f))
    lamp_post(b, P, 2, 2)
    lamp_post(b, P, 8, 8)
    b.set(2, 1, 8, B("hay_block", axis="x"))
    b.set(2, 1, 7, B("hay_block", axis="y"))
    b.set(8, 1, 2, B("hay_block", axis="z"))
    centre_jigsaws(b, P, [("villager", (4, 1)), ("villager", (9, 6)), ("villager", (1, 4)), ("golem", (6, 9)),
                          ("cat", (4, 8)), ("cat", (8, 4))])
    return b


def centre_well(P, name):
    """Town centre: a roofed well with the bell beside it on a stone (as plains_meeting_point_1)."""
    b = Build(name, 11, 7, 11, seed=hash_seed(name))
    rng = b.rng
    plaza(b, P, rng, 5, 5, 4.4)
    for x in range(4, 7):
        for z in range(4, 7):
            for y in range(1, 6):
                b.set(x, y, z, AIR)
            if (x, z) == (5, 5):
                b.set(x, 0, z, B("water"))
                b.set(x, 1, z, B("water"))
            else:
                b.set(x, 0, z, pick(rng, P.base))
                b.set(x, 1, z, pick(rng, P.base))
    for (x, z) in ((4, 4), (6, 6), (4, 6), (6, 4)):
        b.set(x, 2, z, B(P.W("fence")))
        b.set(x, 3, z, B(P.W("fence")))
    hip(b, P, 4, 4, 6, 6, 4)
    b.set(5, 3, 5, B("iron_chain", axis="y"))
    b.set(8, 1, 5, pick(rng, P.base))
    b.bell(8, 2, 5, attachment="floor", facing="west")
    b.set(8, 3, 5, AIR)
    lamp_post(b, P, 2, 2)
    lamp_post(b, P, 8, 8)
    centre_jigsaws(b, P, [("villager", (3, 2)), ("villager", (7, 8)), ("villager", (2, 7)), ("golem", (8, 2)),
                          ("cat", (2, 5)), ("cat", (5, 8))])
    return b


# ------------------------------------------------------------------ houses
def house_build(name, wx, wz, front=1, side=1, back=1, height=16):
    """A Build whose walls stand at x front..front+wx-1, z side..side+wz-1: (b, x0, z0, x1, z1)."""
    b = Build(name, front + wx + back, height, side + wz + side, seed=hash_seed(name))
    return b, front, side, front + wx - 1, side + wz - 1


def trim(b):
    """Shrink the template to the layers used."""
    top = max([p[1] for p in b.cells] + [int(e["pos"][1]) for e in b.entities] + [0])
    b.size = (b.size[0], top + 1, b.size[2])
    return b


def finish(b, P, door_x, door_z, villagers=(), step=None, y=0):
    """The entrance jigsaw before the door (its final state is the step) and villagers in the floor
    (each needs the two cells above it free: the villager template is air there)."""
    j_entrance(b, door_x - 1, y, door_z, step or step_state(P))
    for (x, z) in villagers:
        j_up(b, x, y, z, P.pool("villagers"), state_str(b.get(x, y, z)))
    return trim(b)


def small_house(P, name, variant=0):
    """A one-bed cottage, 5x5, 6x5 or 5x6 walls (vanilla plains_small_house_*)."""
    if P.round_houses and variant == 0:
        return round_house(P, name, 3, 2, beds=1)
    wx, wz = ((5, 5), (6, 5), (5, 6))[variant % 3]
    b, x0, z0, x1, z1 = house_build(name, wx, wz)
    rng = b.rng
    h = P.wall_h
    dz = (z0 + z1) // 2
    mx = (x0 + x1 + 1) // 2
    shell(b, P, x0, z0, x1, z1, rng, dz, windows=[(mx, 2, z0), (mx, 2, z1), (x1, 2, dz)])
    if variant % 3 == 0:                            # bed along the side, crafting table in the corner
        b.bed(x1 - 2, 1, z0 + 1, "east", P.beds[0])
        b.set(x1 - 1, 1, z1 - 1, B("crafting_table"))
        b.set(x0 + 1, 1, z1 - 1, B(P.pot))
    elif variant % 3 == 1:
        b.bed(x1 - 1, 1, z1 - 2, "south", P.beds[1 % len(P.beds)])
        b.set(x1 - 1, 1, z0 + 1, B("crafting_table"))
        b.chest(x1 - 2, 1, z0 + 1, "south", P.loot)
        table(b, P, x0 + 1, 1, z1 - 1)
    else:
        b.bed(x0 + 1, 1, z1 - 2, "south", P.beds[2 % len(P.beds)])
        b.set(x1 - 1, 1, z1 - 1, B("crafting_table"))
        b.set(x1 - 1, 1, z0 + 1, B("furnace", facing="west"))
        b.set(x0 + 1, 1, z0 + 1, B(P.pot))
    wall_torch(b, x1 - 1, h, dz, "west")
    if P.stilts:
        return stilt_house(b, P, x0, z0, x1, z1, dz, [(x0 + 2, dz)])
    roof(b, P, x0, z0, x1, z1, h + 1)
    return finish(b, P, x0, dz, villagers=[(x0 + 2, dz)])


def medium_house(P, name):
    """Two beds, a table and chairs, a chest and a furnace (vanilla plains_medium_house_*): 7x7 walls."""
    if P.round_houses:
        return round_house(P, name, 4, 3, beds=2)
    h = P.wall_h + 1
    b, x0, z0, x1, z1 = house_build(name, 7, 7)
    rng = b.rng
    dz = z0 + 2
    win = [(x, y, z) for x in (x0 + 2, x0 + 4) for z in (z0, z1) for y in (2, 3)]
    win += [(x1, y, z) for z in (z0 + 2, z0 + 4) for y in (2, 3)] + [(x0, 2, z0 + 4), (x0, 3, z0 + 4)]
    shell(b, P, x0, z0, x1, z1, rng, dz, h=h, windows=win)
    b.bed(x1 - 2, 1, z1 - 1, "east", P.beds[0])
    b.bed(x1 - 2, 1, z1 - 3, "east", P.beds[1 % len(P.beds)])
    table(b, P, x0 + 2, 1, z1 - 1)
    chair(b, P, x0 + 1, 1, z1 - 1, "west")
    chair(b, P, x0 + 3, 1, z1 - 1, "east")
    b.set(x1 - 1, 1, z0 + 1, B("crafting_table"))
    b.set(x1 - 2, 1, z0 + 1, B("furnace", facing="south"))
    b.chest(x1 - 3, 1, z0 + 1, "south", P.loot)
    b.set(x0 + 1, 1, z0 + 1, B(P.pot))
    wall_torch(b, x1 - 1, h - 1, z0 + 3, "west")
    wall_torch(b, x0 + 1, h - 1, z0 + 3, "east")
    if P.stilts:
        return stilt_house(b, P, x0, z0, x1, z1, dz, [(x0 + 3, dz), (x0 + 2, z0 + 3)], h=h)
    roof(b, P, x0, z0, x1, z1, h + 1)
    return finish(b, P, x0, dz, villagers=[(x0 + 3, dz), (x0 + 2, z0 + 3)])


def big_house(P, name):
    """Three beds under one long roof (vanilla plains_big_house_1): 9x7 walls, ridge along x."""
    h = P.wall_h + 1
    b, x0, z0, x1, z1 = house_build(name, 9, 7)
    rng = b.rng
    dz = z0 + 3
    win = [(x, y, z) for x in (x0 + 2, x0 + 4, x0 + 6) for z in (z0, z1) for y in (2, 3)]
    win += [(x1, 2, z0 + 2), (x1, 2, z0 + 4), (x0, 2, z0 + 1), (x0, 2, z0 + 5)]
    shell(b, P, x0, z0, x1, z1, rng, dz, h=h, windows=win)
    b.bed(x1 - 2, 1, z0 + 1, "east", P.beds[0])
    b.bed(x1 - 2, 1, z1 - 1, "east", P.beds[1 % len(P.beds)])
    b.bed(x0 + 4, 1, z1 - 1, "east", P.beds[2 % len(P.beds)])
    b.set(x1 - 1, 1, z0 + 3, B("crafting_table"))
    b.chest(x0 + 1, 1, z0 + 1, "east", P.loot)
    b.set(x0 + 2, 1, z0 + 1, B("furnace", facing="south"))
    table(b, P, x0 + 4, 1, z0 + 2)
    chair(b, P, x0 + 4, 1, z0 + 1, "north")
    b.set(x0 + 1, 1, z1 - 1, B(P.pot))
    wall_torch(b, x0 + 1, h - 1, z0 + 3, "east")
    wall_torch(b, x1 - 1, h - 1, z0 + 3, "west")
    wall_torch(b, x0 + 3, h - 1, z1 - 1, "north")
    if P.stilts:
        return stilt_house(b, P, x0, z0, x1, z1, dz, [(x0 + 3, dz), (x0 + 5, dz), (x0 + 7, dz)], h=h, axis="x")
    roof(b, P, x0, z0, x1, z1, h + 1, axis="x")
    return finish(b, P, x0, dz, villagers=[(x0 + 3, dz), (x0 + 5, dz), (x0 + 7, dz)])


def stilt_house(b, P, x0, z0, x1, z1, dz, villagers, h=None, axis=None):
    """Lift a finished room `P.stilts` blocks onto log stilts with a stair up to the door: the room is
    moved up and a flight of steps runs from the entrance jigsaw to the door."""
    s = P.stilts
    h = h or P.wall_h
    cells = sorted(b.cells.items(), key=lambda kv: -kv[0][1])
    b.cells = {}
    for (x, y, z), v in cells:
        b.cells[(x + s, y + s, z)] = v
    ents = []
    for e in b.entities:
        e = dict(e)
        e["pos"] = (e["pos"][0] + s, e["pos"][1] + s, e["pos"][2])
        e["block"] = (e["block"][0] + s, e["block"][1] + s, e["block"][2])
        ents.append(e)
    b.entities = ents
    b.size = (b.size[0] + s, b.size[1] + s, b.size[2])
    x0, x1 = x0 + s, x1 + s
    stilts(b, P, x0, z0, x1, z1, s)
    for k in range(1, s + 1):                       # the flight of steps up to the door, on a plank base
        x = x0 - s - 1 + k
        b.set(x, k, dz, stairs(P.wood, "east"))
        for y in range(0, k):
            b.set(x, y, dz, B(P.W("planks")))
        for y in (k + 1, k + 2):
            b.set(x, y, dz, AIR)
        for zz in (dz - 1, dz + 1):                 # handrail posts
            for y in range(0, k + 2):
                b.set(x, y, zz, B(P.W("fence")))
    roof(b, P, x0, z0, x1, z1, s + h + 1, axis=axis)
    j_entrance(b, x0 - s - 1, 0, dz, step_state(P))
    for (x, z) in villagers:
        j_up(b, x + s, s, z, P.pool("villagers"), state_str(b.get(x + s, s, z)))
    return trim(b)


def stilts(b, P, x0, z0, x1, z1, top, every=2):
    """Log posts from the ground up to the floor at y=top: the corners and every `every` cells."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            corner = x in (x0, x1) and z in (z0, z1)
            side = (x in (x0, x1) and (z - z0) % every == 0) or (z in (z0, z1) and (x - x0) % every == 0)
            if corner or side:
                for y in range(0, top):
                    b.set(x, y, z, log(P.F("log")))


def round_house(P, name, r, cut, beds=1):
    """A round house (the steppe's): octagonal plaster walls with log posts, a cone roof."""
    size = 2 * r + 3
    c = r + 1
    b = Build(name, size, 16, size, seed=hash_seed(name))
    rng = b.rng
    h = P.wall_h
    win = [(c, 2, c - r), (c, 2, c + r), (c + r, 2, c)]
    round_shell(b, P, c, c, r, cut, rng, windows=win)
    x0 = c - r
    b.door(x0, 1, c, P.wood, "east", hinge="right")
    for tz in (c - 1, c + 1):
        b.set(x0 - 1, 2, tz, B("wall_torch", facing="west"))
    if beds == 1:
        b.bed(c, 1, c + r - 2, "south", P.beds[0])
        b.set(c + 1, 1, c - r + 1, B("crafting_table"))
        b.set(c - 1, 1, c - r + 1, B(P.pot))
        vill = [(c - 1, c)]
    else:
        b.bed(c + 1, 1, c + 2, "east", P.beds[0])
        b.bed(c + 1, 1, c - 2, "east", P.beds[1 % len(P.beds)])
        b.set(c + r - 1, 1, c, B("crafting_table"))
        b.chest(c, 1, c - r + 1, "south", P.loot)
        b.set(c + 1, 1, c - r + 1, B("furnace", facing="south"))
        table(b, P, c - 1, 1, c + r - 1)
        b.set(c - 2, 1, c + r - 2, B(P.pot))
        vill = [(c - 1, c), (c + 1, c)]
    wall_torch(b, c + r - 1, h, c, "west")
    cone(b, P, c, c, r, cut, h + 1)
    return finish(b, P, x0, c, villagers=vill)


# ---- workshops: one shell, the job site and the trade's things
def workshop(P, name, prof):
    """A profession house: a 6x5 room (7x5 for the trades that need a bench and an anvil) with the
    job-site block, the trade's vanilla chest and some of its tools."""
    big = prof in ("armorer", "weaponsmith", "toolsmith", "mason", "butcher")
    wx, wz = (7, 5) if big else (6, 5)
    b, x0, z0, x1, z1 = house_build(name, wx, wz)
    rng = b.rng
    h = P.wall_h
    dz = z0 + 2
    stone = prof in ("armorer", "weaponsmith", "toolsmith", "mason")
    win = [(x0 + 2, 2, z0), (x0 + 2, 2, z1), (x1, 2, dz)] + ([(x0 + 4, 2, z0), (x0 + 4, 2, z1)] if wx == 7 else [])
    shell(b, P, x0, z0, x1, z1, rng, dz, windows=win, floor=P.base[0] if stone else None)
    blk, loot = JOB[prof]
    job(b, x1 - 1, 1, z0 + 1, prof, facing="west" if blk == "lectern" else "south")
    if loot:
        b.chest(x1 - 1, 1, z1 - 1, "north", VLOOT + loot)
    TRADE[prof](b, P, x0, z0, x1, z1)
    wall_torch(b, x1 - 1, h, dz, "west")
    if P.stilts and prof == "fisherman":
        return stilt_house(b, P, x0, z0, x1, z1, dz, [])
    roof(b, P, x0, z0, x1, z1, h + 1)
    return finish(b, P, x0, dz)


def _anvil(b, P, x0, z0, x1, z1):
    b.set(x1 - 2, 1, z0 + 1, B("anvil", facing="east"))


def _armorer(b, P, x0, z0, x1, z1):
    _anvil(b, P, x0, z0, x1, z1)
    b.armor_stand(x0 + 1, 1, z1 - 1, yaw=135.0, equipment={"chest": "iron_chestplate", "head": "iron_helmet"})


def _weaponsmith(b, P, x0, z0, x1, z1):
    _anvil(b, P, x0, z0, x1, z1)
    table(b, P, x0 + 1, 1, z1 - 1)


def _toolsmith(b, P, x0, z0, x1, z1):
    b.set(x1 - 2, 1, z0 + 1, B("crafting_table"))
    b.set(x0 + 1, 1, z1 - 1, B("furnace", facing="east"))


def _mason(b, P, x0, z0, x1, z1):
    b.set(x1 - 2, 1, z0 + 1, B("clay"))
    b.set(x0 + 1, 1, z1 - 1, B("stone_bricks"))
    b.set(x0 + 2, 1, z1 - 1, B("clay"))


def _butcher(b, P, x0, z0, x1, z1):
    b.set(x1 - 2, 1, z0 + 1, B("hay_block", axis="y"))
    table(b, P, x0 + 1, 1, z1 - 1)


def _fletcher(b, P, x0, z0, x1, z1):
    b.set(x0 + 1, 1, z1 - 1, B("crafting_table"))
    b.set(x1 - 2, 1, z0 + 1, B("hay_block", axis="x"))


def _cartographer(b, P, x0, z0, x1, z1):
    table(b, P, x0 + 1, 1, z1 - 1, top=f"{P.carpet}_carpet")
    b.set(x1 - 2, 1, z0 + 1, B("bookshelf"))


def _shepherd(b, P, x0, z0, x1, z1):
    b.set(x0 + 1, 1, z1 - 1, B("white_wool"))
    b.set(x1 - 2, 1, z0 + 1, B(f"{P.carpet}_wool"))


def _tannery(b, P, x0, z0, x1, z1):
    b.set(x0 + 1, 1, z1 - 1, B("hay_block", axis="y"))
    b.set(x1 - 2, 1, z0 + 1, B("crafting_table"))


def _fisher(b, P, x0, z0, x1, z1):
    b.set(x0 + 1, 1, z1 - 1, B("crafting_table"))
    b.set(x1 - 2, 1, z0 + 1, B("dried_kelp_block"))


TRADE = {"armorer": _armorer, "weaponsmith": _weaponsmith, "toolsmith": _toolsmith, "mason": _mason,
         "butcher": _butcher, "fletcher": _fletcher, "cartographer": _cartographer, "shepherd": _shepherd,
         "leatherworker": _tannery, "fisherman": _fisher}


def temple(P, name):
    """The cleric's chapel (vanilla plains_temple_*): a hall entered from the street side, taller walls and
    coloured glass, the brewing stand on the far wall facing the door, pews either side of the aisle."""
    h = P.wall_h + 2
    b, x0, z0, x1, z1 = house_build(name, 5, 9)
    rng = b.rng
    dz = z0 + 4
    win = [(x0 + 2, y, z) for z in (z0, z1) for y in (2, 3)] + [(x0, y, z) for z in (z0 + 2, z1 - 2) for y in (2, 3)]
    win += [(x1, y, z) for z in (z0 + 2, z1 - 2) for y in (2, 3, 4)]
    shell(b, P, x0, z0, x1, z1, rng, dz, h=h, windows=win, floor=P.base[0])
    for (x, y, z) in win:
        b.set(x, y, z, B(P.chapel_glass))
    job(b, x1 - 1, 1, dz, "cleric")
    for z in (dz - 1, dz + 1):
        b.set(x1 - 1, 1, z, B("candle", candles=3))
    b.chest(x1 - 1, 1, z1 - 1, "west", VLOOT + "temple")
    for x in (x0 + 1, x0 + 2):                      # pews facing the altar
        for z in (dz - 2, dz - 3, dz + 2, dz + 3):
            if z0 < z < z1:
                chair(b, P, x, 1, z, "west")
    for (x, z, f) in ((x0 + 1, z0 + 1, "east"), (x0 + 1, z1 - 1, "east"), (x1 - 1, z0 + 1, "west"),
                      (x1 - 1, z1 - 1, "west")):
        wall_torch(b, x, 3, z, f)
    roof(b, P, x0, z0, x1, z1, h + 1, axis="z")
    return finish(b, P, x0, dz)


def library(P, name):
    """The librarian's house (vanilla plains_library_*): shelves round the walls, the lectern facing the
    door, a reading table, tall windows above the shelves."""
    h = P.wall_h + 2
    b, x0, z0, x1, z1 = house_build(name, 7, 9)
    rng = b.rng
    dz = z0 + 4
    win = [(x0, y, z) for z in (z0 + 2, z1 - 2) for y in (2, 3)] + [(x1, y, z) for z in (z0 + 2, z1 - 2)
                                                                     for y in (3, 4)]
    win += [(x0 + 3, y, z) for z in (z0, z1) for y in (3, 4)]
    shell(b, P, x0, z0, x1, z1, rng, dz, h=h, windows=win)
    for z in range(z0 + 1, z1):                     # shelves along the back wall and the sides
        if z != dz:
            for y in (1, 2):
                b.set(x1 - 1, y, z, B("bookshelf"))
    for x in (x0 + 2, x0 + 3, x0 + 4):
        for y in (1, 2):
            b.set(x, y, z0 + 1, B("bookshelf"))
            b.set(x, y, z1 - 1, B("bookshelf"))
    job(b, x1 - 2, 1, dz, "librarian", facing="west")
    table(b, P, x0 + 3, 1, dz + 2, top=f"{P.carpet}_carpet")
    chair(b, P, x0 + 3, 1, dz + 1, "north")
    b.set(x0 + 1, 1, z0 + 1, B(P.pot))
    for (x, z, f) in ((x0 + 1, z0 + 1, "east"), (x0 + 1, z1 - 1, "east"), (x1 - 1, dz - 1, "west"),
                      (x1 - 1, dz + 1, "west")):
        wall_torch(b, x, 4, z, f)
    roof(b, P, x0, z0, x1, z1, h + 1, axis="z")
    return finish(b, P, x0, dz)


# ---- farms, pens, the second meeting point
def farm(P, name, beds=2, length=7):
    """A crop farm as vanilla plains_small_farm_1 / large_farm_1: a log frame round farmland and water
    channels, raised one block above the path, wheat (the farm_ processor turns some of it into the
    style's other crops) and a composter for the farmer."""
    sx, sz = 3 * beds + 1, length + 2
    b = Build(name, sx, 3, sz, seed=hash_seed(name))
    for x in range(sx):
        for z in range(sz):
            if x in (0, sx - 1) or z in (0, sz - 1):
                b.set(x, 0, z, log(P.F("log"), "z" if x in (0, sx - 1) and z not in (0, sz - 1) else "x"))
            elif x % 3 == 0:
                b.set(x, 0, z, B("water"))
                b.set(x, 1, z, AIR)
            else:
                b.set(x, 0, z, B("farmland", moisture=7))
                b.set(x, 1, z, B("wheat", age=7))
            if b.get(x, 1, z) is None:
                b.set(x, 1, z, AIR)
            b.set(x, 2, z, AIR)
    b.set(sx - 2, 0, sz - 2, B("dirt"))
    b.set(sx - 2, 1, sz - 2, B("composter"))
    j_entrance(b, 0, 0, sz // 2, state_str(log(P.F("log"), "z")))
    return b


def pen(P, name, sx=6, sz=7, pool=None, animals=3, wall=None):
    """An animal pen (vanilla plains_animal_pen_*): a fence (or dry-stone wall) round grass at ground
    level, a gate on the path side, animals from vanilla's village/common pools, a trough, a hay bale."""
    b = Build(name, sx, 3, sz, seed=hash_seed(name))
    rng = b.rng
    gz = sz // 2
    fence = B(wall or P.W("fence"))
    for x in range(sx):
        for z in range(sz):
            b.set(x, 0, z, B(P.ground))
            b.set(x, 1, z, fence if x in (0, sx - 1) or z in (0, sz - 1) else AIR)
            b.set(x, 2, z, AIR)
    b.set(sx - 2, 0, sz - 2, B("water"))
    b.set(sx - 2, 1, 1, B("hay_block", axis="y"))
    for (x, z) in ((sx - 1, 0), (0, sz - 1)):
        b.set(x, 2, z, B("torch"))
    spots = [(x, z) for x in range(1, sx - 2) for z in range(1, sz - 1) if (x, z) != (1, gz)]
    rng.shuffle(spots)
    for (x, z) in sorted(spots[:animals]):
        j_up(b, x, 0, z, pool or P.animals, state_str(P.ground))
    j_entrance(b, 0, 1, gz, state_str(B(P.W("fence_gate"), facing="east")))
    return b


def meeting_point(P, name):
    """A second, smaller meeting point in the house pool (vanilla plains_meeting_point_4/5): a bell on a
    stone in a little paved garden with benches and flowers."""
    b = Build(name, 9, 4, 9, seed=hash_seed(name))
    rng = b.rng
    for x in range(9):
        for z in range(9):
            d = abs(x - 4) + abs(z - 4)
            if d <= 5:
                b.set(x, 0, z, B(P.path) if d <= 3 or rng.random() < 0.5 else B(P.ground))
                for y in (1, 2, 3):
                    b.set(x, y, z, AIR)
    b.set(4, 1, 4, pick(rng, P.base))
    b.bell(4, 2, 4, attachment="floor", facing="north")
    for (x, z, f) in ((2, 4, "west"), (6, 4, "east")):
        b.set(x, 1, z, stairs(P.wood, f))
    lamp_post(b, P, 4, 1)
    lamp_post(b, P, 4, 7)
    for (x, z) in ((1, 3), (7, 5), (3, 7), (5, 1)):
        b.set(x, 0, z, B(P.ground))
        flower(b, x, 1, z, rng.choice(P.flowers))
    b.set(0, 0, 4, B(P.path))
    j_entrance(b, 0, 1, 4, "minecraft:air")
    return b


# ---- decor
def lamp(P, name):
    """A lamp post (vanilla plains_lamp_1): the down jigsaw becomes the post's foot."""
    b = Build(name, 3, 4, 3, seed=hash_seed(name))
    post = B(P.W("fence"))
    j_down(b, 1, 1, state_str(post))
    b.set(1, 1, 1, post)
    if P.lamp == "lantern":
        b.set(1, 2, 1, post)
        b.set(1, 3, 1, B("lantern"))
    else:
        b.set(1, 2, 1, log(P.F("stripped_log")))
        for (x, z, f) in ((0, 1, "west"), (2, 1, "east"), (1, 0, "north"), (1, 2, "south")):
            b.set(x, 2, z, B("wall_torch", facing=f))
    return trim(b)


def decor(name, size, dress, foot="minecraft:air"):
    """A decor template: dress(b, cx, cz) builds it on layer 1+; the down jigsaw at (cx, 0, cz) becomes
    `foot` (decor stands on the lot's ground, one above the path)."""
    b = Build(name, *size, seed=hash_seed(name))
    cx, cz = size[0] // 2, size[2] // 2
    dress(b, cx, cz)
    j_down(b, cx, cz, foot)
    return trim(b)


# ------------------------------------------------------------------ zombie twins
def zombie_twin(src, P):
    """A copy of a template whose jigsaws point at the zombie pools; the iron golem's jigsaw is left as
    the block it would have become. None when the template has no such jigsaw."""
    import assemble
    b = Build("z_" + src.name, *src.size, seed=hash_seed("z_" + src.name))
    changed = False
    for pos, (st, n) in src.cells.items():
        if st.id == "minecraft:jigsaw":
            pool = n["pool"]
            if pool == V_GOLEM:
                b.cells[pos] = (assemble.parse_state(n["final_state"]) or AIR, None)
                changed = True
                continue
            for p in ("streets", "houses", "decor", "villagers"):
                if pool == P.pool(p):
                    n = dict(n)
                    n["pool"] = P.pool(p, True)
                    changed = True
        b.cells[pos] = (st, n)
    b.entities = [dict(e) for e in src.entities]
    return b if changed else None


# ------------------------------------------------------------------ processors and loot
def _rule(inp, out, prob=None, loc=None, tag=False):
    from processors import ALWAYS, _state_json
    if tag:
        ip = {"predicate_type": "minecraft:tag_match", "tag": inp}
    elif prob is None:
        ip = {"block": inp if ":" in inp else "minecraft:" + inp, "predicate_type": "minecraft:block_match"}
    else:
        ip = {"block": inp if ":" in inp else "minecraft:" + inp, "predicate_type": "minecraft:random_block_match",
              "probability": prob}
    lp = ALWAYS if loc is None else {"block": loc if ":" in loc else "minecraft:" + loc,
                                     "predicate_type": "minecraft:block_match"}
    return {"input_predicate": ip, "location_predicate": lp, "output_state": _state_json(out)}


def _ns(i):
    return i if ":" in i else "minecraft:" + i


def processor_lists(P):
    """street_ (bridges where a path meets water, as vanilla's street_* lists), farm_ (the crop mix),
    houses_ (a little moss) and zombie_ (vanilla's zombie_* rules) lists for a style."""
    m = P.module
    street = [_rule(P.path, P.bridge, loc="water"), _rule(P.path, P.bridge, loc="ice")]
    if P.path_wear:
        street.append(_rule(P.path, P.ground, P.path_wear))
    street += [_rule(P.path, o, p) for o, p in P.street_mix]
    street += [_rule(P.ground, "water", loc="water")]
    farm_ = [_rule("wheat", B(c, age=3 if c == "beetroots" else 7), p) for c, p in P.crops]
    houses = [_rule("cobblestone", "mossy_cobblestone", 0.1)]
    zombie = [_rule("cobblestone", "mossy_cobblestone", 0.8), _rule("minecraft:doors", "air", tag=True),
              _rule("torch", "air"), _rule("wall_torch", "air"), _rule("lantern", "air"),
              _rule("cobblestone", "cobweb", 0.07), _rule("mossy_cobblestone", "cobweb", 0.07),
              _rule(_ns(P.floor), "cobweb", 0.06), _rule(_ns(P.roof) + "_stairs", "cobweb", 0.06),
              _rule("glass_pane", "cobweb", 0.5)] + farm_

    def plist(rules):
        return {"processors": [{"processor_type": "minecraft:rule", "rules": rules}]}
    return {f"street_{m}": plist(street), f"farm_{m}": plist(farm_), f"houses_{m}": plist(houses),
            f"zombie_{m}": plist(zombie)}


def house_loot(P):
    """The style's house chest, after vanilla's village_<biome>_house tables: bread, a few emeralds and
    nuggets, and the style's own crops and saplings."""
    from loot import empty, item, pool, table
    return table(P.module, pool((3, 8), item("bread", 10, (1, 4)), item("emerald", 2, (1, 4)),
                                item("iron_nugget", 1, (1, 5)),
                                *[item(n, w, c) for n, w, c in P.loot_items],
                                item("book", 1), item("feather", 1), empty(2)))


# ------------------------------------------------------------------ the whole village
def module(P):
    """(NAME, STRUCTURE, DATA, FALLBACKS, pools) for a design module."""
    m = P.module
    structure = dict(biomes=P.biomes, size=6, max_distance=80, set="villages", expansion_hack=True,
                     village=True, avoid_extra=48)
    proc = {k: f"{k}_{m}" for k in ("street", "farm", "houses", "zombie")}
    data = {"worldgen/processor_list": processor_lists(P), "loot_table/chests": {m: house_loot(P)}}
    fallbacks = {p: P.pool("terminators") for p in ("streets", "houses", "z_streets", "z_houses")}

    def pools():
        centres = [centre_green(P, "centre_green"), centre_well(P, "centre_well")]
        extras = P.extras(P) if P.extras else {}
        centres += extras.get("centres", [])
        streets = [(street(P, f"street_{n}", rows), w) for n, (w, rows) in STREETS.items()]
        terms = [street(P, f"end_{n}", rows, terminator=True) for n, rows in TERMINATORS.items()]
        houses = [(small_house(P, "house_small_1", 0), 4, "houses"), (small_house(P, "house_small_2", 1), 4, "houses"),
                  (small_house(P, "house_small_3", 2), 4, "houses"), (medium_house(P, "house_medium"), 3, "houses"),
                  (big_house(P, "house_big"), 2, "houses"), (temple(P, "temple"), 2, "houses"),
                  (library(P, "library"), 4, "houses"),
                  (farm(P, "farm_small", 2, 7), 4, "farm"), (farm(P, "farm_large", 3, 9), 4, "farm"),
                  (pen(P, "pen_animals", 6, 8), 3, None), (meeting_point(P, "meeting_point"), 2, None)]
        houses += [(workshop(P, f"workshop_{p}", p), 1 if p == "cartographer" else 2, "houses")
                   for p in WORKSHOPS if p not in extras.get("replaces", ())]
        houses += extras.get("houses", [])
        decor_ = [(lamp(P, "lamp"), 2)] + extras.get("decor", [])
        vill = [(villager_piece(P, "villager_unemployed"), 10), (villager_piece(P, "villager_nitwit", "nitwit"), 1),
                (villager_piece(P, "villager_baby", baby=True), 1)]
        zvill = [(villager_piece(P, "zombie_unemployed", zombie=True), 10),
                 (villager_piece(P, "zombie_nitwit", "nitwit", zombie=True), 1)]

        def twin(b):
            return zombie_twin(b, P) or b
        out = {
            "start": [Piece(c, 50, proc["houses"]) for c in centres] +
                     [Piece(twin(c), 1, proc["zombie"]) for c in centres],
            "streets": [Piece(s, w, proc["street"], "terrain_matching") for s, w in streets],
            "terminators": [Piece(t, 1, proc["street"], "terrain_matching") for t in terms],
            "houses": [Piece(h, w, proc[p] if p else None) for h, w, p in houses] + [Empty(10)],
            "decor": [Piece(d, w) for d, w in decor_] + [Feature(f, 1) for f in P.features] + [Empty(2)],
            "villagers": [Piece(v, w) for v, w in vill],
            "z_streets": [Piece(twin(s), w, proc["street"], "terrain_matching") for s, w in streets],
            "z_houses": [Piece(twin(h), w, proc["zombie"]) for h, w, p in houses] + [Empty(10)],
            "z_decor": [Piece(twin(d), w, proc["zombie"]) for d, w in decor_] + [Feature(f, 1) for f in P.features]
                       + [Empty(2)],
            "z_villagers": [Piece(v, w) for v, w in zvill],
        }
        out.update(extras.get("pools", {}))
        return out
    return m, structure, data, fallbacks, pools
