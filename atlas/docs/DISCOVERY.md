# VOID Atlas — discovery, landmarks and the underground

Atlas marks places the way an open-world game does: big landmarks you can see from afar, smaller places to come back
to, the names of the land. Everything here works with **only Atlas installed on the client**, on any server; with the
game's own data at hand (singleplayer) or with VOID mods and servers, it gets better, never different in kind.

## Places and their states

A place is **unknown → sighted → discovered**, and only ever moves forward (`PlaceBook`, kept per world in
`places.json`; an older `discoveries.json` is read once and carried over).

| | Surface | Underground |
|---|---|---|
| sighted | found in the loaded land (structures), or on the map (landmarks); a "?" | within 32 blocks of the player, in three dimensions |
| discovered | the player came within 24 blocks (of its bounds, when known) | within 16 blocks, in three dimensions |

Being above a dungeon is not finding it: underground places stay unknown (not shown, not counted) until the player is
near. A named region is discovered by walking into it; a lake or river by standing in it or near its label.

**Tiers.** Major landmarks (villages, temples, monuments, mansions, ancient cities, strongholds, forts, trial
chambers, peaks, lakes, waterfalls) are 26 px tiles labelled from 1:4; minor places (ruined portals, shipwrecks, cave
mouths, ravines, geodes, dungeons, mineshafts, trail ruins) are 16 px tiles labelled from 1:1.

