/**
 * Reach display — how far away the last hit that connected was.
 *
 * ## The rule this widget deliberately does not own
 *
 * `docs/mod-roster.md` §6.1 allows a reach display as a readout of your own attack distance and
 * forbids one that reports a distance to a target you have not hit — a reach *indicator*, which
 * is the disallowed thing. The two draw the same figure in the same place and differ only in
 * **when the number is allowed to change**.
 *
 * So the rule is not here. It is in the sensor (`mod/.../sensor/ReachTally.java`), where it is one
 * class with a test, and it is stated on `bridge.json`'s `reach` field so every reader inherits it.
 * This widget subscribes to a number and draws it. That is the point: a page that decided for
 * itself when to refresh could turn a permitted mod into a disallowed one without touching the
 * sensor, the schema, or a review — so it is not given the chance.
 *
 * The same reasoning is why there is no `sample` fixture that moves. Below, `sample` supplies one
 * plausible reading for a page opened outside a fight, and it is a constant.
 *
 * ## Nothing until something has been hit
 *
 * `reach` is `null` until the first landed attack, and null is not zero: a reach of 0 would be a
 * point-blank swing, and what is actually being described is a session in which nothing has
 * connected. So the HUD draws nothing — the rule every readout here follows — and the mod page
 * stands on {@link SAMPLE_BLOCKS}, because an empty box on the page whose whole job is showing you
 * the mod is `design/rendering-invariants.md` §15's failure.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here: `HudSlot`
 * applies the shared chrome block around the widget and `PreviewZoom` applies it in the same
 * place on the mod page (`hud/chrome.ts`).
 */

import { memo } from 'react';

import { ReachChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const REACH_ID = 'reach';

/**
 * The reading a preview stands on: **3.14 blocks**.
 *
 * 1.8.9 gives a survival player 3.0 blocks of reach and the client will raycast to that limit, so
 * a swing that connects lands just under it — 3.14 is a hit at the edge of vanilla's own range,
 * which is both the commonest interesting reading and the one that makes `warn_above` legible:
 * the setting's useful band starts around 3, so a fixture below it would leave the threshold with
 * nothing to bite on until the slider passed the fixture rather than passing the *reach*.
 *
 * It is a constant, and that is load-bearing rather than lazy. A fixture that moved would be a
 * preview drawing a live distance, which is the one thing this mod is not allowed to be.
 */
const SAMPLE_BLOCKS = 3.14;

export const HudReach = memo(function HudReach({ variant, sample }: HudWidgetProps) {
  const live = useVoidStore((s) => s.reach);
  const showLabel = useVoidStore((s) => modSettings(s.loadout, REACH_ID).show_label !== false);
  const warnAbove = useVoidStore((s) => Number(modSettings(s.loadout, REACH_ID).warn_above ?? 0));
  const chip = (blocks: number, key?: number) => (
    <ReachChip
      key={key}
      variant={variant}
      blocks={blocks}
      showLabel={showLabel}
      warnAbove={warnAbove}
    />
  );

  // **The preview draws two chips once a threshold is set, and that is not a licence — it is the
  // only honest picture of a threshold.** `warn_above` decides where one figure changes colour,
  // and a single figure cannot show you where: at 3.14 the chip is plain and stays plain however
  // far the slider is dragged above it, so the control reads as dead. Two readings taken *from
  // the threshold itself* — just under, just over — put one chip on each side of it, so moving
  // the control visibly moves the boundary it owns.
  //
  // `HudPing` makes the same move for `good_ms` / `bad_ms`, and `SAMPLE_ARMOR`'s four different
  // durabilities are the same idea: the fixture is chosen so the setting has something to bite
  // on. In game exactly one chip is ever drawn, and it is this component.
  //
  // With the threshold off there is nothing to straddle, so the preview is the one honest
  // reading — `SAMPLE_BLOCKS`, a hit at the edge of vanilla's own range.
  if (sample && live === null) {
    if (warnAbove <= 0) return chip(SAMPLE_BLOCKS);
    return (
      <div className="v-pingband">
        {[
          Math.max(0, Math.round((warnAbove - 0.4) * 100) / 100),
          Math.round((warnAbove + 0.4) * 100) / 100,
        ].map((blocks, i) => chip(blocks, i))}
      </div>
    );
  }
  const blocks = live ?? (sample ? SAMPLE_BLOCKS : null);
  if (blocks === null) return null;
  return chip(blocks);
});
