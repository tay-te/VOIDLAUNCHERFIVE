/**
 * The wardrobe's state: what the local player wears, what the account owns, and the emote
 * playing — fed by the `cosmetics` and `emote` channels (bridge.json), written through
 * `equipCosmetic`, `setCosmeticTint`, `setEmoteWheel` and `playEmote`.
 *
 * Its own small store rather than more fields on the main one: nothing here is read by the HUD,
 * and the HUD's narrow subscriptions stay exactly as they were.
 *
 * Every call is synchronous and authoritative, like `setModSetting`: the page binds to what Java
 * returned and patches its copy from it. Java never echoes the page's own change back (§6.5).
 *
 * No purchases happen here. A locked item routes to "Get in launcher Store"; the Store is the
 * launcher's, and the page has no network.
 */

import { create } from 'zustand';

import {
  COSMETIC_CATALOG,
  COSMETIC_SLOTS,
  EMOTE_WHEEL_SIZE,
  findCosmetic,
  type CosmeticCatalogItem,
  type CosmeticId,
  type CosmeticSlot,
  type CosmeticsPayload,
  type EmotePayload,
  type WheelPayload,
} from '@/bridge/protocol';
import { getVoid } from '@/bridge/connect';

export type EquippedSet = CosmeticsPayload['self'];

export interface CosmeticsState {
  /** Null until the first `cosmetics` push — the wardrobe shows its loading state. */
  self: EquippedSet | null;
  owned: readonly CosmeticId[];
  catalogVersion: number | null;
  /** The emote playing now. */
  playing: CosmeticId | null;
  /** The crescent emote wheel is up. */
  wheelOpen: boolean;
  /** Who opened it: the menu (B, Try it) or the emote key held in the world (`wheel` channel). */
  wheelSource: 'menu' | 'world';
  /** World wheel: the mouse travel since it opened, screen px (x right, y down). */
  wheelAim: { x: number; y: number } | null;
  /** World wheel: the last 1-8 pressed while held, with a sequence so a repeat still fires. */
  wheelKey: { slot: number; seq: number } | null;
  /** World wheel: bumps when the key comes up — play what it points at. */
  wheelRelease: number;

  apply(payload: CosmeticsPayload): void;
  applyEmote(payload: EmotePayload): void;
  equip(slot: CosmeticSlot, id: CosmeticId | null): void;
  tint(slot: CosmeticSlot, hex: string | null): void;
  setWheel(ids: readonly CosmeticId[]): void;
  play(id: CosmeticId): boolean;
  setWheelOpen(open: boolean): void;
  /** The `wheel` channel: the emote key held in the world. */
  applyWheel(payload: WheelPayload): void;
}

function bridge() {
  try {
    return getVoid();
  } catch {
    return null;
  }
}

export const useCosmetics = create<CosmeticsState>((set, get) => ({
  self: null,
  owned: [],
  catalogVersion: null,
  playing: null,
  wheelOpen: false,
  wheelSource: 'menu',
  wheelAim: null,
  wheelKey: null,
  wheelRelease: 0,

  apply(payload) {
    set({ self: payload.self, owned: payload.owned as CosmeticId[], catalogVersion: payload.catalog_version });
  },
  applyEmote(payload) {
    set({ playing: (payload.id as CosmeticId | null) ?? null });
  },
  equip(slot, id) {
    const b = bridge();
    const self = get().self;
    if (!b || !self) return;
    const worn = b.equipCosmetic(slot, id);
    set({ self: { ...self, [slot]: worn } });
  },
  tint(slot, hex) {
    const b = bridge();
    const self = get().self;
    if (!b || !self) return;
    const applied = b.setCosmeticTint(slot, hex);
    const tints = { ...(self.tints ?? {}) } as Record<string, string>;
    if (applied) tints[slot] = applied;
    else delete tints[slot];
    set({ self: { ...self, tints } });
  },
  setWheel(ids) {
    const b = bridge();
    const self = get().self;
    if (!b || !self) return;
    const stored = b.setEmoteWheel([...ids]);
    set({ self: { ...self, emotes: stored } });
  },
  play(id) {
    const b = bridge();
    if (!b) return false;
    const started = b.playEmote(id);
    if (started) set({ playing: id });
    return started;
  },
  setWheelOpen(wheelOpen) {
    set(wheelOpen ? { wheelOpen } : { wheelOpen, wheelSource: 'menu', wheelAim: null, wheelKey: null });
  },
  applyWheel(payload) {
    switch (payload.phase) {
      case 'open':
        set({ wheelOpen: true, wheelSource: 'world', wheelAim: null, wheelKey: null });
        return;
      case 'aim':
        set({ wheelAim: { x: payload.x ?? 0, y: payload.y ?? 0 } });
        return;
      case 'key':
        set({ wheelKey: { slot: payload.slot ?? 0, seq: (get().wheelKey?.seq ?? 0) + 1 } });
        return;
      case 'release':
        set({ wheelRelease: get().wheelRelease + 1 });
        return;
      case 'cancel':
        get().setWheelOpen(false);
        return;
    }
  },
}));

/* -------------------------------------------------------------------------- */
/* Catalog views                                                              */
/* -------------------------------------------------------------------------- */

export const SLOT_LABEL: Record<CosmeticSlot, string> = {
  cape: 'Cape',
  back: 'Back',
  head: 'Head',
  face: 'Face',
  halo: 'Halo',
  trail: 'Trail',
  badge: 'Badge',
};

/** What a slot holds, said the way a player would. */
export const SLOT_NOUN: Record<CosmeticSlot, string> = {
  cape: 'capes',
  back: 'wings and back items',
  head: 'hats',
  face: 'masks',
  halo: 'halos',
  trail: 'trails',
  badge: 'badges',
};

export const RARITY_HUE: Record<CosmeticCatalogItem['rarity'], string> = {
  common: '#9ba0a8',
  rare: '#7adfff',
  epic: '#9f8bff',
  legendary: '#ffcf6b',
  exclusive: '#ff9e7a',
};

export { COSMETIC_SLOTS, EMOTE_WHEEL_SIZE, findCosmetic };

export function itemsFor(kind: CosmeticSlot | 'emote'): readonly CosmeticCatalogItem[] {
  return COSMETIC_CATALOG.filter((i) => i.kind === kind);
}

/** `$3.99` — prices are USD cents in the catalog; the page never shows a coin. */
export function priceLabel(item: CosmeticCatalogItem): string {
  if (item.availability === 'free') return 'Free';
  if (item.availability === 'grant') return 'Granted';
  return `$${(item.price_cents / 100).toFixed(2)}`;
}
