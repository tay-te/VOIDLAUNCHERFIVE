/**
 * The launcher-update prompt: one banner in the existing `.banners` stack, nothing new
 * in the design vocabulary.
 *
 * Rust checks the channel's `latest.json` at startup and emits `update:available`; the
 * Settings screen's *Check now* lands here too (`useUpdate.getState().found`). *Restart
 * to update* calls `updater_install`, which streams `update:progress` while it downloads
 * and verifies the signed installer, then relaunches. Dismissing hides the banner for
 * this run only; the next start asks again.
 */

import { create } from 'zustand';

import { Button, IconButton } from '@void/ui';

import type { UpdateInfo, UpdateProgress } from '../local/protocol';
import { errorText, invoke, listen } from '../local/tauri';
import { formatBytes, useLaunch } from '../stores/launch';

interface UpdateState {
  info: UpdateInfo | null;
  progress: UpdateProgress | null;
  installing: boolean;
  dismissed: boolean;
  error: string | null;
  /** Record a check result (startup event or Settings → Check now). */
  found: (info: UpdateInfo) => void;
  install: () => Promise<void>;
  dismiss: () => void;
}

export const useUpdate = create<UpdateState>((set, get) => ({
  info: null,
  progress: null,
  installing: false,
  dismissed: false,
  error: null,
  found: (info) => set({ info, dismissed: false, error: null }),
  install: async () => {
    if (get().installing) return;
    set({ installing: true, error: null, progress: null });
    try {
      await invoke('updater_install');
      // On success the process restarts; reaching here means it did not need to.
      set({ installing: false });
    } catch (e) {
      set({ installing: false, error: errorText(e) });
    }
  },
  dismiss: () => set({ dismissed: true }),
}));

export async function wireUpdateEvents(): Promise<() => void> {
  const offs = await Promise.all([
    listen('update:available', (info) => useUpdate.getState().found(info)),
    listen('update:progress', (progress) => useUpdate.setState({ progress })),
  ]);
  return () => offs.forEach((off) => off());
}

function progressText(p: UpdateProgress | null): string {
  if (!p) return 'Starting the download…';
  if (p.phase === 'installing') return 'Installing — the launcher will restart…';
  return p.total
    ? `Downloading ${formatBytes(p.downloaded)} of ${formatBytes(p.total)}…`
    : `Downloading ${formatBytes(p.downloaded)}…`;
}

export function UpdatePrompt() {
  const { info, progress, installing, dismissed, error, install, dismiss } = useUpdate();
  const phase = useLaunch((s) => s.phase);

  if (!info?.available || dismissed) return null;
  const busy = phase !== 'idle';

  return (
    <div className="banner banner--session" role="status">
      <span className="banner__text">
        {error
          ? error
          : installing
            ? progressText(progress)
            : busy
              ? `VOID ${info.version} is ready — it installs once Minecraft is closed.`
              : `VOID ${info.version} is available.`}
      </span>
      {!installing ? (
        <Button variant="raised" disabled={busy} onClick={() => void install()}>
          Restart to update
        </Button>
      ) : null}
      {!installing ? <IconButton icon="close" size="close" label="Later" onClick={dismiss} /> : null}
    </div>
  );
}
