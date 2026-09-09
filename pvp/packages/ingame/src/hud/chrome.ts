/**
 * The shared HUD chrome block, resolved into what the DOM needs.
 *
 * ## The control that is not here
 *
 * `text_shadow` was in this block for exactly one commit, and what removed it is worth keeping
 * written down. `design/ultralight-notes.md` §3 rates `text-shadow` **[risky]** — Ultralight's
 * text rasteriser drops it, and on some builds it smears the glyph atlas — so the switch would
 * have rendered *nothing*, in game, for every player, while the settings page showed it moving.
 * That is `design/rendering-invariants.md` §15's silent failure, shipped by the same pass that
 * was written to remove silent failures. It was caught by `scripts/check-ultralight.mjs`, which
 * is why that guard exists.
 *
 * §3 also answers the question the setting was reaching for. HUD legibility is already solved
 * *structurally* — every readout sits on its own chip — and §3 says in as many words that the
 * decision "must be preserved rather than 'improved' later". `background` below is that chip,
 * exposed to the player. It is the answer, and there is not a second one: do not re-add
 * `text-shadow`, and do not reach for `-webkit-text-stroke` (also unreliable here).
 *
 * ## What it is for
 *
 * A HUD readout is drawn over *arbitrary game pixels*. A chip that is legible on a stone wall
 * can be unreadable on snow or against a nether ceiling, and until this block existed the only
 * answers a player had were `scale` and `opacity` — neither of which is about contrast. So
 * every `kind: hud` mod now carries `background`, `border` and `padding`, declared once in
 * `schema/mods/_shared.json#/hud` rather than eight times.
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
  // Defaults the way the schema does: no border.
  const border = settings.border === true;

  return [
    'hud-chrome',
    `hud-chrome--bg-${background}`,
    `hud-chrome--pad-${padding}`,
    border ? 'hud-chrome--border' : null,
  ]
    .filter(Boolean)
    .join(' ');
}
