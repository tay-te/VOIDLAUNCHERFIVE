//! Finding, or fetching, a Java 8 runtime.
//!
//! 1.8.9 needs Java 8: LWJGL 2 and the 1.8.9 bytecode both refuse anything newer in
//! practice. [`ensure_java8`] looks in `$JAVA_HOME`, on `PATH`, in the usual per-OS
//! install directories and in our own `java/` directory; failing all of that it fetches
//! an Adoptium Temurin 8 JRE for the host.
//!
//! On Apple Silicon it fetches the **x64** build on purpose (§13): LWJGL 2 has no arm64
//! natives, so 1.8.9 runs under Rosetta and the JVM has to match the natives.

use std::path::{Path, PathBuf};
use std::process::Command;

use crate::download::Progress;
use crate::error::{Error, Result};
use crate::manifest::Os;
use crate::paths::Paths;
use tokio::sync::mpsc;

/// A usable Java runtime.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct JavaInstall {
    /// Path to the `java` executable.
    pub path: PathBuf,
    /// The version string `java -version` printed, e.g. `1.8.0_402`.
    pub version: String,
    /// The major version: `8` for `1.8.0_402`, `21` for `21.0.2`.
    pub major: u32,
}

impl JavaInstall {
    /// Whether this is the Java 8 that 1.8.9 needs.
    pub fn is_java8(&self) -> bool {
        self.major == 8
    }
}

/// The name of the java executable on this platform.
fn java_exe() -> &'static str {
    if cfg!(windows) {
        "java.exe"
    } else {
        "java"
    }
}

/// Parses the `java -version` banner, which goes to **stderr**, not stdout.
///
/// Java 8 prints `java version "1.8.0_402"`; Java 9+ prints `openjdk version "21.0.2"`.
pub fn parse_version(banner: &str) -> Option<(String, u32)> {
    let line = banner.lines().find(|l| l.contains("version"))?;
    let quoted = line.split('"').nth(1)?;
    let major = if let Some(rest) = quoted.strip_prefix("1.") {
        rest.split(['.', '_']).next()?.parse().ok()?
    } else {
        quoted.split(['.', '_', '-']).next()?.parse().ok()?
    };
    Some((quoted.to_string(), major))
}

/// Runs `java -version` and reports what it is, or `None` if it will not run.
pub fn probe(java: &Path) -> Option<JavaInstall> {
    let output = Command::new(java).arg("-version").output().ok()?;
    let banner = String::from_utf8_lossy(&output.stderr);
    let banner = if banner.trim().is_empty() {
        String::from_utf8_lossy(&output.stdout).to_string()
    } else {
        banner.to_string()
    };
    let (version, major) = parse_version(&banner)?;
    Some(JavaInstall { path: java.to_path_buf(), version, major })
}

/// Every place worth looking for a JVM on this host, in preference order.
fn candidate_roots(paths: &Paths) -> Vec<PathBuf> {
    let mut roots: Vec<PathBuf> = Vec::new();

    // Runtimes we fetched ourselves win: they are known to be 8.
    if let Ok(entries) = std::fs::read_dir(paths.java_dir()) {
        for entry in entries.flatten() {
            roots.push(entry.path());
        }
    }
    if let Some(home) = std::env::var_os("JAVA_HOME") {
        roots.push(PathBuf::from(home));
    }
    match Os::host() {
        Ok(Os::Windows) => {
            for base in ["C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium", "C:\\Program Files (x86)\\Java"] {
                if let Ok(entries) = std::fs::read_dir(base) {
                    roots.extend(entries.flatten().map(|e| e.path()));
                }
            }
        }
        Ok(Os::Osx) => {
            if let Ok(entries) = std::fs::read_dir("/Library/Java/JavaVirtualMachines") {
                roots.extend(entries.flatten().map(|e| e.path()));
            }
        }
        Ok(Os::Linux) => {
            for base in ["/usr/lib/jvm", "/usr/java"] {
                if let Ok(entries) = std::fs::read_dir(base) {
                    roots.extend(entries.flatten().map(|e| e.path()));
                }
            }
        }
        Err(_) => {}
    }
    roots
}

/// Turns a JVM *home* into the path of its `java` binary, handling the macOS bundle
/// layout and the case where the caller already pointed at `bin/`.
fn java_binary_in(root: &Path) -> Option<PathBuf> {
    for suffix in [
        PathBuf::from("bin").join(java_exe()),
        PathBuf::from("Contents").join("Home").join("bin").join(java_exe()),
        PathBuf::from("jre").join("bin").join(java_exe()),
        PathBuf::from(java_exe()),
    ] {
        let candidate = root.join(suffix);
        if candidate.is_file() {
            return Some(candidate);
        }
    }
    None
}

/// Looks for a Java 8 runtime without touching the network.
pub fn detect_java8(paths: &Paths) -> Option<JavaInstall> {
    for root in candidate_roots(paths) {
        if let Some(binary) = java_binary_in(&root) {
            if let Some(install) = probe(&binary) {
                if install.is_java8() {
                    tracing::info!(path = %install.path.display(), version = %install.version, "found Java 8");
                    return Some(install);
                }
                tracing::debug!(path = %binary.display(), version = %install.version, "not Java 8");
            }
        }
    }
    // Last resort: whatever `java` is on PATH.
    let on_path = probe(Path::new(java_exe()))?;
    on_path.is_java8().then_some(on_path)
}

