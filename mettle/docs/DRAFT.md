# VOID Mettle — the perk draft

**Decision (user, 2026-10-10):** levelling should be fun and random, not a planned route through a tree. Perks are no
longer bought with points from a tree; every skill level deals a **draft**: a hand of cards from that skill's deck, keep
one. Supersedes "points buy perks" in TREES.md §1 and CHARACTER.md §4. Earned perks (feats, synergies, teachings) and
attribute points are unchanged.

> **Cards v2 ([CARDS.md](CARDS.md), wave A built 2026-10-10).** The draft stays the source of cards, at the same
> cadence, but a kept card becomes a unique **rolled card** (perk, rolled rarity, keywords, traits) in a binder, and
> only cards **slotted** in a skill's deck are active. What changes here: rarity is rolled apart from depth (§3), ranks
> and duplicates (§2), the spent deck goes (§1), the budget (§6), migration (§7), respec becomes rebuilding (§8), and a
> Mythic roll joins the draft (§9). Each section below says so in one line.

Code: `character.draft` (`DraftRules` pure and tested, `Drafts` on the server, `Cursed`, `DraftCommands`),
`core.DraftState` (the synced record), overlay fields in `client.overlay.MettleState` / `MettleActions` (OVERLAY.md).
Checks: `DraftRulesTest` (19 tests), `DraftJourney` in the client tour, and the simulator `DraftReportTest`
(`./gradlew test --tests '*DraftReportTest' -Dmettle.balance=true` → `build/reports/mettle-draft.md`).

A player has to know four things:

1. **A level deals a draft.** Three cards from the skill's deck; keep one. Drafts you don't take wait (a badge counts
   them) and never expire.
2. **Cards have a rarity.** Common, Rare, Epic, and **Cursed**: strong, with a real price.
3. **Rerolls** deal a fresh hand. You hold a few and earn more.
4. **Sometimes a wildcard** from another skill you used lately turns up in the hand.

---

## 1. Drafts

- **When.** Every skill level from 1 to 6 deals one draft; from 7 on, every **even** level deals a draft and every odd
  level brings a **reroll** instead (§6 says why). Thirteen drafts from 0 to 20. `DraftRules.HALF_RATE_FROM` (7) is the
  lever; `Integer.MAX_VALUE` gives a draft on every level.
- **The hand** is dealt at once and stored in the character record, so a reconnect shows the same cards. One hand per
  skill at a time; keeping a card deals that skill's next hand if more drafts wait.
- **Waiting.** A draft with nothing to deal (Mining 1: tier I opens at 2) waits until the skill grows. A draft never
  expires.
- **A spent deck.** When every card of a skill is at its last rank (and a Mastery is held), its waiting drafts turn into
  rerolls, with a chip ("Your Mining deck is spent · 2 drafts became rerolls"). A skill with no deck at all (wave 2:
  Speech, Stealth…, which Hearth already levels) keeps its drafts for when its tree ships.
  *v2: gone. Owning a perk never removes it from the deck; a duplicate is a new rolled card (CARDS.md §3.1).*
- **Stale cards.** If a card in a hand stops being good (its rank was learned another way, another Mastery was taken),
  the hand is dealt again for free; keeping a stale card is refused the same way.

## 2. The deck

*v2: "learned" reads "owned" in every rule below; the Maxed rule goes (a root's rank is its rarity, and a perk you own
is dealt at half weight per copy); you may own all three Masteries but slot one (CARDS.md §3.1, §5.2).*

A skill's deck is its tree: every drafted perk (`Perk.Kind` root, path, mastery, cursed), as "perk X, next rank".

| Rule | Cards |
|---|---|
| Skill gate (hard) | tier I at skill 2, tier II at 6, tier III at 12, Masteries at 18 (`Curves.tierSkillGate`) |
| Path order (hard) | a tier III path card needs a tier II perk of its own path |
| Masteries (hard) | a Mastery card needs two perks of its side (any two for the middle one); holding one Mastery removes the other two from the deck for good |
| Maxed (hard) | a card is never a rank you already have; a ranked root comes back at its next rank until its last |
| No repeats | a hand never holds the same perk twice |
| Attribute gate (**soft**) | below the tier's attribute (II 6, III 10, IV 14) a card is dealt **0.4×** as often (`UNDER_GATE_WEIGHT`) |

**Why soft attribute gates.** BALANCE.md §7.3 showed the attribute gate never held anyone back in play (Strength 10
arrives hours before Mining 12), and a hard gate in a random draw would only shrink hands. Soft, it still means
something: a Strength miner sees Delver and Excavator tier III cards two and a half times as often as a miner who put
nothing into Strength. It cannot break the budget, because how *many* cards you keep is set by the draft count, not the
gate.

## 3. Rarity and odds

*v2: the table below becomes the **depth odds** (which perk is drawn); each card's rarity is then rolled on its own
table, Common 70 − 1.5 L, Rare 24 + L, Epic 6 + 0.5 L, and sets its rank, trait count and requirement (CARDS.md
§3.2–§3.4). Cursed perks stay Cursed.*

