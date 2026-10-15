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
    /// Bytes of a single large file arriving, before its `Finished`.
    ///
    /// Only the one-file streaming downloads send this (the JRE archive, the mod JAR):
    /// without it a 40 MB fetch is a bar that sits still and then jumps. The `Finished`
    /// that follows carries the file's full size as usual, so a consumer that counts
    /// these has to subtract them again when it lands.
    Bytes {
        /// How many more bytes arrived since the last `Bytes`.
        delta: u64,
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
    trust_sizes: bool,
}

impl Downloader {
    /// A downloader writing into `paths`.
    pub fn new(client: reqwest::Client, paths: &Paths) -> Self {
        Self {
            client,
            root: paths.root().to_path_buf(),
            cache: paths.cache_objects_dir(),
            concurrency: CONCURRENCY,
            trust_sizes: false,
        }
    }

    /// Accept a file that is already in place when its **size** matches, without
    /// re-hashing it.
    ///
    /// For a warm launch only: [`crate::install::prepare`] turns this on when the
    /// installation carries a stamp saying every file of this exact profile was hashed
    /// and verified by an earlier run. Hashing ~3,000 asset objects costs seconds on
    /// every press of Launch, which is most of a warm launch's budget, and buys nothing
    /// over the stamp. A file that is missing or the wrong size is still fetched and
    /// verified in full.
    pub fn trust_existing_sizes(mut self, trust: bool) -> Self {
        self.trust_sizes = trust;
        self
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
        if self.trust_sizes {
            if let Some(bytes) = size_matches(&dest, spec.size) {
                return Ok(Outcome { bytes, cached: true });
            }
        }
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

/// The length of `path` if it exists and is `size` bytes long (any size when the
/// manifest published none).
fn size_matches(path: &Path, size: Option<u64>) -> Option<u64> {
    let len = std::fs::metadata(path).ok().filter(|m| m.is_file())?.len();
    match size {
        Some(expected) if expected != len => None,
        _ => Some(len),
    }
}

/// Streams one URL to `dest` through a temporary file, hashing as it goes, and renames
/// it into place only when the SHA-256 matches.
///
/// For the two large single files a launch fetches outside the manifest pipeline — the
/// Temurin archive and the `void-client` JAR — both of which are published with a
/// SHA-256 rather than Mojang's SHA-1. Progress goes out as [`Progress::Bytes`] while
/// the body arrives, then one `Finished`.
///
/// A file that fails verification never appears at `dest`: the temporary file is
/// deleted, so a corrupt download cannot be mistaken for a cached one on the next run.
pub async fn fetch_sha256(
    client: &reqwest::Client,
    url: &str,
    dest: &Path,
    sha256: &str,
    size_hint: Option<u64>,
    progress: Option<&mpsc::Sender<Progress>>,
) -> Result<u64> {
    use sha2::{Digest as _, Sha256};
    use std::io::Write as _;

    let dir = dest.parent().unwrap_or_else(|| Path::new("."));
    std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;

    let mut resp = client.get(url).send().await?;
    let status = resp.status();
    if !status.is_success() {
        return Err(Error::HttpStatus { url: url.to_string(), status: status.as_u16(), detail: None });
    }
    let total = resp.content_length().or(size_hint).unwrap_or(0);
    if let Some(tx) = progress {
        let _ = tx.send(Progress::Started { files: 1, bytes: total }).await;
    }

    let tmp = dir.join(format!(
        ".{}.{}.part",
        dest.file_name().and_then(|s| s.to_str()).unwrap_or("download"),
        std::process::id()
    ));
    let result: Result<u64> = async {
        let mut file = std::fs::File::create(&tmp).map_err(|e| Error::io(&tmp, e))?;
        let mut hasher = Sha256::new();
        let mut written = 0u64;
        let mut pending = 0u64;
        while let Some(chunk) = resp.chunk().await? {
            hasher.update(&chunk);
            file.write_all(&chunk).map_err(|e| Error::io(&tmp, e))?;
            written += chunk.len() as u64;
            pending += chunk.len() as u64;
            // One message per ~256 KB: smooth enough for a bar, cheap enough for a
            // channel that also carries 3,000 asset completions.
            if pending >= 256 * 1024 {
                if let Some(tx) = progress {
                    let _ = tx.send(Progress::Bytes { delta: pending }).await;
                }
                pending = 0;
            }
        }
        file.flush().map_err(|e| Error::io(&tmp, e))?;
        drop(file);
        if pending > 0 {
            if let Some(tx) = progress {
                let _ = tx.send(Progress::Bytes { delta: pending }).await;
            }
        }
        let actual = hex::encode(hasher.finalize());
        if !actual.eq_ignore_ascii_case(sha256) {
            return Err(Error::Sha256Mismatch {
                path: dest.to_path_buf(),
                expected: sha256.to_string(),
                actual,
            });
        }
        Ok(written)
    }
    .await;

    let written = match result {
        Ok(n) => n,
        Err(e) => {
            let _ = std::fs::remove_file(&tmp);
            return Err(e);
        }
    };
    if dest.exists() {
        let _ = std::fs::remove_file(dest);
    }
    std::fs::rename(&tmp, dest).map_err(|e| {
        let _ = std::fs::remove_file(&tmp);
        Error::io(dest, e)
    })?;
    if let Some(tx) = progress {
        let _ = tx
            .send(Progress::Finished {
                path: dest.to_path_buf(),
                bytes: written,
                cached: false,
                done: 1,
                total: 1,
            })
            .await;
    }
    Ok(written)
}

/// The SHA-256 of a file on disk, lower-case hex.
pub fn sha256_file(path: &Path) -> Result<String> {
    use sha2::{Digest as _, Sha256};
    use std::io::Read as _;
    let mut file = std::fs::File::open(path).map_err(|e| Error::io(path, e))?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; 256 * 1024];
    loop {
        let n = file.read(&mut buf).map_err(|e| Error::io(path, e))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
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
    fn a_trusted_size_check_accepts_the_right_length_only() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("x");
        std::fs::write(&file, b"void").unwrap();
        assert_eq!(size_matches(&file, Some(4)), Some(4));
        assert_eq!(size_matches(&file, Some(5)), None);
        assert_eq!(size_matches(&file, None), Some(4));
        assert_eq!(size_matches(&dir.path().join("missing"), None), None);
    }

    #[test]
    fn sha256_of_a_file_is_the_well_known_digest() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("abc");
        std::fs::write(&file, b"abc").unwrap();
        assert_eq!(
            sha256_file(&file).unwrap(),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
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
