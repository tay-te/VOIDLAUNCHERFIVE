# Visual QA — in-game overlay vs. Figma frames

A screenshot → diff → fix pass over the six in-game overlay screens and the `@void/ui`
gallery, run against the real Figma exports in `pvp/design/screens/`. Everything below was
measured, not inferred: the previous passes built the geometry from `design/README.md`
without being able to see the result, so this pass compares pixels.

Harness: Chromium (Playwright) at **1300 x 820**, `deviceScaleFactor: 1`, driving
`packages/ingame` under `?debug`, plus the `@void/ui` gallery in both renderer modes.
Diffs are `pixelmatch` at `threshold: 0.1`.

---

## 1. Results

`region` is the mismatch inside the part of the frame this screen actually draws (the
960 x 600 panel, or the 640-wide palette). `full` is the whole 1300 x 820 frame.

| Screen | Frame | before | after | full (after) |
|---|---|---:|---:|---:|
| Mods | `244:538` | 5.74 % | **2.97 %** | 1.63 % |
| Mod settings | `244:834` | 6.58 % | **3.67 %** | 2.00 % |
| Loadouts | `244:1130` | 8.48 % | **3.39 %** | 1.86 % |
| Party | `244:1426` | 5.87 % | **3.55 %** | 1.94 % |
| HUD editor | `244:1722` | 4.81 % | **4.68 %** | 4.68 % |
| Quick palette | `244:1900` | 5.12 % | **4.70 %** | 1.21 % |
| HUD only | — | — | captured | — |

What is left is almost entirely **glyph antialiasing** (Figma's rasteriser vs. Chromium's —
every string shows as a 1px outline in the diff) and **bridge data that differs from the
frame's fixture**: `Keybind None` where the frame says `R-Shift`, `9 mods on` where it says
`24 mods on`, a different set of pressed keys, a different ALSO list in the palette. Those
are listed in section 4; none of them is styling.

Two screens carry a floor the harness cannot remove:

* **HUD editor** — this screen is full-bleed, so the backdrop *is* the frame, complete with
  Figma's own HUD chips and toolbar, and our layer draws over them. Every widget counts
  twice. The geometry table in section 3 is the real evidence for this screen.
* **Quick palette** — the frame lists `Fullscreen` and `Brightness - Gamma 100%` under
  `ALSO`; the bundle's command set produces `Turn on in UHC loadout`. That is
  `src/palette/commands.ts`, not styling.

### Files

* Side-by-side (Figma | ours), one per screen — `visual-qa/final/*.png`
* Working shots, per-region side-by-sides and diff maps — `visual-qa/{shots,side,diff}/{before,after}/`
* Gallery, both renderer modes — `visual-qa/shots/after/gallery-{webview,ultralight}.png`
* Harness — `visual-qa/capture.mjs` (drive + diff), `measure.mjs` (DOM geometry),
  `zoom.mjs` (nearest-neighbour crop compare)

Everything under `visual-qa/` is gitignored except `report.md` and `final/*.png`.

---

## 2. Fixes

### The one that mattered — a reset out-specifying the whole library

`packages/ui/src/styles/01-base.css` reset form controls with

```css
.v-app button, .v-app input { font: inherit; border: 0; background: none; }
```

`.v-app button` is specificity **0,1,1**. Every component class — `.v-btn--accent`,
`.v-toggle--on`, `.v-tab--selected`, `.v-icon-btn`, `.v-keybind` — is **0,1,0**. The reset
won every one of those cascades, on every `<button>`-based component in the package:

* `background: none` erased **every accent fill** — switches rendered as bare knobs,
  `Edit position` / `Launch` / `Queue with party` had no fill, the selected filter tab had
  no tint;
* `border: 0` erased **every rim** — the close button, keybind chips, position chips,
  raised buttons and the selected tab all lost their border;
* the `font` shorthand reset `font-weight` to **400** under every CTA and keycap label the
  design sets at 700.

The reset is now written through `:where()`, which contributes no specificity, so components
win by simply existing. This is the single change behind most of the before/after delta, and
it fixes the gallery in both renderer modes at the same time.

### Design copy: the `·` separators were collapsing

The frames space their separators with runs of real spaces —
`R-Shift closes   ·   drag any tile onto the game to place it   ·   ⌘K search`,
`X 118   Y 64   Z -212   ·   NE`, `HUD   ·   on in 3 loadouts`. HTML collapses those to a
single space, so every one of them read tight. `white-space: pre` (or `pre-wrap`) added to
the twelve classes that carry design copy: panel footer and subtitle, hint and hint bar,
selection readout, palette sub, loadout meta, list row meta, group caption count, coords
chip, HUD chip aside, settings-row sub.

### Switch colours (sampled from the frames, not from prose)

`design/README.md` says the on-state knob is white and the off-state track is a
`--tint-12`-class neutral. Sampling `Overlay-Mods.png` says otherwise:

| | frame | was | now |
|---|---|---|---|
| on knob | `rgb(10,11,12)` | `#ffffff` | `var(--accent-fg)` |
| off track | `rgb(11,12,14)` | `rgb(49,52,55)` | `var(--field-bg)` + `--inset-field` |

