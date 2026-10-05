# Requests from the quest module (phase 3)

What the living world's quests need from the api or other modules, and how the quest module works around each until
then. Everything here works without the request being met; meeting it makes the outcome real in another module.

## 1. Law: lift a wanted status (clearing your name)

**Done** (integration 3): `LawService.pardon`. Finishing a clear-your-name quest pardons the player in that town.

**What.** A way for the quest module to tell the law that a wanted player has worked off their crime in a settlement.

**Why.** LIVING_WORLD.md §4 and the phase 3 brief ask for "clearing your name". The quest side is done: templates with
`"wanted": "only"` (`law/clear_name_patrol`, `law/clear_name_labour`) are offered only to players the law wants in the
town, by the guard, jailer or judge; completing one reports the deed `hearth:quest/name_cleared` and fires
`dev.voidmc.hearth.quest.QuestEvents.NAME_CLEARED (player, settlement, quest)`. Nothing lifts the wanted status yet.

**Proposed signature** (api, `LawService`):

```java
/** Clears the player's wanted status (and bounty) in a settlement, as by a pardon or work done in place of the fine. */
void pardon(UUID player, long settlementId, String reason);
```

Until then the law module can subscribe to `QuestEvents.NAME_CLEARED` (a plain Fabric event in the quest package).

## 2. Law: mark goods as stolen

**Done** (integration 3): `LawService.markStolen`. What a robbed caravan's animals drop is marked stolen from the home town (quest trains and npc parties alike).

**What.** `LawService.markStolen(ItemStack stack, Owner owner)` (or an api helper that writes the `hearth:stolen`
component).

**Why.** A caravan's pack animals spawned by caravan and fence quests carry chests of the town's goods; killing them is
reported as robbery (`LawService.report`), but the loot that drops is not marked stolen, so a merchant of the robbed town
will still buy it. The law module owns the component, so the quest module cannot write it.

## 3. Sim: add a prospected site to a settlement's territory

**Done** (integration 3): `Settlement.reportSite`. A prospect handed in runs a small survey round the spot, adds the site and fires `SITE_FOUND`, which resolves the happening; the planner then works it.

**What.** A way to tell the sim that a player found a resource (a rock face, an ore vein, a clay bank) for a settlement
whose site of that kind was spent, so the survey adds it and the planner opens a quarry, mine or pit there.

**Why.** LIVING_WORLD.md §7.2 (boom and bust) and the brief: "a site depleted (`SITE_DEPLETED`: scout a new quarry or ore
vein — and the sim founds/plans it)". The quest side is done: `SITE_DEPLETED` becomes a `depleted` happening answered by
`disaster/new_site` (and the reeve's `official/reeve/new_ground`); the `prospect` objective completes where the blocks round
the player show the resource; handing it in fires
`QuestEvents.SITE_PROSPECTED (level, settlement, kind, pos, player)`. When the sim then fires `SITE_FOUND` for that kind,
the quest module resolves the happening.

**Proposed signature** (api, `HearthWorld` or `Settlement`):

```java
/** A site of this kind found at {@code pos} by a player; the sim surveys round it and adds it if the land bears it out. */
@Nullable ResourceSite reportSite(Settlement settlement, ResourceSite.Kind kind, BlockPos pos, String reason);
```

## 4. Repair: fire and explosion damage arrive as `DAMAGED`

**Done** (repair, checked): `DAMAGED` is fired once a second per plot and cause, now with the player. `FIRE_STARTED`, `FIRE_OUT`, `REPAIRED` and `REPAIR_STALLED` are in the api too.

**Checked against the api, needs the repair module.** Fires (`Cause.FIRE`) in embodied towns call the bucket brigade once
a plot has lost `fire_min_blocks` blocks in a day; explosions in `extract/mine`, `extract/shaft` and `deep/lift` plots are
mine collapses; other explosions are blasts to mend. Please fire `DAMAGED` promptly (batched per plot is fine) so the
brigade is called while the fire still burns. Rebuilding quests read `RepairService.damaged(settlement)`.

## 5. Sim: caravan events

**Done** (integration 3): the caravan's id rides in the happening. Taking the escort calls `escortCaravan` (it arrives), and a failed escort loses it. The npc module puts no second party on the road for an escorted caravan.

**Checked against the api, needs the sim.** `CARAVAN` `DEPARTED` from an embodied town posts an escort (the quest spawns
its own train of pack animals with cargo chests, so it works without the npc module's embodied caravans); `ARRIVED`
resolves it; `ROBBED` and `LOST` post hunts for the robbers, the stolen cargo and the lost caravan in both towns (at the
road's midpoint). If the npc module also embodies the same caravan near the player, the integration should pick one
(config `living.urgent_broadcast` is unrelated; an option `spawn_caravans` can be added if both run).

## 6. Npc: a caravan master who rides along (optional)

Open: the caravan master riding along was not wired (escort status is in the api now if wanted).

`NpcService.follow` now exists; the escort could ask the giver (a trader) to follow the player to the destination as the
caravan master. Not wired yet, to keep escorts working with or without the npc module and to avoid two systems moving
the same resident; worth doing at integration if the npc module's follow reports arrival and loss.

## 7. Standing: the deed kinds the quest module reports

**Done** (integration 3): the standing table has `hearth:default` (quest-like), `hearth:quest/failed` and `hearth:quest/abandoned`.

For the standing module's mapping to effects and gossip words: `hearth:quest/<type>` (the default for a template without
`deed`), `hearth:quest/failed`, `hearth:quest/abandoned`, and the templates' own: `supplied_build`, `contract_filled`,
`caravan_escorted`, `bandits_routed`, `cargo_recovered`, `caravan_found`, `fence_job`, `thief_taken`,
`outlaw_brought_in`, `name_cleared`, `bounty_collected`, `fire_fought`, `miners_rescued`, `rescued` (one per miner, with
the miner as the resident), `repairs_supplied`, `wolves_driven_off`, `site_prospected`, `town_aided`, `alms_given`,
`official_reeve|guard|priest|builder|lord`. Weights are the template's reward standing (positive) or the config's
`reputation_fail` / `reputation_abandon` (negative). All are reported `witnessed = true` with the giver as the resident.

## 8. Standing / economy lang keys the quest module reads

Noted.

`hearth.title.*` (standing module) for titles on the board and in locks — the quest module falls back to the title's
name where a key is missing; `hearth.goods.*` (economy) is not read by quests (goods are named by their concrete items).
