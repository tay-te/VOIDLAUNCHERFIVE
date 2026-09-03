//! TODO(integrate): §12.2–12.4 — resolve the 1.8.9 and Legacy Fabric manifests,
//! download libraries/assets in parallel with SHA-1 verification and a hash cache,
//! fetch the signed `void-client` JAR, and locate or fetch a Java 8 runtime. All of
//! that is `void-core`'s, and `void-core` is a doc-comment stub.
//!
//! What is real here is the **shape**: the step sequence, the `prepare:progress`
//! payload (`{ step, done, total, bytes_per_sec }`) and the throughput calculation.
//! The UI's progress bar, per-step label and MB/s readout are all driven from this, so
//! when `void_core::prepare` lands it can emit through the same `Progress` sink and
//! nothing in the web layer moves.
//!
//! The stand-in does not touch the network and does not write game files. It says so:
//! the first line the log drawer shows names it, and `PrepareReport::from_cache` is
//! false so nothing downstream can mistake it for a warm cache hit.

use std::time::{Duration, Instant};

use crate::error::Error;
use crate::events::{emit, Emitter, PREPARE_PROGRESS};
use crate::models::{PrepareProgress, PrepareReport};

/// The steps in order, with the byte weight each one carries on a cold install.
/// Weights are the real rough shape of a 1.8.9 + Legacy Fabric install, so the bar
/// moves the way it will once the downloads are wired up.
pub const STEPS: [(&str, u64); 6] = [
    ("manifest", 2 * 1024 * 1024),
    ("libraries", 48 * 1024 * 1024),
    ("assets", 160 * 1024 * 1024),
    ("fabric", 6 * 1024 * 1024),
    ("java", 95 * 1024 * 1024),
    ("mod", 25 * 1024 * 1024),
];

pub fn total_bytes() -> u64 {
    STEPS.iter().map(|(_, b)| b).sum()
}

/// A sink the real downloader can push into, so progress reporting is written once.
pub struct Progress<'a> {
    emitter: &'a dyn Emitter,
    started: Instant,
    done: u64,
    total: u64,
}

impl<'a> Progress<'a> {
    pub fn new(emitter: &'a dyn Emitter, total: u64) -> Self {
        Progress {
            emitter,
            started: Instant::now(),
            done: 0,
            total,
        }
    }

    /// Report `bytes` more of `step` transferred.
    pub fn advance(&mut self, step: &str, bytes: u64, detail: Option<String>) {
        self.done = (self.done + bytes).min(self.total);
        emit(
            self.emitter,
            PREPARE_PROGRESS,
            &PrepareProgress {
                step: step.to_string(),
                done: self.done,
                total: self.total,
                bytes_per_sec: self.rate(),
                detail,
            },
        );
    }

    pub fn finish(&mut self) {
        self.done = self.total;
        emit(
            self.emitter,
            PREPARE_PROGRESS,
            &PrepareProgress {
                step: "done".into(),
                done: self.done,
                total: self.total,
                bytes_per_sec: self.rate(),
                detail: None,
            },
        );
    }

    /// Bytes/second since the run started. Averaged rather than instantaneous: an
    /// instantaneous rate makes the readout flicker between 0 and 90 MB/s on a fast
    /// link, which reads as broken.
    pub fn rate(&self) -> u64 {
        let secs = self.started.elapsed().as_secs_f64();
        if secs <= 0.001 {
            0
        } else {
            (self.done as f64 / secs) as u64
        }
    }

    pub fn elapsed_ms(&self) -> u64 {
        self.started.elapsed().as_millis() as u64
    }
}

/// Walk the steps, emitting progress. Replace the body with `void_core::prepare(...)`.
pub async fn run(emitter: &dyn Emitter, loadout: &str, java_path: String) -> Result<PrepareReport, Error> {
    tracing::warn!(
        "prepare() is the desktop stand-in: no files are downloaded. \
         TODO(integrate) void-core::prepare"
    );

    let total = total_bytes();
    let mut progress = Progress::new(emitter, total);

    for (step, weight) in STEPS {
        // Twenty ticks per step: enough for the bar to animate, few enough that the
        // event stream stays cheap.
        let chunk = weight / 20;
        for i in 0..20 {
            progress.advance(
                step,
                chunk,
                (i == 0).then(|| format!("{step} · {}", human_bytes(weight))),
            );
            tokio::time::sleep(Duration::from_millis(25)).await;
        }
    }
    progress.finish();

    Ok(PrepareReport {
        loadout: loadout.to_string(),
        bytes_downloaded: 0,
        duration_ms: progress.elapsed_ms(),
        java_path,
        from_cache: false,
    })
}

pub fn human_bytes(bytes: u64) -> String {
    const UNITS: [&str; 4] = ["B", "KB", "MB", "GB"];
    let mut value = bytes as f64;
    let mut unit = 0;
    while value >= 1024.0 && unit < UNITS.len() - 1 {
        value /= 1024.0;
        unit += 1;
    }
    if unit == 0 {
        format!("{bytes} B")
    } else {
        format!("{value:.1} {}", UNITS[unit])
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::test_support::Recorder;

    #[test]
    fn progress_never_exceeds_the_total() {
        let rec = Recorder::default();
        let mut p = Progress::new(&rec, 100);
        p.advance("assets", 250, None);
        let payload = &rec.payloads(PREPARE_PROGRESS)[0];
        assert_eq!(payload["done"], 100);
        assert_eq!(payload["total"], 100);
    }

    #[tokio::test]
    async fn run_emits_every_step_in_order_and_finishes_full() {
        let rec = Recorder::default();
        let report = run(&rec, "sword-pvp", "/usr/bin/java".into()).await.unwrap();
        assert_eq!(report.loadout, "sword-pvp");

        let steps: Vec<String> = rec
            .payloads(PREPARE_PROGRESS)
            .into_iter()
            .map(|v| v["step"].as_str().unwrap().to_string())
            .collect();
        let mut seen = Vec::new();
        for s in &steps {
            if seen.last() != Some(s) {
                seen.push(s.clone());
            }
        }
        assert_eq!(
            seen,
            ["manifest", "libraries", "assets", "fabric", "java", "mod", "done"]
        );

        let last = rec.payloads(PREPARE_PROGRESS).pop().unwrap();
        assert_eq!(last["done"], last["total"]);
    }

    #[test]
    fn bytes_are_humanised() {
        assert_eq!(human_bytes(512), "512 B");
        assert_eq!(human_bytes(2048), "2.0 KB");
        assert_eq!(human_bytes(25 * 1024 * 1024), "25.0 MB");
    }
}
