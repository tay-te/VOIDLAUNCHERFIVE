//! The on-disk loadout library: `~/.void-pvp/`.
//!
//! ```text
//! ~/.void-pvp/
//!   loadouts/<id>.json   one file per loadout, exactly `schema/loadout.json`
//!   active.json          which loadout is active, and the library order
//!   settings.json        global settings, `protocol.json#/definitions/global_settings`
//! ```
//!
//! Every write is atomic: a sibling temporary file is written and fsynced, then renamed
//! over the target. A crash mid-write leaves the previous file intact, never a truncated
//! one — this store is the record of the player's whole configuration and the mod keeps
//! no config of its own (§6.1).
//!
//! `VOID_PVP_HOME` overrides the root, which is what the tests use and what lets a
//! portable install keep its data next to the binary.

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::loadout::{Loadout, LoadoutId, LoadoutSummary};
use crate::servers::ServerBook;
use crate::settings::GlobalSettings;
use crate::{defaults, Error};

/// Environment variable that overrides the store root.
pub const HOME_ENV: &str = "VOID_PVP_HOME";

/// Name of the store directory under `$HOME`.
pub const HOME_DIR_NAME: &str = ".void-pvp";

/// `active.json`: the active loadout plus the library order.
///
/// Order is not part of `schema/loadout.json` — it is a launcher concern — but it has to
/// be stable, because it is the order `init.loadouts` is sent in and therefore the order
/// the in-game **L** key cycles through (§6.3).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ActiveState {
    /// Id of the active loadout.
    pub active: LoadoutId,
    /// Library order, back to front on the Loadouts frame.
    #[serde(default)]
    pub order: Vec<LoadoutId>,
}

/// The loadout library on disk.
#[derive(Debug, Clone)]
pub struct Store {
    root: PathBuf,
}

impl Store {
    /// The store root: `$VOID_PVP_HOME`, or `~/.void-pvp`.
    pub fn default_root() -> Result<PathBuf, Error> {
        if let Some(dir) = std::env::var_os(HOME_ENV).filter(|s| !s.is_empty()) {
            return Ok(PathBuf::from(dir));
        }
        dirs::home_dir().map(|h| h.join(HOME_DIR_NAME)).ok_or(Error::NoHome)
    }

    /// Opens the store at [`Store::default_root`], creating nothing yet.
    pub fn open() -> Result<Self, Error> {
        Ok(Self::at(Self::default_root()?))
    }

