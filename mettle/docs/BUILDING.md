# Building VOID Mettle (for people and agents)

How work on Mettle is split, built and checked. Read ARCHITECTURE.md first; this is the practical half.

## Parallel work

- Each build (a skill tree, the burden module, the gear module…) happens in its **own git worktree and branch**
  (`.claude/worktrees/mettle-<unit>` on `mettle-<unit>`), made from `mettle`. The coordinator merges into `mettle`.
- Never touch the main checkout `C:\Users\bitto\VOID`, other worktrees, or run `git stash` / `checkout` / `reset`.
- **Owned files.** The scaffold gives every unit its own files: a module class, mixin configs
  `mettle-<unit>.mixins.json` and `mettle-<unit>.client.mixins.json` (registered), mixin packages
  `dev.voidmc.mettle.mixin.<unit>` / `dev.voidmc.mettle.client.mixin.<unit>`, a client class
  `client.<unit>.<Unit>Client` (called from MettleClient), a lang fragment `lang/en_us/<unit>.json`, a data folder,
  and a tour journey `gametest/<Unit>Journey.java` (called from MettleTour). Edit only those, plus new files in your own
  packages. Anything else: write it in `docs/requests/<unit>.md` (what, why, proposed code) and work around it.
- **Shared contracts** live in `dev.voidmc.mettle.api`: `MettleServices` (burden and gear services, weight and band
  modifier lists), `BurdenService`, `GearService`, `WeightModifier`, `BandModifier`, `BandRules`, `LoadBand`,
  `GearSlot`, `Perk`, `Skill`, `Background`. Code against them; never reach into another unit's package.

## Patterns to copy

- Perk data: `data/mettle/mettle/perk/<skill>/<id>.json` (see `api/Perk.java`, the Mining files). Lang keys
  `mettle.perk.<skill>.<id>`, `.description` (ranked: `%s` + `rank_values`; a literal % is `%%`), `.hint` (feats),
  `mettle.path.<skill>.a|b`, chips `mettle.feedback.<skill>.<id>`. Path names go in `data/mettle/mettle/skill/<skill>.json`
  under `"paths"`.
- Always-on attribute perks: `effects` in the perk JSON. Conditional ones ("while holding…"): code, with your own
  modifier ids `mettle:<unit>/<perk>/<attribute>` (EffectsApplier removes `mettle:perk/*` ids it didn't add).
- XP: `Progress.award(player, skill, baseXp)` (applies favoured and Wits). Feats: `FeatCounters.add(player, counter, n)`
  grants feat perks whose `feat.counter` matches. Synergies unlock by themselves at the levels in their data.
  Teachings: `/mettle perk grant` until trainers and tomes exist.
- Feedback chips: `SignaturePerks.feedback(player, perkId, Component)` for every trigger; throttle passive ones.
- Placed blocks (anti-farm): `PlacedBlocks.mark/isPlaced/takePlaced`; only forget the blocks your unit gives XP for.
- No Fabric "modify damage" event: mixin `LivingEntity#hurtServer` (ModifyVariable on the float, HEAD, argsOnly,
  ordinal 0). Return-value hooks must **chain**: use `@ModifyReturnValue`, never a cancellable `@Inject` at RETURN.
- Per-player state in static maps keyed by UUID: clear it on `ServerPlayerEvents.LEAVE`. Cooldowns that should
  survive a relog: a persistent attachment.
- 26.3 code to grep (names changed; don't trust memory): `C:/Users/bitto/VOID/.tools/mc-src/{common,client}/net/minecraft/…`,
  Fabric API `C:/Users/bitto/VOID/.tools/fabric-src/net/fabricmc/fabric/api/…`.
- UI: the real UI is an Ultralight overlay built later (OVERLAY.md). Native screens and HUD bits are a dev fallback:
  keep them minimal and functional; never invest in their looks. Vanilla tooltips are fine and stay.

## Build and check

- `cd mettle && JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25 ./gradlew build` (JUnit included) must be green.
- JUnit for pure logic in `src/test/java`; vanilla registries need `SharedConstants.tryDetectVersion();
  Bootstrap.bootStrap();` in a static block (see PerkRulesTest).
- The client tour, `./gradlew runClientGameTest` (a few minutes, opens a Minecraft window), plays every journey on a
  real integrated server. Your journey must trigger each of your features for real and throw `AssertionError` with a
  clear message when something is off; leave the world as you found it (respec, `mettle background set mettle:ranger @a`,
  kill what you spawned, restore health and inventory). Commands from the server console that need a player:
  `execute as @p run …`. In `server.computeOnServer` you're on the server thread; never call `Minecraft.getInstance()`
  there.
- **One tour at a time on this machine:** take the lock with `mkdir C:/Users/bitto/VOID/.claude/worktrees/tour.lock`
  (atomic; if it exists, wait and retry every 30 s), run, then `rmdir` it — also when the run fails.
- Commit on your branch; end the message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
