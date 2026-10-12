# VOID Welkin and the new Gloam: design

Status: accepted, October 2026. Built from three read-only research passes: Iris 1.11.6+26.3 internals, a full inventory of Gloam, and a survey of the installed shader packs. Milestone M0 (the pack skeleton) is built; section 7 lists what has been decided since the proposal.

## 0. The decision in one paragraph

All world visuals move into VOID Welkin, a new shader pack for Iris, written from scratch. Gloam stops drawing anything. Instead it works out the world's state (seasons, wind, weather fronts, mist, haze, light) and hands it to the pack through a small bridge: one data texture, which needs no Iris mixin, and a set of `void_*` uniforms added by one small mixin. It keeps the whole soundscape and gains underground sound beds. Expanse is unchanged as the source of land data. LOD keeps drawing far terrain through the pack's Distant Horizons programs, and gets the bridge data with no changes. The look stays Minecraft, lightly stylized and of high quality: smooth gradients with gentle banding, directional sunlight and shadows, crisp pixel art only for the sun, moon, stars and voxel clouds, and no visible dither, TAA, bloom haze or fuzzy volumetrics (section 3.2).

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
| `SkyBands` and the banded sky shaders | Becomes the pack's sky table. |
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

### 3.2 Look rules (set by the user on 2026-10-11)

These replace the first rules (flat colour steps and a 4×4 Bayer dither everywhere), which the user dropped on
2026-10-11. Gloam's STYLE.md and the staged wave-2 modules still describe the old rules; they are adapted as they are
integrated.

- It must still feel like Minecraft: lightly stylized, not realistic, and of very high quality, on a par with good shader packs.
- Light, fog, haze, mist and sky are smooth gradients with gentle banding: a ramp may gather softly at a few levels (`softBands`, the `SOFT_BANDS` option), but never in flat steps with edges.
- No heavy or visible dither or checkerboard anywhere. The only noise is final's anti-banding: one 8-bit step of fixed triangular noise, which cannot be seen.
- Pixel-crisp detail only where it looks good: the sun, the moon, the stars and the voxel clouds. Everything that covers the world (haze, mist, water fog, wet ground) is smooth and is worked out at the pixel's own point, not snapped to a texel grid. Puddle outlines sit on the block textures' own texel grid, like texture.
- Clouds are voxel cells (12×12×4 near, doubling with distance), opaque, with hard edges and no dithered rims. No ray-marched soft volumes.
- Directional sunlight and shadows are the foundation of the quality: the sun lights faces by how squarely they face it, smoothly, and the shadow map (M6) carries it.
- No TAA (it smears pixel art), no bloom haze, no fuzzy volumetrics, no SSAO or SSR on far pixels. Everything renders at full resolution.
- Surface colours stay natural and real-world, with no glowing fantasy tints. Underground may be fantastical (glowworm light, coloured cave water).
- Each palette is small and lives in a pack file, keyed by haze kind, season and time. Palettes are control points: the colour runs smoothly between them, and every weight on them changes smoothly with the hour and the weather.
- The budgets of section 3.5 still hold.

### 3.3 Files and programs

