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
  type InventoryEntry,
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
  | { name: 'review' }
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
 * One clicks-per-second figure per whole second, per hand — what the CPS graph plots.
 *
 * **Summarised, not retained.** The obvious shape is to keep the click ring long enough to plot
 * from, and it is the wrong one: sixty seconds of a fast hand is several hundred timestamps, and
 * the drawing has exactly one column per second. So the ring stays sized for counting (its own
 * module says why it is bounded at all) and this keeps the answer instead — sixty numbers, which
 * is the longest window the mod offers.
 *
 * Written on the second boundary rather than on a rolling window, so a column is a second of wall
 * clock and two adjacent columns never share a click. `lastSecond` is the boundary already
 * written; a tick that lands in the same second adds nothing, and a gap (the menu was open, the
 * game was paused) is filled with the seconds that actually elapsed rather than compressed —
 * otherwise a graph would draw a quiet spell as if it had not happened.
 *
 * Module-level like `fpsSamples`, and reset by `resetDerivedState()` for the same reason.
 */
const CLICK_HISTORY_MAX = 60;
const clickHistory: { l: number; r: number }[] = [];
let lastSecond = 0;

/**
 * Ping samples, for the jitter reading — `docs/mod-roster.md` §8's third gap, by name.
 *
 * Derived here for the same reason the 1% low and CPS are: the wire carries the *reading*, and
 * a statistic over readings is a policy over them. `bridge.json` sends `ping` unrate-limited
 * because it moves slowly, so a sample is one tick and thirty of them is a second and a half —
 * which is the span a player means by "is my connection steady right now", not "was it steady
 * this session". A longer window would average out the burst that is the whole complaint.
 *
 * Sized in samples like `fpsSamples`, and reset by `resetDerivedState()` for the same reason.
 */
const PING_WINDOW = 30;
const pingSamples: number[] = [];

/**
 * The last `hits` pair seen, or null before the first one.
 *
 * Module-level like `fpsSamples`, and reset by `resetDerivedState()` for the same reason: it is
 * derivation scratch, not state anybody renders, and a test that left it set would carry a
 * baseline into the next test and count the wrong number of hits.
 */
let lastHits: { dealt: number; taken: number; sprintDealt: number } | null = null;

/**
 * One entry per landed attack: whether it was a sprint-hit — what the sprint-reset mod reads.
 *
 * **Kept as the raw sequence and not as a rate, because the window is a mod setting.** `hits`
 * carries counters rather than a combo for the same reason and says so; a percentage computed
 * here would be a percentage over a span nobody chose, and the widget would have no way back to
 * the hits it was taken over.
 *
 * Bounded at the largest window the mod offers, so the ring is sized by a number a reader can
 * name rather than by a fight's length. Booleans and not objects: the only fact per hit is which
 * kind it was, and forty of them is the whole structure.
 *
 * Filled from the *deltas* of the two counters, never from an event — a tick that carried two
 * landed hits pushes two entries, which is the property `bridge.json` sends counters to get.
 *
 * Module-level like `fpsSamples`, and reset by `resetDerivedState()` for the same reason.
 */
const SPRINT_HISTORY_MAX = 40;
const sprintHistory: boolean[] = [];

/* ------------------------------------------------------------------ fight review */

/**
 * How long a fight survives without a hit in either direction, in ms — `docs/mod-roster.md` §5's
 * "fight review", and the one policy number the whole feature turns on.
 *
 * **Why ten seconds, and why it is a constant rather than a setting.** Every timeout in this
 * client so far has been a mod setting, because a mod is a thing a player switched on and can
 * therefore be asked about. Fight review is a *screen*, and a screen has no settings page to hang
 * a number on — so this is a choice, made here, with the reasoning in the open rather than a
 * slider that pushes it onto somebody who has never thought about it.
 *
 * Ten is long enough to hold a fight together through a chase — a 1.8 pursuit across half a
 * Bedwars island with no hits landing runs about six seconds — and short enough that two
 * separate engagements in the same minute do not merge into one card that averages them.
 *
 * **The timeout is never applied by a timer.** Nothing here expires: a live fight ends when the
 * next hit arrives more than this long after the last one, and a reader that wants to know
 * whether the live fight is *over* asks the same question of the same constant. That is
 * `combo`'s pattern one layer up — `comboAt` is a timestamp the widget compares, not a value the
 * store retracts — and it is what lets fight review need no clock at all.
 */
