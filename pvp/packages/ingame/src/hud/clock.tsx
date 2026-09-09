/**
 * Clock — the real-world time, on the HUD.
 *
 * ## The one readout with no sensor
 *
 * Every other HUD mod draws something the game told it. This one reads the machine's clock, and
 * there could not be a sensor for it: the JVM knows what time it is on the machine it is running
 * on, which is the same machine the page is running on, so a wire message would be a round trip
 * to fetch a value already in hand. It is therefore also the only readout that is correct at the
 * main menu, in singleplayer and on a server alike.
 *
 * ## It owns a timer, and that is rationed
 *
 * `store.ts` opens by stating that the 20 Hz `tick` push is this bundle's only clock. A wall
 * clock cannot be driven — nothing will ever push "it is now the next minute" — so it is one of
 * the three readouts allowed to wake itself, through the shared rule in `hud/second-edge.ts`:
 * one timeout armed for the instant the drawn figure next changes, re-armed from the clock when
 * it fires, and nothing armed at all behind an open menu or in a preview.
 *
 * The step is the *drawn figure's* period, not a second. With `show_seconds` off the chip
 * changes once a minute, so it sleeps through the other fifty-nine seconds rather than repainting
 * sixty times to redraw the same glyphs — which on this surface is sixty full-panel damage
 * rectangles for nothing.
 *
 * ## Formatting is here, not in the chip
 *
 * `TimeChip` takes a string, on the rule `ServerAddressChip` states: a component in `@void/ui`
 * has no business knowing what a meridiem is, and the widget already owns both the `Date` and
 * the setting. It also keeps the twelve-hour suffix a *part of the format* rather than a second
 * switch — a twelve-hour clock without one is ambiguous twice a day, which is not a preference.
 *
 * `scale`, `opacity`, `background`, `border` and `padding` are **not** read here: `HudSlot`
 * applies the whole shared chrome block around the widget, and `PreviewZoom` applies the same
 * block in the same place on the mod page (`hud/chrome.ts`).
 */

import { memo } from 'react';

import { TimeChip } from '@/ui';
import { modSettings, useVoidStore } from '@/store/store';
import { MINUTE_MS, SECOND_MS, useSecondEdge } from './second-edge';
import type { HudWidgetProps } from './widgets';

/** The mod id, in one place. */
export const CLOCK_ID = 'clock';

/**
 * The reading the mod page's preview draws: **21:41:07**, i.e. 9:41:07 PM.
 *
 * A fixture rather than the real clock, and unlike most widgets the fixture *wins* here — the
 * same call `hud/stopwatch.tsx` makes and for the same two reasons. The page is only ever seen
 * with the menu open, where the repaint timer is deliberately not armed, so a live reading would
 * be frozen at the moment the menu opened while looking exactly like a running clock. And it
 * keeps `format` honest independently of what the player's own clock happens to say: at any hour
 * before one in the afternoon the two forms print the same digits, so a live preview would make
 * the setting look inert for more than half of every day.
 *
 * 21:41 is chosen so both settings bite. It is past noon, so `h12` genuinely differs (`9:41 PM`),
 * and the seconds are `07` rather than a round number, so `show_seconds` adds a field nobody
 * could mistake for part of the minutes.
 */
const SAMPLE = { hours: 21, minutes: 41, seconds: 7 };

/** A stored `format` narrowed to what this draws; anything else is the factory default. */
function asClockFormat(value: unknown): 'h24' | 'h12' {
  return value === 'h12' ? 'h12' : 'h24';
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

/**
 * The reading, and the unit that goes after it.
 *
 * Exported for `test/format.test.ts` and for the tile, which prints the same string at a
 * different size — the tile and the chip agreeing about what o'clock it is should not depend on
 * two people writing the same `padStart`.
 */
export function formatClock(
  now: { hours: number; minutes: number; seconds: number },
  format: 'h24' | 'h12',
  showSeconds: boolean,
): { value: string; unit?: string } {
  const tail = showSeconds ? `:${pad(now.seconds)}` : '';
  if (format === 'h24') return { value: `${pad(now.hours)}:${pad(now.minutes)}${tail}` };
  // 0 and 12 both read as 12 on a twelve-hour clock — midnight is `12 AM`, noon is `12 PM`.
  const hour = now.hours % 12 === 0 ? 12 : now.hours % 12;
  return {
    // Not padded: a twelve-hour clock is written `9:41`, and `09:41 PM` is a form nobody uses.
    // It costs a glyph of width at nine hours of the day, which is the trade `h24` exists to
    // avoid for a player who minds — that is what makes these two genuinely different options
    // rather than one option and its abbreviation.
    value: `${hour}:${pad(now.minutes)}${tail}`,
    unit: now.hours < 12 ? 'AM' : 'PM',
  };
}

export const HudClock = memo(function HudClock({ variant, sample }: HudWidgetProps) {
  const format = useVoidStore((s) => asClockFormat(modSettings(s.loadout, CLOCK_ID).format));
  const showSeconds = useVoidStore(
    (s) => modSettings(s.loadout, CLOCK_ID).show_seconds === true,
  );
  const menuOpen = useVoidStore((s) => s.menuOpen);

  // The drawn figure's own period, so a chip showing minutes sleeps through the seconds.
  useSecondEdge(!sample && !menuOpen, showSeconds ? SECOND_MS : MINUTE_MS);

  const now = new Date();
  const reading = formatClock(
    sample ? SAMPLE : { hours: now.getHours(), minutes: now.getMinutes(), seconds: now.getSeconds() },
    format,
    showSeconds,
  );
  return <TimeChip variant={variant} value={reading.value} unit={reading.unit} />;
});
