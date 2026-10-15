/**
 * The seven HUD mods of §3, bound to the store.
 *
 * Every widget here is a thin adapter: it reads the narrowest slice of state it
 * can and hands it to a Cartridge presentational component (`hud/cart/`). The
 * drawing and the Ultralight-safe treatment live there and in `cart/hud.css`;
 * what lives here is which bridge field feeds which prop, and which of the mod's
 * settings gates it.
 *
 * Cartridge, not `@void/ui`'s chips: the desktop launcher shares those and keeps
 * its look, so the in-game bundle draws its own (Figma section B, frames B10-B13).
 * The pure helpers (`formatPotionTime`, `formatAmplifier`, `yawIndex`, the crosshair)
 * are still `@void/ui`'s — a number has one formatter, whichever bundle draws it.
 *
 * `show_label` means one thing on every module: the label tab (`FPS`, `PING`…).
 * Units that are part of a reading (`ms`, `bps`, `MB`) stay with the figure.
 *
 * Each widget subscribes only to what it draws, so a `tick` push that changes
 * the ping does not re-render the FPS chip, and a `keys` push repaints one
 * keycap's class and nothing else.
 */

import { memo, useRef } from 'react';
import {
  CROSSHAIR_UNIT_PREVIEW,
  Crosshair,
  formatAmplifier,
  formatPotionTime,
  asCrosshairStyle,
  yawIndex,
  type DirectionStyle,
  type HudVariant,
} from '@/ui';
import { cardinalFromYaw, type ArmorSlot, type PotionEffect } from '@/bridge/protocol';
import { modSettings, useModSettings, useVoidStore } from '@/store/store';
import { clicksPerSecond, createClickRing, pushClick } from '@/store/cps';
import { armorRow, potionMeta, shortHost } from './format';
import { pingStats } from './ping-stats';
import {
  CartArmor,
  CartEffects,
  CartKeystrokes,
  HudLed,
  HudModule,
  Meter,
  cartKeysStyle,
  inkStyle,
  litFor,
  type CartEffectRow,
  type LedTone,
} from './cart';

export interface HudWidgetProps {
  /** `compact` in game, `editor` on the HUD-layout frame. */
  variant?: HudVariant;
  /**
   * Stand in for live data the widget has none of, rather than rendering nothing.
   *
   * **Only the mod page's preview passes this.** Three widgets draw a list off the `tick`
   * payload and return `null` when it is empty — no armour worn, no effects applied, no
   * position yet — which on the HUD is right (a widget with nothing to say says nothing) and
   * on a settings page is the §15 failure: an empty box where the mod should be, on a page
   * whose entire job is showing you the mod. Worse, the page is reachable from a lobby, so the
   * empty case is the *common* one there.
   *
   * The fixture is only ever a fallback. Real armour, real effects and a real position all win,
   * so a player standing in a match sees their own gear while they set the mod up.
   */
  sample?: boolean;
  /**
   * Extra class on the widget root. `HudLayer` types every widget as
   * `ComponentType<HudWidgetProps>` and places them all through one table, so a widget
   * that takes a prop outside this interface cannot go in that table.
   */
  className?: string;
}

/* -------------------------------------------------------------------- FPS */

/** The frames' own figure, and `TilePreview`'s. A preview with `0 fps` on it is not a preview. */
const SAMPLE_FPS = 142;

