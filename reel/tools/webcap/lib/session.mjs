// A capture session: one headless Chrome page with the virtual clock installed, plus the
// operations the CLI composes — settle, step a frame, run a timeline action, navigate,
// screenshot, and capture layers.

import { readFileSync } from 'node:fs';

import { launchChrome } from './chrome.mjs';
import { NAV_BY_STORE, clickPath } from './screens.mjs';

const SHIM_SRC = readFileSync(new URL('./shim.js', import.meta.url), 'utf8');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// Injected after load. The preview banner only exists in the browser build (it says the
// Tauri API is mocked); the caret blinks on a real-time timer the clock cannot reach.
const CLEAN_CSS = `
  .banner--preview { display: none !important; }
  * { caret-color: transparent !important; }
`;

const TRANSPARENT_CSS = `html, body { background: transparent !important; }`;

const KEYS = {
  Enter: [13, 'Enter', '\r'],
  Escape: [27, 'Escape'],
  Tab: [9, 'Tab'],
  Backspace: [8, 'Backspace'],
  Delete: [46, 'Delete'],
  ArrowUp: [38, 'ArrowUp'],
  ArrowDown: [40, 'ArrowDown'],
  ArrowLeft: [37, 'ArrowLeft'],
  ArrowRight: [39, 'ArrowRight'],
  Home: [36, 'Home'],
  End: [35, 'End'],
  PageUp: [33, 'PageUp'],
  PageDown: [34, 'PageDown'],
  ' ': [32, 'Space', ' '],
};
const MODS = { alt: 1, ctrl: 2, control: 2, meta: 4, cmd: 4, shift: 8 };

