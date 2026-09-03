# `@void/ui`

The shared React components and design tokens for VOID PVP.

**One React codebase, two bundles** (§9). `apps/desktop` bundles this for the launcher's
system webview; `packages/ingame` bundles it for **Ultralight**, which runs inside the
JVM and is WebKit-derived and older. The in-game renderer is the constraint, not the
launcher — nothing here depends on `backdrop-filter`, `mix-blend-mode`, `text-shadow`, a
3D transform, WebGL or video in a way that breaks when they are unavailable.

Everything is ported from `pvp/design/`: `tokens.css` is copied verbatim, the component
sizes come from the region tables in `design/README.md`, and the fallbacks come from
`design/ultralight-notes.md`. `design/` is read-only reference material and nothing
imports from it at build time.

---

## Consuming it

```ts
// entry.tsx — once, at the top of your app
import '@void/ui/tokens.css'; // design tokens + the two renderer layers
import '@void/ui/fonts.css';  // the three bundled OFL families
import '@void/ui/styles.css'; // the component styles

import { setRenderer, TopNav, NavItem, LaunchButton } from '@void/ui';

setRenderer('webview'); // or 'ultralight' — see below
```

Then put `v-app` on the element that wraps your tree. That class carries the reset, the
type ramp and the base colours; without it the components inherit the host page's font.

```tsx
<div className="v-app">
  <TopNav right={<SearchBar />}>
    <NavItem active icon="play">Play</NavItem>
    <NavItem icon="layers">Mods</NavItem>
  </TopNav>
</div>
```

### Entry points

| Import | What it is |
|---|---|
| `@void/ui` | The components, the icon set, `setRenderer`, `cx`, `TOKEN_NAMES` |
| `@void/ui/tokens.css` | `:root` tokens + `[data-renderer]` layers. **Import first.** |
| `@void/ui/fonts.css` | The six `@font-face` rules |
| `@void/ui/styles.css` | Every component style, one file |
| `@void/ui/tailwind-preset.css` | Tailwind 4 `@theme` mapping (optional) |
| `@void/ui/tokens.json` | The Figma token export, for tooling |
| `@void/ui/fonts/<file>.woff2` | A single face, if you want to preload one |

