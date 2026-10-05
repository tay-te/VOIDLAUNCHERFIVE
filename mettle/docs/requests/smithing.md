# Requests from the Smithing tree (character module, TREES.md §10)

## coordinator: call `SmithingSkill.init()` from `CharacterModule.init()`

**What.** `SmithingSkill` had no `init()`; it now has one. It registers the two item components (`mettle:forged`,
`mettle:honed`, which must exist before the registries freeze) and Light Hand's `WeightModifier`. It is idempotent.

**Done on this branch** (one line and its import, so the tour could run): in `CharacterModule.init()`, after
`SurvivalSkill.init();`:

```java
SmithingSkill.init();
```

If you'd rather wire it yourself, keep the line; an identical change on `mettle` merges cleanly.

## burden: nothing to do for Light Hand (and Reforge Light)

Registered from `SmithingSkill.init()` with `MettleServices.addWeightModifier(Forging::weight)`. It reads the stamp on
the stack (`mettle:forged`), not the player, so a Light Hand chestplate is 25 % lighter for whoever carries it, and a
Light reforge halves it (both: 0.375). It applies to every stack of a stamped item wherever it is (worn or carried),
which is how TREES.md words it ("armour you craft weighs 25 % less"). The tour checks the product of all registered
modifiers; JUnit checks the numbers (`SmithingRulesTest`).

## ui: 16 perk icons

`textures/gui/perk/<id>.png` (16×16 pixel art) for: tinker, thrifty, light_hand, riveted, proofed, shieldwright, forged,
salvager, keen_edge, true_temper, reforge, masterwork, honing, hundred_hammers, black_iron, folded_steel.

## compat (Hearth, Expanse): Folded Steel's sources

Folded Steel is a Teaching; nothing grants it yet except `/mettle perk grant mettle:smithing/folded_steel`. TREES.md:
a city's smith (Hearth trainer) or *A Treatise on Folded Steel* (Expanse Karst temple, the scroll room).

## character (heads-up, already done here): Smithing XP only from smithing

`SmithingSkill.crafted` used to award XP for anything reported through `ItemStack#onCraftedBy`, which included a
villager's trades and ingots knocked out of a block (an endless 9 XP loop with an iron block). It now checks the open
menu: gear XP from a crafting grid or a smithing table upgrade, ingot XP only from a furnace's result slot, and a
reforge 10 XP. Anvil XP is unchanged.
