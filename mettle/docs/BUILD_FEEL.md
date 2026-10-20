# VOID Mettle — build feel: momentum, bynames, boons, the first ten minutes, Mythic shards

**Status: design, 2026-10-20, branch `mettle-build-feel` (from `mettle` 0.2.0). No game code yet.** Every number below
comes from the simulator `BuildFeelReportTest` (§9), which prototypes these rules beside today's in the test sources;
`./gradlew test --tests '*BuildFeelReportTest' -Dmettle.balance=true` writes `build/reports/mettle-build-feel.md`.
Builds on [CARDS.md](CARDS.md) (cards, keywords, sets, rebuilds, Mythics, §14 ceilings) and [DRAFT.md](DRAFT.md)
(cadence). Where they disagree, this doc wins once its wave ships (§10). Screens go to Figma first (§8).

**What the user asked for:** "make it exciting and fun. We want players to feel like they are building out their
character."

A player has to know five more things:

1. **Your deck pulls.** The cards you slot make cards like them likelier, so a set you start comes together. You can
   also **Focus** one keyword to say where you are heading.
2. **You have a byname.** Your build gives you a name ("Ashford the Deepdelver") that grows with it: "Deep Ember
   Warden", then "Deep Ember Warden of the Mountain".
3. **Odd levels give a boon.** Temper a card's trait, Inscribe a keyword on a card, or take a reroll.
4. **The hand shows what a card would do to you** before you keep it: "Deep 3 → 4: Deep 4 opens".
5. **Mythics can be forged.** Shards come from mastery and deeds; thirty forge a Mythic at a Card Shrine. World firsts
   stay one of a kind.

---

## 0. What is wrong today (measured)

The simulator plays characters the way people play: a **focused** character has three main skills, two side skills at
30 % of their XP and three dabbled ones at 8 % (when the mains reach 20, the sides are near 12 and the rest near 7,
character level 39). **Mid-game** is character level 20 (the mains near 11). The all-eight lockstep character of
`CardReportTest` is kept for comparison.

- **Sets happen to you, they aren't built.** A focused character already holds *some* 4-set by character level 11
  (100 % by mid-game), but it is whatever the points-best cards carried: Hoard, Iron, Brutal, Hex. Nothing helps a
  player who wants a particular set.
- **Chosen keywords stall.** A committed player (keeps cards of one keyword, mains chosen for it) reaches a 6-set by
  skill 20 in Tide **7 %**, Deep **23 %**, Kin **44 %**, Warden 69 %, Hex 73 % of careers (CardReportTest's
  all-eight, sets not chased: Tide 0 %, Deep 3 %, Kin 13 %).
- **Odd levels from 7 are dead.** Seven of a skill's fourteen levels past 6 give only a reroll; with rerolls held at
  5, many are lost.
- **No identity on show.** Nothing says what your character *is*; the name card shows background, level and Mythics.
- **Mythics are a lottery.** A focused player draws one with 4.4 % (nine drafts at 0.5 %); a fully maxed character
  11 %; a 20-player server sees about 2.4 of the eight drawn ones in its life. Deeds exist but show no progress.
- **Big learning load, no guide.** Keywords, sets, slots, rebuilds, attunement and Mythics all arrive in the first
  hour with only a "Press K" chip.

---

## 1. Build momentum

### 1.1 Commitment

A keyword's **commitment** is the number of slotted, non-dormant cards carrying it (all decks), plus 2 for the Focus
keyword (§1.3), counted up to **4**. Mythics carry no keyword and add nothing. Commitment is read at the moment a hand
is dealt, so it follows the deck as it is now.

### 1.2 The lean

Two places in the deal lean toward what you are committed to. Nothing else in the roll changes.

| Where | Rule | At full commitment (4) |
|---|---|---|
| **The perk** (CARDS.md §3.1) | Within its depth band, a perk's weight × (1 + **1.0** × commitment of its native keyword ÷ 4). The band's total is kept (the leaned weights are scaled back to it), so the depth odds, and with them the power odds, never move. | ×2 against an uncommitted perk of the same band before rescaling; no perk drops below half its old chance |
| **The second keyword** (CARDS.md §3.4) | When a card rolls a second keyword, each keyword of the skill's pool weighs 1 + **0.5** × its commitment. The chance *of* a second keyword is unchanged (Common 0, Rare 30 %, Epic 70 %, Cursed 100 %). | 3× an uncommitted keyword: in a pool of five, one keyword at 4 is 3 / 7 = 43 % instead of 20 % |

- **Wildcards stay** (12 %, from the last four skills used) and lean the same way inside their own skill.
- **Hex never leans**: it is the cursed cards' native keyword and no pool holds it, so Pact stays a price you choose.
- Rerolls deal under the same lean. The Mythic roll is untouched.
- The lean is a weight, never a filter: every card that can be dealt today can still be dealt.

### 1.3 Focus

