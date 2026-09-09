#!/usr/bin/env node
/**
 * Scripted in-game verification of the mod set.
 *
 * ## Why a fake launcher rather than synthetic input
 *
 * Driving the overlay by posting keystrokes at the window server is what this repo already
 * learned not to do: the events go wherever focus happens to be, and a stray one once landed in
 * the operator's terminal. It is also not reproducible — a missed frame changes which setting
 * you actually changed, and you find out by looking at a screenshot that shows the wrong thing.
 *
 * The mod already has a deterministic control channel, and it is the real one. With
 * `-Dvoid.port` set, `VoidSocket` connects to 127.0.0.1 and waits for an `init` frame before
 * applying anything; after that, a `loadout` frame replaces the whole active loadout in-process
 * (`LiveState.applyRemoteLoadout` → `applyActuatorFields`). So this file is a ~200-line launcher
 * that speaks exactly enough of `schema/protocol.json` to push one loadout at a time and take a
 * screenshot between each. Every step is named, and the run is resumable: `--from <step>` picks
 * up where a lost window left off, which matters because the window has been taken mid-run once
 * already.
 *
 * ## What it does not do
 *
 * It does not open the menu or click anything. Everything it verifies is verified through the
 * state the game is actually in, which is the thing under test; the menu is a separate audit.
 *
 * Usage:
 *   node scripts/verify-mods.mjs --port 41999 --out /tmp/voidshots [--from crosshair-dot]
 *   node scripts/verify-mods.mjs --list
 */

import { createServer } from 'node:http';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { argv, exit } from 'node:process';

/**
 * The registry, from `@void/protocol`'s generated tables rather than from a copy here.
 *
 * This file used to carry its own `HUD` placement table and its own per-mod defaults, both
 * annotated "copied from `Loadout.DEFAULT_HUD`". The copy did what copies of a registry do: the
 * fourteenth mod, `direction`, was added to `schema/mods/` and to every generated table, and
 * this audit — the thing whose entire job is to notice that a mod does not work — **never
 * pushed it once**. It was not in `baseMods()`, so it was never on, and it was not in `HUD`, so
 * it had nowhere to draw. A missing mod reads exactly like a passing one: no step, no shot, no
 * failure.
 *
 * Deriving both from the same generated source the client is built from makes that
 * unrepresentable. A mod added to the schema is in the baseline of this audit on the next run,
 * at its own registry defaults and its own factory placement, whether or not anybody remembers
 * this file exists.
 *
 * The import is the built `dist`, so `pnpm --filter @void/protocol build` has to have run —
 * which `scripts/verify-all.sh` does, and a bare `pnpm install` does not.
 */
const PROTOCOL = new URL('../packages/protocol/dist/index.js', import.meta.url);
const { MOD_REGISTRY, DEFAULT_HUD_PLACEMENTS, HUD_MOD_IDS } = await import(PROTOCOL).catch(
  (cause) => {
    throw new Error(
      "@void/protocol is not built — run `pnpm --filter @void/protocol build` first.\n" +
        `  tried: ${PROTOCOL.pathname}\n  cause: ${cause.message}`,
    );
  },
);

/* -------------------------------------------------------------------------- */
/* Minimal RFC6455 server — no dependency, because this package has none       */
/* -------------------------------------------------------------------------- */

const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';

function accept(key) {
  return createHash('sha1').update(key + GUID).digest('base64');
}

/** Encode one text frame. Payloads here are a few KB, so only the 16-bit length case is needed. */
function frame(text) {
  const body = Buffer.from(text, 'utf8');
  if (body.length < 126) {
    return Buffer.concat([Buffer.from([0x81, body.length]), body]);
  }
  if (body.length < 65536) {
    const head = Buffer.alloc(4);
    head[0] = 0x81;
    head[1] = 126;
    head.writeUInt16BE(body.length, 2);
    return Buffer.concat([head, body]);
  }
  const head = Buffer.alloc(10);
  head[0] = 0x81;
  head[1] = 127;
  head.writeBigUInt64BE(BigInt(body.length), 2);
  return Buffer.concat([head, body]);
}

