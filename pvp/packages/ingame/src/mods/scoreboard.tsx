/**
 * Scoreboard — the server's sidebar, as a HUD chip you can move, scale or hide.
 *
 * The preview is the identical widget `HudLayer` places. The tile is a miniature of the same
 * board — its title and first lines as text, codes stripped — and **monochrome**, whatever
 * `server_colors` says: colour is server information on the HUD, and the grid is chrome
 * (`mods/types.ts`). It reads the live board when there is one, like the FPS tile reads the real
 * figure, and the sample otherwise.
 */

import { defineMod } from './types';
import { HudScoreboard, useScoreboard } from '@/hud/scoreboard';
import { stripMc } from '@/hud/mc-format';

/** How many lines the 118px tile holds under its title. */
const TILE_LINES = 4;

function ScoreboardThumbnail(): React.ReactElement {
  const board = useScoreboard(true)!;
  // Spacer lines carry nothing at this size, so the tile shows the first lines that say something.
  const lines = board.lines.map((line) => stripMc(line.text).trim()).filter((text) => text !== '');
  return (
    <span className="tart tart--rows tart--board">
      <span className="tart__unit tart__boardtitle">{stripMc(board.title)}</span>
      {lines.slice(0, TILE_LINES).map((text, i) => (
        <span key={i} className="tart__line">
          <span className="tart__lname">{text}</span>
        </span>
      ))}
    </span>
  );
}

export default defineMod({
  id: 'scoreboard',
  Thumbnail: ScoreboardThumbnail,
  Preview: HudScoreboard,
  // Under the chip mods' 3: this is a panel of up to sixteen lines, not a figure, and the page's
  // frame has to hold all of them. 1.8 keeps the Bed Wars board inside it with its scores on.
  previewZoom: 1.8,
});
