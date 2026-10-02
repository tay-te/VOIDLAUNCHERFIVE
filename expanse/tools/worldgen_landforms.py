"""The landform biomes: biomes the land itself places (world/terrain/Landform.java), not the climate table.

The terrain model tags each column of a landform (a volcano's cone, ...) with a weirdness nothing else
reaches, and the biome source has a point there for the landform's biome (BiomePlacement.landforms), so
these biomes come out of the source like any other. This module holds their features, biomes and surface
rules; tools/gen_worldgen.py calls it at three points (features, biomes, surface rules), passing itself
in as W so the helpers and registries are the ones it writes from.
"""

ASH = 'expanse:volcanic_ash'
# the landform biomes, by name
NAMES = ['volcanic_highlands', 'painted_canyons', 'salt_flats', 'tepui', 'fjordlands']
SALT = 'expanse:salt_block'


def features(W):
    """Placed features, in the order they are appended to biomes (W.FEATURE_ORDER)."""
    S = {'lakes': 1, 'local': 2, 'surface_struct': 4, 'ores': 6, 'springs': 8, 'veg': 9}
    surface = lambda: [W.IN_SQUARE, W.heightmap('MOTION_BLOCKING_NO_LEAVES'), W.BIOME]

    # Fumaroles: vents let into the ground, breathing smoke, gathered round the upper slopes.
    W.feature('volcanic_fumarole', {'type': 'minecraft:replace_single_block', 'targets': [
        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'expanse:volcanic_ground'}, 'state': 'expanse:fumarole'}]})
    W.placed('volcanic_fumaroles', W.e('volcanic_fumarole'),
             [W.count(W.uniform(0, 3))] + surface() + [{'type': 'minecraft:offset', 'x': 0, 'y': -1, 'z': 0}, W.clear(2)],
             S['local'])
    # Old lava flows, cooled to basalt and, at their glassy edges, obsidian.
    W.feature('volcanic_basalt_flow', {'type': 'minecraft:disk', 'radius': W.uniform(3, 7), 'half_height': 2,
                                       'state_provider': W.state('minecraft:basalt'),
                                       'target': {'type': 'minecraft:matching_block_tag', 'tag': 'expanse:volcanic_ground'}})
    W.placed('volcanic_basalt_flows', W.e('volcanic_basalt_flow'),
             [W.rarity(2)] + surface() + [{'type': 'minecraft:offset', 'x': 0, 'y': -1, 'z': 0}, W.clear(4)], S['local'])
    W.feature('volcanic_obsidian_edge', {'type': 'minecraft:disk', 'radius': W.uniform(1, 2), 'half_height': 1,
                                         'state_provider': W.state('minecraft:obsidian'),
                                         'target': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:basalt', ASH]}})
    W.placed('volcanic_obsidian_edges', W.e('volcanic_obsidian_edge'),
             [W.rarity(3)] + surface() + [{'type': 'minecraft:offset', 'x': 0, 'y': -1, 'z': 0}, W.clear(4)], S['local'])
    # Tumbled blocks of tuff and basalt on the lower slopes.
    W.feature('volcanic_boulder', {'type': 'minecraft:block_blob', 'state': 'minecraft:tuff',
                                   'can_place_on': {'type': 'minecraft:matching_block_tag', 'tag': 'minecraft:forest_rock_can_place_on'}})
    W.placed('volcanic_boulders', W.e('volcanic_boulder'),
             [W.count(W.weighted_int((0, 3), (1, 2), (2, 1)))] + surface() + [W.clear(4)], S['local'])


    # --- salt flats: shallow pink brine pools in the crust, and salt chimneys
    W.feature('salt_brine_pool', {'type': 'minecraft:disk', 'radius': W.uniform(2, 6), 'half_height': 0,
                                  'state_provider': W.state('minecraft:water'),
                                  'target': {'type': 'minecraft:matching_blocks', 'blocks': [SALT]}})
    W.placed('salt_brine_pools', W.e('salt_brine_pool'),
             [W.rarity(2)] + surface() + [{'type': 'minecraft:offset', 'x': 0, 'y': -1, 'z': 0}, W.clear(4)], S['local'])
    W.feature('salt_chimney', {'type': 'minecraft:block_column', 'direction': 'up', 'allowed_placement': W.AIR, 'prioritize_tip': False,
                               'layers': [{'height': W.uniform(1, 3), 'provider': W.state(SALT)}]})
    W.placed('salt_chimneys', W.e('salt_chimney'),
             [W.count(W.uniform(0, 2))] + surface() + [W.predicate({'type': 'minecraft:matching_blocks', 'blocks': [SALT], 'offset': [0, -1, 0]}),
                                                        W.clear(3)], S['local'])
    # --- tepuis: more springs, so the cliffs run with waterfalls
    W.placed('tepui_springs', 'minecraft:spring_water',
             [W.count(40), W.IN_SQUARE, {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
              'min_inclusive': {'absolute': 70}, 'max_inclusive': {'absolute': 320}}}, W.BIOME], S['springs'])
    # --- fjords: the walls weep with springs, waterfalls into the sea arm below
    W.placed('fjord_springs', 'minecraft:spring_water',
             [W.count(30), W.IN_SQUARE, {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
              'min_inclusive': {'absolute': 64}, 'max_inclusive': {'absolute': 260}}}, W.BIOME], S['springs'])


