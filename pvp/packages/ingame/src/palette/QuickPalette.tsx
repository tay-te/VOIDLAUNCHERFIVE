/**
 * Overlay — Quick palette · frame `244:1900`.
 *
 * ⌘K / Ctrl-K opens it over whichever screen is up, and so does the magnifier in
 * the top bar. Type, get ranked mods, loadouts and actions; `↑↓` moves, `↵` takes
 * the selected row's primary action and closes, `⌘↵` takes its secondary — a mod's
 * toggle — and **stays open**, `esc` closes. Which action is which, and why the
 * primary does not toggle, is settled in `commands.ts`.
 *
 * `@void/ui` draws the shell, the rows and the footer; selection and key
 * routing stay here, because the palette does not know what its results mean.
 */

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Palette,
  PaletteFooter,
  PaletteInput,
  PaletteResult,
  PaletteSeam,
  PaletteSection,
} from '@/ui';
import { useVoidStore } from '@/store/store';
import { buildCommands, type Command } from './commands';
import { rank } from './fuzzy';

/** Rows shown under ACTIONS before the rest fall through to ALSO. */
const ACTION_LIMIT = 3;
const ALSO_LIMIT = 2;

/**
 * The footer hints. The frame's four chips, with two words corrected: `run` and
 * `settings` named an Enter that toggled and a ⌘Enter that navigated, and both keys
 * now mean the other thing.
 */
const HINTS = [
  { keys: '↑↓', word: 'move' },
  { keys: '↵', word: 'open' },
  { keys: '⌘↵', word: 'toggle' },
  { keys: 'esc', word: 'close' },
];

/**
 * The sentence under the list.
 *
 * A four-letter chip can name a key's verb but not its shape, and the two facts a
 * player has no way to guess are exactly the shape: that a result goes *to* the thing
 * it names rather than changing it, and that the toggle leaves the palette up so
 * several can be flipped in a row. Same voice, same 9px label tier and same `·`
 * spacing as the overlay's own hint line (`MODS_HINT_GRID`), which is the only other
 * place in the in-game UI that explains a key in a sentence.
 */
export const PALETTE_HINT =
  '↵ opens a mod, or runs the action named   ·   ⌘↵ toggles a mod without closing';

