/**
 * Store reducers against the real fake bridge from `@void/protocol` — the same
 * object the `?debug` harness runs, so these tests exercise the actual call and
 * event shapes of `bridge.json`, not a hand-written mock.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { connectBridge } from '@/bridge/connect';
import {
  FIGHT_QUIET_MS,
  fightIsOver,
  hudItem,
  isModOn,
  modSettings,
  resetDerivedState,
  useVoidStore,
} from '@/store/store';
import type { KeysPayload } from '@/bridge/protocol';

let dispose: () => void;

function keys(patch: Partial<KeysPayload>): KeysPayload {
  return { w: 0, a: 0, s: 0, d: 0, lmb: 0, rmb: 0, space: 0, shift: 0, ...patch };
}

beforeEach(() => {
  resetDerivedState();
  useVoidStore.setState({
    loadout: null,
    library: [],
    keys: keys({}),
    fps: 0,
    fpsLow: 0,
    ping: -1,
    pos: null,
    armor: [],
    fx: [],
    cpsLeft: 0,
    cpsRight: 0,
    menuOpen: false,
    route: { name: 'mods' },
    paletteOpen: false,
    // `resetDerivedState()` clears the module-level scratch; these are store *state*, and the
    // two are deliberately separate — a fight the store has published is not derivation scratch,
    // it is a reading the review screen is drawing. Listing them here is the same contract every
    // other field on this object is under.
    reach: null,
    hits: null,
    sprintHistory: [],
    liveFight: null,
    fights: [],
    selectedFight: 0,
  });
  // No timer: every test drives the clock itself.
  ({ dispose } = connectBridge({ forceFake: true, runFakeClock: false }));
});

afterEach(() => dispose());

describe('bridge ingestion', () => {
  it('receives the active loadout and the library on connect', () => {
    const state = useVoidStore.getState();
    expect(state.loadout).not.toBeNull();
    expect(state.library.length).toBeGreaterThan(1);
  });

  it('fills the library from the `loadouts` event, not from loadouts it happened to see', () => {
    // The library arrives whole on the `loadouts` channel — Rust sends full loadouts in
    // `init.loadouts` — so every card on the Loadouts frame has mod state to compare,
    // including the ones never switched to.
    const library = useVoidStore.getState().library;
    expect(library.length).toBe(3);
    for (const l of library) {
      expect(l.mods).toBeTypeOf('object');
      expect(Array.isArray(l.hud)).toBe(true);
    }
  });

  it('a `setting` push applies one key without replacing the loadout', () => {
    const before = useVoidStore.getState().loadout!;
    useVoidStore.getState().applySetting({ id: 'keystrokes', key: 'opacity', value: 0.4 });
    const after = useVoidStore.getState().loadout!;
    expect(modSettings(after, 'keystrokes').opacity).toBe(0.4);
    expect(after.id).toBe(before.id);
    // Everything the push did not name is the same object it was.
    expect(after.hud).toBe(before.hud);
  });

  it('keeps the library copy of the active loadout in step with a setting write', () => {
    const id = useVoidStore.getState().loadout!.id;
    useVoidStore.getState().setSetting('keystrokes', 'opacity', 0.25);
    const inLibrary = useVoidStore.getState().library.find((l) => l.id === id)!;
    expect(modSettings(inLibrary, 'keystrokes').opacity).toBe(0.25);
  });

  it('replaces the loadout wholesale on a switch', () => {
    const other = useVoidStore.getState().library.find(
      (l) => l.id !== useVoidStore.getState().loadout?.id,
    )!;
    useVoidStore.getState().switchLoadout(other.id);
    expect(useVoidStore.getState().loadout?.id).toBe(other.id);
  });

  it('treats an absent tick field as unchanged', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ fps: 142, ping: 38 });
    apply({ fps: 120 });
    expect(useVoidStore.getState().fps).toBe(120);
    expect(useVoidStore.getState().ping).toBe(38);
  });

  it('derives the combo from the hit counters, and counts nothing on first sight', () => {
    const apply = useVoidStore.getState().applyTick;
    // The counters are session-monotonic, so the first pair is a baseline. Counting it would
    // report an entire match's hits at once to a page that reloaded mid-fight.
    apply({ hits: { dealt: 40, taken: 7 } });
    expect(useVoidStore.getState().combo).toBe(0);

    apply({ hits: { dealt: 41, taken: 7 } });
    expect(useVoidStore.getState().combo).toBe(1);

    // By the delta, not by one: a tick carrying two hits is still exactly right, which is the
    // reason the wire sends counters rather than events.
    apply({ hits: { dealt: 43, taken: 7 } });
    expect(useVoidStore.getState().combo).toBe(3);

    // Being hit breaks it.
    apply({ hits: { dealt: 43, taken: 8 } });
    expect(useVoidStore.getState().combo).toBe(0);

    // Hit and being hit on the same tick: the break wins, then the landed hit re-opens. Worth
    // pinning because the order is a real decision — the other order would swallow the hit.
    apply({ hits: { dealt: 44, taken: 9 } });
    expect(useVoidStore.getState().combo).toBe(1);
  });

  /**
   * This test used to assert the opposite, and the opposite was a bug.
   *
   * It read: "`held_count` is the one tick field whose absence is a value: the sensor omits it
   * for an empty hand rather than sending 0". That was the field's description, faithfully
   * transcribed — and it cannot be true at the same time as the payload's own rule, which
   * `bridge.json` states one paragraph above the field: "a handler must treat an absent field as
   * unchanged". `held_count` is value-checked at the sensor, so it is absent on every tick where
   * the count did not move as well as on every tick where the hand is empty, and the two are
   * indistinguishable downstream.
   *
   * The symptom was a chip that drew its figure for exactly one tick after each change and then
   * blanked itself. **The next test in this file asserts the correct discipline for `saturation`,
   * `speed` and `memory`** — "holds a Wave 2 reading that the sensor coalesced away" — so the two
   * sat side by side saying opposite things about the same payload, and this one was the wrong
   * one. A test that pins a bug reads exactly like a test that pins a contract.
   *
   * Emptiness is a value now (`held_count: 0`), because of the two meanings only that one can be
   * stated: a field cannot also say "nothing changed" by being absent if absence already means
   * something else.
   */
  it('holds the held count through the ticks that omit it, and reads 0 as the empty hand', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ held_count: 64, held_item: 'minecraft:ender_pearl' });
    expect(useVoidStore.getState().heldCount).toBe(64);
    expect(useVoidStore.getState().heldItem).toBe('minecraft:ender_pearl');

    // A tick that carries something else carries neither of those, and changes neither.
    apply({ fps: 100 });
    expect(useVoidStore.getState().heldCount, 'absent means unchanged').toBe(64);
    expect(useVoidStore.getState().heldItem).toBe('minecraft:ender_pearl');

    // The hand empties, and the wire says so.
    apply({ held_count: 0 });
    expect(useVoidStore.getState().heldCount).toBeNull();
    expect(useVoidStore.getState().heldItem, 'one source of truth for an empty hand').toBeNull();
  });

  it('holds a Wave 2 reading that the sensor coalesced away', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ saturation: 12.5, speed: 4.2, memory: { used_mb: 900, max_mb: 4096 } });
    apply({ fps: 100 });
    const s = useVoidStore.getState();
    expect(s.saturation).toBe(12.5);
    expect(s.speed).toBe(4.2);
    expect(s.memory).toEqual({ usedMb: 900, maxMb: 4096 });
  });

  it('keeps the previous armour list on ticks that omit it', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ armor: [{ slot: 'helmet', item: 'diamond_helmet', damage: 0, max_damage: 363 }] });
    apply({ fps: 100 });
    expect(useVoidStore.getState().armor).toHaveLength(1);
  });

  it('publishes nothing when a repeated tick carries the same values', () => {
    // The bug this pins: `pos` arrives as a fresh object every tick, so assigning it
    // unconditionally made the store publish a change 20 times a second while the player stood
    // still. Every one of those repainted the whole menu panel — measured at ~50 ms each, three
    // consecutive stalls on byte-identical payloads. Identity is not change.
    const apply = useVoidStore.getState().applyTick;
    const tick = {
      fps: 120,
      ping: 38,
      pos: { x: 20.07, y: 64, z: 281.85, yaw: 155.85 },
      armor: [{ slot: 'helmet' as const, item: 'diamond_helmet', damage: 0, max_damage: 363 }],
    };
    apply({ ...tick, pos: { ...tick.pos }, armor: [{ ...tick.armor[0]! }] });

    let published = 0;
    const stop = useVoidStore.subscribe(() => {
      published += 1;
    });
    // Same values, all-new objects, exactly as the bridge delivers them.
    for (let i = 0; i < 5; i += 1) {
      apply({ ...tick, pos: { ...tick.pos }, armor: [{ ...tick.armor[0]! }] });
    }
    stop();
    expect(published).toBe(0);
  });

  it('still publishes when a tick value actually moves', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ pos: { x: 1, y: 64, z: 1, yaw: 0 } });
    apply({ pos: { x: 1, y: 64, z: 1, yaw: 0 } });
    expect(useVoidStore.getState().pos).toEqual({ x: 1, y: 64, z: 1, yaw: 0 });
    apply({ pos: { x: 2, y: 64, z: 1, yaw: 0 } });
    expect(useVoidStore.getState().pos?.x).toBe(2);
    apply({ fps: 60 });
    expect(useVoidStore.getState().fps).toBe(60);
  });

  it('ignores a loadout echo that says nothing new', () => {
    // Java echoes the whole loadout after every change, and toggleMod has already applied it
    // optimistically. The echo is a freshly parsed object, so it is never reference-equal — and
    // taking it re-rendered the pane, the grid and the HUD, repainting megapixels at ~50 ms each.
    const before = useVoidStore.getState().loadout!;
    expect(before).not.toBeNull();

    // Structurally identical, entirely new references — exactly what the bridge delivers.
    useVoidStore.getState().applyLoadout(JSON.parse(JSON.stringify(before)));
    expect(useVoidStore.getState().loadout).toBe(before);

    // A real change still lands.
    const changed = JSON.parse(JSON.stringify(before));
    changed.name = `${before.name} edited`;
    useVoidStore.getState().applyLoadout(changed);
    expect(useVoidStore.getState().loadout).not.toBe(before);
    expect(useVoidStore.getState().loadout?.name).toBe(`${before.name} edited`);
  });

  it('holds live tick values while the menu covers the HUD, in game only', () => {
    // Ultralight's damage is one bounding rectangle, so an fps chip at the screen edge ticking
    // behind the panel drags that rectangle across both — ~37 ms a repaint for a number the menu
    // is covering. The harness and the launcher have no such damage model, and the harness opens
    // the menu by default, so the hold is conditioned on the renderer.
    const apply = useVoidStore.getState().applyTick;
    useVoidStore.setState({ menuOpen: true, route: { name: 'mods' } });

    document.documentElement.setAttribute('data-renderer', 'ultralight');
    apply({ fps: 123 });
    expect(useVoidStore.getState().fps).not.toBe(123);

    // The HUD editor is the exception: there the widgets are the subject.
    useVoidStore.setState({ route: { name: 'hud-editor' } });
    apply({ fps: 123 });
    expect(useVoidStore.getState().fps).toBe(123);

    // And in the harness the values flow whatever the menu is doing.
    useVoidStore.setState({ route: { name: 'mods' }, fps: 0 });
    document.documentElement.setAttribute('data-renderer', 'webview');
    apply({ fps: 77 });
    expect(useVoidStore.getState().fps).toBe(77);

    document.documentElement.removeAttribute('data-renderer');
  });

  it('resets to Mods and closes the palette when the menu opens', () => {
    useVoidStore.setState({ route: { name: 'party' }, paletteOpen: true });
    useVoidStore.getState().applyMenu(true);
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    expect(useVoidStore.getState().paletteOpen).toBe(false);
    expect(useVoidStore.getState().menuOpen).toBe(true);
  });

});