def biomes(W, jar):
    W.ANALOGS['volcanic_highlands'] = W.biome('volcanic_highlands', 'windswept_gravelly_hills', jar,
        temperature=0.45, downfall=0.3,
        effects={'grass_color': '#8c9a5e', 'foliage_color': '#748548', 'water_color': '#4a7590'},
        attributes={'visual/sky_color': '#9aa6b6', 'visual/fog_color': '#b4ada8', 'visual/fog_end_distance': 260.0,
                    'visual/ambient_particles': W.particles('minecraft:white_ash', 0.0025),
                    'audio/background_music': W.music('minecraft:music.overworld.stony_peaks')},
        add=['volcanic_fumaroles', 'volcanic_basalt_flows', 'volcanic_obsidian_edges', 'volcanic_boulders'])

    # Mesas and buttes in banded rock, the rivers deep in canyons between them.
    W.ANALOGS['painted_canyons'] = W.biome('painted_canyons', 'badlands', jar,
        effects={'grass_color': '#b39a5c', 'foliage_color': '#9e8a4e', 'water_color': '#4a8aa0'},
        attributes={'visual/sky_color': '#a9c0e0', 'visual/fog_color': '#e6cba8', 'visual/fog_end_distance': 300.0,
                    'audio/background_music': W.music('minecraft:music.overworld.badlands')},
        add=['river_clay', 'river_reeds_arid'])

    # A white plain to the horizon, cracked into polygons, its brine pools pink with salt-loving algae.
    W.ANALOGS['salt_flats'] = W.biome('salt_flats', 'desert', jar,
        effects={'water_color': '#e59ab2', 'grass_color': '#c2b98a', 'foliage_color': '#aea476'},
        attributes={'visual/sky_color': '#b9cfee', 'visual/fog_color': '#f3efe6', 'visual/fog_end_distance': 220.0,
                    'audio/background_music': W.music('minecraft:music.overworld.desert')},
        drop=['minecraft:patch_cactus_desert', 'minecraft:patch_dry_grass_desert', 'minecraft:patch_sugar_cane_desert', 'minecraft:lake_lava_surface',
              'minecraft:desert_well', 'minecraft:fossil_upper', 'minecraft:flower_default', 'minecraft:patch_grass_badlands'],
        add=['salt_brine_pools', 'salt_chimneys'])

    # The lost world on top of a tepui: mist, moss, dwarf cloud-forest trees, ferns, and springs over the cliffs.
    W.ANALOGS['tepui'] = W.biome('tepui', 'jungle', jar,
        temperature=0.8, downfall=1.0,
        effects={'water_color': '#5fb0c0', 'grass_color': '#5aa05a', 'foliage_color': '#479a50'},
        attributes={'visual/sky_color': '#b8cad4', 'visual/fog_color': '#d6e2e0', 'visual/water_fog_color': '#3f7f90',
                    'visual/fog_start_distance': 8.0, 'visual/fog_end_distance': 110.0,
                    'audio/background_music': W.music('minecraft:music.overworld.sparse_jungle')},
        drop=['minecraft:trees_jungle', 'minecraft:bamboo_light', 'minecraft:patch_melon'],
        add=['cloud_forest_trees', 'cloud_forest_moss', 'cloud_forest_floor', 'cloud_forest_ferns', 'tepui_springs'],
        spawns={'creature': [('minecraft:parrot', 10, 1, 2)]})

    # Grey walls a few hundred blocks high over a dark arm of the sea, spruce on every ledge, waterfalls.
    W.ANALOGS['fjordlands'] = W.biome('fjordlands', 'taiga', jar,
        temperature=0.2, downfall=0.8,
        effects={'water_color': '#2f5f80', 'grass_color': '#6a8f5c', 'foliage_color': '#557a4c'},
        attributes={'visual/sky_color': '#93aac6', 'visual/fog_color': '#b8c4cc', 'visual/water_fog_color': '#1d3a52',
                    'visual/fog_end_distance': 240.0,
                    'audio/background_music': W.music('minecraft:music.overworld.snowy_slopes')},
        add=['fjord_springs', 'mossy_boulder'])


