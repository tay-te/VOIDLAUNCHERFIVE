import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

/**
 * The component gallery — `pnpm gallery`.
 *
 * Roots at `gallery/` and resolves `@void/ui` to `src/`, so the page renders the
 * working tree rather than the last build. Everything else is Vite's defaults; the
 * gallery is a development surface and is never shipped.
 */
export default defineConfig({
  root: here,
  plugins: [react()],
  resolve: {
    alias: { '@void/ui': path.resolve(here, '../src/index.ts') },
  },
  server: { port: 5177, strictPort: false },
  build: { outDir: path.resolve(here, '../gallery-dist'), emptyOutDir: true },
});
