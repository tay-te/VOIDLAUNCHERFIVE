# VOID Atlas

A beautiful world map and minimap for Minecraft 26.3 (Fabric, client side). Atlas maps what you have seen, in a
muted, hand-toned palette made to sit on VOID's dark UI: deep teal-navy water stepped by depth with a lighter
shoreline, soft greens, a forest canopy with texture, warm muted sand and red dunes, grey stepped mountains with snow
caps, all lit from the north-west by a flat-stepped hillshade that reads as crafted pixel art. Unexplored land fades
out through a two-pixel dither, never a gradient.

It works on any server and in singleplayer, alone or with the rest of VOID: the VOID Hub embeds its map page, and
other mods (Hearth's settlements) add their markers through a small API.

![The map](docs/tour/map_zoom2.png)

## Features

- **The map** (M): the design's map window (Figma `Hub · Map`): Surface · Underground · Nether segments, search,
  a rounded map with the position and biome readout, north mark, layer chips (Terrain · Structures · Waypoints ·
  Players, plus any a mod adds), zoom ± with the scale, and the list of waypoints, settlements and discoveries.
  Drag to pan, scroll to zoom round the cursor (smooth, and pixel-crisp when it settles on whole pixels per block),
  arrows pan, space recentres. Double-click (or B) adds a waypoint; right-click for *Add waypoint here*,
  *Teleport here* (creative/spectator with operator rights or cheats) and *Centre here*. Click a marker or a list row
  for its detail card (Figma `Hub · Map · settlement selected`).
- **Minimap** (N toggles; = and − zoom): a rounded square in a corner of your choice, north-up or turning with you,
  with your arrow, nearby markers, the north mark on its rim and a biome and coordinates readout under it. Hidden
  with F1, under any screen and under the debug overlay. Underground and in the Nether it shows the slice you are in.
- **Places, as an open-world map marks them** (see [docs/DISCOVERY.md](docs/DISCOVERY.md)): structures worked out
  from the blocks the client is sent, on any server with only Atlas installed (villages, ruined portals, shipwrecks,
  temples, monuments, mansions, outposts, igloos, dungeons, mineshafts, ancient cities, trial chambers, strongholds,
  geodes, trail and ocean ruins, fortresses, bastions, end cities, cave mouths), the game's own structure data in
  singleplayer (it wins), peaks by prominence, lakes, rivers, waterfalls and ravines from the map, and named regions
  of one biome, by its plain name ("Forest"; a mod such as VOID Expanse can give real names), and the cave biomes underground ("Dripstone Caves"). Each is **sighted** (a hollow "?") until you come within 24 blocks, then
  **discovered** (a toast, once). Major landmarks show at every zoom, minor places from 1:4 (labelled from 1:1).
- **Layers**: the surface; the **underground** in 8-block slices, drawn only from what you have been near (a
  connected-air reveal round you — no x-ray), with a depth scrubber, a side view and the places at that depth; the
  **Nether**, drawn the same way.
- **Markers**: you (violet), waypoints (name, one of eight muted colours, distance; edit and delete from the card),
  your last three death points per dimension, other players the client can see, the places above with their detail
  card (status, distance, level, day, what is known, the source chip, Track, Set waypoint), and other mods' markers.
- **HUD**: the discovery toast, the minimap's edge markers for sighted and tracked places, and an optional compass
  strip with the places on it.
- **Waypoints**: a beam in the world with a label that keeps its size (far: a thin core and a one-line label; near:
  a ground ring and "18 m · 6 above"; labels never overlap); quick-add with B (colour, icon, Always / Within N m /
  Temporary, share); death points that clear after 10 minutes or once you are back within 8 blocks; coordinates in
  chat made clickable (a client-side command, works on any server); `atlas-wp:` share codes arrive as a card with
  Accept / Ignore; Overworld and Nether coordinates on every waypoint card; import from Xaero's Minimap and
  JourneyMap (their files only read), offered once; colour-blind pin shapes (one per colour, on by default).
- **Minimap**: square or round, S/M/L, north-up or turning, cave view after a second without sky, an optional radar
  (faces for players, squares and circles for mobs, no red), hidden with F1 and F3.
- **Map**: day and night following the world's clock (or always day / night), PNG export (what you see, everything
  explored or a box; 1, 2 or 4 px a block) to `screenshots/atlas/`, a right-click menu (Waypoint here, Copy
  coordinates, Show in Nether, Track), search over places, biomes and waypoints.
