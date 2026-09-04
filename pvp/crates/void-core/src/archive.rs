//! Unpacking the two archive formats a launch runs into: LWJGL natives jars (zip) and
//! Adoptium runtimes (zip on Windows, tar.gz elsewhere).

use std::io::Read;
use std::path::{Component, Path, PathBuf};

use crate::error::{Error, Result};

/// Rejects entries that would escape the destination directory — the "zip slip" bug.
///
/// A natives jar and a JRE tarball both come from a trusted publisher over TLS, but they
/// are still archives from the network being unpacked into the user's home directory.
fn safe_join(dest: &Path, entry: &Path) -> Option<PathBuf> {
    let mut out = dest.to_path_buf();
    for component in entry.components() {
        match component {
            Component::Normal(part) => out.push(part),
            Component::CurDir => {}
            _ => return None,
        }
    }
    out.starts_with(dest).then_some(out)
}

/// Extracts a zip, skipping entries whose path starts with any of `exclude`.
pub fn extract_zip(archive: &Path, dest: &Path, exclude: &[String]) -> Result<Vec<PathBuf>> {
    let file = std::fs::File::open(archive).map_err(|e| Error::io(archive, e))?;
    let mut zip = zip::ZipArchive::new(file).map_err(|e| Error::Archive {
        path: archive.to_path_buf(),
        message: e.to_string(),
    })?;

    let mut written = Vec::new();
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| Error::Archive {
            path: archive.to_path_buf(),
            message: e.to_string(),
        })?;
        let Some(name) = entry.enclosed_name() else {
            tracing::warn!(entry = entry.name(), "skipping an unsafe archive entry");
            continue;
        };
        let name_str = name.to_string_lossy().replace('\\', "/");
        if exclude.iter().any(|prefix| name_str.starts_with(prefix.trim_end_matches('/'))) {
            continue;
        }
        if entry.is_dir() {
            continue;
        }
        let Some(out_path) = safe_join(dest, &name) else {
            tracing::warn!(entry = %name_str, "skipping an archive entry that escapes the destination");
            continue;
        };
        if let Some(parent) = out_path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| Error::io(parent, e))?;
        }
        let mut bytes = Vec::with_capacity(entry.size() as usize);
        entry.read_to_end(&mut bytes).map_err(|e| Error::io(&out_path, e))?;
        std::fs::write(&out_path, &bytes).map_err(|e| Error::io(&out_path, e))?;
        written.push(out_path);
    }
    Ok(written)
}

/// Extracts a gzipped tar, preserving the executable bit that a JRE's `bin/java` needs.
pub fn extract_tar_gz(archive: &Path, dest: &Path) -> Result<()> {
    let file = std::fs::File::open(archive).map_err(|e| Error::io(archive, e))?;
    let decoder = flate2::read::GzDecoder::new(file);
    let mut tar = tar::Archive::new(decoder);
    tar.set_preserve_permissions(true);
    std::fs::create_dir_all(dest).map_err(|e| Error::io(dest, e))?;
    tar.unpack(dest).map_err(|e| Error::Archive {
        path: archive.to_path_buf(),
        message: e.to_string(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn escaping_entries_are_refused() {
        let dest = Path::new("/tmp/void");
        assert_eq!(safe_join(dest, Path::new("a/b.so")), Some(dest.join("a").join("b.so")));
        assert_eq!(safe_join(dest, Path::new("../escape")), None);
        assert_eq!(safe_join(dest, Path::new("/etc/passwd")), None);
    }
}
