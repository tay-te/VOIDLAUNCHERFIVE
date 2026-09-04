//! The loadout model — `schema/loadout.json`.

use std::fmt;

use serde::{Deserialize, Deserializer, Serialize, Serializer};
use serde_json::{Map, Value};

use crate::mods::{
    defaults_json, validate_settings, ArmorStatusSettings, CoordinatesSettings, CpsSettings,
    CrosshairSettings, FpsSettings, FullbrightSettings, HitboxesSettings, HudModId,
    HypixelSafe, KeystrokesSettings, ModId, PingSettings, PotionEffectsSettings,
    ToggleSprintSettings, ZoomSettings,
};
use crate::Error;

/// The default Minecraft version of a loadout. Only 1.8.9 exists today (§15).
pub const DEFAULT_MC: &str = "1.8.9";

/// Lower-case slug identifying a loadout: letters, digits and single hyphens.
#[derive(Debug, Clone, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct LoadoutId(String);

impl LoadoutId {
    /// Whether `s` matches `loadout.json#/definitions/loadout_id`.
    pub fn is_valid(s: &str) -> bool {
        if s.is_empty() || s.len() > 48 {
            return false;
        }
        // `^[a-z0-9]+(?:-[a-z0-9]+)*$`: no leading, trailing or doubled hyphen.
        s.split('-')
            .all(|seg| !seg.is_empty() && seg.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit()))
    }

    /// Builds a loadout id, returning `None` when the slug is malformed.
    pub fn new(s: impl Into<String>) -> Option<Self> {
        let s = s.into();
        Self::is_valid(&s).then_some(Self(s))
    }

    /// Derives a slug from a display name, e.g. `Sword PvP` becomes `sword-pvp`.
    pub fn slugify(name: &str) -> Option<Self> {
        let mut out = String::new();
        for ch in name.chars() {
            if ch.is_ascii_alphanumeric() {
                out.push(ch.to_ascii_lowercase());
            } else if !out.ends_with('-') {
                out.push('-');
            }
        }
        let slug = out.trim_matches('-').to_string();
        let slug = if slug.len() > 48 { slug[..48].trim_end_matches('-').to_string() } else { slug };
        Self::new(slug)
    }

    /// The slug.
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for LoadoutId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

impl Serialize for LoadoutId {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&self.0)
    }
}

impl<'de> Deserialize<'de> for LoadoutId {
    fn deserialize<D: Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        let s = String::deserialize(d)?;
        Self::new(s.clone())
            .ok_or_else(|| serde::de::Error::custom(format!("`{s}` is not a valid loadout id")))
    }
}

/// The screen edge or corner a HUD item is pinned to (§8.1).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum Anchor {
    /// Top-left corner.
    TopLeft,
    /// Top edge, horizontally centred.
    Top,
    /// Top-right corner.
    TopRight,
    /// Left edge, vertically centred.
    Left,
    /// Screen centre.
    Center,
    /// Right edge, vertically centred.
    Right,
    /// Bottom-left corner.
    BottomLeft,
    /// Bottom edge, horizontally centred.
    Bottom,
    /// Bottom-right corner.
    BottomRight,
}

/// The placement of one HUD mod: anchor plus offset plus scale, never absolute pixels.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct HudItem {
    /// Which HUD mod this item positions.
    pub id: HudModId,
    /// Screen anchor the offsets are measured from.
    pub anchor: Anchor,
    /// Horizontal offset in unscaled GUI pixels; positive is right.
    pub dx: f64,
    /// Vertical offset in unscaled GUI pixels; positive is down.
    pub dy: f64,
    /// Per-item size multiplier. Omitted means 1.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub scale: Option<f64>,
}

impl HudItem {
    /// A HUD item at `anchor` plus `dx`/`dy`, with no explicit scale.
    pub fn new(id: HudModId, anchor: Anchor, dx: f64, dy: f64) -> Self {
        Self { id, anchor, dx, dy, scale: None }
    }

    /// The effective scale: the explicit value, or 1.
    pub fn effective_scale(&self) -> f64 {
        self.scale.unwrap_or(1.0)
    }
}

/// Ordered list of HUD placements; order is paint order, back to front.
pub type HudLayout = Vec<HudItem>;

/// Session statistics accumulated by Rust from the `session` protocol message.
#[derive(Debug, Clone, Copy, Default, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LoadoutStats {
    /// Total milliseconds played with this loadout active.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub played_ms: Option<u64>,
    /// Average frames per second across all sessions with this loadout active.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub fps_avg: Option<f64>,
}

impl LoadoutStats {
    /// Folds one `session` report into these stats.
    ///
    /// `fps_avg` is weighted by played time, so a long session moves the average more
    /// than a short one. `played_ms` in the report is cumulative for the *game* session,
    /// so the caller passes the delta since the previous report.
    pub fn accumulate(&mut self, played_ms_delta: u64, fps_avg: f64) {
        let prior = self.played_ms.unwrap_or(0);
        let total = prior.saturating_add(played_ms_delta);
        self.fps_avg = Some(match self.fps_avg {
            Some(prev) if total > 0 => {
                (prev * prior as f64 + fps_avg * played_ms_delta as f64) / total as f64
            }
            _ => fps_avg,
        });
        self.played_ms = Some(total);
    }
}