Rarity follows depth: **roots Common, tier II path perks Rare, tier III and Masteries Epic, cursed cards Cursed.** Frame
colours (`DraftState.Rarity`): Common `#B9BEC6`, Rare `#4DA3F5`, Epic `#B27CFF`, Cursed `#D8453B`.

Each slot draws one card: a rarity's odds are split over its cards in the deck now, times the soft attribute gate. A
rarity with nothing left hands its share to the others in proportion. Higher skill levels shift the table upward:

| Skill level | Common | Rare | Epic | Cursed |
|---|---|---|---|---|
| 0 | 62 % | 26 % | 8 % | 4 % |
| 5 | 54.5 % | 30 % | 11 % | 4.5 % |
| 10 | 47 % | 34 % | 14 % | 5 % |
| 15 | 39.5 % | 38 % | 17 % | 5.5 % |
| 20 | 32 % | 42 % | 20 % | 6 % |

(Common 62 − 1.5 L, Rare 26 + 0.8 L, Epic 8 + 0.6 L, Cursed 4 + 0.1 L.) The overlay gets each skill's live odds
(`skills[].odds`, after empty rarities and the attribute gate), so a card frame legend can say "Epic 14 %".

## 4. Cursed cards

Two per released skill, sixteen in all: a strong effect with a real downside, tier II (skill 6) and tier III (skill 12).
Taking one is a choice: a skill has only two, so a hand holds at most two of its own cursed cards. Each has `downside` text in
its data. Ten are pure attribute modifiers (both halves in the perk JSON); six need code (`character.draft.Cursed`).

| Skill | Card | Tier | Gives | Costs | Built as |
|---|---|---|---|---|---|
| Mining | **Greedy Pick** | II | dig 30 % faster | one ore in eight crumbles to its stone (cobblestone, cobbled deepslate, netherrack) and drops nothing else | attr + drop hook |
| Mining | **Black Lung** | III | +1 block reach, dig 25 % faster | two fewer hearts | attr |
| Blades | **Reckless Edge** | II | +20 % melee damage | −4 armour | attr |
| Blades | **Glass Dancer** | III | attack 20 % faster, move 10 % faster | three fewer hearts | attr |
| Heavy Weapons | **Bloodrage** | II | +30 % melee damage | attack 20 % slower | attr |
| Heavy Weapons | **Immovable** | III | knockback resistance +75 %, +1 attack knockback | move 12 % slower | attr |
| Marksmanship | **Heavy Draw** | II | arrows hit 35 % harder | bows take a third longer to draw | draw + hit hooks |
| Marksmanship | **Snap Shot** | III | bows draw twice as fast | arrows deal 30 % less | draw + hit hooks |
| Defense | **Iron Hide** | II | +4 armour | move 10 % slower | attr |
| Defense | **Bastion** | III | +3 armour toughness, knockback resistance +50 % | attack 20 % slower | attr |
| Athletics | **Featherweight** | II | move 15 % faster | carry 20 kg less | attr |
| Athletics | **Bull's Back** | III | carry 30 kg more | falls hurt half again as much | attr |
| Survival | **Wolf's Hunger** | II | two more hearts | hungry 40 % faster | attr + exhaustion hook |
| Survival | **Wild Luck** | III | Luck +3 (fishing, loot chests) | two fewer hearts | attr |
| Smithing | **Brittle Edge** | II | weapons you craft deal +2 damage | they last half as long | craft stamp |
| Smithing | **Slapdash** | III | Smithing XP +60 % | what you craft lasts a quarter less | XP + craft stamp |

Cursed perks are refunded by a Draught of Forgetting like any drafted perk. Their icons (`tools/icons/cursed.py`) are a
stand-in set: each is its sibling perk's icon recast in the curse palette with a crimson brand; the draft UI pass may
draw them properly.

## 5. Wildcards and rerolls

**Wildcards.** With a **12 %** chance (`WILDCARD_CHANCE`) one slot of a hand (the last) is a card from another skill the
player used lately: the character record keeps the last four skills that gained XP (`DraftState.recent`), and one of
them with something to deal is chosen. A wildcard obeys its own skill's gates and is marked `wildcard`; keeping it uses
up a draft of the skill whose hand it was in.

**Rerolls** are one pool for every skill. Spending one deals the whole hand again; the new hand avoids the cards just
thrown back while others are left.

| Source | Rerolls |
|---|---|
| A new character, or an old save on migration | 2 |
| Each character level | +1 |
| An odd skill level from 7 (a level that deals no draft) | +1 |
| Each feat earned | +1 |
| Each Hearth trainer's lesson (`compat.hearth.Trainers`) | +1 |
| A spent deck's waiting drafts | +1 each |
| Held at most | **5** (`MAX_REROLLS`); more are lost, so they stay worth spending |

*v2: one more source, releasing three binder cards of a skill at a Card Shrine (CARDS.md §7.5); a spent deck no longer
gives any.*

There is no reroll draught: a draught recipe is the next step if rerolls turn out too scarce, and the trainer hook shows
the pattern for any other source.

