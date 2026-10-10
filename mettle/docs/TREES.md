# VOID Mettle — skill trees and special perks

> **Since 2026-10-10 the trees are the decks of the perk draft ([DRAFT.md](DRAFT.md)).** Nothing below is bought with
> points any more: every skill level (every other one from 7) deals three cards from the skill's tree and you keep one.
> The tree's shape still decides what can be dealt: tiers open at the same skill levels, a tier III path perk still
> needs a tier II of its path, a Mastery still needs two perks of its side and holding one removes the other two. The
> attribute gate is soft (it changes a card's odds), the costs only say how deep a perk is (and its rarity), and each
> skill's deck also holds two **cursed** cards (DRAFT.md §4). Feats, Synergies and Teachings are earned as before.

Supersedes the flat 4 × 3 grids in CHARACTER.md §4. The goal: **very powerful, deep, never complicated.** A player has
to understand only three ideas:

1. **Each skill has a tree.** Skill levels give points; points buy perks; some perks have ranks.
2. **Each tree ends in three Masteries. You can have one.** That choice defines what kind of miner, archer or smith
   you are.
3. **Some perks can't be bought, only earned:** **Feats** (do something remarkable), **Synergies** (master two
   skills that belong together) and **Teachings** (learned from a master or a rare tome).

Everything else (gates, costs, paths) is shown on the tree itself, so nobody has to remember it.

---

## 1. The shape of every tree

```
                 ┌─────────── MASTERY · pick one of three ───────────┐      Tier IV · skill 18 · attribute 14 · 5 points
                 │   [A-side mastery]   [either]   [B-side mastery]  │      needs 2 perks from its side (or any 2 for the middle one)
                 └───────────────────────────────────────────────────┘
        PATH A  (an identity, e.g. "Delver")        PATH B  (another, e.g. "Excavator")
        [A3] [A4]                                    [B3] [B4]               Tier III · skill 12 · attribute 10 · 3 points each
          │    │                                       │    │                (needs A1 or A2 / B1 or B2)
        [A1] [A2]                                    [B1] [B2]               Tier II · skill 6 · attribute 6 · 2 points each
                 └──────────────── ROOTS ────────────────┘
                     [Root 1 ●●●]        [Root 2 ●●●]                        Tier I · skill 2 · 1 point per rank, 3 ranks
```

- **Roots** are ranked: three small, felt steps each (+10 / 20 / 30 %), cheap, and the first thing a new player
  buys. They make early levels feel good.
- **Two Paths**, each with a name and an identity. A path perk in tier III needs one tier II perk of the same path.
  That is the only line on the tree.
- **Masteries are the "wow".** Each changes how the skill plays (vein mining, felling whole trees, a spinning sweep,
  a three-arrow volley). Exactly one per skill. The left one leans on Path A, the right one on Path B, and the
  middle one on any two perks.
- **Points.** One per skill level (20 at level 20). A full tree is roots 6 + paths 20 + mastery 5 = **31**, so every
  player chooses about two thirds of it. Feats, Synergies and Teachings never cost points.
- **Respec.** Free below character level 5; after that a Draught of Forgetting brewed for the skill (CHARACTER.md §6).
  It refunds that tree's points and the Mastery choice. Earned perks stay.

## 2. Earned perks

| Kind | How you get it | How it shows |
|---|---|---|
| **Feat** | Doing something remarkable, tracked quietly (mine 64 diamonds, hit a target 60 blocks away, survive on half a heart nine times). Two or three per skill, plus a few general ones. | Hidden on the tree as a silhouette with a one-line hint until earned, then a toast: "Feat earned · Diamond Hands". |
| **Synergy** | Two related skills both reach level 10 (Mining + Smithing, Blades + Defense…). About eight in wave 1. | Shown between the two trees as a locked link: "Reach Mining 10 and Smithing 10". |
| **Teaching** | A Hearth master trainer (city tier) teaches it for coin, or a rare tome found in an Expanse landmark's hidden chest. One per skill. | A gold-edged node beside the roots; "Taught by a master miner, or found in *The Delver's Almanac*". |

Earned perks are as strong as tier III perks, sometimes stronger, and they are the stories players tell.

## 3. Mining (Strength) — the reference tree

**Roots** (1 point per rank)