    /// Opens the store at an explicit root.
    pub fn at(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into() }
    }

    /// The store root.
    pub fn root(&self) -> &Path {
        &self.root
    }

    /// Directory holding one JSON file per loadout.
    pub fn loadouts_dir(&self) -> PathBuf {
        self.root.join("loadouts")
    }

    fn loadout_path(&self, id: &LoadoutId) -> PathBuf {
        self.loadouts_dir().join(format!("{id}.json"))
    }

    fn active_path(&self) -> PathBuf {
        self.root.join("active.json")
    }

    fn settings_path(&self) -> PathBuf {
        self.root.join("settings.json")
    }

    fn servers_path(&self) -> PathBuf {
        self.root.join("servers.json")
    }

    /// Creates the store if it is not there yet, seeding the three default loadouts.
    ///
    /// Returns `true` when this call did the seeding, i.e. it was a first run.
    pub fn init(&self) -> Result<bool, Error> {
        create_dir_all(&self.loadouts_dir())?;
        let mut seeded = false;

        if !self.active_path().exists() || self.list_ids()?.is_empty() {
            let library = defaults::default_library();
            for loadout in &library {
                if !self.loadout_path(&loadout.id).exists() {
                    self.save(loadout)?;
                }
            }
            let order: Vec<LoadoutId> = library.iter().map(|l| l.id.clone()).collect();
            let active = order.first().cloned().expect("the default library is never empty");
            self.write_active(&ActiveState { active, order })?;
            seeded = true;
        }
        if !self.settings_path().exists() {
            self.save_settings(&GlobalSettings::factory())?;
        }
        Ok(seeded)
    }

    /// Ids of every loadout on disk, unordered.
    pub fn list_ids(&self) -> Result<Vec<LoadoutId>, Error> {
        let dir = self.loadouts_dir();
        if !dir.exists() {
            return Ok(Vec::new());
        }
        let mut ids = Vec::new();
        for entry in read_dir(&dir)? {
            let entry = entry.map_err(|e| Error::Io { path: dir.clone(), source: e })?;
            let path = entry.path();
            if path.extension().is_some_and(|e| e == "json") {
                if let Some(id) =
                    path.file_stem().and_then(|s| s.to_str()).and_then(LoadoutId::new)
                {
                    ids.push(id);
                }
            }
        }
        ids.sort();
        Ok(ids)
    }

    /// Every loadout, in library order. Ids in `active.json` come first, in order; any
    /// file the order does not mention is appended, so a loadout dropped into the
    /// directory by hand still shows up.
    pub fn list(&self) -> Result<Vec<Loadout>, Error> {
        let on_disk = self.list_ids()?;
        let order = self.active_state().map(|a| a.order).unwrap_or_default();
        let mut ordered: Vec<LoadoutId> =
            order.into_iter().filter(|id| on_disk.contains(id)).collect();
        for id in on_disk {
            if !ordered.contains(&id) {
                ordered.push(id);
            }
        }
        ordered.into_iter().map(|id| self.load(&id)).collect()
    }

    /// Every loadout in library order, reduced to `init.loadouts` summaries.
    pub fn summaries(&self) -> Result<Vec<LoadoutSummary>, Error> {
        Ok(self.list()?.iter().map(Loadout::summary).collect())
    }

    /// Reads one loadout, validating it on the way in.
    pub fn load(&self, id: &LoadoutId) -> Result<Loadout, Error> {
        let path = self.loadout_path(id);
        if !path.exists() {
            return Err(Error::NotFound(id.clone()));
        }
        let loadout: Loadout = read_loadout_json(&path)?;
        loadout.validate()?;
        if loadout.id != *id {
            return Err(Error::Invalid(format!(
                "{}: file name says `{id}` but the loadout says `{}`",
                path.display(),
                loadout.id
            )));
        }
        Ok(loadout)
    }

    /// Writes one loadout, validating it before it touches the disk.
    pub fn save(&self, loadout: &Loadout) -> Result<(), Error> {
        loadout.validate()?;
        create_dir_all(&self.loadouts_dir())?;
        write_json_atomic(&self.loadout_path(&loadout.id), loadout)
    }

    /// Adds a loadout that is not in the library yet and appends it to the order.
    pub fn create(&self, loadout: &Loadout) -> Result<(), Error> {
        if self.loadout_path(&loadout.id).exists() {
            return Err(Error::AlreadyExists(loadout.id.clone()));
        }
        self.save(loadout)?;
        let mut state = self.active_state()?;
        if !state.order.contains(&loadout.id) {
            state.order.push(loadout.id.clone());
        }
        self.write_active(&state)
    }

    /// Removes a loadout. If it was active, the first remaining loadout takes over.
    pub fn delete(&self, id: &LoadoutId) -> Result<(), Error> {
        let path = self.loadout_path(id);
        if !path.exists() {
            return Err(Error::NotFound(id.clone()));
        }
        fs::remove_file(&path).map_err(|e| Error::Io { path: path.clone(), source: e })?;
        let mut state = self.active_state()?;
        state.order.retain(|o| o != id);
        if state.active == *id {
            let remaining = self.list_ids()?;
            state.active = state
                .order
                .first()
                .cloned()
                .or_else(|| remaining.first().cloned())
                .ok_or_else(|| Error::Invalid("the library cannot be emptied".into()))?;
        }
        self.write_active(&state)
    }

    /// The active loadout and the library order.
    pub fn active_state(&self) -> Result<ActiveState, Error> {
        let path = self.active_path();
        if path.exists() {
            let state: ActiveState = read_json(&path)?;
            return Ok(state);
        }
        // No active.json yet: fall back to the first loadout on disk.
        let ids = self.list_ids()?;
        let active = ids.first().cloned().ok_or_else(|| {
            Error::Invalid("no loadouts on disk; call Store::init first".into())
        })?;
        Ok(ActiveState { active, order: ids })
    }

    fn write_active(&self, state: &ActiveState) -> Result<(), Error> {
        create_dir_all(&self.root)?;
        write_json_atomic(&self.active_path(), state)
    }

    /// Id of the active loadout.
    pub fn active_id(&self) -> Result<LoadoutId, Error> {
        Ok(self.active_state()?.active)
    }

    /// The active loadout itself.
    pub fn active(&self) -> Result<Loadout, Error> {
        self.load(&self.active_id()?)
    }

    /// Switches the active loadout. The id must already be in the library.
    pub fn set_active(&self, id: &LoadoutId) -> Result<(), Error> {
        if !self.loadout_path(id).exists() {
            return Err(Error::NotFound(id.clone()));
        }
        let mut state = self.active_state()?;
        state.active = id.clone();
        if !state.order.contains(id) {
            state.order.push(id.clone());
        }
        self.write_active(&state)
    }

    /// The id after `from` in library order, wrapping — what the in-game **L** key does.
    pub fn next_after(&self, from: &LoadoutId) -> Result<LoadoutId, Error> {
        let ids: Vec<LoadoutId> = self.list()?.into_iter().map(|l| l.id).collect();
        if ids.is_empty() {
            return Err(Error::NotFound(from.clone()));
        }
        let idx = ids.iter().position(|i| i == from).unwrap_or(0);
        Ok(ids[(idx + 1) % ids.len()].clone())
    }

    /// Global settings, or the factory settings when the file is not there yet.
    pub fn settings(&self) -> Result<GlobalSettings, Error> {
        let path = self.settings_path();
        if !path.exists() {
            return Ok(GlobalSettings::factory());
        }
        read_json(&path)
    }

    /// Writes global settings.
    pub fn save_settings(&self, settings: &GlobalSettings) -> Result<(), Error> {
        create_dir_all(&self.root)?;
        write_json_atomic(&self.settings_path(), settings)
    }

    /// Folds a `session` telemetry report into a loadout's stats and saves it.
    ///
    /// `played_ms_delta` is time since the previous report, not the cumulative
    /// `played_ms` the mod sends; the caller keeps the running total per connection.
    pub fn record_session(
        &self,
        id: &LoadoutId,
        played_ms_delta: u64,
        fps_avg: f64,
    ) -> Result<(), Error> {
        let mut loadout = self.load(id)?;
        let mut stats = loadout.stats.unwrap_or_default();
        stats.accumulate(played_ms_delta, fps_avg);
        loadout.stats = Some(stats);
        self.save(&loadout)
    }

    /// The servers the player has played on — `servers.json`.
    ///
    /// An absent file is an empty book, not an error: a fresh install has played nowhere, and
    /// that is a state rather than a failure. A *corrupt* one is still an error, because
    /// silently replacing a file that has content with an empty one is how a player's history
    /// disappears without anybody being told.
    pub fn servers(&self) -> Result<ServerBook, Error> {
        let path = self.servers_path();
        if !path.exists() {
            return Ok(ServerBook::default());
        }
        read_json(&path)
    }

    /// Writes the server book back, atomically like everything else here.
    pub fn save_servers(&self, book: &ServerBook) -> Result<(), Error> {
        create_dir_all(&self.root)?;
        write_json_atomic(&self.servers_path(), book)
    }

    /// Read, change, write — the shape every caller wants and none should have to spell.
    ///
    /// Deliberately not a held-open handle. The book is small, the writes are rare (a join, a
    /// telemetry report once a minute, a star), and a launcher that kept it in memory would have
    /// to decide what happens when the game and the UI both change it. Read-modify-write against
    /// an atomically-written file has one answer: last writer wins, and neither can see a torn
    /// file.
    pub fn update_servers<T>(
        &self,
        change: impl FnOnce(&mut ServerBook) -> T,
    ) -> Result<T, Error> {
        let mut book = self.servers()?;
        let out = change(&mut book);
        self.save_servers(&book)?;
        Ok(out)
    }
}

