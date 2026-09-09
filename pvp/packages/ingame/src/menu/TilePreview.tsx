/**
 * The data preview inside a mod tile.
 *
 * The grid frames (`289:1611`, `289:3024`) do not put an icon on a tile — they put a **small
 * picture of what the mod draws**: `142 / FPS`, the W-ASD-LMB-RMB keycap cluster, `6.2 / CPS`,
 * four armour slots, the crosshair's real shape, two potion rows, `2.0×`, `X / Y / Z`. A tile
 * is a sample of the HUD, not a launcher row with a symbol on it, and that is the whole reason
 * the grid reads as a mod list for *this* game rather than for any application.
 *
 * ## This file used to be the art. Now it only routes to it.
 *
 * It was a thirteen-arm `switch`, and the arms were the problem: a mod added to `mods.json`
 * without an arm did not fail to build — it fell through to a `default` that printed the mod's
 * own id in a box, in game, in front of a player. With five waves of new mods planned
 * (`docs/mod-roster.md` §7) that is a failure waiting to happen nine times in one pull request.
 *
 * Each mod's thumbnail now lives in its own module (`src/mods/<id>.tsx`) and is reached through
 * `MOD_ART`, which is proved exhaustive over `ModId` at compile time. The `default` arm below
 * is no longer a fallback for a mod somebody forgot; the only ids that can miss are the
 * synthetic ones `?fake=` injects at runtime, and they borrow real art on purpose.
 */

import { type ModId } from '@/bridge/protocol';
import { fakeArtSource } from '@/dev/fake-mods';
import { modArt } from '@/mods';

/** Props for {@link TilePreview}. */
export interface TilePreviewProps {
  id: ModId;
  /**
   * Multiplier on every cell edge. 1 is the tile.
   *
   * The type around the art scales in CSS, because font sizes are exactly what CSS is for — but
   * a cell's edge is an inline length and has to come from here.
   */
  scale?: number;
}

/**
 * The thumbnail for one mod.
 *
 * Every registry id resolves, because `mods/index.ts` will not compile if one does not.
 */
export function TilePreview({ id, scale = 1 }: TilePreviewProps): React.ReactElement {
  const art = modArt(id);
  if (art) {
    const { Thumbnail } = art;
    return <Thumbnail scale={scale} />;
  }

  // DEV ONLY: a synthetic mod (`src/dev/fake-mods.ts`) borrows one of the real thumbnails
  // rather than getting art of its own. Drawn by rendering this component again, so the
  // borrowed art keeps its own hooks and its own live data, and no new vocabulary is invented
  // for a fixture.
  const borrowed = fakeArtSource(id);
  if (borrowed !== null) return <TilePreview id={borrowed} scale={scale} />;

  // Unreachable for any id in the registry — `MOD_ART` is exhaustive over `ModId` — and kept
  // only so a fixture with a broken `fakeArtSource` draws its id rather than crashing the grid.
  return (
    <span className="tart tart--stack">
      <span className="tart__unit tart__unit--wide">{String(id)}</span>
    </span>
  );
}
