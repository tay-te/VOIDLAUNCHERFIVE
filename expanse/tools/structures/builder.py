"""Block-placing DSL for structure templates.

Coordinates are template-local: x east, y up, z south. A cell that is never
set is *structure void* (the template leaves the world untouched there);
set AIR explicitly to carve. Jigsaw pieces are placed with known shapes (no
neighbour updates), so `finalize()` computes fence/wall/pane connections and
stair corner shapes the way the game would.

Block-entity and entity NBT written here mirrors vanilla 26.3 templates
(see the comments on each helper for the template or class it follows).
"""
import math
import random

import nbt
from blocks import AIR, B, BlockState

DATA_VERSION = 5023

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
        "up": (0, 1, 0), "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
CCW = {v: k for k, v in CW.items()}
H4 = ["north", "east", "south", "west"]
# Direction data values (net.minecraft.core.Direction)
DIR3D = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}
DIR2D = {"south": 0, "west": 1, "north": 2, "east": 3}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}


def axis_of(d):
    return "x" if d in ("east", "west") else "z" if d in ("north", "south") else "y"


# ---- shape classification ---------------------------------------------------
_NONFULL_SUFFIX = ("_stairs", "_slab", "_wall", "_fence", "_fence_gate", "_door", "_trapdoor", "_button",
                   "_pressure_plate", "_carpet", "_bed", "_banner", "_candle", "glass_pane", "_sapling", "_torch",
                   "_sign", "_skull", "_head", "_cluster", "_shelf", "_blossoms", "_lantern", "_bars", "_chain", "lightning_rod", "_petals")
_NONFULL = {"air", "structure_void", "torch", "wall_torch", "lantern", "soul_lantern", "chest", "trapped_chest",
            "campfire", "soul_campfire", "bell", "lectern", "anvil", "chipped_anvil", "damaged_anvil", "grindstone",
            "stonecutter", "ladder", "vine", "glow_lichen", "snow", "moss_carpet", "cobweb", "iron_bars", "iron_chain",
            "end_rod", "lightning_rod", "decorated_pot", "sea_pickle", "leaf_litter", "pink_petals", "wildflowers",
            "red_mushroom", "brown_mushroom", "sweet_berry_bush", "spanish_moss", "cattail", "flower_pot", "candle",
            "brewing_stand", "cauldron", "water_cauldron", "composter", "hanging_roots", "short_grass", "tall_grass",
            "fern", "large_fern", "dead_bush", "poppy", "dandelion", "cornflower", "azure_bluet", "allium",
            "oxeye_daisy", "blue_orchid", "lily_of_the_valley", "red_tulip", "orange_tulip", "white_tulip",
            "pink_tulip", "lily_pad", "seagrass", "tall_seagrass", "sugar_cane", "heather", "edelweiss", "frostbloom",
            "glowcap", "dirt_path", "farmland", "wheat", "carrots", "beetroots", "potatoes", "firefly_bush", "bush",
            "kelp", "cactus", "pointed_dripstone", "scaffolding", "tripwire_hook", "mangrove_roots",
            "amethyst_cluster", "skeleton_skull", "skeleton_wall_skull", "sunflower", "lilac", "rose_bush", "peony",
            "lever", "enchanting_table", "cactus_flower", "short_dry_grass", "tall_dry_grass", "pale_hanging_moss",
            "shelf_mushroom", "cake", "bamboo", "water", "azalea", "flowering_azalea", "redstone_torch",
            "redstone_wall_torch", "big_dripleaf"}
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
    return st is not None and st.short.endswith("_wall") and "wall_" not in st.short


def is_bars(st):  # IronBarsBlock family: iron bars and all glass panes
    return st is not None and (st.short.endswith("_bars") or st.short.endswith("glass_pane"))


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
          "crimson", "warped", "poplar", "redwood", "willow", "palm", "lumen", "baobab", "wisteria")


def wooden_fence(st):
    return any(st.short == w + "_fence" for w in WOODEN)


