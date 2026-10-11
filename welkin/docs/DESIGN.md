# VOID Welkin and the new Gloam: design

Status: accepted, October 2026. Built from three read-only research passes: Iris 1.11.6+26.3 internals, a full inventory of Gloam, and a survey of the installed shader packs. Milestone M0 (the pack skeleton) is built; section 7 lists what has been decided since the proposal.

## 0. The decision in one paragraph

All world visuals move into VOID Welkin, a new shader pack for Iris, written from scratch. Gloam stops drawing anything. Instead it works out the world's state (seasons, wind, weather fronts, mist, haze, light) and hands it to the pack through a small bridge: one data texture, which needs no Iris mixin, and a set of `void_*` uniforms added by one small mixin. It keeps the whole soundscape and gains underground sound beds. Expanse is unchanged as the source of land data. LOD keeps drawing far terrain through the pack's Distant Horizons programs, and gets the bridge data with no changes. The look stays Minecraft: flat steps, a 16-pixel grid, dither and voxel clouds, with no soft volumetrics.

---

## 1. Roles

| Part | Owns | Does not do |
|---|---|---|
| **Expanse** (main) | Land data: country, humidity, temperature, lowness, water and coast distance, altitude, haze hint (`ExpanseCountry`), plus caves (`ExpanseUnderground`). | Anything at render time. |
| **Gloam** (branch `gloam`) | The world-state model: `Air`, `Atmosphere`, `WeatherMap`, `FarLand`, `Haze`, `Night`. Also the bridge (data texture and uniforms), regional rain and lightning gating, the gameplay light level, seasonal block colours and bare crowns, motes as particles, and all sound. | Sky, fog, mist, clouds, rain curtains or far air. No core shaders and no render mixins. |
| **VOID Welkin** (new, branch `welkin`) | Every pixel: sky, sun, moon, stars, light ramp, shadows, haze, valley mist, underground fog, water, voxel clouds, far rain curtains, far-land shading, and how particles are lit. All palettes and colours. | Any world logic. It only reads quantities and IDs. |
| **VOID LOD** (branch `claude/lod-next`) | Far terrain to 128 km, drawn through the pack's `dh_terrain` and `dh_water` programs. Keeps its seasonal tint table. | No bridge work: `PackLodProgram` already binds the pack's custom uniforms and samplers. |

### What leaves Gloam

| Goes | Why |
|---|---|
| Sky colour layers (`SKY_COLOR`, `FOG_COLOR`, `CLOUD_COLOR`, `SUNRISE_SUNSET_COLOR`, `SKY_LIGHT_COLOR`, `STAR_BRIGHTNESS`, the colour part of fog distance) | The pack draws the sky. Gloam sends the inputs: golden, blue, vivid, sunset step, moon and cover. |
| `SkyBands` and the banded sky shaders | Becomes the pack's stepped sky table. |
| Ground mist (`MistMote`, `SoftMist`, veil sprites, `ground_mist.*`, the soft-mist mixins, the depth copy) | The pack's composite pass does the mist slab with real depth. This removes Gloam's riskiest code. |
| Clouds (`CloudGrid`, `CloudDeck`, `CloudNoise`, cloud mixins, the cloud-shadow picture) | The pack ray-marches voxel clouds from the weather data. `CloudGrid` becomes the GLSL reference. It stays until the pack's clouds pass review (milestone M3). |
| Far air (`FarRenderer`, `FarMist` mesh, `FarRain`, `far_depth_merge`, `BorrowedDepth`, `Shaders.withoutPackShaders`, `LevelRendererFarMixin`, the `ImmediateState.bypass` hack) | This path only existed to paint over a pack. It moves into the pack's DH and composite programs. |
| LOD calls `setHazeThickness`, `setShade`, `onPackFrame` | Replaced by uniforms the pack reads. |
| Settings `sky`, `haze`, `pixelSky`, `far*` | Become pack options. |

In total that is 6 render mixins, about 15 core shaders and the whole render side of `far/`.

### What stays in Gloam, and why

| Stays | Why |
|---|---|
| `Air`, `Atmosphere`, `Climate`, `WeatherMap`, `FarLand`, `Haze`, `Night` maths | These are the data the pack reads. Pure and unit-tested. |
| `LevelWeatherMixin` (regional rain), `ClientLevelFlashMixin` (lightning only under storms), the snowline | Rain level drives vanilla rain particles, rain sound and the pack's `rainStrength`. They are cheap and data-like. |
| `SKY_LIGHT_FACTOR` and `AMBIENT_LIGHT_COLOR` scale (dark nights) | The darkness matters for gameplay (Hearth stealth, the light rule). It has to work with any pack or none. The pack also reads it as `void_skyLight`. |
| Seasonal grass, foliage and leaf tints, bare and thinning crowns, `SeasonLod` | A pack cannot swap block models. Baking the tints keeps near land, LOD and falling leaves in step. |
| Motes: leaves, pollen, fireflies, breath, snow drift, powder, waterfall spray | They need real block probes, which a pack cannot do. They become plain particles with pixel sprites, and the pack lights them. |
| The soundscape (13 beds, `ThunderRoll`) | Sound is Gloam's other job. New beds come later (section 6, M5). |

