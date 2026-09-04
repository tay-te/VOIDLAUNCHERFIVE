/**
 * `createFakeVoid()` — an in-memory `window.void` for browser development.
 *
 * There are no devtools in game (§9), so the in-game bundle must also run in a normal
 * browser against a fake bridge. This is that fake: it emits realistic `tick`, `keys`,
 * `server`, `loadout` and `menu` pushes, answers all six calls with the same clamping
 * the Java side applies, and holds a small loadout library.
 *
 * It is **deterministic**: give it a `seed` and drive it with {@link FakeVoid.advance}
 * and every number it produces is reproducible, which is what makes it usable in tests.
 * Call {@link FakeVoid.start} instead and it runs on a real 20 Hz timer.
 */

import { LOADOUT_EXAMPLES } from './generated/examples.js';
import type {
  ArmorSlot,
  HUDItem,
  Keybind,
  KeysPayload,
  Loadout,
  LoadoutId,
  ModId,
  PotionEffect,
  ServerPayload,
  TickPayload,
} from './generated/schema.js';
import { getModDefaults, isModId } from './mods.js';
import type {
  HudPlacement,
  ModSettingValue,
  VoidBridge,
  VoidCallEnvelope,
  VoidEnvelope,
  VoidEventEnvelope,
  VoidEventHandler,
  VoidEventName,
} from './void-bridge.js';
import { isVoidCallResultEnvelope, isVoidEventEnvelope } from './void-bridge.js';

/* -------------------------------------------------------------------------- */
/* Determinism                                                                */
/* -------------------------------------------------------------------------- */

/** mulberry32 — small, fast, and identical across engines (JavaScriptCore included). */
function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/* -------------------------------------------------------------------------- */
/* Default library                                                            */
/* -------------------------------------------------------------------------- */

/**
 * The third card of the Loadouts frame (`244:1130`). `loadout.json` ships two examples;
 * this one is built to match the design copy — `16 mods on · Minemen · 1.8.9`, and
 * Hitboxes on, which makes it the deliberately *not* HYPIXEL-READY entry.
 */
const UHC_LOADOUT: Loadout = {
  id: 'uhc',
  name: 'UHC',
  icon: 'heart',
  server: 'minemen',
  mc: '1.8.9',
  mods: {
    armor_status: { on: true },
    potion_effects: { on: true },
    coordinates: { on: true },
    hitboxes: { on: true },
    zoom: { on: true, key: 'C' },
    fps: { on: true },
    ping: { on: true },
    keystrokes: { on: false },
    cps: { on: false },
  },
  hud: [
    { id: 'armor_status', anchor: 'right', dx: -20, dy: 0 },
    { id: 'potion_effects', anchor: 'top-right', dx: -20, dy: 20 },
    { id: 'coordinates', anchor: 'top-left', dx: 20, dy: 56 },
    { id: 'fps', anchor: 'top-left', dx: 20, dy: 20 },
  ],
  stats: { played_ms: 2880000, fps_avg: 0 },
};

/** The three loadouts the fake bridge starts with: the two schema examples plus UHC. */
export const FAKE_LOADOUTS: readonly Loadout[] = Object.freeze([
  ...LOADOUT_EXAMPLES.map((l) => structuredCloneish(l)),
  UHC_LOADOUT,
]);

/** JSON round-trip clone — `structuredClone` is not in Ultralight's JavaScriptCore. */
function structuredCloneish<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

/* -------------------------------------------------------------------------- */
/* Clamping                                                                   */
/* -------------------------------------------------------------------------- */

/** Numeric ranges from `mods.json`. Java clamps rather than throws; so do we. */
const SETTING_RANGES: Record<string, readonly [number, number]> = {
  scale: [0.25, 4],
  opacity: [0, 1],
  gamma: [1, 15],
  window_ms: [200, 5000],
  fov_divisor: [1.1, 10],
  size: [1, 20],
  thickness: [1, 5],
  gap: [0, 10],
  good_ms: [0, 1000],
  bad_ms: [0, 2000],
  decimals: [0, 3],
  line_width: [0.5, 5],
};

const INTEGER_SETTINGS = new Set(['window_ms', 'size', 'thickness', 'gap', 'good_ms', 'bad_ms', 'decimals']);

