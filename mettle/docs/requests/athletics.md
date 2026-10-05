# Requests from the Athletics tree

The Athletics tree works without any of these. The Porter perks (Strong Back, Steady Pace, Beast of Burden, Hauler's
Stride, Ox) are `BandModifier`s and band-aware movement code that take effect once the burden module is merged; until
then `MettleServices.burden()` says LIGHT and they sit idle. The tour checks them against a stand-in burden service.

## 1. Burden: let Hauler's Stride through the server-side sprint stop (burden)

**What.** BURDEN.md §3 has the server force `setSprinting(false)` for Overburdened and Pinned players. Hauler's Stride
lets an Overburdened player sprint (at half the sprint bonus). The client side already chains: Athletics hooks
`Player#hasEnoughFoodToDoExhaustiveManoeuvres` with `@ModifyReturnValue` at mixin priority 1500, so it applies after
burden's hook (default priority) and only turns burden's "no" into a "yes" for an Overburdened player with Hauler's
Stride (never for Pinned). But a server that stops the sprint anyway would undo it.

**Proposed.** Make the server stop ask the same gate instead of the band directly. Vanilla's server never calls that
method, so nothing else changes:

```java
// burden, server tick: stop the sprint only when the gate (burden's hook + Athletics' Hauler's Stride/Marathon) says no
if (player.isSprinting() && band.ordinal() >= LoadBand.OVERBURDENED.ordinal()
	&& !((BurdenPlayerInvoker) player).mettle$hasEnoughFoodToDoExhaustiveManoeuvres()) {
	player.setSprinting(false);
}
```

Keep burden's own gate hook at the default priority (below 1500), and as a `@ModifyReturnValue`.

## 2. Burden: what `penaltyScale` scales (burden)

**What.** Athletics relies on `BandRules.penaltyScale` scaling **all** of a band's Mettle penalties: speed, jump, the
extra hunger (×1.5 becomes ×(1 + 0.5 × scale)) and the extra fall damage. Ox halves it. Steady Pace sets it to 0 while
the load is inside the Burdened band and puts its own −5 % speed (`mettle:athletics/steady_pace`, halved by Ox) in its
place, which gives TREES.md's "slows you by only 5 % and costs no extra hunger". The Burdened band's louder footsteps
(Stealth) can ignore the scale.

**Proposed.** No code from Athletics; just implement the scale that way, e.g.
`speed = -0.10f * rules.penaltyScale()`, `hunger = 1f + 0.5f * rules.penaltyScale()`.

Steady Pace's modifier reads `load(player) / capacity(player)` inside its `BandModifier`; it guards against re-entry, so
it's safe even if `load` asks for the rules, but `load` and `capacity` shouldn't need them.

## 3. A shared "first visit" event (Survival / character)

**What.** Trailblazer (Survival 10 + Athletics 10) fires on a biome or place the character has never been to. Survival
will track first visits for its XP; until then Athletics keeps its own list (`mettle:athletics/trail`: biome ids,
structure ids, Mountain Goat's peaks; persistent, unsynced, bounded by the registries) and checks every 40 ticks.

**Proposed.** When Survival's discovery lands, publish it, e.g. `MettleEvents.DISCOVERED.invoker().discovered(player,
kind, id)` (kind: biome / structure / settlement / landmark), and Trailblazer listens instead of tracking. Athletics would
keep only the peaks.

## 4. Pack Horse can reuse Athletics' distance hook (character, general feats)

**What.** The general feat Pack Horse (TREES.md §12: walk 1 km while Overburdened or Pinned) needs distance per band.
`athletics.AthleticsXp.stat` already sees every distance stat with the band. Whoever builds the general feats can add a
`FeatCounters.add(player, "pack_horse_cm", count)` there for WALK/SPRINT/CROUCH_ONE_CM when the band is OVERBURDENED or
PINNED (or ask and Athletics will add it).
