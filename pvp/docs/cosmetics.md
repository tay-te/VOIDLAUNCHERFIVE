# Cosmetics

How VOID cosmetics are defined, painted, compiled, bundled, synced and drawn in game. The
contract they implement (slots, the equipped set, who talks to whom) is the cosmetics section of
the launch contract; the wire is `schema/protocol.json` (`cosmetics`, `cosmetics_query`, `emote`,
`equip`), the page surface is `schema/bridge.json` (`cosmetics`/`emote` events,
`equipCosmetic`, `setCosmeticTint`, `setEmoteWheel`, `playEmote`).

## The pipeline

```
schema/cosmetics.json ─┬─ scripts/gen-cosmetics.mjs ─┬─ mod/.../cosmetic/CosmeticCatalog.java
(single source:        │                             ├─ packages/protocol/src/generated/cosmetics.ts
 id, kind, name,       │                             └─ mod/src/main/resources/assets/void/cosmetics/<id>/
 rarity, price,        │                                  (compiled, validated, bundled in the jar)
 availability,         └─ backend/scripts/seed-catalog.mjs ── Supabase `catalog`
 tintable, preview)
mod/cosmetics-art/paint.mjs ── mod/src/main/cosmetics-src/<id>/   (the artwork: PNGs + source JSON)
```

```sh
cd pvp
node mod/cosmetics-art/paint.mjs [id ...]   # repaint sources (the artist's brushes; optional)
node scripts/gen-cosmetics.mjs              # compile sources + emit Java/TS catalogs
node scripts/gen-cosmetics.mjs --check      # CI gate: committed outputs are their sources
cd schema && node validate.mjs              # catalog schema + cross-checks
```

Everything generated is committed, for the reason `gen-java-registry.mjs` gives: Gradle has no
Node. The sources under `cosmetics-src/` are committed too and are the artwork; the painter is
just how the launch set was made, and an item can equally be edited by hand.

`availability` is `paid` (sold through checkout), `free` (every account owns it) or `grant`
(never sold: the founder badge). The launcher Store lists `paid` and `free`.

## The reference board: what premium cosmetic art looks like

Lunar's cosmetics are flat-textured models with canned animation: a texture of even values, lit
by nothing but the game's two fixed lights, so in daylight a cape is a poster stuck to a back.
Ours are judged against what the best Minecraft cosmetic and resource-pack artists actually do.
Every item in the launch set was repainted against this list; a new item is held to it too.

1. **Silhouette first.** Fill the item black and look at it at ten blocks. It must still say what
   it is: scalloped primaries on a wing, distinct points on a crown, a cape whose motif makes a
   shape (a seam to an emblem, a cascade, an eclipse, a seal, a skyline, a screen) rather than an
   all-over pattern. If the silhouette needs the colour to read, it is not done.
2. **Value structure before colour.** Three value groups, planned: a quiet dark field (roughly
   5-25% luminance), a mid-value motif (30-60%), and the brightest values only at the focal point
   (80%+), covering a tenth of the area or less. Squint, or desaturate the PNG: the composition
   should survive. Most of our old capes failed here: one value everywhere, so they read flat.
3. **Deliberate ramps, 4-6 shades per hue,** chosen not interpolated. `lib.mjs`'s `ramp()` is the
   starting point and the hand finishes it.
4. **Hue-shifted shadows and lights.** Shadows turn cool (toward violet-blue) and slightly more
   saturated; lights turn warm (toward ember-cream) and lose saturation. Never darken by adding
   black or lighten by adding white. The material shader does the same with occlusion (it
   darkens toward a cool violet) and the lining of every cape is shifted cool.
5. **Selective highlights.** One-texel highlights only where a form turns toward the light — the
   upper-left edge of a bevel, the tip of a point, the lip of a band — and one-texel shadows on
   the lower right. No pillow shading, no all-over noise: noise shimmers at distance and reads as
   cheap. Weave and grain stay under 3% value.
6. **Rest and detail.** Detail clusters at the focal point; the rest of the surface is quiet so
   the eye has somewhere to land. A motif stroke is at least one vanilla pixel (4 texels on a
   cape) wide and contrasts its field by 30% value or more, or it vanishes at ten blocks.
7. **A motif that tells a story.** Each item is an idea, not a pattern: Origin's first thread,
   Cell's grid dissolving, Void's eclipse, Ring's seal pressed into cloth, Dusk's last light over
   the hills with fireflies coming out, Static's signal surfacing in the snow.
