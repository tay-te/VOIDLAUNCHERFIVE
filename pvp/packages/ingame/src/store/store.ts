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
  HUD_MOD_IDS,
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
  type SessionInfo,
  type SettingPayload,
  type TickPayload,
} from '@/bridge/protocol';
import { getVoid } from '@/bridge/connect';
// DEV ONLY. `isFakeMod` is a constant `false` in every build that did not ask for the padding
// (`src/dev/fake-mods.ts`); the two guards below are the only seam it needs in the store.
import { isFakeMod } from '@/dev/fake-mods';
import {
  type ClickRing,
  clicksPerSecond,
  createClickRing,
  pushClick,
  risingEdges,
  trimRing,
} from './cps';
import { DEFAULT_HUD, GRID, clampOffset, clampScale } from './hud-geometry';

/** A scalar a mod setting may hold. */
export type SettingValue = boolean | number | string | null;

/**
 * Which overlay screen the menu layer is showing.
 *
 * `mod` is a **page**, not a panel: one mod, the whole width of the shell. It carries the id
 * rather than reading `selectedMod`, so "which mod am I looking at" is answered by the route
 * — the thing that decides what is drawn — and cannot drift from it.
 */
export type Route =
  | { name: 'mods' }
  | { name: 'mod'; id: ModId }
  | { name: 'settings' }
  | { name: 'loadouts' }
  | { name: 'party' }
  | { name: 'hud-editor' };

/**
 * How the Mods screen arranges its items.
 *
 * The **one** control on the mods shell now. It used to be one of two — the other being an
 * `inspector: open | closed` that decided whether the properties showed beside the grid — and
 * that pairing is gone with the properties panel itself: the toggle is on the card, so the grid
 * is the working surface and most interactions never need the properties at all. Contracting
 * the grid to three columns to keep a panel visible optimised the rare case at the cost of the
 * common one. The grid is now always full-panel and selecting a mod opens {@link Route} `mod`.
 */
export type ModsLayout = 'grid' | 'list';

const EMPTY_KEYS: KeysPayload = { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0 };

/**
 * Who is playing, as the mod reads it off Minecraft's own `Session`.
 *
 * **Not the launcher's account.** The game knows who is signed in whether or not a launcher, a
 * socket or a bridge exists, so it is right in a dev client — which is exactly the client
 * somebody is looking at when it is wrong. Immutable for the life of the process: it arrives
 * once, on `VoidBridge.pushWholeState()`, and nothing ever pushes it again.
 *
 * **It has graduated.** This was declared here, in a comment saying it belonged in
 * `@void/protocol` and had not got there yet; `schema/bridge.json` now carries the `session`
 * channel and its payload, and this is a re-export of the generated type so the page and the
 * contract cannot drift. Kept as a named re-export rather than deleted because everything in
 * the overlay imports it from the store.
 */
export type { SessionInfo };

/**
 * The client's global settings — the things that are true of the client rather than of a mod.
 *
 * `bridge.json`'s `settings` channel, camel-cased at the boundary the way the rest of this file
 * treats its payloads. Java owns them (`LiveState`, from `GlobalSettings`), Rust persists them,
 * and until now the page could neither read nor write any of it: `menuKey` and `uiScale` were
 * live values on the other side of a wall. The Settings page is what needed them.
 *
 * **Only the ones the page has a use for.** `GlobalSettings` also carries `cycleLoadoutKey`; it
 * is not lifted here because nothing in the overlay reads it, and a field mirrored into the store
 * for completeness is a field that goes stale unnoticed. `hudEditorGrid` used to be in that
 * sentence and has earned its way out — the HUD editor's `Snap` toggle is now a control for it.
 */
