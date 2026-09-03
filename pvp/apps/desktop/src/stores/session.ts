/**
 * Account, system info and Java — the "who and what machine" store.
 *
 * Zustand rather than context: the dock's PlayerChip, the Settings account row and
 * the launch guard all read this, and none of them should re-render because a log
 * line arrived.
 */

import { create } from 'zustand';

import type { Account, AuthStatus, JavaStatus, SystemInfo } from '../local/protocol';
import { errorText, invoke, listen } from '../local/tauri';

interface SessionState {
  account: Account | null;
  system: SystemInfo | null;
  java: JavaStatus | null;
  /** Set while the device flow is in the air; drives the Settings sign-in panel. */
  deviceCode: { user_code: string; verification_uri: string } | null;
  authStatus: AuthStatus | null;
  error: string | null;
  loading: boolean;

  hydrate: () => Promise<void>;
  loginMicrosoft: () => Promise<void>;
  loginOffline: (name: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshJava: () => Promise<void>;
  dismissError: () => void;
}

export const useSession = create<SessionState>((set, get) => ({
  account: null,
  system: null,
  java: null,
  deviceCode: null,
  authStatus: null,
  error: null,
  loading: true,

  hydrate: async () => {
    try {
      const [account, system, java] = await Promise.all([
        invoke('auth_current'),
        invoke('system_info'),
        invoke('java_status'),
      ]);
      set({ account, system, java, loading: false });
    } catch (e) {
      set({ error: errorText(e), loading: false });
    }
  },

  loginMicrosoft: async () => {
    set({ error: null, authStatus: null });
    try {
      const code = await invoke('auth_login');
      set({ deviceCode: { user_code: code.user_code, verification_uri: code.verification_uri } });
    } catch (e) {
      set({ error: errorText(e), deviceCode: null });
    }
  },

  loginOffline: async (name) => {
    set({ error: null });
    try {
      const account = await invoke('auth_offline', { name });
      set({ account, deviceCode: null, authStatus: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  logout: async () => {
    try {
      await invoke('auth_logout');
      set({ account: null, deviceCode: null, authStatus: null });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  refreshJava: async () => {
    try {
      set({ java: await invoke('java_status') });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  dismissError: () => set({ error: null }),
}));

/**
 * Subscribe to `auth:status`. Called once from `App`; returns the unsubscribe.
 *
 * Kept outside the store so the store stays a plain state container and the tests can
 * drive it by calling actions rather than by faking events.
 */
export async function wireSessionEvents(): Promise<() => void> {
  return listen('auth:status', (status) => {
    useSession.setState({ authStatus: status });
    if (status.stage === 'complete') {
      useSession.setState({ account: status.account, deviceCode: null });
    }
    if (status.stage === 'failed') {
      useSession.setState({ error: status.message, deviceCode: null });
    }
  });
}
