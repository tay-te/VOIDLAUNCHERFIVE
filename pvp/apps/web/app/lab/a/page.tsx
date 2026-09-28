import { notFound } from "next/navigation"

import { HeroMatch } from "@/components/illustrations/HeroMatch"
import { HudChapter } from "@/components/illustrations/HudChapter"
import { OverlayChapter } from "@/components/illustrations/OverlayChapter"

/**
 * Review page for the in-game illustrations (hero, chapter 01, chapter 02),
 * each at several widths. Dev only. `?p=hero|overlay|hud` and `?w=390` narrow
 * it to one piece or one width.
 */

const PIECES = {
  hero: HeroMatch,
  overlay: OverlayChapter,
  hud: HudChapter,
} as const

const WIDTHS = [1400, 960, 720, 480, 390, 340]

export default async function LabA({
  searchParams,
}: {
  searchParams: Promise<{ p?: string; w?: string }>
}) {
  if (process.env.NODE_ENV === "production") notFound()
  const { p, w } = await searchParams
  const pieces = Object.entries(PIECES).filter(([k]) => !p || p === k)
  const widths = w ? [Number(w)] : WIDTHS

  return (
    <main className="min-h-screen bg-shell p-8">
      {pieces.map(([name, Piece]) => (
        <section key={name} className="mb-24">
          <h2 className="mb-6 text-eyebrow font-medium uppercase text-ink-3">{name}</h2>
          <div className="flex flex-col gap-12">
            {widths.map((width) => (
              <div key={width} data-piece={name} data-width={width} style={{ width }}>
                <p className="mb-2 text-meta font-medium uppercase text-ink-3">{width}px</p>
                <Piece />
              </div>
            ))}
          </div>
        </section>
      ))}
    </main>
  )
}
