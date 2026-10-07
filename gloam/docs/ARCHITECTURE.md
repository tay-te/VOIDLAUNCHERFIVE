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

## The four parts

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

Particles, which shader packs draw too. Spawned around the camera from what is really there (leaf blocks overhead,
grass and flowers, water, the mist layer), moved by `Wind`, scaled by the `motes` setting and a frame budget.

- Falling leaves under trees, tinted like the leaves they fall from, more in autumn and in gusts.
- Pollen and dust motes catching the light in meadows on warm still days.
- Fireflies over grass and water at dusk in late spring and summer (real fireflies: small, warm, blinking).
- Mist wisps drifting low over the ground and water while the mist is up.
- Breath in the cold; snow drifting in the wind; spray at waterfalls.

### Soundscape: `dev.voidmc.gloam.sound` (`Soundscape`)

Layered ambient beds chosen from the air and the land around the camera, cross-faded, muffled indoors
(`AirSample.indoors`), on the ambient sound category times the `sound` setting.

- Wind, by its speed, the altitude and the exposure; through leaves in forests, over grass on open land.
- Rain: on leaves, on open ground, on a roof when indoors; thunder rolling in the distance under storms.
- The hours: the dawn chorus, daytime birds by biome, crickets and night insects, frogs by water, cicadas in warm lands.
- Water: a stream nearby, waves on a shore.
- Clips are synthesised (Python, like Expanse's `tools/gen_living.py`) into `assets/gloam/sounds`. Expanse keeps its
  creatures' own calls; Gloam is the bed underneath. Hearth's bells: later, through a small API.

### Far air: `dev.voidmc.gloam.far` (`FarAir`)

The air over VOID LOD's far terrain. Touches LOD classes only through `compat.Lod` (when `void_lod` is loaded). Drawn
by `FarRenderer` through Mojang's GPU layer (OpenGL and Vulkan) inside vanilla's main pass, after the translucent
terrain and before the weather (where vanilla draws its clouds), in the same reversed depth buffer as the chunks and
the LOD; prepared (uploads, the shadow picture) while the frame graph is built (`LevelRendererFarMixin`).

| Class | What it is |
|---|---|
| `WeatherMap` | `Air.front` over ~49 km on a 256² grid in the fronts' drifting frame, made on a worker every few seconds, uploaded as a texture. |
| `CloudNoise` | Four tiling fractal noises in one 256² texture: the heaps, their edges, the rain's streaks, the mist's tongues. |
| `FarRenderer` | The pipelines and the draws: the cloud deck, the mist mesh, the rain curtains, the cloud shadows' picture; GPU timing. |
| `FarLand` | The far land on 128-block cells (height, water, biome warmth and wetness) from `LodApi.addVisibleTerrainListener`. |
| `FarMist` | The mist mesh: `Air.mist` per far cell with its own climate, lying at the lowest ground nearby plus `Air.mistDepth`. |
| `FarRain` | Rain curtains: one per rainy 1 km cell of the fronts' frame, facing the camera, back to front, every frame. |

- **The cloud deck** replaces vanilla's clouds (`CloudRendererMixin`) and keeps the cloud option (off; fast: one soft
  layer; fancy: heaps with depth), `CLOUD_HEIGHT` as its base and `CLOUD_COLOR` as its sunlit colour. One triangle over
  the screen; each pixel marches its ray through the layer (10-56 steps, jittered), the heaps shaped from the fronts'
  cover and the noise: clear sky, broken heaps, a flat overcast deck, storms dark underneath and towering to ~560 blocks
  over the base. It writes the depth of the first cloud it meets, so land in front covers it. Out to the LOD's radius,
  paling into the horizon's colour.
- **Cloud shadows** on the far land: a 1024² picture of the sunlight under the clouds (relative to the light at the
  camera, so an overcast does not darken the far land against the chunks), laid on the LOD with `LodApi.setShade`.
- **Far storms.** Rain curtains under showers and storms kilometres off, grey veils streaked and leaning, darker under
  storms, travelling with their weather.
- **Far mist.** At dawn (and over water at dusk) mist lies in the far valleys as a translucent sheet; ridges and hills
  stand out of it through the depth buffer, and its edges go soft where the mist over the ground runs out.
- **The haze follows Gloam** through the attributes, and `LodApi.setHazeThickness` thickens it in humid summer
  lowlands and morning mist, thins it in winter and in the washed air after rain (`farHaze`).
- With a shader pack on, the pack draws its own sky and clouds: the far air stays out (vanilla clouds are left to the
  pack) and reaches it only through the attributes and the rain level.

## Shader packs

With Iris and a pack on (`compat.Shaders.packInUse()`), the pack draws the sky, fog and lighting itself. What still
reaches it from Gloam: the environment attributes (packs read fog and sky colour, sky light, moon), the rain level
(`rainStrength`), particles (motes), sound, and geometry. Far geometry under a pack follows VOID LOD's approach: draw
after the pack has finished, tested against the depth the world left. Every visual change is checked with and without
a pack (Complementary Reimagined r5.9.3 in the game test's `shaderpacks`).

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

- **Seasons you can see:** grass and leaf colours through the year (vanilla colour resolvers and the LOD's colours),
  autumn leaves, bare winter branches, a lower snowline; the season as an API for Hearth and Expanse.
- **Server-side regional weather,** so crops, fire and snow follow the same fronts.
- **World details:** puddles and wet surfaces after rain, frost and dew at dawn, leaf litter in autumn, rainbows
  after showers, heat shimmer over the dunes, wind sway without a pack.
- **Hearth's bells and town sounds** carried across the fields.
- **Settings screen** in VOID panels.
