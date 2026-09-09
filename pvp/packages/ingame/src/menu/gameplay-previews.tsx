/**
 * Previews for the eight mods that draw into the world, not onto the page.
 *
 * ## Why these are different from the other nine
 *
 * `LIVE_WIDGETS` (`ModSettingsScreen.tsx`) points most of the registry at **the component
 * `HudLayer` already places**, which is the strongest possible guarantee that the preview and
 * the HUD agree: they are the same code. Eight mods cannot be done that way, because there is
 * no HTML of them anywhere. Fullbright changes `mc.options.gamma`. Zoom and the FOV changer
 * change the camera FOV. Hitboxes pushes GL lines from an entity render pass. Overlay deletes
 * render passes the game was going to run. Old animations changes the shape of a first-person
 * transform. Toggle sprint and Toggle sneak change what a key does and draw nothing at all.
 *
 * So these are **diagrams**, and the file says so rather than pretending otherwise. What is
 * shared with the game is not the drawing but the *numbers*: the same `gamma`, the same
 * `fov_divisor` through the same trigonometry the camera uses, the same `line_width` and
 * `color` the GL pass fills with. A diagram driven by the real value is honest; a picture that
 * ignores the value is the thing this whole pass exists to remove.
 *
 * ## The rules they keep
 *
 * - **Monochrome unless the mod owns the ink.** A diagram sits on the menu's own surface, and
 *   `design/quiet-cell-system.md` §1's accent rule is absolute there. Hitboxes is the one
 *   exception and it is not an exception at all: `hitboxes.color` is the colour of a mark drawn
 *   over the game world, so the diagram draws the line the player will actually see.
 * - **The §3 cell is the primitive.** Two of these are ramps and one is a timeline; all three
 *   are rows of squares with a 30% radius, which is what everything textural in this client is
 *   made of.
 * - **Nothing animates.** A repaint is caused by a setting changing and by nothing else — §4's
 *   budget is 0 paints/s on an idle open menu, and a timeline that ran would spend it.
 *
 * ## One drawing, two densities
 *
 * These are also the **grid tiles** for the same eight mods, through `dense`. That is not a
 * convenience: the grid used to draw its own 7x7 bitmap of a sun, a square and an arrow with
 * `BRIGHT`, `HITBOX` and `SPRINT` captioned under them, so a player who opened Fullbright went
 * from a blocky glyph to a pair of gamma ramps and had to work out that they were the same mod.
 * Nine of the thirteen tiles show what the mod actually draws; those three showed an idea of it,
 * and the caption was the tell — a picture that needs its own name printed underneath is a
 * picture that is not working.
 *
 * `dense` drops the reading, shrinks the cell, and moves each row's label above its cells
 * instead of beside them. Both densities read the same settings through the same hooks, so a
 * tile and a page cannot disagree about what a mod looks like, which is the failure this
 * replaces.
 *
 * **What `dense` may never add**: a caption naming the mod. The tile's name is printed 20px
 * below it by `ModsScreen`, and printing it twice is what the old art did — `BRIGHT` under a
 * picture of brightness is the tell that the picture is not working.
 *
 * That rule was once written as "no text that is not a value", and it was too broad. A row's
 * axis label is neither a caption nor a value: `World` and `Seen` are part of the drawing, the
 * way a chart's axis is part of the chart. Dropping them cost more than it saved — both two-row
 * diagrams collapsed into the same anonymous grid, and a user asked why they looked like that.
 *
 * The general lesson, which is the part worth keeping: **"one drawing, two densities" assumes the
 * drawing degrades gracefully.** It is the right principle — it is what stops a tile and a page
 * drifting apart — but a drawing whose legibility lives in text that `dense` removes does not
 * degrade, it dies. Check the dense form on its own, with no page beside it for context, because
 * that is how a player meets it.
 */

import { type CSSProperties } from 'react';

import { useModSettings } from '@/store/store';
import { SETTING_RANGES } from '@/registry';
import { keybindLabel } from './settings-format';

/* -------------------------------------------------------------------------- */
/* Shared pieces                                                              */
/* -------------------------------------------------------------------------- */

