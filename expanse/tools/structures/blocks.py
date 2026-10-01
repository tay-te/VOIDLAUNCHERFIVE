"""Block-state schema: every block we place gets its FULL property set (as
vanilla structure templates store them), filled from defaults where the
design did not specify a value.

Property sets mirror the vanilla block classes (StairBlock, SlabBlock,
WallBlock, ...). They are cross-checked by validate.py against the palettes
of all vanilla 26.3 templates and against the blockstate asset files.
"""
import re

H4 = ["north", "south", "west", "east"]          # horizontal facings
D6 = ["north", "east", "south", "west", "up", "down"]
BOOL = ["true", "false"]
WALL_SIDE = ["none", "low", "tall"]


def _p(values, default):
    return (list(values), default)


def _ints(lo, hi):
    return [str(i) for i in range(lo, hi + 1)]


# ---- families -------------------------------------------------------------
STAIRS = {"facing": _p(H4, "north"), "half": _p(["top", "bottom"], "bottom"),
          "shape": _p(["straight", "inner_left", "inner_right", "outer_left", "outer_right"], "straight"),
          "waterlogged": _p(BOOL, "false")}
SLAB = {"type": _p(["top", "bottom", "double"], "bottom"), "waterlogged": _p(BOOL, "false")}
WALL = {"up": _p(BOOL, "true"), "north": _p(WALL_SIDE, "none"), "east": _p(WALL_SIDE, "none"),
        "south": _p(WALL_SIDE, "none"), "west": _p(WALL_SIDE, "none"), "waterlogged": _p(BOOL, "false")}
CROSS = {"north": _p(BOOL, "false"), "east": _p(BOOL, "false"), "south": _p(BOOL, "false"),
         "west": _p(BOOL, "false"), "waterlogged": _p(BOOL, "false")}  # fences, panes, bars
FENCE_GATE = {"facing": _p(H4, "north"), "open": _p(BOOL, "false"), "powered": _p(BOOL, "false"),
              "in_wall": _p(BOOL, "false")}
DOOR = {"facing": _p(H4, "north"), "half": _p(["upper", "lower"], "lower"), "hinge": _p(["left", "right"], "left"),
        "open": _p(BOOL, "false"), "powered": _p(BOOL, "false")}
TRAPDOOR = {"facing": _p(H4, "north"), "half": _p(["top", "bottom"], "bottom"), "open": _p(BOOL, "false"),
            "powered": _p(BOOL, "false"), "waterlogged": _p(BOOL, "false")}
BUTTON = {"face": _p(["floor", "wall", "ceiling"], "wall"), "facing": _p(H4, "north"), "powered": _p(BOOL, "false")}
PLATE = {"powered": _p(BOOL, "false")}
AXIS = {"axis": _p(["x", "y", "z"], "y")}
LEAVES = {"distance": _p(_ints(1, 7), "7"), "persistent": _p(BOOL, "true"), "waterlogged": _p(BOOL, "false")}
BED = {"facing": _p(H4, "north"), "part": _p(["head", "foot"], "foot"), "occupied": _p(BOOL, "false")}
FACING4 = {"facing": _p(H4, "north")}
FACING6 = {"facing": _p(D6, "up")}
CANDLE = {"candles": _p(_ints(1, 4), "1"), "lit": _p(BOOL, "false"), "waterlogged": _p(BOOL, "false")}
LANTERN = {"hanging": _p(BOOL, "false"), "waterlogged": _p(BOOL, "false")}
CAMPFIRE = {"facing": _p(H4, "north"), "lit": _p(BOOL, "true"), "signal_fire": _p(BOOL, "false"),
            "waterlogged": _p(BOOL, "false")}
CHEST = {"facing": _p(H4, "north"), "type": _p(["single", "left", "right"], "single"), "waterlogged": _p(BOOL, "false")}
FURNACE = {"facing": _p(H4, "north"), "lit": _p(BOOL, "false")}
HALF2 = {"half": _p(["upper", "lower"], "lower")}
SNOWY = {"snowy": _p(BOOL, "false")}
MULTIFACE = {d: _p(BOOL, "false") for d in ["down", "east", "north", "south", "up", "west"]}
MULTIFACE["waterlogged"] = _p(BOOL, "false")

