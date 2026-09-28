import { HeroMatch } from "../illustrations/HeroMatch"
import { Meta } from "../Button"
import { CellField } from "../CellField"
import { DownloadButtons } from "../DownloadButtons"
import { Eyebrow, Wrap } from "../Section"

/**
 * The hero. Board 44's idea — the product under one line and a pair of
 * download buttons — composed with the PvP short: instead of the launcher's
 * Play screen, the thing a player actually sees, which is the VOID overlay
 * opening over a live match (`HeroMatch`, a film loop with the HUD and menu
 * built in code on top of it).
 *
 * ≥1100px it is a split with the illustration dominant (60 : 40 of the
 * content width after the gap, which is ~58% of it); below, the text comes
 * first and the illustration runs full width under it. The two buttons grow
 * to share the text column, so where they have to wrap (1100–1400) they wrap
 * to two equal full-width rows rather than two ragged ones.
 *
 * Nothing here reveals. The h1 and the illustration's poster frame are the
 * LCP candidates and the buttons have to be there before anything moves, so
 * the whole hero paints with the first frame, with or without JavaScript.
 */
export function Hero() {
  return (
    <section
      id="top"
      aria-labelledby="top-h"
      className="relative pt-[clamp(40px,4.167vw,80px)] pb-chapter"
    >
      <CellField />

      <Wrap>
        <div className="grid grid-cols-1 gap-y-[clamp(40px,5vw,64px)] min-[1100px]:grid-cols-[minmax(0,40fr)_minmax(0,60fr)] min-[1100px]:items-center min-[1100px]:gap-x-[clamp(40px,3.333vw,64px)]">
          <div className="max-w-[640px]">
            <Eyebrow reveal={false}>
              <span className="text-ink-2">VOID</span>
              <span aria-hidden="true" className="mx-[0.9em] text-ink-3/50">
                ·
              </span>
              Minecraft PvP client
            </Eyebrow>

            <h1 id="top-h" className="mt-eyebrow text-hero font-light text-balance">
              Everything in one window.
            </h1>

            <p className="mt-[clamp(18px,1.458vw,28px)] max-w-measure text-subline text-ink-2 text-pretty">
              Free, and finished. Twenty-nine mods, the HUD and your loadouts, in an overlay
              that opens over the match.
            </p>

            <DownloadButtons className="mt-[clamp(28px,2.5vw,48px)] min-[1100px]:[&>a]:grow" />

            <Meta reveal={false} className="mt-[clamp(20px,1.667vw,32px)]">
              48 MB &nbsp;·&nbsp; Minecraft 1.8.9 &nbsp;·&nbsp; macOS and Windows
            </Meta>
          </div>

          <div data-art>
            <HeroMatch />
          </div>
        </div>
      </Wrap>
    </section>
  )
}
