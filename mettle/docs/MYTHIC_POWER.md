# VOID Mettle — Mythic power: ceilings that grow, fewer and stronger cards, hybrids, loadouts, rebirth

**Status: design (2026-10-21), branch `mettle-mythic` (from `mettle` 0.4.0). Nothing in the game changes yet.** Every
number below comes from the simulator `MythicPowerReportTest` (§16), which prototypes these rules in the test sources
beside today's, as `BuildFeelReportTest` did. `./gradlew test --tests '*MythicPowerReportTest' --offline` asserts the
curve on every build; add `-Dmettle.balance=true` for the slot study and `build/reports/mettle-mythic-power.md`.
Builds on [CARDS.md](CARDS.md), [BUILD_FEEL.md](BUILD_FEEL.md), [BALANCE.md](BALANCE.md), [TREES.md](TREES.md) and
[CHARACTER.md](CHARACTER.md); where they disagree, this doc wins once its wave ships (§17). Aligned with the action-RPG
master design on branch `design-arpg` (`docs/arpg/OVERVIEW.md` §5.1; its CONTRACTS.md was not written yet when this
was, see §1.2). Screens go to Figma first (§15).

**What the user asked for.** The Mythic/ARPG direction: "max builds around 2x+ vanilla", because the world is getting
much harder enemies (Bestiary: smarter mobs, elites, bosses, stamina-based combat actions). "Don't you also kind of
think that the cards are weak?" Then: "Definitely want to retune and have fewer but stronger slots"; a **Rebirth**
(a full character redo) and **Loadouts**; and **free-form builds with depth earned by commitment**: no classes, real
power from one or two main disciplines plus supports, hybrids as first-class builds, and a viable generalist, the
**Wayfarer**.

A player has to know eight things:

1. **Every card is felt.** No card does less than a change you notice (about 15 %, or a new thing you can do).
2. **Your ceiling grows with you.** Early characters are modestly stronger than vanilla; a committed late build is
   about **2.3× vanilla** in a fight, and the top needs a **Mythic and a top set**.
3. **The last part of every ceiling is earned in the fight.** Always-on numbers fill only the first part; conditions
   met add more; landing techniques (parry, dodge, charged and heavy attacks, spells) reaches the top.
4. **Fewer, stronger slots.** Two to five cards a skill (six with Wits 12). Every slot is a real choice.
5. **Sets are identity.** 2 cards: a real perk. 3: a mechanic change. 5 (from two decks): a build-defining ability.
6. **Commit and you go deeper; pair and you go wider.** Each damage kind's ceiling follows the skill that governs it.
   Two disciplines joined by a **bond** make a hybrid as strong overall as a specialist.
7. **Loadouts**: two or three saved setups (combat, gathering), switched at a bed or a shrine.
8. **Rebirth**: a rare rite that redoes your character (decks, attributes, Focus, background) and keeps what you
   earned (skill levels, cards, Mythics).

---

## 0. What is wrong today (measured)

| Problem | Measured (simulator §1, §3, §7) |
|---|---|
| Cards don't feel like anything early | Of 18 roots, **3** are felt at rank I (Fencer I swings 5 % faster, Heft I hits 5 % harder, Runner I sprints 3 % faster). In real hands at skill 2–5, **35 %** of dealt cards are felt by their perk; 83 % at 6–11, 90 % from 12. |
| Traits are small | **110 of 123** percent traits can roll under 8 % (broad) or 15 % (narrow); a range's mean is 11.7 %. |
| Set 2-tiers are flavour | **1 of 14** 2-tiers is felt (Swift 2: +3 % speed; Brutal 2: +4 % melee; Warden 2: +1 armour). |
| Power is spread thin | A focused character slots **44** cards at mains 20; a character with every skill at 20, **80** (90+ with ten skills and Wits 14). |
| The ceiling is the same at level 5 and 50 | The §14 ceilings are flat (movement +35 %, melee +60 %, damage taken −45 %...), and they clamp only what **cards** add: perks and attributes sit outside, so the real top end is uneven (a warrior today models at 2.09×, a rogue 1.93×) and unbounded in principle. |
| Nothing uses the coming combat | No card touches dodge, parry, charged or heavy attacks, stamina or mana. |

---

## 1. Measuring power

### 1.1 Fight power

The simulator scores a build as multipliers on a vanilla player **with the same gear**:

- **Offense** = (1 + damage bonuses) × (1 + attack / draw / cast speed), per damage kind (melee, ranged, spell and
  status), with conditional effects weighted by how often they are on.
- **Toughness** = (20 + health) ÷ 20 ÷ the share of a hit you take, × (1 + kiting × movement) for builds that fight at
  range or on the move.
- **Fight power** = √(offense × toughness): twice as strong means killing twice as fast and lasting twice as long
  together.
- Two ways to play: **plain** (swinging, no techniques; conditions at a casual rate) and **skilled** (techniques
  landed at a practised rate). Uptimes per source are in the simulator's archetype tables.

**Areas** (§10): melee, ranged, spell, **ambush** (first strikes on an unaware foe), **boss** (the boss rules of §9),
**road** (movement, digging and gathering, extra drops, carry, hunger and healing: exploring, gathering, surviving).
**General play** = combat^0.45 × boss^0.15 × road^0.25 × ambush^0.15, where combat assumes 30 % of foes resist your
best damage kind (Fray's resistances) and take 60 % of it, so a second kind helps.

### 1.2 Assumptions (stated, until Bestiary's and Fray's docs fix them)

`docs/arpg/OVERVIEW.md` §5.1 (read 2026-10-21) gives the targets: apex **2.3–2.6× vanilla best in damage**, about
**2× staying power**, max health 20 / 26 / 32 / 38 / 40 by stage, stamina 100 → 180, mana 40 → 240; Fray owns stamina,
mana, poise, damage kinds, resistances and statuses; Bestiary the actions and the enemies; Alembic brews and spells;
Arsenal weapons, foci and runes. Its COMBAT.md, BESTIARY.md and CONTRACTS.md were not written yet, so the simulator
assumes:

| | Assumed |
|---|---|
| Stamina (Fray) | 100, regenerating 25 a second; a dodge costs 25 and can be repeated every 0.4 s |
| Mana (Fray) | 40 at the start, the overview's pools |
| A boss | 600 health, armour 10; a 14-damage hit every 2.5 s and a telegraphed 24-damage slam every 12 s |
| An elite | 3× a mob's health, 1.5× its damage (√4.5 = **2.12×** a vanilla player's fight power to feel like a vanilla fight) |
| Geared | armour 20, toughness 8; 8 damage a second on the boss (75 s for a vanilla player, who lasts 7 s face-tanking) |
| Foes resisting your kind | 30 %, taking 60 % of it |

When Bestiary's numbers land, the simulator's constants (`BOSS_*`, `STAMINA*`, `RESIST_*`) change and the asserts say
whether the curve still holds.

---

## 2. Floors: every card felt

### 2.1 Felt steps

A change is **felt** when it reaches its stat's felt step: damage, action speed, chances, chop and dig speed 15 %;
movement 8 %; armour 3 points; health 2 points; carry 10 kg; damage taken 10 %; anvil levels 2. A perk that adds
something you can do (a parry, a dash, a vein mine) is felt by being there.

### 2.2 Roots (rank = rarity: Common I, Rare II, Epic III)

