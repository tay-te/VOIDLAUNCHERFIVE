"use client"

import { useCallback, useEffect, useRef, useState } from "react"

import {
  Armour,
  Cps,
  Crosshair,
  El,
  Fps,
  Keys,
  Ping,
  Potions,
  Readout,
  type Piece,
  type Potion,
} from "./game/Hud"
import { Cursor, MODS, Panel, Tile } from "./game/Menu"
import { cx, pointIn, useActive, useStill, useTick } from "./game/live"
import s from "./game/game.module.css"

/**
 * HERO — the VOID overlay over a live match (16:9).
 *
 * A loop cut from the PvP short (source 23.21–32.29s, rotated to open on the
 * sword clash at 24.88s, so the file's wrap is a continuous frame and the only
 * hard cut is one the film already had), with the real HUD drawn live on top.
 * Keystrokes and CPS follow the fight from a cue list read against
 * `video.currentTime`. Every other loop the menu opens over the match, the
 * pointer switches Armor status, and the menu closes on the change.
 *
 * Click the game or press the R-SHIFT key to drive it yourself; that ends the
 * demo. SSR, no-JS, reduced motion and Save-Data all get the poster and a
 * complete static HUD.
 */

const DUR = 218 / 24 // the loop, in seconds

/* Held keys: [bit, from, to]. W 1, A 2, S 4, D 8, RMB 32. */
const HOLDS: readonly (readonly [number, number, number])[] = [
  [1, 0, 0.62], [1, 0.98, 3.4], [1, 3.75, 7.3], [1, 7.6, DUR],
  [2, 0.9, 1.4], [2, 4.2, 4.65], [2, 7.45, 7.8],
  [8, 2.2, 2.85], [8, 5.6, 6.15], [8, 8.4, 8.85],
  [4, 3.42, 3.72],
  [32, 2.4, 2.75], [32, 6.0, 6.25],
]

/* Left clicks, on the swings you can see. */
const CLICKS = [
  0.04, 0.17, 0.3, 0.43, 0.56, 0.7, 1.5, 1.62, 1.77, 1.93, 2.07, 3.14, 3.28, 3.8, 3.93, 4.06,
  4.66, 4.8, 5.34, 5.48, 6.3, 6.44, 6.96, 7.09, 7.21, 7.34, 7.8, 7.93, 8.08, 8.2, 8.33, 8.49,
  8.62, 8.79, 8.94,
]
const TAP = 0.07

/* The menu script, in loop seconds, on even loops, during the wide shot. */
const OPEN = 1.25
const MOVE = 1.55
const HOVER = 2.45
const CLICK = 2.7
const SHUT = 3.9

const EDGES = [
  ...new Set([
    ...HOLDS.flatMap(([, a, b]) => [a, b]),
    ...CLICKS.flatMap((c) => [c, c + TAP]),
    OPEN,
    MOVE,
    HOVER,
    CLICK,
    SHUT,
  ]),
].sort((a, b) => a - b)

const keysAt = (t: number) => {
  let k = 0
  for (const [bit, a, b] of HOLDS) if (t >= a && t < b) k |= bit
  if (CLICKS.some((c) => t >= c && t < c + TAP)) k |= 16
  return k
}
const cpsAt = (t: number) => CLICKS.filter((c) => c <= t && c > t - 1).length

const POTIONS = (e: number): Potion[] => [
  ["Speed II", "#7aebb5", 84 - (e % 30)],
  ["Strength", "#d9a93a", 42 - (e % 30)],
]
const ARMOUR: Piece[] = [
  ["Helm", 219, 363],
  ["Chest", 404, 528],
  ["Legs", 187, 495],
  ["Boots", 333, 429],
]

const START = new Set(["fps", "ping", "keys", "armour", "cps", "potions", "zoom", "sprint"])
const TARGET = "armour"