function clampSetting(key: string, value: ModSettingValue): ModSettingValue {
  const range = SETTING_RANGES[key];
  if (!range || typeof value !== 'number' || Number.isNaN(value)) return value;
  const clamped = Math.min(range[1], Math.max(range[0], value));
  return INTEGER_SETTINGS.has(key) ? Math.round(clamped) : clamped;
}

const clamp = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, value));

/* -------------------------------------------------------------------------- */
/* Options and surface                                                        */
/* -------------------------------------------------------------------------- */

/** Options for {@link createFakeVoid}. */
export interface FakeVoidOptions {
  /** PRNG seed. The same seed and the same `advance` calls give the same numbers. */
  seed?: number;
  /** The loadout library. Defaults to {@link FAKE_LOADOUTS}. */
  loadouts?: readonly Loadout[];
  /** Which loadout starts active. Defaults to the first. */
  activeLoadoutId?: LoadoutId;
  /** Initial server presence. Defaults to `mc.hypixel.net`, connected. */
  server?: ServerPayload;
  /** Whether the menu layer starts open. Defaults to false. */
  menuOpen?: boolean;
  /** Tick rate in Hz. Defaults to 20, the game's tick rate. */
  tickHz?: number;
  /**
   * Snap grid, in unscaled GUI pixels, applied by `setHud`. Defaults to 4, the
   * `hud_editor_grid` default in `protocol.json`. 0 disables snapping.
   */
  snapGrid?: number;
  /**
   * Attach a keydown listener that toggles the menu on the menu key and feeds
   * `openKeybindCapture`. Defaults to true when a DOM is present.
   */
  attachKeyboard?: boolean;
  /** `KeyboardEvent.code` that toggles the menu. Defaults to `ShiftRight`. */
  menuKey?: string;
  /** Where the keyboard listener is attached. Defaults to `globalThis.document`. */
  keyboardTarget?: EventTarget | null;
}

/** The fake bridge: a {@link VoidBridge} plus the controls a harness or test needs. */
export interface FakeVoid extends VoidBridge {
  /** Run on a real timer at `tickHz`. No-op if already running. */
  start(): void;
  /** Stop the timer. Does not reset state. */
  stop(): void;
  /** Whether the timer is running. */
  isRunning(): boolean;
  /** Advance the simulated clock by `ms`, emitting whole ticks. Deterministic. */
  advance(ms: number): void;
  /** Emit exactly one tick's worth of pushes. */
  tickOnce(): void;
  /**
   * Push the opening world of state — `loadouts`, `loadout`, `server`, then `menu` —
   * the way Java does after `init`. {@link FakeVoid.start} calls this once; call it
   * yourself when you drive the fake with {@link FakeVoid.advance} instead.
   */
  emitInitialState(): void;
  /** Open or close the menu layer, emitting `menu`. */
  setMenuOpen(open: boolean): void;
  /** Whether the menu layer is open. */
  isMenuOpen(): boolean;
  /** Force key state, emitting `keys` when anything changed. */
  setKeys(patch: Partial<KeysPayload>): void;
  /** Current key state. */
  getKeys(): KeysPayload;
  /** Set server presence, emitting `server`. */
  setServer(server: ServerPayload): void;
  /**
   * Change one mod setting the way Java does on its own — an in-game hotkey toggling a
   * mod — clamping it and emitting the `setting` event. Returns the value stored.
   * Distinct from `setModSetting`, which is the *page* asking and pushes nothing.
   */
  applyModSetting(id: ModId, key: string, value: ModSettingValue): ModSettingValue;
  /** The active loadout (a live reference; do not mutate). */
  getLoadout(): Loadout;
  /** The whole library. */
  getLoadouts(): Loadout[];
  /** Resolve a pending `openKeybindCapture`. Pass null for "player cancelled". */
  resolveKeybindCapture(key: Keybind | null): void;
  /** Whether a keybind capture is waiting. */
  isCapturingKeybind(): boolean;
  /** Every call made through the bridge, oldest first — the `?debug` recording. */
  getCalls(): VoidCallEnvelope[];
  /** Forget the recorded calls. */
  clearCalls(): void;
  /** Attach the keyboard listener; returns a detach function. */
  attachKeyboard(target?: EventTarget): () => void;
  /** Install as `target.void` (default `globalThis`). Returns this. */
  install(target?: Record<string, unknown>): FakeVoid;
  /** Stop the timer, detach listeners and drop all handlers. */
  destroy(): void;
}

