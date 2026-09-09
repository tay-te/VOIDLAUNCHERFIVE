/**
 * The overlay's icon renderer: `@void/ui`'s `Icon` drawn from the PNG sprite instead of from
 * inline SVG.
 *
 * ## Why this exists, and why it is one call
 *
 * `packages/ui/src/components/Icon.tsx` has always shipped a seam for this, in as many words:
 *
 * > The in-game bundle should call this once at boot with a sprite-sheet renderer if
 * > Ultralight's partial SVG support turns out to be a problem — see §7 of
 * > `design/ultralight-notes.md`.
 *
 * It has turned out to be a problem, and it had already shipped: the list layout draws
 * `<Icon name={MOD_ICONS[id]} />` on every row and the quick palette draws one on every result,
 * so **twelve mod icons and seven action icons were going through the [risky] path in game** —
 * "strokes, `stroke-linejoin` and non-scaling strokes are the usual casualties", which is every
 * line of every Lucide glyph. Nobody had reported it because a stroke that renders thin or
 * ragged does not look like a bug, it looks like a small icon.
 *
 * Installing the renderer retires inline SVG from the overlay in one call, without touching a
 * single call site. `scripts/build-icons.py` bakes the sheet from Lucide's own 24×24 geometry,
 * so the shapes are the real ones rather than an impression of them, and they are pixels, which
 * cannot be partially supported.
 *
 * ## Names
 *
 * The sheet is keyed by what each cell *is* — `fps`, `keystrokes`, `close` — and `@void/ui` is
 * keyed by what each icon *depicts* — `gauge`, `keyboard`, `close`. {@link SPRITE} is that
 * translation and nothing more.
 *
 * A name with no cell draws a **transparent box of the right size**, not the SVG: `Icon` checks
 * the renderer before it reaches its own paths, so there is no falling back once this is
 * installed. That is the right failure anyway — every unmapped name is a launcher icon this
 * bundle does not render, and a gap where one appeared would be visible, where a neighbouring
 * icon drawn by a wrong offset would not.
 *
 * ## Size
 *
 * `Icon` is called at 13, 14, 16, 18 and 24 px in this bundle, so the cell cannot be a fixed
 * 16 px background and the class-based `.oicon--<name>` offsets in `icons.css` — which the bar
 * uses, at one size — cannot serve here. The geometry is computed per call instead: the sheet
 * is `CELLS` cells wide, so at size *s* it is `CELLS × s` wide and the *i*-th cell starts at
 * `-i × s`. The source cell is 48 device px, so every size below 48 is a downsample and none is
 * ever resampled up.
 */

import type { CSSProperties } from 'react';

import { ICON_NAMES, setIconRenderer, type IconName } from '@/ui';

/**
 * `IconName` → the sprite cell that draws it. Order is `build-icons.py`'s `ICONS`, and the
 * index is the cell's position in the sheet.
 *
 * Keep this in step with that list. It is checked at boot rather than by a type: the script
 * owns the sheet, and a name here with no cell in it would otherwise draw a neighbouring icon,
 * which is the failure mode a hardcoded `background-size` produced before the offsets were
 * generated (see `overlay.css`, "the icons").
 */
export const SHEET_ORDER: readonly string[] = [
  'search',
  'close',
  'grid',
  'list',
  'back',
  'settings',
  'fps',
  'keystrokes',
  'cps',
  'toggle_sprint',
  'crosshair',
  'zoom',
  'fullbright',
  'hitboxes',
  'armor_status',
  'potion_effects',
  'ping',
  'coordinates',
  'layers',
  'sword',
  'users',
  'box',
  'move',
  'chevron-down',
  'sparkle',
  'check',
  'plus',
  'star',
  'heart',
  'eye',
  'play',
  'reset',
  'chevron-right',
  'bed',
  'watermark',
  // Appended, so no existing cell's offset moves — `build-icons.py` writes the sheet in this
  // order and `icons.css` puts `clock` at -560px, which is cell 35.
  'clock',
];

/** How many cells the sheet holds. The width at size *s* is this many *s*. */
export const SHEET_CELLS = SHEET_ORDER.length;

/**
 * The cells the **bar** draws, by class rather than through {@link Icon}.
 *
 * `cell-art.tsx` renders `<span class="oicon oicon--grid">` directly, using the
 * `background-position` rules `icons.css` generates, because those five sit at one fixed size
 * and do not need the per-call geometry the renderer computes. So they are referenced, just not
 * through {@link SPRITE} — and without this list a test that asked "is every cell used?" would
 * call them dead and someone would delete them.
 */
export const BAR_ONLY_CELLS: readonly string[] = ['grid', 'list'];

