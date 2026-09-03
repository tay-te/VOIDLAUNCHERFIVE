/**
 * Servers — `244:324`.
 *
 * The pings are real: `server_ping` opens a TCP socket, does the 1.8 SLP handshake and
 * measures the ping/pong round trip. That is also where the online counts and the
 * version string come from, so a favourite that has gone away says so instead of
 * showing a stale number.
 *
 * The sparkline is the rolling history of those pings — the plain-`div` bars pattern
 * of `ultralight-notes.md` §5, kept identical to the in-game one.
 */

import { useEffect, useState } from 'react';

import {
  Button,
  Caption,
  FilterTabs,
  Panel,
  Pane,
  SearchField,
  Sparkline,
  StatTile,
  StatusDot,
  Switch,
} from '../components';
import { PlayIcon, PlusIcon, StarIcon, TrashIcon } from '../components/icons';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { pingTone, useServers } from '../stores/servers';

const TABS = ['Favourites', 'Recent', 'Browse'] as const;
type Tab = (typeof TABS)[number];

export function ServersScreen() {
  const { servers, pings, selected, select, add, remove, toggleFavourite, ping, pingAll } = useServers();
  const active = useLoadouts((s) => s.active);
  const start = useLaunch((s) => s.start);
  const phase = useLaunch((s) => s.phase);

  const [tab, setTab] = useState<Tab>('Favourites');
  const [query, setQuery] = useState('');

  // One sweep on mount, then a slow refresh — a server list that never updates is a
  // screenshot, and one that updates every second is a port scanner.
  useEffect(() => {
    void pingAll();
    const t = setInterval(() => void pingAll(), 60_000);
    return () => clearInterval(t);
  }, [pingAll]);

  const q = query.trim().toLowerCase();
  const list = servers.filter((s) => {
    if (tab === 'Favourites' && !s.favourite) return false;
    if (!q) return true;
    return s.host.includes(q) || s.name.toLowerCase().includes(q);
  });

  const detail = servers.find((s) => s.host === selected) ?? list[0];
  const detailPing = detail ? pings[detail.host] : undefined;

  return (
    <Panel
      title="Servers"
      controls={
        <>
          <SearchField
            value={query}
            onChange={setQuery}
            placeholder="Search or paste an address"
            width={230}
            onSubmit={(value) => {
              // The field doubles as a direct-connect input, per the frame's note.
              if (value.includes('.')) {
                add(value);
                setQuery('');
              }
            }}
          />
          <FilterTabs tabs={TABS} value={tab} onChange={setTab} />
          <Button
            variant="raised"
            icon={PlusIcon}
            onClick={() => {
              if (query.trim()) {
                add(query);
                setQuery('');
              }
            }}
            disabled={!query.trim()}
            title={query.trim() ? `Add ${query.trim()}` : 'Type an address to add a server'}
          >
            Add server
          </Button>
        </>
      }
    >
      <div className="split">
        <div className="list">
          {list.map((server) => {
            const state = pings[server.host];
            const ms = state?.result?.latency_ms;
            return (
              <div
                key={server.host}
                className={`row row--server${server.host === detail?.host ? ' is-selected' : ''}`}
              >
                <button type="button" className="row__hit" onClick={() => select(server.host)}>
                  <span className="row__icon" aria-hidden="true">
                    {server.name.slice(0, 2).toUpperCase()}
                  </span>
                  <span className="row__text">
                    <span className="row__title">{server.name}</span>
                    <span className="row__sub">{server.host}</span>
                  </span>
                  <span className="row__count">
                    {state?.status === 'ok' && state.result
                      ? `${state.result.online.toLocaleString()} online`
                      : state?.status === 'pinging'
                        ? 'pinging…'
                        : 'offline'}
                  </span>
                  <span className={`row__ping row__ping--${ms === undefined ? 'bad' : pingTone(ms)}`}>
                    <StatusDot tone={ms === undefined ? 'muted' : pingTone(ms)} size={6} />
                    {ms === undefined ? '—' : `${ms} ms`}
                  </span>
                </button>
                <Button
                  variant={server.host === detail?.host ? 'chip-accent' : 'chip'}
                  onClick={() => active && phase === 'idle' && void start(active.id)}
                  disabled={!active || phase !== 'idle'}
                  title="Launch with the active loadout, then connect from the in-game server list"
                >
                  Join
                </Button>
              </div>
            );
          })}
          {list.length === 0 ? (
            <p className="list__empty">
              {q ? `No favourite matches “${query}”. Press Enter to add it.` : 'No servers yet.'}
            </p>
          ) : null}
        </div>

        {detail ? (
          <Pane>
            <div className="pane__head">
              <span className="pane__icon" aria-hidden="true">
                {detail.name.slice(0, 2).toUpperCase()}
              </span>
              <span className="pane__headtext">
                <span className="pane__title">{detail.name}</span>
                <span className="pane__sub">
                  {detail.host}
                  {detailPing?.result ? ` · ${detailPing.result.version}` : ''}
                </span>
              </span>
            </div>

            <div className="stat-tiles">
              <StatTile value={detailPing?.result ? `${detailPing.result.latency_ms} ms` : '—'} unit="ping" />
              <StatTile
                value={detailPing?.result ? detailPing.result.online.toLocaleString() : '—'}
                unit="online"
              />
              <StatTile value={detailPing?.result ? detailPing.result.max.toLocaleString() : '—'} unit="slots" />
            </div>

            <Caption>PING · THIS SESSION</Caption>
            <Sparkline values={detailPing?.history ?? []} />

            {detailPing?.status === 'error' ? (
              <p className="pane__error">{detailPing.error}</p>
            ) : detailPing?.result?.motd ? (
              <p className="pane__motd">{detailPing.result.motd}</p>
            ) : null}

            <div className="pane__row">
              <span className="pane__rowtext">
                <span className="pane__rowtitle">Auto-switch loadout</span>
                <span className="pane__rowsub">Not wired yet — needs a server profile schema (§16.3)</span>
              </span>
              <Switch size="m" checked={false} onChange={() => undefined} label="Auto-switch loadout" disabled />
            </div>

            <div className="pane__spacer" />

            <Button
              variant="accent"
              icon={PlayIcon}
              full
              disabled={!active || phase !== 'idle'}
              onClick={() => active && void start(active.id)}
            >
              {active ? `Join with ${active.name}` : 'No loadout'}
            </Button>
            <Button variant="ghost" icon={StarIcon} full onClick={() => toggleFavourite(detail.host)}>
              {detail.favourite ? 'Favourited' : 'Add to favourites'}
            </Button>
            <Button variant="text" icon={TrashIcon} full onClick={() => remove(detail.host)}>
              Remove server
            </Button>
            <Button variant="text" full onClick={() => void ping(detail.host)}>
              Ping now
            </Button>
          </Pane>
        ) : null}
      </div>
    </Panel>
  );
}