# ---- explicit blocks ------------------------------------------------------
EXPLICIT = {
    "minecraft:barrel": {"facing": _p(D6, "north"), "open": _p(BOOL, "false")},
    "minecraft:lectern": {"facing": _p(H4, "north"), "has_book": _p(BOOL, "false"), "powered": _p(BOOL, "false")},
    "minecraft:loom": FACING4,
    "minecraft:stonecutter": FACING4,
    "minecraft:anvil": FACING4, "minecraft:chipped_anvil": FACING4, "minecraft:damaged_anvil": FACING4,
    "minecraft:grindstone": {"face": _p(["floor", "wall", "ceiling"], "floor"), "facing": _p(H4, "north")},
    "minecraft:bell": {"attachment": _p(["floor", "ceiling", "single_wall", "double_wall"], "floor"),
                       "facing": _p(H4, "north"), "powered": _p(BOOL, "false")},
    "minecraft:ladder": {"facing": _p(H4, "north"), "waterlogged": _p(BOOL, "false")},
    "minecraft:vine": {"east": _p(BOOL, "false"), "north": _p(BOOL, "false"), "south": _p(BOOL, "false"),
                       "up": _p(BOOL, "false"), "west": _p(BOOL, "false")},
    "minecraft:glow_lichen": MULTIFACE,
    "minecraft:iron_chain": {"axis": _p(["x", "y", "z"], "y"), "waterlogged": _p(BOOL, "false")},
    "minecraft:end_rod": FACING6,
    "minecraft:lightning_rod": {"facing": _p(D6, "up"), "powered": _p(BOOL, "false"), "waterlogged": _p(BOOL, "false")},
    "minecraft:snow": {"layers": _p(_ints(1, 8), "1")},
    "minecraft:sweet_berry_bush": {"age": _p(_ints(0, 3), "3")},
    "minecraft:sugar_cane": {"age": _p(_ints(0, 15), "0")},
    "minecraft:composter": {"level": _p(_ints(0, 8), "0")},
    "minecraft:water_cauldron": {"level": _p(_ints(1, 3), "3")},
    "minecraft:skeleton_skull": {"powered": _p(BOOL, "false"), "rotation": _p(_ints(0, 15), "0")},
    "minecraft:skeleton_wall_skull": {"facing": _p(H4, "north"), "powered": _p(BOOL, "false")},
    "minecraft:decorated_pot": {"cracked": _p(BOOL, "false"), "facing": _p(H4, "north"), "waterlogged": _p(BOOL, "false")},
    "minecraft:hanging_roots": {"waterlogged": _p(BOOL, "false")},
    "minecraft:mangrove_roots": {"waterlogged": _p(BOOL, "false")},
    "minecraft:carved_pumpkin": FACING4, "minecraft:jack_o_lantern": FACING4,
    "minecraft:sea_pickle": {"pickles": _p(_ints(1, 4), "1"), "waterlogged": _p(BOOL, "false")},
    "minecraft:leaf_litter": {"facing": _p(H4, "north"), "segment_amount": _p(_ints(1, 4), "1")},
    "minecraft:pink_petals": {"facing": _p(H4, "north"), "flower_amount": _p(_ints(1, 4), "1")},
    "minecraft:wildflowers": {"facing": _p(H4, "north"), "flower_amount": _p(_ints(1, 4), "1")},
    "minecraft:redstone_lamp": {"lit": _p(BOOL, "false")},
    "minecraft:tripwire_hook": {"attached": _p(BOOL, "false"), "facing": _p(H4, "north"), "powered": _p(BOOL, "false")},
    "minecraft:farmland": {"moisture": _p(_ints(0, 7), "0")},
    "minecraft:wheat": {"age": _p(_ints(0, 7), "7")},
    "minecraft:carrots": {"age": _p(_ints(0, 7), "7")},
    "minecraft:beetroots": {"age": _p(_ints(0, 3), "3")},
    "minecraft:seagrass": {},
    "minecraft:tall_seagrass": HALF2,
    "minecraft:kelp": {"age": _p(_ints(0, 25), "0")},
    "minecraft:cactus": {"age": _p(_ints(0, 15), "0")},
    "minecraft:dead_bush": {},
    "minecraft:brewing_stand": {"has_bottle_0": _p(BOOL, "false"), "has_bottle_1": _p(BOOL, "false"),
                                "has_bottle_2": _p(BOOL, "false")},
    "minecraft:cobweb": {},
    "minecraft:pointed_dripstone": {"thickness": _p(["tip_merge", "tip", "frustum", "middle", "base"], "tip"),
                                    "vertical_direction": _p(["up", "down"], "up"), "waterlogged": _p(BOOL, "false")},
    "minecraft:jigsaw": {"orientation": _p(["down_east", "down_north", "down_south", "down_west", "up_east", "up_north",
                                             "up_south", "up_west", "west_up", "east_up", "north_up", "south_up"],
                                            "north_up")},
    "minecraft:lever": {"face": _p(["floor", "wall", "ceiling"], "wall"), "facing": _p(H4, "north"),
                        "powered": _p(BOOL, "false")},
    "minecraft:beehive": {"facing": _p(H4, "north"), "honey_level": _p(_ints(0, 5), "0")},
    "minecraft:bee_nest": {"facing": _p(H4, "north"), "honey_level": _p(_ints(0, 5), "0")},
    "minecraft:suspicious_sand": {"dusted": _p(_ints(0, 3), "0")},
    "minecraft:suspicious_gravel": {"dusted": _p(_ints(0, 3), "0")},
    "minecraft:shelf_mushroom": {"facing": _p(H4, "north"), "age": _p(_ints(0, 1), "0")},
    "minecraft:pale_hanging_moss": {"tip": _p(BOOL, "true")},
    "minecraft:cake": {"bites": _p(_ints(0, 6), "0")},
    "minecraft:potatoes": {"age": _p(_ints(0, 7), "7")},
    "minecraft:bamboo": {"age": _p(_ints(0, 1), "0"), "leaves": _p(["none", "small", "large"], "none"),
                         "stage": _p(_ints(0, 1), "0")},
    "minecraft:water": {"level": _p(_ints(0, 15), "0")},
    "minecraft:target": {"power": _p(_ints(0, 15), "0")},
    "minecraft:redstone_wall_torch": {"facing": _p(H4, "north"), "lit": _p(BOOL, "true")},
    "minecraft:redstone_torch": {"lit": _p(BOOL, "true")},
    "minecraft:big_dripleaf": {"facing": _p(H4, "north"), "tilt": _p(["none", "unstable", "partial", "full"], "none"),
                               "waterlogged": _p(BOOL, "false")},
    "expanse:wisteria_blossoms": {"tip": _p(BOOL, "true")},          # HangingMossBlock
    "expanse:azure_wisteria_blossoms": {"tip": _p(BOOL, "true")},    # HangingMossBlock
    # mod blocks with special classes
    "expanse:spanish_moss": {"tip": _p(BOOL, "true")},               # HangingMossBlock
    "expanse:prismite_cluster": {"facing": _p(D6, "up"), "waterlogged": _p(BOOL, "false")},  # AmethystClusterBlock
    "expanse:cattail": HALF2,                                          # DoublePlantBlock
    "minecraft:amethyst_cluster": {"facing": _p(D6, "up"), "waterlogged": _p(BOOL, "false")},
}
# used by the small structures (RailBlock, ScaffoldingBlock, HugeMushroomBlock)
EXPLICIT.update({
    "minecraft:rail": {"shape": _p(["north_south", "east_west", "ascending_east", "ascending_west", "ascending_north",
                                    "ascending_south", "south_east", "south_west", "north_west", "north_east"],
                                   "north_south"), "waterlogged": _p(BOOL, "false")},
    "minecraft:scaffolding": {"bottom": _p(BOOL, "false"), "distance": _p(_ints(0, 7), "7"),
                              "waterlogged": _p(BOOL, "false")},
    "minecraft:red_mushroom_block": {d: _p(BOOL, "true") for d in ["down", "east", "north", "south", "up", "west"]},
    "minecraft:brown_mushroom_block": {d: _p(BOOL, "true") for d in ["down", "east", "north", "south", "up", "west"]},
    "minecraft:mushroom_stem": {d: _p(BOOL, "true") for d in ["down", "east", "north", "south", "up", "west"]},
})

