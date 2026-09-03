/**
 * The one store. `window.void.on(...)` is the only writer of live game data
 * (§9); everything else here is UI state that never leaves the page.
 *
 * Rendering discipline (§9, and the "update only the DOM that changed" rule):
 *   · `tick` fields are flattened to primitives, so the FPS chip does not
 *     re-render because the ping moved;
 *   · `armor` / `fx` arrays are replaced only on the ticks that carry them
 *     (bridge.json: an absent field means unchanged);
 *   · `keys` is edge-triggered and lands as one object, and every consumer
 *     subscribes to the single field it draws;
 *   · there is no animation frame loop anywhere in this bundle. The 20 Hz
 *     `tick` push is the clock.
 */

import { create } from 'zustand';
import {
  MOD_REGISTRY,
  enabledModCount,
  isModEnabled,
  resolveModSettings,
  type ArmorSlot,
  type GameplayModId,
  type HUDAnchor,
  type HUDModId,
  type Keybind,
  type KeysPayload,
  type Loadout,
  type LoadoutId,
  type ModId,
  type PotionEffect,
  type Position,
  type ServerPayload,
  type TickPayload,
} from '@/bridge/protocol';
import { getVoid } from '@/bridge/connect';
import { type ClickRing, cps, createClickRing, pushClick, risingEdges } from './cps';
import { clampOffset, clampScale } from './hud-geometry';

/** A scalar a mod setting may hold. */
export type SettingValue = boolean | number | string | null;

/** Which overlay screen the menu layer is showing. */
export type Route =
  | { name: 'mods' }
  | { name: 'mod-settings'; mod: ModId }
  | { name: 'loadouts' }
  | { name: 'party' }
  | { name: 'hud-editor' };

const EMPTY_KEYS: KeysPayload = { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0 };

/** Click rings live outside the store: they are scratch, never rendered. */
const rings: { left: ClickRing; right: ClickRing } = {
  left: createClickRing(),
  right: createClickRing(),
};

/**
 * FPS samples for the `· 1% low 96` reading on the HUD-layout frame. The bridge
 * carries no such field, so it is derived here the same way CPS is — 30 s of the
 * 20 Hz tick, recomputed once a second.
 */
const FPS_WINDOW = 600;
const fpsSamples: number[] = [];

function onePercentLow(): number {
  if (fpsSamples.length < 20) return 0;
  const sorted = [...fpsSamples].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length * 0.01)] ?? 0;
}

/** Reset the derived rings. Tests call this between cases. */
export function resetDerivedState(): void {
  rings.left = createClickRing();
  rings.right = createClickRing();
  fpsSamples.length = 0;
}

export interface VoidState {
  /* ------------------------------------------------------------ live data */
  loadout: Loadout | null;
  /**
   * The loadout library the Loadouts frame lists.
   *
   * SCHEMA GAP: `bridge.json` has no accessor for it — Rust sends
   * `init.loadouts` to Java, but Java never forwards the list to JS. Until the
   * bridge grows one, this is filled from the fake bridge's `getLoadouts()` in a
   * browser and grows from the `loadout` events seen in game.
   */
  library: Loadout[];
  keys: KeysPayload;
  fps: number;
  /** 1st-percentile FPS over the last ~30 s. 0 until enough samples exist. */
  fpsLow: number;
  ping: number;
  pos: Position | null;
  armor: ArmorSlot[];
  fx: PotionEffect[];
  server: ServerPayload;
  cpsLeft: number;
  cpsRight: number;

  /* -------------------------------------------------------------- UI state */
  menuOpen: boolean;
  route: Route;
  /** Tile highlighted in the Mods grid; drives the right-hand settings pane. */
  selectedMod: ModId;
  paletteOpen: boolean;
  modSearch: string;
  modFilter: string;

  /* ------------------------------------------------------- HUD editor state */
  editorTarget: HUDModId | null;
  editorSnap: boolean;
  editorGrid: boolean;

  /* ------------------------------------------------------- bridge ingestion */
  applyLoadout(loadout: Loadout): void;
  applyKeys(keys: KeysPayload): void;
  applyTick(tick: TickPayload): void;
  applyServer(server: ServerPayload): void;
  applyMenu(open: boolean): void;

