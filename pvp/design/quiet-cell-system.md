# The quiet cell system — implementation contract

Settled in Figma (`RShlfbx2TfxrdleKPIry8w`, pages "Source of truth — Desktop Client" /
"— In-game Client"). This file is the contract every package codes against. Where this
file and the existing code disagree, **this file wins**.

## 0. What changed from the system already in `@void/ui`

| Was | Now |
|---|---|
| Bricolage Grotesque display + DM Mono + Outfit | **Outfit only.** No display face, no monospace. |
| `--blur-panel/-dock/-dim`, backdrop-filter | **Flat.** No blur, no shadow, no gradient, anywhere. |
| Depth carried state (glow / shadow) | **Fill steps carry state.** shell → ground → card → raised. |
| Accent used for tints, borders, selection chrome | **Accent marks the live value or the selected item, nothing else.** |
| — | **Category hues**: a mod's accent is its category's hue. |
| — | **Cell primitive**: the atom everything textural is built from. |

## 1. Colour

```
--bg-shell    #0B0B0C   window chrome, outermost
--bg-base     #131315   recessed canvas inside the shell   (GROUND)
--card-bg     #191A1C   tiles, panes, cards                (CARD, opaque — no alpha)
--surface-raised #212225 hover / selected surface          (RAISED)
--text-primary   #EDEEEF
--text-secondary #9A9DA1
--text-muted     #626569
--ok          #3DD68C
```

Borders are white at low alpha, never a colour:
`--border-panel rgba(255,255,255,.10)`, `--border-raised rgba(255,255,255,.07)`,
`--border-strong rgba(255,255,255,.14)`, `--divider rgba(255,255,255,.045)`.

### Category hues

```
--hue-hud      #9F8BFF   (also --accent, the default)
--hue-pvp      #FF9E7A
--hue-visual   #7ADFFF
--hue-utility  #7AE0B0
```

A mod's live value, selection dot and drag handles take **its own category hue**.
Expose it as `--hue` set on the mod's root element; every accent-consuming rule reads
`var(--hue, var(--accent))`. Never hard-code `--accent` inside a mod-scoped component.

**The accent rule.** Colour is never decoration. If it is on screen, something is live:
the current value of a control, the selected item, or a drag handle. Historical data,
counts, disabled rows and every meter cell that is not the current one stay monochrome.

## 2. Type — Outfit only

```
--font-ui: "Outfit", system-ui, -apple-system, "Segoe UI", sans-serif;
--font-display: var(--font-ui);   /* alias, kept so callers don't break */
--font-mono:    var(--font-ui);   /* alias — see "tabular numerals" below */
```

Weights: 300 Light, 400 Regular, 500 Medium. Nothing heavier.

Four tiers. Do not invent sizes between them:

| tier | size | weight | use |
|---|---|---|---|
| display | 30 / 34 / 40 | 400 | screen titles, hero |
| title | 15 / 17 | 500 | pane headings, primary buttons |
| body | 13 / 14 | 400 (300 for secondary prose) | rows, labels, descriptions |
| label | 9 / 10 | 500, `letter-spacing:.12em`, UPPERCASE | eyebrows, categories, meta |

Display tracks `-0.02em`. Body and title track `0`.

**Tabular numerals.** Dropping the monospace costs digit alignment on live-updating
numbers (FPS, CPS, ping, coordinates). Fix it with
`font-variant-numeric: tabular-nums; font-feature-settings:"tnum" 1;` on any element
whose digits change at runtime — class `.tnum`. Do **not** reintroduce a mono family.

## 3. The cell primitive

Everything textural is a cell: a square with `border-radius: 30% of its size`.

```css
.cell { border-radius: 30%; }        /* size set by the caller, always square */
```

| role | fill |
|---|---|
| filled / on | `rgba(237,238,239,.36–.45)` |
| empty / off | `rgba(237,238,239,.06–.10)` |
| live value | `var(--hue)` at .95 |

