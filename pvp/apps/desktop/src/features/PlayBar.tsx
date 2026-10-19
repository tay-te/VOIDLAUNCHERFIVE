/**
 * The play bar — Figma v1 Ship component `62:30`, identical on every launcher screen,
 * centred at the bottom and overlapping the panel's lower edge.
 *
 * Rule 2: it holds only what decides what launches — **Server** (Launch joins it;
 * "Title screen" launches without joining) and **Version** — and **Launch**, which is
 * **Stop** while playing. v1 ships 1.8.9 only; the other versions show as Coming soon.
 * Loadouts are a preference, not a launch decision, so they are not here (rule 3).
 *
 * States:
 *   Ready      SERVER · VERSION · ▶ Launch ↵
 *   working    the Launch pill fills left to right while `prepare` downloads, then reads
 *              "Launching…" until the JVM is up
 *   Playing    PLAYING  Hypixel · 1.8.9 · 12 min  ·  ■ Stop
 *
 * Offline, the bar launches to the title screen whatever the Server field was set to — a
 * join would only fail — and says so in the field (`81:198`). The choice is kept for when
 * the connection comes back.
 */

import { useCallback, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

import chevronDown from '../assets/icons/chevron-down.svg';
import playIcon from '../assets/icons/play.svg';
import { useOnline } from '../local/online';
import { formatBytes, formatDuration, stepLabel, useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { pingTone, useServers, useTargetServer } from '../stores/servers';
import { useSession } from '../stores/session';
import { useUi } from '../stores/ui';

/** v1 ships 1.8.9 only (rule 2). */
const VERSIONS = [
  { id: '1.8.9', label: '1.8.9', sub: 'Legacy Fabric · all PvP mods', available: true },
  { id: '1.12.2', label: '1.12.2', available: false },
  { id: '1.21.4', label: '1.21.4', available: false },
] as const;

/** Close on an outside click or Escape — the two menus' shared dismissal. */
function useDismiss(open: boolean, close: () => void, ref: React.RefObject<HTMLElement | null>) {
  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) close();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    window.addEventListener('mousedown', onDown);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('mousedown', onDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open, close, ref]);
}

function Field({
  caption,
  value,
  open,
  onClick,
  label,
}: {
  caption: string;
  value: string;
  open: boolean;
  onClick: () => void;
  label: string;
}) {
  return (
    <button
      type="button"
      className={`pbfield${open ? ' is-open' : ''}`}
      aria-label={label}
      aria-haspopup="menu"
      aria-expanded={open}
      onClick={onClick}
    >
      <span className="pbfield__caption">{caption}</span>
      <span className="pbfield__value">{value}</span>
      <img className="pbfield__chev" src={chevronDown} width={9} height={5} alt="" />
    </button>
  );
}

function MenuItem({
  title,
  sub,
  aside,
  selected,
  disabled,
  onClick,
}: {
  title: string;
  sub?: string | undefined;
  aside?: ReactNode;
  selected?: boolean;
  disabled?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      role="menuitemradio"
      aria-checked={selected ?? false}
      className={`popitem${selected ? ' is-selected' : ''}`}
      disabled={disabled}
      onClick={onClick}
    >
      <span className="popitem__text">
        <span className="popitem__title">{title}</span>
        {sub ? <span className="popitem__sub">{sub}</span> : null}
      </span>
      {aside}
      {selected ? <span className="popitem__dot" aria-hidden="true" /> : null}
    </button>
  );
}

function AddServerRow({ onDone }: { onDone: () => void }) {
  const add = useServers((s) => s.add);
  const [value, setValue] = useState('');
  const [error, setError] = useState(false);
  return (
    <form
      className="popadd"
      onSubmit={(e) => {
        e.preventDefault();
        if (add(value)) onDone();
        else setError(true);
      }}
    >
      <input
        // A field the player opened on purpose; it should take the keyboard.
        autoFocus
        className={`popadd__input${error ? ' is-error' : ''}`}
        value={value}
        placeholder="play.example.net"
        aria-label="Server address"
        aria-invalid={error}
        spellCheck={false}
        onChange={(e) => {
          setValue(e.target.value);
          setError(false);
        }}
      />
      <button type="submit" className="btn2 btn2--secondary btn2--sm">
        Add
      </button>
    </form>
  );
}

