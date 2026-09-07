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
 * The factory HUD layout, copied from `Loadout.DEFAULT_HUD` / `HudEditorScreen.DEFAULT_HUD`.
 * Stated here so a step can move one widget without the other two tables being involved.
 */
const HUD = [
  { id: 'fps', anchor: 'top-left', dx: 23, dy: 23, scale: 1 },
  { id: 'ping', anchor: 'top-left', dx: 23, dy: 65, scale: 1 },
  { id: 'coordinates', anchor: 'top-left', dx: 23, dy: 103, scale: 1 },
  { id: 'watermark', anchor: 'top-left', dx: 23, dy: 141, scale: 1 },
  { id: 'potion_effects', anchor: 'top-right', dx: -25, dy: 23, scale: 1 },
  { id: 'armor_status', anchor: 'top-right', dx: -25, dy: 299, scale: 1 },
  { id: 'keystrokes', anchor: 'bottom-left', dx: 31, dy: -109, scale: 1 },
  { id: 'cps', anchor: 'bottom-left', dx: 175, dy: -108, scale: 1 },
];

/** Every mod on, at its registry default, so a step only has to state its own difference. */
function baseMods() {
  return {
    fps: { on: true, scale: 1, opacity: 1, color: '#FFFFFF', show_label: true },
    keystrokes: {
      on: true, scale: 1, opacity: 0.85, keybind: 'NONE', show_mouse: true,
      show_spacebar: true, show_cps: true, corner_radius: 8,
      key_color: 'shell', pressed_color: 'accent',
    },
    cps: { on: true, scale: 1, opacity: 1, mode: 'both', window_ms: 1000 },
    ping: { on: true, scale: 1, opacity: 1, show_label: true, good_ms: 60, bad_ms: 150 },
    coordinates: {
      on: true, scale: 1, opacity: 1, decimals: 1, show_direction: true, layout: 'inline',
    },
    armor_status: {
      on: true, scale: 1, opacity: 1, orientation: 'horizontal',
      show_durability: true, show_held_item: true,
    },
    potion_effects: {
      on: true, scale: 1, opacity: 1, show_duration: true, show_amplifier: true,
      hide_ambient: false,
    },
    watermark: { on: true, scale: 1, opacity: 0.9, style: 'full' },
    toggle_sprint: { on: true, mode: 'toggle', sneak_too: false },
    fullbright: { on: false, gamma: 10 },
    hitboxes: { on: false, line_width: 2, color: '#FFFFFFFF', show_eye_line: false },
    zoom: { on: true, key: 'C', fov_divisor: 4, smooth: true, cinematic: false },
    crosshair: {
      on: false, style: 'cross', size: 5, thickness: 1, gap: 2,
      color: '#FFFFFFFF', outline: true, dynamic: false,
    },
  };
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
