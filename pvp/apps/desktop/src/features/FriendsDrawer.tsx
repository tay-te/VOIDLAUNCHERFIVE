/**
 * The Friends drawer — Figma v1 Ship `76:1474` and its five states (`56:2` add friend,
 * `56:94` add friend · error, `56:171` pending, `56:253` empty, `81:6039` no match).
 *
 * A 320px sheet over the panel's right edge, opened from the navbar's friends button
 * (rule 1). Rule 7 is the whole scope: the list, online / in a match, requests in and out,
 * and adding by Minecraft username (3–16 letters, digits, `_`). No party, no chat.
 *
 * The data is `stores/friends` — local until there is a friends backend (§16.2); see that
 * file for what that means for a real install.
 */

import { useEffect, useRef, useState } from 'react';

import searchIcon from '../assets/icons/search.svg';
import userPlus from '../assets/icons/user-plus.svg';
import friendsEmpty from '../assets/illustrations/friends-empty.svg';
import noResults from '../assets/illustrations/no-results.svg';
import { Initial } from './art';
import {
  USERNAME_HELP,
  lastSeenText,
  offlineFriends,
  onlineFriends,
  useFriends,
  type Friend,
} from '../stores/friends';
import { nameForHost } from '../stores/servers';
import { useUi } from '../stores/ui';

/** `Duels · Hypixel`, `In the lobby`, `Online` — what a friend is doing (`56:42`). */
function activity(friend: Friend): string {
  const p = friend.presence;
  if (p.state !== 'online') return lastSeenText(p.lastSeen);
  if (p.lobby) return 'In the lobby';
  if (p.game && p.server) return `${p.game} · ${nameForHost(p.server)}`;
  if (p.server) return nameForHost(p.server);
  return 'Online';
}

function Row({ name, sub, online, dim, children }: { name: string; sub: string; online?: boolean; dim?: boolean; children?: React.ReactNode }) {
  return (
    <li className={`frow${dim ? ' is-dim' : ''}`}>
      <Initial name={name} size={32} online={online} dim={dim} />
      <span className="frow__text">
        <span className="frow__name">{name}</span>
        <span className="frow__sub">{sub}</span>
      </span>
      {children}
    </li>
  );
}

function Section({ label, count, children }: { label: string; count: number; children: React.ReactNode }) {
  return (
    <section className="fsection">
      <h3 className="fsection__label">
        {label} · <span className="tnum">{count}</span>
      </h3>
      <ul className="fsection__rows">{children}</ul>
    </section>
  );
}

function Empty({ art, title, body }: { art: string; title: string; body: string }) {
  return (
    <div className="fempty">
      <img src={art} width={240} height={160} alt="" />
      <p className="fempty__title">{title}</p>
      <p className="fempty__body">{body}</p>
    </div>
  );
}

export function FriendsDrawer() {
  const open = useUi((s) => s.friendsOpen);
  const setOpen = useUi((s) => s.setFriendsOpen);
  const { friends, incoming, outgoing, request, accept, decline, cancel } = useFriends();
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const ref = useRef<HTMLElement>(null);

  // Escape closes the add row first, then the drawer.
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (adding) {
        setAdding(false);
        setError(null);
      } else setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, adding, setOpen]);

  useEffect(() => {
    if (!open) {
      setAdding(false);
      setName('');
      setError(null);
      setQuery('');
    }
  }, [open]);

  if (!open) return null;

  const q = query.trim().toLowerCase();
  const match = (n: string) => !q || n.toLowerCase().includes(q);
  const online = onlineFriends(friends).filter((f) => match(f.name));
  const offline = offlineFriends(friends).filter((f) => match(f.name));
  const inc = incoming.filter(match);
  const out = outgoing.filter(match);
  const nothingAtAll = friends.length + incoming.length + outgoing.length === 0;
  const nothingMatches = !nothingAtAll && online.length + offline.length + inc.length + out.length === 0;

  const send = () => {
    const problem = request(name);
    setError(problem);
    if (!problem) {
      setName('');
      setAdding(false);
    }
  };

  return (
    <aside className="sheet sheet--friends" aria-label="Friends" ref={ref}>
      <header className="sheet__head">
        <h2 className="sheet__title">Friends</h2>
        {adding ? (
          <button
            type="button"
            className="btn2 btn2--secondary"
            onClick={() => {
              setAdding(false);
              setError(null);
              setName('');
            }}
          >
            Cancel
          </button>
        ) : (
          <button type="button" className="btn2 btn2--secondary" onClick={() => setAdding(true)}>
            <img src={userPlus} width={15} height={13} alt="" />
            Add friend
          </button>
        )}
      </header>

      {adding ? (
        <>
          <form
            className="faddrow"
            onSubmit={(e) => {
              e.preventDefault();
              send();
            }}
          >
            <input
              autoFocus
              className={`ffield${error ? ' is-error' : ''}`}
              value={name}
              maxLength={24}
              placeholder="Minecraft username"
              aria-label="Minecraft username"
              aria-invalid={Boolean(error)}
              aria-describedby="friends-helper"
              spellCheck={false}
              onChange={(e) => {
                setName(e.target.value);
                setError(null);
              }}
            />
            <button type="submit" className="btn2 btn2--secondary btn2--sm">
              Send
            </button>
          </form>
          <p id="friends-helper" className={`fhelper${error ? ' is-error' : ''}`} role={error ? 'alert' : undefined}>
            {error ?? USERNAME_HELP}
          </p>
        </>
      ) : (
        <label className="fsearch">
          <img src={searchIcon} width={13} height={13} alt="" />
          <input
            value={query}
            placeholder="Search friends"
            aria-label="Search friends"
            spellCheck={false}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
      )}

      <div className="sheet__scroll">
        {nothingAtAll && !adding ? <Empty art={friendsEmpty} title="No friends yet" body={USERNAME_HELP} /> : null}
        {nothingMatches ? (
          <Empty art={noResults} title={`No one called “${query.trim()}”`} body="Check the spelling, or add them by username." />
        ) : null}

        {inc.length > 0 ? (
          <Section label="Requests" count={inc.length}>
            {inc.map((n) => (
              <Row key={n} name={n} sub="New request">
                <button type="button" className="btn2 btn2--secondary btn2--sm" onClick={() => accept(n)}>
                  Accept
                </button>
                <button type="button" className="btn2 btn2--ghost btn2--sm" onClick={() => decline(n)}>
                  Decline
                </button>
              </Row>
            ))}
          </Section>
        ) : null}

        {out.length > 0 ? (
          <Section label="Sent" count={out.length}>
            {out.map((n) => (
              <Row key={n} name={n} sub="Request sent">
                <button type="button" className="btn2 btn2--secondary btn2--sm" onClick={() => cancel(n)}>
                  Cancel
                </button>
              </Row>
            ))}
          </Section>
        ) : null}

        {online.length > 0 ? (
          <Section label="Online" count={online.length}>
            {online.map((f) => (
              <Row key={f.name} name={f.name} sub={activity(f)} online />
            ))}
          </Section>
        ) : null}

        {offline.length > 0 ? (
          <Section label="Offline" count={offline.length}>
            {offline.map((f) => (
              <Row key={f.name} name={f.name} sub={activity(f)} dim />
            ))}
          </Section>
        ) : null}
      </div>
    </aside>
  );
}
