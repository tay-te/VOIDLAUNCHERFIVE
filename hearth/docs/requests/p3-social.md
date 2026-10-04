# Requests from the social module (phase 3)

What the social module needs from outside its package, why, what it does meanwhile, and proposed signatures. In the
order they matter.

## 1. A reply line from `DialogueProvider.select` (api, npc)

**What.** Let a provider answer in the folk's voice in the conversation screen, instead of only refreshing the options.

```java
/** As now, but may return the folk's answer (shown as their new speech); null keeps the current speech. */
default @Nullable Component reply(ServerPlayer player, Settlement s, Resident r, String optionId) { return null; }
```
(or change `select` to return `@Nullable Component`, with `null` = invalid).

**Why.** Every social option is a question with an answer ("A bed for the night." → "There's a bed for you, up the
stairs to the north."). Today the conversation re-shows the old greeting after `select`.

**Meanwhile.** `Hooks.reply` writes the answer to chat as "Edda: ..." (readable behind the conversation screen) and
makes the embodied folk face the player and gesture.

## 2. Notices on the notice board (api, quest)

**What.** A registry like `DialogueProvider` that the quest module's notice board reads:

```java
public interface NoticeSource {
	/** Notices to pin on this settlement's board for this player, newest first. */
	void collect(ServerPlayer player, Settlement settlement, List<Notice> out);
	record Notice(Component text, long day, @Nullable Component when) {}
	static void register(NoticeSource source) { ... }
}
```

**Why.** LIVING_WORLD.md §5: weddings, funerals and births "announced on the board". The social module keeps them
(`SocialLedger.notices`, `Notices.current(ledger, day)`, `Notices.text(notice, today)`) but cannot draw on the board.

**Meanwhile.** Notices are told to players inside the settlement when posted (chat, `life.announce_in_chat`), read out
by the crier at the board when the town is embodied, listed by "Anything on in town?" and `/hearth social notices`.

## 3. Know what a folk's routine is doing (api `NpcService.FolkHandle`, npc)

**What.** `Activity activity()` on `FolkHandle` (SLEEP, WORK, EAT, LEISURE, HOME...), or `boolean atLeisure()`.

**Why.** Market crowds should come in their free time, not leave a builder's scaffold. **Meanwhile** `Agenda.attends`
lets the idle (children, elders, no trade) come any hour and working adults only in the 12:00 hour; festivals are in the
evening after work.

## 4. Poses for folk behaviours (api `FolkHandle`, npc)

**What.** `void pose(String pose)` with the npc module's `FolkPose` names (sit, cheer, pray, talk, wave...).

**Why.** Guests sit in the pews at a wedding, mourners pray, festival crowds cheer, the lord sits on the throne.
**Meanwhile** they stand at their places and `swing()` for a cheer or a gesture.

## 5. Marriages and households (npc, sim)

**What.** An npc-owned `marry(Resident a, Resident b)` (or `NpcService.marry`), and the sim keeping spouses in one home
when it re-counts beds.

**Why.** Weddings are made by the social module. **Meanwhile** `FolkFacts.marry` writes the npc module's documented
`extra("npc").spouse` link on both (only if both are free and not kin) and moves the second spouse into the first's home;
the sim's daily count evicts them again if the house is full.

## 6. Officials keep their posts (sim)

**What.** `SettlementClock.assignJobs` should leave alone residents whose `extra("social").office` names a post
(`reeve`, `lord`, `steward`, `judge`, `jailer`) — keep their job and workplace, and not count them against a
workplace's slots.

**Why.** The sim resets every job without a workplace slot each day. **Meanwhile** `Officials.update` runs on
`DAY_PASSED` (which the sim fires after assigning jobs) and puts the post back, so between days the record is right,
but the sim may give the official a trade for a moment and leave that trade's slot unfilled until the next day.

## 7. A way to clean an appearance (api `StandingService`, standing)

**What.** `void washed(ServerPlayer player)` on `StandingService` (or read `PlayerLife.lastBath`, the persistent
attachment `hearth:social/player`, field `last_bath` = game time).

**Why.** The bathhouse "resets the standing module's cleanliness". **Meanwhile** a bath reports the deed
`social:bath` (weight 0) and records `lastBath`.

## 8. Rented beds and rooms, hired mounts (law)

**What.** The law module should treat a player's inn rental as permission: the rented bed and the rented chest
(`SocialLedger.rooms` / `beds`, positions packed with `BlockPos.asLong`) are not trespass or theft for that player, and
a horse tagged `hearth.hired` belongs to the settlement but may be ridden by its hirer. Proposed api:
`LawService.grantUse(UUID player, ServerLevel level, BlockPos pos, long untilGameTime)` or a `UsePermission` callback
the social module registers.

**Meanwhile** the social module itself locks a rented chest against other players (opening and breaking).

## 9. Pardons at the lord's court (api `LawService`, law)

**What.** `boolean pardon(UUID player, long settlementId, long paid)` — the lord can pardon a crime for a price paid to
the treasury (more than the fine; standing decides whether he will).

**Why.** LIVING_WORLD.md §4–5: the court at the keep. Without it the lord's court only receives pledges and gifts.

## 10. Open the market for a stallholder (api `EconomyService`, economy)

**What.** `void openMarket(ServerPlayer player, Settlement settlement)`.

**Why.** On market day non-merchant folk keep the extra stalls; players should be able to trade with them. **Meanwhile**
the stallholders sell the visiting trader's wares through dialogue options (priced with `EconomyService.price` and the
`market.visiting_markup`), and merchants keep the town's market as before.

## 11. Purses for folk (economy)

**What.** `long purse(Resident)`, `boolean takeFromPurse(Resident, long)`, `void giveToPurse(Resident, long)`.

**Why.** Dice stakes are the folk's own money. **Meanwhile** the folk's stake comes from and goes back to the
settlement treasury (so no coin is made or lost), capped at a quarter of it.

## 12. Dressings and repairs (repair, build)

**What.** The repair module should not count or "repair" the market-day stalls and festival dressings the social
module sets up and takes down (`SocialLedger.dressings`: packed position, block id, event, until) — they only ever
replace air in squares and come down by themselves. When the build module ships its §8.3 dressings (stalls, garlands,
mourning black), the social module would rather ask for them by event than place its own.

## 13. Visiting traders as folk (npc)

**What.** `NpcService.visitor(Settlement s, Culture culture, JobType job, BlockPos at, long untilGameTime)`: a temporary
folk who is not a resident (no RESIDENT_ADDED/REMOVED, no news).

**Why.** "Visiting traders" on market day. **Meanwhile** the visiting trader's wares are sold by the town's own
stallholders and merchants, naming the trader's town.

## 14. Station and title names (standing)

The social module shows `hearth.title.*` (standing titles in the reeve's report and the lord's audience) and
`hearth.station.*`; it defines neither. It reuses `hearth.culture.*` and `hearth.tier.*`.
