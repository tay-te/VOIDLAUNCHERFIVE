"use client"

import { useCallback, useLayoutEffect, useRef, useState, type ReactNode } from "react"

import { Armour, Fps, Keys, Ping, place, type Anchor, type Piece } from "./game/Hud"
import { Cursor, Icon } from "./game/Menu"
import { Plate } from "./game/Plate"
import { cx, pointIn, useActive, useSteps, useStill, useTick } from "./game/live"
import s from "./game/game.module.css"

/**
 * CHAPTER 02 — "Put the HUD anywhere" (4:3).
 *
 * The HUD editor over a match drawn in cells. Every widget can be dragged; it
 * snaps to the nearest of nine anchors, with its handles and the anchors in the
 * hue while it is in the hand (§1: a drag in progress is a live value), and
 * settles over §5's 200ms drain. Arrow keys move a focused widget one anchor.
 *
 * While idle the pointer carries Keystrokes to the bottom centre and back, and
 * two live values cross their thresholds and come back — ping past 60 ms, the
 * leggings under half — so the only other colour in the piece is a value.
 */

const IDS = ["fps", "ping", "keys", "armour"] as const
type Id = (typeof IDS)[number]
const NAME: Record<Id, string> = {
  fps: "FPS display",
  ping: "Ping",
  keys: "Keystrokes",
  armour: "Armor status",
}
const START: Record<Id, Anchor> = { fps: "tl", ping: "tr", keys: "bl", armour: "br" }

const INSET = 24
const UNDER_BAR = 78 // the top-centre anchor sits below the toolbar
const GRID: Anchor[][] = [
  ["tl", "t", "tr"],
  ["l", "c", "r"],
  ["bl", "b", "br"],
]
const WORDS: Record<Anchor, string> = {
  tl: "top left",
  t: "top centre",
  tr: "top right",
  l: "middle left",
  c: "centre",
  r: "middle right",
  bl: "bottom left",
  b: "bottom centre",
  br: "bottom right",
}

const axes = (a: Anchor) => ({
  h: a.includes("l") ? 0 : a.includes("r") ? 2 : 1,
  v: a.includes("t") ? 0 : a.includes("b") ? 2 : 1,
})

const anchorStyle = (a: Anchor) => {
  const { h, v } = axes(a)
  return place(a, h === 1 ? 0 : INSET, v === 1 ? 0 : a === "t" ? UNDER_BAR : INSET)
}

/** Where a widget of size w × h would have its centre at anchor `a`. */
function centreAt(a: Anchor, W: number, H: number, w: number, h: number, u: number) {
  const { h: x, v: y } = axes(a)
  const top = (a === "t" ? UNDER_BAR : INSET) * u
  return {
    x: x === 0 ? INSET * u + w / 2 : x === 2 ? W - INSET * u - w / 2 : W / 2,
    y: y === 0 ? top + h / 2 : y === 2 ? H - INSET * u - h / 2 : H / 2,
  }
}

/* One idle cycle is 32 ticks of 600ms. Ping climbs through 60 ms and the
   leggings drop under half, then both come back. */
const pingAt = (p: number) =>
  p < 6 ? 42 + (p % 2) : p < 12 ? 42 + (p - 5) * 11 : p < 16 ? 104 + (p % 3) * 3 : p < 22 ? 108 - (p - 15) * 11 : 43 - (p % 2)
const legsAt = (p: number) =>
  p < 10 ? 0.62 : p < 14 ? 0.62 - (p - 9) * 0.065 : p < 22 ? 0.36 + (p % 2) * 0.01 : p < 26 ? 0.36 + (p - 21) * 0.065 : 0.62

type Drag = { id: Id; near: Anchor; drain: boolean; below?: boolean }

