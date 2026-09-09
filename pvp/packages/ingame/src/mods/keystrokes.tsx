/**
 * Keystrokes — the WASD / mouse / spacebar tiles.
 *
 * The thumbnail is a **static** keycap block rather than the live widget, and that is the one
 * deliberate exception on the grid: a pressed key is drawn in `--hue`, and §1 keeps the accent
 * off preview art. The page draws the real thing, where a key lighting up as you press it is
 * exactly what you came to see.
 */

import { defineMod } from './types';
import { HudKeystrokes } from '@/hud/widgets';

function KeystrokesThumbnail(): React.ReactElement {
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

export default defineMod({
  id: 'keystrokes',
  Thumbnail: KeystrokesThumbnail,
  Preview: HudKeystrokes,
  // Four rows of 40px caps is 190 tall at 1x. 1.5 puts it at ~285 in a ~300 frame, with the
  // mouse row still clear of the corner slots.
  previewZoom: 1.5,
});