// ---------------------------------------------------------------------------
// file helpers
// ---------------------------------------------------------------------------

fn create_dir_all(path: &Path) -> Result<(), Error> {
    fs::create_dir_all(path).map_err(|e| Error::Io { path: path.to_path_buf(), source: e })
}

fn read_dir(path: &Path) -> Result<fs::ReadDir, Error> {
    fs::read_dir(path).map_err(|e| Error::Io { path: path.to_path_buf(), source: e })
}

fn read_json<T: serde::de::DeserializeOwned>(path: &Path) -> Result<T, Error> {
    let text =
        fs::read_to_string(path).map_err(|e| Error::Io { path: path.to_path_buf(), source: e })?;
    serde_json::from_str(&text).map_err(|e| Error::Json { path: path.to_path_buf(), source: e })
}

/// Settings that used to exist and no longer do, as `(mod id, key)`.
///
/// Every `*Settings` struct is `deny_unknown_fields`, which mirrors the schema's
/// `additionalProperties: false` and is worth keeping — an unknown key really is invalid.
/// But it also means that **deleting a setting orphans every loadout already on disk that
/// carries it**: the file stops deserialising, `Store::load` returns `Error::Json`, and the
/// player's loadout is simply gone. That is a worse outcome than the ornamental setting was.
///
/// So removal is a two-part change: take the key out of the schema and the types, and add it
/// here. A stale key is dropped on the way in and never written back, which makes the next
/// save the migration. Entries can be retired once no on-disk file can plausibly carry them.
const REMOVED_SETTINGS: &[(&str, &str)] = &[
    // `toggle_sprint.show_status` — a status line that was never drawn. It is coming back as
    // its own placeable HUD mod rather than as a setting on a gameplay one.
    ("toggle_sprint", "show_status"),
    // `toggle_sprint.sneak_too` — sneak is `toggle_sneak` now, its own mod with its own bind
    // (`docs/mod-roster.md` §3.2 #3), so the boolean was removed rather than left as a second
    // owner of the same latch. This entry is what makes that removal survivable: the key was in
    // the shipped registry defaults, so it is in essentially every loadout ever written, and
    // without it every one of those files would fail to deserialise on the next launch.
    ("toggle_sprint", "sneak_too"),
    // `toggle_sprint.mode` — removed because it was provably a no-op, not because it was
    // deprecated. `ClientPlayerEntity.tickMovement` re-evaluates sprint as a *level* every tick
    // and `KeyBinding.setKeyPressed(code, true)` writes the same `pressed` field that level
    // reads, so the latch was indistinguishable from a held key and `mode: "hold"` could only
    // mean "write nothing" — which is what `on: false` already means. Same hazard as the two
    // above and a wider blast radius: `mode` was in the shipped registry defaults from the
    // first release, so it is in essentially every loadout on disk.
    ("toggle_sprint", "mode"),
];

