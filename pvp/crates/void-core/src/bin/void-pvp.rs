//! `void-pvp` — the launcher without a launcher.
//!
//! Every step of §12 driven from a terminal: sign in, download 1.8.9 + Legacy Fabric,
//! find a Java 8, start the bridge, spawn the game. `void-core` has no Tauri dependency
//! (§4), so this binary is the whole launch path, testable without a webview — and
//! `--offline` makes it usable with no Microsoft account at all.

use std::path::PathBuf;

use clap::{Args, Parser, Subcommand};
use tokio::sync::mpsc;
use void_bridge::BridgeServer;
use void_core::auth::{self, Auth, Session, TokenStore};
use void_core::launch::{LaunchOptions, Stream};
use void_core::{install, java, launch, Config, Error, Paths, Result, RuleContext};
use void_loadout::{hypixel_ready, LoadoutId, Store};

/// The VOID PVP launcher CLI.
#[derive(Debug, Parser)]
#[command(name = "void-pvp", version, about = "Launch Minecraft 1.8.9 with the VOID PVP client")]
struct Cli {
    /// Installation root. Defaults to $VOID_PVP_HOME, else ~/.void-pvp.
    #[arg(long, global = true, env = "VOID_PVP_HOME")]
    home: Option<PathBuf>,

    /// Print more about what is happening. Repeat for trace level.
    #[arg(short, long, global = true, action = clap::ArgAction::Count)]
    verbose: u8,

    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    /// Sign in with a Microsoft account, using the device-code flow.
    Login,
    /// Forget the stored refresh token and cached profile.
    Logout,
    /// Show who is signed in.
    Whoami,
    /// Download everything a launch needs: manifests, libraries, assets, Java 8.
    Prepare(PrepareArgs),
    /// Start the bridge and launch the game.
    Launch(LaunchArgs),
    /// Inspect and switch loadouts.
    #[command(subcommand)]
    Loadouts(LoadoutsCommand),
}

#[derive(Debug, Args)]
struct PrepareArgs {
    /// Pin a Legacy Fabric loader build instead of taking the newest stable one.
    #[arg(long)]
    loader: Option<String>,
    /// Skip the Java 8 check and download.
    #[arg(long)]
    no_java: bool,
}

#[derive(Debug, Args)]
struct LaunchArgs {
    /// Loadout to make active before launching.
    #[arg(long)]
    loadout: Option<String>,
    /// Launch without an account, under this player name.
    #[arg(long, value_name = "NAME")]
    offline: Option<String>,
    /// The void-client mod JAR to install into the mods directory.
    #[arg(long, value_name = "PATH")]
    mod_jar: Option<PathBuf>,
    /// Maximum JVM heap in megabytes.
    #[arg(long)]
    memory: Option<u32>,
    /// Pin a Legacy Fabric loader build.
    #[arg(long)]
    loader: Option<String>,
    /// Use the profile from a previous `prepare` and do not touch the network.
    #[arg(long)]
    offline_assets: bool,
}

#[derive(Debug, Subcommand)]
enum LoadoutsCommand {
    /// List the library.
    List,
    /// Print one loadout as JSON.
    Show {
        /// Loadout id.
        id: String,
    },
    /// Make a loadout active.
    Switch {
        /// Loadout id.
        id: String,
    },
}

#[tokio::main]
async fn main() {
    let cli = Cli::parse();
    init_tracing(cli.verbose);

    if let Err(error) = run(cli).await {
        eprintln!("error: {error}");
        let mut source = std::error::Error::source(&error);
        while let Some(cause) = source {
            eprintln!("  caused by: {cause}");
            source = cause.source();
        }
        std::process::exit(1);
    }
}

fn init_tracing(verbose: u8) {
    let default = match verbose {
        0 => "void_core=info,void_bridge=info,void_loadout=info,warn",
        1 => "debug",
        _ => "trace",
    };
    let filter = tracing_subscriber::EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new(default));
    tracing_subscriber::fmt().with_env_filter(filter).with_target(false).without_time().init();
}

async fn run(cli: Cli) -> Result<()> {
    let paths = match &cli.home {
        Some(home) => Paths::at(home),
        None => Paths::new()?,
    };
    let store = Store::at(paths.root());
    let config = Config::load(&paths)?;
    let http = reqwest::Client::builder()
        .user_agent(concat!("void-pvp/", env!("CARGO_PKG_VERSION")))
        .build()?;

    match cli.command {
        Command::Login => login(&http, &paths, &config).await,
        Command::Logout => logout(&paths),
        Command::Whoami => whoami(&paths),
        Command::Prepare(args) => prepare(&http, &paths, &args).await,
        Command::Launch(args) => do_launch(&http, &paths, &store, &config, args).await,
        Command::Loadouts(cmd) => loadouts(&store, cmd),
    }
}

