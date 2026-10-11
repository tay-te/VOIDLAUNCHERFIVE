# VOID Gloam: architecture

Gloam is the living air of the VOID world. Expanse is the land, Hearth the people, Mettle the player; Gloam is the
sky, the weather, the light, the mist, the small things drifting in the air and the sound of all of it. Its target is
the look and feel of Kingdom Come Deliverance II's Bohemia: a real, green world whose distances are full of air.

Rules it keeps:

- **Natural on the surface.** Real-world skies, real weather, real light. No coloured or glowing fantasy air on the
  surface (fireflies are real; glowing spores are not). Underground realms may keep their strange atmospheres.
- **Works with and without shader packs.** Everything goes through what both paths read: environment attributes,
  the rain level, particles, sound, and geometry drawn the way VOID LOD draws (see *Shader packs* below).
- **Good LOD support.** The air does not stop at the render distance. VOID LOD shows 8–16 km of land, and the
  weather, clouds, mist and haze over it come from the same model as the air at the camera.
- **No sync needed.** The model is a pure function of the world's hashed seed (sent to every client) and its clocks,
  so every client and the server see the same sky at the same place.

## The model: `dev.voidmc.gloam.air`

| Class | What it is |
|---|---|
| `Air` | The deterministic model: the year and seasons, the wind, the weather fronts, the mist. Pure, unit-tested (`AirTest`). |
| `Atmosphere` | The client's tracker: once a tick, works out the air at the camera (`AirSample`), keeping the one bit of history (ground wetness) and smoothing what the land gives. |
| `AirSample` | The air at the camera this tick. What every other part reads. |
| `Climate` | What the land gives the air at a place: biome temperature, downfall, lowness (valley vs ridge), nearness to water. |
| `Wind`, `Season`, `Noise` | Small value types and the hash noise. |

Key behaviour:

- **Year.** `seasonDays` (default 8) Minecraft days per season; a new world starts a little into spring. `warmth` swings
  from -1 (midwinter) to 1 (midsummer).
- **Fronts.** A fractal noise field, ~3 km to a system, drifting east at 6 blocks/s and changing shape as it goes.
  `front` (0..1) gives `cloudCover`, `showers`, `storm`. Vanilla's weather stays in charge of gameplay (crops, fire,
  snow, mobs); Gloam decides where it *shows*: under vanilla rain most land is wet with clear gaps, and under a clear
  vanilla sky the fronts still bring cloud and the odd passing shower.
- **Wind.** Mostly westerly, swings over hours, freshens over minutes, gusts over seconds; stronger in storms.
- **Mist.** Gathers after midnight in low, wet places, thickest at sunrise, gone by mid-morning; a thinner veil after
  sunset over water. Autumn and cold make more, summer less, recent rain leaves the ground steaming, deserts none.
  `mistDepth` gives how high it reaches above the ground.

The time conventions: `clock` = `Level.getOverworldClockTime()` (tick 0 sunrise, 6000 noon, 18000 midnight);
`gameTime` = `Level.getGameTime()` (never jumps).

## The parts

Each part has one package, one entry class with `init()` (client) and, where it registers things, `register()`
(common), called from `Gloam` / `GloamCommon`. Parts do not call each other; they all read `Atmosphere.get().here()`
and `Atmosphere.get().air()`.

### Sky and light: `dev.voidmc.gloam.sky` (`GloamSky`)

26.3 computes the sky, fog, light, stars, ambient sounds and ambient particles from **environment attributes**
(`net.minecraft.world.attribute`), built from layers: dimension, biome, timelines, weather. `ClientLevelMixin` adds
Gloam's layers on top (`GloamSky.addLayers`). Anything reading the attributes follows: vanilla's sky and fog, VOID
LOD's haze (its `FogRendererMixin` reads them through the camera's probe), and the uniforms shader packs get.

- **Dark nights.** `SKY_LIGHT_FACTOR`, `SKY_LIGHT_COLOR`, `AMBIENT_LIGHT_COLOR`, `STAR_BRIGHTNESS` from the moon phase
  (`MOON_PHASE`) and the cloud cover: a clear full moon lights the fields silver-blue, a cloudy new moon is truly dark
  (`darkNights` setting). This is what gives Hearth's lighting rule and its stealth their weight.
