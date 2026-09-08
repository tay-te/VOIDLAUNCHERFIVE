/**
 * Armor status — worn armour and held item, with durability.
 */

import { defineMod } from './types';
import { ArmourThumb } from './art';
import { HudArmorStatus } from '@/hud/widgets';

export default defineMod({
  id: 'armor_status',
  Thumbnail: ArmourThumb,
  Preview: HudArmorStatus,
  // Set by the **horizontal** strip, which is the wide one: five cells whose natural width is
  // ~322 in a frame measured at 492. 1.6 put it at 518, and the widget did what a flex row does
  // when it is 26px too wide — it squeezed the cells until `1159 / 1561` wrapped onto a second
  // line and pushed the last bar out of the row. Measured in the harness, not guessed. 1.3
  // leaves ~25px of slack, which is the margin a slightly narrower panel needs.
  previewZoom: 1.3,
});