| Perk | Ranks |
|---|---|
| **Quarryman** | Stone-like blocks mine 10 / 20 / 30 % faster. |
| **Prospector** | Ores have a 5 / 10 / 15 % chance to drop one extra. |

**Path A · Delver** (underground, staying alive)

| Tier | Perk | What it does |
|---|---|---|
| II | **Sure Footing** | Mining while climbing, swimming or in the air is only half as slow. |
| II | **Hard Hat** | Falling blocks and dripstone don't hurt you; +2 armour below y 40. |
| III | **Lantern Eyes** | In deep darkness a faint light surrounds you that only you can see. |
| III | **Ore Sense** | Sneaking with a pickaxe, ore near the block you look at gives a soft chime. |

**Path B · Excavator** (throughput and hauling)

| Tier | Perk | What it does |
|---|---|---|
| II | **Rock Hauler** | Stone, ore, raw metal and ingots weigh 40 % less for you. |
| II | **Tunneler** | Sneak-mining with a pickaxe breaks a 1 × 2 passage. |
| III | **Strike the Seam** | The first ore you break in each vein drops double. |
| III | **Gem Cutter** | 10 % chance of an extra diamond, emerald, lapis, quartz or amethyst. |

**Masteries** (pick one)

| Mastery | Needs | What it does |
|---|---|---|
| **Deep Delver** | 2 Delver perks | Below y 0: take 30 % less damage, mine 25 % faster, and never trigger cave-ins. |
| **Motherlode** | any 2 path perks | Sneak-mine an ore to break its whole vein (up to 24 blocks); each block costs durability. |
| **Mountain Breaker** | 2 Excavator perks | Sneak-mining stone breaks 3 × 3 at triple durability cost. |

**Feats**

| Feat | Hint | Earned by | Grants |
|---|---|---|---|
| **Diamond Hands** | "Some miners never let go." | Mining 64 diamond ore | Diamond ore drops one extra 25 % of the time. |
| **Down There** | "Spend long enough in the deep and it spends time in you." | 3 in-game days (72 000 ticks) below y 0 | You see a little further in the dark underground (stacks with Lantern Eyes). |

**Synergy**

| Synergy | Skills | Grants |
|---|---|---|
| **Forgemaster** | Mining 10 + Smithing 10 | Raw iron, gold and copper you mine have a 15 % chance to drop as an ingot. |

**Teaching**

| Teaching | From | Grants |
|---|---|---|
| **Stonesense** | A city's master miner, or *The Delver's Almanac* (Expanse cavern landmarks) | Hold sneak with a pickaxe for 2 s: a low hum pans towards the nearest diamond ore within 24 blocks. |

## 4. Blades (Agility)

The seven trees below copy Mining's shape. Their tables end in a **Hook** column: a short note on how the perk is
built (`attr …` = a vanilla attribute modifier, toggled by the `when` condition named; `weight mod` / `band mod` =
burden effects; everything else is a named behaviour on that hook). Every perk works without Hearth and Expanse.

**Roots** (1 point per rank)

| Perk | Ranks | Hook |
|---|---|---|
| **Fencer** | Swords and spears swing 5 / 10 / 15 % faster. | attr attack_speed (holding) |
| **Quick Draw** | Your first blade hit on a mob that isn't targeting you deals +20 / 40 / 60 %. | on damage |

**Path A · Duelist** (one foe, reach, timing)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Measure** | Swords and spears reach half a block further. | attr entity_interaction_range |
| II | **Disarm** | Critical blade hits knock the weapon out of a mob's hands one time in five. | on crit |
| III | **Momentum** | Each hit on the same target within 2 s of the last adds +10 % damage, up to +40 %. | on damage |
| III | **Single Combat** | With only one hostile within 8 blocks, you deal 20 % more to it and take 20 % less from it. | on damage |

**Path B · Skirmisher** (on the move, many foes)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Light Feet** | Attacking no longer breaks your sprint. | sprint keep |
| II | **Wide Arc** | Your sweeps hit everything around the target for 75 % of your hit. | attr sweeping_damage_ratio |
| III | **Bleed** | Critical hits open a wound that deals 4 damage over 4 s, stacking up to three times. | on crit |
| III | **Cut and Run** | Every kill gives you +20 % speed for 3 s and a fully charged next swing. | on kill |