Cell textures, used sparingly:
- **grid** — 2px cells on an 8px step at 3% — recessed surfaces
- **dither** — 4 rows at 15 / 10 / 6 / 3.5% — the bottom edge of raised surfaces

## 4. Controls

**Toggle** — semi-rounded, `border-radius: 35% of height`. Knob inset 3px, knob radius
35% of knob. Grip = three 2px cells at 30% on the knob centre.

Three sizes only:

| size | track | knob | use |
|---|---|---|---|
| sm | 30×17 | 11 | mod tile |
| md | 40×22 | 16 | list row |
| lg | 46×26 | 20 | properties panel |

ON = track `rgba(237,238,239,.88)`, knob `--bg-shell`.
OFF = track `--bg-base` + 1px `rgba(255,255,255,.16)`, knob `--text-muted`.

**Buttons.** primary = `--text-primary` fill, `--bg-shell` label. secondary = 1px
`rgba(255,255,255,.14)`, no fill. ghost = label only, no box, no underline.

**Meter** (the slider). N discrete cells, 12px on a 17px step. Filled cells
`rgba(237,238,239,.38)`, empty `rgba(237,238,239,.06)`, **the current cell `var(--hue)`
at .95**. The numeric value sits to the right in the hue at .95.

## 5. Reactive / ambient

Monochrome at rest; light and colour appear only where the pointer is.

1. **Hover lifts the surface one step** — `--card-bg` → `--surface-raised`. No glow.
2. **The grid texture behind warms** — the ambient field is the same 3px-cell texture
   that is already there, ramping from 3% to ~22% in the hue, falling off with distance
   from the element. On hover in: 140ms `ease-out`. On leave: 100ms `ease-in`.
3. **Adjusting bleeds** — the cell under the pointer takes the hue at .95; ±1 cell at
   .40; ±2 cells at .16. The bleed travels with the pointer.
4. **Release drains in 200ms**, leaving the hue only on the value that was set.

In-game the field must be a cheap static cell layer whose *opacity* animates — never a
per-cell colour animation, and never a blur.

## 6. Naming — settled, use these words

- **Loadout** — a named bundle of mods + settings (Sword PvP, Bedwars). The code
  already says `stores/loadouts.ts`; that spelling wins everywhere in the UI.
- **Profile** — the player's *account* only (the top-right chip). Never a mod bundle.
- **Preset** — per-mod defaults (Default / Compact / Tournament).

## 7. The two shells

### Desktop launcher — 1600×980, radius 20
Navbar (Play · Mods · Cosmetics · Servers · Friends) + search + profile chip, then one
content panel inset `32,80` sized `1536×768` radius 18, then a dock band below it
holding the loadout selector, version selector, enabled readout and Launch.
Every launcher screen uses that identical shell.

### In-game overlay — 1600×980
Centred nav tabs, VOID mark top-left, close top-right. **Two controls decide the
layout, not three:**

```
layout     grid | list      how items lay out
inspector  open | closed    whether properties show beside
```

Four states, not three modes. Selecting a mod opens the inspector; it never navigates
to another screen — the overlay interrupts a live match, so every step back costs time.

Closing the inspector does **not** reflow the first three columns. They stay exactly
where they are; columns 4–8 wipe in left-to-right on a 40ms stagger, and the panel
slides out right. Opening reverses it. The pointer target never moves.

## 8. Properties — structure follows property count

Spatial properties (position, size, placement) **always** live on the preview as drag
handles or corner slots, never as list rows. Whatever remains gets the simplest
structure that fits:

```
1 property    no list at all — the preview takes the room
2–4           flat list, no section labels
5+            two light groups
tabs          never
```

## 9. Ultralight constraints (in-game only)

`packages/ingame/scripts/check-ultralight.mjs` is the authority and the build fails on
a violation. No backdrop-filter, no mix-blend-mode, no text-shadow, no 3D transforms,
no WebGL/canvas, no media, **no CSS grid**, no sticky, no font-variation-settings, no
network, no rAF loop. Flexbox and absolute positioning only. The flat system is a good
fit for this — it asks for nothing Ultralight cannot draw.
