# Requests from the sim module (phase 3: towns thrive on the resources around them)

What the sim built is in `docs/SIM.md` §16. These are the api changes and the agreements with other modules it needs;
each is worked around inside `sim` until the lead merges it.

## 1. `ResourceSite`: a `material` of its own, and the ore estimate

**Why**: §7.2 "local materials" — the planner resolves style palettes against the territory's sites (a birch town builds
in birch, a limestone town in limestone). The planner needs each site's material: the wood's log, the quarry face's rock,
the bank's clay or mud, the beach's sand. `ResourceSite.speciality` is documented as "an item id for a land's rarity".

**Workaround**: the sim puts the material in `speciality` for every site (woods: the dominant log of the biome's tree
features, e.g. `minecraft:birch_log`, `expanse:redwood_log`; stone: the surface rock, e.g. `expanse:limestone`,
`minecraft:sandstone`, `minecraft:basalt`; ore: the main raw ore, `minecraft:raw_iron`; clay/sand: `minecraft:clay`,
`minecraft:mud`, `expanse:opal_sand`; rarities: the item). Fertile land, pasture, fish and salt have none.

**Proposed** (replaces `speciality`):

```java
public record ResourceSite(int id, Kind kind, BlockPos center, BoundingBox area, int capacity, int remaining, float regenPerDay,
    float[] yields, @Nullable Identifier material, int plot, float oreGrade, float coalGrade, JobType worker) {
  // material: the block or item it gives (log, rock, clay, sand, the rarity); oreGrade/coalGrade: ore and coal per block of
  // rock cut there (the survey's estimate; a mine following the seams finds `seam_factor` times more); worker: the trade
  // whose yields describe it
}
```

## 2. `Settlement`: prosperity, carrying capacity, houses left empty

**Why**: the planner should build finer in a boom and leave a declining town alone; quests and dialogue want "the mine
ran out, folk are leaving"; the build module's decay look needs to know which `ABANDONED` plots were left empty in a
decline (as opposed to built over).

**Workaround**: `SimSettlement.land()` (`LandState`) has all of it; the sim itself raises/lowers `PlotSpec.tier` of new
buildings by one in a boom/decline (config `resources.prosperity.finish_tier`), and fires `PROSPERITY_CHANGED` with a why.

**Proposed**:

```java
/** 0 (ruin) to 1 (boom): a slow average of the town's sites, food, work, trade, coin and housing. */
default double prosperity() { return 0.5; }
/** -1 declining, 0 steady, +1 rising (as the last PROSPERITY_CHANGED said), and why. */
default int fortunes() { return 0; }
default String fortunesWhy() { return ""; }
/** People the territory's food sites and the town's trade can feed. */
default int carryingCapacity() { return Integer.MAX_VALUE; }
/** Whether an ABANDONED plot was left standing empty in a decline (folk may move back in; it decays meanwhile). */
default boolean deserted(int plotId) { return false; }
```

## 3. Caravans: an id in the event, and a view for the npc and quest modules

**Why**: §7.6 — the npc module meets caravans on the road near players, the quest module makes escorts and robberies.
`SettlementEvents.CaravanEvent` has no caravan id, and nothing outside `sim` can see or touch a caravan.

**Workaround**: `dev.voidmc.hearth.sim.trade.Caravans` (`SimWorld.caravans()`): `active()`, `caravan(id)`,
`positionAt(caravan, gameTick)` (straight between the towns at its pace), `escort(id)` (it will arrive whatever the road
held), `rob(id)` (cargo gone, fires `ROBBED`, returns the cargo).

**Proposed**:

```java
// SettlementEvents.CaravanEvent
void onCaravan(HearthWorld world, Settlement from, Settlement to, Kind kind, long caravanId, int[] cargo);
// HearthWorld
default List<CaravanView> caravans() { return List.of(); }
default boolean escortCaravan(long id) { return false; }
default int @Nullable [] robCaravan(long id) { return null; }
record CaravanView(long id, long from, long to, int[] cargo, long value, long departTick, long arriveTick, BlockPos fromPos, BlockPos toPos) {
  BlockPos positionAt(long tick); // lerp, as Caravans.positionAt
}
```

## 4. Economy: where the purse lives

**Agreement asked**: the economy module keeps a resident's purse at `Resident.extra("economy").purse` (a `long`, bits).
`sim/settlement/Stations` reads it (constants `Stations.PURSE_MODULE` / `PURSE_KEY`) to move a household's
`Resident.station` up and down slowly; without it, means are derived from the household's trades and the town's
prosperity. If the economy module stores it elsewhere, tell me the key (or add `EconomyService.purse(Resident)`).

