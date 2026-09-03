/**
 * The 1300 × 62 chrome of every frame: mark, five nav tabs, "Ask VOID anything ⌘K",
 * settings, avatar — plus the window controls a frameless window needs.
 *
 * `TopNav`, `NavItem`, `SearchBar`, `IconButton`, `Kbd` and `Avatar` are `@void/ui`'s;
 * the only things added here are the two nav marks the shared set has no reason to
 * carry (`local/glyphs`) and the Tauri window buttons, which exist because this bundle
 * runs in a window and the in-game one does not.
 *
 * The whole bar is the drag region (`data-tauri-drag-region`), with the interactive
 * children opting out. That is the standard Tauri 2 pattern and the reason the window
 * can be `decorations: false` without becoming unmovable.
 */

import { Avatar, Icon, IconButton, NavItem, SearchBar, TopNav as TopNavBar } from '@void/ui';
import type { ReactElement } from 'react';

import {
  CosmeticsGlyph,
  MaximiseGlyph,
  MinimiseGlyph,
  ServersGlyph,
  TerminalGlyph,
  WindowCloseGlyph,
} from '../local/glyphs';
import { invoke, IS_TAURI } from '../local/tauri';
import { useLaunch } from '../stores/launch';
import { useSession } from '../stores/session';
import { SCREENS, SCREEN_LABELS, useUi, type Screen } from '../stores/ui';

/**
 * The nav marks. Three come from the shared set; Cosmetics and Servers are local
 * because the overlay has neither screen — see `local/glyphs.tsx`.
 */
const NAV_ICONS: Record<Screen, ReactElement> = {
  play: <Icon name="play" size={14} />,
  mods: <Icon name="layers" size={14} />,
  cosmetics: <CosmeticsGlyph size={14} />,
  servers: <ServersGlyph size={14} />,
  friends: <Icon name="users" size={14} />,
};

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
      data-tauri-drag-region
      right={
        <>
          <SearchBar
            placeholder="Ask VOID anything"
            value=""
            aria-label="Ask VOID anything"
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

          <IconButton icon="settings" label="Settings" onClick={openSettings} />

          <button
            type="button"
            className="topnav__avatar"
            onClick={openSettings}
            aria-label="Account"
          >
            <Avatar name={account?.name ?? 'VOID'} src={account?.skin_url ?? undefined} size={32} />
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
      {SCREENS.map((id: Screen) => (
        <NavItem key={id} active={screen === id} onClick={() => go(id)}>
          <span className="v-navitem__icon">{NAV_ICONS[id]}</span>
          {SCREEN_LABELS[id]}
        </NavItem>
      ))}
    </TopNavBar>
  );
}
