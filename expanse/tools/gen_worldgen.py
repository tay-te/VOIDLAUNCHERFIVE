#!/usr/bin/env python3
"""
Generates VOID Expanse's world generation data: tree and terrain features, their placements, the
thirteen biomes, the surface rules that dress them, the biome tags that let vanilla structures and
mobs treat them like their vanilla cousins, and the Earth terrain pack.

    python3 tools/gen_worldgen.py [--jar path/to/minecraft-client.jar]

Each biome is built on a vanilla "analog" (Wisteria Vale on forest, Opal Dunes on desert, ...): it
starts from that biome's own feature list, drops the analog's trees and flowers, and appends ours.
Two rules keep the game's feature sorter happy (it refuses any pair of biomes that place the same two
features in opposite orders):
  * every feature we add is one of OUR placed features — even when it wraps a vanilla configured
    feature — so vanilla's orderings never constrain ours;
  * ours are always appended after the analog's, in one global order (FEATURE_ORDER below).
"""
import argparse
import copy
import json
import os
import shutil
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
EARTH = os.path.join(RES, 'resourcepacks', 'earth')
GRAND = os.path.join(RES, 'resourcepacks', 'grand_scale')  # an earlier version's pack, removed on regeneration
NS = 'expanse'
DEFAULT_JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar')

# Whether biomes list the mod's animals in their spawn tables. Off only for bring-up runs made
# before the entity types exist, since a spawn entry naming an unknown entity fails the whole biome.
WITH_MOBS = True

written = []


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')
    written.append(path)


def data(rel, obj):
    write(os.path.join(RES, 'data', rel), obj)


# ======================================================================== small builders

def e(name):
    return f'{NS}:{name}'


def state(block, **props):
    s = {'id': block if ':' in block else e(block)}
    if props:
        s['properties'] = {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in props.items()}
    return s


def uniform(lo, hi):
    return {'type': 'minecraft:uniform', 'min_inclusive': lo, 'max_inclusive': hi}


def weighted_int(*pairs):
    return {'type': 'minecraft:weighted_list', 'distribution': [{'data': d, 'weight': w} for d, w in pairs]}


def weighted_states(*pairs):
    return {'type': 'minecraft:weighted', 'entries': [{'data': s, 'weight': w} for s, w in pairs]}


def trapezoid(lo, hi):
    return {'type': 'minecraft:trapezoid', 'min': lo, 'max': hi, 'plateau': 0}


# placement modifiers
def count(n):
    return {'type': 'minecraft:count', 'count': n}


def rarity(chance):
    return {'type': 'minecraft:rarity_filter', 'chance': chance}


IN_SQUARE = {'type': 'minecraft:in_square'}
BIOME = {'type': 'minecraft:biome'}


def heightmap(kind):
    return {'type': 'minecraft:heightmap', 'heightmap': kind}