# Blocks with no properties that we use (sanity list; anything else not
# matching a family must appear here so typos are caught).
PLAIN = set("""
air structure_void raw_gold_block lapis_block honey_block honeycomb_block chiseled_deepslate cracked_deepslate_tiles lava_cauldron netherrack magma_block stone cobblestone mossy_cobblestone andesite polished_andesite diorite granite stone_bricks
mossy_stone_bricks cracked_stone_bricks chiseled_stone_bricks tuff gravel coarse_dirt dirt rooted_dirt moss_block
moss_carpet mud packed_mud mud_bricks dirt_path sand red_sand sandstone smooth_sandstone cut_sandstone
chiseled_sandstone terracotta quartz_block smooth_quartz chiseled_quartz_block quartz_bricks glass
bricks calcite packed_ice ice blue_ice snow_block powder_snow bookshelf crafting_table
cartography_table fletching_table smithing_table cauldron flower_pot torch soul_torch sea_lantern glowstone
shroomlight fern short_grass poppy dandelion cornflower azure_bluet allium oxeye_daisy blue_orchid lily_of_the_valley
red_tulip orange_tulip white_tulip pink_tulip lily_pad red_mushroom brown_mushroom pumpkin melon
smooth_stone polished_deepslate deepslate_bricks deepslate_tiles cobbled_deepslate tuff_bricks polished_tuff
chiseled_tuff dripstone_block oak_planks spruce_planks dark_oak_planks birch_planks
jungle_planks acacia_planks mangrove_planks cherry_planks bamboo_planks crimson_planks warped_planks
pale_oak_planks poplar_planks clay coal_block iron_block gold_block emerald_block honeycomb_block spawner
enchanting_table cactus_flower short_dry_grass tall_dry_grass azalea flowering_azalea mossy_stone_bricks
cracked_stone_bricks chiseled_sandstone smooth_red_sandstone cut_red_sandstone chiseled_tuff_bricks
lodestone note_block bricks mud_bricks packed_ice snow_block sea_lantern
dried_kelp_block
dried_kelp_block bamboo_mosaic firefly_bush bush tinted_glass amethyst_block budding_amethyst
chiseled_stone_bricks cracked_deepslate_bricks prismarine dark_prismarine prismarine_bricks
gilded_blackstone blackstone polished_blackstone polished_blackstone_bricks raw_iron_block
""".split())
for _c in ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
           "purple", "blue", "brown", "green", "red", "black"]:
    PLAIN.update({f"{_c}_wool", f"{_c}_carpet", f"{_c}_concrete", f"{_c}_terracotta", f"{_c}_stained_glass",
                  f"{_c}_concrete_powder"})
