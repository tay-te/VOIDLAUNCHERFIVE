//! Servers the player has actually played on, and how long for.
//!
//! `docs/launcher-roster.md` §4 puts presence second in the friends chain, behind identity, and
//! this is the half that needs no backend: **the game already tells the launcher where it is.**
//! The mod sends `server` on connect and `session` every sixty seconds over the bridge, so the
//! launcher has known which host a player is on, and for how long, since the bridge shipped — and
//! has been throwing it away at the end of every session.
//!
//! What this adds is the file that keeps it. A friends backend publishes exactly this record;
//! until one exists it is the Servers screen's own data, and it replaces a `localStorage`
//! favourites list that a desktop app had no business keeping in a webview.
//!
//! ## One list, not two
//!
//! A server is here because it was played on, or because it was starred, or both. The obvious
//! shape is two lists — favourites, and recents — and it is wrong for a reason worth stating: the
//! two would have to be reconciled every time a favourite was played, and every bug in that
//! reconciliation looks like a server appearing twice. `favourite` is a flag on one record.
//!
//! ## The host is the identity, and it has to be normalised to be one
//!
//! The host arrives from two places that disagree about spelling: a text field the player typed
//! into, and `MinecraftClient.serverAddress`, which is whatever the player typed into *the game*.
//! `MC.Hypixel.net`, `mc.hypixel.net` and `mc.hypixel.net:25565` are one server, and a record
//! keyed on the raw string would be three — each with a third of the playtime, which is the one
//! number this file exists to get right.
//!
//! So [`canonical_host`] lower-cases and drops an explicit default port, and nothing else. It
//! deliberately does **not** resolve DNS or follow SRV records: `mc.hypixel.net` and whatever it
//! resolves to today are the same server to a player and different strings to a resolver, and a
//! launcher that grouped by resolved address would merge every server behind one proxy.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

/// Minecraft's default port, which an address may carry explicitly and mean nothing by.
const DEFAULT_PORT: u16 = 25565;

/// The shortest gap between two kept ping samples.
///
/// The Servers screen sweeps every sixty seconds and the Play screen every thirty, so without a
/// limit the file would be rewritten twice a minute for the life of an open launcher — and the
/// twenty samples kept would all be from the last ten minutes, which is a reading about *now*
/// dressed up as a baseline. Five minutes makes twenty samples span a fortnight of ordinary use.
const PING_INTERVAL_MS: u64 = 5 * 60 * 1000;

/// How many samples make the baseline.
const PING_SAMPLES: usize = 20;

/// The most servers kept. Beyond it the least recently played is dropped.
///
/// A cap rather than a trust: the record is written from whatever host the game reported, so a
/// player hopping proxies could otherwise grow the file without limit. Favourites are never
/// dropped — starring one is the player saying it is not a recent, it is a fixture.
const MAX_SERVERS: usize = 64;

/// One server, as the launcher knows it.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ServerRecord {
    /// Canonical host — the key. See [`canonical_host`].
    pub host: String,
    /// A label the player gave it, or `None` to let the UI derive one from the host.
    ///
    /// `None` rather than a derived string on disk, because the derivation is a display rule
    /// (`mc.hypixel.net` reads as `Hypixel`) and a display rule that has been persisted is a
    /// display rule that cannot be improved without a migration.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    /// Whether the player starred it.
    #[serde(default)]
    pub favourite: bool,
    /// Unix milliseconds of the last session that touched it. `0` when never played.
    #[serde(default)]
    pub last_played_ms: u64,
    /// Total milliseconds played, across every session.
    #[serde(default)]
    pub played_ms: u64,
    /// How many times the game has connected.
    #[serde(default)]
    pub joins: u32,
    /// Recent round-trip times in milliseconds, oldest first — the baseline, not a graph.
    ///
    /// **Bounded and rate-limited, and both are the design rather than thrift.** The question a
    /// player has is "is this server usually this bad, or is it me right now", and answering it
    /// needs samples spread over *days*, not two hundred from one evening. So
    /// [`ServerBook::record_ping`] takes at most one every [`PING_INTERVAL_MS`], and twenty of
    /// those is a fortnight of casual use.
    ///
    /// **The summary lives with the thing that draws it.** There is no `typical_ping` here: only
    /// the launcher's UI reads these, so the median is derived once, in `stores/servers.ts`. A
    /// copy on this side would be a second implementation of one rule, kept honest by nothing.
    ///
    /// **It is deliberately not a jitter reading.** Jitter is a sub-second phenomenon and these
    /// samples are minutes apart; what they measure is route stability across sessions. The
    /// in-game `ping.show_jitter` does the other one, from the 20 Hz tick stream, which is the
    /// only place it can honestly be done.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub pings: Vec<u16>,
    /// When the last sample was taken, for the rate limit.
    #[serde(default)]
    pub last_ping_ms: u64,
}

