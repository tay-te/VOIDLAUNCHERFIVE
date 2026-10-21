# Bestiary: enemies that fight back

Bestiary (`bestiary`, `dev.voidmc.bestiary`) owns mob AI, attack patterns with tells, elites, bosses and new
creatures, plus the player's combat actions and the animation library (COMBAT.md, ANIMATION.md). It runs on Fray.
Creature numbers are Bestiary's, tuned against Mettle's reference curves (OVERVIEW §5.1).

---

## 1. Goals

1. **Readable.** Every attack has a tell long enough to answer (§4). Learn a creature and you can beat it.
2. **Smart, not cheap.** Creatures flank, take turns, support each other and adapt to how you fight. They never read
   your inputs: they react to what they can see, like you.
3. **Of their place.** Each region and depth has its own creatures (§9). On the surface they are natural beasts and
   grim folk-horror, never glowing; the strange lives underground (Expanse's rule).
4. **Cheap enough for a server.** 200 creatures in fights around 4 players stay inside 6 ms of the tick (§2.4).
5. **Vanilla still works.** Reworked vanilla mobs keep their ids, drops, spawn rules, advancements and their quirks
   that players rely on (creepers explode, endermen hate stares, wardens hunt by sound).

## 2. The AI framework

### 2.1 Shape

```
 PERCEPTION (every 4 ticks, staggered)      sight cone, light, sneaking, noise, memory
      │
 AWARENESS   UNAWARE ─► SUSPICIOUS ─► SEARCHING ─► ENGAGED ─► (FLEEING)
      │
 TACTICS (utility AI, every 5 ticks while ENGAGED)   scores every move and position option
      │                                               reads the squad: tokens, roles, leader
 EXECUTOR (every tick)   runs the chosen Fray action (windup, active, recovery) or a positioning
      │                  behaviour (close, circle, strafe, retreat, take cover, flank)
 NAVIGATION              vanilla PathNavigation; repath at most every 10 ticks
```

- **Utility AI for choosing, actions for doing.** Each candidate (a move or a positioning) has considerations from
  data: distance band, angle to the target, the target's state (guarding, mid-attack, Staggered, Winded, healing,
  drawing a bow), own health, cooldowns, whether it holds an attack token, a repetition penalty, ±15 % noise. Score =
  weight × the product of its consideration curves. It picks the best, or randomly between the top two when within 10 %
  (so it isn't predictable to the tick).
- **Adapts to the player:** guarding a lot raises guard-break moves; kiting raises closers and leaps; standing in a
  doorway raises throws and ranged support; drinking a brew raises a punish rush. Never input-reading: only what the
  creature can see.
- **Moves are Fray actions** with server timelines (ANIMATION.md §9), so a tell on screen and the hit on the server
  are the same object.
- **Why not behaviour trees.** Trees are good at scripts and poor at weighing many changing things at once (where
  the player stands, who else is attacking, what is on cooldown), which is what group combat is. Bosses get both: a
  small phase script (enter phase, arena changes, the ultimate's timing) on top of the same utility layer.

### 2.2 Integration with vanilla AI (verified 26.3)

| Mob kind | How Bestiary takes over |
|---|---|
| Goal-based mobs (zombies, skeletons, spiders, creepers, illagers, witches, most monsters) | on `ServerEntityEvents.ENTITY_LOAD`, remove their melee and ranged attack goals and add one `BestiaryCombatGoal` (flags MOVE, LOOK) at high priority; target selectors stay, fed by Fray's threat |
| Brain-based mobs (piglins, brutes, hoglins, zoglins, breezes, creakings, wardens) | stage 5: a Bestiary `Behavior` in their FIGHT activity; until then they keep vanilla AI and only gain hit reactions |
| New creatures | own `PathfinderMob` subclasses with only Bestiary goals |
| Hearth folk | Hearth decides whom they fight; `HumanoidFighter.attach` gives them the humanoid movesets (§13) |

### 2.3 Data

A creature profile (`bestiary:creature`): family, health and damage by danger, poise, resistances, status
resistances, moveset, perception numbers, roles it can take, codex text, loot hooks. A move (`fray:action` +
timeline): colour, damage spec, ranges, cooldown, considerations, the danger level it unlocks at.

### 2.4 Budget (server)

| Part | Per creature | 200 engaged creatures |
|---|---|---|
| Perception | ~2 µs every 4 ticks | 0.1 ms a tick |
| Tactics | ~10 µs every 5 ticks | 0.4 ms |
| Executor and actions | ~1 µs a tick, ~20 µs on active ticks | 0.5 ms |
| Pathfinding (vanilla) | the big cost; repath ≤ every 10 ticks, shared paths for a squad's followers | ≤ 4 ms |
| **Total** | | **≤ 6 ms of the 50 ms tick** (gate in ROADMAP) |

Unengaged creatures run vanilla wandering at vanilla cost.

## 3. Perception

| Sense | Rule |
|---|---|
| **Sight** | a cone (120° for most, spiders 160°, owls of prey 200°), range from the profile (24 blocks typical, 16 at night unless the creature sees in the dark); a target in light 4 or less is seen at half range; sneaking ×0.6; invisible ×0.1; vanilla's `mob_visibility` item component and Sensing's visibility percent still apply |
| **Close sense** | anything within 3 blocks is noticed whatever the cone |
| **Hearing** | Fray's `PerceptionService.noise(level, pos, radius, kind)`: walking 4 blocks, sprinting 10, sneaking 1, a hit 16, a parry 20, an explosion 48, a block broken 8, a Hearth bell 64. Mettle's Burdened makes footsteps louder (×1.5) |
| **Memory** | last known position for 30 s; searching walks there and looks around |
| **Alerting** | an engaged creature alerts its kind within 16 blocks (wolves howl to 32, undead don't call) |
| **Stealth (Mettle)** | Mettle registers modifiers on the observer side: Shadow 90/80/70 %, Cloak of Dusk, Ghost (beyond 4 blocks, unseen in the dark), Vanish (loses you after 2 s out of sight), Quiet Feet (no step noise). Bestiary never hard-codes a perk |

**Awareness tells**: SUSPICIOUS turns its head and makes a sound; SEARCHING walks with weapon raised; ENGAGED gives a
battle cue (a growl, a howl, a shriek). A sneak attack works on anything not ENGAGED with you (COMBAT.md §4.1).

## 4. Telegraphs

### 4.1 The tell language

| Colour | Means | Answer | Sound | Visual |
|---|---|---|---|---|
| **White** | an ordinary attack | parry, block, dodge, or hit it first | a breath or grunt | the pose |
| **Orange** | a heavy attack | block (costs more), dodge; parry only with a shield | a deep wind-up note | the pose and a short amber spark on the weapon or claws |
| **Red** | unblockable | dodge, or be out of reach | a shriek or ring | the pose and a red spark; an optional HUD edge flash |
| **Purple** | a grab or a spell | dodge, or interrupt it (stagger or daze) | a rising tone | the pose and a faint shimmer (underground) or dust (surface) |

Sparks are a moment of light on metal or claws, not a glowing aura, so surface creatures stay natural. The HUD flash,
shapes for colour-blind players and subtitles are accessibility options (COMBAT.md §15).

### 4.2 Minimum windups (validated by a data test)

| | Light | Heavy (orange) | Red / purple | Longest string | Delays and feints |
|---|---|---|---|---|---|
| Common | 5 ticks | 8 | 12 | 2 attacks | none |
| Elite | 4 | 8 | 12 | 3 | one delayed swing per string |
| Boss | 5 | 10 | 14 (ultimates 20) | 4 | delays allowed, never two feints in a row |

Every move also needs a **punish window**: a recovery of at least 8 ticks after any heavy or red attack.

## 5. Group tactics

| Rule | Value |
|---|---|
| **Attack tokens** per target player | D1–D2: 2 · D3–D4: 3 · D5: 4 melee tokens; 2 ranged tokens; elites take 2, bosses ignore tokens. A creature without a token positions instead of attacking |
| **Waiting ring** | melee creatures without a token circle at 3.5–6 blocks, feint steps in, never stand still in a line |
| **Roles** | bruiser (front, takes tokens), flanker (circles behind when you engage another), skirmisher (hit and run), ranged (keeps 8–20 blocks, moves for a clear line, never shoots through allies), support (heals, buffs, wards: shamans, hedge witches), leader (alpha, captain: buffs, calls) |
| **Leaders** | buff allies within 12 blocks (+15 % damage, or a ward); when a leader dies the rest test morale |
| **Morale** | packs of beasts flee when the leader dies or below 30 % of their number; a beast alone flees under 20 % health; undead, constructs and bosses never flee |
| **Space** | creatures avoid fire, lava, water they can't swim, and the arc of an ally's heavy |
| **Squads** | a pack shares one path for its followers and one target choice per 20 ticks |

## 6. Difficulty

### 6.1 Danger (world-anchored)

Enemies don't level with the player. **Danger** (1.0–5.0) belongs to a place and is computed when a creature spawns
(and saved with it):

| Part | Adds |
|---|---|
| Distance from world spawn | 1.0 under 2 km · 2.0 to 5 km · 3.0 to 10 km · 4.0 beyond (smooth between) |
| Depth (Earth terrain) | 20+ blocks under the surface +0.5 · great-tunnel depth (floors y 12–44) +1.0 · a realm: at least 4.0 · ember and brimstone realms +1.0 · a great mine: +0.5 a level past the second |
| Place | inside an Expanse ruin or landmark +0.5 · a lair +1.0 |
| Night (surface) | +0.5 |
| Hearth | inside a settlement's territory −1.0 · on a king's road −0.5 · Hearth's road `danger` (0–1) added off the roads |
| Other dimensions | Nether at least 4.0 · End 5.0 |

Clamped to 1–5. Every number is server config (ring distances, step sizes). Atlas can shade known land by danger
(server rule).

### 6.2 What danger changes

| Danger | Health | Damage | Poise | Build-up dealt | Moves | Recovery | Elite chance | Affixes |
|---|---|---|---|---|---|---|---|---|
| **1** | ×1.0 | ×1.0 | ×1.0 | ×1.0 | base set | ×1.0 | 0 | — |
| **2** | ×1.5 | ×1.25 | ×1.2 | ×1.2 | +1 | ×0.95 | 2 % | 1 |
| **3** | ×2.2 | ×2.0 | ×1.4 | ×1.4 | +2 | ×0.9 | 4 % | 1–2 |
| **4** | ×3.2 | ×2.8 | ×1.7 | ×1.6 | all | ×0.85 | 6 % | 2 |
| **5** | ×4.5 | ×3.6 | ×2.0 | ×1.8 | all + desperation moves | ×0.8 | 8 % | 3 |

Fractional danger interpolates. Fray's combat damage type has scaling `never`, so vanilla's difficulty damage
scaling doesn't stack on top; Bestiary's own table (§6.3) replaces it for its moves.

### 6.3 Game difficulty, parties, servers

| | Easy | Normal | Hard | Mythic (server option) |
|---|---|---|---|---|
| Creature damage | ×0.6 | ×1 | ×1.25 | danger +1 everywhere |
| Creature health | ×0.8 | ×1 | ×1.15 | elites twice as common |
| Tokens | −1 | — | +1 | +1 |

**Parties**: elites and bosses get health × (1 + 0.6 per extra player within 32 blocks); commons don't scale, but
spawns rise 25 % per extra player nearby (at most ×2). Peaceful has no hostiles, as vanilla.

### 6.4 Caps

At most 12 creatures engaged with one player (more wait at range); one elite per 64-block radius; vanilla's monster
cap stays and counts every pack member. Spawner creatures are reworked but never elite, and carry Mettle's anti-farm
flag as today.

## 7. Elites and affixes

An **elite** is a creature with: health ×3, damage ×1.4, poise ×1.5, drawn 1.15× bigger, a generated name from its
kind and the place ("the Barrow Wight of Kestrel Heath": region names from Expanse/Atlas), a health bar with affix
icons over its head, and one to three **affixes**. **Champion packs** (D3+): an elite leader with 3–5 commons that
share one of its affixes.

| Affix | Does | Tell | Counter | From |
|---|---|---|---|---|
| **Ironbound** | +6 armour, poise +60 %; can't be Staggered until its plate (a hurt volume) takes 3 blunt hits | dull metal sheen | maces, hammers, Sunder | D2 |
| **Frenzied** | +25 % attack speed; enrages at half health (+25 % more) | snarls; eyes redden at enrage | parry the quicker lights | D2 |
| **Rimebound** | 4-block frost aura (Chill), frost on hits | frosted breath | fire, keep moving | D2, cold places |
| **Venomous** | poison build-up ×2 on hits; a poison cloud when hit hard | drips | Venomward, antidote | D2 |
| **Regenerating** | regains 2 % health a second after 3 s unhurt | | Burn or Blight stop it | D2 |
| **Thorned** | reflects 15 % of melee damage | spines | ranged, armour | D2 |
| **Shielded** (humanoids) | carries a shield: blocks from the front, parries lights | | heavies, flanking, axes | D2 |
| **Swift** | +30 % speed; sidesteps heavies | | lights, Rooted | D2 |
| **Warded** | a ward worth 40 % of its health, back after 8 s unhurt | a haze (surface), a shimmer (underground) | arcane, Unravel, Wardbreaker | D3 |
| **Vampiric** | heals 30 % of damage it deals | | radiant (stops healing 5 s), Blight | D3 |
| **Molten** | fire trail; fire on hits; bursts on death (2.5 blocks, orange tell) | embers | frost, Wet | D3, underground and volcanic |
| **Summoner** | calls two minions every 20 s | a chant (purple) | kill it first, Silence, interrupt | D3 |
| **Splitting** | splits into two at 40 % health each when it dies | | area attacks | D3 |
| **Reflective** | sends back half of arrows and bolts | a shimmer on its front | melee, spells | D3 |
| **Gravebound** (undead) | rises once at 30 % health (3 s, red tell) | | a radiant killing blow stops it | D3 |
| **Bulwark** (leaders) | allies within 6 blocks take 25 % less | a horn or banner | kill it first | D3 |
| **Stormcaller** | every fourth hit calls lightning at your position (a red ring for 1.5 s) | static crackle | move; Grounding | D4 |
| **Blinking** | steps behind you every 8 s (purple, 10 ticks) | a hiss of air | turn on the sound | D4 |
| **Jailer** | a hit roots you for 1 s (once in 6 s) | chains | dodge | D4 |
| **Plagued** | Blight build-up on hits; a blight pool where it dies | flies | radiant, fire | D4 |
| **Sundering** | hits leave you Exposed (armour −30 %) | | dodge | D4 |
| **Echoing** | repeats its last attack at half power 10 ticks later | an after-image | dodge twice | D5 |

## 8. Bosses

### 8.1 Design rules

1. **Two or three phases**, each adding moves or changing the arena, with a clear transition (a roar, a stance
   change: a moment to breathe, never a free hit on the player).
2. **4–8 moves per phase**, all following §4. At most one red move in any three.
3. **A punish window every cycle**: something the player can learn to exploit (after the slam, the knight is stuck).
4. **No one-shots**: no move above 60 % of a stage-matched player's health, except a telegraphed ultimate (20+ ticks,
   red, at most 85 %).
5. **A Break meter** (COMBAT.md §9): parries, weak points and heavy hits fill it; Broken = 60 ticks to hurt it.
6. **Arena-aware**: uses pillars, water, height; never leaves its arena; regains health if the players leave.
7. **Every build can win**: a ranged answer and a melee answer to each phase; no immunity to a whole damage group.
8. **Readable in multiplayer**: the boss turns to its highest-threat target; area moves are shown on the ground.
9. **Remembered**: a name, a place, a lore page, a trophy, a deed.

### 8.2 The encounter framework (Fray's `EncounterService`)

| | Rule |
|---|---|
| Start | a player enters the arena (a box or a radius around the lair anchor) and the boss sees them |
| Bar | Fray's boss bar: name, health, phase notches, the Break meter, status meters; vanilla `ServerBossEvent` (verified API) as the fallback for clients without the HUD |
| Music | a cue for Gloam's soundscape (request) |
| Reset | no player in the arena for 60 s: the boss returns to full and its spot |
| Death of players | they respawn as normal; the fight goes on for those left |
| Respawn | a lair boss returns after 3 in-game days (server config; 0 = once per world) |
| Loot | personal: each player who dealt 10 %+ of the damage or stayed 30 s+ gets their own roll |
| Credit | `ENCOUNTER_ENDED` with damage shares: Hearth deeds, Mettle's "first to fell" deed Mythics, Atlas marks the lair felled |

### 8.3 Stage 1's boss: the Gallows Knight

A revenant knight bound to a gallows hill or a ruined keep (Expanse lair anchor). Custom rig (real elbows, a torn
cape, a chained gibbet cage on its back). D3, 900 health solo: a mid-stage player (13 damage a second, hitting
about a third of the time) needs close to 4 minutes, a late-stage one about 2½. Light cut 10, heavy 18, the
ultimate 26 (under 85 % of a mid-stage player's 26 health after armour).

| Phase | Moves (colour) |
|---|---|
| 1 (100–55 %) | longsword cut (white), two-cut string (white, white), overhead (orange, punish 14 ticks), shoulder charge (red, 3 blocks), gibbet-chain sweep (orange, 120°), kick (white, guard break ×2) |
| transition | tears the cage off its back, which falls and rolls: a hazard for the rest of the fight |
| 2 (55–0 %) | adds: chain lash (purple, pulls you in), the Hanging (red ultimate, 24-tick tell: a chain from the gallows; hits you if you stand on the mound), and a faster three-cut string with one delay |
| Weak point | its exposed chest while it pulls the chain (hurt volume, ×1.5) |
| Drops | Gallows chain (Arsenal material: a heavy flail's chain), a rune tablet (Sunder), the knight's crossguard (a named heirloom Arsenal part), a lore page |

## 9. The roster

Waves follow the roadmap: **S1** (stage 1), **S5a/S5b** (stage 5 content waves), **L** (later).

### 9.1 Reworked vanilla mobs

| Mob | New moves (colour) | Tells and quirks | Family | Wave |
|---|---|---|---|---|
| **Zombie** (gravewalker) | claw (W), double claw (W W, D3), lunge-grab (P, D4: bites until you dodge free, 2 s) | groans before the lunge | undead | S1 |
| **Husk** (dune walker) | as zombie + sand toss (P: Blindness 2 s) | | undead | S5a |
| **Drowned** | trident jab (W), trident throw (O), net throw (P: Rooted 1.5 s) | hums | undead, aquatic | S5a |
| **Skeleton** | quick shot (W), aimed shot (O, glint), backstep-and-shoot, bone club when cornered (W), three-arrow volley (O, D3) | creak of the draw | undead | S1 |
| **Stray**, **Bogged**, **Parched** | as skeleton; frost arrows (Chill) · poison arrows · sand-blind shots | | undead | S5a |
| **Wither skeleton** | greatsword moveset with Blight build-up | | undead | L |
| **Spider** | bite (W), pounce (O: a 6-block leap), web spit (P: Rooted), wall ambush (drops from walls and ceilings) | chitters, rears up before the pounce | arthropod | S1 |
| **Cave spider** | bite with poison ×2, swarms of 3–5 | | arthropod | S5a |
| **Creeper** | the fuse **is** the tell (hiss, 30 ticks); a Staggered creeper's fuse resets (hit it hard to defuse); D3+ creepers rush, then fuse at 3 blocks | swells | — | S1 |
| **Enderman** | teleport strike from behind (P), grab-and-drop (P, D4) | stare rules unchanged | occult | S5b |
| **Witch** (hedge witch) | throws Alembic pots (Venom, Slowness; an oil pot then a fire pot: a combo), drinks to heal (interrupt it with a daze) | cackle before a throw | human | S5a |
| **Pillager** | aimed bolt (O), reposition, crossbow bash (W) | | human | S5a |
| **Vindicator** | axe moveset: chop string (W W), overhead (O), charge (R) | | human | S5a |
| **Evoker** | fang line (P), vex summon (P: summoner) | arms raise | human | S5b |
| **Ravager** | charge (R), roar (P: Stagger in 4 blocks), bite (W) | paws the ground | beast | S5b |
| **Slime**, **magma cube**, **sulfur cube** | splits; magma: fire build-up; sulfur cube keeps its 26.x archetype mechanics | squash before a jump | — / ember | S5b |
| **Phantom** | swoop (O: 16-tick dive with a shriek) | | undead | S5b |
| **Piglin**, **brute**, **hoglin**, **zoglin** | humanoid movesets; hoglin charge and toss (R) | | — | L |
| **Blaze**, **ghast**, **breeze** | volleys (O), flame ring (R) · fireball (O) · wind charges and jumps | | ember / — | L |
| **Guardian**, **elder guardian** | the laser charge already is a tell; add a tail lash (W) | | aquatic | L |
| **Creaking** | unchanged rule (moves when unseen); add clear strike tells | | — | L |
| **Warden** | kept as an apex hazard, not a boss: sonic boom keeps its long tell; hunts by Fray noise too | | — | L |
| **Wolf** (vanilla, tameable) | unchanged; tamed wolves fight with a pack moveset beside you | | beast | S5a |

### 9.2 New creatures on the surface (natural, grounded)

| Region (Expanse biomes, by tag or climate) | Day | Night | Elites and lairs | Danger |
|---|---|---|---|---|
| **Heartland and temperate woods** (woodland_glades, bluebell_woods, aspen_parkland, heartland country, vanilla plains and forests) | **wild boar** (charge O, gore W), **grey wolf** packs at dusk (feint, flank, hamstring bite) | gravewalkers, skeleton archers, **barrow wights** near stone circles (frost and blight) | "the Ravener" (wolf alpha) | 1–3 |
| **Old forest and oakwood** (ancient_oakwood, old_forest, dark forests) | **brown bear** (swipe W, maul O, rear-slam R; territorial), **woodwose** (a moss-hung wild man with a club, ambushes from the undergrowth) | hedge witches | "Bloodmaw", the old bear; a hedge-witch coven | 2–4 |
| **Heath and moor** (heather_moor) | **adder** (small, poison, hides in heather) | barrow wights, **black hounds** (folklore dogs: only at night, never glowing) | the **Barrow King** (lair boss, stone circle crypt) | 2–4 |
| **Cold forests** (pine_heath, larch_taiga, boreal_muskeg, redwood_giants, maple_highlands) | **lynx** (ambush from cover, pounce R) | **bog bodies** (peat-preserved dead rising from the muskeg) | a lynx elite; "the Muskeg Bride" | 2–4 |
| **Tundra and snow** (lichen_tundra, snowy biomes) | polar bears (reworked: hungry ones hunt at D3+), tundra wolves | **frostwights** (strays, reworked) | "the Frostwight" (Hearth's bounty already names it) | 2–4 |
| **Steppe and savanna** (amber_steppe, ghost_gum_outback) | **hyena** packs (harry, laugh calls), **vultures** (circle a fight, dive at the weakest) | dune walkers | "the Grinning Matriarch" | 2–4 |
| **Deserts and badlands** (red_dunes, saguaro_flats, painted_canyons, salt_flats) | **asp** (sand ambush, poison), **scorpion** (small, sting poison, claw pin P) | dune walkers, parched | "the Dune Walker" (Hearth's bounty) | 2–5 |
| **Jungle, karst, monsoon, cloud forest** | **tiger** (stalks, ambush pounce R), jungle weavers (reworked spiders: web traps) | bogged | "the Striped Death" | 3–4 |
| **Swamp and bayou** (willow_bayou, swamps) | **alligator** (ambush from water; grab-roll P) | bog bodies, bogged, swamp witches | the **Bog Mother** (lair boss) | 2–4 |
| **Coasts** (palm_coast, coral_coast, beaches, fjordlands) | — (crabs and turtles stay animals) | tide drowned (nets, tridents) | "Saltbeard", a drowned captain | 2–4 |
| **Mountains** (verdant_peaks, dolomite_peaks, tepui, volcanic_highlands) | mountain goats (reworked ram: knocks you off ledges), **crag eagles** (dive to knock you down) | strays in snow; sulfur cubes at fumaroles | — | 3–4 |
| **Ruins** (gallows_hill, ruined_keep, hill_chapel, graveyard, stone_circle crypts) | — | **revenant soldiers** (armoured dead with sword and shield, the humanoid moveset), crypt skeletons | the **Gallows Knight** (S1), gravebound elites | 2–4 |

Wave split: S1 = the Gallows Knight and revenant soldiers; S5a = heartland, old forest, heath, steppe, swamp; S5b =
cold forests, tundra, deserts, jungle, coasts, mountains.

### 9.3 New creatures underground (the strange is allowed here)

| Place (Expanse) | Creatures | Boss (central plateau) | Danger |
|---|---|---|---|
| Shallow caves and passages | gravewalkers, skeletons, spiders, creepers, **silverfish swarms**, **tunnel ghouls** (D3: fast, pale, climb) | — | 2–3 |
| Mossgrown caves, dripstone grottos | **stalk crawlers** (look like stalagmites, drop from the roof: R) | — | 3 |
| Echoing depths | **echo shades** (blind, hunt by sound: a Stealth playground), the warden | — | 4 |
| Sulfur seeps | sulfur cubes, **brimstone ghouls** | — | 3–4 |
| **Great mines** | **mine wights** (dead miners with picks and lanterns), tunnel ghouls; deeper levels harder | **the Pit Foreman** (lowest level) | 3–5 |
| Glowcap Wilds | **sporelings** (mushroom folk, spore clouds), cap stalkers | the **Mycelial Mother** | 4 |
| Verdant Hollow | **root stranglers** (vines that grab: P) | the **Root Warden** | 4 |
| Stalagmite Deeps | stalk crawlers, drowned | the **Stalk Mother** | 4 |
| Geode Vaults | **shardbacks** (crystal beetles that reflect spells; blunt shatters them), prism golems | the **Geode Colossus** | 4 |
| Ember Deeps | **cinder hounds**, magma cubes, **basalt brutes** | the **Ashen Wyrm** | 5 |
| Sunless Mere | **mere lurkers** (eels that strike from the water), mere drowned | the **Drowned Choir** (a drowned host and its bell) | 4 |
| Rime Hollows | **rime wraiths** (frost spirits), strays | the **Rime Matron** | 4 |
| Root Hollows | **root spiders**, cave spiders | the **Broodmother** | 4 |
| Fossil Deeps | **ossuary guards** (skeleton knights), **bone hounds** | the **Old Bones** (a colossus of fossil bone) | 4 |
| Tidal Grottos | **grotto crabs** (giant), drowned | **Old Carapace** | 4 |
| Amber Hollows | **amber wasp** swarms, resin crawlers | the **Hive Queen** | 4 |
| Brimstone Vaults | sulfur cubes, **brimstone fiends** | the **Brimstone Maw** | 5 |
| Deepstone ruins (crypts) | crypt skeletons | the **Crypt Warden** (an elite-boss) | 4 |
| Nether (later) | reworked piglins, brutes, hoglins, blazes, ghasts, wither skeletons | the **Wither**, reworked into phases | 4–5 |
| End (later) | endermen, shulkers | the **Ender Dragon**, reworked into phases | 5 |

## 10. Families: damage kinds, statuses, runes and brews

### 10.1 Weaknesses and resistances

Multipliers apply through Fray's resistance attributes (CONTRACTS.md §5). "Threshold ×2" = a status needs twice the
build-up.

| Family | Members | Weak | Resists | Statuses |
|---|---|---|---|---|
| **Undead** | zombies, skeletons, wights, revenants, drowned, mine wights, bog bodies | radiant ×1.5, fire ×1.25, blunt ×1.25 (skeletons) | poison and blight immune; Bleed threshold ×2 (skeletons immune) | a radiant proc **Sanctifies**: no regeneration and Burning 3 s |
| **Beast** | wolves, boars, bears, lynx, tigers, hyenas, alligators, hounds | slash ×1.25, Bleed threshold ×0.7, fire ×1.25 | — | flee when Burning at low health |
| **Arthropod** | spiders, scorpions, wasps, crawlers | fire ×1.5, blunt ×1.25 | poison threshold ×2 | Bane (rune) |
| **Construct and crystal** | golems, shardbacks, the Colossus | blunt ×1.5, shock ×1.25 | pierce ×0.5, slash ×0.75; Bleed and Poison immune | Frozen + blunt **Shatters** |
| **Ember** | magma cubes, cinder hounds, brimstone fiends, the Wyrm, Molten elites | frost ×1.75; Wet doubles that | fire and Burn immune | Wet puts out their auras for 5 s |
| **Frost** | frostwights, rime wraiths, the Matron | fire ×1.75 | frost immune | |
| **Aquatic** | drowned, lurkers, crabs, alligators | shock ×1.5 (always Wet in water) | fire ×0.5 | |
| **Occult** | endermen, shades, wraiths, wights | radiant ×1.5, arcane ×1.25 | physical ×0.75 | Hex lasts twice as long |
| **Fungal** | sporelings, the Mycelial Mother | fire ×1.5 | poison immune | spore clouds |
| **Human** | Hearth's bandits and folk, illagers | — | — | — |

### 10.2 What the player's tools do to them

| Tool | Best against | Notes |
|---|---|---|
| Rune of **Sanctity** (was Smite) | undead, occult | radiant share + Sanctify |
| Rune of **Bane** (was Bane of Arthropods) | arthropods | poison build-up and +damage |
| Runes **Kindling**, **Rime**, **Thunder**, **Venom**, **Rend** | fungal and arthropods · ember · aquatic and constructs · beasts and humans · beasts | ENCHANTING.md §5.1 |
| **Wardbreaker**, **Hexbane** | Warded elites, affixed elites | |
| **Hallowed oil**, **holy water** flask (Alembic) | undead, occult | |
| **Frost resin**, **frost pot** | ember | Wet first doubles it |
| **Arc oil** | aquatic, constructs | |
| **Kindling oil**, **fire pot** | fungal, arthropods, beasts | oil flask first: Conflagration |
| **Venom oil** | beasts, humans | useless on undead |
| Spells | MAGIC.md §10: each school has a family it shines against | |

### 10.3 The codex

Every creature has a codex entry (a Hub page, Figma). Knowledge grows: **seen** (name and picture) → **fought** (its
moves and their colours) → **felled 10** (weaknesses and resistances) → **studied** (everything, from a Hearth
scholar, a bestiary page in a ruin, or Mettle perks that say so). The codex only shows what the player learned.

## 11. Drops and rewards

| From | Drops |
|---|---|
| Creatures | **materials** for Arsenal (wolf pelt grips, boar tusk, bear tallow, revenant steel scrap, crystal shards, ember cores, chitin plates) and **reagents** for Alembic (venom glands, grave dust, spore sacs, rime crystals, amber resin, brimstone, wight dust); trophies (heads) Hearth buys |
| Elites | the above ×2, a 5 % rune tablet, a codex page |
| Bosses | a unique material, a rune tablet, a **named heirloom part** (Arsenal: weapons stay forged, so bosses give parts, never finished weapons), a lore page, a deed |

Vanilla drops stay; Bestiary adds through Fabric loot events, never replaces tables.

## 12. Spawning

- New creatures join spawn lists **by biome tag or climate**, never by naming a biome (Expanse's rule), through
  Fabric biome modifications; underground by Expanse's `ExpanseUnderground` sampler (realm, depth, great-mine level).
- Lairs come from **Expanse's lair anchors** (CONTRACTS.md §18.2): one ruined keep in four, every stone-circle crypt,
  each realm's central plateau, each great mine's lowest level. Without Expanse, lairs use vanilla structures
  (ancient cities' edges, trial chambers' outskirts, strongholds) as a fallback.
- Night spawns near Hearth towns follow Hearth's territory (−1 danger) so towns stay livable.

## 13. Hearth's folk in a fight

Hearth decides who fights whom and when to yield (law, bandits' "your purse or your life", tournament duels to a
quarter health). With Bestiary present, Hearth attaches a **humanoid moveset**: guards sword-and-shield (captains a
greatsword), bandits axe, sword or bow, caravan guards sword and shield, companions whatever the player gives them.
Folk then telegraph, guard and parry like any humanoid, and take damage through Fray's pipeline. Their numbers stay
Hearth's (a guard's 20 health and its iron sword).

## 14. Tests

| Test | Checks |
|---|---|
| Data (JUnit) | every move meets §4.2's windup minimums and punish windows; every creature profile loads; every timeline exists |
| Tactics (JUnit) | scoring curves; tokens never let more than N attack one player; morale |
| Game tests | a pack of 5 wolves around a dummy player never has more than the token count in ACTIVE at once; a creeper hit with a heavy during the fuse defuses; a boss resets when the arena empties |
| Simulator (`BestiaryReportTest`) | time-to-kill and time-to-die of every creature at every danger against Mettle's reference player per stage (OVERVIEW §5.2 targets) |
| Perf gate | 200 engaged creatures around 4 bot players on a dedicated server: ≤ 6 ms of the tick for Bestiary, ≤ 25 ms total tick |
| Playtest | each S1 creature and the boss with each archetype (warrior, ranger, rogue, mage stand-in) |
