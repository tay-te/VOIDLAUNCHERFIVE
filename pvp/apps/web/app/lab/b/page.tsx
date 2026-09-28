import { notFound } from "next/navigation"

import { LoadoutChapter } from "@/components/illustrations/LoadoutChapter"
import { PerformanceChapter } from "@/components/illustrations/PerformanceChapter"
import { StepDownload, StepLaunch, StepSignIn } from "@/components/illustrations/StepArt"

/**
 * Review page for the launcher-side illustrations (chapters 03–04, the three steps).
 * Dev only — production builds 404 it.
 */
export default function LabB() {
  if (process.env.NODE_ENV === "production") notFound()

  const Cap = ({ children }: { children: React.ReactNode }) => (
    <p className="mb-3 text-cap font-medium uppercase tracking-[0.16em] text-ink-3">{children}</p>
  )

  return (
    <main className="min-h-screen bg-shell px-4 py-12 sm:px-10">
      <section id="loadout" className="space-y-14">
        <div>
          <Cap>03 · LoadoutChapter · 860</Cap>
          <div className="max-w-[860px]" data-w="860">
            <LoadoutChapter />
          </div>
        </div>
        <div className="flex flex-wrap items-start gap-10">
          <div className="w-[560px] max-w-full">
            <Cap>560</Cap>
            <LoadoutChapter />
          </div>
          <div className="w-[340px] max-w-full">
            <Cap>340</Cap>
            <LoadoutChapter />
          </div>
        </div>
      </section>

      <section id="performance" className="mt-24 space-y-14">
        <div>
          <Cap>04 · PerformanceChapter · 860</Cap>
          <div className="max-w-[860px]">
            <PerformanceChapter />
          </div>
        </div>
        <div className="flex flex-wrap items-start gap-10">
          <div className="w-[560px] max-w-full">
            <Cap>560</Cap>
            <PerformanceChapter />
          </div>
          <div className="w-[340px] max-w-full">
            <Cap>340</Cap>
            <PerformanceChapter />
          </div>
        </div>
      </section>

      <section id="steps" className="mt-24">
        <Cap>Get started · three steps at 360</Cap>
        <div className="grid grid-cols-1 gap-8 min-[1180px]:grid-cols-3 max-w-[1160px]">
          <StepDownload />
          <StepSignIn />
          <StepLaunch />
        </div>
        <div className="mt-14 flex flex-wrap gap-8">
          <div className="w-[280px]">
            <Cap>280</Cap>
            <StepDownload />
          </div>
          <div className="w-[280px]">
            <Cap>280</Cap>
            <StepSignIn />
          </div>
          <div className="w-[280px]">
            <Cap>280</Cap>
            <StepLaunch />
          </div>
        </div>
      </section>

      <section id="wide" className="mt-24">
        <Cap>1400</Cap>
        <div className="w-[1400px] max-w-full space-y-10">
          <LoadoutChapter />
          <PerformanceChapter />
        </div>
      </section>
    </main>
  )
}
