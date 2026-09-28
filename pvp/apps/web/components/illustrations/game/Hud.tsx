import { memo, type CSSProperties, type ReactNode } from "react"

import { CROSS, Glyph } from "./cells"
import { clock, cx } from "./live"
import s from "./game.module.css"

/**
 * The VOID HUD, redrawn for the web from `packages/ui/src/styles/06-hud.css`.
 *
 * The chrome / world split (§1): colour on the HUD encodes a value and nothing
 * else — the ping dot past its threshold, a durability bar under its one, the
 * pressed keycap, a potion's own swatch and the CPS figure. Everything else is
 * ink on a chip.
 */

export type Anchor = "tl" | "t" | "tr" | "l" | "c" | "r" | "bl" | "b" | "br"

/** Place an element at an anchor with an inset (in design px, `--u`). */
export function place(a: Anchor, dx = 24, dy = 24): CSSProperties {
  const u = (v: number) => `calc(${v} * var(--u))`
  const st: CSSProperties = {}
  const h = a.includes("l") ? "l" : a.includes("r") ? "r" : "c"
  const v = a.includes("t") ? "t" : a.includes("b") ? "b" : "m"
  if (h === "l") st.left = u(dx)
  else if (h === "r") st.right = u(dx)
  else st.left = `calc(50% + ${u(dx)})`
  if (v === "t") st.top = u(dy)
  else if (v === "b") st.bottom = u(dy)
  else st.top = `calc(50% + ${u(dy)})`
  if (h === "c" || v === "m") st.translate = `${h === "c" ? "-50%" : "0"} ${v === "m" ? "-50%" : "0"}`
  return st
}

/** A positioned HUD element whose mod can be switched off. */
export function El({
  on = true,
  style,
  className,
  children,
}: {
  on?: boolean
  style?: CSSProperties
  className?: string
  children: ReactNode
}) {
  return (
    <div className={cx(s.el, !on && s.gone, className)} style={style}>
      {children}
    </div>
  )
}

export const Fps = memo(function Fps({ fps, low }: { fps: number; low?: number }) {
  return (
    <span className={s.chip}>
      <span className={cx(s.big, s.tnum)}>{fps}</span>
      <span className={s.unit}>fps</span>
      {low ? (
        <span className={cx(s.unit, s.tnum, s.fine)}>
          ·&nbsp;&nbsp;1% low {low}
        </span>
      ) : null}
    </span>
  )
})

/** `ping.good_ms` 60, `bad_ms` 150 — the defaults. */
export const pingTone = (ms: number) => (ms >= 150 ? "bad" : ms > 60 ? "warn" : "ok")

export const Ping = memo(function Ping({ ms }: { ms: number }) {
  return (
    <span className={s.chip}>
      <span className={s.dot} data-tone={pingTone(ms)} />
      <span className={cx(s.val, s.tnum)}>{ms}</span>
      <span className={s.unit}>ms</span>
    </span>
  )
})

/** A figure over its unit on a chip — combo, memory. */
export const Readout = memo(function Readout({ value, unit }: { value: number; unit: string }) {
  return (
    <span className={s.chip}>
      <span className={cx(s.val, s.tnum)}>{value}</span>
      <span className={s.unit}>{unit}</span>
    </span>
  )
})

export const Cps = memo(function Cps({ cps }: { cps: number }) {
  return (
    <span className={s.chip}>
      <span className={cx(s.val, s.hueInk, s.tnum)}>{cps}</span>
      <span className={s.unit}>CPS</span>
    </span>
  )
})

/** Bits: W 1, A 2, S 4, D 8, LMB 16, RMB 32. */
export const Keys = memo(function Keys({
  down = 0,
  lmb,
  rmb,
}: {
  down?: number
  lmb?: number
  rmb?: number
}) {
  const k = (label: string, bit: number, count?: number) => (
    <span
      className={s.key}
      data-down={down & bit ? "" : undefined}
      data-wide={count !== undefined ? "" : undefined}
    >
      {label}
      {count !== undefined ? <span className={cx(s.kcps, s.tnum)}>{count}</span> : null}
    </span>
  )
  return (
    <span className={s.keys}>
      <span className={s.krow}>{k("W", 1)}</span>
      <span className={s.krow}>
        {k("A", 2)}
        {k("S", 4)}
        {k("D", 8)}
      </span>
      <span className={s.krow}>
        {k("LMB", 16, lmb ?? 0)}
        {k("RMB", 32, rmb ?? 0)}
      </span>
    </span>
  )
})

export type Potion = readonly [name: string, color: string, seconds: number]

export const Potions = memo(function Potions({ rows }: { rows: readonly Potion[] }) {
  return (
    <span className={cx(s.box, s.potions)}>
      {rows.map(([name, color, secs]) => (
        <span className={s.prow} key={name}>
          <span className={s.swatch} style={{ background: color }} />
          <span className={cx(s.pname, s.fine)}>{name}</span>
          <span className={cx(s.ptime, s.tnum)}>{clock(secs)}</span>
        </span>
      ))}
    </span>
  )
})

export type Piece = readonly [label: string, left: number, max: number]

/** `armor_status.warn_below` 0.5 — the default. */
export const Armour = memo(function Armour({ pieces }: { pieces: readonly Piece[] }) {
  return (
    <span className={cx(s.box, s.armour)}>
      {pieces.map(([label, left, max]) => {
        const f = left / max
        return (
          <span className={s.arow} key={label}>
            <i className={s.aicon} />
            <span className={s.abody}>
              <span className={cx(s.alabels, s.fine)}>
                <span className={s.alabel}>{label}</span>
                <span className={cx(s.aval, s.tnum)}>
                  {left} / {max}
                </span>
              </span>
              <span className={s.bar}>
                <i
                  className={s.barFill}
                  data-tone={f < 0.5 ? "warn" : undefined}
                  style={{ width: `${(f * 100).toFixed(1)}%` }}
                />
              </span>
            </span>
          </span>
        )
      })}
    </span>
  )
})

export function Crosshair() {
  return <Glyph rows={CROSS} className={s.cross} s={0.9} />
}
