import { Header } from "@/components/Header"
import { Chapters } from "@/components/sections/Chapters"
import { Download } from "@/components/sections/Download"
import { Footer } from "@/components/sections/Footer"
import { GetStarted } from "@/components/sections/GetStarted"
import { Hero } from "@/components/sections/Hero"
import { Questions } from "@/components/sections/Questions"

/**
 * The landing page, as product chapters: a hero, four chapters that each show
 * one thing the client does, then how to start, what people ask, and the
 * close. See README.md, "The page".
 */
export default function Page() {
  return (
    <>
      <Header />
      <main>
        <Hero />
        <Chapters /> {/* 01 Overlay · 02 HUD · 03 Loadouts · 04 Performance */}
        <GetStarted />
        <Questions /> {/* FAQ + requirements */}
        <Download />
      </main>
      <Footer />
    </>
  )
}
