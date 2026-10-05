# Requests from the plan module (phase 3)

The planner works around every one of these inside `dev.voidmc.hearth.plan`; none blocks it. Each says what, why, and
the signature proposed.

## For the api (coordinator)

1. **The underground through the terrain probe.** The planner only ever sees a `TerrainProbe` (it must stay a pure
   function of the probe, histories run on atlas workers), but deep towns need the `UndergroundProbe` of the same world.
   Proposed: `default @Nullable UndergroundProbe TerrainProbe.underground() { return null; }`, which the sim's
   generator probe overrides. *Workaround:* `PlanModule.underground(terrain)` answers the probe itself when it
   implements `UndergroundProbe`, else the one paired with it — the plan module pairs `HearthWorld.terrain()` with
   `HearthWorld.underground()` on `ServerLevelEvents.LOAD` in a phase after the default one (after the sim made the
   level's world), and tests and previews pair theirs with `PlanModule.registerUnderground`.
   **Done** (integration 3): `TerrainProbe.underground()`. The sim's generator probe answers it, and `PlanModule.underground` asks it first.
2. **One carrying capacity.** LIVING_WORLD §7.2 has the sim compute what a territory carries; the planner stops planning
   spare beds there. Proposed: `default int Settlement.carryingCapacity() { return 0; }` (folk; 0 = unbounded).
   *Workaround:* `resource/Territory.estimateCapacity`: the food the FERTILE, PASTURE and FISH sites give back a day
   (`regenPerDay` × the food goods' share of `yields`) over 2 a head, times `trade_margin` (1.25), plus 6.
   **Done** (integration 3): `Settlement.carryingCapacity()` is the sim's; `Territory.capacity` uses it and falls back to its own estimate.
3. **The site's material.** §8.5 asks for `rock` "from the survey" and §7.2 for birch woods → birch. Proposed:
   `ResourceSite.material()` (`@Nullable Identifier`: the wood's log or the host rock's block). *Workaround:*
   `resource/Materials` reads the biome (and climate) at the site's centre: Expanse woods and stones by biome id
   (redwood, wisteria, palm, limestone in the karst, opal sandstone in the dunes, tuff on the volcanoes...), vanilla ones
   by biome name, else by temperature; deepslate where the ground lies below y 0.
   **Done** (integration 3): `ResourceSite.material`, used for `rock` on stone, clay and sand sites. `local` still reads the biome (its fallback chains cover a missing Expanse).
4. **Realm lakes.** Proposed: `int UndergroundProbe.waterY(int x, int z)` (`TerrainProbe.NO_WATER` if dry), so deep
   towns can build quays on realm meres. *Workaround:* the deep town keeps to floor the probe reports (`floorY` returns
   `Integer.MIN_VALUE` under a lake in the previews' Expanse reader).
   **Done** in the api (integration 3): `UndergroundProbe.waterY(x, z, floorY)` has a default. Open: Expanse's implementation does not answer it yet.

## For the sim module

5. **Households into homes of their station.** Every home the planner plans names its `station` and usually its
   `household` (`job`, and `head` — a resident id — when a family is waiting for it). When housing the homeless, and on
   the day's business, please move: the named head's family into its home first; then families into free homes whose
   `station` param is their station (`Resident.station`); then anyone into any free bed; and a family living in another
   station's home into a free home of its own station when one stands empty. Without this the town is built by station
   but lived in by chance. (`plan/testing/MiniSim.house()` does exactly this for the previews.)
   **Done** (integration 3): `SettlementClock.assignHomes` houses by the planner's homes. The named head's family moves in first, then families into homes of their station, then anyone; up to two households a day move into an empty home of their own station.
6. **Plots below ground.** Plots of a deep town (`deep/*` archetypes, and `infra/*` with a `realm` param) stand on a
   realm's floor (`origin.y` = the floor) under the surface town's columns: they overlap surface plots in x and z by
   design. The worldgen guard must not keep surface features off their columns, and the "player built over this plot"
   check must use their own y range. `deep/lift` stands on both levels (from `origin.y` = the realm floor up to `top_y`).
   **Done** (integration 3): deep plots (`deep/*`, plots with `realm`) are not marked as surface in the worldgen guard (`PlotColumns.underground`). The built-over check is block by block, so it was already right.
7. **Workings and their sites.** Extraction plots (`extract/*`) and the site-bound workplaces (`work/logging_camp`,
   `work/salt_works`) carry `site` (the `ResourceSite` id) and `kind`; please set `ResourceSite.plot` from it and credit
   each removed natural block as yield (§8.5). The planner reads a BUILT working as dug out (its site free for the next
   working there, unless woods or salt), so stages should be the benches and galleries.
   **Done** (sim, checked).
8. **Ore where the realms are.** On Expanse Earth worlds please let the survey put ORE sites over the cavern model's
   tunnels and realms (§7.1: "mines can aim for them"). A deep town is founded when a mine or shaft of the town has been
   worked over a realm (or a shaft's `realm_below` names one).
   **Done** (sim, checked): the survey puts ore over caverns and realms.

## For the build module

9. **New params on plots** (all documented in PLANNING.md §8 and §14):
   - homes: `station` (a `Station` name), `household` (`job`; `head` and `name` when a family is named); the lord's seat
     carries `station: lord`;
   - every plot: `local` — role → material, in the style syntax (a block id, `wood:<ns>:<species>`, `a|b` fallback
     chains). Roles: `foundation`, `plinth`, `paving`, `frame`, `beam`, `floor`, and `wall` for the cultures whose walls
     are of the wood (northern, tundra, bayou) or the stone (moorland; desert and mesa in their sandstone); deep plots get
     the realm's rock and `light` (`expanse:glowcap|minecraft:shroomlight`, amber, prismite, sea lantern...);
   - workings: `site`, `kind`, `rock` (a plain block id), `face` (the way the working face advances: into the hill for a
     mine, from the street side for a pit), `depth` (quarry pits, shafts, clay and sand pits), `length` (mines: the adit
     runs that far from the portal, within the footprint's depth), `speciality` (the site's rarity, when it has one),
     `realm_below` (a shaft sunk into a realm);
   - deep plots: `realm` (the realm id); `infra/road` with `realm` treats the realm floor as its ground and never cuts
     up toward the surface; `deep/lift`: `top_y` (street level at its head), `bottom_y` (= `origin.y`, the realm floor),
     `street_y`, `deep_street_y`, `deep_facing` (its lower door, onto the deep square), `character` (the realm's);
   - `law/toll_gate` stands over its road like a gatehouse (`over_road`, `passage_width`, `street_y`);
     `law/pillory` and `law/gallows` stand in the square like its other furniture (`on_square`);
   - the castle or palace replaces the keep (`replaces`) over the keep and its close (the `castle` reservation);
   - clay and sand pits may take in up to a third of their footprint of water at the bank.
   Open (build architects): the new params are theirs to read.
10. **District values** `poor` and `castle` join the `district` param's list.
   Open (build architects).
11. **Sizes.** `plan/catalogue/DefaultCatalogue.phase3Infos` lists the sizes, jobs, beds, tiers, flags and cultures the
    planner expects for every §8.5 id; the infos the build module registers win. Jobs the planner relies on: the town
    hall provides REEVE, the keep LORD/STEWARD/GUARD, the courthouse JUDGE, the jail JAILER/GUARD, the logging camp
    FORESTER/HAULER, mines and shafts MINER, quarry pits QUARRIER, clay and sand pits CLAY_DIGGER.
   **Done** (integration 3): the build module's infos win at runtime. The default catalogue's 71 phase 1–2 rows now match the registered infos; the §8.5 rows wait for the architects.

## For the npc module

12. **Stations.** The planner reads `Resident.station`. Until the npc module keeps it following work and purse, a
    station still at POOR on a resident who holds a job is read as that job's station, POOR counts as PEASANT below
    village tier (no poor quarter in a hamlet), and GENTRY or LORD below town tier as MERCHANT. Children and elders are
    spread over the stations as the adults are.
   **Done** (integration 3): stations follow trade (npc) and purse (economy).
