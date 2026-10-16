# Cosmetics style test

The same three items (a cape, a back item, a hat) in two candidate directions, rendered in game
against the current v3 set (`void-cape`, `void-wings`, `dapper-top-hat`), to choose a direction for
the whole cosmetics line.

| | v3 (reference) | C · VOID luxe | D · Collectible toy |
|---|---|---|---|
| cape | Void cape | `style-c-cape` Atelier cape | `style-d-cape` Hero cape |
| back | Void wings | `style-c-wings` Blade wings | `style-d-wings` Puff wings |
| head | Top hat | `style-c-hat` Atelier fedora | `style-d-hat` Propeller cap |

- **C · luxe**: ivory, graphite and ink, brushed steel once per item, and one accent: a single
  violet glass stone (VOID's `#9F8BFF`), the only light in the set. Heavy cloth (the cape hangs),
  wings that barely stir (`motion: { rate: 0.7, amp: 0.45 }`).
- **D · toy**: one curated four-hue set (coral, butter, teal, cream) with hue-shifted pixel
  shading, chunky shapes with real volume (the wings are a pixel-art sprite extruded like a
  Minecraft item), a scalloped hem cut out of the cape, a propeller that spins up in bursts, a light
  cape and fluttering wings (`motion: { rate: 1.7, amp: 2.2 }`).
- Both are at the skin's texel density (capes 64 x 32, models one texel per pixel), flat colour
  with single-texel shading, no gradients, and wings about 1.7-2.2 blocks across instead of 3.5.

The items are catalog entries flagged `test` (see `docs/cosmetics.md`): they ride in the jar for the
showcase and never reach the Store, the wardrobe or the backend. Art: `mod/cosmetics-art/style-test.mjs`.

## Contents

- `sheets/<view>.png`: one sheet per view, three labelled columns (the middle 640 px of each set's
  frame, staged identically, Steve skin, LOD pinned at full quality). Views: `front-day`,
  `back-day`, `front-night`, `back-night`, `far-*` (ten blocks; `far-*-2x` is the same pixels
  enlarged), `close-cape`, `close-back`, `close-head`, `close-back-night`.
- `sheets/all-*.png`: the three sets abreast in one frame.
- `clips/run-<set>.mp4`, `clips/fight-<set>.mp4`: running and turning (a sprint round a circle, a
  snap reversal, two sprint-jumps, a stop) and the scripted fight (a hit taken, a six-hit combo, a
  kill) per set; `clips/*-compare.mp4` the three side by side.

## Reproduce

```sh
cd pvp/mod   # take the game-test lock first
./gradlew runShowcase -Pvoid.showcase.quality=1 -Pvoid.showcase.only='st-*' -Pvoid.showcase.out=<out>
bash ../design/cosmetics-style-test/make-sheets.sh <out>
```