/// Enum values that were renamed, as `(setting key, old value, new value)`.
///
/// The sibling problem to [`REMOVED_SETTINGS`], and it needs its own table for the same reason:
/// every settings struct is `deny_unknown_fields` and every generated enum is closed, so a value
/// that left the schema does not degrade — the file stops deserialising and the player's loadout
/// is gone.
///
/// **Applied to every mod, because these are shared-block keys.** `background` is declared once
/// in `schema/mods/_shared.json#/hud` and reaches all seventeen HUD mods, so a remap that named
/// mods would be seventeen rows that have to be edited again with the eighteenth.
///
/// A rename is what makes a remap safe to run on every load rather than once. `background: none`
/// was the shipped default *and* it drew a ground — there was no way to get a bare readout at
/// all — so every occurrence on disk means "I took the default", and `subtle` is exactly what
/// those players have been looking at. Because `none` is no longer a legal value, seeing one can
/// only mean the file predates the rename; a *redefined* `none` would have been indistinguishable
/// from a player who has since chosen it, and this table would have pinned them to `subtle`
/// forever. Rows can be retired once no file on disk can plausibly carry the old value.
const REMAPPED_VALUES: &[(&str, &str, &str)] = &[
    // `background: none` -> `bare`, and the honest default is `subtle`. See above.
    ("background", "none", "subtle"),
];

