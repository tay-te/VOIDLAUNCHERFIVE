import { Meta } from "../Button"
import { CellField } from "../CellField"
import { DownloadButtons } from "../DownloadButtons"
import { Lockup } from "../Mark"
import { at, Wrap } from "../Section"

/**
 * The Download close — after film board 38 (406:8): mark, heading, one line,
 * the button pair, a meta line. The only centred composition on the page,
 * which is what makes it read as a call to action rather than another content
 * section.
 *
 * Its line and meta are its own. The first build repeated the hero's subline
 * and meta verbatim; by the end of the page a reader has seen both.
 *
 * The heading is the section tier like every other h2 — the hero's h1 stays
 * the largest type on the page.
 */
export function Download() {
  return (
    <section
      id="download"
      aria-labelledby="download-h"
      className="relative border-t border-rule-faint py-[calc(var(--spacing-section)*1.5)] text-center"
    >
      <CellField />

      <Wrap>
        <div data-reveal>
          <Lockup
            className="justify-center"
            markClassName="w-[clamp(40px,3.02vw,58px)] h-[clamp(40px,3.02vw,58px)]"
            showWord={false}
            assemble
          />
        </div>

        <h2
          id="download-h"
          data-reveal
          style={at(1)}
          className="mt-[clamp(24px,2.083vw,40px)] text-section font-light"
        >
          Get VOID.
        </h2>

        <p
          data-reveal
          style={at(2)}
          className="mx-auto mt-[clamp(14px,1.25vw,24px)] max-w-measure text-subline text-ink-2 text-balance"
        >
          Set it up once. It’s there every match after.
        </p>

        <div data-reveal style={at(3)}>
          <DownloadButtons className="mt-[clamp(28px,2.5vw,48px)] justify-center" />
        </div>

        <Meta i={4} className="mt-[clamp(20px,1.667vw,32px)]">
          Needs Minecraft: Java Edition
        </Meta>
      </Wrap>
    </section>
  )
}
