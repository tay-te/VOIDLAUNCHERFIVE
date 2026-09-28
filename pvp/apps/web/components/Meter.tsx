"use client"

import { useRef, useState } from "react"

/**
 * §4's meter — the client's signature control, and on this page a real one:
 * a `role="slider"` that takes a click or tap, a drag, and Arrow / Home / End,
 * with its readout following the value.
 *
 * N discrete cells; filled cells ink at .36, empty at .06, and **the current
 * cell in the category hue at .95**. The readout sits to the right in the same
 * hue. The component renders the track and the readout as siblings so they
 * drop straight into the parent's grid columns.
 *
 * §5.3's bleed — hue at .95 under the pointer, .40 at ±1, .16 at ±2 — is a hue
 * layer *over* each cell whose opacity animates, never a replacement of the
 * cell's own fill: a filled cell under the bleed can only get lighter, never
 * dig a dark hole in the run. It follows the pointer on hover and the value
 * while dragging; §5.4, release drains it over 200ms, leaving the hue only on
 * the value that was set. Hover in is 140ms ease-out.
 *
 * Cheap on purpose: geometry is measured once per hover or press, a move
 * inside the cell it is already on does nothing, and state changes only when
 * the pointer crosses into another cell. No rAF, no per-frame render.
 *
 * Cells shrink with the track (to a 10px floor), so fourteen of them fit a
 * 272px column — the content box at a 320px viewport.
 */

const BLEED = [0.95, 0.4, 0.16]

type Geometry = { pitch: number; originX: number }

export function Meter({
  cells,
  value: initial,
  hue,
  min,
  step,
  decimals,
  unit,
  spokenUnit,
  labelledBy,
}: {
  /** How many cells the meter has. */
  cells: number
  /** Index of the live cell — the value that is set to begin with. */
  value: number
  /** The category's hue, as a CSS colour. */
  hue: string
  /** The value cell 0 stands for, and the step between two cells. */
  min: number
  step: number
  decimals: number
  /** Printed after the number: "×", " blk", "%". */
  unit: string
  /** How the unit is read out: "times", "blocks", "percent". */
  spokenUnit: string
  /** Id of the element that names the meter. */
  labelledBy: string
}) {
  const [value, setValue] = useState(initial)
  /** The centre of the bleed, or null when it has drained. */
  const [centre, setCentre] = useState<number | null>(null)

  const trackRef = useRef<HTMLDivElement>(null)
  const geometry = useRef<Geometry | null>(null)
  const press = useRef<{ id: number; start: number } | null>(null)
  /** After a mouse release, the cell it let go over: hovering it again does
   *  not re-light the bleed that has just drained. Moving off it does. */
  const settled = useRef<number | null>(null)

  const number = (i: number) => Number((min + i * step).toFixed(decimals))
  const print = (i: number) => `${number(i).toFixed(decimals)}${unit}`

  const measure = () => {
    const kids = trackRef.current?.children
    const a = kids?.[0]?.getBoundingClientRect()
    const b = kids?.[1]?.getBoundingClientRect()
    geometry.current = a && b ? { pitch: b.left - a.left, originX: a.left + a.width / 2 } : null
  }

  const cellAt = (clientX: number) => {
    const g = geometry.current
    if (!g || g.pitch <= 0) return null
    return Math.min(cells - 1, Math.max(0, Math.round((clientX - g.originX) / g.pitch)))
  }

  const onPointerEnter = (event: React.PointerEvent) => {
    if (event.pointerType === "mouse") measure()
  }

  const onPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return
    measure()
    const i = cellAt(event.clientX)
    if (i === null) return
    event.currentTarget.setPointerCapture(event.pointerId)
    press.current = { id: event.pointerId, start: value }
    settled.current = null
    setValue(i)
    setCentre(i)
  }

  const onPointerMove = (event: React.PointerEvent) => {
    const pressed = press.current?.id === event.pointerId
    if (!pressed && event.pointerType !== "mouse") return
    const i = cellAt(event.clientX)
    if (i === null) return
    if (pressed) {
      setValue(i)
      setCentre(i)
      return
    }
    if (settled.current === i) return
    settled.current = null
    setCentre(i)
  }

  const release = (event: React.PointerEvent) => {
    if (press.current?.id !== event.pointerId) return
    press.current = null
    settled.current = event.pointerType === "mouse" ? value : null
    setCentre(null)
  }

  // A touch that turns into a page scroll is not an adjustment: put it back.
  const cancel = (event: React.PointerEvent) => {
    if (press.current?.id !== event.pointerId) return
    setValue(press.current.start)
    press.current = null
    setCentre(null)
  }

  const onPointerLeave = () => {
    if (press.current) return
    settled.current = null
    setCentre(null)
  }

  const onKeyDown = (event: React.KeyboardEvent) => {
    const next =
      event.key === "ArrowRight" || event.key === "ArrowUp"
        ? value + 1
        : event.key === "ArrowLeft" || event.key === "ArrowDown"
          ? value - 1
          : event.key === "Home"
            ? 0
            : event.key === "End"
              ? cells - 1
              : null
    if (next === null) return
    event.preventDefault()
    setValue(Math.min(cells - 1, Math.max(0, next)))
  }

  return (
    <>
      <div
        ref={trackRef}
        role="slider"
        tabIndex={0}
        aria-labelledby={labelledBy}
        aria-orientation="horizontal"
        aria-valuemin={number(0)}
        aria-valuemax={number(cells - 1)}
        aria-valuenow={number(value)}
        aria-valuetext={`${number(value).toFixed(decimals)} ${spokenUnit}`}
        onPointerEnter={onPointerEnter}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={release}
        onPointerCancel={cancel}
        onLostPointerCapture={release}
        onPointerLeave={onPointerLeave}
        onKeyDown={onKeyDown}
        style={{ "--hue": hue } as React.CSSProperties}
        // The padding is hit area — 44px tall at the smallest cell — and the
        // negative margin gives it back, so the layout is the cells alone.
        className="-m-4 flex cursor-pointer touch-pan-y select-none gap-[clamp(6px,0.73vw,14px)] rounded-[14px] p-4 focus-visible:outline-offset-[-10px]"
      >
        {Array.from({ length: cells }, (_, i) => {
          const distance = centre === null ? Infinity : Math.abs(i - centre)
          const bleed = BLEED[distance] ?? 0
          const rest =
            i === value
              ? "color-mix(in srgb, var(--hue) 95%, transparent)"
              : i < value
                ? "rgb(237 238 239 / 0.36)"
                : "rgb(237 238 239 / 0.06)"
          // The bleed is the cell's ::after — a static hue layer over the
          // resting fill whose opacity is `--bleed`, the product's own
          // technique (packages/ingame overlay.css `.meter__cell::after`).
          return (
            <i
              key={i}
              style={{ background: rest, "--bleed": bleed } as React.CSSProperties}
              className={`relative aspect-square w-[clamp(16px,1.771vw,34px)] min-w-2.5 shrink rounded-cell transition-[background-color] duration-[140ms] ease-out after:absolute after:inset-0 after:rounded-[inherit] after:bg-(--hue) after:opacity-(--bleed) after:transition-opacity ${
                bleed > 0
                  ? "after:duration-[140ms] after:ease-out"
                  : "after:duration-[200ms] after:ease-in"
              }`}
            />
          )
        })}
      </div>

      {/* The value is already on the slider's own aria-valuetext. */}
      <p
        aria-hidden="true"
        className="text-column font-light tabular-nums"
        style={{ color: hue, opacity: 0.95 }}
      >
        {print(value)}
      </p>
    </>
  )
}