  /* -------------------------------------------------------------- UI actions */
  setRoute(route: Route): void;
  selectMod(id: ModId): void;
  setPaletteOpen(open: boolean): void;
  setModSearch(value: string): void;
  setModFilter(value: string): void;
  setEditorTarget(id: HUDModId | null): void;
  setEditorSnap(on: boolean): void;
  setEditorGrid(on: boolean): void;

  /* ------------------------------------------------------------ bridge calls */
  toggleMod(id: ModId, on: boolean): void;
  setSetting(id: ModId, key: string, value: SettingValue): void;
  commitHud(id: HUDModId, anchor: HUDAnchor, dx: number, dy: number, scale: number): void;
  switchLoadout(id: LoadoutId): void;
  closeMenu(): void;
  captureKeybind(id: ModId): Promise<Keybind | null>;
  resetMod(id: ModId): void;
}

export const useVoidStore = create<VoidState>((set, get) => ({
  loadout: null,
  library: [],
  keys: EMPTY_KEYS,
  fps: 0,
  fpsLow: 0,
  ping: -1,
  pos: null,
  armor: [],
  fx: [],
  server: { host: '', connected: false },
  cpsLeft: 0,
  cpsRight: 0,

  menuOpen: false,
  route: { name: 'mods' },
  selectedMod: 'keystrokes',
  paletteOpen: false,
  modSearch: '',
  modFilter: 'all',

  editorTarget: 'keystrokes',
  editorSnap: true,
  editorGrid: false,

  applyLoadout(loadout) {
    const library = get().library;
    const known = library.some((l) => l.id === loadout.id);
    set({
      loadout,
      library: known
        ? library.map((l) => (l.id === loadout.id ? loadout : l))
        : [...library, loadout],
    });
  },

  applyKeys(next) {
    const edges = risingEdges(get().keys, next);
    const now = Date.now();
    const patch: Partial<VoidState> = { keys: next };
    if (edges.lmb) {
      pushClick(rings.left, now);
      patch.cpsLeft = cps(rings.left, now, windowMs(get().loadout));
    }
    if (edges.rmb) {
      pushClick(rings.right, now);
      patch.cpsRight = cps(rings.right, now, windowMs(get().loadout));
    }
    set(patch as VoidState);
  },

  applyTick(tick) {
    const patch: Partial<VoidState> = {};
    if (tick.fps !== undefined) {
      patch.fps = tick.fps;
      fpsSamples.push(tick.fps);
      if (fpsSamples.length > FPS_WINDOW) fpsSamples.splice(0, fpsSamples.length - FPS_WINDOW);
      if (fpsSamples.length % 20 === 0) {
        const low = onePercentLow();
        if (low !== get().fpsLow) patch.fpsLow = low;
      }
    }
    if (tick.ping !== undefined) patch.ping = tick.ping;
    if (tick.pos !== undefined) patch.pos = tick.pos;
    if (tick.armor !== undefined) patch.armor = tick.armor as ArmorSlot[];
    if (tick.fx !== undefined) patch.fx = tick.fx as PotionEffect[];

    // The 20 Hz tick doubles as the CPS clock: without it a counter would sit
    // on its last value until the next click. Only write when it changed.
    const now = Date.now();
    const w = windowMs(get().loadout);
    const left = cps(rings.left, now, w);
    const right = cps(rings.right, now, w);
    if (left !== get().cpsLeft) patch.cpsLeft = left;
    if (right !== get().cpsRight) patch.cpsRight = right;

    if (Object.keys(patch).length > 0) set(patch as VoidState);
  },

  applyServer(server) {
    set({ server });
  },

  applyMenu(open) {
    // Opening always lands on Mods; the editor is left only through Done or Esc.
    set(
      open
        ? { menuOpen: true, route: { name: 'mods' }, paletteOpen: false }
        : { menuOpen: false, paletteOpen: false },
    );
  },

  setRoute(route) {
    set({ route });
  },
  selectMod(id) {
    set({ selectedMod: id });
  },
  setPaletteOpen(paletteOpen) {
    set({ paletteOpen });
  },
  setModSearch(modSearch) {
    set({ modSearch });
  },
  setModFilter(modFilter) {
    set({ modFilter });
  },
  setEditorTarget(editorTarget) {
    set({ editorTarget });
  },
  setEditorSnap(editorSnap) {
    set({ editorSnap });
  },
  setEditorGrid(editorGrid) {
    set({ editorGrid });
  },

  toggleMod(id, on) {
    const bridge = getVoid();
    // §6.5: gameplay mods go through setGameplay, which writes the actuator
    // field the Mixin reads every frame. HUD mods have no actuator, so their
    // `on` is an ordinary setting.
    const applied =
      MOD_REGISTRY[id].kind === 'gameplay'
        ? bridge.setGameplay(id as GameplayModId, on)
        : bridge.setModSetting(id, 'on', on);
    writeSetting(set, get, id, 'on', applied);
  },

  setSetting(id, key, value) {
    // Synchronous and authoritative: bind to what Java stored, not what we sent.
    const applied = getVoid().setModSetting(id, key, value);
    writeSetting(set, get, id, key, applied);
  },

  commitHud(id, anchor, dx, dy, scale) {
    const stored = getVoid().setHud(id, {
      anchor,
      dx: clampOffset(Math.round(dx)),
      dy: clampOffset(Math.round(dy)),
      scale: clampScale(scale),
    });
    const loadout = get().loadout;
    if (!loadout) return;
    const hud = loadout.hud.some((h) => h.id === id)
      ? loadout.hud.map((h) => (h.id === id ? { ...h, ...stored } : h))
      : [...loadout.hud, stored];
    set({ loadout: { ...loadout, hud } });
  },

  switchLoadout(id) {
    getVoid().switchLoadout(id);
  },

  closeMenu() {
    getVoid().closeMenu();
  },

  async captureKeybind(id) {
    // bridge.json: the capture call does not store the key — the UI does.
    return getVoid().openKeybindCapture(id);
  },

  resetMod(id) {
    const defaults = MOD_REGISTRY[id].defaults as unknown as Record<string, SettingValue>;
    for (const [key, value] of Object.entries(defaults)) {
      if (key === 'on') continue; // Reset restores settings, not enablement.
      get().setSetting(id, key, value);
    }
  },
}));

