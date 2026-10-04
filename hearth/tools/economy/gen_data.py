"""VOID Hearth economy data: goods tags, base prices, culture markets and shop kinds.

Run from anywhere:  python hearth/tools/economy/gen_data.py

Writes
  data/hearth/tags/item/goods/<goods>.json        which items count as which goods (all 30 Goods)
  data/hearth/hearth_prices/goods.json            unit values, fallback values, default wares
  data/hearth/hearth_prices/vanilla.json          base values of vanilla items (bits)
  data/hearth/hearth_prices/expanse.json          base values of VOID Expanse items (ignored when Expanse is absent)
  data/hearth/hearth_prices/cultures/<c>.json     each culture's specialities and wares
  data/hearth/hearth_prices/shops.json            what each kind of shop deals in, by workplace and by job

Every vanilla id is checked against the 26.3 client jar and every Expanse id against expanse/'s item definitions, so a
typo fails here rather than silently in game. Vanilla item tags are expanded from the jar, so the script also checks
that no item falls in two goods (the first goods in Goods order would win, silently) and that every ware a market
sells belongs to the goods it is sold as.  Value scale: a loaf of bread is 1 bit, an iron ingot 6, a diamond 120.
"""
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
HEARTH = os.path.normpath(os.path.join(HERE, '..', '..'))
REPO = os.path.dirname(HEARTH)
DATA = os.path.join(HEARTH, 'src', 'main', 'resources', 'data', 'hearth')
CLIENT_JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar')
EXPANSE_ITEMS = os.path.join(REPO, 'expanse', 'src', 'main', 'resources', 'assets', 'expanse', 'items')

# Goods in api/Goods.java order: the first ten keep their ordinals, phase 3 appended the rest.
GOODS = ['food', 'wood', 'stone', 'clay', 'glass', 'metal', 'cloth', 'fuel', 'tools', 'luxury',
         'logs', 'ore', 'dressed_stone', 'brick', 'sand', 'grain', 'flour', 'meat', 'fish', 'wool',
         'leather', 'wax', 'candles', 'paper', 'books', 'dye', 'salt', 'produce', 'specialty']
COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
          'blue', 'brown', 'green', 'red', 'black']
EXPANSE_WOODS = ['aspen', 'baobab', 'beech', 'eucalyptus', 'kapok', 'lumen', 'maple', 'olive', 'palm', 'pine', 'redwood',
                 'teak', 'willow', 'wisteria']


def load_ids():
    tags = {}
    with zipfile.ZipFile(CLIENT_JAR) as z:
        names = z.namelist()
        vanilla = {n[len('assets/minecraft/items/'):-5] for n in names
                   if n.startswith('assets/minecraft/items/') and n.endswith('.json')}
        for n in names:
            if n.startswith('data/minecraft/tags/item/') and n.endswith('.json'):
                tags['minecraft:' + n[len('data/minecraft/tags/item/'):-5]] = json.loads(z.read(n))['values']
    expanse = {f[:-5] for f in os.listdir(EXPANSE_ITEMS) if f.endswith('.json')} if os.path.isdir(EXPANSE_ITEMS) else set()
    return vanilla, expanse, tags


VANILLA, EXPANSE, VANILLA_TAGS = load_ids()
errors = []


def check(ident):
    """Validates 'ns:id' or '#ns:tag' or 'a|b' alternatives."""
    for alt in ident.split('|'):
        if alt.startswith('#'):
            if alt.startswith('#minecraft:') and alt[1:] not in VANILLA_TAGS:
                errors.append(alt)
            continue
        ns, _, path = alt.partition(':')
        known = VANILLA if ns == 'minecraft' else EXPANSE if ns == 'expanse' else None
        if known is not None and path not in known:
            errors.append(alt)
    return ident


def mc(*names):
    return [check('minecraft:' + n) for n in names]


def ex(*names):
    return [check('expanse:' + n) for n in names]


def expand(entry, seen=None):
    """The item ids an entry stands for (vanilla tags expanded from the jar; optional tags of other mods: none)."""
    if isinstance(entry, dict):
        entry = entry['id']
    if not entry.startswith('#'):
        return {entry}
    tag = entry[1:]
    if tag not in VANILLA_TAGS:
        return set()
    seen = seen or set()
    if tag in seen:
        return set()
    seen.add(tag)
    out = set()
    for v in VANILLA_TAGS[tag]:
        out |= expand(v, seen)
    return out


# ------------------------------------------------------------------ goods tags

def expanse_woods(*suffixes):
    out = []
    for w in EXPANSE_WOODS:
        for s in suffixes:
            name = f'{w}_{s}'
            if name in EXPANSE:
                out.append('expanse:' + name)
            if s in ('log', 'wood') and f'stripped_{name}' in EXPANSE:
                out.append(f'expanse:stripped_{name}')
    return out


def optional_tag(tag):
    """A tag other mods may define (conventional tags): referenced but not required."""
    return {'id': '#' + tag, 'required': False}


