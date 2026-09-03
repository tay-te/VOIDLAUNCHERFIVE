//! TODO(integrate): the loadout library and its persistence belong to `void-loadout`
//! (CONTRACTS.md — `core` owns `crates/void-loadout/`). That crate is currently a
//! doc-comment stub, so this file is a stand-in: the same on-disk shape
//! (`<data dir>/loadouts/<id>.json` + `<data dir>/settings.json`), the same
//! `loadout.json` schema, none of the diffing or registry-default merging.
//!
//! When `void-loadout` lands, replace the body of every method here with a call into
//! it and keep the signatures — `commands/loadouts.rs` and `commands/settings.rs`
//! talk only to this type.
//!
//! Two behaviours must survive the swap, because the UI depends on them:
//!
//! - A loadout may omit a mod entirely; the mod then falls back to its registry
//!   defaults (`mods.json`). That is why `create` writes `mods: {}` rather than
//!   materialising twelve default blocks — it keeps old loadouts valid when a
//!   thirteenth mod is added.
//! - Ids are slugs matching `^[a-z0-9]+(?:-[a-z0-9]+)*$`, unique within the library.

use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

use crate::error::Error;
use crate::models::{Loadout, LoadoutPatch, LoadoutSummary, Settings, SettingsPatch};

pub struct Store {
    root: PathBuf,
    loadouts: BTreeMap<String, Loadout>,
    /// Library order. `loadouts` is a map for lookup; this is the order the picker,
    /// the tray submenu and the in-game L-cycle walk.
    order: Vec<String>,
    settings: Settings,
}

impl Store {
    /// Load the library from disk, seeding the two starter loadouts of the Figma on
    /// first run so the Play screen is never empty.
    pub fn open(root: &Path) -> Result<Self, Error> {
        fs::create_dir_all(root.join("loadouts")).map_err(Error::storage)?;

        let settings: Settings = match fs::read(root.join("settings.json")) {
            Ok(bytes) => serde_json::from_slice(&bytes).unwrap_or_default(),
            Err(_) => Settings::default(),
        };

        let mut loadouts = BTreeMap::new();
        let mut order: Vec<String> = Vec::new();
        if let Ok(entries) = fs::read_dir(root.join("loadouts")) {
            let mut files: Vec<PathBuf> = entries
                .filter_map(Result::ok)
                .map(|e| e.path())
                .filter(|p| p.extension().is_some_and(|e| e == "json"))
                .collect();
            files.sort();
            for path in files {
                match fs::read(&path).map_err(Error::storage).and_then(|b| {
                    serde_json::from_slice::<Loadout>(&b).map_err(Error::from)
                }) {
                    Ok(l) => {
                        order.push(l.id.clone());
                        loadouts.insert(l.id.clone(), l);
                    }
                    Err(e) => tracing::warn!(?path, %e, "skipping unreadable loadout"),
                }
            }
        }

        let mut store = Store {
            root: root.to_path_buf(),
            loadouts,
            order,
            settings,
        };

        if store.order.is_empty() {
            for l in seed_library() {
                store.order.push(l.id.clone());
                store.loadouts.insert(l.id.clone(), l);
            }
            store.settings.active_loadout = store.order[0].clone();
            store.flush_all()?;
        }

        // A settings file that names a loadout the player has since deleted must not
        // brick the Play screen.
        if !store.loadouts.contains_key(&store.settings.active_loadout) {
            store.settings.active_loadout = store.order[0].clone();
            store.flush_settings()?;
        }

        Ok(store)
    }

    pub fn list(&self) -> Vec<LoadoutSummary> {
        self.order
            .iter()
            .filter_map(|id| self.loadouts.get(id))
            .map(LoadoutSummary::from)
            .collect()
    }

    pub fn get(&self, id: &str) -> Result<Loadout, Error> {
        self.loadouts
            .get(id)
            .cloned()
            .ok_or_else(|| Error::UnknownLoadout(id.to_string()))
    }

    pub fn active(&self) -> Result<Loadout, Error> {
        self.get(&self.settings.active_loadout)
    }

    pub fn active_id(&self) -> &str {
        &self.settings.active_loadout
    }

