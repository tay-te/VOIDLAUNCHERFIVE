# VOID Mettle card textures

PNG textures and sprite strips for the Mettle perk cards inside VOID Hub. The design is in Figma
`3c0Wgc4ZKFSpm98fWJoARS`, page 295:2, sections 2h (546:23595), 2f (510:18631, D + Mythic M1-M4)
and 2g (set complete 539:23542).

- Generator: `hub/tools/cards/gen_cards.py` (Python 3 + Pillow + numpy). It rewrites this folder,
  `manifest.json` and `preview.html`. Seeds are fixed, so the output is the same every run.
- `manifest.json` is the source of truth for every asset: frame size in CSS px, frame count, fps,
  file per scale, decoded memory per scale and a one-line `about`.
- `preview.html` shows every asset in motion, using only these files and plain CSS. Open it in the
  hub's Ultralight (classpath root `assets/voidhub/ui/`, page `cards/preview.html`) or in any browser.

## Scales and crispness

Everything is pixel art on a fixed texel grid:

| grid       | 1 texel | card          | edge frame            | frame offset      |
|------------|---------|---------------|-----------------------|-------------------|
| hand       | 4 px    | 248 x 328     | 304 x 384             | -28 px, -28 px    |
| collection | 2 px    | 96 x 128      | 112 x 144             | -8 px, -8 px      |
| icons      | 2 px    | glyph 16, gem 14, dot 6 | -           | -                 |

Every asset has these files:

- `<key>_px.png`: native texels. Kept for editing only; do not display it.
- `<key>_x1.png`: the exact CSS display size. Use this at device scale <= 1. GUI scale 3 is
  `u = 1`; see `HubLayout.unit`.
- `<key>_x2.png` / `_x3.png`: nearest-neighbour copies for device scales above 1 and above 2. The
  glyph sheets also come in `_x5` (80 px), for the set-complete moment.

Ultralight smooths every image it scales and ignores `image-rendering: pixelated`. Always set
`background-size` / `-webkit-mask-size` to the x1 CSS size (frame width x frame count by frame
height). Then pick the file whose scale is at least the device scale. Ultralight only shrinks it,
which keeps the edges sharp. The x2/x3 files cost 4x/9x the decoded memory (see `decodedBytes`), so
switch file sets by scale rather than always loading x3.

## Colour: masks, not baked copies

`-webkit-mask-image` works in the hub's Ultralight 1.4 build (CPU view). It was tested headless with
the hub's own natives: a static mask, an animated `-webkit-mask-position` with `steps()`, partial
alpha, strips up to 8192 px wide, and masks with a stepped `opacity`. So the burning edges are
**not** baked per skill. Each "tinted" asset has two layers that share geometry and timing:

- `*_fill_*.png`: a white mask. Draw it as `-webkit-mask-image` on a box whose
  `background-color` is the tint:
  - skill colour (`manifest.colours.skills`, from the Mettle skill json `colour`);
  - `#FF2E4A` for Cursed;
  - `#E8DDBC` for Earned (champagne).

  Mask alpha 1 gives the base tone. Alpha 0.4 gives the char tone: the tint at 40 % over the
  near-black card is the design's dark tone.
