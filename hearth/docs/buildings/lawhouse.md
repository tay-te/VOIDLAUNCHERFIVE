# The law's buildings

Where the town judges, locks up, shames, hangs, watches and takes its toll (LIVING_WORLD.md §4, §8.5). Six archetypes
in `build/archetype/law/`, their parts in `build/kit/lawhouse/`, registered by `build/archetype/LawHouseArchetypes.java`.
All are category `LAW`, built in every culture's manner, and pass `BlueprintInvariantsTest` (light rule included).

| id | class | for | size (frontage × depth) | height | jobs, beds | max |
|---|---|---|---|---|---|---|
| `hearth:law/courthouse` | `Courthouse` → `CourtHall`, `CourtSingle`, `CourtYard`, `Yamen` | town+, VERTICAL | 13–19 × 13–19 | 28–36 | JUDGE | 1 |
| `hearth:law/jail` | `Jail` | town+ | 9–13 × 10–13 | 12–16 | JAILER, GUARD, 2 beds | 1 |
| `hearth:law/gallows` | `Gallows` | town+, CENTRAL | 7–9 × 7–9 | 9–12 | — | 1 |
| `hearth:law/pillory` | `Pillory` | village+, CENTRAL | 5–7 × 5–7 | 6–8 | — | 1 |
| `hearth:law/watch_house` | `WatchHouse` | village+, VERTICAL | 8–10 × 8–10 | 16–20 | GUARD, 2 beds | 6 |
| `hearth:law/toll_gate` | `TollGate` | town+ | 11–13 × 6–8 | 14–17 | GUARD, 1 bed | 2 |

## What each one is

**Courthouse.** An institution of its culture.
- *Towered court* (temperate, wisteria, alpine, moorland, maritime, mediterranean, steppe): a two-storey hall on the
  square, offices and records below, the court room above under a tall hipped roof with a window in each gable and the
  court's tiled stove with its flue through the roof; a five-square tower stands half out of the front with the portal
  at its foot and the loggia from which sentences are proclaimed. The Bohemian court carries the crown of the Prague
  bridge towers (a steep ringed pyramid with corner pinnacles, slate or green copper over tile towns, the roof's own
  shingle otherwise); onion in the Alps, slated spire in the north, campanile in the south, cone on the steppe.
- *Court in a courtyard* (desert, mesa): walled compound through a tall iwan, arcaded court with basin and palm, the
  qadi's hall under a dome at the back.
- *Yamen* (karst): gate hall with the petitioners' drum, stone courtyard, the main hall on a terrace with red columns
  under a double curved roof, the magistrate's table before a screen.
- *Single hall* (northern, tundra: the lawspeaker's log hall with a ridge turret; bayou, tropical: a raised gallery
  court on stilts).

Inside every one: the judge's dais and high seat (`THRONE`, JUDGE job site), clerk's lectern, records chest, the dock,
the bar and public benches (`SEAT`), the town's arms; the notice board outside.

**Jail.** A strong house: blind dressed-stone ground storey with barred slits and a grilled portal, the jailer's
storey above in the town's walls. Guard room across the front (desk and register, key rack, arms, confiscated goods),
cells behind iron bars (iron door, straw, bucket, chain; one `CELL` each), the jailer's room and turnkey's bunk upstairs.

**Gallows.** Stark and plain: a scaffold three steps up (timber deck on posts with knee braces, or a coped stone stage
in the masonry traditions), stair with rail, the frame of two uprights and a beam on corbels, chain ropes, trapdoor
drops, the hangman's lever. No gore. `GALLOWS` on each drop, `MEETING_POINT` for the crowd.

**Pillory.** A dais of the town's stone with a flight across its front; the pillory (posts, neck board, capped beam
with a lantern), stocks for two on wider plots, a bin of rotten vegetables. `PILLORY`, `MEETING_POINT`.

**Watch house.** A small tower house at a gate: two masonry storeys (guard room, bunks), then a timber lookout jettied
out on every side under a steep pyramid roof with the alarm bell (a crenellated roof terrace with a sentry pavilion in
the flat-roofed south). `GUARD_POST`, `VIEWPOINT`, `BED`, GUARD job site.

**Toll gate.** The toll keeper's room bridging the road on two piers, arched passage with lanterns; the booth with its
counter hatch and strongbox in one pier, the ladder in the other; a striped boom with counterweight across the field
side. `GATE`, `GUARD_POST`, GUARD job site, `STRONGBOX`-style storage, `BED`.

## Params

`courthouse`: `banner`, `tower` (`centre`|`corner`), `variant`. `gallows`: `ropes` (1|2). `toll_gate`: `over_road`,
`passage_width` (3|5), `street_y`. All: `station`, `local`, `variant`, `on_square`, `street_y` where they apply.

## Previews

`./gradlew blueprintPreview -Parchetype=law/<id> -Pstyle=all -Pseeds=1 -Pterrain=flat|hills` → `build/previews/law_<id>/`.

## Weak spots

- Jail roofs are low hips; the building is a plain cube in several cultures.
- Toll gate booms use coloured wool stripes (the town's colours) — intended, but loud in pale palettes.
