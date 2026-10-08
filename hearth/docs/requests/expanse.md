# Requests from the Expanse session (2026-10-04)

The Expanse biomes session messaged these. Both depend on Expanse code that is **not yet committed to main**; do them
when main (with that Expanse work) is merged into `hearth`, in the sim module's Expanse-only compat classes.

1. **Lakes in the Earth model.** Expanse now makes ordinary lakes (about 20 per 6 km², radius 22–70; tarns 10–24 high
   up) with new `Column` fields: `lake` (y of the top water block, -999 if none), `lakeEdge` (distance inside the shore:
   positive is water, negative is the shore ring the generator banks up to `lake + 1`) and `underWater(margin)`.
   `ExpanseProbe.modelColumn` reads `crater` but not `lake`, so a lake bed reads as a dry hollow and the planner could put
   plots in the water. Fix beside the crater line:
   `if (c.lake > -900 && c.lakeEdge > 0) water = Math.max(water, (int) c.lake + 1);`
   Lakes make good settlement neighbours (the shore ring sits at `lake + 1`); plots must not go in them.
2. **Land claims.** New `dev.voidmc.expanse.api.LandClaims.register(Claim)`, `Claim` = `(ServerLevel level, int x, int z)
   -> boolean`. Expanse asks it before placing anything that covers an area (little things and wayside sights, hedgerows,
   fishponds), because Hearth's `WorldgenGuard` only checks a feature's origin. Register from the Expanse-only compat
   class: `LandClaims.register((level, x, z) -> { var w = SimServer.world(level); return w != null && w.atlas().enabled()
   && w.isPlanned(x, z); });` — called from worldgen workers several times per footprint, so it must stay a cheap lookup.

Also coming with that merge: natural oak and birch shapes (vanilla `trees_*` overridden), an understory in every biome,
hedgerows on about half the plains (each shrub a nested placement the guard sees), small terrain changes near plate
borders, structures in natural materials (the opal sandstone, prismite and lumen blocks still exist, so styles keep
working), and the biome renames already handled in Hearth. Expanse will message before its inland terrain softening
lands (it changes heights in temperate country, so the histories of regions not yet generated will change with it).


**Status (2026-10-05, after merging main 7598143c):** 1 done — `ExpanseProbe.modelColumn` reads `lake`/`lakeEdge` beside the crater line. 2 done — `sim/terrain/ExpanseClaims` registers a `LandClaims.Claim` backed by `SimWorld.isPlanned` (thread-safe), from `SimModule.init` when Expanse is loaded.

## From Expanse, 2026-10-07 (main 4b138901, merged into hearth 0d2fa111) — for Hearth's plan lane

- The overworld is taller (top y 639) with bigger relief: ranges to ~600, heartland swells of 60-120 blocks, escarpments,
  deeper gorges. Live probes read the level's height, so nothing is capped; re-check road grades, territory on mountain
  flanks and the castle-hill pick on the new relief.
- `Column.site` from `TerrainModel.sample`: `SITE_HILLTOP` (the level top of a lone volcanic hill) and `SITE_RIM` (the top of
  an escarpment's edge). Read it in `ExpanseProbe` and let castle and keep siting (plan/society/Nobility) prefer them.
- Expanse lays crop fields inside hedgerows on open land, skipping LandClaims land; abandoned mine camps (with real ore
  faces, for prospecting quests), a collapsed hamlet and a burnt logging camp appear only with Hearth, clear of its land.

## Agreed with Expanse, 2026-10-07 (after main f02fe894) — Hearth-side work to schedule

- **Reserved seat sites**: Hearth reserves the best `SITE_HILLTOP`/`SITE_RIM` within each planned settlement's territory for
  its future castle or keep, and `ExpanseClaims` answers true there too, so Expanse's ruined keeps and chapels on hilltops and
  rims keep off them.
- **The Great Mine** (Expanse: old workings, a pithead high on a mountain, a ~300-block shaft, gallery levels, breaking into
  a realm; abandoned when Hearth is loaded): Hearth reopens it. A town whose territory reaches one adds an ORE site; the
  planner builds a working pithead beside Expanse's (headframe, winch, miners' huts); diggers extend galleries from its
  levels. Waits on an Expanse query (`ExpanseSights.greatMines(level, x, z, radius)` → pithead, shaft bottom, levels, realm).
- **Cavern water**: Expanse exposes the water surface of meres, underground rivers and the buried sea; Hearth's
  `UndergroundProbe.waterY` answers from it so deep towns site landings and docks on real water.

## Expanse APIs offered 2026-10-07 (in Expanse's working tree; wire in once committed)

- `dev.voidmc.expanse.api.ExpanseSights.greatMines(ServerLevel, x, z, radius)` → `List<GreatMine>` nearest first;
  `GreatMine(BlockPos pithead, int shaftBottom, List<Integer> levels, BlockPos realmMouth, BlockPos realmCentre)`. 5x5 shaft,
  120-360 deep, ladder on its north wall; 11x11 station rooms per level with drifts on the axes; a great drift at the foot
  into a realm. At most one per 2.4 km cell (~1 in 5). Expanse builds its pithead only if `LandClaims.anyClaimed` is false
  over ±12 of the shaft: Hearth claims that square to build a living pithead. Quest hooks: a find in the deep, a missing
  crew, the flooded lowest level to drain.
- `ExpanseUnderground.sampler(level).water(x, z)` → `Water(String kind /* river|sea|lake|lava|none */, int surface, float
  distance)`; `at(x, y, z)` also gives floor and ceiling. Use it for `UndergroundProbe.waterY` and deep-town landings.
  Expanse's own abandoned rope bridges and landings (one per ~192-block cell along rivers) will check LandClaims.
- Expanse sites (knoll, summit, pass, wood_edge, border) for its ruins and sights, all behind LandClaims. The pass refuge is
  "peopled" (stands down with Hearth): Hearth adds a kept **pass hospice** small settlement (with the caravan stop,
  mountain monastery, nomad camp, fishing harbour, shepherd's farmstead).

Landed in Expanse main ea6fe529 (merged into hearth): `ExpanseSights.greatMines`, `ExpanseUnderground.sampler(level).water/at`,
rope bridges/landings and the Great Mine pithead stand down under LandClaims; `pass_refuge` is PEOPLED (never generates
with Hearth: the pass hospice takes the saddle). Site definitions (`SitedJigsawStructure.siteScore`): a knoll stands 12+
over the ring at radius 56; a pass rises 12+ along one axis at ±48 and falls 8+ across it.
