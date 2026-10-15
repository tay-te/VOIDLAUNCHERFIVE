/**
 * Overlay — Loadouts · frame `244:1130`.
 *
 * "A loadout is which mods are on, their settings and HUD layout." — the
 * definition line is on the frame because the model *is* the product.
 *
 * Switching is instant: `void.switchLoadout(id)` writes every actuator field and
 * re-renders the HUD in under a frame (§8.2), and the `loadout` event that
 * follows is what updates this screen. There is no optimistic state here.
 *
 * ## Managing the library
 *
 * The screen used to be a switcher only, with a `New loadout` button that did nothing. It now
 * owns the library the way the launcher does, through the bridge's library calls (`bridge.json`:
 * `createLoadout`, `renameLoadout`, `duplicateLoadout`, `deleteLoadout`, `exportLoadout`,
 * `importLoadout`). Each is synchronous and answers with what Java stored; Java tells Rust, which
 * persists it, and pushes `loadouts` back, so the cards here are always the real library.
 *
 * Nothing new is drawn for it. The card is `@void/ui`'s `LoadoutCard`, and its actions are a row
 * of the system's ghost buttons inside the card (its `children` slot); the import field is the
 * panel-header search field, `SearchBar variant="panel"`, under the header. Rename edits the
 * card's own title in place. Delete is two clicks — the second on the same button, which reads
 * `Delete?` — because it cannot be undone and the menu has no dialog layer.
 *
 * **Share codes travel through Java's clipboard.** The Ultralight binding implements no platform
 * clipboard, so Ctrl+C and Ctrl+V do nothing in this page. `Copy code` asks Java to put the code
 * on the system clipboard (`void.setClipboard`), and `Paste` asks Java for it
 * (`void.getClipboard`). Typing a code into the field still works.
 */

import { useState } from 'react';

import { Button, LoadoutCard, Panel, SearchBar } from '@/ui';
import { enabledMods, enabledModCount, type ImportResult, type Loadout } from '@/bridge/protocol';
import { modLabel } from '@/registry';
import { useVoidStore } from '@/store/store';
import { playedTime } from '@/hud/format';

export const LOADOUTS_FOOTER =
  'Switching applies instantly   ·   settings and HUD layout are per loadout   ·   L cycles loadouts in game';

/** Chips a card lists before it collapses the rest into `+ N more`. */
const CHIP_BUDGET = 6;

/** What a refused import tells the player — `bridge.json#/definitions/import_result` `error`. */
export const IMPORT_ERRORS: Record<Extract<ImportResult, { ok: false }>['error'], string> = {
  empty: 'Paste a share code first.',
  format: 'That is not a VOID loadout code.',
  version: 'That code was made by a newer VOID. Update to import it.',
  checksum: 'That code is damaged. Copy it again.',
  invalid: 'That code does not contain a loadout.',
  full: 'Your library is full (128 loadouts).',
};

/** The card's mono meta line: `24 mods on   ·   Hypixel   ·   1.8.9`. */
export function loadoutMeta(loadout: Loadout): string {
  return [
    `${enabledModCount(loadout)} mods on`,
    loadout.server ? loadout.server.charAt(0).toUpperCase() + loadout.server.slice(1) : null,
    loadout.mc,
  ]
    .filter(Boolean)
    .join('   ·   ');
}

/** `New loadout`, then `New loadout 2`, … — the first name the library does not already use. */
export function nextLoadoutName(library: readonly Loadout[], base = 'New loadout'): string {
  const taken = new Set(library.map((l) => l.name));
  if (!taken.has(base)) return base;
  for (let n = 2; ; n++) if (!taken.has(`${base} ${n}`)) return `${base} ${n}`;
}

