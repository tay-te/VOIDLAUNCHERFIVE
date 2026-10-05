# Requests from the Blades tree

The Blades tree works without any of these: each has a workaround inside `character/skill/blades` and `mixin.blades`.
They would make it tidier or nicer to play.

## 1. Show a charged swing on the client (MettleClient, cosmetic)

**What.** Cut and Run and Sword and Board fully charge the next swing. The server is authoritative and the damage is
right, but the client's own attack indicator still shows the swing recharging, because nothing tells it.

**Proposed.** In `MettleClient`'s `Feedback` receiver, after pushing the chip:

```java
if (payload.perk().equals(BladesSkill.CUT_AND_RUN) || payload.perk().equals(BladesSkill.SWORD_AND_BOARD)) {
	LocalPlayer player = context.client().player;
	if (player != null) {
		((BladesLivingAccess) player).mettle$setAttackStrengthTicker((int) Math.ceil(player.getCurrentItemAttackStrengthDelay()) + 1);
	}
}
```

(`BladesLivingAccess` is the accessor in `mixin.blades`; it already exists and works on both sides.)

## 2. Conditional attribute effects in perk data (EffectsApplier)

**What.** Fencer, Measure and Wide Arc are attribute changes that apply only while a blade (or sword) is held. Perk
`effects` apply always, so the three are code (`BladeGear`) with their own modifier ids `mettle:blades/*`, re-checked on
hand change and every 10 ticks.

**Proposed.** An optional `"when": "holding_blade" | "holding_sword"` on `Perk.AttributeEffect`, evaluated by
`EffectsApplier` and re-applied on `EQUIPMENT_CHANGE` for the main hand. Then the three become data and `BladeGear.refresh`
goes away. Note for whoever does it: `EffectsApplier.perkEffects` only sweeps refunded modifiers on *syncable*
attributes; `sweeping_damage_ratio` and `attack_damage` are not synced, so a refunded data effect on them would linger.

## 3. A bleed damage type (data + Combat)

**What.** Bleed deals `minecraft:generic` damage credited to the player (through armour, no knockback). It works, but the
death message reads "died because of Steve", and `Combat` gives no Blades kill XP for a mob that bleeds out (its
`weapon()` needs the player as the direct entity).

**Proposed.** `data/mettle/damage_type/bleed.json` (`message_id: "mettle.bleed"`, exhaustion 0, scaling `never`), tags
`minecraft:bypasses_armor`, `minecraft:no_knockback`, `minecraft:bypasses_cooldown` (then Bleeding needn't juggle the
hurt cooldown), lang `death.attack.mettle.bleed.player` = "%1$s bled out, cut by %2$s", and in `Combat.skillFor`:
`if (source.is(MettleDamageTypes.BLEED)) return BLADES;`.

## 4. Master Strike's sources (compat)

**What.** Master Strike is a Teaching; nothing grants it yet except `/mettle perk grant mettle:blades/master_strike`.
TREES.md: a city's guard (Hearth trainer) or *The Verses of the Long Edge* (Wisteria manor, the wine cellar, Expanse).
