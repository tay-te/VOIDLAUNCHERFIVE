/**
 * Servers — `244:324`.
 *
 * The pings are real: `server_ping` opens a TCP socket, does the 1.8 SLP handshake and
 * measures the ping/pong round trip. That is also where the online counts and the
 * version string come from, so a favourite that has gone away says so instead of
 * showing a stale number.
 *
 * `ServerRow`, `Pane`, `StatTile`, `Sparkline` and `GroupCaption` are `@void/ui`'s.
 * The sparkline is still the plain-`div` bars of `ultralight-notes.md` §5 — the
 * package draws it that way so the launcher and the overlay are the same chart.
 */

import {
  Button,
  FilterTabs,
  GroupCaption,
  Icon,
  Pane,
  Panel,
  SearchBar,
  ServerRow,
  Sparkline,
  StatTile,
  Toggle,
} from '@void/ui';
import { useEffect, useState } from 'react';

import { TrashGlyph } from '../local/glyphs';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useServers } from '../stores/servers';

const TABS = ['Favourites', 'Recent', 'Browse'] as const;
type Tab = (typeof TABS)[number];

/** `SearchBar` spreads its rest props onto the input, so an id is how Add server
    reaches the field it wants you to type in. */
const SEARCH_ID = 'servers-search';

/**
 * The sparkline draws bars 16–32 px tall, as the frame's twelve-hour history does.
 * Only real samples get a bar: the frame has twelve because it has twelve hours of
 * them, and a launcher two pings into a session has two. Padding the rest with stubs
 * would draw a history that never happened.
 */
function bars(history: readonly number[]): { values: number[]; outliers: number[] } {
  const window = [...history].slice(-12);
  const max = Math.max(32, ...window);
  const values = window.map((ms) => Math.max(6, Math.round((ms / max) * 32)));
  const outliers = window.flatMap((ms, index) => (ms > 100 ? [index] : []));
  return { values, outliers };
}

export function ServersScreen() {
  const { servers, pings, selected, select, add, remove, toggleFavourite, ping, pingAll } =
    useServers();
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
  const spark = bars(detailPing?.history ?? []);

  return (
    <Panel
      title="Servers"
      headerRight={
        <>
          <SearchBar
            id={SEARCH_ID}
            variant="panel"
            placeholder="Search or paste an address"
            value={query}
            onChange={setQuery}
            onKeyDown={(event) => {
              // The field doubles as a direct-connect input, per the frame's note.
              if (event.key === 'Enter' && query.includes('.')) {
                add(query);
                setQuery('');
              }
            }}
          />
          <FilterTabs
            label="Server list"
            tabs={TABS.map((id) => ({ id, label: id }))}
            value={tab}
            onChange={(id) => setTab(id as Tab)}
          />
          <span className="v-spacer" />
          <Button
            variant="raised"
            icon="plus"
            onClick={() => {
              // The search field doubles as the address input, so with nothing typed
              // this button's job is to send you there rather than to grey itself out.
              if (!query.trim()) {
                document.getElementById(SEARCH_ID)?.focus();
                return;
              }
              add(query);
              setQuery('');
            }}
            title={query.trim() ? `Add ${query.trim()}` : 'Type an address in the field to add a server'}
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
              <ServerRow
                key={server.host}
                name={server.name}
                address={server.host}
                players={
                  state?.status === 'ok' && state.result
                    ? `${state.result.online.toLocaleString()} online`
                    : state?.status === 'pinging'
                      ? 'pinging…'
                      : 'offline'
                }
                ping={ms}
                selected={server.host === detail?.host}
                onSelect={() => select(server.host)}
                onJoin={
                  active && phase === 'idle' ? () => void start(active.id) : undefined
                }
                title="Launch with the active loadout, then connect from the in-game server list"
              />
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
              <StatTile
                value={detailPing?.result ? `${detailPing.result.latency_ms} ms` : '—'}
                unit="ping"
              />
              <StatTile
                value={detailPing?.result ? detailPing.result.online.toLocaleString() : '—'}
                unit="online"
              />
              <StatTile
                value={detailPing?.result ? detailPing.result.max.toLocaleString() : '—'}
                unit="slots"
              />
            </div>

            <GroupCaption label="Ping · this session" />
            <Sparkline values={spark.values} outliers={spark.outliers} />

            {detailPing?.status === 'error' ? (
              <p className="pane__error">{detailPing.error}</p>
            ) : detailPing?.result?.motd ? (
              <p className="pane__motd">{detailPing.result.motd}</p>
            ) : null}

            {/* The two housekeeping actions sit with the server's own data, above the
                spacer, so the pane still ends on the frame's Join / Favourited pair. */}
            <div className="pane__actions">
              <Button variant="text" onClick={() => remove(detail.host)}>
                <TrashGlyph size={13} />
                Remove server
              </Button>
              <Button variant="text" onClick={() => void ping(detail.host)}>
                <Icon name="reset" size={13} />
                Ping now
              </Button>
            </div>

            <span className="v-spacer" />

            <div className="pane__row">
              <span className="pane__rowtext">
                <span className="pane__rowtitle">Auto-switch loadout</span>
                <span className="pane__rowsub">
                  {/* TODO(integrate): needs a server-profile schema (§16.3). */}
                  Not wired yet — needs a server profile schema (§16.3)
                </span>
              </span>
              <Toggle size="m" checked={false} label="Auto-switch loadout" disabled />
            </div>

            <Button
              variant="accent"
              icon="play"
              block
              disabled={!active || phase !== 'idle'}
              onClick={() => active && void start(active.id)}
            >
              {active ? `Join with ${active.name}` : 'No loadout'}
            </Button>
            <Button
              variant="ghost"
              icon="star"
              block
              onClick={() => toggleFavourite(detail.host)}
            >
              {detail.favourite ? 'Favourited' : 'Add to favourites'}
            </Button>
          </Pane>
        ) : null}
      </div>
    </Panel>
  );
}
