"use client"

import { useEffect, useId, useRef, useState, type CSSProperties, type PointerEvent } from "react"

import { PERF } from "./launcher/data"
import { Frame, HUES, cx, kit } from "./launcher/Kit"
import s from "./launcher/perf.module.css"
import { useActive, useReducedMotion } from "./launcher/timing"

/**
 * Chapter 04 — "Out of the way of your frames". A live frame-time histogram, one
 * bar per frame, scrolling left; **only the newest bar takes the hue** (board 22,
 * 384:8). Above it the FPS figure and its 1% low; below it one frame's budget, where
 * VOID's cost is a single cell of fifty (board 35, 400:8).
 *
 * Every number comes from `PERF` in `launcher/data.ts` — PLACEHOLDER, MEASURE BEFORE
 * SHIP. The series is generated from a fixed seed, so the server renders the same
 * first frame the client continues from.
 *
 * Runs at 10 bars a second only while visible and the tab is shown. Each tick moves
 * 52 bars by writing SVG attributes directly — React renders the chart once. Hover
 * (or touch) freezes it and reads out the bar under the pointer.
 */

const BARS = 52
const STEP = 17 // 12-unit cells on a 17-unit step, §4's meter geometry
const CELL = 12
const R = CELL * 0.3
const MS_PER_CELL = 1
const LINE_Y = 12 // the 16.7 ms line, in chart units
const BASE = LINE_Y + (PERF.budgetMs / MS_PER_CELL) * STEP // bottom of the lowest cell
const HISTORY = 400 // frames kept for the 1% low
const TICK_MS = 100
const STATS_EVERY = 10 // ticks — the figure updates once a second, like debugFPS

/* ---------------------------------------------------------------- the model */

