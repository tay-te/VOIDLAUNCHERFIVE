"use client"

import { useEffect, useRef, useState, type RefObject } from "react"

import { claimSlot, createTimeline, useActive, useReducedMotion } from "./timing"

type Timeline = ReturnType<typeof createTimeline>

/**
 * Play a short sequence once, when the piece first enters view, then rest on the end
 * state. The server renders `end`, so without JavaScript (or under reduced motion) the
 * piece is simply finished. A piece already on screen at hydration stays finished
 * rather than blanking and replaying. Pieces entering view together claim board 27's
 * 90ms slots, so a row of three plays left to right.
 *
 * Pauses offscreen and in a hidden tab; `replay()` runs it again (on hover).
 */
export function useOnce<S>(
  ref: RefObject<Element | null>,
  start: S,
  end: S,
  cues: readonly (readonly [number, S])[],
): { state: S; replay: () => void } {
  const [state, setState] = useState<S>(end)
  const reduced = useReducedMotion()
  const active = useActive(ref)
  const armed = useRef(false)
  const tl = useRef<Timeline | null>(null)
  const slotTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const build = () => createTimeline(cues.map(([at, s]) => [at, () => setState(s)] as const))

  // Arm on mount — only if the piece is still below (or above) the fold.
  useEffect(() => {
    if (reduced) return
    const el = ref.current
    if (!el) return
    const r = el.getBoundingClientRect()
    const onScreen = r.bottom > 0 && r.top < window.innerHeight
    if (onScreen) return
    armed.current = true
    setState(start)
  }, [reduced])

  // Play on first sight; pause and resume with visibility.
  useEffect(() => {
    if (reduced) {
      // The hydration pass reads reduced motion as false, so the piece may already
      // be armed at its start state — put it back on the finished frame.
      armed.current = false
      if (slotTimer.current) clearTimeout(slotTimer.current)
      if (tl.current) tl.current.finish()
      else setState(end)
      return
    }
    if (active) {
      if (armed.current && !tl.current) {
        armed.current = false
        const t = build()
        tl.current = t
        slotTimer.current = setTimeout(() => t.play(), claimSlot())
      } else {
        tl.current?.play()
      }
    } else {
      if (slotTimer.current) clearTimeout(slotTimer.current)
      tl.current?.pause()
    }
  }, [active, reduced])

  useEffect(
    () => () => {
      if (slotTimer.current) clearTimeout(slotTimer.current)
      tl.current?.pause()
    },
    [],
  )

  const replay = () => {
    if (reduced || !active) return
    if (tl.current && !tl.current.done) return
    setState(start)
    const t = build()
    tl.current = t
    // one frame at the start state, so the transitions have somewhere to come from
    requestAnimationFrame(() => requestAnimationFrame(() => t.play()))
  }

  return { state, replay }
}
