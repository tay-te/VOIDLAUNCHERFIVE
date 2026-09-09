/**
 * FPS display — frames per second, and the 1% low beside it.
 *
 * The tile shows the real figure off the store, so it is the number the HUD would be drawing.
 * `142` is the frames' own fixture and stands in before the first `tick`; a preview with
 * `0 fps` on it is not a preview.
 */

import { defineMod } from './types';
import { Readout } from './art';
import { HudFps } from '@/hud/widgets';
import { useVoidStore } from '@/store/store';

function FpsThumbnail(): React.ReactElement {
  const fps = useVoidStore((s) => s.fps);
  return <Readout value={String(fps || 142)} unit="FPS" />;
}

export default defineMod({
  id: 'fps',
  Thumbnail: FpsThumbnail,
  Preview: HudFps,
  // A HUD chip is ~24px tall and read at arm's length; this page gives it 600px of height and
  // the player's full attention, and at 2.2 it still read as a label on a form rather than as
  // the object under discussion. The four chip mods share the number on purpose — they are the
  // same object, and a set of chips at four different magnifications would say they were not.
  previewZoom: 3,
});
