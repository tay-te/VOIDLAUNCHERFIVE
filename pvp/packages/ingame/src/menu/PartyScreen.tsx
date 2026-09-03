/**
 * Overlay — Party · frame `244:1426`.
 *
 * Presentational only, deliberately. `bridge.json` is a closed surface of five
 * events and six calls, and none of them carries party, presence or queue state;
 * "Friends / Party" is still open question §16.2 (reuse VOID's Supabase, or start
 * clean). So nothing here is wired, and nothing here invents a bridge call. The
 * copy, the geometry and the interaction affordances are the frame's, so the
 * screen is ready the day a party channel exists.
 */

import { useState } from 'react';
import {
  Button,
  FilterTabs,
  GroupCaption,
  Icon,
  IconWell,
  InviteRow,
  Pane,
  Panel,
  PartyMemberRow,
} from '@/ui';
import { useVoidStore } from '@/store/store';

export const PARTY_FOOTER = 'Party chat  T   ·   push to talk  V   ·   R-Shift closes';

const TABS = [
  { id: 'party', label: 'Party' },
  { id: 'friends', label: 'Friends', count: 3 },
  { id: 'requests', label: 'Requests', count: 2, countTone: 'ok' as const },
];

const MEMBERS = [
  { name: 'Searge', meta: 'Sword PvP  ·  1.8.9', badge: 'Leader', tone: 'accent' as const },
  { name: 'marrow', meta: 'Sword PvP  ·  1.8.9', badge: 'Ready', tone: 'ok' as const },
];

const INVITES = [
  { name: 'pilot_ash', meta: 'Sword duels  ·  Minemen' },
  { name: 'nine', meta: 'In lobby  ·  Hypixel' },
];

const GAMES = [
  { id: 'bedwars-4v4', title: 'Bedwars 4v4', meta: 'Hypixel  ·  avg 3:40 queue' },
  { id: 'sword-duels', title: 'Sword duels', meta: 'Minemen  ·  instant' },
  { id: 'uhc', title: 'UHC', meta: 'Minemen  ·  next round 12:00' },
];

export function PartyScreen() {
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const loadout = useVoidStore((s) => s.loadout);
  const [tab, setTab] = useState('party');
  const [game, setGame] = useState('bedwars-4v4');

  return (
    <div className="panel-wrap">
      <Panel
        surface="overlay"
        animate
        title="Party"
        onClose={closeMenu}
        footer={PARTY_FOOTER}
        headerRight={
          <>
            <FilterTabs tabs={TABS} value={tab} onChange={setTab} label="Party tabs" />
            <span className="v-spacer" />
          </>
        }
      >
        <div className="party">
          <div className="party__left">
            <GroupCaption label="In your party" count="·  2 of 4" />
            {MEMBERS.map((member) => (
              <PartyMemberRow
                key={member.name}
                name={member.name}
                meta={member.meta}
                badge={member.badge}
                badgeTone={member.tone}
              />
            ))}

            <div className="party__gap" />
            <GroupCaption label="Invite" count="·  3 online" />
            {INVITES.map((invite) => (
              <InviteRow key={invite.name} name={invite.name} meta={invite.meta} />
            ))}
          </div>

          <Pane heading="Queue" className="party__pane">
            <span className="v-caption v-caption--sm">Game</span>
            {GAMES.map((entry) => (
              <button
                type="button"
                key={entry.id}
                className={`queue-row${entry.id === game ? ' queue-row--selected' : ''}`}
                onClick={() => setGame(entry.id)}
              >
                <span className="queue-row__radio" />
                <span className="queue-row__body">
                  <span className="queue-row__title">{entry.title}</span>
                  <span className="queue-row__meta">{entry.meta}</span>
                </span>
              </button>
            ))}

            <span className="v-caption v-caption--sm">Loadout</span>
            <div className="queue-picker">
              <IconWell icon="sword" size={24} on />
              <span>{loadout?.name ?? '—'}</span>
              <span className="queue-picker__chevron">
                <Icon name="chevron-down" size={14} />
              </span>
            </div>

            <span className="v-spacer" />

            <Button variant="accent" icon="play" block>
              Queue with party
            </Button>
            <button type="button" className="party__leave">
              Leave party
            </button>
          </Pane>
        </div>
      </Panel>
    </div>
  );
}
