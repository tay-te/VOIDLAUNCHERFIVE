/**
 * Tab 2 — Loadouts: the cartridge case (Figma 600:2).
 *
 * Each loadout is a big cartridge with an emblem; the one in use is lifted and ringed. Below,
 * the rack shows what the focused loadout carries, shelf by shelf. Managing the library keeps
 * every feature the v1 screen had (README "Loadouts, notices and keybinds"): New (registry
 * defaults, name opens for editing), Rename in place, Duplicate, Copy code (through Java's
 * clipboard), Delete in two presses (never the last), Import a `VOID1.` code (typed or pasted
 * through Java).
 *
 * Deviation from the frame: it draws an "Auto-insert by server" switch. Loadouts carry an
 * advisory `server` slug and nothing on the bridge switches by server, so the switch would do
 * nothing — the slug is printed on the card instead, and the header holds Import / New.
 *
 * Keys: click focuses a cartridge, Enter (or the Insert button, or a double click) switches to
 * it; L / Shift+L switch to the next / previous loadout.
 */

import { useEffect, useMemo, useState } from 'react';

import { enabledMods, type ImportResult, type Loadout, type ModId } from '@/bridge/protocol';
import { modLabel } from '@/registry';
import { isModOn, useVoidStore } from '@/store/store';
import { hasTextFocus } from '@/bridge/connect';
import { cx } from '@/ui';
import { Sprite, hasSprite, type SpriteName } from './Sprite';
import { CATEGORIES, CATEGORY_LABEL, Kc, Led, Seg, catClass, categoryOf } from './prims';
import { SHELF_MODS, SHELVES } from './shelves';
import { FooterKeys } from './ModsTab';

export const IMPORT_ERRORS: Record<Extract<ImportResult, { ok: false }>['error'], string> = {
  empty: 'Paste a share code first.',
  format: 'That is not a VOID loadout code.',
  version: 'That code was made by a newer VOID. Update to import it.',
  checksum: 'That code is damaged. Copy it again.',
  invalid: 'That code does not contain a loadout.',
  full: 'Your library is full (128 loadouts).',
};

/** `New loadout`, then `New loadout 2`, … — the first name the library does not already use. */
export function nextLoadoutName(library: readonly Loadout[], base = 'New loadout'): string {
  const taken = new Set(library.map((l) => l.name));
  if (!taken.has(base)) return base;
  for (let n = 2; ; n++) if (!taken.has(`${base} ${n}`)) return `${base} ${n}`;
}

const ICON_SPRITES: Record<string, SpriteName> = {
  sword: 'sword',
  swords: 'sword',
  bow: 'bow',
  target: 'bow',
  box: 'wool',
  cube: 'wool',
  bridge: 'wool',
  heart: 'gapple',
  apple: 'gapple',
  shield: 'armor_chestplate_diamond',
  bed: 'bed',
  flame: 'fire',
  fire: 'fire',
  zap: 'fire',
  bolt: 'fire',
  pickaxe: 'pickaxe',
  axe: 'axe',
  rod: 'rod',
  pearl: 'pearl',
  food: 'steak',
  tnt: 'tnt',
};
const FALLBACK_EMBLEMS: readonly SpriteName[] = ['sword', 'bow', 'wool', 'gapple', 'rod', 'pearl', 'steak', 'goldsword'];

/** The emblem a loadout card draws: its icon if the sheet has it, else a stable pick by id. */
export function loadoutEmblem(loadout: Pick<Loadout, 'icon' | 'id' | 'name'>): SpriteName {
  const icon = (loadout.icon ?? '').toLowerCase();
  if (ICON_SPRITES[icon]) return ICON_SPRITES[icon]!;
  if (hasSprite(icon)) return icon;
  const name = loadout.name.toLowerCase();
  if (/bow/.test(name)) return 'bow';
  if (/bridge/.test(name)) return 'wool';
  if (/uhc|gapple/.test(name)) return 'gapple';
  if (/bed ?wars/.test(name)) return 'bed';
  if (/rod/.test(name)) return 'rod';
  let h = 0;
  for (const ch of loadout.id) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
  return FALLBACK_EMBLEMS[h % FALLBACK_EMBLEMS.length]!;
}

