import type { ReactNode } from "react"

import type { ModId } from "./data"
import { WARN } from "./Kit"
import k from "./kit.module.css"

/**
 * Tile previews, redrawn from the launcher's Mods grid (`1600-12-mods.png`) as cells
 * and numerals. Drawn in `currentColor`, so the tile decides the colour: the mod's
 * category hue when it is on, grey when it is off. A 100 × 88 box, centred.
 */

const R = 0.3 // §3 — every cell is a square at a 30% radius

function Cell({ x, y, s, w, o }: { x: number; y: number; s: number; w?: number; o?: number }) {
  return <rect x={x} y={y} width={w ?? s} height={s} rx={s * R} opacity={o} />
}

function Readout({ value, unit }: { value: string; unit: string }) {
  return (
    <>
      <text x="50" y="50" textAnchor="middle" fontSize="27" fontWeight="500" letterSpacing="-0.5">
        {value}
      </text>
      <text x="50.8" y="64" textAnchor="middle" fontSize="6.4" fontWeight="500" letterSpacing="1.1" opacity="0.62">
        {unit}
      </text>
    </>
  )
}

function Keystrokes() {
  const s = 15
  const g = 3
  const x0 = 50 - s * 1.5 - g
  return (
    <>
      <Cell x={50 - s / 2} y={17} s={s} />
      {[0, 1, 2].map((i) => (
        <Cell key={i} x={x0 + i * (s + g)} y={17 + s + g} s={s} o={0.34} />
      ))}
      <Cell x={x0} y={17 + 2 * (s + g)} s={s} w={(3 * s + 2 * g - g) / 2} o={0.34} />
      <Cell x={x0 + (3 * s + 2 * g + g) / 2} y={17 + 2 * (s + g)} s={s} w={(3 * s + 2 * g - g) / 2} o={0.34} />
    </>
  )
}

function Armor({ on }: { on: boolean }) {
  const fills = [0.96, 0.61, 0.43, 0.12]
  return (
    <>
      {fills.map((f, i) => {
        const y = 27 + i * 10
        return (
          <g key={i}>
            <rect x="28" y={y} width="44" height="4.4" rx="1.3" opacity="0.2" />
            <rect
              x="28"
              y={y}
              width={Math.max(4.4, 44 * f)}
              height="4.4"
              rx="1.3"
              // `warn_below` 0.5 — the bar's real in-game amber, kept on the ON state.
              fill={on && f < 0.5 ? WARN : "currentColor"}
            />
          </g>
        )
      })}
    </>
  )
}

function Crosshair() {
  const c = 3.2
  const cx0 = 50 - c / 2
  const cy0 = 44 - c / 2
  const arm = [6.2, 11.4]
  return (
    <>
      <Cell x={cx0} y={cy0} s={c} />
      {arm.map((d) => (
        <g key={d} opacity="0.7">
          <Cell x={cx0} y={cy0 - d} s={c} />
          <Cell x={cx0} y={cy0 + d} s={c} />
          <Cell x={cx0 - d} y={cy0} s={c} />
          <Cell x={cx0 + d} y={cy0} s={c} />
        </g>
      ))}
    </>
  )
}

function Fullbright() {
  const s = 7.4
  const g = 2
  const n = 5
  const w = n * s + (n - 1) * g
  const x0 = 50 - w / 2
  const y0 = 44 - w / 2
  return (
    <>
      {Array.from({ length: n * n }, (_, i) => (
        <Cell key={i} x={x0 + (i % n) * (s + g)} y={y0 + Math.floor(i / n) * (s + g)} s={s} />
      ))}
    </>
  )
}

const ART: Partial<Record<ModId, (on: boolean) => ReactNode>> = {
  fps: () => <Readout value="142" unit="FPS" />,
  keystrokes: () => <Keystrokes />,
  cps: () => <Readout value="6.2" unit="CPS" />,
  armor_status: (on) => <Armor on={on} />,
  crosshair: () => <Crosshair />,
  zoom: () => <Readout value="2.0×" unit="ZOOM" />,
  hitboxes: () => <Readout value="3.2" unit="BLOCKS" />,
  fullbright: () => <Fullbright />,
}

export function Glyph({ id, on }: { id: ModId; on: boolean }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 100 88" className={k.glyph} fill="currentColor">
      {ART[id]?.(on)}
    </svg>
  )
}