8. **Light is spent sparingly.** Emissive covers a tenth of an item or less, at the focal point,
   animates slowly (4-8 fps, never flashing), and is mostly invisible by day — the bloom is
   scaled by ambient light, so the art must look complete without it.
9. **Materials do the rest.** Trims are metal thread (they glint as the cloth turns), crowns and
   halos metal, visors and the void wings' membranes glass, lit parts emissive, everything else
   matte or cloth. The art paints *colour and form*; the shader adds the view-dependent part, so a
   highlight is never painted where a material will put one.
10. **VOID's palette, never a rainbow.** Void blacks, the violets round `#9F8BFF`, ember
    `#FF9E7A`, ice `#7ADFFF`, jade `#7AE0B0`, warm and cool neutrals, and old gold for metal. A hue
    goes to full strength only where it gives off light.

## Adding a cosmetic

1. Add the entry to `schema/cosmetics.json` (id kebab-case and never reused; `preview.style` is
   the kind). Bump `version`.
2. Put its sources in `mod/src/main/cosmetics-src/<id>/` — required files per kind:

   | kind | files |
   |---|---|
   | cape | `cape.json`, `texture.png` (vanilla 64x32 cape layout, frames stacked down), optional `emissive.png`, `tint.png`, `material.png` |
   | back, head, face, halo | `model.json`, `texture.png`, optional `emissive.png`, `tint.png`, `material.png` |
   | trail | `trail.json`, `sprite.png` |
   | badge | `badge.json`, `icon.png` (9x9, frames stacked down), optional `emissive.png` |
   | emote | `emote.json` |

3. `node scripts/gen-cosmetics.mjs`, then run the showcase (below) and look at it.

## Source model format (`model.json`)

Blockbench-style cuboids and bones, in **model pixels with y pointing down**, relative to the
attachment point:

- `attach`: `body` (origin at the neck; torso x,z in [-4,4]x[-2,2], y 0..12, back face at z=+2),
  `head` (origin at the neck; head x,z in [-4,4], y -8..0, face at z=-4), or `halo` (the head's
  position and yaw, a third of its pitch).
- `bones[]`: `name`, `parent`, `pivot`, `rotation` (degrees, applied Z then Y then X like
  `ModelPart`), `cubes[]`. Cubes are absolute (`from`, `to`), with either Minecraft box UV
  (`uv: [u, v]`) or per-face rects (`faces: { north: [u0, v0, u1, v1], ... }`).
- `texture_size`, optional `frames: { count, fps }` (animated strip, every layer the same),
  `helmet_fit` (head items grow to clear a helmet), `procedural` (`wings`, `halo`, `float`),
  `glow[]` (bloom anchors: `bone`, `at`, `color`, `size`, `strength`), `animations.idle`
  (keyframes per bone: `[t, [x, y, z], ease]`, ease one of linear, step, sine, quad, cubic, back,
  elastic, bounce).