`--field-bg` is `rgba(10,11,12,0.92)`, which over `--card-bg` composites to exactly
`rgb(11,12,14)`. The light-grey off-track was the loudest single difference on the mod grid.

### Geometry and layout

| Fix | Was -> is | Where |
|---|---|---|
| Inline buttons 38px tall (`normal` leading on Outfit ~1.4em) | 38 -> **31**, the frames' height | `.v-btn` explicit `line-height: 15px`; accent/raised padding to `8px/7px 12px` |
| `<- Mods` drawn as a full-size raised button | 85 x 38 -> **71 x 28**, r 9 | new `.v-btn--back` |
| Raised block CTA 2px taller than the accent CTA beside it (its 1px rim) | 39 -> **37** | `.v-btn--block.v-btn--raised` padding |
| Mod-settings subtitle in flow pushed the whole body down | preview at y 215 -> **191** (frame: 191) | subtitle out of flow at `top: 50px`, body `padding-top: 26px` |
| Loadouts definition line, same problem | cards at y 211 -> **199** (frame: 199) | subtitle out of flow, body `padding-top: 36px` |
| Mod-settings subtitle in the UI face | -> **DM Mono**, `--text-sm-2`, muted, as the frame draws it | `.mset-panel .v-panel__subtitle` |
| `Edit position` / `Reset` floating mid-panel | y 637 -> **657** (frame: 657) | moved into the footer band, which is what the frame draws: hint left, actions right, bottom-aligned |
| Mods pane stretched past the grid | 278 x 490 -> **278 x 414** | `.mods-body { align-items: flex-start }` + explicit pane height |
| Filter tabs too close to the search field | x 520 -> **528** (frame: 529) | `.mods-panel .v-tabs { margin-left: 8px }` |
| Party tabs too close to the title | x 276 -> **288** (frame: 289) | `.party-panel .v-tabs { margin-left: 12px }` |
| Party columns 4px high | y 181 -> **185** (frame: 185) | `.party-panel .v-panel__body { padding-top: 22px }` |
| Queue rows too tall and too far apart | 51px / 12px gap -> **43px / 6px** | `.queue-row` padding, new `.queue-rows` group |
| Selected queue radio drawn as a ring | 16px with a punched centre -> **12px solid accent** | `.queue-row--selected .queue-row__radio` |
| `Leave party` drawn as a filled bar | -> **bare text button**, as the Button table specifies | `.party__leave` |
| INVITE group 6px low | invite rows y 396 -> **390** (frame: 387) | `.party__invite-cap { margin-top: 3px }`, spacer div removed |
| Party loadout picker well tinted, not filled | `--accent-tint-icon` -> **solid `--accent`** | `IconWell ... solid` |
| LIVE PREVIEW keystrokes at full HUD size, overflowing the 168px strip | 40px keys -> **36px**, mouse keys 56.5 | new `.v-keystrokes--preview` |
| Preview readout uppercased | `BOTTOM LEFT ...` -> **`Bottom left   ·   1.0x   ·   85%`** | caps kept on the caption only |
| Palette caret parked at the far right of the field | x 907 -> **412**, against the last glyph | ghost span sizes the caret; the field is absolutely positioned over the column |
| Palette caption block 5px too tall, pushing rows/captions/footer down | first row y 284 -> **281** (frame: 279) | `.v-palette__caption { line-height: 11px }` |
| Includes chips on a 33px step, drifting 5px over three rows | 27 -> **24px chip**, 30px step | `.v-includes-chip { line-height: 12px }` |
| `ACTIVE` badge pinned to the header's top edge | centre y 227 -> **240**, centred on the 44px icon block | `.v-loadoutcard__header > .v-badge { align-self: center }` |
| Behaviour rows in bridge-key order | -> **mouse buttons, CPS, space bar, sneak**, the frame's order | `BEHAVIOUR_KEYS` beside the existing `APPEARANCE_KEYS` |

### Investigated and deliberately reverted

Moving the panel's 1px rim from `border` to an inset ring (so children would sit at the
README's `25, 21` from the panel's *outer* edge) made all four panel screens **worse**.
Scanning the frames' tile edges shows Figma lays the panel's children out *after* the
stroke — the README's coordinates are inner-relative. Reverted, with the finding recorded in
the CSS so it is not re-attempted.

---

## 3. HUD editor — geometry, since the diff cannot measure it

| Element | `design/README.md` section 10 | measured |
|---|---|---|
| Toolbar | `436, 15`, h 46, r 14, pad `6px 8px`, gap 6 | `431.9, 15`, **436 x 46**, r 14, pad `6px 8px`, gap 6 |
| tool | h 32, pad-x 12, gap 7, r 9 | **32**, 12, 7, 9 |
| divider | 1 x 18 | **1 x 18** |
| Hint chip | `472, 773`, r 8, pad `6px 12px`, 10.5 mono | `473.2, 768`, **353.6 x 27**, r 8, pad `6px 12px`, 10.5 mono |
| Selection box | r 12, 1.5px accent, `--accent-tint-faint` | r 12, accent, tint fill, ok |
| Handles | 8 x 8, r 2, `--text-primary` fill, 1.5px accent rim | **8 x 8**, r 2, ok |
| Selection label | r 6, pad `4px 8px`, gap 8, 30px above the box | r 6, `4px 8px`, gap 8, **29px above** |

