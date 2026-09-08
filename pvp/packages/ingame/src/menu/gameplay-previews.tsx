/**
 * Previews for the four mods that draw into the world, not onto the page.
 *
 * ## Why these are different from the other nine
 *
 * `LIVE_WIDGETS` (`ModSettingsScreen.tsx`) points nine mods at **the component `HudLayer`
 * already places**, which is the strongest possible guarantee that the preview and the HUD
 * agree: they are the same code. Four mods cannot be done that way, because there is no HTML of
 * them anywhere. Fullbright changes `mc.options.gamma`. Zoom changes the camera FOV. Hitboxes
 * pushes GL lines from an entity render pass. Toggle sprint changes what a key does and draws
 * nothing at all.
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
}: {
  label: string;
  /** Alpha per cell, 0-1. */
  cells: readonly number[];
}): React.ReactElement {
  return (
    <div className="gprev__row">
      <span className="gprev__rowlabel">{label}</span>
      <span className="gprev__cells">
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

/** The one-line reading under a diagram: what the current value actually means. */
function Reading({ children }: { children: React.ReactNode }): React.ReactElement {
  return <p className="gprev__reading">{children}</p>;
}

/* -------------------------------------------------------------------------- */
/* Fullbright                                                                 */
/* -------------------------------------------------------------------------- */

/** Steps in each ramp. Twelve reads as a scale rather than as a row of blocks. */
const RAMP = 12;

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
export function FullbrightPreview(): React.ReactElement {
  const settings = useModSettings('fullbright');
  const gamma = Number(settings.gamma ?? 10);
  const world = Array.from({ length: RAMP }, (_, i) => 0.04 + (i / (RAMP - 1)) * 0.9);
  const seen = world.map((base) => litLevel(base, gamma));
  return (
    <div className="gprev">
      <CellRow label="World" cells={world} />
      <CellRow label="Seen" cells={seen} />
      <Reading>
        {gamma <= 1
          ? 'At 1.0 the two rows match — the mod is on and changing nothing.'
          : `The darkest step reads ${Math.round(seen[0]! * 100)}% instead of ${Math.round(
              world[0]! * 100,
            )}%.`}
      </Reading>
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
export function ZoomPreview(): React.ReactElement {
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
  const steps = smooth ? [0.72, 0.48, 0.3].map((t) => 1 - (1 - fraction) * (1 - t)) : [];
  return (
    <div className="gprev gprev--zoom">
      <div className="gprev__fov">
        {steps.map((f, i) => (
          <span key={i} className="gprev__fovstep" style={frame(f)} />
        ))}
        <span className="gprev__fovinner" style={frame(fraction)} />
        {/* The key is part of the sentence this diagram is making — you hold *this* to get
            *that*. It is printed in the page's meta line too, but there it is a fact about the
            mod; here it is the first half of the interaction. */}
        <span className="gprev__fovkey">
          <span className="gprev__kbd">{keybindLabel(settings.key ?? null)}</span>
          <span className="gprev__fovhold">hold</span>
        </span>
        <span className="gprev__fovlabel tnum">{divisor.toFixed(1)}×</span>
      </div>
      <Reading>
        {`${BASE_FOV}° becomes ${(BASE_FOV / Math.max(1, divisor)).toFixed(1)}° — ` +
          `${Math.round(fraction * 100)}% of the width, filling the screen.`}
        {divisor <= range.min ? ' Barely a zoom at all.' : ''}
        {smooth ? ' It eases in through the steps behind it.' : ' It snaps straight there.'}
      </Reading>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Hitboxes                                                                   */
/* -------------------------------------------------------------------------- */

/** px per `line_width` unit. 0.5 comes out at 2px and 5 at 20, which is what 5 looks like. */
const HITBOX_UNIT = 4;

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
export function HitboxPreview(): React.ReactElement {
  const settings = useModSettings('hitboxes');
  const width = Number(settings.line_width ?? 2) * HITBOX_UNIT;
  // A world mark, so it takes the mod's own ink — `design/quiet-cell-system.md` §1, "where the
  // accent rule stops". Everything else on this diagram stays monochrome.
  const color = typeof settings.color === 'string' ? settings.color : 'var(--text-primary)';
  const eye = settings.show_eye_line === true;
  return (
    <div className="gprev gprev--hitbox">
      <div className="gprev__box" style={{ borderWidth: `${width}px`, borderColor: color }}>
        {eye ? (
          <span
            className="gprev__eye"
            style={{ height: `${Math.max(1, width)}px`, background: color }}
          />
        ) : null}
      </div>
      <Reading>
        {`A player's box is 0.6 by 1.8 blocks, drawn at ${Number(
          settings.line_width ?? 2,
        )} px.${eye ? ' The eye line leaves at 1.62, where they are looking.' : ''}`}
      </Reading>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Toggle sprint                                                              */
/* -------------------------------------------------------------------------- */

/** Steps in the timeline. Twelve again, so it reads as the same kind of object as the ramp. */
const TRACK = 12;

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
 */
export function SprintPreview(): React.ReactElement {
  const settings = useModSettings('toggle_sprint');
  const hold = settings.mode === 'hold';
  const sneak = settings.sneak_too === true;
  const on = 0.42;
  const off = 0.07;

  const key = Array.from({ length: TRACK }, (_, i) =>
    hold ? (i >= 2 && i <= 7 ? on : off) : i === 2 ? on : off,
  );
  const state = Array.from({ length: TRACK }, (_, i) =>
    hold ? (i >= 2 && i <= 7 ? on : off) : i >= 2 ? on : off,
  );

  return (
    <div className="gprev">
      <CellRow label="Key" cells={key} />
      <CellRow label="Sprint" cells={state} />
      {sneak ? <CellRow label="Sneak" cells={key} /> : null}
      <Reading>
        {hold
          ? 'Hold: sprint lasts exactly as long as the key is down.'
          : 'Toggle: one tap and sprint stays on until you tap again.'}
        {sneak ? ' Sneak follows the same rule.' : ''}
      </Reading>
    </div>
  );
}
