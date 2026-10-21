# Requests from VOID Mettle: Speech, Stealth, bynames

**Status: reviewed by the Hearth session 2026-10-21: all three fit, with the adjustments written in below. Hearth builds
12–14 (and 7–11) after its current living-world batch merges.**

VOID Mettle is releasing **Stealth** and **Speech** as full skills (`mettle/docs/BUILD_FEEL.md` §14 on branch
`mettle-build-feel`): trees of perks, rolled traits and cards, as its eight released skills have. These requests follow
`mettle.md` (1–6, done) and `mettle-cards.md` (7–11) and keep the split agreed with the user on 2026-10-03: **Mettle
owns the numbers; Hearth owns the minigames, the odds rules, witnesses and how the world reacts.** Mettle calls the api
only, from `compat.hearth`, only when Hearth is loaded.

**Nothing here blocks either mod.** Mettle builds Speech and Stealth on the slots that exist today:

- `PlayerSkills.level` answers an **effective** Speech and Stealth (the level plus perks, traits and Teachings that say
  "counts N higher wherever Hearth reads it", at most 20), and `attribute("presence")` likewise.
- `PriceModifier` (Mettle's `MettlePrices`) for item-kind prices ("food is cheaper", "gear sells dearer").
- `EconomyService.price(player, settlement, stack, playerBuys)` for a perk that shows market values in tooltips.

Until 12–14 ship, each Mettle perk that needs them uses a fallback through those slots (BUILD_FEEL.md §14). Mettle
checks once at init whether the running Hearth's `PlayerSkills` declares `bonus`.

**One channel per effect.** A Mettle effect either raises the effective level (`level()` / `attribute()`), answers a
bonus key, or is a price factor, never two of them for the same thing; Mettle's audit table is in BUILD_FEEL.md §14 and
a Mettle test enforces it. Hearth keeps every clamp (odds 5–95 %, band and patience caps).

## 12. Named bonuses at Hearth's decision points (accepted)

An additive **default** method on `PlayerSkills` (Mettle's `src/hearthApi` copy gets the same default):

```java
/** A character mod's bonus at one of Hearth's decision points (keys below); 0 when unknown or without a skills mod. */
default double bonus(ServerPlayer player, String key) { return 0; }
```

| Key | Hearth applies it in | Meaning | Mettle sources, largest values |
|---|---|---|---|
| `hearth:haggle/band` | `Haggle.band` | Speech levels added only for reading the limit band | Shrewd Eye 5, Shrewd trait 1–3 |
| `hearth:haggle/patience` | `Temper.patience` (Hearth passes the player in) | share added to a merchant's patience | Patient trait 5–15 % |
| `hearth:haggle/never_sour` | walking away from a haggle | 1 = the merchant is never sour | Merchant Prince |
| `hearth:speech/persuade`, `…/intimidate`, `…/bribe`, `…/flatter` | `SpeechCheck`, per kind | odds added before Hearth's clamp | Smooth 3–8 % (persuade, flatter), Imposing 3–8 % (intimidate) |
| `hearth:speech/retry` | `SpeechCheck` | failed checks that may be tried again at once, per sim day | Second Word 1 |
| `hearth:standing/gain` | reputation changes, **positive deltas only** | share added | Charmer 25 %, Charming 5–12 % |
| `hearth:stealth/sight` | witnesses and `Detection` | share off how far a sneaking player is seen (on top of Stealth's) | Cloak of Dusk 25 % at light ≤ 7, Unseen 5–12 %, The Quiet Way 10 % |
| `hearth:stealth/unseen_beyond` | witnesses and `Detection` | blocks beyond which a sneaking player is not seen at all; 0 = off (a rule, not a share) | Ghost 4 (sneaking, light ≤ 7, not attacking) |
| `hearth:stealth/give_up` | a guard's search timer | share off how long a guard searches after losing the player | The Quiet Way 33 % |
| `hearth:pickpocket/awareness` | the pickpocket mark | share off how fast the mark notices | Light-fingered 5–12 % |
| `hearth:greet/hero` | greetings | 1 = folk greet the player as a hero; flavour only, standing and the per-town HERO title untouched | Hero's Welcome |

## 13. A speech-check event (accepted, changed from the original ask)

Award reasons stay exactly as they are. Instead Hearth adds an api event:

```java
StandingEvents.SPEECH_CHECK  (ServerPlayer player, @Nullable Settlement settlement, String kind, boolean passed, int difficulty)
```

and the same for the law's checks (guards and witnesses: persuade, hush, threaten). Mettle listens to both: Speech's
feat **Golden Mouth** counts checks *won*. Until they exist, Mettle counts checks tried from the award reasons.
Mettle's `src/hearthApi` copy gets `StandingEvents` with both events.

## 14. A worn title (accepted)

An additive default on `PlayerSkills`, beside request 10's `titles(player)` (still open on Hearth's side):

```java
/** The title the player wears ("the Deep Ember Warden", "Long Walker", "the Legend of Ashmoor"); empty when none. */
default Component title(ServerPlayer player) { return Component.empty(); }
```

Mettle answers the worn byname, deed title or legend title (BUILD_FEEL.md §2.1, §5.3, §12). Folk greeting a player by
it is Hearth's to word, or to skip.
