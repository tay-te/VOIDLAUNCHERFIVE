"use client"

import { useEffect, useRef, useState } from "react"

/**
 * §5, the reactive rule: monochrome at rest, the hue only where the pointer
 * is. Two layers share one tile mask so the geometry is identical — ink at 3%
 * at rest, the hue at 22% under the pointer.
 *
 * The cell layer is static and only its mask position and opacity change,
 * which is the cheap form the contract asks for: never a per-cell colour
 * animation, never a blur. Warms in over 140ms, drains over 200ms.
 *
 * The radial in `field-warm` is a *mask*, not a painted gradient — it is the
 * distance falloff §5 specifies, and every visible pixel stays a flat cell.
 *
 * The field paints behind the section's content, so the pointer is tracked on
 * the parent section rather than on the field itself. That keeps the section
 * a server component: drop `<CellField />` inside any `relative` element and
 * it wires itself up.
 */
export function CellField() {
  const ref = useRef<HTMLDivElement>(null)
  const [live, setLive] = useState(false)
  const [enabled, setEnabled] = useState(false)

  useEffect(() => {
    if (
      !window.matchMedia("(hover: hover)").matches ||
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ) {
      return
    }
    setEnabled(true)

    const field = ref.current
    const section = field?.parentElement
    if (!field || !section) return

    let frame = 0

    const onMove = (event: PointerEvent) => {
      if (frame) return
      const { clientX, clientY } = event
      frame = requestAnimationFrame(() => {
        frame = 0
        const box = field.getBoundingClientRect()
        field.style.setProperty("--px", `${clientX - box.left}px`)
        field.style.setProperty("--py", `${clientY - box.top}px`)
        setLive(true)
      })
    }

    const onLeave = () => {
      if (frame) cancelAnimationFrame(frame)
      frame = 0
      setLive(false)
    }

    section.addEventListener("pointermove", onMove)
    section.addEventListener("pointerleave", onLeave)
    return () => {
      if (frame) cancelAnimationFrame(frame)
      section.removeEventListener("pointermove", onMove)
      section.removeEventListener("pointerleave", onLeave)
    }
  }, [])

  return (
    <div
      ref={ref}
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 z-0 overflow-hidden"
    >
      <div className="field-tile absolute inset-0 bg-ink opacity-[0.03]" />
      {enabled ? (
        <div
          className={`field-warm absolute inset-0 bg-hue transition-opacity ${
            live
              ? "opacity-[0.22] duration-[140ms] ease-out"
              : "opacity-0 duration-[200ms] ease-in"
          }`}
        />
      ) : null}
    </div>
  )
}
