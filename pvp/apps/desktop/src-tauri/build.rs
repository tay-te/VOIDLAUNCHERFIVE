fn main() {
    // Only generate the Tauri context when the `desktop` feature is on. Without it the
    // crate is a plain library (commands + adapters + the SLP pinger) that compiles on
    // a runner with no webkit2gtk and no frontend bundle.
    if std::env::var("CARGO_FEATURE_DESKTOP").is_ok() {
        tauri_build::build();
    } else {
        println!("cargo:warning=void-desktop built without the `desktop` feature: no Tauri context generated");
    }
    println!("cargo:rerun-if-changed=tauri.conf.json");
}
