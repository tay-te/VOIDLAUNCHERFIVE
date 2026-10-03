"""Assemble the six wood sets (+ azure wisteria leaves)."""
import doors
import leaves
import saplings
import wood

WOOD_NAMES = ['redwood', 'willow', 'palm', 'lumen', 'baobab', 'wisteria']


def generate(rng_for):
    out = []
    for w in WOOD_NAMES:
        p = wood.WOODS[w]
        out.append((f'textures/block/{w}_log.png', wood.BARK[w](rng_for(f'{w}_log'), p), True))
        out.append((f'textures/block/{w}_log_top.png', wood.log_top(rng_for(f'{w}_log_top'), w, p), False))
        out.append((f'textures/block/stripped_{w}_log.png', wood.stripped_side(rng_for(f'stripped_{w}_log'), w, p), True))
        out.append((f'textures/block/stripped_{w}_log_top.png',
                    wood.log_top(rng_for(f'stripped_{w}_log_top'), w, p, stripped=True), False))
        out.append((f'textures/block/{w}_planks.png', wood.planks(rng_for(f'{w}_planks'), w, p), True))
        out.append((f'textures/block/{w}_leaves.png', leaves.LEAVES[w](rng_for(f'{w}_leaves')), True))
        out.append((f'textures/block/{w}_sapling.png', saplings.sapling(w), False))
        top, bottom, trap, item = doors.make(w, p['planks'], rng_for)
        out.append((f'textures/block/{w}_door_top.png', top, False))
        out.append((f'textures/block/{w}_door_bottom.png', bottom, False))
        out.append((f'textures/block/{w}_trapdoor.png', trap, False))
        out.append((f'textures/item/{w}_door.png', item, False))
        if w == 'wisteria':
            # same blossom layout, periwinkle palette
            out.append(('textures/block/azure_wisteria_leaves.png',
                        leaves.leaves_wisteria(rng_for('wisteria_leaves'), 'azure_wisteria'), True))
    return out
