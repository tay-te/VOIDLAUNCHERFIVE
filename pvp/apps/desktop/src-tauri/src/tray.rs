//! The tray menu of §5: **Show launcher · Switch loadout ▸ · Quit**.
//!
//! The submenu is rebuilt whenever the library changes, because a tray listing a
//! loadout the player deleted is worse than no tray. `refresh` is called after every
//! create, delete and switch.

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
            // Left-click shows the launcher; the menu stays on right-click, which is
            // what every tray app on Windows does.
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
        let active = state.store.active_id().ok();
        match state.store.summaries() {
            Ok(summaries) => {
                for summary in summaries {
                    let checked = active.as_ref() == Some(&summary.id);
                    let item = CheckMenuItem::with_id(
                        app,
                        format!("{SWITCH_PREFIX}{}", summary.id),
                        &summary.name,
                        true,
                        checked,
                        None::<&str>,
                    )?;
                    submenu = submenu.item(&item);
                }
            }
            Err(e) => tracing::warn!(error = %e, "tray could not read the library"),
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
pub fn refresh<R: Runtime>(app: &AppHandle<R>) {
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
            let Some(loadout_id) = other.strip_prefix(SWITCH_PREFIX) else { return };
            let Some(state) = app.try_state::<AppState>() else { return };
            // `switch` also pushes the loadout down the bridge, so picking one here
            // hot-swaps a running game (§8.2) rather than only changing what the next
            // launch would use.
            match crate::commands::loadouts::switch(&state, loadout_id) {
                Ok(loadout) => {
                    let _ = app.emit(crate::events::LOADOUT_SWITCHED, &loadout);
                    refresh(app);
                }
                Err(e) => tracing::warn!(%e, "tray could not switch loadout"),
            }
        }
    }
}
