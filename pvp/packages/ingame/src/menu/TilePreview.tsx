/**
 * The data preview inside a mod tile.
 *
 * The grid frames (`289:1611`, `289:3024`) do not put an icon on a tile — they put
 * a **small picture of what the mod draws**: `142 / FPS`, the W-ASD-LMB-RMB keycap
 * cluster, `6.2 / CPS`, four armour arches, the crosshair's plus, two potion rows,
 * `2.0×`, the sprint glyph, `X / Y / Z`. A tile is a sample of the HUD, not a
 * launcher row with a symbol on it, and that is the whole reason the grid reads as
 * a mod list for *this* game rather than for any application.
 *
 * Two rules the frames imply and this file keeps:
 *
 *  - **Monochrome.** `--hue` marks a live value or the selected item and nothing
 *    else (contract §1), so no preview here takes the accent — not the CPS strip,
 *    not the potion swatches, not the reach meter. The tile root carries the hue
 *    for its selection dot; the art never reads it.
 *  - **Real data where there is real data.** FPS, CPS, ping and the coordinates
 *    come off the store, so the tile shows what the HUD would show. The 20 Hz tick
 *    is suppressed while the menu is up (store.ts, `applyTick`), so these are the
 *    last live values and they do not repaint behind the panel.
 *
 * Everything textural is {@link CellArt} — a bitmap of §3 cells. See `cell-art.tsx`
 * for why that rather than an icon font or an inline SVG.
 */

import { type ModId } from '@/bridge/protocol';
import { SETTING_RANGES } from '@/registry';
import { useModSettings, useVoidStore } from '@/store/store';
import { potionMeta } from '@/hud/format';
import { fakeArtSource } from '@/dev/fake-mods';
import { CellArt } from './cell-art';

/* -------------------------------------------------------------------------- */
/* Glyphs                                                                     */
/* -------------------------------------------------------------------------- */

/** The crosshair's plus, with the centre gap the mod actually draws. */
const CROSSHAIR = ['...#...', '...#...', '.......', '##...##', '.......', '...#...', '...#...'];

/** Fullbright: a lit block throwing rays. */
const SUN = ['...#...', '.#...#.', '..###..', '#.###.#', '..###..', '.#...#.', '...#...'];

/** Hitboxes: the bounding box, drawn as its own outline. */
const BOX = ['#######', '#.....#', '#.....#', '#.....#', '#.....#', '#.....#', '#######'];

/** Toggle sprint: a held arrow with its speed lines behind it. */
const SPRINT = ['...#...', '...##..', '##.###.', '##.####', '##.###.', '...##..', '...#...'];

/** One armour piece — four of these are the Armor status preview. */
const ARMOUR = ['.###.', '#...#', '#...#', '#...#'];

/** A potion effect's swatch, the 3 × 3 cell the frame puts before the name. */
const PIP = ['##.', '.##', '##.'];

/* -------------------------------------------------------------------------- */
/* Pieces                                                                     */
/* -------------------------------------------------------------------------- */

function Readout({ value, unit }: { value: string; unit: string }): React.ReactElement {
  return (
    <span className="tart tart--stack">
      <span className="tart__big tnum">{value}</span>
      <span className="tart__unit">{unit}</span>
    </span>
  );
}

function Glyph({ rows, caption }: { rows: readonly string[]; caption: string }): React.ReactElement {
  return (
    <span className="tart tart--stack">
      <CellArt rows={rows} size={9} />
      <span className="tart__unit tart__unit--wide">{caption}</span>
    </span>
  );
}

/** A static keycap block. Deliberately not the live HUD widget: a pressed key is
 *  drawn in `--hue`, and §1 keeps the accent off preview art. */
function Keycaps(): React.ReactElement {
  return (
    <span className="tart tart--keys">
      <span className="tart__krow">
        <span className="tart__key">W</span>
      </span>
      <span className="tart__krow">
        <span className="tart__key">A</span>
        <span className="tart__key">S</span>
        <span className="tart__key">D</span>
      </span>
      <span className="tart__krow">
        <span className="tart__key tart__key--wide">LMB</span>
        <span className="tart__key tart__key--wide">RMB</span>
      </span>
    </span>
  );
}

function Armour(): React.ReactElement {
  const armor = useVoidStore((s) => s.armor);
  // Four pieces whatever the payload says: the preview is the *shape* of the
  // widget, and a row that changes length as gear breaks would make the grid jump.
  const worn = Math.max(1, Math.min(4, armor.filter((slot) => slot.item !== null).length || 4));
  return (
    <span className="tart tart--armour">
      {[0, 1, 2, 3].map((i) => (
        <span className="tart__piece" key={i}>
          <CellArt rows={ARMOUR} size={7} tone={i < worn ? 'on' : 'dim'} />
          <CellArt rows={['#####']} size={3} tone={i < worn ? 'dim' : 'off'} />
        </span>
      ))}
    </span>
  );
}

