/**
 * Mods — the library.
 *
 * The frame (`design/screens/launcher/Launcher-Mods.png`) is a 180px rail of categories
 * over the profile list, and a five-column grid of 234 × 321 cards whose square preview
 * is the card's own width. Two rows fill the content panel exactly at 1600 × 980 — the
 * row height is derived from the shell's bands in `local/app.css`, so a third row is
 * always a scroll away rather than a squeeze.
 *
 * Two things the earlier pass carried are gone because the frame does not have them:
 * the 30px `Mods` title and the in-panel search field. The panel's header line is the
 * library's own count on the left and the sort on the right, and searching a mod by
 * name is the ⌘K palette's job — it already opens a mod's setup page and toggles it by
 * name (`features/CommandPalette`). Losing the field bought the grid 60px of height,
 * which is most of what made the port read as cramped next to the frame.
 *
 * Two rules from the contract shape the card:
 *
 *  - §1 "Category hues" — the card sets `--hue` from its mod's category, and every
 *    accent underneath (the preview mark when the mod is on, the toggle's live track,
 *    the hover lift's warm field) reads `var(--hue, var(--accent))`. Nothing here names
 *    `--accent` directly.
 *  - §1 "the accent rule" — a mod that is **off** is monochrome. Colour on this screen
 *    means "this is on" or "this is selected", never "this is a mod".
 *
 * A card opens that mod's setup page (`screens/ModSetup`); the size-sm toggle on it is
 * the only thing that writes straight through to the loadout, because on/off is not a
 * property, it is the mod.
 */

import { useMemo, useState, type CSSProperties, type ReactElement } from 'react';

import { Toggle } from '../local/controls';
import type { ModId } from '../local/protocol';
import {
  FILTER_TABS,
  MOD_GRID_ORDER,
  MOD_REGISTRY,
  type FilterTab,
  categoryOf,
  effectiveState,
  enabledCount,
  hueOf,
  matchesTab,
} from '../local/registry';
import { useLoadouts } from '../stores/loadouts';
import { useUi } from '../stores/ui';

/* -------------------------------------------------------------------------- */
/* Card previews                                                              */
/* -------------------------------------------------------------------------- */

/**
 * What a card's preview shows: **what the mod draws in game**, in miniature.
 *
 * Not an icon. The Figma (`289:1141`) gives every card a small picture of the mod's own
 * output — the FPS counter's numeral over its unit, the keystroke cluster's six keycaps,
 * the armour row's four durability bars, the crosshair's nine cells — and a grid of ten
 * line-art glyphs reads flat and samey where that grid reads like a library of things
 * that do something. The geometry below is the file's, cell for cell.
 *
 * Every one of them is **monochrome**, which is §1's accent rule: a mod sitting in the
 * library is not a live value, so tinting ten previews by category would make the hue
 * decoration. The single exception is the crosshair's centre dot — that dot *is* the
 * mod's live value, and it takes `var(--hue)`.
 *
 * They are stills. The launcher has no game to read from, and a number that pretended to
 * be state would be the one dishonest thing on the screen.
 */

/** `142` over `FPS` — the file's 36px numeral in a 45px box over an 11px unit. */
function Numeral({ value, unit }: { value: string; unit: string }) {
  return (
    <span className="modcard__readout">
      <span className="modcard__num">{value}</span>
      <span className="modcard__unit">{unit}</span>
    </span>
  );
}

/** The keystroke cluster: W over A S D over two wide mouse keys (file: 26px keys). */
function Keycaps() {
  return (
    <span className="mp mp--keys" aria-hidden="true">
      <span className="mp__krow">
        <i />
      </span>
      <span className="mp__krow">
        <i />
        <i />
        <i />
      </span>
      <span className="mp__krow mp__krow--wide">
        <i />
        <i />
      </span>
    </span>
  );
}

/** Four durability bars, 56 × 8 on a 20 step, filled 40 / 52 / 28 / 16 (file). */
function Bars() {
  return (
    <span className="mp mp--bars" aria-hidden="true">
      {[40, 52, 28, 16].map((fill) => (
        <i key={fill}>
          <b style={{ width: fill }} />
        </i>
      ))}
    </span>
  );
}

/** The crosshair: four cells per arm at ±22 and ±30, and a live centre dot. */
function Crosshair() {
  return (
    <span className="mp mp--cross" aria-hidden="true">
      {[
        [0, -30],
        [0, -22],
        [0, 22],
        [0, 30],
        [-30, 0],
        [-22, 0],
        [22, 0],
        [30, 0],
      ].map(([x, y]) => (
        <i key={`${x}:${y}`} style={{ marginLeft: x, marginTop: y }} />
      ))}
      {/* The centre is the mod's live value, so it — and nothing else here — is hue. */}
      <i className="mp__live" />
    </span>
  );
}

