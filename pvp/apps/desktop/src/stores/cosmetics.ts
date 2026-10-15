/**
 * What the player owns and wears — the Store's "Equipped" / "Owned" and Home's
 * "WEARING CELL CAPE" (Figma v1 Ship, rules 4 and 8).
 *
 * Online (a Microsoft account, in a build with a backend): the server is the source of truth.
 * `void-online` holds the player's entitlements and equipped set in Supabase; `cosmetics_state`
 * reads them, `cosmetics_equip` / `cosmetics_set_emotes` change them (ownership is checked by
 * the database), and `online:cosmetics` pushes every change — an equip from the in-game
 * wardrobe, a purchase landing — so this store never polls. The same set goes to the mod over
 * the bridge, which is what other VOID players see (docs/online-contract.md).
 *
 * Offline (offline account, no backend in this build, or not signed in): the last state the
 * server sent is shown from `localStorage`, and a fresh install owns the free items. Equipping
 * still works locally so the gallery stays usable, and is replaced by the server's state the
 * next time it answers. Buying needs to be online.
 *
 * Items are matched to the contract's slots by kind (`slotFor`): a kind that is a slot name
 * is that slot, `emote` goes on the wheel (up to 8), and the launcher catalog's `nameplate`
 * is the nametag `badge`.
 */

import { create } from 'zustand';

import { FREE_ITEMS, findItem, type CosmeticItem, type CosmeticKind } from '../local/catalog';
import type { CosmeticsState, EquippedSet } from '../local/protocol';
import { errorText, invoke } from '../local/tauri';

export const COSMETICS_STORAGE_KEY = 'void.cosmetics.v1';
export const MAX_EMOTES = 8;

const SLOTS = ['cape', 'back', 'head', 'face', 'halo', 'trail', 'badge'] as const;
export type Slot = (typeof SLOTS)[number];

/** Where an item of `kind` is worn: a slot, the emote wheel, or nowhere this build knows. */
export function slotFor(kind: string): Slot | 'emotes' | null {
  if ((SLOTS as readonly string[]).includes(kind)) return kind as Slot;
  if (kind === 'emote') return 'emotes';
  if (kind === 'nameplate') return 'badge';
  return null;
}

interface CosmeticsData {
  owned: string[];
  /** One equipped item per kind, by id. */
  equipped: Partial<Record<CosmeticKind, string>>;
  /** The emote wheel, in order. */
  emotes: string[];
}

/** A purchase in flight: the browser is open on Stripe Checkout. */
export interface Checkout {
  item: string;
  phase: 'opening' | 'waiting' | 'done' | 'failed';
  message?: string;
}

const DEFAULTS: CosmeticsData = { owned: [...FREE_ITEMS], equipped: { cape: 'origin-cape' }, emotes: [] };

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
    const emotes = Array.isArray(parsed.emotes) ? parsed.emotes.filter((id) => typeof id === 'string' && owned.includes(id)) : [];
    return { owned, equipped, emotes };
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

/** The server's EquippedSet, keyed by the launcher catalog's kinds. */
export function fromServer(set: EquippedSet | null, owned: string[]): CosmeticsData {
  const equipped: CosmeticsData['equipped'] = {};
  if (set) {
    for (const slot of SLOTS) {
      const id = set[slot];
      if (typeof id !== 'string') continue;
      const kind = (findItem(id)?.kind ?? slot) as CosmeticKind;
      equipped[kind] = id;
    }
  }
  return { owned: [...owned], equipped, emotes: set ? [...set.emotes] : [] };
}

/** Is `item` worn right now — in its slot, or on the wheel? */
export function isWorn(s: Pick<CosmeticsData, 'equipped' | 'emotes'>, item: CosmeticItem): boolean {
  return slotFor(item.kind) === 'emotes' ? s.emotes.includes(item.id) : s.equipped[item.kind] === item.id;
}

interface CosmeticsStore extends CosmeticsData {
  /** True while the server is the source of truth. */
  online: boolean;
  error: string | null;
  checkout: Checkout | null;

  /** Show the offline copy at once, then ask the server. */
  hydrate: () => void;
  refresh: () => Promise<void>;
  /** Take a `cosmetics_state` / `online:cosmetics` payload. */
  apply: (state: CosmeticsState) => void;
  /** Wear an owned item; wearing the equipped one again takes it off. */
  equip: (id: string) => Promise<void>;
  /** Open Stripe Checkout for `id` in the browser; ownership arrives by `online:cosmetics`. */
  buy: (id: string) => Promise<void>;
  clearCheckout: () => void;
  dismissError: () => void;
}

export const useCosmetics = create<CosmeticsStore>((set, get) => ({
  ...DEFAULTS,
  online: false,
  error: null,
  checkout: null,

  hydrate: () => {
    set(load());
    void get().refresh();
  },

  refresh: async () => {
    try {
      get().apply(await invoke('cosmetics_state'));
    } catch {
      // Not answering is the same as offline: keep what is shown.
      set({ online: false });
    }
  },

  apply: (state) => {
    if (!state.online) {
      set({ online: false });
      return;
    }
    const data = fromServer(state.equipped, state.owned);
    save(data);
    const checkout = get().checkout;
    set({
      ...data,
      online: true,
      checkout:
        checkout && checkout.phase !== 'failed' && data.owned.includes(checkout.item)
          ? { ...checkout, phase: 'done' }
          : checkout,
    });
  },

  equip: async (id) => {
    const item = findItem(id);
    const where = item ? slotFor(item.kind) : null;
    if (!item || !where || !get().owned.includes(id)) return;
    const before: CosmeticsData = { owned: get().owned, equipped: get().equipped, emotes: get().emotes };

    // Optimistic: the Store answers the click at once; the server's answer replaces it.
    let next: CosmeticsData;
    if (where === 'emotes') {
      const on = before.emotes.includes(id);
      if (!on && before.emotes.length >= MAX_EMOTES) {
        set({ error: `The wheel holds ${MAX_EMOTES} emotes. Take one off first.` });
        return;
      }
      next = { ...before, emotes: on ? before.emotes.filter((e) => e !== id) : [...before.emotes, id] };
    } else {
      const equipped = { ...before.equipped };
      if (equipped[item.kind] === id) delete equipped[item.kind];
      else equipped[item.kind] = id;
      next = { ...before, equipped };
    }
    set({ ...next, error: null });

    if (!get().online) {
      save(next);
      return;
    }
    try {
      const result =
        where === 'emotes'
          ? await invoke('cosmetics_set_emotes', { ids: next.emotes })
          : await invoke('cosmetics_equip', { slot: where, id: next.equipped[item.kind] === id ? id : null });
      const data = fromServer(result, get().owned);
      save(data);
      set(data);
    } catch (e) {
      set({ ...before, error: errorText(e) });
    }
  },

  buy: async (id) => {
    if (!get().online) {
      set({ checkout: { item: id, phase: 'failed', message: 'Sign in with a Microsoft account to buy cosmetics.' } });
      return;
    }
    set({ checkout: { item: id, phase: 'opening' } });
    try {
      await invoke('store_checkout', { item: id });
      if (get().checkout?.item === id && get().checkout?.phase === 'opening') {
        set({ checkout: { item: id, phase: get().owned.includes(id) ? 'done' : 'waiting' } });
      }
    } catch (e) {
      set({ checkout: { item: id, phase: 'failed', message: errorText(e) } });
    }
  },

  clearCheckout: () => set({ checkout: null }),
  dismissError: () => set({ error: null }),
}));
