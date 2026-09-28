"use client"

import { useCallback, useMemo, useRef, useState } from "react"

import { Armour, Crosshair, El, Fps, Keys, place, Potions, type Piece, type Potion } from "./game/Hud"
import { Cursor, mod, Panel, Tile } from "./game/Menu"
import { Plate } from "./game/Plate"
import { cx, pointIn, useActive, useSteps, useStill, useTick } from "./game/live"
import s from "./game/game.module.css"

/**
 * CHAPTER 01 — "It opens over the game" (4:3).
 *
 * The menu is open over a match drawn in cells. Its five tiles are real
 * switches: each one puts its HUD element on the game behind the scrim, or
 * takes it off, the moment it is pressed — "changes apply immediately", shown
 * rather than said. Until someone touches it, a pointer works one tile every
 * few seconds; hovering pauses it, and the first real press ends it.
 */

const IDS = ["fps", "keys", "armour", "potions", "cross"] as const
type Id = (typeof IDS)[number]
const TILES = IDS.map(mod)

/* The demo: off then back on, one tile at a time. Each is a move then a click. */
const ORDER: Id[] = ["cross", "cross", "keys", "keys", "armour", "armour", "potions", "potions", "fps", "fps"]

const ARMOUR: Piece[] = [
  ["Helm", 219, 363],
  ["Chest", 404, 528],
  ["Legs", 187, 495],
  ["Boots", 333, 429],
]
const potions = (e: number): Potion[] => [
  ["Speed II", "#7aebb5", 84 - (e % 40)],
  ["Strength", "#d9a93a", 42 - (e % 40)],
]
/* Movement under the scrim: the match does not stop because the menu is up. */
const WALK = [1, 1, 9, 9, 1, 3, 3, 1, 0, 17, 1, 1, 5, 4, 1, 25]

export function OverlayChapter({ className = "" }: { className?: string }) {
  const root = useRef<HTMLDivElement>(null)
  const screen = useRef<HTMLDivElement>(null)
  const switches = useRef<Partial<Record<Id, HTMLSpanElement | null>>>({})
  const still = useStill()
  const active = useActive(root)

  const [on, setOn] = useState<Record<Id, boolean>>({
    fps: true,
    keys: true,
    armour: true,
    potions: true,
    cross: true,
  })
  const [user, setUser] = useState(false)
  const [hover, setHover] = useState(false)
  const [cur, setCur] = useState<{ x: number; y: number } | null>(null)
  const [hot, setHot] = useState<Id | null>(null)
  const [n, setN] = useState(0)
  const refs = useMemo(
    () =>
      Object.fromEntries(
        IDS.map((id) => [id, (el: HTMLSpanElement | null) => void (switches.current[id] = el)]),
      ) as Record<Id, (el: HTMLSpanElement | null) => void>,
    [],
  )

  const live = active && !still
  const demo = live && !user && !hover

  const toggle = useCallback((id: string) => {
    setOn((o) => ({ ...o, [id]: !o[id as Id] }))
  }, [])

  useSteps(
    demo,
    ORDER.length * 2,
    (i) => (i % 2 ? 950 : i === 0 ? 1600 : 1500),
    (i) => {
      const id = ORDER[i >> 1]
      const f = screen.current
      const el = switches.current[id]
      if (i % 2 === 0) {
        if (f && el) setCur(pointIn(f, el, 0.72, 0.55))
        setHot(id)
      } else toggle(id)
    },
  )

  useTick(live, 400, () => setN((v) => v + 1))

  const onTile = useCallback(
    (id: string) => {
      setUser(true)
      setCur(null)
      setHot(null)
      toggle(id)
    },
    [toggle],
  )

  const count = IDS.filter((id) => on[id]).length
  return (
    <div
      ref={root}
      role="group"
      aria-label="The VOID mods menu open over a match. Each tile switches its HUD element on the game behind the menu, immediately."
      className={cx(s.frame, s.chapter, className)}
      onPointerEnter={(e) => {
        if (e.pointerType === "mouse") {
          setHover(true)
          setCur(null)
          setHot(null)
        }
      }}
      onPointerLeave={() => setHover(false)}
      onFocus={() => {
        setHover(true)
        setCur(null)
        setHot(null)
      }}
      onBlur={() => setHover(false)}
    >
      <div ref={screen} className={s.screen}>
        <Plate live={live} paused={!active} />

        <div className={s.hud} aria-hidden="true">
          <El on={on.fps} style={place("tl")}>
            <Fps fps={143 + ((n * 7) % 9)} low={131} />
          </El>
          <El on={on.potions} style={place("tr")}>
            <Potions rows={potions(Math.floor(n / 2.5))} />
          </El>
          <El on={on.keys} className={s.oKeys}>
            <Keys down={live ? WALK[n % WALK.length] : 1} lmb={live ? 6 + (n % 3) : 7} />
          </El>
          <El on={on.armour} className={s.oArm}>
            <Armour pieces={ARMOUR} />
          </El>
          <El on={on.cross} className={s.oCross}>
            <Crosshair />
          </El>
        </div>

        <div className={s.layer}>
          <div className={s.dim} />
          <Panel className={s.oPanel} enabled={count + 4} lean>
            {TILES.map((m) => (
              <Tile
                key={m[0]}
                m={m}
                on={on[m[0] as Id]}
                hot={hot === m[0]}
                onToggle={onTile}
                switchRef={refs[m[0] as Id]}
              />
            ))}
          </Panel>
          {cur ? <Cursor x={cur.x} y={cur.y} /> : null}
        </div>
      </div>
    </div>
  )
}
