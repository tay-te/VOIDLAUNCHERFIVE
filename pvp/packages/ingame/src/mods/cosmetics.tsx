/**
 * Cosmetics — two players in cells, and what this client draws on them.
 *
 * One of the mods that draw into the **world**, where there is no HTML to reuse — so the preview
 * is a diagram, built from the §3 cell like everything in `menu/gameplay-previews.tsx`, and kept
 * in this file rather than added to that one: what a single mod draws lives in that mod's own
 * module (`mods/art.tsx` states the rule).
 *
 * **What it is a picture of.** Not of an item. What a player *wears* is account data the launcher
 * owns and the wardrobe chooses; these settings are only about what this client draws, so
 * the diagram holds the wardrobe still — a cape, a halo, a trail, the same set on both figures —
 * and moves only what the switches move:
 *
 *   · `others` — the right-hand figure is another VOID player. Off, they stand there in vanilla;
 *     their set is not gone, this client just does not draw it.
 *   · `glow_level` — the halos' bloom, as a dim ring of cells around the bright ones (`low` and
 *     `high` alike; the diagram has no finer step). Off, the same halo, flat.
 *   · `reactive` — the fight: both capes kick out at the hem (whipped by a hit) and the halos
 *     throw a pulse of cells to either side. `subtle` is a shorter kick and a fainter pulse;
 *     `off` leaves the set standing still.
 *   · `own_first_person` — the frame between the two is your own view. Your trail only reaches
 *     it when this is on; your cape and halo never do, because the game does not draw your model
 *     there either.
 *   · `emote_camera` — the orbit, as a ring of cells on the floor around you with the camera on
 *     it. Off, there is no ring: the view stays where it was.
 *
 * Monochrome at both densities. The items have colours, but which colours is the wardrobe's
 * business and not a setting here, so drawing one would be a colour nobody on this page chose —
 * the accent rule (`design/quiet-cell-system.md` §1) has nothing to stop for.
 *
 * The thumbnail is **the same component at tile density**, through `dense`, for the reason every
 * world-drawing mod gives: a tile and a page that are different pictures make a player work out
 * that they are the same mod.
 */

import type { CSSProperties } from 'react';

import { useModSettings } from '@/store/store';
import type { DiagramProps } from '@/menu/gameplay-previews';
import { defineMod } from './types';

/** The scene, in cells. */
const COLS = 40;
const ROWS = 26;

/** Cell edge in px: the page, and the tile (40 x 3 = 120, inside the narrowest 123px well). */
const EDGE = { page: 8, tile: 3 } as const;

/** One lit cell: where, and how bright (0-1, the menu's own ink at that alpha). */
interface Cell {
  x: number;
  y: number;
  a: number;
}

const BODY = 0.42;
const ITEM = 0.9;
const HALO = 1;
const BLOOM = 0.22;
const FRAME = 0.16;

/** A player, face on, with its left edge at `x0`: head, arms, body, legs. */
function figure(x0: number): Cell[] {
  const out: Cell[] = [];
  const rect = (x: number, y: number, w: number, h: number, a: number): void => {
    for (let i = 0; i < w; i++) for (let j = 0; j < h; j++) out.push({ x: x + i, y: y + j, a });
  };
  rect(x0 + 2, 4, 4, 4, BODY + 0.12); // head
  rect(x0 + 2, 8, 4, 6, BODY); // body
  rect(x0 + 1, 8, 1, 6, BODY - 0.08); // arms
  rect(x0 + 6, 8, 1, 6, BODY - 0.08);
  rect(x0 + 2, 14, 2, 6, BODY - 0.04); // legs
  rect(x0 + 4, 14, 2, 6, BODY - 0.04);
  return out;
}

/** The cape, where it shows past a face-on body: two edges from the shoulders down. */
function cape(x0: number): Cell[] {
  const out: Cell[] = [];
  for (let y = 9; y <= 18; y++) {
    out.push({ x: x0, y, a: ITEM }, { x: x0 + 7, y, a: ITEM });
  }
  return out;
}

/** The halo over the head, and its bloom when `glow` is on. */
function halo(x0: number, glow: boolean): Cell[] {
  const out: Cell[] = [];
  for (let x = x0 + 2; x <= x0 + 5; x++) out.push({ x, y: 1, a: HALO });
  if (glow) {
    for (let x = x0 + 1; x <= x0 + 6; x++) out.push({ x, y: 0, a: BLOOM }, { x, y: 2, a: BLOOM });
    out.push({ x: x0 + 1, y: 1, a: BLOOM }, { x: x0 + 6, y: 1, a: BLOOM });
  }
  return out;
}

/** A trail left behind a player walking right: cells fading out to the left of the feet. */
function trail(x0: number): Cell[] {
  const out: Cell[] = [];
  for (let i = 1; i <= 6; i++) {
    const a = ITEM * (1 - i / 7);
    out.push({ x: x0 - i, y: 19 - (i % 2), a });
  }
  return out;
}

