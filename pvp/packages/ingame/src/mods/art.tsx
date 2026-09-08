/**
 * The drawing vocabulary the grid thumbnails are built from.
 *
 * These are the pieces more than one mod needs — a big readout over a unit, a row of live
 * label/value lines — lifted out of what used to be one thirteen-arm `switch` in
 * `menu/TilePreview.tsx`. Everything a *single* mod draws lives in that mod's own module
 * (`mods/<id>.tsx`) instead, so this file stops growing when the roster does.
 *
 * The rules every thumbnail keeps, unchanged from the frames and from
 * `design/quiet-cell-system.md`:
 *
 *  - **Monochrome.** `--hue` marks a live value or the selected item and nothing else (§1),
 *    and §1 is explicit that this is absolute on the menu's own surface, "the mod tiles
 *    included". So no thumbnail reads `fps.color`, `crosshair.color` or an effect's own ink.
 *    The tile root carries the hue for its selection dot; the art never reads it.
 *  - **Real data where there is real data.** FPS, CPS, ping, position and armour come off the
 *    store, so a tile shows what the HUD would show. The 20 Hz tick is suppressed while the
 *    menu is up (`store.ts`, `applyTick`), so these are the last live values and they do not
 *    repaint behind the panel.
 *  - **Everything textural is §3's cell** — a square at a 30% radius. Nothing here is a
 *    *bitmap* of them any more; see `menu/cell-art.tsx` for why the primitive is cells at all
 *    rather than an icon font or an inline SVG.
 */

import { cx } from '@/ui';
import { useVoidStore } from '@/store/store';
import { potionMeta } from '@/hud/format';

/**
 * A cell edge at the caller's scale, in whole pixels.
 *
 * Rounded because a fractional edge is a fractional *step*: rows of cells are flex rows of
 * squares, and at 22.5px the engine lands successive rows half a pixel apart and the bitmap
 * shears.
 */
export function cells(size: number, scale: number): number {
  return Math.max(1, Math.round(size * scale));
}

/** One big figure over its unit — the shape of every chip mod's tile. */
export function Readout({ value, unit }: { value: string; unit: string }): React.ReactElement {
  return (
    <span className="tart tart--stack">
      <span className="tart__big tnum">{value}</span>
      <span className="tart__unit">{unit}</span>
    </span>
  );
}

/** A stack of `label · value` lines — coordinates and potion effects are both this shape. */
export function Lines({
  rows,
  labelClass,
  valueClass,
}: {
  rows: ReadonlyArray<readonly [string, string]>;
  labelClass: string;
  valueClass: string;
}): React.ReactElement {
  return (
    <span className="tart tart--rows">
      {rows.map(([label, value]) => (
        <span className="tart__line" key={label}>
          <span className={labelClass}>{label}</span>
          <span className={cx(valueClass, 'tnum')}>{value}</span>
        </span>
      ))}
    </span>
  );
}

/* -------------------------------------------------------------------------- */
/* Live readings                                                              */
/* -------------------------------------------------------------------------- */

/**
 * The four worn armour slots, each a swatch over its durability.
 *
 * This is `ArmorList`'s own structure, reduced to the two of its three parts that survive at
 * 145px: the swatch (§3's cell, which is what `v-armorlist__icon` is) and the bar (the same
 * two fill steps the meter uses). It was a 5 x 4 pixel-art arch per slot, which at 7px cells
 * came out as four blocky grey blobs.
 *
 * **And it is live.** The old art counted how many slots were occupied and nothing else, so a
 * player one hit from losing a chestplate saw exactly what a player in fresh diamond saw.
 *
 * `held` is a fifth row on the HUD and is left off: four columns is what 123px holds, and
 * armour is what the mod is named for. Always four, even with nothing equipped — a row that
 * changed length as gear broke would make the art jump inside a grid that never moves.
 * Monochrome: `armor_status.warn_below` turns the HUD's bar amber over the game world, and §1
 * keeps that off the menu's own surface.
 */
export function ArmourThumb(): React.ReactElement {
  const armor = useVoidStore((s) => s.armor);
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

/** The frame's own fixture effects, used when nothing is applied in game. */
const FALLBACK_FX: ReadonlyArray<readonly [string, string]> = [
  ['Speed II', '1:24'],
  ['Strength', '0:42'],
];

/**
 * Two effect rows, live where there are effects.
 *
 * `PotionList` puts a swatch before the name and so does this — one §3 cell, not the 3 x 3
 * checkerboard bitmap it was, which at 4px read as grit. Monochrome: the effect's own colour
 * is the HUD's, over the game world (§1).
 */
export function PotionsThumb(): React.ReactElement {
  const fx = useVoidStore((s) => s.fx);
  const rows: ReadonlyArray<readonly [string, string]> =
    fx.length > 0
      ? fx.slice(0, 2).map((effect) => {
          const seconds = Math.max(0, Math.round((effect.duration_ms ?? 0) / 1000));
          const time = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
          return [potionMeta(effect).label, time] as const;
        })
      : FALLBACK_FX;
  return (
    <span className="tart tart--rows">
      {rows.map(([label, time]) => (
        <span className="tart__line" key={label}>
          <span className="tart__pip" />
          <span className="tart__lname">{label}</span>
          <span className="tart__ltime tnum">{time}</span>
        </span>
      ))}
    </span>
  );
}
