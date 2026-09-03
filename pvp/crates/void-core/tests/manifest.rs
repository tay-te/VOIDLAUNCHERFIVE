//! Manifest parsing, rule evaluation and profile merging, against a vendored 1.8.9
//! version JSON and a Legacy Fabric loader profile.
//!
//! None of this touches the network: `resolve_profile` is pure, which is the whole point
//! of keeping the fetching in separate functions. Minecraft cannot run in CI, so this is
//! the closest thing to a launch test we have.

use std::path::PathBuf;

use void_core::manifest::{
    maven_to_path, natives_classifier, resolve_profile, rules_allow, sanitize_id, Library,
    VersionJson,
};
use void_core::{Os, RuleContext};

const VANILLA: &str = include_str!("fixtures/1.8.9.trimmed.json");
const LOADER: &str = include_str!("fixtures/legacyfabric.profile.json");

fn vanilla() -> VersionJson {
    serde_json::from_str(VANILLA).expect("the 1.8.9 fixture parses")
}

fn loader() -> VersionJson {
    serde_json::from_str(LOADER).expect("the Legacy Fabric fixture parses")
}

fn library(name: &str) -> Library {
    vanilla()
        .libraries
        .into_iter()
        .find(|l| l.name == name)
        .unwrap_or_else(|| panic!("no library {name} in the fixture"))
}

#[test]
fn the_version_json_parses_into_what_a_launch_needs() {
    let v = vanilla();
    assert_eq!(v.id, "1.8.9");
    assert_eq!(v.assets.as_deref(), Some("1.8"));
    assert_eq!(v.main_class.as_deref(), Some("net.minecraft.client.main.Main"));
    assert_eq!(v.asset_index.as_ref().unwrap().id, "1.8");
    assert!(v.downloads.contains_key("client"));
    assert!(v.minecraft_arguments.unwrap().contains("${auth_player_name}"));
    assert_eq!(v.libraries.len(), 6);
}

#[test]
fn maven_coordinates_become_repository_paths() {
    assert_eq!(
        maven_to_path("com.mojang:netty:1.6").unwrap(),
        "com/mojang/netty/1.6/netty-1.6.jar"
    );
    assert_eq!(
        maven_to_path("org.lwjgl.lwjgl:lwjgl-platform:2.9.4:natives-windows").unwrap(),
        "org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-windows.jar"
    );
    assert_eq!(
        maven_to_path("net.legacyfabric:intermediary:1.8.9@zip").unwrap(),
        "net/legacyfabric/intermediary/1.8.9/intermediary-1.8.9.zip"
    );
    assert!(maven_to_path("not-maven").is_err());
    assert!(maven_to_path("a:b").is_err());
}

#[test]
fn rules_follow_mojangs_allow_then_disallow_algorithm() {
    // `[{allow}, {disallow: linux}]` — everywhere but Linux.
    let twitch = library("tv.twitch:twitch-platform:5.16.0");
    for (os, expected) in [(Os::Windows, true), (Os::Osx, true), (Os::Linux, false)] {
        assert_eq!(
            rules_allow(twitch.rules.as_ref(), &RuleContext::new(os, "x86_64")),
            expected,
            "twitch-platform on {os:?}"
        );
    }

    // `[{allow: windows}]` — Windows only; an entry whose rules never match is excluded.
    let external = library("tv.twitch:twitch-external-platform:4.5");
    for (os, expected) in [(Os::Windows, true), (Os::Osx, false), (Os::Linux, false)] {
        assert_eq!(
            rules_allow(external.rules.as_ref(), &RuleContext::new(os, "x86_64")),
            expected,
            "twitch-external-platform on {os:?}"
        );
    }

    // No rules at all means allowed.
    let netty = library("com.mojang:netty:1.6");
    assert!(rules_allow(netty.rules.as_ref(), &RuleContext::new(Os::Linux, "x86_64")));
}

#[test]
fn natives_classifiers_pick_the_right_os_and_substitute_arch() {
    let lwjgl = library("org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209");
    assert_eq!(
        natives_classifier(&lwjgl, &RuleContext::new(Os::Windows, "x86_64")).as_deref(),
        Some("natives-windows")
    );
    assert_eq!(
        natives_classifier(&lwjgl, &RuleContext::new(Os::Osx, "x86_64")).as_deref(),
        Some("natives-osx")
    );

    let twitch = library("tv.twitch:twitch-platform:5.16.0");
    assert_eq!(
        natives_classifier(&twitch, &RuleContext::new(Os::Windows, "x86_64")).as_deref(),
        Some("natives-windows-64"),
        "${{arch}} follows the JVM, which is 64-bit everywhere we ship"
    );
    assert_eq!(
        natives_classifier(&twitch, &RuleContext::new(Os::Windows, "x86")).as_deref(),
        Some("natives-windows-32")
    );

    // A plain library has no natives.
    assert_eq!(
        natives_classifier(&library("com.mojang:netty:1.6"), &RuleContext::new(Os::Linux, "x86_64")),
        None
    );
}