describe('CPS derivation through the store', () => {
  it('counts one click per rising edge of lmb', () => {
    const apply = useVoidStore.getState().applyKeys;
    apply(keys({ lmb: 1 }));
    apply(keys({ lmb: 0 }));
    apply(keys({ lmb: 1 }));
    expect(useVoidStore.getState().cpsLeft).toBe(2);
  });

  it('does not count a held button', () => {
    const apply = useVoidStore.getState().applyKeys;
    apply(keys({ lmb: 1 }));
    apply(keys({ lmb: 1, w: 1 }));
    apply(keys({ lmb: 1, w: 0 }));
    expect(useVoidStore.getState().cpsLeft).toBe(1);
  });

  it('counts the two buttons separately', () => {
    const apply = useVoidStore.getState().applyKeys;
    apply(keys({ lmb: 1 }));
    apply(keys({ lmb: 0, rmb: 1 }));
    expect(useVoidStore.getState().cpsLeft).toBe(1);
    expect(useVoidStore.getState().cpsRight).toBe(1);
  });
});


describe('fights, derived from the counters the sensor already sends', () => {
  /**
   * Every assertion here drives `applyTick` with `hits` and a clock, because that is the whole
   * input: fight review adds nothing to the wire (`menu/ReviewScreen.tsx` says why), so the only
   * way it can be wrong is in this derivation.
   */
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-10T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('opens on the first hit in either direction and counts both', () => {
    const apply = useVoidStore.getState().applyTick;
    // The first `hits` establishes a baseline and counts nothing — the counters are
    // session-monotonic, so a page that joined mid-match would otherwise open a fight
    // containing the whole match.
    apply({ hits: { dealt: 12, taken: 4 } });
    expect(useVoidStore.getState().liveFight).toBeNull();

    apply({ hits: { dealt: 13, taken: 4 } });
    let fight = useVoidStore.getState().liveFight!;
    expect(fight).not.toBeNull();
    expect(fight.dealt).toBe(1);
    expect(fight.taken).toBe(0);

    // A hit taken belongs to the same fight, and does not open a second one.
    vi.advanceTimersByTime(1200);
    apply({ hits: { dealt: 13, taken: 5 } });
    fight = useVoidStore.getState().liveFight!;
    expect(fight.dealt).toBe(1);
    expect(fight.taken).toBe(1);
    expect(useVoidStore.getState().fights).toHaveLength(0);
  });

  it('counts a tick that carried two hits as two', () => {
    // The property `bridge.json` sends counters rather than events to get. A dropped tick makes
    // the next delta 2, and the fight is still exactly right — where a lost event would be wrong
    // for the rest of the session.
    const apply = useVoidStore.getState().applyTick;
    apply({ hits: { dealt: 0, taken: 0 } });
    apply({ hits: { dealt: 3, taken: 2 } });
    const fight = useVoidStore.getState().liveFight!;
    expect(fight.dealt).toBe(3);
    expect(fight.taken).toBe(2);
  });

  it('files the old fight and opens a new one after ten quiet seconds', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ hits: { dealt: 0, taken: 0 } });
    apply({ hits: { dealt: 4, taken: 1 } });
    const first = useVoidStore.getState().liveFight!;
    expect(useVoidStore.getState().fights).toHaveLength(0);

    // One millisecond inside the window is still the same fight — the boundary is not a range.
    vi.advanceTimersByTime(FIGHT_QUIET_MS);
    apply({ hits: { dealt: 5, taken: 1 } });
    expect(useVoidStore.getState().liveFight!.id).toBe(first.id);
    expect(useVoidStore.getState().liveFight!.dealt).toBe(5);

    vi.advanceTimersByTime(FIGHT_QUIET_MS + 1);
    apply({ hits: { dealt: 6, taken: 1 } });
    const second = useVoidStore.getState().liveFight!;
    expect(second.id).not.toBe(first.id);
    expect(second.dealt).toBe(1);
    // Newest first, so the screen reads top-down without reversing.
    expect(useVoidStore.getState().fights).toHaveLength(1);
    expect(useVoidStore.getState().fights[0]!.id).toBe(first.id);
    expect(useVoidStore.getState().fights[0]!.dealt).toBe(5);
  });

  it('carries sprint hits into the fight, and only the ones that landed', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ hits: { dealt: 0, taken: 0, sprint_dealt: 0 } });
    apply({ hits: { dealt: 2, taken: 0, sprint_dealt: 2 } });
    vi.advanceTimersByTime(800);
    apply({ hits: { dealt: 3, taken: 0, sprint_dealt: 2 } });
    const fight = useVoidStore.getState().liveFight!;
    expect(fight.dealt).toBe(3);
    expect(fight.sprintDealt).toBe(2);
  });

  it('takes the reach reading that stands, not only the ticks reach republished on', () => {
    // `reach` is value-checked on the wire: two hits at the same distance publish one update.
    // A fight that only recorded a reach on the ticks the field moved would under-count exactly
    // the player whose distance is consistent, which is the opposite of the truth.
    const apply = useVoidStore.getState().applyTick;
    apply({ hits: { dealt: 0, taken: 0 } });
    apply({ hits: { dealt: 1, taken: 0 }, reach: 3.1 });
    vi.advanceTimersByTime(700);
    apply({ hits: { dealt: 2, taken: 0 } });
    vi.advanceTimersByTime(700);
    apply({ hits: { dealt: 3, taken: 0 }, reach: 2.9 });
    expect(useVoidStore.getState().liveFight!.reaches).toEqual([3.1, 3.1, 2.9]);
  });

  it('records no reach for a hit that landed before the first reading', () => {
    // A reach of 0 is not a point-blank swing, it is a session in which nothing has been
    // measured — the distinction the wire itself draws. A zero here would drag the card's mean
    // down by a figure nobody swung.
    const apply = useVoidStore.getState().applyTick;
    apply({ hits: { dealt: 0, taken: 0 } });
    apply({ hits: { dealt: 1, taken: 0 } });
    expect(useVoidStore.getState().liveFight!.reaches).toEqual([]);
  });

  it('draws a timeline whose x-axis is time, gaps included', () => {
    const apply = useVoidStore.getState().applyTick;
    apply({ hits: { dealt: 0, taken: 0 } });
    apply({ hits: { dealt: 1, taken: 0 } });
    // Three seconds later, with nothing in between: the quiet seconds are the ones a player is
    // looking for, so they are drawn rather than closed up.
    vi.advanceTimersByTime(3000);
    apply({ hits: { dealt: 1, taken: 2 } });
    const beats = useVoidStore.getState().liveFight!.beats;
    expect(beats.map((b) => b.t)).toEqual([0, 1, 2, 3]);
    expect(beats[0]!.dealt).toBe(1);
    expect(beats[3]!.taken).toBe(2);
    expect(beats[1]).toEqual({ t: 1, dealt: 0, taken: 0, cps: 0 });
  });

  it('agrees with the screen about when a fight is over', () => {
    // One constant, asked the same question in two places. Two copies of a timeout is how a
    // fight that the store has filed goes on showing as live.
    const apply = useVoidStore.getState().applyTick;
    apply({ hits: { dealt: 0, taken: 0 } });
    apply({ hits: { dealt: 1, taken: 0 } });
    const fight = useVoidStore.getState().liveFight!;
    expect(fightIsOver(fight, fight.lastAt + FIGHT_QUIET_MS)).toBe(false);
    expect(fightIsOver(fight, fight.lastAt + FIGHT_QUIET_MS + 1)).toBe(true);
  });
});

