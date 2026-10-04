# Phase 3 economy: requests to the lead

What the economy module needs from outside its package for money through people (LIVING_WORLD.md §4, §7.5–§7.7), what
it does meanwhile, and proposed signatures. Everything here already works without the request being met; meeting it
removes a workaround or a risk of two modules doing the same thing.

## 1. `EconomyService`: the phase 3 methods (api)

**Why.** The social module charges for inns, baths and stables; the law module collects fines and takes bribes and
confiscates purses; the sim moves stations by household wealth; the planner and the sim put funded projects first; the
law and standing modules need to know who owns a business. Today `EconomyService` has only `balance/charge/pay/format/price`.

**Meanwhile.** All of it is implemented as public methods on `economy/EconomyServiceImpl` (reachable through
`EconomyAccess.get()`), so the lead can wire callers at integration without waiting for an api change. Other modules can
already get most of it with today's api: charge the player with `charge` and `settlement.deposit` the fine.

**Proposed** (add to `api/EconomyService`, implemented as they stand in `EconomyServiceImpl`):

```java
/** Coin a resident holds (their purse). */
long purse(Resident resident);

/** All the coin a resident's household (everyone sharing their home plot) holds: what its station follows. */
long householdWealth(Settlement settlement, Resident resident);

/** What a service costs this player here: "inn_bed", "inn_room_week", "meal", "ale", "bath", "stable_hire_day",
 *  "stable_keep_day", "horse", "tailor_dye", "healer", "dice_stake" (base prices in config/hearth/economy.json). */
long servicePrice(Settlement settlement, String service, @Nullable UUID player);

/** Charges the player for a service and pays the vendor's shop till (or purse, or the treasury).
 *  Returns the bits charged, or -1 if they could not pay (nothing is taken). */
long chargeService(ServerPlayer player, Settlement settlement, String service, @Nullable UUID vendor);

/** A fine, bail or bounty: player → treasury. False (nothing taken) if they cannot pay. */
boolean fine(ServerPlayer player, Settlement settlement, long bits);

/** A bribe: player → a resident's purse. */
boolean bribe(ServerPlayer player, Settlement settlement, Resident resident, long bits);

/** Coin taken out of a resident's purse (pickpocketed, confiscated); returns what they had of it. */
long takeFromPurse(Settlement settlement, Resident resident, long bits);

/** Plots of unfinished buildings players have paid toward. */
Set<Integer> fundedPlots(Settlement settlement);

/** The player who owns the business (workshop, shop, farm, extraction site) at a plot, or null. */
@Nullable UUID businessOwner(Settlement settlement, int plotId);
```

Pickpocketing a resident's purse: the law module should take the coin with `takeFromPurse` and pay it to the player
with `pay`, so the coin is the resident's real coin, not new money.

## 2. Funded projects first (plan, sim)

**What.** A player can pay toward any unfinished public building (not homes, not extraction plots) at the bank, the
town hall, the reeve or the master builder (`TAB_WORKS`). The economy keeps the fund and, when the plot's construction
stalls (`CONSTRUCTION_STALLED`), buys the missing goods from passing traders straight into the stockpile.

