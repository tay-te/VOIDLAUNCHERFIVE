import { readFile } from "node:fs/promises"
import { join } from "node:path"

import { CELLS } from "@/components/Mark"

/**
 * Shared by the generated images (app/opengraph-image, twitter-image,
 * apple-icon). They are drawn by `next/og` at build time and prerendered, so
 * none of this runs on a request.
 *
 * Satori reads TTF / OTF / WOFF but not WOFF2, so app/fonts/og holds WOFF
 * copies of the same Outfit instances the page sets (OFL, as app/fonts).
 */

export const INK = "#edeeef"
export const INK_2 = "#9a9da1"
export const INK_3 = "#808387"
export const SHELL = "#0b0b0c"

export async function outfit() {
  const dir = join(process.cwd(), "app/fonts/og")
  const [light, medium] = await Promise.all([
    readFile(join(dir, "outfit-300.woff")),
    readFile(join(dir, "outfit-500.woff")),
  ])
  return [
    { name: "Outfit", data: light, weight: 300 as const, style: "normal" as const },
    { name: "Outfit", data: medium, weight: 500 as const, style: "normal" as const },
  ]
}

/** The 16-cell mark at §3's 30% radius, as the page draws it. */
export function MarkSvg({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 7 7">
      {CELLS.map(([x, y]) => (
        <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" rx="0.3" fill={INK} fillOpacity="0.9" />
      ))}
    </svg>
  )
}

/** §3's ambient field: 6px cells on a 40px step, ink at 3%. */
export const FIELD = `url("data:image/svg+xml,${encodeURIComponent(
  "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'><rect width='6' height='6' rx='1.8' fill='#edeeef' fill-opacity='0.03'/></svg>",
)}")`
