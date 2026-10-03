"""The warm and dry biomes: Olive Groves, Monsoon Forest, Ghost Gum Outback, Saguaro Flats, Kapok Rainforest and
Coral Coast (BiomePlacement.remap puts them in the climate table, all in temperature bands T3-T4).

Their trees, rocks, ground cover and placements, the biomes themselves and their surface rules. tools/gen_worldgen.py
calls it at three points (features, biomes, surface rules), passing itself in as W, so the builders, the registries
and the one global feature order (W.FEATURE_ORDER) are the ones it writes from.

Every placed feature here is named for its biome (olive_*, monsoon_*, outback_*, saguaro_*, kapok_*, coral_*) and
listed by that biome alone, apart from the river banks every Expanse biome shares; W.biome() appends them in
W.FEATURE_ORDER, so the game's feature sorter never finds two biomes listing a pair the other way round.

The blocks are registry/WarmBlocks.java, their assets tools/assets_warm.py, their textures tools/textures/warm.py;
the trunk placers world/tree/{Fork,Kapok,Tier}TrunkPlacer.java, the feature types world/feature/WarmFeatures.java.

    biome              vanilla analog   look
    olive_groves       plains           gnarled silver-green olives, dark cypress spires, lavender in rows and
                                        drifts, red poppies, dry-stone limestone walls, terra rossa
    monsoon_forest     sparse jungle    tall straight teak in flat-topped tiers over a floor of rust-brown leaf
                                        litter and dry grass, scarlet-orange flame trees, lianas, granite tors
    ghost_gum_outback  savanna          iron-red earth and red sand, white-trunked ghost gums, spinifex hummocks,
                                        cathedral termite mounds, mulga scrub, granite tors, Sturt's desert peas
    saguaro_flats      desert           many-armed saguaros with flowers on their tips, ocotillo whips, barrel
                                        cacti, mesquite, banded hoodoos, red sand, marigold superblooms
    kapok_rainforest   jungle           emergent kapoks with plank buttresses and flat umbrella crowns over a
                                        jungle canopy, lianas, orchids, elephant-ear dripleaf, oxbow ponds, mist
    coral_coast        beach            pale coral sand over limestone, pagoda-shaped sea almonds, coral in the
                                        shallows, turtle nests, washed-up coral, sea oats
"""
import json

S = {'lakes': 1, 'local': 2, 'surface_struct': 4, 'ores': 6, 'springs': 8, 'veg': 9}
DIRS = ('north', 'east', 'south', 'west')


# ======================================================================== small builders

def _fork(base, a, b, forks, length, rise, lean, subfork, flare):
    return {'type': 'expanse:fork_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': b,
            'fork_count': forks, 'fork_length': length, 'fork_rise': rise, 'lean': lean, 'subfork_chance': subfork,
            'root_flare': flare}


def _pads(radius, holes, offset=0):
    """Flat pads of leaves (the clump placer at height 0): the layered crowns of teak, kapok and sea almond."""
    return {'type': 'expanse:clump_foliage_placer', 'radius': radius, 'offset': offset, 'height': 0, 'edge_hole_chance': holes}


def _litter(W, tries, radius):
    """Leaf litter dropped round a tree's foot (vanilla's place_on_ground); it takes the biome's dry foliage colour."""
    states = [(W.state('minecraft:leaf_litter', facing=f, segment_amount=a), 1) for a in (1, 2, 3) for f in DIRS]
    return {'type': 'minecraft:place_on_ground', 'block_state_provider': W.weighted_states(*states),
            'height': 2, 'radius': radius, 'tries': tries}


def _blob(W, name, block, tag='minecraft:forest_rock_can_place_on'):
    return W.feature(name, {'type': 'minecraft:block_blob', 'state': block,
                            'can_place_on': {'type': 'minecraft:matching_block_tag', 'tag': tag}})


def _column(W, name, *layers):
    """A block_column of (height, state) layers, bottom first."""
    return W.feature(name, {'type': 'minecraft:block_column', 'allowed_placement': W.AIR, 'direction': 'up',
                            'prioritize_tip': True,
                            'layers': [{'height': h, 'provider': s} for h, s in layers]})


