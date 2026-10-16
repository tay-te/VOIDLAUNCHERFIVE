# VOID Welkin and the new Gloam: design

Status: accepted, October 2026. Built from three read-only research passes: Iris 1.11.6+26.3 internals, a full inventory of Gloam, and a survey of the installed shader packs. Milestone M0 (the pack skeleton) is built; section 7 lists what has been decided since the proposal.

## 0. The decision in one paragraph

All world visuals move into VOID Welkin, a new shader pack for Iris, written from scratch. Gloam stops drawing anything. Instead it works out the world's state (seasons, wind, weather fronts, mist, haze, light) and hands it to the pack through a small bridge: one data texture, which needs no Iris mixin, and a set of `void_*` uniforms added by one small mixin. It keeps the whole soundscape and gains underground sound beds. Expanse is unchanged as the source of land data. LOD keeps drawing far terrain through the pack's Distant Horizons programs, and gets the bridge data with no changes. The look stays Minecraft, lightly stylized and of high quality: smooth gradients with gentle banding, directional sunlight and shadows, crisp pixel art only for the sun, moon, stars and voxel clouds, no visible dither and no TAA; since 2026-10-16 the heavy effects that make it beautiful (a luminous sky, a volumetric haze lit through the shadows, god rays and bloom), smooth and never a smear (section 3.2).

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

### 3.2 Look rules (set by the user on 2026-10-11, the lighting part updated on 2026-10-12, the heavy effects on 2026-10-16)

These replace the first rules (flat colour steps and a 4×4 Bayer dither everywhere), which the user dropped on
2026-10-11. On 2026-10-12, after three tuning rounds fell short of the reference packs, the user relaxed the
no-noise, no-TAA and no-volumetrics parts for the lighting alone (the last three rules below). Gloam's STYLE.md and
the staged wave-2 modules still describe the old rules; they are adapted as they are integrated.

