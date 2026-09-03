//! The command layer, minus Tauri.
//!
//! Every function here takes `&AppState` (and, where it emits, a `&dyn Emitter`) and
//! returns `Result<T, Error>`. The `#[tauri::command]` wrappers that adapt them to
//! `State<'_, AppState>` / `AppHandle` and flatten `Error` into `String` live in
//! `crate::ipc`, which is compiled only under the `desktop` feature.
//!
//! That split is not ceremony: it is what lets `cargo check --no-default-features`
//! cover the whole launcher on a Linux runner with no webkit2gtk, and what lets these
//! functions be unit-tested without standing up a window.

pub mod auth;
pub mod launch;
pub mod loadouts;
pub mod servers;
pub mod settings;
pub mod system;