The toolbar is 12px wider than the frame's 424 (about 3 %, spread across five tool labels —
text metrics, not spacing) and therefore starts 4px left of `436` because it is centred.
Nothing else is more than 2px out.

---

## 4. Not matched, and why

**Assets that do not exist in the bundle**

1. **The LIVE PREVIEW strip is empty.** The frame puts a game still behind the keystrokes
   widget; there is no game capture available to an HTML overlay, and none ships in the JAR.
   908 x 168 of guaranteed mismatch, so the diff masks it — otherwise it alone would read as
   26 % of the Mod settings panel.
2. **Party avatars are initials, not skins.** The frame shows Minecraft head renders; the
   bundle has no avatar source and no network (`ultralight-notes.md`), so `Avatar` falls
   back to its initials treatment.

**Bridge / registry data, owned elsewhere and deliberately untouched**

3. `Keybind` reads `None`; the frame reads `R-Shift`. The value comes from `mods.json`.
4. `Show sneak key` and, on some mods, `Corner radius` are absent — README "known gap 2":
   those keys are not in `mods.json`. The row *order* is now the frame's; the missing rows
   are a schema question.
5. Loadout counts (`9 mods on` vs `24 mods on`), includes-chip contents and `+ N more` come
   from the fake bridge's library, not from the frame's fixture.
6. HUD widget placement in the editor (`FPS` at 20,20 where the frame draws 23,23; armor at
   y 345 vs 299; an extra `Held` row) comes from `hud[]` and `hud-geometry.ts`.
7. The palette's `ALSO` list is built by `src/palette/commands.ts`; the frame's `Fullscreen`
   / `Brightness - Gamma 100%` are not in the command set.
8. Pressed keys in the previews follow live `keys` events, so `D`/`LMB` rarely match the
   frame's frozen state.
9. A few mod icons are different lucide glyphs from the frame's (`Potion effects`,
   `Armor status`, `New loadout`). `MOD_ICONS` lives in `registry.ts`.

**Renderer constraints, correct as-is**

10. **The selection frame is solid, not dashed.** `ultralight-notes.md` calls for this —
    dash phase on rounded corners is inconsistent in Ultralight. The frame draws it dashed.
11. **No grain.** The frames carry a 32.64px `mix-blend-mode: overlay` noise tile over every
    surface; section 2 forbids it and the ultralight token layer lifts the base hexes about
    1 % instead.
12. **Outfit 700** is now shipped (the gallery reports `Outfit 700 loaded`), so CTA and
    keycap labels are a real weight, not synthesised — README "known gap 6" can be closed.

---

## 5. Harness notes for whoever runs this next

Two things silently corrupted the numbers before they were found, both worth keeping:

* **Headless Chromium hints glyph advances to whole pixels at small sizes.** DM Mono at
  10.5px measures 7.0px/char instead of 6.3 — every mono string reads about 11 % wide against
  the Figma export, which is enough to make you "fix" CSS that is already right.
  `chromium.launch({ args: ['--font-render-hinting=none'] })` restores true metrics.
* **The Figma export already contains the dim scrim and that frame's HUD.** Painting our own
  dim and HUD layer on top measures the harness double-exposing the backdrop, not our UI.
  `capture.mjs` suppresses `.menu-layer__dim`, `.editor__dim`, `.palette-layer__dim` and (on
  panel screens) `.hud-layer`, and injects that CSS via `addInitScript` so a Vite HMR reload
  mid-run cannot drop it.

The `before` column was produced by running the identical harness against an untouched copy
of both packages, served from a second Vite instance, so nothing in the shared working tree
had to be reverted while a sibling agent was editing it.

---

## 6. Verification

| Check | Result |
|---|---|
| `packages/ui` — `pnpm test` | **258 passed** |
| `packages/ui` — `pnpm typecheck` | clean |
| `packages/ui` — `pnpm build` | clean |
| `packages/ingame` — `pnpm test` | **93 passed** |
| `packages/ingame` — `pnpm typecheck` | clean |
| `packages/ingame` — `node scripts/check-ultralight.mjs` | **passed — 30 files, 22 rules** (src *and* emitted bundle) |
| `packages/ingame` — `pnpm size` | **195.8 KB of 400 KB gzipped (49.0 %)** |

No `backdrop-filter`, `mix-blend-mode`, `text-shadow`, 3D transform, CSS grid or
`position: sticky` was introduced. The only new selector feature is `:where()`, used for the
zero-specificity form reset; Ultralight 1.4's JavaScriptCore/WebKit is well past the Safari
14 that shipped it, and the same rule is what makes every accent fill in the bundle paint.