**Masteries** (pick one)

| Mastery | Needs | What it does | Hook |
|---|---|---|---|
| **Duelist** | 2 Duelist perks | Use a sword (right-click) to parry for 0.4 s: a melee hit caught in it is cancelled, the attacker staggers for 1 s, and your next hit is a critical. | use item, allow damage |
| **Whirlwind** | any 2 path perks | Sneak and attack to spin, hitting everything within 3 blocks for full damage (8 s cooldown). | on attack |
| **Executioner** | 2 Skirmisher perks | A blade hit that leaves a mob below 25 % health kills it outright (bosses and players take +50 % instead), and each execution gives Speed II for 2 s. | on damage |

**Feats**

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **Unbroken** | "Ten down, and not a scratch on you." | Killing 10 hostiles in a row with a blade without taking damage | Your first blade hit after 5 s without taking damage is always a critical. | streak counter |
| **Giant Slayer** | "The bigger they are…" | Killing 25 large mobs (ravagers, hoglins, zoglins, endermen, polar bears, wardens) with a blade | Your blade hits deal +25 % to large mobs. | kill counter |

**Teaching**

| Teaching | From | Grants | Hook |
|---|---|---|---|
| **Master Strike** | A city's guard, or *The Verses of the Long Edge* (Wisteria manor, the wine cellar) | Attack within 0.5 s of blocking or parrying a hit and your counter is a critical that ignores armour. | on block, on damage |

**Synergies:** **Sword and Board** with Defense, **Weapon Master** with Heavy Weapons (§11).

## 5. Heavy Weapons (Strength)

**Roots** (1 point per rank)

| Perk | Ranks | Hook |
|---|---|---|
| **Heft** | Axes, maces and tridents hit 5 / 10 / 15 % harder. | attr attack_damage (holding) |
| **Feller** | Axes chop wood 15 / 30 / 45 % faster. | break speed |

**Path A · Woodsman** (the axe, in the forest and in a fight)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Cleave** | Axe hits spill 30 % of their damage onto every enemy beside the target. | on damage |
| II | **Log Hauler** | Logs, planks and stripped wood weigh 40 % less for you. | weight mod |
| III | **Rhythm of the Axe** | Each log you chop within 3 s of the last chops 15 % faster, up to +60 %. | break speed |
| III | **Throwing Axe** | Sneak and use an axe in the air to throw it up to 16 blocks for a full-strength hit; it lies where it lands for you to pick up. | use item, thrown entity |

**Path B · Crusher** (the mace and the heavy blow)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Armour Crusher** | Heavy hits ignore 25 % of the target's armour. | damage hook |
| II | **Bone Breaker** | Critical heavy hits slow the target by 40 % for 3 s. | on crit |
| III | **Iron Grip** | While you hold a heavy weapon nothing knocks you back, and you gain +2 armour toughness. | attr knockback_resistance (holding) |
| III | **Stagger** | Fully charged heavy hits interrupt the target: a creeper stops fusing, a skeleton loses its draw, and any mob misses its next attack. | on damage |

**Masteries** (pick one)

| Mastery | Needs | What it does | Hook |
|---|---|---|---|
| **Timber** | 2 Woodsman perks | Sneak-chop the base of a tree to fell the whole trunk (up to 64 logs); each log costs durability. | block break |
| **Berserker** | any 2 path perks | Below 40 % health you can't be slowed or knocked back, and your heavy hits deal +40 % and heal you for a fifth of the damage. | on damage, attr |
| **Earthshaker** | 2 Crusher perks | A mace smash, or a sneak-attack with any heavy weapon, slams the ground: every enemy within 4 blocks is thrown back and staggered for 1 s (10 s cooldown). | on attack |

