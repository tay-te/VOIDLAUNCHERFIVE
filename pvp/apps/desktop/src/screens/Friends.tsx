/**
 * Friends — `244:431`. Presentational, per the brief: no Supabase yet.
 *
 * §16.2 is still open ("reuse VOID's Supabase or start clean?"), and party implies
 * presence and invites — a whole backend. So this screen renders the frame with the
 * frame's own data and every action disabled, with the import-code field carrying an
 * explicit "coming soon". Nothing here fakes a network.
 */

import { useState } from 'react';

import { Avatar, Button, Caption, FilterTabs, Panel, Pane, SearchField, StatusDot, Tag } from '../components';
import { PlusIcon, UsersIcon } from '../components/icons';

const TABS = ['Online', 'All', 'Requests'] as const;
type Tab = (typeof TABS)[number];

interface Friend {
  name: string;
  status: string;
  action: 'Join' | 'Invite' | 'Message';
  online: boolean;
}

const FRIENDS: Friend[] = [
  { name: 'marrow', status: 'Bedwars · Hypixel · 2h', action: 'Join', online: true },
  { name: 'pilot_ash', status: 'Sword duels · Minemen · 40m', action: 'Join', online: true },
  { name: 'nine', status: 'In lobby · Hypixel', action: 'Invite', online: true },
  { name: 'doorframe', status: 'Last seen 4 hours ago', action: 'Message', online: false },
  { name: 'kestrel', status: 'Last seen yesterday', action: 'Message', online: false },
];

export function FriendsScreen() {
  const [tab, setTab] = useState<Tab>('Online');
  const [query, setQuery] = useState('');
  const [code, setCode] = useState('');

  const q = query.trim().toLowerCase();
  const filtered = FRIENDS.filter((f) => {
    if (tab === 'Online' && !f.online) return false;
    if (tab === 'Requests') return false;
    return !q || f.name.includes(q);
  });
  const online = filtered.filter((f) => f.online);
  const offline = filtered.filter((f) => !f.online);

  return (
    <Panel
      title="Friends"
      controls={
        <>
          <SearchField value={query} onChange={setQuery} placeholder="Find a friend" width={200} />
          <FilterTabs
            tabs={TABS}
            value={tab}
            onChange={setTab}
            counts={{ Online: { value: 3 }, All: { value: 8 }, Requests: { value: 2, tone: 'ok' } }}
          />
          <Button variant="accent" icon={PlusIcon} disabled title="Needs a friends backend (§16.2)">
            Add friend
          </Button>
        </>
      }
      footer={<>Friends and parties need a backend — none is wired yet (architecture §16.2).</>}
    >
      <div className="split">
        <div className="list">
          {tab === 'Requests' ? (
            <p className="list__empty">Requests need a friends backend. Nothing to show.</p>
          ) : null}

          {online.length > 0 ? <Caption count={online.length}>ONLINE</Caption> : null}
          {online.map((f) => (
            <FriendRow key={f.name} friend={f} />
          ))}

          {offline.length > 0 ? <Caption count={offline.length}>OFFLINE</Caption> : null}
          {offline.map((f) => (
            <FriendRow key={f.name} friend={f} />
          ))}
        </div>

        <Pane>
          <div className="pane__headline">
            <span className="pane__title">Your party</span>
            <span className="pane__count">2 / 4</span>
          </div>

          <div className="party">
            <div className="party__row">
              <Avatar name="Searge" size={32} />
              <span className="party__text">
                <span className="party__name">Searge</span>
                <span className="party__role party__role--leader">Leader</span>
              </span>
              <StatusDot tone="ok" size={8} />
            </div>
            <div className="party__row">
              <Avatar name="marrow" size={32} />
              <span className="party__text">
                <span className="party__name">marrow</span>
                <span className="party__role party__role--ready">Ready</span>
              </span>
              <StatusDot tone="ok" size={8} />
            </div>
          </div>

          <Button variant="ghost" icon={UsersIcon} full disabled>
            Invite 2 more
          </Button>

          <Caption>QUEUE</Caption>
          <div className="chips">
            <span className="chip chip--selected">Bedwars 4v4</span>
            <span className="chip">Duels</span>
            <span className="chip">UHC</span>
          </div>

          <div className="pane__spacer" />

          <Caption>IMPORT FRIENDS</Caption>
          <div className="import-row">
            <input
              className="import-row__field"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="Import code"
              aria-label="Import code"
              disabled
            />
            <Tag tone="muted">COMING SOON</Tag>
          </div>

          <Button variant="accent" full disabled>
            Queue with party
          </Button>
          <Button variant="text" full disabled>
            Leave party
          </Button>
        </Pane>
      </div>
    </Panel>
  );
}

function FriendRow({ friend }: { friend: Friend }) {
  return (
    <div className={`row row--friend${friend.online ? '' : ' is-offline'}`}>
      <span className="row__avatar">
        <Avatar name={friend.name} size={36} />
        <span className={`row__presence${friend.online ? ' is-online' : ''}`} />
      </span>
      <span className="row__text">
        <span className="row__title">{friend.name}</span>
        <span className="row__sub">{friend.status}</span>
      </span>
      <Button variant={friend.action === 'Join' ? 'chip-accent' : 'chip'} disabled>
        {friend.action}
      </Button>
    </div>
  );
}