export const HudFps = memo(function HudFps({ sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.fps);
  const low = useVoidStore((s) => s.fpsLow);
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'fps').show_label !== false);
  // `fps.color` (mods.json), the one HUD readout the registry lets you tint. Selected as a
  // string so this subscription stays primitive; `#RRGGBB` and `#RRGGBBAA` are both valid CSS
  // colours, so the clamped value goes straight through with no parsing.
  const showLow = useVoidStore((s) => modSettings(s.loadout, 'fps').show_low !== false);
  const color = useVoidStore((s) => {
    const value = modSettings(s.loadout, 'fps').color;
    return typeof value === 'string' ? value : undefined;
  });
  // The chip reads `0 fps` until the first `tick` lands, and in game `applyTick` is held while
  // the menu is up — so a page opened before the HUD has ever drawn shows a zero, and `colour`
  // and `show_label` are being demonstrated on a figure that is not a figure.
  const fps = live > 0 || !sample ? live : SAMPLE_FPS;
  // Same for the aside: the 1% low window fills over several seconds of play and this page is
  // reachable from a lobby, so without a stand-in `show_low` would be a switch with nothing to
  // switch. Two thirds of the figure is roughly what a real 1% low sits at.
  const lowFigure = low > 0 ? low : sample ? Math.max(1, Math.round(fps * 0.68)) : 0;
  // The 1% low sits in the tab row, right, in the tab's own voice: it is the figure that gives
  // the reading a scale, not a second reading, and there it costs the 96px module no width.
  const showsLow = showLow && lowFigure > 0;
  return (
    <HudModule
      label={showLabel ? 'FPS' : undefined}
      head={showsLow ? <span className="ch-tab ch-tab--aside">1% {lowFigure}</span> : undefined}
      className="ch-fps"
    >
      <div className="ch-row">
        {/* `color` inks the figure only — the one live value on the module. */}
        <span className="ch-num" style={inkStyle(color)}>
          {fps}
        </span>
      </div>
    </HudModule>
  );
});

/* ------------------------------------------------------------------- ping */

/**
 * Readings for the ping preview's stats, as offsets from the band's middle chip.
 *
 * The stats asides need a history and a settings page has none — the server sends a latency every
 * few seconds and the page may have been open for less. So the fixture *is* a history, run
 * through the same `pingStats` the live chip uses, and shaped so each of the four new settings
 * has something to bite on:
 *
 *   · `window` — the last ten readings sit ~4 ms above the sixty, so the average moves between
 *     the two ends of the slider (3 and 60) rather than only its label;
 *   · `jitter_warn_ms` — consecutive readings swing by ~25 ms, above the factory 20 and far below
 *     the 500 ceiling, so the warn ink is on at the default and off at the far end;
 *   · `spike_ms` — the band's third chip (just past `bad_ms`) is judged as the newest reading
 *     against this history, ~85 ms over the last ten's mean: a spike at the factory 80, not at 1000.
 */
const SAMPLE_PING_OFFSETS: readonly number[] = Array.from({ length: 60 }, (_, i) => {
  const swing = i % 2 === 0 ? -12 : 13;
  const drift = i >= 50 ? 5 : 0;
  return swing + drift + ((i * 7) % 5) - 2;
});

/** Settings of the ping mod the stats asides read, as primitives. */
function usePingStatsSettings() {
  const showAverage = useVoidStore((s) => modSettings(s.loadout, 'ping').show_average === true);
  const showJitter = useVoidStore((s) => modSettings(s.loadout, 'ping').show_jitter === true);
  const jitterWarn = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').jitter_warn_ms ?? 20));
  const spikeMs = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').spike_ms ?? 80));
  const window = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').window ?? 10));
  return { showAverage, showJitter, jitterWarn, spikeMs, window };
}

/** Props for one ping module — everything `PingChip` took, in Cartridge. */
interface PingModuleProps {
  /** Round-trip ms; -1 is "no server" and draws an em-dash. */
  ping: number;
  goodMs: number;
  badMs: number;
  showLabel: boolean;
  host?: string;
  average?: number;
  jitter?: number;
  jitterWarnMs: number;
  /** `brief` fades itself out (stepped, CSS); `held` is the preview's form. */
  spike?: 'brief' | 'held';
  spikeKey?: string | number;
}

/**
 * `PING  HYPIXEL ●` over `22 ms`. The LED carries the threshold band — mint inside `good_ms`,
 * amber between, red at `bad_ms` — so the figure stays the ink every other numeral is in.
 * Three bands, three tones: two of them sharing a colour is how `bad_ms` once moved nothing.
 */
