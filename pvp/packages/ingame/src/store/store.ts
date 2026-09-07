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

import { useMemo } from 'react';
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
  type SettingPayload,
  type TickPayload,
} from '@/bridge/protocol';
import { getVoid } from '@/bridge/connect';
// DEV ONLY. `isFakeMod` is a constant `false` in every build that did not ask for the padding
// (`src/dev/fake-mods.ts`); the two guards below are the only seam it needs in the store.
import { isFakeMod } from '@/dev/fake-mods';
import { type ClickRing, cps, createClickRing, pushClick, risingEdges, trimRing } from './cps';
import { clampOffset, clampScale } from './hud-geometry';

/** A scalar a mod setting may hold. */
export type SettingValue = boolean | number | string | null;

/** Which overlay screen the menu layer is showing. */
export type Route =
  | { name: 'mods' }
  | { name: 'loadouts' }
  | { name: 'party' }
  | { name: 'hud-editor' };

/**
 * How the Mods screen arranges its items. One of the **two independent controls**
 * of the quiet-cell contract §7 — see {@link Inspector}.
 */
export type ModsLayout = 'grid' | 'list';

/**
 * Whether the properties panel shows beside the items. The other of the two
 * independent controls (contract §7).
 *
 * These are deliberately two booleans and not one three-valued `mode`. A mode
 * switcher forces "list" and "properties" to be alternatives, so a player who
 * wants the description column has to give up the panel and then find their way
 * back — four states, reachable in any order, is the whole point. Selecting a mod
 * opens the inspector; nothing ever navigates to a separate screen, because the
 * overlay interrupts a live match and every step back costs time.
 */
export type Inspector = 'open' | 'closed';

const EMPTY_KEYS: KeysPayload = { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0 };


/** Click rings live outside the store: they are scratch, never rendered. */
const rings: { left: ClickRing; right: ClickRing } = {
  left: createClickRing(),
  right: createClickRing(),
};

/**
 * FPS samples for the `· 1% low 96` reading on the HUD-layout frame. The bridge
 * carries no such field, so it is derived here the same way CPS is — 30 s of samples,
 * recomputed about once a second.
 *
 * Sized in samples, not ticks. `TickCoalescer` rate-limits `fps` to 4 Hz because republishing it
 * 20 times a second cost ~50 ms of repaint each time, so 30 s is 120 samples rather than 600.
 */
const FPS_WINDOW = 120;
const fpsSamples: number[] = [];

/**
 * Value equality for the object-shaped `tick` fields.
 *
 * The store's contract at the top of this file is that a tick only publishes what actually
 * changed — that is why `fps` and `ping` are flat primitives rather than a nested object. The
 * three fields that *are* objects had no such guard: the bridge deserialises a new one every
 * tick, so `patch.pos = tick.pos` was always a new identity and always a re-render, standing
 * perfectly still. In game that cost ~50 ms of full-panel repaint per tick.
 *
 * Shallow and hand-written on purpose: these shapes come from `bridge.json` and are tiny and
 * fixed, so a generic deep-equal would cost more than the comparison saves and would hide a
 * schema change behind a recursive walk rather than failing to compile.
 */
function samePosition(a: Position | null, b: Position | null | undefined): boolean {
  if (a == null || b == null) return a == null && b == null;
  return a.x === b.x && a.y === b.y && a.z === b.z && a.yaw === b.yaw;
}

function sameArmor(a: ArmorSlot[], b: ArmorSlot[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((slot, i) => {
    const other = b[i]!;
    return (
      slot.slot === other.slot &&
      slot.item === other.item &&
      slot.damage === other.damage &&
      slot.max_damage === other.max_damage &&
      slot.count === other.count &&
      slot.enchanted === other.enchanted
    );
  });
}

function sameEffects(a: PotionEffect[], b: PotionEffect[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((fx, i) => {
    const other = b[i]!;
    return (
      fx.id === other.id &&
      fx.amplifier === other.amplifier &&
      // Duration counts down every tick, so this is the one field that legitimately changes
      // constantly. The widget renders it to the second, so compare at that resolution or the
      // guard never fires and the potion list re-renders 20 times a second for nothing.
      Math.round(fx.duration_ms / 1000) === Math.round(other.duration_ms / 1000) &&
      fx.ambient === other.ambient
    );
  });
}

/**
 * Deep equality for the plain JSON the bridge delivers.
 *
 * Narrow on purpose: everything it is used on came out of `JSON.parse`, so there are no cycles,
 * class instances, dates or functions to worry about, and the objects are a few kilobytes at most.
 * A change that broke those assumptions would be a change to `bridge.json`.
 */
function sameJson(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (a === null || b === null || typeof a !== 'object' || typeof b !== 'object') return false;
  if (Array.isArray(a) !== Array.isArray(b)) return false;
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, i) => sameJson(item, b[i]));
  }
  const left = a as Record<string, unknown>;
  const right = b as Record<string, unknown>;
  const keys = Object.keys(left);
  if (keys.length !== Object.keys(right).length) return false;
  return keys.every((k) => Object.prototype.hasOwnProperty.call(right, k) && sameJson(left[k], right[k]));
}

