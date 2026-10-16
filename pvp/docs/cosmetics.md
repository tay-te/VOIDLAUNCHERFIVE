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

## Adding a cosmetic

1. Add the entry to `schema/cosmetics.json` (id kebab-case and never reused; `preview.style` is
   the kind). Bump `version`.
2. Put its sources in `mod/src/main/cosmetics-src/<id>/` — required files per kind:

   | kind | files |
   |---|---|
   | cape | `cape.json`, `texture.png` (vanilla 64x32 cape layout, frames stacked down), optional `emissive.png`, `tint.png` |
   | back, head, face, halo | `model.json`, `texture.png`, optional `emissive.png`, `tint.png` |
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

**Face UV conventions** (the runtime and the painter agree on these): n/s/e/w are the -z/+z/+x/-x
sides, u/d the -y/+y faces. Side faces have v running down. n: u along +x; s: u along -x; e: u
along +z; w: u along -z; u/d: u along +x, v along -z. Box UV resolves to exactly `ModelBox`'s
layout.

**Wings** must name their bones `wing_r`, `wing_r_tip`, `wing_l`, `wing_l_tip` (right = model
-x). `Procedural` spreads, sweeps, flaps and folds them by state: half open and breathing at
rest, swept back at a sprint, spread to glide while falling, beating while rising or flying,
folded tight while sneaking — every target through a critically damped spring.

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
  shaded per vertex from its own curvature (folds read in daylight), with a darker lining and lit
  edge strips. Capes are painted at 4x density, models at 2x. Models are display lists per bone with albedo, tint-mask and full-bright additive emissive
  layers; `GlowBatch` adds soft additive bloom sprites in eye space, depth-tested, scaled down in
  daylight.
- `Trails` — a fixed particle pool drawn after vanilla particles; density by speed, nothing when
  standing still, per-player caps.
- `Badges` — the 9x9 icon beside the nametag, two-pass like the name; any VOID player without a
  badge gets the plain VOID ring marker.
- `CosmeticsHub` — per-tick queries (`cosmetics_query`, batched, 10 min TTL, no v2 NPC uuids),
  emote housekeeping, the dev hooks.
- Cost: the twenty-player showcase scene (every player in a full outfit, all within 14 blocks)
  measures about 0.33 ms a frame for the whole layer at 120 fps with the adaptive budget, and about
  0.46 ms with the LOD pinned at full quality (`-Pvoid.showcase.quality=1`); `Budget.report()`
  prints the split (cape sim, mesh, draw; models; glow). Distant low-resolution capes integrate at
  60 Hz and reuse their mesh between steps; model emissive passes drop past the mid band by day. Capes draw
  from a per-sheet vertex buffer uploaded once a frame; glow is placed from the camera view matrix
  the game already holds, never a `glGetFloat`.
- Respects invisibility (nothing drawn), spectators (no layer), sleeping (head items only),
  riding (no trail), the inventory preview (the cape holds its shape), the `cosmetics` mod
  switches, and LOD by distance scaled by `Budget.quality`, which shrinks the bands when the layer
  costs more than 0.5 ms a frame.
- The vanilla cape is skipped for a player wearing a VOID cape (`CapeFeatureRendererMixin`).

Ownership: the wire carries who wears what, not who owns what. With a launcher attached an equip
applies optimistically and the backend reverts a refused one with a `cosmetics` self; without one
only free items can be equipped. The tint and the emote wheel are session-only until the protocol
carries them.

## Motion and reactions

Lunar-style cosmetics play a canned loop whatever happens. Ours answer the body and the fight.

