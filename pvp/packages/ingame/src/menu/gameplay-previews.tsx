/**
 * Previews for the twelve mods that draw into the world, not onto the page.
 *
 * ## Why these are different from the rest of the registry
 *
 * `LIVE_WIDGETS` (`ModSettingsScreen.tsx`) points most of the registry at **the component
 * `HudLayer` already places**, which is the strongest possible guarantee that the preview and
 * the HUD agree: they are the same code. Twelve mods cannot be done that way, because there is
 * no HTML of them anywhere. Fullbright changes `mc.options.gamma`. Zoom and the FOV changer
 * change the camera FOV. Hitboxes pushes GL lines from an entity render pass. Overlay deletes
 * render passes the game was going to run. Old animations changes the shape of a first-person
 * transform and Old input deletes two `return`s and a cooldown, so between them they change
 * what a click is allowed to do and draw nothing at all. Toggle sprint and Toggle sneak change
 * what a key does and draw nothing at all either.
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
/**
 * A `#RRGGBB[AA]` colour with any alpha byte removed.
 *
 * `mods.json`'s shared `hex_color` accepts both lengths because `crosshair.color` genuinely ships
 * the eight-digit form. `hit_color` does not: its `intensity` is the sole owner of alpha, defined
 * as a fraction of vanilla's own hurt-overlay strength, and that ownership is what keeps the mod
 * `safe` rather than `grey`. Two owners of one value is the shape `toggle_sneak` was split out of
 * `toggle_sprint` to remove.
 */
export function rgbOnly(color: string): string {
  return /^#[0-9a-fA-F]{8}$/.test(color) ? color.slice(0, 7) : color;
}

function steps(dense: boolean): number {
  return dense ? 7 : 12;
}

/**
 * The one line under a diagram: what the current values *are*.
 *
 * ## It takes facts, not prose, and that is the whole change
 *
 * These were sentences — two and three of them, with subordinate clauses, em-dashes and an
 * argument in each. Read on a page you opened to move one slider, in game, that is not an
 * explanation, it is homework: `damage_tint` printed forty-four words about a vignette that was
 * already drawn above it, and every one of them had to be read to find the two numbers.
 *
 * The diagram is the explanation. This line is the *reading* — the same job the mod page's own
 * `.preview__meta` does with `Bottom left · 1.0× · 85%`, in the same shape, so the two lines on
 * one page read as one system rather than as a caption and an essay. A fact is a few words: a
 * number with its unit, or a state named. Anything that needs a clause to make sense belongs in
 * the drawing or in the setting's own hint (`menu/settings-format.ts`), not here.
 *
 * `null` drops a part, so a value that has nothing to say says nothing rather than saying
 * "none" — the absence rule of `design/quiet-cell-system.md` §1, which the preview's meta line
 * already follows.
 *
 * `test/preview-copy.test.tsx` holds the line to it: every part short, no part a sentence.
 */
