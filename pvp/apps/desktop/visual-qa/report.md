# Visual QA — the launcher against `design/screens/Launcher-*.png`

Real Chromium (Playwright, viewport **1300 × 820**, `deviceScaleFactor: 1`, dark, reduced
motion), driving `pnpm dev:web`; diffs by `pixelmatch` at the default `threshold: 0.1`
with antialiasing detection on. Every frame in `design/` is one 1300 × 820 artboard, so
a viewport shot is the comparable unit and no scaling is involved anywhere.

Rerun it with:

```sh
pnpm dev:web                                   # terminal 1
cd visual-qa
node capture.mjs after                         # terminal 2 — eight shots
node capture.mjs after-plain --no-backdrop     #             …the same eight, no art
node compare.mjs after && node compare.mjs after-plain
```

`playwright`, `pixelmatch` and `pngjs` are resolved at run time from a global install or
`NODE_PATH` — see `../README.md` § *Visual QA* and the note at the top of `lib.mjs`.
They are deliberately not dependencies of `@void/desktop`.

---

## What the two numbers mean

**The launcher has no hero raster.** Figma `244:58` is a rendered Minecraft still behind
the recessed canvas; there is no licenced render in this repository and `design/` is
read-only reference material, so the shipped launcher draws a gradient placeholder
(`.canvas__art`, with a `TODO(art)`). On a straight full-frame diff that placeholder is
40–60 % of every screen and swamps everything a component can affect.

So each pass is scored twice:

| Column | What it measures |
|---|---|
| **frame** | the whole 1300 × 820 shot |
| **ui only** | just the rectangles the frames draw UI in — the 1300 × 62 chrome band, the dock, and either the 960 × 596 panel or (on Play) the eyebrow pill. `visual-qa/lib.mjs` `UI_REGIONS` holds the boxes. |

…and the pass itself runs twice:

- **plain** — the launcher exactly as it ships, gradient placeholder and all. This is the
  column to compare against the *before* baseline: same placeholder on both sides, so the
  number moves only when the components move.
- **with the design crop** — `?no-backdrop` off, i.e. the preview borrows the frame's own
  canvas rectangle as the backdrop (`src/dev/backdrops.ts`; dev only, and a Tauri build
  contains none of it — verified: `TAURI_ENV_PLATFORM=linux vite build` emits zero
  `Launcher-*.png` assets). With the art supplied, what is left is the components.

---

## Per screen

`before` = the launcher before `@void/ui` was adopted (local component copies in
`src/components/`). `after` = the same shots today.

| Screen | before · frame | after · frame | **before · ui only** | **after · ui only** | after · ui only, art supplied |
|---|---:|---:|---:|---:|---:|
| Play | 55.68 % | 54.79 % | **14.04 %** | **10.36 %** | 8.73 % |
| Mods | 27.57 % | 24.03 % | **12.98 %** | **7.41 %** | 5.12 % |
| Cosmetics | 22.58 % | 20.75 % | **16.01 %** | **13.22 %** | 11.65 % |
| Servers | 20.05 % | 17.45 % | **11.89 %** | **8.10 %** | 6.82 % |
| Friends | 20.19 % | 18.07 % | **12.65 %** | **9.45 %** | 7.93 % |

The mock's `server_ping` returns the fixture latency with jitter, so the Play and Servers
figures move by a hundredth between runs; nothing else does.

Full-frame with the design crop supplied — the number that answers "how far off is the
launcher once the art exists": Play **2.30 %**, Mods **3.98 %**, Cosmetics **8.46 %**,
Servers **5.01 %**, Friends **5.81 %**.

Three shots are captured for review but never scored, because no frame draws them:
**Settings** (a launcher screen the frames stop short of — the gear), the **⌘K palette**
(its frame, `244:1900`, is the in-game one over a game render), and the **dock mid-launch**
(a state, not a screen; `prepare` streaming 3,000 asset objects has no frame at all).

## Side-by-side sheets

Three panels, half scale, one sheet per scored screen:

1. **the frame**, straight from `design/screens/`;
2. **the launcher**, from the pass where the preview borrows the frame's own canvas
   rectangle for the backdrop. Because the frames are composites, anything the frame
   itself drew on that rectangle is still in it — so wherever the launcher does not land
   exactly on top of the frame's own type, the frame's shows through as a ghost. On the
   four panel screens the panel covers it and there is nothing to see; on **Play** the
   hero sits directly on the art, and the doubled `Sword PvP` is not a rendering bug, it
   *is* the 12 % title-width difference the last section measures. `out/after-plain/`
   holds the same shots on the gradient placeholder the app ships, with no ghost at all.
3. **the pixelmatch diff** — flagged pixels in pink over a washed-out plate.

The full-resolution captures and per-pixel diffs live in `out/<pass>/` and are gitignored.

- [`play.png`](play.png)
- [`mods.png`](mods.png)
- [`cosmetics.png`](cosmetics.png)
- [`servers.png`](servers.png)
- [`friends.png`](friends.png)

