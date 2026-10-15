/**
 * Settings — the gear, over whichever screen is up.
 *
 * §8.3 draws the line this screen respects: **if it changes how the game plays it is
 * in the loadout, not here.** So there are no mod toggles on this screen. What is here
 * is accounts, Java, RAM and JVM arguments, the game window and folder, hotkeys, theme,
 * hide-to-tray, Discord status, data folder and logs, update channel, credits.
 *
 * Sliders commit on release (`useDraft`), and validated text fields on blur or Enter
 * (`CommitField`): every `settings_set` is a disk write, and for globals a push into a
 * running game, so neither happens once per drag step or keystroke.
 *
 * The credits section carries the Ultralight notice §13 requires, read from the SDK's
 * own `license/NOTICES.md` under `mod/native/sdk/` and reproduced verbatim below. It is
 * a licence obligation, not a nicety: the free tier requires a credit line from
 * Ultralight's NOTICES to appear in an About/credits screen.
 */

import { useEffect, useState } from 'react';

import {
  Button,
  IconButton,
  KeybindChip,
  PositionChips,
  SettingsGroup,
  SettingsRow,
  Slider,
  StatusDot,
  Toggle,
} from '@void/ui';

import { openPath } from '../features/LogDrawer';
import { useUpdate } from '../features/UpdatePrompt';
import { captureKey, prettyKey } from '../local/keys';
import type { Settings, UpdateInfo } from '../local/protocol';
import { errorText, invoke } from '../local/tauri';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useSession } from '../stores/session';
import { useUi } from '../stores/ui';

/**
 * A slider's value while it is being dragged. The slider steps through every value on
 * the way, and each `settings_set` is a disk write (and, for globals, a push to a running
 * game), so the row shows the draft and only the release commits it.
 */
function useDraft<T>(saved: T): [T, (next: T) => void, () => void] {
  const [draft, setDraft] = useState<T | null>(null);
  return [draft ?? saved, setDraft, () => setDraft(null)];
}

/**
 * A text field that commits on blur or Enter rather than on every keystroke — for
 * values Rust validates (a half-typed `-Xm` is not an error worth a banner) or that touch
 * the disk (a game directory is created when chosen).
 */
function CommitField({
  value,
  onCommit,
  placeholder,
  label,
  className = 'inline-form__field inline-form__field--wide',
  inputMode,
}: {
  value: string;
  onCommit: (next: string) => void;
  placeholder?: string;
  label: string;
  className?: string;
  inputMode?: 'numeric';
}) {
  const [draft, setDraft] = useState<string | null>(null);
  const commit = () => {
    if (draft !== null && draft !== value) onCommit(draft);
    setDraft(null);
  };
  return (
    <input
      className={className}
      value={draft ?? value}
      placeholder={placeholder}
      aria-label={label}
      inputMode={inputMode}
      onChange={(e) => setDraft(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
        if (e.key === 'Escape') setDraft(null);
      }}
    />
  );
}

/** `""` → `null`, anything else → a positive integer or `null`. */
function parseSize(text: string): number | null {
  const n = Number.parseInt(text.trim(), 10);
  return Number.isFinite(n) && n > 0 ? n : null;
}

function updateSub(update: UpdateInfo | null, channel: string): string {
  if (update?.error) return update.error;
  if (update?.available) return `${update.version} is available`;
  if (update) return 'You are up to date';
  return `Checks the ${channel} channel on GitHub Releases at every start`;
}