TAGS = {
    # prepared food: what folk eat. Raw meat and fish, grain and produce are their own goods now.
    'food': mc('bread', 'baked_potato', 'cooked_beef', 'cooked_porkchop', 'cooked_chicken', 'cooked_mutton',
               'cooked_rabbit', 'cooked_cod', 'cooked_salmon', 'mushroom_stew', 'rabbit_stew', 'beetroot_soup',
               'pumpkin_pie', 'cookie', 'cake', 'dried_kelp', 'golden_carrot', 'golden_apple')
        + ex('cooked_venison', 'cooked_crab'),
    # sawn timber and joinery (raw logs are LOGS)
    'wood': ['#minecraft:planks', '#minecraft:wooden_slabs', '#minecraft:wooden_stairs', '#minecraft:wooden_fences',
             '#minecraft:fence_gates', '#minecraft:wooden_doors', '#minecraft:wooden_trapdoors',
             '#minecraft:wooden_shelves', '#minecraft:boats', '#minecraft:signs'] + mc(
        'stick', 'bamboo_mosaic', 'chest', 'barrel', 'crafting_table', 'ladder', 'bowl', 'scaffolding')
        + expanse_woods('planks', 'slab', 'stairs', 'fence', 'fence_gate', 'door', 'trapdoor'),
    # rough stone: rubble, field stone, the quarry's raw output (cut stone is DRESSED_STONE)
    'stone': mc('cobblestone', 'stone', 'mossy_cobblestone', 'andesite', 'diorite', 'granite', 'deepslate',
                'cobbled_deepslate', 'tuff', 'calcite', 'sandstone', 'red_sandstone', 'blackstone', 'basalt', 'gravel',
                'flint', 'cobblestone_slab', 'cobblestone_stairs', 'cobblestone_wall', 'mossy_cobblestone_slab',
                'mossy_cobblestone_stairs', 'mossy_cobblestone_wall', 'cobbled_deepslate_slab', 'cobbled_deepslate_stairs',
                'cobbled_deepslate_wall', 'dripstone_block', 'obsidian')
        + ex('limestone', 'mossy_limestone', 'opal_sandstone', 'volcanic_ash'),
    # raw clay and mud (fired brick and terracotta are BRICK)
    'clay': mc('clay_ball', 'clay', 'mud') + ex('red_earth'),
    'glass': mc('glass', 'glass_pane', 'tinted_glass', 'glass_bottle')
        + [check(f'minecraft:{c}_stained_glass') for c in COLORS]
        + [check(f'minecraft:{c}_stained_glass_pane') for c in COLORS],
    # worked metal (raw ore is ORE)
    'metal': ['#minecraft:chains', '#minecraft:lanterns', '#minecraft:rails', '#minecraft:anvil'] + mc(
        'iron_ingot', 'iron_nugget', 'iron_block', 'copper_ingot', 'copper_block', 'copper_nugget', 'gold_ingot',
        'gold_nugget', 'gold_block', 'iron_bars', 'cauldron', 'bucket', 'minecart', 'hopper', 'netherite_ingot',
        'netherite_scrap', 'bell'),
    # woven cloth and finished textiles (fleece is WOOL, hides are LEATHER)
    'cloth': ['#minecraft:wool_carpets', '#minecraft:beds', '#minecraft:banners', '#minecraft:cushions']
        + mc('string', 'item_frame', 'painting') + ex('glowworm_silk', 'cotton_grass'),
    'fuel': ['#minecraft:coal_ores'] + mc('coal', 'charcoal', 'coal_block', 'dried_kelp_block', 'blaze_rod', 'torch')
        + ex('peat'),
    'tools': ['#minecraft:axes', '#minecraft:pickaxes', '#minecraft:shovels', '#minecraft:hoes', '#minecraft:swords',
              '#minecraft:spears', '#minecraft:arrows', '#minecraft:head_armor', '#minecraft:chest_armor',
              '#minecraft:leg_armor', '#minecraft:foot_armor'] + mc(
        'shears', 'flint_and_steel', 'fishing_rod', 'bow', 'crossbow', 'shield', 'brush', 'spyglass', 'compass', 'lead',
        'saddle', 'name_tag', 'trident'),
    # fine things: glazed ware, gems, curios
    'luxury': ['#minecraft:glazed_terracotta', '#minecraft:decorated_pot_sherds'] + mc(
        'diamond', 'amethyst_shard', 'lapis_lazuli', 'quartz', 'glowstone_dust', 'clock', 'ender_pearl',
        'experience_bottle', 'music_disc_cat', 'music_disc_13', 'enchanted_golden_apple', 'redstone')
        + ex('insect_amber'),
    # ---- phase 3
    'logs': ['#minecraft:logs', '#minecraft:bamboo_blocks'] + expanse_woods('log', 'wood'),
    'ore': ['#minecraft:iron_ores', '#minecraft:copper_ores', '#minecraft:gold_ores'] + mc(
        'raw_iron', 'raw_iron_block', 'raw_copper', 'raw_copper_block', 'raw_gold', 'raw_gold_block', 'ancient_debris'),
    'dressed_stone': ['#minecraft:stone_bricks'] + mc(
        'smooth_stone', 'polished_andesite', 'polished_diorite', 'polished_granite', 'polished_deepslate',
        'deepslate_bricks', 'deepslate_tiles', 'tuff_bricks', 'polished_tuff', 'smooth_sandstone', 'cut_sandstone',
        'chiseled_sandstone', 'smooth_red_sandstone', 'cut_red_sandstone', 'chiseled_red_sandstone', 'polished_blackstone',
        'polished_blackstone_bricks', 'stone_slab', 'stone_stairs', 'smooth_stone_slab', 'stone_brick_slab',
        'stone_brick_stairs', 'stone_brick_wall', 'sandstone_slab', 'sandstone_stairs', 'sandstone_wall')
        + ex('limestone_bricks', 'polished_limestone', 'chiseled_limestone', 'limestone_slab', 'limestone_stairs',
             'limestone_wall', 'limestone_brick_slab', 'limestone_brick_stairs', 'limestone_brick_wall',
             'cut_opal_sandstone', 'smooth_opal_sandstone', 'chiseled_opal_sandstone', 'opal_sandstone_slab',
             'opal_sandstone_stairs', 'opal_sandstone_wall'),
    'brick': ['#minecraft:terracotta'] + mc(
        'brick', 'bricks', 'brick_slab', 'brick_stairs', 'brick_wall', 'packed_mud', 'mud_bricks', 'mud_brick_slab',
        'mud_brick_stairs', 'mud_brick_wall', 'flower_pot', 'decorated_pot'),
    'sand': ['#minecraft:sand'] + ex('opal_sand', 'coral_sand'),
    'grain': mc('wheat', 'wheat_seeds', 'hay_block'),
    # no flour in vanilla: other mods' conventional tags, if any
    'flour': [optional_tag('c:flours'), optional_tag('c:foods/flour')],
    'meat': mc('beef', 'porkchop', 'chicken', 'mutton', 'rabbit') + ex('venison'),
    'fish': mc('cod', 'salmon', 'tropical_fish', 'pufferfish') + ex('crab_meat'),
    'wool': ['#minecraft:wool'] + mc('feather'),
    'leather': mc('leather', 'rabbit_hide'),
    'wax': mc('honeycomb', 'honeycomb_block'),
    'candles': ['#minecraft:candles'],
    'paper': mc('paper'),
    'books': mc('book', 'writable_book', 'enchanted_book', 'bookshelf', 'chiseled_bookshelf', 'lectern', 'map'),
    'dye': ['#minecraft:dyes'] + mc('ink_sac', 'glow_ink_sac'),
    'salt': ex('salt_block') + [optional_tag('c:salts')],
    'produce': ['#minecraft:eggs'] + mc(
        'apple', 'carrot', 'potato', 'beetroot', 'melon_slice', 'melon', 'pumpkin', 'sweet_berries', 'glow_berries',
        'sugar', 'sugar_cane', 'red_mushroom', 'brown_mushroom', 'milk_bucket', 'honey_bottle', 'beetroot_seeds',
        'pumpkin_seeds', 'melon_seeds') + ex('cranberries'),
    # a land's rarities: what one place has and others must trade for
    'specialty': ['#minecraft:small_flowers'] + mc('bamboo', 'cocoa_beans', 'kelp', 'chorus_fruit', 'nether_wart',
                                                   'glow_lichen')
        + ex('amber', 'prismite_cluster', 'prismite_block', 'glowcap', 'glowroots', 'lavender', 'heather', 'edelweiss',
             'desert_marigold', 'frostbloom', 'moth_orchid', 'bluebells', 'fireweed', 'wisteria_blossoms',
             'azure_wisteria_blossoms', 'reindeer_lichen', 'cattail', 'desert_pea', 'spanish_moss'),
}

