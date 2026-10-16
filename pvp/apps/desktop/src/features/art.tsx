/**
 * The v1 Ship's drawn marks — everything on the launcher that is art rather than a
 * control, built from the §3 cell the way the frames build it.
 *
 *   `RingMark`        the VOID mark: sixteen 4px cells in a ring (`68:1554`)
 *   `Initial`         a friend's avatar: a circle with their initial (`56:22`)
 *   `FaceAvatar`      the profile chip's cell face (`68:1578`)
 *   `CategoryCube`    the isometric cube per mod category (`81:6357`), the frame's own SVG
 *   `Character`       the player in their cape, back or front (`69:162`, `75:544`)
 *   `CosmeticArt`     a Store item's picture (`73:1466` and siblings)
 *
 * A cosmetic's picture is the item itself: `scripts/gen-cosmetics.mjs` renders it from the
 * bundled model and textures into `assets/cosmetics/`, so the Store shows the game's texels.
 *
 * Positions are the frames' own, so these are absolutely placed boxes rather than flowed
 * ones: a figure assembled from parts has no layout to flow.
 */

import type { CSSProperties, ReactElement } from 'react';

import cubeHud from '../assets/icons/cube-hud.svg';
import cubePvp from '../assets/icons/cube-pvp.svg';
import cubeUtility from '../assets/icons/cube-utility.svg';
import cubeVisual from '../assets/icons/cube-visual.svg';
import type { ArtStyle, CosmeticItem } from '../local/catalog';

// ------------------------------------------------------------------ the mark

/** Cell origins of the 16-cell ring on a 7 × 7 grid of 4px steps (`68:1554`). */
const RING: readonly [number, number][] = [
  [2, 0], [3, 0], [4, 0], [1, 1], [5, 1], [0, 2], [6, 2], [0, 3],
  [6, 3], [0, 4], [6, 4], [1, 5], [5, 5], [2, 6], [3, 6], [4, 6],
];

export function RingMark({ size = 28, tone = 'rgba(237,238,239,0.92)' }: { size?: number; tone?: string }) {
  const step = size / 7;
  return (
    <span className="ringmark" style={{ width: size, height: size }} aria-hidden="true">
      {RING.map(([x, y]) => (
        <i
          key={`${x}:${y}`}
          style={{
            left: x * step,
            top: y * step,
            width: step,
            height: step,
            borderRadius: step * 0.3,
            background: tone,
          }}
        />
      ))}
    </span>
  );
}

// --------------------------------------------------------------- avatars

/** A friend: a filled circle with their initial. Offline rows draw it at half opacity. */
export function Initial({
  name,
  size = 32,
  online,
  dim,
}: {
  name: string;
  size?: number;
  /** Draws the green presence dot at the bottom-right. */
  online?: boolean;
  dim?: boolean;
}) {
  return (
    <span className={`initial${dim ? ' is-dim' : ''}`} style={{ width: size, height: size }} aria-hidden="true">
      <span className="initial__letter">{name.charAt(0).toUpperCase()}</span>
      {online ? <span className="initial__dot" /> : null}
    </span>
  );
}

/** The profile chip's face: twenty 3px cells on a 32px circle (`68:1578`). */
const FACE: readonly [number, number][] = [
  [11, 5], [14, 5], [17, 5], [8, 8], [20, 8], [8, 11], [20, 11], [11, 14], [14, 14], [17, 14],
  [8, 17], [11, 17], [14, 17], [17, 17], [20, 17], [5, 20], [23, 20], [5, 23], [23, 23],
];

export function FaceAvatar({ size = 32 }: { size?: number }) {
  const k = size / 32;
  return (
    <span className="faceavatar" style={{ width: size, height: size }} aria-hidden="true">
      {FACE.map(([x, y]) => (
        <i key={`${x}:${y}`} style={{ left: x * k, top: y * k, width: 3 * k, height: 3 * k }} />
      ))}
    </span>
  );
}

// ------------------------------------------------------------- categories

export type Category = 'hud' | 'pvp' | 'visual' | 'utility';

const CUBES: Record<Category, string> = {
  hud: cubeHud,
  pvp: cubePvp,
  visual: cubeVisual,
  utility: cubeUtility,
};

export function CategoryCube({ category, size = 18 }: { category: Category; size?: number }) {
  return <img className="cube" src={CUBES[category]} width={size} height={size} alt="" aria-hidden="true" />;
}

// ---------------------------------------------------------------- the player

/**
 * The parts of the figure at scale 1, in the order they paint (`69:162`). The figure is
 * 184.8 × 374; the Store sheet draws it at 0.9.
 */
const BODY: readonly { part: string; x: number; y: number; w: number; h: number; fill: string; r: string }[] = [
  { part: 'head', x: 46.2, y: 0, w: 92.4, h: 92.4, fill: '#43454c', r: '11px' },
  { part: 'hair', x: 46.2, y: 0, w: 92.4, h: 61.6, fill: '#393b41', r: '11px 11px 0 0' },
  { part: 'nape', x: 46.2, y: 85.8, w: 92.4, h: 8.8, fill: '#383a40', r: '0' },
  { part: 'torso', x: 46.2, y: 94.6, w: 92.4, h: 138.6, fill: '#50535a', r: '6.6px' },
  { part: 'arm-l', x: 0, y: 94.6, w: 44, h: 138.6, fill: '#494b52', r: '11px' },
  { part: 'hand-l', x: 0, y: 209, w: 44, h: 24.2, fill: '#6e7178', r: '8.8px' },
  { part: 'arm-r', x: 140.8, y: 94.6, w: 44, h: 138.6, fill: '#494b52', r: '11px' },
  { part: 'hand-r', x: 140.8, y: 209, w: 44, h: 24.2, fill: '#6e7178', r: '8.8px' },
  { part: 'leg-l', x: 46.2, y: 233.2, w: 45.1, h: 140.8, fill: '#2f3136', r: '6.6px' },
  { part: 'leg-r', x: 93.5, y: 233.2, w: 45.1, h: 140.8, fill: '#2f3136', r: '6.6px' },
  { part: 'boot-l', x: 46.2, y: 347.6, w: 45.1, h: 26.4, fill: '#25262a', r: '8.8px' },
  { part: 'boot-r', x: 93.5, y: 347.6, w: 45.1, h: 26.4, fill: '#25262a', r: '8.8px' },
];