# ---- items and text ----------------------------------------------------------
def item(id_, count=1, components=None):
    """ItemStack NBT as ItemStack.CODEC writes it: {id, count[, components]}."""
    d = {"id": id_ if ":" in id_ else "minecraft:" + id_, "count": nbt.Int(count)}
    if components:
        d["components"] = components
    return d


def written_book(title, author, pages, generation=0):
    """A written book; pages are plain strings (Filterable/Component simple forms)."""
    assert len(title) <= 32, title
    content = {"title": title, "author": author, "generation": nbt.Int(generation),
               "pages": nbt.List(nbt.TAG_STRING, list(pages)), "resolved": nbt.Byte(1)}
    return item("written_book", 1, {"minecraft:written_book_content": content})


def sign_text(lines, color="black", glowing=False):
    lines = (list(lines) + ["", "", "", ""])[:4]
    msgs = nbt.List(nbt.TAG_COMPOUND, [{"text": t} for t in lines])
    return {"messages": msgs, "has_glowing_text": nbt.Byte(1 if glowing else 0), "color": color}


# ---- rotation (StructureTemplate.transform with pivot 0) --------------------------
ROTS = ["none", "clockwise_90", "180", "counterclockwise_90"]


def rot_pos(x, z, r):
    r %= 4
    if r == 1:
        return -z, x
    if r == 2:
        return -x, -z
    if r == 3:
        return z, -x
    return x, z


def rot_vec(x, z, r):
    r %= 4
    if r == 1:
        return 1 - z, x
    if r == 2:
        return 1 - x, 1 - z
    if r == 3:
        return z, 1 - x
    return x, z


def rot_dir(d, r):
    if d not in CW:
        return d
    for _ in range(r % 4):
        d = CW[d]
    return d


def rot_state(st, r):
    """Rotate a BlockState like BlockState.rotate(Rotation) (enough for previews/assembly)."""
    r %= 4
    if r == 0 or not st.props:
        return st
    p = dict(st.props)
    if "facing" in p:
        p["facing"] = rot_dir(p["facing"], r)
    if "axis" in p and r % 2 == 1 and p["axis"] in ("x", "z"):
        p["axis"] = "z" if p["axis"] == "x" else "x"
    if "rotation" in p:
        p["rotation"] = str((int(p["rotation"]) + 4 * r) % 16)
    if "orientation" in p:
        f, t = p["orientation"].split("_")
        p["orientation"] = f"{rot_dir(f, r)}_{rot_dir(t, r)}"
    sides = [d for d in H4 if d in p]
    if len(sides) == 4:
        old = {d: p[d] for d in H4}
        for d in H4:
            p[rot_dir(d, r)] = old[d]
    return BlockState(st.id, **p)


