"""
The understory: the layers of growth under and between the trees that make a landscape look lived in by
plants, the green of the Bohemian countryside (Kingdom Come: Deliverance II's) rather than grass with trees
stood on it. Every biome takes the kit for its habitat:

    woodland   hazel, elder, hawthorn and dogwood thickets under the trees; ferns, bush and grass over
               the floor in drifts, thick in places and thin in others; bramble patches; alders and
               willows and young oaks along the water
    conifer    juniper and the odd hazel; ferns, moss and bilberry on the floor
    meadow     waist-high grass in drifts with wildflowers through it, lone shrubs and thickets
    farmland   meadow, and hedgerows dividing it into fields (a feature of its own, HedgerowFeature)
    fishponds  in the temperate woods and meadows: shallow ponds behind earth dams (FishpondFeature)
    tropic     jungle bushes, ferns and large ferns in thick drifts
    dry        dry grass and dead bushes in drifts

Drifts are vanilla's flower noise (noise_threshold_count), which changes over a few hundred blocks: a wood
has open stretches and dense thickets, a meadow has tall-grass swathes and short-cropped ground.

The Expanse biomes list the kit's placed features (gen_worldgen.biome() adds them from HABITATS); vanilla's
biomes get the vanilla/ copies (world/feature/Foliage.java, by the habitats named there).
"""

FERN = LARGE_FERN = BUSH = MOSS = GRASS = TALL_GRASS = None


def _shrub(W, name, leaves, radius=(1, 2), height=2, log='minecraft:oak_log'):
    """A shrub: a stub of trunk under a cushion of leaves two or three blocks high and three to five across."""
    return W.feature(name, {
        'type': 'minecraft:tree', 'below_trunk_provider': W.SOIL, 'decorators': [],
        'foliage_placer': {'type': 'minecraft:bush_foliage_placer', 'radius': W.uniform(*radius), 'offset': 1, 'height': height},
        'foliage_provider': W.LEAVES(leaves), 'ignore_vines': True,
        'minimum_size': {'type': 'minecraft:two_layers_feature_size', 'limit': 0, 'lower_size': 0, 'upper_size': 0},
        'trunk_placer': {'type': 'minecraft:straight_trunk_placer', 'base_height': 1, 'height_rand_a': 0, 'height_rand_b': 0},
        'trunk_provider': W.LOG(log)})