function PingModule({
  ping,
  goodMs,
  badMs,
  showLabel,
  host,
  average,
  jitter,
  jitterWarnMs,
  spike,
  spikeKey,
}: PingModuleProps) {
  const unknown = ping < 0;
  const tone: LedTone = unknown ? 'off' : ping >= badMs ? 'bad' : ping <= goodMs ? 'ok' : 'warn';
  return (
    <HudModule
      label={showLabel ? 'PING' : undefined}
      className="ch-ping"
      head={
        <>
          {host ? <span className="ch-tab ch-tab--aside">{host}</span> : null}
          <HudLed tone={tone} />
        </>
      }
    >
      <div className="ch-row">
        <span className="ch-num">{unknown ? '—' : ping}</span>
        {unknown ? null : <span className="ch-unit">ms</span>}
        {spike ? (
          <span
            key={spikeKey}
            className={spike === 'held' ? 'ch-spike ch-spike--held' : 'ch-spike'}
            aria-label="spike"
          />
        ) : null}
        {average === undefined ? null : (
          <span className="ch-aside">avg {Math.round(average)}</span>
        )}
        {jitter === undefined ? null : (
          <span
            className={
              jitterWarnMs > 0 && jitter >= jitterWarnMs ? 'ch-aside ch-aside--warn' : 'ch-aside'
            }
          >
            ±{Math.round(jitter)}
          </span>
        )}
      </div>
    </HudModule>
  );
}

export const HudPing = memo(function HudPing({ sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.ping);
  const liveHost = useVoidStore((s) => s.server.host);
  const showHost = useVoidStore((s) => modSettings(s.loadout, 'ping').show_host !== false);
  const good = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').good_ms ?? 60));
  const bad = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').bad_ms ?? 150));
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'ping').show_label !== false);
  const history = useVoidStore((s) => s.pingHistory);
  const { showAverage, showJitter, jitterWarn, spikeMs, window } = usePingStatsSettings();
  const host = liveHost ?? (sample ? 'mc.hypixel.net' : null);
  const chip = (value: number, extra: Partial<PingModuleProps> = {}, key?: number) => (
    <PingModule
      key={key}
      ping={value}
      goodMs={good}
      badMs={bad}
      showLabel={showLabel}
      host={showHost && host ? shortHost(host).toUpperCase() : undefined}
      jitterWarnMs={jitterWarn}
      {...extra}
    />
  );

  // **The preview draws the chip three times, and that is not a licence — it is the only
  // honest picture of a threshold.** `good_ms` and `bad_ms` decide where one figure changes
  // colour, and a single figure cannot show you where: at 42 ms the chip is green and stays
  // green however far you drag "Good under", so both controls read as dead. Three pings taken
  // *from the thresholds themselves* — just inside good, halfway between, just past bad — put
  // one chip in each band, so dragging either control visibly moves the boundary it owns.
  //
  // The same argument as `SAMPLE_ARMOR`'s four different durabilities, and the same shape: the
  // fixture is chosen so the setting has something to bite on. In game exactly one chip is ever
  // drawn, and it is this component.
  //
  // The stats asides go on the **middle** chip, the typical reading, computed from a fixture
  // history around it (`SAMPLE_PING_OFFSETS`); the spike mark goes on the **third**, judged as
  // the newest reading against that same history — a jump past `bad_ms` is what a spike looks
  // like. Held rather than faded, because a mark that disappeared while you looked at it would be
  // a setting with no visible effect.
  if (sample) {
    const middle = Math.max(good + 1, Math.round((good + bad) / 2));
    const band = [Math.max(1, good - 12), middle, Math.min(9999, bad + 45)];
    const past = SAMPLE_PING_OFFSETS.map((offset) => Math.max(0, middle + offset));
    const stats = pingStats(past, window, spikeMs);
    const spiked = pingStats([...past, band[2]!], window, spikeMs);
    return (
      <div className="ch-band">
        {chip(band[0]!, {}, 0)}
        {chip(
          band[1]!,
          {
            average: showAverage && stats ? stats.average : undefined,
            jitter: showJitter && stats ? stats.jitter : undefined,
          },
          1,
        )}
        {chip(band[2]!, { spike: spiked?.spike ? 'held' : undefined }, 2)}
      </div>
    );
  }
  // The bridge sends -1 for "no server", which is not a ping and draws as an em-dash. The
  // asides need a reading to describe, so they wait for one too.
  const stats = live >= 0 ? pingStats(history, window, spikeMs) : null;
  return chip(live, {
    average: showAverage && stats ? stats.average : undefined,
    jitter: showJitter && stats && stats.count > 1 ? stats.jitter : undefined,
    // `brief`: the mark fades itself out (CSS), and is keyed by the history's length and newest
    // reading so the next spike remounts it and replays the fade. No timer.
    spike: stats?.spike ? 'brief' : undefined,
    spikeKey: `${history.length}:${history[history.length - 1] ?? ''}`,
  });
});

