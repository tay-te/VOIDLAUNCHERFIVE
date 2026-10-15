/**
 * Previews for Wave 7 — the FPS boost mods and the visual table stakes.
 *
 * Eleven more mods that draw into the world (or stop the world drawing), so eleven more
 * **diagrams**, on exactly the terms `gameplay-previews.tsx` sets out at its head: the drawing
 * is not the game's, the numbers are. Same primitives (§3's cell, a plain outline, a ghost for a
 * state the thing reaches), same rules — monochrome unless the mod's setting *is* the ink of a
 * world mark, nothing animates, and `dense` is the same drawing at tile density with the
 * readings and row labels it cannot hold taken off.
 *
 * They live in their own file rather than appended to `gameplay-previews.tsx` for the reason
 * `mods/art.tsx` gives for per-mod modules: the shared file stops growing when the roster does.
 * The shared pieces — {@link CellRow}, {@link Reading}, {@link root}, {@link steps} — are
 * imported from there, so the two files cannot drift into two vocabularies.
 *
 * ## Where the ink is allowed
 *
 * Three of these draw a colour on the page, and each is the "where the accent rule stops"
 * clause hitboxes and hit colour already live under: Block outline draws `outline_color` and
 * `fill_color` (the colour of a mark over the world, chosen on this page), Shiny pots draws the
 * glint's colour (vanilla's purple, or the potion's own when `tint_glint` asks for it — the
 * setting's whole content is that colour), and Nametags draws a black plate over a grey ground
 * because the plate's *alpha* is the setting and black is what the game draws it in. Every tile
 * stays monochrome.
 *
 * ## Plan views
 *
 * Culling and the particle limiter are drawn **from above**, on a ground of 32 x 18 blocks. Both
 * mods are about *where* things are relative to the camera — behind a wall, outside the view
 * cone, past a distance — and a first-person frame cannot show the things it is not drawing.
 * The frame's aspect is the ground's, so a block is the same length in both axes and a radius in
 * blocks is a true circle.
 */

import { type CSSProperties } from 'react';

import { useModSettings, useVoidStore } from '@/store/store';
import { SETTING_RANGES } from '@/registry';
import { CellRow, Reading, root, steps, type DiagramProps } from './gameplay-previews';
import { formatSetting } from './settings-format';

/* -------------------------------------------------------------------------- */
/* Shared pieces                                                              */
/* -------------------------------------------------------------------------- */

/** A cell row's two steps, the same `on` / `off` every timeline in the client uses. */
const ON = 0.42;
const OFF = 0.07;

/** A number clamped to the setting's own schema bounds, falling back to `fallback`. */
function num(key: string, value: unknown, fallback: number): number {
  const range = SETTING_RANGES[key];
  const n = typeof value === 'number' && !Number.isNaN(value) ? value : fallback;
  return range ? Math.min(range.max, Math.max(range.min, n)) : n;
}

/**
 * A row filled to a fraction, with the boundary cell carrying the remainder.
 *
 * Continuous rather than a count of lit cells — `HitColorPreview`'s track does the same — so
 * every value of a fraction moves the row, not only the ones that happen to cross a cell.
 */
function fillRow(n: number, fraction: number, on = ON, off = OFF): number[] {
  const f = Math.min(1, Math.max(0, fraction));
  return Array.from({ length: n }, (_, i) => off + (on - off) * Math.min(1, Math.max(0, f * n - i)));
}

/** Ground size of the plan views, in blocks. The frame's aspect is exactly this. */
const PLAN_W = 32;
const PLAN_H = 18;

/** Position in plan blocks, as the percentages the absolutely-placed marks take. */
function at(x: number, y: number): CSSProperties {
  return { left: `${((x / PLAN_W) * 100).toFixed(2)}%`, top: `${((y / PLAN_H) * 100).toFixed(2)}%` };
}

/** Where the camera stands on both plan views. */
const EYE = { x: 2, y: 9 };

/* -------------------------------------------------------------------------- */
/* Entity culling                                                             */
/* -------------------------------------------------------------------------- */

/** The wall, in plan blocks: one block thick, most of the ground tall. */
const WALL = { x: 13, w: 1, y: 2, h: 14 };

interface Thing {
  readonly kind: 'player' | 'chest';
  readonly x: number;
  readonly y: number;
  /** On the far side of {@link WALL} from the camera — no ray reaches it. */
  readonly behind: boolean;
}

/**
 * What stands on the culling plan.
 *
 * Two things in front of the wall, which are always drawn and are the control. Five behind it,
 * and one of those — the player at (15.5, 8) — is placed **13.5 blocks** from the camera on
 * purpose: inside the top of `always_draw_within`'s range, so dragging the radius past it brings
 * that player back while the wall is still between you. That is exactly what the radius does,
 * and it is the only way to show it that is not a sentence.
 *
 * The far chests sit at 20 and 28 blocks so `block_entity_distance` — 16 at its lowest — can
 * take them off the ground, which is the other thing a chest can lose.
 */
const THINGS: readonly Thing[] = [
  { kind: 'player', x: 8, y: 5.5, behind: false },
  { kind: 'chest', x: 9, y: 13, behind: false },
  { kind: 'player', x: 15.5, y: 8, behind: true },
  { kind: 'chest', x: 20, y: 13.5, behind: true },
  { kind: 'player', x: 25, y: 4.5, behind: true },
  { kind: 'chest', x: 28, y: 10.5, behind: true },
  { kind: 'player', x: 29, y: 15, behind: true },
];

