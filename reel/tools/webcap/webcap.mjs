#!/usr/bin/env node
// webcap — frame-exact 4K capture of the VOID launcher's web UI. See README.md.

import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { parseArgs } from 'node:util';

import { captureLayers } from './lib/layers.mjs';
import { decodePNG, diff, encodePNG, over } from './lib/png.mjs';
import { SCREENS } from './lib/screens.mjs';
import { openSession, runTimeline } from './lib/session.mjs';

const USAGE = `webcap <command> [options]

  still   --out shot.png   [--at 2.5] [--transparent]
  seq     --out dir/       --fps 30 --seconds 4 [--trace ".a,.b"]
  layers  --out dir/       --layers spec.json [--at 2.5] [--crop] [--no-promote] [--strip-ancestors]
  composite dir/                     re-stack a layers dir, diff against full.png
  compare dirA/ dirB/                frame-by-frame determinism check of two seq dirs
  diff a.png b.png                   pixel difference of two PNGs
  screens                            list the names --screen accepts

common: --url http://127.0.0.1:5183/  --screen home[+friends]  --nav-via store|click  --nav-transitions
        --actions timeline.json  --size 1920x1080  --scale 2  --preroll 0
        --seed 1  --epoch 2026-10-24T12:00:00Z  --tz Europe/London
        --keep-banner  --gpu  --headful  --chrome <path>  --chrome-args="--flag ..."  --tick 60
        --slow <ms>  (seq self-test: really wait between frames; output must not change)`;

const { positionals, values: o } = parseArgs({
  allowPositionals: true,
  options: {
    out: { type: 'string', short: 'o' },
    url: { type: 'string', default: 'http://127.0.0.1:5183/' },
    screen: { type: 'string' },
    'nav-via': { type: 'string', default: 'store' },
    'nav-transitions': { type: 'boolean', default: false },
    actions: { type: 'string' },
    size: { type: 'string', default: '1920x1080' },
    scale: { type: 'string', default: '2' },
    fps: { type: 'string', default: '30' },
    seconds: { type: 'string', default: '4' },
    frames: { type: 'string' },
    preroll: { type: 'string', default: '0' },
    at: { type: 'string', default: '2.5' },
    tick: { type: 'string', default: '60' },
    seed: { type: 'string', default: '1' },
    epoch: { type: 'string', default: '2026-10-24T12:00:00Z' },
    tz: { type: 'string' },
    layers: { type: 'string' },
    crop: { type: 'boolean', default: false },
    'strip-ancestors': { type: 'boolean', default: false },
    'no-promote': { type: 'boolean', default: false },
    transparent: { type: 'boolean', default: false },
    trace: { type: 'string' },
    'keep-banner': { type: 'boolean', default: false },
    headful: { type: 'boolean', default: false },
    gpu: { type: 'boolean', default: false },
    chrome: { type: 'string' },
    'chrome-args': { type: 'string', default: '' },
    threshold: { type: 'string', default: '2' },
    slow: { type: 'string', default: '0' },
    help: { type: 'boolean', short: 'h' },
  },
});

const cmd = positionals[0];
const log = (...a) => console.error('[webcap]', ...a);

function readJSON(path) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch (e) {
    throw new Error(`cannot read ${path}: ${e.message}`);
  }
}

function sessionOpts() {
  const [w, h] = o.size.split('x').map(Number);
  if (!w || !h) throw new Error(`--size must look like 1920x1080, not ${o.size}`);
  const epoch = Date.parse(o.epoch);
  if (Number.isNaN(epoch)) throw new Error(`--epoch is not a date: ${o.epoch}`);
  return {
    url: o.url,
    width: w,
    height: h,
    scale: Number(o.scale),
    seed: Number(o.seed),
    epoch,
    tz: o.tz,
    chrome: o.chrome,
    chromeArgs: o['chrome-args'].split(/\s+/).filter(Boolean),
    headful: o.headful,
    gpu: o.gpu,
    clean: !o['keep-banner'],
    log,
  };
}

