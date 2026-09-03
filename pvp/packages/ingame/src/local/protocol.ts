/**
 * LOCAL FALLBACK for `@void/protocol`.
 *
 * `packages/protocol` is being written in parallel. Everything below is coded
 * straight off `pvp/schema/bridge.json`, `loadout.json` and `mods.json`, with the
 * same names and shapes that package exports, so consolidating is a one-line edit
 * in `src/bridge/protocol.ts` (see the note there).
 *
 * Nothing in this file reaches the network, and nothing here is game code.
 */

/* -------------------------------------------------------------------------- */
/* Schema types (mods.json / loadout.json / bridge.json)                       */
/* -------------------------------------------------------------------------- */

export type ModId =
  | 'fps'
  | 'keystrokes'
  | 'cps'
  | 'ping'
  | 'coordinates'
  | 'armor_status'
  | 'potion_effects'
  | 'toggle_sprint'
  | 'fullbright'
  | 'hitboxes'
  | 'zoom'
  | 'crosshair';

export type HUDModId =
  | 'fps'
  | 'keystrokes'
  | 'cps'
  | 'ping'
  | 'coordinates'
  | 'armor_status'
  | 'potion_effects';

export type GameplayModId = 'toggle_sprint' | 'fullbright' | 'hitboxes' | 'zoom' | 'crosshair';

export type ModKind = 'hud' | 'gameplay';
export type HypixelSafe = 'safe' | 'grey';

/** LWJGL 2 key name, MOUSE0..MOUSE7, or NONE. */
export type Keybind = string;

export type HUDAnchor =
  | 'top-left'
  | 'top'
  | 'top-right'
  | 'left'
  | 'center'
  | 'right'
  | 'bottom-left'
  | 'bottom'
  | 'bottom-right';

export interface HUDItem {
  id: HUDModId;
  anchor: HUDAnchor;
  dx: number;
  dy: number;
  scale?: number;
}

export type ModSettingValue = boolean | number | string | null;

export type ModSettings = Record<string, ModSettingValue>;

export type ModStates = Partial<Record<ModId, ModSettings>>;

export interface LoadoutStats {
  played_ms?: number;
  fps_avg?: number;
}

export interface Loadout {
  id: string;
  name: string;
  icon: string;
  server?: string | null;
  mc: string;
  mods: ModStates;
  hud: HUDItem[];
  stats?: LoadoutStats;
}

export interface LoadoutSummary {
  id: string;
  name: string;
  icon: string;
  server?: string | null;
  stats?: LoadoutStats;
}

export type KeyState = 0 | 1;

export interface KeysPayload {
  w: KeyState;
  a: KeyState;
  s: KeyState;
  d: KeyState;
  lmb: KeyState;
  rmb: KeyState;
  space: KeyState;
  shift: KeyState;
}

export interface Position {
  x: number;
  y: number;
  z: number;
  yaw: number;
}

export interface ArmorSlot {
  slot: 'helmet' | 'chestplate' | 'leggings' | 'boots' | 'held';
  item: string | null;
  damage?: number;
  max_damage?: number;
  count?: number;
  enchanted?: boolean;
}

export interface PotionEffect {
  id: number;
  name?: string;
  amplifier: number;
  duration_ms: number;
  ambient?: boolean;
}

export interface TickPayload {
  fps?: number;
  ping?: number;
  pos?: Position;
  armor?: ArmorSlot[];
  fx?: PotionEffect[];
}

export interface ServerPayload {
  host: string;
  connected: boolean;
}

/* -------------------------------------------------------------------------- */
/* Bridge surface                                                             */
/* -------------------------------------------------------------------------- */

export const VOID_EVENTS = ['keys', 'tick', 'server', 'loadout', 'menu'] as const;
export type VoidEventName = (typeof VOID_EVENTS)[number];

export interface VoidEventPayloadMap {
  keys: KeysPayload;
  tick: TickPayload;
  server: ServerPayload;
  loadout: Loadout;
  menu: boolean;
}

export type VoidEventHandler<E extends VoidEventName = VoidEventName> = (
  payload: VoidEventPayloadMap[E],
) => void;

