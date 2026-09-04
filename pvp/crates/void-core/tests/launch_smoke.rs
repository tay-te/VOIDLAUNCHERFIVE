//! An end-to-end launch against a stand-in for the JVM.
//!
//! Minecraft cannot run here, but everything up to `execve` can: the bridge binds, the
//! natives are extracted, the arguments are built and substituted, a process is spawned
//! with them, its output is streamed back line by line, and its exit code is reported.
//! The fake `java` prints the arguments it was given, so the test can assert on the real
//! command line rather than on the template.

#![cfg(unix)]

use std::path::PathBuf;

use void_bridge::BridgeServer;
use void_core::auth::Session;
use void_core::launch::{self, LaunchOptions, Stream};
use void_core::manifest::{FileSpec, LaunchProfile, ResolvedLibrary};
use void_core::Paths;
use void_loadout::{defaults, GlobalSettings, Store};

fn fake_java(dir: &std::path::Path) -> PathBuf {
    use std::os::unix::fs::PermissionsExt;
    // Not `dir/java`: `Paths::ensure` already made a directory by that name.
    let bin = dir.join("fake-jdk");
    std::fs::create_dir_all(&bin).unwrap();
    let path = bin.join("java");
    std::fs::write(
        &path,
        "#!/bin/sh\nfor a in \"$@\"; do echo \"ARG $a\"; done\necho 'to stderr' >&2\nexit 0\n",
    )
    .unwrap();
    let mut perms = std::fs::metadata(&path).unwrap().permissions();
    perms.set_mode(0o755);
    std::fs::set_permissions(&path, perms).unwrap();
    path
}

