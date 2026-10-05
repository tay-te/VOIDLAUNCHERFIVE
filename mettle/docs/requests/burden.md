# Requests from the burden module (BURDEN.md §1–§4)

## coordinator / ui: load in the overlay's view model (`MettleState`)

The HUD chip and the gear panel's meter belong to the Ultralight overlay, so burden built no native HUD beyond a one-line
dev fallback (`client.burden.BurdenHud`, hidden whenever `OverlayBridge.hasListener()`). The overlay needs the synced
load in `MettleState.build` (client/overlay, not burden's file). Everything is in `LoadState.of(mc.player)` (the synced
`mettle:burden/load` attachment; null until the first sync):

```java
LoadState load = LoadState.of(mc.player);
JsonObject burden = new JsonObject();
if (load != null) {
	burden.addProperty("load", load.load());                 // kg
	burden.addProperty("capacity", load.capacity());         // kg (= mettle:carry_capacity)
	burden.addProperty("band", load.band().name().toLowerCase(java.util.Locale.ROOT)); // light|burdened|overburdened|pinned
	burden.addProperty("exempt", load.exempt());             // creative/spectator: show "—"
	BandRules rules = MettleServices.burden().rules(mc.player);
	burden.addProperty("burdenedAt", rules.burdened());      // meter marks (75 / 100 / 125 % by default)
	burden.addProperty("overburdenedAt", rules.overburdened());
	burden.addProperty("pinnedAt", rules.canBePinned() ? rules.pinned() : -1f);
	if (load.hasHeaviest()) {
		JsonObject h = new JsonObject();
		h.addProperty("item", load.heaviest().toString());
		h.addProperty("kg", load.heaviestKg());
		h.addProperty("stacks", load.heaviestStacks());
		h.addProperty("text", LoadTracker.heaviest(load).getString()); // "Cobblestone ×3 stacks · 48.0 kg"
		burden.add("heaviest", h);
	}
}
root.add("burden", burden);
```

Band changes also arrive as `mettleFeedback` chips with perk ids `mettle:burden/{light,burdened,overburdened,pinned}`
(text already says what happened and names the heaviest stack), so the overlay can show the chip without polling.

## coordinator: BALANCE.md §5.2 rows burden added

The fallback (4 kg ÷ stack size) made some uncraftable natural blocks absurd (podzol at a quarter of dirt, a music disc at
4 kg), so `burden.Leaves.TABLE` has a second group the balance sheet should list:

| Group | Items → kg |
|---|---|
| Soil | podzol, mycelium, rooted dirt, dirt path, farmland, soul sand, soul soil, suspicious sand 0.2 · suspicious gravel 0.25 · nylium 0.15 · moss blocks 0.1 · sculk 0.2 |
| Stone | bedrock, reinforced deepslate, gilded blackstone 0.3 · budding amethyst 0.25 · pointed dripstone 0.06 |
| Plants | mangrove roots 0.4 · cactus 0.2 · sugar cane 0.05 · kelp 0.02 · mushrooms 0.02 · mushroom blocks, shroomlight 0.1 · corals 0.05 · coral blocks 0.25 |
| Finds | heavy core 3.0 · bell 2.0 · dragon egg 3.0 · end portal frame 1.0 · spawner 5.0 · goat horn 0.5 · heart of the sea 0.3 · nautilus shell 0.1 · music discs 0.2 · sherds 0.1 · banner patterns 0.01 · froglights 0.15 · amethyst buds and cluster 0.05 · cobweb 0.01 · rabbit 0.2 |
| Overrides of recipes | map 0.1 (the recipe's compass made it 2.1 kg) · golden apple 0.3 (8 gold ingots made it 4.95 kg) · sponge 0.1, wet sponge = sponge + 1.0 |

Rules worth a line in §5.2 too:
- **Equivalences** (no recipe states them): weathered and waxed copper = fresh copper; `stripped_X` = X; `infested_X` = X;
  `<colour>_concrete` = its powder; explorer and filled maps = map; chipped/damaged anvil = anvil; enchanted book = book;
  written book = book and quill; carved pumpkin = pumpkin.
- **Cooking fills gaps**: an item no crafting recipe reaches weighs what it was cooked from (smooth stone = stone, brick =
  clay ball, cooked rabbit = rabbit); listed leaves (glass, ingots, cooked meats) still win.
- **Guesses rank last**: a recipe built from real weights always beats one built on a fallback weight, however light.
- Fish/axolotl buckets = water bucket + the animal (0.3 / 0.5 / tadpole 0.05); powder snow bucket = bucket + 1.0.

111 of 1,658 vanilla items still use the fallback (mob drops like gunpowder and blaze rods at 0.06 kg, horse armour at
4 kg, smithing templates); all look sane.

## coordinator: MettleTour mixin class-load list

Add `net.minecraft.advancements.triggers.InventoryChangeTrigger` and `net.minecraft.world.food.FoodData` to the targets
`MettleTour` loads up front (burden mixes into both; `Player` and `LivingEntity` are loaded anyway).

## coordinator (api): `MettleEvents.LOAD_BAND_CHANGED`

ARCHITECTURE.md §4 lists it but the api has no events class yet. When it lands, burden fires it from
`LoadTracker.changed(player, tracker, from, now)` (server thread, once per real band change). Perk changes need no event:
the tracker notices a new character record (and a capacity change) within the tick.

## gear: what burden reads

- Worn gear: `MettleServices.gear().worn(player, slot)` weighs as its own item (its own `mettle:pockets` component
  skipped) plus every stack from `MettleServices.gear().pockets(player, slot)`, so nothing counts twice.
- Carried gear: any component value implementing `ContainerComponent` (vanilla's `ItemContainerContents` is ideal for
  `mettle:pockets`) is weighed recursively, read by component id; no compile-time dependency either way.
- Capacity from worn gear: add modifiers to `mettle:carry_capacity`; burden notices a capacity change within the tick.
- Pocket edits that do not touch the player's `Inventory` are caught by the 40-tick fallback (up to 2 s late). For
  instant updates, proposed api addition: `BurdenService#invalidate(Player player)` (default no-op), which burden
  implements with `LoadTracker.markDirty`; gear calls it after a pocket change.

## stealth / riding (later trees)

- Stealth: "footsteps louder while Burdened" (BURDEN.md §3) is not built; Stealth should read
  `MettleServices.burden().band(player)`.
- Riding's Saddlebags: mount slowdown is `Bands.MOUNT` (0 / 0 / −20 / −40 %) × `BandRules.penaltyScale`. If Saddlebags
  should change only the mount penalty, `BandRules` needs a `mountScale` field (api change), or Saddlebags registers a
  `BandModifier` and accepts that it also eases the rider.

## done for Athletics (docs/requests/athletics.md)

- The sprint gate hook (`Player#hasEnoughFoodToDoExhaustiveManoeuvres`, `@ModifyReturnValue`, default priority) lives
  in the common mixin `mixin.burden.PlayerMixin`, so it applies on dedicated servers too. The server stops a sprint only
  when the band is Overburdened or worse **and** that same gate says no (`burden.Sprint.blockedByLoad`, through the
  `mixin.burden.PlayerSprintGate` invoker), so Hauler's Stride (priority 1500) is not undone.
- `BandRules.penaltyScale` scales every band penalty: speed, jump, extra fall damage, the extra hunger (×1.5 becomes
  ×(1 + 0.5 × scale)), the mount slowdown and the swimming pull.
