/**
 * The one store. `window.void.on(...)` is the only writer of live game data
 * (§9); everything else here is UI state that never leaves the page.
 *
 * Rendering discipline (§9, and the "update only the DOM that changed" rule):
 *   · `keys` is kept as eight independent numbers, so a keycap subscribed to
 *     `s.keys.w` re-renders only when W changes;
 *   · `tick` fields are flattened to primitives for the same reason — the FPS
 *     chip does not re-render because the ping moved;
 *   · `armor` / `fx` arrays are replaced only on the ticks that carry them
 *     (bridge.json: an absent field means unchanged);
 *   · there is no animation frame loop anywhere in this bundle. The 20 Hz
 *     `tick` push is the clock.
 */

import { create } from 'zustand';
import {
  type ArmorSlot,
  type GameplayModId,
  type HUDAnchor,
  type HUDModId,
  type Keybind,
  type KeysPayload,
  type Loadout,
  type ModId,
  type ModSettingValue,
  type PotionEffect,
  type Position,
  type ServerPayload,
  type TickPayload,
} from '@/bridge/protocol';
import { getVoid } from '@/bridge/connect';
import { MOD_REGISTRY } from '@/registry';
import { type ClickRing, cps, createClickRing, pushClick, risingEdges } from './cps';
import { clampOffset, clampScale } from './hud-geometry';

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
 * has no such field, so it is derived here the same way CPS is — 30 s of the
 * 20 Hz tick, recomputed once a second.
 */
const FPS_WINDOW = 600;
const fpsSamples: number[] = [];

function onePercentLow(): number {
  if (fpsSamples.length < 20) return 0;
  const sorted = [...fpsSamples].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length * 0.01)] ?? 0;
}

export interface VoidState {
  /* ------------------------------------------------------------ live data */
  loadout: Loadout | null;
  /**
   * The loadout library the Loadouts frame lists.
   *
   * SCHEMA GAP: `bridge.json` has no accessor for it — Rust sends
   * `init.loadouts` to Java, but Java never forwards the list to JS. Until the
   * bridge grows one, this is filled from the fake bridge's `__loadouts()` in a
   * browser and falls back to `[active loadout]` in game. Raised in the report.
   */
  library: Loadout[];
  keys: KeysPayload;
  fps: number;
  ping: number;
  pos: Position | null;
  armor: ArmorSlot[];
  fx: PotionEffect[];
  server: ServerPayload;
  cpsLeft: number;
  cpsRight: number;
  /** 1st-percentile FPS over the last ~30 s. 0 until enough samples exist. */
  fpsLow: number;

  /* -------------------------------------------------------------- UI state */
  menuOpen: boolean;
  route: Route;
  /** Tile highlighted in the Mods grid; drives the right-hand settings pane. */
  selectedMod: ModId;
  paletteOpen: boolean;
  modSearch: string;
  modFilter: string;
  /** Set while `openKeybindCapture` is pending, so the chip can say so. */
  capturingKeybind: ModId | null;

  /* ------------------------------------------------------- HUD editor state */
  editorTarget: HUDModId | null;
  editorSnap: boolean;
  editorGrid: boolean;
  /** Live readout while dragging; null when the widget is at rest. */
  editorDraft: { id: HUDModId; x: number; y: number; scale: number } | null;

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
  toggleEditorSnap(): void;
  toggleEditorGrid(): void;
  setEditorDraft(draft: VoidState['editorDraft']): void;

  /* ------------------------------------------------------------ bridge calls */
  toggleMod(id: ModId, on: boolean): void;
  setSetting(id: ModId, key: string, value: ModSettingValue): void;
  commitHud(id: HUDModId, anchor: HUDAnchor, dx: number, dy: number, scale: number): void;
  switchLoadout(id: string): void;
  closeMenu(): void;
  captureKeybind(id: ModId, settingKey?: string): Promise<Keybind | null>;
  resetMod(id: ModId): void;
}

