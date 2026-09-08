/**
 * Two layers into one Ultralight view (§6.2): the HUD, always mounted and never
 * interactive, and the menu, mounted while `VoidMenuScreen` is open — and once
 * more, hidden, shortly after the page loads. See {@link useMenuWarmup}.
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import { useVoidStore } from '@/store/store';
import { HudLayer } from '@/hud/HudLayer';
import { useEffectSurfaces } from '@/effects/surfaces';
import { MenuLayer } from '@/menu/MenuLayer';
import { isDebugBridge } from '@/bridge/connect';
import { FAKE_MOD_COUNT } from '@/dev/fake-mods';

export interface AppProps {
  /**
   * Browser harness: render at the authored 1300 × 820 frame size and put the
   * matching Figma export behind the UI. Never true in game.
   */
  debugFrame?: boolean;
  /** File name under `design/screens` to show behind the UI in dev. */
  backdrop?: string;
}

/** How long the menu stays mounted-but-hidden after the page loads. */
const WARMUP_MS = 1500;

/**
 * The **backstop** for the close, not its schedule.
 *
 * The layer now unmounts on the fade's own `animationend` (see {@link useMenuPhase}), so this
 * number no longer has to be kept in step with the CSS — which is just as well, because keeping
 * it in step was never quite possible. Measured in game, an 80ms fade reached `animationend`
 * **99 ms** after the page was told to close: the animation cannot start until the class has
 * been styled and painted, and that lag is a frame of whatever the UI thread is doing. The
 * 100 ms this used to be was therefore within a millisecond of unmounting the layer *during*
 * its own fade, which is exactly how a close leaves a ghost — the last frame with content is a
 * half-visible menu, and a page that then has nothing to draw submits no commands to replace it.
 *
 * So the timer is now only for the case where the event never comes at all (no animation ran,
 * because the layer was hidden or the engine dropped it), and it sits far past any honest fade.
 * Almost none of it is visible: `both` holds the layer at opacity 0 from the moment the
 * animation ends.
 *
 * `VoidClient.onMenuClosed` opens a window on the other side for the same reason — the host may
 * blank the view's render target on any frame the page repaints for the next 450 ms, which
 * outlasts this timer — so the frame where the layer finally goes away is covered however it gets
 * there, by `animationend` or by this backstop.
 */
const EXIT_FALLBACK_MS = 400;

/** The open fade, 110ms in CSS, plus a frame. See {@link useMenuPhase} for why it is timed. */
const ENTER_MS = 130;

/** Where the menu is in its open/close animation. */
type MenuPhase = 'shut' | 'entering' | 'open' | 'exiting';

/**
 * The menu's own lifecycle, which is longer than the channel's.
 *
 * `menu:true` / `menu:false` are instants; a fade is not. This turns the two instants into four
 * phases so the layer can be mounted before it is visible and stay mounted after it is told to
 * go — see `.menu-layer--entering` / `--exiting` in overlay.css.
 *
 * **Why `entering` ends rather than sticking.** A CSS animation is a standing instruction to the
 * engine, and an element carrying one can be kept on a composited layer for as long as it does.
 * That is not a hypothetical here: a `transform` left on the nav tabs is exactly what made their
 * labels composite over themselves, and the whole menu is a much bigger surface to get that
 * wrong on. So the class is dropped once the animation has run; the steady state is a plain
 * element with no animation, no transform and no opacity of its own.
 */
function useMenuPhase(menuOpen: boolean): { phase: MenuPhase; onExitEnd: () => void } {
  const [phase, setPhase] = useState<MenuPhase>(menuOpen ? 'entering' : 'shut');
  const wasOpen = useRef(menuOpen);

  useEffect(() => {
    if (menuOpen && !wasOpen.current) setPhase('entering');
    else if (!menuOpen && wasOpen.current) setPhase('exiting');
    wasOpen.current = menuOpen;
  }, [menuOpen]);

  useEffect(() => {
    if (phase !== 'entering' && phase !== 'exiting') return undefined;
    const settled: MenuPhase = phase === 'entering' ? 'open' : 'shut';
    const timer = window.setTimeout(
      () => setPhase(settled),
      phase === 'entering' ? ENTER_MS : EXIT_FALLBACK_MS,
    );
    return () => window.clearTimeout(timer);
  }, [phase]);

  // The exit's real end. `MenuLayer` calls this from the fade's own `animationend`, so the
  // unmount lands on the frame after the last one that drew anything — rather than on a number
  // that has to be guessed longer than the animation and shorter than a delay anyone notices.
  const onExitEnd = useCallback(() => {
    setPhase((current) => (current === 'exiting' ? 'shut' : current));
  }, []);

  return { phase, onExitEnd };
}

