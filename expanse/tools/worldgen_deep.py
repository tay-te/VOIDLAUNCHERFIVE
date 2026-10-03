"""
The world underground's biomes (world/terrain/CavernModel.java, CavernLife.java), for tools/gen_worldgen.py.

None of them is a copy of a vanilla cave biome. Each is built here from nothing: its own sky and fog, music
(vanilla tracks, as a setting), creatures, and a feature list that holds only what any underground needs,
vanilla's ores (and, in the caves between the realms, geodes and dungeons). Everything that grows or stands
in them is placed in Java, by CavernLife.

* The realms: one biome per character, put into the chunk by the Earth generator wherever a realm is
  (EarthChunkGenerator.decorateBiomeResolver), never by the biome source, so they can carry no features of
  their own that are not also in some biome of the source: hence only the ores, which every overworld biome
  has. Their sky has no sun, moon, stars or clouds (turned below the horizon; the client keeps them out,
  client/mixin/RealmSky.java, for the #expanse:realms tag written here), and their fog takes the far side of a
  realm into the dark, tinted for its character, with motes drifting in it.
* The caves between: four biomes in the biome source, where vanilla's lush caves, dripstone caves, deep dark
  and sulfur caves were (world/biome/UndergroundBiomes.java remaps their entries). They decorate the
  passages, chambers and tunnels that run through them (CavernLife reads the biome).
"""

# The fog colours are dark and nearly grey: night vision scales a fog colour up until its brightest channel is
# full, so a strong tint would turn into a neon sky; a faint one becomes a pale haze.

CAVE_MONSTERS = [('spider', 4, 100), ('zombie', 4, 95), ('zombie_villager', 1, 5), ('skeleton', 4, 100), ('creeper', 4, 100),
                 ('slime', 4, 100), ('enderman', (1, 4), 10), ('witch', 1, 5)]


def spawn(kind, count, weight):
    c = count if isinstance(count, int) else {'type': 'minecraft:uniform', 'max_inclusive': count[1], 'min_inclusive': count[0]}
    return {'type': f'minecraft:{kind}', 'count': c, 'weight': weight}


def monsters(drop=(), add=()):
    out = [spawn(k, c, w) for k, c, w in CAVE_MONSTERS if k not in drop]
    return out + [spawn(k, c, w) for k, c, w in add]


SQUID = [spawn('glow_squid', (4, 6), 10)]
AXOLOTLS = [spawn('axolotl', (4, 6), 10)]
FISH = [spawn('tropical_fish', 8, 25)]

# name: (lang name, sky, fog, fog end, particle, probability, music, temperature, downfall, water, water fog, spawns)
REALMS = {
    'realm_wilds': ('Glowcap Wilds', '#05090a', '#121a1a', 170, 'spore_blossom_air', 0.006, 'overworld.lush_caves', 0.6, 0.8,
                    '#3f76e4', None, dict(monster=monsters())),
    'realm_lush': ('Verdant Hollow', '#060a05', '#141a13', 180, 'spore_blossom_air', 0.004, 'overworld.lush_caves', 0.7, 0.9,
                   '#3f76e4', None, dict(monster=monsters(), axolotls=AXOLOTLS, water_ambient=FISH)),
    'realm_dripstone': ('Stalagmite Deeps', '#090705', '#1a1714', 180, 'falling_dripstone_water', 0.0012, 'overworld.dripstone_caves',
                        0.8, 0.4, '#3f76e4', None, dict(monster=monsters(add=[('drowned', 4, 95)]))),
    'realm_crystal': ('Geode Vaults', '#09060c', '#17141c', 170, 'glow', 0.0012, 'overworld.dripstone_caves', 0.6, 0.3,
                      '#3f76e4', None, dict(monster=monsters(drop=('slime',)))),
    'realm_ember': ('Ember Deeps', '#100402', '#261612', 150, 'white_ash', 0.02, 'nether.basalt_deltas', 1.2, 0.0,
                    '#3f76e4', None, dict(monster=monsters(drop=('slime',), add=[('magma_cube', (2, 4), 40)]))),
    'realm_mere': ('Sunless Mere', '#03070c', '#12161b', 200, None, 0, 'overworld.lush_caves', 0.6, 0.8,
                   '#3f76e4', None, dict(monster=monsters(add=[('drowned', 4, 100)]), axolotls=AXOLOTLS)),
    'realm_frozen': ('Rime Hollows', '#06080b', '#151a1e', 170, 'snowflake', 0.003, 'overworld.frozen_peaks', -0.7, 0.5,
                     '#3938c9', '#050533', dict(monster=monsters(drop=('slime',)))),
    'realm_roots': ('Root Hollows', '#080906', '#16170f', 160, 'firefly', 0.004, 'overworld.old_growth_taiga', 0.7, 0.8,
                    '#3f76e4', None, dict(monster=monsters(add=[('cave_spider', 2, 30)]))),
    'realm_fossil': ('Fossil Deeps', '#0a0806', '#1c1914', 190, 'white_ash', 0.004, 'overworld.desert', 2.0, 0.0,
                     '#32a598', None, dict(monster=monsters(drop=('slime',), add=[('skeleton', 4, 40)]))),
    'realm_tidal': ('Tidal Grottos', '#04080a', '#11181a', 190, 'falling_water', 0.002, 'overworld.lush_caves', 0.5, 0.9,
                    '#2f8fa8', '#062a33', dict(monster=monsters(add=[('drowned', 4, 100)]), axolotls=AXOLOTLS, water_ambient=FISH)),
    'realm_amber': ('Amber Hollows', '#0b0804', '#1e1810', 170, 'falling_honey', 0.0015, 'overworld.old_growth_taiga', 0.6, 0.4,
                    '#c08a2e', '#4a300c', dict(monster=monsters())),
    'realm_brimstone': ('Brimstone Vaults', '#0a0a05', '#1c1c12', 160, 'white_ash', 0.012, 'overworld.sulfur_caves', 1.0, 0.2,
                        '#34bf89', '#17543c', dict(monster=monsters(drop=('slime',), add=[('sulfur_cube', (2, 4), 60), ('magma_cube', (1, 3), 15)]))),
}

