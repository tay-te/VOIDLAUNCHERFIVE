# `@void/web` — the marketing site

The public site for VOID. One page so far: the landing page.

**Next.js 16 (App Router) · React 19 · Tailwind 4 · deployed on Vercel.** The page is
fully static — `next build` prerenders it, there is no data fetching and no runtime
server work. A handful of client components carry all the interactivity, and every one of
them server-renders a complete first frame.

```
pnpm --filter @void/web dev      # http://localhost:5184
pnpm --filter @void/web build    # prerender
pnpm --filter @void/web typecheck
```

### Deploying

Set the Vercel project's **Root Directory** to `pvp/apps/web`. Vercel walks up to
`pvp/pnpm-workspace.yaml` for the install; the framework preset is detected. If it picks
up the repo root's `package-lock.json` (that belongs to the *other*, Electron launcher)
instead of `pvp/pnpm-lock.yaml`, override the install command to
`pnpm install --filter @void/web...`.

`metadataBase` resolves `NEXT_PUBLIC_SITE_URL`, then `VERCEL_PROJECT_PRODUCTION_URL`, then
`VERCEL_URL`, so Open Graph image URLs resolve on every Vercel deploy without hard-coding a
domain. Off Vercel with none of them set it falls back to localhost, which is only right for a
local build: **a self-hosted production deploy must set `NEXT_PUBLIC_SITE_URL`.**

`app/icon.svg`, `favicon.ico`, `apple-icon.tsx`, `opengraph-image.tsx` and
`twitter-image.tsx` are drawn from the 16-cell mark and prerendered at build; the OG card
uses WOFF copies of Outfit in `app/fonts/og` because `next/og` cannot read WOFF2.

---

## The page

Product chapters: one hero, four chapters that each show one thing the client does, then
how to start, what people ask, and the close. About 5.8 screens at 1440 × 900.

| # | Section | File | Anchor |
|---|---|---|---|
| — | Hero | `sections/Hero.tsx` | `#top` |
| 01 | Overlay — *It opens over the game.* | `sections/Chapters.tsx` | `#overlay` |
| 02 | HUD — *Put it anywhere.* | `sections/Chapters.tsx` | `#hud` |
| 03 | Loadouts — *One per game mode.* | `sections/Chapters.tsx` | `#loadouts` |
| 04 | Performance — *Out of the way of your frames.* | `sections/Chapters.tsx` | `#performance` |
| — | Get started (steps 1–3) | `sections/GetStarted.tsx` | `#get-started` |
| — | Questions + requirements | `sections/Questions.tsx` | `#questions`, `#requirements` |
| — | Download close | `sections/Download.tsx` | `#download` |
| — | Footer | `sections/Footer.tsx` | — |

The only numbering on the page is the chapters (01–04) and the steps (1–3). The first
build carried a film slate on every section (`04 · CATEGORY HUES`); with a hero "01" before
any section and steps "01/02/03" under slate "07", the numbers collided, so the slates are
gone.

**Layout rules.** The hero is a split from 1100 px — text left, the illustration taking
60 : 40 of the content width — and stacks below it, text first. Chapters are two columns
from 900 px, illustration 58 : copy 42, sides alternating (01 art left, 02 art right…), and
stack below it with the illustration first. Steps are three columns from 900 px, a row of
art-beside-line from 600 to 900, and stacked below 600. Questions and the requirements
table sit side by side from 1100 px. Prose never runs wider than 60ch.

**Type.** Two display sizes and no more: `text-hero` is the h1 and is the largest type on
the page at every width (64 px at 1440, 86 at 1920, 40 on a phone); `text-section` is every
h2 — the four chapter titles, Get started, Questions and the Download close (44 / 58 / 30).
Under those, the contract's tiers: one title size (steps, questions), body, label.

---

## Where the design came from

The first build was assembled from the **Promo — film boards** page of the Figma source of
truth (file `RShlfbx2TfxrdleKPIry8w`, node `316:2`), one section per board. That mapping no
longer holds, and nothing on the page should be read as "board NN".

- **The hero** composes board 44's idea (Product hero `413:2` — one line and the download
  pair over the product) with the **PvP short** (`../../design/promo-film.md`, 24 fps):
  instead of the launcher's Play screen, the thing a player sees — the VOID overlay opening
  over a live match. `HeroMatch` is a loop cut from the short with the HUD and the menu built
  live in code on top of it.