**Files**
- `shaders.properties`: screens; profiles LOW, MEDIUM, HIGH; `customTexture.void_data`; `smooth()` uniforms; blend modes (including `blend.dh_water`); `dhShadow.enabled=false`; `size.buffer.colortex5 = 64 32`; the smallest possible `iris.features.required`.
- `clouds=fancy` (vanilla's clouds on; Iris rejects the word `on`) until M3, then `clouds=off`, when the pack draws its own clouds.
- `block.properties` covers vanilla, `expanse:*` and Gloam's seasonal blocks. `item.properties` and `entity.properties` are also needed.
- `dimension.properties` (`world0=*`, `world-1`, `world1`), thin `world0/`, `world-1/`, `world1/` wrappers that `#include "/program/*.glsl"`, `lang/en_US.lang`, and `#version 330 compatibility`.

**Programs**

| Pass | Programs | Job and bridge use |
|---|---|---|
| prepare | `prepare` | 64×32 sky table (smooth control points) from the sun angle, `void_sky`, `void_light`, `void_cloudCover`, `void_hazeKind` |
| shadow | `shadow` | Near chunks only (about 96–160 blocks, 1024–2048²). No LOD. Off on LOW. |
| opaque | `gbuffers_terrain`, `entities`, `block`, `hand`, `basic`, `textured`, `textured_lit`, `skybasic`, `skytextured`, `spidereyes`, `armor_glint`, `beaconbeam` | Albedo, octahedral normal and material, and lightmap into 3 targets. Leaf and grass sway from `void_wind`. Pixel-art sun, moon and stars (`void_light.y`). Motes come through `textured` and `textured_lit`. |
| far land | `dh_terrain` | The same layout. Material from `dhMaterialId`. A hash-based 16-pixel texture on the world grid that coarsens with distance. No `discard`, no texture fetch except `void_data`. |
| deferred | `deferred` | Lights opaques using `depthtex0` where the chunks drew and `dhDepthTex0` elsewhere (section 7). Stepped light ramp scaled by `void_light.z`, so dark nights match gameplay. Near shadow map. Cloud shadows from W0/W1 cover along the sun direction. Underground ambient from U openness. |
| translucent | `gbuffers_water`, `dh_water` | Forward shading with pixel ripples (wind-driven). Far water reflects the sky table; no SSR past about 2 km. Wetness darkens shores. |
| clouds | composite pass (from M3) | Voxel DDA through the cloud decks (`void_cloudLayers`) over W0/W1 cover. Cells are on or off by a `void_seed` hash against cover. Crisp faces, lit by the sky table's ambient and the sun or moon. |
| atmosphere | `composite` | Height haze with an exact formula, taking 2–4 samples of N/F tiles along the view ray. Valley mist slab, underground fog, water fog. All smooth, no dither. |
| far weather | `composite1` | Rain curtains under rainy W1 cells, as smooth 2.5D sheets between cloud base and F0 ground. Far valley mist over F tiles. |
| final | `final` | A soft shoulder past white and invisible anti-banding (one 8-bit step of fixed noise); no palette steps |

**LOD and DH**
- LOD's `StandardMacrosMixin` defines `DISTANT_HORIZONS`. Far material IDs come from `DH_BLOCK_*` (LEAVES 1 … ILLUMINATED 15).
- Depth: Iris stores reversed depth and patches reads back to the usual convention. LOD clears to 0 and tests with `GEQUAL`. The pack checks this on the first test with a depth debug view.
- `PackLodProgram` calls `customUniforms.assignTo` and `addGbufferOrShadowSamplers` inside `DHCompat.<init>`, before `optimise()`, so `void_*` and `void_data` reach `dh_terrain` and `dh_water`. LOD must keep building its programs there; a lazily built program would lose uniforms used only by it.
- LOD's auto-tune counts half of the pack's draw time, so heavy `dh_*` programs shrink the LOD radius. Keep them trivial.

### 3.4 How each feature uses the data

| Feature | Inputs | How |
|---|---|---|
| **Valley mist at dawn** | N0 ground and lowness, N1 mist potential, `void_mistTime`, F tiles beyond 2 km | mist = potential × `void_mistTime`. The slab top is the lowest nearby ground + 1 + (4 + 32·mist)·pool and its density mist·pool², where pool comes from the land's relief (N0 lowness) and nearness to water, so mist pools in hollows and along water and high flat land gets none. Composite intersects the view ray with the slab exactly (2–4 tile samples, no marching). The opacity follows the optical depth smoothly. The sun-side tint comes from the sky table (the horizon colour until M2). Sunrise is thick, midday is gone, after sunset is a thin veil. |
| **Cloud cover from humidity and rain shadow** | W0/W1 cover, `void_cloudLayers`, `void_storm`, `void_maxStorm`, `void_seed` | Gloam bakes the bias into W: the front field (4-octave fbm over 3 km cells, drifting east at 6 blocks/s) times Expanse humidity, reduced downwind of high ground (new: sample altitude upwind along `void_wind`). The pack only thresholds cover per cell, so the CPU and pack agree, and so do rain and sound. Storm cells grow towers to the tower-top height. |
| **Haze colour by country** | N1/F1 haze kind, `void_haze`, `void_hazeKind` | Each kind picks a small palette: humid_lowland pale blue-grey, misty_lakes cool white, forest_damp green-grey, dusty warm ochre, temperate neutral, clear_alpine thin and blue. Strength comes from `void_haze.x` (thinned at midday), `void_haze.w` for far land, and the dust share. Along the view ray, kinds are blended through the 2–4 samples. |
| **Seasons** | `void_year`, `void_warmth`, `void_seasonState`, block tints already baked by Gloam | Blocks arrive already tinted, so the pack adds touches only: frost sparkle when cold, low winter sun palette, autumn haze warmth, snowline-aware wet or snow-covered shading. |
| **Underground fog by openness** | U openness, floor and ceiling, water kind, `void_underground`, `void_cave` | Fog reach follows openness: tight in tunnels, long in great caverns and the underground seas. Mist rises over great rivers. Glowworm caves get a teal glow. Dripping caves get damp, darker walls. Fog thickness is smooth; colours may stay fantastical. |
| **Rain and wet ground** | `void_rain`, `void_showers`, `void_wetness`, W1 rain | Near rain stays vanilla particles. Far rain becomes curtains in `composite1`. Wet ground darkens with `void_wetness`, and puddles mirror the sky. |
| **Night** | `void_light` | Moonlit or clouded sky colour, star count, and a light ramp that matches the gameplay light factor. |
| **Lightning** | Iris lightning uniforms, `void_storm` | A flash; the timing gate stays in Gloam. |

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
| Look drifts toward realism or fuzz | Medium | The look rules (3.2) in the pack's README. Every milestone is reviewed against screenshots by the user. |
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
| Look | The rules of section 3.2 are absolute. Since 2026-10-11: Minecraft-like and lightly stylized, very high quality; smooth gradients with gentle banding; no visible dither anywhere (only invisible anti-banding); pixel-crisp sun, moon, stars and voxel clouds; directional sunlight and shadows first; no TAA, bloom haze or fuzzy volumetrics; natural surface colours. (Before: flat colour steps, Bayer dither and the 16-pixel grid for everything covering the world.) |

### M0 as built (the skeleton)

- Programs live once in `shaders/program/*.glsl`. `world0/`, `world-1/` and `world1/` hold generated wrappers (`tools/wrappers.sh`) that set `#version 330 compatibility`, the dimension (`WORLD_OVERWORLD`, `WORLD_NETHER`, `WORLD_END`) and the stage, then include the program.
- Buffers (`lib/buffers.glsl`): `colortex0` RGBA16F scene; `colortex1` RGBA16 octahedral normal and light map; `colortex2` RGBA8 flags (lit in deferred, material, emission level and light class packed, Minecraft's corner shading from `separateAo`); `colortex3` RGBA8 water mark for composite's water fog.
- Terrain and `dh_terrain` write the G-buffer and are lit in `deferred`. Every other gbuffers program lights itself with the same ramp (`lib/light.glsl`) and clears the deferred flag, with blending off for that buffer, so nothing is lit twice whatever order Iris draws it in.
- `deferred` reads both `depthtex0` and `dhDepthTex0`, each through its own inverse projection (`lib/depth.glsl`). Where the chunks drew, their surface is used even where the LOD's coarser surface stands slightly in front along the join, because the chunks draw after the LOD into the same colour buffers, so the colour on screen is theirs. Elsewhere the LOD's surface is used. `DEBUG_VIEW=1` shows that overlap in magenta and confirms the depth convention (Iris patches every read back to 0 near, 1 far).
- At the LOD join: `dh_terrain` and `dh_water` drop fragments inside `clipDistance` (set by VOID LOD to where the chunks end). `dh_water` also drops fragments where the chunks' own water already stands, so the water is not doubled along the join. Vanilla clouds are cut against `dhDepthTex0`, so a cloud behind an LOD mountain no longer draws over it. Composite's water fog makes the near and far sea beds read the same.
- The bridge helpers (`lib/void.glsl`: size and magic check, `voidMode()`, wrap addressing, h16 heights, flags and haze kind) are in place. Until M1 only `DEBUG_VIEW=6` uses them.

### M1, pack side (valley mist)

- `lib/mist.glsl`, called from `composite` after the water fog, in the overworld only (`VALLEY_MIST`). It reads N0 and N1 with `texelFetch` through `voidTexel`, only after `voidMode()` has passed the magic check and header texel 10 says both tiles hold data. The tiles' centre is read from the header (texel 2), not `void_nearCentre`, because the header's centre always matches the tile data beside it. `void_mistTime` comes from the uniform in full mode and from the header mirror (slot 16, both bytes, only if bit 16 of the live mask is set) in header mode. With no Gloam, or tiles not yet filled, there is no mist.
- **The slab:** mist = potential × `void_mistTime`; pool = max(smoothstep(0.08, 0.65, lowness), 0.6 · water nearness) (water nearness 1 on water, fading from 16 to 96 blocks away), bilinear over the lattice nodes; top = floor + 1 + (4 + 32 · mist) · pool; density = mist · pool². The mist potential alone put a 4 + 26 · mist blanket over any high flat land with some potential (the M1 review: the plateau and the savanna got the same veil as the canyon); measured against relief, level land with nothing lower round it gets a 1-block layer of no density, and a canyon (lowness 1) fills to a level top. "Lowest nearby ground" is a soft minimum over the four nodes of a 32 m lattice (every second cell, anchored to world cells), each node's bilinear weight times exp(−0.2 · (height − lowest)). It leans to the lowest node but stays continuous, so the top does not step at cell edges.
- **The ray:** sampled at `MIST_SAMPLES` places (2, 3 or 4, the camera's included; LOW 2, MEDIUM 3, HIGH 4) at fixed shares of the ray, with the same count for every pixel so no rings appear. The top and density run straight between samples, so the ray's entry and exit and the integral of density are exact for each piece. No marching. The ray stops where it leaves the tiles' square; density fades over the last 16 cells, and where a fill is still in progress.
- **The look** (as built in M1; since the style pivot the opacity is 0.72 · cover, smooth, with no dither): cover is 1 − exp(−0.09 · `MIST_THICKNESS` · density · blocks), cut into 4 flat opacity steps (0.40, 0.64, 0.82, 0.95, so the first step reads as mist and a deep hollow is near-opaque) with a Bayer dither. The dither lies on the 16-pixel world grid at the surface the ray ends on (land with its normal, water and LOD land as up-facing): 1/16 block near by, doubling with distance and on grazing faces (blocks per pixel divided by |n · view|, at least 0.25) so a texel never drops under 2 screen pixels. Only sky pixels use the screen's Bayer. (M1 review: dithering on the mist's top with a texel of 1 pixel read as a 1-pixel screen checker over every surface.) Colour: a light cool white from first light (sun height −0.12 up), so dawn mist lifts the scene instead of greying it; dark blue-grey at night; toward a low sun it warms in 3 steps with the horizon's hue (normalised `fogColor`, so a dark fog colour cannot darken it) mixed with a fixed warm white; darker in rain. The sky table replaces the tint in M2.
- **Gates:** no mist with the eye in water, and none on land the sky light does not reach (sky level below 0.35, fully on from 0.65). Water and sky pixels count as open.
- **LOD land:** the surface comes from `surfaceAt`, so rays to `dh_terrain` pixels get the same slab out to where the tiles end (about 2 km). F tiles take over in M4.
- **Debug:** `DEBUG_VIEW=7` projects the tiles onto the land (red potential, green lowness, blue water nearness, full blue on water; dark checker for unfilled cells; dim red outside). Magenta is the tiles' centre cell, yellow the camera's cell, and orange and red the 112- and 127-cell rings. White hatching marks land under the mist's top where the mist is not nil. Cell edges are lined when near. `MIST_FORCE` (25/50/75/100) overrides `void_mistTime` to show the slab at any hour. The bridge status square in views 6 and 7 is now top left (it was bottom left, since `gl_FragCoord` counts from the bottom).

### The style pivot (2026-10-11)

The user set the new look of section 3.2 over the flat-steps-and-dither rules. What changed in the pack:

- **final:** no `COLOR_STEPS` palette and no 4×4 Bayer. A soft shoulder, then one 8-bit step of triangular noise per pixel (fixed, from a hash), which removes contour lines from the 8-bit output and cannot be seen.
- **The light ramp** (`lib/light.glsl`): sky and block light follow vanilla's curve smoothly, eased into `LIGHT_BANDS` soft bands (`lib/common.glsl` `softBands`: a smoothstep inside each band, mixed in by `SOFT_BANDS`, 50 % by default). The sun lights a face by Lambert with a slight wrap (no three-step n·l), where the sky reaches (sky light 0.55 to 0.93, smooth). No dither.
- **The sky table and dome:** every weight in the mood is continuous (night, dark, overcast, rain, storm, golden and blue hour, sun and moon strength, glow, stars, flash), so the sky, the land's light and the clouds change with the hour without jumping. The sunset palette position is smooth (each of Twilight's steps eased in over 500 ticks). The dome reads the table as control points: band, sun ring and moon ring are continuous positions for the exact view direction, eased into soft bands and blended from neighbouring texels (4 fetches, 6 more near the moon). The glow rings start where the stepped first ring did, so a faint glow does not tint the whole dome. The sky grid is now only the stars'. The sun ring is a solid pixel ring, not a checker. The first stars wait until the sun is well down.
- **Haze:** worked out at the pixel's own point (no texel snapping); opacities, the kinds' mix, the dust share and the world's edge are smooth; the land is blended bilinearly between the four nearest tile cells (8 fetches per segment instead of 2), and a border between kinds is a blend of both, density included. The haze's air turns smoothly toward the sun's horizon inside the glow (`skyHazeAir`), instead of switching at 90° from the sun.
- **Valley mist, water fog, the far fade, wet ground:** smooth. The mist's opacity is 0.72 · cover; puddles fade out toward their reach instead of dithering out; the puddle outlines keep the block texel grid.
- **Far land:** the far texture is fainter (−5 %/+4 %) and fades out between 1 and 6 km, so hazy distance never turns to pixel noise.
- **Gloam's stepped values:** Gloam sends golden, blue and the sunset step already cut into its palette steps (`void_sky.xyw`); the pack now works them out from the clock with the same maths and takes only vivid from Gloam (`void_sky.z`, read as 0.75..1.5; before, it was read as 0..1, which dimmed every sunset).
- **Options:** `COLOR_STEPS`, `DITHER` and `HAZE_STEPS` are gone; `LIGHT_STEPS` is now `LIGHT_BANDS`; `SOFT_BANDS` (0..100 %) is new, on the Colour and finish screen.
- **Kept for the staged modules:** `bayer4`, `steps`, `worldDither`, `hzBayer` and `hzSteps` still exist so far weather, underground and water and shadows compile until they are adapted (the note at the top of the wave-2 INTEGRATION.md).

### The light step: sun, moon and near shadows (2026-10-11)

- **Deferred light** (`lib/light.glsl` `welkinLightCore`): sky ambient from the sky table (x 0.88) by the sky-light ramp and vanilla's face shading; the sun or moon (`shadowLightPosition`, strength and colour from the table, x 1.3) by Lambert with a wrap that widens from 0.08 at a high sun to 0.25 at the golden hour, so tops still catch a grazing sun; a sun-side glow (the table's horizon toward the sun, 0.14 at most) on faces turned to a low sun, lit or shaded, from the late afternoon to just past sunset; warm block light as before. Leaves take a little light on their shaded side; a plant's crossed quads take it on both sides. The sky table's sun strength now holds until the sun is low (eased over sun height -0.03..0.13) and the golden-hour shade stays a dim blue sky's, so the late afternoon keeps clear, warm light and readable shadows.
- **Near shadow map** (`program/shadow.glsl`, `lib/shadow.glsl`): overworld only, off with `SHADOWS` (LOW). `shadowMapResolution` 1024/1536/2048 and `shadowDistance` 96/96/160 by profile (MEDIUM came down from 128 to keep the pass inside 1.2 ms over forest and gorge); only the chunks inside the distance are drawn (`shadowDistanceRenderMul = 1`). A fisheye distortion (0.85) puts the fine texels at the player; the depth range is halved so casters up to about 200 blocks toward the sun land in the map. Leaves and plants sway in it exactly as in `gbuffers_terrain` (the same `welkinSway` inputs). Lookups use a normal offset sized from the local texel (toward the light for a plant's quads), then hardware 2x2 compare taps on a fixed grid (`SHADOW_SOFTNESS` 0 crisp, 1 four taps, 2 nine taps): clean edges, slightly softened, no noise or rotation. Stained glass tints the light (shadowcolor0); under water the light takes a pale blue-green and fades with the depth it travels (caustics wait for P7).
- **The fade and the LOD join:** over the last 20 % of `shadowDistance` the map's answer fades smoothly into the sky-light gate (`sunOpen`, sky light 0.55..0.93) that the far chunks, the LOD's land and LOW use. The LOD's land gets the same directional light and never a map shadow (`dhShadow.enabled=false`). Inside the map a loose sky-light gate (`sunLeakGate`, 0..0.3) stays, so a sealed cave the map misses cannot leak sun.
- **Forward programs:** entities, block entities and water and glass (`G_SHADOW`) read the map too; the other self-lit programs (hand, particles, basic) use the sky-light gate.
- **Options:** `SHADOWS`, `shadowMapResolution`, `shadowDistance`, `SHADOW_SOFTNESS` (Crisp, Slightly soft, Soft) on the Light screen and in the profiles; water_shadows' `WS_SHADOWS` and `WS_SHADOW_TAPS` are gone from the option table. `DEBUG_VIEW` 12 shows the sun's reach x facing with what glass, water and clouds let through, the LOD's land tinted blue.
- **Cost** (RTX 4060, 1600x900, MEDIUM, test world A, shadows on against off): 0.9 ms over the open valley, 1.1-1.4 ms over the dense forest, at the top of the 0.8-1.2 ms budget. Most of it is the shadow pass's leaf geometry, not the lookups.

### The sky, haze, night and rain review (2026-10-11)

- **Stars** come out by the sun's depth, not the clock: none until the sun is 6 degrees down, all by 16 (sky table
  `m.stars`), none by day and none under a closed overcast. The white specks the review took for stars were other
  things: in daytime water they were pinholes between VOID LOD's water tiles where the sky shone through (composite2
  now draws a lone sky pixel well under the horizon, with land on both sides, as its neighbour); over the summit's
  clouds and in the heartland's overcast they were Gloam's powder and snow motes, drawn in front of the clouds as they
  should be.
