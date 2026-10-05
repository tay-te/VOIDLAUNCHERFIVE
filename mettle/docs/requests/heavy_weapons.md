# Requests from the Heavy Weapons tree

## 1. MettleClient: one line (needed before release)

```java
dev.voidmc.mettle.client.heavy.HeavyWeaponsClient.init();
```

in `MettleClient.onInitializeClient`. It registers the renderer for `mettle:thrown_axe` (Throwing Axe). Without it a
client that has a thrown axe in view crashes (vanilla's `EntityRenderDispatcher` has no renderer for the type and
NPEs). `init()` is idempotent. Until it is wired, `HeavyWeaponsJourney` registers the renderer itself (it checks
`HeavyWeaponsClient.initialised()` and rebuilds the entity renderers), so the client tour passes either way.

## 2. Burden: Log Hauler (when the weight system lands)

`mettle:heavy_weapons/log_hauler` ships as data only. Wanted: a `WeightModifier` that makes logs, wood, stems,
hyphae, stripped variants (`ItemTags.LOGS`) and planks (`ItemTags.PLANKS`) weigh 40 % less for a player who holds the
perk. Check: `Perks.has(player, HeavyWeaponsSkill.LOG_HAULER)`.

## 3. UI / art: perk icons

`textures/gui/perk/<id>.png` (16 × 16 pixel art) for heft, feller, cleave, log_hauler, rhythm_of_the_axe,
throwing_axe, armour_crusher, bone_breaker, iron_grip, stagger, timber, berserker, earthshaker, headhunter, old_growth,
stonebreaker, overhead_blow.

## 4. Compat (Hearth): Overhead Blow's teachers

The teaching `mettle:heavy_weapons/overhead_blow` is granted by a city's forester, or by reading *The Feller's Rule*
(Logging camp, the payroll strongroom). Grant with `Perks.grant(player, Perks.key(HeavyWeaponsSkill.OVERHEAD_BLOW))`.

## 5. Core: conditional modifiers (FYI, maybe a helper)

`EffectsApplier` removes every `mettle:perk/...` modifier it did not add itself (only `..._armor` survives), so a
perk whose attribute modifier depends on a condition cannot use that namespace. Iron Grip and Berserker therefore use
`mettle:heavy_weapons/<perk>/<attribute>` and manage themselves in `skill.heavy.Stance` (rechecked on main-hand change,
on damage, and every 5 ticks). Other trees with "while holding" modifiers will hit the same thing; a documented id
prefix for conditional modifiers (or a small toggle helper in core) would keep them consistent.

## 6. Balance question: should chopping teach Heavy Weapons?

BALANCE.md §3 gives Heavy Weapons XP only for combat, so Feller, Rhythm, Timber and Old Growth never level the skill
that owns them. A small award per natural log chopped with an axe (say 0.25, placed logs excluded through
`PlacedBlocks`, which this tree already maintains for logs) would let a woodsman grow by woodcutting. Not built: it is a
balance call.

## 7. Client tour: mixin targets to preload (optional)

`MettleTour` loads every class Mettle mixes into, to fail fast on a broken injection. Heavy Weapons adds:
`net.minecraft.world.entity.player.Player`, `net.minecraft.world.entity.LivingEntity`,
`net.minecraft.world.level.block.Block`, `net.minecraft.world.item.ItemStack`,
`net.minecraft.world.entity.monster.Creeper` (BlockItem is already listed). The journey exercises all of them anyway.

## 8. Mining: `PlayerMixin.getDestroySpeed` blocks every later RETURN hook

`mixin.character.PlayerMixin.mettle$miningSpeed` is a cancellable `@Inject` at RETURN that always calls
`setReturnValue`. That returns from `getDestroySpeed` on the spot, so any other tree's RETURN inject or
`@ModifyReturnValue` on the same method never runs (found the hard way: Feller had no effect). Heavy Weapons now scales
the speed with a `@ModifyVariable` just before the `onGround()` check instead, which works whatever Mining does. Other
trees with dig-speed perks will trip on this; suggest Mining moves to `@ModifyReturnValue` (MixinExtras chains those).

## 9. Please run the client tour once more

The journey was capped at three tour runs. The third run passed Heft, Feller, Cleave and Rhythm of the Axe, then
stopped on a mistake in the test helper (it called `Minecraft.getInstance()` from the gametest thread), which is fixed
but not re-run. Everything after it (Throwing Axe, Timber, the Crusher build, Berserker, both feats, Stonebreaker,
Overhead Blow) is asserted in `HeavyWeaponsJourney` and waits for the next `./gradlew runClientGameTest`. The throw
also saves `mettle_heavy_throw_flight.png`, `mettle_heavy_throw_landed.png` and `mettle_heavy_throw_landed_side.png` to
`build/run/clientGameTest/screenshots`, to check how the thrown axe is drawn.