export function QuickPalette() {
  const store = useVoidStore();
  const setPaletteOpen = useVoidStore((s) => s.setPaletteOpen);
  const loadout = useVoidStore((s) => s.loadout);
  const [query, setQuery] = useState('');
  const [cursor, setCursor] = useState(0);
  const rootRef = useRef<HTMLDivElement | null>(null);

  // Rebuilt on every store change so the "currently off → on" preview is live.
  const commands = useMemo(() => buildCommands(store), [store]);

  const { actions, also, flat } = useMemo(() => {
    const ranked = rank(commands, query);
    const a: Command[] = [];
    const b: Command[] = [];
    // Two rows reading the same words are one row as far as the player is concerned, and five
    // is the whole list — a command set that can produce a title twice fills the palette with
    // it. `load` used to return four rows of `Turn on in UHC loadout`, distinguishable only by
    // a subtitle nobody reads when every line above it says the same thing. `commands.ts` now
    // names the mod in the title so this should never fire; it is here so that a command added
    // later cannot quietly take the palette back.
    const seen = new Set<string>();
    for (const command of ranked) {
      if (seen.has(command.title)) continue;
      seen.add(command.title);
      if (command.section === 'actions' && a.length < ACTION_LIMIT) a.push(command);
      else if (b.length < ALSO_LIMIT) b.push(command);
      if (a.length >= ACTION_LIMIT && b.length >= ALSO_LIMIT) break;
    }
    return { actions: a, also: b, flat: [...a, ...b] };
  }, [commands, query]);

  useEffect(() => {
    setCursor(0);
  }, [query]);

  // PaletteInput does not forward a ref, so focus the field it renders. The
  // query must own the keyboard the moment the palette opens: with it focused,
  // `__hasFocus()` is true and Java forwards Escape to the page instead of
  // closing the screen (§6.3).
  useEffect(() => {
    rootRef.current?.querySelector<HTMLInputElement>('.v-palette__query input')?.focus();
  }, []);

  const close = () => setPaletteOpen(false);

  /**
   * Take a row's action. `alt` is ⌘/Ctrl held — the secondary.
   *
   * The two halves differ in more than which function they call: the **primary closes
   * and the secondary does not**. The primary has taken the player somewhere, so the
   * palette has done its job and is in the way; the secondary has changed a value on a
   * row that is still on screen, and closing over it would put us back at the bug this
   * came from — a state change with nothing to see. Staying open also means a second
   * ⌘↵ flips it back, and a third row can be flipped without retyping.
   */
  const run = (command: Command, alt: boolean) => {
    const state = useVoidStore.getState();
    if (alt) {
      // No secondary is not an error: a row with nothing to toggle simply stays put.
      command.alt?.(state);
      return;
    }
    command.run(state);
    close();
  };

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setCursor((c) => Math.min(flat.length - 1, c + 1));
      return;
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      setCursor((c) => Math.max(0, c - 1));
      return;
    }
    if (e.key === 'Enter') {
      e.preventDefault();
      const alt = e.metaKey || e.ctrlKey;
      // An *unmodified* Enter that started inside a row has already been dealt with:
      // `PaletteResult` puts a keydown handler on the row and `tabIndex={0}` on the selected
      // one, so handling it again here ran the command twice — on a toggle, a flip and a flip
      // back: nothing happens, twice. The field owns the keyboard in practice (it is focused
      // from the moment the palette opens), so this only fires for a row reached by Tab, but
      // "the click and the keyboard do the same thing" is not a property that survives being
      // true only on the path anybody happened to test.
      //
      // A modified Enter is deliberately *not* excluded: the row's own handler now ignores it
      // (`@void/ui`), precisely so that the secondary can only ever be decided here.
      if (!alt && (e.target as HTMLElement | null)?.closest?.('.v-palette__row')) return;
      const command = flat[cursor];
      if (command) run(command, alt);
      return;
    }
    if (e.key === 'Escape') {
      // Stop it reaching MenuLayer, which would close the whole screen.
      e.preventDefault();
      e.stopPropagation();
      close();
    }
  };

  const row = (command: Command, index: number) => (
    <PaletteResult
      key={command.id}
      icon={command.icon}
      title={command.title}
      selected={index === cursor}
      keys={command.kbd}
      onMouseEnter={() => setCursor(index)}
      onSelect={() => run(command, false)}
      sub={
        command.sub && (
          <>
            {command.sub}
            {command.subAccent && <span className="palette-sub-accent">{command.subAccent}</span>}
          </>
        )
      }
    />
  );

  return (
    <div className="palette-layer" ref={rootRef} onKeyDown={onKeyDown}>
      {/* One flat dim; the authored blur(3px) is not available (§1). */}
      <div className="palette-layer__dim" onClick={close} />
      <Palette className="v-panel--enter" aria-label="Quick palette">
        {/* One caret, and it is the engine's.
            `showCaret` draws the kit's own 2 x 22 blinking bar *beside* a real `<input>` that
            already has the engine's caret — two cursors a pixel apart on their own clocks,
            which is what "two of the flashing cursor things" was. The kit keeps it for the
            gallery stills, where there is no live field to own one.

            Measured in game before choosing: Ultralight paints and blinks a caret in a focused
            `<input>` perfectly well (WebKit 615), at the real text position, and it is the only
            one of the two that can be right — the decorative bar is positioned off an invisible
            copy of the query, so it can only ever sit at the *end* of the text, and Left/Home/a
            click all move the real caret away from there.

            What went wrong last time is that the bar was 22px tall and *in flow*, so it was
            holding `.v-palette__query` open; turning it off collapsed that column to zero height
            and `overflow: hidden` clipped the engine's caret and the placeholder away with it.
            The column now states its own height (`@void/ui`, 09-palette.css), so neither caret
            is load-bearing and this prop is free to be what it says it is.

            Cost, measured on the GL driver with the palette open, focused and untouched: **2
            paints/s and 2 presents/s**, 7-14 ms a paint depending on what is under the palette,
            against 0/0 for an idle menu — and back to 0/0 on the frame the palette closes.

            That 2/s is not the price of *this* caret, it is the price of a focused text field.
            Measured, same conditions, with `caret-color: transparent` so the engine draws
            nothing at all: still 2 paints/s, still 2 presents/s. WebCore's blink timer
            invalidates the caret rect whatever colour it is, so suppressing the engine's caret
            in order to draw a cheaper one buys back nothing — a decorative bar would cost the
            same 2/s, plus its own animation if it blinked, and would sit in the wrong place.
            There is no cheaper caret to be had here; the only cheaper option is no caret, which
            is the bug this is fixing. */}
        <PaletteInput
          value={query}
          onChange={setQuery}
          showCaret={false}
          placeholder="Ask VOID anything"
          spellCheck={false}
        />
        <PaletteSeam />

        {flat.length === 0 ? (
          <div className="palette-empty">Nothing matches “{query}”.</div>
        ) : (
          <>
            {actions.length > 0 && (
              <PaletteSection caption="Actions">
                {actions.map((command, index) => row(command, index))}
              </PaletteSection>
            )}
            {also.length > 0 && (
              <PaletteSection caption="Also">
                {also.map((command, index) => row(command, actions.length + index))}
              </PaletteSection>
            )}
          </>
        )}

        <div className="palette-spacer" />
        <div className="palette-hint">{PALETTE_HINT}</div>
        <PaletteSeam />
        <PaletteFooter hints={HINTS} loadout={loadout?.name ?? '—'} />
      </Palette>
    </div>
  );
}