for _f in ["poppy", "dandelion", "fern", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "spruce_sapling",
           "oak_sapling", "dead_bush", "cactus", "azalea_bush", "flowering_azalea_bush", "red_mushroom",
           "brown_mushroom", "blue_orchid", "allium", "azure_bluet", "cornflower", "oxeye_daisy", "lily_of_the_valley",
           "birch_sapling", "dark_oak_sapling", "bamboo", "cherry_sapling", "wither_rose", "torchflower"]:
    PLAIN.add(f"potted_{_f}")

MOD_WOODS = ["redwood", "willow", "palm", "lumen", "baobab", "wisteria"]
MOD_PLAIN = {"expanse:" + n for n in """limestone polished_limestone limestone_bricks chiseled_limestone mossy_limestone
opal_sand opal_sandstone smooth_opal_sandstone cut_opal_sandstone chiseled_opal_sandstone prismite_block heather
edelweiss frostbloom glowcap potted_heather potted_edelweiss potted_frostbloom potted_glowcap""".split()}
for _w in MOD_WOODS:
    MOD_PLAIN.update({f"expanse:{_w}_planks", f"expanse:potted_{_w}_sapling"})

# mod block ids the brief allows (anything else in expanse: is an error)
MOD_ALLOWED = set(MOD_PLAIN) | {"expanse:spanish_moss", "expanse:prismite_cluster", "expanse:cattail",
                                 "expanse:azure_wisteria_leaves"}
