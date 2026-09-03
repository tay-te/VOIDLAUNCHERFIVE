import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';
import { existsSync, createReadStream } from 'node:fs';

const here = dirname(fileURLToPath(import.meta.url));

/**
 * The build output path is pre-agreed in CONTRACTS.md: `packages/ingame` is the only
 * package allowed to write outside its own directory, and only here.
 */
const OUT_DIR = resolve(here, '../../mod/src/main/resources/assets/void/ui');

/**
 * DEV ONLY. Serves the read-only Figma exports in `pvp/design/screens` at
 * `/__design/<name>.png` so `pnpm dev` can put the real frame behind the UI for a
 * side-by-side pixel comparison. Never runs in `vite build`, so nothing from
 * `design/` is ever imported at build time (CONTRACTS.md).
 */
function designScreensDevServer(): Plugin {
  const screens = resolve(here, '../../design/screens');
  return {
    name: 'void-design-screens-dev',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url ?? '';
        if (!url.startsWith('/__design/')) return next();
        const name = url.slice('/__design/'.length).split('?')[0];
        if (!/^[A-Za-z0-9._-]+\.png$/.test(name)) return next();
        const file = join(screens, name);
        if (!existsSync(file)) {
          res.statusCode = 404;
          return res.end('no such design screen');
        }
        res.setHeader('content-type', 'image/png');
        createReadStream(file).pipe(res);
      });
    },
  };
}

export default defineConfig(({ command }) => ({
  // Relative URLs: the bundle is loaded from the JAR classpath as
  // `assets/void/ui/index.html`, where there is no server and no origin.
  base: './',
  plugins: [react(), designScreensDevServer()],
  resolve: {
    alias: {
      '@': resolve(here, 'src'),
    },
  },
  server: {
    port: 5183,
    strictPort: false,
    open: false,
  },
  build: {
    target: 'es2022',
    outDir: command === 'build' ? OUT_DIR : resolve(here, 'dist'),
    emptyOutDir: true,
    assetsDir: 'assets',
    cssCodeSplit: false,
    modulePreload: { polyfill: false },
    sourcemap: false,
    reportCompressedSize: true,
    assetsInlineLimit: 4096,
    rollupOptions: {
      output: {
        // One JS chunk, one CSS file. Fewer requests off a classpath loader.
        manualChunks: undefined,
      },
    },
  },
}));
