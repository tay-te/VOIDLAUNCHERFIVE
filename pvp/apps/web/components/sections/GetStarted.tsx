import { StepDownload, StepLaunch, StepSignIn } from "../illustrations/StepArt"
import { at, Band, SectionHead, Wrap } from "../Section"

/**
 * Get started — three steps, each a small illustration, its number and name,
 * and one line. Three columns from 900px; between 600 and 900 each step is a
 * row (art beside its line) so a tablet does not scroll through three
 * full-width pictures; below 600 they stack.
 *
 * Step 2's line is load-bearing. The obvious generic line is "no account
 * needed" — it would be false. The client signs in through Microsoft like
 * every other Minecraft launcher, and claiming otherwise in a download flow is
 * the kind of thing that gets a client dismissed on its first review.
 */
const STEPS = [
  {
    n: "1",
    title: "Download",
    body: "One file for Mac or Windows. Nothing to configure afterwards.",
    Art: StepDownload,
  },
  {
    n: "2",
    title: "Sign in",
    body: "Your Microsoft account, the same one the game uses.",
    Art: StepSignIn,
  },
  {
    n: "3",
    title: "Launch",
    body: "Pick a loadout and press Enter. The game opens with it on.",
    Art: StepLaunch,
  },
]

export function GetStarted() {
  return (
    <Band id="get-started" labelledBy="get-started-h">
      <Wrap>
        <SectionHead
          id="get-started-h"
          eyebrow="Get started"
          heading="Three steps to your first match."
        />

        <ol className="grid grid-cols-1 gap-[clamp(32px,4vw,48px)] min-[900px]:grid-cols-3 min-[900px]:gap-colgap">
          {STEPS.map(({ n, title, body, Art }, i) => (
            <li
              key={n}
              data-reveal
              style={at(i)}
              className="grid grid-cols-1 content-start gap-y-[clamp(16px,1.667vw,28px)] min-[600px]:max-[899px]:grid-cols-[minmax(0,2fr)_minmax(0,3fr)] min-[600px]:max-[899px]:items-center min-[600px]:max-[899px]:gap-x-colgap"
            >
              <div data-art>
                <Art />
              </div>
              <div className="max-w-measure">
                <h3 className="flex items-baseline gap-[0.6em] text-title font-light">
                  <span className="tabular-nums text-ink-3">{n}</span>
                  {title}
                </h3>
                <p className="mt-[clamp(8px,0.625vw,12px)] text-body text-ink-2 text-pretty">{body}</p>
              </div>
            </li>
          ))}
        </ol>
      </Wrap>
    </Band>
  )
}