# ------------------------------------------------------------------ base values (bits)

V = {}


def val(value, *names, ns='minecraft'):
    for n in names:
        V[check(f'{ns}:{n}' if not n.startswith('#') else n)] = value


def tag(value, *tags):
    for t in tags:
        V['#' + check('#' + t)[1:]] = value


# food and the land's produce
val(1.0, 'bread'); val(0.6, 'apple'); val(0.3, 'carrot', 'potato', 'beetroot', 'wheat', 'sugar', 'red_mushroom', 'brown_mushroom')
val(0.6, 'baked_potato'); val(0.2, 'melon_slice', 'sweet_berries', 'sugar_cane'); val(1.8, 'melon'); val(0.8, 'pumpkin')
val(2.0, 'pumpkin_pie', 'milk_bucket', 'honey_bottle'); val(0.4, 'cookie', 'glow_berries'); val(6.0, 'cake', 'golden_carrot')
val(0.15, 'dried_kelp'); val(0.05, 'kelp', 'wheat_seeds', 'beetroot_seeds', 'rotten_flesh'); val(0.1, 'pumpkin_seeds', 'melon_seeds')
val(1.0, 'beef', 'porkchop'); val(1.6, 'cooked_beef', 'cooked_porkchop'); val(0.7, 'chicken'); val(1.2, 'cooked_chicken')
val(0.8, 'mutton', 'rabbit', 'salmon', 'tropical_fish'); val(1.3, 'cooked_mutton', 'cooked_rabbit', 'cooked_salmon')
val(0.6, 'cod'); val(1.0, 'cooked_cod', 'pufferfish'); val(1.5, 'mushroom_stew', 'beetroot_soup'); val(3.0, 'rabbit_stew')
val(0.25, 'egg', 'blue_egg', 'brown_egg'); val(0.5, 'cocoa_beans', 'chorus_fruit', 'nether_wart'); val(30.0, 'golden_apple')
val(600.0, 'enchanted_golden_apple'); val(2.8, 'hay_block'); val(0.3, 'spider_eye', 'glow_lichen')
val(1.0, 'venison', ns='expanse'); val(1.6, 'cooked_venison', ns='expanse'); val(0.8, 'crab_meat', ns='expanse')
val(1.4, 'cooked_crab', ns='expanse'); val(0.25, 'cranberries', ns='expanse')
# timber
tag(0.5, 'minecraft:logs', 'minecraft:bamboo_blocks'); tag(0.125, 'minecraft:planks'); tag(0.07, 'minecraft:wooden_slabs')
tag(0.2, 'minecraft:wooden_stairs', 'minecraft:wooden_fences'); tag(0.5, 'minecraft:fence_gates')
tag(0.3, 'minecraft:wooden_doors', 'minecraft:signs'); tag(0.4, 'minecraft:wooden_trapdoors'); tag(0.8, 'minecraft:wooden_shelves')
tag(0.7, 'minecraft:boats')
val(0.06, 'stick', 'bamboo'); val(0.125, 'bamboo_mosaic'); val(1.2, 'chest'); val(1.0, 'barrel'); val(0.6, 'crafting_table')
val(0.15, 'ladder', 'scaffolding'); val(0.05, 'bowl')
# stone, rough and dressed
val(0.1, 'cobblestone', 'andesite', 'diorite', 'granite', 'cobbled_deepslate', 'tuff'); val(0.15, 'stone', 'deepslate', 'sandstone',
    'red_sandstone', 'blackstone', 'polished_andesite', 'polished_diorite', 'polished_granite')
val(0.2, 'smooth_stone', 'mossy_cobblestone', 'calcite', 'smooth_sandstone', 'cut_sandstone', 'smooth_red_sandstone',
    'cut_red_sandstone', 'dripstone_block', 'polished_blackstone'); val(0.18, 'polished_deepslate', 'polished_tuff')
tag(0.25, 'minecraft:stone_bricks'); val(0.25, 'deepslate_bricks', 'tuff_bricks'); val(0.3, 'deepslate_tiles', 'chiseled_sandstone',
    'chiseled_red_sandstone', 'polished_blackstone_bricks'); val(0.12, 'basalt', 'cobblestone_wall', 'mossy_cobblestone_wall',
    'cobbled_deepslate_wall'); val(0.06, 'gravel')
val(0.2, 'flint'); val(0.05, 'cobblestone_slab', 'cobbled_deepslate_slab'); val(0.1, 'mossy_cobblestone_slab')
val(0.08, 'stone_slab', 'smooth_stone_slab', 'sandstone_slab'); val(0.13, 'stone_brick_slab')
val(0.15, 'cobblestone_stairs', 'cobbled_deepslate_stairs', 'stone_stairs', 'sandstone_stairs', 'sandstone_wall')
val(0.2, 'mossy_cobblestone_stairs'); val(0.3, 'stone_brick_stairs'); val(0.25, 'stone_brick_wall'); val(3.0, 'obsidian')
val(0.15, 'limestone', ns='expanse'); val(0.25, 'limestone_bricks', ns='expanse'); val(0.2, 'polished_limestone', 'mossy_limestone',
    'opal_sandstone', ns='expanse'); val(0.3, 'chiseled_limestone', ns='expanse')