/** Pull whole text frames out of a rolling buffer. Client frames are always masked. */
function drain(state, chunk) {
  state.buf = Buffer.concat([state.buf, chunk]);
  const out = [];
  for (;;) {
    const b = state.buf;
    if (b.length < 2) break;
    const opcode = b[0] & 0x0f;
    const masked = (b[1] & 0x80) !== 0;
    let len = b[1] & 0x7f;
    let off = 2;
    if (len === 126) {
      if (b.length < 4) break;
      len = b.readUInt16BE(2);
      off = 4;
    } else if (len === 127) {
      if (b.length < 10) break;
      len = Number(b.readBigUInt64BE(2));
      off = 10;
    }
    const maskLen = masked ? 4 : 0;
    if (b.length < off + maskLen + len) break;
    const mask = masked ? b.subarray(off, off + 4) : null;
    const payload = Buffer.from(b.subarray(off + maskLen, off + maskLen + len));
    if (mask) for (let i = 0; i < payload.length; i += 1) payload[i] ^= mask[i % 4];
    state.buf = b.subarray(off + maskLen + len);
    if (opcode === 0x1) out.push(payload.toString('utf8'));
    if (opcode === 0x8) out.push(null); // close
  }
  return out;
}

/* -------------------------------------------------------------------------- */
/* Loadout construction                                                       */
/* -------------------------------------------------------------------------- */

/**
 * The factory HUD layout — every HUD mod at its generated `default_placement`.
 *
 * Not a copy of `Loadout.DEFAULT_HUD` any more; the same table that seeds it. A widget with no
 * `hud[]` entry has nowhere to draw, so an omission here is indistinguishable from the mod
 * being broken, which is how `direction` came to be audited zero times.
 */
const HUD = HUD_MOD_IDS.map((id) => ({ id, ...DEFAULT_HUD_PLACEMENTS[id], scale: 1 }));

/**
 * What the baseline says over the registry's own defaults, and why each one earns it.
 *
 * Everything not named here is whatever `MOD_REGISTRY[id].defaults` says, so the audit's
 * starting point is the client's starting point. These six are the exceptions: each turns on
 * something the factory default hides, so that the *baseline shot* shows it rather than needing
 * a step of its own to prove it exists at all.
 */
const BASELINE = {
  // Both hands, so `mode: 'both'` is what the widest form of the tile looks like; `left` gets
  // its own step below.
  cps: { mode: 'both' },
  // Every optional cap on: the baseline is the one shot that has to show the full keyboard.
  keystrokes: { opacity: 0.85, show_spacebar: true, show_sneak: true, show_cps: true, corner_radius: 8 },
  // A decimal place, so `decimals: 0` and `2` are visibly different from the baseline.
  coordinates: { decimals: 1 },
  ping: { good_ms: 60, bad_ms: 150 },
  watermark: { opacity: 0.9 },
};

/**
 * Every mod at its registry defaults, with the HUD ones on so each widget is in shot.
 *
 * `on` is forced for the HUD mods because two of them (`coordinates`, `direction`) ship off,
 * and a widget that is off is a widget this audit cannot see. The gameplay mods keep their own
 * default — they change the *world*, so turning them all on at once would make every later
 * shot a photograph of four mods at the same time.
 */
function baseMods() {
  const mods = {};
  for (const [id, entry] of Object.entries(MOD_REGISTRY)) {
    mods[id] = {
      ...entry.defaults,
      ...(HUD_MOD_IDS.includes(id) ? { on: true } : {}),
      ...(BASELINE[id] ?? {}),
    };
  }
  return mods;
}

/** Deep-merge a step's overrides onto the base loadout. */
function loadout(overrides = {}, hud = HUD) {
  const mods = baseMods();
  for (const [id, patch] of Object.entries(overrides)) {
    mods[id] = { ...mods[id], ...patch };
  }
  return { id: 'audit', name: 'Audit', icon: 'sword', server: null, mc: '1.8.9', mods, hud };
}

