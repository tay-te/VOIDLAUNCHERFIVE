/**
 * The mod page — one mod, the whole panel.
 *
 * ## Why this is a page and not a panel
 *
 * It used to be the inspector: a properties panel that came out of the right edge while the
 * grid contracted from six columns to three. That arrangement was backwards. **The toggle is
 * on the card**, so the grid is the working surface and most interactions never need the
 * properties at all — and compromising the grid to keep a panel visible optimised the rare
 * case at the cost of the common one. The grid is now always full-panel; selecting a mod
 * comes here; going back returns to the grid, unchanged.
 *
 * ## What full width is for
 *
 * Contract §8 — "properties structure follows property count" — was written for a panel that
 * never had the room to mean anything. Squeezed into what was left after the grid contracted,
 * every structure ended up as the same narrow column, and the live preview was a 128px strip.
 * Here the preview fills the height the properties do not need, which for the visual mods
 * (keystrokes, crosshair, CPS, zoom) is the point of the page: you are looking at what the mod
 * draws, at a size where you can see it, while you change it. The three structures are
 * genuinely three shapes now — see `ModSettingsScreen.tsx`, which owns them.
 *
 * ## The shell it lives in
 *
 * `ModsScreen` owns the panel box and hands it over: the same `--panel-w` / `--panel-h`
 * `solveGrid` gave the grid. **The box does not move between the grid and a page** — only what
 * is inside it changes, which is the property the contracting layout was trying to keep and
 * kept badly. Nothing here re-measures anything.
 *
 * ## Getting back
 *
 * Two visible affordances, both where people look for them: `‹ Mods` at the top left of the
 * bar (`ModsScreen`), and `Done` at the bottom right of the page. **Escape is deliberately not
 * one of them.** In game the page never sees it: `VoidMenuScreen.keyPressed` swallows Escape
 * and closes the whole menu unless `ui.hasFocusedInput()` says a field has focus, so the key
 * never reaches the view — wiring a page-level handler would produce a shortcut that works in
 * the browser harness and silently does nothing else in the product. Escape closing the menu
 * from here is also the same rule R-Shift already follows, and reopening lands on the grid
 * (`store.ts`, `applyMenu`), so nothing is stranded.
 */

import { useState } from 'react';

import { Toggle } from '@/ui';
import { MOD_REGISTRY, type ModId } from '@/bridge/protocol';
import { MOD_CATEGORY, hueStyle, modLabel } from '@/registry';
import { isModOn, useModSettings, useVoidStore } from '@/store/store';
import { ModProperties } from './ModSettingsScreen';
import { keybindLabel } from './settings-format';

export interface ModPageProps {
  id: ModId;
}

/** `HUD  ·  ENABLED  ·  R-SHIFT` — the frames' spaced separator, preserved in CSS. */
export function metaLine(id: ModId, on: boolean, keybind: string | null): string {
  const parts = [MOD_CATEGORY[id], on ? 'ENABLED' : 'DISABLED'];
  parts.push(keybind === null || keybind === 'None' ? 'NO KEYBIND' : keybind.toUpperCase());
  return parts.join('   ·   ');
}

/**
 * The page.
 *
 * The head is the frames' own (`289:5523`), less the `SELECTED MOD` eyebrow: that eyebrow
 * labelled a panel standing beside a grid, and on a page whose entire subject is this mod it
 * says nothing the title does not. What it gains instead is the mod's own description, which
 * until now only the list layout could show — a page is where that sentence is read rather
 * than scanned past.
 *
 * Enablement stays the one control that is in the same place whatever mod you opened, and it
 * is **not** a property row (§8, frame `289:5523`). It is deliberately the same switch the
 * card carries, in the same reading position — top right — so the card and the page agree
 * about where a mod's on/off lives.
 */
export function ModPage({ id }: ModPageProps): React.ReactElement {
  const settings = useModSettings(id);
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const resetMod = useVoidStore((s) => s.resetMod);
  const closeMod = useVoidStore((s) => s.closeMod);

  // The arrival animation, dropped the moment it has run. Same discipline as `.menu-layer`
  // (App.tsx): an element carrying a standing animation is an element the engine may keep on a
  // composited layer, and this one is full of text. `animationend` rather than a timer, so the
  // class comes off the frame after the last one it affected.
  const [entering, setEntering] = useState(true);

  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;
  const keybind = keybindKey ? keybindLabel(settings[keybindKey] ?? null) : null;

  return (
    <div
      className={`modpage${entering ? ' modpage--enter' : ''}`}
      // The mod's own hue, so every accent underneath — the live meter cell, the readout, the
      // corner slot, the drag handle — reads `var(--hue, var(--accent))` (contract §1).
      style={hueStyle(id)}
      onAnimationEnd={(event) => {
        // `animationend` bubbles, and the head runs its own: only this element leaving its
        // entrance ends the entrance.
        if (event.target === event.currentTarget && event.animationName === 'void-page-in') {
          setEntering(false);
        }
      }}
    >
      <div className="modpage__head">
        <span className="modpage__ident">
          <span className="modpage__title">{modLabel(id)}</span>
          <span className="modpage__meta">{metaLine(id, on, keybind)}</span>
          <span className="modpage__desc">{MOD_REGISTRY[id].description}</span>
        </span>
        <span className="modpage__state">
          <span className="modpage__statelabel">Enabled</span>
          <Toggle
            checked={on}
            size="l"
            label={`${modLabel(id)} enabled`}
            onChange={(next) => toggleMod(id, next)}
          />
        </span>
      </div>

      <ModProperties id={id} />

      {/* §4 buttons: `Done` is the primary — a `--text-primary` fill with a `--bg-shell` label
          — and `Reset to default` is the ghost. Neither is tinted: a button is not a live
          value (§1). `Done` and the bar's `‹ Mods` do the same thing on purpose; one is where
          you look when you have finished, the other where you look when you change your mind,
          and a page with only one of them is missing whichever half you reached for. */}
      <div className="modpage__actions">
        <button type="button" className="modpage__reset" onClick={() => resetMod(id)}>
          Reset to default
        </button>
        <button type="button" className="modpage__done" onClick={closeMod}>
          Done
        </button>
      </div>
    </div>
  );
}
