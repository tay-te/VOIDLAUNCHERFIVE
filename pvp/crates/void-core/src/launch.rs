//! Building the JVM command line and spawning the game.
//!
//! Three things happen here that nothing else does:
//!
//! - **Natives.** LWJGL 2 loads `.dll`/`.dylib`/`.so` files from a directory, not from
//!   the classpath, so the natives jars are unzipped into `natives/<version>/` and that
//!   directory is handed over as `-Djava.library.path`.
//! - **`-Dvoid.port` / `-Dvoid.token`.** The seam of CONTRACTS.md: the mod reads both
//!   system properties in `net/` and connects back to [`void_bridge`].
//! - **The argument cache.** Everything except the per-spawn values is cached under
//!   `cache/args/<hash>.json`, keyed by the profile hash (§12), and the per-spawn values
//!   are substituted into the template at launch.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::process::Stdio;

use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;
use tokio::sync::mpsc;

use crate::auth::Session;
use crate::error::{Error, Result};
use crate::manifest::{LaunchProfile, Os};
use crate::paths::Paths;

/// The brand string the game reports; shows up in crash reports and F3.
pub const LAUNCHER_BRAND: &str = "void-pvp";

/// Where a log line came from.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Stream {
    /// The game's stdout.
    Stdout,
    /// The game's stderr.
    Stderr,
}

/// One line of game output.
#[derive(Debug, Clone, PartialEq)]
pub struct LogLine {
    /// Which stream it came from.
    pub stream: Stream,
    /// The line, without its newline.
    pub text: String,
}

/// Everything that varies per spawn.
#[derive(Debug, Clone)]
pub struct LaunchOptions {
    /// Who is playing.
    pub session: Session,
    /// The `java` executable to run.
    pub java: PathBuf,
    /// Maximum heap in megabytes.
    pub max_memory_mb: u32,
    /// Extra JVM arguments from `config.json`.
    pub extra_jvm_args: Vec<String>,
    /// The bridge port, for `-Dvoid.port`.
    pub bridge_port: u16,
    /// The bridge session token, for `-Dvoid.token`.
    pub bridge_token: String,
    /// The `void-client` JAR to install into `mods/`, if any.
    pub mod_jar: Option<PathBuf>,
}

/// The classpath, in order: libraries first, then the client jar.
///
/// Libraries are already de-duplicated by `group:artifact` in [`LaunchProfile`], so the
/// loader's copy of a library shadows the vanilla one rather than racing it.
pub fn build_classpath(profile: &LaunchProfile, paths: &Paths) -> Vec<PathBuf> {
    let mut entries: Vec<PathBuf> =
        profile.libraries.iter().map(|l| paths.root().join(&l.file.relative_path)).collect();
    entries.push(paths.root().join(&profile.client.relative_path));
    entries
}

/// Joins classpath entries with the platform separator.
pub fn classpath_string(entries: &[PathBuf]) -> String {
    let sep = if cfg!(windows) { ";" } else { ":" };
    entries.iter().map(|p| p.display().to_string()).collect::<Vec<_>>().join(sep)
}

/// Unpacks every natives jar into `natives/<profile id>/`, returning that directory.
///
/// The directory is emptied first: a stale native from a previous loader build is the
/// kind of thing that produces an `UnsatisfiedLinkError` twenty minutes later.
pub fn extract_natives(profile: &LaunchProfile, paths: &Paths) -> Result<PathBuf> {
    let dir = paths.natives_dir(&profile.profile_id);
    if dir.exists() {
        std::fs::remove_dir_all(&dir).map_err(|e| Error::io(&dir, e))?;
    }
    std::fs::create_dir_all(&dir).map_err(|e| Error::io(&dir, e))?;

    for lib in &profile.natives {
        let jar = paths.root().join(&lib.file.relative_path);
        if !jar.exists() {
            return Err(Error::Manifest(format!(
                "natives jar {} was not downloaded",
                jar.display()
            )));
        }
        let written = crate::archive::extract_zip(&jar, &dir, &lib.exclude)?;
        tracing::debug!(jar = %lib.name, files = written.len(), "extracted natives");
    }
    Ok(dir)
}

