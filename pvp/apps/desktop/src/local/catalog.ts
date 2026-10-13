/**
 * The Store's catalog — what the gallery sells (Figma `73:211`, `75:236`).
 *
 * Rule 5: the Store is a gallery. Items are sold for real money through secure checkout,
 * cosmetic only, no gameplay advantage (Mojang's commercial guidelines). Rule 8: cosmetics
 * are visible to other VOID players only, and the Store says so.
 *
 * The eight items the frame draws are its own, verbatim — name, kind, price and art. The
 * frame's tab counts (Capes 6 · Trails 4 · Emotes 8 · Nameplates 5 = 23) promise fifteen
 * more, and those are written here in the same voice so every tab has something in it.
 *
 * TODO(integrate): the catalog is static content until a store backend serves it; prices
 * are display prices and nothing here can take a payment (see `Store.tsx`, Buy).
 */

export type CosmeticKind = 'cape' | 'trail' | 'emote' | 'nameplate';

/** How an item's art is drawn — see `CosmeticArt.tsx`. */
export type ArtStyle =
  | { kind: 'cape'; pattern: 'cell' | 'void' | 'ring' | 'origin' | 'dusk' | 'static' | 'split' | 'band' }
  | { kind: 'trail'; pattern: 'spark' | 'drift' | 'ring' | 'stair'; hue: string }
  | { kind: 'emote'; pattern: string[] }
  | { kind: 'nameplate'; pattern: 'cell' | 'ring' | 'bar' | 'dots' | 'plain'; hue: string };

export interface CosmeticItem {
  id: string;
  name: string;
  kind: CosmeticKind;
  /** Price in US cents. `0` is free — granted to every account. */
  price_cents: number;
  /** One line for the item sheet: `Cape · three soft bands, graded from dusk to dark.` */
  blurb: string;
  isNew?: boolean;
  art: ArtStyle;
}

export const KIND_LABEL: Record<CosmeticKind, string> = {
  cape: 'Cape',
  trail: 'Trail',
  emote: 'Emote',
  nameplate: 'Nameplate',
};

const HUD = '#9f8bff';
const PVP = '#ff9e7a';
const VISUAL = '#7adfff';
const UTILITY = '#7ae0b0';