function Reading({ parts }: { parts: ReadonlyArray<string | null> }): React.ReactElement {
  return (
    <p className="gprev__reading">
      {parts.filter((part): part is string => part !== null && part !== '').join('   ·   ')}
    </p>
  );
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
      <Reading
        parts={[
          `Gamma ${gamma.toFixed(1)}`,
          gamma <= 1
            ? 'Nothing lifted'
            : `Darkest step ${Math.round(world[0]! * 100)}% → ${Math.round(seen[0]! * 100)}%`,
        ]}
      />
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
  const divisor = Number(settings.fov_divisor ?? 4);
  const fraction = zoomFraction(divisor);
  const smooth = settings.smooth !== false;
  const sensitivity = Number(settings.sensitivity ?? 1);
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
      {/* `sensitivity` scales how far your look moves for the same mouse travel, and it has a
          drawn form for the same reason `smooth` turned out to: a still frame cannot animate a
          motion but it can draw its *extent*. The row is the mouse step at full sensitivity;
          the lit part is what is left of it while the zoom is engaged. At 1 the row is full,
          which is the honest statement that nothing is being taken away.

          A row of cells rather than a bar because that is what everything textural in this
          client is made of (§3), and separated rather than joined because a sensitivity is a
          *scale* the player picks a step on, not a continuous state the way sprint is. */}
      {dense ? null : (
        <CellRow
          label="Mouse"
          cells={Array.from({ length: steps(false) }, (_, i) =>
            i / steps(false) < sensitivity ? 0.42 : 0.07,
          )}
        />
      )}
      {dense ? null : (
        <Reading
          parts={[
            `${BASE_FOV}° → ${(BASE_FOV / Math.max(1, divisor)).toFixed(1)}°`,
            `${Math.round(fraction * 100)}% of the width`,
            sensitivity >= 1 ? null : `Mouse at ${Math.round(sensitivity * 100)}%`,
            smooth ? 'Eases in' : 'Snaps',
          ]}
        />
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
        <Reading
          parts={[
            `0.6 × 1.8 blocks`,
            `${Number(settings.line_width ?? 2)} px lines`,
            eye ? 'Eye line at 1.62' : null,
            `Out to ${limit} blocks`,
          ]}
        />
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Toggle sprint                                                              */
/* -------------------------------------------------------------------------- */


/**
 * The latch, as a timeline of a key and the state it produces.
 *
 * This mod is the only one in the registry that draws **nothing** — `show_status` was removed
 * rather than faked, because a gameplay mod has no `hud[]` placement and an indicator would
 * have been the one thing on screen a player could not move. So there is no widget to show, and
 * the honest preview is of the *behaviour*: what the key does, and what sprint does because of
 * it. One tap, and the state runs on without the key. That is the whole mod.
 *
 * **It used to draw two modes side by side, and one of them was a lie.** `toggle_sprint.mode`
 * offered `hold` as "restore vanilla hold-to-sprint", and this diagram dutifully drew the two
 * tracks as the same track. They were: `KeyBinding.setKeyPressed` writes the same `pressed`
 * field that both sprint tests in `ClientPlayerEntity.tickMovement` read, so latching it every
 * tick is indistinguishable from a held key — `hold` had no implementation other than writing
 * nothing, which is what turning the mod off already does. The setting is gone, and with it the
 * only branch this drawing had. A diagram that renders a setting faithfully is not evidence the
 * setting means anything.
 *
 * There used to be a third row too, drawn when `sneak_too` was on. That setting is also gone:
 * sneak is `toggle_sneak` now, its own mod with its own bind, and it draws its own diagram —
 * where `mode: hold` *is* real, because that mod latches a key of its own rather than vanilla's.
 *
 * Nothing here reads a setting, and nothing needs to: the mod's one remaining setting is
 * `keybind`, which turns the mod off, and `test/preview.test.tsx` exempts it for the reason it
 * exempts the other three — the game without the mod is not the mod's preview.
 */
export function SprintPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const on = 0.42;
  const off = 0.07;
  const n = steps(dense);
  // The tap lands one cell in at tile density, so the run still reaches the right edge in eight
  // cells: the whole read is "a tap here, and the state carries on past the end", and a run that
  // stops short of the edge says the opposite.
  const tap = dense ? 1 : 2;

  const key = Array.from({ length: n }, (_, i) => (i === tap ? on : off));
  const state = Array.from({ length: n }, (_, i) => (i >= tap ? on : off));

  return (
    <div className={root(dense, undefined, className)}>
      <CellRow label="Key" cells={key} />
      <CellRow label="Sprint" cells={state} joined />
      {dense ? null : <Reading parts={['Tap on', 'Tap off']} />}
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
        <Reading
          parts={[
            `${Math.round(held)}° held`,
            lockSprint ? 'Sprint locked' : `Sprint opens to ${Math.round(held * SPRINT_PUNCH)}°`,
            lockBow ? 'Bow locked' : `Bow closes to ${Math.round(held * BOW_PULL)}°`,
          ]}
        />
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
        <Reading
          parts={[
            hold ? 'Crouches while held' : 'Tap down, tap up',
            armed ? null : 'No key bound',
          ]}
        />
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Scoreboard                                                                 */
/* -------------------------------------------------------------------------- */

/**
 * The screen, with the server's sidebar on it — where it is, how big, and whether at all.
 *
 * ## The one diagram in this file that is a *plan* rather than a schematic
 *
 * Everything else here draws a mechanism: two gamma ramps, a fan of view angles, a click
 * timeline. This mod's whole subject is *position on a screen*, so the honest picture is the
 * screen. It reuses `.gprev__scene` — the frame the overlay mod already draws its first-person
 * view in — because a player looking at two of these pages should not have to learn two ideas of
 * what a screen is.
 *
 * ## The proportions are vanilla's, read out of the method
 *
 * `InGameHud.renderScoreboardObjective` puts the sidebar's left edge at
 * `window.getWidth() - maxWidth - 3` and its top at `window.getHeight() / 2 + rows * fontHeight
 * / 3`, so it hangs from the right edge at half height and grows left and down. The block below
 * is drawn from that same anchor with the same growth direction, which is what makes shrinking
 * it in the preview move it the way shrinking it in game does — the failure this diagram is
 * guarding against is a preview where a scale looks like it slides the sidebar into the middle.
 *
 * The rows are §3 cells at a stated width, not lorem text: a scoreboard's content is the
 * server's and changes every game, and drawing plausible words would be inventing a game mode.
 * What is true of every scoreboard is that it is a stack of lines of ragged length under a
 * heading, which is what this is.
 */
export function ScoreboardPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('scoreboard');
  const hidden = settings.hide === true;
  const scale = Number(settings.sidebar_scale ?? 1);
  const dx = Number(settings.offset_x ?? 0);
  const dy = Number(settings.offset_y ?? 0);
  // The scene is 440x248 on the page and 118x74 on a tile; the offsets are in the game's own
  // scaled pixels, whose screen is nearer 640 wide. One factor, so a nudge of 40 reads as the
  // same fraction of the frame at both densities and in game.
  const unit = dense ? 118 / 640 : 440 / 640;
  // Ragged line lengths, longest first under the heading — the shape every scoreboard has.
  const rows = [0.86, 0.62, 0.94, 0.5, 0.78, 0.68];
  return (
    <div className={root(dense, 'gprev--board', className)}>
      <div className="gprev__scene">
        <span className="gprev__horizon" />
        <span className="gprev__reticle" />
        {/* Absent when hidden, which is the whole of what the setting does — and unlike a HUD
            widget's absence this one is legible, because the frame around it stays. */}
        {hidden ? null : (
          <span
            className="gprev__board"
            style={{
              // Right edge at half height is the anchor, so the box is positioned by its
              // top-right corner and `transform-origin` sits there too — the same point
              // `GlStateManager` is handed in `InGameHudMixin`.
              right: `${-dx * unit}px`,
              top: `${50 + (dy * unit * 100) / (dense ? 74 : 248)}%`,
              transform: `scale(${scale})`,
            }}
          >
            <span className="gprev__boardhead" />
            {rows.map((width, i) => (
              <span key={i} className="gprev__boardrow" style={{ width: `${width * 100}%` }} />
            ))}
          </span>
        )}
      </div>
      {dense ? null : (
        <Reading
          parts={[
            hidden ? 'Hidden' : `${Math.round(scale * 100)}% size`,
            hidden ? null : dx === 0 && dy === 0 ? 'Where vanilla puts it' : `Moved ${dx}, ${dy}`,
          ]}
        />
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
  // What the mod has *not* taken away, for the reading. Hoisted rather than computed in the JSX
  // so the reading stays a flat list of facts — which is the shape `test/preview-copy.test.tsx`
  // reads, and the shape the other eleven are already in.
  const stillShown = [
    fire ? 'fire' : null,
    pumpkin ? 'pumpkin' : null,
    arrows ? 'arrows' : null,
    armour ? 'armour' : null,
  ].filter((item): item is string => item !== null);
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
        <Reading
          parts={[
            stillShown.length === 0 ? 'View clear' : `Still shown: ${stillShown.join(', ')}`,
            bobbing === 'off'
              ? 'Nothing bobs'
              : bobbing === 'minimal'
                ? 'Hand bobs, camera still'
                : 'Camera and hand bob',
          ]}
        />
      )}
    </div>
  );
}


/* -------------------------------------------------------------------------- */
/* Freelook                                                                   */
/* -------------------------------------------------------------------------- */

/**
 * Where the camera stands, per `perspective`, as a bearing in degrees clockwise from the
 * player's own facing — and the bearings it is *also* able to stand at.
 *
 * **This table is the whole anti-freecam argument, expressed as data.** Every value in it is a
 * bearing and none of them is a distance, because the distance is not a setting and must never
 * look like one: `schema/mods/freelook.json`'s `$comment` pins `free` to "an orbit about the
 * same pivot vanilla's third-person camera already uses", and says in as many words that a
 * camera which can leave the body is a freecam, a freecam is scouting, and scouting is on §6.1's
 * "never, under any framing" list. A reader forms their idea of what a setting does from the
 * picture, so the picture may not contain a camera that flies away. Here it cannot: the mark is
 * a dot on a ring of fixed radius, tied to the middle by a spoke, and the only thing any value
 * changes is *where on the ring* it sits.
 *
 * The ghosts are the same vocabulary `FovPreview` uses for the angles the camera would reach.
 * The two vanilla offsets carry one each — standing at the back, the front is the other place
 * F5 can put you, which is the honest statement that these two values are one pair rather than
 * two unrelated pictures. `free` carries five, spread right round the circle, because there
 * every bearing is reachable and a single ghost would understate it to "three positions".
 */
const ORBIT: Record<string, { readonly bearing: number; readonly ghosts: readonly number[] }> = {
  third_back: { bearing: 180, ghosts: [0] },
  third_front: { bearing: 0, ghosts: [180] },
  // Off both axes on purpose. A `free` camera parked at 90° would read as a *third* fixed
  // offset — a left shoulder view — which is the one wrong idea this value can give.
  free: { bearing: 132, ghosts: [36, 84, 192, 240, 300] },
};

/**
 * The camera, on its ring, joined to the player by the spoke it can never leave.
 *
 * A bar rotated about its own foot, exactly as `Ray` is — the foot is the pivot, the bar is the
 * orbit radius, and the mark sits at its far end, so the mark lands on the ring at every bearing
 * and the drawing is a real orbit rather than an orbit-shaped arrangement. 2D `rotate` only
 * (`design/ultralight-notes.md` §4).
 *
 * The spoke is not decoration. Without it the camera is a dot near a circle, which is a picture
 * of a camera that happens to be over there; with it the camera is *attached at a fixed radius*,
 * which is the one thing this diagram has to be unambiguous about.
 */
function Tether({
  /** Degrees clockwise from the player's facing; 0 is directly in front. */
  deg,
  /** A bearing the camera can also stand at, rather than the one it is standing at. */
  ghost = false,
}: {
  deg: number;
  ghost?: boolean;
}): React.ReactElement {
  return (
    <span
      className={ghost ? 'gprev__tether gprev__tether--ghost' : 'gprev__tether'}
      style={{ transform: `rotate(${deg.toFixed(2)}deg)` }}
    >
      <span className="gprev__cam" />
    </span>
  );
}

/**
 * Freelook, as a plan view of one player and one camera — plus the timeline of the key.
 *
 * ## Why a plan view
 *
 * The mod's subject is an *angle between two things*: where your body points, and where the
 * camera points. A first-person frame cannot show that, because in a first-person frame the
 * body is off screen by definition — you would be drawing the second half of the sentence with
 * the first half missing. Seen from above, both halves are on the page at once: the bright arrow
 * is the body, fixed, pointing the way it was already pointing, and the dot is the camera,
 * somewhere else. "Look around without turning" is then a picture rather than a claim.
 *
 * It also makes the dangerous reading unavailable, which is the reason this shape was chosen
 * over the alternatives rather than merely a nice property of it. See {@link ORBIT}.
 *
 * ## What each setting moves
 *
 * - `perspective` — the camera dot's bearing, and how many ghost bearings sit with it
 *   ({@link ORBIT}). Three genuinely different pictures: behind you, in front of you, and off
 *   your axis on a ring that is lit all the way round.
 * - `snap_back` — a **second, faint facing arrow**. On (the default) there is none, because the
 *   body does not move at all: the whole promise of the setting is that freelook is a look and
 *   never a turn, and the honest drawing of "nothing happens to your aim" is nothing on the
 *   page. Off puts a ghost arrow at the camera's own bearing, which is where the body swings to
 *   when you let go. That is `FovPreview`'s lock idiom exactly — past the lock there is no second
 *   angle — read in the other direction, and the quiet picture is the safe default, which is the
 *   right way round for a setting whose off state can rotate your aim mid-duel.
 * - `mode` and `snap_back`'s companion, the two-track timeline. `hold` runs the key row as one
 *   unbroken bar for exactly the length of the look; `toggle` replaces it with two taps at the
 *   same two instants. The look row is **identical in both**, which is the whole content of the
 *   setting: same look, half the key.
 * - `keybind` — the key row's axis label is the mod's own keycap, and the two rows drop to a
 *   whisper while the bind is `NONE`.
 *
 * ## Two things it deliberately does not do
 *
 * **The timeline is the same instrument as `SneakPreview`'s, and that is intended.** Hold versus
 * latch is the same question there and here, and drawing it differently would invent a
 * distinction the mod does not have. What stops the two pages reading as one diagram is that
 * this one is not the whole drawing: the orbit sits above it and carries the mod's identity.
 * The tiles cannot collide either, because at tile density the rows are dropped entirely — the
 * same trade the animation stages make, and for the same reason: a 123px well holds one diagram.
 *
 * **The orbit keeps its full ink while the bind is `NONE`, and only the rows whisper.** The
 * split is real rather than a compromise: the ring is where the camera *can be*, which is true
 * of a mod nobody has bound yet, while the timeline is the mod *running*, which needs a key
 * before it means anything. It also keeps the tile legible, and the tile is the one drawing that
 * has to work at the factory settings, where this mod ships unbound.
 */
export function FreelookPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('freelook');
  // Read into a local before the ternary rather than inlining it. `scripts/check-ultralight.mjs`
  // rejects the *CSS* property `perspective:` (§4, 2D transforms only) with a regex that cannot
  // tell a stylesheet from a source file, and `settings.perspective : 'third_back'` reads as one
  // to it. The rule is right and the spelling is free, so the spelling moves.
  const chosen = settings.perspective;
  const view = typeof chosen === 'string' ? chosen : 'third_back';
  const orbit = ORBIT[view] ?? ORBIT.third_back!;
  const free = view === 'free';
  const snapBack = settings.snap_back !== false;
  const hold = settings.mode !== 'toggle';
  const keybind = settings.keybind ?? null;
  const armed = typeof keybind === 'string' && keybind !== '' && keybind !== 'NONE';
  // `SneakPreview`'s contrast, for `SneakPreview`'s reason: an unbound latch is a real and
  // shipped state, so the rows draw the same shape at a fraction of the ink rather than
  // disappearing or lying about being armed.
  const on = armed ? 0.42 : 0.15;
  const off = armed ? 0.07 : 0.04;
  const n = steps(dense);
  const press = dense ? 1 : 2;
  const lift = dense ? 5 : 9;
  const key = Array.from({ length: n }, (_, i) =>
    hold ? (i >= press && i <= lift ? on : off) : i === press || i === lift ? on : off,
  );
  const look = Array.from({ length: n }, (_, i) => (i >= press && i <= lift ? on : off));
  return (
    <div className={root(dense, 'gprev--freelook', className)}>
      <div className="gprev__orbit">
        {/* Always drawn, at every perspective, because the ring is the invariant: two of the
            three values are points on it and the third is the whole of it. It lights up for
            `free` — the ghost dots say the camera reaches those bearings, and a lit ring says
            it reaches every bearing between them. */}
        <span className={free ? 'gprev__orbitring gprev__orbitring--free' : 'gprev__orbitring'} />
        {orbit.ghosts.map((deg) => (
          <Tether key={deg} deg={deg} ghost />
        ))}
        <Tether deg={orbit.bearing} />
        {/* Where the body ends up. Absent when it does not move, which is the default and is
            the entire point of the default. */}
        {snapBack ? null : (
          <span
            className="gprev__facing gprev__facing--ghost"
            style={{ transform: `rotate(${orbit.bearing.toFixed(2)}deg)` }}
          />
        )}
        {/* The body, pointing where it was already pointing. It never rotates — that is the
            mod's name, drawn. */}
        <span className="gprev__facing" />
        <span className="gprev__pivot" />
      </div>
      {dense ? null : (
        <>
          <CellRow
            label={
              <span className="gprev__keylabel">
                <span className="gprev__kbd">{keybindLabel(keybind)}</span>
              </span>
            }
            cells={key}
            joined={hold}
          />
          <CellRow label="Look" cells={look} joined />
        </>
      )}
      {dense ? null : (
        <Reading
          parts={[
            view === 'free'
              ? 'Orbits your head'
              : view === 'third_front'
                ? 'In front, looking back'
                : 'Over your shoulder',
            hold ? 'Held' : 'Tap on, tap off',
            snapBack ? 'Facing restored' : 'Body follows on release',
            armed ? null : 'No key bound',
          ]}
        />
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Hit colour                                                                 */
/* -------------------------------------------------------------------------- */

/**
 * The alpha this diagram draws a full-strength flash at — the top of `intensity`'s range.
 *
 * **Not the GL constant, and the difference is the point.** In game the hurt pass colours at
 * `RenderLivingBase#setBrightness`'s own alpha, composited over a lit skin texture on arbitrary
 * world pixels; here it is composited over one flat grey silhouette on the menu's own ground.
 * Reproducing the number rather than the *read* would draw a flash far fainter than the one a
 * player sees, which would be faithful to the source and wrong about the subject — the same
 * trade `HITBOX_UNIT` makes when it draws a `line_width` of 5 as 20px rather than as 5.
 *
 * What is faithful is the **ceiling**, and that is the part the schema spends its §11 argument
 * on: `intensity` is a fraction of vanilla's alpha, 1 is exactly what the game already draws,
 * and there is no value above it. So this constant is the drawing's maximum as well as its
 * default — nothing on this diagram can be brighter than a flash the game was going to draw
 * anyway, which is the honest shape of "vanilla, or less".
 */
const VANILLA_FLASH = 0.68;

/**
 * One entity, and the flash on it.
 *
 * A filled silhouette rather than `HitboxPreview`'s outline, because the subject is a *tint on a
 * model* rather than a mark around one. Two layers: the body, which is always there in the
 * menu's own grey, and the flash over it in whatever ink this figure is entitled to. The base
 * layer is why `intensity: 0` still draws something — "recolour nothing" is a picture of an
 * entity with no flash on it, not a picture of no entity.
 */
function Figure({
  /** Standing further off — smaller and dimmer, exactly as `.gprev__box--far` is. */
  far = false,
  /** The flash's ink. `var(--text-primary)` is the game's own flash, uncoloured by this mod. */
  ink,
  /** The flash's alpha: {@link VANILLA_FLASH} scaled by `intensity`. */
  alpha,
}: {
  far?: boolean;
  ink: string;
  alpha: number;
}): React.ReactElement {
  const tint = { background: ink, opacity: alpha.toFixed(3) } as CSSProperties;
  return (
    <span className={far ? 'gprev__fig gprev__fig--far' : 'gprev__fig'}>
      <span className="gprev__fighead">
        <span className="gprev__figtint" style={tint} />
      </span>
      <span className="gprev__figbody">
        <span className="gprev__figtint" style={tint} />
      </span>
    </span>
  );
}

/**
 * The hit flash, as the two entities the setting is actually about.
 *
 * ## Why two figures
 *
 * `own_hits_only` is not a property of a tint, it is a property of *whose fight you are reading*
 * — the schema's own sentence is "two other players hit each other across the arena ... in your
 * peripheral vision, during yours". One figure cannot hold that, and a caption saying it would
 * be the thing `test/preview.test.tsx` exists to refuse. So there are two: the one you are
 * fighting, near, and somebody else's, standing off. Distance is size and air, which is the same
 * answer `HitboxPreview` gives for the same reason — there is no perspective in a face-on
 * diagram to put it in.
 *
 * Both figures flash in **both** states, and only the far one's *ink* changes. That is exact:
 * the schema is explicit that the tint on an entity somebody else hit "is already on your
 * screen, at the same alpha, for the same hurt ticks", so a drawing where the far figure goes
 * dark when `own_hits_only` is on would be inventing a suppression the mod does not do. What the
 * setting decides is whether your colour reaches it, and the drawing says exactly that.
 *
 * ## The colour, and where it stops
 *
 * This is the one diagram in the file whose subject **is** a colour, so the page draws it — the
 * same "where the accent rule stops" clause that lets `HitboxPreview` ink its box, and for the
 * same reason: a hurt overlay is a mark drawn over the game world, where colour is the channel.
 *
 * The **tile stays monochrome**, exactly as the hitbox tile does. §1's other half is about the
 * grid specifically — "no colour on the menu's own surface, the mod tiles included" — and the
 * page is where a colour is chosen, so the page is where it is shown. The cost is stated rather
 * than hidden: at tile density both figures take `--text-primary`, so `own_hits_only` is not
 * legible there. That is the right trade and not a hole, because a tile answers "which mod is
 * this" and two players mid-flash answers it; the page answers "what will this look like", and
 * the page has the colour.
 *
 * **Vanilla's red is not drawn**, and that is a decision worth being able to disagree with. It
 * would make `own_hits_only` read instantly and it would put the mod's whole argument in one
 * picture. It would also put an ink the player did not choose on the menu's own surface, beside
 * the one they did — and it would be the exact hue `hit_color.color` defaults away from and
 * `damage_tint` keeps, so the page would be teaching the confusion the wave was arranged to
 * avoid. The far figure's un-recoloured flash is drawn in the menu's own grey instead, and the
 * reading names it.
 *
 * ## The track under them
 *
 * `intensity` already moves both flashes, so the row is not there to satisfy the gate — it is
 * there because an alpha you can only see *on* something is an alpha you cannot judge. A bounded
 * bar filling toward a right-hand end is the shape of a fraction, and the end it fills to is
 * vanilla's own: {@link VANILLA_FLASH}. There is nothing past it, which is what makes this mod
 * `safe` rather than an argument.
 */
export function HitColorPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('hit_color');
  const range = SETTING_RANGES.intensity!;
  const intensity = Math.min(
    range.max,
    Math.max(range.min, Number(settings.intensity ?? range.max)),
  );
  const alpha = VANILLA_FLASH * intensity;
  // §1, "where the accent rule stops": the page shows the ink because the ink is the setting;
  // the tile is chrome and stays grey, like the hitbox tile and the crosshair tile.
  //
  // `rgbOnly` is not tidying. `intensity` owns the alpha, and the actuator drops any byte stored
  // in `color` for that reason — so a preview that honoured one would draw a flash the game will
  // not, on the page whose whole job is showing what the game will do. The schema now narrows
  // this setting to six digits, which closes it at the contract; this closes it for a loadout
  // written before that, or edited by hand.
  const ink =
    !dense && typeof settings.color === 'string'
      ? rgbOnly(settings.color)
      : 'var(--text-primary)';
  const ownOnly = settings.own_hits_only !== false;
  // The far figure's flash is the game's own until you tell the mod to take it over.
  const farInk = ownOnly ? 'var(--text-primary)' : ink;
  const n = steps(dense);
  const trackOn = 0.42;
  const trackOff = 0.07;
  // A continuous fill rather than a count of lit cells: the boundary cell carries the fraction,
  // so every value of `intensity` moves the row rather than only the ones that cross a cell.
  const track = Array.from({ length: n }, (_, i) => {
    const filled = Math.min(1, Math.max(0, intensity * n - i));
    return trackOff + (trackOn - trackOff) * filled;
  });
  return (
    <div className={root(dense, 'gprev--hit', className)}>
      <div className="gprev__melee">
        <Figure ink={ink} alpha={alpha} />
        <Figure far ink={farInk} alpha={alpha} />
      </div>
      <CellRow label="Alpha" cells={track} joined />
      {dense ? null : (
        <Reading
          parts={[
            intensity <= 0
              ? 'Nothing recoloured'
              : `${Math.round(intensity * 100)}% of vanilla's flash`,
            ownOnly ? 'Your hits only' : 'Every flash',
          ]}
        />
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Damage tint                                                                */
/* -------------------------------------------------------------------------- */

/**
 * The health the diagram draws you at, in half-hearts on vanilla's own 0-20 scale.
 *
 * Two hearts short of one — a *stated* number, like `FAR_BLOCKS`, so the marker and the vignette
 * cannot disagree about what they are describing. It is not a game reading and nothing measures
 * it; it is one heart left, because that is the state this mod exists for and because it is low
 * enough that the vignette is well up its ramp at the factory `threshold` and still visibly
 * short of its peak. Drawing you at full health would make the honest picture an empty frame,
 * and drawing you at zero would make `threshold` and `strength` the same control.
 */
const SAMPLE_HEALTH = 2;

/**
 * Hearts drawn for vanilla's twenty half-hearts.
 *
 * Ten marks and not twenty: hearts are what the game draws and what a player counts, and twenty
 * cells do not fit the narrowest tile the grid can produce. A half-heart threshold still lands
 * exactly, because {@link heartMark} places it inside a heart rather than between two.
 */
const HEARTS = 10;

/**
 * Heart geometry per density, in px — the cell edge and the gap between two.
 *
 * In the TSX rather than only in the CSS because the threshold marker is placed *between* two
 * hearts and has to know where the gaps are. A percentage of the row would be off by up to a gap
 * at every value, and this diagram's entire content is where one line falls on a scale, so being
 * approximately right about it is being wrong about it. 10 x 30 + 9 x 6 = 354 on the page; the
 * tile's 10 x 10 + 9 x 2 = 118, which is the same 118 the scene above it is.
 */
const HEART: Record<'page' | 'tile', { edge: number; gap: number }> = {
  page: { edge: 30, gap: 6 },
  tile: { edge: 10, gap: 2 },
};

/**
 * How deep the vignette is drawn, per density: the shadow's blur and its spread, in px.
 *
 * Inline rather than in a rule because the alpha has to be, and a `box-shadow` is one property —
 * splitting the geometry into CSS and the colour into the style attribute is not expressible.
 */
const VIGNETTE: Record<'page' | 'tile', { blur: number; spread: number }> = {
  page: { blur: 64, spread: 18 },
  tile: { blur: 20, spread: 6 },
};

/**
 * How far vanilla rolls the camera when you are hit, per `camera_shake`, in degrees.
 *
 * Fourteen is the game's own maximum and `vanilla` is unchanged, so `vanilla` is fourteen.
 * `reduced` is four rather than seven, and the number is chosen to make the setting's actual
 * shape visible: it is **not** half way between the other two. What it keeps is the *direction*,
 * which is the only thing 1.8.9 tells you about where a hit came from (`attackedAtYaw`); what it
 * spends is the amplitude, which is the part that costs you the shot. A `reduced` drawn at seven
 * would read as "a bit less", and a player would take it for a compromise between two positions
 * rather than for the one value that keeps the cue and drops the cost.
 */
const ROLL: Record<string, number> = { vanilla: 14, reduced: 4, off: 0 };

/**
 * Where a health threshold falls on the heart row, in px from its left edge.
 *
 * A boundary between two hearts lands in the middle of the gap between them, because that is
 * where it actually is — putting it on a heart's edge would claim the heart it is touching.
 * A half-heart threshold lands inside a heart, half way across.
 */
function heartMark(halves: number, edge: number, gap: number): number {
  const whole = Math.floor(halves / 2);
  const half = (halves % 2) / 2;
  const total = HEARTS * edge + (HEARTS - 1) * gap;
  if (half > 0) return Math.min(total, whole * (edge + gap) + half * edge);
  return whole === 0 ? 0 : Math.min(total, whole * (edge + gap) - gap / 2);
}

/**
 * The vignette's alpha at a given health: nothing at `threshold`, `strength` at zero.
 *
 * The ramp is the schema's, stated there because the two numbers alone do not imply it — "a mark
 * that pops on at one value is a mark you stop seeing after an hour, and the slide from three
 * hearts to dead is exactly the interval this should be describing". It is linear in the health
 * *below* the threshold, so raising the threshold with the health held still deepens the
 * vignette, which is the true and slightly surprising thing about the setting: it is not only a
 * switch-on point, it is the length of the run-in.
 */
export function vignetteAlpha(threshold: number, strength: number, health: number): number {
  if (threshold <= 0) return 0;
  const ramp = Math.min(1, Math.max(0, (threshold - health) / threshold));
  return ramp * strength;
}

/**
 * Low health and the hurt camera, on one screen and one health bar.
 *
 * ## Why the frame is the same frame
 *
 * The scene is `OverlayPreview`'s — the same `.gprev__scene`, the same horizon, the same centre
 * mark — and that is reuse rather than coincidence. Both mods' subject is *your own view*, and
 * two diagrams of the same thing that drew it two different ways would be asking the player to
 * learn a private idiom per mod. What is on it is entirely different, so there is no risk of the
 * two tiles colliding: overlay's frame is full of things being taken out of it, and this one is
 * empty except for what is happening to its edges and its horizon.
 *
 * ## What each setting moves
 *
 * - `threshold` — the **marker on the heart row**, which is the setting's literal form: a health
 *   level is a place on a health bar. It also deepens the vignette, because the ramp starts
 *   there (see {@link vignetteAlpha}), so raising it from three hearts to ten does two visible
 *   things and both of them are true.
 * - `strength` — the vignette's peak, and therefore its alpha here. It is the only one of the
 *   three that is purely a weight.
 * - `camera_shake` — a **ghost horizon at the roll angle** ({@link ROLL}). A roll about the view
 *   axis moves nothing at the centre of the screen and everything at the horizon, so the horizon
 *   is where it is drawn; the reticle stays put because in game it does too. Three values, three
 *   pictures: a steep ghost, a shallow one leaning the same way, and none at all. Leaning the
 *   same way is the whole of `reduced` and is why it is not drawn at half the angle.
 *
 * ## The two things it will not do
 *
 * **The vignette is not red here**, though it is in game and the schema is emphatic that it has
 * to be. §1's accent rule governs the menu's own surface absolutely, `hit_color` is this wave's
 * one licensed colour because there the colour *is* the setting, and here it is a constant. So
 * the vignette is drawn as the menu's own ink closing in from the edges — the shape of a vignette
 * without its hue, which is the same translation the hitbox tile makes when it draws a coloured
 * world mark in grey. The reading says what colour it will be.
 *
 * **The health does not animate and is not a live reading.** It is {@link SAMPLE_HEALTH}, fixed,
 * for §4's budget of 0 paints/s on an idle open menu and because a preview whose picture depends
 * on the player's current health would show a different mod in a lobby than in a fight.
 */
export function DamageTintPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('damage_tint');
  const tRange = SETTING_RANGES.threshold!;
  const sRange = SETTING_RANGES.strength!;
  const threshold = Math.min(
    tRange.max,
    Math.max(tRange.min, Number(settings.threshold ?? 6)),
  );
  const strength = Math.min(sRange.max, Math.max(sRange.min, Number(settings.strength ?? 0.6)));
  const shake = typeof settings.camera_shake === 'string' ? settings.camera_shake : 'vanilla';
  const roll = ROLL[shake] ?? ROLL.vanilla!;
  const alpha = vignetteAlpha(threshold, strength, SAMPLE_HEALTH);
  const { blur, spread } = VIGNETTE[dense ? 'tile' : 'page'];
  const { edge, gap } = HEART[dense ? 'tile' : 'page'];
  const hearts = Array.from({ length: HEARTS }, (_, i) =>
    (i + 1) * 2 <= SAMPLE_HEALTH ? 0.42 : 0.07,
  );
  return (
    <div className={root(dense, 'gprev--damage', className)}>
      <div className="gprev__scene">
        {/* The roll, drawn where a roll is visible. Absent at `off`, because there is then no
            second orientation for the view to reach — the same absence `FovPreview` uses for a
            locked angle rather than a fade, which would say "quieter" instead of "gone". */}
        {roll > 0 ? (
          <span
            className="gprev__horizon gprev__horizon--roll"
            style={{ transform: `rotate(${roll}deg)` }}
          />
        ) : null}
        <span className="gprev__horizon" />
        <span className="gprev__reticle" />
        {/* Last and over everything: the vignette is on your screen, not in the world. */}
        <span
          className="gprev__vignette"
          style={{
            boxShadow: `inset 0 0 ${blur}px ${spread}px rgba(237, 238, 239, ${alpha.toFixed(3)})`,
          }}
        />
      </div>
      <div className="gprev__health">
        <span className="gprev__hearts">
          {hearts.map((a, i) => (
            <span
              key={i}
              className="gprev__heart"
              style={{ background: `rgba(237, 238, 239, ${a.toFixed(3)})` }}
            />
          ))}
        </span>
        {/* Where the vignette starts. A line on a health bar, which is what the setting is. */}
        <span
          className="gprev__mark"
          style={{ left: `${heartMark(threshold, edge, gap).toFixed(1)}px` }}
        />
      </div>
      {dense ? null : (
        <Reading
          parts={[
            `From ${(threshold / 2).toFixed(threshold % 2 === 0 ? 0 : 1)} hearts`,
            `${Math.round(strength * 100)}% at zero`,
            shake === 'off'
              ? 'No hurt roll'
              : shake === 'reduced'
                ? `Hurt roll ${ROLL.reduced}°`
                : `Hurt roll ${ROLL.vanilla}°`,
          ]}
        />
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* The swallowed click — shared by Old animations and Old input               */
/* -------------------------------------------------------------------------- */

/**
 * The dead time 1.8.9 imposes after a click that hit nothing, in ticks.
 *
 * The game's own number and not a chosen one: `MinecraftClient.doAttack` sets
 * `attackCooldown = 10` on the MISS branch, and for those ten ticks every further click returns
 * at the top of the method. 1.7.10's switch has no MISS entry at all.
 *
 * It is here, above both mods, because **both of them are about this window and they disagree
 * about it**, which is the one thing a player has to get right when choosing between them:
 * `old_animations.swing_during_delay` plays the arm for the clicks the window eats,
 * `old_input.no_miss_delay` stops the window eating them. Two diagrams drawing the same ten
 * ticks at two different lengths would make that comparison unreadable, so they draw the same
 * constant.
 */
const MISS_COOLDOWN_TICKS = 10;

/**
 * Ticks per cell on the click timeline. Twelve cells is therefore 24 ticks, about 1.2 seconds.
 *
 * One tick per cell would put the whole dead window past the right-hand edge of a twelve-cell
 * row, and a window with no "after" in it is a picture of a client that stopped responding
 * rather than of a half-second cost. Two ticks per cell leaves five cells of window and five
 * cells of recovery, which is the shape of the thing.
 */
const TICKS_PER_CELL = 2;

/** The dead window, in cells. Derived, so the drawing cannot drift from the number above it. */
const DEAD_CELLS = MISS_COOLDOWN_TICKS / TICKS_PER_CELL;

/** The cell the whiff lands on. One cell in, so the row has a "before" as well as an "after". */
const WHIFF_CELL = 1;

/**
 * When the player clicks, in cells — every other cell, which at {@link TICKS_PER_CELL} is 5 CPS.
 *
 * A stated cadence rather than a measured one, exactly as `FAR_BLOCKS` is a stated distance. It
 * is chosen to be an ordinary human click rate, because the whole content of the dead window is
 * *how many of your own clicks it eats*, and that number is a function of how fast you click.
 */
const CLICK_CELLS: readonly number[] = [1, 3, 5, 7, 9, 11];

/** Whether a click on this cell falls inside the dead window the whiff opened. */
function swallowed(cell: number): boolean {
  return cell > WHIFF_CELL && cell <= WHIFF_CELL + DEAD_CELLS;
}

/* -------------------------------------------------------------------------- */
/* Old animations                                                             */
/* -------------------------------------------------------------------------- */

/**
 * The first-person arm's poses, in degrees about its own foot in the bottom-right corner.
 *
 * Three angles and one arc. {@link ARM_REST} is a hand at rest, {@link ARM_SWEPT} is the far
 * end of a swing, and {@link ARM_GUARD} is the pose `applySwordBlockTransformation` holds the
 * sword at — whose constants the schema records as *identical* in both versions, which is why
 * the guard pose is a constant here and not a setting.
 *
 * {@link ARM_ARC} is the swing's own travel, and the block stage reuses it rather than
 * inventing a shorter one. That is the fix, drawn: 1.7 passed the live
 * `getHandSwingProgress(tickDelta)` into the same `applyEquipAndSwingOffset` the block branch
 * already called, so the 1.7 block-hit is the ordinary swing *applied on top of* the guard
 * pose, not a smaller motion of its own.
 */
const ARM_REST = -10;
const ARM_SWEPT = -50;
const ARM_GUARD = -24;
const ARM_ARC = ARM_SWEPT - ARM_REST;

/*
 * The end of the block stroke is `ARM_GUARD + ARM_ARC` = -64°, and that number is the one
 * constraint on how far the arc may open. `.gprev__armstage` clips — deliberately, "an arm that
 * swings past the edge of the view has swung past the edge of the view" — but the tile's stage
 * is 56 x 62 with a 48px arm, so at -64° the tip lands 3px inside the left edge and at -74° it
 * lands on it. A tip resting exactly on the border reads as a mistake rather than as a swing
 * leaving the view, so the arc is sized for the *tile* and the page inherits it.
 */

/**
 * Where along the stroke the ghost poses sit, and how solid each one is.
 *
 * Bunched toward the start, because a swing decelerates into its end pose and evenly-spaced
 * copies would draw a constant-speed sweep the game does not have. The alpha ramp is what makes
 * the picture directional: faint at rest, nearly the solid arm's weight at the end, so the eye
 * reads the stroke as going one way. Two symmetric ghosts could not — see
 * `.gprev__arm--ghost`, which reserves the alpha for exactly this.
 */
const SWING_STOPS: readonly number[] = [0, 0.3, 0.55, 0.76];
const GHOST_ALPHA = { min: 0.13, max: 0.34 };

/**
 * One pose of the arm, solid or ghosted.
 *
 * `Ray`'s construction used for a motion rather than for an angle: a bar rotated about its own
 * foot, several copies at the angles the real one passes through. 2D `rotate` only
 * (`design/ultralight-notes.md` §4), and nothing animates — §4's budget is 0 paints/s on an
 * idle open menu, and an arm that swung would spend all of it.
 */
function Arm({
  /** Degrees about the foot; negative sweeps the hand up and across the view. */
  deg,
  /** A pose the arm *passes through* rather than the one it is drawn at. */
  ghost = false,
  /** A ghost's own weight along the stroke. Ignored for the solid pose. */
  alpha,
}: {
  deg: number;
  ghost?: boolean;
  alpha?: number;
}): React.ReactElement {
  const style = {
    transform: `rotate(${deg.toFixed(2)}deg)`,
    ...(alpha === undefined ? {} : { opacity: alpha.toFixed(3) }),
  } as CSSProperties;
  return <span className={ghost ? 'gprev__arm gprev__arm--ghost' : 'gprev__arm'} style={style} />;
}

/**
 * One labelled stage: a framed first-person view with an arm in it, at one pose or at all of
 * them.
 *
 * `swings` is the whole of the difference between a motion and a hold. When it is false the arm
 * is drawn once, at `from`, and there are **no ghosts at all** — the same absence `FovPreview`
 * uses for a locked angle and `DamageTintPreview` for `camera_shake: off`. Past the lock there
 * is no second position, and fading the ghosts instead would say "quieter" where the truth is
 * "gone".
 */
function ArmStage({
  label,
  from,
  to,
  swings,
  guard = false,
}: {
  /** The stage's axis label — `Swing` or `Block`. Part of the drawing, not a caption. */
  label: string;
  /** The pose the stroke starts at, and the pose a held arm is drawn at. */
  from: number;
  /** The pose the stroke ends at. */
  to: number;
  /** Whether the arm travels, or is held at `from`. */
  swings: boolean;
  /** The raised sword. Context for the block stage, and never a setting. */
  guard?: boolean;
}): React.ReactElement {
  return (
    <div className="gprev__armcol">
      <span className="gprev__stagelabel">{label}</span>
      <div className="gprev__armstage">
        {guard ? <span className="gprev__guard" /> : null}
        {swings
          ? SWING_STOPS.map((t, i) => (
              <Arm
                key={i}
                ghost
                deg={from + (to - from) * t}
                alpha={GHOST_ALPHA.min + (GHOST_ALPHA.max - GHOST_ALPHA.min) * t}
              />
            ))
          : null}
        <Arm deg={swings ? to : from} />
      </div>
    </div>
  );
}

/**
 * The block-hit, as a swing that survives the guard — and the free swing beside it that does
 * not change.
 *
 * ## Why there are two stages when there is only one enum
 *
 * The left stage reads no setting and never moves, and it is the most load-bearing thing on
 * this diagram. `schema/mods/old_animations.json` spends its whole `$comment` establishing that
 * the swing itself is **byte-for-byte identical** between 1.7.10 and 1.8.9 — `swingHand`,
 * `tickHandSwing`, `getHandSwingProgress` and the three first-person rotations all the way down
 * to `-0.71999997f` — and that a checkbox called `swing` would be a promise to smuggle in a
 * renderer rewrite. A player opens a mod called Old animations expecting their swing to change.
 * It does not, and the honest place to say so is the picture rather than the changelog: the free
 * swing is drawn, in full, and it is the same in both values of the only setting there is.
 *
 * It is also what makes the right stage legible. `vanilla` and `one_seven` on their own are two
 * arms, and an arm is not self-evidently frozen; an arm frozen *beside the arc it should have
 * had* is. The two stages are the mechanism — `applyEquipAndSwingOffset(equip, 0.0F)` throws the
 * swing progress away — expressed as the one comparison that shows what was thrown.
 *
 * This is the shape `SprintPreview`'s doc comment warns about, met from the other side. There,
 * two tracks were drawn side by side and one of them was a lie, because `hold` had no
 * implementation. Here the second stage differs from the first by exactly one float argument in
 * one method, which the schema quotes; the difference is real, so the comparison is allowed.
 *
 * ## What each setting moves
 *
 * - `block_hit` — the **right stage only**. `one_seven` gives it the same {@link ARM_ARC} the
 *   free swing has, ghosts and all, started from the guard pose. `vanilla` removes every ghost
 *   and parks the solid arm at {@link ARM_GUARD}, which is a frozen sword with a hit landing
 *   somewhere behind it. The raised guard stays in both, because the sword is up either way —
 *   what changes is whether your swing reaches it.
 * - `swing_during_delay` — the **`Arm` row**, and nothing else on the page.
 *
 * ## How the timeline refuses to say the clicks work again
 *
 * This is the trap the schema is emphatic about, and it is worth naming: the setting plays a
 * local animation for clicks 1.8 has already eaten. It does not give the clicks back. A one-row
 * drawing of "your arm swings more" is indistinguishable from "your clicks land more", and a
 * player who read it that way would have been told the opposite of the truth by their own
 * settings page.
 *
 * So the timeline has three rows and only the middle one is the setting:
 *
 * - `Click` — the button going down, at {@link CLICK_CELLS}. Never moves. It is what you did.
 * - `Arm` — `LivingEntity.swingHand()`'s two public fields running. The only row the setting
 *   touches. On, it becomes an exact copy of `Click`: the arm agrees with the mouse, which is
 *   the schema's own phrase for what this is.
 * - `Sent` — what leaves the client and reaches the fight. **Identical in both states**, with
 *   the two swallowed clicks dark in both. Nothing reaches the server, so nothing about the
 *   exchange changes.
 *
 * That inversion is `SneakPreview`'s reading turned over. There the crouch row is identical in
 * both values and only the key row moves, and the identity is the content — same sneak, half the
 * key. Here the identity is the *warning*: same fight, more arm. `old_input.no_miss_delay` draws
 * this same `Sent` row, and there it does move — which is the difference between the two mods,
 * and the reason one of them is `safe` and the other is not.
 *
 * ## Tile density
 *
 * The rows are dropped and the two stages are the whole tile, which `.gprev--tile.gprev--anim`
 * already sizes for. Same trade `FreelookPreview` makes: a 123px well holds one diagram, and
 * the one that identifies this mod is an arm moving through a raised sword.
 */
export function AnimationPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('old_animations');
  // Written against `'vanilla'` rather than for `'one_seven'` so an unknown value falls back to
  // the shipped default, which is the mod doing its job.
  const oneSeven = settings.block_hit !== 'vanilla';
  const armInDelay = settings.swing_during_delay === true;
  const n = steps(dense);
  const on = 0.42;
  const off = 0.07;
  const click = Array.from({ length: n }, (_, i) => (CLICK_CELLS.includes(i) ? on : off));
  // What the client actually does with each click. The whiff at `WHIFF_CELL` works — it swings
  // and misses, which is an attack — and the ones inside the window it opened do not.
  const sent = Array.from({ length: n }, (_, i) =>
    CLICK_CELLS.includes(i) && !swallowed(i) ? on : off,
  );
  // The one row this mod moves: the mouse when it is on, the client when it is off.
  const arm = armInDelay ? click : sent;
  const eaten = CLICK_CELLS.filter(swallowed).length;
  return (
    <div className={root(dense, 'gprev--anim', className)}>
      <div className="gprev__stages">
        <ArmStage label="Swing" from={ARM_REST} to={ARM_REST + ARM_ARC} swings />
        <ArmStage
          label="Block"
          from={ARM_GUARD}
          to={ARM_GUARD + ARM_ARC}
          swings={oneSeven}
          guard
        />
      </div>
      {dense ? null : (
        <div className="gprev__gate">
          <CellRow label="Click" cells={click} />
          <CellRow label="Arm" cells={arm} />
          <CellRow label="Sent" cells={sent} />
        </div>
      )}
      {dense ? null : (
        <Reading
          parts={[
            oneSeven ? 'Sword swings while blocking' : 'Sword frozen while blocking',
            `A miss eats ${eaten} clicks`,
            armInDelay ? 'The arm swings anyway' : 'The arm stops too',
          ]}
        />
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Old input                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * Three input gates 1.8 added, as the ticks they take away from you.
 *
 * ## The mirrors are one picture, not two
 *
 * `use_while_digging` and `dig_while_using` are the same one-term difference in two methods —
 * `doUse` opening `if (isBreakingBlock()) return;`, `handleBlockBreaking` opening
 * `if (attackCooldown > 0 || player.isUsingItem()) return;` — and the schema found them
 * together. Drawn as two separate instruments they would be two identical rows of cells, which
 * is the anonymous-grid failure this file's header describes.
 *
 * So they share one timeline and one consequence, which is the true one and is sharper than
 * either setting stated on its own: **in 1.8 these two bars cannot overlap.** A break and an
 * item in use may not be live in the same tick, so at the factory settings the second bar can
 * only begin after the first has ended, and the cell between them is the changeover the
 * interlock costs you. Each switch then opens one end of that:
 *
 * - `use_while_digging` — the `Use` bar starts **earlier**, inside the break, and the two rows
 *   overlap on the left.
 * - `dig_while_using` — the `Mine` bar runs **later**, on through the item in use, and they
 *   overlap on the right.
 *
 * Opposite directions on the same pair of rows, which is what "mirrors" means, drawn.
 *
 * ## The whiff is a second situation and is drawn as one
 *
 * `no_miss_delay` cannot join that timeline, and the reason is mechanical rather than
 * aesthetic: mining and attacking are the **same button**. A row that was a break in progress
 * cannot also be a whiffed swing without claiming the player did both with one hand. So the
 * whiff gets its own pair below, and the two groups are told apart by the `joined` distinction
 * the file already relies on — a break and a use are *states* and run as unbroken bars, a click
 * is an *event* and stays a discrete mark.
 *
 * - `Click` — the button going down, {@link CLICK_CELLS}, never moving.
 * - `Sent` — what reaches the fight. Off, the {@link MISS_COOLDOWN_TICKS}-tick window eats the
 *   clicks inside it. On, every click gets through.
 *
 * **That `Sent` row is deliberately the same row `AnimationPreview` draws**, and comparing the
 * two is the whole difference between the mods: there it is identical in both states because
 * only an animation was restored, and here it lights up because the clicks were. It is also
 * exactly why this mod is `grey` — a click that is not swallowed is a `HandSwingC2SPacket` that
 * is sent, and outbound swing volume on whiffs is the number a CPS anticheat measures. The
 * drawing says that by drawing more marks in the row named for what leaves the client, which is
 * as close to editorialising as art should get: it states the cost and lets the player price it.
 *
 * ## The factory state is 1.8, and every switch has to open something
 *
 * All three ship off and the mod ships off, so the drawing a player meets is 1.8 with three
 * gates closed: two bars that will not overlap and a click row with holes under it. There is no
 * value of any switch that makes the picture quieter, which is the right shape for a mod whose
 * defaults are the classification's doing rather than a hedge.
 *
 * ## Tile density
 *
 * The whiff pair is dropped and the two gate bars are the tile, for `FreelookPreview`'s reason:
 * four labelled rows come to ~126px in a well that can be 123. The pair that survives is the one
 * that carries the mod's name — two inputs that 1.8 will not let be live at once.
 */
export function InputPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('old_input');
  const useWhileDigging = settings.use_while_digging === true;
  const digWhileUsing = settings.dig_while_using === true;
  const noMissDelay = settings.no_miss_delay === true;
  const n = steps(dense);
  const on = 0.42;
  const off = 0.07;
  // The break starts where it starts; what the switches move is where each bar *ends* relative
  // to the other one. Both densities keep the same shape — a break, a changeover cell, a use —
  // so the tile and the page cannot disagree about what the interlock costs.
  const mineFrom = dense ? 0 : 1;
  const mineTo = digWhileUsing ? (dense ? 5 : 9) : dense ? 2 : 5;
  const useFrom = useWhileDigging ? (dense ? 2 : 3) : dense ? 4 : 7;
  const mine = Array.from({ length: n }, (_, i) => (i >= mineFrom && i <= mineTo ? on : off));
  const use = Array.from({ length: n }, (_, i) => (i >= useFrom ? on : off));
  const click = Array.from({ length: n }, (_, i) => (CLICK_CELLS.includes(i) ? on : off));
  const sent = Array.from({ length: n }, (_, i) =>
    CLICK_CELLS.includes(i) && (noMissDelay || !swallowed(i)) ? on : off,
  );
  const eaten = CLICK_CELLS.filter(swallowed).length;
  return (
    <div className={root(dense, 'gprev--input', className)}>
      <div className="gprev__gates">
        <div className="gprev__gate">
          <CellRow label="Mine" cells={mine} joined />
          <CellRow label="Use" cells={use} joined />
        </div>
        {dense ? null : (
          <div className="gprev__gate">
            <CellRow label="Click" cells={click} />
            <CellRow label="Sent" cells={sent} />
          </div>
        )}
      </div>
      {dense ? null : (
        <Reading
          parts={[
            useWhileDigging && digWhileUsing
              ? 'Mine and use overlap'
              : useWhileDigging
                ? 'Use starts inside a break'
                : digWhileUsing
                  ? 'A break runs through a use'
                  : '1.8 keeps them apart',
            noMissDelay
              ? `No miss delay — all ${CLICK_CELLS.length} clicks sent`
              : `A miss eats ${eaten} clicks`,
          ]}
        />
      )}
    </div>
  );
}
