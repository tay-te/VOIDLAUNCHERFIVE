# VOID Welkin

Welkin is VOID's shader pack for Iris (Minecraft 26.3, Fabric, Iris 1.11 with Sodium). It draws every pixel of the
VOID world: sky, light, water, and later haze, valley mist, voxel clouds and far rain. It reads the world's state from
VOID Gloam through a small bridge, and draws the far land of VOID LOD to 128 km through its Distant Horizons programs,
so near and far land are lit the same way.

It still looks like Minecraft, lightly stylized and of very high quality: smooth light and air, directional sunlight,
and crisp pixel art where it belongs. It does not make the game look real.

The design, the bridge contract with Gloam and the milestones are in [docs/DESIGN.md](docs/DESIGN.md). The terms are
in [LICENSE](LICENSE): free to use and to share unchanged, the source open to read, no changed versions shared without
VOID's permission.

## The look rules

Set by the user on 2026-10-11 (they replace the first rules of flat colour steps and Bayer dither). They are
absolute; every change is checked against them on screenshots.

1. **Minecraft first.** Lightly stylized, not realistic, and of very high quality: on a par with good shader packs.
2. **Smooth gradients with gentle banding.** Light, fog, haze, mist and sky are smooth. A ramp may gather softly at
   a few levels (`softBands` in `lib/common.glsl`, the `SOFT_BANDS` option), never in flat steps with edges.
3. **No visible dither.** No checkerboard or Bayer pattern anywhere. The only noise is final's anti-banding, one
   8-bit step of fixed noise, which cannot be seen.
4. **Pixel-crisp only where it looks good:** the sun, the moon, the stars and the voxel clouds. Haze, mist, water
   fog and wet ground are smooth, worked out at each pixel's own point. (Puddle outlines and the far land's faint
   texture sit on the world texel grid, like block texture.)
5. **Voxel clouds.** Clouds are cells, opaque, with crisp edges. Never soft ray-marched volumes.
6. **Directional sunlight and shadows first.** They are the foundation of the quality.
7. **No TAA, no bloom haze, no fuzzy volumetrics, no blur.** Everything renders at full resolution.
8. **Natural surface colours.** The surface stays real-world: nothing glowing or fantasy-tinted. Underground may be
   fantastical.
9. **Within budget.** The pass budgets of DESIGN.md section 3.5 hold.

## Layout

```
welkin/
  shaders/                the pack (the zip's root folder)
    shaders.properties    profiles, screens, the bridge texture, blend modes, vanilla parts kept or dropped
    block.properties      block ids: vanilla, expanse:*, (Gloam adds no blocks)
    item.properties, entity.properties, dimension.properties, lang/en_US.lang
    lib/                  shared GLSL (settings, light ramp, depth, world grid, VOID bridge, water, debug views)
    program/              every program, once
    world0/ world-1/ world1/   generated wrappers per dimension (tools/wrappers.sh)
  tools/pack.sh           zips shaders/ into a run's shaderpacks/ as Welkin.zip and selects it
  tools/wrappers.sh       regenerates the per-dimension wrappers after adding or removing a program
  docs/DESIGN.md          the design
```

### Programs (M0)

| Program | Job |
|---|---|
| `gbuffers_terrain` | Chunk terrain: albedo, normal, light map and material into the G-buffer, lit in `deferred` |
| `dh_terrain` | The LOD's far land: the same G-buffer; a hash texture on the world grid, coarser with distance; cut inside `clipDistance` |
| `gbuffers_entities`, `block`, `hand`, `basic`, `textured`, `textured_lit` | Light themselves with the same ramp; clear the deferred flag |
| `gbuffers_water`, `dh_water` | Light themselves; mark water in `colortex3` for the water fog. `dh_water` is cut where the chunks' water stands |
| `gbuffers_skybasic`, `skytextured` | The sky dome from the sky table (smooth, in soft bands), pixel stars, the pixel-art sun and moon |
| `gbuffers_clouds`, `weather` | Vanilla clouds (until M3) cut against the LOD's depth; rain and snow |
| `gbuffers_damagedblock`, `spidereyes`, `armor_glint`, `beaconbeam` | Unlit |
| `shadow`, `shadow_solid` | The near shadow map: chunks inside `shadowDistance` (128 on MEDIUM) and entities within half of it, seen from the sun or moon, with swaying leaves and plants; faces turned from the light left out on solid blocks and leaves; overworld only, off on LOW; never the LOD |
| `deferred` (`program/deferred_ao.glsl`) | The contact shade's raw term at half resolution into `colortex9` (with `CONTACT_AO`) |
| `deferred1` (`program/deferred.glsl`) | The surface from `depthtex0` where the chunks drew, else `dhDepthTex0`; the light in linear light (sun or moon through the near shadow map with contact-hardening edges, the far shadows found on screen past it and on the LOD; the light through leaves; the sky's fill as a hemisphere with the ground's bounce, under the blurred contact shade; torches) brought to the screen by the land's filmic tone curve (`lib/tone.glsl`); wet ground; cloud shadows; the far fade into the horizon |
| `composite` | Water fog, then valley mist from VOID Gloam's near tiles (`lib/mist.glsl`, M1); debug views. **The atmosphere goes here** (haze M2, underground fog M5), then voxel clouds (M3) and `composite1` far weather (M4) |
| `final` | A soft shoulder past white, then invisible anti-banding (one 8-bit step of fixed triangular noise) |

