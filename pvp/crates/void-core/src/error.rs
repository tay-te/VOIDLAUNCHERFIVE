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

    /// A downloaded file did not match its expected SHA-256.
    #[error("{path}: sha256 mismatch (expected {expected}, got {actual})")]
    Sha256Mismatch {
        /// The file that failed verification.
        path: PathBuf,
        /// The digest the manifest promised.
        expected: String,
        /// The digest the bytes actually hash to.
        actual: String,
    },

    /// The `void-client` release manifest was not signed by the VOID release key.
    #[error("the VOID client release manifest failed verification: {0}")]
    Signature(String),

    /// The published `void-client` needs a newer launcher than this one.
    #[error(
        "this launcher ({current}) is too old for the current VOID client, which needs \
         launcher {required} or newer. Restart the launcher to update it."
    )]
    LauncherTooOld {
        /// The minimum launcher version the manifest asks for.
        required: String,
        /// This launcher's version.
        current: String,
    },

    /// No `void-client` JAR could be found for this platform, online or in the cache.
    #[error("the VOID client could not be installed: {0}")]
    ModUnavailable(String),

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
    /// Wraps an I/O error with the path it happened to.
    pub fn io(path: impl Into<PathBuf>, source: std::io::Error) -> Self {
        Error::Io { path: path.into(), source }
    }

    /// Wraps a JSON error with a description of what was being parsed.
    pub fn json(context: impl Into<String>, source: serde_json::Error) -> Self {
        Error::Json { context: context.into(), source }
    }

    /// Whether this is "the network is not there" rather than "the network said no".
    ///
    /// Transport failures (no route, DNS, TLS, timeout) and server-side trouble (5xx,
    /// 429) are what a warm launch falls back to its caches for. A 404 or a bad
    /// signature is an answer, and is not.
    pub fn is_network(&self) -> bool {
        match self {
            Error::Http(_) => true,
            Error::HttpStatus { status, .. } => *status >= 500 || *status == 429 || *status == 408,
            _ => false,
        }
    }
}

/// Shorthand for a `void-core` result.
pub type Result<T, E = Error> = std::result::Result<T, E>;
