"use client"

import { useEffect, useRef, useState, type RefObject } from "react"

export const cx = (...names: (string | false | null | undefined)[]) =>
  names.filter(Boolean).join(" ")

/**
 * True while the element is on screen *and* the tab is visible. Everything
 * that moves in these pieces keys off this, so a piece scrolled away or in a
 * background tab runs no timers, plays no video and paints nothing.
 */
export function useActive(ref: RefObject<Element | null>) {
  const [active, setActive] = useState(false)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    let seen = false
    const update = () => setActive(seen && document.visibilityState === "visible")
    const io = new IntersectionObserver(([entry]) => {
      seen = entry.isIntersecting
      update()
    })
    io.observe(el)
    document.addEventListener("visibilitychange", update)
    return () => {
      io.disconnect()
      document.removeEventListener("visibilitychange", update)
    }
  }, [ref])
  return active
}

/**
 * `prefers-reduced-motion` or Save-Data: draw the still frame and nothing else.
 * True on the server and on first paint, so the SSR frame *is* the still one
 * and motion is only ever added after hydration.
 */
export function useStill() {
  const [still, setStill] = useState(true)
  useEffect(() => {
    const mq = matchMedia("(prefers-reduced-motion: reduce)")
    const save = (navigator as Navigator & { connection?: { saveData?: boolean } })
      .connection?.saveData
    const read = () => setStill(mq.matches || !!save)
    read()
    mq.addEventListener("change", read)
    return () => mq.removeEventListener("change", read)
  }, [])
  return still
}

/** setInterval that exists only while `on`. */
export function useTick(on: boolean, ms: number, fn: () => void) {
  const f = useRef(fn)
  useEffect(() => {
    f.current = fn
  })
  useEffect(() => {
    if (!on) return
    const id = setInterval(() => f.current(), ms)
    return () => clearInterval(id)
  }, [on, ms])
}

/**
 * A looping sequence of steps on setTimeout: `wait(i)` ms, then `run(i)`, then
 * the next. Stopping cancels the pending step; starting again resumes at the
 * step that was pending. No rAF.
 */
export function useSteps(
  on: boolean,
  count: number,
  wait: (i: number) => number,
  run: (i: number) => void,
) {
  const i = useRef(0)
  const fns = useRef({ wait, run })
  useEffect(() => {
    fns.current = { wait, run }
  })
  useEffect(() => {
    if (!on) return
    let id = 0
    const next = () => {
      id = window.setTimeout(() => {
        const at = i.current
        i.current = (at + 1) % count
        fns.current.run(at)
        next()
      }, fns.current.wait(i.current))
    }
    next()
    return () => clearTimeout(id)
  }, [on, count])
}

/** Position of `target`'s centre (plus an offset in its own size) inside `frame`. */
export function pointIn(frame: Element, target: Element, fx = 0.5, fy = 0.5) {
  const f = frame.getBoundingClientRect()
  const t = target.getBoundingClientRect()
  return { x: t.left - f.left + t.width * fx, y: t.top - f.top + t.height * fy }
}

/** m:ss */
export const clock = (s: number) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`
