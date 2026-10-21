# VOID Mettle — balance sheet

Every number here is a first draft to be tuned in playtests. Each one lives in config or data, never hard-coded.
"Feels right" beats "adds up": change a number when playtesting says so, then update this sheet.

---

## 1. Skill levels

**Cumulative XP for skill level L = round(10 × L^2.66)**, cap 20.

| Level | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|---|---|---|---|
| XP | 10 | 63 | 186 | 399 | 723 | 1,175 | 1,770 | 2,525 | 3,454 | 4,571 |

| Level | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 |
|---|---|---|---|---|---|---|---|---|---|---|
| XP | 5,890 | 7,424 | 9,185 | 11,187 | 13,440 | 15,957 | 18,750 | 21,829 | 25,205 | 28,889 |

**Pacing target**, at ~700 XP per hour of focused activity:

| Level | Time |
|---|---|
| 1 | in the first minute |
| 2 | in 5 minutes (tier I opens) |
| 5 | in about an hour |
| 10 | in about 6½ hours |
| 18 | in about 30 hours (tier IV opens) |
| 20 | in about 40 hours |

Favoured skills arrive 25 % sooner. Trainers and books shortcut the early levels.

In a **mixed survival session** every skill rises at once, each at its own rate (§7): every wave-1 skill a player
actually uses reaches level 6 (tier II) in 2–3½ hours, level 12 (tier III) in 9–29 hours.

## 2. Character level

**Cumulative character XP for level L = round(25 × (L − 1)^2.31)**, cap 50. Character XP = all skill XP gained.

| Level | 2 | 3 | 5 | 8 | 10 | 15 | 20 | 25 | 30 | 40 | 50 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| XP | 25 | 124 | 615 | 2,239 | 4,002 | 11,104 | 22,483 | 38,568 | 59,714 | 118,386 | 200,582 |

Maxing all eight wave-1 skills is 231,112 XP, so level 50 arrives a little before wave-1 mastery. Wave 2 adds headroom.

## 3. XP per action (wave 1)

Every value below is multiplied by: favoured ×1.25, Wits +2 %/point, perks, `xp_scale`. Then anti-farm applies
(CHARACTER.md §3.3).

