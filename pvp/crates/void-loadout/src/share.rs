//! Loadout share codes: a whole loadout as one line of text a player can paste.
//!
//! ```text
//! VOID1.<base64url, no padding, of raw-DEFLATE(json)>.<crc32(json) as 8 lowercase hex>
//! ```
//!
//! The JSON is the loadout's name, icon, server, mc, the registry version it was made
//! against, its HUD layout, its quick pins, and **only the settings that differ from the
//! registry defaults** — that last rule is what keeps a typical code to a couple of hundred
//! characters. Ids and stats never travel: an import gets a fresh id, and a stranger's
//! play time is not yours.
//!
//! The same format is written and read by `ShareCode` in the Java mod (the in-game Loadouts
//! screen) and by the TypeScript fake bridge. `schema/share-code.fixtures.json` holds codes
//! made by each implementation; every test suite decodes all of them, so the three cannot
//! drift apart without a test failing.
//!
//! Decoding is strict about **structure** and lenient about **content**:
//!
//! - an unknown version, a checksum mismatch, data that does not decode, or no usable name
//!   refuses the whole code ([`ShareError`]);
//! - a mod or setting this build does not know, or a value outside its schema range, is
//!   dropped — that mod falls back to its default — and counted in
//!   [`SharedLoadout::dropped`], so a code made on a newer client still imports.
//!
//! The result always passes [`Loadout::validate`], which is the Rust half of
//! `schema/loadout.json` plus the registry.

use std::io::{Read, Write};

use base64::Engine as _;
use serde_json::{json, Map, Value};

use crate::loadout::{normalise_quick, HudItem, Loadout, LoadoutId, ModStates, DEFAULT_MC};
use crate::mods::{defaults_json, registry, validate_settings, ModId, MODS_SCHEMA_JSON};

/// The format version this build writes, and the newest it reads.
pub const SHARE_VERSION: u32 = 1;

/// The prefix every version-1 code starts with.
pub const SHARE_PREFIX: &str = "VOID1.";

/// Longest code accepted. A full loadout with every setting changed is well under this.
pub const MAX_CODE_LEN: usize = 16 * 1024;

/// Largest decompressed payload accepted — a guard against a crafted code that inflates
/// to something enormous.
const MAX_JSON_LEN: u64 = 256 * 1024;

const B64: base64::engine::GeneralPurpose = base64::engine::general_purpose::URL_SAFE_NO_PAD;

/// Why a code was refused. The strings are `bridge.json#/definitions/import_result`'s
/// `error` enum, shared with the Java mod and the launcher UI.
#[derive(Debug, Clone, Copy, PartialEq, Eq, thiserror::Error)]
pub enum ShareError {
    /// Nothing was pasted.
    #[error("nothing to import")]
    Empty,
    /// Not a share code, or its data does not decode.
    #[error("that is not a VOID loadout code")]
    Format,
    /// Made by a newer format than this build reads.
    #[error("that code was made by a newer VOID")]
    Version,
    /// Damaged in transit: the data decoded but its checksum does not match.
    #[error("that code is damaged — copy it again")]
    Checksum,
    /// Decoded, but carries no usable loadout.
    #[error("that code does not contain a loadout")]
    Invalid,
    /// The library is already at its 128-loadout cap.
    #[error("the library is full")]
    Full,
}

impl ShareError {
    /// The wire string.
    pub fn as_str(self) -> &'static str {
        match self {
            ShareError::Empty => "empty",
            ShareError::Format => "format",
            ShareError::Version => "version",
            ShareError::Checksum => "checksum",
            ShareError::Invalid => "invalid",
            ShareError::Full => "full",
        }
    }
}

