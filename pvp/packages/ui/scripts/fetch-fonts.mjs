#!/usr/bin/env node
/**
 * Fetch and build the bundled webfonts in `src/fonts/`.
 *
 * Sources are the OFL originals in the `google/fonts` repository. Two of the three
 * families ship as variable fonts; `ultralight-notes.md` §7 says Ultralight loads
 * **static instances** reliably and variable axes much less so, so each weight we use
 * is instanced to a fixed axis location before it is compressed to woff2. The design
 * pins Bricolage Grotesque at `opsz 14, wdth 100, wght 800`, which is exactly the
 * instance produced here — the overlay CSS never needs `font-variation-settings`.
 *
 * The output is committed; this script only needs to run when a family is added or a
 * font is updated. Requires python3 with `fonttools` and `brotli`:
 *
 *     pip install fonttools brotli
 *     node scripts/fetch-fonts.mjs
 */
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const fontsDir = path.resolve(here, '../src/fonts');
const workDir = path.resolve(here, '../.font-build');
const RAW = 'https://raw.githubusercontent.com/google/fonts/main';

/** Latin + Latin-1 + the punctuation the design actually uses (·, ⌘, ↵, ×, →, arrows). */
const UNICODES =
  'U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,' +
  'U+0304,U+0308,U+0329,U+2000-206F,U+2074,U+20AC,U+2122,U+2191-2193,' +
  'U+2200,U+2202,U+2206,U+220F,U+2211,U+2215,U+2219-221A,U+221E,U+2248,' +
  'U+2260,U+2264-2265,U+2318,U+2325,U+23CE,U+25CA,U+2713,U+FEFF,U+FFFD';

/** family dir, source file, output name, axis pins (empty for an already-static face). */
const FACES = [
  {
    dir: 'ofl/outfit',
    src: 'Outfit[wght].ttf',
    out: 'outfit-400.woff2',
    axes: { wght: 400 },
  },
  { dir: 'ofl/outfit', src: 'Outfit[wght].ttf', out: 'outfit-500.woff2', axes: { wght: 500 } },
  { dir: 'ofl/outfit', src: 'Outfit[wght].ttf', out: 'outfit-600.woff2', axes: { wght: 600 } },
  { dir: 'ofl/dmmono', src: 'DMMono-Regular.ttf', out: 'dm-mono-400.woff2', axes: {} },
  { dir: 'ofl/dmmono', src: 'DMMono-Medium.ttf', out: 'dm-mono-500.woff2', axes: {} },
  {
    dir: 'ofl/bricolagegrotesque',
    src: 'BricolageGrotesque[opsz,wdth,wght].ttf',
    out: 'bricolage-grotesque-800.woff2',
    // The design pins `opsz 14, wdth 100` on every display title.
    axes: { opsz: 14, wdth: 100, wght: 800 },
  },
];

const LICENSES = ['ofl/outfit', 'ofl/dmmono', 'ofl/bricolagegrotesque'];

mkdirSync(fontsDir, { recursive: true });
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

src, dest, axes_json, unicodes = sys.argv[1:5]
axes = json.loads(axes_json)
font = TTFont(src)
if axes:
    font = instancer.instantiateVariableFont(font, axes, inplace=False, updateFontNames=True)
options = Options()
options.layout_features = ['*']
options.name_IDs = ['*']
options.name_legacy = True
options.notdef_outline = True
options.recalc_bounds = True
options.drop_tables = []
subsetter = Subsetter(options=options)
subsetter.populate(unicodes=parse_unicodes(unicodes))
subsetter.subset(font)
font.flavor = 'woff2'
font.save(dest)
`;
const pyFile = path.join(workDir, 'build_face.py');
writeFileSync(pyFile, PY);

for (const dir of LICENSES) {
  const family = dir.split('/')[1];
  const dest = path.join(fontsDir, `OFL-${family}.txt`);
  await download(`${RAW}/${dir}/OFL.txt`, dest);
  process.stdout.write(`license  src/fonts/OFL-${family}.txt\n`);
}

for (const face of FACES) {
  const source = path.join(workDir, face.src);
  if (!existsSync(source)) {
    await download(`${RAW}/${face.dir}/${encodeURIComponent(face.src)}`, source);
  }
  const dest = path.join(fontsDir, face.out);
  execFileSync('python3', [pyFile, source, dest, JSON.stringify(face.axes), UNICODES], {
    stdio: ['ignore', 'ignore', 'inherit'],
  });
  process.stdout.write(`built    src/fonts/${face.out}\n`);
}
