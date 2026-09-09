/**
 * Stopwatch — a manual timer, started and zeroed from the keyboard.
 *
 * In its own module for the reason `watermark.tsx` set and `mods/art.tsx` states as a
 * principle: everything a *single* mod draws lives in that mod's own file, so the shared file
 * stops growing when the roster does. This one earns the separation three times over, because
 * it is the only HUD mod that owns a clock, a piece of state, and a bridge subscription.
 *
 * ## Two mods, two kinds of clock
 *
 * `combo.tsx` is the closest thing to this file and the difference is worth stating first. The
 * combo chip's clock only ever *expires* something the store already knows — `comboAt` is a
 * fact pushed by the sensor and the widget merely holds it against a setting. Here there is no
 * sensor at all: `schema/mods/stopwatch.json` says so outright — "no game field ... the
 * overlay's own clock" — so this file owns the reading itself, and the only thing that crosses
 * the bridge is the *input* that starts and zeroes it.
 *
 * ## Where the run state lives, and why it is here rather than in the store
 *
 * The state is two numbers — {@link StopwatchRun} — and it lives in this module, beside the
 * widget, the way `store/cps.ts` keeps its click rings beside the store. Four things decided
 * that, in order:
 *
 *   · **It is not a setting.** Nothing should persist it: a loadout that remembered `4:07`
 *     across a restart would be remembering a fight that is over. `start_key` and `reset_key`
 *     are settings; what they do to the clock is not.
 *   · **It must survive a re-render and the menu.** So not `useState`: the HUD editor mounts
 *     and unmounts this widget as the player drags it, and a stopwatch that zeroed itself
 *     because a chip was re-parented would be worse than one that could not be started at all.
 *     Module scope outlives every mount in this bundle.
 *   · **It is rendered, so something must subscribe to it.** This is the `cpsPeak` argument in
 *     `store.ts` — the one place that says precisely when a derived value has to be
 *     subscribable rather than a module scratch value, and the reason it gives is that "a peak
 *     nothing subscribed to would not repaint the chip when it moved". Exactly so here: a
 *     press that arrives while the clock is stopped changes the reading and nothing else in
 *     this page would know. The rings escape that rule because nothing renders them directly;
 *     this does not. Hence {@link subscribeRun} and `useSyncExternalStore` below — the same
 *     shape zustand itself is, so the chip repaints on a press the way it would from the
 *     store, and no press can move a value with no listeners.
 *   · **What is left is where, not whether.** With the subscription in place the store buys
 *     nothing: `store.ts`'s live-data half is written by `window.void.on(...)` and nothing
 *     else (§9), and this value is written by no channel — `modaction` is a *request*, not
 *     state, and `VoidEventPayloadMap.modaction` says as much ("nothing is stored ... there is
 *     no stored value to bind to and nothing to reconcile"). Putting it in the store would
 *     have meant a bridge subscription in `connect.ts` and a reducer in `store.ts` for a value
 *     no other mod reads, which is the shape this package has been moving *away* from — the
 *     combo timeout is in the widget for the same reason.
 *
 * If a second mod ever needs a `modaction` of its own, the honest refactor is a shared
 * dispatch in `bridge/`, not a copy of {@link subscribeModActions}; today there is exactly one
 * caller and it is this file.
 *
 * ## The repaint budget
 *
 * `store.ts` opens by saying there is no animation-frame loop anywhere in this bundle and that
 * the 20 Hz `tick` push is the clock, so a private high-frequency interval for one chip would
 * break that quietly. Nothing here is an interval: like `combo.tsx` this arms **one**
 * self-chaining `setTimeout` for the exact next edge of the drawn figure. See
 * {@link MILLIS_STEP_MS} for what that edge is at each `show_millis` setting and what it
 * costs.
 *
 * ## What is *not* here
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are applied by the slot around the
 * widget (`HudLayer`, `hud/chrome.ts`) and on the mod page by `PreviewZoom` in the same place
 * and the same order. Reading any of them here would apply them twice.
 */

