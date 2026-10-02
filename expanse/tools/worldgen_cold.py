"""The cold and temperate biomes: Maple Highlands, Aspen Parkland, Larch Taiga, Boreal Muskeg, Bluebell Woods
and Pine Heath (BiomePlacement.remap puts them in the climate table, all in temperature bands T0-T2).

Their trees, ground cover and placements, the biomes themselves and their surface rules. tools/gen_worldgen.py
calls it at three points (features, biomes, surface rules), passing itself in as W, so the builders, the
registries and the one global feature order (W.FEATURE_ORDER) are the ones it writes from.

Every placed feature here is named for its biome (maple_*, aspen_*, larch_*, muskeg_*, bluebell_*, heath_*) and
listed by that biome alone, apart from the river banks every Expanse biome shares; W.biome() appends them in
W.FEATURE_ORDER, so the game's feature sorter never finds two biomes listing a pair the other way round.

The blocks are registry/ColdBlocks.java, their assets tools/assets_cold.py, their textures tools/textures/cold.py;
the bog pools are world/feature/BogPoolFeature.java.

    biome            vanilla analog          look
    maple_highlands  dappled forest          domed sugar maples in scarlet and flame orange over golden-green
                                             grass, red leaf litter, pumpkins, mushrooms, grey rock on the slopes
    aspen_parkland   plains                  groves of tall white aspens in trembling gold, open straw-pale
                                             prairie between them, fireweed and wildflowers
    larch_taiga      snowy taiga             golden larches among dark spruce in the snow, gold needles under them
    boreal_muskeg    taiga                   a cold bog: tea-brown pools, peat and red-green sphagnum hummocks,
                                             spindly black spruce, tamarack, dead snags, cotton grass, cranberries
    bluebell_woods   forest                  tall grey-barked beeches over a carpet of bluebells, ferns and moss
    pine_heath       old growth pine taiga   flat-crowned Scots pines with orange bark on a floor of pale lichen,
                                             lingonberry, heather and juniper, pink granite boulders
"""

S = {'lakes': 1, 'local': 2, 'surface_struct': 4, 'ores': 6, 'springs': 8, 'veg': 9}
DIRS = ('north', 'east', 'south', 'west')


def _litter(W, tries, radius):
    """Leaf litter dropped round a tree's foot (vanilla's place_on_ground, as its leaf-litter oaks have it). Leaf
    litter takes the biome's dry foliage colour: red under the maples, gold under aspens and larches."""
    states = [(W.state('minecraft:leaf_litter', facing=f, segment_amount=a), 1) for a in (1, 2, 3) for f in DIRS]
    return {'type': 'minecraft:place_on_ground', 'block_state_provider': W.weighted_states(*states),
            'height': 2, 'radius': radius, 'tries': tries}


def _straight(base, a, b):
    return {'type': 'minecraft:straight_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': b}


def _spruce_crown(W, radius, offset, trunk_height):
    return {'type': 'minecraft:spruce_foliage_placer', 'radius': radius, 'offset': offset, 'trunk_height': trunk_height}


SPRUCE_SIZE = {'type': 'minecraft:two_layers_feature_size', 'limit': 2, 'lower_size': 0, 'upper_size': 2}
BEEHIVE = {'type': 'minecraft:beehive', 'probability': 0.05}


