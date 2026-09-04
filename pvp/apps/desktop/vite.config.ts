import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

/**
 * Two ways to run this app:
 *
 *   pnpm dev:web    plain browser, `@tauri-apps/api` aliased to src/mocks/tauri.ts,
 *                   so every screen is reviewable with no Rust and no webview.
 *   pnpm tauri dev  the real thing; Tauri sets TAURI_ENV_PLATFORM, and the alias
 *                   drops away.
 *
 * The alias is the whole mechanism — no `if (isTauri)` branches scattered through the
 * app, and no chance of the mock surviving into a shipped bundle.
 *
 * `@dev/backdrops` rides the same switch. The frames put a rendered Minecraft still
 * behind the recessed canvas and the launcher has no licenced art to ship, so the
 * preview borrows `design/screens/*.png` for the review pass and a Tauri build gets an
 * empty map and the gradient placeholder. `design/` is reference material: this is the
 * one place it is read, it is read only when Tauri is *not* driving the build, and
 * nothing it exports can therefore reach an installer.
 */
const isTauri = Boolean(process.env.TAURI_ENV_PLATFORM);

export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  resolve: {
    alias: [
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
      {
        find: '@dev/backdrops',
        replacement: fileURLToPath(
          new URL(isTauri ? './src/dev/backdrops.none.ts' : './src/dev/backdrops.ts', import.meta.url),
        ),
      },
      ...(isTauri
        ? []
        : [
            {
              find: /^@tauri-apps\/api\/(core|event)$/,
              replacement: fileURLToPath(new URL('./src/mocks/tauri.ts', import.meta.url)),
            },
          ]),
    ],
  },
  server: {
    port: 5183,
    strictPort: true,
    host: '127.0.0.1',
  },
  build: {
    // Tauri ships a modern webview on every supported OS; no legacy transpile needed.
    target: 'es2022',
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: !isTauri,
  },
  envPrefix: ['VITE_', 'TAURI_'],
});
