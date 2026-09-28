import { at, Band, SectionHead, Wrap } from "../Section"

/**
 * Questions and requirements, one section. The questions run down the wider
 * column; the requirements sit beside them from 1100px as a compact spec
 * table, and below them under that. Both open on the same hairline so the two
 * read as one surface. Questions in the primary ink, answers in ink-2 — that
 * alone carries the hierarchy, so there are no cards.
 *
 * Two of the old questions ("Mac and Windows?", "How big is it?") are gone:
 * the table beside them answers both, in the same place.
 *
 * "Is it free?" answers for the client only. Optional cosmetics are planned,
 * so the page no longer says "no store / no pass / no paid tier" anywhere, and
 * says nothing about what the cosmetics will cost.
 */
const QUESTIONS = [
  [
    "Is it free?",
    "Yes. The client is free, every mod included. Optional cosmetics are coming later.",
  ],
  [
    "Where do settings live?",
    "In loadouts. Each one keeps its own mods, their settings and your HUD layout, and one key moves you to the next.",
  ],
  ["Which versions?", "One, the version PvP is played on. Others when they are finished, not before."],
  [
    "Does it need an account?",
    "Only the Microsoft account you already play with. Nothing else is asked for.",
  ],
]

/**
 * PLACEHOLDER. Film board 41 (408:8) carries this table as "— PLACEHOLDER" in
 * Figma; confirm every row against the shipping build before this goes public.
 */
const SPECS = [
  ["System", "macOS 12 or later, Windows 10 or later"],
  ["Minecraft", "Java Edition 1.8.9"],
  ["Account", "A Microsoft account that owns the game"],
  ["Java", "Bundled. Nothing to install."],
  ["Memory", "4 GB for the game, 8 GB in the machine"],
  ["Disk", "About 48 MB"],
]

export function Questions() {
  return (
    <Band id="questions" labelledBy="questions-h">
      <Wrap>
        <SectionHead id="questions-h" eyebrow="Questions" heading="Things people ask." />

        <div className="grid grid-cols-1 gap-y-[clamp(48px,5vw,72px)] min-[1100px]:grid-cols-[minmax(0,58fr)_minmax(0,42fr)] min-[1100px]:gap-x-split">
          <dl className="border-t border-rule-soft">
            {QUESTIONS.map(([question, answer], i) => (
              <div
                key={question}
                data-reveal
                style={at(i)}
                className="border-b border-rule-soft py-[clamp(20px,1.667vw,32px)]"
              >
                <dt className="text-title font-light text-ink">{question}</dt>
                <dd className="m-0 mt-[clamp(8px,0.625vw,12px)] max-w-measure text-body text-ink-2 text-pretty">
                  {answer}
                </dd>
              </div>
            ))}
          </dl>

          <div id="requirements">
            {/* The table's caption, as its first row: its top hairline lines
                up with the first question's. */}
            <h3
              data-reveal
              className="border-t border-rule-soft py-[clamp(14px,1.146vw,22px)] text-eyebrow font-medium uppercase text-ink-2"
            >
              Requirements
            </h3>
            <dl className="border-t border-rule-soft">
              {SPECS.map(([label, value], i) => (
                <div
                  key={label}
                  data-reveal
                  style={at(i)}
                  className="grid grid-cols-[clamp(96px,7.5vw,144px)_minmax(0,1fr)] items-baseline gap-x-4 border-b border-rule-soft py-[clamp(14px,1.146vw,22px)]"
                >
                  <dt className="text-cap font-medium uppercase text-ink-3">{label}</dt>
                  <dd className="m-0 text-body text-ink-2">{value}</dd>
                </div>
              ))}
            </dl>
          </div>
        </div>
      </Wrap>
    </Band>
  )
}
