import type { HTMLAttributes, ReactNode } from 'react';

import { Avatar, Badge, Button, StatusDot } from './primitives.js';
import { cx } from '../lib/cx.js';

/**
 * The list rows of the Servers, Friends and Party frames, plus the cosmetics card.
 *
 * All four rows share one shell — a full-width card at `--card-bg` with `--shadow-tile`
 * and `--inset-card` — and differ only in height, radius and leading-avatar size. They
 * are separate components rather than one configurable row because the frames give each
 * its own anatomy, and a single "Row" with six booleans reads worse at every call site.
 */

/* -------------------------------------------------------------------------- */
/* ServerRow                                                                  */
/* -------------------------------------------------------------------------- */

/** Props for {@link ServerRow}. */
export interface ServerRowProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onSelect'> {
  /** Display name, e.g. `Hypixel`. */
  name: ReactNode;
  /** The address under it, e.g. `mc.hypixel.net`. */
  address?: ReactNode;
  /** The player count, e.g. `24,118 online`. */
  players?: ReactNode;
  /** Round-trip time in milliseconds. */
  ping?: number;
  /** At or above this the ping reads amber. Defaults to the mod's 100ms crossover. */
  pingWarnMs?: number;
  /** Server icon image URL. Falls back to a monogram from the name. */
  iconSrc?: string;
  /** Whether this is the row the detail pane is showing. */
  selected?: boolean;
  /** Called when the row body is clicked. */
  onSelect?: () => void;
  /** Called when Join is pressed. Omit to drop the button. */
  onJoin?: () => void;
  /** Override the Join label. */
  joinLabel?: ReactNode;
}

/**
 * One 580 × 62 row in the server list.
 *
 * Selection is a 1.5px accent border, and the Join chip goes accent on the selected
 * row — the only row where joining is the obvious next action.
 */
