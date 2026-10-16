/**
 * The Store's catalog — what the gallery sells (Figma `73:211`, `75:236`).
 *
 * Rule 5: the Store is a gallery. Items are sold for real money through secure checkout,
 * cosmetic only, no gameplay advantage (Mojang's commercial guidelines). Rule 8: cosmetics
 * are visible to other VOID players only, and the Store says so.
 *
 * The data is no longer written here. `pvp/schema/cosmetics.json` is the single source of the
 * catalog — the mod's Java catalog, `@void/protocol`'s `COSMETIC_CATALOG` and the backend's
 * Supabase seed are all generated from it (`node scripts/gen-cosmetics.mjs`). This module maps
 * that table onto the shape the Store has always used (`CosmeticItem`, `ArtStyle`, the tab
 * list), so `Store.tsx`, `Home.tsx` and `art.tsx` read the same exports they always did.
 *
 * Kinds are the contract's slots plus `emote`; the old `nameplate` kind is the `badge` slot.
 * Granted items (the founder badge) are in `findItem` but not in the gallery: they are not for
 * sale.
 */

import {
  COSMETIC_CATALOG,
  type CosmeticCatalogItem,
  type CosmeticKind as CatalogKind,
} from '@void/protocol';

export type CosmeticKind = CatalogKind;

/**
 * How an item's art is drawn — see `CosmeticArt` in `features/art.tsx`. Every worn item's
 * picture is rendered from the item itself (`scripts/gen-cosmetics.mjs` writes
 * `assets/cosmetics/<id>.png` from the bundled model and textures), so the Store shows the
 * texels the game draws. Emotes have no texture and keep their pose glyph.
 */
export type ArtStyle =
  /** A cape's face (seen from behind) and lining (from the front) at their native 10 x 16. */
  | { kind: 'cape'; face: string; lining: string }
  /** Back, head, face and halo items: a lit three-quarter portrait. */
  | { kind: 'model'; src: string }
  /** A trail's particles along an arc. */
  | { kind: 'trail'; src: string }
  /** A badge's 9 x 9 enamel pin. */
  | { kind: 'badge'; src: string }
  | { kind: 'emote'; pattern: string[] };

export interface CosmeticItem {
  id: string;
  name: string;
  kind: CosmeticKind;
  /** Price in US cents. `0` is free — granted to every account, or not for sale at all. */
  price_cents: number;
  /** One line for the item sheet: `Cape · three soft bands, graded from dusk to dark.` */
  blurb: string;
  isNew?: boolean;
  art: ArtStyle;
  /** `paid`, `free` (every account owns it) or `grant` (never sold). */
  availability: CosmeticCatalogItem['availability'];
  rarity: CosmeticCatalogItem['rarity'];
  tintable: boolean;
}

export const KIND_LABEL: Record<CosmeticKind, string> = {
  cape: 'Cape',
  back: 'Back',
  head: 'Hat',
  face: 'Mask',
  halo: 'Halo',
  trail: 'Trail',
  badge: 'Badge',
  emote: 'Emote',
};

/** The rendered pictures, by file name (`<id>.png`, `<id>-lining.png`). */
const PICTURES: Record<string, string> = Object.fromEntries(
  Object.entries(import.meta.glob('../assets/cosmetics/*.png', { eager: true, import: 'default' }) as Record<string, string>).map(
    ([path, url]) => [path.slice(path.lastIndexOf('/') + 1, -'.png'.length), url],
  ),
);

/** An item's picture, or an empty string for an id this build has none for. */
export const pictureOf = (name: string): string => PICTURES[name] ?? '';

function artFor(i: CosmeticCatalogItem): ArtStyle {
  switch (i.kind) {
    case 'cape':
      return { kind: 'cape', face: pictureOf(i.id), lining: pictureOf(`${i.id}-lining`) };
    case 'trail':
      return { kind: 'trail', src: pictureOf(i.id) };
    case 'emote':
      return { kind: 'emote', pattern: i.preview.rows ?? [] };
    case 'badge':
      return { kind: 'badge', src: pictureOf(i.id) };
    default:
      return { kind: 'model', src: pictureOf(i.id) };
  }
}

const ALL: readonly CosmeticItem[] = COSMETIC_CATALOG.map((i) => ({
  id: i.id,
  name: i.name,
  kind: i.kind,
  price_cents: i.price_cents,
  blurb: i.blurb,
  ...(i.is_new ? { isNew: true } : {}),
  art: artFor(i),
  availability: i.availability,
  rarity: i.rarity,
  tintable: i.tintable,
}));

/** What the gallery shows: everything that can be bought or is free. */
export const CATALOG: readonly CosmeticItem[] = ALL.filter((i) => i.availability !== 'grant');

const TAB_ORDER: readonly CosmeticKind[] = ['cape', 'back', 'head', 'face', 'halo', 'trail', 'emote', 'badge'];
const TAB_LABEL: Record<CosmeticKind, string> = {
  cape: 'Capes',
  back: 'Back',
  head: 'Hats',
  face: 'Masks',
  halo: 'Halos',
  trail: 'Trails',
  badge: 'Badges',
  emote: 'Emotes',
};

export const CATEGORIES: readonly { id: 'all' | CosmeticKind; label: string }[] = [
  { id: 'all', label: 'All' },
  ...TAB_ORDER.filter((k) => CATALOG.some((i) => i.kind === k)).map((k) => ({ id: k, label: TAB_LABEL[k] })),
];

/** Items every account owns without buying them. */
export const FREE_ITEMS: readonly string[] = ALL.filter((i) => i.availability === 'free').map((i) => i.id);

/** Any item by id, granted ones included (a founder still sees their badge on Home). */
export const findItem = (id: string | null | undefined): CosmeticItem | undefined =>
  id ? ALL.find((i) => i.id === id) : undefined;

/** `$2.99` — USD, because the checkout the frame describes is priced in it. */
export const formatPrice = (cents: number): string =>
  cents === 0 ? 'Free' : `$${(cents / 100).toFixed(2)}`;
