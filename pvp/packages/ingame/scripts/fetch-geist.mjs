#!/usr/bin/env node
/**
 * Fetch and build the Geist / Geist Mono faces for the in-game "Cartridge" UI.
 *
 * The source is the OFL original in the `google/fonts` repository (Geist and Geist Mono
 * are SIL OFL 1.1, by Vercel). Both ship as variable fonts; `ultralight-notes.md` §7 says
 * Ultralight loads **static instances** reliably and variable axes much less so, so each
 * weight is instanced to a fixed `wght` before it is subset — the same recipe as
 * `packages/ui/scripts/fetch-fonts.mjs`, whose UNICODES string this extends with the
 * arrows, keycap symbols and markers the Cartridge design draws as text.
 *
 * Two outputs per face, for two renderers:
 *
 *   - **TTF** into `mod/src/main/resources/assets/void/fonts/geist[-mono]-NNN.ttf`. This is
 *     the one that matters: the game's font loader (`mod/native/src/font_loader.cpp`) hands
 *     the file to FreeType, which reads TTF and not woff2, and maps family+weight to these
 *     names by hand in `kFaces`. build.gradle republishes the directory next to the UI
 *     bundle, OFL-geist.txt included.
 *   - **woff2** into `src/cart/fonts/` plus `src/cart/fonts-dev.css`, for the browser
 *     harness only. Ultralight never fetches an @font-face (every family resolves through
 *     the native loader), so the woff2 must stay out of the production bundle — the
 *     stylesheet is imported in dev mode only, by hand, and never from shipped code.
 *
 * Kerning and the rest of GPOS/GSUB are kept (`layout_features = ['*']`). Hinting is
 * dropped, though upstream Geist is effectively unhinted already (no fpgm, a stub prep),
 * so that only saves a few hundred bytes per face.
 *
 * Coverage gap, measured on the upstream files: Geist has no glyph for ⌃ ⌘ ⌥ ◆ ★ ♥ ✓ ✕
 * (and Geist Sans none for ⌫). Those code points are asked for but simply absent, so the
 * subset cannot contain them; Ultralight then asks the loader's
 * fallback_font_for_characters, which serves Inter — Inter has all of them except ✕
 * (U+2715). Use × (U+00D7, present in Geist) or a sprite where ✕ was meant.
 *
 * The output is committed; this script only needs to run when a face is added or the
 * upstream font is updated. Requires Python 3 with `fonttools` and `brotli`:
 *
 *     python -m pip install --user fonttools brotli
 *     node scripts/fetch-geist.mjs            (set PYTHON=… if `python3`/`python` is wrong)
 */
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync, statSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const pkg = path.resolve(here, '..');
const ttfDir = path.resolve(pkg, '../../mod/src/main/resources/assets/void/fonts');
const woffDir = path.resolve(pkg, 'src/cart/fonts');
const devCss = path.resolve(pkg, 'src/cart/fonts-dev.css');
const workDir = path.join(os.tmpdir(), 'void-geist-build');
const RAW = 'https://raw.githubusercontent.com/google/fonts/main';
const PYTHON = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');

/** fetch-fonts.mjs's set, plus the Cartridge glyphs (arrows, ↵ ⇧ − ⌃ ⌘ ⌫ ⌥ ▲ ▼ ● ◆ ★ ♥ ✓ ✕ ×). */
const UNICODES =
  'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,' +
  'U+0304,U+0308,U+0329,U+2000-206F,U+2074,U+20AC,U+2122,U+2191-2193,' +
  'U+2200,U+2202,U+2206,U+220F,U+2211,U+2215,U+2219-221A,U+221E,U+2248,' +
  'U+2260,U+2264-2265,U+2318,U+2325,U+23CE,U+25CA,U+2713,U+FEFF,U+FFFD,' +
  'U+2190-2199,U+21B5,U+21E7,U+2212,U+2303,U+232B,U+25B2,U+25BC,U+25CF,' +
  'U+25C6,U+2605,U+2665,U+2715,U+00D7';

