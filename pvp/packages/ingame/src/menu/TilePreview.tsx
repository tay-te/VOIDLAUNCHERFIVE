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
 * ## This is the grid's art, and only the grid's
 *
 * The mod **page** no longer draws any of it. Every page now draws either the real HUD
 * component or, for the four mods that draw into the world, a diagram fed by the game's own
 * numbers (`ModSettingsScreen.tsx`, `LIVE_WIDGETS`) — because a page whose job is configuring a
 * mod has to move when a setting moves, and cell art cannot. What is left here is the job cell
 * art is genuinely better at: thirteen small, uniform, instantly recognisable pictures sitting
 * side by side, where the question is "which mod is this" rather than "what will it look like".
 *
 * It is still the fallback for a mod added to `mods.json` before its preview is written, and it
 * is still what `?fake=` borrows for a synthetic tile.
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
 * Everything textural is §3's cell — a square at a 30% radius. Nothing here is a bitmap of
 * them any more: the last three, `SUN` / `BOX` / `SPRINT`, were 7 x 7 pixel-art glyphs with
 * `BRIGHT`, `HITBOX` and `SPRINT` captioned underneath, and they are the mod page's own
 * diagrams now (`gameplay-previews.tsx`, `dense`). Armour's four arches and the potion swatch
 * went the same way, to the shapes the widgets themselves draw. See `cell-art.tsx` for why the
 * primitive is cells at all rather than an icon font or an inline SVG — that argument still
 * holds; what changed is that a tile is no longer drawn *as a bitmap*.
 */

import { type ModId } from '@/bridge/protocol';
import { Crosshair, asCrosshairStyle } from '@/ui';
import { SETTING_RANGES } from '@/registry';
import { useModSettings, useVoidStore } from '@/store/store';
import { potionMeta } from '@/hud/format';
import { cx } from '@/ui';
import { fakeArtSource } from '@/dev/fake-mods';
import { Watermark } from '@/hud/watermark';
import {
  FullbrightPreview,
  HitboxPreview,
  SprintPreview,
  ZoomPreview,
} from './gameplay-previews';

/* -------------------------------------------------------------------------- */
/* Glyphs                                                                     */
/* -------------------------------------------------------------------------- */

/* -------------------------------------------------------------------------- */
/* Pieces                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * A cell edge at the caller's scale, in whole pixels.
 *
 * The cell is a square with a 30% radius drawn as a `<span>` of a stated width, so it is the
 * one thing here that cannot be scaled from CSS — hence a number rather than a class. Rounded
 * because a fractional edge is a fractional *step*: rows of cells are flex rows of squares, and
 * at 22.5px the engine lands successive rows half a pixel apart and the bitmap shears.
 */
function cells(size: number, scale: number): number {
  return Math.max(1, Math.round(size * scale));
}