/// Copies the `void-client` JAR into the game's `mods/` directory.
///
/// Legacy Fabric loads every jar in that directory; the name is kept so an upgrade
/// replaces rather than duplicates.
pub fn install_mod_jar(paths: &Paths, jar: &Path) -> Result<PathBuf> {
    if !jar.exists() {
        return Err(Error::io(jar, std::io::Error::new(std::io::ErrorKind::NotFound, "no such mod jar")));
    }
    let mods = paths.mods_dir();
    std::fs::create_dir_all(&mods).map_err(|e| Error::io(&mods, e))?;
    let name = jar
        .file_name()
        .ok_or_else(|| Error::Manifest(format!("{} has no file name", jar.display())))?;
    let dest = mods.join(name);
    if dest != jar {
        std::fs::copy(jar, &dest).map_err(|e| Error::io(&dest, e))?;
    }
    Ok(dest)
}

/// The argument list with `${...}` placeholders still in it.
///
/// Cached on disk, because building it means walking the whole library list and the
/// result only changes when the profile does (§12).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ArgTemplate {
    /// Which profile this was built for; a cache hit is only valid for the same one.
    pub profile_hash: String,
    /// The arguments, JVM first, then the main class, then the game arguments.
    pub args: Vec<String>,
}

/// Builds the argument template for a profile.
pub fn build_arg_template(
    profile: &LaunchProfile,
    paths: &Paths,
    natives_dir: &Path,
    os: Os,
    max_memory_mb: u32,
    extra_jvm_args: &[String],
) -> ArgTemplate {
    let mut args: Vec<String> = Vec::new();

    args.push(format!("-Xmx{max_memory_mb}M"));
    args.push(format!("-Xms{}M", (max_memory_mb / 4).clamp(256, 1024)));
    args.push(format!("-Djava.library.path={}", natives_dir.display()));
    args.push(format!("-Dorg.lwjgl.librarypath={}", natives_dir.display()));
    args.push(format!("-Dminecraft.launcher.brand={LAUNCHER_BRAND}"));
    args.push(format!("-Dminecraft.launcher.version={}", env!("CARGO_PKG_VERSION")));

    // The CONTRACTS.md seam: how the mod finds the bridge.
    args.push("-Dvoid.port=${void_port}".to_string());
    args.push("-Dvoid.token=${void_token}".to_string());

    if os == Os::Osx {
        // LWJGL 2 drives AppKit from the main thread; without this the window never opens.
        args.push("-XstartOnFirstThread".to_string());
        args.push("-Xdock:name=Minecraft".to_string());
    }

    args.extend(profile.jvm_arguments.iter().cloned());
    args.extend(extra_jvm_args.iter().cloned());

    args.push("-cp".to_string());
    args.push(classpath_string(&build_classpath(profile, paths)));
    args.push(profile.main_class.clone());

    // 1.8.9's flat `minecraftArguments` template, split on whitespace: no argument in it
    // contains a space before substitution.
    args.extend(profile.minecraft_arguments.split_whitespace().map(str::to_string));

    ArgTemplate { profile_hash: profile.hash(), args }
}

