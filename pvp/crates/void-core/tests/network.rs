//! Tests that need the internet, and are therefore `#[ignore]`d.
//!
//! Run them deliberately:
//!
//! ```sh
//! cargo test -p void-core --test network -- --ignored --nocapture
//! ```
//!
//! They are the check that Mojang's and Legacy Fabric's live manifests still resolve
//! against the parser — the fixture in `tests/manifest.rs` proves the logic, these prove
//! the contract with the real servers.

use void_core::manifest::{self, MC_VERSION};
use void_core::{Paths, RuleContext};

fn client() -> reqwest::Client {
    reqwest::Client::builder()
        .user_agent("void-pvp-tests")
        .build()
        .expect("http client")
}

#[tokio::test]
#[ignore = "needs the network"]
async fn mojangs_manifest_still_lists_1_8_9() {
    let manifest = manifest::fetch_version_manifest(&client()).await.unwrap();
    let entry = manifest.find(MC_VERSION).expect("1.8.9 is still published");
    assert_eq!(entry.kind, "release");
    assert!(entry.url.starts_with("https://"));
}

#[tokio::test]
#[ignore = "needs the network"]
async fn the_live_1_8_9_version_json_parses() {
    let http = client();
    let manifest = manifest::fetch_version_manifest(&http).await.unwrap();
    let entry = manifest.find(MC_VERSION).unwrap();
    let version = manifest::fetch_version_json(&http, entry).await.unwrap();

    assert_eq!(version.id, MC_VERSION);
    assert_eq!(version.assets.as_deref(), Some("1.8"));
    assert_eq!(version.main_class.as_deref(), Some("net.minecraft.client.main.Main"));
    assert!(version.libraries.len() > 20, "1.8.9 has around 40 libraries");
    assert!(version.minecraft_arguments.is_some(), "1.8.9 predates the structured form");
}

#[tokio::test]
#[ignore = "needs the network"]
async fn legacy_fabric_still_publishes_a_loader_for_1_8_9() {
    let loaders = manifest::fetch_loader_versions(&client(), MC_VERSION).await.unwrap();
    assert!(!loaders.is_empty());
    assert!(loaders.iter().any(|l| l.loader.stable), "at least one stable build");
}

#[tokio::test]
#[ignore = "needs the network"]
async fn the_whole_1_8_9_profile_resolves_from_live_manifests() {
    let profile = manifest::resolve_1_8_9(&client(), &RuleContext::host().unwrap(), None)
        .await
        .unwrap();

    assert_eq!(profile.version_id, MC_VERSION);
    assert!(profile.main_class.contains("Knot"), "Legacy Fabric boots through Knot");
    assert!(!profile.libraries.is_empty());
    assert!(!profile.natives.is_empty(), "LWJGL 2 natives are mandatory");
    assert!(profile.minecraft_arguments.contains("${auth_player_name}"));
    println!(
        "{}: {} libraries, {} natives",
        profile.profile_id,
        profile.libraries.len(),
        profile.natives.len()
    );
}

#[tokio::test]
#[ignore = "needs the network and about 300 MB of disk"]
async fn a_full_prepare_downloads_everything() {
    let dir = tempfile::tempdir().unwrap();
    let paths = Paths::at(dir.path());
    let profile = void_core::install::prepare(
        &client(),
        &paths,
        &RuleContext::host().unwrap(),
        None,
        None,
    )
    .await
    .unwrap();

    assert!(paths.root().join(&profile.client.relative_path).exists());
    assert!(paths.root().join(&profile.asset_index.relative_path).exists());
    for lib in &profile.libraries {
        assert!(
            paths.root().join(&lib.file.relative_path).exists(),
            "{} was not downloaded",
            lib.name
        );
    }
}

#[tokio::test]
#[ignore = "needs the network and about 45 MB of disk"]
async fn adoptium_serves_a_temurin_8_jre_for_this_host() {
    let dir = tempfile::tempdir().unwrap();
    let paths = Paths::at(dir.path());
    paths.ensure().unwrap();
    // Force the download path by pointing detection at an empty installation.
    let java = void_core::java::ensure_java8(&client(), &paths, None).await.unwrap();
    assert!(java.is_java8(), "got Java {}", java.major);
    println!("{} at {}", java.version, java.path.display());
}
