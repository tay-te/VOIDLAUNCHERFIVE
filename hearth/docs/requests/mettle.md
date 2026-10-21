# Requests from VOID Mettle

VOID Mettle (`mettle/`, branch `mettle`) is the classes, skills and weight mod: backgrounds, attributes, learn-by-doing
skills with perk trees, and inventory weight with backpack, belt and quiver slots. Its design is in
`mettle/docs/` (start with ARCHITECTURE.md; the Hearth side is INTEGRATION.md §1).

**The split, agreed with the user on 2026-10-03:** Mettle provides the numbers (skill levels, perks, attributes,
check odds). Hearth owns how the world reacts (prices, standing, opinion, law, the lockpicking and pickpocketing
minigames, witnesses, crime). Mettle never compiles against Hearth internals; it uses the api only, from its own
`compat.hearth` package, and only when Hearth is loaded.

None of these block Hearth's own work. Mettle's Hearth features wait for them.

**Status: all six done** (Hearth branch `hearth-w7-mettle`, 2026-10-07); 7-14 done 2026-10-21 (below). Everything Mettle implements or listens to is
in `dev.voidmc.hearth.api`: `PlayerSkills` (+ `HearthServices.setPlayerSkills`), `PriceModifier` (+
`EconomyService.addPriceModifier`), `DialogueProvider.Option`/`reply`, `QuestEvents`, `QuestType`, `MarketEvents`. How
Hearth uses each, the numbers and the XP it reports: docs/ARCHITECTURE.md §11, Mettle.

## 1. A player price hook

**[done: `api/PriceModifier`, `EconomyService.addPriceModifier`, `EconomyService.price(ServerPlayer, …)`]** Applied in
`MarketPricer.quote(one, ServerPlayer, townBuys)` after reputation (every market row goes through it); product clamped
to `PriceModifier.MIN`–`MAX`; the town still pays under 95 % of what it charges.

**What.** In `api/`:

```java
public interface PriceModifier { double factor(ServerPlayer player, Settlement settlement, ItemStack one, boolean playerBuys); }
EconomyService.addPriceModifier(PriceModifier)
```

Applied in `PriceModel.buy` / `sell` (through `MarketPricer.quote`) after reputation. Product of all factors,
clamped to 0.5–1.5. `EconomyServiceImpl.price` would then need the player too (an overload with `ServerPlayer`).

**Why.** Speech perks, Presence and the Merchant background change prices. Today only `int reputation` enters the
formula, and `EconomyServiceImpl.price` passes no player at all.

## 2. Dialogue option state and replies

**[done]** `DialogueProvider.Option(id, label, order, locked, hint, tooltip)` (3-arg constructor kept) and
`DialogueProvider.reply` (asked right after a `select` that returned true); `TalkPayloads.Option` carries all three; the
server refuses a locked option; the screen draws it faded with the hint at the right and the tooltip on hover.
`standing.speech.SpeechCheck.hint(odds)` words a Hearth check's hint ("Persuade · 65 %").

**What.**

```java
record Option(String id, Component label, int order, boolean locked, @Nullable Component hint, @Nullable Component tooltip)
// keep the 3-arg constructor
@Nullable Component DialogueProvider.reply(ServerPlayer, Settlement, Resident, String optionId)  // default null
```

- Carry `locked`, `hint` and `tooltip` on `TalkPayloads.Option`.
- After `select`, if `reply` returns non-null, show it as the folk's new speech (typewriter as today).
- The conversation screen draws a locked option dim, with its hint at the right ("Persuade · 65 %", "Needs Speech 5").

**Why.** KCD2-style speech checks need the odds shown before choosing and the NPC answering the outcome. Today the
wire option is `(id, label)`, `select`'s result is ignored, and only the npc module can set a new line.

## 3. Quest events in the api

**[done: `api/QuestEvents.COMPLETED/FAILED/ABANDONED`]** Payload `(ServerPlayer, @Nullable Settlement, QuestType,
Identifier template, SettlementTier)`; `QuestType` moved to the api; the internal `quest.QuestEvents.COMPLETED` is gone.

**What.** Move `QuestEvents.COMPLETED` to `api/` (e.g. `api/QuestEvents`), and add `FAILED` and `ABANDONED`. Payload:
`(ServerPlayer, @Nullable Settlement, QuestType type, Identifier template, SettlementTier tier)` or a small
`QuestView` record.

**Why.** Mettle grants skill and character XP by quest type when a quest completes. It may only depend on the api.

## 4. A player-skills service slot

**[done: `api/PlayerSkills`, `HearthServices.setPlayerSkills/playerSkills`]** `Check` is `(odds, roll)` with
`passed()`; `PlayerSkills.NONE` is the default (never null). Read by the standing module's speech odds (Speech,
Presence), the law's guard and witness checks, haggling, lockpicking, pickpocketing and detection (Thievery, Stealth);
`award` is called for passed and failed checks, struck haggles, picked locks, lifted pockets and unseen crimes. Key
constants: `PlayerSkills.SPEECH/THIEVERY/STEALTH`, `PRESENCE/AGILITY/WITS/STRENGTH/VITALITY`.

