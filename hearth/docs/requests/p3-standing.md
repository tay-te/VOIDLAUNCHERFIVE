# Requests from the standing module (phase 3)

The standing module implements `StandingService` (docs/STANDING.md). These are what it needs from files and modules
it does not own. Each says what, why, and the proposed shape. Until they land, the standing module works around them
as noted.

## For the sim module

1. **Delegate `Settlement.reputation` / `addReputation` to the standing service when it is registered.**
   Why: the standing module's `TownLedger` is the authority (LIVING_WORLD.md §3); economy and quest code still call the
   settlement methods. Proposed, in `SimSettlement`:
   **Done** (sim, before this integration). The kind is `hearth:reputation`, which the standing module now treats as a plain adjustment.

   ```java
   public int reputation(UUID p) {
       StandingService st = HearthServices.standing();
       return st != null ? st.standing(this.id, p) : this.reputation.getInt(p);
   }
   public void addReputation(UUID p, int delta) {
       StandingService st = HearthServices.standing();
       if (st != null) {
           st.deed(new StandingService.Deed(p, this.id, "hearth:adjust", delta, null, center(), false));
       } else {
           /* as now */
       }
   }
   ```

   Both directions are already safe on the standing side: its seeding read of `reputation()` is answered without
   recursion, and the echo of its own mirror (`hearth:adjust` while mirroring) is ignored. Tested in
   `StandingEngineTest.delegationNeitherRecursesNorCountsTwice`. Once delegated, set `town.mirror_to_settlement` to
   false in the shipped config (or leave it: the echo is harmless).

2. **Hand the old numbers over once.** Once `reputation()` delegates, the standing module can no longer read the sim's
   own map to seed a player. On settlement load (or the first time the standing service is seen), send each stored
   entry once as `deed(new Deed(player, id, "hearth:seed", value, null, center(), false))`; the standing module ignores
   a seed for a player it already has. Then the sim's map can be dropped.
   **Done** (integration 3): `SimWorld.handReputationOver` sends `hearth:seed` deeds once a session; `mirror_to_settlement` is false by default.

3. Keep firing `DAY_PASSED` and `FOUNDED` as now (standing drifts daily; news held for a not-yet-adopted town is
   delivered on `FOUNDED`).
   **Done** (sim, checked).

## For the economy module

4. Report trade standing as a deed instead of `addReputation`: `market/Standing.java` →
   `standing.deed(new Deed(player, town, "economy:trade", points, merchantResidentOrNull, pos, true))`. Also report
   `economy:donation` (treasury gifts), `economy:property` (a deed bought) and `economy:haggle_soured` (pushing a
   merchant too far, with the merchant as resident). The deed table already has rules for all four.
   **Done** (economy, checked): it reports through `Reputation.deed`, and the table has rules for its kinds.
5. **A way to pay a resident** for bribes: `EconomyService.payResident(Settlement, Resident, long bits)` (into their
   purse, §7.7). Today a bribe that succeeds is charged to the player and leaves the world (a coin sink).
   **Done** (integration 3): `EconomyService.payResident`, and bribes go into the purse (`bribe`).
6. Prices can read `standing.charisma(player)` (−1…1) beside reputation, and haggling can use
   `dev.voidmc.hearth.standing.speech.SpeechCheck` (or the api interface proposed in 12).
   **Done** (economy, checked): it reads charisma and opinion.

## For the quest module

7. Report `quest:completed` (+), `quest:failed` (−), `quest:abandoned` (−) with the giver as `resident` instead of
   `addReputation`, so the giver remembers it and news of a great deed travels. Use `witnessed=true` for public deeds.
   **Done** (quest, checked): it reports through `Standings.deed`.

## For the law module

