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

/** The cape's box on the figure, behind (front view) or over (back view) the torso. */
const CAPE = { x: 35.2, y: 101.2, w: 114.4, h: 226.6 };
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
      <span
        className="figure__cape"
        style={{
          ...box(CAPE.x, CAPE.y, CAPE.w, CAPE.h),
          borderRadius: `${s(8.8)}px ${s(8.8)}px ${s(15.4)}px ${s(15.4)}px`,
        }}
      >
        <CapeFace pattern={cape.pattern} w={s(CAPE.w)} h={s(CAPE.h)} worn />
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
            style={{ ...box(COLLAR.x, COLLAR.y, COLLAR.w, COLLAR.h), background: '#2b2740', borderRadius: s(5.5) }}
          />
          {capeLayer}
        </>
      ) : null}
    </span>
  );
}

// ----------------------------------------------------------------- capes

const CAPE_FILL: Record<string, string> = {
  cell: '#1b1826',
  void: '#080809',
  ring: '#474a4f',
  origin: '#292a2e',
  dusk: '#292a2e',
  static: '#292a2e',
  split: '#292a2e',
  band: '#292a2e',
};

/** The VOID ring in 10px cells on a 14px step, centred in a 96 × 150 cape (`73:1529`). */
const CAPE_RING: readonly [number, number][] = [
  [36, 42], [50, 42], [22, 56], [64, 56], [22, 70], [64, 70], [36, 84], [50, 84],
];

/** Static's scatter, in the card's 96 × 150 frame (`73:1581`). */
const STATIC: readonly [number, number][] = [
  [5, 4], [31, 4], [70, 4], [18, 18], [44, 18], [83, 18], [31, 32], [57, 32], [5, 46], [44, 46],
  [70, 46], [18, 60], [57, 60], [83, 60], [5, 74], [31, 74], [70, 74], [18, 88], [44, 88], [83, 88],
  [31, 102], [57, 102], [5, 116], [44, 116], [70, 116], [18, 130], [57, 130], [83, 130],
];

/** Cell cape on a card: on/dim per cell, six across and nine down (`73:1467`). */
const CELL_CARD = ['.##.##', '##.##.', '#.##.#', '.##.##', '##.##.', '#.##.#', '.##.##', '##.##.', '#.##.#'];

/**
 * A cape's face, drawn into a `w × h` box. Card art is authored at 96 × 150; the worn
 * cape (`worn`) is the figure's 114.4 × 226.6 and the cell pattern is drawn the way the
 * frame wears it — seven across, fourteen down, dimming toward the hem.
 */
function CapeFace({ pattern, w, h, worn = false }: { pattern: string; w: number; h: number; worn?: boolean }) {
  const kx = w / 96;
  const ky = h / 150;
  const cells: ReactElement[] = [];
  const cell = (key: string, x: number, y: number, size: number, fill: string, r = 0.3) =>
    cells.push(
      <i key={key} style={{ left: x, top: y, width: size, height: size, background: fill, borderRadius: size * r }} />,
    );

  switch (pattern) {
    case 'cell':
      if (worn) {
        // `69:176`: 9.9px cells on a 14.3 step, 7 × 14. Alternate cells sit 0.12 apart and
        // the whole field drains 0.06 a row toward the hem.
        const k = w / 114.4;
        for (let row = 0; row < 14; row += 1) {
          for (let col = 0; col < 7; col += 1) {
            const hi = 0.95 - 0.06 * row;
            const a = Math.max(0.12, (row + col) % 2 === 0 ? hi : hi - 0.12);
            cell(`${col}:${row}`, (9.35 + col * 14.3) * k, (8.8 + row * 14.3) * k, 9.9 * k, `rgba(159,139,255,${a.toFixed(2)})`, 0.36);
          }
        }
      } else {
        CELL_CARD.forEach((row, y) =>
          [...row].forEach((c, x) =>
            cell(`${x}:${y}`, (9 + x * 14) * kx, (8 + y * 15) * ky, 9 * kx, c === '#' ? '#9f8bff' : 'rgba(159,139,255,0.35)', 0.33),
          ),
        );
      }
      break;
    case 'void':
    case 'ring':
      CAPE_RING.forEach(([x, y]) => cell(`${x}:${y}`, x * kx, (y + (worn ? 30 : 0)) * ky, 10 * kx, '#ededf0'));
      break;
    case 'origin':
      cells.push(<i key="half" style={{ left: w / 2, top: 0, width: w / 2, height: h, background: '#5c5e63' }} />);
      break;
    case 'dusk':
      ['#5c5c60', '#47474b', '#363638'].forEach((fill, i) =>
        cells.push(<i key={fill} style={{ left: 0, top: (i * h) / 3, width: w, height: h / 3, background: fill }} />),
      );
      break;
    case 'static':
      STATIC.forEach(([x, y]) => cell(`${x}:${y}`, x * kx, y * ky, 8 * kx, 'rgba(237,237,240,0.55)', 0.25));
      break;
    default:
      break;
  }

  return (
    <span className="capeface" style={{ width: w, height: h, background: CAPE_FILL[pattern] ?? '#292a2e' }}>
      {cells}
    </span>
  );
}

