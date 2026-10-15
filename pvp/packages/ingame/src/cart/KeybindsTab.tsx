/**
 * Tab 3 — Keybinds: every key VOID listens for, on a keyboard (Figma 643:2).
 *
 * The bindings are `keybindOverview` — the menu key, the loadout-cycle key and every
 * `keybind`-typed mod setting, read off the active loadout — so a mod that gains a key appears
 * here with no edit. Vanilla's keys are the player's own once Java has pushed `binds`, else the
 * 1.8.9 defaults (cart/keys.ts). A VOID key that is also on a vanilla key, or that two VOID
 * bindings share, is a conflict and is counted.
 *
 * Click a key to select it; the panel on the right rebinds or unbinds it through the same
 * capture block the settings bench uses. `F` arms "find": the next key pressed is selected,
 * digits included (otherwise 1–5 switch sections). `Backspace` unbinds the selected key.
 */

import { useEffect, useMemo, useState } from 'react';

import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { modLabel } from '@/registry';
import { useVoidStore } from '@/store/store';
import { hasTextFocus } from '@/bridge/connect';
import { keybindOverview, type KeybindRow } from '@/menu/keybind-overview';
import { keybindLabel } from '@/menu/settings-format';
import { cx } from '@/ui';
import { Sprite, hasSprite } from './Sprite';
import { CATEGORIES, CATEGORY_LABEL, Kc, Led, SearchGlyph, catClass, categoryOf, type CartCategory } from './prims';
import { KEYBOARD, lwjglName, useVanillaBinds } from './keys';
import { KeyCapture } from './Bench';
import { FooterKeys } from './ModsTab';

const UNIT = 54;
const GAP = 5;

type Filter = 'all' | CartCategory;

function rowCategory(row: KeybindRow): CartCategory | null {
  return row.mod ? categoryOf(row.mod) : null;
}