**Still open (question 1 in section 6).** Without Welkin, everything visual is vanilla except motes, seasonal colours, bare crowns, regional rain and dark nights. This document assumes that is acceptable. The other choice is to keep a small fog and sky-colour attribute fallback in Gloam. That costs little, but it keeps two looks to maintain.

---

## 2. The bridge contract (version 1)

Two channels:
- **Uniforms** for scalar state, added by one Iris mixin. They give the pack `smooth()` and shaders.properties expressions.
- **One RGBA8 data texture** for maps, using pack syntax Iris already supports. No mixin.

The texture's header rows mirror the main scalars, so the pack still works if the mixin ever fails.

### 2.1 How it is wired

**Uniforms**
- One `@Inject(at = TAIL, require = 0)` into the static `net.irisshaders.iris.uniforms.CommonUniforms.addNonDynamicUniforms(UniformHolder, IdMap, PackDirectives, FrameUpdateNotifier)`.
- `IrisRenderingPipeline` builds the pack's custom-uniform set from this once per pipeline. Every program gets it, including composite, final, shadowcomp, computes, Iris's DH programs and LOD's `PackLodProgram`.
- Gloam's mixin config is optional (`required:false`), with a plugin that checks **only** that Iris is loaded, not for DH. It uses `remap = false` and keeps Iris classes in a nested class, as `gloam/compat/Shaders.Iris` already does.
- If the injection does not apply, Gloam logs one warning and the pack reads `void_bridge == 0`.

**Texture**
- At `ClientLifecycleEvents.CLIENT_STARTED`, Gloam calls `TextureManager.register(Identifier.fromNamespaceAndPath("gloam", "pack/data"), tex)`. This must happen before any pipeline is built.
- `tex` is an `AbstractTexture` over a `GpuTexture` with `GpuFormat.RGBA8_UNORM`, 1024×516, and one mip level.
- Uploads use `CommandEncoder.writeToTexture` on the render thread, for changed strips only. Gloam skips uploads while `IrisApi.isShaderPackInUse()` is false.

**Pack declaration**
```properties
# shaders.properties
customTexture.void_data = gloam:pack/data
#ifdef VOID_GLOAM
uniform.float.welkin_mistS = smooth(void_mist, 4, 4)
uniform.float.welkin_rainS = smooth(void_rain, 6, 6)
#endif
```
```glsl
uniform sampler2D void_data;          // read only with texelFetch
uniform int  void_bridge;             // 0 if Gloam or the mixin is missing
```

**Sampling rules**
- Iris binds identifier textures as `TEXTURE_2D` with `MIPPED_NEAREST_REPEAT`, overriding the texture's own settings. The pack therefore reads only with `texelFetch` and does any smoothing itself (4 fetches and a bilinear blend).
- One texture means one sampler slot per program.
- **Reserved names:** Gloam owns `void_*` uniform names. If the pack declares its own `uniform.*` with a `void_` name, Iris drops it, so the pack's derived uniforms are named `welkin_*` (as above). They are declared only under `#ifdef VOID_GLOAM`, so a game without Gloam logs no unresolved uniforms.
- **Optional define:** `VOID_GLOAM` through `StandardMacros.createStandardEnvironmentDefines` at `RETURN`, the same as LOD's `DISTANT_HORIZONS`. It is skipped if the key is already present. It is only needed if the pack wants shaders.properties `#ifdef` switches.

### 2.2 Uniforms

Gloam computes values per client tick (20 Hz) into a snapshot. Iris calls the suppliers every frame on the render thread, so each one is a plain field read. Values marked "frame" are interpolated by Gloam between ticks.

| Name | Type | Range / unit | Meaning | Rate |
|---|---|---|---|---|
| `void_bridge` | int | 0 or 1 | Gloam is live. Undeclared means 0. | once |
| `void_version` | int | 1 | Contract version. The pack refuses to read a newer major version. | once |
| `void_seed` | int | 31 bits | Hash of the `BiomeManager` zoom seed, for per-cell hashes such as cloud cells. | once per world |
| `void_year` | float | 0..1 | Position in the year (`YEAR_START` 0.15) | tick |
| `void_warmth` | float | -1..1 | cos(2π(year − 0.375)) | tick |
| `void_season` | int | 0..3 | spring, summer, autumn, winter | tick |
| `void_seasonState` | vec4 | 0..1, 0..1, 0..1, y | bare, turned, cold, snowline height | tick |
| `void_wind` | vec4 | unit x, unit z, blocks/s, 0..1 | Direction, speed, gust | frame |
| `void_front` | float | 0..1 | Front value at the camera | tick |
| `void_cloudCover` | float | 0..1 | Cloud cover at the camera | tick |
| `void_rain` | float | 0..1 | Regional rain at the camera, including the drizzle floor. Matches the vanilla rain level Gloam drives. | tick |
| `void_showers` | float | 0..0.6 | Shower share | tick |
| `void_storm` | float | 0..1 | Storm strength | tick |
| `void_maxStorm` | float | 0..1 | Storm threshold for the season (towers, anvils) | tick |
| `void_mist` | float | 0..1 | Mist at the camera | tick |
| `void_mistTop` | float | world y | Top of the local mist slab (ground + 4 + 26·mist) | tick |
| `void_mistTime` | float | 0..1 | Time-of-day and season part of mist: peaks at dawn, a thin veil after sunset. Multiplies the per-texel mist potential. | tick |
| `void_wetness` | float | 0..1 | Ground wetness: soaks in over about 1200 ticks, dries over most of a day | tick |
| `void_haze` | vec4 | log haze, 0..1, 0..1, 0.6..2 | haziness, dust share, curtain, far haze thickness | tick |
| `void_hazeKind` | int | 0..15 | Local haze kind (table 2.4) | tick |
| `void_climate` | vec4 | -1..1, 0..1, 0..1, 0..1 | temperature, downfall, lowness, water, eased at 0.05 per tick | tick |
| `void_shelter` | vec4 | 0..1 each | indoors, canopy, roof, buried (from the sound `Land` survey) | tick |
| `void_light` | vec4 | 0..1 each | moon light, star factor, sky light scale (gameplay), dark-nights setting | tick |
| `void_sky` | vec4 | 0..1, 0..1, 0..1, 0..4 | golden, blue, vivid (per day), sunset step | tick |
| `void_cloudLayers` | vec4 | world y | low, main, high, tower top (follows world height: y 383 or 600+) | on world load or setting change |
| `void_underground` | vec4 | 0..1, 0..1, id, id | depth factor, openness at the eye, realm, water kind | tick |
| `void_cave` | vec4 | 0..1 each | glowworms, bats, drip, great-river nearness | tick |
| `void_nearCentre` | ivec2 | cells | Centre cell of the near tiles (16 m) | on change |
| `void_farCentre` | ivec2 | cells | Centre cell of the far tiles (1 km) | on change |
| `void_weatherShift` | vec4 | blocks | Weather-frame shift for W0 and W1, already wrapped (mod 32768 and mod 262144) so precision holds | frame |
| `void_ugCentre` | ivec3 | blocks | Centre of the underground tile (x, eye y, z) | on change |