- **Lit far land:** the orange mounds glowing at night were VOID LOD's stand-ins for structures and settlements, which
  carry block light 11..13 over every face so a lit town glows from afar; the near ramp lit them like day. `dh_terrain`
  now takes a third of the LOD's block light (a faint warm glow of windows). Their orange by day is the LOD's roof
  colour, not light.
- **The glowing columns** over far land under a clear sky were the haze: its far segment read the land at its
  geometric middle, so wherever that point fell on high ground its murk lay over the whole segment and every pixel
  sharing it stood up as a bright column. The far segment now reads the land where the ray ends. Storm towers also
  need rain (or a storm) in their W1 cell now; a high front alone raised lone towers and anvils on the horizon.
- **Clouds** (`lib/clouds/shade.glsl`): tops by the light's height, sides by facing (a side is its top's colour in
  less light, also in the dusk palette, so the thin sides under an outline read as thickness, not a lilac sliver),
  undersides darkest in a big cloud's middle and lighter near an open edge (`wcInterior`, from the finished map's edge
  bits, the edges' shares multiplied so corners have no crease); a closed overcast's base is as bright as the horizon
  under it. Far off and at a grazing angle a deck is a **band** (`wcBandOf`): where its cells would span under about
  five pixels front to back it melts into one colour, the cloud's and the sky's mixed by how much of the deck the
  weather fills, the same for a cell and a gap, so the horizon has no streaks; the march stops at a deck that is all
  band. composite2 now lays the **haze** over cloud pixels at their distance (not the world's edge), so far decks sink
  into the same air as the land beyond them. A deck at **eye height** gives way to a fog bank: its near cells fade
  out, and the ray's path through the slab, by the deck's share of cloud over the camera, veils the view
  (`wcSlabFog`); **inside a cell** the view is veiled by the path through the eye's own cloud (`CloudHit.tOut`).
- **Wet ground:** soil and grass about a quarter darker when soaked, plants a fifth; wet surfaces gloss with the sky
  at a low angle and take a soft sun highlight; puddles are a water film over the darkened ground with the sky's
  gradient and the clouds overhead (the decks as sheets along the reflected ray) by a Fresnel term, with a wider dark
  rim. Rain streaks are the texture's shape only, a pale grey-white in the sky's light, about half as opaque.
- **Night:** the valley mist is lit as the land is at night (the sky's fill and the moon), never brighter than it, and
  half as thick; the dark-nights scale reaches the eye's fill as its square root, so a new moon is still darkest but
  the land's shapes read.
- **Far water:** VOID LOD bakes a depth-darkened bed into the water colour (as vanilla would show it with no pack);
  the pack draws the bed under it anyway, so `dh_water` takes the bed back out and the far water matches the chunks'.
- **Cost** (RTX 4060, 1600x900, MEDIUM, test world A, frame-time sweeps in one run, `gloam.show.packSweep`): clouds
  about 0.9-1.1 ms over vanilla's (the march and its shading; the map passes now work out only the cells each ring
  can draw), shadows 0.2-0.7 ms (1 ms in a forested gorge), haze 0.2-0.3 ms, valley mist 0.1-0.3 ms (the camera's
  sample is worked out once in the vertex shader; it was 0.7 ms in a misty gorge), wet ground 0.2-0.3 ms. Whole
  frames run 2.1-3.5 ms by day and about 4 ms at night in the gorge. The review's 4-9 ms frames came from another
  session's game test running on the same GPU at the same time. The clouds stay over their 0.6 ms budget.
- **Second pass** (re-shot in test world A): far **storm towers** sink into their deck between 6 and 14 x `WC_RING0`
  (2.3-5.4 km at MEDIUM), since a tower cell doubles with the rings and a storm 6 km out stood up as one 1.5 km box
  on the horizon (the noon and dusty "horizon box"). Cloud **undersides** keep only a gentle interior shade (it tiled
  a patchy deck), and under a closed overcast a base is the overcast dome's own colour in its direction. The haze's
  middle segment takes its **kinds** over a 128-block lattice (`hzSmoothKinds`): read at one distance for every far
  pixel, a border between kinds stood up as a vertical wall of dust. **Full moon**: the moon's light 0.22 (was 0.11)
  and the night fill up to 1.7 x under a clear full moon; the full-moon valley reads as vanilla's (27.7 against 28.4
  mean), a new moon is unchanged. The lake district's glowing patch is a surface lava pool (a real light), its halo
  gone with the torch light's daytime fade. With Iris and no pack, the far air's rain curtains draw without the crash.
  Bench rain1 (no Gloam) runs at 325 fps (the review's 117 was contention).

### The sky review's third pass (2026-10-11)

- **Clouds as voxels with a body** (`lib/clouds/shade.glsl`): every deck cell now carries its thickness, how far the
  puffs clear the cover's threshold, in four steps (the raw map's B, the finished map's R bits 6..7 and 12..15,
  `wcMapDensity`): a thick cell's top is a little brighter and its base darker than a thin cell's at a cloud's ragged
  edge, cell by cell, so a deck reads as crisp voxels with a body instead of one flat sheet. A top takes a crisp
  bevel a block and a half wide along the cloud's open edges (a gradient across the whole cell read as a blurred,
  see-through edge), sides darken toward their foot, and a base is one flat shade a cell (a lighter rim drew a
  bright line round every cloud), seeing less of the dome (0.74) and darker still under a thick cover. A top's rim
  and thickness shade it after the soft shoulder, which squeezed a noon-lit top's shades into one flat white. The
  decks lie in each other's shadow by day (`wcSelfShadow`, the cloud-shadow lookup from the face).
