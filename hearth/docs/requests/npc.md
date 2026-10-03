# npc module: requests to the coordinator

Things the npc module needs from outside its package, what it does meanwhile, and proposed signatures.

## 1. Credit embodied labour (sim)

**Done** (integration): `EmbodimentManager.creditLabour` drains `extra("npc").labour` into `Settlement.creditLabour` on fold and on death (adults, not builders).

**Why.** While a resident is embodied the sim leaves them out of abstract labour and production (§4). Builders report
their work through `advanceConstruction`, but other trades (farmers, foresters, smiths, bakers...) have no way to
tell the sim "I worked 3 sim hours", so a town where the player stands produces less than one nobody is near.

**Meanwhile.** Every embodied resident accumulates the ticks they spent in `WORK` in `Resident.extra("npc").labour`
(`NpcData.addLabour/drainLabour`). Farmers also add 1 FOOD to the stockpile per real harvest.

**Proposed.** Either the sim reads and drains `extra("npc").labour` when a resident returns to abstract (it is
`int` ticks), or an api method the npc module calls on fold:

```java
/** An embodied resident worked this long at their trade; the sim credits it at its own rates. */
void creditLabour(UUID resident, JobType job, int ticks);   // on Settlement
```

## 2. Know when a plot is waiting for goods (sim → `PlotView`)

**Done** (integration): builders work only `BUILDING && !waitingForGoods()` plots (`BuilderWork.buildable`); the two-minute stall heuristic is gone. The sim funds stages for embodied builders and no longer force-starts a stage that is short of goods.

**Why.** Builders work on `BUILDING` plots. If a stage cannot start for want of goods, a builder would still place the
next stage's steps unless the sim keeps such plots out of `BUILDING`.

**Meanwhile.** The npc module listens to `CONSTRUCTION_STALLED` and leaves that plot alone for two minutes (and
until `BUILDING_COMPLETED`). It also waits (does not place) when `step >= stepCount(stage)`.

**Proposed.** Either guarantee "a `BUILDING` plot's current stage has had its goods taken", or add
`boolean PlotView.waitingForGoods()`.

## 3. Current sim day in `ResidentFactory.create`

**Kept** (integration): `NpcTime.today()` is the sim world's `day()`; the sim calendar is the overworld's, so it is right.

**Why.** `Resident.bornDay` needs "today". The factory has no `HearthWorld`.

**Meanwhile.** `NpcTime.today()` (the sim world's `day()` for the overworld when the sim runs, else the overworld clock
/ 24000). Fine as long as the sim's calendar is the overworld's.

**Proposed (optional).** `long today` parameter, or `Settlement.world()`.

## 4. Gametest entrypoint

**Done** (lead, 71fee57); `-Dhearth.tour=folk` runs it alone.

Register `dev.voidmc.hearth.gametest.npc.FolkPortraits` under `fabric-client-gametest` in
`src/gametest/resources/fabric.mod.json` (I ran it with a local, reverted edit of that file).

## 5. Lang keys

**Kept** (integration): nothing defines `hearth.job.*`; the npc keys stay.

`npc.json` defines its own `hearth.npc.job.*`, `hearth.npc.trait.*` and `hearth.npc.child` rather than
`hearth.job.*` (from `JobType.translationKey()`), so there is no clash with whoever adds `hearth.job.*` to `core.json`.
If `core.json` defines `hearth.job.*`, the npc module can switch to them.

## 6. Per-player quest markers (optional, later)

**Done** (integration): markers are computed per player within 32 blocks and only changes are sent (`TalkPayloads.Markers`, client `FolkMarkers`); the entity-data marker is no longer used.

Markers are synced through entity data, so every player sees the marker computed for the *nearest* player. For a
crowded server town a per-player marker packet would be more correct; the npc module can add one if wanted.

## 7. Folk spawn egg texture

**Open**: not drawn yet (an art task).

`assets/hearth/textures/item/folk_spawn_egg.png` (an item texture for the npc module's own item).
