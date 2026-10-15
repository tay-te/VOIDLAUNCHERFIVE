//! The `#[tauri::command]` surface.
//!
//! Thin on purpose: every wrapper does three things and no more — adapt
//! `State<'_, AppState>` to `&AppState`, hand the layer below an `Emitter`, and flatten
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

/// Adapts a Tauri app handle to the tiny `Emitter` trait the layer below takes.
pub struct AppEmitter<R: Runtime>(pub AppHandle<R>);

impl<R: Runtime> Emitter for AppEmitter<R> {
    fn emit_json(&self, event: &str, payload: serde_json::Value) {
        if let Err(e) = self.0.emit(event, payload) {
            tracing::warn!(%event, %e, "could not emit event");
        }
    }
}

fn emitter<R: Runtime>(app: &AppHandle<R>) -> Arc<dyn Emitter> {
    Arc::new(AppEmitter(app.clone()))
}

// ------------------------------------------------------------------------- auth

/// Start the Microsoft device flow.
///
/// Returns the user code and verification URL **immediately** — that is the whole point
/// of the device flow, and the player has to go and type the code somewhere else while
/// the exchange runs. Completion arrives as `auth:status`.
#[tauri::command]
pub async fn auth_login<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
) -> CmdResult<DeviceCode> {
    let (dto, code) = map_err(commands::auth::login(&state).await)?;

    let app_for_task = app.clone();
    tauri::async_runtime::spawn(async move {
        let Some(state) = app_for_task.try_state::<AppState>() else { return };
        let emitter = AppEmitter(app_for_task.clone());
        commands::auth::login_poll(&state, &emitter, code).await;
    });

    Ok(dto)
}

/// Sign the active account out; returns the account that is active afterwards, if any.
#[tauri::command]
pub async fn auth_logout(state: State<'_, AppState>) -> CmdResult<Option<Account>> {
    let active = map_err(commands::auth::logout(&state).await)?;
    // The backend follows the active account: off for nobody or an offline one.
    commands::online::follow_account(&state).await;
    Ok(active)
}

#[tauri::command]
pub fn auth_accounts(state: State<'_, AppState>) -> CmdResult<AccountList> {
    map_err(commands::auth::accounts(&state))
}

#[tauri::command]
pub async fn auth_switch(state: State<'_, AppState>, uuid: String) -> CmdResult<Account> {
    let account = map_err(commands::auth::switch(&state, &uuid).await)?;
    commands::online::follow_account(&state).await;
    Ok(account)
}

#[tauri::command]
pub async fn auth_remove(state: State<'_, AppState>, uuid: String) -> CmdResult<AccountList> {
    let before = state.session.lock().unwrap().as_ref().map(|s| s.uuid.clone());
    let list = map_err(commands::auth::remove(&state, &uuid).await)?;
    let after = state.session.lock().unwrap().as_ref().map(|s| s.uuid.clone());
    if before != after {
        commands::online::follow_account(&state).await;
    }
    Ok(list)
}

#[tauri::command]
pub fn auth_current(state: State<'_, AppState>) -> CmdResult<Option<Account>> {
    map_err(commands::auth::current(&state))
}

#[tauri::command]
pub async fn auth_offline(state: State<'_, AppState>, name: String) -> CmdResult<Account> {
    let account = map_err(commands::auth::offline(&state, &name))?;
    // An offline account has no online features: leave the backend if we were on it.
    commands::online::sign_out(&state).await;
    Ok(account)
}

// ------------------------------------------------------------- prepare / launch

#[tauri::command]
pub async fn prepare<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    loadout_id: String,
) -> CmdResult<PrepareReport> {
    map_err(commands::launch::prepare(&state, emitter(&app), &loadout_id).await)
}