- **Dawn and dusk decks** take the dusk palette at the sky's own brightness: a top about as bright as the horizon
  behind it (a little brighter toward the sun), a base as the dome over it, all dimmer while the sun is under the
  horizon (the earth's shadow), when the tops toward the sun turn rose. Before sunrise a deck seen from the summit is
  dusky (about 60 in 8-bit against a 19,22,41 zenith; it was a beige 119,112,99), not a pale sheet brighter than the
  sky. Near tops toward a low sun are gold.
- **Slivers:** a side face is never brighter than the cloud's body seen with it (its base from below, its top from
  above) unless the direct sun falls on it, and then by a quarter at most; it leans to the body's hue (no olive
  strips along a rose dusk base); a side under about 9 pixels tall takes the body's colour. After a ring change the ray may stand inside the new, wider cell rather than on its side: a hit
  there is drawn as the cap, not as a side (it stood as a wall on the ring's edge).
- **Far fade:** cells sink into the sky from a few hundred blocks out (`wcFog`): over the eye into the dome's own
  colour at their height (`wcFogTarget`; into the paler horizon they were brighter than the sky round them and read as
  see-through), seen from above into the horizon. The far band's hand-over is narrow (cells under about 1.2-3 pixels
  deep), so cells nearer than that are drawn whole and crisp; a cell hit inside the band keeps most of its body.
