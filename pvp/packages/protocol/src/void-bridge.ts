/**
 * `window.void` — the Java ⇄ JS bridge surface.
 *
 * This module is the TypeScript face of `pvp/schema/bridge.json` (§6.5 of
 * docs/PVP_ARCHITECTURE.md). It contains no game code: it declares the interface, the
 * envelope shapes, and one reference implementation of the shim that builds the object.
 *
 * ## How the real bridge is assembled at runtime
 *
 * 1. The Java host installs exactly one function into the JavaScript context:
 *
 *        window.__void_native(json: string): string
 *
 *    It takes one JSON-encoded **call envelope** `{"c": <name>, "params": [...]}` and
 *    returns one JSON-encoded **call-result envelope** `{"c": <name>, "returns": ...}`.
 *    Ultralight lives inside the JVM, so this hop is synchronous (§6.2, §6.5) — there is
 *    no ack, no request id and no optimistic UI.
 *
 * 2. `void-shim.js` (written by the **mod** owner, shipped inside the JAR) runs before
 *    the app bundle and builds `window.void` on top of `__void_native`.
 *
 * 3. Java pushes events by calling `window.void.__emit(envelope)` with an event envelope
 *    `{"e": <name>, "payload": …}` — either as an object or as a JSON string.
 *
 * {@link installVoidShim} is the reference implementation of step 2. The browser
 * harness and the JAR therefore run the *same* shim semantics; if the two ever
 * disagree, this file is the specification that settles it.
 *
 * ## `openKeybindCapture` is the one asynchronous call
 *
 * Java takes over key input until the next key press, so it cannot answer synchronously.
 * `__void_native` acknowledges the call without a `returns` field, and Java later
 * delivers the resolution as a **call-result envelope** through the same `__emit`
 * channel: `__emit({ c: 'openKeybindCapture', returns: 'V' })` — or `returns: null`
 * when the player pressed Escape. The shim keeps the pending resolvers in FIFO order.
 * The promise never rejects.
 */

import type {
  GameplayModId,
  HUDAnchor,
  HUDItem,
  HUDModId,
  Keybind,
  KeysPayload,
  Loadout,
  LoadoutId,
  ModId,
  ServerPayload,
  TickPayload,
} from './generated/schema.js';

/* -------------------------------------------------------------------------- */
/* Events                                                                     */
/* -------------------------------------------------------------------------- */

/** The five channels Java pushes on. Closed set; any other name is a programming error. */
export const VOID_EVENTS = ['keys', 'tick', 'server', 'loadout', 'menu'] as const;

/** Name of one of the five push channels. */
export type VoidEventName = (typeof VOID_EVENTS)[number];

/** Payload handed to the handler of each channel. */
export interface VoidEventPayloadMap {
  /** Edge-triggered key state; pushed only when a key changes. */
  keys: KeysPayload;
  /** Per-tick telemetry, coalesced to one push per game tick (20 Hz). */
  tick: TickPayload;
  /** Server connect / disconnect. */
  server: ServerPayload;
  /** The full loadout that is now active. Treat as whole-state replacement. */
  loadout: Loadout;
  /** True when VoidMenuScreen opened, false when it closed. */
  menu: boolean;
}

/** Handler signature for a given channel. */
export type VoidEventHandler<E extends VoidEventName = VoidEventName> = (
  payload: VoidEventPayloadMap[E],
) => void;

/** One Java → JS push, as `__emit` receives it. */
export type VoidEventEnvelope = {
  [E in VoidEventName]: { e: E; payload: VoidEventPayloadMap[E] };
}[VoidEventName];

/* -------------------------------------------------------------------------- */
/* Calls                                                                      */
/* -------------------------------------------------------------------------- */

/** The six methods JS may call on `window.void`. Closed set. */
export const VOID_CALLS = [
  'setGameplay',
  'setHud',
  'setModSetting',
  'switchLoadout',
  'closeMenu',
  'openKeybindCapture',
] as const;

/** Name of one of the six calls. */
export type VoidCallName = (typeof VOID_CALLS)[number];

/** Value a mod setting may take. Scalars only; no mod has an object- or array-valued setting. */
export type ModSettingValue = boolean | number | string | null;

/** The placement `setHud` accepts: a {@link HUDItem} minus its `id`. */
export interface HudPlacement {
  /** Screen anchor the offsets are measured from. */
  anchor: HUDAnchor;
  /** Horizontal offset in unscaled GUI pixels from the anchor. */
  dx: number;
  /** Vertical offset in unscaled GUI pixels from the anchor. */
  dy: number;
  /** Per-item size multiplier; omitted means leave unchanged. */
  scale?: number;
}

/** Positional arguments of each call, exactly as `bridge.json` defines them. */
export interface VoidCallParamsMap {
  setGameplay: [id: GameplayModId, on: boolean];
  setHud: [id: HUDModId, placement: HudPlacement];
  setModSetting: [id: ModId, key: string, value: ModSettingValue];
  switchLoadout: [id: LoadoutId];
  closeMenu: [];
  openKeybindCapture: [modId: ModId];
}