Packs already get time, sun angle, `rainStrength`, `wetness`, `cameraPositionInt/Fract` and the lightning bolt from Iris, so those are not duplicated.

### 2.3 The data texture

One RGBA8 texture, 1024×516: four header rows, then a 4×2 grid of 256² tiles. Tile `t` starts at `x = (t % 4) · 256`, `y = 4 + (t / 4) · 256`. In total about 2.1 MB of GPU memory.

| Tile | Name | Cell size | Frame | Coverage | Purpose |
|---|---|---|---|---|---|
| T0 | N0 near land A | 16 m | world | 4 km (±2 km) | Ground and lowness for mist and haze |
| T1 | N1 near land B | 16 m | world | 4 km | Mist potential, climate, haze kind |
| T2 | F0 far land A | 1 km | world | 256 km (±128 km, the LOD reach) | The same for far terrain |
| T3 | F1 far land B | 1 km | world | 256 km | The same |
| T4 | W0 near weather | 128 m | drifting front frame | 32 km | Clouds, rain and storms near |
| T5 | W1 far weather | 1 km | drifting front frame | 256 km | Clouds and curtains to the horizon |
| T6 | U underground | 4 m | world, round eye height | 128² used = 512 m | Cave fog and life |
| T7 | (reserved) | | | | Future: a sun-horizon map, or a second underground slab |

**Channel layout** (all unorm8 unless noted; "h16" means a height split across two bytes: `y = (hi·256 + lo)/32 − 1024`, so −1024..1024 to 1/32 block)

| Tile | R | G | B | A |
|---|---|---|---|---|
| N0, F0 | ground height hi | ground height lo | lowness (Expanse, smoothstep 0..24 blocks) | N0: water distance (4 m steps, 255 = 1 km or more); F0: water fraction |
| N1, F1 | mist potential (place part of `Air.mist`: lowness, water, wetness, climate) | temperature (-1..1) | humidity / downfall | high nibble: haze kind; low nibble: flags (bit 0 data present, bit 1 Expanse (not estimated), bit 2 snow ground, bit 3 water surface) |
| W0, W1 | front 0..1 | cloud cover (after the humidity and rain-shadow bias) | rain incl. showers | storm |
| U | openness (space) | floor height relative to the eye (signed, 1 block per step) | ceiling height relative to the eye (signed) | high nibble: water kind; low nibble: realm or life flags (glowworms, drip, river) |

**Header rows (y 0..3)**
- Texel (0,0) holds the bytes `V O I D`. Texel (1,0) holds the contract version, flags (Expanse present, LOD present, singleplayer) and a frame counter.
- Texels (2..63, 0) mirror the tile centres as 16-bit values.
- Rows 1..3 mirror every uniform above, 8-bit quantised in the same order. This is the fallback when the mixin fails.

**Addressing (wrap-around)**
```glsl
ivec2 voidTexel(int tile, ivec2 cell) {
  return ivec2((tile & 3) * 256, 4 + (tile >> 2) * 256) + (cell & 255);
}
// near: cell = ivec2(floor(worldXZ / 16.0));
// weather: cell = ivec2(floor((worldXZ + void_weatherShift.xy) / 128.0));
```
- Each cell sits at `world cell mod 256`. A cell is valid when its Chebyshev distance from the tile's centre is under 128.
- The pack fades from cell 112 to 127: near → far → uniforms.
- Camera-relative maths uses `cameraPositionInt` so far-away coordinates keep their precision.

**Update policy**