export function ServerRow({
  name,
  address,
  players,
  ping,
  pingWarnMs = 100,
  iconSrc,
  selected = false,
  onSelect,
  onJoin,
  joinLabel = 'Join',
  className,
  ...rest
}: ServerRowProps): React.ReactElement {
  const warn = ping !== undefined && ping >= pingWarnMs;
  const monogram = typeof name === 'string' ? name.slice(0, 2).toUpperCase() : '';
  return (
    <div
      className={cx('v-row', 'v-row--server', selected && 'v-row--selected', className)}
      {...rest}
    >
      {onSelect ? (
        <button
          type="button"
          className="v-modtile__select"
          aria-pressed={selected}
          aria-label={typeof name === 'string' ? name : 'Server'}
          onClick={onSelect}
        />
      ) : null}
      <span className="v-row__icon">
        {iconSrc ? (
          <img src={iconSrc} alt="" width={40} height={40} />
        ) : (
          monogram
        )}
      </span>
      <span className="v-row__text">
        <span className="v-row__name">{name}</span>
        {address ? <span className="v-row__meta">{address}</span> : null}
      </span>
      {players ? <span className="v-row__aside">{players}</span> : null}
      {ping === undefined ? null : (
        <span className={cx('v-row__ping', warn && 'v-row__ping--warn')}>
          <StatusDot tone={warn ? 'warn' : 'ok'} size={6} />
          {ping} ms
        </span>
      )}
      {onJoin ? (
        <Button variant={selected ? 'chip-accent' : 'chip'} onClick={onJoin}>
          {joinLabel}
        </Button>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* FriendRow                                                                  */
/* -------------------------------------------------------------------------- */

/** What a friend is doing right now. */
export type FriendPresence = 'online' | 'away' | 'offline';

/** Props for {@link FriendRow}. */
export interface FriendRowProps extends HTMLAttributes<HTMLDivElement> {
  /** Friend name. */
  name: string;
  /** The status line, e.g. `Bedwars  ·  Hypixel  ·  2h`. */
  status?: ReactNode;
  /** Drives the presence dot and, when offline, the dimmed treatment. */
  presence?: FriendPresence;
  /** Avatar image URL. */
  avatarSrc?: string;
  /**
   * The trailing action. `Join` for a friend in a match, `Invite` for one in a lobby,
   * `Message` for an offline friend — the frames pick by presence, and so should you.
   */
  action?: ReactNode;
  /** Called when the action is pressed. Ignored when `action` is a node. */
  onAction?: () => void;
  /** Which button treatment the action takes. */
  actionVariant?: 'chip' | 'chip-accent';
}

/** One 580 × 56 row in the friends list. */
export function FriendRow({
  name,
  status,
  presence = 'online',
  avatarSrc,
  action,
  onAction,
  actionVariant = 'chip',
  className,
  ...rest
}: FriendRowProps): React.ReactElement {
  const offline = presence === 'offline';
  return (
    <div
      className={cx('v-row', 'v-row--friend', offline && 'v-row--offline', className)}
      {...rest}
    >
      <Avatar name={name} src={avatarSrc} size={36} presence={presence} dimmed={offline} />
      <span className="v-row__text">
        <span className="v-row__name">{name}</span>
        {status ? <span className="v-row__meta">{status}</span> : null}
      </span>
      {action ? (
        typeof action === 'string' ? (
          <Button variant={actionVariant} onClick={onAction}>
            {action}
          </Button>
        ) : (
          action
        )
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* PartyMemberRow                                                             */
/* -------------------------------------------------------------------------- */

/** Props for {@link PartyMemberRow}. */
export interface PartyMemberRowProps extends HTMLAttributes<HTMLDivElement> {
  /** Member name. */
  name: string;
  /** The line under it, e.g. `Sword PvP  ·  1.8.9`. */
  meta?: ReactNode;
  /** The trailing badge: `LEADER` (accent) or `READY` (green). */
  badge?: ReactNode;
  /** Which tone the badge takes. */
  badgeTone?: 'accent' | 'ok';
  /** Avatar image URL. */
  avatarSrc?: string;
  /**
   * - `overlay` — the 580 × 64 in-game row with a 44px avatar and a filled badge.
   * - `compact` — the launcher pane's lighter r12 row with a 32px avatar and a dot.
   */
  variant?: 'overlay' | 'compact';
}

/** A party member, in the overlay's full row or the launcher pane's compact one. */
export function PartyMemberRow({
  name,
  meta,
  badge,
  badgeTone = 'accent',
  avatarSrc,
  variant = 'overlay',
  className,
  ...rest
}: PartyMemberRowProps): React.ReactElement {
  const compact = variant === 'compact';
  return (
    <div
      className={cx(
        'v-row',
        'v-row--member',
        compact && 'v-row--member-compact',
        className,
      )}
      {...rest}
    >
      <Avatar name={name} src={avatarSrc} size={compact ? 32 : 44} />
      <span className="v-row__text">
        <span className="v-row__name">{name}</span>
        {meta ? <span className="v-row__meta">{meta}</span> : null}
      </span>
      {badge === undefined ? null : compact ? (
        <>
          <StatusDot tone={badgeTone === 'ok' ? 'ok' : 'accent'} size={8} />
          <span className="v-row__aside">{badge}</span>
        </>
      ) : (
        <span className={cx('v-row__badge', badgeTone === 'ok' && 'v-row__badge--ok')}>
          {badge}
        </span>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* InviteRow                                                                  */
/* -------------------------------------------------------------------------- */

/** Props for {@link InviteRow}. */
export interface InviteRowProps extends HTMLAttributes<HTMLDivElement> {
  /** Name of the person to invite. */
  name: string;
  /** The line under it, e.g. `Sword duels  ·  Minemen`. */
  meta?: ReactNode;
  /** Avatar image URL. */
  avatarSrc?: string;
  /** Called when Invite is pressed. */
  onInvite?: () => void;
  /** Override the button label. */
  inviteLabel?: ReactNode;
}

/** One 580 × 54 invite row under the `INVITE` caption. */
export function InviteRow({
  name,
  meta,
  avatarSrc,
  onInvite,
  inviteLabel = 'Invite',
  className,
  ...rest
}: InviteRowProps): React.ReactElement {
  return (
    <div className={cx('v-row', 'v-row--invite', className)} {...rest}>
      <Avatar name={name} src={avatarSrc} size={34} />
      <span className="v-row__text">
        <span className="v-row__name">{name}</span>
        {meta ? <span className="v-row__meta">{meta}</span> : null}
      </span>
      <Button variant="chip" icon="users" onClick={onInvite}>
        {inviteLabel}
      </Button>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* CosmeticCard                                                               */
/* -------------------------------------------------------------------------- */

/** Props for {@link CosmeticCard}. */
export interface CosmeticCardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onSelect'> {
  /** Cosmetic name, e.g. `Void Trail`. */
  name: ReactNode;
  /**
   * The state line: `Equipped` (accent ink), `Owned` (green) or a price
   * (`1,200 coins`, secondary ink).
   */
  state?: ReactNode;
  /** Which treatment the state line takes. */
  stateTone?: 'equipped' | 'owned' | 'price';
  /** The swatch colour — a flat 62 × 98 approximation of the cape. */
  color: string;
  /**
   * The coloured drop glow under the swatch, at the design's `0 10px 24px -4px` with
   * alpha 0.45. Defaults to `color` at that alpha when `color` is an `rgba()`/hex.
   */
  glow?: string;
  /** Show the `NEW` badge — items added this week. */
  isNew?: boolean;
  /** Whether this is the equipped / selected item: a 1.5px accent border. */
  selected?: boolean;
  /** Called when the card is clicked. */
  onSelect?: () => void;
}

/**
 * One 186 × 216 cosmetics card.
 *
 * The cape is a flat swatch with a coloured glow, not a 3D hang: Ultralight supports 2D
 * transforms only, and the design's overlay treatment is already this 2D approximation
 * (§4). The glow is a `box-shadow`, never `filter: drop-shadow()`, which is [risky].
 */
export function CosmeticCard({
  name,
  state,
  stateTone = 'price',
  color,
  glow,
  isNew = false,
  selected = false,
  onSelect,
  className,
  ...rest
}: CosmeticCardProps): React.ReactElement {
  return (
    <div
      className={cx('v-cosmetic', selected && 'v-cosmetic--selected', className)}
      {...rest}
    >
      {onSelect ? (
        <button
          type="button"
          className="v-modtile__select"
          aria-pressed={selected}
          aria-label={typeof name === 'string' ? name : 'Cosmetic'}
          onClick={onSelect}
        />
      ) : null}
      <div className="v-cosmetic__preview">
        {isNew ? (
          <Badge tone="solid" className="v-cosmetic__badge">
            New
          </Badge>
        ) : null}
        <span className="v-cosmetic__hanger" />
        <span
          className="v-cosmetic__swatch"
          style={{
            background: color,
            boxShadow: `0 10px 24px -4px ${glow ?? color}`,
          }}
        />
      </div>
      <div className="v-cosmetic__body">
        <span className="v-cosmetic__title">{name}</span>
        {state === undefined ? null : (
          <span
            className={cx(
              'v-cosmetic__state',
              stateTone === 'equipped' && 'v-cosmetic__state--equipped',
              stateTone === 'owned' && 'v-cosmetic__state--owned',
            )}
          >
            {state}
          </span>
        )}
      </div>
    </div>
  );
}
