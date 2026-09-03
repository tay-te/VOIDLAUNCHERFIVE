/**
 * The game log drawer, fed by `game:log`, and the launch-error surface.
 *
 * Two things share this file because they are the same moment: when a launch fails,
 * the error banner is useless without the last lines the JVM printed, and the drawer
 * is useless without knowing what went wrong.
 */

import { useEffect, useRef } from 'react';

import { Button, IconButton } from '../components';
import { TerminalIcon, XIcon } from '../components/icons';
import { invoke } from '../local/tauri';
import { useLaunch } from '../stores/launch';
import { useUi } from '../stores/ui';

export function LogDrawer() {
  const open = useUi((s) => s.logOpen);
  const setOpen = useUi((s) => s.setLogOpen);
  const { log, phase, bridgePort, simulated, clearLog } = useLaunch();
  const bodyRef = useRef<HTMLDivElement>(null);

  // Pull whatever the ring buffer already holds when the drawer opens mid-session —
  // events only carry lines emitted since the app started listening.
  useEffect(() => {
    if (!open || log.length > 0) return;
    void invoke('game_log_tail', { lines: 500 }).catch(() => []);
  }, [open, log.length]);

  useEffect(() => {
    if (!open) return;
    const el = bodyRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [open, log.length]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, setOpen]);

  if (!open) return null;

  return (
    <aside className="drawer" aria-label="Game log">
      <header className="drawer__head">
        <TerminalIcon size={14} />
        <span className="drawer__title">Game log</span>
        <span className="drawer__meta">
          {phase === 'running' ? 'running' : 'idle'}
          {bridgePort ? ` · bridge :${bridgePort}` : ''}
          {simulated ? ' · simulated' : ''}
        </span>
        <span className="drawer__spacer" />
        <Button variant="text" onClick={clearLog}>
          Clear
        </Button>
        <IconButton icon={XIcon} size={28} glyph={13} label="Close log" onClick={() => setOpen(false)} />
      </header>
      <div className="drawer__body" ref={bodyRef}>
        {log.length === 0 ? (
          <p className="drawer__empty">Nothing yet. Lines appear here as soon as the JVM starts.</p>
        ) : (
          log.map((line, i) => (
            <pre key={i} className={`drawer__line${line.stream === 'stderr' ? ' is-err' : ''}`}>
              {line.line}
            </pre>
          ))
        )}
      </div>
    </aside>
  );
}

/** The banner that appears when `prepare` or `launch` rejects, or the JVM crashes. */
export function LaunchError() {
  const error = useLaunch((s) => s.error);
  const dismiss = useLaunch((s) => s.dismissError);
  const setLogOpen = useUi((s) => s.setLogOpen);

  if (!error) return null;
  return (
    <div className="banner banner--error" role="alert">
      <span className="banner__text">{error}</span>
      <Button variant="ghost" onClick={() => setLogOpen(true)}>
        Open log
      </Button>
      <IconButton icon={XIcon} size={28} glyph={12} label="Dismiss" onClick={dismiss} />
    </div>
  );
}

/** The session summary the window comes back with after `game:closed` (§5). */
export function SessionSummary() {
  const session = useLaunch((s) => s.lastSession);
  const dismiss = useLaunch((s) => s.dismissSession);
  if (!session || session.code !== 0) return null;

  const minutes = Math.round(session.played_ms / 60_000);
  return (
    <div className="banner banner--session" role="status">
      <span className="banner__text">
        Session ended · {minutes} min played · {Math.round(session.fps_avg)} fps avg
        {session.server ? ` · ${session.server}` : ''}
      </span>
      <IconButton icon={XIcon} size={28} glyph={12} label="Dismiss" onClick={dismiss} />
    </div>
  );
}
