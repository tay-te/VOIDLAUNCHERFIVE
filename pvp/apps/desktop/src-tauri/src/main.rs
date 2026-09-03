// Prevents an extra console window on Windows in release.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

#[cfg(feature = "desktop")]
fn main() {
    void_desktop_lib::run();
}

#[cfg(not(feature = "desktop"))]
fn main() {
    eprintln!(
        "void-desktop was compiled without the `desktop` feature, so there is no window to \
         open. This build exists so CI without webkit2gtk can still type-check the command \
         layer. Rebuild with `cargo run --features desktop`."
    );
    std::process::exit(2);
}
