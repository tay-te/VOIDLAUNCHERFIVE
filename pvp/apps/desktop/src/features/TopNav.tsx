/**
 * The navbar — Figma v1 Ship `navbar · 72` (`68:1553`), identical on every screen.
 *
 *   left    the 16-cell ring and the VOID wordmark, then the tabs: PvP and Store live;
 *           Modpacks, Servers and Quests muted with SOON, each opening its coming-soon
 *           screen (rule 1)
 *   right   search (opens ⌘K, rule 10), the friends button — who is online and how many —
 *           which opens the Friends drawer, and the profile chip, which opens a menu
 *           (Settings · Sign in / Sign out)
 *
 * The whole bar is the window's drag region (`data-tauri-drag-region`), with interactive
 * children opting out by being buttons. That is the standard Tauri 2 pattern and the
 * reason the window can be `decorations: false` without becoming unmovable.
 */

import { useEffect, useRef, useState } from 'react';

import searchIcon from '../assets/icons/search.svg';
import chevronDown from '../assets/icons/nav-chevron.svg';
import { FaceAvatar, Initial, RingMark } from './art';
import { MaximiseGlyph, MinimiseGlyph, TerminalGlyph, WindowCloseGlyph } from '../local/glyphs';
import { invoke, IS_TAURI } from '../local/tauri';
import { onlineFriends, useFriends } from '../stores/friends';
import { useLaunch } from '../stores/launch';
import { useSession } from '../stores/session';
import { SCREENS, SCREEN_LABELS, isSoon, tabFor, useUi } from '../stores/ui';

function ProfileChip() {
  const account = useSession((s) => s.account);
  const accounts = useSession((s) => s.accounts);
  const logout = useSession((s) => s.logout);
  const switchAccount = useSession((s) => s.switchAccount);
  const phase = useLaunch((s) => s.phase);
  const openSettings = useUi((s) => s.openSettings);
  const others = accounts.filter((a) => a.uuid !== account?.uuid);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('mousedown', onDown);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('mousedown', onDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="navmenu" ref={ref}>
      <button
        type="button"
        className={`navpill navpill--profile${open ? ' is-open' : ''}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={account ? `Profile — ${account.name}` : 'Profile — not signed in'}
        onClick={() => setOpen((o) => !o)}
      >
        <FaceAvatar size={32} />
        <span className="navpill__name">{account?.name ?? 'Sign in'}</span>
        <img src={chevronDown} width={9} height={5} alt="" className="navpill__chev" />
      </button>

      {open ? (
        <div className="popmenu popmenu--profile" role="menu">
          {account ? (
            <div className="popmenu__head">
              <span className="popmenu__title">{account.name}</span>
              <span className="popmenu__sub">
                {account.kind === 'offline' ? 'Offline account' : 'Microsoft account'}
              </span>
            </div>
          ) : null}
          {/* The other signed-in accounts, one click to switch; adding one is Settings. */}
          {others.map((other) => (
            <button
              key={other.uuid}
              type="button"
              role="menuitem"
              className="popmenu__item"
              disabled={phase !== 'idle'}
              onClick={() => {
                setOpen(false);
                void switchAccount(other.uuid);
              }}
            >
              Switch to {other.name}
            </button>
          ))}
          <button
            type="button"
            role="menuitem"
            className="popmenu__item"
            onClick={() => {
              setOpen(false);
              openSettings();
            }}
          >
            {account ? 'Settings' : 'Sign in'}
          </button>
          {account ? (
            <button
              type="button"
              role="menuitem"
              className="popmenu__item"
              onClick={() => {
                setOpen(false);
                void logout();
              }}
            >
              Sign out
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function FriendsButton() {
  const friends = useFriends((s) => s.friends);
  const requests = useFriends((s) => s.incoming.length);
  const open = useUi((s) => s.friendsOpen);
  const toggle = useUi((s) => s.toggleFriends);
  const online = onlineFriends(friends);
  // The frame stacks three faces; with nobody online it shows the first three friends.
  const stack = (online.length > 0 ? online : friends).slice(0, 3);

  return (
    <button
      type="button"
      className={`navpill navpill--friends${open ? ' is-open' : ''}`}
      aria-expanded={open}
      aria-label={`Friends — ${online.length} online${requests ? `, ${requests} request${requests === 1 ? '' : 's'}` : ''}`}
      onClick={toggle}
    >
      {stack.length > 0 ? (
        <span className="navpill__stack" style={{ width: 28 + (stack.length - 1) * 19 }}>
          {stack.map((f, i) => (
            <span key={f.name} style={{ left: i * 19 }}>
              <Initial name={f.name} size={28} />
            </span>
          ))}
        </span>
      ) : (
        <span className="navpill__label">Friends</span>
      )}
      <span className={`navpill__dot${online.length > 0 ? ' is-on' : ''}`} aria-hidden="true" />
      <span className="navpill__count tnum">{online.length}</span>
    </button>
  );
}

export function TopNav() {
  const screen = useUi((s) => s.screen);
  const go = useUi((s) => s.go);
  const openPalette = useUi((s) => s.openPalette);
  const toggleLog = useUi((s) => s.toggleLog);
  const logLines = useLaunch((s) => s.log.length);
  const tab = tabFor(screen);

  return (
    <header className="navbar" data-tauri-drag-region>
      <span className="navbar__brand" data-tauri-drag-region>
        <RingMark size={28} />
        <span className="navbar__word">VOID</span>
      </span>

      <nav className="navbar__tabs" aria-label="Sections">
        {SCREENS.map((id) => {
          const soon = isSoon(id);
          return (
            <button
              key={id}
              type="button"
              className={`navtab${tab === id ? ' is-active' : ''}${soon ? ' is-soon' : ''}`}
              aria-current={tab === id ? 'page' : undefined}
              onClick={() => go(id)}
            >
              <span className="navtab__label">{SCREEN_LABELS[id]}</span>
              {soon ? <span className="navtab__soon">Soon</span> : null}
            </button>
          );
        })}
      </nav>

      <span className="navbar__spacer" data-tauri-drag-region />

      <button type="button" className="navsearch" onClick={openPalette} aria-label="Search VOID">
        <img src={searchIcon} width={13} height={13} alt="" className="navsearch__icon" />
        <span className="navsearch__placeholder">Search VOID</span>
        <span className="keycap">⌘K</span>
      </button>

      <FriendsButton />
      <ProfileChip />

      {/* The JVM log, once there is one — the frames draw nothing here, and an empty log
          is nothing to open. */}
      {logLines > 0 ? (
        <button
          type="button"
          className="navicon"
          aria-label={`Game log — ${logLines} lines`}
          title={`Game log — ${logLines} lines`}
          onClick={toggleLog}
        >
          <TerminalGlyph size={14} />
        </button>
      ) : null}

      {IS_TAURI ? (
        <span className="wincontrols">
          <button type="button" className="wincontrols__btn" aria-label="Minimise" onClick={() => void invoke('window_minimize')}>
            <MinimiseGlyph size={13} />
          </button>
          <button type="button" className="wincontrols__btn" aria-label="Maximise" onClick={() => void invoke('window_toggle_maximize')}>
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
    </header>
  );
}
