/**
 * The cell as a drawing primitive: a bitmap written as strings (`#` a cell,
 * anything else a gap) turned into ONE svg path of §3 cells — squares with a
 * 30% radius on a whole-number grid. Sprites scale by the svg's size only,
 * uniformly, and are never rotated.
 */

const n = (v: number) => +v.toFixed(3)

/** One rounded cell at (x, y), edge `s`, as path data. */
export function cellD(x: number, y: number, s = 0.86) {
  const o = (1 - s) / 2
  const r = n(s * 0.3)
  const e = n(s - 2 * r)
  return `M${n(x + o + r)} ${n(y + o)}h${e}a${r} ${r} 0 0 1 ${r} ${r}v${e}a${r} ${r} 0 0 1 -${r} ${r}h-${e}a${r} ${r} 0 0 1 -${r} -${r}v-${e}a${r} ${r} 0 0 1 ${r} -${r}z`
}

/** Every `ch` in the bitmap as cells. */
export function bitmapD(rows: readonly string[], ch = "#", s?: number) {
  let d = ""
  rows.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) if (row[x] === ch) d += cellD(x, y, s)
  })
  return d
}

export function Glyph({
  rows,
  className,
  s,
}: {
  rows: readonly string[]
  className?: string
  s?: number
}) {
  const w = rows[0].length
  const h = rows.length
  return (
    <svg
      viewBox={`0 0 ${w} ${h}`}
      className={className}
      style={{ "--gw": w, "--gh": h } as React.CSSProperties}
      aria-hidden="true"
    >
      <path d={bitmapD(rows, "#", s)} />
    </svg>
  )
}

export const CROSS = ["...#...", "...#...", ".......", "##.#.##", ".......", "...#...", "...#..."]