/** Return value of each call. `openKeybindCapture` resolves this through a Promise. */
export interface VoidCallReturnsMap {
  setGameplay: boolean;
  setHud: HUDItem;
  setModSetting: ModSettingValue;
  switchLoadout: boolean;
  closeMenu: null;
  openKeybindCapture: Keybind | null;
}

/** One JS → Java call envelope. */
export type VoidCallEnvelope = {
  [C in VoidCallName]: { c: C; params: VoidCallParamsMap[C] };
}[VoidCallName];

/** One call-result envelope. */
export type VoidCallResultEnvelope = {
  [C in VoidCallName]: { c: C; returns: VoidCallReturnsMap[C] };
}[VoidCallName];

/** Anything `__emit` accepts, and anything the `?debug` harness records. */
export type VoidEnvelope = VoidEventEnvelope | VoidCallEnvelope | VoidCallResultEnvelope;

/* -------------------------------------------------------------------------- */
/* The bridge object                                                          */
/* -------------------------------------------------------------------------- */

/**
 * The single `window.void` object. Implemented by the mod for real, by
 * {@link createFakeVoid} in a browser, and installed by {@link installVoidShim} on
 * top of `window.__void_native`.
 */
export interface VoidBridge {
  /**
   * Subscribe to a push channel. Returns an unsubscribe function; calling it is
   * equivalent to `off(event, cb)`.
   */
  on<E extends VoidEventName>(event: E, cb: VoidEventHandler<E>): () => void;

  /** Unsubscribe a handler previously passed to {@link VoidBridge.on}. */
  off<E extends VoidEventName>(event: E, cb: VoidEventHandler<E>): void;

  /**
   * Deliver one envelope into the page. Java calls this to push events; it also accepts
   * a deferred `openKeybindCapture` call result. Accepts the envelope as an object or
   * as its JSON encoding, because the Java side hands over a string.
   */
  __emit(envelope: VoidEnvelope | string): void;

  /**
   * Whether the web layer currently owns keyboard/mouse input. False in HUD mode: in
   * HUD mode Ultralight receives no input events at all (§6.3), so this tracks the
   * `menu` channel.
   */
  __hasFocus(): boolean;

  /** Toggle one gameplay mod. Returns the state actually applied. */
  setGameplay(id: GameplayModId, on: boolean): boolean;

  /** Move or scale one HUD item. Returns the stored item after snapping and clamping. */
  setHud(id: HUDModId, placement: HudPlacement): HUDItem;

  /** Write one setting of one mod. Returns the value actually stored, after clamping. */
  setModSetting(id: ModId, key: string, value: ModSettingValue): ModSettingValue;

  /** Switch the active loadout. Returns false when no loadout has that id. */
  switchLoadout(id: LoadoutId): boolean;

  /** Close VoidMenuScreen and return the mouse to the game. Always returns null. */
  closeMenu(): null;

  /**
   * Take over key input until the next key press. Resolves with the captured key, or
   * null when the player cancelled with Escape. Never rejects.
   */
  openKeybindCapture(modId: ModId): Promise<Keybind | null>;
}

declare global {
  interface Window {
    /**
     * The bridge. Present in game (installed by `void-shim.js`) and in the `?debug`
     * harness (installed by {@link createFakeVoid}). Absent in a plain browser.
     */
    void?: VoidBridge;
    /**
     * Installed by the Java host before the app bundle runs. Takes a JSON call envelope
     * and returns a JSON call-result envelope. Not for application code — use
     * `window.void`.
     */
    __void_native?: (json: string) => string;
  }
}

/* -------------------------------------------------------------------------- */
/* Envelope guards                                                            */
/* -------------------------------------------------------------------------- */

/** True when `value` is one of the five event envelopes. */
export function isVoidEventEnvelope(value: unknown): value is VoidEventEnvelope {
  return (
    typeof value === 'object' &&
    value !== null &&
    'e' in value &&
    'payload' in value &&
    (VOID_EVENTS as readonly string[]).includes((value as { e: string }).e)
  );
}

/** True when `value` is a JS → Java call envelope. */
export function isVoidCallEnvelope(value: unknown): value is VoidCallEnvelope {
  return (
    typeof value === 'object' &&
    value !== null &&
    'c' in value &&
    'params' in value &&
    (VOID_CALLS as readonly string[]).includes((value as { c: string }).c)
  );
}

/** True when `value` is a call-result envelope. */
export function isVoidCallResultEnvelope(value: unknown): value is VoidCallResultEnvelope {
  return (
    typeof value === 'object' &&
    value !== null &&
    'c' in value &&
    'returns' in value &&
    (VOID_CALLS as readonly string[]).includes((value as { c: string }).c)
  );
}

