"""Assets and data for the blocks of the warm and dry biomes (registry/WarmBlocks.java): the olive, eucalyptus, teak
and kapok wood sets and the flame tree's leaves, red earth and coral sand, and the plants (lavender, Sturt's desert
pea, moth orchid, desert marigold, spinifex, saguaro, ocotillo).

tools/gen_assets.py calls build(wood_family, g, tags) from its main() and lang(lang) from its write_lang(). Every
block is cloned from the vanilla block it is shaped like, as the rest of gen_assets.py does it; the saguaro and
the ocotillo have no vanilla twin and get models of their own.

26.3 decides several things by tag that a new block has to opt into: the heightmaps features place by count only
#minecraft:blocks_motion (so the saguaro, solid like a cactus, is in #blocks_motion_no_leaves; the soils and sand
come in through #dirt and #sand), and which blocks flowing water washes away (#minecraft:washed_away_by_fluids).
"""
import json

NS = 'expanse'
WOODS = ['olive', 'eucalyptus', 'teak', 'kapok']
WOOD_PARTS = ['oak_log', 'oak_wood', 'stripped_oak_log', 'stripped_oak_wood', 'oak_planks', 'oak_stairs', 'oak_slab', 'oak_fence',
              'oak_fence_gate', 'oak_door', 'oak_trapdoor', 'oak_button', 'oak_pressure_plate', 'oak_sapling']
FLOWERS = ['lavender', 'desert_pea', 'moth_orchid', 'desert_marigold']
# flower -> the dye it crafts into, as vanilla's single-flower dyes do
DYES = {'lavender': 'purple_dye', 'desert_pea': 'red_dye', 'moth_orchid': 'pink_dye', 'desert_marigold': 'yellow_dye'}


def ns(*names):
    return [f'{NS}:{n}' for n in names]


