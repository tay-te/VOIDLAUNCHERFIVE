"""Chest loot tables (26.3 format: entry `modifier` object/list, `random_sequence`)."""

ENCHANT = {"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}


def _count(c):
    if isinstance(c, tuple):
        return {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "max": c[1], "min": c[0]}}
    return {"type": "minecraft:set_count", "count": c}


def item(name, weight=1, count=None, damage=None, enchant=False):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name}
    mods = []
    if count is not None:
        mods.append(_count(count))
    if damage is not None:
        mods.append({"type": "minecraft:set_damage",
                     "damage": {"type": "minecraft:uniform", "max": damage[1], "min": damage[0]}})
    if enchant:
        mods.append(dict(ENCHANT))
    if len(mods) == 1:
        e["modifier"] = mods[0]
    elif mods:
        e["modifier"] = mods
    if weight != 1:
        e["weight"] = weight
    return e


def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}


def pool(rolls, *entries):
    r = {"type": "minecraft:uniform", "max": rolls[1], "min": rolls[0]} if isinstance(rolls, tuple) else rolls
    return {"entries": list(entries), "rolls": r}


def table(name, *pools):
    return {"type": "minecraft:chest", "pools": list(pools), "random_sequence": f"expanse:chests/{name}"}


BOOK = lambda w: item("book", w, enchant=True)  # noqa: E731

