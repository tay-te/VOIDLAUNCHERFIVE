# Buildings that age, dress and grow

Three systems that work on **every** archetype's drawing, without the archetypes knowing (LIVING_WORLD.md §7.2, §8.3,
§8.4). Code: `build/age/` (the passes) and `build/kit/age/` (their parts). Previews: `docs/buildings/age_preview.gradle`.

| system | driven by | what it does | who asks |
|---|---|---|---|
| **Decay** | plot param `decay` (0 to 1) | a monotone pass from weathered to roofless ruin | the planner / sim, for abandoned plots (boom and bust) |
| **Dressings** | `Dressings.steps(spec, blueprint, kind, day)` | extra steps for a special day, placed and taken down without touching the building | the social module |
| **Accretion** | plot param `additions` (count or list) | lean-tos, outshots, dormers, jetties, oriels, galleries, storeys, roof rooms, annexes on housing | the planner, as a household grows and prospers |

Pipeline (`BlueprintServiceImpl.draw`): `Age.draw` draws the house as first built (on part of its plot when it will
grow), then `Age.apply` runs accretion and then decay, then the compiler lights, resolves and orders it as usual. A plot
without either param is drawn exactly as before.

## Decay

`decay` accepts a float or double 0–1, an int percent (`50`) or a string (`"0.5"`).

| decay | state | looks like |
|---|---|---|
| 0 | kept | as built |
| 0.05–0.2 | weathered, still lived in | moss up the north plinth, ivy at corners, cracked stone, a shutter askew, weeds; **light rule in full** |
| **0.25** | **abandoned** (`Decay.ABANDONED`) | lamps, bedding and pots taken, cold hearths, dead crops, cobwebs; no beds, job sites or stores as POIs |
| 0.5 | abandoned for years | holes in the roof, broken ridge, door off or hanging, glass and shutters gone, ivy to the eaves, sagging floors |
| 0.75 | derelict | roof mostly gone, gables crumbling, upper floors fallen, rubble mounds and fallen beams, bushes inside |
| 1 | ruin | a roofless shell at ragged heights (corners and stone longest), a lone chimney, rubble, a tree in the hall |

Every block gets a *life* (the decay at which it goes), capped by a widest-path support pass from the ground, so the pass
is **monotone** (more decay only takes more) and **nothing ever floats**; the way in from the street always stands.
Growth (moss, ivy, weeds, litter, cobwebs, bushes, trees, rubble where things fell) lies over what stands.

