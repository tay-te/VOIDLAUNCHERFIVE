# VOID Mettle — cards v2: rolled cards, decks, sets, trading, Mythics

**Status: DESIGN (2026-10-10), approved in outline by the user; nothing built.** Builds on the perk draft
([DRAFT.md](DRAFT.md)): the draft stays the source of cards. Where this doc and DRAFT.md disagree, this doc wins once
wave A ships (§16). Screens go to Figma first (§13).

**What the user asked for:** "More build defining: someone's path should never be the same as someone else's. You can
rebuild decks, but at an expensive cost. Trade perk cards with other people. Each skill should have different rarities
and attributes so one skill has hundreds of different styles and combinations. Deck building should be fun because it
directly affects the player's abilities."

A player has to know five things:

1. **Every card is one of a kind.** A perk, a rarity, one or two **keywords** and a few **traits**, all rolled when it
   is drawn. Two Quarryman cards are never the same card.
2. **Only your deck counts.** Each skill has a few **slots**; the cards in them are active, the rest wait in your
   **binder**. Slotting is free. Taking a card out is not.
3. **Keywords make sets.** Two, four or six slotted cards sharing a keyword (from any skills) give a set bonus. Three
   sets are active at a time. That is your build.
4. **Cards can change hands.** Trade them face to face for items, cards or coin, or list them at a Hearth card broker.
   A card remembers who drew it.
5. **Mythics** are single named cards, one of a kind in the world or earned by a legendary deed. Everyone can see who
   holds them.

---

## 1. What changes from the draft (v1 → v2)

| | v1 (DRAFT.md) | v2 (this doc) |
|---|---|---|
| A kept card | learns a perk rank | becomes a **card** in your binder: a unique, rolled object with an id |
| Rarity | set by depth (roots Common, tier II Rare, tier III and Masteries Epic) | **rolled** per card, separately from depth (§3.2); Cursed stays the frame of the 16 cursed perks |
| Ranks | a root comes back as "next rank" until rank 3 | a root's **rank is its rarity**: Common I, Rare II, Epic III. Owning a perk never stops it being dealt again: a duplicate is a new rolled card (§3.4) |
| What is active | every perk you learned | only **slotted** cards (§5); earned perks (feats, synergies, teachings) are always active and are not cards |
| Masteries | holding one removes the other two from the deck for good | you can own all three; **one** may be slotted per deck, and only with two cards of its side slotted |
| Spent deck | turns waiting drafts into rerolls | gone: a deck is never spent |
| Respec | a Draught of Forgetting refunds a skill's perks as drafts | the draught is the key to a **rebuild** at a trainer or Card Shrine; removed cards come out **scarred** (§7) |
| Attributes | gate tiers softly | still do; and **Wits gives deck slots**, the other four boost traits of their kind (§4.3) |
| New | — | keywords and set bonuses (§6), trading and attunement (§8), Mythics (§9), the Card Shrine block (§7.3) |

Unchanged: the cadence (a draft every level to 6, then even levels; odd levels from 7 bring a reroll; 13 drafts to
skill 20), hands of three, keep one, wildcards (12 %), the reroll pool (start 2, hold 5), the soft attribute gate, the
tier gates (2 / 6 / 12 / 18), hands stored in the record, everything decided on the server.

---

## 2. Card anatomy

```
┌──────────────────────────────────────────┐
│ ▌Mining                     II · Rare    │  skill hue band · tier pips · rarity (frame colour)
│                                          │
│            [ pixel art: Tunneler ]       │
│                                          │
│ Tunneler                                 │  base perk name
│ Sneak-mining with a pickaxe breaks a     │  base effect (the perk's own sentence; never changes)
│ 1 × 2 passage.                           │
│ ◆ Echo   ◆ Deep                          │  keywords: 1–2
│ ──────────────────────────────────────── │
│ Deepborn   dig 9 % faster below y 0      │  traits: 0–3, each with its rolled value
│            → 11 % (Strength 14)          │  … and what your attribute makes of it
│ Echoing    8 % chance to take the        │
│            touching ore too              │
│ ──────────────────────────────────────── │
│ Needs Mining 7                           │  requirement to slot (§3.3)
│ Drawn by Ashford at Mining 9 · day 41    │  history
│ MN-7Q4K-2F                ✂ scarred ×1   │  id · scars
└──────────────────────────────────────────┘
```

| Part | What it is | Where it comes from |
|---|---|---|
| **Perk** | one of the skill's 15 draftable perks (2 roots, 8 path perks, 3 Masteries, 2 cursed; TREES.md) | the draft's depth draw (§3.1) |
| **Tier** | I (root), II, III, M (Mastery); cursed cards are II or III | the perk |
| **Rarity** | Common, Rare, Epic; Cursed for cursed perks; Mythic for Mythics | rolled (§3.2) |
| **Rank** | roots only: I / II / III = Common / Rare / Epic (Quarryman I digs 10 % faster, III 30 %) | rarity |
| **Keywords** | the perk's **native** keyword, plus a second one rolled from the skill's pool (§6.2) | data + roll |
| **Traits** | 0–3 small effects from the skill's own pool (§4), each with a rolled value | roll |
| **Requirement** | the skill level needed to slot it, from tier and rarity (§3.3) | rule |
| **History** | who drew it, at what skill level, on which in-game day and real date; how many have held it; the last five owners; scars | ledger |
| **Id** | world-unique, server-issued, printed short (`MN-7Q4K-2F`: skill prefix + base-32) | ledger |

Mythics have a name, art, a line of lore, one effect and a history: no rarity, tier, rank, keywords or traits (§9).

### 2.1 The data model

