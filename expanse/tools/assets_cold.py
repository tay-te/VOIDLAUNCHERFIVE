"""Assets and data for the blocks of the cold and temperate biomes (registry/ColdBlocks.java): the maple, aspen,
beech and pine wood sets, the orange maple and larch leaves and the larch sapling, the bog's peat and sphagnum,
and the plants (bluebells, fireweed, cotton grass, cranberry bush, reindeer lichen).

tools/gen_assets.py calls build(wood_family, g, tags) from its main() and lang(lang) from its write_lang(), passing
its wood_family. Every block is cloned from the vanilla block it is shaped like, as the rest of gen_assets.py does it.

Two things 26.3 decides by tag that a new block has to opt into: the heightmaps that features place by
(MOTION_BLOCKING and friends count only #minecraft:blocks_motion_in_heightmap, so new solid ground must be in
#minecraft:blocks_motion_no_leaves or it is looked straight through), and which blocks flowing water washes away
(#minecraft:washed_away_by_fluids).
"""

NS = 'expanse'
WOODS = ['maple', 'aspen', 'beech', 'pine']
WOOD_PARTS = ['oak_log', 'oak_wood', 'stripped_oak_log', 'stripped_oak_wood', 'oak_planks', 'oak_stairs', 'oak_slab', 'oak_fence',
              'oak_fence_gate', 'oak_door', 'oak_trapdoor', 'oak_button', 'oak_pressure_plate', 'oak_sapling']
# Leaves that drop another tree's sapling (maple_leaves and the rest come with their wood set).
EXTRA_LEAVES = {'orange_maple_leaves': 'maple_sapling', 'larch_leaves': 'larch_sapling'}
PLANTS = ['cotton_grass', 'cranberry_bush', 'reindeer_lichen']


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
    for leaves, sapling in EXTRA_LEAVES.items():
        g.clone_block('cherry_leaves', leaves, {'cherry_leaves': leaves, 'cherry_sapling': sapling})
        tags.both('minecraft:leaves', f'{NS}:{leaves}')
    g.clone_block('oak_sapling', 'larch_sapling', {'oak_sapling': 'larch_sapling'})
    g.clone_block('potted_oak_sapling', 'potted_larch_sapling', {'potted_oak_sapling': 'potted_larch_sapling', 'oak_sapling': 'larch_sapling'},
                  item=False)
    tags.both('minecraft:saplings', f'{NS}:larch_sapling')
    tags.add('block', 'minecraft:flower_pots', f'{NS}:potted_larch_sapling')

    # ------------------------------------------------------------ the bog
    # Peat is soil (#dirt: saplings, grass and mushrooms take to it, a hoe tills it); sphagnum is moss.
    g.clone_block('dirt', 'peat', {'dirt': 'peat'})
    tags.both('minecraft:dirt', f'{NS}:peat')
    tags.add('block', 'minecraft:mineable/shovel', f'{NS}:peat')
    g.clone_block('moss_block', 'sphagnum_moss', {'moss_block': 'sphagnum_moss'})
    tags.both('minecraft:moss_blocks', f'{NS}:sphagnum_moss')
    tags.add('block', 'minecraft:blocks_motion_no_leaves', f'{NS}:sphagnum_moss')
    g.clone_block('moss_carpet', 'sphagnum_moss_carpet', {'moss_carpet': 'sphagnum_moss_carpet', 'moss_block': 'sphagnum_moss'})
    tags.add('block', 'minecraft:mineable/hoe', *ns('sphagnum_moss', 'sphagnum_moss_carpet'))
    tags.add('block', 'minecraft:combination_step_sound_blocks', f'{NS}:sphagnum_moss_carpet')
    tags.add('block', 'minecraft:frogs_spawnable_on', *ns('peat', 'sphagnum_moss'))

    # ------------------------------------------------------------ plants
    for plant in PLANTS:
        g.clone_block('poppy', plant, {'poppy': plant}, loot=plant != 'cranberry_bush')
    # The cranberry bush gives cranberries; shears or silk touch take the bush itself.
    g.write(f'data/{NS}/loot_table/blocks/cranberry_bush.json', {
        'type': 'minecraft:block',
        'pools': [{'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'condition': {'type': 'minecraft:any_of', 'terms': ['minecraft:tool/can_shear', 'minecraft:tool/can_silk_touch']},
             'name': f'{NS}:cranberry_bush'},
            {'type': 'minecraft:item', 'name': f'{NS}:cranberries',
             'modifier': [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'max': 3, 'min': 1}},
                          {'type': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:uniform_bonus_count',
                           'parameters': {'bonusMultiplier': 1}},
                          {'type': 'minecraft:explosion_decay'}]}]}],
                   'rolls': 1}],
        'random_sequence': f'{NS}:blocks/cranberry_bush'})
    g.flat_item('cranberries')
    tags.add('item', 'minecraft:fox_food', f'{NS}:cranberries')
    g.clone_block('wildflowers', 'bluebells', {'wildflowers': 'bluebells'})
    g.clone_block('lilac', 'fireweed', {'lilac': 'fireweed'})
    tags.both('minecraft:flowers', *ns('bluebells', 'fireweed'))
    tags.add('block', 'minecraft:bee_attractive', *ns('bluebells', 'fireweed'))
    tags.add('item', 'minecraft:bee_food', *ns('bluebells', 'fireweed'))
    tags.add('block', 'minecraft:inside_step_sound_blocks', f'{NS}:bluebells')
    tags.add('block', 'minecraft:replaceable_by_trees', *ns('fireweed', *PLANTS))
    tags.add('block', 'minecraft:replaceable_by_mushrooms', *ns('fireweed', *PLANTS))
    tags.add('block', 'minecraft:washed_away_by_fluids', *ns('bluebells', 'fireweed', 'sphagnum_moss_carpet', *PLANTS))
    # Dyes, as vanilla makes them from its own blue and magenta flowers.
    for flower, vanilla, dye in [('bluebells', 'cornflower', 'blue_dye'), ('fireweed', 'lilac', 'magenta_dye')]:
        recipe = f'{dye}_from_{vanilla}'
        g.clone(f'data/minecraft/recipe/{recipe}.json', f'data/{NS}/recipe/{dye}_from_{flower}.json', {vanilla: flower})
        adv = g.find_advancement(recipe)
        if adv:
            vpath, cat = adv
            g.clone(vpath, f'data/{NS}/advancement/recipes/{cat}/{dye}_from_{flower}.json', {vanilla: flower},
                    tweak=lambda a, r=recipe, f=flower, d=dye: _retarget(a, r, f'{NS}:{d}_from_{f}'))


def _retarget(adv, vanilla_recipe, ours):
    """A cloned recipe advancement still names vanilla's recipe; point it at ours."""
    import json
    return json.loads(json.dumps(adv).replace(f'"minecraft:{vanilla_recipe}"', f'"{ours}"'))


def lang(lang):
    for b, n in [('maple_highlands', 'Maple Highlands'), ('aspen_parkland', 'Aspen Parkland'), ('larch_taiga', 'Larch Taiga'),
                 ('boreal_muskeg', 'Boreal Muskeg'), ('bluebell_woods', 'Bluebell Woods'), ('pine_heath', 'Pine Heath')]:
        lang[f'biome.{NS}.{b}'] = n
    for w in WOODS:
        lang[f'tag.item.{NS}.{w}_logs'] = f'{w.capitalize()} Logs'
        lang[f'tag.block.{NS}.{w}_logs'] = f'{w.capitalize()} Logs'