**Feats**

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **Headhunter** | "Some trophies are taken, not found." | Killing 100 hostiles with an axe | Axe kills drop the mob's head one time in five. | kill counter |
| **Old Growth** | "A forest's worth of axe-work." | Chopping 2 000 natural logs (placed logs don't count) | Axes take no durability damage from chopping wood. | block counter |

**Teaching**

| Teaching | From | Grants | Hook |
|---|---|---|---|
| **Overhead Blow** | A city's forester, or *The Feller's Rule* (Logging camp, the payroll strongroom) | Jump crits with an axe or mace deal double damage instead of one and a half, and stagger the target for 0.5 s. | on crit |

**Synergies:** **Stonebreaker** with Mining, **Weapon Master** with Blades (§11).

## 6. Marksmanship (Agility)

**Roots** (1 point per rank)

| Perk | Ranks | Hook |
|---|---|---|
| **Steady Aim** | Bows draw 10 / 20 / 30 % faster. | draw time |
| **Retrieval** | Arrows that hit a mob drop back where it dies 15 / 30 / 45 % of the time. | on arrow hit |

**Path A · Sharpshooter** (one arrow, well placed)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Long Shot** | +5 % damage for every 5 blocks beyond 15, up to +50 %. | on damage |
| II | **Hunter's Mark** | Mobs you hit are outlined for you alone for 6 s. | personal glow |
| III | **Steady Breath** | Shots you loose sneaking at full draw fly 30 % faster and perfectly straight. | on shoot |
| III | **Pinning Shot** | Fully drawn hits slow the target by half for 2 s. | on arrow hit |

**Path B · Quickshot** (many arrows, fast)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Quick Nock** | A shot loosed within 1 s of your last one draws 40 % faster. | draw time |
| II | **Fletcher** | Arrow recipes make 6 instead of 4. | craft hook |
| III | **Double Nock** | One shot in five looses a second arrow beside the first, for free. | on shoot |
| III | **Quiverwise** | Crossbows load 40 % faster, and arrows taken from your quiver are kept one time in four. | charge time, quiver draw |

**Masteries** (pick one)

| Mastery | Needs | What it does | Hook |
|---|---|---|---|
| **Deadeye** | 2 Sharpshooter perks | Arrows that strike a mob's head deal double damage and always crit, and a headshot kill returns the arrow. | on arrow hit |
| **Volley** | any 2 path perks | Release a fully drawn bow while sneaking to loose three arrows in a spread (uses three). | on shoot |
| **Arrowstorm** | 2 Quickshot perks | Keep holding a fully drawn bow and it looses an arrow every 0.5 s at three-quarter power until you let go or run dry. | use tick |

**Feats**

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **Sixty Paces** | "Some shots are talked about for years." | Hitting a living target from 60 or more blocks away | Your arrows fly 20 % faster, so they drop less at range. | on arrow hit |
| **Wingshot** | "Look up." | Killing 25 phantoms, blazes, ghasts or breezes with arrows | Arrows deal +50 % to flying mobs. | kill counter |

**Teaching**

| Teaching | From | Grants | Hook |
|---|---|---|---|
| **Steppe Draw** | A city's hunter, or *The Steppe Bow* (Caravanserai, the cistern down the well) | Bows reach full power at three-quarter draw, and shots loosed from the saddle don't spread. | draw time, on shoot |

**Synergies:** **Huntsman** with Survival, **Leaping Shot** with Athletics (§11).

## 7. Defense (Vitality)

**Roots** (1 point per rank)

| Perk | Ranks | Hook |
|---|---|---|
| **Thick Skin** | +1 / 2 / 3 armour. | attr armor |
| **Armour Training** | Worn armour weighs 10 / 20 / 30 % less for you. | weight mod |

**Path A · Shieldbearer** (the shield, and the people behind it)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Shield Up** | Your shield raises instantly, and nothing knocks you back while you block. | block delay, attr |
| II | **Shield Bash** | Hit a mob within 0.5 s of lowering your shield to shove it back 3 blocks and daze it for 1 s. | use release, on damage |
| III | **Perfect Block** | A hit that lands within 0.25 s of raising your shield is reflected: the attacker takes half of it. | on block |
| III | **Bulwark** | Your raised shield covers your sides too: it blocks anything not coming from behind you. | block angle |

**Path B · Ironclad** (armour, and lasting the fight)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Mender** | Armour and shields you wear lose durability 30 % more slowly. | item damage |
| II | **Second Wind** | Once every 5 minutes, dropping below 4 hearts gives you Regeneration II for 5 s. | on damage taken |
| III | **Hold the Line** | Every hit you take while standing still adds +1 armour for 5 s, up to +5. | on damage taken |
| III | **Unbowed** | In full armour, poison, wither, slowness and weakness on you last half as long. | effect hook |

**Masteries** (pick one)

| Mastery | Needs | What it does | Hook |
|---|---|---|---|
| **Guardian** | 2 Shieldbearer perks | While your shield is up, allies within 4 blocks (players, tamed animals, villagers, Hearth folk) take 40 % less damage, and hostiles within 8 blocks that attack them turn on you instead. | on block, target event |
| **Last Stand** | any 2 path perks | Once every 10 minutes, a killing blow leaves you at 1 heart with 4 s of Resistance IV instead. | allow death |
| **Juggernaut** | 2 Ironclad perks | In full armour nothing knocks you back, and sprinting into a mob bowls it over: 4 damage, thrown 3 blocks, dazed for 1 s. | attr, collision tick |

**Feats**

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **Pincushion** | "A good shield keeps what it catches." | Blocking 300 arrows or other projectiles with a shield | Half the arrows you block end up in your inventory. | on block |
| **Still Standing** | "That should have killed you." | Taking a single hit of 15 damage or more (7½ hearts) and living | The first hit over 10 damage you take each minute is reduced by a third. | on damage taken |

**Teaching**

| Teaching | From | Grants | Hook |
|---|---|---|---|
| **Roll with the Blow** | A city's guard captain, or *The Citadel Drill* (Frostwatch Citadel, the ice cellar) | A quarter of every hit you take is dealt over the next 4 s instead of all at once. | damage defer |

**Synergies:** **Sword and Board** with Blades, **Marching Order** with Athletics, **Battle-Worn** with Smithing (§11).

## 8. Athletics (Vitality)

**Roots** (1 point per rank)

| Perk | Ranks | Hook |
|---|---|---|
| **Pack Mule** | +5 / 8 / 12 kg carry capacity. | attr carry_capacity |
| **Runner** | You sprint 3 / 6 / 9 % faster. | attr movement_speed (sprinting) |

**Path A · Porter** (the heavy load)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Strong Back** | You only become Burdened at 85 % of capacity. | band mod |
| II | **Steady Pace** | Being Burdened slows you by only 5 % and costs no extra hunger. | band mod |
| III | **Beast of Burden** | +22 kg, and a heavy load never pins you: Overburdened is the worst it gets. | attr, band mod |
| III | **Hauler's Stride** | You can still sprint while Overburdened, with half the usual sprint speed. | sprint gate |

**Path B · Freerunner** (water, height, ground)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Swimmer** | You swim 30 % faster and hold your breath 50 % longer. | attr water_movement_efficiency, oxygen_bonus |
| II | **Soft Landing** | You fall 3 more blocks before taking damage. | attr safe_fall_distance |
| III | **Vaulter** | While sprinting you step up full blocks without jumping. | attr step_height (sprinting) |
| III | **Spring** | Sprint-jumps carry you half a block higher and a block further. | attr jump_strength (sprinting) |

**Masteries** (pick one)

| Mastery | Needs | What it does | Hook |
|---|---|---|---|
| **Ox** | 2 Porter perks | +38 kg, load slows you only half as much, and you can sneak-use an empty hand on a chest or barrel to pick it up whole, contents and all (it weighs what's inside). | attr, band mod, use block |
| **Marathon** | any 2 path perks | You can sprint at any hunger level, and sprinting costs half the hunger. | sprint gate, exhaustion |
| **Freerunner** | 2 Freerunner perks | Hold jump against a wall to scramble up it, up to 2 blocks above your jump, once before you land again. | input packet |

**Feats**

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **Ironlung** | "Some of us were born in the water." | Swimming 2 km under water | +15 s of breath, and you mine at full speed under water. | stat counter |
| **Mountain Goat** | "The view is better from up there." | Standing above y 220 in 10 places at least 500 blocks apart | You fall 3 more blocks before taking damage (stacks with Soft Landing). | place counter |

**Teaching**

| Teaching | From | Grants | Hook |
|---|---|---|---|
| **Courier's Stride** | A city's courier, or *Letters of a King's Runner* (Palm harbour, the smugglers' hold) | After 20 s of unbroken sprinting you find your stride: +15 % speed and no hunger cost until you stop. | sprint timer |

**Synergies:** **Leaping Shot** with Marksmanship, **Marching Order** with Defense, **Trailblazer** with Survival (§11).

## 9. Survival (Vitality)

**Roots** (1 point per rank)

| Perk | Ranks | Hook |
|---|---|---|
| **Gatherer** | Berries, herbs and wild plants give a bonus item 10 / 20 / 30 % of the time. | loot hook |
| **Field Cook** | Cooked food fills you 10 / 20 / 30 % more. | eat hook |

**Path A · Hunter** (game, the stalk and the kill)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Quiet Approach** | While you sneak, animals (Expanse elk and deer too) don't flee until you're within 4 blocks. | AI goal |
| II | **Skinner** | Animals you kill drop one more meat and one more leather, hide, wool or feather. | loot hook |
| III | **Stalker** | A sneak attack on an animal that hasn't noticed you kills it outright. | on damage |
| III | **Sixth Sense** | A quickening heartbeat, heard only by you, warns you when a hostile within 16 blocks starts hunting you. | target event |

**Path B · Forager** (living off the land)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Iron Stomach** | Raw meat, rotten flesh and spider eyes never poison you or give you Hunger. | eat hook |
| II | **Campfire Rest** | Standing still beside a lit campfire heals a heart every 4 s. | tick check |
| III | **Pathfinder** | You move 15 % faster on paths and roads, and berry bushes no longer hurt or slow you. | attr (on path), block hook |
| III | **Self-Sufficient** | You get hungry 25 % more slowly. | exhaustion |

**Masteries** (pick one)

| Mastery | Needs | What it does | Hook |
|---|---|---|---|
| **Tracker** | 2 Hunter perks | Sneak still for 2 s to read the ground: the trail of the nearest wild animal within 96 blocks shows as faint prints only you can see, for 30 s. | personal particles |
| **Lay of the Land** | any 2 path perks | Every place you discover marks the nearest landmark you haven't found on your locator bar until you reach it (vanilla structures when Expanse is absent). | discovery, waypoint |
| **Living off the Land** | 2 Forager perks | Grass, ferns, bushes and leaves you break turn up something useful one time in six (berries, seeds, apples, mushrooms, string, herbs), and wild food fills you double. | loot hook, eat hook |

**Feats**

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **Weatherwise** | "Every sky has its own weather." | Visiting 40 different biomes | A far-off rumble warns you 2 minutes before rain or a storm arrives. | biome counter |
| **Big Game** | "The deer are not the only ones who learn." | Killing 30 big game animals (elk, deer, mammoths, polar bears, hoglins, goats) | Big game you kill drops double meat and hide. | kill counter |

**Teaching**

| Teaching | From | Grants | Hook |
|---|---|---|---|
| **Poultice** | A city's healer, or *The Monks' Herbal* (Cloud monastery, the hidden study) | Sneak-use three herbs or flowers to bind a wound: heal 4 hearts over 8 s and cure poison (30 s cooldown). | use item |

**Synergies:** **Huntsman** with Marksmanship, **Trailblazer** with Athletics (§11).

## 10. Smithing (Strength)

**Roots** (1 point per rank)

| Perk | Ranks | Hook |
|---|---|---|
| **Tinker** | Anvil repairs and renames cost 1 / 2 / 3 levels less (never below 1). | anvil hook |
| **Thrifty** | A recipe with ingots gives one back 5 / 10 / 15 % of the time. | craft hook |

**Path A · Armourer** (armour and shields)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Light Hand** | Armour you craft weighs 25 % less. | craft stamp, weight mod |
| II | **Riveted** | Armour and shields you craft last 30 % longer. | craft stamp |
| III | **Proofed** | Each piece of armour you craft gives +1 armour (+4 for a full set). | craft stamp |
| III | **Shieldwright** | Shields you craft raise instantly and wear half as fast. | craft stamp |

**Path B · Toolwright** (tools and weapons)

| Tier | Perk | What it does | Hook |
|---|---|---|---|
| II | **Forged** | Tools and weapons you craft last 25 % longer and carry "Forged by <you>". | craft stamp |
| II | **Salvager** | Grinding down iron, gold or copper gear gives back up to half its metal as nuggets. | grindstone hook |
| III | **Keen Edge** | Swords, spears, axes and maces you craft deal +1 damage. | craft stamp |
| III | **True Temper** | Pickaxes, axes, shovels and hoes you craft dig 15 % faster. | craft stamp (mining_efficiency) |

**Masteries** (pick one)

| Mastery | Needs | What it does | Hook |
|---|---|---|---|
| **Reforge** | 2 Armourer perks | At a smithing table, armour plus a feather, an ingot or an amethyst shard is reforged Light (half weight), Sturdy (+50 % durability) or Warded (+1 toughness). | smithing hook |
| **Masterwork** | any 2 path perks | One in ten things you craft is a Masterwork: +1 to its main stat, a third more durability and a gold name. | craft stamp |
| **Honing** | 2 Toolwright perks | Put a weapon or tool alone in a grindstone to hone it, keeping its enchantments: weapons deal +2 damage for 60 hits, tools dig 30 % faster for 300 blocks. | grindstone hook |

**Feats**

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **A Hundred Hammers** | "A smith is made at the anvil, one piece at a time." | Crafting 100 iron-or-better tools, weapons or armour pieces | Anvil repairs never add the "prior work" cost. | craft counter |
| **Black Iron** | "The hardest metal asks the most of you." | Making 9 netherite upgrades at a smithing table | Netherite upgrades you make return the template one time in three. | smithing hook |

**Teaching**

| Teaching | From | Grants | Hook |
|---|---|---|---|
| **Folded Steel** | A city's smith, or *A Treatise on Folded Steel* (Karst temple, the scroll room) | Iron tools, weapons and armour you craft last twice as long. | craft stamp |

**Synergies:** **Forgemaster** with Mining, **Battle-Worn** with Defense (§11).

## 11. Synergies

Each one unlocks by itself the moment both skills reach level 10, costs nothing and survives a respec. Every wave-1
skill is in at least one.

| Synergy | Skills | Grants | Hook |
|---|---|---|---|
| **Forgemaster** | Mining 10 + Smithing 10 | Raw iron, gold and copper you mine have a 15 % chance to drop as an ingot. | block break |
| **Stonebreaker** | Mining 10 + Heavy Weapons 10 | A mace breaks stone, ore and deepslate as fast as an iron pickaxe, with the same drops. | break speed |
| **Sword and Board** | Blades 10 + Defense 10 | Blocking a hit with your shield fully charges your next blade swing. | on block |
| **Weapon Master** | Blades 10 + Heavy Weapons 10 | Switching between a blade and a heavy weapon makes your first hit with the new one a critical (once every 5 s). | slot change, on damage |
| **Huntsman** | Marksmanship 10 + Survival 10 | An arrow that kills an animal always comes back, and the kill drops one extra meat and hide. | on kill |
| **Leaping Shot** | Marksmanship 10 + Athletics 10 | Arrows you loose in mid-air are always critical. | on shoot |
| **Marching Order** | Defense 10 + Athletics 10 | Worn armour counts only half towards your load. | weight mod |
| **Trailblazer** | Survival 10 + Athletics 10 | Discovering a new biome or place gives you Speed I and Regeneration I for 60 s. | discovery |
| **Battle-Worn** | Smithing 10 + Defense 10 | Armour you wear never breaks: at 1 durability it stops wearing and protects at half strength until repaired. | item damage |

## 12. General feats

Not tied to one tree. They show in the character screen's feats strip (silhouette and hint until earned), and they
grant something everyone can use.

| Feat | Hint | Earned by | Grants | Hook |
|---|---|---|---|---|
| **Nine Lives** | "Not today. Not yet." | Dropping to half a heart or less and living, nine times (at most once a minute) | Below 3 hearts you move 20 % faster. | health check |
| **Seen It All** | "There is always one more hill." | Discovering all 8 Expanse landmarks (without Expanse: 8 kinds of vanilla structure) | +1 attribute point. | discovery |
| **Pack Horse** | "Nobody asked you to carry all that." | Walking 1 km on foot while Overburdened or Pinned | +8 kg carry capacity. | band distance |
| **Seasoned** | "Thirty days, and still breathing." | Living through 30 in-game days (720 000 ticks) without dying | +1 heart. | life timer |
| **Jack of All Trades** | "A little of everything goes a long way." | Reaching level 5 in all eight wave-1 skills | Skills below level 10 gain XP 25 % faster. | level check |
