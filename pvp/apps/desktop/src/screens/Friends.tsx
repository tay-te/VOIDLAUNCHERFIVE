/**
 * Friends — `244:431`. The frame's layout, with nothing in it, because there is nothing.
 *
 * §16.2 is still open ("reuse VOID's Supabase or start clean?"), and party implies presence and
 * invites — a whole backend. `docs/launcher-roster.md` §4 carries the dependency order.
 *
 * **This screen used to render the frame's own five people**, and the header counted them: three
 * online, eight all, two requests. The footer said no backend was wired, which made the screen
 * disagree with itself — and the Play dock printed the same invented three with no footer near
 * it at all. The layout stays, because the component structure *is* the design and it is ready
 * the day a `friends_list` command exists; the data is gone, and the counts with it.
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
  PositionChips,
  SearchBar,
} from '@void/ui';
import { useState } from 'react';

import { FRIENDS } from '../local/friends';

const TABS = ['Online', 'All', 'Requests'] as const;
type Tab = (typeof TABS)[number];

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
            // Counted from the list rather than written down. They were 3 / 8 / 2 — three
            // numbers describing five hardcoded people, which is how a placeholder becomes a
            // claim. Derived, they are honest whatever the list holds.
            tabs={[
              { id: 'Online', label: 'Online', count: FRIENDS.filter((f) => f.online).length },
              { id: 'All', label: 'All', count: FRIENDS.length },
              { id: 'Requests', label: 'Requests', count: 0, countTone: 'ok' },
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
          {tab === 'Requests' || FRIENDS.length === 0 ? (
            <p className="list__empty">
              Friends need a backend, and VOID does not run one yet. Nothing to show.
            </p>
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

        {/* The party had two named members in it and a "2 / 4" count. Same problem as the
            list, and the same answer: the pane stays, the people go. `@void/ui`'s
            `PartyMemberRow` is what it will draw, and the in-game Party screen still uses it. */}
        <Pane heading="Your party" headingAside={<span className="pane__count">0 / 4</span>}>
          <p className="list__empty">Parties need the same backend. Nobody here yet.</p>

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
