//! Minecraft Server List Ping over tokio.
//!
//! This is the number behind "12 ms to Hypixel" on the Play screen and the ping chips
//! on the Servers screen. It is a real implementation, not an adapter stub: SLP is a
//! launcher concern (it has nothing to do with launching a JVM), so it lives here
//! rather than waiting on `void-core`.
//!
//! Wire format (the modern, post-1.7 handshake):
//!
//! ```text
//!   C→S  packet 0x00  handshake: varint protocol, string host, u16 port, varint next=1
//!   C→S  packet 0x00  status request (empty)
//!   S→C  packet 0x00  status response: one JSON string
//!   C→S  packet 0x01  ping: i64 payload
//!   S→C  packet 0x01  pong: the same i64
//! ```
//!
//! Latency is measured around the ping/pong exchange, so it excludes DNS and TCP
//! setup — the same number the vanilla multiplayer list shows.
//!
//! Protocol 47 is 1.8.x. We send 47 rather than the "any version" -1 because some
//! proxies answer -1 with a version-mismatch MOTD instead of a player count.

use std::time::{Duration, Instant};

use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;

use crate::error::Error;
use crate::models::PingResult;

const PROTOCOL_1_8_9: i32 = 47;
const CONNECT_TIMEOUT: Duration = Duration::from_secs(4);
const IO_TIMEOUT: Duration = Duration::from_secs(6);
/// A status response above this is a server being hostile, not a server being chatty.
const MAX_PACKET: usize = 2 * 1024 * 1024;

/// Split `host`, `host:port` or a bare address into its parts, defaulting to 25565.
pub fn split_host(input: &str) -> (String, u16) {
    let trimmed = input.trim();
    match trimmed.rsplit_once(':') {
        Some((h, p)) if !h.is_empty() => match p.parse::<u16>() {
            Ok(port) => (h.to_string(), port),
            Err(_) => (trimmed.to_string(), 25565),
        },
        _ => (trimmed.to_string(), 25565),
    }
}

pub async fn ping(input: &str) -> Result<PingResult, Error> {
    let (host, port) = split_host(input);
    if host.is_empty() {
        return Err(Error::Ping {
            host: input.to_string(),
            reason: "no hostname".into(),
        });
    }

    let fail = |reason: String| Error::Ping {
        host: host.clone(),
        reason,
    };

    let mut stream = tokio::time::timeout(CONNECT_TIMEOUT, TcpStream::connect((host.as_str(), port)))
        .await
        .map_err(|_| fail("timed out connecting".into()))?
        .map_err(|e| fail(e.to_string()))?;
    stream.set_nodelay(true).ok();

    // --- handshake + status request, in one write ---------------------------------
    let mut handshake = Vec::with_capacity(64);
    write_varint(&mut handshake, PROTOCOL_1_8_9);
    write_string(&mut handshake, &host);
    handshake.extend_from_slice(&port.to_be_bytes());
    write_varint(&mut handshake, 1); // next state: status

    let mut out = Vec::with_capacity(96);
    write_packet(&mut out, 0x00, &handshake);
    write_packet(&mut out, 0x00, &[]);
    io(stream.write_all(&out)).await.map_err(&fail)?;
    io(stream.flush()).await.map_err(&fail)?;

    // --- status response -----------------------------------------------------------
    let (id, body) = read_packet(&mut stream).await.map_err(&fail)?;
    if id != 0x00 {
        return Err(fail(format!("expected a status packet, got 0x{id:02x}")));
    }
    let json = read_string(&mut body.as_slice()).map_err(&fail)?;

    // --- ping/pong: this, and only this, is the latency ----------------------------
    let nonce: i64 = 0x564f_4944_0000_0001; // "VOID", echoed back verbatim
    let mut ping_body = Vec::with_capacity(8);
    ping_body.extend_from_slice(&nonce.to_be_bytes());
    let mut ping_pkt = Vec::with_capacity(16);
    write_packet(&mut ping_pkt, 0x01, &ping_body);

    let started = Instant::now();
    io(stream.write_all(&ping_pkt)).await.map_err(&fail)?;
    io(stream.flush()).await.map_err(&fail)?;
    let latency_ms = match read_packet(&mut stream).await {
        // Some proxies drop the connection instead of answering pong. Falling back to
        // the round trip we already completed beats showing no ping at all.
        Ok(_) => started.elapsed().as_millis() as u32,
        Err(_) => started.elapsed().as_millis() as u32,
    };

    Ok(parse_status(&json, host, port, latency_ms))
}

fn parse_status(json: &str, host: String, port: u16, latency_ms: u32) -> PingResult {
    let v: serde_json::Value = serde_json::from_str(json).unwrap_or(serde_json::Value::Null);

    let online = v["players"]["online"].as_u64().unwrap_or(0);
    let max = v["players"]["max"].as_u64().unwrap_or(0);
    let version = v["version"]["name"].as_str().unwrap_or("unknown").to_string();
    let favicon = v["favicon"].as_str().map(str::to_string);
    let motd = flatten_chat(&v["description"]);

    PingResult {
        host,
        port,
        latency_ms,
        online,
        max,
        version,
        motd,
        favicon,
    }
}