export function HeroMatch({ className = "" }: { className?: string }) {
  const root = useRef<HTMLDivElement>(null)
  const screen = useRef<HTMLDivElement>(null)
  const video = useRef<HTMLVideoElement>(null)
  const target = useRef<HTMLSpanElement>(null)
  const still = useStill()
  const active = useActive(root)

  const [on, setOn] = useState(START)
  const [open, setOpen] = useState(false)
  const [manual, setManual] = useState(false)
  const [playing, setPlaying] = useState(false)
  /* The still frame is the poster's: W held, mid-swing. */
  const [keys, setKeys] = useState(17)
  const [cps, setCps] = useState(7)
  const [vitals, setVitals] = useState({ fps: 144, low: 130, ping: 42, e: 0 })
  const [cur, setCur] = useState<{ x: number; y: number; hidden: boolean } | null>(null)
  const [hot, setHot] = useState(false)
  const [rsh, setRsh] = useState(false)

  const openRef = useRef(open)
  const manualRef = useRef(manual)
  const activeRef = useRef(active)
  useEffect(() => {
    openRef.current = open
    manualRef.current = manual
    activeRef.current = active
  }, [open, manual, active])

  const toggle = useCallback((id: string) => {
    setOn((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      return next
    })
  }, [])

  const flash = () => {
    setRsh(true)
    window.setTimeout(() => setRsh(false), 160)
  }

  /* Play only while on screen and the tab is visible. */
  useEffect(() => {
    const v = video.current
    if (!v) return
    if (active) v.play().catch(() => setPlaying(false))
    else v.pause()
  }, [active, still])

  /* The cue scheduler: every 250ms, look 300ms ahead and set a timeout for
     each key edge / script step in the window. State is a pure function of
     the loop time, so a step scheduled twice is harmless. */
  const loop = useRef(0)
  const last = useRef(0)
  const pending = useRef<number[]>([])
  const clicked = useRef(-1)
  const run = useCallback((t: number) => {
    const menu = openRef.current
    setKeys(menu ? 0 : keysAt(t))
    setCps(menu ? 0 : cpsAt(t))
    if (manualRef.current || loop.current % 2) return
    const f = screen.current
    if (t === OPEN) {
      flash()
      setOpen(true)
      if (f) setCur({ x: f.clientWidth / 2, y: f.clientHeight / 2, hidden: false })
    } else if (t === MOVE && f && target.current) {
      const p = pointIn(f, target.current, 0.72, 0.55)
      setCur({ x: p.x, y: p.y, hidden: false })
    } else if (t === HOVER) setHot(true)
    else if (t === CLICK && clicked.current !== loop.current) {
      clicked.current = loop.current
      toggle(TARGET)
    }
    else if (t === SHUT) {
      flash()
      setOpen(false)
      setHot(false)
      setCur(null)
    }
  }, [toggle])

  const ticks = useRef(0)
  useTick(active && playing, 250, () => {
    const v = video.current
    if (!v) return
    const t = v.currentTime
    if (t + 1 < last.current) loop.current++
    last.current = t
    for (const id of pending.current) clearTimeout(id)
    pending.current = []
    for (const e of EDGES) {
      let dt = e - t
      if (dt < -DUR + 0.3) dt += DUR
      if (dt >= 0 && dt < 0.3) {
        pending.current.push(window.setTimeout(() => run(e), (dt * 1000) / (v.playbackRate || 1)))
      }
    }
    if (++ticks.current % 4 === 0) {
      setVitals((p) => ({
        fps: 139 + Math.round(Math.random() * 12),
        low: 128 + Math.round(Math.random() * 4),
        ping: Math.max(38, Math.min(47, p.ping + Math.round(Math.random() * 4 - 2))),
        e: p.e + 1,
      }))
    }
  })

  /* Paused (or gone): drop every scheduled step. */
  useEffect(() => {
    const drop = () => {
      for (const id of pending.current) clearTimeout(id)
      pending.current = []
    }
    if (!(active && playing)) drop()
    return drop
  }, [active, playing])

  const takeOver = (next: boolean) => {
    setManual(true)
    setCur(null)
    setHot(false)
    setOpen(next)
    setKeys(0)
    setCps(0)
    flash()
  }

  const onTile = useCallback(
    (id: string) => {
      setManual(true)
      setCur(null)
      setHot(false)
      toggle(id)
    },
    [toggle],
  )

  const enabled = on.size
  return (
    <div
      ref={root}
      role="group"
      aria-label="A PvP match in VOID with the HUD drawn live over it. Click the game or press R-SHIFT to open the mods menu over the match."
      className={cx(s.frame, s.hero, className)}
      onKeyDown={(e) => {
        if (e.key === "Escape" && open) takeOver(false)
      }}
    >
      <div
        ref={screen}
        className={cx(s.screen, s.clickable)}
        onClick={(e) => {
          if ((e.target as Element).closest(`.${s.panel}`)) return
          takeOver(!open)
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/media/hero-match-poster.webp"
          alt=""
          width={1600}
          height={900}
          fetchPriority="high"
          decoding="async"
          className={s.media}
        />
        {!still ? (
          <video
            ref={video}
            className={s.media}
            muted
            playsInline
            loop
            autoPlay
            preload="metadata"
            poster="/media/hero-match-poster.webp"
            aria-hidden="true"
            tabIndex={-1}
            disablePictureInPicture
            onPlaying={() => {
              if (activeRef.current) setPlaying(true)
              else video.current?.pause()
            }}
            onPause={() => setPlaying(false)}
          >
            <source src="/media/hero-match.webm" type='video/webm; codecs="vp9"' />
            <source src="/media/hero-match.mp4" type="video/mp4" />
          </video>
        ) : null}

        <div className={s.hud} aria-hidden="true">
          <El on={on.has("fps")} className={s.hFps}>
            <Fps fps={vitals.fps} low={vitals.low} />
          </El>
          <El on={on.has("ping")} className={s.hPing}>
            <Ping ms={vitals.ping} />
          </El>
          <El on={on.has("combo")} className={s.hCombo}>
            <Readout value={cps > 3 ? 4 : 2} unit="combo" />
          </El>
          <El on={on.has("memory")} className={s.hMem}>
            <Readout value={1462} unit="MB" />
          </El>
          <El on={on.has("cross")} className={s.hCross}>
            <Crosshair />
          </El>
          <El on={on.has("potions")} className={s.hPot}>
            <Potions rows={POTIONS(vitals.e)} />
          </El>
          <El on={on.has("armour")} className={s.hArm}>
            <Armour pieces={ARMOUR} />
          </El>
          <El on={on.has("keys")} className={s.hKeys}>
            <Keys down={keys} lmb={cps} rmb={0} />
          </El>
          <El on={on.has("cps")} className={s.hCps}>
            <Cps cps={cps} />
          </El>
        </div>

        <button
          type="button"
          className={s.rshift}
          aria-label={open ? "Close the mods menu (R-SHIFT)" : "Open the mods menu (R-SHIFT)"}
          aria-expanded={open}
          data-down={rsh ? "" : undefined}
          onClick={(e) => {
            e.stopPropagation()
            takeOver(!open)
          }}
        >
          R-SHIFT
        </button>

        <div className={cx(s.layer, !open && s.shut)} inert={!open}>
          <div className={s.dim} />
          <Panel
            className={s.hPanel}
            enabled={enabled}
            onClose={() => takeOver(false)}
          >
            {MODS.map((m) => (
              <Tile
                key={m[0]}
                m={m}
                on={on.has(m[0])}
                hot={hot && m[0] === TARGET}
                switchRef={m[0] === TARGET ? target : undefined}
                onToggle={onTile}
              />
            ))}
          </Panel>
          {cur ? <Cursor x={cur.x} y={cur.y} hidden={cur.hidden} /> : null}
        </div>
      </div>
    </div>
  )
}