    pub fn create(&mut self, name: &str, icon: &str) -> Result<Loadout, Error> {
        let id = slugify(name);
        validate_id(&id)?;
        if self.loadouts.contains_key(&id) {
            return Err(Error::DuplicateLoadout(id));
        }
        let loadout = Loadout {
            id: id.clone(),
            name: name.trim().to_string(),
            icon: icon.to_string(),
            server: None,
            mc: "1.8.9".into(),
            // Empty on purpose: every mod falls back to its registry defaults.
            mods: BTreeMap::new(),
            hud: Vec::new(),
            stats: Default::default(),
        };
        self.order.push(id.clone());
        self.loadouts.insert(id.clone(), loadout.clone());
        self.flush_loadout(&id)?;
        Ok(loadout)
    }

    pub fn update(&mut self, id: &str, patch: LoadoutPatch) -> Result<Loadout, Error> {
        let loadout = self
            .loadouts
            .get_mut(id)
            .ok_or_else(|| Error::UnknownLoadout(id.to_string()))?;

        if let Some(name) = patch.name {
            loadout.name = name;
        }
        if let Some(icon) = patch.icon {
            loadout.icon = icon;
        }
        if let Some(server) = patch.server {
            loadout.server = server;
        }
        if let Some(mods) = patch.mods {
            // Merge, not replace: the Mods screen sends only the mod it touched.
            for (k, v) in mods {
                loadout.mods.insert(k, v);
            }
        }
        if let Some(hud) = patch.hud {
            loadout.hud = hud;
        }
        let updated = loadout.clone();
        self.flush_loadout(id)?;
        Ok(updated)
    }

    pub fn delete(&mut self, id: &str) -> Result<(), Error> {
        if !self.loadouts.contains_key(id) {
            return Err(Error::UnknownLoadout(id.to_string()));
        }
        if self.loadouts.len() == 1 {
            return Err(Error::LastLoadout);
        }
        self.loadouts.remove(id);
        self.order.retain(|x| x != id);
        fs::remove_file(self.path_for(id)).ok();
        if self.settings.active_loadout == id {
            self.settings.active_loadout = self.order[0].clone();
            self.flush_settings()?;
        }
        Ok(())
    }

    pub fn switch(&mut self, id: &str) -> Result<Loadout, Error> {
        let loadout = self.get(id)?;
        self.settings.active_loadout = id.to_string();
        self.flush_settings()?;
        Ok(loadout)
    }

    pub fn settings(&self) -> Settings {
        self.settings.clone()
    }

    pub fn set_settings(&mut self, patch: SettingsPatch) -> Result<Settings, Error> {
        let s = &mut self.settings;
        if let Some(v) = patch.menu_key {
            s.menu_key = v;
        }
        if let Some(v) = patch.cycle_loadout_key {
            s.cycle_loadout_key = v;
        }
        if let Some(v) = patch.theme {
            s.theme = v;
        }
        if let Some(v) = patch.ui_scale {
            s.ui_scale = v.clamp(0.5, 3.0);
        }
        if let Some(v) = patch.hud_editor_grid {
            s.hud_editor_grid = v.min(64);
        }
        if let Some(v) = patch.java_auto {
            s.java_auto = v;
        }
        if let Some(v) = patch.java_path {
            s.java_path = v;
        }
        if let Some(v) = patch.ram_mb {
            s.ram_mb = v.clamp(1024, 32768);
        }
        if let Some(v) = patch.hide_to_tray_on_launch {
            s.hide_to_tray_on_launch = v;
        }
        if let Some(v) = patch.update_channel {
            s.update_channel = v;
        }
        self.flush_settings()?;
        Ok(self.settings.clone())
    }

    /// Fold a `session` telemetry summary into the active loadout's stats.
    /// TODO(integrate): `void-loadout` owns this accumulation (§8, "Accumulating
    /// `session` telemetry into each loadout's `played_ms` and `fps_avg`").
    pub fn record_session(&mut self, id: &str, played_ms: u64, fps_avg: f64) {
        if let Some(l) = self.loadouts.get_mut(id) {
            let prev_ms = l.stats.played_ms;
            let total = prev_ms + played_ms;
            l.stats.fps_avg = if total == 0 {
                fps_avg
            } else {
                // Time-weighted, so a 30-second session does not move a 40-hour average.
                (l.stats.fps_avg * prev_ms as f64 + fps_avg * played_ms as f64) / total as f64
            };
            l.stats.played_ms = total;
            let _ = self.flush_loadout(id);
        }
    }

