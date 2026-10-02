#!/usr/bin/env python3
"""
Generates every blockstate, block/item model, item definition, loot table, recipe, recipe
advancement, tag and the English lang file for VOID Expanse's blocks.

Why it clones vanilla instead of writing JSON from scratch: 26.x changed several of these formats
(item definitions, recipe ingredients, block transformers), and the one copy of the format that is
guaranteed right for the game we ship against is the game's own. So each of our blocks names the
vanilla block it is "shaped like" (redwood planks are shaped like oak planks, limestone bricks like
tuff bricks), and the generator reads that block's files out of the client jar and renames the ids.

    python3 tools/gen_assets.py [--jar path/to/minecraft-client.jar]

The jar defaults to the one Loom caches after any Gradle build of this project.
"""
import argparse
import copy
import json
import os
import re
import shutil
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'expanse'
DEFAULT_JAR = os.path.expanduser('~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar')

WOODS = ['redwood', 'willow', 'palm', 'lumen', 'baobab', 'wisteria']

# Our block -> the vanilla block whose files are the template, and the id renames to apply to them.
# Every rename is a whole-id rename (after namespace), applied to ids, model paths and texture paths.


def wood_family(w):
    m = {}
    for part in ['log', 'wood', 'planks', 'stairs', 'slab', 'fence', 'fence_gate', 'door', 'trapdoor',
                 'button', 'pressure_plate', 'sapling']:
        m[f'oak_{part}'] = f'{w}_{part}'
    m['stripped_oak_log'] = f'stripped_{w}_log'
    m['stripped_oak_wood'] = f'stripped_{w}_wood'
    m['potted_oak_sapling'] = f'potted_{w}_sapling'
    m['oak_logs'] = f'{w}_logs'
    return m


LIMESTONE = {
    'tuff': 'limestone', 'tuff_stairs': 'limestone_stairs', 'tuff_slab': 'limestone_slab', 'tuff_wall': 'limestone_wall',
    'polished_tuff': 'polished_limestone', 'tuff_bricks': 'limestone_bricks', 'tuff_brick_stairs': 'limestone_brick_stairs',
    'tuff_brick_slab': 'limestone_brick_slab', 'tuff_brick_wall': 'limestone_brick_wall', 'chiseled_tuff': 'chiseled_limestone',
}
OPAL = {
    'sand': 'opal_sand', 'sandstone': 'opal_sandstone', 'sandstone_stairs': 'opal_sandstone_stairs',
    'sandstone_slab': 'opal_sandstone_slab', 'sandstone_wall': 'opal_sandstone_wall', 'smooth_sandstone': 'smooth_opal_sandstone',
    'cut_sandstone': 'cut_opal_sandstone', 'chiseled_sandstone': 'chiseled_opal_sandstone',
}