- `material` on the model, a bone or a cube (the cube's wins, then its bone's, then the
  model's; default `matte`) — see Materials below.

**Face UV conventions** (the runtime and the painter agree on these): n/s/e/w are the -z/+z/+x/-x
sides, u/d the -y/+y faces. Side faces have v running down. n: u along +x; s: u along -x; e: u
along +z; w: u along -z; u/d: u along +x, v along -z. Box UV resolves to exactly `ModelBox`'s
layout.

**Wings** must name their bones `wing_r`, `wing_r_tip`, `wing_l`, `wing_l_tip` (right = model
-x). `Procedural` spreads, sweeps, flaps and folds them by state: half open and breathing at
rest, swept back at a sprint, spread to glide while falling, beating while rising or flying,
folded tight while sneaking — every target through a critically damped spring.

## Materials

Every surface of a cosmetic is lit by the material shader (`cosmetic.vsh`/`cosmetic.fsh`, driven
by `CosmeticShader`), which starts from Minecraft's own entity lighting — its two fixed lights,
the 0.4 ambient, the lightmap texel where the player stands, the fog — so nothing looks pasted
onto a skin lit differently, and adds per material:

| material | what it does | use it for |
|---|---|---|
| `matte` | vanilla's diffuse, contact occlusion, a rim of sky light | ceramic, bone, wood, the default |
| `cloth` | wrapped diffuse (a soft terminator), slightly less ambient so form reads in daylight, a grazing velvet sheen | capes (their default), felt, knit, feathers |
| `metal` | a stylised environment reflected about the normal (dark ground, a bright horizon band, sky) plus a tight specular, so it glints as it turns | crowns, halos, trims, buckles, metal thread |
| `glass` | tinted and see-through (alpha by Fresnel: clear face-on, mirror-like at a glance), a sky reflection, a hard highlight | visors, gems, the void wings' membranes |
| `emissive` | full bright, and into the bloom | filaments, motes, cores: parts that *are* light |

All materials share **contact occlusion** — each vertex is measured against the wearer's torso
(back items, capes) or head (hats, masks) as a box, and darkens toward a cool violet when it is
close to it and facing it: wing roots, the underside of a brim, a cape's lining where it lies on
the back — and a **rim** of sky light on edges turned away from the camera, scaled by the
lightmap so it fades in caves.

How an item names them:

- **Models**: `material` on the model, a bone or a cube in `model.json`; compiled to `m` per
  cube and carried to the shader as each vertex's second texture coordinate.
- **Capes**: `material` in `cape.json` is the sheet's material (default `cloth`).
- **Per texel** (capes and models): an optional `material.png`, the texture's width and one frame
  (or the whole strip) tall, painted in key colours — black matte, blue `#0000FF` cloth, yellow
  `#FFFF00` metal, cyan `#00FFFF` glass, white emissive; transparent means "the part's own". It is
  folded into the albedo's alpha at load (254 matte, 253 cloth, 252 metal, 251 glass, 250
  emissive) — so **albedo alpha 250-254 is reserved** in a texture that has a mask. The launch
  capes use it for their metal-thread trims, the static cape for its glass screen.

