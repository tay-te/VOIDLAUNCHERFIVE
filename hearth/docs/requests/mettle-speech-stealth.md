# Requests from VOID Mettle: Speech, Stealth, bynames

VOID Mettle is releasing **Stealth** and **Speech** as full skills (`mettle/docs/BUILD_FEEL.md` §14 on branch
`mettle-build-feel`): trees of perks, rolled traits and cards, as its eight released skills have. These requests follow
`mettle.md` (1–6, done) and `mettle-cards.md` (7–11) and keep the split agreed with the user on 2026-10-03: **Mettle
owns the numbers; Hearth owns the minigames, the odds rules, witnesses and how the world reacts.** Mettle calls the api
only, from `compat.hearth`, only when Hearth is loaded.

**Nothing here blocks either mod.** Mettle builds Speech and Stealth on the slots that exist today:

- `PlayerSkills.level` answers an **effective** Speech and Stealth (the level plus perks, traits and Teachings that say
  "counts N higher wherever Hearth reads it", at most 20), and `attribute("presence")` likewise. Every place Hearth
  already reads them (speech checks, haggling room, patience and the limit band from Speech 5 to 15, `LawSkills`,
  `Detection.Stance.stealth`, bandits) gets them unchanged in form.
- `PriceModifier` (Mettle's `MettlePrices`) for item-kind prices ("food is cheaper", "gear sells dearer").
- `EconomyService.price(player, settlement, stack, playerBuys)` for a perk that shows market values in tooltips.
- `award` reasons for feats (`hearth:speech/<kind>`, `hearth:economy/haggle`, `hearth:law/unseen/<offence>`).

Each perk that would need more names a fallback through those slots, so it works (more bluntly) until Hearth answers.

## 12. Named bonuses Hearth reads at its own decision points

**What.** One method on `PlayerSkills`, neutral by default:

```java
/**
 * A character mod's bonus at one of Hearth's decision points (keys below); 0 when unknown or without a skills mod.
 * Hearth decides how each key enters its own rule and clamps it there. Server thread.
 */
default double bonus(ServerPlayer player, String key) { return 0; }
```

Keys Mettle would answer (Hearth may rename or drop any; unknown keys answer 0):

| Key | Meaning (Hearth applies it) | Mettle sources, largest values |
|---|---|---|
| `hearth:haggle/band` | Speech levels added only for reading a merchant's limit band (`Haggle.band`) | Shrewd Eye 5, Shrewd trait 1–3 |
| `hearth:haggle/patience` | share added to a merchant's patience (`Temper.patience`) | Patient trait 5–15 % |
| `hearth:haggle/never_sour` | 1 when walking away never sours a merchant | Merchant Prince |
| `hearth:speech/persuade`, `…/flatter`, `…/intimidate`, `…/bribe` | odds added to that check (before Hearth's clamp) | Smooth 3–8 %, Imposing 3–8 % |
| `hearth:speech/retry` | checks per sim day that may be tried again at once after a failure | Second Word 1 |
| `hearth:standing/gain` | share added to reputation the player gains | Charmer 25 %, Charming 5–12 % |
| `hearth:stealth/sight` | share taken off how far folk see a sneaking player (on top of Stealth's) | Cloak of Dusk 25 % at light ≤ 7, Unseen 5–12 %, Ghost: beyond 4 blocks unseen |
| `hearth:stealth/give_up` | share taken off how long a guard searches after losing the player | The Quiet Way 33 % |
| `hearth:pickpocket/awareness` | share taken off how fast the mark notices | Light-fingered 5–12 % |

**Why.** Effective levels move everything at once; a few perks and traits should move one thing (the haggle band, one
kind of check, how far a witness sees). One generic slot keeps the api small and lets Hearth keep its rules and
clamps; Mettle documents its keys and values in BUILD_FEEL.md §14.

## 13. The outcome in speech-check award reasons

**What.** Speech-check reasons carry the outcome: `hearth:speech/persuade/passed` and `…/failed` (the same for law
checks: `hearth:law/<what>/passed`). Or an api event `StandingEvents.SPEECH_CHECK (player, settlement, kind, passed,
difficulty)`, whichever Hearth prefers.

**Why.** Speech's feat **Golden Mouth** counts checks *won*. The XP amounts differ (a third on failure) but can't be
told apart reliably. Until then Mettle counts checks tried.

## 14. A worn title for greetings (extends request 10)

**What.** Beside request 10's `titles(player)` (the Mythics held), one more default method:

```java
/** The title the player wears ("the Deep Ember Warden", "Long Walker", "the Legend of Ashmoor"); empty when none. */
default Component title(ServerPlayer player) { return Component.empty(); }
```

**Why.** Mettle now gives every character a **byname** that grows with their build, and **deed titles** and a cosmetic
**legend title** (BUILD_FEEL.md §2.1, §5.3, §12). Folk greeting a player by it ("Well met, Deep Ember Warden"), and
Speech's mastery **Hero's Welcome** ("folk greet you as a hero") are Hearth's to word, or to skip.