- **Dawn and dusk.** `SUNRISE_SUNSET_COLOR` and `SKY_COLOR` richer and more varied, muted by cloud.
- **Overcast.** `SKY_COLOR`, `CLOUD_COLOR`, `FOG_COLOR` greyed by the front overhead even when vanilla says clear.
- **Mist in the fog.** `FOG_START_DISTANCE` / `FOG_END_DISTANCE` pulled in while the camera is in the mist layer.
- **Regional rain.** The client's rain level at the camera follows `Air.rain` (so vanilla's rain renderer and shader
  packs' `rainStrength` both follow); `Atmosphere.vanillaRain` keeps reading vanilla's own level.

### Motes: `dev.voidmc.gloam.motes` (`Motes`)

Particles, which shader packs draw too, drawn pixel-true (docs/STYLE.md): crisp nearest-filtered sprites at about
vanilla's particle pixel size, a few flat alpha steps with a Bayer dither between them, fading in and out in steps of a
quarter, colours from small palettes. Spawned around the camera from what is really there (leaf blocks overhead,
grass and flowers, water, the mist layer), moved by `Wind`, scaled by the `motes` setting and a frame budget.

- Falling leaves under trees (4x4 and 5x5 pixels, three greys tinted with one clean colour: their tree's green lifted
  paler, or one of five autumn colours), more in autumn and in gusts.
- Pollen and dust motes catching the light in meadows on warm still days.
- Fireflies over grass and water at dusk in late spring and summer: 2x2 bright pixels and a darker ring, no glow,
  blinking on and off in steps.
- Mist wisps drifting low over the ground and water while the mist is up.
- Breath in the cold; snow drifting in the wind; spray at waterfalls.

`MoteSpawner` tops the ambient kinds up toward a number worked out from the air (filling empty air at once with motes
already partway through their lives) and fires the rest as events (a leaf from a canopy underside, a breath, spray where
falling water lands); the stretch of the view two to twelve blocks ahead is also kept stocked with leaves let go from
the canopy over it, so falling leaves are always in frame under trees (slow, swinging, tumbling, green ones lifted paler
to stand out). The ground mist near the camera is a few hundred flat sheets (`MistMote`) out to the chunk edge,
layered and densest in the lowest blocks, drawn through `MoteLayers.GROUND` with the same look as `FarMist`
(opacity from the eye's path through the slab, colour leaning from the fog to the cloud colour), so it runs on into the
far banks. Each sheet is drawn on the world's own texel grid (`ground_mist.fsh` with `GLOAM_SOFT`): 16 texels a block
near the camera (8, 4, 2 further off), the sheet's ragged shape and grain from noise on that grid, its opacity in four
flat steps with a Bayer 4x4 dither on the same grid where one step meets the next, its colour picked from a four-tone
palette between the fog's shadow tone and near-white. The sheet's centre (x, z modulo 256) comes in as its texture
coordinate and its size and seed in its colour, and the camera (modulo 256) and the clouds' colour in a uniform
(`SoftMist`). Without a pack (and without improved transparency) the sheets are soft (`SoftMist`): their draw is held
back from vanilla's particle pass and made right after the main pass, against a copy of the main depth, fading (in the same
dithered steps) where the scene behind comes close, so no sheet ends in a straight line along a slope or a step; a
sheet level with the eye, or within a few blocks of it, fades too. With improved transparency the stepped, dithered
mist sprites are drawn instead. Looked down into steeply, a sheet's optical depth is eased toward a ceiling, so mist seen from above (a gorge
floor from its flank) is a thin veil and not a floor; and the number of sheets follows the share of the land around
that can hold mist (a running survey of places, too steep or above the mist's top), so they are not heaped onto the
few flat places in mountains. One's own breath shows low in the view and thins close to the camera (under a pack it
stays a faint wisp, never a lit blob). Under a pack (which treats flat translucent quads as glass) the sheets become upright
veils: 128x48 sprites in four alpha steps with a dither between, so the pixels survive the pack's lighting.
Breath, spray and powder (small stepped pixel puffs and streaks) draw through `MoteLayers.MIST` (no alpha cut, no depth
writes, the colour of the air at full brightness); fireflies through `MoteLayers.GLOW` (additive, never through the
lightmap, so dark nights cannot dim them). Every soft sprite leaves its outer pixel empty. Spray is capped at 360 puffs,
so a canyon of falls cannot take the whole budget. Under a pack the pipelines go to Iris's translucent
particle program and the pack lights them.
Sprites and the icon: `tools/gen_motes.py`. Settings: `motes` (density), `moteLimit` (cap), `breath`. Each showcase
view logs mote counts and their cost per tick.

### Soundscape: `dev.voidmc.gloam.sound` (`Soundscape`)

