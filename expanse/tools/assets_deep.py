"""
The world underground's blocks (registry/DeepBlocks.java), for tools/gen_assets.py: icicles, amber, amber
spikes, deeproot, glowroots and glowworm silk; and the English names of the underground's biomes
(tools/worldgen_deep.py). Textures come from tools/textures/deep.py.
"""
import worldgen_deep

NS = 'expanse'


def build(g, tags):
    # speleothems, shaped like pointed dripstone (the shared parent model comes along, renamed)
    for ours in ['icicle', 'amber_spike']:
        mapping = {'pointed_dripstone': ours}
        g.clone_block('pointed_dripstone', ours, mapping)
        g.clone_model('minecraft:block/pointed_dripstone', mapping)
    g.cube_all('amber')
    g.cube_all('insect_amber')
    g.clone_block('oak_log', 'deeproot', {'oak_log': 'deeproot'})
    g.clone_block('hanging_roots', 'glowroots', {'hanging_roots': 'glowroots'})
    g.clone_block('pale_hanging_moss', 'glowworm_silk', {'pale_hanging_moss': 'glowworm_silk'})

    tags.add('block', 'minecraft:mineable/pickaxe', *[f'{NS}:{b}' for b in ['icicle', 'amber', 'insect_amber', 'amber_spike']])
    tags.add('block', 'minecraft:mineable/axe', f'{NS}:deeproot')
    tags.add('block', 'minecraft:mineable/hoe', f'{NS}:glowworm_silk')
    tags.both('minecraft:logs_that_burn', f'{NS}:deeproot')
    # 26.3 heightmaps (and rain, snow and spawning) only see what blocks motion: the speleothems go with pointed
    # dripstone, the amber with the other solid blocks; the hanging plants wash away like hanging roots
    tags.add('block', 'minecraft:speleothems', f'{NS}:icicle', f'{NS}:amber_spike')  # SpeleothemBlock's own logic reads it
    tags.add('block', 'minecraft:blocks_motion_no_leaves', f'{NS}:amber', f'{NS}:insect_amber')
    tags.add('block', 'minecraft:washed_away_by_fluids', f'{NS}:glowroots', f'{NS}:glowworm_silk')


def lang(lang):
    lang.update({
        f'block.{NS}.icicle': 'Icicle',
        f'block.{NS}.amber': 'Amber',
        f'block.{NS}.insect_amber': 'Amber with Insect',
        f'block.{NS}.amber_spike': 'Amber Spike',
        f'block.{NS}.deeproot': 'Deeproot',
        f'block.{NS}.glowroots': 'Glowroots',
        f'block.{NS}.glowworm_silk': 'Glowworm Silk',
    })
    worldgen_deep.lang(lang, NS)
