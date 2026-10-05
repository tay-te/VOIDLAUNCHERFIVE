# Requests from the Marksmanship tree

1. **MettleClient** (`client/MettleClient.java`): call `dev.voidmc.mettle.client.marksmanship.MarksmanshipClient.init();` in
   `onInitializeClient`. It registers the Hunter's Mark packet receiver. Until then `client.mixin.marksmanship.MinecraftMixin`
   calls it at the end of `Minecraft.<init>`; `init()` is idempotent, so both can stay until the coordinator removes the
   mixin's `<init>` inject.

2. **MettleTour** (`gametest/MettleTour.java`): pass the client context to the journeys, e.g.
   `MarksmanshipJourney.run(context, world)`. The journey needs it to hold the use key (Arrowstorm is only real with a
   held bow), wait client ticks, read the client (Hunter's Mark outline) and take a screenshot. Until then it reads the
   private `context` field of `TestSingleplayerContextImpl` by reflection (`MarksmanshipJourney.clientContext`).
   Also worth adding to the tour's class-preload list so a broken injection fails fast:
   `net.minecraft.world.item.BowItem`, `net.minecraft.world.item.CrossbowItem`, `net.minecraft.world.item.ProjectileWeaponItem`,
   `net.minecraft.world.entity.projectile.arrow.AbstractArrow`, `net.minecraft.world.inventory.CraftingMenu`,
   `net.minecraft.client.renderer.item.properties.numeric.UseDuration`,
   `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer`.

3. **gear** (quiver): Quiverwise says "arrows taken from your quiver are kept one time in four". With no quiver yet it
   applies to every arrow a bow or crossbow draws from the inventory (`character.skill.marksmanship.Ammo.keepArrow`,
   hooked in `ProjectileWeaponItem.useAmmo`). When the quiver lands, please expose whether a projectile stack lives in
   the worn quiver, e.g. `GearService#isInQuiver(Player, ItemStack)`, and Marksmanship will narrow `keepArrow` to it.

4. **ui / art**: perk icons `assets/mettle/textures/gui/perk/<id>.png` for the 18 Marksmanship perks (steady_aim,
   retrieval, long_shot, hunters_mark, steady_breath, pinning_shot, quick_nock, fletcher, double_nock, quiverwise,
   deadeye, volley, arrowstorm, sixty_paces, wingshot, huntsman, leaping_shot, steppe_draw). Not drawn here.

5. **compat** (Hearth / Expanse): Steppe Draw is a Teaching. Its sources (a city hunter trainer; *The Steppe Bow* in the
   Caravanserai cistern) should call `Perks.grant(player, Perks.key(MarksmanshipSkill.STEPPE_DRAW))`. Today it can only
   be given with `/mettle perk grant mettle:marksmanship/steppe_draw <player>`.
