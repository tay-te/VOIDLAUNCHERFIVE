/**
 * Overlay — Quick palette · frame `244:1900`.
 *
 * ⌘K / Ctrl-K opens it over whichever screen is up. Type, get ranked mods,
 * loadouts and actions, `↵` runs the highlighted one, `⌘↵` opens its settings
 * instead, `esc` closes. The selected row previews the state change inline —
 * `currently off  →  on` — which is only honest because the toggle is
 * synchronous and in-process (§6.5).
 */

import { useEffect, useMemo, useRef, useState } from 'react';
import { useVoidStore } from '@/store/store';
import { Icon, Kbd } from '@/ui';
import { buildCommands, type Command } from './commands';
import { rank } from './fuzzy';

/** Rows shown under ACTIONS before the rest fall through to ALSO. */
const ACTION_LIMIT = 3;
const ALSO_LIMIT = 2;

export function QuickPalette() {
  const store = useVoidStore();
  const setPaletteOpen = useVoidStore((s) => s.setPaletteOpen);
  const loadout = useVoidStore((s) => s.loadout);
  const [query, setQuery] = useState('');
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef<HTMLInputElement | null>(null);

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

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const close = () => setPaletteOpen(false);

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
      if (!command) return;
      const wantsSettings = e.metaKey || e.ctrlKey;
      const state = useVoidStore.getState();
      if (wantsSettings && command.settings) command.settings(state);
      else command.run(state);
      close();
      return;
    }
    if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      close();
    }
  };

  const renderRow = (command: Command, index: number) => (
    <button
      type="button"
      key={command.id}
      className={`palette__row${index === cursor ? ' palette__row--selected' : ''}`}
      onMouseEnter={() => setCursor(index)}
      onClick={() => {
        command.run(useVoidStore.getState());
        close();
      }}
    >
      <span className="palette__well">
        <Icon name={command.icon} size={16} />
      </span>
      <span className="palette__body">
        <span className="palette__title">{command.title}</span>
        {command.sub && (
          <span className="palette__sub">
            {command.sub}
            {command.subAccent && <span className="palette__sub-accent">{command.subAccent}</span>}
          </span>
        )}
      </span>
      {command.kbd && (
        <span className="palette__kbds">
          {command.kbd.map((key) => (
            <Kbd key={key} flavour="palette">{key}</Kbd>
          ))}
        </span>
      )}
    </button>
  );

  return (
    <div className="palette-layer" onKeyDown={onKeyDown}>
      <div className="palette-layer__dim" onClick={close} />
      <div className="palette void-anim-in" role="dialog" aria-label="Quick palette">
        <div className="palette__input">
          <Icon name="search" size={18} />
          <input
            ref={inputRef}
            value={query}
            spellCheck={false}
            placeholder="Ask VOID anything"
            onChange={(e) => setQuery(e.target.value)}
          />
          <Kbd flavour="palette">esc</Kbd>
        </div>
        <div className="palette__seam" />

        {flat.length === 0 ? (
          <div className="palette__empty">Nothing matches “{query}”.</div>
        ) : (
          <>
            {actions.length > 0 && (
              <>
                <div className="palette__caption">Actions</div>
                <div className="palette__list">{actions.map((c, i) => renderRow(c, i))}</div>
              </>
            )}
            {also.length > 0 && (
              <>
                <div className="palette__caption">Also</div>
                <div className="palette__list">
                  {also.map((c, i) => renderRow(c, actions.length + i))}
                </div>
              </>
            )}
          </>
        )}

        <div className="palette__spacer" />
        <div className="palette__seam" />
        <div className="palette__footer">
          {[
            ['↑↓', 'move'],
            ['↵', 'run'],
            ['⌘↵', 'settings'],
            ['esc', 'close'],
          ].map(([key, word]) => (
            <span className="palette__hint" key={key}>
              <span className="palette__hint-key">{key}</span>
              <span className="palette__hint-word">{word}</span>
            </span>
          ))}
          <span className="palette__loadout">
            <Icon name="sword" size={14} />
            {loadout?.name ?? '—'}
          </span>
        </div>
      </div>
    </div>
  );
}