/**
 * Geist 400/500/600/700, Geist Mono 400/500/600. Keep in step with `kFaces` in
 * mod/native/src/font_loader.cpp: a weight in one place and not the other is a silent
 * divergence (the loader matches the nearest weight it has, it never errors).
 */
const FAMILIES = [
  { dir: 'ofl/geist', src: 'Geist[wght].ttf', css: 'Geist', stem: 'geist', weights: [400, 500, 600, 700] },
  { dir: 'ofl/geistmono', src: 'GeistMono[wght].ttf', css: 'Geist Mono', stem: 'geist-mono', weights: [400, 500, 600] },
];

mkdirSync(ttfDir, { recursive: true });
mkdirSync(woffDir, { recursive: true });
mkdirSync(workDir, { recursive: true });

async function download(url, dest) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${url}`);
  writeFileSync(dest, Buffer.from(await response.arrayBuffer()));
}

const PY = `
import sys, json
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.subset import Subsetter, Options, parse_unicodes

src, ttf_dest, woff_dest, axes_json, unicodes = sys.argv[1:6]
font = TTFont(src)
font = instancer.instantiateVariableFont(font, json.loads(axes_json), inplace=False, updateFontNames=True)
options = Options()
options.layout_features = ['*']
options.name_IDs = ['*']
options.name_legacy = True
options.notdef_outline = True
options.recalc_bounds = True
options.hinting = False
options.drop_tables = ['DSIG']
subsetter = Subsetter(options=options)
subsetter.populate(unicodes=parse_unicodes(unicodes))
subsetter.subset(font)
font.flavor = None
font.save(ttf_dest)
font.flavor = 'woff2'
font.save(woff_dest)
`;
const pyFile = path.join(workDir, 'build_face.py');
writeFileSync(pyFile, PY);

const rel = (p) => path.relative(path.resolve(pkg, '../..'), p).replaceAll('\\', '/');
const report = (p) => process.stdout.write(`${String(statSync(p).size).padStart(8)}  ${rel(p)}\n`);

// One licence covers both families (same copyright line, same OFL text upstream).
const license = path.join(woffDir, 'OFL-geist.txt');
await download(`${RAW}/ofl/geist/OFL.txt`, license);
copyFileSync(license, path.join(ttfDir, 'OFL-geist.txt'));
report(license);
report(path.join(ttfDir, 'OFL-geist.txt'));

const css = [
  '/*',
  ' * Geist / Geist Mono for the browser dev harness ONLY. Generated by',
  ' * scripts/fetch-geist.mjs — do not edit by hand.',
  ' *',
  ' * Never import this from shipped code: in the game every family resolves through the',
  ' * native font loader (mod/native/src/font_loader.cpp), which serves the TTF copies from',
  ' * the mod jar. Pulling these woff2 into the Ultralight bundle would only cost budget.',
  ' * SIL OFL 1.1 — see ./fonts/OFL-geist.txt.',
  ' */',
];

for (const family of FAMILIES) {
  const source = path.join(workDir, family.src);
  if (!existsSync(source)) {
    await download(`${RAW}/${family.dir}/${encodeURIComponent(family.src)}`, source);
  }
  for (const weight of family.weights) {
    const name = `${family.stem}-${weight}`;
    const ttf = path.join(ttfDir, `${name}.ttf`);
    const woff = path.join(woffDir, `${name}.woff2`);
    execFileSync(PYTHON, ['-P', pyFile, source, ttf, woff, JSON.stringify({ wght: weight }), UNICODES], {
      stdio: ['ignore', 'ignore', 'inherit'],
    });
    report(ttf);
    report(woff);
    css.push(
      '',
      '@font-face {',
      `  font-family: '${family.css}';`,
      '  font-style: normal;',
      `  font-weight: ${weight};`,
      '  font-display: block;',
      `  src: url('./fonts/${name}.woff2') format('woff2');`,
      '}',
    );
  }
}

writeFileSync(devCss, css.join('\n') + '\n');
report(devCss);
