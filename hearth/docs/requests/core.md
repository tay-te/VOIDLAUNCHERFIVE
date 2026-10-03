# Requests from the sim module (core)

## 1. `HearthWorld.found` — founding settlements from other modules

**Done** (integration): in the api since 71fee57; the quest module founds scouted sites with it, crowded towns found daughter hamlets through the sim (`sim/world/Hamlets`).

**Why**: §5.1 says a scouting quest picks a site "and it is founded there", and settlers from a town that outgrew its
land found a hamlet. The sim has `SimWorld.found(BlockPos, Culture, name)` and `SimWorld.whyNotFound(BlockPos, spacing)`,
but other modules can only reach `HearthWorld`.

```java
/** Founds a live settlement at {@code at} (null culture: from the land). Null if it cannot be founded there. */
@Nullable Settlement found(BlockPos at, @Nullable Culture culture);
/** Why a settlement cannot be founded here (water, too near another), or null if it can. */
@Nullable String whyNotFound(BlockPos at);
```

## 2. `HearthWorld.knownSites` — the atlas for rumours and exploration

**Done** (integration): npc rumours read it (`npc/talk/Rumours.others`), so unvisited historic towns are named too.

**Why**: NPC rumours ("directions to other settlements") and quests need settlements the player has not reached yet;
those exist only in the atlas until adopted. The sim can answer cheaply from cached region plans.

```java
record SiteInfo(long settlementId, String name, Culture culture, BlockPos center, SettlementTier tier, boolean live) {}
/** Settlements live or historic within {@code radius} blocks (regions not yet planned are planned off-thread; may miss them). */
List<SiteInfo> knownSites(BlockPos near, int radius);
```

## 3. A `PLOT_ABANDONED` event (optional)

**Done** (integration): fired when a plot is built over, loses its archetype, or comes down for its rebuild (`SimSettlement.firePlotAbandoned`).

**Why**: when a player builds over a planned plot the sim abandons it (§5.3). Quests and NPC dialogue could react
("someone built on the Millers' plot"). Proposed: `SettlementEvents.PLOT_ABANDONED (world, settlement, plot)`.

## 4. Gametest entrypoint for the arrival banner (optional)

**Done** (integration): the arrival banner is photographed by `gametest/IntegrationTour` (registered); no separate ArrivalTour.

**Why**: the banner and the F3 entry are untested in a client. A client game test in the sim package
(`dev.voidmc.hearth.client.sim.gametest.ArrivalTour`, gametest source set) would need an entry in
`src/gametest/resources/fabric.mod.json` under `fabric-client-gametest`. I have not written it; say if you want it.

## 5. Lang keys shared by all modules

**Done** (integration): checked; no module redefines `hearth.culture.*` or `hearth.tier.*`, and the merged lang builds clean.

`core.json` defines `hearth.culture.<culture>` and `hearth.tier.<tier>` (display names, capitalised) since the sim is
the core; other modules should use them rather than define their own (a duplicate key with different text fails the
build). The banner uses its own `hearth.sim.banner.*` keys.

## 6. Notes for the build module (no api change)

**Noted** (integration): passed on to the build module in the integration report; nothing to change here.

- The terrain probe's ground is within one block of the generator's real ground for ~92 % of columns on Expanse Earth
  terrain (the model omits the generator's 3D noise and density interpolation). Footings should go two blocks below
  the probe's ground so no building floats by a block at an edge.
- `PlotFacts` reads `Blueprint.goods(stage)` and `work(stage)` once per plot; a stage whose goods exceed the stockpile's
  capacity (2000 + storage buildings) is clamped. Keep per-stage bills within a few thousand units.