| Tile | When | What is written | Cost |
|---|---|---|---|
| N0/N1 | Camera crosses a 16 m cell | One new row or column per tile (256 texels, 1 KB). A rolling refresh of 8 rows a second picks up wetness and mist changes. | about 2 KB per step |
| F0/F1 | Camera crosses 1 km; filled as `FarLand` or Expanse samples arrive | Strips plus the cells that just arrived | rare |
| W0/W1 | `WeatherMap` rebuild (every 200 ticks, on a worker) | The whole tile, 256 KB, spread over 4 frames. Drift between rebuilds is only the `void_weatherShift` uniform, plus new edge strips as the frame slides. | about 64 KB per frame for 4 frames |
| U | Every 10 ticks while `void_underground.x > 0`, re-centred after 16 m of movement | The 128² area | 64 KB |
| Header | Every tick | Rows 0..3 | 16 KB |

**Sources**
- **Singleplayer:** the Expanse samplers. Gloam already calls `ExpanseCountry.sampler(level).at(x, z)`. `ExpanseUnderground.sampler(level)` is new to Gloam.
- **Dedicated server:** Expanse samplers are null, so N tiles come from heightmaps and biomes inside loaded chunks (flag bit 1 clear). F tiles come from `FarLand` when LOD is loaded. Cells with no source have flag bit 0 clear.
- A later milestone (M8) adds a small server-to-client packet so the server's Expanse data reaches multiplayer clients.

### 2.4 Haze kinds (stable indices)

| Index | 0 | 1 | 2 | 3 | 4 | 5 | 6..15 |
|---|---|---|---|---|---|---|---|
| Kind | none / vanilla | temperate | humid_lowland | misty_lakes | forest_damp | dusty | clear_alpine, then reserved |

These come from the Expanse country `haze` hint, or from Gloam's biome table when Expanse is absent. The pack owns the colour for each index.

### 2.5 When the bridge is missing (the pack must always run)

| Situation | How the pack sees it | What it does |
|---|---|---|
| Gloam not installed | `void_bridge == 0`, texel (0,0) is not `VOID` (Iris binds the missing texture) | Built-in defaults: Iris `rainStrength` and `wetness`, one temperate haze, no valley mist, clouds from its own hash at vanilla cloud height, no underground fog colouring beyond light level. It checks the magic **before any other fetch**, because the missing texture is only 16×16. |
| Gloam installed, but the mixin did not apply (Iris update) | `void_bridge == 0`, magic present | Reads the 8-bit header mirror. Everything works, just coarser. |
| Contract version newer than the pack | `void_version` > supported | Treated as "Gloam not installed". |
| Gloam with another pack (Complementary etc.) | The texture and uniforms are ignored | That pack looks as it always does; regional rain, dark nights, motes and seasons still apply. |
| Welkin with LOD absent | `DISTANT_HORIZONS` undefined | Far tiles are only used for haze, which fades by the vanilla render distance. |

---

## 3. The pack

### 3.1 Foundation: our own pack

| Pack | Licence facts (paraphrased, clause numbers from the shipped files) | Usable for VOID? |
|---|---|---|
| Complementary Reimagined r5.9.3 | A modified version may only be published with credit and a link, Complementary's unchanged licence file and no other terms (1.3a,b,e). The name must differ by at least 3 letters from "Complementary" (1.3c), and the daytime overworld must look clearly different (1.3d). An unmodified copy may only be bundled through Modrinth or CurseForge, with no changes of any kind (1.2). | Only as a fork that stays under their licence forever. Its look is soft. |
| Photon v1.3b | Modified versions may be shared, but not on creator-paying platforms (it names Modrinth and CurseForge) without written permission, and copies may not be sold. | Risky while the launcher plans cosmetics and checkout. It also forces `clouds=off`. |
| Bliss v2.1.2 | Edits must use Chocapic13's set naming format and his rules; anything else needs written permission. | No: forced naming, soft volumetrics. |
| BSL v10.1.8 | The zip ships no licence, so all rights are reserved by default. | No, unless Capt Tatsu gives written permission. |

**Choice:** a lean pack of our own, about 3–5k lines of GLSL. The others are reading material only, and no code is copied from them. MIT snippets may be taken from their original sources with credit. The licence is VOID's own (section 7): free to use and to bundle unmodified, source readable, no redistribution of modified versions without permission. The skeleton is proven: LOD's `LodTestPack` already runs terrain, composite and final through LOD's DH path.

**Where it lives:** `welkin/` in the repo, on branch `welkin` with its worktree at `.claude/worktrees/welkin`. `welkin/tools/pack.sh` zips it into a game-test run folder's `shaderpacks/` as `Welkin.zip` and selects it.

### 3.2 Look rules (from STYLE.md and the user's notes)

- It must still feel like Minecraft. It adds texture; it is stylized, not realistic, with no fuzz.
- Light, fog, mist and sky use flat colour steps (3–5 per ramp), not smooth gradients.
- Transitions use a 4×4 Bayer dither. Effects that cover the world (mist, haze edges, cloud rims) snap to a 16-pixel world grid.
- Clouds are voxel cells (12×12×4 near, doubling with distance), opaque, with dithered rims. No ray-marched soft volumes.
- No TAA (it smears pixel art), no bloom haze, no SSAO or SSR on far pixels. Everything renders at full resolution.
- Surface colours stay natural and real-world, with no glowing fantasy tints. Underground may be fantastical (glowworm light, coloured cave water).
- Each palette is small and lives in a pack file, keyed by haze kind, season and time.