ON_SAND = {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:sand', 'offset': [0, -1, 0]}
# Under a desert or beach tree: soil only where there is grass; sand stays sand.
SAND_SOIL = {'type': 'minecraft:rule_based', 'rules': [
    {'if_true': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:grass_block', 'minecraft:podzol']},
     'then': {'id': 'minecraft:dirt'}}]}


def _trees(W, jar):
    v = lambda block, **props: W.state(f'minecraft:{block}', **props)
    # ---- Olive Groves
    # Olive: a short gnarled bole with knuckles of root round its foot, opening into three or four low limbs, each
    # with a rounded clump of silver-green, so the crown is wider than the tree is tall. Old trees are bigger and
    # more twisted. Bees work the blossom.
    W.tree('olive', 'olive_log', _fork(2, 1, 1, W.uniform(3, 4), W.uniform(2, 3), 0.6, 0.5, 0.3, W.uniform(2, 4)),
           'olive_leaves', W.CLUMP(2, 1, 0.25), decorators=[{'type': 'minecraft:beehive', 'probability': 0.02}])
    W.tree('old_olive', 'olive_log', _fork(3, 1, 1, W.uniform(3, 5), W.uniform(3, 4), 0.5, 0.6, 0.45, W.uniform(4, 6)),
           'olive_leaves', W.CLUMP(2, 1, 0.25))
    # Italian cypress: a dark narrow spire, the other half of every Mediterranean skyline.
    W.feature('olive_cypress', {
        'type': 'minecraft:tree', 'below_trunk_provider': W.SOIL, 'decorators': [],
        'foliage_placer': {'type': 'minecraft:spruce_foliage_placer', 'radius': 1, 'offset': 0, 'trunk_height': W.uniform(1, 2)},
        'foliage_provider': W.LEAVES('minecraft:spruce_leaves'), 'ignore_vines': True,
        'minimum_size': {'type': 'minecraft:two_layers_feature_size', 'limit': 2, 'lower_size': 0, 'upper_size': 1},
        'trunk_placer': {'type': 'minecraft:straight_trunk_placer', 'base_height': 8, 'height_rand_a': 3, 'height_rand_b': 1},
        'trunk_provider': W.LOG('minecraft:spruce_log')})
    W.selector('olive_grove_trees', W.e('olive'), (W.e('old_olive'), 0.2))

    # ---- Monsoon Forest
    # Teak: a straight grey bole, limbs climbing steeply into flat broad pads of big leaves, one storey over another;
    # it drops a thick litter of dry leaves round its foot. Lianas hang from some.
    W.tree('teak', 'teak_log', _fork(8, 3, 2, W.uniform(3, 4), W.uniform(2, 4), 1.3, 0.08, 0.35, W.uniform(0, 2)),
           'teak_leaves', _pads(3, 0.3), decorators=[_litter(W, 64, 4), {'type': 'minecraft:leave_vine', 'probability': 0.06}])
    # Flame of the forest: a small crooked tree, leafless but for a blaze of scarlet-orange blossom.
    W.tree('flame_tree', 'teak_log', _fork(3, 2, 1, W.uniform(3, 4), W.uniform(2, 3), 0.8, 0.3, 0.3, W.uniform(0, 1)),
           'flame_tree_leaves', W.CLUMP(2, 1, 0.3), decorators=[_litter(W, 24, 3)])
    W.fallen('fallen_teak', 'teak_log', W.uniform(5, 8))
    W.selector('monsoon_trees', W.e('teak'), (W.e('flame_tree'), 0.12), (W.e('fallen_teak'), 0.05))

    # ---- Ghost Gum Outback
    # Ghost gum: a tall chalk-white trunk with a lean, forking and forking again into a broken, open crown of
    # grey-green; nothing in the outback is taller. Bleached snags stand where old ones died.
    W.tree('eucalyptus', 'eucalyptus_log', _fork(5, 3, 2, W.uniform(2, 3), W.uniform(3, 5), 1.2, 0.35, 0.6, W.uniform(0, 1)),
           'eucalyptus_leaves', W.CLUMP(2, 1, 0.45))
    W.fallen('fallen_eucalyptus', 'eucalyptus_log', W.uniform(4, 7), log_decorators=[], stump_decorators=[])
    W.selector('outback_trees', W.e('eucalyptus'), (W.e('fallen_eucalyptus'), 0.08))
    _column(W, 'outback_snag', (W.uniform(3, 6), W.state('stripped_eucalyptus_log', axis='y')))
    # Mulga: an acacia shrub, a few crooked stems under a grey-green crown.
    W.tree('outback_mulga', 'minecraft:acacia_log', _fork(1, 1, 0, W.uniform(3, 4), W.uniform(1, 2), 1.0, 0.4, 0.2, 0),
           'minecraft:acacia_leaves', W.CLUMP(1, 1, 0.3))

    # ---- Saguaro Flats
    # Mesquite: low and spreading, dark crooked stems under an airy crown of fine leaves.
    W.tree('saguaro_mesquite', 'minecraft:dark_oak_log', _fork(1, 1, 0, W.uniform(3, 4), W.uniform(2, 3), 0.5, 0.5, 0.4, W.uniform(0, 1)),
           'minecraft:acacia_leaves', _pads(2, 0.45, 1), soil=SAND_SOIL)

    # ---- Kapok Rainforest
    # Kapok: the emergent, standing clear of the canopy on a bare column with plank buttresses round its foot, under
    # a flat umbrella crown; lianas hang from it. A young one (a lone sapling) is a thinner, shorter tree.
    kapok_decor = [{'type': 'minecraft:trunk_vine'}, {'type': 'minecraft:leave_vine', 'probability': 0.18}]
    W.tree('giant_kapok', 'kapok_log',
           {'type': 'expanse:kapok_trunk_placer', 'base_height': 26, 'height_rand_a': 8, 'height_rand_b': 6, 'trunk_width': 2,
            'buttress_count': W.uniform(6, 9), 'buttress_length': W.uniform(2, 4), 'limb_count': W.uniform(4, 6),
            'limb_length': W.uniform(4, 7)},
           'kapok_leaves', _pads(2, 0.3), W.TWO_LAYERS_MEGA, decorators=kapok_decor)
    W.tree('kapok', 'kapok_log',
           {'type': 'expanse:kapok_trunk_placer', 'base_height': 12, 'height_rand_a': 4, 'height_rand_b': 2, 'trunk_width': 1,
            'buttress_count': W.uniform(2, 4), 'buttress_length': W.uniform(1, 2), 'limb_count': W.uniform(3, 5),
            'limb_length': W.uniform(2, 4)},
           'kapok_leaves', _pads(2, 0.3), decorators=kapok_decor)
    W.selector('kapok_canopy', 'minecraft:jungle_tree', ('minecraft:mega_jungle_tree', 0.3), ('minecraft:fallen_jungle_tree', 0.04))
    oxbow = json.loads(jar.read('data/minecraft/worldgen/feature/lake_lava.json'))
    oxbow['barrier'] = v('mud')
    oxbow['fluid'] = v('water', level=0)
    W.feature('kapok_oxbow', oxbow)

    # ---- Coral Coast
    # Sea almond: tier on tier of level branches, each with a flat pad of leaves: a stack of plates on the shore.
    W.tree('sea_almond', 'minecraft:jungle_log',
           {'type': 'expanse:tier_trunk_placer', 'base_height': 6, 'height_rand_a': 2, 'height_rand_b': 1,
            'tier_count': W.uniform(3, 4), 'tier_spacing': 2, 'branch_count': W.uniform(3, 5), 'branch_length': W.uniform(2, 4)},
           'minecraft:jungle_leaves', _pads(1, 0.2), soil=SAND_SOIL)
    W.fallen('coral_driftwood', 'minecraft:stripped_jungle_log', W.uniform(3, 6), log_decorators=[], stump_decorators=[])


def features(W, jar):
    v = lambda block, **props: W.state(f'minecraft:{block}', **props)
    _trees(W, jar)

    # ---- rocks, walls, mounds, cacti
    _blob(W, 'olive_boulder', W.e('limestone'))
    W.feature('olive_wall', {'type': 'expanse:field_wall', 'wall': W.state('limestone_wall'),
                             'mossy_wall': v('mossy_cobblestone_wall'), 'length': W.uniform(5, 12),
                             'moss_chance': 0.2, 'gap_chance': 0.12})
    W.feature('olive_lavender_rows', {'type': 'expanse:plant_rows', 'plant': W.state('lavender'),
                                      'rows': W.uniform(3, 5), 'length': W.uniform(7, 12), 'spacing': 2})
    _blob(W, 'monsoon_tor', 'minecraft:granite')
    _blob(W, 'outback_tor', 'minecraft:granite', 'minecraft:dirt')
    W.feature('outback_termite_mound', {
        'type': 'expanse:termite_spire',
        'material': W.weighted_states((W.state('red_earth'), 4), (v('orange_terracotta'), 3), (v('terracotta'), 1)),
        'height': W.uniform(3, 7), 'radius': W.weighted_int((0, 2), (1, 3), (2, 1))})
    W.feature('saguaro', {'type': 'expanse:saguaro', 'block': W.state('saguaro'),
                          'height': W.weighted_int((2, 1), (4, 2), (5, 3), (6, 3), (7, 2), (8, 2), (9, 1)),
                          'arms': W.weighted_int((0, 2), (1, 3), (2, 3), (3, 2), (4, 1)),
                          'flower_chance': 0.35, 'flower': v('cactus_flower')})
    _column(W, 'saguaro_barrel_cactus', (1, v('cactus', age=0)), (W.weighted_int((0, 1), (1, 2)), v('cactus_flower')))
    _column(W, 'saguaro_ocotillo', (W.uniform(1, 3), W.state('ocotillo', tip=False)), (1, W.state('ocotillo', tip=True)))
    W.feature('saguaro_hoodoo', {'type': 'expanse:hoodoo',
                                 'layers': [v('terracotta'), v('orange_terracotta'), v('white_terracotta'), v('orange_terracotta'),
                                            v('red_terracotta'), v('smooth_red_sandstone'), v('brown_terracotta')],
                                 'band_height': 2, 'cap': v('smooth_sandstone'), 'height': W.uniform(6, 13), 'radius': W.uniform(1, 2)})
    _blob(W, 'saguaro_rock', 'minecraft:red_sandstone', 'minecraft:sand')

    # ---- ground cover
    FERN, LARGE_FERN, BUSH, GRASS = v('fern'), v('large_fern', half='lower'), v('bush'), v('short_grass')
    DRY, TALL_DRY, DEAD = v('short_dry_grass'), v('tall_dry_grass'), v('dead_bush')
    W.simple('olive_scrub', W.weighted_states((BUSH, 4), (DRY, 3), (TALL_DRY, 2), (GRASS, 4)))
    W.simple('olive_meadow', W.weighted_states((v('poppy'), 6), (W.state('lavender'), 3), (v('oxeye_daisy'), 2),
                                               (v('cornflower'), 1), (v('golden_dandelion'), 1), (GRASS, 4)))
    W.simple('lavender', W.state('lavender'))
    W.simple('monsoon_undergrowth', W.weighted_states((BUSH, 4), (DRY, 4), (TALL_DRY, 2), (FERN, 2), (GRASS, 3)))
    W.simple('monsoon_flowers', W.weighted_states((v('orange_tulip'), 2), (v('golden_dandelion'), 2), (v('red_tulip'), 1)))
    W.simple('spinifex', W.state('spinifex'))
    W.simple('outback_grass', W.weighted_states((DRY, 5), (TALL_DRY, 3), (DEAD, 1)))
    W.simple('outback_flowers', W.weighted_states((W.state('desert_pea'), 4), (v('golden_dandelion'), 1)))
    W.simple('saguaro_scrub', W.weighted_states((DEAD, 3), (DRY, 4), (TALL_DRY, 2)))
    W.simple('saguaro_flowers', W.weighted_states((W.state('desert_marigold'), 5), (DRY, 1)))
    W.simple('kapok_ferns', W.weighted_states((FERN, 4), (LARGE_FERN, 3), (GRASS, 2), (BUSH, 1)))
    W.simple('kapok_orchids', W.weighted_states((W.state('moth_orchid'), 3), (v('blue_orchid'), 2)))
    W.simple('kapok_pitcher', v('pitcher_plant', half='lower'))
    W.simple('coral_beach_grass', W.weighted_states((TALL_DRY, 3), (DRY, 3), (BUSH, 1), (GRASS, 1)))
    W.simple('coral_nest', W.weighted_states(*[(v('turtle_egg', eggs=n, hatch=0), 1) for n in (1, 2, 3, 4)]))
    W.simple('coral_washed_up', W.weighted_states(*[(v(f'dead_{c}_coral_fan', waterlogged=False), 1)
                                                    for c in ('brain', 'bubble', 'fire', 'horn', 'tube')]))

    # ======================== placements (FEATURE_ORDER is the order below)
    placed, e = W.placed, W.e
    cover, drifts, rarity, count, wi = W.cover, W.drifts, W.rarity, W.count, W.weighted_int
    IN_SQUARE, BIOME, hm, clear, pred, survives, AIR, all_of = W.IN_SQUARE, W.BIOME, W.heightmap, W.clear, W.predicate, W.survives, W.AIR, W.all_of

    placed('kapok_oxbow', e('kapok_oxbow'), [rarity(4), IN_SQUARE, hm('WORLD_SURFACE_WG'), BIOME, clear(10)], S['lakes'])

    placed('olive_boulders', e('olive_boulder'), [rarity(3), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])
    placed('monsoon_tors', e('monsoon_tor'), [rarity(3), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])
    placed('outback_tors', e('outback_tor'), [count(wi((0, 3), (1, 2), (2, 1))), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])
    placed('saguaro_rocks', e('saguaro_rock'), [rarity(3), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])

    placed('olive_walls', e('olive_wall'), [rarity(3), IN_SQUARE, hm('WORLD_SURFACE_WG'), BIOME, clear(8)], S['surface_struct'])
    placed('outback_termite_mounds', e('outback_termite_mound'), [count(wi((0, 2), (1, 3), (2, 1))), IN_SQUARE, hm('WORLD_SURFACE_WG'), BIOME,
                                                                  pred(survives('minecraft:dead_bush')), clear(3)], S['surface_struct'])
    placed('saguaro_hoodoos', e('saguaro_hoodoo'), [rarity(8), IN_SQUARE, hm('WORLD_SURFACE_WG'), BIOME, clear(8)], S['surface_struct'])

    # trees
    placed('olive_trees', e('olive_grove_trees'), W.tree_placement(wi((0, 2), (1, 4), (2, 4), (3, 2)), e('olive_sapling')), S['veg'])
    placed('olive_cypresses', e('olive_cypress'), W.tree_placement(wi((0, 6), (1, 2), (3, 1)), 'minecraft:spruce_sapling'), S['veg'])
    placed('monsoon_trees', e('monsoon_trees'), W.tree_placement(wi((4, 3), (6, 3), (8, 1)), e('teak_sapling')), S['veg'])
    placed('outback_trees', e('outback_trees'), W.tree_placement(wi((0, 4), (1, 4), (2, 2)), e('eucalyptus_sapling')), S['veg'])
    placed('outback_snags', e('outback_snag'), [rarity(5), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, pred(survives('minecraft:dead_bush')),
                                                clear(3)], S['veg'])
    placed('outback_mulga', e('outback_mulga'), [count(wi((0, 2), (1, 2), (2, 1))), IN_SQUARE, W.water_depth(0), hm('OCEAN_FLOOR'),
                                                 pred(survives('minecraft:dead_bush')), BIOME, clear(3)], S['veg'])
    placed('saguaro_cacti', e('saguaro'), [count(wi((1, 3), (2, 3), (3, 2), (5, 1))), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, clear(3)], S['veg'])
    placed('saguaro_mesquite', e('saguaro_mesquite'), [count(wi((0, 3), (1, 2))), IN_SQUARE, W.water_depth(0), hm('OCEAN_FLOOR'),
                                                       pred(survives('minecraft:dead_bush')), BIOME, clear(3)], S['veg'])
    placed('kapok_emergents', e('giant_kapok'), W.tree_placement(wi((0, 3), (1, 3), (2, 1)), e('kapok_sapling')) [:-1] + [clear(6)], S['veg'])
    placed('kapok_canopy_trees', e('kapok_canopy'), W.tree_placement(wi((6, 2), (9, 2), (12, 1)), 'minecraft:jungle_sapling'), S['veg'])
    placed('kapok_bushes', 'minecraft:jungle_bush', W.tree_placement(W.uniform(3, 6), 'minecraft:jungle_sapling'), S['veg'])
    placed('coral_sea_almonds', e('sea_almond'), [count(wi((0, 3), (1, 3), (2, 1))), IN_SQUARE, W.water_depth(0), hm('OCEAN_FLOOR'),
                                                  pred(survives(e('palm_sapling'))), BIOME, clear(3)], S['veg'])

    # rows and drifts, then the ground cover
    placed('olive_lavender_rows', e('olive_lavender_rows'), [rarity(4), IN_SQUARE, hm('MOTION_BLOCKING_NO_LEAVES'), BIOME, clear(8)], S['veg'])
    placed('olive_lavender', e('lavender'), cover(drifts(0.3, 0, 2), 32, 5, 2), S['veg'])
    placed('olive_meadow', e('olive_meadow'), cover(drifts(0.0, 1, 3), 32, 6, 2), S['veg'])
    placed('olive_scrub', e('olive_scrub'), cover(2, 24, 6, 2), S['veg'])
    placed('monsoon_leaf_litter', 'minecraft:leaf_litter', cover(3, 32, 7, 2), S['veg'])
    placed('monsoon_undergrowth', e('monsoon_undergrowth'), cover(3, 24, 6, 2, margin=4), S['veg'])
    placed('monsoon_flowers', e('monsoon_flowers'), cover(drifts(0.5, 0, 2), 16, 5, 2), S['veg'])
    placed('outback_spinifex', e('spinifex'), cover(4, 28, 6, 2, on=None), S['veg'])
    placed('outback_grass', e('outback_grass'), cover(2, 24, 7, 2, on=None), S['veg'])
    placed('outback_flowers', e('outback_flowers'), cover(drifts(0.4, 0, 3), 24, 6, 2, on=None), S['veg'])
    placed('saguaro_barrel_cacti', e('saguaro_barrel_cactus'), [rarity(2), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, *W.scatter(6, 4, 1),
                                                                pred(all_of(AIR, survives('minecraft:cactus')))], S['veg'])
    placed('saguaro_ocotillo', e('saguaro_ocotillo'), [rarity(2), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, *W.scatter(5, 1, 0),
                                                       pred(all_of(AIR, survives(W.state('ocotillo', tip=True))))], S['veg'])
    placed('saguaro_scrub', e('saguaro_scrub'), cover(2, 16, 7, 2, on=None), S['veg'])
    placed('saguaro_superbloom', e('saguaro_flowers'), cover(drifts(0.55, 0, 4), 32, 7, 2, on=None), S['veg'])
    placed('kapok_ferns', e('kapok_ferns'), cover(4, 32, 6, 2), S['veg'])
    placed('kapok_orchids', e('kapok_orchids'), cover(drifts(0.1, 1, 3), 16, 5, 2), S['veg'])
    placed('kapok_dripleaf', 'minecraft:dripleaf', [count(2), IN_SQUARE, hm(W.GROUND), BIOME, *W.scatter(8, 4, 1),
                                                    pred(all_of(AIR, survives('minecraft:big_dripleaf')))], S['veg'])
    placed('kapok_pitchers', e('kapok_pitcher'), cover(rarity(3), 6, 4, 1), S['veg'])
    placed('kapok_moss', 'minecraft:moss_patch', [rarity(2), IN_SQUARE, hm('WORLD_SURFACE_WG'), BIOME, clear(6)], S['veg'])
    placed('coral_reef', 'minecraft:warm_ocean_vegetation', [count(4), IN_SQUARE, hm('OCEAN_FLOOR_WG'), BIOME,
                                                            pred(W.WATER_BLOCK)], S['veg'])
    placed('coral_beach_grass', e('coral_beach_grass'), cover(2, 20, 6, 2, on=None), S['veg'])
    placed('coral_nests', e('coral_nest'), [rarity(6), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, *W.scatter(4, 2, 1),
                                            pred(all_of(AIR, ON_SAND))], S['veg'])
    placed('coral_washed_up', e('coral_washed_up'), [rarity(2), IN_SQUARE, hm('MOTION_BLOCKING'), BIOME, *W.scatter(6, 5, 1),
                                                     pred(all_of(AIR, ON_SAND))], S['veg'])
    placed('coral_driftwood', e('coral_driftwood'), [rarity(5), IN_SQUARE, W.water_depth(0), hm('OCEAN_FLOOR'), BIOME, clear(3)], S['veg'])


# ======================================================================== biomes

def biomes(W, jar):
    A, e, music, particles = W.ANALOGS, W.e, W.music, W.particles

    A['olive_groves'] = W.biome('olive_groves', 'plains', jar,
        temperature=0.9, downfall=0.35,
        effects={'grass_color': '#a6ae64', 'foliage_color': '#8e9d63', 'dry_foliage_color': '#b59a62', 'water_color': '#3aa8d2'},
        attributes={'visual/sky_color': '#86c0ff', 'visual/fog_color': '#e8e3cc', 'visual/water_fog_color': '#1b7fae',
                    'audio/background_music': music('minecraft:music.overworld.flower_forest')},
        drop=['minecraft:trees_plains', 'minecraft:flower_plains', 'minecraft:patch_grass_plain'],
        add=['olive_boulders', 'olive_walls', 'olive_trees', 'olive_cypresses', 'olive_lavender_rows', 'olive_lavender',
             'olive_meadow', 'olive_scrub',
             'river_clay', 'river_reeds_warm', 'river_lily_pads', 'river_seagrass'],
        spawns={'creature': [('minecraft:donkey', 3, 1, 2), ('minecraft:rabbit', 4, 2, 3)]})

    A['monsoon_forest'] = W.biome('monsoon_forest', 'sparse_jungle', jar,
        temperature=0.9, downfall=0.6,
        effects={'grass_color': '#a3a74c', 'foliage_color': '#93a23c', 'dry_foliage_color': '#a8692c', 'water_color': '#4c9a86'},
        attributes={'visual/sky_color': '#a6c4e0', 'visual/fog_color': '#e6d9b4', 'visual/water_fog_color': '#2f6e5e',
                    'visual/fog_end_distance': 220.0,
                    'audio/background_music': music('minecraft:music.overworld.sparse_jungle')},
        drop=['minecraft:trees_sparse_jungle', 'minecraft:flower_warm', 'minecraft:patch_grass_jungle', 'minecraft:patch_melon_sparse'],
        add=['monsoon_tors', 'monsoon_trees', 'monsoon_leaf_litter', 'monsoon_undergrowth', 'monsoon_flowers',
             'river_clay', 'river_reeds_warm', 'river_lily_pads', 'river_wet_bank', 'river_seagrass'],
        spawns={'creature': [(e('elk'), 5, 2, 3), ('minecraft:parrot', 8, 1, 2), ('minecraft:ocelot', 2, 1, 1)]})

    A['ghost_gum_outback'] = W.biome('ghost_gum_outback', 'savanna', jar,
        effects={'grass_color': '#b5a957', 'foliage_color': '#93a37f', 'dry_foliage_color': '#b57745', 'water_color': '#4a9ec0'},
        attributes={'visual/sky_color': '#6fb0ff', 'visual/fog_color': '#f0c8a0', 'visual/water_fog_color': '#2a6f8a',
                    'audio/background_music': music('minecraft:music.overworld.badlands')},
        drop=['minecraft:trees_savanna', 'minecraft:flower_warm', 'minecraft:patch_tall_grass', 'minecraft:patch_grass_savanna'],
        add=['outback_tors', 'outback_termite_mounds', 'outback_trees', 'outback_snags', 'outback_mulga', 'outback_spinifex',
             'outback_grass', 'outback_flowers',
             'river_clay', 'river_reeds_arid', 'river_seagrass'],
        spawns={'creature': [('minecraft:rabbit', 8, 2, 3), ('minecraft:camel', 2, 1, 1)]})
    _drop_spawns(W, 'ghost_gum_outback', 'minecraft:armadillo')   # no armadillos in the outback

    A['saguaro_flats'] = W.biome('saguaro_flats', 'desert', jar,
        effects={'grass_color': '#c2b26a', 'foliage_color': '#a2ad5a', 'water_color': '#3fa6c4'},
        attributes={'visual/sky_color': '#7cb2ff', 'visual/fog_color': '#f6d8b4', 'visual/water_fog_color': '#2a7f99',
                    'audio/background_music': music('minecraft:music.overworld.desert')},
        drop=['minecraft:flower_default', 'minecraft:patch_cactus_desert'],
        add=['saguaro_rocks', 'saguaro_hoodoos', 'saguaro_cacti', 'saguaro_mesquite', 'saguaro_barrel_cacti', 'saguaro_ocotillo',
             'saguaro_scrub', 'saguaro_superbloom',
             'river_clay', 'river_reeds_arid', 'river_seagrass'],
        spawns={'creature': [('minecraft:armadillo', 6, 1, 2)]})

    A['kapok_rainforest'] = W.biome('kapok_rainforest', 'jungle', jar,
        temperature=0.95, downfall=0.95,
        effects={'grass_color': '#45a83a', 'foliage_color': '#2f9a33', 'dry_foliage_color': '#6b5232', 'water_color': '#3e8a6a'},
        attributes={'visual/sky_color': '#9fcfc4', 'visual/fog_color': '#c4dccc', 'visual/water_fog_color': '#2f5d46',
                    'visual/fog_start_distance': 6.0, 'visual/fog_end_distance': 115.0,
                    'audio/background_music': music('minecraft:music.overworld.jungle')},
        drop=['minecraft:trees_jungle', 'minecraft:bamboo_light', 'minecraft:flower_warm', 'minecraft:patch_melon'],
        add=['kapok_oxbow', 'kapok_emergents', 'kapok_canopy_trees', 'kapok_bushes', 'kapok_ferns', 'kapok_orchids', 'kapok_dripleaf',
             'kapok_pitchers', 'kapok_moss',
             'river_clay', 'river_reeds_warm', 'river_lily_pads', 'river_wet_bank', 'river_seagrass'],
        spawns={'creature': [(e('capybara'), 8, 2, 4), ('minecraft:ocelot', 2, 1, 1), ('minecraft:frog', 6, 2, 4)]})
    _drop_spawns(W, 'kapok_rainforest', 'minecraft:panda')   # pandas belong to the bamboo

    A['coral_coast'] = W.biome('coral_coast', 'beach', jar,
        temperature=0.95, downfall=0.7,
        effects={'water_color': '#2ee6c8', 'grass_color': '#84d35c', 'foliage_color': '#5ec244'},
        attributes={'visual/sky_color': '#66c6ff', 'visual/fog_color': '#dff7f2', 'visual/water_fog_color': '#12a896'},
        drop=['minecraft:flower_default'],
        add=['coral_sea_almonds', 'coral_reef', 'coral_beach_grass', 'coral_nests', 'coral_washed_up', 'coral_driftwood',
             'river_clay', 'river_reeds_arid', 'river_seagrass'],
        spawns={'creature': [(e('crab'), 8, 2, 4)]})


def _drop_spawns(W, name, *entities):
    """Takes vanilla animals that would be out of place back out of a biome its analog lent them to."""
    path = W.os.path.join(W.RES, 'data', W.NS, 'worldgen', 'biome', f'{name}.json')
    with open(path) as f:
        b = json.load(f)
    cats = b['attributes']['minecraft:gameplay/natural_mob_spawns']['argument']['spawns_by_category']
    for cat in cats:
        cats[cat] = [s for s in cats[cat] if s['type'] not in entities]
    W.write(path, b)


# ======================================================================== surface rules

def surface_rules(W):
    cond, seq, block, biome_is, noise = W.cond, W.seq, W.block, W.biome_is, W.noise
    ON_FLOOR, UNDER_FLOOR, STEEP = W.ON_FLOOR, W.UNDER_FLOOR, W.STEEP
    e = W.e
    sand_layers = lambda top, under: seq(
        cond(ON_FLOOR, top),
        cond(UNDER_FLOOR, block(under)),
        cond(W.stone_depth(0, True, secondary=6), block('minecraft:sandstone')))
    return [
        # Olive Groves: terra rossa and stony ground in the grass, pale limestone breaking through on the steeps.
        cond(biome_is('olive_groves'), cond(ON_FLOOR, seq(
            cond(STEEP, cond(noise(-0.35, 0.35), block(e('limestone')))),
            cond(noise(0.3, 0.5), block('minecraft:coarse_dirt')),
            cond(noise(-0.62, -0.42), block(e('red_earth')))))),
        # Monsoon Forest: laterite and bare dusty earth under the teak.
        cond(biome_is('monsoon_forest'), cond(ON_FLOOR, seq(
            cond(noise(0.3, 10.0), block('minecraft:coarse_dirt')),
            cond(noise(-10.0, -0.55), block(e('red_earth')))))),
        # Ghost Gum Outback: iron-red earth through and through, red sand drifts, and grass only in the hollows.
        cond(biome_is('ghost_gum_outback'), seq(
            cond(ON_FLOOR, seq(
                cond(noise(0.45, 10.0), block('minecraft:red_sand')),
                cond(noise(-0.25, 0.45), block(e('red_earth'))))),
            cond(UNDER_FLOOR, block(e('red_earth'))))),
        # Saguaro Flats: sand, with drifts of red sand and stony desert pavement, over sandstone; red rock on the
        # steeps (the badlands half of its slot is often mountain country).
        cond(biome_is('saguaro_flats'), sand_layers(
            seq(cond(STEEP, block('minecraft:red_sandstone')),
                cond(noise(0.35, 10.0), block('minecraft:red_sand')),
                cond(noise(-0.15, 0.1), block('minecraft:coarse_dirt')),
                block('minecraft:sand')),
            'minecraft:sand')),
        # Kapok Rainforest: podzol and moss under the canopy, mud in the low places.
        cond(biome_is('kapok_rainforest'), cond(ON_FLOOR, seq(
            cond(noise(0.2, 10.0), block('minecraft:podzol')),
            cond(noise(-0.5, -0.2), block('minecraft:moss_block')),
            cond(noise(-10.0, -0.85), block('minecraft:mud'))))),
        # Coral Coast: coral sand over the limestone the reef laid down.
        cond(biome_is('coral_coast'), seq(
            cond(ON_FLOOR, block(e('coral_sand'))),
            cond(UNDER_FLOOR, block(e('coral_sand'))),
            cond(W.stone_depth(0, True, secondary=6), block(e('limestone'))))),
    ]
