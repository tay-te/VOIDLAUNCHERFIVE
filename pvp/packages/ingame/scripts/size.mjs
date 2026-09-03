#!/usr/bin/env node
/**
 * Bundle budget: **≤ 400 KB gzipped total** (PVP_ARCHITECTURE.md §10, and the
 * `packages/ingame → mod` seam in CONTRACTS.md). Exits 1 over budget.
 *
 * Measures the whole emitted directory, gzipped file by file — the JAR carries
 * every one of these bytes, fonts included.
 */

import { gzipSync } from 'node:zlib';
import { readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');
const OUT_DIR =
  process.env.VOID_UI_OUT ?? resolve(root, '../../mod/src/main/resources/assets/void/ui');

const BUDGET_BYTES = 400 * 1024;

if (!existsSync(OUT_DIR)) {
  console.error(`size: nothing built at ${OUT_DIR}. Run \`pnpm build\` first.`);
  process.exit(1);
}

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

const files = walk(OUT_DIR)
  .filter((file) => !file.endsWith('README.md'))
  .map((file) => {
    const raw = readFileSync(file);
    return {
      name: relative(OUT_DIR, file),
      raw: raw.length,
      // Already-compressed payloads (woff2) do not shrink; gzip them anyway so
      // the number is what the JAR + transport actually costs.
      gz: gzipSync(raw, { level: 9 }).length,
    };
  })
  .sort((a, b) => b.gz - a.gz);

const totalRaw = files.reduce((sum, file) => sum + file.raw, 0);
const totalGz = files.reduce((sum, file) => sum + file.gz, 0);
const kb = (bytes) => `${(bytes / 1024).toFixed(1)} KB`;

const width = Math.max(...files.map((file) => file.name.length), 12);
console.log(`\n  ${'file'.padEnd(width)}   ${'raw'.padStart(10)}   ${'gzip'.padStart(10)}`);
console.log(`  ${'-'.repeat(width)}   ${'-'.repeat(10)}   ${'-'.repeat(10)}`);
for (const file of files) {
  console.log(`  ${file.name.padEnd(width)}   ${kb(file.raw).padStart(10)}   ${kb(file.gz).padStart(10)}`);
}
console.log(`  ${'-'.repeat(width)}   ${'-'.repeat(10)}   ${'-'.repeat(10)}`);
console.log(`  ${'TOTAL'.padEnd(width)}   ${kb(totalRaw).padStart(10)}   ${kb(totalGz).padStart(10)}`);

const percent = ((totalGz / BUDGET_BYTES) * 100).toFixed(1);
if (totalGz > BUDGET_BYTES) {
  console.error(`\n  OVER BUDGET: ${kb(totalGz)} gzipped, budget ${kb(BUDGET_BYTES)} (${percent}%).\n`);
  process.exit(1);
}
console.log(`\n  Within budget: ${kb(totalGz)} of ${kb(BUDGET_BYTES)} gzipped (${percent}%).\n`);