#[test]
fn apple_silicon_still_resolves_the_x64_natives() {
    // 1.8.9 runs on an x64 JVM under Rosetta (§13), so an arm64 host must not end up
    // asking for arm64 natives that LWJGL 2 does not publish.
    let ctx = RuleContext::new(Os::Osx, "arm64");
    assert_eq!(ctx.natives_bits(), "64");
    let profile = resolve_profile(&vanilla(), Some(&loader()), &ctx).unwrap();
    assert!(profile.natives.iter().any(|n| n.name.ends_with(":natives-osx")));
}

#[test]
fn merging_puts_the_loader_first_and_shadows_duplicates() {
    let ctx = RuleContext::new(Os::Windows, "x86_64");
    let profile = resolve_profile(&vanilla(), Some(&loader()), &ctx).unwrap();

    assert_eq!(profile.version_id, "1.8.9");
    assert_eq!(profile.main_class, "net.fabricmc.loader.impl.launch.knot.KnotClient");
    assert_eq!(profile.assets_index, "1.8");
    assert!(profile.minecraft_arguments.contains("${auth_player_name}"), "vanilla args survive");
    assert_eq!(profile.jvm_arguments, ["-DFabricMcEmu= net.minecraft.client.main.Main "]);

    let names: Vec<&str> = profile.libraries.iter().map(|l| l.name.as_str()).collect();
    assert_eq!(names[0], "net.legacyfabric:intermediary:1.8.9", "loader libraries come first");
    assert!(names.contains(&"com.google.guava:guava:21.0"), "the loader's guava wins");
    assert!(
        !names.contains(&"com.google.guava:guava:17.0"),
        "vanilla's older guava is shadowed, not appended: {names:?}"
    );
    assert!(names.contains(&"com.mojang:netty:1.6"));
}

#[test]
fn loader_libraries_get_a_url_built_from_their_repository() {
    let ctx = RuleContext::new(Os::Linux, "x86_64");
    let profile = resolve_profile(&vanilla(), Some(&loader()), &ctx).unwrap();
    let loader_lib = profile
        .libraries
        .iter()
        .find(|l| l.name == "net.fabricmc:fabric-loader:0.16.0")
        .expect("the loader is on the classpath");

    assert_eq!(
        loader_lib.file.url,
        "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar"
    );
    assert_eq!(
        loader_lib.file.relative_path,
        PathBuf::from("libraries/net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar")
    );
    assert_eq!(loader_lib.file.sha1, None, "the meta server publishes no hashes");
}

#[test]
fn each_platform_gets_its_own_natives_and_library_set() {
    // lwjgl-platform is counted once even though both manifests ship a copy.
    let expectations = [
        (Os::Windows, 3, true),  // lwjgl + both twitch natives
        (Os::Osx, 2, false),     // lwjgl + twitch-platform
        (Os::Linux, 1, false),   // lwjgl only: twitch is disallowed on linux
    ];
    for (os, natives_count, has_external) in expectations {
        let profile =
            resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(os, "x86_64")).unwrap();
        assert_eq!(profile.natives.len(), natives_count, "{os:?} natives");
        assert_eq!(
            profile.natives.iter().any(|n| n.name.contains("twitch-external-platform")),
            has_external,
            "{os:?} twitch-external-platform"
        );
        for native in &profile.natives {
            assert!(
                native.exclude.iter().any(|e| e.starts_with("META-INF")),
                "{}: META-INF must never be unpacked next to the natives",
                native.name
            );
        }
    }
}

#[test]
fn the_client_jar_and_asset_index_land_where_the_launcher_expects_them() {
    let profile =
        resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Linux, "x86_64"))
            .unwrap();
    assert_eq!(profile.client.relative_path, PathBuf::from("versions/1.8.9/1.8.9.jar"));
    assert_eq!(profile.client.sha1.as_deref(), Some("0a1b2c3d4e5f60718293a4b5c6d7e8f901234567"));
    assert_eq!(profile.asset_index.relative_path, PathBuf::from("assets/indexes/1.8.json"));

    // `files()` is what the downloader is handed: everything but the assets themselves.
    let files = profile.files();
    assert_eq!(files.len(), 2 + profile.libraries.len() + profile.natives.len());
}

