"use client"

import { useEffect, useRef, useState } from "react"

import { readPlatform, type Platform } from "@/lib/boot"

import { Button, buttonClass } from "./Button"

/**
 * Board 38 names macOS first, and that is what the server renders, so with
 * JavaScript off (and for crawlers) both desktop buttons are there. On the
 * client the boot script's platform decides (lib/boot.ts):
 *
 *  - desktop: macOS leads and takes the primary fill, as rendered.
 *  - windows: the pair swaps, so Windows leads and takes the primary fill.
 *  - handheld: a phone or tablet cannot install a desktop client, so the
 *    buttons give way to one line saying where VOID runs and a Copy link, so
 *    the page can be sent to the machine that can.
 *
 * Until the client has decided, the pair carries `data-dl-pending`; globals.css
 * keeps it invisible on windows and handheld so the wrong pair never flashes.
 *
 * TODO: both point at #download until the release artefacts exist.
 */
export function DownloadButtons({ className = "" }: { className?: string }) {
  const [platform, setPlatform] = useState<Platform | null>(null)

  useEffect(() => {
    setPlatform(readPlatform())
  }, [])

  if (platform === "handheld") return <CopyLink className={className} />

  const windowsFirst = platform === "windows"
  const mac = (
    <Button key="mac" href="#download" tone={windowsFirst ? "secondary" : "primary"}>
      Download for macOS
    </Button>
  )
  const windows = (
    <Button key="windows" href="#download" tone={windowsFirst ? "primary" : "secondary"}>
      Download for Windows
    </Button>
  )

  return (
    <div
      {...(platform === null ? { "data-dl-pending": true } : {})}
      className={`flex flex-wrap gap-[clamp(12px,1.25vw,24px)] ${className}`}
    >
      {windowsFirst ? [windows, mac] : [mac, windows]}
    </div>
  )
}

/** The handheld state: where it runs, and the page's address to take there. */
function CopyLink({ className }: { className: string }) {
  const [copied, setCopied] = useState(false)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const copy = async () => {
    const url = `${window.location.origin}${window.location.pathname}`
    let ok = false
    try {
      await navigator.clipboard.writeText(url)
      ok = true
    } catch {
      // No async clipboard (older WebViews, insecure origins): the selection
      // route still works on every mobile browser that matters.
      const field = document.createElement("textarea")
      field.value = url
      field.setAttribute("readonly", "")
      field.style.position = "fixed"
      field.style.opacity = "0"
      document.body.append(field)
      field.select()
      ok = document.execCommand("copy")
      field.remove()
    }
    if (!ok) return
    setCopied(true)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className={`flex flex-wrap gap-[clamp(12px,1.25vw,24px)] ${className}`}>
      <p className="w-full text-body text-ink-2 text-balance">
        VOID runs on macOS and Windows. Copy the link to open it on your computer.
      </p>
      <button
        type="button"
        onClick={copy}
        className={`${buttonClass("secondary", "lg")} min-w-[10em]`}
      >
        {copied ? "Copied" : "Copy link"}
      </button>
      <span role="status" className="sr-only">
        {copied ? "Link copied" : ""}
      </span>
    </div>
  )
}