/**
 * The cape's box on the figure, behind (front view) or over (back view) the torso: Minecraft's
 * proportions (ten pixels to the torso's eight, sixteen down), so its texels are square.
 */
const CAPE = { x: 35.2, y: 101.2, w: 114.4, h: 183 };
const COLLAR = { x: 33, y: 92.4, w: 118.8, h: 11 };

/** The face the front view adds: two eyes and a mouth, in the figure's own greys. */
const FACE_FRONT = [
  { x: 64, y: 50, w: 13, h: 10, fill: '#25262a' },
  { x: 108, y: 50, w: 13, h: 10, fill: '#25262a' },
  { x: 80, y: 72, w: 25, h: 6, fill: '#2f3136' },
];

export function Character({
  cape,
  view = 'back',
  scale = 1,
  dim = false,
}: {
  /** The equipped cape's art, or nothing for a bare figure. */
  cape?: ArtStyle | undefined;
  view?: 'back' | 'front';
  scale?: number;
  /** Greys the figure, for the crash state behind its card. */
  dim?: boolean;
}): ReactElement {
  const s = (v: number) => v * scale;
  const box = (x: number, y: number, w: number, h: number): CSSProperties => ({
    left: s(x),
    top: s(y),
    width: s(w),
    height: s(h),
  });
  const capeLayer =
    cape && cape.kind === 'cape' ? (
      <span className="figure__cape" style={box(CAPE.x, CAPE.y, CAPE.w, CAPE.h)}>
        <img className="pixelart" src={view === 'back' ? cape.face : cape.lining} alt="" draggable={false} />
      </span>
    ) : null;

  return (
    <span
      className={`figure${dim ? ' is-dim' : ''}`}
      style={{ width: s(184.8), height: s(374) }}
      role="img"
      aria-label={cape ? `Your character, ${view === 'back' ? 'from behind' : 'from the front'}` : 'Your character'}
    >
      {/* Front view: the cape hangs behind, so it paints first and only its edges show. */}
      {view === 'front' ? capeLayer : null}
      {BODY.map((p) => (
        <i
          key={p.part}
          style={{
            ...box(p.x, p.y, p.w, p.h),
            background: p.fill,
            borderRadius: p.r.replace(/[\d.]+px/g, (m) => `${s(parseFloat(m))}px`),
          }}
        />
      ))}
      {view === 'front'
        ? FACE_FRONT.map((f, i) => (
            <i key={`face${i}`} style={{ ...box(f.x, f.y, f.w, f.h), background: f.fill, borderRadius: s(2) }} />
          ))
        : null}
      {cape && view === 'back' ? (
        <>
          <i
            style={{ ...box(COLLAR.x, COLLAR.y, COLLAR.w, COLLAR.h), background: '#36323b', borderRadius: s(5.5) }}
          />
          {capeLayer}
        </>
      ) : null}
    </span>
  );
}

// ------------------------------------------------------------- store art

/** The founder's and every badge's plate: the pin beside a name, as the nametag shows it. */
function BadgeArt({ src }: { src: string }) {
  return (
    <span className="art__plate">
      <img className="art__pin pixelart" src={src} alt="" draggable={false} />
      <span className="art__plate-name">Kestrel</span>
    </span>
  );
}

function EmoteArt({ pattern }: { pattern: readonly string[] }) {
  const size = 10;
  const step = 13;
  const w = (pattern[0]?.length ?? 0) * step;
  const h = pattern.length * step;
  return (
    <span className="art__emote" style={{ width: w, height: h }}>
      {pattern.flatMap((row, y) =>
        [...row].map((c, x) =>
          c === '#' ? <i key={`${x}:${y}`} style={{ left: x * step, top: y * step, width: size, height: size }} /> : null,
        ),
      )}
    </span>
  );
}

/** A Store item's picture, filling a card's 188px art well or the sheet's preview. */
export function CosmeticArt({ item }: { item: CosmeticItem }) {
  const art = item.art;
  return (
    <span className={`art art--${art.kind}`} aria-hidden="true">
      {art.kind === 'cape' ? (
        <span className="art__cape">
          <img className="pixelart" src={art.face} alt="" draggable={false} />
        </span>
      ) : null}
      {art.kind === 'model' ? <img className="art__model" src={art.src} alt="" draggable={false} /> : null}
      {art.kind === 'trail' ? <img className="art__trail" src={art.src} alt="" draggable={false} /> : null}
      {art.kind === 'badge' ? <BadgeArt src={art.src} /> : null}
      {art.kind === 'emote' ? <EmoteArt pattern={art.pattern} /> : null}
    </span>
  );
}
