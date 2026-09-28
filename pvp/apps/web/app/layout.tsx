import type { Metadata, Viewport } from "next"
import localFont from "next/font/local"

import { Reveal } from "@/components/Reveal"
import { BOOT_SCRIPT } from "@/lib/boot"
import "./globals.css"

/* §2 of the contract: Outfit only, 300 / 400 / 500, nothing heavier. These are
   the same woff2 instances @void/ui bundles for the launcher and the in-game
   overlay, so the site renders in the face the product does.

   300 and 500 are what the page sets, so they are preloaded. 400 is the
   contract's display weight and stays available, but next/font preloads per
   call rather than per file, so it is its own family and is fetched only when
   something uses it (`font-regular` in globals.css). If the display tier moves
   to 400, flip its `preload` so the headline is not a font swap. */
const outfit = localFont({
  variable: "--font-outfit",
  display: "swap",
  src: [
    { path: "./fonts/outfit-300.woff2", weight: "300", style: "normal" },
    { path: "./fonts/outfit-500.woff2", weight: "500", style: "normal" },
  ],
})

const outfitRegular = localFont({
  variable: "--font-outfit-regular",
  display: "swap",
  preload: false,
  src: [{ path: "./fonts/outfit-400.woff2", weight: "400", style: "normal" }],
})

/* Absolute URLs for the Open Graph and Twitter images. An explicit
   NEXT_PUBLIC_SITE_URL wins; on Vercel the production domain, then the
   deployment's own URL, are always set. Anything else is a build on this
   machine, which is the only place localhost is right. A self-hosted
   production deploy must set NEXT_PUBLIC_SITE_URL. */
const vercelHost =
  process.env.VERCEL_PROJECT_PRODUCTION_URL || process.env.VERCEL_URL
const siteUrl =
  process.env.NEXT_PUBLIC_SITE_URL ||
  (vercelHost ? `https://${vercelHost}` : `http://localhost:${process.env.PORT || 5184}`)

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: "VOID — a quiet Minecraft PvP client",
  description:
    "VOID is a free Minecraft PvP client for 1.8.9, on Mac and Windows. Twenty-nine mods, an overlay that opens over the game, a HUD you can put anywhere, and a loadout for every game mode.",
  openGraph: {
    type: "website",
    title: "VOID — a quiet Minecraft PvP client",
    description:
      "Everything in one window. Minecraft 1.8.9, Mac and Windows, free and finished.",
    // The image is app/opengraph-image.tsx; file-based metadata wins over a
    // config entry, so none is listed here.
  },
  twitter: { card: "summary_large_image" },
}

export const viewport: Viewport = {
  themeColor: "#0b0b0c",
  colorScheme: "dark",
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    // suppressHydrationWarning: the boot script adds `js` and `data-platform`
    // to <html> before React hydrates, on purpose.
    <html
      lang="en"
      className={`${outfit.variable} ${outfitRegular.variable}`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: BOOT_SCRIPT }} />
      </head>
      <body>
        {children}
        <Reveal />
      </body>
    </html>
  )
}
