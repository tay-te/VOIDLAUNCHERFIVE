# VOID Gloam: the look

Gloam should still feel like Minecraft. It adds texture to the game, and the texture is **pixel-true**: it looks as
if a careful texture pack drew it, not a photograph and not a modern engine. The user chose this on 2026-10-07, over a
painterly toon look and an all-blocks look, after rejecting soft volumetric clouds as "too fuzzy".

The surface stays natural (real-world colours and weather, nothing glowing or fantasy-coloured). Stylized is about
*how* things are drawn, not what they are. Underground may stay fantastical.

## The rules

1. **The 16-pixel grid.** Anything drawn in the world, such as a mist sheet, a veil or a rain curtain, carries texture
   at Minecraft's own density: 16 texels per block, sampled nearest. Hold that density in world space where it can be
   held (snap texture coordinates to world texels). Far things may use coarser texels (8 per block, 4, 2, 1 with
   distance), the way the LOD's blocks get coarser, but never smooth blur.
2. **Steps, not gradients.** Opacity and colour change in a few flat steps: 3–5 opacity levels for mist and haze, 4–6
   colour bands for a dawn or dusk sky. A step is a hard edge.
3. **Dithered edges.** Where a soft fade is needed (the top of a mist bank, a veil's rim, a haze edge), use an ordered
   (Bayer 4×4) dither on the world texel grid, not alpha blending. Treat translucency as a few levels plus dither.
4. **Crisp pixel motes.** Particles are small pixel sprites, nearest-filtered, at Minecraft's particle pixel size
   (vanilla particles are 8×8 sprites drawn about 0.1–0.2 blocks across). No soft glow halos: a firefly is a couple of
   bright pixels with a darker pixel ring at most, and it blinks on and off in steps.
5. **Clean, limited palettes.** Each effect has a small hand-picked palette (mist: 3–4 tones from near-white to the
   shadow tone; dusk: gold, orange, rose, violet, dusk blue). Colours are clean and slightly saturated, like Minecraft's
   own textures, never muddy. Choose from the palette; don't blend freely between arbitrary colours.
6. **Blocky where shapes are big.** Clouds are voxel (done). Big masses, such as fog banks in far valleys or storm rain
   curtains, read as stacked pixel layers or block-stepped silhouettes, not smooth volumes.
7. **Works with shader packs.** A pack lights things its own way, but the pixel texture, dither and steps live in the
   geometry and sprites, so they survive the pack's lighting.

## How to check

Every change gets an off/on screenshot pair, with and without Complementary. Zoom into a 200×200 crop: you should be
able to see the pixel grid, the steps and the dither, and nothing should look airbrushed.