/* -------------------------------------------------------------------------- */
/* DOM key → LWJGL 2 key name                                                 */
/* -------------------------------------------------------------------------- */

const CODE_TO_KEYBIND: Record<string, string> = {
  ShiftLeft: 'LSHIFT',
  ShiftRight: 'RSHIFT',
  ControlLeft: 'LCONTROL',
  ControlRight: 'RCONTROL',
  AltLeft: 'LMENU',
  AltRight: 'RMENU',
  CapsLock: 'CAPITAL',
  Space: 'SPACE',
  Tab: 'TAB',
  Enter: 'RETURN',
  Backspace: 'BACK',
  Delete: 'DELETE',
  Insert: 'INSERT',
  Home: 'HOME',
  End: 'END',
  PageUp: 'PRIOR',
  PageDown: 'NEXT',
  ArrowUp: 'UP',
  ArrowDown: 'DOWN',
  ArrowLeft: 'LEFT',
  ArrowRight: 'RIGHT',
  BracketLeft: 'LBRACKET',
  BracketRight: 'RBRACKET',
  Semicolon: 'SEMICOLON',
  Quote: 'APOSTROPHE',
  Comma: 'COMMA',
  Period: 'PERIOD',
  Slash: 'SLASH',
  Backslash: 'BACKSLASH',
  Minus: 'MINUS',
  Equal: 'EQUALS',
  Backquote: 'GRAVE',
};

/**
 * Translate a `KeyboardEvent.code` into the LWJGL 2 key name `mods.json` accepts,
 * or null when there is no equivalent.
 */
export function keybindFromKeyboardCode(code: string): Keybind | null {
  if (code in CODE_TO_KEYBIND) return CODE_TO_KEYBIND[code] as Keybind;
  const letter = /^Key([A-Z])$/.exec(code);
  if (letter) return letter[1] as Keybind;
  const digit = /^Digit([0-9])$/.exec(code);
  if (digit) return digit[1] as Keybind;
  const numpad = /^Numpad([0-9])$/.exec(code);
  if (numpad) return `NUMPAD${numpad[1]}` as Keybind;
  const fn = /^F([1-9]|1[0-2])$/.exec(code);
  if (fn) return code as Keybind;
  return null;
}

/* -------------------------------------------------------------------------- */
/* Implementation                                                             */
/* -------------------------------------------------------------------------- */

const CARDINALS = ['S', 'SW', 'W', 'NW', 'N', 'NE', 'E', 'SE'] as const;

/** The cardinal direction a yaw faces, as the Coordinates mod prints it. */
export function cardinalFromYaw(yaw: number): (typeof CARDINALS)[number] {
  const index = Math.round(((yaw + 180) % 360) / 45) % 8;
  return CARDINALS[index] ?? 'S';
}

/**
 * Create an in-memory `window.void`.
 *
 * @example
 * ```ts
 * const fake = createFakeVoid({ seed: 7 });
 * fake.install();           // window.void = fake
 * fake.start();             // 20 Hz pushes
 * ```
 *
 * @example Deterministic, no timers:
 * ```ts
 * const fake = createFakeVoid({ seed: 7, attachKeyboard: false });
 * fake.on('tick', (t) => console.log(t.fps));
 * fake.advance(1000);       // exactly 20 ticks
 * ```
 */