import { memo, useEffect, useReducer, useSyncExternalStore } from 'react';

import { StopwatchChip, type StopwatchFormat } from '@/ui';
import { getVoid } from '@/bridge/connect';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. It is a real registry id; the string is here to be imported. */
export const STOPWATCH_ID = 'stopwatch';

/* ------------------------------------------------------------------ the clock */

/**
 * The whole of the stopwatch's transient state.
 *
 * Two fields rather than one running total, because a total would have to be *written* on a
 * schedule to stay true, and this way nothing writes anything between presses: the reading is
 * derived from `Date.now()` at the moment it is drawn ({@link elapsedOf}), so a chip that has
 * not repainted for a second is not a chip that has fallen a second behind — it is a chip
 * nobody was looking at, which then draws the right number the instant it is looked at.
 *
 * That is also what makes the menu free. The clock keeps time behind an open panel without a
 * timer running, because there is no timer *keeping* the time; there is only one deciding when
 * to redraw it.
 */
export interface StopwatchRun {
  /** Milliseconds banked by earlier runs. The whole reading while the clock is stopped. */
  baseMs: number;
  /** `Date.now()` when the current run began, or `null` when the clock is not running. */
  startedAt: number | null;
}

/** The stopped, never-started clock — and what {@link resetStopwatch} restores. */
const ZERO: StopwatchRun = { baseMs: 0, startedAt: null };

/**
 * The live run state.
 *
 * Replaced rather than mutated on every change, so the object identity *is* the version and
 * `useSyncExternalStore`'s snapshot comparison needs nothing else.
 */
let run: StopwatchRun = ZERO;

/** Everything currently rendering the run state. */
const runListeners = new Set<() => void>();

function setRun(next: StopwatchRun): void {
  run = next;
  for (const listener of runListeners) listener();
}

/** The current run state. The `getSnapshot` half of {@link HudStopwatch}'s subscription. */
export function stopwatchRun(): StopwatchRun {
  return run;
}

/** The `subscribe` half. Module-level so its identity is stable across renders. */
export function subscribeRun(onChange: () => void): () => void {
  runListeners.add(onChange);
  return () => {
    runListeners.delete(onChange);
  };
}

/** The reading a state draws at `now`, in ms. Never negative, even against a clock that moved. */
export function elapsedOf(state: StopwatchRun, now: number): number {
  if (state.startedAt === null) return state.baseMs;
  return state.baseMs + Math.max(0, now - state.startedAt);
}

/**
 * Act on one of the mod's two actions. Anything else is ignored, deliberately.
 *
 * `ModactionPayload.action` is a plain `string` and its contract says an unrecognised action
 * "is ignored, not an error" — the event is dispatched by `mod` first, so a verb this build
 * has never heard of is a no-op inside this function rather than a lost channel.
 *
 * **`start_stop` is one key for two verbs**, per `stopwatch.start_key`: it banks the current
 * run and stops, or starts a new one from the banked total. Nothing is ever recomputed from a
 * stale `startedAt`, so a stop is exact to the millisecond the key was seen.
 *
 * **`reset` zeroes the reading and leaves the run state alone**, which is `stopwatch.reset_key`
 * verbatim: "a reset while running is a lap restart and a reset while stopped clears the last
 * reading". So a reset that arrives *while running* does not stop the clock — it drops
 * `baseMs` to zero and re-bases `startedAt` to now, and the chip carries on from `0:00` with
 * its dot still `ok`. That is the behaviour a lap key has on every physical stopwatch, and it
 * is the one case where doing the obvious thing (setting both fields to zero) would silently
 * stop a clock the player expected to keep running.
 *
 * Exported so it can be exercised without a bridge; the widget never calls it directly.
 */
export function applyStopwatchAction(action: string, now: number = Date.now()): void {
  if (action === 'start_stop') {
    setRun(
      run.startedAt === null
        ? { baseMs: run.baseMs, startedAt: now }
        : { baseMs: elapsedOf(run, now), startedAt: null },
    );
  } else if (action === 'reset') {
    setRun({ baseMs: 0, startedAt: run.startedAt === null ? null : now });
  }
}

