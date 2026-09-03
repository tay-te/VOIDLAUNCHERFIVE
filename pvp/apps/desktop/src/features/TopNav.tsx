/**
 * The 1300 × 62 chrome of every frame: mark, five nav tabs, "Ask VOID anything ⌘K",
 * settings, avatar — plus the window controls a frameless window needs.
 *
 * The whole bar is the drag region (`data-tauri-drag-region`), with the interactive
 * children opting out. That is the standard Tauri 2 pattern and the reason the window
 * can be `decorations: false` without becoming unmovable.
 */

import { GearIcon, LayersIcon, MinusIcon, PlayIcon, ServerIcon, ShirtIcon, SquareIcon, TerminalIcon, UsersIcon, XIcon } from '../components/icons';
import { Avatar, IconButton, Kbd } from '../components';
import { invoke, IS_TAURI } from '../local/tauri';
import { useLaunch } from '../stores/launch';
import { useSession } from '../stores/session';
import { SCREENS, SCREEN_LABELS, useUi, type Screen } from '../stores/ui';

const NAV_ICONS = {
  play: PlayIcon,
  mods: LayersIcon,
  cosmetics: ShirtIcon,
  servers: ServerIcon,
  friends: UsersIcon,
} as const;

export function TopNav() {
  const screen = useUi((s) => s.screen);
  const go = useUi((s) => s.go);
  const openPalette = useUi((s) => s.openPalette);
  const openSettings = useUi((s) => s.openSettings);
  const toggleLog = useUi((s) => s.toggleLog);
  const account = useSession((s) => s.account);
  const logLines = useLaunch((s) => s.log.length);

  return (
    <nav className="topnav" data-tauri-drag-region>
      <span className="topnav__mark" aria-hidden="true">
        <span className="topnav__mark-inner" />
      </span>

      <div className="topnav__tabs">
        {SCREENS.map((id: Screen) => {
          const Icon = NAV_ICONS[id];
          return (
            <button
              key={id}
              type="button"
              className={`navtab${screen === id ? ' is-active' : ''}`}
              aria-current={screen === id ? 'page' : undefined}
              onClick={() => go(id)}
            >
              <Icon size={14} />
              {SCREEN_LABELS[id]}
            </button>
          );
        })}
      </div>

      <span className="topnav__spacer" data-tauri-drag-region />

      <button type="button" className="askbar" onClick={openPalette}>
        <span className="askbar__dot" />
        <span className="askbar__placeholder">Ask VOID anything</span>
        <Kbd>⌘K</Kbd>
      </button>

      <IconButton
        icon={TerminalIcon}
        label={logLines > 0 ? `Game log — ${logLines} lines` : 'Game log'}
        onClick={toggleLog}
        className={logLines > 0 ? 'has-activity' : undefined}
      />
      <IconButton icon={GearIcon} label="Settings" onClick={openSettings} />
      <button type="button" className="topnav__avatar" onClick={openSettings} aria-label="Account">
        <Avatar name={account?.name ?? 'VOID'} src={account?.skin_url} size={32} />
      </button>

      {IS_TAURI ? (
        <div className="wincontrols">
          <IconButton icon={MinusIcon} size={28} glyph={13} label="Minimise" onClick={() => void invoke('window_minimize')} />
          <IconButton icon={SquareIcon} size={28} glyph={12} label="Maximise" onClick={() => void invoke('window_toggle_maximize')} />
          <IconButton icon={XIcon} size={28} glyph={13} label="Hide to tray" onClick={() => void invoke('window_close')} className="wincontrols__close" />
        </div>
      ) : null}
    </nav>
  );
}
