/**
 * `hud/format.ts` — the readout formatters, and the one that was wrong in game.
 *
 * Its own file because these are pure functions over the sensor's own values, and the widgets
 * that use them are covered by the screen and preview suites rather than by unit tests. What is
 * worth pinning here is the shape of the *rule*, not one example of it.
 */

import { describe, expect, it } from 'vitest';

import { playedTime, shortHost } from '@/hud/format';
import { formatClock } from '@/hud/clock';

/**
 * The twelve-hour form, which is the only part of a clock that has rules rather than padding.
 *
 * Midnight and noon are the two hours where the obvious arithmetic is wrong: `0 % 12` and
 * `12 % 12` are both 0, and a clock reading `0:41 AM` is not a clock anybody has seen. They are
 * the two values worth a test, and the two a hand-written `hours - 12` gets wrong.
 */
describe('formatClock', () => {
  const at = (hours: number, minutes = 41, seconds = 7) => ({ hours, minutes, seconds });

  it('pads the 24-hour form to one width at every hour', () => {
    expect(formatClock(at(9), 'h24', false).value).toBe('09:41');
    expect(formatClock(at(21), 'h24', false).value).toBe('21:41');
  });

  it('does not pad the 12-hour form, which is written 9:41 and never 09:41', () => {
    expect(formatClock(at(9), 'h12', false)).toEqual({ value: '9:41', unit: 'AM' });
    expect(formatClock(at(21), 'h12', false)).toEqual({ value: '9:41', unit: 'PM' });
  });

  it('reads midnight and noon as 12, not as 0', () => {
    expect(formatClock(at(0), 'h12', false)).toEqual({ value: '12:41', unit: 'AM' });
    expect(formatClock(at(12), 'h12', false)).toEqual({ value: '12:41', unit: 'PM' });
  });

  it('adds seconds to both forms, padded', () => {
    expect(formatClock(at(21, 41, 7), 'h24', true).value).toBe('21:41:07');
    expect(formatClock(at(21, 41, 7), 'h12', true).value).toBe('9:41:07');
  });

  /** There is no meridiem on the 24-hour form — the suffix is part of the other format. */
  it('gives the 24-hour form no unit', () => {
    expect(formatClock(at(21), 'h24', false).unit).toBeUndefined();
  });
});

describe('shortHost', () => {
  it('takes the label before the public suffix, which is what a player calls the server', () => {
    expect(shortHost('mc.hypixel.net')).toBe('Hypixel');
    expect(shortHost('hypixel.net')).toBe('Hypixel');
    expect(shortHost('play.cubecraft.net')).toBe('Cubecraft');
  });

  /**
   * The regression. A direct-IP server drew `1` on the HUD — the domain rule picked a digit
   * group out of the middle of the address, because every label of an IPv4 literal is also a
   * legal domain label.
   *
   * It had been live in `ping.show_host` without anyone noticing; `server_address` put it on a
   * chip of its own, with `style` defaulting to `short`, which is where a one-character server
   * name stops being odd and starts being obviously broken.
   */
  it('returns an address that is not a name whole, rather than shortening a digit group', () => {
    expect(shortHost('192.168.1.20')).toBe('192.168.1.20');
    expect(shortHost('127.0.0.1')).toBe('127.0.0.1');
    expect(shortHost('10.0.0.7')).toBe('10.0.0.7');
  });

  it('strips a port from either shape — it belongs to the connection, not the name', () => {
    expect(shortHost('mc.hypixel.net:25565')).toBe('Hypixel');
    expect(shortHost('1.2.3.4:25565')).toBe('1.2.3.4');
  });

  it('leaves a single-label host alone but for its capital', () => {
    expect(shortHost('localhost')).toBe('Localhost');
  });

  it('does not mistake a four-label domain for an address', () => {
    // Four labels, but not four numeric ones — the IPv4 test is on the digits, not the count.
    expect(shortHost('eu.play.example.net')).toBe('Example');
  });

  it('survives the empty host the sensor sends on disconnect', () => {
    expect(shortHost('')).toBe('');
  });
});

describe('playedTime', () => {
  it('drops the hour until there is one, and pads the minutes once there is', () => {
    expect(playedTime(48 * 60_000)).toBe('48m');
    expect(playedTime((4 * 60 + 20) * 60_000)).toBe('4h 20m');
    expect(playedTime((4 * 60 + 5) * 60_000)).toBe('4h 05m');
  });
});
