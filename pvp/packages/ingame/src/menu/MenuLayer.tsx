/**
 * The menu layer. Mounted while `VoidMenuScreen` is open — the `menu` channel is
 * the single source of that (§6.2: both layers are drawn into the same Ultralight
 * view, and this flag decides which is visible) — and once more, hidden, for the
 * page's warm-up (`App.tsx`, `useMenuWarmup`), which is the only time `hidden` is
 * ever true.
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
import { cx } from '@/ui';
import { useDirectWheel } from '@/menu/wheel';
import { useVoidStore } from '@/store/store';
import { hasTextFocus } from '@/bridge/connect';
import { ModsScreen } from './ModsScreen';
import { LoadoutsScreen } from './LoadoutsScreen';
import { PartyScreen } from './PartyScreen';
import { HudEditorScreen } from './HudEditorScreen';
import { QuickPalette } from '@/palette/QuickPalette';

/** Props for {@link MenuLayer}. */
export interface MenuLayerProps {
  /** Laid out and rasterised, but never on screen and deaf to input: the warm-up state. */
  hidden?: boolean;
  /** Fading up. Interactive from the first frame — the animation is opacity, not a gate. */
  entering?: boolean;
  /**
   * On screen and fading out: the channel has already said `menu:false` and Minecraft has taken
   * the mouse back, so the layer is inert, but it is still drawn until the fade ends
   * (`App.tsx`, `useMenuPhase`).
   */
  exiting?: boolean;
  /**
   * Called when the exit fade has actually finished, so `App` can drop the tree on the frame
   * after the last one that drew anything rather than on a timer that has to be guessed longer
   * than the animation.
   */
  onExitEnd?: () => void;
}

export function MenuLayer({
  hidden = false,
  entering = false,
  exiting = false,
  onExitEnd,
}: MenuLayerProps) {
  const route = useVoidStore((s) => s.route);
  const paletteOpen = useVoidStore((s) => s.paletteOpen);
  const setPaletteOpen = useVoidStore((s) => s.setPaletteOpen);
  const setRoute = useVoidStore((s) => s.setRoute);
  const closeMenu = useVoidStore((s) => s.closeMenu);

  useEffect(() => {
    // Not bound during the warm-up. In game this is belt and braces — Ultralight is sent no
    // input at all in HUD mode — but the browser harness does deliver keys, and Escape belongs to
    // a menu that is on screen.
    if (hidden || exiting) return undefined;
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
  }, [hidden, exiting, closeMenu, setPaletteOpen, setRoute]);

  // The engine's smooth-scroll animation is cancelled while the menu is up; see wheel.ts.
  useDirectWheel(!hidden && !exiting);

  const editing = route.name === 'hud-editor';

  return (
    <div
      className={cx(
        'menu-layer',
        hidden && 'menu-layer--hidden',
        !hidden && entering && 'menu-layer--entering',
        exiting && 'menu-layer--exiting',
      )}
      aria-hidden={hidden || exiting}
      // The close is over when the fade says so, not when a timer guesses. Both extra tests are
      // load-bearing: `animationend` bubbles, so a child's own animation — the palette's
      // entrance, a version dropdown — would otherwise end the close mid-fade, and only
      // `void-menu-out` is this layer leaving.
      onAnimationEnd={(event) => {
        if (
          exiting &&
          event.target === event.currentTarget &&
          event.animationName === 'void-menu-out'
        ) {
          onExitEnd?.();
        }
      }}
    >
      {/* The HUD editor supplies its own lighter scrim. */}
      {!editing && <div className="menu-layer__dim" />}
      {route.name === 'mods' && <ModsScreen />}
      {route.name === 'loadouts' && <LoadoutsScreen />}
      {route.name === 'party' && <PartyScreen />}
      {editing && <HudEditorScreen />}
      {paletteOpen && <QuickPalette />}
    </div>
  );
}