- **Inside a cloud:** the march follows the eye's own cloud (a deck cell, a tower, an anvil) through as many cells as
  the ray crosses before it comes out (`insideRun`), so its inside is one smooth fog of the cloud's colour, never its
  own neighbours' faces (an eye inside a tower saw two flat colour fields split at the horizon). A deck at eye height
  is a fog bank with soft edges (20 blocks over and under the slab, `wcSlabCum`), laid over everything else the clouds
  draw; its cells fade into it out to 3 km and it has no far band, so neither the horizon nor the deck's far edge
  splits the view when the eye stands in it or just over or under it.
- **Cloud shadows** are light (the sun's share 0.74 under one deck, 0.63 under two, 0.56 under three: open ground some
  15-20 % darker) and their edges spread over 5 blocks inside each cell's open sides, so they no longer tile a meadow
  in hard 16-block squares.
- **Haze columns:** the middle haze segment reads the ground, lowness and water over the same 128-block lattice as its
  kinds once it reaches past a few hundred blocks (`hzSmoothKinds` with `ground`): the one point it reads stood for a
  kilometre of land, and where it fell on a knoll the far land behind it stood up in narrow glowing columns.
- **Far water seams:** a pixel of the LOD's land with no water on it, water on both sides and itself farther than that
  water is a crack in dh_water's mesh showing the bed: composite2 draws it as its neighbour (with the sky pinholes).
