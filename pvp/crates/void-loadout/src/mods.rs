//! The closed registry of the 13 mods — `schema/mods.json`.
//!
//! `mods.json` is a JSON Schema *document*; the registry VOID actually ships is its
//! `examples[0]`. That document is compiled into the binary with [`include_str!`] and
//! parsed once, so [`registry()`] is the single source of factory defaults and of every
//! mod's [`Kind`] and [`HypixelSafe`] class.
//!
//! # What is written here and what is generated
//!
//! The data has never been able to drift — it *is* the compiled-in schema. The **types**
//! around it could, and did: a `ModId` variant, a `<Name>Settings` struct, a settings enum,
//! a `ModRegistryEntries` field and three match arms, per mod, by hand. Every settings
//! struct is `#[serde(deny_unknown_fields)]` (deliberately — see `validate_settings`), so
//! a field left out of a struct is not a warning but a **runtime parse failure of the whole
//! registry**, for every mod at once. That is the tax `scripts/gen-rust-mods.mjs` removes:
//! it writes `mods/generated.rs` from `schema/mods.json`, and `--check` is the CI gate that
//! the committed file still matches. `docs/adding-a-mod.md` §5 is the short version.
//!
//! Generated: [`ModId`], [`HudModId`], [`GameplayModId`] and their `ALL`/`as_str`, one
//! settings struct and one settings enum per mod, [`ModRegistryEntries`], and the three
//! per-mod dispatches ([`Registry::info`] and the two behind [`defaults_json`] and
//! `validate_settings`).
//!
//! Hand-written, because each carries a decision a generator cannot: [`Kind`], [`Category`]
//! and [`HypixelSafe`]; [`ModEntry`] and [`ModInfo`]; [`registry()`], [`defaults_json`] and
//! `validate_settings`; and the semantic half of `impl ModId`.
//!
//! Two notes the generated types cannot carry, both about `toggle_sprint` and both about the
//! same habit. It used to have a `show_status` setting and no longer does — see
//! `ModRegistry.java`; a sprint indicator comes back as its own placeable HUD mod, not as a
//! setting on a gameplay one. It also used to have `sneak_too`, and sneak is now `toggle_sneak`,
//! its own mod with its own bind. **Both removals are only safe because of `REMOVED_SETTINGS` in
//! [`crate::store`]**: every settings struct here is `deny_unknown_fields`, so a loadout already
//! on disk carrying a deleted key fails to deserialise and takes the whole library listing with
//! it. Deleting a setting is a two-part change, and this half is the half that cannot see the
//! other one.

use std::fmt;
use std::sync::OnceLock;

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use crate::Error;

mod generated;

pub use generated::*;

/// The raw `schema/mods.json` document, compiled in.
pub const MODS_SCHEMA_JSON: &str = include_str!("../../../schema/mods.json");

// ---------------------------------------------------------------------------
// identity
// ---------------------------------------------------------------------------

impl ModId {
    /// Parses a snake_case mod id.
    pub fn parse(s: &str) -> Option<Self> {
        ModId::ALL.into_iter().find(|m| m.as_str() == s)
    }

    /// Whether this mod draws (`hud`) or mutates a client-side option (`gameplay`).
    pub fn kind(self) -> Kind {
        registry().info(self).kind
    }

    /// The §11 anti-cheat class of this mod.
    pub fn hypixel_safe(self) -> HypixelSafe {
        registry().info(self).hypixel_safe
    }

    /// The Mods-panel filter category of this mod (Figma 244:538).
    pub fn category(self) -> Category {
        registry().info(self).category
    }

    /// The `hud_mod_id` narrowing, when this mod owns a draggable HUD item.
    pub fn as_hud(self) -> Option<HudModId> {
        HudModId::from_mod_id(self)
    }
}

/// Data direction of a mod (§3).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Kind {
    /// Reads game state and draws.
    Hud,
    /// Mutates a documented client-side option through an actuator Mixin.
    Gameplay,
}

