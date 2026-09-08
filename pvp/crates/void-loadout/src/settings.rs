//! Global, non-loadout settings — `protocol.json#/definitions/global_settings`.
//!
//! These live here rather than in `void-bridge` because they are *persisted*
//! (`~/.void-pvp/settings.json`) as well as sent on the wire, and `void-bridge` depends
//! on this crate rather than the other way round. `void-bridge` re-exports the type.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::keybind::Keybind;
use crate::Error;

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

    /// Folds a [`GlobalPatch`] from the game into these settings.
    ///
    /// The merge is done through JSON rather than field by field, and that is what makes
    /// [`GlobalSettings::extra`] survive: a key this build does not model lands in `extra`
    /// on the way back in, exactly as it would from disk. A field-by-field `match` would
    /// have to name every key and would therefore drop the ones it does not name — the
    /// same hole `msg_globals` is a delta to avoid.
    ///
    /// Types are checked here, not trusted: the mod has already clamped the value, but a
    /// frame is a frame. A patch that will not deserialize leaves `self` untouched and
    /// returns [`Error::InvalidGlobal`], so a bad key cannot half-apply.
    pub fn apply_patch(&mut self, patch: &GlobalPatch) -> Result<(), Error> {
        if patch.is_empty() {
            return Ok(());
        }
        let mut merged = match serde_json::to_value(&*self) {
            Ok(Value::Object(map)) => map,
            _ => return Err(Error::InvalidGlobal("settings are not a JSON object".into())),
        };
        for (key, value) in patch.entries() {
            merged.insert(key.clone(), value.clone());
        }
        let next: GlobalSettings = serde_json::from_value(Value::Object(merged))
            .map_err(|e| Error::InvalidGlobal(e.to_string()))?;
        *self = next;
        Ok(())
    }
}

/// The globals that changed, `protocol.json#/definitions/global_patch`.
///
/// A delta and not the whole object, because the mod's own `GlobalSettings` is a fixed
/// five-field class: a mod that echoed the whole object back would erase every global the
/// launcher had added that the mod does not model. See [`GlobalSettings::apply_patch`].
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(transparent)]
pub struct GlobalPatch(Map<String, Value>);

impl GlobalPatch {
    /// An empty patch.
    pub fn new() -> Self {
        Self::default()
    }

    /// Records `key = value`.
    pub fn insert(&mut self, key: impl Into<String>, value: impl Into<Value>) -> &mut Self {
        self.0.insert(key.into(), value.into());
        self
    }

    /// The raw key/value pairs.
    pub fn entries(&self) -> &Map<String, Value> {
        &self.0
    }

    /// Number of keys in the patch.
    pub fn len(&self) -> usize {
        self.0.len()
    }

    /// Whether the patch carries nothing. `globals` requires at least one key, so a
    /// caller must not send an empty patch.
    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

impl FromIterator<(String, Value)> for GlobalPatch {
    fn from_iter<I: IntoIterator<Item = (String, Value)>>(iter: I) -> Self {
        Self(iter.into_iter().collect())
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
    fn a_patch_writes_the_named_key_and_leaves_the_rest_alone() {
        let mut s = GlobalSettings::factory();
        let mut patch = GlobalPatch::new();
        patch.insert("hud_editor_grid", 8);

        s.apply_patch(&patch).unwrap();
        assert_eq!(s.hud_editor_grid(), 8);
        assert_eq!(s.menu_key().as_str(), "RSHIFT", "an untouched global must not move");
        assert_eq!(s.ui_scale(), 1.0);
    }

    #[test]
    fn a_patch_does_not_erase_a_global_the_mod_cannot_model() {
        // The whole reason `globals` is a delta: the mod's GlobalSettings is five fields,
        // so a whole-object echo would drop `chat_opacity` on the first in-game toggle.
        let mut s: GlobalSettings =
            serde_json::from_str(r#"{"menu_key":"RSHIFT","chat_opacity":0.5}"#).unwrap();
        let mut patch = GlobalPatch::new();
        patch.insert("hud_editor_grid", 0);

        s.apply_patch(&patch).unwrap();
        assert_eq!(s.hud_editor_grid(), 0);
        assert_eq!(s.extra.get("chat_opacity"), Some(&Value::from(0.5)));
    }

    #[test]
    fn a_patch_of_the_wrong_type_is_refused_whole() {
        let mut s = GlobalSettings::factory();
        let before = s.clone();
        let mut patch = GlobalPatch::new();
        patch.insert("theme", "void-light");
        patch.insert("hud_editor_grid", "eight");

        assert!(s.apply_patch(&patch).is_err());
        assert_eq!(s, before, "a bad key must not half-apply the good ones");
    }

    #[test]
    fn an_empty_patch_is_a_no_op_rather_than_an_error() {
        let mut s = GlobalSettings::factory();
        let before = s.clone();
        s.apply_patch(&GlobalPatch::new()).unwrap();
        assert_eq!(s, before);
    }

    #[test]
    fn a_patch_is_a_bare_object_on_the_wire() {
        // `#[serde(transparent)]`: the schema says `patch` is the map itself, not a wrapper.
        let mut patch = GlobalPatch::new();
        patch.insert("hud_editor_grid", 8);
        assert_eq!(serde_json::to_value(&patch).unwrap(), serde_json::json!({"hud_editor_grid": 8}));
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