describe('bridge calls', () => {
  it('routes a gameplay mod through setGameplay and stores what Java applied', () => {
    useVoidStore.getState().toggleMod('fullbright', true);
    expect(isModOn(useVoidStore.getState().loadout, 'fullbright')).toBe(true);
  });

  it('routes a HUD mod through setModSetting', () => {
    useVoidStore.getState().toggleMod('coordinates', true);
    expect(isModOn(useVoidStore.getState().loadout, 'coordinates')).toBe(true);
  });

  it('binds a setting to the value Java stored, not the one it sent', () => {
    // `scale` is bounded to [0.25, 4] by mods.json; the fake clamps like Java.
    useVoidStore.getState().setSetting('keystrokes', 'scale', 99);
    expect(modSettings(useVoidStore.getState().loadout, 'keystrokes').scale).toBe(4);
  });

  it('writes a HUD placement back through setHud and keeps what came back', () => {
    useVoidStore.getState().commitHud('keystrokes', 'bottom-right', -40, -40, 1.25);
    const item = hudItem(useVoidStore.getState().loadout, 'keystrokes')!;
    expect(item.anchor).toBe('bottom-right');
    expect(item.scale).toBe(1.25);
  });

  it('clamps an out-of-range placement before it reaches the bridge', () => {
    useVoidStore.getState().commitHud('fps', 'top-left', 99999, -99999, 99);
    const item = hudItem(useVoidStore.getState().loadout, 'fps')!;
    expect(item.dx).toBeLessThanOrEqual(4096);
    expect(item.dy).toBeGreaterThanOrEqual(-4096);
    expect(item.scale).toBeLessThanOrEqual(4);
  });

  it('resets a mod to its registry defaults without touching `on`', () => {
    const before = isModOn(useVoidStore.getState().loadout, 'keystrokes');
    useVoidStore.getState().setSetting('keystrokes', 'opacity', 0.2);
    useVoidStore.getState().resetMod('keystrokes');
    expect(modSettings(useVoidStore.getState().loadout, 'keystrokes').opacity).toBe(0.85);
    expect(isModOn(useVoidStore.getState().loadout, 'keystrokes')).toBe(before);
  });
});