function ServerMenu({ close }: { close: () => void }) {
  const servers = useServers((s) => s.servers);
  const target = useServers((s) => s.target);
  const pings = useServers((s) => s.pings);
  const choose = useServers((s) => s.choose);
  const pingAll = useServers((s) => s.pingAll);
  const online = useOnline();
  const [adding, setAdding] = useState(false);

  // Fresh numbers every time the menu opens — they are what it is opened to compare.
  useEffect(() => {
    if (online) void pingAll();
  }, [pingAll, online]);

  return (
    <div className="popmenu popmenu--server" role="menu" aria-label="Server">
      <MenuItem
        title="Title screen"
        sub="Launch without joining a server"
        selected={target === null || !online}
        onClick={() => {
          choose(null);
          close();
        }}
      />
      <span className="popmenu__divider" />
      <span className="popmenu__label">Favourites</span>
      {servers.map((s) => {
        const p = pings[s.host];
        const ms = p?.status === 'ok' && p.result ? p.result.latency_ms : null;
        return (
          <MenuItem
            key={s.host}
            title={s.name}
            sub={s.host}
            selected={online && target === s.host}
            disabled={!online}
            aside={
              <span className={`popitem__ping tnum${ms !== null && pingTone(ms) === 'ok' ? ' is-ok' : ''}`}>
                {ms !== null ? `${ms} ms` : '—'}
              </span>
            }
            onClick={() => {
              choose(s.host);
              close();
            }}
          />
        );
      })}
      <span className="popmenu__divider" />
      {adding ? (
        <AddServerRow onDone={close} />
      ) : (
        <button type="button" className="popitem popitem--action" disabled={!online} onClick={() => setAdding(true)}>
          <span className="popitem__title">+&nbsp;&nbsp;Add server</span>
        </button>
      )}
    </div>
  );
}

function VersionMenu({ close }: { close: () => void }) {
  return (
    <div className="popmenu popmenu--version" role="menu" aria-label="Version">
      {VERSIONS.map((v) => (
        <MenuItem
          key={v.id}
          title={v.label}
          sub={'sub' in v ? v.sub : undefined}
          selected={v.id === '1.8.9'}
          disabled={!v.available}
          aside={v.available ? null : <span className="popitem__soon">Coming soon</span>}
          onClick={close}
        />
      ))}
    </div>
  );
}

/** Re-renders once a minute while playing, so "12 min" moves. */
function useMinuteClock(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return undefined;
    setNow(Date.now());
    const t = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(t);
  }, [active]);
  return now;
}