---

## What the remaining difference is

Measured, not guessed. Ink bounds below are from the *plain* pass, so nothing of the
design's own frame is showing through.

### Fixed in this pass

- **Every button in `@void/ui` painted transparent.** `01-base.css` resets
  `.v-app button { background: none }` at specificity (0,1,1); every component background
  in the package — `.v-btn--accent`, `.v-toggle--on`, `.v-launch`, `.v-pill`,
  `.v-tab--selected` — is a single class at (0,1,0), so the reset won. Measured:
  `getComputedStyle('.v-launch').backgroundColor === 'rgba(0, 0, 0, 0)'`. The launcher
  now carries the same reset itself at zero specificity and leaves `v-app` off the root;
  the fix belongs upstream (`.v-app :where(button, input)`), and there is a `TODO(ui)` at
  the top of `local/app.css` that says so. **This is a `packages/ui` bug and affects its
  gallery too.**
- The dock was capped at 635 px wide — an absolutely-positioned `left: 50%` shrink-to-fit
  box can only be as wide as the containing block minus its offset, so the loadout pill
  wrapped. Now `left: 0; right: 0; justify-content: center`.
- Panel header: the frames leave ~20 px between a panel's search field and its tabs where
  the header's own gap is 12. Tabs now land on the frame's x to the pixel (Mods: design
  530, render 530).
- Settings groups collapsed to their captions: `.v-group` carries `overflow: hidden`,
  which zeroes a flex item's automatic minimum size, so the modal's column squeezed every
  group. `.settings__body > * { flex: 0 0 auto }`.
- The keystrokes preview was being flex-shrunk from 128 px to 113. Only the settings block
  gives now.
- The mock's `prepare` resolved before its own progress events had fired, so the CTA never
  entered its progress state — the one launch state a reviewer most wants to see. It now
  resolves after them, which is what `commands::launch::prepare` does.
- Servers' detail pane: the two housekeeping actions moved above the spacer so the pane
  still ends on the frame's `Join with …` / `Favourited` pair, and a caption inside a Pane
  no longer carries the list's 12 px indent.

### Left, and why

- **The hero title is 12 % wider than the frame.** Design ink `x 47..507` (461 × 73),
  render `x 48..564` (517 × 75) — left edge and baseline land within a pixel, the glyphs
  are simply wider. `@void/ui` bundles Bricolage Grotesque as a static instance pinned at
  `opsz 14`; the frame rendered its 104 px title at a higher optical size, which is
  narrower. At 26 px the two agree exactly (panel title `Mods`: design 66 px of ink,
  render 67), so this is only visible on the hero. **A `packages/ui` font-instancing
  question, not a launcher one** — the launcher renders `--text-hero` and
  `--tracking-hero` as authored.
- **DM Mono runs ~8.5 % wide.** `ACTIVE LOADOUT`, same 14 characters both sides: design
  106 px of ink from x 50, render 115 px from x 50. The bundled face measures 0.667 em per
  advance where the frame's measures 0.6. Same package, same answer.
- **Avatars are initials, not skin heads.** The frames show rendered Minecraft heads;
  `MOCK_ACCOUNT.skin_url` is `null` because an offline account has no skin and the
  launcher must render with no network. Affects the dock identity, the three
  `FriendsOnline` heads and the nav avatar.
- **The cosmetics stage is a blocky stand-in.** `246:16` is a 160 × 376 player render.
  It is the single largest remaining region (300 × 452 = 13 % of the frame) and the reason
  Cosmetics scores worst. `TODO(art)` in `screens/Cosmetics.tsx` and `local/app.css`.
- **Disabled CTAs the frames draw enabled.** `Add friend`, `Queue with party` and
  `Preview in lobby` have no backend (§16.1, §16.2) and `Auto-switch loadout` has no
  schema (§16.3), so they render disabled with a `title`. The panel footers say why. This
  is a product state, not a fidelity gap — but it is why three accent buttons read grey.
- **Live data instead of the frame's copy.** `8 mods on` (the fixture loadout really has
  eight on) not `24`; `39 ms` not `12 ms`; `200,000 slots` not `37 wins here`; `1.8.9` not
  `1.8 – 1.21`; `PING · THIS SESSION` with as many bars as there have been pings, not a
  twelve-hour history. Nothing here fakes a number it does not have.
- **Friends' `Online` tab filters.** The frame shows `ONLINE · 3` *and* `OFFLINE · 5` with
  the `Online` tab selected — internally inconsistent with its own tab counts
  (`Online 3 / All 8`). The launcher filters, which is what the tabs mean; the two offline
  rows are the largest single block of the Friends difference.
- **One row of the Mods settings pane is below the fold.** `252:189` sizes the pane 278 ×
  410 around *its* three Keystrokes rows; the registry gives that mod six. The pane keeps
  the frame's height and the rows scroll, with a 16 px fade at the foot as the affordance,
  rather than pushing the pane's bottom past the grid's.
