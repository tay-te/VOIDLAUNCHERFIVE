"""Loot tables (26.3 format: entry `modifier` object/list, `random_sequence`).

Each structure has a modest public chest and a better hidden/guarded one;
ruins add an archaeology table (type minecraft:archaeology, rolled once per
brushed block like vanilla's desert_well / trail_ruins tables) and some
places add supply barrels. Balance follows comparable vanilla chests
(village houses / igloo for public chests, desert pyramid / simple dungeon /
buried treasure for hidden ones).
"""
from lore import BOOKS

ENCHANT = {"type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot"}


def _count(c):
    if isinstance(c, tuple):
        return {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "max": c[1], "min": c[0]}}
    return {"type": "minecraft:set_count", "count": c}


def item(name, weight=1, count=None, damage=None, enchant=False, extra=()):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name}
    mods = []
    if count is not None:
        mods.append(_count(count))
    if damage is not None:
        mods.append({"type": "minecraft:set_damage",
                     "damage": {"type": "minecraft:uniform", "max": damage[1], "min": damage[0]}})
    if enchant:
        mods.append(dict(ENCHANT))
    mods += list(extra)
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


def table(name, *pools, kind="chest"):
    return {"type": f"minecraft:{kind}", "pools": list(pools), "random_sequence": f"expanse:chests/{name}"}


def book(weight):
    return item("book", weight, enchant=True)


def lore_book(key, weight=1):
    """A written book carrying one of the structure journals (set_written_book_pages + set_book_cover)."""
    b = BOOKS[key]
    return item("written_book", weight, extra=[
        {"type": "minecraft:set_written_book_pages", "pages": list(b["pages"]), "mode": "replace_all"},
        {"type": "minecraft:set_book_cover", "title": b["title"], "author": b["author"], "generation": 2}])


def explorer_map(dest, decoration="minecraft:target_x", weight=1):
    """Vanilla exploration map (cf. chests/shipwreck_map) to the nearest structure in `dest` tag."""
    return {"type": "minecraft:item", "modifier": [
        {"type": "minecraft:exploration_map", "decoration": decoration, "destination": dest,
         "skip_existing_chunks": False, "zoom": 1},
        {"type": "minecraft:filtered", "item_filter": {"predicates": {"minecraft:map_id": {}}},
         "on_fail": {"type": "minecraft:discard"}}], "name": "minecraft:map", "weight": weight}


SHERDS = ["angler", "archer", "arms_up", "blade", "brewer", "burn", "danger", "explorer", "flow", "friend",
          "guster", "heart", "heartbreak", "howl", "miner", "mourner", "plenty", "prize", "scrape", "sheaf",
          "shelter", "skull", "snort"]