function serverLabel(slug: string | null | undefined): string | null {
  if (!slug) return null;
  return slug.charAt(0).toUpperCase() + slug.slice(1).replace(/[-_]/g, ' ');
}

export function loadoutMeta(loadout: Loadout): string {
  return [`${enabledMods(loadout).length} mods`, serverLabel(loadout.server), loadout.mc].filter(Boolean).join(' · ');
}

function LoadoutCart({
  loadout,
  active,
  focused,
  cycleKey,
  renaming,
  draft,
  onDraft,
  onCommit,
  onFocus,
  onInsert,
}: {
  loadout: Loadout;
  active: boolean;
  focused: boolean;
  cycleKey: string | null;
  renaming: boolean;
  draft: string;
  onDraft: (v: string) => void;
  onCommit: () => void;
  onFocus: () => void;
  onInsert: () => void;
}) {
  const on = enabledMods(loadout).filter((id) => SHELF_MODS.includes(id as ModId)).length;
  const total = SHELF_MODS.length;
  return (
    <div
      className={cx('c-lcart', active && 'is-active', focused && 'is-focus')}
      data-loadout-id={loadout.id}
      onMouseDown={onFocus}
      onDoubleClick={onInsert}
    >
      <div className="c-lcart__strip">
        {active && cycleKey ? <Kc tone="lt">{cycleKey}</Kc> : <span className="c-cart__nokey">—</span>}
        <span className={cx('c-mono', active ? 'c-lcart__inuse' : 'c-lcart__ready')}>{active ? '● in use' : 'ready'}</span>
      </div>
      <div className="c-lcart__bed">
        <Sprite name={loadoutEmblem(loadout)} size={96} />
        <i className="c-cart__notch" />
      </div>
      {renaming ? (
        <input
          className="c-lcart__rename"
          aria-label="Loadout name"
          value={draft}
          maxLength={48}
          autoFocus
          spellCheck={false}
          onChange={(e) => onDraft(e.target.value)}
          onBlur={onCommit}
          onKeyDown={(e) => {
            if (e.key === 'Enter') onCommit();
          }}
        />
      ) : (
        <div className="c-lcart__name">{loadout.name}</div>
      )}
      <div className="c-lcart__meta">{loadoutMeta(loadout)}</div>
      <Seg className="c-lcart__seg" n={total} on={on} w={3} h={10} gap={2} />
    </div>
  );
}