/* ----------------------------------------------------------------- coords */

/** Somewhere in a world, for a preview opened before the first `tick` lands. */
const SAMPLE_POS = { x: 128.4, y: 64, z: -302.7, yaw: 45 };

export const HudCoordinates = memo(function HudCoordinates({ sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.pos);
  const settings = useModSettings('coordinates');
  const pos = live ?? (sample ? SAMPLE_POS : null);
  if (!pos) return null;
  const decimals = Math.min(2, Math.max(0, Math.round(Number(settings.decimals ?? 0)))) || 0;
  const stacked = settings.layout === 'stacked';
  // `color` inks the three readings and the cardinal — the live values — never the axis letters.
  const ink = inkStyle(typeof settings.color === 'string' ? settings.color : undefined);
  const facing = settings.show_direction === false ? null : cardinalFromYaw(pos.yaw);
  const axis = (letter: string, value: number) => (
    <span className="ch-axis" key={letter}>
      <span className="ch-axis__k">{letter}</span>
      <span className="ch-num ch-num--md" style={ink}>
        {value.toFixed(decimals)}
      </span>
    </span>
  );
  // Stacked, one axis per line: the three figures share an x-position, so a digit changing is a
  // digit you can see change. The facing moves to the tab row there, where a line of its own
  // would be a fourth row for two letters.
  return (
    <HudModule
      label="XYZ"
      className={stacked ? 'ch-coords ch-coords--stacked' : 'ch-coords'}
      head={
        stacked && facing ? (
          <span className="ch-tab ch-tab--live" style={ink}>
            {facing}
          </span>
        ) : undefined
      }
    >
      <div className={stacked ? 'ch-coords__axes ch-coords__axes--stacked' : 'ch-coords__axes'}>
        {axis('X', pos.x)}
        {axis('Y', pos.y)}
        {axis('Z', pos.z)}
        {!stacked && facing ? (
          <span className="ch-num ch-num--md ch-coords__facing" style={ink}>
            {facing}
          </span>
        ) : null}
      </div>
    </HudModule>
  );
});

/* -------------------------------------------------------------- direction */

/**
 * Which way you are facing, as its own placeable chip.
 *
 * Reads the same `pos.yaw` Coordinates already reads, so this mod cost no sensor work — the
 * reason it was the first of Wave 2 to ship. `coordinates.show_direction` still exists and is
 * the *inline* form; this is the standalone one. Neither reads the other's settings.
 */
const COMPASS_WORDS: Record<string, string> = {
  S: 'South', SW: 'Southwest', W: 'West', NW: 'Northwest',
  N: 'North', NE: 'Northeast', E: 'East', SE: 'Southeast',
};

/** South is +Z and west is +X in 1.8.9; the diagonals name both axes. Same table as `@void/ui`. */
const COMPASS_AXES: Record<string, string> = {
  S: '+Z', SW: '+X +Z', W: '+X', NW: '+X −Z',
  N: '−Z', NE: '−X −Z', E: '−X', SE: '−X +Z',
};