/** A labelled row of cells — the primitive both the ramp and the timeline are built from. */
function CellRow({
  label,
  cells,
  joined = false,
}: {
  /**
   * The row's axis label — part of the drawing, not a caption.
   *
   * It used to be dropped at tile density, because 58px of eyebrow *beside* a 116px row is a
   * third of the tile's width. That was the right instinct about width and the wrong conclusion:
   * these labels are load-bearing. Without them both two-row diagrams degrade to the same
   * anonymous grid — a user asked "why do toggle sprint and full bright look like that", and the
   * answer was that neither could be read at all. Fullbright is the sharper case, because the mod
   * ships off, so its two ramps are identical *by design* and the honest statement that nothing is
   * happening is indistinguishable from a fault.
   *
   * The label now sits **above** the cells at tile density (`.gprev--tile .gprev__row` turns the
   * column), where width is free and the cost is ~11px of height the tile has to spare.
   *
   * A node rather than a string, because a row's axis is not always a word. `SneakPreview` puts
   * the mod's own keycap here: the row is "what this key does over time", and the key is the
   * honest name of that axis — the same sentence `ZoomPreview` makes with `hold C`, said in the
   * place the row already reserves for saying what it is. Anything passed here is still an axis
   * label and still subject to the rule above it; it is not a slot for a caption.
   */
  label?: React.ReactNode;
  /** Alpha per cell, 0-1. */
  cells: readonly number[];
  /**
   * Draw the cells edge to edge as one bar, rather than as separate marks.
   *
   * **The gap is what says whether the thing is continuous**, and that is the whole reason these
   * two diagrams were unreadable even after they got their labels back. A ramp and a timeline are
   * opposite ideas, and both were drawn as a row of identical separated squares — so a user asked
   * why they were "just squares", which was the right question.
   *
   * A tap is an *event* and stays discrete. A sprint is a *state* that runs on, and a brightness
   * ramp is a *scale*; both of those are continuous and are drawn joined. Now the shape carries
   * the meaning and the two mods cannot be confused: toggle sprint is one mark above an unbroken
   * bar, fullbright is two unbroken bars.
   *
   * This stays inside §3 — the cell is still the atom and nothing gradients — it only stops
   * putting air between cells that are describing something with no gaps in it.
   */
  joined?: boolean;
}): React.ReactElement {
  return (
    <div className="gprev__row">
      {label === undefined ? null : <span className="gprev__rowlabel">{label}</span>}
      <span className={joined ? 'gprev__cells gprev__cells--joined' : 'gprev__cells'}>
        {cells.map((alpha, i) => (
          <span
            key={i}
            className="gprev__cell"
            style={{ background: `rgba(237, 238, 239, ${alpha.toFixed(3)})` }}
          />
        ))}
      </span>
    </div>
  );
}

/**
 * Props every diagram takes.
 *
 * One flag rather than a `scale` number, because the two densities are not the same drawing at
 * two sizes: the tile drops the labels and the reading entirely, and a scale factor cannot
 * express "and stop printing the sentence".
 */
export interface DiagramProps {
  /** Tile density — see "One drawing, two densities" above. */
  dense?: boolean;
  /**
   * Extra classes on the root.
   *
   * Also load-bearing for the types: `LIVE_WIDGETS` in `ModSettingsScreen` is a
   * `Record<ModId, ComponentType<HudWidgetProps>>` covering all thirteen mods, and these four
   * are four of its entries. A props type with *no* property in common with `HudWidgetProps`
   * trips TypeScript's weak-type check and the whole table stops compiling; `className` is a
   * property both genuinely have, so the overlap is real rather than a placation.
   */
  className?: string;
}

/**
 * Cells in a row.
 *
 * Twelve reads as a scale on a page. Seven is what the tile holds, and the trade is worth
 * stating: the preview well is `--tile-w - 20`, which is **145px at seven columns** and as
 * little as 123 at eight (`ModsScreen.solveGrid` — `?fake=` reaches eight today), so the row
 * has to fit the narrowest tile the grid can produce rather than the one it happens to be in.
 * Within 123px the choice is more steps or bigger ones, and bigger won when both were rendered:
 * eight 12px cells read as a texture and seven 14px cells read as a scale, which is the whole
 * job. 7 x 14 + 6 x 3 = 116.
 */
function steps(dense: boolean): number {
  return dense ? 7 : 12;
}

/** The one-line reading under a diagram: what the current value actually means. */
function Reading({ children }: { children: React.ReactNode }): React.ReactElement {
  return <p className="gprev__reading">{children}</p>;
}

/** The diagram root's classes. `--tile` is the density modifier; the rest is unchanged. */
function root(dense: boolean, modifier?: string, extra?: string): string {
  return ['gprev', modifier, dense ? 'gprev--tile' : null, extra].filter(Boolean).join(' ');
}

