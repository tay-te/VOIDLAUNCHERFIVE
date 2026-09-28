"use client"

import { useEffect, useRef, useState } from "react"

import { Button, buttonClass } from "./Button"
import { Lockup } from "./Mark"

/**
 * Not on any board — the boards are film frames and have no navigation. Built
 * from the product shell's own navbar vocabulary: lockup left, light nav
 * links, one primary action. Opaque rather than translucent, because §0
 * forbids backdrop blur.
 *
 * Below 1041px the links move into a flat sheet under the bar: the same
 * anchors plus Download, on the shell fill with a hairline, over a flat scrim.
 * Escape, the scrim, or any tap outside the sheet closes it; focus goes to the
 * first link on open, stays inside while it is open, and returns to the toggle
 * on Escape; the page behind does not scroll. With JavaScript off the toggle
 * is not drawn at all (it could not open anything), so nothing dead is shown.
 */
const LINKS = [
  { href: "#overlay", label: "Overlay" },
  { href: "#hud", label: "HUD" },
  { href: "#loadouts", label: "Loadouts" },
  { href: "#performance", label: "Performance" },
  { href: "#get-started", label: "Get started" },
  { href: "#questions", label: "Questions" },
]

export function Header() {
  const [stuck, setStuck] = useState(false)
  const [open, setOpen] = useState(false)
  const toggleRef = useRef<HTMLButtonElement>(null)
  const sheetRef = useRef<HTMLElement>(null)

  useEffect(() => {
    const sync = () => setStuck(window.scrollY > 8)
    sync()
    window.addEventListener("scroll", sync, { passive: true })
    return () => window.removeEventListener("scroll", sync)
  }, [])

  useEffect(() => {
    if (!open) return
    const root = document.documentElement
    root.style.overflow = "hidden"
    sheetRef.current?.querySelector("a")?.focus()

    const focusables = (): HTMLElement[] => {
      const links = [...(sheetRef.current?.querySelectorAll("a") ?? [])]
      return toggleRef.current ? [toggleRef.current, ...links] : links
    }

    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false)
        toggleRef.current?.focus()
        return
      }
      if (event.key !== "Tab") return
      const items = focusables()
      const first = items[0]
      const last = items[items.length - 1]
      if (!first || !last) return
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    // Outside = anything that is neither the sheet nor the toggle, which
    // includes the scrim and the rest of the header bar.
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node
      if (sheetRef.current?.contains(target) || toggleRef.current?.contains(target)) return
      setOpen(false)
    }

    const wide = window.matchMedia("(min-width: 1041px)")
    const onWide = () => wide.matches && setOpen(false)

    document.addEventListener("keydown", onKey)
    document.addEventListener("pointerdown", onPointerDown)
    wide.addEventListener("change", onWide)
    return () => {
      root.style.overflow = ""
      document.removeEventListener("keydown", onKey)
      document.removeEventListener("pointerdown", onPointerDown)
      wide.removeEventListener("change", onWide)
    }
  }, [open])

  // A link in the sheet: unlock the page before the anchor jump, not after.
  const follow = () => {
    document.documentElement.style.overflow = ""
    setOpen(false)
  }

  return (
    <header
      className={`sticky top-0 z-40 border-b bg-shell transition-colors duration-[140ms] ease-out ${
        stuck || open ? "border-divider" : "border-transparent"
      }`}
    >
      <div className="relative z-1 mx-auto flex min-h-20 w-full max-w-[calc(var(--container-content)+var(--spacing-gutter)*2)] items-center gap-[clamp(20px,2.5vw,48px)] px-gutter">
        <a href="#top" aria-label="VOID, home" className="inline-flex min-h-11 items-center">
          <Lockup className="text-[clamp(16px,1.042vw,20px)]" />
        </a>

        <nav
          aria-label="Sections"
          className="ms-[clamp(12px,2vw,40px)] hidden items-center gap-[clamp(16px,1.667vw,32px)] min-[1041px]:flex"
        >
          {LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="whitespace-nowrap py-2 text-link text-ink-2 transition-colors duration-[140ms] ease-out hover:text-ink"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="ms-auto flex items-center gap-2">
          <Button href="#download" scale="sm">
            Download
          </Button>

          <button
            ref={toggleRef}
            type="button"
            aria-label="Menu"
            aria-expanded={open}
            aria-controls="site-menu"
            onClick={() => setOpen((was) => !was)}
            className={`-me-2 inline-flex size-11 cursor-pointer items-center justify-center rounded-nav text-ink transition-colors duration-100 ease-in hover:bg-card hover:duration-[140ms] hover:ease-out min-[1041px]:hidden [html:not(.js)_&]:hidden ${
              open ? "bg-card" : ""
            }`}
          >
            {/* Two bars that cross. Transform only, inside the 140ms budget. */}
            <span aria-hidden="true" className="relative block h-3 w-[18px]">
              <i
                className={`absolute inset-x-0 top-0.5 h-[1.5px] rounded-full bg-current transition-[translate,rotate] duration-[140ms] ease-out ${
                  open ? "translate-y-[3.75px] rotate-45" : ""
                }`}
              />
              <i
                className={`absolute inset-x-0 bottom-0.5 h-[1.5px] rounded-full bg-current transition-[translate,rotate] duration-[140ms] ease-out ${
                  open ? "-translate-y-[3.75px] -rotate-45" : ""
                }`}
              />
            </span>
          </button>
        </div>
      </div>

      {/* The sheet, and under it the scrim, together filling the viewport
          below the bar. Kept mounted and toggled with `hidden` so a link's own
          navigation is never racing its removal. */}
      <div
        id="site-menu"
        hidden={!open}
        className="absolute inset-x-0 top-full flex h-[calc(100dvh-100%)] flex-col min-[1041px]:hidden"
      >
        <nav
          ref={sheetRef}
          aria-label="Menu"
          className="min-h-0 shrink overflow-y-auto border-t border-b border-divider bg-shell"
        >
          <ul className="mx-auto w-full max-w-[calc(var(--container-content)+var(--spacing-gutter)*2)] px-gutter">
            {LINKS.map((link) => (
              <li key={link.href} className="border-b border-rule-faint">
                <a
                  href={link.href}
                  onClick={follow}
                  className="flex min-h-14 items-center text-body text-ink-2 transition-colors duration-[140ms] ease-out hover:text-ink"
                >
                  {link.label}
                </a>
              </li>
            ))}
            <li className="py-6">
              <a
                href="#download"
                onClick={follow}
                className={`${buttonClass("primary", "lg")} w-full`}
              >
                Download
              </a>
            </li>
          </ul>
        </nav>
        <div aria-hidden="true" className="flex-1 bg-shell/80" />
      </div>
    </header>
  )
}