/// A decoded share code: everything a loadout needs except its id.
#[derive(Debug, Clone, PartialEq)]
pub struct SharedLoadout {
    /// Display name, trimmed, 1 to 48 characters.
    pub name: String,
    /// Icon name.
    pub icon: String,
    /// Server slug, if the code carried a usable one.
    pub server: Option<String>,
    /// Minecraft version.
    pub mc: String,
    /// The settings the code carried that this build accepts.
    pub mods: ModStates,
    /// The HUD layout the code carried, minus unusable items.
    pub hud: Vec<HudItem>,
    /// Quick pins, if the code carried any.
    pub quick: Option<Vec<ModId>>,
    /// The registry version the code was made against.
    pub registry_version: Option<u64>,
    /// How many mods, settings, HUD items and pins were dropped as unusable.
    pub dropped: usize,
}

impl SharedLoadout {
    /// The loadout under `id`.
    pub fn into_loadout(self, id: LoadoutId) -> Loadout {
        Loadout {
            id,
            name: self.name,
            icon: self.icon,
            server: self.server,
            mc: self.mc,
            mods: self.mods,
            hud: self.hud,
            quick: self.quick,
            stats: None,
        }
    }
}

/// Encodes `loadout` as a share code.
pub fn encode(loadout: &Loadout) -> String {
    let json = serde_json::to_vec(&payload(loadout)).expect("a share payload always serializes");
    let mut deflater = flate2::write::DeflateEncoder::new(Vec::new(), flate2::Compression::best());
    deflater.write_all(&json).expect("writing to a Vec cannot fail");
    let compressed = deflater.finish().expect("writing to a Vec cannot fail");
    format!("{SHARE_PREFIX}{}.{:08x}", B64.encode(compressed), crc32fast::hash(&json))
}

/// The JSON a code carries, before compression. Exposed for tests and tooling.
pub fn payload(loadout: &Loadout) -> Value {
    let mut mods = Map::new();
    for id in ModId::ALL {
        let defaults = defaults_json(id);
        let effective = loadout.mods.effective(id);
        let changed: Map<String, Value> = effective
            .into_iter()
            .filter(|(k, v)| defaults.get(k) != Some(v))
            .collect();
        if !changed.is_empty() {
            mods.insert(id.as_str().to_string(), Value::Object(changed));
        }
    }
    let mut out = json!({
        "name": loadout.name,
        "icon": loadout.icon,
        "mc": loadout.mc,
        "reg": registry().version,
        "mods": mods,
        "hud": loadout.hud,
    });
    if let Some(server) = &loadout.server {
        out["server"] = json!(server);
    }
    if let Some(quick) = &loadout.quick {
        out["quick"] = json!(quick);
    }
    out
}

/// Decodes a share code. See the module docs for what is strict and what is lenient.
pub fn decode(code: &str) -> Result<SharedLoadout, ShareError> {
    let code = code.trim();
    if code.is_empty() {
        return Err(ShareError::Empty);
    }
    if code.len() > MAX_CODE_LEN {
        return Err(ShareError::Format);
    }
    let (head, rest) = code.split_once('.').ok_or(ShareError::Format)?;
    let version = head
        .strip_prefix("VOID")
        .and_then(|v| v.parse::<u32>().ok())
        .ok_or(ShareError::Format)?;
    if version == 0 {
        return Err(ShareError::Format);
    }
    if version > SHARE_VERSION {
        return Err(ShareError::Version);
    }
    let (body, check) = rest.rsplit_once('.').ok_or(ShareError::Format)?;
    if check.len() != 8 || !check.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(ShareError::Format);
    }
    let expected = u32::from_str_radix(check, 16).map_err(|_| ShareError::Format)?;
    let compressed = B64.decode(body).map_err(|_| ShareError::Format)?;
    let mut json = Vec::new();
    flate2::read::DeflateDecoder::new(compressed.as_slice())
        .take(MAX_JSON_LEN + 1)
        .read_to_end(&mut json)
        .map_err(|_| ShareError::Format)?;
    if json.len() as u64 > MAX_JSON_LEN {
        return Err(ShareError::Format);
    }
    if crc32fast::hash(&json) != expected {
        return Err(ShareError::Checksum);
    }
    let value: Value = serde_json::from_slice(&json).map_err(|_| ShareError::Format)?;
    let Value::Object(obj) = value else {
        return Err(ShareError::Invalid);
    };
    read_payload(&obj)
}