const actions = () => (o.actions ? readJSON(o.actions) : []);

/** Open, go to --screen at virtual t=0, return the session. */
async function prepare() {
  const s = await openSession(sessionOpts());
  if (o.screen) {
    await s.nav(o.screen, o['nav-via']);
    // The screen is "already up" at t = 0: state-change transitions the navigation started
    // (the navbar's tab highlight) are ended; the new screen's mount animations still play.
    if (!o['nav-transitions']) {
      await s.eval('__webcap.endTransitions()');
      await s.settle();
    }
  }
  return s;
}

function report(s) {
  const p = [...new Set(s.problems)].filter((m) => !/favicon\.ico/.test(m));
  if (p.length) log(`page reported ${p.length} problem(s):\n  ${p.slice(0, 10).join('\n  ')}`);
}

/** Instants from `a` to `b` seconds at --tick Hz, both ends included. */
function ticks(a, b) {
  const hz = Number(o.tick);
  const n = Math.max(0, Math.round((b - a) * hz));
  return Array.from({ length: n + 1 }, (_, i) => (i === n ? b : a + i / hz));
}

/** Run the preroll at --tick Hz with no actions, so rAF code sees a normal cadence. */
async function runPreroll(s) {
  const preroll = Number(o.preroll);
  if (preroll > 0) await runTimeline(s, { times: ticks(0, preroll) });
}

/** Step at --tick Hz from 0 to `at` (after the preroll), applying the actions on the way. */
async function stepTo(s, at) {
  await runPreroll(s);
  await runTimeline(s, { times: ticks(0, at), actions: actions(), offset: Number(o.preroll) });
}

const sha = (buf) => createHash('sha256').update(buf).digest('hex');

async function still() {
  if (!o.out) throw new Error('still needs --out file.png');
  mkdirSync(dirname(resolve(o.out)), { recursive: true });
  const s = await prepare();
  try {
    await stepTo(s, Number(o.at));
    if (o.transparent) {
      await s.setTransparent(true);
      await s.eval('__webcap.freezeNew()');
    }
    const png = await s.screenshot();
    writeFileSync(o.out, png);
    const { width, height } = decodePNG(png);
    log(`wrote ${o.out} (${width}x${height}, t=${o.at}s)`);
    report(s);
  } finally {
    await s.close();
  }
}

async function seq() {
  if (!o.out) throw new Error('seq needs --out dir');
  mkdirSync(o.out, { recursive: true });
  const fps = Number(o.fps);
  const count = o.frames ? Number(o.frames) : Math.round(Number(o.seconds) * fps);
  const preroll = Number(o.preroll);
  const trace = o.trace ? o.trace.split(',').map((x) => x.trim()).filter(Boolean) : null;
  const s = await prepare();
  const frames = [];
  const t0 = Date.now();
  try {
    // Frame i is at exactly i/fps seconds; the clock never runs between them.
    const times = Array.from({ length: count }, (_, i) => i / fps);
    await runPreroll(s);
    await runTimeline(s, {
      times,
      actions: actions(),
      offset: preroll,
      onFrame: async (i, t, info) => {
        const png = await s.screenshot();
        const file = `frame_${String(i).padStart(5, '0')}.png`;
        writeFileSync(join(o.out, file), png);
        const f = { i, t, file, sha256: sha(png), animations: info.animations };
        if (trace) f.trace = await s.probe(trace);
        frames.push(f);
        // Self-test: real time passing between frames must not change a single pixel.
        if (Number(o.slow) > 0) await new Promise((r) => setTimeout(r, Number(o.slow)));
        if (i % 10 === 0 || i === count - 1) log(`frame ${i + 1}/${count}  t=${t.toFixed(3)}s  ${((Date.now() - t0) / (i + 1)).toFixed(0)} ms/frame`);
      },
    });
    const meta = {
      url: o.url,
      screen: o.screen ?? null,
      fps,
      frames: count,
      seconds: count / fps,
      preroll,
      viewport: [s.width, s.height],
      scale: s.scale,
      actions: actions(),
      seed: Number(o.seed),
      epoch: o.epoch,
      chrome: s.version,
      list: frames,
    };
    writeFileSync(join(o.out, 'sequence.json'), JSON.stringify(meta, null, 2));
    log(`wrote ${count} frames to ${o.out} in ${((Date.now() - t0) / 1000).toFixed(1)} s`);
    report(s);
  } finally {
    await s.close();
  }
}

