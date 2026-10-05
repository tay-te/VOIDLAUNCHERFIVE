# VOID Mettle — burden: weight, capacity, and the backpack, belt and quiver

§1–§4 are owned by the **burden** module, §5–§8 by the **gear** module; screens by **ui** (UI.md). Numbers are drafts;
the tunable ones live in [BALANCE.md](BALANCE.md).

Everything weighs, worn armour included. The vanilla 36 slots stay. What you can carry is set by weight, and you
raise it with Strength, perks, gear, potions and animals.

---

## 1. Item weight

Weights are in kilograms, shown with one decimal ("0.3 kg", stack "16.0 kg"). Nobody hand-writes a weight for 1,500
items. The table is **derived**, so modded items (Expanse, Hearth, anything) get sensible weights for free.

**Resolution order for an item:**

1. **Override**: `data/<ns>/mettle/weight/*.json` lists items or item tags → kg. Wins over everything.
2. **Leaf**: a short table of raw materials (stone, logs, ingots, gems, leather, string, food staples, coins…;
   BALANCE.md §3).
3. **Recipe**: weight = the lightest way to make it = Σ ingredient weights ÷ output count, minus crafting remainders
   (the bucket you get back). This covers shaped and shapeless crafting, stonecutting, and smithing transforms.
   Smelting is ignored (it changes mass); smelted outputs are leaves.
   - Computed to a fixed point on the server whenever datapacks load. Cycles (ingot ↔ nugget ↔ block) are anchored
     by leaves; iteration is bounded.
   - For tag ingredients, the lightest option counts.
4. **Fallback**, if nothing above applies: by kind. Spawn eggs, debug and technical items weigh 0. Anything else:
   4 kg ÷ max stack size (a 64-stack item is 0.06 kg; an unstackable one is 4 kg).

**Contents count.** A stack's weight includes what is inside it, recursively:
- bundles (`bundle_contents`)
- shulker boxes and other block items with `container`
- worn and carried gear pockets (§5)
- Hearth coin pouches (`hearth:pouch_contents`: coin counts × coin weight)

Loot-table containers that haven't been rolled weigh as empty.

**Per-player weight.** `BurdenService.weight(player, stack)` applies `WeightModifier`s: perks (Rock Hauler, Armour
Training, Light Hand), Merchant's Coin Sense, compat.

**Explainable.** `/mettle weight [item]` prints how a weight was reached, for example:

```
iron_block = 9 × iron_ingot (leaf 0.5) = 4.5 kg
```

Server owners can see what an override would change.

**Sync.** Recipes are server data in 26.3, so the server computes the table and sends it to each client on join and
after `/reload` (`mettle:burden/weights`, about 1,500 floats). Tooltips and the gear panel use it client-side.

## 2. Load and capacity

- **Load** is the weight of everything the player carries:
  - the 36 main slots, 4 armour slots and offhand
  - the three worn gear items with their pockets
  - the stack on the cursor while a menu is open