8. Report each confirmed crime as `deed(new Deed(offender, town, "law:" + offence, weight, victim, pos, witnessed))`
   (weights as in StandingService's Javadoc: −30 a theft, −100 a murder). A crime first reported unwitnessed and
   later confirmed can be reported again with `witnessed=true`: the victim's memory is not counted twice.
   Also `law:fine_paid` (+, restores some standing), `law:jail_served` (+), `law:pillory` (− and public).
   **Done** (law, checked): `CrimeDesk.deed`.
9. `LawService.outlaw(player, town)` makes the title Outlaw; the standing module reads it.
   **Done** (checked).
10. Guards and witnesses: use `SpeechCheck.odds/attempt` for persuade, intimidate and bribe (it shows odds and leaves
    the right memories).
   Partly (integration 3): law reads standing, opinion, charisma and menace from the service but keeps its own odds formulas (they weigh the offence's severity). `StandingService.odds/attempt` are in the api for anyone who wants them.

## For the npc module

11. Greetings and reactions can read `standing.title(town, player)`, `standing.opinion(resident, player)` and
    `standing.fame(player, pos)`. The standing module reads these fields of `Resident.extra("npc")` directly and
    relies on their names and formats: `traits` (string list), `mood` (int), `spouse` (uuid), `children`, `parents`,
    `friends` (uuid lists). `FolkHandle.say(line, player)` is used to speak speech-check replies; `folkNear` +
    `awake` + `canSee` choose witnesses.
   **Done** (npc, checked).

## For the social module

12. The bathhouse reports a bath as `deed(new Deed(player, town, "social:bathed", 0, null, pos, false))`: the deed
    table's `wash` action makes the player clean and freshly bathed (charisma). (Bathing in water near a `BATH` POI
    already counts.) Festivals and weddings: `social:festival`, `social:wedding_gift`.
   Open (social module).

## For the repair module

13. `repair:helped` (a player helped a repair) and `repair:funded` (paid for one).
   **Done** (integration 3): repair's kinds are `repair:rebuilt` and `repair:fire_brigade`, now in the table.

## For the api owner

14. **Speech checks in the api.** Other modules are told to call `dev.voidmc.hearth.standing.speech.SpeechCheck`
    directly, which works but crosses module packages. Proposed:
   **Done** (integration 3): `StandingService.Speech`, `Odds`, `odds`, `attempt` and `expectedBribe`, implemented by the engine.

    ```java
    // StandingService
    enum Speech { PERSUADE, INTIMIDATE, BRIBE, FLATTER }
    record Odds(Speech kind, double chance, List<String> factors) {}
    Odds odds(ServerPlayer player, Settlement town, Resident resident, Speech kind, double difficulty, long bribeBits);
    boolean attempt(ServerPlayer player, Settlement town, Resident resident, Speech kind, double difficulty, long bribeBits);
    long expectedBribe(Resident resident, double difficulty);
    ```

15. **Signed renown.** `fame()` is unsigned (infamy is fame too); a `renown(UUID player, BlockPos near)` returning
    −100…100 would let greetings tell the famous from the infamous. The engine already computes it.
   **Done** (integration 3): `StandingService.renown`.
16. **A personal memory without town effects**: `remember(UUID resident, UUID player, String kind, int weight)` for
    small things said in conversation. Today a module can get the same effect with a deed kind whose table rule has
    `settlement: 0` (e.g. `npc:insult`).
   **Done** (integration 3): `StandingService.remember`.
17. **Roads between towns.** Gossip follows an approximation of the road network (each town linked to its nearest
    neighbours within a day's travel). If the sim or planner exposes the inter-town roads
    (`HearthWorld.roads(long settlementId)` → neighbouring settlement ids with road length), gossip would follow the
    real roads.
   Open: the inter-town roads are not exposed yet; gossip keeps its approximation.
18. **A line in the arrival banner.** The standing line is drawn by the standing module just under the sim's banner,
    with the same timing. A hook in `ArrivalBanner` (`ArrivalBanner.addLine(Function<ArrivalPayload, Component>)`)
    would make it part of the banner.
   Open: the banner hook was not added (the standing line still draws under it with the same timing).
