//! TODO(integrate): Microsoft OAuth → Xbox Live → XSTS → Minecraft token belongs to
//! `void-core` (§12, and CONTRACTS.md gives `core` `crates/void-core/`). That crate is
//! a doc-comment stub today, so this file provides:
//!
//! - the **real** offline-account path, which needs no network and no `void-core`;
//! - the device-code **shape** for the Microsoft path — `auth_login` returns the user
//!   code and verification URL immediately, then pushes `auth:status` events — with a
//!   stand-in that walks the stages and then reports honestly that the token exchange
//!   is not implemented.
//!
//! It deliberately does **not** fabricate a signed-in Microsoft account: a launcher
//! that claims you are signed in and then fails at spawn time is worse than one that
//! says up front what is missing. When `void_core::auth` lands, replace
//! `run_device_flow` with a call into it and keep everything else.
//!
//! The refresh token belongs in the OS keychain (§12.1), not in `<data dir>` — that
//! is `void-core`'s job too, which is why nothing here writes a credential to disk.

use std::time::Duration;

use crate::error::Error;
use crate::events::{emit, Emitter, AUTH_STATUS};
use crate::models::{Account, AccountKind, AuthStatus, DeviceCode};

/// Microsoft's public device-authorization endpoint, quoted so the URL the player is
/// asked to visit is the real one even while the exchange is stubbed.
pub const VERIFICATION_URI: &str = "https://www.microsoft.com/link";

/// Start the device flow. Returns as soon as there is something to show the player.
pub fn begin_device_flow() -> Result<DeviceCode, Error> {
    Ok(DeviceCode {
        // TODO(integrate): void-core requests this from Microsoft's
        // /devicecode endpoint; until then it is visibly a placeholder.
        user_code: "VOID-SETUP".to_string(),
        verification_uri: VERIFICATION_URI.to_string(),
        expires_in_s: 900,
        interval_s: 5,
    })
}

/// Drive the flow to completion, pushing `auth:status` as it goes.
///
/// Runs on a background task; the command has already returned the device code.
pub async fn run_device_flow(emitter: &dyn Emitter) {
    let stages = [
        AuthStatus::Pending {
            message: "Waiting for you to finish signing in…".into(),
        },
        AuthStatus::Xbox {
            message: "Authenticating with Xbox Live…".into(),
        },
        AuthStatus::Minecraft {
            message: "Fetching your Minecraft profile…".into(),
        },
    ];
    for stage in stages {
        emit(emitter, AUTH_STATUS, &stage);
        tokio::time::sleep(Duration::from_millis(700)).await;
    }

    // TODO(integrate): replace with `void_core::auth::complete_device_flow(...)`,
    // which ends in `AuthStatus::Complete { account }`.
    emit(
        emitter,
        AUTH_STATUS,
        &AuthStatus::Failed {
            message: "Microsoft sign-in needs void-core's auth pipeline, which is not \
                      implemented yet. Use \"Play offline\" in the meantime."
                .into(),
        },
    );
}

/// The offline account of the Figma dock. Real, and independent of `void-core`.
///
/// The UUID is the offline-mode one Minecraft itself derives: MD5 of
/// `OfflinePlayer:<name>`, version 3, variant RFC 4122. Deriving it the same way
/// means an offline session keeps the same identity across launches, which is what
/// makes per-loadout stats and server whitelists behave.
pub fn offline_account(name: &str) -> Result<Account, Error> {
    let name = name.trim();
    if name.is_empty() || name.len() > 16 || !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
    {
        return Err(Error::Auth(
            "A Minecraft name is 1–16 characters: letters, digits and underscores.".into(),
        ));
    }
    Ok(Account {
        uuid: offline_uuid(name),
        name: name.to_string(),
        kind: AccountKind::Offline,
        level: 1,
        skin_url: None,
    })
}

/// Version-3 (MD5) UUID over `OfflinePlayer:<name>`, formatted with hyphens.
///
/// TODO(integrate): `void-core` will own this too (it needs the same value to build
/// the JVM args). Written out here rather than pulling an MD5 crate into the launcher
/// twice — when `void-core` exports it, delete this and the tiny MD5 below.
fn offline_uuid(name: &str) -> String {
    let mut digest = md5(format!("OfflinePlayer:{name}").as_bytes());
    digest[6] = (digest[6] & 0x0f) | 0x30; // version 3
    digest[8] = (digest[8] & 0x3f) | 0x80; // RFC 4122 variant
    let hex: String = digest.iter().map(|b| format!("{b:02x}")).collect();
    format!(
        "{}-{}-{}-{}-{}",
        &hex[0..8],
        &hex[8..12],
        &hex[12..16],
        &hex[16..20],
        &hex[20..32]
    )
}

