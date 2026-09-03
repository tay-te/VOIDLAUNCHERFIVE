#!/usr/bin/env node
/**
 * Ultralight constraint guard. Fails the build (exit 1) when the bundle asks
 * the in-game renderer for something it cannot draw.
 *
 * The rules come from `pvp/design/ultralight-notes.md`:
 *   §1  backdrop-filter        — parsed and dropped; nothing behind gets blurred
 *   §2  mix-blend-mode         — separable blend modes are not implemented
 *   §3  text-shadow            — dropped, or it smears the glyph atlas
 *   §4  3D transforms          — 2D only; no perspective, translateZ, rotate3d
 *   §5  WebGL / canvas 3D      — no context at all
 *   §6  <video> / <audio>      — no media pipeline
 *   §7  font-variation-settings, -webkit-text-stroke, CSS grid
 *
 * It scans BOTH the sources and, when present, the emitted bundle — a token
 * pulled in from a dependency has to fail too, not just one written here.
 *
 * Run standalone: `pnpm lint:ultralight`.
 */

import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const OUT_DIR = resolve(root, '../../mod/src/main/resources/assets/void/ui');

/** [regexp, why]. Each pattern must be safe to run over both CSS and JS. */
const RULES = [
  [/backdrop-filter\s*:/i, 'backdrop-filter — §1: paint a semi-opaque solid; the GL pass supplies the blur'],
  [/-webkit-backdrop-filter\s*:/i, 'backdrop-filter — §1'],
  [/\bmix-blend-mode\s*:/i, 'mix-blend-mode — §2: bake the effect into the flat colour instead'],
  [/\bbackground-blend-mode\s*:/i, 'background-blend-mode — §2'],
  [/\btext-shadow\s*:/i, 'text-shadow — §3: give the text a chip, or a 4-copy offset outline'],
  [/-webkit-text-stroke/i, '-webkit-text-stroke — §3: unreliable in Ultralight'],
  [/\bperspective\s*:/i, 'perspective — §4: 2D transforms only'],
  [/\btransform-style\s*:\s*preserve-3d/i, 'preserve-3d — §4'],
  [/\bbackface-visibility\s*:/i, 'backface-visibility — §4'],
  [/\b(?:translate|rotate|scale)3d\s*\(/i, '3D transform function — §4'],
  [/\btranslateZ\s*\(/i, 'translateZ — §4'],
  [/\brotate[XY]\s*\(/i, 'rotateX / rotateY — §4'],
  [/getContext\s*\(\s*['"](?:webgl2?|experimental-webgl)['"]/i, 'WebGL — §5: no context exists'],
  [/<video[\s>]/i, '<video> — §6: no media pipeline'],
  [/<audio[\s>]/i, '<audio> — §6'],
  [/\bfont-variation-settings\s*:/i, 'font-variation-settings — §7: bundle static instances'],
  [/\bdisplay\s*:\s*(?:inline-)?grid\b/i, 'CSS grid — §7: the design is flex + absolute throughout'],
  [/\bgrid-template-(?:columns|rows|areas)\s*:/i, 'CSS grid — §7'],
  [/\bposition\s*:\s*sticky\b/i, 'position: sticky — §7: fixed-height panels, no sticky headers'],
  [/\bfetch\s*\(/, 'fetch() — the bundle runs off the JAR classpath with no network'],
  [/new\s+XMLHttpRequest\b/, 'XMLHttpRequest — no network in game'],
  [/new\s+WebSocket\b/, 'WebSocket — the WS link is Java↔Rust, never the page (§7 of the architecture)'],
  [/requestAnimationFrame\s*\(/, 'requestAnimationFrame — the 20 Hz `tick` push is the only clock (§9)'],
];

/** Files the guard reads. */
const SOURCE_EXT = new Set(['.ts', '.tsx', '.css', '.html', '.js', '.jsx']);
const SKIP_DIRS = new Set(['node_modules', 'dist', '.git', 'scripts']);

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    if (SKIP_DIRS.has(name)) continue;
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (SOURCE_EXT.has(extname(name))) out.push(full);
  }
  return out;
}

const files = [
  ...walk(resolve(root, 'src')),
  resolve(root, 'index.html'),
  ...(existsSync(OUT_DIR) ? walk(OUT_DIR) : []),
];

const failures = [];
for (const file of files) {
  const text = readFileSync(file, 'utf8');
  const lines = text.split('\n');
  for (const [pattern, why] of RULES) {
    lines.forEach((line, index) => {
      // A rule may name itself inside a comment explaining why it is banned.
      const trimmed = line.trim();
      if (trimmed.startsWith('*') || trimmed.startsWith('//') || trimmed.startsWith('/*')) return;
      if (!pattern.test(line)) return;
      failures.push({ file: relative(root, file), line: index + 1, why, text: trimmed.slice(0, 120) });
    });
  }
}

if (failures.length > 0) {
  console.error('\nUltralight constraint check FAILED\n');
  for (const failure of failures) {
    console.error(`  ${failure.file}:${failure.line}`);
    console.error(`    ${failure.why}`);
    console.error(`    ${failure.text}\n`);
  }
  console.error(`${failures.length} violation${failures.length === 1 ? '' : 's'}.\n`);
  process.exit(1);
}

console.log(`Ultralight constraint check passed — ${files.length} files, ${RULES.length} rules.`);
