"""The Speech tree (docs/BUILD_FEEL.md §14.2) as data: perk/speech/*.json and lang en_us/speech.json.

    python speech_tree.py

The tables below are the source; the JSON is generated (the traits, card skill and bynames are in cards_data.py).
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources'))
PERKS = os.path.join(RES, 'data', 'mettle', 'mettle', 'perk', 'speech')
LANG = os.path.join(RES, 'assets', 'mettle', 'lang', 'en_us', 'speech.json')
# The cursed cards' lines live with the others in draft.json (as Stealth's do).
DRAFT_LANG = os.path.join(RES, 'assets', 'mettle', 'lang', 'en_us', 'draft.json')

# (id, kind, extra fields, name, description, (hint|downside, text) or None)
TREE = [
    ('haggler', 'root', {'tier': 1, 'ranks': 3, 'cost': 1, 'rank_values': ['2', '4', '6']}, 'Haggler',
     "Your market edge at Hearth is %s%% better, and villagers' trades of 4 or more emeralds cost 1 / 2 / 3 less.", None),
    ('glib', 'root', {'tier': 1, 'ranks': 3, 'cost': 1, 'rank_values': ['1', '2', '3']}, 'Glib',
     "Your Speech counts %s higher wherever Hearth reads it (checks, haggling room, patience and the limit band); without Hearth, "
     "villagers' gossip about you grows 25 / 50 / 75%% faster.", None),
    # Path A: Trader (coin)
    ('provisioner', 'path', {'tier': 2, 'path': 'a', 'cost': 2}, 'Provisioner',
     'Food, drink and potions: you buy 10%% cheaper and sell 10%% dearer at Hearth markets.', None),
    ('appraiser', 'path', {'tier': 2, 'path': 'a', 'cost': 2}, 'Appraiser',
     "Item tooltips show what this settlement's market pays and asks for them; away from a market, what the nearest villager pays.", None),
    ('ledger_keeper', 'path', {'tier': 3, 'path': 'a', 'cost': 3}, 'Ledger-Keeper',
     'Speech XP from business never tapers, and coins you carry weigh nothing.', None),
    ('shrewd_eye', 'path', {'tier': 3, 'path': 'a', 'cost': 3}, 'Shrewd Eye',
     "You read a Hearth merchant's limit band as if your Speech were 5 higher (until Hearth reads it: your market edge +2%%).", None),
    # Path B: Courtier (people)
    ('charmer', 'path', {'tier': 2, 'path': 'b', 'cost': 2}, 'Charmer',
     'Reputation you gain with Hearth folk +25%% (until Hearth reads it: your Presence counts 1 higher there).', None),
    ('barterer', 'path', {'tier': 2, 'path': 'b', 'cost': 2}, 'Barterer',
     'Piglins give one more item a quarter of the time, and a wandering trader restocks once more for you.', None),
    ('commanding_voice', 'path', {'tier': 3, 'path': 'b', 'cost': 3}, 'Commanding Voice',
     'Your Presence counts 2 higher wherever Hearth reads it, and allies within 8 blocks gain 5%% more skill XP.', None),
    ('second_word', 'path', {'tier': 3, 'path': 'b', 'cost': 3}, 'Second Word',
     'Once a sim day, a failed Hearth speech check may be tried again at once (until Hearth reads it: your Speech counts 1 higher).', None),
    # Masteries
    ('merchant_prince', 'mastery', {'tier': 4, 'cost': 5, 'side': 'a'}, 'Merchant Prince',
     'Your market edge at Hearth +5%%, and a merchant you walked away from is never sour with you (until Hearth reads it: 2%% more edge instead).',
     None),
    ('heros_welcome', 'mastery', {'tier': 4, 'cost': 5, 'side': 'any'}, "Hero's Welcome",
     'Hero of the Village after a raid you helped win lasts 3 in-game days, and Hearth folk greet you as a hero.', None),
    ('voice_of_the_realm', 'mastery', {'tier': 4, 'cost': 5, 'side': 'b'}, 'Voice of the Realm',
     'Your Speech counts 4 higher and your Presence 2 higher wherever Hearth reads them.', None),
    # Cursed
    ('honeyed_lies', 'cursed', {'tier': 2, 'cost': 2}, 'Honeyed Lies',
     'Your Speech counts 4 higher wherever Hearth reads it.', ('downside', 'Your market edge at Hearth is 5%% worse.')),
    ('demagogue', 'cursed', {'tier': 3, 'cost': 3}, 'Demagogue',
     'Your Presence counts 4 higher wherever Hearth reads it.', ('downside', 'Skill XP from everything but Speech is 20%% lower.')),
    # Feats
    ('golden_mouth', 'feat', {'feat': {'counter': 'speech_golden_mouth', 'target': 50}}, 'Golden Mouth',
     'Failed speech checks give full XP.', ('hint', 'They came to argue and left agreeing.')),
    ('market_regular', 'feat', {'feat': {'counter': 'speech_market_regular', 'target': 200}}, 'Market Regular',
     'Speech XP from haggles +25%%.', ('hint', 'Every stall knows your name.')),
    # Synergies
    ('caravan_master', 'synergy', {'synergy': {'mettle:speech': 10, 'mettle:athletics': 10}}, 'Caravan Master',
     'You and allies within 8 blocks carry 10%% more on paths and roads.', None),
    ('tavern_keeper', 'synergy', {'synergy': {'mettle:speech': 10, 'mettle:survival': 10}}, 'Tavern-Keeper',
     'Food you cook in a furnace or smoker carries your name and sells 20%% dearer at Hearth markets.', None),
    # Teaching
    ('the_courtiers_manners', 'teaching', {}, "The Courtier's Manners",
     'Your Presence counts 2 higher wherever Hearth reads it.', ('hint', "Taught by a city's merchant, or found in A Treatise on Courtesy.")),
]

ORDER = {'honeyed_lies': 20, 'demagogue': 21}

FEEDBACK = {
    'mettle.feedback.speech.golden_mouth': 'Golden Mouth · full XP',
    'mettle.feedback.speech.barterer': 'Barterer · one more',
    'mettle.feedback.speech.restock': 'Barterer · the trader restocks',
    'mettle.feedback.speech.heros_welcome': "Hero's Welcome · three days",
    'mettle.feedback.speech.caravan': 'Caravan Master · +10 %% carry on the road',
    'mettle.feedback.speech.cooked': 'Tavern-Keeper · cooked by you',
    'mettle.speech.cooked_by': 'Cooked by %s',
    'mettle.speech.appraise': '%s pays %s · asks %s',
    'mettle.speech.appraise.villager': 'A villager pays %s emeralds',
    'mettle.speech.appraise.none': 'No market near to price it',
    'mettle.path.speech.a': 'Trader',
    'mettle.path.speech.b': 'Courtier',
    'mettle.tome.the_courtiers_manners': 'A Treatise on Courtesy',
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent='\t', ensure_ascii=False)
        f.write('\n')


def main():
    shutil.rmtree(PERKS, ignore_errors=True)
    lang = dict(FEEDBACK)
    for order, (pid, kind, extra, name, desc, more) in enumerate(TREE):
        key = 'mettle.perk.speech.' + pid
        obj = {'skill': 'mettle:speech', 'kind': kind}
        for k in ('tier', 'path', 'ranks', 'cost', 'side'):
            if k in extra:
                obj[k] = extra[k]
        obj['name'] = {'translate': key}
        obj['description'] = {'translate': key + '.description'}
        lang[key] = name
        lang[key + '.description'] = desc
        if 'rank_values' in extra:
            obj['rank_values'] = extra['rank_values']
        if more:
            obj[more[0]] = {'translate': key + '.' + more[0]}
            lang[key + '.' + more[0]] = more[1]
        if 'feat' in extra:
            obj['feat'] = extra['feat']
        if 'synergy' in extra:
            obj['synergy'] = extra['synergy']
        obj['order'] = ORDER.get(pid, order)
        write(os.path.join(PERKS, pid + '.json'), obj)
    with open(DRAFT_LANG, encoding='utf-8') as f:
        draft = json.load(f)
    for k in [k for k in lang if any(k.startswith('mettle.perk.speech.' + c) for c in ('honeyed_lies', 'demagogue'))]:
        draft[k] = lang.pop(k)
    write(DRAFT_LANG, draft)
    write(LANG, dict(sorted(lang.items())))
    print('speech perks %d' % len(TREE))


if __name__ == '__main__':
    main()