| Root | Today I / II / III | After |
|---|---|---|
| Pack Mule (carry) | 5 / 8 / 12 kg | **10 / 16 / 24 kg** |
| Runner (sprint speed) | 3 / 6 / 9 % | **8 / 12 / 16 %** |
| Fencer (swing speed) | 5 / 10 / 15 % | **15 / 22 / 30 %** |
| Quick Draw (first hit) | 20 / 40 / 60 % | 30 / 50 / 70 % |
| Armour Training (armour weight) | 10 / 20 / 30 % | 25 / 40 / 55 % |
| Thick Skin (armour) | 1 / 2 / 3 | **3 / 4 / 5** |
| Feller (chop speed) | 15 / 30 / 45 % | 25 / 45 / 65 % |
| Heft (heavy damage) | 5 / 10 / 15 % | **15 / 22 / 30 %** |
| Retrieval (arrows back) | 15 / 30 / 45 % | 25 / 40 / 55 % |
| Steady Aim (draw speed) | 10 / 20 / 30 % | 20 / 30 / 40 % |
| Prospector (extra ore) | 5 / 10 / 15 % | 15 / 25 / 35 % |
| Quarryman (stone dig speed) | 10 / 20 / 30 % | 20 / 35 / 50 % |
| Thrifty (ingot back) | 5 / 10 / 15 % | 15 / 25 / 35 % |
| Tinker (anvil levels off) | 1 / 2 / 3 | 2 / 3 / 4 |
| Shadow (noticed at) | 90 / 80 / 70 % | 80 / 70 / 60 % |
| Soft Step, Field Cook, Gatherer | 10 / 20 / 30 % | 20 / 35 / 50 % |

Roots felt at rank I: **3 → 18 of 18**. Cards dealt at skill 2–5 felt by their perk: **35 % → 100 %** (all bands 100 %).

### 2.3 Traits

- **Ranges**: percent traits roll from max(1.5 × today's min, the floor) to max(1.35 × today's max, 1.5 × the new
  min); floors **broad 8 %, narrow 15 %**; broad traits top out at 18 %. Flat traits ×1.5 (Stalwart 0.75–2.25 armour,
  Stout 1–2 hearts, Strapped 4.5–12 kg). A range's mean goes **11.7 % → 18.9 %**; none rolls under its floor.
- **Count**: Common **1** (today 0–1: an empty Common felt like nothing), Rare 1–2, Epic 2–3, Cursed 1–2.
- The card cap (max × 1.25) and the total cap (twice the card cap) stay; the band ceilings (§3) are what bind.

### 2.4 Techniques: cards that change how you play

Each combat skill's two paths gain a **technique** at tier II and one at tier III (four a skill; a deck grows from 15
to 19 draftable cards). Techniques use the combat actions (Fray and Bestiary own the actions; Mettle owns these
numbers). **A technique is never Common**: rolled Common, it comes as Rare. They count as path cards for Masteries.

| Skill | Tier II | Tier III |
|---|---|---|
| Blades | **Riposte** (Duelist): a perfect parry readies a riposte: your next hit within 1 s deals +60 % and costs no stamina. **Feint** (Skirmisher): dodging out of a charged attack costs half its stamina and your next hit within 1 s is a critical. | **Flurry**: three light hits within 2 s make the third a free charged attack (+40 %). **Blade Dance**: a perfect dodge through a foe cuts it for 120 % and opens a bleed. |
| Heavy Weapons | **Momentum Swing**: a heavy attack that kills charges your next heavy instantly (within 1.5 s). **Guard Break**: charged heavy hits deal triple poise damage and drop a raised shield for 3 s. | **Unstoppable**: while charging a heavy attack you can't be staggered and take 25 % less. **Groundbreaker**: a heavy attack after a fall of 2+ blocks shocks everything within 3 blocks for 60 %. |
| Marksmanship | **Charged Arrow**: hold a full draw 1 s more for +50 % and a piercing shot (15 stamina). **Evasive Shot**: dodging keeps your draw; an arrow loosed within 0.5 s of a dodge deals +40 %. | **Pin Down**: arrows to the legs Hobble (−40 % speed for 3 s; elites half). **Rain of Arrows**: a full draw straight up falls as five arrows where you look (uses five, 12 s). |
| Defense | **Perfect Guard**: your parry window is half again as wide and a perfect parry costs no stamina. **Shield Charge**: sprint with your shield raised to charge 4 blocks and knock a foe down (10 s). | **Stand Fast**: a blocked hit that would stagger you costs half the stamina instead. **Retaliate**: after a perfect parry, your next hit is a critical that ignores half the target's armour. |
| Athletics | **Combat Roll**: dodges go 50 % further with 30 % more invulnerability. **Deep Breath**: stamina starts regenerating half again sooner after you spend it. | **Wall Kick**: dodging into a wall rebounds you (a mid-air dodge). **Endurance**: the first 5 s of a sprint in a fight cost no stamina. |
| Stealth | **Backstab**: a charged attack from behind on an unaware foe deals ×2.5 (bosses ×1.5). **Smoke Bomb**: a dodge drops smoke: foes inside lose you for 2 s (20 s). | **Vanish Step**: a perfect dodge breaks line of sight: foes lose you for 1 s. **Shadowstep**: dodge through a staggered foe to land behind it, unaware of you. |
| Survival | **Field Dressing**: a bandage heals 4 health over 4 s (30 stamina, 20 s). **Hunter's Read**: the first hit on a beast you have killed before reveals its weakness to you. | **Sixth Sense**: an attack from outside your view flashes its tell at the screen's edge. **Second Nature**: brews and food used in a fight take half the time. |
| Mining, Smithing | **Power Swing** (Mining): a charged pickaxe swing breaks three blocks in a line (20 stamina). **Field Whetstone** (Smithing): sharpen in the field: +15 % for the next 60 hits. | **Tremor Sense** (Mining): sneak-strike stone to see hollow spaces within 8 blocks for 3 s. **Quick Repair** (Smithing): restore a quarter of an item's durability without an anvil (once an in-game day). |
| Arcana, Alchemy (wave 2, with their trees) | **Quickcast**, **Mana Shield** (damage drawn from mana, 2 mana a point) | **Overchannel** (double mana for +50 %), **Spellparry** (a perfect parry with a focus reflects a bolt); Alchemy: **Quaff on the Move**, **Volatile Mix**, **Coat** (oils and venoms), **Bombardier** |

Rarity odds lean up to match: **Common 64 − 1.6 L, Rare 28 + L, Epic 8 + 0.6 L** (skill 20: 32 / 48 / 20 %; today
40 / 44 / 16).

---

## 3. Ceilings that grow

### 3.1 Bands and depth