/// Reads a loadout, dropping settings that have since been removed from the registry and
/// remapping enum values that have since been renamed.
///
/// Deliberately not a general `read_json` behaviour: this tolerance is for one shape of file
/// and two known classes of change. Everything else the store reads keeps failing loudly on a
/// key it does not recognise, which is what `deny_unknown_fields` is for.
fn read_loadout_json(path: &Path) -> Result<Loadout, Error> {
    let text =
        fs::read_to_string(path).map_err(|e| Error::Io { path: path.to_path_buf(), source: e })?;
    let mut raw: Value = serde_json::from_str(&text)
        .map_err(|e| Error::Json { path: path.to_path_buf(), source: e })?;

    if let Some(mods) = raw.get_mut("mods").and_then(Value::as_object_mut) {
        for (mod_id, key) in REMOVED_SETTINGS {
            if let Some(settings) = mods.get_mut(*mod_id).and_then(Value::as_object_mut) {
                settings.remove(*key);
            }
        }
        for settings in mods.values_mut().filter_map(Value::as_object_mut) {
            for (key, from, to) in REMAPPED_VALUES {
                if settings.get(*key).and_then(Value::as_str) == Some(*from) {
                    settings.insert((*key).to_string(), Value::String((*to).to_string()));
                }
            }
        }
    }

    serde_json::from_value(raw).map_err(|e| Error::Json { path: path.to_path_buf(), source: e })
}