fn read_payload(obj: &Map<String, Value>) -> Result<SharedLoadout, ShareError> {
    let mut dropped = 0usize;

    let name: String = obj
        .get("name")
        .and_then(Value::as_str)
        .map(|n| n.trim().chars().take(48).collect::<String>())
        .map(|n| n.trim_end().to_string())
        .filter(|n| !n.is_empty())
        .ok_or(ShareError::Invalid)?;
    let icon = obj
        .get("icon")
        .and_then(Value::as_str)
        .filter(|i| !i.is_empty() && i.chars().count() <= 32)
        .unwrap_or("sword")
        .to_string();
    let server = obj
        .get("server")
        .and_then(Value::as_str)
        .filter(|s| s.chars().count() <= 32)
        .map(str::to_string);
    let mc = obj
        .get("mc")
        .and_then(Value::as_str)
        .filter(|m| is_mc_version(m))
        .unwrap_or(DEFAULT_MC)
        .to_string();

    let mut mods = ModStates::default();
    if let Some(Value::Object(given)) = obj.get("mods") {
        for (key, settings) in given {
            let Some(id) = ModId::parse(key) else {
                dropped += 1;
                continue;
            };
            let Value::Object(settings) = settings else {
                dropped += 1;
                continue;
            };
            let (accepted, lost) = lenient_settings(id, settings);
            dropped += lost;
            if accepted.len() > 1 || accepted.get("on") != defaults_json(id).get("on") {
                if mods.set(id, accepted).is_err() {
                    dropped += 1;
                }
            }
        }
    }

    let mut hud: Vec<HudItem> = Vec::new();
    if let Some(Value::Array(items)) = obj.get("hud") {
        for item in items {
            match serde_json::from_value::<HudItem>(item.clone()) {
                Ok(h)
                    if !hud.iter().any(|o| o.id == h.id)
                        && (-4096.0..=4096.0).contains(&h.dx)
                        && (-4096.0..=4096.0).contains(&h.dy)
                        && h.scale.is_none_or(|s| (0.25..=4.0).contains(&s)) =>
                {
                    hud.push(h)
                }
                _ => dropped += 1,
            }
        }
    }

    let quick = match obj.get("quick") {
        Some(Value::Array(items)) => {
            let names: Vec<&str> = items.iter().filter_map(Value::as_str).collect();
            let kept = normalise_quick(names.iter().copied());
            dropped += items.len() - kept.len();
            Some(kept)
        }
        _ => None,
    };

    Ok(SharedLoadout {
        name,
        icon,
        server,
        mc,
        mods,
        hud,
        quick,
        registry_version: obj.get("reg").and_then(Value::as_u64),
        dropped,
    })
}

/// Keeps every setting of `incoming` that `id` knows and whose value its schema accepts,
/// in order, each checked against the defaults plus what was accepted before it. `on` is
/// always present in the result. Returns the accepted map and how many were dropped.
fn lenient_settings(id: ModId, incoming: &Map<String, Value>) -> (Map<String, Value>, usize) {
    let defaults = defaults_json(id);
    let mut accepted = Map::new();
    let mut dropped = 0;
    for (key, value) in incoming {
        if !defaults.contains_key(key) {
            dropped += 1;
            continue;
        }
        if !within_schema_range(id, key, value) {
            dropped += 1;
            continue;
        }
        let mut candidate = defaults.clone();
        candidate.extend(accepted.clone());
        candidate.insert(key.clone(), value.clone());
        match validate_settings(id, Value::Object(candidate)) {
            Ok(_) => {
                accepted.insert(key.clone(), value.clone());
            }
            Err(_) => dropped += 1,
        }
    }
    if !accepted.contains_key("on") {
        if let Some(on) = defaults.get("on") {
            accepted.insert("on".into(), on.clone());
        }
    }
    (accepted, dropped)
}

