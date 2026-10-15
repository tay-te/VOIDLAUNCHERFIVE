/**
 * The game log drawer, fed by `game:log`, and the launch-error surface.
 *
 * Two things share this file because they are the same moment: when a launch fails,
 * the error banner is useless without the last lines the JVM printed, and the drawer
 * is useless without knowing what went wrong.
 *
 * The drawer's head carries the three things a bug report needs: *Copy* (the lines on
 * screen, to the clipboard), *latest.log* (the game's own log file) and *Crash reports*
 * (the folder Minecraft writes them to). The launcher's own log is in Settings.
 */

import { useEffect, useRef, useState } from 'react';

import { Button, IconButton } from '@void/ui';

import { TerminalGlyph } from '../local/glyphs';
import type { LogLine, OpenTarget } from '../local/protocol';
import { errorText, invoke } from '../local/tauri';
import { useLaunch } from '../stores/launch';
import { useUi } from '../stores/ui';

/** How many lines the drawer asks for when it opens on an empty buffer. */
const TAIL_LINES = 500;

/** Puts text on the clipboard; falls back to a hidden textarea where the API is absent. */
export async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    return;
  } catch {
    /* fall through: some webviews gate the async clipboard */
  }
  const area = document.createElement('textarea');
  area.value = text;
  area.setAttribute('readonly', '');
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.appendChild(area);
  area.select();
  document.execCommand('copy');
  area.remove();
}

/** Asks Rust to reveal one of the places `open_path` allows. */
export function openPath(which: OpenTarget, path?: string): Promise<string> {
  return invoke('open_path', path === undefined ? { which } : { which, path });
}

export function LogDrawer() {
  const open = useUi((s) => s.logOpen);
  const setOpen = useUi((s) => s.setLogOpen);
  const { log, phase, bridgePort, clearLog } = useLaunch();
  const bodyRef = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<string | null>(null);

  // Pull whatever the ring buffer already holds when the drawer opens mid-session —
  // events only carry lines emitted since the app started listening (a reload, or a
  // window that was hidden to the tray when the game started).
  useEffect(() => {
    if (!open || log.length > 0) return;
    let cancelled = false;
    void invoke('game_log_tail', { lines: TAIL_LINES })
      .then((lines) => {
        if (cancelled || lines.length === 0 || useLaunch.getState().log.length > 0) return;
        const now = Date.now();
        const restored: LogLine[] = lines.map((line) => ({
          // The ring keeps text only; `[void]` warnings were stderr when they were live.
          stream: 'stdout',
          line,
          ts_ms: now,
        }));
        useLaunch.setState({ log: restored });
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
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

  useEffect(() => {
    if (!status) return;
    const t = setTimeout(() => setStatus(null), 2500);
    return () => clearTimeout(t);
  }, [status]);

  if (!open) return null;

  const reveal = (which: OpenTarget) => {
    void openPath(which).catch((e) => setStatus(errorText(e)));
  };

  return (
    <aside className="drawer" aria-label="Game log">
      <header className="drawer__head">
        <TerminalGlyph size={14} />
        <span className="drawer__title">Game log</span>
        <span className="drawer__meta">
          {status ?? (
            <>
              {phase === 'running' ? 'running' : 'idle'}
              {bridgePort ? ` · bridge :${bridgePort}` : ''}
            </>
          )}
        </span>
        <span className="drawer__spacer" />
        <Button
          variant="text"
          disabled={log.length === 0}
          onClick={() => {
            void copyText(log.map((l) => l.line).join('\n'))
              .then(() => setStatus(`copied ${log.length} lines`))
              .catch((e) => setStatus(errorText(e)));
          }}
        >
          Copy
        </Button>
        <Button variant="text" onClick={() => reveal('game_log')}>
          latest.log
        </Button>
        <Button variant="text" onClick={() => reveal('crash_reports')}>
          Crash reports
        </Button>
        <Button variant="text" onClick={clearLog}>
          Clear
        </Button>
        <IconButton icon="close" size="close" label="Close log" onClick={() => setOpen(false)} />
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

/**
 * The banners over the play bar: a launch that failed (`error`), and a non-fatal note
 * from `prepare` (`notice` — e.g. a newer VOID client that needs a launcher update).
 */
export function LaunchError() {
  const error = useLaunch((s) => s.error);
  const notice = useLaunch((s) => s.notice);
  const dismiss = useLaunch((s) => s.dismissError);
  const dismissNotice = useLaunch((s) => s.dismissNotice);
  const setLogOpen = useUi((s) => s.setLogOpen);

  return (
    <>
      {error ? (
        <div className="banner banner--error" role="alert">
          <span className="banner__text">{error}</span>
          <Button variant="ghost" onClick={() => setLogOpen(true)}>
            Open log
          </Button>
          <IconButton icon="close" size="close" label="Dismiss" onClick={dismiss} />
        </div>
      ) : null}
      {notice ? (
        <div className="banner banner--session" role="status">
          <span className="banner__text">{notice}</span>
          <IconButton icon="close" size="close" label="Dismiss" onClick={dismissNotice} />
        </div>
      ) : null}
    </>
  );
}
