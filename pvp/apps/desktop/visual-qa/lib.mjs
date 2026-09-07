/**
 * Shared plumbing for the visual-QA pass.
 *
 * `playwright`, `pixelmatch` and `pngjs` are deliberately *not* dependencies of
 * `@void/desktop`: they are a review tool, not something the launcher ships, and
 * adding them would put three heavyweight packages (and a browser download) into
 * every `pnpm install` of the workspace. They are resolved at run time from wherever
 * the machine already has them — a global `npm i -g`, a `NODE_PATH` entry, or a plain
 * `npm i --no-save` in this folder. See `../README.md` § Visual QA.
 */

import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const HERE = path.dirname(fileURLToPath(import.meta.url));
export const APP = path.resolve(HERE, '..');
export const DESIGN = path.resolve(APP, '../../design/screens');
export const OUT = path.join(HERE, 'out');

/** The one viewport every frame in `design/` is drawn at. */
export const VIEWPORT = { width: 1600, height: 980 };

let globalRoot = null;
function globalNodeModules() {
  if (globalRoot !== null) return globalRoot;
  try {
    globalRoot = execFileSync('npm', ['root', '-g'], { encoding: 'utf8' }).trim();
  } catch {
    globalRoot = '';
  }
  return globalRoot;
}

/**
 * Resolve a review-only package from the local folder, NODE_PATH, or the global root.
 *
 * CommonJS packages (`playwright`, `pngjs`) arrive as `{ default: exports }` through
 * `import()`, ESM ones (`pixelmatch`) as themselves, so the namespace is flattened
 * once here rather than at every call site.
 */
export async function need(name) {
  const mod = await load(name);
  return mod.default && typeof mod.default === 'object' ? { ...mod.default, ...mod } : mod;
}

async function load(name) {
  const roots = [
    HERE,
    APP,
    ...(process.env.NODE_PATH ?? '').split(path.delimiter).filter(Boolean),
    globalNodeModules(),
  ].filter(Boolean);

  for (const root of roots) {
    const candidates = [
      path.join(root, 'node_modules', name),
      path.join(root, name), // NODE_PATH / npm root -g already point at a node_modules
    ];
    for (const dir of candidates) {
      if (!existsSync(dir)) continue;
      try {
        const require = createRequire(pathToFileURL(path.join(dir, 'package.json')));
        const entry = require.resolve(name, { paths: [path.dirname(dir)] });
        return await import(pathToFileURL(entry).href);
      } catch {
        /* try the next root */
      }
    }
  }
  try {
    return await import(name);
  } catch {
    throw new Error(
      `visual-qa needs "${name}". Install it globally (npm i -g ${name}) or run ` +
        `\`npm i --no-save ${name}\` inside apps/desktop/visual-qa.`,
    );
  }
}

/**
 * The regions of a frame that carry UI, per screen.
 *
 * The canonical frames (`design/screens/launcher/`, 1600 x 980) are flat: there is no
 * photographic hero behind the content panel any more, only the shell fill and the
 * recessed panel, so a straight full-frame diff is meaningful and the "art supplied"
 * pass that the 1300 x 820 baseline needed is gone.
 *
 * The regions are still scored separately because they say *where* a difference is:
 *
 *   chrome  the 80px navbar band                     0,0    1600 x 80
 *   panel   the content panel, inset 32,80 (§7)      32,80  1536 x 768
 *   dock    the band under it                        0,848  1600 x 132
 *
 * Together they are the whole frame, so `ui only` and `frame` differ only by the
 * rounded corners of the 20px window radius.
 */
const CHROME = [0, 0, 1600, 80];
const PANEL = [32, 80, 1536, 768];
const DOCK = [0, 848, 1600, 132];

export const UI_REGIONS = {
  play: [CHROME, PANEL, DOCK],
  mods: [CHROME, PANEL, DOCK],
  setup: [CHROME, PANEL, DOCK],
};

/**
 * The shots the pass takes. `design` names the frame to diff against, relative to
 * `design/screens`.
 *
 * Only three frames were re-cut at the shipped 1600 x 980 window size, and they are the
 * three the launcher is scored on. Cosmetics, Servers and Friends still have only the
 * superseded 1300 x 820 boards in `design/screens/`, which cannot be diffed against a
 * 1600 x 980 capture at all — they are captured for review and left unscored rather
 * than compared against a frame that is the wrong size and the wrong system.
 */
export const SHOTS = [
  { id: 'play', label: 'Play', design: 'launcher/Launcher-Play.png' },
  { id: 'mods', label: 'Mods', design: 'launcher/Launcher-Mods.png' },
  { id: 'setup', label: 'Mod setup', design: 'launcher/Launcher-Setup.png' },
  { id: 'cosmetics', label: 'Cosmetics', design: null },
  { id: 'servers', label: 'Servers', design: null },
  { id: 'friends', label: 'Friends', design: null },
  { id: 'settings', label: 'Settings', design: null },
  { id: 'palette', label: 'Command palette (Cmd-K)', design: null },
  { id: 'launching', label: 'Dock - launching / progress', design: null },
];
