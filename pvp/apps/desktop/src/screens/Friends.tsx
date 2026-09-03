/**
 * Friends — `244:431`. Presentational, per the brief: no Supabase yet.
 *
 * §16.2 is still open ("reuse VOID's Supabase or start clean?"), and party implies
 * presence and invites — a whole backend. So this screen renders the frame with the
 * frame's own data and every action disabled, with the import-code field carrying an
 * explicit "coming soon". Nothing here fakes a network.
 *
 * `FriendRow`, `GroupCaption`, `PartyMemberRow`, `Pane` and `PositionChips` are all
 * `@void/ui`'s — the party pane's row variant is the package's `compact` one, which is
 * exactly the launcher treatment the design describes (r 12, `--party-row-bg`, 32px
 * avatar) as opposed to the 64px overlay row.
 */

import {
  Button,
  FilterTabs,
  FriendRow,
  GroupCaption,
  Pane,
  Panel,
  PartyMemberRow,
  PositionChips,
  SearchBar,
} from '@void/ui';
import { useState } from 'react';

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

const QUEUES = [
  { id: 'bedwars', label: 'Bedwars 4v4' },
  { id: 'duels', label: 'Duels' },
  { id: 'uhc', label: 'UHC' },
];

export function FriendsScreen() {
  const [tab, setTab] = useState<Tab>('Online');
  const [query, setQuery] = useState('');

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
      headerRight={
        <>
          <SearchBar variant="panel" narrow placeholder="Find a friend" value={query} onChange={setQuery} />
          <FilterTabs
            label="Friends list"
            tabs={[
              { id: 'Online', label: 'Online', count: 3 },
              { id: 'All', label: 'All', count: 8 },
              { id: 'Requests', label: 'Requests', count: 2, countTone: 'ok' },
            ]}
            value={tab}
            onChange={(id) => setTab(id as Tab)}
          />
          <span className="v-spacer" />
          <Button variant="accent" icon="plus" disabled title="Needs a friends backend (§16.2)">
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

          {online.length > 0 ? <GroupCaption label="Online" count={`· ${online.length}`} /> : null}
          {online.map((f) => (
            <FriendRow
              key={f.name}
              name={f.name}
              status={f.status}
              presence="online"
              action={f.action}
              actionVariant={f.action === 'Join' ? 'chip-accent' : 'chip'}
            />
          ))}

          {offline.length > 0 ? <GroupCaption label="Offline" count={`· ${offline.length}`} /> : null}
          {offline.map((f) => (
            <FriendRow
              key={f.name}
              name={f.name}
              status={f.status}
              presence="offline"
              action={f.action}
            />
          ))}
        </div>

        <Pane heading="Your party" headingAside={<span className="pane__count">2 / 4</span>}>
          <div className="party">
            {/* The role is the row's meta line, tinted; `badge={null}` is what asks the
                compact variant for its trailing status dot without a label beside it. */}
            <PartyMemberRow
              variant="compact"
              className="party__row party__row--leader"
              name="Searge"
              meta="Leader"
              badge={null}
              badgeTone="accent"
            />
            <PartyMemberRow
              variant="compact"
              className="party__row party__row--ready"
              name="marrow"
              meta="Ready"
              badge={null}
              badgeTone="ok"
            />
          </div>

          <Button variant="ghost" icon="users" block disabled>
            Invite 2 more
          </Button>

          <span className="v-spacer" />

          <GroupCaption label="Queue" />
          <PositionChips options={QUEUES} value="bedwars" aria-label="Queue" />

          <Button variant="accent" block disabled>
            Queue with party
          </Button>
          <Button variant="text" block disabled>
            Leave party
          </Button>
        </Pane>
      </div>
    </Panel>
  );
}
