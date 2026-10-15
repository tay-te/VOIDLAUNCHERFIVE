//! The launcher's own log: stdout for development, and a rolling file in the data
//! directory for everyone else.
//!
//! `<data dir>/logs/launcher.YYYY-MM-DD.log`, one file a day, the last
//! [`KEEP_FILES`] kept. A released build has no console (the Windows subsystem is
//! `windows`), so without this file a player's "it doesn't launch" leaves nothing to
//! look at. Settings → *Launcher log* opens the folder, and the log drawer can copy the
//! game's output; together they are a complete bug report.
//!
//! `$VOID_LOG` sets the filter (`info` by default), as before.

use std::path::Path;

use tracing_appender::non_blocking::WorkerGuard;
use tracing_subscriber::prelude::*;
use tracing_subscriber::EnvFilter;

/// How many daily files to keep.
pub const KEEP_FILES: usize = 7;

/// The file-name prefix inside the logs directory.
pub const FILE_PREFIX: &str = "launcher";

/// Installs the global subscriber. Keep the returned guard alive for the whole
/// process: dropping it flushes and stops the file writer.
///
/// A logs directory that cannot be created degrades to stdout only, with a warning —
/// logging must never be the reason the launcher does not start.
pub fn init(logs_dir: &Path) -> Option<WorkerGuard> {
    let filter = || {
        EnvFilter::try_from_env("VOID_LOG").unwrap_or_else(|_| EnvFilter::new("info"))
    };
    let stdout = tracing_subscriber::fmt::layer().with_filter(filter());

    let appender = std::fs::create_dir_all(logs_dir).ok().and_then(|()| {
        tracing_appender::rolling::Builder::new()
            .rotation(tracing_appender::rolling::Rotation::DAILY)
            .filename_prefix(FILE_PREFIX)
            .filename_suffix("log")
            .max_log_files(KEEP_FILES)
            .build(logs_dir)
            .ok()
    });

    match appender {
        Some(appender) => {
            let (writer, guard) = tracing_appender::non_blocking(appender);
            let file = tracing_subscriber::fmt::layer()
                .with_ansi(false)
                .with_writer(writer)
                .with_filter(filter());
            let _ = tracing_subscriber::registry().with(stdout).with(file).try_init();
            Some(guard)
        }
        None => {
            let _ = tracing_subscriber::registry().with(stdout).try_init();
            tracing::warn!(dir = %logs_dir.display(), "could not open the launcher log directory; logging to stdout only");
            None
        }
    }
}

/// The newest launcher log file, if there is one.
pub fn latest_file(logs_dir: &Path) -> Option<std::path::PathBuf> {
    let mut files: Vec<_> = std::fs::read_dir(logs_dir)
        .ok()?
        .flatten()
        .map(|e| e.path())
        .filter(|p| {
            p.file_name()
                .and_then(|n| n.to_str())
                .is_some_and(|n| n.starts_with(FILE_PREFIX) && n.ends_with(".log"))
        })
        .collect();
    files.sort();
    files.pop()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_latest_file_is_the_newest_day() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(latest_file(dir.path()), None);
        for day in ["2026-10-13", "2026-10-15", "2026-10-14"] {
            std::fs::write(dir.path().join(format!("launcher.{day}.log")), "x").unwrap();
        }
        std::fs::write(dir.path().join("other.txt"), "x").unwrap();
        assert!(latest_file(dir.path()).unwrap().ends_with("launcher.2026-10-15.log"));
    }
}