/**
 * Layout, selection, and the mod page.
 *
 * This replaces a block that asserted the four states of `layout × inspector` and that
 * selecting a mod "never navigates". Both were true and both described the arrangement that
 * has just been removed — the properties panel beside the grid. What the old block was really
 * protecting survives here in a different shape: `layout` is still the player's own and still
 * survives a close, selecting is still not the same act as toggling, and the way back from a
 * mod is still exactly one step.
 */
describe('layout, selection and the mod page', () => {
  beforeEach(() => {
    useVoidStore.setState({
      layout: 'grid',
      route: { name: 'mods' },
      selectedMod: 'keystrokes',
    });
  });

  it('selecting moves the highlight and goes nowhere', () => {
    // The arrow keys call this on every step. It used to open the properties panel as a side
    // effect, which made walking the grid impossible without opening something.
    useVoidStore.getState().selectMod('fullbright');
    expect(useVoidStore.getState().selectedMod).toBe('fullbright');
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
  });

  it('opening a mod is one write: the page, and the tile you will come back to', () => {
    useVoidStore.getState().openMod('fullbright');
    expect(useVoidStore.getState().route).toEqual({ name: 'mod', id: 'fullbright' });
    expect(useVoidStore.getState().selectedMod).toBe('fullbright');
  });

  it('going back leaves the grid exactly as it was, selection included', () => {
    useVoidStore.getState().openMod('zoom');
    useVoidStore.getState().closeMod();
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    // Not reset: the tile you were just inside is the one that should be marked.
    expect(useVoidStore.getState().selectedMod).toBe('zoom');
  });

  it('opening a mod changes no loadout state', () => {
    const before = isModOn(useVoidStore.getState().loadout, 'fullbright');
    useVoidStore.getState().openMod('fullbright');
    expect(isModOn(useVoidStore.getState().loadout, 'fullbright')).toBe(before);
  });

  it('reopening the menu lands on the grid, never on the page you left from', () => {
    useVoidStore.getState().setLayout('list');
    useVoidStore.getState().openMod('cps');
    useVoidStore.getState().applyMenu(false);
    useVoidStore.getState().applyMenu(true);
    expect(useVoidStore.getState().route).toEqual({ name: 'mods' });
    // How the player likes to read the grid is theirs, and survives.
    expect(useVoidStore.getState().layout).toBe('list');
  });
});