async function layers() {
  if (!o.out || !o.layers) throw new Error('layers needs --out dir and --layers spec.json');
  mkdirSync(o.out, { recursive: true });
  const spec = readJSON(o.layers);
  const s = await prepare();
  try {
    await stepTo(s, Number(o.at));
    const doc = await captureLayers(s, spec, o.out, {
      crop: o.crop,
      stripAncestors: o['strip-ancestors'],
      promote: o['no-promote'] ? '' : 'opacity',
      meta: { url: o.url, screen: o.screen ?? null, time: Number(o.at), chrome: s.version },
    });
    for (const l of doc.layers) log(`${l.name.padEnd(12)} ${l.elements} el  bbox ${l.bbox ? `${l.bbox.x},${l.bbox.y} ${l.bbox.w}x${l.bbox.h}` : 'empty'}`);
    log(`wrote ${doc.layers.length} layers + full.png + layers.json to ${o.out}`);
    report(s);
  } finally {
    await s.close();
  }
}

function composite() {
  const dir = positionals[1];
  if (!dir) throw new Error('composite needs a layers dir');
  const doc = readJSON(join(dir, 'layers.json'));
  const base = { width: doc.width, height: doc.height, data: new Uint8Array(doc.width * doc.height * 4) };
  for (const l of [...doc.layers].sort((a, b) => a.order - b.order)) over(base, decodePNG(readFileSync(join(dir, l.file))));
  writeFileSync(join(dir, 'composite.png'), encodePNG(base));
  const d = diff(base, decodePNG(readFileSync(join(dir, doc.full))), Number(o.threshold));
  writeFileSync(join(dir, 'composite.json'), JSON.stringify(d, null, 2));
  console.log(JSON.stringify(d, null, 2));
}

function compare() {
  const [a, b] = positionals.slice(1);
  if (!a || !b) throw new Error('compare needs two seq dirs');
  const list = (d) => readdirSync(d).filter((f) => /^frame_\d+\.png$/.test(f)).sort();
  const fa = list(a);
  const fb = list(b);
  const rows = [];
  let same = 0;
  for (const f of fa) {
    if (!fb.includes(f)) {
      rows.push({ frame: f, missing: true });
      continue;
    }
    const ba = readFileSync(join(a, f));
    const bb = readFileSync(join(b, f));
    if (sha(ba) === sha(bb)) {
      same++;
      continue;
    }
    rows.push({ frame: f, ...diff(decodePNG(ba), decodePNG(bb), Number(o.threshold)) });
  }
  const res = { frames: fa.length, identical: same, different: rows.length, extraInB: fb.filter((f) => !fa.includes(f)).length, rows };
  console.log(JSON.stringify(res, null, 2));
  if (rows.length) process.exitCode = 1;
}

function diffCmd() {
  const [a, b] = positionals.slice(1);
  console.log(JSON.stringify(diff(decodePNG(readFileSync(a)), decodePNG(readFileSync(b)), Number(o.threshold)), null, 2));
}

const COMMANDS = { still, seq, layers, composite, compare, diff: diffCmd, screens: () => console.log(Object.entries(SCREENS).map(([k, v]) => `${k.padEnd(36)} ${v}`).join('\n')) };

if (o.help || !COMMANDS[cmd]) {
  console.log(USAGE);
  process.exit(o.help ? 0 : 1);
}
if (o.out && !existsSync(dirname(resolve(o.out)))) mkdirSync(dirname(resolve(o.out)), { recursive: true });
try {
  await COMMANDS[cmd]();
} catch (e) {
  log(`error: ${e.message}`);
  process.exitCode = 1;
}