Layered ambient beds chosen from the air and the land around the camera, cross-faded, muffled indoors
(`AirSample.indoors`), on the ambient sound category (so Minecraft's ambient slider scales them) times the `sound`
setting.

- Wind, by its speed, the altitude and the exposure; through leaves in forests, over grass on open land, hollow on
  bare summits and over open land in winter; its pitch rises a little with the gusts and the cold.
- Rain: on leaves, on open ground, on a roof when indoors; thunder rolling in the distance under storms (`thunder`
  setting).
- The hours: the dawn chorus, sparse daytime birds, crickets on warm nights, frogs by still water, cicadas on hot days
  in warm lands. No insects in winter, few birds.
- Water: a brook nearby (flowing water, or a long narrow ribbon of water above the sea), waves on the shore of open
  water. These and the frogs are heard from the direction they lie in.

| Class | What it is |
|---|---|
| `Soundscape` | The client tick: reads the land every 30 ticks, asks `Mix` for each bed's volume, starts, aims and stops the beds, rolls the thunder. `describe()` for the logs. |
| `Mix` | Pure: each bed's volume from an `AirSample` and `Surroundings` (hour and year windows, warmth, weather, habitat, muffling). Unit-tested (`MixTest`). |
| `Land`, `Surroundings` | The land read: 16 directions x 6 radii out to 34 blocks plus the columns overhead (~300 lookups), eased between reads. |
| `Bed`, `BedSound` | The beds and their gains; one looping, self-easing sound instance each (starts silent, stops itself once faded). |
| `ThunderRoll` | One distant roll from a random direction. |

Clips are synthesised by `tools/gen_sounds.py` (numpy, written with `soundfile`) into `assets/gloam/sounds`: 13 beds
and 4 thunder rolls, mono 24 kHz Vorbis q3, 1.3 MB in all. Each bed is one period of a periodic signal, so it loops
without a seam; `--check` decodes them back and checks length, levels and the seam, `--png` draws spectrograms;
`ClipsTest` decodes them with Minecraft's own decoder. Nothing is registered: the clips are played by name from
`sounds.json`, so a server needs none of it. Expanse keeps its creatures' own calls; Gloam is the bed underneath.
Vanilla's ambience (cave moods, the underwater loop, rain patter) plays on beneath. Hearth's bells: later, through a
small API.

### Far air: `dev.voidmc.gloam.far` (`FarAir`)

The air over VOID LOD's far terrain, and the clouds. Touches LOD classes only through `compat.Lod` (when `void_lod`
is loaded).