export const HudDirection = memo(function HudDirection({ sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.pos);
  const settings = useModSettings('direction');
  const pos = live ?? (sample ? SAMPLE_POS : null);
  if (!pos) return null;
  const notation = asDirectionStyle(settings.style);
  const point = yawIndex(pos.yaw);
  const facing =
    notation === 'word' ? COMPASS_WORDS[point] : notation === 'axis' ? COMPASS_AXES[point] : point;
  // Yaw runs unbounded in game; a module printing `-1043°` is not a reading.
  const degrees = Math.round(((pos.yaw % 360) + 360) % 360);
  return (
    <HudModule label="FACING" className="ch-dir">
      <div className="ch-row">
        <span className="ch-num" style={inkStyle(typeof settings.color === 'string' ? settings.color : undefined)}>
          {facing}
        </span>
        {settings.show_degrees === true ? <span className="ch-unit">{degrees}°</span> : null}
      </div>
    </HudModule>
  );
});

/** A stored `style` narrowed to what the chip draws; anything else is the factory default. */
function asDirectionStyle(value: unknown): DirectionStyle {
  return value === 'word' || value === 'axis' ? value : 'letter';
}

/* ---------------------------------------------------------------- potions */

/**
 * Three effects, for a preview opened with none applied.
 *
 * One of them is `ambient`, which is the only way `hide_ambient` can be seen to do anything:
 * a fixture of non-ambient rows would leave that switch inert on the page for exactly the
 * reason the real data does. The third, Haste II (short enough for the 150px list), has
 * **13 seconds** left, which is what makes
 * `blink_below_s` visible: above the factory 5, so it rests, and inside the 30 ceiling, so the far
 * end of the slider puts it on its dim beat — 13 is odd, and odd seconds are the dim half.
 */
const SAMPLE_FX = [
  { id: 1, amplifier: 1, duration_ms: 84_000, ambient: false, name: 'potion.moveSpeed' },
  { id: 5, amplifier: 0, duration_ms: 42_000, ambient: true, name: 'potion.damageBoost' },
  { id: 3, amplifier: 1, duration_ms: 13_000, ambient: false, name: 'potion.digSpeed' },
];

/** Whether vanilla effect `id` is in a `hidden_effects` mask (bit n = status effect id n). */
export function effectHidden(mask: number, id: number): boolean {
  if (!Number.isFinite(mask) || mask <= 0 || id < 0 || id > 30) return false;
  return Math.floor(mask / 2 ** id) % 2 === 1;
}

/**
 * Whether an effect is on its dim beat — `blink_below_s`.
 *
 * Driven by the tick data and nothing else: the parity of the remaining **whole** seconds, at the
 * same rounding `formatPotionTime` prints and the store's `sameEffects` re-publishes on. So the row
 * flips exactly when its timer redraws — once a second — and there is no timer of its own. Odd
 * seconds are dim; 0 left is not a blink, it is an effect about to vanish.
 */
export function effectBlinks(durationMs: number, belowS: number): boolean {
  if (belowS <= 0) return false;
  const seconds = Math.max(0, Math.round(durationMs / 1000));
  return seconds > 0 && seconds <= belowS && seconds % 2 === 1;
}

/**
 * Full durations for the fixture, so its three timers sit at three different fill levels: Speed
 * II drunk from a 3:00 bottle, Strength from a beacon's minute, Haste on its last quarter.
 */
const SAMPLE_FULL_MS: Readonly<Record<number, number>> = { 1: 180_000, 5: 60_000, 3: 45_000 };

/**
 * The longest duration seen per effect id while it stays applied — the timer's "full".
 *
 * The tick sends time left, never the potion's original length, and that length varies by the
 * bottle (Speed is 3:00, 8:00 extended, 1:30 at II). So the widget remembers: the first reading
 * of an effect is its full bar, a re-drink that raises it raises the full, and an effect that
 * drops out is forgotten so the next one starts full again. Per widget instance (a ref, not
 * module state), so the mod page's fixture cannot teach the HUD a wrong length.
 */