Ceilings now cover **everything Mettle adds**: attributes, perks, cards, traits, sets, talents, Mythics and bonds
(today's §14 ceilings saw only cards). Gear (Arsenal parts and runes, a vanilla enchantment) and a brew's own effect
sit outside, on the item.

**Depth** = the better of your **three best skills' mean** and your **six best skills' mean + 3** (the Wayfarer's
road, §7). The bands are anchors at depth 2, 6, 12 and 18, and every ceiling rises with each point of depth between
them, so no stretch of play is flat. Past Master:

- **Apex**: a slotted **Mythic or** an active **top set**, and the three best skills at 18 or more.
- **Legend**: **both**, and the three best skills at 18 or more. Breadth alone never opens Apex: commitment does.

| Band | depth | offense: always / conditions / techniques | damage taken: always / conditions / techniques | health | movement | dig | extra drops | fight power: plain / full |
|---|---|---|---|---|---|---|---|---|
| I | 0-2 | ×1.15 / ×1.22 / ×1.25 | 95 / 93 / 92 % | +2 | +15 % | +50 % | +20 % | 1.20× / **1.22×** |
| II | 6 | ×1.25 / ×1.36 / ×1.45 | 93 / 91 / 89 % | +6 | +20 % | +90 % | +30 % | 1.39× / **1.46×** |
| III | 12 | ×1.35 / ×1.52 / ×1.70 | 91 / 89 / 87 % | +12 | +26 % | +140 % | +40 % | 1.65× / **1.77×** |
| IV · Master | 18 | ×1.45 / ×1.72 / ×2.00 | 90 / 87 / 85 % | +18 | +32 % | +180 % | +50 % | 1.94× / **2.11×** |
| Apex | IV + a Mythic or a top set | ×1.50 / ×1.82 / ×2.15 | 90 / 86 / 84 % | +19 | +36 % | +200 % | +55 % | 2.03× / **2.23×** |
| Legend | IV + both | ×1.55 / ×1.92 / ×2.30 | 90 / 86 / 83 % | +20 | +40 % | +220 % | +60 % | 2.11× / **2.35×** |

Health matches the overview's 22 / 26 / 32 / 38 / 39 / 40. **Staying power is mostly health**, as the overview wants
(the fights are read-and-answer: dodging and parrying are the player's, not Mettle's); Mettle's own damage cut tops at
−17 % (today −45 %). The jump stays under two blocks.

### 3.2 Three layers: always, conditions, techniques

Each band's offense and damage-taken ceilings come in three layers. **Always-on** sources (attributes, roots,
traits, set 2-tiers, talents) fill only the first; **conditions** (below 40 % health, the first hit, at long range, in
the dark) the second; **techniques** landed (a riposte, a charged arrow, a backstab, an overchannel) the third. The
game clamps each layer where the numbers meet the hit, as `Ceilings` does today. Plain play tops out at ×1.92 at
Legend; only landed techniques reach ×2.30. **The last sixth of every ceiling is earned in the fight.**

### 3.3 Each damage kind follows its discipline

A kind's offense ceiling is the band of the skill that governs it, never above your own band: **melee** the better of
Blades, Heavy Weapons and Defense; **ranged** Marksmanship; **spell and status** Arcana, or Alchemy for poisons and
oils; **openers** Stealth. A Wayfarer with Marksmanship 15 shoots at the 15 ceiling, however deep its other skills
go. **Devotion**, the specialist's edge: when your active top set is native to your best skill and that skill's own
drawn Mythic is slotted, that skill's kind gets **+0.10 / +0.20** on its conditions / techniques layers (a devoted
Legend ×2.50: the overview's 2.3–2.6). The Rogue's devotion is Stealth's: its openers.

**Openers** (a hit on a foe unaware of you) have Stealth's own allowance: the ceiling × (1 + Stealth ÷ 40) for melee,
× (1 + Stealth ÷ 80) for arrows and spells; never on bosses.

### 3.4 PvP

Whatever the band: offense **×1.30**, damage taken no lower than **75 %**, health **+4**, movement **+20 %**,
crowd control half as long, no executes, Mettle healing **0.4** a second. A PvP fight caps at **1.44×** for everyone.
`pvp_perks` (`full` / `reduced` / `off`) now covers perks too.

### 3.5 Other ceilings

Extra drops per drop by band (+20 % to +60 %); dig ×1.5 to ×3.2; road healing and hunger (+30 % to +70 %); Mettle
healing in a fight 0.5 to 1.0 health a second (half while a boss is near); cooldowns no shorter than ×0.60. The
stat contract (§8) gives every other number its range by band.

### 3.6 The curve (simulator §3; specialists, skilled play, plain in brackets)

| Character level · depth · state | Band | Warrior | Ranger | Rogue | Mage |
|---|---|---|---|---|---|
| 5 · 3 | I→II | 1.21 → **1.18** (1.18) | 1.16 → **1.18** | 1.18 → **1.23** | — → **1.20** |
| 12 · 7 | II→III | 1.43 → **1.41** (1.33) | 1.35 → **1.48** (1.39) | 1.27 → **1.45** (1.38) | — → **1.43** (1.37) |
| 22 · 12 | III | 1.74 → **1.68** (1.54) | 1.54 → **1.69** (1.59) | 1.51 → **1.65** (1.56) | — → **1.62** (1.53) |
| 30 · 16 · top set | III→IV | 1.86 → **1.89** (1.69) | 1.64 → **1.87** (1.71) | 1.63 → **1.83** (1.69) | — → **1.79** (1.65) |
| 38 · 20 | IV | 1.94 → **2.01** (1.78) | 1.78 → **1.99** (1.80) | 1.65 → **1.95** (1.79) | — → **1.91** (1.75) |
| 38 · 20 · top set | Apex | 1.99 → **2.10** (1.84) | 1.87 → **2.08** (1.86) | 1.72 → **2.03** (1.84) | — → **1.99** (1.81) |
| 50 · 20 · top set + Mythic | Legend | 2.09 → **2.33** (1.91) | 1.98 → **2.37** (2.05) | 1.93 → **2.22** (1.98) | — → **2.27** (2.01) |

"Today" is today's numbers with today's clamps (the Mage has no Arcana yet). Early power stays modest by design (the
band holds level 5 at about 1.2×, as today); what changes early is that **every card is felt** and the power sits in
fewer, bigger numbers. The top grows to 2.2–2.4×, evenly across the pillars, and every step is bounded.

---

## 4. Fewer, stronger slots

### 4.1 Slots

| Skill level | 2 | 6 | 10 | 14 |
|---|---|---|---|---|
| Slots (today 3 → 8) | **2** | **3** | **4** | **5** |

**Wits 12: +1 slot** in every skill (today +1 at Wits 8 and +2 at 14). Polymath stays +1. The steps follow the tier
gates: two roots fit at skill 2, a path card joins at 6, a Mastery with its two side cards fits from 14 (one slot
left for a technique, a cursed card or a Mythic).