- **Stars** lie behind the sky's own light: they fade out over a sky brighter than a lit dusk, whatever the star
  amount says. (The crosses in the heartland's overcast were Gloam's snow motes, 333 of them in that rain.)
- **Sky near the horizon:** the lowest bands and the glow rings low in the sky are no longer terraced (`softBands` only
  from the third band and above about 5 degrees), so a dawn sky has no hard steps over the horizon.
- **Night:** the moon is the key light (0.34, was 0.22), the night's fill 1.2 + 0.36 x moon of NIGHT_SKYLIGHT (was
  1 + 0.7 x moon) and a little less blue, and Gloam's dark-nights scale reaches the fill as its 0.25th power (was the
  square root). Against no pack (mean / 90th percentile): full moon 29.8 / 38 to 27.2 / 42, new moon 12.5 / 16 to
  12.4 / 20 (they were 17.8 / 24 and 10.5 / 16 measured the same way).
- **Particles** carry no normal: they are lit as a level surface (it read as NaN light). Gloam draws its soft motes
  (spray, breath, powder) under Welkin a pale grey with a smooth alpha and less body (`Mote.WELKIN_BODY`), so the
  waterfall's spray at night is a faint veil, not a bright speckled blob.
- **Far land block light:** the LOD's land takes a fifth of its block light (was a third). The no-Gloam bench's
  orange mound (`night1`) already read a dim 41,32,25 after the third; the review's crop was from an older shot.
  The bench without Gloam (peak1, night1, rain1) loads and runs with no errors.
- **The straight cut-off** of the alpine view (the eye was 1 block inside the low deck under a 0.95 cover) was not
  found in the code: the tiles fade out cell by cell (a hashed scatter), the march's reach hands over to sheets, and
  the map covers every ring past its reach. The new deck views (the eye in the low deck and 15 over it, in rain) show
  no straight edge; the eye-level fixes above (the fog bank laid last, no band, the cells fading out over 3 km) may
  have been it. Still to watch for.
- **Showcase:** `whaze_clear_alpine` is found under a clear sky (the fronts round it under 0.38) with the eye out of
  the low deck's slab; `wdeck0` and `wdeck1` put the eye in the low deck and just over it, over the valley in rain.
