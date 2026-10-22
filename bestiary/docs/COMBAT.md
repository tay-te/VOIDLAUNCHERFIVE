# Fray combat: stage 1B (systems and the native HUD)

Branch `bestiary-combat`. Design: `docs/arpg/` on branch `design-arpg` (CONTRACTS, COMBAT, ROADMAP decisions D1-D17),
Mettle's stat contract `mettle/docs/MYTHIC_POWER.md` §8 (branch `mettle-mythic`), the HUD in Figma file
3c0Wgc4ZKFSpm98fWJoARS, section 723:50512 "3a · ARPG stage 1". The animation lane's contract is `docs/ANIM_EVENTS.md`
(branch `bestiary-anim`).

| | |
|---|---|
| Api | `fray/src/main/java/dev/voidmc/fray/api` (`FrayApi` locator, services, `CombatEvents`, `DamageKind`, `StatusType`, `HitContext`, `BalanceProvider`, `FrayAttributes`) |
| Server | `dev.voidmc.fray.combat` (pipeline, statuses, defense, encounters, threat, profiles, telegraphs, damage profiles, commands), `.action` (player actions), `.math` (pure maths), `.net` (payloads, sync, handshake), `.mixin` |
| Client | `dev.voidmc.fray.client.combat` (input, prediction, lock-on, options), `.hud` (H1-H10), `.feel` (hit-stop, camera shake), `.mixin` |
| Unit tests | `./gradlew :fray:test` (58 combat tests: pipeline maths, guard windows at every tick, stamina, meters and Wet/Oiled, threat, phases/hold/movesets/poise, defender grace, lone-key gates, hit-stop and shake maths, status tuning) |
| Game tests | `bash tools/gametest.sh -Dfray.show.only=none -Dfray.combat=all` (or `pipeline,actions,keys,feel,hud,perf,dedicated`); report `build/run/clientGameTest/fray-report-combat.txt` |
| HUD sprites | `python tools/hud_sprites.py` → `fray/src/client/resources/assets/fray/textures/gui/sprites/hud` |
| Status tuning | `fray/src/main/resources/data/fray/fray/status_tuning/defaults.json`; Bestiary's creatures `src/main/resources/data/bestiary/fray/status_tuning/creatures.json` |
| Pictures | `docs/combat/` |

## 1. What runs

**Damage pipeline** (`Pipeline`, CONTRACTS §4.2): stages 1-9 for every Fray hit: attacker pools (add, increased, more;
Mettle's `damage.<kind>` and `melee/ranged/spell_power`; `MODIFY_DAMAGE` listeners; ×2.6 ceiling), situational (largest
of riposte, backstab, plunge, execute, charged, rolled crit; weak point on top; ×3.0 cap), guard (players), defence
(resistance attributes `fray:resist/<kind>` + Mettle's `resist.<kind>`, Hex, Frozen + blunt, `reduce` listeners, the 25 %
floor, vanilla armour and toughness per kind with Breach), statuses and poise, hand-off to `hurtServer` as `fray:combat`
(bypasses armour, enchantments, shields, cooldown and knockback; Resistance still applies), knockback by weight,
`DAMAGE_DEALT` / `KILLED`, threat, combat tags, hit packets. The 4-tick per-attacker floor replaces vanilla's cooldown for
Fray hits; foreign damage keeps vanilla's path plus the resistance step by damage profile
(`data/fray/fray/damage_profile/vanilla.json`, 32 vanilla types).

**Statuses** (`Statuses`): seven build-up meters (Bleed, Burn, Chill, Shock, Poison, Hex, Blight) with thresholds from
`fray:status_resist/<s>` (bosses ×4; all numbers from status tuning, §5.4), drain after 2 s, Wet (shock and frost ×1.5, fire ×0.5) and Oiled (fire ×2,
Conflagration); procs reuse vanilla where it exists (fire ticks, freeze ticks, Poison II, Wither). Chill stacks slow per
third; Frozen roots; Shock stuns and jumps half the hit to the nearest wet or metal-armoured target; Bleed bursts 8 % + 4.
Applied statuses: Stagger, Daze, Exposed (armour −30 %), Rooted, Silenced, Wet, Oiled, Marked. A Staggered, Dazed,
Frozen or stunned creature can't strike (vanilla AI included) and its Fray Motion clip is stopped.

**Stamina and poise**: 100 base (`fray:max_stamina` + `mettle:stamina_max`, ceiling 220), 25 a second after 12 ticks
(half guarding), Winded 30 ticks; costs per COMBAT §4/§6/§7 × Mettle's `stamina_cost.<group>` (floor 65 %). Poise by
armour class for players (10/18/28, flinch), by `fray:poise` × rank for creatures (stagger 20/14 ticks), Break meter for
bosses. Hyper-armour +20 on heavies, charged and Arts.

