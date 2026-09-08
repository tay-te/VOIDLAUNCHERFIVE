import type { ButtonHTMLAttributes, HTMLAttributes, ImgHTMLAttributes, ReactNode } from 'react';

import { Icon, type IconName } from './Icon.js';
import { cx } from '../lib/cx.js';

/* -------------------------------------------------------------------------- */
/* Button                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * The button variants (`design/quiet-cell-system.md` §4).
 *
 * Three treatments, and only three:
 *
 * - `primary` — `--text-primary` fill, `--bg-shell` label.
 * - `secondary` — 1px `rgba(255,255,255,.14)`, no fill.
 * - `ghost` — label only, no box, no underline.
 *
 * Note that the primary action is *not* the accent. Colour marks the live value or the
 * selected item and nothing else (§1), so a button — which is neither — takes the ink
 * itself as its fill.
 *
 * `accent`, `raised` and `text` are the pre-contract names for the same three and are
 * still accepted; `chip` and `chip-accent` are those treatments at chip size, which is
 * a size and not a fourth treatment.
 */
export type ButtonVariant =
  | 'primary'
  | 'secondary'
  | 'ghost'
  | 'chip'
  | 'chip-accent'
  | 'accent'
  | 'raised'
  | 'text';

/**
 * Which classes each variant emits.
 *
 * Both names are emitted for the aliased variants — `v-btn--primary v-btn--accent` —
 * so a stylesheet or a test that still selects on the old name keeps working while the
 * rules themselves are written against the contract's.
 */
const BUTTON_CLASS: Record<ButtonVariant, string> = {
  primary: 'v-btn--primary v-btn--accent',
  accent: 'v-btn--primary v-btn--accent',
  secondary: 'v-btn--secondary v-btn--raised',
  raised: 'v-btn--secondary v-btn--raised',
  ghost: 'v-btn--ghost',
  text: 'v-btn--ghost v-btn--text',
  chip: 'v-btn--chip',
  'chip-accent': 'v-btn--chip v-btn--primary v-btn--accent',
};

/** Props for {@link Button}. */
export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /**
   * Which of the three treatments this is. See {@link ButtonVariant}.
   */
  variant?: ButtonVariant;
  /** Stretch to the container's width, as card and pane CTAs do. */
  block?: boolean;
  /** A leading icon. */
  icon?: IconName;
  /** A trailing keyboard hint, e.g. `⌘↵`. */
  kbd?: ReactNode;
}

/**
 * Every button in the design that is not one of the bespoke controls
 * ({@link LaunchButton}, {@link EditPositionButton}, {@link IconButton}).
 */
