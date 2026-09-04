//! Diffing two loadouts and applying the flat `state` patch of `protocol.json`.
//!
//! The unit of change on the wire is a dotted path, `mods.<mod_id>.<setting>` — flat
//! rather than nested so that concurrent changes to sibling settings merge without
//! conflict (§7). HUD layout does not travel this way: the `hud` message carries the
//! whole layout, because the editor holds the full list and a partial write could leave
//! Rust with a half-moved layout.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::loadout::{HudItem, Loadout};
use crate::mods::{HudModId, Kind, ModId};
use crate::Error;

/// A flat map of dotted paths to their new values, the payload of `state.patch`.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(transparent)]
pub struct StatePatch(BTreeMap<String, Value>);

impl StatePatch {
    /// An empty patch.
    pub fn new() -> Self {
        Self::default()
    }

    /// Splits `mods.<mod_id>.<setting>` into its parts.
    pub fn parse_path(path: &str) -> Result<(ModId, &str), Error> {
        let mut parts = path.splitn(3, '.');
        match (parts.next(), parts.next(), parts.next()) {
            (Some("mods"), Some(id), Some(key)) if !key.is_empty() && !key.contains('.') => {
                let mod_id = ModId::parse(id).ok_or_else(|| Error::UnknownMod(id.to_string()))?;
                Ok((mod_id, key))
            }
            _ => Err(Error::BadPatchPath(path.to_string())),
        }
    }

    /// Records `mods.<mod_id>.<key> = value`.
    pub fn insert(&mut self, mod_id: ModId, key: &str, value: impl Into<Value>) -> &mut Self {
        self.0.insert(format!("mods.{}.{}", mod_id.as_str(), key), value.into());
        self
    }

    /// The raw path/value pairs.
    pub fn entries(&self) -> &BTreeMap<String, Value> {
        &self.0
    }

    /// Number of paths in the patch.
    pub fn len(&self) -> usize {
        self.0.len()
    }

    /// Whether the patch carries nothing. `state` requires at least one path, so a
    /// caller must not send an empty patch.
    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

impl FromIterator<(String, Value)> for StatePatch {
    fn from_iter<I: IntoIterator<Item = (String, Value)>>(iter: I) -> Self {
        Self(iter.into_iter().collect())
    }
}

/// One difference between two loadouts.
///
/// The split is exactly the split between the two protocol messages that carry changes:
/// [`Change::Gameplay`] is everything under `mods.*` and travels in `state`;
/// [`Change::Hud`] and [`Change::HudOrder`] are layout and travel in `hud`.
#[derive(Debug, Clone, PartialEq)]
pub enum Change {
    /// A per-mod setting changed. Note that "gameplay" here means *what the loadout does
    /// in game*, which includes the settings of HUD-kind mods; [`Change::mod_kind`]
    /// narrows further to the registry [`Kind`] when a caller needs that split.
    Gameplay {
        /// Which mod changed.
        mod_id: ModId,
        /// Which setting of that mod changed.
        key: String,
        /// The effective value before, or `Value::Null` if the key did not exist.
        from: Value,
        /// The effective value after, or `Value::Null` if the key no longer exists.
        to: Value,
    },
    /// A HUD item was added, removed or moved.
    Hud {
        /// Which HUD mod's placement changed.
        mod_id: HudModId,
        /// Its placement before, or `None` if it was added.
        from: Option<HudItem>,
        /// Its placement after, or `None` if it was removed.
        to: Option<HudItem>,
    },
    /// The paint order of the HUD changed without any placement changing.
    HudOrder {
        /// Paint order before, back to front.
        from: Vec<HudModId>,
        /// Paint order after, back to front.
        to: Vec<HudModId>,
    },
    /// A loadout field outside `mods` and `hud` changed: name, icon, server, mc, stats.
    Meta {
        /// The loadout field that changed.
        field: &'static str,
        /// Its value before.
        from: Value,
        /// Its value after.
        to: Value,
    },
}

impl Change {
    /// The dotted path this change would take on the wire, for `Gameplay` changes.
    pub fn path(&self) -> Option<String> {
        match self {
            Change::Gameplay { mod_id, key, .. } => {
                Some(format!("mods.{}.{}", mod_id.as_str(), key))
            }
            _ => None,
        }
    }

    /// Whether this change belongs in a `state` message.
    pub fn is_gameplay(&self) -> bool {
        matches!(self, Change::Gameplay { .. })
    }

    /// Whether this change belongs in a `hud` message.
    pub fn is_hud(&self) -> bool {
        matches!(self, Change::Hud { .. } | Change::HudOrder { .. })
    }

