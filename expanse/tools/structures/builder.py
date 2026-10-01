"""Block-placing DSL for structure templates.

Coordinates are template-local: x east, y up, z south. A cell that is never
set is *structure void* (the template leaves the world untouched there);
set AIR explicitly to carve. Jigsaw pieces are placed with known shapes (no
neighbour updates), so `finalize()` computes fence/wall/pane connections and
stair corner shapes the way the game would.
"""
import math
import random

import nbt
from blocks import AIR, B

DATA_VERSION = 5023

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
        "up": (0, 1, 0), "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
H4 = ["north", "east", "south", "west"]


def axis_of(d):
    return "x" if d in ("east", "west") else "z" if d in ("north", "south") else "y"


# ---- shape classification ---------------------------------------------------
_NONFULL_SUFFIX = ("_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_door", "_trapdoor", "_button",
                   "_pressure_plate", "_carpet", "_bed", "_banner", "_candle", "glass_pane", "_sapling", "_torch",
                   "_sign", "_skull", "_head", "_cluster")
_NONFULL = {"air", "structure_void", "torch", "wall_torch", "lantern", "soul_lantern", "chest", "trapped_chest",
            "campfire", "soul_campfire", "bell", "lectern", "anvil", "chipped_anvil", "damaged_anvil", "grindstone",
            "stonecutter", "ladder", "vine", "glow_lichen", "snow", "moss_carpet", "cobweb", "iron_bars", "iron_chain",
            "end_rod", "lightning_rod", "decorated_pot", "sea_pickle", "leaf_litter", "pink_petals", "wildflowers",
            "red_mushroom", "brown_mushroom", "sweet_berry_bush", "spanish_moss", "cattail", "flower_pot", "candle",
            "brewing_stand", "cauldron", "water_cauldron", "composter", "hanging_roots", "short_grass", "tall_grass",
            "fern", "large_fern", "dead_bush", "poppy", "dandelion", "cornflower", "azure_bluet", "allium",
            "oxeye_daisy", "blue_orchid", "lily_of_the_valley", "red_tulip", "orange_tulip", "white_tulip",
            "pink_tulip", "lily_pad", "seagrass", "tall_seagrass", "sugar_cane", "heather", "edelweiss", "frostbloom",
            "glowcap", "dirt_path", "farmland", "wheat", "carrots", "beetroots", "firefly_bush", "bush", "kelp",
            "cactus", "pointed_dripstone", "scaffolding", "tripwire_hook", "mangrove_roots", "amethyst_cluster",
            "skeleton_skull", "skeleton_wall_skull", "lily_pad", "sunflower", "lilac", "rose_bush", "peony"}
_CONNECT_EXCEPTION_SUFFIX = ("_leaves",)
_CONNECT_EXCEPTION = {"barrier", "pumpkin", "carved_pumpkin", "jack_o_lantern", "melon"}


def is_full(st):
    if st is None:
        return False
    s = st.short
    if s in _NONFULL or s.startswith("potted_") or s.endswith(_NONFULL_SUFFIX):
        return s.endswith("_slab") and st.get("type") == "double"
    if s == "snow":
        return st.get("layers") == "8"
    return True


def is_fence(st):
    return st is not None and st.short.endswith("_fence")


def is_gate(st):
    return st is not None and st.short.endswith("_fence_gate")


def is_wall(st):
    return st is not None and st.short.endswith("_wall") and "wall_" not in st.short and st.short not in ("wall_torch",)


def is_bars(st):  # IronBarsBlock family: iron bars and all glass panes
    return st is not None and (st.short == "iron_bars" or st.short.endswith("glass_pane"))


def is_stairs(st):
    return st is not None and st.short.endswith("_stairs")


def solid_side(st):
    """Approximation of isFaceSturdy for horizontal faces + connection exceptions."""
    if st is None or not is_full(st):
        return False
    s = st.short
    if s.endswith(_CONNECT_EXCEPTION_SUFFIX) or s in _CONNECT_EXCEPTION:
        return False
    return True