def build(wood_family, g, tags):
    # ------------------------------------------------------------ woods, as gen_assets.py makes its own
    for w in WOODS:
        fam = wood_family(w)
        for v in WOOD_PARTS:
            g.clone_block(v, fam[v], fam)
        g.clone_block('potted_oak_sapling', f'potted_{w}_sapling', fam, item=False)
        g.clone_block('cherry_leaves', f'{w}_leaves', {'cherry_leaves': f'{w}_leaves', 'cherry_sapling': f'{w}_sapling'})
        tags.both(f'{NS}:{w}_logs', *ns(f'{w}_log', f'{w}_wood', f'stripped_{w}_log', f'stripped_{w}_wood'))
        tags.both('minecraft:logs_that_burn', f'#{NS}:{w}_logs')
        tags.add('block', 'minecraft:overworld_natural_logs', f'{NS}:{w}_log')
        for t, part in [('planks', 'planks'), ('wooden_stairs', 'stairs'), ('wooden_slabs', 'slab'), ('wooden_fences', 'fence'),
                        ('fence_gates', 'fence_gate'), ('wooden_doors', 'door'), ('wooden_trapdoors', 'trapdoor'),
                        ('wooden_buttons', 'button'), ('wooden_pressure_plates', 'pressure_plate'), ('leaves', 'leaves'),
                        ('saplings', 'sapling')]:
            tags.both(f'minecraft:{t}', f'{NS}:{w}_{part}')
        tags.add('block', 'minecraft:flower_pots', f'{NS}:potted_{w}_sapling')
        g.clone_recipes(['oak_', 'stripped_oak_'], fam)
    # The flame tree is grown from a teak sapling (one in four), and its leaves give teak saplings back.
    g.clone_block('cherry_leaves', 'flame_tree_leaves', {'cherry_leaves': 'flame_tree_leaves', 'cherry_sapling': 'teak_sapling'})
    tags.both('minecraft:leaves', f'{NS}:flame_tree_leaves')
    # Olive, teak and the flame tree flower: bees work them.
    tags.add('block', 'minecraft:bee_attractive', *ns('olive_leaves', 'flame_tree_leaves'))

    # ------------------------------------------------------------ ground
    # Red earth is soil (#dirt: grass, saplings, flowers and dry plants take to it); coral sand is sand.
    g.clone_block('coarse_dirt', 'red_earth', {'coarse_dirt': 'red_earth'})
    tags.both('minecraft:dirt', f'{NS}:red_earth')
    tags.add('block', 'minecraft:mineable/shovel', f'{NS}:red_earth')
    tags.add('block', 'minecraft:armadillo_spawnable_on', f'{NS}:red_earth')
    tags.add('block', 'minecraft:rabbits_spawnable_on', f'{NS}:red_earth')
    tags.add('block', 'minecraft:triggers_ambient_desert_dry_vegetation_block_sounds', f'{NS}:red_earth')
    g.clone_block('sand', 'coral_sand', {'sand': 'coral_sand'})
    tags.both('minecraft:sand', f'{NS}:coral_sand')
    tags.both('minecraft:smelts_to_glass', f'{NS}:coral_sand')
    tags.add('block', 'minecraft:mineable/shovel', f'{NS}:coral_sand')
    for t in ['enderman_holdable', 'camel_sand_step_sound_blocks', 'rabbits_spawnable_on']:
        tags.add('block', f'minecraft:{t}', f'{NS}:coral_sand')

    # ------------------------------------------------------------ flowers
    for flower in FLOWERS:
        g.clone_block('poppy', flower, {'poppy': flower})
        g.clone_block('potted_poppy', f'potted_{flower}', {'potted_poppy': f'potted_{flower}', 'poppy': flower}, item=False)
        tags.both('minecraft:small_flowers', f'{NS}:{flower}')
        tags.add('block', 'minecraft:bee_attractive', f'{NS}:{flower}')
        tags.add('item', 'minecraft:bee_food', f'{NS}:{flower}')
        tags.add('block', 'minecraft:flower_pots', f'{NS}:potted_{flower}')
        tags.add('block', 'minecraft:washed_away_by_fluids', f'{NS}:{flower}')
        _dye_recipe(g, flower, DYES[flower])

    # ------------------------------------------------------------ spinifex: a dry grass, taken with shears
    g.clone_block('short_dry_grass', 'spinifex', {'short_dry_grass': 'spinifex'})
    for t in ['replaceable', 'replaceable_by_trees', 'replaceable_by_mushrooms', 'washed_away_by_fluids']:
        tags.add('block', f'minecraft:{t}', f'{NS}:spinifex')

    # ------------------------------------------------------------ saguaro and ocotillo
    _saguaro(g)
    tags.add('block', 'minecraft:blocks_motion_no_leaves', f'{NS}:saguaro')
    tags.add('block', 'minecraft:support_override_cactus_flower', f'{NS}:saguaro')
    tags.add('block', 'minecraft:enderman_holdable', f'{NS}:saguaro')
    _ocotillo(g)


def _dye_recipe(g, flower, dye):
    recipe = f'{dye}_from_{flower}'
    g.write(f'data/{NS}/recipe/{recipe}.json', {
        'type': 'minecraft:crafting_shapeless', 'group': dye, 'ingredients': [f'{NS}:{flower}'], 'result': {'id': f'minecraft:{dye}'}})
    g.write(f'data/{NS}/advancement/recipes/misc/{recipe}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {f'has_{flower}': {'conditions': {'items': [{'items': f'{NS}:{flower}'}]}, 'trigger': 'minecraft:inventory_changed'},
                     'has_the_recipe': {'conditions': {'recipes': f'{NS}:{recipe}'}, 'trigger': 'minecraft:recipe_unlocked'}},
        'requirements': [['has_the_recipe', f'has_{flower}']],
        'rewards': {'recipes': [f'{NS}:{recipe}']}})


def _face(texture, uv, rotation=0):
    f = {'uv': uv, 'texture': texture}
    if rotation:
        f['rotation'] = rotation
    return f