export function Button({
  variant = 'secondary',
  block = false,
  icon,
  kbd,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps): React.ReactElement {
  return (
    <button
      type={type}
      className={cx('v-btn', BUTTON_CLASS[variant] ?? BUTTON_CLASS.secondary, block && 'v-btn--block', className)}
      {...rest}
    >
      {icon ? <Icon name={icon} size={14} /> : null}
      {children}
      {kbd}
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* IconButton                                                                 */
/* -------------------------------------------------------------------------- */

/** Props for {@link IconButton}. */
export interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  /** Which glyph to draw. */
  icon: IconName;
  /**
   * - `default` — 34 × 34, the top-nav settings button.
   * - `close` — 32 × 32 raised, the overlay panel's close X.
   * - `dock` — 44 × 44 raised, the settings button at the end of the launcher dock.
   */
  size?: 'default' | 'close' | 'dock';
  /** Accessible name. Required: the button has no visible label. */
  label: string;
}

/** A square button whose only content is one icon. */
export function IconButton({
  icon,
  size = 'default',
  label,
  className,
  type = 'button',
  ...rest
}: IconButtonProps): React.ReactElement {
  const glyph = size === 'dock' ? 16 : 14;
  return (
    <button
      type={type}
      aria-label={label}
      title={label}
      className={cx(
        'v-icon-btn',
        size === 'close' && 'v-icon-btn--close',
        size === 'dock' && 'v-icon-btn--dock',
        className,
      )}
      {...rest}
    >
      <Icon name={icon} size={glyph} />
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* Card, Panel                                                                */
/* -------------------------------------------------------------------------- */

/** Props for {@link Card}. */
export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Draw the 1.5px border, in this item's hue, that marks a selected card. */
  selected?: boolean;
  /** Lift one fill step on hover (§5.1). For a card the pointer can act on. */
  hoverable?: boolean;
}

/**
 * The generic tile / pane / card surface — the CARD step of the ramp (§1), opaque, with
 * a 1px white-alpha rim and no shadow. Pass `hoverable` for §5.1's lift: hovering takes
 * it one step up the ramp, to RAISED. Never a glow.
 */
export function Card({ selected, hoverable, className, ...rest }: CardProps): React.ReactElement {
  return (
    <div
      className={cx(
        'v-card',
        hoverable && 'v-card--hoverable',
        selected && 'v-card--selected',
        className,
      )}
      {...rest}
    />
  );
}

/** Props for {@link Panel}. */
export interface PanelProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  /** Panel title, e.g. `Mods`. */
  title?: ReactNode;
  /** A line under the title, e.g. the Loadouts definition line. */
  subtitle?: ReactNode;
  /** Controls that sit on the header band at y 19 — search, tabs, buttons. */
  headerRight?: ReactNode;
  /** The mono footer hint at the bottom-left. */
  footer?: ReactNode;
  /**
   * `launcher` is 960 × 596 and has no close button; `overlay` is 960 × 600 and does.
   */
  surface?: 'launcher' | 'overlay';
  /** Called when the overlay panel's close X is pressed. Adds the button. */
  onClose?: () => void;
  /** Play the 2D open transition (opacity + translateY + scale; never a 3D flip). */
  animate?: boolean;
}

/** The 960px panel that carries every launcher and overlay screen. */
export function Panel({
  title,
  subtitle,
  headerRight,
  footer,
  surface = 'launcher',
  onClose,
  animate = false,
  className,
  children,
  ...rest
}: PanelProps): React.ReactElement {
  return (
    <div
      className={cx(
        'v-panel',
        surface === 'overlay' && 'v-panel--overlay',
        animate && 'v-panel--enter',
        className,
      )}
      {...rest}
    >
      {title || headerRight || onClose ? (
        <div className="v-panel__header">
          {title ? <h2 className="v-panel__title">{title}</h2> : null}
          {headerRight}
          {onClose ? (
            <IconButton icon="close" size="close" label="Close" onClick={onClose} />
          ) : null}
        </div>
      ) : null}
      {subtitle ? <p className="v-panel__subtitle">{subtitle}</p> : null}
      <div className="v-panel__body">{children}</div>
      {footer ? (
        <div className="v-panel__footer">
          <span className="v-hint">{footer}</span>
        </div>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Kbd, Tag, Badge                                                            */
/* -------------------------------------------------------------------------- */

/** Props for {@link Kbd}. */
export interface KbdProps extends HTMLAttributes<HTMLElement> {
  /**
   * - `nav` — `--surface-2`, uppercase; the `⌘K` badge in the launcher search.
   * - `accent` — on an ink fill; the `⌘↵` chip inside the Launch button.
   * - `palette` — `--tint-07`; the trailing hints in the quick palette and footers.
   */
  flavour?: 'nav' | 'accent' | 'palette';
}

/** A keyboard hint chip. */
export function Kbd({
  flavour = 'nav',
  className,
  children,
  ...rest
}: KbdProps): React.ReactElement {
  return (
    <kbd className={cx('v-kbd', `v-kbd--${flavour}`, className)} {...rest}>
      {children}
    </kbd>
  );
}

/** A ModTile's category tag: `HUD` `PVP` `VISUAL` `UTILITY`. */
export function Tag({ className, children, ...rest }: HTMLAttributes<HTMLSpanElement>) {
  return (
    <span className={cx('v-tag', className)} {...rest}>
      {children}
    </span>
  );
}

/** Props for {@link Badge}. */
export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  /**
   * - `accent` — `ACTIVE`, `LEADER`: a white-alpha ground with the hue as the ink,
   *   because both mark the selected item.
   * - `ok` — `READY`.
   * - `solid` — `NEW`, on an ink fill. Monochrome: a fact about a row is not a live
   *   value, and colour is reserved for one (§1).
   */
  tone?: 'accent' | 'ok' | 'solid';
}

/** A small filled status badge. */
export function Badge({
  tone = 'accent',
  className,
  children,
  ...rest
}: BadgeProps): React.ReactElement {
  return (
    <span
      className={cx('v-badge', tone === 'ok' && 'v-badge--ok', tone === 'solid' && 'v-badge--solid', className)}
      {...rest}
    >
      {children}
    </span>
  );
}

/* -------------------------------------------------------------------------- */
/* Avatar                                                                     */
/* -------------------------------------------------------------------------- */

/** Props for {@link Avatar}. */
export interface AvatarProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'width' | 'height'> {
  /** Edge length in pixels. The design uses 32, 34, 36 and 44. */
  size?: number;
  /** Corner radius token value. Defaults to the design's radius for the given size. */
  radius?: number;
  /** Player name — used for the alt text and for the initial when there is no image. */
  name: string;
  /** Presence dot. Omit for no dot. */
  presence?: 'online' | 'away' | 'offline';
  /** Draw the row at 50% opacity, as offline friend rows do. */
  dimmed?: boolean;
}

/** Radii the design pairs with each avatar size. */
function radiusFor(size: number): number {
  if (size >= 44) return 13;
  if (size >= 36) return 11;
  if (size >= 34) return 10;
  return 10;
}

/** A player head, with an optional presence dot. */
export function Avatar({
  size = 32,
  radius,
  name,
  presence,
  dimmed = false,
  src,
  className,
  style,
  ...rest
}: AvatarProps): React.ReactElement {
  const box = { width: size, height: size, borderRadius: radius ?? radiusFor(size) };
  return (
    <span
      className={cx('v-avatar', dimmed && 'v-avatar--offline', className)}
      style={{ ...box, ...style }}
    >
      {src ? (
        <img src={src} alt={name} width={size} height={size} style={box} {...rest} />
      ) : (
        <span className="v-avatar__fallback" style={{ fontSize: Math.round(size * 0.36) }}>
          {name.slice(0, 2)}
        </span>
      )}
      {presence ? (
        <span
          className={cx(
            'v-avatar__presence',
            presence === 'online' && 'v-avatar__presence--online',
            presence === 'away' && 'v-avatar__presence--away',
          )}
        />
      ) : null}
    </span>
  );
}

/* -------------------------------------------------------------------------- */
/* IconWell, Divider, StatusDot                                               */
/* -------------------------------------------------------------------------- */

/** Props for {@link IconWell}. */
export interface IconWellProps extends HTMLAttributes<HTMLSpanElement> {
  /** Which glyph sits inside. */
  icon: IconName;
  /** One of the design's four well sizes. */
  size?: 24 | 30 | 34 | 44;
  /** Tint the well and take the hue ink — how an *enabled* mod reads. */
  on?: boolean;
  /** Fill the well with `var(--hue, var(--accent))` — how an *active* loadout card reads. */
  solid?: boolean;
}

/** The rounded square that holds an icon: 24 / 30 / 34 / 44px at r 7 / 8 / 10 / 13. */
export function IconWell({
  icon,
  size = 34,
  on = false,
  solid = false,
  className,
  ...rest
}: IconWellProps): React.ReactElement {
  const glyph = size >= 44 ? 22 : size >= 34 ? 16 : size >= 30 ? 16 : 13;
  return (
    <span
      className={cx(
        'v-icon-well',
        `v-icon-well--${size}`,
        on && 'v-icon-well--on',
        solid && 'v-icon-well--solid',
        className,
      )}
      {...rest}
    >
      <Icon name={icon} size={glyph} />
    </span>
  );
}

/** A 1px vertical rule: 36px tall in the dock, 18px in the HUD-editor toolbar. */
export function Divider({
  toolbar = false,
  className,
  ...rest
}: HTMLAttributes<HTMLSpanElement> & { toolbar?: boolean }): React.ReactElement {
  return <span className={cx('v-divider', toolbar && 'v-divider--toolbar', className)} {...rest} />;
}

/** Props for {@link StatusDot}. */
export interface StatusDotProps extends HTMLAttributes<HTMLSpanElement> {
  /**
   * `ok` online/ready, `warn` degraded, `bad` failing, `muted` offline, `accent` selected.
   *
   * `bad` was added for `PingChip`, which had three thresholds and two tones: with a valid
   * config `bad_ms` is always above `good_ms`, so "at or above bad" and "above good" both came
   * out amber and the `bad_ms` setting could not change a pixel. A third tone is what makes it
   * a setting rather than a stored number.
   */
  tone?: 'ok' | 'warn' | 'bad' | 'muted' | 'accent';
  /** Diameter in pixels. The design uses 6 to 11. */
  size?: number;
}

/** The small status circle used by the eyebrow, ping chips and party rows. */
export function StatusDot({
  tone = 'ok',
  size = 7,
  className,
  style,
  ...rest
}: StatusDotProps): React.ReactElement {
  return (
    <span
      className={cx(
        'v-dot',
        tone === 'ok' && 'v-dot--ok',
        tone === 'warn' && 'v-dot--warn',
        tone === 'bad' && 'v-dot--bad',
        tone === 'accent' && 'v-dot--accent',
        className,
      )}
      style={{ width: size, height: size, ...style }}
      {...rest}
    />
  );
}

/* -------------------------------------------------------------------------- */
/* StatusPill                                                                 */
/* -------------------------------------------------------------------------- */

/** Props for {@link StatusPill}. */
export interface StatusPillProps extends HTMLAttributes<HTMLDivElement> {
  /** Colour of the leading LED. */
  tone?: 'ok' | 'warn' | 'muted';
  /** Hide the LED entirely. */
  hideDot?: boolean;
}

/**
 * The eyebrow pill — `VOID PVP · 1.8.9 · HYPIXEL-READY`. The leading dot is a live
 * status LED, so pass `tone="warn"` when the loadout is not Hypixel-ready.
 *
 * @example
 * ```tsx
 * <StatusPill>VOID PVP&nbsp;&nbsp;·&nbsp;&nbsp;1.8.9&nbsp;&nbsp;·&nbsp;&nbsp;HYPIXEL-READY</StatusPill>
 * ```
 */
export function StatusPill({
  tone = 'ok',
  hideDot = false,
  className,
  children,
  ...rest
}: StatusPillProps): React.ReactElement {
  return (
    <div className={cx('v-status-pill', className)} {...rest}>
      {hideDot ? null : <StatusDot tone={tone} size={7} />}
      <span>{children}</span>
    </div>
  );
}
