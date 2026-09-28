import type { ComponentType, ReactNode } from "react"

import { HudChapter } from "../illustrations/HudChapter"
import { LoadoutChapter } from "../illustrations/LoadoutChapter"
import { OverlayChapter } from "../illustrations/OverlayChapter"
import { PerformanceChapter } from "../illustrations/PerformanceChapter"
import { at, Eyebrow, Wrap } from "../Section"

/**
 * The four product chapters — the page's body. Each is one big illustration
 * and short copy, sides alternating: 01 art left, 02 art right, and so on.
 *
 * ≥900px: two columns, illustration 58 : copy 42, vertically centred.
 * Below: stacked, illustration first, prose held to 60ch.
 *
 * The illustrations are built from the cell primitive in
 * `components/illustrations/` and are owned there — this file only places
 * them. Each fills its column's width and sets its own 4:3 ratio.
 *
 * Copy is written to players, from what the product does: the menu key is
 * `settings.rs` DEFAULT_MENU_KEY (RSHIFT), the loadout cycle key is L, the 29
 * mods and their four categories are `schema/mods/*.json`, the nine anchors
 * are `loadout.rs` Anchor, and the three ready-made loadouts are
 * `defaults.rs`. Chapter 04's share of the frame is PLACEHOLDER — see
 * README, "Before this goes public".
 */

type Fact = [label: string, value: ReactNode]

type ChapterSpec = {
  id: string
  n: string
  name: string
  title: string
  body: ReactNode
  facts: Fact[]
  Art: ComponentType<{ className?: string }>
}

const CHAPTERS: ChapterSpec[] = [
  {
    id: "overlay",
    n: "01",
    name: "Overlay",
    title: "It opens over the game.",
    body: (
      <>
        Press Right Shift mid-match and the menu opens over the running game. Every mod you
        turn on lights up in its category’s colour, and every change applies the moment you make
        it. Close it and keep playing.
      </>
    ),
    facts: [
      ["Opens with", "Right Shift, or any key you choose"],
      ["Mods", "29, in HUD, PvP, Visual and Utility"],
      ["View", "A grid or a list, settings beside"],
    ],
    Art: OverlayChapter,
  },
  {
    id: "hud",
    n: "02",
    name: "HUD",
    title: "Put it anywhere.",
    body: (
      <>
        Drag any widget where you want it. It snaps to one of nine anchors, so it stays put at any
        window size, and the arrow keys nudge it the rest of the way. Ping and armour turn amber
        when they cross the threshold you set.
      </>
    ),
    facts: [
      ["Anchors", "Nine, with snapping"],
      ["Widgets", "Eleven, from FPS to potion effects"],
      ["Each one", "Its own place, scale and settings"],
    ],
    Art: HudChapter,
  },
  {
    id: "loadouts",
    n: "03",
    name: "Loadouts",
    title: "One per game mode.",
    body: (
      <>
        A loadout is a whole setup under one name: which mods are on, how each one is set, and
        where your HUD sits. Switch loadouts and all of it changes at once, from the launcher or
        with L in game.
      </>
    ),
    facts: [
      ["Ready to go", "Sword PvP, Bedwars and UHC"],
      ["Switch", "In the launcher, or press L in game"],
      ["Holds", "Mods, their settings and your HUD"],
    ],
    Art: LoadoutChapter,
  },
  {
    id: "performance",
    n: "04",
    name: "Performance",
    title: "Out of the way of your frames.",
    body: (
      <>
        At 60 fps the game has 16.7 milliseconds to draw each frame. VOID’s HUD and menu draw
        inside that same frame, not in a window of their own, and they are built to take as little
        of it as they can.
      </>
    ),
    facts: [
      ["One frame", "16.7 ms at 60 fps"],
      // PLACEHOLDER — MEASURE BEFORE SHIP. Matches PerformanceChapter's
      // placeholder figure; both change together once a real frame is timed.
      ["VOID’s share", "About 0.3 ms"],
      ["Drawn", "In the game’s own frame"],
    ],
    Art: PerformanceChapter,
  },
]

function Chapter({ spec, flip }: { spec: ChapterSpec; flip: boolean }) {
  const { id, n, name, title, body, facts, Art } = spec
  const headingId = `${id}-h`

  return (
    <section
      id={id}
      aria-labelledby={headingId}
      className={`grid grid-cols-1 gap-y-[clamp(28px,4vw,48px)] min-[900px]:items-center min-[900px]:gap-x-split ${
        flip
          ? "min-[900px]:grid-cols-[minmax(0,42fr)_minmax(0,58fr)]"
          : "min-[900px]:grid-cols-[minmax(0,58fr)_minmax(0,42fr)]"
      }`}
    >
      <div
        data-reveal
        data-art
        className={flip ? "min-[900px]:col-start-2 min-[900px]:row-start-1" : undefined}
      >
        <Art />
      </div>

      <div
        className={`max-w-measure ${
          flip ? "min-[900px]:col-start-1 min-[900px]:row-start-1" : ""
        }`}
      >
        <Eyebrow i={1}>
          <span className="tabular-nums text-ink-2">{n}</span>
          <span aria-hidden="true" className="mx-[0.9em] text-ink-3/50">
            ·
          </span>
          {name}
        </Eyebrow>

        <h2
          id={headingId}
          data-reveal
          style={at(2)}
          className="mt-eyebrow text-section font-light text-balance"
        >
          {title}
        </h2>

        <p
          data-reveal
          style={at(3)}
          className="mt-[clamp(16px,1.458vw,28px)] text-prose text-ink-2 text-pretty"
        >
          {body}
        </p>

        <dl
          data-reveal
          style={at(4)}
          className="mt-[clamp(24px,2.083vw,40px)] border-t border-rule-soft"
        >
          {facts.map(([label, value]) => (
            <div
              key={label}
              className="grid grid-cols-[clamp(104px,8.333vw,160px)_minmax(0,1fr)] items-baseline gap-x-4 border-b border-rule-soft py-[clamp(10px,0.833vw,16px)]"
            >
              <dt className="text-cap font-medium uppercase text-ink-3">{label}</dt>
              <dd className="m-0 text-body text-ink-2">{value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </section>
  )
}

export function Chapters() {
  return (
    <div className="pb-section">
      <Wrap className="grid gap-y-chapter">
        {CHAPTERS.map((spec, i) => (
          <Chapter key={spec.id} spec={spec} flip={i % 2 === 1} />
        ))}
      </Wrap>
    </div>
  )
}
