/**
 * The menu layer. Mounted while `VoidMenuScreen` is open — the `menu` channel is
 * the single source of that (§6.2: both layers are drawn into the same Ultralight
 * view, and this flag decides which is visible) — and once more, hidden, for the
 * page's warm-up (`App.tsx`, `useMenuWarmup`), which is the only time `hidden` is
 * ever true.
 *
 * Keyboard, per §6.3:
 *   · **Escape means "up one level"**, and this handler owns every level but the last:
 *
 *     ```
 *     a focused text field   gives up focus, menu stays
 *     the quick palette      closes, menu stays          (QuickPalette's own handler)
 *     a mod's page           back to the grid
 *     the settings page      back to the grid
 *     the HUD editor         back to the grid
 *     the grid               closes the menu
 *     ```
 *
 *     The order of the branches below **is** that table, and it is not free to change: a field
 *     focused inside a mod page must give up focus before the page gives up the route.
 *
 *     Java has to agree with it a frame ahead. `VoidMenuScreen.keyPressed` swallows Escape and
 *     closes the menu unless `UiHost.keepsEscape()` says the page will handle it, and that is
 *     `keepsEscape()` in `bridge/connect.ts` — the same table, expressed as one predicate. If
 *     the two ever disagree the symptom is loud and one-directional: Escape closes the whole
 *     menu from a page. The `esc` key cap the bar draws on the control Escape actually drives
 *     (`ModsScreen`) is the third copy, and the only one the player sees.
 *
 *     A **keybind capture beats all of it**: `VoidMenuScreen` consumes Escape for the capture
 *     before it asks anything, so Escape while binding a key cancels the capture and changes no
 *     route.
 *   · ⌘K / Ctrl-K opens the quick palette over whatever is up.
 *   · Right Shift is **not** bound here. It is a Java `KeyBinding`, and in HUD
 *     mode Ultralight receives no input at all.
 */

import { useEffect } from 'react';
import { cx } from '@/ui';
import { isEscape } from '@/menu/keys';
import { useDirectWheel } from '@/menu/wheel';
import { useVoidStore } from '@/store/store';
import { hasTextFocus } from '@/bridge/connect';
import { ModsScreen } from './ModsScreen';
import { LoadoutsScreen } from './LoadoutsScreen';
import { PartyScreen } from './PartyScreen';
import { ReviewScreen } from './ReviewScreen';
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
      // Not `e.key === 'Escape'`: in game it arrives as `Unidentified` with `which` 27.
      // See `keys.ts`, which is where that measurement lives.
      if (!isEscape(e)) return;
      if (useVoidStore.getState().paletteOpen) return; // the palette handles its own Escape
      if (hasTextFocus()) {
        // Give the field up first; a second Escape then takes the level above it.
        (document.activeElement as HTMLElement | null)?.blur();
        e.preventDefault();
        return;
      }
      e.preventDefault();
      const route = useVoidStore.getState().route;
      if (route.name === 'hud-editor' || route.name === 'mod' || route.name === 'settings') {
        // Up one level. Both of these are places you went *from* the grid, so the grid is what
        // is above them; neither is a place Escape should close the whole menu out of.
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
      {/* One screen, two routes: the mods shell owns the panel box and draws either the items
          or one mod's page inside it (`ModsScreen`). Splitting them here would mean two
          components solving the same geometry, and the box moving when they disagreed. */}
      {(route.name === 'mods' || route.name === 'mod' || route.name === 'settings') && (
        <ModsScreen />
      )}
      {route.name === 'loadouts' && <LoadoutsScreen />}
      {route.name === 'party' && <PartyScreen />}
      {route.name === 'review' && <ReviewScreen />}
      {editing && <HudEditorScreen />}
      {paletteOpen && <QuickPalette />}
    </div>
  );
}
