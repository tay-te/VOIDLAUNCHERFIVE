/**
 * The shared HUD chrome block, resolved into what the DOM needs.
 *
 * ## What it is for
 *
 * A HUD readout is drawn over *arbitrary game pixels*. A chip that is legible on a stone wall
 * can be unreadable on snow or against a nether ceiling, and until this block existed the only
 * answers a player had were `scale` and `opacity` — neither of which is about contrast. So
 * every `kind: hud` mod now carries `background`, `border`, `text_shadow` and `padding`,
 * declared once in `schema/mods/_shared.json#/hud` rather than nine times.
 *
 * ## Why none of it is a colour
 *
 * Lunar gives essentially every HUD mod a background colour, a border colour and per-element
 * text colours. `design/quiet-cell-system.md` §1 names exactly that as the far side of its
 * line — "a chip background, border or label colour per mod" — because colour in this system
 * encodes a value or a state and is **never a preference**. §1's own list of what customisation
 * legitimately is ("position, anchor and offset, scale, opacity, density, what is shown,
 * format") is the shape of this block instead: the player chooses whether a chip has a ground,
 * never what colour that ground is, and `padding` is `density` under its own name.
 *
 * ## Why it is applied by the slot, not by the widget
 *
 * The same reason `scale` and `opacity` are. On the HUD those two are applied by `HudSlot`
 * *around* the widget, so a widget never has to know it is being scaled; doing chrome the same
 * way means one implementation instead of nine, and means a new HUD mod gets the whole block by
 * existing rather than by remembering to draw it.
 *
 * It also means the mod page's preview gets it for free, because `PreviewZoom` applies exactly
 * this in exactly the same place — which is what makes the preview the same drawing rather than
 * a similar one.
 */

import type { SettingValue } from '@/store/store';

const BACKGROUNDS = new Set(['none', 'subtle', 'solid']);
const PADDINGS = new Set(['tight', 'normal', 'roomy']);

/**
 * Resolve one mod's chrome settings into a class list for its slot box.
 *
 * A string rather than a style object: every one of the four is a *step*, not a length, so the
 * value each resolves to belongs in the stylesheet beside the rest of the design's tokens.
 *
 * Every value is validated against the schema's own enum rather than interpolated blind: these
 * become class names, and an unrecognised value would produce a class that matches no rule —
 * which renders as "the setting did nothing", the exact silent failure
 * `design/rendering-invariants.md` §15 is about. An unknown value falls back to the factory
 * default, which is what Java does with it too (`ModRegistry.clamp`).
 */
export function hudChrome(settings: Readonly<Record<string, SettingValue>>): string {
  const background = typeof settings.background === 'string' && BACKGROUNDS.has(settings.background)
    ? settings.background
    : 'none';
  const padding = typeof settings.padding === 'string' && PADDINGS.has(settings.padding)
    ? settings.padding
    : 'normal';
  // Both booleans default the way the schema does: no border, shadow on. `text_shadow` is on by
  // default because that is what Minecraft itself does, and it is the single most load-bearing
  // legibility control here — it is what makes white text survive a snow biome.
  const border = settings.border === true;
  const shadow = settings.text_shadow !== false;

  return [
    'hud-chrome',
    `hud-chrome--bg-${background}`,
    `hud-chrome--pad-${padding}`,
    border ? 'hud-chrome--border' : null,
    shadow ? 'hud-chrome--shadow' : null,
  ]
    .filter(Boolean)
    .join(' ');
}