TABLES = {
    # ------------------------------------------------------------------ stone circle
    "stone_circle": table(
        "stone_circle",
        pool((2, 5), item("flint", 10, (1, 4)), item("bone", 10, (1, 3)), item("candle", 8, (1, 2)),
             item("amethyst_shard", 6, (1, 3)), item("gold_nugget", 10, (2, 6)), item("iron_nugget", 8, (2, 7)),
             item("expanse:heather", 8, (1, 4)), item("expanse:edelweiss", 6, (1, 3)), item("bread", 6, (1, 2)),
             item("emerald", 2)),
        pool(1, empty(8), book(2), item("ender_pearl", 1))),
    "stone_circle_hidden": table(
        "stone_circle_hidden",
        pool((3, 6), item("bone", 10, (2, 6)), item("rotten_flesh", 8, (2, 5)), item("gold_ingot", 6, (1, 4)),
             item("iron_ingot", 8, (1, 4)), item("redstone", 6, (2, 6)), item("amethyst_shard", 6, (2, 6)),
             item("emerald", 5, (1, 3)), item("candle", 5, (2, 4)), item("ender_pearl", 3, (1, 2)),
             item("iron_sword", 2, damage=(0.2, 0.6), enchant=True)),
        pool((1, 2), empty(2), book(5), item("golden_apple", 4), item("diamond", 2, (1, 2)),
             item("music_disc_13", 1), item("name_tag", 2)),
        pool(1, empty(3), lore_book("stone_circle", 1))),
    "stone_circle_archaeology": table(
        "stone_circle_archaeology", pool(1, item("bone", 4), item("flint", 3), item("gold_nugget", 3),
                                         item("amethyst_shard", 2), item("emerald"), item("skull_pottery_sherd", 2),
                                         item("mourner_pottery_sherd", 2), item("heart_pottery_sherd"),
                                         item("expanse:edelweiss", 2)), kind="archaeology"),
    # ------------------------------------------------------------------ watchtower
    "ruined_watchtower": table(
        "ruined_watchtower",
        pool((3, 6), item("arrow", 10, (2, 8)), item("bread", 10, (1, 3)), item("iron_nugget", 8, (3, 9)),
             item("coal", 8, (2, 6)), item("torch", 6, (2, 6)), item("flint", 5, (1, 3)), item("string", 5, (1, 3)),
             item("expanse:limestone_bricks", 4, (2, 6)), item("spyglass", 1)),
        pool(1, empty(6), item("emerald", 3), book(1))),
    "ruined_watchtower_hidden": table(
        "ruined_watchtower_hidden",
        pool((3, 6), item("iron_ingot", 10, (2, 5)), item("gold_ingot", 6, (1, 3)), item("arrow", 8, (6, 16)),
             item("crossbow", 4, damage=(0.5, 1.0), enchant=True), item("iron_sword", 4, damage=(0.5, 1.0)),
             item("iron_helmet", 3), item("iron_chestplate", 2), item("shield", 3), item("emerald", 6, (2, 4)),
             item("spyglass", 3)),
        pool((1, 2), empty(2), book(4), item("diamond", 2, (1, 2)), item("golden_apple", 3),
             item("saddle", 2), lore_book("ruined_watchtower", 3))),
    "ruined_watchtower_archaeology": table(
        "ruined_watchtower_archaeology", pool(1, item("iron_nugget", 4), item("arrow", 3), item("coal", 2),
                                              item("emerald"), item("archer_pottery_sherd", 2),
                                              item("blade_pottery_sherd", 2), item("shelter_pottery_sherd"),
                                              item("expanse:chiseled_limestone", 2), item("bone", 2)),
        kind="archaeology"),
    # ------------------------------------------------------------------ lodge
    "woodland_lodge": table(
        "woodland_lodge",
        pool((4, 7), item("bread", 10, (1, 3)), item("expanse:cooked_venison", 8, (1, 3)), item("apple", 8, (1, 3)),
             item("sweet_berries", 8, (2, 6)), item("leather", 6, (1, 3)), item("expanse:redwood_sapling", 5, (1, 2)),
             item("expanse:wisteria_sapling", 4, (1, 2)), item("expanse:redwood_log", 6, (2, 6)),
             item("wheat", 5, (2, 5)), item("lead", 3), item("book", 3, (1, 2)), item("iron_nugget", 5, (1, 6))),
        pool(1, empty(6), item("emerald", 3, (1, 2)), book(1))),
    "woodland_lodge_hidden": table(
        "woodland_lodge_hidden",
        pool((3, 6), item("expanse:cooked_venison", 8, (2, 5)), item("honey_bottle", 6, (1, 3)),
             item("iron_ingot", 8, (1, 4)), item("gold_ingot", 4, (1, 3)), item("emerald", 8, (2, 5)),
             item("bow", 3, enchant=True), item("iron_axe", 3, enchant=True), item("leather_chestplate", 3),
             item("expanse:wisteria_blossoms", 4, (2, 5))),
        pool((1, 2), empty(3), book(3), item("golden_apple", 2), item("diamond", 1), item("name_tag", 2),
             lore_book("woodland_lodge", 2))),
    # ------------------------------------------------------------------ sun shrine
    "sun_shrine": table(
        "sun_shrine",
        pool((2, 5), item("gold_nugget", 12, (3, 9)), item("bone", 8, (1, 4)), item("candle", 6, (1, 3)),
             item("glowstone_dust", 6, (2, 6)), item("expanse:opal_sand", 5, (2, 6)),
             item("expanse:palm_sapling", 4, (1, 2)), item("cactus", 4, (1, 3)), item("emerald", 3)),
        pool(1, empty(6), item("brush", 2), book(1))),
    "sun_shrine_hidden": table(
        "sun_shrine_hidden",
        pool((3, 6), item("gold_ingot", 10, (2, 6)), item("gold_nugget", 6, (5, 12)), item("emerald", 8, (2, 5)),
             item("glowstone_dust", 4, (4, 8)), item("golden_carrot", 4, (2, 4)), item("diamond", 3, (1, 2)),
             item("golden_horse_armor", 2), item("prize_pottery_sherd", 2)),
        pool((1, 2), empty(2), item("golden_apple", 5), book(4), item("enchanted_golden_apple", 1),
             item("expanse:chiseled_opal_sandstone", 2, (2, 4)), lore_book("sun_shrine", 2))),
    "sun_shrine_archaeology": table(
        "sun_shrine_archaeology", pool(1, item("gold_nugget", 4), item("emerald", 2), item("bone", 2),
                                       item("prize_pottery_sherd", 2), item("burn_pottery_sherd", 2),
                                       item("plenty_pottery_sherd", 2), item("diamond"),
                                       item("expanse:palm_sapling", 2), item("glowstone_dust", 2)),
        kind="archaeology"),
    # ------------------------------------------------------------------ stilt hamlet
    "stilt_hamlet": table(
        "stilt_hamlet",
        pool((4, 7), item("cod", 10, (1, 4)), item("salmon", 8, (1, 3)), item("expanse:cooked_crab", 8, (1, 3)),
             item("string", 8, (1, 4)), item("slime_ball", 5, (1, 3)), item("lily_pad", 5, (1, 4)),
             item("expanse:willow_sapling", 5, (1, 2)), item("expanse:cattail", 5, (1, 3)),
             item("expanse:spanish_moss", 5, (1, 4)), item("gold_nugget", 5, (1, 5)),
             item("fishing_rod", 3, damage=(0.2, 0.8))),
        pool(1, empty(6), item("emerald", 3, (1, 2)), item("name_tag", 1))),
    "stilt_hamlet_hidden": table(
        "stilt_hamlet_hidden",
        pool((3, 5), item("emerald", 8, (2, 6)), item("gold_ingot", 6, (1, 4)), item("iron_ingot", 6, (1, 3)),
             item("nautilus_shell", 2), item("fishing_rod", 4, enchant=True), item("heart_of_the_sea", 1),
             item("expanse:cooked_crab", 6, (3, 6)), item("slime_ball", 4, (2, 5))),
        pool((1, 2), empty(3), book(3), item("golden_apple", 2), item("name_tag", 2), lore_book("stilt_hamlet", 2))),
    "stilt_hamlet_supplies": table(
        "stilt_hamlet_supplies",
        pool((2, 5), item("cod", 10, (2, 6)), item("salmon", 8, (1, 4)), item("kelp", 6, (3, 8)),
             item("expanse:crab_meat", 8, (1, 3)), item("string", 5, (2, 4)), item("bowl", 4),
             item("bamboo", 4, (2, 6)), item("expanse:willow_log", 4, (2, 4)))),
    # ------------------------------------------------------------------ pagoda
    "karst_pagoda": table(
        "karst_pagoda",
        pool((3, 6), item("paper", 10, (2, 6)), item("book", 6, (1, 3)), item("ink_sac", 6, (1, 3)),
             item("bamboo", 6, (2, 6)), item("glass_bottle", 5, (1, 3)), item("honey_bottle", 4, (1, 2)),
             item("lantern", 4, (1, 2)), item("expanse:limestone_bricks", 5, (2, 8)),
             item("expanse:wisteria_sapling", 4, (1, 2)), item("emerald", 4, (1, 3))),
        pool(1, empty(4), book(3), item("experience_bottle", 2, (1, 3)))),
    "karst_pagoda_hidden": table(
        "karst_pagoda_hidden",
        pool((3, 6), item("emerald", 10, (3, 7)), item("gold_ingot", 6, (2, 5)), item("experience_bottle", 6, (2, 6)),
             item("expanse:wisteria_blossoms", 5, (2, 6)), item("expanse:azure_wisteria_blossoms", 4, (2, 6)),
             item("amethyst_shard", 4, (2, 6)), item("diamond", 3, (1, 2))),
        pool((1, 2), empty(1), book(6), item("golden_apple", 3), item("enchanted_golden_apple", 1),
             lore_book("karst_pagoda", 2))),
    # ------------------------------------------------------------------ lighthouse
    "lighthouse": table(
        "lighthouse",
        pool((4, 7), item("expanse:cooked_crab", 10, (1, 4)), item("expanse:crab_meat", 6, (1, 3)),
             item("cooked_cod", 8, (1, 4)), item("salmon", 6, (1, 3)), item("glowstone_dust", 6, (2, 6)),
             item("lead", 5, (1, 2)), item("iron_nugget", 6, (2, 8)), item("candle", 5, (1, 3)),
             item("expanse:palm_sapling", 4, (1, 2)), item("fishing_rod", 3, damage=(0.2, 0.8))),
        pool(1, empty(6), item("emerald", 3, (1, 3)), item("name_tag", 1))),
    "lighthouse_map": table(
        "lighthouse_map",
        pool(1, explorer_map("#expanse:on_lighthouse_maps")),
        pool((2, 4), item("paper", 8, (2, 6)), item("compass", 3), item("spyglass", 2), item("feather", 4, (1, 3)),
             item("ink_sac", 4, (1, 3)), item("map", 2)),
        pool(1, lore_book("lighthouse"))),
    "lighthouse_hidden": table(
        "lighthouse_hidden",
        pool((3, 5), item("emerald", 8, (2, 5)), item("gold_ingot", 6, (1, 4)), item("nautilus_shell", 4, (1, 2)),
             item("prismarine_shard", 6, (2, 6)), item("sea_lantern", 3, (1, 2)), item("trident", 1, damage=(0.3, 0.7)),
             item("iron_ingot", 6, (1, 4))),
        pool((1, 2), empty(2), item("heart_of_the_sea", 1), book(3), item("golden_apple", 2), item("diamond", 2))),
    # ------------------------------------------------------------------ lumen shrine
    "lumen_shrine": table(
        "lumen_shrine",
        pool((2, 5), item("expanse:prismite_cluster", 10, (1, 3)), item("expanse:glowcap", 8, (1, 4)),
             item("expanse:lumen_sapling", 5, (1, 2)), item("glow_berries", 8, (2, 6)), item("glow_ink_sac", 6, (1, 3)),
             item("amethyst_shard", 6, (2, 5)), item("gold_nugget", 6, (2, 7))),
        pool(1, empty(6), item("experience_bottle", 2, (1, 2)), book(1))),
    "lumen_shrine_hidden": table(
        "lumen_shrine_hidden",
        pool((3, 6), item("expanse:prismite_block", 4, (1, 3)), item("glowstone_dust", 6, (3, 8)),
             item("nether_wart", 6, (2, 5)), item("blaze_powder", 4, (1, 3)), item("glass_bottle", 6, (2, 4)),
             item("ghast_tear", 2), item("experience_bottle", 6, (2, 5)), item("emerald", 6, (2, 4)),
             item("diamond", 2)),
        pool((1, 2), empty(2), book(5), item("golden_apple", 3), lore_book("lumen_shrine", 2))),
    "lumen_shrine_archaeology": table(
        "lumen_shrine_archaeology", pool(1, item("amethyst_shard", 3), item("glow_ink_sac", 2),
                                         item("expanse:prismite_cluster", 3), item("gold_nugget", 2), item("emerald"),
                                         item("flow_pottery_sherd", 2), item("guster_pottery_sherd")),
        kind="archaeology"),
    # ------------------------------------------------------------------ frost outpost
    "frost_outpost": table(
        "frost_outpost",
        pool((4, 7), item("expanse:venison", 10, (1, 4)), item("expanse:cooked_venison", 8, (1, 3)),
             item("leather", 8, (1, 4)), item("rabbit_hide", 6, (1, 3)), item("arrow", 8, (2, 8)),
             item("snowball", 6, (2, 8)), item("coal", 6, (2, 5)), item("expanse:frostbloom", 5, (1, 3))),
        pool(1, empty(6), item("emerald", 3, (1, 2)), item("rabbit_foot", 2))),
    "frost_outpost_hidden": table(
        "frost_outpost_hidden",
        pool((3, 6), item("iron_ingot", 10, (2, 6)), item("gold_ingot", 4, (1, 3)), item("emerald", 8, (2, 5)),
             item("bow", 4, enchant=True), item("leather_boots", 3, enchant=True), item("powder_snow_bucket", 2),
             item("blue_ice", 3, (2, 4)), item("diamond", 2, (1, 2)), item("expanse:cooked_venison", 6, (3, 6))),
        pool((1, 2), empty(2), book(4), item("golden_apple", 2), item("netherite_scrap", 1),
             lore_book("frost_outpost", 2))),
    # ------------------------------------------------------------------ nomad camp
    "nomad_camp": table(
        "nomad_camp",
        pool((4, 7), item("bread", 10, (1, 3)), item("wheat", 8, (2, 6)), item("leather", 8, (1, 3)),
             item("string", 6, (1, 4)), item("red_dye", 5, (1, 3)), item("yellow_dye", 5, (1, 3)),
             item("orange_dye", 4, (1, 3)), item("white_wool", 5, (1, 4)), item("lead", 4, (1, 2)),
             item("expanse:baobab_sapling", 4, (1, 2)), item("gold_nugget", 6, (2, 8))),
        pool(1, empty(6), item("emerald", 3, (1, 2)), item("bundle", 1))),
    "nomad_camp_hidden": table(
        "nomad_camp_hidden",
        pool((3, 6), item("emerald", 10, (3, 8)), item("gold_ingot", 6, (2, 5)), item("saddle", 4),
             item("iron_horse_armor", 3), item("golden_horse_armor", 2), item("bundle", 3),
             item("lapis_lazuli", 4, (3, 8)), item("diamond", 1)),
        pool((1, 2), empty(2), book(3), item("golden_apple", 2), item("music_disc_cat", 1),
             lore_book("nomad_camp", 2))),
    "nomad_camp_supplies": table(
        "nomad_camp_supplies",
        pool((2, 5), item("red_dye", 6, (2, 6)), item("yellow_dye", 6, (2, 6)), item("blue_dye", 4, (1, 4)),
             item("lime_dye", 4, (1, 4)), item("white_wool", 8, (2, 6)), item("string", 6, (2, 6)),
             item("wheat", 6, (3, 8)), item("hay_block", 3, (1, 2)), item("bread", 5, (1, 3)))),
    # ------------------------------------------------------------------ cloud monastery
    "cloud_monastery": table(
        "cloud_monastery",
        pool((4, 7), item("book", 10, (1, 3)), item("paper", 10, (2, 6)), item("candle", 8, (1, 3)),
             item("honey_bottle", 5, (1, 2)), item("bread", 8, (1, 3)), item("apple", 6, (1, 3)),
             item("glow_berries", 5, (1, 4)), item("writable_book", 3), item("expanse:edelweiss", 4, (1, 3)),
             item("lantern", 3)),
        pool(1, empty(5), book(2), item("emerald", 2, (1, 2)))),
    "cloud_monastery_hidden": table(
        "cloud_monastery_hidden",
        pool((3, 6), item("experience_bottle", 8, (2, 6)), item("lapis_lazuli", 10, (4, 12)),
             item("emerald", 8, (2, 6)), item("gold_ingot", 5, (1, 4)), item("amethyst_shard", 4, (2, 6)),
             item("diamond", 2, (1, 2))),
        pool((2, 3), empty(1), book(8), item("golden_apple", 3), item("enchanted_golden_apple", 1)),
        pool(1, lore_book("cloud_monastery"))),
}
