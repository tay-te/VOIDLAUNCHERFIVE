/**
 * Overlay — Party · frame `244:1426`.
 *
 * Presentational only, deliberately. `bridge.json` is a closed surface of five events and eight
 * calls, and none of them carries party, presence or queue state; "Friends / Party" is still
 * open question §16.2 (reuse VOID's Supabase, or start clean), and `docs/launcher-roster.md` §4
 * carries the dependency order. So nothing here is wired, and nothing here invents a bridge call.
 * The copy, the geometry and the interaction affordances are the frame's, so the screen is ready
 * the day a party channel exists.
 *
 * **It used to invent the people, too** — two named party members, two named invites, and tab
 * counts of 3 and 2 — under a footer that talked about push-to-talk and said nothing about none
 * of it being real. The launcher's Friends screen had the same fixture and at least disagreed
 * with itself in a footer; this one simply presented as working. The layout stays and the people
 * are gone, which is the same call `local/friends.ts` records on the other side.
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

// The footer says what is true. It used to promise party chat and push-to-talk on a screen
// with no party channel behind it, which is the one thing a placeholder must not do.
export const PARTY_FOOTER =
  'Parties need a backend — none is wired yet   ·   the layout is ready   ·   R-Shift closes';

const TABS = [
  { id: 'party', label: 'Party' },
  // No counts. They were 3 and 2, describing four hardcoded people — and a count is the part of
  // a placeholder that reads as a fact, because it is the part that looks like it came from
  // somewhere.
  { id: 'friends', label: 'Friends' },
  { id: 'requests', label: 'Requests' },
];

/** Nobody, until there is somewhere to ask. See this file's header. */
const MEMBERS: { name: string; meta: string; badge: string; tone: 'accent' | 'ok' }[] = [];
const INVITES: { name: string; meta: string }[] = [];

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
        className="party-panel"
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
            <GroupCaption label="In your party" count={`·  ${MEMBERS.length} of 4`} />
            {MEMBERS.length === 0 ? (
              <p className="party__empty">
                Parties need a backend, and VOID does not run one yet.
              </p>
            ) : null}
            {MEMBERS.map((member) => (
              <PartyMemberRow
                key={member.name}
                name={member.name}
                meta={member.meta}
                badge={member.badge}
                badgeTone={member.tone}
              />
            ))}

            <GroupCaption className="party__invite-cap" label="Invite" />
            {INVITES.map((invite) => (
              <InviteRow key={invite.name} name={invite.name} meta={invite.meta} />
            ))}
          </div>

          <Pane heading="Queue" className="party__pane">
            <span className="v-caption v-caption--sm">Game</span>
            {/* The three game rows are a 6px-gap group inside the pane's own 12px
                rhythm, as the frame stacks them. */}
            <div className="queue-rows">
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
            </div>

            <span className="v-caption v-caption--sm">Loadout</span>
            <div className="queue-picker">
              <IconWell icon="sword" size={24} solid />
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