export function LoadoutsTab() {
  const active = useVoidStore((s) => s.loadout);
  const library = useVoidStore((s) => s.library);
  const cycleKey = useVoidStore((s) => s.globals.cycleLoadoutKey);
  const switchLoadout = useVoidStore((s) => s.switchLoadout);
  const createLoadout = useVoidStore((s) => s.createLoadout);
  const renameLoadout = useVoidStore((s) => s.renameLoadout);
  const duplicateLoadout = useVoidStore((s) => s.duplicateLoadout);
  const deleteLoadout = useVoidStore((s) => s.deleteLoadout);
  const copyShareCode = useVoidStore((s) => s.copyShareCode);
  const readClipboard = useVoidStore((s) => s.readClipboard);
  const importLoadout = useVoidStore((s) => s.importLoadout);

  const cards = library.length > 0 ? library : active ? [active] : [];
  const [focusId, setFocusId] = useState<string | null>(active?.id ?? null);
  const focused = cards.find((l) => l.id === focusId) ?? active ?? cards[0] ?? null;
  const [importing, setImporting] = useState(false);
  const [code, setCode] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [confirming, setConfirming] = useState<string | null>(null);
  const [shown, setShown] = useState<{ id: string; code: string } | null>(null);
  const [query, setQuery] = useState('');

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return q ? cards.filter((l) => l.name.toLowerCase().includes(q)) : cards;
  }, [cards, query]);

  const startRename = (loadout: Loadout) => {
    setConfirming(null);
    setRenaming(loadout.id);
    setDraft(loadout.name);
  };
  const commitRename = () => {
    if (renaming && draft.trim() !== '') renameLoadout(renaming, draft);
    setRenaming(null);
  };
  const create = () => {
    const created = createLoadout(nextLoadoutName(cards));
    if (created) {
      setFocusId(created.id);
      startRename(created);
    }
  };
  const runImport = (text: string) => {
    const result = importLoadout(text);
    if (result.ok) {
      setCode('');
      setImportError(null);
      setImporting(false);
    } else {
      setImportError(IMPORT_ERRORS[result.error]);
    }
  };
  const step = (by: number) => {
    if (cards.length < 2 || !active) return;
    const at = cards.findIndex((l) => l.id === active.id);
    const next = cards[(at + by + cards.length) % cards.length]!;
    switchLoadout(next.id);
    setFocusId(next.id);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (hasTextFocus() || useVoidStore.getState().paletteOpen) return;
      if (e.key === 'l' || e.key === 'L') {
        e.preventDefault();
        step(e.shiftKey ? -1 : 1);
      } else if (e.key === 'Enter' && focused && !(document.activeElement instanceof HTMLButtonElement)) {
        e.preventDefault();
        switchLoadout(focused.id);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const isActive = (l: Loadout | null) => Boolean(l && active && l.id === active.id);
  const isConfirming = focused ? confirming === focused.id : false;

  return (
    <>
      <header className="c-head">
        <div className="c-head__title">
          <span className="c-h1">Loadouts</span>
          <span className="c-mono">
            {cards.length} cartridge{cards.length === 1 ? '' : 's'}
          </span>
        </div>
        <div className="c-head__actions">
          <button
            type="button"
            className={cx('c-btn c-btn--g c-btn--sm', importing && 'is-on')}
            aria-pressed={importing}
            title="Add a loadout from a share code"
            onClick={() => {
              setImporting(!importing);
              setImportError(null);
            }}
          >
            Import code
          </button>
          <button type="button" className="c-btn c-btn--s c-btn--sm" onClick={create} title="Create a loadout with every mod at its defaults">
            New loadout
          </button>
        </div>
        <label className={cx('c-search', query !== '' && 'is-active')}>
          <span className="c-glyph-search" aria-hidden="true">
            <i />
            <b />
          </span>
          <input value={query} placeholder="Search loadouts" aria-label="Search loadouts" spellCheck={false} onChange={(e) => setQuery(e.target.value)} />
          <Kc>/</Kc>
        </label>
      </header>

      {importing ? (
        <div className="c-import">
          <input
            className="c-import__field"
            placeholder="Paste a VOID1. share code"
            aria-label="Share code"
            value={code}
            autoFocus
            spellCheck={false}
            onChange={(e) => {
              setCode(e.target.value);
              setImportError(null);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') runImport(code);
            }}
          />
          <button
            type="button"
            className="c-btn c-btn--g c-btn--sm"
            title="Paste the share code on the clipboard and import it"
            onClick={() => {
              const pasted = readClipboard();
              setCode(pasted);
              runImport(pasted);
            }}
          >
            Paste
          </button>
          <button type="button" className="c-btn c-btn--p c-btn--sm" onClick={() => runImport(code)}>
            Import
          </button>
          {importError ? (
            <span className="c-import__error" role="alert">
              {importError}
            </span>
          ) : (
            <span className="c-import__hint">Codes carry mods, settings, HUD layout and pins. They never switch you.</span>
          )}
        </div>
      ) : null}

      <div className="c-lcase">
        {visible.map((loadout) => (
          <LoadoutCart
            key={loadout.id}
            loadout={loadout}
            active={isActive(loadout)}
            focused={focused?.id === loadout.id}
            cycleKey={cycleKey && cycleKey !== 'NONE' ? cycleKey : null}
            renaming={renaming === loadout.id}
            draft={draft}
            onDraft={setDraft}
            onCommit={commitRename}
            onFocus={() => setFocusId(loadout.id)}
            onInsert={() => switchLoadout(loadout.id)}
          />
        ))}
        <button type="button" className="c-lnew" onClick={create} aria-label="New loadout">
          <span className="c-lnew__plus">+</span>
          <span className="c-mono">New</span>
        </button>
      </div>

      {focused ? (
        <div className="c-rack">
          <div className="c-rack__head">
            <span className="c-label c-rack__title">{focused.name} · rack</span>
            <span className="c-rack__actions">
              {!isActive(focused) ? (
                <button type="button" className="c-btn c-btn--p c-btn--sm" onClick={() => switchLoadout(focused.id)}>
                  Insert <Kc tone="lt">Enter</Kc>
                </button>
              ) : (
                <span className="c-mono c-rack__note">mods + HUD layout swap together · {cycleKey && cycleKey !== 'NONE' ? `${cycleKey} cycles` : 'cycle key off'}</span>
              )}
              <button type="button" className="c-btn c-btn--g c-btn--sm" onClick={() => startRename(focused)}>
                Rename
              </button>
              <button type="button" className="c-btn c-btn--g c-btn--sm" onClick={() => duplicateLoadout(focused.id)}>
                Duplicate
              </button>
              <button
                type="button"
                className="c-btn c-btn--g c-btn--sm"
                title="Copy this loadout as a share code"
                onClick={() => {
                  const result = copyShareCode(focused.id);
                  setShown(result && !result.copied ? { id: focused.id, code: result.code } : null);
                }}
              >
                Copy code
              </button>
              <button
                type="button"
                className={cx('c-btn c-btn--sm', isConfirming ? 'c-btn--warn' : 'c-btn--g')}
                disabled={cards.length <= 1}
                title={cards.length <= 1 ? 'The last loadout cannot be deleted' : undefined}
                onClick={() => {
                  if (isConfirming) {
                    setConfirming(null);
                    deleteLoadout(focused.id);
                    setFocusId(active?.id ?? null);
                  } else {
                    setConfirming(focused.id);
                  }
                }}
                onBlur={() => {
                  if (isConfirming) setConfirming(null);
                }}
              >
                {isConfirming ? 'Delete?' : 'Delete'}
              </button>
            </span>
          </div>
          {shown?.id === focused.id ? (
            <input
              className="c-import__field c-rack__code"
              aria-label={`${focused.name} share code`}
              readOnly
              value={shown.code}
              onFocus={(e) => e.currentTarget.select()}
            />
          ) : null}
          {CATEGORIES.map((c) => {
            const ids = SHELVES[c].filter((id) => isModOn(focused, id));
            return (
              <div key={c} className={cx('c-rack__row', catClass(c))}>
                <span className="c-rack__cat">
                  <Led on />
                  <span className="c-label">{CATEGORY_LABEL[c]}</span>
                </span>
                <span className="c-rack__mods">
                  {ids.map((id) => (
                    <span key={id} className="c-rack__mod" title={modLabel(id)}>
                      {hasSprite(id) ? <Sprite name={id} size={24} /> : null}
                      <i />
                    </span>
                  ))}
                  {ids.length === 0 ? <span className="c-mono c-rack__none">none on</span> : null}
                </span>
                <span className="c-mono">
                  {ids.length}/{SHELVES[c].length}
                </span>
              </div>
            );
          })}
        </div>
      ) : null}

      <footer className="c-foot">
        <span className="c-mono">Cartridges hold mods, settings and HUD layout</span>
        <FooterKeys
          keys={[
            ['L', 'next'],
            ['Shift L', 'previous'],
            ['Enter', 'insert'],
          ]}
        />
      </footer>
    </>
  );
}

/** For tests: the rack's mods for a loadout and category. */
export function rackMods(loadout: Loadout, category: (typeof CATEGORIES)[number]): ModId[] {
  return SHELVES[category].filter((id) => isModOn(loadout, id) && categoryOf(id) === category);
}