function useFullDurations(fx: readonly PotionEffect[], fixture: boolean): (id: number, ms: number) => number {
  const seen = useRef(new Map<number, number>());
  if (fixture) return (id, ms) => SAMPLE_FULL_MS[id] ?? ms;
  const map = seen.current;
  const present = new Set<number>();
  for (const effect of fx) {
    present.add(effect.id);
    const prev = map.get(effect.id) ?? 0;
    if (effect.duration_ms > prev) map.set(effect.id, effect.duration_ms);
  }
  for (const id of [...map.keys()]) if (!present.has(id)) map.delete(id);
  return (id, ms) => Math.max(ms, map.get(id) ?? ms);
}

export const HudPotionEffects = memo(function HudPotionEffects({ sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.fx);
  const settings = useModSettings('potion_effects');
  const fixture = live.length === 0 && sample === true;
  const fx = fixture ? (SAMPLE_FX as unknown as typeof live) : live;
  const fullOf = useFullDurations(fx, fixture);
  const hidden = Number(settings.hidden_effects ?? 0);
  const blinkBelow = Number(settings.blink_below_s ?? 5);
  const visible = fx.filter(
    (f) => !(settings.hide_ambient && f.ambient) && !effectHidden(hidden, f.id),
  );
  if (visible.length === 0) return null;
  const rows: CartEffectRow[] = visible.map((effect) => {
    const meta = potionMeta(effect);
    const level = settings.show_amplifier === false ? '' : formatAmplifier(effect.amplifier);
    const seconds = Math.max(0, Math.round(effect.duration_ms / 1000));
    const full = fullOf(effect.id, effect.duration_ms);
    return {
      id: effect.id,
      color: meta.color,
      name: level ? `${meta.label} ${level}` : meta.label,
      time: settings.show_duration === false ? undefined : formatPotionTime(effect.duration_ms),
      fraction: full > 0 ? effect.duration_ms / full : 0,
      // Inside the blink window the timer turns amber for the whole of it; the row dims on the
      // odd seconds only — the blink itself.
      ending: blinkBelow > 0 && seconds > 0 && seconds <= blinkBelow,
      dim: effectBlinks(effect.duration_ms, blinkBelow),
    };
  });
  return <CartEffects rows={rows} />;
});

/* ------------------------------------------------------------------ armor */

/**
 * A full set plus a held item, for a preview opened by a player wearing nothing.
 *
 * Damaged rather than pristine on purpose: `warn_below` moves the amber threshold, and against
 * four full bars nothing about that setting is visible. These four sit at 96%, 61%, 43% and 12%
 * of their maxima, so dragging the threshold across the range lights each bar in turn.
 */
const SAMPLE_ARMOR: ArmorSlot[] = [
  { slot: 'helmet', item: 'diamond_helmet', damage: 14, max_damage: 363 },
  { slot: 'chestplate', item: 'diamond_chestplate', damage: 205, max_damage: 528 },
  { slot: 'leggings', item: 'diamond_leggings', damage: 282, max_damage: 495 },
  { slot: 'boots', item: 'diamond_boots', damage: 377, max_damage: 429 },
  { slot: 'held', item: 'diamond_sword', damage: 402, max_damage: 1561 },
];

export const HudArmorStatus = memo(function HudArmorStatus({ sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.armor);
  const settings = useModSettings('armor_status');
  const worn = live.some((slot) => slot.item !== null) || !sample ? live : SAMPLE_ARMOR;
  const rows = worn
    .filter((slot) => settings.show_held_item !== false || slot.slot !== 'held')
    .map((slot) => {
      const row = armorRow(slot);
      return row ? { ...row, item: slot.item } : null;
    })
    .filter((row): row is NonNullable<typeof row> => row !== null);
  if (rows.length === 0) return null;
  return (
    <CartArmor
      rows={rows}
      orientation={settings.orientation === 'vertical' ? 'vertical' : 'horizontal'}
      showDurability={settings.show_durability !== false}
      warnBelow={Number(settings.warn_below ?? 0.5)}
    />
  );
});

/* ------------------------------------------------------------- keystrokes */

