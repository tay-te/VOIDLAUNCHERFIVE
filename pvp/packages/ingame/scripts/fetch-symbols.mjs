#!/usr/bin/env node
/**
 * Build `VOID Symbols` — one small OFL fallback face for the symbols servers put in scoreboards,
 * titles and chat: ✔ ✖ ❤ ★ ⚔ ✪ ➤, arrows, geometric shapes, box drawing and block elements.
 *
 * Geist has none of these, and the native loader's last resort (Inter) lacks the dingbats, so a
 * Bed Wars sidebar's `Red: ✔` drew a tofu box. This face sits after Geist in every font stack
 * (`--c-sans`, `--c-mono`, the scoreboard) and is what `fallback_font_for_characters` answers
 * for those ranges in game (mod/native/src/font_loader.cpp).
 *
 * Sources, all SIL OFL 1.1 from google/fonts: Noto Sans Symbols 2 (dingbats, misc symbols,
 * arrows), Noto Sans Symbols (the few it lacks: ⚔ ⚝ ⚑ ☺ ♪ Ⓒ …) and Noto Sans Mono (box drawing,
 * block elements). Each is instanced to a static 400, cut to the code points it alone supplies,
 * stripped of layout tables, and merged into one TTF (mod) and one woff2 (browser harness).
 *
 *     python -m pip install --user fonttools brotli
 *     node scripts/fetch-symbols.mjs
 *
 * Output is committed; run this only to change the ranges.
 */
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import os from 'node:os';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const pkg = path.resolve(here, '..');
const ttfDir = path.resolve(pkg, '../../mod/src/main/resources/assets/void/fonts');
const woffDir = path.resolve(pkg, 'src/cart/fonts');
const workDir = path.join(os.tmpdir(), 'void-symbols-build');
const RAW = 'https://raw.githubusercontent.com/google/fonts/main';
const PYTHON = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');

const SOURCES = [
  { file: 'NotoSansSymbols2-Regular.ttf', url: `${RAW}/ofl/notosanssymbols2/NotoSansSymbols2-Regular.ttf`, axes: {} },
  { file: 'NotoSansSymbols.ttf', url: `${RAW}/ofl/notosanssymbols/NotoSansSymbols%5Bwght%5D.ttf`, axes: { wght: 400 } },
  { file: 'NotoSansMono.ttf', url: `${RAW}/ofl/notosansmono/NotoSansMono%5Bwdth,wght%5D.ttf`, axes: { wght: 400, wdth: 100 } },
];
const LICENSE = `${RAW}/ofl/notosanssymbols2/OFL.txt`;

/** The ranges servers draw from. Latin and punctuation stay Geist's. */
const RANGES = [
  [0x2190, 0x21ff], // arrows
  [0x2300, 0x23ff], // misc technical
  [0x2460, 0x24ff], // enclosed alphanumerics
  [0x2500, 0x257f], // box drawing
  [0x2580, 0x259f], // block elements
  [0x25a0, 0x25ff], // geometric shapes
  [0x2600, 0x26ff], // misc symbols ☠ ⚔ ☀ ♪
  [0x2700, 0x27bf], // dingbats ✔ ✖ ❤ ✪ ➤
  [0x27f0, 0x27ff], // supplemental arrows A
  [0x2900, 0x297f], // supplemental arrows B
  [0x2b00, 0x2bff], // misc symbols and arrows ⬛ ⭐
];

const PY = String.raw`
import sys, json
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.subset import Subsetter, Options
from fontTools.merge import Merger, Options as MergeOptions

sources, ranges, out_ttf, out_woff = json.loads(sys.argv[1]), json.loads(sys.argv[2]), sys.argv[3], sys.argv[4]
want = set()
for lo, hi in ranges:
    want.update(range(lo, hi + 1))
taken = set()
parts = []
for i, (path, axes) in enumerate(sources):
    font = TTFont(path)
    if axes and 'fvar' in font:
        font = instancer.instantiateVariableFont(font, axes)
    cmap = set(font.getBestCmap().keys())
    mine = sorted((cmap & want) - taken)
    taken.update(mine)
    opts = Options()
    opts.layout_features = []
    opts.drop_tables += ['GSUB', 'GPOS', 'GDEF', 'STAT', 'MATH', 'meta', 'DSIG']
    opts.hinting = False
    opts.notdef_outline = i == 0
    opts.name_IDs = ['*']
    sub = Subsetter(opts)
    sub.populate(unicodes=mine)
    sub.subset(font)
    for t in ['GSUB', 'GPOS', 'GDEF']:
        if t in font: del font[t]
    p = path + '.part.ttf'
    font.save(p)
    parts.append(p)
    print(path, len(mine), 'glyphs')
merged = Merger(options=MergeOptions(drop_tables=['vhea', 'vmtx'])).merge(parts)
name = merged['name']
for rec in list(name.names):
    if rec.nameID in (1, 4, 16):
        name.setName('VOID Symbols', rec.nameID, rec.platformID, rec.platEncID, rec.langID)
    elif rec.nameID == 6:
        name.setName('VOIDSymbols-Regular', rec.nameID, rec.platformID, rec.platEncID, rec.langID)
merged['OS/2'].usWeightClass = 400
merged.save(out_ttf)
merged.flavor = 'woff2'
merged.save(out_woff)
print('total', len(taken))
`;

mkdirSync(workDir, { recursive: true });
mkdirSync(ttfDir, { recursive: true });
mkdirSync(woffDir, { recursive: true });

async function download(url, dest) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText} for ${url}`);
  writeFileSync(dest, Buffer.from(await response.arrayBuffer()));
}

for (const s of SOURCES) await download(s.url, path.join(workDir, s.file));
const licence = await (await fetch(LICENSE)).text();

const outTtf = path.join(ttfDir, 'void-symbols.ttf');
const outWoff = path.join(woffDir, 'void-symbols.woff2');
execFileSync(
  PYTHON,
  ['-P', '-c', PY, JSON.stringify(SOURCES.map((s) => [path.join(workDir, s.file), s.axes])), JSON.stringify(RANGES), outTtf, outWoff],
  { stdio: 'inherit' },
);
const notice = `VOID Symbols is a subset and merge of Noto Sans Symbols 2, Noto Sans Symbols and Noto Sans Mono,\nCopyright The Noto Project Authors, licensed under the SIL Open Font License 1.1:\n\n${licence}`;
writeFileSync(path.join(ttfDir, 'OFL-noto.txt'), notice);
writeFileSync(path.join(woffDir, 'OFL-noto.txt'), notice);
for (const f of [outTtf, outWoff]) console.log(`${path.relative(pkg, f)}  ${statSync(f).size} bytes`);
