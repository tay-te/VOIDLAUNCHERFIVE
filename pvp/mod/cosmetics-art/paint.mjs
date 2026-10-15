#!/usr/bin/env node
/**
 * Paints every launch cosmetic's sources into `mod/src/main/cosmetics-src/<id>/`.
 *
 *   node mod/cosmetics-art/paint.mjs            # (from pvp/) repaint everything
 *   node mod/cosmetics-art/paint.mjs cell-cape  # repaint one item
 *
 * Then `node scripts/gen-cosmetics.mjs` compiles the sources into the jar's assets. The two steps
 * are separate on purpose: the sources are the artwork, committed, and can be edited by hand or
 * in Blockbench without ever running this painter again.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { paintCapes } from './capes.mjs';
import { paintModels } from './models.mjs';
import { paintBadges, paintEmotes, paintTrails } from './motion.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const SRC = join(HERE, '..', 'src', 'main', 'cosmetics-src');
const only = process.argv.slice(2);

let n = 0;
function save(id, file, content) {
  if (only.length && !only.includes(id)) return;
  const path = join(SRC, id, file);
  mkdirSync(dirname(path), { recursive: true });
  if (file.endsWith('.png')) writeFileSync(path, content.png());
  else writeFileSync(path, `${JSON.stringify(content, null, 1)}\n`);
  n += 1;
}

paintCapes(save);
paintModels(save);
paintEmotes(save);
paintTrails(save);
paintBadges(save);
console.log(`paint: ${n} file(s) under ${SRC}`);
