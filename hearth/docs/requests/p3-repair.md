# repair module: requests to the coordinator

What the repair module (docs/REPAIRS.md) needs from outside its package, what it does meanwhile, and proposed
signatures. Numbered by how much they matter.

## 1. Builders' repair time should come out of construction (sim)

**Why.** LIVING_WORLD.md §2 puts repairs *ahead of* new construction. Embodied builders already do that (mending is a
behaviour at priority 150, so a builder who mends is not building). Abstract repair cannot: the sim's clock spends all
of a builder's hours on construction, and the repair module accrues its own repair labour on top
(`blocks_per_builder_hour`, 16 by default, against construction's 33). A town nobody is near therefore mends *and*
builds at full speed.

**Meanwhile.** Repair labour is extra, kept modest (half a builder's construction rate) and capped at a day banked.

**Proposed.** On `Settlement`:

```java
/**
 * Takes up to {@code units} of abstract builder labour (work units, as construction) for other work before the clock
 * spends it on construction; returns how much was granted. Only labour of builders who are records.
 */
default int takeBuilderLabour(int units) { return units; }
```

The repair module would then ask for its blocks each accrual and stop accruing its own.

## 2. One material bill for construction and repairs (build → api)

**Why.** A repaired block should cost what the build module's bill says it cost to build. The repair module keeps its own
copy (`RepairBill`, already on the phase 3 goods: planks are WOOD, logs LOGS, stone bricks DRESSED_STONE, bricks BRICK,
lanterns METAL + CANDLES, torches and candles CANDLES) which can drift from `build/blueprint/MaterialBill` (still on the
ten phase 2 goods).

**Proposed.** On `BlueprintService` (the build module implements it with `MaterialBill`):

```java
/** The goods one block of this state costs to place (empty for free blocks: air, plants, natural ground). */
Goods[] goodsOf(BlockState state);
```

## 3. Fire and repair events in the api (api ← repair; quest, social, standing listen)

**Why.** Quests to help after a fire (LIVING_WORLD.md §2), town news and gossip ("the inn burned last week"), and the
social module's mood all want to know when a fire breaks out, when it is out, and when a building is whole again.
`SettlementEvents.DAMAGED` (batched once a second per plot and cause) covers the blocks, not the episode.

**Meanwhile.** `dev.voidmc.hearth.repair.RepairEvents` has `FIRE_STARTED(world, settlement, at)`,
`FIRE_OUT(world, settlement, burned, helpers)` and `REPAIRED(world, settlement, plotId, blocks)`. Other modules cannot
see them without depending on the repair package.

**Proposed.** Move them into `SettlementEvents` as they are:

```java
public static final Event<FireStarted> FIRE_STARTED;   // void onFireStarted(HearthWorld, Settlement, BlockPos at)
public static final Event<FireOut> FIRE_OUT;           // void onFireOut(HearthWorld, Settlement, int burned, Set<UUID> helpers)
public static final Event<Repaired> REPAIRED;          // void onRepaired(HearthWorld, Settlement, int plotId, int blocks)
/** Repairs cannot go on for want of goods (once per stall, like CONSTRUCTION_STALLED), so quests can ask for them. */
public static final Event<RepairStalled> REPAIR_STALLED; // void onRepairStalled(HearthWorld, Settlement, Goods goods, int blocks)
```

The repair module already knows when a repair is deferred for goods (`RepairBill.missing`) and would fire the last one.

## 4. Fire wakes the town (npc)

**Why.** `FolkHandle.awake()` is false for sleeping folk, and the brigade (priority 250) only takes awake folk, so a fire
at night burns with nobody fighting it.

**Proposed.** Either the NPC module wakes folk for behaviours of priority 300 and up (the api's "emergencies") and the
brigade moves to 300, or a handle method:

```java
/** Wakes a sleeping folk (a fire, an alarm bell); they get up and the routine resumes from where the day is. */
void wake();   // on NpcService.FolkHandle
```

## 5. Whether the applier has written a plot's chunk (sim)

**Why.** Inspections compare a built plot with the world. If the sim's applier has not yet written a plot's recorded
stages into a chunk (it just loaded), every block looks missing, and mending them would build the house twice.

**Meanwhile.** Inspections skip chunks loaded less than a minute ago and throw away a round that finds a quarter or more
of a plot's blocks missing (`Inspector.commit`).

**Proposed.** On `HearthWorld`:

```java
/** Whether every recorded step of the plot lying in this loaded chunk is in the world (the chunk's ledger has caught up). */
boolean written(Settlement settlement, int plotId, int chunkX, int chunkZ);
```

## 6. Notes for the modules that consume repair output

- **law.** The repair module reports `Offence.VANDALISM` through `LawService.report`: witnessed sprees (one crime per
  player per settlement per 5 s, worth summed from `EconomyService.price`), and unwitnessed damage when builders find it
  (`witnessed = false`). The law module should not also report players' block breaks in settlements as vandalism, or a
  window would be fined twice. Victim: `LawService.owner(level, pos).resident()` when the law module knows it.
- **standing.** Deed kinds the repair module sends: `repair:fire_brigade` (weight 2 + helps/2, max 15; players who put
  out flames by hand or poured water on them, or put blocks back during the fire), `repair:rebuilt` (1 + blocks/8, max
  10, per settlement per two minutes, for players who put damaged blocks back), and `repair:vandalism` (only when no law
  module runs: −2 per block, at least −30, witnessed sprees only).
- **quest.** Listen to `SettlementEvents.DAMAGED` with `Cause.FIRE` (or `RepairEvents` until §3) for fire quests;
  `RepairService.damaged(settlement)` says how much is left to mend.
- **economy.** Deeded houses are mended only when their owner pays (`/hearth repair pay`, or "Mend my house" in a
  builder's conversation): goods at the market's buying price × 1.25 plus 2 bits a block, into the treasury.
