/**
 * Overlay — Party · frame `244:1426`.
 *
 * Presentational only, deliberately. `bridge.json` is a closed surface of five
 * events and six calls; none of them carries party, presence or queue state, and
 * "Friends / Party" is still open question §16.2 (reuse VOID's Supabase, or
 * start clean). Nothing here is wired, and nothing here invents a bridge call.
 * The copy and geometry are the frame's, so the screen is ready the moment a
 * party channel exists.
 */

import { useState } from 'react';
import { useVoidStore } from '@/store/store';
import { Badge, Button, FilterTabs, GroupCaption, IconWell } from '@/ui';
import { Icon } from '@/icons/Icon';
import { Panel } from './Panel';

export const PARTY_FOOTER = 'Party chat  T   ·   push to talk  V   ·   R-Shift closes';

const TABS = [
  { id: 'party', label: 'Party' },
  { id: 'friends', label: 'Friends', count: 3 },
  { id: 'requests', label: 'Requests', count: 2, countTone: 'ok' as const },
];

const MEMBERS = [
  { name: 'Searge', meta: 'Sword PvP  ·  1.8.9', badge: 'Leader', tone: 'accent' as const, tint: '#e8b58a' },
  { name: 'marrow', meta: 'Sword PvP  ·  1.8.9', badge: 'Ready', tone: 'ok' as const, tint: '#f0d79a' },
];

const INVITES = [
  { name: 'pilot_ash', meta: 'Sword duels  ·  Minemen', tint: '#9b7b63' },
  { name: 'nine', meta: 'In lobby  ·  Hypixel', tint: '#8a6b52' },
];

const GAMES = [
  { id: 'bedwars-4v4', title: 'Bedwars 4v4', meta: 'Hypixel  ·  avg 3:40 queue' },
  { id: 'sword-duels', title: 'Sword duels', meta: 'Minemen  ·  instant' },
  { id: 'uhc', title: 'UHC', meta: 'Minemen  ·  next round 12:00' },
];

function Avatar({ name, tint, small }: { name: string; tint: string; small?: boolean }) {
  return (
    <span
      className={`party-avatar${small ? ' party-avatar--sm' : ''}`}
      style={{ background: tint }}
      aria-hidden="true"
    >
      {name.charAt(0).toUpperCase()}
    </span>
  );
}

export function PartyScreen() {
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const loadout = useVoidStore((s) => s.loadout);
  const [tab, setTab] = useState('party');
  const [game, setGame] = useState('bedwars-4v4');

  return (
    <Panel
      title="Party"
      header={<FilterTabs tabs={TABS} value={tab} onChange={setTab} />}
      footer={PARTY_FOOTER}
      onClose={closeMenu}
    >
      <div className="party">
        <div className="party__left">
          <GroupCaption label="In your party" count="2 of 4" />
          {MEMBERS.map((member) => (
            <div className="party-row" key={member.name}>
              <Avatar name={member.name} tint={member.tint} />
              <span className="party-row__body">
                <span className="party-row__name">{member.name}</span>
                <span className="party-row__meta">{member.meta}</span>
              </span>
              <Badge tone={member.tone}>{member.badge}</Badge>
            </div>
          ))}

          <div style={{ height: 6 }} />
          <GroupCaption label="Invite" count="3 online" />
          {INVITES.map((invite) => (
            <div className="party-row party-row--invite" key={invite.name}>
              <Avatar name={invite.name} tint={invite.tint} small />
              <span className="party-row__body">
                <span className="party-row__name">{invite.name}</span>
                <span className="party-row__meta">{invite.meta}</span>
              </span>
              <Button variant="ghost" icon="users" iconSize={12}>
                Invite
              </Button>
            </div>
          ))}
        </div>

        <div className="pane party__pane">
          <div className="pane__heading">Queue</div>
          <div className="lcard__cap">Game</div>
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

          <div className="lcard__cap">Loadout</div>
          <div className="queue-picker">
            <IconWell icon="sword" size={24} on />
            <span>{loadout?.name ?? '—'}</span>
            <span className="queue-picker__chevron">
              <Icon name="chevron-down" size={14} />
            </span>
          </div>

          <div className="pane__spacer" />

          <Button variant="accent" icon="play" iconSize={13} full>
            Queue with party
          </Button>
          <button type="button" className="party__leave">
            Leave party
          </button>
        </div>
      </div>
    </Panel>
  );
}
