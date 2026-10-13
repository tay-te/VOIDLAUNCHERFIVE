"""
The natural broadleaves: oak and birch as they grow, standing in for vanilla's wherever the world plants them.

Vanilla's oak is a ball of leaves on a post, its fancy oak a few balls on sticks, its birch a post with a block
of leaves on top, and a wood of them is one tree repeated. Here an oak forks low into crooked limbs, each under
a clump of leaves; a birch is a slim white stem leaning a little under a light crown whose twigs droop; and a
wood mixes the ages, as real woods do:

    young      a few blocks high, a thin crown on two or three short limbs
    mature     the wood's own tree (forest oak: taller, limbs climbing; open oak: low, limbs spreading wide)
    veteran    now and then a broad old tree with a flared foot and bracket fungus
    snag       a dead trunk still standing, broken off, its top weathered bare
    fallen     vanilla's fallen trees, at vanilla's rate

How they stand in. Vanilla's tree mixes (trees_plains, trees_birch_and_oak_leaf_litter, dark_forest_vegetation,
meadow_trees, ...) are random selectors over placed trees. Each is overridden (data/minecraft/worldgen/feature/)
with its oak, fancy oak and birch entries swapped for one of the families below, wearing the decorators of the
vanilla tree it replaces (bees at the same rate, leaf litter where it had leaf litter) and keeping its placement
(the sapling-survival check), at the same chance. Spruce, dark oak, acacia, jungle, cherry, mangrove and pale oak
stay as vanilla grows them.

Saplings are untouched: they grow the configured trees (minecraft:oak, minecraft:birch, ...), which nothing
here overrides, so a tree farm grows what it always did. The Expanse biomes that planted vanilla oaks and
birches among their own trees plant these instead (the `plain` variants, without decorators).
"""
import json

# Every natural tree asks the land whether a tree would stand there (LandFitFilter, TerrainModel.woodland): woods on
# the rises with clean edges, open fields and meadows in the vales, each country's share of open land, a treeline.
LAND_FIT = [{'type': 'expanse:land_fit'}]

FORK = 'expanse:fork_trunk_placer'


def _fork(base, a, b, forks, length, rise, lean, subfork, flare):
    return {'type': FORK, 'base_height': base, 'height_rand_a': a, 'height_rand_b': b, 'fork_count': forks, 'fork_length': length,
            'fork_rise': rise, 'lean': lean, 'subfork_chance': subfork, 'root_flare': flare}


def _straight(base, a, b):
    return {'type': 'minecraft:straight_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': b}


def _pads(radius, holes):
    """Flat pads of leaves (the clump placer at height 0): umbrella and storeyed crowns."""
    return {'type': 'expanse:clump_foliage_placer', 'radius': radius, 'offset': 0, 'height': 0, 'edge_hole_chance': holes}


