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
export const VIEWPORT = { width: 1300, height: 820 };

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
 * The eight shots the pass takes. `design` names the frame to diff against; the three
 * with no `design` entry have no Figma frame of their own (Settings is a launcher
 * addition, the palette's frame is the in-game one, and the progress dock is a state
 * rather than a screen) so they are captured for review but never scored.
 */
export const SHOTS = [
  { id: 'play', label: 'Play', design: 'Launcher-Play.png' },
  { id: 'mods', label: 'Mods', design: 'Launcher-Mods.png' },
  { id: 'cosmetics', label: 'Cosmetics', design: 'Launcher-Cosmetics.png' },
  { id: 'servers', label: 'Servers', design: 'Launcher-Servers.png' },
  { id: 'friends', label: 'Friends', design: 'Launcher-Friends.png' },
  { id: 'settings', label: 'Settings', design: null },
  { id: 'palette', label: 'Command palette (⌘K)', design: null },
  { id: 'launching', label: 'Dock — launching / progress', design: null },
];
