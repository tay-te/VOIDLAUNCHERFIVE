#!/usr/bin/env node
// Builds, signs and verifies the void-client release manifest (`mod-manifest.json`).
//
// The launcher (`void_core::release`) downloads this file from the channel release
// (`pvp-stable` / `pvp-beta`), checks its ed25519 signature against the public key
// compiled into the launcher (`crates/void-core/keys/mod-manifest.pub`), and only then
// trusts the per-platform JAR URLs and SHA-256s inside it.
//
// The file is an envelope so the signed bytes are exactly the parsed bytes:
//   { "payload": "<manifest JSON as a string>", "signature": "<base64 ed25519 over payload>" }
//
// Sign (CI, `.github/workflows/pvp-release.yml`):
//   VOID_MOD_MANIFEST_KEY="$(cat mod-manifest-ed25519.pem)" \
//   node pvp/scripts/release/mod-manifest.mjs sign \
//     --jars dist/jars --channel stable --min-launcher 0.2.0 \
//     --base-url https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download/pvp-v0.2.0 \
//     --out dist/mod-manifest.json
//
// Verify (anywhere; defaults to the committed public key):
//   node pvp/scripts/release/mod-manifest.mjs verify dist/mod-manifest.json
//
// No dependencies: Node's own crypto does ed25519.

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const pvp = path.resolve(here, '..', '..');
const PUBLIC_KEY_FILE = path.join(pvp, 'crates', 'void-core', 'keys', 'mod-manifest.pub');
const PROTOCOL_SCHEMA = path.join(pvp, 'schema', 'protocol.json');
const GRADLE_PROPERTIES = path.join(pvp, 'mod', 'gradle.properties');
const PLATFORMS = ['windows-x64', 'macos-x64', 'macos-arm64', 'linux-x64'];
/** Platforms a release must carry; the launcher refuses to run where the JAR is missing. */
const REQUIRED = ['windows-x64', 'macos-x64'];

function die(msg) {
  console.error(`mod-manifest: ${msg}`);
  process.exit(1);
}

function args(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i += 1) {
    const a = argv[i];
    if (a.startsWith('--')) {
      const key = a.slice(2);
      const next = argv[i + 1];
      if (next === undefined || next.startsWith('--')) out[key] = true;
      else {
        out[key] = next;
        i += 1;
      }
    } else out._.push(a);
  }
  return out;
}

function gradleProperty(name) {
  const text = fs.readFileSync(GRADLE_PROPERTIES, 'utf8');
  const line = text.split(/\r?\n/).find((l) => l.split('=')[0]?.trim() === name);
  return line ? line.split('=').slice(1).join('=').trim() : undefined;
}

function protocolVersion() {
  const schema = JSON.parse(fs.readFileSync(PROTOCOL_SCHEMA, 'utf8'));
  const v = schema?.definitions?.protocol_version?.const;
  if (!Number.isInteger(v)) die(`${PROTOCOL_SCHEMA} has no definitions.protocol_version.const`);
  return v;
}

function publicKey(b64) {
  const raw = Buffer.from(b64.trim(), 'base64');
  if (raw.length !== 32) die('the public key must be 32 raw bytes, base64');
  return crypto.createPublicKey({ key: { kty: 'OKP', crv: 'Ed25519', x: raw.toString('base64url') }, format: 'jwk' });
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function sign(opts) {
  const pem = process.env[opts['key-env'] ?? 'VOID_MOD_MANIFEST_KEY']
    ?? (opts['key-file'] ? fs.readFileSync(opts['key-file'], 'utf8') : undefined);
  if (!pem) die('no private key: set VOID_MOD_MANIFEST_KEY (PEM) or pass --key-file');
  const key = crypto.createPrivateKey(pem);

  const jarsDir = opts.jars ?? die('--jars <dir> is required');
  const baseUrl = (opts['base-url'] ?? die('--base-url <release download url> is required')).replace(/\/+$/, '');
  const modVersion = opts['mod-version'] ?? gradleProperty('mod_version') ?? die('no mod version');
  const channel = opts.channel === 'beta' ? 'beta' : 'stable';
  const minLauncher = opts['min-launcher'] ?? die('--min-launcher <launcher version> is required');

  const jars = {};
  for (const platform of PLATFORMS) {
    const name = `void-client-${modVersion}-${platform}.jar`;
    const file = path.join(jarsDir, name);
    if (!fs.existsSync(file)) continue;
    jars[platform] = { name, url: `${baseUrl}/${name}`, sha256: sha256(file), size: fs.statSync(file).size };
  }
  const missing = REQUIRED.filter((p) => !jars[p]);
  if (missing.length && !opts['allow-missing']) die(`no JAR for ${missing.join(', ')} in ${jarsDir}`);

  const manifest = {
    schema: 1,
    channel,
    mod_version: modVersion,
    protocol_version: opts.protocol ? Number(opts.protocol) : protocolVersion(),
    min_launcher_version: minLauncher,
    published_at: new Date().toISOString(),
    jars,
  };
  const payload = JSON.stringify(manifest);
  const signature = crypto.sign(null, Buffer.from(payload, 'utf8'), key).toString('base64');
  const envelope = JSON.stringify({ payload, signature }, null, 2) + '\n';

  const out = opts.out ?? 'mod-manifest.json';
  fs.writeFileSync(out, envelope);
  console.log(`mod-manifest: signed ${Object.keys(jars).join(', ')} (void-client ${modVersion}, protocol ${manifest.protocol_version}) -> ${out}`);

  // Check the signature against the *committed* public key too: signing with a key the
  // launcher does not carry would publish a manifest every launcher rejects.
  verify(out, opts.pub);
}

function verify(file, pubOverride) {
  const pub = pubOverride ?? fs.readFileSync(PUBLIC_KEY_FILE, 'utf8');
  const envelope = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (typeof envelope.payload !== 'string' || typeof envelope.signature !== 'string') die(`${file} is not an envelope`);
  const ok = crypto.verify(null, Buffer.from(envelope.payload, 'utf8'), publicKey(pub), Buffer.from(envelope.signature, 'base64'));
  if (!ok) die(`${file}: the signature does not match the launcher's public key (${PUBLIC_KEY_FILE})`);
  const m = JSON.parse(envelope.payload);
  console.log(`mod-manifest: ${file} verified — void-client ${m.mod_version}, protocol ${m.protocol_version}, min launcher ${m.min_launcher_version}, ${Object.keys(m.jars).join(', ')}`);
  return m;
}

const opts = args(process.argv.slice(2));
const cmd = opts._[0];
if (cmd === 'sign') sign(opts);
else if (cmd === 'verify') verify(opts._[1] ?? die('verify <file>'), opts.pub);
else die('usage: mod-manifest.mjs sign --jars <dir> --base-url <url> --min-launcher <v> [--channel beta] [--out file] | verify <file>');
