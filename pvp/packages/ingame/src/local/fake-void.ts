/**
 * LOCAL FALLBACK for `createFakeVoid()` from `@void/protocol`.
 *
 * A `window.void` that behaves like the Java bridge but runs in a normal
 * browser — the `?debug` harness of §9, which is the only way to inspect this
 * bundle since there are no devtools in game.
 *
 * It plays the part of Java, not of the app: it owns the loadout library, it
 * applies and clamps calls the way `void-loadout` and the actuators will, and it
 * pushes the five channels. That includes owning **Right Shift** — in game the
 * mod's `KeyBinding` opens `VoidMenuScreen` and the app never binds the key
 * (§6.3), so the fake has to be the one listening for it.
 */

import {
  type GameplayModId,
  type HUDItem,
  type HUDModId,
  type Keybind,
  type KeysPayload,
  type Loadout,
  type ModId,
  type ModSettingValue,
  type TickPayload,
  type VoidBridge,
  type VoidEnvelope,
  type VoidEventHandler,
  type VoidEventName,
  isVoidEventEnvelope,
} from './protocol';

/* -------------------------------------------------------------------------- */
/* The library the Loadouts frame (244:1130) shows                            */
/* -------------------------------------------------------------------------- */

const SWORD_PVP: Loadout = {
  id: 'sword-pvp',
  name: 'Sword PvP',
  icon: 'sword',
  server: 'hypixel',
  mc: '1.8.9',
  mods: {
    fps: { on: true, scale: 1, opacity: 1, color: '#FFFFFF', show_label: true },
    keystrokes: {
      on: true,
      scale: 1,
      opacity: 0.85,
      keybind: 'RSHIFT',
      show_mouse: true,
      show_spacebar: false,
      show_cps: true,
      corner_radius: 8,
    },
    cps: { on: true, scale: 1, opacity: 1, mode: 'both', window_ms: 1000 },
    ping: { on: true, scale: 1, opacity: 1, show_label: true, good_ms: 60, bad_ms: 150 },
    coordinates: { on: true, scale: 1, opacity: 1, decimals: 0, show_direction: true, layout: 'inline' },
    armor_status: {
      on: true,
      scale: 1,
      opacity: 1,
      orientation: 'vertical',
      show_durability: true,
      show_held_item: false,
    },
    potion_effects: { on: true, scale: 1, opacity: 1, show_duration: true, show_amplifier: true, hide_ambient: false },
    toggle_sprint: { on: true, mode: 'toggle', sneak_too: false, show_status: true },
    fullbright: { on: false, gamma: 10 },
    hitboxes: { on: false, line_width: 2, color: '#FFFFFFFF', show_eye_line: false },
    zoom: { on: true, key: 'C', fov_divisor: 4, smooth: true, cinematic: false },
    crosshair: { on: true, style: 'cross', size: 5, thickness: 1, gap: 2, color: '#FFFFFFFF', outline: true },
  },
  hud: [
    { id: 'fps', anchor: 'top-left', dx: 23, dy: 23 },
    { id: 'ping', anchor: 'top-left', dx: 23, dy: 65 },
    { id: 'coordinates', anchor: 'top-left', dx: 23, dy: 103 },
    { id: 'potion_effects', anchor: 'top-right', dx: -25, dy: 23 },
    { id: 'armor_status', anchor: 'top-right', dx: -25, dy: 299 },
    { id: 'keystrokes', anchor: 'bottom-left', dx: 31, dy: -109, scale: 1 },
    { id: 'cps', anchor: 'bottom-left', dx: 175, dy: -108, scale: 1 },
  ],
  stats: { played_ms: 15600000, fps_avg: 142 },
};