WOODEN = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "pale_oak",
          "crimson", "warped", "redwood", "willow", "palm", "lumen", "baobab", "wisteria")


def wooden_fence(st):
    return any(st.short == w + "_fence" for w in WOODEN)


class Build:
    def __init__(self, name, sx, sy, sz, seed=None):
        self.name = name
        self.size = (sx, sy, sz)
        self.cells = {}
        self.rng = random.Random(seed if seed is not None else sum(map(ord, name)) * 7919)

    # ---- basic access ----
    def inb(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x, y, z, st, nbt_=None, clip=False):
        if isinstance(st, str):
            st = B(st)
        if not self.inb(x, y, z):
            if clip:
                return
            raise IndexError(f"{self.name}: ({x},{y},{z}) outside {self.size} for {st}")
        if st is None:
            self.cells.pop((x, y, z), None)
        else:
            self.cells[(x, y, z)] = (st, nbt_)

    def get(self, x, y, z):
        c = self.cells.get((x, y, z))
        return c[0] if c else None

    def is_air_or_void(self, x, y, z):
        st = self.get(x, y, z)
        return st is None or st.short == "air"

    def setif(self, x, y, z, st, pred):
        if pred(self.get(x, y, z)):
            self.set(x, y, z, st)

    def fill(self, x0, y0, z0, x1, y1, z1, st, clip=False, only_void=False, only_air_or_void=False):
        if isinstance(st, str):
            st = B(st)
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if only_void and (x, y, z) in self.cells:
                        continue
                    if only_air_or_void and not self.is_air_or_void(x, y, z):
                        continue
                    self.set(x, y, z, st, clip=clip)

    def void(self, x0, y0, z0, x1, y1, z1):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.cells.pop((x, y, z), None)

    def air(self, x0, y0, z0, x1, y1, z1, only_void=False):
        self.fill(x0, y0, z0, x1, y1, z1, AIR, only_void=only_void)

    def walls(self, x0, z0, x1, z1, y0, y1, st, corner=None):
        """Perimeter of the rectangle, optionally with distinct corner posts."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                for z in (z0, z1):
                    self.set(x, y, z, st)
            for z in range(z0, z1 + 1):
                for x in (x0, x1):
                    self.set(x, y, z, st)
            if corner is not None:
                for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
                    self.set(x, y, z, corner)

    def disc(self, cx, cz, r, y, st, clip=True, pred=None):
        for x in range(int(math.floor(cx - r)), int(math.ceil(cx + r)) + 1):
            for z in range(int(math.floor(cz - r)), int(math.ceil(cz + r)) + 1):
                if (x - cx) ** 2 + (z - cz) ** 2 <= r * r + 0.01:
                    if pred is None or pred(x, z):
                        self.set(x, y, z, st, clip=clip)

    def ring(self, cx, cz, r_in, r_out, y, st, clip=True):
        for x in range(int(math.floor(cx - r_out)), int(math.ceil(cx + r_out)) + 1):
            for z in range(int(math.floor(cz - r_out)), int(math.ceil(cz + r_out)) + 1):
                d2 = (x - cx) ** 2 + (z - cz) ** 2
                if r_in * r_in - 0.01 <= d2 <= r_out * r_out + 0.01:
                    self.set(x, y, z, st, clip=clip)

    def column(self, x, z, y0, y1, st):
        for y in range(y0, y1 + 1):
            self.set(x, y, z, st)

    def replace(self, frm, to, prob=1.0, region=None):
        """Replace block ids (string or BlockState); `to` may be a callable(rng, old)->state."""
        frm_ids = {f if ":" in f else "minecraft:" + f for f in ([frm] if isinstance(frm, str) else frm)}
        for pos, (st, n) in sorted(self.cells.items()):
            if st.id in frm_ids and (region is None or region(*pos)) and self.rng.random() < prob:
                new = to(self.rng, st) if callable(to) else (B(to) if isinstance(to, str) else to)
                self.cells[pos] = (new, n)

    # ---- furniture helpers ----
    def chest(self, x, y, z, facing, loot):
        self.set(x, y, z, B("chest", facing=facing),
                 {"LootTable": loot, "components": {}, "id": "minecraft:chest"})

    def barrel(self, x, y, z, facing="up", loot=None):
        n = {"LootTable": loot, "components": {}, "id": "minecraft:barrel"} if loot else None
        self.set(x, y, z, B("barrel", facing=facing), n)

    def bed(self, x, y, z, facing, color="red"):
        """(x,y,z) is the FOOT; the head is one block toward `facing`."""
        dx, _, dz = DIRS[facing]
        self.set(x, y, z, B(f"{color}_bed", facing=facing, part="foot"))
        self.set(x + dx, y, z + dz, B(f"{color}_bed", facing=facing, part="head"))

    def door(self, x, y, z, wood, facing, hinge="left", open_=False):
        name = wood + "_door"
        self.set(x, y, z, B(name, facing=facing, half="lower", hinge=hinge, open=open_))
        self.set(x, y + 1, z, B(name, facing=facing, half="upper", hinge=hinge, open=open_))

    def tall_plant(self, x, y, z, name):
        self.set(x, y, z, B(name, half="lower"))
        self.set(x, y + 1, z, B(name, half="upper"))

    def campfire(self, x, y, z, lit=True, facing="north", soul=False):
        self.set(x, y, z, B("soul_campfire" if soul else "campfire", lit=lit, facing=facing),
                 {"components": {}, "Items": nbt.List(nbt.TAG_END, []), "CookingTimes": nbt.IntArray([0, 0, 0, 0]),
                  "CookingTotalTimes": nbt.IntArray([0, 0, 0, 0]),
                  "id": "minecraft:soul_campfire" if soul else "minecraft:campfire"})

    def bell(self, x, y, z, attachment="floor", facing="north"):
        self.set(x, y, z, B("bell", attachment=attachment, facing=facing), {"id": "minecraft:bell"})

    def banner(self, x, y, z, color, patterns=(), wall_facing=None, rotation=0):
        pats = nbt.List(nbt.TAG_COMPOUND, [{"color": c, "pattern": p if ":" in p else "minecraft:" + p}
                                           for p, c in patterns])
        n = {"components": {}, "patterns": pats, "id": "minecraft:banner"}
        if wall_facing:
            self.set(x, y, z, B(f"{color}_wall_banner", facing=wall_facing), n)
        else:
            self.set(x, y, z, B(f"{color}_banner", rotation=rotation), n)

    def hang_moss(self, x, y, z, length):
        """Spanish moss strand hanging down from below (x,y+1,z)."""
        for i in range(length):
            yy = y - i
            if yy < 0 or not self.is_air_or_void(x, yy, z):
                break
            self.set(x, yy, z, B("expanse:spanish_moss", tip=(i == length - 1 or yy == 0)))
        # make sure the last placed piece is a tip
        for i in range(length - 1, -1, -1):
            st = self.get(x, y - i, z)
            if st is not None and st.id == "expanse:spanish_moss":
                self.set(x, y - i, z, B("expanse:spanish_moss", tip=True))
                break

    # ---- finalisation -------------------------------------------------------
    def finalize(self):
        self._connect()
        self._stair_shapes()
        self._check_pairs()

    def _connect(self):
        for (x, y, z), (st, n) in list(self.cells.items()):
            if not (is_fence(st) or is_wall(st) or is_bars(st)):
                continue
            con = {}
            for d in H4:
                dx, _, dz = DIRS[d]
                nb = self.get(x + dx, y, z + dz)
                if nb is None:
                    con[d] = False
                    continue
                if is_fence(st):
                    same = is_fence(nb) and wooden_fence(nb) == wooden_fence(st)
                    gate = is_gate(nb) and axis_of(nb.get("facing")) == axis_of(CW[d])
                    con[d] = solid_side(nb) or same or gate
                elif is_wall(st):
                    gate = is_gate(nb) and axis_of(nb.get("facing")) == axis_of(CW[d])
                    con[d] = is_wall(nb) or solid_side(nb) or is_bars(nb) or gate
                else:
                    con[d] = solid_side(nb) or is_bars(nb) or is_wall(nb)
            if is_wall(st):
                above = self.get(x, y + 1, z)
                covered = above is not None and (is_full(above) or (is_wall(above)))
                sides = {d: ("tall" if covered else "low") if con[d] else "none" for d in H4}
                above_post = above is not None and is_wall(above) and above.get("up") == "true"
                nn, ss, ww, ee = (sides[d] == "none" for d in ("north", "south", "west", "east"))
                corner = (nn and ss and ww and ee) or (nn != ss) or (ww != ee)
                high = (sides["north"] == "tall" and sides["south"] == "tall") or (
                    sides["east"] == "tall" and sides["west"] == "tall")
                post_override = above is not None and (above.short in ("torch", "lantern", "soul_lantern", "campfire")
                                                       or above.short.endswith(("_banner", "_pressure_plate",
                                                                               "_candle", "candle")))
                if above_post or corner:
                    up = True
                elif high:
                    up = False
                else:
                    up = post_override or (above is not None and is_full(above))
                new = st.with_(up=up, **sides)
            else:
                new = st.with_(**con)
            self.cells[(x, y, z)] = (new, n)

    def _stair_shapes(self):
        def st_at(p):
            return self.get(*p)

        def rel(p, d):
            dx, dy, dz = DIRS[d]
            return (p[0] + dx, p[1] + dy, p[2] + dz)

        def can_take(state, pos, nd):
            nbs = st_at(rel(pos, nd))
            return not is_stairs(nbs) or nbs.get("facing") != state.get("facing") or nbs.get("half") != state.get("half")

        updates = {}
        for pos, (st, n) in self.cells.items():
            if not is_stairs(st):
                continue
            f = st.get("facing")
            shape = "straight"
            behind = st_at(rel(pos, f))
            if is_stairs(behind) and behind.get("half") == st.get("half"):
                bf = behind.get("facing")
                if axis_of(bf) != axis_of(f) and can_take(st, pos, OPP[bf]):
                    shape = "outer_left" if bf == CCW[f] else "outer_right"
            if shape == "straight":
                front = st_at(rel(pos, OPP[f]))
                if is_stairs(front) and front.get("half") == st.get("half"):
                    ff = front.get("facing")
                    if axis_of(ff) != axis_of(f) and can_take(st, pos, ff):
                        shape = "inner_left" if ff == CCW[f] else "inner_right"
            updates[pos] = (st.with_(shape=shape), n)
        self.cells.update(updates)

    def _check_pairs(self):
        for (x, y, z), (st, _) in self.cells.items():
            s = st.short
            if s.endswith("_door") or st.get("half") in ("upper", "lower"):
                other = (x, y + 1, z) if st.get("half") == "lower" else (x, y - 1, z)
                o = self.get(*other)
                if o is None or o.id != st.id:
                    raise ValueError(f"{self.name}: unpaired double block {st} at {(x, y, z)}")
            if s.endswith("_bed"):
                dx, _, dz = DIRS[st.get("facing")]
                sign = 1 if st.get("part") == "foot" else -1
                o = self.get(x + sign * dx, y, z + sign * dz)
                if o is None or o.id != st.id:
                    raise ValueError(f"{self.name}: unpaired bed at {(x, y, z)}")

    # ---- output -------------------------------------------------------------
    def to_nbt(self):
        palette, index = [], {}
        full, other, ents = [], [], []
        for pos in sorted(self.cells, key=lambda p: (p[1], p[0], p[2])):
            st, n = self.cells[pos]
            if st not in index:
                index[st] = len(palette)
                entry = {"id": st.id}
                if st.props:
                    entry["properties"] = dict(st.props)
                palette.append(entry)
            rec = {"pos": nbt.List(nbt.TAG_INT, [nbt.Int(v) for v in pos]), "state": nbt.Int(index[st])}
            if n is not None:
                rec["nbt"] = n
                ents.append(rec)
            elif is_full(st):
                full.append(rec)
            else:
                other.append(rec)
        return {
            "size": nbt.List(nbt.TAG_INT, [nbt.Int(v) for v in self.size]),
            "entities": nbt.List(nbt.TAG_END, []),
            "blocks": nbt.List(nbt.TAG_COMPOUND, full + other + ents),
            "palette": nbt.List(nbt.TAG_COMPOUND, palette),
            "DataVersion": nbt.Int(DATA_VERSION),
        }

    def save(self, path):
        nbt.dump(self.to_nbt(), path)


# ---- small conveniences for designs ------------------------------------------
def stairs(mat, facing, half="bottom"):
    return B(f"{mat}_stairs", facing=facing, half=half)


def slab(mat, type_="bottom"):
    return B(f"{mat}_slab", type=type_)


def log(mat, axis="y"):
    return B(f"{mat}_log", axis=axis)


def wood(mat, axis="y"):
    return B(f"{mat}_wood", axis=axis)


def trapdoor(mat, facing, half="bottom", open_=False):
    return B(f"{mat}_trapdoor", facing=facing, half=half, open=open_)


def lantern(hanging=False, soul=False):
    return B("soul_lantern" if soul else "lantern", hanging=hanging)


def floating_report(b):
    """Blocks that touch nothing (or plants without ground) - design smell list."""
    out = []
    need_below = ("_carpet", "_pressure_plate", "_sapling", "_bed", "_banner", "candle", "torch", "lantern")
    plants = {"heather", "edelweiss", "frostbloom", "glowcap", "short_grass", "fern", "poppy", "dandelion",
              "cornflower", "azure_bluet", "allium", "oxeye_daisy", "dead_bush", "sweet_berry_bush", "tall_grass",
              "large_fern", "cattail", "red_mushroom", "brown_mushroom", "lily_of_the_valley", "blue_orchid"}
    for (x, y, z), (st, _) in sorted(b.cells.items()):
        s = st.short
        if s == "air":
            continue
        nbs = [b.get(x + dx, y + dy, z + dz) for dx, dy, dz in DIRS.values()]
        if y > 1 and all(n is None or n.short == "air" for n in nbs):
            out.append(((x, y, z), str(st), "isolated"))
            continue
        below = b.get(x, y - 1, z)
        if y == 1 and below is None:
            continue  # stands on the (beard-levelled) terrain surface
        if s in plants and st.get("half") != "upper" and y > 0 and (below is None or below.short == "air"):
            out.append(((x, y, z), str(st), "plant without ground"))
        if s.endswith(need_below) and st.get("hanging") != "true" and not s.startswith("wall") and \
                "wall_" not in s and y > 0 and (below is None or below.short == "air"):
            out.append(((x, y, z), str(st), "needs support below"))
        if st.get("hanging") == "true":
            above = b.get(x, y + 1, z)
            if above is None or above.short == "air":
                out.append(((x, y, z), str(st), "hanging from nothing"))
    return out


def prune_unsupported(b, keep=lambda st: False):
    """Remove every non-air block not connected (6-neighbour) to the ground layer."""
    solid = {p for p, (st, _) in b.cells.items() if st.short != "air"}
    seen = {p for p in solid if p[1] == 0}
    stack = list(seen)
    while stack:
        x, y, z = stack.pop()
        for dx, dy, dz in DIRS.values():
            q = (x + dx, y + dy, z + dz)
            if q in solid and q not in seen:
                seen.add(q)
                stack.append(q)
    removed = 0
    for p in solid - seen:
        if not keep(b.cells[p][0]):
            del b.cells[p]
            removed += 1
    return removed