export const FIGHT_QUIET_MS = 10000;

/**
 * Seconds of beats one fight keeps, and how many finished fights are kept.
 *
 * Three minutes is longer than any 1.8 fight that is still one fight; past it the timeline stops
 * growing and the totals keep counting, which is the honest degradation — a card that said
 * `47 / 19` over a truncated graph is still telling the truth about the trade.
 *
 * Ten fights is a session's worth of looking back without becoming a history feature. Persisting
 * them is `net/`'s job on a day this reaches the launcher, and nothing here pretends to.
 */
const FIGHT_BEATS_MAX = 180;
const FIGHTS_MAX = 10;

/** One second of a fight: what you landed, what you took, and how fast you were clicking. */
export interface FightBeat {
  /** Whole seconds since the fight opened. */
  t: number;
  /** Hits landed in this second. */
  dealt: number;
  /** Hits taken in this second. */
  taken: number;
  /** Clicks per second, both hands summed, over this second. */
  cps: number;
}

/**
 * One engagement — the record behind the review card.
 *
 * Totals *and* beats, because they answer different questions and neither can be recovered from
 * the other: the totals are exact (they are counter deltas, so a dropped tick cannot lose one),
 * and the beats are where it went wrong. Summing the beats would give a total that drifts from
 * the counters; deriving beats from totals is not possible at all.
 */
export interface Fight {
  /** Monotonic within a session. A React key, and the only stable identity a fight has. */
  id: number;
  /** Wall clock at the first hit, ms. */
  startedAt: number;
  /** Wall clock at the most recent hit, ms. A fight is over `FIGHT_QUIET_MS` after this. */
  lastAt: number;
  /** Hits landed. */
  dealt: number;
  /** Hits taken. */
  taken: number;
  /** Of `dealt`, the ones delivered while sprinting — `hits.sprint_dealt`. */
  sprintDealt: number;
  /**
   * The reach of each landed hit this fight, in blocks.
   *
   * One entry per hit the counters moved by, taking whatever `reach` reads at that moment — and
   * that is worth stating rather than glossing, because `reach` is value-checked on the wire.
   * Two consecutive hits at the same distance publish one update, so the second entry is the
   * first one's figure, which is correct. What is *not* exact is two hits in one tick: the wire
   * carries one reach for the pair, so both entries take it. The mean is right to within one
   * swing's worth in the rare case, and the alternative is a reach *event* stream, which is the
   * shape `bridge.json` refuses for hits and gives its reason for.
   *
   * Empty entries are impossible: a hit landed before the first reach ever arrived contributes
   * nothing rather than a zero, because a reach of 0 is not a point-blank swing.
   */
  reaches: number[];
  /** One entry per second, oldest first. Stops growing at `FIGHT_BEATS_MAX`. */
  beats: FightBeat[];
}

/**
 * The fight being fought, and the scratch that builds it.
 *
 * Module-level like `fpsSamples`, and reset by `resetDerivedState()` for the same reason. The
 * live fight is *also* published to the store, as a fresh object each time it moves, because the
 * review screen renders it while it is happening — so this is the mutable copy and the state
 * holds a snapshot, which is the same split `clickHistory` uses.
 */
let liveFight: Fight | null = null;
let nextFightId = 1;
let fightBeatSecond = 0;

/**
 * The beat a moment falls in, creating it and any silent seconds before it.
 *
 * Gaps are filled rather than closed up, for the reason `clickHistory`'s own gap-fill gives: a
 * timeline whose x-axis skips the quiet seconds is drawing a fight that did not happen, and the
 * quiet seconds in a fight are exactly where it went wrong. Returns null once the fight has run
 * past `FIGHT_BEATS_MAX` — the totals keep counting and the graph stops, which is the honest
 * degradation rather than a loop that overwrites the opening.
 */