**Secondary motion** (`Secondary`, `DampedSpring`): every worn thing has inertia on top of its
pose. Hats ride the head on an underdamped spring (a snap turn leaves the brim a beat behind, it
rocks once and settles; a landing presses it down). Halos and floating back items follow the
head through their own looser spring: they trail when you run, drift back when you stop, tilt
like a pendulum against the lag. Landings squash wings, halo, hat and cape in proportion to the
fall; a jump gives the wings a downstroke and a sprint-jump lifts the cape. Wing feather layers
and tips chase the arm on progressively looser, later springs, so a beat or a flinch rolls out
along the wing. The cape gets the body's acceleration exaggerated into the cloth (a whip on every
change of direction, a flare on sharp turns) and one-shot kicks (`Cloth.kick`, hem-weighted
velocity) plus a travelling ripple (`Cloth.wave`).

All of it is exact integration of damped springs over real frame time with interpolated inputs:
the same at 30 fps and 400, smooth between ticks. Springs of one kind share their coefficients
(`DampedSpring.Params`, keyed on the frame dt every player steps with), so twenty players pay the
exp/sin/cos once per kind per frame. Past the mid LOD band, and in the inventory preview, the
springs rest on the body and the old cheap procedural offsets take over.

**Reactions** (`FightWatch` -> `Reaction` -> `Reactive`), only from what the client already sees:

| event | detected from | what reacts |
|---|---|---|
| hurt | rising edge of `hurtTime` (vanilla's red flash) | cape whips out, wings flinch in and tremble, halo wobbles, hat jolts |
| landed hit | the local player's own click on that entity (14-tick window), else the remote player who started a swing within 8 ticks, in reach (4.5) and facing the victim | wings flare progressively (6 hits to full), trail thickens, halo brightens a step per hit and spins up, cape billows |
| kill / streak | death (health 0 or the death animation) within 5 s of a credited hit; kills within 20 s are a streak | halo pulses (scale and light), cape ripples outward, wings give one full-spread beat |
| low health | the local player's own health under 35% | everything dims, to at most 30% (albedo at most 15%) |

Another player's health is never read. `cosmetics.reactive` scales all of it: `off` 0 (every
intensity exactly 1, no pulses), `subtle` 0.45, `full` 1 (default).

### For the bloom pass: intensity and pulses

```java
// Emissive/bloom multiplier for one slot of one player: 1 at rest, <1 dimmed (low health),
// >1 while a combo builds, a hit lands or a kill flourishes (capped 2.5). Smooth in time.
float k = Reactive.intensity(uuid, "halo");   // slots: cape back head face halo trail badge
float k2 = Reactive.intensity(player.getUuid(), "back");

// One-shot events, on the client thread, the tick they are detected.
Reactive.addListener(new Reactive.Listener() {
    public void onPulse(Reactive.Pulse p) {
        // p.uuid, p.kind (HURT, HIT, KILL), p.count (combo / streak), p.strength 0..1, p.self
    }
});
```

The renderer already multiplies emissive texels (a second additive pass carries anything above
one) and `GlowBatch` sprite strength by it. A bloom pass should multiply its own strength by the
same number; the pulses already live inside the curve, so a listener is only for edge effects.

## Dev: seeing it without a launcher

- `-Dvoid.dev.cosmetics=<file.json>`: applies a `cosmetics`-shaped object (`self`, `players`) at
  start and owns everything.
- `./gradlew runShowcase` (in `mod/`): a flat creative world, mannequins wearing every item,
  scripted scenes (line-ups by day and night, close-ups, running capes, a glide, every emote,
  nametag badges), stills and frame sequences written to `build/showcase` (or
  `-Pvoid.showcase.out=<dir>`), then it quits. `-Pvoid.showcase.only=scene,scene` runs a subset.
  Its log is `<out>/showcase.log`. Take the shared game-test lock first. The motion scenes:
  `fight`/`fight-night` (a hit taken, a six-hit combo, a kill), `turns` (snap turns, strafe
  reversals, a jump and a high landing), `sprint-jump`/`sprint-jump-night`, and `brawl-20` (ten
  duels at once, the frame budget's worst case); `fight-off` and `turns-off` are the same scripts
  with motion and reactions switched off, for side-by-side comparison.