**The light-rule exemption (precise):** `LightCheck` skips the light rule (rooms ≥ 8, none at 0, ≥ 8 outside entrances)
for a plot whose `decay` ≥ 0.25 and for nothing else. The surface-glow rule (no Expanse light blocks above ground) still
applies to ruins. Below 0.25 a weathered house keeps its lamps (the lighting pass runs after decay). Tests:
`DecayTest` (all archetypes × 14 cultures × 6 terrains; monotone; deterministic; identity at 0; abandoned = nobody's).

## Dressings

```java
List<Dressings.Step> steps = Dressings.steps(plot.spec(), blueprint, Dressings.Kind.FESTIVAL, day, terrain);
// place each step in order; to take it down, set each position back to air if it still holds the step's block
```

| kind | what goes up |
|---|---|
| `MARKET` | striped awnings (the town's two colours on a board frame) over ground-floor openings; barrels, crates and hay with wares before the front; cloths on the sills |
| `FESTIVAL` | a leaf-and-blossom garland over the main door, banners in the culture's colours by the door and between upper windows, lanterns under the front eave, flower pots on the sills |
| `WEDDING` | a garland heavy with blossom, white and pink ribbon banners, a white runner at the threshold, white and pink flowers on every sill |
| `MOURNING` | black banners by the door, black cloth on sills and threshold (white in karst and wisteria) |
| `WINTER` | snow on every ledge the sky reaches and drifted in the yard: **cold cultures only** (tundra, northern, alpine, moorland); empty elsewhere (`Dressings.applies`) |

Contract (tested in `DressingsTest`): world positions in placing order (supports first); only where the blueprint places
air or nothing; inside the footprint and height, or over the street at 3+ up; every step held by the building, an
earlier step or the ground; doorways, the cells beside doors and entrances clear (a runner excepted); deterministic in
(spec, kind, day); cheap and thread-safe. Colours per culture: `Dressings.colours(culture)`.

## Accretion

`additions` is either a **count** (int, or a numeric string; 0–8): that many additions, chosen and ordered by the plot's
seed among what suits the house and its culture's habits; or a **list** of kinds (a list tag of strings, or
`"lean_to, dormer"`) tried in that order. Additions are **cumulative**: a house's first n + 1 additions are its first n
and one more, so a planner that counts up sees the house grow, never rebuilt. Housing only; other categories ignore it.

| kind | what is added | where it fits |
|---|---|---|
| `lean_to` | a one-storey room against a side or the back, a roof falling away from the house tucked under its eave, a door cut through, a window, furnished as the household's workshop (its `household.job`) or a store; open-fronted woodshed with a woodpile where no door can be cut | 3–4 rows of free yard |
| `outshot` | the same across the whole back | the whole back free |
| `annex` | a lean-to under a flat deck with a coping (flat-roofing cultures, and flat-roofed houses) | as lean-to |
| `dormer` | a window bay cut into the roof over the attic floor: posts, gable infill, its own little gable running back into the slope, the eave broken in front, the attic opened into it; a pair on a long slope | an attic floor and a slope that rises past the dormer's ridge |
| `jetty` | the upper storeys rebuilt a block out over the street or yard on beam ends and brackets; floors run out, what hung on the old wall goes with it | an eave wall (not under a gable), a storey above the ground floor |
| `oriel` | a glazed bay on corbels (latticed where the culture screens its windows) on an upper storey | an upper storey with two clear rows |
| `gallery` | a deck along an upper storey on brackets, a door cut out onto it | a non-party wall clear of dressing |
| `storey` | the roof lifted whole (with chimneys, finials) and a storey built under it repeating the one below window for window, a floor, a ladder up, a bedroom | a pitched roof whose eave course is its own (not running on into a wing), height to spare |
| `roof_room` | a room on a flat roof terrace rising from the house walls at a corner: door onto the terrace, window, coped flat roof, a bed; a ladder up where the house had no way onto its roof | a flat deck with room beside the chimney and hatch |

**The yard to grow into.** A housing plot carrying `additions` (any value, **0 included**) is first built on part of its
plot (`Accretion.reserve`): up to 4 rows kept behind where the plot is ≥ 3 deeper than the archetype's least depth, and
up to 4 columns on one non-party side where it is ≥ 4 wider than its least frontage. The front stays on the street.
Houses on water, on terraces and stacked on roofs build on the whole plot. So the planner should set `additions: 0` from
a growing household's first build and give such plots a little more room than the archetype's minimum.

**Safety.** Each addition reads the house as the last one left it (`HouseScan`: body, eave, floors, flat or pitched,
party walls, free yard, the way in from the street, which no addition may cover); what an addition cuts loose comes
down (`Settle`: a lantern under a removed bracket, a torch on a moved wall); an addition that leaves any bed, job site
or store out of reach (`Reach`, the checks' walk on the drawing) or cannot finish is taken down again (`Settle.restore`)
and the next kind is tried. Every grown house keeps every invariant, light rule included (`AccretionTest`: all housing
× 14 cultures × 6 terrains; deterministic; beds and bulk never shrink; most houses with room grow).

## Previews

```
./gradlew -I docs/buildings/age_preview.gradle agePreview -Pmode=decay -Parchetype=house/cottage -Pstyle=temperate
./gradlew -I docs/buildings/age_preview.gradle agePreview -Pmode=dress -Parchetype=house/shophouse -Pstyle=all
./gradlew -I docs/buildings/age_preview.gradle agePreview -Pmode=grow -Parchetype=all-housing -Pstyle=temperate -Pparams=levels=0/2/4,back=true
./gradlew -I docs/buildings/age_preview.gradle ageTown -Pculture=temperate -Pdays=300 -Pterrain=hills -Pseed=2
```

`-Pparams` takes `levels=a/b/c`, `back=true`, `yaw=`, `scale=` (renderer pixels per block), `cell=`, `seed=`, `name=`,
`list=true` (dress: print the steps) and any spec key (`frontage`, `depth`, `tier`...). `HEARTH_AGE_LOG=1` prints what
each addition did, `HEARTH_AGE_DBG=1` why one did not fit. `AgeTown` renders a planned town three ways: as planned,
grown (more additions nearer the old heart) and in decline (a quarter east of the heart abandoned, decay rising outward).