- **Not counted:** the ender chest (it's elsewhere), creative and spectator players (exempt; their load reads "—").
- **Capacity** = the `mettle:carry_capacity` attribute. Base 40, plus modifiers from:
  - Strength (+3 per point)
  - Pack Mule, Beast of Burden and Ox perks
  - worn gear (§5)
  - the Ox Draught potion
  - anything else that adds modifiers
- **When load is recomputed:**
  - on change: a HEAD mixin on `InventoryChangeTrigger.trigger`, which vanilla fires from the menu's per-tick diff and so catches edits inside bundles too
  - `ServerEntityEvents.EQUIPMENT_CHANGE`
  - pocket changes
  - a 40-tick fallback
  
  Vanilla's `Inventory.setChanged` counter is not a reliable signal in 26.3, so it isn't used.
- The result is synced to the owner as `mettle:burden/load`: load, capacity, band, and the heaviest stack's item and kg. It is only set when a value moves by ≥ 0.1 kg or the band changes.

## 3. Load bands

| Band | Load ÷ capacity | Effects |
|---|---|---|
| **Light** | under 75 % | none |
| **Burdened** | 75–100 % | speed −10 % · hunger drain ×1.5 · fall damage +25 % · footsteps louder (Stealth) |
| **Overburdened** | 100–125 % | speed −30 % · **no sprinting** · jump −40 % · you sink slowly when swimming · elytra won't open |
| **Pinned** | over 125 % | speed −70 % · no jumping · as Overburdened otherwise |

How each effect is applied:

- **Speed, jump and fall damage:** server-side attribute modifiers (`mettle:burden/band/*`).
  - Speed: `movement_speed`, `ADD_MULTIPLIED_TOTAL` −0.10 / −0.30 / −0.70.
  - Jump: `jump_strength`.
  - Fall damage: `fall_damage_multiplier`.
  - These update only when the band changes.
- **Hunger:** a mixin on `Player#causeFoodExhaustion`.
- **Sprinting:** vanilla gates sprint on the client (`hasEnoughFoodToDoExhaustiveManoeuvres`), so the client mixin reads the synced band. The server also forces `setSprinting(false)` for Overburdened and Pinned players.
- **Swimming:** a small downward pull while in water, as a behaviour. `water_movement_efficiency` can't go below 0, so it can't express this.
- **Mounts:**
  - A Burdened rider doesn't slow the mount.
  - Overburdened and Pinned riders slow it by 20 % and 40 % (Riding's Saddlebags perk changes this).
- **Picking up is always allowed** (KCD2). You never lose an item to weight; you drop what you choose.
- **Crossing into Overburdened** plays a soft strain sound once, and the HUD chip appears (UI.md §6).
- Perks can move thresholds (Strong Back) or scale penalties (Ox) through `mettle:band` effects.

## 4. Reading it

- **Tooltip line** under the name, on every item:

  ```
  0.25 kg · stack 16.0 kg
  ```

  For containers it shows the contents total: `Satchel · 6.2 kg with contents`.
- **Gear panel** (UI.md §5):
  - a weight meter with band marks at 75, 100 and 125 %
  - when Burdened or worse, one plain line naming the heaviest stack, e.g. "Heaviest: cobblestone ×3 stacks · 48.0 kg"
- **HUD chip** only when Burdened or worse.
- Nothing else. No numbers on the hotbar.

---

## 5. Gear: backpack, belt, quiver

Three new equipment slots **on the character**, in the empty column between the player figure and the crafting grid,
directly above the offhand slot (inventory GUI coordinates x = 77, y = 8 / 26 / 44). Each shows a faint outline icon
when empty.

| Slot | Items | What they do |
|---|---|---|
| **Back** | Satchel · Leather Pack · Frame Pack | +8 / +15 / +25 kg capacity, and 9 / 18 / 27 pocket slots |
| **Belt** | Belt · Tool Belt | +3 / +5 kg, and 4 / 6 quick pockets for small things (potions, food, torches, tools ≤ 1 kg) |
| **Quiver** | Quiver · Hunter's Quiver | 2 / 4 pockets that hold only arrows; bows and crossbows draw from it first |

- **Gear has its own weight** (Satchel 0.8 kg … Frame Pack 3.0 kg).
- **Pockets live inside the item.** Contents are stored in the item's own component (`mettle:pockets`, the same shape as vanilla's container contents). Take a backpack off and its contents go with it, like a shulker box. A worn backpack is one item, so death drops one item.
- **Any item can be gear.** Gear is defined by a component, `mettle:gear` (`slot`, `capacity_kg`, `pockets`, `accepts` tag). Hearth or a future mod can add a backpack with data alone.
- **Dyeable**, like leather armour.
- **No gear inside gear.** Backpacks can't go in backpacks. Shulker boxes and bundles can, at full weight; weight keeps that honest.
- **Recipes** (exact shapes in BALANCE.md §5):
  - Satchel, Belt and Quiver are crafted from leather and string.
  - The Leather Pack and Tool Belt upgrade them.
  - The Frame Pack and Hunter's Quiver need iron and more leather, and Hearth tanners and tailors sell them.
  - No recipe is gated by a skill; gating confuses players.

## 6. Slots in the inventory menu

The menu must have **the same slots on client and server, always**, so the layout is fixed and unused slots are
switched off (`Slot#isActive` = false), never added or removed at runtime.

| Menu index | Slot | Backing |
|---|---|---|
| 0–45 | vanilla (result, craft grid, armour, main, hotbar, offhand) | vanilla |
| 46 / 47 / 48 | Back / Belt / Quiver | the player's `mettle:gear/worn` container |
| 49–75 | 27 backpack pockets | view over the worn back item's `mettle:pockets` |
| 76–81 | 6 belt pockets | view over the worn belt's pockets |
| 82–85 | 4 quiver pockets | view over the worn quiver's pockets |

Pocket slots beyond the worn item's count are inactive. They are drawn in the gear panel, not inside the vanilla frame.

**Patches the gear module owns:**

- **Construction.** A mixin on `InventoryMenu.<init>` (TAIL) adds the slots.
- **Shift-click.** Patch `quickMoveStack`:
  - gear goes to its slot
  - arrows go to the quiver
  - small items reach belt pockets only by drag, never automatically
  - with a pack worn, shift-click on a main or hotbar stack sends it to the pack first, then falls back to vanilla behaviour
  - from pockets, items go back to the main inventory
- **Pickups overflow into the backpack** when the 36 slots are full (`Inventory.add` path).
- **Creative.** `CreativeModeInventoryScreen` lays slots out by index 0–45, so extra indices would land on the hotbar row. Patch it to hide pocket slots and place the three gear slots. The server's creative slot check only accepts 1–45, so extend it to the new range.
- **Click-outside.** `AbstractContainerScreen.hasClickedOutside` must treat the gear panel area as inside. Otherwise a click there drops the held stack.
- **Recipe book.** Opening the recipe book moves `leftPos`, so the panel and slot positions follow it (accessor plus per-tick sync).
- **Death.** Worn gear drops with the inventory unless `keepInventory` is on, in which case it is copied in `COPY_FROM`. Curse of Vanishing applies to gear like any item.
- **Containers (second step, still phase 3).** Looting straight into the backpack matters for ease of use. The gear panel's pockets are added to the common container menus (chest, barrel, shulker box, ender chest, crafting table) by appending slots after the vanilla range. Each one gets its own `quickMoveStack` fix: vanilla often targets `slots.size()`, which would otherwise fill pockets before the hotbar. Other menus show the panel read-only.

## 7. Using gear

- **Quiver.** A mixin on `Player#getProjectile` checks, in order: the held projectile (vanilla), then the quiver's pockets, then the inventory scan (vanilla). The quiver's pocket count shows on the HUD when you hold a bow or crossbow.
- **Belt radial.** Hold **R** and a radial of the belt pockets opens around the crosshair. Release over one to use it; tap R to reuse the last pocket.
  - **To use** means the item is swapped into your main hand, and food, potions and similar items start being used at once.
  - When the use finishes, or you switch away, the item you were holding comes back.
  - The server does the swap (`mettle:gear/belt_use`) and validates the pocket.
  - It works in combat, and nothing pauses: this is multiplayer.
- **Backpack.** Pockets are reachable in the inventory (and container screens, §6). There is no "open backpack from the hotbar" item use; the panel is the backpack.

## 8. Seeing it

- **On the character.** Worn gear renders on the player through Fabric's `LivingEntityRenderLayerRegistrationCallback`: a pack on the back sized by tier, a belt at the waist, a quiver over the shoulder. Each has a pixel-art texture and is drawn over armour.
- **Others see it.** `mettle:gear/worn` syncs to all trackers.
- **Hiding it.** A client option, "Show my gear on my character", syncs a hide flag for those who want a clean look. It is cosmetic only.
