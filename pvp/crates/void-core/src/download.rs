//! Bounded-parallel downloads with SHA-1 verification and a hash-addressed cache.
//!
//! Everything a launch needs — libraries, natives, assets, the client jar — is a
//! [`FileSpec`]. [`Downloader::fetch_all`] runs at most [`CONCURRENCY`] of them at once,
//! verifies each against the SHA-1 the manifest published, and keeps a copy under
//! `cache/objects/<ab>/<sha1>` so a reinstall or a second profile costs no bandwidth.
//!
//! Progress is reported over a channel rather than a callback, so the Tauri layer can
//! forward it to the UI and the CLI can render it without either knowing about the other.

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use futures_util::stream::{self, StreamExt};
use sha1::{Digest, Sha1};
use tokio::sync::mpsc;

use crate::error::{Error, Result};
use crate::manifest::FileSpec;
use crate::paths::Paths;

/// How many downloads run at once (§12).
pub const CONCURRENCY: usize = 16;

/// What the downloader reports as it works.
#[derive(Debug, Clone, PartialEq)]
pub enum Progress {
    /// Sent once, before anything is fetched.
    Started {
        /// How many files are in this batch.
        files: usize,
        /// Total bytes, where the manifest published sizes.
        bytes: u64,
    },
    /// One file finished.
    Finished {
        /// Where it landed, relative to the installation root.
        path: PathBuf,
        /// How many bytes it holds.
        bytes: u64,
        /// True when it was already on disk or in the cache, so nothing was fetched.
        cached: bool,
        /// How many files are done, including this one.
        done: usize,
        /// How many files are in the batch.
        total: usize,
    },
    /// The batch finished.
    Completed {
        /// How many files were fetched or verified.
        files: usize,
        /// How many bytes were actually pulled over the network.
        downloaded_bytes: u64,
    },
}

/// Fetches [`FileSpec`]s into an installation.
#[derive(Debug, Clone)]
pub struct Downloader {
    client: reqwest::Client,
    root: PathBuf,
    cache: PathBuf,
    concurrency: usize,
}

impl Downloader {
    /// A downloader writing into `paths`.
    pub fn new(client: reqwest::Client, paths: &Paths) -> Self {
        Self {
            client,
            root: paths.root().to_path_buf(),
            cache: paths.cache_objects_dir(),
            concurrency: CONCURRENCY,
        }
    }

    /// Overrides the parallelism; the default is [`CONCURRENCY`].
    pub fn with_concurrency(mut self, n: usize) -> Self {
        self.concurrency = n.max(1);
        self
    }

    /// Fetches everything, at most [`Downloader::with_concurrency`] at a time.
    ///
    /// Fails on the first error rather than half-installing: a missing library is not
    /// something a launch can recover from, and the cache means a retry is cheap.
    pub async fn fetch_all(
        &self,
        files: &[FileSpec],
        progress: Option<mpsc::Sender<Progress>>,
    ) -> Result<u64> {
        let total = files.len();
        let announced: u64 = files.iter().filter_map(|f| f.size).sum();
        if let Some(tx) = &progress {
            let _ = tx.send(Progress::Started { files: total, bytes: announced }).await;
        }

        let done = Arc::new(AtomicU64::new(0));
        let downloaded = Arc::new(AtomicU64::new(0));

        let mut stream = stream::iter(files.iter().cloned())
            .map(|spec| {
                let this = self.clone();
                let progress = progress.clone();
                let done = done.clone();
                let downloaded = downloaded.clone();
                async move {
                    let outcome = this.fetch_one(&spec).await?;
                    if !outcome.cached {
                        downloaded.fetch_add(outcome.bytes, Ordering::Relaxed);
                    }
                    let n = done.fetch_add(1, Ordering::Relaxed) + 1;
                    if let Some(tx) = progress {
                        let _ = tx
                            .send(Progress::Finished {
                                path: spec.relative_path.clone(),
                                bytes: outcome.bytes,
                                cached: outcome.cached,
                                done: n as usize,
                                total,
                            })
                            .await;
                    }
                    Ok::<_, Error>(())
                }
            })
            .buffer_unordered(self.concurrency);

        while let Some(result) = stream.next().await {
            result?;
        }

        let downloaded_bytes = downloaded.load(Ordering::Relaxed);
        if let Some(tx) = &progress {
            let _ = tx.send(Progress::Completed { files: total, downloaded_bytes }).await;
        }
        Ok(downloaded_bytes)
    }