**Asked.** The planner and the sim should read `EconomyService.fundedPlots(settlement)` (until then:
`EconomyAccess.get().fundedPlots(settlement)`, or `settlement.data(EconomyModule.WORKS).projects.keySet()`):
- **sim**: start and labour funded plots before others (in `construct`'s plot order), and hold incoming goods for them
  first;
- **planner**: never `replaces` or drops a funded plot; a funded plot that is still `PLANNED` should be started next.

The funded flag clears itself when the plot is `BUILT` (the town keeps the change) or gone (`PLOT_ABANDONED`: the
givers are repaid into their bank accounts).

## 3. Station follows the purse (npc/sim, plan)

**What.** The economy moves `Resident.station` on `DAY_PASSED`, per household (everyone sharing a home plot): one rung up
the ladder poor → peasant → artisan → merchant → gentry (gentry only at town tier and up) when the household holds
`station.rise_days` (25) days of the next rung's wage per head and is not hungry; one rung down (as far as poor) after
`station.hunger_days` (3) days unable to pay for food with less than `fall_days` (2) days of its own wage per head; at
most one rung above what its trade brings (`Station.of(job)`); clergy, scholars, officials and lords hold their station
by office. A move holds for 10 days. Rule: `economy/purse/StationRule` (pure, tested).

**Asked.** If the npc or sim agent also sets stations, one of us should stop: set `station.follow_purse: false` in
`config/hearth/economy.json` and call `StationRule.next(...)` with `householdWealth` instead, or leave it to the economy.
The planner reads `Resident.station` as it is.

## 4. What folk consume (sim)

**What.** Households pay for their food every day (`households.food_per_head` units of FOOD at the town's price) but the
economy does **not** take FOOD from the stockpile: the sim already eats it. Households **do** take the comforts in
`households.consumed_goods` (default cloth, candles, luxury, books, dye, specialty) off shop shelves (shops restock from
the stockpile's surplus above its target) or from the stockpile's surplus directly.

**Asked.** If the sim's phase 3 chains consume any of those goods for households itself (candles for lighting homes,
say), tell me which and I drop them from the default list, so nothing is eaten twice. Candles and books a blueprint bill
asks for are not affected: shops and households only take the surplus above the town's target stock.

## 5. Shop stock is the town's (sim)

Shops buy their stock from the stockpile at wholesale (coin to the treasury) and return what they cannot hold. The units
on shop shelves are no longer in `Stockpile.get`, so the sim's carrying capacity reads a little low for a town with full
shops (a market stall holds at most `shops.max_stock` (512) of each goods it deals in, usually 16–60). If that matters,
the sim can add `settlement.data(EconomyModule.SHOPS)` stock to its view of the stores, or I can cap shop stock lower.

## 6. Hiring into a player's business (sim)

**What.** The owner of a business may hire an idle adult (job NONE, no workplace) into it at the property tab: the
economy sets `resident.job` (from the archetype's `ArchetypeInfo.jobs`) and `resident.work`.

**Asked.** The sim's daily job assignment should leave workers of a player-owned business where they are (check
`businessOwner(settlement, resident.work) != null`), and not move an owned workplace's workers elsewhere.

## 7. Ownership for the law and standing modules (law, standing)

- A player-owned business is the player's property: `LawService.owner(pos)` inside its plot should say
  `Owner(settlementId, null, player, PLAYER)` when `businessOwner` names them; its stock is still worked by its folk.
- A shop's stock and till are its keeper's (`Owner.Kind.WORKPLACE`, resident = the keeper): what a thief takes from a
  shop's chests would ideally come off its shelves. The economy exposes the shops as
  `settlement.data(EconomyModule.SHOPS).shops` (stock in goods units, `till` in bits); I'm happy to add a
  `stealFromShop(settlement, plotId, goods, units)` if the law module wants burglary to empty real shelves.
- Stolen goods: the economy calls `LawService.isStolen` / `stolenFrom` on every item a player offers. Honest merchants
  refuse goods stolen in their own town (`fence.refuse_stolen`; `fence.refuse_anywhere` for strict servers); a
  resident with job `FENCE` (or anyone keeping a `commerce/thieves_den`) buys them anywhere at `fence.rate` (0.4).
- Deeds reported to the standing service: `economy:trade` (+1 per crown of honest trade, at most 3 a day),
  `economy:haggle_soured` (−2, the merchant as `resident`), `economy:funded_project` (+1…+25),
  `economy:contract_fulfilled` (+4), `economy:contract_failed` (−6), `economy:bought_business` (+3),
  `economy:bought_property` (+2; buying a house or plot is what makes a player a Burgher). The standing module maps
  them to words; weights are config (`haggle.sour_weight`, `works.*_weight`).
- The economy reads `StandingService.standing/charisma/opinion` for prices, haggling room and patience; without the
  service it uses `Settlement.reputation` and neutral charisma and opinion.

## 8. Traits by name (npc)

Haggling reads a merchant's traits from `Resident.extra("npc").traits` as strings (`greedy`, `generous`, `cheerful`,
`grumpy`, `sociable`, `shy`) without importing the npc module. If the npc module renames a trait or moves that list,
please keep those six names or tell me.

## 9. Names the economy reads (build, standing)

`hearth.archetype.<category>.<name>` (build) names shops and businesses ("Edda Miller's bakery"); `hearth.goods.*` are
defined here for all 29 goods (including `tag.item.hearth.goods.*`). `hearth.station.*` and `hearth.title.*` are the
standing module's; the economy does not show them yet.

## 10. Gametest (optional)

The dev pilot covers the new screens (`HEARTH_DEV_ECONOMY=shots ./gradlew runClient`: shops, a haggle, the fence, a
business, works, contracts). If the integration tour wants it, the steps can move into `IntegrationTour` against a sim
settlement.