class Gen:
    def __init__(self, jar):
        self.zip = zipfile.ZipFile(jar)
        self.names = set(self.zip.namelist())
        self.written = []
        self.blocks = []        # every block id we generated assets for
        self.items = set()      # every item id that exists
        self.lang = {}

    # ---------------------------------------------------------------- io
    def vanilla(self, path):
        path = path if path.startswith(('assets/', 'data/')) else path
        if path not in self.names:
            return None
        return json.loads(self.zip.read(path))

    def write(self, rel, obj):
        out = os.path.join(RES, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, 'w') as f:
            json.dump(obj, f, indent=2)
            f.write('\n')
        self.written.append(rel)

    # ---------------------------------------------------------------- renaming
    @staticmethod
    def rename(obj, mapping):
        """Rewrites every string that is a vanilla id in `mapping` (optionally #tag, block/, item/ prefixed,
        or followed by a model suffix such as _inner / _top / _bottom_left_open) to our namespace."""
        keys = sorted(mapping, key=len, reverse=True)

        def fix(s):
            m = re.fullmatch(r'(#?)minecraft:((?:block/|item/)?)([a-z0-9_]+)', s)
            if not m:
                return s
            tag, prefix, name = m.groups()
            for k in keys:
                if name == k or (prefix and name.startswith(k + '_')):
                    return f'{tag}{NS}:{prefix}{mapping[k]}{name[len(k):]}'
            return s

        def walk(o):
            if isinstance(o, dict):
                return {k: walk(v) for k, v in o.items()}
            if isinstance(o, list):
                return [walk(v) for v in o]
            if isinstance(o, str):
                return fix(o)
            return o
        return walk(obj)

    def clone(self, vpath, opath, mapping, tweak=None):
        obj = self.vanilla(vpath)
        if obj is None:
            raise SystemExit(f'missing vanilla template {vpath}')
        obj = self.rename(obj, mapping)
        if tweak:
            obj = tweak(obj)
        self.write(opath, obj)
        return obj

    def clone_block(self, vname, ours, mapping, item=True, item_model=True, loot=True, vitem=None):
        """Blockstate + every model it references + item definition + item model + loot table."""
        mapping = dict(mapping)
        mapping.setdefault(vname, ours)
        bs = self.clone(f'assets/minecraft/blockstates/{vname}.json', f'assets/{NS}/blockstates/{ours}.json', mapping)
        for model in sorted(self.models_in(self.vanilla(f'assets/minecraft/blockstates/{vname}.json'))):
            self.clone_model(model, mapping)
        if item:
            vi = vitem or vname
            idef = self.clone(f'assets/minecraft/items/{vi}.json', f'assets/{NS}/items/{ours}.json', {**mapping, vi: ours})
            for model in self.models_in(self.vanilla(f'assets/minecraft/items/{vi}.json')):
                self.clone_model(model, {**mapping, vi: ours})
            self.items.add(ours)
        if loot:
            self.clone(f'data/minecraft/loot_table/blocks/{vname}.json', f'data/{NS}/loot_table/blocks/{ours}.json', mapping)
        self.blocks.append(ours)

    @staticmethod
    def models_in(obj):
        found = set()

        def walk(o):
            if isinstance(o, dict):
                for k, v in o.items():
                    if k == 'model' and isinstance(v, str):
                        found.add(v)
                    else:
                        walk(v)
            elif isinstance(o, list):
                for v in o:
                    walk(v)
        walk(obj)
        return found

    def clone_model(self, ref, mapping):
        """Clones a vanilla model only if renaming moves it into our namespace; shared parents stay vanilla."""
        renamed = self.rename(ref, mapping)
        if not renamed.startswith(NS + ':'):
            return
        vpath = 'assets/minecraft/models/' + ref.split(':', 1)[1] + '.json'
        opath = f'assets/{NS}/models/' + renamed.split(':', 1)[1] + '.json'
        obj = self.vanilla(vpath)
        if obj is None:
            raise SystemExit(f'missing vanilla model {vpath}')
        self.write(opath, self.rename(obj, mapping))

    # ---------------------------------------------------------------- simple hand-made assets
    def cube_all(self, name, texture=None, loot_self=True):
        texture = texture or name
        self.write(f'assets/{NS}/blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{name}'}}})
        self.write(f'assets/{NS}/models/block/{name}.json',
                   {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{texture}'}})
        self.write(f'assets/{NS}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{name}'}})
        if loot_self:
            self.loot_self(name)
        self.items.add(name)
        self.blocks.append(name)

    def loot_self(self, name):
        self.write(f'data/{NS}/loot_table/blocks/{name}.json', {
            'type': 'minecraft:block',
            'pools': [{'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
                       'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}], 'rolls': 1.0}],
            'random_sequence': f'{NS}:blocks/{name}'})

    def flat_item(self, name):
        self.write(f'assets/{NS}/models/item/{name}.json',
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{name}'}})
        self.write(f'assets/{NS}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{name}'}})
        self.items.add(name)

    # ---------------------------------------------------------------- recipes and advancements
    def clone_recipes(self, prefixes, mapping, require_all_known=True):
        """Every vanilla recipe whose file name maps into our family, kept only if every id it mentions
        exists here (so tuff's polished-stairs recipes do not make limestone recipes for blocks we lack)."""
        for path in sorted(self.names):
            m = re.fullmatch(r'data/minecraft/recipe/([a-z0-9_]+)\.json', path)
            if not m:
                continue
            name = m.group(1)
            ours = self.rename_name(name, mapping)
            if ours is None:
                continue
            obj = self.rename(json.loads(self.zip.read(path)), mapping)
            if require_all_known and not self.all_known(obj):
                continue
            if not self.mentions_ours(obj):
                continue
            self.write(f'data/{NS}/recipe/{ours}.json', obj)
            adv = self.find_advancement(name)
            if adv:
                vpath, cat = adv
                a = self.rename(json.loads(self.zip.read(vpath)), {**mapping, name: ours})
                a = json.loads(json.dumps(a).replace(f'"minecraft:{name}"', f'"{NS}:{ours}"'))
                if self.all_known(a):
                    self.write(f'data/{NS}/advancement/recipes/{cat}/{ours}.json', a)

    def find_advancement(self, recipe):
        for cat in ['building_blocks', 'decorations', 'redstone', 'transportation', 'misc', 'food', 'tools', 'combat', 'brewing']:
            p = f'data/minecraft/advancement/recipes/{cat}/{recipe}.json'
            if p in self.names:
                return p, cat
        return None

    @staticmethod
    def rename_name(name, mapping):
        """Maps a recipe file name such as tuff_brick_slab_from_tuff_stonecutting by its leading id."""
        for k in sorted(mapping, key=len, reverse=True):
            if name == k:
                return mapping[k]
            if name.startswith(k + '_from_'):
                rest = name[len(k) + len('_from_'):]
                for k2 in sorted(mapping, key=len, reverse=True):
                    if rest.startswith(k2 + '_'):
                        return f'{mapping[k]}_from_{mapping[k2]}{rest[len(k2):]}'
                return None
        return None

    def all_known(self, obj):
        s = json.dumps(obj)
        for ref in re.findall(r'"#?' + NS + r':([a-z0-9_/]+)"', s):
            if ref.startswith(('block/', 'item/', 'blocks/')) or ref.endswith('_logs'):
                continue
            if ref not in self.items and ref not in self.blocks:
                return False
        return True

    def mentions_ours(self, obj):
        return f'"{NS}:' in json.dumps(obj) or f'"#{NS}:' in json.dumps(obj)


# -------------------------------------------------------------------- tags

class Tags:
    def __init__(self):
        self.t = {}

    def add(self, registry, tag, *values):
        ns, path = tag.split(':')
        self.t.setdefault((ns, registry, path), [])
        for v in values:
            if v not in self.t[(ns, registry, path)]:
                self.t[(ns, registry, path)].append(v)

    def both(self, tag, *values):
        self.add('block', tag, *values)
        self.add('item', tag, *values)

    def write(self, gen):
        for (ns, registry, path), values in sorted(self.t.items()):
            gen.write(f'data/{ns}/tags/{registry}/{path}.json', {'replace': False, 'values': values})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--jar', default=DEFAULT_JAR)
    args = ap.parse_args()

    # Generated trees only — textures, worldgen and structures are written by their own tools.
    for d in ['blockstates', 'models/block', 'models/item', 'items']:
        shutil.rmtree(os.path.join(RES, 'assets', NS, d), ignore_errors=True)
    for d in ['loot_table/blocks', 'recipe', 'advancement/recipes']:
        shutil.rmtree(os.path.join(RES, 'data', NS, d), ignore_errors=True)
    for ns in ['minecraft', NS]:
        for sub in ['block', 'item']:
            shutil.rmtree(os.path.join(RES, 'data', ns, 'tags', sub), ignore_errors=True)

    g = Gen(args.jar)
    tags = Tags()

    # ------------------------------------------------------------ woods
    leaves_family = lambda w: {'cherry_leaves': f'{w}_leaves', 'cherry_sapling': f'{w}_sapling'}
    for w in WOODS:
        fam = wood_family(w)
        for v in ['oak_log', 'oak_wood', 'stripped_oak_log', 'stripped_oak_wood', 'oak_planks', 'oak_stairs', 'oak_slab',
                  'oak_fence', 'oak_fence_gate', 'oak_door', 'oak_trapdoor', 'oak_button', 'oak_pressure_plate', 'oak_sapling']:
            g.clone_block(v, fam[v], fam)
        g.clone_block('potted_oak_sapling', f'potted_{w}_sapling', fam, item=False)
        g.clone_block('cherry_leaves', f'{w}_leaves', leaves_family(w))
        tags.both(f'{NS}:{w}_logs', *[f'{NS}:{x}' for x in [f'{w}_log', f'{w}_wood', f'stripped_{w}_log', f'stripped_{w}_wood']])
        tags.both('minecraft:logs_that_burn', f'#{NS}:{w}_logs')
        tags.add('block', 'minecraft:overworld_natural_logs', f'{NS}:{w}_log')
        for t, part in [('planks', 'planks'), ('wooden_stairs', 'stairs'), ('wooden_slabs', 'slab'), ('wooden_fences', 'fence'),
                        ('fence_gates', 'fence_gate'), ('wooden_doors', 'door'), ('wooden_trapdoors', 'trapdoor'),
                        ('wooden_buttons', 'button'), ('wooden_pressure_plates', 'pressure_plate'), ('leaves', 'leaves'),
                        ('saplings', 'sapling')]:
            tags.both(f'minecraft:{t}', f'{NS}:{w}_{part}')
        tags.add('block', 'minecraft:flower_pots', f'{NS}:potted_{w}_sapling')
        g.clone_recipes(['oak_', 'stripped_oak_'], fam)
    g.clone_block('cherry_leaves', 'azure_wisteria_leaves', {'cherry_leaves': 'azure_wisteria_leaves', 'cherry_sapling': 'wisteria_sapling'})
    tags.both('minecraft:leaves', f'{NS}:azure_wisteria_leaves')
    # Wisteria is in bloom: bees work it like a flowering tree.
    tags.add('block', 'minecraft:bee_attractive', f'{NS}:wisteria_leaves', f'{NS}:azure_wisteria_leaves')

    # ------------------------------------------------------------ limestone
    for v, ours in LIMESTONE.items():
        if v == 'chiseled_tuff':
            g.cube_all(ours)
        else:
            g.clone_block(v, ours, LIMESTONE)
    g.cube_all('mossy_limestone')
    g.clone_recipes(list(LIMESTONE), LIMESTONE)
    lime = [f'{NS}:{x}' for x in list(LIMESTONE.values()) + ['mossy_limestone']]
    tags.add('block', 'minecraft:mineable/pickaxe', *lime)
    tags.add('block', 'minecraft:base_stone_overworld', f'{NS}:limestone')
    tags.both('minecraft:stairs', f'{NS}:limestone_stairs', f'{NS}:limestone_brick_stairs')
    tags.both('minecraft:slabs', f'{NS}:limestone_slab', f'{NS}:limestone_brick_slab')
    tags.both('minecraft:walls', f'{NS}:limestone_wall', f'{NS}:limestone_brick_wall')

    # ------------------------------------------------------------ opal sand
    for v, ours in OPAL.items():
        g.clone_block(v, ours, OPAL)
    g.clone_recipes(list(OPAL), OPAL)
    opal = [f'{NS}:{x}' for x in OPAL.values() if x != 'opal_sand']
    tags.add('block', 'minecraft:mineable/pickaxe', *opal)
    tags.add('block', 'minecraft:mineable/shovel', f'{NS}:opal_sand')
    tags.both('minecraft:sand', f'{NS}:opal_sand')
    tags.both('minecraft:smelts_to_glass', f'{NS}:opal_sand')
    for t in ['enderman_holdable', 'triggers_ambient_desert_sand_block_sounds', 'camel_sand_step_sound_blocks', 'rabbits_spawnable_on']:
        tags.add('block', f'minecraft:{t}', f'{NS}:opal_sand')
    tags.both('minecraft:stairs', f'{NS}:opal_sandstone_stairs')
    tags.both('minecraft:slabs', f'{NS}:opal_sandstone_slab')
    tags.both('minecraft:walls', f'{NS}:opal_sandstone_wall')

    # ------------------------------------------------------------ crystals
    g.clone_block('amethyst_block', 'prismite_block', {'amethyst_block': 'prismite_block'})
    g.clone_block('amethyst_cluster', 'prismite_cluster', {'amethyst_cluster': 'prismite_cluster'}, loot=False)
    g.loot_self('prismite_cluster')
    tags.add('block', 'minecraft:mineable/pickaxe', f'{NS}:prismite_block', f'{NS}:prismite_cluster')
    tags.add('block', 'minecraft:crystal_sound_blocks', f'{NS}:prismite_block')

    # ------------------------------------------------------------ plants
    for flower in ['heather', 'edelweiss', 'frostbloom', 'glowcap']:
        g.clone_block('poppy', flower, {'poppy': flower})
        g.clone_block('potted_poppy', f'potted_{flower}', {'potted_poppy': f'potted_{flower}', 'poppy': flower}, item=False)
        tags.both('minecraft:small_flowers', f'{NS}:{flower}')
        tags.add('block', 'minecraft:bee_attractive', f'{NS}:{flower}')
        tags.add('item', 'minecraft:bee_food', f'{NS}:{flower}')
        tags.add('block', 'minecraft:flower_pots', f'{NS}:potted_{flower}')
    g.clone_block('rose_bush', 'cattail', {'rose_bush': 'cattail'})
    tags.add('block', 'minecraft:replaceable_by_trees', f'{NS}:cattail')
    tags.add('block', 'minecraft:replaceable_by_mushrooms', f'{NS}:cattail')
    g.clone_block('pale_hanging_moss', 'spanish_moss', {'pale_hanging_moss': 'spanish_moss'})
    tags.add('block', 'minecraft:mineable/hoe', f'{NS}:spanish_moss')
    for blossom in ['wisteria_blossoms', 'azure_wisteria_blossoms']:
        g.clone_block('pale_hanging_moss', blossom, {'pale_hanging_moss': blossom})
        tags.add('block', 'minecraft:mineable/hoe', f'{NS}:{blossom}')
        tags.add('block', 'minecraft:bee_attractive', f'{NS}:{blossom}')

    # ------------------------------------------------------------ items
    for item in ['venison', 'cooked_venison', 'crab_meat', 'cooked_crab',
                 'elk_spawn_egg', 'mammoth_spawn_egg', 'capybara_spawn_egg', 'crab_spawn_egg']:
        g.flat_item(item)
    tags.add('item', 'minecraft:meat', f'{NS}:venison', f'{NS}:cooked_venison')
    for raw, cooked, xp in [('venison', 'cooked_venison', 0.35), ('crab_meat', 'cooked_crab', 0.35)]:
        for kind, rtype, time, suffix in [('smelting', 'minecraft:smelting', 200, ''),
                                          ('smoking', 'minecraft:smoking', 100, '_from_smoking'),
                                          ('campfire', 'minecraft:campfire_cooking', 600, '_from_campfire_cooking')]:
            g.write(f'data/{NS}/recipe/{cooked}{suffix}.json', {
                'type': rtype, 'category': 'food', 'cookingtime': time, 'experience': xp,
                'ingredient': f'{NS}:{raw}', 'result': {'id': f'{NS}:{cooked}'}})

    tags.write(g)
    write_lang(g)
    print(f'{len(g.written)} files, {len(g.blocks)} blocks')


def title(s):
    small = {'of', 'the'}
    return ' '.join(p if p in small else p.capitalize() for p in s.split('_'))


def write_lang(g):
    lang = {'itemGroup.expanse': 'VOID Expanse'}
    for b in g.blocks:
        name = title(b)
        if b.startswith('potted_'):
            name = 'Potted ' + title(b[len('potted_'):])
        lang[f'block.{NS}.{b}'] = name
    for i in g.items:
        if f'block.{NS}.{i}' not in lang:
            lang[f'item.{NS}.{i}'] = title(i)
    lang.update({
        f'block.{NS}.lumen_log': 'Lumenwood Log', f'block.{NS}.lumen_wood': 'Lumenwood',
        f'block.{NS}.stripped_lumen_log': 'Stripped Lumenwood Log', f'block.{NS}.stripped_lumen_wood': 'Stripped Lumenwood',
        f'block.{NS}.lumen_planks': 'Lumenwood Planks', f'block.{NS}.lumen_leaves': 'Lumen Leaves',
        f'block.{NS}.cattail': 'Cattail', f'block.{NS}.spanish_moss': 'Spanish Moss',
    })
    for e, n in [('elk', 'Elk'), ('mammoth', 'Woolly Mammoth'), ('capybara', 'Capybara'), ('crab', 'Shore Crab')]:
        lang[f'entity.{NS}.{e}'] = n
        lang[f'item.{NS}.{e}_spawn_egg'] = f'{n} Spawn Egg'
    for b, n in [('frostbloom_tundra', 'Frostbloom Tundra'), ('heather_moor', 'Heather Moor'), ('wisteria_vale', 'Wisteria Vale'),
                 ('redwood_giants', 'Redwood Giants'), ('lumen_grove', 'Lumen Grove'), ('willow_bayou', 'Willow Bayou'),
                 ('amber_steppe', 'Amber Steppe'), ('opal_dunes', 'Opal Dunes'), ('jade_karst', 'Jade Karst'),
                 ('cloud_forest', 'Cloud Forest'), ('verdant_peaks', 'Verdant Peaks'), ('prismatic_peaks', 'Prismatic Peaks'),
                 ('palm_coast', 'Palm Coast')]:
        lang[f'biome.{NS}.{b}'] = n
    # the realms underground (gen_worldgen.py build_realm_biomes)
    for b, n in [('realm_wilds', 'Glowcap Wilds'), ('realm_lush', 'Verdant Hollow'), ('realm_dripstone', 'Stalagmite Deeps'),
                 ('realm_crystal', 'Geode Vaults'), ('realm_ember', 'Ember Deeps'), ('realm_mere', 'Sunless Mere')]:
        lang[f'biome.{NS}.{b}'] = n
    lang['commands.expanse.atlas.done'] = 'Atlas written to %s'
    lang.update({
        'advancements.expanse.root.title': 'VOID Expanse',
        'advancements.expanse.root.description': 'Set foot in a land no one has mapped',
        'advancements.expanse.wanderer.title': 'Wanderer of the Expanse',
        'advancements.expanse.wanderer.description': 'Visit all thirteen Expanse biomes',
    })
    for w in WOODS:
        lang[f'tag.item.{NS}.{w}_logs'] = f'{title(w)} Logs'
        lang[f'tag.block.{NS}.{w}_logs'] = f'{title(w)} Logs'
    lang[f'tag.item.{NS}.lumen_logs'] = 'Lumenwood Logs'
    lang[f'tag.block.{NS}.lumen_logs'] = 'Lumenwood Logs'
    g.write(f'assets/{NS}/lang/en_us.json', dict(sorted(lang.items())))


if __name__ == '__main__':
    main()