export function SettingsPanel() {
  const open = useUi((s) => s.settingsOpen);
  const close = useUi((s) => s.closeSettings);

  const {
    account,
    accounts,
    system,
    java,
    deviceCode,
    authStatus,
    error,
    loginMicrosoft,
    loginOffline,
    switchAccount,
    removeAccount,
    refreshJava,
    dismissError,
  } = useSession();
  const { settings, saveSettings, error: settingsError } = useLoadouts();
  const phase = useLaunch((s) => s.phase);
  const update = useUpdate((s) => s.info);
  const foundUpdate = useUpdate((s) => s.found);
  const installUpdate = useUpdate((s) => s.install);

  const [offlineName, setOfflineName] = useState('');
  const [dataDir, setDataDir] = useState('');
  const [logStatus, setLogStatus] = useState('');
  const [checking, setChecking] = useState(false);
  const [ram, setRam, settleRam] = useDraft(settings?.ram_mb ?? 2048);
  const [grid, setGrid, settleGrid] = useDraft(settings?.hud_editor_grid ?? 4);
  const [scale, setScale, settleScale] = useDraft(settings?.ui_scale ?? 1);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, close]);

  if (!open || !settings) return null;

  const ramMax = system ? Math.min(32768, Math.max(4096, Math.floor(system.ram_total_mb * 0.75))) : 16384;
  const save = (patch: Parameters<typeof saveSettings>[0]) => void saveSettings(patch);
  const commitSlider = <K extends keyof Settings>(key: K, settle: () => void) => (next: number) => {
    void saveSettings({ [key]: next } as Parameters<typeof saveSettings>[0]).finally(settle);
  };
  const shownError = error ?? settingsError;

  return (
    <div className="settings-scrim" onMouseDown={close} role="presentation">
      <section
        className="settings"
        role="dialog"
        aria-modal="true"
        aria-label="Settings"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <header className="settings__head">
          <h1 className="settings__title">Settings</h1>
          <span className="settings__spacer" />
          <IconButton icon="close" size="close" label="Close settings" onClick={close} />
        </header>

        <div className="settings__body">
          {shownError ? (
            <div className="banner banner--error">
              <span className="banner__text">{shownError}</span>
              <IconButton
                icon="close"
                size="close"
                label="Dismiss"
                onClick={() => {
                  dismissError();
                  useLoadouts.setState({ error: null });
                }}
              />
            </div>
          ) : null}

          {/* -------------------------------------------------------- account */}
          <SettingsGroup caption="ACCOUNTS">
            {accounts.map((a) => {
              const active = a.uuid === account?.uuid;
              return (
                <SettingsRow
                  key={a.uuid}
                  title={a.name}
                  sub={`${a.kind === 'offline' ? 'Offline account' : 'Microsoft account'}${active ? ' · active' : ''} · ${a.uuid}`}
                >
                  <div className="inline-form">
                    {active ? (
                      <StatusDot tone="ok" size={8} />
                    ) : (
                      <Button variant="raised" disabled={phase !== 'idle'} onClick={() => void switchAccount(a.uuid)}>
                        Use
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      disabled={active && phase !== 'idle'}
                      onClick={() => void removeAccount(a.uuid)}
                    >
                      Sign out
                    </Button>
                  </div>
                </SettingsRow>
              );
            })}
            <SettingsRow
              title={accounts.length > 0 ? 'Add a Microsoft account' : 'Microsoft account'}
              sub={
                deviceCode
                  ? `Enter ${deviceCode.user_code} at ${deviceCode.verification_uri}`
                  : authStatus && 'message' in authStatus
                    ? authStatus.message
                    : accounts.length > 0
                      ? 'Keep several accounts and switch between them from the profile menu'
                      : 'Sign in to launch'
              }
            >
              <Button variant="accent" onClick={() => void loginMicrosoft()}>
                {deviceCode ? 'Waiting…' : 'Sign in'}
              </Button>
            </SettingsRow>
            <SettingsRow title="Play offline" sub="A local account. Works on offline-mode servers only.">
              <div className="inline-form">
                <input
                  className="inline-form__field"
                  value={offlineName}
                  onChange={(e) => setOfflineName(e.target.value)}
                  placeholder="Name"
                  aria-label="Offline account name"
                  maxLength={16}
                />
                <Button
                  variant="raised"
                  disabled={!offlineName.trim()}
                  onClick={() => {
                    void loginOffline(offlineName);
                    setOfflineName('');
                  }}
                >
                  Use
                </Button>
              </div>
            </SettingsRow>
          </SettingsGroup>

          {/* ----------------------------------------------------------- java */}
          <SettingsGroup caption="JAVA & MEMORY">
            <SettingsRow
              title="Java runtime"
              sub={
                java?.found
                  ? `Java ${java.version} · ${java.source} · ${java.path}`
                  : java?.version
                    ? `Found Java ${java.version}, but 1.8.9 needs Java 8 — Temurin 8 will be fetched on first launch`
                    : 'Not found — Temurin 8 will be fetched from Adoptium on first launch'
              }
            >
              <div className="inline-form">
                <StatusDot tone={java?.found ? 'ok' : 'warn'} size={8} />
                <Button variant="ghost" onClick={() => void refreshJava()}>
                  Re-detect
                </Button>
              </div>
            </SettingsRow>
            <SettingsRow title="Find Java automatically" sub="Off lets you point at a specific JVM.">
              <Toggle
                size="l"
                checked={settings.java_auto}
                onChange={(next) => save({ java_auto: next })}
                label="Find Java automatically"
              />
            </SettingsRow>
            {!settings.java_auto ? (
              <SettingsRow title="Java path" sub="Path to a Java 8 `java` executable.">
                <input
                  className="inline-form__field inline-form__field--wide"
                  value={settings.java_path ?? ''}
                  placeholder="/usr/lib/jvm/java-8/bin/java"
                  aria-label="Java path"
                  onChange={(e) => save({ java_path: e.target.value || null })}
                />
              </SettingsRow>
            ) : null}
            <SettingsRow
              title="Memory"
              value={`${(ram / 1024).toFixed(1)} GB`}
              sub={
                system
                  ? `${system.ram_total_mb.toLocaleString()} MB installed · ${system.recommended_ram_mb} MB recommended`
                  : 'Allocated to the JVM'
              }
            >
              <Slider
                variant="wide"
                hideLabels
                ariaLabel="RAM"
                value={ram}
                min={1024}
                max={ramMax}
                step={512}
                readout={`${(ram / 1024).toFixed(1)} GB`}
                onChange={setRam}
                onCommit={commitSlider('ram_mb', settleRam)}
              />
            </SettingsRow>
            <SettingsRow
              title="JVM arguments"
              sub="Added after VOID's own. Memory is the slider above; quotes keep a value with spaces together."
            >
              <CommitField
                label="JVM arguments"
                value={settings.jvm_args}
                placeholder="-XX:+UseG1GC"
                onCommit={(next) => save({ jvm_args: next })}
              />
            </SettingsRow>
          </SettingsGroup>

          {/* ----------------------------------------------------------- game */}
          <SettingsGroup caption="GAME WINDOW & FOLDER">
            <SettingsRow
              title="Window size"
              sub="The size Minecraft opens at. Leave empty for the game's default."
            >
              <div className="inline-form">
                <CommitField
                  label="Window width"
                  className="inline-form__field inline-form__field--num"
                  inputMode="numeric"
                  value={settings.game_width?.toString() ?? ''}
                  placeholder="854"
                  onCommit={(next) => save({ game_width: parseSize(next) })}
                />
                <span className="mono-value">×</span>
                <CommitField
                  label="Window height"
                  className="inline-form__field inline-form__field--num"
                  inputMode="numeric"
                  value={settings.game_height?.toString() ?? ''}
                  placeholder="480"
                  onCommit={(next) => save({ game_height: parseSize(next) })}
                />
              </div>
            </SettingsRow>
            <SettingsRow title="Start fullscreen" sub="F11 still toggles it in game.">
              <Toggle
                size="l"
                checked={settings.fullscreen}
                onChange={(next) => save({ fullscreen: next })}
                label="Start fullscreen"
              />
            </SettingsRow>
            <SettingsRow
              title="Game directory"
              sub="Saves, options, resource packs, screenshots and logs. Downloads stay in the data folder."
            >
              <div className="inline-form">
                <CommitField
                  label="Game directory"
                  value={settings.game_dir ?? ''}
                  placeholder={settings.game_dir_effective}
                  onCommit={(next) => save({ game_dir: next.trim() || null })}
                />
                {settings.game_dir ? (
                  <Button variant="ghost" disabled={phase !== 'idle'} onClick={() => save({ game_dir: null })}>
                    Default
                  </Button>
                ) : null}
                <Button variant="raised" onClick={() => void openPath('game_dir').catch(() => undefined)}>
                  Open
                </Button>
              </div>
            </SettingsRow>
          </SettingsGroup>

          {/* -------------------------------------------------------- hotkeys */}
          <SettingsGroup caption="IN-GAME HOTKEYS">
            <SettingsRow title="Menu key" sub="Opens and closes the VOID panel in game.">
              <KeybindChip
                aria-label="Menu key"
                value={prettyKey(settings.menu_key)}
                onCapture={captureKey}
                onChange={(next) => save({ menu_key: next })}
              />
            </SettingsRow>
            <SettingsRow title="Cycle loadout" sub="Steps to the next loadout in the library.">
              <KeybindChip
                aria-label="Cycle loadout key"
                value={prettyKey(settings.cycle_loadout_key)}
                onCapture={captureKey}
                onChange={(next) => save({ cycle_loadout_key: next })}
              />
            </SettingsRow>
            <SettingsRow
              title="HUD editor grid"
              sub="Snap size in GUI pixels. 0 disables snapping."
              value={grid === 0 ? 'off' : `${grid} px`}
            >
              <Slider
                variant="wide"
                hideLabels
                ariaLabel="HUD editor grid"
                value={grid}
                min={0}
                max={32}
                step={1}
                readout={grid === 0 ? 'off' : `${grid} px`}
                onChange={setGrid}
                onCommit={commitSlider('hud_editor_grid', settleGrid)}
              />
            </SettingsRow>
          </SettingsGroup>

          {/* --------------------------------------------------- appearance */}
          <SettingsGroup caption="APPEARANCE & WINDOW">
            <SettingsRow title="Theme" sub="Shared by the launcher and the in-game UI.">
              <PositionChips
                aria-label="Theme"
                value={settings.theme}
                options={[{ id: 'void-dark', label: 'void-dark' }]}
                onChange={(next) => save({ theme: next })}
              />
            </SettingsRow>
            <SettingsRow
              title="In-game UI scale"
              sub="On top of the game's GUI scale."
              value={`${scale.toFixed(1)}×`}
            >
              <Slider
                variant="wide"
                hideLabels
                ariaLabel="In-game UI scale"
                value={scale}
                min={0.5}
                max={3}
                step={0.1}
                readout={`${scale.toFixed(1)}×`}
                onChange={setScale}
                onCommit={commitSlider('ui_scale', settleScale)}
              />
            </SettingsRow>
            <SettingsRow title="Hide to tray on launch" sub="The window returns when the game closes.">
              <Toggle
                size="l"
                checked={settings.hide_to_tray_on_launch}
                onChange={(next) => save({ hide_to_tray_on_launch: next })}
                label="Hide to tray on launch"
              />
            </SettingsRow>
            <SettingsRow title="Discord status" sub="Shows the server you are on and your loadout in Discord.">
              <Toggle
                size="l"
                checked={settings.discord_rpc}
                onChange={(next) => save({ discord_rpc: next })}
                label="Discord status"
              />
            </SettingsRow>
          </SettingsGroup>

          {/* --------------------------------------------------------- system */}
          <SettingsGroup caption="DATA & UPDATES">
            {settings.mod_jar ? (
              // Only a developer build sets this ($VOID_MOD_JAR or `mod_jar` in config.json):
              // say so plainly, because that client is not the signed release one.
              <SettingsRow title="Developer client JAR" sub={`${settings.mod_jar} — installed instead of the release client`}>
                <StatusDot tone="warn" size={8} />
              </SettingsRow>
            ) : null}
            <SettingsRow
              title="Data folder"
              sub={
                dataDir ||
                (system
                  ? `${system.data_dir} — loadouts, settings, game files and the Java runtime`
                  : 'Loadouts, settings, game files and the Java runtime')
              }
            >
              <Button
                variant="raised"
                onClick={() => {
                  void invoke('open_data_dir')
                    .then(setDataDir)
                    .catch((e) => setDataDir(errorText(e)));
                }}
              >
                Open
              </Button>
            </SettingsRow>
            <SettingsRow
              title="Launcher log"
              sub={logStatus || 'What the launcher did, one file a day, the last week kept — attach it to a bug report'}
            >
              <Button
                variant="raised"
                onClick={() => {
                  void openPath('launcher_log')
                    .then(setLogStatus)
                    .catch((e) => setLogStatus(errorText(e)));
                }}
              >
                Open
              </Button>
            </SettingsRow>
            <SettingsRow
              title="Version"
              sub={
                system
                  ? `${system.app_version} · ${system.os} ${system.os_version} · ${system.arch}`
                  : 'unknown'
              }
            >
              <span className="mono-value">{system?.app_version ?? '—'}</span>
            </SettingsRow>
            <SettingsRow title="Update channel" sub="Beta gets new launcher and client builds first.">
              <PositionChips
                aria-label="Update channel"
                value={settings.update_channel}
                options={[
                  { id: 'stable', label: 'stable' },
                  { id: 'beta', label: 'beta' },
                ]}
                onChange={(next) => save({ update_channel: next })}
              />
            </SettingsRow>
            <SettingsRow title="Updates" sub={updateSub(update, settings.update_channel)}>
              <div className="inline-form">
                {update?.available ? (
                  <Button variant="accent" disabled={phase !== 'idle'} onClick={() => void installUpdate()}>
                    Restart to update
                  </Button>
                ) : null}
                <Button
                  variant="raised"
                  disabled={checking}
                  onClick={() => {
                    setChecking(true);
                    void invoke('updater_check')
                      .then(foundUpdate)
                      .catch((e) =>
                        foundUpdate({
                          available: false,
                          current_version: system?.app_version ?? '0.0.0',
                          version: null,
                          notes: null,
                          date: null,
                          error: errorText(e),
                        }),
                      )
                      .finally(() => setChecking(false));
                  }}
                >
                  {checking ? 'Checking…' : 'Check now'}
                </Button>
              </div>
            </SettingsRow>
          </SettingsGroup>

          {/* -------------------------------------------------------- credits */}
          <SettingsGroup caption="CREDITS">
            <div className="credits">
              <p className="credits__line">
                <strong>Ultralight</strong> — the in-game UI renderer.
              </p>
              <p className="credits__notice">
                Ultralight © 2023 Ultralight, Inc. All rights reserved. Ultralight is a trademark of
                Ultralight, Inc. This software includes portions of WebKit; all WebKit modifications
                are published under LGPL 2.1 at{' '}
                <span className="credits__url">https://github.com/ultralight-ux/WebCore</span>.
                Ultralight also includes brotli, cURL, FreeType, Harfbuzz, mimalloc, ICU,
                libjpeg-turbo, libpng, libressl, libxml2, libxslt, nghttp2, skia, SQLite and zlib
                under their respective licences.
              </p>
              <p className="credits__line">
                <strong>Legacy Fabric</strong> — mod loader for Minecraft 1.8.9. ·{' '}
                <strong>Mixin</strong> (SpongePowered). · <strong>Tauri</strong>, <strong>React</strong>,{' '}
                <strong>Zustand</strong>, <strong>Vite</strong>.
              </p>
              <p className="credits__line credits__line--muted">
                Not affiliated with Mojang, Microsoft or Hypixel. Minecraft is a trademark of Mojang
                Synergies AB.
              </p>
            </div>
          </SettingsGroup>
        </div>
      </section>
    </div>
  );
}