class Build:
    def __init__(self, name, sx, sy, sz, seed=None):
        self.name = name
        self.size = (sx, sy, sz)
        self.cells = {}
        self.entities = []      # dicts: {"pos": (x,y,z) floats, "block": (x,y,z) ints, "nbt": compound}
        self.rng = random.Random(seed if seed is not None else sum(map(ord, name)) * 7919)

    # ---- basic access ----
    def inb(self, x, y, z):
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x, y, z, st, nbt_=None, clip=False):
        if isinstance(st, str):
            st = B(st)
        if not (type(x) is int and type(y) is int and type(z) is int):
            raise TypeError(f"{self.name}: non-integer cell ({x},{y},{z}) for {st}")
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
                    if clip and not self.inb(x, y, z):
                        continue
                    self.set(x, y, z, st)

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
                    self.set(x, y, z, st(x, y, z) if callable(st) else st)
            for z in range(z0, z1 + 1):
                for x in (x0, x1):
                    self.set(x, y, z, st(x, y, z) if callable(st) else st)
            if corner is not None:
                for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
                    self.set(x, y, z, corner)

    def disc(self, cx, cz, r, y, st, clip=True, pred=None):
        for x in range(int(math.floor(cx - r)), int(math.ceil(cx + r)) + 1):
            for z in range(int(math.floor(cz - r)), int(math.ceil(cz + r)) + 1):
                if (x - cx) ** 2 + (z - cz) ** 2 <= r * r + 0.01:
                    if pred is None or pred(x, z):
                        self.set(x, y, z, st(x, z) if callable(st) else st, clip=clip)

    def column(self, x, z, y0, y1, st):
        for y in range(y0, y1 + 1):
            self.set(x, y, z, st)

    def replace(self, frm, to, prob=1.0, region=None):
        """Replace block ids (string or list); `to` may be a callable(rng, old)->state."""
        frm_ids = {f if ":" in f else "minecraft:" + f for f in ([frm] if isinstance(frm, str) else frm)}
        for pos, (st, n) in sorted(self.cells.items()):
            if st.id in frm_ids and (region is None or region(*pos)) and self.rng.random() < prob:
                new = to(self.rng, st) if callable(to) else (B(to) if isinstance(to, str) else to)
                self.cells[pos] = (new, n)

    # ---- furniture & block entities --------------------------------------------
    def chest(self, x, y, z, facing, loot, trapped=False):
        """Loot chest as in abandoned_camp/campsite_*: {LootTable, components, id}."""
        self.set(x, y, z, B("trapped_chest" if trapped else "chest", facing=facing),
                 {"LootTable": loot, "components": {}, "id": "minecraft:trapped_chest" if trapped else "minecraft:chest"})

    def barrel(self, x, y, z, facing="up", loot=None):
        n = {"LootTable": loot, "components": {}, "id": "minecraft:barrel"} if loot else None
        self.set(x, y, z, B("barrel", facing=facing), n)

    def bed(self, x, y, z, facing, color="red"):
        """(x,y,z) is the FOOT; the head is one block toward `facing` (AbstractBedBlock)."""
        dx, _, dz = DIRS[facing]
        self.set(x, y, z, B(f"{color}_bed", facing=facing, part="foot"))
        self.set(x + dx, y, z + dz, B(f"{color}_bed", facing=facing, part="head"))

    def door(self, x, y, z, wood, facing, hinge="left", open_=False):
        name = wood + "_door" if not wood.endswith("_door") else wood
        self.set(x, y, z, B(name, facing=facing, half="lower", hinge=hinge, open=open_))
        self.set(x, y + 1, z, B(name, facing=facing, half="upper", hinge=hinge, open=open_))

    def tall_plant(self, x, y, z, name):
        self.set(x, y, z, B(name, half="lower"))
        self.set(x, y + 1, z, B(name, half="upper"))

    def campfire(self, x, y, z, lit=True, facing="north", soul=False):
        """As abandoned_camp campfires: {components, Items, CookingTimes, CookingTotalTimes, id}."""
        self.set(x, y, z, B("soul_campfire" if soul else "campfire", lit=lit, facing=facing),
                 {"components": {}, "Items": nbt.List(nbt.TAG_END, []), "CookingTimes": nbt.IntArray([0, 0, 0, 0]),
                  "CookingTotalTimes": nbt.IntArray([0, 0, 0, 0]),
                  "id": "minecraft:campfire"})  # one BE type serves campfire and soul_campfire

    def bell(self, x, y, z, attachment="floor", facing="north"):
        self.set(x, y, z, B("bell", attachment=attachment, facing=facing), {"id": "minecraft:bell"})

    def banner(self, x, y, z, color, patterns=(), wall_facing=None, rotation=0):
        """Banner BE as in end_city/tower_top: {patterns:[{color, pattern}], id: banner}."""
        pats = nbt.List(nbt.TAG_COMPOUND, [{"color": c, "pattern": p if ":" in p else "minecraft:" + p}
                                           for p, c in patterns])
        n = {"components": {}, "patterns": pats, "id": "minecraft:banner"}
        if wall_facing:
            self.set(x, y, z, B(f"{color}_wall_banner", facing=wall_facing), n)
        else:
            self.set(x, y, z, B(f"{color}_banner", rotation=rotation), n)

    def pot(self, x, y, z, facing="north", sherds=("brick", "brick", "brick", "brick"), loot=None):
        """Decorated pot as in trial_chambers/decor/flow_pot: sherds {back,front,left,right}."""
        b_, f_, l_, r_ = [s if ":" in s else "minecraft:" + s for s in sherds]
        n = {"components": {}, "sherds": {"back": {"id": b_}, "front": {"id": f_}, "left": {"id": l_},
                                          "right": {"id": r_}}, "id": "minecraft:decorated_pot"}
        if loot:
            n["LootTable"] = loot
        self.set(x, y, z, B("decorated_pot", facing=facing), n)

    def lectern(self, x, y, z, facing, book=None):
        """Lectern BE (LecternBlockEntity): {Book: ItemStack, Page, id}."""
        if book is None:
            self.set(x, y, z, B("lectern", facing=facing), {"id": "minecraft:lectern"})
        else:
            self.set(x, y, z, B("lectern", facing=facing, has_book=True),
                     {"Book": book, "Page": nbt.Int(0), "id": "minecraft:lectern"})

    def sign(self, x, y, z, wood, lines, wall_facing=None, rotation=0, hanging=False, attached=False,
             color="black", glowing=False, back=None):
        """Sign BE as in igloo/bottom (SignBlockEntity): front_text/back_text, is_waxed."""
        if hanging:
            st = B(f"{wood}_wall_hanging_sign", facing=wall_facing) if wall_facing else \
                B(f"{wood}_hanging_sign", rotation=rotation, attached=attached)
            beid = "minecraft:hanging_sign"
        else:
            st = B(f"{wood}_wall_sign", facing=wall_facing) if wall_facing else B(f"{wood}_sign", rotation=rotation)
            beid = "minecraft:sign"
        self.set(x, y, z, st, {"back_text": sign_text(back or [], color, glowing),
                               "allow_op_features": nbt.Byte(0), "id": beid,
                               "front_text": sign_text(lines, color, glowing), "is_waxed": nbt.Byte(1)})

    def spawner(self, x, y, z, entity, min_delay=200, max_delay=800, count=4, max_nearby=6, player_range=16,
                spawn_range=4):
        """Monster spawner BE exactly as bastion/treasure/bases/lava_basin."""
        e = entity if ":" in entity else "minecraft:" + entity
        self.set(x, y, z, B("spawner"), {
            "MaxNearbyEntities": nbt.Short(max_nearby), "RequiredPlayerRange": nbt.Short(player_range),
            "SpawnCount": nbt.Short(count), "SpawnData": {"entity": {"id": e}},
            "MaxSpawnDelay": nbt.Short(max_delay), "id": "minecraft:mob_spawner", "SpawnRange": nbt.Short(spawn_range),
            "Delay": nbt.Short(0), "MinSpawnDelay": nbt.Short(min_delay),
            "SpawnPotentials": nbt.List(nbt.TAG_COMPOUND, [{"data": {"entity": {"id": e}}, "weight": nbt.Int(1)}])})

    def brushable(self, x, y, z, kind, loot):
        """Suspicious sand/gravel as desert_well/suspicious_sand plus the LootTable key it loads."""
        self.set(x, y, z, B(f"suspicious_{kind}"),
                 {"LootTable": loot, "components": {}, "id": "minecraft:brushable_block"})

    def bookshelf(self, x, y, z, facing, slots=(0, 2, 3), books=None):
        """Chiseled bookshelf whose occupied slots match its Items (ChiseledBookShelfBlockEntity)."""
        props = {f"slot_{i}_occupied": (i in slots) for i in range(6)}
        items = []
        for i in sorted(slots):
            it = (books or {}).get(i) or item("book")
            it = dict(it)
            it["Slot"] = nbt.Byte(i)
            items.append(it)
        self.set(x, y, z, B("chiseled_bookshelf", facing=facing, **props),
                 {"Items": nbt.List(nbt.TAG_COMPOUND, items), "last_interacted_slot": nbt.Int(-1),
                  "id": "minecraft:chiseled_bookshelf"})

    def shelf(self, x, y, z, wood, facing, items=()):
        """Wooden shelf with up to 3 displayed items (ShelfBlockEntity)."""
        its = []
        for i, it in enumerate(items[:3]):
            if it:
                d = dict(it if isinstance(it, dict) else item(it))
                d["Slot"] = nbt.Byte(i)
                its.append(d)
        self.set(x, y, z, B(f"{wood}_shelf", facing=facing),
                 {"Items": nbt.List(nbt.TAG_COMPOUND, its), "align_items_to_bottom": nbt.Byte(0),
                  "id": "minecraft:shelf"})

    def jigsaw(self, x, y, z, front, name="minecraft:empty", target="minecraft:empty", pool="minecraft:empty",
               final="minecraft:air", top=None, joint="rollable", priority=0, selection=0):
        """Jigsaw block exactly as abandoned_camp templates store it."""
        if top is None:
            top = "up" if front in CW else "north"
        self.set(x, y, z, B("jigsaw", orientation=f"{front}_{top}"),
                 {"components": {}, "joint": joint, "final_state": final, "name": name,
                  "placement_priority": nbt.Int(priority), "pool": pool, "selection_priority": nbt.Int(selection),
                  "id": "minecraft:jigsaw", "target": target})

    def hang_moss(self, x, y, z, length, block="expanse:spanish_moss"):
        """Hanging-moss style strand (tip=true at the bottom) hanging below (x,y+1,z)."""
        placed = []
        for i in range(length):
            yy = y - i
            if yy < 0 or not self.is_air_or_void(x, yy, z):
                break
            self.set(x, yy, z, B(block, tip=False))
            placed.append(yy)
        if placed:
            self.set(x, placed[-1], z, B(block, tip=True))

    # ---- entities --------------------------------------------------------------
    def _uuid(self):
        return nbt.IntArray([self.rng.randint(-2 ** 31, 2 ** 31 - 1) for _ in range(4)])

    def _entity(self, eid, pos, block, extra, yaw=0.0, pitch=0.0):
        n = {"Motion": nbt.List(nbt.TAG_DOUBLE, [nbt.Double(0.0)] * 3),
             "Invulnerable": nbt.Byte(0), "Air": nbt.Short(300), "OnGround": nbt.Byte(0),
             "fall_distance": nbt.Double(0.0), "PortalCooldown": nbt.Int(0),
             "Rotation": nbt.List(nbt.TAG_FLOAT, [nbt.Float(yaw), nbt.Float(pitch)]),
             "Pos": nbt.List(nbt.TAG_DOUBLE, [nbt.Double(v) for v in pos]),
             "Fire": nbt.Short(0), "id": eid if ":" in eid else "minecraft:" + eid, "UUID": self._uuid()}
        n.update(extra)
        bx, by, bz = block
        if not self.inb(bx, by, bz):
            raise IndexError(f"{self.name}: entity {eid} block {block} outside {self.size}")
        self.entities.append({"pos": tuple(float(v) for v in pos), "block": (bx, by, bz), "nbt": n})
        return n

    def painting(self, x, y, z, facing, variant):
        """Painting hung on the wall behind (x,y,z) facing `facing`; (x,y,z) is the anchor cell
        (even sizes extend toward CCW(facing) and up - Painting.calculateBoundingBox)."""
        dx, _, dz = DIRS[facing]
        pos = (x + 0.5 - 0.46875 * dx, y + 0.5, z + 0.5 - 0.46875 * dz)
        v = variant if ":" in variant else "minecraft:" + variant
        self._entity("painting", pos, (x, y, z),
                     {"facing": nbt.Byte(DIR2D[facing]), "variant": v,
                      "block_pos": nbt.IntArray([x, y, z])}, yaw=YAW[facing])

    def item_frame(self, x, y, z, facing, it=None, rotation=0, glow=False):
        """Item frame (ItemFrame): Facing (3D id), Item, ItemRotation, ItemDropChance, Invisible, Fixed."""
        dx, dy, dz = DIRS[facing]
        pos = (x + 0.5 - 0.46875 * dx, y + 0.5 - 0.46875 * dy, z + 0.5 - 0.46875 * dz)
        extra = {"Facing": nbt.Byte(DIR3D[facing]), "ItemRotation": nbt.Byte(rotation),
                 "ItemDropChance": nbt.Float(1.0), "Invisible": nbt.Byte(0), "Fixed": nbt.Byte(0),
                 "block_pos": nbt.IntArray([x, y, z])}
        if it:
            extra["Item"] = it if isinstance(it, dict) else item(it)
        pitch = -90.0 if facing == "up" else 90.0 if facing == "down" else 0.0
        self._entity("glow_item_frame" if glow else "item_frame", pos, (x, y, z), extra,
                     yaw=YAW.get(facing, 0.0), pitch=pitch)

    def cushion(self, x, y, z, color="white", support_top=1.0):
        """Cushion as in abandoned_camp campsites: sits on the block below (x,y,z)."""
        pos = (x + 0.5, y - 1 + support_top, z + 0.5)
        bpos = (x, int(math.floor(pos[1])), z)
        self._entity("cushion", pos, bpos, {"color": color, "block_pos": nbt.IntArray(list(bpos))})

    def boat(self, x, y, z, wood="spruce", yaw=0.0):
        """Boat (AbstractBoat stores only leash data beyond the Entity basics)."""
        self._entity(f"{wood}_boat", (x + 0.5, y, z + 0.5), (x, y, z), {}, yaw=yaw)

    def mob(self, x, y, z, eid, yaw=0.0, extra=None):
        """A persistent passive animal standing in cell (x,y,z), as in village/common/animals/*.nbt
        (the template loader finalizes it, so variants may be re-rolled per placement)."""
        ex = {"PersistenceRequired": nbt.Byte(1), "CanPickUpLoot": nbt.Byte(0), "LeftHanded": nbt.Byte(0),
              "Age": nbt.Int(0)}
        ex.update(extra or {})
        n = self._entity(eid, (x + 0.5, y, z + 0.5), (x, y, z), ex, yaw=yaw)
        n["OnGround"] = nbt.Byte(1)
        return n

    def armor_stand(self, x, y, z, yaw=0.0, equipment=None, pose=None, arms=True, small=False):
        """Armor stand mirroring village/taiga/houses/taiga_armorer_2 (incl. legacy hand/armor lists)."""
        eq = {k: (v if isinstance(v, dict) else item(v)) for k, v in (equipment or {}).items()}
        pose = pose or {"Head": (0.0, 0.0, 0.0), "Body": (0.0, 0.0, 0.0)}
        armor = [eq.get(s, {}) for s in ("feet", "legs", "chest", "head")]
        hands = [eq.get("mainhand", {}), eq.get("offhand", {})]
        extra = {"HurtByTimestamp": nbt.Int(0), "FallFlying": nbt.Byte(0), "ShowArms": nbt.Byte(1 if arms else 0),
                 "AbsorptionAmount": nbt.Float(0.0), "DisabledSlots": nbt.Int(0), "DeathTime": nbt.Short(0),
                 "Pose": {k: nbt.List(nbt.TAG_FLOAT, [nbt.Float(a) for a in v]) for k, v in pose.items()},
                 "Invisible": nbt.Byte(0), "Small": nbt.Byte(1 if small else 0), "Health": nbt.Float(20.0),
                 "equipment": eq, "HandItems": nbt.List(nbt.TAG_COMPOUND, hands),
                 "ArmorItems": nbt.List(nbt.TAG_COMPOUND, armor), "NoBasePlate": nbt.Byte(0),
                 "attributes": nbt.List(nbt.TAG_COMPOUND, [
                     {"id": "minecraft:max_health", "base": nbt.Double(20.0)},
                     {"id": "minecraft:knockback_resistance", "base": nbt.Double(0.0)},
                     {"id": "minecraft:movement_speed", "base": nbt.Double(0.699999988079071)},
                     {"id": "minecraft:armor", "base": nbt.Double(0.0)},
                     {"id": "minecraft:armor_toughness", "base": nbt.Double(0.0)}]),
                 "HurtTime": nbt.Short(0)}
        n = self._entity("armor_stand", (x + 0.5, y, z + 0.5), (x, y, z), extra, yaw=yaw)
        n["OnGround"] = nbt.Byte(1)
        n["Fire"] = nbt.Short(-1)

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
                covered = above is not None and (is_full(above) or is_wall(above))
                sides = {d: ("tall" if covered else "low") if con[d] else "none" for d in H4}
                above_post = above is not None and is_wall(above) and above.get("up") == "true"
                nn, ss, ww, ee = (sides[d] == "none" for d in ("north", "south", "west", "east"))
                corner = (nn and ss and ww and ee) or (nn != ss) or (ww != ee)
                high = (sides["north"] == "tall" and sides["south"] == "tall") or (
                    sides["east"] == "tall" and sides["west"] == "tall")
                post_override = above is not None and (above.short in ("torch", "lantern", "soul_lantern", "campfire")
                                                       or above.short.endswith(("_banner", "_pressure_plate",
                                                                               "_candle", "candle", "_sign")))
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
        entities = [{"nbt": e["nbt"], "blockPos": nbt.List(nbt.TAG_INT, [nbt.Int(v) for v in e["block"]]),
                     "pos": nbt.List(nbt.TAG_DOUBLE, [nbt.Double(v) for v in e["pos"]])} for e in self.entities]
        return {
            "size": nbt.List(nbt.TAG_INT, [nbt.Int(v) for v in self.size]),
            "entities": nbt.List(nbt.TAG_COMPOUND if entities else nbt.TAG_END, entities),
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
              "large_fern", "cattail", "red_mushroom", "brown_mushroom", "lily_of_the_valley", "blue_orchid",
              "short_dry_grass", "tall_dry_grass", "bush", "firefly_bush"}
    for (x, y, z), (st, _) in sorted(b.cells.items()):
        s = st.short
        if s in ("air", "jigsaw"):
            continue
        nbs = [b.get(x + dx, y + dy, z + dz) for dx, dy, dz in DIRS.values()]
        if y > 1 and all(n is None or n.short == "air" for n in nbs):
            out.append(((x, y, z), str(st), "isolated"))
            continue
        below = b.get(x, y - 1, z)
        if y <= 1 and below is None:
            continue  # stands on the (beard-levelled) terrain surface
        if s in plants and st.get("half") != "upper" and (below is None or below.short == "air"):
            out.append(((x, y, z), str(st), "plant without ground"))
        if s.endswith(need_below) and st.get("hanging") != "true" and "wall" not in s and \
                (below is None or below.short == "air"):
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