export const HudKeystrokes = memo(function HudKeystrokes({ className }: HudWidgetProps) {
  const keys = useVoidStore((s) => s.keys);
  const settings = useModSettings('keystrokes');
  const cpsLeft = useVoidStore((s) => s.cpsLeft);
  const cpsRight = useVoidStore((s) => s.cpsRight);
  // `corner_radius`, `key_color` and `pressed_color` all override a custom property the keycap
  // rules already read (`cart/hud.css`), rather than three bespoke style hooks; the name-to-
  // colour table is `CART_KEY_COLORS`, the one place both the widget and a swatch can read.
  // `animate` + `fade_ms`: how long a released cap takes to return to rest, in three steps. The
  // press is always on the frame; only the release is shaped. Off is a snap both ways.
  const style = cartKeysStyle({
    keyColor: typeof settings.key_color === 'string' ? settings.key_color : null,
    pressedColor: typeof settings.pressed_color === 'string' ? settings.pressed_color : null,
    radius: typeof settings.corner_radius === 'number' ? settings.corner_radius : null,
    fadeMs: settings.animate === false ? 0 : Number(settings.fade_ms ?? 120),
  });
  return (
    <CartKeystrokes
      className={className}
      style={style}
      keys={{
        w: keys.w === 1,
        a: keys.a === 1,
        s: keys.s === 1,
        d: keys.d === 1,
        lmb: keys.lmb === 1,
        rmb: keys.rmb === 1,
        space: keys.space === 1,
        shift: keys.shift === 1,
      }}
      showMouse={settings.show_mouse !== false}
      showSpacebar={settings.show_spacebar === true}
      showSneak={settings.show_sneak === true}
      cps={settings.show_cps === true ? { left: cpsLeft, right: cpsRight } : undefined}
    />
  );
});

/* -------------------------------------------------------------------- CPS */

/**
 * A plausible burst of clicks, as ages in ms before "now".
 *
 * **Bursty on purpose, and that is the whole point.** A uniform click stream reads the same at
 * every window length — correctly, because the window changes how the rate is *measured*, not
 * what it is — so a tidy fixture would have left `window_ms` looking dead in the preview when
 * it is not. People do not click uniformly: they burst during a fight and drift between them,
 * and the setting is precisely the choice between tracking the burst and averaging it away.
 *
 * Over 200 ms this reads ~15 CPS, over a second ~8, over five seconds ~2. That spread is the
 * trade the player is actually making, and it is visible the moment the meter moves.
 */
const SAMPLE_CLICKS_LEFT = [30, 90, 150, 210, 280, 350, 620, 900, 1400, 2100, 3000, 4200];

/**
 * Which of the left clicks above the game swallowed — `cps.ignore_cancelled`'s fixture.
 *
 * A burst is exactly where 1.8 cancels clicks: a whiff starts the 10-tick miss delay, and every
 * press inside it reaches the button but never reaches `doAttack`. Two of the burst's six land in
 * one, so with the setting on the figure drops by what a real burst loses, through the same
 * `clicksPerSecond` — counting swings rather than presses, as the store does from `hits.swings`.
 */
const SAMPLE_CANCELLED_LEFT: ReadonlySet<number> = new Set([90, 210]);
const SAMPLE_CLICKS_RIGHT = [120, 400, 1300, 2600, 4100];

/** A fixed instant, so the fixture never depends on the clock and never repaints on a timer. */
const SAMPLE_NOW = 100_000;

function sampleRate(ages: readonly number[], windowMs: number): number {
  const ring = createClickRing();
  for (const age of [...ages].reverse()) pushClick(ring, SAMPLE_NOW - age);
  return clicksPerSecond(ring, SAMPLE_NOW, windowMs);
}