/* -------------------------------------------------------------------------- */
/* Fullbright                                                                 */
/* -------------------------------------------------------------------------- */


/**
 * What `gamma` does to a light level, as the schematic this diagram is.
 *
 * Minecraft's gamma lifts the **floor** of the lightmap: dark areas brighten and lit areas are
 * left alone, which is exactly why the mod is useful and why "brightness" is the wrong word for
 * it. The curve below is that shape and not a colorimetric model of the lightmap — it is
 * monotone in `gamma`, it fixes 1.0 as the identity, and it lifts the dark end far more than the
 * lit end, which is the three things a player needs the picture to be true about.
 */
export function litLevel(base: number, gamma: number): number {
  const range = SETTING_RANGES.gamma!;
  const t = (Math.min(range.max, Math.max(range.min, gamma)) - range.min) / (range.max - range.min);
  return base + (1 - base) * t * 0.88;
}

/**
 * Two ramps: the world's own light, and what this gamma lets you see of it.
 *
 * A single ramp would be a picture of a brightness slider, which says nothing — the setting is
 * only legible as a *difference*. At gamma 1 the two rows are identical, which is the honest
 * statement that the mod is doing nothing; by 15 the dark end of the lower row has come up to
 * meet the light end and the row is nearly flat, which is what fullbright looks like.
 */
