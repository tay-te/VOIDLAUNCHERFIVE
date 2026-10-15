//! The launcher's own updates, through `tauri-plugin-updater` and GitHub Releases.
//!
//! Each release workflow run (`.github/workflows/pvp-release.yml`) publishes a signed
//! `latest.json` on the fixed channel releases, so the URL never moves:
//!
//! ```text
//! https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download/pvp-stable/latest.json
//! https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download/pvp-beta/latest.json
//! ```
//!
//! `tauri.conf.json` carries the stable URL and the updater public key; the beta URL is
//! swapped in at run time from the `update_channel` setting. The installers' signatures
//! are checked against the public key by the plugin before anything is run — the private
//! half is the release workflow's `TAURI_SIGNING_PRIVATE_KEY` secret.
//!
//! Flow: a background check at startup emits `update:available`; the window shows a
//! small prompt; *Restart to update* calls [`updater_install`], which downloads (emitting
//! `update:progress`), installs and relaunches. On Windows the NSIS installer runs in
//! passive mode and the app exits for it.
//!
//! Checking is deliberately non-fatal: a launcher that refuses to start because GitHub is
//! down is a launcher that cannot play Minecraft.

use std::sync::Mutex;

use serde::Serialize;
use tauri::{AppHandle, Emitter as _, Manager, Runtime};
use tauri_plugin_updater::{Update, UpdaterExt};

use crate::state::AppState;

pub const UPDATE_AVAILABLE: &str = "update:available";
pub const UPDATE_PROGRESS: &str = "update:progress";

/// Where the channel releases live.
const RELEASES_BASE: &str = "https://github.com/tay-te/VOIDLAUNCHERFIVE/releases/download";

/// The update found by the last check, so *Restart to update* installs exactly what the
/// prompt described rather than whatever a second check finds.
#[derive(Default)]
pub struct PendingUpdate(pub Mutex<Option<Update>>);

#[derive(Debug, Clone, Serialize)]
pub struct UpdateInfo {
    pub available: bool,
    pub current_version: String,
    pub version: Option<String>,
    pub notes: Option<String>,
    pub date: Option<String>,
    /// Present when the check itself failed — the Settings screen shows it as
    /// "could not check for updates", not as "you are up to date".
    pub error: Option<String>,
}

/// Payload of `update:progress`.
#[derive(Debug, Clone, Serialize)]
pub struct UpdateProgress {
    pub downloaded: u64,
    pub total: Option<u64>,
    /// `downloading` | `installing`
    pub phase: &'static str,
}

/// The `latest.json` URL for a channel.
pub fn endpoint(channel: &str) -> String {
    let tag = if channel == "beta" { "pvp-beta" } else { "pvp-stable" };
    format!("{RELEASES_BASE}/{tag}/latest.json")
}

/// Check once at startup, in the background. Never blocks the window appearing.
pub fn spawn_check<R: Runtime>(app: AppHandle<R>) {
    tauri::async_runtime::spawn(async move {
        match check(&app).await {
            Ok(info) if info.available => {
                tracing::info!(version = ?info.version, "an update is available");
                let _ = app.emit(UPDATE_AVAILABLE, &info);
            }
            Ok(_) => tracing::debug!("no update available"),
            Err(e) => tracing::info!(%e, "update check failed"),
        }
    });
}

fn channel<R: Runtime>(app: &AppHandle<R>) -> String {
    app.try_state::<AppState>()
        .map(|s| crate::commands::settings::update_channel(&s))
        .unwrap_or_else(|| "stable".into())
}

async fn check<R: Runtime>(app: &AppHandle<R>) -> Result<UpdateInfo, String> {
    let current_version = app.package_info().version.to_string();
    let url = tauri::Url::parse(&endpoint(&channel(app))).map_err(|e| e.to_string())?;
    let updater = app
        .updater_builder()
        .endpoints(vec![url])
        .map_err(|e| e.to_string())?
        .build()
        .map_err(|e| e.to_string())?;
    let found = updater.check().await.map_err(|e| e.to_string())?;
    let info = match &found {
        Some(update) => UpdateInfo {
            available: true,
            current_version,
            version: Some(update.version.clone()),
            notes: update.body.clone(),
            date: update.date.map(|d| d.to_string()),
            error: None,
        },
        None => UpdateInfo {
            available: false,
            current_version,
            version: None,
            notes: None,
            date: None,
            error: None,
        },
    };
    if let Some(pending) = app.try_state::<PendingUpdate>() {
        *pending.0.lock().unwrap() = found;
    }
    Ok(info)
}

/// The Settings screen's "Check for updates" button.
#[tauri::command]
pub async fn updater_check<R: Runtime>(app: AppHandle<R>) -> Result<UpdateInfo, String> {
    let current_version = app.package_info().version.to_string();
    Ok(check(&app).await.unwrap_or_else(|e| UpdateInfo {
        available: false,
        current_version,
        version: None,
        notes: None,
        date: None,
        error: Some(e),
    }))
}

/// *Restart to update*: download, verify, install, relaunch.
///
/// Refused while Minecraft is running — the installer replaces the launcher, and the
/// launcher is what holds the game's bridge.
#[tauri::command]
pub async fn updater_install<R: Runtime>(app: AppHandle<R>) -> Result<(), String> {
    if let Some(state) = app.try_state::<AppState>() {
        if state.game.lock().unwrap().is_running() {
            return Err("Close Minecraft first: updating restarts the launcher.".into());
        }
    }
    let pending = app.try_state::<PendingUpdate>().and_then(|p| p.0.lock().unwrap().clone());
    let update = match pending {
        Some(update) => update,
        None => {
            check(&app).await?;
            app.try_state::<PendingUpdate>()
                .and_then(|p| p.0.lock().unwrap().clone())
                .ok_or("You are already on the latest version.")?
        }
    };

    tracing::info!(version = %update.version, "installing update");
    let mut downloaded: u64 = 0;
    let progress_app = app.clone();
    let finished_app = app.clone();
    update
        .download_and_install(
            move |chunk, total| {
                downloaded += chunk as u64;
                let _ = progress_app.emit(
                    UPDATE_PROGRESS,
                    UpdateProgress { downloaded, total, phase: "downloading" },
                );
            },
            move || {
                let _ = finished_app.emit(
                    UPDATE_PROGRESS,
                    UpdateProgress { downloaded: 0, total: None, phase: "installing" },
                );
            },
        )
        .await
        .map_err(|e| {
            tracing::warn!(%e, "update failed");
            format!("The update could not be installed: {e}")
        })?;

    // On Windows the installer has taken over and the process is exiting; elsewhere the
    // new bundle is in place and a restart picks it up.
    app.restart();
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn each_channel_has_a_fixed_latest_json() {
        assert!(endpoint("stable").ends_with("/pvp-stable/latest.json"));
        assert!(endpoint("beta").ends_with("/pvp-beta/latest.json"));
        assert!(endpoint("anything-else").ends_with("/pvp-stable/latest.json"));
    }
}