    fn path_for(&self, id: &str) -> PathBuf {
        self.root.join("loadouts").join(format!("{id}.json"))
    }

    fn flush_loadout(&self, id: &str) -> Result<(), Error> {
        let Some(l) = self.loadouts.get(id) else {
            return Ok(());
        };
        let bytes = serde_json::to_vec_pretty(l)?;
        write_atomic(&self.path_for(id), &bytes)
    }

    fn flush_settings(&self) -> Result<(), Error> {
        let bytes = serde_json::to_vec_pretty(&self.settings)?;
        write_atomic(&self.root.join("settings.json"), &bytes)
    }

    fn flush_all(&self) -> Result<(), Error> {
        for id in &self.order {
            self.flush_loadout(id)?;
        }
        self.flush_settings()
    }
}

/// Write via a sibling temp file so a crash mid-write cannot leave a half-written
/// loadout behind — that would take the Play screen down on next start.
fn write_atomic(path: &Path, bytes: &[u8]) -> Result<(), Error> {
    let tmp = path.with_extension("json.tmp");
    fs::write(&tmp, bytes).map_err(Error::storage)?;
    fs::rename(&tmp, path).map_err(Error::storage)
}

pub fn slugify(name: &str) -> String {
    let mut out = String::with_capacity(name.len());
    let mut pending_hyphen = false;
    for c in name.trim().chars() {
        if c.is_ascii_alphanumeric() {
            if pending_hyphen && !out.is_empty() {
                out.push('-');
            }
            pending_hyphen = false;
            out.push(c.to_ascii_lowercase());
        } else {
            pending_hyphen = true;
        }
    }
    out
}

pub fn validate_id(id: &str) -> Result<(), Error> {
    let ok = !id.is_empty()
        && id.len() <= 48
        && !id.starts_with('-')
        && !id.ends_with('-')
        && !id.contains("--")
        && id
            .chars()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-');
    if ok {
        Ok(())
    } else {
        Err(Error::BadLoadoutId(id.to_string()))
    }
}