| Skill | Action | XP |
|---|---|---|
| Mining | stone-like / deepslate / netherrack / obsidian | 1 / 1.5 / 0.5 / 3 |
| Mining | coal · copper · nether quartz · nether gold · redstone · lapis · iron · gold | 3 · 3 · 3 · 4 · 5 · 6 · 6 · 8 |
| Mining | diamond · emerald · ancient debris · amethyst cluster | 20 · 25 · 30 · 2 |
| Mining | deepslate ore variants | ×1.25 |
| Blades / Heavy | per point of damage dealt (capped at the target's remaining health) | 1 |
| Heavy | per natural log chopped with an axe (placed logs give nothing) | 0.5 |
| Blades / Heavy | kill bonus | ¼ of the target's max health |
| Blades / Heavy | modifiers: passive animals ×0.5 · players ×0.5 · spawner mobs ×0.1 (tag `mettle.spawner`, set at spawn; trial spawners keep full XP) | |
| Marksmanship | per point of damage | 1.5 |
| Marksmanship | distance bonus | +1 per 5 blocks |
| Marksmanship | kill bonus | ¼ max health |
| Defense | per point blocked by a shield / absorbed by armour (vanilla's armour formula; damage that bypasses armour gives none) | 8 / 8 |
| Defense | cap | 60 XP a minute |
| Athletics | per 6 m sprinted · 3 m swum · 2 m climbed | 1 |
| Athletics | load multiplier: Burdened / Overburdened or Pinned | ×2 / ×3 |
| Athletics | cap | 60 XP a minute |
| Survival | animal killed (≥ 50 % of the damage yours) · wild plant or berries picked · food cooked (per item) | 15 · 3 · 4 |
| Survival | first visit: biome / structure / Hearth settlement / Expanse landmark | 60 / 100 / 100 / 150 |
| Smithing | per metal unit in a crafted tool, weapon or armour piece (ingot = 1, diamond = 2) | 25 |
| Smithing | wooden, stone or leather piece · ingot taken from a furnace · anvil job · smithing upgrade | 5 · 4 · 25 + 10 per level spent · 150 |
| Smithing | salvage at the grindstone (Salvager) · reforge | 1 per unit · 10 |
| Speech, Thievery, Stealth | what Hearth reports (`PlayerSkills.award`): a speech check 15–45 by difficulty (a third on failure), a struck haggle 5–40, a picked lock 10 a tier, a lifted pocket 6–30, an unseen crime 5–25 | Hearth's numbers (its ARCHITECTURE.md §11) |
| Any | Hearth quest completed | 60–150 by type (§9.3) |
| Any | Hearth trainer's lesson | 40 % of the way to the next level (§9.4) |
| Speech | business at a Hearth market | 5 per crown, 1 per crown past 20 crowns a sim day (§9.2) |

## 4. Attributes per point (modifier ids `mettle:attr/<attribute>/<vanilla>`)

| Attribute | Vanilla attribute | Operation | Amount per point above 3 |
|---|---|---|---|
| Strength | `mettle:carry_capacity` | add_value | +5 (applies from 1: Strength 3 = +15) |
| Strength | `attack_damage` | add_multiplied_base | +0.02 |
| Strength | `block_break_speed` | add_multiplied_base | +0.02 |
| Agility | `movement_speed` | add_multiplied_base | +0.01 |
| Agility | `attack_speed` | add_multiplied_base | +0.015 |
| Agility | `sneaking_speed` | add_multiplied_base | +0.02 |
| Vitality | `max_health` (permanent modifier) | add_value | +1 per 2 points (½ heart) |
| Vitality | hunger drain | behaviour | −2 % |
| Wits | skill XP | internal | +2 % |
| Presence | Hearth prices (§9.2) / Hearth's speech checks | compat | ±0.5 % / Hearth's own factor |

Points below 3 (only via respec mistakes; the minimum is 1) apply the same rates negatively, except capacity, which
stays at +5 per point from 1.

## 5. Weight

### 5.1 Capacity and bands

| | Value |
|---|---|
| Base capacity | 65 kg (`MettleAttributes.BASE_CARRY_CAPACITY`; players saved with the old base 40 are moved up on join) |
| Strength | +5 kg per point (Strength 3 → 80 kg total, 6 → 95, 10 → 115, 20 → 165) |
| Perks | Pack Mule +8 · Beast of Burden +15 · Ox +25 |
| Gear | Satchel +10 · Leather Pack +20 · Frame Pack +30 · Belt +3 · Tool Belt +5 |
| Ox Draught (Alchemy) | +20 kg for 5 min |
| Bands | Burdened ≥ 75 % · Overburdened ≥ 100 % · Pinned > 125 % |
| Speed | −10 % / −30 % / −70 % |
| Jump | 0 / −40 % / −100 % |
| Hunger | ×1.5 / ×1.5 / ×1.5 |
| Fall damage | +25 % / +25 % / +25 % |

Reference loads:

| Character | Capacity |
|---|---|
| Fresh character (Strength 3, no gear) | 80 kg |
| Fresh Smith with the kit belt (Strength 6) | 98 kg |
| Mid game (Strength 10, Leather Pack, Belt, Pack Mule) | 146 kg |
| Late game (Strength 16, Frame Pack, Tool Belt, Pack Mule, Beast of Burden, Ox) | 228 kg |

What that means in play (measured, §7): a 20-minute strip-mine with everything kept (8 stacks of stone and earth, 40
raw iron, iron armour) is **Burdened** at 92 % for a fresh character, 83 % with a Satchel, Light from Strength 6 with a
Satchel. A full inventory of cobblestone (36 stacks, 138 kg) still pins a fresh character.

### 5.2 Leaf weights (kg per item)

Everything not listed is derived from recipes (BURDEN.md §1). Smelted and cooked outputs are leaves, and cooked food
weighs the same as raw.

| Group | Items → kg |
|---|---|
| Coins (Hearth) | bit, mark, crown → 0.01 |
| Gems and dusts | diamond, emerald, quartz, amethyst shard 0.05 · lapis, redstone, glowstone dust 0.02 · coal 0.1 · charcoal 0.08 |
| Metals | iron ingot 0.5 · gold ingot 0.6 · copper ingot 0.4 · netherite scrap 0.8 · raw iron 0.5 · raw gold 0.6 · raw copper 0.4 (raw = what it smelts into) · ancient debris 1.0 |
| Stone-like | cobblestone, stone, andesite, diorite, granite, tuff, calcite 0.06 (stack 3.8) · deepslate, cobbled deepslate, basalt, blackstone 0.08 · netherrack 0.04 · end stone 0.05 · obsidian, crying obsidian 0.6 · ores: their stone + 0.1 |
| Earth | dirt, grass block, sand, red sand, mud, sandstone, red sandstone 0.05 · gravel 0.06 · clay ball 0.015 (a brick block = stone) · snowball 0.02 · ice 0.04 |
| Wood | any log or stem 0.25 (stack 16; planks then derive to 0.0625, sticks to 0.031, a chest to 0.5) · bamboo 0.05 |
| Plants | saplings, flowers, leaves 0.05 · seeds 0.005 · sweet berries, glow berries 0.02 |
| Food | wheat 0.05 · apple 0.15 · carrot, beetroot 0.1 · potato 0.15 · raw beef, pork, mutton, fish 0.3 · chicken 0.4 · egg 0.06 · melon slice 0.1 · pumpkin 2.0 · melon 1.8 |
| Animal | leather 0.3 · rabbit hide 0.1 · string, feather, paper 0.01 · wool (block) 0.15 · bone 0.1 · shulker shell 0.3 |
| Liquids | water, milk or lava in a bucket: bucket + 1.0 / 1.0 / 1.5 · potion 0.35 · glass 0.05 |
| Soil | podzol, mycelium, rooted dirt, dirt path, farmland, soul sand, soul soil, suspicious sand 0.05 · suspicious gravel 0.06 · nylium 0.04 · moss blocks 0.1 · sculk 0.05 |
| Stone (uncraftable) | bedrock, reinforced deepslate, gilded blackstone 0.08 · budding amethyst 0.06 · pointed dripstone 0.06 |
| Plants (uncraftable) | mangrove roots 0.1 · cactus 0.2 · sugar cane 0.05 · kelp 0.02 · mushrooms 0.02 · mushroom blocks, shroomlight 0.1 · corals 0.05 · coral blocks 0.06 |
| Finds | heavy core 3.0 · bell 2.0 · dragon egg 3.0 · end portal frame 1.0 · spawner 5.0 · goat horn 0.5 · heart of the sea 0.3 · nautilus shell 0.1 · music discs 0.2 · sherds 0.1 · banner patterns 0.01 · froglights 0.15 · amethyst buds and cluster 0.05 · cobweb 0.01 · rabbit 0.2 |
| Overrides of recipes | map 0.1 (the recipe's compass made it 2.1 kg) · golden apple 0.3 (8 gold ingots made it 4.95 kg) · sponge 0.1, wet sponge = sponge + 1.0 |

Rules (`burden.Leaves`, `burden.Weights`):
- **Equivalences** no recipe states: weathered and waxed copper = fresh copper; `stripped_X` = X; `infested_X` = X;
  `<colour>_concrete` = its powder; explorer and filled maps = map; chipped/damaged anvil = anvil; enchanted book = book;
  written book = book and quill; carved pumpkin = pumpkin.
- **Cooking fills gaps**: an item no crafting recipe reaches weighs what it was cooked from (smooth stone = stone, brick =
  clay ball, cooked rabbit = rabbit); listed leaves (glass, ingots, cooked meats) still win.
- **Guesses rank last**: a recipe built from real weights always beats one built on a fallback weight, however light.
- Fish and axolotl buckets = water bucket + the animal (0.3 / 0.5 / tadpole 0.05); powder snow bucket = bucket + 1.0.
- About 110 of 1,658 vanilla items still use the fallback (4 kg ÷ stack size): mob drops such as gunpowder and blaze
  rods at 0.06 kg, horse armour at 4 kg, smithing templates.

### 5.3 Equipment (overrides; recipes would make diamond lighter than iron)

| Full set | kg | Split |
|---|---|---|
| Leather | 4 | helmet 20 % · chest 40 % · legs 30 % · boots 10 % |
| Chainmail | 8 | same |
| Copper | 9 | same |
| Iron | 12 | same |
| Diamond | 11 | same |
| Gold | 13 | same |
| Netherite | 14 | same |

| Tool or weapon | wood | stone | copper | iron | gold | diamond | netherite |
|---|---|---|---|---|---|---|---|
| sword | 0.8 | 1.4 | 1.3 | 1.5 | 1.7 | 1.5 | 1.9 |
| axe, pickaxe | 1.3 | 1.9 | 1.8 | 2.0 | 2.2 | 2.0 | 2.4 |
| shovel, hoe | 0.6 | 1.0 | 0.9 | 1.1 | 1.2 | 1.1 | 1.4 |

Other: shield 3.0 · bow 0.8 · crossbow 2.5 · mace 3.5 · trident 2.5 · elytra 2.0 · saddle 3.0 · totem 0.2. Spears
follow the sword row +0.3.

### 5.4 Gear

| Item | Weight | Capacity | Pockets | Recipe (draft) |
|---|---|---|---|---|
| Satchel | 0.8 | +10 | 9 | 5 leather, 2 string |
| Leather Pack | 1.5 | +20 | 18 | satchel, 4 leather, 2 string, 2 iron nuggets |
| Frame Pack | 3.0 | +30 | 27 | leather pack, 4 sticks, 2 iron ingots, 2 leather (Hearth: ~2 crowns) |
| Belt | 0.4 | +3 | 4 (small items) | 3 leather, 1 iron nugget |
| Tool Belt | 0.7 | +5 | 6 (small items) | belt, 2 leather, 2 iron nuggets |
| Quiver | 0.5 | — | 2 (arrows) | 4 leather, 1 string |
| Hunter's Quiver | 0.8 | — | 4 (arrows) | quiver, 2 rabbit hide, 1 string |

## 6. Config defaults (`config/mettle/<module>.json`)

| Module | Field | Default |
|---|---|---|
| core | `xp_scale` | 1.0 |
| core | `skill_cap` / `character_level_cap` | 20 / 50 |
| core | `free_respec_below_level` | 5 |
| character | `favoured_bonus` | 0.25 |
| character | `wits_xp_per_point` | 0.02 |
| character | `pvp_perks` | `full` (`reduced` halves perk damage effects against players, `off` disables them); not built for perks yet. Cards have their own `pvp_perks` in `cards.json` (CARDS.md §4.1), which is built |
| character | `pvp_xp_scale` | 0.5 |
| character | `enabled_skills` | all shipped |
| burden | `enabled` | true |
| burden | `weight_scale` | 1.0 |
| burden | `base_capacity` / `capacity_per_strength` | 65 / 5 |
| burden | `bands` | 0.75 / 1.0 / 1.25 |
| burden | `creative_exempt` / `elytra_blocked_when_overburdened` | true / true |
| gear | `enabled` / `container_pockets` | true / true |

## 7. Playability check (2026-10-07)

Two reports keep these numbers honest; rerun them after any change to §3 or §5:

- `./gradlew test --tests '*ReportTest' -Dmettle.balance=true` → `build/reports/mettle-weight.md` (real weight derivation
  and band rules) and `build/reports/mettle-pacing.md` (the session model below, priced with the real XP constants).
- `./gradlew runClientGameTest -Dmettle.balance=true` → `build/run/clientGameTest/mettle-balance.md`: the same
  inventories put on a Ranger in a real world and read through the live load tracker (the tour itself is skipped).

### 7.1 Weight

Four inventories, carried by a Ranger (Strength 3 base). Load includes worn armour and the pack itself.

| Inventory | Load | No pack, Str 3 / 6 / 10 | Satchel, Str 3 / 6 / 10 | Leather Pack, Str 3 / 6 / 10 |
|---|---|---|---|---|
| (a) Ranger kit: bow, 32 arrows in the quiver, leather cap, 4 mutton | 4 kg | Light everywhere (5 %) | Light | Light |
| (b) 20-minute strip-mine: iron armour, iron pickaxe, 32 torches, 8 beef, 3 stacks cobblestone, 3 cobbled deepslate, 1 dirt, 1 gravel, 40 raw iron, 25 coal | 74 kg | **Burdened 92 %** / Burdened 78 % / Light 64 % | Burdened 83 % / Light 71 % / Light 60 % | Burdened 75 % / Light 65 % / Light 56 % |
| (c) builder: 3 stacks oak planks, 3 oak logs, 2 stone bricks, 8 bread, iron axe and pickaxe | 73 kg | Burdened 91 % / Burdened 77 % / Light 63 % | Burdened 82 % / Light 70 % / Light 59 % | Light 74 % / Light 65 % / Light 55 % |
| (d) mid-game explorer: iron armour, sword, pickaxe, axe, shield, bow, 128 arrows, 16 beef, 16 bread, a stack each of cobblestone, dirt, planks, logs, 32 torches | 60 kg | Light 75 % (at the line) / 63 % / 52 % | Light 67 % / 58 % / 48 % | Light 61 % / 53 % / 45 % |

Before this pass (base 40, +3 per Strength, Satchel +8, Pack +15, cobblestone 0.25, logs 0.8, raw iron 0.6) the same
inventories weighed 4.7 / 179 / 229 / 128 kg: (b), (c) and (d) were **Pinned in every column**, 153–468 % of capacity,
even at Strength 10 with a Leather Pack.

### 7.2 Skill pacing

**The session** (`PacingReportTest.PHASES`), per hour of play:

| | Hour 1 | Hours 2–5 | Hour 6 on |
|---|---|---|---|
| Mining | 250 stone, 22 ores | 530 stone and deepslate, 60 ores (30 % deepslate variants), half a diamond | same |
| Melee | 12 hostile kills, 6 animals | 30 hostile kills, 4 animals | same |
| Bow | 3 kills | 10 kills (3 hits from 15 blocks each) | same |
| Logs | 64 | 64 | 64 |
| Damage coming at you | 40, no armour or shield | 100 in 3.5-point hits; a shield takes 40 %, iron armour (15) the rest | same |
| Movement | 2 km sprinted, 200 m swum, 30 m climbed | same, a quarter of it Burdened | same |
| Survival | 6 animals, 10 plants, 8 cooked, 4 biomes, ½ structure | 5 / 10 / 12 / 2 / ½ | 5 / 10 / 12 / ¾ / 0.4 |
| Smithing | 6 wooden/stone tools, 8 ingots, an iron pickaxe | hours 2–3: the iron kit (34 metal units) and 25 ingots an hour; hours 4–5: 25 ingots, a few repairs | 25 ingots, the diamond kit over ten hours, repairs, a netherite upgrade now and then |

**Result** (not favoured, Wits 3; a favoured skill takes 0.8× the time). "Before" is the same session with the old
constants.

| Skill | XP/h, hour 6 on (before) | Level at 1 h / 3 h / 5 h | Hours to 6 (before) | to 12 (before) | to 18 | to 20 |
|---|---|---|---|---|---|---|
| Mining | 845 (same) | 3 / 7 / 9 | 2.0 | 9.4 | 26 | 35 |
| Blades, as your weapon | 756 (same) | 3 / 7 / 8 | 2.1 | 10.4 | 29 | 39 |
| Heavy Weapons, as your weapon | 788 (same) | 3 / 7 / 9 | 2.0 | 10.0 | 28 | 37 |
| Heavy Weapons, logs only | 32 (same) | 1 / 2 / 2 | 37 | 232 | — | — |
| Marksmanship | 431 (328) | 2 / 5 / 7 | 3.4 (4.3) | 17.9 (23.4) | 51 | 68 |
| Defense | 574 (58) | 0 / 5 / 7 | 3.0 (21.3) | 13.9 (129) | 39 | 51 |
| Athletics | 519 (79) | 4 / 6 / 7 | 2.5 (15.1) | 14.5 (94.5) | 42 | 56 |
| Survival | 238 (77) | 4 / 5 / 6 | 3.3 (12.4) | 28.9 (93.8) | 89 | 119 |
| Smithing | 340 (108) | 2 / 6 / 6 | 3.0 (11.6) | 22.4 (69.7) | 65 | 86 |

Mining and the melee skills don't race: they sit on the §1 target (700–850 XP an hour). A dedicated strip-miner with
an efficiency pickaxe (1,500 blocks an hour) earns about 1,800 an hour and reaches 12 in about 4 hours; that is the
"focused activity" case, not normal play.

### 7.3 Points and gates

Character XP in the session: level 7 after 1 hour, 14 after 3, 17 after 5, 24 after 10, 32 after 20, 50 after about 60.
Attribute points to place = 4 (background) + character level − 1.

| Gate | Skill gets there (mixed session) | Attribute points by then | Points the gate needs (from 3) |
|---|---|---|---|
| Tier II: skill 6 + attribute 6 | 2–3.4 h | 14–18 | 3 |
| Tier III: skill 12 + attribute 10 | 9–29 h | 26–41 | 7 |
| Tier IV: skill 18 + attribute 14 | 26–89 h | 39–53 | 11 |

The attribute gate is never what holds a player back: Strength 10 for Mining's tier III needs 7 points, which arrive at
character level 8 (about 1½ hours); Mining 12 arrives at 9½ hours with 26 points to place. Three attributes at 10 (21
points) are affordable before the first tier III opens, and three at 14 (33) before the first tier IV. The gates bind
only a character that spreads into four or five attributes.

Perk points (one per skill level) matched the tree: 2 at tier I, 6 at tier II (roots ×4 + the first tier II), 12 at tier
III (roots 6 + a tier II + a tier III = 11), 18 at tier IV (roots 6 + tier II + tier III + the mastery = 16). A full tree
costs 31, so 20 points bought about two thirds of it.

**Since the draft (2026-10-10, [DRAFT.md](DRAFT.md)) perks are not bought.** Every skill level to 6 deals a draft (three
cards, keep one), then every even level; odd levels from 7 bring a reroll. Thirteen drafts to skill 20 keep this budget:
the draft simulator (`DraftReportTest`) holds 9 ranks at skill 12 against the old 9, and 13 at 20 against 12, at about a
third more depth (DRAFT.md §6). A draft on every level would have handed over the whole deck by skill 18. The attribute
gate above is now soft: below it a card is dealt 0.4 times as often, never hidden; the table still says when it bites.

## 8. Respec draughts (CHARACTER.md §6)

Since the draft, forgetting a skill turns each of its drafted ranks back into a waiting draft of that skill (DRAFT.md §8).
**Cards v2 changes this (CARDS.md §7):** the Draught of Forgetting becomes the key to a rebuild sitting at a trainer or
Card Shrine (plus a fee per card and a scar); it no longer refunds drafts. The recipes below stay.

Crafted shapeless, one recipe per skill (`data/mettle/recipe/draught_of_*.json`). Every draught shares a base of
**glass bottle + amethyst shard** (a geode is the gate: reachable early, never free) and a flavour ingredient; the
skill's own ingredient is roughly one tool's worth of that skill's work. Draughts stack to 16 and weigh 0.35 kg.

| Draught | Ingredients besides bottle + amethyst shard |
|---|---|
| Forgetting · Blades | poppy, iron sword |
| Forgetting · Heavy Weapons | poppy, iron axe |
| Forgetting · Marksmanship | poppy, bow |
| Forgetting · Defense | poppy, shield |
| Forgetting · Athletics | poppy, rabbit's foot |
| Forgetting · Mining | poppy, raw gold |
| Forgetting · Survival | poppy, campfire |
| Forgetting · Smithing | poppy, smithing table |
| Unbinding (attributes) | fermented spider eye, ender pearl |

Poppy is the forgetting (sleep, Lethe); the fermented spider eye is the brewer's "reverser". Recipes unlock in the
recipe book when an amethyst shard is picked up. No cooldown and no rising cost; tune the ingredients if players
respec too freely (a second amethyst shard, or a gold ingot, is the next step up).

## 9. With VOID Hearth (`compat.hearth.HearthRules`)

A trainer's lesson also brings one draft reroll (DRAFT.md §5).

Hearth owns how the world reacts; these are the numbers Mettle hands it. All in `HearthRules`, checked by
`HearthRulesTest`. INTEGRATION.md §1.2 says where each one is used.

### 9.1 Checks

`PlayerSkills.check(player, skill, difficulty)`: **odds = 50 % + 5 % × (skill level + governing attribute ÷ 2 −
difficulty), within 5–95 %** (CHARACTER.md §7; Hearth's `PlayerSkills.odds` documents the same rule). A check is even
when the difficulty equals the level plus half the attribute; each level is worth 5 %, each attribute point 2.5 %.

| | Easy (5) | Fair (10) | Hard (15) | Heroic (20) |
|---|---|---|---|---|
| Fresh (skill 0, attribute 3) | 32.5 % | 7.5 % | 5 % | 5 % |
| Skill 5, attribute 6 | 65 % | 40 % | 15 % | 5 % |
| Skill 10, attribute 10 | 95 % | 75 % | 50 % | 25 % |
| Skill 20, attribute 14 | 95 % | 95 % | 95 % | 85 % |

No XP from the roll itself: Hearth decides what a check is worth and reports it (§3).

### 9.2 Prices and business

The player's **edge** at a Hearth market = 0.5 % × (Presence − 3) + 0.5 % × Speech level + 5 % with Coin Sense
(Merchant), kept within −10 %…+25 %. Buying costs × (1 − edge), selling pays × (1 + edge). Hearth multiplies every mod's
factor, clamps the product to 0.5–1.5 and keeps what the town pays under 95 % of what it charges.

| Character | Edge | Buys at | Sells at |
|---|---|---|---|
| Fresh (Presence 3, Speech 0) | 0 | 100 % | 100 % |
| Merchant (Presence 5, Speech 0, Coin Sense) | 6 % | 94 % | 106 % |
| Presence 10, Speech 10 | 8.5 % | 91.5 % | 108.5 % |
| Presence 20, Speech 20, Coin Sense | 23.5 % | 76.5 % | 123.5 % |
| Presence 1 (respec'd down) | −1 % | 101 % | 99 % |

Speech XP for **business**: 5 per crown (100 bits) that changes hands at a counter, bought or sold, for the first 20
crowns a sim day; 1 per crown after that. Turning goods over to farm it loses the market's spread each time.

### 9.3 Quests

Base skill XP on completion (before favoured and Wits; character XP follows, since it is all skill XP). Failed and
abandoned quests give nothing.

| Type | Skill | XP |
|---|---|---|
| bounty · caravan · law | the combat skill used most since the last fighting quest | 120 |
| exploration · scouting | Survival | 150 |
| courier · escort | Athletics | 100 |
| disaster | combat (wolves) · Mining (mines, surveys) · Survival | 100 |
| crime | Stealth | 100 |
| supply · contract | Mining · Smithing · Survival, by the goods | 80 |
| official | combat (guard work) · by goods (builder) · Speech (reeve, priest, lord) | 80 |
| personal | Speech | 80 |
| gathering | Survival | 60 |

An exploration quest is about 13 minutes of focused activity at level 5 rates; a bounty about 10.

### 9.4 Trainers

| Settlement | Teaches up to |
|---|---|
| camp | 4 |
| hamlet | 6 |
| village | 10 |
| town | 15 |
| city | 20 |

A **lesson** (once a sim day per trainer) gives 40 % of the XP between the player's level and the next, rounded, then
the favoured and Wits multipliers. Its **fee** is 2.5 × (level + 1)² + 15 bits, to the nearest 5.

| Level now → next | Lesson XP | Fee | Worth (at ~700 XP an hour) |
|---|---|---|---|
| 0 → 1 | 4 | 20 bits | seconds |
| 2 → 3 | 49 | 40 bits | 4 minutes |
| 5 → 6 | 181 | 105 bits (a crown) | 15 minutes |
| 9 → 10 | 447 | 265 bits | 40 minutes |
| 14 → 15 | 901 | 580 bits | 1¼ hours |
| 19 → 20 | 1,474 | 1,015 bits (more than a horse, 800) | 2 hours |

Early lessons are a nudge; near mastery a lesson is hours of work for ten crowns: coin buys time, and the fee's square
keeps it a choice. Each trainer teaches once a sim day (20 minutes of play), so a city with several smiths can give a
player who can pay several Smithing lessons a day.
A **city master's Teaching** costs 1,000 bits (10 crowns) and needs the skill at 6.

## 10. The draft (DRAFT.md)

Cards v2 keeps the cadence, hand, wildcard and reroll rows; the rarity row becomes the depth odds, and rarity gets its
own table (§11).

| | Value | Where |
|---|---|---|
| Drafts | one per skill level to 6, then one per even level (13 to skill 20); odd levels from 7 give a reroll | `DraftRules.HALF_RATE_FROM` = 7 |
| Hand | 3 cards, keep one | `HAND` |
| Slot odds at skill L | Common 62 − 1.5 L · Rare 26 + 0.8 L · Epic 8 + 0.6 L · Cursed 4 + 0.1 L (%) | `odds` |
| Soft attribute gate | a card below its tier's attribute is dealt 0.4× as often | `UNDER_GATE_WEIGHT` |
| Wildcard | 12 % of hands, one card from one of the last four skills used | `WILDCARD_CHANCE` |
| Rerolls | start 2; +1 per character level, odd skill level from 7, feat, Hearth lesson, spent-deck draft; hold at most 5 | `START_REROLLS`, `MAX_REROLLS` |
| Cursed cards | two per released skill, tier II and III; numbers in DRAFT.md §4 | `character.draft.Cursed`, perk data |

## 11. Cards v2 (CARDS.md, wave A built 2026-10-10)

Every lever, in `config/mettle/cards.json` (CARDS.md §15). The power limits and the simulator's checks are CARDS.md §14.

| | Value | Where |
|---|---|---|
| Rarity at skill L | Common 70 − 1.5 L · Rare 24 + L · Epic 6 + 0.5 L (%); cursed perks always Cursed | `rarity_odds` |
| Root rank | Common I · Rare II · Epic III | rule |
| Traits | Common 0–1 · Rare 1–2 · Epic 2–3 · Cursed 1–2; values low two thirds / whole / top two thirds of the range | trait data |
| Trait caps | broad ≤ 12 % rolled (15 % boosted) · narrow ≤ 25 % (30 %) · one trait's total ≤ 2 × its card cap | `trait_total_cap` |
| Attribute boost | +1.5 % of a trait's value per point of its attribute above 3 (×1.255 at 20) | `trait_boost_per_point` |
| Requirement | tier I 2 / 4 / 7 · II 6 / 7 / 9 · III 12 / 13 / 15 · Mastery 18 / 19 / 20 (Common / Rare / Epic); cursed 6 / 12 | rule |
| Deck slots | 3 at skill 2, +1 every 3 levels, 8 at 17; +1 at Wits 8, +2 at Wits 14 | `slots_*`, `wits_slots` |
| Duplicates | dealt at ½ weight per copy owned, at least ¼ | `duplicate_weight` |
| Sets | 14 keywords; 2 / 4 / 6 cards; 6 needs two skills; 3 active; a pin changes once an in-game day | `active_sets` |
| Rebuild | one Draught of Forgetting per skill per sitting + 40 / 120 / 350 / 900 bits per card by requirement (Mythic 1,000, no draught); emeralds 1 / 3 / 8 / 20 / 24 without Hearth; one scar per card (a city master: one in two) | `rebuild_fees` |
| Free swaps | one per character level, held up to 3 | `free_swaps_max` |
| Trading | 8 blocks, Lock, 3 s countdown; attunement 24,000 ticks | `trade_*`, `attune_ticks` |
| Binder | 300 cards; release 3 for 1 reroll | `binder_max`, `release_per_reroll` |
| Mythics | 0.5 % per draft from skill 15 (once per draft, each drawn Mythic once per world); 2 slotted | `mythic_*` |

Simulated (`CardReportTest`, CARDS.md §20): a skill-20 deck is 1.11× v1's draft with base slots and 1.21× with Wits 14
(limits 1.15× and 1.3×); a full deck rebuild costs about 520 bits at skill 10, 1,220 at 15 and 2,350 at 20, plus a
draught. Levers added in code: `card_cap_boost` 1.25 (a card's trait cap over its max), `free_rebuild_below_level` 5.

**Approved (BUILD_FEEL.md §7, not built):** momentum perk lean 1.0 and keyword lean 0.5, times each keyword's
`momentum` (Tide, Deep 1.0; Kin, Warden 0.5; Gale, Echo 0.25; the rest 0), commitment counted to 4, Focus +2; the
6 tier wakes when two of its decks reach skill 12; boons from skill 7 (Temper ¼ of a range, three tempered cards a
deck; Inscribe one card a deck, the keyword already in that deck); 24 drawn Mythics (three a skill), shards forge an
unclaimed one (30, 45, 60). Simulated by `BuildFeelReportTest`: 1.12× / 1.23× v1 (worst skill 1.13× / 1.24×), deck
overlap 0.16, every committed 6-set at skill 12–17.
