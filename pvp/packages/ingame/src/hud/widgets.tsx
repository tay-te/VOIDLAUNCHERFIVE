/**
 * The seven HUD mods of §3, bound to the store.
 *
 * Every widget here is a thin adapter: it reads the narrowest slice of state it
 * can and hands it to the corresponding presentational component in `@void/ui`.
 * The drawing, the geometry and the Ultralight-safe treatment all live there;
 * what lives here is which bridge field feeds which prop, and which of the mod's
 * settings gates it.
 *
 * Each widget subscribes only to what it draws, so a `tick` push that changes
 * the ping does not re-render the FPS chip, and a `keys` push repaints one
 * keycap's class and nothing else.
 */

import { memo } from 'react';
import {
  ArmorList,
  CROSSHAIR_UNIT_PREVIEW,
  CoordsChip,
  CpsChip,
  Crosshair,
  FpsChip,
  KeystrokesWidget,
  PingChip,
  PotionList,
  formatAmplifier,
  formatPotionTime,
  asCrosshairStyle,
  keystrokesColorStyle,
  DirectionChip,
  type DirectionStyle,
  type HudVariant,
} from '@/ui';
import { cardinalFromYaw, type ArmorSlot } from '@/bridge/protocol';
import { modSettings, useModSettings, useVoidStore } from '@/store/store';
import { clicksPerSecond, createClickRing, pushClick } from '@/store/cps';
import { armorRow, potionMeta, shortHost } from './format';

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

export const HudFps = memo(function HudFps({ variant, sample }: HudWidgetProps) {
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
  return (
    <FpsChip
      variant={variant}
      fps={fps}
      showLabel={showLabel}
      color={color}
      onePercentLow={showLow && lowFigure > 0 ? lowFigure : undefined}
    />
  );
});

/* ------------------------------------------------------------------- ping */

export const HudPing = memo(function HudPing({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.ping);
  const liveHost = useVoidStore((s) => s.server.host);
  const showHost = useVoidStore((s) => modSettings(s.loadout, 'ping').show_host !== false);
  const good = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').good_ms ?? 60));
  const bad = useVoidStore((s) => Number(modSettings(s.loadout, 'ping').bad_ms ?? 150));
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'ping').show_label !== false);
  const host = liveHost ?? (sample ? 'mc.hypixel.net' : null);
  const chip = (value: number, key?: number) => (
    <PingChip
      key={key}
      variant={variant}
      ping={value}
      goodMs={good}
      badMs={bad}
      showLabel={showLabel}
      host={showHost && host ? shortHost(host) : undefined}
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
  if (sample) {
    const band = [
      Math.max(1, good - 12),
      Math.max(good + 1, Math.round((good + bad) / 2)),
      Math.min(9999, bad + 45),
    ];
    return <div className="v-pingband">{band.map((value, i) => chip(value, i))}</div>;
  }
  // The bridge sends -1 for "no server", which is not a ping and draws as an em-dash.
  return chip(live);
});

/* ----------------------------------------------------------------- coords */

/** Somewhere in a world, for a preview opened before the first `tick` lands. */
const SAMPLE_POS = { x: 128.4, y: 64, z: -302.7, yaw: 45 };

