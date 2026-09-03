//! The `#[tauri::command]` surface.
//!
//! Thin on purpose: every wrapper does three things and no more — adapt
//! `State<'_, AppState>` to `&AppState`, hand the adapters an `Emitter`, and flatten
//! `Error` into the `String` the TypeScript side receives. The logic lives in
//! `crate::commands`, which compiles without Tauri.
//!
//! Command names are the contract with `src/local/tauri.ts` and `src/mocks/tauri.ts`;
//! the mock implements exactly this list.

use std::sync::Arc;

use tauri::{AppHandle, Emitter as _, Manager, Runtime, State};

use crate::commands;
use crate::error::{map_err, CmdResult};
use crate::events::Emitter;
use crate::models::*;
use crate::state::AppState;

/// Adapts a Tauri app handle to the tiny `Emitter` trait the adapters take.
pub struct AppEmitter<R: Runtime>(pub AppHandle<R>);

impl<R: Runtime> Emitter for AppEmitter<R> {
    fn emit_json(&self, event: &str, payload: serde_json::Value) {
        if let Err(e) = self.0.emit(event, payload) {
            tracing::warn!(%event, %e, "could not emit event");
        }
    }
}

// ------------------------------------------------------------------------- auth

#[tauri::command]
pub async fn auth_login<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
) -> CmdResult<DeviceCode> {
    let code = map_err(commands::auth::login(&state))?;
    // The device flow continues in the background; the player already has the code.
    let emitter = AppEmitter(app);
    tauri::async_runtime::spawn(async move {
        commands::auth::login_poll(&emitter).await;
    });
    Ok(code)
}

#[tauri::command]
pub fn auth_logout(state: State<'_, AppState>) -> CmdResult<()> {
    map_err(commands::auth::logout(&state))
}

#[tauri::command]
pub fn auth_current(state: State<'_, AppState>) -> CmdResult<Option<Account>> {
    map_err(commands::auth::current(&state))
}

#[tauri::command]
pub fn auth_offline(state: State<'_, AppState>, name: String) -> CmdResult<Account> {
    map_err(commands::auth::offline(&state, &name))
}

// ------------------------------------------------------------- prepare / launch

#[tauri::command]
pub async fn prepare<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    loadout_id: String,
) -> CmdResult<PrepareReport> {
    let emitter = AppEmitter(app);
    map_err(commands::launch::prepare(&state, &emitter, &loadout_id).await)
}

#[tauri::command]
pub async fn launch<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    loadout_id: String,
) -> CmdResult<LaunchReport> {
    let emitter: Arc<dyn Emitter> = Arc::new(AppEmitter(app.clone()));
    let report = map_err(commands::launch::launch(&state, emitter, &loadout_id).await)?;

    // §5: after a successful spawn the window hides to the tray, and comes back on
    // `game:closed` with the session stats. Configurable, because a second monitor
    // makes it unwanted.
    if state.store.lock().unwrap().settings().hide_to_tray_on_launch {
        crate::window::hide_to_tray(&app);
    }
    Ok(report)
}

#[tauri::command]
pub fn game_kill(state: State<'_, AppState>) -> CmdResult<()> {
    map_err(commands::launch::kill(&state))
}

#[tauri::command]
pub fn game_log_tail(state: State<'_, AppState>, lines: Option<usize>) -> CmdResult<Vec<String>> {
    map_err(commands::launch::log_tail(&state, lines.unwrap_or(500)))
}

// -------------------------------------------------------------------- loadouts

#[tauri::command]
pub fn loadouts_list(state: State<'_, AppState>) -> CmdResult<Vec<LoadoutSummary>> {
    map_err(commands::loadouts::list(&state))
}

#[tauri::command]
pub fn loadouts_get(state: State<'_, AppState>, id: String) -> CmdResult<Loadout> {
    map_err(commands::loadouts::get(&state, &id))
}

#[tauri::command]
pub fn loadouts_active(state: State<'_, AppState>) -> CmdResult<Loadout> {
    map_err(commands::loadouts::active(&state))
}

#[tauri::command]
pub fn loadouts_create(
    state: State<'_, AppState>,
    name: String,
    icon: Option<String>,
) -> CmdResult<Loadout> {
    map_err(commands::loadouts::create(
        &state,
        &name,
        icon.as_deref().unwrap_or("sword"),
    ))
}

#[tauri::command]
pub fn loadouts_update(
    state: State<'_, AppState>,
    id: String,
    patch: LoadoutPatch,
) -> CmdResult<Loadout> {
    map_err(commands::loadouts::update(&state, &id, patch))
}

#[tauri::command]
pub fn loadouts_delete(state: State<'_, AppState>, id: String) -> CmdResult<Vec<LoadoutSummary>> {
    map_err(commands::loadouts::delete(&state, &id))
}

#[tauri::command]
pub fn loadouts_switch<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    id: String,
) -> CmdResult<Loadout> {
    let loadout = map_err(commands::loadouts::switch(&state, &id))?;
    // Tell every window (and, once void-bridge is wired, the running game) that the
    // active loadout moved — the tray can switch it too, so the UI cannot assume it
    // was the one that asked.
    let _ = app.emit(crate::events::LOADOUT_SWITCHED, &loadout);
    crate::tray::refresh(&app, &state);
    Ok(loadout)
}

// -------------------------------------------------------------------- settings

#[tauri::command]
pub fn settings_get(state: State<'_, AppState>) -> CmdResult<Settings> {
    map_err(commands::settings::get(&state))
}

#[tauri::command]
pub fn settings_set(state: State<'_, AppState>, patch: SettingsPatch) -> CmdResult<Settings> {
    map_err(commands::settings::set(&state, patch))
}

// ---------------------------------------------------------------------- system

#[tauri::command]
pub fn system_info(state: State<'_, AppState>) -> CmdResult<SystemInfo> {
    map_err(commands::system::system_info(&state))
}

#[tauri::command]
pub fn java_status(state: State<'_, AppState>) -> CmdResult<JavaStatus> {
    map_err(commands::system::java_status(&state))
}

#[tauri::command]
pub async fn server_ping(host: String) -> CmdResult<PingResult> {
    map_err(commands::servers::ping(&host).await)
}

#[tauri::command]
pub fn open_data_dir<R: Runtime>(app: AppHandle<R>, state: State<'_, AppState>) -> CmdResult<String> {
    let dir = map_err(commands::system::data_dir(&state))?;
    if let Err(e) = tauri_plugin_opener::open_path(&dir, None::<&str>) {
        tracing::warn!(%e, "could not open the data folder");
    }
    let _ = app;
    Ok(dir)
}

// ---------------------------------------------------------------------- window

#[tauri::command]
pub fn window_minimize<R: Runtime>(app: AppHandle<R>) -> CmdResult<()> {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.minimize();
    }
    Ok(())
}

#[tauri::command]
pub fn window_toggle_maximize<R: Runtime>(app: AppHandle<R>) -> CmdResult<()> {
    if let Some(w) = app.get_webview_window("main") {
        match w.is_maximized() {
            Ok(true) => {
                let _ = w.unmaximize();
            }
            _ => {
                let _ = w.maximize();
            }
        }
    }
    Ok(())
}

/// Closing the window hides it to the tray rather than quitting — the tray's Quit item
/// is the only way out, which is what makes "Switch loadout ▸" useful mid-session.
#[tauri::command]
pub fn window_close<R: Runtime>(app: AppHandle<R>) -> CmdResult<()> {
    crate::window::hide_to_tray(&app);
    Ok(())
}
