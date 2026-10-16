/* eslint-disable */
/**
 * GENERATED FILE — do not edit by hand.
 *
 * Source: pvp/schema/cosmetics.json (shape: cosmetics.schema.json)
 * Generator: `node scripts/gen-cosmetics.mjs` (from `pvp/`).
 *
 * The cosmetics catalog for the launcher Store and the in-game wardrobe. Ownership and what a
 * player wears come from the backend; how an item looks in game is its bundled asset in the
 * mod jar. This is only the metadata, and it is the same table the mod and the backend seed
 * are generated from.
 */

/** Where a worn cosmetic goes; one item per slot. */
export type CosmeticSlot = "cape" | "back" | "head" | "face" | "halo" | "trail" | "badge";

/** A slot, or `emote` (emotes live on the wheel, not in a slot). */
export type CosmeticKind = CosmeticSlot | 'emote';

export type CosmeticRarity = 'common' | 'rare' | 'epic' | 'legendary' | 'exclusive';

/** `paid`: bought. `free`: every account owns it. `grant`: granted, never sold. */
export type CosmeticAvailability = 'paid' | 'free' | 'grant';

export interface CosmeticPreview {
  style: CosmeticKind;
  pattern?: string;
  hue?: string;
  rows?: string[];
}

export interface CosmeticCatalogItem {
  id: CosmeticId;
  kind: CosmeticKind;
  name: string;
  blurb: string;
  rarity: CosmeticRarity;
  availability: CosmeticAvailability;
  /** US cents; 0 for free and granted items. */
  price_cents: number;
  tintable: boolean;
  default_tint?: string;
  is_new?: true;
  /** Emotes: holds until cancelled. */
  loop?: true;
  preview: CosmeticPreview;
}

export type CosmeticId =
  | "origin-cape"
  | "bespoke-cape"
  | "meridian-cape"
  | "atelier-cape"
  | "nocturne-cape"
  | "regent-cape"
  | "blade-wings"
  | "halcyon-ring"
  | "vesper-sheath"
  | "atelier-fedora"
  | "sovereign-crown"
  | "cloister-hood"
  | "aureole-halo"
  | "lumen-halo"
  | "porcelain-mask"
  | "aegis-visor"
  | "emote-wave"
  | "emote-clap"
  | "emote-bow"
  | "emote-salute"
  | "emote-shrug"
  | "emote-sit"
  | "gilt-trail"
  | "amethyst-trail"
  | "topaz-trail"
  | "jade-trail"
  | "void-badge-founder"
  | "void-badge-player"
  | "atelier-badge"
  | "laurel-badge";

export const COSMETIC_CATALOG_VERSION = 4;

export const COSMETIC_SLOTS: readonly CosmeticSlot[] = ["cape", "back", "head", "face", "halo", "trail", "badge"];

/** Max emotes on a player's wheel. */
export const EMOTE_WHEEL_SIZE = 8;