/** Back to a stopped zero. For a harness or a test that wants a clean clock. */
export function resetStopwatch(): void {
  setRun(ZERO);
}

/* -------------------------------------------------------------- the bridge */

/**
 * How many mounted widgets are relying on the `modaction` subscription.
 *
 * **Refcounted, and it has to be.** More than one live widget is a normal state, not an edge
 * case: `App` keeps the HUD layer mounted at all times and the HUD editor renders a second
 * copy of every widget while it is open, so two instances subscribing independently would each
 * act on the same press and `start_stop` would toggle twice — a key that does nothing, in the
 * one screen where the player is looking straight at the chip. One subscription, however many
 * chips are drawing from it.
 *
 * Registered from an effect rather than at import time because `getVoid()` throws until
 * `connectBridge()` has run, and dropped when the last widget unmounts so that a bridge
 * replaced under us (tests call `dispose()` between cases) is not held by a dead handler.
 */
let bridgeRefs = 0;
let offModaction: (() => void) | null = null;

function subscribeModActions(): () => void {
  bridgeRefs += 1;
  if (bridgeRefs === 1) {
    // The same `bridge.on(name, handler)` `connect.ts` uses for the other ten channels — this
    // one is subscribed here rather than there because what it changes is this mod's own
    // state, which is in this module for the reasons at the top of the file. Filtered by `mod`
    // first: the channel is shared by every mod that has an action, and a widget must never
    // act on another mod's press.
    offModaction = getVoid().on('modaction', (payload) => {
      if (payload.mod !== STOPWATCH_ID) return;
      applyStopwatchAction(payload.action);
    });
  }
  return () => {
    bridgeRefs -= 1;
    if (bridgeRefs === 0) {
      offModaction?.();
      offModaction = null;
    }
  };
}

/* ------------------------------------------------------------- the repaints */

/** One second, the edge of the drawn figure while `show_millis` is off. */
const SECOND_MS = 1000;

/**
 * How often the chip repaints while `show_millis` is on: 50 ms, i.e. 20 Hz.
 *
 * **This is the repaint budget and it is chosen as one**, the way `combo.tsx` chooses its four
 * bands. The two settings do not want the same rate and neither wants a loop:
 *
 *   · `show_millis` **off** — the drawn figure changes only when a whole second turns over, so
 *     the timeout is armed for that exact instant and the chip repaints **once a second** while
 *     running. There is nothing cheaper that is still a clock.
 *   · `show_millis` **on** — the hundredths digit turns over 100 times a second, and honouring
 *     that would be a 100 Hz timer for one chip in a bundle whose stated clock is the 20 Hz
 *     `tick` (`store.ts`, first paragraph). So the edge is capped here: **20 repaints a
 *     second**, the same rate as the sensor push that already drives every other readout, and
 *     the hundredths advance in steps of about five.
 *
 *     That is not a compromise the player can see. A digit changing twenty times a second is
 *     already past the point where anyone reads its *value* — what a hundredths field
 *     communicates at speed is that the clock is moving, and it does that identically at 20 Hz
 *     and at 100 Hz. The schema makes the same call one digit further along, refusing
 *     thousandths because it would be "a digit nobody can read". This is the same argument
 *     applied to the refresh rather than to the field.
 *
 *   · **Stopped, previewing, or behind the menu — zero.** See the effect in
 *     {@link HudStopwatch}: a stopped clock has no next edge, the preview's reading is a
 *     constant, and a HUD behind an open panel is not being read (`applyTick` is held for the
 *     same reason). Closing the menu re-runs the effect, which re-reads the real clock, so the
 *     chip is correct the instant it is visible again.
 *
 * The worst case is therefore 20 repaints a second, of one chip, only while a clock the player
 * started is running, only while they can see it, and only if they asked for hundredths.
 */
const MILLIS_STEP_MS = 50;