/// Builds the template, reusing `cache/args/<hash>.json` when it is still valid.
pub fn cached_arg_template(
    profile: &LaunchProfile,
    paths: &Paths,
    natives_dir: &Path,
    os: Os,
    max_memory_mb: u32,
    extra_jvm_args: &[String],
) -> Result<ArgTemplate> {
    let key = cache_key(profile, natives_dir, max_memory_mb, extra_jvm_args);
    let file = paths.args_cache_dir().join(format!("{key}.json"));

    if let Ok(text) = std::fs::read_to_string(&file) {
        match serde_json::from_str::<ArgTemplate>(&text) {
            Ok(cached) if cached.profile_hash == profile.hash() => {
                tracing::debug!(path = %file.display(), "reusing cached JVM arguments");
                return Ok(cached);
            }
            Ok(_) => tracing::debug!("cached JVM arguments are for another profile"),
            Err(e) => tracing::warn!(error = %e, "ignoring an unreadable argument cache"),
        }
    }

    let template =
        build_arg_template(profile, paths, natives_dir, os, max_memory_mb, extra_jvm_args);
    if let Some(dir) = file.parent() {
        std::fs::create_dir_all(dir).map_err(|e| Error::io(dir, e))?;
    }
    match serde_json::to_string_pretty(&template) {
        Ok(text) => {
            if let Err(e) = std::fs::write(&file, text) {
                tracing::warn!(error = %e, "could not write the argument cache");
            }
        }
        Err(e) => tracing::warn!(error = %e, "could not serialize the argument cache"),
    }
    Ok(template)
}

fn cache_key(
    profile: &LaunchProfile,
    natives_dir: &Path,
    max_memory_mb: u32,
    extra_jvm_args: &[String],
) -> String {
    use sha1::{Digest, Sha1};
    let mut hasher = Sha1::new();
    hasher.update(profile.hash().as_bytes());
    hasher.update(natives_dir.display().to_string().as_bytes());
    hasher.update(max_memory_mb.to_le_bytes());
    for arg in extra_jvm_args {
        hasher.update(arg.as_bytes());
        hasher.update([0]);
    }
    hex::encode(hasher.finalize())[..16].to_string()
}

/// The values substituted into the template at spawn time.
pub fn launch_variables(
    profile: &LaunchProfile,
    paths: &Paths,
    options: &LaunchOptions,
) -> BTreeMap<String, String> {
    let mut vars = BTreeMap::new();
    vars.insert("auth_player_name".into(), options.session.username.clone());
    vars.insert("version_name".into(), profile.version_id.clone());
    vars.insert("game_directory".into(), paths.game_dir().display().to_string());
    vars.insert("assets_root".into(), paths.assets_dir().display().to_string());
    vars.insert("game_assets".into(), paths.assets_dir().display().to_string());
    vars.insert("assets_index_name".into(), profile.assets_index.clone());
    vars.insert("auth_uuid".into(), options.session.uuid.clone());
    vars.insert("auth_access_token".into(), options.session.access_token.clone());
    vars.insert("auth_session".into(), format!("token:{}", options.session.access_token));
    vars.insert("user_type".into(), options.session.user_type.clone());
    vars.insert("user_properties".into(), "{}".into());
    vars.insert("version_type".into(), LAUNCHER_BRAND.into());
    vars.insert("void_port".into(), options.bridge_port.to_string());
    vars.insert("void_token".into(), options.bridge_token.clone());
    vars
}

/// Replaces every `${name}` in `args` with its value, leaving unknown ones alone.
pub fn substitute(args: &[String], vars: &BTreeMap<String, String>) -> Vec<String> {
    args.iter()
        .map(|arg| {
            let mut out = arg.clone();
            for (key, value) in vars {
                let needle = format!("${{{key}}}");
                if out.contains(&needle) {
                    out = out.replace(&needle, value);
                }
            }
            out
        })
        .collect()
}

/// A running game.
pub struct GameProcess {
    child: tokio::process::Child,
    /// Every line the game writes to stdout or stderr.
    pub logs: mpsc::Receiver<LogLine>,
    /// The full argument list, for the log view and for bug reports.
    pub args: Vec<String>,
}

impl GameProcess {
    /// Waits for the game to exit, returning its exit code.
    pub async fn wait(&mut self) -> Result<i32> {
        let status = self.child.wait().await?;
        Ok(status.code().unwrap_or(-1))
    }

    /// Asks the game to stop.
    pub async fn kill(&mut self) -> Result<()> {
        self.child.kill().await?;
        Ok(())
    }

    /// The OS process id, while the game is running.
    pub fn pid(&self) -> Option<u32> {
        self.child.id()
    }
}