def _shapes(W):
    """name -> (log, leaves, trunk placer, foliage placer, extra decorators[, minimum size]). Every tree keeps its
    vanilla wood, so wood from a natural tree is the wood it always was."""
    u = W.uniform
    wood = lambda w: (f'minecraft:{w}_log', f'minecraft:{w}_leaves')
    oak, birch = wood('oak'), wood('birch')
    spruce, dark, acacia, jungle, cherry = wood('spruce'), wood('dark_oak'), wood('acacia'), wood('jungle'), wood('cherry')
    spruce_crown = lambda r, off, bare: {'type': 'minecraft:spruce_foliage_placer', 'radius': r, 'offset': off, 'trunk_height': bare}
    giant = lambda crown: {'type': 'minecraft:mega_pine_foliage_placer', 'radius': 0, 'offset': 0, 'crown_height': crown}
    giant_trunk = {'type': 'minecraft:giant_trunk_placer', 'base_height': 15, 'height_rand_a': 4, 'height_rand_b': 12}
    three = {'type': 'minecraft:three_layers_feature_size', 'limit': 1, 'upper_limit': 1, 'lower_size': 0, 'middle_size': 1, 'upper_size': 1}
    return {
        # ---- the conifers of the uplands, in vanilla's spruce wood
        # a young spruce, a small cone close to the ground
        'spruce_young': (*spruce, _straight(4, 2, 1), spruce_crown(u(1, 2), u(0, 1), u(1, 2)), []),
        # the forest spruce: tall and slim, its lower trunk bare where the shade killed the branches
        'spruce': (*spruce, _straight(9, 4, 2), spruce_crown(u(2, 3), u(0, 2), u(2, 4)), []),
        # an old spruce standing over the rest of the wood
        'spruce_tall': (*spruce, _straight(14, 5, 3), spruce_crown(u(2, 3), u(1, 2), u(4, 6)), []),
        # the giants of the old-growth taigas: a two-wide bole, a long narrow crown
        'spruce_giant': (*spruce, giant_trunk, giant(u(14, 19)), [], W.TWO_LAYERS_MEGA),
        # the Scots pine: a tall bare trunk and a flat crown up in the light
        'pine_scots': (*spruce, W.crown_trunk(11, 4, 3, u(3, 5), u(2, 3), 0.05), W.CLUMP(2, 1, 0.3), []),
        # a young pine: a tuft of needles on a short trunk
        'pine_young': (*spruce, _straight(5, 3, 1), {'type': 'minecraft:pine_foliage_placer', 'radius': 1, 'offset': 1, 'height': u(3, 4)}, []),
        # the old-growth pine: a two-wide bole with a short crown at the top
        'pine_giant': (*spruce, giant_trunk, giant(u(4, 8)), [], W.TWO_LAYERS_MEGA),
        # ---- dark oak: the tall dense ones of the dark woods, old forked ones, young ones
        'dark_oak_tall': (*dark, {'type': 'minecraft:dark_oak_trunk_placer', 'base_height': 8, 'height_rand_a': 3, 'height_rand_b': 2},
                          {'type': 'minecraft:dark_oak_foliage_placer', 'radius': 0, 'offset': 0}, [], three),
        'dark_oak_old': (*dark, _fork(4, 1, 1, u(4, 5), u(3, 4), 0.5, 0.25, 0.5, u(3, 5)), W.CLUMP(3, 1, 0.3), [W.SHELF_MUSHROOMS(0.3)]),
        'dark_oak_young': (*dark, _fork(3, 1, 1, u(2, 3), u(1, 2), 0.8, 0.15, 0.1, 0), W.CLUMP(2, 1, 0.3), []),
        # ---- acacia: an umbrella of flat pads on a forked trunk
        'acacia_umbrella': (*acacia, _fork(4, 2, 1, u(2, 4), u(3, 4), 1.1, 0.35, 0.45, u(0, 1)), _pads(3, 0.35), []),
        'acacia_young': (*acacia, _fork(2, 1, 1, u(2, 3), u(1, 2), 1.0, 0.3, 0.2, 0), _pads(2, 0.3), []),
        # ---- jungle: tall forked trees with storeyed crowns, buttressed giants over the canopy
        'jungle_tall': (*jungle, _fork(9, 4, 3, u(3, 4), u(2, 4), 1.3, 0.1, 0.35, u(1, 3)), _pads(3, 0.3), []),
        'jungle_young': (*jungle, _fork(4, 3, 1, u(2, 3), u(1, 2), 1.2, 0.15, 0.2, 0), W.CLUMP(2, 1, 0.3), []),
        'jungle_giant': (*jungle, {'type': 'expanse:kapok_trunk_placer', 'base_height': 20, 'height_rand_a': 6, 'height_rand_b': 4,
                                   'trunk_width': 2, 'buttress_count': u(4, 7), 'buttress_length': u(2, 3), 'limb_count': u(4, 6),
                                   'limb_length': u(3, 6)}, _pads(2, 0.3), [], W.TWO_LAYERS_MEGA),
        # ---- cherry: a spreading crown of blossom on a low forked trunk
        'cherry_spreading': (*cherry, _fork(3, 1, 1, u(3, 5), u(3, 4), 0.5, 0.3, 0.45, u(1, 2)), W.CLUMP(2, 2, 0.3), []),
        'cherry_young': (*cherry, _fork(3, 1, 1, u(2, 3), u(1, 2), 0.8, 0.2, 0.1, 0), W.CLUMP(2, 1, 0.3), []),
        # a young oak: four or five blocks, two or three short limbs under small clumps
        'oak_young': (*oak, _fork(3, 1, 1, u(2, 3), u(1, 2), 0.9, 0.15, 0.1, 0), W.CLUMP(2, 1, 0.3), []),
        # the forest oak: a taller bole, limbs climbing into a rounded crown, some forking again
        'oak': (*oak, _fork(4, 2, 1, u(3, 4), u(2, 3), 0.8, 0.2, 0.35, u(0, 1)), W.CLUMP(2, 2, 0.3), []),
        # the open-grown oak of fields and hills: short, limbs spreading low and wide, the crown wider than tall
        'oak_spreading': (*oak, _fork(2, 1, 1, u(4, 5), u(3, 4), 0.45, 0.25, 0.45, u(1, 2)), W.CLUMP(2, 1, 0.25), []),
        # the veteran: a massive flared foot, five or six long limbs forking again, bracket fungus on the trunk
        'oak_veteran': (*oak, _fork(4, 1, 1, u(4, 6), u(3, 5), 0.55, 0.3, 0.6, u(3, 5)), W.CLUMP(3, 1, 0.3), [W.SHELF_MUSHROOMS(0.3)]),
        # a young birch: a thin stem, one or two twigs, a narrow crown
        'birch_young': (*birch, _fork(4, 2, 1, u(1, 2), 1, 1.5, 0.1, 0.0, 0), W.CLUMP(1, 2, 0.35), []),
        # the silver birch: slim, leaning a little, steep limbs under a light crown with drooping twigs
        'birch': (*birch, _fork(6, 3, 2, u(2, 3), u(1, 2), 1.8, 0.15, 0.25, 0), W.drooping(2, 2, 0.3, 2), []),
        # an old birch of the tall birch woods: twice the height, the crown high and open
        'birch_tall': (*birch, _fork(9, 4, 3, u(2, 3), u(1, 2), 2.2, 0.12, 0.3, 0), W.drooping(2, 2, 0.35, 2), []),
    }


