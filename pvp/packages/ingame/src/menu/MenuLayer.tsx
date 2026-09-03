/**
 * The menu layer. Mounted only while `VoidMenuScreen` is open — the `menu`
 * channel is the single source of that (§6.2: both layers are drawn into the
 * same Ultralight view, and this flag decides which is visible).
 *
 * Keyboard, per §6.3:
 *   · Escape closes the menu — unless a text field has focus, in which case it
 *     leaves the field first. Java asks `window.void.__hasFocus()` before it
 *     acts on Escape itself, so the two agree.
 *   · ⌘K / Ctrl-K opens the quick palette over whatever is up.
 *   · Right Shift is **not** bound here. It is a Java `KeyBinding`, and in HUD
 *     mode Ultralight receives no input at all.
 */

import { useEffect } from 'react';
import { useVoidStore } from '@/store/store';
import { hasTextFocus } from '@/bridge/connect';
import { ModsScreen } from './ModsScreen';
import { ModSettingsScreen } from './ModSettingsScreen';
import { LoadoutsScreen } from './LoadoutsScreen';
import { PartyScreen } from './PartyScreen';
import { HudEditorScreen } from './HudEditorScreen';
import { QuickPalette } from '@/palette/QuickPalette';

export function MenuLayer() {
  const route = useVoidStore((s) => s.route);
  const paletteOpen = useVoidStore((s) => s.paletteOpen);
  const setPaletteOpen = useVoidStore((s) => s.setPaletteOpen);
  const setRoute = useVoidStore((s) => s.setRoute);
  const closeMenu = useVoidStore((s) => s.closeMenu);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && (e.key === 'k' || e.key === 'K')) {
        e.preventDefault();
        setPaletteOpen(!useVoidStore.getState().paletteOpen);
        return;
      }
      if (e.key !== 'Escape') return;
      if (useVoidStore.getState().paletteOpen) return; // the palette handles its own Escape
      if (hasTextFocus()) {
        // Give the field up first; a second Escape then closes the menu.
        (document.activeElement as HTMLElement | null)?.blur();
        e.preventDefault();
        return;
      }
      e.preventDefault();
      if (useVoidStore.getState().route.name === 'hud-editor') {
        setRoute({ name: 'mods' });
        return;
      }
      closeMenu();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [closeMenu, setPaletteOpen, setRoute]);

  const editing = route.name === 'hud-editor';

  return (
    <div className="menu-layer">
      {/* The HUD editor supplies its own lighter scrim. */}
      {!editing && <div className="menu-layer__dim" />}
      {route.name === 'mods' && <ModsScreen />}
      {route.name === 'mod-settings' && <ModSettingsScreen id={route.mod} />}
      {route.name === 'loadouts' && <LoadoutsScreen />}
      {route.name === 'party' && <PartyScreen />}
      {editing && <HudEditorScreen />}
      {paletteOpen && <QuickPalette />}
    </div>
  );
}