function beatFor(fight: Fight, now: number): FightBeat | null {
  const t = Math.max(0, Math.floor((now - fight.startedAt) / 1000));
  const last = fight.beats.length > 0 ? fight.beats[fight.beats.length - 1] : null;
  if (last !== null && last.t === t) return last;
  if (fight.beats.length >= FIGHT_BEATS_MAX) return null;
  for (let fill = last === null ? 0 : last.t + 1; fill < t; fill += 1) {
    if (fight.beats.length >= FIGHT_BEATS_MAX) return null;
    fight.beats.push({ t: fill, dealt: 0, taken: 0, cps: 0 });
  }
  const beat: FightBeat = { t, dealt: 0, taken: 0, cps: 0 };
  fight.beats.push(beat);
  return beat;
}

/** A snapshot of a fight, safe to hand to React. */
function snapshotFight(fight: Fight): Fight {
  return { ...fight, reaches: [...fight.reaches], beats: [...fight.beats] };
}

/**
 * Whether a fight has gone quiet — the one place `FIGHT_QUIET_MS` is applied.
 *
 * Exported because the review screen asks the same question about the live fight that
 * `applyTick` asks about the last one, and two copies of a timeout is how they come apart.
 */
export function fightIsOver(fight: Fight, now: number): boolean {
  return now - fight.lastAt > FIGHT_QUIET_MS;
}

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
function sameInventory(
  a: readonly InventoryEntry[] | null,
  b: readonly InventoryEntry[] | null | undefined,
): boolean {
  if (a == null || b == null) return a == null && b == null;
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i += 1) {
    const x = a[i]!;
    const y = b[i]!;
    if (x.item !== y.item || x.count !== y.count || x.effect !== y.effect || x.splash !== y.splash) {
      return false;
    }
  }
  return true;
}

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

/**
 * Ping jitter: the mean absolute change between consecutive readings, in ms.
 *
 * **Consecutive differences, not a standard deviation about the mean**, and the difference
 * between those two is the whole reason this number is worth drawing. A connection that sits at
 * 30 ms for a second and then at 90 for a second has a large deviation and feels fine; one that
 * alternates 30, 90, 30, 90 has the same deviation and is unplayable. What a player feels is the
 * *step*, so the step is what this measures — which is also how RFC 3550 defines interarrival
 * jitter, for the same reason.
 *
 * Zero until there are enough samples to mean anything. Zero is also a legitimate reading (a
 * perfectly flat link reports 0), and the widget does not need to tell the two apart: on a link
 * with no readings at all the ping itself is `-1` and the chip is already drawing an em-dash.
 *
 * Rounded to a whole millisecond because that is the resolution of the input — `responseTime` is
 * an integer — and a jitter figure with a decimal on it would be claiming precision the sensor
 * does not have.
 */
function pingJitter(): number {
  if (pingSamples.length < 4) return 0;
  let total = 0;
  for (let i = 1; i < pingSamples.length; i += 1) {
    total += Math.abs(pingSamples[i]! - pingSamples[i - 1]!);
  }
  return Math.round(total / (pingSamples.length - 1));
}