On the Deck screen (and offered once, at your first set, §4), you may **Focus** one keyword: it counts as two slotted
cards for momentum, **never for sets**. One Focus per character; changing it is free once per in-game day (the pin's
rule, CARDS.md §6.1). With no Focus the lean still follows your slotted cards.

### 1.4 What it does (simulator, before → after)

| | Today | Proposed |
|---|---|---|
| A focused player who follows the game's Focus suggestion (§4): a 4-set **in that keyword** by mid-game · a 6-set in it by the mains' skill 20 | 78 % · 75 % | **98 % · 98 %** |
| First 4-set / first 6-set (character level, focused, guided) | 11.3 / 18.0 | **10.8 / 15.1** |
| All-eight character, first 4-set / 6-set (character level) | 8.1 / 17.0 | 8.0 / 15.7 |
| Power at skill 20 (momentum alone, no boons) | 1.11× / 1.21× | 1.11× / 1.21× |

Momentum alone is power-neutral by construction (§9.3). It mostly helps the keywords people already reach; the scarce
ones need Inscribe too (§3), which is why both ship together: the per-keyword table is in §3.5.

---

## 2. Build identity

### 2.1 The byname

Every character earns a **byname** from its build: "Ashford **the Deepdelver**". It is built from the build's top
keywords and top skills, and grows in four tiers as the build matures.