/* -------------------------------------------------------------------------- */
/* Derived readers                                                            */
/* -------------------------------------------------------------------------- */

function windowMs(loadout: Loadout | null): number {
  const value = loadout?.mods?.cps?.window_ms;
  return typeof value === 'number' ? value : 1000;
}

function writeSetting(
  set: (partial: Partial<VoidState>) => void,
  get: () => VoidState,
  id: ModId,
  key: string,
  value: SettingValue,
): void {
  const loadout = get().loadout;
  if (!loadout) return;
  set({
    loadout: {
      ...loadout,
      mods: { ...loadout.mods, [id]: { ...(loadout.mods[id] ?? {}), [key]: value } },
    },
  });
}

/**
 * Effective settings of one mod: registry defaults overlaid with the loadout's
 * own state. Thin wrapper over `resolveModSettings` that tolerates a null
 * loadout, which is the state before the first `loadout` push arrives.
 */
export function modSettings(loadout: Loadout | null, id: ModId): Record<string, SettingValue> {
  const source = loadout ?? { mods: {} };
  return resolveModSettings(source, id) as unknown as Record<string, SettingValue>;
}

/** Whether a mod is enabled in the active loadout. */
export function isModOn(loadout: Loadout | null, id: ModId): boolean {
  return isModEnabled(loadout ?? { mods: {} }, id);
}

/** The `hud[]` entry for a mod, or null when the loadout does not place it. */
export function hudItem(loadout: Loadout | null, id: HUDModId) {
  return loadout?.hud.find((h) => h.id === id) ?? null;
}

/** Number of enabled mods — the "24 mods on" line on a loadout card. */
export function modsOnCount(loadout: Loadout | null): number {
  return loadout ? enabledModCount(loadout) : 0;
}