/// Whether a numeric `value` is inside the `minimum`/`maximum` the setting's schema
/// declares. The typed settings structs are `Option<f64>`/integers with no bounds of their
/// own, so this reads the bounds off the compiled-in `mods.json`, following local `$ref`s
/// (the shared `scale` and `opacity` are refs). Non-numbers pass: serde checks those.
fn within_schema_range(id: ModId, key: &str, value: &Value) -> bool {
    static DOC: std::sync::OnceLock<Value> = std::sync::OnceLock::new();
    let Some(n) = value.as_f64() else { return true };
    let doc = DOC.get_or_init(|| serde_json::from_str(MODS_SCHEMA_JSON).expect("mods.json parses"));
    let defs = &doc["definitions"];
    let mut node = &defs[format!("{}_settings", id.as_str())]["properties"][key];
    for _ in 0..4 {
        match node.get("$ref").and_then(Value::as_str).and_then(|r| r.strip_prefix("#/definitions/")) {
            Some(name) if node.get("minimum").is_none() && node.get("maximum").is_none() => {
                node = &defs[name];
            }
            _ => break,
        }
    }
    let lo = node.get("minimum").and_then(Value::as_f64).unwrap_or(f64::NEG_INFINITY);
    let hi = node.get("maximum").and_then(Value::as_f64).unwrap_or(f64::INFINITY);
    n.is_finite() && n >= lo && n <= hi
}

fn is_mc_version(s: &str) -> bool {
    let parts: Vec<&str> = s.split('.').collect();
    (2..=3).contains(&parts.len())
        && parts.iter().all(|p| !p.is_empty() && p.len() <= 4 && p.bytes().all(|b| b.is_ascii_digit()))
}

