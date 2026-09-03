//! `tauri-plugin-updater`, wired but pointing at a placeholder endpoint.
//!
//! The endpoint in `tauri.conf.json` is `https://updates.void.invalid/...` on purpose:
//! `.invalid` is reserved by RFC 2606 and can never resolve, so a development build
//! cannot be talked into installing anything. Point it at the real release channel
//! (and paste the matching public key) when signing is set up — §16.5.
//!
//! Checking is deliberately non-fatal: a launcher that refuses to start because an
//! update server is down is a launcher that cannot play Minecraft.

use serde::Serialize;
use tauri::{AppHandle, Emitter as _, Runtime};
use tauri_plugin_updater::UpdaterExt;

pub const UPDATE_AVAILABLE: &str = "update:available";

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

/// Check once at startup, in the background. Never blocks the window appearing.
pub fn spawn_check<R: Runtime>(app: AppHandle<R>) {
    tauri::async_runtime::spawn(async move {
        match check(&app).await {
            Ok(info) if info.available => {
                tracing::info!(version = ?info.version, "an update is available");
                let _ = app.emit(UPDATE_AVAILABLE, &info);
            }
            Ok(_) => tracing::debug!("no update available"),
            Err(e) => tracing::debug!(%e, "update check failed (expected while the endpoint is a placeholder)"),
        }
    });
}

async fn check<R: Runtime>(app: &AppHandle<R>) -> Result<UpdateInfo, String> {
    let current_version = app.package_info().version.to_string();
    let updater = app.updater().map_err(|e| e.to_string())?;
    match updater.check().await {
        Ok(Some(update)) => Ok(UpdateInfo {
            available: true,
            current_version,
            version: Some(update.version.clone()),
            notes: update.body.clone(),
            date: update.date.map(|d| d.to_string()),
            error: None,
        }),
        Ok(None) => Ok(UpdateInfo {
            available: false,
            current_version,
            version: None,
            notes: None,
            date: None,
            error: None,
        }),
        Err(e) => Err(e.to_string()),
    }
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