export const HudCps = memo(function HudCps({ sample }: HudWidgetProps) {
  const liveLeft = useVoidStore((s) => s.cpsLeft);
  const liveRight = useVoidStore((s) => s.cpsRight);
  const mode = useVoidStore((s) => String(modSettings(s.loadout, 'cps').mode ?? 'left'));
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'cps').show_label !== false);
  const window = useVoidStore((s) => Number(modSettings(s.loadout, 'cps').window_ms ?? 1000));
  // Nobody is clicking while the settings page is open, so both rings read zero: `mode` would
  // switch between three zeroes and `window_ms` would have nothing to measure. The fixture is
  // run through the real `clicksPerSecond`, so what the preview shows is what that click
  // pattern would actually produce at this window — not an illustration of it.
  const ignoreCancelled = useVoidStore(
    (s) => modSettings(s.loadout, 'cps').ignore_cancelled === true,
  );
  const idle = liveLeft === 0 && liveRight === 0;
  const sampleLeft = ignoreCancelled
    ? SAMPLE_CLICKS_LEFT.filter((age) => !SAMPLE_CANCELLED_LEFT.has(age))
    : SAMPLE_CLICKS_LEFT;
  const left = idle && sample ? sampleRate(sampleLeft, window) : liveLeft;
  const right = idle && sample ? sampleRate(SAMPLE_CLICKS_RIGHT, window) : liveRight;
  const showPeak = useVoidStore((s) => modSettings(s.loadout, 'cps').show_peak === true);
  const livePeak = useVoidStore((s) => s.cpsPeak);
  // The same stand-in argument as the two figures above, one line down: on the settings page
  // nobody has clicked, so the session peak is zero and the switch would turn a `· peak 0` on
  // and off. A peak is by definition at or above the live figure, so the fixture's is the
  // higher of the two hands with a little headroom — which is what a real one looks like.
  const peak = idle && sample ? Math.round(Math.max(left, right) * 1.35 * 10) / 10 : livePeak;
  const which = mode === 'right' ? 'right' : mode === 'both' ? 'both' : 'left';
  const main = which === 'right' ? right : left;
  return (
    <HudModule
      label={showLabel ? 'CPS' : undefined}
      head={showPeak ? <span className="ch-tab ch-tab--aside">peak {peak}</span> : undefined}
      className="ch-cps"
    >
      <div className="ch-row">
        <span className="ch-num">{main}</span>
        {/* `both`: the right hand small beside the left, as the frame draws `11 4`. */}
        {which === 'both' ? <span className="ch-num ch-num--sub">{right}</span> : null}
        {/* Six steps in the HUD hue, 2.5 CPS each — the one live mark on the module in colour. */}
        <Meter
          n={6}
          on={litFor(Math.min(main, CPS_METER_MAX) / CPS_METER_MAX, 6)}
          w={4}
          h={16}
          gap={2}
          color="var(--c-hue-hud)"
          className="ch-cps__meter"
        />
      </div>
    </HudModule>
  );
});

/** Where the CPS meter tops out: 15 is a hard butterfly; past it the meter is simply full. */
const CPS_METER_MAX = 15;

/* ------------------------------------------------------------- crosshair */

/**
 * The crosshair is GL, not HTML (§3 — it must sit at the exact pixel centre and is 20 lines of
 * code). This is the stand-in: the `?debug` view draws it so the harness matches the frames,
 * and the mod page draws it because a GL mark is a mark the settings panel cannot otherwise
 * show you.
 *
 * It reads all eight settings. It used to read none — `Crosshair` took no props at all — so the
 * harness and the HUD editor drew one fixed plus whatever the loadout said, and the page that
 * configures it drew a bitmap of a plus that was not even the same shape. Every setting was
 * live in GL and none of them was visible anywhere a player could see it while changing it.
 */
export const HudCrosshair = memo(function HudCrosshair({ sample }: HudWidgetProps) {
  const settings = useModSettings('crosshair');
  const color = typeof settings.color === 'string' ? settings.color : undefined;
  return (
    <Crosshair
      shape={asCrosshairStyle(settings.style)}
      size={Number(settings.size ?? 5)}
      thickness={Number(settings.thickness ?? 1)}
      gap={Number(settings.gap ?? 2)}
      color={color}
      outline={settings.outline !== false}
      dynamic={settings.dynamic === true}
      centerDot={settings.center_dot === true}
      // `sample` is the preview, and the preview is the one place `dynamic` can be seen at
      // all: it only bites while sprinting, which is not something you do from a settings page.
      showSpread={sample === true}
      unit={sample ? CROSSHAIR_UNIT_PREVIEW : 1}
    />
  );
});

