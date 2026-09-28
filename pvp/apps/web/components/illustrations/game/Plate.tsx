import { memo, useId } from "react"

import { cx } from "./live"
import s from "./game.module.css"

/**
 * The game plate — board 47's arena in cells. Original art on the cell grid:
 * a terrain silhouette, a ground course, two generic blocky fighters mid-swing.
 * No Mojang textures, skins or marks.
 *
 * It is one svg. Shapes are plain rectangles filled with a *pattern* of §3
 * cells, so a silhouette of a thousand cells costs one path and one pattern —
 * the grid does the drawing. Every shape sits on the pattern's whole-number
 * grid; sprites scale with the svg, uniformly, and are never rotated.
 *
 * The fighters swap between two poses on a CSS steps() loop, paused whenever
 * the piece is off screen and removed under reduced motion.
 */

type R = readonly [x: number, y: number, w: number, h: number]

const d = (rects: readonly R[], ox = 0, oy = 0, flip = 0) =>
  rects
    .map(([x, y, w, h]) => `M${flip ? flip - x - w : x + ox} ${y + oy}h${w}v${h}h${-w}z`)
    .join("")

/* A fighter facing right, feet on y = 24. */
const BODY: R[] = [
  [2, 0, 6, 6], // head
  [2, 6, 6, 8], // torso
  [0, 6, 2, 7], // back arm
]
const THRUST = {
  body: [...BODY, [8, 7, 4, 2], [2, 14, 2, 10], [6, 14, 2, 10]] as R[],
  blade: [[12, 6, 1, 3], [13, 7, 6, 1]] as R[],
}
const RAISE = {
  body: [...BODY, [8, 3, 2, 5], [1, 14, 2, 10], [7, 14, 2, 10]] as R[],
  blade: [[7, 2, 4, 1], [10, 1, 1, 1], [11, 0, 1, 1], [12, -1, 1, 1], [13, -2, 1, 1], [14, -3, 1, 1]] as R[],
}
const SPARKS: R[] = [
  [78, 5, 1, 1],
  [81, 3, 1, 1],
  [80, 8, 1, 1],
  [83, 6, 1, 1],
  [76, 2, 1, 1],
]

/* Far terrain, one height (in 5-unit blocks) per column. */
const HILLS = "23443212334543212234432123454321"

/* Floating blocks in the void above. */
const DRIFT: R[] = [
  [15, 15, 5, 5],
  [20, 15, 5, 5],
  [125, 10, 5, 5],
  [70, 5, 5, 5],
  [140, 30, 5, 5],
]

/** Where the fighters stand, as the x of each one's back edge. */
const A = 58
const B = 102

export const Plate = memo(function Plate({
  live,
  paused,
  ground: g = 65,
  className,
}: {
  live?: boolean
  paused?: boolean
  /** The ground line, in plate units (the plate is 160 × 120, cells of 1 and 5). */
  ground?: number
  className?: string
}) {
  const raw = useId().replace(/[^a-zA-Z0-9]/g, "")
  const id = (k: string) => `${raw}${k}`
  const url = (k: string) => `url(#${id(k)})`
  const pat = (k: string, size: number, inset: number, alpha: number) => (
    <pattern id={id(k)} width={size} height={size} patternUnits="userSpaceOnUse">
      <rect
        x={inset}
        y={inset}
        width={size - 2 * inset}
        height={size - 2 * inset}
        rx={(size - 2 * inset) * 0.3}
        fill={`rgb(237 238 239 / ${alpha})`}
      />
    </pattern>
  )
  const hills = [...HILLS].map((h, i) => [i * 5, g - +h * 5, 5, +h * 5] as R)
  const course: R[] = [
    [0, g, 45, 5],
    [50, g, 60, 5],
    [115, g, 45, 5],
    [5, g - 5, 10, 5],
    [25, g - 5, 5, 5],
    [135, g - 5, 10, 5],
    [150, g - 10, 5, 10],
  ]
  const second: R[] = [
    [0, g + 5, 20, 5],
    [35, g + 5, 10, 5],
    [60, g + 5, 40, 5],
    [120, g + 5, 25, 5],
  ]
  const fy = g - 24
  const pose = (p: typeof THRUST, q: typeof RAISE) => (
    <>
      <path d={d(p.body, A, fy)} fill={url("fa")} />
      <path d={d(p.blade, A, fy)} fill={url("sw")} />
      <path d={d(q.body, 0, fy, B)} fill={url("fb")} />
      <path d={d(q.blade, 0, fy, B)} fill={url("sw")} />
    </>
  )
  return (
    <svg
      viewBox="0 0 160 120"
      preserveAspectRatio="xMidYMid slice"
      className={cx(s.plate, live && s.live, paused && s.paused, className)}
      aria-hidden="true"
    >
      <defs>
        {pat("dot", 4, 1.7, 0.06)}
        {pat("far", 5, 0.12, 0.035)}
        {pat("hl", 5, 0.15, 0.05)}
        {pat("gr", 5, 0.25, 0.16)}
        {pat("g2", 5, 0.25, 0.07)}
        {pat("fa", 1, 0.07, 0.52)}
        {pat("fb", 1, 0.07, 0.28)}
        {pat("sw", 1, 0.07, 0.85)}
        {pat("sp", 1, 0.1, 0.95)}
      </defs>
      <rect width="160" height="120" fill="#0d0d0f" />
      <rect width="160" height="120" fill={url("dot")} />
      <path d={d(DRIFT)} fill={url("far")} />
      <path d={d(hills)} fill={url("hl")} />
      <rect y={g} width="160" height={120 - g} fill="#0b0b0c" />
      <rect y={g + 10} width="160" height={110 - g} fill={url("dot")} />
      <path d={d(second)} fill={url("g2")} />
      <path d={d(course)} fill={url("gr")} />
      <g className={s.poseA}>{pose(THRUST, RAISE)}</g>
      <g className={s.poseB}>{pose(RAISE, THRUST)}</g>
      <path className={s.spark} d={d(SPARKS, 0, fy)} fill={url("sp")} />
    </svg>
  )
})