// ---------------------------------------------------------------------------
// auth
// ---------------------------------------------------------------------------

async fn login(http: &reqwest::Client, paths: &Paths, config: &Config) -> Result<()> {
    let client_id = config.ms_client_id(paths)?;
    let auth = Auth::new(http.clone(), client_id);

    let code = auth.start_device_code().await?;
    println!();
    match &code.message {
        Some(message) => println!("{message}"),
        None => println!(
            "Open {} and enter the code {}",
            code.verification_uri, code.user_code
        ),
    }
    println!("\nWaiting for you to finish in the browser…");

    let tokens = auth.poll_for_token(&code).await?;
    if let Some(refresh) = &tokens.refresh_token {
        TokenStore::new(paths).save(refresh)?;
    }
    let session = auth.minecraft_session(&tokens.access_token).await?;
    auth::save_profile(paths, &session)?;

    println!("Signed in as {} ({})", session.username, session.uuid);
    Ok(())
}

fn logout(paths: &Paths) -> Result<()> {
    TokenStore::new(paths).clear()?;
    auth::clear_profile(paths)?;
    println!("Signed out.");
    Ok(())
}

fn whoami(paths: &Paths) -> Result<()> {
    match auth::load_profile(paths)? {
        Some(session) => {
            println!("{}  ({})", session.username, session.uuid);
            println!("account: {}", if session.is_offline() { "offline" } else { "Microsoft" });
            Ok(())
        }
        None => {
            println!("Not signed in. Run `void-pvp login`, or launch with --offline <name>.");
            Ok(())
        }
    }
}

async fn resolve_session(
    http: &reqwest::Client,
    paths: &Paths,
    config: &Config,
    offline: Option<String>,
) -> Result<Session> {
    if let Some(name) = offline {
        return Ok(Session::offline(name));
    }
    let client_id = config.ms_client_id(paths)?;
    auth::sign_in_silently(http, paths, &client_id).await?.ok_or(Error::NotSignedIn)
}

// ---------------------------------------------------------------------------
// prepare
// ---------------------------------------------------------------------------

/// Spawns a task that prints download progress as a single rewritten line.
fn progress_printer() -> mpsc::Sender<void_core::download::Progress> {
    use void_core::download::Progress;
    let (tx, mut rx) = mpsc::channel(256);
    tokio::spawn(async move {
        while let Some(event) = rx.recv().await {
            match event {
                Progress::Started { files, bytes } => {
                    println!("Fetching {files} files ({:.1} MB)", bytes as f64 / 1e6);
                }
                Progress::Finished { done, total, .. } if done % 25 == 0 || done == total => {
                    print!("\r  {done}/{total}");
                    use std::io::Write;
                    let _ = std::io::stdout().flush();
                }
                Progress::Completed { files, downloaded_bytes } => {
                    println!(
                        "\r  {files}/{files} — {:.1} MB downloaded",
                        downloaded_bytes as f64 / 1e6
                    );
                }
                Progress::Finished { .. } => {}
            }
        }
    });
    tx
}

