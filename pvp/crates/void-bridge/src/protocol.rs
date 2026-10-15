//! Serde types for every message in `schema/protocol.json`.
//!
//! Discriminated on `t`, which is the serde tag. Two rules from §7 shape these types and
//! must not be undone:
//!
//! 1. **Unknown `t` values and unknown fields are ignored, never an error.** Inbound
//!    messages therefore carry a [`JavaToRust::Unknown`] catch-all and no type here uses
//!    `deny_unknown_fields`. The two halves ship together but not atomically; a newer
//!    mod talking to an older launcher must degrade, not disconnect.
//! 2. **`v` is carried on `hello` and `init` only**, and is always [`PROTOCOL_VERSION`].
//!    A mismatch means the two halves were not shipped together, and the launcher
//!    refuses to launch.

use serde::{Deserialize, Serialize};

use crate::cosmetics::{EquippedSet, Slot};
use void_loadout::{GlobalPatch, GlobalSettings, HudItem, Loadout, LoadoutId, StatePatch};

/// The protocol version carried on `hello` and `init`. Bumped on any breaking change.
///
/// **2** since `init.loadouts` became whole loadouts rather than summaries: a v2 mod
/// talking to a v1 launcher would receive summaries, materialise every mod at its factory
/// default, and silently apply the wrong loadout on a switch. Refusing the pair is the
/// whole job of this constant.
pub const PROTOCOL_VERSION: u32 = 2;

/// A message the mod sends to the launcher.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "t", rename_all = "snake_case")]
pub enum JavaToRust {
    /// Handshake sent immediately on connect.
    Hello {
        /// Protocol version the mod speaks.
        v: u32,
        /// Minecraft version the mod is running inside.
        mc: String,
        /// Semantic version of the void-client mod build.
        #[serde(rename = "mod")]
        mod_version: String,
        /// The session token the launcher passed as `-Dvoid.token`.
        token: String,
    },

    /// Live loadout state delta, as a flat patch of dotted paths.
    State {
        /// Id of the loadout the patch applies to.
        loadout: LoadoutId,
        /// Changed fields.
        patch: StatePatch,
    },

    /// HUD layout after a drag in the HUD editor; the whole layout, not a delta.
    Hud {
        /// Id of the loadout whose layout this replaces.
        loadout: LoadoutId,
        /// The complete new layout.
        items: Vec<HudItem>,
    },

    /// A global setting written in game; a delta, never the whole object.
    ///
    /// The counterpart of [`JavaToRust::State`] for the non-loadout half of §8.3, and the
    /// reason a global written in game survives the process at all: without it `setGlobal`
    /// is in-process only and `hud_editor_grid` reverts at every launch.
    ///
    /// A delta because the mod's own `GlobalSettings` is a fixed five-field class, so a
    /// whole-object echo would erase every global the launcher had added that the mod
    /// does not model. `GlobalSettings::apply_patch` merges the named keys and leaves
    /// `extra` intact.
    Globals {
        /// The globals that changed, and only those.
        patch: GlobalPatch,
    },

    /// Periodic session telemetry summary; cumulative, not a delta.
    Session {
        /// Mean frames per second over the session so far.
        fps_avg: f64,
        /// Milliseconds elapsed since the game window opened.
        played_ms: u64,
        /// Mean ping over the session in milliseconds, once there has been a reading on a
        /// server. Optional on the wire, so a mod that predates it still parses.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        ping_avg: Option<f64>,
        /// Host the player is on, or null in the main menu or singleplayer.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        server: Option<String>,
        /// Loadout active at the time of the report.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        loadout: Option<LoadoutId>,
    },

    /// Server connect or disconnect notification.
    Server {
        /// Hostname without port; empty string when disconnecting.
        host: String,
        /// True on connect, false on disconnect.
        connected: bool,
        /// Port, when it is not the default 25565.
        #[serde(default, skip_serializing_if = "Option::is_none")]
        port: Option<u16>,
    },

    /// A global hotkey the player pressed in game (§6.3).
    ///
    /// A notification, not a request: Java has already cycled the loadout or opened the
    /// overlay by the time this arrives. The loadout the L key selected still travels in
    /// its own `state` message.
    Hotkey {
        /// Which hotkey fired.
        id: HotkeyId,
    },

