# Combat: actions, timing, feel

How the player fights. Bestiary owns the actions and movesets, Fray runs them, Mettle owns every number that grows
with the player (CONTRACTS.md §2), Arsenal and Alembic add weapon families, techniques, foci and casts.

All timings are in **ticks** (20 a second, 50 ms each). Numbers are first drafts for playtests; each lives in
action or moveset data (`fray:action`, `fray:moveset`) or in Mettle's balance keys.

---

## 1. Pillars of feel

1. **Commit, then read.** Pressing attack commits you for a short, honest time. You win by choosing well, not by
   clicking fast. Light attacks commit you a little, heavies a lot.
2. **Every attack has an answer, every answer has a cost.** Dodge costs stamina and needs timing; parry is a tight
   window and a gamble; block is safe but drains you.
3. **Hits land.** A short freeze on impact (hit-stop), a sound for what you hit, the target reacting, a little camera
   kick on heavy blows. You should feel the weight of a mace in your hands.
4. **Minecraft first person stays home.** Everything works in first person, which is how most people play. A
   third-person combat camera is an option, not a requirement.
5. **Readable on a laptop.** Mouse and four extra keys. No combos that need a fighting-game stick.

## 2. Controls

| Input | Default | Does |
|---|---|---|
| **Attack** | left mouse | tap: light attack (chains into a combo). Hold 6+ ticks: heavy attack on release. Hold past 16 ticks: charging, full at the moveset's charge time |
| **Guard** | right mouse | belongs to the **off hand** if it holds a shield or a focus, else to the main weapon. Shield or weapon: tap = **parry**, hold = **block**. Bow, crossbow, throwable: draw or aim. Focus: cast the selected spell |
| **Dodge** | left Alt | roll in the movement direction; no direction = backstep. Option: double-tap a direction instead |
| **Technique** | C | tap: the weapon's technique (Arsenal) or the focus's secondary cast. **Sneak + C**: your Mettle Mastery's Art (Whirlwind, Earthshaker, Volley, Shadowstep...). Hold C + scroll: choose a spell on a focus |
| **Lock-on** | middle mouse | toggle; scroll or flick the mouse to switch target. Vanilla pick-block moves to Alt + middle mouse |
| Belt | R | Mettle's belt radial (brews, bombs, food), unchanged |
| Sprint, sneak, jump | vanilla | unchanged; sprinting and jumping cost no stamina |

Keys already taken in VOID and avoided: J (Hearth journal), K (Mettle character), R (belt), M N B = - (Atlas),
Right Shift (Hub), F8 (LOD). Iris's own defaults (O, R, K) already overlap Mettle's; Fray's settings page lists every
conflict found at start. The layout is a ROADMAP decision (D2).

**Blocks and the attack key.** With a weapon in hand, the crosshair decides: on an entity or air, attack swings; on
grass, flowers, leaves and other blocks you can walk through, the swing passes through; on a solid block with a
**tool** (axe, pickaxe, shovel, hoe) and no creature in the swing's arc, holding attack mines as vanilla does. A
sword never mines.

## 3. The action model

Every action (Fray) runs three phases on the server, mirrored by the clip on every client:

```
 input ──► WINDUP ──────► ACTIVE ──► RECOVERY ──────► done
           tell plays     hits land  open to punishment
           (hyper-armour   (hit volumes /      │
            on heavies)    swept arcs)          ├─ chain window: a buffered attack starts the next move
                                                ├─ dodge-cancel window: a dodge cuts recovery (+5 stamina)
                                                └─ guard-cancel window: last 3 ticks
```