/** Whether the page is the in-game overlay, as `setRenderer` stamped it on `<html>`. */
function isUltralight(): boolean {
  return (
    typeof document !== 'undefined' &&
    document.documentElement?.getAttribute('data-renderer') === 'ultralight'
  );
}

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
   * Delivered whole by the `loadouts` bridge event, which Java pushes on `init` and
   * again whenever the library changes — Rust sends full loadouts in `init.loadouts`
   * (protocol.json, `msg_init`), so this is the real library in game as well as in the
   * harness. `applyLoadout` still folds the active loadout in, so a switch keeps the
   * list current between pushes.
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
  /** Tile highlighted in the Mods grid; drives the properties panel. */
  selectedMod: ModId;
  /** `grid` or `list`. Independent of {@link VoidState.inspector}. */
  layout: ModsLayout;
  /** `open` or `closed`. Independent of {@link VoidState.layout}. */
  inspector: Inspector;
  paletteOpen: boolean;
  modSearch: string;
  modFilter: string;

  /* ------------------------------------------------------- HUD editor state */
  editorTarget: HUDModId | null;
  editorSnap: boolean;
  editorGrid: boolean;

  /* ------------------------------------------------------- bridge ingestion */
  applyLoadout(loadout: Loadout): void;
  applyLoadouts(library: Loadout[]): void;
  applySetting(change: SettingPayload): void;
  applyKeys(keys: KeysPayload): void;
  applyTick(tick: TickPayload): void;
  applyServer(server: ServerPayload): void;
  applyMenu(open: boolean): void;

  /* -------------------------------------------------------------- UI actions */
  setRoute(route: Route): void;
  selectMod(id: ModId): void;
  setLayout(layout: ModsLayout): void;
  setInspector(inspector: Inspector): void;
  toggleInspector(): void;
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
  layout: 'grid',
  inspector: 'open',
  paletteOpen: false,
  modSearch: '',
  modFilter: 'all',

  editorTarget: 'keystrokes',
  editorSnap: true,
  editorGrid: false,

  applyLoadout(loadout) {
    const current = get().loadout;
    // Java echoes the whole loadout after every change we make, and `writeSetting` has already
    // applied that change optimistically — so the echo is almost always the state we are already
    // in. Taking it anyway swapped in a freshly parsed object where every reference is new, which
    // re-rendered the settings pane, the grid and the HUD for no new information. Measured in
    // game, each of those redundant renders repainted 2.5-6.6 MP at ~50 ms.
    //
    // Value, not identity: the echo is never reference-equal, that is the whole problem.
    if (current && sameJson(current, loadout)) return;

    const library = get().library;
    const known = library.some((l) => l.id === loadout.id);
    set({
      loadout,
      library: known
        ? library.map((l) => (l.id === loadout.id ? loadout : l))
        : [...library, loadout],
    });
  },

  applyLoadouts(library) {
    // Whole-state replacement (bridge.json, `loadouts_payload`). The active loadout is
    // in the list, but the copy the `loadout` channel pushed is the live one, so it
    // wins where both describe the same id.
    const active = get().loadout;
    set({
      library: active ? library.map((l) => (l.id === active.id ? active : l)) : library,
    });
  },

  applySetting({ id, key, value }) {
    // One key changed outside our own call — an in-game hotkey, or a launcher echo.
    // Applied exactly as the return value of `setModSetting` is; no whole-loadout
    // replacement, so nothing else in the tree re-renders.
    writeSetting(set, get, id, key, value as SettingValue);
  },

  applyKeys(next) {
    const edges = risingEdges(get().keys, next);
    const now = Date.now();
    const patch: Partial<VoidState> = { keys: next };
    // The click rings are updated below whatever happens — CPS has to stay correct across a spell
    // in the menu — but the keystrokes widget itself is behind the panel and cannot be seen, and
    // repainting it drags the damage rectangle across the panel for ~37 ms a time. Same reasoning
    // and same condition as applyTick.
    const hidden =
      get().menuOpen && get().route.name !== 'hud-editor' && isUltralight();
    if (edges.lmb) {
      pushClick(rings.left, now);
      patch.cpsLeft = cps(rings.left, now, windowMs(get().loadout));
    }
    if (edges.rmb) {
      pushClick(rings.right, now);
      patch.cpsRight = cps(rings.right, now, windowMs(get().loadout));
    }
    if (hidden) {
      return;
    }
    set(patch as VoidState);
  },

  applyTick(tick) {
    const patch: Partial<VoidState> = {};
    const prev = get();
    if (tick.fps !== undefined) {
      fpsSamples.push(tick.fps);
      if (fpsSamples.length > FPS_WINDOW) fpsSamples.splice(0, fpsSamples.length - FPS_WINDOW);
    }

    // Hold the live readouts while the menu covers them.
    //
    // Ultralight reports damage as one bounding rectangle, not a region. The HUD chips sit at the
    // screen edges and the panel sits in the middle, so an fps chip ticking behind the menu drags
    // that rectangle across both — measured in game at ~37 ms a repaint, several times a second,
    // for a number the panel is covering. Sampling above still runs, so the 1% low stays honest;
    // only publishing waits, and it resumes within a tick of the menu closing.
    //
    // Ultralight only: the launcher and the `?debug` harness draw through a renderer with no such
    // damage model, the harness opens the menu by default, and freezing the HUD there would make
    // HUD work impossible. Same split as the token layer — the constraint belongs to the renderer,
    // so the condition names the renderer.
    if (prev.menuOpen && prev.route.name !== 'hud-editor' && isUltralight()) {
      return;
    }

    if (tick.fps !== undefined) {
      if (tick.fps !== prev.fps) patch.fps = tick.fps;
      if (fpsSamples.length % 4 === 0) {
        const low = onePercentLow();
        if (low !== prev.fpsLow) patch.fpsLow = low;
      }
    }

    if (tick.ping !== undefined && tick.ping !== prev.ping) patch.ping = tick.ping;
    // Compare by value, never by identity. The bridge builds a fresh object every tick, so
    // assigning it unconditionally published a change 20 times a second while standing still —
    // and every one of those repainted the whole menu panel at ~50 ms. Measured: three
    // consecutive stalls of 56, 48 and 56 ms on byte-identical payloads.
    if (tick.pos !== undefined && !samePosition(prev.pos, tick.pos)) patch.pos = tick.pos;
    if (tick.armor !== undefined && !sameArmor(prev.armor, tick.armor as ArmorSlot[])) {
      patch.armor = tick.armor as ArmorSlot[];
    }
    if (tick.fx !== undefined && !sameEffects(prev.fx, tick.fx as PotionEffect[])) {
      patch.fx = tick.fx as PotionEffect[];
    }

    // The 20 Hz tick doubles as the CPS clock: without it a counter would sit
    // on its last value until the next click. Only write when it changed.
    const now = Date.now();
    const w = windowMs(get().loadout);
    const left = cps(trimRing(rings.left, now, w), now, w);
    const right = cps(trimRing(rings.right, now, w), now, w);
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
    // Contract §7: selecting a mod opens the inspector. It is the only thing that
    // opens it implicitly — the toggle button is how it is closed and reopened by
    // hand, and the two stay independent of `layout` either way.
    set({ selectedMod: id, inspector: 'open' });
  },
  setLayout(layout) {
    set({ layout });
  },
  setInspector(inspector) {
    set({ inspector });
  },
  toggleInspector() {
    set({ inspector: get().inspector === 'open' ? 'closed' : 'open' });
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
    // A synthetic mod is not a mod Java knows: `LiveState.setModSetting` returns null for an id
    // outside `ModRegistry`, and this store binds to what Java returns, so the switch would
    // flip back under the cursor. Written locally instead, which is all a fixture needs.
    if (isFakeMod(id)) {
      writeSetting(set, get, id, 'on', on);
      return;
    }
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
    // Same reason as `toggleMod`, and this is the one that carries `resetMod` too.
    if (isFakeMod(id)) {
      writeSetting(set, get, id, key, value);
      return;
    }
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
  const { loadout, library } = get();
  if (!loadout) return;
  const next: Loadout = {
    ...loadout,
    mods: { ...loadout.mods, [id]: { ...(loadout.mods[id] ?? {}), [key]: value } },
  };
  // The library holds a copy of the active loadout; leaving it behind is how the
  // Loadouts frame ends up showing a stale "24 mods on" for the loadout you are on.
  set({
    loadout: next,
    library: library.map((l) => (l.id === next.id ? next : l)),
  });
}