val(0.08, 'limestone_slab', ns='expanse'); val(0.15, 'limestone_stairs', 'limestone_wall', ns='expanse')
val(0.13, 'limestone_brick_slab', ns='expanse'); val(0.3, 'limestone_brick_stairs', ns='expanse'); val(0.25, 'limestone_brick_wall', ns='expanse')
val(0.25, 'cut_opal_sandstone', 'smooth_opal_sandstone', ns='expanse'); val(0.35, 'chiseled_opal_sandstone', ns='expanse')
val(0.1, 'opal_sandstone_slab', ns='expanse'); val(0.2, 'opal_sandstone_stairs', 'opal_sandstone_wall', ns='expanse')
val(0.05, 'volcanic_ash', ns='expanse')
# clay and brick
val(0.25, 'clay_ball'); val(1.0, 'clay'); val(0.4, 'brick'); val(1.6, 'bricks', 'brick_stairs', 'brick_wall'); val(0.8, 'brick_slab')
tag(0.5, 'minecraft:terracotta'); val(0.1, 'mud'); val(0.2, 'packed_mud'); val(0.3, 'mud_bricks', 'mud_brick_stairs', 'mud_brick_wall')
val(0.15, 'mud_brick_slab'); val(1.2, 'flower_pot'); val(2.0, 'decorated_pot'); val(0.08, 'red_earth', ns='expanse')
# glass and sand
tag(0.08, 'minecraft:sand'); val(0.4, 'glass'); val(0.15, 'glass_pane', 'glass_bottle'); val(3.0, 'tinted_glass')
for c in COLORS:
    val(0.5, f'{c}_stained_glass'); val(0.2, f'{c}_stained_glass_pane')
val(0.1, 'opal_sand', 'coral_sand', ns='expanse')
# ore and metal
val(6.0, 'iron_ingot'); val(0.6, 'iron_nugget'); val(4.5, 'raw_iron'); val(54.0, 'iron_block'); val(40.0, 'raw_iron_block')
val(1.5, 'copper_ingot'); val(1.1, 'raw_copper'); val(13.0, 'copper_block'); val(10.0, 'raw_copper_block'); val(0.15, 'copper_nugget')
val(12.0, 'gold_ingot'); val(1.2, 'gold_nugget'); val(9.0, 'raw_gold'); val(108.0, 'gold_block'); val(80.0, 'raw_gold_block')
tag(4.5, 'minecraft:iron_ores'); tag(1.1, 'minecraft:copper_ores'); tag(9.0, 'minecraft:gold_ores'); tag(7.0, 'minecraft:chains')
tag(4.0, 'minecraft:lanterns'); tag(2.5, 'minecraft:rails'); tag(190.0, 'minecraft:anvil')
val(2.2, 'iron_bars'); val(40.0, 'cauldron'); val(18.0, 'bucket'); val(30.0, 'minecart'); val(32.0, 'hopper'); val(10.0, 'powered_rail')
val(400.0, 'netherite_ingot'); val(90.0, 'netherite_scrap', 'ancient_debris'); val(60.0, 'bell')
# fleece, hides, cloth
tag(1.0, 'minecraft:wool'); tag(0.7, 'minecraft:wool_carpets'); tag(4.0, 'minecraft:beds'); tag(6.0, 'minecraft:banners')
tag(1.5, 'minecraft:cushions'); val(2.0, 'leather'); val(0.5, 'rabbit_hide'); val(0.3, 'string'); val(0.25, 'feather')
val(2.5, 'item_frame'); val(1.5, 'painting'); val(2.0, 'glowworm_silk', ns='expanse'); val(0.3, 'cotton_grass', ns='expanse')
# fuel
val(1.0, 'coal'); val(0.8, 'charcoal'); val(9.0, 'coal_block'); tag(1.0, 'minecraft:coal_ores'); val(1.4, 'dried_kelp_block')
val(6.0, 'blaze_rod'); val(0.25, 'torch'); val(0.6, 'peat', ns='expanse')
# tools and armour, from what they are made of
MATERIAL = {'wooden': 0.125, 'stone': 0.1, 'copper': 1.5, 'iron': 6.0, 'golden': 12.0, 'diamond': 120.0}
TOOL = {'sword': 2, 'pickaxe': 3, 'axe': 3, 'shovel': 1, 'hoe': 2, 'spear': 1}
for m, v in MATERIAL.items():
    for t, n in TOOL.items():
        name = f'{m}_{t}'
        if name in VANILLA:
            val(round(n * v * 1.25 + 0.15, 2), name)
for t, n in TOOL.items():
    if f'netherite_{t}' in VANILLA:
        val(round(n * 120 * 1.25 + 450, 2), f'netherite_{t}')
ARMOR = {'helmet': 5, 'chestplate': 8, 'leggings': 7, 'boots': 4}
for m, v in (('leather', 2.0), ('chainmail', 3.0), ('copper', 1.5), ('iron', 6.0), ('golden', 12.0), ('diamond', 120.0)):
    for a, n in ARMOR.items():
        if f'{m}_{a}' in VANILLA:
            val(round(n * v * 1.25, 2), f'{m}_{a}')
for a, n in ARMOR.items():
    val(round(n * 120 * 1.25 + 450, 2), f'netherite_{a}')
val(14.0, 'shears'); val(7.0, 'flint_and_steel'); val(1.5, 'fishing_rod', 'lead'); val(2.0, 'bow', 'brush'); val(12.0, 'crossbow')
tag(0.3, 'minecraft:arrows'); val(9.0, 'shield'); val(6.0, 'spyglass'); val(26.0, 'compass'); val(15.0, 'saddle', 'name_tag')
val(120.0, 'trident'); val(5.0, 'turtle_helmet')
# wax, candles, paper, books, dye
val(1.5, 'honeycomb'); val(6.0, 'honeycomb_block'); tag(1.5, 'minecraft:candles'); val(0.3, 'paper'); val(2.0, 'book')
val(3.0, 'writable_book'); val(24.0, 'enchanted_book'); val(7.0, 'bookshelf'); val(9.0, 'chiseled_bookshelf'); val(10.0, 'lectern')
val(4.0, 'map'); tag(0.4, 'minecraft:dyes'); val(0.4, 'ink_sac'); val(2.0, 'glow_ink_sac')
# fine things and the land's rarities
tag(1.5, 'minecraft:glazed_terracotta'); tag(0.3, 'minecraft:small_flowers'); tag(10.0, 'minecraft:decorated_pot_sherds')
val(2.0, 'lapis_lazuli'); val(3.0, 'amethyst_shard'); val(1.5, 'quartz', 'glowstone_dust'); val(120.0, 'diamond')
val(0.6, 'redstone'); val(50.0, 'clock'); val(15.0, 'ender_pearl'); val(20.0, 'experience_bottle')
val(60.0, 'music_disc_cat', 'music_disc_13'); val(25.0, 'emerald')
val(4.0, 'amber', ns='expanse'); val(25.0, 'insect_amber', ns='expanse'); val(6.0, 'prismite_cluster', ns='expanse')
val(20.0, 'prismite_block', ns='expanse'); val(1.5, 'glowcap', ns='expanse'); val(0.8, 'glowroots', ns='expanse')
val(0.5, 'lavender', 'heather', 'desert_marigold', 'bluebells', 'fireweed', 'reindeer_lichen', 'cattail', 'desert_pea',
    'spanish_moss', ns='expanse')