/**
 * ## Where an icon earns its place, and where it does not
 *
 * Recorded here because it is a judgement rather than a mechanism, and the next person will
 * otherwise put icons everywhere the component allows one.
 *
 * **Yes — the list layout's rows.** A row is a line of text among eight columns of text, and the
 * icon is the only thing that makes one scannable. They were already there; what changed is that
 * they are pixels now instead of the [risky] SVG path.
 *
 * **Yes — the quick palette's results.** Same reason: a flat list of titles, where the icon is
 * what tells a mod apart from an action at a glance.
 *
 * **No — the grid's tiles.** A tile draws a *preview of what the mod draws*: the fps tile shows
 * a live frame rate, the keystrokes tile shows the keycaps, the watermark tile shows the mark
 * itself. That is strictly more information than a symbol, and it is the whole reason the grid
 * reads as a mod list for this game rather than for any application (`TilePreview.tsx` opens
 * with that argument). An icon here would replace something better.
 *
 * **No — the mod page's head.** The page already draws the preview at 2.6x, an inch below the
 * title. An icon beside a 30px title would be a second, smaller, worse copy of the identity the
 * preview is already carrying.
 */

/**
 * The translation. Left is `@void/ui`'s name, right is the sheet's. **Total over `ICON_NAMES`**,
 * and the type says so.
 *
 * It was partial for about an hour — the eleven names the overlay's call sites obviously used —
 * and that was wrong for a reason worth keeping: `resolveLoadoutIcon` maps a loadout's own
 * `icon` string onto `ICON_NAMES`, so **the domain is the whole set, not the subset you can find
 * by grepping**. Four names were reachable and uncovered (`check` on the HUD editor's active
 * tool, `plus` on Loadouts' new-loadout button, `star` and `bed` through that resolution), and
 * each would have drawn an empty box — the exact silent-miss shape of §15 in
 * `design/rendering-invariants.md`, built by the fix for one of the others.
 *
 * A partial mapping cannot be gated by a test, only by a guess. A total one can: see
 * `test/registry.test.tsx`, which checks both directions — every name has a cell, and every cell
 * is claimed by a name or by {@link BAR_ONLY_CELLS}.
 */
export const SPRITE: Record<IconName, string> = {
  search: 'search',
  close: 'close',
  settings: 'settings',
  'arrow-left': 'back',
  'chevron-down': 'chevron-down',
  move: 'move',
  layers: 'layers',
  users: 'users',
  box: 'box',
  sword: 'sword',
  sparkle: 'sparkle',
  // The twelve of `MOD_ICONS`, plus the mark's.
  gauge: 'fps',
  keyboard: 'keystrokes',
  'cursor-click': 'cps',
  wifi: 'ping',
  compass: 'coordinates',
  shield: 'armor_status',
  flask: 'potion_effects',
  bolt: 'toggle_sprint',
  sun: 'fullbright',
  cube: 'hitboxes',
  zoom: 'zoom',
  crosshair: 'crosshair',
  watermark: 'watermark',
  // The rest, by identity. `play`, `reset` and `chevron-right` are launcher-only today; they are
  // in the sheet so the mapping is total, which is what makes the miss testable rather than
  // guessable, at about 3 KB.
  check: 'check',
  plus: 'plus',
  star: 'star',
  heart: 'heart',
  eye: 'eye',
  play: 'play',
  reset: 'reset',
  'chevron-right': 'chevron-right',
  bed: 'bed',
  clock: 'clock',
};

/** The inline geometry for one cell at one size. Exported for the test that pins the mapping. */
export function cellStyle(cell: number, size: number): CSSProperties {
  return {
    width: `${size}px`,
    height: `${size}px`,
    backgroundSize: `${SHEET_CELLS * size}px ${size}px`,
    backgroundPosition: `${-cell * size}px 0`,
  };
}

/**
 * Point `@void/ui`'s `Icon` at the sheet, for the life of the process.
 *
 * Called once from `main.tsx`, before the first render. Returns the names it could not place,
 * which is always empty in a correct build — it is a return value rather than a throw because a
 * page that dies at boot shows nothing at all, and the honest failure for a missing icon is the
 * SVG it was drawing yesterday.
 */
export function installSpriteIcons(): IconName[] {
  const missing: IconName[] = [];
  const index = new Map<string, number>(SHEET_ORDER.map((name, i) => [name, i]));

  // Only `name`, `size`, `className` and `style` are read. The rest of `IconProps` is
  // `SVGProps`, which a `<span>` cannot take: forwarding it would be a type error at best and
  // an invalid attribute on the element at worst. No call site in this bundle passes one.
  setIconRenderer(({ name, size = 16, className, style }) => {
    const cell = index.get(SPRITE[name] ?? '');
    // Unmapped: a transparent box of the right size. The renderer cannot fall back to the SVG
    // path — `Icon` checks the renderer first — so this keeps the layout and makes the absence
    // visible, which a neighbouring icon drawn by a wrong offset would not be.
    const geometry: CSSProperties =
      cell === undefined ? { width: `${size}px`, height: `${size}px` } : cellStyle(cell, size);
    return (
      <span
        className={['oicon', cell === undefined && 'oicon--unmapped', className]
          .filter(Boolean)
          .join(' ')}
        style={{ ...geometry, ...(style as CSSProperties | undefined) }}
        aria-hidden="true"
      />
    );
  });

  for (const name of ICON_NAMES) {
    const cell = SPRITE[name];
    if (cell !== undefined && !index.has(cell)) missing.push(name);
  }
  return missing;
}
