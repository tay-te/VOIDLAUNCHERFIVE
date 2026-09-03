//! Window behaviour that more than one caller needs.
//!
//! §5: "Single frameless window. After a successful spawn it hides to the tray; on
//! `game-closed` it returns with session stats."

use tauri::{AppHandle, Manager, Runtime};

pub const MAIN: &str = "main";

pub fn hide_to_tray<R: Runtime>(app: &AppHandle<R>) {
    if let Some(w) = app.get_webview_window(MAIN) {
        if let Err(e) = w.hide() {
            tracing::warn!(%e, "could not hide the window to the tray");
        }
    }
}

/// Bring the window back and focus it. Called from the tray, from a second instance,
/// and from `game:closed`.
pub fn show<R: Runtime>(app: &AppHandle<R>) {
    if let Some(w) = app.get_webview_window(MAIN) {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
}