export const COSMETIC_CATALOG: readonly CosmeticCatalogItem[] = [
  {"id":"origin-cape","kind":"cape","name":"Origin cape","blurb":"taupe wool, a weighted hem, a glint running down one silver thread. Every account has one.","rarity":"common","availability":"free","price_cents":0,"tintable":false,"preview":{"style":"cape","pattern":"origin","hue":"#e9e5f0"}},
  {"id":"bespoke-cape","kind":"cape","name":"Bespoke cape","blurb":"wool in the colour you choose, an ivory yoke piped in silver, a pearl at the clasp.","rarity":"rare","availability":"paid","price_cents":299,"tintable":true,"default_tint":"#827a77","preview":{"style":"cape","pattern":"bespoke","hue":"#e9e5f0"}},
  {"id":"meridian-cape","kind":"cape","name":"Meridian cape","blurb":"ivory and taupe meeting at a brass seam, a jade at its head, cut to a swallowtail.","rarity":"rare","availability":"paid","price_cents":349,"tintable":false,"preview":{"style":"cape","pattern":"meridian","hue":"#7ae0b0"}},
  {"id":"atelier-cape","kind":"cape","name":"Atelier cape","blurb":"an ivory V-yoke piped in silver, an amethyst where it closes, a bias-cut hem.","rarity":"epic","availability":"paid","price_cents":449,"tintable":false,"preview":{"style":"cape","pattern":"atelier","hue":"#9f8bff"}},
  {"id":"nocturne-cape","kind":"cape","name":"Nocturne cape","blurb":"a constellation stitched in silver; light runs along the thread to one ice-topaz star.","rarity":"epic","availability":"paid","price_cents":449,"tintable":false,"preview":{"style":"cape","pattern":"nocturne","hue":"#7adfff"}},
  {"id":"regent-cape","kind":"cape","name":"Regent cape","blurb":"charcoal wool framed in gold, a gold crest set with a garnet, cut to a point.","rarity":"legendary","availability":"paid","price_cents":599,"tintable":false,"preview":{"style":"cape","pattern":"regent","hue":"#ff9e7a"}},
  {"id":"blade-wings","kind":"back","name":"Blade wings","blurb":"ivory lacquer blades on a polished silver spar, an amethyst at each wrist.","rarity":"legendary","availability":"paid","price_cents":899,"tintable":false,"preview":{"style":"back","pattern":"blades","hue":"#9f8bff"}},
  {"id":"halcyon-ring","kind":"back","name":"Halcyon ring","blurb":"a silver ring that floats behind you, an ice-topaz held at its heart.","rarity":"epic","availability":"paid","price_cents":699,"tintable":false,"preview":{"style":"back","pattern":"ring","hue":"#7adfff"}},
  {"id":"vesper-sheath","kind":"back","name":"Vesper sheath","blurb":"a sword in a lacquered scabbard across the back, gold fittings, a garnet pommel.","rarity":"epic","availability":"paid","price_cents":549,"tintable":false,"preview":{"style":"back","pattern":"sheath","hue":"#ff9e7a"}},
  {"id":"atelier-fedora","kind":"head","name":"Atelier fedora","blurb":"bone felt, a pinched crown, the brim snapped down; an amethyst on the band.","rarity":"epic","availability":"paid","price_cents":449,"tintable":false,"preview":{"style":"head","pattern":"fedora","hue":"#9f8bff"}},
  {"id":"sovereign-crown","kind":"head","name":"Sovereign crown","blurb":"worked gold on an ermine band, eight points, one garnet at the front.","rarity":"legendary","availability":"paid","price_cents":699,"tintable":false,"preview":{"style":"head","pattern":"crown","hue":"#ff9e7a"}},
  {"id":"cloister-hood","kind":"head","name":"Cloister hood","blurb":"a bone wool hood with a peaked cowl, edged in brass, a jade at the throat.","rarity":"rare","availability":"paid","price_cents":349,"tintable":false,"preview":{"style":"head","pattern":"hood","hue":"#7ae0b0"}},
  {"id":"aureole-halo","kind":"halo","name":"Aureole","blurb":"a fine band of polished gold with a single pearl at the front.","rarity":"legendary","availability":"paid","price_cents":599,"tintable":false,"preview":{"style":"halo","pattern":"band","hue":"#e9e5f0"}},
  {"id":"lumen-halo","kind":"halo","name":"Lumen","blurb":"a silver ring of rays, an amethyst at its crown.","rarity":"epic","availability":"paid","price_cents":449,"tintable":false,"preview":{"style":"halo","pattern":"rays","hue":"#9f8bff"}},
  {"id":"porcelain-mask","kind":"face","name":"Porcelain mask","blurb":"ivory porcelain, a brass hairline, one jade tear.","rarity":"epic","availability":"paid","price_cents":449,"tintable":false,"preview":{"style":"face","pattern":"porcelain","hue":"#7ae0b0"}},
  {"id":"aegis-visor","kind":"face","name":"Aegis visor","blurb":"brushed steel across the eyes, a slit of ice-topaz glass.","rarity":"rare","availability":"paid","price_cents":349,"tintable":false,"preview":{"style":"face","pattern":"visor","hue":"#7adfff"}},
  {"id":"emote-wave","kind":"emote","name":"Greeting","blurb":"a raised hand, two easy waves, chin up.","rarity":"common","availability":"paid","price_cents":199,"tintable":false,"preview":{"style":"emote","rows":["#...#","#.#.#","#####",".###.","..#.."]}},
  {"id":"emote-clap","kind":"emote","name":"Applause","blurb":"three unhurried claps for a good fight.","rarity":"common","availability":"paid","price_cents":199,"tintable":false,"preview":{"style":"emote","rows":["#...#",".#.#.","..#..",".#.#.","#...#"]}},
  {"id":"emote-bow","kind":"emote","name":"Reverence","blurb":"a slow court bow, one hand to the heart.","rarity":"rare","availability":"paid","price_cents":199,"tintable":false,"preview":{"style":"emote","rows":["..##.",".##..","####.",".#.#.",".#..#"]}},
  {"id":"emote-salute","kind":"emote","name":"Salute","blurb":"a crisp salute, held, to the other side.","rarity":"common","availability":"paid","price_cents":199,"tintable":false,"preview":{"style":"emote","rows":["###..","#.#..","####.","..#..",".#.#."]}},
  {"id":"emote-shrug","kind":"emote","name":"Nonchalance","blurb":"a shrug with the palms open, unbothered.","rarity":"common","availability":"paid","price_cents":199,"tintable":false,"preview":{"style":"emote","rows":["#...#",".#.#.","..#..","..#..",".#.#."]}},
  {"id":"emote-sit","kind":"emote","name":"Repose","blurb":"sit down wherever you stand, at ease, until you move.","rarity":"rare","availability":"paid","price_cents":149,"tintable":false,"loop":true,"preview":{"style":"emote","rows":[".#...",".#...",".###.","...#.","...##"]}},
  {"id":"gilt-trail","kind":"trail","name":"Gilt sparks","blurb":"fine sparks of gold kicked up at your heels.","rarity":"rare","availability":"paid","price_cents":399,"tintable":false,"preview":{"style":"trail","pattern":"spark","hue":"#eec064"}},
  {"id":"amethyst-trail","kind":"trail","name":"Amethyst dust","blurb":"cut amethyst motes that drift up behind you.","rarity":"rare","availability":"paid","price_cents":399,"tintable":false,"preview":{"style":"trail","pattern":"drift","hue":"#9f8bff"}},
  {"id":"topaz-trail","kind":"trail","name":"Topaz frost","blurb":"ice-topaz facets that settle where you stepped.","rarity":"rare","availability":"paid","price_cents":399,"tintable":false,"preview":{"style":"trail","pattern":"settle","hue":"#7adfff"}},
  {"id":"jade-trail","kind":"trail","name":"Jade steps","blurb":"small jade tiles that climb in step with your stride.","rarity":"common","availability":"paid","price_cents":299,"tintable":false,"preview":{"style":"trail","pattern":"stair","hue":"#7ae0b0"}},
  {"id":"void-badge-founder","kind":"badge","name":"Founder","blurb":"a gold pin set with a garnet, for the players who were here first. Not for sale.","rarity":"exclusive","availability":"grant","price_cents":0,"tintable":false,"preview":{"style":"badge","pattern":"founder","hue":"#ff9e7a"}},
  {"id":"void-badge-player","kind":"badge","name":"VOID player","blurb":"the VOID ring as a silver pin. Every account has one.","rarity":"common","availability":"free","price_cents":0,"tintable":false,"preview":{"style":"badge","pattern":"ring","hue":"#d3d7df"}},
  {"id":"atelier-badge","kind":"badge","name":"Atelier pin","blurb":"amethyst enamel in a polished silver rim.","rarity":"rare","availability":"paid","price_cents":199,"tintable":false,"preview":{"style":"badge","pattern":"atelier","hue":"#9f8bff"}},
  {"id":"laurel-badge","kind":"badge","name":"Laurel pin","blurb":"a brass laurel round a jade.","rarity":"rare","availability":"paid","price_cents":199,"tintable":false,"preview":{"style":"badge","pattern":"laurel","hue":"#7ae0b0"}},
];

export const COSMETIC_IDS: readonly CosmeticId[] = COSMETIC_CATALOG.map((i) => i.id);

export const findCosmetic = (id: string | null | undefined): CosmeticCatalogItem | undefined =>
  id ? COSMETIC_CATALOG.find((i) => i.id === id) : undefined;