    /// The registry kind of the mod this change touches, when it touches one.
    pub fn mod_kind(&self) -> Option<Kind> {
        match self {
            Change::Gameplay { mod_id, .. } => Some(mod_id.kind()),
            Change::Hud { .. } => Some(Kind::Hud),
            _ => None,
        }
    }
}

/// The changes between two loadouts, already split the way the protocol splits them.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct Diff {
    /// Mod-setting changes; the body of a `state` message.
    pub gameplay: Vec<Change>,
    /// HUD layout changes; the trigger for a `hud` message.
    pub hud: Vec<Change>,
    /// Name, icon, server, mc and stats changes; launcher-side only.
    pub meta: Vec<Change>,
}

impl Diff {
    /// Every change, gameplay first, then HUD, then metadata.
    pub fn all(&self) -> Vec<Change> {
        let mut out = self.gameplay.clone();
        out.extend(self.hud.iter().cloned());
        out.extend(self.meta.iter().cloned());
        out
    }

    /// Whether the two loadouts were identical.
    pub fn is_empty(&self) -> bool {
        self.gameplay.is_empty() && self.hud.is_empty() && self.meta.is_empty()
    }

    /// The `state.patch` these gameplay changes would be sent as.
    pub fn state_patch(&self) -> StatePatch {
        self.gameplay
            .iter()
            .filter_map(|c| c.path().map(|p| (p, c.to_value())))
            .collect()
    }
}

impl Change {
    fn to_value(&self) -> Value {
        match self {
            Change::Gameplay { to, .. } | Change::Meta { to, .. } => to.clone(),
            _ => Value::Null,
        }
    }
}

/// Every difference between `from` and `to`, split into gameplay and HUD changes.
pub fn diff_split(from: &Loadout, to: &Loadout) -> Diff {
    let mut out = Diff::default();

    // --- mods: compare *effective* settings, so "explicit value equal to the default"
    // and "omitted" are the same thing, which is what the game actually sees.
    for id in ModId::ALL {
        let before = from.mods.effective(id);
        let after = to.mods.effective(id);
        let mut keys: Vec<&String> = before.keys().chain(after.keys()).collect();
        keys.sort_unstable();
        keys.dedup();
        for key in keys {
            let b = before.get(key).cloned().unwrap_or(Value::Null);
            let a = after.get(key).cloned().unwrap_or(Value::Null);
            if b != a {
                out.gameplay.push(Change::Gameplay {
                    mod_id: id,
                    key: key.clone(),
                    from: b,
                    to: a,
                });
            }
        }
    }

    // --- hud
    for id in HudModId::ALL {
        let b = from.hud.iter().find(|i| i.id == id);
        let a = to.hud.iter().find(|i| i.id == id);
        if b != a {
            out.hud.push(Change::Hud { mod_id: id, from: b.cloned(), to: a.cloned() });
        }
    }
    let order_before: Vec<HudModId> = from.hud.iter().map(|i| i.id).collect();
    let order_after: Vec<HudModId> = to.hud.iter().map(|i| i.id).collect();
    if out.hud.is_empty() && order_before != order_after {
        out.hud.push(Change::HudOrder { from: order_before, to: order_after });
    }

    // --- metadata
    let mut meta = |field: &'static str, b: Value, a: Value| {
        if b != a {
            out.meta.push(Change::Meta { field, from: b, to: a });
        }
    };
    meta("id", Value::from(from.id.as_str()), Value::from(to.id.as_str()));
    meta("name", Value::from(from.name.clone()), Value::from(to.name.clone()));
    meta("icon", Value::from(from.icon.clone()), Value::from(to.icon.clone()));
    meta(
        "server",
        from.server.clone().map_or(Value::Null, Value::from),
        to.server.clone().map_or(Value::Null, Value::from),
    );
    meta("mc", Value::from(from.mc.clone()), Value::from(to.mc.clone()));
    meta(
        "stats",
        serde_json::to_value(from.stats).unwrap_or(Value::Null),
        serde_json::to_value(to.stats).unwrap_or(Value::Null),
    );

    out
}

/// Every difference between `from` and `to`, flattened.
///
/// Callers that need the protocol's own split use [`diff_split`]; this is the flat form
/// the launcher's "unsaved changes" indicator wants.
pub fn diff(from: &Loadout, to: &Loadout) -> Vec<Change> {
    diff_split(from, to).all()
}