# Each family: its default shape, then (shape, chance) in the order the random selector tries them.
FAMILIES = {
    'forest_oak': ('oak', [('oak_young', 0.35), ('oak_veteran', 0.05), ('oak_snag', 0.03)]),
    'open_oak': ('oak_spreading', [('oak_young', 0.3), ('oak', 0.25), ('oak_veteran', 0.06)]),
    'big_oak': ('oak', [('oak_veteran', 0.4), ('oak_spreading', 0.3)]),        # where vanilla had a fancy oak in a wood
    'big_open_oak': ('oak_spreading', [('oak_veteran', 0.35)]),                 # ... and out in the open
    'birch': ('birch', [('birch_young', 0.35), ('birch_snag', 0.03)]),
    'tall_birch': ('birch_tall', [('birch', 0.3), ('birch_snag', 0.02)]),
    'spruce_forest': ('spruce', [('spruce_young', 0.3), ('spruce_tall', 0.25), ('spruce_snag', 0.03)]),
    'spruce_giant': ('spruce_giant', [('spruce_tall', 0.3)]),
    'pine_forest': ('pine_scots', [('pine_young', 0.35), ('spruce_snag', 0.03)]),
    'pine_giant': ('pine_giant', [('pine_scots', 0.3)]),
    'dark_wood': ('dark_oak_tall', [('dark_oak_old', 0.35), ('dark_oak_young', 0.2)]),
    'acacia_wood': ('acacia_umbrella', [('acacia_young', 0.35)]),
    'jungle_wood': ('jungle_tall', [('jungle_young', 0.35)]),
    'jungle_giant': ('jungle_giant', [('jungle_tall', 0.2)]),
    'cherry_wood': ('cherry_spreading', [('cherry_young', 0.35)]),
    'swamp_wood': ('oak_spreading', [('oak', 0.4)]),               # wearing vanilla's swamp vines
}

