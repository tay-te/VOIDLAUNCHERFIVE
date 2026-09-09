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
      <span className="v-hudchip__value">{facing}</span>
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
}

/** `X 118   Y 64   Z -212   ·   NE`, or the same three axes stacked. */
export function CoordsChip({
  x,
  y,
  z,
  direction,
  decimals = 0,
  layout = 'inline',
  variant = 'compact',
  dimmed = false,
  className,
  ...rest
}: CoordsChipProps): React.ReactElement {
  const format = (value: number): string => value.toFixed(decimals);
  const stacked = layout === 'stacked';
  return (
    <div
      className={chipClass(
        variant,
        dimmed,
        cx('v-coordschip', stacked && 'v-coordschip--stacked', className),
      )}
      {...rest}
    >
      <span className="v-coordschip__axis">X {format(x)}</span>
      <span className="v-coordschip__axis">Y {format(y)}</span>
      <span className="v-coordschip__axis">Z {format(z)}</span>
      {direction ? (
        <>
          {/* The separator is a horizontal device. Stacked, the line break already
              separates, and a `·` on a line of its own is a mark that means nothing. */}
          {stacked ? null : <span className="v-hudchip__aside">·</span>}
          <span className="v-coordschip__axis">{direction}</span>
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
}

/** `12 | 9 CPS` — the left figure in the accent ink, the right in the primary. */
export function CpsChip({
  left,
  right,
  mode = 'both',
  showLabel = true,
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