- `*_glow_*.png`: white highlights at 55 % (the design's light tone) and 85 % (white-hot). Draw it
  as a normal `background-image` on top of the fill.

`mix-blend-mode` is not available in Ultralight, and none of this needs it. Mythic, the card back,
ash, rays, motes, burst, crack, foil and ring have their own fixed colours and are baked
(`kind: colour`).

Fallback: `python hub/tools/cards/gen_cards.py --bake` writes pre-tinted copies for all 15 skills +
cursed + earned into `hub/build/cards-baked/` (765 files, about 8 MB). Nothing ships them.

## Strip animation (CSS)

All strips are horizontal. The page steps them with `steps(n)`:

```css
.edge { position:absolute; left:-28px; top:-28px; width:304px; height:384px; pointer-events:none; }
.edge-fill {
  background-color: var(--skill);                           /* tint */
  -webkit-mask-image: url(cards/hand/edge_rare_fill_x1.png);
  -webkit-mask-size: 1216px 384px;                          /* 4 frames x 304 */
  -webkit-mask-repeat: no-repeat;
  animation: edge-m .5s steps(4) infinite;                  /* 4 frames @ 8 fps */
}
.edge-glow {
  background: url(cards/hand/edge_rare_glow_x1.png) 0 0 / 1216px 384px no-repeat;
  animation: edge-b .5s steps(4) infinite;
}
@keyframes edge-m { from { -webkit-mask-position: 0 0 } to { -webkit-mask-position: -1216px 0 } }
@keyframes edge-b { from { background-position: 0 0 } to { background-position: -1216px 0 } }
```

### One-shot strips in a loop

For a one-shot strip inside a loop (pick, reveal, foil every 4 s), use one keyframe per segment.
Put a different `animation-timing-function` on each keyframe, for example `steps(1)` while waiting,
then `steps(n)` while the strip plays. A position past either end of a no-repeat strip draws
nothing.

Gate the layer with `opacity` as well.

**Ultralight pitfall (verified):** a masked element inside an ancestor that has
`overflow:hidden` **and** `border-radius` loses its mask and paints a solid box of the tint colour.
Either property alone is fine. If a panel must clip with rounded corners, keep the masked layers
outside it, or clip with a square box. `preview.html` (`Css.oneshot` in `tools/cards/preview.py`) is a working
example.

## Files

### Hand mode (`hand/`), card 248 x 328, frames 304 x 384 at -28 px

All hand burns stay within a 12 px rim inside the card. Text keeps the 28 px safe area.

| key | layers | frames | timing | use |
|-----|--------|--------|--------|-----|
| `edge_common` | fill + glow | 4 | 8 fps loop | Common: smoulders. Thin rim, mostly char, few highlights. |
| `edge_rare` | fill + glow | 4 | 8 fps loop | Rare: 2-texel rim, flames up to ~11 px, sparks. |
| `edge_epic` | fill + glow | 4 | 8 fps loop | Epic: fully alight. Tall flames, white-hot core, many sparks. |
| `edge_cursed` | fill + glow | 4 | 8 fps loop | Cursed: jagged, more char. Tint `#FF2E4A`. |
| `edge_wildcard` | fill + glow | 4 | 8 fps loop | Wildcard (card from another skill): Rare heat with burnt-through holes. Tint with that skill's colour. |
| `edge_hover` | fill + glow | 4 | 8 fps loop | Hot frame. On hover, lift 16 px + scale 1.05 in 160 ms and cross-fade this layer's opacity in 120 ms over the rarity edge. |
| `embers_sparse` / `embers_dense` | fill + glow | 6 | 1.2 s loop (5 fps) | Embers rise 12 px and fade. Sparse for Common/Rare, dense for Epic/Cursed/hover. |
| `flare` | fill + glow | 4 | 50 ms each (200 ms) | Pick: the kept card flares. Add a radial-gradient halo in the skill colour, then fly the card to the hand. |
| `burn_mask` | mask | 9 | frame 0 intact, then 8 x 75 ms | Card-sized (248 x 328, offset 0). Put it on the **card face element** as `-webkit-mask-image` with `-webkit-mask-size: 2232px 328px`. Keep the edge/ember layers as siblings, not children, and fade them out as the burn starts. |
| `burn_front` | fill + glow | 8 | 75 ms each | The burning front eating the card; frame k pairs with `burn_mask` frame k+1. Tint with the skill colour. |
| `burn_ash` | colour | 8 | 75 ms each | Grey ash pixels drifting up. |
| `back` | colour | 1 | - | Face-down card with the white-gold halo baked in. Used for the Mythic reveal and deal-in. |

### Collection mode (`collection/`), card 96 x 128, frames 112 x 144 at -8 px

At rest a collection card does not burn: draw a 1.5 px CSS border in the rarity colour
(`manifest.colours.rarity`), with a 10 px inset for the panel and text. While hovered, lift 4 px in
120 ms and show:

| key | layers | frames | timing |
|-----|--------|--------|--------|
| `hover_common`, `hover_rare`, `hover_epic`, `hover_cursed`, `hover_wildcard` | fill + glow | 2 | 6 fps loop (`steps(2)` over .333 s) |

Tint with the skill colour; for cursed, tint crimson.

### Mythic (`mythic/`), white-gold, never tinted, never flickers

From back to front:

1. **Bloom**: a CSS radial-gradient from white-gold to violet to clear.
2. **Rays**: `rays` (`rays_x1` 512 px, `rays_x2` 1024 px; soft, not pixel art, because it rotates).
   Centre it on the card and rotate 6°/s (`rotate` 60 s linear infinite). Add a second copy at
   60 % opacity rotating the other way.
3. **Pulse ring**: `ring` (480 px pixel ring), scale 0.95 to 1.08 with opacity to 0 over 2.4 s. A
   CSS radial-gradient ring is a cheaper alternative.
4. **Prismatic halo**: a CSS radial-gradient, cyan to pink, just outside the card.
5. **Motes**: `mote_white`, `mote_gold`, `mote_cyan`, `mote_pink`. Each is one 10 x 10 px mote,
   12 frames at 10 fps. Scatter about 22 around the card, each with its own negative
   `animation-delay`, plus a slow `translate` drift (3 s alternate). This costs 4.8 KB decoded per
   colour instead of a full-screen 12-frame field.
6. **Fire edge**: `edge` (304 x 384 at -28 px), 6 frames at 10 fps. White base, gold body,
   cyan/pink tips.
7. **Foil sheen**: `foil` (card-sized 248 x 328), 16 frames at 10 fps, once every 4 s. Clip it to
   the art panel with `overflow:hidden`. It is a background, not a mask, so a rounded clip is fine.
   Keyframes:
   `0% { pos 0; steps(16) } 40% { pos -3968px } 100% { pos -3968px }`.

The reveal (about 2.6 s, storyboard M2) uses three more strips:

- `crack`: 8 frames at 10 fps, on the same frame as `hand/back`.
- `burst`: 448 x 448, 10 frames at 10 fps, centred on the card at the flip.
- a CSS radial white flash.

### Set complete (`set/`), 384 x 256, centred on the keyword glyph

| key | kind | frames | timing |
|-----|------|--------|--------|
| `rays` | colour | 12 | 10 fps, once (1.2 s): rays plus white pixel sparks |
| `sparks` | mask | 12 | same timing. Tint with each counted skill's colour; mirror the second copy with `scaleX(-1)` |

The glyph scales in with `icons/glyphs_x5` (80 px). The bloom is a CSS radial-gradient.

### Icons (`icons/`), sheets with one frame per entry in `order`

| key | size (x1) | order | note |
|-----|-----------|-------|------|
| `gems` | 14 px | common, rare, epic, cursed, mythic, earned | `background-position: -14px * i` |
| `gem_mask` | 14 px | - | silhouette to tint, e.g. a wildcard gem |
| `trait_dots` | 6 px | same as gems | one dot per trait tier |
| `trait_dot_mask` | 6 px | - | |
| `glyphs` | 16 px (x5 = 80 px) | brutal, deep, echo, ember, gale, hex, hoard, iron, keen, kin, swift, tide, warden, wild | 8 x 8 pixel glyphs copied from the Figma components (539:23796), neutral `#D8CDBB` |
| `glyph_mask` | 16 px | same | white silhouettes to tint |

## Memory

The hand strips decode to 2.8 to 7.5 MB each at x1. Times 4 at x2, times 9 at x3; see
`decodedBytes` in the manifest. Load only the rarities on screen, and use one URL per strip so
three cards share one decoded image. The PNGs together are about 1.3 MB on disk.