#[tauri::command]
pub async fn launch<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    loadout_id: String,
    server: Option<String>,
) -> CmdResult<LaunchReport> {
    let report = map_err(
        commands::launch::launch(&state, emitter(&app), &loadout_id, server.as_deref()).await,
    )?;

    // §5: after a successful spawn the window hides to the tray, and comes back on
    // `game:closed` with the session stats. Configurable, because a second monitor makes
    // it unwanted.
    if map_err(commands::settings::get(&state))?.hide_to_tray_on_launch {
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
pub fn loadouts_create<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    name: String,
    icon: Option<String>,
) -> CmdResult<Loadout> {
    let loadout =
        map_err(commands::loadouts::create(&state, &name, icon.as_deref().unwrap_or("sword")))?;
    crate::tray::refresh(&app);
    Ok(loadout)
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
pub fn loadouts_delete<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    id: String,
) -> CmdResult<Vec<LoadoutSummary>> {
    let rest = map_err(commands::loadouts::delete(&state, &id))?;
    crate::tray::refresh(&app);
    Ok(rest)
}

#[tauri::command]
pub fn loadouts_switch<R: Runtime>(
    app: AppHandle<R>,
    state: State<'_, AppState>,
    id: String,
) -> CmdResult<Loadout> {
    let loadout = map_err(commands::loadouts::switch(&state, &id))?;
    // Tell every window that the active loadout moved — the tray can switch it too, so
    // the UI cannot assume it was the one that asked.
    let _ = app.emit(crate::events::LOADOUT_SWITCHED, &loadout);
    crate::tray::refresh(&app);
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

/// Reveal one of a closed set of places (see `commands::system::open_target`): the
/// launcher log, the game's `latest.log`, the crash reports, one crash report, the game
/// directory.
#[tauri::command]
pub fn open_path(state: State<'_, AppState>, which: String, path: Option<String>) -> CmdResult<String> {
    let target = map_err(commands::system::open_target(&state, &which, path.as_deref()))?;
    let target = target.display().to_string();
    if let Err(e) = tauri_plugin_opener::open_path(&target, None::<&str>) {
        tracing::warn!(%e, %target, "could not open the path");
        return Err(format!("Could not open {target}: {e}"));
    }
    Ok(target)
}

#[tauri::command]
pub fn open_data_dir(state: State<'_, AppState>) -> CmdResult<String> {
    let dir = map_err(commands::system::data_dir(&state))?;
    if let Err(e) = tauri_plugin_opener::open_path(&dir, None::<&str>) {
        tracing::warn!(%e, "could not open the data folder");
    }
    Ok(dir)
}

// ---------------------------------------------------------------------- online

#[tauri::command]
pub fn online_status(state: State<'_, AppState>) -> CmdResult<void_online::OnlineStatus> {
    Ok(commands::online::status(&state))
}

#[tauri::command]
pub async fn cosmetics_state(state: State<'_, AppState>) -> CmdResult<CosmeticsState> {
    map_err(commands::online::cosmetics_state(&state).await)
}

#[tauri::command]
pub async fn cosmetics_equip(
    state: State<'_, AppState>,
    slot: String,
    id: Option<String>,
) -> CmdResult<void_online::EquippedSet> {
    map_err(commands::online::equip(&state, &slot, id).await)
}

#[tauri::command]
pub async fn cosmetics_set_emotes(
    state: State<'_, AppState>,
    ids: Vec<String>,
) -> CmdResult<void_online::EquippedSet> {
    map_err(commands::online::set_emotes(&state, ids).await)
}

/// Create a Stripe Checkout Session and open it in the system browser. Ownership arrives
/// later, as `online:cosmetics`, once Stripe's webhook has granted it.
#[tauri::command]
pub async fn store_checkout(
    state: State<'_, AppState>,
    item: String,
) -> CmdResult<void_online::CheckoutSession> {
    let session = map_err(commands::online::checkout(&state, &item).await)?;
    if let Err(e) = tauri_plugin_opener::open_url(&session.url, None::<&str>) {
        tracing::warn!(%e, "could not open the checkout page");
        return Err("Couldn’t open your browser for checkout. Nothing was charged.".into());
    }
    Ok(session)
}

#[tauri::command]
pub async fn friends_list(state: State<'_, AppState>) -> CmdResult<void_online::FriendsList> {
    map_err(commands::online::friends(&state).await)
}

#[tauri::command]
pub async fn friends_request(
    state: State<'_, AppState>,
    name: String,
) -> CmdResult<void_online::FriendsList> {
    map_err(commands::online::friend_request(&state, &name).await)
}

#[tauri::command]
pub async fn friends_accept(
    state: State<'_, AppState>,
    uuid: String,
) -> CmdResult<void_online::FriendsList> {
    map_err(commands::online::friend_accept(&state, &uuid).await)
}

#[tauri::command]
pub async fn friends_decline(
    state: State<'_, AppState>,
    uuid: String,
) -> CmdResult<void_online::FriendsList> {
    map_err(commands::online::friend_decline(&state, &uuid).await)
}

#[tauri::command]
pub async fn friends_cancel(
    state: State<'_, AppState>,
    uuid: String,
) -> CmdResult<void_online::FriendsList> {
    map_err(commands::online::friend_cancel(&state, &uuid).await)
}

#[tauri::command]
pub async fn friends_remove(
    state: State<'_, AppState>,
    uuid: String,
) -> CmdResult<void_online::FriendsList> {
    map_err(commands::online::friend_remove(&state, &uuid).await)
}

// ---------------------------------------------------------------------- window

#[tauri::command]
pub fn window_minimize<R: Runtime>(app: AppHandle<R>) -> CmdResult<()> {
    if let Some(w) = app.get_webview_window(crate::window::MAIN) {
        let _ = w.minimize();
    }
    Ok(())
}

#[tauri::command]
pub fn window_toggle_maximize<R: Runtime>(app: AppHandle<R>) -> CmdResult<()> {
    if let Some(w) = app.get_webview_window(crate::window::MAIN) {
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
