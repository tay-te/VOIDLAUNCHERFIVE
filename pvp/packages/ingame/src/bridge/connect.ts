/**
 * Boot: pick a bridge, subscribe the five channels, report focus.
 *
 * §6.5 / bridge.json — Java → JS is push through `void.on(...)`; JS → Java is a
 * synchronous call. There is no fetch, no XHR and no socket in this bundle: the
 * page is loaded off the JAR classpath and the machine may have no internet.
 */

import {
  createFakeVoid,
  installVoidShim,
  type VoidBridge,
} from './protocol';
import { useVoidStore } from '@/store/store';

let bridge: VoidBridge | null = null;
let usingFake = false;

/** The bridge, once {@link connectBridge} has run. */
export function getVoid(): VoidBridge {
  if (!bridge) throw new Error('window.void is not connected yet — call connectBridge() first');
  return bridge;
}

/** True when the app is running against `createFakeVoid()` rather than the mod. */
export function isDebugBridge(): boolean {
  return usingFake;
}

/**
 * True while a text input owns the keyboard.
 *
 * `VoidMenuScreen` asks this before it acts on Escape (§6.3): with a search
 * field focused, Escape must reach the page so it can clear the field, and only
 * an unfocused Escape closes the screen. Read straight off the document — no
 * store round-trip, so it can never be one render behind the real focus.
 */
export function hasTextFocus(): boolean {
  if (typeof document === 'undefined') return false;
  const el = document.activeElement as HTMLElement | null;
  if (!el) return false;
  if (el.isContentEditable) return true;
  const tag = el.tagName;
  if (tag === 'TEXTAREA') return true;
  if (tag !== 'INPUT') return false;
  const type = (el as HTMLInputElement).type;
  return type !== 'checkbox' && type !== 'radio' && type !== 'button' && type !== 'submit';
}

export interface ConnectResult {
  bridge: VoidBridge;
  /** True when the fake bridge is in use, i.e. the DEBUG badge should show. */
  debug: boolean;
  /** Tear down every subscription. Used by tests. */
  dispose(): void;
}

/**
 * Install `window.void` and wire it to the store.
 *
 * In game the Java host has already installed `window.__void_native`, and
 * `installVoidShim()` builds the bridge on top of it. In a browser — or with
 * `?debug` in the query string — `createFakeVoid()` stands in.
 */
export function connectBridge(): ConnectResult {
  const debugRequested =
    typeof location !== 'undefined' && /(^|[?&])debug($|[=&])/.test(location.search);
  const hasNative = typeof window !== 'undefined' && typeof window.__void_native === 'function';

  if (hasNative && !debugRequested) {
    bridge = installVoidShim();
    usingFake = false;
  } else {
    bridge = createFakeVoid({ menuOpen: debugRequested });
    usingFake = true;
  }

  // The shim's own __hasFocus tracks the menu channel. The Java side needs the
  // narrower question — is a text field eating the keyboard right now — so the
  // app owns this one. Assigning it here keeps `window.void.__hasFocus()`
  // correct for the host no matter which bridge is underneath.
  bridge.__hasFocus = hasTextFocus;

  const store = useVoidStore.getState();

  // The bridge exposes no loadout-library accessor (see VoidState.library). The
  // fake one does, so seed the store from it when it is there.
  const readLibrary = (bridge as { __loadouts?: () => typeof store.library }).__loadouts;
  if (readLibrary) useVoidStore.setState({ library: readLibrary.call(bridge) });

  const offs = [
    bridge.on('loadout', store.applyLoadout),
    bridge.on('keys', store.applyKeys),
    bridge.on('tick', store.applyTick),
    bridge.on('server', store.applyServer),
    bridge.on('menu', store.applyMenu),
  ];

  return {
    bridge,
    debug: usingFake,
    dispose() {
      for (const off of offs) off();
    },
  };
}