/// The Mods panel's filter taxonomy — the tabs of Figma 244:538.
///
/// Independent of [`Kind`] on purpose: `Kind` says which direction data flows (draw, or
/// mutate a client-side option), this says which tab the tile sits under. Crosshair is
/// [`Kind::Gameplay`] but [`Category::Visual`]; Zoom is [`Kind::Gameplay`] but
/// [`Category::Utility`]. It lives in `mods.json` so no consumer hard-codes the mapping.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Category {
    /// Draws a readout over the game.
    Hud,
    /// Changes how a fight plays.
    Pvp,
    /// Changes how the game looks.
    Visual,
    /// A convenience that is neither of the above.
    Utility,
}

impl Category {
    /// Every category, in tab order.
    pub const ALL: [Category; 4] =
        [Category::Hud, Category::Pvp, Category::Visual, Category::Utility];

    /// The lower-case id used in `mods.json`.
    pub fn as_str(self) -> &'static str {
        match self {
            Category::Hud => "hud",
            Category::Pvp => "pvp",
            Category::Visual => "visual",
            Category::Utility => "utility",
        }
    }

    /// The tab label as the frame prints it: `HUD`, `PvP`, `Visual`, `Utility`.
    pub fn label(self) -> &'static str {
        match self {
            Category::Hud => "HUD",
            Category::Pvp => "PvP",
            Category::Visual => "Visual",
            Category::Utility => "Utility",
        }
    }
}

impl fmt::Display for Category {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// Anti-cheat posture of a mod (§11).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HypixelSafe {
    /// Unambiguously allowed.
    Safe,
    /// Tolerated but not endorsed; disqualifies the HYPIXEL-READY badge.
    Grey,
}

// ---------------------------------------------------------------------------
// registry
// ---------------------------------------------------------------------------

/// One row of the §3 table plus its §11 class and factory defaults.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ModEntry<S> {
    /// The mod's snake_case id; equals the key it is stored under.
    pub id: ModId,
    /// Whether this mod draws or mutates.
    pub kind: Kind,
    /// Which tab of the Mods panel this mod sits under.
    pub category: Category,
    /// Anti-cheat class.
    pub hypixel_safe: HypixelSafe,
    /// Human-readable name as it appears in the Mods panel.
    pub label: String,
    /// Glyph the Mods list and the quick palette draw for this mod.
    ///
    /// Not used by the launcher — Rust draws nothing — but the field has to exist because
    /// `ModEntry` is `deny_unknown_fields`, and it is carried on the entry rather than in a
    /// per-application table because two applications need it and neither can import the other.
    pub icon: String,
    /// One-line explanation shown under the label.
    pub description: String,
    /// The 1.8.9 field, method or injection point the mod reads or writes.
    pub source: String,
    /// Factory settings, used when a loadout omits this mod.
    pub defaults: S,
    /// Where this mod's widget starts on a HUD nobody has touched, for a `kind: hud` mod.
    ///
    /// `Option` because one entry struct serves both kinds and a gameplay mod draws nothing,
    /// so it has nowhere to be. The two halves of that are enforced by the schema rather than
    /// by this type — each `<id>_entry` `required`s the field on a HUD mod and forbids it on a
    /// gameplay one — so `None` here means gameplay and never "a HUD mod nobody placed".
    /// [`Registry::default_placement`] is the total accessor over [`HudModId`], and it is what
    /// callers should use.
    ///
    /// Not read by the launcher: Rust seeds its three shipped loadouts from `defaults.rs`,
    /// which are hand-authored product layouts rather than the factory one. The field exists
    /// because `ModEntry` is `deny_unknown_fields` and the registry now carries it, and
    /// because this is the one place the layout is written down for the two halves that do
    /// draw it — `ModRegistry.java`'s generated table and `@void/protocol`'s.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub default_placement: Option<HudPlacement>,
}

impl<S> ModEntry<S> {
    /// This entry's classification and copy, without its typed defaults.
    ///
    /// Generic over the settings type, so the generated [`Registry::info`] dispatch is one
    /// arm per mod and nothing else — the field list lives here, once.
    pub(crate) fn info(&self) -> ModInfo<'_> {
        ModInfo {
            id: self.id,
            kind: self.kind,
            category: self.category,
            hypixel_safe: self.hypixel_safe,
            label: self.label.as_str(),
            icon: self.icon.as_str(),
            description: self.description.as_str(),
            source: self.source.as_str(),
        }
    }
}

