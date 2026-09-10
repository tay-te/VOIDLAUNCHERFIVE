//! `server_ping` · `servers_list` · `servers_favourite` · `servers_forget` · `servers_rename`
//!
//! The list is the launcher's own record of where the player has played — `void_loadout`'s
//! `ServerBook`, persisted as `servers.json` beside the loadouts. It used to be a `localStorage`
//! array in the webview, which is scored in `docs/launcher-roster.md` §2 for what it was: a
//! desktop app keeping its own data where a cleared cache takes it.
//!
//! Playtime is not written here. It arrives from the running game through `void_core::sync::pump`
//! — the mod reports which host it is on and how long it has been playing — so these commands
//! only carry what the *player* says: starred, renamed, forgotten.

use void_loadout::ServerRecord;

use crate::adapters::slp;
use crate::error::Error;
use crate::models::PingResult;
use crate::state::AppState;

/// Minecraft SLP handshake plus a ping/pong round trip. `host` may carry a port.
pub async fn ping(host: &str) -> Result<PingResult, Error> {
    slp::ping(host).await
}

/// Every server the player has played on or starred, most recently played first.
pub fn list(state: &AppState) -> Result<Vec<ServerRecord>, Error> {
    Ok(state.store.servers()?.list())
}

/// Star or unstar a host, creating the record if this is the first thing said about it.
pub fn set_favourite(
    state: &AppState,
    host: &str,
    favourite: bool,
) -> Result<Vec<ServerRecord>, Error> {
    state.store.update_servers(|book| {
        book.set_favourite(host, favourite);
        book.list()
    })
    .map_err(Error::from)
}

/// Give a host a label, or clear it back to the one the UI derives.
pub fn rename(state: &AppState, host: &str, name: Option<&str>) -> Result<Vec<ServerRecord>, Error> {
    state.store.update_servers(|book| {
        book.set_name(host, name);
        book.list()
    })
    .map_err(Error::from)
}

/// Drop a host entirely — the record and its playtime.
///
/// Playtime included, and that is the point rather than an oversight: "forget" that kept the
/// hours would be a button that hides a row and leaves the data, which is the shape of every
/// privacy control nobody trusts.
pub fn forget(state: &AppState, host: &str) -> Result<Vec<ServerRecord>, Error> {
    state.store.update_servers(|book| {
        book.forget(host);
        book.list()
    })
    .map_err(Error::from)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn an_unroutable_host_fails_with_a_sentence_a_player_can_read() {
        // 192.0.2.0/24 is TEST-NET-1: guaranteed not to route anywhere.
        let err = ping("192.0.2.1:25565").await.unwrap_err();
        let text = err.to_string();
        assert!(text.starts_with("Could not reach 192.0.2.1"), "{text}");
    }

    #[tokio::test]
    async fn an_empty_host_is_rejected_before_any_socket_is_opened() {
        assert!(ping("").await.is_err());
    }
}