async fn prepare(http: &reqwest::Client, paths: &Paths, args: &PrepareArgs) -> Result<()> {
    let ctx = RuleContext::host()?;
    println!("Installing into {}", paths.root().display());

    let profile =
        install::prepare(http, paths, &ctx, args.loader.as_deref(), Some(progress_printer()))
            .await?;
    println!(
        "Ready: {} ({} libraries, {} natives jars, main class {})",
        profile.profile_id,
        profile.libraries.len(),
        profile.natives.len(),
        profile.main_class
    );

    if !args.no_java {
        let java = java::ensure_java8(http, paths, None).await?;
        println!("Java {} at {}", java.version, java.path.display());
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// launch
// ---------------------------------------------------------------------------

async fn do_launch(
    http: &reqwest::Client,
    paths: &Paths,
    store: &Store,
    config: &Config,
    args: LaunchArgs,
) -> Result<()> {
    store.init()?;
    if let Some(id) = &args.loadout {
        let id = parse_id(id)?;
        store.set_active(&id)?;
    }
    let active = store.active()?;
    println!(
        "Loadout: {} ({} mods on){}",
        active.name,
        active.enabled_mods().len(),
        if hypixel_ready(&active) { "  ·  HYPIXEL-READY" } else { "" }
    );

    let session = resolve_session(http, paths, config, args.offline).await?;
    println!("Playing as {}{}", session.username, if session.is_offline() { " (offline)" } else { "" });

    let ctx = RuleContext::host()?;
    let profile = match (args.offline_assets, install::cached_profile(paths, "1.8.9")) {
        (true, Some(cached)) => cached,
        (true, None) => {
            return Err(Error::Manifest(
                "no prepared profile on disk; run `void-pvp prepare` once with a network"
                    .into(),
            ))
        }
        _ => {
            install::prepare(http, paths, &ctx, args.loader.as_deref(), Some(progress_printer()))
                .await?
        }
    };

    let java = match &config.java_path {
        Some(path) => java::probe(path)
            .ok_or_else(|| Error::Java(format!("{} did not run", path.display())))?,
        None => java::ensure_java8(http, paths, None).await?,
    };
    if !java.is_java8() {
        return Err(Error::Java(format!(
            "{} is Java {}; 1.8.9 needs Java 8",
            java.path.display(),
            java.major
        )));
    }

    // The bridge comes up before the JVM: the mod connects back to it on start-up, and
    // the port and token are only knowable once it is bound.
    let server = BridgeServer::bind(void_core::sync::StoreInit::new(store.clone())).await?;
    println!("Bridge listening on {} (token {}…)", server.url(), &server.token()[..8]);
    tokio::spawn(void_core::sync::pump(server.clone(), store.clone()));

    let options = LaunchOptions {
        session,
        java: java.path,
        max_memory_mb: args.memory.unwrap_or(config.max_memory_mb),
        extra_jvm_args: config.jvm_args.clone(),
        bridge_port: server.port(),
        bridge_token: server.token().to_string(),
        mod_jar: args.mod_jar.or_else(|| config.mod_jar.clone()),
    };

    let mut game = launch::launch(&profile, paths, &options).await?;
    println!("Minecraft started (pid {:?})\n", game.pid());

    let mut logs = std::mem::replace(&mut game.logs, mpsc::channel(1).1);
    let printer = tokio::spawn(async move {
        while let Some(line) = logs.recv().await {
            match line.stream {
                Stream::Stdout => println!("{}", line.text),
                Stream::Stderr => eprintln!("{}", line.text),
            }
        }
    });

    let code = game.wait().await?;
    let _ = printer.await;
    println!("\nMinecraft exited with code {code}");
    if code != 0 {
        std::process::exit(code);
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// loadouts
// ---------------------------------------------------------------------------

fn parse_id(id: &str) -> Result<LoadoutId> {
    LoadoutId::new(id).ok_or_else(|| {
        Error::Loadout(void_loadout::Error::Invalid(format!("`{id}` is not a valid loadout id")))
    })
}

fn loadouts(store: &Store, command: LoadoutsCommand) -> Result<()> {
    store.init()?;
    match command {
        LoadoutsCommand::List => {
            let active = store.active_id()?;
            for loadout in store.list()? {
                let stats = loadout.stats.unwrap_or_default();
                println!(
                    "{} {:<12} {:<12} {:>2} mods on  {:>6}  {}",
                    if loadout.id == active { "*" } else { " " },
                    loadout.id.to_string(),
                    loadout.name,
                    loadout.enabled_mods().len(),
                    format_played(stats.played_ms.unwrap_or(0)),
                    if hypixel_ready(&loadout) { "hypixel-ready" } else { "" }
                );
            }
            Ok(())
        }
        LoadoutsCommand::Show { id } => {
            let loadout = store.load(&parse_id(&id)?)?;
            println!(
                "{}",
                serde_json::to_string_pretty(&loadout)
                    .map_err(|e| Error::json("loadout", e))?
            );
            Ok(())
        }
        LoadoutsCommand::Switch { id } => {
            let id = parse_id(&id)?;
            store.set_active(&id)?;
            println!("Active loadout is now {id}.");
            Ok(())
        }
    }
}

fn format_played(ms: u64) -> String {
    let minutes = ms / 60_000;
    if minutes >= 60 {
        format!("{}h {:02}m", minutes / 60, minutes % 60)
    } else {
        format!("{minutes}m")
    }
}
