/**
 * Chat — timestamps, stacked repeats, copy on right-click, the band and the history.
 *
 * A gameplay mod: it changes vanilla's own chat (`GuiNewChat`) rather than placing a widget, so
 * there is no HTML of it to reuse and the preview is a diagram from `menu/gameplay-previews.tsx`,
 * fed by the mod's own settings. Every one of the five moves the drawing — see `ChatPreview`.
 *
 * The thumbnail is **the same component at tile density**, through `dense`, for the reason every
 * world mod's is: a tile and a page showing different pictures of one mod is drift by
 * construction.
 */

import { defineMod } from './types';
import { ChatPreview } from '@/menu/gameplay-previews';

function ChatPreviewThumbnail(): React.ReactElement {
  return <ChatPreview dense className="tart" />;
}

export default defineMod({
  id: 'chat',
  Thumbnail: ChatPreviewThumbnail,
  Preview: ChatPreview,
});