/// A fresh id for `name` that is not in `taken`: the name slugified, then `-2`, `-3`, …
/// The Java mod picks ids the same way, so a loadout created in game and one created in
/// the launcher from the same name get the same id.
pub fn unique_id<'a>(name: &str, taken: impl IntoIterator<Item = &'a LoadoutId> + Clone) -> LoadoutId {
    let base = LoadoutId::slugify(name).unwrap_or_else(|| LoadoutId::new("loadout").expect("valid"));
    let is_taken = |candidate: &LoadoutId| taken.clone().into_iter().any(|t| t == candidate);
    if !is_taken(&base) {
        return base;
    }
    for n in 2u32.. {
        let suffix = format!("-{n}");
        let stem = base.as_str();
        let room = 48usize.saturating_sub(suffix.len());
        let stem = if stem.len() > room { stem[..room].trim_end_matches('-') } else { stem };
        if let Some(candidate) = LoadoutId::new(format!("{stem}{suffix}")) {
            if !is_taken(&candidate) {
                return candidate;
            }
        }
    }
    unreachable!("the integers do not run out before the library does")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::defaults;
    use crate::loadout::Anchor;
    use crate::mods::HudModId;

    fn fixtures() -> Value {
        serde_json::from_str(include_str!("../../../schema/share-code.fixtures.json")).unwrap()
    }

    #[test]
    fn a_loadout_survives_the_round_trip() {
        for original in defaults::default_library() {
            let code = encode(&original);
            assert!(code.starts_with(SHARE_PREFIX), "{code}");
            let back = decode(&code).unwrap();
            assert_eq!(back.dropped, 0);
            let id = original.id.clone();
            let mut restored = back.into_loadout(id);
            restored.validate().unwrap();
            restored.stats = original.stats;
            for m in ModId::ALL {
                assert_eq!(restored.mods.effective(m), original.mods.effective(m), "{m}");
            }
            assert_eq!(restored.hud, original.hud);
            assert_eq!(restored.name, original.name);
            assert_eq!(restored.quick, original.quick);
        }
    }

    #[test]
    fn only_changed_settings_travel() {
        let mut l = Loadout::new(LoadoutId::new("x").unwrap(), "Bridge", "sword");
        let p = payload(&l);
        assert_eq!(p["mods"], json!({}), "an untouched loadout carries no settings at all");
        let mut zoom = l.mods.effective(ModId::Zoom);
        zoom.insert("fov_divisor".into(), json!(6));
        l.mods.set(ModId::Zoom, zoom).unwrap();
        assert_eq!(payload(&l)["mods"], json!({ "zoom": { "fov_divisor": 6.0 } }));
        assert!(encode(&l).len() < 200, "{}", encode(&l));
    }

    #[test]
    fn a_damaged_code_is_refused_by_its_checksum() {
        let code = encode(&defaults::sword_pvp());
        let (rest, check) = code.rsplit_once('.').unwrap();
        let flipped = if check.starts_with('0') { format!("1{}", &check[1..]) } else { format!("0{}", &check[1..]) };
        assert_eq!(decode(&format!("{rest}.{flipped}")), Err(ShareError::Checksum));
    }

    #[test]
    fn structure_is_strict() {
        assert_eq!(decode("   "), Err(ShareError::Empty));
        assert_eq!(decode("hello"), Err(ShareError::Format));
        assert_eq!(decode("VOID1.!!!.00000000"), Err(ShareError::Format));
        assert_eq!(decode("VOID9.abc.00000000"), Err(ShareError::Version));
        assert_eq!(decode("VOID1.abc.xyz"), Err(ShareError::Format));
        // Valid deflate, valid checksum, but a JSON array rather than a loadout.
        assert_eq!(decode(&raw_code(b"[1,2,3]")), Err(ShareError::Invalid));
        // No name.
        assert_eq!(decode(&raw_code(br#"{"icon":"bed"}"#)), Err(ShareError::Invalid));
    }

    #[test]
    fn content_is_lenient() {
        let json = br#"{"name":"  From the future  ","icon":"bed","mc":"1.8.9","reg":999,
            "mods":{"zoom":{"fov_divisor":6,"warp_drive":true},"teleport":{"on":true},
                    "fps":{"show_label":"maybe"},"cps":{"window_ms":99999}},
            "hud":[{"id":"fps","anchor":"top-left","dx":4,"dy":4},{"id":"fps","anchor":"top","dx":0,"dy":0},
                   {"id":"radar","anchor":"top","dx":0,"dy":0}],
            "quick":["zoom","zoom","teleport","fullbright"]}"#;
        let shared = decode(&raw_code(json)).unwrap();
        // warp_drive, teleport, fps.show_label, cps.window_ms, the second fps, radar,
        // the repeated zoom pin, the teleport pin.
        assert_eq!(shared.dropped, 8);
        assert_eq!(shared.name, "From the future");
        let l = shared.into_loadout(LoadoutId::new("future").unwrap());
        l.validate().unwrap();
        assert_eq!(l.mods.effective(ModId::Zoom)["fov_divisor"], json!(6.0));
        assert_eq!(l.mods.effective(ModId::Cps)["window_ms"], json!(1000));
        assert_eq!(l.hud.len(), 1);
        assert_eq!(l.quick, Some(vec![ModId::Zoom, ModId::Fullbright]));
    }

    #[test]
    fn every_fixture_code_decodes_to_what_it_says() {
        let f = fixtures();
        for case in f["codes"].as_array().unwrap() {
            let code = case["code"].as_str().unwrap();
            let made_by = case["made_by"].as_str().unwrap();
            let shared = decode(code).unwrap_or_else(|e| panic!("{made_by}: {e:?}"));
            let expect = &case["expect"];
            assert_eq!(shared.name, expect["name"].as_str().unwrap(), "{made_by}");
            assert_eq!(shared.dropped, 0, "{made_by}");
            let l = shared.into_loadout(LoadoutId::new("fixture").unwrap());
            l.validate().unwrap();
            for (m, settings) in expect["mods"].as_object().unwrap() {
                let id = ModId::parse(m).unwrap();
                let effective = l.mods.effective(id);
                for (k, v) in settings.as_object().unwrap() {
                    // Numbers compare by value: Java writes `6`, Rust `6.0`, and both are 6.
                    let same = match (effective[k].as_f64(), v.as_f64()) {
                        (Some(a), Some(b)) => a == b,
                        _ => &effective[k] == v,
                    };
                    assert!(same, "{made_by}: {m}.{k} is {} not {v}", effective[k]);
                }
            }
            assert_eq!(l.hud.len(), expect["hud_items"].as_u64().unwrap() as usize, "{made_by}");
            if let Some(q) = expect.get("quick") {
                let want: Vec<&str> = q.as_array().unwrap().iter().map(|v| v.as_str().unwrap()).collect();
                let got: Vec<&str> = l.quick.unwrap_or_default().iter().map(|m| m.as_str()).collect();
                assert_eq!(got, want, "{made_by}");
            }
        }
    }

    #[test]
    fn the_rust_fixture_is_what_this_encoder_makes() {
        // Pins the fixture to the encoder: if the payload shape changes, the fixture (which
        // the Java and TypeScript suites decode) has to be regenerated deliberately. Compared
        // by checksum, which is over the JSON: the compressed bytes depend on which DEFLATE
        // backend flate2 was built with (feature unification picks it per build graph), and
        // any valid stream decodes the same.
        let f = fixtures();
        let rust = f["codes"].as_array().unwrap().iter().find(|c| c["made_by"] == "rust").unwrap();
        let fixture = rust["code"].as_str().unwrap();
        let ours = encode(&fixture_loadout());
        assert_eq!(
            fixture.rsplit_once('.').unwrap().1,
            ours.rsplit_once('.').unwrap().1,
            "the Rust payload changed; regenerate the rust fixture: {ours}"
        );
    }

    /// The loadout behind the `rust` fixture code.
    pub(crate) fn fixture_loadout() -> Loadout {
        let mut l = Loadout::new(LoadoutId::new("fixture").unwrap(), "Bridge practice", "bed");
        let mut zoom = l.mods.effective(ModId::Zoom);
        zoom.insert("fov_divisor".into(), json!(6));
        zoom.insert("key".into(), json!("V"));
        l.mods.set(ModId::Zoom, zoom).unwrap();
        let mut cps = l.mods.effective(ModId::Cps);
        cps.insert("on".into(), json!(true));
        cps.insert("mode".into(), json!("both"));
        l.mods.set(ModId::Cps, cps).unwrap();
        l.hud.push(HudItem::new(HudModId::Cps, Anchor::BottomLeft, 175.0, -108.0));
        l.hud.push(HudItem { scale: Some(1.5), ..HudItem::new(HudModId::Fps, Anchor::TopLeft, 23.0, 23.0) });
        l.quick = Some(vec![ModId::ToggleSprint, ModId::Zoom, ModId::Cps]);
        l
    }

    #[test]
    fn unique_ids_take_a_numeric_suffix() {
        let taken: Vec<LoadoutId> =
            ["sword-pvp", "sword-pvp-2"].iter().map(|s| LoadoutId::new(*s).unwrap()).collect();
        assert_eq!(unique_id("Sword PvP", &taken).as_str(), "sword-pvp-3");
        assert_eq!(unique_id("Bedwars", &taken).as_str(), "bedwars");
        assert_eq!(unique_id("!!!", &taken).as_str(), "loadout");
    }

    fn raw_code(json: &[u8]) -> String {
        let mut d = flate2::write::DeflateEncoder::new(Vec::new(), flate2::Compression::default());
        d.write_all(json).unwrap();
        format!("{SHARE_PREFIX}{}.{:08x}", B64.encode(d.finish().unwrap()), crc32fast::hash(json))
    }
}