- **The four chapters are new.** Each illustration is built in code from the cell
  primitive (`quiet-cell-system.md` §3), not exported from Figma, so it stays in step with
  the product's real vocabulary (29 mods, their categories, the nine HUD anchors, the three
  seeded loadouts).
- **What survives from the boards:** the Download close keeps board 38's centred mark,
  heading and button pair (`406:8`); the footer keeps board 43 (`410:5`); the requirements
  rows are board 41's (`408:8`, still placeholder); the step-2 line and the frame-budget
  numbers are boards 37 and 35's.

The **site header is not on any board**. It is built out of the product shell's own navbar
vocabulary: lockup left, light nav links, one primary action.

---

## The design system

`app/globals.css` is the whole system, expressed as a Tailwind `@theme`. It is derived
from [`../../design/quiet-cell-system.md`][contract] — **not** from `design/tokens.css`
or `@void/ui/tokens.css`, which still carry the superseded direction (Bricolage
Grotesque, backdrop blur, noise overlays, accent glows). §0 of the contract replaced all
of it, and the contract wins.

[contract]: ../../design/quiet-cell-system.md

- **Tailwind's default colour palette is cleared** (`--color-*: initial`). Four fill
  steps, three inks, four category hues. If a colour is not in the theme it is not in the
  system, and `bg-red-500` fails rather than quietly shipping.
- **No blur, no shadow, no gradient fill, no blend mode.** Depth is carried by fill steps
  — shell → ground → card → raised.
- **Outfit only**, 300 / 400 / 500, loaded with `next/font/local` from the same woff2
  instances `@void/ui` bundles for the launcher and the in-game overlay. 300 and 500 are
  preloaded; 400 is its own non-preloaded family, reached with `font-regular` (there is
  no `font-normal` — see `globals.css`). Nothing on the page sets 400 today; if the h1
  ever moves to it, turn that family's `preload` on in `layout.tsx`.
- **`--color-ink-3` is `#808387`, not the contract's `#626569`**, which is 3.4:1 on the
  shell and fails WCAG AA. Prose and nav links are `ink-2`; `ink-3` is the label tier only,
  and on the raised fill it has to step up to `ink-2`.
- **Hue when on.** A mod's art and its category label take the category's hue while the
  mod is enabled — HUD `#9F8BFF`, PvP `#FF9E7A`, Visual `#7ADFFF`, Utility `#7AE0B0` — and
  an off mod is grey. That lives in the illustrations. The page's own chrome carries no
  colour at all apart from the cell field under the pointer.
- **Nothing takes longer than 200 ms.** Hover in 140 ms ease-out, leave in 100 ms, the
  field drains in 200 ms.

### Scaling

Every size in the theme is `clamp(min, Xvw, max)` where the vw term is `max ÷ 1920 × 100`,
so **at 1920 the page renders at exactly the authored values** and scales down from there.
The spacing is the page's own rhythm, not the film's: the boards left ~260 px between a
heading and its content, which is a 1080p frame with nothing else to do. Now a heading sits
`head` (48 px at 1440) above what it introduces, sections are `section` (72) apart on each
side, chapters `chapter` (84) apart, and the gutter is 90. Breakpoints exist only where a
grid has to change shape: 600, 900 and 1100, and 1041 for the header.

### The client components

Everything else is a server component. These are the `"use client"` files:

| File | Why |
|---|---|
| `Reveal.tsx` | Scroll reveal — an 8px rise over 140 ms on a 90 ms stagger. The hidden state is keyed on `html.js`, set by the inline boot script in `<head>` (`lib/boot.ts`), so nothing is hidden without JS. At the end of the page everything on screen is shown, since the last strip can never reach the reveal band. **The hero never reveals** — its text, buttons and illustration paint with the first frame. Chapters, steps, questions and the close do. A MutationObserver picks up nodes that mount after it. |
| `CellField.tsx` | §5's reactive rule — the ambient cell field behind the hero and the close, monochrome at 3% and warmed to 22% in the hue where the pointer is. The radial is a *mask*, not a painted gradient. |
| `Header.tsx` | The sticky hairline, and below 1041 px the compact menu sheet. Its anchors are the chapters, Get started and Questions. |
| `DownloadButtons.tsx` | macOS-first on the server; Windows-first on Windows; on phones and tablets a "runs on macOS and Windows" line and a Copy link instead. |
| `illustrations/**` | The hero and chapter art. Owned there, placed by the sections; each fills its parent's width, sets its own aspect ratio and server-renders a static frame. |
| `Meter.tsx` | §4's meter as a real `role="slider"`. **Not on the page any more** — it served the retired Category hues section. Kept for reuse. |