**World ledger** (`character.cards.CardLedger`, a `SavedData` in the overworld's data storage, `mettle_cards.dat`) is
the single truth for every card that exists. A card is one record:

```
Card
  id          long        world-unique; the counter never reuses an id (released cards keep theirs)
  perk        ResourceKey<Perk> | mythic id
  rarity      COMMON | RARE | EPIC | CURSED | MYTHIC
  keywords    1–2 keyword ids (none on a Mythic)
  traits      0–3 × (trait id, value)        value as rolled, before attributes
  needs       int          skill level to slot (§3.3)
  drawn       (player uuid, name, skill level, world day, epoch millis, how: DRAFT | WILDCARD | MIGRATED | MYTHIC)
  owner       player uuid
  owners      count, and the last 5 × (uuid, name, world day)
  scars       int
  scarSeed    long         decides which trait a scar takes, so the preview is honest (§7.2)
  place       BINDER | SLOTTED(skill, index) | TRADE(session id) | LISTED(listing id) | RELEASED
  attunedAt   world tick when it may next be slotted, traded or listed (0 = attuned)
```

The ledger also holds `nextId`, and the **Hall of Firsts**: each world-first Mythic → (holder of record, the deed's
date) and each drawn Mythic → claimed or not (§9).

**Player side** (`mettle:character/cards`, persistent, copied on death, synced to its owner): only a cache of the
owner's view (`binder` ids, the `deck` per skill, `freeSwaps`, `activeSets`, `setsChangedDay`). On join the server
rebuilds it from the ledger, so a rolled-back or copied `playerdata` file can neither lose nor duplicate a card: an id
the ledger gives to someone else is dropped from the cache and logged; a card the ledger gives to this player and the
cache lacks is put back.

**Public side** (`mettle:character/mythics`, not persistent, synced to all trackers): the ids of the Mythics a player
holds, for name cards and tooltips (§9.4).

**Hands** (`core.DraftState`) store **offers**, not cards: the rolled perk, rarity, keywords, traits and wildcard
flag. An offer gets an id and enters the ledger only when it is kept; rerolled offers never existed.

**Data** (dynamic registries, synced, loaded with the world):

| What | Where | Fields |
|---|---|---|
| Traits | `data/<ns>/mettle/trait/<skill>/<id>.json` | `skill`, `kind` (might / finesse / endurance / fortune), `min`, `max`, `step`, `scope` (broad / narrow), `effect` (an attribute modifier or a behaviour id, as perk effects), `name`, `description` with `%s` for the value |
| Keywords | `data/<ns>/mettle/keyword/<id>.json` | `name`, `colour`, `icon`, `sets` {`2`, `4`, `6`: effects} |
| Mythics | `data/<ns>/mettle/mythic/<id>.json` | `name`, `lore`, `effect` (behaviour id), `source` (world_first / deed / drawn), `trigger` (a deed id handled in code), `skill` (drawn only) |
| Perks | existing perk JSON gains `keyword` (its native keyword) | |
| Skills | existing skill JSON gains `keyword_pool` (the second keywords it can roll) and `card_prefix` (`MN`) | |

Every number in §3–§7 lives in config (`config/mettle/cards.json`, BALANCE.md §11) or data.

---

## 3. Rolling a card

A hand still has three slots. Each slot is rolled in four steps, all from the player's seed and the hand's place in
it (DRAFT.md §9), so the same character always gets the same offers.

### 3.1 The perk (depth)

As v1, with "learned" read as "owned": the slot draws a depth band, then a perk in it, with the soft attribute gate
(0.4×) and the hard gates (tier skill levels; a tier III path card needs a tier II card of its path **owned**; a
Mastery card needs two cards of its side owned, any two path cards for the middle one). The v1 rarity odds become the
**depth odds**:

| Skill level | Root | Tier II | Tier III or Mastery | Cursed |
|---|---|---|---|---|
| 0 | 62 % | 26 % | 8 % | 4 % |
| 10 | 47 % | 34 % | 14 % | 5 % |
| 20 | 32 % | 42 % | 20 % | 6 % |

(Root 62 − 1.5 L, II 26 + 0.8 L, deep 8 + 0.6 L, cursed 4 + 0.1 L, as before.) New: **a perk you already own is dealt
at ½ weight per copy owned** (at least ¼), so duplicates come but variety wins. A hand still never holds the same perk
twice.

### 3.2 Rarity

Rolled per card, separately from depth. Higher skill shifts it up:

| Skill level | Common | Rare | Epic |
|---|---|---|---|
| 0 | 70 % | 24 % | 6 % |
| 5 | 62.5 % | 29 % | 8.5 % |
| 10 | 55 % | 34 % | 11 % |
| 15 | 47.5 % | 39 % | 13.5 % |
| 20 | 40 % | 44 % | 16 % |

(Common 70 − 1.5 L, Rare 24 + L, Epic 6 + 0.5 L.) Cursed perks are always **Cursed**. Frame colours stay
(`DraftState.Rarity`): Common `#B9BEC6`, Rare `#4DA3F5`, Epic `#B27CFF`, Cursed `#D8453B`; Mythic is new (§13).

### 3.3 Requirement ("Needs Mining 7")

A card needs its tier's level, and a little more the rarer it is. It is printed on the card, and it binds everyone:
the player who drew it and anyone who trades for it.

| Tier | Common | Rare | Epic | Cursed |
|---|---|---|---|---|
| I (roots) | 2 | 4 | 7 | — |
| II | 6 | 7 | 9 | 6 |
| III | 12 | 13 | 15 | 12 |
| Mastery | 18 | 19 | 20 | — |

The step is bigger for roots because a root's rank is its rarity: an Epic Quarryman III dealt at Mining 2 waits in the
binder until Mining 7. A card above your level is never wasted, just early; the hand shows it ("Slots at Mining 7").

### 3.4 Traits and keywords

| Rarity | Traits | Values roll in | Second keyword |
|---|---|---|---|
| Common | 0 (50 %) or 1 (50 %) | the low two thirds of each range | never |
| Rare | 1 (60 %) or 2 (40 %) | the whole range | 30 % |
| Epic | 2 (60 %) or 3 (40 %) | the top two thirds | 70 % |
| Cursed | 1 (50 %) or 2 (50 %) | the whole range | always (the native keyword of a cursed card is **Hex**) |

- Traits come only from the card's own skill's pool (§4), all equally likely, never twice on one card. Values are
  rounded to the trait's `step` (1 % for percentages, 0.5 for blocks, hearts and seconds).
- The second keyword comes from the skill's pool (§6.2) and is never the same as the first.
- **Ranks and duplicates.** Roots have no "next rank" any more: Quarryman I, II and III are three rarities of the same
  perk, and you can own all three. Only one card of a perk can be slotted in a deck, so a better copy replaces a worse
  one (a rebuild) instead of stacking with it. Path perks and Masteries have one strength; their rarity shows in their
  traits.
- **Cursed cards keep their downside** exactly as DRAFT.md §4 prints it. Their traits are the reward for carrying it,
  which is why they roll one or two at any value.

**How many styles?** A Mining card is one of 15 perks × 3 rarities, carrying one of 576 trait sets (0–3 of 15 traits)
and one of six second keywords, before the values: about 150,000 distinct faces per skill. A deck of eight is one of
far more than anyone will ever see twice.

### 3.5 Mythic roll

From skill 15, each **draft** (not each hand: rerolls never roll it) has a **0.5 %** chance that its third slot is that
skill's drawn Mythic, if no one in the world has drawn it yet (§9.3). A Mythic offer survives rerolls: rerolling deals
the other two slots again.

---

## 4. Traits

### 4.1 Rules

- A trait works on its own while its card is slotted. It never changes the base perk.
- **Broad** traits (any damage, all digging, all movement, armour) roll at most **+12 %**; with the best attribute
  (§4.3) they reach **+15 %**. **Narrow** traits (one block kind, one damage type, one mob kind) roll at most **+25 %**
  (30 % with the attribute). Flat traits are sized to the same feel (a broad +1.5 armour is about +15 % of iron armour).
- **The same trait on several slotted cards adds up, to at most twice its card cap**: Deepborn can give at most +30 %
  however many cards carry it. The Deck screen shows "Deepborn 24 % of 30 %".
- Social traits (they help allies near you) never stack between players: an ally gets the strongest one in range.
- `pvp_perks` (`full` / `reduced` / `off`, BALANCE.md §6) governs traits, sets and Mythics exactly as it governs perks.

### 4.2 Pools (all eight released skills)

X is the rolled value. **Attr** is the attribute that boosts it (§4.3): **Str** might, **Agi** finesse, **Vit**
endurance, **Pre** fortune and fellowship.

#### Mining

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Deepborn** | Dig X faster below y 0. | 5–12 % | Str |
| **Rockbreaker** | Stone-like blocks (stone, deepslate, tuff, granite…) break X faster. | 5–12 % | Str |
| **Netherborn** | Netherrack, blackstone, basalt and ancient debris break X faster. | 10–25 % | Str |
| **Hauler's** | Stone, ore and raw metal weigh X less for you. | 8–20 % | Str |
| **Greedy** | Metal ores drop one more raw metal X of the time. | 3–8 % | Pre |
| **Lucky** | Gem ores (diamond, emerald, lapis, redstone, quartz) drop one more X of the time. | 3–8 % | Pre |
| **Seamfinder** | The first ore of each vein drops one more X of the time. | 8–20 % | Pre |
| **Geodic** | Amethyst buds and clusters drop one more shard X of the time. | 15–25 % | Pre |
| **Prospecting** | Mining XP from ore +X. | 5–12 % | Pre |
| **Echoing** | Breaking an ore also breaks one touching ore of the same kind X of the time. | 4–10 % | Agi |
| **Surefooted** | Digging while climbing, swimming or in the air is X less slow. | 10–25 % | Agi |
| **Stonehide** | Falling blocks, dripstone, magma and suffocation hurt X less. | 10–25 % | Vit |
| **Cavern-wise** | Mobs hurt you X less while you are below y 0. | 4–10 % | Vit |
| **Tireless** | Digging costs X less hunger. | 10–25 % | Vit |
| **Unworn** | Pickaxes lose durability X less often. | 6–15 % | Vit |

#### Blades

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Keen** | Blade critical hits deal X more. | 5–12 % | Str |
| **Sweeping** | Your sweeps deal X more. | 8–20 % | Str |
| **Finishing** | Blade hits on targets below 30 % health deal X more. | 5–12 % | Str |
| **Tempo** | Each hit on the same target within 1.5 s of the last swings X faster, up to 3 stacks. | 3–6 % a stack | Agi |
| **Bleeding** | Critical blade hits make the target bleed X damage over 3 s. | 1–3 | Agi |
| **Parrying** | Holding a blade (not blocking), a melee hit is turned aside for half its damage X of the time. | 3–8 % | Agi |
| **Fleet** | You move X faster while holding a blade. | 2–6 % | Agi |
| **Ambushing** | Blade hits on a mob that isn't targeting you deal X more. | 6–15 % | Agi |
| **Long** | Blades reach X blocks further. | 0.1–0.3 | Agi |
| **Flourishing** | A blade kill gives you X speed for 3 s. | 5–12 % | Agi |
| **Thirsting** | You heal X of the blade damage you deal (at most 1 heart a second). | 2–5 % | Vit |
| **Grounded** | X less knockback while you hold a blade. | 10–25 % | Vit |
| **Bloodied** | Below half health, blades swing X faster. | 5–12 % | Vit |
| **Honed** | Swords and spears lose durability X less often. | 6–15 % | Vit |
| **Trophy** | Blade kills drop one more of their loot X of the time. | 3–8 % | Pre |
| **Taunting** | A mob you hit with a blade turns on you instead of an ally X of the time. | 10–25 % | Pre |

#### Heavy Weapons

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Crushing** | Heavy hits ignore X of the target's armour. | 5–12 % | Str |
| **Staggering** | Heavy hits daze the target for 0.5 s X of the time. | 4–10 % | Str |
| **Woodcutter's** | Axes chop wood X faster. | 8–20 % | Str |
| **Heavy-handed** | Heavy hits knock back X further. | 10–25 % | Str |
| **Cleaving** | X of an axe hit spills onto one enemy beside the target. | 5–12 % | Str |
| **Thunderous** | Mace smashes deal X more. | 8–20 % | Str |
| **Shieldbreaker** | Heavy hits keep a raised shield down X longer. | 15–25 % | Str |
| **Charging** | Your first heavy hit after 2 s of sprinting deals X more. | 6–15 % | Agi |
| **Swinging** | Heavy weapons swing X faster. | 3–8 % | Agi |
| **Raging** | Each hit you take gives +X damage for 5 s, up to 3 stacks. | 2–4 % a stack | Vit |
| **Rooted** | X less knockback while you hold a heavy weapon. | 10–25 % | Vit |
| **Bloodthirsty** | A heavy kill heals X hearts. | 0.5–1.5 | Vit |
| **Unshaken** | Slowness and Weakness on you last X less while you hold a heavy weapon. | 15–25 % | Vit |
| **Unworn** | Axes, maces and tridents lose durability X less often. | 6–15 % | Vit |
| **Splitting** | Logs you chop drop one more X of the time. | 5–12 % | Pre |
| **Headtaker** | Heavy kills drop the mob's head X of the time. | 2–5 % | Pre |

#### Marksmanship

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Heavy-tipped** | Arrows deal X more. | 3–8 % | Str |
| **Piercing** | Arrows pass through their first target X of the time. | 5–12 % | Str |
| **Quickdraw** | Bows draw X faster. | 4–10 % | Agi |
| **Farsighted** | Arrows deal X more beyond 20 blocks. | 6–15 % | Agi |
| **Swift-flighted** | Arrows fly X faster (they drop less at range). | 5–12 % | Agi |
| **Cranking** | Crossbows load X faster. | 8–15 % | Agi |
| **Headhunting** | Arrows that hit the head deal X more. | 8–20 % | Agi |
| **Winged** | Arrows deal X more to flying mobs. | 10–25 % | Agi |
| **Twinned** | A shot looses a second arrow beside the first X of the time (it uses one). | 3–8 % | Agi |
| **Poised** | After you stand still for 1 s, arrows deal X more. | 5–12 % | Vit |
| **Steady** | Your shots spread X less while you move. | 10–25 % | Vit |
| **Thrifty** | Arrows aren't used up X of the time. | 4–10 % | Pre |
| **Retrieving** | Arrows drop back from what they kill X of the time. | 8–20 % | Pre |
| **Marking** | A target you hit takes X more damage from everyone for 3 s. | 3–8 % | Pre |
| **Game-hunter's** | Arrows deal X more to animals. | 10–25 % | Pre |

#### Defense

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Thorned** | Melee hits your shield blocks hurt the attacker for X of the hit. | 5–12 % | Str |
| **Unyielding** | X less knockback. | 5–12 % | Str |
| **Fitted** | Worn armour weighs X less. | 8–15 % | Str |
| **Quickguard** | Your shield raises X faster. | 15–25 % | Agi |
| **Arrowcatcher** | Arrows and other projectiles hurt X less. | 6–12 % | Agi |
| **Stalwart** | +X armour. | 0.5–1.5 | Vit |
| **Toughened** | +X armour toughness. | 0.5–1.5 | Vit |
| **Stout** | +X max health. | ½–1 heart | Vit |
| **Mending** | Armour and shields wear X slower. | 8–15 % | Vit |
| **Resolute** | Harmful effects on you last X less. | 8–15 % | Vit |
| **Fireproof** | Fire and lava hurt X less. | 10–25 % | Vit |
| **Blastproof** | Explosions hurt X less. | 10–25 % | Vit |
| **Recovering** | After 5 s without being hurt, you regenerate X faster. | 10–25 % | Vit |
| **Shielding** | While you block, players within 4 blocks take X less damage. | 4–10 % | Pre |
| **Taunting** | While you block, a hostile within 8 blocks attacking an ally turns on you X of the time. | 10–25 % | Pre |

#### Athletics

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Strapped** | +X kg carry capacity. | 3–8 kg | Str |
| **Burden-wise** | You become Burdened X points later (75 % → 75 + X). | 2–5 | Str |
| **Bracing** | X less knockback while you sprint. | 10–25 % | Str |
| **Fleet** | You sprint X faster. | 2–6 % | Agi |
| **Springing** | Sprint-jumps go X higher. | 4–10 % | Agi |
| **Featherfall** | You fall X more blocks before taking damage. | 0.5–1.5 | Agi |
| **Climbing** | You climb X faster. | 10–25 % | Agi |
| **Surefoot** | Soul sand, honey, powder snow and cobwebs slow you X less. | 15–25 % | Agi |
| **Windless** | Sprinting costs X less hunger. | 8–15 % | Vit |
| **Waterborn** | You swim X faster. | 6–15 % | Vit |
| **Deep-lunged** | You hold your breath X longer. | 3–8 s | Vit |
| **Long-winded** | After 10 s of sprinting, +X speed until you stop. | 3–8 % | Vit |
| **Rolling** | Falls hurt X less. | 6–15 % | Vit |
| **Pacesetting** | Allies sprinting within 8 blocks of you move X faster. | 2–5 % | Pre |

#### Survival

| Trait | What it does | Range | Attr |
|---|---|---|---|
| **Stalking** | Animals notice you X closer. | 10–25 % | Agi |
| **Wayfaring** | You move X faster on paths and roads. | 4–10 % | Agi |
| **Hearty** | Food fills you X more. | 5–12 % | Vit |
| **Iron-gut** | You get hungry X slower. | 4–10 % | Vit |
| **Weathered** | Freezing and drowning hurt X less. | 15–25 % | Vit |
| **Fireside** | Campfire Rest and campfire cooking work X faster. | 15–25 % | Vit |
| **Thornwalker** | Berry bushes and cacti hurt X less. | 15–25 % | Vit |
| **Herbwise** | Potions you drink last X longer. | 6–15 % | Vit |
| **Antidote** | Poison and Hunger on you last X less. | 15–25 % | Vit |
| **Foraging** | Wild plants and berries give a bonus item X of the time. | 5–12 % | Pre |
| **Skinning** | Animals drop one more hide, leather, wool or feather X of the time. | 8–20 % | Pre |
| **Butchering** | Animals drop one more meat X of the time. | 8–20 % | Pre |
| **Cook's** | Food you cook comes out one extra X of the time. | 5–12 % | Pre |
| **Wayfinder** | Discovery XP +X. | 8–20 % | Pre |
| **Beastfriend** | Your tamed animals near you deal X more and take X less. | 8–20 % | Pre |

#### Smithing

Each Smithing trait has a vanilla meaning and, when VOID Arsenal is loaded, a forging meaning (§10).

| Trait | Without Arsenal | With Arsenal | Range | Attr |
|---|---|---|---|---|
| **Tempered** | Tools and weapons you craft last X longer. | Parts you forge last X longer. | 5–12 % | Str |
| **Edged** | Weapons you craft deal X more. | Blades and heads you forge deal X more. | 2–5 % | Str |
| **Lightened** | Armour you craft weighs X less. | Also parts you forge. | 6–15 % | Agi |
| **Steady-handed** | Anvil jobs cost X fewer levels (never below 1). | The anvil's strike window is X wider. | 8–20 % | Agi |
| **Quenched** | Tools you craft dig X faster. | Quench tempers are X stronger. | 3–8 % | Agi |
| **Hot-forged** | Smithing XP from smelting +X. | The forge heats X faster. | 10–25 % | Vit |
| **Mender's** | Anvil repairs restore X more durability. | Same, and smith repairs. | 10–25 % | Vit |
| **Proofing** | Armour you craft gets +X toughness a piece. | Same. | 0.25–0.5 | Vit |
| **Thrifty** | A recipe with ingots gives one back X of the time. | Forging returns a nugget X of the time. | 3–8 % | Pre |
| **Masterly** | A tool, weapon or armour piece you craft is a Masterwork X of the time (TREES.md §10). | A part comes out one quality higher X of the time. | 2–5 % | Pre |
| **Salvaging** | Salvaging (grindstone, Salvager) returns X more metal. | Workbench salvage returns X more. | 10–25 % | Pre |
| **Signature** | Goods marked "Forged by you" sell for X more at Hearth markets. | Parts roll their ±4 % variation X points toward the good side. | 5–12 % / 1–2 pts | Pre |
| **Runewise** | Smithing XP from anvil jobs +X. | +1 arcane capacity on X of the parts you forge. | 10–25 % / 5–12 % | Pre |
| **Diligent** | Smithing XP +X. | Same. | 5–12 % | Pre |

### 4.3 Attributes and traits

| Attribute | v2 role on cards |
|---|---|
| **Wits** | **Deck slots**: +1 slot in every skill at Wits 8, +2 at Wits 14 (§5.1). Wits builds are wide. |
| **Strength** | boosts **Str** traits (might: damage, breaking, hauling, knockback) |
| **Agility** | boosts **Agi** traits (finesse: speed, draw, reach, precision) |
| **Vitality** | boosts **Vit** traits (endurance: armour, health, hunger, resistances, durability) |
| **Presence** | boosts **Pre** traits (fortune and fellowship: drops, loot, XP, help for allies) |

A trait's value × (1 + **1.5 %** per point of its attribute above 3): ×1.105 at 10, ×1.165 at 14, ×1.255 at 20. The
card prints the rolled value; the boosted value is shown under it for the viewer's own attributes, so a traded card is
worth more to a player built for it. Points below 3 do not shrink traits.

Every skill mixes kinds on purpose: Blades is mostly Agility, but a Strength fencer rolls toward Keen, Sweeping and
Finishing, and a Vitality one toward Thirsting and Bloodied. Which traits you keep follows from where you put points.

---

## 5. The deck

### 5.1 Slots

| Skill level | 0–1 | 2 | 5 | 8 | 11 | 14 | 17 | 20 |
|---|---|---|---|---|---|---|---|---|
| Base slots | 0 | 3 | 4 | 5 | 6 | 7 | 8 | 8 |
| With Wits 8 / Wits 14 | — | 4 / 5 | 5 / 6 | 6 / 7 | 7 / 8 | 8 / 9 | 9 / 10 | 9 / 10 |

Base = 3 + ⌊(L − 2) ÷ 3⌋ from skill 2, at most 8. Drafts to that level for comparison: 2, 5, 7, 8, 10, 11, 13: there
are always a few more cards than slots, so a deck is a choice from the first hour.

### 5.2 Slot rules

1. Your skill level ≥ the card's requirement, and the card is **attuned** (§8.3).
2. One card of a perk per deck (a better Quarryman replaces the old one).
3. One Mastery per deck, and only while two cards of its side are slotted (any two path cards for the middle one).
   Take one away and the Mastery goes **dormant**: it stays slotted, does nothing and counts for no set until the side
   is whole again.
4. Wildcards are cards of their own skill: a Blades card dealt into a Mining hand goes in the Blades deck.
5. **Mythics** fit any deck and take one of its slots; at most **two** slotted per character, never two in one deck.
6. If slots shrink (a Draught of Unbinding takes Wits below 8 or 14), the last slots go dormant; their cards come out
   free and unscarred.

**Slotting is free and works anywhere**: open the Deck, pick an empty slot, pick a card. Taking one out is a rebuild
(§7). The draft's pick offers both **Keep** (to the binder) and **Keep & slot** when a legal slot is free.

### 5.3 What "active" means in code

`Perks.has` / `Perks.rank` (and so `CharacterService.hasPerk`, Hearth's `PlayerSkills.hasPerk`) answer from slotted,
non-dormant cards plus earned perks. The character record's learned-perk set keeps only earned perks (feats,
synergies, teachings, signature perks). `EffectsApplier` gains three kinds of fixed ids:
`mettle:trait/<skill>/<trait>` (one per trait, the capped sum of its slotted copies), `mettle:set/<keyword>/<tier>`,
`mettle:mythic/<id>`. Perks keep `mettle:perk/<perk>`. Slotting or unslotting re-applies effects.

---

## 6. Keywords and set bonuses

### 6.1 Rules

- A card counts once for **each** of its keywords. Only slotted, non-dormant cards count. Mythics carry none.
- Counts cross skills: two Swift cards in Athletics and two in Blades make Swift 4.
- **Six needs two skills**: the 6-card tier needs its cards from at least two different decks.
- **Three sets are active at a time.** By default the three keywords with the most slotted cards (ties: the one
  reached first). The player may pin their three on the Deck screen instead; a pinned choice can change once per
  in-game day (20 minutes). Inactive keywords show their counts greyed.
- A set's tiers add up: Swift 6 gives the 2, 4 and 6 bonuses.

### 6.2 Native keywords and pools

Each draftable perk has one native keyword (perk JSON `keyword`); the second is rolled from the skill's pool.

| Skill | Native keywords | Second-keyword pool |
|---|---|---|
| Mining | Quarryman Brutal · Prospector Hoard · Sure Footing Gale · Hard Hat Iron · Lantern Eyes Deep · Ore Sense Deep · Rock Hauler Hoard · Tunneler Echo · Strike the Seam Hoard · Gem Cutter Keen · Deep Delver Deep · Motherlode Echo · Mountain Breaker Brutal · Greedy Pick, Black Lung Hex | Deep, Hoard, Brutal, Echo, Iron, Ember |
| Blades | Fencer Swift · Quick Draw Keen · Measure Keen · Disarm Warden · Momentum Echo · Single Combat Keen · Light Feet Swift · Wide Arc Brutal · Bleed Brutal · Cut and Run Swift · Duelist Warden · Whirlwind Echo · Executioner Brutal · Reckless Edge, Glass Dancer Hex | Swift, Keen, Echo, Brutal, Warden, Gale |
| Heavy Weapons | Heft Brutal · Feller Wild · Cleave Echo · Log Hauler Hoard · Rhythm of the Axe Echo · Throwing Axe Gale · Armour Crusher Brutal · Bone Breaker Brutal · Iron Grip Iron · Stagger Keen · Timber Wild · Berserker Ember · Earthshaker Brutal · Bloodrage, Immovable Hex | Brutal, Iron, Ember, Echo, Wild |
| Marksmanship | Steady Aim Keen · Retrieval Hoard · Long Shot Keen · Hunter's Mark Wild · Steady Breath Gale · Pinning Shot Keen · Quick Nock Swift · Fletcher Hoard · Double Nock Echo · Quiverwise Swift · Deadeye Keen · Volley Echo · Arrowstorm Gale · Heavy Draw, Snap Shot Hex | Keen, Gale, Swift, Wild, Echo |
| Defense | Thick Skin Iron · Armour Training Iron · Shield Up Warden · Shield Bash Brutal · Perfect Block Keen · Bulwark Warden · Mender Iron · Second Wind Ember · Hold the Line Iron · Unbowed Warden · Guardian Kin · Last Stand Ember · Juggernaut Brutal · Iron Hide, Bastion Hex | Iron, Warden, Kin, Brutal, Ember |
| Athletics | Pack Mule Hoard · Runner Swift · Strong Back Hoard · Steady Pace Swift · Beast of Burden Hoard · Hauler's Stride Swift · Swimmer Tide · Soft Landing Gale · Vaulter Gale · Spring Gale · Ox Hoard · Marathon Swift · Freerunner Gale · Featherweight, Bull's Back Hex | Swift, Gale, Tide, Hoard, Kin |
| Survival | Gatherer Wild · Field Cook Ember · Quiet Approach Wild · Skinner Hoard · Stalker Keen · Sixth Sense Deep · Iron Stomach Iron · Campfire Rest Ember · Pathfinder Swift · Self-Sufficient Wild · Tracker Wild · Lay of the Land Kin · Living off the Land Wild · Wolf's Hunger, Wild Luck Hex | Wild, Ember, Tide, Kin, Deep |
| Smithing | Tinker Ember · Thrifty Hoard · Light Hand Iron · Riveted Iron · Proofed Iron · Shieldwright Warden · Forged Ember · Salvager Hoard · Keen Edge Keen · True Temper Ember · Reforge Echo · Masterwork Ember · Honing Keen · Brittle Edge, Slapdash Hex | Ember, Iron, Hoard, Keen, Kin |

Tide and Kin are scarce on purpose: a 6-set of either is a rare, recognisable build.

### 6.3 The fourteen keywords and their sets

"Allies" everywhere means: players on your scoreboard team if you are on one, otherwise every player; tamed animals
and Hearth folk where a line says so. No ally bonus ever applies to someone attacking you.

| Keyword | Theme | 2 cards | 4 cards | 6 cards (two skills at least) |
|---|---|---|---|---|
| **Ember** | fire, forge, the cookfire | Fire and lava hurt you 15 % less. | Melee hits set the target alight one time in eight (3 s); campfire food heals 1 heart when eaten. | **Kindled**: taking fire damage makes you Kindled for 8 s: +15 % damage, your hits ignite, and you burn for at most 2 s (30 s cooldown). |
| **Deep** | the underground, the dark | Below y 0: dig 5 % faster, take 5 % less damage. | Below y 0, hostiles notice you 25 % closer. | **Underdark**: in light 4 or less you regenerate half a heart every 5 s, and your first hit from the dark on a mob is a critical (10 s cooldown). |
| **Swift** | speed | +3 % movement speed. | +5 % attack speed and bow draw speed. | **Slipstream**: after 3 s of sprinting, your next hit or shot within 2 s deals +30 % and doesn't break your sprint. |
| **Brutal** | heavy blows | +4 % melee damage. | Critical hits knock back half again as far and daze for 0.5 s. | **Overkill**: damage beyond what kills a mob spills onto the nearest enemy within 4 blocks (up to 10). |
| **Echo** | things that happen again | Perk cooldowns 10 % shorter. | Perk cooldowns 20 % shorter (in all). | **Reverberate**: when a perk with a cooldown fires, one time in four it is ready again at once. |
| **Warden** | guarding others | +1 armour. | Allies within 6 blocks take 8 % less damage (the best Warden in range counts). | **Bodyguard**: when an ally within 6 blocks would take more than 4 damage, you take a third of it instead (4 s cooldown). |
| **Keen** | precision | Critical hits (melee and arrows) deal +8 %. | Your first hit on a target at full health is a critical, once per target. | **Weak Point**: three hits on one target within 4 s expose it: it takes +20 % from everyone for 4 s (10 s per target). |
| **Wild** | beasts and plants | Animals you kill and wild plants you pick give 10 % more. | Your tamed animals near you take 25 % less damage and deal 15 % more. | **Feral**: on natural ground (outside settlements) you regenerate as if well fed and move 8 % faster. |
| **Iron** | armour and wear | Armour and shields wear 10 % slower. | +1 armour toughness. | **Ironskin**: the first hit you take every 8 s is 3 damage lighter (after armour). |
| **Tide** | water | Swim 10 % faster, +5 s of breath. | Mine at full speed under water; thrown tridents +10 %. | **Undertow**: in water or rain you deal +15 %, every hit slows the target for 1 s, and you see clearly under water. |
| **Gale** | air, falls, flight | Fall 1.5 blocks more before taking damage. | Arrows and thrown things fly 10 % faster; sprint-jumps +10 %. | **Updraft**: jump once more in mid-air before you land (8 s cooldown). |
| **Hoard** | plenty | Everything you carry weighs 5 % less. | Blocks and mobs drop one more of something one time in 25. | **Magpie**: loot chests you open first roll one extra time one time in four; Hearth markets give you 5 % more edge. |
| **Hex** | cursed cards | Your cursed cards' downsides are 25 % smaller. | Their upsides are 15 % stronger. | **Pact**: once every 5 minutes a killing blow leaves you on 4 hearts with Strength I and Speed I for 8 s, and takes 3 max hearts until you next sleep. |
| **Kin** | fellowship | Allies within 8 blocks gain 5 % more skill XP. | You and allies within 8 blocks regenerate 10 % faster. | **Rally Cry**: sneak and swing an empty hand: allies within 16 blocks get Speed I and Strength I for 8 s (90 s cooldown). |

The **Echo** set and every cooldown perk read one scale (`Cooldowns.scale(player)`), which Arsenal's techniques use too
(§10).

---

## 7. Rebuilding

### 7.1 The cost

Taking a card out of a slot (to replace it, to trade it, or just to free the slot) is a **rebuild**. It needs:

1. **A place**: a Hearth trainer of that skill, or a **Card Shrine** (§7.3).
2. **A Draught of Forgetting** brewed for that skill (unchanged recipe, BALANCE.md §8). One draught opens that skill's
   deck for one sitting: take out as many of its cards as you like before you close the screen.
3. **A fee per card**, by its requirement:

| Card needs | Fee (Hearth coin) | Without Hearth |
|---|---|---|
| 2–5 | 40 bits | 1 emerald |
| 6–11 | 120 bits | 3 emeralds |
| 12–17 | 350 bits (3½ crowns) | 8 emeralds |
| 18–20 | 900 bits (9 crowns) | 20 emeralds |
| a Mythic | 1,000 bits, no draught | 24 emeralds, no draught |

For scale: a lesson from 14 to 15 is 580 bits; a horse is 800 (BALANCE.md §9.4). Rebuilding a whole deck at 20 costs
a draught and three to five thousand bits: a decision, never a restart.

4. **A scar**: every card taken out loses one trait (§7.2).

### 7.2 Scars

- The trait a scar takes is decided by the card's own `scarSeed` and its scar count, and is **shown before you
  confirm** ("Tunneler will lose Echoing (8 %)"). Cancelling and coming back shows the same trait: nothing to fish for.
- A card with no traits left can still be taken out; it gains a scar mark and loses nothing.
- Keywords, rank, rarity and the base perk are never scarred. Mythics are never scarred.
- Scars show on the card (✂ ×2) and in its history, so a buyer knows.

### 7.3 Where: trainers and the Card Shrine

| | Hearth trainer | Card Shrine |
|---|---|---|
| What | The trainer offers "Rebuild your Mining deck · fee" beside the lesson (the 300–399 training band) | A block; the screen asks for the draught and the cards |
| Which skills | the skills that trainer's job teaches (INTEGRATION.md §1.2) | any |
| Which cards | those needing at most the trainer's cap (camp 4, hamlet 6, village 10, town 15, city 20) | any |
| The fee goes to | the trainer's till or purse (`EconomyService.charge(player, settlement, trainer, fee)`) | nobody: it is burned (a coin sink) |
| Scars | as above, except a **city master's** hand scars only one card in two (rolled from the scar seed, shown in the preview) | always |
| Without Hearth | — | the only way; the fee is emeralds |

**Card Shrine** (`mettle:card_shrine`): shaped recipe, a lectern in the middle, 4 amethyst shards, 2 gold ingots and
2 polished deepslate; weighs 6 kg; drawn as a small stone lectern with an open book of card slots. Its screen has the
draught slot, the skill's deck with each card's fee and scar preview, the total, and Confirm. It also **releases**
cards (§7.5).

### 7.4 Free ways out

| Way | Draught | Fee | Scar |
|---|---|---|---|
| **A free swap**: one is earned with each character level, held up to **3** | no | no | no |
| Below character level 5 (the old free-respec rule) | no | no | no |
| A dormant card (slots lost to Unbinding; a Mastery without its side) | no | no | no |
| The **first rebuild of each deck after the v2 migration** (§12) | no | no | no |
| An op (`/mettle cards unslot`) | no | no | no |

A free swap works anywhere, from the Deck screen. Fifty character levels give fifty free swaps over a career: many
early, when mistakes should be cheap, and about one every two hours of play past level 30.

### 7.5 Releasing cards

At a Card Shrine, three binder cards of one skill can be released for **one reroll** (refused while you hold 5).
Released cards leave the world; their ids are never reused. Mythics can't be released. This keeps binders tidy and
gives spare duplicates a use. The binder holds at most **300** cards; a full binder refuses trades and keeps (a draft
waits instead).

### 7.6 The draughts after v2

- **Draught of Forgetting** (one per skill): no longer drunk to refund drafts. It is the key to a rebuild sitting,
  spent by the shrine or the trainer. Its tooltip says so. Drinking one anywhere else is refused with the reason ("Take
  it to a Card Shrine or a Mining trainer").
- **Draught of Unbinding** (attributes): unchanged. Slots lost to lower Wits go dormant (§5.2).
- Op respec and background reset: every card goes back to the binder, unscarred. Free respec below character level 5:
  the same.

---

## 8. Trading

### 8.1 Face to face

- **Start**: sneak and use an empty hand on another player, or `/mettle trade <player>`, or Trade on their name card.
  They get a chip: "Ashford wants to trade · Accept". Both must be within **8 blocks**, alive, not in a menu, and not
  hurt in the last 5 s.
- **The screen** (§13.4): each side offers up to 9 **binder cards** (slotted cards must be rebuilt out first, at full
  cost), up to 9 **item stacks**, and **coin** (Hearth: from carried coin; without Hearth, emeralds go in as items).
- **Locking**: each side presses Lock; any change unlocks both. When both are locked, Confirm starts a **3 s**
  countdown; any change, moving apart past 8 blocks, damage or a disconnect cancels it.
- **The swap** happens in one server-thread step: items and cards were held in escrow while offered (items in a
  temporary container returned on cancel or disconnect; cards in ledger state `TRADE`), coin is charged and paid with
  `EconomyService.charge` / `pay` in the same tick. Both inventories and binders are checked for room first; a swap
  that cannot complete does not start.
- Each card's screen line warns about the requirement and attunement for the receiver: "Needs Mining 12 · Bree has
  Mining 9 · attunes in 20 min".

### 8.2 The card broker (Hearth)

Listing cards for coin at a **card broker** in a town or city is Hearth's feature (it owns markets, residents and
coin). Mettle hands Hearth a small "virtual goods" api: describe, hold, release and transfer a card by id, plus a
display stack Hearth can draw. Hearth owns the listing board, prices, fees and taxes, escrowed coin and paying sellers
who are away. The asks are in [hearth/docs/requests/mettle-cards.md](../../hearth/docs/requests/mettle-cards.md).
NPCs never sell cards of their own; the broker only lists players' cards.

### 8.3 Attunement and history

- A card received by trade or bought at a broker is **attuning** for one in-game day (24,000 server ticks, 20
  minutes): it shows in the binder with a timer and can't be slotted, traded, listed or released until it is done.
  This stops lending a card for one fight and passing it back, and slows flipping.
- Attuned, it is fully the new owner's: it still needs its printed requirement in the new owner's skill, and its
  traits scale with the new owner's attributes.
- History is kept forever on the card: "Drawn by Ashford at Mining 9 · day 41", "Held by 3 · Bree since day 58",
  scars. The last five owners are listed by name.

---

## 9. Mythics

### 9.1 What makes them Mythic

- One named card with one effect nothing else in Mettle has; no rarity, tier, keywords or traits.
- **Earned three ways**: a **world first** (only the first in the world to do it), a **legendary deed** (hard; anyone
  can still earn it, once each), or **drawn** (0.5 % per draft from skill 15, each one once per world).
- **Never sold by NPCs, never destroyed, never dropped on death, never released or scarred.** The only way to lose one
  is to trade it away. Unslotting one costs a free swap or 1,000 bits at a shrine, no draught.
- Takes a deck slot, any deck; at most two slotted, never two in one deck.
- **Public**: the world is told when one is earned ("Ashford has drawn a Mythic: The Twin Edge"), a player's Mythics
  show on their name card and on the hover of their name in chat, and the binder's **Hall of Firsts** page shows every
  world first with its holder (unclaimed ones as silhouettes with their deed) and how many of the eight drawn Mythics
  still wait in the decks.

### 9.2 Credit rules

- A boss's world first goes to the player who dealt the **most damage** in the fight (ties: the killing blow).
- Creative and spectator players never earn one. Mobs from spawn eggs, commands or spawners never count.
- If the world's Ender Dragon was killed before v2 arrived, Dragonfall goes to the first kill of a **respawned**
  dragon. Every other first starts counting when v2 arrives.
- Deed counters are server-side, in the journal. Two claims in the same tick: the earlier event wins; the server is
  single-threaded, so there is always an order.
- Ops can grant, move or reset a Mythic (`/mettle cards mythic …`), logged.

### 9.3 The twenty

**World firsts** (one in the world, ever):

| Mythic | Earned by | Effect |
|---|---|---|
| **Dragonfall** | First to fell the Ender Dragon | Sneak-use any banner to raise the Dragon Standard for 60 s (10 min cooldown): allies within 24 blocks gain **your best active set bonus** as if it were theirs (it doesn't stack with their own of the same keyword). |
| **The Warden's Hush** | First to kill a Warden | You make no vibrations: sculk sensors, shriekers and wardens never hear you. Allies sneaking within 6 blocks of you are hushed too. |
| **Witherbane** | First to kill the Wither | Wither and poison never touch you; allies within 12 blocks take them at half strength for half as long. |
| **The Far Horizon** | First to stand 10,000 blocks from world spawn (Overworld) | **Homeward**: hold a compass and sneak for 5 s to return to your bed or respawn point (once per in-game day). Nothing else in Mettle moves you like this. |
| **The Elder's Eye** | First to kill an Elder Guardian | You and allies within 12 blocks breathe under water, see clearly in it, and mine there at full speed. |
| **The Open Hand** | First to be Honoured in a Hearth city (without Hearth: first Hero of the Village) | Your Hearth market edge (BALANCE.md §9.2) also applies to allies within 8 blocks of you at a counter, and Hearth folk know your name. |
| **First of Masters** | First to bring any skill to 20 | **Mentor**: allies within 16 blocks gain skill XP 20 % faster in every skill where yours is higher than theirs. |

**Legendary deeds** (anyone, once each; each copy is numbered: "the 3rd in this world"):

| Mythic | Earned by | Effect |
|---|---|---|
| **The Unclad** | Kill a Warden wearing no armour | With no armour worn, you take 25 % less damage and move 10 % faster. |
| **Hundredth Dawn** | Live through 100 in-game days without dying | **Pardon**: once every 7 in-game days, when you die you keep everything you carry and your vanilla XP. |
| **The Lone Banner** | Win a raid with no other player within 96 blocks for the whole raid | With no ally within 24 blocks, you deal 15 % more and take 15 % less. |
| **Long Walker** | Travel 100 km on foot (walked, sprinted, swum) | **Trailmaker**: allies within 16 blocks behind you move 10 % faster, and none of you pay hunger to sprint on paths and roads. |
| **Polymath** | Reach 15 in all eight wave-1 skills | +1 deck slot in every skill (it pays for its own). |

**Drawn** (one per skill, once per world, 0.5 % per draft from skill 15):

| Mythic | Skill | Effect |
|---|---|---|
| **Heart of the Mountain** | Mining | Sneak-use a pickaxe on stone to bore a 1 × 2 tunnel 8 blocks deep at once; each block costs durability and drops as normal (30 s cooldown). |
| **The Twin Edge** | Blades | Every fourth blade hit on the same target is struck again by a spectral twin, for full damage. |
| **Worldsplitter** | Heavy Weapons | A fully charged sneak-attack sends a shockwave 8 blocks along the ground: every mob in its line takes half the hit and is thrown up (10 s cooldown; no blocks broken). |
| **The Seeking Fletch** | Marksmanship | Fully drawn arrows bend towards the last mob you hit, within 20°. |
| **Aegis** | Defense | Hold your shield up for 1 s to raise a dome (3 blocks, 4 s): nothing fired from outside passes it, for everyone inside (45 s cooldown). |
| **Seven-League Stride** | Athletics | Double-tap forward while sprinting to dash 6 blocks; the fall after a dash doesn't hurt (8 s cooldown). |
| **The Wild Hunt** | Survival | Up to three wild wolves within 24 blocks join you as a pack while you stay in their biome, and fight what you fight. |
| **The First Hammer** | Smithing | Tools, weapons and armour you craft last 20 % longer and carry your mark in gold; with Arsenal, every part you forge comes out one quality higher (Masterwork stays the top). |

How rare: a character who takes all eight skills from 15 to 20 sees 24 drafts at 0.5 %, about an 11 % chance of one
drawn Mythic, and only if that skill's Mythic is still unclaimed. A busy server sees a handful in its life.

---

## 10. Smithing and VOID Arsenal

Arsenal (`arsenal/docs/DESIGN.md`) makes weapons from forged parts with maker's marks. **Smithing becomes its forging
skill.** The split is the Hearth one: Mettle owns the numbers, Arsenal owns items and stations; neither compiles
against the other's internals.

- **XP**: Arsenal reports forging to `CharacterService.award(player, mettle:smithing, xp, source)`: a shaped part
  25 per metal unit (as a crafted tool today), a quench 5, an assembly 10, a salvage 1 per unit. Vanilla crafting XP
  stays for vanilla items.
- **Strike window and quality**: Arsenal asks Mettle for a player's Smithing numbers. Proposed (Arsenal's §4.4 leaves
  them to Mettle): strike window × (1 + 3 % per Smithing level) × (1 + Steady-handed); steel from Smithing 6;
  Masterwork parts possible from Smithing 12, and twice as likely with the **Masterwork** Mastery slotted.
- **Perks get forging meanings** when Arsenal is loaded: Forged (parts last 25 % longer and carry the mark), Keen Edge
  (+1 damage on blades and heads), True Temper (quench tempers +15 %), Honing (grindstone edges last twice as long),
  Reforge (one more attachment point on a part you forge), Masterwork (above).
  Armour perks (Riveted, Proofed, Light Hand) stay vanilla until Arsenal makes armour.
- **Traits** have both columns (§4.2).
- **The First Hammer** (§9.3) is the Smithing Mythic; Arsenal draws its gold mark.
- **Techniques and Echo**: Arsenal's technique cooldowns read `Cooldowns.scale(player)`, so the Echo set shortens them.
- **History**: a weapon's history (Arsenal §7b) may record the card that shaped it ("forged under Folded Steel"); a
  card's history never records items.

Api additions Mettle makes for this (and for Hearth): `CardService` in `dev.voidmc.mettle.api`:

```java
public interface CardService {
	double trait(ServerPlayer player, Identifier trait);          // effective total, capped and boosted; 0 if none
	int setCount(ServerPlayer player, Identifier keyword);        // slotted cards counting for it
	boolean setActive(ServerPlayer player, Identifier keyword, int tier);
	float cooldownScale(ServerPlayer player);                     // Echo
	List<Identifier> mythics(ServerPlayer player);                // held, for display and world reactions
}
```

with events `CARD_KEPT`, `CARD_SLOTTED`, `CARD_UNSLOTTED`, `CARD_TRADED`, `MYTHIC_EARNED` on `MettleEvents`.

---

## 11. Anti-exploit rules

| Exploit | Rule |
|---|---|
| Duplicating cards | The world ledger is the only truth; ids never repeat; every move is one server-thread step; player files are a cache rebuilt on join (§2.1). |
| Duping through a trade | Escrow while offered; items in a temporary container returned on cancel or disconnect; the swap runs only after room is checked for both sides; a crash mid-trade leaves cards in `TRADE`, and on start-up the ledger returns every `TRADE` card to its owner. |
| Fishing for good rolls | Offers are rolled from the seed and stored; reconnects, restarts and cancelled screens never re-roll. The Mythic roll is per draft, not per reroll. |
| Fishing for a lucky scar | The scarred trait comes from the card's seed and scar count, shown before confirming (§7.2). |
| Lending a card for a fight | Attunement: a received card can't be slotted for 20 minutes, and a slotted card can't be traded without a rebuild. |
| Ping-pong trades to launder history or dodge scars | Attunement blocks re-trading for 20 minutes; scars happen on unslotting, which trading can't skip. |
| A high-level friend arming a new player | Requirements bind every owner; traits scale with the receiver's attributes. |
| Alts drafting for a main | Alts must level the skill like anyone (anti-farm applies); cards they draw are ordinary cards with their name on them. This is trading working as meant. |
| Farming Mythic draws | Skill 15 first, once per draft, each drawn Mythic once per world. |
| Cheesing world firsts | Most damage wins; creative, spawn-egg, command and spawner mobs never count; ops' grants are logged. |
| Binder bloat | 300 cards at most; release three for a reroll. |
| Set stacking | Three active sets; six needs two skills; a pinned choice changes once a day. |
| Stacking a trait | One trait's total from all cards is capped at twice its card cap (§4.1). |
| Slot dodging with Wits | Slots lost to Unbinding go dormant (no bonus, no count) until Wits returns. |
| PvP | `pvp_perks` covers traits, sets and Mythics; ally bonuses never help someone attacking you. |

---

## 12. Old saves (v1 draft → v2)

On join, a character with a `draft` and no `cards` is migrated before any XP counts (a pre-draft save first goes
through DRAFT.md §7, then this):

1. **Each drafted perk held becomes one card** (`how: MIGRATED`), drawn by the player "at" their current skill level:
   a root at rank r becomes the rarity of that rank (I Common, II Rare, III Epic); tier II perks Rare; tier III and
   the Mastery Epic; cursed perks Cursed. They are the colours the player already saw.
2. **Nobody loses a draft**: a root held at rank r gave r drafts and is now one card, so r − 1 waiting drafts come back.
3. **A migrated card needs only its tier's level** (2, 6, 12, 18), so nothing the player had becomes unusable.
4. Traits and the second keyword are rolled fresh by rarity from the player's seed.
5. **Auto-slot**: deepest first, up to the slots; the Mastery only if two of its side fit; the rest go to the binder.
6. Waiting drafts stay; stored v1 hands are dealt again under v2 rules; rerolls stay; three free swaps are given and
   each deck's **first rebuild is free** (§7.4).
7. Earned perks (feats, synergies, teachings, signature) stay learned; they are not cards.

Example: Mining 9 with Quarryman III, Prospector I, Rock Hauler and Tunneler, and one draft waiting (7 drafts in all):
Epic Quarryman III, Common Prospector I, Rare Rock Hauler, Rare Tunneler, all four slotted (Mining 9 has 5 slots), and
three drafts waiting (one, plus two from Quarryman's ranks).

---

## 13. Screens (for the Figma designer)

Design these in the VOID Figma file on page 264:2, a new section **"Cards v2 (explore)"**, before code; never
overwrite v1 frames; mirror what ships on page 295:2. The VOID rules hold (UI.md §1): flat, rounded, no gradients or
glow except the skill-content glow of UI.md §1b; skill hue = which skill; champagne gold = earned (feats, teachings).

**Card face** (one component, three sizes: hand 160 × 224, binder 96 × 134, chip 48 × 64 in Figma px): frame in the
rarity colour, a band of the skill hue, the perk's pixel art, tier pips, keyword badges (14 small glyphs, one colour
each, never the only signal: the name is beside it), traits as name + value lines with the boosted value in Temper
blue, requirement line (red with a lock glyph when you can't slot it), scar notches, id in mono. **Mythic** frame: a
new flat colour (proposal: ivory `#EDE3C8` with a double hairline), full-bleed art, the lore line in italics, no
traits area. Dormant and attuning states: dimmed with a reason chip ("Needs two Delver cards", "Attuning · 14 min").

| # | Screen | Where | What it needs |
|---|---|---|---|
| 13.1 | **Binder** | Classes page, new tab "Cards" | Grid of all owned cards; filters (skill, rarity, keyword, trait, slottable now, attuning, Mythic); sort (newest, rarity, requirement); the selected card large in a fixed side panel with history, owners, scars and actions (Slot, Trade, Release at a shrine); a counter "212 / 300". A page **Hall of Firsts** (world firsts with holders, silhouettes for unclaimed, "6 of 8 drawn Mythics still wait"). |
| 13.2 | **Deck** | each skill's page (beside the tree view, which now shows what the deck can deal and how many of each you own) | The slot row (3–10; locked slots say "Opens at Mining 11" or "Wits 14"); slotted cards; the **set tracker** strip: 14 keywords with counts and 2 / 4 / 6 pips, the three active ones lit, a pin control with "changes once a day"; trait totals against caps ("Deepborn 24 % of 30 %"); free swaps "2 / 3"; a Rebuild hint ("Visit a Mining trainer or a Card Shrine"). |
| 13.3 | **Draft hand** | the existing pick screen | Three rolled cards; Keep / Keep & slot; reroll with the count; a Mythic offer drawn differently (§13.6). |
| 13.4 | **Trade** | its own window over the game | Two columns (you, them): 9 card slots, 9 item slots, a coin field (Hearth) each; per-card warnings (requirement vs the receiver's level, attunement); Lock on each side; Confirm with the 3 s countdown; a clear cancelled state. |
| 13.5 | **Card Shrine** | block screen (trainer: the same panel opened from conversation) | Skill picker, draught slot, the deck with fee and scar preview per card, multi-select, total, Confirm; a Release tab (pick three, get a reroll). Trainer version: no picker, the trainer's cap shown, "a master's hand" on city masters. |
| 13.6 | **Mythic reveal** | full screen, once | The card turns over, its name, lore line, effect, how it was earned ("World first · only one exists" or "The 3rd in this world"); a world chip goes out at the same moment. |
| 13.7 | **Name card** | looking at a player (Hub) and the hover of their name in chat | Name, background, character level, up to two slotted Mythics, Trade button. |
| 13.8 | **Broker** | Hearth's market screen (Hearth designs it) | Draws Mettle's card face from the display stack (§8.2). |

---

## 14. Balance limits

Points are the old perk costs (TREES.md §1), so v1 and v2 compare: a root rank 1, tier II 2, tier III 3, Mastery 5,
cursed 2 or 3. A trait is worth its value ÷ its card cap (a capped trait = 1 point, an average one about 0.55). A set's
2 / 4 / 6 tiers are worth 0.5 / 1.5 / 3 points.

| Limit | Value | Checked by |
|---|---|---|
| One trait on one card | broad ≤ 12 % rolled, ≤ 15 % boosted; narrow ≤ 25 % / 30 % | trait data test |
| One trait across all cards | ≤ 2 × its card cap | `CardRulesTest` |
| Slotted power per skill at 20, base slots | ≤ 1.15 × v1's draft (28 points, DRAFT.md §6) | `CardReportTest` |
| … with Wits 14 (+2 slots) | ≤ 1.3 × v1 | `CardReportTest` |
| Sets, whole character | 3 active × 5 points = 15 points at most (about 7 % on top of eight skills at 20) | rule |
| Mythics | 2 slotted, each sized to an Epic Mastery with traits (about 6 points) | review |
| Whole-character ceilings from Mettle (perks + traits + sets + Mythics) | movement +35 %, melee damage +60 %, damage taken −45 %, dig speed +120 %, extra-drop chance +50 % per drop | `CardReportTest`, worst-case deck search |

Estimated (to be confirmed by the simulator): at skill 20 the best eight of thirteen cards hold about 24 points of
perks plus about 7 of traits, 31 against v1's 28 (1.1×); with Wits 14 about 36 (1.3×).

**What the simulator must check** (`CardReportTest`, extending `DraftReportTest`; `-Dmettle.balance=true` →
`build/reports/mettle-cards.md`), 3,000 careers per skill, a player who keeps the card with the most points and slots
the best legal deck:

1. Cards owned, slots, slotted points and traits at skill 2, 6, 8, 10, 12, 14, 16, 18, 20, against v1 (§14 limits).
2. Trait totals never above their caps; whole-character ceilings never passed by the best possible deck (a search over
   each skill's top cards, worst case, not average).
3. **Distinct builds**: mean overlap (Jaccard of perk + trait pairs) between two careers' Mining decks at 20 below 0.3;
   two identical decks less than 1 in 10,000.
4. Sets: at which character level a career first reaches 2 / 4 / 6 in its best keyword; how many keywords can reach
   6 at all by 20 in each skill mix (no keyword should be unreachable; Tide and Kin should be the hardest).
5. Requirements: how long an early Epic waits (mean levels between draw and slot).
6. Mythic draws per maxed character and per 20-player server life.
7. Rebuild cost: a full deck rebuild at 10, 15 and 20 in bits and in hours of Hearth income (BALANCE.md §9).
8. Migration: every v1 career from `DraftReportTest` migrates to a v2 deck with the same or more points.

---

## 15. Config (`config/mettle/cards.json`)

| Field | Default |
|---|---|
| `slots_base` / `slots_every` / `slots_max` | 3 / 3 / 8 |
| `wits_slots` | `[8, 14]` |
| `rarity_odds` | Common 70 − 1.5 L, Rare 24 + L, Epic 6 + 0.5 L |
| `duplicate_weight` / `duplicate_floor` | 0.5 / 0.25 |
| `trait_boost_per_point` | 0.015 |
| `trait_total_cap` | 2.0 (× the card cap) |
| `active_sets` / `set_pin_cooldown_days` | 3 / 1 |
| `rebuild_fees` | 40 / 120 / 350 / 900 bits; Mythic 1,000; emeralds 1 / 3 / 8 / 20 / 24 |
| `city_master_scar_chance` | 0.5 |
| `free_swaps_max` | 3 |
| `attune_ticks` | 24,000 |
| `trade_range` / `trade_countdown_s` | 8 / 3 |
| `binder_max` / `release_per_reroll` | 300 / 3 |
| `mythic_draw_chance` / `mythic_draw_from` / `mythic_slots` | 0.005 / 15 / 2 |
| `far_horizon_blocks` | 10,000 |

---

## 16. Build plan

Each wave: Figma frames first for its screens, then code, then the tour. Module: **character** (`character.cards`:
`CardRules` pure, `CardLedger`, `Decks`, `Rebuilds`, `Trades`, `Mythics`, `CardCommands`), **ui**/Hub for screens,
**compat** for Hearth and Arsenal.

| Wave | Contents | Done when |
|---|---|---|
| **A · Cards** | Ledger and ids; trait, keyword registries and the eight pools as data; rolling (perk, rarity, rank, traits, keywords, requirement); offers in hands; the binder (virtual) and decks with slots, Wits slots, the slot rules; `Perks.has` reads decks; attribute-modifier traits through `EffectsApplier`; set counting with the 2 and 4 tiers that are attribute modifiers; v2 migration; `/mettle cards` (list, show, slot, give, roll); overlay state for binder and deck; `CardReportTest`. | A fresh character and a migrated one both play a session with a deck; the simulator report meets §14. |
| **B · Rebuild and sets** | Card Shrine block, recipe and screen; trainer "Rebuild" option; draught as the key; fees; scars with preview; free swaps; release for rerolls; dormant cards; behaviour traits; every set tier including the 6s; active-set pinning. | Client tour: rebuild a Mining deck at a shrine and at a Hearth trainer, with screenshots. |
| **C · Trading** | Trade requests, the trade screen, escrow, coin via Hearth, attunement, history and owners; crash recovery of `TRADE` cards. | A two-player game test swaps cards, items and coin; cancels and disconnects lose nothing. |
| **D · Mythics** | The twenty: triggers, credit, Hall of Firsts, the draw roll, effects, reveal, world chip, name card and chat hover, public sync. | Each Mythic is granted and checked in the tour (op-granted where the deed is too long). |
| **E · Partners** | Hearth card broker (after Hearth's requests land), The Open Hand's Hearth trigger, Arsenal's Smithing hooks (after Arsenal wave 1), `CardService` api; balance pass with the user. | Works with Hearth and Arsenal present and absent. |

### Tests

**JUnit (`CardRulesTest`, pure):**
- Offers are deterministic from seed and deal count; rerolls never change the Mythic slot.
- Rarity odds and trait counts by rarity match §3 over 100,000 rolls (within 1 %); values stay in their rarity's band
  and round to the step; no trait twice on a card; second keyword never equals the first; cursed cards always carry
  Hex.
- Requirement table (§3.3), migrated cards' tier-only requirement.
- Slot curve and Wits thresholds; one card per perk; one Mastery per deck and dormancy without its side; Mythic limits.
- Set counting across skills; the 6 tier refused from one skill; three active, auto order and the once-a-day pin.
- Trait boost by attribute; caps per card and per trait total.
- Scar preview equals the scar taken; no-trait cards; city master halves; Mythics never scarred.
- Free swap bank caps at 3; fees by requirement; free cases of §7.4.
- Migration: the Mining 9 example; every rank turns into a card or a returned draft; nothing lost.
- Ledger: ids unique across 1,000,000 creations; a cache that disagrees with the ledger is corrected both ways;
  `TRADE` cards return on start-up.
- Trade validation: range, room, slotted cards refused, attuning cards refused, binder full.
- Mythic claims: one world first per world, same-tick order, creative and spawned mobs refused; drawn Mythic once per
  world; deed copies numbered.

**Game tests (server):** slotted traits and sets apply and clear; survive death, relog and dimension change; Wits loss
makes slots dormant; a two-player trade with items and cards; a disconnect mid-countdown returns everything.

**Client tour (screenshots):** the hand with rolled cards, the binder, a deck with sets, the shrine with a scar
preview, the trainer rebuild, the trade screen both sides, a Mythic reveal, a name card.

**Simulator:** `CardReportTest` (§14), and `DraftReportTest` still passes for the draft cadence.

---

## 17. Decisions (confirmed by the user, 2026-10-10: "I agree with all of your decisions")

1. Rarity is rolled apart from depth; the v1 rarity odds become depth odds; new rarity odds Common 70 − 1.5 L, Rare
   24 + L, Epic 6 + 0.5 L.
2. A root's rank is its rarity (I / II / III); duplicates are new cards; one card per perk per deck.
3. A card's requirement grows with rarity (Epic root at 7, Epic Mastery at 20); migrated cards need only their tier.
4. Cursed cards roll 1–2 traits at full range and always carry Hex plus a second keyword.
5. Traits are standalone effects; values roll low / whole / high by rarity; attribute boost +1.5 % per point (×1.255
   at 20); one trait's total capped at twice its card cap.
6. Strength, Agility, Vitality, Presence boost might, finesse, endurance, fortune-and-fellowship traits; Wits gives
   +1 slot at 8 and +2 at 14.
7. Slots 3 at skill 2, +1 every three levels, 8 at 17; slotting free anywhere.
8. Fourteen keywords; three active sets; six needs two skills; pinning once a day.
9. A rebuild: one draught per skill per sitting, a fee per card (40 / 120 / 350 / 900 bits; emeralds without Hearth),
   a scar per card; trainers pay the trainer and cap by tier, a city master scars half as often; the shrine burns the
   fee.
10. Free swap = no draught, fee or scar; banked up to 3.
11. Draught of Forgetting no longer refunds drafts.
12. Attunement is 20 minutes; trading needs 8 blocks, Lock, a 3 s countdown.
13. Mythics: 7 world firsts, 5 deeds (once per player), 8 drawn (once per world); two slotted; unslotting costs a free
    swap or 1,000 bits.
14. Binder cap 300; release three for a reroll.

## 18. Answered (user, 2026-10-10)

1. **Fusion: yes.** Three copies of a perk fuse into one card of the next rarity at a Card Shrine (or a trainer): the
   new card rolls fresh traits at its rarity, keeps the best trait of the three, and records "Fused from three". Epic
   triples do not fuse (Epic is the top rarity; Mythics are never made). Design it in the wave that builds the shrine.
2. **Cards stay in their world.** The ledger is per world/server; nothing travels.
3. **Mythic frame: white-gold**, as designed in Figma (section 2f, M1–M4). Keyword glyphs: one neutral colour.
4. **Earned perks stay always on**, outside the deck (rewards for deeds, not build choices).
5. **Reroll supply**: keep "release three for a reroll"; watch it in play and tune `RELEASE_FOR_REROLL`.
6. **The Far Horizon** keeps Homeward (once a day) for now.

## 19. Rulings (coordinator, for the Figma designer's questions, 2026-10-10)

1. **Card look: direction D.** A pixel edge burning in the skill colour; rarity shows in the gem and in how much of the
   edge burns. It replaces the flat "rarity frame + skill band" of §13.
2. **Fusion cost** = the resulting card's rebuild fee for its requirement band (§7.1); no draught, no scar.
3. **Fusion's "best trait"** = the trait with the highest share of its cap among the three inputs; it is kept at its
   old rolled value (not rerolled).
4. **A slotted card can't be fused or released directly**: unslot it first (a rebuild or a free swap).
5. **Release happens at a Card Shrine**, but the binder shows a Release action that explains "at a Card Shrine" when
   the player isn't at one; the overlay state says whether the player is at a shrine (`cards.atShrine`).