export function HudChapter({ className = "" }: { className?: string }) {
  const root = useRef<HTMLDivElement>(null)
  const screen = useRef<HTMLDivElement>(null)
  const els = useRef<Partial<Record<Id, HTMLButtonElement | null>>>({})
  const readout = useRef<HTMLSpanElement>(null)
  const still = useStill()
  const active = useActive(root)
  const live = active && !still

  const [pos, setPos] = useState(START)
  const [drag, setDrag] = useState<Drag | null>(null)
  const [user, setUser] = useState(false)
  const [cur, setCur] = useState<{ x: number; y: number } | null>(null)
  const [n, setN] = useState(0)

  const posRef = useRef(pos)
  useLayoutEffect(() => {
    posRef.current = pos
  }, [pos])

  useTick(live, 600, () => setN((v) => v + 1))
  const p = n % 32
  const ping = still ? 42 : pingAt(p)
  const legs = still ? 0.62 : legsAt(p)
  const armour: Piece[] = [
    ["Helm", 219, 363],
    ["Chest", 404, 528],
    ["Legs", Math.round(legs * 495), 495],
    ["Boots", 333, 429],
  ]

  /* ---------------------------------------------------- FLIP on re-anchor */
  const flip = useRef<Map<HTMLElement, DOMRect> | null>(null)
  useLayoutEffect(() => {
    const before = flip.current
    if (!before) return
    flip.current = null
    before.forEach((old, el) => {
      el.classList.remove(s.settle, s.glide)
      el.style.transform = ""
      const now = el.getBoundingClientRect()
      const dx = old.left - now.left
      const dy = old.top - now.top
      if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return
      el.style.transform = `translate(${dx}px, ${dy}px)`
      void el.offsetWidth
      el.classList.add(s.settle)
      el.style.transform = ""
    })
  }, [pos])

  const metrics = () => {
    const f = screen.current!
    const u = parseFloat(getComputedStyle(f).getPropertyValue("--u")) || f.clientWidth / 960
    return { f, u, fr: f.getBoundingClientRect() }
  }

  /* The x · y readout changes on every pointermove, so it is written straight
     to the DOM rather than through React state. */
  const text = useRef("")
  const label = (x: number, y: number, u: number) => {
    text.current = `x ${Math.round(x / u)}  ·  y ${Math.round(y / u)}`
    if (readout.current) readout.current.textContent = text.current
  }
  const readoutRef = useCallback((el: HTMLSpanElement | null) => {
    readout.current = el
    if (el) el.textContent = text.current
  }, [])

  /** A person has the HUD now: end the demo and put back anything it held. */
  const takeOver = () => {
    setUser(true)
    setCur(null)
    for (const el of Object.values(els.current)) {
      if (el?.classList.contains(s.glide)) {
        el.classList.remove(s.glide)
        el.style.transform = ""
      }
    }
  }

  /** Move `id` to `to`, swapping with whoever is there, and drain the hue. */
  const drop = useCallback((id: Id, to: Anchor) => {
    const now = posRef.current
    const other = IDS.find((k) => k !== id && now[k] === to)
    const before = new Map<HTMLElement, DOMRect>()
    for (const k of [id, other]) {
      const el = k && els.current[k]
      if (el) before.set(el, el.getBoundingClientRect())
    }
    flip.current = before
    setPos((p) => ({ ...p, [id]: to, ...(other ? { [other]: p[id] } : {}) }))
    setDrag((d) => (d ? { ...d, near: to, drain: true } : d))
    window.setTimeout(() => setDrag((d) => (d?.drain ? null : d)), 200)
  }, [])

  /* ------------------------------------------------------------ pointer */
  const hand = useRef<{
    id: Id
    x0: number
    y0: number
    cx: number
    cy: number
    w: number
    h: number
    W: number
    H: number
    u: number
    near: Anchor
  } | null>(null)

  const nearest = (x: number, y: number, g: NonNullable<typeof hand.current>) => {
    let best: Anchor = "c"
    let bd = Infinity
    for (const a of GRID.flat()) {
      const c = centreAt(a, g.W, g.H, g.w, g.h, g.u)
      const d = (c.x - x) ** 2 + (c.y - y) ** 2
      if (d < bd) {
        bd = d
        best = a
      }
    }
    return best
  }

  const onDown = (id: Id) => (e: React.PointerEvent<HTMLButtonElement>) => {
    if (e.button !== 0) return
    const el = e.currentTarget
    el.setPointerCapture(e.pointerId)
    takeOver()
    const { u, fr } = metrics()
    el.classList.remove(s.settle, s.glide)
    el.style.transform = ""
    const r = el.getBoundingClientRect()
    hand.current = {
      id,
      x0: e.clientX,
      y0: e.clientY,
      cx: r.left - fr.left + r.width / 2,
      cy: r.top - fr.top + r.height / 2,
      w: r.width,
      h: r.height,
      W: fr.width,
      H: fr.height,
      u,
      near: posRef.current[id],
    }
    label(r.left - fr.left, r.top - fr.top, u)
    setDrag({ id, near: posRef.current[id], drain: false, below: r.top - fr.top < 60 * u })
  }

  const onMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    const g = hand.current
    if (!g) return
    const dx = e.clientX - g.x0
    const dy = e.clientY - g.y0
    e.currentTarget.style.transform = `translate(${dx}px, ${dy}px)`
    const x = g.cx + dx
    const y = g.cy + dy
    label(x - g.w / 2, y - g.h / 2, g.u)
    const near = nearest(x, y, g)
    if (near !== g.near) {
      g.near = near
      setDrag((d) => (d ? { ...d, near } : d))
    }
  }

  const onUp = () => {
    const g = hand.current
    if (!g) return
    hand.current = null
    drop(g.id, g.near)
  }

  const onKey = (id: Id) => (e: React.KeyboardEvent) => {
    const d = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[e.key]
    if (!d) return
    e.preventDefault()
    takeOver()
    const { h, v } = axes(posRef.current[id])
    const to = GRID[Math.max(0, Math.min(2, v + d[1]))][Math.max(0, Math.min(2, h + d[0]))]
    if (to === posRef.current[id]) return
    setDrag({ id, near: to, drain: false })
    drop(id, to)
  }

  /* ---------------------------------------------------------- idle demo */
  const demoTo = useRef<Anchor>("b")
  useSteps(
    live && !user,
    3,
    (i) => (i === 0 ? 2600 : i === 1 ? 450 : 900),
    (i) => {
      const el = els.current.keys
      if (!el || !screen.current) return
      const { f, u, fr } = metrics()
      if (i === 0) {
        demoTo.current = posRef.current.keys === "b" ? "bl" : "b"
        const c = pointIn(f, el, 0.5, 0.42)
        const r = el.getBoundingClientRect()
        label(r.left - fr.left, r.top - fr.top, u)
        setCur(c)
        setDrag({ id: "keys", near: posRef.current.keys, drain: false })
      } else if (i === 1) {
        const r = el.getBoundingClientRect()
        const to = centreAt(demoTo.current, fr.width, fr.height, r.width, r.height, u)
        const dx = to.x - (r.left - fr.left + r.width / 2)
        const dy = to.y - (r.top - fr.top + r.height / 2)
        el.classList.remove(s.settle)
        el.classList.add(s.glide)
        el.style.transform = `translate(${dx}px, ${dy}px)`
        setCur((c) => c && { x: c.x + dx, y: c.y + dy })
        label(to.x - r.width / 2, to.y - r.height / 2, u)
        window.setTimeout(() => setDrag((d) => (d ? { ...d, near: demoTo.current } : d)), 420)
      } else {
        drop("keys", demoTo.current)
        window.setTimeout(() => setCur(null), 400)
      }
    },
  )

  const widget = (id: Id, body: ReactNode) => {
    const held = drag?.id === id
    return (
      <button
        key={id}
        type="button"
        ref={(el) => void (els.current[id] = el)}
        className={cx(s.widget, held && !drag.drain && s.dragging, held && drag.below && s.below)}
        style={anchorStyle(pos[id])}
        aria-label={`${NAME[id]}, ${WORDS[pos[id]]}. Drag, or use the arrow keys, to move it.`}
        onPointerDown={onDown(id)}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerCancel={onUp}
        onKeyDown={onKey(id)}
      >
        <span aria-hidden="true" style={{ display: "block" }}>
          {body}
        </span>
        {held ? (
          <span className={cx(s.selection, drag.drain && s.drain)} aria-hidden="true">
            <i className={s.handle} />
            <i className={s.handle} />
            <i className={s.handle} />
            <i className={s.handle} />
            <span className={s.label}>
              {NAME[id]}
              <span ref={readoutRef} className={s.tnum} />
            </span>
          </span>
        ) : null}
      </button>
    )
  }

  return (
    <div
      ref={root}
      role="group"
      aria-label="The VOID HUD editor over a match. Four widgets, each movable to any of nine anchors."
      className={cx(s.frame, s.chapter, className)}
    >
      <div ref={screen} className={cx(s.screen, s.editor)}>
        <Plate live={live} paused={!active} ground={75} />

        <div className={s.toolbar} aria-hidden="true">
          <span className={cx(s.tool)}>
            <Icon name="move" />
            HUD layout
          </span>
          <span className={cx(s.tool, s.toolOn)}>Snap</span>
          <span className={cx(s.tool, s.hide560)}>Grid</span>
          <span className={cx(s.tool, s.hide560)}>Reset</span>
          <span className={cx(s.tool, s.toolDone)}>
            Done <span className={s.kbd}>ESC</span>
          </span>
        </div>

        <div className={cx(s.anchors, (!drag || drag.drain) && s.anchorsOff)} aria-hidden="true">
          {GRID.flat().map((a) => {
            const { h, v } = axes(a)
            return (
              <i
                key={a}
                className={cx(s.anchor, drag?.near === a && s.anchorNear)}
                style={{
                  left: h === 0 ? `calc(${INSET} * var(--u))` : h === 2 ? `calc(100% - ${INSET} * var(--u))` : "50%",
                  top:
                    v === 0
                      ? `calc(${a === "t" ? UNDER_BAR : INSET} * var(--u))`
                      : v === 2
                        ? `calc(100% - ${INSET} * var(--u))`
                        : "50%",
                }}
              />
            )
          })}
        </div>

        {widget("fps", <Fps fps={still ? 144 : 141 + ((n * 5) % 8)} low={131} />)}
        {widget("ping", <Ping ms={ping} />)}
        {widget("keys", <Keys down={0} lmb={0} rmb={0} />)}
        {widget("armour", <Armour pieces={armour} />)}

        {cur ? <Cursor x={cur.x} y={cur.y} /> : null}
      </div>
    </div>
  )
}
