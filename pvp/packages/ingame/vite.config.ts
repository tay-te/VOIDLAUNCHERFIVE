import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';
import { existsSync, createReadStream, readFileSync, writeFileSync, rmSync } from 'node:fs';

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

/**
 * Injects `<script src="./void-shim.js"></script>` at the top of `<head>` in the built
 * `index.html`.
 *
 * The shim is **not** an asset of this package: it is committed by the mod at
 * `mod/src/main/resources/assets/void/shim/void-shim.js` and copied next to this bundle
 * by Gradle's `processResources`, so it exists in the JAR but never on disk here. That
 * is exactly why the tag is injected rather than written in `index.html` — Vite would
 * try to resolve a missing file and fail the build.
 *
 * Build only. In the browser harness there is no Java, `createFakeVoid()` installs
 * `window.void` itself, and a 404 for the shim would be noise (CONTRACTS.md, "The
 * bridge shim").
 */
function injectVoidShim(): Plugin {
  return {
    name: 'void-inject-shim',
    apply: 'build',
    enforce: 'post',
    transformIndexHtml(html) {
      if (html.includes('void-shim.js')) return html;
      return html.replace('<head>', '<head>\n    <script src="./void-shim.js"></script>');
    },
  };
}

/**
 * Folds the emitted JS, the emitted CSS and the bridge shim into `index.html` as inline
 * `<script>` / `<style>`, and deletes the now-unreferenced standalone files.
 *
 * **Why this is not optional.** Ultralight loads this bundle through the host's classpath
 * FileSystem on a `file:///` scheme with no origin. Two of the three subresource kinds do
 * not survive that:
 *
 *  - `<script type="module">` is *always* fetched with CORS semantics, whatever the
 *    `crossorigin` attribute says, and an opaque custom scheme cannot satisfy it — so
 *    `stripCrossorigin()` below is necessary but not sufficient, and the React bundle
 *    never executed in game.
 *  - the classic `<script src>` for the shim did not execute either, so `window.void` was
 *    undefined and every `__emit` from Java threw — the HUD and the Right-Shift menu stayed
 *    empty even though the view itself was painting.
 *
 *  - the webfaces did not load either. That one is silent rather than fatal: Ultralight's
 *    own loader answers with a single bundled face (Inter) for any family it cannot fetch,
 *    so the whole design simply renders in the wrong typeface — no error anywhere.
 *
 * So everything the page needs is folded in, fonts included as base64. Verified in game on
 * 1.8.9: inlined, the menu renders in the design's own faces and the bridge round-trips;
 * external, it does not.
 *
 * Build only — the browser harness at `pnpm dev` serves over HTTP, where none of this applies.
 */
function inlineForUltralight(outDir: string): Plugin {
  return {
    name: 'void-inline-for-ultralight',
    apply: 'build',
    enforce: 'post',
    closeBundle() {
      const indexHtml = join(outDir, 'index.html');
      if (!existsSync(indexHtml)) return;
      let html = readFileSync(indexHtml, 'utf8');
      const consumed: string[] = [];

      // Vite emits the app as <script type="module"> in <head>. A module is deferred until
      // after parsing, but an inline classic script is not — left in <head> it would run
      // before #void-root exists and React would have nothing to mount on. So the app moves
      // to the end of <body>, which is where a module would effectively have run anyway.
      const deferred: string[] = [];
      html = html.replace(
        /<script\b([^>]*)\bsrc="\.\/([^"]+)"[^>]*><\/script>/g,
        (tag, attrs: string, src: string) => {
          const file = join(outDir, src);
          if (!existsSync(file)) return tag;
          consumed.push(file);
          const code = readFileSync(file, 'utf8');
          if (/\btype="module"/.test(attrs)) {
            deferred.push(code);
            return '';
          }
          return `<script>${code}</script>`;
        },
      );
      for (const code of deferred) {
        html = html.replace('</body>', `  <script>${code}</script>\n  </body>`);
      }

      html = html.replace(
        /<link\b[^>]*\brel="stylesheet"[^>]*\bhref="\.\/([^"]+)"[^>]*>/g,
        (tag, href: string) => {
          const file = join(outDir, href);
          if (!existsSync(file)) return tag;
          consumed.push(file);
          // The CSS moves from assets/ up to the document root, so its own relative
          // url(./x.woff2) references have to gain that segment back.
          // Fonts have to be inlined too. Ultralight's own loader answers with a single
          // bundled face (Inter) for any family it cannot fetch, so a font that fails to
          // load is not a visible error — it is the whole design silently rendering in the
          // wrong typeface. Base64 costs ~33% before gzip, which woff2 largely recovers.
          let css = readFileSync(file, 'utf8').replace(
            /url\(\s*['"]?\.\/([^'")]+\.woff2)['"]?\s*\)/g,
            (ref, name: string) => {
              const font = join(outDir, 'assets', name);
              if (!existsSync(font)) return ref;
              consumed.push(font);
              return `url(data:font/woff2;base64,${readFileSync(font).toString('base64')})`;
            },
          );
          return `<style>${css}</style>`;
        },
      );

      // The shim is the mod's file, copied next to this bundle by Gradle at package time —
      // at build time it is still only at its source path.
      const shim = resolve(here, '../../mod/src/main/resources/assets/void/shim/void-shim.js');
      if (existsSync(shim)) {
        html = html.replace(
          '<script src="./void-shim.js"></script>',
          `<script>${readFileSync(shim, 'utf8')}</script>`,
        );
      }

      writeFileSync(indexHtml, html);
      for (const file of consumed) rmSync(file, { force: true });
    },
  };
}