/** Reset the derived rings. Tests call this between cases. */
export function resetDerivedState(): void {
  rings.left = createClickRing();
  rings.right = createClickRing();
  fpsSamples.length = 0;
  pingSamples.length = 0;
  clickHistory.length = 0;
  lastSecond = 0;
  lastHits = null;
  sprintHistory.length = 0;
  liveFight = null;
  nextFightId = 1;
  fightBeatSecond = 0;
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
  /**
   * Clicks per second for each hand, one entry per whole second, oldest first — the CPS graph.
   *
   * At most sixty entries, which is the longest window the mod offers. Replaced whole on each
   * second boundary and left alone in between, so the one widget that reads it repaints once a
   * second and nothing else notices it exists.
   */
  clickHistory: readonly { l: number; r: number }[];
  /**
   * Mean absolute change between consecutive ping readings, in ms — the `ping.show_jitter` aside.
   *
   * A steadiness reading rather than a second latency one: 40 ms of ping that never moves plays
   * better than 25 that swings, and the figure the chip already draws cannot say which you have.
   * 0 until there are enough samples, and 0 is also what a flat link reports.
   */
  pingJitter: number;
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
  /**
   * Registry name of the held item; null on an empty hand, with {@link heldCount}.
   *
   * It is what `item_counter.source: inventory` sums by — the item you are holding is the choice,
   * rather than a picker listing a game's worth of ids.
   */
  heldItem: string | null;
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
  /**
   * The two monotonic hit counters as the sensor last sent them — the trade counter's reading.
   *
   * Kept whole rather than derived, because unlike `combo` there is no policy to apply: these
   * *are* the session totals, and `bridge.json` sends counters precisely so a reader can take
   * them at face value after a dropped tick. `combo` is the same two numbers with a timeout
   * over them and lives beside this rather than being computed from it, because a timeout is a
   * mod setting and this pair must never expire.
   *
   * `null` until the first `hits` arrives: zero hits dealt and no reading yet are different
   * states, and only one of them is a chip that should draw `0 / 0`.
   */
  hits: { dealt: number; taken: number; sprintDealt: number } | null;
  /**
   * One entry per landed attack, oldest first: `true` when it was delivered while sprinting.
   *
   * At most forty, which is the longest window the sprint-reset mod offers. The rate is not here
   * on purpose — see the module constant: a rate is a rate *over something*, and what it is over
   * is `sprint_reset.window`, a mod setting.
   *
   * Empty until the first landed hit, and empty is not zero percent: a player who has not swung
   * has no reset rate, and the widget draws nothing rather than a failure they did not have.
   */
  sprintHistory: readonly boolean[];
  /**
   * The fight in progress, or the last one — `docs/mod-roster.md` §5's review card.
   *
   * Not cleared when it goes quiet, and that is the design: a card you can only read *during*
   * the fight is a card nobody reads. `fightIsOver` is how a reader tells a live one from a
   * finished one, and both are worth drawing.
   *
   * `null` only before the first hit of a session.
   */
  liveFight: Fight | null;
  /**
   * Finished fights, newest first, at most ten.
   *
   * Session-only. Persisting them is `net/`'s job on the day this reaches the launcher, and the
   * record here is deliberately the shape that would travel — totals plus beats, no references
   * to anything that only exists in this process.
   */
  fights: readonly Fight[];
  /**
   * How far the last hit moved you, in blocks — the knockback meter.
   *
   * `null` until one has landed, and null is not zero: **zero is a hit that did not move you**,
   * which is a real reading, and no reading at all is a session in which nothing has hit you.
   *
   * Nothing here decides when it moves. `bridge.json`'s `knockback` carries the rule — a fixed
   * ten-tick window from the push, only for a push that came with damage — and the sensor
   * enforces it, which is what keeps this a measurement rather than a score.
   */
  knockback: number | null;
  /**
   * Distance of the last attack that landed, in blocks — the reach readout.
   *
   * `null` until one has landed, and that is the mod rather than a nicety: a reach of 0 is not a
   * point-blank swing, it is a session in which nothing has connected.
   *
   * **Nothing here decides when this moves.** `bridge.json`'s `reach` carries the rule and the
   * sensor enforces it — the field only ever changes on a landed attack, which is what keeps the
   * mod a readout rather than the reach *indicator* `docs/mod-roster.md` §6.1 forbids. The store
   * takes the value at face value on purpose: a reader that re-derived the update rule would be
   * a second place it could be got wrong.
   */
  reach: number | null;
  /**
   * The main inventory, one entry per distinct thing, or `null` before the first reading.
   *
   * Null and empty are different and both reach here: an empty array is an empty inventory,
   * which is a measurement, and null is "the sensor has not said" — a counter must draw nothing
   * for the second rather than a zero nobody measured.
   *
   * The identity is the sensor's (`InventoryTally`), not this store's: three stacks of pearls are
   * one entry, and a water bottle is not a healing potion even though both are `minecraft:potion`.
   * `bridge.json`'s `inventory_entry` carries why.
   */
  inventory: readonly InventoryEntry[] | null;
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
  /**
   * Which fight the review screen is showing, by `Fight.id`, or `0` for "the newest".
   *
   * Navigation state rather than a route, for the same reason `selectedMod` is: the screen is
   * the same screen whichever fight is open, and a route that carried the id would have to be
   * corrected every time a new fight pushed the old one down the list. `0` is a real value here
   * — ids start at 1 — so "follow the newest" is a state rather than an absence, and a player
   * who has not picked anything keeps seeing the fight they just had.
   */
  selectedFight: number;
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
  /** Show this fight on the review screen. */
  selectFight(id: number): void;
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
  clickHistory: [],
  ping: -1,
  pingJitter: 0,
  pos: null,
  armor: [],
  fx: [],
  saturation: null,
  heldCount: null,
  heldItem: null,
  speed: null,
  memory: null,
  combo: 0,
  comboAt: 0,
  hits: null,
  knockback: null,
  sprintHistory: [],
  liveFight: null,
  fights: [],
  reach: null,
  inventory: null,
  server: { host: '', connected: false },
  cpsLeft: 0,
  cpsRight: 0,
  cpsPeak: 0,
  session: null,
  globals: FACTORY_GLOBALS,

  menuOpen: false,
  route: { name: 'mods' },
  selectedMod: 'keystrokes',
  selectedFight: 0,
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
    // Above the menu guard, like the FPS samples and for the same reason: sampling is what keeps
    // the statistic honest, and a window with the menu's worth of readings missing from it would
    // report a calm link for as long as it took to scroll out. `-1` is "no server", not a
    // reading, and averaging it in would make disconnecting look like the worst jitter there is.
    if (tick.ping !== undefined && tick.ping >= 0) {
      pingSamples.push(tick.ping);
      if (pingSamples.length > PING_WINDOW) pingSamples.splice(0, pingSamples.length - PING_WINDOW);
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
    // Recomputed on the ticks the reading moved, not on every tick: the window is 30 samples, so
    // one new reading moves the mean by at most a thirtieth and a figure that changes when the
    // ping did not is a chip repainting for nothing — which on this surface is a full-panel
    // damage rectangle (see the guard above).
    if (patch.ping !== undefined) {
      const jitter = pingJitter();
      if (jitter !== prev.pingJitter) patch.pingJitter = jitter;
    }
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
    // Trimmed to the *longer* of the two readers' windows. `cps.window_ms` goes down to 200 ms,
    // and the CPS graph below counts over a fixed second — so trimming to `w` alone would throw
    // away four fifths of the clicks the graph is about to count, and a player who narrowed the
    // counter's window would silently flatten a different mod's chart.
    const retain = Math.max(w, 1000);
    const left = clicksPerSecond(trimRing(rings.left, now, retain), now, w);
    const right = clicksPerSecond(trimRing(rings.right, now, retain), now, w);
    if (left !== get().cpsLeft) patch.cpsLeft = left;
    if (right !== get().cpsRight) patch.cpsRight = right;

    // One column per whole second of wall clock, for the CPS graph.
    //
    // Fixed at a one-second window rather than the CPS counter's `window_ms`: a column is a
    // second, and reading it through another mod's averaging window would make this graph's
    // meaning depend on a setting on a different page. It is also why the figures are taken here
    // rather than reusing `left` and `right` above.
    const second = Math.floor(now / 1000);
    if (second !== lastSecond) {
      // A gap is elapsed time, not compressed time — the menu was open, or the game was paused.
      // Filling it with silence is what makes the graph's x-axis a clock; dropping it would draw
      // a quiet spell as though it had never happened. Capped, so a long spell is a full window
      // of silence rather than a loop.
      const gap = lastSecond === 0 ? 1 : Math.min(CLICK_HISTORY_MAX, second - lastSecond);
      for (let i = 1; i < gap; i += 1) clickHistory.push({ l: 0, r: 0 });
      clickHistory.push({
        l: clicksPerSecond(rings.left, now, 1000),
        r: clicksPerSecond(rings.right, now, 1000),
      });
      if (clickHistory.length > CLICK_HISTORY_MAX) {
        clickHistory.splice(0, clickHistory.length - CLICK_HISTORY_MAX);
      }
      lastSecond = second;
      // A fresh array, because the widget's selector compares by identity and the entries are
      // the same objects. One allocation of at most sixty pointers, once a second.
      patch.clickHistory = [...clickHistory];

      // The other half of the fight timeline, on the boundary that already exists rather than
      // on a clock of its own. A live fight gets a beat per second whether or not a hit landed
      // in it, which is what makes the graph's x-axis time — and the seconds with no hits are
      // the ones a player is looking for.
      //
      // Guarded on the fight not having gone quiet, so the ten seconds *after* the last hit do
      // not become ten empty columns on the end of every card. The fight is not closed here:
      // closing is the next hit's job, above, because that is the only moment it can be known
      // without asking a clock, and this branch has no business deciding a fight is over.
      if (liveFight !== null && second !== fightBeatSecond && !fightIsOver(liveFight, now)) {
        fightBeatSecond = second;
        const beat = beatFor(liveFight, now);
        if (beat !== null) {
          // Both hands summed and taken over a fixed second, like the CPS graph's columns and
          // for the same reason: a fight's clicking is one hand's story only if you already
          // know which hand, and the review card is read after the fact.
          beat.cps =
            clicksPerSecond(rings.left, now, 1000) + clicksPerSecond(rings.right, now, 1000);
          patch.liveFight = snapshotFight(liveFight);
        }
      }
    }
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
    // `held_count: 0` is the empty hand, and an absent field is "unchanged" like every other
    // field on this payload.
    //
    // **It used to be the other way round and that was a bug.** The sensor value-checks this
    // field, so it is absent whenever the count did not move — and the page read absence as
    // emptiness, so the chip drew a count for exactly one tick after each change and then
    // blanked itself until the next one. Both halves were individually correct:
    // `bridge.json`'s payload rule says an absent field means unchanged, and the field's own
    // description said absent meant empty. The wire says emptiness explicitly now, which is the
    // only shape that leaves both true — a field cannot mean both "nothing changed" and "the
    // thing is gone", and of the two only the second can be stated.
    //
    // `null` stays the store's spelling of an empty hand: it is what the widget's `count ??
    // sample` already reads, and a stack of zero is not a stack.
    if (tick.held_count !== undefined) {
      const held = tick.held_count > 0 ? tick.held_count : null;
      if (held !== prev.heldCount) patch.heldCount = held;
      // One source of truth for "the hand is empty". `held_item` is omitted when nothing is
      // held, so it is cleared from *this* field's zero rather than from its own absence —
      // which would be the same ambiguity again, one field along.
      if (held === null && prev.heldItem !== null) patch.heldItem = null;
    }
    if (tick.held_item !== undefined && tick.held_item !== prev.heldItem) {
      patch.heldItem = tick.held_item;
    }

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
    // Compared by value, never by identity: the sensor sends this only on the ticks where it
    // actually changed, so an identity check would be redundant on the way in and the real work
    // is not repeating it — but the bridge deserialises a fresh array either way, and assigning
    // it unconditionally would republish on every tick that carried one. `sameInventory` is
    // shallow and hand-written for the same reason `sameArmor` is: the shape is `bridge.json`'s,
    // it is tiny and fixed, and a generic deep-equal would hide a schema change behind a walk.
    if (tick.inventory !== undefined && !sameInventory(prev.inventory, tick.inventory)) {
      patch.inventory = tick.inventory as InventoryEntry[];
    }

    // Assigned straight through: the sensor has already decided that this is a landed attack's
    // distance and rounded it, and there is nothing left here to judge (see the field's note).
    if (tick.reach !== undefined && tick.reach !== prev.reach) patch.reach = tick.reach;
    // Assigned straight through like `reach`, and for the same reason: the sensor has already
    // decided this is a completed measurement and rounded it, and there is nothing left to judge.
    if (tick.knockback !== undefined && tick.knockback !== prev.knockback) {
      patch.knockback = tick.knockback;
    }

    if (tick.hits !== undefined) {
      const seen = lastHits;
      // `sprint_dealt` is optional on the wire and absence means unchanged, like every field on
      // this payload — so it is carried forward from the last reading rather than defaulted to
      // zero. Defaulting would make a tick where the sensor had no reading look like a player
      // whose every hit since the session began stopped being a sprint-hit at once.
      const sprintDealt = tick.hits.sprint_dealt ?? seen?.sprintDealt ?? 0;
      lastHits = { dealt: tick.hits.dealt, taken: tick.hits.taken, sprintDealt };
      // The pair itself, for the trade counter. Written on first sight, unlike the combo below:
      // a baseline is something a *derived* value needs, and these two are not derived — they
      // are the session totals the sensor is reporting, and the first push is as true as the
      // hundredth. Replaced only when a figure moved, so a chip subscribed to this object is
      // not re-rendered by a tick that carried the same counters.
      if (
        prev.hits === null ||
        prev.hits.dealt !== tick.hits.dealt ||
        prev.hits.taken !== tick.hits.taken ||
        prev.hits.sprintDealt !== sprintDealt
      ) {
        patch.hits = { dealt: tick.hits.dealt, taken: tick.hits.taken, sprintDealt };
      }
      if (seen !== null) {
        let combo = prev.combo;
        if (tick.hits.taken > seen.taken) combo = 0;
        const landed = tick.hits.dealt - seen.dealt;
        if (landed > 0) {
          combo += landed;
          patch.comboAt = Date.now();
        }
        if (combo !== prev.combo) patch.combo = combo;

        // One entry per hit the counters moved by, sprint-hits first. The order within a single
        // tick is a guess — the wire carries two totals, not a sequence — and it is a guess that
        // cannot change any answer this mod gives: every window it offers is a count of `true`
        // over the last N, and reordering entries inside one tick moves neither. Guessing was
        // still the choice worth naming, because the alternative is a sequence on the wire, and
        // an *event* stream is what `bridge.json` refuses for hits and gives its reason for.
        const sprintLanded = sprintDealt - seen.sprintDealt;
        for (let i = 0; i < landed; i += 1) sprintHistory.push(i < sprintLanded);
        if (landed > 0) {
          if (sprintHistory.length > SPRINT_HISTORY_MAX) {
            sprintHistory.splice(0, sprintHistory.length - SPRINT_HISTORY_MAX);
          }
          patch.sprintHistory = [...sprintHistory];
        }

        // -------------------------------------------------------------- the fight
        //
        // A fight is a run of combat with quiet on both sides of it, and this is the whole of
        // that rule: the first hit in either direction after a gap opens one, and every hit
        // until the next gap belongs to it. No timer, no world event, no opponent identity.
        //
        // **Why not scope it to an opponent.** The client does know which entity a swing
        // resolved onto — `onAttackedEntity` already reports it for `hit_color.own_hits_only` —
        // and scoping by it would be wrong in the case a review card is most wanted: a 2v1 is
        // one fight, and splitting it into two cards would report two comfortable trades where
        // the player actually lost. It would also be unable to scope the hits *taken* at all,
        // because `taken` rides the rising edge of `hurtTime` and the client is never told who
        // did it. A definition that can only see half the fight is not a definition.
        //
        // **Why hits and not damage.** Damage is the server's arithmetic — armour, enchants,
        // absorption — and the client sees only its own health, which moves for reasons that
        // are not fights. Hits are the thing this client can count exactly.
        const taken = tick.hits.taken - seen.taken;
        if (landed > 0 || taken > 0) {
          if (liveFight === null || fightIsOver(liveFight, now)) {
            // The previous one is finished the moment a new one starts, which is the only
            // moment it can be *known* finished without asking a clock. Pushed newest-first so
            // the screen reads top-down without reversing.
            if (liveFight !== null) {
              const closed = [snapshotFight(liveFight), ...prev.fights].slice(0, FIGHTS_MAX);
              patch.fights = closed;
            }
            liveFight = {
              id: nextFightId,
              startedAt: now,
              lastAt: now,
              dealt: 0,
              taken: 0,
              sprintDealt: 0,
              reaches: [],
              beats: [],
            };
            nextFightId += 1;
            fightBeatSecond = Math.floor(now / 1000);
          }
          liveFight.lastAt = now;
          liveFight.dealt += landed;
          liveFight.taken += taken;
          liveFight.sprintDealt += sprintLanded;
          // The reach of each hit, taking whatever the field reads now — see `Fight.reaches`
          // for what that is exact about and what it is not. `prev.reach` rather than
          // `patch.reach`, because a hit at the same distance as the last one publishes no
          // update and its reach is still the last one's figure.
          const at = patch.reach !== undefined ? patch.reach : prev.reach;
          if (at !== null) {
            for (let i = 0; i < landed; i += 1) liveFight.reaches.push(at);
          }
          // The beat this hit falls in, so a fight shorter than a second still has a timeline.
          // The second boundary below extends it; this seeds it.
          const beat = beatFor(liveFight, now);
          if (beat !== null) {
            beat.dealt += landed;
            beat.taken += taken;
          }
          patch.liveFight = snapshotFight(liveFight);
        }
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
  selectFight(id) {
    set({ selectedFight: id });
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