# Vanilla's tree mixes, and for each the oak and birch trees (by configured feature) it plants and the family
# that takes over each. Anything oak or birch in these mixes that is not listed fails the run.
OVERRIDES = {
    'trees_plains': {'oak_bees_005': 'open_oak', 'fancy_oak_bees_005': 'big_open_oak'},
    'trees_birch_and_oak_leaf_litter': {'oak_bees_0002_leaf_litter': 'forest_oak', 'birch_bees_0002_leaf_litter': 'birch',
                                        'fancy_oak_bees_0002_leaf_litter': 'big_oak'},
    'trees_flower_forest': {'oak_bees_002': 'forest_oak', 'birch_bees_002': 'birch', 'fancy_oak_bees_002': 'big_oak'},
    'trees_birch': {'birch_bees_0002': 'birch'},
    'birch_tall': {'birch_bees_0002': 'birch', 'super_birch_bees_0002': 'tall_birch'},
    'meadow_trees': {'super_birch_bees': 'tall_birch', 'fancy_oak_bees': 'big_open_oak'},
    'dark_forest_vegetation': {'oak_leaf_litter': 'forest_oak', 'birch_leaf_litter': 'birch', 'fancy_oak_leaf_litter': 'big_oak',
                               'dark_oak_leaf_litter': 'dark_wood'},
    'trees_badlands': {'oak_leaf_litter': 'open_oak'},
    'trees_savanna': {'oak': 'open_oak', 'acacia': 'acacia_wood'},
    'trees_water': {'oak': 'open_oak', 'fancy_oak': 'big_open_oak'},
    'trees_windswept_hills': {'oak': 'open_oak', 'fancy_oak': 'big_open_oak', 'spruce': 'spruce_forest'},
    'trees_jungle': {'fancy_oak': 'big_open_oak', 'jungle_tree': 'jungle_wood', 'mega_jungle_tree': 'jungle_giant'},
    'trees_sparse_jungle': {'fancy_oak': 'big_open_oak', 'jungle_tree': 'jungle_wood'},
    'bamboo_vegetation': {'fancy_oak': 'big_open_oak', 'mega_jungle_tree': 'jungle_giant'},
    'trees_grove': {'pine': 'pine_forest', 'spruce': 'spruce_forest'},
    'trees_taiga': {'pine': 'pine_forest', 'spruce': 'spruce_forest'},
    'trees_snowy': {'spruce': 'spruce_forest'},
    'trees_old_growth_pine_taiga': {'mega_pine': 'pine_giant', 'mega_spruce': 'spruce_giant', 'pine': 'pine_forest', 'spruce': 'spruce_forest'},
    'trees_old_growth_spruce_taiga': {'mega_spruce': 'spruce_giant', 'pine': 'pine_forest', 'spruce': 'spruce_forest'},
}
# Biomes that plant one tree directly rather than through a mix: the placed feature itself is overridden.
DIRECT = {'trees_cherry': 'cherry_wood', 'trees_swamp': 'swamp_wood'}
# trees in those mixes that stay vanilla's: the shrubs, the fallen trees, and pale oak (the creaking heart grows in it)
KEEP = {'pale_oak', 'pale_oak_creaking', 'jungle_bush', 'fallen_oak_tree', 'fallen_birch_tree', 'fallen_super_birch_tree',
        'fallen_spruce_tree', 'fallen_jungle_tree'}


