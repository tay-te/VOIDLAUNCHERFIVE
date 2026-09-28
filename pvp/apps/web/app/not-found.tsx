import type { Metadata } from "next"

import { LinkArrow } from "@/components/Button"
import { Mark } from "@/components/Mark"

export const metadata: Metadata = {
  title: "Not found — VOID",
}

/** The 404, in the page's own system: the mark, one line, the way home. */
export default function NotFound() {
  return (
    <main className="grid min-h-dvh place-items-center px-gutter py-section text-center">
      <div>
        <Mark className="mx-auto h-[clamp(40px,3.02vw,58px)] w-[clamp(40px,3.02vw,58px)]" />
        <p className="mt-[clamp(28px,2.6vw,50px)] text-eyebrow font-medium uppercase text-ink-3">
          <span className="tabular-nums text-ink-2">404</span>
          <span aria-hidden="true" className="mx-[0.9em] text-ink-3/50">·</span>
          Not found
        </p>
        <h1 className="mt-eyebrow text-hero font-light text-balance">
          There is nothing at this address.
        </h1>
        <p className="mt-[clamp(28px,2.5vw,48px)]">
          <LinkArrow href="/">Back to VOID</LinkArrow>
        </p>
      </div>
    </main>
  )
}
