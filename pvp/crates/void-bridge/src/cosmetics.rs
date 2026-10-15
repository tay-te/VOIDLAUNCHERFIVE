//! Cosmetics on the wire: the equipped set and its slots.
//!
//! `protocol.json#/definitions/equipped_set` and `#/definitions/cosmetic_slot`. These are
//! the shapes the launcher pushes to the mod in `cosmetics` and the mod names in `equip`;
//! `void-online` uses the same types for what Supabase returns, so the launcher forwards
//! a lookup result to the mod without translating it.
//!
//! The backend only says who owns and wears what. Every launch cosmetic is bundled in
//! the mod jar under `assets/void/cosmetics/<id>/`, so nothing here ever names a URL.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

/// The most emotes a player can have on their wheel.
pub const MAX_EMOTES: usize = 8;

/// A wearable slot. One item each; emotes are not a slot (see [`EquippedSet::emotes`]).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Slot {
    /// Capes.
    Cape,
    /// Wings and other back items.
    Back,
    /// Hats.
    Head,
    /// Masks.
    Face,
    /// Floats above the head; may coexist with a hat.
    Halo,
    /// Particles while moving.
    Trail,
    /// Icon beside the nametag.
    Badge,
}

impl Slot {
    /// Every slot, in the order the contract lists them.
    pub const ALL: [Slot; 7] =
        [Slot::Cape, Slot::Back, Slot::Head, Slot::Face, Slot::Halo, Slot::Trail, Slot::Badge];

    /// The wire name.
    pub fn as_str(self) -> &'static str {
        match self {
            Slot::Cape => "cape",
            Slot::Back => "back",
            Slot::Head => "head",
            Slot::Face => "face",
            Slot::Halo => "halo",
            Slot::Trail => "trail",
            Slot::Badge => "badge",
        }
    }

    /// Parse a wire name; `None` for anything that is not a slot (including `emote`).
    pub fn parse(s: &str) -> Option<Slot> {
        Slot::ALL.into_iter().find(|slot| slot.as_str() == s)
    }
}

impl std::fmt::Display for Slot {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(self.as_str())
    }
}

/// What one player wears, `protocol.json#/definitions/equipped_set`.
///
/// Every slot is always present on the wire, `null` when empty, exactly as the contract's
/// example draws it; `tints` is omitted when there are none.
#[derive(Debug, Clone, PartialEq, Eq, Default, Serialize, Deserialize)]
pub struct EquippedSet {
    /// The player's Minecraft UUID, dashed and lowercase.
    pub uuid: String,
    /// Cape item id.
    #[serde(default)]
    pub cape: Option<String>,
    /// Back item id (wings and the like).
    #[serde(default)]
    pub back: Option<String>,
    /// Hat item id.
    #[serde(default)]
    pub head: Option<String>,
    /// Mask item id.
    #[serde(default)]
    pub face: Option<String>,
    /// Halo item id.
    #[serde(default)]
    pub halo: Option<String>,
    /// Trail item id.
    #[serde(default)]
    pub trail: Option<String>,
    /// Nametag badge item id.
    #[serde(default)]
    pub badge: Option<String>,
    /// Up to [`MAX_EMOTES`] emote ids on the wheel, in wheel order.
    #[serde(default)]
    pub emotes: Vec<String>,
    /// Per-slot recolour for items flagged `tintable`, slot name → `#rrggbb`.
    #[serde(default, skip_serializing_if = "BTreeMap::is_empty")]
    pub tints: BTreeMap<String, String>,
}

impl EquippedSet {
    /// An empty set for `uuid`: a VOID player wearing nothing.
    pub fn empty(uuid: impl Into<String>) -> Self {
        EquippedSet { uuid: uuid.into(), ..Default::default() }
    }

    /// The item in `slot`, if any.
    pub fn get(&self, slot: Slot) -> Option<&str> {
        match slot {
            Slot::Cape => self.cape.as_deref(),
            Slot::Back => self.back.as_deref(),
            Slot::Head => self.head.as_deref(),
            Slot::Face => self.face.as_deref(),
            Slot::Halo => self.halo.as_deref(),
            Slot::Trail => self.trail.as_deref(),
            Slot::Badge => self.badge.as_deref(),
        }
    }

