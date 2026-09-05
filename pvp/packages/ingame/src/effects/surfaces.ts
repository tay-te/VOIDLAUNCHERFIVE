/**
 * Reports the surfaces that carry a shadow to the host, so it can draw them in GL.
 *
 * The overlay's CSS has no blurred shadows. A blurred `box-shadow` is the costliest primitive
 * Ultralight's CPU rasteriser has — a blur pass per element per repaint — and because the shadow
 * extends past its element it also enlarges the damage rectangle, so a change to one card repaints
 * far more than the card. Measured in game on a mod toggle: 43-80 ms a repaint with the authored
 * ramp against 15-25 ms without, more than every other optimisation combined.
 *
 * Dropping the effect is not the same as dropping the design. The host draws these on the GPU that
 * is already rendering the game, underneath the view, where a blur costs nothing — and it draws the
 * *authored* values, read here from the `--shadow-*-gl` tokens that the token build copies verbatim
 * out of `design/tokens.css`. So the overlay shows the Figma shadow, and a designer changing that
 * file changes the launcher's CSS and the overlay together, with nothing to keep in sync by hand.
 *
 * Only in game: under `data-renderer="webview"` the launcher draws its own shadows in CSS, the
 * `-gl` tokens are not defined, and this reports nothing.
 */
import { useEffect } from 'react';
import { getVoid } from '@/bridge/connect';
import type { EffectSurface } from '@/bridge/protocol';

/** A surface worth a shadow: the element to measure, and the authored token to draw it with. */
interface Tracked {
  /** CSS selector. First match wins; absent is fine. */
  selector: string;
  /** Which authored shadow to use. */
  token: string;
}

/**
 * The large surfaces, and only those.
 *
 * Two reasons the list is short. The cost that mattered was blur x area, so the big ambient
 * shadows are the ones worth moving; everything small keeps its shadow in CSS, where it is cheap.
 * And the host draws *underneath* the view — the panel is opaque, so a shadow for anything inside
 * it would be hidden. Only surfaces that sit over the game can be drawn there, which is exactly
 * the set below.
 */
const TRACKED: Tracked[] = [
  { selector: '.v-panel', token: '--shadow-panel-gl' },
  { selector: '.v-palette', token: '--shadow-panel-gl' },
  { selector: '.v-dock', token: '--shadow-dock-gl' },
  { selector: '.v-toolbar', token: '--shadow-toolbar-gl' },
];

/**
 * Parses one CSS `box-shadow` into numbers, so neither Java nor GLSL has to.
 *
 * Deliberately narrow: it reads the shape the design actually authors — `<dx> <dy> <blur> <spread>
 * <rgba()>`, spread optional — and returns null for anything else rather than half-understanding
 * it. A shadow it cannot read is a shadow the host does not draw, which is a missing effect and
 * never a wrong one. A multi-layer shadow uses its first layer, the ambient one in every token here
 * that has two.
 */
export function parseShadow(value: string): EffectSurface['shadow'] | null {
  const text = value.trim();
  if (!text || text === 'none') return null;

  // Split on commas outside rgba(...), then take the first layer.
  const layer = text.split(/,(?![^(]*\))/)[0]!.trim();

  const colorMatch = layer.match(/rgba?\(([^)]+)\)/i);
  if (!colorMatch) return null;
  const channels = colorMatch[1]!.split(/[,/]/).map((c) => Number.parseFloat(c.trim()));
  if (channels.length < 3 || channels.slice(0, 3).some((c) => Number.isNaN(c))) return null;

  const lengths = layer
    .replace(/rgba?\([^)]+\)/i, ' ')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((n) => Number.parseFloat(n));
  if (lengths.length < 3 || lengths.slice(0, 3).some((n) => Number.isNaN(n))) return null;

  const [dx = 0, dy = 0, blur = 0, spread = 0] = lengths;
  return {
    dx,
    dy,
    blur,
    spread,
    color: [
      (channels[0] ?? 0) / 255,
      (channels[1] ?? 0) / 255,
      (channels[2] ?? 0) / 255,
      channels[3] ?? 1,
    ],
  };
}

/** Measures every tracked surface that is currently on screen. */
export function measureSurfaces(doc: Document): EffectSurface[] {
  const rootStyles = getComputedStyle(doc.documentElement);
  const out: EffectSurface[] = [];

  for (const { selector, token } of TRACKED) {
    const element = doc.querySelector(selector);
    if (!element) continue;
    const shadow = parseShadow(rootStyles.getPropertyValue(token));
    if (!shadow) continue;

    const box = element.getBoundingClientRect();
    if (box.width <= 0 || box.height <= 0) continue;
    // Read the radius off the element, not a token: a component may round its corners differently
    // from the default, and the shadow has to follow the shape it sits under.
    const radius = Number.parseFloat(getComputedStyle(element).borderTopLeftRadius) || 0;

    out.push({
      id: selector.replace(/^\./, ''),
      x: box.left,
      y: box.top,
      w: box.width,
      h: box.height,
      radius,
      shadow,
    });
  }
  return out;
}

/**
 * Keeps the host's idea of the shadowed surfaces in step with the layout.
 *
 * Sends only when the measurement changed. The call crosses into Java and the host holds the result
 * until the next one, so re-sending an identical set would be pure waste for a value that moves
 * when a panel opens or the window resizes — and this file exists to remove waste, not add it.
 */
export function useEffectSurfaces(active: boolean): void {
  useEffect(() => {
    if (typeof document === 'undefined') return undefined;

    let last = '';
    const push = () => {
      const surfaces = active ? measureSurfaces(document) : [];
      const key = JSON.stringify(surfaces);
      if (key === last) return;
      last = key;
      try {
        getVoid().setSurfaces(surfaces);
      } catch {
        // No host, or one without the call. The overlay renders fine; it just has no shadows.
      }
    };

    push();
    window.addEventListener('resize', push);
    // The panel is not at its final size on the frame it mounts.
    const settle = window.setTimeout(push, 250);
    return () => {
      window.removeEventListener('resize', push);
      window.clearTimeout(settle);
    };
  }, [active]);
}