export const HudCoordinates = memo(function HudCoordinates({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.pos);
  const settings = useModSettings('coordinates');
  const pos = live ?? (sample ? SAMPLE_POS : null);
  if (!pos) return null;
  return (
    <CoordsChip
      variant={variant}
      x={pos.x}
      y={pos.y}
      z={pos.z}
      decimals={Number(settings.decimals ?? 0)}
      layout={settings.layout === 'stacked' ? 'stacked' : 'inline'}
      color={typeof settings.color === 'string' ? settings.color : undefined}
      direction={settings.show_direction === false ? undefined : cardinalFromYaw(pos.yaw)}
    />
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
export const HudDirection = memo(function HudDirection({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.pos);
  const settings = useModSettings('direction');
  const pos = live ?? (sample ? SAMPLE_POS : null);
  if (!pos) return null;
  return (
    <DirectionChip
      variant={variant}
      yaw={pos.yaw}
      notation={asDirectionStyle(settings.style)}
      showDegrees={settings.show_degrees === true}
      color={typeof settings.color === 'string' ? settings.color : undefined}
    />
  );
});

/** A stored `style` narrowed to what the chip draws; anything else is the factory default. */
function asDirectionStyle(value: unknown): DirectionStyle {
  return value === 'word' || value === 'axis' ? value : 'letter';
}

/* ---------------------------------------------------------------- potions */

/**
 * Two effects, for a preview opened with none applied.
 *
 * One of them is `ambient`, which is the only way `hide_ambient` can be seen to do anything:
 * a fixture of two non-ambient rows would leave that switch inert on the page for exactly the
 * reason the real data does.
 */
const SAMPLE_FX = [
  { id: 1, amplifier: 1, duration_ms: 84_000, ambient: false, name: 'potion.moveSpeed' },
  { id: 5, amplifier: 0, duration_ms: 42_000, ambient: true, name: 'potion.damageBoost' },
];

export const HudPotionEffects = memo(function HudPotionEffects({ sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.fx);
  const settings = useModSettings('potion_effects');
  const fx = live.length > 0 || !sample ? live : (SAMPLE_FX as unknown as typeof live);
  const visible = settings.hide_ambient ? fx.filter((f) => !f.ambient) : fx;
  if (visible.length === 0) return null;
  return (
    <PotionList
      effects={visible.map((effect) => {
        const meta = potionMeta(effect);
        const level = settings.show_amplifier === false ? '' : formatAmplifier(effect.amplifier);
        return {
          color: meta.color,
          name: level ? `${meta.label} ${level}` : meta.label,
          time:
            settings.show_duration === false ? undefined : formatPotionTime(effect.duration_ms),
        };
      })}
    />
  );
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
    .map(armorRow)
    .filter((row): row is NonNullable<typeof row> => row !== null);
  if (rows.length === 0) return null;
  return (
    <ArmorList
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
  const radius = settings.corner_radius;
  // `corner_radius`, `key_color` and `pressed_color` are all applied the same way: by
  // overriding the token the keycap rules already read, rather than by three bespoke style
  // hooks. `keystrokesColorStyle` owns the name-to-token resolution so the settings swatches
  // and the widget cannot show different colours for the same choice.
  const style: React.CSSProperties = {
    ...keystrokesColorStyle(
      typeof settings.key_color === 'string' ? settings.key_color : null,
      typeof settings.pressed_color === 'string' ? settings.pressed_color : null,
    ),
    ...(typeof radius === 'number'
      ? ({ ['--radius-control' as string]: `${radius}px` } as React.CSSProperties)
      : null),
  };
  return (
    <KeystrokesWidget
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
const SAMPLE_CLICKS_RIGHT = [120, 400, 1300, 2600, 4100];

/** A fixed instant, so the fixture never depends on the clock and never repaints on a timer. */
const SAMPLE_NOW = 100_000;

function sampleRate(ages: readonly number[], windowMs: number): number {
  const ring = createClickRing();
  for (const age of [...ages].reverse()) pushClick(ring, SAMPLE_NOW - age);
  return clicksPerSecond(ring, SAMPLE_NOW, windowMs);
}

export const HudCps = memo(function HudCps({ variant, sample }: HudWidgetProps) {
  const liveLeft = useVoidStore((s) => s.cpsLeft);
  const liveRight = useVoidStore((s) => s.cpsRight);
  const mode = useVoidStore((s) => String(modSettings(s.loadout, 'cps').mode ?? 'left'));
  const showLabel = useVoidStore((s) => modSettings(s.loadout, 'cps').show_label !== false);
  const window = useVoidStore((s) => Number(modSettings(s.loadout, 'cps').window_ms ?? 1000));
  // Nobody is clicking while the settings page is open, so both rings read zero: `mode` would
  // switch between three zeroes and `window_ms` would have nothing to measure. The fixture is
  // run through the real `clicksPerSecond`, so what the preview shows is what that click
  // pattern would actually produce at this window — not an illustration of it.
  const idle = liveLeft === 0 && liveRight === 0;
  const left = idle && sample ? sampleRate(SAMPLE_CLICKS_LEFT, window) : liveLeft;
  const right = idle && sample ? sampleRate(SAMPLE_CLICKS_RIGHT, window) : liveRight;
  const showPeak = useVoidStore((s) => modSettings(s.loadout, 'cps').show_peak === true);
  const livePeak = useVoidStore((s) => s.cpsPeak);
  // The same stand-in argument as the two figures above, one line down: on the settings page
  // nobody has clicked, so the session peak is zero and the switch would turn a `· peak 0` on
  // and off. A peak is by definition at or above the live figure, so the fixture's is the
  // higher of the two hands with a little headroom — which is what a real one looks like.
  const peak = idle && sample ? Math.round(Math.max(left, right) * 1.35 * 10) / 10 : livePeak;
  return (
    <CpsChip
      variant={variant}
      left={left}
      right={right}
      showLabel={showLabel}
      peak={showPeak ? peak : undefined}
      mode={mode === 'right' ? 'right' : mode === 'both' ? 'both' : 'left'}
    />
  );
});

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