export function FullbrightPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('fullbright');
  const gamma = Number(settings.gamma ?? 10);
  const n = steps(dense);
  const world = Array.from({ length: n }, (_, i) => 0.04 + (i / (n - 1)) * 0.9);
  const seen = world.map((base) => litLevel(base, gamma));
  return (
    <div className={root(dense, undefined, className)}>
      <CellRow label="World" cells={world} joined />
      <CellRow label="Seen" cells={seen} joined />
      {dense ? null : (
      <Reading>
        {gamma <= 1
          ? 'At 1.0 the two rows match — the mod is on and changing nothing.'
          : `The darkest step reads ${Math.round(seen[0]! * 100)}% instead of ${Math.round(
              world[0]! * 100,
            )}%.`}
      </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Zoom                                                                       */
/* -------------------------------------------------------------------------- */

/** Vanilla's default field of view, in degrees. The divisor divides this. */
const BASE_FOV = 70;

/**
 * The fraction of the screen's width the zoomed view keeps.
 *
 * `fov_divisor` divides the **angle**, and what you see is the tangent of half of it, so the
 * visible fraction is `tan(fov / 2d) / tan(fov / 2)` rather than `1 / d`. The two are close at
 * small divisors and diverge at large ones — at 10x the naive answer is 10% and the real one is
 * 8.4% — and using the real one is free.
 */
export function zoomFraction(divisor: number): number {
  const half = (BASE_FOV / 2) * (Math.PI / 180);
  const d = Math.max(1, divisor);
  return Math.tan(half / d) / Math.tan(half);
}

/**
 * The field of view, as two nested frames.
 *
 * The outer frame is what you see now; the inner one is what fills the screen when the key is
 * held. That is the whole of what this setting does, and it is a shape rather than a number —
 * the number is printed too, because "4.0x" is what a player says to another player.
 */
export function ZoomPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('zoom');
  const range = SETTING_RANGES.fov_divisor!;
  const divisor = Number(settings.fov_divisor ?? 4);
  const fraction = zoomFraction(divisor);
  const smooth = settings.smooth !== false;
  const frame = (f: number): CSSProperties => ({
    width: `${(f * 100).toFixed(2)}%`,
    height: `${(f * 100).toFixed(2)}%`,
  });
  // `smooth` eases the view in rather than snapping to it, and a still frame cannot animate —
  // but it can draw the sizes the view passes *through*. Three ghost frames on the way in when
  // it is on, nothing between the two when it is off, which is what a snap looks like. The same
  // ghost vocabulary the crosshair's `dynamic` uses: faint means "a state this reaches".
  // The ghosts are 1px outlines at .3 alpha. On a 440px frame they are the three sizes the view
  // eases through; on a 118px tile they are four concentric hairlines inside 68px of height, and
  // rendered, that is noise rather than motion — it read as a moire, and it was what made the
  // divisor illegible. Page only.
  const ghosts = smooth && !dense ? [0.72, 0.48, 0.3].map((t) => 1 - (1 - fraction) * (1 - t)) : [];
  return (
    <div className={root(dense, 'gprev--zoom', className)}>
      <div className="gprev__fov">
        {ghosts.map((f, i) => (
          <span key={i} className="gprev__fovstep" style={frame(f)} />
        ))}
        <span className="gprev__fovinner" style={frame(fraction)} />
        {/* The key is part of the sentence this diagram is making — you hold *this* to get
            *that*. It is printed in the page's meta line too, but there it is a fact about the
            mod; here it is the first half of the interaction. */}
        {/* The grid draws the mod's keybind itself, as `.modcell__kbd` in the tile's top-left
            corner — so at tile density this cap would be the second one on the same 145px
            square, saying the same thing. Page only. */}
        {dense ? null : (
          <span className="gprev__fovkey">
            <span className="gprev__kbd">{keybindLabel(settings.key ?? null)}</span>
            <span className="gprev__fovhold">hold</span>
          </span>
        )}
        {/* Inside the frame on the page, under it on the tile. Not a style preference: the label
            is absolutely positioned bottom-right, and at `fov_divisor` 1.0 the inner frame is
            100% of the box — so on a 118px tile the number would sit on top of the very edge it
            is describing. Under the frame it is legible at every divisor. */}
        {dense ? null : <span className="gprev__fovlabel tnum">{divisor.toFixed(1)}×</span>}
      </div>
      {dense ? <span className="gprev__fovunder tnum">{divisor.toFixed(1)}×</span> : null}
      {dense ? null : (
        <Reading>
          {`${BASE_FOV}° becomes ${(BASE_FOV / Math.max(1, divisor)).toFixed(1)}° — ` +
            `${Math.round(fraction * 100)}% of the width, filling the screen.`}
          {divisor <= range.min ? ' Barely a zoom at all.' : ''}
          {smooth ? ' It eases in through the steps behind it.' : ' It snaps straight there.'}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Hitboxes                                                                   */
/* -------------------------------------------------------------------------- */

/**
 * px per `line_width` unit, per density. On the page 0.5 comes out at 2px and 5 at 20, which is
 * what 5 looks like; the tile's box is 53% of the page's, so its stroke is too — a diagram whose
 * subject is a stroke width has to keep the stroke in proportion to the shape it is on, or the
 * tile shows a thicker line than the page for the same setting.
 */
const HITBOX_UNIT = { page: 4, tile: 2.1 };

/**
 * How far off the second figure in the Hitboxes diagram is standing, in blocks.
 *
 * Half the schema's maximum, so both ends of the slider land on opposite sides of it: at 64 the
 * far figure is drawn and at 4 it is not, and the control moves the picture rather than only the
 * number under it. Not a game distance — nothing measures it — but a stated one, so the caption
 * and the drawing cannot disagree about what the second figure means.
 */
const FAR_BLOCKS = 32;

/**
 * One entity's box, head-on, at the mod's own line width and colour.
 *
 * Head-on rather than in perspective on purpose. The real thing is a 3D wireframe pushed by
 * `HitboxGeometry`, and a projection of it in HTML would need rotated elements and would still
 * be a projection — a lie with extra steps. Face-on is a true view of the same box (it is the
 * view you get looking straight at someone, which in a PvP match is most of the time), and the
 * two settings that matter here are the stroke and the ink, both of which it shows exactly.
 *
 * The proportions are a player's: 0.6 wide by 1.8 tall, and the eye line leaves at 1.62.
 */
export function HitboxPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('hitboxes');
  const width = Number(settings.line_width ?? 2) * (dense ? HITBOX_UNIT.tile : HITBOX_UNIT.page);
  // A world mark, so it takes the mod's own ink — `design/quiet-cell-system.md` §1, "where the
  // accent rule stops". Everything else on this diagram stays monochrome.
  //
  // **The tile does not**, and that is not an inconsistency. §1's other half — "no colour on the
  // menu's own surface, the mod tiles included" — is about the grid specifically, where thirteen
  // tiles each carrying their own configured ink is the failure the rule exists to stop. The page
  // is where the colour is *chosen*, so the page is where it is shown; the tile is chrome and
  // stays monochrome, exactly like the crosshair tile, which does not draw `crosshair.color`
  // either.
  const color =
    !dense && typeof settings.color === 'string' ? settings.color : 'var(--text-primary)';
  // The ray gets its own ink, on the same terms as the box's: chosen on the page, so shown on
  // the page, and monochrome on the tile where §1 keeps the grid quiet.
  const eyeColor =
    !dense && typeof settings.eye_line_color === 'string' ? settings.eye_line_color : color;
  const eye = settings.show_eye_line === true;
  // `max_distance` is about *which* entities get a box, and one figure cannot show a cutoff. So
  // the diagram has two: the near one, always drawn, and a second standing at FAR_BLOCKS which
  // is drawn only while the limit reaches it. Dropping out is the whole content of the setting,
  // and it is the same thing that happens in game — which is why this is a second figure rather
  // than a sentence about a number.
  const limit = Number(settings.max_distance ?? 64);
  const far = limit >= FAR_BLOCKS;
  return (
    <div className={root(dense, 'gprev--hitbox', className)}>
      <div className="gprev__box" style={{ borderWidth: `${width}px`, borderColor: color }}>
        {eye ? (
          <span
            className="gprev__eye"
            style={{ height: `${Math.max(1, width)}px`, background: eyeColor }}
          />
        ) : null}
      </div>
      {far ? (
        <div
          className="gprev__box gprev__box--far"
          style={{ borderWidth: `${Math.max(1, width * 0.5)}px`, borderColor: color }}
        />
      ) : null}
      {dense ? null : (
        <Reading>
          {`A player's box is 0.6 by 1.8 blocks, drawn at ${Number(
            settings.line_width ?? 2,
          )} px.${eye ? ' The eye line leaves at 1.62, where they are looking.' : ''}` +
            ` Boxes stop at ${limit} blocks, so the second figure${far ? ' is drawn' : ' is not'}.`}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Toggle sprint                                                              */
/* -------------------------------------------------------------------------- */


/**
 * The two modes, as a timeline of a key and the state it produces.
 *
 * This mod is the only one in the registry that draws **nothing** — `show_status` was removed
 * rather than faked, because a gameplay mod has no `hud[]` placement and an indicator would
 * have been the one thing on screen a player could not move. So there is no widget to show, and
 * the honest preview is of the *behaviour*: what the key does, and what sprint does because of
 * it.
 *
 * In `toggle` the key is tapped once and the state runs on without it. In `hold` the two tracks
 * are the same track. That difference is the entire mod, and side by side it needs no caption —
 * though it gets one, because a diagram nobody can decode is decoration.
 *
 * There used to be a third row here, drawn when `sneak_too` was on. That setting is gone: sneak
 * is `toggle_sneak` now, its own mod with its own bind, so it draws its own diagram rather than
 * riding along in this one.
 */
export function SprintPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('toggle_sprint');
  const hold = settings.mode === 'hold';
  const on = 0.42;
  const off = 0.07;
  const n = steps(dense);
  // The tap lands one cell in at tile density, so the run still reaches the right edge in eight
  // cells: the whole read is "a tap here, and the state carries on past the end", and a run that
  // stops short of the edge says the opposite.
  const tap = dense ? 1 : 2;
  const release = dense ? 4 : 7;

  const key = Array.from({ length: n }, (_, i) =>
    hold ? (i >= tap && i <= release ? on : off) : i === tap ? on : off,
  );
  const state = Array.from({ length: n }, (_, i) =>
    hold ? (i >= tap && i <= release ? on : off) : i >= tap ? on : off,
  );

  return (
    <div className={root(dense, undefined, className)}>
      <CellRow label="Key" cells={key} />
      <CellRow label="Sprint" cells={state} joined />
      {dense ? null : (
        <Reading>
          {hold
            ? 'Hold: sprint lasts exactly as long as the key is down.'
            : 'Toggle: one tap and sprint stays on until you tap again.'}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* FOV changer                                                                */
/* -------------------------------------------------------------------------- */

/**
 * What vanilla multiplies the field of view by while you are sprinting.
 *
 * The game's own number, not a chosen one. `EntityPlayerSP#getFOVModifier` reads
 * `f *= (movementSpeed / walkSpeed + 1) / 2`, and sprinting applies a +30% modifier to
 * `movementSpeed` — so `(1.3 + 1) / 2 = 1.15`. That is the punch this mod exists to stop, and
 * the diagram draws the same 15% the camera would take.
 */
const SPRINT_PUNCH = 1.15;

/**
 * What a fully-drawn bow multiplies the field of view by.
 *
 * The same method's other branch: `f -= f * 0.15` at full draw, so the camera ends at 0.85 of
 * where it was. It pulls the opposite way from the sprint punch, which is exactly why
 * `lock_bow` is a second switch — one is noise and the other is the only cue 1.8 gives for how
 * far the shot is charged.
 */
const BOW_PULL = 0.85;

/**
 * Half of a field of view, in degrees off straight ahead — the angle one ray is drawn at.
 *
 * Capped just inside 90 because the diagram's ground is the half-disc of everything in front of
 * you, and a ray past 88° would leave it. Nothing vanilla's slider can reach comes close; the
 * cap exists for the *punched* angle, which is `fov` x 1.15 and does legitimately run past 110.
 */
function halfAngle(degrees: number): number {
  return Math.min(88, Math.max(1, degrees) / 2);
}

/**
 * One edge of a field of view, drawn from the eye.
 *
 * A 2D `rotate` about the ray's own foot, which is the apex — so the tip lands on the arc at
 * every angle and the drawing is a real fan rather than a fan-shaped approximation. 2D only:
 * `design/ultralight-notes.md` §4 has no 3D transforms at all, and none are wanted here.
 */
function Ray({
  /** Degrees off straight ahead; negative is to the left. */
  deg,
  /**
   * A state the camera *reaches* rather than the one it is held at.
   *
   * The same ghost vocabulary `ZoomPreview` uses for `smooth` and the crosshair uses for
   * `dynamic`: an outline in the same ink, thinner and faint, means "this is where it would
   * go". Here it is the whole content of the two locks — an unlocked camera has a second
   * angle it swings to, and a locked one does not.
   */
  ghost = false,
}: {
  deg: number;
  ghost?: boolean;
}): React.ReactElement {
  return (
    <span
      className={ghost ? 'gprev__ray gprev__ray--ghost' : 'gprev__ray'}
      style={{ transform: `rotate(${deg.toFixed(2)}deg)` }}
    />
  );
}

/**
 * The field of view, as the angle it actually is.
 *
 * An angle is the most directly drawable thing in this set, so this draws the angle: two rays
 * from the eye, on the half-disc of everything in front of you. 30° is a slit of that half and
 * 110° is most of it, which is the reading vanilla's own slider never gives you.
 *
 * **The locks are the mod, and they are drawn as ghost rays.** A held field of view on its own
 * is a picture of the vanilla slider, which says nothing — the setting is only legible as the
 * difference between the angle you asked for and the angle the game keeps taking. So an
 * *unlocked* sprint puts a second, wider pair of rays on the drawing at `fov` x
 * {@link SPRINT_PUNCH}, and an unlocked bow puts a narrower pair at `fov` x {@link BOW_PULL}.
 * Lock either one and its ghost is **gone**, not faded: past the lock there is no second angle,
 * the same way there is no dimmer box past `hitboxes.max_distance`.
 *
 * The ghosts stay at tile density, where `ZoomPreview`'s did not. That is not an inconsistency
 * with it — zoom's ghosts were concentric hairlines inside 68px, which interfere; rays share an
 * apex and *diverge*, so they separate as they travel and are furthest apart exactly where the
 * eye reads them, at the arc.
 */
export function FovPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('fov');
  const range = SETTING_RANGES.fov!;
  const held = Math.min(range.max, Math.max(range.min, Number(settings.fov ?? 90)));
  const lockSprint = settings.lock_sprint !== false;
  const lockBow = settings.lock_bow === true;
  const half = halfAngle(held);
  const sprint = halfAngle(held * SPRINT_PUNCH);
  const bow = halfAngle(held * BOW_PULL);
  return (
    <div className={root(dense, 'gprev--fov', className)}>
      <div className="gprev__fan">
        {lockSprint ? null : (
          <>
            <Ray deg={-sprint} ghost />
            <Ray deg={sprint} ghost />
          </>
        )}
        {lockBow ? null : (
          <>
            <Ray deg={-bow} ghost />
            <Ray deg={bow} ghost />
          </>
        )}
        <Ray deg={-half} />
        <Ray deg={half} />
        <span className="gprev__fanapex" />
      </div>
      {/* Under the fan at both densities. The wedge's interior is where the rays are, so a
          number inside it would sit on the thing it is describing at every angle — the same
          problem `ZoomPreview` solved by moving its label out from under the 1.0x frame. */}
      <span className="gprev__fandeg tnum">{`${Math.round(held)}°`}</span>
      {dense ? null : (
        <Reading>
          {`${Math.round(held)}° held. ` +
            (lockSprint
              ? `Sprinting would open it to ${Math.round(held * SPRINT_PUNCH)}°; it does not.`
              : `Sprinting still opens it to ${Math.round(held * SPRINT_PUNCH)}° — the outer pair.`) +
            (lockBow
              ? ' A drawn bow is held too.'
              : ` A drawn bow still closes it to ${Math.round(held * BOW_PULL)}° — the inner pair.`)}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Toggle sneak                                                               */
/* -------------------------------------------------------------------------- */

/**
 * The latch, as a timeline of a key and the crouch it produces.
 *
 * `SprintPreview` is this mod's sibling and this is deliberately the same two-row instrument —
 * the two mods are the same actuator on a different `KeyBinding`, and drawing them differently
 * would be inventing a distinction the code does not have.
 *
 * **What it does differently, and why.** Sprint's timeline runs off the right-hand edge,
 * because its statement is "and it carries on". Sneak's shows the **second tap**: both rows end
 * at it. That is the sharper picture of a latch, and it is what stops these two tiles being the
 * anonymous grid the file's header describes — side by side, sprint is a mark above a bar that
 * leaves, and sneak is a pair of marks above a bar between them.
 *
 * Read across the two rows, `mode` is the whole mod: the crouch window is *identical* in both
 * values and only the key row changes, from a run held down for its whole length to two taps at
 * its ends. Same sneak, half the key.
 *
 * **The keybind is drawn**, as the key row's own axis label rather than as a caption. A latch
 * on `NONE` is a real and shipped state — the schema defaults there on purpose, because a latch
 * on a key nobody chose leaves a player crouched in a hole — so the unbound diagram draws the
 * same shape at a fraction of the ink. Nothing has happened yet, and the picture says so
 * without saying it in words.
 */
export function SneakPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('toggle_sneak');
  const hold = settings.mode === 'hold';
  const keybind = settings.keybind ?? null;
  const armed = typeof keybind === 'string' && keybind !== '' && keybind !== 'NONE';
  // Both rows drop to a whisper while the mod has no key. The shape stays — it is still the
  // truth about what `mode` would do — but the contrast says the thing is not armed.
  const on = armed ? 0.42 : 0.15;
  const off = armed ? 0.07 : 0.04;
  const n = steps(dense);
  // The press lands one cell in and the release two from the end, so both marks are inside the
  // row at either density: the read is "here, and again here", and a tap on the very edge reads
  // as a row that was cut off rather than as an event.
  const press = dense ? 1 : 2;
  const lift = dense ? 5 : 9;

  const key = Array.from({ length: n }, (_, i) =>
    hold ? (i >= press && i <= lift ? on : off) : i === press || i === lift ? on : off,
  );
  const crouch = Array.from({ length: n }, (_, i) => (i >= press && i <= lift ? on : off));

  return (
    <div className={root(dense, undefined, className)}>
      <CellRow
        label={
          // The tile drops the cap for the reason `ZoomPreview` does: `ModsScreen` already
          // chips the mod's keybind into the tile's own top-left corner, and two caps on one
          // 145px square saying the same thing is the caption problem in miniature.
          dense ? (
            'Key'
          ) : (
            <span className="gprev__keylabel">
              <span className="gprev__kbd">{keybindLabel(keybind)}</span>
            </span>
          )
        }
        cells={key}
        joined={hold}
      />
      <CellRow label="Sneak" cells={crouch} joined />
      {dense ? null : (
        <Reading>
          {(hold
            ? 'Hold: the crouch lasts exactly as long as the key is down — vanilla’s behaviour, on a key you picked.'
            : 'Toggle: one tap crouches, the next stands. Same crouch, two presses instead of a held key.') +
            (armed ? '' : ' No key is bound yet, so nothing latches.')}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Overlay                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * The flame tongues, as a fraction of the scene's height.
 *
 * Eleven of them, the tallest reaching past the middle of the view. That size is the drawing
 * agreeing with the roster: §3.3 #1 says "fire overlay alone decides fights", so the fire has
 * to be the biggest thing this picture ever loses — a polite little flame at the bottom edge
 * would be a picture of a different mod.
 *
 * Eleven rather than seven because the tongues share the width: at seven they came out 41px
 * across on the page, which reads as a row of columns rather than as fire.
 */
const FLAMES: readonly number[] = [34, 52, 41, 66, 47, 74, 51, 62, 38, 56, 36];

/**
 * The player's own view, with things taken out of it.
 *
 * Five independent suppressions, and the obvious drawing — five little icons in a row, one per
 * switch — is a legend rather than a picture: it would say *that* there are five settings
 * without saying what any of them costs you. The honest subject is the thing they all act on,
 * which is one framed first-person view. So there is one scene, every setting owns one element
 * of it, and turning a suppression off puts its element back on your screen.
 *
 * That inversion is worth stating because it is what makes the diagram read. Four of the five
 * ship **on**, so the factory picture is the clean view and the switches *add* clutter — which
 * is exactly the trade the mod is offering, seen from the player's side.
 *
 * - `hide_fire` — the flames, and they are the biggest mark here by a long way (see
 *   {@link FLAMES}).
 * - `hide_pumpkin` — the carved mask, drawn as the thick inset frame it actually is: it eats
 *   the view from every edge at once, which is the whole reason wearing one is a trade.
 * - `hide_stuck_arrows` — shafts standing out of the bottom of the frame, where the ones in
 *   your own model sit.
 * - `hide_own_armor` — the plate on the held hand. Deliberately the smallest change on the
 *   drawing, because the schema is explicit that it is the one switch here that changes nothing
 *   about a first-person fight.
 * - `view_bobbing` — ghost positions rather than motion, the trick `zoom.smooth` was rescued
 *   by. `vanilla` bobs the camera and the hand, so both the horizon and the hand carry ghosts;
 *   `minimal` holds the camera still and keeps the hand moving, so only the hand does; `off`
 *   has no ghosts anywhere. Three values, three visibly different drawings, and nothing
 *   animates — §4's idle-menu budget is 0 paints/s.
 */
export function OverlayPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('overlay');
  // Every boolean here is a *hide*, so the drawing asks the opposite question: what is still on
  // screen. Written as `=== false` / `!== false` rather than `!x` so a missing key falls back to
  // the registry default rather than to "drawn".
  const fire = settings.hide_fire === false;
  const armour = settings.hide_own_armor === false;
  const arrows = settings.hide_stuck_arrows === false;
  const pumpkin = settings.hide_pumpkin === false;
  const bobbing = typeof settings.view_bobbing === 'string' ? settings.view_bobbing : 'vanilla';
  const cameraBobs = bobbing === 'vanilla';
  const handBobs = bobbing !== 'off';
  return (
    <div className={root(dense, 'gprev--overlay', className)}>
      <div className="gprev__scene">
        {/* The world: one horizon, and the camera's ghost positions above and below it when the
            camera is what bobs. A tilted copy either side is what a bob does to a horizon. */}
        {cameraBobs ? (
          <>
            <span className="gprev__horizon gprev__horizon--up" />
            <span className="gprev__horizon gprev__horizon--down" />
          </>
        ) : null}
        <span className="gprev__horizon" />
        {/* Dead centre, so the frame reads as a first-person view rather than as a landscape.
            Not the crosshair mod's shape and not its colour — it is the scene's centre mark. */}
        <span className="gprev__reticle" />
        {/* The held hand, bottom right where 1.8 puts it. Its ghosts are the sweep the item
            makes, and they survive `minimal` because that value is precisely "the hand keeps
            moving and the camera stops". */}
        {handBobs ? (
          <>
            <span className="gprev__hand gprev__hand--lead" />
            <span className="gprev__hand gprev__hand--trail" />
          </>
        ) : null}
        <span className="gprev__hand">{armour ? <span className="gprev__plate" /> : null}</span>
        {arrows ? (
          <>
            <span className="gprev__arrow gprev__arrow--a" />
            <span className="gprev__arrow gprev__arrow--b" />
            <span className="gprev__arrow gprev__arrow--c" />
          </>
        ) : null}
        {fire ? (
          <span className="gprev__flames">
            {FLAMES.map((height, i) => (
              <span key={i} className="gprev__flame" style={{ height: `${height}%` }} />
            ))}
          </span>
        ) : null}
        {/* Last, and over everything: the mask is on your face, not in the world. */}
        {pumpkin ? <span className="gprev__mask" /> : null}
      </div>
      {dense ? null : (
        <Reading>
          {(() => {
            const left = [
              fire ? 'the fire' : null,
              pumpkin ? 'the pumpkin mask' : null,
              arrows ? 'stuck arrows' : null,
              armour ? 'your own armour' : null,
            ].filter((x): x is string => x !== null);
            const bob =
              bobbing === 'off'
                ? 'Nothing bobs, hand included.'
                : bobbing === 'minimal'
                  ? 'The hand still bobs; the camera is held still.'
                  : 'The camera and the hand both bob.';
            return left.length === 0
              ? `Nothing left between you and the fight. ${bob}`
              : `Still on your screen: ${left.join(', ')}. ${bob}`;
          })()}
        </Reading>
      )}
    </div>
  );
}