#[test]
fn a_vanilla_only_profile_still_resolves() {
    let profile =
        resolve_profile(&vanilla(), None, &RuleContext::new(Os::Windows, "x86_64")).unwrap();
    assert_eq!(profile.main_class, "net.minecraft.client.main.Main");
    assert_eq!(profile.profile_id, "1.8.9");
    assert!(profile.jvm_arguments.is_empty());
}

/// `version_id` is the *vanilla* version, never the merged Legacy Fabric id — that is
/// `profile_id`. `install::cached_profile` keys the profile cache directory on the
/// vanilla version, so if these two ever swapped, `prepare` would write the cache
/// somewhere `launch` never looks and every launch would silently re-resolve.
#[test]
fn version_id_stays_the_vanilla_version_even_with_a_loader() {
    let merged =
        resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Linux, "x86_64"))
            .unwrap();
    assert_eq!(merged.version_id, "1.8.9");
    assert_ne!(merged.version_id, merged.profile_id, "the loader id names the file, not the dir");
}

#[test]
fn profile_ids_are_safe_as_directory_names() {
    // Legacy Fabric really does publish ids with spaces in them.
    assert_eq!(sanitize_id("1.8.9-Legacy Fabric-0.16.0"), "1.8.9-Legacy-Fabric-0.16.0");
    let profile =
        resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Linux, "x86_64"))
            .unwrap();
    assert_eq!(profile.profile_id, "1.8.9-Legacy-Fabric-0.16.0");
    assert!(!profile.profile_id.contains(' '));
}

#[test]
fn the_profile_hash_changes_only_when_the_profile_does() {
    let a = resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Linux, "x86_64"))
        .unwrap();
    let b = resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Linux, "x86_64"))
        .unwrap();
    assert_eq!(a.hash(), b.hash(), "resolution is deterministic");

    let windows =
        resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Windows, "x86_64"))
            .unwrap();
    assert_ne!(a.hash(), windows.hash(), "a different platform is a different profile");
    assert_eq!(a.hash().len(), 40);
}

#[test]
fn asset_indexes_become_hash_addressed_downloads() {
    let index: void_core::manifest::AssetIndex = serde_json::from_str(
        r#"{"objects":{
            "minecraft/sounds/step/grass1.ogg":{"hash":"5e3f1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b","size":1234},
            "minecraft/lang/en_GB.lang":{"hash":"ab3f1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b","size":5678}
        }}"#,
    )
    .unwrap();

    let files = index.files();
    assert_eq!(files.len(), 2);
    let grass = files.iter().find(|f| f.sha1.as_deref() == Some("5e3f1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b")).unwrap();
    assert_eq!(
        grass.relative_path,
        PathBuf::from("assets/objects/5e/5e3f1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b")
    );
    assert_eq!(
        grass.url,
        "https://resources.download.minecraft.net/5e/5e3f1a2b3c4d5e6f708192a3b4c5d6e7f8091a2b"
    );
}

#[test]
fn a_natives_only_library_contributes_no_classpath_entry() {
    // `org.lwjgl.lwjgl:lwjgl-platform` has classifier jars in Maven and no plain jar at
    // all; asking the repository for one is a guaranteed 404, which is exactly what a
    // real `prepare` hit before this case was handled.
    let profile =
        resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Linux, "x86_64"))
            .unwrap();

    assert!(
        !profile.libraries.iter().any(|l| l.name.contains("lwjgl-platform")),
        "lwjgl-platform must not be on the classpath: {:?}",
        profile.libraries.iter().map(|l| &l.name).collect::<Vec<_>>()
    );
    assert!(profile.natives.iter().any(|n| n.name.contains("lwjgl-platform")));
    // Its plain sibling, which does exist in Maven, still is.
    assert!(profile
        .libraries
        .iter()
        .any(|l| l.name == "org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17"));
}

#[test]
fn the_loaders_patched_natives_shadow_the_vanilla_ones() {
    // Legacy Fabric ships a patched LWJGL for newer OS support. Both copies unpack into
    // the same natives directory, so only one may be extracted — the loader's.
    let profile =
        resolve_profile(&vanilla(), Some(&loader()), &RuleContext::new(Os::Linux, "x86_64"))
            .unwrap();

    let lwjgl: Vec<&str> = profile
        .natives
        .iter()
        .filter(|n| n.name.contains("lwjgl-platform"))
        .map(|n| n.name.as_str())
        .collect();
    assert_eq!(lwjgl.len(), 1, "one lwjgl-platform natives jar, not two: {lwjgl:?}");
    assert!(lwjgl[0].starts_with("org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17"));
}
