import { forwardRef, memo, type CSSProperties, type ReactNode, type Ref } from "react"

import { Mark } from "@/components/Mark"

import { Glyph } from "./cells"
import { cx } from "./live"
import s from "./game.module.css"

/**
 * The in-game overlay menu — frames `289:1611` / `289:3024` as the shipped
 * build draws them (7 across, category hues, ESC cap on the close).
 *
 * Tiles are "hue when on": an ON tile draws its preview art and category label
 * in its category's hue; OFF is grey at half strength with a muted name. The
 * tile ground, the well, the borders and the switch never take a colour.
 */

export type Cat = "HUD" | "PvP" | "Visual" | "Utility"

export const HUE: Record<Cat, string> = {
  HUD: "#9f8bff",
  PvP: "#ff9e7a",
  Visual: "#7adfff",
  Utility: "#7ae0b0",
}

/** What a tile's well draws — a small picture of what the mod draws. */
export type Art =
  | readonly ["n", string, string]
  | readonly ["keys" | "armour" | "potions"]
  | readonly ["meter", string, string, string, string]
  | readonly ["frame", string]
  | readonly ["glyph", readonly string[]]

export type Mod = readonly [id: string, name: string, cat: Cat, art: Art]

const G = {
  cross: ["...#...", "...#...", ".......", "##.#.##", ".......", "...#...", "...#..."],
  sword: ["......##", ".....###", "....###.", "#..###..", "##.##...", ".###....", "..##....", ".#.##...", "#...#..."],
  box: ["#######", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", "#.....#", "#######"],
  eye: ["..###..", ".#...#.", "#..#..#", ".#...#.", "..###.."],
  heart: [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."],
}

export const MODS: readonly Mod[] = [
  ["fps", "FPS display", "HUD", ["n", "142", "FPS"]],
  ["ping", "Ping", "HUD", ["n", "42", "MS"]],
  ["keys", "Keystrokes", "HUD", ["keys"]],
  ["armour", "Armor status", "HUD", ["armour"]],
  ["cps", "CPS counter", "HUD", ["n", "6.2", "CPS"]],
  ["potions", "Potion effects", "HUD", ["potions"]],
  ["combo", "Combo counter", "PvP", ["n", "7", "COMBO"]],
  ["cross", "Crosshair", "Visual", ["glyph", G.cross]],
  ["zoom", "Zoom", "Utility", ["frame", "4.0×"]],
  ["sprint", "Toggle sprint", "PvP", ["meter", "KEY", "SPRINT", "0100000", "0111111"]],
  ["memory", "Memory", "HUD", ["n", "1462", "MB"]],
  ["sneak", "Toggle sneak", "PvP", ["meter", "KEY", "SNEAK", "0010000", "0011111"]],
  ["anim", "Old animations", "PvP", ["glyph", G.sword]],
  ["bright", "Fullbright", "Visual", ["meter", "WORLD", "SEEN", "0001111", "1111111"]],
  ["sat", "Saturation", "HUD", ["n", "13.7", "SAT"]],
  ["hitbox", "Hitboxes", "PvP", ["glyph", G.box]],
  ["hitcol", "Hit colour", "PvP", ["meter", "ALPHA", "TINT", "1111100", "0000110"]],
  ["tint", "Damage tint", "PvP", ["glyph", G.heart]],
  ["fov", "FOV changer", "PvP", ["n", "90°", "FOV"]],
  ["look", "Freelook", "PvP", ["glyph", G.eye]],
  ["input", "Old input", "PvP", ["meter", "MINE", "USE", "1110000", "0000111"]],
]

export const mod = (id: string) => MODS.find((m) => m[0] === id)!

/* ------------------------------------------------------------ previews */

const Readout = ({ v, u }: { v: string; u: string }) => (
  <span className={s.art}>
    <span className={cx(s.artBig, s.tnum)}>{v}</span>
    <span className={s.artUnit}>{u}</span>
  </span>
)

function Preview({ art }: { art: Art }) {
  switch (art[0]) {
    case "n":
      return <Readout v={art[1]} u={art[2]} />
    case "keys": {
      const k = (l: string, w?: boolean) => (
        <i className={s.artKey} data-wide={w ? "" : undefined}>
          {l}
        </i>
      )
      return (
        <span className={s.artKeys}>
          <span>{k("W")}</span>
          <span>
            {k("A")}
            {k("S")}
            {k("D")}
          </span>
          <span>
            {k("LMB", true)}
            {k("RMB", true)}
          </span>
        </span>
      )
    }
    case "armour":
      return (
        <span className={s.artArmour}>
          {[0.62, 0.78, 0.37, 0.8].map((w, i) => (
            <span key={i}>
              <i className={s.artSlot} />
              <i className={s.artWear}>
                <i
                  style={{ width: `${w * 100}%`, background: w < 0.5 ? "var(--warn)" : undefined }}
                />
              </i>
            </span>
          ))}
        </span>
      )
    case "potions":
      return (
        <span className={s.artRows}>
          {(
            [
              ["SPEED II", "1:24", "#7aebb5"],
              ["STRENGTH", "0:42", "#d9a93a"],
            ] as const
          ).map(([l, t, c]) => (
            <span className={s.artLine} key={l}>
              <i className={s.artPip} style={{ "--p": c } as CSSProperties} />
              <b>{l}</b>
              <span className={s.tnum}>{t}</span>
            </span>
          ))}
        </span>
      )
    case "meter":
      return (
        <span className={s.artMeter}>
          {[
            [art[1], art[3]],
            [art[2], art[4]],
          ].map(([l, bits]) => (
            <span key={l} style={{ display: "contents" }}>
              <span style={{ opacity: 0.6 }}>{l}</span>
              <span className={s.artCells}>
                {[...bits].map((b, i) => (
                  <i key={i} data-on={b === "1" ? "" : undefined} />
                ))}
              </span>
            </span>
          ))}
        </span>
      )
    case "frame":
      return <span className={cx(s.artFrame, s.tnum)}>{art[1]}</span>
    case "glyph":
      return <Glyph rows={art[1]} className={s.artGlyph} s={0.9} />
  }
}

/* --------------------------------------------------------------- tiles */

function Switch() {
  return (
    <span className={s.sw} aria-hidden="true">
      <span className={s.knob}>
        <i />
        <i />
        <i />
      </span>
    </span>
  )
}

type TileProps = {
  m: Mod
  on: boolean
  hot?: boolean
  /** Makes the tile a real switch. */
  onToggle?: (id: string) => void
  /** Ref on the switch, so a demo cursor can find it. */
  switchRef?: Ref<HTMLSpanElement>
  tabIndex?: number
}

export const Tile = memo(function Tile({ m, on, hot, onToggle, switchRef, tabIndex }: TileProps) {
  const [id, name, cat, art] = m
  const body = (
    <>
      <span className={s.well} aria-hidden="true">
        <Preview art={art} />
      </span>
      <span className={s.foot}>
        <span className={s.names}>
          <span className={s.name}>{name}</span>
          <span className={s.cat} aria-hidden="true">
            {cat.toUpperCase()}
          </span>
        </span>
        <span ref={switchRef} style={{ display: "flex" }}>
          <Switch />
        </span>
      </span>
    </>
  )
  const props = {
    className: cx(s.tile, hot && s.hot),
    "data-off": on ? undefined : "",
    style: { "--c": HUE[cat] } as CSSProperties,
  }
  return onToggle ? (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      tabIndex={tabIndex}
      onClick={(e) => {
        e.stopPropagation()
        onToggle(id)
      }}
      {...props}
    >
      {body}
    </button>
  ) : (
    <span {...props}>{body}</span>
  )
})

/* --------------------------------------------------------------- icons */

const ICONS = {
  move: "M5 9l-3 3 3 3M9 5l3-3 3 3M15 19l-3 3-3-3M19 9l3 3-3 3M2 12h20M12 2v20",
  grid: "M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z",
  list: "M4 6h16M4 12h16M4 18h16",
  gear: "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0M12 2v3M12 19v3M4.9 4.9 7 7M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1",
  search: "M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0M21 21l-4.3-4.3",
  x: "M18 6 6 18M6 6l12 12",
}

export function Icon({ name }: { name: keyof typeof ICONS }) {
  return (
    <svg viewBox="0 0 24 24" className={s.icon} aria-hidden="true">
      <path d={ICONS[name]} />
    </svg>
  )
}

/* --------------------------------------------------------------- panel */

type PanelProps = {
  children: ReactNode
  style?: CSSProperties
  className?: string
  /** "N OF 29 ENABLED" */
  enabled: number
  /** The close control. A real button when `onClose` is given. */
  onClose?: () => void
  closeTabIndex?: number
  /** Leave out the layout, view and tool buttons: mark, tabs, close only. */
  lean?: boolean
}

export const Panel = forwardRef<HTMLDivElement, PanelProps>(function Panel(
  { children, style, className, enabled, onClose, closeTabIndex, lean },
  ref,
) {
  const close = (
    <>
      <Icon name="x" />
      <span className={s.kbd}>ESC</span>
    </>
  )
  return (
    <div ref={ref} className={cx(s.panel, className)} style={style}>
      <div className={s.pbar}>
        <span className={s.vmark} aria-hidden="true">
          <Mark className={s.ring} />
          <span className={s.word}>VOID</span>
        </span>
        {lean ? null : (
          <span className={cx(s.dest, s.wideOnly)} aria-hidden="true">
            <Icon name="move" />
            HUD LAYOUT
          </span>
        )}
        <span className={cx(s.center, s.midOnly)} aria-hidden="true">
          <span className={s.tabs}>
            <span className={cx(s.tab, s.tabOn)}>All</span>
            {(["HUD", "PvP", "Visual", "Utility"] as const).map((c) => (
              <span className={s.tab} key={c}>
                <i className={s.tabDot} style={{ "--c": HUE[c] } as CSSProperties} />
                {c}
              </span>
            ))}
          </span>
          {lean ? null : (
            <span className={s.seg}>
              <span className={cx(s.segBtn, s.segOn)}>
                <Icon name="grid" />
              </span>
              <span className={s.segBtn}>
                <Icon name="list" />
              </span>
            </span>
          )}
        </span>
        <span className={s.tools}>
          {lean ? null : (
            <>
              <span className={cx(s.obtn, s.wideOnly)} aria-hidden="true">
                <Icon name="gear" />
              </span>
              <span className={cx(s.obtn, s.wideOnly)} aria-hidden="true">
                <Icon name="search" />
              </span>
            </>
          )}
          {onClose ? (
            <button
              type="button"
              className={cx(s.obtn, s.close)}
              aria-label="Close the menu"
              tabIndex={closeTabIndex}
              onClick={(e) => {
                e.stopPropagation()
                onClose()
              }}
            >
              {close}
            </button>
          ) : (
            <span className={cx(s.obtn, s.close)} aria-hidden="true">
              {close}
            </span>
          )}
        </span>
      </div>
      <div className={s.rule} />
      <div className={s.grid}>{children}</div>
      <div className={s.status} aria-hidden="true">
        <span className={s.tnum}>{enabled} OF 29 ENABLED</span>
        <span className={s.midOnly}>CHANGES APPLY IMMEDIATELY</span>
      </div>
      <div className={cx(s.hint, s.wideOnly)} aria-hidden="true">
        {"R-SHIFT CLOSES   ·   CLICK A TILE TO EDIT IT   ·   ⌘K SEARCH"}
      </div>
    </div>
  )
})

/* -------------------------------------------------------------- cursor */

const ARROW = [
  "#............",
  "##...........",
  "#o#..........",
  "#oo#.........",
  "#ooo#........",
  "#oooo#.......",
  "#ooooo#......",
  "#oooooo#.....",
  "#ooooooo#....",
  "#oooooooo#...",
  "#ooooo#####..",
  "#oo#oo#......",
  "#o#.#oo#.....",
  "##..#oo#.....",
  "#....#oo#....",
  ".....#oo#....",
  "......##.....",
]

/** The pointer, drawn on the cell grid. Positioned by its hotspot. */
export function Cursor({ x, y, hidden }: { x: number; y: number; hidden?: boolean }) {
  return (
    <svg
      viewBox="0 0 13 17"
      className={s.cursor}
      data-hidden={hidden ? "" : undefined}
      style={{ transform: `translate(${x}px, ${y}px)` }}
      aria-hidden="true"
    >
      <path d={cursorD("#")} fill="#0b0b0c" />
      <path d={cursorD("o")} fill="#edeeef" />
    </svg>
  )
}

let arrowCache: Record<string, string> = {}
function cursorD(ch: string) {
  if (!arrowCache[ch]) {
    let d = ""
    ARROW.forEach((row, y) => {
      for (let x = 0; x < row.length; x++) if (row[x] === ch) d += `M${x} ${y}h1v1h-1z`
    })
    arrowCache = { ...arrowCache, [ch]: d }
  }
  return arrowCache[ch]
}