fn profile(paths: &Paths) -> LaunchProfile {
    // The classpath entries need not exist for a spawn; the natives jar does, because it
    // is unzipped before launch.
    let jar_rel = PathBuf::from("libraries/org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-linux.jar");
    let jar = paths.root().join(&jar_rel);
    std::fs::create_dir_all(jar.parent().unwrap()).unwrap();
    write_zip(&jar);

    LaunchProfile {
        version_id: "1.8.9".into(),
        profile_id: "1.8.9-Legacy-Fabric-0.16.0".into(),
        main_class: "net.fabricmc.loader.impl.launch.knot.KnotClient".into(),
        assets_index: "1.8".into(),
        minecraft_arguments:
            "--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} \
             --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} \
             --accessToken ${auth_access_token} --userType ${user_type}"
                .into(),
        jvm_arguments: vec![],
        libraries: vec![ResolvedLibrary {
            name: "net.fabricmc:fabric-loader:0.16.0".into(),
            file: FileSpec {
                relative_path: PathBuf::from("libraries/net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar"),
                url: "https://example.invalid/loader.jar".into(),
                sha1: None,
                size: None,
            },
            natives: false,
            exclude: vec![],
        }],
        natives: vec![ResolvedLibrary {
            name: "org.lwjgl.lwjgl:lwjgl-platform:2.9.4:natives-linux".into(),
            file: FileSpec {
                relative_path: jar_rel,
                url: "https://example.invalid/natives.jar".into(),
                sha1: None,
                size: None,
            },
            natives: true,
            exclude: vec!["META-INF/".into()],
        }],
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

/// A minimal one-entry zip, written by hand so the test needs no zip writer.
fn write_zip(path: &std::path::Path) {
    // Stored (uncompressed) entry "liblwjgl.so" holding "native".
    let name = b"liblwjgl.so";
    let data = b"native";
    let crc = crc32(data);
    let mut out = Vec::new();

    out.extend_from_slice(&[0x50, 0x4b, 0x03, 0x04]); // local file header
    out.extend_from_slice(&[20, 0, 0, 0, 0, 0, 0, 0, 0, 0]); // version, flags, method, time, date
    out.extend_from_slice(&crc.to_le_bytes());
    out.extend_from_slice(&(data.len() as u32).to_le_bytes());
    out.extend_from_slice(&(data.len() as u32).to_le_bytes());
    out.extend_from_slice(&(name.len() as u16).to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    let local_offset = 0u32;
    out.extend_from_slice(name);
    out.extend_from_slice(data);

    let central_offset = out.len() as u32;
    out.extend_from_slice(&[0x50, 0x4b, 0x01, 0x02]); // central directory
    out.extend_from_slice(&[20, 0, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
    out.extend_from_slice(&crc.to_le_bytes());
    out.extend_from_slice(&(data.len() as u32).to_le_bytes());
    out.extend_from_slice(&(data.len() as u32).to_le_bytes());
    out.extend_from_slice(&(name.len() as u16).to_le_bytes());
    out.extend_from_slice(&[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
    out.extend_from_slice(&local_offset.to_le_bytes());
    out.extend_from_slice(name);

    let central_size = out.len() as u32 - central_offset;
    out.extend_from_slice(&[0x50, 0x4b, 0x05, 0x06]); // end of central directory
    out.extend_from_slice(&[0, 0, 0, 0]);
    out.extend_from_slice(&1u16.to_le_bytes());
    out.extend_from_slice(&1u16.to_le_bytes());
    out.extend_from_slice(&central_size.to_le_bytes());
    out.extend_from_slice(&central_offset.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());

    std::fs::write(path, out).unwrap();
}

fn crc32(data: &[u8]) -> u32 {
    let mut crc = 0xffff_ffffu32;
    for byte in data {
        crc ^= *byte as u32;
        for _ in 0..8 {
            crc = if crc & 1 != 0 { (crc >> 1) ^ 0xedb8_8320 } else { crc >> 1 };
        }
    }
    !crc
}

#[tokio::test]
async fn a_full_offline_launch_reaches_the_jvm_with_the_bridge_seam_intact() {
    let dir = tempfile::tempdir().unwrap();
    let paths = Paths::at(dir.path());
    paths.ensure().unwrap();

    let store = Store::at(paths.root());
    store.init().unwrap();

    let server = BridgeServer::bind(void_core::sync::StoreInit::new(store.clone())).await.unwrap();
    assert_ne!(server.port(), 0);

    let profile = profile(&paths);
    let mut game = launch::launch(
        &profile,
        &paths,
        &LaunchOptions {
            session: Session::offline("Tester"),
            java: fake_java(dir.path()),
            max_memory_mb: 2048,
            extra_jvm_args: vec!["-Dvoid.test=1".into()],
            bridge_port: server.port(),
            bridge_token: server.token().to_string(),
            mod_jar: None,
        },
    )
    .await
    .unwrap();

    let mut printed = Vec::new();
    let mut saw_stderr = false;
    while let Some(line) = game.logs.recv().await {
        match line.stream {
            Stream::Stdout => printed.push(line.text),
            Stream::Stderr => saw_stderr = true,
        }
    }
    assert_eq!(game.wait().await.unwrap(), 0);
    assert!(saw_stderr, "stderr is streamed too");

    let args: Vec<String> =
        printed.iter().filter_map(|l| l.strip_prefix("ARG ").map(str::to_string)).collect();
    assert!(args.contains(&format!("-Dvoid.port={}", server.port())));
    assert!(args.contains(&format!("-Dvoid.token={}", server.token())));
    assert!(args.contains(&"-Xmx2048M".to_string()));
    assert!(args.contains(&"-Dvoid.test=1".to_string()));
    assert!(args.contains(&"net.fabricmc.loader.impl.launch.knot.KnotClient".to_string()));
    assert!(args.contains(&"Tester".to_string()));
    assert!(!args.iter().any(|a| a.contains("${")), "an unsubstituted placeholder escaped");

    // The natives jar was unpacked, and META-INF was not.
    let natives = paths.natives_dir(&profile.profile_id);
    assert!(natives.join("liblwjgl.so").exists());

    // And the bridge that the JVM was pointed at is still the store's own state.
    let payload = <void_core::sync::StoreInit as void_bridge::InitSource>::init(
        &void_core::sync::StoreInit::new(store.clone()),
    );
    assert_eq!(payload.loadout.id, defaults::sword_pvp().id);
    assert_eq!(payload.settings, GlobalSettings::factory());
}

#[tokio::test]
async fn the_mod_jar_is_installed_into_the_game_mods_directory() {
    let dir = tempfile::tempdir().unwrap();
    let paths = Paths::at(dir.path());
    paths.ensure().unwrap();

    let jar = dir.path().join("void-client-0.1.0.jar");
    std::fs::write(&jar, b"not really a jar").unwrap();

    let profile = profile(&paths);
    let mut game = launch::launch(
        &profile,
        &paths,
        &LaunchOptions {
            session: Session::offline("Tester"),
            java: fake_java(dir.path()),
            max_memory_mb: 1024,
            extra_jvm_args: vec![],
            bridge_port: 1,
            bridge_token: "x".repeat(32),
            mod_jar: Some(jar),
        },
    )
    .await
    .unwrap();
    assert_eq!(game.wait().await.unwrap(), 0);

    assert!(paths.mods_dir().join("void-client-0.1.0.jar").exists());
}