| Rule | Value |
|---|---|
| Input buffer | 6 ticks (300 ms): an input pressed early runs when its window opens |
| Chain window | from the end of ACTIVE to the end of RECOVERY; the next light starts at the moveset's chain point |
| Feint | during a heavy's windup (ticks 0 to windup − 3), Guard cancels it for 8 stamina |
| Turning | free during WINDUP; the swing's direction is taken from your look on the first ACTIVE tick |
| Moving | ×0.5 speed during a light's windup and active, ×0.2 during a heavy's, ×0.6 while casting; free in recovery |
| Root motion | lights step 0.3 blocks forward, heavies 0.6, lunges up to 2.0, over their ACTIVE ticks (client-predicted, server-checked) |
| Hyper-armour | heavies, charged attacks and heavy casts add +20 poise during WINDUP and ACTIVE (§9) |
| Interrupts | stagger, daze, flinch (§9), Frozen, death; an interrupted action refunds nothing |

**Server authority with prediction.** Your client starts the action the instant you press (so it feels instant) and
sends the request with its tick. The server validates (stamina, not staggered, not Winded for heavies, the item
really in hand) and runs the action from receipt; if it refuses, your client blends back to idle in 3 ticks. Hits
are decided only on the server (§10). ANIMATION.md §8 has the timing details.

## 4. Light, heavy and charged attacks by family

Base numbers before Mettle's scaling. Damage multipliers apply to the weapon's base damage (vanilla attack damage,
or Arsenal's build). W / A / R = windup / active / recovery ticks.

| Family | Light W/A/R | Combo (finisher) | Heavy W/A/R | Charged (extra ticks → max ×) | Reach | Arc | Stamina L / H / C max | Poise factor |
|---|---|---|---|---|---|---|---|---|
| **Unarmed** | 2/2/4 | jab, jab, hook (3/2/6 ×1.3) | 8/2/10 | +10 → ×2.0 | 2.5 | 30° | 4 / 12 / 18 | 0.6 |
| **Dagger** | 2/2/5 | 4 cuts (4/2/8 ×1.4) | 9/2/10 thrust | +12 → ×2.2 | 2.6 | 40° | 5 / 14 / 20 | 0.6 |
| **Sword** (longsword, sabre) | 4/2/6 | 3 cuts (6/3/9 ×1.3) | 12/3/12 | +20 → ×2.4 | 3.0 | 100° | 8 / 18 / 28 | 1.0 |
| **Greatsword** | 7/3/10 | 3 cuts (9/3/12 ×1.4) | 16/4/16 | +20 → ×2.4 | 3.5 | 140° | 13 / 26 / 38 | 1.6 |
| **Axe** | 5/2/8 | 2 chops (6/2/10 ×1.3) | 13/3/14 | +20 → ×2.4 | 3.0 | 80° | 10 / 22 / 32 | 1.3 |
| **Mace, hammer** | 7/2/10 | 2 blows (8/3/12 ×1.4) | 16/3/16 | +20 → ×2.4 | 3.0 | 60° | 12 / 26 / 38 | 1.8 |
| **Spear, trident** | 4/2/7 thrust | thrust, thrust, sweep (6/3/9 ×1.2, 120°) | 12/3/12 lunge +2 blocks | +16 → ×2.2 | 4.0 (trident 3.5) | 20° | 9 / 20 / 30 | 1.0 |
| **Staff** (focus, melee) | 5/2/8 | 2 strikes | 13/3/13 | charge = a channelled cast | 3.2 | 90° | 8 / 18 / – | 1.2 |

- **Heavy** = ×1.6 damage, ×1.5 poise damage; **charged** rises from ×1.6 to the family's max over the extra ticks,
  ×2.0 poise damage at full. Held at full charge it costs 4 stamina a second and releases itself after 40 ticks.
  A full charge flashes (a ring sound in first person, a glint on the weapon).
- A three-hit sword string takes about 38 ticks for 3.3 hits of damage: roughly vanilla's 1.6 swings a second. The
  Mythic power comes from Mettle, Arsenal quality, runes and brews, not from clicking faster.
- **Vanilla weapons** map to families by item tag (swords, axes, maces, spears, tridents); vanilla's spear
  `kinetic_weapon` charge and the mace's falling smash stay as each weapon's technique.
- **Arsenal families** (DESIGN.md §4.1) map onto these rows and change them with parts: a long haft adds reach and
  windup, a light pommel takes a tick off recovery, a counterweight adds poise factor.

### 4.1 Special attacks (every melee family)

| Attack | Input | Effect |
|---|---|---|
| **Running strike** | light while sprinting | moves 1.5 blocks, ×1.2, keeps the sprint if Mettle's Light Feet is slotted |
| **Plunging strike** | light while falling 1.5+ blocks | ×1.5 and Daze; replaces vanilla's jump critical (Mettle's Overhead Blow teaching makes it ×2.0) |
| **Riposte** | light within 20 ticks of a successful parry | ×2.0, a critical, ignores half the target's armour, can't be blocked |
| **Backstab** | a hit from behind (within 60° of its back) on a creature not targeting you | ×2.0 (the Rogue's Backstab signature makes it ×2.5; they don't stack) |
| **Guard counter** | attack within 6 ticks of blocking a hit | a quick counter (3/2/6): ×1.2 and Daze; with a shield it is a **shield bash** |
| **Roll attack** | light in a dodge's last 3 ticks | 3/2/6, ×1.1 |
| **Execute** | light at melee range on a Staggered elite or a Broken boss | 6/3/14, ×2.5, a critical |

