"use client"

import { useEffect } from "react"

/**
 * Reveal on scroll, mounted once in the layout so every section can stay a
 * server component. Board motion applied to scroll: the 8px rise over 140ms
 * ease-out that board 02 uses to set its words, on board 27's 90ms stagger.
 *
 * globals.css owns the transition and the hidden state; this only adds the
 * class. It is entirely additive — the hidden state is keyed on `html.js`, so
 * with JavaScript off nothing is hidden — and `prefers-reduced-motion` shows
 * everything immediately.
 *
 * The reveal band stops 12% above the viewport's floor, which the last strip
 * of the page can never scroll into: the footer's legal line would stay
 * invisible for ever. So once the document is scrolled to its end (or cannot
 * scroll at all), everything already on screen is shown regardless.
 *
 * A MutationObserver picks up `[data-reveal]` nodes that appear after this
 * mounts. That matters in two places: a client-side route change replaces the
 * page without remounting the layout, and Fast Refresh does the same in dev.
 * Without it the next page would render permanently invisible.
 */
export function Reveal() {
  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches

    const show = (el: Element) => el.classList.add("is-in")

    if (reduced || !("IntersectionObserver" in window)) {
      const showAll = () => document.querySelectorAll("[data-reveal]").forEach(show)
      showAll()
      const mutations = new MutationObserver(showAll)
      mutations.observe(document.body, { childList: true, subtree: true })
      return () => mutations.disconnect()
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          show(entry.target)
          observer.unobserve(entry.target)
        }
      },
      { rootMargin: "0px 0px -12% 0px", threshold: 0.01 },
    )

    const scan = () => {
      for (const el of document.querySelectorAll("[data-reveal]:not(.is-in)")) {
        observer.observe(el)
      }
    }

    // Only does any work at the very end of the page.
    const flushAtEnd = () => {
      const root = document.documentElement
      if (window.innerHeight + window.scrollY < root.scrollHeight - 2) return
      for (const el of document.querySelectorAll("[data-reveal]:not(.is-in)")) {
        if (el.getBoundingClientRect().top >= window.innerHeight) continue
        show(el)
        observer.unobserve(el)
      }
    }

    scan()
    flushAtEnd()
    const mutations = new MutationObserver(() => {
      scan()
      flushAtEnd()
    })
    mutations.observe(document.body, { childList: true, subtree: true })
    window.addEventListener("scroll", flushAtEnd, { passive: true })
    window.addEventListener("resize", flushAtEnd, { passive: true })

    return () => {
      mutations.disconnect()
      observer.disconnect()
      window.removeEventListener("scroll", flushAtEnd)
      window.removeEventListener("resize", flushAtEnd)
    }
  }, [])

  return null
}
