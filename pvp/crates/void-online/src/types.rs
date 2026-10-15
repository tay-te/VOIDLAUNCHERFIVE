//! The data the backend returns and the events this crate emits.
//!
//! Field names are exactly the contract's (`docs/online-contract.md`, Part 2), so the desktop
//! launcher can hand these straight to its webview without a mapping layer.

use serde::{Deserialize, Serialize};
use void_bridge::EquippedSet;

/// The signed-in player: their Minecraft identity as the backend confirmed it.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Player {
    /// Dashed lowercase Minecraft UUID. Also the Supabase auth user id.
    pub uuid: String,
    /// Minecraft name.
    pub name: String,
}

/// The signed-in player's cosmetics: what they own and what they wear (RPC `my_cosmetics`).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MyCosmetics {
    /// Item ids owned: live entitlements plus every active free catalog item.
    pub owned: Vec<String>,
    /// What they wear.
    pub equipped: EquippedSet,
}

/// A Stripe Checkout session (edge function `checkout`). Open `url` in the system browser.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CheckoutSession {
    /// The `https://checkout.stripe.com/...` page. Always `https`; the crate checks.
    pub url: String,
    /// Stripe's `cs_...` id.
    pub session_id: String,
}

/// The whole friends picture, as every `friends` action returns it.
#[derive(Debug, Clone, PartialEq, Eq, Default, Serialize, Deserialize)]
pub struct FriendsList {
    /// Accepted friends.
    #[serde(default)]
    pub friends: Vec<Friend>,
    /// Requests other players sent this one.
    #[serde(default)]
    pub incoming: Vec<FriendRequest>,
    /// Requests this player sent that are still pending.
    #[serde(default)]
    pub outgoing: Vec<FriendRequest>,
}

/// One accepted friend.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Friend {
    /// Dashed Minecraft UUID.
    pub uuid: String,
    /// Minecraft name.
    pub name: String,
    /// Whether their launcher is up and where they are playing.
    pub presence: FriendPresence,
}

/// A friend's presence, from the `presence` table (which only they can write).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FriendPresence {
    /// `"online"` while their launcher heartbeated in the last 90 s, else `"offline"`.
    pub state: String,
    /// The normalised server address, only while online and on a server.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub server: Option<String>,
    /// Unix ms of the last heartbeat; absent only for someone never seen.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub last_seen: Option<u64>,
}

/// A pending friend request, either direction.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FriendRequest {
    /// The other player's dashed UUID.
    pub uuid: String,
    /// The other player's name.
    pub name: String,
    /// Unix ms when it was sent.
    pub at: u64,
}

/// Where the online half stands, for the launcher's status chip (`online_status`).
///
/// Serialised with a `state` tag: `{"state":"online","uuid":"..","name":".."}`,
/// `{"state":"error","message":".."}`, `{"state":"signed_out"}`.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "state", rename_all = "snake_case")]
pub enum OnlineStatus {
    /// No backend configured in this build.
    Disabled,
    /// Configured, nobody signed in.
    SignedOut,
    /// Signing in.
    Connecting,
    /// Signed in.
    Online {
        /// Dashed UUID.
        uuid: String,
        /// Minecraft name.
        name: String,
    },
    /// Sign-in failed, or the session could not be renewed. The crate keeps retrying a
    /// renewal on its own; a failed [`crate::Online::sign_in`] needs the caller to try again.
    Error {
        /// A sentence for the player.
        message: String,
    },
}

/// Something that happened, pushed on [`crate::Online::subscribe`].
#[derive(Debug, Clone, PartialEq)]
pub enum OnlineEvent {
    /// [`crate::Online::status`] changed.
    Status(OnlineStatus),
    /// The signed-in player's owned items or equipped set changed (or were first fetched).
    SelfCosmetics(MyCosmetics),
    /// Other players' sets that are new to the cache or changed since it last saw them.
    Cosmetics(Vec<EquippedSet>),
    /// Another VOID player on the current server emoted.
    Emote {
        /// Their dashed UUID.
        uuid: String,
        /// The emote item id.
        id: String,
        /// Unix ms the backend stamped it with.
        at: u64,
    },
    /// The full set of other VOID launchers present on the current server's channel, after
    /// every presence change. Presence is client-claimed, so treat it as a hint: the crate
    /// uses it to prefetch cosmetics, and a lookup is what proves someone is a VOID player.
    ServerPlayers {
        /// The channel topic, `server:<32 hex>`.
        topic: String,
        /// Dashed UUIDs, sorted, never including the signed-in player. Empty when the
        /// channel is left.
        uuids: Vec<String>,
    },
    /// The friends list, after any friends call, a `friends` broadcast, or a poll that saw a
    /// change.
    Friends(FriendsList),
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn status_serialises_with_a_state_tag() {
        assert_eq!(serde_json::to_value(OnlineStatus::SignedOut).unwrap(), json!({"state":"signed_out"}));
        assert_eq!(
            serde_json::to_value(OnlineStatus::Online { uuid: "u".into(), name: "n".into() }).unwrap(),
            json!({"state":"online","uuid":"u","name":"n"})
        );
        assert_eq!(
            serde_json::to_value(OnlineStatus::Error { message: "m".into() }).unwrap(),
            json!({"state":"error","message":"m"})
        );
    }

    #[test]
    fn the_contract_friends_example_round_trips() {
        let v = json!({
            "friends":  [ { "uuid": "a", "name": "Moss",
                            "presence": { "state": "online", "server": "mc.hypixel.net", "last_seen": 1760527200000u64 } },
                          { "uuid": "b", "name": "Fern", "presence": { "state": "offline" } } ],
            "incoming": [ { "uuid": "c", "name": "marrow", "at": 1760527200000u64 } ],
            "outgoing": [ { "uuid": "d", "name": "tavi",   "at": 1760527200000u64 } ]
        });
        let list: FriendsList = serde_json::from_value(v.clone()).unwrap();
        assert_eq!(list.friends[0].presence.server.as_deref(), Some("mc.hypixel.net"));
        assert_eq!(serde_json::to_value(&list).unwrap(), v);
    }
}
