/**
 * Diff each captured shot against its Figma frame and write the side-by-sides.
 *
 * Output per scored screen: `<id>.png` — design | render | diff, three 1300 × 820
 * panels side by side with a caption band, which is the artefact a designer reads.
 * The raw captures stay in `out/` and are gitignored.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { DESIGN, HERE, need, OUT, SHOTS, VIEWPORT } from './lib.mjs';

const { PNG } = await need('pngjs');
const pixelmatch = (await need('pixelmatch')).default;

const TAG = process.argv[2] ?? 'after';
const readPng = (file) => PNG.sync.read(readFileSync(file));

/** `mismatch` is the share of pixels pixelmatch flags at the default 0.1 threshold. */
export function diff(designFile, renderFile) {
  const a = readPng(designFile);
  const b = readPng(renderFile);
  const { width, height } = a;
  const out = new PNG({ width, height });
  const differing = pixelmatch(a.data, b.data, out.data, width, height, {
    threshold: 0.1,
    includeAA: false,
    alpha: 0.15,
    diffColor: [255, 64, 128],
  });
  return { out, differing, pct: (differing / (width * height)) * 100 };
}

/** design | render | diff, with a 22px caption band over each panel. */
function sideBySide(panels) {
  const gap = 12;
  const band = 26;
  const width = panels.length * VIEWPORT.width + (panels.length + 1) * gap;
  const height = VIEWPORT.height + band + gap * 2;
  const sheet = new PNG({ width, height, fill: true });
  for (let i = 0; i < sheet.data.length; i += 4) {
    sheet.data[i] = 0x0a;
    sheet.data[i + 1] = 0x0b;
    sheet.data[i + 2] = 0x0c;
    sheet.data[i + 3] = 0xff;
  }
  panels.forEach((png, index) => {
    PNG.bitblt(
      png,
      sheet,
      0,
      0,
      VIEWPORT.width,
      VIEWPORT.height,
      gap + index * (VIEWPORT.width + gap),
      gap + band,
    );
  });
  return sheet;
}

mkdirSync(OUT, { recursive: true });
const rows = [];

for (const shot of SHOTS) {
  const render = path.join(OUT, TAG, `${shot.id}.png`);
  if (!existsSync(render)) {
    rows.push({ ...shot, missing: true });
    continue;
  }
  if (!shot.design) {
    rows.push({ ...shot, pct: null });
    continue;
  }
  const designFile = path.join(DESIGN, shot.design);
  const { out, pct, differing } = diff(designFile, render);
  const sheet = sideBySide([readPng(designFile), readPng(render), out]);
  writeFileSync(path.join(HERE, `${shot.id}.png`), PNG.sync.write(sheet));
  writeFileSync(path.join(OUT, TAG, `${shot.id}.diff.png`), PNG.sync.write(out));
  rows.push({ ...shot, pct, differing });
}

writeFileSync(
  path.join(OUT, `${TAG}.json`),
  `${JSON.stringify(rows.map(({ id, label, pct, differing }) => ({ id, label, pct, differing })), null, 2)}\n`,
);

for (const row of rows) {
  const value = row.missing ? 'missing' : row.pct === null ? '—' : `${row.pct.toFixed(2)} %`;
  process.stdout.write(`  ${row.id.padEnd(11)} ${value}\n`);
}