const BEDWARS: Loadout = {
  id: 'bedwars',
  name: 'Bedwars',
  icon: 'box',
  server: 'hypixel',
  mc: '1.8.9',
  mods: {
    keystrokes: { on: true, scale: 1, opacity: 0.85, show_mouse: true, show_spacebar: true, show_cps: false },
    armor_status: { on: true, orientation: 'vertical', show_durability: true, show_held_item: true },
    potion_effects: { on: true, show_duration: true, show_amplifier: true },
    fullbright: { on: true, gamma: 12 },
    ping: { on: true, show_label: true, good_ms: 60, bad_ms: 150 },
    cps: { on: true, mode: 'both', window_ms: 1000 },
    zoom: { on: true, key: 'V', fov_divisor: 4, smooth: true },
    toggle_sprint: { on: true, mode: 'toggle' },
  },
  hud: [
    { id: 'keystrokes', anchor: 'bottom-left', dx: 24, dy: -96 },
    { id: 'cps', anchor: 'bottom', dx: 0, dy: -60, scale: 0.75 },
    { id: 'armor_status', anchor: 'right', dx: -24, dy: 0 },
    { id: 'ping', anchor: 'top-left', dx: 23, dy: 23 },
    { id: 'potion_effects', anchor: 'top-right', dx: -25, dy: 23 },
  ],
  stats: { played_ms: 7500000, fps_avg: 0 },
};

const UHC: Loadout = {
  id: 'uhc',
  name: 'UHC',
  icon: 'heart',
  server: 'minemen',
  mc: '1.8.9',
  mods: {
    armor_status: { on: true, orientation: 'vertical', show_durability: true },
    potion_effects: { on: true },
    coordinates: { on: true, decimals: 0, show_direction: true, layout: 'inline' },
    hitboxes: { on: true, line_width: 2 },
    zoom: { on: true, key: 'C', fov_divisor: 4 },
    fps: { on: true, show_label: true },
  },
  hud: [
    { id: 'fps', anchor: 'top-left', dx: 23, dy: 23 },
    { id: 'coordinates', anchor: 'top-left', dx: 23, dy: 65 },
    { id: 'armor_status', anchor: 'right', dx: -25, dy: 0 },
    { id: 'potion_effects', anchor: 'top-right', dx: -25, dy: 23 },
  ],
  stats: { played_ms: 2880000, fps_avg: 0 },
};

/** The library `init.loadouts` would carry. Deep-cloned on every switch. */
export const FAKE_LOADOUTS: Loadout[] = [SWORD_PVP, BEDWARS, UHC];

/** Mod counts the Loadouts frame prints ("24 mods on"). Cosmetic. */
export const FAKE_MOD_COUNTS: Record<string, number> = {
  'sword-pvp': 24,
  bedwars: 19,
  uhc: 16,
};

/* -------------------------------------------------------------------------- */

const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

const CLAMPS: Record<string, [number, number]> = {
  scale: [0.25, 4],
  opacity: [0, 1],
  window_ms: [200, 5000],
  good_ms: [0, 1000],
  bad_ms: [0, 2000],
  decimals: [0, 3],
  gamma: [1, 15],
  line_width: [0.5, 5],
  fov_divisor: [1.1, 10],
  size: [1, 20],
  thickness: [1, 5],
  gap: [0, 10],
  corner_radius: [0, 20],
};

export interface FakeVoidOptions {
  /** Where to install the bridge. `null` builds it without installing. */
  target?: Record<string, unknown> | null;
  /** Loadout the session starts on. Defaults to `sword-pvp`. */
  activeId?: string;
  /** Whether the menu is open at boot. Defaults to false. */
  menuOpen?: boolean;
  /** Drive `keys` from real browser input and `tick` from a 20 Hz timer. */
  live?: boolean;
}

export interface FakeVoidBridge extends VoidBridge {
  /** The loadout library, the way `init.loadouts` carries it. */
  __loadouts(): Loadout[];
  /** Push an arbitrary envelope, e.g. to replay a recorded session. */
  __push(envelope: VoidEnvelope): void;
  /** Open / close the menu the way the Java `KeyBinding` would. */
  __setMenu(open: boolean): void;
  /** Stop the timers and the input listeners. */
  __dispose(): void;
}

/**
 * Build a fake `window.void` for the browser harness.
 *
 * Behaviour differences from the real bridge, all deliberate:
 *   · it owns Right Shift, L and Escape, because Java does in game;
 *   · `openKeybindCapture` resolves on the next `keydown` instead of taking over
 *     LWJGL input;
 *   · `tick` is a plausible synthetic feed rather than real telemetry.
 */
