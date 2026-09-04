//! Turns `void_core::download::Progress` into the `prepare:progress` events the launch
//! button renders.
//!
//! The two shapes differ on purpose and the translation is the whole job of this file:
//!
//! - The downloader counts **files** (`done`/`total`) and reports bytes only for files
//!   whose manifest published a size. A progress bar driven off file counts jumps —
//!   160 MB of assets is 3,000 tiny files and the client jar is one — so the bar here
//!   is driven off **bytes**, with the file count carried in `detail`.
//! - `bytes_per_sec` is averaged over the run rather than instantaneous. An
//!   instantaneous rate flickers between 0 and 90 MB/s on a fast link, which reads as
//!   broken rather than as fast.
//! - The downloader is called several times per prepare (libraries and the client jar,
//!   then assets, then maybe a JRE). A `Started` therefore does *not* reset the bar; the
//!   caller names the step and the totals accumulate across passes.

use std::time::Instant;

use tokio::sync::mpsc;

use crate::events::{emit, Emitter, PREPARE_PROGRESS};
use crate::models::PrepareProgress;
use void_core::download::Progress;

/// Accumulates one prepare run and emits `prepare:progress`.
pub struct PrepareProgressSink {
    started: Instant,
    step: String,
    done_bytes: u64,
    total_bytes: u64,
    done_files: u64,
    total_files: u64,
    downloaded_bytes: u64,
}

impl PrepareProgressSink {
    pub fn new() -> Self {
        PrepareProgressSink {
            started: Instant::now(),
            step: "manifest".into(),
            done_bytes: 0,
            total_bytes: 0,
            done_files: 0,
            total_files: 0,
            downloaded_bytes: 0,
        }
    }

    pub fn elapsed_ms(&self) -> u64 {
        self.started.elapsed().as_millis() as u64
    }

    pub fn files(&self) -> u64 {
        self.done_files
    }

    pub fn downloaded_bytes(&self) -> u64 {
        self.downloaded_bytes
    }

    /// Name the phase the next messages belong to, and emit a frame so the label
    /// changes even before the first file lands.
    pub fn step(&mut self, emitter: &dyn Emitter, step: &str, detail: Option<String>) {
        self.step = step.to_string();
        self.emit(emitter, detail);
    }

    /// Fold one downloader message in.
    pub fn absorb(&mut self, emitter: &dyn Emitter, progress: Progress) {
        match progress {
            Progress::Started { files, bytes } => {
                self.total_files += files as u64;
                self.total_bytes += bytes;
                self.emit(emitter, Some(format!("{files} files")));
            }
            Progress::Finished { bytes, cached, .. } => {
                self.done_files += 1;
                self.done_bytes += bytes;
                // A batch whose manifest published no sizes would otherwise sit at 0/0;
                // growing the total as files land keeps the bar monotonic and honest.
                if self.done_bytes > self.total_bytes {
                    self.total_bytes = self.done_bytes;
                }
                if !cached {
                    self.downloaded_bytes += bytes;
                }
                // One event per file is too many at 3,000 assets: emit on every 16th
                // file, and always on the last one of a batch.
                if self.done_files % 16 == 0 || self.done_files == self.total_files {
                    self.emit(emitter, None);
                }
            }
            Progress::Completed { .. } => self.emit(emitter, None),
        }
    }

    /// The final frame: `step: "done"`, bar full.
    pub fn finish(&mut self, emitter: &dyn Emitter) {
        self.step = "done".into();
        if self.total_bytes == 0 {
            self.total_bytes = 1;
        }
        self.done_bytes = self.total_bytes;
        self.done_files = self.total_files;
        self.emit(emitter, None);
    }

    fn emit(&self, emitter: &dyn Emitter, detail: Option<String>) {
        emit(
            emitter,
            PREPARE_PROGRESS,
            &PrepareProgress {
                step: self.step.clone(),
                done: self.done_bytes,
                total: self.total_bytes.max(self.done_bytes),
                bytes_per_sec: self.rate(),
                detail,
            },
        );
    }

    fn rate(&self) -> u64 {
        let secs = self.started.elapsed().as_secs_f64();
        if secs <= 0.05 {
            0
        } else {
            (self.downloaded_bytes as f64 / secs) as u64
        }
    }
}

impl Default for PrepareProgressSink {
    fn default() -> Self {
        Self::new()
    }
}

/// A channel the downloader can send into, drained into `sink` on a background task.
///
/// Returns the sender to hand to `void_core`, and a join handle that resolves once the
/// sender is dropped and every queued message has been emitted — awaiting it before the
/// next phase is what keeps the event order matching the work order.
pub fn channel(
    emitter: std::sync::Arc<dyn Emitter>,
    sink: std::sync::Arc<std::sync::Mutex<PrepareProgressSink>>,
) -> (mpsc::Sender<Progress>, tokio::task::JoinHandle<()>) {
    let (tx, mut rx) = mpsc::channel::<Progress>(256);
    let handle = tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if let Ok(mut sink) = sink.lock() {
                sink.absorb(emitter.as_ref(), msg);
            }
        }
    });
    (tx, handle)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::events::test_support::Recorder;
    use std::path::PathBuf;

    #[test]
    fn the_bar_is_driven_by_bytes_not_file_counts() {
        let rec = Recorder::default();
        let mut sink = PrepareProgressSink::new();
        sink.absorb(&rec, Progress::Started { files: 2, bytes: 1000 });
        for _ in 0..2 {
            sink.absorb(
                &rec,
                Progress::Finished {
                    path: PathBuf::from("x"),
                    bytes: 400,
                    cached: false,
                    done: 1,
                    total: 2,
                },
            );
        }
        sink.absorb(&rec, Progress::Completed { files: 2, downloaded_bytes: 800 });

        let last = rec.payloads(PREPARE_PROGRESS).pop().unwrap();
        assert_eq!(last["done"], 800);
        assert_eq!(last["total"], 1000);
    }

    #[test]
    fn totals_grow_rather_than_letting_the_bar_overflow() {
        let rec = Recorder::default();
        let mut sink = PrepareProgressSink::new();
        // A batch that published no sizes at all — Mojang's asset index does this.
        sink.absorb(&rec, Progress::Started { files: 1, bytes: 0 });
        sink.absorb(
            &rec,
            Progress::Finished {
                path: PathBuf::from("a"),
                bytes: 500,
                cached: true,
                done: 1,
                total: 1,
            },
        );
        let last = rec.payloads(PREPARE_PROGRESS).pop().unwrap();
        assert_eq!(last["done"], 500);
        assert_eq!(last["total"], 500);
        // A cached file counts toward the bar but not toward the download rate.
        assert_eq!(last["bytes_per_sec"], 0);
    }

    #[test]
    fn finish_fills_the_bar_and_names_the_step() {
        let rec = Recorder::default();
        let mut sink = PrepareProgressSink::new();
        sink.step(&rec, "assets", None);
        sink.finish(&rec);
        let last = rec.payloads(PREPARE_PROGRESS).pop().unwrap();
        assert_eq!(last["step"], "done");
        assert_eq!(last["done"], last["total"]);
    }

    #[test]
    fn steps_are_named_before_any_file_lands() {
        let rec = Recorder::default();
        let mut sink = PrepareProgressSink::new();
        sink.step(&rec, "java", Some("Temurin 8".into()));
        let first = rec.payloads(PREPARE_PROGRESS).remove(0);
        assert_eq!(first["step"], "java");
        assert_eq!(first["detail"], "Temurin 8");
    }
}
