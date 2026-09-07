import type { HTMLAttributes, ReactNode } from 'react';

import { StatusDot } from './primitives.js';
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
  const tone = unknown ? 'muted' : ping >= badMs ? 'warn' : ping <= goodMs ? 'ok' : 'warn';
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
}

/** `12 | 9 CPS` — the left figure in the accent ink, the right in the primary. */
export function CpsChip({
  left,
  right,
  mode = 'both',
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
      <span className="v-cpschip__unit">CPS</span>
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
      {showSpacebar ? (
        <div className="v-keystrokes__row">{key('␣', keys.space, 'v-keystrokes__key--space')}</div>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Crosshair, Hotbar                                                          */
/* -------------------------------------------------------------------------- */

/**
 * The 18 × 18 crosshair.
 *
 * In production the crosshair is drawn in GL, not HTML, because it must sit at the
 * exact pixel centre (§3). This is the stand-in the HUD editor and the gallery draw.
 */
export function Crosshair({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx('v-crosshair', className)} aria-hidden="true" {...rest} />;
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