/**
 * Strips the `crossorigin` attribute Vite puts on the emitted `<script>` and
 * `<link>`. The bundle is loaded by the Ultralight host from the JAR classpath,
 * not over HTTP: there is no origin to be cross to, and a CORS-flagged fetch on
 * a custom scheme is a way to fail for nothing.
 */
function stripCrossorigin(): Plugin {
  return {
    name: 'void-strip-crossorigin',
    apply: 'build',
    enforce: 'post',
    transformIndexHtml(html) {
      return html.replace(/\s+crossorigin(?==|>|\s)/g, '');
    },
  };
}

export default defineConfig(({ command }) => ({
  // Relative URLs: the bundle is loaded from the JAR classpath as
  // `assets/void/ui/index.html`, where there is no server and no origin.
  base: './',
  plugins: [
    react(),
    designScreensDevServer(),
    injectVoidShim(),
    stripCrossorigin(),
    inlineForUltralight(command === 'build' ? OUT_DIR : resolve(here, 'dist')),
  ],
  resolve: {
    /**
     * `@void/ui` and `@void/protocol` are consumed from **source**, not from their
     * `dist/`. Both are written by sibling owners in this same monorepo and their
     * `exports` maps point at build output that may not exist yet; aliasing to
     * source means this bundle always compiles against what they have actually
     * written, and never needs another package's build to have been run. The
     * import specifiers in the code are the real package names either way.
     */
    alias: [
      { find: '@void-ui-src', replacement: resolve(here, '../ui/src') },
      { find: '@void/ui/tokens.css', replacement: resolve(here, '../ui/src/tokens.css') },
      { find: '@void/ui/fonts.css', replacement: resolve(here, '../ui/src/fonts.css') },
      { find: '@void/ui/styles.css', replacement: resolve(here, 'src/styles/void-ui.css') },
      { find: /^@void\/ui$/, replacement: resolve(here, '../ui/src/index.ts') },
      { find: /^@void\/protocol$/, replacement: resolve(here, '../protocol/src/index.ts') },
      { find: '@', replacement: resolve(here, 'src') },
    ],
  },
  server: {
    // 5184, not 5183: `apps/desktop`'s `dev:web` takes 5183 with --strictPort, and
    // reviewing the launcher and the in-game harness side by side is normal.
    // `visual-qa/{capture,measure}.mjs` already default to 5184.
    port: 5184,
    strictPort: false,
    open: false,
  },
  /**
   * DEV ONLY — `VOID_UI_FAKEMODS=<n>` pads the Mods panel to `n` tiles with synthetic mods,
   * so the layout can be looked at at a count the registry does not ship (`src/dev/fake-mods.ts`).
   *
   * A `define` rather than an `envPrefix`, for two reasons. The in-game page is loaded off the
   * JAR classpath on a `file:///` URL with no query string and no environment, so a build-time
   * substitution is the only channel it has — and naming the one variable keeps every other
   * `VOID_UI_*` the mod reads (renderer, profile, blur) out of the client bundle, which a
   * prefix would have swept in.
   *
   * Absent, it substitutes `''`, `planFakeMods` returns nothing and the whole feature costs an
   * empty Set and a dead branch.
   */
  define: {
    __VOID_UI_FAKEMODS__: JSON.stringify(process.env.VOID_UI_FAKEMODS ?? ''),
  },
  build: {
    target: 'es2022',
    outDir: command === 'build' ? OUT_DIR : resolve(here, 'dist'),
    // Safe to wipe: the only things in this directory are this bundle's own outputs.
    // The shim lives one directory up in `assets/void/shim/` and is copied here by
    // Gradle at package time, into `build/resources/main/`, never into the source tree
    // — so the two builds cannot delete each other's work whichever runs first
    // (CONTRACTS.md, "The bridge shim").
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
