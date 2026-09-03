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

use crate::loadout::{Loadout, LoadoutId, LoadoutSummary};
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
        let loadout: Loadout = read_json(&path)?;
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

    fn store() -> (tempfile::TempDir, Store) {
        let dir = tempfile::tempdir().expect("tempdir");
        let store = Store::at(dir.path());
        (dir, store)
    }

    #[test]
    fn first_run_seeds_the_three_defaults() {
        let (_d, s) = store();
        assert!(s.init().unwrap(), "first init seeds");
        assert!(!s.init().unwrap(), "second init is a no-op");

        let ids: Vec<String> = s.list().unwrap().iter().map(|l| l.id.to_string()).collect();
        assert_eq!(ids, ["sword-pvp", "bedwars", "uhc"]);
        assert_eq!(s.active_id().unwrap().as_str(), "sword-pvp");
        assert_eq!(s.settings().unwrap(), GlobalSettings::factory());
        assert_eq!(s.summaries().unwrap().len(), 3);
    }

    #[test]
    fn save_load_round_trips_and_switching_persists() {
        let (_d, s) = store();
        s.init().unwrap();

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
        assert_eq!(s.next_after(&bedwars).unwrap().as_str(), "uhc");
        assert_eq!(s.next_after(&LoadoutId::new("uhc").unwrap()).unwrap().as_str(), "sword-pvp");
    }

    #[test]
    fn deleting_the_active_loadout_moves_the_pointer() {
        let (_d, s) = store();
        s.init().unwrap();
        s.delete(&LoadoutId::new("sword-pvp").unwrap()).unwrap();
        assert_eq!(s.active_id().unwrap().as_str(), "bedwars");
        assert_eq!(s.list().unwrap().len(), 2);
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
        let (_d, s) = store();
        s.init().unwrap();
        let id = LoadoutId::new("sword-pvp").unwrap();
        s.record_session(&id, 60_000, 140.0).unwrap();
        s.record_session(&id, 60_000, 160.0).unwrap();
        let stats = s.load(&id).unwrap().stats.unwrap();
        assert_eq!(stats.played_ms, Some(120_000));
        assert_eq!(stats.fps_avg, Some(150.0));
    }
}
