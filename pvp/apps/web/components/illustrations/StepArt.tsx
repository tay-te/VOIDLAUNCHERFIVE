"use client"

import { useRef, type CSSProperties } from "react"

import { Mark } from "@/components/Mark"

import { CellMeter, Chevron, Frame, PlayMark, cx, kit, u } from "./launcher/Kit"
import s from "./launcher/steps.module.css"
import { useOnce } from "./launcher/useOnce"

/**
 * Get started — three small 4:3 crops for board 37's three steps (406:5). Each
 * plays once when it enters view (three together stagger 90ms apart), then rests
 * on its end state; hovering replays it. The server renders the end state.
 */

type P = { className?: string }

/* ----------------------------------------------------------- 01 Download */

const DL_CELLS = 20
const DL_MB = 48
const DL_CUES = Array.from({ length: DL_CELLS }, (_, i) => [180 + i * 55, i + 1] as const)

export function StepDownload({ className }: P) {
  const ref = useRef<HTMLDivElement>(null)
  const { state: n, replay } = useOnce(ref, 0, DL_CELLS, DL_CUES)
  const done = n === DL_CELLS

  return (
    <Frame
      units={400}
      frameRef={ref}
      className={className}
      role="img"
      aria-label={`The VOID installer, one ${DL_MB} MB download.`}
      onPointerEnter={replay}
    >
      <div className={s.center} aria-hidden="true">
        <div className={s.dlHead}>
          <span className={s.appIcon}>
            <Mark />
          </span>
          <span className={s.dlText}>
            <span className={s.dlTitle}>VOID</span>
            <span className={cx(s.dlSub, kit.tnum)}>
              {done ? `${DL_MB} MB` : `${((DL_MB * n) / DL_CELLS).toFixed(1)} of ${DL_MB} MB`}
            </span>
          </span>
        </div>

        <CellMeter
          className={s.dlMeter}
          cells={DL_CELLS}
          filled={n}
          live={n > 0 ? n - 1 : null}
          size={u(11)}
          gap={u(4.8)}
        />

        <div className={cx(kit.label, s.dlFoot)}>
          <span>{done ? "Ready" : "Downloading"}</span>
          <span className={kit.tnum}>{Math.round((n / DL_CELLS) * 100)}%</span>
        </div>
      </div>
    </Frame>
  )
}

/* ------------------------------------------------------------ 02 Sign in */

const SI_CUES = [
  [160, 1],
  [400, 2],
  [820, 3],
] as const

export function StepSignIn({ className }: P) {
  const ref = useRef<HTMLDivElement>(null)
  const { state, replay } = useOnce(ref, 0, 3, SI_CUES)

  return (
    <Frame
      units={400}
      frameRef={ref}
      className={className}
      role="img"
      aria-label="Signing in with the Microsoft account you already play Minecraft with."
      onPointerEnter={replay}
    >
      <div className={s.center} aria-hidden="true">
        {/* A neutral four-cell glyph — deliberately not Microsoft's mark. */}
        <div className={s.msButton} data-down={state === 1}>
          <span className={s.four}>
            <i />
            <i />
            <i />
            <i />
          </span>
          Sign in with Microsoft
        </div>

        <div className={s.account} data-in={state >= 2} data-chosen={state === 3}>
          <span className={s.avatar}>KE</span>
          <span className={s.who}>
            <span className={s.whoName}>Kestrel</span>
            <span className={cx(kit.label, s.whoKind)}>Microsoft account</span>
          </span>
          <i className={s.pick} />
        </div>
      </div>
    </Frame>
  )
}

/* ------------------------------------------------------------- 03 Launch */

type L =
  | { k: "idle" }
  | { k: "pressed" }
  | { k: "working"; p: number }
  | { k: "launching" }
  | { k: "running" }

const LA_STEPS = 12
const LA_CUES: (readonly [number, L])[] = [
  [260, { k: "pressed" }],
  [380, { k: "working", p: 0 }],
  ...Array.from({ length: LA_STEPS }, (_, i) => [480 + i * 105, { k: "working", p: (i + 1) / LA_STEPS }] as const),
  [1820, { k: "launching" }],
  [2320, { k: "running" }],
]
const LA_START: L = { k: "idle" }
const LA_END: L = { k: "running" }

export function StepLaunch({ className }: P) {
  const ref = useRef<HTMLDivElement>(null)
  const { state, replay } = useOnce<L>(ref, LA_START, LA_END, LA_CUES)

  const p = state.k === "working" ? state.p : state.k === "launching" ? 1 : 0
  const btnState =
    state.k === "pressed" ? "pressed"
    : state.k === "working" || state.k === "launching" ? "working"
    : state.k === "running" ? "running"
    : "idle"

  return (
    <Frame
      units={400}
      frameRef={ref}
      className={className}
      role="img"
      aria-label="The launcher's Launch button, pressed with Enter: assets download and the game starts."
      onPointerEnter={replay}
    >
      <div className={s.dock} aria-hidden="true">
        <span className={s.dockPicker}>
          <span className={s.dockPickerText}>
            <span className={cx(kit.label, s.dockCaption)}>Loadout</span>
            <span className={s.dockValue}>Sword PvP</span>
          </span>
          <Chevron size={u(12)} />
        </span>

        <span className={s.dockRule}>
          <i />
          <i />
          <i />
          <i />
        </span>

        <span
          className={cx(kit.launch, s.launchAt)}
          data-state={btnState}
          style={{ "--p": p } as CSSProperties}
        >
          {btnState === "working" ? (
            <>
              <span>{state.k === "launching" ? "Launching…" : "Downloading assets"}</span>
              {state.k === "working" ? (
                <span className={cx(s.launchSub, kit.tnum)}>{Math.round(p * 100)}% · 24.0 MB/s</span>
              ) : null}
            </>
          ) : btnState === "running" ? (
            <span>Playing</span>
          ) : (
            <>
              <PlayMark />
              <span>Launch</span>
              <span className={cx(kit.kbd, s.launchKbd)} data-down={btnState === "pressed"}>
                Enter
              </span>
            </>
          )}
        </span>
      </div>
    </Frame>
  )
}
