import path from "node:path"
import type { NextConfig } from "next"

const nextConfig: NextConfig = {
  // This app lives inside the pvp pnpm workspace, so Next has to be told where
  // the real root is or it traces (and warns about) the whole repo.
  outputFileTracingRoot: path.join(import.meta.dirname, "../.."),

  // Next 16 writes its own AGENTS.md / CLAUDE.md into the app directory. The
  // repo already has conventions in pvp/CONTRACTS.md and this README, and a
  // generated CLAUDE.md here would shadow them.
  agentRules: false,

  // Lets several dev servers run side by side (each needs its own build dir and
  // its own single-instance lock): `NEXT_DIST_DIR=.next-b next dev --port 5192`.
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
}

export default nextConfig
