"""Talents (docs/BUILD_FEEL.md §13.2): the twenty-six talents as data and their lang lines.

    python talents_data.py      # rewrites data/mettle/mettle/talent and lang/en_us/talents_data.json

The table below is the source; the JSON is generated. A talent's effect is a trait effect: an attribute modifier
(value × scale) or a behaviour the code handles (CardHooks reads the slotted value as the trait mettle:talent/<id>).
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources'))
OUT = os.path.join(RES, 'data', 'mettle', 'mettle', 'talent')
LANG = os.path.join(RES, 'assets', 'mettle', 'lang', 'en_us', 'talents_data.json')

P = 'percent'
F = 'flat'
MB = 'add_multiplied_base'


def beh(name):
    return {'behaviour': 'mettle:talent/' + name}


def attr(attribute, operation='add_value', scale=1.0):
    return {'attribute': attribute, 'operation': operation, 'scale': scale}


# (id, family, name, description with %s, min, max, step, unit, fixed rarity or None, ceiling or None, effect)
TALENTS = [
    ('hardy', 'body', 'Hardy', '+%s ♥ max health.', 0.5, 1.5, 0.5, F, None, None, attr('minecraft:max_health', scale=2.0)),
    ('iron_gut', 'body', 'Iron Gut', 'You get hungry %s slower.', 5, 15, 1, P, None, None, beh('iron_gut')),
    ('deep_lungs', 'body', 'Deep Lungs', 'You hold your breath %s s longer.', 3, 8, 1, F, None, None, beh('deep_lungs')),
    ('quick_healer', 'body', 'Quick Healer', 'Natural regeneration is %s faster (it costs hunger as vanilla\'s does).', 10, 25, 1, P, None, None, beh('quick_healer')),
    ('weathered_hide', 'body', 'Weathered Hide', 'All damage you take is %s smaller.', 2, 4, 1, P, None, 'taken', beh('weathered_hide')),
    ('grit', 'body', 'Grit', 'Below 30 %% health, damage you take is %s smaller.', 3, 6, 1, P, None, 'taken', beh('grit')),
    ('cold_blood', 'body', 'Cold Blood', 'Freezing and powder snow hurt %s less and chill you %s slower.', 15, 30, 1, P, None, None, beh('cold_blood')),
    ('steady_nerves', 'body', 'Steady Nerves', 'Darkness, Blindness and Nausea on you last %s less.', 15, 30, 1, P, None, None, beh('steady_nerves')),
    ('fleet', 'motion', 'Fleet', '+%s movement speed.', 2, 4, 1, P, None, 'movement', attr('minecraft:movement_speed', MB, 0.01)),
    ('climber', 'motion', 'Climber', 'You climb ladders, vines and scaffolding %s faster.', 10, 20, 1, P, None, None, beh('climber')),
    ('sure_footed', 'motion', 'Sure-Footed', 'You fall %s more blocks before taking damage.', 0.5, 1.5, 0.5, F, None, None, attr('minecraft:safe_fall_distance')),
    ('strong_swimmer', 'motion', 'Strong Swimmer', 'You swim %s faster.', 5, 10, 1, P, None, None, attr('minecraft:water_movement_efficiency', scale=0.01)),
    ('light_step', 'motion', 'Light Step', 'Sneaking, you never set off pressure plates or tripwires.', 1, 1, 1, F, 'rare', None, beh('light_step')),
    ('broad_shoulders', 'hands', 'Broad Shoulders', '+%s kg carry capacity.', 2, 6, 1, F, None, None, attr('mettle:carry_capacity')),
    ('long_arms', 'hands', 'Long Arms', 'You reach %s blocks further for blocks (not for hits).', 0.25, 0.5, 0.05, F, None, None, attr('minecraft:block_interaction_range')),
    ('careful_hands', 'hands', 'Careful Hands', 'Tools, weapons and armour wear %s slower.', 5, 12, 1, P, None, None, beh('careful_hands')),
    ('quick_hands', 'hands', 'Quick Hands', 'You eat and drink %s faster.', 10, 25, 1, P, None, None, beh('quick_hands')),
    ('packrat', 'hands', 'Packrat', 'One more belt pocket (up to the six a belt can show).', 1, 1, 1, F, 'epic', None, beh('packrat')),
    ('quick_learner', 'mind', 'Quick Learner', 'Skill XP +%s in every skill.', 2, 5, 1, P, None, None, beh('quick_learner')),
    ('night_owl', 'mind', 'Night Owl', 'Between dusk and dawn, skill XP +%s.', 4, 8, 1, P, None, None, beh('night_owl')),
    ('night_eyes', 'mind', 'Night Eyes', 'The dark is %s less dark for you.', 10, 30, 1, P, None, None, beh('night_eyes')),
    ('light_sleeper', 'mind', 'Light Sleeper', 'You wake when a hostile comes within 10 blocks of your bed, and phantoms leave you be one night in two.', 1, 1, 1, F, 'rare', None, beh('light_sleeper')),
    ('homebody', 'mind', 'Homebody', 'Within 64 blocks of your bed or respawn point, you regenerate %s faster.', 10, 20, 1, P, None, None, beh('homebody')),
    ('lucky', 'fortune', 'Lucky', '+%s Luck (fishing, loot chests).', 0.5, 1.5, 0.5, F, None, None, attr('minecraft:luck')),
    ('scavenger', 'fortune', 'Scavenger', 'Mobs you kill drop one more of their loot %s of the time.', 2, 5, 1, P, None, 'extra_drop', beh('scavenger')),
    ('silver_tongue', 'fortune', 'Silver Tongue', "Your market edge at Hearth +%s; without Hearth, villagers' prices %s better.", 1, 3, 1, P, None, None, beh('silver_tongue')),
]

FAMILIES = {'body': 'Body', 'motion': 'Motion', 'hands': 'Hands', 'mind': 'Mind', 'fortune': 'Fortune'}


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    lang = {}
    for (tid, family, name, desc, lo, hi, step, unit, fixed, ceiling, effect) in TALENTS:
        data = {
            'family': family,
            'min': lo,
            'max': hi,
            'step': step,
            'unit': unit,
        }
        if fixed:
            data['fixed'] = fixed
        if ceiling:
            data['ceiling'] = ceiling
        data['effect'] = effect
        data['name'] = {'translate': 'mettle.talent.' + tid}
        data['description'] = {'translate': 'mettle.talent.' + tid + '.description'}
        with open(os.path.join(OUT, tid + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, indent='\t', ensure_ascii=False)
            f.write('\n')
        lang['mettle.talent.' + tid] = name
        lang['mettle.talent.' + tid + '.description'] = desc
    for f, n in FAMILIES.items():
        lang['mettle.talent.family.' + f] = n
    with open(LANG, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(dict(sorted(lang.items())), f, indent='\t', ensure_ascii=False)
        f.write('\n')
    print('wrote %d talents to %s and %d lang lines' % (len(TALENTS), OUT, len(lang)))


if __name__ == '__main__':
    main()