/// Enabled state plus settings for each mod. Every key is optional: an omitted mod falls
/// back to its registry `defaults`, which is what keeps old loadouts valid as mods are
/// added.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
#[allow(missing_docs)]
pub struct ModStates {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub fps: Option<FpsSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub keystrokes: Option<KeystrokesSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub cps: Option<CpsSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ping: Option<PingSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub coordinates: Option<CoordinatesSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub armor_status: Option<ArmorStatusSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub potion_effects: Option<PotionEffectsSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub toggle_sprint: Option<ToggleSprintSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub fullbright: Option<FullbrightSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub hitboxes: Option<HitboxesSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub zoom: Option<ZoomSettings>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub crosshair: Option<CrosshairSettings>,
}

impl ModStates {
    fn as_object(&self) -> Map<String, Value> {
        match serde_json::to_value(self).expect("mod states always serialize") {
            Value::Object(o) => o,
            _ => unreachable!("mod states are always an object"),
        }
    }

    /// The settings this loadout stores for `id`, or `None` when it omits the mod.
    pub fn get(&self, id: ModId) -> Option<Map<String, Value>> {
        self.as_object()
            .remove(id.as_str())
            .and_then(|v| match v {
                Value::Object(o) => Some(o),
                _ => None,
            })
    }

    /// The settings that actually apply: registry defaults overlaid with what this
    /// loadout stores. Never empty, and always complete.
    pub fn effective(&self, id: ModId) -> Map<String, Value> {
        let mut merged = defaults_json(id).clone();
        if let Some(present) = self.get(id) {
            for (k, v) in present {
                merged.insert(k, v);
            }
        }
        merged
    }

    /// Whether `id` is enabled, falling back to the registry default.
    pub fn is_on(&self, id: ModId) -> bool {
        self.effective(id).get("on").and_then(Value::as_bool).unwrap_or(false)
    }

    /// Writes a complete settings object for `id`, validating it against that mod's
    /// settings sub-schema first.
    pub fn set(&mut self, id: ModId, settings: Map<String, Value>) -> Result<(), Error> {
        let validated = validate_settings(id, Value::Object(settings))?;
        let mut all = self.as_object();
        all.insert(id.as_str().to_string(), validated);
        *self = serde_json::from_value(Value::Object(all))
            .map_err(|e| Error::InvalidSettings { mod_id: id, source: e })?;
        Ok(())
    }

    /// Drops `id` from the loadout, so it falls back to registry defaults.
    pub fn clear(&mut self, id: ModId) {
        let mut all = self.as_object();
        all.remove(id.as_str());
        *self = serde_json::from_value(Value::Object(all))
            .expect("removing a key from valid mod states leaves valid mod states");
    }

    /// The mods this loadout stores explicitly, in registry order.
    pub fn present(&self) -> Vec<ModId> {
        let all = self.as_object();
        ModId::ALL.into_iter().filter(|id| all.contains_key(id.as_str())).collect()
    }
}

/// A complete, hot-swappable loadout (§8).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Loadout {
    /// Stable slug identifying the loadout.
    pub id: LoadoutId,
    /// Display name shown in the library and the in-game switcher.
    pub name: String,
    /// Icon name resolved by the UI against the shared icon set; not a file path.
    pub icon: String,
    /// Server profile slug this loadout is intended for, or null.
    ///
    /// Always serialized, including as `null`: the schema gives it a `null` default and
    /// every example carries the key explicitly.
    #[serde(default)]
    pub server: Option<String>,
    /// Minecraft version this loadout targets.
    pub mc: String,
    /// Per-mod enabled state and settings.
    pub mods: ModStates,
    /// Layout of the HUD items this loadout draws.
    pub hud: HudLayout,
    /// Accumulated play statistics.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub stats: Option<LoadoutStats>,
}

impl Loadout {
    /// An empty loadout targeting 1.8.9: every mod falls back to registry defaults.
    pub fn new(id: LoadoutId, name: impl Into<String>, icon: impl Into<String>) -> Self {
        Self {
            id,
            name: name.into(),
            icon: icon.into(),
            server: None,
            mc: DEFAULT_MC.to_string(),
            mods: ModStates::default(),
            hud: Vec::new(),
            stats: None,
        }
    }

    /// The reduced form sent in `init.loadouts` and used by the tray switcher.
    pub fn summary(&self) -> LoadoutSummary {
        LoadoutSummary {
            id: self.id.clone(),
            name: self.name.clone(),
            icon: self.icon.clone(),
            server: self.server.clone(),
            stats: self.stats,
        }
    }

    /// The mods that are enabled, in registry order, counting registry defaults.
    pub fn enabled_mods(&self) -> Vec<ModId> {
        ModId::ALL.into_iter().filter(|id| self.mods.is_on(*id)).collect()
    }

