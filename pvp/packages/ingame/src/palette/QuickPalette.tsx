/**
 * Overlay — Quick palette · frame `244:1900`.
 *
 * ⌘K / Ctrl-K opens it over whichever screen is up. Type, get ranked mods,
 * loadouts and actions, `↵` runs the highlighted one, `⌘↵` opens its settings
 * instead, `esc` closes. The selected row previews the state change inline —
 * `currently off  →  on` — which is only honest because the toggle is
 * synchronous and in-process (§6.5).
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

/** The footer hints, verbatim from the frame. */
const HINTS = [
  { keys: '↑↓', word: 'move' },
  { keys: '↵', word: 'run' },
  { keys: '⌘↵', word: 'settings' },
  { keys: 'esc', word: 'close' },
];

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
    for (const command of ranked) {
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

  const run = (command: Command, wantsSettings: boolean) => {
    const state = useVoidStore.getState();
    if (wantsSettings && command.settings) command.settings(state);
    else command.run(state);
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
      const command = flat[cursor];
      if (command) run(command, e.metaKey || e.ctrlKey);
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
        <PaletteInput
          value={query}
          onChange={setQuery}
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
        <PaletteSeam />
        <PaletteFooter hints={HINTS} loadout={loadout?.name ?? '—'} />
      </Palette>
    </div>
  );
}
