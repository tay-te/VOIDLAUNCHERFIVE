"use client"

import { useEffect, useId, useRef, useState, type CSSProperties, type KeyboardEvent } from "react"

import { LOADOUTS, MOD_TOTAL, TILE_COLS, TILE_MODS, modMeta } from "./launcher/data"
import { Glyph } from "./launcher/glyphs"
import { CellMeter, Chevron, Frame, HUES, Toggle, cx, kit, u } from "./launcher/Kit"
import s from "./launcher/loadout.module.css"
import { T, useActive, useReducedMotion } from "./launcher/timing"

/**
 * Chapter 03 — "A loadout per game mode". A crop of the launcher: the active
 * loadout's name, the loadout menu open above the dock, a 4 × 2 window onto the Mods
 * grid and the dock's "N of 29 mods enabled" readout.
 *
 * Choosing a loadout — click, tap, or the arrow keys in the open list — re-configures
 * the mods: the tiles flip on a 40ms left-to-right stagger, colour flooding into the
 * ones that come on and draining out of the ones that go off, while the readout walks
 * to its new count and the name swaps. Idle, it cycles the loadouts every few seconds
 * until the first interaction. Offscreen, in a hidden tab, or under reduced motion it
 * does nothing on its own.
 *
 * Board refs: 05 Loadout (316:17), 30 Loadout switch (398:2); launcher Mods (289:1141).
 */

const IDLE_MS = 3400 // one loadout on screen
const PROBE_MS = 520 // the "pointer" rests on the next row before choosing it

