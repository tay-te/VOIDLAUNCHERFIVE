# Mod roster — what Lunar and Badlion actually ship, what VOID has, what to build

**Researched 2026-09-08.** Not from memory: both rosters were pulled from primary sources
this session, because both clients change them and training data on this subject is stale.

---

## 0. Method and sources

| Source | What it gave | Why it is trustworthy |
|---|---|---|
| `github.com/LunarClient/Apollo`, `api/src/main/java/com/lunarclient/apollo/mods/impl/*.java` | **98 Lunar mods**, each with Lunar's own one-line javadoc and its full server-settable option list | Apollo is Lunar's own server API; the mod classes are generated against the shipping client and the repo was last pushed 2026-08-17. This is the roster, not a blog's guess at it. |
| `badlion.net/free-minecraft-mods`, Wayback snapshot `20260307033601` | **57 Badlion mods**, each with Badlion's own one-liner, grouped All / PvP / Minigame / PvE / Faction | Badlion's own product page. Fetched via Wayback because badlion.net Cloudflare-403s every non-browser client. |
| `lunarclient.com/news/*`, Hypixel forums, `support.hypixel.net` | Acquisition, 2025–26 additions, anticheat posture | Hypixel's official Allowed Modifications article is Cloudflare-gated; the policy statements below are from forum/staff sourcing and are flagged where that matters. |
| This repo — `mod/.../state/ModRegistry.java`, `schema/mods.json` | VOID's thirteen and their settings | Read, not guessed. |

Cost legend, calibrated against **this** codebase (see §9):

- **S — small.** A HUD chip fed by an existing or trivially-added sensor field, or a
  settings-driven tweak through an existing mixin. Days.
- **M — medium.** A new mixin plus real game-state work, or a world-space GL pass
  (`render/`), or new input/camera behaviour. A week-ish each.
- **L — large.** Its own subsystem: a renderer, a network layer, a physics change, a
  hosted backend, or a third-party dependency you now own forever. Not a mod.

---

## 1. The landscape changed, and it changes what "table stakes" means

**Moonsworth (Lunar Client) acquired Badlion from ESL FACEIT Group on 11 March 2025.**
As of the March 2026 snapshot, Badlion's own site says *"Badlion Client is now launchable
through Lunar Client"* and its footer reads © Moonsworth. Lunar has been porting Badlion
features across (Motion Blur, Always Swing, Bed Wars level formatting) rather than merging
the launchers.

Two consequences you should plan around:

1. **"Both clients ship it" no longer means two independent teams converged on it.** It
   means *frozen Badlion and current Lunar agree*. It is still the best table-stakes signal
   available — a player switching from either one notices the same absences — but it is a
   historical signal, not a live one.
2. **The forward-looking competitor is Lunar alone**, and Lunar is still shipping: TierTagger
   and Kill Sounds and Markers and Rewind in 2025, Knockback Trainer in 2026. The category
   Lunar is expanding into is **training and self-analysis**, which is exactly the gap §5
   argues VOID should take.

Raw counts: Lunar **98** mods, Badlion **57**, of which roughly **50 overlap**. VOID has
**13**, twelve of which sit inside that overlap. So the honest framing is not "VOID has 13
of 98" — most of Lunar's 98 are Skyblock, modern-version, or ornamental. It is **"VOID has
12 of ~50 table-stakes mods, and about 20 of the missing 38 actually matter on 1.8.9."**

---

## 2. Where VOID stands: the thirteen, checked against both rosters

Read from `ModRegistry.java` and `schema/mods.json`, not assumed.

