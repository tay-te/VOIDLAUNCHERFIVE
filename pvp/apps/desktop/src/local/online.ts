/**
 * Is the machine online? — Home's Offline state (`81:198`) and the play bar's fallback to
 * the title screen.
 *
 * `navigator.onLine` is the webview's own answer (WebView2, WKWebView and WebKitGTK all
 * implement it from the OS network state), and its `online` / `offline` events are what
 * move it. It is optimistic — a captive portal reads as online — which is the right side
 * to err on: the cost of a false "online" is one failed ping, which Home already handles as
 * *Server unreachable*.
 */

import { useSyncExternalStore } from 'react';

function subscribe(onChange: () => void): () => void {
  window.addEventListener('online', onChange);
  window.addEventListener('offline', onChange);
  return () => {
    window.removeEventListener('online', onChange);
    window.removeEventListener('offline', onChange);
  };
}

const snapshot = (): boolean => (typeof navigator === 'undefined' ? true : navigator.onLine !== false);

export function useOnline(): boolean {
  return useSyncExternalStore(subscribe, snapshot, () => true);
}