/// Minimal MD5. Only ever fed a short ASCII string; not for anything security-bearing.
fn md5(input: &[u8]) -> [u8; 16] {
    const S: [u32; 64] = [
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5,
        9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10,
        15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    ];
    let k: Vec<u32> = (0..64)
        .map(|i| ((i as f64 + 1.0).sin().abs() * 4_294_967_296.0) as u32)
        .collect();

    let mut msg = input.to_vec();
    let bit_len = (input.len() as u64).wrapping_mul(8);
    msg.push(0x80);
    while msg.len() % 64 != 56 {
        msg.push(0);
    }
    msg.extend_from_slice(&bit_len.to_le_bytes());

    let (mut a0, mut b0, mut c0, mut d0) =
        (0x6745_2301u32, 0xefcd_ab89u32, 0x98ba_dcfeu32, 0x1032_5476u32);

    for chunk in msg.chunks(64) {
        let m: Vec<u32> = chunk
            .chunks(4)
            .map(|w| u32::from_le_bytes([w[0], w[1], w[2], w[3]]))
            .collect();
        let (mut a, mut b, mut c, mut d) = (a0, b0, c0, d0);
        for i in 0..64 {
            let (f, g) = match i / 16 {
                0 => ((b & c) | (!b & d), i),
                1 => ((d & b) | (!d & c), (5 * i + 1) % 16),
                2 => (b ^ c ^ d, (3 * i + 5) % 16),
                _ => (c ^ (b | !d), (7 * i) % 16),
            };
            let f2 = f
                .wrapping_add(a)
                .wrapping_add(k[i])
                .wrapping_add(m[g]);
            a = d;
            d = c;
            c = b;
            b = b.wrapping_add(f2.rotate_left(S[i]));
        }
        a0 = a0.wrapping_add(a);
        b0 = b0.wrapping_add(b);
        c0 = c0.wrapping_add(c);
        d0 = d0.wrapping_add(d);
    }

    let mut out = [0u8; 16];
    out[0..4].copy_from_slice(&a0.to_le_bytes());
    out[4..8].copy_from_slice(&b0.to_le_bytes());
    out[8..12].copy_from_slice(&c0.to_le_bytes());
    out[12..16].copy_from_slice(&d0.to_le_bytes());
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn md5_matches_the_reference_vectors() {
        let hex = |b: [u8; 16]| b.iter().map(|x| format!("{x:02x}")).collect::<String>();
        assert_eq!(hex(md5(b"")), "d41d8cd98f00b204e9800998ecf8427e");
        assert_eq!(hex(md5(b"abc")), "900150983cd24fb0d6963f7d28e17f72");
        assert_eq!(
            hex(md5(b"The quick brown fox jumps over the lazy dog")),
            "9e107d9d372bb6826bd81d3542a419d6"
        );
    }

    #[test]
    fn offline_uuid_is_the_one_minecraft_derives() {
        // Known value for the vanilla offline-mode scheme.
        assert_eq!(offline_uuid("Notch"), "b50ad385-829d-3141-a216-7e7d7539ba7f");
        // Stable across calls — per-loadout stats depend on it.
        assert_eq!(offline_uuid("Searge"), offline_uuid("Searge"));
    }

    #[test]
    fn offline_names_are_validated() {
        assert!(offline_account("Searge").is_ok());
        assert!(offline_account("").is_err());
        assert!(offline_account("a name with spaces").is_err());
        assert!(offline_account("waytoolongminecraftname").is_err());
    }

    #[tokio::test]
    async fn device_flow_reports_stages_then_the_missing_backend() {
        let rec = crate::events::test_support::Recorder::default();
        run_device_flow(&rec).await;
        let stages: Vec<String> = rec
            .payloads(AUTH_STATUS)
            .into_iter()
            .map(|v| v["stage"].as_str().unwrap_or_default().to_string())
            .collect();
        assert_eq!(stages, ["pending", "xbox", "minecraft", "failed"]);
    }
}