/** Two effect rows: a cell, a name and a countdown (file: 30px apart, 8px cell). */
function Effects({ rows }: { rows: readonly [string, string][] }) {
  return (
    <span className="mp mp--rows" aria-hidden="true">
      {rows.map(([name, value]) => (
        <span key={name} className="mp__row">
          <i />
          <span className="mp__name">{name}</span>
          <span className="mp__value tnum">{value}</span>
        </span>
      ))}
    </span>
  );
}

/** A 6 × 6 cell matrix over a caption — the file's sprint glyph. */
const SPRINT_GLYPH = ['001100', '011110', '111111', '001100', '001100', '010010'];

function CellGlyph({ rows, caption, size }: { rows: readonly string[]; caption?: string; size: number }) {
  return (
    <span className="mp mp--glyph" aria-hidden="true">
      <span
        className="mp__grid"
        style={{ '--mp-cell': `${size}px`, '--mp-cols': rows[0]?.length ?? 0 } as CSSProperties}
      >
        {rows.map((row, y) =>
          [...row].map((bit, x) => (
            <i key={`${x}:${y}`} className={bit === '1' ? 'is-lit' : undefined} />
          )),
        )}
      </span>
      {caption ? <span className="modcard__unit">{caption}</span> : null}
    </span>
  );
}

/** A 5 × 5 of 18px cells, all lit — Fullbright's preview in the file. */
const FULL_GLYPH = ['11111', '11111', '11111', '11111', '11111'];

/**
 * A mark in the corner of a screen, which is what the watermark mod is.
 *
 * It had no arm in the old `switch` and therefore drew `FULL_GLYPH` — Fullbright's art, on the
 * watermark's card, since the fallthrough arm was `case 'fullbright': default:`. That shipped;
 * nobody saw it, because a wrong picture is still a picture. Same shape as `MOD_ICONS.watermark`
 * in `@void/ui`, which is the glyph the row layout already draws for it.
 */
const MARK_GLYPH = ['11111', '10001', '10001', '11001', '11111'];

/**
 * Every card's preview, by mod.
 *
 * A `Record<ModId, …>` rather than a `switch`, and the difference is a bug this table actually
 * had. It was a switch whose last arm read `case 'fullbright': default:` — so every mod the
 * switch did not name fell through to Fullbright's glyph. Adding the fourteenth mod
 * (`direction`) did exactly that: the launcher drew a 5x5 of lit cells on its card and nothing
 * failed, because a `default` arm is a lookup miss that produces a *plausible* answer.
 *
 * `packages/ingame` had the same shape in `TilePreview` and it was removed for the same reason;
 * this copy was missed because it lives in the other application. Now the type is total over
 * `ModId`, so a mod added to `schema/mods/` does not compile until it has a card.
 *
 * These stay deliberately **static**, unlike the overlay's. The launcher has no game running
 * behind it and no live values to read: a card here answers "which mod is this", which is the
 * thumbnail's job, and `packages/ingame/src/mods/types.ts` explains why that is a different
 * question from "what will this look like".
 */
const PREVIEWS: Record<ModId, () => ReactElement> = {
  fps: () => <Numeral value="142" unit="fps" />,
  cps: () => <Numeral value="6.2" unit="cps" />,
  zoom: () => <Numeral value="2.0×" unit="zoom" />,
  hitboxes: () => <Numeral value="3.2" unit="blocks" />,
  ping: () => <Numeral value="42" unit="ms" />,
  keystrokes: () => <Keycaps />,
  armor_status: () => <Bars />,
  crosshair: () => <Crosshair />,
  watermark: () => <CellGlyph rows={MARK_GLYPH} size={18} />,
  potion_effects: () => (
    <Effects
      rows={[
        ['Speed II', '1:24'],
        ['Strength', '0:42'],
      ]}
    />
  ),
  coordinates: () => (
    <Effects
      rows={[
        ['X', '118'],
        ['Z', '−402'],
      ]}
    />
  ),
  // The facing over its angle, which is what the chip draws. Static here, live in the overlay.
  direction: () => <Numeral value="NE" unit="45°" />,
  toggle_sprint: () => <CellGlyph rows={SPRINT_GLYPH} caption="sprint" size={10} />,
  fullbright: () => <CellGlyph rows={FULL_GLYPH} size={18} />,
};

