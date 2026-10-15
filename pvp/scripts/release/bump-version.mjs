#!/usr/bin/env node
// Sets the launcher version everywhere it is written down, so a `pvp-v<version>` tag
// matches what the build reports (the release workflow refuses a mismatch).
//
//   node pvp/scripts/release/bump-version.mjs 0.2.0
//   node pvp/scripts/release/bump-version.mjs 0.2.0-beta.1
//
// Touches: apps/desktop/src-tauri/tauri.conf.json, apps/desktop/src-tauri/Cargo.toml,
// apps/desktop/package.json. The mod's own version (mod/gradle.properties `mod_version`)
// moves independently — bump it when the mod changes.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const version = process.argv[2];
if (!version || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version)) {
  console.error('usage: bump-version.mjs <semver>');
  process.exit(1);
}
const desktop = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'apps', 'desktop');

const conf = path.join(desktop, 'src-tauri', 'tauri.conf.json');
const json = JSON.parse(fs.readFileSync(conf, 'utf8'));
json.version = version;
fs.writeFileSync(conf, JSON.stringify(json, null, 2) + '\n');

const pkg = path.join(desktop, 'package.json');
const p = JSON.parse(fs.readFileSync(pkg, 'utf8'));
p.version = version;
fs.writeFileSync(pkg, JSON.stringify(p, null, 2) + '\n');

const cargo = path.join(desktop, 'src-tauri', 'Cargo.toml');
const text = fs.readFileSync(cargo, 'utf8');
const next = text.replace(/^(\[package\][\s\S]*?^version\s*=\s*)"[^"]*"/m, `$1"${version}"`);
if (next === text && !text.includes(`version = "${version}"`)) {
  console.error(`could not find [package] version in ${cargo}`);
  process.exit(1);
}
fs.writeFileSync(cargo, next);

console.log(`launcher version ${version}: tauri.conf.json, package.json, Cargo.toml (run cargo check to refresh Cargo.lock)`);
