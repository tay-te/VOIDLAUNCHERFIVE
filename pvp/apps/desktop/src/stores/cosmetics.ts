/**
 * What the player owns and wears — the Store's "Equipped" / "Owned" and Home's
 * "WEARING CELL CAPE" (Figma v1 Ship, rules 4 and 8).
 *
 * TODO(integrate): ownership belongs to the store backend, which does not exist yet. Until
 * it does this lives in `localStorage`: a real install owns the free items and nothing
 * else, and buying cannot complete (see `Store.tsx`). The browser preview's mock seeds the
 * frame's state — Cell cape equipped, Origin cape owned — so the gallery can be reviewed.
 *
 * Rendering a cape *in game*, for other VOID players to see, is §16.1's render Mixin and
 * asset pipeline; nothing in the mod reads this yet.
 */

import { create } from 'zustand';

import { FREE_ITEMS, findItem, type CosmeticKind } from '../local/catalog';

export const COSMETICS_STORAGE_KEY = 'void.cosmetics.v1';

interface CosmeticsData {
  owned: string[];
  /** One equipped item per kind, by id. */
  equipped: Partial<Record<CosmeticKind, string>>;
}

const DEFAULTS: CosmeticsData = { owned: [...FREE_ITEMS], equipped: { cape: 'origin-cape' } };

function load(): CosmeticsData {
  if (typeof localStorage === 'undefined') return DEFAULTS;
  try {
    const raw = localStorage.getItem(COSMETICS_STORAGE_KEY);
    if (!raw) return DEFAULTS;
    const parsed = JSON.parse(raw) as Partial<CosmeticsData>;
    const owned = [...new Set([...FREE_ITEMS, ...(Array.isArray(parsed.owned) ? parsed.owned : [])])];
    const equipped: CosmeticsData['equipped'] = {};
    for (const [kind, id] of Object.entries(parsed.equipped ?? {})) {
      if (typeof id === 'string' && owned.includes(id)) equipped[kind as CosmeticKind] = id;
    }
    return { owned, equipped };
  } catch {
    return DEFAULTS;
  }
}

function save(data: CosmeticsData): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(COSMETICS_STORAGE_KEY, JSON.stringify(data));
  } catch {
    /* in memory is enough for this session */
  }
}

interface CosmeticsState extends CosmeticsData {
  hydrate: () => void;
  /** Wear an owned item; wearing the equipped one again takes it off. */
  equip: (id: string) => void;
}

export const useCosmetics = create<CosmeticsState>((set, get) => ({
  ...DEFAULTS,

  hydrate: () => set(load()),

  equip: (id) => {
    const item = findItem(id);
    if (!item || !get().owned.includes(id)) return;
    const equipped = { ...get().equipped };
    if (equipped[item.kind] === id) delete equipped[item.kind];
    else equipped[item.kind] = id;
    save({ owned: get().owned, equipped });
    set({ equipped });
  },
}));