def _trees(W):
    # Sugar maple: a short straight trunk dividing into a ring of rising limbs, each carrying a dome, so the
    # crown is one broad round head of scarlet or flame orange (or both: a tree on the turn).
    maple_trunk = lambda: W.crown_trunk(5, 2, 1, W.uniform(4, 6), W.uniform(2, 3), 0.1)
    maple = [_litter(W, 96, 4), W.SHELF_MUSHROOMS(0.08)]
    W.tree('maple', 'maple_log', maple_trunk(), 'maple_leaves', W.CLUMP(2, 2, 0.3), decorators=maple)
    W.tree('orange_maple', 'maple_log', maple_trunk(), 'orange_maple_leaves', W.CLUMP(2, 2, 0.3), decorators=maple)
    W.tree('mottled_maple', 'maple_log', maple_trunk(),
           W.weighted_states((W.LEAVES('maple_leaves'), 3), (W.LEAVES('orange_maple_leaves'), 2)), W.CLUMP(2, 2, 0.3), decorators=maple)
    # Quaking aspen: a tall, slim, dead-straight white trunk with a small oval crown of gold at the top.
    aspen_trunk = lambda: W.crown_trunk(10, 3, 2, W.uniform(2, 3), 1, 0.0)
    W.tree('aspen', 'aspen_log', aspen_trunk(), 'aspen_leaves', W.CLUMP(2, 2, 0.35), decorators=[_litter(W, 32, 3)])
    W.tree('aspen_bees', 'aspen_log', aspen_trunk(), 'aspen_leaves', W.CLUMP(2, 2, 0.35), decorators=[_litter(W, 32, 3), BEEHIVE])
    # Beech: a smooth grey column under a high, wide, flat-domed canopy, so the floor below stays open.
    beech_trunk = lambda: W.crown_trunk(7, 3, 2, W.uniform(5, 7), W.uniform(3, 4), 0.05)
    beech = [_litter(W, 64, 4), W.SHELF_MUSHROOMS(0.12)]
    W.tree('beech', 'beech_log', beech_trunk(), 'beech_leaves', W.CLUMP(3, 1, 0.3), decorators=beech)
    W.tree('beech_bees', 'beech_log', beech_trunk(), 'beech_leaves', W.CLUMP(3, 1, 0.3), decorators=beech + [BEEHIVE])
    # Scots pine: a tall bare trunk (orange in the crown) carrying flat pads of blue-green needles at the top.
    # Young pines are still conical.
    W.tree('scots_pine', 'pine_log', W.crown_trunk(11, 4, 3, W.uniform(3, 5), W.uniform(2, 3), 0.0), 'pine_leaves', W.CLUMP(2, 1, 0.3))
    W.tree('young_pine', 'pine_log', _straight(5, 2, 1), 'pine_leaves',
           _spruce_crown(W, W.uniform(2, 3), W.uniform(0, 1), W.uniform(1, 2)), SPRUCE_SIZE)
    # Larch: a spruce's cone, more open and taller, of soft gold needles that drift down in autumn.
    W.tree('larch', 'minecraft:spruce_log', _straight(8, 3, 2), 'larch_leaves',
           _spruce_crown(W, W.uniform(2, 3), W.uniform(0, 1), W.uniform(2, 3)), SPRUCE_SIZE, decorators=[_litter(W, 40, 3)])
    # Black spruce of the bog: spindly, narrow as a bottle brush, often stunted.
    W.tree('black_spruce', 'minecraft:spruce_log', _straight(4, 3, 1), 'minecraft:spruce_leaves',
           _spruce_crown(W, 1, W.uniform(0, 1), W.uniform(0, 1)), SPRUCE_SIZE)
    W.tree('tall_black_spruce', 'minecraft:spruce_log', _straight(8, 3, 2), 'minecraft:spruce_leaves',
           {'type': 'minecraft:pine_foliage_placer', 'radius': 1, 'offset': 1, 'height': W.uniform(4, 6)}, SPRUCE_SIZE)

    W.fallen('fallen_maple', 'maple_log', W.uniform(4, 7), log_decorators=[W.LOG_TOPPING, W.SHELF_MUSHROOMS(0.4)])
    W.fallen('fallen_aspen', 'aspen_log', W.uniform(5, 8))
    W.fallen('fallen_beech', 'beech_log', W.uniform(5, 8), log_decorators=[W.LOG_TOPPING, W.SHELF_MUSHROOMS(0.6)])
    W.fallen('fallen_pine', 'pine_log', W.uniform(5, 9), stump_decorators=[])

    W.selector('maple_highlands_trees', W.e('maple'), (W.e('orange_maple'), 0.4), (W.e('mottled_maple'), 0.25),
               ('minecraft:spruce', 0.06), ('minecraft:birch', 0.05), (W.e('fallen_maple'), 0.04))
    W.selector('aspen_grove', W.e('aspen'), (W.e('aspen_bees'), 0.08), (W.e('fallen_aspen'), 0.05))
    W.selector('larch_taiga_trees', W.e('larch'), ('minecraft:spruce', 0.35), ('minecraft:pine', 0.08),
               ('minecraft:fallen_spruce_tree', 0.04))
    W.selector('muskeg_trees', W.e('black_spruce'), (W.e('tall_black_spruce'), 0.3), (W.e('larch'), 0.2))
    W.selector('bluebell_woods_trees', W.e('beech'), (W.e('beech_bees'), 0.05), ('minecraft:oak', 0.12), ('minecraft:birch', 0.06),
               ('minecraft:fancy_oak', 0.04), (W.e('fallen_beech'), 0.04))
    W.selector('pine_heath_trees', W.e('scots_pine'), (W.e('young_pine'), 0.22), ('minecraft:birch', 0.08), (W.e('fallen_pine'), 0.05))