**What.**

```java
public interface PlayerSkills {
	int level(ServerPlayer player, String skill);            // "mettle:speech" etc.; 0 when unknown
	int attribute(ServerPlayer player, String attribute);    // "presence"…; 3 when unknown
	boolean hasPerk(ServerPlayer player, Identifier perk);
	Check check(ServerPlayer player, String skill, int difficulty);  // odds 0–1 and the roll
	PlayerSkills NONE = ...;                                 // neutral fallback
}
HearthServices.setPlayerSkills(PlayerSkills) / playerSkills()
```

Plus one call the other way: `PlayerSkills.award(ServerPlayer, String skill, float xp, String reason)` (no-op in
`NONE`), so Hearth's law module can report a picked lock or a lifted purse.

**Why.**
- `StandingService.charisma` / `menace` and the law module's sneaking, lockpicking and pickpocketing odds should read the player's Presence, Speech, Stealth and Thievery.
- This keeps Hearth free of any Mettle types: Mettle registers the implementation, and absent Mettle everything is neutral.

## 5. A dialogue order band for training

**[done: `DialogueProvider.TRAINING_FIRST/LAST` = 300/399, documented on `Option`]** For the fee:
`EconomyService.charge(player, settlement, trainer, bits)` takes the coin (nothing if they cannot pay) and pays the
trainer's shop till or purse. `Resident.job` and `Settlement.tier()` are already api.

**What.** Reserve 300–399 for training options in `DialogueProvider`'s documented bands.

**Why.** Trainers ("Train Blades · 40 bits") come from Mettle's provider, keyed on `Resident.job`, and should sort
after quests and before "bye".

## 6. A market trade event

**[done: `api/MarketEvents.TRADED`]** Fired after every successful buy or sell at a market counter (town or shop,
posted price or haggled), with the bits that changed hands.

**What.** An api event fired by `Market.trade` / `buyFromTown` / `sellToTown`:
`(ServerPlayer, Settlement, ItemStack item, int count, long bits, boolean playerBuys)`.

**Why.** Speech XP per crown of business, and Mettle's stats. `Standing.earn` already measures the same thing
internally.

## Done (2026-10-21): Mettle requests 7-11 (mettle-cards.md) and 12-14 (mettle-speech-stealth.md), both on branch mettle-build-feel

**Status: all done** (Hearth branch `hearth-mettle714`). Additions only; no signature of a type Mettle copies changed.
How each is applied and clamped: docs/ARCHITECTURE.md §11, Mettle.

- **7 [done]** `api/VirtualGoods` + `HearthServices.addVirtualGoods/virtualGoods()`; the broker is a merchant or trader
  in a town or city's market ("The card table", conversation options 140–149 until the screen is designed in Figma);
  `economy/broker/Broker`: seller-set price, fee 2 % and tax 5 % to the town, proceeds to the seller's bank deposits
  (offline too), 7-day term then `release`, 8 listings a seller, 96 a board, `Broker.search` by name.
- **8 [done]** `EconomyService.transfer(from, to, bits)` and `canReceive(player, bits)` (defaults; the economy module
  checks the receiver's pouches and slots).
- **9 [done, nothing new]** `SettlementEvents.STANDING_CHANGED (world, settlement, player, from, to)` fires on each
  title change; Honoured is `Title.HERO`.
- **10 [done]** `PlayerSkills.titles(player)` (default empty); folk greet by a Mythic now and then (`npc/talk/Legends`).
- **11** nothing needed (training band and `EconomyService.charge`).
- **12 [done]** `PlayerSkills.bonus(player, key)` (default 0) with the keys as `PlayerSkills.BONUS_*`; Hearth's clamps in
  ARCHITECTURE §11. `speech/retry` is a new rule (failures taken back, a budget a sim day); `greet/hero` is flavour only.
- **13 [done]** `api/StandingEvents.SPEECH_CHECK` and `LAW_CHECK` `(player, settlement?, kind, passed, difficulty)`;
  award reasons unchanged.
- **14 [done]** `PlayerSkills.title(player)` -> `Component` (default empty); greetings use it (`legend.title`).


Agreed answer to 12-14 (sent to the Mettle session): `PlayerSkills.bonus(player, key)` additive default with the keys in
that doc (Hearth applies and clamps; a perk is either in the effective level or a key, never both); a separate key
`hearth:stealth/unseen_beyond` (blocks); `speech/retry` as a new rule; outcome via a new api event
`StandingEvents.SPEECH_CHECK (player, settlement, kind, passed, difficulty)` and the same for law checks (award reasons
unchanged); `PlayerSkills.title(player)` -> Component (beside request 10's `titles(player)`); `hearth:greet/hero` = 1 is
a greeting flavour only, never a standing change. To build after the living-world batch B merges.