/// Extracts natives, installs the mod jar, builds the arguments and spawns the JVM.
pub async fn launch(
    profile: &LaunchProfile,
    paths: &Paths,
    options: &LaunchOptions,
) -> Result<GameProcess> {
    paths.ensure()?;
    let natives = extract_natives(profile, paths)?;
    if let Some(jar) = &options.mod_jar {
        let installed = install_mod_jar(paths, jar)?;
        tracing::info!(path = %installed.display(), "installed the void-client mod");
    }

    let template = cached_arg_template(
        profile,
        paths,
        &natives,
        Os::host()?,
        options.max_memory_mb,
        &options.extra_jvm_args,
    )?;
    let args = substitute(&template.args, &launch_variables(profile, paths, options));

    tracing::info!(
        java = %options.java.display(),
        port = options.bridge_port,
        "spawning Minecraft {}",
        profile.version_id
    );

    let mut child = Command::new(&options.java)
        .args(&args)
        .current_dir(paths.game_dir())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .stdin(Stdio::null())
        .kill_on_drop(false)
        .spawn()
        .map_err(|e| Error::io(&options.java, e))?;

    let (tx, logs) = mpsc::channel(1024);
    if let Some(stdout) = child.stdout.take() {
        pump(BufReader::new(stdout), Stream::Stdout, tx.clone());
    }
    if let Some(stderr) = child.stderr.take() {
        pump(BufReader::new(stderr), Stream::Stderr, tx);
    }

    Ok(GameProcess { child, logs, args })
}

