import type { HTMLAttributes, ReactNode } from 'react';

import { Icon, resolveLoadoutIcon } from './Icon.js';
import { Badge, Button, IconWell } from './primitives.js';
import { cx } from '../lib/cx.js';

/* -------------------------------------------------------------------------- */
/* LoadoutCard                                                                */
/* -------------------------------------------------------------------------- */

/** One `INCLUDES` chip on a loadout card. */
export interface IncludesChip {
  /** The mod's display label, e.g. `Keystrokes`. */
  label: ReactNode;
  /** Override the dot colour. Defaults to the card's active/inactive treatment. */
  dotColor?: string;
}

/** One column of the card's stats footer. */
export interface LoadoutStat {
  /** The number, set in DM Mono — `142`, `4h 20m`, or `—` when unknown. */
  value: ReactNode;
  /** The unit under it — `fps avg`, `played`. */
  unit: ReactNode;
}

/** Props for {@link LoadoutCard}. */
export interface LoadoutCardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onSelect'> {
  /** Loadout name, set in the display face at 22px. */
  name: ReactNode;
  /** The mono line under the name — `24 mods on · Hypixel · 1.8.9`. */
  meta?: ReactNode;
  /**
   * The loadout's `icon` field. Resolved against the shared icon set — `loadout.json`
   * says the value names an icon, not a file path.
   */
  icon?: string;
  /** Whether this is the loadout currently applied. */
  active?: boolean;
  /** The `INCLUDES` chips, wrapped across rows. */
  includes?: readonly IncludesChip[];
  /** The `+ N more` line under the chips. Omit when nothing is hidden. */
  moreCount?: number;
  /** The two stat columns. */
  stats?: readonly LoadoutStat[];
  /**
   * Called when the card's button is pressed. Not called on the active card, whose
   * button is a state rather than an action.
   */
  onSwitch?: () => void;
  /** Override the button label. Defaults to `Active` / `Switch to <name>`. */
  buttonLabel?: ReactNode;
}

/**
 * The 292 × 428 card on the Loadouts frame.
 *
 * The active card takes a 1.5px accent border, `--shadow-card-active` and the `ACTIVE`
 * badge, and its button is a **disabled state** (`Active`, with a check) rather than an
 * action — switching is instant, so there is nothing to press on the loadout you are
 * already using.
 */