val(2.0, 'edelweiss', 'frostbloom', ns='expanse'); val(0.4, 'wisteria_blossoms', 'azure_wisteria_blossoms', ns='expanse')
val(3.0, 'salt_block', ns='expanse'); val(1.0, 'moth_orchid', ns='expanse')

# ------------------------------------------------------------------ goods.json

# Bits one stockpile unit of each goods is worth: about one block or one item of its typical form, so a blueprint's
# bill (one unit per block) and the market agree. An item's units are its value over this (at least 1).
UNITS = {'food': 1.0, 'wood': 0.125, 'stone': 0.1, 'clay': 1.0, 'glass': 0.4, 'metal': 6.0, 'cloth': 1.0, 'fuel': 1.0,
         'tools': 10.0, 'luxury': 2.0, 'logs': 0.5, 'ore': 4.5, 'dressed_stone': 0.25, 'brick': 1.0, 'sand': 0.08,
         'grain': 0.3, 'flour': 0.4, 'meat': 0.9, 'fish': 0.7, 'wool': 1.0, 'leather': 2.0, 'wax': 1.5, 'candles': 1.5,
         'paper': 0.3, 'books': 2.0, 'dye': 0.4, 'salt': 0.5, 'produce': 0.4, 'specialty': 1.0}
# What an item of the goods is worth when the price files do not name it (times its rarity).
DEFAULTS = {'food': 0.8, 'wood': 0.3, 'stone': 0.15, 'clay': 0.5, 'glass': 0.5, 'metal': 4.0, 'cloth': 1.0, 'fuel': 1.0,
            'tools': 15.0, 'luxury': 5.0, 'logs': 0.5, 'ore': 3.0, 'dressed_stone': 0.3, 'brick': 0.6, 'sand': 0.1,
            'grain': 0.3, 'flour': 0.4, 'meat': 0.9, 'fish': 0.7, 'wool': 1.0, 'leather': 2.0, 'wax': 1.5,
            'candles': 1.5, 'paper': 0.3, 'books': 3.0, 'dye': 0.4, 'salt': 0.5, 'produce': 0.4, 'specialty': 1.5}

DEFAULT_WARES = {
    'food': mc('bread', 'baked_potato', 'cooked_cod'),
    'wood': mc('oak_planks', 'oak_slab', 'stick'),
    'stone': mc('cobblestone', 'stone', 'gravel'),
    'clay': mc('clay'),
    'glass': mc('glass', 'glass_pane', 'glass_bottle'),
    'metal': mc('iron_ingot', 'copper_ingot', 'lantern'),
    'cloth': mc('white_carpet', 'string', 'white_bed'),
    'fuel': mc('charcoal', 'coal', 'torch'),
    'tools': mc('iron_pickaxe', 'iron_axe', 'iron_shovel', 'shears'),
    'luxury': mc('amethyst_shard', 'quartz', 'lapis_lazuli'),
    'logs': mc('oak_log', 'birch_log', 'spruce_log'),
    'ore': mc('raw_iron', 'raw_copper'),
    'dressed_stone': mc('stone_bricks', 'smooth_stone', 'polished_andesite'),
    'brick': mc('bricks', 'terracotta'),
    'sand': mc('sand', 'red_sand'),
    'grain': mc('wheat', 'hay_block'),
    'flour': [],
    'meat': mc('beef', 'porkchop', 'mutton'),
    'fish': mc('cod', 'salmon'),
    'wool': mc('white_wool', 'brown_wool'),
    'leather': mc('leather', 'rabbit_hide'),
    'wax': mc('honeycomb'),
    'candles': mc('candle', 'white_candle'),
    'paper': mc('paper'),
    'books': mc('book', 'writable_book'),
    'dye': mc('red_dye', 'yellow_dye', 'blue_dye'),
    'salt': ex('salt_block'),
    'produce': mc('apple', 'carrot', 'potato'),
    'specialty': mc('cocoa_beans', 'bamboo', 'kelp'),
}

GOODS_JSON = {
    'units': UNITS,
    'defaults': DEFAULTS,
    'untagged': 0.25,
    'rarity': {'common': 1.0, 'uncommon': 4.0, 'rare': 12.0, 'epic': 40.0},
    'wares': DEFAULT_WARES,
}


def alt(expanse_id, vanilla_id):
    return check(f'expanse:{expanse_id}|minecraft:{vanilla_id}')


# Where a phase 3 goods takes its speciality from when a culture says nothing of it: the goods it was split from.
DERIVED = {'logs': 'wood', 'dressed_stone': 'stone', 'brick': 'clay', 'sand': 'glass', 'grain': 'food', 'flour': 'food',
           'produce': 'food', 'meat': 'food', 'fish': 'food', 'wool': 'cloth', 'leather': 'cloth', 'wax': 'luxury',
           'candles': 'luxury', 'paper': 'luxury', 'books': 'luxury', 'dye': 'luxury', 'ore': 'metal'}