impl ServerRecord {
    /// A record for a host nobody has played on yet — what starring an address produces.
    pub fn new(host: &str) -> Self {
        Self {
            host: canonical_host(host),
            name: None,
            favourite: false,
            last_played_ms: 0,
            played_ms: 0,
            joins: 0,
            pings: Vec::new(),
            last_ping_ms: 0,
        }
    }


    /// Whether this record is worth keeping when the list is trimmed.
    fn pinned(&self) -> bool {
        self.favourite
    }
}

/// The host as a key: lower-cased, with an explicit `:25565` dropped.
///
/// **Only a trailing numeric segment is a port.** An IPv6 literal is all colons, and cutting one
/// at the last colon produces a different address that still parses — the worst kind of wrong,
/// because it would silently key a record to a machine nobody asked for.
///
/// A non-default port is kept, because `mc.example.com` and `mc.example.com:25566` really are two
/// servers and a player who typed the port meant it.
pub fn canonical_host(host: &str) -> String {
    let host = host.trim().to_ascii_lowercase();
    match host.rsplit_once(':') {
        Some((left, right))
            if !left.contains(':') && right.parse::<u16>() == Ok(DEFAULT_PORT) =>
        {
            left.to_string()
        }
        _ => host,
    }
}

/// The server list, ordered most recently played first.
///
/// A map on disk and a vector in the API: the file is keyed so a hand-edit cannot produce two
/// records for one host, and the order callers want is a *derived* one that would be a lie if it
/// were persisted — a file written in play order goes stale the moment anything is played.
#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct ServerBook {
    #[serde(default)]
    servers: BTreeMap<String, ServerRecord>,
}

impl ServerBook {
    /// Every record, most recently played first, then favourites, then the rest by host.
    ///
    /// Favourites float above never-played entries rather than sinking to the bottom with them:
    /// a starred server the player has not touched this install is still the one they are looking
    /// for, which is what starring it said.
    pub fn list(&self) -> Vec<ServerRecord> {
        let mut out: Vec<ServerRecord> = self.servers.values().cloned().collect();
        out.sort_by(|a, b| {
            b.last_played_ms
                .cmp(&a.last_played_ms)
                .then(b.favourite.cmp(&a.favourite))
                .then(a.host.cmp(&b.host))
        });
        out
    }

    /// The record for a host, if there is one.
    pub fn get(&self, host: &str) -> Option<&ServerRecord> {
        self.servers.get(&canonical_host(host))
    }

    /// Star or unstar a host, creating the record if this is the first thing said about it.
    pub fn set_favourite(&mut self, host: &str, favourite: bool) {
        let key = canonical_host(host);
        self.servers.entry(key.clone()).or_insert_with(|| ServerRecord::new(&key)).favourite =
            favourite;
        self.trim();
    }

    /// Give a host a label, or clear it back to the derived one.
    pub fn set_name(&mut self, host: &str, name: Option<&str>) {
        let key = canonical_host(host);
        let entry = self.servers.entry(key.clone()).or_insert_with(|| ServerRecord::new(&key));
        entry.name = name.map(str::trim).filter(|n| !n.is_empty()).map(str::to_string);
        self.trim();
    }

    /// Drop a host entirely — the record and its playtime.
    pub fn forget(&mut self, host: &str) {
        self.servers.remove(&canonical_host(host));
    }