function distance(t: { x: number; y: number }): number {
  return Math.hypot(t.x - EYE.x, t.y - EYE.y);
}

/**
 * Culling, as a plan view of a wall and what is behind it — plus the four distance limits.
 *
 * - `occlude_entities` / `occlude_block_entities` — a player or chest behind the wall drops from
 *   a filled mark to a **ghost**: still on the ground, no longer drawn. Ghost rather than absent
 *   because the thing is still there in the world and the mod's whole promise is that you lose
 *   nothing you could have seen.
 * - `always_draw_within` — the ring round the camera. Anything inside it is drawn whatever the
 *   wall says.
 * - `nametags_through_walls` — a culled player keeps the plate over its head, or loses it.
 * - The four distances — one joined row each on a 0-64 block scale, filled to the limit; the far
 *   chests also leave the plan when `block_entity_distance` falls short of them.
 */
export function CullingPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('culling');
  const occE = settings.occlude_entities !== false;
  const occB = settings.occlude_block_entities !== false;
  const radius = num('always_draw_within', settings.always_draw_within, 4);
  const tags = settings.nametags_through_walls !== false;
  const chestTo = num('block_entity_distance', settings.block_entity_distance, 64);
  const standTo = num('armor_stand_distance', settings.armor_stand_distance, 64);
  const frameTo = num('item_frame_distance', settings.item_frame_distance, 64);
  const signTo = num('sign_text_distance', settings.sign_text_distance, 32);
  const n = steps(dense);

  const marks = THINGS.filter((t) => t.kind === 'player' || distance(t) <= chestTo).map((t) => {
    const occludes = t.kind === 'player' ? occE : occB;
    const culled = t.behind && occludes && distance(t) > radius;
    return { ...t, culled, tag: t.kind === 'player' && (!culled || tags) };
  });
  const skipped = marks.filter((m) => m.culled).length;

  return (
    <div className={root(dense, 'gprev--culling', className)}>
      <div className="vprev__plan">
        <span
          className="vprev__ring"
          style={{
            ...at(EYE.x, EYE.y),
            width: `${(((2 * radius) / PLAN_W) * 100).toFixed(2)}%`,
            height: `${(((2 * radius) / PLAN_H) * 100).toFixed(2)}%`,
          }}
        />
        <span
          className="vprev__wall"
          style={{
            ...at(WALL.x, WALL.y),
            width: `${((WALL.w / PLAN_W) * 100).toFixed(2)}%`,
            height: `${((WALL.h / PLAN_H) * 100).toFixed(2)}%`,
          }}
        />
        {marks.map((m, i) => (
          <span key={i} className="vprev__thing" style={at(m.x, m.y)}>
            {m.tag ? <span className="vprev__tagmark" /> : null}
            <span
              className={[
                m.kind === 'player' ? 'vprev__mob' : 'vprev__chest',
                m.culled ? 'vprev__ghost' : null,
              ]
                .filter(Boolean)
                .join(' ')}
            />
          </span>
        ))}
        <span className="vprev__eyept" style={at(EYE.x, EYE.y)} />
      </div>
      {dense ? null : (
        <div className="gprev__gate">
          <CellRow label="Chests" cells={fillRow(n, chestTo / 64)} joined />
          <CellRow label="Stands" cells={fillRow(n, standTo / 64)} joined />
          <CellRow label="Frames" cells={fillRow(n, frameTo / 64)} joined />
          <CellRow label="Signs" cells={fillRow(n, signTo / 64)} joined />
        </div>
      )}
      {dense ? null : (
        <Reading>
          {(occE && occB
            ? `Entities and chests no ray can reach are skipped — ${skipped} behind this wall`
            : occE
              ? `Entities no ray can reach are skipped — ${skipped} here; chests behind walls are still drawn`
              : occB
                ? `Chests no ray can reach are skipped — ${skipped} here; entities behind walls are still drawn`
                : 'Nothing is skipped for being behind a wall') +
            (occE || occB ? `, but nothing inside ${radius} blocks ever is.` : '.') +
            (occE
              ? tags
                ? ' A culled player keeps their name, as vanilla shows it.'
                : ' A culled player loses their name too.'
              : '') +
            ` Chests draw to ${chestTo} blocks, armour stands ${standTo}, framed items ${frameTo}, sign text ${signTo}.`}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Particle limiter                                                           */
/* -------------------------------------------------------------------------- */

/** Half the view cone drawn on the plan, in degrees — 70° vanilla FOV, as `gameplay-previews`. */
const CONE_HALF = 35;

/** A deterministic scatter, so the drawing never changes unless a setting does. */
function scatter(count: number, seed: number, cx: number, cy: number, spread: number) {
  let s = seed;
  const next = () => {
    s = (s * 1103515245 + 12345) % 2147483648;
    return s / 2147483648;
  };
  return Array.from({ length: count }, () => ({
    x: cx + (next() - 0.5) * spread * 2,
    y: cy + (next() - 0.5) * spread * 1.4,
  }));
}

/** A block being broken in front of you: vanilla's burst, at the scale this ground holds. */
const BREAK_BURST = scatter(10, 7, 12, 9, 1.6);
/** An explosion further out: its smoke. */
const BLAST_CLOUD = scatter(14, 19, 24, 10, 2.6);
/** Particles beside and behind you, outside the view cone. */
const AMBIENT = [
  ...scatter(5, 31, 7, 2, 2.5),
  ...scatter(5, 43, 8, 16, 2.5),
  ...scatter(3, 57, 1.5, 3, 1),
];

function inCone(p: { x: number; y: number }): boolean {
  const deg = (Math.atan2(p.y - EYE.y, p.x - EYE.x) * 180) / Math.PI;
  return Math.abs(deg) <= CONE_HALF;
}

/**
 * The particle limiter, as a plan view of the view cone and three kinds of particle in it.
 *
 * - `frustum_cull` — the particles outside the cone. Off, they are drawn (paid for, never seen);
 *   on, they are ghosts. That is the one switch here that is free, and the picture says why: the
 *   ghosts are exactly the ones you could not have seen.
 * - `block_break_particles` / `explosion_particles` — how much of each burst survives, as the
 *   number of pieces left in it and as a row filled to the fraction.
 * - `particle_cap` — a row on vanilla's own 4000 scale. A cap is a ceiling on a count nobody can
 *   see on a still frame, so it is drawn as the ceiling.
 */
export function ParticlesPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('particles');
  const frustum = settings.frustum_cull !== false;
  const cap = num('particle_cap', settings.particle_cap, 4000);
  const breakKept = num('block_break_particles', settings.block_break_particles, 1);
  const blastKept = num('explosion_particles', settings.explosion_particles, 1);
  const n = steps(dense);
  const kept = <T,>(list: readonly T[], fraction: number) =>
    list.slice(0, Math.round(list.length * fraction));
  const dots = [
    ...kept(BREAK_BURST, breakKept).map((p) => ({ ...p, ghost: false })),
    ...kept(BLAST_CLOUD, blastKept).map((p) => ({ ...p, ghost: false })),
    ...AMBIENT.filter((p) => !inCone(p)).map((p) => ({ ...p, ghost: frustum })),
  ].filter((d) => !(dense && d.ghost));
  const ghosts = AMBIENT.filter((p) => !inCone(p)).length;
  return (
    <div className={root(dense, 'gprev--particles', className)}>
      <div className="vprev__plan">
        <span className="vprev__ray" style={{ ...at(EYE.x, EYE.y), transform: `rotate(${-CONE_HALF}deg)` }} />
        <span className="vprev__ray" style={{ ...at(EYE.x, EYE.y), transform: `rotate(${CONE_HALF}deg)` }} />
        <span className="vprev__block" style={at(12, 9)} />
        {dots.map((d, i) => (
          <span key={i} className={d.ghost ? 'vprev__dot vprev__ghost' : 'vprev__dot'} style={at(d.x, d.y)} />
        ))}
        <span className="vprev__eyept" style={at(EYE.x, EYE.y)} />
      </div>
      {dense ? null : (
        <div className="gprev__gate">
          <CellRow label="Cap" cells={fillRow(n, cap / 4000)} joined />
          <CellRow label="Break" cells={fillRow(n, breakKept)} joined />
          <CellRow label="Blast" cells={fillRow(n, blastKept)} joined />
        </div>
      )}
      {dense ? null : (
        <Reading>
          {(frustum
            ? `The ${ghosts} particles outside your view are skipped — you could not have seen them.`
            : `The ${ghosts} particles outside your view are still drawn, and paid for.`) +
            ` ${Math.round(breakKept * 100)}% of a block-break burst and ${Math.round(blastKept * 100)}% of explosion smoke are kept.` +
            (cap >= 4000
              ? ' The cap is vanilla’s 4000.'
              : ` At most ${cap} live per layer; past that the oldest goes first.`)}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Frame limiter                                                              */
/* -------------------------------------------------------------------------- */

/** The scale the rows are drawn on: the top of `menu_fps`, the highest cap the mod can set. */
const FPS_SCALE = 240;
/** What a minimised window drops to — fixed by the mod, not a setting. */
const MINIMISED_FPS = 5;

/** One labelled row with its figure beside it. */
function RateRow({
  label,
  fps,
  dense,
}: {
  label: string;
  /** `null` is uncapped — a full row, because nothing holds it down. */
  fps: number | null;
  dense: boolean;
}): React.ReactElement {
  const cells = fillRow(steps(dense), fps === null ? 1 : fps / FPS_SCALE);
  if (dense) return <CellRow label={label} cells={cells} joined />;
  return (
    <div className="vprev__rate">
      <CellRow label={label} cells={cells} joined />
      <span className="vprev__rateval tnum">{fps === null ? 'Uncapped' : `${fps} fps`}</span>
    </div>
  );
}

/**
 * The frame limiter, as one row per situation, each filled to its cap.
 *
 * The reference row — playing, focused, uncapped — is drawn on the page so every cap reads
 * against what the game does when nothing holds it down. The tile keeps the three rows the mod
 * actually sets.
 */
export function FrameLimiterPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('frame_limiter');
  const away = num('unfocused_fps', settings.unfocused_fps, 30);
  const menu = num('menu_fps', settings.menu_fps, 60);
  const idle = settings.idle_minimized !== false;
  return (
    <div className={root(dense, 'gprev--frames', className)}>
      <div className="gprev__gate">
        {dense ? null : <RateRow label="Game" fps={null} dense={dense} />}
        <RateRow label="Away" fps={away} dense={dense} />
        <RateRow label="Menu" fps={menu} dense={dense} />
        <RateRow label="Hidden" fps={idle ? MINIMISED_FPS : null} dense={dense} />
      </div>
      {dense ? null : (
        <Reading>
          {`Playing is never capped. In the background the game holds ${away} fps, in menus ${menu}` +
            (idle
              ? `, and minimised it drops to ${MINIMISED_FPS} — nothing is visible, so nothing is lost.`
              : ', and minimised it renders as fast as it can, for nobody.')}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Block outline                                                              */
/* -------------------------------------------------------------------------- */

/** A block face's texture, 4 x 4, as alphas. One pattern for all three blocks: the same block. */
const TEXTURE: readonly number[] = [
  0.16, 0.11, 0.14, 0.1, 0.1, 0.15, 0.09, 0.13, 0.13, 0.08, 0.15, 0.11, 0.09, 0.14, 0.11, 0.16,
];

/** px per `outline_width` screen pixel, per density — a stroke in proportion to its block. */
const OUTLINE_UNIT = { page: 2, tile: 0.7 };

function BlockFace({ children }: { children?: React.ReactNode }): React.ReactElement {
  return (
    <span className="vprev__face">
      {TEXTURE.map((a, i) => (
        <span key={i} className="vprev__texel" style={{ background: `rgba(237, 238, 239, ${a})` }} />
      ))}
      {children}
    </span>
  );
}

/**
 * Block outline, as a run of three blocks with the middle one targeted.
 *
 * Three rather than one because the outline's subject is *which* block you are looking at: on
 * its own a framed square is a picture of a frame. The style, width and both colours are drawn
 * exactly — the page in the player's ink, the tile in the menu's.
 */
export function BlockOutlinePreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('block_outline');
  const style = typeof settings.outline_style === 'string' ? settings.outline_style : 'outline';
  const width = num('outline_width', settings.outline_width, 2.5);
  const lines = style === 'outline' || style === 'both';
  const filled = style === 'fill' || style === 'both';
  const line =
    !dense && typeof settings.outline_color === 'string' ? settings.outline_color : 'var(--text-primary)';
  const fill =
    !dense && typeof settings.fill_color === 'string' ? settings.fill_color : 'rgba(237, 238, 239, 0.22)';
  const px = Math.max(1, width * (dense ? OUTLINE_UNIT.tile : OUTLINE_UNIT.page));
  return (
    <div className={root(dense, 'gprev--outline', className)}>
      <div className="vprev__blocks">
        <BlockFace />
        <BlockFace>
          {filled ? <span className="vprev__fill" style={{ background: fill }} /> : null}
          {lines ? (
            <span
              className="vprev__outline"
              style={{ borderWidth: `${px.toFixed(1)}px`, borderColor: line, inset: `${(-px / 2).toFixed(1)}px` }}
            />
          ) : null}
          <span className="vprev__aim" />
        </BlockFace>
        <BlockFace />
      </div>
      {dense ? null : (
        <Reading>
          {style === 'none'
            ? 'Nothing marks the block you are looking at — you aim off the crosshair alone.'
            : style === 'fill'
              ? 'The targeted block’s face is shaded in your colour, with no lines round it.'
              : `The targeted block is outlined at ${width} px` +
                (style === 'both' ? ', and its face shaded too.' : ' — vanilla’s is 2 px, in black at 40%.')}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Shiny pots                                                                 */
/* -------------------------------------------------------------------------- */

type Slot = 'sword' | 'heal' | 'speed' | 'fire' | 'water' | 'block' | 'pearl' | 'empty';

/** The page's hotbar — a Bedwars or pot-PvP bar — and the tile's four pots. */
const HOTBAR: Record<'page' | 'tile', readonly Slot[]> = {
  page: ['sword', 'heal', 'heal', 'speed', 'fire', 'water', 'block', 'pearl', 'empty'],
  tile: ['heal', 'speed', 'fire', 'water'],
};

/** Each pot's liquid, as a grey step — the bottles are not the subject, their glint is. */
const LIQUID: Partial<Record<Slot, number>> = { heal: 0.5, speed: 0.36, fire: 0.28, water: 0.14 };

/**
 * The glint's colour, on the page only.
 *
 * Vanilla's is the enchantment purple. `tint_glint` swaps it for the potion's own liquid
 * colour, so these are 1.8's potion colours softened to sit on the menu — the setting's whole
 * content is which of these two inks the shimmer is.
 */
const VANILLA_GLINT = '#9a6ad8';
const POT_GLINT: Partial<Record<Slot, string>> = {
  heal: '#d9706b',
  speed: '#86b6dc',
  fire: '#e0a457',
  water: '#6f8fd8',
};

function Pot({
  slot,
  passes,
  glint,
  ink,
}: {
  slot: Slot;
  passes: number;
  glint: boolean;
  ink: string;
}): React.ReactElement {
  return (
    <span className="vprev__pot">
      <span className="vprev__potneck" />
      <span className="vprev__potbody" style={{ background: `rgba(237, 238, 239, ${LIQUID[slot] ?? 0.2})` }}>
        {glint ? (
          <span className="vprev__glint">
            <span className="vprev__glintwash" style={{ background: ink, opacity: (0.14 * passes).toFixed(2) }} />
            {Array.from({ length: passes }, (_, i) => (
              <span
                key={i}
                className="vprev__shine"
                style={{ background: ink, left: `${18 + i * 26}%` }}
              />
            ))}
          </span>
        ) : null}
      </span>
    </span>
  );
}

/**
 * Shiny pots, as a hotbar.
 *
 * - `glint_passes` — how many shimmer bands cross each bottle, and how strong the wash under them
 *   is: one is vanilla's, three is as far as it goes.
 * - `tint_glint` — the bands' ink: vanilla's purple on every pot, or each pot's own colour.
 * - `all_potions` — the water bottle. No effect, so no glint in vanilla; with this on it shines
 *   like the rest, because on most servers that is the pot you were looking for.
 */
export function ShinyPotsPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('shiny_pots');
  const all = settings.all_potions !== false;
  const passes = Math.round(num('glint_passes', settings.glint_passes, 2));
  const tint = settings.tint_glint === true;
  const bar = HOTBAR[dense ? 'tile' : 'page'];
  return (
    <div className={root(dense, 'gprev--pots', className)}>
      <div className="vprev__hotbar">
        {bar.map((slot, i) => (
          <span key={i} className="vprev__slot">
            {slot in LIQUID ? (
              <Pot
                slot={slot}
                passes={passes}
                glint={slot !== 'water' || all}
                ink={dense ? 'rgb(237, 238, 239)' : tint ? POT_GLINT[slot] ?? VANILLA_GLINT : VANILLA_GLINT}
              />
            ) : slot === 'empty' ? null : (
              <span className={`vprev__item vprev__item--${slot}`} />
            )}
          </span>
        ))}
      </div>
      {dense ? null : (
        <Reading>
          {`Every pot shimmers at ${passes}× vanilla’s glint` +
            (tint ? ', each in its own colour' : ', in vanilla’s purple') +
            (all
              ? ' — the water bottle too, and any pot whose effects only live in its lore.'
              : '. The water bottle has no effect, so it stays plain.')}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Clear glass                                                                */
/* -------------------------------------------------------------------------- */

/**
 * Clear glass, as three windows onto the same figure: a glass block, a pane and stained glass.
 *
 * Glass that is drawn sits **over** the figure, a frame and two streaks with a wash behind them,
 * and the figure reads through it at reduced contrast. Cleared glass is gone — only a ghost of
 * its edge is left so you can see where it was — and the figure is at full strength. Glass
 * blocks are always cleared, because that is the mod; the other two follow their switches.
 */
export function ClearGlassPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('clear_glass');
  const panes = settings.clear_panes !== false;
  const stained = settings.clear_stained === true;
  const windows = [
    { label: 'Glass', kind: 'block', clear: true },
    { label: 'Pane', kind: 'pane', clear: panes },
    { label: 'Stained', kind: 'stained', clear: stained },
  ] as const;
  return (
    <div className={root(dense, 'gprev--glass', className)}>
      <div className="vprev__windows">
        {windows.map((w) => (
          <div key={w.kind} className="vprev__wincol">
            {dense ? null : <span className="gprev__rowlabel vprev__winlabel">{w.label}</span>}
            <span className="vprev__window">
              <span className={w.clear ? 'vprev__seen' : 'vprev__seen vprev__seen--behind'} />
              <span
                className={
                  w.clear
                    ? 'vprev__glass vprev__glass--clear'
                    : `vprev__glass vprev__glass--${w.kind}`
                }
              >
                {w.clear ? null : (
                  <>
                    <span className="vprev__streak" />
                    <span className="vprev__streak vprev__streak--b" />
                  </>
                )}
              </span>
            </span>
          </div>
        ))}
      </div>
      {dense ? null : (
        <Reading>
          {'Glass blocks are clear air. ' +
            (panes ? 'Panes are cleared too. ' : 'Panes are drawn as vanilla draws them. ') +
            (stained
              ? 'Stained glass is cleared as well — and with it the team colour it carries.'
              : 'Stained glass keeps its colour, which in Bedwars is a team’s.')}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Nametags                                                                   */
/* -------------------------------------------------------------------------- */

/** One plate: a name over a black ground at the setting's alpha, at the setting's size. */
function Plate({
  name,
  alpha,
  scale,
  shadow,
  dense,
}: {
  name: string;
  alpha: number;
  scale: number;
  shadow: boolean;
  dense: boolean;
}): React.ReactElement {
  // Black, the game's own plate colour, at exactly the alpha the setting stores — which is why the
  // figures stand on a grey ground: on the menu's near-black a black plate is invisible at every
  // value, and the setting with it.
  const plate = { background: `rgba(0, 0, 0, ${alpha.toFixed(2)})` } as CSSProperties;
  if (dense) {
    // Glyphs do not survive 118px; a bar the length of the name does, at the same scale.
    const bar = { width: `${(name.length * 3.2 * scale).toFixed(1)}px`, height: `${(3 * scale).toFixed(1)}px` };
    return (
      <span className="vprev__plate" style={plate}>
        <span className="vprev__name" style={bar}>
          {shadow ? <span className="vprev__namebar vprev__namebar--shadow" /> : null}
          <span className="vprev__namebar" />
        </span>
      </span>
    );
  }
  // The shadow is a second copy of the text, offset — `text-shadow` is one of the properties
  // Ultralight drops (`design/ultralight-notes.md` §3), and this is the shape the game's own
  // shadow is anyway: the same glyphs, darker, one pixel down and right.
  return (
    <span className="vprev__plate" style={{ ...plate, fontSize: `${(13 * scale).toFixed(1)}px` }}>
      <span className="vprev__name">
        {shadow ? <span className="vprev__name--shadow">{name}</span> : null}
        <span className="vprev__nametext">{name}</span>
      </span>
    </span>
  );
}

/**
 * Nametags, as two players: you in third person, and somebody else.
 *
 * - `show_own` — the plate over your own head, which vanilla never draws.
 * - `hide_others` — the other player's plate, gone.
 * - `tag_background`, `tag_scale`, `tag_shadow` — every drawn plate's ground, size and shadow.
 */
export function NametagsPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('nametags');
  const own = settings.show_own !== false;
  const hideOthers = settings.hide_others === true;
  const alpha = num('tag_background', settings.tag_background, 0.25);
  const scale = num('tag_scale', settings.tag_scale, 1);
  const shadow = settings.tag_shadow !== false;
  const you = useVoidStore((s) => s.session?.name ?? 'Player');
  const plate = { alpha, scale, shadow, dense };
  return (
    <div className={root(dense, 'gprev--tags', className)}>
      <div className="vprev__crowd">
        <div className="vprev__who">
          <span className="vprev__over">{own ? <Plate name={you} {...plate} /> : null}</span>
          <span className="gprev__fig">
            <span className="gprev__fighead" />
            <span className="gprev__figbody" />
          </span>
          {dense ? null : <span className="gprev__rowlabel vprev__whoname">You · F5</span>}
        </div>
        <div className="vprev__who">
          <span className="vprev__over">{hideOthers ? null : <Plate name="Steve" {...plate} />}</span>
          <span className="gprev__fig">
            <span className="gprev__fighead" />
            <span className="gprev__figbody" />
          </span>
          {dense ? null : <span className="gprev__rowlabel vprev__whoname">Others</span>}
        </div>
      </div>
      {dense ? null : (
        <Reading>
          {(own ? 'Your own name shows over your head in third person. ' : 'Your own name is not drawn, as in vanilla. ') +
            (hideOthers
              ? 'Everyone else’s is hidden. '
              : `Plates draw at ${Math.round(alpha * 100)}% black, ${scale.toFixed(2)}× vanilla’s size, ` +
                (shadow ? 'with a text shadow.' : 'with no shadow.'))}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* GUI scale                                                                  */
/* -------------------------------------------------------------------------- */

/** Minecraft's auto GUI scale at 1080p — what `0` draws as, since it follows the game. */
const AUTO_SCALE = 4;
/** The screen the diagram is, in real pixels. */
const SCREEN = { w: 1920, h: 1080 };
/** 1.8's hotbar texture, 182 x 22 GUI pixels, and the inventory's 176 x 166. */
const HOTBAR_GUI = { w: 182, h: 22 };
const INVENTORY_GUI = { w: 176, h: 166 };

/**
 * GUI scale, as a 1080p screen with the hotbar and an open inventory at their real sizes.
 *
 * Both boxes are sized from the textures' own GUI pixels times the step, against 1920 x 1080,
 * so the proportions are the game's. A step of 0 hands the choice back to Minecraft, which this
 * page cannot know — it is drawn at the 1080p auto scale as an outline only, the ghost
 * vocabulary for "a size this reaches, not one this mod sets".
 */
export function GuiScalePreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('gui_scale');
  const hot = Math.round(num('hotbar_scale', settings.hotbar_scale, 2));
  const inv = Math.round(num('inventory_scale', settings.inventory_scale, 3));
  const hs = hot || AUTO_SCALE;
  const is = inv || AUTO_SCALE;
  const size = (gui: { w: number; h: number }, s: number): CSSProperties => ({
    width: `${(((gui.w * s) / SCREEN.w) * 100).toFixed(2)}%`,
    height: `${(((gui.h * s) / SCREEN.h) * 100).toFixed(2)}%`,
  });
  return (
    <div className={root(dense, 'gprev--gui', className)}>
      <div className="vprev__screen">
        <span className={hot ? 'vprev__hotbox' : 'vprev__hotbox vprev__follow'} style={size(HOTBAR_GUI, hs)}>
          {Array.from({ length: 9 }, (_, i) => (
            <span key={i} className="vprev__hotcell" />
          ))}
        </span>
        <span className={inv ? 'vprev__invbox' : 'vprev__invbox vprev__follow'} style={size(INVENTORY_GUI, is)}>
          {dense
            ? null
            : Array.from({ length: 4 }, (_, r) => (
                <span key={r} className="vprev__invrow">
                  {Array.from({ length: 9 }, (_, c) => (
                    <span key={c} className="vprev__invcell" />
                  ))}
                </span>
              ))}
        </span>
      </div>
      {dense ? null : (
        <Reading>
          {(hot ? `The hotbar is at GUI scale ${hot}` : 'The hotbar follows Minecraft’s GUI scale') +
            (inv ? `, inventories at ${inv}` : ', inventories follow Minecraft’s') +
            (hot && inv ? '.' : ' — drawn here at 1080p’s auto, 4.') +
            ' Chat, the pause menu and VOID’s own menus are unaffected.'}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Fog                                                                        */
/* -------------------------------------------------------------------------- */

/**
 * Where each fog starts and ends, as fractions of what you could see without it.
 *
 * Schematic, like `litLevel`: the three rows have to be true about *order* — vanilla is the
 * shortest view, reduced is longer, off is the whole way — and about the one stated fact,
 * distance fog's three-quarters start against reduced's last chunk.
 */
const FOG: Record<'distance' | 'water' | 'lava', Record<'vanilla' | 'reduced', readonly [number, number]>> = {
  distance: { vanilla: [0.75, 1], reduced: [0.92, 1] },
  water: { vanilla: [0, 0.3], reduced: [0, 0.7] },
  lava: { vanilla: [0, 0.08], reduced: [0, 0.3] },
};

function fogRow(n: number, kind: keyof typeof FOG, mode: unknown): number[] {
  const m = mode === 'off' || mode === 'reduced' ? mode : 'vanilla';
  return Array.from({ length: n }, (_, i) => {
    if (m === 'off') return ON;
    const [start, end] = FOG[kind][m];
    const x = (i + 0.5) / n;
    const seen = x <= start ? 1 : x >= end ? 0 : 1 - (x - start) / (end - start);
    return OFF + (ON - OFF) * seen;
  });
}

/**
 * Fog, as three rows of what you can see from your eye (left) to the edge of the view (right).
 *
 * A joined ramp per medium. Where it fades is where the fog is; `off` is a flat bar the whole
 * way out. Blindness is not a row because the mod never touches it.
 */
export function FogPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('fog');
  const n = steps(dense);
  const word = (v: unknown) => (v === 'off' ? 'off' : v === 'reduced' ? 'reduced' : 'vanilla');
  const d = word(settings.distance_fog ?? 'reduced');
  const w = word(settings.water_fog ?? 'reduced');
  const l = word(settings.lava_fog ?? 'vanilla');
  return (
    <div className={root(dense, 'gprev--fog', className)}>
      <div className="gprev__gate">
        <CellRow label="Air" cells={fogRow(n, 'distance', d)} joined />
        <CellRow label="Water" cells={fogRow(n, 'water', w)} joined />
        <CellRow label="Lava" cells={fogRow(n, 'lava', l)} joined />
      </div>
      {dense ? null : (
        <Reading>
          {(d === 'off'
            ? 'No distance fog: the world runs to the last chunk and stops. '
            : d === 'reduced'
              ? 'Distance fog starts at the last chunk. '
              : 'Distance fog starts three quarters of the way out, as vanilla’s does. ') +
            (w === 'off'
              ? 'Under water you see as far as on land. '
              : w === 'reduced'
                ? 'Under water, about what Respiration III gives. '
                : 'Under water, vanilla’s murk. ') +
            (l === 'off'
              ? 'In lava you see clearly — the biggest advantage on this page.'
              : l === 'reduced'
                ? 'In lava, a little further than vanilla.'
                : 'In lava, vanilla’s wall of orange.')}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Menu blur                                                                  */
/* -------------------------------------------------------------------------- */

/** The world behind the menu, 16 x 9 cells: ground height per column (rows from the top). */
const MOSAIC_W = 16;
const MOSAIC_H = 9;
const GROUND = [6, 6, 5, 5, 5, 4, 5, 6, 6, 5, 4, 3, 3, 4, 5, 6];

/** The unblurred world, as alphas: sky, a sun, a tree, grass and earth. */
const WORLD: readonly number[] = (() => {
  const out: number[] = [];
  for (let r = 0; r < MOSAIC_H; r++) {
    for (let c = 0; c < MOSAIC_W; c++) {
      const g = GROUND[c]!;
      let a = 0.06 + r * 0.008;
      if (r === 1 && c === 12) a = 0.7;
      if (c === 3 && (r === 3 || r === 4)) a = 0.44;
      if (r === 2 && c >= 2 && c <= 4) a = 0.36;
      if (r === g) a = 0.34;
      if (r > g) a = 0.2 + ((r + c) % 3) * 0.03;
      out.push(a);
    }
  }
  return out;
})();

/**
 * `passes` of a small blur over {@link WORLD}: each pass mixes a cell half-and-half with the mean
 * of its four neighbours.
 *
 * The real thing is a Gaussian run `passes` times, and a small kernel repeated is the same shape
 * — which is the point of drawing it this way rather than as a filter (`filter: blur` is exactly
 * what Ultralight will not do). The kernel is gentle because the world here is only 16 cells
 * wide: one pass softens the edges, ten and the world is a smear of its own average.
 */
function blurred(passes: number): number[] {
  let cur = [...WORLD];
  const cell = (r: number, c: number) =>
    cur[Math.min(MOSAIC_H - 1, Math.max(0, r)) * MOSAIC_W + Math.min(MOSAIC_W - 1, Math.max(0, c))]!;
  for (let p = 0; p < passes; p++) {
    const next = new Array<number>(cur.length);
    for (let r = 0; r < MOSAIC_H; r++) {
      for (let c = 0; c < MOSAIC_W; c++) {
        const around = (cell(r - 1, c) + cell(r + 1, c) + cell(r, c - 1) + cell(r, c + 1)) / 4;
        next[r * MOSAIC_W + c] = cell(r, c) * 0.5 + around * 0.5;
      }
    }
    cur = next;
  }
  return cur;
}

/** Vanilla's dark wash behind a container: `0xC0101010`, three quarters black. */
const VANILLA_DIM = 0.75;

/**
 * Menu blur, as an inventory open over the world it blurs.
 *
 * - `blur_radius` — the world behind the panel, run through that many passes.
 * - `menu_dim` — the dark wash over it, as a fraction of vanilla's.
 * - `blur_fade` — a row of the first moments after the screen opens: the blur easing up over a
 *   fifth of a second, or there from the first frame.
 */
export function MenuBlurPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('menu_blur');
  const radius = Math.round(num('blur_radius', settings.blur_radius, 4));
  const fade = settings.blur_fade !== false;
  const dim = num('menu_dim', settings.menu_dim, 0.5);
  const world = blurred(radius);
  const n = steps(dense);
  const ramp = dense ? 2 : 4;
  const open = Array.from({ length: n }, (_, i) =>
    fade ? OFF + (ON - OFF) * Math.min(1, (i + 1) / ramp) : ON,
  );
  return (
    <div className={root(dense, 'gprev--blur', className)}>
      <div className="vprev__screen">
        <span className="vprev__mosaic">
          {Array.from({ length: MOSAIC_H }, (_, r) => (
            <span key={r} className="vprev__mrow">
              {Array.from({ length: MOSAIC_W }, (_, c) => (
                <span
                  key={c}
                  className="vprev__mcell"
                  style={{ background: `rgba(237, 238, 239, ${world[r * MOSAIC_W + c]!.toFixed(3)})` }}
                />
              ))}
            </span>
          ))}
        </span>
        <span className="vprev__dim" style={{ background: `rgba(10, 11, 12, ${(VANILLA_DIM * dim).toFixed(3)})` }} />
        <span className="vprev__menupanel" />
      </div>
      {dense ? null : <CellRow label="Blur" cells={open} joined />}
      {dense ? null : (
        <Reading>
          {`The world behind the menu is blurred ${radius} ${radius === 1 ? 'pass' : 'passes'} deep` +
            (dim <= 0
              ? ' with none of vanilla’s dark wash on top.'
              : ` under ${Math.round(dim * 100)}% of vanilla’s dark wash.`) +
            (fade ? ' It eases in as the screen opens.' : ' It is there from the first frame.')}
        </Reading>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Time & weather                                                             */
/* -------------------------------------------------------------------------- */

/** Rain streaks, as positions across the dome in % and a length step. */
const RAIN: readonly (readonly [number, number])[] = [
  [12, 30], [20, 52], [28, 18], [35, 60], [43, 34], [50, 12], [57, 48],
  [64, 26], [71, 58], [78, 38], [85, 22], [24, 72], [61, 74], [46, 80],
];

/** Where a body on the sky arc sits, for an angle in degrees from the east horizon. */
function arcAt(deg: number): CSSProperties {
  const rad = (deg * Math.PI) / 180;
  return {
    left: `${(50 - Math.cos(rad) * 42).toFixed(2)}%`,
    top: `${(100 - Math.sin(rad) * 84).toFixed(2)}%`,
  };
}

/**
 * Time and weather, as the sky.
 *
 * - `lock_time` / `time_ticks` — the sun (or, past sunset, the moon) on its arc at the held
 *   time; tick 0 is the east horizon and 12000 the west. Unlocked, the arc carries ghost suns and
 *   no sun of its own: the time is the server's, moving.
 * - `clear_weather` — rain streaks over the dome, and a darker sky, when the weather is left on.
 */
export function TimeWeatherPreview({ dense = false, className }: DiagramProps = {}): React.ReactElement {
  const settings = useModSettings('time_weather');
  const lock = settings.lock_time !== false;
  const ticks = Math.round(num('time_ticks', settings.time_ticks, 6000));
  const clear = settings.clear_weather !== false;
  const deg = (ticks / 24000) * 360;
  const day = deg <= 180;
  const light = lock ? Math.max(0, Math.sin((deg * Math.PI) / 180)) : 0.6;
  const sky = (0.03 + 0.1 * light) * (clear ? 1 : 0.55);
  const streaks = dense ? RAIN.slice(0, 7) : RAIN;
  return (
    <div className={root(dense, 'gprev--sky', className)}>
      <div className="vprev__sky" style={{ background: `rgba(237, 238, 239, ${sky.toFixed(3)})` }}>
        <span className="vprev__track" />
        {lock ? (
          <span className={day ? 'vprev__sun' : 'vprev__moon'} style={arcAt(day ? deg : deg - 180)} />
        ) : (
          [30, 60, 90, 120, 150].map((d) => <span key={d} className="vprev__sun vprev__ghost" style={arcAt(d)} />)
        )}
        {clear
          ? null
          : streaks.map(([x, y], i) => (
              <span key={i} className="vprev__drop" style={{ left: `${x}%`, top: `${y}%` }} />
            ))}
      </div>
      <span className="gprev__fandeg tnum">{lock ? formatSetting('time_ticks', ticks) : 'Server'}</span>
      {dense ? null : (
        <Reading>
          {(lock
            ? `Held at ${formatSetting('time_ticks', ticks)} whatever the server says.`
            : 'The time follows the server.') +
            (clear ? ' Rain, snow and thunder are hidden, and the sky stays bright.' : ' The weather is the server’s too.')}
        </Reading>
      )}
    </div>
  );
}