    /// Checks the invariants JSON Schema cannot express, plus the numeric bounds.
    ///
    /// The store runs this before writing and after reading, so a hand-edited file
    /// cannot smuggle a duplicate HUD entry or an out-of-range offset into the wire.
    pub fn validate(&self) -> Result<(), Error> {
        if self.name.is_empty() || self.name.chars().count() > 48 {
            return Err(Error::Invalid("`name` must be 1..=48 characters".into()));
        }
        if self.icon.is_empty() || self.icon.chars().count() > 32 {
            return Err(Error::Invalid("`icon` must be 1..=32 characters".into()));
        }
        if self.server.as_ref().is_some_and(|s| s.chars().count() > 32) {
            return Err(Error::Invalid("`server` must be at most 32 characters".into()));
        }
        if self.hud.len() > 7 {
            return Err(Error::Invalid("`hud` holds at most one item per HUD mod (7)".into()));
        }
        let mut seen: Vec<HudModId> = Vec::with_capacity(self.hud.len());
        for item in &self.hud {
            if seen.contains(&item.id) {
                return Err(Error::DuplicateHudItem(item.id));
            }
            seen.push(item.id);
            if !(-4096.0..=4096.0).contains(&item.dx) || !(-4096.0..=4096.0).contains(&item.dy) {
                return Err(Error::Invalid(format!(
                    "hud item `{}` offset is outside -4096..=4096",
                    item.id
                )));
            }
            if let Some(scale) = item.scale {
                if !(0.25..=4.0).contains(&scale) {
                    return Err(Error::Invalid(format!(
                        "hud item `{}` scale is outside 0.25..=4",
                        item.id
                    )));
                }
            }
        }
        // Round-trips every present mod through its settings sub-schema.
        for id in self.mods.present() {
            if let Some(settings) = self.mods.get(id) {
                validate_settings(id, Value::Object(settings))?;
            }
        }
        Ok(())
    }
}

/// The reduced loadout form sent in `init.loadouts`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct LoadoutSummary {
    /// Slug of the summarised loadout.
    pub id: LoadoutId,
    /// Display name.
    pub name: String,
    /// Icon name.
    pub icon: String,
    /// Server slug, or null. Always serialized, as in [`Loadout::server`].
    #[serde(default)]
    pub server: Option<String>,
    /// Play statistics.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub stats: Option<LoadoutStats>,
}

/// Whether every *enabled* mod in `loadout` is classified `safe` (§11).
///
/// This is the HYPIXEL-READY badge. A mod the loadout omits still counts if the registry
/// enables it by default, which is why the check goes through [`ModStates::is_on`].
pub fn hypixel_ready(loadout: &Loadout) -> bool {
    loadout
        .enabled_mods()
        .into_iter()
        .all(|id| id.hypixel_safe() == HypixelSafe::Safe)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::mods::FullbrightSettings;

    #[test]
    fn loadout_ids_follow_the_schema_pattern() {
        for ok in ["sword-pvp", "bedwars", "uhc", "a", "a1-b2-c3"] {
            assert!(LoadoutId::is_valid(ok), "{ok} should be valid");
        }
        for bad in ["", "-x", "x-", "a--b", "Sword", "sword_pvp", "sword pvp"] {
            assert!(!LoadoutId::is_valid(bad), "{bad} should be invalid");
        }
        assert_eq!(LoadoutId::slugify("Sword PvP").unwrap().as_str(), "sword-pvp");
    }

    #[test]
    fn omitted_mods_fall_back_to_registry_defaults() {
        let l = Loadout::new(LoadoutId::new("empty").unwrap(), "Empty", "sword");
        // fps defaults to on, coordinates defaults to off.
        assert!(l.mods.is_on(ModId::Fps));
        assert!(!l.mods.is_on(ModId::Coordinates));
        assert_eq!(l.mods.effective(ModId::Zoom)["key"], Value::from("C"));
    }

    #[test]
    fn hypixel_ready_tracks_the_grey_mods() {
        let mut l = Loadout::new(LoadoutId::new("x").unwrap(), "X", "sword");
        assert!(hypixel_ready(&l));
        l.mods.fullbright = Some(FullbrightSettings { on: true, gamma: None });
        assert!(!hypixel_ready(&l));
        l.mods.fullbright = Some(FullbrightSettings { on: false, gamma: None });
        assert!(hypixel_ready(&l));
    }

    #[test]
    fn duplicate_hud_entries_are_rejected() {
        let mut l = Loadout::new(LoadoutId::new("x").unwrap(), "X", "sword");
        l.hud.push(HudItem::new(HudModId::Fps, Anchor::TopLeft, 20.0, 20.0));
        l.validate().unwrap();
        l.hud.push(HudItem::new(HudModId::Fps, Anchor::Top, 0.0, 0.0));
        assert!(matches!(l.validate(), Err(Error::DuplicateHudItem(HudModId::Fps))));
    }

    #[test]
    fn stats_average_is_weighted_by_played_time() {
        let mut s = LoadoutStats::default();
        s.accumulate(1000, 100.0);
        s.accumulate(3000, 200.0);
        assert_eq!(s.played_ms, Some(4000));
        assert_eq!(s.fps_avg, Some(175.0));
    }
}