/// The Adoptium API URL for a Temurin 8 JRE for this host.
///
/// `os` is `windows`, `mac` or `linux`; `arch` is always `x64` for us — see the module
/// docs on Rosetta.
pub fn adoptium_url(os: Os, arch: &str) -> String {
    let os_name = match os {
        Os::Windows => "windows",
        Os::Osx => "mac",
        Os::Linux => "linux",
    };
    format!(
        "https://api.adoptium.net/v3/binary/latest/8/ga/{os_name}/{arch}/jre/hotspot/normal/eclipse"
    )
}

/// The architecture to ask Adoptium for.
///
/// Apple Silicon gets the x64 build deliberately: 1.8.9 runs under Rosetta because
/// LWJGL 2 has no arm64 natives (§13), and a JVM that does not match the natives cannot
/// load them.
pub fn adoptium_arch(os: Os) -> &'static str {
    match (os, std::env::consts::ARCH) {
        (Os::Osx, _) => "x64",
        (_, "x86") => "x86",
        (_, "aarch64") => "aarch64",
        _ => "x64",
    }
}

/// Returns a Java 8 runtime, downloading Temurin 8 if the host has none.
pub async fn ensure_java8(
    client: &reqwest::Client,
    paths: &Paths,
    progress: Option<mpsc::Sender<Progress>>,
) -> Result<JavaInstall> {
    if let Some(found) = detect_java8(paths) {
        return Ok(found);
    }
    let os = Os::host()?;
    let arch = adoptium_arch(os);
    let url = adoptium_url(os, arch);
    tracing::info!(%url, "no Java 8 on this machine; fetching Temurin 8");

    let dest_root = paths.java_dir().join(format!("temurin8-{}-{arch}", os.key()));
    std::fs::create_dir_all(&dest_root).map_err(|e| Error::io(&dest_root, e))?;

    if let Some(tx) = &progress {
        let _ = tx.send(Progress::Started { files: 1, bytes: 0 }).await;
    }
    let resp = client.get(&url).send().await?;
    let status = resp.status();
    if !status.is_success() {
        return Err(Error::HttpStatus { url, status: status.as_u16(), detail: None });
    }
    let bytes = resp.bytes().await?;
    if let Some(tx) = &progress {
        let _ = tx
            .send(Progress::Finished {
                path: dest_root.clone(),
                bytes: bytes.len() as u64,
                cached: false,
                done: 1,
                total: 1,
            })
            .await;
    }

    // Windows ships a zip, macOS and Linux a tar.gz.
    let archive = dest_root.join(if os == Os::Windows { "jre.zip" } else { "jre.tar.gz" });
    std::fs::write(&archive, &bytes).map_err(|e| Error::io(&archive, e))?;
    if os == Os::Windows {
        crate::archive::extract_zip(&archive, &dest_root, &[])?;
    } else {
        crate::archive::extract_tar_gz(&archive, &dest_root)?;
    }
    let _ = std::fs::remove_file(&archive);

    // The archive unpacks into a single versioned directory.
    let install = std::fs::read_dir(&dest_root)
        .map_err(|e| Error::io(&dest_root, e))?
        .flatten()
        .filter_map(|e| java_binary_in(&e.path()))
        .find_map(|binary| probe(&binary).filter(JavaInstall::is_java8))
        .or_else(|| java_binary_in(&dest_root).and_then(|b| probe(&b)))
        .ok_or_else(|| {
            Error::Java(format!("Temurin unpacked into {} but no java 8 binary was found", dest_root.display()))
        })?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mut perms = std::fs::metadata(&install.path)
            .map_err(|e| Error::io(&install.path, e))?
            .permissions();
        perms.set_mode(0o755);
        let _ = std::fs::set_permissions(&install.path, perms);
    }

    tracing::info!(path = %install.path.display(), version = %install.version, "fetched Java 8");
    Ok(install)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_both_java_version_banners() {
        let eight = "openjdk version \"1.8.0_402\"\nOpenJDK Runtime Environment (Temurin)";
        assert_eq!(parse_version(eight), Some(("1.8.0_402".into(), 8)));

        let modern = "openjdk version \"21.0.2\" 2024-01-16";
        assert_eq!(parse_version(modern), Some(("21.0.2".into(), 21)));

        let oracle = "java version \"1.8.0_202\"";
        assert_eq!(parse_version(oracle), Some(("1.8.0_202".into(), 8)));

        assert_eq!(parse_version("command not found"), None);
    }

    #[test]
    fn apple_silicon_asks_for_the_x64_build() {
        // LWJGL 2 has no arm64 natives, so the JVM has to be x64 under Rosetta (§13).
        assert_eq!(adoptium_arch(Os::Osx), "x64");
        assert!(adoptium_url(Os::Osx, "x64").contains("/mac/x64/jre/"));
        assert!(adoptium_url(Os::Windows, "x64").contains("/windows/x64/jre/"));
    }

    #[test]
    fn java_binary_is_found_in_every_layout() {
        let dir = tempfile::tempdir().unwrap();

        let plain = dir.path().join("plain");
        std::fs::create_dir_all(plain.join("bin")).unwrap();
        std::fs::write(plain.join("bin").join(java_exe()), b"").unwrap();
        assert_eq!(java_binary_in(&plain), Some(plain.join("bin").join(java_exe())));

        let bundle = dir.path().join("Temurin.jdk");
        std::fs::create_dir_all(bundle.join("Contents/Home/bin")).unwrap();
        std::fs::write(bundle.join("Contents/Home/bin").join(java_exe()), b"").unwrap();
        assert!(java_binary_in(&bundle).unwrap().ends_with(
            PathBuf::from("Contents").join("Home").join("bin").join(java_exe())
        ));

        assert_eq!(java_binary_in(&dir.path().join("nothing")), None);
    }
}