function mulberry32(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function frameSource(seed: number) {
  const r = mulberry32(seed)
  return () => {
    const n = (r() + r() + r() - 1.5) * 2 // ≈ N(0, 1)
    let ms = PERF.frameMs + n * PERF.jitterMs
    if (r() < PERF.hitchChance) ms += PERF.hitchMs[0] + r() * (PERF.hitchMs[1] - PERF.hitchMs[0])
    return Math.max(3, ms)
  }
}

const cellsFor = (ms: number) => Math.max(1, Math.min(16, Math.round(ms / MS_PER_CELL)))

function stats(history: number[]) {
  const recent = history.slice(-60)
  const mean = recent.reduce((a, b) => a + b, 0) / recent.length
  const sorted = [...history].sort((a, b) => a - b)
  const p99 = sorted[Math.floor(sorted.length * 0.99)]
  return {
    fps: Math.round(1000 / mean),
    low: Math.round(1000 / p99),
    avg: mean.toFixed(1),
  }
}

/** Deterministic first frame — identical on the server and the client. */
function initial() {
  const next = frameSource(PERF.seed)
  const history = Array.from({ length: HISTORY }, next)
  return { next, history, stats: stats(history) }
}

const SPLIT = (() => {
  const per = PERF.budgetMs / PERF.budgetCells
  const tick = Math.round(PERF.split.tick / per)
  const render = Math.round(PERF.split.render / per)
  const cost = Math.max(1, Math.round(PERF.split.void / per))
  return { tick, render, cost, headroom: PERF.budgetCells - tick - render - cost }
})()

/* ------------------------------------------------------------------ piece */

export function PerformanceChapter({ className }: { className?: string }) {
  const frame = useRef<HTMLDivElement>(null)
  const active = useActive(frame)
  const reduced = useReducedMotion()
  const uid = useId().replace(/[^a-zA-Z0-9_-]/g, "")

  const [seedState] = useState(initial)
  const model = useRef(seedState)
  const bars = useRef<(SVGGElement | null)[]>([])
  const fpsEl = useRef<HTMLSpanElement>(null)
  const lowEl = useRef<HTMLSpanElement>(null)
  const avgEl = useRef<HTMLSpanElement>(null)
  const [probe, setProbe] = useState<{ i: number; ms: number } | null>(null)

  const first = seedState.history.slice(-BARS)

  /* The ticker: one new frame per tick, the window shifts left by one bar. */
  useEffect(() => {
    if (!active || reduced || probe) return
    let n = 0
    const timer = setInterval(() => {
      const m = model.current
      m.history.push(m.next())
      if (m.history.length > HISTORY) m.history.shift()
      const win = m.history.slice(-BARS)
      for (let i = 0; i < BARS; i++) {
        const g = bars.current[i]
        if (g) draw(g, win[i])
      }
      if (++n % STATS_EVERY === 0) {
        const st = stats(m.history)
        if (fpsEl.current) fpsEl.current.textContent = String(st.fps)
        if (lowEl.current) lowEl.current.textContent = String(st.low)
        if (avgEl.current) avgEl.current.textContent = st.avg
      }
    }, TICK_MS)
    return () => clearInterval(timer)
  }, [active, reduced, probe])

  const onProbe = (e: PointerEvent<SVGSVGElement>) => {
    const box = e.currentTarget.getBoundingClientRect()
    const x = ((e.clientX - box.left) / box.width) * 880
    const i = Math.max(0, Math.min(BARS - 1, Math.floor((x + (STEP - CELL) / 2) / STEP)))
    const ms = model.current.history.slice(-BARS)[i]
    if (!probe || probe.i !== i) setProbe({ i, ms })
  }

  const mono = `${uid}m`
  const live = `${uid}l`
  const dots = `${uid}d`

  return (
    <Frame
      units={1000}
      frameRef={frame}
      className={className}
      role="img"
      aria-label={`Frame time, one bar per frame, holding near ${PERF.frameMs.toFixed(1)} milliseconds — about ${seedState.stats.fps} fps with a 1% low of ${seedState.stats.low} — far under the ${PERF.budgetMs} millisecond budget. VOID's own share of a frame is one cell of ${PERF.budgetCells}.`}
      style={{ "--hue": HUES.hud } as CSSProperties}
    >
      {/* The figure and its 1% low — the FPS display mod's own two numbers. */}
      <div className={s.top} aria-hidden="true">
        <span className={s.figure}>
          <span ref={fpsEl} className={cx(s.fps, kit.tnum)}>
            {seedState.stats.fps}
          </span>
          <span className={cx(kit.label, s.unit)}>fps</span>
        </span>
        <span className={s.low}>
          <span className={cx(kit.label, s.lowLabel)}>1% low</span>
          <span className={s.lowValue}>
            <span ref={lowEl} className={kit.tnum}>
              {seedState.stats.low}
            </span>
          </span>
        </span>
      </div>

      <div className={cx(kit.label, s.chartHead)} aria-hidden="true">
        <span>
          Frame time <span className="mx-[0.6em] opacity-60">·</span>
          <span ref={avgEl} className={kit.tnum}>
            {seedState.stats.avg}
          </span>{" "}
          ms avg
        </span>
        <span>
          {PERF.budgetMs} ms <span className="mx-[0.6em] opacity-60">·</span> {PERF.budgetFps} fps
        </span>
      </div>

      <svg
        className={s.chart}
        viewBox={`0 0 880 ${BASE + 10}`}
        aria-hidden="true"
        onPointerMove={onProbe}
        onPointerDown={onProbe}
        onPointerLeave={() => setProbe(null)}
      >
        <defs>
          <pattern id={mono} width={STEP} height={STEP} y={BASE - CELL} patternUnits="userSpaceOnUse">
            <rect width={CELL} height={CELL} rx={R} fill="#edeeef" fillOpacity="0.3" />
          </pattern>
          <pattern id={live} width={STEP} height={STEP} y={BASE - CELL} patternUnits="userSpaceOnUse">
            <rect width={CELL} height={CELL} rx={R} fill={HUES.hud} fillOpacity="0.5" />
          </pattern>
          <pattern id={dots} width={STEP} height="4" x="4" patternUnits="userSpaceOnUse">
            <rect width="4" height="4" rx="1.2" fill="#edeeef" fillOpacity="0.22" />
          </pattern>
        </defs>

        {/* The budget line: 16.7 ms, drawn in cells. */}
        <rect x="0" y={LINE_Y - 2} width="880" height="4" fill={`url(#${dots})`} />

        {first.map((ms, i) => {
          const isNew = i === BARS - 1
          const isProbe = probe?.i === i
          const hue = probe ? isProbe : isNew
          const n = cellsFor(ms)
          return (
            <g
              key={i}
              ref={(el) => {
                bars.current[i] = el
              }}
            >
              <rect
                x={i * STEP}
                width={CELL}
                y={BASE - CELL - (n - 2) * STEP}
                height={n > 1 ? (n - 2) * STEP + CELL : 0}
                fill={`url(#${hue ? live : mono})`}
              />
              <rect
                x={i * STEP}
                width={CELL}
                height={CELL}
                rx={R}
                y={BASE - CELL - (n - 1) * STEP}
                fill={hue ? HUES.hud : "#edeeef"}
                fillOpacity={hue ? 0.95 : 0.62}
              />
            </g>
          )
        })}
      </svg>

      {probe ? (
        <span
          className={cx(kit.label, kit.tnum, s.probe)}
          style={{ left: `calc(${60 + probe.i * STEP + CELL / 2} * var(--u))` }}
          aria-hidden="true"
        >
          {probe.ms.toFixed(1)} ms
        </span>
      ) : null}

      <div className={s.rule} aria-hidden="true" />

      {/* One frame, fifty cells. VOID is the one in the hue. */}
      <div className={s.budget} aria-hidden="true">
        <span
          className={cx(kit.label, kit.tnum, s.voidTag)}
          style={{ left: `calc(${(SPLIT.tick + SPLIT.render) * ((880 - 12.5) / 49)} * var(--u))` }}
        >
          VOID <span className="opacity-60">·</span> {PERF.split.void} ms
        </span>
        <div className={s.budgetCells}>
          {Array.from({ length: PERF.budgetCells }, (_, i) => {
            const seg =
              i < SPLIT.tick ? 0.34
              : i < SPLIT.tick + SPLIT.render ? 0.5
              : i < SPLIT.tick + SPLIT.render + SPLIT.cost ? -1
              : 0.07
            return seg < 0 ? <i key={i} data-live="" /> : <i key={i} style={{ opacity: seg }} />
          })}
        </div>
        <div className={cx(kit.label, s.budgetFoot)}>
          <span>One frame</span>
          <span className={kit.tnum}>{PERF.budgetMs} ms</span>
        </div>
      </div>
    </Frame>
  )
}

/** Re-draws one bar in place — three attribute writes, no React. */
function draw(g: SVGGElement, ms: number) {
  const n = cellsFor(ms)
  const body = g.firstElementChild as SVGRectElement
  const cap = g.lastElementChild as SVGRectElement
  body.setAttribute("y", String(BASE - CELL - (n - 2) * STEP))
  body.setAttribute("height", String(n > 1 ? (n - 2) * STEP + CELL : 0))
  cap.setAttribute("y", String(BASE - CELL - (n - 1) * STEP))
}
