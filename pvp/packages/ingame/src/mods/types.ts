/**
 * What a mod has to supply before it can appear in the client — the art contract.
 *
 * ## Why this is a contract rather than four tables
 *
 * A mod's two pictures used to live in four separate places, each keyed by mod id, each
 * maintained by hand and each silent when a mod was missing from it:
 *
 *   · `TilePreview.tsx` — a thirteen-arm `switch` whose `default` drew the mod's id as text
 *   · `ModSettingsScreen.tsx` — `LIVE_WIDGETS`, a `Record<ModId, ComponentType>`
 *   · `ModSettingsScreen.tsx` — `PREVIEW_ZOOM`, a `Partial<Record<ModId, number>>`
 *   · `@void/ui`'s `Icon.tsx` — `MOD_ICONS`, in a different package again
 *
 * Four tables in three files in two packages is survivable at thirteen mods and is the whole
 * project at fifty. `docs/mod-roster.md` §7 plans five waves of them, Wave 2 alone being nine
 * mods "all S, all the same shape". Nine mods against four tables is thirty-six edits, and the
 * failure mode of missing one is not a compile error — it is a mod that renders its own id in
 * a box, in game, in front of a player.
 *
 * So the tables are gone and a mod declares itself **once**, in `mods/<id>.tsx`, as one
 * {@link ModArt}. `mods/index.ts` asserts the set is exhaustive over `ModId`, which turns
 * "somebody forgot the thumbnail" from a thing you find in game into a thing that will not
 * compile.
 *
 * ## The two pictures are different jobs
 *
 * This is the distinction the old split got right and is worth keeping explicit, because the
 * temptation with one descriptor is to collapse them:
 *
 * **{@link ModArt.Thumbnail} answers "which mod is this".** It is 145px in a grid of thirteen
 * siblings, seen at a glance, and its job is identification. It is monochrome — `--hue` marks a
 * live value or the selected item and nothing else (`design/quiet-cell-system.md` §1), and §1
 * is explicit that the rule is absolute on the menu's own surface, "the mod tiles included".
 *
 * **{@link ModArt.Preview} answers "what will this look like".** It is the subject of a page
 * the player opened to configure the mod, so it has to *move when a setting moves* — that is
 * the whole point, and `test/preview.test.tsx` enumerates every setting of every mod to prove
 * it. Where the mod draws HTML, this is the identical component `HudLayer` places, not a
 * preview-shaped copy of it, so the two cannot drift.
 *
 * ## Dynamic unless it genuinely cannot be
 *
 * Every preview reads the live loadout through `useModSettings`, and the four mods that draw
 * into the *world* rather than onto the page — Fullbright, Zoom, Hitboxes, Toggle sprint —
 * are diagrams fed by the game's own numbers, not pictures that ignore them
 * (`menu/gameplay-previews.tsx` opens with why that is a different promise and what it still
 * guarantees). {@link ModArt.staticPreview} is the escape hatch and it demands a written
 * reason, because "this one cannot be dynamic" is a claim that should have to be argued.
 */

import type { ComponentType } from 'react';

import type { ModId } from '@/bridge/protocol';
import type { HudWidgetProps } from '@/hud/widgets';

/** Props a grid thumbnail is drawn with. */
export interface ThumbnailProps {
  /**
   * Multiplier on every cell edge. 1 is the tile.
   *
   * A cell is a `<span>` of a stated width, so it is the one thing here that cannot be scaled
   * from CSS — hence a number rather than a class. Type around the art scales in CSS, which is
   * what CSS is for.
   */
  scale?: number;
}

/**
 * Everything the client needs to draw one mod, declared in one place.
 *
 * The type parameter is not decoration: `ModArt<'fps'>` in `mods/fps.tsx` is what stops a
 * copy-pasted module from declaring `id: 'fps'` while living in `cps.tsx`, which is the
 * single most likely mistake when a new mod is written by copying its neighbour.
 *
 * **Not here: the mod's icon.** It is in the schema, and there is nothing left to declare.
 *
 * Worth stating, because it is the one thing a reader will look for in this interface. The
 * icon is identity rather than art — *two* applications need it, the in-game overlay's list
 * layout and quick palette and the desktop launcher's `ModSetup` and command palette, and
 * neither can import the other — so it belongs where the rest of a mod's identity already
 * lives. `mods/<id>.json` carries `icon`, `@void/protocol` generates `MOD_ICON_NAMES` from
 * the shipped registry, and `@void/ui` publishes that as `MOD_ICONS` narrowed to its own
 * `IconName`. A mod adding itself to the schema arrives in both applications' lists with a
 * mark on it and no edit anywhere near this file.
 *
 * This paragraph used to predict that change and call it Wave 0 work; it has landed, and the
 * argument it made still holds — declaring the icon *here as well* would be two sources of
 * truth with a test between them, which `docs/mod-roster.md` §9 names as the problem rather
 * than the fix. The narrowing is the part worth knowing about downstream: the schema
 * constrains `icon` to a pattern, which cannot know what `@void/ui` can draw, so `Icon.tsx`
 * closes it with one `satisfies Record<ModId, IconName>` and an undrawable name is a type
 * error rather than an empty box on thirteen rows.
 */
export interface ModArt<I extends ModId = ModId> {
  /** The mod's snake_case id; equals the file's own name and the key it is registered under. */
  readonly id: I;

  /**
   * The grid tile's art: a small picture of what the mod draws.
   *
   * Not an icon. The frames put a *sample of the HUD* on a tile — `142 / FPS`, the W-ASD
   * keycap cluster, four armour arches — and that is the whole reason the grid reads as a mod
   * list for this game rather than for any application.
   *
   * Monochrome, always (§1). It may read live store values — the FPS tile shows the real
   * figure — and it may read the mod's own settings, but it may not read the mod's *colour*:
   * the tile is chrome, and the page is where a colour is chosen and shown.
   */
  readonly Thumbnail: ComponentType<ThumbnailProps>;

  /**
   * The mod page's preview: the real thing, moving with its settings.
   *
   * For a mod that draws HTML this is the identical component `HudLayer` places. For one that
   * draws into the world it is a diagram from `menu/gameplay-previews.tsx`, fed by the same
   * numbers the game uses.
   */
  readonly Preview: ComponentType<HudWidgetProps>;

  /**
   * How much bigger than life the preview draws, before the mod's own `scale`.
   *
   * A HUD widget is sized for a 1458px view seen at arm's length over a match; the preview
   * frame is ~300px tall and the player is reading it. One number for all of them would be
   * wrong for all of them, so each mod says what it needs and why. Absent means 1.
   */
  readonly previewZoom?: number;

  /**
   * Why this mod's preview does **not** move with its settings.
   *
   * Absent for every mod today, and the goal is that it stays that way. It exists so that a
   * mod which genuinely cannot be previewed dynamically has somewhere to say so in a sentence
   * a reviewer can disagree with, rather than shipping a still picture and hoping nobody
   * compares it to the game.
   */
  readonly staticPreview?: string;
}

/**
 * Declare a mod's art.
 *
 * A plain identity function, and it earns its place twice: it pins `I` to the literal id so
 * `ModArt<'fps'>` is inferred rather than widened to `ModArt<ModId>`, and it gives every module
 * in `mods/` the same one-line shape, which is what makes thirteen of them scannable and will
 * make fifty of them survivable.
 */
export function defineMod<I extends ModId>(art: ModArt<I>): ModArt<I> {
  return art;
}