impl<S: Serialize> ModEntry<S> {
    /// This entry's factory defaults, as a JSON object.
    pub(crate) fn defaults_object(&self) -> Map<String, Value> {
        match serde_json::to_value(&self.defaults).expect("registry defaults must serialize") {
            Value::Object(o) => o,
            _ => unreachable!("mod settings are always objects"),
        }
    }
}

/// Classification and copy for one mod, without its typed defaults.
///
/// Borrows from the registry it came from; [`registry()`] hands out `ModInfo<'static>`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ModInfo<'a> {
    /// The mod's id.
    pub id: ModId,
    /// Whether this mod draws or mutates.
    pub kind: Kind,
    /// Which tab of the Mods panel this mod sits under.
    pub category: Category,
    /// Anti-cheat class.
    pub hypixel_safe: HypixelSafe,
    /// Human-readable name.
    pub label: &'a str,
    /// Glyph the Mods list and the quick palette draw for this mod.
    pub icon: &'a str,
    /// One-line explanation.
    pub description: &'a str,
    /// The injection point or field.
    pub source: &'a str,
}

/// A registry document: `{ version, mods }`, i.e. `mods.json`'s `examples[0]`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Registry {
    /// Integer revision, bumped when a mod is added, removed or reclassified.
    pub version: u32,
    /// The 13 entries.
    pub mods: ModRegistryEntries,
}

#[derive(Deserialize)]
struct SchemaDocument {
    examples: Vec<Registry>,
}

static REGISTRY: OnceLock<Registry> = OnceLock::new();
static DEFAULTS_JSON: OnceLock<Vec<(ModId, Map<String, Value>)>> = OnceLock::new();

/// The compiled-in mod registry: `schema/mods.json`'s `examples[0]`.
///
/// Panics only if the compiled-in schema document is malformed, which a build can never
/// observe without the file changing under it — the round-trip test in
/// `tests/schema_roundtrip.rs` is the guard.
pub fn registry() -> &'static Registry {
    REGISTRY.get_or_init(|| {
        let doc: SchemaDocument = serde_json::from_str(MODS_SCHEMA_JSON)
            .expect("schema/mods.json must parse as a registry document");
        doc.examples
            .into_iter()
            .next()
            .expect("schema/mods.json must carry the shipped registry as examples[0]")
    })
}

impl Registry {
    /// Every mod's classification, in registry order.
    pub fn all_info(&self) -> Vec<ModInfo<'_>> {
        ModId::ALL.into_iter().map(|id| self.info(id)).collect()
    }
}

/// The factory defaults of one mod, as a JSON object.
///
/// This is the merge base for [`crate::ModStates::effective`] and the fallback that keeps
/// an old loadout valid when a mod is added (`loadout.json#/definitions/mod_states`).
pub fn defaults_json(id: ModId) -> &'static Map<String, Value> {
    let table = DEFAULTS_JSON.get_or_init(|| {
        let r = registry();
        ModId::ALL.into_iter().map(|id| (id, r.defaults_object(id))).collect()
    });
    table
        .iter()
        .find(|(k, _)| *k == id)
        .map(|(_, v)| v)
        .expect("every mod id has defaults")
}

/// Deserializes a settings object for `id`, rejecting unknown keys and bad values.
///
/// This is how a JSON blob assembled by [`crate::apply_patch`] is checked against the
/// mod's settings sub-schema before it is written back into a loadout. Every settings type
/// is `#[serde(deny_unknown_fields)]`, matching the schema's `additionalProperties: false`:
/// a key VOID does not model must never survive into a loadout file, because on the way out
/// again it would be silently dropped and the player's setting would be gone.
pub(crate) fn validate_settings(id: ModId, value: Value) -> Result<Value, Error> {
    generated::check_settings(id, value)
}

