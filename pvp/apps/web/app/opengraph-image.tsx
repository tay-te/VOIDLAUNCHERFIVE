import { ImageResponse } from "next/og"

import { FIELD, INK, INK_2, INK_3, MarkSvg, SHELL, outfit } from "@/lib/og"

/**
 * The share card, 1200 × 630. Board 44's hero in the site's own system: flat
 * shell, the 3% cell field, the lockup, the eyebrow, the headline, the
 * subline. No colour — nothing on it is a live value.
 */
export const alt = "VOID — Everything in one window. A quiet Minecraft PvP client for 1.8.9."
export const size = { width: 1200, height: 630 }
export const contentType = "image/png"

export default async function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: "72px 80px 80px",
          background: SHELL,
          backgroundImage: FIELD,
          backgroundSize: "40px 40px",
          fontFamily: "Outfit",
          color: INK,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <MarkSvg size={52} />
          <div style={{ fontSize: 34, fontWeight: 300, letterSpacing: 4.4 }}>VOID</div>
        </div>

        <div style={{ display: "flex", flexDirection: "column" }}>
          <div
            style={{
              display: "flex",
              fontSize: 17,
              fontWeight: 500,
              letterSpacing: 2.7,
              textTransform: "uppercase",
              color: INK_3,
            }}
          >
            <span style={{ color: INK_2 }}>VOID</span>
            <span style={{ margin: "0 15px", opacity: 0.5 }}>·</span>
            Minecraft PvP client
          </div>
          <div
            style={{
              marginTop: 28,
              fontSize: 96,
              fontWeight: 300,
              letterSpacing: -2.9,
              lineHeight: 1.08,
            }}
          >
            Everything in one window.
          </div>
          <div style={{ marginTop: 26, fontSize: 32, fontWeight: 300, color: INK_2 }}>
            Free, and finished. Minecraft 1.8.9, Mac and Windows.
          </div>
        </div>
      </div>
    ),
    { ...size, fonts: await outfit() },
  )
}
