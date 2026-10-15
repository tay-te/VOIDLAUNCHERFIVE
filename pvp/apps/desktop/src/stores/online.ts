/**
 * The online session — whether `void-online` is signed in to the backend — and the one place
 * its events are routed to the stores that show them.
 *
 * Rust signs in to the backend by itself whenever a Microsoft account is signed in (startup
 * restore or the device flow) and signs out with it; this store only reflects that. A move to
 * `online` refetches cosmetics and friends; anything else drops them back to their offline
 * copies.
 */

import { create } from 'zustand';

import type { OnlineStatus } from '../local/protocol';
import { invoke, listen } from '../local/tauri';
import { useCosmetics } from './cosmetics';
import { useFriends } from './friends';

interface OnlineState {
  status: OnlineStatus;
  hydrate: () => Promise<void>;
}

export const useOnlineStatus = create<OnlineState>((set) => ({
  status: { state: 'signed_out' },
  hydrate: async () => {
    try {
      set({ status: await invoke('online_status') });
    } catch {
      set({ status: { state: 'disabled' } });
    }
  },
}));

/** A sentence for the status, for the places that explain why something is unavailable. */
export function onlineText(status: OnlineStatus): string {
  switch (status.state) {
    case 'online':
      return `Online as ${status.name}`;
    case 'connecting':
      return 'Connecting…';
    case 'disabled':
      return 'Online features aren’t available in this build.';
    case 'error':
      return status.message;
    default:
      return 'Sign in with a Microsoft account to use online features.';
  }
}

/** Subscribe to `online:*`. Called once from `App`; resolves to the unsubscribe. */
export async function wireOnlineEvents(): Promise<() => void> {
  const offs = await Promise.all([
    listen('online:status', (status) => {
      const was = useOnlineStatus.getState().status.state;
      useOnlineStatus.setState({ status });
      if (status.state === 'online' && was !== 'online') {
        void useCosmetics.getState().refresh();
        void useFriends.getState().refresh();
      } else if (status.state !== 'online' && status.state !== 'connecting') {
        useCosmetics.getState().apply({ online: false, owned: [], equipped: null });
        useFriends.getState().goOffline();
      }
    }),
    listen('online:cosmetics', (state) => useCosmetics.getState().apply(state)),
    listen('online:friends', (list) => useFriends.getState().apply(list)),
  ]);
  return () => offs.forEach((off) => off());
}