/// One mod's half of [`validate_settings`], monomorphised by the generated dispatch.
fn check<T: serde::de::DeserializeOwned + Serialize>(
    id: ModId,
    value: Value,
) -> Result<Value, Error> {
    let typed: T =
        serde_json::from_value(value).map_err(|e| Error::InvalidSettings { mod_id: id, source: e })?;
    Ok(serde_json::to_value(typed).expect("mod settings always serialize"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn registry_holds_every_mod_id() {
        let r = registry();
        // No literal count and no literal version. Both were here — `13` and `version == 4` —
        // and both had to be edited by the next mod and the one after that, which is a test
        // that measures how recently it was updated rather than whether the code is right.
        // `version` is a human decision about loadout compatibility and nothing here can check
        // it; what IS checkable is that the parsed registry covers `ModId` exactly.
        assert!(r.version >= 1, "registry document carries no version");
        assert_eq!(r.all_info().len(), ModId::ALL.len());
        for id in ModId::ALL {
            assert_eq!(r.info(id).id, id, "entry `id` must equal its key");
        }
    }

    #[test]
    fn kind_agrees_with_the_narrowed_id_enums() {
        for id in ModId::ALL {
            match id.kind() {
                Kind::Hud => {
                    assert!(HudModId::from_mod_id(id).is_some(), "{id} missing from hud_mod_id");
                    assert!(GameplayModId::from_mod_id(id).is_none());
                }
                Kind::Gameplay => {
                    assert!(GameplayModId::from_mod_id(id).is_some());
                    assert!(HudModId::from_mod_id(id).is_none());
                }
            }
        }
    }

    #[test]
    fn category_is_carried_by_the_registry_and_is_not_kind() {
        // Every mod has one, every tab has at least one mod, and the two
        // classifications genuinely differ — if they ever collapsed into each other,
        // `category` would be dead weight and the Mods panel could filter on `kind`.
        for c in Category::ALL {
            assert!(
                ModId::ALL.into_iter().any(|id| id.category() == c),
                "no mod is categorised {c}",
            );
        }
        assert_eq!(ModId::Crosshair.kind(), Kind::Gameplay);
        assert_eq!(ModId::Crosshair.category(), Category::Visual);
        assert_eq!(ModId::Zoom.category(), Category::Utility);
        assert_eq!(ModId::Fps.category(), Category::Hud);
        // The watermark draws, so `kind: hud`, but the Mods panel tabs it under Visual.
        assert_eq!(ModId::Watermark.kind(), Kind::Hud);
        assert_eq!(ModId::Watermark.category(), Category::Visual);
    }

    #[test]
    fn labels_are_the_panel_copy_the_frames_print() {
        // Figma 244:538 reads "FPS display", "CPS counter", "Ping display"; the registry
        // is the one place that copy lives, so no consumer overrides it.
        let r = registry();
        assert_eq!(r.info(ModId::Fps).label, "FPS display");
        assert_eq!(r.info(ModId::Cps).label, "CPS counter");
        assert_eq!(r.info(ModId::Ping).label, "Ping display");
    }

    #[test]
    fn grey_mods_are_exactly_fullbright_hitboxes_and_overlay() {
        // An exact set, not a count, and it is edited by hand on purpose: `grey` is the class
        // that decides whether the HYPIXEL-READY badge can be shown, so a mod joining it is a
        // product decision that should have to touch a test with a name in it. `overlay` is the
        // third, and `schema/mods/overlay.json`'s `$comment` carries the argument — two of its
        // five switches (`hide_fire`, `hide_pumpkin`) remove a view cost the game imposes
        // deliberately, which is not the "purely aesthetic" category §6.1 would need it to be.
        let grey: Vec<ModId> = ModId::ALL
            .into_iter()
            .filter(|id| id.hypixel_safe() == HypixelSafe::Grey)
            .collect();
        assert_eq!(grey, vec![ModId::Fullbright, ModId::Hitboxes, ModId::Overlay]);
    }

    #[test]
    fn defaults_validate_against_their_own_settings_schema() {
        for id in ModId::ALL {
            let d = Value::Object(defaults_json(id).clone());
            validate_settings(id, d).unwrap_or_else(|e| panic!("{id}: {e}"));
        }
    }
}