    /// The UUIDs of players the mod currently sees, whose cosmetics it wants.
    ///
    /// Batched, debounced and cached on the mod side; the launcher answers with one or
    /// more `cosmetics` messages carrying the sets of the ones that are VOID players.
    /// UUIDs that are not VOID players simply never appear in a reply.
    CosmeticsQuery {
        /// Minecraft UUIDs, dashed or undashed.
        uuids: Vec<String>,
    },

    /// The local player played an emote. The launcher broadcasts it to the other VOID
    /// players on the same server; the mod has already started the animation locally.
    Emote {
        /// The emote's item id.
        id: String,
    },

    /// The player equipped (or, with `id: null`, took off) an item in the in-game
    /// wardrobe. Java has already applied it locally; the launcher persists it.
    Equip {
        /// The slot.
        slot: Slot,
        /// The item id, or `None` to empty the slot.
        #[serde(default)]
        id: Option<String>,
    },

    /// A `t` this build does not know. Kept rather than rejected, per §7.
    ///
    /// Never construct this to *send*: it would serialize as `{"t":"unknown"}`.
    #[serde(other)]
    Unknown,
}

/// The global hotkeys the mod reports, `protocol.json#/definitions/hotkey_id`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum HotkeyId {
    /// The L-key cycle of §6.3, already applied when the message is sent.
    #[serde(rename = "loadout.next")]
    LoadoutNext,
    /// The menu key opened or closed VoidMenuScreen.
    #[serde(rename = "overlay")]
    Overlay,
}

impl HotkeyId {
    /// The wire id.
    pub fn as_str(self) -> &'static str {
        match self {
            HotkeyId::LoadoutNext => "loadout.next",
            HotkeyId::Overlay => "overlay",
        }
    }
}

impl JavaToRust {
    /// The `t` discriminator of this message.
    pub fn tag(&self) -> &'static str {
        match self {
            JavaToRust::Hello { .. } => "hello",
            JavaToRust::State { .. } => "state",
            JavaToRust::Hud { .. } => "hud",
            JavaToRust::Globals { .. } => "globals",
            JavaToRust::Session { .. } => "session",
            JavaToRust::Server { .. } => "server",
            JavaToRust::Hotkey { .. } => "hotkey",
            JavaToRust::CosmeticsQuery { .. } => "cosmetics_query",
            JavaToRust::Emote { .. } => "emote",
            JavaToRust::Equip { .. } => "equip",
            JavaToRust::Unknown => "unknown",
        }
    }
}

/// A message the launcher sends to the mod.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "t", rename_all = "snake_case")]
pub enum RustToJava {
    /// Handshake reply carrying the entire world of persisted state.
    Init {
        /// Protocol version the launcher speaks.
        v: u32,
        /// The full active loadout to apply immediately.
        loadout: Box<Loadout>,
        /// Every loadout in the library, in full and in library order.
        ///
        /// Whole loadouts rather than summaries: a library is capped at 128 entries of
        /// roughly a kilobyte each and is sent once per launch, and in exchange the mod
        /// can hot-swap to any of them in under a frame with no round trip (§8.2), and
        /// the in-game Loadouts screen can list them without a bridge accessor.
        loadouts: Vec<Loadout>,
        /// Global settings that affect the game session.
        settings: GlobalSettings,
    },

    /// The loadout was switched from the launcher UI or the tray.
    Loadout {
        /// The full loadout to switch to.
        loadout: Box<Loadout>,
    },

    /// Global settings changed in the launcher; the whole object, not a delta.
    Settings {
        /// The complete new global settings.
        settings: GlobalSettings,
    },

    /// Cosmetics: the local player's own set and other VOID players' sets.
    ///
    /// `players` is a delta — the sets that are new or changed since the last message —
    /// never a full roster; the mod keeps its own cache. `self` is the local player's set
    /// whenever it is known, or `null` when the player has no online session.
    Cosmetics {
        /// The local player's equipped set.
        #[serde(rename = "self", default)]
        self_set: Option<EquippedSet>,
        /// Other VOID players' sets, new or changed.
        #[serde(default)]
        players: Vec<EquippedSet>,
    },

    /// Another VOID player on the same server played an emote.
    Emote {
        /// Who played it, dashed Minecraft UUID.
        uuid: String,
        /// The emote's item id.
        id: String,
        /// When it was played, Unix milliseconds (server clock).
        at: u64,
    },
}