### 4.2 Ranged

| Weapon | Input | Numbers |
|---|---|---|
| **Bow** | hold Guard to draw, release to loose | full draw 20 ticks (vanilla's `BowItem` power curve: power = draw time ÷ 20); a quick shot from 8 ticks; a full draw is a critical (vanilla's crit arrow). Holding at full drains 5 stamina a second after 20 ticks; at 0 the draw slips and looses at half power. Move ×0.35 while drawing (the bow's `use_effects` speed). Attack = **bow bash**: 2/2/6, blunt 2, pushes 0.6 blocks |
| **Crossbow** | Guard to load (vanilla 25 ticks), Guard to aim, Attack to shoot | aimed shots (after 6 ticks aiming) ×1.2 poise; Attack while unloaded = stock bash |
| **Throwables** (hatchets, javelins, throwing knives: Arsenal; bombs: Alembic) | hold Guard to aim, release | knives 4 ticks, hatchets 8, javelins full at 14 (the trident's 10 vanilla); thrown weapons land and can be picked up (Tether rune returns them) |
| **Weak points** | any projectile | hitting a creature's hurt volume flagged weak (a head, a glowing joint) ×1.5 (Mettle's Deadeye: ×2.0) |

### 4.3 Foci (Arsenal items, Alembic spells; MAGIC.md §9 and §12)

| Focus | Hands | Attack | Guard | Technique |
|---|---|---|---|---|
| **Staff** | two | staff strikes (blunt, table above) | cast the selected spell | the staff's second spell; hold for a channel |
| **Wand** | off hand (main hand keeps a weapon) | the main-hand weapon | cast (replaces the weapon's guard: the spellblade's trade) | cycle spells |
| **Tome** | off hand | the main-hand weapon | cast the open page | turn the page (next spell) |

Cast times: quick 6 ticks, standard 12, heavy 20+, channels while held. Casting has hyper-armour only for heavy
casts. Mana, not stamina, pays for spells.

## 5. Combos per family (the graphs)

A moveset is a small graph (`fray:moveset`). L = light, H = heavy, C = charged, D = dodge, G = guard, T = technique.

| Family | Strings | Notes |
|---|---|---|
| Unarmed | L L L · H · D→L | the hook dazes |
| Dagger | L L L L · L L H (a stab that inflicts Bleed build-up ×2) · D→L (a slash out of the roll) · backstab | fastest recovery; Arsenal technique *quick step* (a 2-block side step, 6 i-frame ticks, 14 stamina) |
| Sword | L L L · L L H (rising cut: launches small creatures 0.6 up) · H→L (follow-up cut) · C | longsword technique *riposte stance* (hold T: next hit caught is auto-parried); sabre *flurry* (4 fast cuts); greatsword *cleave* |
| Axe | L L · L H (overhead) · C · T *hook shield* (pulls a raised shield aside) or *throw* (hatchet) | heavy breaks a raised shield for 5 s (vanilla's axe rule kept: `Weapon` component, 5 s) |
| Mace, hammer | L L · H (ground slam: 2-block radius, 0.5 of damage, Daze) · C · falling smash (vanilla) | best poise breaker; *crush* ignores 30 % armour |
| Spear | L L (sweep) · H lunge · T *brace* (stops a charging creature: ×2 damage to it) or *throw* · C | longest reach; keeps enemies at the tip |
| Bow | quick shot · full shot · bow bash · T volley (split rest) · Arts: Volley, Arrowstorm (Mettle) | |
| Crossbow | aimed shot · stock bash · T repeater (3 bolts loaded, Arsenal) | |
| Shield (off hand) | G tap parry · G hold block · L while guarding = shield bash · T *shield charge* (Arsenal) | |
| Staff | L L · casts · channel · H staff slam | |

## 6. Dodge

| | Roll | Backstep |
|---|---|---|
| Length | 12 ticks | 8 ticks |
| i-frames | ticks 1–7 (7 ticks) | ticks 1–4 |
| Distance | 3.5 blocks (light armour 4.0, heavy 3.0) | 2.0 blocks |
| Stamina | light armour 18 · medium 22 · heavy 28; +4 when Burdened | 12 |
| Recovery | last 4 ticks; a light in the last 3 is a roll attack | last 3 ticks |

- **Armour classes** come from worn armour weight (Mettle's numbers: light ≤ 5 kg, medium ≤ 10 kg, heavy above: a
  leather set is light, chain medium, iron, diamond and netherite heavy). Overburdened: backstep only. Pinned: no
  dodge.
- **Perfect dodge.** If an attack's active tick would have hit you in your first 3 i-frame ticks: **Slip**. +12
  stamina back, your next attack within 20 ticks deals +25 %, a soft whoosh. Creatures that lose you this way
  hesitate for 6 ticks.
- No dodging in the air, while swimming or climbing. Dodge cancels a bow draw and a cast.
- Movement is predicted by your client (in Minecraft the client moves the player; the server checks "moved too
  quickly", and a 0.35 block/tick roll is far inside that). The server grants the i-frames from when it receives the
  dodge, minus the lag allowance (§10.3).

## 7. Guard: block, parry, guard break

| | Shield | Weapon guard (no shield) |
|---|---|---|
| Parry window | ticks 0–5 after pressing (6 ticks) | ticks 0–4 (5 ticks) |
| Block active from | tick 2 | tick 3 |
| Covers | the front half (as vanilla's shield) | 120° in front |
| Blocks physical | 100 % | 60 % |
| Blocks elemental / occult | 50 % / 0 % (runed shields more) | 0 % / 0 % |
| Stamina per blocked point | × stability: wood 1.3, iron 1.0, steel 0.85 (Arsenal decides per build) | × 1.6 |
| Arrows | blocked | not blocked |

**Attack colours (the tell language, BESTIARY.md §4).** **White**: parry or block. **Orange** (heavy): block (stamina
×1.5), parry only with a shield. **Red**: unblockable, dodge or be elsewhere. **Purple**: a grab or a spell; dodge
or interrupt it.

**A parry** (an attack's active tick lands inside your window):

| Against | Result |
|---|---|
| Common creature | no damage; it is **Staggered** 20 ticks; you get +15 stamina and a 20-tick riposte window |
| Elite | no damage; Staggered 12 ticks; riposte window 14 |
| Boss | no damage; +8 % on its Break meter; staggers only on moves flagged `parry_stagger` |
| Player (PvP) | no damage; Staggered 10 ticks; riposte window 10 |
| A projectile, with a shield | deflected (no damage) |

**A missed parry** (nothing lands in the window): if you still hold Guard you keep blocking; if you released, 8
ticks of whiff recovery. Presses within 12 ticks of the last attempt get half a window (no spam).

**Guard break.** Stamina reaches 0 while blocking: **Staggered** 25 ticks and **Winded**. Axes' heavy breaks a
shield for 5 s (vanilla's rule, kept).

## 8. Stamina

Fray holds it, Mettle scales it (CONTRACTS.md §7). Only combat actions spend it.

| | Base |
|---|---|
| Pool | 100 (Mettle: up to ~180 at the apex, ceiling 220) |
| Regen | 25 a second, starting 12 ticks after the last spend; half while guarding |
| Winded (at 0) | 30 ticks: no dodge, heavy, charged or parry; a blocked hit breaks your guard; lights still work |
| Costs | §4 table per family; dodge §6; parry attempt 6 (a success refunds 15); feint 8; full-charge hold 4/s; bow hold 5/s after 1 s at full; blocking per §7 |
| Free | sprinting, jumping, swimming, climbing, mining, building |

The HUD bar (Figma, ROADMAP §7) shows the pool, a ghost of what the next action costs, and the regen delay; it fades
out after 3 s full and out of combat.

## 9. Poise, stagger, flinch

**Creatures** have poise (BESTIARY.md profiles: zombie 20, skeleton 15, wolf 12, brute 60, elite ×1.5). Each hit
deals poise damage = damage before resistances × weapon poise factor × (light 1.0, heavy 1.5, charged 2.0). At 0:
**Staggered** (common 20 ticks, elite 14), open to a riposte-strength Execute. Poise refills fully after 60 ticks
without poise damage (elites 80).

**Bosses** have a **Break meter** instead: poise damage, parries (+8 %) and weak-point hits fill it; when full the
boss is **Broken** for 60 ticks (falls, kneels, or reels: authored per boss), then the meter empties. The meter is on
the boss bar.

**Players** have poise from armour class (light 10, medium 18, heavy 28, plus Mettle). A single hit whose poise
damage is at least your remaining poise **flinches** you: your current action is interrupted (6 ticks). Poise refills
after 40 ticks. Hyper-armour (+20) on heavies and charged attacks lets a heavy-armoured warrior trade blows; a rogue
in leather must not get hit.

## 10. Hits

### 10.1 Detection (server)

- **Player melee**: each move defines an arc (angle, reach, height band). On each ACTIVE tick the server sweeps the
  arc from the player's eye along the look taken at the first ACTIVE tick, interpolating between ticks so fast
  swings don't skip a target at 20 ticks a second. Reach = the move's reach + `entity_interaction_range` modifiers
  above vanilla's 3.0 (Arsenal hafts, Mettle's Measure). A hit needs line of sight from the eye to the target's
  centre or head (no hits through walls).
- **Creature melee**: hit volumes from the move's server timeline (ANIMATION.md §9): capsules baked from the clip,
  swept tick to tick.
- **Projectiles**: vanilla projectile entities with a Fray hit context; arrows keep vanilla flight.
- **Hit-once**: an action instance hits each target once unless the move says `multi_hit` (a flurry, a channel).
  Fray's own hits bypass vanilla's 10-tick damage cooldown and use a 4-tick per-attacker floor instead
  (CONTRACTS.md §4.2).
- **Your declared target.** The request carries what was under your crosshair or locked; the server always tests it
  first and accepts it within reach + 0.5 blocks (lag margin), so a swing at a fleeing wolf that your screen showed
  connecting isn't lost.

### 10.2 Critical hits

Criticals come from **situations** (riposte, backstab, plunge, weak point, Execute, a perfect dodge's follow-up) and
from `fray:crit_chance` (0 by default; cards, runes and brews add it, ceiling 40 %). A critical from crit chance
deals `fray:crit_damage` (×1.5 by default). Mettle cards that speak of "critical hits" (Keen, Bleed, Bone Breaker)
read the critical flag; the vanilla jump critical becomes the plunging strike.

**Situational multipliers don't multiply each other.** A hit takes the largest of riposte, backstab, plunge,
Execute, charged or a rolled critical, then a weak point's multiplier on top. A riposte, backstab or Execute counts as
a critical for perks but doesn't add crit damage again. Combined, the situational part is capped at ×3.0 (×3.5 with a
Mastery that says so, such as Duelist or Assassin); see CONTRACTS.md §7.6.

### 10.3 Latency (multiplayer)

| Case | Rule |
|---|---|
| Your attack on a creature | the server tests your declared target first (above); creatures aren't rewound |
| Your attack on a player | the server rewinds the target's hitbox to where your screen showed it, up to 4 ticks (200 ms), from a 6-tick position history kept for players in combat |
| A creature's attack on you | **defender's grace**: a hit on a player is held for up to half the player's measured ping, at most 3 ticks (150 ms); a dodge or parry request stamped earlier than the hit's active tick that arrives in that time cancels it |
| Feel at 150 ms ping | tells are 8+ ticks (400 ms) for anything heavy, so reactions stay possible; the grace covers the rest |

## 11. Lock-on

| | |
|---|---|
| Toggle | middle mouse; picks the creature nearest the crosshair within 35°, line of sight, 20 blocks (bows and foci 48) |
| Switch | scroll, or flick the mouse more than 8° in 3 ticks toward another candidate |
| Breaks | target dies, is beyond 30 blocks (60 ranged), or out of sight for 40 ticks |
| Camera | soft follow: yaw eased 20 % a frame toward the target, pitch 10 %; you can still look away |
| Movement | while locked, walking strafes around the target; dodges are relative to it |
| Attacks | a swing's direction snaps to the target if it is within 60° of your look (the server checks the same 60°) |
| Shown | a small reticle on the target, its health and build-up meters (HUD, Figma) |
| PvP | allowed, but no attack snapping and no camera easing (camera only marks the target) |

**Combat camera** (option): an over-the-shoulder third person, offset 0.6 right, 3 blocks back (the 26.3
`camera_distance` attribute plus a side offset), for players who prefer it. First person stays the default.

## 12. Feel targets

| | Light | Heavy | Charged (full) | Parry | Kill |
|---|---|---|---|---|---|
| Hit-stop (client only, attacker and target) | 45 ms | 75 ms | 110 ms | 140 ms | +30 ms |
| Camera shake | none | 0.25°, 120 ms | 0.6°, 180 ms | 0.3°, 100 ms | — |
| FOV kick | — | — | −3 % for 150 ms on release | — | — |
| Knockback (blocks) | 0.15 | 0.45 | 0.8 | attacker 0.3 | 0.6 |
| Being hit (you) | 0.25 shake-free | 0.5° shake, 0.6 knockback | — | — | — |

- **Hit-stop** freezes only the two clips (and their particles) for that long, then catches up over the recovery;
  the server's timeline never pauses, so nothing desyncs.
- **Sound**, layered: a whoosh on the first ACTIVE tick (by weapon weight), an impact by what was hit (flesh, bone,
  mail, plate, wood shield, stone or construct), a distinct ring for a parry, a muffled thump for a block, a crunch
  for a stagger, a whisper for a Slip. Sound budget and style: Gloam's soundscape team owns the palette.
- **Particles**: impact puffs coloured by material (dust, bone chips, sparks on metal; never blood), status puffs
  (frost motes, sparks, sickly green), weapon trails only on heavy, charged and runed swings (drawn with vanilla
  translucent render types; ANIMATION.md §7.5).
- **Damage numbers**: off by default (the user's call, ROADMAP D9); when on, small, flat, coloured by kind.
- **Enemy reactions**: every creature has light and heavy hit reactions (additive clips) and a stagger clip; a
  stagger is unmistakable.

## 13. Reference damage by stage

What the reference player (OVERVIEW §5.1) deals with a sword, before the target's armour and resistances:

| Stage | Light | 3-hit string | Heavy | Full charge | Riposte | Backstab (Rogue) |
|---|---|---|---|---|---|---|
| Early | 6 | 20 | 10 | 14 | 12 | 15 |
| Mid | 9 | 30 | 14 | 22 | 18 | 23 |
| Late | 14 | 46 | 22 | 34 | 28 | 35 |
| Endgame | 20 | 66 | 32 | 48 | 40 | 50 |
| Apex | 26 | 86 | 42 | 62 | 52 | 65 |

## 14. PvP

| Rule | Value |
|---|---|
| Damage between players | ×0.6 (time to kill between equal players about 8–12 s) |
| Parry | allowed; Stagger 10 ticks, riposte window 10 |
| Dodge | as PvE |
| Lock-on | camera marker only (§11) |
| Control effects (stun, root, Frozen, daze, slowness, silence) | diminishing returns per victim: a second within 10 s lasts half, a third a quarter, then immune for 10 s |
| Healing brews | toxicity ×1.5; healing over time only (no instant heals) during combat with a player |
| Mettle | `pvp_perks` (full / reduced / off) applies to Fray hits too; `pvp_xp_scale` unchanged |
| Accessibility windows | never apply against players (the server uses the standard windows) |
| Hearth | PvP inside towns stays blocked unless the server turns on `pvp_in_towns` |
| Lag | §10.3 rewind for hits on players |

## 15. Accessibility and options

| Option | Default | Notes |
|---|---|---|
| Parry and dodge windows (PvE) | standard | forgiving ×1.5, very forgiving ×2.0; set per player, checked on the server, PvE only |
| Hold or toggle guard | hold | |
| Combo assist | off | one press finishes the whole light string |
| Simple stamina | off | lights cost nothing (a server may forbid it) |
| Telegraph cues | on | a sound and an edge flash for red and purple attacks aimed at you; colours shape-coded (diamond, triangle) for colour-blind players |
| Screen shake | 100 % | slider to 0 |
| Hit-stop | on | off for motion sensitivity |
| Flash reduction | off | softens full-charge and Slip flashes |
| Auto lock-on | off | locks the nearest threat when you attack |
| Combat camera | first person | over-the-shoulder option |
| HUD scale and position | Hub settings | stamina bar beside or above the hotbar |
| Subtitles for combat sounds | follows vanilla subtitles | tells have their own subtitle lines ("Wight draws breath") |

All of these live on a **Combat** page in the Hub's settings (Figma first).

## 16. Mettle hooks

### 16.1 The keys Mettle answers (CONTRACTS.md §13.4)

`action_speed`, `stamina_cost`, `parry_window`, `iframe_ticks`, `guard_efficiency`, `poise_damage`,
`status_buildup`, `technique_cooldown`, plus attribute modifiers on `fray:max_stamina`, `stamina_regen`, `poise`,
`crit_chance`, `crit_damage`. Everything ends at the ceilings (CONTRACTS.md §7.6).

### 16.2 Existing combat perks under the new rules

Parry, dodge, blocking and heavy attacks are now everyone's, so the perks that granted them become **better versions**
of them. Mettle owns the final wording and numbers; this is the proposal for the Mythic retune.

| Perk (Mettle TREES.md) | Was | Becomes |
|---|---|---|
| Blades · Fencer | swings faster | light recovery −5/10/15 % (`action_speed`) |
| Blades · Disarm | crits knock weapons away | a **riposte** knocks the weapon away one time in three |
| Blades · Light Feet | attacking keeps the sprint | running strikes keep the sprint, and cost no stamina |
| Blades · Wide Arc | sweeps hit 75 % | the arc's other targets take 75 % (instead of 50 %) |
| Blades · Bleed | crits bleed | criticals add Bleed build-up ×2 |
| Blades · **Duelist** (Mastery) | right-click parry, 0.4 s | parry window +2 ticks; ripostes ×2.5 and always disarm commons |
| Blades · **Whirlwind** (Mastery) | sneak + attack spins | an **Art** (sneak + C): 360° spin, 8 s cooldown (`technique_cooldown` and Echo apply) |
| Blades · Master Strike (Teaching) | counter after a block | guard counters and ripostes ignore all armour |
| Heavy · Iron Grip | no knockback | +15 poise while wielding a heavy weapon |
| Heavy · Stagger | charged hits interrupt | charged hits deal ×1.5 poise damage |
| Heavy · **Earthshaker** (Mastery) | sneak-attack slam | an Art: 4-block slam, Stagger commons, 10 s |
| Heavy · Overhead Blow (Teaching) | jump crits ×2 | plunging strikes ×2.0 and Daze 10 ticks |
| Marksmanship · Steady Breath | sneaking full draw flies straight | holding a full draw while sneaking costs no stamina |
| Marksmanship · **Deadeye** (Mastery) | head hits ×2 | weak-point hits ×2.0 (any hurt volume flagged weak, not only heads) |
| Marksmanship · **Volley**, **Arrowstorm** | sneak release / hold | Arts on sneak + C |
| Defense · Shield Up | raises instantly | block active from tick 0 |
| Defense · Shield Bash | shove after lowering | guard counters with a shield Daze 20 ticks |
| Defense · Perfect Block | reflect half within 0.25 s | shield parries also reflect half the blocked damage to the attacker |
| Defense · Bulwark | blocks from the sides | shield coverage 270° |
| Defense · **Guardian** (Mastery) | allies take 40 % less; hostiles turn on you | unchanged; the "turn on you" goes through Fray's threat (taunt) |
| Defense · **Juggernaut** (Mastery) | no knockback, sprint bowls over | +20 poise in heavy armour; sprint bowling Staggers commons |
| Stealth · Assassin, Shadowstep | sneak attacks, sneak-jump step | unchanged; Shadowstep becomes an Art |
| Athletics roots and paths | movement | gain the **stamina** numbers: max stamina, regen, cheaper dodges (the Mythic retune adds them) |

### 16.3 Cards and keywords

Keyword sets already speak combat (Mettle CARDS.md §6.3) and read Fray's events: **Swift 6** Slipstream (a sprint
into a hit) adds "or a Slip"; **Keen 4** (first hit on a full-health target is a critical) uses Fray's crit flag;
**Echo** shortens technique, Art and spell cooldowns through `technique_cooldown` and `spell_cooldown`; **Warden 4**
and **Bodyguard** use Fray's damage stage 5; **Iron 6** Ironskin is a stage 5 reduction; **Brutal 4** dazes via
Fray's Daze. Mettle's simulator reruns with Fray's numbers (ROADMAP stage 2).

## 17. How we test the feel

- **Unit (JUnit, Fray)**: phase math, buffers, cancel windows, stamina regen and Winded, poise refills, the pipeline's
  pools and ceilings, i-frame and parry windows at every tick boundary.
- **Game tests (server)**: a scripted zombie swings at a dummy player: parry at tick −1, 0, +4, +5, +6 of its active
  tick; dodge at the same; at simulated pings of 0, 100, 150 and 250 ms (a dev latency injector on the connection).
  Expected outcomes are table rows, not eyeballing.
- **Client game tests**: screenshot strips of each family's light, heavy and charged in first and third person;
  a GIF per family for the user.
- **Playtest checklist per family**: 10 fights against each stage-1 creature; does every attack have an answer, does
  every death feel earned, is the stamina bar ever annoying outside fights (it must not be).
