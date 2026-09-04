import type {
  ButtonHTMLAttributes,
  HTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
} from 'react';

import { Icon, type IconName } from './Icon.js';
import { Avatar, Kbd } from './primitives.js';
import { cx } from '../lib/cx.js';

/* -------------------------------------------------------------------------- */
/* TopNav + NavItem                                                           */
/* -------------------------------------------------------------------------- */

/** Props for {@link TopNav}. */
export interface TopNavProps extends HTMLAttributes<HTMLElement> {
  /** The nav tabs. Rendered in order after the mark. */
  children?: ReactNode;
  /** Everything on the right of the flex spacer: search, settings, avatar. */
  right?: ReactNode;
  /** Hide the 30 × 30 VOID mark. */
  hideMark?: boolean;
}

/** The 1300 × 62 window chrome: mark, nav tabs, spacer, search and account controls. */
export function TopNav({
  children,
  right,
  hideMark = false,
  className,
  ...rest
}: TopNavProps): React.ReactElement {
  return (
    <nav className={cx('v-topnav', className)} {...rest}>
      {hideMark ? null : <span className="v-topnav__mark" aria-hidden="true" />}
      <span className="v-topnav__gap" />
      <div className="v-topnav__tabs">{children}</div>
      <span className="v-spacer" />
      {right}
    </nav>
  );
}

/** Props for {@link NavItem}. */
export interface NavItemProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Whether this is the current screen. */
  active?: boolean;
  /** The 14px leading glyph. */
  icon?: IconName;
}

/**
 * One nav tab: `Play`, `Mods`, `Cosmetics`, `Servers`, `Friends`.
 *
 * *active* takes the `--surface-3` fill, a `--hairline` border and the raised bevel;
 * *default* is transparent with a `--text-secondary` label.
 */
