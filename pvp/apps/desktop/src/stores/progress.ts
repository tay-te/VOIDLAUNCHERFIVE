/**
 * Level, points and quests — what the profile chip, PvP's quest strip and the Quests
 * screen all read.
 *
 * Two currencies, kept apart on purpose:
 *
 *   XP     — earned, never spent. It is what `level` is made of.
 *   Points — earned and spent in the Store. Spending never lowers your level.
 *
 * TODO(integrate): there is no progression backend yet. Quest progress must come from
 * server-verified match results (never from the client's own counters, which are
 * trivially spoofed), and the balance must be a server-side ledger so a claim or a
 * purchase can only land once. Until then this is fixture data; the store's shape is
 * the contract and does not change when the backend lands.
 */

import { create } from 'zustand';

export interface Quest {
  id: string;
  title: string;
  /** Daily quests reset at 00:00 UTC; weekly ones on Monday 00:00 UTC. */
  cadence: 'daily' | 'weekly';
  progress: number;
  goal: number;
  /** Points paid out on claim. */
  reward: number;
  claimed: boolean;
}

interface ProgressState {
  level: number;
  /** XP into the current level, and the XP the current level needs. */
  xp: number;
  xpForLevel: number;
  points: number;
  quests: Quest[];
}

export const useProgress = create<ProgressState>(() => ({
  level: 7,
  xp: 820,
  xpForLevel: 1000,
  points: 1240,
  quests: [
    { id: 'q-duels', title: 'Win 3 Duels', cadence: 'daily', progress: 2, goal: 3, reward: 50, claimed: false },
    { id: 'q-bedwars', title: 'Play 5 Bedwars games', cadence: 'daily', progress: 1, goal: 5, reward: 80, claimed: false },
    { id: 'q-combo', title: 'Land a 10-hit combo', cadence: 'weekly', progress: 0, goal: 1, reward: 150, claimed: false },
  ],
}));

/** When a quest of this cadence next resets, measured from `now`. */
export function nextReset(cadence: Quest['cadence'], now: Date = new Date()): Date {
  const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1));
  if (cadence === 'weekly') {
    // getUTCDay: 0 is Sunday. Advance to the next Monday 00:00 UTC.
    const daysToMonday = (8 - next.getUTCDay()) % 7;
    next.setUTCDate(next.getUTCDate() + daysToMonday);
  }
  return next;
}

/** "6 h", "45 min", "3 d" — the reset countdown as the strip prints it. */
export function formatUntil(target: Date, now: Date = new Date()): string {
  const minutes = Math.max(0, Math.round((target.getTime() - now.getTime()) / 60_000));
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours} h`;
  return `${Math.round(hours / 24)} d`;
}

/** 1240 → "1,240"; 48920 → "48.9k". Abbreviates from 10,000, one decimal, rounded. */
export function formatPoints(value: number): string {
  if (value < 10_000) return value.toLocaleString('en-US');
  const k = Math.round(value / 100) / 10;
  return `${k.toLocaleString('en-US')}k`;
}