    /// The game connected to a host. Moves `joins` and `last_played_ms`, never `played_ms`.
    ///
    /// Split from [`Self::record_session`] because a connection and a minute of play are two
    /// different facts and arrive as two different messages. Merging them would mean either
    /// counting a join for every sixty-second telemetry report, or not counting the join until
    /// the first one arrives — and a player who alt-F4s in the queue has still joined.
    pub fn record_join(&mut self, host: &str, now_ms: u64) {
        let key = canonical_host(host);
        let entry = self.servers.entry(key.clone()).or_insert_with(|| ServerRecord::new(&key));
        entry.joins = entry.joins.saturating_add(1);
        entry.last_played_ms = now_ms;
        self.trim();
    }

    /// Fold a slice of played time into a host's total.
    ///
    /// `played_ms_delta` is time since the previous report, not the cumulative figure the mod
    /// sends — the same contract `Store::record_session` states for loadouts, and for the same
    /// reason: the mod's `played_ms` counts from when the *game window* opened, so adding it
    /// whole once a minute would count the first minute sixty times by the end of an hour.
    ///
    /// Does nothing for a host with no record, which is the honest response to telemetry that
    /// arrived after the server was forgotten.
    pub fn record_session(&mut self, host: &str, played_ms_delta: u64, now_ms: u64) {
        if let Some(entry) = self.servers.get_mut(&canonical_host(host)) {
            entry.played_ms = entry.played_ms.saturating_add(played_ms_delta);
            entry.last_played_ms = now_ms;
        }
    }

    /// Take a round-trip sample, if one is due. Returns whether it was kept.
    ///
    /// Only for a host already in the book: an unsolicited ping to somewhere the player has
    /// neither played nor starred should not create a record, or the Servers screen's search
    /// field would file every address anybody ever typed.
    pub fn record_ping(&mut self, host: &str, ms: u16, now_ms: u64) -> bool {
        let Some(entry) = self.servers.get_mut(&canonical_host(host)) else {
            return false;
        };
        if entry.last_ping_ms != 0 && now_ms.saturating_sub(entry.last_ping_ms) < PING_INTERVAL_MS {
            return false;
        }
        entry.last_ping_ms = now_ms;
        entry.pings.push(ms);
        if entry.pings.len() > PING_SAMPLES {
            let excess = entry.pings.len() - PING_SAMPLES;
            entry.pings.drain(0..excess);
        }
        true
    }

