//! Global, non-loadout settings — `protocol.json#/definitions/global_settings`.
//!
//! These live here rather than in `void-bridge` because they are *persisted*
//! (`~/.void-pvp/settings.json`) as well as sent on the wire, and `void-bridge` depends
//! on this crate rather than the other way round. `void-bridge` re-exports the type.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::keybind::Keybind;

/// Default menu key (§6.3).
pub const DEFAULT_MENU_KEY: &str = "RSHIFT";
/// Default loadout-cycle key (§6.3).
pub const DEFAULT_CYCLE_KEY: &str = "L";
/// Default design-token theme.
pub const DEFAULT_THEME: &str = "void-dark";

/// The subset of §8.3 globals the game needs.
///
/// Account, Java path and RAM are deliberately absent: they are launcher concerns and
/// the mod has no use for them. Every field is optional and unknown keys are preserved
/// in [`GlobalSettings::extra`], because the schema sets `additionalProperties: true` so
/// the launcher can add a global without a protocol bump.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct GlobalSettings {
    /// Key that opens and closes `VoidMenuScreen`.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub menu_key: Option<Keybind>,
    /// Key that cycles to the next loadout in the library.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cycle_loadout_key: Option<Keybind>,
    /// Name of the design-token theme both renderers use.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub theme: Option<String>,
    /// Extra multiplier on the in-game UI, on top of MC GUI scale x window DPI.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ui_scale: Option<f64>,
    /// HUD editor snap grid in unscaled GUI pixels; 0 disables snapping.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub hud_editor_grid: Option<i64>,
    /// Any global the launcher added that this build does not know about, kept verbatim
    /// so a round-trip through Rust never drops it.
    #[serde(flatten)]
    pub extra: Map<String, Value>,
}

impl GlobalSettings {
    /// The factory settings: every schema default, spelled out.
    pub fn factory() -> Self {
        Self {
            menu_key: Keybind::new(DEFAULT_MENU_KEY),
            cycle_loadout_key: Keybind::new(DEFAULT_CYCLE_KEY),
            theme: Some(DEFAULT_THEME.to_string()),
            ui_scale: Some(1.0),
            hud_editor_grid: Some(4),
            extra: Map::new(),
        }
    }

    /// The effective menu key, falling back to the schema default.
    pub fn menu_key(&self) -> Keybind {
        self.menu_key.clone().unwrap_or_else(|| Keybind::new(DEFAULT_MENU_KEY).expect("valid"))
    }

    /// The effective loadout-cycle key, falling back to the schema default.
    pub fn cycle_loadout_key(&self) -> Keybind {
        self.cycle_loadout_key
            .clone()
            .unwrap_or_else(|| Keybind::new(DEFAULT_CYCLE_KEY).expect("valid"))
    }

    /// The effective theme name.
    pub fn theme(&self) -> &str {
        self.theme.as_deref().unwrap_or(DEFAULT_THEME)
    }

    /// The effective in-game UI scale multiplier.
    pub fn ui_scale(&self) -> f64 {
        self.ui_scale.unwrap_or(1.0)
    }

    /// The effective HUD editor snap grid.
    pub fn hud_editor_grid(&self) -> i64 {
        self.hud_editor_grid.unwrap_or(4)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unknown_globals_survive_a_round_trip() {
        let json = r#"{"menu_key":"RSHIFT","chat_opacity":0.5}"#;
        let s: GlobalSettings = serde_json::from_str(json).unwrap();
        assert_eq!(s.extra.get("chat_opacity"), Some(&Value::from(0.5)));
        let back = serde_json::to_value(&s).unwrap();
        assert_eq!(back, serde_json::from_str::<Value>(json).unwrap());
    }

    #[test]
    fn factory_settings_match_the_schema_defaults() {
        let s = GlobalSettings::factory();
        assert_eq!(s.menu_key().as_str(), "RSHIFT");
        assert_eq!(s.cycle_loadout_key().as_str(), "L");
        assert_eq!(s.theme(), "void-dark");
        assert_eq!(s.ui_scale(), 1.0);
        assert_eq!(s.hud_editor_grid(), 4);
    }
}
