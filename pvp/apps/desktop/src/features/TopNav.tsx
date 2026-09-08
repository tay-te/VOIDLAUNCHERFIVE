/**
 * The navbar of §7: mark, five nav tabs, search, settings and the profile chip — plus
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
import { useLaunch } from '../stores/launch';
import { useSession } from '../stores/session';
import { SCREENS, SCREEN_LABELS, useUi, type Screen } from '../stores/ui';

export function TopNav() {
  const screen = useUi((s) => s.screen);
  const go = useUi((s) => s.go);
  const openPalette = useUi((s) => s.openPalette);
  const openSettings = useUi((s) => s.openSettings);
  const toggleLog = useUi((s) => s.toggleLog);
  const account = useSession((s) => s.account);
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
              <span className="profile-chip__kind">
                {account ? (account.kind === 'offline' ? 'Offline' : 'Microsoft') : 'Signed out'}
              </span>
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
      {SCREENS.map((id: Screen) => (
        <NavItem key={id} active={screen === id} onClick={() => go(id)}>
          {SCREEN_LABELS[id]}
        </NavItem>
      ))}
    </TopNavBar>
  );
}
