//! Errors from every stage between "the player clicks Play" and "the window is up".

use std::path::PathBuf;

/// Anything that can go wrong in `void-core`.
#[derive(Debug, thiserror::Error)]
#[non_exhaustive]
pub enum Error {
    /// A file or directory operation failed.
    #[error("{path}: {source}")]
    Io {
        /// The path being touched.
        path: PathBuf,
        /// The underlying error.
        #[source]
        source: std::io::Error,
    },

    /// An I/O error with no single path to blame.
    #[error(transparent)]
    BareIo(#[from] std::io::Error),

    /// An HTTP request failed at the transport level.
    #[error(transparent)]
    Http(#[from] reqwest::Error),

    /// An HTTP request returned a status we cannot proceed from.
    #[error("{url} returned {status}{}", detail.as_deref().map(|d| format!(": {d}")).unwrap_or_default())]
    HttpStatus {
        /// The URL requested.
        url: String,
        /// The status returned.
        status: u16,
        /// The response body, when it was short enough to be useful.
        detail: Option<String>,
    },

    /// A JSON document was not the shape we expected.
    #[error("{context}: {source}")]
    Json {
        /// What was being parsed.
        context: String,
        /// The underlying error.
        #[source]
        source: serde_json::Error,
    },

    /// A downloaded file did not match its expected SHA-1.
    #[error("{path}: sha1 mismatch (expected {expected}, got {actual})")]
    Sha1Mismatch {
        /// The file that failed verification.
        path: PathBuf,
        /// The digest the manifest promised.
        expected: String,
        /// The digest the bytes actually hash to.
        actual: String,
    },

    /// The Microsoft OAuth, Xbox Live, XSTS or Minecraft services flow failed.
    #[error("sign-in failed: {0}")]
    Auth(String),

    /// No Azure application client id is configured.
    #[error(
        "no Microsoft client id: set VOID_MS_CLIENT_ID, or `ms_client_id` in {0}. \
         Register an Azure application with the device-code flow enabled; VOID ships no \
         client id of its own."
    )]
    MissingClientId(PathBuf),

    /// The user is not signed in and no offline name was supplied.
    #[error("not signed in: run `void-pvp login`, or launch with --offline <name>")]
    NotSignedIn,

    /// A version, library or asset the manifest promised was not there.
    #[error("{0}")]
    Manifest(String),

    /// No Java 8 runtime could be found or fetched.
    #[error("no Java 8 runtime: {0}")]
    Java(String),

    /// The host OS or architecture is not one we can launch 1.8.9 on.
    #[error("unsupported platform: {0}")]
    UnsupportedPlatform(String),

    /// An archive could not be read.
    #[error("{path}: {message}")]
    Archive {
        /// The archive.
        path: PathBuf,
        /// What went wrong.
        message: String,
    },

