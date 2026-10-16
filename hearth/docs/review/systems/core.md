# Systems review: the core (wave 7)

Lane `hearth-sys-core`, 2026-10-16. Scope: the abstract simulation and persistence, the server and multiplayer, worldgen
histories and adoption, the chunk applier, dimensions, commands and permissions, configs, dedicated-server safety,
partner mods, server-thread cost and crash safety. Code: `sim/**`, `world/**`, `mixin/**`, `api/**`, `client/{sim,map}`.

How it was checked: reading the flows end to end, JUnit (new `HearthConfigTest`, `sim/world/PersistenceTest`; the whole
`sim.*` and `api.*` suites pass), and a new client game test, **`PersistTour`** (`JAVA_TOOL_OPTIONS="-Dhearth.tour=persist"
./gradlew runClientGameTest`), run eight times while fixing what it found. It founds a live town near spawn and grows it 40
days, adopts the nearest historic town, holds the clock, then:

1. loads the live town's chunks, lets the applier write it, records every block;
2. lets the chunks unload, loads them again: no block may change, no block may be written again, ledgers unchanged;
3. runs the core's commands as an operator, and parses 18 op-only commands as a player without permissions;
4. saves (flushing) and reads the settlement files back with a fresh store: each must equal the live record;
5. quits, reopens the world: every settlement (people, plots and cursors, stores, coin, module data), the index's next
   id, and every block of the town must be as before.

Final run: **everything came back** (see "Verified in game").

## Feature matrix

Status: **ok** works and was checked · **fixed** was broken, fixed in this lane · **gap** missing or not verifiable here.

| feature | implemented | wired | reachable | tested | in game | status |
|---|---|---|---|---|---|---|
| Settlement record (residents, plots, stockpile, treasury, reputation, module data) | yes | yes | — | SettlementCodecTest | PersistTour | ok |
| One file per settlement, async atomic writes, `.bad` set-aside | yes | yes | — | PersistenceTest | PersistTour | ok |
| Index SavedData (ids, adopted regions, world data) | yes | yes | — | PersistenceTest | PersistTour | **fixed** (ids reused after a crash; ids per dimension) |
| Flushing save at stop, sliced autosave | yes | BEFORE_SAVE, level unload | — | — | PersistTour | ok |
| Settlers of a hamlet laid out off-thread at stop | yes | unload | — | — | — | **fixed** (lost) |
| Sim clock (due queue, budget, MSPT scaling, catch-up) | yes | END_SERVER_TICK | — | SettlementClockTest | stats | ok |
| One bad settlement / listener / blueprint must not break the world | partly | — | — | — | — | **fixed** (applier, hamlets, caravans, felling, embodiment and FOUNDED listeners, adoption, load, worldgen) |
| Atlas history from the seed | yes | yes | `/hearth atlas` | AtlasTest | adopted Askdal | ok |
| Worldgen writes history, guard keeps features off plans | yes | feature + mixin | — | — | stats | **fixed** (guard holes where two towns share a chunk; a throwing blueprint crashed worldgen) |
| Adoption on chunk load | yes | CHUNK_LOAD | — | — | PersistTour | **fixed** (failures now undone and retried next session) |
| Chunk applier with per-chunk ledgers | yes | load, progress | — | StepWriterTest | PersistTour | **fixed** (chunks loaded before reachable never written; CME when a plot was abandoned) |
| Embodiment (radius, hysteresis), two players in two towns | yes | presence every second | walk in | — | single player only | ok (code path), **gap** (no two-player run) |
| Arrival banner and F3 line | yes | payload both sides | walk in | — | IntegrationTour | **fixed** (stale after a portal) |
| Founding: command, `HearthWorld.found`, daughter hamlets | yes | yes | `/hearth settlement found` | — | PersistTour | **fixed** (unknown culture silently ignored) |
| Commands and permission levels | yes | CommandRegistrationCallback | op 2 | — | PersistTour (18 op-only commands closed) | ok |
| Configs `config/hearth/*.json` load, validate, fall back | yes | every module | file | HearthConfigTest | — | **fixed** (one bad value threw away the whole file) |
| Dimension rules (`dimensions`), unique ids across levels | yes | level load | config | — | — | **fixed** (live ids); **gap** (historic ids per level) |
| Dedicated-server safety | split source sets | payloads in main | — | compile | — | ok by construction; **gap** (no dedicated run: EULA rule) |
| Partners: Expanse probe, Atlas map, LOD footprints, Mettle skills | yes | behind isModLoaded / service | — | LodFootprintsTest, PlayerSkillsTest | Expanse in the run | ok |
| Server-thread cost | budgets | yes | `/hearth sim stats` | — | measured | **fixed** (848 ms tick after each load); **gap** (single chunk passes 10-47 ms) |

Counts: 20 features; 9 ok, 11 fixed (some partly), 5 gaps (listed below).

## What was fixed