function Readout({ value, unit }: { value: string; unit: string }): React.ReactElement {
  return (
    <span className="tart tart--stack">
      <span className="tart__big tnum">{value}</span>
      <span className="tart__unit">{unit}</span>
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

/**
 * Four armour slots, each a swatch over its durability.
 *
 * WAS a 5 x 4 bitmap of an arch per slot with a 5 x 1 rule under it — pixel art of a helmet,
 * which at 7px cells came out as four blocky grey blobs and was, once the three world mods
 * stopped being bitmaps, the crudest thing on the grid.
 *
 * This is the widget's own structure instead: `ArmorList` draws a swatch, a label and a
 * durability bar per row, and the two of those three that survive at 145px are the swatch and
 * the bar. The swatch is §3's cell, which is what `v-armorlist__icon` is; the bar is the same
 * two fill steps the meter uses.
 *
 * **And it is live.** The old art counted how many slots were occupied and nothing else, so a
 * player one hit from losing a chestplate saw exactly what a player in fresh diamond saw. The
 * bar is `1 - damage / max_damage` off the real payload — the same fraction `ArmorList`
 * fills — so the tile carries the reading the HUD does. Monochrome: `armor_status.warn_below`
 * turns the HUD's bar amber over the game world, and §1 keeps that off the menu's own surface.
 */
function Armour(): React.ReactElement {
  const armor = useVoidStore((s) => s.armor);
  // The four worn slots, in the order the widget lists them. `held` is a fifth row on the HUD
  // and is left off here: four columns is what 123px holds, and armour is what the mod is
  // named for. Always four, even with nothing equipped — a row that changed length as gear
  // broke would make the tile's art jump inside a grid that never moves.
  const slots = ['helmet', 'chestplate', 'leggings', 'boots'] as const;
  return (
    <span className="tart tart--armour">
      {slots.map((slot) => {
        const worn = armor.find((piece) => piece.slot === slot && piece.item !== null) ?? null;
        const max = worn?.max_damage ?? 0;
        // `damage` counts wear *up* from 0, so what is left is the complement.
        const left = worn && max > 0 ? Math.max(0, Math.min(1, 1 - (worn.damage ?? 0) / max)) : 0;
        return (
          <span className="tart__piece" key={slot}>
            <span className={cx('tart__slot', worn === null && 'tart__slot--empty')} />
            <span className="tart__wear">
              <span className="tart__wearfill" style={{ width: `${(left * 100).toFixed(1)}%` }} />
            </span>
          </span>
        );
      })}
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
          {/* `PotionList` puts a swatch before the name and so does this — one §3 cell, not the
              3 x 3 checkerboard bitmap it was, which at 4px read as grit. Monochrome: the
              effect's own colour is the HUD's, over the game world (§1). */}
          <span className="tart__pip" />
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

/* -------------------------------------------------------------------------- */

/** Props for {@link TilePreview}. */
export interface TilePreviewProps {
  id: ModId;
  /**
   * Multiplier on every cell edge. 1 is the tile; the mod page draws the same art at 2.6.
   *
   * The type around the art scales in CSS (`.modpage .preview__stage`), because font sizes are
   * exactly what CSS is for — but a cell's edge is an inline length and has to come from here.
   * Two mechanisms for one enlargement is not ideal; the alternative is a second set of art
   * for the page, which is worse: the grid and the page would then be able to disagree about
   * what a mod looks like.
   */
  scale?: number;
}

/**
 * The preview art for one mod.
 *
 * Every id in the registry has a case; the default is the mod's own description
 * initial as a caption, so a mod added to `mods.json` still draws something rather
 * than an empty recess.
 */
export function TilePreview({ id, scale = 1 }: TilePreviewProps): React.ReactElement {
  const settings = useModSettings(id);
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
    // The four world mods. Same components the mod page draws, at tile density — see
    // `gameplay-previews.tsx`, "One drawing, two densities", for why the grid stopped having
    // art of its own for them.
    case 'zoom':
      return <ZoomPreview dense className="tart" />;
    case 'fullbright':
      return <FullbrightPreview dense className="tart" />;
    case 'hitboxes':
      return <HitboxPreview dense className="tart" />;
    case 'toggle_sprint':
      return <SprintPreview dense className="tart" />;
    // The second preview that is the real widget rather than a picture of one, and for the same
    // reason as the watermark: the crosshair is small enough that the real thing fits, and its
    // whole identity is a shape the settings change. It used to be a fixed 7 x 7 bitmap of a
    // plus, so a player who had chosen `circle` or `t_shape` saw a cross on the tile.
    //
    // **Monochrome, like the other eleven.** `crosshair.color` is not passed: the tile's rule
    // is that no art here takes a colour (see the header), and every other tile keeps it —
    // the FPS tile does not show `fps.color` and the potion tile does not show effect colours.
    // The page, which is where the colour is chosen, does show it.
    case 'crosshair':
      return (
        <span className="tart tart--stack">
          <Crosshair
            shape={asCrosshairStyle(settings.style)}
            size={Number(settings.size ?? 5)}
            thickness={Number(settings.thickness ?? 1)}
            gap={Number(settings.gap ?? 2)}
            outline={false}
            centerDot={settings.center_dot === true}
            unit={Math.max(2, Math.round(3 * scale))}
          />
        </span>
      );
    // The one preview that is literally the widget. Every other case here is a *picture* of what
    // the mod draws, redrawn at tile size; the watermark is small enough that the real thing
    // fits, and drawing an impression of a mark instead of the mark would be absurd. It reads
    // the same `style` setting the HUD does, so switching to `word` changes this tile too.
    case 'watermark':
      return (
        <span className="tart tart--stack">
          <Watermark cell={cells(3, scale)} className="tart__mark" />
        </span>
      );
    default: {
      // DEV ONLY: a synthetic mod (`src/dev/fake-mods.ts`) borrows one of the real
      // previews rather than getting art of its own. Drawn by rendering this component
      // again, so the borrowed art keeps its own hooks and its own live data, and no
      // new vocabulary is invented for a fixture. Null for every real id.
      const borrowed = fakeArtSource(id);
      if (borrowed !== null) return <TilePreview id={borrowed} scale={scale} />;
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
