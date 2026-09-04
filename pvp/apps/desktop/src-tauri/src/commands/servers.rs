//! `server_ping`

use crate::adapters::slp;
use crate::error::Error;
use crate::models::PingResult;

/// Minecraft SLP handshake plus a ping/pong round trip. `host` may carry a port.
pub async fn ping(host: &str) -> Result<PingResult, Error> {
    slp::ping(host).await
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
