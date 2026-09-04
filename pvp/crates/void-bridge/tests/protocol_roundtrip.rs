//! Every `examples` entry in `schema/protocol.json` must survive a round trip through
//! the serde types in this crate.
//!
//! `protocol.json` mixes both directions in one `examples` array, so each entry is
//! routed by its `t` to [`JavaToRust`] or [`RustToJava`] — which is itself a check that
//! the two enums between them cover the whole message set and nothing twice.

use serde_json::Value;
use void_bridge::{JavaToRust, RustToJava};

const PROTOCOL_SCHEMA: &str = include_str!("../../../schema/protocol.json");

/// JSON has one number type; Rust has several. Compare with every number as f64.
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

fn examples() -> Vec<Value> {
    let doc: Value = serde_json::from_str(PROTOCOL_SCHEMA).expect("schema parses");
    doc["examples"].as_array().expect("schema carries examples").clone()
}

fn tags(direction: &str) -> Vec<String> {
    let doc: Value = serde_json::from_str(PROTOCOL_SCHEMA).expect("schema parses");
    doc["definitions"][direction]["oneOf"]
        .as_array()
        .expect("a direction is a oneOf")
        .iter()
        .map(|branch| {
            let name = branch["$ref"]
                .as_str()
                .expect("branch is a $ref")
                .rsplit('/')
                .next()
                .expect("ref has a name")
                .to_string();
            doc["definitions"][&name]["properties"]["t"]["const"]
                .as_str()
                .expect("every message pins its `t`")
                .to_string()
        })
        .collect()
}

#[test]
fn protocol_examples_round_trip() {
    let examples = examples();
    assert_eq!(examples.len(), 11, "protocol.json ships eleven examples");

    let inbound = tags("java_to_rust");
    let mut seen: Vec<String> = Vec::new();

    for (i, example) in examples.iter().enumerate() {
        let t = example["t"].as_str().expect("every example has a `t`").to_string();
        let back = if inbound.contains(&t) {
            let msg: JavaToRust = serde_json::from_value(example.clone())
                .unwrap_or_else(|e| panic!("example {i} (`{t}`) does not deserialize: {e}"));
            assert_eq!(msg.tag(), t, "example {i} deserialized as the wrong variant");
            serde_json::to_value(&msg)
        } else {
            let msg: RustToJava = serde_json::from_value(example.clone())
                .unwrap_or_else(|e| panic!("example {i} (`{t}`) does not deserialize: {e}"));
            assert_eq!(msg.tag(), t, "example {i} deserialized as the wrong variant");
            serde_json::to_value(&msg)
        }
        .expect("serializes");

        assert_eq!(normalize(&back), normalize(example), "example {i} (`{t}`) changed shape");
        seen.push(t);
    }

    // The examples are meant to demonstrate every message; if one grows an example this
    // list changes, and if one loses its example we want to know.
    for t in tags("java_to_rust").iter().chain(tags("rust_to_java").iter()) {
        assert!(seen.contains(t), "no example covers `{t}`");
    }
}

#[test]
fn the_two_directions_cover_all_nine_messages_exactly_once() {
    let inbound = tags("java_to_rust");
    let outbound = tags("rust_to_java");
    assert_eq!(inbound, ["hello", "state", "hud", "session", "server", "hotkey"]);
    assert_eq!(outbound, ["init", "loadout", "settings"]);
    for t in &inbound {
        assert!(!outbound.contains(t), "`{t}` is claimed by both directions");
    }
}