**Player actions** (`PlayerCombat`, server-authoritative): tap light (string per family), hold 6+ heavy, 16+ charged
(full at the family's ticks, 4 a second held, releases itself 40 ticks later), running strike, plunging strike (D16),
riposte, guard counter, roll attack, Execute; dodge roll/backstep with i-frames 1-7/1-4 and Slip (+12, +25 % next hit);
parry (shield 6 / weapon 5 ticks, spam halves) and block (shield 100 % physical, weapon 60 %, orange ×1.5, red and
purple unblockable, axes' heavy breaks a shield), guard break, feint; lock-on with server validation; Arts on sneak + C
(Whirlwind, Earthshaker; Mettle's `ArtProvider` decides who has which, cooldown × `mettle:cooldown_scale`); 6-tick input
buffer, chain / dodge-cancel / guard-cancel windows. Vanilla families from item tags (`Movesets`, COMBAT §4 table).
Hits: arc sweeps each ACTIVE tick, declared target first (+0.5 reach), line of sight, hit once.

**Defender-favoured timing** (ANIM_EVENTS.md §4, the user's call): inputs carry the client's tick; the server accepts
it clamped to the grace (round trip + 1 tick, ≤ 5 ticks, PvP ≤ 2, monotonic, never future) and judges i-frames and
windows from it; creature hits on a player are held for the grace when the round trip is above 20 ms.

**Encounters** (`Encounters`): Fray's boss bar to players within 48 blocks (phase notches, Break meter, Broken 60
ticks, status meters), start and Felled banners, damage shares, `ENCOUNTER_*` events. **Telegraphs**: `tell()` sends
the cue to the target and gives the following strike its tell flags. **Profiles**: rank, target-frame subtitle,
affixes. **Threat**: 8-entry tables, taunts, decay. **Handshake**: `fray:combat/hello` (protocol + hash of the
movesets) beside Fray Motion's clip check; a mismatch is refused with a plain message.

**Keys** (D2, decisions 1-2 in §5.1): Left Alt dodge (fires when Alt is pressed and released by itself), C technique
(sneak + C the Art; ignored while a hotbar number or F3 is held), middle mouse lock-on (scroll switches); Attack and
Guard are vanilla's attack and use keys. All three are ordinary key mappings in Controls → "VOID Combat" and can be
rebound. Conflicts found at start are logged and kept in `CombatKeys.CONFLICTS` for the Hub's settings page: C ↔
save-toolbar (and F3 + C), middle mouse ↔ pick block and the spectator hotbar. Pick block stays on Alt + middle mouse,
and outside a fight on a block or a creature that isn't hostile (`CombatInput.middleIsPick`).

**HUD** (`FrayHud`, Figma 3a): H1 stamina (cost ghost, regen-delay line, refund flash, Winded, fades when full out of a
fight), H2 health up to 40 (width grows 160 + 3 px a point, absorption, recent damage, low health, build-up meters),
H3 status row (Fray statuses then vanilla effects; vanilla's icons step aside), H4 reticle (slides on switch, fades
when lost) and target frame, H5 nameplates (nearest 10; vanilla name tags stand down for elites and bosses), H6 boss
bar, H7 banners, H8 hit marker / parry ring with the riposte arc / Slip / Blocked / Guard broken / damage numbers
(option, off: D9), H9 shape cues (circle, square, diamond, triangle) on an 84 px ring with an edge flash and a subtitle,
H10 the Art's well. Vanilla's food and air bars move up a row, armour sits above the 40-health bar, vanilla boss bars
move below Fray's, vanilla's attack indicator stays full. All shapes are baked GUI sprites (one nine-slice or three-part
pill each). The HUD's text stays in vanilla's pixel font (§5.1). Options: `config/fray-combat-client.json`.

## 2. Results (2026-10-21)

Stage 1B first pass (the later work is in §5): all 40 combat unit tests (48 with Fray Motion's) and all 42 game-test checks pass (integrated server, real input through Fabric's test input; and an
in-process dedicated server).

| | |
|---|---|
| HUD draw cost | **0.10 ms a frame** with everything up (boss bar, 20 hurt creatures with nameplates, cluster, status row, Art well); 1.3 ms before the HUD moved to sprites |
| Server, 50 creatures in combat | upkeep 0.05-0.08 ms a tick; a pipeline hit 50-77 µs of which vanilla's `hurtServer` is 23-39 µs; 10 Fray hits a tick + upkeep ≈ 0.6-0.8 ms; whole server tick 1.0-1.6 ms (shared machine) |
| Handshake | a client with other movesets is refused: "Your VOID combat rules don't match this server's (movesets 61357ab7 here, 00000000 on your side)…" |

## 3. With the animation lane (on merge)

- `MotionBridge` finds `MotionHits.setHandler` and `MotionServer.startPlayerAction` by reflection: creature timeline
  hits then run through the pipeline with the window Fray Motion judged (PERFECT → Slip, IFRAMES → dodged, PARRY →
  parried unless red/purple, or orange without a shield, BLOCK → blocked); a player's own clip volumes are ignored (the
  arc sweep decides); player actions start on Fray Motion with the defender stamp. Without them (the stage 0 runtime)
  creature hits reach Fray through `hurtServer` and `Defense` judges them on the combat side's windows.
- Clip ids follow ANIM_EVENTS.md §1.1 (`ActionClips`), falling back to `player/heavy_swing` and `player/dodge_roll`.
- Asked of the animation lane: call `FrayApi.telegraphs().tell(...)` on a timeline's `tell` event (or let combat
  listen to `MotionEvents.EVENT`) so cues come from clips; first-person framing of the swing clips (the prototype's
  heavy swing fills the screen on a light).

## 4. Not built yet, and partner requests

- Hearth: fold the quest tracker during a boss fight by reading `dev.voidmc.fray.client.combat.FrayHudState.bossFight()`
  (reflection, client). Atlas: `AtlasHud.reserve` doesn't exist yet; Fray keeps out of the top right.
- Mettle: implement `BalanceProvider`/`ArtProvider` and the §8 attributes; Fray already reads `mettle:*` where present.
  Status build-up for players: §5.4 lists the three hooks (attribute modifiers, `mettle:status_resist.<s>`,
  `fray:status_threshold`).
- Animation lane: the local player's own first-person arms don't freeze on hit-stop yet (Fray Motion samples them
  from its own clock). Wanted: a per-entity hold in `Animator` (e.g. "hold the ACTION layer until t") that Fray's
  `HitStop` can set; third-person and every other entity already freeze (§5.2).
- Not built: mana/toxicity pools (attributes only), the accessibility windows (forgiving ×1.5/×2), PvP diminishing
  returns, the stamina bar's "above the hotbar" placement, the
  Combat settings page (P1, Hub), technique on C (Arsenal, stage 3: refused for now), a latency-injected G-MP run.

## 5. Decisions and hit feel (2026-10-21)

### 5.1 The five open questions (the user's answers)

1. **Alt dodge alone.** The dodge on Left Alt fires when Alt is released having been pressed by itself: any other key
   or mouse button pressed in between spoils it (so Alt + middle mouse is pick block, Alt + Tab nothing), and so does
   holding it past 0.5 s. Keys already held when Alt went down (W for the roll's direction) don't spoil it. Rebound to
   a key that isn't a modifier, the dodge fires on press. Pick block: Alt + middle mouse always; outside a fight, plain
   middle mouse on a block or a non-hostile creature too. Code: `combat.math.LoneKey`, `CombatInput.onKey` (fed by
   `KeyboardHandlerCombatMixin` and `MouseHandlerCombatMixin.onButton`), `CombatInput.middleIsPick`.
2. **C stays the technique key,** ignored while a hotbar number (or F3) is held. In creative, where C + number is the
   save/load toolbar, a C shared with those activators fires on its release unless a number went down meanwhile. Dodge,
   technique and lock-on are ordinary key mappings in Controls ("VOID Combat"), rebindable.
3. **Vanilla's pixel font** for the native HUD. No change.
4. **Status build-up: Mettle owns the numbers for players, Bestiary sets each creature's resistances.** Defaults moved
   to data (§5.4).
5. **Nameplates: the nearest 10** stays.

### 5.2 Hit-stop

A landed hit freezes the attacker's and the victim's pose for a few ticks (`combat.math.Feel.hitStop`): light 2 (running
or rolling light 3), Art 3, heavy / plunge / guard counter 4, charged 5, riposte and execute 6, a Break 8; a crit and a
kill add one each (cap 9); parry 4 on both, guard break 5 / 6, a block 2 (3 for heavies); dodged hits and status procs
none; projectiles and spells freeze only the victim.

It is a picture on each client, never a delay. The server sends the hit packet (`fray:combat/hit`, now with
`HIT_BREAK` and `HIT_BOSS` bits) to the attacker, the target **and every other player tracking the target**; each client
freezes the entities involved (`client.combat.feel.HitStop`). `LevelExtractorHitStopMixin` hands back the render state
captured on the freeze's first frame (vanilla's pose and Fray Motion's sampled clip with it), moved each frame to the
entity's real position, and the victim shudders along the push (≤ 0.06 blocks, 28 Hz). The entity keeps moving and
its clip keeps running underneath, so when the freeze ends the pose rejoins the server's timeline. Inventory previews
never freeze. Option `hitStop` (on).

Measured: the same heavy lands 13 server ticks after release with hit-stop on and off; the server keeps 20 ticks a
second through the freeze (tick rate 20, not frozen, 10 ticks in 10). The local player's first-person arms are the one
part not frozen yet (§4, the animation lane).

### 5.3 Camera shake

Up to eight decaying impulses (`client.combat.feel.CameraShake`, maths in `Feel`): each pushes the view along the hit
(hit from the left, the view turns right; from the front, it tips up) with a smaller, faster wobble across it, decaying
with a 50-120 ms time constant (gone within 0.5 s), summed and capped at 4°. Weight: Break and boss slams (a boss's
heavy, charged, orange, red, purple or area strike) 1, guard break 0.9, charged 0.7, riposte 0.65, heavy 0.5, light 0.25.
Incoming: 2.2° × weight, × 0.55-1.45 by the damage as a share of max health; outgoing: heavies and up only (a Break
1.6°); a nearby boss slam shakes bystanders within 16 blocks. Applied by `CameraShakeMixin` at the end of
`Camera.alignWithEntity`, before the frustum and matrices: the camera turns, the player's look and aim don't.

Options (`config/fray-combat-client.json`): `cameraShake` (on/off), `shakeIntensity` 0-1 (shown as 0-100 %, default
0.6). Minecraft has no reduce-motion toggle; Fray follows the accessibility slider closest to it, **Damage Tilt**: the
shake is multiplied by it, so Damage Tilt 0 means no shake. Game test: at intensity 0 a heavy on the player adds no
impulse and the camera stays exactly on the look (0.0000°); at 100 % it moves off the look (0.6-1.4° in the samples
taken once a tick) and settles within half a second.

### 5.4 Status tuning (data)

`data/<ns>/fray/status_tuning/*.json` (`StatusTuning`): Fray ships `fray:defaults` (base threshold 100, rank factors
common 1 / elite 1 / boss 4, drain after 40 ticks at 10 % a second, build factors slash 1.5, pierce 0.75, blunt and
radiant 0, the rest 3); every other file is read after it, sorted by id, overriding key by key. `creatures` rows add to
a creature's `fray:status_resist/<status>` (1.0 = a meter twice as big). Bestiary owns its table
(`data/bestiary/fray/status_tuning/creatures.json`: skeletons hard to bleed, blazes and magma cubes all but immune to
Burn, iron golems to Poison, drowned and creepers easier to Shock, …). A unit test keeps the shipped file equal to the
code's defaults.

Mettle's hooks for players (nothing in Mettle changed): a meter's size is
`base × (1 + resist) × rank`, then `BalanceProvider.value(player, fray:status_threshold, size, fray:<status>)`, where
`resist` = the `fray:status_resist/<status>` attribute (Mettle may add modifiers to it) + `mettle:status_resist.<status>`
÷ 100 if Mettle registers that attribute. Build-up dealt keeps its existing hooks (`mettle:status_power`,
`mettle:status_chance.<s>`, `fray:status_buildup`). Players never read the creature table.

### 5.5 Results

All 58 combat unit tests (66 with Fray Motion's) and all 62 combat game-test checks pass (new parts `keys`, 11 checks,
and `feel`, 8 checks, plus the feel perf check; the earlier 42 unchanged), integrated and dedicated server.

| | |
|---|---|
| Hit-stop | **0.80 µs a frame**, camera shake **0.37 µs a frame** (20 creatures in view, a heavy every other tick, up to 3 frozen at once; 10 800 frames, measured like the HUD: summed `System.nanoTime` over the hooks ÷ frames). With nothing frozen the extraction hook is one `isEmpty()` |
| HUD | 0.10 ms a frame, unchanged |
| Server timing | a heavy lands 13 ticks after release with hit-stop on and off; 10 server ticks in 10 through the freeze; the hit packet now also goes to players tracking the target (26 bytes a hit) |
| Bystander | a mannequin's Break on a boss freezes both on this client, which only tracks them |

Pictures (as a bystander sees them; a mannequin is the player model): `docs/combat/11_feel_windup.jpg`,
`12_feel_break_hitstop.jpg` (the Break's first frozen frame: the swing held at contact, the boss in its hurt flash),
`13_feel_break_hitstop_later.jpg` (three ticks on, still frozen).
