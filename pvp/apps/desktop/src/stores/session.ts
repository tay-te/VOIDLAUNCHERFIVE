/**
 * Accounts, system info and Java — the "who and what machine" store.
 *
 * Zustand rather than context: the dock's PlayerChip, the Settings account rows and
 * the launch guard all read this, and none of them should re-render because a log
 * line arrived.
 *
 * Several accounts: `accounts` is every account the launcher has signed in, `account` is
 * the active one. Signing in (Microsoft or offline) adds an account and makes it active;
 * `switchAccount` and `removeAccount` do the rest. Tokens never reach the webview.
 */

import { create } from 'zustand';

import type { Account, AuthStatus, JavaStatus, SystemInfo } from '../local/protocol';
import { errorText, invoke, listen } from '../local/tauri';

interface SessionState {
  account: Account | null;
  /** Every signed-in account, active one included. */
  accounts: Account[];
  system: SystemInfo | null;
  java: JavaStatus | null;
  /** Set while the device flow is in the air; drives the Settings sign-in panel. */
  deviceCode: { user_code: string; verification_uri: string } | null;
  authStatus: AuthStatus | null;
  error: string | null;
  loading: boolean;

  hydrate: () => Promise<void>;
  refreshAccounts: () => Promise<void>;
  loginMicrosoft: () => Promise<void>;
  loginOffline: (name: string) => Promise<void>;
  /** Signs the active account out; another account, if any, becomes active. */
  logout: () => Promise<void>;
  switchAccount: (uuid: string) => Promise<void>;
  removeAccount: (uuid: string) => Promise<void>;
  refreshJava: () => Promise<void>;
  dismissError: () => void;
}

export const useSession = create<SessionState>((set, get) => ({
  account: null,
  accounts: [],
  system: null,
  java: null,
  deviceCode: null,
  authStatus: null,
  error: null,
  loading: true,

  hydrate: async () => {
    try {
      const [account, list, system, java] = await Promise.all([
        invoke('auth_current'),
        invoke('auth_accounts'),
        invoke('system_info'),
        invoke('java_status'),
      ]);
      set({ account, accounts: list.accounts, system, java, loading: false });
    } catch (e) {
      set({ error: errorText(e), loading: false });
    }
  },

  refreshAccounts: async () => {
    try {
      const list = await invoke('auth_accounts');
      set({ accounts: list.accounts });
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  loginMicrosoft: async () => {
    set({ error: null, authStatus: null });
    const before = get().account?.uuid ?? null;
    try {
      const code = await invoke('auth_login');
      // The device flow runs on a background task, and on a warm keychain it can
      // finish before this promise resolves. Showing the code after a new account has
      // already landed would leave the panel asking the player to type a code that no
      // longer means anything, so the arrival of an account wins.
      if ((get().account?.uuid ?? null) === before) {
        set({
          deviceCode: { user_code: code.user_code, verification_uri: code.verification_uri },
        });
      }
    } catch (e) {
      set({ error: errorText(e), deviceCode: null });
    }
  },

  loginOffline: async (name) => {
    set({ error: null });
    try {
      const account = await invoke('auth_offline', { name });
      set({ account, deviceCode: null, authStatus: null });
      await get().refreshAccounts();
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  logout: async () => {
    try {
      const next = await invoke('auth_logout');
      set({ account: next, deviceCode: null, authStatus: null });
      await get().refreshAccounts();
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  switchAccount: async (uuid) => {
    if (get().account?.uuid === uuid) return;
    set({ error: null });
    try {
      const account = await invoke('auth_switch', { uuid });
      set({ account });
      await get().refreshAccounts();
    } catch (e) {
      set({ error: errorText(e) });
    }
  },

  removeAccount: async (uuid) => {
    set({ error: null });
    try {
      const list = await invoke('auth_remove', { uuid });
      const active = list.accounts.find((a) => a.uuid === list.active) ?? null;
      set({ accounts: list.accounts, account: active });
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
      void useSession.getState().refreshAccounts();
    }
    if (status.stage === 'failed') {
      useSession.setState({ error: status.message, deviceCode: null });
    }
  });
}
