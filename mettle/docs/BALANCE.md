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
| Blades / Heavy | modifiers: passive animals ×0.5 · players ×0.5 · spawner mobs ×0.1 | |
| Marksmanship | per point of damage | 1.2 |
| Marksmanship | distance bonus | +1 per 10 blocks |
| Marksmanship | kill bonus | ¼ max health |
| Defense | per point blocked by a shield / absorbed by armour | 1 / 0.5 |
| Athletics | per 40 m sprinted · 20 m swum · 10 m climbed | 1 |
| Athletics | load multiplier: Burdened / Overburdened or Pinned | ×2 / ×3 |
| Athletics | cap | 120 XP a minute |
| Survival | animal killed (≥ 50 % of the damage yours) · wild plant or berries picked · food cooked (per item) | 4 · 1 · 1 |
| Survival | first visit: biome / structure / Hearth settlement / Expanse landmark | 25 / 40 / 60 / 150 |
| Smithing | per metal unit in a crafted tool, weapon or armour piece (ingot = 1, diamond = 2) | 8 |
| Smithing | wooden or stone tool · ingot taken from a furnace · anvil repair · smithing upgrade | 2 · 1 · 10 + 5 per level spent · 50 |
| Any | skill check passed / failed | 15 / 5 × difficulty ÷ 10 |
| Any | Hearth quest completed | 60–200 by type (INTEGRATION.md) |
| Any | trainer session | see INTEGRATION.md |

## 4. Attributes per point (modifier ids `mettle:attr/<attribute>/<vanilla>`)

| Attribute | Vanilla attribute | Operation | Amount per point above 3 |
|---|---|---|---|
| Strength | `mettle:carry_capacity` | add_value | +3 (applies from 1: Strength 3 = +9) |
| Strength | `attack_damage` | add_multiplied_base | +0.02 |
| Strength | `block_break_speed` | add_multiplied_base | +0.02 |
| Agility | `movement_speed` | add_multiplied_base | +0.01 |
| Agility | `attack_speed` | add_multiplied_base | +0.015 |
| Agility | `sneaking_speed` | add_multiplied_base | +0.02 |
| Vitality | `max_health` (permanent modifier) | add_value | +1 per 2 points (½ heart) |
| Vitality | hunger drain | behaviour | −2 % |
| Wits | skill XP | internal | +2 % |
| Presence | Hearth prices / reputation gains | compat | ±0.5 % / +3 % |

Points below 3 (only via respec mistakes; the minimum is 1) apply the same rates negatively, except capacity, which
stays at +3 per point from 1.

## 5. Weight

### 5.1 Capacity and bands

| | Value |
|---|---|
| Base capacity | 40 kg |
| Strength | +3 kg per point (Strength 3 → 49 kg total, 20 → 100 kg) |
| Perks | Pack Mule +8 · Beast of Burden +15 · Ox +25 |
| Gear | Satchel +8 · Leather Pack +15 · Frame Pack +25 · Belt +3 · Tool Belt +5 |
| Ox Draught (Alchemy) | +20 kg for 5 min |
| Bands | Burdened ≥ 75 % · Overburdened ≥ 100 % · Pinned > 125 % |
| Speed | −10 % / −30 % / −70 % |
| Jump | 0 / −40 % / −100 % |
| Hunger | ×1.5 / ×1.5 / ×1.5 |
| Fall damage | +25 % / +25 % / +25 % |

Reference loads:

| Character | Capacity |
|---|---|
| Fresh character (Strength 3, no gear) | 49 kg |
| Fresh Smith with the kit belt | 61 kg |
| Mid game (Strength 10, Leather Pack, Belt, Pack Mule) | 106 kg |
| Late game (Strength 16, Frame Pack, Tool Belt, Pack Mule, Beast of Burden, Ox) | 166 kg |

### 5.2 Leaf weights (kg per item)

Everything not listed is derived from recipes (BURDEN.md §1). Smelted and cooked outputs are leaves, and cooked food
weighs the same as raw.

| Group | Items → kg |
|---|---|
| Coins (Hearth) | bit, mark, crown → 0.01 |
| Gems and dusts | diamond, emerald, quartz, amethyst shard 0.05 · lapis, redstone, glowstone dust 0.02 · coal 0.1 · charcoal 0.08 |
| Metals | iron ingot 0.5 · gold ingot 0.6 · copper ingot 0.4 · netherite scrap 0.8 · raw iron 0.6 · raw gold 0.7 · raw copper 0.5 · ancient debris 1.0 |
| Stone-like | cobblestone, stone, andesite, diorite, granite, tuff, calcite 0.25 · deepslate, cobbled deepslate, basalt, blackstone 0.3 · netherrack 0.15 · end stone 0.2 · obsidian, crying obsidian 0.6 · ores: their stone + 0.1 |
| Earth | dirt, grass block, sand, red sand, mud 0.2 · gravel 0.25 · clay ball 0.05 · snowball 0.02 · ice 0.15 |
| Wood | any log or stem 0.8 (planks then derive to 0.2, sticks to 0.1) · bamboo 0.05 |
| Plants | saplings, flowers, leaves 0.05 · seeds 0.005 · sweet berries, glow berries 0.02 |
| Food | wheat 0.05 · apple 0.15 · carrot, beetroot 0.1 · potato 0.15 · raw beef, pork, mutton, fish 0.3 · chicken 0.4 · egg 0.06 · melon slice 0.1 · pumpkin 2.0 · melon 1.8 |
| Animal | leather 0.3 · rabbit hide 0.1 · string, feather, paper 0.01 · wool (block) 0.15 · bone 0.1 · shulker shell 0.3 |
| Liquids | water, milk or lava in a bucket: bucket + 1.0 / 1.0 / 1.5 · potion 0.35 · glass 0.2 |
| Soil | podzol, mycelium, rooted dirt, dirt path, farmland, soul sand, soul soil, suspicious sand 0.2 · suspicious gravel 0.25 · nylium 0.15 · moss blocks 0.1 · sculk 0.2 |
| Stone (uncraftable) | bedrock, reinforced deepslate, gilded blackstone 0.3 · budding amethyst 0.25 · pointed dripstone 0.06 |
| Plants (uncraftable) | mangrove roots 0.4 · cactus 0.2 · sugar cane 0.05 · kelp 0.02 · mushrooms 0.02 · mushroom blocks, shroomlight 0.1 · corals 0.05 · coral blocks 0.25 |
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
| Satchel | 0.8 | +8 | 9 | 5 leather, 2 string |
| Leather Pack | 1.5 | +15 | 18 | satchel, 4 leather, 2 string, 2 iron nuggets |
| Frame Pack | 3.0 | +25 | 27 | leather pack, 4 sticks, 2 iron ingots, 2 leather (Hearth: ~2 crowns) |
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
| character | `pvp_perks` | `full` (`reduced` halves perk damage effects against players, `off` disables them) |
| character | `pvp_xp_scale` | 0.5 |
| character | `enabled_skills` | all shipped |
| burden | `enabled` | true |
| burden | `weight_scale` | 1.0 |
| burden | `base_capacity` / `capacity_per_strength` | 40 / 3 |
| burden | `bands` | 0.75 / 1.0 / 1.25 |
| burden | `creative_exempt` / `elytra_blocked_when_overburdened` | true / true |
| gear | `enabled` / `container_pockets` | true / true |

## 7. Respec draughts (CHARACTER.md §6)

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