/**
 * Draws the menu once, hidden, so the first real open is not the first time
 * Ultralight has seen it.
 *
 * The first `menu:true` of a process cost 118-135 ms between Right Shift and the pixels
 * changing, against 20-28 ms for every open after it — measured in game, and almost none of it
 * React (0.2 ms of JS). It is the engine meeting a thousand nodes, a font size and a glyph it has
 * never rasterised. Those caches outlive the render tree, so the cost only has to be paid once,
 * and it does not have to be paid by the player: mounting the layer hidden for a moment at page
 * load pays it while the world is still coming up, and the first open then measures like the
 * tenth.
 *
 * **Why it unmounts again rather than staying hidden forever.** Staying was tried and it is
 * wrong. `visibility: hidden` is the only way to hide the layer that still warms it — `display:
 * none` and `opacity: 0` are skipped so completely that the first open pays the full cold cost
 * anyway — and a layer that is hidden for good leaves the page's last painted frame standing:
 * whatever it managed to draw is what the view's render target keeps, and an idle page never
 * replaces it. Warm the engine, then let the tree go.
 *
 * **It ends on a timer and nothing else.** It used to wait for the loadout before starting the
 * clock, and in a client with no launcher attached the loadout never arrives at all — `onInit`
 * is the only thing that pushes one — so the timer never started, the layer stayed mounted for
 * the life of the process, and the one frame it painted stayed on screen over the world for the
 * life of the process with it. That is the ghost this comment used to describe as a close-path
 * problem; it was not one. The premise of the wait was also wrong: the mods grid is built from
 * `MOD_REGISTRY`, not the loadout, so it has all 12 (or 24) tiles from the first render and
 * there was never an empty screen to avoid laying out. The loadout only decides which of them
 * read as enabled.
 *
 * Nothing inside the layer may paint while it is hidden — see `.menu-layer--hidden *` in
 * overlay.css, which is what makes that true and why it has to be stated in CSS rather than
 * left to the inherited `visibility`.
 */
function useMenuWarmup(menuOpen: boolean): boolean {
  const [warming, setWarming] = useState(true);

  useEffect(() => {
    if (!warming) return undefined;
    const timer = window.setTimeout(() => setWarming(false), WARMUP_MS);
    return () => window.clearTimeout(timer);
  }, [warming]);

  // A real open ends the warm-up early: from here on the menu mounts and unmounts with the
  // channel, which is the path whose close is known to be clean.
  useEffect(() => {
    if (menuOpen) setWarming(false);
  }, [menuOpen]);

  return warming;
}

export function App({ debugFrame, backdrop }: AppProps) {
  const menuOpen = useVoidStore((s) => s.menuOpen);
  // The host draws the panel's shadow in GL; tell it where the panel is. Only while the menu is up,
  // because that is the only time any of the tracked surfaces exist.
  useEffectSurfaces(menuOpen);
  const editing = useVoidStore((s) => s.route.name === 'hud-editor');
  const warming = useMenuWarmup(menuOpen);
  const { phase, onExitEnd } = useMenuPhase(menuOpen);
  const exiting = phase === 'exiting';
  // On screen: open, or still fading out. Everything that asks "is the menu up" for *drawing*
  // has to ask this rather than `menuOpen`, or the HUD editor would mount a second copy of the
  // HUD under a menu layer that is still showing its own.
  const onScreen = phase !== 'shut';

  return (
    <div className={`v-app void-app${debugFrame ? ' void-app--debug' : ''}`}>
      {debugFrame && backdrop && (
        <div
          className="void-debug-backdrop"
          style={{ backgroundImage: `url(/__design/${backdrop})` }}
        />
      )}

      {/* The editor renders its own copy of the HUD, with drag handlers. */}
      {/* `onScreen`, not `menuOpen`: the chips are dimmed because a panel is over them, and
          during the exit fade one still is. Keyed to the channel instead, the HUD jumped back
          to full brightness on the first frame of the close while the panel was still there —
          the same one-frame step the scrim used to make, at the screen edges instead of behind
          the panel. It now brightens once the layer is gone. */}
      {!(onScreen && editing) && <HudLayer dimmed={onScreen} />}

      {(onScreen || warming) && (
        <MenuLayer
          hidden={!onScreen}
          entering={phase === 'entering'}
          exiting={exiting}
          onExitEnd={onExitEnd}
        />
      )}

      {/* One badge, whatever is true of this build. The fake-mod count is on it because a
          screenshot of a padded grid must never be mistaken for the real registry — it is the
          only marker that survives being pasted somewhere else. */}
      {(isDebugBridge() || FAKE_MOD_COUNT > 0) && (
        <span className="void-debug-badge">
          {[isDebugBridge() ? 'debug' : null, FAKE_MOD_COUNT > 0 ? `fake ×${FAKE_MOD_COUNT}` : null]
            .filter(Boolean)
            .join('  ·  ')}
        </span>
      )}
    </div>
  );
}