export function NavItem({
  active = false,
  icon,
  className,
  children,
  type = 'button',
  ...rest
}: NavItemProps): React.ReactElement {
  return (
    <button
      type={type}
      aria-current={active ? 'page' : undefined}
      className={cx('v-navitem', active && 'v-navitem--active', className)}
      {...rest}
    >
      {icon ? (
        <span className="v-navitem__icon">
          <Icon name={icon} size={14} />
        </span>
      ) : null}
      {children}
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* SearchBar                                                                  */
/* -------------------------------------------------------------------------- */

/** Props for {@link SearchBar}. */
export interface SearchBarProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'onChange'> {
  /** Current query. Controlled when given together with `onChange`. */
  value?: string;
  /** Called with the new query text. */
  onChange?: (value: string) => void;
  /**
   * - `nav` — the 300 × 34 launcher search with the accent dot and the `⌘K` hint.
   * - `panel` — the 230 × 34 field inside a Panel header, with a search glyph.
   */
  variant?: 'nav' | 'panel';
  /** Narrow the panel variant to 200px, as the Friends panel does. */
  narrow?: boolean;
  /**
   * The keyboard hint chip at the right of the nav variant. Defaults to `⌘K`; pass
   * null to drop it.
   */
  hint?: ReactNode;
}

/**
 * The search field.
 *
 * @example The launcher's own search:
 * ```tsx
 * <SearchBar placeholder="Ask VOID anything" value={q} onChange={setQ} />
 * ```
 *
 * @example Inside a panel header:
 * ```tsx
 * <SearchBar variant="panel" placeholder="Search or paste an address" />
 * ```
 */
export function SearchBar({
  value,
  onChange,
  variant = 'nav',
  narrow = false,
  hint = <Kbd flavour="nav">⌘K</Kbd>,
  placeholder = 'Ask VOID anything',
  className,
  ...rest
}: SearchBarProps): React.ReactElement {
  return (
    <label
      className={cx(
        'v-searchbar',
        variant === 'panel' && 'v-searchbar--panel',
        narrow && 'v-searchbar--narrow',
        className,
      )}
    >
      {variant === 'nav' ? (
        <span className="v-searchbar__dot" aria-hidden="true" />
      ) : (
        <span className="v-searchbar__icon" aria-hidden="true">
          <Icon name="search" size={13} />
        </span>
      )}
      <input
        type="search"
        className="v-searchbar__input"
        placeholder={placeholder}
        value={value}
        // A value with no handler is a display-only field, not a mistake.
        readOnly={value !== undefined && !onChange}
        onChange={onChange ? (event) => onChange(event.target.value) : undefined}
        {...rest}
      />
      {variant === 'nav' ? hint : null}
    </label>
  );
}

/* -------------------------------------------------------------------------- */
/* Dock + its children                                                        */
/* -------------------------------------------------------------------------- */

/** The launcher dock: the rounded bar that holds identity, pickers and Launch. */
export function Dock({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cx('v-dock', className)} {...rest} />;
}

/** Props for {@link PlayerChip}. */
export interface PlayerChipProps extends HTMLAttributes<HTMLDivElement> {
  /** Player name, e.g. `Searge`. */
  name: string;
  /** The line under the name, e.g. `Lvl 42`. */
  level?: ReactNode;
  /** Avatar image URL. Falls back to the first two letters of the name. */
  avatarSrc?: string;
  /** Avatar edge length. Defaults to the dock's 44px. */
  avatarSize?: number;
}

/** Avatar + name + level, as the left end of the launcher dock. */
export function PlayerChip({
  name,
  level,
  avatarSrc,
  avatarSize = 44,
  className,
  ...rest
}: PlayerChipProps): React.ReactElement {
  return (
    <div className={cx('v-playerchip', className)} {...rest}>
      <Avatar name={name} src={avatarSrc} size={avatarSize} />
      <span className="v-playerchip__text">
        <span className="v-playerchip__name">{name}</span>
        {level === undefined ? null : <span className="v-playerchip__level">{level}</span>}
      </span>
    </div>
  );
}

/** Props shared by {@link LoadoutPicker} and {@link VersionPicker}. */
export interface PickerProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'value'> {
  /** The small uppercase line — `LOADOUT`, `VERSION`. */
  eyebrow: ReactNode;
  /** The value line — `Sword PvP`, `1.8.9`. */
  value: ReactNode;
  /** A 14px leading glyph. The loadout pill carries the loadout's own icon. */
  icon?: IconName;
  /** Whether the menu this pill opens is showing — takes the accent border. */
  open?: boolean;
  /** Set both lines in DM Mono, as the version pill does. */
  mono?: boolean;
  /** Render as a full-width row, as the in-game Party pane does. */
  row?: boolean;
  /** Hide the trailing chevron. */
  hideChevron?: boolean;
}

/** The eyebrow + value + chevron pill. Both dock pickers are this component. */
function Picker({
  eyebrow,
  value,
  icon,
  open = false,
  mono = false,
  row = false,
  hideChevron = false,
  className,
  type = 'button',
  ...rest
}: PickerProps): React.ReactElement {
  return (
    <button
      type={type}
      aria-haspopup="menu"
      aria-expanded={open}
      className={cx(
        'v-pill',
        mono && 'v-pill--mono',
        open && 'v-pill--open',
        row && 'v-pill--row',
        className,
      )}
      {...rest}
    >
      {icon ? (
        <span className="v-pill__icon">
          <Icon name={icon} size={14} />
        </span>
      ) : null}
      <span className="v-pill__text">
        <span className="v-pill__eyebrow">{eyebrow}</span>
        <span className="v-pill__value">{value}</span>
      </span>
      {row ? <span className="v-spacer" /> : null}
      {hideChevron ? null : (
        <span className="v-pill__chevron">
          <Icon name="chevron-down" size={14} />
        </span>
      )}
    </button>
  );
}

/**
 * `LOADOUT / Sword PvP` — the dock pill that opens the loadout menu.
 *
 * @example
 * ```tsx
 * <LoadoutPicker eyebrow="LOADOUT" value="Sword PvP" icon="sword" onClick={open} />
 * ```
 */
export function LoadoutPicker({
  icon = 'sword',
  eyebrow = 'LOADOUT',
  ...rest
}: Partial<PickerProps> & Pick<PickerProps, 'value'>): React.ReactElement {
  return <Picker icon={icon} eyebrow={eyebrow} {...rest} />;
}

/** `VERSION / 1.8.9` — the same pill without a leading icon and set in DM Mono. */
export function VersionPicker({
  eyebrow = 'VERSION',
  mono = true,
  ...rest
}: Partial<PickerProps> & Pick<PickerProps, 'value'>): React.ReactElement {
  return <Picker eyebrow={eyebrow} mono={mono} {...rest} />;
}

/* -------------------------------------------------------------------------- */
/* LaunchButton                                                               */
/* -------------------------------------------------------------------------- */

/** What the launcher is doing right now. */
export type LaunchState = 'idle' | 'launching' | 'running';

/** Props for {@link LaunchButton}. */
export interface LaunchButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
  /**
   * - `idle` — the accent CTA reading `Launch`, with the `⌘↵` hint inside it.
   * - `launching` — busy; the play glyph becomes a spinner and the label reads `Launching…`.
   * - `running` — the game is up; the button becomes a raised `Playing` / stop control.
   */
  state?: LaunchState;
  /** Override the label. Defaults to the copy for the current state. */
  label?: ReactNode;
  /** The keyboard hint chip. Defaults to `⌘↵`, shown only when idle. */
  kbd?: ReactNode;
}

const LAUNCH_LABEL: Record<LaunchState, string> = {
  idle: 'Launch',
  launching: 'Launching…',
  running: 'Playing',
};

/**
 * The dock's primary action: 232 × 56, accent, with the `⌘↵` chip inside the button.
 *
 * @example
 * ```tsx
 * <LaunchButton state={state} onClick={launch} />
 * ```
 */
export function LaunchButton({
  state = 'idle',
  label,
  kbd = <Kbd flavour="accent">⌘↵</Kbd>,
  className,
  type = 'button',
  ...rest
}: LaunchButtonProps): React.ReactElement {
  return (
    <button
      type={type}
      aria-busy={state === 'launching' || undefined}
      className={cx(
        'v-launch',
        state === 'launching' && 'v-launch--launching',
        state === 'running' && 'v-launch--running',
        className,
      )}
      {...rest}
    >
      <span className="v-launch__glyph">
        {state === 'launching' ? (
          <span className="v-launch__spinner" />
        ) : state === 'running' ? (
          <Icon name="check" size={18} />
        ) : (
          <Icon name="play" size={18} />
        )}
      </span>
      <span className="v-launch__label">{label ?? LAUNCH_LABEL[state]}</span>
      {state === 'idle' ? kbd : null}
    </button>
  );
}

/* -------------------------------------------------------------------------- */
/* FriendsOnline                                                              */
/* -------------------------------------------------------------------------- */

/** One head in the {@link FriendsOnline} stack. */
export interface FriendHead {
  /** Friend name — the alt text and the fallback initials. */
  name: string;
  /** Avatar image URL. */
  src?: string;
}

/** Props for {@link FriendsOnline}. */
export interface FriendsOnlineProps extends HTMLAttributes<HTMLDivElement> {
  /** The heads to stack, in order. At most three are drawn. */
  friends: readonly FriendHead[];
  /**
   * The count label. Defaults to `<n> online` using the total, which is what the
   * design shows even when more friends are online than heads are drawn.
   */
  label?: ReactNode;
  /** Total online count, when it differs from `friends.length`. */
  total?: number;
}

/** Three overlapped 32px heads in an 80px box, then `3 online`. */
export function FriendsOnline({
  friends,
  label,
  total,
  className,
  ...rest
}: FriendsOnlineProps): React.ReactElement {
  const count = total ?? friends.length;
  return (
    <div className={cx('v-friends', className)} {...rest}>
      <span className="v-friends__heads">
        {friends.slice(0, 3).map((friend, index) => (
          <span
            key={friend.name}
            className="v-friends__head"
            style={{ left: index * 24, zIndex: 3 - index }}
          >
            <Avatar name={friend.name} src={friend.src} size={32} />
          </span>
        ))}
      </span>
      <span className="v-friends__label">{label ?? `${count} online`}</span>
    </div>
  );
}