fn pump<R>(reader: BufReader<R>, stream: Stream, tx: mpsc::Sender<LogLine>)
where
    R: tokio::io::AsyncRead + Unpin + Send + 'static,
{
    tokio::spawn(async move {
        let mut lines = reader.lines();
        while let Ok(Some(text)) = lines.next_line().await {
            if tx.send(LogLine { stream, text }).await.is_err() {
                break; // nobody is reading the logs any more
            }
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::manifest::{FileSpec, ResolvedLibrary};

    fn profile() -> LaunchProfile {
        LaunchProfile {
            version_id: "1.8.9".into(),
            profile_id: "1.8.9-legacyfabric-0.16.0".into(),
            main_class: "net.fabricmc.loader.impl.launch.knot.KnotClient".into(),
            assets_index: "1.8".into(),
            minecraft_arguments:
                "--username ${auth_player_name} --version ${version_name} --gameDir \
                 ${game_directory} --assetsDir ${assets_root} --assetIndex \
                 ${assets_index_name} --uuid ${auth_uuid} --accessToken \
                 ${auth_access_token} --userType ${user_type}"
                    .into(),
            jvm_arguments: vec![],
            libraries: vec![ResolvedLibrary {
                name: "org.lwjgl.lwjgl:lwjgl:2.9.4".into(),
                file: FileSpec {
                    relative_path: PathBuf::from("libraries/org/lwjgl/lwjgl/lwjgl/2.9.4/lwjgl-2.9.4.jar"),
                    url: "https://example.invalid/lwjgl.jar".into(),
                    sha1: None,
                    size: None,
                },
                natives: false,
                exclude: vec![],
            }],
            natives: vec![],
            client: FileSpec {
                relative_path: PathBuf::from("versions/1.8.9/1.8.9.jar"),
                url: "https://example.invalid/client.jar".into(),
                sha1: None,
                size: None,
            },
            asset_index: FileSpec {
                relative_path: PathBuf::from("assets/indexes/1.8.json"),
                url: "https://example.invalid/1.8.json".into(),
                sha1: None,
                size: None,
            },
        }
    }

    #[test]
    fn the_classpath_is_libraries_then_the_client_jar() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let cp = build_classpath(&profile(), &paths);
        assert_eq!(cp.len(), 2);
        assert!(cp[0].ends_with("lwjgl-2.9.4.jar"));
        assert!(cp[1].ends_with("1.8.9.jar"), "the client jar goes last");

        let joined = classpath_string(&cp);
        let sep = if cfg!(windows) { ';' } else { ':' };
        assert_eq!(joined.matches(sep).count(), 1);
    }

    #[test]
    fn the_bridge_seam_is_in_the_jvm_arguments() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let natives = paths.natives_dir("1.8.9-legacyfabric-0.16.0");
        let t = build_arg_template(&profile(), &paths, &natives, Os::Linux, 2048, &[]);

        assert!(t.args.contains(&"-Xmx2048M".to_string()));
        assert!(t.args.contains(&"-Dvoid.port=${void_port}".to_string()));
        assert!(t.args.contains(&"-Dvoid.token=${void_token}".to_string()));
        assert!(t
            .args
            .iter()
            .any(|a| a.starts_with("-Djava.library.path=") && a.contains("natives")));
        // The main class sits between the JVM arguments and the game arguments.
        let main = t.args.iter().position(|a| a.contains("KnotClient")).unwrap();
        assert_eq!(t.args[main - 2], "-cp");
        assert_eq!(t.args[main + 1], "--username");
    }

    #[test]
    fn macos_gets_the_main_thread_flag_and_others_do_not() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let natives = paths.natives_dir("x");
        for (os, expected) in [(Os::Osx, true), (Os::Windows, false), (Os::Linux, false)] {
            let t = build_arg_template(&profile(), &paths, &natives, os, 2048, &[]);
            assert_eq!(
                t.args.contains(&"-XstartOnFirstThread".to_string()),
                expected,
                "{os:?} — LWJGL 2 needs AppKit on the main thread, but only on macOS"
            );
        }
    }

    #[test]
    fn substitution_fills_every_placeholder() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        let profile = profile();
        let natives = paths.natives_dir(&profile.profile_id);
        let template = build_arg_template(&profile, &paths, &natives, Os::Linux, 2048, &[]);

        let options = LaunchOptions {
            session: Session::offline("Tester"),
            java: PathBuf::from("java"),
            max_memory_mb: 2048,
            extra_jvm_args: vec![],
            bridge_port: 51234,
            bridge_token: "cafebabe".repeat(8),
            mod_jar: None,
        };
        let args = substitute(&template.args, &launch_variables(&profile, &paths, &options));

        assert!(!args.iter().any(|a| a.contains("${")), "left a placeholder: {args:?}");
        assert!(args.contains(&"-Dvoid.port=51234".to_string()));
        assert!(args.contains(&format!("-Dvoid.token={}", "cafebabe".repeat(8))));
        let name = args.iter().position(|a| a == "--username").unwrap();
        assert_eq!(args[name + 1], "Tester");
        let uuid = args.iter().position(|a| a == "--uuid").unwrap();
        assert_eq!(args[uuid + 1], crate::auth::offline_uuid("Tester"));
    }

    #[test]
    fn the_argument_cache_hits_for_the_same_profile_and_misses_for_another() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::at(dir.path());
        paths.ensure().unwrap();
        let profile = profile();
        let natives = paths.natives_dir(&profile.profile_id);

        let first = cached_arg_template(&profile, &paths, &natives, Os::Linux, 2048, &[]).unwrap();
        let second = cached_arg_template(&profile, &paths, &natives, Os::Linux, 2048, &[]).unwrap();
        assert_eq!(first, second);
        assert_eq!(std::fs::read_dir(paths.args_cache_dir()).unwrap().count(), 1);

        // A different heap is a different command line, so a different cache entry.
        let bigger = cached_arg_template(&profile, &paths, &natives, Os::Linux, 4096, &[]).unwrap();
        assert_ne!(first.args, bigger.args);
        assert_eq!(std::fs::read_dir(paths.args_cache_dir()).unwrap().count(), 2);

        // A changed profile invalidates the entry even at the same key.
        let mut changed = profile.clone();
        changed.main_class = "other.Main".into();
        assert_ne!(changed.hash(), profile.hash());
    }
}
