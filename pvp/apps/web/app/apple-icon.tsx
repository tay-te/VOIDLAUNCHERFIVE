import { ImageResponse } from "next/og"

import { MarkSvg, SHELL } from "@/lib/og"

/* The home-screen icon. Full-bleed shell with no corner radius of its own:
   iOS rounds it. The mark sits at the same 7-in-9 proportion as icon.svg. */
export const size = { width: 180, height: 180 }
export const contentType = "image/png"

export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: SHELL,
        }}
      >
        <MarkSvg size={112} />
      </div>
    ),
    size,
  )
}
