/**
 * The inspector — the properties panel beside the items on the Mods screen.
 *
 * `inspector` is one of the two independent controls of contract §7: `open` or
 * `closed` regardless of whether `layout` is `grid` or `list`, which is four states
 * and not three modes. Selecting a mod opens it; the bar toggle closes and reopens
 * it. It never navigates anywhere — the overlay interrupts a live match, and a
 * screen you have to come back from costs a round.
 *
 * The head is the frames' own three lines (`289:3024`, `289:5523`): a `SELECTED MOD`
 * eyebrow, the mod name at display size, and a meta line reading
 * `HUD · ENABLED · R-SHIFT`. Enablement is the `ENABLED` switch beside them — the one
 * control that is in the same place whatever mod is selected — and it is *not* a
 * property row, which is what frame `289:5523` settles and what §8 already said.
 *
 * The foot is `Reset to default` as a bare label on the left and the one filled
 * button in the whole overlay, `Done`, on the right. There is no `Edit position`
 * button any more: §8 puts placement on the preview, which is where the panel now
 * draws it. The standalone HUD editor is still one ⌘K away (`Edit HUD layout`).
 *
 * What goes *inside* — the preview, the spatial handles and §8's property structure
 * — is `ModSettingsScreen.tsx`, because that is where the registry knowledge lives.
 */

import { Toggle } from '@/ui';
import { type ModId } from '@/bridge/protocol';
import { MOD_CATEGORY, hueStyle, modLabel } from '@/registry';
import { isModOn, useModSettings, useVoidStore } from '@/store/store';
import { CloseGlyph } from './cell-art';
import { ModProperties } from './ModSettingsScreen';
import { keybindLabel } from './settings-format';

export interface ModInspectorProps {
  id: ModId;
  /** Whether the panel is showing. Always mounted, so the slide can transition. */
  open: boolean;
}

/** `HUD  ·  ENABLED  ·  R-SHIFT` — the frames' spaced separator, preserved in CSS. */
function metaLine(id: ModId, on: boolean, keybind: string | null): string {
  const parts = [MOD_CATEGORY[id], on ? 'ENABLED' : 'DISABLED'];
  parts.push(keybind === null || keybind === 'None' ? 'NO KEYBIND' : keybind.toUpperCase());
  return parts.join('   ·   ');
}

export function ModInspector({ id, open }: ModInspectorProps): React.ReactElement {
  const settings = useModSettings(id);
  const on = useVoidStore((s) => isModOn(s.loadout, id));
  const toggleMod = useVoidStore((s) => s.toggleMod);
  const resetMod = useVoidStore((s) => s.resetMod);
  const setInspector = useVoidStore((s) => s.setInspector);

  const keybindKey = 'keybind' in settings ? 'keybind' : 'key' in settings ? 'key' : null;
  const keybind = keybindKey ? keybindLabel(settings[keybindKey] ?? null) : null;

  return (
    <div
      className={`inspector${open ? ' inspector--open' : ''}`}
      style={hueStyle(id)}
      aria-hidden={open ? undefined : true}
    >
      <div className="inspector__head">
        <span className="inspector__ident">
          <span className="inspector__eyebrow">Selected mod</span>
          <span className="inspector__title">{modLabel(id)}</span>
          <span className="inspector__meta">{metaLine(id, on, keybind)}</span>
        </span>
        <span className="inspector__state">
          <span className="inspector__statelabel">Enabled</span>
          <Toggle
            checked={on}
            size="l"
            label={`${modLabel(id)} enabled`}
            onChange={(next) => toggleMod(id, next)}
          />
        </span>
        <button
          type="button"
          className="inspector__close"
          aria-label="Close properties"
          onClick={() => setInspector('closed')}
        >
          <CloseGlyph />
        </button>
      </div>

      <ModProperties id={id} />

      <div className="inspector__actions">
        <button type="button" className="inspector__reset" onClick={() => resetMod(id)}>
          Reset to default
        </button>
        <button
          type="button"
          className="inspector__done"
          onClick={() => setInspector('closed')}
        >
          Done
        </button>
      </div>
    </div>
  );
}