The sim's own coin (per worker per day) still stands aside when an economy module is registered
(`economyHandlesWages`). The sim now also trades: export of overflow, imports for stalled builds, tools and food from
passing traders, caravans paying between treasuries — all through `Settlement.deposit/withdraw`.

## 5. Planner: excavations, local materials, empty houses, finish tier

- **Excavations**: open `hearth:extract/*` plots for sites from `Settlement.sites()` with the §8.5 params (`site` = the
  site id, `kind`, `rock` = the site's material, `depth`/`length`, `face`). The sim links the plot to the site, credits its
  cuts as yield (stone by rock, ore and coal by the site's grade × seam factor) and debits the site; its labour comes
  from its own trade (quarrier, miner, clay digger, forester), never builders, and it employs them from the day it is
  staked out (its JOB_SITE POIs count while it is PLANNED/BUILDING). When one is built, open the next bench/gallery.
  The stand-ins in `sim/dev/PlaceholderExcavations` show the shape (and are in the dev catalogue).
- **Local materials**: the `local` param can come from the sites' materials (request 1).
- **Empty houses**: a plot `ABANDONED` because its folk left (`LandState.isDeserted`, request 2) still stands; please do
  not plan over it — folk move back in before anything new is built.
- **Finish tier**: the sim nudges `PlotSpec.tier` of a new building by one for prosperity (switchable). If the planner
  would rather do this itself (request 2), set `resources.prosperity.finish_tier: false` by default and I will drop it.

## 6. NPC: miners and quarriers, foresters, caravans

- Embodied quarriers/miners/clay diggers work `hearth:extract/*` plots under the same contract as builders: place exactly
  step `(stage, step)` of the plot's blueprint (cuts are air steps) and call `advanceConstruction(plotId, 1)`. Leave plots
  that are `waitingForGoods()` (a gallery waiting for timber for its props). Stage yields are credited by the sim as each
  stage finishes, the same either way.
- An embodied forester who fells natural trees reports the work with `creditLabour` as before; the sim does not then owe
  the world those trees again (`Woodcutter` fells only what abstract foresters took).
- Caravans near players: request 3.

## 7. Build: excavation blueprints, the bill, decay

- Excavation blueprints (`hearth:extract/*`): steps that cut are air; the sim counts air steps below the terrain probe's
  ground as cut rock (a plantation's air steps at any height as felled logs). Many stages (a bench or a gallery each),
  JOB_SITE POIs for the trade and WORK_FACE POIs. Props, rails and lanterns go in the stage's goods bill as usual.
- The material bill in the new goods (timber WOOD, DRESSED_STONE, BRICK, GLASS, METAL, CANDLES...): the sim accepts
  whatever a blueprint reports, buys what it cannot make from passing traders (dear), and carries it by caravan.
- The decay look for houses left empty: request 2.
- `#hearth:buildable_over` now includes every natural ore and cave rock so excavations can cut them.

## 8. Standing: reputation goes through the service

`Settlement.reputation(player)` returns `StandingService.standing(id, player)` when a standing service is registered;
`Settlement.addReputation(player, delta)` becomes `StandingService.deed(new Deed(player, id, "hearth:reputation", delta,
null, center, true))`. Calls made from inside the standing service's own `standing`/`deed` read and write the sim's raw
numbers (so a standing module built on top of them cannot recurse). `SimSettlement.rawReputation/rawAddReputation` give
the raw numbers for migration of old saves. Proposed: none needed in the api; please map the `hearth:reputation` deed
kind to a plain standing change.

## 9. Repair: excavations are never damage

The cuts of `hearth:extract/*` plots are their blueprint, not damage, and with `resources.extraction.abstract_only` the
sim writes nothing of them at all (an excavation is then numbers only) — the repair module should not "restore" them
either way (the applicable cursor is 0 then; ask the sim, or skip `extract/*`).

## 10. `UndergroundProbe.Realm.character`

The sim's implementation names realms by Expanse's realm biome names: `wilds`, `lush`, `dripstone`, `crystal`, `ember`,
`mere`, `frozen`, `roots`, `fossil`, `tidal`, `amber`, `brimstone` (biome `expanse:realm_<character>`). Proposed: say so
in the api's Javadoc (it gives `glowcap_wilds` and `amber_hollows` as examples). Realm ids are `cellX << 32 | cellZ` of
the cavern model's 640-block realm cells (stable per world).
