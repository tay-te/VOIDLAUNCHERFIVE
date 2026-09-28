/**
 * The VOID mark — 16 cells on a 7 × 7 grid, each a square at §3's 30% radius.
 * This is the same object the film assembles in its cold open (board 01) and
 * the same geometry the footer and download boards carry.
 *
 * It is geometry rather than an exported asset: `get_design_context` returns
 * it as 16 rounded rectangles, so it is reproduced cell for cell and scales
 * to any size without shipping a PNG.
 *
 * CELLS is ordered **clockwise from the top row**, which is the order board
 * 01 builds them in. The index drives the 40ms stagger in `mark-assemble`.
 */
export const CELLS: [number, number][] = [
  [2, 0],
  [3, 0],
  [4, 0],
  [5, 1],
  [6, 2],
  [6, 3],
  [6, 4],
  [5, 5],
  [4, 6],
  [3, 6],
  [2, 6],
  [1, 5],
  [0, 4],
  [0, 3],
  [0, 2],
  [1, 1],
]

export function Mark({
  className = "",
  /** Play board 01's cell-by-cell build when the mark scrolls into view. */
  assemble = false,
}: {
  className?: string
  assemble?: boolean
}) {
  return (
    <svg
      viewBox="0 0 7 7"
      aria-hidden="true"
      {...(assemble ? { "data-reveal": true } : {})}
      className={`fill-cell-mark ${assemble ? "mark-assemble" : ""} ${className}`}
    >
      {CELLS.map(([x, y], n) => (
        <rect
          key={`${x}-${y}`}
          x={x}
          y={y}
          width="1"
          height="1"
          rx="0.3"
          style={assemble ? ({ "--n": n } as React.CSSProperties) : undefined}
        />
      ))}
    </svg>
  )
}

/** Mark + wordmark. The tracking leaves a trailing gap after the D, so it is
 *  pulled back with a negative margin to keep the lockup optically centred. */
export function Lockup({
  className = "",
  markClassName = "w-[1.62em] h-[1.62em]",
  showWord = true,
  assemble = false,
}: {
  className?: string
  markClassName?: string
  showWord?: boolean
  assemble?: boolean
}) {
  return (
    <span className={`inline-flex items-center gap-[0.46em] ${className}`}>
      <Mark assemble={assemble} className={`shrink-0 ${markClassName}`} />
      {showWord ? (
        <span className="text-wordmark font-light text-ink -mr-[0.13em]">VOID</span>
      ) : (
        <span className="sr-only">VOID</span>
      )}
    </span>
  )
}