/// Writes `value` to `path` atomically: temp file in the same directory, fsync, rename.
///
/// Same directory matters — a rename is only atomic within one filesystem.
fn write_json_atomic<T: serde::Serialize>(path: &Path, value: &T) -> Result<(), Error> {
    let dir = path.parent().unwrap_or_else(|| Path::new("."));
    create_dir_all(dir)?;
    let text = serde_json::to_string_pretty(value)
        .map_err(|e| Error::Json { path: path.to_path_buf(), source: e })?;

    let tmp = dir.join(format!(
        ".{}.tmp{}",
        path.file_name().and_then(|s| s.to_str()).unwrap_or("void"),
        std::process::id()
    ));
    let io = |e: std::io::Error| Error::Io { path: tmp.clone(), source: e };
    {
        let mut f = fs::File::create(&tmp).map_err(io)?;
        f.write_all(text.as_bytes()).map_err(io)?;
        f.write_all(b"\n").map_err(io)?;
        f.sync_all().map_err(io)?;
    }
    fs::rename(&tmp, path).map_err(|e| {
        let _ = fs::remove_file(&tmp);
        Error::Io { path: path.to_path_buf(), source: e }
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::mods::ModId;

    #[test]
    fn a_fresh_store_has_played_nowhere_and_says_so_without_erroring() {
        // An absent `servers.json` is a state, not a failure — a fresh install has played
        // nowhere. Erroring here would make the Servers screen unopenable on first run.
        let (_dir, store) = store();
        assert_eq!(store.servers().unwrap().list().len(), 0);
    }

    #[test]
    fn the_server_book_survives_a_round_trip_through_the_disk() {
        let (_dir, store) = store();
        store
            .update_servers(|book| {
                book.record_join("MC.Hypixel.net:25565", 1_000);
                book.record_session("mc.hypixel.net", 120_000, 121_000);
                book.set_favourite("na.minemen.club", true);
            })
            .unwrap();

        let book = store.servers().unwrap();
        let listed = book.list();
        assert_eq!(listed.len(), 2);
        // Canonicalised on the way in, so the record that comes back is keyed the way every
        // later reader will ask for it.
        let hypixel = book.get("mc.hypixel.net").unwrap();
        assert_eq!(hypixel.played_ms, 120_000);
        assert_eq!(hypixel.joins, 1);
        assert!(book.get("na.minemen.club").unwrap().favourite);
    }

    fn store() -> (tempfile::TempDir, Store) {
        let dir = tempfile::tempdir().expect("tempdir");
        let store = Store::at(dir.path());
        (dir, store)
    }

    /// An initialised store holding the three curated loadouts, active on `sword-pvp`.
    ///
    /// **Seeded here rather than by `init` alone.** A fresh install seeds one neutral loadout as
    /// of 2026-09-10, and that loadout deliberately stores no mod settings — which is exactly
    /// what the migration tests below need a file to *have*. Coupling them to the seed made them
    /// tests of a product decision as well as of the store, and they broke together when it
    /// changed. What a new player's library contains is `defaults.rs`'s to assert.
    fn seeded() -> (tempfile::TempDir, Store) {
        let (dir, store) = store();
        store.init().unwrap();
        for loadout in [defaults::sword_pvp(), defaults::bedwars(), defaults::uhc()] {
            store.save(&loadout).unwrap();
        }
        store.set_active(&LoadoutId::new("sword-pvp").unwrap()).unwrap();
        (dir, store)
    }

    #[test]
    fn first_run_seeds_one_loadout_and_the_factory_settings() {
        let (_d, s) = store();
        assert!(s.init().unwrap(), "first init seeds");
        assert!(!s.init().unwrap(), "second init is a no-op");

        // One, not three. It seeded all three curated loadouts until 2026-09-10 — see
        // `defaults::starter` for why a fresh install stopped arriving with a library.
        let ids: Vec<String> = s.list().unwrap().iter().map(|l| l.id.to_string()).collect();
        assert_eq!(ids, ["default"]);
        assert_eq!(s.active_id().unwrap().as_str(), "default");
        assert_eq!(s.settings().unwrap(), GlobalSettings::factory());
        assert_eq!(s.summaries().unwrap().len(), 1);
    }

    #[test]
    fn save_load_round_trips_and_switching_persists() {
        let (_d, s) = seeded();

        let mut l = s.active().unwrap();
        l.mods.set(ModId::Fullbright, {
            let mut m = l.mods.effective(ModId::Fullbright);
            m.insert("on".into(), true.into());
            m
        })
        .unwrap();
        s.save(&l).unwrap();
        assert_eq!(s.load(&l.id).unwrap(), l);

        let bedwars = LoadoutId::new("bedwars").unwrap();
        s.set_active(&bedwars).unwrap();
        assert_eq!(s.active_id().unwrap(), bedwars);

        // The cycle is a ring over whatever the library holds, asserted as a *property* rather
        // than as a list of names. It used to be `bedwars -> uhc -> sword-pvp`, which was a
        // statement about the seed as much as about `next_after`, and it broke when a fresh
        // install stopped seeding three loadouts.
        let ids = s.list_ids().unwrap();
        let mut at = bedwars.clone();
        for _ in 0..ids.len() {
            let next = s.next_after(&at).unwrap();
            assert_ne!(next, at, "the cycle must move");
            assert!(ids.contains(&next), "the cycle must stay inside the library");
            at = next;
        }
        assert_eq!(at, bedwars, "and come back round");
    }

    #[test]
    fn a_loadout_written_before_a_setting_was_removed_still_loads() {
        // The regression this guards is not hypothetical: every `*Settings` struct is
        // `deny_unknown_fields`, so before `read_loadout_json` existed, deleting a setting
        // made every loadout already on disk that carried it fail to deserialise — the player
        // opens the launcher and a loadout they built is simply gone, with a JSON error.
        let (_d, s) = seeded();

        let id = LoadoutId::new("sword-pvp").unwrap();
        let path = s.loadouts_dir().join("sword-pvp.json");

        // Put the removed key back on disk, exactly as an older build would have written it.
        let mut raw: Value = serde_json::from_str(&fs::read_to_string(&path).unwrap()).unwrap();
        raw["mods"]["toggle_sprint"]
            .as_object_mut()
            .unwrap()
            .insert("show_status".into(), true.into());
        fs::write(&path, serde_json::to_string_pretty(&raw).unwrap()).unwrap();

        // It loads, and the stale key is not in the loaded state.
        let loaded = s.load(&id).expect("a stale removed setting must not orphan the loadout");
        assert!(
            !loaded.mods.effective(ModId::ToggleSprint).contains_key("show_status"),
            "the removed key must be dropped, not carried"
        );

        // …and the next save is the migration: the key is gone from the file too.
        s.save(&loaded).unwrap();
        let after: Value = serde_json::from_str(&fs::read_to_string(&path).unwrap()).unwrap();
        assert!(
            after["mods"]["toggle_sprint"].get("show_status").is_none(),
            "a save after the migration must not write the removed key back"
        );
    }

    #[test]
    fn a_loadout_written_before_a_value_was_renamed_still_loads() {
        // Same hazard as the test above, from the other side: `HudBackground` is a closed
        // generated enum, so a value that left the schema does not degrade — it fails the whole
        // file. `background: "none"` was the shipped default and is therefore in essentially
        // every loadout ever written, which is the widest blast radius this store has.
        let (_d, s) = seeded();

        let id = LoadoutId::new("sword-pvp").unwrap();
        let path = s.loadouts_dir().join("sword-pvp.json");

        let mut raw: Value = serde_json::from_str(&fs::read_to_string(&path).unwrap()).unwrap();
        for mod_id in ["fps", "cps", "ping"] {
            raw["mods"][mod_id]
                .as_object_mut()
                .unwrap()
                .insert("background".into(), "none".into());
        }
        fs::write(&path, serde_json::to_string_pretty(&raw).unwrap()).unwrap();

        // It loads, and every occurrence has become the value those players were looking at.
        let loaded = s.load(&id).expect("a renamed enum value must not orphan the loadout");
        for mod_id in [ModId::Fps, ModId::Cps, ModId::Ping] {
            assert_eq!(
                loaded.mods.effective(mod_id).get("background").and_then(Value::as_str),
                Some("subtle"),
                "{mod_id:?} kept the pre-rename value"
            );
        }

        // …and the next save is the migration: the old value is gone from the file too.
        s.save(&loaded).unwrap();
        let after: Value = serde_json::from_str(&fs::read_to_string(&path).unwrap()).unwrap();
        assert_ne!(after["mods"]["fps"]["background"].as_str(), Some("none"));
    }

    #[test]
    fn an_unknown_setting_that_was_never_ours_still_fails_loudly() {
        // The tolerance above is for one named list of retired keys, not for junk in general.
        let (_d, s) = seeded();
        let path = s.loadouts_dir().join("sword-pvp.json");
        let mut raw: Value = serde_json::from_str(&fs::read_to_string(&path).unwrap()).unwrap();
        raw["mods"]["toggle_sprint"]
            .as_object_mut()
            .unwrap()
            .insert("wat".into(), true.into());
        fs::write(&path, serde_json::to_string_pretty(&raw).unwrap()).unwrap();

        assert!(s.load(&LoadoutId::new("sword-pvp").unwrap()).is_err());
    }

    #[test]
    fn deleting_the_active_loadout_moves_the_pointer() {
        let (_d, s) = seeded();
        let before = s.list_ids().unwrap().len();
        let gone = LoadoutId::new("sword-pvp").unwrap();
        s.delete(&gone).unwrap();

        // The property, not the name: the pointer is off the deleted loadout and onto one that
        // still exists. Which one is the library's order, and naming it made this a test of the
        // seed as well as of `delete`.
        let now = s.active_id().unwrap();
        assert_ne!(now, gone, "the pointer cannot name a loadout that is gone");
        assert!(s.load(&now).is_ok(), "and must name one that loads");
        assert_eq!(s.list().unwrap().len(), before - 1);
    }

    #[test]
    fn writes_leave_no_temporary_files_behind() {
        let (d, s) = store();
        s.init().unwrap();
        let stray: Vec<_> = fs::read_dir(d.path())
            .unwrap()
            .chain(fs::read_dir(s.loadouts_dir()).unwrap())
            .map(|e| e.unwrap().file_name().to_string_lossy().to_string())
            .filter(|n| n.contains(".tmp"))
            .collect();
        assert!(stray.is_empty(), "left behind {stray:?}");
    }

    #[test]
    fn session_stats_accumulate_across_reports() {
        let (_d, s) = seeded();
        let id = LoadoutId::new("sword-pvp").unwrap();
        s.record_session(&id, 60_000, 140.0).unwrap();
        s.record_session(&id, 60_000, 160.0).unwrap();
        let stats = s.load(&id).unwrap().stats.unwrap();
        assert_eq!(stats.played_ms, Some(120_000));
        assert_eq!(stats.fps_avg, Some(150.0));
    }
}