export function LoadoutChapter({ className }: { className?: string }) {
  const frame = useRef<HTMLDivElement>(null)
  const active = useActive(frame)
  const reduced = useReducedMotion()
  const id = useId()

  const [sel, setSel] = useState(0)
  const [probeNext, setProbeNext] = useState(false)
  const [count, setCount] = useState(LOADOUTS[0].mods.length)
  const [swapped, setSwapped] = useState(false)
  const [touched, setTouched] = useState(false)

  const loadout = LOADOUTS[sel]
  const on = new Set<string>(loadout.mods)

  const choose = (i: number) => {
    setProbeNext(false)
    if (i === sel) return
    setSel(i)
    setSwapped(true)
  }

  /* The readout walks to the new count, one cell per 40ms stagger step. */
  useEffect(() => {
    const target = LOADOUTS[sel].mods.length
    if (reduced) {
      setCount(target)
      return
    }
    const timer = setInterval(() => {
      setCount((c) => {
        if (c === target) {
          clearInterval(timer)
          return c
        }
        return c + Math.sign(target - c)
      })
    }, T.stagger)
    return () => clearInterval(timer)
  }, [sel, reduced])

  /* Idle: the pointer comes to rest on the next loadout, then chooses it. */
  useEffect(() => {
    if (!active || touched || reduced) return
    const timers: ReturnType<typeof setTimeout>[] = []
    const cycle = () => {
      timers.push(
        setTimeout(() => setProbeNext(true), IDLE_MS - PROBE_MS),
        setTimeout(() => {
          setSel((prev) => (prev + 1) % LOADOUTS.length)
          setSwapped(true)
          setProbeNext(false)
          cycle()
        }, IDLE_MS),
      )
    }
    cycle()
    return () => {
      timers.forEach(clearTimeout)
      setProbeNext(false)
    }
  }, [active, touched, reduced])

  const probed = probeNext ? (sel + 1) % LOADOUTS.length : null

  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const last = LOADOUTS.length - 1
    const next =
      e.key === "ArrowDown" ? Math.min(last, sel + 1)
      : e.key === "ArrowUp" ? Math.max(0, sel - 1)
      : e.key === "Home" ? 0
      : e.key === "End" ? last
      : null
    if (next === null) return
    e.preventDefault()
    setTouched(true)
    choose(next)
  }

  return (
    <Frame
      units={1000}
      frameRef={frame}
      className={className}
      role="group"
      aria-label="The VOID launcher with its loadout menu open. Choosing a loadout switches which mods are enabled."
    >
      <div className={s.panel} aria-hidden="true" />

      {/* The Play screen's name block. */}
      <div className={s.head} aria-hidden="true">
        <p className={cx(kit.label, s.eyebrow)}>Active loadout</p>
        <span key={loadout.id} className={cx(s.name, swapped && s.swap)}>
          {loadout.name}
        </span>
        <p key={`${loadout.id}-m`} className={cx(s.meta, kit.tnum, swapped && s.swap)}>
          {loadout.mods.length} mods on <b>·</b> {loadout.fps} fps avg
        </p>
      </div>

      {/* A 4 × 2 window onto the Mods grid. Each tile's delay is its column plus its
          row, 40ms a step, so the change sweeps left to right. */}
      <div className={s.tiles} aria-hidden="true">
        {TILE_MODS.map((mod, i) => {
          const meta = modMeta(mod)
          const isOn = on.has(mod)
          const delay = reduced ? 0 : ((i % TILE_COLS) + Math.floor(i / TILE_COLS)) * T.stagger
          return (
            <div
              key={mod}
              className={s.tile}
              data-on={isOn}
              style={{ "--hue": HUES[meta.cat], "--d": `${delay}ms` } as CSSProperties}
            >
              <div className={s.well}>
                <div className={s.art}>
                  <Glyph id={mod} on={isOn} />
                </div>
              </div>
              <div className={s.foot}>
                <span className={s.tileText}>
                  <span className={s.tileName}>{meta.label}</span>
                  <span className={cx(kit.label, s.tileCat)}>{meta.cat}</span>
                </span>
                <Toggle on={isOn} delay={delay} />
              </div>
            </div>
          )
        })}
      </div>

      {/* The loadout menu, open above its picker — the one live control here. */}
      <div className={s.menu}>
        <p className={cx(kit.label, s.menuHead)} id={`${id}-h`} aria-hidden="true">
          Switch loadout
        </p>
        <div
          role="listbox"
          tabIndex={0}
          aria-label="Loadout"
          aria-activedescendant={`${id}-o${sel}`}
          className={s.list}
          onKeyDown={onKey}
          onPointerDown={() => setTouched(true)}
        >
          {LOADOUTS.map((l, i) => (
            <div
              key={l.id}
              id={`${id}-o${i}`}
              role="option"
              aria-selected={i === sel}
              data-probe={probed === i && i !== sel}
              className={s.opt}
              onClick={() => {
                setTouched(true)
                choose(i)
              }}
            >
              <i className={s.dot} aria-hidden="true" />
              <span className={s.optName}>{l.name}</span>
              <span className={cx(kit.label, kit.tnum, s.optCount)}>{l.mods.length} mods</span>
            </div>
          ))}
        </div>
      </div>

      {/* The dock band. */}
      <div className={s.dock} aria-hidden="true">
        <span className={cx(kit.picker, s.loadoutPicker)} data-open="true">
          <span key={loadout.id} className={swapped ? s.swap : undefined}>
            {loadout.name}
          </span>
          <Chevron />
        </span>
        <span className={cx(kit.picker, s.versionPicker)}>
          1.8.9
          <Chevron />
        </span>
        <span className={s.readout}>
          <span className={cx(kit.label, kit.tnum, s.readoutLabel)}>
            {count} of {MOD_TOTAL} mods enabled
          </span>
          <CellMeter cells={MOD_TOTAL} filled={count} size={u(12)} gap={u(5)} />
        </span>
      </div>

      <p className={cx(kit.label, s.hint)} aria-hidden="true">
        Changes save to the active loadout
      </p>

      <p className={kit.srOnly} aria-live="polite">
        {touched ? `${loadout.name}: ${loadout.mods.length} of ${MOD_TOTAL} mods enabled` : ""}
      </p>
    </Frame>
  )
}