def _saguaro(g):
    """A pipe of a cactus: a 12-wide core, an extension towards each joined neighbour, and a rounded cap on any
    column with nothing above it. The ribs run along every piece (the arm's side faces are turned a quarter)."""
    S, T = '#side', '#top'
    tex = {'side': f'{NS}:block/saguaro_side', 'top': f'{NS}:block/saguaro_top', 'particle': f'{NS}:block/saguaro_side'}
    model = lambda name, elements, parent=None: g.write(f'assets/{NS}/models/block/{name}.json',
                                                         dict(({'parent': parent} if parent else {}), textures=tex, elements=elements))
    side4 = lambda uv: {d: _face(S, uv) for d in ('north', 'south', 'east', 'west')}
    model('saguaro_core', [{'from': [2, 2, 2], 'to': [14, 14, 14],
                            'faces': {**side4([2, 2, 14, 14]), 'up': _face(T, [2, 2, 14, 14]), 'down': _face(T, [2, 2, 14, 14])}}])
    model('saguaro_up', [{'from': [2, 14, 2], 'to': [14, 16, 14], 'faces': side4([2, 0, 14, 2])}])
    model('saguaro_down', [{'from': [2, 0, 2], 'to': [14, 2, 14], 'faces': side4([2, 14, 14, 16])}])
    model('saguaro_cap', [{'from': [3, 14, 3], 'to': [13, 16, 13],
                           'faces': {**side4([3, 0, 13, 2]), 'up': _face(T, [3, 3, 13, 13])}}])
    # the arm towards the north; the others are this model turned
    model('saguaro_arm', [{'from': [2, 2, 0], 'to': [14, 14, 2],
                           'faces': {'up': _face(S, [2, 0, 14, 2]), 'down': _face(S, [2, 14, 14, 16]),
                                     'east': _face(S, [2, 0, 14, 2], 90), 'west': _face(S, [2, 0, 14, 2], 90)}}])
    model('saguaro_inventory', [{'from': [2, 0, 2], 'to': [14, 16, 14],
                                 'faces': {**side4([2, 0, 14, 16]), 'up': _face(T, [2, 2, 14, 14]), 'down': _face(T, [2, 2, 14, 14])}}],
          parent='minecraft:block/block')
    m = lambda name, **kw: dict({'model': f'{NS}:block/{name}'}, **kw)
    g.write(f'assets/{NS}/blockstates/saguaro.json', {'multipart': [
        {'apply': m('saguaro_core')},
        {'when': {'up': 'true'}, 'apply': m('saguaro_up')},
        {'when': {'up': 'false'}, 'apply': m('saguaro_cap')},
        {'when': {'down': 'true'}, 'apply': m('saguaro_down')},
        {'when': {'north': 'true'}, 'apply': m('saguaro_arm')},
        {'when': {'east': 'true'}, 'apply': m('saguaro_arm', y=90)},
        {'when': {'south': 'true'}, 'apply': m('saguaro_arm', y=180)},
        {'when': {'west': 'true'}, 'apply': m('saguaro_arm', y=270)}]})
    g.write(f'assets/{NS}/items/saguaro.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/saguaro_inventory'}})
    g.loot_self('saguaro')
    g.items.add('saguaro')
    g.blocks.append('saguaro')


def _ocotillo(g):
    """Thin whips in a cross, the flowering tip on whichever block has nothing above it."""
    for name, texture in [('ocotillo', 'ocotillo'), ('ocotillo_tip', 'ocotillo_top')]:
        g.write(f'assets/{NS}/models/block/{name}.json', {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/{texture}'}})
    g.write(f'assets/{NS}/blockstates/ocotillo.json', {'variants': {
        'tip=false': {'model': f'{NS}:block/ocotillo'}, 'tip=true': {'model': f'{NS}:block/ocotillo_tip'}}})
    g.write(f'assets/{NS}/models/item/ocotillo.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:block/ocotillo_top'}})
    g.write(f'assets/{NS}/items/ocotillo.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/ocotillo'}})
    g.loot_self('ocotillo')
    g.items.add('ocotillo')
    g.blocks.append('ocotillo')


def lang(lang):
    for b, n in [('olive_groves', 'Olive Groves'), ('monsoon_forest', 'Monsoon Forest'), ('ghost_gum_outback', 'Ghost Gum Outback'),
                 ('saguaro_flats', 'Saguaro Flats'), ('kapok_rainforest', 'Kapok Rainforest'), ('coral_coast', 'Coral Coast')]:
        lang[f'biome.{NS}.{b}'] = n
    for w in WOODS:
        lang[f'tag.item.{NS}.{w}_logs'] = f'{w.capitalize()} Logs'
        lang[f'tag.block.{NS}.{w}_logs'] = f'{w.capitalize()} Logs'
    lang.update({
        f'block.{NS}.desert_pea': "Sturt's Desert Pea",
        f'block.{NS}.potted_desert_pea': "Potted Sturt's Desert Pea",
        f'block.{NS}.flame_tree_leaves': 'Flame Tree Leaves',
    })