- **Servers**: worlds behind one address (a proxy's lobbies and games) are told apart by their terrain before
  anything is mapped, never mixed; when unsure Atlas asks "keep separate / same as before". Servers' map rules are
  honoured (Xaero's, VoxelMap's, Rei's and JourneyMap's codes; see [docs/SERVER_RULES.md](docs/SERVER_RULES.md)); the
  radar is off on servers unless the server opts in.
- **Data**: maps live in `<game dir>/atlas/<sp/save | mp/address/world>/<dimension>/<layer>/`: per 512 × 512-block
  region a raw file (what is known of each column) and an image file (the region drawn, every mip level apart). Files
  are checksummed and written atomically; a damaged one is set aside and the region mapped afresh. Waypoints, places
  and the region index carry a checked header and rolling backups, and the newest good backup is restored if one is
  damaged (with a notice). A disk cap (4 GB by default) lets the least recently viewed far regions go first.

## Keys

| Key | |
|---|---|
| M | open / close the map |
| N | minimap on / off |
| B | quick-add a waypoint where you stand |
| = / − | minimap zoom |
| (unbound) | Atlas settings (Minimap, Map, Waypoints, Data) |

In the map: drag, scroll, arrows, space (recentre), B, Ctrl+F (search), T (track the selected place), W (a waypoint
on it), Esc (close the card, then the map); underground, Shift+scroll changes the depth and F follows your height.

## Install (and the VOID launcher)

Atlas is a normal Fabric mod: `build/libs/void-atlas-<version>.jar` goes in a Fabric 26.3 instance's `mods/` folder
next to Fabric API. The VOID launcher keeps its instances under `%APPDATA%\void-launcher\instances\<uuid>\mods`, so
dropping the jar there (or letting the launcher install it as part of a modpack) is all it takes. It needs nothing
else; with VOID Expanse installed it names Expanse's structures and biomes.

## Building and running

Java 25 is in `.tools/jdk-25` (`scripts/setup-dev.ps1` puts it there).

```
cd atlas
JAVA_HOME=../.tools/jdk-25 ./gradlew build               # the jar, and the unit tests
JAVA_HOME=../.tools/jdk-25 ./gradlew runClient           # a dev client with Atlas
JAVA_HOME=../.tools/jdk-25 ./gradlew runClient -Patlas.withExpanse=true   # ... and Expanse (composite build of ../expanse)
```

`-Patlas.withExpanse=true` includes `../expanse` as a composite build (as `lod/` does) and puts it on the dev runs'
classpath, so the map, the tour and the perf gate can run over an Expanse Earth world. Atlas never calls Expanse.

## Tests

| Command | What it does |
|---|---|
| `./gradlew test` | Unit tests, no game: every structure detector with positive and look-alike negative cases on synthetic chunks (`StructureScanTest`), the underground reveal's no-x-ray guarantees (`RevealTest`), the place book's states, merging, exact-data folding, persistence and migration (`PlaceBookTest`), peaks, waterfalls, ravines and patches (`RegionAnalysisTest`), areas joined across regions and their ids (`AreaIndexTest`), plain biome names and the world hash (`PlainNamesTest`), cave areas (`CaveAreasTest`), biome families (`BiomeFamilyTest`); the palette and tone curve, hillshade quantisation, elevation bands, water depth steps and shoreline, the dithered edge, and **golden images** of three small synthetic worlds (an island with a river, beach, forest and snow-capped mountain; stepped ramps; a cave slice); tile maths (block ↔ region ↔ pixel ↔ mip level, negative coordinates); the tile store (write/read round trip, single-level reads, corrupt, truncated and foreign files recovered); waypoint and discovery persistence (incl. a corrupt file); the marker-provider API (ordering, replacement, a throwing provider fenced off and switched off after three failures in a row). After a deliberate change of look: `./gradlew test -Patlas.golden.update=true`, then review `src/test/resources/golden/*.png` (6× copies in `build/test-images`). |
| `./gradlew atlasTour` | The screenshot tour (a client game test): makes a world, flies from spawn to the nearest coast and on to the nearest mountains, analyses the land, then photographs the full map at three zooms, the coast and mountains, the surface zoomed out with region names and "?"s, the legend, a sighted and a discovered place's cards, sighted and discovered side by side, the region labels, the embeddable map widget, the HUD toast with the minimap's edge markers and the compass, the underground at two depths (walked through real caves), the Nether, the minimap (north-up and turning), into `build/tour/` (with an `index.md` that also reports how the block heuristics did against the game's own structure data). `-Patlas.test.only=quick,map_0,...` runs a short version; `-Patlas.test.seed=` picks the world. |
| `./gradlew atlasSoak` | The soak and world-switch test: two dedicated servers in turn behind one address (needs `eula=true` in `build/run/atlasSoak/eula.txt`, the player's to accept), then a long session with many dimension changes and the map opened and closed; fails on two worlds mixed, an old world not found again, heap growth over 400 MB, or Atlas spending more than 33 ms of CPU in one frame. |
| `-Psodium`, `-Piris` | Any run with Sodium, or Sodium and Iris (26.3 builds), on the classpath, to check Atlas with them. |
| `-Patlas.baseline=true` (with `atlasPerf`) | The perf flight with Atlas idle: the game alone, for comparison (`build/perf-baseline/`). |
| `./gradlew atlasPerf -Pbackend=opengl` / `-Pbackend=vulkan` | The performance gate (see [docs/PERF.md](docs/PERF.md)): uncapped, scripted flight with the minimap on, a walk through a real cave, then a scripted pan and zoom over a few hundred tiles with the map open, on the surface and underground; writes `build/perf/<backend>.json`, updates `docs/PERF.md` and **fails** if the minimap averages over 0.3 ms/frame, the map screen (surface or underground) averages over 4 ms or its p99 is over 8 ms, chunk ingest takes over 1 ms of client-thread time in any frame, structure detection takes over 0.5 ms a chunk or the underground reveal over 20 ms a walk on the workers, or any frame takes over 33 ms. |

Both game tasks open a game window (1920 × 1080 by default, `-Patlas.test.width/height`); add
`-Patlas.withExpanse=true` to run them over an Expanse world.

## API (for the VOID Hub and other mods)

Everything is in `dev.voidmc.atlas.api`; it only ever grows.

**Markers from another mod** — an `"atlas"` entrypoint in your `fabric.mod.json`:

```json
"entrypoints": { "atlas": ["com.example.MyAtlasPlugin"] }
```

```java
public final class MyAtlasPlugin implements AtlasPlugin {
    public void onAtlasReady(AtlasMarkers markers) {
        markers.registerLayer(new MapLayer("hearth:settlements", "Settlements", true, 15));
        markers.registerProvider("hearth:settlements", 0, (query, out) -> {
            for (var s : Settlements.near(query)) {
                out.accept(new Marker("hearth:" + s.id(), "hearth:settlements", s.name(), String.valueOf(s.folk()),
                    "HEARTH  ·  VILLAGE", s.x(), s.y(), s.z(), query.dimension(), MarkerGlyph.HOUSE,
                    Marker.Style.SETTLEMENT, 0, s.detail()));
            }
        });
    }
}
```

**Places and names** — the same plugin can implement `onAtlasPlaces(AtlasPlaces places)`: `registerPlaceProvider`
(landmarks and discoveries of your own), `registerNameProvider` (names for regions, lakes, rivers, peaks — Expanse's
real names), `report(place)` for places a server tells the client about (`Source.voidServer()` gives them the "from
the VOID server" chip). Every marker carries a `Source` (the provider's id unless it says otherwise) and a `Tier`. See
[docs/DISCOVERY.md](docs/DISCOVERY.md).

Providers are called on the client thread each time a map draws (with the area it shows) and should only hand over
what they already know. A provider that throws loses its markers for that call; after three failures in a row it is
switched off and logged once. `MarkerDetail` fills the detail card: up to three big readouts, an optional cell meter
(a standing), and sections of rows with tags (the design's "What the town needs").

**The client API** — `AtlasApi.get()` (or `AtlasApi.available()` first, if Atlas is optional for you):

| | |
|---|---|
| `markers()` | the `AtlasMarkers` registry above |
| `collect(MarkerQuery)` | every marker (Atlas's waypoints, discoveries, players, and every provider's) as plain data |
| `waypoints()`, `addWaypoint(...)`, `removeWaypoint(id)` | the current world's waypoints (`Waypoint` records) |
| `discoveries()` | the structures discovered in this world (`Discovery` records) |
| `places()` | `AtlasPlaces`: every place sighted or discovered (`Place` records, with state, tier and source), place and region-name providers, `report` for a VOID server's places, listeners |
| `playerView()` | a `MapView` centred on the player at the map's zoom |
| `renderMap(graphics, x, y, w, h, MapView)` | draws a map view (tiles, markers, the player) into any rectangle, rounded |
| `createMapWidget()` | the whole interactive map page as a `MapWidget` for a screen of your own: `render(graphics, x, y, w, h, mouseX, mouseY)` and the mouse and key methods; this is what the VOID Hub's *Map* page hosts inside its shell |
| `openMap()`, `openSettings(parent)` | Atlas's own screens |

`MapView` is a record: layer (`surface`, `underground`, `nether`), centre, zoom in pixels per block (whole numbers
are pixel-crisp), rotation, whether to draw markers and the player, and the corner radius.

## How it draws

Chunks are read on worker threads (the client thread only queues them) into raw regions; dirty rectangles are drawn
(tone, hillshade, bands, depth, shoreline, dither coverage) into each region's image and mip levels; a composer
thread assembles 512 × 512 tiles at the right level from region images and patches only the changed rectangle of a
tile when a region is drawn again; the render thread uploads within a time budget into four 2048 × 2048 atlas pages
(least recently drawn tiles give up their slots). The map and minimap are textured meshes clipped to their rounded
shape on the CPU with an anti-aliased edge made of the same tiles fading out, through the stock GUI pipelines only —
so Atlas draws the same on OpenGL and Vulkan. Text is Outfit (SIL Open Font License, `assets/atlas/font/OFL.txt`),
rasterised by the game at each of Atlas's UI scales so it lands pixel for pixel.