/// The two loadouts of `schema/loadout.json`'s examples, so a first run looks like the
/// Figma rather than an empty library.
fn seed_library() -> Vec<Loadout> {
    let sword = serde_json::json!({
        "id": "sword-pvp", "name": "Sword PvP", "icon": "sword",
        "server": "hypixel", "mc": "1.8.9",
        "mods": {
            "fps":            { "on": true, "scale": 1, "opacity": 1, "color": "#FFFFFF", "show_label": true },
            "keystrokes":     { "on": true, "scale": 1, "opacity": 0.85, "keybind": "NONE", "show_mouse": true, "show_spacebar": true, "show_cps": true },
            "cps":            { "on": true, "mode": "left", "window_ms": 1000 },
            "ping":           { "on": true, "good_ms": 60, "bad_ms": 150 },
            "coordinates":    { "on": false },
            "armor_status":   { "on": true, "orientation": "horizontal", "show_durability": true, "show_held_item": true },
            "potion_effects": { "on": true },
            "toggle_sprint":  { "on": true, "mode": "toggle", "show_status": true },
            "fullbright":     { "on": false, "gamma": 10 },
            "hitboxes":       { "on": false },
            "zoom":           { "on": true, "key": "C", "fov_divisor": 4, "smooth": true },
            "crosshair":      { "on": true, "style": "cross", "size": 5, "gap": 2, "color": "#FFFFFFFF", "outline": true }
        },
        "hud": [
            { "id": "keystrokes",     "anchor": "bottom-left", "dx": 32, "dy": -40, "scale": 1 },
            { "id": "cps",            "anchor": "bottom-left", "dx": 32, "dy": -8,  "scale": 1 },
            { "id": "fps",            "anchor": "top-left",    "dx": 20, "dy": 20 },
            { "id": "ping",           "anchor": "top-left",    "dx": 20, "dy": 38 },
            { "id": "armor_status",   "anchor": "right",       "dx": -20, "dy": 0 },
            { "id": "potion_effects", "anchor": "top-right",   "dx": -20, "dy": 20 }
        ],
        "stats": { "played_ms": 15600000, "fps_avg": 142 }
    });
    let bedwars = serde_json::json!({
        "id": "bedwars", "name": "Bedwars", "icon": "bed", "server": null, "mc": "1.8.9",
        "mods": {
            "keystrokes":    { "on": true },
            "cps":           { "on": true, "mode": "both" },
            "toggle_sprint": { "on": true },
            "zoom":          { "on": true, "key": "V" }
        },
        "hud": [
            { "id": "keystrokes", "anchor": "bottom-left", "dx": 24, "dy": -24 },
            { "id": "cps",        "anchor": "bottom",      "dx": 0,  "dy": -60, "scale": 0.75 }
        ]
    });
    [sword, bedwars]
        .into_iter()
        .filter_map(|v| serde_json::from_value(v).ok())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tmp() -> PathBuf {
        let p = std::env::temp_dir().join(format!(
            "void-store-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        fs::create_dir_all(&p).unwrap();
        p
    }

    #[test]
    fn first_run_seeds_the_figma_library() {
        let root = tmp();
        let store = Store::open(&root).unwrap();
        let ids: Vec<_> = store.list().into_iter().map(|s| s.id).collect();
        assert_eq!(ids, vec!["sword-pvp", "bedwars"]);
        assert_eq!(store.active_id(), "sword-pvp");
        assert!(root.join("loadouts/sword-pvp.json").exists());
    }

    #[test]
    fn library_survives_a_restart() {
        let root = tmp();
        {
            let mut store = Store::open(&root).unwrap();
            store.create("Crystal PvP", "sword").unwrap();
            store.switch("crystal-pvp").unwrap();
        }
        let store = Store::open(&root).unwrap();
        assert_eq!(store.active_id(), "crystal-pvp");
        assert_eq!(store.list().len(), 3);
    }

    #[test]
    fn update_merges_mods_rather_than_replacing_them() {
        let root = tmp();
        let mut store = Store::open(&root).unwrap();
        let mut mods = BTreeMap::new();
        mods.insert(
            "fullbright".to_string(),
            crate::models::ModState {
                on: true,
                settings: Default::default(),
            },
        );
        let updated = store
            .update(
                "sword-pvp",
                LoadoutPatch {
                    mods: Some(mods),
                    ..Default::default()
                },
            )
            .unwrap();
        assert!(updated.mods["fullbright"].on);
        // untouched mods are still there
        assert!(updated.mods.contains_key("keystrokes"));
    }

    #[test]
    fn deleting_the_active_loadout_moves_the_pointer() {
        let root = tmp();
        let mut store = Store::open(&root).unwrap();
        store.delete("sword-pvp").unwrap();
        assert_eq!(store.active_id(), "bedwars");
    }

    #[test]
    fn the_last_loadout_cannot_be_deleted() {
        let root = tmp();
        let mut store = Store::open(&root).unwrap();
        store.delete("bedwars").unwrap();
        assert!(matches!(
            store.delete("sword-pvp"),
            Err(Error::LastLoadout)
        ));
    }

    #[test]
    fn ids_are_slugs() {
        assert_eq!(slugify("  Sword PvP!! "), "sword-pvp");
        assert_eq!(slugify("UHC 1.8"), "uhc-1-8");
        assert!(validate_id("sword-pvp").is_ok());
        assert!(validate_id("Sword").is_err());
        assert!(validate_id("-x").is_err());
        assert!(validate_id("a--b").is_err());
    }

    #[test]
    fn session_stats_are_time_weighted() {
        let root = tmp();
        let mut store = Store::open(&root).unwrap();
        store.record_session("bedwars", 1000, 100.0);
        store.record_session("bedwars", 3000, 200.0);
        let l = store.get("bedwars").unwrap();
        assert_eq!(l.stats.played_ms, 4000);
        assert!((l.stats.fps_avg - 175.0).abs() < 1e-9);
    }
}