impl RustToJava {
    /// The `t` discriminator of this message.
    pub fn tag(&self) -> &'static str {
        match self {
            RustToJava::Init { .. } => "init",
            RustToJava::Loadout { .. } => "loadout",
            RustToJava::Settings { .. } => "settings",
            RustToJava::Cosmetics { .. } => "cosmetics",
            RustToJava::Emote { .. } => "emote",
        }
    }
}

/// The state the launcher hands the mod in `init`.
#[derive(Debug, Clone, PartialEq)]
pub struct InitPayload {
    /// The active loadout.
    pub loadout: Loadout,
    /// The library, in full and in order.
    pub loadouts: Vec<Loadout>,
    /// Global settings.
    pub settings: GlobalSettings,
}

impl From<InitPayload> for RustToJava {
    fn from(p: InitPayload) -> Self {
        RustToJava::Init {
            v: PROTOCOL_VERSION,
            loadout: Box::new(p.loadout),
            loadouts: p.loadouts,
            settings: p.settings,
        }
    }
}

/// Where `void-bridge` gets the state it puts in `init`.
///
/// The launcher owns the store; the server only asks for a snapshot when a mod connects,
/// which is also what makes a reconnect after a launcher-side change deliver current
/// state rather than a stale copy captured at bind time.
pub trait InitSource: Send + Sync + 'static {
    /// A snapshot of the active loadout, the library and the global settings.
    fn init(&self) -> InitPayload;
}

impl<F> InitSource for F
where
    F: Fn() -> InitPayload + Send + Sync + 'static,
{
    fn init(&self) -> InitPayload {
        self()
    }
}

/// An [`InitSource`] holding one fixed snapshot; useful in tests and for `--offline`.
#[derive(Debug, Clone)]
pub struct StaticInit(pub InitPayload);

impl InitSource for StaticInit {
    fn init(&self) -> InitPayload {
        self.0.clone()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn unknown_tags_deserialize_to_unknown_rather_than_failing() {
        let m: JavaToRust = serde_json::from_value(json!({"t": "telemetry_v2", "x": 1})).unwrap();
        assert_eq!(m, JavaToRust::Unknown);
    }

    #[test]
    fn unknown_fields_are_ignored() {
        let m: JavaToRust = serde_json::from_value(json!({
            "t": "server", "host": "mc.hypixel.net", "connected": true, "region": "eu"
        }))
        .unwrap();
        assert!(matches!(m, JavaToRust::Server { connected: true, .. }));
    }

    #[test]
    fn cosmetics_messages_use_the_contract_field_names() {
        let q: JavaToRust =
            serde_json::from_value(json!({"t": "cosmetics_query", "uuids": ["a", "b"]})).unwrap();
        assert_eq!(q, JavaToRust::CosmeticsQuery { uuids: vec!["a".into(), "b".into()] });

        let off: JavaToRust =
            serde_json::from_value(json!({"t": "equip", "slot": "halo", "id": null})).unwrap();
        assert_eq!(off, JavaToRust::Equip { slot: Slot::Halo, id: None });

        let m = RustToJava::Cosmetics { self_set: Some(EquippedSet::empty("u")), players: vec![] };
        let v = serde_json::to_value(&m).unwrap();
        assert_eq!(v["t"], json!("cosmetics"));
        assert_eq!(v["self"]["uuid"], json!("u"));
        assert_eq!(v["players"], json!([]));

        let e = RustToJava::Emote { uuid: "u".into(), id: "emote-wave".into(), at: 1 };
        assert_eq!(serde_json::to_value(&e).unwrap()["t"], json!("emote"));
    }

    #[test]
    fn an_equip_for_a_slot_this_build_does_not_know_is_a_parse_error_not_a_panic() {
        assert!(serde_json::from_value::<JavaToRust>(json!({"t": "equip", "slot": "aura", "id": "x"})).is_err());
    }

    #[test]
    fn hello_keeps_the_reserved_word_field_name() {
        let m = JavaToRust::Hello {
            v: PROTOCOL_VERSION,
            mc: "1.8.9".into(),
            mod_version: "0.1.0".into(),
            token: "b7f1c0a94e2d43aa9c1e5f6b8d0a2c34".into(),
        };
        let v = serde_json::to_value(&m).unwrap();
        assert_eq!(v["mod"], json!("0.1.0"));
        assert_eq!(v["t"], json!("hello"));
    }
}
