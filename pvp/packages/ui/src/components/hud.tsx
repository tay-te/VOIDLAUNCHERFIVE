import type { HTMLAttributes, ReactNode } from 'react';

import { StatusDot } from './primitives.js';
import {
  VANILLA,
  crosshairRects,
  dynamicSpread,
  isRing,
  keepsVanilla,
  type CrosshairRect,
  type CrosshairStyle,
} from '../lib/crosshair.js';
import { cx } from '../lib/cx.js';

/**
 * The in-game HUD widgets.
 *
 * Everything here draws over live game pixels, which is why every readout sits on its
 * own chip: Ultralight's text rasteriser does not reliably apply `text-shadow`, so the
 * design solves legibility structurally instead (design/ultralight-notes.md §3). The
 * only unbacked marks are the crosshair — a solid shape, not text — and the HUD editor's
 * selection label, which sits on a solid accent pill.
 */

/** How much visual weight a HUD chip carries. */
export type HudVariant = 'compact' | 'editor';

/** Props shared by every chip-shaped HUD widget. */
export interface HudChipProps extends HTMLAttributes<HTMLDivElement> {
  /**
   * - `compact` — `--hud-chip-bg` at r8, the in-game default.
   * - `editor` — `--hud-chip-bg-strong` at r10 with a visible `--border-dock` edge,
   *   as the HUD-layout frame draws it.
   */
  variant?: HudVariant;
  /** Render at 0.7 opacity, which is what the chips do while a panel is open. */
  dimmed?: boolean;
}

function chipClass(variant: HudVariant, dimmed: boolean, extra?: string): string {
  return cx('v-hudchip', variant === 'editor' && 'v-hudchip--editor', dimmed && 'v-hudchip--dimmed', extra);
}

/**
 * The level a chip draws when a reading is a proportion of something — `SaturationChip`'s
 * `bar` and `MemoryChip`'s `show_bar`.
 *
 * Not a new invention and deliberately not the quiet-cell meter: `Meter` / `CellMeter` are
 * the *slider*, a control with a current cell that takes the hue, and neither of these is a
 * control. The device this is, down to the 4px height and the `--tint-10` ground, is the
 * armour panel's durability bar — the one meter this design already draws over live game —
 * lifted out of `.v-armorlist__*` into the shared `.v-hudchip` vocabulary so that two more
 * chips did not each grow a private copy of it. Solid fill, no gradient, no animation (§5,
 * and design/ultralight-notes.md §8 on what the renderer will actually paint).
 *
 * Monochrome, unlike the armour bar, which turns amber and then red: durability has
 * thresholds and saturation and heap do not, so there is no state for a colour to mark, and
 * the live value on both of those chips is the figure standing next to this (§1).
 */
interface HudBarProps {
  /** How full the level is, 0-1. Already clamped by the caller. */
  fraction: number;
  /** A modifier on the track — `v-hudchip__bar--rule` is the one this file uses. */
  className?: string;
}

function HudBar({ fraction, className }: HudBarProps): React.ReactElement {
  return (
    <span className={cx('v-hudchip__bar', className)}>
      <span className="v-hudchip__fill" style={{ width: `${fraction * 100}%` }} />
    </span>
  );
}

/** A 0-1 reading held to 0-1, wherever one arrives from a sensor rather than from arithmetic. */
function clampFraction(value: number): number {
  return Math.min(1, Math.max(0, value));
}

/* -------------------------------------------------------------------------- */
/* FpsChip                                                                    */
/* -------------------------------------------------------------------------- */

/** Props for {@link FpsChip}. */
export interface FpsChipProps extends HudChipProps {
  /** Current frames per second. */
  fps: number;
  /** Whether to draw the trailing `fps` label — the mod's `show_label` setting. */
  showLabel?: boolean;
  /** The 1% low, drawn as ` ·  1% low 96` after the label. */
  onePercentLow?: number;
  /**
   * Ink for the figure — the mod's `color` setting, `#RRGGBB` or `#RRGGBBAA`.
   *
   * The **figure only**, never the `fps` unit or the 1% low aside. Quiet-cell §1: colour marks
   * a live value, and of the three things on this chip exactly one is a live value.
   */
  color?: string;
}

