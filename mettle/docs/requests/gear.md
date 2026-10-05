# Requests from the gear module

1. **character** (`character/Backgrounds.giveKit`): wear kit gear instead of dropping it in the bag, so a new Ranger can
   shoot at once (the quiver's 32 arrows are inside the quiver, and a bow only draws from a *worn* quiver):

   ```java
   ItemStack stack = template.create();
   if (dev.voidmc.mettle.gear.GearModule.wear(player, stack)) {
   	continue;
   }
   ```

   (or `MettleServices.gear().wear(...)` once item 2 lands). Until then the Ranger right-clicks the quiver once; its
   tooltip says so.

2. **api** (`GearService`): two methods other modules need, both already implemented in `GearModule`:

   ```java
   /** Whether this exact stack lives in the worn quiver (a bow that drew it took it from the quiver). */
   boolean isInQuiver(Player player, ItemStack stack);   // = GearModule.fromQuiver
   /** Puts gear on if its slot is free; consumes the stack when worn. */
   boolean wear(Player player, ItemStack stack);          // = GearModule.wear
   ```

3. **marksmanship** (Quiverwise, your request 3): narrow `Ammo.keepArrow` to arrows from the quiver with
   `GearModule.fromQuiver(player, projectile)` (the stack `useAmmo` gets is the live quiver stack, so the identity
   check works; growing it back in place is fine, the gear module commits in-place changes at the end of the tick).

4. **character** (`lang/en_us/character.json`): the kit summaries now read wrong:
   - `mettle.background.ranger.kit`: "Bow, a quiver of 32 arrows, leather cap, cooked mutton"
   - `mettle.background.rogue.kit`: "Stone sword, dark leathers, a satchel with bread"
   - `mettle.background.smith.kit`: "Iron pickaxe, 6 iron ingots, a belt with bread"

5. **burden**:
   - Weight overrides for the gear items (BALANCE.md §5.4), else recipe derivation makes a Satchel 1.5 kg:
     `mettle:satchel` 0.8, `leather_pack` 1.5, `frame_pack` 3.0, `belt` 0.4, `tool_belt` 0.7, `quiver` 0.5,
     `hunters_quiver` 0.8. (Upgrades use the recipe type `mettle:gear_upgrade`, which is a shaped recipe; derivation
     may treat it like `crafting_shaped`.)
   - Pockets: component `mettle:pockets`, type `ItemContainerContents` (same codec as `minecraft:container`). Worn gear:
     `MettleServices.gear().worn(player, slot)`; the stack's `mettle:pockets` is always current.
   - Recompute: every gear change fires `CriteriaTriggers.INVENTORY_CHANGED.trigger(player, inventory, EMPTY)`, so the
     HEAD hook BURDEN.md §2 plans catches worn-gear and pocket changes with no extra coupling.
   - Belt pockets take items of at most 1 kg each, using `BurdenService.weight(stack) / count`. With the stand-in
     service (0 kg) everything fits a belt until burden lands.

6. **MettleTour**: add to the class-preload list so a broken gear injection fails fast:
   `net.minecraft.world.inventory.InventoryMenu`, `net.minecraft.world.entity.player.Inventory`,
   `net.minecraft.server.network.ServerGamePacketListenerImpl`,
   `net.minecraft.client.gui.screens.inventory.InventoryScreen`,
   `net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen`,
   `net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen`.

7. **ui** (gear panel, UI.md §5; belt radial §7; quiver chip §6):
   - The pockets are inventory-menu slots 49–75 (back), 76–81 (belt), 82–85 (quiver), inactive past the worn item's
     count. Move them where the panel draws its cells with `client.mixin.gear.GearSlotAccessor` (see
     `client.gear.GearPanel`, the dev fallback to replace), and make `GearPanel.contains` (click-outside) answer for the
     new panel rectangle.
   - Belt radial: `GearClient.useBelt(minecraft, pocket)` sends `mettle:gear/belt_use` and keeps the use key held for a
     consumable; R currently reuses the last pocket and "Belt pocket 1–4" are unbound keys.
   - Quiver chip: `GearInventory.of(player).pockets(GearSlot.QUIVER)` on the client.
   - "Show my gear on my character": `GearClient.hidden(player)` is the hook; it needs the option and a synced flag.
   - First-time hint "Wear it in the slots beside your character" (UI.md §9) on the first gear pickup.

8. **Not done here (phase 3, second step)**: pockets in container screens (chest, barrel, shulker box, ender chest,
   crafting table). Only the player inventory has them.