- **Cost**, re-timed with the machine otherwise idle (RTX 4060, 1600x900, MEDIUM, test world A, one run,
  `gloam.show.packSweep` with CLOUDS, SHADOWS, HAZE_ENABLED and VALLEY_MIST each switched off in turn; a pass's cost is
  the view's frame time less its sweep line; CLOUDS off brings vanilla's clouds back, so the clouds' line is theirs
  over vanilla's):

  | View | Frame | Clouds | Shadows | Haze | Valley mist |
  |---|---|---|---|---|---|
  | wsky3_noon (summit, deck below) | 2.22 ms | 0.79 | 0.0 | 0.22 | (stall) |
  | wsky5_sunset | 2.79 ms | 0.96 | (stall) | 0.25 | (stall) |
  | whaze_old_forest | 3.61 ms | 0.99 | 0.74 | 0.16 | 0.08 |
  | wnight0 (gorge, full moon) | 4.36 ms | 0.74 | 1.10 | 0.36 | 0.76 |

  Readings above the view's own (marked stall) are the reload's shadow-chunk rebuild, not the change. Whole frames run
  2.2-3.6 ms by day and 4.4 ms in the night gorge, inside the 4 ms MEDIUM total by day. The clouds stay at 0.75-1.0 ms,
  over their 0.6 ms budget (about as before this pass, 0.9-1.1 ms: the march is most of it), and the gorge's shadows
  at the top of theirs; the gorge's mist line may hold some stall too. The review's 8.6-9.4 ms night frames were contention.

### The lighting rebuild, step 1: exposure, tone and the light's colour (2026-10-12)

The bench against Complementary Reimagined, Unbound + Euphoria and Hysteria (scratchpad `welkin_w2/lighting/GAPS.md`)
found Welkin's daylight a quarter of their linear brightness, too neutral and grey, its golden hour gone before the sun
set, and its shade lifted and grey. The user's direction: on par with them or better, strong sun that shapes every
form, real contrast, a golden hour to the horizon, natural clean warmth (less gold than Complementary), storybook
shade that is deep, sky-tinted and readable.

- **Linear light and one tone curve** (`lib/tone.glsl`, `lib/light.glsl` `welkinLightFull`): the albedo goes to linear;
  the sun or moon, the sky's fill, the ground's bounce and the torches add as light does; one filmic curve per pixel
  brings the sum to the display values the rest of the pack works in (exposure, a gentle S on luminance round mid
  grey that fades out in the deep dark so nights keep their level and eases off in the highlights so a sunlit pale
  face keeps its texture, a soft shoulder mostly on luminance so sunlit grass
  stays green and a pale face in full sun rolls into near white without clipping, saturation 0.97). No neighbourhood,
  no adaptation, no bloom, no TAA. The sky, the clouds and the haze keep their display values and final's shoulder.
- **The land's light in the sky table** (`lib/sky/build.glsl` `skyLand`, row 5 x 12..15; the table is now RGBA16 so a
  moonless night's linear fill keeps its precision): the shadow light's direct light, the sky's fill on an upward face,
  the ground's bounce on a downward face, and the warm horizon band, all smooth in the sun's height h:
  - **sun:** 2.4 at noon, about two thirds at the golden hour, on until the disc touches the horizon (out over h
    0.03..-0.012); a clean warm white high up (1, 0.97, 0.90), warming through the afternoon (1, 0.83, 0.48), amber at
    the golden hour (1, 0.73, 0.40) and deep amber at the horizon (1, 0.54, 0.24); the warmth comes out of the blue more
    than the green, so sunlit grass stays green; the morning takes the same path paler and rosier (a rose-gold dawn,
    1, 0.55, 0.42 at the horizon). Overcast and storms cut it as before.
  - **fill:** 0.245 on an upward face at noon, a clear blue that cools as the sun sinks and bluer still in the blue hour,
    dimming slowly with the sun and fast after sunset; grey and brighter under a closed sky; the night's fill is the old
    moonlit look taken to linear (x 1.6 for the linear sum), so nights read as before.
  - **hemisphere:** a face takes the fill by how much sky it sees ((1 + n.y) / 2) and the ground's bounce (a warm earthy
    average ground under the sun and the fill, x 0.36) by how much ground; Minecraft's face shading is kept at 45 % on
    the fill (the sun now models the forms). A low sun's bright side of the sky lights what faces it (the fill's
    `glow` share, in the colour of the horizon toward the sun), replacing the old sun-side glow and rim.
  - **torches:** a little more orange at the source (1.76, 0.95, 0.35) and paler as the light spreads, with a steeper
    falloff (the ramp to the power 2.3); the sun now takes only 35 % of a lamp's light in the open, so a lamp's pool
    reads in the shade at noon.
  - **corner shading:** Minecraft's AO to the power 2.3 on the fill and the torches (a little deeper than vanilla), never
    on the sun. The canopy leak is down to 0.2 (in linear light the old 0.45 lit the trunks orange under the leaves).
- **The warm horizon band** (`lib/sky/dome.glsl` `skyLowSunBandOver`): toward a low sun the dome brightens along the
  horizon into a warm white, gold as the sun touches the horizon and rose as it goes under; broad in azimuth, hugging
  the horizon, only ever brightening. The horizon toward the sun (row 5 x 5) and the far haze toward the sun (row 7)
  take it too, so the far land near a low sun is warm and bright.
- **Low-sun shadows** (`lib/shadow.glsl`): the step toward the light grows with how far the light grazes the face (a
  texel's depth spread is the texel times the tangent of the light's angle), so a low sun on level ground lights it
  instead of the ground shadowing itself (at 18:30 the whole near land had gone dark).
- **Kept as they were:** the clouds' own light (they still read the old sky-table texels x 0..3), mist, wet ground and
  water's glint, caves (the dark end of the curve is untouched), rain and overcast.
- **Left for step 2:** shadows past the map (at the golden hour the map's edge shows as a lit ring where a hill shades
  the near land), light through leaves and sun patches under the canopy, contact shadow, water.

### The lighting rebuild, step 2: shadow reach, edges, contact shade, light through leaves (2026-10-12)

Closes GAPS.md gaps 5, 6 and 8 (water stays with P7).

- **Reach by profile:** MEDIUM's map reaches 128 blocks at 1536 (was 96), HIGH 192 at 2048; the option now offers
  64..256 and 1024..4096. The pass pays for it: faces turned away from the light are left out wherever a closed shape
  stands behind them (every solid block, through the new `shadow_solid` program, which also skips the texture and the
  alpha test so the depth test runs early; and leaves), and creatures cast only within half the reach
  (`entityShadowDistanceMul`). What the light meets first of a closed shape is always a face turned to it, so the map
  is unchanged.
- **Edges (contact hardening, `lib/shadow.glsl` `shadowSampleW`):** a 3x3 search of the map's depths round the point
  finds its casters and how far above it they stand; the penumbra's half width is 0.02 blocks per block of that height
  (0.034 on Soft) plus 0.015, filtered by a 4x4 grid of hardware-compared taps over at most 6 texels. A post's foot is
  crisp, a tree's shadow on the ground 20 blocks below softens, a house's evening shadow widens toward its end. Points
  with no caster round them, or deep inside a shadow, stop after the search. Fixed offsets, no rotation, no noise.
  `SHADOW_SOFTNESS` is now Crisp (the hardware tap only), Natural (default), Soft.
- **Far shadows (`FAR_SHADOWS`, `lib/farshadow.glsl`):** past the map's 80 % (everywhere with the map off) and on the
  LOD's land, the lit point steps 14 fixed, quadratically spread steps toward the light through the chunks' depth and
  then the LOD's, over 0.3 of its distance (8..192 blocks); a step behind a surface by more than a depth margin and
  less than a thickness counts as shadow, faded smoothly over both. Houses and trees 100-200 blocks off throw long
  shadows, ridges shade the slopes behind them, the far hills their own valleys. It fades in over the map's last 20 %,
  so there is no lit ring. What is off the screen casts nothing there (checked against a 256-block map in the same
  views: the same shadows on screen; at 18:30 the map also had hills off screen to the west shading more of the land).
