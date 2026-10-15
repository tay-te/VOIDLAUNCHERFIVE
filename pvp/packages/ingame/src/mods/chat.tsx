/** Chat — PLACEHOLDER art, replaced in the depth wave's HUD pass. */

import { defineMod } from './types';
import { Readout } from './art';

function ChatThumbnail(): React.ReactElement {
  return <Readout value="[14:03]" unit="CHAT" />;
}

export default defineMod({
  id: 'chat',
  Thumbnail: ChatThumbnail,
  Preview: ChatThumbnail,
});
