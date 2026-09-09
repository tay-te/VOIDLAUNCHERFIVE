//! The generated settings types must accept **exactly** what `schema/mods.json` describes.
//!
//! `schema_roundtrip.rs` proves the shipped registry survives a round trip, which catches a
//! field that is missing or mistyped *and carries a factory default*. It cannot catch the
//! other two drifts, because both are invisible to a round trip:
//!
//! - a field the schema does not have, sitting on a Rust struct as `Option::None`, silently
//!   skipped on the way out;
//! - an `enum` value the schema gained that the Rust enum did not — nothing in the registry
//!   uses it, so nothing round-trips through it, but the first player to pick it in the UI
//!   writes a loadout this build cannot read back.
//!
//! Both are exactly the failure `#[serde(deny_unknown_fields)]` turns into a *runtime* parse
//! failure of the whole registry. So this test asks serde itself what the types accept, using
//! the two error messages that enumerate it — `unknown field \`x\`, expected one of …` and
//! `unknown variant \`x\`, expected one of …` — and compares that list to the schema's.
//!
//! It is the compile-time-ish half of `node scripts/gen-rust-mods.mjs --check`: that proves
//! the committed generated file matches the schema *text*, this proves the compiled types
//! match the schema *meaning*, and neither needs updating when a mod is added.

use serde_json::{Map, Value};
use void_loadout::mods::MODS_SCHEMA_JSON;
use void_loadout::{ModId, ModStates};

/// Deserializes `{ "<id>": body }` as a `ModStates` and returns the error, if any.
///
/// `ModStates`' fields are the per-mod settings types, so this reaches every one of them
/// through the public API without the crate having to expose a probe of its own.
fn error_for(id: ModId, body: Value) -> Option<String> {
    let mut outer = Map::new();
    outer.insert(id.as_str().to_string(), body);
    serde_json::from_value::<ModStates>(Value::Object(outer)).err().map(|e| e.to_string())
}

/// The backtick-quoted names serde lists after "expected", in the order it prints them.
///
/// Two spellings have to be handled: `expected one of \`a\`, \`b\`, \`c\`` for three or more,
/// and `expected \`a\` or \`b\`` for exactly two. Both are just a run of quoted names, so
/// they are read as one.
fn expected(message: &str) -> Vec<String> {
    let tail = message
        .split_once("expected ")
        .unwrap_or_else(|| panic!("not an `expected …` error: {message}"))
        .1;
    let mut names = Vec::new();
    let mut rest = tail;
    while let Some((_, after)) = rest.split_once('`') {
        let Some((name, tail)) = after.split_once('`') else { break };
        names.push(name.to_string());
        rest = tail;
    }
    names
}

fn schema() -> Value {
    serde_json::from_str(MODS_SCHEMA_JSON).expect("mods.json parses")
}

/// One mod's settings sub-schema.
fn settings_of(doc: &Value, id: ModId) -> &Value {
    &doc["definitions"][format!("{}_settings", id.as_str())]
}

/// Resolves a property that may be a local `$ref`.
fn resolve<'a>(doc: &'a Value, prop: &'a Value) -> &'a Value {
    match prop["$ref"].as_str() {
        Some(r) => &doc["definitions"][r.trim_start_matches("#/definitions/")],
        None => prop,
    }
}

/// Every settings struct accepts exactly the properties its sub-schema declares — no more
/// and no fewer.
#[test]
fn settings_structs_carry_exactly_the_schema_properties() {
    let doc = schema();
    for id in ModId::ALL {
        let properties = settings_of(&doc, id)["properties"]
            .as_object()
            .unwrap_or_else(|| panic!("{id}_settings has properties"));
        // `serde_json::Map` is a `BTreeMap` here, so both sides are compared sorted: the
        // declaration order of the struct is not something the schema can pin.
        let mut want: Vec<String> = properties.keys().cloned().collect();
        want.sort();

        // A key no schema will ever have, so the error lists the struct's whole field set.
        let message = error_for(id, serde_json::json!({ "__not_a_setting__": 1 }))
            .unwrap_or_else(|| panic!("{id}: an unknown key must be rejected"));
        let mut got = expected(&message);
        got.sort();

        assert_eq!(got, want, "{id}: Rust fields differ from mods.json properties");
    }
}

