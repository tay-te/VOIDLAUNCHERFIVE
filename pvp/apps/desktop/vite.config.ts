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
 */
const isTauri = Boolean(process.env.TAURI_ENV_PLATFORM);

export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  resolve: {
    alias: [
      { find: '@', replacement: fileURLToPath(new URL('./src', import.meta.url)) },
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
