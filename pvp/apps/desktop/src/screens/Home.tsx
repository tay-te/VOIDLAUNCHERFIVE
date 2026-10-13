/**
 * Home (PvP) — Figma v1 Ship `68:135` and its states.
 *
 * Rule 4: Home leads with the server you will join, who of your friends is on it, and your
 * character in its equipped cape. Rule 3: the loadout is a quiet strip, not a launch
 * decision. Rule 6: only data we actually have — the SLP ping and player count, the last
 * session as the game reported it; nothing invented.
 *
 * One component, nine frames. Which one is on screen is derived, never set:
 *
 *   `76:5335` Playing           the launch store is `running`
 *   `76:4091` Title screen      the play bar's server is "Title screen"
 *   `81:198`  Offline           the webview says the machine is offline
 *   `81:1634` Server unreachable the target's SLP ping failed; retries every 10 s
 *   `81:3035` First launch      no session has ever been recorded
 *   `81:4445` Game crashed      the last game exited non-zero — a card over the rest
 *   `68:135`  Ready             otherwise
 *
 * (`76:1556` / `76:2833` / `76:249` are this screen with a play bar menu or the Friends
 * drawer over it; those belong to `PlayBar` and `FriendsDrawer`.)
 */

import { useEffect, useRef, useState } from 'react';

import chevronDown from '../assets/icons/chevron-down.svg';
import chevronRight from '../assets/icons/chevron-right.svg';
import loadoutIcon from '../assets/icons/loadout.svg';
import newIcon from '../assets/icons/new.svg';
import sessionIcon from '../assets/icons/session.svg';
import crashedArt from '../assets/illustrations/crashed.svg';
import noSessionsArt from '../assets/illustrations/no-sessions.svg';
import offlineArt from '../assets/illustrations/offline.svg';
import unreachableArt from '../assets/illustrations/server-unreachable.svg';
import { CategoryCube, Character, Initial } from '../features/art';
import { CATEGORY_HUE_VAR, CATEGORY_LABEL, TOTAL_MODS, countsFor, totalOn } from '../local/categories';
import { findItem } from '../local/catalog';
import { hypixelReady } from '../local/hypixelReady';
import { useOnline } from '../local/online';
import { useCosmetics } from '../stores/cosmetics';
import { friendsOn, useFriends } from '../stores/friends';
import { formatDuration, useLaunch, type LastSession } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { nameForHost, useServers, useTargetServer, type ServerEntry } from '../stores/servers';
import { useUi } from '../stores/ui';

/** How often a reachable server is re-pinged, and how soon an unreachable one is retried. */
const PING_EVERY_MS = 30_000;
const RETRY_MS = 10_000;

// ------------------------------------------------------------------ helpers