/* -------------------------------------------------------------------------- */
/* The steps                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * Each step is `{ name, why, state }`. `state` is the loadout to push; the runner screenshots
 * after it settles. Ordered so the highest-value evidence lands first — the seven widgets
 * appearing at all is the payoff for the `hud` seeding fix, so it is step one and nothing can
 * push it later.
 */
export const STEPS = [
  { name: 'baseline-all-huds', why: 'every HUD widget on, factory placement', state: loadout() },
  { name: 'hud-all-off', why: 'the on switch genuinely gates each widget',
    state: loadout(Object.fromEntries(
      ['fps', 'keystrokes', 'cps', 'ping', 'coordinates', 'armor_status', 'potion_effects',
       'watermark'].map((id) => [id, { on: false }]))) },

  { name: 'fps-color-mint', why: 'fps.color tints the figure and not the unit',
    state: loadout({ fps: { color: '#7AE0B0' } }) },
  { name: 'fps-no-label', why: 'fps.show_label drops the unit',
    state: loadout({ fps: { show_label: false } }) },
  { name: 'fps-scale-2', why: 'scale multiplies the widget',
    state: loadout({ fps: { scale: 2 } }) },
  { name: 'fps-opacity-25', why: 'opacity is applied to the slot',
    state: loadout({ fps: { opacity: 0.25 } }) },

  { name: 'coords-stacked', why: 'coordinates.layout stacked draws three lines',
    state: loadout({ coordinates: { layout: 'stacked' } }) },
  { name: 'coords-decimals-0', why: 'decimals 0 prints whole numbers',
    state: loadout({ coordinates: { decimals: 0 } }) },
  { name: 'coords-decimals-2', why: 'decimals 2 is what the wire carries',
    state: loadout({ coordinates: { decimals: 2 } }) },

  { name: 'armor-vertical', why: 'orientation vertical is a column of full rows',
    state: loadout({ armor_status: { orientation: 'vertical' } }) },
  { name: 'armor-no-durability', why: 'show_durability off drops bar and figure, keeps the label',
    state: loadout({ armor_status: { show_durability: false } }) },

  { name: 'keys-pressed-fear', why: 'pressed_color fear is distinct from warn',
    state: loadout({ keystrokes: { pressed_color: 'fear' } }) },
  { name: 'keys-pressed-warn', why: '…and warn is the other colour, not the same one',
    state: loadout({ keystrokes: { pressed_color: 'warn' } }) },
  { name: 'keys-bg-sky', why: 'key_color repaints the unpressed caps',
    state: loadout({ keystrokes: { key_color: 'sky' } }) },
  { name: 'keys-radius-0', why: 'corner_radius 0 squares the caps',
    state: loadout({ keystrokes: { corner_radius: 0 } }) },

  { name: 'crosshair-cross', why: 'crosshair.style cross', state: loadout({ crosshair: { on: true, style: 'cross' } }) },
  { name: 'crosshair-dot', why: 'crosshair.style dot', state: loadout({ crosshair: { on: true, style: 'dot' } }) },
  { name: 'crosshair-circle', why: 'crosshair.style circle', state: loadout({ crosshair: { on: true, style: 'circle' } }) },
  { name: 'crosshair-t-shape', why: 'crosshair.style t_shape', state: loadout({ crosshair: { on: true, style: 't_shape' } }) },
  { name: 'crosshair-none', why: 'crosshair.style none draws nothing at all', state: loadout({ crosshair: { on: true, style: 'none' } }) },
  { name: 'crosshair-default', why: 'crosshair.style default hands back the vanilla one', state: loadout({ crosshair: { on: true, style: 'default' } }) },
  { name: 'ping-bad-band', why: 'ping.bad_ms now has a tone of its own — the dot goes red past it, where it used to stay amber',
    state: loadout({ ping: { good_ms: 5, bad_ms: 10 } }) },
  { name: 'fps-no-low', why: 'fps.show_low drops the 1% low aside and keeps the figure',
    state: loadout({ fps: { show_low: false } }) },
  { name: 'cps-no-label', why: 'cps.show_label drops the trailing CPS unit',
    state: loadout({ cps: { show_label: false } }) },
  { name: 'ping-no-host', why: 'ping.show_host drops the server name after the figure',
    state: loadout({ ping: { show_host: false } }) },
  { name: 'crosshair-center-dot', why: 'center_dot rides on top of the cross, one dot on the centre point',
    state: loadout({ crosshair: { on: true, style: 'cross', gap: 4, size: 7, center_dot: true } }) },
  { name: 'crosshair-ring-dot', why: 'center_dot is the one rectangle a ring draws',
    state: loadout({ crosshair: { on: true, style: 'circle', size: 8, center_dot: true } }) },
  { name: 'keys-sneak', why: 'keystrokes.show_sneak draws the shift cap beside the space bar',
    state: loadout({ keystrokes: { show_sneak: true, show_spacebar: true } }) },
  { name: 'armor-warn-high', why: 'armor_status.warn_below at 90% — every damaged bar goes amber',
    state: loadout({ armor_status: { warn_below: 0.9 } }) },
  { name: 'armor-warn-never', why: 'armor_status.warn_below at 0 — no bar ever warns',
    state: loadout({ armor_status: { warn_below: 0 } }) },
  { name: 'crosshair-big-coral', why: 'size, thickness, gap and colour together',
    state: loadout({ crosshair: { on: true, style: 'cross', size: 14, thickness: 4, gap: 8, color: '#FF9E7A' } }) },

  { name: 'fullbright-off', why: 'the reference: gamma untouched', state: loadout({ fullbright: { on: false } }) },
  { name: 'fullbright-g1', why: 'gamma 1 — the bottom of the range', state: loadout({ fullbright: { on: true, gamma: 1 } }) },
  { name: 'fullbright-g2', why: 'gamma 2', state: loadout({ fullbright: { on: true, gamma: 2 } }) },
  { name: 'fullbright-g3', why: 'gamma 3', state: loadout({ fullbright: { on: true, gamma: 3 } }) },
  { name: 'fullbright-g4', why: 'gamma 4', state: loadout({ fullbright: { on: true, gamma: 4 } }) },
  { name: 'fullbright-g5', why: 'gamma 5', state: loadout({ fullbright: { on: true, gamma: 5 } }) },
  { name: 'fullbright-g10', why: 'gamma 10 — the default', state: loadout({ fullbright: { on: true, gamma: 10 } }) },
  { name: 'fullbright-g15', why: 'gamma 15 — is anything above 10 distinguishable?', state: loadout({ fullbright: { on: true, gamma: 15 } }) },

  { name: 'hitbox-off', why: 'no boxes', state: loadout({ hitboxes: { on: false } }) },
  { name: 'hitbox-default', why: 'the new renderer at vanilla-ish defaults',
    state: loadout({ hitboxes: { on: true, line_width: 2, color: '#FFFFFFFF' } }) },
  { name: 'hitbox-thick-coral', why: 'line_width and color are real',
    state: loadout({ hitboxes: { on: true, line_width: 5, color: '#FF9E7A' } }) },
  { name: 'hitbox-thin', why: 'line_width 0.5 is visibly thinner than 5',
    state: loadout({ hitboxes: { on: true, line_width: 0.5, color: '#FFFFFFFF' } }) },
  { name: 'hitbox-eye-line', why: 'show_eye_line adds the look ray',
    state: loadout({ hitboxes: { on: true, line_width: 2, color: '#7ADFFF', show_eye_line: true } }) },

  // A burst of identical frames. Nothing here changes the loadout — the point is that the
  // *world* changes between them: a wandering mob moves, and the box either moves with it or
  // trails it. The rebase into camera space is the one step the unit tests cannot reach, and a
  // still frame of a stationary entity cannot tell the two apart.
  ...Array.from({ length: 8 }, (_, i) => ({
    name: `hitbox-track-${i}`,
    why: `tracking frame ${i} — does the box stay on a moving entity`,
    state: loadout({
      hitboxes: { on: true, line_width: 4, color: '#FF9E7A' },
      // The keycaps are in shot for the whole burst, and W is held by VOID_UI_AUTOWALK — so the
      // same frames that answer "does the box track" also answer "does pressed_color paint".
      keystrokes: { pressed_color: 'fear' },
    }),
  })),
  { name: 'pressed-accent', why: 'pressed_color accent, with W held',
    state: loadout({ keystrokes: { pressed_color: 'accent' } }) },
  { name: 'pressed-warn', why: 'pressed_color warn — must differ from fear',
    state: loadout({ keystrokes: { pressed_color: 'warn' } }) },
  { name: 'pressed-teal', why: 'pressed_color teal',
    state: loadout({ keystrokes: { pressed_color: 'teal' } }) },

  /* ---------------------------------------------------------------------- */
  /* The settings that were being counted as covered without being tested    */
  /*                                                                        */
  /* Everything below was pushed by the steps above at exactly one value —   */
  /* usually its default — which proves the loadout parses and nothing more. */
  /* A setting is verified when two values produce two different frames, so  */
  /* each of these states the value the baseline does *not* hold.            */
  /* ---------------------------------------------------------------------- */

  // Direction — the fourteenth mod, and until this run the only one with no step at all.
  { name: 'direction-letter', why: 'direction on at style letter — the compass point as N/NE/E',
    state: loadout({ direction: { on: true, style: 'letter' } }) },
  { name: 'direction-word', why: 'direction.style word spells it out',
    state: loadout({ direction: { on: true, style: 'word' } }) },
  { name: 'direction-axis', why: 'direction.style axis names the axis (+X / -Z) instead',
    state: loadout({ direction: { on: true, style: 'axis' } }) },
  { name: 'direction-degrees', why: 'direction.show_degrees adds the yaw figure',
    state: loadout({ direction: { on: true, style: 'letter', show_degrees: true } }) },

  { name: 'watermark-mark', why: 'watermark.style mark — the glyph alone, no wordmark',
    state: loadout({ watermark: { style: 'mark' } }) },
  { name: 'watermark-word', why: 'watermark.style word — the wordmark alone, no glyph',
    state: loadout({ watermark: { style: 'word' } }) },

  { name: 'cps-left-only', why: 'cps.mode left draws one figure where the baseline draws two',
    state: loadout({ cps: { mode: 'left' } }) },
  { name: 'cps-right-only', why: 'cps.mode right is the other hand, not the same one',
    state: loadout({ cps: { mode: 'right' } }) },

  { name: 'coords-no-direction', why: 'coordinates.show_direction drops the cardinal off the row',
    state: loadout({ coordinates: { show_direction: false } }) },
  { name: 'armor-no-held', why: 'armor_status.show_held_item drops the fifth slot',
    state: loadout({ armor_status: { show_held_item: false } }) },
  { name: 'ping-no-label', why: 'ping.show_label drops the ms unit',
    state: loadout({ ping: { show_label: false } }) },

  { name: 'keys-no-mouse', why: 'keystrokes.show_mouse drops LMB/RMB, keeping WASD',
    state: loadout({ keystrokes: { show_mouse: false } }) },
  { name: 'keys-no-space', why: 'keystrokes.show_spacebar drops the bar the baseline shows',
    state: loadout({ keystrokes: { show_spacebar: false } }) },
  { name: 'keys-no-cps', why: 'keystrokes.show_cps drops the click figures beside the caps',
    state: loadout({ keystrokes: { show_cps: false } }) },
  { name: 'keys-no-sneak', why: 'keystrokes.show_sneak off — `keys-sneak` only ever asserted the on side',
    state: loadout({ keystrokes: { show_sneak: false } }) },

  { name: 'crosshair-no-outline', why: 'crosshair.outline off — no dark rim, at a size where a rim is visible',
    state: loadout({ crosshair: { on: true, style: 'cross', size: 12, thickness: 3, outline: false } }) },
  { name: 'crosshair-dynamic', why: 'crosshair.dynamic spreads with movement — AUTOWALK is walking',
    state: loadout({ crosshair: { on: true, style: 'cross', size: 12, gap: 4, dynamic: true } }) },

  /* The chrome every HUD mod shares — `_shared.json`'s `hud` block. Applied to all nine at
     once rather than one widget at a time: the question is whether the shared property reaches
     every widget, and nine widgets in one frame answers it nine times. */
  { name: 'chrome-bg-subtle', why: 'background subtle on every HUD widget',
    state: loadout(Object.fromEntries(HUD_MOD_IDS.map((id) => [id, { background: 'subtle' }]))) },
  { name: 'chrome-bg-solid', why: 'background solid — a filled plate behind each widget',
    state: loadout(Object.fromEntries(HUD_MOD_IDS.map((id) => [id, { background: 'solid' }]))) },
  { name: 'chrome-border', why: 'border on, over the solid plate',
    state: loadout(Object.fromEntries(
      HUD_MOD_IDS.map((id) => [id, { background: 'solid', border: true }]))) },
  { name: 'chrome-padding-tight', why: 'padding tight pulls each plate in',
    state: loadout(Object.fromEntries(
      HUD_MOD_IDS.map((id) => [id, { background: 'solid', padding: 'tight' }]))) },
  { name: 'chrome-padding-roomy', why: 'padding roomy pushes it out — must differ from tight',
    state: loadout(Object.fromEntries(
      HUD_MOD_IDS.map((id) => [id, { background: 'solid', padding: 'roomy' }]))) },
  { name: 'chrome-scale-half', why: 'scale 0.5 on every widget, so the property is not fps-only',
    state: loadout(Object.fromEntries(HUD_MOD_IDS.map((id) => [id, { scale: 0.5 }]))) },

  /* ---------------------------------------------------------------------- */
  /* The settings added to fill out the thin mods                            */
  /* ---------------------------------------------------------------------- */

  { name: 'coords-coral', why: 'coordinates.color inks the three axes and the cardinal, not the separator',
    state: loadout({ coordinates: { color: '#FF9E7A' } }) },
  { name: 'direction-mint', why: 'direction.color inks the facing and leaves the degrees muted',
    state: loadout({ direction: { on: true, color: '#7AE0B0', show_degrees: true } }) },
  { name: 'cps-peak', why: 'cps.show_peak adds the session-high aside',
    state: loadout({ cps: { show_peak: true } }) },
  { name: 'hitbox-eye-own-colour', why: 'eye_line_color is its own ink — a coral box with an ice ray',
    state: loadout({ hitboxes: {
      on: true, line_width: 3, color: '#FF9E7A', show_eye_line: true, eye_line_color: '#7ADFFF',
    } }) },
  { name: 'hitbox-near-only', why: 'max_distance 4 — boxes on nothing but what is in your face',
    state: loadout({ hitboxes: { on: true, line_width: 3, color: '#FF9E7A', max_distance: 4 } }) },
  { name: 'hitbox-far-again', why: '…and 64 brings them back, so the cutoff is the setting and not the world',
    state: loadout({ hitboxes: { on: true, line_width: 3, color: '#FF9E7A', max_distance: 64 } }) },
];