export async function openSession(opts) {
  const {
    url = 'http://127.0.0.1:5183/',
    width = 1920,
    height = 1080,
    scale = 2,
    epoch = Date.UTC(2026, 9, 24, 12, 0, 0),
    seed = 1,
    chrome,
    chromeArgs = [],
    gpu = false,
    headful = false,
    clean = true,
    tz,
    log = () => {},
  } = opts;

  const browser = await launchChrome({ chrome, width, height, headful, gpu, extraArgs: chromeArgs });
  const { cdp } = browser;
  const problems = [];
  const inflight = new Map();

  cdp.on('Runtime.exceptionThrown', (p) => {
    const d = p.exceptionDetails;
    problems.push(`exception: ${d.exception?.description ?? d.text}`);
  });
  cdp.on('Runtime.consoleAPICalled', (p) => {
    if (p.type !== 'error' && p.type !== 'assert') return;
    problems.push(`console.${p.type}: ${p.args.map((a) => a.value ?? a.description ?? '').join(' ')}`);
  });
  cdp.on('Log.entryAdded', (p) => {
    if (p.entry.level === 'error') problems.push(`log: ${p.entry.text} ${p.entry.url ?? ''}`);
  });
  cdp.on('Network.requestWillBeSent', (p) => inflight.set(p.requestId, p.request.url));
  cdp.on('Network.loadingFinished', (p) => inflight.delete(p.requestId));
  cdp.on('Network.loadingFailed', (p) => inflight.delete(p.requestId));

  await Promise.all([
    cdp.send('Page.enable'),
    cdp.send('Runtime.enable'),
    cdp.send('Network.enable'),
    cdp.send('Log.enable'),
  ]);
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: false });
  await cdp.send('Emulation.setDeviceMetricsOverride', {
    width,
    height,
    deviceScaleFactor: scale,
    mobile: false,
    screenWidth: width,
    screenHeight: height,
  });
  // The Coming soon screens pause every loop when the window has no focus.
  await cdp.send('Emulation.setFocusEmulationEnabled', { enabled: true });
  await cdp.send('Emulation.setEmulatedMedia', {
    features: [
      { name: 'prefers-reduced-motion', value: 'no-preference' },
      { name: 'prefers-color-scheme', value: 'dark' },
    ],
  });
  if (tz) await cdp.send('Emulation.setTimezoneOverride', { timezoneId: tz });

  const shim = SHIM_SRC.replace(/\/\*WEBCAP_CONFIG\*\/[\s\S]*?\/\*END\*\//, JSON.stringify({ epoch, seed }));
  await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: shim });

  const loaded = new Promise((r) => {
    const off = cdp.on('Page.loadEventFired', () => {
      off();
      r();
    });
  });
  const nav = await cdp.send('Page.navigate', { url });
  if (nav.errorText) throw new Error(`could not load ${url}: ${nav.errorText} (is the dev server up? npm run dev:web)`);
  await loaded;

  const s = {
    cdp,
    browser,
    problems,
    log,
    width,
    height,
    scale,
    version: browser.version,

    eval: (expr) => cdp.eval(expr),

    async networkIdle(timeout = 15000) {
      const t0 = Date.now();
      while (inflight.size > 0 && Date.now() - t0 < timeout) await sleep(10);
      if (inflight.size > 0) log(`warning: still loading after ${timeout}ms: ${[...inflight.values()].slice(0, 3).join(', ')}`);
    },

    /** Let the page finish reacting at the current virtual instant (and finish loading). */
    async settle() {
      for (let i = 0; i < 10; i++) {
        await cdp.eval('__webcap.settle()');
        if (inflight.size === 0) break;
        await s.networkIdle();
      }
    },

    /** Step the clock to `t` seconds and leave every animation seeked to it. */
    async frame(t) {
      let info;
      for (let i = 0; i < 10; i++) {
        info = await cdp.eval(`__webcap.frame(${t * 1000})`);
        if (inflight.size === 0) break;
        await s.networkIdle();
      }
      return info;
    },

    async advanceTo(t) {
      await cdp.eval(`__webcap.advanceTo(${t * 1000}).then(() => __webcap.seek())`);
    },

    now: async () => (await cdp.eval('__webcap.now()')) / 1000,

    async addStyle(id, css) {
      await cdp.eval(`(() => {
        let el = document.getElementById(${JSON.stringify(id)});
        if (!el) { el = document.createElement('style'); el.id = ${JSON.stringify(id)}; document.head.append(el); }
        el.textContent = ${JSON.stringify(css)};
      })()`);
    },

    async removeStyle(id) {
      await cdp.eval(`document.getElementById(${JSON.stringify(id)})?.remove()`);
    },

    async setTransparent(on) {
      if (on) {
        await cdp.send('Emulation.setDefaultBackgroundColorOverride', { color: { r: 0, g: 0, b: 0, a: 0 } });
        await s.addStyle('webcap-transparent', TRANSPARENT_CSS);
      } else {
        await cdp.send('Emulation.setDefaultBackgroundColorOverride', {});
        await s.removeStyle('webcap-transparent');
      }
    },

    /** Centre of the first element matching `sel` (and containing `text`), in CSS px. */
    async locate(sel, text, nth = 0) {
      const r = await cdp.eval(`(() => {
        const sel = ${JSON.stringify(sel)}, text = ${JSON.stringify(text ?? null)};
        const all = [...document.querySelectorAll(sel)].filter((e) => text === null || e.textContent.includes(text));
        const el = all[${Number(nth) || 0}];
        if (!el) return { error: 'no element matches ' + sel + (text !== null ? ' containing ' + JSON.stringify(text) : '') + ' (found ' + all.length + ')' };
        el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        const b = el.getBoundingClientRect();
        const x = b.left + b.width / 2, y = b.top + b.height / 2;
        const hit = document.elementFromPoint(x, y);
        return { x, y, covered: !!hit && hit !== el && !el.contains(hit) ? (hit.className || hit.tagName) : null };
      })()`);
      if (r.error) throw new Error(r.error);
      if (r.covered) log(`warning: ${sel} is covered at its centre by "${r.covered}"`);
      return r;
    },

    /**
     * One input event, then settle. Settling after *each* event (not just after the whole
     * click) makes Chrome resolve styles between move, press and release every time, so
     * the :hover and :active transitions they start are the same on every run instead of
     * depending on whether a real frame happened to fall between two CDP messages.
     */
    async mouse(type, x, y, extra = {}) {
      await cdp.send('Input.dispatchMouseEvent', { type, x, y, pointerType: 'mouse', ...extra });
      await cdp.eval('__webcap.settle()');
    },

    async key(spec, modifiers = []) {
      const m = modifiers.reduce((a, k) => a | (MODS[k.toLowerCase()] ?? 0), 0);
      const known = KEYS[spec];
      const key = spec;
      const vk = known ? known[0] : spec.length === 1 ? spec.toUpperCase().charCodeAt(0) : 0;
      const code = known ? known[1] : spec.length === 1 && /[a-z]/i.test(spec) ? `Key${spec.toUpperCase()}` : /[0-9]/.test(spec) ? `Digit${spec}` : spec;
      const text = known ? known[2] : spec.length === 1 && !(m & 7) ? spec : undefined;
      const base = { key, code, windowsVirtualKeyCode: vk, nativeVirtualKeyCode: vk, modifiers: m };
      await cdp.send('Input.dispatchKeyEvent', { type: text ? 'keyDown' : 'rawKeyDown', ...base, ...(text ? { text, unmodifiedText: text } : {}) });
      await cdp.eval('__webcap.settle()');
      await cdp.send('Input.dispatchKeyEvent', { type: 'keyUp', ...base });
    },

    mouseAt: { x: -1, y: -1 },

    /** One timeline action, at the current virtual instant. */
    async act(a) {
      const pos = async () => (a.sel ? s.locate(a.sel, a.text, a.nth) : { x: a.x, y: a.y });
      switch (a.do) {
        case 'hover':
        case 'move': {
          const p = await pos();
          await s.mouse('mouseMoved', p.x, p.y, { button: 'none', buttons: 0 });
          s.mouseAt = p;
          break;
        }
        case 'click':
        case 'down':
        case 'up': {
          const p = await pos();
          const button = a.button ?? 'left';
          const bit = { left: 1, right: 2, middle: 4 }[button] ?? 1;
          if (p.x !== s.mouseAt.x || p.y !== s.mouseAt.y) {
            await s.mouse('mouseMoved', p.x, p.y, { button: 'none', buttons: 0 });
            s.mouseAt = p;
          }
          if (a.do !== 'up') await s.mouse('mousePressed', p.x, p.y, { button, buttons: bit, clickCount: 1 });
          if (a.do !== 'down') await s.mouse('mouseReleased', p.x, p.y, { button, buttons: 0, clickCount: 1 });
          break;
        }
        case 'wheel': {
          const p = await pos();
          await s.mouse('mouseWheel', p.x, p.y, { deltaX: a.dx ?? 0, deltaY: a.dy ?? 0 });
          break;
        }
        case 'key':
          await s.key(a.key, a.modifiers ?? []);
          break;
        case 'type':
          await cdp.send('Input.insertText', { text: String(a.text) });
          break;
        case 'nav':
          await s.nav(a.screen, a.via ?? 'store');
          break;
        case 'eval':
          await cdp.eval(a.js);
          break;
        default:
          throw new Error(`unknown action "${a.do}" in ${JSON.stringify(a)}`);
      }
      await s.settle();
    },

    /** Go to a screen (or "screen+overlay+…") by setting the store, or by clicking. */
    async nav(target, via = 'store') {
      const names = Array.isArray(target) ? target : String(target).split('+').filter(Boolean);
      if (via === 'store') {
        await cdp.eval(`(${NAV_BY_STORE})(${JSON.stringify(names)})`);
        await s.settle();
        return;
      }
      for (const name of names) {
        const path = clickPath(name);
        if (!path) throw new Error(`no click path for "${name}"; use via "store"`);
        for (const step of path) {
          const p = await s.locate(step.sel, step.text);
          await s.mouse('mouseMoved', p.x, p.y, { button: 'none', buttons: 0 });
          await s.mouse('mousePressed', p.x, p.y, { button: 'left', buttons: 1, clickCount: 1 });
          await s.mouse('mouseReleased', p.x, p.y, { button: 'left', buttons: 0, clickCount: 1 });
          s.mouseAt = p;
          await s.settle();
        }
      }
    },

    async screenshot() {
      const r = await cdp.send('Page.captureScreenshot', { format: 'png', fromSurface: true, captureBeyondViewport: false });
      return Buffer.from(r.data, 'base64');
    },

    /** Computed state of the first match of each selector, for timing checks. */
    async probe(selectors) {
      return cdp.eval(`(() => {
        const out = {};
        for (const sel of ${JSON.stringify(selectors)}) {
          const el = document.querySelector(sel);
          if (!el) { out[sel] = null; continue; }
          const cs = getComputedStyle(el), b = el.getBoundingClientRect();
          out[sel] = { opacity: +cs.opacity, transform: cs.transform, translate: cs.translate, scale: cs.scale,
            rotate: cs.rotate, filter: cs.filter, background: cs.backgroundColor, color: cs.color,
            width: b.width, height: b.height, x: b.x, y: b.y };
        }
        return out;
      })()`);
    },

    async close() {
      await browser.close();
    },
  };

  // Ready: the app mounted, no Vite error overlay, every font face loaded.
  for (let i = 0; ; i++) {
    const st = await cdp.eval(`({ root: document.getElementById('root')?.childElementCount ?? 0,
      overlay: !!document.querySelector('vite-error-overlay'), shim: !!window.__webcap })`);
    if (!st.shim) throw new Error('the clock shim did not install');
    if (st.overlay) {
      const msg = await cdp.eval(`document.querySelector('vite-error-overlay').shadowRoot?.textContent?.slice(0, 800) ?? ''`);
      throw new Error(`Vite error overlay is up:\n${msg}`);
    }
    if (st.root > 0) break;
    if (i > 300) throw new Error('the app did not mount within 15 s');
    await sleep(50);
  }
  const faces = await cdp.eval('__webcap.preloadFonts()');
  log(`loaded ${url} (${faces} font faces) in ${browser.version}`);
  if (clean) await s.addStyle('webcap-clean', CLEAN_CSS);
  await s.settle();
  return s;
}