export interface GlobalsView {
  /** Key that opens and closes the menu, as a `KeyNames` name — `RSHIFT` by default. */
  menuKey: string;
  /**
   * Extra multiplier on the whole in-game UI, on top of the fit scale. 0.5 .. 3.
   *
   * **Read here, written only by the launcher.** Nothing in game writes it any more — the
   * Settings page carried a meter for it for about an hour and it was removed on purpose, and
   * `SettingsScreen.tsx` has the measurement: a control that resizes the surface it lives on is
   * a feedback loop in this engine, because the relayout moves the control under a stationary
   * pointer and `MouseEvent.buttons` is 0 for an entire drag (rendering-invariants §13). It ran
   * away to the 3.0 clamp in fourteen seconds.
   *
   * It is emphatically **not dead**: `setGlobal('ui_scale', …)` still stores it in `LiveState`,
   * and `VoidClient.pumpUi` multiplies it into the view scale on every frame. The value here is
   * the current one so the page can *show* it if it ever needs to; the writer is the launcher,
   * over the WS bridge.
   */
  uiScale: number;
  /** Stored and, as of today, read by nothing. Kept so a push round-trips unchanged. */
  theme: string;
  /**
   * The HUD editor's snap grid, in unscaled GUI pixels. 0 disables snapping.
   *
   * **This is the `Snap` toggle**, and lifting it here is what made that toggle real.
   * `LiveState.setHud` re-snaps every drop against this value on the Java side, so with the
   * global left at its factory 4 the page's `Snap: off` still produced 4 px quantisation — the
   * control looked like it did something and did not. The toggle now writes the global (`GRID`
   * on, 0 off) and reads back from it, so one number decides the snap and both sides use it.
   */
  hudEditorGrid: number;
}

/** What the page believes before the first `settings` push. Matches `GlobalSettings::factory()`. */
export const FACTORY_GLOBALS: GlobalsView = {
  menuKey: 'RSHIFT',
  uiScale: 1,
  theme: 'void-dark',
  hudEditorGrid: 4,
};

/** Which global settings the page may write. The rest of `GlobalSettings` is Rust's business. */
export type GlobalKey = 'menu_key' | 'ui_scale' | 'hud_editor_grid';


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
 * The last `hits` pair seen, or null before the first one.
 *
 * Module-level like `fpsSamples`, and reset by `resetDerivedState()` for the same reason: it is
 * derivation scratch, not state anybody renders, and a test that left it set would carry a
 * baseline into the next test and count the wrong number of hits.
 */