/** `Yesterday`, `Today`, `3 days ago`, `12 Sep` — the session sub line. */
function dayText(at: number, now = Date.now()): string {
  const day = (t: number) => Math.floor((t - new Date(t).getTimezoneOffset() * 60_000) / 86_400_000);
  const diff = day(now) - day(at);
  if (diff <= 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  if (diff < 7) return `${diff} days ago`;
  return new Date(at).toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

/** `Moss`, `Moss and tavi`, `Moss, tavi and Rin_` — as the frame puts it, in capitals by CSS. */
function namesSentence(names: string[]): string {
  if (names.length <= 1) return names[0] ?? '';
  if (names.length <= 3) return `${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`;
  return `${names.slice(0, 2).join(', ')} and ${names.length - 2} others`;
}

/** A countdown that restarts every time `from` moves. */
function useCountdown(from: number | undefined, ms: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (from === undefined) return undefined;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [from]);
  if (from === undefined) return 0;
  return Math.max(0, Math.ceil((from + ms - now) / 1000));
}

/** Keep the target's ping fresh: every 30 s when it answers, every 10 s when it does not. */
function usePingLoop(host: string | null, online: boolean) {
  const ping = useServers((s) => s.ping);
  const status = useServers((s) => (host ? s.pings[host]?.status : undefined));
  useEffect(() => {
    if (!host || !online) return undefined;
    void ping(host);
    return undefined;
  }, [host, online, ping]);
  useEffect(() => {
    if (!host || !online || status === 'pinging' || status === undefined) return undefined;
    const t = setTimeout(() => void ping(host), status === 'error' ? RETRY_MS : PING_EVERY_MS);
    return () => clearTimeout(t);
  }, [host, online, status, ping]);
}

// ------------------------------------------------------------------- field

/** The ambient cell field over the hero (`68:218`): 10px cells on a 28px step at 2–3%. */
function Field() {
  return <div className="home__field" aria-hidden="true" />;
}

// -------------------------------------------------------------------- hero

function FriendsOnServer({ host, name }: { host: string; name: string }) {
  const friends = useFriends((s) => s.friends);
  const here = friendsOn(friends, host);
  if (here.length === 0) return null;
  return (
    <p className="homehero__friends">
      <span className="homehero__faces" style={{ width: 28 + (Math.min(here.length, 3) - 1) * 19 }}>
        {here.slice(0, 3).map((f, i) => (
          <span key={f.name} style={{ left: i * 19 }}>
            <Initial name={f.name} size={28} />
          </span>
        ))}
      </span>
      <span className="eyebrow2">
        {namesSentence(here.map((f) => f.name))} {here.length === 1 ? 'is' : 'are'} on {name}
      </span>
    </p>
  );
}

function ServerHero({ server, eyebrow }: { server: ServerEntry; eyebrow: string }) {
  const ping = useServers((s) => s.pings[server.host]);
  const ok = ping?.status === 'ok' && ping.result ? ping.result : null;
  const parts = [server.host];
  if (ok) parts.push(`${ok.latency_ms} ms`, `${ok.online.toLocaleString('en-US')} playing`);
  else parts.push('pinging…');

  return (
    <section className="homehero">
      <p className="eyebrow2">{eyebrow}</p>
      <h1 className="homehero__title">{server.name}</h1>
      <p className="homehero__line tnum">{parts.join('  ·  ')}</p>
      <FriendsOnServer host={server.host} name={server.name} />
    </section>
  );
}

function UnreachableHero({ server }: { server: ServerEntry }) {
  const at = useServers((s) => s.pings[server.host]?.at);
  const pinging = useServers((s) => s.pings[server.host]?.status === 'pinging');
  const setPlayMenu = useUi((s) => s.setPlayMenu);
  const left = useCountdown(at, RETRY_MS);
  return (
    <section className="homehero">
      <p className="eyebrow2">Can't reach {server.name}</p>
      <h1 className="homehero__title">{server.name}</h1>
      <p className="homehero__line">
        No response from {server.host}
        {'  ·  '}
        <span className="tnum">{pinging || left === 0 ? 'retrying now' : `retrying in ${left} s`}</span>
      </p>
      <button type="button" className="btn2 btn2--secondary homehero__action" onClick={() => setPlayMenu('server')}>
        Choose another server
      </button>
    </section>
  );
}

function TitleHero({ eyebrow }: { eyebrow: string }) {
  return (
    <section className="homehero">
      <p className="eyebrow2">{eyebrow}</p>
      <h1 className="homehero__title">Minecraft 1.8.9</h1>
      <p className="homehero__line">Launches to the title screen{'  ·  '}pick a server in game</p>
    </section>
  );
}

function OfflineState() {
  const ping = useServers((s) => s.ping);
  const target = useServers((s) => s.target);
  return (
    <section className="home__offline">
      <img src={offlineArt} width={312} height={208} alt="" />
      <p className="eyebrow2">Offline</p>
      <h1 className="display2">You're offline</h1>
      <p className="home__offline-line">
        Sign-in, friends and the Store need a connection.
        <br />
        You can still launch to the title screen.
      </p>
      <button
        type="button"
        className="btn2 btn2--secondary"
        onClick={() => {
          // The webview flips `navigator.onLine` by itself when the OS does; a retry is a
          // ping, which is the only thing a person can actually do from here.
          window.dispatchEvent(new Event(navigator.onLine ? 'online' : 'offline'));
          if (target) void ping(target);
        }}
      >
        Retry
      </button>
    </section>
  );
}

// -------------------------------------------------------------- the figure

function Wearing() {
  const capeId = useCosmetics((s) => s.equipped.cape);
  const openStoreItem = useUi((s) => s.openStoreItem);
  const go = useUi((s) => s.go);
  const cape = findItem(capeId);
  return (
    <figure className="home__figure">
      <Character cape={cape?.art} view="back" />
      <figcaption className="home__wearing">
        <span className="eyebrow2">{cape ? `Wearing ${cape.name}` : 'No cape'}</span>
        <button type="button" className="ghostlink" onClick={() => (cape ? openStoreItem(cape.id) : go('store'))}>
          Store
          <img src={chevronRight} width={5} height={8} alt="" />
        </button>
      </figcaption>
    </figure>
  );
}

// ----------------------------------------------------------- the strip

function LoadoutColumn() {
  const { active, library, switchTo } = useLoadouts();
  const go = useUi((s) => s.go);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false);
    window.addEventListener('mousedown', onDown);
    return () => window.removeEventListener('mousedown', onDown);
  }, [open]);

  const counts = countsFor(active);
  const on = totalOn(active);

  return (
    <div className="strip__col strip__col--loadout">
      <p className="strip__label">
        <img src={loadoutIcon} width={15} height={15} alt="" />
        <span className="eyebrow2">Loadout</span>
        <span className="strip__spacer" />
        <button type="button" className="ghostlink" onClick={() => go('mods')}>
          Edit mods
          <img src={chevronRight} width={5} height={8} alt="" />
        </button>
      </p>

      <div className="strip__picker" ref={ref}>
        <button
          type="button"
          className="strip__title strip__title--button"
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={() => setOpen((o) => !o)}
        >
          {active?.name ?? '—'}
          <img src={chevronDown} width={9} height={5} alt="" />
        </button>
        {open ? (
          <div className="popmenu popmenu--loadout" role="menu" aria-label="Loadout">
            {library.map((l) => (
              <button
                key={l.id}
                type="button"
                role="menuitemradio"
                aria-checked={l.id === active?.id}
                className={`popitem${l.id === active?.id ? ' is-selected' : ''}`}
                onClick={() => {
                  setOpen(false);
                  void switchTo(l.id);
                }}
              >
                <span className="popitem__text">
                  <span className="popitem__title">{l.name}</span>
                </span>
                {l.id === active?.id ? <span className="popitem__dot" aria-hidden="true" /> : null}
              </button>
            ))}
          </div>
        ) : null}
      </div>

      <p className="strip__sub tnum">
        {on} of {TOTAL_MODS} mods on{'  ·  '}L switches in game
      </p>

      <div className="strip__meter" aria-label={`${on} of ${TOTAL_MODS} mods on`}>
        {counts.map(({ category, on: n, total }) => (
          <span key={category} className="strip__group">
            {Array.from({ length: total }, (_, i) => (
              <i key={i} style={i < n ? { background: CATEGORY_HUE_VAR[category] } : undefined} />
            ))}
          </span>
        ))}
      </div>

      <p className="strip__legend">
        {counts.map(({ category, on: n }) => (
          <span key={category}>
            <CategoryCube category={category} />
            <span className="tnum">
              {n} {CATEGORY_LABEL[category]}
            </span>
          </span>
        ))}
      </p>
    </div>
  );
}