for _w in MOD_WOODS:
    for _s in ["log", "wood"]:
        MOD_ALLOWED.update({f"expanse:{_w}_{_s}", f"expanse:stripped_{_w}_{_s}"})
    for _s in ["stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate", "leaves"]:
        MOD_ALLOWED.add(f"expanse:{_w}_{_s}")
MOD_ALLOWED.update({"expanse:wisteria_blossoms", "expanse:azure_wisteria_blossoms"})
MOD_ALLOWED.update({f"expanse:{_w}_sapling" for _w in MOD_WOODS})
for _s in ["stairs", "slab", "wall"]:
    MOD_ALLOWED.update({f"expanse:limestone_{_s}", f"expanse:limestone_brick_{_s}", f"expanse:opal_sandstone_{_s}"})

# copper decoration families (chains, bars, lightning rods) share the iron/vanilla schemas
for _ox in ["", "exposed_", "weathered_", "oxidized_"]:
    for _wx in ["", "waxed_"]:
        EXPLICIT[f"minecraft:{_wx}{_ox}copper_chain"] = EXPLICIT["minecraft:iron_chain"]
        EXPLICIT[f"minecraft:{_wx}{_ox}lightning_rod"] = EXPLICIT["minecraft:lightning_rod"]
        PLAIN.update({f"{_wx}{_ox}cut_copper", f"{_wx}{_ox}copper" if _ox else f"{_wx}copper_block",
                      f"{_wx}{_ox}chiseled_copper"})
# ores and stones used by the small structures (mine faces, carvings)
PLAIN.update({"iron_ore", "coal_ore", "copper_ore", "polished_diorite", "polished_granite"})

_NOT_WALL = re.compile(r"_(wall_banner|wall_torch|wall_sign|wall_hanging_sign|wall_skull|wall_head|wall_fan)$")
PILLARS = {"minecraft:quartz_pillar", "minecraft:bone_block", "minecraft:hay_block", "minecraft:basalt",
           "minecraft:polished_basalt", "minecraft:purpur_pillar", "minecraft:bamboo_block",
           "minecraft:stripped_bamboo_block", "minecraft:muddy_mangrove_roots", "minecraft:deepslate",
           "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:pearlescent_froglight"}


def ns(name):
    return name if ":" in name else "minecraft:" + name