export function createFakeVoid(options: FakeVoidOptions = {}): FakeVoidBridge {
  const target = options.target === undefined ? (globalThis as unknown as Record<string, unknown>) : options.target;
  const live = options.live ?? true;

  const library: Loadout[] = FAKE_LOADOUTS.map((l) => structuredClone(l));
  let active: Loadout = library.find((l) => l.id === options.activeId) ?? library[0]!;
  let menuOpen = options.menuOpen ?? false;

  const handlers = new Map<VoidEventName, Set<VoidEventHandler<never>>>();
  const disposers: Array<() => void> = [];
  let captureResolve: ((k: Keybind | null) => void) | null = null;

  function emit(envelope: VoidEnvelope): void {
    if (!isVoidEventEnvelope(envelope)) return;
    if (envelope.e === 'menu') menuOpen = envelope.payload;
    const set = handlers.get(envelope.e);
    if (!set) return;
    for (const handler of [...set]) {
      try {
        (handler as VoidEventHandler)(envelope.payload);
      } catch (error) {
        console.error('[fake-void] handler threw', error);
      }
    }
  }

  const pushLoadout = () => emit({ e: 'loadout', payload: structuredClone(active) });

  /* ------------------------------------------------------------------ keys */

  const keys: KeysPayload = { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0 };

  function setKey(field: keyof KeysPayload, state: 0 | 1): void {
    if (keys[field] === state) return; // edge-triggered: no push when unchanged
    keys[field] = state;
    emit({ e: 'keys', payload: { ...keys } });
  }

  const CODE_TO_KEY: Record<string, keyof KeysPayload> = {
    KeyW: 'w',
    KeyA: 'a',
    KeyS: 's',
    KeyD: 'd',
    Space: 'space',
    ShiftLeft: 'shift',
  };

  /* ------------------------------------------------------------------ tick */

  let t = 0;
  function tick(): void {
    t += 1;
    const payload: TickPayload = {
      fps: 138 + Math.round(Math.sin(t / 9) * 6),
      ping: 42 + Math.round(Math.sin(t / 21) * 5),
      pos: {
        x: 118 + Math.sin(t / 30) * 3,
        y: 64,
        z: -212 + Math.cos(t / 30) * 3,
        yaw: -45,
      },
    };
    if (t % 20 === 0) {
      payload.armor = [
        { slot: 'helmet', item: 'diamond_helmet', damage: 132, max_damage: 363, count: 1, enchanted: true },
        { slot: 'chestplate', item: 'diamond_chestplate', damage: 116, max_damage: 528, count: 1, enchanted: true },
        { slot: 'leggings', item: 'diamond_leggings', damage: 307, max_damage: 495, count: 1, enchanted: false },
        { slot: 'boots', item: 'diamond_boots', damage: 88, max_damage: 429, count: 1, enchanted: true },
        { slot: 'held', item: 'diamond_sword', damage: 3, max_damage: 1561, count: 1, enchanted: true },
      ];
      payload.fx = [
        { id: 1, name: 'potion.moveSpeed', amplifier: 1, duration_ms: Math.max(0, 84000 - t * 50), ambient: false },
        { id: 5, name: 'potion.damageBoost', amplifier: 0, duration_ms: Math.max(0, 48000 - t * 50), ambient: false },
      ];
    }
    emit({ e: 'tick', payload });
  }

  /* ----------------------------------------------------------------- calls */

  const bridge: FakeVoidBridge = {
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
    __emit(envelope) {
      emit(typeof envelope === 'string' ? (JSON.parse(envelope) as VoidEnvelope) : envelope);
    },
    __hasFocus() {
      return menuOpen;
    },

    setGameplay(id: GameplayModId, on: boolean) {
      const entry = (active.mods[id] ??= {});
      entry.on = on;
      pushLoadout();
      return on;
    },

    setHud(id: HUDModId, placement) {
      const stored: HUDItem = {
        id,
        anchor: placement.anchor,
        dx: clamp(Math.round(placement.dx), -4096, 4096),
        dy: clamp(Math.round(placement.dy), -4096, 4096),
        ...(placement.scale === undefined ? {} : { scale: clamp(placement.scale, 0.25, 4) }),
      };
      const index = active.hud.findIndex((h) => h.id === id);
      if (index >= 0) active.hud[index] = { ...active.hud[index], ...stored };
      else active.hud.push(stored);
      pushLoadout();
      return active.hud.find((h) => h.id === id)!;
    },

    setModSetting(id: ModId, key: string, value: ModSettingValue) {
      const entry = (active.mods[id] ??= {});
      let applied = value;
      const range = CLAMPS[key];
      if (range && typeof value === 'number') applied = clamp(value, range[0], range[1]);
      entry[key] = applied;
      pushLoadout();
      return applied;
    },

    switchLoadout(id: string) {
      const next = library.find((l) => l.id === id);
      if (!next) return false;
      active = next;
      pushLoadout();
      return true;
    },

    closeMenu() {
      emit({ e: 'menu', payload: false });
      return null;
    },

    openKeybindCapture() {
      return new Promise<Keybind | null>((resolve) => {
        captureResolve = resolve;
      });
    },

    __loadouts: () => library.map((l) => structuredClone(l)),
    __push: emit,
    __setMenu: (open: boolean) => emit({ e: 'menu', payload: open }),
    __dispose() {
      for (const d of disposers.splice(0)) d();
    },
  };

  /* ------------------------------------------------------------- listeners */

  if (live && typeof window !== 'undefined') {
    const onKeyDown = (e: KeyboardEvent) => {
      if (captureResolve) {
        e.preventDefault();
        const resolve = captureResolve;
        captureResolve = null;
        resolve(e.key === 'Escape' ? null : lwjglName(e));
        return;
      }
      // Java owns these two in game.
      if (e.code === 'ShiftRight') {
        e.preventDefault();
        bridge.__setMenu(!menuOpen);
        return;
      }
      if (e.code === 'KeyL' && !menuOpen) {
        const i = library.findIndex((l) => l.id === active.id);
        active = library[(i + 1) % library.length]!;
        pushLoadout();
        return;
      }
      const field = CODE_TO_KEY[e.code];
      if (field && !menuOpen) setKey(field, 1);
    };
    const onKeyUp = (e: KeyboardEvent) => {
      const field = CODE_TO_KEY[e.code];
      if (field) setKey(field, 0);
    };
    const onMouseDown = (e: MouseEvent) => {
      if (menuOpen) return;
      if (e.button === 0) setKey('lmb', 1);
      if (e.button === 2) setKey('rmb', 1);
    };
    const onMouseUp = (e: MouseEvent) => {
      if (e.button === 0) setKey('lmb', 0);
      if (e.button === 2) setKey('rmb', 0);
    };

    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    window.addEventListener('mousedown', onMouseDown);
    window.addEventListener('mouseup', onMouseUp);
    disposers.push(() => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      window.removeEventListener('mousedown', onMouseDown);
      window.removeEventListener('mouseup', onMouseUp);
    });

    const tickTimer = setInterval(tick, 50); // 20 Hz, like the game tick
    disposers.push(() => clearInterval(tickTimer));
  }

  if (target) target['void'] = bridge;

  // Deliver the boot state on a microtask, the way `hello` arrives after load.
  queueMicrotask(() => {
    pushLoadout();
    emit({ e: 'server', payload: { host: 'mc.hypixel.net', connected: true } });
    emit({ e: 'menu', payload: menuOpen });
    tick();
  });

  return bridge;
}

/** Best-effort DOM `KeyboardEvent` → LWJGL 2 key name, for the fake capture. */
function lwjglName(e: KeyboardEvent): Keybind {
  const code = e.code;
  if (/^Key[A-Z]$/.test(code)) return code.slice(3);
  if (/^Digit[0-9]$/.test(code)) return code.slice(5);
  if (/^F[0-9]{1,2}$/.test(code)) return code;
  const map: Record<string, string> = {
    Space: 'SPACE',
    Tab: 'TAB',
    Escape: 'ESCAPE',
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
    ShiftLeft: 'LSHIFT',
    ShiftRight: 'RSHIFT',
    ControlLeft: 'LCONTROL',
    ControlRight: 'RCONTROL',
    AltLeft: 'LMENU',
    AltRight: 'RMENU',
    CapsLock: 'CAPITAL',
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
  return map[code] ?? 'NONE';
}