| VOID mod | Category / kind | Lunar equivalent | Badlion equivalent | Depth vs theirs |
|---|---|---|---|---|
| `fps` | HUD / hud | `fps` | FPS | **Ahead.** We ship a 1% low; neither does. |
| `keystrokes` | HUD / hud | `keystrokes` | Keystrokes | Behind on chrome — Lunar has press animation, fade delay, per-state colours. |
| `cps` | HUD / hud | `cps` | CPS | Near parity. Lunar adds `ignore-cancelled-clicks`, which matters for honest CPS. |
| `ping` | HUD / hud | `ping` | Ping | **Behind.** Lunar has spike detection with two thresholds, rolling averages, and a nametag mode. We have `good_ms` / `bad_ms`. |
| `coordinates` | HUD / hud | `coordinates` + `direction-hud` | Coordinates + Direction | Parity; ours folds direction in as a boolean where theirs is a separate placeable mod. |
| `armor_status` | HUD / hud | `armorstatus` | ArmorStatus | Near parity. Theirs allows moving each armour piece individually. |
| `potion_effects` | HUD / hud | `potion-effects` | PotionStatus | Behind on breadth (Lunar has per-effect exclusion, blink-on-expiry) but ours is legible. |
| `watermark` | HUD / visual | — | — | Ours alone. Branding, not a feature. |
| `toggle_sprint` | PvP / gameplay | `toggle-sneak` (covers both) | ToggleSprint + ToggleSneak | Behind: Lunar has double-tap activation and keybind overrides; sneak is a sub-boolean for us. |
| `hitboxes` | PvP / gameplay | `hitbox` | Hitboxes | **Far behind in breadth** — Lunar has per-entity-class config, hittable/damaged colouring. Ours is one colour and a width. Breadth here is cheap and mostly pointless; hittable-colour is the one worth stealing. |
| `crosshair` | Visual / gameplay | `crosshair` | Crosshair | Parity, and ours is cleaner. Lunar adds friendly/enemy colouring (server-driven). |
| `fullbright` | Visual / gameplay | `lighting` (`full-bright`, `brightness-boost`) | Fullbright | Parity. Already classed `grey` in our schema — correctly, see §6.1. |
| `zoom` | Utility / gameplay | `zoom` | Zoom | **Behind.** Lunar has variable zoom (scroll to adjust) and camera-sensitivity scaling. Both are S and both are noticed. |

Cross-cutting observation: nearly every Lunar HUD mod carries the same chrome block —
background, border, thickness, text shadow, brackets, per-element colours. VOID gives every
HUD mod `scale` / `opacity` / `color`. **That is a depth gap, not a roster gap**, and it is
worth exactly one shared decision rather than 20 per-mod ones. Do not solve it 20 times.

---

## 3. The full roster, grouped as our registry groups it, ranked by value to a PvP player

`L` = Lunar, `B` = Badlion, `V` = VOID. Ranked by PvP value inside each group, not
alphabetically. Everything below the horizontal rule in each group is on the roster for
completeness and should not be built.

### 3.1 HUD

| # | Mod | What it does | L | B | V | Cost | Verdict |
|---|---|---|:-:|:-:|:-:|:-:|---|
| 1 | **Combo counter** | Counts consecutive hits landed on one player inside a window | ✓ | ✓ | ✗ | S | **Build.** Cheapest real PvP readout on the list; we already track hit events for CPS-adjacent state. |
| 2 | **Reach display** | Shows the distance you attacked from | ✓ | ✓ | ✗ | M | **Build**, class it `grey`. Needs an attack-event hook plus interpolated positions to be honest. See §6.1 on why the number is a lie if you do it lazily. |
| 3 | **Saturation** | Hunger *and* saturation bars, plus held-food values | ✓ | ✓ | ✗ | S | **Build.** In 1.8 combat, saturation is regen, and vanilla hides it. |
| 4 | **Potion/soup counter** | Counts healing pots or soups in inventory | ✓ | ✗ | ✗ | S | **Build.** Pot PvP is a first-class 1.8.9 mode; the count is what you plan around. |
| 5 | **Item counter** | Counts a chosen item (blocks, pearls, gapples) in inventory | ✓ | ✓ | ✗ | S | **Build.** Same engine as #4 — do them together. |
| 6 | **Momentum / BPS** | Current horizontal speed | ✓ | ✗ | ✗ | S | **Build.** Bridging, speed-pot uptime, KB testing. Underrated. |
| 7 | **Scoreboard customiser** | Hide / move / scale / recolour the sidebar | ✓ | ✓ | ✗ | M | **Build.** The vanilla sidebar covers the right third of the screen. |
| 8 | **PvP info** | Session PvP statistics | ✓ | ✗ | ✗ | M | **Build, then go past it.** Lunar's has exactly two options — it is a stub. See §5. |
| 9 | **Server address** | Current server IP on the HUD | ✓ | ✓ | ✗ | S | Build. Trivial, universally shipped. |
| 10 | **Memory** | Heap used / total | ✓ | ✓ | ✗ | S | Build. Trivial and expected next to an FPS chip. |
| 11 | **Stopwatch / timers** | Manual timers; Badlion's variant times Bedwars spawners | ✓ | ✓ | ✗ | S | Build the generic stopwatch. Skip the Bedwars-specific one. |
| 12 | **Direction HUD** | Cardinal facing as its own placeable chip | ✓ | ✓ | ~ | S | **Split out.** We have it as `coordinates.show_direction`; theirs is independently placeable, which is the point of a HUD editor. |
| 13 | **Item tracker / item info** | Shows items picked up or dropped, with counts | ✓ | ✓ | ✗ | M | Later. Nice in Bedwars, invisible in a 1v1. |
| 14 | **Chat customiser** | Chat width/height/opacity/fade, message stacking | ✓ | ✓ | ✗ | M | Later. Real, but not combat. |
| 15 | **Tab list customiser** | Rebuild the tab overlay: ping numbers, heads, colours | ✓ | ✗ | ✗ | M–L | Later. Lunar's has 35 options; it is a mod-sized rewrite of a vanilla screen. |
| 16 | **Boss bar / Titles / Action bar movers** | Move, scale or hide vanilla overlays | ✓ | ✓/✗ | ✗ | M / M / S | Later, and only once the HUD editor can own vanilla elements. Three mods, one mechanism. |
| 17 | **Pack display** | Shows the active resource pack | ✓ | ✓ | ✗ | S | Later. Cheap, ornamental. |
| 18 | **LevelHead / Hypixel HUD** | Hypixel level above nametags, network integrations | ✓ | ✓ | ✗ | M | Later, and only if you commit to being a Hypixel client specifically. |
| — | Clock, Playtime, Day counter | Local time; hours played; in-world day count | ✓ | ✓/✗ | ✗ | S | **Ornaments.** Ship them the week you have nothing better; they win zero fights. |
| — | F3 display customiser | Move/restyle the debug screen | ✓ | ✗ | ✗ | M | Skip. |
| — | TierTagger | PvP tier from pvptiers.com on nametags | ✓ | ✗ | ✗ | M | **Don't.** See §6.2 — you inherit someone else's API and its rate limits and its downtime. |
| — | Minimap, Waypoints | World map / beacons | ✓ | ✓ | ✗ | L | **Never.** See §6.1. |
| — | Totem counter, Horse stats, Shulker preview, WAILA, Light overlay, Chunk borders, TNT countdown, UHC overlay | Modern-version or non-PvP readouts | ✓ | partly | ✗ | — | Out of scope on 1.8.9 or out of scope for PvP. Several (totems, shulkers) do not exist in 1.8.9 at all. |