/* -------------------------------------------------------------------------- */
/* Reference shim                                                             */
/* -------------------------------------------------------------------------- */

/** Options for {@link installVoidShim}. */
export interface InstallVoidShimOptions {
  /**
   * The native entry point. Defaults to `globalThis.__void_native`, which is what the
   * Java host installs.
   */
  native?: (json: string) => string;
  /**
   * Where to install the bridge. Defaults to `globalThis`, i.e. `window.void`.
   * Pass `null` to build the object without installing it anywhere.
   */
  target?: Record<string, unknown> | null;
  /**
   * Called when a handler throws, or when an envelope cannot be decoded. Defaults to
   * `console.error`. A throwing handler must never break the Java-side push loop.
   */
  onError?: (error: unknown, context: string) => void;
}

/**
 * Build `window.void` on top of `window.__void_native`.
 *
 * This is the TypeScript reference implementation of `void-shim.js`. The mod ships a
 * hand-written JavaScript copy inside the JAR (the in-game bundle must not depend on a
 * module graph before the app boots); this function is the same behaviour, used by the
 * browser harness and by tests, so the two surfaces cannot drift apart.
 *
 * @returns The bridge object, also assigned to `target.void` unless `target` is null.
 */
export function installVoidShim(options: InstallVoidShimOptions = {}): VoidBridge {
  const globalTarget = globalThis as unknown as Record<string, unknown>;
  const target = options.target === undefined ? globalTarget : options.target;
  const native =
    options.native ?? (globalTarget['__void_native'] as ((json: string) => string) | undefined);
  const onError =
    options.onError ??
    ((error: unknown, context: string) => {
      // eslint-disable-next-line no-console
      console.error(`[void-shim] ${context}`, error);
    });

  if (typeof native !== 'function') {
    throw new Error(
      'installVoidShim: window.__void_native is not installed. The Java host installs it ' +
        'before the UI bundle runs; in a browser use createFakeVoid() instead.',
    );
  }

  const handlers = new Map<VoidEventName, Set<VoidEventHandler<never>>>();
  const pendingCaptures: Array<(key: Keybind | null) => void> = [];
  let menuOpen = false;

  function call<C extends VoidCallName>(
    name: C,
    params: VoidCallParamsMap[C],
  ): VoidCallReturnsMap[C] | undefined {
    const raw = native!(JSON.stringify({ c: name, params }));
    if (raw === undefined || raw === null || raw === '') return undefined;
    let decoded: unknown;
    try {
      decoded = JSON.parse(raw);
    } catch (error) {
      onError(error, `could not decode the result of ${name}`);
      return undefined;
    }
    if (
      typeof decoded === 'object' &&
      decoded !== null &&
      'returns' in decoded &&
      (decoded as { c?: string }).c === name
    ) {
      return (decoded as { returns: VoidCallReturnsMap[C] }).returns;
    }
    return undefined;
  }

  function dispatch(envelope: VoidEventEnvelope): void {
    if (envelope.e === 'menu') menuOpen = envelope.payload;
    const set = handlers.get(envelope.e);
    if (!set) return;
    // Copy first: a handler may unsubscribe itself while we iterate.
    for (const handler of [...set]) {
      try {
        (handler as VoidEventHandler)(envelope.payload);
      } catch (error) {
        onError(error, `handler for "${envelope.e}" threw`);
      }
    }
  }

  const bridge: VoidBridge = {
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
      let decoded: unknown = envelope;
      if (typeof envelope === 'string') {
        try {
          decoded = JSON.parse(envelope);
        } catch (error) {
          onError(error, 'could not decode an emitted envelope');
          return;
        }
      }
      if (isVoidEventEnvelope(decoded)) {
        dispatch(decoded);
        return;
      }
      if (isVoidCallResultEnvelope(decoded) && decoded.c === 'openKeybindCapture') {
        const resolve = pendingCaptures.shift();
        resolve?.(decoded.returns as Keybind | null);
        return;
      }
      onError(new Error(`unroutable envelope: ${JSON.stringify(decoded)}`), '__emit');
    },

    __hasFocus() {
      return menuOpen;
    },

    setGameplay(id, on) {
      return call('setGameplay', [id, on]) ?? on;
    },

    setHud(id, placement) {
      return call('setHud', [id, placement]) ?? { id, ...placement };
    },

    setModSetting(id, key, value) {
      const applied = call('setModSetting', [id, key, value]);
      return applied === undefined ? value : applied;
    },

    switchLoadout(id) {
      return call('switchLoadout', [id]) ?? false;
    },

    closeMenu() {
      call('closeMenu', []);
      return null;
    },

    openKeybindCapture(modId) {
      const immediate = call('openKeybindCapture', [modId]);
      if (immediate !== undefined) return Promise.resolve(immediate);
      return new Promise<Keybind | null>((resolve) => {
        pendingCaptures.push(resolve);
      });
    },
  };

  if (target) target['void'] = bridge;
  return bridge;
}