export const useVoidStore = create<VoidState>((set, get) => ({
  loadout: null,
  library: [],
  keys: EMPTY_KEYS,
  fps: 0,
  ping: -1,
  pos: null,
  armor: [],
  fx: [],
  server: { host: '', connected: false },
  cpsLeft: 0,
  cpsRight: 0,
  fpsLow: 0,

  menuOpen: false,
  route: { name: 'mods' },
  selectedMod: 'keystrokes',
  paletteOpen: false,
  modSearch: '',
  modFilter: 'all',
  capturingKeybind: null,

  editorTarget: 'keystrokes',
  editorSnap: true,
  editorGrid: false,
  editorDraft: null,

  applyLoadout(loadout) {
    const library = get().library;
    const known = library.some((l) => l.id === loadout.id);
    set({
      loadout,
      library: known ? library.map((l) => (l.id === loadout.id ? loadout : l)) : [...library, loadout],
    });
  },

  applyKeys(next) {
    const previous = get().keys;
    const edges = risingEdges(previous, next);
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
    if (tick.armor !== undefined) patch.armor = tick.armor;
    if (tick.fx !== undefined) patch.fx = tick.fx;

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
    // Opening always lands on Mods; the editor is left only through Done/Esc.
    set(open ? { menuOpen: true, route: { name: 'mods' }, paletteOpen: false } : { menuOpen: false });
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
  toggleEditorSnap() {
    set({ editorSnap: !get().editorSnap });
  },
  toggleEditorGrid() {
    set({ editorGrid: !get().editorGrid });
  },
  setEditorDraft(editorDraft) {
    set({ editorDraft });
  },

  toggleMod(id, on) {
    const bridge = getVoid();
    const entry = MOD_REGISTRY[id];
    // §6.5: gameplay mods go through setGameplay, which writes the actuator
    // field the Mixin reads every frame. HUD mods have no actuator, so their
    // `on` is an ordinary setting.
    const applied =
      entry.kind === 'gameplay'
        ? bridge.setGameplay(id as GameplayModId, on)
        : bridge.setModSetting(id, 'on', on);
    writeSetting(set, get, id, 'on', applied);
  },

  setSetting(id, key, value) {
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
    set({ loadout: { ...loadout, hud }, editorDraft: null });
  },

  switchLoadout(id) {
    getVoid().switchLoadout(id);
  },

  closeMenu() {
    getVoid().closeMenu();
  },

  async captureKeybind(id, settingKey = 'key') {
    set({ capturingKeybind: id });
    try {
      const key = await getVoid().openKeybindCapture(id);
      // bridge.json: the capture call does not store the key — the UI does.
      if (key !== null) get().setSetting(id, settingKey, key);
      return key;
    } finally {
      set({ capturingKeybind: null });
    }
  },

  resetMod(id) {
    const defaults = MOD_REGISTRY[id].defaults;
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
  value: ModSettingValue,
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
 * Effective settings of one mod: registry defaults overlaid with whatever the
 * loadout carries. `mod_states` says every key is optional and an omitted mod
 * falls back to its `defaults`, which is what keeps old loadouts valid.
 */
export function modSettings(
  loadout: Loadout | null,
  id: ModId,
): Record<string, ModSettingValue> {
  return { ...MOD_REGISTRY[id].defaults, ...(loadout?.mods?.[id] ?? {}) };
}

/** Whether a mod is enabled in the active loadout. */
export function isModOn(loadout: Loadout | null, id: ModId): boolean {
  return modSettings(loadout, id).on === true;
}

/** The `hud[]` entry for a mod, or null when the loadout does not place it. */
export function hudItem(loadout: Loadout | null, id: HUDModId) {
  return loadout?.hud.find((h) => h.id === id) ?? null;
}

/** Number of enabled mods — the "24 mods on" line on a loadout card. */
export function modsOnCount(loadout: Loadout | null): number {
  if (!loadout) return 0;
  return (Object.keys(MOD_REGISTRY) as ModId[]).filter((id) => isModOn(loadout, id)).length;
}
