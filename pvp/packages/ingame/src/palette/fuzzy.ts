/**
 * Fuzzy ranking for the quick palette. Pure, deterministic, allocation-light —
 * it runs on every keystroke inside a game frame.
 *
 * The match is a subsequence match, scored by how *tight* and how *anchored* it
 * is: consecutive characters score, characters that begin a word score more, and
 * a match at the very start scores most. A gap costs a little. That ordering is
 * what puts `Toggle Fullbright` above `Turn on in Bedwars loadout` for `fullb`.
 */

const BONUS_CONSECUTIVE = 8;
const BONUS_WORD_START = 10;
const BONUS_FIRST_CHAR = 14;
const PENALTY_GAP = 1;
const PENALTY_LEADING = 0.5;

function isBoundary(text: string, index: number): boolean {
  if (index === 0) return true;
  const previous = text.charCodeAt(index - 1);
  // space, hyphen, underscore, slash, dot, middle dot
  return (
    previous === 32 || previous === 45 || previous === 95 || previous === 47 || previous === 46 || previous === 183
  );
}

/**
 * Score `query` against `text`. Higher is better; `null` means no match.
 * An empty query matches everything with a score of 0.
 */
export function fuzzyScore(query: string, text: string): number | null {
  const q = query.trim().toLowerCase();
  if (q === '') return 0;
  const t = text.toLowerCase();
  if (q.length > t.length) return null;

  let score = 0;
  let at = 0;
  let previousIndex = -1;

  for (let i = 0; i < q.length; i += 1) {
    const found = t.indexOf(q[i]!, at);
    if (found === -1) return null;

    if (found === 0) score += BONUS_FIRST_CHAR;
    else if (isBoundary(t, found)) score += BONUS_WORD_START;

    if (previousIndex >= 0) {
      const gap = found - previousIndex - 1;
      if (gap === 0) score += BONUS_CONSECUTIVE;
      else score -= gap * PENALTY_GAP;
    } else {
      score -= found * PENALTY_LEADING;
    }

    previousIndex = found;
    at = found + 1;
  }

  // Shorter haystacks win ties: "CPS" beats "CPS counter settings" for "cps".
  return score - t.length * 0.05;
}

export interface Rankable {
  /** Primary text the query is matched against. */
  title: string;
  /** Secondary text, matched at a discount. */
  sub?: string;
  /** Static nudge, so a toggle outranks the settings entry of the same mod. */
  weight?: number;
}

/** Best score across a rankable's title and subtitle. */
export function scoreRankable(query: string, item: Rankable): number | null {
  const title = fuzzyScore(query, item.title);
  const sub = item.sub === undefined ? null : fuzzyScore(query, item.sub);
  const best =
    title === null ? (sub === null ? null : sub * 0.4) : sub === null ? title : Math.max(title, sub * 0.4);
  return best === null ? null : best + (item.weight ?? 0);
}

/**
 * Rank a list, dropping non-matches. Stable: items that tie keep the order they
 * were declared in, which is how the palette keeps a sensible empty-query list.
 */
export function rank<T extends Rankable>(items: readonly T[], query: string): T[] {
  const scored: Array<{ item: T; score: number; index: number }> = [];
  for (let index = 0; index < items.length; index += 1) {
    const score = scoreRankable(query, items[index]!);
    if (score === null) continue;
    scored.push({ item: items[index]!, score, index });
  }
  scored.sort((a, b) => (b.score === a.score ? a.index - b.index : b.score - a.score));
  return scored.map((entry) => entry.item);
}