### 3.3 Files and programs

**Files**
- `shaders.properties`: screens; profiles LOW, MEDIUM, HIGH; `customTexture.void_data`; `smooth()` uniforms; blend modes (including `blend.dh_water`); `dhShadow.enabled=false`; `size.buffer.colortex5 = 64 32`; the smallest possible `iris.features.required`.
- `clouds=fancy` (vanilla's clouds on; Iris rejects the word `on`) until M3, then `clouds=off`, when the pack draws its own clouds.
- `block.properties` covers vanilla, `expanse:*` and Gloam's seasonal blocks. `item.properties` and `entity.properties` are also needed.
- `dimension.properties` (`world0=*`, `world-1`, `world1`), thin `world0/`, `world-1/`, `world1/` wrappers that `#include "/program/*.glsl"`, `lang/en_US.lang`, and `#version 330 compatibility`.

**Programs**

| Pass | Programs | Job and bridge use |
|---|---|---|
| prepare | `prepare` | 64×32 stepped sky table from the sun angle, `void_sky`, `void_light`, `void_cloudCover`, `void_hazeKind` |
| shadow | `shadow` | Near chunks only (about 128–160 blocks, 1024–2048²). No LOD. Off on LOW. |
| opaque | `gbuffers_terrain`, `entities`, `block`, `hand`, `basic`, `textured`, `textured_lit`, `skybasic`, `skytextured`, `spidereyes`, `armor_glint`, `beaconbeam` | Albedo, octahedral normal and material, and lightmap into 3 targets. Leaf and grass sway from `void_wind`. Pixel-art sun, moon and stars (`void_light.y`). Motes come through `textured` and `textured_lit`. |
| far land | `dh_terrain` | The same layout. Material from `dhMaterialId`. A hash-based 16-pixel texture on the world grid that coarsens with distance. No `discard`, no texture fetch except `void_data`. |
| deferred | `deferred` | Lights opaques using `depthtex0` where the chunks drew and `dhDepthTex0` elsewhere (section 7). Stepped light ramp scaled by `void_light.z`, so dark nights match gameplay. Near shadow map. Cloud shadows from W0/W1 cover along the sun direction. Underground ambient from U openness. |
| translucent | `gbuffers_water`, `dh_water` | Forward shading with pixel ripples (wind-driven). Far water reflects the sky table; no SSR past about 2 km. Wetness darkens shores. |
| clouds | composite pass (from M3) | Voxel DDA through the cloud decks (`void_cloudLayers`) over W0/W1 cover. Cells are on or off by a `void_seed` hash against cover. Faces are lit in steps from the sun side. |
| atmosphere | `composite` | Height haze with an exact formula, taking 2–4 samples of N/F tiles along the view ray. Valley mist slab, underground fog, water fog. All stepped and dithered. |
| far weather | `composite1` | Rain curtains under rainy W1 cells, as stepped 2.5D sheets between cloud base and F0 ground. Far valley mist over F tiles. |
| final | `final` | Palette steps, dither, light tone curve |

**LOD and DH**
- LOD's `StandardMacrosMixin` defines `DISTANT_HORIZONS`. Far material IDs come from `DH_BLOCK_*` (LEAVES 1 … ILLUMINATED 15).
- Depth: Iris stores reversed depth and patches reads back to the usual convention. LOD clears to 0 and tests with `GEQUAL`. The pack checks this on the first test with a depth debug view.
- `PackLodProgram` calls `customUniforms.assignTo` and `addGbufferOrShadowSamplers` inside `DHCompat.<init>`, before `optimise()`, so `void_*` and `void_data` reach `dh_terrain` and `dh_water`. LOD must keep building its programs there; a lazily built program would lose uniforms used only by it.
- LOD's auto-tune counts half of the pack's draw time, so heavy `dh_*` programs shrink the LOD radius. Keep them trivial.

### 3.4 How each feature uses the data

| Feature | Inputs | How |
|---|---|---|
| **Valley mist at dawn** | N0 ground and lowness, N1 mist potential, `void_mistTime`, F tiles beyond 2 km | mist = potential × `void_mistTime`. The slab top is the lowest nearby ground + 1 + (4 + 32·mist)·pool and its density mist·pool², where pool comes from the land's relief (N0 lowness) and nearness to water, so mist pools in hollows and along water and high flat land gets none. Composite intersects the view ray with the slab exactly (2–4 tile samples, no marching). Optical depth is cut into 4 steps and Bayer-dithered. The sun-side tint comes from the sky table (the horizon colour until M2). Sunrise is thick, midday is gone, after sunset is a thin veil. |
| **Cloud cover from humidity and rain shadow** | W0/W1 cover, `void_cloudLayers`, `void_storm`, `void_maxStorm`, `void_seed` | Gloam bakes the bias into W: the front field (4-octave fbm over 3 km cells, drifting east at 6 blocks/s) times Expanse humidity, reduced downwind of high ground (new: sample altitude upwind along `void_wind`). The pack only thresholds cover per cell, so the CPU and pack agree, and so do rain and sound. Storm cells grow towers to the tower-top height. |
| **Haze colour by country** | N1/F1 haze kind, `void_haze`, `void_hazeKind` | Each kind picks a small palette: humid_lowland pale blue-grey, misty_lakes cool white, forest_damp green-grey, dusty warm ochre, temperate neutral, clear_alpine thin and blue. Strength comes from `void_haze.x` (thinned at midday), `void_haze.w` for far land, and the dust share. Along the view ray, kinds are blended through the 2–4 samples. |
| **Seasons** | `void_year`, `void_warmth`, `void_seasonState`, block tints already baked by Gloam | Blocks arrive already tinted, so the pack adds touches only: frost sparkle steps when cold, low winter sun palette, autumn haze warmth, snowline-aware wet or snow-covered shading. |
| **Underground fog by openness** | U openness, floor and ceiling, water kind, `void_underground`, `void_cave` | Fog reach follows openness: tight in tunnels, long in great caverns and the underground seas. Mist rises over great rivers. Glowworm caves get a stepped teal glow. Dripping caves get damp, darker walls. Fog thickness is stepped; colours stay fantastical but flat. |
| **Rain and wet ground** | `void_rain`, `void_showers`, `void_wetness`, W1 rain | Near rain stays vanilla particles. Far rain becomes curtains in `composite1`. Wet ground darkens with `void_wetness`, and puddle highlights appear in steps. |
| **Night** | `void_light` | Moonlit or clouded sky colour, star count, and a light ramp that matches the gameplay light factor. |
| **Lightning** | Iris lightning uniforms, `void_storm` | A flash in steps; the timing gate stays in Gloam. |

### 3.5 Performance budget (1080p, mid-range GPU, MEDIUM)

| Pass | Budget |
|---|---|
| Near gbuffers | about 0.3 ms over vanilla |
| `dh_terrain` | 1.0 ms or less |
| `dh_water` | 0.3 ms or less |
| Shadow map | 0.8–1.2 ms (off on LOW) |
| Deferred | about 0.4 ms |
| Atmosphere composite | about 0.3 ms |
| Voxel clouds (M3) | 0.6 ms or less |
| Far weather composite | about 0.2 ms |
| Sky table and final | about 0.2 ms |
| **Total** | **about 4 ms MEDIUM, about 2 ms LOW** |

Cost must grow with screen resolution, not distance:
- Exact formulas and a few fetches, not marching.
- No LOD in the shadow map.
- Cloud DDA stays bounded: marching only inside each deck's thin slab, cells doubling with distance, and a step cap.
- Small buffer formats: RGBA8, RG16 for normals, R11G11B10F for the lit scene.

---

## 4. Risks

| Risk | Likelihood | How it is contained |
|---|---|---|
| **Iris update breaks the uniform mixin** (internal static method) | Medium | `require = 0` and a logged warning. The pack falls back to the header mirror. Iris is pinned to 1.11.x in Gloam's `fabric.mod.json`. A game test checks `void_bridge` on each Iris bump. |
| Identifier texture registered late | Low | Register at `CLIENT_STARTED`. A late register would close the placeholder anyway. |
| Pack reads the missing texture out of range | Low | The magic check comes first. All reads go through one `voidTexel` function behind the check. |
| Uniform pruned from a lazily built LOD program | Low | LOD keeps building pack programs in `DHCompat.<init>`, written down in LOD's README. |
| Custom texture not bound in `dh_terrain` | Low, untested | It is the first check in M1. If it fails, a one-line LOD fix in `PackLodProgram`. |
| GPU cost of voxel clouds to 128 km | Medium | Bounded DDA and distance-doubled cells. The cloud pass has its own profile setting. Gloam's `CloudGrid` mesh stays until the pack's clouds meet the 0.6 ms budget. |
| `dh_*` cost shrinks the LOD radius | Medium | Trivial far programs with budgets measured in every game test. |
| Look drifts toward realism or fuzz | Medium | STYLE.md rules in the pack's README. Every milestone is reviewed against screenshots by the user. |
| Multiplayer has no Expanse data | Certain today | Fallback flags in the tiles. M8 adds a server packet. |
| Licence | Low with our own pack | No copied code. Only MIT snippets from their original sources, with credit. The pack is under VOID's own terms (section 7). |
| Shared working tree | Ongoing | Pack work is in its own worktree. One game test at a time, using the mkdir lock and `lod-gametest.sh`. No stash, checkout or reset in VOID's checkout. |
| Two looks (Gloam before and after) during the move | Temporary | Gloam's visual code is removed only after the pack replaces each feature (M7). Until then Gloam turns its own visuals off while Welkin is active. |

---

## 5. Thinking levels for subagents

Subagent effort is capped at high: nothing runs at xhigh or max.

| Work | Level |
|---|---|
| Bridge mixin, texture class, contract encoding and wrap addressing | high |
| Pack skeleton, deferred light, DH integration, valley mist, sky, haze | high |
| Voxel cloud DDA | high |
| Underground data and fog; far curtains | high |
| Gloam clean-up (deleting render code, settings migration) | medium |
| `block.properties`, language file, profiles, option screens | low to medium |
| Screenshot review of game-test output against STYLE.md | medium |

---

## 6. Milestones

Each one ends with game-test screenshots taken with Welkin (and LOD's vista shots where far land matters), sent to the user. Screenshots are also taken with Complementary to confirm third-party packs still work. One game test runs at a time under the shared lock.

| # | Milestone | Who | Test |
|---|---|---|---|
| **M0** | **Pack skeleton:** gbuffers, `dh_terrain`, `dh_water`, deferred and final; flat stepped light; a depth debug view | Pack (high) | Vista shot with LOD at 32 and 128 km: no seam at the LOD join, and the depth convention confirmed in the debug view |
| **M1** | **First slice, the bridge end to end:** <br>Gloam registers `gloam:pack/data` with the header and N0/N1 (Expanse sampler, wrap strips), and adds the mixin with `void_bridge`, `void_version` and `void_mistTime`. <br>The pack draws valley mist in `composite` from N0/N1 and `void_mistTime`, with a debug view that shows the tiles on screen. | Gloam (high), Pack (high); Expanse: nothing | Same valley at 05:30, 08:00 and 12:00: mist pools at dawn and is gone by noon. Debug view shows the tile moving with the camera. Mist visible in `dh_terrain` hollows (proves the texture reaches LOD). Run without Gloam: the pack still runs, no mist. Run with the mixin disabled: header fallback still drives the mist. Upload cost logged per frame. |
| M2 | Sky table, haze by country, night, regional rain look, wet ground; all `void_*` uniforms | Pack (high), Gloam (medium) | One shot per haze kind at the same hour; dusk and night in clear and overcast; dark-night brightness matches the gameplay light |
| M3 | W0/W1 weather tiles with the rain-shadow bias; voxel clouds in the pack; cloud shadows; then `clouds=off` and `CloudGrid` removed | Gloam (high), Pack (high) | Clouds match where the soundscape and particles rain; storm towers; cloud GPU time under 0.6 ms; side-by-side with the old mesh before removal |
| M4 | F0/F1 tiles (Expanse and `FarLand`); far mist and rain curtains in `composite1`; far haze via `void_haze.w`; Gloam's `far/` render side and LOD calls (`setHazeThickness`, `setShade`, `onPackFrame`) removed | Gloam (high), Pack (high), LOD: review only | Vista shots at dawn and in rain to 128 km; LOD radius not reduced by the pack's cost |
| M5 | U tile from `ExpanseUnderground`; the pack's underground fog by openness; new Gloam sound beds (drip, bats, great-river roar, underground sea) | Gloam (high), Pack (high); Expanse (low) only if a cheaper batch sampling call is wanted | Shots in a tunnel, a great cavern, a glowworm cave and by a great river; sound checked by the Mix unit tests |
| M6 | Water, shadows, seasons touches, motes lit well, `block.properties` for `expanse:*`, profiles and option screens, language file | Pack (medium to high) | Four seasons from the same spot; LOW, MEDIUM and HIGH timings |
| M7 | Gloam clean-up: delete the 6 render mixins, about 15 core shaders, `SkyBands`, `SoftMist`, colour layers and the old settings; update `gloam/docs/ARCHITECTURE.md` to the new contract | Gloam (medium) | Full Gloam test suite; a no-pack shot showing vanilla visuals plus motes, seasons and dark nights |
| M8 | Multiplayer land data: the server sends Expanse tiles in a small packet | Gloam (high), Expanse (medium) | Dedicated-server client shows the same mist as singleplayer |

### Open questions for the user

1. **Still open.** Without Welkin installed (no shader pack, or another pack), should the game simply look like vanilla Minecraft for sky and fog, keeping only Gloam's motes, seasonal colours, regional rain and dark nights? The other choice is a small fallback in Gloam that keeps a plain stepped sky and fog colour with no shader pack at all. The user asked what this means (October 2026), so it needs asking again in plain words, with a screenshot of each.
2. **Answered:** the pack is named VOID Welkin; the licence is in section 7.
3. **Answered:** motes and seasonal crowns stay in Gloam.

---

## 7. Decisions since the proposal

| Decision | Detail |
|---|---|
| Name | VOID Welkin ("welkin": the vault of the sky). Directory `welkin/`, branch `welkin`, worktree `.claude/worktrees/welkin`, zipped as `Welkin.zip`. |
| Licence | VOID's own terms (`welkin/LICENSE`): free to use, and to bundle unmodified; the source may be read; modified versions may not be redistributed without permission; all other rights reserved. |
| Motes and seasonal crowns | Stay in Gloam (question 3). The pack lights motes. Gloam's seasonal crowns are model swaps onto existing leaves, so `block.properties` already covers them. |
| Subagent effort | High at most (section 5). |
| Derived uniforms | `welkin_*`, declared only under `#ifdef VOID_GLOAM` (section 2.1). |
| Look | The rules of section 3.2 are absolute: Minecraft-like and stylized (adds texture, not realism), flat colour steps, Bayer dither, the 16-pixel world grid for anything covering the world, voxel clouds, no TAA, no soft volumetrics, natural surface colours. |

### M0 as built (the skeleton)

- Programs live once in `shaders/program/*.glsl`. `world0/`, `world-1/` and `world1/` hold generated wrappers (`tools/wrappers.sh`) that set `#version 330 compatibility`, the dimension (`WORLD_OVERWORLD`, `WORLD_NETHER`, `WORLD_END`) and the stage, then include the program.
- Buffers (`lib/buffers.glsl`): `colortex0` RGBA16F scene; `colortex1` RGBA16 octahedral normal and light map; `colortex2` RGBA8 flags (lit in deferred, material, emission); `colortex3` RGBA8 water mark for composite's water fog.
- Terrain and `dh_terrain` write the G-buffer and are lit in `deferred`. Every other gbuffers program lights itself with the same ramp (`lib/light.glsl`) and clears the deferred flag, with blending off for that buffer, so nothing is lit twice whatever order Iris draws it in.
- `deferred` reads both `depthtex0` and `dhDepthTex0`, each through its own inverse projection (`lib/depth.glsl`). Where the chunks drew, their surface is used even where the LOD's coarser surface stands slightly in front along the join, because the chunks draw after the LOD into the same colour buffers, so the colour on screen is theirs. Elsewhere the LOD's surface is used. `DEBUG_VIEW=1` shows that overlap in magenta and confirms the depth convention (Iris patches every read back to 0 near, 1 far).
- At the LOD join: `dh_terrain` and `dh_water` drop fragments inside `clipDistance` (set by VOID LOD to where the chunks end). `dh_water` also drops fragments where the chunks' own water already stands, so the water is not doubled along the join. Vanilla clouds are cut against `dhDepthTex0`, so a cloud behind an LOD mountain no longer draws over it. Composite's water fog makes the near and far sea beds read the same.
- The bridge helpers (`lib/void.glsl`: size and magic check, `voidMode()`, wrap addressing, h16 heights, flags and haze kind) are in place. Until M1 only `DEBUG_VIEW=6` uses them.

### M1, pack side (valley mist)

- `lib/mist.glsl`, called from `composite` after the water fog, in the overworld only (`VALLEY_MIST`). It reads N0 and N1 with `texelFetch` through `voidTexel`, only after `voidMode()` has passed the magic check and header texel 10 says both tiles hold data. The tiles' centre is read from the header (texel 2), not `void_nearCentre`, because the header's centre always matches the tile data beside it. `void_mistTime` comes from the uniform in full mode and from the header mirror (slot 16, both bytes, only if bit 16 of the live mask is set) in header mode. With no Gloam, or tiles not yet filled, there is no mist.
- **The slab:** mist = potential × `void_mistTime`; pool = max(smoothstep(0.08, 0.65, lowness), 0.6 · water nearness) (water nearness 1 on water, fading from 16 to 96 blocks away), bilinear over the lattice nodes; top = floor + 1 + (4 + 32 · mist) · pool; density = mist · pool². The mist potential alone put a 4 + 26 · mist blanket over any high flat land with some potential (the M1 review: the plateau and the savanna got the same veil as the canyon); measured against relief, level land with nothing lower round it gets a 1-block layer of no density, and a canyon (lowness 1) fills to a level top. "Lowest nearby ground" is a soft minimum over the four nodes of a 32 m lattice (every second cell, anchored to world cells), each node's bilinear weight times exp(−0.2 · (height − lowest)). It leans to the lowest node but stays continuous, so the top does not step at cell edges.
- **The ray:** sampled at `MIST_SAMPLES` places (2, 3 or 4, the camera's included; LOW 2, MEDIUM 3, HIGH 4) at fixed shares of the ray, with the same count for every pixel so no rings appear. The top and density run straight between samples, so the ray's entry and exit and the integral of density are exact for each piece. No marching. The ray stops where it leaves the tiles' square; density fades over the last 16 cells, and where a fill is still in progress.
- **The look:** cover is 1 − exp(−0.09 · `MIST_THICKNESS` · density · blocks), cut into 4 flat opacity steps (0.40, 0.64, 0.82, 0.95, so the first step reads as mist and a deep hollow is near-opaque) with a Bayer dither. The dither lies on the 16-pixel world grid at the surface the ray ends on (land with its normal, water and LOD land as up-facing): 1/16 block near by, doubling with distance and on grazing faces (blocks per pixel divided by |n · view|, at least 0.25) so a texel never drops under 2 screen pixels. Only sky pixels use the screen's Bayer. (M1 review: dithering on the mist's top with a texel of 1 pixel read as a 1-pixel screen checker over every surface.) Colour: a light cool white from first light (sun height −0.12 up), so dawn mist lifts the scene instead of greying it; dark blue-grey at night; toward a low sun it warms in 3 steps with the horizon's hue (normalised `fogColor`, so a dark fog colour cannot darken it) mixed with a fixed warm white; darker in rain. The sky table replaces the tint in M2.
- **Gates:** no mist with the eye in water, and none on land the sky light does not reach (sky level below 0.35, fully on from 0.65). Water and sky pixels count as open.
- **LOD land:** the surface comes from `surfaceAt`, so rays to `dh_terrain` pixels get the same slab out to where the tiles end (about 2 km). F tiles take over in M4.
- **Debug:** `DEBUG_VIEW=7` projects the tiles onto the land (red potential, green lowness, blue water nearness, full blue on water; dark checker for unfilled cells; dim red outside). Magenta is the tiles' centre cell, yellow the camera's cell, and orange and red the 112- and 127-cell rings. White hatching marks land under the mist's top where the mist is not nil. Cell edges are lined when near. `MIST_FORCE` (25/50/75/100) overrides `void_mistTime` to show the slab at any hour. The bridge status square in views 6 and 7 is now top left (it was bottom left, since `gl_FragCoord` counts from the bottom).