/// `on` is the one required property, and serde agrees it is required.
#[test]
fn on_is_required_and_nothing_else_is() {
    let doc = schema();
    for id in ModId::ALL {
        let required: Vec<&str> = settings_of(&doc, id)["required"]
            .as_array()
            .unwrap_or_else(|| panic!("{id}_settings has required"))
            .iter()
            .map(|v| v.as_str().expect("required entries are strings"))
            .collect();
        assert_eq!(required, ["on"], "{id}: the schema's required set moved");

        let message = error_for(id, Value::Object(Map::new()))
            .unwrap_or_else(|| panic!("{id}: `on` must be required"));
        assert!(message.contains("missing field `on`"), "{id}: {message}");

        // Everything else is optional: `{ "on": … }` alone is a valid settings object.
        assert_eq!(error_for(id, serde_json::json!({ "on": true })), None, "{id}");
    }
}

/// Every `enum`-typed setting maps to a Rust enum with exactly the schema's values, in order.
///
/// This covers the shared HUD chrome block eight times over — `background` and `padding` are
/// declared once in `schema/mods/_shared.json` and reach every `kind: hud` mod — as well as
/// the `$ref`'d `key_swatch`/`pressed_swatch` and the six inline per-mod enums.
#[test]
fn enum_settings_accept_exactly_the_schema_values() {
    let doc = schema();
    let mut checked = 0;
    for id in ModId::ALL {
        let properties = settings_of(&doc, id)["properties"].as_object().expect("properties");
        for (key, prop) in properties {
            let resolved = resolve(&doc, prop);
            let Some(values) = resolved["enum"].as_array() else { continue };
            let want: Vec<String> = values
                .iter()
                .map(|v| v.as_str().expect("enum values are strings").to_string())
                .collect();

            let mut body = Map::new();
            body.insert("on".into(), Value::Bool(true));
            body.insert(key.clone(), Value::String("__not_a_value__".into()));
            let message = error_for(id, Value::Object(body))
                .unwrap_or_else(|| panic!("{id}.{key}: an unknown enum value must be rejected"));
            assert_eq!(
                expected(&message),
                want,
                "{id}.{key}: Rust variants differ from the schema enum",
            );
            checked += 1;
        }
    }
    // The guard this line is for is real — a walk that silently checks nothing passes — but the
    // number was not: `24` was "8 hud mods x 2 shared enums + 2 swatches + 6 inline ones", which
    // is arithmetic over the registry and had to be redone by the next mod. Counted from the
    // schema instead, so it still catches a walk that stopped walking and no longer catches
    // somebody shipping a mod.
    let want_checked = enum_settings_in_schema();
    assert!(want_checked > 0, "the schema declares no enum settings at all");
    assert_eq!(checked, want_checked, "an enum setting stopped being checked");
}

/// How many `<id>_settings` properties in `schema/mods.json` carry an `enum`, resolving `$ref`
/// the same way the walk above does. The expected size of that walk, read from the same file it
/// reads rather than restated as a total.
fn enum_settings_in_schema() -> usize {
    let doc = schema();
    let defs = doc["definitions"].as_object().expect("definitions");
    let mut n = 0;
    for id in ModId::ALL {
        let props = defs[&format!("{id}_settings")]["properties"]
            .as_object()
            .expect("settings properties");
        for value in props.values() {
            let resolved = match value["$ref"].as_str() {
                Some(r) => &defs[r.trim_start_matches("#/definitions/")],
                None => value,
            };
            if resolved["enum"].is_array() {
                n += 1;
            }
        }
    }
    n
}
