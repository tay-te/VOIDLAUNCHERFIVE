import type { CSSProperties, ReactNode, Ref } from "react"

import k from "./kit.module.css"

/** A length in the piece's own unit: `u(12)` is 12/Nths of the piece's width. */
export const u = (n: number) => `calc(${n} * var(--u))`

/** Same, with a pixel floor — for the few labels that must stay legible at 340px. */
export const uMin = (n: number, px: number) => `max(${px}px, calc(${n} * var(--u)))`

export const HUES = {
  hud: "#9f8bff",
  pvp: "#ff9e7a",
  visual: "#7adfff",
  utility: "#7ae0b0",
} as const

export const WARN = "#d9a93a"

export function cx(...names: (string | false | null | undefined)[]) {
  return names.filter(Boolean).join(" ")
}

/**
 * The crop. Fills its parent's width, sets its own aspect ratio, and establishes the
 * container every `--u` length resolves against.
 */
export function Frame({
  units,
  ratio = "4 / 3",
  className,
  frameRef,
  children,
  style,
  ...rest
}: {
  units: number
  ratio?: string
  className?: string
  frameRef?: Ref<HTMLDivElement>
  children: ReactNode
} & React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div ref={frameRef} className={cx(k.frame, className)} style={{ ...style, aspectRatio: ratio }} {...rest}>
      <div className={k.stage} style={{ "--units": units } as CSSProperties}>
        {children}
      </div>
    </div>
  )
}

/** §4 toggle, sm. `delay` is its place in a stagger wave. */
export function Toggle({ on, delay = 0, className }: { on: boolean; delay?: number; className?: string }) {
  return (
    <span
      aria-hidden="true"
      data-on={on}
      className={cx(k.toggle, className)}
      style={{ "--d": `${delay}ms` } as CSSProperties}
    >
      <i>
        <b />
        <b />
        <b />
      </i>
    </span>
  )
}

export function Chevron({ size }: { size?: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 12 12"
      className={k.chev}
      style={size ? ({ "--s": size } as CSSProperties) : undefined}
    >
      <path d="M3 4.5 6 7.5 9 4.5" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** N cells; the first `filled` on. `live` (optional) is the one cell in the hue. */
export function CellMeter({
  cells,
  filled,
  live = null,
  size,
  gap,
  className,
  hue,
}: {
  cells: number
  filled: number
  live?: number | null
  size: string
  gap: string
  className?: string
  hue?: string
}) {
  return (
    <span
      aria-hidden="true"
      className={cx(k.meter, className)}
      style={{ "--s": size, "--gap": gap, ...(hue ? { "--hue": hue } : null) } as CSSProperties}
    >
      {Array.from({ length: cells }, (_, i) => (
        <i key={i} data-on={i < filled} data-live={live === i || undefined} />
      ))}
    </span>
  )
}

/** Three bars stepping down — the launcher's play mark. */
export function PlayMark() {
  return (
    <span aria-hidden="true" className={k.playmark}>
      <i />
      <i />
      <i />
    </span>
  )
}

export { k as kit }