/**
 * Effective settings of one mod: registry defaults overlaid with the loadout's
 * own state. Thin wrapper over `resolveModSettings` that tolerates a null
 * loadout, which is the state before the first `loadout` push arrives.
 */
export function modSettings(
  loadout: Pick<Loadout, 'mods'> | null,
  id: ModId,
): Record<string, SettingValue> {
  const source = loadout ?? { mods: {} };
  return resolveModSettings(source, id) as unknown as Record<string, SettingValue>;
}

/**
 * React hook form of {@link modSettings}.
 *
 * `modSettings` builds a fresh object, and zustand v5 compares snapshots with
 * `Object.is` — selecting it directly would hand React a new value on every
 * render and spin. Select the loadout (a stable reference) and merge in a memo.
 */
export function useModSettings(id: ModId): Record<string, SettingValue> {
  // Subscribe to this mod's own entry, not the whole loadout. `resolveModSettings` reads exactly
  // `loadout.mods[id]` and the registry defaults, and `writeSetting` rebuilds only the entry it
  // touches — so every other mod's entry stays reference-equal and this memo holds across an
  // unrelated toggle. Selecting the loadout meant one toggle recomputed every mod's settings and
  // re-rendered the whole settings pane, which in game repainted ~2.5 MP at ~50 ms.
  const own = useVoidStore((s) => s.loadout?.mods?.[id]);
  return useMemo(
    () => modSettings(own === undefined ? null : { mods: { [id]: own } }, id),
    [own, id],
  );
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
