/**
 * Runs in <head> before first paint, inlined by app/layout.tsx.
 *
 * 1. `html.js` — the pre-reveal hidden state in globals.css is keyed on it, so
 *    with JavaScript off nothing is ever hidden.
 * 2. `html[data-platform]` — handheld | windows | desktop, read once here so
 *    DownloadButtons and the CSS agree on it before hydration. Phones and
 *    tablets cannot install a desktop client; iPadOS reports itself as a Mac,
 *    which is what the maxTouchPoints test catches.
 *
 * A string, not a function: it is inlined verbatim and must not depend on
 * anything the bundler does to it.
 */
export const BOOT_SCRIPT = `(function(){var d=document.documentElement;d.classList.add("js");try{var n=navigator,u=n.userAgent||"",h=(n.userAgentData&&n.userAgentData.mobile)||/Android|iPhone|iPad|iPod|Mobile|Silk|Kindle|BlackBerry|Opera Mini|IEMobile/i.test(u)||(/Macintosh/.test(u)&&n.maxTouchPoints>1);d.setAttribute("data-platform",h?"handheld":/Win(dows|32|64)/i.test(u)?"windows":"desktop")}catch(e){}})()`

export type Platform = "handheld" | "windows" | "desktop"

/** What the boot script decided. `desktop` if it never ran. */
export function readPlatform(): Platform {
  const value = document.documentElement.getAttribute("data-platform")
  return value === "handheld" || value === "windows" ? value : "desktop"
}
