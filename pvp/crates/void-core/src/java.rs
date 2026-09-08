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
    /// The JVM's own `os.arch`, e.g. `x86_64` or `aarch64`, when it would say.
    ///
    /// The JVM's architecture, not the host's: an x64 JVM under Rosetta on an arm64 Mac
    /// reports `x86_64`, which is exactly the distinction that matters here.
    pub arch: Option<String>,
}

impl JavaInstall {
    /// Whether this is the Java 8 that 1.8.9 needs.
    pub fn is_java8(&self) -> bool {
        self.major == 8
    }

    /// Whether this JVM can load LWJGL 2's natives.
    ///
    /// **On macOS this is a real filter, not a formality.** LWJGL 2 ships no arm64 natives
    /// (§13), and a JVM that does not match the natives cannot load them — so an arm64
    /// Java 8, which Apple Silicon has had since 8u302 and which is what a developer's own
    /// `/Library/Java/JavaVirtualMachines` is most likely to hold, launches 1.8.9 into a
    /// window that never composites: 0x0, `BackgroundOnly`, no error. The process runs, the
    /// log scrolls, and there is nothing on screen. [`adoptium_arch`] already knew this and
    /// fetched x64 accordingly; detection did not, and picked whichever Java 8 `read_dir`
    /// happened to return first.
    ///
    /// Unknown arch is treated as usable: this must not reject a working JVM because it
    /// declined to print a property.
    pub fn runs_lwjgl2(&self) -> bool {
        if !cfg!(target_os = "macos") {
            return true;
        }
        match self.arch.as_deref() {
            Some(arch) => matches!(arch, "x86_64" | "amd64" | "x64"),
            None => true,
        }
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
///
/// **The quotes are load-bearing, not decoration.** [`probe`] asks for
/// `-XshowSettings:properties` in the same run, and that dump opens with a dozen
/// unquoted lines that contain the word "version" — `java.class.version = 52.0` is the
/// first of them. Matching on "version" alone finds that line, comes away with no
/// quoted token, and answers `None` for a JVM that is perfectly fine. Every probe on the
/// machine then fails, detection finds no Java 8 at all, and the launcher quietly starts
/// downloading one it already has.
pub fn parse_version(banner: &str) -> Option<(String, u32)> {
    banner
        .lines()
        .filter(|l| l.contains("version"))
        .find_map(|l| {
            let quoted = l.split('"').nth(1)?;
            let major = if let Some(rest) = quoted.strip_prefix("1.") {
                rest.split(['.', '_']).next()?.parse().ok()?
            } else {
                quoted.split(['.', '_', '-']).next()?.parse().ok()?
            };
            Some((quoted.to_string(), major))
        })
}

/// Reads `os.arch` out of a `-XshowSettings:properties` dump.
pub fn parse_arch(banner: &str) -> Option<String> {
    banner
        .lines()
        .find_map(|l| l.trim().strip_prefix("os.arch"))
        .and_then(|rest| rest.split('=').nth(1))
        .map(|v| v.trim().to_string())
        .filter(|v| !v.is_empty())
}

/// Runs the JVM once and reports what it is, or `None` if it will not run.
///
/// `-XshowSettings:properties` rides along with `-version` so the architecture costs no
/// extra process. Both streams are read because the two have swapped between JDK
/// versions before, and a probe that reads the wrong one silently learns nothing.
pub fn probe(java: &Path) -> Option<JavaInstall> {
    let output = Command::new(java).args(["-XshowSettings:properties", "-version"]).output().ok()?;
    let mut banner = String::from_utf8_lossy(&output.stderr).into_owned();
    banner.push('\n');
    banner.push_str(&String::from_utf8_lossy(&output.stdout));
    let (version, major) = parse_version(&banner)?;
    Some(JavaInstall { path: java.to_path_buf(), version, major, arch: parse_arch(&banner) })
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
///
/// **Two passes, and the second one is a last resort that warns.** A Java 8 that cannot
/// load LWJGL 2's natives ([`JavaInstall::runs_lwjgl2`]) launches the game into an
/// invisible window with no error anywhere, so it must never be preferred over one that
/// can — and `read_dir` order is not a preference. It is still returned if it is the only
/// Java 8 on the machine, because refusing to launch at all is worse than launching into
/// a failure the log now names.
pub fn detect_java8(paths: &Paths) -> Option<JavaInstall> {
    let mut fallback: Option<JavaInstall> = None;

    let consider = |install: JavaInstall, fallback: &mut Option<JavaInstall>| {
        if !install.is_java8() {
            tracing::debug!(path = %install.path.display(), version = %install.version, "not Java 8");
            return None;
        }
        if install.runs_lwjgl2() {
            tracing::info!(
                path = %install.path.display(),
                version = %install.version,
                arch = install.arch.as_deref().unwrap_or("unknown"),
                "found Java 8"
            );
            return Some(install);
        }
        tracing::debug!(
            path = %install.path.display(),
            arch = install.arch.as_deref().unwrap_or("unknown"),
            "Java 8, but the wrong architecture for LWJGL 2; keeping looking"
        );
        fallback.get_or_insert(install);
        None
    };

    for root in candidate_roots(paths) {
        if let Some(binary) = java_binary_in(&root) {
            if let Some(install) = probe(&binary) {
                if let Some(found) = consider(install, &mut fallback) {
                    return Some(found);
                }
            }
        }
    }
    // Last resort: whatever `java` is on PATH.
    if let Some(install) = probe(Path::new(java_exe())) {
        if let Some(found) = consider(install, &mut fallback) {
            return Some(found);
        }
    }

    if let Some(only) = &fallback {
        tracing::warn!(
            path = %only.path.display(),
            arch = only.arch.as_deref().unwrap_or("unknown"),
            "the only Java 8 on this machine cannot load LWJGL 2's natives; 1.8.9 will \
             launch into a window that never appears. Install an x64 Java 8, or let the \
             launcher fetch one"
        );
    }
    fallback
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

    /// A real `-XshowSettings:properties -version` dump, trimmed to the lines that matter.
    ///
    /// The order is the JVM's, not ours: every unquoted `*.version` property comes before
    /// the quoted banner, which is exactly what broke `parse_version`.
    const PROPERTIES_DUMP: &str = concat!(
        "Property settings:\n",
        "    java.class.version = 52.0\n",
        "    java.runtime.version = 1.8.0_202-b08\n",
        "    java.specification.version = 1.8\n",
        "    java.version = 1.8.0_202\n",
        "    os.arch = x86_64\n",
        "    os.name = Mac OS X\n",
        "\n",
        "java version \"1.8.0_202\"\n",
        "Java(TM) SE Runtime Environment (build 1.8.0_202-b08)\n",
    );

    #[test]
    fn the_properties_dump_does_not_hide_the_version_banner() {
        // `java.class.version = 52.0` is the first line containing "version" and carries no
        // quotes. Stopping there answers None, every probe on the machine fails, and the
        // launcher downloads a JVM it already has — which is what it did.
        assert_eq!(parse_version(PROPERTIES_DUMP), Some(("1.8.0_202".into(), 8)));
    }

    #[test]
    fn the_arch_comes_out_of_the_same_dump() {
        assert_eq!(parse_arch(PROPERTIES_DUMP).as_deref(), Some("x86_64"));
        assert_eq!(parse_arch("java version \"1.8.0_202\""), None, "a bare banner says nothing");
    }

    #[test]
    fn only_an_x64_jvm_can_run_lwjgl2_on_macos() {
        let at = |arch: Option<&str>| JavaInstall {
            path: PathBuf::from("java"),
            version: "1.8.0_202".into(),
            major: 8,
            arch: arch.map(str::to_string),
        };
        // The whole reason this exists: an arm64 Java 8 launches 1.8.9 into a window that
        // never composites, with no error anywhere.
        assert_eq!(at(Some("aarch64")).runs_lwjgl2(), !cfg!(target_os = "macos"));
        assert!(at(Some("x86_64")).runs_lwjgl2());
        assert!(at(Some("amd64")).runs_lwjgl2());
        // A JVM that would not say must not be rejected for it.
        assert!(at(None).runs_lwjgl2());
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
