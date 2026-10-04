# npc module, phase 3: requests to the coordinator and the other modules

What the phase 3 npc work needs from outside its package, what it does meanwhile, and proposed signatures. The phase 2
file (`npc.md`) is unchanged.

## 1. Escort outcomes in the api (api)

**Why.** `NpcService.follow` starts an escort but gives the caller no way to learn that it arrived or was lost, which
the quest module's escort quests and the law module's walk to the cell both need.

**Meanwhile.** `dev.voidmc.hearth.npc.service.Escorts.status(resident)` (ACTIVE, ARRIVED, LOST; kept ten minutes after
the end) and the Fabric event `Escorts.FINISHED (resident, player, status)`. Other modules should not import them; the
integrator can bridge them.

**Proposed** (either):

```java
/** On NpcService: how an escort started with follow() stands, or null if there is none (outcomes kept ten minutes). */
@Nullable EscortStatus escort(UUID resident);
enum EscortStatus { ACTIVE, ARRIVED, LOST }
```

or a `SettlementEvents.ESCORT_ENDED (world, settlement, resident, player, arrived)` event.

## 2. Excavations: who advances them and what is credited (sim)

**What the npc module does.** Plots whose archetype is `EXTRACTION` (or an `extract/` id) are never touched by builders.
Miners, quarriers, clay diggers, salters (and foresters, for plantations) work them under the builders' contract and call
`advanceConstruction(plot, 1)` per step — cuts (air) and the props, ladders and lanterns. **Time spent cutting is not
reported through `creditLabour`** (as with builders), so the sim must:

- credit each removed natural block of an excavation step as yield (stone by `rock`, ore by the survey's estimate), as
  LIVING_WORLD.md §8.5 says, when it arrives through `advanceConstruction`;
- not advance excavations with builders' abstract labour, but with the gathering trades' (`MINER`, `QUARRIER`,
  `CLAY_DIGGER`, `SALTER`, `FORESTER` for plantations), at the same work-unit pace the npc module uses
  (`gathering.cut_step_ticks`, default 30 = the builders' pace);
- keep `waitingForGoods()` meaningful for excavation stages (props, rails, lanterns cost goods): gatherers leave a
  waiting plot alone.

Other gatherers' embodied time (foresters felling outside a plantation, herders, beekeepers, fishers, haulers,
diggers without a pit) is reported with `creditLabour` as before.

## 3. Exact yields (api + sim, optional)

**Why.** A forester near a player fells a real tree of, say, 46 logs; the sim credits wood from their working time. The
two agree on average but not tree by tree.

**Proposed.** `void creditYield(UUID resident, Goods goods, int units)` on `Settlement`: real units gathered while
embodied, which the sim adds to the stockpile (and depletes the site) instead of the labour-derived yield for that time.
The npc module would report felled logs (`LOGS`), and could report shorn wool, milk (`PRODUCE`), honeycomb (`WAX`) and the
catch (`FISH`).

## 4. Caravans overcome near a player (api + sim)

**Why.** When a player kills a caravan's party (§15 of FOLK.md), the npc module drops the journey and reports the
robbery to the law, but the sim still thinks the cargo is on the road and will deliver it.

**Proposed.** `void caravanLost(long fromId, long toId, @Nullable UUID robbedBy)` on `HearthWorld` (the sim fires
`CARAVAN ROBBED` itself and loses the cargo). Meanwhile nothing reaches the sim.

## 5. Blueprints the gatherers and officials rely on (build, plan)

- **Excavations**: `WORK_FACE` POIs on every bench or gallery, each standing within reach (4.6 blocks from the eyes, eyes
  1.62 above the POI) of the cuts it serves; a way out of every bench (ladder or stairs, as steps); a `STORAGE` POI (the
  spoil heap or crate at the pit head) or at least `entrances().get(0)`; `kind` and `rock` params; one stage per bench or
  gallery. The dev sandbox's `SandboxBlueprints.quarry` is a small working example.
- **The lord and officials**: a `THRONE` POI in the hall of `noble/keep|castle|palace` (the lord holds court there, the
  reeve and the captain report before it) and in `law/courthouse` (the judge presides); `CELL` POIs in `law/jail`,
  `PILLORY` at `law/pillory`, `STRONGBOX` in the keep or counting house, `STAGE` (playhouse, tavern), `BATH`
  (bathhouse), `SICKBED` (infirmary), `HITCHING_POST` (stables, inn). Job sites (`JOB_SITE` with the job) for the new
  workshops, so each worker has a bench.
- **Homes**: the lord's and steward's `Resident.home` should be the keep, so meals and nights are there.

## 6. Goods names (economy)

The caravan traders' lines name their cargo with `Goods.translationKey()` (`hearth.goods.*`), including the phase 3
goods (`hearth.goods.logs`, `...salt` ...). The economy module owns those keys.

## 7. Law and standing (notes for those modules)

- The npc module sends no deed about hurt or killed residents when `HearthServices.law()` is set: witnesses and the
  courts are the law module's, and it reports deeds itself. Without a law module it sends `npc:hurt` (−3) and
  `npc:killed` (−100) to standing, or moves the raw reputation without standing.
- Caravan folk are not residents; caravan crimes come to `LawService.report` as `ASSAULT`, `MURDER` and `ROBBERY` with
  `victim` null and the caravan's home town as `settlementId`. Pack animals carry the entity tag `hearth.caravan`; the law
  module may treat them as the home town's property in `owner(Entity)`.
- `FolkHandle.canSee` is range + field of view + line of sight only; light, sneaking, armour and traits are the law
  module's to weigh.

## 8. Repairs (repair module)

Builders do not consult `RepairService` themselves: the repair module's builders' behaviour (a `FolkBehaviour` at
100–199) takes a builder over between steps, which stops the construction task cleanly (scaffolding comes down) and the
routine resumes after.

## 9. Carts (later)

Haulers carry loads in their arms; carts on roads need an entity (or a drawn vehicle model) that does not exist yet.
Caravans use vanilla pack animals.

## 10. The gametest source set does not compile at `b452cf1` (lead)

`gametest/plan/Palette.java` switches over `ArchetypeCategory` without the phase 3 values, so `compileGametestJava`
fails (the `build` task does not compile that source set, so the build stays green). To run the npc tour I added a
`default` branch locally and reverted it before committing; the plan module (or the lead) should add the cases.