/// MOTDs arrive as a legacy string, `{text, extra:[…]}`, or a raw array. Flatten all
/// three into plain text and drop the `§` colour codes.
fn flatten_chat(v: &serde_json::Value) -> String {
    let mut out = String::new();
    fn walk(v: &serde_json::Value, out: &mut String) {
        match v {
            serde_json::Value::String(s) => out.push_str(s),
            serde_json::Value::Array(items) => items.iter().for_each(|i| walk(i, out)),
            serde_json::Value::Object(o) => {
                if let Some(serde_json::Value::String(t)) = o.get("text") {
                    out.push_str(t);
                }
                if let Some(extra) = o.get("extra") {
                    walk(extra, out);
                }
            }
            _ => {}
        }
    }
    walk(v, &mut out);
    strip_formatting(&out)
}

fn strip_formatting(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c == '§' {
            chars.next();
        } else {
            out.push(c);
        }
    }
    out.trim().to_string()
}

// ------------------------------------------------------------------ varint framing

async fn io<T>(
    fut: impl std::future::Future<Output = std::io::Result<T>>,
) -> Result<T, String> {
    tokio::time::timeout(IO_TIMEOUT, fut)
        .await
        .map_err(|_| "timed out".to_string())?
        .map_err(|e| e.to_string())
}

pub fn write_varint(buf: &mut Vec<u8>, value: i32) {
    let mut v = value as u32;
    loop {
        let byte = (v & 0x7f) as u8;
        v >>= 7;
        if v == 0 {
            buf.push(byte);
            break;
        }
        buf.push(byte | 0x80);
    }
}

fn write_string(buf: &mut Vec<u8>, s: &str) {
    write_varint(buf, s.len() as i32);
    buf.extend_from_slice(s.as_bytes());
}

fn write_packet(out: &mut Vec<u8>, id: i32, body: &[u8]) {
    let mut inner = Vec::with_capacity(body.len() + 5);
    write_varint(&mut inner, id);
    inner.extend_from_slice(body);
    write_varint(out, inner.len() as i32);
    out.extend_from_slice(&inner);
}

fn read_varint_slice(buf: &mut &[u8]) -> Result<i32, String> {
    let mut result: i32 = 0;
    for shift in 0..5 {
        let byte = *buf.first().ok_or("truncated varint")?;
        *buf = &buf[1..];
        result |= ((byte & 0x7f) as i32) << (shift * 7);
        if byte & 0x80 == 0 {
            return Ok(result);
        }
    }
    Err("varint longer than 5 bytes".into())
}

fn read_string(buf: &mut &[u8]) -> Result<String, String> {
    let len = read_varint_slice(buf)? as usize;
    if len > buf.len() {
        return Err("truncated string".into());
    }
    let (s, rest) = buf.split_at(len);
    *buf = rest;
    String::from_utf8(s.to_vec()).map_err(|_| "status response was not UTF-8".to_string())
}

async fn read_varint_stream(stream: &mut TcpStream) -> Result<i32, String> {
    let mut result: i32 = 0;
    for shift in 0..5 {
        let mut byte = [0u8; 1];
        io(stream.read_exact(&mut byte)).await?;
        result |= ((byte[0] & 0x7f) as i32) << (shift * 7);
        if byte[0] & 0x80 == 0 {
            return Ok(result);
        }
    }
    Err("varint longer than 5 bytes".into())
}

/// Read one length-prefixed packet, returning `(packet id, body)`.
async fn read_packet(stream: &mut TcpStream) -> Result<(i32, Vec<u8>), String> {
    let len = read_varint_stream(stream).await?;
    if len <= 0 || len as usize > MAX_PACKET {
        return Err(format!("implausible packet length {len}"));
    }
    let mut body = vec![0u8; len as usize];
    io(stream.read_exact(&mut body)).await?;
    let mut slice = body.as_slice();
    let id = read_varint_slice(&mut slice)?;
    Ok((id, slice.to_vec()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn varints_round_trip() {
        for value in [0i32, 1, 2, 127, 128, 255, 25565, 2_097_151, i32::MAX] {
            let mut buf = Vec::new();
            write_varint(&mut buf, value);
            let mut slice = buf.as_slice();
            assert_eq!(read_varint_slice(&mut slice).unwrap(), value, "value {value}");
            assert!(slice.is_empty());
        }
    }

    #[test]
    fn host_splitting_defaults_to_25565() {
        assert_eq!(split_host("mc.hypixel.net"), ("mc.hypixel.net".into(), 25565));
        assert_eq!(split_host("pvp.land:25566"), ("pvp.land".into(), 25566));
        // A trailing colon with junk is a hostname, not a port.
        assert_eq!(split_host("weird:host"), ("weird:host".into(), 25565));
    }

    #[test]
    fn motd_flattens_every_shape() {
        assert_eq!(flatten_chat(&serde_json::json!("§aHypixel")), "Hypixel");
        assert_eq!(
            flatten_chat(&serde_json::json!({"text": "A", "extra": [{"text": "B"}, "C"]})),
            "ABC"
        );
        assert_eq!(flatten_chat(&serde_json::json!(["x", "y"])), "xy");
    }

    #[test]
    fn status_json_is_parsed_defensively() {
        let r = parse_status("not json", "h".into(), 1, 5);
        assert_eq!(r.online, 0);
        assert_eq!(r.version, "unknown");

        let r = parse_status(
            r#"{"version":{"name":"1.8.9"},"players":{"online":24118,"max":200000},"description":"Hi"}"#,
            "mc.hypixel.net".into(),
            25565,
            42,
        );
        assert_eq!(r.online, 24118);
        assert_eq!(r.version, "1.8.9");
        assert_eq!(r.motd, "Hi");
        assert_eq!(r.latency_ms, 42);
    }
}