Without GLSL (or in the inventory preview, whose matrices are the screen's) everything draws the
old fixed-function way: same textures, vanilla lighting, no materials.

## Emotes (`emote.json`)

`length`, `loop` + `loop_from` (held emotes such as sit), `blend` (seconds to ease in and out),
`camera` (`low` for a lower orbit), and `parts.<part>.rotation|position` keyframes, parts being
`root` (whole body, e.g. down onto the ground), `upper` (bends head, torso and arms about the
waist), `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`. Model space: -z is
forward, -x the player's right; arm x < 0 swings forward/up, right arm z > 0 swings outward.
One-shot emotes must end at rest (a test enforces it).

Playback (`Emotes`, `EmotePose`): wall-clock sampled, blended from vanilla's pose by an eased
weight, re-copied onto the overlay layers; cancelled by moving, jumping, sneaking, attacking or
using (a 250 ms grace covers the click that started it), and remote emotes are cancelled the same
way when the player visibly moves or swings. The local player gets `EmoteCamera`: the view eases
out of the head into a slow third-person orbit and back, restoring the player's perspective.

## Runtime

`dev.voidpvp.client.cosmetic`:

- `CosmeticRenderer` — a feature layer on both player renderers. Capes are cloth (`Cloth`, a
  world-space position-based grid at a fixed step with an accumulator, so it moves the same at
  every frame rate; collides with the posed torso and legs; aerodynamic drag lifts it when you
  run). The sheet is a quarter wider at the hem than at the collar and gathered into pleats at
  the top, so it hangs as a bell with soft folds; an idle breeze with gusts keeps it moving; it is
  shaded per vertex from its own curvature (folds read in daylight), with a darker, cool-shifted
  lining and lit edge strips. Capes are painted at 4x density, models at 2x. Models are display
  lists per bone (each vertex carrying its cube's material) with albedo, tint-mask and
  full-bright additive emissive layers. Every pass goes through the material shader (above).
- `Bloom` — the glow. While a player whose outfit has an emissive part draws its cosmetics, a
  full-size mask texture attached to Minecraft's framebuffer as a second colour attachment
  receives the shader's emissive output (black from every other cosmetic surface, so a glow
  behind something is hidden — same matrices and depth test as the visible pass, so nothing glows
  through a wall). After the world, before the hand and the HUD, the mask is averaged down to a
  quarter, an eighth and a sixteenth of the screen, each level blurred with a separable 5-tap
  Gaussian (three bilinear fetches per axis), and the levels are added back through a soft knee.
  Strength is per player and written into the mask: about a third in daylight, full on a dark
  night, and per slot multiplied by the fight-reaction intensity (`slotIntensity`, from the
  motion stream's `Reactive`), as the emissive layer is — the shader adds above 1 in its one pass,
  no second draw. The `cosmetics` mod's `glow_level` picks `off` (no mask, the pass never runs), `low` (two
  levels, gentler) or `high`. A frame in which no emissive cosmetic was drawn skips the pass
  entirely. Everything is best-effort: no FBOs, fewer than two draw buffers or a program that
  will not build turns it off once, logged, and `GlowBatch`'s soft additive sprites stand in.
  All of it runs inside saved and restored GL state (`-PvoidGlDebug` logs any GL error with its
  Java stack, and every few seconds how far the rebuilt modelview is from GL's).
- `Trails` — a fixed particle pool drawn after vanilla particles; density by speed, nothing when
  standing still, per-player caps.
- `Badges` — the 9x9 icon beside the nametag, two-pass like the name; any VOID player without a
  badge gets the plain VOID ring marker.
- `CosmeticsHub` — per-tick queries (`cosmetics_query`, batched, 10 min TTL, no v2 NPC uuids),
  emote housekeeping, the dev hooks.
- Cost: the twenty-player showcase scene (every player in a full outfit, all within 14 blocks)
  measured about 0.46 ms a frame for the whole layer with the LOD pinned at full quality
  (`-Pvoid.showcase.quality=1`) on the fixed-function path. The material shader draws an item's
  albedo, tint and emissive layers in one pass instead of three, which pays for itself: `perf-ab`
  measures it at 0.82-0.99x the fixed-function cost, and the shader plus the bloom at 1.01-1.27x
  (two runs on a heavily loaded machine, every mode in alternating windows of the same run; the
  bloom's own share is a composite of about 80-115 us and draw-buffer switches of 30-45 us under
  that load). Scaled to the idle measurement that is roughly 0.47-0.58 ms, inside the 0.6 ms the
  layer is allowed. `Budget.report()` prints the split (cape sim, mesh, draw; models; glow) and
  the bloom's line. Distant low-resolution capes integrate at 60 Hz and reuse their mesh between
  steps; model emissive layers drop past the mid band by day. Capes draw from a per-sheet vertex
  buffer uploaded once a frame; the modelview is rebuilt from the camera view matrix the game
  already holds (`-PvoidGlDebug` checks it against GL's: within 5e-7), never a `glGetFloat`.
- Respects invisibility (nothing drawn), spectators (no layer), sleeping (head items only),
  riding (no trail), the inventory preview (the cape holds its shape), the `cosmetics` mod
  switches, and LOD by distance scaled by `Budget.quality`, which shrinks the bands when the layer
  costs more than 0.5 ms a frame.
- The vanilla cape is skipped for a player wearing a VOID cape (`CapeFeatureRendererMixin`).

Ownership: the wire carries who wears what, not who owns what. With a launcher attached an equip
applies optimistically and the backend reverts a refused one with a `cosmetics` self; without one
only free items can be equipped. The tint and the emote wheel are session-only until the protocol
carries them.

## Dev: seeing it without a launcher

- `-Dvoid.dev.cosmetics=<file.json>`: applies a `cosmetics`-shaped object (`self`, `players`) at
  start and owns everything.
- `./gradlew runShowcase` (in `mod/`): a flat creative world, mannequins wearing every item,
  scripted scenes (line-ups by day and night, close-ups, running capes, a glide, every emote,
  nametag badges), stills and frame sequences written to `build/showcase` (or
  `-Pvoid.showcase.out=<dir>`), then it quits. `-Pvoid.showcase.only=scene,scene` runs a subset.
  Its log is `<out>/showcase.log`. Take the shared game-test lock first.
- Showcase switches: `-Pvoid.showcase.quality=1` pins the LOD (for measuring);
  `-Pvoid.showcase.glow=off|low|high` pins the glow level; `-Pvoid.showcase.shader=false` draws
  the fixed-function way; `-Pvoid.showcase.bloomView` draws the bloom mask and its blurred quarter
  level in the lower left of every frame; `-PvoidGlDebug` logs every GL error with the Java stack
  that raised it (zero is the bar) and how far the rebuilt modelview is from GL's.
- Scenes include `far-back` / `far-back-night` (the line-up at ten blocks, the distance an item
  has to read at in a fight) and `perf-ab`: the twenty-player crowd measured in alternating
  windows — fixed-function, material shader, shader + bloom — so whatever else the machine is
  doing weighs on every mode alike; the per-mode means go to `showcase.log`.