/** `142 fps  ·  1% low 96`. */
export function FpsChip({
  fps,
  showLabel = true,
  onePercentLow,
  color,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: FpsChipProps): React.ReactElement {
  return (
    <div className={chipClass(variant, dimmed, cx('v-fpschip', className))} {...rest}>
      <span className="v-hudchip__value" style={color ? { color } : undefined}>
        {fps}
      </span>
      {showLabel ? <span className="v-hudchip__unit">fps</span> : null}
      {onePercentLow === undefined ? null : (
        <span className="v-hudchip__aside">·&nbsp;&nbsp;1% low {onePercentLow}</span>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* PingChip                                                                   */
/* -------------------------------------------------------------------------- */

/** Props for {@link PingChip}. */
export interface PingChipProps extends HudChipProps {
  /** Round-trip time in milliseconds. `-1` means unknown and renders as `—`. */
  ping: number;
  /** The server host, drawn after the number. */
  host?: ReactNode;
  /** At or below this, the dot is green. Defaults to the mod's 60ms default. */
  goodMs?: number;
  /** At or above this, the dot is amber. Defaults to the mod's 150ms default. */
  badMs?: number;
  /** Whether to draw the trailing `ms` unit. */
  showLabel?: boolean;
}

/** `● 42 ms  Hypixel`, with the dot coloured by the mod's good/bad thresholds. */
export function PingChip({
  ping,
  host,
  goodMs = 60,
  badMs = 150,
  showLabel = true,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: PingChipProps): React.ReactElement {
  const unknown = ping < 0;
  // Three bands, three tones. It used to be `>= bad ? 'warn' : <= good ? 'ok' : 'warn'`, and
  // with any valid config — `bad_ms` above `good_ms`, which the schema asks for — the first and
  // last arms are the same colour, so `bad_ms` could not change a pixel however it was dragged.
  // A setting that is stored, clamped, echoed and drawn by nothing is the exact shape of the
  // audit that produced `design/rendering-invariants.md` §15; this one had simply hidden inside
  // a ternary rather than inside a missing read.
  const tone = unknown ? 'muted' : ping >= badMs ? 'bad' : ping <= goodMs ? 'ok' : 'warn';
  return (
    <div className={chipClass(variant, dimmed, className)} {...rest}>
      <StatusDot tone={tone} size={7} />
      <span className="v-hudchip__value">
        {unknown ? '—' : ping}
        {showLabel && !unknown ? <span className="v-hudchip__unit">&nbsp;ms</span> : null}
      </span>
      {host ? <span className="v-hudchip__unit">{host}</span> : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* DirectionChip                                                              */
/* -------------------------------------------------------------------------- */

/** How a facing is written. */
export type DirectionStyle = 'letter' | 'word' | 'axis';

/** Props for {@link DirectionChip}. */
export interface DirectionChipProps extends HudChipProps {
  /** Yaw in degrees, straight off the tick payload. */
  yaw: number;
  /**
   * `letter` (`N`), `word` (`North`) or `axis` (`+X`).
   *
   * Named `notation` rather than `style`, which is what the *setting* is called: this interface
   * extends `HTMLAttributes<HTMLDivElement>`, where `style` is already the inline style object,
   * and TypeScript rejects the clash outright. Worth the mismatch — `notation` is also the more
   * honest word, since all three are notations for one reading.
   */
  notation?: DirectionStyle;
  /** Whether the raw angle is printed after the facing. */
  showDegrees?: boolean;
  /**
   * Ink for the facing — the mod's `color` setting, `#RRGGBB` or `#RRGGBBAA`.
   *
   * The facing only, never the degrees aside: the aside is the same reading in another unit,
   * and colouring both makes the chip one solid block with no hierarchy in it.
   */
  color?: string;
}

/** The eight compass points, in the order `yawIndex` produces. */
const COMPASS = ['S', 'SW', 'W', 'NW', 'N', 'NE', 'E', 'SE'] as const;

const COMPASS_WORDS: Record<(typeof COMPASS)[number], string> = {
  S: 'South', SW: 'Southwest', W: 'West', NW: 'Northwest',
  N: 'North', NE: 'Northeast', E: 'East', SE: 'Southeast',
};

/**
 * Minecraft's world axes, per compass point.
 *
 * South is +Z and west is +X in 1.8.9 — not a mnemonic anybody remembers, which is exactly why
 * this style exists. The four diagonals name both axes because that is what a player lining up
 * a nether tunnel needs; naming only the dominant one would be a lie at 45°.
 */
const COMPASS_AXES: Record<(typeof COMPASS)[number], string> = {
  S: '+Z', SW: '+X +Z', W: '+X', NW: '+X −Z',
  N: '−Z', NE: '−X −Z', E: '−X', SE: '−X +Z',
};

/**
 * Which of the eight compass points a yaw faces.
 *
 * The same arithmetic as `@void/protocol`'s `cardinalFromYaw`, and deliberately duplicated
 * rather than imported: `@void/ui` is the design system and does not depend on the wire
 * protocol, so a chip that took its reading from the protocol package would invert that. The
 * two are pinned together by `test/hud.test.tsx`, which walks all eight octants against
 * `cardinalFromYaw` — a shared *number* is worth a test; a shared *dependency* is not.
 */
export function yawIndex(yaw: number): (typeof COMPASS)[number] {
  const index = Math.round((((yaw + 180) % 360) + 360) % 360 / 45) % 8;
  return COMPASS[index] ?? 'S';
}

/** `N`, `North`, or `−Z` — optionally with the raw angle after it. */
export function DirectionChip({
  yaw,
  notation = 'letter',
  showDegrees = false,
  color,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: DirectionChipProps): React.ReactElement {
  const point = yawIndex(yaw);
  const facing =
    notation === 'word' ? COMPASS_WORDS[point] : notation === 'axis' ? COMPASS_AXES[point] : point;
  // Yaw runs unbounded in both directions in game; a chip printing `-1043°` is not a reading.
  const degrees = Math.round((((yaw % 360) + 360) % 360));
  return (
    <div className={chipClass(variant, dimmed, cx('v-dirchip', className))} {...rest}>
      <span className="v-hudchip__value" style={color ? { color } : undefined}>
        {facing}
      </span>
      {showDegrees ? <span className="v-hudchip__unit">{degrees}°</span> : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* CoordsChip                                                                 */
/* -------------------------------------------------------------------------- */

/** Props for {@link CoordsChip}. */
export interface CoordsChipProps extends HudChipProps {
  /** World X. */
  x: number;
  /** World Y, feet level. */
  y: number;
  /** World Z. */
  z: number;
  /** The cardinal direction derived from yaw, e.g. `NE`. Omit to hide it. */
  direction?: string;
  /** Decimal places, per the mod's `decimals` setting. Defaults to 0. */
  decimals?: number;
  /**
   * The mod's `layout` setting.
   *
   * - `inline` — one line, `X 118   Y 64   Z -212   ·   NE`, as the frame draws it.
   * - `stacked` — one axis per line, which is what people who read coordinates while
   *   moving actually want: the three numbers hold the same x-position, so a digit
   *   changing is a digit you can see change.
   */
  layout?: 'inline' | 'stacked';
  /**
   * Ink for the readings — the mod's `color` setting, `#RRGGBB` or `#RRGGBBAA`.
   *
   * The three axes and the cardinal, which are the live values; never the `·` between them,
   * which is a separator. Quiet-cell §1, and the same division {@link FpsChipProps.color}
   * makes between a figure and its unit.
   */
  color?: string;
}

/** `X 118   Y 64   Z -212   ·   NE`, or the same three axes stacked. */
export function CoordsChip({
  x,
  y,
  z,
  direction,
  decimals = 0,
  layout = 'inline',
  color,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: CoordsChipProps): React.ReactElement {
  const format = (value: number): string => value.toFixed(decimals);
  const stacked = layout === 'stacked';
  const ink = color ? { color } : undefined;
  return (
    <div
      className={chipClass(
        variant,
        dimmed,
        cx('v-coordschip', stacked && 'v-coordschip--stacked', className),
      )}
      {...rest}
    >
      <span className="v-coordschip__axis" style={ink}>X {format(x)}</span>
      <span className="v-coordschip__axis" style={ink}>Y {format(y)}</span>
      <span className="v-coordschip__axis" style={ink}>Z {format(z)}</span>
      {direction ? (
        <>
          {/* The separator is a horizontal device. Stacked, the line break already
              separates, and a `·` on a line of its own is a mark that means nothing. */}
          {stacked ? null : <span className="v-hudchip__aside">·</span>}
          <span className="v-coordschip__axis" style={ink}>{direction}</span>
        </>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* CpsChip                                                                    */
/* -------------------------------------------------------------------------- */

/** Props for {@link CpsChip}. */
export interface CpsChipProps extends HudChipProps {
  /** Left-button clicks per second. */
  left: number;
  /** Right-button clicks per second. Omit in `left`-only mode. */
  right?: number;
  /** Which buttons the mod counts. Matches the mod's `mode` setting. */
  mode?: 'left' | 'right' | 'both';
  /**
   * Whether to draw the trailing `CPS` unit — the mod's `show_label` setting.
   *
   * The other three readouts have had this since they were written; this one had not, which
   * made it the widest chip on screen for a player who knows perfectly well what the number is.
   */
  showLabel?: boolean;
  /**
   * The session's highest rate, drawn as ` · peak 14` after the unit.
   *
   * Deliberately the same shape as {@link FpsChipProps.onePercentLow}, and for the same reason:
   * both are a second figure that gives the first one a scale, and both are the number that
   * holds still while the live one moves. Muted, like that one — the live figure is the live
   * value and stays the only coloured thing on the chip.
   */
  peak?: number;
}

/** `12 | 9 CPS` — the left figure in the accent ink, the right in the primary. */
export function CpsChip({
  left,
  right,
  mode = 'both',
  showLabel = true,
  peak,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: CpsChipProps): React.ReactElement {
  return (
    <div className={chipClass(variant, dimmed, className)} {...rest}>
      {mode === 'right' ? (
        <span className="v-cpschip__right">{right ?? 0}</span>
      ) : (
        <span className="v-cpschip__left">{left}</span>
      )}
      {mode === 'both' ? (
        <>
          <span className="v-cpschip__sep">|</span>
          <span className="v-cpschip__right">{right ?? 0}</span>
        </>
      ) : null}
      {showLabel ? <span className="v-cpschip__unit">CPS</span> : null}
      {peak === undefined ? null : (
        <span className="v-hudchip__aside">·&nbsp;&nbsp;peak {peak}</span>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* ComboChip                                                                  */
/* -------------------------------------------------------------------------- */

/** Props for {@link ComboChip}. */
export interface ComboChipProps extends HudChipProps {
  /** Consecutive hits landed. */
  combo: number;
  /**
   * Trailing "combo" unit — the mod's `show_label` setting.
   *
   * On by default, like every other chip's label: `7` alone over game pixels is a number
   * with no noun, and this chip is small enough that the noun costs nothing.
   */
  showLabel?: boolean;
  /**
   * Fraction of the combo's reset window still to run, 0..1. Undefined draws no rule.
   *
   * `combo.reset_ms` is the mod's own policy — `bridge.json`'s `hits` field sends monotonic
   * counters precisely so the timeout lives here rather than in the sensor — and a timeout
   * is the one kind of setting that is invisible in a still frame. This is what makes it
   * visible: a hairline under the figure that shortens as the window runs out, so a combo
   * about to lapse says so while there is still time to do something about it.
   *
   * A **state**, not a preference, which is what keeps it inside quiet-cell §1: it is the
   * live value of a clock, and it is drawn in the figure's own ink at low alpha rather than
   * in a second colour, because it is the same combo it sits under.
   */
  remaining?: number;
}

/**
 * `7 combo`, over a rule that runs out with the reset window.
 *
 * A count and its noun — the house shape ({@link FpsChip}), with no aside, because a combo
 * has no second figure that gives the first one a scale; what it has instead is a clock, and
 * {@link ComboChipProps.remaining} draws that.
 *
 * A `combo` of `0` draws `0` here, but the in-game HUD never asks for one: `hud/combo.tsx`
 * returns `null` at zero or past the window, because a chip reading `0 combo` between fights
 * is noise, and `schema/mods/combo.json`'s `$comment` argues why that is behaviour rather than
 * a setting. Hiding costs nothing on the HUD — `placementStyle` gives every slot
 * `position: absolute` from its own anchor, so a widget that draws nothing cannot move
 * anything else, and `HudCoordinates`, `HudPotionEffects` and `HudArmorStatus` already take
 * exactly this shape. The zero path is for `apps/desktop`, which draws mod cards from these
 * chips without the widget layer in front of them.
 */
export function ComboChip({
  combo,
  showLabel = true,
  remaining,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: ComboChipProps): React.ReactElement {
  const ruled = remaining !== undefined;
  return (
    <div className={chipClass(variant, dimmed, className)} {...rest}>
      <span
        className={cx('v-hudchip__value', ruled && 'v-hudchip__value--ruled')}
      >
        {combo}
        {/* Inside the figure, so `currentColor` is the figure's ink and the rule cannot end
            up a second colour; and so it measures the figure rather than the chip, which is
            what makes a full window read as full. */}
        {remaining === undefined ? null : (
          <HudBar fraction={clampFraction(remaining)} className="v-hudchip__bar--rule" />
        )}
      </span>
      {showLabel ? <span className="v-hudchip__unit">combo</span> : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* SaturationChip                                                             */
/* -------------------------------------------------------------------------- */

/** How {@link SaturationChip} draws its reading. */
export type SaturationStyle = 'number' | 'bar' | 'both';

/** Props for {@link SaturationChip}. */
export interface SaturationChipProps extends Omit<HudChipProps, 'style'> {
  /** Food saturation, 0-20. Clamped to that range before it is drawn. */
  saturation: number;
  /**
   * The mod's `style` setting: the figure, the bar, or both.
   *
   * Named `style` because that is the frozen contract, which costs this interface the
   * inline `style` attribute — see the note on {@link SaturationChip} itself.
   */
  style?: SaturationStyle;
  /**
   * Decimal places on the figure — `0`, `1` or `2`, per the mod's `decimals` setting.
   *
   * Defaults to 1. Saturation is genuinely fractional and it is the fraction that is the
   * reading: it drains continuously while food holds still, so a chip rounding 17.9 to 18
   * for four seconds is a chip that says nothing is happening.
   *
   * The range is 0..2 rather than the 0..1 this prop was first specified with, because
   * `SETTING_BOUNDS` in `packages/protocol/scripts/gen.mjs` is keyed by property *name*
   * across every mod and `coordinates.decimals` and `momentum.decimals` are both 0..2.
   * Nothing here clamps: 2 is passed straight to `toFixed` like the other two.
   */
  decimals?: number;
  /** Whether to draw the trailing `sat` unit — the mod's `show_label` setting. */
  showLabel?: boolean;
}

/** Saturation held to the 0-20 the food system produces. */
function clampSaturation(value: number): number {
  return Math.min(20, Math.max(0, value));
}

/**
 * `17.5 sat`, a 40px level, or both.
 *
 * `style` collides with `HTMLAttributes.style`, so this interface omits the inline style
 * attribute rather than renaming the prop. {@link DirectionChipProps.notation} went the
 * other way and renamed; this one cannot, because the name is a frozen contract three
 * other packages are already coding against. Nothing in the HUD passes a chip an inline
 * style — the editor positions the wrapper — so the omission costs nothing today, but it
 * is the second time this collision has been paid for and it is worth saying out loud.
 */
export function SaturationChip({
  saturation,
  style = 'number',
  decimals = 1,
  showLabel = true,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: SaturationChipProps): React.ReactElement {
  const value = clampSaturation(saturation);
  return (
    <div className={chipClass(variant, dimmed, className)} {...rest}>
      {style === 'bar' ? null : (
        <>
          <span className="v-hudchip__value">
            {value.toFixed(decimals)}
          </span>
          {showLabel ? <span className="v-hudchip__unit">sat</span> : null}
        </>
      )}
      {style === 'number' ? null : <HudBar fraction={value / 20} />}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* MomentumChip                                                               */
/* -------------------------------------------------------------------------- */

/** The units {@link MomentumChip} can read a speed in. */
export type MomentumUnit = 'bps' | 'kmh';

/** Props for {@link MomentumChip}. */
export interface MomentumChipProps extends HudChipProps {
  /** Horizontal ground speed in blocks per second. Always in `bps`, whatever `unit` says. */
  speed: number;
  /**
   * The mod's `unit` setting.
   *
   * - `bps` — blocks per second, the number the game itself works in.
   * - `kmh` — the same speed at 3.6 km/h per block per second, since a block is a metre.
   *
   * A conversion, not a second reading: {@link MomentumChipProps.speed} is `bps` in both.
   */
  unit?: MomentumUnit;
  /**
   * Decimal places — `0`, `1` or `2`, per the mod's `decimals` setting.
   *
   * Defaults to 2, which is also the ceiling, because the sensor rounds to 2 dp before it
   * sends: a third place would be digits the wire never carried. It is where the differences
   * a movement player is chasing actually are — sprinting and sprint-jumping are tenths apart.
   */
  decimals?: number;
  /** Whether to draw the trailing unit — the mod's `show_label` setting. */
  showLabel?: boolean;
}

/** 1 block per second is 3.6 km/h — a block is a metre. */
const KMH_PER_BPS = 3.6;

/** `4.3 bps`, or the same speed as `15.5 km/h`. */
export function MomentumChip({
  speed,
  unit = 'bps',
  decimals = 2,
  showLabel = true,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: MomentumChipProps): React.ReactElement {
  const figure = unit === 'kmh' ? speed * KMH_PER_BPS : speed;
  return (
    <div className={chipClass(variant, dimmed, className)} {...rest}>
      <span className="v-hudchip__value">
        {figure.toFixed(decimals)}
      </span>
      {showLabel ? (
        <span className="v-hudchip__unit">{unit === 'kmh' ? 'km/h' : 'bps'}</span>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* MemoryChip                                                                 */
/* -------------------------------------------------------------------------- */

/** How {@link MemoryChip} writes the heap. */
export type MemoryStyle = 'used' | 'used_of_max' | 'percent';

/** Props for {@link MemoryChip}. */
export interface MemoryChipProps extends Omit<HudChipProps, 'style'> {
  /** Heap in use, in megabytes. The live value, and the only figure that ticks. */
  usedMb: number;
  /** Heap ceiling, in megabytes — the JVM's `-Xmx`. Constant for the session. */
  maxMb: number;
  /**
   * The mod's `style` setting.
   *
   * - `used` — `1024 MB`.
   * - `used_of_max` — `1024 / 4096 MB`.
   * - `percent` — `25%`.
   *
   * Named `style` because that is the frozen contract, at the cost of the inline `style`
   * attribute — the same trade {@link SaturationChipProps.style} makes.
   */
  style?: MemoryStyle;
  /** Draw the 40px level after the figure, filled to `usedMb / maxMb`. */
  showBar?: boolean;
  /**
   * Whether to draw the unit — the mod's `show_label` setting.
   *
   * The unit is the last thing on the figure in all three styles (`MB`, `MB`, `%`) and
   * `/ 4096` is not part of it: the ceiling is a second *figure*, not a label, so
   * `used_of_max` with the label off still reads `1024 / 4096`. That is what keeps this
   * switch doing one job everywhere rather than three different ones.
   */
  showLabel?: boolean;
}

/** `1024 / 4096 MB`, `1024 MB` or `25%`, optionally over a level. */
export function MemoryChip({
  usedMb,
  maxMb,
  style = 'used_of_max',
  showBar = false,
  showLabel = true,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: MemoryChipProps): React.ReactElement {
  const fraction = maxMb > 0 ? Math.min(1, Math.max(0, usedMb / maxMb)) : 0;
  const figure = style === 'percent' ? Math.round(fraction * 100) : Math.round(usedMb);
  // The tail is one muted run in one span rather than several, so it stays a single flex item
  // and the chip's 8px gap cannot open up inside a reading. Its spaces are `\u00a0`, for the
  // same reason `PingChip` writes `&nbsp;ms`: `1024 / 4096 MB` is one reading, not three
  // things with room between them.
  const tail =
    style === 'percent'
      ? showLabel ? '%' : ''
      : (style === 'used_of_max' ? `\u00a0/\u00a0${Math.round(maxMb)}` : '') +
        (showLabel ? '\u00a0MB' : '');
  return (
    <div className={chipClass(variant, dimmed, className)} {...rest}>
      <span className="v-hudchip__value">
        <span>{figure}</span>
        {tail ? <span className="v-hudchip__unit">{tail}</span> : null}
      </span>
      {showBar ? <HudBar fraction={fraction} /> : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* ServerAddressChip                                                          */
/* -------------------------------------------------------------------------- */

/** Props for {@link ServerAddressChip}. */
export interface ServerAddressChipProps extends HudChipProps {
  /**
   * The address to draw.
   *
   * Already formatted by the caller — short or full is the widget's decision, not the
   * chip's. An empty or whitespace-only host renders nothing at all; see
   * {@link ServerAddressChip}.
   */
  host: string;
}

/**
 * `mc.hypixel.net` — the server you are on, and nothing else.
 *
 * **Returns `null` for an empty host**, rather than an empty chip: a widget with nothing
 * to say says nothing. That is safe here in a way it is not on
 * {@link ItemCounterChip} because the absence is not intermittent — singleplayer has no
 * address for the whole session, so the chip does not appear and disappear under the
 * player, it is simply not there. `HudCoords` already takes this shape in
 * `packages/ingame` for a position it has not received yet.
 *
 * The return type is therefore `React.ReactElement | null`, which is the one place this
 * component's signature is wider than the frozen API's `React.ReactElement`.
 */
export function ServerAddressChip({
  host,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: ServerAddressChipProps): React.ReactElement | null {
  if (host.trim() === '') return null;
  return (
    <div className={chipClass(variant, dimmed, cx('v-serverchip', className))} {...rest}>
      <span className="v-hudchip__value">
        {host}
      </span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* ItemCounterChip                                                            */
/* -------------------------------------------------------------------------- */

/** Props for {@link ItemCounterChip}. */
export interface ItemCounterChipProps extends HudChipProps {
  /**
   * Stack size of the held item.
   *
   * `null` is an empty hand, which is **not** a count of zero — zero is a real reading a
   * stack can arrive at, and the two must not draw the same. An empty hand renders `—`;
   * see {@link ItemCounterChip}.
   */
  count: number | null;
  /**
   * Whether the count is prefixed with the multiplication sign — `x12` rather than `12`.
   *
   * The mod's `show_label` setting, and the one label on these six chips that leads rather
   * than trails: a stack size has no unit, so there is no noun to put after it, and `x` is
   * the cheapest mark that says the figure is a quantity of something rather than a rate —
   * which matters because this chip is usually placed next to a CPS figure. Written as an
   * ASCII `x` rather than `×`, which is how the mod's own schema spells the example, and
   * which cannot go missing from a bundled static face.
   */
  showLabel?: boolean;
  /**
   * At or below this the figure takes the warn treatment. `0` disables it.
   *
   * Defaults to `0`, i.e. off: the warn ink is legitimate because it marks a state, but a
   * state nobody configured is not one, and the threshold that is worth warning at is a
   * property of what you are holding — sixteen blocks and sixteen pearls are not the same
   * situation — so the chip will not pick one for you.
   */
  lowThreshold?: number;
}

/**
 * `x12`, with the figure amber under the low threshold.
 *
 * **An empty hand renders `—`, and does not return `null`** — which is where this parts
 * company with {@link ServerAddressChip}. An empty hand is the most ordinary thing a
 * player's hand does: it happens every time a stack is thrown, and it un-happens a tick
 * later. A chip that removed itself on each of those would strobe in and out of a
 * player-placed HUD slot during exactly the fight it was placed for, and would drag
 * whatever the layout puts after it back and forth. `—` is what {@link PingChip} already
 * draws for a reading it does not have: the widget stays where it was put and says it
 * has nothing.
 */
export function ItemCounterChip({
  count,
  showLabel = true,
  lowThreshold = 0,
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: ItemCounterChipProps): React.ReactElement {
  const empty = count === null;
  const low = !empty && lowThreshold > 0 && count <= lowThreshold;
  return (
    <div className={chipClass(variant, dimmed, className)} {...rest}>
      {/* The prefix is inside the value rather than beside it, so the chip's 8px gap cannot
          open up between the `x` and the number it belongs to — the same reason `PingChip`
          keeps its `ms` in the value span. It is dropped on an empty hand: `x —` is not a
          reading of anything. */}
      <span className="v-hudchip__value">
        {showLabel && !empty ? <span className="v-hudchip__unit">x</span> : null}
        <span className={cx(low && 'v-hudchip__value--warn')}>{empty ? '—' : count}</span>
      </span>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* PotionList                                                                 */
/* -------------------------------------------------------------------------- */

/** One row of {@link PotionList}. */
export interface PotionRow {
  /** Effect name plus amplifier, e.g. `Speed II`. */
  name: ReactNode;
  /** Remaining time, e.g. `1:24`. */
  time?: ReactNode;
  /** The 10px swatch colour. */
  color: string;
}

/** Props for {@link PotionList}. */
export interface PotionListProps extends HTMLAttributes<HTMLDivElement> {
  /** The active effects. */
  effects: readonly PotionRow[];
}

/** The 150px effect list: a colour swatch, the name, and the countdown. */
export function PotionList({
  effects,
  className,
  ...rest
}: PotionListProps): React.ReactElement {
  return (
    <div className={cx('v-potionlist', className)} {...rest}>
      {effects.map((effect, index) => (
        <div key={index} className="v-potionlist__row">
          <span className="v-potionlist__swatch" style={{ background: effect.color }} />
          <span className="v-potionlist__name">{effect.name}</span>
          {effect.time === undefined ? null : (
            <span className="v-potionlist__timer">{effect.time}</span>
          )}
        </div>
      ))}
    </div>
  );
}

/** Format a `duration_ms` from a `tick` payload as the HUD's `m:ss`. */
export function formatPotionTime(durationMs: number): string {
  const total = Math.max(0, Math.round(durationMs / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/** Render a 0-based amplifier as the roman numeral the HUD prints: 0 → ``, 1 → `II`. */
export function formatAmplifier(amplifier: number): string {
  const numerals = ['', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];
  return numerals[amplifier] ?? String(amplifier + 1);
}

/* -------------------------------------------------------------------------- */
/* ArmorList                                                                  */
/* -------------------------------------------------------------------------- */

/** One row of {@link ArmorList}. */
export interface ArmorRow {
  /** Slot label, e.g. `Helmet`. */
  label: ReactNode;
  /**
   * Short slot label, e.g. `Helm`, used by the `horizontal` orientation.
   *
   * Laid side by side, five cells have room for four or five characters, not for
   * `Chestplate`. Falls back to {@link ArmorRow.label} when absent, which is correct
   * for a caller that has no shorter form rather than a caller that forgot.
   */
  short?: ReactNode;
  /** Remaining durability. */
  remaining: number;
  /** Maximum durability. `0` means the item takes no damage. */
  max: number;
  /** Override the icon swatch colour. */
  iconColor?: string;
}

/** Props for {@link ArmorList}. */
export interface ArmorListProps extends HTMLAttributes<HTMLDivElement> {
  /** The worn pieces, and the held item when `show_held_item` is on. */
  rows: readonly ArmorRow[];
  /** Below this fraction the bar turns amber. Defaults to 0.5. */
  warnBelow?: number;
  /**
   * The mod's `orientation` setting, and it names the direction the pieces run in.
   *
   * - `horizontal` — the pieces side by side, one narrow cell each: short label over a
   *   durability bar. The compact strip a PvP HUD wants, and the default.
   * - `vertical` — the pieces stacked top to bottom, one full row each: label, value and
   *   a full-width bar. The informative one.
   *
   * Both used to render the same column: the base rule is `flex-direction: column` and the
   * `--vertical` modifier only widened it, so `horizontal` drew a vertical list and the
   * setting changed a width. The enum was never wrong; the second layout was missing.
   */
  orientation?: 'horizontal' | 'vertical';
  /**
   * The mod's `show_durability` setting.
   *
   * Off drops the bar and the `n / max` figure and keeps the icon and the label, which
   * leaves a plain "what am I wearing" slot indicator — a coherent thing to want, and the
   * only reading of this setting that leaves anything behind.
   */
  showDurability?: boolean;
}

/** The armour panel: icon, label / value, and a 4px durability bar per piece. */
export function ArmorList({
  rows,
  warnBelow = 0.5,
  orientation = 'horizontal',
  showDurability = true,
  className,
  ...rest
}: ArmorListProps): React.ReactElement {
  const horizontal = orientation === 'horizontal';
  return (
    <div
      className={cx(
        'v-armorlist',
        horizontal ? 'v-armorlist--horizontal' : 'v-armorlist--vertical',
        !showDurability && 'v-armorlist--nodurability',
        className,
      )}
      {...rest}
    >
      {rows.map((row, index) => {
        const fraction = row.max > 0 ? Math.max(0, Math.min(1, row.remaining / row.max)) : 1;
        return (
          <div key={index} className="v-armorlist__row">
            <span
              className="v-armorlist__icon"
              style={row.iconColor ? { background: row.iconColor } : undefined}
            />
            <span className="v-armorlist__body">
              <span className="v-armorlist__labels">
                <span className="v-armorlist__label">
                  {horizontal ? (row.short ?? row.label) : row.label}
                </span>
                {showDurability ? (
                  <span className="v-armorlist__value">
                    {row.remaining} / {row.max}
                  </span>
                ) : null}
              </span>
              {showDurability ? (
                <span className="v-armorlist__bar">
                  <span
                    className={cx(
                      'v-armorlist__fill',
                      fraction < warnBelow && 'v-armorlist__fill--warn',
                      fraction <= 0.1 && 'v-armorlist__fill--empty',
                    )}
                    style={{ width: `${fraction * 100}%` }}
                  />
                </span>
              ) : null}
            </span>
          </div>
        );
      })}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* KeystrokesWidget                                                           */
/* -------------------------------------------------------------------------- */

/** Which keys are down. Every field defaults to released. */
export interface KeystrokesState {
  w?: boolean;
  a?: boolean;
  s?: boolean;
  d?: boolean;
  lmb?: boolean;
  rmb?: boolean;
  space?: boolean;
  shift?: boolean;
}

/* ------------------------------------------------ keycap colour vocabulary */

/**
 * `keystrokes.key_color` — the enum of swatch *names* mods.json declares, resolved to the
 * token each one stands for.
 *
 * Names, not hex, because a name survives a theme change and a hex freezes the palette
 * into the loadout. This table is the **single** resolution of those names: the widget
 * paints from it and the settings swatches are drawn from it, so a swatch cannot show one
 * colour while the keycap takes another. It used to exist only in the settings panel,
 * which is precisely why the widget did not read it at all.
 */
export const KEYCAP_COLORS: Readonly<Record<string, { color: string; label: string }>> = {
  shell: { color: 'var(--bg-shell)', label: 'Shell' },
  raised: { color: 'var(--surface-raised)', label: 'Raised' },
  pill: { color: 'var(--card-bg)', label: 'Pill' },
  sky: { color: 'var(--hue-visual)', label: 'Ice' },
  teal: { color: 'var(--hue-utility)', label: 'Mint' },
};

/**
 * `keystrokes.pressed_color`, resolved the same way.
 *
 * `warn` and `fear` used to resolve to the same `--hue-pvp`, which made five options where
 * four existed — a menu that lies about the choice on offer. They are now the two tokens
 * whose Figma names they are: `--warn` is zg/warn (amber) and `--danger` is zg/fear (red).
 */
export const KEYCAP_PRESSED_COLORS: Readonly<Record<string, { color: string; label: string }>> = {
  accent: { color: 'var(--hue, var(--accent))', label: 'Hue' },
  sky: { color: 'var(--hue-visual)', label: 'Ice' },
  warn: { color: 'var(--warn)', label: 'Amber' },
  fear: { color: 'var(--danger)', label: 'Fear' },
  teal: { color: 'var(--hue-utility)', label: 'Mint' },
};

/**
 * The inline style that paints a keystrokes widget in the loadout's chosen colours.
 *
 * Two custom properties on the root rather than two bespoke style hooks, which is the same
 * mechanism `keystrokes.corner_radius` already uses for `--radius-control`: the keycap rules
 * underneath read the token and nothing in them names a colour. An unknown or absent name
 * writes no property at all, so the CSS fallback stands.
 */
export function keystrokesColorStyle(
  keyColor?: string | null,
  pressedColor?: string | null,
): React.CSSProperties {
  const style: Record<string, string> = {};
  const key = keyColor ? KEYCAP_COLORS[keyColor] : undefined;
  const pressed = pressedColor ? KEYCAP_PRESSED_COLORS[pressedColor] : undefined;
  if (key) style['--key-bg'] = key.color;
  if (pressed) style['--key-pressed-bg'] = pressed.color;
  return style as React.CSSProperties;
}

/** Props for {@link KeystrokesWidget}. */
export interface KeystrokesWidgetProps extends HTMLAttributes<HTMLDivElement> {
  /**
   * Key state, straight from the `keys` bridge event. The event is edge-triggered and
   * carries `0 | 1` per key; pass `keys.w === 1` and so on.
   */
  keys?: KeystrokesState;
  /** Draw the LMB / RMB row — the mod's `show_mouse` setting. */
  showMouse?: boolean;
  /** Draw the wide space bar under the block — the mod's `show_spacebar` setting. */
  showSpacebar?: boolean;
  /**
   * Draw the sneak cap beside the space bar — the mod's `show_sneak` setting.
   *
   * {@link KeystrokesState.shift} has been carried by the `keys` event and handed to this
   * component since it was written, and no cap has ever drawn it. This is that cap.
   */
  showSneak?: boolean;
  /** Print CPS inside the mouse keys — the mod's `show_cps` setting. */
  cps?: { left: number; right: number };
}

/**
 * The full-size 40px keystrokes widget the HUD draws.
 *
 * A pressed key is `--accent` with a `--tint-35` border and the accent glow; an
 * unpressed key is `--key-bg` with the `--inset-key` bevel. Both are box-shadows, which
 * Ultralight renders correctly (§8).
 *
 * The renderer must touch only the changed key's node (§9), which is why each cap is
 * its own element with its own class and nothing above it re-renders on a key change.
 */
export function KeystrokesWidget({
  keys = {},
  showMouse = true,
  showSpacebar = false,
  showSneak = false,
  cps,
  className,
  ...rest
}: KeystrokesWidgetProps): React.ReactElement {
  const key = (label: string, pressed: boolean | undefined, modifier?: string, extra?: ReactNode) => (
    <span
      key={label}
      className={cx(
        'v-keystrokes__key',
        modifier,
        pressed && 'v-keystrokes__key--pressed',
      )}
    >
      {label}
      {extra}
    </span>
  );

  return (
    <div className={cx('v-keystrokes', className)} {...rest}>
      <div className="v-keystrokes__row">{key('W', keys.w)}</div>
      <div className="v-keystrokes__row">
        {key('A', keys.a)}
        {key('S', keys.s)}
        {key('D', keys.d)}
      </div>
      {showMouse ? (
        <div className="v-keystrokes__row">
          {key(
            'LMB',
            keys.lmb,
            'v-keystrokes__key--wide',
            cps ? <span className="v-keystrokes__cps">{cps.left}</span> : null,
          )}
          {key(
            'RMB',
            keys.rmb,
            'v-keystrokes__key--wide',
            cps ? <span className="v-keystrokes__cps">{cps.right}</span> : null,
          )}
        </div>
      ) : null}
      {/* One row for both, because they are the two keys a foot is on: the space bar keeps the
          width it had when it was alone, and the sneak cap takes the square beside it. A row of
          its own for sneak would make the widget taller than the frames draw it for a cap that
          is off by default. */}
      {showSpacebar || showSneak ? (
        <div className="v-keystrokes__row">
          {showSpacebar
            ? key(
                '␣',
                keys.space,
                cx(
                  'v-keystrokes__key--space',
                  showSneak && 'v-keystrokes__key--space-narrow',
                ),
              )
            : null}
          {showSneak ? key('⇧', keys.shift, 'v-keystrokes__key--sneak') : null}
        </div>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Crosshair, Hotbar                                                          */
/* -------------------------------------------------------------------------- */

/** Props for {@link Crosshair}. */
export interface CrosshairProps extends Omit<HTMLAttributes<HTMLDivElement>, 'color'> {
  /**
   * The mod's `style` setting.
   *
   * Named `shape` because `style` is React's own prop on every element, and a component that
   * shadows it cannot be given a `style` either.
   */
  shape?: CrosshairStyle;
  /** The mod's `size` — half-length of each arm, in crosshair units. */
  size?: number;
  /** The mod's `thickness`. */
  thickness?: number;
  /** The mod's `gap`. */
  gap?: number;
  /** The mod's `color`, `#RRGGBB` or `#RRGGBBAA`. */
  color?: string;
  /** The mod's `outline` — a one-unit black edge, exactly as `CrosshairRenderer` fills it. */
  outline?: boolean;
  /** The mod's `dynamic`. */
  dynamic?: boolean;
  /** The mod's `center_dot`. */
  centerDot?: boolean;
  /** Whether the player is sprinting, which is what `dynamic` widens the gap for. */
  sprinting?: boolean;
  /**
   * Draw where `dynamic` will put the arms, as a low-alpha ghost behind the live ones.
   *
   * A **preview affordance, and only that** — the game never draws two crosshairs. `dynamic`
   * is otherwise a setting whose whole effect happens during the one activity that takes the
   * player out of the settings panel, so a page without this has a switch that does nothing
   * visible when you flip it. The ghost is the same ink at .22, so it stays monochrome where
   * the crosshair is monochrome and takes the crosshair's own colour where it is tinted —
   * it is the same live value, drawn twice.
   *
   * It appears only where the setting bites: `dot`, `circle`, `default` and `none` ignore the
   * gap, so nothing ghosts on them, which is the honest answer rather than a decoration.
   */
  showSpread?: boolean;
  /**
   * Pixels per crosshair unit. 1 is the game's own scale; `CROSSHAIR_UNIT_PREVIEW`
   * (`lib/crosshair.ts`) is what the mod page draws at.
   *
   * A multiplier on the arithmetic, never a `transform` — see the note on that constant.
   */
  unit?: number;
}

/**
 * The crosshair, drawn from {@link crosshairRects} — the same arithmetic
 * `CrosshairGeometry.java` fills in GL.
 *
 * In production the mod is GL, not HTML, because it must sit on the exact pixel centre (§3).
 * This is what the `?debug` harness, the HUD editor and the mod page's live preview draw, and
 * until now it was a bare 18 × 18 plus that took **no props at all** — so seven settings that
 * are live in game could not be seen anywhere the player was actually setting them.
 */
export function Crosshair({
  shape = 'cross',
  size = 5,
  thickness = 1,
  gap = 2,
  color,
  outline = true,
  dynamic = false,
  centerDot = false,
  sprinting = false,
  showSpread = false,
  unit = 1,
  className,
  style,
  ...rest
}: CrosshairProps): React.ReactElement {
  // `default` means "the vanilla pass draws it" — which is true in game and useless in a
  // preview, where an empty box on the mod's own default value reads as a broken mod. Drawing
  // vanilla's own proportions is what is actually on screen.
  const vanilla = keepsVanilla(shape);
  const drawn = vanilla ? 'cross' : shape;
  const s = vanilla ? VANILLA.size : size;
  const t = vanilla ? VANILLA.thickness : thickness;
  const g = vanilla ? VANILLA.gap : gap;
  const dot = vanilla ? false : centerDot;

  const spread = dynamicSpread(dynamic, sprinting);
  const live = crosshairRects(drawn, s, t, g, spread, dot);
  const ghost =
    showSpread && dynamic && !sprinting ? crosshairRects(drawn, s, t, g, 2, dot) : [];

  // The box is the widest the shape can reach, so the mark is centred on the box centre and
  // the caller can place the box rather than the arms.
  const reach = (isRing(drawn) ? s + t : s + g + t) * unit;
  const ink = color ?? 'var(--text-primary)';
  // The ghost is drawn as an **outline**, not as a fill, and the reason is geometric rather
  // than aesthetic: `dynamic` moves an arm out by 2 while the arm is `size` long, so wherever
  // `spread < size` the two positions overlap. Two translucent fills over one another read as
  // one dirty arm with a smear on the end — it looks like a rendering fault, not like a second
  // position. An outline stays legible through the overlap: you can see the arm, and you can
  // see the box it will move into.
  const edge = Math.max(1, Math.round(unit / 3));
  const rect = (r: CrosshairRect, index: number, faint: boolean) => (
    <span
      key={`${faint ? 'g' : 'r'}${index}`}
      className={cx('v-crosshair__rect', faint && 'v-crosshair__rect--ghost')}
      style={{
        left: reach + r.x * unit,
        top: reach + r.y * unit,
        width: r.w * unit,
        height: r.h * unit,
        background: faint ? 'transparent' : ink,
        border: faint ? `${edge}px solid ${ink}` : undefined,
        // The outline is `CrosshairRenderer`'s own: it fills a black rectangle one pixel out
        // on every side before it fills the ink. A spread box-shadow is that rectangle.
        boxShadow: !faint && outline ? `0 0 0 ${unit}px #000` : undefined,
      }}
    />
  );

  return (
    <div
      className={cx('v-crosshair', className)}
      aria-hidden="true"
      style={{ width: reach * 2, height: reach * 2, ...style }}
      {...rest}
    >
      {ghost.map((r, i) => rect(r, i, true))}
      {isRing(drawn) ? (
        <span
          className="v-crosshair__ring"
          style={{
            left: reach - s * unit,
            top: reach - s * unit,
            width: s * 2 * unit,
            height: s * 2 * unit,
            borderWidth: t * unit,
            borderColor: ink,
            boxShadow: outline
              ? `0 0 0 ${unit}px #000, inset 0 0 0 ${unit}px #000`
              : undefined,
          }}
        />
      ) : null}
      {live.map((r, i) => rect(r, i, false))}
    </div>
  );
}

/** Props for {@link Hotbar}. */
export interface HotbarProps extends HTMLAttributes<HTMLDivElement> {
  /** One entry per slot; `null` is an empty slot. Nine slots is vanilla. */
  slots: readonly (string | null)[];
}

/** The nine 40px hotbar slots, each holding a 20px colour block when filled. */
export function Hotbar({ slots, className, ...rest }: HotbarProps): React.ReactElement {
  return (
    <div className={cx('v-hotbar', className)} {...rest}>
      {slots.map((color, index) => (
        <span key={index} className="v-hotbar__slot">
          {color ? <span className="v-hotbar__item" style={{ background: color }} /> : null}
        </span>
      ))}
    </div>
  );
}