export function KeybindsTab() {
  const loadout = useVoidStore((s) => s.loadout);
  const globals = useVoidStore((s) => s.globals);
  const setSetting = useVoidStore((s) => s.setSetting);
  const setGlobal = useVoidStore((s) => s.setGlobal);
  const captureKeybind = useVoidStore((s) => s.captureKeybind);
  const captureMenuKey = useVoidStore((s) => s.captureMenuKey);
  const captureGlobalKey = useVoidStore((s) => s.captureGlobalKey);
  const openMod = useVoidStore((s) => s.openMod);
  const rows = useMemo(() => keybindOverview(loadout, globals), [loadout, globals]);
  const { table: vanillaBinds, real: realVanilla } = useVanillaBinds();
  const [filter, setFilter] = useState<Filter>('all');
  const [query, setQuery] = useState('');
  const [finding, setFinding] = useState(false);

  const byKey = useMemo(() => {
    const map = new Map<string, KeybindRow[]>();
    for (const row of rows) map.set(row.key, [...(map.get(row.key) ?? []), row]);
    return map;
  }, [rows]);

  const firstBound = rows.find((r) => r.mod) ?? rows[0];
  const [selected, setSelected] = useState<string>(firstBound?.key ?? 'K');

  const conflicts = useMemo(() => {
    let n = 0;
    for (const [key, list] of byKey) if (list.length > 1 || vanillaBinds[key]) n += 1;
    return n;
  }, [byKey, vanillaBinds]);

  const counts: Record<Filter, number> = {
    all: rows.length,
    hud: rows.filter((r) => rowCategory(r) === 'hud').length,
    pvp: rows.filter((r) => rowCategory(r) === 'pvp').length,
    visual: rows.filter((r) => rowCategory(r) === 'visual').length,
    utility: rows.filter((r) => rowCategory(r) === 'utility').length,
  };

  const q = query.trim().toLowerCase();
  const matches = (row: KeybindRow) =>
    (filter === 'all' || rowCategory(row) === filter) && (!q || row.label.toLowerCase().includes(q) || row.keyLabel.toLowerCase().includes(q));

  // F arms find; Backspace unbinds. Digits are the shell's unless find is armed.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (hasTextFocus() || useVoidStore.getState().paletteOpen) return;
      if (finding) {
        const name = lwjglName(e);
        if (name && name !== 'ESCAPE') {
          e.preventDefault();
          e.stopImmediatePropagation();
          setSelected(name);
        }
        setFinding(false);
        return;
      }
      if (e.key === 'f' || e.key === 'F') {
        e.preventDefault();
        setFinding(true);
      } else if (e.key === 'Backspace') {
        const row = byKey.get(selected)?.[0];
        if (!row) return;
        e.preventDefault();
        if (row.mod) setSetting(row.mod, row.setting, 'NONE');
        else if (row.setting === 'cycle_loadout_key') setGlobal('cycle_loadout_key', 'NONE');
      }
    };
    // Capture phase, so an armed find gets the digit before the shell turns it into a section.
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [finding, byKey, selected, setSetting, setGlobal]);

  /* ---- the keyboard ---- */

  const caps: React.ReactNode[] = [];
  let y = 0;
  KEYBOARD.forEach((line, r) => {
    let x = 0;
    for (const [name, label, w] of line) {
      const width = w * UNIT + (w - 1) * GAP;
      const binds = byKey.get(name) ?? [];
      const vanilla = vanillaBinds[name];
      const bind = binds[0];
      const visible = binds.filter(matches);
      const dimmed = binds.length > 0 && visible.length === 0;
      const conflict = binds.length > 1 || (binds.length > 0 && Boolean(vanilla));
      const category = bind ? rowCategory(bind) : null;
      caps.push(
        <button
          key={name}
          type="button"
          data-key={name}
          className={cx(
            'c-kb',
            bind ? 'is-void' : vanilla ? 'is-vanilla' : 'is-free',
            selected === name && 'is-sel',
            conflict && 'is-conflict',
            dimmed && 'is-dim',
            category && catClass(category),
          )}
          style={{ left: x, top: y, width }}
          onClick={() => setSelected(name)}
          aria-label={`${label}${bind ? ` — ${bind.label}` : vanilla ? ` — ${vanilla.label}` : ''}`}
        >
          <span className="c-kb__l">{label}</span>
          {bind ? (
            <span className="c-kb__ico">
              {bind.mod && hasSprite(bind.mod) ? <Sprite name={bind.mod} size={22} /> : <Sprite name="logo" size={16} />}
            </span>
          ) : null}
          {bind && category ? <Led on className="c-kb__led" /> : null}
          <span className="c-kb__t">{bind ? bind.label : vanilla ? vanilla.label : ''}</span>
        </button>,
      );
      x += width + GAP;
    }
    y += r === 0 ? UNIT + 12 : UNIT + GAP;
  });
  const boardW = 15 * UNIT + 14 * GAP;

  /* ---- the selected key ---- */

  const selRows = byKey.get(selected) ?? [];
  const selVanilla = vanillaBinds[selected];
  const selRow = selRows[0];
  const selLabel = keybindLabel(selected);

  const detail = (
    <div className={cx('c-blk c-kdetail', selRow?.mod && catClass(categoryOf(selRow.mod)))}>
      <span className="c-mono">{finding ? 'Press any key…' : 'Selected key'}</span>
      <div className="c-kdetail__who">
        <Kc tone="lt" large>
          {selLabel}
        </Kc>
        <span className="c-kdetail__titles">
          <span className="c-label">{selRow ? selRow.label : selVanilla ? selVanilla.label : 'Free'}</span>
          <span className="c-mono">
            {selRow
              ? selRow.mod
                ? `${CATEGORY_LABEL[categoryOf(selRow.mod)]} · ${selRow.live ? 'on' : 'mod is off'}`
                : 'VOID · client key'
              : selVanilla
                ? realVanilla
                  ? 'Vanilla'
                  : 'Vanilla default'
                : 'Nothing on this key'}
          </span>
        </span>
      </div>
      <div className="c-kdetail__bed">
        {selRow?.mod && hasSprite(selRow.mod) ? (
          <Sprite name={selRow.mod} size={48} />
        ) : selRow ? (
          <Sprite name="logo" size={40} />
        ) : (
          <span className="c-mono">{selVanilla ? `Vanilla ${selVanilla.does}` : 'Bind a mod here from its settings'}</span>
        )}
      </div>
      {selRows.length > 1 ? (
        <p className="c-kdetail__warn">Also on this key: {selRows.slice(1).map((r) => r.label).join(', ')}</p>
      ) : selRow && selVanilla ? (
        <p className="c-kdetail__warn">
          Vanilla also {selVanilla.does} on {selLabel}
          {realVanilla ? '.' : ' (default).'}
        </p>
      ) : null}
      {selRow ? (
        <KeyCapture
          key={selRow.id}
          compact
          label=""
          value={selRow.key}
          selfId={selRow.id}
          allowNone={selRow.setting !== 'menu_key'}
          capture={() =>
            (selRow.mod
              ? captureKeybind(selRow.mod)
              : selRow.setting === 'menu_key'
                ? captureMenuKey()
                : captureGlobalKey('cycle')) as Promise<string | null>
          }
          write={(key) => {
            if (selRow.mod) setSetting(selRow.mod, selRow.setting, key);
            else setGlobal(selRow.setting as 'menu_key' | 'cycle_loadout_key', key);
            if (key !== 'NONE') setSelected(key);
          }}
        />
      ) : null}
    </div>
  );

  const legend = (
    <div className="c-blk c-legend">
      <span className="c-mono">Legend</span>
      <span className="c-legend__row">
        <i className="c-legend__cap is-void" />
        VOID mod
      </span>
      <span className="c-legend__row">
        <i className="c-legend__cap is-vanilla" />
        Vanilla
      </span>
      <span className="c-legend__row is-free">
        <i className="c-legend__cap is-free" />
        Free
      </span>
    </div>
  );

  const bound = rows.filter(matches);

  return (
    <>
      <header className="c-head">
        <div className="c-head__title">
          <span className="c-h1">Keybinds</span>
          <span className="c-mono">
            {rows.length} VOID · {Object.keys(vanillaBinds).length} vanilla
          </span>
        </div>
        <div className="c-chips" role="tablist" aria-label="Category">
          {(['all', ...CATEGORIES] as Filter[]).map((f) => (
            <button
              key={f}
              type="button"
              role="tab"
              aria-selected={filter === f}
              className={cx('c-chip', filter === f && 'is-on', f !== 'all' && catClass(f))}
              onClick={() => setFilter(f)}
            >
              {f !== 'all' ? <Led on /> : null}
              {f === 'all' ? 'All' : CATEGORY_LABEL[f]}
              <i>{counts[f]}</i>
            </button>
          ))}
        </div>
        <label className={cx('c-search', query !== '' && 'is-active')}>
          <SearchGlyph lit={query !== ''} />
          <input
            value={query}
            placeholder="Search keys and mods"
            aria-label="Search keybinds"
            spellCheck={false}
            onChange={(e) => setQuery(e.target.value)}
          />
          <Kc>/</Kc>
        </label>
      </header>
      <div className="c-kbwrap">
        <div className="c-board" style={{ width: boardW, height: y }}>
          {caps}
        </div>
        <div className="c-kside">
          {detail}
          {legend}
          <div className="c-blk c-kconf">
            <span className="c-label">Conflicts</span>
            <span className={cx('c-kconf__n', conflicts > 0 && 'is-warn')}>{conflicts}</span>
          </div>
        </div>
      </div>
      <div className="c-kbinds">
        {bound.slice(0, 10).map((row) => (
          <button
            key={row.id}
            type="button"
            className={cx('c-row2 c-kbind', row.mod && catClass(categoryOf(row.mod)), !row.live && 'is-off')}
            onClick={() => (row.mod ? openMod(row.mod as ModId) : setSelected(row.key))}
            title={row.mod ? `Open ${modLabel(row.mod)} settings` : undefined}
          >
            <span className="c-kbind__name">
              {row.mod && hasSprite(row.mod) ? <Sprite name={row.mod} size={24} /> : <Sprite name="logo" size={20} />}
              {row.label}
            </span>
            <Kc tone={row.clashes.length > 0 || vanillaBinds[row.key] ? 'warn' : 'idle'}>{row.keyLabel}</Kc>
          </button>
        ))}
        {bound.length === 0 ? <p className="c-none">No binding matches.</p> : null}
      </div>
      <footer className="c-foot">
        <span className="c-mono">{finding ? 'Press the key you want to find' : 'Press F, then any key, to find what it does'}</span>
        <FooterKeys
          keys={[
            ['Click', 'rebind'],
            ['Bksp', 'unbind'],
            ['F', 'find key'],
          ]}
        />
      </footer>
    </>
  );
}

/** Mods with a key setting, for tests. */
export function keyedMods(): ModId[] {
  return (Object.keys(MOD_REGISTRY) as ModId[]).filter((id) => Object.keys(MOD_REGISTRY[id].defaults ?? {}).some((k) => /key/.test(k)));
}
