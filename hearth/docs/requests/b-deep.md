# Requests from the deep architect

What the deep town's buildings (`docs/buildings/deep.md`) need from files they do not own. Nothing here blocks: each
item has a fallback inside the deep package.

## For the plan module (`plan/deep/DeepTown`)

1. **Pass `ceiling_y` on every deep plot** (int, world y): the lowest underside of the realm's vault over the plot's
   footprint (and, for `deep/lift`, over the shaft: where its tower meets the rock). The buildings keep one clear of
   it; without it they are limited only by the plot's height, and a hall's towers or a stacked dwelling can be drawn
   into the vault where it comes down near the realm's wall. `RealmTerrain` already knows the vault; a helper like
   `DeepTerrain.ceilingOver(x0, z0, x1, z1)` (the synthetic one in `build/kit/deep/`) is all it takes. Also cap the
   plot's `maxHeight` at `ceiling_y - floorY - 1`.
2. **Pass `character` on every deep plot** (the realm's character, `UndergroundProbe.Realm.character()`). Today only
   the lift carries it; the others infer it from the `local` light (`DeepSite.character`), which cannot tell the
   dripstone, frozen, roots, fossil, tidal and brimstone realms apart.
3. **The lift's lower door faces `deep_facing`** (already passed): the blueprint opens its foot onto that side and
   cuts the way out to the plot's edge level. The deep square should take in the cell outside it (as `found` does
   with `PlotGeometry.entrance` of a plot facing `in`); the blueprint's own main entrance is the lower door when it is
   drawn on the realm's probe, the street door when drawn on the surface's.
4. **Stacked deep dwellings** (`Stacks.stackOn` into the rock, `Parcels.cutsIn`): the blueprint reads `stacked`; its
   front rows stand on the terrace of the dwelling below (whose standing level is that plot's `floorY + maxHeight - 1`,
   one step under the stacked floor) and it cuts its own back half into the rock. Good as is.
5. **`DefaultCatalogue` is behind the registered infos** (only matters for runs without the build module):

   | id | DefaultCatalogue (w × d, h) | registered (w × d, h) | why |
   |---|---|---|---|
   | `deep/hall` | h 10–18 | h 12–20 | towers over a steep nave |
   | `deep/dwelling` | h 6–10 | h 7–11 | an arcade storey, an upper storey and the terrace parapet |
   | `deep/market` | h 6–10 | h 8–12 | pavilions over the wings |
   | `deep/forge` | h 7–11 | h 8–14 | the stack and the louvre over the ridge |
   | `deep/glow_garden` | h 2–5 | h 6–9 | the pergola and the giant mushroom (under 7 a lamp post instead) |
   | `deep/lift` | 5–7 × 5–7, h 8–320 | 9–11 × 9–11, h 12–320 | a seven-wide shaft (stair round a lightwell) and its plinth |

   Proposed: copy the registered values (or derive the defaults from the build module's list at test time).

## For the lead (shared files)

6. **`Archetype.terrain(PlotSpec, TerrainProbe)`** — a default method added to `build/Archetype.java` (returns the
   probe unchanged) and used by `BlueprintServiceImpl.draw` when it makes the `Site`. Deep buildings use it to stand on
   a flat realm floor when a caller hands them the surface's probe (`DeepSite.probeFor`). Backward compatible.
7. **`BlueprintChecks.hangs`**: a horizontal chain (a festoon strung between two walls) was treated as hanging from
   above and failed the support check; now only a vertical chain hangs. A horizontal chain is held at its ends, which
   the floating check already covers.
8. **Previews without touching Gradle files**: `docs/buildings/deep_preview.gradle` is an init script that registers
   `deepPreview` and `deepTown` (`./gradlew -I docs/buildings/deep_preview.gradle deepPreview ...`). If the lead wants
   them as first-class tasks, they can be copied into `build.gradle` beside `blueprintPreview`.

## For the sim / expanse modules

9. **Expanse glow under the deep town.** The blueprints place Expanse's `glowcap`, `amber` and `prismite_block` as
   accents (keystones, planters, the market's finials) with vanilla stand-ins where Expanse is absent. The light rule is
   met by vanilla light alone (lanterns, shroomlight, froglights, sea lanterns), so nothing depends on Expanse's light
   levels.

## Integration (hearth-integrate-4)

- §1 done: every deep plot gets `ceiling_y` (the lowest vault over its footprint, `RealmTerrain.ceilingOver`) and its height capped at `ceiling_y - floorY - 1` (`PlanContext.withLocal`); the lift gets `ceiling_y` over its shaft.
- §2 done: every deep plot gets the realm's `character`.
- §3 done: the deep square already takes in the cell outside the lower door (`DeepTown.found`).
- §4 good as is.
- §5 done: `DefaultCatalogue` = registered infos (`CatalogueMatchesBuildTest`).
- §6, §7 merged as they were.
- §8 done: `build.gradle` applies every `docs/buildings/*_preview.gradle`, so `deepPreview` and `deepTown` are tasks of the build (`-I` still works).
- §9 nothing to do.