TABLES = {
    "stone_circle": table(
        "stone_circle",
        pool((3, 6),
             item("flint", 10, (1, 4)), item("bone", 10, (1, 3)), item("string", 8, (1, 3)),
             item("candle", 8, (1, 2)), item("amethyst_shard", 6, (1, 4)), item("gold_nugget", 10, (2, 7)),
             item("iron_nugget", 8, (2, 8)), item("expanse:heather", 8, (1, 4)), item("expanse:edelweiss", 6, (1, 3)),
             item("bread", 6, (1, 2)), item("emerald", 3, (1, 2))),
        pool(1, empty(6), BOOK(3), item("golden_apple", 2), item("ender_pearl", 2), item("diamond", 1)),
    ),
    "ruined_watchtower": table(
        "ruined_watchtower",
        pool((3, 7),
             item("arrow", 10, (2, 8)), item("bread", 10, (1, 3)), item("iron_nugget", 8, (3, 9)),
             item("iron_ingot", 5, (1, 3)), item("gold_nugget", 6, (2, 6)), item("coal", 8, (2, 6)),
             item("torch", 6, (2, 6)), item("flint", 5, (1, 3)), item("string", 5, (1, 3)),
             item("crossbow", 2, damage=(0.25, 0.75)), item("iron_sword", 2, damage=(0.2, 0.7)),
             item("expanse:limestone_bricks", 4, (2, 6)), item("spyglass", 1)),
        pool(1, empty(5), BOOK(3), item("emerald", 4, (1, 3)), item("diamond", 1), item("shield", 1)),
    ),
    "woodland_lodge": table(
        "woodland_lodge",
        pool((4, 8),
             item("bread", 10, (1, 3)), item("expanse:cooked_venison", 8, (1, 3)), item("expanse:venison", 6, (1, 3)),
             item("apple", 8, (1, 3)), item("sweet_berries", 8, (2, 6)), item("leather", 6, (1, 3)),
             item("expanse:redwood_sapling", 5, (1, 2)), item("expanse:wisteria_sapling", 4, (1, 2)),
             item("expanse:redwood_log", 6, (2, 6)), item("wheat", 5, (2, 5)), item("lead", 3),
             item("iron_axe", 2, damage=(0.3, 0.9)), item("book", 3, (1, 2)), item("iron_nugget", 5, (1, 6))),
        pool(1, empty(6), item("emerald", 4, (1, 2)), BOOK(2), item("name_tag", 1)),
    ),
    "sun_shrine": table(
        "sun_shrine",
        pool((3, 6),
             item("gold_nugget", 12, (3, 9)), item("gold_ingot", 6, (1, 3)), item("bone", 8, (1, 4)),
             item("candle", 6, (1, 3)), item("glowstone_dust", 6, (2, 6)), item("emerald", 6, (1, 3)),
             item("expanse:opal_sand", 5, (2, 6)), item("expanse:palm_sapling", 4, (1, 2)),
             item("cactus", 4, (1, 3)), item("prize_pottery_sherd", 2), item("brush", 2)),
        pool((1, 2), empty(4), item("golden_apple", 3), BOOK(3), item("diamond", 2, (1, 2)),
             item("expanse:chiseled_opal_sandstone", 2, (1, 3))),
    ),
    "stilt_hamlet": table(
        "stilt_hamlet",
        pool((4, 7),
             item("cod", 10, (1, 4)), item("salmon", 8, (1, 3)), item("expanse:cooked_crab", 8, (1, 3)),
             item("expanse:crab_meat", 6, (1, 3)), item("string", 8, (1, 4)), item("slime_ball", 5, (1, 3)),
             item("lily_pad", 5, (1, 4)), item("expanse:willow_sapling", 5, (1, 2)), item("expanse:cattail", 5, (1, 3)),
             item("expanse:spanish_moss", 5, (1, 4)), item("bowl", 3, (1, 2)), item("gold_nugget", 5, (1, 5)),
             item("fishing_rod", 3, damage=(0.2, 0.8))),
        pool(1, empty(6), item("emerald", 4, (1, 3)), item("name_tag", 2), BOOK(2)),
    ),
    "karst_pagoda": table(
        "karst_pagoda",
        pool((4, 7),
             item("paper", 10, (2, 6)), item("book", 6, (1, 3)), item("ink_sac", 6, (1, 3)),
             item("bamboo", 6, (2, 6)), item("glass_bottle", 5, (1, 3)), item("honey_bottle", 4, (1, 2)),
             item("lantern", 4, (1, 2)), item("expanse:limestone_bricks", 5, (2, 8)),
             item("expanse:wisteria_sapling", 4, (1, 2)), item("gold_ingot", 4, (1, 3)), item("emerald", 6, (1, 4))),
        pool((1, 2), empty(3), BOOK(5), item("experience_bottle", 3, (1, 4)), item("golden_apple", 2),
             item("diamond", 1, (1, 2))),
    ),
    "lighthouse": table(
        "lighthouse",
        pool((4, 7),
             item("expanse:cooked_crab", 10, (1, 4)), item("expanse:crab_meat", 6, (1, 3)), item("cooked_cod", 8, (1, 4)),
             item("salmon", 6, (1, 3)), item("glowstone_dust", 6, (2, 6)), item("lead", 5, (1, 2)),
             item("iron_nugget", 6, (2, 8)), item("candle", 5, (1, 3)), item("expanse:palm_sapling", 4, (1, 2)),
             item("compass", 2), item("spyglass", 2), item("map", 3), item("fishing_rod", 3, damage=(0.2, 0.8))),
        pool(1, empty(6), item("emerald", 4, (1, 3)), item("nautilus_shell", 2), BOOK(2), item("name_tag", 1)),
    ),
    "lumen_shrine": table(
        "lumen_shrine",
        pool((3, 6),
             item("expanse:prismite_cluster", 10, (1, 3)), item("expanse:glowcap", 8, (1, 4)),
             item("expanse:lumen_sapling", 5, (1, 2)), item("glow_berries", 8, (2, 6)), item("glow_ink_sac", 6, (1, 3)),
             item("amethyst_shard", 6, (2, 5)), item("gold_nugget", 6, (2, 7)), item("emerald", 4, (1, 2)),
             item("experience_bottle", 4, (1, 3)), item("expanse:prismite_block", 2)),
        pool(1, empty(5), BOOK(4), item("diamond", 1), item("golden_apple", 2)),
    ),
    "frost_outpost": table(
        "frost_outpost",
        pool((4, 8),
             item("expanse:venison", 10, (1, 4)), item("expanse:cooked_venison", 8, (1, 3)), item("leather", 8, (1, 4)),
             item("rabbit_hide", 6, (1, 3)), item("arrow", 8, (2, 8)), item("snowball", 6, (2, 8)),
             item("coal", 6, (2, 5)), item("iron_ingot", 4, (1, 3)), item("expanse:frostbloom", 5, (1, 3)),
             item("bow", 2, damage=(0.3, 0.9)), item("leather_boots", 2, damage=(0.3, 0.9)),
             item("powder_snow_bucket", 1), item("rabbit_foot", 2)),
        pool(1, empty(6), item("emerald", 4, (1, 3)), BOOK(2), item("diamond", 1)),
    ),
    "nomad_camp": table(
        "nomad_camp",
        pool((4, 8),
             item("bread", 10, (1, 3)), item("wheat", 8, (2, 6)), item("leather", 8, (1, 3)), item("string", 6, (1, 4)),
             item("red_dye", 5, (1, 3)), item("yellow_dye", 5, (1, 3)), item("orange_wool", 5, (1, 4)),
             item("white_wool", 5, (1, 4)), item("lead", 4, (1, 2)), item("expanse:baobab_sapling", 4, (1, 2)),
             item("gold_nugget", 6, (2, 8)), item("emerald", 5, (1, 3)), item("bundle", 1)),
        pool(1, empty(6), item("saddle", 3), BOOK(2), item("golden_apple", 1)),
    ),
    "cloud_monastery": table(
        "cloud_monastery",
        pool((4, 7),
             item("book", 10, (1, 3)), item("paper", 10, (2, 6)), item("candle", 8, (1, 3)),
             item("honey_bottle", 5, (1, 2)), item("bread", 8, (1, 3)), item("apple", 6, (1, 3)),
             item("glow_berries", 5, (1, 4)), item("writable_book", 3), item("expanse:edelweiss", 4, (1, 3)),
             item("lantern", 3), item("emerald", 5, (1, 3)), item("gold_nugget", 5, (2, 6))),
        pool((1, 2), empty(3), BOOK(5), item("experience_bottle", 4, (1, 4)), item("golden_apple", 2),
             item("diamond", 1)),
    ),
}