export const CATALOG: readonly CosmeticItem[] = [
  // ---- the frame's eight, in its order -------------------------------------------
  { id: 'cell-cape', name: 'Cell cape', kind: 'cape', price_cents: 399, blurb: 'a grid of violet cells that dims toward the hem.', art: { kind: 'cape', pattern: 'cell' } },
  { id: 'void-cape', name: 'Void cape', kind: 'cape', price_cents: 499, blurb: 'the VOID ring, white on black.', art: { kind: 'cape', pattern: 'void' } },
  { id: 'ring-cape', name: 'Ring cape', kind: 'cape', price_cents: 299, blurb: 'the VOID ring on slate.', art: { kind: 'cape', pattern: 'ring' } },
  { id: 'origin-cape', name: 'Origin cape', kind: 'cape', price_cents: 0, blurb: 'two halves of grey. Every account has one.', art: { kind: 'cape', pattern: 'origin' } },
  { id: 'dusk-cape', name: 'Dusk cape', kind: 'cape', price_cents: 299, blurb: 'three soft bands, graded from dusk to dark.', art: { kind: 'cape', pattern: 'dusk' } },
  { id: 'static-cape', name: 'Static cape', kind: 'cape', price_cents: 399, blurb: 'a scatter of pale cells, like a dead channel.', art: { kind: 'cape', pattern: 'static' } },
  { id: 'spark-trail', name: 'Spark trail', kind: 'trail', price_cents: 399, blurb: 'cells that shrink and fade behind you.', isNew: true, art: { kind: 'trail', pattern: 'spark', hue: HUD } },
  { id: 'cell-nameplate', name: 'Cell nameplate', kind: 'nameplate', price_cents: 199, blurb: 'a three-by-three mark before your name.', art: { kind: 'nameplate', pattern: 'cell', hue: HUD } },

  // ---- the rest of the counts ---------------------------------------------------
  { id: 'ember-trail', name: 'Ember trail', kind: 'trail', price_cents: 399, blurb: 'warm cells drifting up and out.', art: { kind: 'trail', pattern: 'drift', hue: PVP } },
  { id: 'frost-trail', name: 'Frost trail', kind: 'trail', price_cents: 399, blurb: 'a cold ring of cells at every step.', art: { kind: 'trail', pattern: 'ring', hue: VISUAL } },
  { id: 'stair-trail', name: 'Stair trail', kind: 'trail', price_cents: 299, blurb: 'cells that climb in even steps.', art: { kind: 'trail', pattern: 'stair', hue: UTILITY } },

  { id: 'wave-emote', name: 'Wave', kind: 'emote', price_cents: 199, blurb: 'a hand, raised and shaken.', art: { kind: 'emote', pattern: ['#...#', '#.#.#', '#####', '.###.', '..#..'] } },
  { id: 'gg-emote', name: 'GG', kind: 'emote', price_cents: 199, blurb: 'good game, said with your whole body.', art: { kind: 'emote', pattern: ['####.####', '#....#...', '#.##.#.##', '#..#.#..#', '####.####'] } },
  { id: 'bow-emote', name: 'Bow', kind: 'emote', price_cents: 199, blurb: 'a slow, formal bow.', art: { kind: 'emote', pattern: ['..##.', '.##..', '####.', '.#.#.', '.#..#'] } },
  { id: 'clap-emote', name: 'Clap', kind: 'emote', price_cents: 199, blurb: 'three claps, unhurried.', art: { kind: 'emote', pattern: ['#...#', '.#.#.', '..#..', '.#.#.', '#...#'] } },
  { id: 'shrug-emote', name: 'Shrug', kind: 'emote', price_cents: 199, blurb: 'both shoulders, no comment.', art: { kind: 'emote', pattern: ['#...#', '.#.#.', '..#..', '..#..', '.#.#.'] } },
  { id: 'salute-emote', name: 'Salute', kind: 'emote', price_cents: 199, blurb: 'a crisp salute to the other side.', art: { kind: 'emote', pattern: ['###..', '#.#..', '####.', '..#..', '.#.#.'] } },
  { id: 'sit-emote', name: 'Sit', kind: 'emote', price_cents: 149, blurb: 'sit down wherever you stand.', art: { kind: 'emote', pattern: ['.#...', '.#...', '.###.', '...#.', '...##'] } },
  { id: 'point-emote', name: 'Point', kind: 'emote', price_cents: 149, blurb: 'that one. Over there.', art: { kind: 'emote', pattern: ['.....', '#####', '..#..', '.#...', '#....'] } },

  { id: 'ring-nameplate', name: 'Ring nameplate', kind: 'nameplate', price_cents: 199, blurb: 'the VOID ring before your name.', art: { kind: 'nameplate', pattern: 'ring', hue: '#edeeef' } },
  { id: 'ember-nameplate', name: 'Ember nameplate', kind: 'nameplate', price_cents: 199, blurb: 'a warm bar under your name.', art: { kind: 'nameplate', pattern: 'bar', hue: PVP } },
  { id: 'frost-nameplate', name: 'Frost nameplate', kind: 'nameplate', price_cents: 199, blurb: 'three cold cells beside your name.', art: { kind: 'nameplate', pattern: 'dots', hue: VISUAL } },
  { id: 'plain-nameplate', name: 'Plain nameplate', kind: 'nameplate', price_cents: 0, blurb: 'just your name. Every account has one.', art: { kind: 'nameplate', pattern: 'plain', hue: UTILITY } },
];

export const CATEGORIES: readonly { id: 'all' | CosmeticKind; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'cape', label: 'Capes' },
  { id: 'trail', label: 'Trails' },
  { id: 'emote', label: 'Emotes' },
  { id: 'nameplate', label: 'Nameplates' },
];

/** Items every account owns without buying them. */
export const FREE_ITEMS: readonly string[] = CATALOG.filter((i) => i.price_cents === 0).map((i) => i.id);

export const findItem = (id: string | null | undefined): CosmeticItem | undefined =>
  id ? CATALOG.find((i) => i.id === id) : undefined;

/** `$2.99` — USD, because the checkout the frame describes is priced in it. */
export const formatPrice = (cents: number): string =>
  cents === 0 ? 'Free' : `$${(cents / 100).toFixed(2)}`;