def split_build(b, axis, at, name_lo, name_hi):
    """Cut a finished-but-not-finalized Build in two along `axis` ("x" or "z"): cells with coordinate < at
    go to the first piece, the rest (shifted so they start at 0) to the second. Used to keep landmark
    pieces within the 48-block structure-block limit; the caller adds the seam jigsaws."""
    i = "xyz".index(axis)
    sx, sy, sz = b.size
    lo_size = list(b.size)
    hi_size = list(b.size)
    lo_size[i] = at
    hi_size[i] = b.size[i] - at
    lo = Build(name_lo, *lo_size, seed=b.rng.randint(0, 2 ** 31))
    hi = Build(name_hi, *hi_size, seed=b.rng.randint(0, 2 ** 31))
    for pos, cell in b.cells.items():
        if pos[i] < at:
            lo.cells[pos] = cell
        else:
            p = list(pos)
            p[i] -= at
            hi.cells[tuple(p)] = cell
    for e in b.entities:
        if e["block"][i] < at:
            lo.entities.append(e)
            continue
        e = dict(e)
        p = list(e["pos"])
        p[i] -= at
        bl = list(e["block"])
        bl[i] -= at
        e["pos"], e["block"] = tuple(p), tuple(bl)
        n = dict(e["nbt"])
        n["Pos"] = nbt.List(nbt.TAG_DOUBLE, [nbt.Double(v) for v in p])
        if "block_pos" in n:
            n["block_pos"] = nbt.IntArray(bl)
        e["nbt"] = n
        hi.entities.append(e)
    return lo, hi


def fit_height(b):
    """Shrink the template height to the highest used layer (+1)."""
    top = max([p[1] for p in b.cells] + [int(e["pos"][1]) for e in b.entities] + [0])
    b.size = (b.size[0], top + 1, b.size[2])
    return b