    /// Keeps the book bounded, dropping the least recently played unstarred records first.
    fn trim(&mut self) {
        if self.servers.len() <= MAX_SERVERS {
            return;
        }
        let mut droppable: Vec<(u64, String)> = self
            .servers
            .values()
            .filter(|r| !r.pinned())
            .map(|r| (r.last_played_ms, r.host.clone()))
            .collect();
        droppable.sort();
        let excess = self.servers.len() - MAX_SERVERS;
        for (_, host) in droppable.into_iter().take(excess) {
            self.servers.remove(&host);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn one_server_typed_three_ways_is_one_record() {
        // The failure this prevents is the only number the file exists for: playtime split three
        // ways, each a third of the truth. The two spellings arrive from two places that
        // genuinely disagree — a launcher text field and `MinecraftClient.serverAddress`, which
        // is whatever the player typed into the game.
        let mut book = ServerBook::default();
        book.record_join("MC.Hypixel.net", 1_000);
        book.record_join("mc.hypixel.net:25565", 2_000);
        book.record_join("  mc.hypixel.net  ", 3_000);
        assert_eq!(book.list().len(), 1);
        assert_eq!(book.get("mc.hypixel.net").unwrap().joins, 3);
    }

    #[test]
    fn a_non_default_port_is_a_different_server_and_an_ipv6_literal_survives() {
        assert_eq!(canonical_host("mc.example.com:25565"), "mc.example.com");
        // Kept: a player who typed a port meant it, and the two really are different servers.
        assert_eq!(canonical_host("mc.example.com:25566"), "mc.example.com:25566");
        // An IPv6 literal is all colons. Cutting at the last one produces a *different address
        // that still parses*, which is the worst kind of wrong — it would key a record to a
        // machine nobody asked for, silently.
        assert_eq!(canonical_host("::1"), "::1");
        assert_eq!(canonical_host("[2001:db8::1]"), "[2001:db8::1]");
    }

    #[test]
    fn playtime_accumulates_by_delta_and_a_join_is_not_a_minute() {
        // Two facts, two messages. A join with no play is a player who quit in the queue, and it
        // is still a join; a minute of play is not a second join.
        let mut book = ServerBook::default();
        book.record_join("mc.hypixel.net", 1_000);
        book.record_session("mc.hypixel.net", 60_000, 61_000);
        book.record_session("mc.hypixel.net", 60_000, 121_000);
        let entry = book.get("mc.hypixel.net").unwrap();
        assert_eq!(entry.joins, 1);
        assert_eq!(entry.played_ms, 120_000);
        assert_eq!(entry.last_played_ms, 121_000);
    }

    #[test]
    fn telemetry_for_a_forgotten_server_is_dropped_rather_than_resurrecting_it() {
        // A session report can outlive the record: the player forgets a server while the game is
        // still running. Re-creating it from telemetry would make "Forget" a button that does
        // not work for another minute.
        let mut book = ServerBook::default();
        book.record_join("mc.hypixel.net", 1_000);
        book.forget("mc.hypixel.net");
        book.record_session("mc.hypixel.net", 60_000, 61_000);
        assert!(book.get("mc.hypixel.net").is_none());
    }

    #[test]
    fn starring_a_server_nobody_has_played_keeps_it_findable() {
        let mut book = ServerBook::default();
        book.set_favourite("na.minemen.club", true);
        let entry = book.get("na.minemen.club").unwrap();
        assert!(entry.favourite);
        assert_eq!(entry.joins, 0);
        assert_eq!(entry.last_played_ms, 0);

        // And it sorts above an unstarred server nobody has played, because starring it said it
        // was not a recent.
        book.set_name("play.cubecraft.net", Some("CubeCraft"));
        let listed = book.list();
        assert_eq!(listed.first().unwrap().host, "na.minemen.club");
    }

    #[test]
    fn the_book_is_bounded_and_never_drops_a_favourite() {
        let mut book = ServerBook::default();
        book.set_favourite("kept.example.com", true);
        for i in 0..(MAX_SERVERS as u64 + 20) {
            book.record_join(&format!("host{i}.example.com"), 1_000 + i);
        }
        assert!(book.list().len() <= MAX_SERVERS + 1);
        assert!(book.get("kept.example.com").is_some(), "a favourite is a fixture, not a recent");
        // The oldest unstarred ones went, not the newest.
        assert!(book.get("host0.example.com").is_none());
        assert!(book.get(&format!("host{}.example.com", MAX_SERVERS + 19)).is_some());
    }

    #[test]
    fn a_ping_baseline_is_rate_limited_so_it_spans_days_rather_than_minutes() {
        // Without the limit, twenty samples would all come from the ten minutes the launcher
        // happened to be open — a reading about *now*, presented as "usually".
        let mut book = ServerBook::default();
        book.set_favourite("mc.hypixel.net", true);
        assert!(book.record_ping("mc.hypixel.net", 42, 1_000));
        assert!(!book.record_ping("mc.hypixel.net", 44, 60_000), "a minute later is not due");
        assert!(book.record_ping("mc.hypixel.net", 48, 1_000 + PING_INTERVAL_MS));
        assert_eq!(book.get("mc.hypixel.net").unwrap().pings, vec![42, 48]);
    }

    #[test]
    fn a_ping_never_creates_a_record_for_a_server_nobody_asked_about() {
        // The Servers screen pings what you type into its search field. Filing every address
        // anybody ever typed would turn a list of your servers into a list of your typos.
        let mut book = ServerBook::default();
        assert!(!book.record_ping("some.random.host", 42, 1_000));
        assert!(book.get("some.random.host").is_none());
    }

    #[test]
    fn a_book_written_before_a_field_existed_still_loads() {
        // Every field but `host` is `#[serde(default)]`, which is what lets this file grow. The
        // §16.3 per-server default loadout is the next field to go in, and it must not orphan
        // anybody's playtime when it does.
        let book: ServerBook =
            serde_json::from_str(r#"{"servers":{"mc.hypixel.net":{"host":"mc.hypixel.net"}}}"#)
                .unwrap();
        let entry = book.get("mc.hypixel.net").unwrap();
        assert_eq!(entry.played_ms, 0);
        assert!(!entry.favourite);
    }
}
