# VOID Mettle — build feel: momentum, bynames, boons, the first ten minutes, Mythic shards

**Status: design approved by the user 2026-10-20 with two changes, both applied here: momentum scaled by how common a
keyword is, so every keyword's 6-set lands around skill 12–16 (§1); and Mythics stay one of a kind, so shards forge
only unclaimed Mythics from a wider drawn pool (§5). Branch `mettle-build-feel` (from `mettle` 0.2.0). The first
wave (§1–§6) is built in Mettle 0.3.0: §15.** Every number below
comes from the simulator `BuildFeelReportTest` (§9), which prototypes these rules beside today's in the test sources;
`./gradlew test --tests '*BuildFeelReportTest' -Dmettle.balance=true` writes `build/reports/mettle-build-feel.md`.
Builds on [CARDS.md](CARDS.md) (cards, keywords, sets, rebuilds, Mythics, §14 ceilings) and [DRAFT.md](DRAFT.md)
(cadence). Where they disagree, this doc wins once its wave ships (§10). Screens go to Figma first (§8).

**What the user asked for:** "make it exciting and fun. We want players to feel like they are building out their
character."

A player has to know seven more things:

1. **Your deck pulls.** The cards you slot make cards like them likelier (more for the scarce keywords), so a set you
   start comes together. You can also **Focus** one keyword to say where you are heading. A 6-set wakes once two of
   its decks are at skill 12.
2. **You have a byname.** Your build gives you a name ("Ashford the Deepdelver") that grows with it: "Deep Ember
   Warden", then "Deep Ember Warden of the Mountain".
3. **Odd levels give a boon.** Temper a card's trait, Inscribe a keyword on a card, or take a reroll.
4. **The hand shows what a card would do to you** before you keep it: "Deep 3 → 4: Deep 4 opens".
5. **Mythics can be forged.** Shards come from mastery and deeds; thirty forge an **unclaimed** Mythic of your choice
   at a Card Shrine. Every Mythic stays one of a kind, deeds included (the first to do a deed holds it, later ones get
   shards and its title); when none is left, shards give a legend title and frame.
6. **Talents** are a third layer between attributes and skill decks: 2 to 5 general cards (Hardy, Broad Shoulders,
   Lucky…) from a talent draft every few character levels, changed freely after a night's sleep.
7. **Stealth and Speech** join the released skills, built for VOID Hearth's witnesses, markets and conversations.

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

Two places in the deal lean toward what you are committed to. Nothing else in the roll changes. **How much a keyword
leans is its `momentum` (keyword data), set by how common it is** (user's change, 2026-10-20): the common keywords
already come together on their own, so only the scarce ones are helped.

| Keyword | Cards that carry it (native · in a second-keyword pool) | `momentum` m |
|---|---|---|
| Tide | 1 · 30 | **1.0** |
| Deep | 4 · 30 | **1.0** |
| Kin | 2 (both Masteries) · 60 | 0.5 |
| Warden | 6 · 30 | 0.5 |
| Gale, Echo | 8–9 · 45–60 | 0.25 |
| Wild, Swift, Keen, Brutal, Ember, Hoard, Iron | 8–13 · 45–75 | 0 |
| Hex | 16 cursed · 0 | 0 (never leans: Pact stays a price you choose) |

| Where | Rule | At full commitment (4), m = 1 |
|---|---|---|
| **The perk** (CARDS.md §3.1) | Within its depth band, a perk's weight × (1 + **1.0** × m × commitment of its native keyword ÷ 4). The band's total is kept (the leaned weights are scaled back to it), so the depth odds, and with them the power odds, never move. | ×2 against an uncommitted perk of the same band before rescaling; no perk drops below half its old chance |
| **The second keyword** (CARDS.md §3.4) | When a card rolls a second keyword, each keyword of the skill's pool weighs 1 + **0.5** × m × its commitment. The chance *of* a second keyword is unchanged (Common 0, Rare 30 %, Epic 70 %, Cursed 100 %). | 3× an uncommitted keyword: in a pool of five, one keyword at 4 is 3 / 7 = 43 % instead of 20 % |

### 1.2b Six-sets wake at skill 12

A keyword's **6 tier counts only while at least two of the decks holding its cards belong to skills at 12 or more**
(tier III opens there; CARDS.md §6.1 already asks for two decks). Below that the set works at its 4 tier and the
tracker says "Deep 6 · wakes when two of its decks reach 12". This is what moves the common keywords off skill 7–8:
today a committed Hoard or Brutal player holds six cards by skill 7 with no help at all, so no lean could slow them.
`six_tier_skill` (12) is the lever.

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
| A focused player who follows the game's Focus suggestion (§4): a 4-set **in that keyword** by mid-game · a 6-set in it by the mains' skill 20 | 78 % · 75 % | **95 % · 94 %** |
| A committed player's first 6-set, mean skill: common keywords · scarce (Tide, Deep, Kin, Warden) · Hex | 7.3–12.5 · 16.4–17.9 · 16.7 | **12.0–12.5 · 13.0–14.8 · 16.8** |
| First 4-set / first 6-set (character level, focused, guided) | 11.3 / 18.0 | 11.1 / **22.3** |
| All-eight character, first 4-set / 6-set (character level) | 8.1 / 17.0 | 8.1 / 29.0 |
| Power at skill 20 (momentum alone, no boons) | 1.11× / 1.21× | 1.11× / 1.21× |

Momentum alone is power-neutral by construction (§9.3), and so is the gate (it only delays a tier). The 6-set becomes a
mid-game goal (skill 12–16) for everyone instead of an early-game accident for some and out of reach for others. The
scarce keywords need Inscribe too (§3), which is why both ship together: the per-keyword table is in §3.5.

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

With keyword momentum (§1.2) and the skill-12 gate (§1.2b), 266 careers a keyword:

| Keyword (m) | 6-set by skill 16 / 18 / 20, today | **proposed** | mean skill at the first 6-set, today → **proposed** | 4-set by skill 12 |
|---|---|---|---|---|
| Tide (1.0) | 5 / 6 / 7 % | **66 / 75 / 79 %** | 16.7 → **14.0** | 17 → 83 % |
| Deep (1.0) | 9 / 12 / 23 % | **76 / 89 / 95 %** | 17.9 → **14.8** | 8 → 61 % |
| Kin (0.5) | 20 / 27 / 44 % | **83 / 89 / 95 %** | 17.0 → **13.6** | 27 → 82 % |
| Warden (0.5) | 44 / 54 / 69 % | 94 / 97 / 99 % | 16.4 → **13.0** | 42 → 95 % |
| Gale (0.25) | 64 / 76 / 89 % | 97 / 98 / 100 % | 15.6 → **13.0** | 56 → 90 % |
| Echo (0.25) | 68 / 75 / 89 % | 98 / 99 / 100 % | 14.9 → **12.5** | 64 → 95 % |
| Keen, Wild (0) | 94–98 % by 20 | 100 % | 12.1–12.5 → **12.0–12.1** | 98 → 100 % |
| Swift, Ember, Brutal, Iron, Hoard (0) | 100 % | 100 % | 7.3–10.6 → **12.0** | 100 % |
| Hex (0) | 47 / 57 / 73 % | 48 / 56 / 76 % | 16.7 → 16.8 | 26 % |

Every keyword's 6-set now lands between skill 12 and 17 for a committed player (common ones at the gate, 12; scarce
ones 13–15; Hex 16.8, left as it is). Every keyword is reachable; Tide stays the rarest (a recognisable build, as
CARDS.md §6.2 wants). Hex doesn't move on purpose: its cards carry a curse. A focused player's boons go about 18
Temper, 4 Inscribe and 8 rerolls in a career (all-eight: 28 / 4 / 24).

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
| 11 | first talent draft (character level 3, a few minutes in) | "A talent: who you are, not what you trained. Keep one." | the talent hand | kept |

