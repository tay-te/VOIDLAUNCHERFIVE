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