### 3.2 PvP

| # | Mod | What it does | L | B | V | Cost | Verdict |
|---|---|---|:-:|:-:|:-:|:-:|---|
| 1 | **Old / 1.7 animations** | Reverts swing, block-hit and item-use animation to 1.7 behaviour; Lunar's adds `use-item-while-digging` and `always-swing` | ✓ | ✓ | ✗ | M | **Build this first.** It is the single most-noticed absence in a 1.8 PvP client. A large fraction of the target audience considers 1.7 animations non-negotiable, and its absence reads as "this client was made by someone who does not play." |
| 2 | **FOV changer / static FOV** | Lock FOV so sprint and speed stop punching the camera; per-state overrides | ✓ | ✓ | ✗ | S | **Build second.** Small, and every serious player turns it on. Lunar has 25 options here; you need about four. |
| 3 | **Toggle sneak, first-class** | Sneak as its own toggle with its own keybind, double-tap activation | ✓ | ✓ | ~ | S | **Promote it.** `toggle_sprint.sneak_too` is a boolean where competitors ship a separate mod with a separate bind. |
| 4 | **Freelook / Perspective** | Look around without turning the player | ✓ | ✓ | ✗ | M | **Build.** Camera-only, allowed, universally used. Note it needs a camera-detach in `GameRendererMixin` territory, not a HUD widget. |
| 5 | **Hit color** | Recolour the red damage flash on entities you hit | ✓ | ✓ | ✗ | M | Build. Entity-render tint; genuinely helps read whether a hit landed. |
| 6 | **Knockback / jump-reset trainer** | Tells you whether your jump landed early, late or on the tick you were hit; graphs distance-from-ground against hits; session timing-error stats | ✓ (2026) | ✗ | ✗ | M | **Build**, and go further — §5. This is Lunar's newest PvP mod and the clearest signal of where the category is going. |
| 7 | **Damage tint** | Vignette when low on health, optional heartbeat audio | ✓ | ✗ | ✗ | S | Build. Cheap, and "I did not notice I was at 3 hearts" is a real way to lose. |
| 8 | **Hurt cam control** | Disable or tune the hurt-camera shake and tilt | ✓ | ✗ | ✗ | S | Build. Trivially small, immediately felt. |
| 9 | **Snaplook** | Hold a key for a third-person look, release to snap back | ✓ | ✗ | ✗ | S* | Build **after** freelook — it is the same camera machinery with a different input mode. |
| 10 | **Nick hider** | Hides your own (or others') name and skin locally | ✓ | ✓ | ✗ | M | Later. Client-side only, so it is safe; it is also only meaningful on Hypixel. |
| 11 | **Protection display** | Shows total protection value of your armour | ✗ | ✓ | ✗ | S | Optional. Derived from enchants you can already read, so it is fine — but it is a convenience, not an edge. |
| — | **Cooldowns** | Server tells the client what is on cooldown, client draws it | ✓ | ✗ | ✗ | L | **Don't.** Lunar's works because Apollo exists. We have a WebSocket to *our own launcher*, not to game servers. Shipping this means shipping a Minecraft server plugin and convincing networks to install it. That is a company strategy, not a mod. |
| — | **Team view / Markers** | Teammate positions on HUD; FPS-style world pings | ✓ | ✗ | ✗ | L | Same reason. Both are server-driven. |
| — | **MLG cobweb** | Tells you when a cobweb MLG will save you | ✗ | ✓ | ✗ | M | **Don't.** It computes something the player cannot, which is precisely the definition Hypixel uses for a disallowed information mod. |
| — | **AutoGG / AutoText / AutoTip** | Send chat on triggers or a hotkey; auto-tip on Hypixel | ✓ | ✓ | ✗ | S | **Risky, see §6.1.** Cheap to build, and cheap is why it is tempting. Hypixel's written rule is "no macros of any kind". |
| — | **Attack indicator, Shields** | 1.9+ combat features | ✓ | ✗ | ✗ | — | Do not exist in 1.8.9. |

### 3.3 Visual

| # | Mod | What it does | L | B | V | Cost | Verdict |
|---|---|---|:-:|:-:|:-:|:-:|---|
| 1 | **The "overlay" grab-bag** — minimal view bobbing, lower/hide fire overlay, hide own armour pieces, hide stuck & ground arrows, disable damage overlay | A dozen independent visual suppressions of things that block your view mid-fight | ✓ (one mod: `overlay-mod`) | ✗ | ✗ | S each | **Build. Highest value-per-hour on this entire page.** Each is a settings-driven suppression in an existing render path. Fire overlay alone decides fights. Lunar bundles ~50 of these into one mod; ship the six that matter as one VOID mod and stop. |
| 2 | **GUI scale** | Hotbar and inventory scaled independently of Minecraft's GUI scale | ✓ | ✗ | ✗ | S | Build. Small, and it fixes the "my hotbar is enormous at 1080p" complaint permanently. |
| 3 | **Block outline** | Colour, thickness, or removal of the block-targeting outline | ✓ | ✓ | ✗ | M | Build. World-space GL, so it costs more than it looks — but it is table stakes. |
| 4 | **Nametag customiser** | Hide, scale, recolour, or de-background nametags | ✓ | ✗ | ✗ | M | Build. Nametag clutter in team modes is a real visibility problem. |
| 5 | **Menu / inventory blur** | Blurs the world behind menus | ✓ | ✓ | ~ | S | **Finish what exists.** `screen/BlurBackdrop.java` is already here; it needs to become a registry mod with settings. |
| 6 | **Clear glass** | Glass without the frame texture, without a resource pack | ✓ (in `overlay-mod`) | ✓ | ✗ | M | Build. Bedwars-relevant, allowed, and a resource-pack behaviour people expect built in. |
| 7 | **Fog customiser** | Reduce or remove water / distance / dimension fog | ✓ | ✗ | ✗ | M | Build, class it **`grey`**. It increases what you can see beyond vanilla — same posture as fullbright. |
| 8 | **Shiny pots** | Enchantment glint on potions so they read at a glance in the hotbar | ✓ | ✓ | ✗ | S | Cheap. Genuinely useful in pot PvP for a small change. |
| 9 | **Enchant glint colour / off** | Recolour or disable the enchantment shimmer | ✓ | ✓ | ✗ | M | Cheap-ish, cosmetic. Later. |
| 10 | **Time / Weather changer** | Fix time of day; disable rain | ✓ | ✓/✗ | ✗ | S | Later. Rain removal is a small visibility win and is aesthetic, so it is safe. |
| 11 | **Particle changer** | Reduce, recolour or add attack particles | ✓ | ✓ | ✗ | M | Later. Badlion's variant *adds* particles on hit, which is a hit-confirmation cue — mildly useful. |
| — | **Motion blur** | Blurs the frame when the camera moves | ✓ | ✓ | ✗ | L | **Don't build it, and be glad.** It needs a full-screen accumulation pass every frame. Our window is already compositing an Ultralight surface over the game (see `design/rendering-invariants.md`); adding a second full-screen pass is the exact thing our render budget cannot absorb. It is also a mod that makes a PvP player *worse*. |
| — | **Color saturation** | Global post-process saturation | ✓ | ✗ | ✗ | M–L | Skip. Same post-process cost, less payoff. |
| — | **Item physics, 2D items, 3D skins** | Dropped items get physics; items render flat; skins get voxel depth | ✓ | ✓/✗ | ✗ | L | **Don't.** Each is its own renderer or simulation, wearing the word "mod". Zero competitive value. |
| — | **Mob size** | Rescales mob and player models | ✓ | ✗ | ✗ | M | **Don't.** Render-only, but it changes where a player *looks* like they are relative to where they can be hit. A PvP client shipping this invites exactly the accusation you cannot afford. |
| — | **Item customizer** | Held/dropped item render and animation tweaks | ✓ | ✗ | ✗ | M | Skip. |

### 3.4 Utility

| # | Mod | What it does | L | B | V | Cost | Verdict |
|---|---|---|:-:|:-:|:-:|:-:|---|
| 1 | **Zoom depth** | Variable zoom (scroll to adjust) and reduced mouse sensitivity while zoomed | ✓ | ✗ | ~ | S | **Finish ours.** Two settings, and the absence of sensitivity scaling is felt every single time you zoom. |
| 2 | **Scrollable tooltips** | Scroll wheel reads long item descriptions | ✓ | ✗ | ✗ | S | Cheap. Ship it whenever. |
| 3 | **Inventory slot lock** | Lock hotbar/inventory slots so you cannot drop your sword | ✓ | ✗ | ✗ | S–M | Later. Real value in Bedwars and UHC. |
| 4 | **Kill sounds / Sound changer** | Custom sound on a kill; per-sound volume control | ✓ | ✗ | ✗ | M | Later. Kill sounds is a *retention* feature — people share them — but it needs an audio asset pipeline. |
| — | **Screenshot uploader** | Instantly upload a screenshot and get a link | ✓ | ✗ | ✗ | M + backend | **Don't yet.** The mod is medium; the hosted endpoint, the storage bill, the abuse moderation and the takedown path are the actual project. |
| — | **Pack organizer, Quickplay, Mumble link** | Resource-pack folders; Hypixel game shortcuts; positional voice | ✓ | ✓/✗ | ✗ | M | Skip. |
| — | **Replay Mod / Rewind** | Record and replay sessions from any camera | ✓ | ✓ | ✗ | L | **Never.** A recorder, a timeline UI, a camera system, a keyframe interpolator and a video encoder. Lunar wrote Rewind in-house *after* years of maintaining a ReplayMod fork, and they have a team. |
| — | **Schematica, WorldEdit CUI** | Build-assist overlays | ✗/✓ | ✓/✗ | ✗ | L | Never. Not PvP. |
| — | **Skyblock / NEU / SBA** | Hypixel Skyblock feature suites | ✓ | ✓ | ✗ | L | Never. This is a different product. Lunar ships three of them because Lunar is not only a PvP client. VOID is. |
| — | **Radio** | Licensed music streaming | ✓ | ✗ | ✗ | L | Never. Music licensing. |
| — | **TeamSpeak, Clan Wars** | TS3 integration; GommeHD overlay | ✗ | ✓ | ✗ | — | Never. Both are dead-server artefacts of Badlion's frozen roster. |
| — | **Name History** | Shows a player's past usernames | ✗ | ✓ | ✗ | S | **Never — it cannot work.** Mojang removed the name-history API endpoint in 2022. Badlion still lists it. This is the clearest evidence that Badlion's roster is a historical document, not a spec. |

---

## 4. What is table stakes — the plough-through list

These appear on **both** clients and are absent from VOID. A player arriving from either
client notices these missing. Ordered by how badly they are noticed on 1.8.9:

1. **Old / 1.7 animations** (M) — the one that makes the client feel wrong without it
2. **FOV changer / static FOV** (S)
3. **Combo counter** (S)
4. **Reach display** (M)
5. **Freelook / perspective** (M)
6. **Toggle sneak as its own mod** (S)
7. **Saturation** (S)
8. **Hit color** (M)
9. **Block outline** (M)
10. **Scoreboard customiser** (M)
11. **Item counter** (S)
12. **Server address** (S)
13. **Memory** (S)
14. **Stopwatch** (S)
15. **Direction HUD split out** (S)
16. **Menu blur finished** (S)
17. **Nick hider** (M)
18. **Shiny pots** (S)
19. **Clear glass** (M)
20. **Pack display, Clock, Chat customiser, Time changer, Enchant glint, Boss bar, Particles** — the long tail. Ship for completeness, expect no thanks.

Deliberately excluded from the table-stakes list despite appearing on both: **Minimap,
Waypoints, Replay, Item Physics, Motion Blur, MumbleLink, Schematica, AutoTip**. See §6.

Fifteen of the first nineteen are S or one-mixin M. **That is the roster problem: it is
mostly volume, not difficulty.** Which is why §9 matters more than any individual row here.

---

## 5. What is genuinely differentiating

Two things are true at once: almost nothing on either roster is a differentiator (they are
the same 50 mods), and the *category* Lunar entered in 2026 is wide open.

**The differentiator is not a mod. It is self-analysis.** Lunar's Knockback Trainer — jump
timing feedback, a distance-from-ground graph with your hits overlaid, session stats with
average timing error in ticks — was their headline 2026 PvP feature and it covers exactly
one mechanic. Nobody, on either client, ships:

| Idea | What it is | Cost | Why it is ours to take |
|---|---|---|---|
| **Fight review** | A post-fight card: hits landed / taken, average reach, CPS through the fight, sprint-reset rate, time-to-kill, where it went wrong | M–L | Lunar's `pvp-info` has **two settings**. It is a stub. We already run an Ultralight surface and a desktop launcher — a review screen is a page, not a renderer. This is the one place our architecture is straightforwardly better suited than a plain Fabric mod. |
| **Click analytics** | Interval histogram of *your own* clicks, jitter spread, drag/butterfly detection, consistency over a session | S–M | Purely your own input, so it is unimpeachably safe. We already window clicks for `cps.window_ms`. Nobody ships it and every player is curious. |
| **Sprint-reset (W-tap) feedback** | The other half of knockback control: did your W-tap land in the window, how often, drifting over a session | M | Lunar covers jump resets only. This is the obvious adjacent mechanic and it is unclaimed. |
| **Connection quality, not a ping number** | Jitter band, packet-loss estimate, tick-skip / "the server is behind" indicator | S–M | Careful: Lunar's `ping` mod *does* have spike detection with two thresholds and rolling averages, so "we show ping spikes" is not new. Jitter distribution, loss and tick-skip **are**. We already have `net/SessionStats` and `sensor/ServerWatcher`. |
| **Input latency readout** | Actual input-to-action latency | S | `input/InputLatency.java` **already exists in this repo.** Neither competitor exposes anything like it. This is close to free. |
| **Loadout as a shareable artifact** | Export/import a full loadout — mods, settings, HUD placement — by code | S–M | Lunar has profiles; neither has sharing. The Electron launcher already has a share-code system to copy from. Pure product work, no game code. |

Of Lunar's own recent additions, the ones players actually talk about are **TierTagger**
(social status, not gameplay) and **Kill Sounds** (shareable, personality). Both are
retention features rather than PvP features. Worth knowing what "talked about" actually
means: it is rarely the mod that wins fights.

---

## 6. What we should not build

Four separate reasons, and they get conflated constantly.

### 6.1 Anticheat-sensitive or outright disallowed

Hypixel's Allowed Modifications policy is **allowlist-shaped**: three permitted categories —
performance improvements, purely aesthetic changes, and cosmetic HUD changes *that add no
information otherwise unavailable* — and "if it does not fit a category, assume it is
disallowed." Our `schema/mods.json` already encodes this as `safe` / `grey` and gates the
HYPIXEL-READY badge on it. Keep doing that.

- **Minimap and Waypoints.** Both competitors ship them. Ship them anyway and the
  HYPIXEL-READY badge becomes a lie. Community and staff sourcing says minimaps were
  removed from the allowed list *entirely* — not merely "with entity radar off". **Verify
  against the official article before you act on this**, because I could not fetch it
  (Cloudflare), but the downside is asymmetric: a banned mod on a client that advertises
  Hypixel-readiness is a product-ending mistake, and a minimap is an L anyway.
- **Damage numbers.** Frequently requested, disallowed on Hypixel. Do not.
- **Reach display, honestly.** It is allowed as a HUD readout of your *own* attack distance
  — but if you implement it by ray-marching toward an entity rather than reporting an actual
  attack event, you have built a reach *indicator*, which is the disallowed thing. Class it
  `grey`, compute it only from a landed attack, never predict.
- **Fullbright, which we already ship.** Our schema calls it `grey`. That is correct and
  worth remembering: gamma beyond vanilla is specifically named in Hypixel's disallowed
  discussion. Our range goes to 15. This is an existing exposure, not a hypothetical.
- **AutoGG / AutoText / AutoTip.** Hypixel's rule text is "no macros of any kind are allowed
  on the network." AutoGG has been tolerated for a decade, which is not the same as allowed.
  For a client whose landing page says HYPIXEL-READY, an S-cost mod is not worth the
  ambiguity.
- **MLG cobweb.** Computes a save the player cannot compute. Textbook "additional data".
- **Mob size.** Render-only and therefore technically fine — and it will get you accused of
  shipping hitbox manipulation the first time a clip goes around.
- **Never, under any framing:** extended reach, tracers/ESP, chest or player radar,
  auto-potion, auto-soup, velocity/knockback modifiers, aim assist, autoclickers, AFK
  ping mods. These are cheats. A PvP client that ships one of them is a cheat client with a
  launcher, and the reputation is unrecoverable.

### 6.2 Enormous projects wearing the word "mod"

Anything needing a renderer, a network layer, a physics change or a hosted backend:

**Replay / Rewind** (recorder + timeline + camera system + encoder) · **Minimap /
Waypoints** (chunk raster cache + own renderer) · **Motion blur / Color saturation**
(full-screen post-process, against a frame budget already carrying an Ultralight
composite) · **Item physics** (simulation) · **3D skins / 2D items** (model pipeline) ·
**Cooldowns / Team view / Markers** (require a Minecraft *server* plugin — our `net/` layer
is a WebSocket to our own Rust launcher, not to game servers) · **Tab list rewrite** ·
**Screenshot uploader** (backend, storage, abuse moderation) · **Radio** (licensing) ·
**Mumble link** (audio subsystem) · **Skyblock / NEU / SBA** (a different product) ·
**TierTagger** (a third-party API you now depend on and cannot fix).

### 6.3 Everybody ships it and it is pointless anyway

Day counter · Playtime · Clock · Horse stats · WAILA · Light overlay · Chunk borders ·
2D items · Weather changer · Pack organizer · Item physics · Name History (the endpoint
it needs was deleted in 2022). Ship the cheap ones for roster-count optics if you must, but
do not let them displace §4 or §5.

### 6.4 Does not exist in 1.8.9

Attack indicator (1.9 attack cooldown) · Shields · Totem counter · Shulker preview ·
Chorus fruit / spyglass / frost overlay options · Audio subtitles (vanilla subtitles are a
post-1.8 feature, so this is not a customiser — it is a from-scratch subtitle system).
Roughly **a quarter of Lunar's 98 mods are irrelevant to us for version reasons alone**,
which is the real answer to "they have 98 and we have 13."

---

## 7. Suggested order

**Wave 0 — pay the tax first (§9).** Generate `ModRegistry.java` and
`crates/void-loadout/src/mods.rs` from `schema/mods.json` the way `@void/protocol` already
generates its TypeScript. Do this *before* adding twenty mods, not after.

**Wave 1 — the four that change how the client feels.** Old animations · FOV changer ·
Toggle sneak promoted · the overlay grab-bag (view bobbing, fire overlay, own armour,
arrows).

**Wave 2 — the cheap HUD sweep, one PR.** Combo · Saturation · Item counter · Potion
counter · Momentum · Server address · Memory · Stopwatch · Direction split out. Nine mods,
all S, all the same shape. This is where a generated registry pays for itself.

**Wave 3 — the medium PvP set.** Reach display · Freelook (then Snaplook) · Hit color ·
Damage tint · Hurt cam · Scoreboard.

**Wave 4 — the differentiator.** Fight review · click analytics · W-tap trainer ·
connection quality · surface the input-latency number we already compute.

**Wave 5 — long tail, only if the roster count matters commercially.** Chat, tab, boss bar,
titles, pack display, glint, clear glass, particles, time changer, nick hider, scrollable
tooltips, inventory lock, kill sounds.

**Never:** §6.

---

## 8. Honest read on the roster gap

VOID is not thirteen mods behind ninety-eight. Strip Skyblock, modern-version and
ornamental mods from Lunar's 98 and the 1.8.9-PvP-relevant roster is around **45**. VOID
has 13 of them and is *ahead* on two (1% low FPS, and an HUD editor with live per-mod
previews that neither competitor matches).

The real gaps are four:

1. **1.7 animations.** Not having it is disqualifying for a meaningful slice of the audience.
2. **The cheap sweep** — about a dozen S-cost readouts whose absence reads as unfinished.
3. **Depth on the mods we already have** — zoom sensitivity, ping jitter, HUD chrome. These
   are *not* roster items and will not show up on a feature-count comparison, but they are
   what a returning player notices in the first ten minutes.
4. **Nothing anybody would switch clients for.** §5 is the answer to that, and it is the
   only section on this page that is not catch-up work.

---

## 9. Before you add twenty mods: the per-mod tax

Adding one mod id to this repo today touches **~25 files across five packages**:

```
schema/mods.json                              (the contract, + examples[0])
schema/loadout.json, protocol.json            ($ref'd enums)
mod/.../state/ModRegistry.java                 hand-transcribed
mod/.../state/LiveState.java                   actuator wiring, if gameplay
mod/src/test/.../LiveStateTest.java            + ModRegistryTest
crates/void-loadout/src/{mods,loadout,store}.rs
packages/protocol/src/mods.ts + generated/*    (generated — fine)
packages/ingame/src/registry.ts
packages/ingame/src/icons.tsx, styles/icons.css, scripts/build-icons.py
packages/ingame/src/menu/{ModSettingsScreen,TilePreview,gameplay-previews}.tsx
packages/ingame/src/dev/fake-mods.ts
packages/ingame/test/{preview,screens}.test.tsx
packages/ui/src/components/Icon.tsx, gallery/main.tsx
apps/desktop/src/local/registry.ts, mocks/fixtures.ts, screens/Mods.tsx
apps/desktop/src/stores/stores.test.ts
scripts/verify-mods.mjs
```

Only the TypeScript is generated. `ModRegistry.java` says so itself — *"schema/mods.json
stays the source of truth: when it changes, this file changes with it and ModRegistryTest
is what catches the drift"* — which is a test compensating for hand-transcription.
`mods.rs` at least `include_str!`s the schema, so the Rust side is already close to right.

**Twenty mods × that tax is the actual project**, and it is larger than the sum of the
twenty mods. Generate the Java registry and the icon plumbing from `schema/mods.json` first.
Everything in §7 gets roughly twice as fast, and Wave 2 stops being a slog.

One more thing to settle *once* rather than twenty times: the HUD chrome block. Lunar gives
essentially every HUD mod background / border / thickness / text-shadow / brackets /
per-element colours. We give every HUD mod `scale` / `opacity` / `color`. Decide whether
VOID matches that depth as a **shared HUD-item property set** before you write the ninth
HUD widget, or you will retrofit it into twenty settings pages later.

---

## Appendix — full source rosters

**Lunar, 98 mods** (`Mods.ALL_MODS`, Apollo `master` @ 2026-08-17): 2d-items, 3d-skins,
action-bar, armorstatus, attack-indicator, audio-subtitles, auto-text-actions,
auto-text-hotkey, block-outline, bossbar, chat, chunk-borders, clock, color-saturation,
combo, cooldowns, coordinates, cps, crosshair, damage-tint, day-counter, direction-hud,
f3-display, fog, fov, fps, freelook, glint-colorizer, gui-scale, hit-color, hitbox,
horse-stats, hurt-cam, hypixel-bedwars, hypixel-mod, inventory-mod, item-counter,
item-customizer, item-physics, item-tracker, keystrokes, kill-sounds, knockback-trainer,
light-overlay, lighting, markers, memory, menu-blur, minimap, mob-size, momentum,
motion-blur, mumble-link, nametag, neu, nick-hider, one-seven-visuals, overlay-mod,
pack-display, pack-organizer, particle-changer, ping, playtime, potion-counter,
potion-effects, pvp-info, quickplay, radio, reach-display, replaymod, rewind, saturation,
sba, scoreboard, screenshot, scrollable-tooltips, server-address, shields, shiny-pots,
shulker-preview, skyblock, snaplook, sound-changer, stopwatch, tab, team-view, tier-tagger,
time-changer, titles, tnt-countdown, toggle-sneak, totem-counter, uhc-overlay, waila,
waypoints, weather-changer, worldedit-cui, zoom.

**Badlion, 57 mods** (badlion.net/free-minecraft-mods, 2026-03-07): Animations, ArmorStatus,
AutoGG, AutoText, AutoTip, Block Overlay, BossBar, CPS, Chat, Chunk Borders, Clan Wars,
Clear Glass, Clock, Combo Counter, Coordinates, Crosshair, Direction, EnchantGlint,
FOV Changer, FPS, Fullbright, Hit Color, Hitboxes, InventoryBlur, Item Counter, Item Info,
ItemPhysic, Keystrokes, LevelHead, MLG Cobweb, Memory, MiniMap, MotionBlur, MumbleLink,
Name History, NickHider, Pack Display, Particles, Perspective, Ping, PotionStatus,
Protection, Reach Display, Replay, Saturation, Schematica, Scoreboard, Server Address,
Shinypots, SkyblockAddons, Stopwatch, TeamSpeak, TimeChanger, Timers, ToggleSneak,
ToggleSprint, Waypoints, Zoom.

**VOID, 13** (`ModRegistry.java`): fps, keystrokes, cps, ping, coordinates, armor_status,
potion_effects, watermark, toggle_sprint, fullbright, hitboxes, zoom, crosshair.