/* --------------------------------------------------------------- the fixture */

/**
 * The reading the mod page's preview draws: **4:07.62**.
 *
 * `format` and `show_millis` both have to move this picture, and the fixture is what decides
 * whether they can — the `HudCps` click-burst argument in a different shape.
 *
 * *Why not an hour.* The obvious fixture is one past an hour, because that is where `mmss`'s
 * carry shows off (`64:07`). It is the wrong one, and `formatElapsed` is why: past an hour
 * `auto` prints the hour field too, so `auto` and `hmmss` are **the same string** — by
 * construction, for every value over 3,600,000 ms. A player dragging the control would see
 * two of the three options draw identically and conclude one of them was broken, on the page
 * whose entire job is showing them what the options do.
 *
 * *Why under ten minutes.* Between ten minutes and an hour the collapse moves rather than
 * disappears: with no hours and two-digit minutes, `auto` and `mmss` are both `MM:SS` and
 * identical — which `test/preview.test.tsx` would report as an inert setting, since the value
 * it flips `auto` to is `mmss`. Under ten minutes, with no hours, all three separate:
 *
 *   · `auto`  → `4:07`     — the narrowest honest reading, and the schema's own example
 *   · `mmss`  → `04:07`    — the fixed two-digit minute
 *   · `hmmss` → `0:04:07`  — the hour printed whether or not there is one, which is the
 *     entire content of that option and the only fixture range where it is visible at all
 *
 * Three options, three readings, no pair alike. What this fixture cannot also show is `mmss`
 * carrying past 59, and that is not a fixture that was available: a live `hmmss` and a carried
 * `mmss` are mutually exclusive in one frame. The option that would have gone dark is chosen
 * on which misreading costs more — "this control does nothing" is worse than "this control
 * pads the minutes", and only one of the two is a false statement about the mod.
 *
 * *Why the hundredths are 62.* `show_millis` appends `.62`, which changes the drawing at every
 * `format` — and 62 is not a value anyone reads as a placeholder the way `.00` or `.50` would
 * be, so the fixture looks like a reading rather than like a round number chosen to fill a
 * slot.
 *
 * *Why 4:07 is also an honest reading.* It is a fight, a bridge run, a potion window — the
 * durations `schema/mods/stopwatch.json` names as what the mod is for. A preview showing
 * `1:04:07` would be a picture of a session timer, which is the rarer use.
 */
const SAMPLE_ELAPSED_MS = 247_620;

/** A stored `format` narrowed to what the chip draws; anything else is the factory default. */
function asStopwatchFormat(value: unknown): StopwatchFormat {
  return value === 'mmss' || value === 'hmmss' ? value : 'auto';
}

/* ---------------------------------------------------------------- the widget */

/**
 * The chip.
 *
 * ## It never hides
 *
 * `combo.tsx` disappears at zero and this one deliberately does not, which is the one place
 * these two files disagree. The combo count is the *game's* number and a `0` between fights is
 * noise on a screen whose job is the fight. A stopwatch at `0:00` is not noise, for two
 * reasons the schema states rather than leaves to the widget:
 *
 *   · `start_key` defaults to `NONE` and the schema's own account of that default is that "a
 *     timer nobody bound costs nothing but a chip that reads `0:00`" — i.e. the zero chip is
 *     the documented resting state, not an empty case;
 *   · `reset_key`'s whole purpose while stopped is to "clear the last reading", and a reset
 *     whose visible result was the chip *vanishing* would read as having turned the mod off.
 *
 * There is also a difference in provenance. Every widget that returns `null` does so because
 * the game has given it nothing — no armour, no effects, no position. Nothing gives this one
 * anything: it is on screen because the player enabled it and dragged it somewhere, and a
 * readout that answers a placement with absence is answering the wrong question.
 *
 * ## The preview draws the fixture, always
 *
 * Every other widget lets live data win over its `sample`, so a player standing in a match
 * configures the mod against their own numbers. Not here, and it is `combo.tsx`'s argument
 * about `SAMPLE_AGE_MS` in a stronger form: this page is only ever seen with the menu open,
 * where the repaint timer is deliberately not armed, so a live reading would be *frozen at the
 * moment the menu opened* while looking exactly like a running clock. And the common live
 * value is `0:00`, on a page that exists to show three formats apart. The fixture is also what
 * keeps `format` honest independently of what the player's own clock happens to read — see
 * {@link SAMPLE_ELAPSED_MS}, where two whole ranges of elapsed time make two of the options
 * draw identically.
 *
 * The preview's dot is `ok`: the picture is of the mod doing its job, which is the state worth
 * showing, and a frozen figure under a running dot is the pairing `StopwatchChip` explicitly
 * calls the quiet one of the two.
 */
