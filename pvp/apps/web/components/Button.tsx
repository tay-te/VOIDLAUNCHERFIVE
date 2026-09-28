import type { ReactNode } from "react"

/**
 * §4 of the contract. primary = ink fill with a shell-coloured label.
 * secondary = 1px border, no fill. Nothing glows.
 *
 * Hover and keyboard focus share one state, and it has to be seen: the primary
 * fill steps 16% of the way down to the shell (ink → white was a change of
 * 1.2%, which nobody could see), and the secondary takes the raised fill. In
 * 140ms ease-out, out in 100ms ease-in, per §5. The focus ring itself is the
 * global `:focus-visible` outline in globals.css.
 */

/* `border-transparent` lives on the primary variant, not here: two border-color
   utilities on one element tie on specificity, and which wins comes down to
   Tailwind's own source order rather than the order they are written in. */
const base =
  "inline-flex cursor-pointer items-center justify-center rounded-button border " +
  "text-button font-medium transition-colors duration-100 ease-in " +
  "hover:duration-[140ms] hover:ease-out focus-visible:duration-[140ms] focus-visible:ease-out"

const size = {
  lg: "min-h-[clamp(48px,3.542vw,68px)] px-[clamp(24px,2.2vw,42px)]",
  sm: "min-h-[clamp(36px,2.083vw,40px)] px-[clamp(16px,1.146vw,22px)] rounded-nav text-link touch:min-h-11",
}

const variant = {
  primary:
    "border-transparent bg-ink text-shell " +
    "hover:bg-[color-mix(in_srgb,var(--color-ink)_84%,var(--color-shell))] " +
    "focus-visible:bg-[color-mix(in_srgb,var(--color-ink)_84%,var(--color-shell))]",
  secondary:
    "border-line-button text-ink hover:bg-raised focus-visible:bg-raised",
}

export type ButtonTone = keyof typeof variant
export type ButtonScale = keyof typeof size

/** The classes, for the one place a button is a <button> rather than a link. */
export function buttonClass(tone: ButtonTone = "primary", scale: ButtonScale = "lg") {
  return `${base} ${size[scale]} ${variant[tone]}`
}

export function Button({
  href,
  children,
  tone = "primary",
  scale = "lg",
  className = "",
}: {
  href: string
  children: ReactNode
  tone?: ButtonTone
  scale?: ButtonScale
  className?: string
}) {
  return (
    <a href={href} className={`${buttonClass(tone, scale)} ${className}`}>
      {children}
    </a>
  )
}

export function LinkArrow({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a
      href={href}
      className="group inline-flex items-center gap-[0.6em] text-link font-medium text-ink-2 transition-colors duration-[140ms] ease-out hover:text-ink touch:min-h-11"
    >
      <span>{children}</span>
      <span
        aria-hidden="true"
        className="transition-transform duration-[140ms] ease-out group-hover:translate-x-1"
      >
        &rarr;
      </span>
    </a>
  )
}

export function Meta({
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
      style={i ? ({ "--i": i } as React.CSSProperties) : undefined}
      className={`text-meta font-medium uppercase text-ink-3 ${className}`}
    >
      {children}
    </p>
  )
}
