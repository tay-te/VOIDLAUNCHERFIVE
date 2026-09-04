/**
 * Diff each captured shot against its Figma frame and write the side-by-sides.
 *
 * Output per scored screen: `<id>.png` — design | render | diff, three 1300 × 820
 * panels side by side with a caption band, which is the artefact a designer reads.
 * The raw captures stay in `out/` and are gitignored.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { DESIGN, HERE, need, OUT, SHOTS, UI_REGIONS, VIEWPORT } from './lib.mjs';

const { PNG } = await need('pngjs');
const pixelmatch = (await need('pixelmatch')).default;

const TAG = process.argv[2] ?? 'after';
const readPng = (file) => PNG.sync.read(readFileSync(file));

/** `mismatch` is the share of pixels pixelmatch flags at the default 0.1 threshold. */
export function diff(designFile, renderFile, regions) {
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

  // The same diff, counted only inside the UI rectangles. `out` already marks every
  // flagged pixel with `diffColor`, so this is a tally rather than a second pass — and
  // overlapping rectangles are counted once, because the tally is per pixel.
  let uiTotal = 0;
  let uiDiffering = 0;
  if (regions) {
    const seen = new Uint8Array(width * height);
    for (const [rx, ry, rw, rh] of regions) {
      for (let y = ry; y < Math.min(ry + rh, height); y += 1) {
        for (let x = rx; x < Math.min(rx + rw, width); x += 1) {
          const at = y * width + x;
          if (seen[at]) continue;
          seen[at] = 1;
          uiTotal += 1;
          const i = at * 4;
          if (out.data[i] === 255 && out.data[i + 1] === 64 && out.data[i + 2] === 128) {
            uiDiffering += 1;
          }
        }
      }
    }
  }

  return {
    out,
    differing,
    pct: (differing / (width * height)) * 100,
    uiPct: uiTotal ? (uiDiffering / uiTotal) * 100 : null,
  };
}

/**
 * Halve a panel by box-averaging 2 × 2 blocks.
 *
 * The sheets are review artefacts that live in the tree, and three 1300 × 820
 * photographic panels come to ~3 MB each at full size. Half scale still shows every
 * geometry, spacing and colour difference at a glance, and the full-resolution
 * captures are in `out/` for anyone who runs the pass.
 */
function halve(src) {
  const width = Math.floor(src.width / 2);
  const height = Math.floor(src.height / 2);
  const out = new PNG({ width, height });
  for (let y = 0; y < height; y += 1) {
    for (let x = 0; x < width; x += 1) {
      for (let c = 0; c < 4; c += 1) {
        const a = src.data[((y * 2) * src.width + x * 2) * 4 + c];
        const b = src.data[((y * 2) * src.width + x * 2 + 1) * 4 + c];
        const d = src.data[((y * 2 + 1) * src.width + x * 2) * 4 + c];
        const e = src.data[((y * 2 + 1) * src.width + x * 2 + 1) * 4 + c];
        out.data[(y * width + x) * 4 + c] = (a + b + d + e) >> 2;
      }
    }
  }
  return out;
}

/** design | render | diff, side by side at half scale on the shell colour. */
function sideBySide(panels) {
  const scaled = panels.map(halve);
  const pw = Math.floor(VIEWPORT.width / 2);
  const ph = Math.floor(VIEWPORT.height / 2);
  const gap = 10;
  const width = scaled.length * pw + (scaled.length + 1) * gap;
  const height = ph + gap * 2;
  const sheet = new PNG({ width, height, fill: true });
  for (let i = 0; i < sheet.data.length; i += 4) {
    sheet.data[i] = 0x0a;
    sheet.data[i + 1] = 0x0b;
    sheet.data[i + 2] = 0x0c;
    sheet.data[i + 3] = 0xff;
  }
  scaled.forEach((png, index) => {
    PNG.bitblt(png, sheet, 0, 0, pw, ph, gap + index * (pw + gap), gap);
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
  const { out, pct, uiPct, differing } = diff(designFile, render, UI_REGIONS[shot.id]);
  // Only the reference pass writes the side-by-side sheets; the others are scores.
  if (TAG === 'after') {
    const sheet = sideBySide([readPng(designFile), readPng(render), out]);
    writeFileSync(path.join(HERE, `${shot.id}.png`), PNG.sync.write(sheet));
  }
  writeFileSync(path.join(OUT, TAG, `${shot.id}.diff.png`), PNG.sync.write(out));
  rows.push({ ...shot, pct, uiPct, differing });
}

writeFileSync(
  path.join(OUT, `${TAG}.json`),
  `${JSON.stringify(
    rows.map(({ id, label, pct, uiPct, differing }) => ({ id, label, pct, uiPct, differing })),
    null,
    2,
  )}\n`,
);

process.stdout.write(`  ${'screen'.padEnd(11)} ${'frame'.padStart(8)} ${'ui only'.padStart(8)}\n`);
for (const row of rows) {
  const full = row.missing ? 'missing' : row.pct === null ? '—' : `${row.pct.toFixed(2)}%`;
  const ui = row.uiPct === null || row.uiPct === undefined ? '—' : `${row.uiPct.toFixed(2)}%`;
  process.stdout.write(`  ${row.id.padEnd(11)} ${full.padStart(8)} ${ui.padStart(8)}\n`);
}
