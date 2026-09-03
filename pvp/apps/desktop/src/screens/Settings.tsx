/**
 * Settings — the gear, over whichever screen is up.
 *
 * §8.3 draws the line this screen respects: **if it changes how the game plays it is
 * in the loadout, not here.** So there are no mod toggles on this screen. What is here
 * is account, Java, RAM, hotkeys, theme, hide-to-tray, data folder, updates, credits.
 *
 * The credits section carries the Ultralight notice §13 requires, read from the SDK's
 * own `license/NOTICES.md` under `mod/native/sdk/` and reproduced verbatim below. It is
 * a licence obligation, not a nicety: the free tier requires a credit line from
 * Ultralight's NOTICES to appear in an About/credits screen.
 */

import { useEffect, useState } from 'react';

import { Button, Group, IconButton, KeybindChip, Row, Segmented, Slider, StatusDot, Switch } from '../components';
import { XIcon } from '../components/icons';
import type { UpdateInfo } from '../local/protocol';
import { errorText, invoke } from '../local/tauri';
import { useLaunch } from '../stores/launch';
import { useLoadouts } from '../stores/loadouts';
import { useSession } from '../stores/session';
import { useUi } from '../stores/ui';

export function SettingsPanel() {
  const open = useUi((s) => s.settingsOpen);
  const close = useUi((s) => s.closeSettings);

  const { account, system, java, deviceCode, authStatus, error, loginMicrosoft, loginOffline, logout, refreshJava, dismissError } =
    useSession();
  const { settings, saveSettings } = useLoadouts();
  const phase = useLaunch((s) => s.phase);

  const [offlineName, setOfflineName] = useState('');
  const [dataDir, setDataDir] = useState('');
  const [update, setUpdate] = useState<UpdateInfo | null>(null);

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
          <IconButton icon={XIcon} size={32} glyph={14} label="Close settings" onClick={close} />
        </header>

        <div className="settings__body">
          {error ? (
            <div className="banner banner--error">
              <span className="banner__text">{error}</span>
              <IconButton icon={XIcon} size={26} glyph={12} label="Dismiss" onClick={dismissError} />
            </div>
          ) : null}

          {/* -------------------------------------------------------- account */}
          <Group caption="ACCOUNT">
            {account ? (
              <>
                <Row title={account.name} sub={`${account.kind === 'offline' ? 'Offline account' : 'Microsoft account'} · ${account.uuid}`}>
                  <Button variant="ghost" onClick={() => void logout()} disabled={phase !== 'idle'}>
                    Sign out
                  </Button>
                </Row>
              </>
            ) : (
              <>
                <Row
                  title="Microsoft account"
                  sub={
                    deviceCode
                      ? `Enter ${deviceCode.user_code} at ${deviceCode.verification_uri}`
                      : (authStatus && 'message' in authStatus ? authStatus.message : 'Sign in to launch')
                  }
                >
                  <Button variant="accent" onClick={() => void loginMicrosoft()}>
                    {deviceCode ? 'Waiting…' : 'Sign in'}
                  </Button>
                </Row>
                <Row title="Play offline" sub="A local account. Works on offline-mode servers only.">
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
                      onClick={() => void loginOffline(offlineName)}
                    >
                      Use
                    </Button>
                  </div>
                </Row>
              </>
            )}
          </Group>

          {/* ----------------------------------------------------------- java */}
          <Group caption="JAVA & MEMORY">
            <Row
              title="Java runtime"
              sub={
                java?.found
                  ? `${java.version} · ${java.source} · ${java.path}`
                  : java?.version
                    ? `Found Java ${java.version}, but 1.8.9 needs Java 8`
                    : 'Not found — the launcher will fetch Adoptium 8 on first launch'
              }
            >
              <div className="inline-form">
                <StatusDot tone={java?.found ? 'ok' : 'warn'} size={8} />
                <Button variant="ghost" onClick={() => void refreshJava()}>
                  Re-detect
                </Button>
              </div>
            </Row>
            <Row title="Find Java automatically" sub="Off lets you point at a specific JVM.">
              <Switch
                size="l"
                checked={settings.java_auto}
                onChange={(next) => void saveSettings({ java_auto: next })}
                label="Find Java automatically"
              />
            </Row>
            {!settings.java_auto ? (
              <Row title="Java path" sub="Path to a Java 8 `java` executable.">
                <input
                  className="inline-form__field inline-form__field--wide"
                  value={settings.java_path ?? ''}
                  placeholder="/usr/lib/jvm/java-8/bin/java"
                  aria-label="Java path"
                  onChange={(e) => void saveSettings({ java_path: e.target.value || null })}
                />
              </Row>
            ) : null}
            <Row
              title="Memory"
              sub={
                system
                  ? `${system.ram_total_mb.toLocaleString()} MB installed · ${system.recommended_ram_mb} MB recommended`
                  : 'Allocated to the JVM'
              }
            >
              <Slider
                wide
                label="RAM"
                value={settings.ram_mb}
                min={1024}
                max={ramMax}
                step={512}
                display={`${(settings.ram_mb / 1024).toFixed(1)} GB`}
                onChange={(next) => void saveSettings({ ram_mb: next })}
              />
            </Row>
          </Group>

          {/* -------------------------------------------------------- hotkeys */}
          <Group caption="IN-GAME HOTKEYS">
            <Row title="Menu key" sub="Opens and closes the VOID panel in game.">
              <KeybindChip
                label="Menu key"
                value={settings.menu_key}
                onChange={(next) => void saveSettings({ menu_key: next })}
              />
            </Row>
            <Row title="Cycle loadout" sub="Steps to the next loadout in the library.">
              <KeybindChip
                label="Cycle loadout key"
                value={settings.cycle_loadout_key}
                onChange={(next) => void saveSettings({ cycle_loadout_key: next })}
              />
            </Row>
            <Row title="HUD editor grid" sub="Snap size in GUI pixels. 0 disables snapping.">
              <Slider
                wide
                label="Grid"
                value={settings.hud_editor_grid}
                min={0}
                max={32}
                step={1}
                display={settings.hud_editor_grid === 0 ? 'off' : `${settings.hud_editor_grid} px`}
                onChange={(next) => void saveSettings({ hud_editor_grid: next })}
              />
            </Row>
          </Group>

          {/* --------------------------------------------------- appearance */}
          <Group caption="APPEARANCE & WINDOW">
            <Row title="Theme" sub="Shared by the launcher and the in-game UI.">
              <Segmented
                label="Theme"
                value={settings.theme}
                options={['void-dark']}
                onChange={(next) => void saveSettings({ theme: next })}
              />
            </Row>
            <Row title="In-game UI scale" sub="On top of the game's GUI scale.">
              <Slider
                wide
                label="UI scale"
                value={settings.ui_scale}
                min={0.5}
                max={3}
                step={0.1}
                display={`${settings.ui_scale.toFixed(1)}×`}
                onChange={(next) => void saveSettings({ ui_scale: next })}
              />
            </Row>
            <Row title="Hide to tray on launch" sub="The window returns when the game closes.">
              <Switch
                size="l"
                checked={settings.hide_to_tray_on_launch}
                onChange={(next) => void saveSettings({ hide_to_tray_on_launch: next })}
                label="Hide to tray on launch"
              />
            </Row>
          </Group>

          {/* --------------------------------------------------------- system */}
          <Group caption="DATA & UPDATES">
            <Row title="Data folder" sub={dataDir || 'Loadouts, settings, game files and the Java runtime'}>
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
            </Row>
            <Row
              title="Version"
              sub={
                system
                  ? `${system.app_version} · ${system.os} ${system.os_version} · ${system.arch}`
                  : 'unknown'
              }
            >
              <span className="mono-value">{system?.app_version ?? '—'}</span>
            </Row>
            <Row
              title="Updates"
              sub={
                update?.error
                  ? update.error
                  : update?.available
                    ? `${update.version} is available`
                    : update
                      ? 'You are up to date'
                      : 'The update endpoint is a placeholder until release signing is set up (§16.5)'
              }
            >
              <Button
                variant="raised"
                onClick={() => {
                  void invoke('updater_check')
                    .then(setUpdate)
                    .catch((e) =>
                      setUpdate({
                        available: false,
                        current_version: system?.app_version ?? '0.0.0',
                        version: null,
                        notes: null,
                        date: null,
                        error: errorText(e),
                      }),
                    );
                }}
              >
                Check now
              </Button>
            </Row>
          </Group>

          {/* -------------------------------------------------------- credits */}
          <Group caption="CREDITS">
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
          </Group>
        </div>
      </section>
    </div>
  );
}