# speciality below 1: plentiful and cheap here; above 1: imported and dear
CULTURES = {
    'temperate': ({'wood': 0.9, 'food': 0.85, 'cloth': 0.9, 'glass': 1.1, 'metal': 1.1, 'luxury': 1.1, 'grain': 0.8,
                   'flour': 0.85, 'produce': 0.85, 'wax': 0.95, 'salt': 1.1, 'specialty': 1.15}, {
        'wood': mc('oak_planks', 'oak_slab') + [alt('beech_planks', 'birch_planks')],
        'logs': mc('oak_log') + [alt('beech_log', 'birch_log')],
        'food': mc('bread', 'baked_potato', 'cooked_porkchop', 'pumpkin_pie'),
        'stone': mc('cobblestone', 'stone'),
        'dressed_stone': mc('stone_bricks', 'smooth_stone'),
        'brick': mc('bricks', 'terracotta'),
        'cloth': mc('white_carpet', 'white_bed'),
        'produce': mc('apple', 'potato', 'carrot'),
        'luxury': mc('amethyst_shard', 'lapis_lazuli')}),
    'wisteria': ({'wood': 0.8, 'luxury': 0.8, 'clay': 0.85, 'cloth': 0.9, 'metal': 1.2, 'stone': 1.1, 'fuel': 1.1, 'dye': 0.7,
                  'paper': 0.85, 'specialty': 0.8, 'wax': 0.85, 'candles': 0.85}, {
        'wood': [alt('wisteria_planks', 'cherry_planks')] + mc('cherry_planks', 'cherry_slab'),
        'logs': [alt('wisteria_log', 'cherry_log')] + mc('cherry_log'),
        'food': mc('bread', 'cooked_salmon', 'cookie'),
        'brick': mc('terracotta', 'white_terracotta', 'bricks'),
        'cloth': mc('purple_carpet', 'pink_carpet'),
        'wool': mc('purple_wool', 'pink_wool'),
        'dye': mc('purple_dye', 'pink_dye', 'magenta_dye'),
        'specialty': [alt('wisteria_blossoms', 'allium'), alt('azure_wisteria_blossoms', 'cornflower')]}),
    'northern': ({'wood': 0.7, 'food': 0.95, 'fuel': 0.85, 'cloth': 0.9, 'glass': 1.25, 'luxury': 1.2, 'clay': 1.15,
                  'logs': 0.6, 'meat': 0.85, 'leather': 0.85, 'grain': 1.15, 'produce': 1.1}, {
        'wood': [alt('pine_planks', 'spruce_planks')] + mc('spruce_planks', 'spruce_slab'),
        'logs': [alt('redwood_log', 'spruce_log'), alt('pine_log', 'dark_oak_log')] + mc('spruce_log'),
        'food': [alt('cooked_venison', 'cooked_beef')] + mc('bread', 'cooked_salmon'),
        'meat': [alt('venison', 'beef')] + mc('rabbit'),
        'stone': mc('cobblestone', 'mossy_cobblestone'),
        'leather': mc('leather', 'rabbit_hide'),
        'tools': mc('iron_axe', 'bow', 'iron_shovel'),
        'fuel': mc('charcoal', 'coal')}),
    'alpine': ({'stone': 0.8, 'wood': 0.85, 'cloth': 0.85, 'glass': 1.2, 'luxury': 1.1, 'clay': 1.1, 'dressed_stone': 0.8,
                'ore': 0.85, 'wool': 0.8, 'produce': 1.1, 'specialty': 0.9}, {
        'wood': mc('spruce_planks', 'dark_oak_planks'),
        'logs': mc('spruce_log', 'dark_oak_log'),
        'dressed_stone': mc('stone_bricks', 'polished_andesite'),
        'stone': mc('cobblestone', 'andesite'),
        'food': mc('bread', 'cooked_mutton', 'baked_potato'),
        'produce': mc('milk_bucket', 'potato'),
        'wool': mc('white_wool', 'gray_wool'),
        'ore': mc('raw_iron', 'raw_copper'),
        'specialty': [alt('edelweiss', 'lily_of_the_valley')]}),
    'tundra': ({'food': 1.1, 'wood': 1.25, 'stone': 0.95, 'cloth': 0.75, 'fuel': 1.3, 'glass': 1.3, 'metal': 1.2, 'luxury': 1.3,
                'fish': 0.8, 'meat': 0.9, 'wool': 0.75, 'leather': 0.75, 'grain': 1.45, 'flour': 1.4, 'produce': 1.4}, {
        'food': mc('cooked_cod', 'cooked_salmon', 'dried_kelp') + [alt('cooked_venison', 'cooked_mutton')],
        'fish': mc('cod', 'salmon'),
        'leather': mc('leather', 'rabbit_hide'),
        'wool': mc('white_wool'),
        'logs': mc('spruce_log'),
        'specialty': [alt('frostbloom', 'blue_orchid'), alt('reindeer_lichen', 'glow_lichen')]}),
    'moorland': ({'stone': 0.75, 'cloth': 0.8, 'fuel': 0.8, 'wood': 1.3, 'luxury': 1.05, 'glass': 1.15, 'wool': 0.7,
                  'logs': 1.35, 'meat': 0.9}, {
        'stone': mc('cobblestone', 'mossy_cobblestone', 'cobbled_deepslate'),
        'dressed_stone': mc('stone_bricks', 'deepslate_bricks'),
        'wool': mc('white_wool', 'light_gray_wool'),
        'cloth': mc('gray_carpet', 'white_carpet'),
        'fuel': [alt('peat', 'charcoal')] + mc('coal'),
        'food': mc('cooked_mutton', 'baked_potato', 'bread'),
        'specialty': [alt('heather', 'allium')]}),
    'maritime': ({'food': 0.8, 'stone': 0.9, 'glass': 0.95, 'wood': 1.1, 'luxury': 1.0, 'fish': 0.65, 'salt': 0.75,
                  'specialty': 0.9}, {
        'food': mc('cooked_cod', 'cooked_salmon', 'dried_kelp') + [alt('cooked_crab', 'pumpkin_pie')],
        'fish': mc('cod', 'salmon') + [alt('crab_meat', 'tropical_fish')],
        'cloth': mc('string', 'white_carpet'),
        'dressed_stone': mc('stone_bricks', 'smooth_stone'),
        'tools': mc('fishing_rod', 'iron_axe', 'shears'),
        'salt': ex('salt_block'),
        'specialty': mc('kelp')}),
    'tropical': ({'wood': 0.75, 'food': 0.8, 'luxury': 0.85, 'metal': 1.35, 'stone': 1.2, 'cloth': 1.15, 'fuel': 1.1, 'glass': 1.1,
                  'produce': 0.7, 'specialty': 0.7, 'dye': 0.8, 'grain': 1.1, 'wool': 1.3}, {
        'wood': [alt('palm_planks', 'jungle_planks')] + mc('jungle_planks'),
        'logs': mc('jungle_log', 'bamboo_block') + [alt('kapok_log', 'acacia_log')],
        'food': mc('cooked_cod') + [alt('cooked_crab', 'cooked_salmon')],
        'produce': mc('melon_slice', 'melon', 'sweet_berries'),
        'specialty': mc('cocoa_beans', 'bamboo'),
        'dye': mc('lime_dye', 'cyan_dye'),
        'cloth': mc('lime_carpet', 'string')}),
    'desert': ({'stone': 0.8, 'glass': 0.7, 'clay': 0.85, 'luxury': 0.85, 'food': 1.3, 'wood': 1.4, 'cloth': 1.1, 'fuel': 1.2,
                'sand': 0.6, 'salt': 0.7, 'dye': 0.8, 'logs': 1.5, 'produce': 1.3, 'grain': 1.25}, {
        'stone': mc('sandstone') + [alt('opal_sandstone', 'red_sandstone')],
        'dressed_stone': mc('cut_sandstone', 'smooth_sandstone', 'chiseled_sandstone'),
        'sand': mc('sand') + [alt('opal_sand', 'red_sand')],
        'glass': mc('glass', 'glass_pane'),
        'brick': mc('terracotta', 'orange_terracotta', 'mud_bricks'),
        'food': mc('bread', 'cooked_rabbit'),
        'dye': mc('green_dye', 'red_dye', 'yellow_dye'),
        'salt': ex('salt_block'),
        'cloth': mc('orange_carpet', 'red_carpet')}),
    'mediterranean': ({'stone': 0.85, 'clay': 0.75, 'food': 0.85, 'luxury': 0.9, 'wood': 1.15, 'metal': 1.1, 'brick': 0.75,
                       'produce': 0.8, 'wax': 0.85, 'salt': 0.9, 'fish': 0.9}, {
        'dressed_stone': mc('polished_diorite', 'smooth_sandstone', 'stone_bricks'),
        'stone': mc('calcite', 'diorite'),
        'brick': mc('terracotta', 'orange_terracotta', 'bricks', 'decorated_pot'),
        'wood': [alt('olive_planks', 'oak_planks')],
        'logs': [alt('olive_log', 'oak_log')],
        'food': mc('bread', 'cooked_cod', 'beetroot_soup'),
        'produce': mc('apple', 'honey_bottle', 'beetroot'),
        'wax': mc('honeycomb')}),
    'steppe': ({'cloth': 0.75, 'food': 0.9, 'clay': 0.9, 'wood': 1.35, 'stone': 1.1, 'metal': 1.1, 'glass': 1.3, 'wool': 0.7,
                'leather': 0.7, 'meat': 0.8, 'logs': 1.4, 'grain': 0.9}, {
        'wood': mc('acacia_planks', 'acacia_slab') + [alt('baobab_planks', 'acacia_stairs')],
        'logs': mc('acacia_log') + [alt('baobab_log', 'acacia_wood')],
        'wool': mc('brown_wool', 'white_wool'),
        'leather': mc('leather'),
        'cloth': mc('yellow_carpet', 'brown_carpet'),
        'food': mc('cooked_beef', 'cooked_mutton', 'bread'),
        'meat': mc('beef', 'mutton'),
        'brick': mc('terracotta', 'orange_terracotta', 'yellow_terracotta')}),
    'karst': ({'wood': 0.7, 'stone': 0.75, 'food': 0.85, 'luxury': 0.8, 'metal': 1.25, 'glass': 1.1, 'clay': 0.95,
               'paper': 0.65, 'books': 0.8, 'specialty': 0.7, 'logs': 0.75, 'fish': 0.85}, {
        'wood': mc('bamboo_planks', 'bamboo_mosaic', 'bamboo_slab'),
        'logs': mc('bamboo_block'),
        'dressed_stone': [alt('limestone_bricks', 'stone_bricks'), alt('polished_limestone', 'smooth_stone')],
        'stone': [alt('limestone', 'calcite')] + mc('cobblestone'),
        'food': mc('cooked_cod', 'cooked_salmon', 'bread'),
        'paper': mc('paper'),
        'books': mc('book'),
        'specialty': mc('bamboo') + [alt('glowcap', 'glow_lichen')],
        'cloth': mc('string', 'green_carpet')}),
    'bayou': ({'food': 0.8, 'wood': 0.8, 'clay': 0.65, 'stone': 1.4, 'metal': 1.25, 'glass': 1.2, 'cloth': 1.05, 'luxury': 0.95,
               'fish': 0.75, 'produce': 0.85, 'dressed_stone': 1.5, 'specialty': 0.85, 'wax': 0.9}, {
        'wood': [alt('willow_planks', 'mangrove_planks')] + mc('mangrove_planks'),
        'logs': mc('mangrove_log') + [alt('willow_log', 'oak_log')],
        'clay': mc('clay', 'mud'),
        'brick': mc('mud_bricks', 'packed_mud'),
        'food': mc('cooked_cod', 'mushroom_stew') + [alt('cooked_crab', 'cooked_salmon')],
        'fish': mc('cod', 'salmon'),
        'specialty': [alt('spanish_moss', 'glow_lichen'), alt('cattail', 'blue_orchid')]}),
    'mesa': ({'clay': 0.65, 'metal': 0.85, 'stone': 0.85, 'glass': 0.9, 'luxury': 0.9, 'wood': 1.4, 'food': 1.25, 'cloth': 1.1,
              'brick': 0.6, 'ore': 0.75, 'sand': 0.75, 'dye': 0.85, 'logs': 1.5}, {
        'brick': mc('terracotta', 'red_terracotta', 'orange_terracotta', 'white_terracotta'),
        'metal': mc('gold_ingot', 'gold_nugget', 'iron_ingot'),
        'ore': mc('raw_gold', 'raw_iron'),
        'stone': mc('red_sandstone'),
        'dressed_stone': mc('cut_red_sandstone', 'smooth_red_sandstone'),
        'sand': mc('red_sand'),
        'food': mc('bread', 'cooked_rabbit', 'cooked_beef'),
        'dye': mc('red_dye', 'orange_dye', 'brown_dye')}),
}