function Preview({ id }: { id: ModId }): ReactElement {
  return PREVIEWS[id]();
}

/** How many mods sit under each filter tab — the count the rail prints. */
function countFor(tab: FilterTab): number {
  return MOD_GRID_ORDER.filter((id) => matchesTab(id, tab)).length;
}

/** A tab's hue. `All` has no category of its own, so it keeps the default accent. */
function tabHue(tab: FilterTab): string | undefined {
  const match = MOD_GRID_ORDER.find((id) => tab !== 'All' && matchesTab(id, tab));
  return match ? hueOf(match) : undefined;
}

function ModCard({
  id,
  on,
  onOpen,
  onToggle,
}: {
  id: ModId;
  on: boolean;
  onOpen: () => void;
  onToggle: (next: boolean) => void;
}) {
  const entry = MOD_REGISTRY[id];
  return (
    <article
      className={`modcard${on ? ' is-on' : ''}`}
      style={{ '--hue': hueOf(id) } as CSSProperties}
    >
      <button type="button" className="modcard__open" onClick={onOpen}>
        <span className="modcard__preview">
          <span className="modcard__field" aria-hidden="true" />
          <Preview id={id} />
        </span>
        <span className="modcard__foot">
          <span className="modcard__text">
            <span className="modcard__name">{entry.label}</span>
            <span className="modcard__cat">{categoryOf(id)}</span>
          </span>
        </span>
      </button>
      <Toggle
        size="sm"
        className="modcard__toggle"
        checked={on}
        onChange={onToggle}
        label={`${entry.label} — ${on ? 'on' : 'off'}`}
      />
    </article>
  );
}

export function ModsScreen() {
  const active = useLoadouts((s) => s.active);
  const library = useLoadouts((s) => s.library);
  const switchTo = useLoadouts((s) => s.switchTo);
  const setMod = useLoadouts((s) => s.setMod);
  const openModSetup = useUi((s) => s.openModSetup);

  const [tab, setTab] = useState<FilterTab>('All');

  const visible = useMemo(() => MOD_GRID_ORDER.filter((id) => matchesTab(id, tab)), [tab]);
  const on = active ? enabledCount(active) : 0;

  return (
    <div className="mods">
      <div className="mods__body">
        <nav className="rail" aria-label="Mod categories and loadouts">
          <p className="eyebrow">Categories</p>
          <ul className="rail__list">
            {FILTER_TABS.map((id) => (
              <li key={id}>
                <button
                  type="button"
                  className={`rail__row${id === tab ? ' is-on' : ''}`}
                  style={{ '--hue': tabHue(id) } as CSSProperties}
                  aria-current={id === tab}
                  onClick={() => setTab(id)}
                >
                  <span className="rail__label">{id}</span>
                  <span className="rail__count tnum">{countFor(id)}</span>
                </button>
              </li>
            ))}
          </ul>

          <hr className="rail__divider" />

          {/* §6: a bundle of mods is a **Loadout**. The frame says PROFILES because it
              predates that decision; the word here is the contract's, not the frame's. */}
          <p className="eyebrow">Loadouts</p>
          <ul className="rail__list">
            {library.map((loadout) => (
              <li key={loadout.id}>
                <button
                  type="button"
                  className={`rail__row${loadout.id === active?.id ? ' is-on' : ''}`}
                  aria-current={loadout.id === active?.id}
                  onClick={() => void switchTo(loadout.id)}
                >
                  <span className="rail__dot cell" aria-hidden="true" />
                  <span className="rail__label">{loadout.name}</span>
                </button>
              </li>
            ))}
            {library.length === 0 ? <li className="rail__empty">No loadouts yet</li> : null}
          </ul>
        </nav>

        <div className="modcol">
          {/* The frame's header line, which belongs to the grid column rather than to
              the panel: the count on the left, the sort on the right. It stays put
              while the grid under it scrolls. */}
          <header className="modcol__head">
            <p className="eyebrow tnum">
              {MOD_GRID_ORDER.length} mods · {on} enabled
            </p>
            <p className="eyebrow">Recently used</p>
          </header>

          <div className="modgrid">
            {active
            ? visible.map((id) => (
                <ModCard
                  key={id}
                  id={id}
                  on={effectiveState(active, id).on === true}
                  onOpen={() => openModSetup(id)}
                  onToggle={(next) => void setMod(id, { on: next })}
                />
              ))
            : null}
            {active && visible.length === 0 ? (
              <p className="modgrid__empty">Nothing under {tab}.</p>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