    async fn fetch_one(&self, spec: &FileSpec) -> Result<Outcome> {
        let dest = self.root.join(&spec.relative_path);

        // Already in place and correct?
        if let Some(bytes) = verified_len(&dest, spec.sha1.as_deref())? {
            return Ok(Outcome { bytes, cached: true });
        }

        // In the hash cache?
        let cached = spec.sha1.as_deref().map(|sha| self.cache_path(sha));
        if let Some(cache_path) = &cached {
            if let Some(bytes) = verified_len(cache_path, spec.sha1.as_deref())? {
                copy_into_place(cache_path, &dest)?;
                return Ok(Outcome { bytes, cached: true });
            }
        }

        tracing::debug!(url = %spec.url, "downloading");
        let resp = self.client.get(&spec.url).send().await?;
        let status = resp.status();
        if !status.is_success() {
            return Err(Error::HttpStatus {
                url: spec.url.clone(),
                status: status.as_u16(),
                detail: None,
            });
        }
        let body = resp.bytes().await?;

        if let Some(expected) = &spec.sha1 {
            let actual = hex::encode(Sha1::digest(&body));
            if !actual.eq_ignore_ascii_case(expected) {
                return Err(Error::Sha1Mismatch {
                    path: spec.relative_path.clone(),
                    expected: expected.clone(),
                    actual,
                });
            }
        }

        write_atomic(&dest, &body)?;
        if let Some(cache_path) = &cached {
            // A cache miss is never fatal; the file is already where it needs to be.
            if let Err(e) = write_atomic(cache_path, &body) {
                tracing::warn!(error = %e, "could not populate the download cache");
            }
        }
        Ok(Outcome { bytes: body.len() as u64, cached: false })
    }

    fn cache_path(&self, sha1: &str) -> PathBuf {
        self.cache.join(&sha1[..2.min(sha1.len())]).join(sha1)
    }
}

struct Outcome {
    bytes: u64,
    cached: bool,
}

/// The length of `path` if it exists and matches `sha1`, else `None`.
///
/// With no expected digest, existence is taken as good enough — that is only the case
/// for Legacy Fabric libraries, which the meta server publishes without hashes.
fn verified_len(path: &Path, sha1: Option<&str>) -> Result<Option<u64>> {
    if !path.exists() {
        return Ok(None);
    }
    let bytes = std::fs::read(path).map_err(|e| Error::io(path, e))?;
    match sha1 {
        None => Ok(Some(bytes.len() as u64)),
        Some(expected) => {
            let actual = hex::encode(Sha1::digest(&bytes));
            if actual.eq_ignore_ascii_case(expected) {
                Ok(Some(bytes.len() as u64))
            } else {
                tracing::warn!(path = %path.display(), "sha1 mismatch on disk; refetching");
                Ok(None)
            }
        }
    }
}

fn copy_into_place(from: &Path, to: &Path) -> Result<()> {
    if let Some(dir) = to.parent() {
        std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
    }
    std::fs::copy(from, to).map_err(|e| Error::io(to, e))?;
    Ok(())
}

/// Writes bytes through a temporary file in the same directory, then renames.
fn write_atomic(path: &Path, bytes: &[u8]) -> Result<()> {
    let dir = path.parent().unwrap_or_else(|| Path::new("."));
    std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
    let tmp = dir.join(format!(
        ".{}.{}.tmp",
        path.file_name().and_then(|s| s.to_str()).unwrap_or("void"),
        std::process::id()
    ));
    std::fs::write(&tmp, bytes).map_err(|e| Error::io(&tmp, e))?;
    std::fs::rename(&tmp, path).map_err(|e| {
        let _ = std::fs::remove_file(&tmp);
        Error::io(path, e)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cache_paths_fan_out_by_the_first_two_hex_digits() {
        let dir = tempfile::tempdir().unwrap();
        let d = Downloader::new(reqwest::Client::new(), &Paths::at(dir.path()));
        assert_eq!(
            d.cache_path("abcdef0123"),
            dir.path().join("cache").join("objects").join("ab").join("abcdef0123")
        );
    }

    #[test]
    fn verification_rejects_a_corrupt_file_and_accepts_a_good_one() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("x");
        std::fs::write(&file, b"void").unwrap();
        let sha = hex::encode(Sha1::digest(b"void"));
        assert_eq!(verified_len(&file, Some(&sha)).unwrap(), Some(4));
        assert_eq!(verified_len(&file, Some(&"0".repeat(40))).unwrap(), None);
        assert_eq!(verified_len(&file, None).unwrap(), Some(4));
        assert_eq!(verified_len(&dir.path().join("missing"), None).unwrap(), None);
    }

    #[test]
    fn atomic_writes_leave_no_temporary_files() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("nested").join("x.json");
        write_atomic(&file, b"{}").unwrap();
        assert_eq!(std::fs::read(&file).unwrap(), b"{}");
        let strays: Vec<_> = std::fs::read_dir(file.parent().unwrap())
            .unwrap()
            .filter(|e| e.as_ref().unwrap().file_name().to_string_lossy().contains(".tmp"))
            .collect();
        assert!(strays.is_empty());
    }
}