function SessionColumn({ session }: { session: LastSession | null }) {
  const library = useLoadouts((s) => s.library);
  return (
    <div className="strip__col strip__col--session">
      <p className="strip__label">
        <img src={sessionIcon} width={15} height={15} alt="" />
        <span className="eyebrow2">Last session</span>
      </p>
      {session ? (
        <>
          <p className="strip__title tnum">
            {formatDuration(session.played_ms)} on {session.server ? nameForHost(session.server) : 'the title screen'}
          </p>
          <p className="strip__sub">
            {dayText(session.ended_at)}
            {'  ·  '}
            {library.find((l) => l.id === session.loadout)?.name ?? session.loadout}
          </p>
          <div className="strip__stats">
            <span>
              <span className="strip__stat tnum">{Math.round(session.fps_avg)}</span>
              <span className="strip__statlabel">fps average</span>
            </span>
            {session.ping_avg !== undefined ? (
              <span>
                <span className="strip__stat tnum">{Math.round(session.ping_avg)} ms</span>
                <span className="strip__statlabel">average ping</span>
              </span>
            ) : null}
          </div>
        </>
      ) : (
        <div className="strip__empty">
          <img src={noSessionsArt} width={149} height={99} alt="" />
          <span>
            <span className="strip__title">No sessions yet</span>
            <span className="strip__sub">Your first match shows up here.</span>
          </span>
        </div>
      )}
    </div>
  );
}

export const WHATS_NEW = {
  title: 'VOID 1.0 is here',
  body: 'Capes, trails and nameplates are in the Store, and your friends are one click away, top right.',
  notes: [
    'Home leads with the server you will join, who of your friends is on it, and your character in its cape.',
    'One play bar on every screen: pick a server and Launch joins it — or launch to the title screen.',
    'The Store: capes, trails, emotes and nameplates. Cosmetic only, visible to other VOID players.',
    'Friends: see who is online and in a match, and add anyone by their Minecraft username.',
    'In game, Right Shift opens the full menu in the lobby and a quick loadout panel in a match.',
  ],
};