/**
 * The session — who is playing.
 *
 * The one immutable thing on the bridge, and the one that has no sensor behind it: it arrives on
 * `pushWholeState()` and never again, so a page that dropped it would carry a blank chip for the
 * life of the process. That is the whole reason the guard below is a *value* comparison — a
 * reloaded document is sent the identical session again (§9a), and taking the fresh object would
 * re-render the bar for nothing.
 */
describe('session', () => {
  const notch = {
    name: 'Notch',
    uuid: '069a79f4-44e9-4726-a5be-fca90e38aaf5',
    kind: 'microsoft' as const,
  };

  it('is null until it is pushed, and then holds', () => {
    useVoidStore.setState({ session: null });
    expect(useVoidStore.getState().session).toBeNull();
    useVoidStore.getState().applySession(notch);
    expect(useVoidStore.getState().session).toEqual(notch);
  });

  it('ignores a re-push of the same session by value, not by identity', () => {
    useVoidStore.setState({ session: null });
    useVoidStore.getState().applySession(notch);
    const first = useVoidStore.getState().session;
    // The shape a reloaded document gets: same data, new object.
    useVoidStore.getState().applySession({ ...notch });
    expect(useVoidStore.getState().session).toBe(first);
  });

  it('takes a genuinely different session', () => {
    useVoidStore.setState({ session: null });
    useVoidStore.getState().applySession(notch);
    useVoidStore.getState().applySession({ ...notch, name: 'Dev', kind: 'offline' });
    expect(useVoidStore.getState().session?.name).toBe('Dev');
  });
});

describe('window.void', () => {
  it('is installed on the window', () => {
    expect(typeof window.void?.on).toBe('function');
  });

  it('__hasFocus is false with nothing focused', () => {
    expect(window.void!.__hasFocus()).toBe(false);
  });

  it('__hasFocus is true while a text input has focus', () => {
    const input = document.createElement('input');
    document.body.append(input);
    input.focus();
    expect(window.void!.__hasFocus()).toBe(true);
    input.remove();
  });

  it('__hasFocus stays false for a checkbox — Escape must still close', () => {
    const box = document.createElement('input');
    box.type = 'checkbox';
    document.body.append(box);
    box.focus();
    expect(window.void!.__hasFocus()).toBe(false);
    box.remove();
  });
});