- It must still feel like Minecraft: lightly stylized, not realistic, and of very high quality, on a par with good shader packs.
- Light, fog, haze, mist and sky are smooth gradients with gentle banding: a ramp may gather softly at a few levels (`softBands`, the `SOFT_BANDS` option), but never in flat steps with edges.
- No heavy or visible dither or checkerboard anywhere. The image's only screen noise is final's anti-banding: one 8-bit step of fixed triangular noise, which cannot be seen.
- Pixel-crisp detail only where it looks good: the sun, the moon, the stars and the voxel clouds. Everything that covers the world (haze, mist, water fog, wet ground) is smooth and is worked out at the pixel's own point, not snapped to a texel grid. Puddle outlines sit on the block textures' own texel grid, like texture.
- Clouds are voxel cells (12×12 near, doubling with distance, 4 blocks tall at a rim and up to 16 in 4-block tiers over a cloud's heart since the clouds rework), opaque, with hard edges and no dithered rims, lit as volumes inside that outline. No ray-marched soft volumes.
- Directional sunlight and shadows are the foundation of the quality: the sun lights faces by how squarely they face it, smoothly, and the shadow map carries it near, the far shadows and the terrain's light past it.
- Surface colours stay natural and real-world, with no glowing fantasy tints. Underground may be fantastical (glowworm light, coloured cave water).
- Each palette is small and lives in a pack file, keyed by haze kind, season and time. Palettes are control points: the colour runs smoothly between them, and every weight on them changes smoothly with the hour and the weather.
- **Lighting quality (2026-10-12):** soft shadows with natural penumbras (contact-hardening: crisp at a caster's foot, softer with its height), stronger ambient occlusion (corners, wall feet, the inside of crowns, the floor of a wood and the far land's hollows). None of it may read as fuzz, smear or a veil.
- **The heavy effects (2026-10-16, the user's pick after the depth A/B sheet):** "focus hard on the overall look, lighting, haze, bloom, all of the heavy effects that make this thing beautiful". Built properly and stronger than the prototypes: the **luminous sky** (a bright horizon band, a wide warm glow toward the sun, a deep zenith; the far land, the haze and the clouds' far fog sink into the same air), the **volumetric haze** (lit by the sun through the shadow map, the terrain's shadow line and the cloud shadows, thicker low down and toward the horizon, warm toward the sun, cool in shadow; the near 72 blocks clear, never more than 70 %), **god rays** (the shafts from that walk, bold at golden hour, a quarter at noon; the screen-space sun beams when the sun is in or near the view) and **bloom** (only light past white spreads; a core and a wide halo, energy-conserving; the open sky a fifth of the source, a cloud's body a share and its silver lining all of it, and the frame's flat mean kept off the halo, so it glows round bright things and never veils the frame). Push toward Complementary's and Euphoria's warm, luminous air while staying less gold than Complementary. Not picked, and left as they were: the prototypes SHADE, AIR, BANDS=0 and RELIEF (the open shade, the aerial perspective and the banding stay).
- **Noise only inside a lighting term, and never on screen:** a lighting term (the shadow's reach, the occlusion, later the shafts) may be sampled with the 4×4-tiled lighting noise (`lib/noise.glsl`) only if a depth- and normal-aware resolve (`lib/denoise.glsl`; optionally temporal accumulation with reprojection and a neighbourhood clamp, `TEMPORAL_LIGHT`, off by default) smooths it until it is grain-free in a single still. Every such term must pass the grain check (`audit/grain.py`: flat land patches within +10 % of the term off, or of its noise-free reference, `SHADOW_REFERENCE` and `AO_REFERENCE`), in every bench view. Terms may be worked out at half resolution (the occlusion, the shafts) and brought back to full resolution depth-aware.
- **Textures are never filtered:** colour and texture never pass through a resolve, a blur or a history; they stay pixel-crisp. The bloom only adds light past white (a texel's pattern stays), the haze and the god rays are smooth air laid over the image. No TAA over the whole image, no blur, no fuzzy ray-marched volumes; the pixel-crisp sun, moon, stars and voxel clouds stay.
- The budgets of section 3.5 hold, with the lighting set's own lines.

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

**Measured after the phase 2 merge (2026-10-16, RTX 4060, 1600x900, MEDIUM, test world A, 8-second on/off sweeps in
one run; the other game slot was in use, so about +-0.4 ms):** the heavy effects together 0.67 ms on the plain at noon,
1.33 ms on the steppe at 17:00 and 1.51 ms facing the sun on the plain at 17:30 (their lane read 0.8, 1.09 and 0.89 ms
on cleaner readings), so at a low sun they sit at or a little over their 1.2 ms line; the bloom alone within the noise;
the clouds 0.73 to 1.1 ms (with their shadows on the land), over their 0.6 ms line as before the rework; the land
reflections within the noise on these views (0.35 to 1.05 ms over water in the defects lane's sweeps). Frames: plain
12:00 7.1 ms, steppe 17:00 7.1 ms, the plain facing the sun 8.0 ms; shore and valley views 9.3 to 10.4 ms, wmist0 11.4 ms.

The lighting set the user allowed on 2026-10-12 (soft shadows on a resolved buffer, the far and terrain shadows,
ground-truth ambient occlusion, later the sun shafts) was given to the user as about +0.8 to 1.0 ms at MEDIUM, for a
total of about 4 to 5 ms; LOW stays cheap (no shadow map, no occlusion, no terrain pass).

| Pass | Budget |
|---|---|
| Near gbuffers | about 0.3 ms over vanilla |
| `dh_terrain` | 1.0 ms or less |
| `dh_water` | 0.3 ms or less |
| Shadow chain: the shadow map (`shadow`, `shadow_solid`), the raw terms (`deferred2`) and the resolve (`deferred3`) | 1.2 ms or less (raw 0.45, resolve 0.2); off on LOW |
| The terrain's light (`prepare3`) | 0.15 ms or less; off on LOW |
| Ambient occlusion: the raw GTAO (`deferred`) and its resolve (`deferred1`), both at half resolution | 0.4 ms or less (raw 0.3); off on LOW |
| Light pass (`deferred4`) | about 0.4 ms |
| Atmosphere composite | about 0.3 ms |
| Voxel clouds (M3) | 0.6 ms or less |
| Far weather composite | about 0.2 ms |
| Sun shafts (`deferred5` at half resolution, resolved in composite2; gated to a low sun, nothing from about 37 degrees up) | 0.3 ms or less while active |
| Sky table and final | about 0.2 ms |
| The heavy effects (2026-10-16): the luminous sky, the volumetric haze (`deferred5`'s walk, which also gives the shafts, resolved in composite2), the sun beams (`composite3`) and the bloom (`composite3` source, `composite4` halo, laid on in `final`) | 1.2 ms or less together (the sky and haze about 0.6 of it); LOW keeps only the luminous sky |
| Water's land reflections (`WS_WATER_SSR`, on at MEDIUM since the defects pass) | 0.5 ms or less over open water |
| **Total** | **about 5.5-6.5 ms MEDIUM, about 2 ms LOW** |

**Measured after the A + B merge (2026-10-13, RTX 4060, 1600x900, MEDIUM, idle stretches only):** plain 12:00 frame
5.1-5.2 ms, shadow chain about 1.3 ms, occlusion about 0.45 ms; valley 05:30 (the worst case) frame 7.2 ms, shadow
chain about 1.9 ms. Over budget at a low sun.

**Measured after the balance step (2026-10-13, the same machine, idle GPU, best and mean seconds of 8-second runs,
interleaved repeats):** the occlusion's resolve now runs at half resolution and the shadow's resolve skips the windows
that hold one value; the occlusion is about 0.3-0.4 ms (was 0.64-0.70), inside its line; the shadow chain is about
1.75 ms at valley 12:00 and 2.1-2.2 ms at valley 05:30 (PCSS and its resolve about 0.55 of it, was about 0.93; the
rest the map's geometry, the far rays, the contact rays and the terrain's lookup), still over its 1.2 ms line; the
lighting set together, 2.2-2.6 ms, is about +0.9-1.0 ms over round 3's light, inside the +0.8-1.0 ms the user was given
and the 2.0-2.6 ms total of TECHNIQUES 2.11. Of the cuts below, the 1536 map saves 0.1-0.15 ms at valley 05:30 (the
merge's 0.5 was read on a contended GPU) and 8 + 8 taps nothing measurable, so neither was taken; the map's geometry
(leaves at a low sun) is the cost left, and cut 3 is the one that would move it.

**Measured after the C + D merge (2026-10-13, the same machine, no other game running, 8-second means, on/off sweeps
in the same run; about 0.2 ms either way):** plain 12:00 frame 5.4 ms, shadow chain about 1.3 ms, occlusion about
0.4 ms, sun shafts about 0.05 ms (gate shut), foliage (canopy and light through leaves) and air (forward scatter,
aerial perspective) within the noise; valley 05:30 frame 7.6 ms, shadow chain with the shafts about 2.4 ms (the
shafts about 0.24 of it, inside their line), occlusion about 0.45 ms, foliage about 0.1 ms. Wave 2 adds about 0.05-0.2
ms at noon and about 0.35-0.4 ms at a low sun, so the whole lighting set is over the +0.8-1.0 ms the user was given at
a low sun; the shadow chain is still the part over its line. The cuts, in this order:
1. the map at 1536 on MEDIUM (about 0.5 ms at valley 05:30, nothing at noon);
2. PCSS at 8 + 8 taps (10 filter taps now);
3. leaves culled from the shadow pass beyond about 64 blocks;
4. the occlusion's `AO_SUN_FAR` 0, or `AO_FAR` cut from 4096 toward 1024 blocks;
5. creatures casting only near (already within half the reach).

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
| Look | The rules of section 3.2 are absolute. Since 2026-10-11: Minecraft-like and lightly stylized, very high quality; smooth gradients with gentle banding; no visible dither anywhere (only invisible anti-banding); pixel-crisp sun, moon, stars and voxel clouds; directional sunlight and shadows first; no TAA or fuzzy ray-marched volumes; natural surface colours. Since 2026-10-16 the heavy effects are wanted (a luminous sky, volumetric haze, god rays, bloom from light past white), smooth and never a veil. (Before: flat colour steps, Bayer dither and the 16-pixel grid for everything covering the world.) |

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

### The lighting review, round 2: grazing shadows, lake beds, sky-tinted shade, amber low sun (2026-10-12)

The round 1 review ranked the low sun first (05:30 and 18:30 grey-olive), then neutral dusk shade, muddy shaded
stone, black step pockets on lit cliffs, a slightly flat and bright noon, and a shore whose bed vanished at dawn.

- **What the low sun really does:** a debug pass (`DEBUG_VIEW` 12, with `SHADOWS=false` beside it) showed the plain's
  meadow at 05:30 and 18:30 (sun about 4 degrees up) genuinely in the long shadows of the birch wood and the eastern
  rise, as Complementary draws it too. So the round lights what the low sun does reach in amber and makes the shade
  between luminous and sky-coloured, rather than lifting the shadows.
- **Receiver-plane depth (`lib/shadow.glsl shadowPlaneGrad`):** every tap of the caster search and of the 4x4 edge
  filter compares against the receiver's own plane where it lands (the plane's map-space depth gradient from two small
  steps along the face, held to cot 2.5 degrees), so a grazing light never lets level ground shadow itself.
- **Under water:** the light is bent at the surface (at most 49 degrees off the vertical). A point under water is
  looked up just under the surface, found up the bent ray (straight along the light line where it leaves through
  water, else by five probes 1..10 blocks up the bent ray, only where the sky light is under 0.95); the water's tint
  fades over the bent path; a bed's face takes the bent, steeper light (up to 4x a grazing facing). The dawn lake's
  bed was black under the whole surface (the straight 4 degree line ran into the bed's own rise toward the shore).
- **Shade (`lib/light.glsl`):** an upright face takes 0.6 of the sky's fill (was 0.5); the short-range bounce
  (`LAND_SELF_BOUNCE` 0.3) is near neutral (albedo a quarter, the sun's colour half), a fifth of it on tops, and only on
  pale surfaces (smoothstep 0.1..0.45 of the linear albedo luminance); the ground bounce less warm; corner shading
  to the 1.8th power, never under 0.2 of the fill. Shaded pale stone reads a light cool grey, dirt banks stay deep.
- **Low sun (`lib/sky/build.glsl`):** the fill holds 0.73 of noon over the last degrees (+0.2, fading out by h 0.3)
  and takes the dome's own colours (zenith to horizon, more horizon at dawn), up to 60 % within h 0.1..0.35; the bright
  sky round a low sun lights what faces it at 0.85 (was 0.45), a quarter of it on level ground; the last degrees of
  sun a deep amber (dusk 1, 0.42, 0.11; dawn 1, 0.46, 0.15; over h 0.03..0.22) at 0.4..1 of noon by height.
- **Tone (`lib/tone.glsl`):** a colour past white comes back three quarters by losing brightness and a quarter by
  losing saturation (was all saturation), the hue kept 0.92 (was 0.8), saturation 0.95: a white trunk in the low sun
  reads peach-amber, not cream.
- **Noon:** the high sun 6.5 % less, the fill 0.155 and bluer, the morning sun warmer, leaves' foliage 14 % less
  saturated in deferred, the canopy's shade 0.68; one cloud deck's shadow 0.8 of the sun (was 0.74).
- **Measured (test world A, `CLOUD_SHADOWS=0`, pinned front), round 1 -> round 2, Complementary in brackets:** plain
  05:30 median 28.6 -> 32.8 (32.9), R/B 1.87 -> 2.19; plain 18:30 29.1 -> 33.0 (32.8); plain 12:00 143.4 -> 136.8
  (139.7), sun to shade 4.80 -> 4.90; village 12:00 154.1 -> 148.0; valley 12:00 71.8 -> 88.7 (99.6), R/B 0.86 -> 0.97;
  valley 16:30 74.5 -> 101.2 (77.5), contrast 7.78; village 18:30 30.4 -> 33.9 (37.5); nights unchanged.
- **Cost (idle machine, MEDIUM, 1600x900, shadows on minus off):** plain 0.44..0.50 ms, village 0.64, shore at dawn
  1.04 (with the surface probes), valley at dawn 1.26 ms (the only view over the 1.2 ms line).

### The lighting review, round 3: the leaf-glow bug, a strong low sun over directional shade, light in the crowns (2026-10-12)

The round 2 review ranked the flat low sun first (plain 18:30 sun-to-shade 1.90 against Complementary's 2.66), then
lime crowns at the golden hour, pale cyan-white glowing leaves at night, weak village lamps, a dark flat forest, open
ground's noon contrast under 5:1, cream stone and olive-yellow dry grass, a yellow 08:00, the shore's water, the shadow
pass's cost at a low sun and at night, and a salmon dawn deck.

- **The leaf-glow bug (`lib/shadow.glsl`):** with the receiver plane's slope in the caster test, a caster's mean depth
  could land past the point's own, so the "blocks above" went negative and the light through leaves,
  `exp(-0.8 * depth)`, grew far past 1 (`DEBUG_VIEW` 14 showed it saturated on exactly the glowing faces). Far crowns
  seen toward a low sun glowed lime; seen toward the summer's low full moon they glowed cyan-white. The depth is held
  at 0 or more and what passes at 1 or less; under the moon the light through leaves is 0.35 of the sun's share.
- **Low sun:** the sun keeps 0.6 of noon down to the horizon (was 0.4) and goes out only as the disc meets it; its
  last degrees are redder (dusk 1, 0.36, 0.07; dawn 1, 0.39, 0.12; the gold 1, 0.63, 0.28 and 1, 0.64, 0.40) so pale
  stone reads amber, not peach-cream. The sky's fill comes from above while the sun is low: an upright face takes 0.36
  of a top's (0.5 by day, 0.6 before), the tops keep about round 2's level (+0.31 over the last degrees on a smaller
  `LAND_FILL`), the sun-side glow is 0.6 (was 0.85) and 0.12 on level ground (was 0.25), and the dusk shade takes less
  of the dome's colour (0.4) and more of its zenith: cool blue-lavender against the amber.
- **Plants and leaves (`lightFillUp`, set by deferred):** plants' crossed quads take the fill as from above (0.7 of the
  way) and 1.45x, two-sided blades: tufts read light in the long shadows, as dry grass does at dusk. Leaves take 0.35
  of the way. A leaf takes the sun at 0.5 + 0.5 Lambert (was 0.36 + 0.64), and a leaf in the shade of the leaves just
  before it takes half of what passes them, so a crown's sunward side glows a block or two deep while its heart and
  the floor stay in deep shade. The canopy's fill cut is 0.8 (was 0.68).
- **Day:** `LAND_FILL` 0.126 (was 0.155), `LAND_SUN` 2.7 (was 2.65), the sun a tenth stronger from about 15 to 45
  degrees (08:00 and 16:30 as bright as noon), the afternoon fill falling a little faster (pow 2). The noon sun is a
  cleaner white (1, 0.965, 0.90), the morning a fresh pale gold (1, 0.89, 0.72), the afternoon (1, 0.84, 0.52).
- **Tone (`lib/tone.glsl`):** a colour past white comes back nine tenths by brightness, a tenth by saturation (was
  three quarters / a quarter); highlights give up up to 30 % saturation from luminance 0.45 to 0.9
  (`LAND_HIGHLIGHT_DESAT`), scaled by the sun's height (none in the low sun), so sunlit pale stone is white at noon
  and amber at dusk.
- **Torches:** a third stronger, falloff to the 1.9th power (was 2.05): the lamp glow on walls at dusk and the torch
  street are back (35.6 against round 2's 33.1 and Complementary's 35.1), still orange and pooled.
- **Shadow cost:** under the moon the map reaches half as far (`SHADOW_NIGHT_REACH`; the shadow pass drops blocks
  further than that plus 24 blocks, by the block's centre) and is looked up with one hardware compare, no search or
  filter. By day a penumbra up to 1.5 texels wide is filtered by a 2x2 grid of hardware taps (`shadowPcf4`), the 4x4
  only past it.
- **Clouds:** the low sun's under-light on the decks fades away from the sun (peach over its side, rose overhead,
  lavender-blue away; the brightest rim toward it), so a dawn deck is graded, not one salmon slab.
- **Measured (`CLOUD_SHADOWS=0`, pinned front; round 2 re-shot in the same conditions -> round 3, Complementary in
  brackets; worlds A and B):** plain 18:30 sun-to-shade 1.91 -> 2.42..2.62 (2.66), plain 05:30 2.01 -> 2.54 (2.68);
  village 18:30 2.91 -> 3.95 (2.99); plain 12:00 4.87 -> 4.93, median 137 -> 139.5 (139.7); plain 16:30 5.99 -> 6.35,
  village 16:30 6.31 -> 6.90; plain 08:00 median 115.8 -> 123.2, R/B 3.41 -> 3.01; plain 16:30 R/B 4.24 -> 3.91;
  valley 08:00 R/B 1.34 -> 1.20; torch street 33.1 -> 36.0; nights within 1 of round 2. Valley 18:30 stays about 2.8
  (its p5 is the falls' water, P7). The forest regrows differently from run to run, so its figures are not comparable.
- **Cost (world B, one game at a time, MEDIUM, 1600x900, frame time minus a run with `SHADOWS=false`):** valley 05:30
  1.21 -> 1.07 ms, valley at night 0.56 -> 0.33 ms, shore 05:30 0.89 -> 0.91 ms: all under the 1.2 ms line. Far
  shadows cost 0.37 ms of the valley's dawn. Round 2's 1.4..2.2 ms readings were taken while another game shared the
  GPU.
- **Not done here:** the shore's water (Fresnel sky reflection, a glitter path, the 18:30 murk, the gorge's opaque
  blue) and the hard white mist band on the dawn lake's horizon belong to P7 and the mist; the noon "grey-white wash"
  at the top of the frame is the cloud decks' bases, not the sky's zenith (the dome deepens to 131, 171, 233 there).

### Lighting techniques, workstream A: soft shadows, the shadow buffers and the terrain's light (2026-10-12)

The user allowed noise inside a lighting term that a depth-aware resolve smooths until no grain shows (TECHNIQUES.md),
then judged the 4K vistas "still pretty flat from time to time": worst in the middle and far distance, past the shadow
map, where the steppe, the woods and the hills read evenly lit.

- **The shared noise and resolve (`lib/noise.glsl`, `lib/denoise.glsl`):** interleaved gradient noise, a 4x4-tiled R2
  lighting noise (a 4x4 window always holds the tile's sixteen values once), the Vogel disk; a plane- and
  normal-weighted 4x4 resolve with the window's range, reprojection, the history clamp and a material-aware history
  weight. A pixel the plane test leaves nearly alone (a leaf's mip edge) takes its neighbours at a looser depth test,
  so its own noisy value never shows as a speck. Workstream A owns both; the others include them.
- **The pass layout:** `prepare3` the terrain's light (`colortex15`), `deferred` the occlusion (B), `deferred1` the
  shadow's raw terms (`colortex10`), `deferred2` the resolve (`colortex11`), `deferred3` the light; forward programs
  keep the fixed-grid lookup (`shadowSampleW`).
- **Soft shadows:** PCSS on a noise-turned Vogel disk (8 search taps over the penumbra of casters up to 32 blocks
  above, the casters' height averaged harmonically so the nearest rules and a stem's foot stays crisp; 10 compare
  taps with the receiver plane; 0.025 blocks of half width per block, 0.04 on Soft; at least 1.5 map texels, at most
  28). MEDIUM's map is 2048 at 128 blocks with the distortion 0.8 (HIGH 3072 at 192). Contact rays (8 steps, out to
  48 blocks) and the far ray (14 steps) start where the noise puts them; everything goes through the resolve.
  `TEMPORAL_LIGHT` exists (reprojection, clamp, 0.5 on swaying leaves) but ships off: the spatial floor alone passes
  the grain check and the camera-pan shots showed no gain worth its ghosting risk on swaying leaves.
- **The long low-sun shadows (`SHADOW_LONG_FADE`, 0..4, default 2):** the round 3 "low-sun bug" at plain and valley
  18:30 was measured to be the shade of casters 25-170 blocks off, not self-shadowing: in the last degrees before
  sunset a caster's shadow thins from 56 to 144 blocks along the light (two fifths gone on the default), keyed on the
  casters at the point itself (no noise), so the golden hour reaches the horizon.
- **The terrain's light past the map (`lib/terrain.glsl`, `TERRAIN_SHADOWS`, `TERRAIN_SLOPE`):** the LOD's land is
  columns whose every top faces up, so a hillside turned from the sun took the same sun as one turned to it, and
  nothing off the screen cast a shadow. Once a frame `prepare3` reads Gloam's near land tile (N0, the ground every 16
  blocks, 4 km round the camera) and works out for every cell (a) the shadow line toward the light: the height above
  which the column sees it, the max over a march out to 2 km of ground(t) - t tan(elevation), with the caster's
  distance for the penumbra (0.75 + 0.03 per block); the relief counts 1.5x while the sun stands above 0.35, easing
  to the true relief by 0.15 (a stylized hill shading); a second line of only the casters past the map's reach, for
  the points inside it; and (b) the land's Sobel slope. `deferred1` lets a point (never taken under the tile's own
  ground) under the line into shade over a soft edge, bilinear between cells, no noise. The light pass turns a top
  face's sun by the hillside it stands on, its slope steepened `TERRAIN_SLOPE` % (default 300), at most 3x, fading
  in from 40 to 160 blocks. The terrain's shadows give way in the last degrees (sun height 0.25 -> 0.08; the
  golden-hour rule; the slopes' own light carries the form then). Without Gloam every cell reads lit and level.
  `DEBUG_VIEW` 21.
- **Cost (RTX 4060, 1600x900, MEDIUM; frame times noisy while another game ran):** the terrain pass (65 536 cells,
  about 60 steps of 4 fetches) and its lookups were under the run-to-run noise (on/off within about +-0.3 ms in the
  three vistas); estimated 0.05-0.15 ms. The PCSS, contact and far rays and the resolve as measured for A2-A3.
- **Open:** casters off the screen past 2 km cast nothing (Gloam's 1 km F0 tile could carry the same lines further);
  the far land's sky fill does not yet see its valleys (a sky-openness term from the same heights would be the
  ambient workstream's); forward programs keep the fixed grid.

### Lighting techniques, workstream B: ambient occlusion and the sky's light (2026-10-12, branch `welkin-ambient`)

TECHNIQUES.md 2.4 and 2.5, and the user's verdict on the 4K vistas ("still looks pretty flat from time to time"):
the occlusion and the sky's light now reach the middle and far land and the LOD's.

- **Sky irradiance (`lib/sky/irradiance.glsl`):** prepare integrates the dome (128 directions, linear light) into
  nine spherical-harmonic coefficients in the table's row 13, normalised per channel to an upward face; the light
  pass takes the table's tuned fill times that irradiance along the bent normal (half the dome's hue by orientation,
  all its brightness). It replaces `skyW`, `sunSideGlow` and most of the face shading (0.45 of it stays by day, 0.3
  at night). The low sky's last degrees count at half (`SKY_IRR_HORIZON`: land and trees hide most of them).
- **Ground-truth ambient occlusion (`program/deferred_ao.glsl`, run as `deferred`):** half resolution, 2 slices x 5
  steps a side (`AO_QUALITY` 2: 3 x 6), turned by workstream A's 4x4-tiled lighting noise and resolved grain-free in
  the light pass by `lib/denoise.glsl`'s 4x4 plane- and normal-weighted window (`lib/ao.glsl lightAoFetch`). 1.75
  blocks (3 in crowns, 1 in tufts, plants taken as facing up), a thickness guess, the multi-bounce fit on the
  albedo, the bent normal into the sky's irradiance. On the fill and the ground's bounce fully, the torch light by
  half, never the sun inside the shadow map. Minecraft's corner shading stays as a 0.35 partner. `colortex9` is
  RGBA16F (visibility, bent normal's tangent parts, view depth).
- **The middle and far land:** the radius never looks narrower than `AO_WIDE_PX` (40 px at 900 lines) on screen, so
  past about 28 blocks it grows with the distance, and the pass reads the LOD's depth where the chunks drew nothing:
  a hillside's terrace steps at a hundred blocks, the floor between a far wood's crowns and a valley's sides take
  occlusion at their own scale, out to `AO_FAR` 4096 blocks. Where a block is under about 7.5 pixels tall the
  visibility is taken relative to open level ground (0.5 + 0.5 n.y), so 1-block terrace risers do not turn into
  rows of dark dashes. `AO_SUN_FAR` (75 %): past the map and on the LOD the sun outside the cone of open sky the
  visibility and bent normal give is taken off softly (ambient aperture lighting, Oat and Sander 2007).
- **The rebalance:** `LAND_FILL` 0.16 by day (0.126 while the sun is low), `LAND_SELF_BOUNCE` 0.05, `AO_STRENGTH`
  1.25, `AO_FLOOR` 0.12.
- **Debug:** `DEBUG_VIEW` 18 occlusion, 19 bent normal, 20 sky irradiance, 22 the far land's own shade.
- **Measured (world B, `CLOUD_SHADOWS=0`, pinned front; round 3 -> B):** valley 12:00 calcite lit/shaded wall
  206/111 -> 196/70 (2.8:1, was 1.9:1), shade bluer (R/B 1.34 -> 1.13); valley 18:30 wall toward the afterglow
  R/B 2.95 against 0.86 away; plain 12:00 median 139.7 -> 139.8, p95/p5 4.91 -> 5.0; nights and the torch street
  within 1.5 levels of median. Valley medians fall 15-35 % (the shaded walls are meant to be deeper). The grain
  check passes on every patch (consecutive on/off runs; the plain and forest geometry differs from run to run).
- **Cost (contended GPU, frame time with the occlusion on minus off):** about 0.3-0.7 ms at MEDIUM, 1600x900. B
  expected most of it to be the full-resolution 4x4 resolve in the light pass; the merge measured the resolve's own
  share at about 0.1 ms or less (`AO_RESOLVE=0`), so most of it is the raw GTAO pass (see the merge below).

### The lighting merge: workstreams A and B on `welkin` (2026-10-13)

`welkin-shadows` (A, head d08055fb) merged first (27f4a1ee), then `welkin-ambient` (B, head 9fd03855; its copy of A's
shared libraries, dbe1cd78, is byte-identical to fa75074e) onto A's pass layout (9754ae93).

- **Conflicts resolved (B onto A):** `lib/denoise.glsl` A's copy (B's copy was A's fa75074e; A's later
  `resolveSpatial4x4Loose` only added); the options in `lib/settings.glsl`, the lang file and `shaders.properties`
  both blocks, with round 3's `CONTACT_AO` renamed `AMBIENT_OCCLUSION` (B) in A's profiles and screen, A's maps (2048
  MEDIUM, 3072 HIGH) and terrain options with B's `AO_QUALITY` per profile; `DEBUG_VIEW` A keeps 15-17 and 21 (the
  terrain's light), B keeps 18-20 and its far land's own shade moves from 21 to 22; `program/deferred.glsl` A's
  `lightShadowFetch` reach first, then B's occlusion fetch and its `AO_SUN_FAR` cone after it; buffers, README and
  DESIGN both workstreams' lines and sections.
- **The sun past the map, counted once:** B's cone now fades in over A's `farShadowNeed` (the map's own fade, half
  reach under the moon) instead of its own copy, and takes the darker of the cone and A's far rays and terrain shadow
  (`min`, not the product), so a far penumbra is not darkened twice.
- **The occlusion's resolve moved into `deferred2`:** with the shadow map on, `deferred_resolve` resolves `colortex9`
  once into `colortex12` (RGBA16F: visibility, the bent normal's tangent parts) beside the shadow's, and the light pass
  reads it with one fetch (`lib/ao.glsl aoResolveAt`, `WELKIN_AO_IN_RESOLVE`); without the map (LOW, a custom mix, the
  other dimensions) `deferred2` does not run and the light pass resolves it itself, the same way. The resolve stays at
  full resolution (each pixel weighted to its own plane and normal, so occlusion never bleeds across a block's edge);
  its own share measured at about 0.1 ms or less on an idle GPU (0.1-0.25 contended), not the bulk of B's cost, so
  the half-resolution resolve with an upsample was not built.
- **Grain-check references:** `SHADOW_REFERENCE` (A) and the new `AO_REFERENCE` (the GTAO slices in `aoSlices()`,
  every noise value of the tile averaged per texel). `AO_RESOLVE=0` (timing only, on no screen) skips the occlusion's
  resolve.
- **Checked on the merged state:** every `live.sh` variant compiles (A's, B's and the merge's runs are now in
  `live_runs.txt`); load runs in both test worlds log no shader errors; the bench (world A: plain, village, torch
  street, valley at their hours; world B: plain, village, valley, shore) matches A's and B's own after-shots within
  the run-to-run noise, with B's deeper valley walls and A's golden hour at 18:30 both present (the forest views are
  not comparable: the trees regrow). Grain: the shadows against `SHADOW_REFERENCE` pass (plain 16:30 term p90 1.22,
  forest 12:00 1.19, which failed at 1.29 on A's branch); the occlusion's final image against `AO_REFERENCE` passes
  (valley 12:00, plain 12:00 and 18:30: land medians 1.00, p90 at most 1.08), and its term alone (`DEBUG_VIEW` 18)
  passes in the valley (1.09) but sits just over A's stricter term limit on the plain's grass (p90 1.27 against 1.25:
  faint blotches in a block's inside corner that the final image does not show).
- **Cost:** RTX 4060, 1600x900, MEDIUM defaults, 8-second frame-time means, on/off sweeps in the same run; only the
  stretches with no other game on the GPU are counted (another session's game tests kept starting mid-run).
  Plain 12:00: the whole frame 5.1-5.2 ms; the shadow chain (map, raw, resolve; `SHADOWS` on minus off) about 1.3 ms;
  the occlusion (`AMBIENT_OCCLUSION` on minus off) about 0.45 ms, its resolve's share within the noise; the 1536 map
  saves nothing measurable there. Valley 05:30, the worst case: the whole frame 7.2 ms; the shadow chain about 1.9 ms
  (round 3's shadow pass: 1.07); the 1536 map saves about 0.5 ms. The terrain pass stays under the noise. So the
  shadow chain is over its 1.2 ms line at a low sun (and about 0.1 over at noon), and the occlusion about 0.05 over
  its 0.4 ms line; the lighting set costs about +1.0-1.5 ms over round 3, against the +0.8-1.0 ms the user was told.
  Nothing was cut in the merge; the cuts, in order (section 3.5), are for the timing step with an idle GPU.

### The balance step after wave 1 (2026-10-13)

The A + B balance (TECHNIQUES 3.2 step 5) re-benched both worlds (`-Pgloam.test.front=0.38`, `CLOUD_SHADOWS=0` for the
tone figures) and the user's three vistas (steppe 17:00, countryside 06:30, mesa forest 16:48, at 1600x900 with the LOD
at its default), and took the cost lines to an idle GPU. The user's verdict on round 3's 4K shots was that the light
still reads flat from time to time, most in the middle and far distance; the vista changes answer that.

- **Fill, occlusion and contrast:** `LAND_FILL` 0.16 to 0.14 (noon's open shade back in the 25..35 band),
  `LAND_CONTRAST` 1.26 to 1.32, the sun's morning and afternoon shoulder 0.1 to 0.14 (`LAND_SUN_SHOULDER_HOURS`), so
  16:30 passes 7:1 on the plain and in the village without losing its brightness. `AO_STRENGTH` stays 1.25: in the
  sweeps it moved the figures by less than a level.
- **The gorge's pale walls:** `LAND_SELF_BOUNCE` 0.05 to 0.15, in a near-neutral colour (`LAND_SELF_BOUNCE_HUE` 0.25):
  B's deeper corners had taken the valley's median a fifth to a third under round 3's; shaded calcite now reads a
  light cool grey again under its sunlit tops, the steps' grading kept (valley 12:00 median 71 to 88, round 3 87).
- **Clear near air (`HAZE_NEAR_CLEAR`, 160 blocks):** the land's murk (not the clear air or the rain) sets in over the
  first 160 blocks or so (its optical depth times d^2 / (d^2 + D^2) at both ends of the ray), so a humid forest's near
  and middle land keep their sun and shade under the haze and only the distance takes the air's colour (mesa forest
  16:48: land p95/p5 3.4 to 5.6, the darkest 5 % 57 to 34). The far land and the sky are as they were.
- **Far woods' form (`FAR_LEAF_FORM`, 75 %):** past the shadow map (and on the LOD's land) a leaf face takes the sun
  three quarters by its own facing instead of the cube-of-leaves half on every face, since no map shades a crown's far
  side there: the far acacias and oaks keep a lit top and a shaded side.
- **Cloud shadows deeper** (one deck 0.8 to 0.62, two 0.69 to 0.52, three 0.6 to 0.45): about a third darker on open
  ground under one deck, so passing clouds lay patches of shade over the middle and far land.
- **Cost:** the occlusion's resolve moved to its own half-resolution pass (`program/deferred_aoresolve.glsl`, Iris's
  `deferred1`, into a half-size `colortex12`; the light pass takes it up with a 2x2 upsample weighted to the pixel's
  own plane), and the shadow's resolve returns at once where its 4x4 window holds one value (exactly what the weighted
  resolve would give). The pass layout is now `deferred` occlusion raw, `deferred1` its resolve, `deferred2` the
  shadow's raw terms, `deferred3` their resolve, `deferred4` the light (`tools/wrappers.sh`). Measured in section 3.5:
  the occlusion is back inside its line; the shadow chain is still over its own at a low sun, the set as a whole
  inside what the user was told.
- **Checked:** every `live.sh` variant compiles (the step's runs are in the balance step's `runs_extra.txt`); every
  game run logged no shader errors; the grain check passes for the shadows (plain 12:00 and 16:30, valley 12:00 and
  16:30) and for the occlusion's final image everywhere; its term alone passes in the valley (p90 1.06) and sits at the
  stricter term limit on the plain's grass at noon (1.252 against 1.25; the merge's full-resolution resolve read 1.27).
- **Not in this step:** the clouds' own look (flat grey undersides; P4's follow-up), water (P7), the forest's numbers
  (the trees regrow), and the options' generator table (`options.py`), which still lacks wave 1's and this step's
  options.

### Lighting techniques, workstream C: foliage and forest light (2026-10-13, branch `welkin-foliage`)

TECHNIQUES 3.3 and 2.6, plus the user's verdict on the vistas (the far woods read as a flat green carpet). Tested in
world A only; the forest bench is now repeatable (below). The leaf and plant rules live in `lib/foliage.glsl` (moved
out of `lib/light.glsl` first, as a pure move) and in the marked leaf section of `lib/shadow.glsl`.

- **A repeatable forest.** `-Pgloam.test.bgThreads=1` (gloam's `build.gradle`) runs the game's background executor on
  one thread, so neighbouring chunks' features land in the same order and the forest grows the same trees every run:
  two runs shot the same grove, differing only by sway. The bench gains `grove` (deep under the valley forest's
  canopy, 3192,97,548) and `crowns` (40 over it); both are outside the full set (`-Pgloam.show.bench=survey` with
  `gloam.show.only`). The old `forest` place is a forest edge in the single-threaded world. Far places (the vistas'
  LOD land) can still differ run to run: the LOD generates on its own threads.
- **Leaves take the sun as a cube of small leaves** (`leafFacing`): 0.15 + 0.85 of a Lambert wrapped by 0.6, so a
  crown's top under a grazing sun still takes about half the sun, its sunward side all of it, and its far side 0.15.
  Round 3's half on every face is gone; leaves keep half again of Minecraft's face shading on their fill
  (`LEAF_FACE_SHADE`), so crowns read block by block.
- **The lifts are gone.** `max(vis, 0.5 * pass)` became `max(vis, 0.3 * exp(-0.75 t))`, t the blocks of leaves before
  the leaf at its own map texel (`shadowThickAt`, also under the moon); `lightFillUp` on leaves 0.35 to 0.15.
- **Light through leaves** (`welkinGlow`): a Henyey-Greenstein lobe (g 0.6, 0.35 at a low sun) plus 15 % from every
  side, times that per-pixel transmission (worked out in the light pass, noise-free, so the resolve keeps its shortcut over
  uniform windows; `colortex10.g` stays the search's pass, for plants), in the sun's colour
  times the leaf at round 1's desaturation. Thin crown edges glow warm toward the sun; hearts do not. `LEAF_TRANSLUCENCY`
  scales it.
- **The canopy from the map.** The shadow pass marks leaf texels (`shadowcolor0` blue 0; solid-drawn leaves too). A
  point lies under a canopy where a leaf is the first thing the light meets before it (more than half a block off) and
  its sky light is cut (`shadowCanopy`; an open meadow keeps 15 even in a wood's long shadow). The map alone cannot
  say it at a low sun (it keeps only the wood's sunward edge), nor can the sky light (roofs, cliffs). Under a canopy:
  the low sun's long fade is taken back out (`shadowLeafAdjust`; the forest floor at 18:30 no longer glows orange), the
  sky's fill is cut by 0.8 and the diffuse leak through the leaves is 0.012 of the sun (`canopyShade`, `canopyLeak`;
  the sky-light key stays past the map). `deferred_shadow` writes it as `colortex10.b = -1 - canopy` in the air (the
  leaf thickness that sat there is read by the light pass itself); the leaf mark is only read where the sky light is
  cut; `FOLIAGE_CANOPY` 0 turns it off. `DEBUG_VIEW` 14 shows it in blue.
- **Far woods.** The far shadows' screen ray took nearly every far crown's top for shaded (its thickness test cannot
  tell a crown in front of it on screen from one between it and the sun): on leaves it now acts 15 % on tops and half
  on sides (`shadowLeafFarRay`, before the terrain's shadow, which still darkens a wood under a ridge); the occlusion's
  sun cone acts a third on leaves; far leaves keep 0.85 of the sun (was 0.56) and 0.8 of the fill; `FAR_LEAF_FORM`
  defaults to 0 (the leaves' own form carries it). The ground in a far wood's gaps keeps the full ray, so far crowns read
  as sunlit masses over dark gaps.
- **Plant roots** (`PLANT_ROOT_SHADE`, 45 %): `gbuffers_terrain` darkens a plant toward its root from `at_midBlock.y`
  (two-block plants as one stalk) on Minecraft's corner shading, which the light pass lays on a plant's fill and torch
  light whole with the occlusion.
- **Cost** (RTX 4060, 1600x900, MEDIUM, an idle machine; in-run A/B, four alternations each, against the canopy and the
  shadow-side leaf rules off): within the frame-time noise (under about 0.1 ms) in the grove at 12:00 and 18:30 and
  over the crowns at 16:30; estimated 0.03-0.05 ms (two extra fetches where the sky light is cut, one on leaf faces
  turned from the light, a few dozen ALU). A first version that sent the leaves' per-pixel transmission through the
  resolve cost about 0.15-0.25 ms there (the resolve's shortcut over uniform windows stopped firing on leaves).
- **Figures and checks** are in the workstream's hand-off (`tech/foliage/`): forest floor p5 27 to 17-19 by day, flecks
  6.5x the floor at noon, grove 18:30 R/B 6.9 to 3.9, crowns 16:30 p90/p10 4.6 to 5.2; the vistas' woods brighter crowns
  over dark gaps (steppe crown p90 112 to 160, countryside 77 to 109). Every `live.sh` variant compiles, no shader errors,
  the grain check passes on the final image (the grove's shadow term fails its own stricter test before and after
  this work alike: sway between the shots in a dense canopy).

### Lighting techniques, workstream D: air, the clouds' light and tone (2026-10-13, branch `welkin-air`)

TECHNIQUES 2.7-2.9 and 3.4, with the user's latest verdict on round 3's 4K vistas (flat from time to time, worst in
the middle and far distance; the clouds flat grey slabs; a hard haze band where the far land meets the sky, with the
far mountains floating over it). Benched in test world B only (`-Pgloam.test.front=0.38`, the vistas also at a
fair-weather front of 0.52).

- **The horizon band was the clouds' far band, not the haze.** Past the decks' cells (`wcBandOf`) a deck is drawn as
  a band mixed from the horizon colour and the cloud by the deck's share of cloud, laid over whatever lay behind it.
  Over the far land beyond a deck below the eye that painted the land out in the horizon's colour along the line where
  the deck plane meets it, with the far ridges, which stand above the plane, floating unhazed over it (with the pack's
  clouds off the line went). The band now lays the deck's cloud over what lies behind it, by its share (gaps show the
  land or the sky), and counts as cloud for the haze only where it is mostly cloud, so the far land behind it is hazed
  as land: the far ridges sit on their land in the air's colour.
- **The clouds' light** (`lib/clouds/shade.glsl`):
  - a base by day is a neutral grey body (less of the sky's blue, a little deeper), graded block by block, over a
    cell's width, into a white lit outline along its open edges (the sun through a cloud's thin parts, its sunlit
    sides), and brighter seen toward the sun than away from it, with a little warm light from the sunlit ground;
  - far cells pale into the bright air (half the horizon's colour by day) rather than the deeper blue behind them, and
    less (0.12 a ring, at most 0.62): sunk into the sky's own colour a far deck had read as a see-through grey slab;
  - at night a cloud takes the dome's colour behind it at about its brightness (a base a little under, a top a little
    over, brighter toward the moon), never a black cut-out in the stars.
- **Cloud shadows** (`lib/clouds/shadow.glsl`): 0.42 / 0.34 / 0.28 of the sun under one, two and three decks (was 0.62
  / 0.52 / 0.45), their soft edge a third of a cell on the far rings (a far cloud's shadow is a soft patch, not a hard
  square), from about 3 degrees of sun (was 5). They lie on the near land, the far chunks and the LOD's land as patches
  of shade between sunlit fields, drift with the cloud frame, and take only the sun: the sky's fill keeps them blue.
- **Forward scatter** (`lib/air.glsl airMie`, `AIR_MIE`): two Henyey-Greenstein lobes (g 0.8 and 0.45, normalized to 1
  at the sun), in the sun-side horizon's hue, added to the colour the haze converges to, strongest from about 25
  degrees down to the horizon, a trace at noon, gone at night. The haze lays it on the far land and the low sky by its
  own opacity; the middle land toward the sun takes it besides over about 1.4 km (the clear near air's opacity is
  small), so a low sun's side of the view has a warm, bright band and veil.
- **Aerial perspective** (`airPerspective`, `AERIAL_PERSPECTIVE` 50 %): past 120 blocks the land leans toward the air's
  hue at its own brightness and gives up a little contrast toward the air, more toward a low sun, over a few km.
- **Sun shafts** (`program/deferred_shafts.glsl` as `deferred5`, `lib/shafts.glsl`, `SUN_SHAFTS`, `SHAFT_STRENGTH`):
  the sunlit share of the near air (96 blocks) along each view ray from 12 shadow-map compares with the 4x4-tiled noise
  start, at half resolution into `colortex13`; composite2 gathers the 4x4 half-resolution texels round each pixel with
  depth weights (every noise offset once: grain-free in a single frame, and a trunk's outline kept) and adds the sun's
  light by a forward lobe, gated to a low sun (a trace at noon), a fraction on the sky, softly capped at about a dozen
  8-bit levels. `SHAFT_DEBUG` 1 shows the resolved term, 2 the raw one.
- **Tone and calibration** (`lib/sky/build.glsl`'s land light): dawn rose and softer than dusk (its gold
  (1.00, 0.67, 0.52), its last degrees (1.00, 0.47, 0.30), and the low dawn sun a quarter weaker); the night's shade a
  clearer, deeper moon blue (no longer greyed by 0.3, and about a fifth less), the moon a little stronger. The tone
  curve itself (`lib/tone.glsl`) is unchanged: sunlit grass measured at noon is Complementary's colour a little less
  saturated (no cream), and pale faces sit at p95 197-221, under white. No auto exposure was added (optional in the
  plan, a style call left for the user).
- **Figures** (land, lower 55 % of the frame, sRGB; before `welkin` f5d6dc76, after this branch; front 0.38, defaults,
  so cloud shadows are on): village 05:30 median 59.0 to 52.4 and R/B 10.05 to 6.80 (now under the evening's 7.45:
  dawn rosier and softer than dusk); valley 05:30 74.7 to 65.2, R/B 2.59 to 1.82; plain night p5 15.3 to 14.3, contrast
  2.17 to 2.31, R/B 1.15 to 1.05 (Complementary 0.99); valley night R/B 0.60 to 0.53; noon, 16:30 and the torch street
  within a level where no cloud shadow fell (a cloud's shadow lay over the whole near meadow in the plain's 16:30 shot:
  median 116 to 76, the reason the tone figures are taken with `CLOUD_SHADOWS=0`). Vistas: the steppe's far ridges now
  sit on their land; a cloud's shadow lies over the near hill (steppe) and the far valley (mesa, fair weather).
- **Shafts and grain:** the shafts add about a level on average at a low sun (village 18:30 +1, forest 18:30 +0.8; under
  2 levels at noon, now exactly nothing with the gate shut). The grain check (shafts on against off, same run) passes in
  valley 05:30, forest 16:30, plain 18:30 and plain 12:00 (lit and shade medians 1.00, p90 at most 1.22); the steppe's
  failing shade p90 is the cloud shadows drifting between the two shots, not grain. The resolved term itself has a
  Laplacian of about 2.4 levels of its own 0..255 range against 8-19 raw, under a tenth of a level on screen.
- **Cost** (RTX 4060, 1600x900, MEDIUM; another session's game ran beside every sweep, so best seconds, about 0.2 ms
  either way): shafts 0.0-0.34 ms while the gate is open (valley 05:30 the most), nothing above 37 degrees of sun;
  forward scatter and aerial perspective under the noise (a few dozen operations a pixel); the clouds' new base and
  night shading under the noise (estimated +0.02-0.05 ms). Inside the 0.3 ms shaft line of section 3.5.
- **For the merge:** `composite2` (the shaft and air lines), `lib/settings.glsl`, the lang file and
  `shaders.properties` (screen.AIR, sliders, profiles, the `deferred5` and `colortex13` lines at the end) carry this
  branch's blocks; `tools/wrappers.sh` maps `deferred_shafts` to `deferred5`. `lib/sky/build.glsl`: the dawn colours and
  strength in `skyLandSunColour` and `skyLand`, the night's `nightAmb` and the moon's 0.36 (marked "air and tone (D)").
  The shaft pass needs the shadow map (it returns at once without it). C's foliage edits do not touch these lines.

### The wave 2 merge: workstreams C and D on `welkin` (2026-10-13)

`welkin-foliage` (C, head b0abbb58) merged first (65bfc8bf; C was built on f5d6dc76, so the merged tree is C's own),
then `welkin-air` (D, head edd7c83b) on top (aeaa335a). Neither adds a pass that moves the layout: C adds none, D's
shafts run as `deferred5` after the light (`deferred4`), as D built them.

- **Conflicts (D onto C):** only the per-workstream blocks, all kept: `lib/settings.glsl` and `lang/en_US.lang` (C's
  foliage block, then D's air block), the `sliders` line in `shaders.properties` (one union), and section 7 here (C's
  subsection, then D's). README, `lib/buffers.glsl`, `shaders.properties`' screens and profiles merged on their own.
- **Cross-checks:** D's shafts read `shadowtex1` only, so C's leaf mark in `shadowcolor0`'s blue never reaches them;
  D's composite2 lines do not read `colortex10`, whose blue changed meaning in C; D's dawn and night colours
  (`lib/sky/build.glsl`) feed C's light through leaves through the sky table's sun colour, as intended.
- **Checked on the merged state:** every `live.sh` variant compiles, with C's, D's and 15 merge variants added to
  `live_runs.txt`; load runs in both test worlds log no shader errors. World A, C's foliage bench (single-threaded
  generation, `CLOUD_SHADOWS=0`): after C's merge every view matches C's own after-shots within a level except grove
  05:30 (darker floor: C's shots predate its own ce319d19, the leaf mark read only under cover); after D's merge C's
  figures hold (grove floor p5 16.7-18.8 by day, crown contrasts within 0.1, fleck 6.5) and D's colour moves show (grove
  05:30 R/B 2.5 to 2.0, nights bluer: plain night R/B 1.15 to 1.06). World B, D's view set (defaults): D's figures hold
  (village 05:30 R/B 6.65 against D's 6.80, valley night 0.53, plain night 1.06, the far ridges sit on their land) with
  C's deeper valley shade (valley 05:30 p5 25 to 16) and lit far crowns (steppe crown p90 110 to 142); views under a
  drifting cloud shadow (plain and village 16:30) differ by the cloud alone.
- **Grain** (`audit/grain.py`, same-run sweeps, `CLOUD_SHADOWS=0`): the shadows against `SHADOW_REFERENCE` pass in
  grove 16:30, plain 18:30 and plain 12:00 (term p90 1.13, 1.16, 1.22); the occlusion against `AO_REFERENCE` passes in
  valley 05:30, valley 12:00 and plain 12:00 (term p90 1.07, 1.05, 1.16: the plain's grass, at the limit before, now
  passes); the shafts on against off pass in all six views (medians 1.00, p90 at most 1.16).
- **Cost:** in section 3.5 (measured after the C + D merge). Nothing was cut in the merge.
### P5: far weather (2026-10-11, branch `welkin-far`)

The staged module M4 (far haze, far valley mist, rain curtains), rewritten for the look of section 3.2 and the pack as it stands after P4 and the light step. Not yet run in game (compile-checked only).

- **The pass:** `composite1` (overworld only; `program/composite1.glsl` slot, body `program/parts/far_weather.glsl`, library `lib/far_weather/`), after the clouds and before the near atmosphere, so the near haze and mist lie in front of everything it draws. It always runs in the overworld, because it now lays the **water fog** there first (moved from `composite2`, which keeps it in the Nether and the End): the far air and the far layers must lie over a lake's water as over the land, and before the move composite2's water fog would have been laid over them. Iris writes both `colortex0` and `colortex6`.
- **Far air** (`wf_air.glsl`): the near haze (`welkinHaze`) stops at 1912 blocks horizontal with `WF_FAR_HAZE` and LOD (it already did); the far air carries the same integral on from exactly that line with the same media and densities: the camera's clear air (`s.haze.w` x the kind's clear-air factor over max(2048, 0.75 x the world's reach), thinning over 160 blocks), the camera's rain (constant along the ray, VOID LOD's rain curtain) and the far land's murk (F tiles through `hzLandIn`, blended bilinearly, read once at the geometric middle of the far piece, as the near haze's last segment). Two far-only terms that do not thin with height, a hazy country's curtain (`void_haze.z`) and the grey of far rain (W1 rain at the ray's middle, bilinear), ease in over 2 km past the seam, so the density has no step at the line. The colour is the air pulled toward the far kinds by the murk's share, as in the near haze. Closed form (`hzLayer2`), no marching, no steps, no dither, no texel snapping. The seam test (no ring at 1912 blocks) follows from the two integrals meeting at one line.
- **Far valley mist** (`wf_mist.glsl`): the near mist's rules carried onto the F tiles (1 km cells, blended bilinearly): mist = potential x the hour's mist (or `MIST_FORCE`); pool from the cell's valley-scale lowness or its water; the slab from F0's valley floor + 1 + (3 + 24 x mist) x pool, capped at the floor + 2 + 26 x mist; density mist x pool, cut under the floor value. Its density fades **in** over the near tiles' fade band (Chebyshev cells 112..127 from the near tiles' centre) with weight 1 minus the near mist's, so along any ray the two add up to one mist. The ray is sampled at the band's inner and outer edges, once between (`MIST_SAMPLES` 3 and up) and at the surface, with the near mist's exact straight pieces (`mistSegment`) and its surface slab. Opacity and colour are the near mist's (`MIST_OPACITY` x cover, `mistColor`). Needs `VALLEY_MIST` and Gloam's F tiles.
- **Rain curtains** (`wf_curtains.glsl`): one box column per rainy W1 cell (rain over 0.12), in the clouds' drifting frame (`CloudEnv`: W1 addressed with the clouds' own shift and `w1Off`, its edge fade from the header's W1 centre), so curtains travel with the fronts and the clouds over them. Without Gloam's W1 the clouds' fallback field places them (only in vanilla rain). The column's top is the base of the deck the cloud map (`wcMapCell`) holds over its middle (the low stratus, else the main deck, else the lowest deck) plus 2 blocks, so rain meets the cloud with no sky between; its foot is under F0's ground. A 2D DDA walks the cells between the floor and the main deck's base, from 384 blocks out to the land, a nearer cloud (`colortex6`) or `WF_CURTAIN_REACH`, capped at `WF_CURTAIN_STEPS`; each rainy cell's box is met exactly. Opacity 0.82 x (1 - exp(-depth)), smooth. The nearest column carries soft streaks (smooth value noise across its face in the weather's frame, drifting slowly down), not pixel steps. Colour: rain lit like a cloud's underside by the sky table, ambient x 0.85 plus the direct light where a low sun or moon gets under the deck (less under a storm), brighter looking toward the light, darker storm grey; lightning through the table's flash. Each curtain sinks into the far air for its own distance.
- **Curtains against the sky or a cloud** haze themselves (the far piece, then `welkinHaze` for the near piece), because composite2 would otherwise haze a sky pixel for the whole ray and a curtain would look paler against the sky than against land behind it. On such a sky pixel the pass also lays composite2's sky haze on the sky behind the curtain and writes the curtain's distance to `colortex6`, so composite2 treats the pixel as a cloud (no second haze; its valley mist still lies in front). composite2 now reads `colortex6` in the overworld whether or not `CLOUDS` is on.
- **Left out:** the module's `dh_terrain` touches (`WF_DH_TEXTURE`, `WF_DH_WET`): the skeleton's `FAR_TEXTURE` owns the far texture, and the wet far land waits for a measurement of LOD's auto-tune (option table module `far_weather_dh`, proposed). `WF_HAZE_STEPS` (no steps since the style pivot) and `WF_LIGHTNING` (the flash lights curtains always) are gone.
- **Options:** `WF_CURTAINS`, `WF_CURTAIN_STEPS` (16/40/64), `WF_CURTAIN_REACH` (16/32/64 km) on the Weather screen; `WF_FAR_MIST`, `WF_FAR_HAZE` on the Far land screen; LOW turns off curtains and far mist. `WF_DEBUG`: 1 the W1 and F tiles as panels, 2 curtain depth, 3 far mist depth with the near band as a ring, 4 the far air's opacity (and sky pixels whose curtain hazed itself, blue).
- **Cost** (estimates, not measured): the base (ray, `hzState`, `CloudEnv`) about 0.02 ms; far air one bilinear land read and a W1 bilinear per far-land pixel, about 0.02-0.04 ms; far mist 32 fetches per far-land pixel only while there is mist, about 0.03-0.06 ms at dawn; curtains one W1 fetch per cell walked (the horizon band only walks far) plus two per rainy cell, 0.03-0.08 ms. About 0.1-0.2 ms in all against the 0.2 ms budget. Measure in P8.

### The wave 3 merge, 1 of 3: far weather (P5) onto the lighting (2026-10-16)

`welkin-far` (head a99778d4, built on 05e84318, before the lighting rework) merged onto `welkin` at 27ecbc82. The user's
newest rule (2026-10-16) wants the heavy effects done beautifully; this merge keeps the lighting's air as it is and
fits far weather into it, so the far land, the clouds and the curtains carry the same air on past the near haze's seam.

- **Conflicts:** `program/composite2.glsl` (the lighting's `uv` and `cloudKm` against P5's `colortex6` read without
  `CLOUDS` and the water fog moved to composite1: both kept, the water fog laid by composite2 only in the Nether and
  the End); `shaders.properties` (the lighting's profiles, sliders and pass lines with P5's options added: LOW
  `!WF_CURTAINS !WF_FAR_MIST`, MEDIUM 40 steps / 32 km, HIGH 64 / 64 km, the composite1 lines after the shafts');
  the lang file, README (composite1's row before composite2's) and this file (both). `lib/settings.glsl` merged on its
  own. The pass layout is unchanged but for composite1 (no `tools/wrappers.sh` mapping needed).
- **The far air with the forward scatter, once** (`wf_far_weather.glsl`): composite2 gives its haze the sun's forward
  scatter (`airMie`) and lays the share its haze did not on the land as part of the aerial perspective, by the near
  haze's opacity, which with `WF_FAR_HAZE` and LOD now stops at the seam. So the far air over the land keeps the plain
  air (else the far land toward a low sun would take the scatter twice), while everything composite2 does not touch
  again (a cloud's far piece, a finished curtain and what lies behind it) takes the air with the scatter.
- **Clouds past the seam** take the far air's piece in composite1: since workstream D composite2 hazes a cloud as land
  at its distance, and with far haze on that stops at the seam too, so far decks low over the horizon would have lost
  their haze.
- **Finished curtains, marked negative:** a curtain over the sky or a cloud is hazed here (the far piece, then the near
  piece, in the scattered air), over what lies behind it hazed as composite2 would (the sky as sky; a cloud as land at
  its distance, without the world's edge), and `colortex6` takes the curtain's distance negated. composite2 then lays
  no haze, shafts or aerial perspective on it (P5's old mark, the distance itself, would now have had composite2 haze
  the pixel again as a cloud), only the valley mist in front where the pixel is against the sky.
- **The far murk where the ray ends** (`wf_air.glsl`): the near haze's far segment now reads the land seen, not the
  segment's geometric middle (which stood bright columns of haze over the far land), and gives the far kinds' tint up
  to the camera's own from 3 to 16 km; the far air does the same, so the two still meet with no ring at 1912 blocks.
- **Far mist at night** is the near mist's thinner veil (`MIST_OPACITY` x 0.5..1 by the sun's height), so the near and
  far mist still add up to one; its colour is the near mist's (`mistColor`, now lit by the night sky).
- **Checked:** every `live.sh` variant compiles (P5's own variants and four for the merge added to the merge's
  `live_runs.txt`); the load runs are in the wave 3 summary.

### The wave 3 merge, 2 of 3: underground air and rock (P6) onto the lighting (2026-10-16)

`welkin-ug` (head 3884bd53, built on 05e84318; its own notes are in `lib/underground/`'s headers) merged second. It adds
the cave air in composite2 (`ugComposite`: cave fog by the space's openness in floor, middle and roof strata, cavern
water tinted by kind, mist over cave rivers and buried seas, an ember haze over lava) and the rock in the light pass
(`ugSurface`: damp walls with drip streaks, the realm's faint light, glowworm light and specks, the lava's warmth),
with the Underground screen (`UG_*`; `UG_FOG_SAMPLES` 2/3/4 in the profiles) and `UG_DEBUG`.

- **Conflicts:** `lib/settings.glsl` (the modules line and the debug options: both), `program/composite2.glsl` (the
  lighting's and far weather's air with the hand-over at cave mouths), `program/deferred.glsl` (the lighting's light
  pass against P6's `welkinLightCore` call and its light added after it), `shaders.properties` (the lighting's
  profiles, sliders and screens with the Underground screen and options added) and the lang file (both).
- **The rock's light in linear light:** P6 added its light to the lit, display-space colour (`+= albedo * light +
  glow`), which the new light pass would have laid over the tone curve. Now deferred hands it to `lib/light.glsl`
  (`lightUgLin`, `lightUgEmit`, taken to linear with `toLinear`, which matches P6's display values in the dark, where
  the curve's S has faded out): the realm's and glowworms' light lights the albedo like the sky's fill, under the
  fill's occlusion (corners and the feet of walls keep their depth in a glowworm cave), and the specks and drips add
  as emitted light, all before the tone, so a lit cave wall rolls into its highlights as the land does.
- **The emission carried through the fog** is read with `unpackEmission` (the light review packed the light class
  into `colortex2.z`; P6 read the raw value).
- **The hand-over at cave mouths covers the lighting's air too:** besides the haze and the valley mist (P6), the sun
  shafts and the aerial perspective fade out by `ug.amount`, and so does composite1's far weather (the far air, far
  mist and curtains), so a far view from a cave mouth does not keep the far piece of the air while the near piece has
  handed over (which would have drawn a ring at 1912 blocks).
- **Checked:** every `live.sh` variant compiles (P6's own and five for the merge added); the load runs are in the
  wave 3 summary.
### The water step (P7, the water half; branch welkin-water)

- **The surface** (`lib/ws/`, `program/parts/ws_gbuffers_water.glsl`, `ws_dh_water.glsl`): an exact sum of 18 fixed waves at the pixel's own point (integer-wrapped phase, seamless anywhere in the world and across the clock's hourly wrap), weighted by VOID Gloam's wind (`void_wind`; without Gloam the breeze the leaves sway in), calmer under trees and roofs; rain rings spread and fade in each block while it rains (`WS_RIPPLES` 2). With distance the waves shorter than a few screen pixels fade out and hand their tilt on as roughness, so nothing shimmers and the sun's glitter path grows longer and softer toward the horizon, continuous from the chunks' water onto the LOD's.
- **Reflection and glint:** the sky table's dome in the reflected direction (`skyDomeOnly`), by Schlick's Fresnel, dimmed where the sky is hidden; the sun's (or moon's) image as a crisp-edged disc on calm water that spreads with roughness, the only thing the water draws above 1.0. Near water on HIGH also reflects terrain (`WS_WATER_SSR`, composite2: 12 growing steps and 4 refine steps, a fixed start, a smooth fade at the screen's edge; nothing past 256 blocks or on the LOD).
- **The body:** the biome tint over the vanilla texture (`WS_WATER_TEXTURE`), lit by the pack's own ramp with the sun through the near shadow map and the clouds' shadows (trees and decks shade the water); its opacity from the depth of water the view crosses (`depthtex1` near, `dhDepthTex1` far), murkier in rain, with a pale band where it thins out over a bank. From below, the Snell window opens smoothly. `lib/water.glsl`'s fog from above is gone (the body does it); its under-water fog stays. Glass and ice take the sky's reflection by Fresnel.
- **Caustics** (`WS_CAUSTICS`): the shadow pass's water writes the same wave field's light lines into shadowcolor0, so the sun on the ground under water carries them through `shadowVisibility`'s water tint; they fade out where a shadow texel grows past a fraction of a block.
- **Shore, frost and seasons** (deferred, `lib/ws/surface.glsl`): a smooth wet band within about 6 blocks of water and 1.4 blocks above its level (2.4 when the ground is wet), from Gloam's N0 tile, counted as the wetter of it and the rain's wet ground, never both; winter's paler, weaker sun, cooler shade and darker water, autumn's warmer light (`WS_SEASON_LIGHT`, from Gloam's warmth and season state); frost settling texel by texel on the blocks' own grid on cold mornings, melting by mid-morning (later in shade), a smooth average where a texel is under a pixel, and a few fixed glints on frost and snow in the sun (`WS_FROST`). No seasons, shore band or frost without Gloam's uniforms.
- **Not taken from the staged module:** its shadow program and lookup (`ws_shadow.glsl`, `lib/ws/shadow.glsl`: the light step's own are the pack's), its stepped facets, banded Fresnel and opacity, Bayer-dithered opacity and shadow fade, hashed 5 Hz glints and far glitter cells, and `wsSeasonHaze` (the haze has its own seasons: INTEGRATION.md C7).

### The wave 3 merge, 3 of 3: water (P7) onto the lighting (2026-10-16)

`welkin-water` (head 46f29b33, built on 05e84318) merged last. Its water replaces the lighting's (`lib/watersurf.glsl`
stays for the puddles' sky in deferred; gbuffers_common's `G_WATERFLAG` path is no longer used by any program), and
the lighting's own water fixes are carried into it.

- **Conflicts:** `lib/settings.glsl` (the modules line, the Weather and Debug options, the empty `#ifdef` tests: all
  kept), `lib/water.glsl` (P7's: no fog from above, the body covers the bed by the depth it crosses; the fog under
  water stays, laid by composite1 in the overworld), `program/dh_water.glsl` (P7's slot, body in
  `parts/ws_dh_water.glsl`), `program/shadow.glsl` (the lighting's low-plant shrink and solid pass with P7's
  caustics; the caustics are off in `shadow_solid`, which draws no water), `program/composite2.glsl` (P7's reflections
  of terrain before any air, after the cloud and finished-curtain marks), `program/deferred.glsl` (the lighting's light
  pass with P7's shore, frost and season), `shaders.properties` (profiles with `WS_CAUSTICS` on MEDIUM and HIGH and
  `WS_WATER_SSR` on HIGH; the Water screen; the Weather screen with both P5's and P7's options; debug and sliders),
  the lang file, README and this file. `gbuffers_water.glsl` merged on its own to P7's slot.
- **The water on the new light** (`lib/ws/water.glsl`): the body is lit in the light pass's linear light and tone,
  its albedo into `welkinLightCore` (P7 multiplied the albedo onto a white face's toned light, which over-saturated
  and darkened it under the new curve), with the sky's irradiance as its fill, the sun through the shadow map and the
  clouds' shadows; the bank's pale band now pales the albedo before the light. The biome tint loses its cobalt
  (`waterTone`, the lighting's natural blue-green). Reflection by Schlick's Fresnel of the sky table's dome, clear
  shallows from the depth crossed (0.2 opacity at the thinnest film, 0.94 deep), and the sun's image spreading with
  the waves' sub-pixel roughness into a glitter path toward a low sun were all in P7 and stay.
- **The lighting's water fixes kept:** the turned-down copy of water's top is discarded from the air (the light and
  dark triangles on falls); every top, sloped ones too, ripples and takes its light as level water, and a fall's
  upright sheet reflects about a normal leaning up (`welkinWaterNormal`, `waterReflectNormal`); the LOD's baked bed is
  taken back out of its water colour (`parts/ws_dh_water.glsl`), so the far water matches the near.
- **Glass** reads `separateAo`'s vertex alpha as corner shading (P7 took it for the texture's alpha) and lights with
  it (`welkinLightFull`).
- **Shore, frost and seasons in the light pass:** the season's sun tint on `sunShare`, its shade tint on the fill;
  frost and snow glints go in as emitted linear light (`lightEmitLin`, shared with P6's glow; P7 added
  `skyDirectLight() * LIGHT_SUN_GAIN`, a constant the lighting removed, onto the toned colour), with the land's direct
  light, so the tone rolls a glint into white; the emission is read with `unpackEmission`.
- **Checked:** every `live.sh` variant compiles (P7's own and seven for the merge added); the load runs are in the
  wave 3 summary.

### The wave 3 merge: the summary (2026-10-16)

- **Follow-ups on the merged water** (seen in the first load run of the valley at 12:00, its cascades in light and
  dark triangles where the lighting's water had read as smooth blue sheets): from the air every turned-away water face
  is left out (a fall's step above the eye showed its top's back, shaded as the view from under water, beside its
  turned-down copy's sky); water down a slope or a fall (from about 15 degrees) reflects and takes its light as level
  water, is nearly opaque (`WS_ALPHA_FALL` 0.86), a little paler (churned with air) and has no bank band; and the sun's
  reach on water is worked out for level water (each facet had met the shadow map at its own angle). The cascades now
  read as smooth, slightly paler sheets, in the sun and in the gorge's shade, as before the merge.
- **Compile check:** every `live.sh` variant compiles on the final tree: 322 runs (every program, wave 2's variants,
  and P5's, P6's and P7's with the merge's own added to `live_runs.txt`).
- **Load runs** (test world B, Welkin from each commit by `git archive`, defaults): after P5 far0, mist1, storm0,
  village 18:30 and valley 18:30; after P6 wcave0 and mist1; after P7 water0, rain0, shore 12:00 and 18:30, valley 12:00
  and 18:30, village 18:30, and the valley at 12:00 again after each water follow-up, with the tree before P7 (015d01be)
  for comparison. No shader errors in any run. The shore at 18:30 shows clear shallows over the sand and weed, the
  sky's Fresnel reflection and a glitter path broken by the waves toward the low sun; the crystal cavern of wcave0 its
  violet cave air over the lit amethyst.
- **Not done here:** the grain check and the cost of the three modules (P5's and P7's were estimates on their
  branches; P6's was not measured); the curtains themselves were not caught on screen (storm0 aims at a storm 7 km off
  from the summit, where it hangs under the decks the summit looks down on); the options' generator table
  (`options.py`) still lacks every option since wave 1.

### The clouds rework (2026-10-16, branch `welkin-clouds`)

The voxel clouds were the biggest beauty gap in every sheet: grey stepped slabs, the low deck flat white slabs, the rain
deck sawtooth grey bands. Their light is rebuilt as a lit volume inside the crisp voxel outline (lib/clouds/shade.glsl,
its header has the whole model); Gloam's data (cover, fronts, decks, wind, the W tiles) is read as before.

- **Shape:** a deck cell's base stays flat; its top rises in 4-block tiers (`WC_TIER`) with how deep inside its cloud
  it lies, with a lumpy swell over a cloud's heart: the main deck 4 to 16 blocks (`WC_MAIN_TIERS` 3), the low stratus 4
  to 8, the high sheet 4. A cumulus is a voxel dome; a sea of clouds seen from a peak has relief and, at a low sun,
  long tier shadows. The march, the band, the slab fog and the deck tests use each deck's full span (`wcDeckSpan`).
- **The body field:** the raw cloud map's B and A hold the low and main decks' smooth margin fields (0.5 at the outline,
  1 deep inside, normalised by the puffs' headroom so a thin cover's clouds are not all rim); the clouds pass reads them
  through the bilinear filter (`wcField`, `WC_FIELD`). The tiers ride in the raw map's spare bits and the finished
  map's G (rise bits 0..6, low tier 7, main tiers 8..9). The high sheet and towers take a smooth value-noise mottle.
- **Light:** the length of cloud toward the sun or moon from the hit's own column and `WC_LIGHT_SAMPLES` (4, LOW 2)
  field samples along the light, each read as a soft height field (taller tiers shade lower ones, a cloud's body its
  far side, a belly its thickness and breadth); Beer's law with two multiple-scattering octaves; back-scatter and
  powder off the lit side; a forward lobe through the thin parts whose single scattering (the silver lining) is laid on
  past the soft shoulder, so a rim toward the sun may pass white and bloom; the sky's light by how much dome a face
  sees, less in a deep belly; the land's bounce under a cloud.
- **Hours:** the clouds' sun is held on longer round the horizon and turns gold, then rose (they stand high); the
  under-light once the sun is down; through the golden hour the warm light stays on what the sun reaches and the
  bellies keep the dusk's violet and rose. At night the moon (`WC_MOON_GAIN`) and the night dome's floor. Under a closed
  overcast the bellies keep near the dome's brightness and darken under their thick parts (rain decks mottled and
  layered, no pale slabs); the far band takes its cells' tone from the weather (a fair band beyond rain cells drew the
  sawtooth line). Far cells sink into the horizon's own light, its glow toward the sun.
- **Cost** (RTX 4060, 1600x900, MEDIUM, test world B, `CLOUDS=false:true` sweeps, 6 s readings): the clouds stay at
  about 0.7 to 1.0 ms with their shadows on the land (about 0.3 ms of it, `CLOUD_SHADOWS`), the same as before the
  rework within the readings' noise (before: 0.7 to 1.0 ms); the extra field reads cost no more than the per-face work
  they replaced. Over the 0.6 ms budget as before; the march, not the light, is most of it.
- **Shots:** ground (the plain, looking up) and peak (y 498 over the main deck) at 08:00, 12:00, 16:30, 18:30 and night,
  fair (front 0.45) and rain (0.8), and the steppe and countryside vistas beside Complementary and Euphoria, before and
  after. Clouds drift between runs, so the cells differ from shot to shot.
- **Still open:** a big low-deck belly straight overhead at noon is a soft, light grey with little structure; the rain
  deck seen from above is flat under the haze; dusk bellies near the eye lean taupe where the sky behind them is dark;
  the vistas' afternoon clouds are cooler and greyer than Complementary's cream (the sky and haze lane's warmth will
  shift them).

### The defects pass: far rain, falls, water by day, the valley mist (2026-10-16, branch `welkin-clouds`)

After the post-merge showcase (its report listed the far rain curtains never showing, falls as light and dark
triangles of pale ice, the shallows under a milky veil, no glitter at 16:30, no shore foam, no land in the water at
MEDIUM, a solid plume of mist at a fall's foot and a black hole with the eye at a fall).

- **Far rain curtains (lib/far_weather/).** A debug view of why a ray meets none (`WF_DEBUG` 5: black no stretch to
  walk, blue dry cells, green a rainy cell, yellow its column missed, magenta no depth, red a curtain) and the W1 panel
  (`WF_DEBUG` 1, checked against Gloam's own front table: the tile lines up with the world) showed the data reaching
  the pass and the walk finding rainy cells. Three things lost them: the reach was cut to `dhRenderDistance` (VOID LOD
  reports it small: everything past 4 to 8 km went, where the fronts hang their showers and storms), a column's top
  was always "under its deck" from above (the peak views saw none, even through gaps), and the rain was lit with the
  land's linear light and came out the haze's own pale grey, then took the whole air for its distance. Now the reach
  is `WF_CURTAIN_REACH` (16 km without far land), a top the ray reaches is seen, the rain takes the deck's underside
  colour (the sky table's cloud base, slate under a storm) a little darker inside the shaft and glows toward a low
  sun, keeps a third of its own colour through the air (`WF_CURTAIN_KEEP`), is denser in light rain (`sqrt(rain)`), and
  each column is a soft shaft: densest down its middle, thinning to its sides and up into the deck, streaked on every
  column. Gloam's showers only fall where a front passes about 0.74, so a curtain in view still needs weather there.
- **Falls (lib/ws/water.glsl, program/parts/ws_gbuffers_water.glsl).** The triangles were three things: smooth
  lighting's sky light differing at each corner of a step's uneven quad (shaded halves), each face's own Fresnel (the
  steps seen edge-on mirrored the bright sky), and the turned-down copy of a top, which also arrives front-facing with
  the top's upward normal and was shaded as the view from under water (the dark triangles up a cascade, the black one
  with the eye at a fall, `WS_DEBUG` 5 to 7 found it). Now a top seen from under its plane, and any downward copy that
  is not a real underside, is left out with the eye in the air; moving water takes the open sky's light wherever a
  corner sees some, and water within a few blocks at least the eye's own; a fall's faces reflect about one normal facing
  the eye. The look: a clear sheet (`WS_ALPHA_FALL` 0.62), a little whiter than still water, with foam pouring down it
  in the block's own 1/16 pixels (each pixel column its own speed, stepping a pixel at a time, gathered into ropes),
  foam on the water still running off below it, and a soft bright spray veil when a fall is at the eye. A fall's mark
  in colortex3 is 0.5 (level water 1), so the valley mist takes it as the rock behind and not as open water.
- **Water by day.** The body throws back only a share of its lit tint (`WS_SCATTER_SHALLOW` 0.42 to `_DEEP` 0.82): the
  shallows show their bed tinted and darker, not through a milky veil. The glitter path: a wide lobe about the sun's
  image (`WS_GLITTER_SPREAD`, wider in the wind) with a soft glow, broken where a cell is 1.5 to 14 pixels into
  sparkles on the water's 1/16-block grid, each redrawn a few times a second. Foam along the shore: on screen
  (lib/ws/shore.glsl, composite1), three rings of probes round each near water pixel find land at the water's level and
  how far off its edge is; a breathing band in the water's pixels, lit at the water's brightness. Land reflections are
  on at MEDIUM now: 18 steps growing 1.45x reach about 1.5 km, the LOD's land met as the chunks' is (surfaceBehind), far
  water reflecting too, a hit checked against the scene and above the water, far ripples calmed; about 0.3 to 0.5 ms
  (`WS_WATER_SSR` sweeps, noisy).
- **Valley mist (lib/mist.glsl).** The slab thins over its top 8 blocks (`MIST_SOFT_TOP`), the last stretch of a ray
  counts at most 40 blocks (`MIST_LAST_MAX`), and the density drifts in soft billows 22 blocks across: no hard white
  line where the top meets the rock, no solid wall. The chunks' water has its pinholes filled as the LOD's (composite2
  `pinholeFill`): the dotted seams along a fall's steps and the shore.
- **Particles:** none closer than a third of a block (`G_NEARCUT`, gbuffers_textured and _lit).

### The luminous sky, haze, shafts and bloom (2026-10-16, branch `welkin-atmos`)

The user's pick after the depth A/B sheet (section 3.2): HAZE, SHAFTS, BLOOM and the LUMINOUS SKY, stronger than the
prototypes; SHADE, AIR, BANDS=0 and RELIEF not carried over.

- **Luminous sky** (`lib/sky/luminous.glsl`, `SKY_LUMINOUS`, `SKY_GLOW`): a broad soft horizon band, cool away from the
  sun and warming toward it, a wide glow round the sun, a deeper zenith by day; its warmth follows the sky table's golden
  hour. The glow is mixed toward a warm gold with a shoulder rather than added, so the sun-side horizon is ivory-gold, not
  white, and less gold than Complementary. It lies on the dome, the far fade, the haze's air and the clouds' far fog
  (`wcFogTarget`). No daytime sky draws underground (the cave's own fog takes sky and cloud pixels seen from below), and
  rain closes the sky to stars as cloud cover does.
- **Volumetric haze** (`lib/haze/volume.glsl`, `HAZE_VOLUME`, `HAZE_VOLUME_STRENGTH`, `HAZE_VOLUME_STEPS` 10/14/28): one
  walk in `deferred5` through the shadow map near, the terrain's shadow line far and the cloud shadows throughout (cloud
  shadows stand as columns in the air); thickest low, on low ground and toward the horizon, by haze kind, Gloam's
  haziness, rain and snow; it gives way to the valley mist and hands over to the far haze at its band; the near 72 blocks
  clear, at most 70 %. Off on LOW.
- **God rays:** the same walk measures the sunlit share of each view ray (finer steps near the eye), in the sun's colour
  led toward gold through the golden hours, a strong forward lobe and a weak broad one, half-shadowed rays dimmed more
  than in proportion; full from the horizon through the golden hours, a quarter at noon, thicker in haze and morning
  mist, off under overcast and at night. The **sun beams** (`SHAFT_BEAMS`, `composite3`, overworld) gather the open sky
  toward the sun's place on screen, so light streams through crowns, between clouds and over ridges when the sun is in
  or near the view.
- **Bloom** (`lib/bloom.glsl`, `BLOOM`, `BLOOM_STRENGTH`; `composite3`, `composite4`, `final`; every dimension): see its
  header and section 3.2. Glowing blocks get extra headroom, so torches and lanterns show halos at night.
- **Passes and buffers:** `composite3` and `composite4` are new (half resolution); `colortex14` is new, `colortex12` and
  `colortex9` are reused after the light pass. LOW turns off the haze volume, beams and bloom.
- **Cost:** see section 3.5 (the lane read 1.09 ms for all four on the steppe, 0.89 ms facing the sun, about 0.8 ms at
  noon). Grain: none (flat-area medians 2.07-2.25 with the effects on or off, final's own anti-banding level).

### The phase 2 merge: atmos and clouds on `welkin` (2026-10-16)

`welkin-atmos` (the luminous sky, haze, god rays and bloom) merged first, then `welkin-clouds` (the clouds rework and
the defects pass), both from abe2fa09.

- **Conflicts:** `lib/clouds/shade.glsl` `wcFogTarget` (the clouds lane's 0.7 horizon share for a far deck over the eye,
  with the atmos lane's luminous air over it, so far clouds and far land fade into one band) and the profiles in
  `shaders.properties` (the atmos options on every profile, the land reflections on at MEDIUM). `lib/settings.glsl`,
  `lang/en_US.lang` and `composite2` merged cleanly.
- **The clouds under the bloom** (found in the first load run: from the peak over a sunlit cloud sea the whole frame was
  source, and the peak and the shadows went milky, p5 luma 106 against 51 with the bloom off): a cloud's body now gives
  `BLOOM_CLOUD` (0.3) of its bright share and a rim past white (the clouds lane's silver lining) all of it; a cloud lends
  its own light out in `final` as the land does; and the frame's mean bright share (the 1x1 level of `colortex14`,
  `composite4`'s alpha) is kept off the halo (`BLOOM_FLAT` 0.85), so a frame full of snow and sunlit cloud keeps its
  depth (p5 87 against 76 with the bloom off) while lamps at night, the sun's glow and the silver linings still bloom.
- **Checks:** every `live.sh` variant compiles (the merge's own variants added: the new passes in the Nether and End,
  each new option off, the clouds and defects lanes' debug views). Load runs in both test worlds after each merge, no
  shader errors. Shots against each lane's own after-shots: the atmos lane's vistas, sun-facing views, dusk and night
  (world A); the clouds lane's ground and peak views fair and in rain (world B); the defects lane's shore, valley, river
  and rain views (world B). Nothing lost: the beams through trunks, the lamp halos, the ivory sun-side sky, the lit
  clouds, the falls' foam, the glitter (now blooming), the shore foam and the far curtains all show.
- **Still open:** dusk bellies near the eye lean brown against the luminous dusk sky (plain 18:30, ground 18:30); a
  thin pale line along a rain cloud's side seen edge-on from below; the land seen through gaps in the cloud sea from
  the peak is near black in the deck's shadow; at a low sun the heavy effects sit at or over their 1.2 ms line and the
  clouds over their 0.6 ms line; trees in test world A grew differently from the lane runs (forest and sunforest views
  are not comparable).
