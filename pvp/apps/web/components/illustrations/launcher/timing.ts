"use client"

import { useEffect, useState, useSyncExternalStore, type RefObject } from "react"

/**
 * Timing for the launcher illustrations. The design system's clock, and the three
 * rules every piece obeys: nothing runs offscreen, nothing runs in a hidden tab, and
 * `prefers-reduced-motion` gets a still.
 */

/** §5 / globals.css motion — the whole vocabulary. */
export const T = {
  in: 140, // hover in, ease-out
  out: 100, // leave, ease-in
  drain: 200, // release drains
  stagger: 40, // cell / column stagger
  reveal: 90, // board 27's section stagger
} as const

/* ---------------------------------------------------------------- reduced motion */

const RM = "(prefers-reduced-motion: reduce)"
function subscribeRM(cb: () => void) {
  const mq = window.matchMedia(RM)
  mq.addEventListener("change", cb)
  return () => mq.removeEventListener("change", cb)
}
export function useReducedMotion(): boolean {
  return useSyncExternalStore(
    subscribeRM,
    () => window.matchMedia(RM).matches,
    () => false,
  )
}

/* --------------------------------------------------------------------- activity */

function subscribeVis(cb: () => void) {
  document.addEventListener("visibilitychange", cb)
  return () => document.removeEventListener("visibilitychange", cb)
}
function usePageVisible(): boolean {
  return useSyncExternalStore(
    subscribeVis,
    () => document.visibilityState === "visible",
    () => false,
  )
}

/**
 * True while the element intersects the viewport **and** the tab is visible.
 * False on the server and before the first observation, so nothing starts until
 * the piece has actually been seen.
 */
export function useActive(ref: RefObject<Element | null>, rootMargin = "0px"): boolean {
  const [inView, setInView] = useState(false)
  const visible = usePageVisible()

  useEffect(() => {
    const el = ref.current
    if (!el || !("IntersectionObserver" in window)) return
    const io = new IntersectionObserver(([e]) => setInView(e.isIntersecting), {
      rootMargin,
      threshold: 0.01,
    })
    io.observe(el)
    return () => io.disconnect()
  }, [ref, rootMargin])

  return inView && visible
}

/* -------------------------------------------------------------------- scheduler */

/**
 * The three step pieces reveal on board 27's 90ms stagger when they enter view
 * together. Each claims the next slot; a slot more than one gap in the past is free.
 */
let nextSlot = 0
export function claimSlot(gap: number = T.reveal): number {
  const now = performance.now()
  const at = Math.max(now, nextSlot)
  nextSlot = at + gap
  return at - now
}

/* --------------------------------------------------------------------- timeline */

export type Cue = readonly [atMs: number, run: () => void]

/**
 * A pausable list of cues. setTimeout-driven (no rAF): pausing clears the pending
 * timer and remembers the elapsed time, so a piece that scrolls away mid-play
 * resumes where it stopped and does no work in between.
 */
export function createTimeline(cues: readonly Cue[], onDone?: () => void) {
  let i = 0
  let elapsed = 0
  let startedAt = 0
  let timer: ReturnType<typeof setTimeout> | null = null

  const schedule = () => {
    if (i >= cues.length) {
      onDone?.()
      return
    }
    const [at, run] = cues[i]
    const wait = Math.max(0, at - elapsed)
    startedAt = performance.now()
    timer = setTimeout(() => {
      elapsed += performance.now() - startedAt
      elapsed = Math.max(elapsed, at)
      i += 1
      run()
      schedule()
    }, wait)
  }

  return {
    play() {
      if (timer !== null || i >= cues.length) return
      schedule()
    },
    pause() {
      if (timer === null) return
      clearTimeout(timer)
      timer = null
      elapsed += performance.now() - startedAt
    },
    /** Jump straight to the end state. */
    finish() {
      if (timer !== null) clearTimeout(timer)
      timer = null
      for (; i < cues.length; i++) cues[i][1]()
      onDone?.()
    },
    get done() {
      return i >= cues.length
    },
  }
}