/* -------------------------------------------------------------------------- */
/* Runner                                                                     */
/* -------------------------------------------------------------------------- */

function arg(name, fallback) {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] ? argv[i + 1] : fallback;
}

/** Screenshot the frontmost window Minecraft owns. */
function shot(outDir, name) {
  const path = `${outDir}/${name}.png`;
  try {
    execFileSync('/usr/sbin/screencapture', ['-x', '-o', path], { stdio: 'ignore' });
    return path;
  } catch (e) {
    return `FAILED: ${e.message}`;
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Block until the client is actually in a world.
 *
 * Watches the launch log for the line the UI prints once it has been sized against the game's
 * framebuffer, which cannot happen before `InGameHud` is rendering. Falls back to the timeout so
 * a missing or unexpected log delays the run rather than failing it.
 */
async function waitForWorld(logPath, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  if (!logPath) {
    await sleep(timeoutMs);
    return 'no log given; waited out the full warmup';
  }
  while (Date.now() < deadline) {
    try {
      const text = readFileSync(logPath, 'utf8');
      if (/in-game UI started at/.test(text)) {
        // The UI exists; give the first frames the forced-render window they are promised.
        await sleep(2500);
        return 'world up (saw "in-game UI started at")';
      }
    } catch { /* the log may not exist yet */ }
    await sleep(500);
  }
  return `timed out after ${timeoutMs}ms without seeing the UI come up — shots may be early`;
}

async function main() {
  if (argv.includes('--list')) {
    for (const s of STEPS) console.log(`${s.name.padEnd(24)} ${s.why}`);
    return;
  }
  const port = Number(arg('port', '41999'));
  const outDir = arg('out', '/tmp/voidshots');
  const from = arg('from', null);
  const settleMs = Number(arg('settle', '1400'));
  // The mod connects and says hello while the title screen is still up, several seconds before
  // `VOID_UI_AUTOWORLD` has a world. The HUD layer is only painted from `InGameHud.render`, so a
  // step that fires before then screenshots a title screen and reads as "the widget is missing" —
  // the single most misleading failure this script could produce, given step one is exactly that
  // question. So the run waits for the log to say a world is up, and falls back to a plain delay
  // if it was not given a log to watch.
  const logPath = arg('log', null);
  const warmupMs = Number(arg('warmup', '35000'));
  mkdirSync(outDir, { recursive: true });

  const start = from ? STEPS.findIndex((s) => s.name === from) : 0;
  if (start < 0) {
    console.error(`no such step: ${from}`);
    exit(2);
  }
  const steps = STEPS.slice(start);

  const server = createServer();
  server.on('upgrade', (req, socket) => {
    socket.write(
      'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n' +
        `Sec-WebSocket-Accept: ${accept(req.headers['sec-websocket-key'])}\r\n\r\n`,
    );
    const state = { buf: Buffer.alloc(0) };
    const send = (obj) => socket.write(frame(JSON.stringify(obj)));
    const log = [];

    socket.on('data', async (chunk) => {
      for (const text of drain(state, chunk)) {
        if (text === null) return;
        let msg;
        try { msg = JSON.parse(text); } catch { continue; }
        if (msg.t !== 'hello') continue;

        console.log(`hello from mod ${msg.mod} on mc ${msg.mc} (protocol v${msg.v})`);
        const first = steps[0].state;
        send({ t: 'init', v: 2, loadout: first, loadouts: [first], settings: {} });

        console.log(`waiting for a world… (${await waitForWorld(logPath, warmupMs)})`);

        for (const step of steps) {
          send({ t: 'loadout', loadout: step.state });
          await sleep(settleMs);
          const path = shot(outDir, step.name);
          console.log(`  ${step.name.padEnd(24)} ${path}   — ${step.why}`);
          log.push({ step: step.name, why: step.why, shot: path });
        }
        writeFileSync(`${outDir}/run.json`, JSON.stringify(log, null, 2));
        console.log(`\n${log.length} steps, shots in ${outDir}`);
        exit(0);
      }
    });
    socket.on('error', () => {});
  });

  server.listen(port, '127.0.0.1', () => {
    console.log(`fake launcher on ws://127.0.0.1:${port}/  — ${steps.length} steps queued`);
    console.log('launch the client with -Dvoid.port=' + port + ' -Dvoid.token=audit');
  });
}

main();