export function LoadoutsScreen() {
  const active = useVoidStore((s) => s.loadout);
  const library = useVoidStore((s) => s.library);
  const switchLoadout = useVoidStore((s) => s.switchLoadout);
  const closeMenu = useVoidStore((s) => s.closeMenu);
  const createLoadout = useVoidStore((s) => s.createLoadout);
  const renameLoadout = useVoidStore((s) => s.renameLoadout);
  const duplicateLoadout = useVoidStore((s) => s.duplicateLoadout);
  const deleteLoadout = useVoidStore((s) => s.deleteLoadout);
  const copyShareCode = useVoidStore((s) => s.copyShareCode);
  const readClipboard = useVoidStore((s) => s.readClipboard);
  const importLoadout = useVoidStore((s) => s.importLoadout);

  const [importing, setImporting] = useState(false);
  const [code, setCode] = useState('');
  const [importError, setImportError] = useState<string | null>(null);
  const [renaming, setRenaming] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [confirming, setConfirming] = useState<string | null>(null);
  /** A code the clipboard refused, shown on its card so it can still be read off. */
  const [shown, setShown] = useState<{ id: string; code: string } | null>(null);

  const cards = library.length > 0 ? library : active ? [active] : [];

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
    if (created) startRename(created);
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

  return (
    <div className="panel-wrap">
      <Panel
        surface="overlay"
        animate
        title="Loadouts"
        className="loadouts-panel"
        subtitle="A loadout is which mods are on, their settings and HUD layout."
        onClose={closeMenu}
        footer={LOADOUTS_FOOTER}
        headerRight={
          <>
            <span className="v-spacer" />
            <Button
              variant="raised"
              icon="layers"
              title="Add a loadout from a share code"
              aria-pressed={importing}
              onClick={() => {
                setImporting(!importing);
                setImportError(null);
              }}
            >
              Import code
            </Button>
            <Button
              variant="raised"
              icon="plus"
              title="Create a loadout with every mod at its defaults"
              onClick={create}
            >
              New loadout
            </Button>
          </>
        }
      >
        {importing ? (
          <div className="loadouts-import">
            <SearchBar
              variant="panel"
              hint={null}
              placeholder="Paste a VOID1. share code"
              aria-label="Share code"
              value={code}
              onChange={(next) => {
                setCode(next);
                setImportError(null);
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter') runImport(code);
              }}
              spellCheck={false}
              autoFocus
            />
            <Button
              variant="ghost"
              title="Paste the share code on the clipboard and import it"
              onClick={() => {
                const pasted = readClipboard();
                setCode(pasted);
                runImport(pasted);
              }}
            >
              Paste
            </Button>
            <Button variant="accent" onClick={() => runImport(code)}>
              Import
            </Button>
            {importError ? (
              <span className="loadouts-import__error" role="alert">
                {importError}
              </span>
            ) : (
              <span className="loadouts-import__hint">
                Codes carry mods, settings, HUD layout and quick pins. They never switch you.
              </span>
            )}
          </div>
        ) : null}

        <div className="loadouts">
          {cards.map((loadout) => {
            const on = enabledMods(loadout);
            const shownChips = on.slice(0, CHIP_BUDGET);
            const fps = loadout.stats?.fps_avg ?? 0;
            const played = loadout.stats?.played_ms ?? 0;
            const isRenaming = renaming === loadout.id;
            const isConfirming = confirming === loadout.id;
            return (
              <LoadoutCard
                key={loadout.id}
                name={
                  isRenaming ? (
                    <input
                      className="loadout-rename"
                      aria-label="Loadout name"
                      value={draft}
                      maxLength={48}
                      autoFocus
                      spellCheck={false}
                      onChange={(event) => setDraft(event.target.value)}
                      onBlur={commitRename}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') commitRename();
                      }}
                    />
                  ) : (
                    loadout.name
                  )
                }
                icon={loadout.icon}
                meta={loadoutMeta(loadout)}
                active={loadout.id === active?.id}
                includes={shownChips.map((id) => ({ label: modLabel(id) }))}
                moreCount={on.length - shownChips.length}
                stats={[
                  { value: fps > 0 ? Math.round(fps) : '—', unit: 'fps avg' },
                  { value: played > 0 ? playedTime(played) : '—', unit: 'played' },
                ]}
                onSwitch={() => switchLoadout(loadout.id)}
                // The card prints `name` twice — the title and `Switch to <name>` — and while it
                // is the rename field it must exist once, or the second copy takes the focus.
                buttonLabel={isRenaming ? `Switch to ${loadout.name}` : undefined}
              >
                {shown?.id === loadout.id ? (
                  <input
                    className="loadout-code"
                    aria-label={`${loadout.name} share code`}
                    readOnly
                    value={shown.code}
                    onFocus={(event) => event.currentTarget.select()}
                  />
                ) : null}
                <div className="loadout-actions">
                  <Button variant="ghost" onClick={() => startRename(loadout)}>
                    Rename
                  </Button>
                  <Button variant="ghost" onClick={() => duplicateLoadout(loadout.id)}>
                    Duplicate
                  </Button>
                  <Button
                    variant="ghost"
                    title="Copy this loadout as a share code"
                    onClick={() => {
                      const result = copyShareCode(loadout.id);
                      setShown(result && !result.copied ? { id: loadout.id, code: result.code } : null);
                    }}
                  >
                    Copy code
                  </Button>
                  <Button
                    variant="ghost"
                    className={isConfirming ? 'loadout-actions__confirm' : undefined}
                    disabled={cards.length <= 1}
                    title={cards.length <= 1 ? 'The last loadout cannot be deleted' : undefined}
                    onClick={() => {
                      if (isConfirming) {
                        setConfirming(null);
                        deleteLoadout(loadout.id);
                      } else {
                        setConfirming(loadout.id);
                      }
                    }}
                    onBlur={() => {
                      if (isConfirming) setConfirming(null);
                    }}
                  >
                    {isConfirming ? 'Delete?' : 'Delete'}
                  </Button>
                </div>
              </LoadoutCard>
            );
          })}
        </div>
      </Panel>
    </div>
  );
}
