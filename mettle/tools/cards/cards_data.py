"""Card data for cards v2 (docs/CARDS.md §4, §6): the eight trait pools, the fourteen keywords with their set bonuses,
each skill's native keywords and second-keyword pool, the lang lines for all of it, and the keyword glyphs.

    python cards_data.py      # rewrites data/mettle/mettle/{trait,keyword,card_skill} and lang/en_us/cards_data.json

The tables below are the source; the JSON is generated. CARDS.md §4.2 and §6 are the design they copy.
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', '..', 'src', 'main', 'resources'))
DATA = os.path.join(RES, 'data', 'mettle', 'mettle')
LANG = os.path.join(RES, 'assets', 'mettle', 'lang', 'en_us', 'cards_data.json')
GLYPHS = os.path.join(RES, 'assets', 'mettle', 'textures', 'gui', 'keyword')

P = 'percent'
F = 'flat'
MIGHT, FIN, END, FOR = 'might', 'finesse', 'endurance', 'fortune'
B, N = 'broad', 'narrow'


def beh(name):
    return {'behaviour': 'mettle:' + name}


def attr(attribute, operation='add_value', scale=1.0, when='always'):
    e = {'attribute': attribute, 'operation': operation, 'scale': scale}
    if when != 'always':
        e['when'] = when
    return e


MB = 'add_multiplied_base'

# skill -> [(id, name, description with %s, min, max, step, unit, kind, scope, effect, arsenal description or None)]
TRAITS = {
    'mining': [
        ('deepborn', 'Deepborn', 'Dig %s faster below y 0.', 5, 12, 1, P, MIGHT, B, beh('dig_below_zero'), None),
        ('rockbreaker', 'Rockbreaker', 'Stone-like blocks (stone, deepslate, tuff, granite…) break %s faster.', 5, 12, 1, P, MIGHT, B, beh('dig_stone'), None),
        ('netherborn', 'Netherborn', 'Netherrack, blackstone, basalt and ancient debris break %s faster.', 10, 25, 1, P, MIGHT, N, beh('dig_nether'), None),
        ('haulers', "Hauler's", 'Stone, ore and raw metal weigh %s less for you.', 8, 20, 1, P, MIGHT, N, beh('lighter_stone'), None),
        ('greedy', 'Greedy', 'Metal ores drop one more raw metal %s of the time.', 3, 8, 1, P, FOR, N, beh('extra_metal_ore'), None),
        ('lucky', 'Lucky', 'Gem ores (diamond, emerald, lapis, redstone, quartz) drop one more %s of the time.', 3, 8, 1, P, FOR, N, beh('extra_gem_ore'), None),
        ('seamfinder', 'Seamfinder', 'The first ore of each vein drops one more %s of the time.', 8, 20, 1, P, FOR, N, beh('seam_first_ore'), None),
        ('geodic', 'Geodic', 'Amethyst buds and clusters drop one more shard %s of the time.', 15, 25, 1, P, FOR, N, beh('extra_amethyst'), None),
        ('prospecting', 'Prospecting', 'Mining XP from ore +%s.', 5, 12, 1, P, FOR, B, beh('ore_xp'), None),
        ('echoing', 'Echoing', 'Breaking an ore also breaks one touching ore of the same kind %s of the time.', 4, 10, 1, P, FIN, N, beh('echo_ore'), None),
        ('surefooted', 'Surefooted', 'Digging while climbing, swimming or in the air is %s less slow.', 10, 25, 1, P, FIN, N, beh('dig_unsteady'), None),
        ('stonehide', 'Stonehide', 'Falling blocks, dripstone, magma and suffocation hurt %s less.', 10, 25, 1, P, END, N, beh('hurt_stonehide'), None),
        ('cavern_wise', 'Cavern-wise', 'Mobs hurt you %s less while you are below y 0.', 4, 10, 1, P, END, B, beh('hurt_mobs_below_zero'), None),
        ('tireless', 'Tireless', 'Digging costs %s less hunger.', 10, 25, 1, P, END, N, beh('dig_hunger'), None),
        ('unworn', 'Unworn', 'Pickaxes lose durability %s less often.', 6, 15, 1, P, END, N, beh('durability_pickaxe'), None),
    ],
    'blades': [
        ('keen', 'Keen', 'Blade critical hits deal %s more.', 5, 12, 1, P, MIGHT, B, beh('blade_crit_damage'), None),
        ('sweeping', 'Sweeping', 'Your sweeps deal %s more.', 8, 20, 1, P, MIGHT, N, beh('sweep_damage'), None),
        ('finishing', 'Finishing', 'Blade hits on targets below 30 %% health deal %s more.', 5, 12, 1, P, MIGHT, B, beh('blade_finishing'), None),
        ('tempo', 'Tempo', 'Each hit on the same target within 1.5 s of the last swings %s faster, up to 3 stacks.', 3, 6, 1, P, FIN, B, beh('blade_tempo'), None),
        ('bleeding', 'Bleeding', 'Critical blade hits make the target bleed %s damage over 3 s.', 1, 3, 0.5, F, FIN, N, beh('blade_bleed'), None),
        ('parrying', 'Parrying', 'Holding a blade (not blocking), a melee hit is turned aside for half its damage %s of the time.', 3, 8, 1, P, FIN, B, beh('blade_parry'), None),
        ('fleet', 'Fleet', 'You move %s faster while holding a blade.', 2, 6, 1, P, FIN, B, attr('minecraft:movement_speed', MB, 0.01, 'holding_blade'), None),
        ('ambushing', 'Ambushing', "Blade hits on a mob that isn't targeting you deal %s more.", 6, 15, 1, P, FIN, N, beh('blade_ambush'), None),
        ('long', 'Long', 'Blades reach %s blocks further.', 0.1, 0.3, 0.1, F, FIN, B, attr('minecraft:entity_interaction_range', 'add_value', 1.0, 'holding_blade'), None),
        ('flourishing', 'Flourishing', 'A blade kill gives you %s speed for 3 s.', 5, 12, 1, P, FIN, N, beh('blade_flourish'), None),
        ('thirsting', 'Thirsting', 'You heal %s of the blade damage you deal (at most 1 heart a second).', 2, 5, 1, P, END, B, beh('blade_lifesteal'), None),
        ('grounded', 'Grounded', '%s less knockback while you hold a blade.', 10, 25, 1, P, END, N, attr('minecraft:knockback_resistance', 'add_value', 0.01, 'holding_blade'), None),
        ('bloodied', 'Bloodied', 'Below half health, blades swing %s faster.', 5, 12, 1, P, END, N, attr('minecraft:attack_speed', MB, 0.01, 'bloodied_blade'), None),
        ('honed', 'Honed', 'Swords and spears lose durability %s less often.', 6, 15, 1, P, END, N, beh('durability_blade'), None),
        ('trophy', 'Trophy', 'Blade kills drop one more of their loot %s of the time.', 3, 8, 1, P, FOR, N, beh('blade_extra_loot'), None),
        ('taunting', 'Taunting', 'A mob you hit with a blade turns on you instead of an ally %s of the time.', 10, 25, 1, P, FOR, N, beh('blade_taunt'), None),
    ],
    'heavy_weapons': [
        ('crushing', 'Crushing', "Heavy hits ignore %s of the target's armour.", 5, 12, 1, P, MIGHT, B, beh('heavy_armour_pierce'), None),
        ('staggering', 'Staggering', 'Heavy hits daze the target for 0.5 s %s of the time.', 4, 10, 1, P, MIGHT, B, beh('heavy_daze'), None),
        ('woodcutters', "Woodcutter's", 'Axes chop wood %s faster.', 8, 20, 1, P, MIGHT, N, beh('dig_wood'), None),
        ('heavy_handed', 'Heavy-handed', 'Heavy hits knock back %s further.', 10, 25, 1, P, MIGHT, N, beh('heavy_knockback'), None),
        ('cleaving', 'Cleaving', '%s of an axe hit spills onto one enemy beside the target.', 5, 12, 1, P, MIGHT, B, beh('heavy_cleave'), None),
        ('thunderous', 'Thunderous', 'Mace smashes deal %s more.', 8, 20, 1, P, MIGHT, N, beh('mace_smash'), None),
        ('shieldbreaker', 'Shieldbreaker', 'Heavy hits keep a raised shield down %s longer.', 15, 25, 1, P, MIGHT, N, beh('heavy_shieldbreak'), None),
        ('charging', 'Charging', 'Your first heavy hit after 2 s of sprinting deals %s more.', 6, 15, 1, P, FIN, N, beh('heavy_charge'), None),
        ('swinging', 'Swinging', 'Heavy weapons swing %s faster.', 3, 8, 1, P, FIN, B, attr('minecraft:attack_speed', MB, 0.01, 'holding_heavy'), None),
        ('raging', 'Raging', 'Each hit you take gives +%s damage for 5 s, up to 3 stacks.', 2, 4, 1, P, END, B, beh('heavy_rage'), None),
        ('rooted', 'Rooted', '%s less knockback while you hold a heavy weapon.', 10, 25, 1, P, END, N, attr('minecraft:knockback_resistance', 'add_value', 0.01, 'holding_heavy'), None),
        ('bloodthirsty', 'Bloodthirsty', 'A heavy kill heals %s hearts (at most that much a second; a kill by a cleave or an overkill heals half).', 0.5, 1.5, 0.5, F, END, B, beh('heavy_kill_heal'), None),
        ('unshaken', 'Unshaken', 'Slowness and Weakness on you last %s less while you hold a heavy weapon.', 15, 25, 1, P, END, N, beh('heavy_unshaken'), None),
        ('unworn', 'Unworn', 'Axes, maces and tridents lose durability %s less often.', 6, 15, 1, P, END, N, beh('durability_heavy'), None),
        ('splitting', 'Splitting', 'Logs you chop drop one more %s of the time.', 5, 12, 1, P, FOR, N, beh('extra_logs'), None),
        ('headtaker', 'Headtaker', "Heavy kills drop the mob's head %s of the time.", 2, 5, 1, P, FOR, N, beh('heavy_heads'), None),
    ],
    'marksmanship': [
        ('heavy_tipped', 'Heavy-tipped', 'Arrows deal %s more.', 3, 8, 1, P, MIGHT, B, beh('arrow_damage'), None),
        ('piercing', 'Piercing', 'Arrows pass through their first target %s of the time.', 5, 12, 1, P, MIGHT, N, beh('arrow_pierce'), None),
        ('quickdraw', 'Quickdraw', 'Bows draw %s faster.', 4, 10, 1, P, FIN, B, beh('bow_draw'), None),
        ('farsighted', 'Farsighted', 'Arrows deal %s more beyond 20 blocks.', 6, 15, 1, P, FIN, N, beh('arrow_far'), None),
        ('swift_flighted', 'Swift-flighted', 'Arrows fly %s faster (they drop less at range).', 5, 12, 1, P, FIN, B, beh('arrow_speed'), None),
        ('cranking', 'Cranking', 'Crossbows load %s faster.', 8, 15, 1, P, FIN, N, beh('crossbow_load'), None),
        ('headhunting', 'Headhunting', 'Arrows that hit the head deal %s more.', 8, 20, 1, P, FIN, N, beh('arrow_head'), None),
        ('winged', 'Winged', 'Arrows deal %s more to flying mobs.', 10, 25, 1, P, FIN, N, beh('arrow_flying'), None),
        ('twinned', 'Twinned', 'A shot looses a second arrow beside the first %s of the time (it uses one).', 3, 8, 1, P, FIN, N, beh('arrow_twin'), None),
        ('poised', 'Poised', 'After you stand still for 1 s, arrows deal %s more.', 5, 12, 1, P, END, B, beh('arrow_poised'), None),
        ('steady', 'Steady', 'Your shots spread %s less while you move.', 10, 25, 1, P, END, N, beh('arrow_steady'), None),
        ('thrifty', 'Thrifty', "Arrows aren't used up %s of the time.", 4, 10, 1, P, FOR, B, beh('arrow_save'), None),
        ('retrieving', 'Retrieving', 'Arrows drop back from what they kill %s of the time.', 8, 20, 1, P, FOR, N, beh('arrow_retrieve'), None),
        ('marking', 'Marking', 'A target you hit takes %s more damage from everyone for 3 s.', 3, 8, 1, P, FOR, B, beh('arrow_mark'), None),
        ('game_hunters', "Game-hunter's", 'Arrows deal %s more to animals.', 10, 25, 1, P, FOR, N, beh('arrow_animals'), None),
    ],
    'defense': [
        ('thorned', 'Thorned', 'Melee hits your shield blocks hurt the attacker for %s of the hit.', 5, 12, 1, P, MIGHT, B, beh('shield_thorns'), None),
        ('unyielding', 'Unyielding', '%s less knockback.', 5, 12, 1, P, MIGHT, B, attr('minecraft:knockback_resistance', 'add_value', 0.01), None),
        ('fitted', 'Fitted', 'Worn armour weighs %s less.', 8, 15, 1, P, MIGHT, N, beh('lighter_armour'), None),
        ('quickguard', 'Quickguard', 'Your shield raises %s faster.', 15, 25, 1, P, FIN, N, beh('shield_raise'), None),
        ('arrowcatcher', 'Arrowcatcher', 'Arrows and other projectiles hurt %s less.', 6, 12, 1, P, FIN, B, beh('hurt_projectile'), None),
        ('stalwart', 'Stalwart', '+%s armour.', 0.5, 1.5, 0.5, F, END, B, attr('minecraft:armor'), None),
        ('toughened', 'Toughened', '+%s armour toughness.', 0.5, 1.5, 0.5, F, END, B, attr('minecraft:armor_toughness'), None),
        ('stout', 'Stout', '+%s ♥ max health.', 0.5, 1, 0.5, F, END, B, attr('minecraft:max_health', 'add_value', 2.0), None),
        ('mending', 'Mending', 'Armour and shields wear %s slower.', 8, 15, 1, P, END, N, beh('durability_armour'), None),
        ('resolute', 'Resolute', 'Harmful effects on you last %s less.', 8, 15, 1, P, END, N, beh('effects_shorter'), None),
        ('fireproof', 'Fireproof', 'Fire and lava hurt %s less.', 10, 25, 1, P, END, N, beh('hurt_fire'), None),
        ('blastproof', 'Blastproof', 'Explosions hurt %s less.', 10, 25, 1, P, END, N, beh('hurt_explosion'), None),
        ('recovering', 'Recovering', 'After 5 s without being hurt, you regenerate %s faster.', 10, 25, 1, P, END, N, beh('regen_rest'), None),
        ('shielding', 'Shielding', 'While you block, players within 4 blocks take %s less damage.', 4, 10, 1, P, FOR, B, beh('shield_allies'), None),
        ('taunting', 'Taunting', 'While you block, a hostile within 8 blocks attacking an ally turns on you %s of the time.', 10, 25, 1, P, FOR, N, beh('shield_taunt'), None),
    ],
    'athletics': [
        ('strapped', 'Strapped', '+%s kg carry capacity.', 3, 8, 1, F, MIGHT, B, attr('mettle:carry_capacity'), None),
        ('burden_wise', 'Burden-wise', 'You become Burdened later: at 75 %% of your capacity plus %s points.', 2, 5, 1, F, MIGHT, B, beh('burden_later'), None),
        ('bracing', 'Bracing', '%s less knockback while you sprint.', 10, 25, 1, P, MIGHT, N, attr('minecraft:knockback_resistance', 'add_value', 0.01, 'sprinting'), None),
        ('fleet', 'Fleet', 'You sprint %s faster.', 2, 6, 1, P, FIN, B, attr('minecraft:movement_speed', MB, 0.01, 'sprinting'), None),
        ('springing', 'Springing', 'Sprint-jumps go %s higher.', 4, 10, 1, P, FIN, B, attr('minecraft:jump_strength', MB, 0.01, 'sprinting'), None),
        ('featherfall', 'Featherfall', 'You fall %s more blocks before taking damage.', 0.5, 1.5, 0.5, F, FIN, B, attr('minecraft:safe_fall_distance'), None),
        ('climbing', 'Climbing', 'You climb %s faster.', 10, 25, 1, P, FIN, N, beh('climb_speed'), None),
        ('surefoot', 'Surefoot', 'Soul sand, honey, powder snow and cobwebs slow you %s less.', 15, 25, 1, P, FIN, N, beh('sticky_blocks'), None),
        ('windless', 'Windless', 'Sprinting costs %s less hunger.', 8, 15, 1, P, END, N, beh('sprint_hunger'), None),
        ('waterborn', 'Waterborn', 'You swim %s faster.', 6, 15, 1, P, END, N, attr('minecraft:water_movement_efficiency', 'add_value', 0.01), None),
        ('deep_lunged', 'Deep-lunged', 'You hold your breath %s s longer.', 3, 8, 1, F, END, N, beh('breath_longer'), None),
        ('long_winded', 'Long-winded', 'After 10 s of sprinting, +%s speed until you stop.', 3, 8, 1, P, END, B, beh('sprint_long'), None),
        ('rolling', 'Rolling', 'Falls hurt %s less.', 6, 15, 1, P, END, N, attr('minecraft:fall_damage_multiplier', MB, -0.01), None),
        ('pacesetting', 'Pacesetting', 'Allies sprinting within 8 blocks of you move %s faster.', 2, 5, 1, P, FOR, B, beh('allies_sprint'), None),
    ],
    'survival': [
        ('stalking', 'Stalking', 'Animals notice you %s closer.', 10, 25, 1, P, FIN, N, beh('animals_notice'), None),
        ('wayfaring', 'Wayfaring', 'You move %s faster on paths and roads.', 4, 10, 1, P, FIN, B, attr('minecraft:movement_speed', MB, 0.01, 'on_path'), None),
        ('hearty', 'Hearty', 'Food fills you %s more.', 5, 12, 1, P, END, B, beh('food_fill'), None),
        ('iron_gut', 'Iron-gut', 'You get hungry %s slower.', 4, 10, 1, P, END, B, beh('hunger_slower'), None),
        ('weathered', 'Weathered', 'Freezing and drowning hurt %s less.', 15, 25, 1, P, END, N, beh('hurt_freeze_drown'), None),
        ('fireside', 'Fireside', 'Campfire Rest and campfire cooking work %s faster.', 15, 25, 1, P, END, N, beh('campfire_faster'), None),
        ('thornwalker', 'Thornwalker', 'Berry bushes and cacti hurt %s less.', 15, 25, 1, P, END, N, beh('hurt_thorns'), None),
        ('herbwise', 'Herbwise', 'Potions you drink last %s longer.', 6, 15, 1, P, END, N, beh('potions_longer'), None),
        ('antidote', 'Antidote', 'Poison and Hunger on you last %s less.', 15, 25, 1, P, END, N, beh('poison_shorter'), None),
        ('foraging', 'Foraging', 'Wild plants and berries give a bonus item %s of the time.', 5, 12, 1, P, FOR, N, beh('extra_plants'), None),
        ('skinning', 'Skinning', 'Animals drop one more hide, leather, wool or feather %s of the time.', 8, 20, 1, P, FOR, N, beh('extra_hide'), None),
        ('butchering', 'Butchering', 'Animals drop one more meat %s of the time.', 8, 20, 1, P, FOR, N, beh('extra_meat'), None),
        ('cooks', "Cook's", 'Food you cook comes out one extra %s of the time.', 5, 12, 1, P, FOR, N, beh('extra_cooked'), None),
        ('wayfinder', 'Wayfinder', 'Discovery XP +%s.', 8, 20, 1, P, FOR, N, beh('discovery_xp'), None),
        ('beastfriend', 'Beastfriend', 'Your tamed animals near you deal %s more and take %s less.', 8, 20, 1, P, FOR, N, beh('pets_stronger'), None),
    ],
    'smithing': [
        ('tempered', 'Tempered', 'Tools and weapons you craft last %s longer.', 5, 12, 1, P, MIGHT, B, beh('crafted_durability'), 'Parts you forge last %s longer.'),
        ('edged', 'Edged', 'Weapons you craft deal %s more.', 2, 5, 1, P, MIGHT, B, beh('crafted_damage'), 'Blades and heads you forge deal %s more.'),
        ('lightened', 'Lightened', 'Armour you craft weighs %s less.', 6, 15, 1, P, FIN, N, beh('crafted_lighter'), 'Armour and parts you forge weigh %s less.'),
        ('steady_handed', 'Steady-handed', 'Anvil jobs cost %s fewer levels (never below 1).', 8, 20, 1, P, FIN, N, beh('anvil_cheaper'), "The anvil's strike window is %s wider."),
        ('quenched', 'Quenched', 'Tools you craft dig %s faster.', 3, 8, 1, P, FIN, B, beh('crafted_dig'), 'Quench tempers are %s stronger.'),
        ('hot_forged', 'Hot-forged', 'Smithing XP from smelting +%s.', 10, 25, 1, P, END, N, beh('smelt_xp'), 'The forge heats %s faster.'),
        ('menders', "Mender's", 'Anvil repairs restore %s more durability.', 10, 25, 1, P, END, N, beh('anvil_repair'), 'Anvil and smith repairs restore %s more durability.'),
        ('proofing', 'Proofing', 'Armour you craft gets +%s toughness a piece.', 0.25, 0.5, 0.25, F, END, B, beh('crafted_toughness'), None),
        ('thrifty', 'Thrifty', 'A recipe with ingots gives one back %s of the time.', 3, 8, 1, P, FOR, N, beh('craft_refund'), 'Forging returns a nugget %s of the time.'),
        ('masterly', 'Masterly', 'A tool, weapon or armour piece you craft is a Masterwork %s of the time.', 2, 5, 1, P, FOR, N, beh('craft_masterwork'), 'A part comes out one quality higher %s of the time.'),
        ('salvaging', 'Salvaging', 'Salvaging (grindstone, Salvager) returns %s more metal.', 10, 25, 1, P, FOR, N, beh('salvage_more'), 'Workbench salvage returns %s more.'),
        ('signature', 'Signature', 'Goods marked "Forged by you" sell for %s more at Hearth markets.', 5, 12, 1, P, FOR, N, beh('market_signature'), None),
        ('runewise', 'Runewise', 'Smithing XP from anvil jobs +%s.', 10, 25, 1, P, FOR, N, beh('anvil_xp'), None),
        ('diligent', 'Diligent', 'Smithing XP +%s.', 5, 12, 1, P, FOR, B, beh('smithing_xp'), None),
    ],
    'stealth': [
        ('shadowed', 'Shadowed', 'While you sneak, hostiles notice you %s closer.', 5, 12, 1, P, FIN, B, beh('sneak_unseen'), None),
        ('hushed', 'Hushed', 'Your vibrations reach sculk %s less far.', 15, 25, 1, P, FIN, N, beh('vibration_hush'), None),
        ('nightstalker', 'Nightstalker', 'In light 7 or less you sneak %s faster.', 10, 25, 1, P, FIN, N, beh('sneak_dark_speed'), None),
        ('backbiting', 'Backbiting', 'Sneak attacks deal %s more.', 8, 20, 1, P, MIGHT, N, beh('sneak_attack_damage'), None),
        ('hamstringing', 'Hamstringing', 'Sneak attacks slow the target %s for 2 s.', 15, 25, 1, P, FIN, N, beh('sneak_attack_slow'), None),
        ('vanishing', 'Vanishing', 'A mob that loses sight of you gives up %s sooner.', 15, 25, 1, P, FIN, N, beh('lose_sooner'), None),
        ('catfooted', 'Catfooted', 'Falls while sneaking hurt %s less.', 10, 25, 1, P, FIN, N, beh('sneak_fall'), None),
        ('muffled', 'Muffled', 'A kill from stealth alerts mobs nearby %s less often.', 10, 25, 1, P, FIN, N, beh('stealth_alert'), None),
        ('patient', 'Patient', 'After sneaking still for 2 s, your next hit deals %s more.', 6, 15, 1, P, FIN, N, beh('still_hit'), None),
        ('lurking', 'Lurking', 'Darkness and Blindness on you last %s less.', 15, 25, 1, P, END, N, beh('dark_shorter'), None),
        ('swift_exit', 'Swift Exit', 'After a sneak attack, +%s speed for 2 s.', 4, 10, 1, P, FIN, N, beh('sneak_attack_speed'), None),
        ('light_fingered', 'Light-fingered', "The pickpocket's mark notices you %s slower (until Hearth reads it: animals notice you that much closer).", 5, 12, 1, P, FOR, B, beh('pickpocket_awareness'), None),
        ('unseen', 'Unseen', "Hearth's witnesses see you %s nearer (until Hearth reads it: your Stealth counts 1 higher at night).", 5, 12, 1, P, FIN, B, beh('witness_sight'), None),
        ('lootfinger', 'Lootfinger', 'Mobs you kill unseen drop one more of their loot %s of the time.', 3, 8, 1, P, FOR, N, beh('unseen_loot'), None),
    ],
}

KEYWORDS = {
    # id: (name, theme, {tier: (description, [modifiers], behaviour or None)})
    'ember': ('Ember', 'fire, forge, the cookfire', {
        2: ('Fire and lava hurt you 15 % less.', [], 'set_ember_2'),
        4: ('Melee hits set the target alight one time in eight (3 s); campfire food heals 1 heart when eaten.', [], 'set_ember_4'),
        6: ('Kindled: taking fire damage makes you Kindled for 8 s: +15 % damage, your hits ignite, and you burn for at most 2 s (30 s cooldown).', [], 'set_ember_6')}),
    'deep': ('Deep', 'the underground, the dark', {
        2: ('Below y 0: dig 5 % faster, take 5 % less damage.', [], 'set_deep_2'),
        4: ('Below y 0, hostiles notice you 25 % closer.', [], 'set_deep_4'),
        6: ('Underdark: in light 4 or less you regenerate half a heart every 5 s, and your first hit from the dark on a mob is a critical (10 s cooldown).', [], 'set_deep_6')}),
    'swift': ('Swift', 'speed', {
        2: ('+3 % movement speed.', [('minecraft:movement_speed', 0.03, MB)], None),
        4: ('+5 % attack speed and bow draw speed.', [('minecraft:attack_speed', 0.05, MB)], 'set_swift_4'),
        6: ('Slipstream: after 3 s of sprinting, your next hit or shot within 2 s deals +30 %.', [], 'set_swift_6')}),
    'brutal': ('Brutal', 'heavy blows', {
        2: ('+4 % melee damage.', [('minecraft:attack_damage', 0.04, MB)], None),
        4: ('Critical hits knock back half again as far and daze for 0.5 s.', [], 'set_brutal_4'),
        6: ('Overkill: damage beyond what kills a mob spills onto the nearest enemy within 4 blocks (up to 10).', [], 'set_brutal_6')}),
    'echo': ('Echo', 'things that happen again', {
        2: ('Perk cooldowns 10 % shorter.', [], 'set_echo_2'),
        4: ('Perk cooldowns 20 % shorter (in all).', [], 'set_echo_4'),
        6: ('Reverberate: when a perk with a cooldown fires, one time in four it is ready again at once.', [], 'set_echo_6')}),
    'warden': ('Warden', 'guarding others', {
        2: ('+1 armour.', [('minecraft:armor', 1.0, 'add_value')], None),
        4: ('Allies within 6 blocks take 8 % less damage (the best Warden in range counts).', [], 'set_warden_4'),
        6: ('Bodyguard: when an ally within 6 blocks would take more than 4 damage, you take a third of it instead (4 s cooldown).', [], 'set_warden_6')}),
    'keen': ('Keen', 'precision', {
        2: ('Critical hits (melee and arrows) deal +8 %.', [], 'set_keen_2'),
        4: ('Your first hit on a target at full health is a critical, once per target.', [], 'set_keen_4'),
        6: ('Weak Point: three hits on one target within 4 s expose it: it takes +20 % from everyone for 4 s (10 s per target).', [], 'set_keen_6')}),
    'wild': ('Wild', 'beasts and plants', {
        2: ('Animals you kill and wild plants you pick give 10 % more.', [], 'set_wild_2'),
        4: ('Your tamed animals near you take 25 % less damage and deal 15 % more.', [], 'set_wild_4'),
        6: ('Feral: on natural ground (soil, sand, snow, gravel or stone under open sky) you regenerate as if well fed and move 8 % faster.', [], 'set_wild_6')}),
    'iron': ('Iron', 'armour and wear', {
        2: ('Armour and shields wear 10 % slower.', [], 'set_iron_2'),
        4: ('+1 armour toughness.', [('minecraft:armor_toughness', 1.0, 'add_value')], None),
        6: ('Ironskin: the first hit you take every 8 s is 3 damage lighter (after armour).', [], 'set_iron_6')}),
    'tide': ('Tide', 'water', {
        2: ('Swim 10 % faster, +5 s of breath.', [('minecraft:water_movement_efficiency', 0.1, 'add_value')], 'set_tide_2'),
        4: ('Mine at full speed under water; thrown tridents +10 %.', [('minecraft:submerged_mining_speed', 0.8, 'add_value')], 'set_tide_4'),
        6: ('Undertow: in water or rain you deal +15 %, every hit slows the target for 1 s, and you see clearly under water.', [], 'set_tide_6')}),
    'gale': ('Gale', 'air, falls, flight', {
        2: ('Fall 1.5 blocks more before taking damage.', [('minecraft:safe_fall_distance', 1.5, 'add_value')], None),
        4: ('Arrows and thrown things fly 10 % faster; sprint-jumps go 10 % higher.', [('minecraft:jump_strength', 0.1, MB, 'sprinting')], 'set_gale_4'),
        6: ('Updraft: jump once more in mid-air before you land (8 s cooldown).', [], 'set_gale_6')}),
    'hoard': ('Hoard', 'plenty', {
        2: ('Everything you carry weighs 5 % less.', [], 'set_hoard_2'),
        4: ('Blocks and mobs drop one more of something one time in 25.', [], 'set_hoard_4'),
        6: ('Magpie: loot chests you open first roll one extra time one time in four; Hearth markets give you 5 % more edge.', [], 'set_hoard_6')}),
    'hex': ('Hex', 'cursed cards', {
        2: ("Your cursed cards' downsides are 25 % smaller.", [], 'set_hex_2'),
        4: ('Their upsides are 15 % stronger.', [], 'set_hex_4'),
        6: ('Pact: once every 5 minutes a killing blow leaves you on 4 hearts with Strength I and Speed I for 8 s, and takes 3 max hearts until you next sleep.', [], 'set_hex_6')}),
    'kin': ('Kin', 'fellowship', {
        2: ('Allies within 8 blocks gain 5 % more skill XP.', [], 'set_kin_2'),
        4: ('You and allies within 8 blocks regenerate 10 % faster.', [], 'set_kin_4'),
        6: ('Rally Cry: sneak and swing an empty hand: allies within 16 blocks get Speed I and Strength I for 8 s (90 s cooldown).', [], 'set_kin_6')}),
}


# id: (name, source, trigger, skill or None, deed, effect, lore)
MYTHICS = {
    'dragonfall': ('Dragonfall', 'world_first', 'first_dragon', None, 'First to fell the Ender Dragon',
                   'Sneak-use any banner to raise the Dragon Standard for 60 s (10 min cooldown): allies within 24 blocks gain your best active set bonus as if it were theirs.',
                   'The last wingbeat over the End is still falling.'),
    'wardens_hush': ("The Warden's Hush", 'world_first', 'first_warden', None, 'First to kill a Warden',
                     'You make no vibrations: sculk sensors, shriekers and wardens never hear you. Allies sneaking within 6 blocks of you are hushed too.',
                     'The deep dark learned to hold its breath around you.'),
    'witherbane': ('Witherbane', 'world_first', 'first_wither', None, 'First to kill the Wither',
                   'Wither and poison never touch you; allies within 12 blocks take them at half strength for half as long.',
                   'Three skulls, one name, and none of them yours.'),
    'far_horizon': ('The Far Horizon', 'world_first', 'first_far', None, 'First to stand 10,000 blocks from world spawn',
                    'Homeward: hold a compass and sneak for 5 s to return to your bed or respawn point (once per in-game day).',
                    'Whoever walks that far learns the way back.'),
    'elders_eye': ("The Elder's Eye", 'world_first', 'first_elder_guardian', None, 'First to kill an Elder Guardian',
                   'You and allies within 12 blocks breathe under water, see clearly in it, and mine there at full speed.',
                   'Its eye still watches the sea, now for you.'),
    'open_hand': ('The Open Hand', 'world_first', 'first_honoured', None, 'First to be Honoured in a Hearth city (without Hearth: first Hero of the Village)',
                  'Your Hearth market edge also applies to allies within 8 blocks of you at a counter.',
                  'A town remembers who stood in its gate.'),
    'first_of_masters': ('First of Masters', 'world_first', 'first_master', None, 'First to bring any skill to 20',
                         'Mentor: allies within 16 blocks gain skill XP 20 % faster in every skill where yours is higher than theirs.',
                         'Someone had to be first. Everyone after learns faster.'),
    'unclad': ('The Unclad', 'deed', 'unclad_warden', None, 'Kill a Warden wearing no armour',
               'With no armour worn, you take 25 % less damage and move 10 % faster.',
               'Skin, nerve, and nothing else.'),
    'hundredth_dawn': ('Hundredth Dawn', 'deed', 'hundred_days', None, 'Live through 100 in-game days without dying',
                       'Pardon: once every 7 in-game days, when you die you keep everything you carry and your vanilla XP.',
                       'A hundred sunrises owe you one more.'),
    'lone_banner': ('The Lone Banner', 'deed', 'lone_raid', None, 'Win a raid with no other player within 96 blocks',
                    'With no ally within 24 blocks, you deal 15 % more and take 15 % less.',
                    'One flag held a village.'),
    'long_walker': ('Long Walker', 'deed', 'walk_100km', None, 'Travel 100 km on foot (walked, sprinted, swum)',
                    'Trailmaker: allies within 16 blocks behind you move 10 % faster, and none of you pay hunger to sprint on paths and roads.',
                    'Roads are just walks that went on long enough.'),
    'polymath': ('Polymath', 'deed', 'all_fifteen', None, 'Reach 15 in all eight wave-1 skills',
                 '+1 deck slot in every skill (it pays for its own).',
                 'Good at everything is its own kind of good.'),
    'heart_of_the_mountain': ('Heart of the Mountain', 'drawn', 'draw', 'mining', 'Drawn from the Mining deck (0.5 % a draft from 15)',
                              'Sneak-use a pickaxe on stone to bore a 1 × 2 tunnel 8 blocks deep at once; each block costs durability and drops as normal (30 s cooldown).',
                              'The stone opens for one hand in a world.'),
    'twin_edge': ('The Twin Edge', 'drawn', 'draw', 'blades', 'Drawn from the Blades deck (0.5 % a draft from 15)',
                  'Every fourth blade hit on the same target is struck again by a spectral twin, for full damage.',
                  'Two blades, one wrist.'),
    'worldsplitter': ('Worldsplitter', 'drawn', 'draw', 'heavy_weapons', 'Drawn from the Heavy Weapons deck (0.5 % a draft from 15)',
                      'A fully charged sneak-attack sends a shockwave 8 blocks along the ground: every mob in its line takes half the hit and is thrown up (10 s cooldown; no blocks broken).',
                      'The ground remembers the blow.'),
    'seeking_fletch': ('The Seeking Fletch', 'drawn', 'draw', 'marksmanship', 'Drawn from the Marksmanship deck (0.5 % a draft from 15)',
                       'Fully drawn arrows bend towards the last mob you hit, within 20°.',
                       'The feathers know who you meant.'),
    'aegis': ('Aegis', 'drawn', 'draw', 'defense', 'Drawn from the Defense deck (0.5 % a draft from 15)',
              'Hold your shield up for 1 s to raise a dome (3 blocks, 4 s): nothing fired from outside passes it, for everyone inside (45 s cooldown).',
              'Behind it, the rain stops.'),
    'seven_league_stride': ('Seven-League Stride', 'drawn', 'draw', 'athletics', 'Drawn from the Athletics deck (0.5 % a draft from 15)',
                            "Double-tap forward while sprinting to dash 6 blocks; the fall after a dash doesn't hurt (8 s cooldown).",
                            'Seven leagues, one step.'),
    'wild_hunt': ('The Wild Hunt', 'drawn', 'draw', 'survival', 'Drawn from the Survival deck (0.5 % a draft from 15)',
                  'Up to three wild wolves within 24 blocks join you as a pack while you stay in their biome, and fight what you fight.',
                  'The pack runs with you, not for you.'),
    'first_hammer': ('The First Hammer', 'drawn', 'draw', 'smithing', 'Drawn from the Smithing deck (0.5 % a draft from 15)',
                     'Tools, weapons and armour you craft last 20 % longer and carry your mark in gold.',
                     'Every hammer since has been a copy.'),
}

# The sixteen drawn Mythics of build feel (BUILD_FEEL.md §5.4): three a skill with the eight above.
NEW_MYTHICS = {
    'stonesong': ('The Stonesong', 'drawn', 'draw', 'mining', 'Drawn from the Mining deck (0.5 % a draft from 15), or forged with Mythic shards',
                  'Stand still sneaking with a pickaxe for 2 s: the nearest unmined diamond, emerald or ancient-debris vein within 24 blocks hums from its direction (60 s cooldown).',
                  'Listen long enough and the stone tells you where it keeps its best.'),
    'deep_lantern': ('The Deep Lantern', 'drawn', 'draw', 'mining', 'Drawn from the Mining deck (0.5 % a draft from 15), or forged with Mythic shards',
                     "Below y 0, hostiles can't spawn within 12 blocks of you.",
                     'A small light the dark agrees to leave alone.'),
    'misericorde': ('Misericorde', 'drawn', 'draw', 'blades', 'Drawn from the Blades deck (0.5 % a draft from 15), or forged with Mythic shards',
                    'A blade hit on a mob below 15 % health ends it (never a player or a boss).',
                    'The mercy stroke, for what has fought long enough.'),
    'red_harvest': ('Red Harvest', 'drawn', 'draw', 'blades', 'Drawn from the Blades deck (0.5 % a draft from 15), or forged with Mythic shards',
                    'A blade kill readies your swing at once, and your next swing within 2 s sweeps.',
                    'One falls, and the edge is already moving.'),
    'thunderhead': ('Thunderhead', 'drawn', 'draw', 'heavy_weapons', 'Drawn from the Heavy Weapons deck (0.5 % a draft from 15), or forged with Mythic shards',
                    'In a thunderstorm, a fully charged heavy hit calls lightning on the target (20 s cooldown; never fire on ground you may not build on).',
                    'The storm leans in when it hears the swing.'),
    'dread_maul': ('The Dread Maul', 'drawn', 'draw', 'heavy_weapons', 'Drawn from the Heavy Weapons deck (0.5 % a draft from 15), or forged with Mythic shards',
                   'A heavy kill makes hostiles within 6 blocks flee for 3 s (bosses and players untouched; 15 s cooldown).',
                   'They have heard what it does.'),
    'hunters_moon': ("Hunter's Moon", 'drawn', 'draw', 'marksmanship', 'Drawn from the Marksmanship deck (0.5 % a draft from 15), or forged with Mythic shards',
                     'Under the night sky, a fully drawn arrow that hits looses a silent second arrow at the same target for half its damage.',
                     'Two shadows on the grass, one string.'),
    'pinning_star': ('The Pinning Star', 'drawn', 'draw', 'marksmanship', 'Drawn from the Marksmanship deck (0.5 % a draft from 15), or forged with Mythic shards',
                     "A fully drawn arrow that hits a mob's legs (its lower half) roots it for 2 s (once per target every 10 s).",
                     'Where it lands, things stay.'),
    'iron_vow': ('The Iron Vow', 'drawn', 'draw', 'defense', 'Drawn from the Defense deck (0.5 % a draft from 15), or forged with Mythic shards',
                 'Once per in-game day, a blow that would kill an ally within 8 blocks lands on you instead (after your armour).',
                 'Said once, kept once a day.'),
    'long_watch': ('The Long Watch', 'drawn', 'draw', 'defense', 'Drawn from the Defense deck (0.5 % a draft from 15), or forged with Mythic shards',
                   'Sneak still with a shield raised for 3 s: hostiles within 16 blocks glow for you and allies near you for 10 s (30 s cooldown).',
                   'The one who watches is the one who sees.'),
    'ferrymans_stride': ("The Ferryman's Stride", 'drawn', 'draw', 'athletics', 'Drawn from the Athletics deck (0.5 % a draft from 15), or forged with Mythic shards',
                         'While you sprint, water bears you like ground (no swimming, no ice made); allies sprinting within 4 blocks too.',
                         'The river owes the ferryman a crossing.'),
    'cats_way': ("The Cat's Way", 'drawn', 'draw', 'athletics', 'Drawn from the Athletics deck (0.5 % a draft from 15), or forged with Mythic shards',
                 'Sprint along a wall for up to 2 s without falling (8 s cooldown).',
                 'Walls are only floors turned sideways.'),
    'beastspeakers_torc': ("The Beastspeaker's Torc", 'drawn', 'draw', 'survival', 'Drawn from the Survival deck (0.5 % a draft from 15), or forged with Mythic shards',
                           'Wolves, bears, bees, llamas and other provoked animals never turn on you; an animal you feed follows you for an in-game day.',
                           'They know the voice, and the voice knows them.'),
    'greenway': ('The Greenway', 'drawn', 'draw', 'survival', 'Drawn from the Survival deck (0.5 % a draft from 15), or forged with Mythic shards',
                 'Leaves, vines and berry bushes neither slow nor hurt you, and hostiles that lose sight of you lose your trail twice as fast.',
                 'The woods close behind you like a door.'),
    'ever_ember': ('The Ever-Ember', 'drawn', 'draw', 'smithing', 'Drawn from the Smithing deck (0.5 % a draft from 15), or forged with Mythic shards',
                   'Furnaces, smokers and blast furnaces you last filled smelt twice as fast and burn half the fuel.',
                   'A coal that remembers every fire it was part of.'),
    'menders_hand': ("The Mender's Hand", 'drawn', 'draw', 'smithing', 'Drawn from the Smithing deck (0.5 % a draft from 15), or forged with Mythic shards',
                     'Once per in-game day, repair any item fully at an anvil for no levels and with no prior-work cost.',
                     'Some hands only have to touch a thing for it to be whole.'),
}

# The eight drawn Mythics can be forged too: their "how" line says so.
FORGEABLE = ', or forged with Mythic shards'

# Each Mythic's epithet, the last words of a tier IV byname (BUILD_FEEL.md §2.1).
EPITHETS = {
    'dragonfall': 'Dragonfelled', 'wardens_hush': 'of the Hush', 'witherbane': 'of the Three Skulls', 'far_horizon': 'of the Far Horizon',
    'elders_eye': "of the Elder's Eye", 'open_hand': 'of the Open Hand', 'first_of_masters': 'of the First Mastery', 'unclad': 'the Unclad',
    'hundredth_dawn': 'of the Hundredth Dawn', 'lone_banner': 'of the Lone Banner', 'long_walker': 'of the Long Road', 'polymath': 'of Many Crafts',
    'heart_of_the_mountain': 'of the Mountain', 'twin_edge': 'of the Twin Edge', 'worldsplitter': 'of the Split World',
    'seeking_fletch': 'of the Seeking Fletch', 'aegis': 'of the Aegis', 'seven_league_stride': 'of Seven Leagues', 'wild_hunt': 'of the Wild Hunt',
    'first_hammer': 'of the First Hammer', 'stonesong': 'of the Stonesong', 'deep_lantern': 'of the Deep Lantern', 'misericorde': 'of the Mercy Stroke',
    'red_harvest': 'of the Red Harvest', 'thunderhead': 'of the Thunderhead', 'dread_maul': 'of the Dread Maul', 'hunters_moon': "of the Hunter's Moon",
    'pinning_star': 'of the Pinning Star', 'iron_vow': 'of the Iron Vow', 'long_watch': 'of the Long Watch', 'ferrymans_stride': 'of the Ferry',
    'cats_way': "of the Cat's Way", 'beastspeakers_torc': 'Beastspeaker', 'greenway': 'of the Greenway', 'ever_ember': 'of the Ever-Ember',
    'menders_hand': "of the Mender's Hand",
}

# How much each keyword leans the deal toward it (BUILD_FEEL.md §1.2): the scarce ones most, the common ones not at all.
MOMENTUM = {'tide': 1.0, 'deep': 0, 'kin': 0.5, 'warden': 0.5, 'gale': 0.25, 'echo': 0.25}

# Each set tier in plain words, for "Your build" (BUILD_FEEL.md §2.3).
SUMMARY = {
    'ember': ('Fire and lava hurt you less.', 'Your hits sometimes set foes alight.', 'Burning makes you Kindled: harder hits that set foes alight.'),
    'deep': ('Below ground you dig faster and take less.', 'Below ground, hostiles notice you later.', 'In the dark you heal, and your first hit is a critical.'),
    'swift': ('You move a little faster.', 'You swing and draw faster.', 'A sprint makes your next hit land harder.'),
    'brutal': ('Your melee hits land harder.', 'Your critical hits stagger and throw foes back.', 'Damage past a kill spills onto the next enemy.'),
    'echo': ('Your perk cooldowns are shorter.', 'Your perk cooldowns are much shorter.', 'Perks sometimes come back at once.'),
    'warden': ('You wear a little more armour.', 'Allies near you take less damage.', 'You take part of the hits meant for allies.'),
    'keen': ('Your critical hits deal more.', 'Your first hit on a fresh foe is a critical.', 'Three quick hits expose a foe to everyone.'),
    'wild': ('Hunting and gathering give a little more.', 'Your tamed animals are tougher and hit harder.', 'On wild ground you heal and run faster.'),
    'iron': ('Armour and shields wear slower.', 'Your armour is tougher.', 'Every few seconds a hit on you lands lighter.'),
    'tide': ('You swim faster and hold your breath longer.', 'You mine at full speed under water.', 'In water or rain you hit harder and slow your foes.'),
    'gale': ('Falls hurt you later.', 'Your shots fly faster and your sprint-jumps go higher.', 'You can jump once more in mid-air.'),
    'hoard': ('What you carry weighs less.', 'Blocks and mobs sometimes drop extra.', 'Loot chests sometimes roll twice.'),
    'hex': ('Your curses bite less.', 'Your curses give more.', 'Now and then a killing blow leaves you standing.'),
    'kin': ('Allies near you learn faster.', 'You and your allies heal faster.', 'A rally cry spurs every ally near you.'),
}

# Bynames (BUILD_FEEL.md §2.1): keyword prefix and word, skill suffix and noun, pair nouns with their lines.
BYNAME_KEYWORDS = {
    'brutal': ('Grim', 'Grim'), 'deep': ('Deep', 'Deep'), 'echo': ('Echo', 'Echoing'), 'ember': ('Ember', 'Ember'), 'gale': ('Gale', 'Gale'),
    'hex': ('Hex', 'Hexed'), 'hoard': ('Gold', 'Gilded'), 'iron': ('Iron', 'Iron'), 'keen': ('Keen', 'Keen'), 'kin': ('Kin', 'Kindred'),
    'swift': ('Swift', 'Swift'), 'tide': ('Tide', 'Tidal'), 'warden': ('Ward', 'Warding'), 'wild': ('Wild', 'Wild'),
}
BYNAME_SKILLS = {
    'mining': ('delver', 'Delver', 'One who lives below the stone.'),
    'blades': ('blade', 'Blade', 'A fighter who trusts the edge.'),
    'heavy_weapons': ('hammer', 'Breaker', 'One who settles things with weight.'),
    'marksmanship': ('shot', 'Bowman', 'A hunter who strikes from afar.'),
    'defense': ('guard', 'Shield', 'One who stands in front.'),
    'athletics': ('runner', 'Runner', 'One who is always already there.'),
    'survival': ('walker', 'Strider', 'One at home in the wild.'),
    'smithing': ('smith', 'Smith', 'One who makes what others carry.'),
    'stealth': ('shade', 'Shadow', 'One who is never seen coming.'),
}
BYNAME_PAIRS = {
    ('mining', 'blades'): ('Undercutter', 'A miner who fights in close.'),
    ('mining', 'heavy_weapons'): ('Stonebreaker', 'A miner who breaks what stands in the way.'),
    ('mining', 'marksmanship'): ('Cavehunter', 'A miner who hunts in the dark.'),
    ('mining', 'defense'): ('Warden', 'A miner who holds the line.'),
    ('mining', 'athletics'): ('Pathcutter', 'A miner who opens the way.'),
    ('mining', 'survival'): ('Spelunker', 'A miner at home in deep places.'),
    ('mining', 'smithing'): ('Forgemaster', 'A miner who forges what they dig.'),
    ('blades', 'heavy_weapons'): ('Weaponmaster', 'A fighter at home with any weapon.'),
    ('blades', 'marksmanship'): ('Skirmisher', 'A fighter who strikes near and far.'),
    ('blades', 'defense'): ('Knight', 'A blade behind a shield.'),
    ('blades', 'athletics'): ('Bladedancer', 'A blade that never stands still.'),
    ('blades', 'survival'): ('Reaver', 'A blade that lives off the land.'),
    ('blades', 'smithing'): ('Swordsmith', 'One who makes the blades they wield.'),
    ('heavy_weapons', 'marksmanship'): ('Raider', 'A brute with a bow.'),
    ('heavy_weapons', 'defense'): ('Vanguard', 'The first into the fight.'),
    ('heavy_weapons', 'athletics'): ('Marauder', 'A brute who runs the field.'),
    ('heavy_weapons', 'survival'): ('Woodsman', 'An axe at home in the forest.'),
    ('heavy_weapons', 'smithing'): ('Hammerwright', 'A hammer at the anvil and in the fight.'),
    ('marksmanship', 'defense'): ('Sentinel', 'A watcher who shoots from cover.'),
    ('marksmanship', 'athletics'): ('Outrider', 'A rider of the far edges.'),
    ('marksmanship', 'survival'): ('Huntsman', 'A hunter who reads the wild.'),
    ('marksmanship', 'smithing'): ('Fletchwright', 'One who makes the arrows they loose.'),
    ('defense', 'athletics'): ('Legionary', 'A shield that keeps pace.'),
    ('defense', 'survival'): ('Keeper', 'One who keeps others safe out there.'),
    ('defense', 'smithing'): ('Armourer', 'One who makes the armour they wear.'),
    ('athletics', 'survival'): ('Trailblazer', 'One who walks where no road goes.'),
    ('athletics', 'smithing'): ('Wandersmith', 'A smith who takes the forge on the road.'),
    ('survival', 'smithing'): ('Hearthwright', 'One who makes a home anywhere.'),
    ('mining', 'stealth'): ('Deepcreeper', 'A miner who moves unseen through the dark.'),
    ('blades', 'stealth'): ('Cutthroat', 'A blade out of the dark.'),
    ('heavy_weapons', 'stealth'): ('Waylayer', 'A heavy blow from nowhere.'),
    ('marksmanship', 'stealth'): ('Nightbow', 'An arrow nobody saw loosed.'),
    ('defense', 'stealth'): ('Shadowguard', 'A guard nobody sees standing watch.'),
    ('athletics', 'stealth'): ('Footpad', 'Quick feet, quiet feet.'),
    ('survival', 'stealth'): ('Poacher', 'A hunter the woods never hear.'),
    ('smithing', 'stealth'): ('Locksmith', 'One who makes the tools of quiet work.'),
}


# skill -> (prefix, pool, {perk: native keyword})
SKILLS = {
    'mining': ('MN', ['deep', 'hoard', 'brutal', 'echo', 'iron', 'ember'], {
        'quarryman': 'brutal', 'prospector': 'hoard', 'sure_footing': 'gale', 'hard_hat': 'iron', 'lantern_eyes': 'deep', 'ore_sense': 'deep',
        'rock_hauler': 'hoard', 'tunneler': 'echo', 'strike_the_seam': 'hoard', 'gem_cutter': 'keen', 'deep_delver': 'deep', 'motherlode': 'echo',
        'mountain_breaker': 'brutal', 'greedy_pick': 'hex', 'black_lung': 'hex'}),
    'blades': ('BL', ['swift', 'keen', 'echo', 'brutal', 'warden', 'gale'], {
        'fencer': 'swift', 'quick_draw': 'keen', 'measure': 'keen', 'disarm': 'warden', 'momentum': 'echo', 'single_combat': 'keen',
        'light_feet': 'swift', 'wide_arc': 'brutal', 'bleed': 'brutal', 'cut_and_run': 'swift', 'duelist': 'warden', 'whirlwind': 'echo',
        'executioner': 'brutal', 'reckless_edge': 'hex', 'glass_dancer': 'hex'}),
    'heavy_weapons': ('HW', ['brutal', 'iron', 'ember', 'echo', 'wild'], {
        'heft': 'brutal', 'feller': 'wild', 'cleave': 'echo', 'log_hauler': 'hoard', 'rhythm_of_the_axe': 'echo', 'throwing_axe': 'gale',
        'armour_crusher': 'brutal', 'bone_breaker': 'brutal', 'iron_grip': 'iron', 'stagger': 'keen', 'timber': 'wild', 'berserker': 'ember',
        'earthshaker': 'brutal', 'bloodrage': 'hex', 'immovable': 'hex'}),
    'marksmanship': ('MK', ['keen', 'gale', 'swift', 'wild', 'echo'], {
        'steady_aim': 'keen', 'retrieval': 'hoard', 'long_shot': 'keen', 'hunters_mark': 'wild', 'steady_breath': 'gale', 'pinning_shot': 'keen',
        'quick_nock': 'swift', 'fletcher': 'hoard', 'double_nock': 'echo', 'quiverwise': 'swift', 'deadeye': 'keen', 'volley': 'echo',
        'arrowstorm': 'gale', 'heavy_draw': 'hex', 'snap_shot': 'hex'}),
    'defense': ('DF', ['iron', 'warden', 'kin', 'brutal', 'ember'], {
        'thick_skin': 'iron', 'armour_training': 'iron', 'shield_up': 'warden', 'shield_bash': 'brutal', 'perfect_block': 'keen',
        'bulwark': 'warden', 'mender': 'iron', 'second_wind': 'ember', 'hold_the_line': 'iron', 'unbowed': 'warden', 'guardian': 'kin',
        'last_stand': 'ember', 'juggernaut': 'brutal', 'iron_hide': 'hex', 'bastion': 'hex'}),
    'athletics': ('AT', ['swift', 'gale', 'tide', 'hoard', 'kin'], {
        'pack_mule': 'hoard', 'runner': 'swift', 'strong_back': 'hoard', 'steady_pace': 'swift', 'beast_of_burden': 'hoard',
        'haulers_stride': 'swift', 'swimmer': 'tide', 'soft_landing': 'gale', 'vaulter': 'gale', 'spring': 'gale', 'ox': 'hoard',
        'marathon': 'swift', 'freerunner': 'gale', 'featherweight': 'hex', 'bulls_back': 'hex'}),
    'survival': ('SV', ['wild', 'ember', 'tide', 'kin', 'deep'], {
        'gatherer': 'wild', 'field_cook': 'ember', 'quiet_approach': 'wild', 'skinner': 'hoard', 'stalker': 'keen', 'sixth_sense': 'deep',
        'iron_stomach': 'iron', 'campfire_rest': 'ember', 'pathfinder': 'swift', 'self_sufficient': 'wild', 'tracker': 'wild',
        'lay_of_the_land': 'kin', 'living_off_the_land': 'wild', 'wolfs_hunger': 'hex', 'wild_luck': 'hex'}),
    'smithing': ('SM', ['ember', 'iron', 'hoard', 'keen', 'kin'], {
        'tinker': 'ember', 'thrifty': 'hoard', 'light_hand': 'iron', 'riveted': 'iron', 'proofed': 'iron', 'shieldwright': 'warden',
        'forged': 'ember', 'salvager': 'hoard', 'keen_edge': 'keen', 'true_temper': 'ember', 'reforge': 'echo', 'masterwork': 'ember',
        'honing': 'keen', 'brittle_edge': 'hex', 'slapdash': 'hex'}),
    'stealth': ('SL', ['deep', 'keen', 'swift', 'wild', 'echo'], {
        'soft_step': 'swift', 'shadow': 'deep', 'quiet_feet': 'gale', 'cloak_of_dusk': 'deep', 'vanish': 'echo', 'silent_fall': 'gale',
        'ambusher': 'keen', 'hamstring': 'keen', 'unseen_blade': 'echo', 'poisoner': 'wild', 'ghost': 'deep', 'shadowstep': 'swift',
        'assassin': 'keen', 'skulker': 'hex', 'cowards_edge': 'hex'}),
}

# 9 × 9 glyphs, one per keyword: '#' ink, '.' clear. Drawn small and plain; the name always sits beside it.
GLYPH_ART = {
    'ember': ['....#....', '...##....', '...###...', '..####...', '..#####..', '.###.###.', '.##...##.', '.##...##.', '..#####..'],
    'deep': ['#########', '#.......#', '#.#####.#', '#.#...#.#', '#.#.#.#.#', '#.#...#.#', '#.#####.#', '#.......#', '#########'],
    'swift': ['.........', '#...#....', '.#...#...', '..#...#..', '...#...#.', '..#...#..', '.#...#...', '#...#....', '.........'],
    'brutal': ['.........', '.#######.', '.#######.', '.#######.', '....#....', '....#....', '....#....', '....#....', '.........'],
    'echo': ['.........', '..#...#..', '.#...#...', '#...#....', '#...#....', '#...#....', '.#...#...', '..#...#..', '.........'],
    'warden': ['.#######.', '.#.....#.', '.#.....#.', '.#.....#.', '.#.....#.', '..#...#..', '..#...#..', '...#.#...', '....#....'],
    'keen': ['....#....', '....#....', '...###...', '#########', '...###...', '....#....', '....#....', '.........', '.........'],
    'wild': ['....#....', '...###...', '..#.#.#..', '....#....', '.#..#..#.', '..#.#.#..', '...###...', '....#....', '....#....'],
    'iron': ['.........', '.#######.', '.#.....#.', '.#.###.#.', '.#.#.#.#.', '.#.###.#.', '.#.....#.', '.#######.', '.........'],
    'tide': ['.........', '.........', '.##...##.', '#..#.#..#', '.....#...', '.##...##.', '#..#.#..#', '.....#...', '.........'],
    'gale': ['.........', '.#####...', '......#..', '.######..', '.........', '.#######.', '........#', '.######..', '.........'],
    'hoard': ['.........', '...###...', '..#...#..', '.#######.', '.#.....#.', '.#.###.#.', '.#.....#.', '.#######.', '.........'],
    'hex': ['....#....', '...#.#...', '..#...#..', '.#######.', '..#...#..', '..#...#..', '.#.#.#.#.', '#...#...#', '.........'],
    'kin': ['.........', '.##...##.', '#..#.#..#', '#..#.#..#', '.##...##.', '.........', '#######..', '..#######', '.........'],
}


def comp(key):
    return {'translate': key}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent='\t', ensure_ascii=False)
        f.write('\n')


def main():
    lang = {}
    for sub in ('trait', 'keyword', 'card_skill', 'mythic', 'byname'):
        shutil.rmtree(os.path.join(DATA, sub), ignore_errors=True)
    for skill, traits in TRAITS.items():
        for (tid, name, desc, lo, hi, step, unit, kind, scope, effect, arsenal) in traits:
            key = 'mettle.trait.%s.%s' % (skill, tid)
            obj = {'skill': 'mettle:' + skill, 'kind': kind, 'scope': scope, 'min': lo, 'max': hi, 'step': step, 'unit': unit,
                   'effect': effect, 'name': comp(key), 'description': comp(key + '.description')}
            lang[key] = name
            lang[key + '.description'] = desc
            if arsenal:
                obj['arsenal'] = comp(key + '.arsenal')
                lang[key + '.arsenal'] = arsenal
            write(os.path.join(DATA, 'trait', skill, tid + '.json'), obj)
    for kid, (name, theme, sets) in KEYWORDS.items():
        key = 'mettle.keyword.' + kid
        lang[key] = name
        lang[key + '.theme'] = theme
        out = {}
        for tier, (desc, mods, behaviour) in sets.items():
            lang['%s.set.%d' % (key, tier)] = desc
            entry = {'description': comp('%s.set.%d' % (key, tier))}
            if mods:
                entry['effects'] = []
                for m in mods:
                    e = {'attribute': m[0], 'amount': m[1], 'operation': m[2]}
                    if len(m) > 3:
                        e['when'] = m[3]
                    entry['effects'].append(e)
            if behaviour:
                entry['behaviour'] = 'mettle:' + behaviour
            lang['%s.set.%d.summary' % (key, tier)] = SUMMARY[kid][tier // 2 - 1]
            entry['summary'] = comp('%s.set.%d.summary' % (key, tier))
            out[str(tier)] = entry
        obj = {'name': comp(key), 'theme': comp(key + '.theme'), 'colour': '#C9CCD1',
               'icon': 'mettle:textures/gui/keyword/%s.png' % kid, 'sets': out}
        if MOMENTUM.get(kid, 0) > 0:
            obj['momentum'] = MOMENTUM[kid]
        write(os.path.join(DATA, 'keyword', kid + '.json'), obj)
    for skill, (prefix, pool, natives) in SKILLS.items():
        write(os.path.join(DATA, 'card_skill', skill + '.json'), {
            'prefix': prefix, 'keyword_pool': ['mettle:' + k for k in pool],
            'natives': {'mettle:%s/%s' % (skill, p): 'mettle:' + k for p, k in sorted(natives.items())}})
    for mid, (name, source, trigger, skill, deed, effect, lore) in ALL_MYTHICS.items():
        key = 'mettle.mythic.' + mid
        if source == 'drawn' and FORGEABLE not in deed:
            deed += FORGEABLE
        lang[key + '.epithet'] = EPITHETS[mid]
        lang[key] = name
        lang[key + '.lore'] = lore
        lang[key + '.effect'] = effect
        lang[key + '.deed'] = deed
        obj = {'name': comp(key), 'lore': comp(key + '.lore'), 'effect': comp(key + '.effect'), 'deed': comp(key + '.deed'),
               'source': source, 'trigger': trigger, 'icon': 'mettle:textures/gui/mythic/%s.png' % mid, 'epithet': comp(key + '.epithet')}
        if skill:
            obj['skill'] = 'mettle:' + skill
        write(os.path.join(DATA, 'mythic', mid + '.json'), obj)
    byname(lang)
    write(LANG, dict(sorted(lang.items())))
    mythic_icons()
    glyphs()
    print('traits %d, keywords %d, skills %d' % (sum(len(t) for t in TRAITS.values()), len(KEYWORDS), len(SKILLS)))


ALL_MYTHICS = {**MYTHICS, **NEW_MYTHICS}


def byname(lang):
    """data/mettle/mettle/byname/{keywords,skills,pairs}.json: the words bynames are made of, all in lang."""
    words = {}
    for kid, (prefix, word) in BYNAME_KEYWORDS.items():
        key = 'mettle.byname.keyword.' + kid
        lang[key + '.prefix'] = prefix
        lang[key + '.word'] = word
        words['mettle:' + kid] = {'prefix': comp(key + '.prefix'), 'word': comp(key + '.word')}
    write(os.path.join(DATA, 'byname', 'keywords.json'), {'words': words})
    words = {}
    for sid, (suffix, noun, line) in BYNAME_SKILLS.items():
        key = 'mettle.byname.skill.' + sid
        lang[key + '.suffix'] = suffix
        lang[key + '.noun'] = noun
        lang[key + '.description'] = line
        words['mettle:' + sid] = {'suffix': comp(key + '.suffix'), 'noun': comp(key + '.noun'), 'description': comp(key + '.description')}
    write(os.path.join(DATA, 'byname', 'skills.json'), {'words': words})
    words = {}
    for (a, b), (noun, line) in BYNAME_PAIRS.items():
        key = 'mettle.byname.pair.%s.%s' % (a, b)
        lang[key] = noun
        lang[key + '.description'] = line
        words['mettle:%s+mettle:%s' % (a, b)] = {'noun': comp(key), 'description': comp(key + '.description')}
    write(os.path.join(DATA, 'byname', 'pairs.json'), {'words': words})


def glyphs():
    from PIL import Image
    os.makedirs(GLYPHS, exist_ok=True)
    for kid, rows in GLYPH_ART.items():
        img = Image.new('RGBA', (9, 9), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == '#':
                    img.putpixel((x, y), (255, 255, 255, 255))
        # White ink: the UI tints it with the keyword colour (one neutral colour for all).
        img.save(os.path.join(GLYPHS, kid + '.png'))


def mythic_icons():
    """A 16 x 16 sigil per Mythic on white-gold (the Mythic frame colour), a plain stand-in for the card art."""
    from PIL import Image
    out = os.path.join(RES, 'assets', 'mettle', 'textures', 'gui', 'mythic')
    os.makedirs(out, exist_ok=True)
    gold = [(242, 230, 194, 255), (214, 186, 118, 255), (150, 112, 48, 255)]
    numbered = list(enumerate(sorted(MYTHICS))) + [(len(MYTHICS) + i, m) for i, m in enumerate(sorted(NEW_MYTHICS))]
    for n, mid in numbered:
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for y in range(16):
            for x in range(16):
                d = abs(x - 7.5) + abs(y - 7.5)
                if d < 7.5:
                    img.putpixel((x, y), gold[0] if d < 4.5 else gold[1])
        seed = sum(ord(c) for c in mid) + n * 7
        for i in range(10):
            x = 5 + (seed * (i + 3)) % 6
            y = 5 + (seed * (i + 5) // 3) % 6
            img.putpixel((x, y), gold[2])
            img.putpixel((15 - x, y), gold[2])
        img.save(os.path.join(out, mid + '.png'))


if __name__ == '__main__':
    main()