// ------------------------------------------------------------- store art

/** A trail: cells stepping off toward the top-right, shrinking and fading (`73:1616`). */
const SPARK: readonly [number, number, number, number][] = [
  [210, 30, 20, 1], [186, 56, 16, 0.9], [164, 76, 13, 0.8], [144, 92, 11, 0.7],
  [127, 105, 9, 0.6], [112, 115, 7, 0.5], [99, 123, 6, 0.4],
];

function hexA(hex: string, a: number): string {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

function TrailArt({ pattern, hue }: { pattern: string; hue: string }) {
  let cells: [number, number, number, number][] = [...SPARK];
  if (pattern === 'drift') cells = SPARK.map(([x, y, s, a], i) => [x - 40 + (i % 2) * 18, y + 10, s, a]);
  if (pattern === 'ring')
    cells = Array.from({ length: 10 }, (_, i) => {
      const t = (i / 10) * Math.PI * 2;
      return [161 + Math.cos(t) * 56 - 6, 94 + Math.sin(t) * 56 - 6, 12, 0.3 + (i / 10) * 0.7];
    });
  if (pattern === 'stair') cells = Array.from({ length: 7 }, (_, i) => [96 + i * 20, 136 - i * 16, 12, 0.35 + i * 0.1]);
  return (
    <>
      {cells.map(([x, y, s, a]) => (
        <i key={`${x}:${y}`} className="art__cell" style={{ left: x, top: y, width: s, height: s, borderRadius: s * 0.3, background: hexA(hue, a) }} />
      ))}
    </>
  );
}

function NameplateArt({ pattern, hue }: { pattern: string; hue: string }) {
  const mark: ReactElement[] = [];
  if (pattern === 'cell')
    for (let y = 0; y < 3; y += 1) for (let x = 0; x < 3; x += 1) mark.push(<i key={`${x}${y}`} style={{ left: 17 + x * 8, top: 13 + y * 8, width: 6, height: 6, background: hue }} />);
  if (pattern === 'ring')
    RING.forEach(([x, y]) => mark.push(<i key={`${x}${y}`} style={{ left: 16 + x * 3.4, top: 14 + y * 3.4, width: 3, height: 3, background: hue }} />));
  if (pattern === 'dots')
    [0, 1, 2].forEach((i) => mark.push(<i key={i} style={{ left: 18 + i * 9, top: 22, width: 6, height: 6, background: hue }} />));
  return (
    <span className="art__plate">
      {mark}
      {pattern === 'bar' ? <i style={{ left: 18, top: 34, width: 120, height: 4, background: hue }} /> : null}
      <span className="art__plate-name" style={pattern === 'plain' || pattern === 'bar' ? { left: 18 } : undefined}>
        Kestrel
      </span>
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
          <CapeFace pattern={art.pattern} w={96} h={150} />
        </span>
      ) : null}
      {art.kind === 'trail' ? <TrailArt pattern={art.pattern} hue={art.hue} /> : null}
      {art.kind === 'nameplate' ? <NameplateArt pattern={art.pattern} hue={art.hue} /> : null}
      {art.kind === 'emote' ? <EmoteArt pattern={art.pattern} /> : null}
    </span>
  );
}