**Sources** (the source chip): *Seen by you* (worked out from the blocks or the map), *From the world* (the game's own
structure data, singleplayer), *from the VOID server* (`AtlasPlaces.report` with `Source.voidServer()`), *from Hearth*
(another mod's provider).

## Structures from their blocks

`StructureScan` runs on the worker that reads each chunk. Every section's palette is **counted** (no block visited);
only sections whose palette holds an *anchor* are walked block by block, and each anchor is judged by the blocks round
it (which may lie in the neighbouring chunks). A village needs a wider look, so `Detections` weighs the beds and job
sites of the chunks within 48 blocks of each bell. Every rule asks for a combination that does not occur in nature
and is unlikely in a build — a false landmark is worse than a missed one.

| Structure | Rule |
|---|---|
| Village | a bell, and within 48 blocks two beds (four bed blocks) and a job site |
| Ruined portal | crying obsidian against obsidian, at least five frame blocks round it, no gilded blackstone and fewer than 24 polished blackstone bricks (not a bastion) |
| Shipwreck | a chest among at least 20 ship-wood blocks, at least 8 of them drowned (waterlogged or under water) |
| Desert pyramid | blue terracotta in a star of 4+ orange terracotta in a sandstone hall (24+), or a grid of 4+ TNT under a stone pressure plate in sandstone |
| Jungle temple | a dispenser, tripwire hook or sticky piston among 12+ mossy cobblestone, 12+ cobblestone and chiseled stone bricks |
| Ocean monument | 64+ prismarine in a chunk, with 4+ dark prismarine or 2+ sea lanterns |
| Woodland mansion | 160+ dark oak planks in a chunk, with 16+ cobblestone or 32+ birch planks |
| Pillager outpost | a white banner over 8+ dark oak logs and 8+ birch planks, or 40+ birch planks and 16+ dark oak logs (with few dark oak planks) |
| Dungeon | a spawner in a room with 4+ mossy cobblestone and 16+ cobblestone in all |
| Mineshaft | 6+ rails, 4+ wood fences, 6+ planks and a cobweb in a chunk, or a spawner in 6+ cobwebs by the supports |
| Ancient city | 4+ reinforced deepslate, or 48+ deepslate tiles and bricks with 8+ sculk and a shrieker or sensor |
| Trial chambers | a trial spawner or vault among 8+ tuff bricks or copper, or 96+ tuff bricks with 16+ copper in a chunk |
| Stronghold | 3+ end portal frames among stone bricks, or 96+ stone bricks (12+ mossy or cracked) with infested bricks, bookshelves, or iron bars in a big corridor |
| Geode | budding amethyst or an amethyst cluster with 4+ amethyst blocks and 2+ calcite round it |
| Trail ruins | suspicious gravel near 4+ terracotta or mud bricks, out of the water |
| Ocean ruin | suspicious sand or gravel among 6+ stone bricks or sandstone, under water |
| Igloo | a furnace in 20+ snow blocks with ice and white carpet |
| Nether fortress / bastion / end city | 64+ nether bricks with fences or wart / 96+ polished blackstone bricks, or 32+ with gilded blackstone / 64+ purpur |
| Cave mouth | ground in the open beside a passage two blocks high that runs 4+ blocks into the hill, with earth for its floor and rock for its roof |

Only vanilla's block ids carry signals, so a mod's look-alike blocks never make a landmark. A place is **underground**
when at least six blocks of natural earth lie over it (a structure's own roof is not ground).

**Exact data wins.** In singleplayer every structure start round the player (as far as they can see) is filed with its
bounds. A sighting of the same structure near it, or any sighting inside its bounds, is folded into it (keeping how far
the player had come with it), and later sightings inside are ignored. The tour reports how the heuristics did against
exact data (`build/tour/index.md`).

## Natural landmarks and names

`RegionAnalysis` reads each region's stored surface map a few seconds after it was last drawn (on a worker) and keeps
what it finds beside it (`r.x.z.feat`):

* **Peaks** — local maxima on a 4-block grid (trees discounted), at least 24 blocks above the sea, flooded downhill
  (highest ground first) until higher ground is reached: the lowest ground crossed is the col, and a summit with 30+
  blocks of prominence is a peak. Unexplored land stops the flood, so no peak is claimed on land not seen.
* **Waterfalls** — water standing 6+ blocks above the water beside it.
* **Ravines** — ground 14+ blocks below the land 6 blocks away on both sides, in a long, narrow run.
* **Regions, lakes, rivers** — 8-block cells by biome, lake water and river water, joined into patches, and the patches
  joined across region borders (`AreaIndex`). A region of one biome needs ~160 × 160 blocks; a lake 1,500 square
  blocks; a river 30 cells. An area keeps the id of an earlier area whose label point it covers, so its name survives as
  it grows.
* **Cave mouths** come from the chunk scan (above).

**Names** are generated deterministically from the world's seed hash, the feature, the biome family and where it was
first found (`NameGen`: "Larkspur Meadow", "Greytooth Range", "Lake Merrow"), unless a mod supplies one: a
`RegionNameProvider` registered through `AtlasPlugin.onAtlasPlaces` is asked first (VOID Expanse gives its lands their
real names this way). Names are kept with the world.

## The underground: what you have seen, never what is loaded

The old slice reader drew every cave in the loaded chunks — x-ray. It is gone. The underground map is written only by
the **connected-air reveal** (`Reveal`):

* while the player is under the ground (six blocks of something that is not leaves over their head) or in the Nether,
  each time they move three blocks (or every two seconds), a worker walks **breadth-first through open blocks** (air,
  and water, which can be seen through) from their feet and head, inside a sphere of **24 blocks** (32 in the Nether's
  caverns), at most 60,000 open blocks;
* every open block reached is revealed, and every solid block bounding one is revealed as wall; lava bounds the walk
  (seen, not walked through);
* a cave behind a wall, however close, is never reached: it stays hidden. A chamber round the corner of the passage
  the player stands in is revealed, as a look round with a torch would;
* the result is written in **8-block slices** (`under_<n>`): for each column, the floor under the highest open block
  seen in the slice (with its water or lava), or rock where only wall was seen and nothing better is known.

The map draws a slice as the design asks: floors lifted 0.04 L and stepped −0.016 L for every 4 blocks below the
slice, hillshaded, shadowed at the foot of west and north walls; walls as a 1 px rim (#2A2C31), a step (#1D1F22) and
rock (#141517), only within 3 px of seen air; everything else is the map's well, with the surface's dithered edge.
"Water & lava" can be switched off (water then shows its bed).

## The API

Everything is in `dev.voidmc.atlas.api` and only grows; Atlas runs the same with none of it in use.

* `AtlasPlugin.onAtlasPlaces(AtlasPlaces)` (the `"atlas"` entrypoint) or `AtlasApi.get().places()`:
  `registerPlaceProvider` (pull, about once a second; a place given as UNKNOWN has its state kept by Atlas),
  `registerNameProvider`, `report` / `forget` (push, e.g. a VOID server's landmarks with `Source.voidServer()`),
  `places(dimension)`, `addListener` (sighted and discovered, for a toast or a journal).
* `Place`, `DiscoveryState`, `Tier`, `Source` (with `label()` and `phrase()` for chips).
* `Marker` gained `source` and `tier` (the old constructor still works; a provider's markers are credited to it).
* `MapView.underground(x, z, zoom, y)` draws a chosen depth; `MapWidget.showLayer` / `showDepth` steer the embedded
  map page.
