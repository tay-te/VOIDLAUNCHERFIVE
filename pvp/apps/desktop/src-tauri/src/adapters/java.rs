//! Java 8 detection.
//!
//! TODO(integrate): §12.4 puts "locate a Java 8 runtime or fetch one from Adoptium"
//! in `void-core`. The **fetch** half is not written here — this file only *detects*
//! what is already on the machine, which is enough for `java_status` to tell the
//! Settings screen the truth. When `void_core::java` lands, replace `detect` with a
//! call into it so that "not found" can turn into "downloading Adoptium 8".
//!
//! Detection order matches what the Settings screen shows: an explicitly configured
//! path wins, then a runtime we previously downloaded into the data dir, then
//! whatever `java` resolves to on PATH.

use std::path::{Path, PathBuf};
use std::process::Command;

use crate::models::JavaStatus;

pub fn detect(data_dir: &Path, configured: Option<&str>, auto: bool) -> JavaStatus {
    if !auto {
        if let Some(path) = configured {
            return probe(Path::new(path), "configured");
        }
    }

    let bundled = data_dir
        .join("runtime")
        .join("java8")
        .join("bin")
        .join(exe("java"));
    if bundled.exists() {
        return probe(&bundled, "bundled");
    }

    match which("java") {
        Some(path) => probe(&path, "system"),
        None => JavaStatus {
            found: false,
            path: None,
            version: None,
            source: "missing".into(),
        },
    }
}

fn probe(path: &Path, source: &str) -> JavaStatus {
    let version = Command::new(path)
        .arg("-version")
        .output()
        .ok()
        .and_then(|out| {
            // `java -version` writes to stderr on every JVM ever shipped.
            let text = String::from_utf8_lossy(&out.stderr).to_string();
            parse_version(&text)
        });

    // `found` means "usable for 1.8.9", not merely "a JVM exists": Minecraft 1.8.9
    // will not start on a modern runtime — LWJGL 2 and the Legacy Fabric loader both
    // refuse — so a Java 21 on PATH is a miss, and the Settings screen says so.
    let usable = version.as_deref().is_some_and(is_java_8);
    JavaStatus {
        found: usable,
        path: Some(path.display().to_string()),
        source: if version.is_some() && !usable {
            format!("{source}-wrong-version")
        } else {
            source.to_string()
        },
        version,
    }
}

/// `java -version` prints `openjdk version "1.8.0_412"` or `"17.0.9"`. Pull the quoted
/// string out of whichever line carries it.
pub fn parse_version(output: &str) -> Option<String> {
    for line in output.lines() {
        if line.contains("version") {
            if let Some(start) = line.find('"') {
                if let Some(end) = line[start + 1..].find('"') {
                    return Some(line[start + 1..start + 1 + end].to_string());
                }
            }
        }
    }
    None
}

/// True when the version string names a Java 8 runtime (`1.8.x` or a bare `8`).
pub fn is_java_8(version: &str) -> bool {
    version.starts_with("1.8.") || version == "8" || version.starts_with("8.")
}

fn exe(name: &str) -> String {
    if cfg!(windows) {
        format!("{name}.exe")
    } else {
        name.to_string()
    }
}

/// Minimal `which`: walk PATH looking for an executable entry.
fn which(name: &str) -> Option<PathBuf> {
    let name = exe(name);
    std::env::var_os("PATH").and_then(|paths| {
        std::env::split_paths(&paths)
            .map(|dir| dir.join(&name))
            .find(|p| p.is_file())
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_is_pulled_from_the_quoted_string() {
        assert_eq!(
            parse_version("openjdk version \"1.8.0_412\"\nOpenJDK Runtime Environment"),
            Some("1.8.0_412".into())
        );
        assert_eq!(
            parse_version("java version \"17.0.9\" 2023-10-17 LTS"),
            Some("17.0.9".into())
        );
        assert_eq!(parse_version("no version here"), None);
    }

    #[test]
    fn java_8_is_recognised_and_others_are_not() {
        assert!(is_java_8("1.8.0_412"));
        assert!(is_java_8("8"));
        assert!(!is_java_8("17.0.9"));
        assert!(!is_java_8("21"));
    }

    #[test]
    fn missing_java_reports_missing_rather_than_panicking() {
        let status = detect(Path::new("/nonexistent-void-data"), Some("/nope/java"), true);
        // On a machine with no `java` on PATH this is `missing`; on one with a JVM it
        // is `system`. Either way the call returns and names its source.
        assert!(!status.source.is_empty());
    }
}