def features(W):
    v = lambda block, **props: W.state(f'minecraft:{block}', **props)
    fern, large_fern, bush, moss = v('fern'), v('large_fern', half='lower'), v('bush'), v('moss_carpet')
    grass, tall_grass = v('short_grass'), v('tall_grass', half='lower')
    S = {'veg': 9, 'surface_struct': 4}
    e = W.e

    # ---- shrubs
    _shrub(W, 'shrub_hazel', 'minecraft:oak_leaves')
    _shrub(W, 'shrub_elder', 'minecraft:birch_leaves', radius=(1, 2), height=3)
    _shrub(W, 'shrub_dogwood', 'minecraft:dark_oak_leaves', radius=(1, 1), height=2)
    _shrub(W, 'shrub_hawthorn', 'minecraft:azalea_leaves', radius=(1, 2), height=2)
    _shrub(W, 'shrub_maquis', 'minecraft:acacia_leaves', radius=(1, 1), height=1, log='minecraft:acacia_log')
    W.selector('shrubs_broadleaf', e('shrub_hazel'), (e('shrub_elder'), 0.3), (e('shrub_dogwood'), 0.25), (e('shrub_hawthorn'), 0.2))
    W.selector('shrubs_conifer', e('spruce_shrub'), (e('shrub_hazel'), 0.15))
    # along the water: alders (young oaks), weeping willows, the odd young birch
    W.selector('bank_trees', e('natural/tree/oak_young/plain'), (e('willow'), 0.35), (e('natural/tree/birch_young/plain'), 0.2),
               (e('shrub_hazel'), 0.25))

    # ---- ground layers
    W.simple('floor_woodland', W.weighted_states((fern, 5), (large_fern, 2), (bush, 3), (grass, 3), (moss, 1)))
    W.simple('floor_conifer', W.weighted_states((fern, 5), (moss, 3), (v('sweet_berry_bush', age=1), 1), (grass, 2)))
    W.simple('brambles', W.weighted_states((v('sweet_berry_bush', age=3), 2), (v('sweet_berry_bush', age=2), 2), (fern, 1)))
    W.simple('meadow_grass', W.weighted_states((tall_grass, 6), (grass, 6), (large_fern, 1)))
    W.simple('meadow_flowers', W.weighted_states((v('poppy'), 2), (v('oxeye_daisy'), 2), (v('cornflower'), 2), (v('dandelion'), 2),
                                                  (v('azure_bluet'), 1), (v('white_tulip'), 1), (grass, 4)))
    W.simple('floor_tropic', W.weighted_states((fern, 4), (large_fern, 3), (bush, 3)))
    W.simple('dry_grass', W.weighted_states((v('short_dry_grass'), 5), (v('tall_dry_grass'), 3), (v('dead_bush'), 1)))

    # ---- placements: each for the Expanse biomes and, as vanilla/<name>, for vanilla's
    under = lambda per_chunk, sapling: [per_chunk, W.IN_SQUARE, W.heightmap(W.GROUND), W.water_depth(0),
                                        W.predicate(W.survives(sapling)), W.BIOME, W.clear(3)]
    W.both('kcd_woodland_shrubs', e('shrubs_broadleaf'), under(W.drifts(-0.1, 3, 7), 'minecraft:oak_sapling'), S['veg'])
    W.both('kcd_conifer_shrubs', e('shrubs_conifer'), under(W.drifts(0.0, 2, 5), 'minecraft:spruce_sapling'), S['veg'])
    W.both('kcd_open_shrubs', e('shrubs_broadleaf'), under(W.drifts(0.3, 0, 2), 'minecraft:oak_sapling'), S['veg'])
    W.both('kcd_tropic_bushes', 'minecraft:jungle_bush', under(W.drifts(0.0, 2, 5), 'minecraft:jungle_sapling'), S['veg'])
    W.both('kcd_bank_trees', e('bank_trees'), W.bank_anchors(2) + [W.predicate(W.survives('minecraft:oak_sapling')), W.clear(3)], S['veg'])
    # Ground layers grow over the short grass vanilla has already laid as well as onto bare soil, so a try is
    # rarely wasted on a cell that is taken: each layer needs a third of the tries for the same cover. (Every
    # try runs the whole placement chain, and the tries are most of what ground cover costs.)
    def overgrow(frequency, tries, xz=7, y=3):
        return (W.frequency_modifiers(frequency) + [W.IN_SQUARE, W.heightmap(W.GROUND), W.BIOME] + W.scatter(tries, xz, y)
                + [W.predicate(W.all_of(W.any_of(W.AIR, {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:short_grass'}), W.ON_SOIL))])
    W.both('kcd_woodland_floor', e('floor_woodland'), overgrow(W.drifts(-0.2, 2, 5), 32), S['veg'])
    W.both('kcd_conifer_floor', e('floor_conifer'), overgrow(W.drifts(-0.1, 2, 4), 32), S['veg'])
    W.both('kcd_brambles', e('brambles'), W.cover(W.rarity(3), 20, 4, 1, margin=5), S['veg'])
    W.both('kcd_meadow_grass', e('meadow_grass'), overgrow(W.drifts(-0.3, 3, 6), 32, 8, 3), S['veg'])
    W.both('kcd_meadow_flowers', e('meadow_flowers'), overgrow(W.drifts(0.2, 0, 3), 32, 7, 2), S['veg'])
    W.both('kcd_tropic_floor', e('floor_tropic'), overgrow(W.drifts(-0.2, 2, 5), 32), S['veg'])
    W.both('kcd_dry_grass', e('dry_grass'), W.cover(W.drifts(0.0, 1, 4), 32, 7, 3, on=None), S['veg'])

    # ---- fishponds: a shallow pond dug into gentle ground behind an earth dam, a wooden sluice by the dam; dug in the
    # lakes step, so the reeds, lily pads and bank trees of the water's edge come to it afterwards
    W.feature('fishpond', {'type': e('fishpond'), 'half_length': W.uniform(7, 12), 'half_width': W.uniform(5, 9)})
    W.both('kcd_fishponds', e('fishpond'), [W.rarity(36), W.IN_SQUARE, W.heightmap('WORLD_SURFACE_WG'), W.BIOME], 1)

    # ---- hedgerows: once per chunk; the feature finds the field edges itself
    hedge = lambda f: {'feature': f, 'placement': []}
    W.feature('hedgerows', {'type': e('hedgerow'), 'shrub': hedge(e('shrubs_broadleaf')),
                            'tree': hedge(e('natural/tree/oak_spreading/plain')),
                            'biomes': ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:forest', 'minecraft:birch_forest',
                                       'minecraft:flower_forest', 'minecraft:meadow', 'minecraft:old_growth_birch_forest']
                                      + [e(b) for b in HEDGED],
                            'field': 64, 'missing': 0.22, 'trees': 0.3})
    W.both('kcd_hedgerows', e('hedgerows'), [], S['surface_struct'])

    # ---- lone trees: a veteran oak on the crest of an open rise, a mark on the skyline seen for miles (HilltopFilter)
    W.both('kcd_lone_trees', e('natural/tree/oak_veteran/plain'),
           [W.count(4), W.IN_SQUARE, W.heightmap('WORLD_SURFACE_WG'), {'type': e('hilltop')}, W.BIOME], S['veg'])


WOODLAND = ['kcd_fishponds', 'kcd_woodland_shrubs', 'kcd_woodland_floor', 'kcd_brambles', 'kcd_bank_trees', 'kcd_lone_trees']
CONIFER = ['kcd_conifer_shrubs', 'kcd_conifer_floor', 'kcd_bank_trees']
MEADOW = ['kcd_fishponds', 'kcd_open_shrubs', 'kcd_meadow_grass', 'kcd_meadow_flowers', 'kcd_bank_trees', 'kcd_lone_trees']
TROPIC = ['kcd_tropic_bushes', 'kcd_tropic_floor']
DRY = ['kcd_dry_grass']

# Expanse biomes that are farmed country, hedged into fields (on the Earth terrain, in their open country only)
HEDGED = ['woodland_glades', 'aspen_parkland', 'ancient_oakwood', 'maple_highlands', 'bluebell_woods']

# Each Expanse biome's kit, on top of its own undergrowth
HABITATS = {
    'woodland_glades': WOODLAND + ['kcd_meadow_grass'],
    'ancient_oakwood': WOODLAND,
    'maple_highlands': WOODLAND,
    'bluebell_woods': ['kcd_woodland_shrubs', 'kcd_brambles', 'kcd_bank_trees'],
    'redwood_giants': CONIFER,
    'larch_taiga': CONIFER,
    'pine_heath': CONIFER,
    'fjordlands': CONIFER,
    'boreal_muskeg': ['kcd_conifer_floor'],
    'aspen_parkland': MEADOW,
    'heather_moor': ['kcd_fishponds', 'kcd_open_shrubs', 'kcd_meadow_grass'],
    'verdant_peaks': ['kcd_meadow_grass', 'kcd_meadow_flowers'],
    'dolomite_peaks': ['kcd_meadow_grass', 'kcd_meadow_flowers'],
    'volcanic_highlands': ['kcd_meadow_grass'],
    'olive_groves': DRY,
    'willow_bayou': ['kcd_woodland_floor'],
    'amber_steppe': DRY,
    'ghost_gum_outback': DRY,
    'saguaro_flats': DRY,
    'painted_canyons': DRY,
    'jade_karst': TROPIC,
    'cloud_forest': TROPIC,
    'kapok_rainforest': TROPIC,
    'monsoon_forest': TROPIC,
    'tepui': TROPIC,
}


def kit(name):
    """The kit's placed features for Expanse biome `name` (gen_worldgen.biome() adds them)."""
    return HABITATS.get(name, []) + (['kcd_hedgerows'] if name in HEDGED else [])