| Simulated (careers, the game's deal, momentum, Focus, boons) | Today 3 → 8, sets 2/4/6 | 2 → 5, 2/4/6 | 3 → 6, 2/3/5 | **2 → 5, 2/3/5 (chosen)** |
|---|---|---|---|---|
| Slotted cards: focused character · every skill at 20 | 43.6 · 80 | 31.4 · 54 | 36.3 · 63 | **31.4 · 54** |
| Committed top set by skill 20: worst keyword · mean | 83 · 97 % | 83 · 95 % | 95 · 98 % | **94 · 98 %** |
| First top set, mean main skill: common · scarce keywords | 12.5 · 13.5 | 12.4 · 13.1 | 12.4 · 12.6 | **12.3 · 12.5** |
| A guided player's Focus: middle tier by mid-game · top by 20 | 97 · 95 % | 92 · 77 % | 100 · 94 % | **99 · 89 %** |
| Deck overlap (Jaccard): one skill · players chasing one keyword | 0.157 · 0.140 | 0.097 · 0.096 | 0.113 · 0.106 | **0.097 · 0.096** |
| Identical decks | 0 | 0 | 0 | **0** |

Fewer slots **raise variety** (overlap 0.16 → 0.10): with five slots, which five is a real choice. Sets 2/4/6 at five
slots leave the guided player's Focus at 77 % by 20; **2/3/5** brings it back to 89 % and every keyword's committed
top set to 94 % or more (Hex 76 %, a price you choose). Per keyword: Brutal, Echo, Ember, Gale, Hoard, Iron, Keen, Kin,
Swift, Wild 100 %, Deep 99 %, Warden 98 %, Tide 94 %.

### 4.2 Set tiers

**2 / 3 / 5** cards. The **5** (the top set) needs cards from two decks and wakes when two of those decks reach skill
12 (BUILD_FEEL.md §1.2b's gate, unchanged). Three sets active; pins as today. Momentum, Focus, Temper and Inscribe
work unchanged (Temper still three cards a deck, Inscribe one).

### 4.3 Migration (old saves)

On the first join under these rules (once, before any XP counts; the ledger is versioned):

1. **Each deck keeps its first slots up to the new count**; a slotted Mastery keeps itself and its two side cards
   first. Every card over the limit goes back to the binder **free: no draught, fee or scar**. Mythics over a deck's
   slots go back the same way.
2. **Values move to the new ranges by their place in the old one**: a trait rolled at 70 % of its old range sits at
   70 % of its new one; a root's rank is its rarity, so it reads its new value. Commons with no trait get one, rolled
   from the card's own seed.
3. Every deck gets **one free rebuild** (as the v2 migration did) and free swaps are refilled to 3.
4. Today's decks become **loadout A** (§11).
5. A chip says what happened: "Your decks hold fewer, stronger cards now: 3 cards went back to your binder. Each
   deck has one free rebuild."

---

## 5. Sets as identity (all fourteen keywords, and a fifteenth)

**2 = a real perk** (felt, §2.1: 1 of 14 today → 14 of 14). **3 = a mechanic change.** **5 = a build-defining
ability** (two decks, wakes at 12).

| Keyword | 2 cards | 3 cards | 5 cards |
|---|---|---|---|
| **Ember** | Fire and lava hurt you 35 % less; your hits on burning foes deal +15 %. | **Fire-tempered**: a third of your melee damage becomes fire (Fray's kind) and your hits ignite one time in four; campfire food heals 2 hearts. | **Kindled**: while burning or in fire you take no fire damage, deal +25 %, regenerate stamina 50 % faster; heavy and charged attacks release a 3-block flame burst (8 s). |
| **Deep** | Below y 0: dig 20 % faster, take 10 % less. | **Dark sight**: in light 4 or less you see as by a dim night vision, and your first hit on a foe that hasn't seen you is a critical. | **Underdark**: in light 4 or less you regenerate half a heart every 3 s, foes lose you when you leave their sight, and hits from the dark deal +40 % (once per foe every 10 s). |
| **Swift** | +8 % movement; stamina regenerates 10 % faster while you move. | **Light step**: dodges cost 30 % less; a perfect dodge refunds half its cost and gives +20 % attack speed for 3 s. | **Slipstream**: after 2 s of sprinting or a perfect dodge, your next hit or shot within 2 s deals +60 % and staggers. |
| **Brutal** | +12 % melee damage. | **Bone-shaker**: heavy and charged attacks deal +50 % poise damage; criticals knock back and daze for 0.5 s. | **Overkill**: damage past a kill spills onto the nearest foe within 5 blocks (up to 20), and a kill refunds a third of that attack's stamina. |
| **Echo** | Cooldowns 15 % shorter. | **Second charge**: abilities with a cooldown hold two charges; cooldowns 25 % shorter in all. | **Reverberate**: an ability with a cooldown is ready again at once one time in three (never twice in a row). |
| **Warden** | +3 armour; blocking costs 20 % less stamina. | **Shield wall**: allies within 6 blocks take 15 % less; foes that hit them are marked: your hits on marked foes +20 %. | **Bodyguard**: when an ally within 6 blocks would take more than 4 damage you take a third of it (at your own armour, 30 % less); a perfect parry within 6 blocks of an ally covers them too. |
| **Keen** | Critical hits (melee, arrows, spells) +15 %. | **Opening**: your first hit on a foe at full health is a critical (once per foe); a perfect parry or a headshot makes your next hit one too. | **Weak Point**: three hits on a foe within 4 s expose it: +25 % from everyone and its resistances 20 points lower for 5 s (10 s per foe). |
| **Wild** | Animals and wild plants give 20 % more; your tamed animals deal 20 % more. | **Pack**: your tamed animals take 35 % less, deal 25 % more and follow your target; bleeds and poisons you cause last half again as long. | **Feral**: on natural ground you regenerate as if well fed, move 12 % faster, and your statuses tick 50 % faster. |
| **Iron** | +2 armour toughness; armour wears 20 % slower. | **Poise**: common foes' hits can't stagger you while you hold more than half your stamina; blocking costs 25 % less. | **Ironskin**: the first hit every 6 s is 4 lighter (after armour), and a hit that would take more than 40 % of your health is cut by a third (8 s). |
| **Tide** | Swim 25 % faster, +15 s of breath; in rain or water stamina regenerates 25 % faster. | **Undercurrent**: mine at full speed under water; thrown tridents +25 %; your hits in water or rain chill (Fray's status). | **Undertow**: in water or rain +25 % damage, every hit slows for 1 s, you see clearly under water, and a dodge in water is a 4-block surge with no stamina (3 s). |
| **Gale** | You fall 3 more blocks before taking damage; projectiles fly 15 % faster. | **Aerial**: attacks while falling count as charged, arrows loosed in mid-air don't spread; sprint-jumps 10 % higher (under the jump ceiling). | **Updraft**: jump once more in mid-air (6 s) and dodge in mid-air. |
| **Hoard** | Everything you carry weighs 12 % less. | **Plenty**: blocks and mobs drop one more of something one time in 12 (natural drops, plain stacks only). | **Magpie**: loot chests roll once more one time in three (never a vault); Hearth markets +8 % edge; elites drop one more loot roll. |
| **Hex** | Your cursed cards' downsides are 40 % smaller. | **Dark bargain**: their upsides are 30 % stronger. | **Pact**: once every 4 minutes a killing blow leaves you on 5 hearts with +30 % damage and Speed I for 8 s, and takes 3 max hearts until you next sleep. |
| **Kin** | Allies within 8 blocks gain 10 % more XP and regenerate stamina 10 % faster. | **Aid**: sneak-use an empty hand on an ally to heal them 4 hearts over 4 s from your stamina (20 s); you and allies within 8 regenerate 20 % faster. | **Rally Cry**: allies within 16 blocks get +20 % damage, +20 % stamina regeneration and Speed I for 10 s (60 s). |
| **Wayfarer** (new, §7) | counts **decks** holding a Wayfarer card, not cards: **3 decks** — Roadwise: +8 % movement off roads and on them, hunger −15 %. | **5 decks** — Many Hands: digging, chopping and harvesting +30 % in every gathering skill, +10 % yields, **+1 talent slot**. | **7 decks** — Wanderer's Grace: away from settlements and your bed (500+ blocks) +20 % damage and −15 % damage taken; your depth counts your six best skills' mean + 4. It never opens Apex by itself (§3.1). |

The Echo set and every cooldown read one scale (`Cooldowns.scale`, now `mettle:cooldown_scale`, §8).

---

## 6. Hybrids and bonds

### 6.1 Bonds: every pair of skills has one

Today's nine synergies (TREES.md §11) become **bonds** with three steps, and every designed hybrid gets one. A bond
opens at **both skills 10**, grows at **15** and completes at **20**; it costs no slot (earned, like a feat) and its
numbers sit inside the ceilings. A completed bond also unlocks the pair's **pair Mythic** for forging with shards
(BUILD_FEEL.md §5.2; 45 shards, both skills 18+), which counts as a Mythic for Apex and Legend but never for Devotion.
**Two resource pools**: a pair whose second discipline spends another resource (mana beside stamina, a brew beside a
blade) fights with both, the second kind filling the gaps the first leaves (the simulator adds a quarter of the second
kind's bonus to general fights and bosses).

| Hybrid (byname) | Pair | 10 | 15 | 20 | Pair Mythic |
|---|---|---|---|---|---|
| **Spellblade** | Blades + Arcana | Imbue: a charged blade attack spends 10 mana to add 25 % as arcane | Arcane Riposte: a perfect parry refunds 15 mana and makes your next spell instant | runes on your blade feed your spells' status combos (+35 % on Imbue) | The Runed Edge |
| **Deepwarden** | Defense + Mining | Bedrock: below y 0, +20 % melee and blocks cost half the stamina | Stoneguard: standing on stone, poise +40 % | below y 0 your shield blocks from every side | The Mountain's Oath |
| **Shadowbow** | Stealth + Marksmanship | Unseen Shot: an arrow at an unaware foe +60 % | Slip Away: after a kill from hiding, foes lose you for 2 s | full-draw shots from stealth make no sound and no trail | Nightfall Quiver |
| **Battlesmith** | Heavy Weapons + Smithing | Field Whetstone: +15 % with a weapon you forged | Riveted: armour you forged takes 10 % less | Field Repairs: your forged gear never wears out mid-fight | The Anvil's Voice |
| **Venomblade** (Poisoner) | Blades + Alchemy | Venom Edge: coatings last twice as many hits | Antivenom: poisons on you last half as long | poison you apply ignores armour and stacks twice | The Green Vial |
| **Lumen knight** | Defense + Arcana | wards on allies take 25 % of their hits | radiant smites +40 % on the undead | a perfect parry casts your ward on everyone within 6 | The Dawn Shield |
| **Battle alchemist** | Heavy Weapons + Alchemy | a heavy hit on a coated foe bursts the coating (AoE) | bombs thrown mid-swing cost no stamina | tinctures you drink in a fight act twice as fast | The Iron Alembic |
| **Hunter** | Marksmanship + Survival (Huntsman) | arrows that kill animals come back; +1 meat and hide | a beast you have killed before takes +20 % from you | coated arrows apply the beast's weakness | The Old Hunt |
| **Hexer** | Arcana (Hex) + Stealth | curses cast from hiding last twice as long | a cursed foe can't see you beyond 8 blocks | a dagger finish on a cursed foe spreads the curse | The Black Candle |
| **Stormcaller** | Arcana (Storm) + Athletics | a perfect dodge refunds 10 mana | sprinting charges your next bolt (+30 %) | a dodge through a foe leaves a lightning trail | The Running Storm |
| Sword and Board · Weapon Master · Leaping Shot · Marching Order · Trailblazer · Forgemaster · Stonebreaker · Battle-Worn · Herbalist | (today's synergies) | today's grant | +50 % on its number | a second line (designed in the build wave) | — |

**Bynames** (BUILD_FEEL.md §2) celebrate pairs: a completed bond gives the hybrid's name as the byname's core
("Ashford the Spellblade", "Bree the Deepwarden of the Mountain's Oath"); a Wayfarer 7 gives "the Wayfarer".

### 6.2 Archetypes at character level 50 (simulator §4; fight power ×vanilla, skilled play)

| Archetype | Group | Skills | Band | Own area | Combat | Boss | Road | **General** |
|---|---|---|---|---|---|---|---|---|
| Warrior | specialist | Heavy 20, Defense 20, Athletics 16, Smithing 12 | Legend | melee **2.48** | 2.33 | 2.25 | 1.05 | **1.92** |
| Ranger | specialist | Marksmanship 20, Survival 20, Athletics 16, Stealth 12 | Legend | ranged **2.52** | 2.37 | 2.29 | 1.18 | **2.02** |
| Rogue | specialist | Blades 20, Stealth 20, Athletics 16, Survival 12 | Legend | ambush **3.03** | 2.22 | 2.13 | 1.08 | **1.93** |
| Mage | specialist | Arcana 20, Alchemy 20, Defense 16, Athletics 12 | Legend | spell **2.42** | 2.27 | 2.19 | 1.05 | **1.88** |
| Spellblade | hybrid | Blades 20, Arcana 20, Athletics 16, Alchemy 12 | Legend | melee 2.26 · spell 2.26 | 2.40 | 2.18 | 1.08 | **1.92** |
| Deepwarden | hybrid | Defense 20, Mining 20, Heavy 16, Smithing 12 | Legend | melee 2.36 | 2.21 | 2.15 | 1.23 | **1.92** |
| Shadowbow | hybrid | Stealth 20, Marksmanship 20, Survival 16, Athletics 12 | Legend | ranged 2.42 · ambush 2.70 | 2.27 | 2.20 | 1.14 | **1.95** |
| Battlesmith | hybrid | Heavy 20, Smithing 20, Defense 16, Mining 12 | Legend | melee 2.36 | 2.21 | 2.15 | 1.23 | **1.92** |
| Venomblade | hybrid | Blades 20, Alchemy 20, Survival 16, Stealth 12 | Legend | melee 2.33 · status 2.25 | 2.45 | 2.23 | 1.10 | **2.00** |
| Balanced fighter | balanced | six skills at 16 | Master | melee 2.00 · ranged 2.06 | 1.94 | 1.91 | 1.27 | **1.76** |
| Wayfarer | Wayfarer | nine skills at 13-17 | Master | road **1.59** | 2.08 | 2.03 | 1.59 | **1.98** |

Asserted (all pass):

| Target | Result |
|---|---|
| A specialist clearly wins its own area: ≥ 8 % over everyone, ≥ 4 % over builds sharing its main skill at 20 | Warrior melee +24 % / +5 %; Ranger ranged +15 % / +4 %; Rogue ambush +12 % / +12 %; Mage spell +36 % / +7 % |
| A good hybrid matches a specialist overall (≥ 97 % of the specialists' mean general, ≥ 93 % of its parent) | Spellblade −1 %, Deepwarden −1 %, Shadowbow +1 %, Battlesmith −1 %, Venomblade +3 % |
| A balanced build within 20 % of a specialist in general play, and under it | −9 % |
| The Wayfarer is viable: general ≥ 85 %, combat ≥ 75 % of the specialists, the best on the road | general +2 %, combat 90 %, road 1.59 (next best 1.27) |
| Commitment pays in a fight: specialists' combat ≥ 8 % over the best broad build | +11 % |
| Every build's PvP power ≤ 1.5× | 1.35–1.41× |

Free-form holds: backgrounds stay a head start (CHARACTER.md §1); any skill can be trained; the archetypes are shapes
the simulator balances, never classes. The Lumen knight, Battle alchemist, Hunter, Hexer and Stormcaller are designed
(§6.1) and enter the simulator with Arcana and Alchemy's trees.

---

## 7. The Wayfarer: the generalist's road

- **Depth from breadth**: the six best skills' mean + 3 (Wanderer's Grace: + 4) reaches Master with nine skills at
  13-17, so the Wayfarer's health, road and defence ceilings are a Master's.
- **Its damage stays at its weapons' depth** (§3.3): Marksmanship 15 shoots at the 15 ceiling. Commitment wins
  fights (+11 %); the Wayfarer wins the road (1.59 against 1.05–1.27) and holds its own (combat 90 % of a
  specialist's).
- **The Wayfarer keyword** (§5) counts decks, not cards: it sits in the second-keyword pools of Athletics, Survival,
  Mining, Smithing, Speech and Stealth (and Husbandry and Riding when they ship), so it rewards many skills at
  moderate levels. Its 5 gives **+1 talent slot**: talents are the generalist's tools.
- Its Mythics are the deed ones (Long Walker, the Far Horizon, Hundredth Dawn), already in reach of a traveller.

---

## 8. The stat contract: Mettle's numbers, the other mods' mechanics

Mettle registers each stat as an attribute on the player (`mettle:<stat>`, like `mettle:carry_capacity` today), with
its default (the neutral value) and its range. **Mettle applies its own modifiers already clamped to the player's
band** (one correcting modifier per stat, as `mettle:ceiling/movement_speed` does today), so a reader just reads
`player.getAttributeValue(...)`; other mods' modifiers (Arsenal runes, Alembic brews) are theirs and sit outside the
clamp. `MettleServices.power()` adds `band(player)`, `caps(player)`, `offenseCap(player, target, kind)` (PvP and boss
aware) and `apex(player)`. Bestiary may put the resistance attributes on mobs: one name for both sides.

| Stat | Unit | I · II · III · IV · Apex · Legend | PvP | Read by |
|---|---|---|---|---|
| `mettle:stamina_max` | + points (Fray's base 100) | 5 · 20 · 40 · 60 · 70 · 80 | 30 | Fray (pool), Bestiary (actions) |
| `mettle:stamina_regen` | × regen | 1.10 · 1.20 · 1.30 · 1.40 · 1.45 · 1.50 | 1.25 | Fray |
| `mettle:stamina_cost.dodge` | × cost, floor | 0.95 · 0.90 · 0.80 · 0.72 · 0.68 · 0.65 | 0.85 | Fray, Bestiary |
| `mettle:stamina_cost.parry` / `.block` / `.heavy` / `.charged` / `.sprint` | × cost, floor | 0.90 · 0.80 · 0.70 · 0.60 · 0.55 · 0.50 | 0.75 | Fray, Bestiary |
| `mettle:stamina_refund` | share of an action's cost, perfect timing only | 0.20 · 0.30 · 0.40 · 0.50 · 0.50 · 0.50 | 0.25 | Fray, Bestiary |
| `mettle:dodge_iframes`, `mettle:parry_window` | + ms | 20 · 40 · 60 · 80 · 90 · 100 | 40 | Fray, Bestiary |
| `mettle:charge_speed` | × wind-up | 1.10 · 1.20 · 1.30 · 1.40 · 1.45 · 1.50 | 1.20 | Bestiary, Arsenal |
| `mettle:poise` / `mettle:stagger_power` | + % (bosses take stagger at half) | 10 · 25 · 40 · 60 · 70 · 80 | 30 | Fray, Bestiary |
| `mettle:melee_power`, `ranged_power`, `spell_power` | × hit: the band's offense ceiling (§3) | 1.25 · 1.45 · 1.70 · 2.00 · 2.15 · 2.30 (devoted 2.50) | 1.30 | Fray's damage pipeline, Arsenal, Alembic |
| `mettle:crit_chance` / `mettle:crit_damage` | + points / + % | 5–25 / 15–60 | 10 / 25 | Fray |
| `mettle:damage.<kind>` (slash, pierce, blunt, fire, frost, shock, poison, arcane, radiant) | + %, inside the offense ceiling | 15 · 25 · 35 · 45 · 50 · 55 | 20 | Fray, Arsenal, Alembic |
| `mettle:resist.<kind>` | % less, inside the taken ceiling | 15 · 25 · 35 · 45 · 50 · 55 | 30 | Fray |
| `mettle:status_chance.<status>` (bleed, burn, chill, shock, poison, stagger) | + points | 5 · 10 · 15 · 20 · 25 · 30 | 10 | Fray, Arsenal, Alembic |
| `mettle:status_power` / `mettle:status_taken` | × strength / × duration on you (floor) | 1.10–1.50 / 0.90–0.50 | 1.15 / 0.80 | Fray, Alembic |
| `mettle:draw_speed` | × bow draw, crossbow load | 1.20 · 1.30 · 1.40 · 1.50 · 1.55 · 1.60 | 1.25 | vanilla bows, Arsenal |
| `mettle:projectile_velocity` / `projectile_spread` / `ammo_save` | × / × floor / chance | 1.10–1.40 / 0.85–0.35 / 0.10–0.40 | 1.15 / 0.70 / 0.20 | vanilla, Arsenal, Alembic |
| `mettle:mana_max` | + points (Fray's base 40: the overview's 40 → 240) | 10 · 50 · 110 · 160 · 180 · 200 | 80 | Fray, Alembic |
| `mettle:mana_regen` / `spell_cost` / `cast_speed` | × / × floor / × | 1.10–1.60 / 0.95–0.60 / 1.10–1.40 | 1.25 / 0.85 / 1.15 | Fray, Alembic |
| `mettle:cooldown_scale` | × cooldowns, floor | 0.95 · 0.85 · 0.75 · 0.65 · 0.62 · 0.60 | 0.85 | Mettle, Arsenal, Alembic, Bestiary |
| `mettle:brew_strength` / `brew_duration` | × a brew's continuous numbers / × duration | 1.10–1.50 / 1.20–2.00 | 1.15 / 1.30 | Alembic |
| `mettle:brew_amplifier` / `brew_doses` | + whole levels (never past the brew's max + 1) / + doses | 0 · 0 · 1 · 1 · 1 · 1 / 0 · 1 · 1 · 2 · 2 · 2 | 0 / 2 | Alembic |
| `mettle:healing_received` | × healing | 1.05 · 1.10 · 1.20 · 1.30 · 1.35 · 1.40 | 1.10 | everyone who heals |
| `mettle:combat_sustain` | most Mettle healing a second in a fight | 0.5 · 0.6 · 0.8 · 1.0 · 1.0 · 1.0 | 0.4 | Mettle (Fray marks boss fights) |
| `minecraft:max_health` (Mettle's modifiers) | + health | 2 · 6 · 12 · 18 · 19 · 20 | 4 | vanilla |

Fray marks a fight as a **boss fight** (its boss tag) and a hit as **PvP**; Mettle's hooks read both. The simulator
asserts each range grows with the band and that PvP never passes Legend.

---

## 9. Fairness

| Rule | Why |
|---|---|
| **Bosses**: Mettle's offense bonus counts 70 %; executes (Misericorde, Executioner, Backstab's ×2.5 → ×1.5) never apply; stagger counts half; Mettle healing counts half. | A Legend build kills the assumed boss in 36–46 s (vanilla geared 75 s) and lasts 15–19 s face-tanking (vanilla 7 s): **every build must still avoid or block 49–67 % of the boss's attacks** (vanilla 90 %). Bosses become fair, not free. |
| **No gear, no win**: Mettle multiplies what you hold. | Naked, every Legend build dies to **3.0–3.4** boss hits (asserted ≤ 4). |
| **Sustain** below incoming: Mettle healing ≤ the band's 0.5–1.0 a second, half near a boss. | At most 0.18 of a boss's 2.31 health a second (asserted ≤ 60 %). |
| **No endless dodging**: dodge cost floor ×0.65, regeneration ×1.5 at most. | Dodging as fast as allowed spends 40.6 stamina a second against 37.5 regenerated even with no pause. |
| **Refunds** only on a perfect timing (which needs an incoming attack), at most half the cost. | Skill sustains; nothing sustains itself. |
| **Cooldowns** no shorter than ×0.60; Reverberate one in three, never twice in a row. | An ability is never under 45 % of its base cooldown (floor 40 %). |
| **Openers** once per foe per 10 s; never on bosses. | Ambush builds win first strikes, not whole fights. |
| **PvP** has its own caps (§3.4). | 1.35–1.41× for every build. |
| **Elites**: the Master band (2.11×) meets the 2.12× an elite needs to feel like a vanilla fight; Legend (2.35×) is a little past it. | Late builds beat elites; D5 places and bosses still kill. |

---

## 10. Talents and Mythics, rescaled

### 10.1 Talents (BUILD_FEEL.md §13)

Slots unchanged (2 / 3 / 4 / 5 at character level 3 / 15 / 27 / 39; the Wayfarer 5 adds one). With fewer skill slots
talents carry more, and every range doubles toward the felt step:

| Talent | Today | After |
|---|---|---|
| Hardy | ½–1½ hearts | **1–3 hearts** |
| Fleet | 2–4 % | **4–8 %** |
| Weathered Hide · Grit | 2–4 % · 3–6 % | 4–8 % · 6–12 % (inside the taken ceiling) |
| Iron Gut · Quick Healer · Homebody | 5–15 · 10–25 · 10–20 % | 10–25 · 20–40 · 20–35 % |
| Broad Shoulders · Careful Hands · Quick Hands | 2–6 kg · 5–12 · 10–25 % | 4–12 kg · 10–20 · 20–40 % |
| Climber · Strong Swimmer · Sure-Footed · Long Arms | 10–20 % · 5–10 % · ½–1½ · 0.25–0.5 | 20–35 % · 10–18 % · 1–3 · 0.5–1 |
| Quick Learner · Night Owl · Night Eyes | 2–5 · 4–8 · 10–30 % | 4–8 · 6–12 · 20–45 % |
| Cold Blood · Steady Nerves | 15–30 % | 25–50 % |
| Lucky · Scavenger · Silver Tongue | ½–1½ · 2–5 % · 1–3 % | 1–2 · 4–8 % · 2–5 % |
| **New**: Second Wind · Deep Well · Steady Hands · Iron Will · Light Feet · Hawkeye | — | stamina regen +8–15 % · mana +10–25 · brew duration +10–25 % · status duration on you −10–20 % · dodge cost −5–10 % · projectile speed +5–10 % |

### 10.2 Mythics

Every Mythic keeps one effect nothing else has, and now **opens Apex** (with a top set, Legend). Numbers grow with
the curve:

| Mythic | Today | After |
|---|---|---|
| The Twin Edge | every 4th hit again, full | every 3rd hit again at 80 % |
| The Lone Banner | +15 % dealt, −15 % taken alone | +25 % / −20 % |
| The Unclad | −25 % taken, +10 % speed with no armour | −35 % / +15 % (inside the ceilings) |
| Hunter's Moon | a second arrow at half | at 75 % |
| Worldsplitter | half the hit, 10 s | 80 %, 8 s, costs a heavy attack's stamina |
| The Seeking Fletch | bends 20° | 30° |
| Aegis | 4 s dome, 45 s | 6 s, 30 s |
| Seven-League Stride | a 6-block dash, 8 s | your dodge goes three times as far and costs no stamina once every 4 s |
| The Dread Maul · Misericorde · The Iron Vow | flee 3 s · ends a foe below 15 % · once a day | 4 s · below 20 % (never a boss) · once every 10 minutes |
| **New**: Stealth's three (The Unseen Hand: openers +40 % and an opener every 10 s on a foe that loses you; Nightfall Cloak; Last Breath), Arcana's three (The Second Voice: every 4th spell cast again at 50 %; Starfall Codex; The Quiet Word), Alchemy's three (The Philosopher's Ember; The Endless Flask; The Green Vial is the Venomblade's pair Mythic) | | sized like the rest (an Epic Mastery with traits) |
| **Pair Mythics** (§6.1) | — | one per designed hybrid, forged with shards once the bond completes |

---

## 11. Loadouts

| | Rule |
|---|---|
| **How many** | **2** from character level 10, **3** from 30 (`loadouts`, `loadout_levels`) |
| **What one holds** | every skill deck's slots (card ids by slot, Mythics included), the talent slots, the **Focus** and the set **pins**. Not attributes or background (that is Rebirth, §12). |
| **The same card in two loadouts** | allowed: a loadout is a plan of card ids. **Only the live loadout's cards are slotted**; the others' cards sit in the binder, so a card never counts twice (sets, traits, Mythics, the ledger's one place). Trading a card another loadout plans for leaves a marked hole there; the trade screen warns "used in loadout B". |
| **Switching** | **free when you wake from sleep in a bed** (once an in-game day, beside the talents' "Reconsider" prompt); otherwise at a **Card Shrine or a Hearth trainer** for a quarter of each changed card's rebuild fee (10 / 30 / 90 / 225 bits; emeralds a quarter, rounded up), **no draught, no scar**; 5 minutes between switches. Never within 10 s of being hurt, in a boss fight (Fray's marker), mid-trade, or within 5 minutes of a PvP hit. Set and Mythic cooldowns stay with you, so a switch can't reset them. |
| **Building one** | a new loadout starts as a copy of the live one; adding a card to any loadout is free. **A card leaving a loadout always costs a rebuild** (draught, fee, scar), live or not, unless another loadout still holds it; deleting a loadout is a rebuild of the cards only it held. So loadouts are setups to switch between, never a way around rebuilds. |
| **Simulated** | a focused player at mains 20 builds a second loadout from the same binder for the best keyword it hasn't completed: 31 cards, **89 % shared** with the first, its set rising from the 3 to the 5 (simulator §7). |

---

## 12. Rebirth

A big, deliberate redo. **The Rite of Rebirth** at a Card Shrine:

| | Rule |
|---|---|
| **Cost** | an **Ember of Rebirth** (brewed at a cauldron or crafted: a **boss's heart** from any Bestiary lair or realm boss, or without Bestiary a nether star; plus a totem of undying and a Draught of Unbinding) and **10 crowns** (1,000 bits; 24 emeralds without Hearth), burned |
| **Cooldown** | **once every 30 in-game days** (10 hours of play; "an in-game month"), counted in game time and saved with the player. The first rite below character level 15 needs no Ember. |
| **What resets** | every deck emptied to the binder **with no scars**; every attribute point refunded (placed again on the Rebirth screen, or later); the **Focus** re-picked; the **background** may be changed (its starting attributes and favoured skills only: no second kit, skill levels untouched); talents unslotted; set pins cleared; every loadout emptied (their names kept); the byname reset, with a history line "Reborn on day 120"; free swaps refilled to 3 |
| **What stays** | skill levels and XP, character level, the whole card collection (tempered and inscribed cards stay so), Mythics, shards, deeds, Hall of Firsts credits, rerolls, waiting drafts and boons, and every cooldown |
| **Mythics** | unslotted free (no 1,000-bit fee), never scarred; slot them again like any card |
| **Refused** | while any of your cards is in a trade (`TRADE`), in a fight (hurt in the last 10 s), in a boss fight, within 5 minutes of a PvP hit, or while the cooldown runs. Cards listed at a Hearth broker stay listed (they are not slotted). Attuning cards stay attuning. |
| **Hearth** | reputation, crimes and standing are Hearth's and stay; townsfolk who know you may remark on it ("You look... different."), a dialogue condition Hearth can read from `MettleEvents.REBORN` |

The Draught of Unbinding (attributes only) and rebuilds stay for smaller changes.

---

## 13. Attributes (small changes)

- **Vitality**: +1 health per point above 3 (today +1 per two points), so a committed body reaches the overview's 40
  health within the health ceiling.
- **Wits**: +2 % spell power per point above 3 (Arcana's governing attribute); its deck-slot bonus becomes +1 at Wits
  12 (§4.1).
- Strength, Agility and Presence as today; Agility also speeds bow draws (+1.5 % a point), not only swings.

---

## 14. Config and data

New in `config/mettle/cards.json` (and a new `config/mettle/power.json` for the bands): `slots_base` 2,
`slots_every` 4, `slots_max` 5, `wits_slots` [12], `set_tiers` [2, 3, 5], `rarity_odds` {64, −1.6, 28, 1, 8, 0.6},
`technique_min_rarity` rare, `bands` (the §3.1 table), `band_anchors` [2, 6, 12, 18], `apex_needs_top3` 18,
`devotion` [0.10, 0.20], `opener_stealth_divisors` [40, 80], `boss_offense_share` 0.7, `boss_sustain_share` 0.5,
`pvp_caps`, `loadouts` [10, 30], `loadout_switch_fee_share` 0.25, `loadout_switch_minutes` 5, `rebirth_cooldown_days`
30, `rebirth_fee` 1000, `rebirth_free_below` 15. Data: root `rank_values`, trait ranges, keyword sets (2 / 3 / 5),
the Wayfarer keyword, technique perks, bonds (perk kind `bond` with three steps), new talents and Mythics; all generated
by `tools/cards/cards_data.py` and `talents_data.py` as today.

---

## 15. Screens (for the Figma designer; VOID rules, UI.md §1)

| Screen | What it needs |
|---|---|
| **Deck** (each skill) | 2–5 slots (6 with Wits); locked slots say "Opens at Mining 10"; the band strip: "Band III → IV · 67 %" with the three layers as a stacked bar per stat (always / conditions / techniques) and where your build sits |
| **Power** (character sheet, new tab) | your band and depth ("Depth 16: your three best skills"), each damage kind's ceiling and its governing skill, Devotion and Apex / Legend requirements as checkmarks, PvP caps on hover |
| **Bonds** | between two skill pages: the pair, its three steps, the pair Mythic silhouette |
| **Loadouts** | a row of 2–3 tabs on the Deck screen; a switch preview (cards changing, fee, cooldown) and "Free when you wake" |
| **Rebirth rite** | at the shrine: what resets and what stays in two columns, the Ember and fee, the cooldown, a hold-to-confirm; then the attribute screen |
| **Migration notice** | one panel: cards returned, free rebuilds, new values side by side |
| **Card face** | techniques get a small action glyph (parry, dodge, charge, heavy, cast); the Wayfarer keyword its own badge |

---

## 16. The simulator (`MythicPowerReportTest`)

Test-only, beside `BuildFeelReportTest` (whose career harness it reuses for the slot study, through a new
`SET_COUNTS` hook). It reads the shipped data (roots, traits, the deal) and holds the proposal as data: bands, the
three layers, kind ceilings, Devotion, openers, boss and PvP rules, the stat contract, and source lists for the
archetypes (each source: stat, kind, value, uptimes plain / skilled, the depth it arrives at, needs a top set or a
Mythic, opener, never on bosses). **`curveHolds()` runs on every build** and asserts:

1. Every root's Common copy and every percent trait's lowest roll reach their floors; every dealt card is felt; every
   set 2-tier is felt.
2. Bands grow in every stat; the techniques layer is at least 0.4 above the always layer from Master; band I ≤ 1.35×,
   Master ≥ 2.0×, Legend 2.3–2.6×, PvP ≤ 1.5×.
3. Specialists at level 5 between 1.10× and 1.35×; at 50 at least 2.0×, plain play at most 92 % of skilled.
4. The archetype targets (§6.2).
5. The fairness checks (§9): boss avoidance ≥ 15 %, kill time ≥ 25 s, sustain ≤ 60 % of a boss's damage, naked ≤ 4
   boss hits, the dodge chain runs dry, refunds ≤ half, cooldowns ≥ 40 %.
6. The stat contract: every range grows with the band, PvP never passes Legend, every stat has a reader.

**`report()`** (`-Dmettle.balance=true`) adds the slot study (§4.1: four variants, real careers) and loadouts (§11),
asserting for the chosen variant: deck overlap < 0.3, chasing overlap < 0.35, no identical decks, every keyword's
committed top set by 20 ≥ 60 % (Hex aside), first top sets at mean skill 12–17, a guided Focus's top set ≥ 80 %, and a
second loadout strengthening its keyword. It runs in about a minute.

When this ships, `CardReportTest`'s points budget (1.15× / 1.3× v1, CARDS.md §14) retires: the band ceilings replace
it, and its worst-case search moves to the bands and the three layers.

---

## 17. Build plan

| Wave | Contents | Done when |
|---|---|---|
| **M1 · Floors and slots** | root values, trait ranges and counts, rarity odds, 2 → 5 slots and Wits 12, sets 2 / 3 / 5 and all fourteen keywords rewritten, migration §4.3 | the migration game test turns a 0.4.0 save into the new decks with no card lost; `CardReportTest` rewritten to the bands |
| **M2 · Bands** | `Ceilings` becomes the bands: depth, interpolation, three layers, kind ceilings, Devotion, openers, Apex / Legend, PvP and boss rules; perks and attributes inside; Vitality and Wits changes; the Power tab | `CeilingsTest` asserts the §3.1 table in every state; the client tour shows the band strip |
| **M3 · Contract** | the §8 attributes and `MettleServices.power()`; Fray reads them | Fray's own tests read Mettle's numbers through the attributes only |
| **M4 · Techniques, bonds, Wayfarer** | 4 techniques a combat skill, bonds for every pair, pair Mythics, the Wayfarer keyword, hybrid bynames | each technique has a client-tour step with Bestiary's actions |
| **M5 · Loadouts and Rebirth** | §11 and §12 with their screens | trade, loadout and rebirth edge cases in game tests |
| **M6 · Talents and Mythics** | §10 | the simulator's talent and Mythic sections updated |

Figma first for every screen in §15.

---

## 18. Decisions for the user

1. **Staying power from health** (the ARPG overview's 40 health, Mettle's own damage cut ≤ 17 %) instead of today's
   −45 % mitigation ceiling. Defense keeps its identity through blocking, poise, guarding allies and Ironskin.
2. **The top**: a Mythic *or* a top set opens Apex (2.23×), **both** open Legend (2.35×; devoted 2.50×), and only with
   the three best skills at 18+.
3. **Slots 2 → 5 (+1 at Wits 12) with sets 2 / 3 / 5** (the simulator's pick) or 3 → 6.
4. **Top sets come early with 2 / 3 / 5**: scarce keywords land as fast as common ones (skill 12.5 against 12.3).
   Raise the scarce keywords' gate to 14 if Tide and Deep should stay rarer.
5. **Rebirth** price: a boss's heart + 10 crowns, once every 30 in-game days, free below character level 15.
6. **Loadouts**: 2 at level 10, 3 at 30; free on waking, else a quarter of the rebuild fee.
7. **Wits gives spell power** (+2 % a point) and only +1 slot (at 12).
8. **Assumed mob and combat numbers** (§1.2) until Bestiary and Fray write theirs; the simulator re-checks the
   curve when they do.

Known: `TradeRulesTest.roomForCardsAndItems` fails on `mettle` before this branch ("Components not bound yet"); not
touched here.

## Decisions taken (user, 2026-10-21)

All seven decisions accepted as recommended: health-based staying power (40 at the top); a Mythic or a top set unlocks the next tier, both the top; 2-5 slots per skill (sets 2/3/5); every keyword's top set around skill 12-13; Rebirth = a boss heart + 1,000 bits, once per 30 in-game days; 2 loadouts (3 later), free switch on waking; Wits gives spell power.