1. **Chunks loaded before they were reachable were never written** (`ChunkApplier`, `SimModule`). Fabric's `CHUNK_LOAD`
   fires inside the chunk's promotion, before `getChunkNow` sees it; the applier dropped such keys "to be queued again on
   load", which never came. In game a town was missing 8903 blocks (beds, chests, doors, walls) until the world was saved
   and reopened. Chunks are now queued again on `FULL_CHUNK_STATUS_CHANGE` to FULL (which also covers border chunks
   promoted later), and a missing chunk is looked for a few more times.
2. **An 848 ms hitch after every world load** (`Woodcutter` → `SimWorld.isPlanned` → `Atlas.plan` computing a region's
   history on the server thread). `isPlannedNow` asks the atlas worker and counts the column as planned meanwhile.
   After: felling at most 1.5 ms in a tick.
3. **Crash safety**: each part of a world's tick runs under a guard (log once with the trace, then at 2, 4, 8 ... failures);
   a chunk pass that throws is skipped; worldgen skips a historic plot whose blueprint throws (no ledger: the applier
   retries at load); a settlement file that will not register is left out with its file kept instead of failing the
   level; a failed adoption is undone and retried next session; a `FOUNDED` listener that throws no longer unfounds.
4. **CME in the applier** when giving up a built-over plot: `PLOT_ABANDONED` listeners can re-index the settlement while
   the applier iterated the index's live list.
5. **Id reuse**: a settlement file numbered past the index's next id (index saved before it, then a crash) let the next
   founding take the same id and overwrite the town. Loading reserves every id. Live ids now come from the overworld's
   index so they are unique across dimensions (other modules key player state by id alone).
6. **Worldgen guard holes**: when one settlement re-indexed, the planned columns of every chunk it shared with another
   were dropped until the other re-indexed (trees and boulders on the other's plots). The guard is now the union of
   every settlement planning in each chunk, recomputed for the chunks that changed.
7. **Config fallback**: `HearthConfig` used every default when any one value did not decode. Values are now salvaged
   one by one; only the bad ones fall back, each named in the log (all modules).
8. **Settlers lost at shutdown**: a hamlet being laid out off-thread had already taken its settlers from the parent;
   a stop in between saved the parent without them. The world's unload waits for the layout and gives them back.
9. **Arrival banner across levels**: presence is tracked per player server-wide; going through a portal out of a town
   now clears the banner and F3 line.
10. Smaller: `/hearth settlement found <culture>` refuses a typo instead of founding the land's culture; a
    `min_budget_ms` above `budget_ms` no longer raises the budget under load; a settlement failing every step logs once,
    then now and then; `/hearth sim stats` names the slowest part of a tick and the slowest chunk pass.

## Verified in game

Seed 20261003, Expanse in the run, the live hamlet Canewade (26 plots) and the adopted village Askdal (64 plots):

- chunk unload and reload of the live town's 144 chunks: 0 blocks changed, 0 blocks written again, ledgers unchanged
  (only natural trees felled by the town's foresters, which is the land's own behaviour);
- 18 op-only commands are not reachable for a player without permissions; the core's commands run as an operator;
- the settlement files read back exactly as the live records;
- save, quit and reopen: both settlements came back exactly (people, plots and cursors, stores, coin, every module's
  data), the index hands out no id twice, and all 144 chunks' blocks are as before;
- the costs after the fixes: sim work 0.19-0.38 ms a tick on average; felling 1.5 ms at most.

Screenshots (same camera, before and after the reload):
`C:/Users/bitto/AppData/Local/Temp/claude/C--Users-bitto-VOID/564c6839-160a-4660-85ef-00c49dcb634f/scratchpad/w7/sys-core/0000_hearth_persist_before_reload.png`,
`.../0001_hearth_persist_after_reload.png`.

## Still missing or broken, ranked

1. **The full build is red outside the core**: 4 tests, all `civic/cemetery` (build module), fail on this branch and on
   the branch it was cut from. See `docs/requests/sys-core.md` §1.
2. **Single chunk passes of 10-47 ms** (`/hearth sim stats`: "slowest chunk pass ... 47.6 ms, 1 plot, 1 block"). The
   applier checks its 3 ms budget between stages and plots, not inside one; a stage of a long plot (a road) walks all its
   steps to find this chunk's. Worth indexing a blueprint's steps by chunk once.
3. **No two-player test.** Presence, embodiment and per-player state were read through and run with one player only; a
   client game test has one player and the dedicated-server test needs a EULA this lane may not accept.
4. **No dedicated-server run.** Safety rests on the split source sets (the main source set cannot name client classes)
   and on every payload type being registered in `main`; both hold.
5. **Config files are written as `{}`**: optional fields equal to their default are not encoded, so a server owner sees
   no knobs (they are documented in SIM.md §10 and each module's doc).
6. **Historic settlement ids are per level** (region-derived): only matters if the atlas runs in more than one dimension.
7. **Bare trunks by new plots**: `clearCanopy` lops crowns of trees rooted beside a plot and leaves the trunk; from above
   a spruce hamlet reads as a field of poles.
8. **The client game test framework can deadlock in `TestSingleplayerContext.close()` on 26.3** (two of eight runs);
   `PersistTour.quit` avoids it, the other tours still use `close()` (`docs/requests/sys-core.md` §2).