def features(W, jar):
    """Configured and placed features; the placements are registered in the order biomes append them."""
    _trees(W)
    v = lambda block, **props: W.state(f'minecraft:{block}', **props)
    FERN, LARGE_FERN, BUSH, MOSS, GRASS = v('fern'), v('large_fern', half='lower'), v('bush'), v('moss_carpet'), v('short_grass')
    BERRIES = v('sweet_berry_bush', age=3)
    on_rock = {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:forest_rock_can_place_on'}
    patch = lambda n: [W.count(n), W.IN_SQUARE, W.heightmap('WORLD_SURFACE_WG'), W.BIOME]
    rocks = lambda frequency: [frequency, W.IN_SQUARE, W.heightmap('MOTION_BLOCKING'), W.BIOME, W.clear(4)]

    # ---- configured: plants and ground
    W.simple('bluebells', W.weighted_states(*[(W.state('bluebells', facing=f, flower_amount=a), 1) for a in (1, 2, 3, 4) for f in DIRS]))
    W.simple('fireweed', W.state('fireweed', half='lower'))
    W.simple('cotton_grass', W.state('cotton_grass'))
    W.simple('cranberry_bush', W.state('cranberry_bush'))
    W.simple('reindeer_lichen', W.state('reindeer_lichen'))
    W.simple('sphagnum_carpet', W.state('sphagnum_moss_carpet'))
    W.simple('woodland_moss', MOSS)
    W.simple('maple_floor', W.weighted_states((FERN, 4), (BUSH, 3), (v('brown_mushroom'), 2), (v('red_mushroom'), 1), (GRASS, 3)))
    W.simple('bluebell_undergrowth', W.weighted_states((FERN, 5), (LARGE_FERN, 2), (GRASS, 3), (v('lily_of_the_valley'), 2),
                                                       (v('oxeye_daisy'), 1), (v('dandelion'), 1)))
    W.simple('prairie_flowers', W.weighted_states((v('oxeye_daisy'), 2), (v('cornflower'), 1), (v('dandelion'), 2), (v('allium'), 1),
                                                  (GRASS, 4), (v('tall_grass', half='lower'), 2)))
    W.simple('prairie_scrub', W.weighted_states((BUSH, 3), (BERRIES, 1)))                       # wild rose and saskatoon
    W.simple('larch_floor', W.weighted_states((FERN, 4), (LARGE_FERN, 1), (GRASS, 2), (BERRIES, 1)))
    W.simple('muskeg_floor', W.weighted_states((BUSH, 3), (GRASS, 3), (FERN, 2), (W.state('cotton_grass'), 2)))   # labrador tea, sedge
    W.simple('heath_scrub', W.weighted_states((W.state('cranberry_bush'), 4), (BUSH, 3), (W.state('heather'), 3), (BERRIES, 1)))
    W.simple('heath_bracken', W.weighted_states((FERN, 3), (LARGE_FERN, 1)))

    # A bog hummock: a low cushion of sphagnum with cotton grass, cranberry and more sphagnum on it.
    def cushion(name, ground, plants, radius, chance):
        W.feature(name, {
            'type': 'minecraft:vegetation_patch', 'depth': 1, 'extra_bottom_block_chance': 0.0, 'extra_edge_column_chance': 0.3,
            'ground_state': ground, 'replaceable': '#minecraft:moss_replaceable', 'surface': 'floor', 'vegetation_chance': chance,
            'vegetation_feature': {'feature': {'type': 'minecraft:simple_block', 'to_place': plants}, 'placement': []},
            'vertical_range': 5, 'xz_radius': radius})
    cushion('muskeg_hummock', W.state('sphagnum_moss'),
            W.weighted_states((W.state('sphagnum_moss_carpet'), 5), (W.state('cotton_grass'), 3), (W.state('cranberry_bush'), 2), (GRASS, 1)),
            W.uniform(2, 4), 0.6)
    # A mat of lichen on the heath: pale moss underfoot, reindeer lichen tufts and moss carpet on it.
    cushion('heath_lichen_mat', v('pale_moss_block'),
            W.weighted_states((W.state('reindeer_lichen'), 4), (v('pale_moss_carpet'), 2), (GRASS, 1)), W.uniform(2, 4), 0.5)
    W.feature('bog_pool', {'type': W.e('bog_pool'), 'liner': W.e('peat'), 'rim': W.e('sphagnum_moss'), 'rim_chance': 0.55,
                           'radius': W.uniform(2, 4)})
    W.feature('spruce_snag', {'type': 'minecraft:block_column', 'allowed_placement': W.AIR, 'direction': 'up', 'prioritize_tip': False,
                              'layers': [{'height': W.uniform(3, 6), 'provider': v('spruce_log', axis='y')}]})
    W.feature('granite_boulder', {'type': 'minecraft:block_blob', 'state': 'minecraft:granite', 'can_place_on': on_rock})

    # ======================== placements (W.FEATURE_ORDER is the order below)
    # ---- the bog's pools, before anything grows; hummocks and lichen mats before the trees
    W.placed('muskeg_pools', W.e('bog_pool'), [W.count(W.weighted_int((0, 2), (1, 3), (2, 3), (3, 2))), W.IN_SQUARE,
                                               W.heightmap('WORLD_SURFACE_WG'), W.BIOME, W.clear(6)], S['lakes'])
    W.placed('maple_boulders', W.e('moor_boulder'), rocks(W.count(W.weighted_int((0, 4), (1, 2), (2, 1)))), S['local'])
    W.placed('larch_boulders', W.e('moor_boulder'), rocks(W.rarity(4)), S['local'])
    W.placed('bluebell_boulders', W.e('mossy_boulder'), rocks(W.rarity(4)), S['local'])
    W.placed('heath_boulders', W.e('granite_boulder'), rocks(W.count(W.weighted_int((0, 3), (1, 2), (2, 1)))), S['local'])
    W.placed('muskeg_hummocks', W.e('muskeg_hummock'), patch(W.uniform(1, 3)) + [W.clear(4)], S['local'])
    W.placed('heath_lichen_mats', W.e('heath_lichen_mat'), patch(W.uniform(1, 3)) + [W.clear(3)], S['local'])

    # ---- trees
    W.placed('maple_trees', W.e('maple_highlands_trees'), W.tree_placement(W.weighted_int((4, 3), (6, 4), (8, 2)), W.e('maple_sapling')), S['veg'])
    # Aspens grow in clonal groves: dense where the slow flower noise is high, open prairie where it is low,
    # with a lone tree here and there between.
    grove = lambda first: [first, W.IN_SQUARE, W.water_depth(0), W.heightmap('OCEAN_FLOOR'),
                           W.predicate(W.survives(W.e('aspen_sapling'))), W.BIOME, W.clear(3)]
    W.placed('aspen_groves', W.e('aspen_grove'), grove(W.drifts(-0.05, 0, 9)), S['veg'])
    W.placed('aspen_lone_trees', W.e('aspen_grove'), grove(W.rarity(3)), S['veg'])
    W.placed('larch_trees', W.e('larch_taiga_trees'), W.tree_placement(W.weighted_int((6, 3), (8, 3), (10, 1)), W.e('larch_sapling')), S['veg'])
    W.placed('muskeg_trees', W.e('muskeg_trees'), W.tree_placement(W.weighted_int((1, 3), (2, 3), (4, 2), (7, 1)), 'minecraft:spruce_sapling'),
             S['veg'])
    W.placed('muskeg_snags', W.e('spruce_snag'), [W.rarity(3), W.IN_SQUARE, W.heightmap('MOTION_BLOCKING'), W.BIOME,
                                                  W.predicate(W.survives('minecraft:spruce_sapling')), W.clear(3)], S['veg'])
    W.placed('bluebell_trees', W.e('bluebell_woods_trees'), W.tree_placement(W.weighted_int((4, 3), (5, 3), (7, 1)), W.e('beech_sapling')),
             S['veg'])
    W.placed('heath_trees', W.e('pine_heath_trees'), W.tree_placement(W.weighted_int((1, 3), (2, 4), (3, 2), (5, 1)), W.e('pine_sapling')),
             S['veg'])
    W.placed('larch_shrubs', W.e('spruce_shrub'), W.tree_placement(W.weighted_int((0, 3), (1, 2), (2, 1)), 'minecraft:spruce_sapling'), S['veg'])
    W.placed('heath_junipers', W.e('spruce_shrub'), W.tree_placement(W.weighted_int((0, 2), (1, 2), (2, 1)), 'minecraft:spruce_sapling'),
             S['veg'])

    # ---- the floor
    W.placed('maple_floor', W.e('maple_floor'), W.cover(2, 20, 6, 2), S['veg'])
    W.placed('maple_leaf_litter', 'minecraft:leaf_litter', W.cover(3, 32, 7, 2), S['veg'])
    W.placed('maple_pumpkins', 'minecraft:pumpkin', W.cover(W.rarity(10), 24, 5, 2), S['veg'])
    W.placed('aspen_fireweed', W.e('fireweed'), W.cover(W.drifts(-0.4, 2, 0), 20, 6, 2), S['veg'])
    W.placed('aspen_wildflowers', 'minecraft:wildflower', W.cover(W.drifts(0.0, 1, 3), 32, 6, 2), S['veg'])
    W.placed('aspen_prairie', W.e('prairie_flowers'), W.cover(2, 32, 7, 2), S['veg'])
    W.placed('aspen_scrub', W.e('prairie_scrub'), W.cover(W.weighted_int((0, 1), (1, 1)), 12, 4, 1, margin=3), S['veg'])
    W.placed('larch_undergrowth', W.e('larch_floor'), W.cover(1, 16, 6, 2, margin=4), S['veg'])
    W.placed('larch_fireweed', W.e('fireweed'), W.cover(W.rarity(4), 12, 5, 2), S['veg'])
    W.placed('muskeg_cotton_grass', W.e('cotton_grass'), W.cover(W.drifts(-0.2, 2, 5), 32, 6, 2), S['veg'])
    W.placed('muskeg_cranberries', W.e('cranberry_bush'), W.cover(2, 16, 5, 2), S['veg'])
    W.placed('muskeg_sphagnum', W.e('sphagnum_carpet'), W.cover(2, 24, 6, 2), S['veg'])
    W.placed('muskeg_floor', W.e('muskeg_floor'), W.cover(1, 16, 6, 2), S['veg'])
    W.placed('muskeg_lily_pads', 'minecraft:waterlily', [W.count(2), W.IN_SQUARE, W.heightmap('WORLD_SURFACE_WG'), W.BIOME,
                                                         *W.scatter(10, 5, 2), W.predicate(W.all_of(W.AIR, W.survives('minecraft:lily_pad')))],
             S['veg'])
    W.placed('bluebell_carpet', W.e('bluebells'), W.cover(W.drifts(-0.3, 2, 6), 40, 7, 2), S['veg'])
    W.placed('bluebell_undergrowth', W.e('bluebell_undergrowth'), W.cover(2, 24, 6, 2), S['veg'])
    W.placed('bluebell_moss', W.e('woodland_moss'), W.cover(1, 16, 5, 1), S['veg'])
    W.placed('bluebell_leaf_litter', 'minecraft:leaf_litter', W.cover(2, 24, 7, 2), S['veg'])
    W.placed('heath_lichen', W.e('reindeer_lichen'), W.cover(W.drifts(-0.3, 2, 5), 32, 7, 2), S['veg'])
    W.placed('heath_scrub', W.e('heath_scrub'), W.cover(2, 16, 5, 2, margin=3), S['veg'])
    W.placed('heath_heather', W.e('heather'), W.cover(W.drifts(0.2, 0, 3), 24, 6, 2), S['veg'])
    W.placed('heath_bracken', W.e('heath_bracken'), W.cover(W.rarity(2), 20, 6, 2), S['veg'])


# The river banks every Expanse biome shares (gen_worldgen.py build_features), by how cold the water is.
BANKS = ['river_clay', 'river_reeds_temperate', 'river_lily_pads', 'river_wet_bank', 'river_seagrass']
FROZEN_BANKS = ['river_clay']


def biomes(W, jar):
    A, e, music = W.ANALOGS, W.e, W.music
    A['maple_highlands'] = W.biome('maple_highlands', 'dappled_forest', jar,
        temperature=0.5, downfall=0.7,
        effects={'grass_color': '#9cac52', 'foliage_color': '#cf6f2c', 'dry_foliage_color': '#b23a1c', 'water_color': '#3d6fae'},
        attributes={'visual/sky_color': '#8ab0f2', 'visual/fog_color': '#ead7c2', 'visual/water_fog_color': '#2f5689',
                    'audio/background_music': music('minecraft:music.overworld.forest')},
        drop=['minecraft:trees_dappled_forest'],
        add=['maple_boulders', 'maple_trees', 'maple_floor', 'maple_leaf_litter', 'maple_pumpkins'] + BANKS,
        spawns={'creature': [(e('elk'), 6, 2, 4), ('minecraft:wolf', 4, 2, 4)]})

    A['aspen_parkland'] = W.biome('aspen_parkland', 'plains', jar,
        temperature=0.55, downfall=0.5,
        effects={'grass_color': '#b6b763', 'foliage_color': '#b5b24c', 'dry_foliage_color': '#caa236', 'water_color': '#4a86c8'},
        attributes={'visual/sky_color': '#8fbcff', 'visual/fog_color': '#e7e8d2',
                    'audio/background_music': music('minecraft:music.overworld.meadow')},
        drop=['minecraft:trees_plains', 'minecraft:flower_plains'],
        add=['aspen_groves', 'aspen_lone_trees', 'aspen_fireweed', 'aspen_wildflowers', 'aspen_prairie', 'aspen_scrub'] + BANKS,
        spawns={'creature': [(e('elk'), 6, 2, 4), ('minecraft:rabbit', 4, 2, 3)]})

    A['larch_taiga'] = W.biome('larch_taiga', 'snowy_taiga', jar,
        effects={'grass_color': '#86a37a', 'foliage_color': '#c29a3e', 'dry_foliage_color': '#c8922e', 'water_color': '#3d5ed0'},
        attributes={'visual/sky_color': '#a2bdf2', 'visual/fog_color': '#e0e6ee', 'visual/water_fog_color': '#2a3f8f',
                    'audio/background_music': music('minecraft:music.overworld.old_growth_taiga')},
        drop=['minecraft:trees_taiga', 'minecraft:flower_default'],
        add=['larch_boulders', 'larch_trees', 'larch_shrubs', 'larch_undergrowth', 'larch_fireweed'] + FROZEN_BANKS,
        spawns={'creature': [(e('elk'), 5, 2, 4), (e('mammoth'), 2, 1, 2)]})

    A['boreal_muskeg'] = W.biome('boreal_muskeg', 'taiga', jar,
        temperature=0.25, downfall=0.9,
        effects={'grass_color': '#8f8c4c', 'foliage_color': '#7c7f3e', 'dry_foliage_color': '#7d5a32', 'water_color': '#6b5434'},
        attributes={'visual/sky_color': '#9aaabb', 'visual/fog_color': '#bcc1b2', 'visual/water_fog_color': '#3b2c18',
                    'visual/water_fog_end_distance': {'argument': 0.6, 'modifier': 'multiply'},
                    'visual/fog_end_distance': 150.0,
                    'audio/background_music': music('minecraft:music.overworld.swamp')},
        drop=['minecraft:trees_taiga', 'minecraft:flower_default', 'minecraft:patch_large_fern', 'minecraft:patch_pumpkin',
              'minecraft:patch_sugar_cane', 'minecraft:patch_berry_common'],
        add=['muskeg_pools', 'muskeg_hummocks', 'muskeg_trees', 'muskeg_snags', 'muskeg_cotton_grass', 'muskeg_cranberries',
             'muskeg_sphagnum', 'muskeg_floor', 'muskeg_lily_pads', 'river_clay', 'river_reeds_temperate', 'river_wet_bank', 'river_seagrass'],
        spawns={'creature': [(e('elk'), 10, 1, 3), ('minecraft:frog', 8, 2, 4)]})

    A['bluebell_woods'] = W.biome('bluebell_woods', 'forest', jar,
        temperature=0.6, downfall=0.85,
        effects={'grass_color': '#6db54d', 'foliage_color': '#5aa73f', 'dry_foliage_color': '#8b6a3c', 'water_color': '#3f7ad8'},
        attributes={'visual/sky_color': '#84adff', 'visual/fog_color': '#ccd5f2', 'visual/water_fog_color': '#2f5ab0',
                    'audio/background_music': music('minecraft:music.overworld.forest')},
        drop=['minecraft:trees_birch_and_oak_leaf_litter', 'minecraft:forest_flowers', 'minecraft:flower_default'],
        add=['bluebell_boulders', 'bluebell_trees', 'bluebell_carpet', 'bluebell_undergrowth', 'bluebell_moss', 'bluebell_leaf_litter'] + BANKS,
        spawns={'creature': [('minecraft:rabbit', 5, 2, 3), ('minecraft:fox', 3, 2, 3)]})

    A['pine_heath'] = W.biome('pine_heath', 'old_growth_pine_taiga', jar,
        temperature=0.25, downfall=0.6,
        effects={'grass_color': '#8e9b5b', 'foliage_color': '#6b8a4b', 'dry_foliage_color': '#8c6c3e', 'water_color': '#3b6c98'},
        attributes={'visual/sky_color': '#8fb3e6', 'visual/fog_color': '#d5dad2',
                    'audio/background_music': music('minecraft:music.overworld.old_growth_taiga')},
        drop=['minecraft:trees_old_growth_pine_taiga', 'minecraft:forest_rock', 'minecraft:flower_default', 'minecraft:patch_dead_bush',
              'minecraft:patch_large_fern', 'minecraft:patch_sugar_cane'],
        add=['heath_boulders', 'heath_lichen_mats', 'heath_trees', 'heath_junipers', 'heath_lichen', 'heath_scrub', 'heath_heather',
             'heath_bracken'] + BANKS,
        spawns={'creature': [(e('elk'), 6, 2, 4)]})


def surface_rules(W):
    """Conditions tried before vanilla's surface (the first that matches a column wins)."""
    ON, UNDER, STEEP = W.ON_FLOOR, W.UNDER_FLOOR, W.STEEP
    c, s, b, n, here = W.cond, W.seq, W.block, W.noise, W.biome_is
    return [
        # The bog: red-green sphagnum in hummocks, bare peat between, and peat under it all.
        c(here('boreal_muskeg'), s(
            c(ON, s(
                c(n(0.2, 10.0, 'minecraft:surface_swamp'), b(W.e('sphagnum_moss'))),
                c(n(-0.3, -0.05, 'minecraft:surface_swamp'), b(W.e('peat'))))),
            c(UNDER, c(W.not_(STEEP), b(W.e('peat')))))),
        # The heath: needle-strewn podzol and stony coarse dirt in patches.
        c(here('pine_heath'), c(ON, s(
            c(n(0.3, 0.55), b('minecraft:podzol')),
            c(n(-0.55, -0.3), b('minecraft:coarse_dirt'))))),
        # The maples' hills: grey rock breaking through the steep slopes, leaf mould under the trees.
        c(here('maple_highlands'), c(ON, s(
            c(STEEP, c(n(-0.25, 0.25), b('minecraft:stone'))),
            c(n(0.45, 10.0), b('minecraft:podzol'))))),
        c(here('larch_taiga'), c(ON, c(n(0.2, 10.0), b('minecraft:podzol')))),
        c(here('bluebell_woods'), c(ON, c(n(0.6, 10.0), b('minecraft:moss_block')))),
        c(here('aspen_parkland'), c(ON, c(n(0.65, 10.0), b('minecraft:coarse_dirt')))),
    ]
