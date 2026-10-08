# Requests from wave 7 lane L6 (land, extraction, deep, infra)

Branch `hearth-w7-land`. What the build side now does, and what it needs from the lanes that own the rest.

## 1. Town lane / T4 planner (`plan/growth/Upgrades.java`, `plan/road/**`): walkways

`infra/walkway` now stands on posts to the ground at both ends and every fourth step along both edges (stone piers
under the stone passage, frame posts under timber, piles under planks), so a gallery whose house is not built never
hangs in the air (review 2 item 5, AUDIT L6-6). The posts stand in the street's edge columns under the walkway.

- Lay a walkway only between two facades that are built (or queued together with it) and stand at least
  `walkway_y + 3` tall; never over a junction. Where the planner can promise that, pass `posts=false` and the gallery
  rests on the facades alone, as designed (the posts then disappear from the street).
- The long lines of plank walkways across the tropical coast town (review 2) are many walkways end to end: a walkway
  should join two houses, not run along a street.

## 2. T3 planner (`plan/growth/Farms.java`): the countryside belt

The farm archetypes are ready to ring a town:

- every terraced field and paddy keeps its compost bin and chest on any terrain (`workstations >= 2` in every case);
- orchards plant six or seven apart (rows that read from afar), trees have boughs and lobed crowns, not blobs;
- `farm/field` lays its beds along the long axis of a long plot (strips between the channels, one crop each), so
  long narrow parcels along the roads out of town come out as strip fields (the Bohemian *lány*).

Asks: fields along the roads out of town on long narrow parcels (9-11 wide, as deep as `farm/field` allows: 24; ask
here if strips should run longer and I will raise `maxDepth`); orchards behind the houses at the edge; a `farm/pasture`
by every farmstead (13 is its smallest side; ask if a 9-11 paddock is wanted and I will lower it).

## 3. L0 / T12 (`build/check/**`, the audit)

- Fall edges beside a ladder: a climber on a ladder up a facade counts every rung beside open air as a fall edge. The
  deep dwelling now cages its front ladder with a post of the railing; a rule that exempts the climbing column of a
  ladder (or counts only cells a person stands in) would let ladders stay open as they are in real life.
- The deep buildings and extraction on the synthetic `cliff` (and some on `slope`) report fall edges on the
  synthetic terrain beside the plot (cliff steps at the plot's edge), not in the build. `deepPreview` on a realm floor
  shows them clean; an audit terrain for the deep (the realm's floor) would make the numbers mean what they say.
- The street before a plot: `BlueprintChecks.reach` starts the street at the plot's floor in front (the planner's
  contract), while the audit's synthetic `cliff` and `slope` leave the land falling away before some plots. The deep
  halls and dwellings now kerb the street row only where some of the street is there and no water lies before it, and
  never between the street and the way in; an audit street at the floor before every plot (as the checks assume) would
  make the front-row fall edges mean what they say.
- Unbound tags offline: `BlockTags` (`DIRT`, `BASE_STONE_OVERWORLD`, ...) are empty when blueprints are drawn in the
  tests and the audit (no data pack loaded), so a generator testing `state.is(tag)` behaves differently offline and in
  game. `farm/StepRoom` now names its blocks; a shared helper (or loading the vanilla tags in `Bootstrap` for the
  audit) would keep the two the same everywhere.
- The mine's walk: folk do not walk onto rails they did not start on (vanilla `UNPASSABLE_RAIL`), so a gallery whose
  only way past a timber set was the track shut the miners out. The audit's player walker passes rails; a folk-aware
  check (rails not walkable unless started on) would catch this offline.

## 4. Homestead kit owner (`kit/homestead/Terraces.java`)

`Terraces.accessPath` lays the path block at a band's level over cells where the flight through the next bank already
descends inside the band (the step one or two under the path), so the path buries the flight. `farm/StepRoom` now
notches those (and banks a flooded bed's last cell where the notch meets it), but the kit should not lay a path over a
step in the first place: skip a cell whose column holds a step at `level - 2` or `level - 3`.

## 5. NPC lane (diggers)

`extract/mine`: every work face is reachable on foot beside the rails (timber sets stand in the rock's line; the
heading's WORK_FACE is on the track, a side lane is one block from it, so `createPath(face, 1)` reaches it). Miners
should stand beside the track at the heading, not on it.
