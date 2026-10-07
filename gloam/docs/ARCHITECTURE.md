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

The air over VOID LOD's far terrain. Touches LOD classes only through `compat.Lod` (when `void_lod` is loaded).

- **The haze follows Gloam** already, through the attributes. A small `LodApi` hook may add a ground-mist term to the
  LOD's height haze (`lod.vsh`) so far valleys fill with mist at dawn.
- **A cloud deck to the horizon.** Vanilla's clouds stop at the cloud render distance; Gloam draws the far cloud layer
  from the fronts, so a storm 10 km off is a dark mass on the horizon.
- **Far storms.** Rain curtains under storm cells, drawn kilometres away over the LOD land, in the same depth buffer
  (26.3 renders reversed depth; see `lod/.../render/LodRenderer.java`).
- **Far mist.** Using `LodApi.addVisibleTerrainListener` (heights and biomes of each far tile), mist banks lie in the
  far valleys while the mist is up.

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