- **Contact shade (`CONTACT_AO`, `program/deferred_ao.glsl` run as Iris's `deferred`, `lib/ao.glsl`):** the light
  pass is now Iris's `deferred1` (`tools/wrappers.sh` maps the names; the file is still `program/deferred.glsl`). At
  half resolution (colortex9 RG16F: the raw term and the view distance), four samples on a disc in the face's plane,
  0.6 blocks, turned by the texel's place in a 4x4 tile; the light pass averages each 4x4 tile back with depth weights,
  so the result is smooth and stable without noise. It darkens the fill and torch light in inside corners and round
  plants' feet, the sun by 30 % of it; half on leaves; none on plants' own quads; out past 72 blocks. On top of
  Minecraft's corner shading.
- **Light through leaves (`lib/light.glsl` `welkinGlow`, `welkinLightGlow`):** a leaf's face turned from the sun takes
  the light that comes through the leaves (0.8 x a lobe 0.1 + 1.4 c^3 toward the sun x what passes), in its own colour
  twice over, warmed (a deeper yellow-green); back-lit plants the same at 0.5. What passes is the search's: the gaps
  whole, the rest exp(-0.8 x the blocks of leaves). A leaf block counts as a cube of small leaves turned every way: any
  face takes 0.45 of the sun plus 0.55 of its Lambert share (was a flat 30 % floor), where the map lets the light
  reach it; leaves take a penumbra at least 0.22 blocks wide, so a crown's own leaves are dappled softly instead of streaked by a
  grazing sun. With the map on, the canopy leak is 0.12 (was 0.2), so the floor's sun patches stand out between deep
  green shade.
- **Debug:** `DEBUG_VIEW` 13 the contact shade (blue: far shadow), 14 what passes the leaves (red) and the glow
  (green).
- **Cost (1080p, RTX 4060, MEDIUM, best second of 8 per sweep step in game):** the map with its filter 0.28-0.63 ms,
  far shadows 0-0.36 ms, the contact shade 0.08-0.2 ms (forest, village, valley, meadow); HIGH's 192-block map adds
  0.2-0.4 ms. Inside 3.5's shadow line plus the deferred line.

### The lighting review, round 1: deeper cool shade, a leading sun, gold at the low sun (2026-10-12)

The review of the step 2 bench (test worlds A and B against Complementary, Unbound + Euphoria and Hysteria) asked for
more sun against shade, cool blue shade instead of olive grey, deep pools under the canopy, a natural (not lime) light
through leaves, amber that lasts until the sun touches the horizon, and less clipping on pale stone. Measured from
here on with the pack's own cloud shadows off (`CLOUD_SHADOWS=0`) and the same view list in every run, because
Gloam's weather (and with it the cloud shadow over the near land) changes from run to run.

- **Sun and fill (`lib/tone.glsl`, `lib/sky/build.glsl skyLand`):** the noon sun 2.65 (was 2.4) in a neutral-warm
  white (1, 0.955, 0.85); the sky's fill 0.17 at noon (was 0.245), falling faster than the sun through the afternoon
  (`skyLandFillLevel`: 0.53 + 0.47 smoothstep(0.1, 0.92, h)^1.6), in a clearer blue; the ground's bounce 0.2 (was
  0.36), a less yellow ground. The sun's strength 0.5..1 of noon by height (was 0.6..1).
- **The low sun:** the last ten degrees take the low colour (amber at dusk 1, 0.42, 0.16; rose-gold at dawn 1, 0.46,
  0.29), so sunlit grass tips and crowns turn gold, not lime; the colour mixes over sun height 0.02..0.18.
- **Canopy shade (`lib/light.glsl canopyShade`):** by day, where the sky is partly hidden (sky light 0.35..0.97) and
  the sun does not reach, the fill is cut by up to 55 %; the canopy leak is 0.035 (was 0.12). Forest floors and a
  crown's inner leaves sink into deep, readable shade between the sun patches; nights unchanged.
- **Short-range bounce (`LAND_SELF_BOUNCE` 0.13):** a face takes back 0.13 x the sun on level ground x its own colour
  (half desaturated), more on walls than tops, under open sky: pale stone's shaded steps read a light grey, grass and
  dirt keep their deep shade.
- **Light through leaves:** the sun's colour times the leaf at half its saturation (was the leaf's colour twice over,
  tinted yellow), at most 0.7 x a narrow lobe 0.06 + 0.94 c^4 for leaves (0.32 for plants), widening at a low sun
  (0.4 + 0.6 c^2) so crowns seen from the side glow gold at dawn and dusk. Leaves take 0.36 + 0.64 of their Lambert
  share (was 0.45 + 0.55). Far plants (24..64 blocks) lose their glow and a quarter of their sun: no bright specks.
- **Tone:** a hyperbolic shoulder from 0.45 (sRGB 177; was an exponential from 0.52), so pale faces in full sun keep
  their texture at sRGB 220..240; the S-curve 1.26 (was 1.22). Glowing texels go through `welkinToneEmit`, which keeps
  their hue into white (a torch's flame stays yellow-orange).
- **Torches:** `rampBlockLin` to the 2.05th power (was 2.3) and a ninth stronger: wider, cosier pools.
- **Overcast nights:** the night's fill x (1 + 0.9 overcast), so a clouded night stays readable.
- **Sky and clouds:** the dusk glow's spread 1.25 (was 0.95) and its rings eased at both ends (no seam between a rose
  half and a grey half); a rose band opposite a low sun along the horizon; the clouds' sides and bases lit much more
  (light scattered through the body: base sky view 0.9 and diffuse 0.3, sides wrapped to -0.7), and by a low sun from
  below (peach bases toward the sun, rose away from it); the dusk palette's sunward blend a smooth lobe over the whole
  sky (was a 40 degree step).
- **Cost:** a few dozen ALU operations per pixel in deferred; the bench's frame times stayed within run-to-run noise
  (3.6..5.6 ms at 1600x900 MEDIUM).