    /// Put `id` in `slot` (or empty it with `None`).
    pub fn set(&mut self, slot: Slot, id: Option<String>) {
        let field = match slot {
            Slot::Cape => &mut self.cape,
            Slot::Back => &mut self.back,
            Slot::Head => &mut self.head,
            Slot::Face => &mut self.face,
            Slot::Halo => &mut self.halo,
            Slot::Trail => &mut self.trail,
            Slot::Badge => &mut self.badge,
        };
        *field = id;
    }
}

/// Is `s` a legal item id — kebab-case, `[a-z0-9]` words joined by single dashes?
pub fn is_item_id(s: &str) -> bool {
    !s.is_empty()
        && s.len() <= 64
        && !s.starts_with('-')
        && !s.ends_with('-')
        && !s.contains("--")
        && s.bytes().all(|b| b.is_ascii_lowercase() || b.is_ascii_digit() || b == b'-')
}

/// Normalise a Minecraft UUID to the dashed lowercase form, from either the dashed or the
/// 32-hex undashed form. `None` for anything else.
pub fn normalize_uuid(s: &str) -> Option<String> {
    let hex: String = s.chars().filter(|c| *c != '-').collect::<String>().to_ascii_lowercase();
    if hex.len() != 32 || !hex.bytes().all(|b| b.is_ascii_hexdigit()) {
        return None;
    }
    // A dashed input must have its dashes in the right places.
    if s.contains('-') {
        let dashes: Vec<usize> = s.match_indices('-').map(|(i, _)| i).collect();
        if dashes != [8, 13, 18, 23] {
            return None;
        }
    }
    Some(format!("{}-{}-{}-{}-{}", &hex[0..8], &hex[8..12], &hex[12..16], &hex[16..20], &hex[20..32]))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn an_equipped_set_carries_every_slot_as_null_and_omits_empty_tints() {
        let v = serde_json::to_value(EquippedSet::empty("a")).unwrap();
        for slot in Slot::ALL {
            assert_eq!(v[slot.as_str()], json!(null), "{slot}");
        }
        assert_eq!(v["emotes"], json!([]));
        assert!(v.get("tints").is_none());
    }

    #[test]
    fn a_sparse_set_parses_with_defaults() {
        let s: EquippedSet = serde_json::from_value(json!({"uuid": "u", "cape": "void-cape-rift"})).unwrap();
        assert_eq!(s.get(Slot::Cape), Some("void-cape-rift"));
        assert_eq!(s.get(Slot::Halo), None);
        assert!(s.emotes.is_empty());
    }

    #[test]
    fn slots_round_trip_and_emote_is_not_one() {
        for slot in Slot::ALL {
            assert_eq!(Slot::parse(slot.as_str()), Some(slot));
        }
        assert_eq!(Slot::parse("emote"), None);
        let mut s = EquippedSet::empty("u");
        s.set(Slot::Badge, Some("void-badge-founder".into()));
        assert_eq!(s.badge.as_deref(), Some("void-badge-founder"));
        s.set(Slot::Badge, None);
        assert_eq!(s.badge, None);
    }

    #[test]
    fn item_ids_are_kebab_case() {
        for ok in ["void-cape-rift", "emote-wave", "a", "cape2"] {
            assert!(is_item_id(ok), "{ok}");
        }
        for bad in ["", "Void-cape", "void--cape", "-cape", "cape-", "void_cape", "void cape"] {
            assert!(!is_item_id(bad), "{bad}");
        }
    }

    #[test]
    fn uuids_normalise_to_dashed_lowercase() {
        let dashed = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
        assert_eq!(normalize_uuid("069A79F444E94726A5BEFCA90E38AAF5").as_deref(), Some(dashed));
        assert_eq!(normalize_uuid(dashed).as_deref(), Some(dashed));
        assert_eq!(normalize_uuid("069a79f4-44e94726-a5be-fca90e38aaf5"), None);
        assert_eq!(normalize_uuid("nope"), None);
    }
}