# The caves between the realms: (lang name, vanilla cave biome whose entries they take, music, temperature, downfall, spawns)
UNDERGROUND = {
    'mossy_caves': ('Mossgrown Caves', 'lush_caves', 'overworld.lush_caves', 0.5, 0.5,
                    dict(monster=monsters(), axolotls=AXOLOTLS, water_ambient=FISH)),
    'dripstone_grottos': ('Dripstone Grottos', 'dripstone_caves', 'overworld.dripstone_caves', 0.8, 0.4,
                          dict(monster=monsters(add=[('drowned', 4, 95)]))),
    'echoing_depths': ('Echoing Depths', 'deep_dark', 'overworld.deep_dark', 0.8, 0.4, dict(monster=monsters(drop=('slime', 'witch')))),
    'sulfur_seeps': ('Sulfur Seeps', 'sulfur_caves', 'overworld.sulfur_caves', 0.8, 0.4,
                     dict(monster=monsters(drop=('slime',), add=[('sulfur_cube', (2, 4), 80)]))),
}

# Tags the caves between take from the cave biomes they replace (not the deep dark's ancient city: those are gone).
UNDERGROUND_TAGS = ['is_overworld', 'has_structure/mineshaft', 'has_structure/trial_chambers', 'has_structure/ruined_portal_standard',
                    'stronghold_biased_to']


def _ores(jar):
    """Vanilla's ores, in plains' order (the feature sorter needs every biome to list shared features in one order)."""
    import json
    plains = json.loads(jar.read('data/minecraft/worldgen/biome/plains.json'))
    return [f for f in plains['features'][6] if f.startswith('minecraft:ore_')]


def _biome(music, temperature, downfall, water, water_fog, spawns, features):
    categories = {'ambient': [spawn('bat', 8, 10)], 'underground_water_creature': SQUID}
    categories.update(spawns)
    effects = {'water_color': water}
    attributes = {
        'minecraft:audio/background_music': {'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': f'minecraft:music.{music}'}},
        'minecraft:gameplay/natural_mob_spawns': {'argument': {'spawn_costs': {}, 'spawns_by_category': categories}, 'modifier': 'overlay'},
        'minecraft:visual/sky_color': '#78a7ff',
    }
    if water_fog:
        attributes['minecraft:visual/water_fog_color'] = water_fog
    return {'attributes': attributes, 'carvers': [], 'downfall': downfall, 'effects': effects, 'features': features,
            'has_precipitation': downfall > 0, 'temperature': temperature}


def biomes(W, jar):
    """Writes the realm biomes, the #expanse:realms tag, and the biomes of the caves between."""
    ores = _ores(jar)
    for name, (_, sky, fog, end, particle, probability, music, temperature, downfall, water, water_fog, spawns) in REALMS.items():
        features = [[] for _ in range(11)]
        features[6] = list(ores)
        biome = _biome(music, temperature, downfall, water, water_fog, spawns, features)
        biome['attributes'].update({
            'minecraft:visual/sky_color': sky,
            'minecraft:visual/fog_color': fog,
            'minecraft:visual/fog_start_distance': 24.0,
            'minecraft:visual/fog_end_distance': float(end),
            'minecraft:visual/sky_fog_end_distance': 0.0,
            'minecraft:visual/sun_angle': 180.0,
            'minecraft:visual/moon_angle': 180.0,
            'minecraft:visual/star_brightness': 0.0,
            'minecraft:visual/sunrise_sunset_color': '#00000000',
            'minecraft:visual/cloud_color': '#00000000',
        })
        if particle:
            biome['attributes']['minecraft:visual/ambient_particles'] = [{'particle': {'type': f'minecraft:{particle}'}, 'probability': probability}]
        W.data(f'{W.NS}/worldgen/biome/{name}.json', biome)
    # the client keeps the sun out of these and their fog steady whatever the hour (client/mixin/RealmSky.java)
    W.data(f'{W.NS}/tags/worldgen/biome/realms.json', {'values': [W.e(n) for n in REALMS]})

    for name, (_, _, music, temperature, downfall, spawns) in UNDERGROUND.items():
        features = [[] for _ in range(11)]
        features[2] = ['minecraft:amethyst_geode']
        features[3] = ['minecraft:monster_room', 'minecraft:monster_room_deep']
        features[6] = list(ores)
        W.data(f'{W.NS}/worldgen/biome/{name}.json', _biome(music, temperature, downfall, '#3f76e4', None, spawns, features))


def tags(W, tags):
    """Adds the caves between to the vanilla biome tags they need (build_biome_tags' dict of tag -> values)."""
    for tag in UNDERGROUND_TAGS:
        tags.setdefault(tag, []).extend(W.e(n) for n in UNDERGROUND)


def lang(lang, ns='expanse'):
    for name, row in list(REALMS.items()) + list(UNDERGROUND.items()):
        lang[f'biome.{ns}.{name}'] = row[0]
