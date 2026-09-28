import { Meta } from "../Button"
import { Lockup } from "../Mark"
import { Wrap } from "../Section"

/**
 * After film board 43 Footer (410:5) — lockup, four link columns, and the
 * line any third-party Minecraft client has to carry. NOT AFFILIATED WITH
 * MOJANG OR MICROSOFT is load-bearing: it stays, and it stays visible.
 *
 * TODO: Changelog, Keybinds, Discord, GitHub, Terms and Privacy have no
 * destinations yet. The in-page anchors resolve.
 */
const COLUMNS = [
  {
    heading: "Product",
    links: [
      { label: "Download", href: "#download" },
      { label: "Mods", href: "#overlay" },
      { label: "Loadouts", href: "#loadouts" },
      { label: "Changelog", href: "#" },
    ],
  },
  {
    heading: "Help",
    links: [
      { label: "Requirements", href: "#requirements" },
      { label: "Keybinds", href: "#" },
      { label: "FAQ", href: "#questions" },
    ],
  },
  {
    heading: "Community",
    links: [
      { label: "Discord", href: "#" },
      { label: "GitHub", href: "#" },
    ],
  },
  {
    heading: "Legal",
    links: [
      { label: "Terms", href: "#" },
      { label: "Privacy", href: "#" },
    ],
  },
]

export function Footer() {
  return (
    <footer className="border-t border-rule pt-[clamp(64px,5.5vw,106px)] pb-[clamp(32px,2.5vw,48px)]">
      <Wrap>
        <div className="grid grid-cols-2 gap-[clamp(40px,6vw,64px)] min-[1101px]:grid-cols-[minmax(0,2fr)_repeat(4,minmax(0,1fr))] min-[1101px]:gap-[clamp(32px,2.5vw,48px)]">
          <div className="col-span-2 min-[1101px]:col-span-1">
            <Lockup />
            <p className="mt-[clamp(24px,2.083vw,40px)] text-link text-ink-2">
              A quiet Minecraft PvP client.
            </p>
          </div>

          {COLUMNS.map((column) => (
            <nav key={column.heading} aria-labelledby={`f-${column.heading}`}>
              <h2
                id={`f-${column.heading}`}
                className="text-cap font-medium uppercase text-ink-3"
              >
                {column.heading}
              </h2>
              {/* Touch-sized: each link is a 44px row and the rows butt, so
                  the pitch is the target rather than a gap between them. */}
              <ul className="mt-[clamp(20px,1.458vw,28px)] grid gap-[clamp(14px,1.198vw,23px)] touch:mt-2 touch:gap-0">
                {column.links.map((link) => (
                  <li key={link.label}>
                    <a
                      href={link.href}
                      className="text-link text-ink-2 transition-colors duration-[140ms] ease-out hover:text-ink touch:inline-flex touch:min-h-11 touch:items-center"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="mt-[clamp(60px,5.729vw,110px)] flex flex-wrap justify-between gap-4 border-t border-rule-faint pt-[clamp(24px,2.188vw,42px)]">
          <Meta>&copy; 2026 VOID</Meta>
          <Meta>Not affiliated with Mojang or Microsoft</Meta>
        </div>
      </Wrap>
    </footer>
  )
}