# What each kind of shop deals in. A shop is a workplace whose keeper sells to folk and players from its own stock and
# till: '*' deals in everything (the market). Jobs give a trader's goods where the workplace says nothing.
SHOPS = {
    'workplaces': {
        'commerce/market_stall': ['*'],
        'commerce/market_hall': ['*'],
        'commerce/counting_house': ['*'],
        'commerce/caravan_yard': ['*'],
        'commerce/caravanserai': ['*'],
        'commerce/thieves_den': ['*'],
        'deep/market': ['*'],
        'house/shophouse': ['food', 'produce', 'cloth', 'candles', 'tools', 'luxury', 'books', 'dye'],
        'house/merchant_house': ['luxury', 'cloth', 'dye', 'salt', 'specialty', 'books'],
        'commerce/inn': ['food'],
        'commerce/tavern': ['food'],
        'commerce/hostel': ['food'],
        'work/bakery': ['food', 'flour'],
        'work/brewery': ['food', 'grain'],
        'work/butcher': ['meat', 'food', 'leather'],
        'work/fishery': ['fish', 'salt'],
        'work/tailor': ['cloth', 'wool', 'leather', 'dye'],
        'work/smithy': ['tools', 'metal'],
        'deep/forge': ['tools', 'metal', 'ore'],
        'work/carpentry': ['wood', 'logs'],
        'work/sawmill': ['wood', 'logs'],
        'work/logging_camp': ['logs', 'wood'],
        'work/masonry': ['dressed_stone', 'stone'],
        'work/quarry': ['stone', 'dressed_stone', 'sand'],
        'work/brick_kiln': ['brick', 'clay'],
        'work/glassworks': ['glass', 'sand'],
        'work/smelter': ['metal', 'ore'],
        'work/charcoal_kiln': ['fuel'],
        'work/tannery': ['leather'],
        'work/chandler': ['candles', 'wax'],
        'work/apiary': ['wax', 'produce'],
        'work/paper_mill': ['paper', 'books'],
        'work/dye_works': ['dye'],
        'work/salt_works': ['salt'],
        'work/windmill': ['flour', 'grain'],
        'work/watermill': ['flour', 'grain'],
        'work/hunters_lodge': ['meat', 'leather'],
        'work/alchemist_tower': ['luxury', 'specialty'],
        'civic/library': ['books', 'paper'],
        'civic/monastery': ['books', 'candles', 'wax', 'food'],
        'farm/orchard': ['produce'],
        'deep/glow_garden': ['specialty', 'produce'],
    },
    'jobs': {
        'merchant': ['*'],
        'trader': ['*'],
        'fence': ['*'],
        'baker': ['food', 'flour'],
        'brewer': ['food'],
        'innkeeper': ['food'],
        'butcher': ['meat', 'food'],
        'fisher': ['fish'],
        'tailor': ['cloth', 'dye'],
        'smith': ['tools', 'metal'],
        'carpenter': ['wood'],
        'mason': ['dressed_stone', 'stone'],
        'kiln_worker': ['brick'],
        'glassmaker': ['glass'],
        'smelter': ['metal'],
        'tanner': ['leather'],
        'chandler': ['candles', 'wax'],
        'beekeeper': ['wax', 'produce'],
        'papermaker': ['paper', 'books'],
        'dyer': ['dye'],
        'salter': ['salt'],
        'scholar': ['books', 'paper'],
    },
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def tag_entries(entries):
    out = []
    for e in entries:
        if isinstance(e, dict):
            out.append(e)
        elif e.startswith('expanse:'):
            out.append({'id': e, 'required': False})
        else:
            out.append(e)
    return out


def verify():
    """No item in two goods; every ware in the goods it is sold as; every shop names real goods."""
    owner = {}
    for g in GOODS:
        for e in TAGS[g]:
            for item in expand(e):
                if item in owner and owner[item] != g:
                    errors.append(f'{item} is in both {owner[item]} and {g}')
                owner[item] = g
    members = {g: set().union(*[expand(e) for e in TAGS[g]]) if TAGS[g] else set() for g in GOODS}

    def ware_ok(g, ware):
        for a in ware.split('|'):
            if a not in members[g]:
                errors.append(f'ware {a} is sold as {g} but is not in #hearth:goods/{g}')

    for g, wares in DEFAULT_WARES.items():
        for w in wares:
            ware_ok(g, w)
    for name, (_, wares) in CULTURES.items():
        for g, ws in wares.items():
            if g not in GOODS:
                errors.append(f'culture {name} sells unknown goods {g}')
                continue
            for w in ws:
                ware_ok(g, w)
    for kind in ('workplaces', 'jobs'):
        for k, gs in SHOPS[kind].items():
            for g in gs:
                if g != '*' and g not in GOODS:
                    errors.append(f'shop {k} deals in unknown goods {g}')
    for g in GOODS:
        if g not in UNITS or g not in DEFAULTS or g not in DEFAULT_WARES or g not in TAGS:
            errors.append(f'goods {g} is missing a unit, default, wares list or tag')


def main():
    verify()
    if errors:
        print('errors:', *sorted(set(errors)), sep='\n  ')
        sys.exit(1)
    for g in GOODS:
        write(os.path.join(DATA, 'tags', 'item', 'goods', g + '.json'), {'values': tag_entries(TAGS[g])})
    write(os.path.join(DATA, 'hearth_prices', 'goods.json'), GOODS_JSON)
    vanilla = {k: v for k, v in sorted(V.items()) if not k.startswith('expanse:')}
    expanse = {k: v for k, v in sorted(V.items()) if k.startswith('expanse:')}
    write(os.path.join(DATA, 'hearth_prices', 'vanilla.json'), {'values': vanilla})
    write(os.path.join(DATA, 'hearth_prices', 'expanse.json'), {'values': expanse})
    for name, (spec, wares) in CULTURES.items():
        full = {g: spec.get(g, spec.get(DERIVED.get(g, g), 1.0)) for g in GOODS}
        write(os.path.join(DATA, 'hearth_prices', 'cultures', name + '.json'), {'speciality': full, 'wares': wares})
    write(os.path.join(DATA, 'hearth_prices', 'shops.json'), SHOPS)
    print(f'{len(vanilla)} vanilla and {len(expanse)} Expanse values, {sum(len(t) for t in TAGS.values())} tag entries '
          f'for {len(GOODS)} goods, {len(CULTURES)} cultures, {len(SHOPS["workplaces"])} shop workplaces')


if __name__ == '__main__':
    main()