export function createFakeVoid(options: FakeVoidOptions = {}): FakeVoid {
  const tickHz = options.tickHz ?? 20;
  const stepMs = 1000 / tickHz;
  const snapGrid = options.snapGrid ?? 4;
  const menuKey = options.menuKey ?? 'ShiftRight';
  const random = mulberry32(options.seed ?? 0x5eed);

  const library: Loadout[] = (options.loadouts ?? FAKE_LOADOUTS).map(structuredCloneish);
  if (library.length === 0) throw new Error('createFakeVoid: the loadout library is empty');
  let active =
    library.find((l) => l.id === options.activeLoadoutId) ?? (library[0] as Loadout);

  const handlers = new Map<VoidEventName, Set<VoidEventHandler<never>>>();
  const calls: VoidCallEnvelope[] = [];
  const pendingCaptures: Array<(key: Keybind | null) => void> = [];

  let menuOpen = options.menuOpen ?? false;
  let server: ServerPayload = options.server ?? { host: 'mc.hypixel.net', connected: true };
  let timer: ReturnType<typeof setInterval> | null = null;
  let detachKeyboard: (() => void) | null = null;
  let clockMs = 0;
  let carryMs = 0;
  let ticks = 0;

  // ---- simulated world -----------------------------------------------------
  let keys: KeysPayload = { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0 };
  let fps = 142;
  let ping = 42;
  let pos = { x: 118.0, y: 64.0, z: -212.0, yaw: -45.0 };

  const armor: ArmorSlot[] = [
    { slot: 'helmet', item: 'diamond_helmet', damage: 132, max_damage: 363, count: 1, enchanted: true },
    { slot: 'chestplate', item: 'diamond_chestplate', damage: 116, max_damage: 528, count: 1, enchanted: true },
    { slot: 'leggings', item: 'diamond_leggings', damage: 307, max_damage: 495, count: 1, enchanted: false },
    { slot: 'boots', item: 'diamond_boots', damage: 88, max_damage: 429, count: 1, enchanted: true },
    { slot: 'held', item: 'diamond_sword', damage: 41, max_damage: 1561, count: 1, enchanted: true },
  ];

  // Speed II 1:24 and Strength 0:48, exactly as the HUD-layout frame draws them.
  const fx: PotionEffect[] = [
    { id: 1, name: 'potion.moveSpeed', amplifier: 1, duration_ms: 84000, ambient: false },
    { id: 5, name: 'potion.damageBoost', amplifier: 0, duration_ms: 48000, ambient: false },
  ];

  let lastArmorSignature = signature(armor);
  let lastFxSignature = signature(fx);
  let lastFxSecond = -1;

  function signature(value: unknown): string {
    return JSON.stringify(value);
  }

  // ---- event plumbing ------------------------------------------------------

  function emit(envelope: VoidEventEnvelope): void {
    if (envelope.e === 'menu') menuOpen = envelope.payload;
    const set = handlers.get(envelope.e);
    if (!set) return;
    for (const handler of [...set]) {
      (handler as VoidEventHandler)(envelope.payload);
    }
  }

  // ---- simulation ----------------------------------------------------------

  /** Randomly flip keys, edge-triggered: `keys` is pushed only when something changed. */
  function stepKeys(): void {
    const next: KeysPayload = { ...keys };
    // Movement keys are sticky (a player holds W for a while); clicks are twitchy.
    const flip = (
      k: keyof KeysPayload,
      pressChance: number,
      releaseChance: number,
    ): void => {
      const held = next[k] === 1;
      const roll = random();
      if (held ? roll < releaseChance : roll < pressChance) next[k] = held ? 0 : 1;
    };
    flip('w', 0.08, 0.04);
    flip('a', 0.03, 0.12);
    flip('s', 0.02, 0.16);
    flip('d', 0.03, 0.12);
    flip('lmb', 0.3, 0.45);
    flip('rmb', 0.05, 0.3);
    flip('space', 0.05, 0.5);
    flip('shift', 0.03, 0.1);

    for (const key of Object.keys(next) as Array<keyof KeysPayload>) {
      if (next[key] !== keys[key]) {
        keys = next;
        emit({ e: 'keys', payload: { ...keys } });
        return;
      }
    }
  }

  /** Drift fps, ping, position, durability and potion timers by one tick. */
  function stepWorld(): void {
    // fps 130–160, ping 40–50, both wandering rather than jumping.
    fps = Math.round(clamp(fps + (random() - 0.5) * 6, 130, 160));
    ping = Math.round(clamp(ping + (random() - 0.5) * 3, 40, 50));

    pos.yaw = ((pos.yaw + (random() - 0.5) * 9 + 180) % 360) - 180;
    const speed = keys.w === 1 ? 0.22 : keys.s === 1 ? -0.12 : 0;
    const rad = (pos.yaw * Math.PI) / 180;
    pos.x = Math.round((pos.x - Math.sin(rad) * speed) * 100) / 100;
    pos.z = Math.round((pos.z + Math.cos(rad) * speed) * 100) / 100;
    pos.y = Math.round((64 + Math.sin(ticks / 60) * 1.5) * 100) / 100;

    // Durability ticks down while the player is swinging.
    if (keys.lmb === 1 && random() < 0.35) {
      const held = armor[4];
      if (held && held.damage !== undefined && held.max_damage) {
        held.damage = Math.min(held.max_damage - 1, held.damage + 1);
      }
      const piece = armor[Math.floor(random() * 4)];
      if (piece && piece.damage !== undefined && piece.max_damage) {
        piece.damage = Math.min(piece.max_damage - 1, piece.damage + 1);
      }
    }

    for (const effect of fx) {
      effect.duration_ms = Math.max(0, effect.duration_ms - stepMs);
    }
  }

  /** Build this tick's payload, omitting fields whose sensor has nothing to report. */
  function buildTick(): TickPayload {
    const payload: TickPayload = { fps, ping, pos: { ...pos } };

    const armorSignature = signature(armor);
    if (armorSignature !== lastArmorSignature) {
      lastArmorSignature = armorSignature;
      payload.armor = armor.map((slot) => ({ ...slot }));
    }

    // `fx` is pushed when the set changes; we also push once a second so the countdown
    // on screen stays live without the UI needing to know the tick rate.
    const second = Math.floor(clockMs / 1000);
    const fxSignature = signature(fx.map((e) => `${e.id}:${e.amplifier}`));
    if (fxSignature !== lastFxSignature || second !== lastFxSecond) {
      lastFxSignature = fxSignature;
      lastFxSecond = second;
      payload.fx = fx.map((effect) => ({ ...effect }));
    }
    return payload;
  }

  function tickOnce(): void {
    ticks += 1;
    clockMs += stepMs;
    stepWorld();
    stepKeys();
    emit({ e: 'tick', payload: buildTick() });
  }

  // ---- calls ---------------------------------------------------------------

  function record<C extends VoidCallEnvelope>(envelope: C): void {
    calls.push(envelope);
  }

  function settingsFor(id: Parameters<typeof getModDefaults>[0]): Record<string, unknown> {
    const mods = active.mods as Record<string, Record<string, unknown> | undefined>;
    let state = mods[id];
    if (!state) {
      state = { ...(getModDefaults(id) as unknown as Record<string, unknown>) };
      mods[id] = state;
    }
    return state;
  }

  const bridge: FakeVoid = {
    /* --- VoidBridge ------------------------------------------------------- */

    __isVoidBridge: true,

    on(event, cb) {
      let set = handlers.get(event);
      if (!set) {
        set = new Set();
        handlers.set(event, set);
      }
      set.add(cb as VoidEventHandler<never>);
      return () => bridge.off(event, cb);
    },

    off(event, cb) {
      handlers.get(event)?.delete(cb as VoidEventHandler<never>);
    },

    __emit(envelope: VoidEnvelope | string) {
      const decoded: unknown = typeof envelope === 'string' ? JSON.parse(envelope) : envelope;
      if (isVoidEventEnvelope(decoded)) {
        emit(decoded);
        return;
      }
      if (isVoidCallResultEnvelope(decoded) && decoded.c === 'openKeybindCapture') {
        pendingCaptures.shift()?.(decoded.returns as Keybind | null);
        return;
      }
      throw new Error(`fake-void: unroutable envelope ${JSON.stringify(decoded)}`);
    },

    __hasFocus() {
      return menuOpen;
    },

    setGameplay(id, on) {
      record({ c: 'setGameplay', params: [id, on] });
      settingsFor(id).on = on;
      return on;
    },

    setHud(id, placement) {
      record({ c: 'setHud', params: [id, placement] });
      const snap = (value: number): number =>
        snapGrid > 0 ? Math.round(value / snapGrid) * snapGrid : value;
      const existing = active.hud.find((item) => item.id === id);
      const stored: HUDItem = {
        id,
        anchor: placement.anchor,
        dx: clamp(snap(placement.dx), -4096, 4096),
        dy: clamp(snap(placement.dy), -4096, 4096),
        scale: clamp(placement.scale ?? existing?.scale ?? 1, 0.25, 4),
      };
      if (existing) Object.assign(existing, stored);
      else active.hud.push(stored);
      return { ...stored };
    },

    setModSetting(id, key, value) {
      record({ c: 'setModSetting', params: [id, key, value] });
      if (!isModId(id)) return value;
      const applied = clampSetting(key, value);
      settingsFor(id)[key] = applied;
      return applied;
    },

    switchLoadout(id) {
      record({ c: 'switchLoadout', params: [id] });
      const found = library.find((l) => l.id === id);
      if (!found) return false;
      active = found;
      emit({ e: 'loadout', payload: structuredCloneish(active) });
      return true;
    },

    closeMenu() {
      record({ c: 'closeMenu', params: [] });
      if (menuOpen) emit({ e: 'menu', payload: false });
      return null;
    },

    openKeybindCapture(modId) {
      record({ c: 'openKeybindCapture', params: [modId] });
      return new Promise<Keybind | null>((resolve) => {
        pendingCaptures.push(resolve);
      });
    },

    /* --- FakeVoid controls ------------------------------------------------ */

    emitInitialState() {
      // The library first, then the active loadout: `init` carries both, and the
      // Loadouts frame wants the whole list rather than whatever it has happened to
      // observe (bridge.json, `loadouts_payload`).
      emit({ e: 'loadouts', payload: library.map(structuredCloneish) });
      emit({ e: 'loadout', payload: structuredCloneish(active) });
      emit({ e: 'server', payload: { ...server } });
      emit({ e: 'menu', payload: menuOpen });
    },

    start() {
      if (timer !== null) return;
      bridge.emitInitialState();
      timer = setInterval(() => bridge.advance(stepMs), stepMs);
    },

    stop() {
      if (timer === null) return;
      clearInterval(timer);
      timer = null;
    },

    isRunning() {
      return timer !== null;
    },

    advance(ms) {
      carryMs += ms;
      let guard = 0;
      while (carryMs >= stepMs && guard < 100000) {
        carryMs -= stepMs;
        guard += 1;
        tickOnce();
      }
    },

    tickOnce,

    setMenuOpen(open) {
      if (open === menuOpen) return;
      emit({ e: 'menu', payload: open });
    },

    isMenuOpen() {
      return menuOpen;
    },

    setKeys(patch) {
      const next = { ...keys, ...patch };
      const changed = (Object.keys(next) as Array<keyof KeysPayload>).some(
        (k) => next[k] !== keys[k],
      );
      if (!changed) return;
      keys = next;
      emit({ e: 'keys', payload: { ...keys } });
    },

    getKeys() {
      return { ...keys };
    },

    setServer(next) {
      server = { ...next };
      emit({ e: 'server', payload: { ...server } });
    },

    applyModSetting(id, key, value) {
      // Plays the part of Java changing a setting on its own — an in-game hotkey, or a
      // launcher-side echo. Clamps exactly as `setModSetting` does, then pushes the
      // `setting` event rather than a whole loadout, because one key changed.
      if (!isModId(id)) return value;
      const applied = clampSetting(key, value);
      settingsFor(id)[key] = applied;
      emit({ e: 'setting', payload: { id, key, value: applied } });
      return applied;
    },

    getLoadout() {
      return active;
    },

    getLoadouts() {
      return library;
    },

    resolveKeybindCapture(key) {
      pendingCaptures.shift()?.(key);
    },

    isCapturingKeybind() {
      return pendingCaptures.length > 0;
    },

    getCalls() {
      return [...calls];
    },

    clearCalls() {
      calls.length = 0;
    },

    attachKeyboard(target) {
      const node =
        target ??
        options.keyboardTarget ??
        (globalThis as { document?: EventTarget }).document ??
        null;
      if (!node) return () => {};

      const onKeyDown = (raw: Event): void => {
        const event = raw as KeyboardEvent;
        // A pending keybind capture eats the next key press; Escape cancels it.
        if (pendingCaptures.length > 0) {
          event.preventDefault?.();
          if (event.code === 'Escape') bridge.resolveKeybindCapture(null);
          else bridge.resolveKeybindCapture(keybindFromKeyboardCode(event.code));
          return;
        }
        if (event.code === menuKey) {
          if (event.repeat) return;
          event.preventDefault?.();
          bridge.setMenuOpen(!menuOpen);
        }
      };

      node.addEventListener('keydown', onKeyDown as EventListener);
      const detach = (): void => node.removeEventListener('keydown', onKeyDown as EventListener);
      detachKeyboard = detach;
      return detach;
    },

    install(target) {
      const dest = (target ?? (globalThis as unknown as Record<string, unknown>)) as Record<
        string,
        unknown
      >;
      dest['void'] = bridge;
      return bridge;
    },

    destroy() {
      bridge.stop();
      detachKeyboard?.();
      detachKeyboard = null;
      handlers.clear();
      pendingCaptures.length = 0;
    },
  };

  const wantsKeyboard =
    options.attachKeyboard ??
    Boolean(options.keyboardTarget ?? (globalThis as { document?: unknown }).document);
  if (wantsKeyboard) bridge.attachKeyboard();

  return bridge;
}
