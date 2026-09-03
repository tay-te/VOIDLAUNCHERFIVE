/**
 * Quick-palette ranking. The frame types `fullb` and expects
 * `Toggle Fullbright` first, `Fullbright settings` second — this is the
 * behaviour that produces that order.
 */

import { describe, expect, it } from 'vitest';
import { fuzzyScore, rank, scoreRankable } from '@/palette/fuzzy';

describe('fuzzyScore', () => {
  it('matches an empty query against everything, neutrally', () => {
    expect(fuzzyScore('', 'anything')).toBe(0);
  });

  it('returns null when the query is not a subsequence', () => {
    expect(fuzzyScore('zzz', 'Toggle Fullbright')).toBeNull();
    expect(fuzzyScore('thgirb', 'Fullbright')).toBeNull();
  });

  it('is case-insensitive', () => {
    expect(fuzzyScore('FULL', 'Fullbright')).toBe(fuzzyScore('full', 'fullbright'));
  });

  it('scores a prefix above a mid-word match', () => {
    const prefix = fuzzyScore('full', 'Fullbright')!;
    const middle = fuzzyScore('full', 'Toggle Fullbright')!;
    expect(prefix).toBeGreaterThan(middle);
  });

  it('scores consecutive characters above a scattered subsequence', () => {
    const tight = fuzzyScore('keys', 'Keystrokes')!;
    const loose = fuzzyScore('keys', 'Kill effect yields spread')!;
    expect(tight).toBeGreaterThan(loose);
  });

  it('rewards a match at a word boundary', () => {
    const boundary = fuzzyScore('s', 'Armor status')!;
    const inside = fuzzyScore('r', 'Armor status')!;
    expect(boundary).toBeGreaterThan(inside);
  });

  it('breaks ties towards the shorter haystack', () => {
    expect(fuzzyScore('cps', 'CPS')!).toBeGreaterThan(fuzzyScore('cps', 'CPS counter settings')!);
  });
});

describe('scoreRankable', () => {
  it('matches the subtitle at a discount when the title does not match', () => {
    const score = scoreRankable('visual', { title: 'Toggle Fullbright', sub: 'Visual · off' });
    expect(score).not.toBeNull();
    expect(score!).toBeLessThan(fuzzyScore('visual', 'Visual · off')!);
  });

  it('is null when neither field matches', () => {
    expect(scoreRankable('zzz', { title: 'Toggle Fullbright', sub: 'Visual' })).toBeNull();
  });

  it('applies the static weight', () => {
    const plain = scoreRankable('full', { title: 'Fullbright' })!;
    const heavy = scoreRankable('full', { title: 'Fullbright', weight: 100 })!;
    expect(heavy - plain).toBe(100);
  });
});

describe('rank', () => {
  const commands = [
    { id: 'settings', title: 'Fullbright settings', sub: 'Open in the mod menu', weight: 2 },
    { id: 'toggle', title: 'Toggle Fullbright', sub: 'Visual  ·  currently off', weight: 6 },
    { id: 'other', title: 'Turn on in Bedwars loadout', sub: 'Fullbright is off there', weight: 1 },
    { id: 'zoom', title: 'Toggle Zoom', sub: 'Utility', weight: 6 },
  ];

  it('reproduces the frame: `fullb` puts the toggle first and the settings second', () => {
    const ids = rank(commands, 'fullb').map((c) => c.id);
    expect(ids[0]).toBe('toggle');
    expect(ids[1]).toBe('settings');
  });

  it('drops non-matches entirely', () => {
    expect(rank(commands, 'fullb').map((c) => c.id)).not.toContain('zoom');
  });

  it('keeps declaration order for an empty query', () => {
    expect(rank(commands, '').map((c) => c.id)).toEqual([
      'toggle',
      'zoom',
      'settings',
      'other',
    ]);
  });

  it('is stable for equal scores', () => {
    const tied = [
      { id: 'a', title: 'Same', weight: 1 },
      { id: 'b', title: 'Same', weight: 1 },
    ];
    expect(rank(tied, 'same').map((c) => c.id)).toEqual(['a', 'b']);
  });
});