export type VoidEventEnvelope = {
  [E in VoidEventName]: { e: E; payload: VoidEventPayloadMap[E] };
}[VoidEventName];

export const VOID_CALLS = [
  'setGameplay',
  'setHud',
  'setModSetting',
  'switchLoadout',
  'closeMenu',
  'openKeybindCapture',
] as const;
export type VoidCallName = (typeof VOID_CALLS)[number];

export interface HudPlacement {
  anchor: HUDAnchor;
  dx: number;
  dy: number;
  scale?: number;
}

export interface VoidCallParamsMap {
  setGameplay: [id: GameplayModId, on: boolean];
  setHud: [id: HUDModId, placement: HudPlacement];
  setModSetting: [id: ModId, key: string, value: ModSettingValue];
  switchLoadout: [id: string];
  closeMenu: [];
  openKeybindCapture: [modId: ModId];
}

export interface VoidCallReturnsMap {
  setGameplay: boolean;
  setHud: HUDItem;
  setModSetting: ModSettingValue;
  switchLoadout: boolean;
  closeMenu: null;
  openKeybindCapture: Keybind | null;
}

export type VoidCallEnvelope = {
  [C in VoidCallName]: { c: C; params: VoidCallParamsMap[C] };
}[VoidCallName];

export type VoidCallResultEnvelope = {
  [C in VoidCallName]: { c: C; returns: VoidCallReturnsMap[C] };
}[VoidCallName];

export type VoidEnvelope = VoidEventEnvelope | VoidCallEnvelope | VoidCallResultEnvelope;

export interface VoidBridge {
  on<E extends VoidEventName>(event: E, cb: VoidEventHandler<E>): () => void;
  off<E extends VoidEventName>(event: E, cb: VoidEventHandler<E>): void;
  __emit(envelope: VoidEnvelope | string): void;
  __hasFocus(): boolean;
  setGameplay(id: GameplayModId, on: boolean): boolean;
  setHud(id: HUDModId, placement: HudPlacement): HUDItem;
  setModSetting(id: ModId, key: string, value: ModSettingValue): ModSettingValue;
  switchLoadout(id: string): boolean;
  closeMenu(): null;
  openKeybindCapture(modId: ModId): Promise<Keybind | null>;
}

declare global {
  interface Window {
    void?: VoidBridge;
    __void_native?: (json: string) => string;
  }
}

export function isVoidEventEnvelope(value: unknown): value is VoidEventEnvelope {
  return (
    typeof value === 'object' &&
    value !== null &&
    'e' in value &&
    'payload' in value &&
    (VOID_EVENTS as readonly string[]).includes((value as { e: string }).e)
  );
}

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
/* installVoidShim — window.void on top of window.__void_native               */
/* -------------------------------------------------------------------------- */

export interface InstallVoidShimOptions {
  native?: (json: string) => string;
  target?: Record<string, unknown> | null;
  onError?: (error: unknown, context: string) => void;
}

export function installVoidShim(options: InstallVoidShimOptions = {}): VoidBridge {
  const globalTarget = globalThis as unknown as Record<string, unknown>;
  const target = options.target === undefined ? globalTarget : options.target;
  const native =
    options.native ?? (globalTarget['__void_native'] as ((json: string) => string) | undefined);
  const onError =
    options.onError ??
    ((error: unknown, context: string) => {
      console.error(`[void-shim] ${context}`, error);
    });

  if (typeof native !== 'function') {
    throw new Error(
      'installVoidShim: window.__void_native is not installed. In a browser use createFakeVoid().',
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
        if (decoded.e === 'menu') menuOpen = decoded.payload;
        const set = handlers.get(decoded.e);
        if (set) {
          for (const handler of [...set]) {
            try {
              (handler as VoidEventHandler)(decoded.payload);
            } catch (error) {
              onError(error, `handler for "${decoded.e}" threw`);
            }
          }
        }
        return;
      }
      if (isVoidCallResultEnvelope(decoded) && decoded.c === 'openKeybindCapture') {
        pendingCaptures.shift()?.(decoded.returns as Keybind | null);
        return;
      }
      onError(new Error('unroutable envelope'), '__emit');
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
