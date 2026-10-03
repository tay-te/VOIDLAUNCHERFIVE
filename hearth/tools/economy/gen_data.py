"""VOID Hearth economy data: goods tags, base prices and culture markets.

Run from anywhere:  python hearth/tools/economy/gen_data.py

Writes
  data/hearth/tags/item/goods/<goods>.json        which items count as which goods
  data/hearth/hearth_prices/goods.json            unit values, fallback values, default wares
  data/hearth/hearth_prices/vanilla.json          base values of vanilla items (bits)
  data/hearth/hearth_prices/expanse.json          base values of VOID Expanse items (ignored when Expanse is absent)
  data/hearth/hearth_prices/cultures/<c>.json     each culture's specialities and wares

Every vanilla id is checked against the 26.3 client jar and every Expanse id against expanse/'s item definitions, so a
typo fails here rather than silently in game.  Value scale: a loaf of bread is 1 bit, an iron ingot 6, a diamond 120.
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

GOODS = ['food', 'wood', 'stone', 'clay', 'glass', 'metal', 'cloth', 'fuel', 'tools', 'luxury']
COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple',
          'blue', 'brown', 'green', 'red', 'black']
EXPANSE_WOODS = ['aspen', 'baobab', 'beech', 'eucalyptus', 'kapok', 'lumen', 'maple', 'olive', 'palm', 'pine', 'redwood',
                 'teak', 'willow', 'wisteria']


def load_ids():
    with zipfile.ZipFile(CLIENT_JAR) as z:
        vanilla = {n[len('assets/minecraft/items/'):-5] for n in z.namelist()
                   if n.startswith('assets/minecraft/items/') and n.endswith('.json')}
    expanse = {f[:-5] for f in os.listdir(EXPANSE_ITEMS) if f.endswith('.json')} if os.path.isdir(EXPANSE_ITEMS) else set()
    return vanilla, expanse


VANILLA, EXPANSE = load_ids()
errors = []


def check(ident):
    """Validates 'ns:id' or '#ns:tag' (tags are trusted) or 'a|b' alternatives."""
    for alt in ident.split('|'):
        if alt.startswith('#'):
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


TAGS = {
    'food': ['#minecraft:meat', '#minecraft:fishes', '#minecraft:eggs'] + mc(
        'bread', 'apple', 'carrot', 'potato', 'baked_potato', 'beetroot', 'wheat', 'melon_slice', 'melon', 'pumpkin',
        'pumpkin_pie', 'cookie', 'cake', 'sweet_berries', 'glow_berries', 'dried_kelp', 'kelp', 'cooked_beef',
        'cooked_porkchop', 'cooked_chicken', 'cooked_mutton', 'cooked_rabbit', 'cooked_cod', 'cooked_salmon',
        'mushroom_stew', 'rabbit_stew', 'beetroot_soup', 'sugar', 'sugar_cane', 'cocoa_beans', 'milk_bucket',
        'red_mushroom', 'brown_mushroom', 'golden_carrot', 'golden_apple', 'hay_block', 'wheat_seeds', 'beetroot_seeds',
        'pumpkin_seeds', 'melon_seeds', 'chorus_fruit') + ex('venison', 'cooked_venison', 'crab_meat', 'cooked_crab',
                                                             'cranberries'),
    'wood': ['#minecraft:logs', '#minecraft:planks', '#minecraft:wooden_slabs', '#minecraft:wooden_stairs',
             '#minecraft:wooden_fences', '#minecraft:fence_gates', '#minecraft:wooden_doors', '#minecraft:wooden_trapdoors',
             '#minecraft:bamboo_blocks', '#minecraft:wooden_shelves', '#minecraft:boats', '#minecraft:signs'] + mc(
        'stick', 'bamboo', 'bamboo_mosaic', 'chest', 'barrel', 'crafting_table', 'ladder', 'bowl')
        + expanse_woods('log', 'wood', 'planks', 'slab', 'stairs', 'fence', 'fence_gate', 'door', 'trapdoor'),
    'stone': ['#minecraft:stone_bricks'] + mc(
        'cobblestone', 'stone', 'smooth_stone', 'mossy_cobblestone', 'andesite', 'diorite', 'granite',
        'polished_andesite', 'polished_diorite', 'polished_granite', 'deepslate', 'cobbled_deepslate',
        'polished_deepslate', 'deepslate_bricks', 'deepslate_tiles', 'tuff', 'tuff_bricks', 'polished_tuff', 'calcite',
        'sandstone', 'smooth_sandstone', 'cut_sandstone', 'chiseled_sandstone', 'red_sandstone',
        'smooth_red_sandstone', 'cut_red_sandstone', 'chiseled_red_sandstone', 'blackstone',
        'polished_blackstone_bricks', 'basalt', 'gravel', 'flint', 'cobblestone_slab', 'cobblestone_stairs',
        'cobblestone_wall', 'stone_slab', 'stone_stairs', 'stone_brick_slab', 'stone_brick_stairs', 'stone_brick_wall',
        'smooth_stone_slab', 'sandstone_slab', 'sandstone_stairs', 'sandstone_wall', 'dripstone_block', 'obsidian')
        + ex('limestone', 'limestone_bricks', 'polished_limestone', 'chiseled_limestone', 'mossy_limestone',
             'limestone_slab', 'limestone_stairs', 'limestone_wall', 'limestone_brick_slab', 'limestone_brick_stairs',
             'limestone_brick_wall', 'opal_sandstone', 'cut_opal_sandstone', 'smooth_opal_sandstone',
             'chiseled_opal_sandstone', 'opal_sandstone_slab', 'opal_sandstone_stairs', 'opal_sandstone_wall',
             'volcanic_ash'),
    'clay': ['#minecraft:terracotta'] + mc(
        'clay_ball', 'clay', 'brick', 'bricks', 'brick_slab', 'brick_stairs', 'brick_wall', 'mud', 'packed_mud',
        'mud_bricks', 'mud_brick_slab', 'mud_brick_stairs', 'mud_brick_wall', 'flower_pot', 'decorated_pot')
        + ex('red_earth'),
    'glass': ['#minecraft:sand'] + mc('glass', 'glass_pane', 'tinted_glass', 'glass_bottle')
        + [check(f'minecraft:{c}_stained_glass') for c in COLORS]
        + [check(f'minecraft:{c}_stained_glass_pane') for c in COLORS] + ex('opal_sand', 'coral_sand'),
    'metal': ['#minecraft:iron_ores', '#minecraft:copper_ores', '#minecraft:gold_ores', '#minecraft:chains',
              '#minecraft:lanterns', '#minecraft:rails', '#minecraft:anvil'] + mc(
        'iron_ingot', 'iron_nugget', 'raw_iron', 'iron_block', 'raw_iron_block', 'copper_ingot', 'raw_copper',
        'copper_block', 'raw_copper_block', 'copper_nugget', 'gold_ingot', 'gold_nugget', 'raw_gold', 'gold_block',
        'raw_gold_block', 'iron_bars', 'cauldron', 'bucket', 'minecart', 'hopper', 'netherite_ingot', 'netherite_scrap'),
    'cloth': ['#minecraft:wool', '#minecraft:wool_carpets', '#minecraft:beds', '#minecraft:banners', '#minecraft:cushions']
        + mc('leather', 'rabbit_hide', 'string', 'feather', 'item_frame', 'painting') + ex('glowworm_silk', 'cotton_grass'),
    'fuel': ['#minecraft:coal_ores'] + mc('coal', 'charcoal', 'coal_block', 'dried_kelp_block', 'blaze_rod') + ex('peat'),
    'tools': ['#minecraft:axes', '#minecraft:pickaxes', '#minecraft:shovels', '#minecraft:hoes', '#minecraft:swords',
              '#minecraft:spears', '#minecraft:arrows', '#minecraft:head_armor', '#minecraft:chest_armor',
              '#minecraft:leg_armor', '#minecraft:foot_armor'] + mc(
        'shears', 'flint_and_steel', 'fishing_rod', 'bow', 'crossbow', 'shield', 'brush', 'spyglass', 'compass', 'lead',
        'saddle', 'name_tag', 'trident'),
    'luxury': ['#minecraft:candles', '#minecraft:dyes', '#minecraft:glazed_terracotta', '#minecraft:small_flowers',
               '#minecraft:decorated_pot_sherds'] + mc(
        'book', 'paper', 'writable_book', 'ink_sac', 'glow_ink_sac', 'honey_bottle', 'honeycomb', 'diamond',
        'amethyst_shard', 'lapis_lazuli', 'quartz', 'redstone', 'glowstone_dust', 'bookshelf', 'lectern', 'map', 'clock',
        'ender_pearl', 'experience_bottle', 'music_disc_cat', 'music_disc_13')
        + ex('amber', 'insect_amber', 'prismite_cluster', 'lavender', 'heather', 'edelweiss', 'desert_marigold',
             'frostbloom', 'salt_block', 'moth_orchid', 'bluebells', 'fireweed'),
}

# ------------------------------------------------------------------ base values (bits)

V = {}


def val(value, *names, ns='minecraft'):
    for n in names:
        V[check(f'{ns}:{n}' if not n.startswith('#') else n)] = value


def tag(value, *tags):
    for t in tags:
        V['#' + t] = value


# food
val(1.0, 'bread'); val(0.6, 'apple'); val(0.3, 'carrot', 'potato', 'beetroot', 'wheat', 'sugar', 'red_mushroom', 'brown_mushroom')
val(0.6, 'baked_potato'); val(0.2, 'melon_slice', 'sweet_berries', 'sugar_cane'); val(1.8, 'melon'); val(0.8, 'pumpkin')
val(2.0, 'pumpkin_pie', 'milk_bucket'); val(0.4, 'cookie', 'glow_berries'); val(6.0, 'cake', 'golden_carrot')
val(0.15, 'dried_kelp'); val(0.05, 'kelp', 'wheat_seeds', 'beetroot_seeds', 'rotten_flesh'); val(0.1, 'pumpkin_seeds', 'melon_seeds')
val(1.0, 'beef', 'porkchop'); val(1.6, 'cooked_beef', 'cooked_porkchop'); val(0.7, 'chicken'); val(1.2, 'cooked_chicken')
val(0.8, 'mutton', 'rabbit', 'salmon', 'tropical_fish'); val(1.3, 'cooked_mutton', 'cooked_rabbit', 'cooked_salmon')
val(0.6, 'cod'); val(1.0, 'cooked_cod', 'pufferfish'); val(1.5, 'mushroom_stew', 'beetroot_soup'); val(3.0, 'rabbit_stew')
val(0.25, 'egg'); val(0.5, 'cocoa_beans', 'chorus_fruit'); val(30.0, 'golden_apple'); val(600.0, 'enchanted_golden_apple')
val(2.8, 'hay_block'); val(0.3, 'spider_eye')
val(1.0, 'venison', ns='expanse'); val(1.6, 'cooked_venison', ns='expanse'); val(0.8, 'crab_meat', ns='expanse')
val(1.4, 'cooked_crab', ns='expanse'); val(0.25, 'cranberries', ns='expanse')
# wood
tag(0.5, 'minecraft:logs', 'minecraft:bamboo_blocks'); tag(0.125, 'minecraft:planks'); tag(0.07, 'minecraft:wooden_slabs')
tag(0.2, 'minecraft:wooden_stairs', 'minecraft:wooden_fences'); tag(0.5, 'minecraft:fence_gates')
tag(0.3, 'minecraft:wooden_doors', 'minecraft:signs'); tag(0.4, 'minecraft:wooden_trapdoors'); tag(0.8, 'minecraft:wooden_shelves')
tag(0.7, 'minecraft:boats')
val(0.06, 'stick', 'bamboo'); val(0.125, 'bamboo_mosaic'); val(1.2, 'chest'); val(1.0, 'barrel'); val(0.6, 'crafting_table')
val(0.15, 'ladder'); val(0.05, 'bowl')
# stone
val(0.1, 'cobblestone', 'andesite', 'diorite', 'granite', 'cobbled_deepslate', 'tuff'); val(0.15, 'stone', 'deepslate', 'sandstone',
    'red_sandstone', 'blackstone', 'polished_andesite', 'polished_diorite', 'polished_granite')
val(0.2, 'smooth_stone', 'mossy_cobblestone', 'calcite', 'smooth_sandstone', 'cut_sandstone', 'smooth_red_sandstone',
    'cut_red_sandstone', 'dripstone_block'); val(0.18, 'polished_deepslate', 'polished_tuff')
tag(0.25, 'minecraft:stone_bricks'); val(0.25, 'deepslate_bricks', 'tuff_bricks'); val(0.3, 'deepslate_tiles', 'chiseled_sandstone',
    'chiseled_red_sandstone', 'polished_blackstone_bricks'); val(0.12, 'basalt', 'cobblestone_wall'); val(0.06, 'gravel')
val(0.2, 'flint'); val(0.05, 'cobblestone_slab'); val(0.08, 'stone_slab', 'smooth_stone_slab', 'sandstone_slab'); val(0.13, 'stone_brick_slab')
val(0.15, 'cobblestone_stairs', 'stone_stairs', 'sandstone_stairs', 'sandstone_wall'); val(0.3, 'stone_brick_stairs')
val(0.25, 'stone_brick_wall'); val(3.0, 'obsidian')
val(0.15, 'limestone', ns='expanse'); val(0.25, 'limestone_bricks', ns='expanse'); val(0.2, 'polished_limestone', 'mossy_limestone',
    'opal_sandstone', ns='expanse'); val(0.3, 'chiseled_limestone', ns='expanse')
val(0.08, 'limestone_slab', ns='expanse'); val(0.15, 'limestone_stairs', 'limestone_wall', ns='expanse')
val(0.13, 'limestone_brick_slab', ns='expanse'); val(0.3, 'limestone_brick_stairs', ns='expanse'); val(0.25, 'limestone_brick_wall', ns='expanse')
val(0.25, 'cut_opal_sandstone', 'smooth_opal_sandstone', ns='expanse'); val(0.35, 'chiseled_opal_sandstone', ns='expanse')
val(0.1, 'opal_sandstone_slab', ns='expanse'); val(0.2, 'opal_sandstone_stairs', 'opal_sandstone_wall', ns='expanse')
val(0.05, 'volcanic_ash', ns='expanse')
# clay
val(0.25, 'clay_ball'); val(1.0, 'clay'); val(0.4, 'brick'); val(1.6, 'bricks', 'brick_stairs', 'brick_wall'); val(0.8, 'brick_slab')
tag(0.5, 'minecraft:terracotta'); val(0.1, 'mud'); val(0.2, 'packed_mud'); val(0.3, 'mud_bricks', 'mud_brick_stairs', 'mud_brick_wall')
val(0.15, 'mud_brick_slab'); val(1.2, 'flower_pot'); val(2.0, 'decorated_pot'); val(0.08, 'red_earth', ns='expanse')
# glass
tag(0.08, 'minecraft:sand'); val(0.4, 'glass'); val(0.15, 'glass_pane', 'glass_bottle'); val(3.0, 'tinted_glass')
for c in COLORS:
    val(0.5, f'{c}_stained_glass'); val(0.2, f'{c}_stained_glass_pane')
val(0.1, 'opal_sand', 'coral_sand', ns='expanse')
# metal
val(6.0, 'iron_ingot'); val(0.6, 'iron_nugget'); val(4.5, 'raw_iron'); val(54.0, 'iron_block'); val(40.0, 'raw_iron_block')
val(1.5, 'copper_ingot'); val(1.1, 'raw_copper'); val(13.0, 'copper_block'); val(10.0, 'raw_copper_block'); val(0.15, 'copper_nugget')
val(12.0, 'gold_ingot'); val(1.2, 'gold_nugget'); val(9.0, 'raw_gold'); val(108.0, 'gold_block'); val(80.0, 'raw_gold_block')
tag(4.5, 'minecraft:iron_ores'); tag(1.1, 'minecraft:copper_ores'); tag(9.0, 'minecraft:gold_ores'); tag(7.0, 'minecraft:chains')
tag(4.0, 'minecraft:lanterns'); tag(2.5, 'minecraft:rails'); tag(190.0, 'minecraft:anvil')
val(2.2, 'iron_bars'); val(40.0, 'cauldron'); val(18.0, 'bucket'); val(30.0, 'minecart'); val(32.0, 'hopper'); val(10.0, 'powered_rail')
val(400.0, 'netherite_ingot'); val(90.0, 'netherite_scrap', 'ancient_debris')
# cloth
tag(1.0, 'minecraft:wool'); tag(0.7, 'minecraft:wool_carpets'); tag(4.0, 'minecraft:beds'); tag(6.0, 'minecraft:banners')
tag(1.5, 'minecraft:cushions'); val(2.0, 'leather'); val(0.5, 'rabbit_hide'); val(0.3, 'string'); val(0.25, 'feather')
val(2.5, 'item_frame'); val(1.5, 'painting'); val(2.0, 'glowworm_silk', ns='expanse'); val(0.3, 'cotton_grass', ns='expanse')
# fuel
val(1.0, 'coal'); val(0.8, 'charcoal'); val(9.0, 'coal_block'); tag(1.0, 'minecraft:coal_ores'); val(1.4, 'dried_kelp_block')
val(6.0, 'blaze_rod'); val(0.6, 'peat', ns='expanse')
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
# luxury
val(2.0, 'book', 'honey_bottle', 'lapis_lazuli'); val(0.3, 'paper'); val(3.0, 'writable_book', 'amethyst_shard'); tag(1.5, 'minecraft:candles')
tag(0.4, 'minecraft:dyes'); tag(1.5, 'minecraft:glazed_terracotta'); tag(0.3, 'minecraft:small_flowers'); tag(10.0, 'minecraft:decorated_pot_sherds')
val(0.4, 'ink_sac'); val(2.0, 'glow_ink_sac'); val(1.5, 'honeycomb', 'quartz', 'glowstone_dust'); val(120.0, 'diamond')
val(0.6, 'redstone'); val(7.0, 'bookshelf'); val(10.0, 'lectern'); val(4.0, 'map'); val(50.0, 'clock'); val(15.0, 'ender_pearl')
val(20.0, 'experience_bottle'); val(60.0, 'music_disc_cat', 'music_disc_13'); val(25.0, 'emerald')
val(4.0, 'amber', ns='expanse'); val(25.0, 'insect_amber', ns='expanse'); val(6.0, 'prismite_cluster', ns='expanse')
val(0.5, 'lavender', 'heather', 'desert_marigold', 'bluebells', 'fireweed', ns='expanse'); val(2.0, 'edelweiss', 'frostbloom', ns='expanse')
val(3.0, 'salt_block', ns='expanse'); val(1.0, 'moth_orchid', ns='expanse')

# ------------------------------------------------------------------ goods.json

GOODS_JSON = {
    'units': {'food': 1.0, 'wood': 0.125, 'stone': 0.1, 'clay': 0.4, 'glass': 0.4, 'metal': 6.0, 'cloth': 1.0, 'fuel': 1.0,
              'tools': 10.0, 'luxury': 2.0},
    'defaults': {'food': 0.8, 'wood': 0.3, 'stone': 0.15, 'clay': 0.5, 'glass': 0.5, 'metal': 4.0, 'cloth': 1.0, 'fuel': 1.0,
                 'tools': 15.0, 'luxury': 5.0},
    'untagged': 0.25,
    'rarity': {'common': 1.0, 'uncommon': 4.0, 'rare': 12.0, 'epic': 40.0},
    'wares': {
        'food': mc('bread', 'baked_potato', 'cooked_cod'),
        'wood': mc('oak_planks', 'oak_log', 'stick'),
        'stone': mc('cobblestone', 'stone_bricks', 'stone'),
        'clay': mc('bricks', 'terracotta', 'clay_ball'),
        'glass': mc('glass', 'glass_pane', 'sand'),
        'metal': mc('iron_ingot', 'copper_ingot', 'lantern'),
        'cloth': mc('white_wool', 'leather', 'string'),
        'fuel': mc('charcoal', 'coal'),
        'tools': mc('iron_pickaxe', 'iron_axe', 'iron_shovel', 'shears'),
        'luxury': mc('book', 'candle', 'paper'),
    },
}


def alt(expanse_id, vanilla_id):
    return check(f'expanse:{expanse_id}|minecraft:{vanilla_id}')


# speciality below 1: plentiful and cheap here; above 1: imported and dear
CULTURES = {
    'temperate': ({'wood': 0.9, 'food': 0.85, 'cloth': 0.9, 'glass': 1.1, 'metal': 1.1, 'luxury': 1.1}, {
        'wood': mc('oak_log', 'oak_planks') + [alt('beech_planks', 'birch_planks')],
        'food': mc('bread', 'baked_potato', 'apple', 'cooked_porkchop'),
        'stone': mc('cobblestone', 'stone_bricks', 'smooth_stone'),
        'clay': mc('bricks', 'terracotta'),
        'cloth': mc('white_wool', 'leather', 'white_carpet'),
        'luxury': mc('book', 'candle', 'honey_bottle')}),
    'wisteria': ({'wood': 0.8, 'luxury': 0.8, 'clay': 0.85, 'cloth': 0.9, 'metal': 1.2, 'stone': 1.1, 'fuel': 1.1}, {
        'wood': [alt('wisteria_planks', 'cherry_planks'), alt('wisteria_log', 'cherry_log')] + mc('cherry_planks'),
        'food': mc('bread', 'sweet_berries', 'cooked_salmon', 'cookie'),
        'clay': mc('terracotta', 'white_terracotta', 'bricks'),
        'cloth': mc('purple_wool', 'pink_wool', 'purple_carpet'),
        'luxury': [alt('wisteria_blossoms', 'pink_petals')] + mc('purple_dye', 'candle')}),
    'northern': ({'wood': 0.7, 'food': 0.95, 'fuel': 0.85, 'cloth': 0.9, 'glass': 1.25, 'luxury': 1.2, 'clay': 1.15}, {
        'wood': [alt('redwood_log', 'spruce_log'), alt('pine_planks', 'spruce_planks')] + mc('spruce_planks'),
        'food': [alt('cooked_venison', 'cooked_beef')] + mc('sweet_berries', 'bread', 'cooked_salmon'),
        'stone': mc('cobblestone', 'mossy_cobblestone', 'stone_bricks'),
        'cloth': mc('leather', 'brown_wool', 'rabbit_hide'),
        'tools': mc('iron_axe', 'bow', 'iron_shovel'),
        'fuel': mc('charcoal', 'coal')}),
    'alpine': ({'stone': 0.8, 'wood': 0.85, 'cloth': 0.85, 'glass': 1.2, 'luxury': 1.1, 'clay': 1.1}, {
        'wood': mc('spruce_planks', 'spruce_log', 'dark_oak_planks'),
        'stone': mc('stone_bricks', 'cobblestone', 'polished_andesite'),
        'food': mc('bread', 'milk_bucket', 'cooked_mutton', 'baked_potato'),
        'cloth': mc('white_wool', 'gray_wool', 'leather'),
        'luxury': [alt('edelweiss', 'lily_of_the_valley')] + mc('candle', 'book')}),
    'tundra': ({'food': 1.1, 'wood': 1.25, 'stone': 0.95, 'cloth': 0.75, 'fuel': 1.3, 'glass': 1.3, 'metal': 1.2, 'luxury': 1.3}, {
        'food': mc('cooked_cod', 'cooked_salmon', 'dried_kelp') + [alt('cooked_venison', 'cooked_mutton')],
        'cloth': mc('leather', 'white_wool', 'rabbit_hide'),
        'wood': mc('spruce_planks', 'spruce_log'),
        'stone': mc('cobblestone', 'stone_bricks'),
        'luxury': [alt('frostbloom', 'blue_orchid')] + mc('candle')}),
    'moorland': ({'stone': 0.75, 'cloth': 0.8, 'fuel': 0.8, 'wood': 1.3, 'luxury': 1.05, 'glass': 1.15}, {
        'stone': mc('cobblestone', 'mossy_cobblestone', 'stone_bricks', 'cobbled_deepslate'),
        'cloth': mc('white_wool', 'light_gray_wool', 'gray_carpet'),
        'fuel': [alt('peat', 'charcoal')] + mc('coal'),
        'food': mc('cooked_mutton', 'baked_potato', 'bread'),
        'luxury': [alt('heather', 'allium')] + mc('book')}),
    'maritime': ({'food': 0.8, 'stone': 0.9, 'glass': 0.95, 'wood': 1.1, 'luxury': 1.0}, {
        'food': mc('cooked_cod', 'cooked_salmon', 'dried_kelp') + [alt('cooked_crab', 'tropical_fish')],
        'cloth': mc('string', 'white_wool', 'leather'),
        'stone': mc('stone_bricks', 'cobblestone', 'smooth_stone'),
        'tools': mc('fishing_rod', 'iron_axe', 'shears'),
        'luxury': mc('book', 'candle', 'ink_sac')}),
    'tropical': ({'wood': 0.75, 'food': 0.8, 'luxury': 0.85, 'metal': 1.35, 'stone': 1.2, 'cloth': 1.15, 'fuel': 1.1, 'glass': 1.1}, {
        'wood': [alt('palm_planks', 'jungle_planks')] + mc('jungle_log', 'bamboo_block'),
        'food': mc('melon_slice', 'cocoa_beans', 'cooked_cod') + [alt('cooked_crab', 'cooked_salmon')],
        'luxury': mc('lime_dye', 'cyan_dye', 'book'),
        'cloth': mc('lime_wool', 'string')}),
    'desert': ({'stone': 0.8, 'glass': 0.7, 'clay': 0.85, 'luxury': 0.85, 'food': 1.3, 'wood': 1.4, 'cloth': 1.1, 'fuel': 1.2}, {
        'stone': mc('sandstone', 'cut_sandstone', 'smooth_sandstone') + [alt('opal_sandstone', 'red_sandstone')],
        'glass': mc('sand', 'glass', 'glass_pane'),
        'clay': mc('terracotta', 'orange_terracotta', 'mud_bricks'),
        'food': mc('bread', 'cooked_rabbit', 'melon_slice'),
        'luxury': mc('green_dye', 'red_dye', 'yellow_dye'),
        'cloth': mc('orange_wool', 'red_carpet', 'leather')}),
    'mediterranean': ({'stone': 0.85, 'clay': 0.75, 'food': 0.85, 'luxury': 0.9, 'wood': 1.15, 'metal': 1.1}, {
        'stone': mc('polished_diorite', 'calcite', 'stone_bricks', 'smooth_sandstone'),
        'clay': mc('terracotta', 'orange_terracotta', 'bricks', 'decorated_pot'),
        'wood': [alt('olive_planks', 'oak_planks'), alt('olive_log', 'oak_log')],
        'food': mc('bread', 'apple', 'cooked_cod', 'beetroot_soup'),
        'luxury': mc('honey_bottle', 'book', 'candle')}),
    'steppe': ({'cloth': 0.75, 'food': 0.9, 'clay': 0.9, 'wood': 1.35, 'stone': 1.1, 'metal': 1.1, 'glass': 1.3}, {
        'wood': mc('acacia_planks', 'acacia_log') + [alt('baobab_planks', 'acacia_slab')],
        'cloth': mc('leather', 'brown_wool', 'white_wool', 'yellow_carpet'),
        'food': mc('cooked_beef', 'cooked_mutton', 'bread'),
        'clay': mc('terracotta', 'orange_terracotta', 'yellow_terracotta')}),
    'karst': ({'wood': 0.7, 'stone': 0.75, 'food': 0.85, 'luxury': 0.8, 'metal': 1.25, 'glass': 1.1, 'clay': 0.95}, {
        'wood': mc('bamboo_block', 'bamboo_planks', 'bamboo', 'bamboo_mosaic'),
        'stone': [alt('limestone_bricks', 'stone_bricks'), alt('polished_limestone', 'smooth_stone'), alt('limestone', 'calcite')],
        'food': mc('cooked_cod', 'cooked_salmon', 'bread', 'glow_berries'),
        'luxury': mc('paper', 'book', 'green_dye'),
        'cloth': mc('string', 'green_carpet')}),
    'bayou': ({'food': 0.8, 'wood': 0.8, 'clay': 0.75, 'stone': 1.4, 'metal': 1.25, 'glass': 1.2, 'cloth': 1.05, 'luxury': 0.95}, {
        'wood': [alt('willow_planks', 'mangrove_planks')] + mc('mangrove_log', 'mangrove_planks'),
        'clay': mc('mud_bricks', 'packed_mud', 'mud'),
        'food': mc('cooked_cod', 'mushroom_stew') + [alt('cooked_crab', 'cooked_salmon')],
        'luxury': mc('candle', 'honey_bottle')}),
    'mesa': ({'clay': 0.65, 'metal': 0.85, 'stone': 0.85, 'glass': 0.9, 'luxury': 0.9, 'wood': 1.4, 'food': 1.25, 'cloth': 1.1}, {
        'clay': mc('terracotta', 'red_terracotta', 'orange_terracotta', 'white_terracotta'),
        'metal': mc('gold_ingot', 'gold_nugget', 'iron_ingot'),
        'stone': mc('red_sandstone', 'cut_red_sandstone', 'smooth_red_sandstone'),
        'food': mc('bread', 'cooked_rabbit', 'cooked_beef'),
        'luxury': mc('red_dye', 'orange_dye', 'brown_dye')}),
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def tag_entries(entries):
    out = []
    for e in entries:
        if e.startswith('expanse:'):
            out.append({'id': e, 'required': False})
        else:
            out.append(e)
    return out


def main():
    for g, entries in TAGS.items():
        write(os.path.join(DATA, 'tags', 'item', 'goods', g + '.json'), {'values': tag_entries(entries)})
    write(os.path.join(DATA, 'hearth_prices', 'goods.json'), GOODS_JSON)
    vanilla = {k: v for k, v in sorted(V.items()) if not k.startswith('expanse:')}
    expanse = {k: v for k, v in sorted(V.items()) if k.startswith('expanse:')}
    write(os.path.join(DATA, 'hearth_prices', 'vanilla.json'), {'values': vanilla})
    write(os.path.join(DATA, 'hearth_prices', 'expanse.json'), {'values': expanse})
    for name, (spec, wares) in CULTURES.items():
        full = {g: spec.get(g, 1.0) for g in GOODS}
        write(os.path.join(DATA, 'hearth_prices', 'cultures', name + '.json'), {'speciality': full, 'wares': wares})
    if errors:
        print('unknown item ids:', sorted(set(errors)))
        sys.exit(1)
    print(f'{len(vanilla)} vanilla and {len(expanse)} Expanse values, {sum(len(t) for t in TAGS.values())} tag entries, '
          f'{len(CULTURES)} cultures')


if __name__ == '__main__':
    main()