No runtime CSS-in-JS, `sideEffects` limited to the stylesheets, ESM only, ES2022 output
(Ultralight's JavaScriptCore is ES2022). Importing one component does not pull in the
rest of the JavaScript.

---

## Renderer modes

The difference between the two surfaces is **one attribute on `<html>`**:

```html
<html data-renderer="webview">    <!-- the launcher: real glass, real grain -->
<html data-renderer="ultralight"> <!-- the overlay:  baked fallbacks       -->
```

`setRenderer('ultralight')` stamps it for you. Unset behaves as `webview`, because the
authored token values *are* the launcher's.

**Nothing in this package branches on the renderer in JavaScript.** The attribute selects
a token layer, and every component reads the same token names either way — which is what
lets one stylesheet be correct in both places. If you find yourself writing
`if (getRenderer() === …)` in a component, the fix is almost always a token.

### What the ultralight layer changes, and why

Each row is prescribed by a section of `design/ultralight-notes.md`. `test/renderer.test.ts`
asserts every one of them against the shipped CSS, so they cannot quietly regress.

| Note | Authored | Ultralight |
|---|---|---|
| §1 `backdrop-filter` **[hard]** | `--panel-bg: rgba(10,11,12,0.84)` + `blur(15px)` | `rgba(10,11,12,0.97)`, no blur |
| §1 | `--palette-bg: …0.90` + blur | `rgba(10,11,12,0.96)` |
| §1 | `--dim-palette: …0.55` + `blur(3px)` | one flat `rgba(10,11,12,0.62)` |
| §1 | `--blur-panel/dock/dim` | all `0px` |
| §2 `mix-blend-mode` **[hard]** | 32.64px noise tile at `overlay` | grain off; `--surface-1` `#191c20`→`#1a1d21`, `--surface-2` `#22262b`→`#23272c` (fallback 1, "bake it") |
| §7 dashed rounded border **[risky]** | `--selection-border-style: dashed` | `solid` — the `--accent-tint-faint` fill already distinguishes the selection |

**The GL blur host contract** (§1). If the host's GL blur pass is running, the panel may
sit at the lighter alpha. The host tells the page by setting `data-glblur="on"`, or by
calling `setGlBlur(true)`:

```ts
setRenderer('ultralight');
setGlBlur(true); // --panel-bg becomes rgba(10,11,12,0.94)
```

Never branch on `@supports (backdrop-filter: …)`. Ultralight may claim support and still
no-op. Read the radius through `var(--blur-panel)` instead, which the ultralight layer
sets to `0px` — the property then resolves to `blur(0px)` and is inert either way.

### Fallback rules for new work

If you add a rule to this package, it has to hold in the overlay too:

- **No `text-shadow`, no `-webkit-text-stroke`.** Ultralight's text rasteriser drops
  them and on some builds smears the glyph atlas. HUD legibility is solved
  *structurally*: every readout over live game sits on its own chip with a solid-ish
  background, a `--border-dock` edge and the `--inset-hud` bevel. If text must go over
  the game, give it a chip.
- **No `mix-blend-mode` / `background-blend-mode`.** The grain layer (`.v-noise`) is
  scoped to `[data-renderer="webview"]` and is the only place either appears.
- **2D transforms only.** No `rotateX/Y`, `translateZ`, `perspective`, `preserve-3d`,
  `backface-visibility`. Panel motion is `opacity` + `translateY(8px)` + `scale(0.98)`
  over `--duration-slow` with `--ease-out`.
- **No WebGL, no `<canvas>`, no `<video>`.** Charts are built from positioned divs —
  see `Sparkline` and `ArmorList`.
- **No CSS Grid, no `position: sticky`, no `filter: drop-shadow`.** The design is flex
  plus absolute positioning throughout; the 3 × 4 ModTile grid is nested flex rows with
  `gap: 10px`, deliberately.
- **No `font-variation-settings`.** The bundled faces are static instances already
  pinned to the axes the design specifies.
- `box-shadow` **including `inset`** is fine and is what carries the whole bevelled
  look. Keep the shadow lists short — Ultralight rasterises shadows on the CPU on some
  configurations.

---

## Fonts

Three OFL families, bundled as **static-instance** woff2 (~104 KB total):

| Family | Weights | Instance |
|---|---|---|
| Bricolage Grotesque | 800 | `opsz 14, wdth 100, wght 800` — the axes every display title pins |
| Outfit | 400 / 500 / 600 | (700 aliases 600; no fake bolding) |
| DM Mono | 400 / 500 | static upstream |

`font-display: block`: the in-game bundle has no network and the faces are local, so
there is no FOUT to trade against — a swap would just flash the fallback for a frame
over a live game.

The `url()`s in `fonts.css` are **relative**, so the consumer's bundler resolves,
fingerprints and emits them. With Vite that is automatic; the gallery build confirms it
(`assets/outfit-400-<hash>.woff2`). Licences ship alongside as `OFL-<family>.txt`.

To rebuild the faces (only needed when a family changes):

```sh
pip install fonttools brotli
node scripts/fetch-fonts.mjs
```

---

## Tailwind 4

Optional. The preset maps the tokens onto Tailwind's theme namespaces so utilities read
in the design's vocabulary — `bg-shell`, `text-ink-2`, `rounded-panel`, `shadow-cta`,
`font-display`, `text-hero`, `tracking-caps`:

```css
@import 'tailwindcss';
@import '@void/ui/tokens.css';
@import '@void/ui/fonts.css';
@import '@void/ui/tailwind-preset.css';
@import '@void/ui/styles.css';
```

Every theme value is `var(--token)`, never a copied literal — so the
`[data-renderer="ultralight"]` layer still reaches anything built out of these
utilities. A theme value that baked in a hex would freeze the launcher's colours into
the in-game bundle.

The design's spacing ramp has odd steps (5, 7, 9, 11, 13, 21, 25) that Tailwind's 4px
scale cannot express. Keep the default `--spacing` and reach for the token directly —
`p-[var(--space-25)]` — where the design is off-grid.

---

## Icons

`Icon` draws from a bundled set of conservative SVG paths. Inline SVG is **[risky]** in
Ultralight (§7): strokes and `stroke-linejoin` are the usual casualties, and the note
recommends a PNG sprite sheet for the overlay.

So the icon renderer is swappable, process-wide, without touching a call site:

```ts
import { setIconRenderer } from '@void/ui';

setIconRenderer(({ name, size = 16 }) => (
  <span className="sprite" style={{ width: size, height: size, backgroundPosition: offsetFor(name) }} />
));
```

`MOD_ICONS` maps each of the 12 mod ids to its glyph. `resolveLoadoutIcon(name)` resolves
a loadout's `icon` field — `loadout.json` says that value names an icon in this package,
not a file path — and falls back to `box`.

---

## The gallery

```sh
pnpm --filter @void/ui gallery
```

Every component in the design README's inventory, in every state that README lists,
rendered on the `ground` background, with a **renderer toggle** in the header. It is the
visual acceptance surface: flip between `webview` and `ultralight` and both must look
right. HUD widgets and the editor render over a stand-in for the game.

The gallery drives the live specimens from `createFakeVoid({ seed: 42 })` in
`@void/protocol`, so the keystrokes widget, the FPS chip and the ping chip animate off
the same fake bridge the in-game `?debug` harness uses.

---

## What is in here

Every entry in the design README's component inventory.

| Group | Components |
|---|---|
| **Shell chrome** | `TopNav`, `NavItem`, `SearchBar`, `StatusPill` |
| **Launcher dock** | `Dock`, `PlayerChip`, `LoadoutPicker`, `VersionPicker`, `LaunchButton`, `FriendsOnline` |
| **Panels** | `Panel`, `FilterTabs` |
| **Primitives** | `Button`, `IconButton`, `Card`, `Kbd`, `Tag`, `Badge`, `Avatar`, `IconWell`, `Divider`, `StatusDot`, `Icon` |
| **Controls** | `Toggle`, `Slider`, `KeybindChip` |
| **Mods** | `ModGrid`, `ModTile`, `ModSettingsPanel`, `ModSettingsRow`, `KeystrokesPreview`, `EditPositionButton`, `SettingsGroup`, `SettingsRow`, `Swatches`, `PositionChips` |
| **Cards & panes** | `LoadoutCard`, `Pane`, `StatTile`, `Sparkline`, `GroupCaption`, `BackButton` |
| **List rows** | `ServerRow`, `FriendRow`, `PartyMemberRow`, `InviteRow`, `CosmeticCard` |
| **HUD widgets** | `FpsChip`, `PingChip`, `CoordsChip`, `CpsChip`, `PotionList`, `ArmorList`, `KeystrokesWidget`, `Crosshair`, `Hotbar` |
| **HUD editor** | `EditorToolbar`, `Tool`, `SelectionFrame`, `HintBar` |
| **Quick palette** | `Palette`, `PaletteInput`, `PaletteSeam`, `PaletteSection`, `PaletteResult`, `PaletteFooter` |

Plus the formatters the frames imply: `formatPotionTime`, `formatAmplifier`,
`formatSelectionReadout`.

---

## Scripts

| Script | What it does |
|---|---|
| `pnpm build` | Regenerate tokens → `tsc` → assemble `dist/` CSS and fonts |
| `pnpm typecheck` | `tsc --noEmit` across `src`, `test`, `gallery`, `scripts` |
| `pnpm test` | Vitest + React Testing Library |
| `pnpm gallery` | The gallery dev server |
| `pnpm gen:tokens` | Rebuild `tokens.css` / `tokens.ts` / `noise.css` from `design/` |

`scripts/build-tokens.mjs` runs as part of both `build` and `typecheck`, so a change in
`design/tokens.css` is picked up without a separate step.

---

## Conventions

- Class names are `v-<component>__<part>` with `v-<component>--<modifier>`. Nothing is
  hashed, so a consumer can target a part when it must.
- Components are **controlled**. `Toggle`, `Slider`, `FilterTabs` and `ModTile` never
  hold their own value — the bridge is authoritative and returns the state actually
  applied, so an optimistic local state would be a bug waiting to happen.
- `KeybindChip` is the exception, and only for the one asynchronous call on the bridge:
  it owns `idle → capturing → idle` and takes the applied key from
  `void.openKeybindCapture`'s promise.
- Interactive components carry their ARIA roles: `switch`, `slider`, `tablist`/`tab`,
  `radiogroup`/`radio`. The in-game overlay is navigable without a mouse.
- A component that holds two actions keeps them as **siblings**, never nested.
  `ModTile` is the case in point: it is a `<div>` with a stretched select `<button>`
  behind its contents and the switch layered above, because a button inside a button is
  invalid HTML and would leave the switch unreachable by keyboard.
- `src/index.ts` exports are stable. If something is renamed, the old name stays as an
  alias — `packages/ingame` and `apps/desktop` import these names directly.
