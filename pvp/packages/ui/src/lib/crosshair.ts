/**
 * The crosshair's shape, as arithmetic — the TypeScript twin of
 * `mod/src/main/java/dev/voidpvp/client/render/CrosshairGeometry.java`.
 *
 * ## Why this is a port and not a second design
 *
 * The crosshair is the one HUD mod that is **not** drawn by this page. It has to sit on the
 * exact pixel centre, which a DOM element inside a scaled view cannot promise, so `LiveState`
 * feeds `CrosshairRenderer` and GL fills the rectangles (`design/quiet-cell-system.md` §3, and
 * the renderer's own javadoc). That leaves the settings page with a problem no other mod has:
 * seven settings — `style`, `size`, `thickness`, `gap`, `color`, `outline`, `center_dot` — that
 * the player cannot see the effect of without closing the menu, walking somewhere with a wall in
 * front of them, and looking.
 *
 * A preview therefore has to draw the crosshair itself. The one thing it must not do is draw a
 * *different* crosshair, which is what an eyeballed approximation becomes about a week later:
 * the arms end up one pixel long, `t_shape` keeps its top arm, `gap` measures from the arm
 * instead of from the centre, and nobody notices because the two are never on screen together.
 *
 * So the arithmetic is transcribed rather than reinvented, function for function, and
 * `test/crosshair.test.ts` pins it against the Java file's own cases. The units are the Java
 * ones — pixels relative to the centre, y down — and the caller multiplies by whatever it draws
 * at. {@link CROSSHAIR_UNIT_PREVIEW} is the mod page's multiplier.
 *
 * If `CrosshairGeometry.java` changes, this changes with it. That is the cost of the crosshair
 * being GL, and it is cheaper than the alternative, which is a settings page that lies.
 */

/** What `crosshair.style` can be, per `schema/mods.json`. */
export type CrosshairStyle = 'default' | 'cross' | 'dot' | 'circle' | 't_shape' | 'none';

/** Every style, in the order `mods.json` declares them. */
export const CROSSHAIR_STYLES: readonly CrosshairStyle[] = [
  'default',
  'cross',
  'dot',
  'circle',
  't_shape',
  'none',
];

/**
 * Narrow an unknown stored value to a style.
 *
 * One copy, in the package that owns the enum. A host running a newer registry than this
 * bundle can send a style this build has never heard of, and the honest answer is to draw the
 * default rather than to draw nothing — an absent crosshair is the §15 failure that survives
 * being looked at.
 */
export function asCrosshairStyle(value: unknown): CrosshairStyle {
  return (CROSSHAIR_STYLES as readonly string[]).includes(String(value))
    ? (value as CrosshairStyle)
    : 'cross';
}

/** `[x, y, w, h]` relative to the centre point, y down — `CrosshairGeometry.Rect`. */
export interface CrosshairRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** True when the style is drawn as a ring rather than as rectangles. */
export function isRing(style: string | null | undefined): boolean {
  return style === 'circle';
}

/** True when the vanilla crosshair pass is left alone — the mod draws nothing of its own. */
export function keepsVanilla(style: string | null | undefined): boolean {
  return style === null || style === undefined || style === 'default';
}

/**
 * The vanilla crosshair, in this module's units.
 *
 * Not part of the Java geometry — Java's answer for `default` is "do not draw, the game already
 * did". A preview cannot say that: an empty box on the mod's *default* setting reads as a broken
 * mod, which is the failure mode `design/rendering-invariants.md` §15 ends on ("an absence is the
 * one failure mode that survives being looked at"). These are vanilla 1.8.9's own proportions —
 * a 9x9 plus, one pixel thick, no gap — so the preview shows what is actually on screen.
 */
export const VANILLA: { size: number; thickness: number; gap: number } = {
  size: 4,
  thickness: 1,
  gap: 0,
};

/**
 * The rectangles to fill.
 *
 * Transcribed from `CrosshairGeometry.rects`. `spread` is the extra gap the `dynamic` setting
 * adds; pass {@link dynamicSpread}'s answer, or 0.
 */
export function crosshairRects(
  style: string | null | undefined,
  size: number,
  thickness: number,
  gap: number,
  spread = 0,
  centerDot = false,
): CrosshairRect[] {
  const out: CrosshairRect[] = [];
  const t = Math.max(1, thickness);
  const half = t / 2;
  const g = Math.max(0, gap) + Math.max(0, spread);
  const s = Math.max(1, size);

  // The centre dot rides on top of every drawn style, including the ring — it is the one thing
  // the ring has room for, and a ring with no centre is the hardest crosshair in the game to
  // aim with. `none` is still nothing: off is off.
  if (style === 'none') return out;
  if (centerDot && style !== null && style !== undefined && style !== 'default') {
    out.push({ x: -half, y: -half, w: t, h: t });
  }

  if (style === null || style === undefined || style === 'default' || style === 'circle') {
    return out;
  }
  if (style === 'dot') {
    // A `dot` style with `center_dot` on would otherwise fill the same rect twice.
    if (!centerDot) out.push({ x: -half, y: -half, w: t, h: t });
    return out;
  }
  // cross and t_shape share the horizontal bar and the lower arm.
  out.push({ x: -g - s, y: -half, w: s, h: t });
  out.push({ x: g, y: -half, w: s, h: t });
  out.push({ x: -half, y: g, w: t, h: s });
  if (style !== 't_shape') {
    out.push({ x: -half, y: -g - s, w: t, h: s });
  }
  return out;
}

/**
 * Extra gap the `dynamic` setting adds while the player is sprinting —
 * `CrosshairGeometry.dynamicSpread`.
 */
export function dynamicSpread(dynamic: boolean, sprinting: boolean): number {
  return dynamic && sprinting ? 2 : 0;
}

/**
 * How many px the preview draws one crosshair unit at.
 *
 * The crosshair's whole range is 1-20 px of arm, which at 1:1 is a mark 42 px across in a frame
 * ~490 x 620 — legible in the game, where it sits under the player's eye and there is nothing
 * else to compare it to, and lost in a panel they are configuring it in.
 *
 * Five is set by the **widest legal crosshair**, not by the default one. `size` 20, `gap` 10 and
 * `thickness` 5 reach 35 units, so the mark is 350 px across and the plate around it 418 — which
 * fits the narrowest frame §8 gives this page (the `grouped` structure, seven properties). Six
 * would put the widest at 488 and clip it, and a preview that clips at the top of its own
 * sliders is a preview that stops answering the question exactly where the question gets
 * interesting.
 *
 * A multiplier and not `transform: scale()`, and not even `zoom`: every length here is a number
 * this module computes, so the enlargement is exact arithmetic on the geometry rather than a
 * transform on the result. `design/rendering-invariants.md` §10a is about scaled *text*, and a
 * crosshair has none — but a multiplied length is the right answer regardless, because a
 * transform would also scale the 1px outline into a 5px one, and the outline is a setting.
 */
export const CROSSHAIR_UNIT_PREVIEW = 5;