def _family(W, shapes, made, family, variant, decorators):
    """The configured selector natural/<family>/<variant>: the family's shapes, each wearing `decorators`."""
    name = f'natural/{family}/{variant}'
    if name in made:
        return W.e(name)
    made.add(name)

    def shape(s):
        if s.endswith('_snag'):
            return W.e(f'natural/tree/{s}')   # a dead trunk: no leaves, so no bees and no litter
        tree = f'natural/tree/{s}/{variant}'
        if tree not in made:
            made.add(tree)
            log, leaves, trunk, foliage, extra, *size = shapes[s]
            W.tree(tree, log, trunk, leaves, foliage, *size, decorators=list(decorators) + extra)
        return W.e(tree)
    default, choices = FAMILIES[family]
    return W.selector(name, shape(default), *[(shape(s), c) for s, c in choices])


def features(W, jar):
    shapes = _shapes(W)
    made = set()
    # snags: a broken trunk, three to six blocks of log under a weathered stub
    for kind in ('oak', 'birch', 'spruce'):
        W.feature(f'natural/tree/{kind}_snag', {
            'type': 'minecraft:block_column', 'allowed_placement': W.AIR, 'direction': 'up', 'prioritize_tip': True,
            'layers': [{'height': W.uniform(3, 6), 'provider': W.LOG(f'minecraft:{kind}_log')},
                       {'height': 1, 'provider': W.state(f'minecraft:stripped_{kind}_log', axis='y')}]})
    # the plain families, for the Expanse biomes' own mixes
    for family in FAMILIES:
        _family(W, shapes, made, family, 'plain', [])
    # the cloud forest's: hung with Spanish moss
    for family in ('jungle_wood', 'jungle_giant'):
        _family(W, shapes, made, family, 'mossy', [W.hanging(W.SPANISH_MOSS, 0.12)])
    _vanilla_mixes(W, jar, shapes, made)


def _vanilla_mixes(W, jar, shapes, made):
    read = lambda path: json.loads(jar.read(path))
    for mix, swaps in OVERRIDES.items():
        obj = read(f'data/minecraft/worldgen/feature/{mix}.json')
        assert obj['type'] == 'minecraft:random_selector', mix
        used = set()

        def swap(ref):
            # an entry is a placed feature: by name, or inline {feature, placement}
            placed = read(f'data/minecraft/worldgen/placed_feature/{ref.split(":")[1]}.json') if isinstance(ref, str) else ref
            configured = placed['feature']
            if not isinstance(configured, str):
                return ref                          # an inline configured feature (dark forest's huge mushrooms)
            base = configured.split(':')[1]
            if base not in swaps:
                is_tree = read(f'data/minecraft/worldgen/feature/{base}.json').get('type') == 'minecraft:tree'
                assert base in KEEP or not is_tree, f'{mix}: {base} has no family'
                return ref
            used.add(base)
            family = swaps[base]
            decorators = read(f'data/minecraft/worldgen/feature/{base}.json').get('decorators', [])
            name = f'natural/{family}/{base}'
            _family(W, shapes, made, family, base, decorators)
            # one placed feature per vanilla placement, not per tree: spruce_on_snow and spruce_checked plant the same
            # spruce under different rules (on snow; where a sapling would live)
            placed_name = f'natural/{family}/{ref.split(":")[1] if isinstance(ref, str) else base}'
            W.data(f'{W.NS}/worldgen/placed_feature/{placed_name}.json', {'feature': W.e(name), 'placement': LAND_FIT + placed['placement']})
            return W.e(placed_name)

        obj['default'] = swap(obj['default'])
        for entry in obj['features']:
            entry['feature'] = swap(entry['feature'])
        missing = set(swaps) - used
        assert not missing, f'{mix}: vanilla no longer plants {missing}'
        W.data(f'minecraft/worldgen/feature/{mix}.json', obj)
    for placed_name, family in DIRECT.items():
        placed = read(f'data/minecraft/worldgen/placed_feature/{placed_name}.json')
        base = placed['feature'].split(':')[1]
        decorators = read(f'data/minecraft/worldgen/feature/{base}.json').get('decorators', [])
        name = f'natural/{family}/{base}'
        _family(W, shapes, made, family, base, decorators)
        W.data(f'minecraft/worldgen/placed_feature/{placed_name}.json', {'feature': W.e(name), 'placement': LAND_FIT + placed['placement']})
