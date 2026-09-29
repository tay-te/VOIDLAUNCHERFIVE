/**
 * The navbar of §7: mark, the three v1 tabs, search, the friends button and the
 * profile chip — plus
 * the window controls a frameless window needs. 80px tall, sitting on `--bg-shell`,
 * with the content panel inset below it.
 *
 * `TopNav`, `NavItem`, `SearchBar` and `Avatar` are `@void/ui`'s;
 * the only things added here are the two nav marks the shared set has no reason to
 * carry (`local/glyphs`) and the Tauri window buttons, which exist because this bundle
 * runs in a window and the in-game one does not.
 *
 * The whole bar is the drag region (`data-tauri-drag-region`), with the interactive
 * children opting out. That is the standard Tauri 2 pattern and the reason the window
 * can be `decorations: false` without becoming unmovable.
 */

import { Avatar, NavItem, SearchBar, TopNav as TopNavBar } from '@void/ui';

import {
  MarkGlyph,
  MaximiseGlyph,
  MinimiseGlyph,
  TerminalGlyph,
  WindowCloseGlyph,
} from '../local/glyphs';
import { invoke, IS_TAURI } from '../local/tauri';
import { sortFriends, useFriends } from '../stores/friends';
import { useLaunch } from '../stores/launch';
import { formatPoints, useProgress } from '../stores/progress';
import { useSession } from '../stores/session';
import { NAV_SCREENS, SCREEN_LABELS, useUi } from '../stores/ui';

/**
 * Who is online, as a three-avatar stack and a count. It opens the friends drawer —
 * the only way in, so the button carries the pending-request badge too.
 */
function FriendsButton() {
  const friends = useFriends((s) => s.friends);
  const incoming = useFriends((s) => s.requests.filter((r) => r.direction === 'incoming').length);
  const open = useUi((s) => s.friendsOpen);
  const toggle = useUi((s) => s.toggleFriends);

  const online = sortFriends(friends).filter((f) => f.online);
  const label = `Friends — ${online.length} online${
    incoming > 0 ? `, ${incoming} request${incoming === 1 ? '' : 's'}` : ''
  }`;

  return (
    <button
      type="button"
      className={`friends-btn${open ? ' is-open' : ''}`}
      aria-label={label}
      aria-expanded={open}
      aria-controls="friends-drawer"
      title={label}
      onClick={toggle}
    >
      {online.length > 0 ? (
        <span className="friends-btn__stack" aria-hidden="true">
          {online.slice(0, 3).map((f) => (
            <Avatar key={f.id} name={f.name} size={24} />
          ))}
        </span>
      ) : null}
      <span className={`dot${online.length > 0 ? ' is-ok' : ''}`} aria-hidden="true" />
      <span className="friends-btn__count tnum">{online.length}</span>
      {incoming > 0 ? (
        <span className="friends-btn__badge tnum" aria-hidden="true">
          {incoming > 9 ? '9+' : incoming}
        </span>
      ) : null}
    </button>
  );
}

export function TopNav() {
  const screen = useUi((s) => s.screen);
  const go = useUi((s) => s.go);
  const openPalette = useUi((s) => s.openPalette);
  const openSettings = useUi((s) => s.openSettings);
  const toggleLog = useUi((s) => s.toggleLog);
  const account = useSession((s) => s.account);
  const level = useProgress((s) => s.level);
  const points = useProgress((s) => s.points);
  const logLines = useLaunch((s) => s.log.length);

  return (
    <TopNavBar
      hideMark
      data-tauri-drag-region
      right={
        <>
          <SearchBar
            placeholder="Search VOID"
            value=""
            hint={<span className="v-kbd v-kbd--nav">⌘K</span>}
            aria-label="Search VOID"
            onMouseDown={(event) => {
              event.preventDefault();
              openPalette();
            }}
            onFocus={(event) => {
              event.currentTarget.blur();
              openPalette();
            }}
          />

          {/* The log button appears once the JVM has said anything — the frames show
              one control here, and an empty log is nothing to open. */}
          {logLines > 0 ? (
            <button
              type="button"
              className="v-icon-btn"
              aria-label={`Game log — ${logLines} lines`}
              title={`Game log — ${logLines} lines`}
              onClick={toggleLog}
            >
              <TerminalGlyph size={14} />
            </button>
          ) : null}

          <FriendsButton />

          {/* The profile chip. §6 settles the word: "Profile" is the player's account
              and nothing else — a bundle of mods is a Loadout, and the dock band below
              is where that lives.

              It is also the way into Settings, which is why there is no gear beside it:
              all three frames put exactly two controls on the right of the bar, the
              300px search and this chip, and a separate gear pushed the search 30px off
              the frame's x. ⌘, and the ⌘K palette reach Settings too. */}
          <button
            type="button"
            className="profile-chip"
            onClick={openSettings}
            title="Settings"
            aria-label={account ? `Settings — signed in as ${account.name}` : 'Sign in'}
          >
            <Avatar name={account?.name ?? 'VOID'} src={account?.skin_url ?? undefined} size={28} />
            <span className="profile-chip__text">
              <span className="profile-chip__name">{account?.name ?? 'Sign in'}</span>
              {account ? (
                <span className="profile-chip__level tnum">
                  Lv {level} · <span className="profile-chip__points">{formatPoints(points)} pts</span>
                </span>
              ) : (
                <span className="profile-chip__kind">Signed out</span>
              )}
            </span>
          </button>

          {IS_TAURI ? (
            <span className="wincontrols">
              <button
                type="button"
                className="wincontrols__btn"
                aria-label="Minimise"
                onClick={() => void invoke('window_minimize')}
              >
                <MinimiseGlyph size={13} />
              </button>
              <button
                type="button"
                className="wincontrols__btn"
                aria-label="Maximise"
                onClick={() => void invoke('window_toggle_maximize')}
              >
                <MaximiseGlyph size={12} />
              </button>
              <button
                type="button"
                className="wincontrols__btn wincontrols__btn--close"
                aria-label="Hide to tray"
                onClick={() => void invoke('window_close')}
              >
                <WindowCloseGlyph size={13} />
              </button>
            </span>
          ) : null}
        </>
      }
    >
      {/* The lockup. It rides in the tab group rather than the package's own
          `v-topnav__mark`, which has no room beside it for the wordmark the frames
          draw — see `.brand` in `local/app.css`. */}
      <span className="brand" aria-hidden="true">
        <span className="brand__mark">
          <MarkGlyph size={16} />
        </span>
        <span className="brand__word">VOID</span>
      </span>

      {/* Text only. The frames carry no glyph on a nav tab. */}
      {NAV_SCREENS.map((id) => (
        <NavItem key={id} active={screen === id} onClick={() => go(id)}>
          {SCREEN_LABELS[id]}
        </NavItem>
      ))}
    </TopNavBar>
  );
}
