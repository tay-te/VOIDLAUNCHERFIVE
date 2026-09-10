/**
 * Overlay — Fight review.
 *
 * `docs/mod-roster.md` §5's first row and the one place this client's architecture is
 * straightforwardly better suited than a plain Fabric mod: a review screen is a *page*, and we
 * already run a page. Lunar's `pvp-info` has two settings; going past it is not a matter of
 * adding a third.
 *
 * ## It adds nothing to the wire, and that is the finding
 *
 * The obvious build is a Java sensor that decides when a fight starts and ends and reports one
 * summary per fight. Everything it would report is already on the tick payload — `hits.dealt`,
 * `hits.taken`, `hits.sprint_dealt`, `reach` — and the only thing it would add is the *timeout*,
 * which is policy. This client's rule is that policy lives on the client: `bridge.json`'s `hits`
 * refuses to carry the combo for exactly this reason, and the same sentence covers a fight.
 *
 * So there is no new sensor, no new message and no protocol bump behind this screen. The store
 * derives fights from counters it was already receiving (`store.ts`, `FIGHT_QUIET_MS`), and the
 * day these should survive a restart, the record is already the shape that would travel.
 *
 * ## What is not on this card, and why
 *
 * **Time to kill.** It is on the roster's own list for this row, and the client cannot know it.
 * A kill is an entity being removed, and an entity is also removed by a chunk unloading, a
 * teleport, or a render-distance change; the client is never told which. Reporting a
 * time-to-kill from a removal would be reporting a fabrication in the one figure players would
 * quote. Duration and the trade are what this client can state exactly, so they are what it
 * states.
 *
 * **Damage.** The server does that arithmetic — armour, enchants, absorption, resistance — and
 * the client sees only its own health, which moves for reasons that are not fights. Hits are
 * what can be counted; hits are what is counted.
 *
 * **Anything about the opponent.** Nothing here is scoped to who you fought, and `store.ts`'s
 * fight rule says why: the client is never told who hit *it*, so an opponent-scoped fight could
 * only ever see half of one. It also keeps the whole screen inside `docs/mod-roster.md` §6.1 —
 * every figure is a fact about your own play, after the fact.
 */

import { FightTimeline, GroupCaption, Pane, Panel, StatTile } from '@/ui';
import { fightIsOver, useVoidStore, type Fight } from '@/store/store';

export const REVIEW_FOOTER =
  'A fight ends after ten quiet seconds   ·   nothing here leaves the client   ·   R-Shift closes';

/** How many fights the list shows. The store keeps ten; this shows all of them. */
const LIST_MAX = 10;

