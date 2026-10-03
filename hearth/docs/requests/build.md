# Requests from the build module

Things the build module needs from files it does not own, and notes for the modules that consume blueprints.

## For the lead (shared files)

1. **`hearth/.gitignore`: add `logs/`.** The offline preview (`blueprintPreview`, a `JavaExec` on the gametest
   classpath) bootstraps Minecraft, which writes `hearth/logs/`. It is not ignored, so it is easy to commit by
   accident (it happened once, reverted in `b1dd7ba`).
2. **ARCHITECTURE.md §6 lists the synthetic terrains as `flat, slope, hills, cliff, river, coast`;** `SyntheticTerrain`
   names them `flat, slope, hills, river, coast, steep`. The build tests and previews use the latter.

## For the api owner

3. **`ArchetypeInfo.translationKey()`** (optional): `"hearth.archetype." + id.getPath().replace('/', '.')`. The build
   module already ships names under that key in `lang/en_us/build.json`, for UIs that list buildings.

## For the plan module (what the blueprints accept and promise)

4. **Params** the archetypes read (all optional; defaults are seeded choices):
   - every archetype: `variant` (int, the style variant; leave unset to vary by seed), `roof` (a `RoofForm` name, where
     the archetype has a choice);
   - `house/cottage`: `storeys` (1–3), `outshot` (boolean: a lean-to behind, on plots 10+ deep);
   - `house/townhouse`: `storeys` (2–5), `party_left`, `party_right` (boolean: shared side walls), `shop` (boolean),
     `terrace` (boolean: flat-roofed houses step their top storey back behind a roof terrace);
   - `commerce/market_stall`: `stripes` (boolean).
5. **Floor level.** Any `origin.y` works: the blueprint grounds itself (footings, stilts, levelling, steps and an
   approach to the street). Best results when `origin.y` is the street's walking level in front of the plot.
6. **Entrances.** `entrances().get(0)` is the main entrance: on the street row (`z = -1` local) or the cell outside the
   front door. Connect paths there. Every blueprint has at least one.
7. **New archetype:** `hearth:commerce/market_stall` (MERCHANT, CENTRAL, 4–7 × 4–6, hamlet+), with `MARKET_STALL` and
   `COUNTER` POIs for the economy module.

## For the sim module (placing blueprints)

8. Steps carry final, shape-resolved states: place them with `Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE`
   (and `UPDATE_SUPPRESS_DROPS` when replacing), no neighbour updates. Block entity tags are 26.3 `CompoundTag`s: load
   them with `blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, tag))`
   and `setChanged()`. `BuildCommand` does exactly this and can serve as the reference.
9. `/hearth build ... auto` asks `HearthServices.culture(biome)` for the style; without the sim's mapping it falls back
   to temperate.
