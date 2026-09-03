//! The tray menu of §5: **Show launcher · Switch loadout ▸ · Quit**.
//!
//! The submenu is rebuilt whenever the library changes, because a tray listing a
//! loadout the player deleted is worse than no tray. `refresh` is called from
//! `loadouts_switch` and after create/delete.

use tauri::menu::{CheckMenuItem, Menu, MenuBuilder, MenuEvent, MenuItem, SubmenuBuilder};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter as _, Manager, Runtime};

use crate::state::AppState;

pub const TRAY_ID: &str = "void-tray";

const SHOW: &str = "show";
const QUIT: &str = "quit";
/// Loadout items are `switch:<id>`; the prefix is how the handler tells them apart.
const SWITCH_PREFIX: &str = "switch:";

pub fn build<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<()> {
    let menu = build_menu(app)?;

    TrayIconBuilder::with_id(TRAY_ID)
        .icon(app.default_window_icon().cloned().ok_or_else(|| {
            tauri::Error::AssetNotFound("no default window icon to use for the tray".into())
        })?)
        .tooltip("VOID PVP")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(on_menu_event)
        .on_tray_icon_event(|tray, event| {
            // Left-click the icon = show the launcher. The menu stays on right-click,
            // which is what every tray app on Windows does.
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                crate::window::show(tray.app_handle());
            }
        })
        .build(app)?;

    Ok(())
}

fn build_menu<R: Runtime>(app: &AppHandle<R>) -> tauri::Result<Menu<R>> {
    let show = MenuItem::with_id(app, SHOW, "Show launcher", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, QUIT, "Quit", true, None::<&str>)?;

    let mut submenu = SubmenuBuilder::new(app, "Switch loadout");
    if let Some(state) = app.try_state::<AppState>() {
        let store = state.store.lock().unwrap();
        let active = store.active_id().to_string();
        for summary in store.list() {
            let item = CheckMenuItem::with_id(
                app,
                format!("{SWITCH_PREFIX}{}", summary.id),
                &summary.name,
                true,
                summary.id == active,
                None::<&str>,
            )?;
            submenu = submenu.item(&item);
        }
    }

    MenuBuilder::new(app)
        .item(&show)
        .item(&submenu.build()?)
        .separator()
        .item(&quit)
        .build()
}

/// Rebuild the submenu after the library or the active loadout changed.
pub fn refresh<R: Runtime>(app: &AppHandle<R>, _state: &AppState) {
    let Some(tray) = app.tray_by_id(TRAY_ID) else {
        return;
    };
    match build_menu(app) {
        Ok(menu) => {
            if let Err(e) = tray.set_menu(Some(menu)) {
                tracing::warn!(%e, "could not refresh the tray menu");
            }
        }
        Err(e) => tracing::warn!(%e, "could not rebuild the tray menu"),
    }
}

fn on_menu_event<R: Runtime>(app: &AppHandle<R>, event: MenuEvent) {
    let id = event.id().as_ref().to_string();
    match id.as_str() {
        SHOW => crate::window::show(app),
        QUIT => app.exit(0),
        other => {
            if let Some(loadout_id) = other.strip_prefix(SWITCH_PREFIX) {
                let Some(state) = app.try_state::<AppState>() else {
                    return;
                };
                match crate::commands::loadouts::switch(&state, loadout_id) {
                    Ok(loadout) => {
                        // Same event the command emits, so the UI does not care who
                        // switched. TODO(integrate): once void-bridge is wired, this
                        // also sends the `loadout` protocol message to a running game
                        // (§8.2 — the tray must hot-swap mid-session).
                        let _ = app.emit(crate::events::LOADOUT_SWITCHED, &loadout);
                        refresh(app, &state);
                    }
                    Err(e) => tracing::warn!(%e, "tray could not switch loadout"),
                }
            }
        }
    }
}