def schema(name):
    """Return {prop: (values, default)} for a namespaced block id, or raise."""
    if name in EXPLICIT:
        return EXPLICIT[name]
    short = name.split(":", 1)[1]
    if short.endswith("_stairs"):
        return STAIRS
    if short.endswith("_slab"):
        return SLAB
    if short.endswith("_wall_hanging_sign") or short.endswith("_wall_sign"):
        return {"facing": _p(H4, "north"), "waterlogged": _p(BOOL, "false")}
    if short.endswith("_hanging_sign"):
        return {"attached": _p(BOOL, "false"), "rotation": _p(_ints(0, 15), "0"), "waterlogged": _p(BOOL, "false")}
    if short.endswith("_sign"):
        return {"rotation": _p(_ints(0, 15), "0"), "waterlogged": _p(BOOL, "false")}
    if short.endswith("_shelf") and short != "chiseled_bookshelf":
        return {"facing": _p(H4, "north"), "powered": _p(BOOL, "false"),
                "side_chain": _p(["unconnected", "right", "center", "left"], "unconnected"),
                "waterlogged": _p(BOOL, "false")}
    if short.endswith("_wall") and not _NOT_WALL.search(short):
        return WALL
    if short.endswith("_fence_gate"):
        return FENCE_GATE
    if short.endswith("_fence") or short.endswith("glass_pane") or short.endswith("_bars"):
        return CROSS
    if short.endswith("_trapdoor"):
        return TRAPDOOR
    if short.endswith("_door"):
        return DOOR
    if short.endswith("_button"):
        return BUTTON
    if short.endswith("_pressure_plate"):
        return PLATE
    if (short.endswith("_log") or short.endswith("_wood") or short.endswith("_stem") and "mushroom" not in short
            or short.endswith("_hyphae") or name in PILLARS):
        return AXIS
    if short.endswith("_leaves"):
        return LEAVES
    if short.endswith("_bed"):
        return BED
    if short.endswith("_wall_banner") or short.endswith("_glazed_terracotta"):
        return FACING4
    if short.endswith("_banner"):
        return {"rotation": _p(_ints(0, 15), "0")}
    if short.endswith("candle"):
        return CANDLE
    if short in ("lantern", "soul_lantern") or short.endswith("copper_lantern"):
        return LANTERN
    if short in ("wall_torch", "soul_wall_torch"):
        return FACING4
    if short in ("campfire", "soul_campfire"):
        return CAMPFIRE
    if short in ("chest", "trapped_chest"):
        return CHEST
    if short in ("furnace", "smoker", "blast_furnace"):
        return FURNACE
    if short in ("grass_block", "podzol", "mycelium"):
        return SNOWY
    if short in ("tall_grass", "large_fern", "sunflower", "lilac", "rose_bush", "peony"):
        return HALF2
    if short.endswith("_sapling") and not short.startswith("potted_"):
        return {"stage": _p(["0", "1"], "0")}
    if short == "chiseled_bookshelf":
        d = {"facing": _p(H4, "north")}
        for i in range(6):
            d[f"slot_{i}_occupied"] = _p(BOOL, "false")
        return d
    if name in MOD_PLAIN or (name.startswith("minecraft:") and short in PLAIN):
        return {}
    raise KeyError(f"no block-state schema for {name}")


class BlockState:
    """Immutable, hashable (id, properties) pair with the full property set."""
    __slots__ = ("id", "props", "_key")

    def __init__(self, name, **props):
        name = ns(name)
        sch = schema(name)
        full = {}
        for k, (vals, default) in sch.items():
            v = props.pop(k, default)
            if isinstance(v, bool):
                v = "true" if v else "false"
            v = str(v)
            if v not in vals:
                raise ValueError(f"{name}: bad value {k}={v} (allowed {vals})")
            full[k] = v
        if props:
            raise ValueError(f"{name}: unknown properties {sorted(props)}")
        self.id = name
        self.props = dict(sorted(full.items()))
        self._key = (name, tuple(self.props.items()))

    def with_(self, **changes):
        p = dict(self.props)
        p.update({k: ("true" if v is True else "false" if v is False else str(v)) for k, v in changes.items()})
        return BlockState(self.id, **p)

    def get(self, k):
        return self.props.get(k)

    def __eq__(self, o):
        return isinstance(o, BlockState) and o._key == self._key

    def __hash__(self):
        return hash(self._key)

    def __repr__(self):
        if not self.props:
            return self.id
        return self.id + "[" + ",".join(f"{k}={v}" for k, v in self.props.items()) + "]"

    @property
    def short(self):
        return self.id.split(":", 1)[1]


def B(name, **props):
    return BlockState(name, **props)


AIR = B("air")