All are additive. With JavaScript off the page renders complete, every link
resolves, and only the motion, the menu sheet and the illustrations' animation are lost.
`prefers-reduced-motion` turns the motion off too.

---

## Assets

`public/media/` holds the hero's film loop (`hero-match.mp4` / `.webm`, poster
`hero-match-poster.webp`), cut from the PvP short. It belongs to `HeroMatch`; re-cut it
there. The two PNG shells the first build used (`shell-play.png`, `shell-mods.png`) are
gone — nothing on the page, and nothing in the OG image, drew them.

---

## Before this goes public

Not yet true or not yet wired:

1. **Both download buttons point at `#download`.** They need the real release artefacts.
2. **The requirements table is placeholder content** (board 41 is marked `— PLACEHOLDER`
   in Figma). Confirm every row against the shipping build — OS versions, memory, Java
   bundling, the 48 MB.
3. **Chapter 04's numbers are placeholder — MEASURE BEFORE SHIP.** `PerformanceChapter`
   draws `PERF` from `illustrations/launcher/data.ts` (a 7.0 ms average frame, a 119 fps
   1% low, VOID at 0.3 ms of a 16.7 ms budget), and the chapter's facts repeat
   "About 0.3 ms". Time a real frame and change both together. Only "16.7 ms at 60 fps" is
   arithmetic.
4. **Six footer links have no destination**: Changelog, Keybinds, Discord, GitHub, Terms
   and Privacy resolve to `#`. The rest are in-page anchors and work.
5. **Optional cosmetics are planned.** The page no longer says *no store / no pass / no
   paid tier* anywhere, and the whole *Left out* section is gone. "Is it free?" answers for
   the client only and says cosmetics are coming, with **no pricing claim**. Keep it that
   way until pricing is decided; don't reintroduce a "never" about money.

Lines that are load-bearing and should not be "improved":

- Step 2 says *"Your Microsoft account, the same one the game uses."* The tempting
  generic line is "no account needed" — it would be false, and claiming it in a download
  flow is the kind of thing that gets a client dismissed on its first review.
- The footer carries **NOT AFFILIATED WITH MOJANG OR MICROSOFT**, visible, in every
  build. Any third-party Minecraft client needs it.
- Chapter copy is written from the product, not about the design: Right Shift is
  `DEFAULT_MENU_KEY`, L is the loadout-cycle key, 29 mods in four categories is
  `schema/mods/`, the nine anchors are `loadout.rs`, and Sword PvP / Bedwars / UHC are the
  seeded loadouts in `defaults.rs`. If any of those change, the chapter copy changes with it.

---

## Layout

```
app/
  layout.tsx        fonts, metadata, the boot script, the reveal observer
  not-found.tsx     the 404, in the page's own system
  page.tsx          section order — hero, four chapters, steps, questions, close
  globals.css       the design system as a Tailwind @theme
  fonts/            Outfit 300 / 400 / 500 woff2, from packages/ui/src/fonts
  lab/              dev-only review pages for the illustrations (404 in production)
components/
  Section.tsx       Band / Wrap / Eyebrow / SectionHead — the scaffold
  Mark.tsx          the 16-cell mark, and its cell-by-cell assembly
  Button.tsx        §4's primary / secondary / link-arrow / meta
  Meter.tsx         §4's meter with §5's pointer bleed (unused on the page)
  CellField.tsx     §3's ambient field, §5's hue-under-the-pointer
  Reveal.tsx        scroll reveal
  Header.tsx        the sticky nav and the compact menu
  DownloadButtons.tsx  the platform-aware download pair
  illustrations/    the hero and chapter art, built from the cell primitive
  sections/         Hero, Chapters, GetStarted, Questions, Download, Footer
lib/
  boot.ts           the inline <head> script: html.js and data-platform
  og.tsx            shared drawing for the generated OG / Twitter / Apple images
public/media/       the hero's film loop
```