## 6. Balance: the power budget

*v2: the draft count is unchanged (13 to skill 20), but only the slotted cards count (8 at skill 20, 10 with Wits 14),
plus traits and sets. Limits and the new simulator `CardReportTest` are in CARDS.md §14.*

The old trees gave one point per level and charged 1 / 2 / 3 / 5 per perk by depth, so 20 points bought about two
thirds of a 31-point tree (BALANCE.md §7.3). A card costs one draft whatever its depth, and a skill's deck holds 17
ranks (roots 6, paths 8, one Mastery, two cursed). The simulator (`DraftReportTest`, 3,000 careers per skill, a player
who keeps the rarest card) prices both schedules against the old trees bought the cheapest way:

| Skill level | 6 | 8 | 10 | 12 | 14 | 16 | 18 | 20 |
|---|---|---|---|---|---|---|---|---|
| Old trees: ranks held · depth (points) | 6 · 6 | 7 · 8 | 8 · 10 | 9 · 12 | 10 · 14 | 10 · 14 | 11 · 17 | 12 · 20 |
| **Draft, the default** (every level to 6, then even levels) | 6 · 7 | 7 · 9 | 8 · 11 | 9 · 14 | 10 · 17 | 11 · 20 | 12 · 24 | 13 · 28 |
| Draft on every level (lever off) | 6 · 7 | 8 · 11 | 10 · 15 | 12 · 19 | 14 · 25 | 16 · 31 | 17 · 36 | 17 · 36 |

On every level the draft hands over **the whole deck by skill 18**: everyone ends with every perk but two Masteries, at
1.8 times the old depth, and builds stop differing. The default keeps the ranks within one of the old budget all the way
and the depth within a third (cards are dealt by rarity, so the cheap roots are not always what you keep); 99 % of
careers still hold a Mastery by 20 (mean skill 18.5), a cursed card is kept in about one career in four, and four cards
of seventeen are never taken, so two miners at 20 still differ.

Pacing does not change: XP, curves and the tier gates are as in BALANCE.md §1–§3. A new skill deals its first hand at
level 2 (in the first five minutes), the first tier II card turns up at 6.2 on average, and with drafts at every level to
6 the first hours feel as generous as before.

## 7. Old saves (migration)

*v2: a v1 save migrates once more: each drafted perk becomes one card, a rank-r root gives back r − 1 drafts, migrated
cards need only their tier's level, and the first rebuild of each deck is free (CARDS.md §12).*

A character saved before drafts has no `draft` in its record. On join (and before any XP is counted) the server
migrates it: **perks already learned stay learned; each skill's unspent perk points become that many waiting drafts**
(points = level − the old cost of its bought perks), the seed is made, two rerolls are given and the hands are dealt.
Mining 9 with Quarryman III and Tunneler (5 points spent) gets 4 drafts.

## 8. Respec

*v2: replaced. A Draught of Forgetting no longer refunds drafts: it opens one skill's deck for a **rebuild** at a Hearth
trainer or a Card Shrine, with a fee per card, and every card taken out loses a trait (CARDS.md §7). Free below
character level 5, op respec and background reset return cards to the binder unscarred.*

- **Draught of Forgetting (one skill):** every drafted perk of that skill (roots, paths, cursed cards, the Mastery) is
  forgotten, and **each rank comes back as a waiting draft of that skill** ("· 8 drafts returned"). A hand is dealt at
  once. Earned perks, XP and other skills are untouched; draughts and recipes are unchanged (BALANCE.md §8).
- **Free respec below character level 5, op respec, background reset:** every drafted perk, each rank back as a draft of
  its own skill.
- An old save's perks bought with points come back one draft per rank, not per point: the draft prices every card the
  same, and returning points would let a deep perk be traded for three cards.

## 9. Determinism and fairness

*v2: the same, for every roll (traits, keywords, values); offers enter the world ledger with an id only when kept; the
0.5 % Mythic roll from skill 15 is made once per draft, never per reroll (CARDS.md §3.5, §11).*

- Everything is decided on the server. The client sends `pick_card (skill, index)` and `reroll (skill)`; the server
  checks the draft, the card and the reroll count, and answers with a chip either way.
- Hands come from the player's own seed (from their UUID) and a count of hands dealt, and every list is sorted by id, so
  the same character always gets the same hand (`DraftRulesTest.dealingIsDeterministic`). The hand is stored, so a
  reconnect or a server restart never re-deals it.
- Never a duplicate in a hand, never a maxed perk, never a second Mastery.

## 10. Playing it before the overlay

`/mettle draft` lists every waiting draft and its hand in chat, rarity-coloured; click a card to keep it, or the reroll
line. `/mettle draft pick <skill> <1–3>`, `/mettle draft reroll <skill>`. Ops: `/mettle draft give <skill> <n>
[players]`, `/mettle draft rerolls <n> [players]`, and `/mettle perk learn <perk>` (now op-only: learns a rank without a
draft, for tests and fixing characters). `/mettle tree <skill>` marks what is in your deck now.
