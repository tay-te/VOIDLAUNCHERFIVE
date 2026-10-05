# Requests from the Defense tree (character module, TREES.md §7)

## burden: two weight perks to read (Armour Training, Marching Order)

Both ship as data only (`data/mettle/mettle/perk/defense/armour_training.json`, `marching_order.json`); nothing
reads them yet because the weight system is not built.

- **Armour Training** (root, 3 ranks): armour *worn in the four armour slots* weighs 10 / 20 / 30 % less for that player.
  `Perks.rank(player, DefenseSkill.ARMOUR_TRAINING)` gives the rank (0–3).
- **Marching Order** (synergy, Defense 10 + Athletics 10): worn armour counts only half towards load.
  `Perks.has(player, DefenseSkill.MARCHING_ORDER)`.
- They stack multiplicatively: rank 3 plus Marching Order = 0.7 × 0.5 = 35 % of the armour's weight.
- Proposed: when `BurdenService.registerModifier(WeightModifier)` exists, Defense registers one from
  `DefenseSkill.init()` that scales only stacks in `EquipmentSlot.Type.HUMANOID_ARMOR` slots. If burden would rather own
  it, the numbers above are all it needs. The load should be recomputed on `PERK_LEARNED` / `RESPEC`.

## ui: 18 perk icons

`textures/gui/perk/<id>.png` (16×16 pixel art) for: thick_skin, armour_training, shield_up, shield_bash,
perfect_block, bulwark, mender, second_wind, hold_the_line, unbowed, guardian, last_stand, juggernaut, pincushion,
still_standing, roll_with_the_blow, marching_order, battle_worn.

## compat (Hearth): Guardian's "Hearth folk"

Guardian protects any `Player` or non-`Enemy` `Mob` within 4 blocks of the shield, so Hearth folk are covered already
as long as they are `Mob`s that do not implement `Enemy`. If Hearth folk are ever made hostile-capable (guards that
implement `Enemy`) they would drop out; then a small predicate hook (`Shieldbearer.isAlly(LivingEntity)`) would let
compat add them back. Nothing needed today.

## coordinator: a shared daze

Shield Bash and Juggernaut "daze" a mob for 1 s (`character.skill.defense.Daze`: Slowness VII plus its melee swings
cancelled through ALLOW_DAMAGE). Heavy Weapons' tree may want the same thing; if so, consider moving `Daze` to
`character.skill` (next to `Combat`) so every tree dazes the same way. It has no Defense-specific state.

## coordinator: cooldown status for the overlay

Last Stand (10 min), Second Wind (5 min) and Still Standing (1 min) cooldowns are kept in a persistent player
attachment `mettle:character/defense_cooldowns` (`Cooldowns.remaining(player, Cooldowns.LAST_STAND)` gives ticks
left). Not synced: if the overlay wants a live "Last Stand ready" badge like Rally's, it needs a sync or a payload.
