/**
 * `hud/format.ts` — the readout formatters, and the one that was wrong in game.
 *
 * Its own file because these are pure functions over the sensor's own values, and the widgets
 * that use them are covered by the screen and preview suites rather than by unit tests. What is
 * worth pinning here is the shape of the *rule*, not one example of it.
 */

import { describe, expect, it } from 'vitest';

import { playedTime, shortHost } from '@/hud/format';

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
