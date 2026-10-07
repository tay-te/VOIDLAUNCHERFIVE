# VOID Mettle — character: backgrounds, attributes, skills, perks

Owned by the **character** module (data and rules) and the **ui** module (screens). Numbers here are first drafts;
the tunable values are collected in [BALANCE.md](BALANCE.md).

The whole model in one picture:

```
Background (chosen once)   → +4 attribute points placed for you, 2 favoured skills (+25 % XP), a kit, a signature perk
Attributes (5 × 1–20)      ← start 3 each; +1 point per character level
Skills (15 × 0–20)         ← rise by doing; every level = 1 perk point in that skill's tree
Perk trees (12 perks each) ← 4 tiers; a tier needs the skill level AND the governing attribute
Character level (1–50)     ← all skill XP added together
```

---

## 1. Backgrounds

Chosen on first join from the picker (UI.md §2). A background is where you start, never a limit: every skill is open
to everyone.

| Background | Attributes | Favoured skills | Signature perk | Kit |
|---|---|---|---|---|
| **Soldier** | Str +2, Vit +2 | Blades, Defense | **Rally**: killing a hostile heals 1 heart (10 s cooldown) | iron sword, shield, leather tunic, 4 bread |
| **Ranger** | Agi +3, Vit +1 | Marksmanship, Survival | **Field Dressing**: animals you hunt drop an extra meat and hide | bow, quiver with 32 arrows, leather cap, 4 cooked meat |
| **Rogue** | Agi +2, Pre +2 | Stealth, Thievery | **Backstab**: sneak attacks on unaware foes deal double damage | stone sword, dyed dark leathers, satchel |
| **Smith** | Str +3, Wit +1 | Mining, Smithing | **Smith's Eye**: see exact durability; anvils never say "Too Expensive" | iron pickaxe, 6 iron ingots, belt |
| **Herbalist** | Wit +3, Vit +1 | Alchemy, Husbandry | **Herb Lore**: potions you brew last 50 % longer | brewing stand, 4 glass bottles, 6 nether wart, 2 blaze powder |
| **Merchant** | Pre +3, Wit +1 | Speech, Riding | **Coin Sense**: better prices in towns, and your coins weigh half | coin pouch with 3 gold crowns (6 emeralds without Hearth), satchel, lead |
| **Scholar** | Wit +3, Pre +1 | Scholarship, Alchemy | **Well Read**: double XP from books; enchanting costs one level less | 3 books, book and quill, map, lantern |
| **Homesteader** | Vit +3, Str +1 | Husbandry, Survival | **Green Thumb**: crops you plant sometimes sprout a stage early | iron hoe, 3 kinds of seed, 4 bread, lead |
| **Wanderer** | +4 placed by you | **any two released skills you pick** | none: freedom instead | bedroll (a bed that doesn't set spawn), 4 bread |

- How each signature perk works in code (`character.signature`, built 2026-10-05):
  - **Rally:** a hostile kill heals 1 heart, at most once per 10 s, only if you are hurt.
  - **Field Dressing:** an adult animal you kill rolls its loot table a second time.
  - **Backstab:** a sneaking melee hit on a mob that isn't targeting you is doubled before armour.
  - **Smith's Eye:** exact durability in tooltips; the anvil's 40-level "Too Expensive" cap is lifted. You still pay every level.
  - **Herb Lore:** a stand remembers whether the last player to open it was an Herbalist. What it brews lasts ×1.5, keeps its name, and is marked so it is never extended twice.
  - **Coin Sense:** villagers take a tenth off (at least 1) via special prices; Hearth markets join through the price hook.
  - **Well Read:** a 2- or 3-level enchant gives one level back.
  - **Green Thumb:** a planted crop has a 25 % chance to start one stage grown.
  - Each one sends a feedback event for the HUD chip.
- Kits are item lists inside each background's JSON (`data/mettle/mettle/background/<id>.json`, field `kit`), so servers can change them and the picker can show them.
- The picker can be dismissed. A player who closes it without choosing plays as an undecided Wanderer, and the K
  screen keeps a "Choose your background" button until they do. Nothing blocks play.
- A background cannot be changed in survival (it is your story). Ops can reset one: `/mettle background reset <player>`
  reopens the picker and refunds everything except skill XP.

## 2. Attributes

Five, each 1–20. Everyone starts at 3 in each; the background adds 4; each character level from 2 to 50 adds one point
to place. That is 68 points by level 50: enough for three attributes at 14+ (capstone tiers) or two at 20.

| Attribute | Per point (as vanilla attribute modifiers where possible) | Governs |
|---|---|---|
| **Strength** | +5 kg carry capacity, +2 % melee damage, +2 % mining speed | Heavy Weapons, Mining, Smithing |
| **Agility** | +1 % movement speed, +1.5 % attack speed, +2 % sneaking speed | Blades, Marksmanship, Stealth, Riding |
| **Vitality** | +½ heart per 2 points above 3, −2 % hunger drain | Defense, Athletics, Survival, Husbandry |
| **Wits** | +2 % XP in every skill | Alchemy, Scholarship |
| **Presence** | prices in towns ±0.5 %, reputation gains +3 % (Hearth); weight in speech checks | Speech, Thievery |

Spending a point is one click on "+" in the character screen, and it applies at once. Points are spent, not
auto-placed; an unspent point shows a badge on the K hint.

## 3. Skills

Fifteen, each 0–20. Wave 1 ships first; wave 2 and 3 follow the phases in ARCHITECTURE.md §9.

| Wave | Skill | Attribute | You get better by |
|---|---|---|---|
| 1 | **Blades** | Agility | fighting with swords and spears |
| 1 | **Heavy Weapons** | Strength | fighting with axes, maces and tridents (melee) |
| 1 | **Marksmanship** | Agility | hitting with bows, crossbows and thrown tridents; longer shots count more |
| 1 | **Defense** | Vitality | blocking with a shield and taking hits on armour |
| 1 | **Athletics** | Vitality | sprinting, swimming and climbing, **double while carrying a heavy load** |
| 1 | **Mining** | Strength | breaking natural stone and ore; harder blocks count more |
| 1 | **Survival** | Vitality | hunting, foraging, cooking, and first visits to biomes and places |
| 1 | **Smithing** | Strength | crafting tools, weapons and armour, anvils and smithing tables |
| 2 | **Alchemy** | Wits | brewing |
| 2 | **Scholarship** | Wits | enchanting, reading skill books and lore |
| 2 | **Husbandry** | Vitality | harvesting grown crops, breeding and taming |
| 2 | **Riding** | Agility | distance ridden on horses, camels and other mounts |
| 2 | **Stealth** | Agility | moving unseen near mobs, sneak attacks |
| 2 | **Speech** | Presence | trading in towns, winning speech checks (Hearth), villager trades |
| 3 | **Thievery** | Presence | lockpicking and pickpocketing (Hearth's law module runs the minigames) |

### 3.1 XP and levels

- Each skill has an XP total; its level comes from the curve in BALANCE.md (fast at first: level 1 in a minute,
  level 5 in about an hour of that activity, level 10 in an evening or two, 20 is mastery).
- XP is multiplied by: favoured skill ×1.25, Wits +2 % per point, perks, server `xp_scale`. Nothing else.
- Character XP is the sum of all skill XP earned; character level uses its own curve (BALANCE.md).
- Trainers (Hearth) and books grant skill XP directly (INTEGRATION.md).
- Vanilla XP orbs and levels are untouched. They stay the currency of enchanting and anvils.

### 3.2 Where XP comes from (hooks, verified against 26.3)

| Skill | Source | Hook |
|---|---|---|
| Mining | block broken, by block | `PlayerBlockBreakEvents.AFTER` |
| Blades, Heavy Weapons | damage dealt to a living target, by weapon tag in the main hand | `ServerLivingEntityEvents.AFTER_DAMAGE` (attacker = `source.getEntity()`) |
| Marksmanship | arrow/bolt/trident hits, plus distance | `AFTER_DAMAGE` with `source.getDirectEntity()` an `AbstractArrow` / thrown trident |
| Defense | damage blocked by a shield; damage absorbed by armour | `AFTER_DAMAGE` (`blocked`), `DAMAGE_BLOCKED_BY_SHIELD` stat |
| Athletics | metres sprinted, swum, climbed | `ServerPlayer#awardStat` mixin (SPRINT/SWIM/CLIMB cm stats) |
| Survival | animals killed, wild plants harvested, food cooked (taken from furnace/smoker/campfire), first visit to a biome or structure | `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`, block break, `ItemStack#onCraftedBy`, discovery check |
| Smithing | tools/weapons/armour crafted, smithing upgrades, anvil repairs | `ItemStack#onCraftedBy` (ResultSlot, SmithingMenu), `AnvilMenu#onTake` |
| Alchemy | potions brewed (stamped with the brewer, see §3.3) | `BrewingStandBlockEntity.doBrew` + `BrewingStandMenu$PotionSlot#onTake` |
| Scholarship | enchanting, reading books | `Player#onEnchantmentPerformed`, book use |
| Husbandry | mature crops harvested, animals bred, animals tamed | block break (age = max), `Animal#finalizeSpawnChildFromBreeding`, `TamableAnimal#tame` |
| Riding | metres ridden | `awardStat` (HORSE/… cm stats) |
| Stealth | seconds sneaking within 12 blocks of a hostile that is not targeting you; sneak attacks | 20-tick check; `AFTER_DAMAGE` |
| Speech | crowns of business at a Hearth market, checks won; villager trades | compat events; `AbstractVillager#notifyTrade` |

The single `ServerPlayer#awardStat` mixin is the cheapest funnel for everything vanilla already counts; the targeted
hooks above cover what it can't (weapon type, distance, block hardness).

### 3.3 Anti-farm (fair, invisible to normal play)

- **Placed blocks give nothing.** XP-bearing blocks (ores, stone-likes, logs, crops' seeds) placed by a player are
  recorded in a per-chunk ledger; breaking them gives no Mining/Survival XP. Silk-touched ore re-placed is "placed".
- **Farm mobs give little.** Mobs from monster spawners are flagged at spawn (`mettle.spawner`): 10 % XP. Trial spawners are a fight, not a farm, and keep full XP. Mobs that die mostly
  from fall, lava, suffocation or drowning give none; a kill counts if the player dealt ≥ 50 % of the damage.
- **Repetition tapers.** Per skill and source, XP from the same kind of action within 5 minutes tapers to 25 % after
  the first ~60 occurrences (mob grinders, arrow spam at one target, sprinting in circles). It recovers in minutes.
  Normal play never sees it.
- **Brewing is stamped.** The player who last put an ingredient in a stand is recorded on the stand; brewed potions
  carry that player. Taking potions you didn't brew gives nothing.
- **Movement** counts only on foot or swimming (not boats, carts, elytra) and caps at 60 XP a minute. Defense caps at 60 XP a minute too.
- **Damage on players** (PvP) gives half XP and is off on servers with `pvp_perks: off`.

## 4. Perk trees

> **Superseded by [TREES.md](TREES.md) (2026-10-05).** Every tree now has ranked roots, two named paths and three
> Masteries (pick one), plus earned Feats, Synergies and Teachings. The grids below are the original ideas the trees
> were built from; where they disagree, TREES.md wins. Mining is built and verified in game; the other trees are
> designed there and built next.


Every skill has a tree of **12 perks in 4 tiers of 3**. Rules that hold for every tree:

| Tier | Opens at skill level | Needs governing attribute | Cost per perk |
|---|---|---|---|
| I | 2 | — | 1 point |
| II | 6 | 6 | 2 points |
| III | 12 | 10 | 3 points |
| IV | 18 | 14 | 4 points |

- Each skill level gives 1 point **for that skill's tree** (20 by level 20). The whole tree costs 30, so you choose
  about two thirds of it. That is the build.
- A locked perk always says why: "Needs Mining 6 and Strength 6".
- Learning is one click in the side panel ("Learn · 2 points"), with no confirmation dialog. Mistakes are cheap: see §6.
- Perks are data (`data/<ns>/mettle/perk/<skill>/<perk>.json`): `tier`, `cost`, optional `excludes`, and a list of
  effects:
  - `mettle:attribute` — a vanilla or Mettle attribute modifier, with an optional `when` condition (`holding <tag>`,
    `sneaking`, `sprinting`, `blocking`, `below_y`, `in_darkness`, `full_armor`, `health_below`)
  - `mettle:xp` — XP multiplier for a skill
  - `mettle:weight` — weight multiplier for an item tag
  - `mettle:band` — shift a load band threshold or scale its penalty
  - `mettle:behaviour` — a named behaviour implemented in code (`mettle:motherlode`, …)
- **Wording rule**: the description is one sentence a player understands without numbers explained elsewhere.

Below: the eight wave-1 trees. `[attr]` = an attribute modifier, `[weight]` / `[band]` = burden effects, everything
else is a behaviour.

### Mining (Strength)

| Tier | Perks |
|---|---|
| I | **Quarryman** — stone-like blocks mine 20 % faster. · **Ore Sense** — sneaking with a pickaxe, ore near the block you look at gives a soft chime. · **Sure Footing** — mining while climbing, swimming or in the air is only half as slow. |
| II | **Rock Hauler** — stone, ore, raw metal and ingots weigh 40 % less for you. `[weight]` · **Gem Cutter** — 10 % chance of an extra diamond, emerald, lapis, quartz or amethyst. · **Hard Hat** — falling blocks and dripstone don't hurt you; +2 armour below y 40. |
| III | **Strike the Seam** — the first ore you break in each vein drops double. · **Lantern Eyes** — in deep darkness a faint light surrounds you that only you can see. · **Tunneler** — sneak-mining with a pickaxe breaks a 1×2 passage. |
| IV | **Motherlode** — sneak-mine an ore to break its whole vein (up to 24 blocks). · **Deep Delver** — below y 0 you take 30 % less damage and mine 25 % faster. · **Mountain Breaker** — sneak-mining stone breaks 3×3, at triple durability cost. |

### Blades (Agility)

| Tier | Perks |
|---|---|
| I | **Quick Draw** — your first hit on a mob that hasn't noticed you deals +50 %. · **Light Feet** — attacking doesn't break your sprint. · **Riposte** — after you block with a shield, your next blade hit within 1.5 s deals +60 %. |
| II | **Wide Arc** — sweeps hit everything around for 75 % of your hit. `[attr]` · **Bleed** — critical hits make the target bleed for 3 damage over 3 s. · **Disarm** — critical hits have a 20 % chance to knock the weapon out of a mob's hands. |
| III | **Momentum** — each hit on the same target within 2 s adds +10 % damage, up to +40 %. · **Fencer** — +20 % attack speed with blades. `[attr]` · **Second Cut** — fully charged hits strike again for 30 % a moment later. |
| IV | **Whirlwind** — sneak and attack to spin, hitting everything within 3 blocks (8 s cooldown). · **Executioner** — +50 % damage to targets below 30 % health. · **Duelist** — with only one hostile within 8 blocks, deal 25 % more and take 20 % less from it. |

### Heavy Weapons (Strength)

| Tier | Perks |
|---|---|
| I | **Woodsman** — axes chop wood 30 % faster. · **Cleave** — axe hits spill 30 % of their damage onto an enemy beside the target. · **Heft** — +50 % knockback with heavy weapons. `[attr]` |
| II | **Armour Crusher** — heavy hits ignore 25 % of the target's armour. · **Concussion** — critical hits slow the target for 2 s. · **Headsman** — axe kills have a 20 % chance to drop the mob's head. |
| III | **Timber** — sneak-chop the base of a tree to fell the whole trunk (up to 64 logs). · **Rage** — every hit you take gives +5 % damage for 6 s, up to +25 %. · **Iron Grip** — while holding a heavy weapon: no knockback, +2 armour toughness. `[attr]` |
| IV | **Earthshaker** — a mace smash throws back and staggers every enemy within 4 blocks. · **Berserker** — below 30 % health: +40 % damage and +30 % attack speed. `[attr]` · **Unstoppable** — heavy hits can't be blocked by shields, and you can't be slowed while swinging. |

### Marksmanship (Agility)

| Tier | Perks |
|---|---|
| I | **Steady Aim** — bows draw 25 % faster. · **Fletcher** — arrow recipes make 6 instead of 4. · **Retrieval** — 50 % chance to get your arrow back from what it killed. |
| II | **Long Shot** — +4 % damage for every 5 blocks beyond 15, up to +40 %. · **Hunter's Mark** — mobs you hit glow for you alone for 5 s. · **Ranger's Pace** — drawing a bow no longer slows you down. |
| III | **Double Nock** — 20 % chance to loose a second arrow for free. · **Pinning Shot** — fully drawn hits slow the target by half for 2 s. · **Silent Shot** — your arrows don't alert other mobs; +50 % damage to unaware targets. |
| IV | **Deadeye** — fully drawn shots always crit and fly faster and flatter. · **Volley** — sneak-shoot a fully drawn bow to loose three arrows in a spread (uses three). · **Quiverwise** — crossbows reload 40 % faster; arrows drawn from your quiver have a 25 % chance not to be used. |

### Defense (Vitality)

| Tier | Perks |
|---|---|
| I | **Shield Up** — your shield raises instantly. · **Thick Skin** — +2 armour. `[attr]` · **Brace** — no knockback while blocking. `[attr]` |
| II | **Shield Bash** — attack while blocking to shove the target back and stun it for 1 s. · **Armour Training** — worn armour weighs 30 % less. `[weight]` · **Second Wind** — once every 5 minutes, dropping below 4 hearts gives Regeneration II for 5 s. |
| III | **Perfect Block** — blocking just as a hit lands reflects half of it back. · **Bulwark** — your shield also stops arrows and fireballs from the sides. · **Mender** — armour and shields wear 30 % slower. |
| IV | **Last Stand** — once every 20 minutes, a killing blow leaves you at 1 heart with 3 s of Resistance IV. · **Juggernaut** — in full armour: immune to knockback, +4 armour toughness. `[attr]` · **Guardian** — while you block, players right behind you take 40 % less damage. |

### Athletics (Vitality)

| Tier | Perks |
|---|---|
| I | **Pack Mule** — +8 kg carry capacity. `[attr]` · **Runner** — sprinting uses 25 % less hunger. · **Climber** — climb ladders, vines and scaffolding 50 % faster. |
| II | **Strong Back** — you only become Burdened at 85 % of capacity. `[band]` · **Swimmer** — swim 30 % faster and hold your breath 50 % longer. `[attr]` · **Soft Landing** — fall 3 more blocks without damage. `[attr]` |
| III | **Beast of Burden** — +15 kg, and a heavy load never pins you (Overburdened is the worst it gets). `[attr][band]` · **Vaulter** — while sprinting, step up full blocks without jumping. `[attr]` · **Second Breath** — the first 10 s of every sprint cost no hunger. |
| IV | **Ox** — +25 kg, and load slows you only half as much. `[attr][band]` · **Freerunner** — hold jump against a wall to climb up to 2 blocks. · **Marathon** — you can sprint at any hunger level. |

### Survival (Vitality)

| Tier | Perks |
|---|---|
| I | **Forager** — berries, herbs and wild plants give a bonus item 30 % of the time. · **Field Cook** — food you cook restores 1 extra saturation. · **Quiet Approach** — while you sneak, animals don't flee until you're within 4 blocks. |
| II | **Pathfinder** — 15 % faster on paths and roads. `[attr]` · **Iron Stomach** — raw meat, rotten flesh and spider eyes never poison you. · **Campfire Rest** — standing still by a lit campfire heals a heart every 4 s. |
| III | **Skinner** — animals you kill drop double leather, hide and wool. · **Weather Sense** — the HUD warns of rain and storms 2 minutes before they come. · **Stalker** — sneak attacks on animals are instant kills. |
| IV | **Lay of the Land** — each place you discover marks the nearest undiscovered landmark on your locator bar. · **Self-Sufficient** — all food fills 30 % more. · **Sixth Sense** — a heartbeat warns you when a hostile within 16 blocks starts hunting you. |

### Smithing (Strength)

| Tier | Perks |
|---|---|
| I | **Tinker** — anvil repairs and renames cost 1 level less. · **Salvager** — grindstones return a few nuggets from iron, gold and copper gear. · **Field Repair** — put a damaged tool and its ingot in a crafting grid to repair it by a quarter. |
| II | **Forged** — tools and weapons you craft last 15 % longer and carry "Forged by <you>". · **Light Hand** — armour you craft weighs 25 % less. `[weight]` · **Thrifty** — 20 % chance a recipe with ingots gives one back. |
| III | **Master Temper** — diamond and netherite gear you craft or upgrade lasts 25 % longer. · **Steady Anvil** — anvils never wear down when you use them. · **Keen Edge** — swords and axes you craft deal +1 damage. |
| IV | **Masterwork** — one in ten things you craft is Masterwork: +1 to its main stat and a gold name. · **Artisan** — gear you craft sells for 50 % more in towns (with villagers: one more emerald). · **Clean Work** — anvil repairs never add the "prior work" cost. |

### Wave 2 and 3 (headlines; full trees written before their phase)

| Skill | Headline perks |
|---|---|
| Alchemy | Distiller (brewing 50 % faster) · Herb Lore of the land (Expanse herbs as ingredients) · Potent (20 % chance of a stronger potion) · **Ox Draught** recipe (+20 kg for 5 min) |
| Scholarship | Speed Reader · Lore Keeper (lore books mark their place) · Enchanter's Hint (see one more enchantment) · Thrifty Enchanter |
| Husbandry | Green Fingers (crops near you grow faster) · Twins (15 % twin births) · Tamer · Shepherd |
| Riding | Saddle-born (+15 % mount speed) · **Saddlebags** (a mount carries 40 kg for you) · Mounted Combat · Roll Off (no dismount fall damage) |
| Stealth | Soft Step · Shadow (mobs notice you at 60 % of their range, via `getVisibilityPercent`) · Silent Fall · Vanish |
| Speech | Haggler (prices 10 % better) · Silver Tongue (+15 % check odds) · Charmer (reputation gains +50 %) · Intimidate (Strength 10) |
| Thievery | Nimble Fingers (wider lockpick sweet spot) · Light Touch (pickpocketing noticed 30 % less) · Fence's Friend · Cold Trail (stolen marks fade after a day) |

## 5. Character level

- Character XP = every skill XP gained, after multipliers. Level curve in BALANCE.md; level 50 is the cap.
- Each level from 2 to 50: one attribute point and the level-up chip.
- Character level is shown on the K screen header and nowhere else (no name-tag clutter).

## 6. Respec

Choices should be brave, not permanent. A player who regrets a tree drinks it away (built 2026-10-07,
`character.respec`).

| What | How | Cost |
|---|---|---|
| One skill's bought perks (roots, path perks, the Mastery) | drink a **Draught of Forgetting** brewed for that skill | crafted: glass bottle + amethyst shard + poppy + the skill's ingredient (BALANCE.md §8) |
| Placed attribute points | drink a **Draught of Unbinding** | crafted: glass bottle + amethyst shard + fermented spider eye + ender pearl |
| Everything, below character level 5 | `/mettle respec` | free (ops: any time, any player) |
| Favoured skills (Wanderer) | — | not yet: an op's background reset |
| Background | op only (`/mettle background reset`) | — |
| Skill XP | never | it is what you did |

- **Draught of Forgetting** (`mettle:draught_of_forgetting`). The skill it was brewed for is the component
  `mettle:forgets` (a skill id), so it shows as "Draught of Forgetting (Mining)" and its liquid takes the skill's colour.
  Drinking it (vanilla drinking, 1.6 s) refunds every **bought** perk of that skill: every root rank, path perk and the
  Mastery. The points return to the tree to spend again, and the Mastery choice is open again. **Earned** perks (feats,
  synergies, teachings), skill XP and levels, attributes and other trees are untouched. The chip says "You forget the
  ways of the Mining tree · 16 points returned".
- **Draught of Unbinding** (`mettle:draught_of_unbinding`). Returns every attribute point the player placed: the ones
  from character levels and, for a Wanderer, the four free points too (they come back as free points, so the whole
  placement is redone). A background's fixed bonus (Soldier's Str +2…) is not placed, so it stays. Perk-granted points
  (Seen It All) are earned and stay. Perks are not touched: a tree whose tier is now above your attribute keeps its perks
  until you forget it.
- **Refused and kept.** A draught with nothing to give back (no perks bought in that tree, no attribute points placed)
  doesn't start drinking and says why ("You have learned nothing in Mining to forget"). The client checks the synced
  character first; the server checks again when the drink finishes and only then uses the draught up (the bottle comes
  back). Everything is decided on the server.
- No cooldown and no rising price: each draught costs its ingredients, which is the decision. Drinking fires
  `MettleEvents.RESPEC` (`attributes` = false for Forgetting, true for Unbinding) and re-applies perk effects.
- Recipes are plain `crafting_shapeless` JSON whose result sets the `mettle:forgets` component, one file per skill
  (`data/mettle/recipe/draught_of_forgetting_<skill>.json`): servers rebalance a skill's cost by editing one file, add a
  draught for a wave-2 skill by adding one, and the recipe book shows each draught by name. A custom recipe type or a
  brewing-stand recipe was not needed (a brewing recipe can't carry the skill without code, and eight of them would be
  just as many entries).

## 7. Skill checks (for Hearth and future content)

`CharacterService.check(player, skill, difficulty)`: odds = 50 % + 5 % × (skill level + governing attribute ÷ 2 −
difficulty), clamped to 5–95 %. Callers show the odds before the player commits (Hearth's speech checks show
"Persuade · 65 %"). Difficulty is 1–25 by convention: 5 easy, 10 fair, 15 hard, 20 heroic. A passed check grants
the skill a little XP; a failed one grants a little too (you learn from failure, KCD2-style).