def surface_rules(W):
    """Conditions tried before vanilla's surface."""
    ON, UNDER, STEEP = W.ON_FLOOR, W.UNDER_FLOOR, W.STEEP
    return [W.cond(W.biome_is('volcanic_highlands'), W.seq(
        W.cond(ON, W.seq(
            # cliffs and gully walls show the rock: basalt and tuff
            W.cond(STEEP, W.cond(W.noise(-0.3, 0.3), W.block('minecraft:basalt'))),
            W.cond(STEEP, W.block('minecraft:tuff')),
            # the upper cone is bare: ash with outcrops of tuff
            W.cond(W.y_above(140), W.seq(W.cond(W.noise(-0.35, 0.05), W.block('minecraft:tuff')), W.block(ASH))),
            # lower down, grass where the ash has weathered, ash fields and bare cinders between
            W.cond(W.noise(0.2, 10.0), W.block(ASH)),
            W.cond(W.noise(-10.0, -0.6), W.block('minecraft:coarse_dirt')))),
        W.cond(UNDER, W.seq(
            W.cond(W.y_above(140), W.block(ASH)),
            W.cond(W.noise(0.2, 10.0), W.block(ASH)))),
        # and under all of it, the tuff the mountain is built of
        W.cond(W.stone_depth(0, True, secondary=10), W.block('minecraft:tuff')))),
        # canyon country: red sand on the flats, and the banded rock of the badlands through every cliff
        W.cond(W.biome_is('painted_canyons'), W.seq(
            W.cond(ON, W.cond(W.not_(STEEP), W.seq(
                W.cond(W.noise(-0.15, 0.15), W.block('minecraft:coarse_dirt')),
                W.block('minecraft:red_sand')))),
            W.cond(W.y_above(55), {'type': 'minecraft:bandlands'}))),
        # salt flats: crust over the whole plain, a little deep
        W.cond(W.biome_is('salt_flats'), W.seq(
            W.cond(ON, W.block(SALT)),
            W.cond(W.stone_depth(0, True, secondary=3), W.block(SALT)))),
        # tepuis: bare sandstone-grey cliffs, moss and grass on top
        W.cond(W.biome_is('tepui'), W.seq(
            W.cond(ON, W.seq(
                W.cond(STEEP, W.block('minecraft:stone')),
                W.cond(W.noise(0.25, 10.0), W.block('minecraft:moss_block')))),
            W.cond(UNDER, W.cond(STEEP, W.block('minecraft:stone'))))),
        # fjordlands: bare rock walls, snow high up
        W.cond(W.biome_is('fjordlands'), W.seq(
            W.cond(ON, W.seq(
                W.cond(STEEP, W.cond(W.noise(-0.2, 0.2), W.block('minecraft:andesite'))),
                W.cond(STEEP, W.block('minecraft:stone')),
                W.cond(W.y_above(175), W.block('minecraft:snow_block')),
                W.cond(W.noise(0.3, 10.0), W.block('minecraft:podzol')))),
            W.cond(UNDER, W.cond(STEEP, W.block('minecraft:stone')))))]