/** `2:07`, or `14s` under a minute — a fight is short, and `0:14` reads like a stopwatch. */
export function fightLength(ms: number): string {
  const seconds = Math.max(0, Math.round(ms / 1000));
  if (seconds < 60) return `${seconds}s`;
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/** Mean of a list, or null when there is nothing to average. */
function mean(values: readonly number[]): number | null {
  if (values.length === 0) return null;
  let sum = 0;
  for (const value of values) sum += value;
  return sum / values.length;
}

/**
 * The trade, as the ratio players quote — or the pair when there is nothing to divide by.
 *
 * The same fallback `TradeChip` makes and for the same reason: a fight with no hits taken has no
 * ratio, and every stand-in is a lie of a different kind.
 */
function tradeFigure(fight: Fight): string {
  if (fight.taken === 0) return `${fight.dealt} / 0`;
  return (fight.dealt / fight.taken).toFixed(1);
}

/** One row in the list of fights. */
function FightRow({
  fight,
  live,
  selected,
  onSelect,
}: {
  fight: Fight;
  live: boolean;
  selected: boolean;
  onSelect: () => void;
}): React.ReactElement {
  return (
    <button
      type="button"
      className={`fightrow${selected ? ' fightrow--selected' : ''}`}
      onClick={onSelect}
    >
      <span className="fightrow__body">
        <span className="fightrow__title tnum">{`${fight.dealt} / ${fight.taken}`}</span>
        <span className="fightrow__meta">
          {live ? 'Now' : fightLength(fight.lastAt - fight.startedAt)}
          {'   ·   '}
          {`${fight.dealt + fight.taken} hits`}
        </span>
      </span>
      {/* The live fight is the only thing on this screen that is still happening, so it is the
          only thing that carries the accent — `design/quiet-cell-system.md` §1's "colour marks a
          state". A finished fight is history and reads as history. */}
      {live ? <span className="fightrow__live" /> : null}
    </button>
  );
}

export function ReviewScreen() {
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const liveFight = useVoidStore((s) => s.liveFight);
  const fights = useVoidStore((s) => s.fights);
  const selectedId = useVoidStore((s) => s.selectedFight);
  const selectFight = useVoidStore((s) => s.selectFight);

  const all = (liveFight === null ? fights : [liveFight, ...fights]).slice(0, LIST_MAX);
  // Falls through to the newest rather than showing nothing, so the screen opens on the fight
  // you just had — which is the only reason anybody opens it.
  const shown = all.find((fight) => fight.id === selectedId) ?? all[0] ?? null;
  const now = Date.now();

  return (
    <div className="panel-wrap">
      <Panel
        surface="overlay"
        animate
        title="Fight review"
        className="review-panel"
        subtitle="Every figure here is your own play, after the fact."
        onClose={closeMenu}
        footer={REVIEW_FOOTER}
      >
        <div className="review">
          <div className="review__left">
            <GroupCaption label="This session" count={`·  ${all.length}`} />
            {all.length === 0 ? (
              // Not an empty box: `design/rendering-invariants.md` §15 — a screen whose whole
              // job is showing you something may not be the thing that renders blank. It says
              // what it is waiting for, which is also the only instruction this screen needs.
              <p className="review__empty">
                Nothing yet. A fight opens on the first hit landed or taken, and closes after ten
                quiet seconds.
              </p>
            ) : (
              all.map((fight) => (
                <FightRow
                  key={fight.id}
                  fight={fight}
                  live={fight === liveFight && !fightIsOver(fight, now)}
                  selected={shown !== null && fight.id === shown.id}
                  onSelect={() => selectFight(fight.id)}
                />
              ))
            )}
          </div>

          <Pane heading="The fight" className="review__pane">
            {shown === null ? (
              <p className="review__empty">Pick a fight on the left.</p>
            ) : (
              <FightCard fight={shown} />
            )}
          </Pane>
        </div>
      </Panel>
    </div>
  );
}

/** The card itself — five readings and the shape of the fight underneath them. */
function FightCard({ fight }: { fight: Fight }): React.ReactElement {
  const reach = mean(fight.reaches);
  const cpsBeats = fight.beats.filter((beat) => beat.cps > 0).map((beat) => beat.cps);
  const cps = mean(cpsBeats);
  const peak = cpsBeats.length > 0 ? Math.max(...cpsBeats) : null;
  const share = fight.dealt + fight.taken > 0 ? fight.dealt / (fight.dealt + fight.taken) : 0;
  const resets = fight.dealt > 0 ? fight.sprintDealt / fight.dealt : null;

  return (
    <>
      <div className="review__tiles">
        <StatTile value={tradeFigure(fight)} unit="TRADE" />
        <StatTile
          value={resets === null ? '—' : `${Math.round(resets * 100)}%`}
          unit="SPRINT RESET"
        />
        {/* An em dash rather than `0.00`: a fight with no reach reading is a fight whose hits all
            landed before the first one arrived, and a zero there would be a figure nobody
            earned — the same distinction `reach` itself draws on the wire. */}
        <StatTile value={reach === null ? '—' : reach.toFixed(2)} unit="MEAN REACH" />
        <StatTile value={cps === null ? '—' : cps.toFixed(1)} unit="MEAN CPS" />
        <StatTile value={peak === null ? '—' : String(Math.round(peak))} unit="PEAK CPS" />
        <StatTile value={fightLength(fight.lastAt - fight.startedAt)} unit="LENGTH" />
      </div>

      {/* The share of the fight's hits that were yours, said once. The tiles above carry the
          ratio and this carries the same fact as a level — half full is even, and which side of
          half you are on is legible without reading a digit. Monochrome, because a share has no
          threshold to mark (§1).

          Its own two divs rather than `Meter` or the chips' `HudBar`: `Meter` is a control and a
          reading that cannot be dragged should not look like one, and `HudBar` is scoped to the
          inside of a HUD chip. One local rule is cheaper than widening the shared surface for a
          single use. */}
      <span className="review__share" aria-hidden="true">
        <span className="review__share-fill" style={{ width: `${share * 100}%` }} />
      </span>

      <span className="v-caption v-caption--sm">Second by second</span>
      {fight.beats.length === 0 ? (
        <p className="review__empty">Too short to draw.</p>
      ) : (
        <FightTimeline beats={fight.beats} />
      )}
      <p className="review__legend">
        Above the line, hits you landed   ·   below it, hits you took   ·   behind, your clicking
      </p>
    </>
  );
}
