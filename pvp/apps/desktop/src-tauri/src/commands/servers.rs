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
///
/// **The sample is recorded here rather than by a second command**, because this is the one place
/// in the app a ping happens. A `servers_record_ping` would mean every caller had to remember to
/// use it, and the first one that forgot would leave a server with no baseline and no sign of why.
///
/// The write is cheap and usually skipped: `ServerBook::record_ping` keeps at most one sample per
/// five minutes, and refuses outright for a host that is not already in the book — so pinging
/// whatever is typed into the search field does not file it.
///
/// A failure to record is logged and swallowed. The ping succeeded; the player asked for a
/// latency, and losing one sample of a twenty-sample baseline is not worth failing that.
pub async fn ping(state: &AppState, host: &str) -> Result<PingResult, Error> {
    let result = slp::ping(host).await?;
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0);
    let ms = result.latency_ms.min(u32::from(u16::MAX)) as u16;
    if let Err(e) = state.store.update_servers(|book| book.record_ping(host, ms, now)) {
        tracing::warn!(%e, %host, "could not record a ping sample");
    }
    Ok(result)
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
    use crate::state::scratch_state;

    #[tokio::test]
    async fn an_unroutable_host_fails_with_a_sentence_a_player_can_read() {
        // 192.0.2.0/24 is TEST-NET-1: guaranteed not to route anywhere.
        let state = scratch_state();
        let err = ping(&state, "192.0.2.1:25565").await.unwrap_err();
        let text = err.to_string();
        assert!(text.starts_with("Could not reach 192.0.2.1"), "{text}");
    }

    #[tokio::test]
    async fn an_empty_host_is_rejected_before_any_socket_is_opened() {
        assert!(ping(&scratch_state(), "").await.is_err());
    }
}