export function LoadoutCard({
  name,
  meta,
  icon = 'box',
  active = false,
  includes = [],
  moreCount,
  stats = [],
  onSwitch,
  buttonLabel,
  className,
  children,
  ...rest
}: LoadoutCardProps): React.ReactElement {
  return (
    <div className={cx('v-loadoutcard', active && 'v-loadoutcard--active', className)} {...rest}>
      <div className="v-loadoutcard__header">
        <IconWell icon={resolveLoadoutIcon(icon)} size={44} solid={active} on={!active} />
        <span className="v-loadoutcard__heading">
          <span className="v-loadoutcard__title">{name}</span>
          {meta ? <span className="v-loadoutcard__meta">{meta}</span> : null}
        </span>
        {active ? <Badge>Active</Badge> : null}
      </div>

      {includes.length > 0 ? (
        <>
          <span className="v-caption v-caption--sm">Includes</span>
          <div className="v-loadoutcard__includes">
            {includes.map((chip, index) => (
              <span key={index} className="v-includes-chip">
                <span
                  className="v-includes-chip__dot"
                  style={chip.dotColor ? { background: chip.dotColor } : undefined}
                />
                {chip.label}
              </span>
            ))}
          </div>
          {moreCount ? <span className="v-loadoutcard__more">+ {moreCount} more</span> : null}
        </>
      ) : null}

      {children}
      <span className="v-spacer" />

      {stats.length > 0 ? (
        <div className="v-loadoutcard__stats">
          {stats.map((stat, index) => (
            <span key={index} className="v-loadoutcard__stat">
              <span className="v-loadoutcard__stat-value">{stat.value}</span>
              <span className="v-loadoutcard__stat-unit">{stat.unit}</span>
            </span>
          ))}
        </div>
      ) : null}

      {active ? (
        <Button variant="raised" block icon="check" disabled style={{ color: 'var(--text-secondary)' }}>
          {buttonLabel ?? 'Active'}
        </Button>
      ) : (
        <Button variant="accent" block onClick={onSwitch}>
          {buttonLabel ?? <>Switch to {name}</>}
        </Button>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Pane, StatTile, Sparkline, GroupCaption                                    */
/* -------------------------------------------------------------------------- */

/** Props for {@link Pane}. */
export interface PaneProps extends HTMLAttributes<HTMLDivElement> {
  /** The 16px semibold heading at the top. */
  heading?: ReactNode;
  /** A trailing element on the heading row, e.g. the party's `2 / 4`. */
  headingAside?: ReactNode;
}

/** The 308px right-hand side pane in Servers, Friends and Party. */
export function Pane({
  heading,
  headingAside,
  className,
  children,
  ...rest
}: PaneProps): React.ReactElement {
  return (
    <div className={cx('v-pane', className)} {...rest}>
      {heading ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-8)' }}>
          <span className="v-pane__heading">{heading}</span>
          <span className="v-spacer" />
          {headingAside}
        </div>
      ) : null}
      {children}
    </div>
  );
}

/** Props for {@link StatTile}. */
export interface StatTileProps extends HTMLAttributes<HTMLDivElement> {
  /** The number, in DM Mono. */
  value: ReactNode;
  /** The unit under it. */
  unit: ReactNode;
}

/** `42 ms / ping` — one of the three tiles in the server detail pane. */
export function StatTile({
  value,
  unit,
  className,
  ...rest
}: StatTileProps): React.ReactElement {
  return (
    <div className={cx('v-stattile', className)} {...rest}>
      <span className="v-stattile__value">{value}</span>
      <span className="v-stattile__unit">{unit}</span>
    </div>
  );
}

/** Props for {@link Sparkline}. */
export interface SparklineProps extends HTMLAttributes<HTMLDivElement> {
  /** Bar heights in pixels, oldest first. */
  values: readonly number[];
  /** Index of the current bar, drawn at full opacity. Defaults to the last. */
  currentIndex?: number;
  /** Indices drawn in `--warn` — the spikes. */
  outliers?: readonly number[];
}

/**
 * The 12-bar ping history.
 *
 * Plain divs, never a canvas: Ultralight exposes no WebGL and its 2D canvas is slow, so
 * every chart in this design is built out of positioned boxes (§5).
 */
export function Sparkline({
  values,
  currentIndex,
  outliers = [],
  className,
  ...rest
}: SparklineProps): React.ReactElement {
  const current = currentIndex ?? values.length - 1;
  return (
    <div className={cx('v-sparkline', className)} {...rest}>
      {values.map((height, index) => (
        <span
          key={index}
          className={cx(
            'v-sparkline__bar',
            index === current && 'v-sparkline__bar--current',
            outliers.includes(index) && 'v-sparkline__bar--outlier',
          )}
          style={{ height }}
        />
      ))}
    </div>
  );
}

/** Props for {@link GroupCaption}. */
export interface GroupCaptionProps extends HTMLAttributes<HTMLDivElement> {
  /** The uppercase label, e.g. `ONLINE`. */
  label: ReactNode;
  /** The trailing count, e.g. `· 3` or `· 2 of 4`. */
  count?: ReactNode;
}

/** The caption that splits a list into groups: `ONLINE · 3`, `IN YOUR PARTY · 2 of 4`. */
export function GroupCaption({
  label,
  count,
  className,
  ...rest
}: GroupCaptionProps): React.ReactElement {
  return (
    <div className={cx('v-groupcaption', className)} {...rest}>
      <span className="v-caption">{label}</span>
      {count === undefined ? null : <span className="v-groupcaption__count">{count}</span>}
    </div>
  );
}

/** A back link — `← Mods` — as the Mod settings frame draws it. */
export function BackButton({
  label = 'Mods',
  className,
  ...rest
}: { label?: ReactNode } & HTMLAttributes<HTMLButtonElement>): React.ReactElement {
  return (
    <Button variant="raised" className={cx('v-btn--back', className)} {...rest}>
      <Icon name="arrow-left" size={12} />
      {label}
    </Button>
  );
}
