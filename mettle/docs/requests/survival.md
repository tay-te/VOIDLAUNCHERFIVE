# Requests from the Survival tree

Everything in TREES.md §9 and §12 works today. Two general feats reach into numbers another unit owns, through small
chaining mixins in `mixin.survival`; the rest are data or compat that other units will want.

## 1. Character: perk XP bonuses in `Progress.multiplier` (Jack of All Trades)

**What.** Jack of All Trades makes skills below level 10 gain XP 25 % faster. `Progress.multiplier` has no hook, so
`mixin.survival.SurvivalProgressMixin` multiplies its return value (`@ModifyReturnValue`, chains with anything else).

**Proposed.** In `Progress.multiplier`, after the Wits term:

```java
m *= dev.voidmc.mettle.character.skill.survival.GeneralFeats.jackBonus(state, skill);
```

then delete `SurvivalProgressMixin` and its line in `mettle-survival.mixins.json`. (Or a general "XP multiplier from
perks" list in `Progress` that trees register into; Jack is the only user so far.)

## 2. Character: perk-granted attribute points in `Perks.attributePoints` (Seen It All)

**What.** Seen It All grants +1 attribute point. `Perks.attributePoints` counts only character levels, so
`mixin.survival.SurvivalPerksMixin` adds `GeneralFeats.bonusAttributePoints(player)` to its return value.

**Proposed.** In `Perks.attributePoints`: `return Math.max(0, state.characterLevel() - 1 - placed + bonusPoints(player));`
with `bonusPoints` summing a small list of perks that grant points (today: `mettle:general/seen_it_all` → 1). Then
delete `SurvivalPerksMixin`. A respec keeps the point (the feat is earned and stays).

## 3. Core: permanent max-health modifiers for perk effects (Seasoned)

Seasoned's +1 heart is a data `effect` on `minecraft:max_health`, which `EffectsApplier.perkEffects` adds as a
*transient* modifier. ARCHITECTURE §6 says max-health modifiers must be permanent so health isn't clamped to the
vanilla 20 when a world loads; Vitality's already is, perk ones aren't. Proposed: in `perkEffects`, use
`addPermanentModifier` when the attribute is `Attributes.MAX_HEALTH`. Affects every tree with a max-health perk.

## 4. Expanse: `#expanse:landmarks` and wary elk

- **Landmarks tag** (already in INTEGRATION.md §2): `data/expanse/tags/worldgen/structure/landmarks.json` listing the eight
  landmark structures. Survival reads it as soon as it exists: landmarks then give 150 XP, count for Seen It All and are
  what Lay of the Land points at. Until then it falls back to `#mettle:survival/notable` (villages, outposts, temples,
  huts, igloos, mansions, monuments, trail ruins, trial chambers, ancient cities, abandoned camps).
- **Elk and Quiet Approach.** Quiet Approach works on every `AvoidEntityGoal` (vanilla rabbits, foxes, ocelots… and
  Expanse animals that use it): a sneaking hunter with the perk isn't fled until within 4 blocks. Expanse's elk never
  flee a sneaking player at all (`Elk#spooks`), so for elk the perk adds nothing yet. If elk should also notice a
  careless sneaker (say within 8 blocks), make `spooks` return true for sneaking players closer than 8; Quiet Approach
  then trims that to 4 by itself, no Survival change needed.
- `expanse:deer` is listed (optional) in `#mettle:survival/big_game`; there is a deer loot table but no deer entity yet.

## 5. Compat (Hearth, later)

- **Settlement discovery** (BALANCE §3: 60 XP on first entry). Call
  `SurvivalSkill.award(player, "place", 60)` from the settlement-entry hook; there is nothing to remember on the Survival
  side if Hearth already tracks first entries per player.
- **Poultice** (teaching): a city's healer, or *The Monks' Herbal* (Cloud monastery, the hidden study). Grant with
  `Perks.grant(player, Perks.key(SurvivalSkill.POULTICE))`.

## 6. Burden: Pack Horse

Pack Horse counts metres walked on foot while `MettleServices.burden().band(player)` is `OVERBURDENED` or `PINNED`
(`GeneralFeats.packHorse`, 1 s samples, teleports ignored). With the stand-in service the band is always `LIGHT`, so the
tally can't move until burden merges; the logic is covered in `SurvivalRulesTest`.

## 7. UI / art: perk icons

`textures/gui/perk/<id>.png` (16 × 16) for gatherer, field_cook, quiet_approach, skinner, stalker, sixth_sense,
iron_stomach, campfire_rest, pathfinder, self_sufficient, tracker, lay_of_the_land, living_off_the_land, weatherwise,
big_game, trailblazer, poultice, and the general feats nine_lives, seen_it_all, pack_horse, seasoned,
jack_of_all_trades. The general feats live in `data/mettle/mettle/perk/general/` with `"skill": "mettle:survival"`;
the character screen's feats strip (TREES §12) should list `general/*` apart from the Survival tree.

## 8. Mining (and anyone using `PlacedBlocks` with drop perks): AFTER fires before the drops

Fabric's `PlayerBlockBreakEvents.AFTER` is injected at `Block#destroy`, i.e. right after the block is removed and
**before** `Block#playerDestroy` rolls its loot. `MiningSkill` calls `PlacedBlocks.takePlaced` in AFTER and then checks
`PlacedBlocks.isPlaced` in its `MODIFY_DROPS` handler, so by then the mark is gone: a re-placed ore still gets
Prospector, Gem Cutter, Strike the Seam and Diamond Hands (its XP is correctly withheld). Survival hit the same thing and
keeps the positions it just forgot in a set cleared at the end of the tick (`Foraging.JUST_BROKEN`); Mining could do the
same, or take the mark in `PlayerBlockBreakEvents.BEFORE`-recorded state.

## 9. Coordinator notes

- **Trailblazer** (Survival 10 + Athletics 10) is built here (`perk/survival/trailblazer.json`, discovery hook in
  `skill.survival.Discovery`), since Survival is its first skill and owns discovery. Athletics needn't build it.
- **Drops don't double-stack.** Skinner and Big Game dress an animal's drops once (on its first loot roll, like
  Huntsman), so Field Dressing's second roll is plain. With all four on a hoglin: Big Game doubles what the hoglin
  dropped, Skinner adds one meat and one hide, Huntsman (arrow kills) one more each, Field Dressing rolls the table
  once more.
- **`gradlew build` doesn't compile `src/gametest`.** A journey that doesn't compile only shows up in
  `runClientGameTest`. Worth adding `compileGametestJava` to `check`, or a line in BUILDING.md.
- **Sixth Sense** hooks `Mob#setTarget`, which every goal-driven hostile uses (zombies, skeletons, spiders, creepers,
  witches, pillagers, endermen…). Brain-driven mobs (piglins, hoglins, zoglins, wardens) set their target through brain
  memory and don't trigger it yet; a second hook on `BehaviorUtils`/`StartAttacking` would cover them.
- `MettleTour` preloads mixin targets to fail fast; Survival's are `AvoidEntityGoal`, `Mob`, `LivingEntity`,
  `FoodProperties`, `ApplyStatusEffectsConsumeEffect`, `SweetBerryBushBlock`, `Player`, `FurnaceResultSlot`,
  `CampfireBlockEntity`, `BlockItem`, and Mettle's own `Progress` and `Perks` (the journey exercises all of them).
