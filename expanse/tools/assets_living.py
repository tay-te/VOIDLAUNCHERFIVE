"""
The wild creatures' items and names (entity/life, world/LivingWorld.java), for tools/gen_assets.py: their
spawn eggs' item models, and the English names of the creatures, their eggs and their sounds' subtitles.
Their models, skins, egg sprites and sounds come from tools/gen_living.py.
"""
NS = 'expanse'

CREATURES = [('butterfly', 'Butterfly'), ('dragonfly', 'Dragonfly'), ('songbird', 'Songbird'), ('heron', 'Heron'),
             ('owl', 'Owl'), ('deer', 'Deer')]

SUBTITLES = {
    'entity.songbird.song': 'Songbird sings',
    'entity.songbird.call': 'Songbird calls in alarm',
    'entity.owl.hoot': 'Owl hoots',
    'entity.heron.croak': 'Heron croaks',
    'entity.bird.wings': 'Wings flap',
}


def build(g):
    for e, _ in CREATURES:
        g.flat_item(f'{e}_spawn_egg')


def lang(lang):
    for e, n in CREATURES:
        lang[f'entity.{NS}.{e}'] = n
        lang[f'item.{NS}.{e}_spawn_egg'] = f'{n} Spawn Egg'
    for event, text in SUBTITLES.items():
        lang[f'subtitles.{NS}.{event}'] = text