- **kw1, kw2**: the active sets in order (highest tier, then most cards, then the Focus, then the one reached first).
- **skill1**: the skill with the most slotted points (perks + traits, CARDS.md §14's points). **skill2**: the next,
  when it holds at least half of skill1's points.

| Tier | Earned when | Form | Example |
|---|---|---|---|
| — | from the start | the background | Ashford · Smith |
| **I · Marked** | first 2-set active | `{prefix kw1}{suffix skill1}` | the Deepdelver |
| **II · Proven** | first 4-set | `{word kw2} {prefix kw1}{suffix skill1}` (one active set: tier I's form) | the Ember Deepdelver |
| **III · Renowned** | first 6-set, or a Mastery slotted beside a 4-set | `{word kw1} {word kw2} {pair noun skill1 + skill2}` (no skill2: `{noun skill1}`) | the Deep Ember Warden |
| **IV · Legend** | tier III and a Mythic slotted | tier III + `{epithet of the Mythic}` | the Deep Ember Warden of the Mountain |

**Keywords** (prefix for compounds · word for the others):

| Brutal | Deep | Echo | Ember | Gale | Hex | Hoard | Iron | Keen | Kin | Swift | Tide | Warden | Wild |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Grim · Grim | Deep · Deep | Echo · Echoing | Ember · Ember | Gale · Gale | Hex · Hexed | Gold · Gilded | Iron · Iron | Keen · Keen | Kin · Kindred | Swift · Swift | Tide · Tidal | Ward · Warding | Wild · Wild |

**Skills** (suffix · noun): Mining delver · Delver; Blades blade · Blade; Heavy Weapons hammer · Breaker; Marksmanship
shot · Bowman; Defense guard · Shield; Athletics runner · Runner; Survival walker · Strider; Smithing smith · Smith.
Tier I gives 112 compounds (Swiftblade, Embersmith, Tidewalker, Ironguard, Galeshot, Hexhammer…).

**Pair nouns** (skill1 + skill2, either order; where a synergy exists its name is the noun):

| | Blades | Heavy | Marksman. | Defense | Athletics | Survival | Smithing |
|---|---|---|---|---|---|---|---|
| **Mining** | Undercutter | Stonebreaker | Cavehunter | Warden | Pathcutter | Spelunker | Forgemaster |
| **Blades** | | Weaponmaster | Skirmisher | Knight | Bladedancer | Reaver | Swordsmith |
| **Heavy** | | | Raider | Vanguard | Marauder | Woodsman | Hammerwright |
| **Marksman.** | | | | Sentinel | Outrider | Huntsman | Fletchwright |
| **Defense** | | | | | Legionary | Keeper | Armourer |
| **Athletics** | | | | | | Trailblazer | Wandersmith |
| **Survival** | | | | | | | Hearthwright |

Each pair noun also has a one-line description for the build summary ("Warden: a miner who holds the line").
**Mythic epithets** are one data field per Mythic ("of the Mountain", "of the Twin Edge", "Dragonfelled", "of the
Hundredth Dawn"). Everything is lang and data (`data/mettle/mettle/byname/*.json`), so a server can rename. Tier III
alone has about 6,500 forms.

**Rules that keep it steady:**

1. **Tiers are earned and never drop.** Rebuilding away from a 6-set keeps tier III; the words follow the build.
2. **No flicker.** When the build's byname differs from the one you wear, the Deck screen offers it ("Your build reads
   as **the Iron Ember Vanguard** · Take it"); a tier rise takes the new byname at once, with its moment (§2.4).
3. **Every byname you have held is kept** in a list on the character sheet, and you may wear any of them.
4. The byname shows on the name card, the character sheet, chat hover and (Hearth) in how folk greet you
   (`CardService.byname(player)`, a new api call).

### 2.2 Signature cards

Each deck may mark one slotted card as its **Signature** (free, any time). The Signature of skill1's deck is the
character's crest: its art on the name card and character sheet. Signatures are pure identity (no power): history
records "Signature of Ashford, day 41-90", and the first one chosen is a milestone. A Signature that leaves the deck
stops being one.

### 2.3 "Your build" in plain words

One screen, a few lines, generated from the build:

```
the Deep Ember Warden                         Tier III · Renowned
A miner who holds the line.

· In the dark you heal, and your first hit is a critical.  Deep 6 (Underdark)
· Below ground you dig 24 % faster and take 10 % less.     Deep 2, Deepborn
· Fire barely touches you; your hits set foes alight.      Ember 4
· Armour and shields wear 10 % slower.                     Iron 2
· Your Mastery: Deep Delver.                               Mining
· Next: two more Ember cards → Kindled.
```

- **Headline**: byname, tier, the pair noun's description.
- **Up to five lines**, in order: each active set (its tier's `summary` line, new in keyword data), then the two
  traits nearest their caps not already said, then the slotted Mastery and Mythics. Numbers are the boosted, capped
  ones the game applies; a capped stat says so ("Movement: capped at +35 %").
- **Next**: the nearest milestone (one card from a set tier, a byname tier, a forge).
- Pure: `character.cards.BuildSummary.of(derived, bynames)`; the overlay gets `cards.build` (§8).

### 2.4 Milestones

Each fires **once per character** (remembered in the journal), through the existing moment queue (UI.md §6b): one
toast at a time, held while a screen is open or for 5 s after you were last hurt. "Big" ones add a 2.5 s banner over the
name card, skippable with any key.

| Milestone | Trigger | Toast (title / line) | Sound (SOUNDS.md) | Big |
|---|---|---|---|---|
| First card slotted | first slot ever | "Your first card · Quarryman I" / its effect | `slot` | |
| First set | a keyword reaches 2 | "Set · Deep 2" / its line | `set_complete` | |
| Byname I | §2.1 | "You are **the Deepdelver**" / "Your build has a name" | `set_complete` + level chime | yes |
| First 4-set | a keyword reaches 4 | "Deep 4" / its line | `set_complete` | |
| A keyword's first 6-set | each keyword, once | "**Underdark** · Deep 6" / its line | `set_complete`, then the Mythic shimmer tail (0.8 s) | yes |
| Byname II / III / IV | §2.1 | "the Deep Ember Warden" / tier name | `set_complete` + bell | yes |
| First Signature | §2.2 | "Signature · Deep Delver" / "It is on your name card" | `slot` | |
| First Temper / Inscribe | §3 | "Tempered · Deepborn 8 → 10 %" / "Inscribed · Deep on Tunneler" | `pick.epic` sweetener | |
| A forge is ready | shards reach the cost | "A legend can be forged" / "Take 30 shards to a Card Shrine" | feat fanfare | |

---

## 3. No dead levels: boons

### 3.1 The choice

From skill 7, each odd level (7, 9 … 19; seven per skill) gives a **boon** instead of a bare reroll. A boon waits like a
draft (a badge counts them, they never expire) and is spent on one of:

| Choice | What it does |
|---|---|
| **Temper** | raise one trait on one card of this skill by a quarter of its range |
| **Inscribe** | set the second keyword of one card of this skill |
| **Reroll** | +1 reroll, as today (greyed while you hold 5) |

A boon of Mining works on Mining cards (binder or slotted, attuned, not in a trade or listing). Wildcards are cards of
their own skill (CARDS.md §5.2), so a Blades card in your binder takes a Blades boon.

### 3.2 Temper

- One trait on the card rises by **¼ of its range**, rounded to the nearest step, never above the highest value its
  rarity can roll (Common: two thirds of the range; Rare, Epic, Cursed: the max). Deepborn (5–12 %) on a Rare:
  8 % → 10 %; on a Common: 8 % → 9 %, the top of a Common's band.
- **Once per card** (a ◆ Tempered mark, kept through trades and scars; history: "Tempered by Ashford at Mining 9 ·
  Deepborn 8 → 10 %").
- **A deck holds at most three tempered cards** (a slot rule like the Mythic one; the Deck shows "Tempered 2 / 3").
- Per-card caps and trait totals (CARDS.md §4.1) and the whole-character ceilings (§14) are unchanged; a scar can still
  take a tempered trait; fusion keeps the best trait at its tempered value.
- The hand-style preview shows the result before you confirm.

### 3.3 Inscribe

- The card's second keyword becomes one you choose from its skill's pool, never its native one (a Common gains a
  second keyword; any other loses the one it rolled). Cursed cards keep Hex and may have their second inscribed. Hex
  can never be inscribed (no pool holds it).
- **The keyword must already be on another card in that deck** (native, rolled or inscribed): Inscribe strengthens a
  set you have begun, it never starts one from nothing.
- **Once per card** (✎ Inscribed mark; history "Inscribed Deep by Ashford at Mining 11").
- **A deck holds at most one inscribed card** (slot rule; "Inscribed 1 / 1"). A 6-set from inscriptions alone is
  impossible: it always needs cards that came with the keyword.
- Fusion rolls fresh keywords, so an inscription is not carried into a fused card.

### 3.4 Why these two

- **Temper** is the "my character got better" beat at levels where nothing was dealt: small, visible, chosen, and it
  makes a favourite card more yours (and worth more in a trade). The deck cap keeps it from becoming a grind for power.
- **Inscribe** is the steering wheel: the one lever that turns "I want Tide" into a plan. Without it, momentum alone
  leaves Tide near 14 % by skill 20 (§9.2). The seed rule and the cap of one per deck keep scarce sets scarce.
- **Rejected:** shifting a card's *native* keyword (breaks the perk's identity and the Mastery side rules); adding a
  trait (too much power: about +0.55 points each, §14); "draw an extra card" (a draft by another name: v1's
  draft-every-level gave 1.8× the depth, DRAFT.md §6).

### 3.5 What boons do (simulator, committed players, mains chosen for the keyword)

A committed player keeps cards of one keyword (+1 point in its choice), Focuses it from the start, Inscribes toward it
and Tempers otherwise. "Today" is the same player under today's rules.

| Keyword | 6-set by skill 16 / 18 / 20, today | **proposed** | mean skill at the first 6-set | 4-set by skill 12 |
|---|---|---|---|---|
| Tide | 5 / 6 / 7 % | **48 / 56 / 65 %** | 16.7 → 13.8 | 17 → 74 % |
| Deep | 9 / 12 / 23 % | **49 / 63 / 77 %** | 17.9 → 15.7 | 8 → 45 % |
| Hex | 47 / 57 / 73 % | 48 / 56 / 73 % | 16.7 → 16.8 | 26 → 30 % |
| Kin | 20 / 27 / 44 % | **77 / 85 / 92 %** | 17.0 → 12.3 | 27 → 79 % |
| Warden | 44 / 54 / 69 % | **94 / 98 / 98 %** | 16.4 → 11.9 | 42 → 96 % |
| Gale | 64 / 76 / 89 % | 96 / 98 / 100 % | 15.6 → 11.3 | 56 → 92 % |
| Echo | 68 / 75 / 89 % | 98 / 99 / 100 % | 14.9 → 10.3 | 64 → 95 % |
| Wild | 85 / 90 / 97 % | 100 / 100 / 100 % | 12.5 → 7.5 | 98 → 100 % |
| Keen | 94 / 95 / 98 % | 100 / 100 / 100 % | 12.1 → 7.8 | 98 → 100 % |
| Swift | 98 / 99 / 100 % | 100 / 100 / 100 % | 10.6 → 7.4 | 100 % |
| Ember, Brutal, Iron, Hoard | 97–100 % | 100 % | 7.3–9.6 → 6.4–7.7 | 100 % |

Every keyword is reachable; Tide stays the rarest (a recognisable build, as CARDS.md §6.2 wants), then Deep and Hex.
Hex doesn't move on purpose: its cards carry a curse. A focused player's boons go about 18 Temper, 4 Inscribe and 8
rerolls in a career (all-eight: 28 / 4 / 24).

---

## 4. The first ten minutes

**New characters only**: a character created after this ships gets the guide (journal flag `guide`); migrated
characters and anyone who has slotted three cards never see it. Hints are short chips in the VOID style, anchored to
what they talk about, **one at a time**, never during a fight (5 s calm), each **once**. They step aside as soon as you
do the thing they point at, or on ×. The first one carries **"Skip the guide"**; the existing hints switch (UI.md §10)
turns them all off.

| # | When | Hint (≤ 12 words) | Anchored to | Gone when |
|---|---|---|---|---|
| 1 | first skill XP | "Your hands are learning. At Mining 2, a card." | the XP chip | 6 s |
| 2 | the first hand opens (skill 2) | "Three cards. Keep one; the others burn." | the hand | a card is kept |
| 3 | same hand | "Keep & slot puts it to work now." | the Keep & slot button (lit) | kept |
| 4 | first card slotted | "Slotted cards work. The rest wait in your binder." | the deck row | 6 s or the Deck opened |
| 5 | first hover of a keyword badge | "Cards sharing a word make sets: 2, 4, 6." | the badge | unhover |
| 6 | a hand whose card would make your first set | "This one makes a set." | the card's preview line (§6.1) | kept |
| 7 | first set | "A set. Follow it? Focus **Deep**: cards lean your way." · [Focus Deep] [Not now] | the set tracker | a button |
| 8 | first card you can't slot yet | "Slots at Mining 7. It waits in your binder." | the card | 6 s |
| 9 | first full deck and a better card | "Swapping costs. You hold 3 free swaps." | the free-swap counter | 6 s |
| 10 | first boon (skill 7; past the first ten minutes) | "An odd level: temper a card, inscribe one, or reroll." | the boon badge | spent |

**The guided first hand.** A new character's first hand holds at least one **Rare** card (so a trait and a rank show
and can be explained) and is dealt in the background's first favoured skill if that skill reaches 2 first. Nothing
else is forced. In the first ten minutes a player sees hints 1–5 and usually 6–8 (two roots per skill below 6 make a
2-set likely by the second skill's hand).

**In-world voice:** the chips carry the card-back glyph and no "Tutorial" label; they read like a trainer's aside.

---

## 5. Mythics within reach

World firsts (seven) stay **one of a kind**. The drawn roll stays 0.5 % per draft from 15. What changes:

### 5.1 Deeds show progress

The Hall of Firsts and the character sheet show each legendary deed's progress: Long Walker "63.2 / 100 km",
Hundredth Dawn "day 41 of 100", Polymath "5 / 8 skills at 15"; the Unclad and the Lone Banner as a one-line hint. The
nearest deed sits under "Next" in the build summary (§2.3).

### 5.2 Mythic shards

Shards are bound to the character (not items, not tradeable), shown as "Shards 24 / 30".

| Source | Shards |
|---|---|
| A draft from skill 15 (any skill) | 1 |
| A skill reaching 20 | 2 |
| A feat or a synergy earned | 1 |
| A keyword's first 6-set | 2 |
| A drawn-Mythic roll that lands on a Mythic someone already holds | 3 |
| Taking part (≥ 25 % of the damage) in killing an Ender Dragon, Wither, Warden or Elder Guardian after its world first is claimed: once per kind | 2 |

### 5.3 Forging

At a Card Shrine, **30 shards** (then 45, then 60 for each later forge) forge a Mythic: choose one of the eight
**drawn** Mythics of a skill you have at 18 or more.

- **Unclaimed**: you claim the original, as if drawn ("Drawn · the first in this world").
- **Claimed**: you forge a numbered copy ("Forged · the 3rd in this world"), like a deed's copy; the original keeps
  its place in the Hall. One copy of each Mythic per player.
- The reveal plays (SOUNDS.md §3). All Mythic rules hold: two slotted, never two in one deck, never scarred or
  released.

### 5.4 Numbers (simulator, guided players; counted: drafts from 15, skills at 20, one feat at 12 and one at 18 per
skill, synergies, first 6-sets; bosses and claimed rolls are a bonus on top)

| Character | shards when the mains reach 12 / 14 / 16 / 18 / 20 | a forge by 18 · by 20 | two by 20 | character level at the first |
|---|---|---|---|---|
| focused | 8.3 / 9.8 / 17.2 / 24.2 / 37.2 | 1 % · 100 % | 0 % | 37.6 |
| all-eight (fully maxed) | 27.8 / 30.6 / 40.7 / 58.2 / 83.4 | 100 % · 100 % | 100 % (30 + 45) | 35.8 |

Before → after: a focused career earns a Mythic with **4.4 % → about 100 %** (when its mains master, character level
about 38, plus deeds); a fully maxed character **11 % → two forged**, plus deeds and any drawn. Bosses after a world
first and claimed rolls bring the first forge earlier for players who seek them out.

---

## 6. More that makes building feel good

### 6.1 What a card does to your build (the hand preview)

Under each card in a hand, up to three lines, computed on the server from the build as it is:

- **Where it goes**: "Fills slot 5" · "Replaces Quarryman I (+1.4) · free swap" or "· rebuild 120 bits + draught" ·
  "Waits: slots at Mining 7".
- **Sets**: "Deep 3 → 4 · **Deep 4 opens**: hostiles notice you 25 % closer below y 0", and what it breaks: "Iron 4 → 3:
  loses Iron 4".
- **You**: trait totals ("Deepborn 24 → 30 % of 30 %"), a byname change ("Byname → the Deep Ember Delver"), a Focus
  mark when it carries the Focus keyword.

The same preview shows for Temper and Inscribe before you confirm, and in the binder for any card ("If slotted…").
Pure: `character.cards.BuildPreview.of(card, character)` reusing `CardRules.derive`.

### 6.2 Set progress cues

- The set tracker (CARDS.md §13.2) marks the keyword **one card away** from its next tier with a soft pulse and "3 / 4".
- Keyword badges on cards in a hand light when they match an active set or the Focus.
- The binder filter gains "Completes a set" (cards whose slotting would raise a tier).

### 6.3 Loadouts (proposal, needs the user's call)

Save up to two layouts per skill. Switching to a saved layout is a rebuild sitting with the draught and the fees but
**no scars** (scars are what stops trying things), once per in-game day, at a shrine or trainer. Default: **not in the
first waves**; it softens the "expensive rebuild" the user asked for.

---

## 7. Config and data

New fields in `config/mettle/cards.json` (CARDS.md §15):

| Field | Default |
|---|---|
| `momentum_perk_lean` / `momentum_keyword_lean` / `momentum_cap` | 1.0 / 0.5 / 4 |
| `focus_weight` / `focus_change_days` | 2 / 1 |
| `boons_from` | 7 (odd levels from it give a boon; 0 turns boons off and odd levels give a reroll as before) |
| `temper_step` / `temper_per_deck` | 0.25 (of a trait's range) / 3 |
| `inscribe_per_deck` / `inscribe_needs_seed` | 1 / true |
| `shard_forge_costs` | `[30, 45, 60]` (the last repeats) |
| `shard_sources` | draft_from_15 1, skill_20 2, feat 1, synergy 1, first_six 2, claimed_roll 3, boss_after_first 2 |
| `guide` / `first_hand_rare` | true / true |

Data: `data/mettle/mettle/byname/{keywords,skills,pairs}.json` (prefix, word, suffix, noun, pair noun and description),
`summary` lines per set tier in keyword data, `epithet` in Mythic data; all text in lang.

Record changes: `CardData` gains `marks` (`tempered`: trait id and the value before, `inscribed`: keyword) and history
lines; the character's card cache gains `focus`, `boons` per skill, `signatures` per skill, `byname` (worn, held list,
tier), `shards`, `forges`; the journal gains `milestones` and `guide`. Old saves: no marks, no Focus, tier computed on
join from the current build, waiting odd-level rerolls stay rerolls.

---

## 8. Screens for the Figma designer

In the VOID Figma file, page 264:2, a new section **"Build feel (explore)"**; never overwrite earlier frames; mirror
what ships on page 295:2. VOID rules: flat, rounded, no gradients; skill hue = which skill; champagne gold = earned
(bynames, Signatures and forged Mythics use it).

| # | Screen / state | Where | What it needs |
|---|---|---|---|
| B1 | **Character sheet header with byname** | Classes page top strip | Name, "the Deep Ember Warden", tier pips (I–IV) and name, the crest (Signature art), background, level; a "Bynames" list to choose which to wear; the "Your build reads as … · Take it" offer |
| B2 | **Name card** (update of CARDS.md §13.7) | looking at a player, chat hover | Byname under the name, tier pips, crest, up to two Mythics, Trade |
| B3 | **Your build** | Classes page, first tab | §2.3: headline, five lines with their sources, capped-stat note, "Next"; empty state for a new character ("Slot cards to give your build a shape") |
| B4 | **Set tracker with Focus** | Deck (CARDS.md §13.2) | 14 keywords, counts, 2/4/6 pips, active three lit, the Focus marker and control ("changes once a day"), "3 / 4" one-away pulse, Tempered n/3 and Inscribed n/1 pips per deck |
| B5 | **Hand preview lines** | draft hand (§13.3) | Under each card: where it goes, set deltas (open / break states), you-lines; Focus-lit keyword badges; a long-line overflow state |
| B6 | **Boon choice** | a skill's page and the draft area when a boon waits | Badge with count; three choices (Temper, Inscribe, Reroll greyed at 5); then a card picker (eligible cards lit, why others aren't) |
| B7 | **Temper confirm** | after picking a card | Its traits, the chosen one "8 % → 10 %", band-top note, deck "Tempered 2 / 3", Confirm |
| B8 | **Inscribe confirm** | after picking a card | Pool keywords as badges (illegal ones dim with the reason: "needs Deep on another Mining card"), the set delta preview, "Inscribed 0 / 1", Confirm |
| B9 | **Card marks** | card face, all sizes | ◆ Tempered (the raised value in Temper blue), ✎ Inscribed (the keyword badge with a small quill), Signature (gold corner), history lines |
| B10 | **Milestone toast** | HUD (UI.md §6b style) | Set, first card, Temper/Inscribe, forge-ready variants |
| B11 | **Milestone banner** | HUD, 2.5 s | Byname tier-up and a 6-set: the byname or set name large over a slim name-card plate; skip hint |
| B12 | **Guide hints** | anchored chips | The chip with card-back glyph, an anchor pointer, ×, "Skip the guide" variant, the Focus offer with two buttons (§4 #7) |
| B13 | **Guided first hand** | draft hand | Lit Keep & slot, the hint over the hand, a Rare card's trait called out |
| B14 | **Shards and forge** | Card Shrine screen (CARDS.md §13.5) new tab "Forge" | Shard meter "24 / 30", the eight drawn Mythics (unclaimed: "claim the original"; claimed: "forge the 4th"; locked: "needs Smithing 18"), Confirm → the reveal |
| B15 | **Deed progress** | Hall of Firsts (CARDS.md §13.1) | Progress bars per deed, hints, holders and copies; shards line |
| B16 | **Binder filter and preview** | Binder (§13.1) | "Completes a set" filter; the side panel's "If slotted…" preview |

---

## 9. The simulator (`BuildFeelReportTest`)

Test-only: the proposed deal (momentum, Focus), boons (Temper, Inscribe), the byname-free parts of milestones and
shards are written inside the test against the shipped data and `CardRules`, so nothing in the game changes until §10.
Levers can be swept with a `build/bf.properties` file (`bf.perkLean`, `bf.keywordLean`, `bf.focus`, `bf.inscribeCap`,
`bf.inscribeSeed`, `bf.temperStep`, `bf.temperDeckCap`, `bf.shardCost`, `bf.runs`, `bf.sections`). Players: **points**
(today's simulator player: keeps the card that adds most points, tempers), **guided** (as points, accepts the Focus
offer at its first set and inscribes toward it), **committed** (Focus from the start, +1 point for its keyword's cards,
mains chosen as the three skills that carry the keyword best).

### 9.1 Sets (800 careers a row)

| Character · rules · player | first 2 / 4 / 6-set (character level) | any 4-set by mid-game | own Focus: 4-set by mid-game · 6-set by 20 | boons: temper / inscribe / reroll |
|---|---|---|---|---|
| focused · today · points | 4.7 / 11.3 / 18.0 | 100 % | — | 0 / 0 / 30 |
| focused · momentum only · points | 4.7 / 11.0 / 16.9 | 100 % | — | 0 / 0 / 30 |
| focused · proposed · points | 4.7 / 11.0 / 16.9 | 100 % | — | 20 / 0 / 10 |
| focused · today · guided | 4.7 / 11.3 / 18.0 | 100 % | 78 % · 75 % | 0 / 0 / 30 |
| focused · proposed · guided | 4.7 / 10.8 / 15.1 | 100 % | **98 % · 98 %** | 18.1 / 3.8 / 8.0 |
| all-eight · today · points | 4.1 / 8.1 / 17.0 | 100 % | — | 0 / 0 / 56 |
| all-eight · proposed · guided | 4.1 / 8.0 / 15.7 | 100 % | 96 % · 99 % | 28.1 / 4.4 / 23.6 |

(This harness plays wildcards and rerolls, which CardReportTest's set table doesn't, so its all-eight "today" reads
8.1 for the first 4-set where CardReportTest reads 9.0.)

### 9.2 Lever sweep (committed players, 6-set by skill 18; 300 careers a keyword, sweep runs, about ±3 %)

| Variant | Tide | Deep | Kin | Warden | Notes |
|---|---|---|---|---|---|
| today | 6 % | 12 % | 27 % | 54 % | |
| momentum only (lean 1.0 / 0.5, Focus 2, no Inscribe) | 8 % | 24 % | 47 % | 64 % | Tide barely moves: two skills hold it, one native |
| + Inscribe, 1 a deck, no seed rule | 55 % | 66 % | 97 % | 96 % | Kin becomes easy (four pools) |
| **+ Inscribe, 1 a deck, seed rule (chosen)** | **56 %** | **63 %** | **85 %** | **98 %** | Tide stays the rarest |
| + Inscribe, 2 a deck, seed rule | 73 % | 80 % | 94 % | 100 % | 6-sets too common |

### 9.3 Power and variety at skill 20 (single-skill careers as CardReportTest, 1,500 a skill; Temper on every boon)

| | Today | Proposed |
|---|---|---|
| Deck vs v1 draft, base slots (limit 1.15×) | 1.11× | **1.12×** (worst skill 1.13×) |
| Wits 14, +2 slots (limit 1.3×) | 1.21× | **1.23×** (worst 1.24×) |
| Momentum alone | — | 1.11× / 1.21× |
| Temper without the deck cap (¼ step) | — | 1.14× / 1.24× (worst 1.15×: at the limit, so the cap of three) |
| Two careers' decks (Jaccard of perk + trait pairs, limit 0.25) | 0.154–0.159 | 0.155–0.162 |
| Two players chasing the same keyword, same skill | — | 0.18–0.20 |
| Identical decks in 1,500 pairs | 0 | 0 |
| Tempered traits in a skill-20 deck | — | 2.7 |

The whole-character ceilings (CARDS.md §14) are untouched: Temper never passes a trait's max, trait totals stay capped
at twice the card cap, and `Ceilings` clamps every stat whatever the deck. CardReportTest's worst-case search covers
any tempered deck already (it puts every trait at its total cap).

### 9.4 Mythic shards: §5.4.

---

## 10. Build plan

Each wave: Figma frames first (§8), then code, then the client tour with screenshots. Pure rules in `CardRules` /
new pure classes, server in `character.cards`, screens in the Hub from overlay state.

| Wave | Contents | Done when |
|---|---|---|
| **F1 · Momentum and the preview** | `CardRules.draw` / `keywords` take a `Commitment` (keyword → count, capped; Focus); band-rescaled perk lean; leaned second keyword; Focus on the record, once-a-day change, `setFocus`; `BuildPreview` (placement, set deltas, breaks, trait totals) on every offer (`skills[].hand[].preview`) and binder card; set tracker one-away cues | BuildFeelReportTest §1–§3 pass with the code's own `deal` (the test's prototype deleted); a client tour shot of a hand with preview lines |
| **F2 · Boons** | `DraftState` boons per skill (odd levels from `boons_from`; migration keeps waiting rerolls); `Temper` and `Inscribe` (pure rules + server), `marks` on `CardData` with history, slot rules for 3 tempered / 1 inscribed, previews; overlay `boons`, `temperCard(id, trait)`, `inscribeCard(id, keyword)`, `boonReroll(skill)`; chat fallback `/mettle cards temper|inscribe` | tour: a boon each way at Mining 7, 9, 11; caps refused with reasons |
| **F3 · Identity** | `Bynames` (pure, data-driven) and tiers, held list, the "Take it" offer, `CardService.byname`; Signatures; `BuildSummary`; milestone moments (new `Moment` kinds `set`, `byname`, `signature`, `boon`, `forge_ready`) and the banner; name card and chat hover | tour: a fresh character to byname II in a scripted session, shots of B1–B3, B10–B11 |
| **F4 · Guide** | journal `guide`; the ten hints with anchors (overlay `guide.hint`), skip and the options switch; the guided first hand (one Rare, favoured skill) | tour: a new character's first ten minutes, every hint seen once and none for a migrated character |
| **F5 · Mythic shards** | shard sources (events already exist: drafts, levels, feats, synergies, set tiers, claims, boss credit), the forge at the shrine (cost ladder, original vs copy, numbering), deed progress in the Hall (`cards.mythics` gains `progress`) | tour: shards granted by op, a forge of an unclaimed and of a claimed Mythic |

### Tests

**JUnit (pure):**
- Momentum: with commitment zero every weight equals today's (the deal is today's deal, same seed → same hand); each
  depth band's total is unchanged for any commitment (100,000 random commitments); no perk below half its old weight;
  the cap at 4; Focus counts for momentum and never for `tier`; Hex never leans; determinism from seed and commitment.
- Second keyword: the chance of one is unchanged by rarity; leaned odds match 1 + 0.5 × c over 100,000 rolls (±1 %).
- Temper: +¼ range rounded to step, never above the band top or the trait max; once per card; the deck cap refuses a
  fourth; fusion and scars keep and drop marks as §3.2 says.
- Inscribe: pool only, never native or Hex, the seed rule, once per card, the deck cap, Commons gain a second keyword.
- Bynames: every keyword × skill × pair produces a name in lang; tiers never drop; kw/skill ordering and ties; the
  example "the Deepdelver" → "the Deep Ember Warden" → "… of the Mountain".
- BuildSummary and BuildPreview: set deltas and breaks, capped-stat notes, numbers equal what `derive` applies.
- Milestones fire once per character and wait for screens and calm (MomentQueue).
- Shards: each source once where it says once; cost ladder; forge refused below skill 18; original vs numbered copy;
  one copy each per player.
- Guide: new characters only; each hint once; skip turns all off.

**Simulator:** `BuildFeelReportTest` asserts: power ≤ 1.15× / 1.3× in every skill; deck overlap < 0.25 (any two, and
two chasing one keyword in one skill); no identical decks; every keyword reaches a 6-set when chased. CardReportTest
and DraftReportTest still pass.

**Game tests:** Focus and boons survive death, relog and dimension change; a tempered and an inscribed card traded keep
their marks; a fourth tempered card can't be slotted.

---

## 11. Decisions for the user

1. **Momentum strength**: perk lean 1.0, keyword lean 0.5, Focus counts as 2. Mainstream sets (Brutal, Ember, Iron,
   Hoard, Swift, Keen, Wild) then reach 6 around skill 7–8 for a committed player (today 7–12). If that is too early,
   halve the perk lean.
2. **Odd levels become boons** (Temper / Inscribe / Reroll) from skill 7, and no longer give a bare reroll.
3. **Temper**: ¼ of a range, once per card, three tempered cards a deck. Power 1.11× → 1.12× (Wits 14 1.21× → 1.23×).
4. **Inscribe**: once per card, one inscribed card a deck, only a keyword already on another card in that deck.
5. **Bynames**: the grammar, tiers and word tables of §2.1 (the 28 pair nouns especially: "Warden" for Mining + Defense
   follows the user's example).
6. **Signatures are cosmetic** (no power).
7. **Forging copies of drawn Mythics**: shards forge the original if unclaimed, else a numbered copy (one each per
   player). The alternative is originals only, which leaves late players nothing once the eight are drawn.
8. **Shard cost** 30, then 45, then 60: a focused player forges one as their mains master (character level ~38); a
   fully maxed character two.
9. **The guide** shows to new characters only, with a guaranteed Rare in the first hand.
10. **Loadouts** (§6.3): leave them out for now (recommended, they soften the costly rebuild) or add them in F3.