**The guided first hand.** A new character's first hand holds at least one **Rare** card (so a trait and a rank show
and can be explained) and is dealt in the background's first favoured skill if that skill reaches 2 first. Nothing
else is forced. In the first ten minutes a player sees hints 1–5 and usually 6–8 (two roots per skill below 6 make a
2-set likely by the second skill's hand).

**In-world voice:** the chips carry the card-back glyph and no "Tutorial" label; they read like a trainer's aside.

---

## 5. Mythics within reach, still one of a kind

**User rule: every Mythic is one of a kind.** Shards never make copies. World firsts (seven) are unchanged. Instead the
**drawn pool widens from 8 to 24** (three per skill), the drawn roll keeps its odds (0.5 % per draft from skill 15, now
offering an unclaimed Mythic of that skill), and shards let a dedicated player **choose** an unclaimed one. Deed
Mythics are one of a kind too, since 2026-10-21 (§12).

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
| A drawn-Mythic roll when that skill's three are all held | 3 |
| A legendary deed someone already holds (§12) | 10 |
| Taking part (≥ 25 % of the damage) in killing an Ender Dragon, Wither, Warden or Elder Guardian after its world first is claimed: once per kind | 2 |

### 5.3 Forging an unclaimed Mythic

At a Card Shrine, **30 shards** (then 45, then 60 for each later forge) claim **one unclaimed drawn Mythic of your
choice**, of a skill you have at 18 or more. It is the one and only: "Forged by Ashford · day 212" in its history and
the Hall, exactly like a drawn one. The reveal plays (SOUNDS.md §3). All Mythic rules hold: two slotted, never two in
one deck, never scarred or released; it can change hands only by trade.

- **Nothing unclaimed in your skills at 18** but some left elsewhere: the shards wait (the Forge tab says which skills
  still hold one and what you need).
- **Nothing unclaimed anywhere**: a forge's shards buy a **legend title and frame** instead: a cosmetic only, no
  power. The title is "the Legend of {world name}" (or the player's byname with a "Legend" mark), shown on the name
  card; the frame is a white-gold border on the name card and character sheet. One per player; later forges add a
  star to it (up to three).

### 5.4 The sixteen new drawn Mythics

Each is sized like the eight (an Epic Mastery with traits, about 6 points, CARDS.md §14), has one effect nothing else
in Mettle has, and stays inside the whole-character ceilings (none adds movement, melee or damage-taken beyond what
`Ceilings` clamps). Two per skill; each says why it belongs there.

| Skill | Mythic | Effect | Fit |
|---|---|---|---|
| Mining | **The Keystone** | Sneak-use a pickaxe on a wall to set your keystone (one, moved freely); underground within 256 blocks, the way back to it shows on your locator bar. | The delver who is never lost; it marks, it never moves you (Homeward stays the Far Horizon's) |
| Mining | **The Deep Lantern** | Below y 0, hostiles can't spawn within 12 blocks of you. | The Deep keyword's safety, for the delver who lives down there |
| Blades | **Misericorde** | A blade hit on a mob below 15 % health ends it (never a player or a boss). | The mercy dagger: a finisher's card, Executioner taken to its end |
| Blades | **Red Harvest** | A blade kill readies your swing at once, and your next swing within 2 s sweeps. | Chains fights for the duellist; the swing is still clamped by the melee ceiling |
| Heavy Weapons | **Thunderhead** | In a thunderstorm, a fully charged heavy hit calls lightning on the target (20 s cooldown; never fire on claimed ground). | Tridents and maces as the storm's own |
| Heavy Weapons | **The Dread Maul** | A heavy kill makes hostiles within 6 blocks flee for 3 s (bosses and players untouched; 15 s cooldown). | The brute's reputation; control, not damage |
| Marksmanship | **Hunter's Moon** | Under the night sky, a fully drawn arrow that hits looses a silent second arrow at the same target for half its damage. | Night hunting; ranged, outside the melee ceiling |
| Marksmanship | **The Pinning Star** | A fully drawn arrow that hits a mob's legs (its lower half) roots it for 2 s (once per target every 10 s). | Pinning Shot's legend |
| Defense | **The Iron Vow** | Once per in-game day, a blow that would kill an ally within 8 blocks lands on you instead (after your armour). | The guardian's oath; Bodyguard made absolute, once |
| Defense | **The Long Watch** | Sneak still with a shield raised for 3 s: hostiles within 16 blocks glow for you and allies near you for 10 s (30 s cooldown). | The sentry; information, not armour |
| Athletics | **The Ferryman's Stride** | While you sprint, water bears you like ground (no swimming, no ice made); allies sprinting within 4 blocks too. | Tide and Athletics; movement *kind*, not speed, so the +35 % ceiling holds |
| Athletics | **The Cat's Way** | Sprint along a wall for up to 2 s without falling (8 s cooldown). | The freerunner's gift; height stays under the jump ceiling |
| Survival | **The Beastspeaker's Torc** | Wolves, bears, bees, llamas and other provoked animals never turn on you; an animal you feed follows you for an in-game day. | Wild keyword's legend; the Wild Hunt's quiet cousin |
| Survival | **The Greenway** | Leaves, vines and berry bushes neither slow nor hurt you, and hostiles that lose sight of you lose your trail twice as fast. | The ranger who is never caught in the woods |
| Smithing | **The Ever-Ember** | Furnaces, smokers and blast furnaces you last filled smelt twice as fast and burn half the fuel. | The forge that never cools |
| Smithing | **The Mender's Hand** | Once per in-game day, repair any item fully at an anvil for no levels and with no prior-work cost. | The master smith's touch; Arsenal: one smith repair a day at no cost |

Names, art (stand-in sigils first, as wave D), lore lines and epithets (§2.1: "of the Keystone", "of the Iron Vow")
go in the Mythic data and lang. The pool is per world and reads `mythic/*.json` as now (`source: drawn`, `skill`), so
a server can add or remove drawn Mythics.

### 5.5 Numbers (simulator)

**Shard income** (guided players; counted: drafts from 15, skills at 20, one feat at 12 and one at 18 per skill,
synergies, first 6-sets; bosses and full-skill rolls are a bonus on top):

| Character | shards when the mains reach 12 / 14 / 16 / 18 / 20 | enough for a forge by 18 · by 20 | for two by 20 | character level at the first |
|---|---|---|---|---|
| focused | 7.0 / 8.1 / 14.8 / 21.7 / 36.5 | 0 % · 100 % | 0 % | 37.7 |
| all-eight (fully maxed) | 26.7 / 30.0 / 40.7 / 58.7 / 84.7 | 100 % · 100 % | 100 % (30 + 45) | 37.2 |

**A server, every Mythic one of a kind** (200 servers of 20 players: 4 dedicated with mains to 20, 1 maxing all eight,
7 regulars to 16, 8 casuals to 10; each starts in the server's first half and plays half to all of its life; world
firsts and deeds not counted):

| Rules | drawn pool | claimed in a server's life: by draws · by forges | left unclaimed | a dedicated player holds one | the maxed player holds one · mean held | a regular holds one |
|---|---|---|---|---|---|---|
| today (draws only) | 8 | 0.5 · 0 | 7.5 | 4 % | 14 % · 0.2 | 1 % |
| forging, today's 8 | 8 | 0.3 · 5.7 | 2.0 | 94 % | 100 % · 2.1 | 0 % |
| forging, 16 (8 new) | 16 | 0.3 · 6.0 | 9.7 | 100 % | 100 % · 2.1 | 1 % |
| **forging, 24 (16 new)** | **24** | **0.3 · 6.0** | **17.7** | **100 %** | **100 % · 2.1** | 1 % |

**A server whose players turn over** (100 servers, 60 careers over its life, the same mix three times):

| Rules | drawn pool | by draws · by forges | left | a dedicated player holds one | the maxed player · mean | legend titles given |
|---|---|---|---|---|---|---|
| today (draws only) | 8 | 1.0 · 0 | 7.0 | 3 % | 10 % · 0.1 | 0 |
| forging, today's 8 | 8 | 0.4 · 7.7 | 0 | 37 % | 61 % · 1.1 | 9.5 |
| forging, 16 | 16 | 0.8 · 14.9 | 0.3 | 75 % | 99 % · 2.0 | 1.2 |
| **forging, 24** | **24** | **1.1 · 17.5** | **5.4** | **96 %** | **100 % · 2.1** | **0** |

Before → after:

- **A focused player** (three mains to 20): a Mythic from the drawn pool with **4.4 % → about 100 %** alone, **4 % →
  100 %** on a 20-player server, **3 % → 96 %** on a busy one; it comes as the mains master (character level about 38).
- **A fully maxed character**: **11 % → 100 %**, about two held (30 + 45 shards).
- **A 20-player server**: about **0.5 → 6.3** of the drawn Mythics claimed in its life (CardReportTest's 2.4 assumed
  twenty maxed characters), with 17.7 of 24 still waiting; with players turning over three times, 18.6 claimed and
  5.4 left, so the 24th player to master still finds one. Legend titles appear only when a pool truly runs out.
- Regulars and casuals almost never hold a drawn Mythic (1 %): forging stays a mastery reward. World firsts and deeds
  remain their way in.

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
| `momentum_perk_lean` / `momentum_keyword_lean` / `momentum_cap` | 1.0 / 0.5 / 4 (times each keyword's `momentum`, keyword data, §1.2) |
| `six_tier_skill` | 12 (the 6 tier needs two of its decks at this skill; 0 = today's rule) |
| `drawn_mythics_per_skill` | read from data: 3 a skill with the sixteen new ones (§5.4) |
| `focus_weight` / `focus_change_days` | 2 / 1 |
| `boons_from` | 7 (odd levels from it give a boon; 0 turns boons off and odd levels give a reroll as before) |
| `temper_step` / `temper_per_deck` | 0.25 (of a trait's range) / 3 |
| `inscribe_per_deck` / `inscribe_needs_seed` | 1 / true |
| `shard_forge_costs` | `[30, 45, 60]` (the last repeats) |
| `shard_sources` | draft_from_15 1, skill_20 2, feat 1, synergy 1, first_six 2, claimed_roll 3, boss_after_first 2 |
| `guide` / `first_hand_rare` | true / true |
| `deed_later_shards` / `deed_migration_shards` | 10 / 30 (§12) |
| `talent_slots` | `[[3, 2], [15, 3], [27, 4], [39, 5]]` (character level, slots) |
| `talent_draft_from` / `talent_draft_every` | 3 / 4 |
| `talent_rarity_odds` | Common 70 − 0.6 CL, Rare 24 + 0.4 CL, Epic 6 + 0.2 CL |
| `talent_swap_on_wake_s` | 60 (the "Reconsider your talents" window after a night's sleep; 0 = only free swaps) |

Data: `data/mettle/mettle/byname/{keywords,skills,pairs}.json` (prefix, word, suffix, noun, pair noun and description),
`summary` lines per set tier and `momentum` in keyword data, `epithet` in Mythic data, sixteen new
`mythic/*.json` (`source: drawn`, `skill`); all text in lang.

Record changes: `CardData` gains `marks` (`tempered`: trait id and the value before, `inscribed`: keyword) and history
lines; the character's card cache gains `focus`, `boons` per skill, `signatures` per skill, `byname` (worn, held list,
tier), `shards`, `forges`, `legend` (the cosmetic title's stars); the journal gains `milestones` and `guide`. Old saves: no marks, no Focus, tier computed on
join from the current build, waiting odd-level rerolls stay rerolls.

---

## 8. Screens for the Figma designer

In the VOID Figma file, page 264:2, a new section **"Build feel (explore)"**; never overwrite earlier frames; mirror
what ships on page 295:2. VOID rules: flat, rounded, no gradients; skill hue = which skill; champagne gold = earned
(bynames, Signatures and forged Mythics use it).

| # | Screen / state | Where | What it needs |
|---|---|---|---|
| B1 | **Character sheet header with byname** | Classes page top strip | Name, "the Deep Ember Warden", tier pips (I–IV) and name, the crest (Signature art), background, level; a "Bynames" list to choose which to wear; the "Your build reads as … · Take it" offer |
| B2 | **Name card** (update of CARDS.md §13.7) | looking at a player, chat hover | Byname under the name, tier pips, crest, up to two Mythics, Trade; the **legend frame** variant (white-gold border, 1–3 stars, "the Legend of …") |
| B3 | **Your build** | Classes page, first tab | §2.3: headline, five lines with their sources, capped-stat note, "Next"; empty state for a new character ("Slot cards to give your build a shape") |
| B4 | **Set tracker with Focus** | Deck (CARDS.md §13.2) | 14 keywords, counts, 2/4/6 pips, active three lit, the Focus marker and control ("changes once a day"), "3 / 4" one-away pulse, the asleep 6 state ("wakes when two of its decks reach 12"), Tempered n/3 and Inscribed n/1 pips per deck |
| B5 | **Hand preview lines** | draft hand (§13.3) | Under each card: where it goes, set deltas (open / break states), you-lines; Focus-lit keyword badges; a long-line overflow state |
| B6 | **Boon choice** | a skill's page and the draft area when a boon waits | Badge with count; three choices (Temper, Inscribe, Reroll greyed at 5); then a card picker (eligible cards lit, why others aren't) |
| B7 | **Temper confirm** | after picking a card | Its traits, the chosen one "8 % → 10 %", band-top note, deck "Tempered 2 / 3", Confirm |
| B8 | **Inscribe confirm** | after picking a card | Pool keywords as badges (illegal ones dim with the reason: "needs Deep on another Mining card"), the set delta preview, "Inscribed 0 / 1", Confirm |
| B9 | **Card marks** | card face, all sizes | ◆ Tempered (the raised value in Temper blue), ✎ Inscribed (the keyword badge with a small quill), Signature (gold corner), history lines |
| B10 | **Milestone toast** | HUD (UI.md §6b style) | Set, first card, Temper/Inscribe, forge-ready variants |
| B11 | **Milestone banner** | HUD, 2.5 s | Byname tier-up and a 6-set: the byname or set name large over a slim name-card plate; skip hint |
| B12 | **Guide hints** | anchored chips | The chip with card-back glyph, an anchor pointer, ×, "Skip the guide" variant, the Focus offer with two buttons (§4 #7) |
| B13 | **Guided first hand** | draft hand | Lit Keep & slot, the hint over the hand, a Rare card's trait called out |
| B14 | **Shards and forge** | Card Shrine screen (CARDS.md §13.5) new tab "Forge" | Shard meter "24 / 30"; the 24 drawn Mythics by skill: unclaimed and forgeable (lit), unclaimed but locked ("needs Smithing 18"), held (holder's name, not selectable); Confirm → the reveal; the empty-pool state offering the legend title and frame |
| B15 | **Deed progress and the pool** | Hall of Firsts (CARDS.md §13.1) | Progress bars per deed, hints, holders and copies; "17 of 24 drawn Mythics still wait"; shards line |
| B17 | **The sixteen new Mythic cards** | Mythic frame (CARDS.md §19.3, M1–M4) | Sixteen sigils / pixel art and the reveal for each |
| B18 | **Talent row** | character sheet, under the attributes | 2–5 talent slots (locked ones: "Opens at character level 27"), the earned talents (the five feats) as gold cards beside them, an empty state, the talent-draft badge |
| B19 | **Talent card face** | all three card sizes | the ash edge (`#A39E96`), rarity gem, family glyph (Body, Motion, Hands, Mind, Fortune) in place of the skill band, the value line, no keyword area; earned (gold) variant; 26 + 5 pixel-art icons |
| B20 | **Talent draft hand** | the draft screen | "Talent draft · character level 7", three talent cards, Keep / Keep & slot, reroll; what it does to you (§6.1: "Carry 84 → 88 kg") |
| B21 | **Reconsider your talents** | on waking in a bed, 60 s | the talent row with binder talents to drag in, Done; the chip that opens it |
| B22 | **Stealth and Speech pages** | Classes page, skill gallery and deck | the two skill cards in violet `#7E7AE6` and honey `#E8C46A`, their trees (roots, Ghost / Knife, Trader / Courtier, Masteries, cursed, feats, synergies, Teachings), 30 perk icons; a "Hearth" tag on perks that read through Hearth, and the fallback line when Hearth is absent |
| B23 | **Effective level chips** | Hub conversation odds chips, haggle panel, Stealth HUD | "Speech 12 (+3)" with a hover listing what adds (Glib II, Persuasive, The Courtier's Manners) |
| B24 | **Deed titles and legend cosmetics** | name card, character sheet, Hall | the deed-title chip ("Long Walker") and a picker of held titles; the Hall's deed rows (holder, "also done by" list with titles); the legend frame (white-gold border, 1–3 stars) and "the Legend of …" title; the migration notice ("Your Long Walker returned: 30 shards and the title") |
| B16 | **Binder filter and preview** | Binder (§13.1) | "Completes a set" filter; the side panel's "If slotted…" preview |

---

## 9. The simulator (`BuildFeelReportTest`)

Test-only: the proposed deal (momentum, Focus), boons (Temper, Inscribe), the byname-free parts of milestones and
shards are written inside the test against the shipped data and `CardRules`, so nothing in the game changes until §10.
Levers can be swept with a `build/bf.properties` file (`bf.perkLean`, `bf.keywordLean`, `bf.focus`, `bf.inscribeCap`,
`bf.inscribeSeed`, `bf.temperStep`, `bf.temperDeckCap`, `bf.momentum` (per keyword), `bf.sixGate`, `bf.shardCost`,
`bf.runs`, `bf.servers`, `bf.sections`). Players: **points**
(today's simulator player: keeps the card that adds most points, tempers), **guided** (as points, accepts the Focus
offer at its first set and inscribes toward it), **committed** (Focus from the start, +1 point for its keyword's cards,
mains chosen as the three skills that carry the keyword best).

### 9.1 Sets (800 careers a row)

| Character · rules · player | first 2 / 4 / 6-set (character level) | any 4-set by mid-game | own Focus: 4-set by mid-game · 6-set by 20 | boons: temper / inscribe / reroll |
|---|---|---|---|---|
| focused · today · points | 4.7 / 11.3 / 18.0 | 100 % | — | 0 / 0 / 30 |
| focused · momentum only (with the gate) · points | 4.7 / 11.3 / 22.7 | 100 % | — | 0 / 0 / 30 |
| focused · proposed · points | 4.7 / 11.3 / 22.7 | 100 % | — | 20 / 0 / 10 |
| focused · today · guided | 4.7 / 11.3 / 18.0 | 100 % | 78 % · 75 % | 0 / 0 / 30 |
| focused · proposed · guided | 4.7 / 11.1 / 22.3 | 100 % | **95 % · 94 %** | 18.2 / 3.7 / 8.1 |
| all-eight · today · points | 4.1 / 8.1 / 17.0 | 100 % | — | 0 / 0 / 56 |
| all-eight · proposed · guided | 4.1 / 8.1 / 29.0 | 100 % | 93 % · 99 % | 28.0 / 4.3 / 23.7 |

The first 6-set comes later than today for everyone (the gate at skill 12: for a focused player the mains reach 12
around character level 22; an all-eight character at 29); every keyword then gets there, which today's rules don't
give.

(This harness plays wildcards and rerolls, which CardReportTest's set table doesn't, so its all-eight "today" reads
8.1 for the first 4-set where CardReportTest reads 9.0.)

### 9.2 Lever sweep (committed players, 6-set by skill 18; 300 careers a keyword, sweep runs, about ±3 %)

The first sweep used the same momentum for every keyword and no gate; the second applies the user's change.

| Variant | Tide | Deep | Kin | Warden | Notes |
|---|---|---|---|---|---|
| today | 6 % | 12 % | 27 % | 54 % | |
| momentum only (lean 1.0 / 0.5, Focus 2, no Inscribe) | 8 % | 24 % | 47 % | 64 % | Tide barely moves: two skills hold it, one native |
| + Inscribe, 1 a deck, no seed rule | 55 % | 66 % | 97 % | 96 % | Kin becomes easy (four pools) |
| **+ Inscribe, 1 a deck, seed rule (chosen)** | **56 %** | **63 %** | **85 %** | **98 %** | Tide stays the rarest |
| + Inscribe, 2 a deck, seed rule | 73 % | 80 % | 94 % | 100 % | 6-sets too common |

With the gate at 12 (mean skill at the first 6-set; common keywords all land at 12.0):

| Keyword momentum m (Tide, Deep, Kin, Warden, Gale/Echo) | Tide | Deep | Kin | Warden | Gale |
|---|---|---|---|---|---|
| 1.5, 1.25, 0.75, 0.5, 0.25 | 13.7 (88 % by 20) | 14.5 | 13.3 | 13.0 | 13.1 |
| **1.0, 1.0, 0.5, 0.5, 0.25 (chosen)** | **14.0–14.2 (73–79 %)** | **14.8** | **13.6** | **13.0** | **13.0** |

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

### 9.4 Mythic shards and the one-of-a-kind server: §5.5 (report §5 and §6).

### 9.5 Talents: §13.3 (report §7: pacing, power with talents, and the ceilings through `Ceilings.potential` on each
simulated character's real decks; lever `bf.talentRuns`).

---

## 10. Build plan

Each wave: Figma frames first (§8), then code, then the client tour with screenshots. Pure rules in `CardRules` /
new pure classes, server in `character.cards`, screens in the Hub from overlay state.

| Wave | Contents | Done when |
|---|---|---|
| **F1 · Momentum and the preview** | `CardRules.draw` / `keywords` take a `Commitment` (keyword → count, capped; Focus) and each keyword's `momentum`; band-rescaled perk lean; leaned second keyword; the 6 tier's skill-12 gate in `CardRules.tier` / `derive`; Focus on the record, once-a-day change, `setFocus`; `BuildPreview` (placement, set deltas, breaks, trait totals) on every offer (`skills[].hand[].preview`) and binder card; set tracker one-away cues | BuildFeelReportTest §1–§3 pass with the code's own `deal` (the test's prototype deleted); a client tour shot of a hand with preview lines |
| **F2 · Boons** | `DraftState` boons per skill (odd levels from `boons_from`; migration keeps waiting rerolls); `Temper` and `Inscribe` (pure rules + server), `marks` on `CardData` with history, slot rules for 3 tempered / 1 inscribed, previews; overlay `boons`, `temperCard(id, trait)`, `inscribeCard(id, keyword)`, `boonReroll(skill)`; chat fallback `/mettle cards temper|inscribe` | tour: a boon each way at Mining 7, 9, 11; caps refused with reasons |
| **F3 · Identity** | `Bynames` (pure, data-driven) and tiers, held list, the "Take it" offer, `CardService.byname`; Signatures; `BuildSummary`; milestone moments (new `Moment` kinds `set`, `byname`, `signature`, `boon`, `forge_ready`) and the banner; name card and chat hover | tour: a fresh character to byname II in a scripted session, shots of B1–B3, B10–B11 |
| **F4 · Guide** | journal `guide`; the ten hints with anchors (overlay `guide.hint`), skip and the options switch; the guided first hand (one Rare, favoured skill) | tour: a new character's first ten minutes, every hint seen once and none for a migrated character |
| **F5 · Mythics within reach** | the sixteen new drawn Mythics (data, effects in `MythicEffects`, ceilings checked by CardReportTest's worst-case search); the drawn roll offering an unclaimed one of the skill; shard sources (events already exist: drafts, levels, feats, synergies, set tiers, claims, boss credit); the forge at the shrine (cost ladder, unclaimed only, skill 18); the legend title and frame; deed progress in the Hall (`cards.mythics` gains `progress`) | tour: each new Mythic granted and checked; shards granted by op, a forge, a refused forge of a held Mythic, the legend title on an emptied pool |
| **F6 · Deeds one of a kind** | `MythicRules` drops copy numbering; deed credit as world firsts; later achievers' 10 shards and titles; titles on the record and name card; the one-time migration (§12) with its log and notice | JUnit below; tour: a second player finishing Long Walker gets shards and the title, a world with two Unclad copies migrates to one |
| **F7 · Talents** | `talent` registry (26 talents as data, `tools/cards/cards_data.py`), the `TL` ledger prefix (bound), `TalentRules` (pure: slots, cadence, rarity, values, one per talent), the talent draft beside skill drafts (`DraftState.talents`), the talent row and slot rules, the wake-up window, earned talents (the five feats re-homed, no change to how they're earned), effects (attribute modifiers and hooks; Fleet, Weathered Hide, Grit and Scavenger feed `Ceilings`) | BuildFeelReportTest §7 with `TalentRules`; tour: drafts at 3 and 7, a swap on waking, Packrat's pocket, ceilings still clamp |
| **F8 · Stealth** | `src/hearthApi`: default `PlayerSkills.bonus` / `title` / `titles` and `StandingEvents` (`SPEECH_CHECK`, the law-check event), as Hearth will ship them; the init check for `bonus` and the fallback switch; the tree, traits and card skill as data; XP sources (sneak proximity, sneak attacks, Warden pass, Hearth's unseen awards); visibility through `LivingEntity#getVisibilityPercent`, vibrations, Vanish, Shadowstep; effective Stealth in `MettlePlayerSkills.level` | PacingReportTest Stealth phase near 600–750 XP/h; tour: a sneak attack, Vanish, Ghost in the dark; with `-Phearth` a witness sees an effective Stealth |
| **F9 · Speech** | the tree, traits, card skill; villager and piglin hooks without Hearth; effective Speech and Presence in `MettlePlayerSkills`; item-kind factors in `MettlePrices`; Appraiser tooltips; feats counted from award reasons; `MettlePlayerSkills.bonus` (the keys of §14, one channel per effect) and `title` (byname, deed or legend title); Golden Mouth on `SPEECH_CHECK`; fallbacks when the running Hearth lacks them | tour with `-Phearth`: Glib moves a haggle's band and a check's odds chip; Provisioner and Appraiser at a market; without Hearth, a villager discount |

### Tests

**JUnit (pure):**
- Momentum: with commitment zero every weight equals today's (the deal is today's deal, same seed → same hand); each
  depth band's total is unchanged for any commitment (100,000 random commitments); no perk below half its old weight;
  the cap at 4; a keyword with `momentum` 0 never leans; Focus counts for momentum and never for `tier`; Hex never
  leans; determinism from seed and commitment.
- The 6 tier: refused below two decks at skill 12, at 4 meanwhile, wakes the moment the second deck's skill reaches 12.
- Second keyword: the chance of one is unchanged by rarity; leaned odds match 1 + 0.5 × c over 100,000 rolls (±1 %).
- Temper: +¼ range rounded to step, never above the band top or the trait max; once per card; the deck cap refuses a
  fourth; fusion and scars keep and drop marks as §3.2 says.
- Inscribe: pool only, never native or Hex, the seed rule, once per card, the deck cap, Commons gain a second keyword.
- Bynames: every keyword × skill × pair produces a name in lang; tiers never drop; kw/skill ordering and ties; the
  example "the Deepdelver" → "the Deep Ember Warden" → "… of the Mountain".
- BuildSummary and BuildPreview: set deltas and breaks, capped-stat notes, numbers equal what `derive` applies.
- Milestones fire once per character and wait for screens and calm (MomentQueue).
- Shards: each source once where it says once; cost ladder; forge refused below skill 18 and for any held Mythic (never
  a copy: every Mythic id exists once in the ledger); the legend title only when no drawn Mythic is unclaimed; the
  drawn roll never offers a held one.
- Guide: new characters only; each hint once; skip turns all off.
- Deeds: one holder per deed; a later achiever gets 10 shards and the title, never a card; migration keeps the earliest
  copy, returns the rest with 30 shards and the title, empties their slots for free, and is idempotent (a second run
  changes nothing).
- Talents: slots and draft levels by character level; rarity odds and value bands over 100,000 rolls (±1 %); one card
  per talent in the deck; bound (trade and listing refused); the wake window opens only after a full night; earned
  talents always on and never slotted; Fleet / Weathered Hide / Grit / Scavenger counted in `Ceilings`.
- Stealth and Speech: every perk, trait and keyword table loads; effective levels never pass 20 and include only the
  listed sources; `MettlePrices` item-kind factors; **one channel per effect**: a table test walks every Speech and
  Stealth perk, trait, Teaching and talent and asserts each reaches Hearth through exactly one of effective level,
  bonus key or price factor (and that a fallback is off whenever `bonus` exists); `bonus` answers 0 for unknown keys,
  `standing/gain` only on gains, `stealth/unseen_beyond` 4 or 0; Golden Mouth counts `SPEECH_CHECK` passes only.

**Simulator:** `BuildFeelReportTest` asserts: power ≤ 1.15× / 1.3× in every skill; deck overlap < 0.25 (any two, and
two chasing one keyword in one skill); no identical decks; every keyword reaches a 6-set when chased. CardReportTest
and DraftReportTest still pass.

**Game tests:** Focus and boons survive death, relog and dimension change; a tempered and an inscribed card traded keep
their marks; a fourth tempered card can't be slotted.

---

## 11. Decisions

**Confirmed by the user (2026-10-20):** boons (Temper, Inscribe, Reroll) with the rules of §3; the byname grammar and
word tables (§2.1); Signatures cosmetic; the guide for new characters with a guaranteed Rare in the first hand; no
loadouts; Temper's small power step (1.11× → 1.12×); momentum scaled by keyword commonness so every committed 6-set
lands around skill 12–16 (§1.2, §3.5); Mythics one of a kind, shards forging only unclaimed ones from a pool of 24, an
empty pool giving a cosmetic legend title and frame (§5).

**Confirmed by the user (2026-10-21, "let's get working on this"):** the skill-12 rule for 6-sets (§1.2b), the keyword
`momentum` values, the sixteen new Mythics (§5.4), the forge rules; and three additions designed in §12–§14: deed
Mythics one of a kind, Talents, and Stealth and Speech as the next released skills.

**Small calls this round made, for the user to see:**

1. Later deed achievers get **10 shards** and the deed title; migrated copies **30 shards** (one forge) and the title.
2. Talents: 2 → 5 slots (character level 3 / 15 / 27 / 39), a talent draft at 3 and every 4 levels (twelve), changed
   freely on waking from a night's sleep, bound to the character (never traded), Wits adds no talent slot.
3. The five general feats become **earned talents** (gold, always on, no slot).
4. Speech and Stealth perks reach Hearth through **effective levels** ("counts N higher wherever Hearth reads it") and
   price factors, or one of Hearth's bonus keys (request 12, accepted by Hearth 2026-10-21) — one channel per effect,
   never two; each key has a fallback until Hearth ships it.
5. Speech's honey colour sits near the earned champagne gold: Figma checks they read apart.

---

## 12. Deed Mythics, one of a kind (approved 2026-10-21)

Every Mythic is now one of a kind, deeds included. **A deed Mythic goes to the first player who does the deed**, like a
world first; numbered copies are gone everywhere (CARDS.md §9.1, §9.3, MythicRules, the Hall, the reveal's "the 3rd in
this world").

- **Later achievers** get **10 shards** (a third of a forge, §5.2) and the deed's **title**, a cosmetic: "Long Walker",
  "the Unclad", "of the Hundredth Dawn", "the Lone Banner", "Polymath". A deed title can be worn instead of the byname or
  beside it on the name card ("Ashford the Deep Ember Warden · Long Walker"); one at a time, chosen on the character
  sheet. The first holder gets the title too.
- **Progress still shows** (§5.1), with the holder: "Long Walker · held by Bree since day 112 · you: 63.2 / 100 km
  (title and 10 shards)".
- **Credit** follows the world-first rules (CARDS.md §9.2): deed counters are server-side; two players finishing in the
  same tick, the earlier event wins.
- Mythics per world: 7 world firsts + 5 deeds + 24 drawn = **36, each once**.

**Migration** (once, when this ships; logged, and told to each player on join):

1. For each deed Mythic with several copies in the ledger, the **earliest** (the lowest copy number; ties by claim day)
   stays: it becomes the one card, its history "the first to do it" kept.
2. Every other copy is **returned**: taken from binder or deck (no fee, no free swap spent, no scar), released with the
   history line "Returned: deed Mythics are one of a kind", and its holder gets **30 shards** (one forge, enough to claim
   an unclaimed drawn Mythic at once if they have a skill at 18) and the deed's title.
3. A returned copy that was one of a player's two slotted Mythics leaves the slot empty. Polymath's extra slot goes with
   it: a card in that slot goes dormant (no cost, CARDS.md §5.2 rule 6).

---

## 13. Talents: the third layer (approved 2026-10-21)

```
Background      → where you start
Attributes (5)  → what your body and mind are
TALENTS (2-5)   → what kind of person you are: general, build-agnostic cards      ← new
Skill decks     → what you have trained: keyworded cards, sets
Character level → all skill XP added together
```

A **talent** is a card with no keyword and no skill: a modest, general gift (more carry, a heart, longer breath, a
little luck). Talents never make sets; their numbers are small and the ones that touch a §14 stat count toward its
ceiling like any card. Each skill keeps its own flavour and its biggest numbers: Athletics' Ox (+25 kg and its special
moves) stays the largest load, a talent's carry tops at +6 kg.

### 13.1 The talent deck and the talent draft

| | Rule |
|---|---|
| **Slots** | 2 from character level 3, 3 at 15, 4 at 27, 5 at 39 (Wits adds none: Wits widens skill decks) |
| **Talent draft** | a hand of three talents, keep one, at character level **3 and every 4 levels after** (3, 7, 11 … 47: twelve), separate from skill drafts; waits like a draft (a badge on the character sheet); rerolls come from the shared pool |
| **Rarity** | rolled per card by character level: Common 70 − 0.6 CL, Rare 24 + 0.4 CL, Epic 6 + 0.2 CL (40 / 44 / 16 % at 50); values roll as traits do (Common the low two thirds, Rare the whole range, Epic the top two thirds) |
| **Duplicates** | dealt at ½ weight per copy owned (as skill cards), one card of a talent per deck; a better copy replaces a worse one |
| **Changing them** | cheap, on purpose: **all talent slots can be rearranged freely when you wake from a night's sleep in a bed** (a "Reconsider your talents" prompt on waking, open 60 s), or any time for a free swap (§7.4); no draught, fee or scar |
| **Bound** | talents are cards in the ledger (prefix `TL`) but never traded or listed: they are you. Three talent cards release for a reroll and three of one talent and rarity fuse into the next, at a Card Shrine (CARDS.md §7.5, §24) |
| **Look** | the card look of direction D with a neutral **ash** edge (`#A39E96`), the rarity in the gem, no keyword badges; a family glyph (Body, Motion, Hands, Mind, Fortune) where the skill band would be |

### 13.2 The twenty-six talents

Values per rarity: Common / Rare / Epic are the bands of the range. "Ceiling" names the §14 stat it counts toward.

| Family | Talent | What it does | Range | Ceiling |
|---|---|---|---|---|
| Body | **Hardy** | +X max health | ½–1½ hearts | |
| Body | **Iron Gut** | You get hungry X slower. | 5–15 % | |
| Body | **Deep Lungs** | You hold your breath X longer. | 3–8 s | |
| Body | **Quick Healer** | Natural regeneration X faster (costs hunger as vanilla). | 10–25 % | |
| Body | **Weathered Hide** | All damage you take is X smaller. | 2–4 % | taken |
| Body | **Grit** | Below 30 % health, damage you take is X smaller. | 3–6 % | taken |
| Body | **Cold Blood** | Freezing and powder snow hurt X less and chill you X slower. | 15–30 % | |
| Body | **Steady Nerves** | Darkness, Blindness and Nausea on you last X less. | 15–30 % | |
| Motion | **Fleet** | +X movement speed. | 2–4 % | movement |
| Motion | **Climber** | You climb ladders, vines and scaffolding X faster. | 10–20 % | |
| Motion | **Sure-Footed** | You fall X more blocks before taking damage. | ½–1½ | (jump ceiling untouched) |
| Motion | **Strong Swimmer** | You swim X faster. | 5–10 % | |
| Motion | **Light Step** (Rare) | Sneaking, you never trigger pressure plates or tripwires. | fixed | |
| Hands | **Broad Shoulders** | +X carry capacity. | 2–6 kg | |
| Hands | **Long Arms** | You reach X blocks further for blocks (not for hits). | 0.25–0.5 | |
| Hands | **Careful Hands** | Tools, weapons and armour wear X slower. | 5–12 % | |
| Hands | **Quick Hands** | You eat and drink X faster. | 10–25 % | |
| Hands | **Packrat** (Epic) | One more belt pocket (BURDEN.md §5). | fixed | |
| Mind | **Quick Learner** | Skill XP +X (all skills, before anti-farm). | 2–5 % | |
| Mind | **Night Owl** | Between dusk and dawn, skill XP +X. | 4–8 % | |
| Mind | **Night Eyes** | The dark is X less dark for you (a client brightness lift in light 0–4). | 10–30 % | |
| Mind | **Light Sleeper** (Rare) | You wake when a hostile comes within 10 blocks of your bed, and phantoms leave you be one night in two. | fixed | |
| Mind | **Homebody** | Within 64 blocks of your bed or respawn point, you regenerate X faster. | 10–20 % | |
| Fortune | **Lucky** | +X Luck (fishing, loot chests). | ½–1½ | |
| Fortune | **Scavenger** | Mobs you kill drop one more of their loot X of the time (plain stacks only). | 2–5 % | extra drop |
| Fortune | **Silver Tongue** | Your market edge at Hearth +X (Mettle's price hook, `MettlePrices`); without Hearth, villagers' prices X better. | 1–3 % | |

**Earned talents.** The five general feats (TREES.md §12) move into this layer as **earned talents**: Pack Horse (+8 kg),
Seasoned (+1 heart), Seen It All (+1 attribute point), Jack of All Trades (+25 % XP below 10), Nine Lives (+20 % speed
below 3 hearts). They show in the talent row as gold cards beside the slots and are always on (earned perks never take a
slot, CARDS.md §18.4); new general feats join them there. Nine Lives' speed counts toward the movement ceiling as it does
today.

### 13.3 Power and pacing (simulator, report §7; 400 careers a row)

| Character | character level | skill decks | three sets | talents (slots) | talents' share of the whole |
|---|---|---|---|---|---|
| focused (mains to 20), keeps for points | 38 | 160.1 | 14.9 | 3.4 (4) | **1.9 %** |
| focused, chases ceiling stats | 38 | 160.1 | 14.9 | 2.8 (4) | 1.6 % |
| all-eight (maxed) | 50 | 249.6 | 15.0 | 4.3 (5) | **1.6 %** |

Average power with talents: a whole character is **1.6–1.9 % stronger** than its decks and sets alone (in skill terms
the per-skill ratio stays 1.12× / 1.23×; talents are not in it).

**§14 ceilings** (each simulated character's real decks through the game's own `Ceilings.potential`; mean potential and
the share already at the ceiling, without → with talents):

| Character · player | movement (+35 %) | damage taken (−45 %) | extra drops (+50 %) |
|---|---|---|---|
| focused · points | +26.2 → +26.5 % · 30 → 30 % | −44.0 → −44.5 % · 61 → 62 % | +36.0 → +36.4 % · 19 → 20 % |
| focused · ceiling chaser | +26.2 → +27.8 % · 30 → 32 % | −44.0 → −46.4 % · 61 → 64 % | +36.0 → +37.9 % · 19 → 24 % |
| all-eight · points | +37.1 → +37.4 % · 55 → 56 % | −51.3 → −51.8 % · 79 → 80 % | +43.4 → +43.8 % · 35 → 36 % |
| all-eight · ceiling chaser | +37.1 → +39.1 % · 55 → 57 % | −51.3 → −53.7 % · 79 → 83 % | +43.4 → +45.6 % · 35 → 40 % |

("Potential" is the worst moment the ceilings imagine: every condition at once, the best damage kind.) Talents alone add
at most movement +4 %, damage taken −9.8 % (Weathered Hide and Grit at their Epic tops, Grit only below 30 % health),
extra drops +5 %. `Ceilings` clamps the sum exactly as it does today, so no deck passes a ceiling in any state.

**Pacing** (a focused character, about 3,070 character XP an hour: PacingReportTest's per-skill rates over three mains,
two sides and three dabbled):

| Talent event | character level | the mains' skill then | about hours in |
|---|---|---|---|
| first talent draft, 2 slots | 3 | 2 | 0.05 (in the first ten minutes) |
| drafts | 7 · 11 | 5 · 7 | 0.5 · 1.7 |
| 3rd slot (and a draft) | 15 | 9 | 3.6 |
| draft | 19 · 23 | 11 · 13 | 6.5 · 10 |
| 4th slot (and a draft) | 27 | 15 | 15 |
| 5th slot (and a draft) | 39 | past 20 (the sides grow) | 36 |
| last draft | 47 | — | 56 |

The talent draft lands where the skill cadence thins (after skill 6, drafts come every other level): at mains 7, 9,
11, 13, 15, 17 a talent draft or a slot arrives between skill drafts, so the mid-game always has a next thing.

---

## 14. Stealth and Speech, the next released skills (approved 2026-10-21)

Both exist as unreleased skills (`skill/stealth.json` Agility, colour `#7E7AE6`; `skill/speech.json` Presence, colour
`#E8C46A`) whose XP has banked since Hearth began reporting it. Releasing them: their trees below as data, card skills
(prefix `SL` and `SP`), trait pools, keywords, trainers (Hearth's fence trains Stealth; merchant and trader train
Speech: `HearthRules.TRAINERS`, already there), and Teachings from a city's fence and merchant (`TEACHERS`). The
**split holds**: Mettle owns the numbers (levels, perks, traits, effective levels and price factors); Hearth owns the
minigames, witnesses, odds rules and reactions. Backgrounds already point at them: the **Rogue** favours Stealth (and
Thievery, still unreleased: its XP keeps banking), the **Merchant** favours Speech (and Riding); a Wanderer may now pick
either.

**How Mettle reaches Hearth, using only existing slots** (the Hearth api in `src/hearthApi`):

- **Effective levels**: `PlayerSkills.level(player, "mettle:speech" | "mettle:stealth")` answers the skill level **plus**
  the perks, traits and Teachings that say "counts N higher wherever Hearth reads it", at most 20; `attribute(player,
  "presence")` likewise. Every Hearth reading (speech checks, haggling room and patience, the haggle band from Speech 5
  to 15, witnesses, the pickpocket mark, bandits) gets them for free.
- **Prices**: `PriceModifier` via `MettlePrices` (per item kind, so "food is cheaper", "gear sells dearer" work).
- **Values**: `EconomyService.price(player, settlement, stack, playerBuys)` for the Appraiser's tooltips.
- **XP and feats**: Hearth's `award` reasons (`hearth:speech/<kind>`, `hearth:economy/haggle`, `hearth:law/unseen/<offence>`)
  are counted for feats.
- **Training**: inline, paid in conversation in the 300–399 band (built), as the Hub's conversation shows it.

Effects that need one number Hearth doesn't read yet are asked for in
[hearth/docs/requests/mettle-speech-stealth.md](../../hearth/docs/requests/mettle-speech-stealth.md) (requests 12–14).
**Hearth reviewed them (2026-10-21): all three fit, and Hearth builds them after its living-world batch.** As agreed:

- **12** is an additive **default** method `double bonus(ServerPlayer player, String key)` on `PlayerSkills` (0 by
  default). Hearth reads these keys and keeps every clamp of its own (odds 5–95 %, band and patience caps):
  `hearth:haggle/band` (Speech levels for reading the limit band), `hearth:haggle/patience` (a share, the player passed
  in), `hearth:haggle/never_sour` (1 = walking away never sours), `hearth:speech/<kind>` for each SpeechCheck kind
  (`persuade`, `intimidate`, `bribe`, `flatter`: odds added before the clamp), `hearth:speech/retry` (failed checks
  that may be tried again at once, per sim day), `hearth:standing/gain` (a share on positive reputation deltas only),
  `hearth:stealth/sight` (a share off how far witnesses and `Detection` see a sneaking player), `hearth:stealth/give_up`
  (a share off a guard's search timer), `hearth:stealth/unseen_beyond` (blocks beyond which a sneaking player is not
  seen at all; 0 = off), `hearth:pickpocket/awareness` (a share off how fast the mark notices), and
  `hearth:greet/hero` (1 = folk greet the player as a hero; flavour only, standing untouched).
- **13** is an api event, **`StandingEvents.SPEECH_CHECK (player, settlement, kind, passed, difficulty)`**, and the same
  for the law's checks; award reasons stay exactly as they are. Mettle listens to it for Golden Mouth.
- **14** is an additive default `Component title(ServerPlayer player)` on `PlayerSkills`, beside request 10's
  `titles(player)` (still open on Hearth's side).

**Mettle's `src/hearthApi` copy** gains the same three additions, so `compat.hearth` compiles against them: the default
methods `bonus` and `title` (and request 10's `titles`) on `PlayerSkills`, and `StandingEvents` with `SPEECH_CHECK` and
the law-check event. `MettlePlayerSkills` overrides `bonus` and `title`. Against a Hearth that predates them, the
defaults are never called and the event never fires: Mettle checks once at init whether the running Hearth's
`PlayerSkills` declares `bonus` (reflection on the loaded interface) and, if not, turns on each perk's **fallback**
below instead.

**One channel per effect (rule a, no double counting).** Every Speech and Stealth perk, trait, Teaching and talent
reaches Hearth through exactly one channel: the **effective level** (`level()` / `attribute()`), a **bonus key**, or a
**price factor**. None does two of them for the same thing; a fallback replaces the key's effect only while Hearth has
no `bonus`, never on top of it. The audit:

| Channel | Speech | Stealth |
|---|---|---|
| effective level / attribute (`level`, `attribute("presence")`) | Glib, Persuasive, Commanding Voice (Presence), Voice of the Realm, Honeyed Lies, Demagogue (Presence), The Courtier's Manners (Presence) | Shadow (its Hearth half) |
| bonus key | Shrewd Eye, Shrewd (`haggle/band`); Patient (`haggle/patience`); Merchant Prince's never-sour (`haggle/never_sour`); Smooth (`speech/persuade`, `speech/flatter`); Imposing (`speech/intimidate`); Second Word (`speech/retry`); Charmer, Charming (`standing/gain`); Hero's Welcome (`greet/hero`) | Cloak of Dusk, Unseen, The Quiet Way (`stealth/sight`); The Quiet Way (`stealth/give_up`); Ghost (`stealth/unseen_beyond`); Light-fingered (`pickpocket/awareness`) |
| price factor (`MettlePrices`) | Haggler, Haggling, Provisioner, Provisioning, Appraising, Merchant Prince's edge, Honeyed Lies' cost, Tavern-Keeper; talent Silver Tongue | — |
| Mettle only (vanilla mobs, villagers, XP, weight) | Appraiser (reads `EconomyService.price`), Ledger-Keeper, Barterer, Bartering, Gregarious, Rallying, Heroic, Coinwise, Caravan Master | everything else (sneak speed, mob sight, vibrations, sneak attacks, Vanish, Shadowstep, Assassin) |

Fallbacks (only without `bonus`): Cloak of Dusk and Unseen → Stealth counts 2 / 1 higher at night; Ghost → Stealth
counts 5 higher; The Quiet Way → Stealth counts 2 higher; Shrewd Eye → market edge +2 %; Charmer → Presence counts 1
higher; Second Word → Speech counts 1 higher; Merchant Prince's never-sour → +2 % more edge; Light-fingered → animals
notice you closer; Smooth, Imposing, Patient, Shrewd, Charming → not rolled at all (`requires: hearth_bonus`).

### 14.1 Stealth (Agility) — "unseen, unheard, first to strike"

**XP**

| Source | XP |
|---|---|
| Sneaking within 12 blocks of a hostile that isn't targeting you | 1 every 4 s (at most 15 a minute); none from spawner mobs, caged, boated or leashed ones, or one that can't path to you |
| A sneak attack: damage dealt to a mob not targeting you | 1 a point (capped at its health), kill bonus ¼ of its max health (the weapon's skill gets its own XP too) |
| Sneaking within 16 blocks of a Warden without angering it | 2 a second (at most 60 a minute) |
| A crime nobody saw (Hearth) | Hearth's 5–25 (`hearth:law/unseen/<offence>`) |

Anti-farm as CHARACTER.md §3.3 (the same source tapers after about 60 in 5 minutes). Target: about 600–750 XP an hour
for a player who hunts by night; PacingReportTest gets a Stealth phase when it is built.

**Roots**

| Perk | Ranks |
|---|---|
| **Soft Step** | You sneak 10 / 20 / 30 % faster. |
| **Shadow** | While you sneak, hostiles notice you at 90 / 80 / 70 % of their range; your Stealth counts 1 / 2 / 3 higher with Hearth folk. |

**Path A · Ghost** (never seen)

| Tier | Perk | What it does |
|---|---|---|
| II | **Quiet Feet** | Your sneaking steps and landings make no vibration for sculk sensors and shriekers. |
| II | **Cloak of Dusk** | In light 7 or less, hostiles notice you at 75 % of their range, and Hearth's witnesses see you 25 % nearer (`stealth/sight`). |
| III | **Vanish** | Sneak for 2 s out of a hostile's sight: it loses you at once (30 s cooldown). |
| III | **Silent Fall** | Falls while sneaking make no vibration and land 2 blocks softer. |

**Path B · Knife in the Dark** (strike first)

| Tier | Perk | What it does |
|---|---|---|
| II | **Ambusher** | Sneak attacks on a mob that isn't targeting you deal 30 % more. |
| II | **Hamstring** | A sneak attack slows its target by half for 3 s. |
| III | **Unseen Blade** | A kill no mob saw alerts nothing within 8 blocks. |
| III | **Poisoner** | Sneak attacks with a blade poison the target for 3 s. |

**Masteries**

| Mastery | Needs | What it does |
|---|---|---|
| **Ghost** | 2 Ghost perks | Sneaking in light 7 or less and not attacking, hostiles beyond 4 blocks can't see you at all, and neither can Hearth's witnesses (`hearth:stealth/unseen_beyond` = 4 while those hold, else 0). |
| **Shadowstep** | any 2 path perks | Sneak-jump at a mob within 8 blocks that isn't targeting you to step behind it (12 s cooldown; never at a player; walls stop it). |
| **Assassin** | 2 Knife perks | Your first sneak attack on a mob that hasn't seen you deals triple damage (a boss: half again). |

**Cursed**

| Card | Tier | Gives | Costs |
|---|---|---|---|
| **Skulker** | II | You sneak 40 % faster. | In light 12 or more, hostiles notice you from 25 % further. |
| **Coward's Edge** | III | Sneak attacks deal 50 % more. | While not sneaking, you take 15 % more damage. |

**Feats**

| Feat | Hint | Earned by | Grants |
|---|---|---|---|
| **Never Seen** | "Nobody remembers your face." | 20 crimes in Hearth settlements that nobody saw (without Hearth: 50 kills of mobs that never noticed you) | Mobs that lose you forget you twice as fast. |
| **Under Its Nose** | "It listened. It did not hear." | Pass within 8 blocks of a Warden without it noticing you | Darkness lasts half as long on you. |

**Synergies**: **Night Hunter** (Stealth 10 + Marksmanship 10): arrows you loose while sneaking at a mob that isn't
targeting you always crit. **Footpad** (Stealth 10 + Athletics 10): climbing and swimming make no sound and keep
sneaking's cover.

**Teaching**: **The Quiet Way** (a city's fence, or *The Nightwalker's Book* in an Expanse ruin): Hearth's witnesses see
you 10 % nearer (`stealth/sight`) and a guard who loses sight of you gives up a third sooner (`stealth/give_up`).

**Traits** (X rolled; Attr as CARDS.md §4.2; bonus keys as above, the fallback without `bonus` in brackets)

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Shadowed** | While sneaking, hostiles notice you X closer. | 5–12 % | Agi |
| **Hushed** | Your vibrations reach sculk X less far. | 15–25 % | Agi |
| **Nightstalker** | In light 7 or less you sneak X faster. | 10–25 % | Agi |
| **Backbiting** | Sneak attacks deal X more. | 8–20 % | Str |
| **Hamstringing** | Sneak attacks slow the target X for 2 s. | 15–25 % | Agi |
| **Vanishing** | A mob that loses sight of you gives up X sooner. | 15–25 % | Agi |
| **Catfooted** | Falls while sneaking hurt X less. | 10–25 % | Agi |
| **Muffled** | A kill from stealth alerts mobs nearby X less often. | 10–25 % | Agi |
| **Patient** | After sneaking still for 2 s, your next hit deals X more. | 6–15 % | Agi |
| **Lurking** | Darkness and Blindness on you last X less. | 15–25 % | Vit |
| **Swift Exit** | After a sneak attack, +X speed for 2 s. | 4–10 % | Agi (movement ceiling) |
| **Light-fingered** | The pickpocket mark notices you X slower (`pickpocket/awareness`) [animals notice you X closer]. | 5–12 % | Pre |
| **Unseen** | Hearth's witnesses see you X nearer (`stealth/sight`) [Stealth counts 1 higher at night]. | 5–12 % | Agi |
| **Lootfinger** | Mobs you kill unseen drop one more of their loot X of the time. | 3–8 % | Pre (extra-drop ceiling) |

**Keywords**: natives Soft Step Swift · Shadow Deep · Quiet Feet Gale · Cloak of Dusk Deep · Vanish Echo · Silent Fall
Gale · Ambusher Keen · Hamstring Keen · Unseen Blade Echo · Poisoner Wild · Ghost Deep · Shadowstep Swift · Assassin
Keen · Skulker, Coward's Edge Hex. Second-keyword pool: **Deep, Keen, Swift, Wild, Echo**. Stealth adds Deep cards
(3 natives, a pool): when it ships, re-run the simulator and lower Deep's `momentum` if its 6-set lands before skill 13.

### 14.2 Speech (Presence) — "the right word, the right price"

**XP**: Hearth's reports as today (a speech check 15–45 by difficulty, a third on failure; a struck haggle 5–40;
business 5 a crown, 1 past 20 crowns a sim day; a personal quest 80; a law check 20 or 7). Without Hearth Speech still
rises, slowly: a villager trade 3 + 1 per emerald (tapers like any source), a zombie villager cured 60, Hero of the
Village 100, a piglin barter 1.

**Roots**

| Perk | Ranks |
|---|---|
| **Haggler** | Your market edge at Hearth +2 / 4 / 6 % (`MettlePrices`); villagers' trades of 4+ emeralds cost 1 / 2 / 3 less. |
| **Glib** | Your Speech counts 1 / 2 / 3 higher wherever Hearth reads it (checks, haggling room, patience and the limit band); without Hearth, villagers' gossip about you grows 25 / 50 / 75 % faster. |

**Path A · Trader** (coin)

| Tier | Perk | What it does |
|---|---|---|
| II | **Provisioner** | Food, drink and potions: you buy 10 % cheaper and sell 10 % dearer. |
| II | **Appraiser** | Item tooltips show what this settlement's market pays and asks (`EconomyService.price`); without Hearth, what the nearest villager pays. |
| III | **Ledger-Keeper** | Speech XP from business never tapers, and coins you carry weigh nothing. |
| III | **Shrewd Eye** | You read a merchant's limit band as if your Speech were 5 higher (`haggle/band`). |

**Path B · Courtier** (people)

| Tier | Perk | What it does |
|---|---|---|
| II | **Charmer** | Reputation you gain with Hearth folk +25 % (`standing/gain`, gains only). |
| II | **Barterer** | Piglins give one more item a quarter of the time; wandering traders restock once more. |
| III | **Commanding Voice** | Your Presence counts 2 higher wherever Hearth reads it; allies within 8 blocks gain 5 % more skill XP. |
| III | **Second Word** | Once a sim day, a failed Hearth speech check may be tried again at once (`speech/retry` = 1). |

**Masteries**

| Mastery | Needs | What it does |
|---|---|---|
| **Merchant Prince** | 2 Trader perks | Your market edge +5 % (price), and a merchant you walked away from is never sour with you (`haggle/never_sour`). |
| **Hero's Welcome** | any 2 path perks | Hero of the Village after a raid you helped win lasts 3 in-game days; Hearth folk greet you as a hero (`hearth:greet/hero` = 1: a greeting only; Hearth's per-town HERO reputation title and standing are untouched). |
| **Voice of the Realm** | 2 Courtier perks | Your Speech counts 4 higher and your Presence 2 higher wherever Hearth reads them. |

**Cursed**

| Card | Tier | Gives | Costs |
|---|---|---|---|
| **Honeyed Lies** | II | Your Speech counts 4 higher wherever Hearth reads it. | Your market edge is 5 % worse. |
| **Demagogue** | III | Your Presence counts 4 higher wherever Hearth reads it. | Skill XP from everything but Speech −20 %. |

**Feats**

| Feat | Hint | Earned by | Grants |
|---|---|---|---|
| **Golden Mouth** | "They came to argue and left agreeing." | 50 Hearth speech checks won (`StandingEvents.SPEECH_CHECK` and the law's check event, `passed`; before Hearth has them, 50 checks tried, counted from award reasons) · without Hearth: 5 zombie villagers cured | Failed speech checks give full XP. |
| **Market Regular** | "Every stall knows your name." | 100 struck haggles (`hearth:economy/haggle`) · without Hearth: 200 villager trades | Speech XP from haggles +25 %. |

**Synergies**: **Caravan Master** (Speech 10 + Athletics 10): you and allies within 8 blocks carry 10 % more on paths
and roads. **Tavern-Keeper** (Speech 10 + Survival 10): food you cooked sells 20 % dearer at Hearth markets (cooked food
gains a "cooked by" stamp like Smithing's "Forged by").

**Teaching**: **The Courtier's Manners** (a city's merchant, or *A Treatise on Courtesy* in an Expanse manor): your
Presence counts 2 higher wherever Hearth reads it.

**Traits**

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Haggling** | Market edge +X (Hearth); villagers' prices X better without. | 1–3 % | Pre |
| **Persuasive** | Your Speech counts X higher wherever Hearth reads it. | 1–2 levels | Pre (whole levels, not boosted) |
| **Appraising** | Tools, weapons and armour sell X dearer. | 3–8 % | Pre |
| **Provisioning** | Food and drink cost X less. | 3–8 % | Pre |
| **Charming** | Reputation gains +X (`standing/gain`, gains only) [not rolled]. | 5–12 % | Pre |
| **Imposing** | Intimidate odds +X (`speech/intimidate`) [not rolled]. | 3–8 % | Str |
| **Smooth** | Persuade and flatter odds +X (`speech/persuade`, `speech/flatter`) [not rolled]. | 3–8 % | Pre |
| **Patient** | A merchant's patience +X (`haggle/patience`) [not rolled]. | 5–15 % | Vit |
| **Shrewd** | You read the haggle band as if your Speech were X higher (`haggle/band`) [not rolled]. | 1–3 levels | Pre (whole levels, not boosted) |
| **Bartering** | Piglins give one more item X of the time. | 5–12 % | Pre |
| **Gregarious** | Speech XP +X. | 5–12 % | Pre |
| **Rallying** | Allies within 8 blocks gain X more skill XP. | 2–5 % | Pre |
| **Heroic** | Hero of the Village lasts X longer. | 25–50 % | Pre |
| **Coinwise** | Coins you carry weigh X less. | 20–40 % | Str |

Traits whose only effect is a bonus key roll only when the running Hearth has `bonus` (the pool reads `requires: hearth_bonus`),
so no card carries a dead trait.

**Keywords**: natives Haggler Hoard · Glib Kin · Provisioner Ember · Appraiser Keen · Ledger-Keeper Hoard · Shrewd Eye
Keen · Charmer Kin · Barterer Hoard · Commanding Voice Kin · Second Word Echo · Merchant Prince Hoard · Hero's Welcome
Warden · Voice of the Realm Kin · Honeyed Lies, Demagogue Hex. Second-keyword pool: **Kin, Hoard, Keen, Warden, Ember**.
Speech is Kin's home (4 natives, a pool): when it ships, re-run the simulator and lower Kin's `momentum` (0.5 → 0.25 or 0)
so its 6-set stays in the 12–16 band.

**Card colours**: Stealth violet `#7E7AE6`, Speech honey `#E8C46A` (both already in the skill data). Speech's honey sits
near champagne gold (`#E8DDBC`, earned); the Figma pass should check they read apart (§8).

---

## 15. Built (first wave, Mettle 0.3.0, 2026-10-21)

Built on branch `mettle-build-feel-code` (from this design at 25e0b0ad) and merged into `mettle`: everything of §1–§6
except what §15.3 lists. Talents, Stealth and Speech, and first-achiever-only deed Mythics are later waves.

### 15.1 Where it lives

| Part | Code |
|---|---|
| Momentum, Focus, the 6 tier's gate | `CardRules.Commitment` / `perkLean` / `keywordLean` / `weights` (band-rescaled) / `tier(count, decks, levels)` / `sixAsleep`; `Keyword.momentum` (data); hands dealt with the commitment in `Drafts` (settle, reroll, top-up); `BuildFeel.focus` |
| Boons | `DraftState.boons` (odd levels from `boons_from` via `CardRules.levelled`; an old save has none, its rerolls stay); `CardRules.tempered` / `canTemper` / `temper` / `canInscribe` / `inscribe`; `CardData.Marks` (Tempered, Inscribed, Signature lines, in the ledger, kept through trades and scars); the slot rules `TEMPERED_FULL` / `INSCRIBED_FULL` in `CardRules.canSlot`; `BuildFeel.temper` / `inscribe` / `boonReroll` (ledger, then `LedgerFile.commit`) |
| Bynames, Signatures | `Bynames` (pure: tier now / earned, keyword and skill order, the four forms), `BynameText` and the synced `mettle:byname` registry (`data/mettle/mettle/byname/{keywords,skills,pairs}.json`, words in lang); `Mythic.epithet`; `BuildState.Bynames` (worn, earned tier, held); the public `mettle:character/renown` (byname, legend, crest) for name cards; `CardService.byname` (api, default empty) |
| "Your build", the preview | `BuildSummary`, `BuildPreview` (pure, from `CardRules.derive`); `SetBonus.summary` lines (data) |
| Milestones | `BuildFeel.derived` (called from `Cards.derive` when the deck's derived state changes), once each in `BuildState.once`; `MomentKind` CARD, SET, BYNAME, SIGNATURE, BOON, FORGE_READY; `Moment` carries the server's words; `MilestoneSounds` reuses the card cues; `MomentQueue.advance(ms, calm)` holds milestones while a screen is open or for 5 s after harm |
| Shards and the forge | `ForgeRules` (pure: ladder, `canForge`, `canLegend`, `ready`, the roll's `offered`); `BuildFeel.addShards` / `forge` / `bossKilled`; `Mythics.drawnAll` / `pool` / `grant(…, How.FORGED)`; the drawn roll offers one of the skill's unclaimed three, or pays 3 shards when all are held |
| The sixteen Mythics | `character.cards.legend` (`LegendEffects` and helpers), `mixin.legend`, `client.legend`, `LegendJourney` |
| The guide | `GuideRules` (pure state machine), `BuildFeel.guideTick` (once a second), `BuildState.Guide`; the guided first hand in `Drafts.settle` (`BuildFeel.guidedFirstHand`) |
| Overlay | `client.overlay.MettleBuild` (`cards.build`, set / deck / card additions, hand `preview`), `MettleBoons` (previews on demand), `MettleActions` (OVERLAY.md "Build feel") |
| Chat | `/mettle build` (show, `focus`, `temper`, `inscribe`, `boon reroll`, `signature`, `byname [n]`, `forge`, `guide skip`; ops: `shards`, `boons`, `guide on/off`) |
| Config | `config/mettle/cards.json` §7's fields (`momentum_*`, `six_tier_skill`, `focus_*`, `boons_from`, `temper_*`, `inscribe_*`, `shard_forge_costs`, `forge_skill`, `shard_*`, `legend_stars_max`, `guide`, `first_hand_rare`) |

Persistence: build feel's record is `CardsState.build` (`BuildState`, saved with the player, kept on death, synced to the
owner); marks are on the card in the world ledger; every codec clamps or skips instead of failing; a Temper, an
Inscription, a Signature and a forge are committed (ledger, then the player's file) at once. The client never deals:
previews read cards already dealt and owned. The protocol is 3 (`MettleHandshake`), the version 0.3.0.

### 15.2 Checked

`BuildFeelRulesTest` (21: momentum is today's deal with no commitment, band totals kept and no perk below half over
100,000 random commitments, the cap, Hex and momentum-0 keywords never lean, Focus never counts for sets, determinism,
the second keyword's unchanged chance and its leaned odds over 100,000 rolls, the gate, Temper (¼ step, band top, grid,
once, three a deck, scars, fusion), Inscribe (pool, native, Hex, seed, once, one a deck, Commons, cursed cards), boons
and old saves, every byname word, the Deepdelver → … of the Mountain, keyword order, the preview against `derive`, the
summary, the forge ladder and one of a kind, two players forging the last Mythic, once-only sources, the guide, records
that never fail and the ledger's writer), `MomentQueueTest` (milestones wait for calm, never merge), `MythicRulesTest`
(thirty-six, three drawn a skill), `CardAuditTest` (every Mythic wired). The simulator now plays the game's own deal,
gate and Temper (`BuildFeelReportTest`, the prototype removed). Client tour: `BuildFeelJourney` (the guide for a new
character, none for a migrated one, the guided Rare, Skip; the Focus and its daily change; boons at Mining 7, 9, 11 each
way with previews, marks and refusals; first card, first set and byname moments, once; forge-ready, a forge, a held
Mythic refused, the legend title on an emptied pool) and `LegendJourney` (the sixteen).

### 15.3 Where it differs, and why

1. **Milestones and the guide live in the card cache's build record**, not the unsynced journal: the guide's hint and
   the Forge tab must reach the client.
2. **The hand preview is worked out on the client** from the synced cards with the same pure rules (no round trip; it
   only reads what is already dealt and owned).
3. **Not in this wave:** deed progress in the Hall (§5.1), the binder's "Completes a set" filter (§6.2: the data is
   there, `preview`), chat-name hover (the renown attachment carries what it needs), loadouts (§6.3, not chosen).
4. **Shards:** "a draft from skill 15" pays on each draft kept from a skill at 15 or more; boss shards go to everyone with
   at least 25 % of the fight's damage, once per kind, after its world first is claimed.
5. **The guided first hand** counts an Epic as "at least a Rare"; otherwise its first Common is rolled again at Rare
   from the secret seed. Hint 3 shows only after hint 2's six seconds while the hand is still open.
6. **A Mythic counts 6 points** in byname skill points and previews (CARDS.md §14's size).
7. **The sixteen Mythics' close readings** (LegendJourney checks each): Deep Lantern also blocks spawners, trial
   spawners and zombie reinforcements near a holder below y 0 (natural spawns never come within 24 blocks anyway);
   Misericorde counts the target's health before the hit; Thunderhead needs open sky over the target, spares the
   caller and allies, and lights no fire where the caller may not build; Dread Maul's cooldown starts only when
   something fled; Hunter's Moon's night is clock time 12542–23460 under open sky, and its second arrow is half the first
   arrow's own damage; the Pinning Star roots any mob, bosses too (the text doesn't exclude them); the Iron Vow takes
   killing blows from an attacker (not falls or lava); the Long Watch's glow is drawn only on its players' clients;
   the Ferryman's Stride stands on still source water; the Cat's Way holds height from the top of a sprint-jump beside a
   wall; the Greenway's leaves let you walk through natural leaves from the side (leaves never slow in vanilla); the
   Ever-Ember halves cook time while its owner is online with the card (each item then takes half the fuel); the
   Mender's Hand repairs a plain repair (material or a plain copy) fully once a day, prior work unchanged.

---

## 16. Built (second wave, Mettle 0.4.0, 2026-10-21): deeds one of a kind, Talents

Built on branch `mettle-talents` (from `mettle` 0.3.0 with this design merged in): F6 and F7 of §10. Stealth (F8) is
§17; Speech (F9) waits for Hearth's requests 12–14.

### 16.1 Where it lives

| Part | Code |
|---|---|
| Deeds one of a kind | `DeedRules` (pure: who gets what, the migration's plan: earliest copy by copy number, then day, then card id); `Deeds` (the deed done: card for the first, `deed_later_shards` 10 and the title for later ones; `wear`; the start-up migration and the join-time settling); `Mythics.grant` claims a deed in the Hall like a world first; `CardLedger.deeds` stays the list of who did each deed (once a player), `CardLedger.returns` the migration's permanent log |
| Deed titles | `BuildState.Titles` (held, worn, instead of the byname or beside it), `Renown.title` / `titleInstead` (public, name cards), `BuildActionKind.TITLE`, `/mettle build title`; words in lang `mettle.title.<deed>` |
| Talent data | `api.Talent` and the synced registry `mettle:talent` (`data/mettle/mettle/talent/*.json`, generated by `tools/cards/talents_data.py`); stand-in icons `textures/gui/talent/*.png` (copies of the nearest perk icon) |
| Talent rules | `TalentRules` (pure: slots, cadence, odds, bands rolled as traits roll, the hand, the row's slot rules and upgrades, rearranging, the waking window, the row's values) |
| Talent cards | cards in the world ledger with skill `mettle:talent` and perk key `mettle:talent/<id>`, one roll (the trait `mettle:talent/<id>`), print prefix `TL`; cached apart in `CardsState.talent` (`TalentState`: cards, `wakeUntil`), so no skill-card path ever sees one; bound (`TradeRules.BOUND`), released three for a reroll and fused three into the next rarity at a Card Shrine (`Talents.fuse`) |
| The talent draft | `DraftState.talent` (`TalentDraft`: queued, hand, `upTo` = the character level paid, `dealt`), queued by `Talents.refresh` on level-ups and joins (an old character gets every draft it passed, once), dealt from the player's secret seed; keep and reroll (`BuildActionKind.TALENT_KEEP` / `TALENT_REROLL`) |
| Effects | the row's values merge into `CardsState.traits` in `Cards.derive` (never boosted by attributes); attribute talents through `CardEffects` (`mettle:trait/talent/<id>`, so Fleet is a card modifier under the movement ceiling); behaviours in `CardHooks` beside their traits (Iron Gut, Deep Lungs, Quick Healer, Homebody, Weathered Hide, Grit, Cold Blood, Steady Nerves, Climber, Careful Hands, Quick Learner, Night Owl, Scavenger) and `TalentHooks` + `mixin.talents` (Light Step, Quick Hands, Light Sleeper's phantoms; Night Eyes in the client lightmap); Light Sleeper's waking and Cold Blood's chill in `Talents` once a second; Packrat in `GearInventory.pocketCount` (and `spillHidden`); Silver Tongue in `MettlePrices` (§14's one-channel rule) |
| Ceilings | `Ceilings`: Fleet in `MOVEMENT_TRAITS`, Weathered Hide and Grit in `takenPotential` (every hit; Grit at the worst moment), Cold Blood with Weathered's kind, Scavenger in `dropPotential` |
| Overlay | `MettleTalents` (`talents`), `MettleBuild` (`cards.build.titles`), `MettleCards` (Hall `doneBy`, `title`; players' `title`), `MettleActions` (`wearTitle`, `keepTalent`, `rerollTalents`, `slotTalent`, `unslotTalent`); OVERLAY.md "Deed titles and Talents" |
| Chat | `/mettle talents` (`keep`, `reroll`, `slot`, `unslot`; ops `give`, `drafts`, `wake`), `/mettle build title` |
| Config | `deed_later_shards`, `deed_migration_shards`, `talent_slots`, `talent_draft_from`, `talent_draft_every`, `talent_rarity_odds`, `talent_swap_on_wake_s` (§7) |

### 16.2 Checked

`DeedRulesTest` (6: outcomes, the migration keeps the earliest copy by copy number and returns the rest including a
slotted one, ties by day then id, idempotent and blind to released cards, the ledger's deed list once a player and the
return log through a save, titles held once and worn), `TalentRulesTest` (15: 26 talents in five families with the three
fixed ones and four ceiling ones, slots 2 / 3 / 4 / 5 at 3 / 15 / 27 / 39, drafts at 3 and every 4 (twelve), drafts
between levels never paid twice, rarity odds over 100,000 rolls ±1 %, values in their bands on the step, band means,
the hand of three different talents from the seed, owned talents at half weight, one card per talent and the better
copy's upgrade, the row's refusals, bound, the row's values within its slots, rearranging, the wake window only after a
full night, the ceilings counting Fleet / Weathered Hide / Grit / Scavenger). The simulator (`BuildFeelReportTest` §7)
plays the game's own `TalentRules` on the shipped talent data. Tour: `DeedJourney` and `TalentJourney`.

### 16.3 Where it differs, and why

1. **Twenty-six talents** as §13.2's table (23 rolled, 3 fixed: Light Step, Light Sleeper, Packrat), plus the five earned.
2. **The general feats** stay earned perks (holders keep them, nothing to convert); they are shown as earned talents
   (`talents.earned`, `perks[].earnedTalent`) and their effects are unchanged.
3. **Rearranging**: putting a card in an empty slot is free any time (as skill decks); only taking one out needs the
   waking window, a free swap, or the low-level line (character level below 5, as rebuilds). "A full night" is waking
   in the first ten seconds of the morning after at least five seconds in bed.
4. **Values roll as traits do** (every step in the band equally likely), not the prototype's rounded-down roll: the
   talents' share of a whole character comes out 1.5–2.1 % instead of 1.6–1.9 % (report §7).
5. **Migration log**: a returned copy's card is archived to its id like every released card, so its "Returned" line
   lives in the ledger's `deed_returns` (card, deed, holder, day, settled) and the server log, and in the chat line its
   holder gets on the next join.
6. **Talent fusion is free** (the skill cards' fee table is by skill level, which a talent has none of).

---

## 17. Built (F8 Stealth, 2026-10-21)

Stealth ships as the ninth released skill (§14.1), built on branch `mettle-stealth`. Speech (F9) waits for Hearth's
requests 12–14.

### 17.1 Where it lives

| Part | Code |
|---|---|
| The tree | `data/mettle/mettle/perk/stealth/` (20 perks), `skill/stealth.json` (`released`, paths Ghost / Knife in the Dark), lang `stealth.json` (the two cursed cards' lines in `draft.json`, with the others) |
| Traits, card skill, bynames | `tools/cards/cards_data.py` tables (14 traits, prefix `SL`, natives and pool as §14.1, the skill's byname words and its eight pairs) |
| XP, perks, traits | `character.skill.StealthSkill` (ids, XP with the anti-farm taper), `skill.stealth.Sneaking` (sneak timers, near-hostile and Warden XP, Vanish, Nightstalker, Under Its Nose), `StealthHooks` (visibility, sneak attacks, slows, poison, Swift Exit, alerts, vibrations, falls, Darkness, Lootfinger, Night Hunter), `Shadowstep`, pure numbers in `StealthRules`; mixins `mixin.stealth` |
| Hearth | `src/hearthApi`: `PlayerSkills.bonus` / `title` / `titles` defaults and `StandingEvents` (`SPEECH_CHECK`, `LAW_CHECK`); `compat.HearthLink` (Hearth loaded, `bonus` declared by the loaded interface: reflection once at init), `compat.HearthKeys`, `compat.HearthChannels` (the one-channel table); `MettlePlayerSkills.level` answers the effective Stealth, `bonus` the four keys, `title` the worn byname, `titles` the Mythics held; unseen crimes (`hearth:law/unseen/…`) count toward Never Seen |
| Teaching | a city's fence (`HearthRules.TEACHERS`); *The Nightwalker's Book* in Expanse's gallows hill hidden chest, a dungeon chest without Expanse (`Tomes`) |
| Silver Tongue (talent) | `MettlePrices.edge` (market edge, the price channel only) and, without Hearth, `VillagerMixin` (villagers' prices) |
| Icons | `tools/icons/tree_stealth.py` (18), the two cursed cards recast by `cursed.py` (Skulker from Soft Step, Coward's Edge from Ambusher) |

### 17.2 Checked

`StealthDataTest` (the tree's shape, every id has a file, text and icon, the 14 traits' ranges and attributes, the card
skill), `StealthRulesTest` (XP rules, caps and exclusions; sight; sneak-attack perks; giving up; Darkness; Hush; the
effective level never past 20 and only the listed sources over 20,000 random characters; bonus keys, `unseen_beyond`
4 or 0; fallbacks off with keys), `HearthChannelsTest` (each Stealth effect and Silver Tongue alone reaches Hearth
through exactly the channel the table names, with and without keys; every Stealth line that speaks of Hearth has a
route), `PacingReportTest` (a night hunter: 636 XP an hour), `CardAuditTest` (134 traits; Coward's Edge and Skulker
under Hex). The simulator plays nine skills: per-skill power 1.13× / 1.24× (Wits 14) worst, Stealth 1.13× / 1.24×.
Client tour: `StealthJourney` (a level from real XP and a card kept from the overlay; XP sneaking near an unaware
hostile and none walking; a sneak attack's XP and Ambusher's 30 %; Vanish; Ghost at midnight, its key and fallback;
Shadowstep's sneak-jump) and, with `-Phearth`, `HearthJourney` reads the effective Stealth through Hearth's own slot.

### 17.3 Where it differs, and why

1. **Deep's `momentum` is now 0** (was 1.0): Stealth carries three Deep natives and Deep in its pool, and the re-run put
   a committed Deep 6-set at skill 12.2 (0.5: 12.3, 0.25: 12.5); at 0 it lands at 13.1, 98 % by skill 20.
2. **A sneak attack** is a hit on a mob that isn't targeting you while you sneak (an arrow loosed while sneaking counts
   wherever it lands). Its XP counts the hit before armour, as weapon XP does.
3. **Unseen Blade and Muffled** stop the struck mob calling its kind (`HurtByTargetGoal#alertOthers`): vanilla raises no
   other alarm at a kill.
4. **Fallbacks while Hearth reads no keys**: Cloak of Dusk +2 and Unseen +1 at night, Ghost +5 while Ghost holds, The
   Quiet Way +2 (all on the effective level), Light-fingered as animals noticing you closer. With keys, none of them.
5. **Never Seen**: one tally to 100, an unseen Hearth crime 5 (20 crimes), without Hearth an unnoticed kill 2 (50).
   **Under Its Nose**: within 8 blocks of a Warden that never noticed you, then past 16 (or it gone).
6. Stealth has **no drawn Mythics** of its own yet, so its drafts roll for none (Drafts skips a skill with no drawn pool).