/// Applies an inbound `state.patch` to `loadout`, returning what actually changed.
///
/// Each touched mod is materialised from its registry defaults first, so a patch against
/// a mod the loadout omitted writes a complete, valid settings object rather than a
/// fragment. The result is validated against that mod's settings sub-schema, so a bad
/// key or a bad value type is an error and the loadout is left untouched.
pub fn apply_patch(loadout: &mut Loadout, patch: &StatePatch) -> Result<Vec<Change>, Error> {
    // Group by mod so each mod is validated and written exactly once.
    let mut grouped: BTreeMap<ModId, Vec<(&str, &Value)>> = BTreeMap::new();
    for (path, value) in patch.entries() {
        let (mod_id, key) = StatePatch::parse_path(path)?;
        grouped.entry(mod_id).or_default().push((key, value));
    }

    // Build every new settings object before writing any of them: all-or-nothing.
    let mut staged = Vec::with_capacity(grouped.len());
    let mut changes = Vec::new();
    for (mod_id, edits) in grouped {
        let before = loadout.mods.effective(mod_id);
        let mut after = before.clone();
        for (key, value) in edits {
            after.insert(key.to_string(), value.clone());
        }
        crate::mods::validate_settings(mod_id, Value::Object(after.clone()))?;
        for (key, value) in &after {
            let b = before.get(key).cloned().unwrap_or(Value::Null);
            if b != *value {
                changes.push(Change::Gameplay {
                    mod_id,
                    key: key.clone(),
                    from: b,
                    to: value.clone(),
                });
            }
        }
        staged.push((mod_id, after));
    }
    for (mod_id, settings) in staged {
        loadout.mods.set(mod_id, settings)?;
    }
    Ok(changes)
}

impl Loadout {
    /// [`apply_patch`] as a method.
    pub fn apply_patch(&mut self, patch: &StatePatch) -> Result<Vec<Change>, Error> {
        apply_patch(self, patch)
    }

    /// [`diff_split`] as a method.
    pub fn diff(&self, to: &Loadout) -> Diff {
        diff_split(self, to)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::loadout::{Anchor, HudItem, LoadoutId};
    use crate::mods::FullbrightSettings;

    fn base() -> Loadout {
        Loadout::new(LoadoutId::new("x").unwrap(), "X", "sword")
    }

    #[test]
    fn paths_parse_and_reject() {
        assert_eq!(
            StatePatch::parse_path("mods.fullbright.on").unwrap(),
            (ModId::Fullbright, "on")
        );
        assert!(matches!(
            StatePatch::parse_path("mods.nope.on"),
            Err(Error::UnknownMod(_))
        ));
        for bad in ["hud.fps.dx", "mods.fps", "mods.fps.a.b", "", "mods..on"] {
            assert!(StatePatch::parse_path(bad).is_err(), "{bad} should not parse");
        }
    }

    #[test]
    fn apply_patch_materialises_defaults_then_edits() {
        let mut l = base();
        let mut patch = StatePatch::new();
        patch.insert(ModId::Fullbright, "on", true);
        let changes = apply_patch(&mut l, &patch).unwrap();
        assert_eq!(changes.len(), 1);
        assert!(l.mods.is_on(ModId::Fullbright));
        // gamma came from the registry defaults, not from thin air.
        assert_eq!(l.mods.fullbright.as_ref().unwrap().gamma, Some(10.0));
    }

    #[test]
    fn apply_patch_rejects_unknown_settings_and_leaves_the_loadout_alone() {
        let mut l = base();
        let patch: StatePatch =
            [("mods.fullbright.nope".to_string(), Value::from(true))].into_iter().collect();
        assert!(apply_patch(&mut l, &patch).is_err());
        assert_eq!(l.mods.fullbright, None);

        let patch: StatePatch =
            [("mods.fullbright.on".to_string(), Value::from("yes"))].into_iter().collect();
        assert!(apply_patch(&mut l, &patch).is_err());
        assert_eq!(l.mods.fullbright, None);
    }

    #[test]
    fn diff_splits_gameplay_from_hud() {
        let a = base();
        let mut b = a.clone();
        b.mods.fullbright = Some(FullbrightSettings { on: true, gamma: None });
        b.hud.push(HudItem::new(HudModId::Fps, Anchor::TopLeft, 20.0, 20.0));
        b.name = "Y".into();

        let d = diff_split(&a, &b);
        assert_eq!(d.gameplay.len(), 1, "{:?}", d.gameplay);
        assert_eq!(d.hud.len(), 1);
        assert_eq!(d.meta.len(), 1);
        assert!(d.gameplay[0].is_gameplay() && d.hud[0].is_hud());
        assert_eq!(d.state_patch().entries()["mods.fullbright.on"], Value::from(true));
        assert_eq!(diff(&a, &b).len(), 3);
    }

    #[test]
    fn an_explicit_value_equal_to_the_default_is_not_a_change() {
        let a = base();
        let mut b = a.clone();
        b.mods.fullbright = Some(FullbrightSettings { on: false, gamma: Some(10.0) });
        assert!(diff_split(&a, &b).is_empty());
    }

    #[test]
    fn patch_round_trips_through_a_diff() {
        let a = base();
        let mut b = a.clone();
        b.mods.zoom = Some(crate::mods::ZoomSettings {
            on: true,
            key: crate::keybind::Keybind::new("V"),
            fov_divisor: None,
            smooth: None,
            cinematic: None,
        });
        let patch = diff_split(&a, &b).state_patch();
        let mut replayed = a.clone();
        apply_patch(&mut replayed, &patch).unwrap();
        assert!(diff_split(&replayed, &b).gameplay.is_empty());
    }
}