def water_depth(n):
    return {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': n}


def clear(margin):
    """Keeps a feature `margin` blocks clear of any surface structure (see ClearOfStructuresFilter)."""
    return {'type': e('clear_of_structures'), 'margin': margin}


def predicate(p):
    return {'type': 'minecraft:block_predicate_filter', 'predicate': p}


def survives(block_state):
    return {'type': 'minecraft:would_survive', 'state': block_state}


AIR = {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:air'}


def all_of(*ps):
    return {'type': 'minecraft:all_of', 'predicates': list(ps)}


def scatter(tries, xz=7, y=3):
    """Vanilla 26.x's way of making a patch: N tries scattered round the anchor, kept if the cell is air."""
    return [count(tries), {'type': 'minecraft:offset', 'x': trapezoid(-xz, xz), 'y': trapezoid(-y, y), 'z': trapezoid(-xz, xz)}]


def tree_placement(per_chunk, sapling):
    return [count(per_chunk), IN_SQUARE, water_depth(0), heightmap('OCEAN_FLOOR'),
            predicate(survives(sapling if isinstance(sapling, (str, dict)) else sapling)), BIOME, clear(3)]


def patch_placement(frequency, tries, block_for_survival, xz=7, y=3, hm='MOTION_BLOCKING', extra=None):
    """`frequency` is patches per chunk (an int or int provider) or a ready-made modifier such as rarity(n)."""
    first = frequency if isinstance(frequency, dict) and frequency.get('type', '').endswith('_filter') else count(frequency)
    p = [first, IN_SQUARE, heightmap(hm), BIOME]
    p += scatter(tries, xz, y)
    preds = [AIR, survives(block_for_survival)]
    if extra:
        preds.append(extra)
    p.append(predicate(all_of(*preds)))
    return p


FEATURES = {}   # name -> configured feature json
PLACED = {}     # name -> placed feature json
FEATURE_ORDER = []  # global order of our placed features, the order they are appended to biomes


def feature(name, obj):
    FEATURES[name] = obj
    return e(name)


def placed(name, feat, placement, step):
    PLACED[name] = ({'feature': feat, 'placement': placement}, step)
    FEATURE_ORDER.append(name)
    return name


# ======================================================================== trees

LEAVES = lambda b: state(b, distance=7, persistent=False, waterlogged=False)
LOG = lambda b: state(b, axis='y')
TWO_LAYERS = {'type': 'minecraft:two_layers_feature_size', 'limit': 1, 'lower_size': 0, 'upper_size': 1}
TWO_LAYERS_MEGA = {'type': 'minecraft:two_layers_feature_size', 'limit': 1, 'lower_size': 1, 'upper_size': 2}
SOIL = 'minecraft:soil_beneath_tree'
# Under a palm: soil only where there is grass; sand stays sand.
PALM_SOIL = {'type': 'minecraft:rule_based', 'rules': [
    {'if_true': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:grass_block', 'minecraft:podzol']}, 'then': {'id': 'minecraft:dirt'}}]}


def tree(name, trunk_block, trunk, leaves_block, foliage, size=TWO_LAYERS, decorators=(), soil=SOIL):
    return feature(name, {
        'type': 'minecraft:tree', 'below_trunk_provider': soil, 'decorators': list(decorators),
        'foliage_placer': foliage, 'foliage_provider': LEAVES(leaves_block) if isinstance(leaves_block, str) else leaves_block,
        'ignore_vines': True, 'minimum_size': size, 'trunk_placer': trunk, 'trunk_provider': LOG(trunk_block)})


def hanging(block_provider, probability, required_empty=2):
    return {'type': 'minecraft:attached_to_leaves', 'block_provider': block_provider, 'directions': ['down'],
            'exclusion_radius_xz': 1, 'exclusion_radius_y': 0, 'probability': probability, 'required_empty_blocks': required_empty}


def redwood_trunk(base, ra, rb, width, crown, branch):
    return {'type': e('redwood_trunk_placer'), 'base_height': base, 'height_rand_a': ra, 'height_rand_b': rb,
            'trunk_width': width, 'crown_start': crown, 'max_branch_length': branch}


CLUMP = lambda r, h, holes=0.25: {'type': e('clump_foliage_placer'), 'radius': r, 'offset': 0, 'height': h, 'edge_hole_chance': holes}

tree('redwood', 'redwood_log', redwood_trunk(14, 6, 4, 1, 0.42, 2), 'redwood_leaves', CLUMP(2, 1))
tree('giant_redwood', 'redwood_log', redwood_trunk(24, 10, 8, 2, 0.48, 3), 'redwood_leaves', CLUMP(2, 1), TWO_LAYERS_MEGA)
tree('colossal_redwood', 'redwood_log', redwood_trunk(32, 16, 14, 3, 0.52, 4), 'redwood_leaves', CLUMP(3, 1, 0.3), TWO_LAYERS_MEGA)

SPANISH_MOSS = state('spanish_moss', tip=True)


def crown_trunk(base, ra, rb, branches, length, wander):
    return {'type': e('crown_trunk_placer'), 'base_height': base, 'height_rand_a': ra, 'height_rand_b': rb,
            'branch_count': branches, 'branch_length': length, 'wander': wander}


def drooping(r, canopy, chance, hang):
    return {'type': e('drooping_foliage_placer'), 'radius': r, 'offset': 0, 'canopy_height': canopy, 'hang_chance': chance, 'max_hang': hang}


def cascade(block, probability, lo, hi):
    return {'type': e('hanging_cascade'), 'block': block, 'probability': probability, 'length': uniform(lo, hi)}


tree('willow', 'willow_log', crown_trunk(5, 2, 1, uniform(4, 6), uniform(2, 3), 0.15), 'willow_leaves', drooping(2, 1, 0.75, 4),
     decorators=[cascade(e('spanish_moss'), 0.1, 2, 5)])
tree('wisteria', 'wisteria_log', crown_trunk(4, 2, 2, uniform(3, 5), uniform(2, 4), 0.35), 'wisteria_leaves', drooping(2, 1, 0.85, 4),
     decorators=[cascade(e('wisteria_blossoms'), 0.3, 2, 6), {'type': 'minecraft:beehive', 'probability': 0.02}])
tree('azure_wisteria', 'wisteria_log', crown_trunk(4, 2, 2, uniform(3, 5), uniform(2, 4), 0.35), 'azure_wisteria_leaves', drooping(2, 1, 0.85, 4),
     decorators=[cascade(e('azure_wisteria_blossoms'), 0.3, 2, 6), {'type': 'minecraft:beehive', 'probability': 0.02}])
tree('palm', 'palm_log', {'type': e('palm_trunk_placer'), 'base_height': 7, 'height_rand_a': 3, 'height_rand_b': 2, 'bend': uniform(1, 4)},
     'palm_leaves', {'type': e('palm_foliage_placer'), 'radius': 4, 'offset': 0, 'missing_frond_chance': 0.1}, soil=PALM_SOIL)
# Glow lichen clings under the lumenwood canopy, so a lumen grove is lit from above at night too.
# (Glow berries would read better, but cave vines need a sturdy face above them and leaves have none.)
GLOW_LICHEN = state('minecraft:glow_lichen', up=True, down=False, north=False, south=False, east=False, west=False, waterlogged=False)
tree('lumen', 'lumen_log', {'type': e('spiral_trunk_placer'), 'base_height': 7, 'height_rand_a': 3, 'height_rand_b': 2, 'coil_radius': 1.3},
     'lumen_leaves', CLUMP(2, 2, 0.3), decorators=[hanging(GLOW_LICHEN, 0.08, 1)])
tree('baobab', 'baobab_log', {'type': e('baobab_trunk_placer'), 'base_height': 9, 'height_rand_a': 3, 'height_rand_b': 2, 'branch_count': uniform(4, 6)},
     'baobab_leaves', CLUMP(2, 1, 0.35), TWO_LAYERS_MEGA)


def mossy_copy(vanilla_name, ours, jar):
    """A vanilla tree with Spanish moss hanging from its leaves: the cloud forest's jungle."""
    obj = json.loads(jar.read(f'data/minecraft/worldgen/feature/{vanilla_name}.json'))
    obj['decorators'] = list(obj.get('decorators', [])) + [hanging(SPANISH_MOSS, 0.12)]
    return feature(ours, obj)


def fallen(name, log, length):
    return feature(name, {
        'type': 'minecraft:fallen_tree',
        'log_decorators': [{'type': 'minecraft:attached_to_logs', 'block_provider': weighted_states(
            ('minecraft:red_mushroom', 1), ('minecraft:brown_mushroom', 2), (state('minecraft:moss_carpet'), 3)),
            'directions': ['up'], 'probability': 0.2}],
        'log_length': length, 'stump_decorators': [{'type': 'minecraft:trunk_vine'}], 'trunk_provider': LOG(log)})


def selector(name, default, *choices):
    """A random_selector over configured features, each wrapped as an anonymous placed feature."""
    def wrap(f):
        return {'feature': f, 'placement': []}
    return feature(name, {'type': 'minecraft:random_selector', 'default': wrap(default),
                          'features': [{'chance': c, 'feature': wrap(f)} for f, c in choices]})


def simple(name, provider):
    return feature(name, {'type': 'minecraft:simple_block', 'to_place': provider})


def build_features(jar):
    fallen('fallen_redwood', 'redwood_log', uniform(6, 11))
    fallen('fallen_willow', 'willow_log', uniform(4, 7))
    selector('redwood_forest', e('giant_redwood'), (e('colossal_redwood'), 0.14), (e('redwood'), 0.35), (e('fallen_redwood'), 0.05))
    selector('wisteria_forest', e('wisteria'), (e('azure_wisteria'), 0.25), ('minecraft:fancy_oak_bees_005', 0.08))
    mossy_copy('mega_jungle_tree', 'mossy_mega_jungle_tree', jar)
    mossy_copy('jungle_tree', 'mossy_jungle_tree', jar)
    selector('cloud_forest_trees', e('mossy_jungle_tree'), (e('mossy_mega_jungle_tree'), 0.35), (e('lumen'), 0.03))
    selector('steppe_trees', e('baobab'), ('minecraft:acacia', 0.3))
    selector('willow_bayou_trees', e('willow'), (e('fallen_willow'), 0.04))

    # ---- ground cover
    simple('heather', state('heather'))
    simple('edelweiss', state('edelweiss'))
    simple('frostbloom', state('frostbloom'))
    simple('glowcap', state('glowcap'))
    simple('cattail', state('cattail', half='lower'))
    simple('prismite_cluster', state('prismite_cluster', facing='up', waterlogged=False))
    simple('moor_flowers', weighted_states((state('heather'), 6), (state('edelweiss'), 1), (state('minecraft:short_grass'), 3)))
    simple('alpine_flowers', weighted_states((state('edelweiss'), 4), (state('minecraft:cornflower'), 2),
                                             (state('minecraft:allium'), 1), (state('minecraft:short_grass'), 4)))
    simple('wisteria_floor', weighted_states((state('minecraft:allium'), 2), (state('minecraft:lily_of_the_valley'), 2),
                                             (state('minecraft:short_grass'), 5), (state('minecraft:fern'), 1)))
    simple('lumen_floor', weighted_states((state('glowcap'), 3), (state('minecraft:fern'), 3), (state('minecraft:short_grass'), 2),
                                          (state('minecraft:firefly_bush'), 1)))
    simple('steppe_grass', weighted_states((state('minecraft:short_dry_grass'), 5), (state('minecraft:tall_dry_grass', ), 3),
                                           (state('minecraft:short_grass'), 2)))

    # ---- rocks, spires, arches, crystals
    feature('mossy_boulder', {'type': 'minecraft:block_blob', 'state': 'minecraft:mossy_cobblestone',
                              'can_place_on': {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:forest_rock_can_place_on'}})
    feature('moor_boulder', {'type': 'minecraft:block_blob', 'state': 'minecraft:andesite',
                             'can_place_on': {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:forest_rock_can_place_on'}})
    feature('limestone_boulder', {'type': 'minecraft:block_blob', 'state': e('mossy_limestone'),
                                  'can_place_on': {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:forest_rock_can_place_on'}})
    feature('ice_boulder', {'type': 'minecraft:block_blob', 'state': 'minecraft:packed_ice',
                            'can_place_on': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:snow_block', 'minecraft:grass_block']}})
    for name, block in [('frost_spire', 'minecraft:packed_ice'), ('blue_frost_spire', 'minecraft:blue_ice')]:
        feature(name, {'type': 'minecraft:spike', 'state': block,
                       'can_place_on': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:snow_block', 'minecraft:grass_block', 'minecraft:snow']},
                       'can_replace': {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:ice_spike_replaceable'}})
    feature('karst_pillar', {
        'type': e('karst_pillar'),
        'stone': weighted_states((e('limestone'), 8), (e('mossy_limestone'), 3), ('minecraft:calcite', 1)),
        'cap': 'minecraft:grass_block', 'soil': 'minecraft:dirt',
        'height': weighted_int((24, 2), (34, 3), (46, 3), (60, 2), (76, 1)), 'radius': uniform(3, 6),
        'top_feature': {'feature': 'minecraft:jungle_bush', 'placement': []}})
    feature('opal_arch', {
        'type': e('natural_arch'),
        'layers': [e('opal_sandstone'), e('smooth_opal_sandstone'), 'minecraft:pink_terracotta', e('opal_sandstone'),
                   'minecraft:white_terracotta', e('cut_opal_sandstone'), 'minecraft:light_blue_terracotta', e('opal_sandstone')],
        'band_height': 2, 'span': uniform(12, 24), 'height': uniform(9, 17), 'thickness': uniform(2, 3)})
    feature('prismite_outcrop', {'type': e('crystal_outcrop'), 'crystal': e('prismite_block'),
                                 'cluster': e('prismite_cluster'), 'base': 'minecraft:calcite',
                                 'count': uniform(3, 7), 'height': uniform(5, 13)})
    feature('opal_dunes', {'type': e('dunes'), 'sand': e('opal_sand'), 'biomes': e('opal_dunes'), 'max_height': 9, 'wavelength': 34.0})
    feature('termite_mound', {'type': 'minecraft:block_column', 'allowed_placement': AIR, 'direction': 'up', 'prioritize_tip': True,
                              'layers': [{'height': uniform(2, 3), 'provider': state('minecraft:brown_terracotta')},
                                         {'height': uniform(1, 2), 'provider': state('minecraft:terracotta')}]})
    pond = json.loads(jar.read('data/minecraft/worldgen/feature/lake_lava.json'))
    pond['barrier'] = state('minecraft:mossy_cobblestone')
    pond['fluid'] = state('minecraft:water', level=0)
    feature('water_pond', pond)

    # ======================== placements (FEATURE_ORDER is the order below)
    S = {'lakes': 1, 'local': 2, 'surface_struct': 4, 'springs': 8, 'veg': 9}

    # Dunes go down first, before anything is placed on the sand. No placement modifiers: once per
    # chunk, and the feature itself decides column by column whether it is in the dune field.
    placed('opal_dunes', e('opal_dunes'), [], 0)
    placed('karst_pond', e('water_pond'), [rarity(5), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME, clear(10)], S['lakes'])
    placed('cloud_forest_pond', e('water_pond'), [rarity(4), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME, clear(10)], S['lakes'])

    placed('mossy_boulder', e('mossy_boulder'), [count(uniform(0, 2)), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])
    placed('moor_boulder', e('moor_boulder'), [count(weighted_int((0, 3), (1, 2), (2, 1))), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])
    placed('limestone_boulder', e('limestone_boulder'), [count(uniform(0, 1)), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])
    placed('ice_boulder', e('ice_boulder'), [rarity(3), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME, clear(4)], S['local'])
    placed('frost_spire', e('frost_spire'), [count(uniform(0, 2)), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME, clear(6)], S['surface_struct'])
    placed('blue_frost_spire', e('blue_frost_spire'), [rarity(6), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME, clear(6)], S['surface_struct'])
    placed('karst_pillar', e('karst_pillar'), [rarity(2), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME, clear(14)], S['surface_struct'])
    placed('opal_arch', e('opal_arch'), [rarity(9), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME, clear(18)], S['surface_struct'])
    placed('prismite_outcrop', e('prismite_outcrop'), [count(uniform(0, 2)), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME, clear(6)], S['surface_struct'])
    placed('termite_mound', e('termite_mound'), [rarity(4), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME,
                                                 predicate(survives('minecraft:dead_bush')), clear(3)], S['surface_struct'])

    # Waterfalls: open springs in the mountain walls, many more than vanilla's.
    placed('alpine_springs', 'minecraft:spring_water', [count(24), IN_SQUARE,
           {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': 90},
                                                         'max_inclusive': {'below_top': 24}}}, BIOME], S['springs'])

    # trees
    placed('redwood_forest', e('redwood_forest'), tree_placement(weighted_int((3, 6), (5, 3), (7, 1)), e('redwood_sapling')), S['veg'])
    placed('wisteria_forest', e('wisteria_forest'), tree_placement(weighted_int((2, 4), (4, 4), (6, 1)), e('wisteria_sapling')), S['veg'])
    placed('lumen_trees', e('lumen'), tree_placement(weighted_int((5, 4), (7, 3), (9, 1)), e('lumen_sapling')), S['veg'])
    placed('willow_trees', e('willow_bayou_trees'), [count(weighted_int((2, 4), (3, 3), (5, 1))), IN_SQUARE, water_depth(2),
                                                     heightmap('OCEAN_FLOOR'), BIOME, clear(3)], S['veg'])
    placed('steppe_trees', e('steppe_trees'), tree_placement(weighted_int((0, 6), (1, 3), (2, 1)), e('baobab_sapling')), S['veg'])
    placed('cloud_forest_trees', e('cloud_forest_trees'), tree_placement(weighted_int((8, 3), (11, 2), (14, 1)), 'minecraft:jungle_sapling'), S['veg'])
    placed('palm_trees', e('palm'), [count(weighted_int((0, 3), (1, 3), (2, 2), (3, 1))), IN_SQUARE, water_depth(0), heightmap('OCEAN_FLOOR'),
                                     predicate(survives(e('palm_sapling'))), BIOME, clear(3)], S['veg'])
    placed('moor_trees', 'minecraft:birch', [rarity(5), IN_SQUARE, water_depth(0), heightmap('OCEAN_FLOOR'),
                                             predicate(survives('minecraft:birch_sapling')), BIOME, clear(3)], S['veg'])
    placed('tundra_trees', 'minecraft:spruce', [rarity(3), IN_SQUARE, water_depth(0), heightmap('OCEAN_FLOOR'),
                                                predicate(survives('minecraft:spruce_sapling')), BIOME, clear(3)], S['veg'])
    placed('alpine_trees', 'minecraft:spruce', [count(weighted_int((0, 5), (1, 2), (3, 1))), IN_SQUARE, water_depth(0), heightmap('OCEAN_FLOOR'),
                                                predicate(survives('minecraft:spruce_sapling')), BIOME, clear(3)], S['veg'])
    placed('karst_trees', 'minecraft:jungle_bush', [count(uniform(2, 4)), IN_SQUARE, water_depth(0), heightmap('OCEAN_FLOOR'),
                                                    predicate(survives('minecraft:jungle_sapling')), BIOME, clear(3)], S['veg'])
    placed('karst_bamboo', 'minecraft:bamboo_some_podzol', [rarity(2), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME,
                                                            *scatter(24, 5, 2), predicate(all_of(AIR, survives('minecraft:bamboo_sapling'))), clear(3)], S['veg'])

    # flowers and ground cover
    placed('heather_patch', e('moor_flowers'), patch_placement(4, 48, e('heather')), S['veg'])
    placed('alpine_flowers', e('alpine_flowers'), patch_placement(3, 40, e('edelweiss')), S['veg'])
    placed('edelweiss_patch', e('edelweiss'), patch_placement(rarity(2), 12, e('edelweiss'), 4, 2), S['veg'])
    placed('frostbloom_patch', e('frostbloom'), patch_placement(2, 20, e('frostbloom'), 5, 2), S['veg'])
    placed('wisteria_floor', e('wisteria_floor'), patch_placement(3, 32, 'minecraft:short_grass'), S['veg'])
    placed('wisteria_petals', 'minecraft:flower_cherry', patch_placement(2, 32, 'minecraft:pink_petals'), S['veg'])
    placed('wisteria_leaf_litter', 'minecraft:leaf_litter', patch_placement(2, 24, 'minecraft:leaf_litter'), S['veg'])
    placed('lumen_floor', e('lumen_floor'), patch_placement(5, 40, e('glowcap')), S['veg'])
    placed('lumen_moss', 'minecraft:moss_patch', [count(uniform(1, 2)), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME], S['veg'])
    placed('glowcap_patch', e('glowcap'), patch_placement(2, 16, e('glowcap'), 4, 2), S['veg'])
    placed('cattails', e('cattail'), [count(6), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME, *scatter(24, 6, 1),
                                      predicate(all_of(AIR, survives(e('cattail')),
                                                       {'type': 'minecraft:any_of', 'predicates': [
                                                           {'type': 'minecraft:matching_fluids', 'fluids': ['minecraft:water'], 'offset': [1, -1, 0]},
                                                           {'type': 'minecraft:matching_fluids', 'fluids': ['minecraft:water'], 'offset': [-1, -1, 0]},
                                                           {'type': 'minecraft:matching_fluids', 'fluids': ['minecraft:water'], 'offset': [0, -1, 1]},
                                                           {'type': 'minecraft:matching_fluids', 'fluids': ['minecraft:water'], 'offset': [0, -1, -1]}]}))], S['veg'])
    placed('bayou_lily_pads', 'minecraft:waterlily', [count(4), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME, *scatter(10, 7, 3),
                                                      predicate(all_of(AIR, survives('minecraft:lily_pad')))], S['veg'])
    placed('bayou_fireflies', 'minecraft:firefly_bush', patch_placement(2, 12, 'minecraft:firefly_bush'), S['veg'])
    placed('steppe_grass', e('steppe_grass'), patch_placement(6, 32, 'minecraft:short_dry_grass'), S['veg'])
    placed('prismite_clusters', e('prismite_cluster'), [count(6), IN_SQUARE, heightmap('MOTION_BLOCKING'), BIOME,
                                                        predicate(all_of(AIR, survives(e('prismite_cluster'))))], S['veg'])
    placed('cloud_forest_floor', e('lumen_floor'), patch_placement(1, 16, 'minecraft:fern'), S['veg'])
    placed('cloud_forest_moss', 'minecraft:moss_patch', [count(uniform(1, 3)), IN_SQUARE, heightmap('WORLD_SURFACE_WG'), BIOME], S['veg'])


# ======================================================================== biomes

STEPS = 11


def biome(name, analog, jar, *, temperature=None, downfall=None, precipitation=None, effects=None, attributes=None,
          drop=(), add=(), spawns=None, keep_attributes=True):
    b = json.loads(jar.read(f'data/minecraft/worldgen/biome/{analog}.json'))
    if temperature is not None:
        b['temperature'] = temperature
    if downfall is not None:
        b['downfall'] = downfall
    if precipitation is not None:
        b['has_precipitation'] = precipitation
    if effects:
        b['effects'].update(effects)
    b.pop('temperature_modifier', None) if analog != 'frozen_peaks' else None
    attrs = b.setdefault('attributes', {})
    if attributes:
        attrs.update({f'minecraft:{k}': v for k, v in attributes.items()})
    feats = [list(s) for s in b['features']] + [[] for _ in range(STEPS - len(b['features']))]
    feats = [[f for f in step if f not in drop] for step in feats]
    for name_ in FEATURE_ORDER:
        if name_ in add:
            feats[PLACED[name_][1]].append(e(name_))
    b['features'] = feats
    if WITH_MOBS and spawns:
        cat = attrs['minecraft:gameplay/natural_mob_spawns']['argument']['spawns_by_category']
        for category, entries in spawns.items():
            cat.setdefault(category, [])
            for ent, weight, lo, hi in entries:
                cat[category].append({'type': ent, 'count': uniform(lo, hi) if lo != hi else lo, 'weight': weight})
    data(f'{NS}/worldgen/biome/{name}.json', b)
    return analog


def music(sound):
    return {'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': sound}}


def particles(kind, probability, **extra):
    p = {'type': kind}
    p.update(extra)
    return {'argument': [{'particle': p, 'probability': probability}], 'modifier': 'append'}


ANALOGS = {}


def build_biomes(jar):
    A = ANALOGS
    A['frostbloom_tundra'] = biome('frostbloom_tundra', 'snowy_plains', jar,
        effects={'water_color': '#3d6fd6', 'grass_color': '#9fc7c0', 'foliage_color': '#7fa8a0'},
        attributes={'visual/sky_color': '#9cc0ff', 'visual/fog_color': '#d9ecff', 'visual/water_fog_color': '#2a4fa8',
                    'visual/ambient_particles': particles('minecraft:snowflake', 0.006),
                    'audio/background_music': music('minecraft:music.overworld.snowy_slopes')},
        drop=['minecraft:trees_snowy', 'minecraft:flower_default', 'minecraft:patch_pumpkin', 'minecraft:patch_sugar_cane'],
        add=['ice_boulder', 'frost_spire', 'blue_frost_spire', 'tundra_trees', 'frostbloom_patch'],
        spawns={'creature': [(e('mammoth'), 10, 2, 4), ('minecraft:fox', 4, 2, 4)]})

    A['heather_moor'] = biome('heather_moor', 'meadow', jar,
        temperature=0.4, downfall=0.7,
        effects={'grass_color': '#97a565', 'foliage_color': '#839356', 'water_color': '#4a6a8a'},
        attributes={'visual/sky_color': '#93aed2', 'visual/fog_color': '#c3ccd6', 'visual/fog_end_distance': 170.0,
                    'audio/background_music': music('minecraft:music.overworld.meadow')},
        drop=['minecraft:flower_meadow', 'minecraft:trees_meadow', 'minecraft:wildflowers_meadow'],
        add=['moor_boulder', 'moor_trees', 'heather_patch', 'edelweiss_patch'],
        spawns={'creature': [(e('elk'), 8, 2, 4), ('minecraft:sheep', 6, 2, 4)]})

    A['wisteria_vale'] = biome('wisteria_vale', 'forest', jar,
        temperature=0.65, downfall=0.8,
        effects={'grass_color': '#79c06a', 'foliage_color': '#6fb35f', 'water_color': '#5f8fe6', 'dry_foliage_color': '#a77fc4'},
        attributes={'visual/sky_color': '#a3b6ff', 'visual/fog_color': '#e4d8f2', 'visual/water_fog_color': '#3f5fb8',
                    'audio/background_music': music('minecraft:music.overworld.cherry_grove')},
        drop=['minecraft:forest_flowers', 'minecraft:trees_birch_and_oak_leaf_litter', 'minecraft:flower_default', 'minecraft:patch_bush'],
        add=['wisteria_forest', 'wisteria_floor', 'wisteria_petals', 'wisteria_leaf_litter'],
        spawns={'creature': [('minecraft:fox', 4, 2, 3)]})

    A['redwood_giants'] = biome('redwood_giants', 'old_growth_spruce_taiga', jar,
        effects={'grass_color': '#6f9a52', 'foliage_color': '#4f7d43', 'water_color': '#3f76a4'},
        attributes={'visual/sky_color': '#8aaee0', 'visual/fog_color': '#b9c9c2', 'visual/fog_end_distance': 220.0,
                    'visual/ambient_particles': particles('minecraft:spore_blossom_air', 0.0012),
                    'audio/background_music': music('minecraft:music.overworld.old_growth_taiga')},
        drop=['minecraft:trees_old_growth_spruce_taiga', 'minecraft:flower_default'],
        add=['mossy_boulder', 'redwood_forest'],
        spawns={'creature': [(e('elk'), 12, 2, 5)]})

    A['lumen_grove'] = biome('lumen_grove', 'dark_forest', jar,
        temperature=0.6, downfall=0.9,
        effects={'grass_color': '#3f8a7a', 'foliage_color': '#2fa08f', 'water_color': '#2ab5a5', 'dry_foliage_color': '#3a6a68'},
        attributes={'visual/sky_color': '#5d7fb0', 'visual/fog_color': '#2f5563', 'visual/water_fog_color': '#1a6a66',
                    'visual/fog_end_distance': 130.0,
                    'visual/ambient_particles': particles('minecraft:firefly', 0.012),
                    'audio/background_music': music('minecraft:music.overworld.lush_caves')},
        drop=['minecraft:dark_forest_vegetation', 'minecraft:forest_flowers', 'minecraft:flower_default', 'minecraft:patch_leaf_litter'],
        add=['lumen_trees', 'lumen_moss', 'lumen_floor', 'glowcap_patch'])

    A['willow_bayou'] = biome('willow_bayou', 'swamp', jar,
        effects={'water_color': '#4b8a6b', 'foliage_color': '#7fa64a', 'grass_color': '#6f9a48', 'dry_foliage_color': '#6b5a3a'},
        attributes={'visual/sky_color': '#8db4a8', 'visual/fog_color': '#a7c2a2', 'visual/water_fog_color': '#3a6650',
                    'visual/fog_end_distance': 120.0,
                    'visual/ambient_particles': particles('minecraft:firefly', 0.004)},
        drop=['minecraft:trees_swamp', 'minecraft:flower_swamp'],
        add=['willow_trees', 'cattails', 'bayou_lily_pads', 'bayou_fireflies'],
        spawns={'creature': [(e('capybara'), 12, 2, 4)]})

    A['amber_steppe'] = biome('amber_steppe', 'savanna', jar,
        effects={'grass_color': '#d1ad48', 'foliage_color': '#b5a03f', 'water_color': '#3f94c4'},
        attributes={'visual/sky_color': '#9fc6ff', 'visual/fog_color': '#ecdcb2',
                    'audio/background_music': music('minecraft:music.overworld.badlands')},
        drop=['minecraft:trees_savanna', 'minecraft:flower_warm', 'minecraft:patch_tall_grass'],
        add=['termite_mound', 'steppe_trees', 'steppe_grass'],
        spawns={'creature': [(e('elk'), 4, 2, 4)]})

    A['opal_dunes'] = biome('opal_dunes', 'desert', jar,
        effects={'water_color': '#6fd2d8', 'grass_color': '#c9b88a', 'foliage_color': '#b4a874'},
        attributes={'visual/sky_color': '#acb4ff', 'visual/fog_color': '#f4d8e6', 'visual/water_fog_color': '#3aa4b4'},
        drop=['minecraft:flower_default'],
        add=['opal_dunes', 'opal_arch'])

    A['jade_karst'] = biome('jade_karst', 'jungle', jar,
        temperature=0.85, downfall=0.85,
        effects={'water_color': '#3fc2a2', 'grass_color': '#62b85a', 'foliage_color': '#4fa84a'},
        attributes={'visual/sky_color': '#9ccac2', 'visual/fog_color': '#c4e0d6', 'visual/water_fog_color': '#1f8a74',
                    'visual/fog_end_distance': 190.0,
                    'audio/background_music': music('minecraft:music.overworld.bamboo_jungle')},
        drop=['minecraft:trees_jungle', 'minecraft:bamboo_light', 'minecraft:flower_warm'],
        add=['karst_pond', 'limestone_boulder', 'karst_pillar', 'karst_trees', 'karst_bamboo'],
        spawns={'creature': [(e('capybara'), 8, 2, 4), ('minecraft:panda', 2, 1, 2)]})

    A['cloud_forest'] = biome('cloud_forest', 'jungle', jar,
        temperature=0.7, downfall=1.0,
        effects={'water_color': '#5fa8c0', 'grass_color': '#4f9a58', 'foliage_color': '#3f8f4a'},
        attributes={'visual/sky_color': '#b7c8d2', 'visual/fog_color': '#d4dfe2', 'visual/water_fog_color': '#3f7f90',
                    'visual/fog_start_distance': 4.0, 'visual/fog_end_distance': 72.0,
                    'audio/background_music': music('minecraft:music.overworld.sparse_jungle')},
        drop=['minecraft:trees_jungle', 'minecraft:bamboo_light', 'minecraft:patch_melon'],
        add=['cloud_forest_pond', 'cloud_forest_trees', 'cloud_forest_moss', 'cloud_forest_floor'],
        spawns={'creature': [(e('capybara'), 6, 1, 3)]})

    A['verdant_peaks'] = biome('verdant_peaks', 'jagged_peaks', jar,
        temperature=0.3, downfall=0.6, precipitation=True,
        effects={'grass_color': '#7fb85a', 'foliage_color': '#6aa64a', 'water_color': '#3f86e0'},
        attributes={'visual/sky_color': '#78a8ff', 'visual/fog_color': '#cfe0f5',
                    'audio/background_music': music('minecraft:music.overworld.meadow')},
        add=['alpine_springs', 'alpine_trees', 'alpine_flowers'],
        spawns={'creature': [(e('elk'), 6, 2, 3), ('minecraft:sheep', 4, 2, 3)]})

    A['prismatic_peaks'] = biome('prismatic_peaks', 'stony_peaks', jar,
        effects={'water_color': '#5fc0ff'},
        attributes={'visual/sky_color': '#86b0ff', 'visual/fog_color': '#e2e8ff',
                    'visual/ambient_particles': particles('minecraft:end_rod', 0.0015),
                    'audio/background_music': music('minecraft:music.overworld.frozen_peaks')},
        add=['prismite_outcrop', 'prismite_clusters'])

    A['palm_coast'] = biome('palm_coast', 'beach', jar,
        temperature=0.9, downfall=0.6,
        effects={'water_color': '#22d0e0', 'grass_color': '#7dcf5a', 'foliage_color': '#5fbf4a'},
        attributes={'visual/sky_color': '#76c0ff', 'visual/fog_color': '#d6f0ff', 'visual/water_fog_color': '#139ab8'},
        drop=['minecraft:flower_default'],
        add=['palm_trees'],
        spawns={'creature': [(e('crab'), 10, 2, 5)]})


# ======================================================================== surface rules

def cond(if_true, then_run):
    return {'type': 'minecraft:condition', 'if_true': if_true, 'then_run': then_run}


def seq(*rules):
    return {'type': 'minecraft:sequence', 'sequence': list(rules)}


def block(b):
    return {'type': 'minecraft:block', 'result_state': b}


def biome_is(*names):
    return {'type': 'minecraft:biome', 'biome_is': [e(n) for n in names]}


def noise(lo, hi, n='minecraft:surface'):
    return {'type': 'minecraft:noise_threshold', 'noise': n, 'min_threshold': lo, 'max_threshold': hi}


def y_above(y, mult=0):
    return {'type': 'minecraft:y_above', 'anchor': {'absolute': y}, 'surface_depth_multiplier': mult, 'add_stone_depth': False}


def not_(c):
    return {'type': 'minecraft:not', 'invert': c}


def stone_depth(offset, add_surface, kind='floor', secondary=0):
    return {'type': 'minecraft:stone_depth', 'offset': offset, 'add_surface_depth': add_surface,
            'secondary_depth_range': secondary, 'surface_type': kind}


ON_FLOOR = 'minecraft:on_floor'
UNDER_FLOOR = 'minecraft:under_floor'
STEEP = {'type': 'minecraft:steep'}


def build_surface_rules():
    sand_beach = lambda top, under: seq(
        cond(ON_FLOOR, block(top)),
        cond(UNDER_FLOOR, block(top)),
        cond(stone_depth(0, True, secondary=6), block(under)))
    rules = seq(
        cond(biome_is('opal_dunes'), sand_beach(e('opal_sand'), e('opal_sandstone'))),
        cond(biome_is('palm_coast'), sand_beach('minecraft:sand', 'minecraft:sandstone')),
        cond(biome_is('verdant_peaks'), seq(
            cond(ON_FLOOR, seq(
                cond(y_above(300), block('minecraft:snow_block')),
                cond(STEEP, cond(noise(-0.2, 0.2), block('minecraft:mossy_cobblestone'))),
                cond(STEEP, block('minecraft:stone')),
                block('minecraft:grass_block'))),
            cond(UNDER_FLOOR, cond(not_(STEEP), block('minecraft:dirt'))))),
        cond(biome_is('prismatic_peaks'), seq(
            cond(ON_FLOOR, seq(
                cond(noise(0.0, 0.35), block('minecraft:snow_block')),
                cond(noise(-0.35, -0.15), block('minecraft:packed_ice')),
                block('minecraft:calcite'))),
            cond(UNDER_FLOOR, block('minecraft:calcite')))),
        cond(biome_is('frostbloom_tundra'), cond(ON_FLOOR, cond(noise(0.15, 10.0), block('minecraft:snow_block')))),
        cond(biome_is('heather_moor'), cond(ON_FLOOR, seq(
            cond(noise(0.35, 0.6), block('minecraft:coarse_dirt')),
            cond(noise(-0.6, -0.4), block('minecraft:podzol'))))),
        cond(biome_is('redwood_giants'), cond(ON_FLOOR, seq(
            cond(noise(0.15, 10.0), block('minecraft:podzol')),
            cond(noise(-0.95, -0.6), block('minecraft:coarse_dirt'))))),
        cond(biome_is('lumen_grove', 'cloud_forest'), cond(ON_FLOOR, cond(noise(0.3, 10.0), block('minecraft:moss_block')))),
        cond(biome_is('amber_steppe'), cond(ON_FLOOR, cond(noise(0.45, 10.0), block('minecraft:coarse_dirt')))),
        cond(biome_is('willow_bayou'), cond(ON_FLOOR, seq(
            # Like a swamp: puddles of standing water at sea level, mud at the margins.
            cond(y_above(62), cond(not_(y_above(63)), cond(noise(0.0, 10.0, 'minecraft:surface_swamp'), block('minecraft:water')))),
            cond(noise(-0.25, -0.05, 'minecraft:surface_swamp'), block('minecraft:mud'))))),
        # Karst country is limestone through and through, down to where the deepslate gradient starts.
        cond(biome_is('jade_karst'), cond(y_above(16), cond(not_(ON_FLOOR), cond(not_(UNDER_FLOOR), block(e('limestone')))))),
    )
    data(f'{NS}/worldgen/material_rule/overworld_surface.json', rules)
    # The vanilla entry point, with ours tried before vanilla's surface. Kept as small as vanilla's own
    # file so it is obvious what changed: one sequence wrapped round minecraft:overworld/surface.
    data('minecraft/worldgen/material_rule/overworld.json', seq(
        'minecraft:bedrock_floor', 'minecraft:overworld/copper_ore_vein', 'minecraft:overworld/iron_ore_vein',
        cond({'type': 'minecraft:above_preliminary_surface'}, seq(e('overworld_surface'), 'minecraft:overworld/surface')),
        'minecraft:overworld/underground'))


# ======================================================================== tags

def build_biome_tags(jar):
    """Each biome joins every biome tag its analog is in, so villages, temples, mineshafts, mob variants
    and the rest treat a Wisteria Vale like the forest it grew from."""
    tags = {}
    for path in jar.namelist():
        if not path.startswith('data/minecraft/tags/worldgen/biome/') or not path.endswith('.json'):
            continue
        values = json.loads(jar.read(path))['values']
        tag = path[len('data/minecraft/tags/worldgen/biome/'):-len('.json')]
        for ours, analog in ANALOGS.items():
            if f'minecraft:{analog}' in values:
                tags.setdefault(tag, []).append(e(ours))
    # Analogs chosen for their features, not their geography, get the geography fixed here.
    def drop(tag, *names):
        if tag in tags:
            tags[tag] = [v for v in tags[tag] if v not in [e(n) for n in names]]
            if not tags[tag]:
                del tags[tag]
    drop('is_jungle', 'jade_karst', 'cloud_forest')
    drop('has_structure/jungle_temple', 'cloud_forest')
    drop('is_hill', 'verdant_peaks')
    for tag, values in sorted(tags.items()):
        data(f'minecraft/tags/worldgen/biome/{tag}.json', {'replace': False, 'values': sorted(set(values))})
    data(f'{NS}/tags/worldgen/biome/all.json', {'replace': False, 'values': [e(b) for b in sorted(ANALOGS)]})


# ======================================================================== grand scale

def build_earth(jar):
    """The Earth terrain: the overworld made by the expanse:earth chunk generator.

    Shipped as a built-in data pack (on by default, can be turned off on the world-creation screen),
    because it changes how the overworld is made: a world made without it should not gain it later.

    * world_preset/normal: the default world type's overworld uses expanse:earth with the expanse:earth
      noise settings. Other world types (large biomes, amplified, superflat...) are left alone.
    * noise_settings/earth: vanilla's overworld settings with the terrain swapped out. The ground height
      and the climate the biome source reads (continentalness, erosion, ridges) come from the terrain
      model through expanse:terrain density functions; caves, aquifers, ore veins and surface rules
      are vanilla's, pointed at the new terrain where they read it.
    * The overworld is 448 tall (y -64 to 383), for mountain ranges that need the room.
    """
    out = lambda rel, obj: write(os.path.join(EARTH, 'data', rel), obj)
    vdf = lambda name: json.loads(jar.read(f'data/minecraft/worldgen/density_function/overworld/{name}.json'))
    D = f'{NS}:earth'
    ref = lambda name: f'{D}/{name}'
    terrain = lambda output: {'type': f'{NS}:terrain', 'output': output}
    df = lambda name, obj: out(f'{NS}/worldgen/density_function/earth/{name}.json', obj)
    gradient_y = lambda at_bottom, at_top: {'type': 'minecraft:gradient', 'axis': 'y', 'from_coordinate': -64, 'from_value': at_bottom,
                                            'to_coordinate': 384, 'to_value': at_top}

    # The model's outputs, each cached per column (they are two-dimensional).
    for name in ['height', 'continents', 'erosion', 'ridges']:
        df(name, {'type': 'minecraft:cache', 'input': terrain(name)})
    # Climate zones a little larger than vanilla's, and colder up mountains.
    temperature = vdf('temperature')
    temperature['xz_scale'] = 0.2
    df('temperature', {'type': 'minecraft:add', 'left': temperature, 'right': terrain('lapse')})
    vegetation = vdf('vegetation')
    vegetation['xz_scale'] = 0.2
    df('vegetation', vegetation)
    # depth = (ground height - y) / 128: zero at the surface, as vanilla's depth is.
    df('depth', {'type': 'minecraft:add', 'left': {'type': 'minecraft:mul', 'left': ref('height'), 'right': 1 / 128},
                 'right': gradient_y(64 / 128, -384 / 128)})
    # The terrain density, in place of vanilla's "sloped_cheese": (ground height - y) / 25, so it passes
    # vanilla's cave thresholds at the depths vanilla's does, plus a little 3D noise for texture and the
    # odd overhang on steep ground.
    df('terrain', {'type': 'minecraft:add',
                   'left': {'type': 'minecraft:add', 'left': {'type': 'minecraft:mul', 'left': ref('height'), 'right': 0.04},
                            'right': gradient_y(64 * 0.04, -384 * 0.04)},
                   'right': {'type': 'minecraft:mul', 'left': 'minecraft:overworld/base_3d_noise', 'right': 0.15}})
    # Vanilla's final density (caves, entrances, noodles, pillars) round the new terrain; its top slide,
    # which flattens anything near the old ceiling, moved up to the new one.
    final = json.dumps(vdf('final_density'))
    final = final.replace('"minecraft:overworld/sloped_cheese"', json.dumps(ref('terrain')))
    final = final.replace('"from_coordinate": 240', '"from_coordinate": 368').replace('"to_coordinate": 256', '"to_coordinate": 384')
    df('final_density', json.loads(final))
    df('preliminary_surface_level', {'type': 'minecraft:cache', 'input': terrain('height')})
    df('chunk_surface_level', {'type': 'minecraft:interpolated', 'cell_size_xz': 16, 'cell_size_y': 1, 'input': ref('preliminary_surface_level')})

    ns = json.loads(jar.read('data/minecraft/worldgen/noise_settings/overworld.json'))
    ns['noise']['height'] = 448
    ns['noise_router'] = {k: ref(k) for k in ['temperature', 'vegetation', 'continents', 'erosion', 'depth', 'ridges',
                                               'chunk_surface_level', 'final_density']}
    swap = lambda obj: json.loads(json.dumps(obj).replace('"minecraft:overworld/erosion"', json.dumps(ref('erosion')))
                                  .replace('"minecraft:overworld/depth"', json.dumps(ref('depth')))
                                  .replace('"minecraft:overworld/continents"', json.dumps(ref('continents')))
                                  .replace('"minecraft:overworld/ridges"', json.dumps(ref('ridges')))
                                  .replace('"minecraft:overworld/temperature"', json.dumps(ref('temperature')))
                                  .replace('"minecraft:overworld/vegetation"', json.dumps(ref('vegetation')))
                                  .replace('"minecraft:overworld/preliminary_surface_level"', json.dumps(ref('preliminary_surface_level')))
                                  .replace('"minecraft:overworld/final_density"', json.dumps(ref('final_density'))))
    ns['aquifers'] = swap(ns['aquifers'])
    ns['spawn_target'] = swap(ns['spawn_target'])
    ns['debug_functions'] = swap(ns['debug_functions'])
    out(f'{NS}/worldgen/noise_settings/earth.json', ns)

    preset = json.loads(jar.read('data/minecraft/worldgen/world_preset/normal.json'))
    preset['dimensions']['minecraft:overworld']['generator'] = {
        'type': f'{NS}:earth', 'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:overworld'}, 'settings': f'{NS}:earth'}
    out('minecraft/worldgen/world_preset/normal.json', preset)

    dim = json.loads(jar.read('data/minecraft/dimension_type/overworld.json'))
    dim['height'] = 448
    dim['logical_height'] = 448
    out('minecraft/dimension_type/overworld.json', dim)

    write(os.path.join(EARTH, 'pack.mcmeta'), {'pack': {
        'description': 'VOID Expanse: Earth — tectonic plates, eroded mountain ranges, rivers that run to the sea',
        'min_format': 121, 'max_format': 121}})


def build_advancements():
    """An Expanse tab: a root granted on entering any Expanse biome, and a challenge for seeing all thirteen."""
    def visit(b):
        return {'conditions': {'player': {'type': 'minecraft:entity_properties', 'entity': 'this',
                                          'predicate': {'minecraft:location': {'biomes': e(b)}}}}, 'trigger': 'minecraft:location'}
    biomes = sorted(ANALOGS)
    shutil.rmtree(os.path.join(RES, 'data', NS, 'advancement', 'expanse'), ignore_errors=True)
    data(f'{NS}/advancement/expanse/root.json', {
        'criteria': {b: visit(b) for b in biomes}, 'requirements': [biomes],
        'display': {'title': {'translate': 'advancements.expanse.root.title'}, 'description': {'translate': 'advancements.expanse.root.description'},
                    'icon': {'id': e('wisteria_sapling')}, 'background': 'minecraft:gui/advancements/backgrounds/adventure',
                    'announce_to_chat': False, 'show_toast': True}})
    data(f'{NS}/advancement/expanse/wanderer.json', {
        'parent': e('expanse/root'),
        'criteria': {b: visit(b) for b in biomes}, 'requirements': [[b] for b in biomes],
        'display': {'title': {'translate': 'advancements.expanse.wanderer.title'}, 'description': {'translate': 'advancements.expanse.wanderer.description'},
                    'icon': {'id': 'minecraft:filled_map'}, 'frame': 'challenge'},
        'rewards': {'experience': 250}})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--jar', default=DEFAULT_JAR)
    args = ap.parse_args()
    jar = zipfile.ZipFile(args.jar)

    for d in ['worldgen/biome', 'worldgen/feature', 'worldgen/placed_feature', 'worldgen/material_rule']:
        shutil.rmtree(os.path.join(RES, 'data', NS, d), ignore_errors=True)
    shutil.rmtree(os.path.join(RES, 'data', 'minecraft', 'worldgen'), ignore_errors=True)
    shutil.rmtree(os.path.join(RES, 'data', 'minecraft', 'tags', 'worldgen'), ignore_errors=True)
    shutil.rmtree(os.path.join(RES, 'data', NS, 'tags', 'worldgen', 'biome', 'all.json'), ignore_errors=True)
    shutil.rmtree(GRAND, ignore_errors=True)
    shutil.rmtree(EARTH, ignore_errors=True)

    build_features(jar)
    for name, obj in FEATURES.items():
        data(f'{NS}/worldgen/feature/{name}.json', obj)
    for name, (obj, _) in PLACED.items():
        data(f'{NS}/worldgen/placed_feature/{name}.json', obj)
    build_biomes(jar)
    build_surface_rules()
    build_biome_tags(jar)
    build_earth(jar)
    build_advancements()
    print(f'{len(written)} files')


if __name__ == '__main__':
    main()