    /// The loadout store failed.
    #[error(transparent)]
    Loadout(#[from] void_loadout::Error),

    /// The bridge server failed.
    #[error(transparent)]
    Bridge(#[from] void_bridge::Error),
}

impl Error {
    /// One sentence telling the player what to do about it, or `None` when there is nothing.
    ///
    /// **Separate from `Display`, because these errors have two audiences and they want
    /// different things.** The messages above are written for the CLI, where the reader is
    /// whoever is running `void-pvp` and a path with an errno on it is exactly right. The same
    /// text reaches the launcher's error banner, where the reader is a player who wants to know
    /// whether to press Play again or go and do something else.
    ///
    /// So the technical sentence stays and this is appended to it — one message, both audiences,
    /// and one place to fix either. The alternative was re-wording every variant at the launcher
    /// boundary, which is two texts per error and no way to notice when they disagree.
    ///
    /// `None` is a real answer and not a gap: an unsupported platform is not something the
    /// player can act on, and inventing an instruction there would be worse than silence.
    pub fn advice(&self) -> Option<&'static str> {
        match self {
            // Almost always a full disk or a data folder the user cannot write to — a game
            // install is several hundred megabytes and the errno rarely says which.
            Error::Io { .. } | Error::BareIo(_) => {
                Some("Check that VOID can write to its data folder and that the disk is not full.")
            }
            // By the time this reaches a player the downloader has already tried four times
            // (`download::ATTEMPTS`), so "try again" is advice about a later attempt rather than
            // a suggestion to mash the button.
            Error::Http(_) | Error::HttpStatus { .. } => Some(
                "Check your connection and try again. If it keeps failing, Mojang's servers may \
                 be having trouble.",
            ),
            // Also post-retry, so the bytes on the server really are wrong or something on the
            // path is rewriting them. Both are somebody else's problem and both pass.
            Error::Sha1Mismatch { .. } => Some(
                "A file arrived damaged four times over. That usually means a proxy or a VPN is \
                 rewriting downloads.",
            ),
            Error::Json { .. } | Error::Manifest(_) => {
                Some("Mojang's version data was not what VOID expected. Try again later.")
            }
            Error::Auth(_) | Error::NotSignedIn => Some("Sign in again from Settings."),
            // The honest player-facing version of "register an Azure application": this build
            // cannot do Microsoft sign-in at all, and no amount of retrying changes that.
            Error::MissingClientId(_) => Some(
                "This build has no Microsoft sign-in configured. Use Play offline, or ask \
                 whoever built it for a client id.",
            ),
            Error::Java(_) => {
                Some("Set a Java 8 path in Settings, or turn \"Find Java automatically\" back on.")
            }
            Error::Archive { .. } => {
                Some("Delete VOID's data folder and press Play again to reinstall.")
            }
            // Nothing to do. 1.8.9 does not run here, and an instruction would be a lie.
            Error::UnsupportedPlatform(_) => None,
            // Both wrap an error whose own type owns the advice.
            Error::Loadout(_) | Error::Bridge(_) => None,
        }
    }

    /// Whether this is worth another press of Play.
    ///
    /// Distinct from [`Self::advice`] because a banner wants to know whether to offer a retry
    /// button, and that is not the same question as what sentence to print — `UnsupportedPlatform`
    /// has no advice and is certainly not retryable, and a `Java` error has advice that is not
    /// "try again".
    pub fn worth_retrying(&self) -> bool {
        matches!(
            self,
            Error::Io { .. }
                | Error::BareIo(_)
                | Error::Http(_)
                | Error::HttpStatus { .. }
                | Error::Json { .. }
                | Error::Manifest(_)
                | Error::Sha1Mismatch { .. }
        )
    }

    /// Wraps an I/O error with the path it happened to.
    pub fn io(path: impl Into<PathBuf>, source: std::io::Error) -> Self {
        Error::Io { path: path.into(), source }
    }

    /// Wraps a JSON error with a description of what was being parsed.
    pub fn json(context: impl Into<String>, source: serde_json::Error) -> Self {
        Error::Json { context: context.into(), source }
    }
}

/// Shorthand for a `void-core` result.
pub type Result<T, E = Error> = std::result::Result<T, E>;

#[cfg(test)]
mod tests {
    use super::*;

    /// Every error a *player* can reach carries advice.
    ///
    /// A walk rather than a list, so a variant added tomorrow is covered tonight — and the
    /// `match` in `advice` has no wildcard arm, so adding one is a compile error before it is a
    /// test failure. This is the second half of that: it fails when somebody adds a variant and
    /// answers `None` to make it compile.
    ///
    /// The exclusions are stated rather than skipped. `UnsupportedPlatform` has nothing to
    /// advise — 1.8.9 does not run here — and `Loadout` and `Bridge` wrap error types that own
    /// their own advice.
    #[test]
    fn every_player_facing_error_says_what_to_do() {
        let cases: Vec<Error> = vec![
            Error::io("/x", std::io::Error::other("disk full")),
            Error::BareIo(std::io::Error::other("nope")),
            Error::HttpStatus { url: "https://x/y".into(), status: 503, detail: None },
            Error::Sha1Mismatch {
                path: "libraries/a.jar".into(),
                expected: "a".repeat(40),
                actual: "b".repeat(40),
            },
            Error::Manifest("no 1.8.9".into()),
            Error::Auth("xsts 2148916233".into()),
            Error::NotSignedIn,
            Error::MissingClientId("/cfg.json".into()),
            Error::Java("none on PATH".into()),
            Error::Archive { path: "natives.zip".into(), message: "truncated".into() },
        ];
        for case in &cases {
            let advice = case.advice();
            assert!(advice.is_some(), "{case} reaches a player with no advice");
            let text = advice.unwrap();
            // The banner shows it under one technical sentence. Two sentences of instruction is
            // a paragraph, and a paragraph in an error banner is not read.
            assert!(text.len() < 140, "advice is too long to be read: {text}");
            assert!(text.ends_with('.'), "advice should be a sentence: {text}");
        }
    }

    #[test]
    fn what_is_worth_retrying_is_not_what_has_advice() {
        // Two different questions, which is why they are two methods. A Java error has advice
        // and pressing Play again will not help; an unsupported platform has neither.
        assert!(Error::Java("none".into()).advice().is_some());
        assert!(!Error::Java("none".into()).worth_retrying());
        assert!(Error::UnsupportedPlatform("riscv".into()).advice().is_none());
        assert!(!Error::UnsupportedPlatform("riscv".into()).worth_retrying());
        assert!(Error::HttpStatus { url: "u".into(), status: 500, detail: None }.worth_retrying());
    }
}
