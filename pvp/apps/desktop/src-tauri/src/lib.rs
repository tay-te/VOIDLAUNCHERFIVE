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
//!   models.rs     the DTOs that cross `invoke`      ← no Tauri
//! ```
//!
//! The bottom half compiles with `--no-default-features`, which is what lets a Linux CI
//! runner with no webkit2gtk still type-check and test the launch pipeline.

pub mod adapters;
pub mod commands;
pub mod error;
pub mod events;
pub mod models;
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

    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_env("VOID_LOG")
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")),
        )
        .init();

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(move |app| {
            // Failing to open the installation is fatal and must say why: without it
            // there is no loadout library, and every screen is empty.
            let app_state = state::AppState::open_default().map_err(|e| {
                tracing::error!(%e, "could not open the VOID installation");
                std::io::Error::other(e.to_string())
            })?;
            tracing::info!(root = %app_state.paths.root().display(), "starting VOID PVP");
            app.manage(app_state);

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
            ipc::settings_get,
            ipc::settings_set,
            ipc::system_info,
            ipc::java_status,
            ipc::server_ping,
            ipc::open_data_dir,
            ipc::window_minimize,
            ipc::window_toggle_maximize,
            ipc::window_close,
            updater::updater_check,
        ])
        .run(tauri::generate_context!())
        .expect("the VOID PVP launcher could not start");
}