/**
 * Run a timeline: `times` are the output instants in seconds; `actions` are applied at
 * their own `t` (relative to the same zero) before the frame that follows them.
 * `onFrame(i, t, info)` is awaited after each frame is stepped and seeked.
 */
export async function runTimeline(s, { times, actions = [], offset = 0, onFrame }) {
  const queue = expand(actions);
  let qi = 0;
  for (let i = 0; i < times.length; i++) {
    const T = times[i];
    while (qi < queue.length && queue[qi].t <= T + 1e-9) {
      const a = queue[qi++];
      await s.advanceTo(offset + a.t);
      await s.act(a);
    }
    const info = await s.frame(offset + T);
    if (onFrame) await onFrame(i, T, info);
  }
  return qi;
}

/** Sort by time (stable) and split clicks with a `hold` into down/up. */
function expand(actions) {
  const out = [];
  actions.forEach((a, i) => {
    if (typeof a.t !== 'number') throw new Error(`action ${i} has no numeric "t": ${JSON.stringify(a)}`);
    if (a.do === 'click' && a.hold > 0) {
      out.push({ ...a, do: 'down', i });
      out.push({ ...a, do: 'up', t: a.t + a.hold, i: i + 0.5 });
    } else out.push({ ...a, i });
  });
  return out.sort((x, y) => x.t - y.t || x.i - y.i);
}