/** `1:24`. The frame's own fixture times, used when nothing is applied in game. */
const FALLBACK_FX: Array<[string, string]> = [
  ['Speed II', '1:24'],
  ['Strength', '0:42'],
];

function Potions(): React.ReactElement {
  const fx = useVoidStore((s) => s.fx);
  const rows: Array<[string, string]> =
    fx.length > 0
      ? fx.slice(0, 2).map((effect) => {
          const seconds = Math.max(0, Math.round((effect.duration_ms ?? 0) / 1000));
          const time = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
          return [potionMeta(effect).label, time];
        })
      : FALLBACK_FX;
  return (
    <span className="tart tart--rows">
      {rows.map(([label, time]) => (
        <span className="tart__line" key={label}>
          <CellArt rows={PIP} size={4} tone="dim" />
          <span className="tart__lname">{label}</span>
          <span className="tart__ltime tnum">{time}</span>
        </span>
      ))}
    </span>
  );
}

function Coords(): React.ReactElement {
  const pos = useVoidStore((s) => s.pos);
  const axes: Array<[string, string]> = [
    ['X', String(Math.round(pos?.x ?? 128))],
    ['Y', String(Math.round(pos?.y ?? 64))],
    ['Z', String(Math.round(pos?.z ?? -302))],
  ];
  return (
    <span className="tart tart--rows">
      {axes.map(([axis, value]) => (
        <span className="tart__line" key={axis}>
          <span className="tart__axis">{axis}</span>
          <span className="tart__value tnum">{value}</span>
        </span>
      ))}
    </span>
  );
}

/** Zoom: the field-of-view brackets with the divisor in the middle. */
function Zoom({ id }: { id: ModId }): React.ReactElement {
  const settings = useModSettings(id);
  const range = SETTING_RANGES.fov_divisor;
  const divisor = Number(settings.fov_divisor ?? range?.min ?? 2);
  return (
    <span className="tart tart--frame">
      <span className="tart__corner tart__corner--tl" />
      <span className="tart__corner tart__corner--tr" />
      <span className="tart__corner tart__corner--bl" />
      <span className="tart__corner tart__corner--br" />
      <span className="tart__big tnum">{divisor.toFixed(1)}×</span>
    </span>
  );
}

/* -------------------------------------------------------------------------- */

/** Props for {@link TilePreview}. */
export interface TilePreviewProps {
  id: ModId;
}

/**
 * The preview art for one mod.
 *
 * Every id in the registry has a case; the default is the mod's own description
 * initial as a caption, so a mod added to `mods.json` still draws something rather
 * than an empty recess.
 */
export function TilePreview({ id }: TilePreviewProps): React.ReactElement {
  const fps = useVoidStore((s) => s.fps);
  const ping = useVoidStore((s) => s.ping);
  const cpsLeft = useVoidStore((s) => s.cpsLeft);
  const cpsRight = useVoidStore((s) => s.cpsRight);

  switch (id) {
    case 'fps':
      return <Readout value={String(fps || 142)} unit="FPS" />;
    case 'ping':
      // The bridge sends -1 for "no server", which is not a ping and must not be
      // printed as one.
      return <Readout value={String(ping > 0 ? ping : 22)} unit="MS" />;
    case 'cps':
      return <Readout value={(Math.max(cpsLeft, cpsRight) || 6.2).toFixed(1)} unit="CPS" />;
    case 'keystrokes':
      return <Keycaps />;
    case 'armor_status':
      return <Armour />;
    case 'potion_effects':
      return <Potions />;
    case 'coordinates':
      return <Coords />;
    case 'zoom':
      return <Zoom id={id} />;
    case 'crosshair':
      return <Glyph rows={CROSSHAIR} caption="" />;
    case 'fullbright':
      return <Glyph rows={SUN} caption="BRIGHT" />;
    case 'hitboxes':
      return <Glyph rows={BOX} caption="HITBOX" />;
    case 'toggle_sprint':
      return <Glyph rows={SPRINT} caption="SPRINT" />;
    default: {
      // DEV ONLY: a synthetic mod (`src/dev/fake-mods.ts`) borrows one of the real
      // previews rather than getting art of its own. Drawn by rendering this component
      // again, so the borrowed art keeps its own hooks and its own live data, and no
      // new vocabulary is invented for a fixture. Null for every real id.
      const borrowed = fakeArtSource(id);
      if (borrowed !== null) return <TilePreview id={borrowed} />;
      // Every id in the registry has a case above, so this is unreachable today —
      // it exists so a mod added to `mods.json` draws a caption rather than an
      // empty recess while its art is being written.
      return (
        <span className="tart tart--stack">
          <span className="tart__unit tart__unit--wide">{String(id)}</span>
        </span>
      );
    }
  }
}