### Buffers

| Buffer | Format | Holds |
|---|---|---|
| `colortex0` | RGBA16F | albedo from the deferred-lit programs, then the lit scene |
| `colortex1` | RGBA16 | octahedral world normal (xy), light map (z block, w sky) |
| `colortex2` | RGBA8 | x lit in deferred, y material / 255, z emission |
| `colortex3` | RGBA8 | water in front: its lit colour (rgb) and how much fog to apply (a) |
| `colortex9` | RG16F, half size | the contact shade's raw term (r) and view distance (g) |

Self-lit programs write `colortex2.x = 0` with blending off for that buffer (`shaders.properties`), so deferred never
lights a pixel twice, whatever order Iris draws in.

### The VOID bridge

`lib/void.glsl` holds the contract-1 helpers: `voidMode()` (none, header only, full), the size and `V O I D` magic
check that must come before any other read of `void_data`, wrap addressing (`voidTexel`), h16 heights, flags and haze
kinds, and a bilinear fetch. Without Gloam the pack runs on its own defaults. Gloam's uniforms are `void_*`; the pack's
own smoothed copies are `welkin_*` and are declared in `shaders.properties` only under `#ifdef VOID_GLOAM`.

### Options

In `lib/settings.glsl`, named in `lang/en_US.lang`. Profiles LOW, MEDIUM (default) and HIGH.

| Option | Default | |
|---|---|---|
| `SOFT_BANDS` | 50 | how much light, the sky and the sun's glow gather into gentle bands (0 a plain gradient) |
| `LIGHT_BANDS` | 5 | soft bands in the sky and block light ramps |
| `sunPathRotation` | -20 | the sun's path leans south |
| `FAR_TEXTURE` | on | the LOD's world-grid texture (off on LOW) |
| `FAR_TEXTURE_PIXELS` | 2 | screen pixels per far texel before it doubles |
| `FAR_FADE` | on | far land fades smoothly into the horizon (while the haze is off) |
| `VALLEY_MIST` | on | valley mist at dawn from VOID Gloam's near tiles |
| `MIST_SAMPLES` | 3 | places along each view ray the mist slab is read at (2 LOW, 4 HIGH) |
| `MIST_THICKNESS` | 1.0 | the mist's opacity per block, as a share of the default |
| `DEBUG_VIEW` | 0 | 1 depth, 2 normals, 3 light map, 4 albedo, 5 G-buffer flags, 6 VOID bridge, 7 VOID near tiles |
| `MIST_FORCE` | 0 | lays the mist at this percentage whatever the hour (0: Gloam's time of day) |
| `DEBUG_JOIN` | off | a magenta line along the join between the chunks and the LOD's land, over the finished picture |

`DEBUG_VIEW=1` (depth): chunks warm, LOD cool, each banded at every doubling of distance, so the bands must run on
unbroken across the join. Magenta: the LOD's surface stands in front of the chunks'. Red: an LOD depth outside its own
near and far planes, which means the depth convention is read wrong. Sky black.

## Build and test

There is nothing to compile: the pack is GLSL, compiled by Iris when it loads.

```bash
# install into a game-test run (zips shaders/, selects it in that run's config/iris.properties)
welkin/tools/pack.sh <run>/shaderpacks
# the same with options, written to Welkin.zip.txt (Iris's per-pack settings)
welkin/tools/pack.sh <run>/shaderpacks DEBUG_VIEW=1 SOFT_BANDS=0
# after adding or removing a program in shaders/program/
welkin/tools/wrappers.sh
```

Testing is by game-test screenshots, one Minecraft at a time across all sessions (the shared game-test lock):

* **With VOID LOD:** install into the LOD bench's `build/run/clientGameTest/shaderpacks`, then run its showcase
  through the LOD game-test script with `-Piris`, for example
  `-Pvoid.lod.radius=32768 -Plod.show.only=seam0,seam2,peak1,far0,sea0` (the `seam*` views look at the join with the
  chunks; `-Pvoid.lod.radius=131072` for the 128 km vista; `-Plod.show.at=name:x,y,z,yaw,pitch[,fov]` for a view of
  one's own).
* **With VOID Gloam:** install into Gloam's `build/run/clientGameTest/shaderpacks` and run its showcase through the
  Gloam game-test script with `-Piris`.

After each run, check the log for Iris shader errors (`grep "(Iris)" build/gametest.log`; a program that fails to
compile is named there), then look at the screenshots at full size, and a 200x200 crop: gradients smooth, with no
checker, dither or contour lines (the dusk sky and hazy distance especially), crisp pixels only on the sun, moon,
stars and clouds, and nothing fuzzy or airbrushed.

Each milestone also gets screenshots with Complementary, to confirm Gloam and LOD still work with other packs.
