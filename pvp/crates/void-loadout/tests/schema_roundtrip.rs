//! Every `examples` entry in `schema/loadout.json` and `schema/mods.json` must survive a
//! round trip through the serde types in this crate, byte-for-byte after JSON number
//! normalisation.
//!
//! This is the guard `schema/README.md` asks for: the Rust types are hand-written rather
//! than generated, so this test — not the compiler — is what keeps them the same
//! contract as `validate.mjs` checks on the JS side.

use serde_json::Value;
use void_loadout::mods::Registry;
use void_loadout::{HudModId, Loadout, ModId};

const LOADOUT_SCHEMA: &str = include_str!("../../../schema/loadout.json");
const MODS_SCHEMA: &str = include_str!("../../../schema/mods.json");

/// JSON has one number type; Rust has several. `1` and `1.0` are the same value, so
/// compare with every number widened to f64.
fn normalize(v: &Value) -> Value {
    match v {
        Value::Number(n) => Value::from(n.as_f64().expect("finite JSON number")),
        Value::Array(a) => Value::Array(a.iter().map(normalize).collect()),
        Value::Object(o) => {
            Value::Object(o.iter().map(|(k, v)| (k.clone(), normalize(v))).collect())
        }
        other => other.clone(),
    }
}

fn examples(schema: &str) -> Vec<Value> {
    let doc: Value = serde_json::from_str(schema).expect("schema parses");
    doc["examples"].as_array().expect("schema carries examples").clone()
}

#[test]
fn loadout_examples_round_trip() {
    let examples = examples(LOADOUT_SCHEMA);
    assert_eq!(examples.len(), 2, "loadout.json ships two examples");
    for (i, example) in examples.iter().enumerate() {
        let loadout: Loadout = serde_json::from_value(example.clone())
            .unwrap_or_else(|e| panic!("loadout example {i} does not deserialize: {e}"));
        loadout.validate().unwrap_or_else(|e| panic!("loadout example {i} is invalid: {e}"));
        let back = serde_json::to_value(&loadout).expect("serializes");
        assert_eq!(normalize(&back), normalize(example), "loadout example {i} changed shape");
    }
}

#[test]
fn mods_registry_example_round_trips() {
    let examples = examples(MODS_SCHEMA);
    assert_eq!(examples.len(), 1, "mods.json ships the registry as examples[0]");
    for (i, example) in examples.iter().enumerate() {
        let registry: Registry = serde_json::from_value(example.clone())
            .unwrap_or_else(|e| panic!("mods example {i} does not deserialize: {e}"));
        let back = serde_json::to_value(&registry).expect("serializes");
        assert_eq!(normalize(&back), normalize(example), "mods example {i} changed shape");
    }
}

/// The cross-checks `validate.mjs` performs, repeated on the Rust side so a schema edit
/// that breaks them fails `cargo test` too.
#[test]
fn registry_agrees_with_the_narrowed_id_enums() {
    let doc: Value = serde_json::from_str(MODS_SCHEMA).expect("schema parses");
    let ids = |name: &str| -> Vec<String> {
        doc["definitions"][name]["enum"]
            .as_array()
            .unwrap_or_else(|| panic!("{name} has an enum"))
            .iter()
            .map(|v| v.as_str().expect("enum member is a string").to_string())
            .collect()
    };

    assert_eq!(
        ids("mod_id"),
        ModId::ALL.iter().map(|m| m.as_str().to_string()).collect::<Vec<_>>()
    );
    assert_eq!(
        ids("hud_mod_id"),
        HudModId::ALL.iter().map(|m| m.as_str().to_string()).collect::<Vec<_>>()
    );
    assert_eq!(
        ids("gameplay_mod_id"),
        void_loadout::GameplayModId::ALL
            .iter()
            .map(|m| m.as_str().to_string())
            .collect::<Vec<_>>()
    );

    // The `hud`/`gameplay` split in the enums has to agree with each entry's `kind`.
    for id in ModId::ALL {
        let kind = doc["examples"][0]["mods"][id.as_str()]["kind"]
            .as_str()
            .expect("every entry has a kind");
        let expected = match id.kind() {
            void_loadout::Kind::Hud => "hud",
            void_loadout::Kind::Gameplay => "gameplay",
        };
        assert_eq!(kind, expected, "{id}");
    }
}

/// A loadout that omits a mod must stay valid, and must behave as the registry defaults
/// say — that is the compatibility promise `mod_states` makes.
#[test]
fn omitted_mods_fall_back_to_the_registry() {
    let bedwars: Loadout =
        serde_json::from_value(examples(LOADOUT_SCHEMA)[1].clone()).expect("deserializes");
    assert_eq!(bedwars.mods.present().len(), 4, "the example stores four mods");
    // `fps` is absent from the example but on by default in the registry.
    assert!(bedwars.mods.is_on(ModId::Fps));
    assert_eq!(bedwars.mods.effective(ModId::Fps)["show_label"], Value::Bool(true));
}

/// `default_placement` is per-`kind`, and the whole domain is walked rather than the nine
/// rows anyone thought to write down.
///
/// It matters here twice over. `ModEntry` is `deny_unknown_fields`, so a field the schema has
/// and the struct does not is a parse failure of the *entire* registry — that is what the
/// round-trip test above catches. What this one adds is the other direction: the field is
/// `Option`, because one entry struct serves both kinds, so a HUD mod whose placement went
/// missing would deserialize perfectly and simply have nowhere to be. The schema forbids that
/// (each `<id>_entry` `required`s it on a HUD mod and `not`s it on a gameplay one); this
/// asserts the compiled-in registry really satisfies it, which is what makes
/// `Registry::default_placement` total over `HudModId`.
#[test]
fn every_hud_mod_is_placed_and_no_gameplay_mod_is() {
    let doc: Value = serde_json::from_str(MODS_SCHEMA).expect("schema parses");
    for id in ModId::ALL {
        let placed = doc["examples"][0]["mods"][id.as_str()].get("default_placement").is_some();
        assert_eq!(
            placed,
            id.kind() == void_loadout::Kind::Hud,
            "{id}: default_placement must be present iff the mod is kind hud",
        );
    }
    // Total over the narrowed enum: this is the call site that would panic if it were not.
    let registry = void_loadout::mods::registry();
    for id in HudModId::ALL {
        let place = registry.default_placement(id);
        assert!(place.dx.abs() <= 4096.0 && place.dy.abs() <= 4096.0, "{id} is off-screen");
    }
}