/** Your own view, as a frame of cells; the trail reaches its bottom edge only when allowed. */
function firstPerson(ownTrail: boolean): Cell[] {
  const out: Cell[] = [];
  const left = 17;
  const right = 23;
  const top = 12;
  const bottom = 21;
  for (let x = left; x <= right; x++) out.push({ x, y: top, a: FRAME }, { x, y: bottom, a: FRAME });
  for (let y = top + 1; y < bottom; y++) out.push({ x: left, y, a: FRAME }, { x: right, y, a: FRAME });
  // The crosshair, so the frame reads as a view rather than as a box.
  out.push({ x: 20, y: 16, a: 0.5 });
  if (ownTrail) {
    for (let x = left + 1; x < right; x++) {
      out.push({ x, y: bottom - 1 - ((x + 1) % 2), a: ITEM * (0.35 + 0.1 * (x - left)) });
    }
  }
  return out;
}

/**
 * `reactive`: the cape's hem whipped outward and a pulse off either side of the halo. Drawn on
 * every figure that draws its set, because reactions play on other players' cosmetics too.
 */
function reaction(x0: number, level: string): Cell[] {
  if (level === 'off') return [];
  const full = level !== 'subtle';
  const out: Cell[] = [];
  const kick = full ? 2 : 1;
  for (let y = 15; y <= 18; y++) {
    const k = Math.min(kick, y - 14);
    out.push({ x: x0 - k, y, a: ITEM * 0.55 }, { x: x0 + 7 + k, y, a: ITEM * 0.55 });
  }
  const a = full ? 0.6 : 0.3;
  out.push({ x: x0 - 1, y: 1, a }, { x: x0 + 8, y: 1, a });
  if (full) out.push({ x: x0 - 2, y: 1, a: a * 0.5 }, { x: x0 + 9, y: 1, a: a * 0.5 });
  return out;
}

/** The emote camera's orbit: a ring on the floor around you, and the camera on it. */
function orbit(x0: number): Cell[] {
  const out: Cell[] = [];
  const cx = x0 + 3.5;
  const cy = 22.5;
  const seen = new Set<string>();
  for (let step = 0; step < 28; step++) {
    const t = (step / 28) * Math.PI * 2;
    const x = Math.round(cx + Math.cos(t) * 6.5);
    const y = Math.round(cy + Math.sin(t) * 2.4);
    const key = `${x},${y}`;
    if (step % 2 === 0 && !seen.has(key) && x >= 0 && y < ROWS) {
      seen.add(key);
      out.push({ x, y, a: 0.3 });
    }
  }
  out.push({ x: Math.round(cx + 6.5), y: Math.round(cy), a: 1 });
  return out;
}

/** Where each figure stands: you on the left, another VOID player on the right. */
const YOU = 9;
const OTHER = 26;

export function CosmeticsPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('cosmetics');
  const others = settings.others !== false;
  const ownFirstPerson = settings.own_first_person === true;
  const glow = settings.glow_level !== 'off';
  const emoteCamera = settings.emote_camera !== false;
  const reactive = typeof settings.reactive === 'string' ? settings.reactive : 'full';

  const cells: Cell[] = [
    ...(emoteCamera ? orbit(YOU) : []),
    ...trail(YOU),
    ...cape(YOU),
    ...figure(YOU),
    ...halo(YOU, glow),
    ...reaction(YOU, reactive),
    ...firstPerson(ownFirstPerson),
    ...(others ? [...trail(OTHER), ...cape(OTHER)] : []),
    ...figure(OTHER),
    ...(others ? halo(OTHER, glow) : []),
    ...(others ? reaction(OTHER, reactive) : []),
  ];

  const edge = dense ? EDGE.tile : EDGE.page;
  const scene: CSSProperties = { position: 'relative', width: COLS * edge, height: ROWS * edge };
  return (
    <div className={['gprev', dense ? 'gprev--tile' : null, className].filter(Boolean).join(' ')}>
      <div style={scene}>
        {cells.map((c, i) => (
          <span
            key={i}
            style={{
              position: 'absolute',
              left: c.x * edge,
              top: c.y * edge,
              width: edge,
              height: edge,
              borderRadius: '30%',
              background: `rgba(237, 238, 239, ${c.a.toFixed(3)})`,
            }}
          />
        ))}
      </div>
      {dense ? null : (
        <>
          <div className="gprev__row">
            <span className="gprev__rowlabel">You</span>
            <span className="gprev__rowlabel">Your view</span>
            <span className="gprev__rowlabel">Other</span>
          </div>
          <p className="gprev__reading">
            {(others
              ? 'Other VOID players wear what they chose. '
              : 'Other VOID players are drawn as vanilla; their set is still theirs. ') +
              (glow ? 'Halos and bright trails glow. ' : 'Halos and trails draw flat. ') +
              (ownFirstPerson
                ? 'Your trail shows in first person. '
                : 'In first person you see nothing of your own. ') +
              (reactive === 'off'
                ? 'Cosmetics keep to their idle motion in a fight. '
                : reactive === 'subtle'
                  ? 'Cosmetics answer the fight, quietly. '
                  : 'Capes whip, wings flare and halos pulse with the fight. ') +
              (emoteCamera
                ? 'While you emote, the camera circles you.'
                : 'While you emote, your view stays put.')}
          </p>
        </>
      )}
    </div>
  );
}

function CosmeticsPreviewThumbnail(): React.ReactElement {
  return <CosmeticsPreview dense className="tart" />;
}

export default defineMod({
  id: 'cosmetics',
  Thumbnail: CosmeticsPreviewThumbnail,
  Preview: CosmeticsPreview,
});
