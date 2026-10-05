"""Biome groups shared by the small structures' palettes, so a well, a shrine and a campsite in the same
country use the same list. Bare names are Expanse biomes (gen_structures prefixes them with expanse:);
vanilla biomes carry minecraft:.
"""


def mc(*names):
    return [f"minecraft:{n}" for n in names]


# green lowlands and broadleaf woods: oak, cobble and field flowers (the _meadow palettes)
MEADOW_LANDS = mc("plains", "sunflower_plains", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest",
                  "dark_forest", "dappled_forest", "meadow") + ["aspen_parkland", "bluebell_woods", "maple_highlands",
                                                                "olive_groves"]
# conifer and upland country: spruce, cobble, ferns and heather (the timber / cobble / forest palettes)
TAIGA_LANDS = mc("taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "windswept_hills",
                 "windswept_gravelly_hills", "windswept_forest") + ["pine_heath", "fjordlands", "boreal_muskeg",
                                                                    "volcanic_highlands"]
# vanilla's snowlands (no frostbloom: that belongs to the Lichen Tundra)
SNOW_LANDS = mc("snowy_plains", "ice_spikes", "snowy_taiga", "grove", "snowy_slopes") + ["larch_taiga"]
# deserts, savannas, badlands and the dry coasts: sandstone and acacia
DRY_LANDS = mc("desert", "savanna", "savanna_plateau", "windswept_savanna", "badlands", "eroded_badlands",
               "wooded_badlands") + ["coral_coast", "ghost_gum_outback", "saguaro_flats", "painted_canyons",
                                     "salt_flats"]
# jungles, bamboo and the warm wet uplands: limestone, redwood and bamboo
WARM_WET_LANDS = mc("jungle", "sparse_jungle", "bamboo_jungle", "cherry_grove") + ["monsoon_forest",
                                                                                   "kapok_rainforest", "tepui"]
SWAMP_LANDS = mc("swamp", "mangrove_swamp")
