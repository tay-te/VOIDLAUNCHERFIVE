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
 *
 * ## The half of that which was wrong, and what it cost
 *
 * `background` and `border` are genuinely things the slot draws. **`padding` is not.** Every HUD
 * widget already carries its own inset — `.v-hudchip` reads `--pad-hud-chip`, and the armour and
 * potion panels and the keycap cluster had theirs written into the rule — so padding on the slot
 * was a second, outer inset stacked on top of an inner one the setting could not reach. At the
 * shipped default the outer box was transparent, so the whole control moved
 * an invisible edge: the chip a player was looking at never changed size at any step.
 *
 * It shipped because the gate could not see it. `test/preview.test.tsx` compares the preview's
 * markup, the class on the box changed, and a class that matches a rule which draws nothing is
 * indistinguishable from one that draws. Its own doc comment names this as the way the exemption
 * list erodes; this was the same erosion one level up, in the comparison itself.
 *
 * So the padding classes now set `--pad-hud-chip`, `--pad-hud-panel` and `--gap-hud-keys`
 * (`styles/overlay.css`) — the three variables the widgets actually read their density from —
 * and inherit down into the widget rather than boxing it. `test/hud-chrome.test.ts` asserts the
 * resolved lengths differ from step to step, which is a claim about what is drawn rather than
 * about what is spelled.
 *
 * **And it was all three, not just padding.** `background` and `border` failed the same way from
 * the other side: the slot is *behind* the widget, so a ground drawn here sat under a ground the
 * widget already had, and an edge drawn here sat under it too. Both now set variables the
 * widgets paint from — `--hud-chip-bg` / `--hud-chip-bg-strong` and `--border-hud` — so the
 * whole block reaches the drawing rather than the box around it. What the slot still genuinely
 * owns is `scale` and `opacity`, which are transforms of the widget and not properties of it.
 */

import { SETTING_OPTIONS } from '@/bridge/protocol';
import type { SettingValue } from '@/store/store';

/**
 * The legal values of one shared key, read out of the registry rather than transcribed.
 *
 * These two sets were literals, and the literal is what let `padding` gain two steps in
 * `schema/mods/_shared.json` and lose them again here: an unlisted value falls back to the
 * factory default, so `wide` would have been stored, echoed back, and drawn as `normal` — a
 * setting that moves on the page and not in the game, which is `rendering-invariants.md` §15
 * exactly. Deriving the set makes that unrepresentable instead of tested for.
 *
 * A union across every mod that has the key, because the block is shared and the generated
 * table is keyed `<mod>.<key>`. `fps.padding` alone would work today and would be a lookup
 * pinned to one mod's continued existence.
 */
function sharedEnum(key: string): ReadonlySet<string> {
  const suffix = `.${key}`;
  const values = Object.entries(SETTING_OPTIONS)
    .filter(([id]) => id.endsWith(suffix))
    .flatMap(([, options]) => options);
  return new Set(values);
}

const BACKGROUNDS = sharedEnum('background');
const PADDINGS = sharedEnum('padding');

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
    : 'subtle';
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