| Class | What it is |
|---|---|
| `WeatherMap` | `Air.front` over ~49 km on a 256² grid in the fronts' drifting frame, made on a worker every few seconds. |
| `CloudGrid` | Minecraft's cloud cells laid out from the fronts, in rings coarser with distance, built on a worker. |
| `CloudDeck` | The cells on the GPU, in vanilla's own face format, drawn by vanilla's cloud renderer group by group. |
| `FarRenderer` | The far mist and the rain curtains (and, over a shader pack, the far land's depth merged in first). |
| `FarLand` | The far land on 128-block cells (height, water, biome warmth and wetness) from `LodApi.addVisibleTerrainListener`. |
| `FarMist` | The mist mesh: `Air.mist` per far cell with its own climate, lying at the lowest ground nearby plus `Air.mistDepth`. |
| `FarRain` | Rain curtains: one per rainy 1 km cell of the fronts' frame, facing the camera, back to front, every frame. |
| `CloudNoise` | Four tiling noises in one 256² texture, for the rain's streaks and the mist's tongues. |

- **Clouds: Minecraft's, laid out by the weather.** Vanilla's look (12 x 12 cells, 4 high, flat-shaded boxes, the
  top brightest and the bottom darkest, `CLOUD_COLOR`, the fast/fancy option) but placed from the fronts instead of
  `clouds.png`: scattered puffs about as many as vanilla's under a clear front, bigger merged shapes in broken weather, a
  near-continuous slab with holes under an overcast; showers and storms thicker and darker (two darker tones), stacked
  into stepped towers from the lowest deck up into the highest, a storm's top spreading wider into an anvil, its
  footprint widened so a thunderhead reads kilometres off.
- **Layers fitted to the world** (`CloudGrid.Layers`, from the build height and `CLOUD_HEIGHT`): a vanilla-height world
  keeps one deck at `CLOUD_HEIGHT`; a somewhat taller one (Expanse's top 383 today) a low grey stratus layer 16 above
  it, thick over lowlands and wet fronts and broken over high ground (read from VOID LOD's far land), and the main deck
  near the top (343); a tall one (top 600 and more, Expanse's coming 639) the stratus, the main deck in the middle (393)
  and a thin high deck near the top (619), so 550-600 ranges stand between the main and the high deck, towers spanning
  208 to 619. `farCloudLift` -1 (the default) is this; 0 and up puts one deck that far above `CLOUD_HEIGHT` by hand.
  `-Dgloam.far.maxY=639` lays the clouds out as for a world that tall, to try it on a lower one.
- The cells lie in the fronts' drifting frame, so the clouds travel with their weather; out to just past VOID LOD's
  radius the rings get coarser (12, 24, 48 ... blocks), some 65-80 thousand faces in about 200-370 draws, and the clouds'
  fog (`FogRendererCloudMixin`) stretches to that reach so they thin into the sky like the far land. `CloudRendererMixin`
  has vanilla's own `CloudRenderer` draw the mesh, through the pipeline it chose this frame, so a shader pack's clouds
  program, which Iris swaps in for vanilla's, draws Gloam's clouds itself, lit by the pack. Without a pack the faces
  take the low sun's colour at dawn and dusk (`SUNRISE_SUNSET_COLOR`): warm on the sides facing the sun and on the
  undersides and tops in its half of the sky, cool blue-grey away from it.
- **Cloud shadows** on the far land: from the same cells, a 512² picture carried along with the fronts and offset along
  the sun's ray, laid on the LOD with `LodApi.setShade` (relative to the camera's light, so an overcast does not darken
  the far land against the chunks).
- **Far storms.** Rain curtains under showers and storms kilometres off, grey veils streaked and leaning, darker under
  storms, travelling with their weather.
- **Far mist.** At dawn (and over water at dusk) mist lies in the far valleys as a translucent sheet with an uneven,
  drifting top, thin at the slopes, cool white-grey away from the sun and warm toward it; ridges and hills stand out of
  it through the depth buffer.
- **The haze follows Gloam** through the attributes, and `LodApi.setHazeThickness` thickens it in humid summer
  lowlands and morning mist, thins it in winter and in the washed air after rain (`farHaze`).
- **Over a shader pack** (`farOverPack`): when the pack draws VOID LOD's far land through its Distant Horizons programs,
  VOID LOD hands over that land's depth once the pack has finished its picture (`LodApi.onPackFrame`). Gloam writes it
  into the main depth buffer in the game's perspective (`far_depth_merge`, sampling the LOD's GL texture through a
  borrowed `GlTexture`), then draws the mist and the curtains over the pack's picture, with Iris's shader overrides held
  off for those draws.

### Seasons: `dev.voidmc.gloam.season` (`Seasons`)

The year the model keeps (`Air.year`) shown on the land, near and far, with and without a pack. Nothing in the world is
changed: it is all in how the client tints and draws what is there.

| Class | What it is |
|---|---|
| `Seasons` | The client tick: the year in steps (64 a year, half a day each at eight-day seasons); at a step clears the tint caches, re-meshes the chunks around the camera (which also swaps the crowns' models) and sets VOID LOD's season table (every 10 ticks, smoothly). Gloam or `seasons` off is a step back to vanilla. |
| `SeasonColours` | Pure colour maths per step (`SeasonColoursTest`): keyframes through the year for grass, broadleaf crowns, birch, needles and leaf litter, from vanilla's colour and the biome's climate; hot, dry and tropical lands change little. |
| `SeasonResolvers` | Vanilla's grass, foliage and dry-foliage `ColorResolver`s wrapped (`BiomeColorsMixin`), so chunk meshes bake the season and every biome (vanilla, Expanse) turns in its own greens; grass turns in patches. Sodium reads the same resolvers. |
| `SeasonLeaves` | Tint sources for oak, dark oak, birch and spruce leaves (Fabric's `BlockColorRegistry`): each tree its own hue about the season's colour (a jittered crown-sized grid), birch bright yellow in autumn, spruce darker in the cold. |
| `SeasonalLeaves` | The broadleaf leaf blocks' models wrapped (Fabric's model loading API): block by block, by how bare the season has the crowns and each tree's own timing, the leaf cube, a thinning crown (twigs and small clumps of leaves) or a bare one (two crossed and two flat planes of thin twigs, mostly clear), so a winter wood is a light tracery with the ground through it; Expanse's untinted leaves swap to their other season's texture (beech copper, willow yellow; maple, aspen, larch green until autumn). Chosen as chunks are meshed (vanilla through Fabric's renderer, Sodium through its own); items and particles keep the cube. Textures and models from `tools/gen_seasons.py`. |
| `Remesh` | Marks the sections around each loaded column's surface (lowest open ground to the treetops), nearest first, over ten ticks: ~1500 sections at render distance 12. |
| `Snowline` | The precipitation look (`ClientLevelSnowlineMixin` on `ClientLevel.getPrecipitationAt`): the temperature it is decided by drops with the season's cold and faster with height in winter, so it snows on temperate hills and rains in the valleys, and the line sits higher in summer. The server's snow stays vanilla's. |
| `SeasonLod` | VOID LOD's `LodApi.Seasons` hook: LOD bakes vanilla's colours and stamps each grass and leaf face with a season class this picks (temperate, wet and warm grass, broadleaf, warm broadleaf, mixed, needles, birch, beech, willow, maples, aspen, larch); a 32 x 4 table of tints set from the year every 10 ticks (`setSeasonTints`) turns them at draw time, nothing rebuilt. Crowns take one of four variants by the same 12-block hash near and far. |

Falling leaves (motes) take the season's colour from the leaves and fall less from thinning crowns. Settings: `seasons`,
`bareTrees`, `seasonSnow`, and `seasonDays` for the year's length.

## Shader packs

With Iris and a pack on (`compat.Shaders.packInUse()`), the pack draws the sky, fog and lighting itself. What still
reaches it from Gloam: the environment attributes (packs read fog and sky colour, sky light, moon), the rain level
(`rainStrength`), particles (motes), sound, and geometry. Far geometry under a pack follows VOID LOD's approach: draw
after the pack has finished, tested against the depth the world left. Every visual change is checked with and without
a pack (Complementary Reimagined r5.9.3 in the game test's `shaderpacks`).


How the packs tested take Gloam's clouds (vanilla's cloud geometry, drawn through the pack's `gbuffers_clouds`):

| Pack | Default | Gloam's clouds |
|---|---|---|
| Complementary Reimagined r5.9.3 | its own clouds; `clouds=off` unless *Cloud Style* is Vanilla (50) | with Cloud Style: Vanilla (`CLOUD_STYLE_DEFINE=50`), lit by the pack; checked |
| BSL v10.1.8 | its own volumetric clouds (`CLOUDS=2`) | with `CLOUDS=3` (vanilla); its vanilla-cloud fog (`FOG_VANILLA_CLOUD`) fades them out past ~1 km unless set to 0 |
| Photon v1.3b | always `clouds=off`; its own volumetric and blocky clouds | never |

With their own clouds the packs still follow Gloam's weather through the rain level and the attributes.
## The bridge to VOID Welkin: `dev.voidmc.gloam.bridge` (`Bridge`)

VOID Welkin (the VOID shader pack, branch `welkin`) draws every pixel; Gloam works out the world's state and hands it
over through two channels (contract version 1; the design is the Welkin design document, section 2). M1 is built: the
uniforms below, the header and the near land tiles N0/N1. The other tiles stay zero until their milestones.

| Class | What it is |
|---|---|
| `Bridge` | The client tick (after the atmosphere and the sky): the uniforms' values, the camera's 16 m cell, the header, and the upload of what changed. Registers the texture at `CLIENT_STARTED`. Nothing is uploaded, and the worker rests, while no shader pack is on. `stats()` for the showcase's log. |
| `Contract` | Pure: the texture's layout, wrap addressing, h16 heights, the cell texels, the uniform order and ranges, the header mirror (`ContractTest`). |
| `Uniforms` | The `void_*` values (plain field reads for Iris every frame) and Iris's side in a nested class. |
| `LandTiles` | N0/N1 rolling with the camera: a worker works out the strip that came into range (256 cells) from Expanse's country sampler; strips reach the render thread with the centre they belong to (`LandTilesTest`). |
| `DataTexture` | `gloam:pack/data`, RGBA8 1024x516, one mip; uploads the used header columns and per tile the changed rows or columns (or the whole tile when many). |
| `mixin/iris/CommonUniformsMixin` | `@Inject(TAIL, require = 0)` into Iris's `CommonUniforms.addNonDynamicUniforms`, in the optional `gloam.iris.mixins.json` (plugin: Iris loaded). |

**Uniforms (M1).** `void_bridge` (int, 1), `void_version` (int, 1), `void_seed` (int), `void_year`, `void_warmth`
(float), `void_season` (int 0..3), `void_wind` (vec4: x, z, blocks/s, gust), `void_front`, `void_cloudCover`,
`void_rain` (the client's rain level, regional), `void_showers`, `void_storm`, `void_mist`, `void_mistTop` (world y),
`void_mistTime` (`Air.mistTime`: the hour's and season's part of the mist), `void_wetness` (float), `void_light` (vec4:
moon light, the star amount 0..1 (`Night.stars` / 1.5), sky-light scale, dark-nights setting), `void_nearCentre` (ivec2, the N tiles' centre cell). All are
updated per client tick. `-Dgloam.bridge.uniforms=false` (`-Pgloam.bridge.uniforms=false`) leaves them out, to test the
pack's fallback to the header.

**Texture.** Header row 0: texel 0 `VOID`; texel 1 version, flags (1 Expanse, 2 VOID LOD, 4 singleplayer, 8 uniforms
injected), 16-bit frame counter in BA; texels 2..9 the centres of tiles 0..7 (RG x, BA z, the cell's low 16 bits, to be
unwrapped against `cameraPositionInt`); texel 10 R the mask of tiles holding data; texel 11 the 32-bit mask of live
uniform slots. Rows 1..3: the uniform mirror, texel column = the uniform's place in the design's table (31 slots, from
`void_bridge` 0 to `void_ugCentre` 30): floats as 16 bits over the slot's range (row 1 the high byte, enough alone; row 2
the low byte), ints as four little-endian bytes in row 1, an ivec's component k in row 1 + k. Tiles: N0 RG ground h16
(`(hi*256+lo)/32 - 1024`; the sea's surface out at sea), B lowness, A water distance (4 blocks a step, 255 a km or
more); N1 R mist potential (`Air.mistPotential`), G temperature (-1..1), B humidity, A haze kind (high nibble) and flags
(low nibble: 1 present, 2 Expanse, 4 snow, 8 water). A cell is at `cell & 255`; valid within 127 cells of the centre.

**Welkin.** When the pack Iris has loaded is VOID Welkin (its name contains `welkin`), Gloam's ground-mist sheets go
(Welkin lays the valley mist itself). Everything else of Gloam's still draws until its milestone replaces it: the sky
layers and mist fog, the far mist and rain curtains over VOID LOD, the clouds, motes, seasons.

## Partners

- **VOID LOD** (`void_lod`): optional. `compat.Lod`, `LodApi`. Gloam is cut from `claude/lod-quality`, so LOD changes
  made here (an atmosphere hook, the ground-mist term) are small, additive, and offered back to the LOD branch.
- **Expanse** (`expanse`): optional. Expanse sets its biomes' base colours in biome JSON (natural); Gloam only changes
  them on top. Expanse is adding `ExpanseCountry.at(x, z)` (country, humidity, valley depth, water distance), which
  Gloam will read for its `Climate` once it lands.
- **Hearth** (`hearth`): optional, later. Gloam's dark nights give Hearth's light rule and stealth their weight;
  Hearth's bells carried across the land; the season for markets, crops and festivals.
- **Mettle**: no overlap planned.

## Settings

`config/gloam.json` (`GloamSettings`), `gloam.<name>` system properties override it for a session (the dev runs and
game tests pass `-Pgloam.<name>=` through). The settings screen comes later and goes Figma-first (VOID panels: flat,
rounded, no gradients).

## Verification

- `./gradlew test` for the model.
- `./gradlew runClientGameTest` (`GloamShowcase`): an Earth world with VOID LOD, photographed at the views each part
  adds (dawn valley, noon meadow, dusk by water, night under a full and a new moon, an approaching storm, the far
  cloud deck), each with Gloam off and on; again with `-Piris` and Complementary. Screenshots land in
  `build/run/clientGameTest/screenshots`.
- At most two game instances on the machine at once: hold `C:/Users/bitto/VOID/.tools/locks/game1` or `game2`
  (`mkdir`, `rmdir` when done). Heavy builds and tests go through `bash C:/Users/bitto/VOID/.tools/locks/slot.sh`.

## Later phases

- **Seasons, more:** snow lying in winter (server side, from the same year), the season as an API for Hearth and
  Expanse, branches that sway in the wind.
- **Server-side regional weather,** so crops, fire and snow follow the same fronts.
- **World details:** puddles and wet surfaces after rain, frost and dew at dawn, leaf litter in autumn, rainbows
  after showers, heat shimmer over the dunes, wind sway without a pack.
- **Hearth's bells and town sounds** carried across the fields.
- **Settings screen** in VOID panels.