export function PlayBar() {
  const phase = useLaunch((s) => s.phase);
  const progress = useLaunch((s) => s.progress);
  const startedAt = useLaunch((s) => s.startedAt);
  const launchedServer = useLaunch((s) => s.launchedServer);
  const start = useLaunch((s) => s.start);
  const kill = useLaunch((s) => s.kill);
  const active = useLoadouts((s) => s.active);
  const account = useSession((s) => s.account);
  const openSettings = useUi((s) => s.openSettings);
  const servers = useServers((s) => s.servers);
  const target = useTargetServer();
  const online = useOnline();
  const menu = useUi((s) => s.playMenu);
  const setMenu = useUi((s) => s.setPlayMenu);
  const ref = useRef<HTMLDivElement>(null);
  const close = useCallback(() => setMenu(null), [setMenu]);
  useDismiss(menu !== null, close, ref);
  const now = useMinuteClock(phase === 'running');

  // Offline launches to the title screen, whatever the field was set to.
  const joining = online ? target : null;
  const canLaunch = Boolean(active) && Boolean(account) && phase === 'idle';
  const launch = () => {
    if (!account) {
      openSettings();
      return;
    }
    if (active && phase === 'idle') void start(active.id, joining?.host ?? null);
  };

  /*
    Enter launches — ↵ is on the button. Plain Enter is only honoured when nothing
    focusable has the keyboard, so it cannot fire while someone types a server address;
    ⌘↵ works from anywhere.
  */
  const launchRef = useRef(launch);
  launchRef.current = launch;
  useEffect(() => {
    const onKey = (event: KeyboardEvent): void => {
      if (event.key !== 'Enter' || event.repeat) return;
      const chord = event.metaKey || event.ctrlKey;
      const bare = document.activeElement === null || document.activeElement === document.body;
      if (!chord && !bare) return;
      event.preventDefault();
      if (useLaunch.getState().phase === 'idle') launchRef.current();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  if (phase === 'running') {
    const where = launchedServer
      ? (servers.find((s) => s.host === launchedServer)?.name ?? launchedServer)
      : 'Title screen';
    const played = startedAt ? formatDuration(now - startedAt) : '';
    return (
      <div className="playbar playbar--playing" ref={ref}>
        <span className="pbfield pbfield--static">
          <span className="pbfield__caption">Playing</span>
          <span className="pbfield__value tnum">
            {[where, '1.8.9', played].filter(Boolean).join(' · ')}
          </span>
        </span>
        <button type="button" className="pbstop" onClick={() => void kill()}>
          <span className="pbstop__mark" aria-hidden="true" />
          <span>Stop</span>
        </button>
      </div>
    );
  }

  const working = phase === 'preparing' || phase === 'launching';
  const pct = progress && progress.total > 0 ? (progress.done / progress.total) * 100 : 0;

  return (
    <div className="playbar" ref={ref}>
      <div className="pbslot">
        <Field
          caption="Server"
          value={joining ? joining.name : 'Title screen'}
          open={menu === 'server'}
          label={`Server — ${joining ? joining.name : 'Title screen'}`}
          onClick={() => setMenu(menu === 'server' ? null : 'server')}
        />
        {menu === 'server' ? <ServerMenu close={close} /> : null}
      </div>
      <div className="pbslot">
        <Field
          caption="Version"
          value="1.8.9"
          open={menu === 'version'}
          label="Version — 1.8.9"
          onClick={() => setMenu(menu === 'version' ? null : 'version')}
        />
        {menu === 'version' ? <VersionMenu close={close} /> : null}
      </div>

      {working ? (
        <button
          type="button"
          className="pblaunch is-working"
          style={{ '--pb-progress': `${phase === 'launching' ? 100 : pct}%` } as CSSProperties}
          disabled
          aria-label={phase === 'launching' ? 'Launching' : `Preparing — ${Math.round(pct)}%`}
        >
          <span className="pblaunch__label">
            {phase === 'launching' ? 'Launching…' : stepLabel(progress?.step ?? 'manifest')}
          </span>
          {phase === 'preparing' && progress ? (
            <span className="pblaunch__rate tnum">
              {Math.round(pct)}% · {formatBytes(progress.bytes_per_sec)}/s
            </span>
          ) : null}
        </button>
      ) : (
        <button
          type="button"
          className="pblaunch"
          disabled={!account ? false : !canLaunch}
          onClick={launch}
          title={joining ? `Launch and join ${joining.host}` : 'Launch to the title screen'}
        >
          <img className="pblaunch__play" src={playIcon} width={10} height={12} alt="" />
          <span className="pblaunch__label">{account ? 'Launch' : 'Sign in'}</span>
          <span className="pblaunch__key" aria-hidden="true">
            ↵
          </span>
        </button>
      )}
    </div>
  );
}