function NewColumn() {
  const setNotesOpen = useUi((s) => s.setNotesOpen);
  return (
    <div className="strip__col strip__col--new">
      <p className="strip__label">
        <img src={newIcon} width={13} height={13} alt="" />
        <span className="eyebrow2">What's new</span>
      </p>
      <p className="strip__title">{WHATS_NEW.title}</p>
      <p className="strip__body">{WHATS_NEW.body}</p>
      <span className="strip__spacer" />
      <button type="button" className="ghostlink strip__notes" onClick={() => setNotesOpen(true)}>
        Release notes
        <img src={chevronRight} width={5} height={8} alt="" />
      </button>
    </div>
  );
}

// ------------------------------------------------------------- the crash

function CrashCard() {
  const crash = useLaunch((s) => s.crash);
  const dismiss = useLaunch((s) => s.dismissCrash);
  const start = useLaunch((s) => s.start);
  const launchedServer = useLaunch((s) => s.launchedServer);
  const active = useLoadouts((s) => s.active);
  const setLogOpen = useUi((s) => s.setLogOpen);
  if (!crash) return null;
  const where = crash.server ? ` into ${nameForHost(crash.server)}` : '';
  return (
    <div className="home__crash" role="alertdialog" aria-labelledby="crash-title">
      <div className="crashcard">
        <img src={crashedArt} width={240} height={160} alt="" />
        <p id="crash-title" className="crashcard__title">
          Minecraft closed unexpectedly
        </p>
        <p className="crashcard__line tnum">
          Exit code {crash.code}
          {'  ·  '}
          {formatDuration(crash.played_ms)}
          {where}
          {'  ·  '}your settings are safe
        </p>
        <div className="crashcard__actions">
          <button type="button" className="btn2 btn2--secondary" onClick={() => setLogOpen(true)}>
            View log
          </button>
          <button
            type="button"
            className="btn2 btn2--primary"
            onClick={() => {
              dismiss();
              if (active) void start(active.id, launchedServer);
            }}
          >
            Launch again
          </button>
        </div>
        <button type="button" className="crashcard__close" aria-label="Dismiss" onClick={dismiss}>
          ×
        </button>
      </div>
    </div>
  );
}

// ------------------------------------------------------------- the screen

export function HomeScreen() {
  const active = useLoadouts((s) => s.active);
  const phase = useLaunch((s) => s.phase);
  const startedAt = useLaunch((s) => s.startedAt);
  const lastSession = useLaunch((s) => s.lastSession);
  const crash = useLaunch((s) => s.crash);
  const target = useTargetServer();
  const online = useOnline();
  const pingStatus = useServers((s) => (target ? s.pings[target.host]?.status : undefined));
  usePingLoop(target?.host ?? null, online);

  // Minute clock for "IN GAME · 12 MIN".
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (phase !== 'running') return undefined;
    const t = setInterval(() => setNow(Date.now()), 15_000);
    return () => clearInterval(t);
  }, [phase]);

  const firstLaunch = !lastSession && (active?.stats?.played_ms ?? 0) === 0;
  const readiness = active ? hypixelReady(active) : null;
  const ready = readiness ? (readiness.ready ? 'Hypixel-ready' : 'Review mods') : null;

  let eyebrow = [firstLaunch ? 'Welcome to VOID' : 'Ready to play', target ? ready : null]
    .filter(Boolean)
    .join('  ·  ');
  if (phase === 'running') eyebrow = `In game  ·  ${formatDuration(startedAt ? now - startedAt : 0)}`;
  if (phase === 'preparing' || phase === 'launching') eyebrow = 'Launching';

  const unreachable = online && target && pingStatus === 'error' && phase !== 'running';
  const state = !online
    ? 'offline'
    : crash
      ? 'crashed'
      : phase === 'running'
        ? 'playing'
        : !target
          ? 'title'
          : unreachable
            ? 'unreachable'
            : firstLaunch
              ? 'first'
              : 'ready';

  return (
    <div className="home" data-state={state}>
      <Field />

      <div className="home__top">
        {state === 'offline' ? (
          <OfflineState />
        ) : (
          <>
            {!target ? (
              <TitleHero eyebrow={eyebrow} />
            ) : unreachable ? (
              <UnreachableHero server={target} />
            ) : (
              <ServerHero server={target} eyebrow={eyebrow} />
            )}
            {unreachable ? (
              <img className="home__unreachable" src={unreachableArt} width={384} height={256} alt="" />
            ) : (
              <Wearing />
            )}
          </>
        )}
      </div>

      <hr className="home__divider" />

      <div className="strip">
        <LoadoutColumn />
        <span className="strip__rule" />
        <SessionColumn session={lastSession} />
        <span className="strip__rule" />
        <NewColumn />
      </div>

      <CrashCard />
    </div>
  );
}