let lastHits: { dealt: number; taken: number } | null = null;

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
  lastHits = null;
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

  /* ------------------------------------------------------- the Wave 2 readings */
  /* Each is `null` until the sensor sends one, and null means "no reading" rather than
     zero — a saturation of 0 and an unread saturation are different states, and only one
     of them should draw a number. See `bridge.json`'s `tick_payload`. */

  /** Food saturation, 0-20. */
  saturation: number | null;
  /** Stack size of the held item; null for an empty hand, which is not a count of zero. */
  heldCount: number | null;
  /** Horizontal ground speed, blocks/sec. */
  speed: number | null;
  /** JVM heap, mebibytes. */
  memory: { usedMb: number; maxMb: number } | null;
  /**
   * Consecutive hits landed without being hit back.
   *
   * Derived here from the monotonic counters the sensor sends, not sent by it: `bridge.json`'s
   * `hits` records why. What lives here is the *count*, which is a fact. The **timeout** —
   * `combo.reset_ms` — is policy and stays in the widget, so this number never silently expires
   * underneath a reader that has its own opinion about when it should.
   */
  combo: number;
  /** When the combo last advanced, ms. The widget compares this against its own `reset_ms`. */
  comboAt: number;
  server: ServerPayload;
  cpsLeft: number;
  cpsRight: number;
  /**
   * The highest rate either hand has reached this session — the mod's `show_peak` aside.
   *
   * Session, not window: `window_ms` is already the averaging window, so a peak measured over
   * the same window would be the live figure with a lag. What a player wants beside the live
   * number is the best they have managed, which only ever goes up and holds still — so it is
   * kept here, next to the rings the live figures come out of, rather than derived per render
   * from a history nothing keeps.
   *
   * Store state rather than a module-level scratch value like the rings themselves, because it
   * is *rendered*: a peak nothing subscribed to would not repaint the chip when it moved.
   */
  cpsPeak: number;
  /** Who is playing. Null until the first `session` push, and after that never changes. */
  session: SessionInfo | null;
  /** The client's globals. Factory values until the first `settings` push replaces them. */
  globals: GlobalsView;


  /* -------------------------------------------------------------- UI state */
  menuOpen: boolean;
  route: Route;
  /**
   * Tile highlighted in the Mods grid.
   *
   * Selection is **navigation state, not a route**: it says which tile the arrow keys are on
   * and which one is marked when you come back from a mod's page. Opening a mod sets it too,
   * so returning from the page lands on the tile you left from.
   */
  selectedMod: ModId;
  /** `grid` or `list`. */
  layout: ModsLayout;
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
  applySession(session: SessionInfo): void;
  applyGlobals(payload: unknown): void;
  applyMenu(open: boolean): void;

  /* -------------------------------------------------------------- UI actions */
  setRoute(route: Route): void;
  selectMod(id: ModId): void;
  openMod(id: ModId): void;
  closeMod(): void;
  setLayout(layout: ModsLayout): void;
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
  resetHud(): void;
  switchLoadout(id: LoadoutId): void;
  setGlobal(key: GlobalKey, value: string | number): void;
  closeMenu(): void;
  captureKeybind(id: ModId): Promise<Keybind | null>;
  captureMenuKey(): Promise<string | null>;
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
  saturation: null,
  heldCount: null,
  speed: null,
  memory: null,
  combo: 0,
  comboAt: 0,
  server: { host: '', connected: false },
  cpsLeft: 0,
  cpsRight: 0,
  cpsPeak: 0,
  session: null,
  globals: FACTORY_GLOBALS,

  menuOpen: false,
  route: { name: 'mods' },
  selectedMod: 'keystrokes',
  layout: 'grid',
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
    // and same condition as applyTick, with one more screen exempted.
    //
    // **The keystrokes mod page draws the real widget**, at the centre of the panel, as the
    // whole subject of the page (`ModSettingsScreen`, `LIVE_WIDGETS`). Holding `keys` there
    // made that preview a dead pad: it showed whichever keys happened to be down when the menu
    // opened — none — and no key the player pressed while looking straight at it ever lit. The
    // suppression is about widgets *behind* the panel; this one is in front of it.
    //
    // It does not cost the §4 budget either. That invariant is "an **idle** open menu paints
    // nothing", and a key going down is not idle: the repaint is caused by an input, which is
    // the one thing a repaint is always allowed to be caused by. Nothing here runs on a timer.
    const route = get().route;
    const previewingKeys = route.name === 'mod' && route.id === 'keystrokes';
    const hidden =
      get().menuOpen && route.name !== 'hud-editor' && !previewingKeys && isUltralight();
    if (edges.lmb) {
      pushClick(rings.left, now);
      patch.cpsLeft = clicksPerSecond(rings.left, now, windowMs(get().loadout));
    }
    if (edges.rmb) {
      pushClick(rings.right, now);
      patch.cpsRight = clicksPerSecond(rings.right, now, windowMs(get().loadout));
    }
    // Both hands against one peak: `mode` decides which figures are *drawn*, and a peak that
    // forgot the other hand would drop the moment a player switched to it.
    const peaked = Math.max(patch.cpsLeft ?? 0, patch.cpsRight ?? 0);
    if (peaked > get().cpsPeak) patch.cpsPeak = peaked;
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
    const left = clicksPerSecond(trimRing(rings.left, now, w), now, w);
    const right = clicksPerSecond(trimRing(rings.right, now, w), now, w);
    if (left !== get().cpsLeft) patch.cpsLeft = left;
    if (right !== get().cpsRight) patch.cpsRight = right;
    // Only ever upwards here. This branch is the clock rather than a click — it exists to let
    // the live figures *decay* — so taking a max against it is what keeps the peak a peak.
    const highest = Math.max(left, right);
    if (highest > get().cpsPeak) patch.cpsPeak = highest;

    // ---------------------------------------------------------- the Wave 2 readings
    //
    // Same discipline as everything above: compare by value and patch only on a real change.
    // The sensor already coalesces, so most ticks carry none of these — but `undefined` means
    // "unchanged" and must not be written, or a coalesced-away field would blank the chip.
    if (tick.saturation !== undefined && tick.saturation !== prev.saturation) {
      patch.saturation = tick.saturation;
    }
    // An absent `held_count` is an empty hand, which IS news — the chip has to stop showing the
    // last stack. That makes it the one field here where `undefined` is a value rather than a
    // non-answer, and it is why the sensor is careful to omit it rather than send 0.
    const held = tick.held_count ?? null;
    if (held !== prev.heldCount) patch.heldCount = held;

    if (tick.speed !== undefined && tick.speed !== prev.speed) patch.speed = tick.speed;
    if (
      tick.memory !== undefined &&
      (prev.memory === null ||
        tick.memory.used_mb !== prev.memory.usedMb ||
        tick.memory.max_mb !== prev.memory.maxMb)
    ) {
      patch.memory = { usedMb: tick.memory.used_mb, maxMb: tick.memory.max_mb };
    }

    // The combo, derived from the two monotonic counters.
    //
    // First sight of the pair establishes a baseline and counts nothing: the counters are
    // session-monotonic, so a page reload mid-match would otherwise report every hit of the
    // match at once. After that a rise in `taken` breaks the combo and a rise in `dealt`
    // advances it by however much it moved — by the delta, not by one, so a tick that carried
    // two hits is still exactly right. That robustness is the reason the wire sends counters
    // rather than events (`bridge.json`, `hits`).
    if (tick.hits !== undefined) {
      const seen = lastHits;
      lastHits = { dealt: tick.hits.dealt, taken: tick.hits.taken };
      if (seen !== null) {
        let combo = prev.combo;
        if (tick.hits.taken > seen.taken) combo = 0;
        const landed = tick.hits.dealt - seen.dealt;
        if (landed > 0) {
          combo += landed;
          patch.comboAt = Date.now();
        }
        if (combo !== prev.combo) patch.combo = combo;
      }
    }

    if (Object.keys(patch).length > 0) set(patch as VoidState);
  },

  applyServer(server) {
    set({ server });
  },

  applySession(session) {
    // Value, not identity: a reloaded document is re-sent the same session (§9a), and taking a
    // fresh object for identical data would re-render the bar for nothing.
    const current = get().session;
    if (current && sameJson(current, session)) return;
    set({ session });
  },

  applyGlobals(payload) {
    // Snake_case on the wire (`protocol.json`, `global_settings`), camel in the store. Only the
    // three keys the page uses are lifted; an unknown or absent key leaves the current value
    // alone, so a host that pushes a partial object cannot blank a setting.
    if (payload === null || typeof payload !== 'object') return;
    const raw = payload as Record<string, unknown>;
    const current = get().globals;
    const next: GlobalsView = {
      menuKey: typeof raw.menu_key === 'string' ? raw.menu_key : current.menuKey,
      uiScale: typeof raw.ui_scale === 'number' ? raw.ui_scale : current.uiScale,
      theme: typeof raw.theme === 'string' ? raw.theme : current.theme,
      hudEditorGrid:
        typeof raw.hud_editor_grid === 'number' ? raw.hud_editor_grid : current.hudEditorGrid,
    };
    // Value, not identity — same reason as `applySession`. A reloaded document is sent the same
    // settings again (rendering-invariants §9a), and taking a fresh object for identical data
    // would re-render the Settings page for nothing.
    if (sameJson(current, next)) return;
    // The `Snap` toggle is a view of `hud_editor_grid`, not a second opinion about it: taking it
    // from the push is what stops the editor opening with `Snap` lit while Java is not snapping.
    set({ globals: next, editorSnap: next.hudEditorGrid > 0 });
  },

  applyMenu(open) {
    // Opening always lands on the grid — never on the mod page you happened to be inside when
    // you last closed. Reopening is a fresh look at the loadout, and a page you did not ask for
    // is a step to undo before you can do anything. It is also the only reset a mod page needs:
    // R-Shift and Escape both close the whole menu, so "close from a page" is covered here.
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
    // Selection **only**. It used to open the properties panel as a side effect, which is what
    // made the arrow keys unusable as navigation: every step opened something. Moving the
    // highlight and going somewhere are two different intentions and are now two calls — the
    // arrows select, the tile body and the palette {@link VoidState.openMod}.
    set({ selectedMod: id });
  },
  openMod(id) {
    // Both, and in one write: the page is what you are looking at, and the grid you come back
    // to has that tile marked. Two `set`s would render the grid once with a moved selection
    // before replacing it, which in game is a repaint of twelve tiles nobody sees.
    set({ selectedMod: id, route: { name: 'mod', id } });
  },
  closeMod() {
    // Back to the grid, unchanged. `selectedMod` is deliberately left alone: it is what marks
    // the tile you were just inside.
    set({ route: { name: 'mods' } });
  },
  setLayout(layout) {
    set({ layout });
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
    // Optimistic, then authoritative — `setGlobal` binds the store to what Java stored. Writing
    // the global is the whole point: Java re-snaps every drop against it (`LiveState.setHud`), so
    // a toggle that only changed the page would be overruled on every commit.
    set({ editorSnap });
    get().setGlobal('hud_editor_grid', editorSnap ? GRID : 0);
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

  /**
   * Put every HUD widget back where the client started — the editor's `Reset layout`.
   *
   * **Every id, not every entry in `loadout.hud`.** A mod the loadout has forgotten is exactly
   * the one Reset has to be able to rescue, and reading the broken table to repair it could not
   * (rendering-invariants §15). `DEFAULT_HUD` is a total `Record<HUDModId, …>`, so this cannot
   * miss one.
   *
   * **The snap grid is stood down for the duration**, and that is the difference between Reset
   * being an undo and Reset being a move. `LiveState.setHud` normalises every drop against
   * `hud_editor_grid`, and the factory placements are not on that grid — `keystrokes` is
   * `31, -109`, which at the factory grid of 4 comes back as `32, -108` and at the editor's own
   * 8 as `32, -112`. Restoring a layout is not a placement gesture and must not be quantised
   * like one. Both calls are synchronous in-process (bridge.json), so there is no window in
   * which another write could see the grid at 0.
   */
  resetHud() {
    const bridge = getVoid();
    const grid = get().globals.hudEditorGrid;
    if (grid > 0) bridge.setGlobal('hud_editor_grid', 0);
    try {
      for (const id of HUD_MOD_IDS) {
        const home = DEFAULT_HUD[id];
        get().commitHud(id, home.anchor, home.dx, home.dy, 1);
      }
    } finally {
      if (grid > 0) bridge.setGlobal('hud_editor_grid', grid);
    }
  },

  switchLoadout(id) {
    getVoid().switchLoadout(id);
  },

  setGlobal(key, value) {
    // Synchronous and authoritative, exactly as `setSetting` is: Java clamps `ui_scale` to
    // 0.5..3 and validates `menu_key` against `KeyNames`, and the store binds to what came
    // back rather than to what was sent. `null` means Java refused it, and the page then
    // shows the value it still has instead of one that was never stored.
    const applied = getVoid().setGlobal(key, value);
    if (applied === null || applied === undefined) return;
    const current = get().globals;
    if (key === 'ui_scale' && typeof applied === 'number') {
      set({ globals: { ...current, uiScale: applied } });
    } else if (key === 'menu_key' && typeof applied === 'string') {
      set({ globals: { ...current, menuKey: applied } });
    } else if (key === 'hud_editor_grid' && typeof applied === 'number') {
      set({ globals: { ...current, hudEditorGrid: applied }, editorSnap: applied > 0 });
    }
  },

  closeMenu() {
    getVoid().closeMenu();
  },

  async captureKeybind(id) {
    // bridge.json: the capture call does not store the key — the UI does.
    return getVoid().openKeybindCapture(id);
  },

  async captureMenuKey() {
    // `openKeybindCapture` is typed over mod ids because until today every keybind belonged to a
    // mod. The argument is only a label for the arming, though: Java stores it in `captureModId`
    // and never reads it, and `finishKeybindCapture` answers with the key name alone. So a
    // non-mod capture works, and this is the one — the key that opens the menu, which is a
    // property of the client rather than of anything in the registry.
    const capture = getVoid().openKeybindCapture as unknown as (
      id: string,
    ) => Promise<string | null>;
    return capture('menu');
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
