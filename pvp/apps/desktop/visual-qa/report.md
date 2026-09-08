# Visual QA — the launcher against `design/screens/launcher/`

Real Chromium (Playwright, viewport **1600 x 980**, `deviceScaleFactor: 1`, dark, reduced
motion), driving `pnpm dev:web`; diffs by `pixelmatch` at the default `threshold: 0.1`
with antialiasing detection on.

**1600 x 980 is the shipped window** (`src-tauri/tauri.conf.json`) and the size the three
canonical frames are drawn at. The previous pass captured at 1300 x 820 — the size of the
superseded boards in `design/screens/*.png` — so every number it produced was measured
against a window the launcher does not have, and the whole layout had been tuned to it.
That is half of why the port read as cramped next to the frames; the other half was the
type ramp, which capped the Play hero at 40 where the frame draws it at 88.

Rerun it with:

```sh
pnpm dev:web                     # terminal 1
cd visual-qa
node capture.mjs after           # terminal 2 — nine shots
node compare.mjs after
```

`playwright`, `pixelmatch` and `pngjs` are resolved at run time from a global install or
`NODE_PATH` — see `../README.md` § *Visual QA* and the note at the top of `lib.mjs`. They
are deliberately not dependencies of `@void/desktop`. Run `npx playwright install chromium`
once if the browser is not already in `~/Library/Caches/ms-playwright`.

---

## What is scored, and what is not

The canonical frames are flat — §0 of `design/quiet-cell-system.md` removed the hero
still — so the launcher and the frame draw the same empty stage and a straight full-frame
diff is meaningful. There is no "art supplied" variant any more, and `--no-backdrop` is
gone with it.

| Column | What it measures |
|---|---|
| **frame** | the whole 1600 x 980 shot |
| **ui only** | the three regions the shell is made of — the 1600 x 80 chrome band, the 1536 x 768 content panel at 32,80, and the 1600 x 132 dock band. `lib.mjs` `UI_REGIONS`. |

Together those regions are the whole window, so the two columns differ only by the 20px
window radius. They are kept apart because the split says *where* a difference is.

Only three frames were re-cut at 1600 x 980, and they are the three that are scored.
Cosmetics, Servers and Friends have only the superseded 1300 x 820 boards, which cannot be
diffed against a 1600 x 980 capture at all; they are captured for review and left
unscored rather than compared against a frame that is both the wrong size and the wrong
system. Settings, the Cmd-K palette and the mid-launch dock have no frame of their own.

## Per screen

`before` = the launcher as it stood after the quiet-cell port, captured at the shipped
window size for the first time. `after` = the same shots today.

| Screen | before · frame | after · frame | before · ui only | after · ui only |
|---|---:|---:|---:|---:|
| Play | 2.69 % | **0.60 %** | 2.78 % | **0.62 %** |
| Mods | 1.94 % | **1.38 %** | 2.00 % | **1.42 %** |
| Mod setup | 2.14 % | **1.88 %** | 2.21 % | **1.94 %** |

The mock's `server_ping` returns the fixture latency with jitter, so Play moves by a
hundredth between runs; nothing else does.

The absolute numbers are small on all three because both sides are near-black: pixelmatch
at `threshold: 0.1` does not flag a difference of a few luminance steps, which is most of
a dark flat UI. Read them as a ratio, not as a share of the screen — Play is now at 22 %
of the difference it started with.

## Side-by-side sheets

Three panels, half scale, one sheet per scored screen: the frame, the launcher, and the
pixelmatch diff (flagged pixels in pink over a washed-out plate).

- [`play.png`](play.png)
- [`mods.png`](mods.png)
- [`setup.png`](setup.png)

The full-resolution captures and per-pixel diffs live in `out/<pass>/` and are gitignored.

---

## What the remaining difference is

Measured, not guessed. Geometry is matched to the pixel on all three screens — the numbers
below come from the Figma file itself (`RShlfbx2TfxrdleKPIry8w`, page *Source of truth —
Desktop Client*, nodes `289:1141` Mods and `289:1411` Setup), read with the Figma MCP
server rather than transcribed off the PNGs.

### Verified against the file

| | file | render |
|---|---|---|
| content panel | 32,80 1536 x 768 | same |
| panel content line | x 64 / y 112 | same |
| Mods rail rows | 180 x 34 on a 40 step, fill from x 56, label at 72 | same |
| Mods grid | five 236-wide cards on a 20 gutter, 322 tall on a 20 gutter | same |
| card preview | 216 square at +10,+10 | same |
| card numeral | 36px in a 45px box at y 240, unit at y 290 | ink 251-275 / 292-298 both sides |
| card name / category | boxes at y 424 h 17 and y 446 h 11 | ink within 1px |
| mod-tile switch | 34 x 19, knob 13 | same |
| nav tabs | text boxes 34 apart, selected pill 240,18 60 x 30 | within 1px on four of five |
| search | 1112,15 300 x 34, placeholder at 1134 | same |
| dock | pills at 64 and 248 (46 tall), readout at 400, 24 cells on an 11 step, Launch 1346,888 190 x 52 | same |
| Setup columns | 780 preview at x 64, 644 properties at x 892 | same |
| Setup title | box y 138 h 38 (30px) | ink 146-173 both sides |
| Setup rows | 48 apart in a group, 76 across one | same |
| Setup meters | ten 12px cells on a 17 step ending at 1435, value ending at 1536 | same |
| Setup toggles | 46 x 26, knob 20 | same |
| Play hero | 88px, ink 65-454 / 591-654 | 65-463 / 591-654 |

### Left, and why

- **The frames are drawn on a 24-mod library; the fixtures have 12.** Every count differs
  (`12 MODS · 8 ENABLED` against `24 MODS · 11 ENABLED`, `HUD 7` against `HUD 9`), the dock
  meter has twelve cells rather than twenty-four, and the grid's third row does not exist.
  This is the single largest remaining block on Mods, and it is data, not layout.
- **`Launcher-Play.png` is a flattened frame.** `289:8` has no sublayers, so Play's numbers
  are read back off the PNG rather than out of the file. The watermark matrix in
  `src/local/watermark.ts` is that read-back, cell by cell — 536 of 1458 slots.
- **The hero is 1.5 % wider than the frame.** Ink `65..463` against `65..454`: the left
  edge and both vertical bounds land exactly (the -3px optical margin on `.hero` is what
  puts the glyph on the content line), and the glyphs are simply a little wide. Outfit at
  88px in Chromium against Figma's own shaping; nothing the launcher authors.
- **Label tracking runs ~5 % wide.** `CATEGORIES` is 66px of ink in the file and 71 in the
  render at the contract's `.12em`. The contract wins over the frame here.
- **Mod setup has two property groups where the frame has three.** §8 says *5+ -> two light
  groups*, and `propertiesFor('keystrokes')` returns five; the frame draws DISPLAY / INPUT
  / BEHAVIOUR over seven rows, two of which (`Drop shadow`, `Show on scoreboard`) are not
  in the mod's schema. Everything below the last row therefore sits ~90px higher than the
  frame's, Save included.
- **Avatars are initials, not skin heads.** `MOCK_ACCOUNT.skin_url` is `null` because an
  offline account has no skin and the launcher must render with no network.
- **The profile chip carries the account kind where the frame carries a level.** There is no
  level in the account model, and inventing one would be the only fake number on screen.
- **`PROFILES` in the rail is `LOADOUTS` in the code.** The frame predates §6, which settles
  the word: a bundle of mods is a **Loadout**, and *Profile* is the account chip only. The
  code is right and the frame is stale. The same goes for the footer hints.
