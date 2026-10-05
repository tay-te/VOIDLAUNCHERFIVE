# VOID Atlas — server map rules

Servers have long told map mods what they may show: no cave view, no radar of other players, no minimap at all. Atlas
honours the conventions other map mods already use, so a server set up for Xaero's Minimap or VoxelMap restricts
Atlas too without doing anything for it. Atlas also has one small code of its own, for servers that want to **allow**
the radar.

The engine is `dev.voidmc.atlas.core.rules` (`ServerRules`, `ServerCode`, `Restrictions`), pure Java and unit tested
(`ServerRulesTest`).

## What Atlas does by default

| | Single player | Multiplayer, server says nothing |
|---|---|---|
| Minimap, full-screen map, waypoints | on | on |
| Cave (underground) view | on | **on**, unless a server restricts it |
| Radar: other players | on | **off**, unless a server opts in |
| Radar: mobs and animals | on | **off**, unless a server opts in |

When several sources speak, the **most restrictive wins**: a "no" beats a "yes" whatever the order, so a server that
sends both Xaero's fair-play code and Atlas's radar code gets no radar. Codes stay in force until a reset code
(Xaero's) or until the player leaves the server.

## Codes in text

Each code is a run of formatting codes: `§` followed by a character. The game draws none of it, so players never see
the code (the client skips `§` and the character after it, whether or not that character is a real formatting code).

**Where Atlas looks:** text the *server* sends: system and login messages in chat, the MOTD shown on joining, and the
tab-list header and footer. **Never** player chat (anyone could type a code there; vanilla servers refuse `§` in chat
anyway, but plugins may relay). The server-list MOTD is also fine to scan, as servers use it for this.

**How Atlas matches:** exactly, the way the mods that defined the codes do (they test whether the message *contains*
the code). The only leniency is case: `§F§A§I§R...` counts like `§f§a§i§r...`, since the game treats code letters the
same in either case. A code with something inserted inside it (another colour code, a space where there is none) is
not that code. A code may sit anywhere in a longer message, next to other formatting. Where two codes overlap, the
longer one counts: Xaero's two Nether codes end and begin with the letters of the fair-play code, and sent back to back
they must not read as fair-play.

Servers usually turn `&` into `§` with a plugin (Essentials and similar); Atlas only sees what reaches the client, so
`&f&a&i&r...` arriving as literal ampersands is not a code.

### Xaero's Minimap and World Map

| Code | Meaning | What Atlas does |
|---|---|---|
| `§f§a§i§r§x§a§e§r§o` | "fair-play": cave mode off (minimap *and* world map), entity radar off | cave view off on both maps; radar off for players and mobs |
| `§x§a§e§r§o§m§m§n§e§t§h§e§r§i§s§f§a§i§r` | minimap cave mode works in the Nether despite fair-play | cave view allowed on the **minimap** in the Nether |
| `§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r` | world map cave mode works in the Nether despite fair-play | cave view allowed on the **full-screen map** in the Nether |
| `§n§o§m§i§n§i§m§a§p` | minimap off completely | minimap off (the full-screen map stays) |
| `§r§e§s§e§t§x§a§e§r§o` | reset all the flags set by these messages | forgets every code received so far (not the channel settings below) |

Xaero says to put them in "a server/system message sent to players (on login)".

**Verified** from Xaero's own mod descriptions:
[Xaero's Minimap on Modrinth](https://modrinth.com/mod/xaeros-minimap),
[Xaero's Minimap on CurseForge](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap),
[Xaero's World Map on Modrinth](https://modrinth.com/mod/xaeros-world-map) (fair-play, the world map's Nether code and
reset; the minimap page notes "The world map has a separate code for this").

### VoxelMap (and Zan's Minimap before it)

| Code | Meaning | What Atlas does |
|---|---|---|
| `§3 §6 §3 §6 §3 §6 §e` | radar off | radar off for players and mobs |
| `§3 §6 §3 §6 §3 §6 §d` | cave mode off | cave view off |

The spaces are part of the code. Sent as chat lines on login (typically through a join-message/MOTD plugin such as
Essentials' `motd.txt`, one code per line).

**Verified** in the source: MamiyaOtaru's VoxelMap history,
[`ZanMinimap.java`](https://github.com/MamiyaOtaru/voxelmap/blob/HEAD/ZanMinimap.java), checks every chat line with
`msg.contains("§3 §6 §3 §6 §3 §6 §e")` (radar) and `msg.contains("§3 §6 §3 §6 §3 §6 §d")` (caves). Server owners
confirm the same in [this Feed The Beast forum thread](https://forum.feed-the-beast.com/threads/disabling-voxelmap-radar-did-it-work-for-you.19675/latest).
Today's VoxelMap ([Updated-VoxelMap/VoxelMap](https://github.com/Updated-VoxelMap/VoxelMap)) no longer reads chat; it
uses the settings channel below. Atlas reads both.

### Rei's Minimap (legacy)

Rei's Minimap worked the other way round: cave mapping and the radar were **off** in multiplayer until the server put
a keyword in its login MOTD. The keyword is `§0§0`, then the features to enable, then `§e§f`:

| Digit | Enables | What Atlas does |
|---|---|---|
| `§1` | cave mapping | nothing extra (Atlas's cave view is on unless restricted) |
| `§2` | radar: players | **opts in** to the player radar |
| `§3` | radar: animals | not separate in Atlas: ignored |
| `§4` | radar: mobs | **opts in** to the mob radar |
| `§5` `§6` `§7` | radar: slimes, squid, other living | not separate in Atlas: ignored |

For example `§0§0§1§2§3§4§5§6§7§e§f` enables everything, `§0§0§1§e§f` only cave mapping. Atlas takes the radar digits
as an explicit opt-in, the same as its own code; a "no" from any other source still wins.

**Verified** from a secondary source only:
[Rei's Minimap on the MWP mod wiki](https://modwiki.miraheze.org/wiki/Rei's_Minimap), which quotes Rei's own
instructions (the original forum post is gone). Atlas accepts the digits in any order, at least one of them.

### Atlas's own opt-in

| Code | Spelling | What Atlas does |
|---|---|---|
| `§a§t§l§a§s§r§a§d§a§r` | "atlasradar" | radar allowed: players **and** mobs |
| `§a§t§l§a§s§m§o§b§r§a§d§a§r` | "atlasmobradar" | radar allowed: mobs only, not players |

A server that wants Atlas players to have the radar sends one of these in a system or login message, exactly as for
Xaero's codes. Both end in `§r`, so the rest of the message is not left bold or coloured by them. There is no Atlas code
for restricting: use Xaero's (fair-play, no-minimap) or VoxelMap's, which every map mod understands. Xaero's reset
clears an Atlas opt-in too.

## Plugin channels

These arrive as custom payload packets. Atlas's client side has to register the channel for a server to send them;
the engine only decodes the bytes. A settings packet replaces what the same source said before (it does not stack), and
Xaero's reset code does not clear it. Like the codes, these can only restrict: VoxelMap's or JourneyMap's "radar
allowed" is their default, not an opt-in for Atlas's radar.

### VoxelMap: `voxelmap:settings`

**Verified** in VoxelMap's own source,
[`VoxelMapSettingsPayload.java`](https://github.com/Updated-VoxelMap/VoxelMap/blob/HEAD/common/src/main/java/com/mamiyaotaru/voxelmap/packets/VoxelMapSettingsPayload.java):
one byte (0), then a JSON object as a Minecraft string (VarInt byte count, then UTF-8). Keys are applied one by one; a
packet without a key leaves it as it was. Server owners write the same keys in `config/voxelmap.json`
([VoxelMap's description](https://modrinth.com/mod/voxelmap-updated)).

| Key | Atlas, when `false` |
|---|---|
| `radarAllowed` | radar off (players and mobs) |
| `radarPlayersAllowed` | player radar off |
| `radarMobsAllowed` | mob radar off |
| `cavesAllowed` | cave view off |
| `minimapAllowed` | minimap off |
| `worldmapAllowed` | full-screen map off |
| `waypointsAllowed` | waypoints off |

Not used: `deathWaypointAllowed`, `teleportCommand`, `serverIdentity` and `worldName` (the last two name the world;
the world matcher could use them later).

### JourneyMap: `journeymap:perm_req` (unverified payload)

**Channel names verified:** JourneyMap's own issue tracker names `journeymap:perm_req`
([TeamJM/journeymap#783](https://github.com/TeamJM/journeymap/issues/783)); older versions shared one channel,
`journeymap:common`, with a type byte first.

**Payload shape not verified against JourneyMap**, which is closed source. It is taken from a third-party server
plugin that JourneyMap clients work with,
[funniray/minimap-control `JMHandler.java`](https://github.com/funniray/minimap-control/blob/HEAD/common/src/main/java/com/funniray/minimap/common/jm/JMHandler.java):
on 1.21 and later the reply is byte `42`, a boolean (is the player an operator), the effective config as a Minecraft
string of JSON, and a trailing boolean; before 1.21, and on `journeymap:common` (type byte `2`), one more byte comes
first. Values are `"ALL"`, `"OPS"` or `"NONE"` for levels and `"true"`/`"false"` for switches
([JourneyMap's server docs](https://teamjm.github.io/journeymap-docs/5.8.5/Server%20Docs/basic-usage/) describe the
same switches in an older form).

| Key | Atlas, when it says no to this player (`NONE`, `false`, or `OPS` for a non-operator) |
|---|---|
| `journeymapEnabled` | minimap and full-screen map off |
| `radarEnabled` | radar off (players and mobs) |
| `playerRadarEnabled` | player radar off |
| `mobRadarEnabled`, `animalRadarEnabled`, `villagerRadarEnabled` | mob radar off (Atlas has one switch for all creatures) |
| `caveMapping` | cave view off |
| `allowWaypoints` | waypoints off |

Not used: `surfaceMapping`, `topoMapping`, `biomeMapping` (Atlas has no separate surface/topo/biome modes),
teleport settings and radar ranges. Atlas would also have to *ask* (JourneyMap clients send a request on that channel
first); what to send is not verified either, so until it is, this decoder only matters if a server sends the reply
unprompted.

### Not implemented

- **Xaero's channels** (`xaerominimap:main`, `xaeroworldmap:main`): a third-party plugin sends a handshake, then type
  `4` with an NBT compound `{cm, ncm, r}` (cave mode, Nether cave mode, radar), per
  [minimap-control's `XaerosHandler.java`](https://github.com/funniray/minimap-control/blob/HEAD/common/src/main/java/com/funniray/minimap/common/xaeros/XaerosHandler.java).
  Unverified against Xaero's mods (closed source) and needs NBT reading on the client side; servers that use it almost
  always send the text codes too.
- **`worldinfo:world_id`** (world identity for JourneyMap and VoxelMap, per
  [MapModCompanion's notes](https://github.com/turikhay/MapModCompanion/wiki/How-it-works)): about telling worlds apart,
  not restrictions; a candidate signal for `WorldMatcher`.

## Notes for server owners

- To restrict Atlas, use the codes you would use for Xaero's Minimap or VoxelMap; nothing Atlas-specific is needed.
- To give Atlas players the radar, add `§a§t§l§a§s§r§a§d§a§r` (or the mobs-only one) to your login message. A
  fair-play or no-radar code anywhere still turns it off.
- Send codes on every join (and after a proxy switches the player to another server, send Xaero's reset first, then
  that server's codes): Atlas forgets all codes when the player disconnects.
