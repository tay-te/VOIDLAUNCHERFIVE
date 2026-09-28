import type { CSSProperties, ReactNode } from "react"

/**
 * The shared section scaffold.
 *
 * The first build measured this off film board 36 (eyebrow, heading, a rule
 * 153px under it, content 110px under that). That was a 1080p frame's rhythm
 * and it put ~200px of nothing between every heading and what it introduced.
 * The page now runs on its own: eyebrow, heading, then the content `head`
 * below it, with no rule in between (globals.css, spacing).
 *
 * There are no slates. The only numbering on the page is the four chapters
 * (01–04) and the three steps (1–3).
 */

/** Stagger index for Reveal, as an inline custom property. */
export const at = (i: number) => ({ "--i": i }) as CSSProperties

export function Wrap({
  className = "",
  children,
}: {
  className?: string
  children: ReactNode
}) {
  return (
    <div
      className={`relative z-1 mx-auto w-full max-w-[calc(var(--container-content)+var(--spacing-gutter)*2)] px-gutter ${className}`}
    >
      {children}
    </div>
  )
}

export function Band({
  id,
  className = "",
  labelledBy,
  children,
}: {
  id?: string
  className?: string
  labelledBy?: string
  children: ReactNode
}) {
  return (
    <section id={id} aria-labelledby={labelledBy} className={`relative py-section ${className}`}>
      {children}
    </section>
  )
}

export function Eyebrow({
  children,
  className = "",
  i,
  reveal = true,
}: {
  children: ReactNode
  className?: string
  i?: number
  /** False where the line has to paint with the first frame (the hero). */
  reveal?: boolean
}) {
  return (
    <p
      {...(reveal ? { "data-reveal": true } : {})}
      style={i ? at(i) : undefined}
      className={`text-eyebrow font-medium uppercase text-ink-3 ${className}`}
    >
      {children}
    </p>
  )
}

/**
 * Eyebrow over an h2. The h2 is the section tier — the second of the page's
 * two display sizes, always smaller than the hero's h1. `mb-head` is the one
 * gap between a heading and its content.
 */
export function SectionHead({
  eyebrow,
  heading,
  id,
  className = "",
}: {
  eyebrow: ReactNode
  heading: ReactNode
  id: string
  className?: string
}) {
  return (
    <div className={`mb-head ${className}`}>
      <Eyebrow>{eyebrow}</Eyebrow>
      <h2
        id={id}
        data-reveal
        style={at(1)}
        className="mt-eyebrow text-section font-light text-ink text-balance"
      >
        {heading}
      </h2>
    </div>
  )
}
