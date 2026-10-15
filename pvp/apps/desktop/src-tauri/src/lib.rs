//! VOID PVP — desktop launcher.
//!
//! Per `CONTRACTS.md` this crate owns `apps/desktop/` and nothing else: one frameless
//! window, the tray, the updater, and thin `#[tauri::command]` wrappers over
//! `void-core`. Auth, downloads, the JVM spawn, the WS bridge and the loadout store all
//! live in `pvp/crates/`, and this crate calls into them rather than re-implementing
//! any of it.
//!
//! Layering, outermost first:
//!
//! ```text
//!   ipc.rs        #[tauri::command] wrappers        ← needs Tauri
//!   tray.rs       tray menu + loadout submenu       ← needs Tauri
//!   window.rs     show / hide-to-tray               ← needs Tauri
//!   updater.rs    tauri-plugin-updater              ← needs Tauri
//!   commands/     the launcher's own logic          ← no Tauri
//!   adapters/     bridge+JVM orchestration, SLP     ← no Tauri
//!   presence.rs   Discord Rich Presence thread      ← no Tauri
//!   logging.rs    stdout + rolling launcher log     ← no Tauri
//!   models.rs     the DTOs that cross `invoke`      ← no Tauri
//! ```
//!
//! The bottom half compiles with `--no-default-features`, which is what lets a Linux CI
//! runner with no webkit2gtk still type-check and test the launch pipeline.

pub mod adapters;
pub mod commands;
pub mod error;
pub mod events;
pub mod logging;
pub mod models;
pub mod presence;
pub mod state;

#[cfg(feature = "desktop")]
pub mod ipc;
#[cfg(feature = "desktop")]
pub mod tray;
#[cfg(feature = "desktop")]
pub mod updater;
#[cfg(feature = "desktop")]
pub mod window;

#[cfg(feature = "desktop")]
pub fn run() {
    use tauri::Manager;

    // The rolling file log lives in the data directory; resolve it before anything can
    // log. The guard flushes the file writer when `run` returns at exit.
    let _log_guard = void_core::Paths::new().ok().and_then(|p| logging::init(&p.logs_dir()));

    tauri::Builder::default()
        // First, so a second launch hands over to this one before any other plugin or
        // window starts: the running launcher comes to the front and the new process exits.
        .plugin(tauri_plugin_single_instance::init(|app, _argv, _cwd| {
            window::show(app);
        }))
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(move |app| {
            // Failing to open the installation is fatal and must say why: without it
            // there is no loadout library, and every screen is empty.
            let mut app_state = state::AppState::open_default().map_err(|e| {
                tracing::error!(%e, "could not open the VOID installation");
                std::io::Error::other(e.to_string())
            })?;
            tracing::info!(
                root = %app_state.paths.root().display(),
                version = env!("CARGO_PKG_VERSION"),
                "starting VOID PVP"
            );
            let rpc_on = app_state
                .store
                .settings()
                .map(|g| commands::settings::discord_rpc(&g))
                .unwrap_or(true);
            app_state.presence = presence::Presence::start(presence::Presence::app_id(), rpc_on);
            app_state.presence.set(presence::Activity::Idle);
            // `online:*` events for the window, for the life of the app.
            tauri::async_runtime::spawn(commands::online::forwarder(
                app_state.online.clone(),
                std::sync::Arc::new(ipc::AppEmitter(app.handle().clone())),
            ));
            app.manage(app_state);
            app.manage(updater::PendingUpdate::default());

            let handle = app.handle().clone();
            if let Err(e) = tray::build(&handle) {
                // A missing tray is a degraded launcher, not a dead one.
                tracing::warn!(%e, "could not build the tray icon");
            }

            // Sign in from the keychain before the first frame asks. Not awaited: a slow
            // or unreachable Microsoft must not hold the window closed.
            let auth_handle = handle.clone();
            tauri::async_runtime::spawn(async move {
                let Some(state) = auth_handle.try_state::<state::AppState>() else { return };
                match commands::auth::restore(&state).await {
                    Ok(Some(account)) => {
                        use tauri::Emitter as _;
                        let _ = auth_handle.emit(
                            events::AUTH_STATUS,
                            models::AuthStatus::Complete { account },
                        );
                        // Online features follow a restored Microsoft account.
                        commands::online::sign_in(&state).await;
                    }
                    Ok(None) => tracing::info!("no stored account"),
                    Err(e) => tracing::warn!(%e, "could not restore the stored account"),
                }
            });

            updater::spawn_check(handle);
            Ok(())
        })
        .on_window_event(|window, event| {
            // The X hides to the tray; Quit in the tray is the only exit (§5).
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .invoke_handler(tauri::generate_handler![
            ipc::auth_login,
            ipc::auth_logout,
            ipc::auth_current,
            ipc::auth_offline,
            ipc::auth_accounts,
            ipc::auth_switch,
            ipc::auth_remove,
            ipc::prepare,
            ipc::launch,
            ipc::game_kill,
            ipc::game_log_tail,
            ipc::loadouts_list,
            ipc::loadouts_get,
            ipc::loadouts_active,
            ipc::loadouts_create,
            ipc::loadouts_update,
            ipc::loadouts_delete,
            ipc::loadouts_switch,
            ipc::loadouts_export,
            ipc::loadouts_import,
            ipc::settings_get,
            ipc::settings_set,
            ipc::system_info,
            ipc::java_status,
            ipc::server_ping,
            ipc::open_data_dir,
            ipc::open_path,
            ipc::online_status,
            ipc::cosmetics_state,
            ipc::cosmetics_equip,
            ipc::cosmetics_set_emotes,
            ipc::store_checkout,
            ipc::friends_list,
            ipc::friends_request,
            ipc::friends_accept,
            ipc::friends_decline,
            ipc::friends_cancel,
            ipc::friends_remove,
            ipc::window_minimize,
            ipc::window_toggle_maximize,
            ipc::window_close,
            updater::updater_check,
            updater::updater_install,
        ])
        .run(tauri::generate_context!())
        .expect("the VOID PVP launcher could not start");
}