export const HudStopwatch = memo(function HudStopwatch({
  variant,
  sample,
  className,
}: HudWidgetProps) {
  // The run state, subscribed rather than read: a press that arrives while this chip is on
  // screen has to repaint it, and only a subscription can do that (`cpsPeak`, `store.ts`).
  const state = useSyncExternalStore(subscribeRun, stopwatchRun);
  // Selected as primitives, like `HudCps` and `HudCombo`: `modSettings` builds a fresh object
  // every call and zustand compares snapshots with `Object.is`, so a selector returning the
  // object would hand React a new value on every render.
  const showMillis = useVoidStore(
    (s) => modSettings(s.loadout, STOPWATCH_ID).show_millis === true,
  );
  const format = useVoidStore((s) =>
    asStopwatchFormat(modSettings(s.loadout, STOPWATCH_ID).format),
  );
  const menuOpen = useVoidStore((s) => s.menuOpen);
  const [, repaint] = useReducer((n: number) => n + 1, 0);

  /**
   * The keys, for as long as this widget is mounted.
   *
   * The preview does not subscribe: it draws a fixture, so an action it received would move a
   * clock it is not showing, and the live widget behind the menu is already holding the one
   * subscription (see {@link subscribeModActions} for why there is exactly one).
   */
  useEffect(() => {
    if (sample) return;
    return subscribeModActions();
  }, [sample]);

  /**
   * One timeout, armed for the moment the drawn figure next changes.
   *
   * Not an interval, and not a frame loop: the edges are known exactly, so the cheap shape is
   * to sleep until the next one and re-arm from the clock rather than to wake up and ask.
   * Three states arm nothing at all — {@link MILLIS_STEP_MS} says why each of them is free.
   *
   * The origin is the *virtual zero of the reading* (`startedAt - baseMs`), not the moment the
   * run began, so the edges line up with the figure rather than with the last press: after a
   * stop and a start at 3.4 s the next second still turns over at 4.0 s on the chip, where an
   * origin of `startedAt` would have drawn `3` for a further 600 ms and then jumped.
   *
   * Waking early is safe by construction. `arm` recomputes the next edge from the current
   * clock, so a timer that fires a millisecond short lands on the same edge again and re-arms
   * for what is left of it, rather than skipping a value.
   */
  useEffect(() => {
    if (sample || menuOpen || state.startedAt === null) return;
    const step = showMillis ? MILLIS_STEP_MS : SECOND_MS;
    const origin = state.startedAt - state.baseMs;
    let timer = 0;
    const arm = () => {
      const now = Date.now();
      const next = origin + (Math.floor((now - origin) / step) + 1) * step;
      timer = window.setTimeout(
        () => {
          repaint();
          arm();
        },
        Math.max(1, next - now),
      );
    };
    arm();
    return () => window.clearTimeout(timer);
  }, [sample, menuOpen, showMillis, state.startedAt, state.baseMs]);

  return (
    <StopwatchChip
      variant={variant}
      className={className}
      elapsedMs={sample ? SAMPLE_ELAPSED_MS : elapsedOf(state, Date.now())}
      running={sample ? true : state.startedAt !== null}
      showMillis={showMillis}
      format={format}
    />
  );
});
