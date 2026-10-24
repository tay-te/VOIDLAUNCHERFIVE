// webcap page shim: a virtual clock for frame-exact capture.
//
// Installed with Page.addScriptToEvaluateOnNewDocument, so it runs before any page
// script. From then on the page never sees real time:
//
//   performance.now(), Date, new Date(), Event#timeStamp   -> the virtual clock
//   setTimeout / setInterval / requestIdleCallback          -> a virtual timer queue
//   requestAnimationFrame                                   -> called once per stepped frame
//   Math.random                                             -> a seeded PRNG
//   every Web Animation in document.getAnimations()         -> paused, then seeked to
//     (CSS animations, CSS transitions, element.animate(),     (virtual now - the virtual
//      view-transition pseudo-elements)                          time it first appeared)
//
// The virtual clock only moves when the capture driver calls __webcap.frame(t). While it
// is still, "settle" lets real macrotasks and rendering run (React's MessageChannel work,
// font loads, image decodes, ResizeObserver, view-transition captures) until the page is
// quiet, so everything that happens in response to an event is attributed to the virtual
// instant of that event, however long it really took.
(function () {
  'use strict';
  if (window.__webcap) return;
  const CFG = /*WEBCAP_CONFIG*/ { epoch: 1790000000000, seed: 1 } /*END*/;

  const W = window;
  const realSetTimeout = W.setTimeout.bind(W);
  const realRAF = W.requestAnimationFrame.bind(W);
  const RealDate = W.Date;
  const report = (e) => {
    try {
      W.reportError(e);
    } catch {
      console.error(e);
    }
  };

  let vnow = 0; // virtual ms since the page started

  // ------------------------------------------------------------------ clocks

  performance.now = function now() {
    return vnow;
  };

  function VDate(...args) {
    if (!new.target) return new RealDate(Math.floor(CFG.epoch + vnow)).toString();
    if (args.length === 0) return Reflect.construct(RealDate, [Math.floor(CFG.epoch + vnow)], new.target);
    return Reflect.construct(RealDate, args, new.target);
  }
  VDate.prototype = RealDate.prototype;
  VDate.now = function now() {
    return Math.floor(CFG.epoch + vnow);
  };
  VDate.parse = RealDate.parse;
  VDate.UTC = RealDate.UTC;
  Object.defineProperty(VDate, 'name', { value: 'Date' });
  W.Date = VDate;

  try {
    Object.defineProperty(Event.prototype, 'timeStamp', {
      configurable: true,
      get() {
        return vnow;
      },
    });
  } catch {
    /* non-fatal */
  }

  // mulberry32: small, fast, and the same sequence on every run.
  let seed = CFG.seed >>> 0;
  Math.random = function random() {
    seed = (seed + 0x6d2b79f5) >>> 0;
    let t = seed;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };

  // ------------------------------------------------------------------ timers

  let nextId = 1;
  let order = 0;
  let nesting = 0; // HTML's timer nesting level: >5 deep clamps to 4ms
  const timers = new Map();

  function addTimer(fn, delay, args, repeat) {
    if (typeof fn !== 'function') {
      const code = String(fn);
      fn = () => (0, eval)(code);
    }
    delay = Number(delay) || 0;
    if (delay < 0) delay = 0;
    const nest = nesting + 1;
    if ((nest > 5 || repeat) && delay < 4) delay = 4;
    const id = nextId++;
    timers.set(id, { id, fn, args, delay, repeat, due: vnow + delay, order: order++, nest });
    return id;
  }
  const clearTimer = (id) => {
    timers.delete(Number(id));
  };

  W.setTimeout = function setTimeout(fn, delay, ...args) {
    return addTimer(fn, delay, args, false);
  };
  W.setInterval = function setInterval(fn, delay, ...args) {
    return addTimer(fn, delay, args, true);
  };
  W.clearTimeout = clearTimer;
  W.clearInterval = clearTimer;
  W.requestIdleCallback = function requestIdleCallback(cb) {
    return addTimer(() => cb({ didTimeout: false, timeRemaining: () => 50 }), 0, [], false);
  };
  W.cancelIdleCallback = clearTimer;

  /** The earliest timer due at or before `limit`, in due-then-creation order. */
  function nextDue(limit) {
    let best = null;
    for (const t of timers.values()) {
      if (t.due > limit) continue;
      if (!best || t.due < best.due || (t.due === best.due && t.order < best.order)) best = t;
    }
    return best;
  }

  function runTimer(t) {
    if (t.repeat) {
      t.due += t.delay;
      t.order = order++;
    } else timers.delete(t.id);
    const prev = nesting;
    nesting = t.nest;
    try {
      t.fn.apply(W, t.args);
    } catch (e) {
      report(e);
    } finally {
      nesting = prev;
    }
  }

  /** Run every timer already due at the current virtual instant. */
  function runDue() {
    let n = 0;
    for (let t; (t = nextDue(vnow)); ) {
      runTimer(t);
      if (++n > 10000) throw new Error('webcap: more than 10000 timers fired in one instant');
    }
    return n;
  }

  // ---------------------------------------------------------- animation frames

  let rafId = 1;
  let rafQueue = new Map();
  W.requestAnimationFrame = function requestAnimationFrame(cb) {
    const id = rafId++;
    rafQueue.set(id, cb);
    return id;
  };
  W.cancelAnimationFrame = function cancelAnimationFrame(id) {
    rafQueue.delete(id);
  };
  function flushRAF() {
    const q = rafQueue;
    rafQueue = new Map();
    for (const cb of q.values()) {
      try {
        cb(vnow);
      } catch (e) {
        report(e);
      }
    }
    return q.size;
  }

  // -------------------------------------------------------------- animations

  /** Every animation we have taken over, with the virtual time it first appeared. */
  const reg = new Map();

  function register() {
    let n = 0;
    for (const a of document.getAnimations()) {
      if (reg.has(a)) continue;
      // Scroll- and view-driven timelines follow scrolling, not time; leave them be.
      if (a.timeline && a.timeline !== document.timeline) continue;
      reg.set(a, vnow);
      n++;
      try {
        a.pause();
        a.currentTime = 0;
      } catch {
        /* an animation with no effect can refuse; seek() deals with it */
      }
    }
    return n;
  }

  function seek() {
    for (const [a, start] of reg) {
      if (a.playState === 'idle') {
        reg.delete(a); // cancelled: its element went, or its CSS rule stopped applying
        continue;
      }
      const rate = a.playbackRate || 1;
      const local = (vnow - start) * rate;
      let end = Infinity;
      try {
        end = a.effect ? a.effect.getComputedTiming().endTime : 0;
      } catch {
        /* keep Infinity */
      }
      try {
        if (rate > 0 && Number.isFinite(end) && local >= end) {
          // Past its end: let it really finish, so fill modes, animationend, `finished`
          // promises and view transitions behave as they would live.
          if (a.playState !== 'finished') {
            a.currentTime = end;
            a.finish();
          }
        } else {
          if (a.playState !== 'paused') a.pause();
          a.currentTime = local;
        }
      } catch (e) {
        report(e);
      }
    }
  }

  // ------------------------------------------------------------------ settle

  let dirty = false;
  const mo = new MutationObserver(() => {
    dirty = true;
  });
  const observe = () => mo.observe(document, { subtree: true, childList: true, attributes: true, characterData: true });
  if (document.documentElement) observe();
  else document.addEventListener('readystatechange', observe, { once: true });

  const macrotask = () =>
    new Promise((r) => {
      const ch = new MessageChannel();
      ch.port1.onmessage = () => r();
      ch.port2.postMessage(0);
    });
  const realDelay = (ms) => new Promise((r) => realSetTimeout(r, ms));
  // One real rendering opportunity: style, layout, ResizeObserver, view-transition steps.
  const realFrame = () => Promise.race([new Promise((r) => realRAF(() => realSetTimeout(r, 0))), realDelay(200)]);

  async function assets() {
    let waited = false;
    if (document.fonts && document.fonts.status !== 'loaded') {
      waited = true;
      await Promise.race([document.fonts.ready, realDelay(8000)]);
    }
    // Each <img> source is waited for once (a broken or lazy image must not stall every pass).
    const imgs = [...document.images].filter((i) => i.src && tried.get(i) !== i.src && (!i.complete || !i.naturalWidth));
    if (imgs.length) {
      waited = true;
      for (const i of imgs) tried.set(i, i.src);
      await Promise.race([Promise.all(imgs.map((i) => i.decode().catch(() => {}))), realDelay(8000)]);
    }
    return waited;
  }
  const tried = new WeakMap();

  async function pass() {
    let act = runDue() > 0;
    await macrotask();
    if (runDue() > 0) act = true;
    await macrotask();
    if (await assets()) act = true;
    if (register() > 0) act = true;
    if (dirty) {
      dirty = false;
      act = true;
    }
    return act;
  }

  /** Let the page finish reacting at the current virtual instant. */
  async function settle(maxRounds = 60) {
    for (let i = 0; i < maxRounds; i++) {
      if (await pass()) {
        await realFrame();
        continue;
      }
      await realFrame();
      if (!(await pass())) return i;
    }
    return maxRounds;
  }

  /** Advance the clock to `ms`, firing each timer at its own due time on the way. */
  async function advanceTo(ms) {
    for (let t, n = 0; (t = nextDue(ms)); ) {
      if (++n > 100000) throw new Error('webcap: timer storm while advancing');
      if (t.due > vnow) {
        vnow = t.due;
        seek();
      }
      runTimer(t);
      await settle();
    }
    if (ms > vnow) vnow = ms;
  }

  /** One output frame at virtual time `ms`: timers, rAF, settle, then seek every animation. */
  async function frame(ms) {
    await advanceTo(ms);
    seek();
    const rafs = flushRAF();
    await settle();
    for (let i = 0; i < 5; i++) {
      seek();
      // finish() resolves `finished` promises; give their handlers a turn.
      if (!(await pass())) break;
      await settle();
    }
    seek();
    return { now: vnow, rafs, animations: reg.size, timers: timers.size };
  }

  /** Load every declared @font-face now, so no face swaps in mid-sequence. */
  async function preloadFonts() {
    if (!document.fonts) return 0;
    const faces = [...document.fonts];
    await Promise.race([Promise.all(faces.map((f) => f.load().catch(() => {}))), realDelay(15000)]);
    await document.fonts.ready;
    return faces.length;
  }

  /**
   * Jump every running CSS transition to its end (re-stamped as if it had started long
   * ago). Used after the setup navigation, so the navbar's tab highlight is already lit at
   * t = 0 while the new screen's own mount animations still play from t = 0.
   */
  function endTransitions() {
    let n = 0;
    for (const [a] of reg) {
      if (typeof a.transitionProperty !== 'string') continue;
      let end = 0;
      try {
        end = a.effect.getComputedTiming().endTime;
      } catch {
        continue;
      }
      if (!Number.isFinite(end)) continue;
      reg.set(a, vnow - end - 1);
      n++;
    }
    seek();
    return n;
  }

  /**
   * After webcap's own style edits (layer isolation): anything those edits started must not
   * change the frozen picture. Visibility transitions are dropped (the new visibility wins);
   * anything else is held at its start, which is the look from before the edit.
   */
  function freezeNew() {
    let n = 0;
    for (const a of document.getAnimations()) {
      if (reg.has(a)) continue;
      n++;
      try {
        if (a.transitionProperty === 'visibility') a.cancel();
        else {
          a.pause();
          a.currentTime = 0;
        }
      } catch (e) {
        report(e);
      }
    }
    return n;
  }

  /** What every registered animation is doing, for timing checks. */
  function animations() {
    const out = [];
    for (const [a, start] of reg) {
      const t = a.effect?.target;
      const ct = a.effect?.getComputedTiming?.() ?? {};
      out.push({
        name: a.animationName ?? (a.transitionProperty ? `transition:${a.transitionProperty}` : a.id || 'animation'),
        target: t ? `${t.tagName.toLowerCase()}${t.id ? `#${t.id}` : ''}${t.classList?.length ? `.${[...t.classList].join('.')}` : ''}` : null,
        pseudo: a.effect?.pseudoElement ?? null,
        start: start / 1000,
        currentTime: a.currentTime,
        progress: ct.progress ?? null,
        playState: a.playState,
      });
    }
    return out;
  }

  Object.defineProperty(W, '__webcap', {
    enumerable: false,
    value: Object.freeze({
      now: () => vnow,
      frame,
      settle,
      advanceTo,
      register,
      seek,
      freezeNew,
      endTransitions,
      preloadFonts,
      animations,
      pendingTimers: () => [...timers.values()].map((t) => ({ due: t.due, repeat: t.repeat })),
    }),
  });
})();
