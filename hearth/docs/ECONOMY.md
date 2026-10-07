# VOID Hearth — coin, markets, purses and shops

The economy module (`dev.voidmc.hearth.economy`, client `dev.voidmc.hearth.client.economy`). Money in Hearth is meant
to feel as if it was always part of Minecraft: coins you can hold, struck from the game's own metals, earned and spent
with folk at their stalls. There is no balance on the HUD; what you carry is what you have.

Phase 3 (LIVING_WORLD.md §4, §7.5–§7.7) makes money move **through people**: residents carry purses, earn wages and buy
their bread, cloth and candles; every shop keeps its own stock and till, as KCD's merchants do; players haggle, sell
stolen goods to fences, own workshops and quarries, pay for the town's works and take supply contracts. Goods grew from
ten kinds to twenty-nine.

## Coins

| Coin | Worth | Metal | Melts to |
|---|---|---|---|
| **Copper bit** `hearth:copper_bit` | 1 bit | copper | 1 copper nugget |
| **Iron mark** `hearth:iron_mark` | 10 bits | iron | 1 iron nugget |
| **Gold crown** `hearth:gold_crown` | 100 bits | gold | 2 gold nuggets |

- All amounts in code are **bits** (`long`). Each coin is ten of the one below, so change can always be made and the
  largest-first rule gives the fewest coins.
- Three coins are enough: a stack of crowns is 6,400 bits (more than a house), and a pouch carries hundreds of coins in
  one slot; a fourth name would only be one more thing to learn.
- Coins stack to 64. Their item model definitions use `minecraft:range_dispatch` on `minecraft:count` (not normalised):
  1 coin shows a single stamped coin, 2–7 a few overlapping coins, 8–31 a short column, 32+ a heap.
  Textures: `assets/hearth/textures/item/{copper_bit,iron_mark,gold_crown}{,_few,_stack,_pile}.png`, drawn by
  `tools/economy/gen_textures.py` in the vanilla nugget and ingot palettes (copper bit: a square-holed cash coin; iron
  mark: a struck cross; gold crown: a crown).
- Picking up coins clinks (vanilla `block.chain.step`, pitched by metal: copper low, iron brighter, gold highest).
- **Players cannot craft coins.** Coin enters the world by trade, wages and quest rewards (all paid from settlement
  treasuries), and as treasure in chests. Melting coin at a furnace or blast furnace gives nuggets worth far less than
  face value (`melt_*` recipes in `data/hearth/recipe/`; the nugget count is the config's melting yield, applied by
  `mixin/economy/AbstractFurnaceBlockEntityMixin`; 0 disables melting).

### The coin pouch

`hearth:coin_pouch`, crafted from three leather and a string (` S ` / `L L` / ` L `). A bundle that takes only coins:

- Click coins onto it to put them in; right-click it (or right-click with it on an empty slot) to take out a handful of
  the largest coin. Use it in hand to hear it and see what it holds.
- **Picks up coins for you**: coins you walk over go into the first pouch with room (`mixin/economy/ItemEntityMixin`),
  the rest into the inventory as usual.
- Holds 512 coins (config). It stores three counts (`hearth:pouch_contents` component), not item stacks.
- Its tooltip shows the total ("Holds 2 crowns, 3 marks") and a picture of the coins; its look fills out as it fills
  (`custom_model_data` fullness → `coin_pouch`, `_some`, `_full`).
- Counts as carried money everywhere: `balance`, `charge` and `pay` use pouches, and change goes into a pouch first.

## Where money comes from and where it goes

```
                 poll tax, tithes, purchases from the stores, wholesale, dues
   purses ─────────────────────────────────────────────────────────────► TREASURY ── upkeep ──► gone
     ▲  │ food, comforts                                                  │  ▲   ▲
     │  ▼                                                       wages     │  │   └── mint, passing trade
     │ SHOP TILLS ── restock at wholesale ─────────────────────────────►  │  │
     │  │ keeper's share                                                  │  └── players: market, property, fines
     └──┴─────────────────────────────── wages ◄──────────────────────────┘
                                                     businesses: the owner's share ──► bank deposits
```

- **Treasury** (`Settlement.treasury()`, owned by the sim; the economy only deposits and withdraws). A town that cannot
  pay does not buy, and quests should only offer what the treasury can pay.
- **Daily** (`daily/SettlementEconomy`, on `SettlementEvents.DAY_PASSED`, including caught-up days — arithmetic over
  the settlement's records, no world access; about 0.14 ms a day for a town of 150, measured in `EconomyMeasureTest`):
  - shops matched to buildings and folk (below); passing trade: `tradeIncomeRate ×` the base value of the stock above
    target;
  - **purses** (below): wages to residents, poll tax and tithes to the treasury, food and comforts bought from the shops
    and the stores; stations follow purses. With `households.enabled` off, the phase 2 model instead: taxes
    `taxPerHead × population` from nobody in particular and wages `wagePerWorker ×` working adults to nobody;
  - upkeep: `upkeepRate ×` treasury — this keeps treasuries bounded: they settle near `net income / upkeepRate`;
  - **the mint**: with a minter or a built `hearth:commerce/bank`, up to `mintUnitsPerDay + mintUnitsPerMinter ×
    minters` units of METAL above half its target become `bitsPerMetalUnit` bits each, but not while a building waits
    for goods (w6: the metal bought in for it was struck before its builders came for it);
  - price drift, the town's own use of each goods, and expiry of builders' needs (below);
  - shops restock, return surplus, pay dues, and pay their keepers (or owners); owned businesses pay their owners;
    funded projects buy what they lack; supply contracts lapse and are offered.
- **Treasure**: coin is added to built-in chest loot with Fabric's `LootTableEvents.MODIFY` (`loot/CoinLootTables`): a
  roll for bits and marks at the category's chance, and a separate rarer roll for crowns. Categories: village chests,
  shipwreck (supply/map) and shipwreck treasure, underwater ruins and ruined portals, buried treasure, dungeons and the
  abandoned camp's secret chest, mineshafts, ancient cities (marks and crowns: old money), desert/jungle temples,
  strongholds, woodland mansions, pillager outposts, igloos and camps; and when VOID Expanse is installed, its
  `expanse:chests/micro/lost_chest`, every structure chest (`expanse`) and every hidden stash (`expanse_hidden`),
  but not its archaeology (brushed) tables or map chests. Amounts per category in the config.
- **New settlements**: on `SettlementEvents.FOUNDED`, a treasury still at 0 is seeded with 40 bits per head. Sim
  settlements never are: a new camp brings the sim's `founding.treasury` (200 bits), and an adopted history keeps at most
  60 bits a head. While this module is present the sim's clock leaves its own per-worker coin out, so these daily books
  are the treasury's income (with the sim's export sales and import purchases on top).
- **What builds a young town is not coin** (w6, SIM.md §2 and §18): its income is a few bits a worker a day, and the
  wages the treasury pays out (at most `wage_cap_share` of it a day) come back through poll tax, tithes and food bought
  from the stores, so coin mostly circulates. A stalled stage's imports are paid with coin first and then in barter for
  what the town has in plenty (grain, logs, stone beyond its builds' wants), and the sim buys them in the day's business
  before these books run, so wages never take the coin a stalled build was waiting for. The founders' own buildings
  come provisioned and idle builders fetch raw materials from the town's land. With these a town left alone, books
  running, reaches a village in 50–70 days and a town in 170–180, caught up a day at a time in 60–70 and 150–190 (it
  averaged 7 souls before).

## Prices

### Base values (data)

`data/<ns>/hearth_prices/*.json`: `{"values": {"minecraft:bread": 1.0, "#minecraft:logs": 0.5, ...}}` in bits per
item. An item's base value is its own entry, else the last-loaded matching tag entry, else a guess: its goods'
default value × a rarity factor (common 1, uncommon 4, rare 12, epic 40) — so items from any mod get a price.
Shipped: `vanilla.json` (~330 entries incl. every tool and armour piece derived from its material) and `expanse.json`
(Expanse foods, stones, flowers, amber...; ignored when Expanse is absent). Worn tools are worth their remaining
durability (at least a fifth); each enchantment level adds a fifth.

Scale: bread 1 bit, a log ½, cobblestone ⅒, an iron ingot 6, a gold ingot 12, an emerald 25, a diamond 120, an iron
pickaxe ~23. `hearth_prices/goods.json` sets each goods' **unit value** (WOOD 0.125 = a plank, STONE 0.1, METAL 6 = an
ingot, LOGS 0.5 = a log, BRICK 1 = a block of bricks or terracotta...), the fallback values and the default wares.
**Stockpile units** for an item are its value over its goods' unit value (at least 1), so a town's stores and its prices
agree; units are chosen so a typical block is about one unit, as in a blueprint's bill.

### Goods

Items map to the 29 `Goods` through the item tags `#hearth:goods/<goods>` (`data/hearth/tags/item/goods/`), built from
vanilla tags plus items, with Expanse entries marked `required: false`. The phase 3 split follows the chains of
LIVING_WORLD.md §7.5 — raw, processed, finished:

| Raw | Processed / finished |
|---|---|
| `logs` (logs, wood, bamboo blocks, Expanse logs) | `wood` (planks, slabs, fences, doors, chests, barrels...) |
| `stone` (cobble, field stone, sandstone, limestone, gravel, flint) | `dressed_stone` (stone bricks, polished and cut stone, limestone bricks...) |
| `clay` (clay, clay balls, mud, red earth) | `brick` (bricks, terracotta, mud bricks, pots) |
| `sand` (sand, red sand, Expanse sands) | `glass` (glass, panes, bottles) |
| `ore` (ores, raw metal) | `metal` (ingots, nuggets, chains, lanterns, rails...) |
| `grain` (wheat, seeds, hay) | `flour` (no vanilla item: other mods' `#c:flours`) → `food` (bread, cooked meat and fish, stews, pies) |
| `meat` (raw), `fish` (raw, crab) | `food` |
| `wool` (wool, feathers) | `cloth` (carpets, beds, banners, cushions, string) |
| — | `leather` (leather, rabbit hide) |
| `wax` (honeycomb) | `candles` |
| — | `paper` → `books` (books, bookshelves, lecterns, maps) |
| — | `dye` (dyes, ink sacs) |
| `salt` (Expanse salt blocks, `#c:salts`) | |
| `produce` (fruit, vegetables, eggs, milk, honey) | |
| `specialty` (flowers, bamboo, cocoa, kelp, chorus; Expanse amber, prismite, glowcaps, lavender, edelweiss...) | |
| | `fuel` (coal, charcoal, torches, peat), `tools` (tools, weapons, armour), `luxury` (gems, glazed ware, curios) |

No item is in two goods (the generator checks it against the expanded vanilla tags, since the first goods in `Goods`
order would silently win). Emeralds are deliberately in no goods tag: they belong to the money changer.

### A settlement's price

`price/PriceModel` (pure), fed by `price/MarketPricer` per settlement:

```
mid  = base × scarcity × culture × drift
buy  = mid × (1 + spread/2) × (1 − reputationEffect × rep/100)          what the player pays
sell = mid × (1 − spread/2) × (1 + reputationEffect × rep/100) × need    what the town pays (always < buy)
```

- **scarcity** = `(target / stock) ^ elasticity`, clamped to `[scarcityMin, scarcityMax]` (0.5–3 by default). The
  **target** is `max(capacity × targetFraction, dailyUse × reserveDays)`, where **daily use** is learned each day from
  how the town's own stock falls (trade with players is subtracted so a big sale is not mistaken for hunger).
- **culture**: `hearth_prices/cultures/<culture>.json` gives each culture a factor for every goods (below 1 = plentiful
  and cheap there): karst towns are rich in bamboo, limestone and paper, desert towns in sandstone, sand, salt and dyes,
  mesa towns in brick and ore, maritime towns in fish and salt, steppe towns in leather, wool and meat, northern towns in
  logs... `cultureEffect` scales them.
- **land**: goods the settlement's own resource sites yield (`Settlement.sites()`, the sim's survey) are `siteEffect`
  (15 %) cheaper there — a town by a birch wood sells timber cheaply, a mining town ore.
- **drift**: each settlement's price for each goods wanders a little every day, mean-reverting, deterministic per
  (settlement, day, goods) so a caught-up history is reproducible.
- **reputation** −100..100 moves both prices toward the player (±25 % at the extremes). Below
  `minTradeReputation` (−50) merchants refuse to deal.
- **need**: goods the builders are waiting on are bought at a premium (+30 %).

Because scarcity, culture and drift differ between towns, **carrying goods between towns pays**: buy timber where it is
plentiful, sell it where the builders are short.

**Lots.** Markets trade cheap things by the lot: the smallest power of two (up to a stack) worth at least 4 bits, so
rounding to whole bits costs at most a quarter (32 planks, 4 bread, 1 iron ingot). The market rounds its asking price
up and what it pays down.

## The market

Opened by **"Let's trade."** with whoever keeps a shop (`dialogue/EconomyDialogue`, a `DialogueProvider`), or with a
merchant who keeps none (the town's own market). Server-authoritative (`market/Market`, `MarketMenu`; the client draws
`MarketView` and sends clicks as `EconomyActionPayload`).

The counter (`market/TradeCounter`) is either **a shop** — its own shelves and till — or **the town's market** (its
stockpile and treasury; `/hearth market <s> open`, quests and the sandbox use it).

- **For sale** (left page): for each goods the counter holds (a shop: its shelves; the town: its surplus above
  `target × surplusAbove`), up to three of the culture's own wares (WOOD → local planks; LOGS → its logs, Expanse
  wisteria, redwood, palm or olive where installed; FOOD → its bread, fish, venison...; DRESSED_STONE → its building
  stone; BRICK → its bricks, terracotta or mud brick...). Buying takes the units off the shelves (or out of the stores)
  and puts the coin in the till (or the treasury).
- **Wanted** (right page): everything the player carries that the counter deals in, priced by how short the town is,
  plus a row for each thing the builders need that the player lacks. A shop pays from its till and shelves what it buys
  (up to `shops.max_stock`); **it cannot buy what it cannot pay for** ("There isn't enough in the till"). The town pays
  from the treasury into the stores (up to twice the target and the store's capacity).
- **Selling timber and stone helps the town build.** When a construction stalls (`CONSTRUCTION_STALLED`), the economy
  remembers the missing goods and the building. The merchant says so ("Our builders are waiting on stone for the
  chapel. I'll pay you well for it."), those rows sit at the top with a hammer and a green wash, pay a premium, and a
  sale says "The builders will put it into the chapel." Whatever the counter, the **town** buys these (from the
  treasury, straight into the stockpile) so construction can resume; the need is forgotten once the stockpile holds it
  (or after 8 days). A specialist shop only shows the needs it deals in.
- Click: one lot. Shift-click: a stack (buying) or all you carry (selling). Every trade refreshes the whole view (new
  prices, purse, treasury) and its result shows on the merchant's note strip.
- Honest trade earns standing: an `economy:trade` deed of +1 per crown of business, at most 3 a day per settlement
  (to the standing service, or the settlement's reputation without it).
- Right-click a row to **haggle** over one lot (shift-right-click: the whole stack or all you carry) — below.
- The UI: an oak frame round two parchment pages, coin amounts drawn as little coins, your inventory below
  (`textures/gui/economy/market.png`, generated by `tools/economy/gen_textures.py`).

## Purses: money through people

Every resident has a purse (`purse/Purses`, kept in `Resident.extra("economy")`). On each `DAY_PASSED`
(`purse/PurseEconomy`):

- **Savings**: an adult the economy sees for the first time arrives with `starting_purse_days` (10) days of wages
  (elders half again as much); children have nothing of their own. This, the mint and passing trade are where coin
  enters a town.
- **Wages**: each working adult earns the day's wage of the station their trade brings (`households.wages`: poor 2,
  peasant 4, artisan 5, merchant 7, clergy 4, scholar 5, official 6, gentry 12, lord 25 bits). Who pays: the business's
  revenue if a player owns their workplace (below); their shop's till if they work at a shop that can pay; otherwise the
  treasury, which pays at most `wage_cap_share` (25 %) of itself in a day — below that everyone it pays is short-paid
  alike, and the books count the unpaid days.
- **Poll tax**: each grown resident pays `poll_tax` (1 bit) if their purse holds it.
- **Food**: each household (everyone sharing a home plot; someone without one is a household of one) buys
  `food_per_head` units of food a day per member at the town's price, from a baker, the inn, a butcher or the market
  (spread over them) — the shop keeps its margin and pays the treasury wholesale for food that came from the stores —
  or from the town directly. The food is **paid for, not taken**: the sim already ate it. A household that cannot pay
  goes hungry (`hunger` days).
- **Comforts**: a household spends `comfort_share` (6 %) of what it holds above `reserve_days` (6) days of its costs:
  `inn_share` (30 %) of it at the inn or tavern, the rest over its station's basket — the poor buy cloth; peasants cloth,
  candles and dye; artisans also books and fine things; merchants and gentry fine things, specialities and books;
  clergy candles, wax and books; scholars books and paper. Only goods in `consumed_goods` (cloth, candles, luxury, books,
  dye, specialty) are bought, and they are **taken**: off a shop's shelves (the most stocked honest shop that sells it),
  else from the stores' surplus. This is the real demand that empties shelves and makes shops restock.
- **Tithes**: `tithe` (0.4 %) of each household's purse a day goes to the treasury (alms, dues), which keeps purses
  bounded.
- Small amounts are rounded to whole bits up or down with the chance of their fraction, deterministically per
  (settlement, day, household), so a 0.4-bit candle budget buys a candle on the right share of days and a caught-up
  history repeats. Coin is conserved by all of this (tested day by day).
- **Station follows the purse** (`purse/StationRule`): a household rises a rung (poor → peasant → artisan → merchant →
  gentry) when it holds `station.rise_days` (25) days of the next rung's wage per head and is not hungry — at most one
  rung above its trade, gentry only in a town or city; it falls a rung after `hunger_days` (3) hungry days with less
  than `fall_days` (2) days of its own wage per head, as far as poor. Clergy, scholars, officials and lords hold their
  station by office. A move holds `hold_days` (10). `station.follow_purse: false` leaves stations to another module.
- **Death**: a resident's purse goes to their household (a grown member first) or, if they lived alone, to the
  treasury (`purse/Inheritance`, on `RESIDENT_REMOVED`).

Measured in a 30-person test village over two years: wages ~100 bits a day, folk spending ~75, purses settling around
90 bits a head, nobody hungry, the treasury bounded.

## Shops

A **shop** (`shop/Shop`, all of a settlement's in `shop/ShopBook`, key `hearth:economy/shops`) is any built workplace
`hearth_prices/shops.json` names, kept by whoever works there — a market stall, market hall or counting house deals in
everything; a bakery in food and flour; a smithy in tools and metal; a tailor in cloth, wool, leather and dye; an inn in
food; a chandler in candles and wax; a brick kiln in brick and clay... (42 workplaces) — and any trader by job
(merchant, trader, fence) without such a building. Its keeper is a worker whose trade it sells, else a merchant, else
any grown worker there; with nobody at work it is shut (keeps its stock and till, sells nothing).

- **Own stock and limited cash.** Each shop has its own shelves (goods units) and till. A new shop's till is lent by
  the treasury (`shops.starting_till`, 200, or a tenth of the treasury). Each day it **restocks** toward
  `stock_days` (4) days of its own sales (at least `min_stock` 16, at most `max_stock` 512 of each goods it deals in),
  only from what the stores hold above `town_floor` (60 %) of the town's target (less what the builders are waiting
  on) and only what `restock_share` (half) of its till can pay at `wholesale` (75 %) of the town's mid price — the
  rest stays in the till to buy from players with; shelves
  over the most it keeps go back to the stores, paid at wholesale if the treasury can. It pays `due_rate` (8 %) of its
  takings as market dues; the keeper takes `keeper_share` (30 %) of whatever the till holds above `working_till` (400) —
  into their purse, or into the owner's income if a player owns the shop.
- A shop whose building is gone closes: stock back to the stores, till to the treasury.
- `/hearth market <s> shops` lists them; `/hearth market <s> open <n>` opens one.

## Haggling

Right-clicking a row opens a parchment slip over the pages (`haggle/Haggle`, pure and deterministic; `haggle/Temper`):

- The merchant's posted price is their first word; at heart they will go `room` (15 %) below it (or above, when they
  buy — never past what their till holds). The room widens with the player's **charisma** (`charisma_room`, from the
  standing service) and **standing** (`standing_room`), and with the merchant's traits: the **greedy** give 40 % less,
  the **generous** 40 % more, the cheerful a little more, the grumpy a little less.
- The player nudges an offer (−10 %, −1, +1, +10 %; shift ×5) and makes it. The merchant takes it if it is at least as
  good as their own last word or within a fifth of their room of it; meets it part way if it is within their limit
  (less far as patience wears thin); is **offended** by an offer past their limit, losing patience in proportion to how
  far past, budging only a little. Every counter costs `round_cost` (14) patience; an insult up to `outrage_cost` (90)
  more. Patience starts at `patience` (100), moved by the merchant's opinion of the player and traits (grumpy −30 %,
  cheerful +20 %, greedy +15 %: they enjoy the game). After 12 offers anyone has had enough.
- "Take" accepts the merchant's last word. A struck deal trades at the agreed price.
- **Push too hard** and the merchant walks away: sour with the player for `sour_days` (1) — prices `sour_markup`
  (15 %) worse, no haggling — and an `economy:haggle_soured` deed (`sour_weight`, −2) with the merchant as the resident.

## Stolen goods and fences

Every item a player offers is checked with `LawService.isStolen` / `stolenFrom` (`market/StolenGoods`; without the law
module nothing is stolen). An honest merchant refuses goods stolen **in their own town** (red row, "That's stolen. I'll
not touch it") — `fence.refuse_anywhere` makes them refuse goods stolen anywhere; `fence.refuse_stolen: false` lets
them buy. A **fence** (a resident with job `FENCE`, or anyone keeping a `commerce/thieves_den`) offers "I've some goods
that need a quiet buyer." and buys stolen goods anywhere at `fence.rate` (40 %) of an honest price, no questions, no
standing earned. Contracts refuse goods stolen in their town too.

## Owning the economy

- **Businesses** (`holdings/BusinessMarket`, key `hearth:economy/holdings`): built workshops (`work/*`), farms
  (`farm/*`), extraction sites (`extract/*`: a quarry pit, a mine — the deed to a resource site's working) and the
  commercial places folk run (market stall, inn, tavern, hostel, caravanserai, caravan yard, counting house; deep forge,
  glow garden, market) are listed at the property tab with their workers and expected income. Price: land and building
  as a house, plus `holdings.income_days` (60) days of income; standing `holdings.min_reputation` (35).
- A business is not emptied like a house: its folk keep working. The town now buys their work from the business at
  `productivity` (1.8) times their wage; the worker is paid, the owner keeps the rest less `upkeep` (10 %); a shop's
  profit share goes to its owner instead of its keeper. Income is paid **into the owner's bank account** every day
  (`DepositStore`), fractions carried.
- The owner can **hire** an idle adult (no trade, no workplace) into it if it has room (its job sites): `hire_cost` (5)
  days of the wage, paid to the new hand; they take the business's trade (`ArchetypeInfo.jobs`).
- The deed (`hearth:deed` with `business: true`) is a bearer document like the others: used on the property, it moves
  the business (and its income) to whoever holds it.
- **Public works** (`works/Projects`, key `hearth:economy/works`): at the bank, the town hall (the reeve, the steward)
  or the builders' yard, "I'd like to pay toward the town's works." lists planned and rising public buildings (not homes
  or extraction plots) with their materials' worth (from the blueprint's remaining bill at the town's prices), what
  players have given and the player's own share. Gifts of a crown, ten, or "the rest" go into the project's fund: the
  planner and the sim put **funded plots** first (the sim builds them first; the planner never rebuilds them), and whenever its construction stalls
  the fund buys the missing goods from passing traders at the town's price × `works.import_markup` (1.25), straight
  into the stores. Finished, the town keeps the change; abandoned, the givers are repaid into their bank accounts, by
  share. A gift is an `economy:funded_project` deed (`fund_weight_per_crown`, up to 25).
- **Supply contracts** (`works/Contracts`): when a goods' scarcity reaches `contract_scarcity` (1.6) or the builders
  wait on it, the reeve offers (up to `max_contracts`, 3, for `offer_days`, 6) a contract: 32–512 units, paying the
  town's buying price plus `contract_premium` (35 %) — only if the treasury could pay it all. Taking one sets the sum
  aside in escrow; "Deliver what you carry" hands over what you carry of that goods (units by value), paid as it
  arrives, straight into the stores. Fulfilled: `economy:contract_fulfilled` (+4). Due in `contract_days` (8); left to
  lapse, the escrow returns to the treasury and `economy:contract_failed` (−6). Two contracts per player per town.

## Services, fines and bribes (for other modules)

`Tariff` prices services other modules sell — `inn_bed`, `inn_room_week`, `meal`, `ale`, `bath`, `stable_hire_day`,
`stable_keep_day`, `horse`, `tailor_dye`, `healer`, `dice_stake` — from `services` in the config (village prices),
× town size (camp 0.6 … city 1.6) × standing (±20 %). The api's `EconomyService` carries `servicePrice`, `chargeService`
(coin to the vendor's till or purse), `fine` (to the treasury), `bribe` and `payResident` (to a resident's purse),
`takeFromPurse` (pickpocketing, confiscation), `purse`, `householdWealth`, `fundedPlots` and `businessOwner`. The law
pays fines and bribes through them, and a pickpocket takes the mark's real purse coin. While this module runs it alone
moves stations (the sim's own rule stands aside).

## Banks and the mint

The bank counter (`bank/Bank`, `BankMenu`, `BankScreen`), opened by a **MINTER** (or anyone working at
`hearth:commerce/bank`) with "I'd like to see to my account.". Its tabs: Bank, Property, Works, Contracts (a town hall's
counter has all but Bank):

- **Deposits**: deposit and withdraw in marks, crowns or everything. One ledger for the whole world
  (`bank/Deposits`, kept as the WORLD-scope `HearthDataKey` `hearth:economy/deposits` with the overworld; until the sim
  provides world data it lives in a Fabric attachment on the overworld and moves over on first use), so **a deposit is
  honoured at every bank**. Deposits are not in the treasury and earn no interest.
- **Money changer**: emeralds (vanilla's villager currency) for coin and back: it buys at 18 bits from the treasury and
  sells at 24 bits from its own drawer of emeralds (seeded by town size, refilled by what players sell).
- **The mint** is part of the daily economy (above): a town with a minter or a bank strikes its metal into coin.

## Property

At the bank, or from the reeve, the steward or whoever keeps the town hall: **"Is there property for sale?"** opens the
property tab.

- For sale: built houses (`hearth:house/*`, state BUILT) that nobody lives in, housing plots staked out but not begun
  (state PLANNED), and businesses (above). The player's own property and businesses there are listed too.
- **Price** = land + house: `area × landPerBlock × town factor` (camp 0.5 … city 2.2) + for a house
  `area × storeys × buildingPerBlock × (1 + 0.35 × finish tier)`, less up to 20 % at reputation 100. Standing needed:
  25 for a house, 10 for a plot.
- Buying pays the treasury, calls `Settlement.setPlotOwner(plot, player)` (its folk then leave it alone — the sim's
  part) and gives a **deed** (`hearth:deed`, component `hearth:deed`) naming the settlement, the building, its size and
  place. A deed is a bearer document: whoever holds it can claim the title by using it on the property, so land can be
  sold or given between players.

## Paying and charging players from other modules

```java
EconomyService economy = HearthServices.economy();     // null if the economy module is absent
if (economy != null) {
	long carried = economy.balance(player);             // coins + pouches (not bank deposits)
	if (economy.charge(player, 250)) { ... }            // all or nothing, change made automatically
	economy.pay(player, 1200);                          // fewest coins: pouches, stacks, empty slots, rest at their feet
	Component text = economy.format(1234);              // "12 crowns, 3 marks, 4 bits" (localised)
	long ask = economy.price(settlement, new ItemStack(Items.STONE, 32), false);   // what the town pays for 32 stone
}
```

**Quest rewards**: take the coin from the settlement first, then pay the player, so money is never made from nothing:

```java
if (settlement.withdraw(reward)) {
	economy.pay(player, reward);
} // else offer a smaller reward, or none: the town cannot pay
```

`price(settlement, stack, playerBuys)` prices the **whole stack** (`stack.getCount()` items; one item for a single):
a supply quest can offer, say, 1.5 × `price(settlement, needed, false)`.

## Config — `config/hearth/economy.json`

| Section | Field | Default | Meaning |
|---|---|---|---|
| `pricing` | `spread` | 0.3 | gap between asking and paying price (fraction of mid) |
| | `scarcity_elasticity` / `_min` / `_max` | 0.8 / 0.5 / 3.0 | the scarcity curve |
| | `target_fraction`, `reserve_days` | 0.35, 6 | a town's target stock |
| | `surplus_above` | 1.15 | a town sells only above target × this |
| | `reputation_effect`, `min_trade_reputation` | 0.25, −50 | |
| | `builder_premium` | 0.3 | extra paid for what the builders need |
| | `culture_effect` | 1.0 | 0 makes every town alike |
| | `drift_sigma`, `drift_reversion`, `drift_limit` | 0.05, 0.15, 0.35 | daily price wander (log scale) |
| | `wares_per_goods` | 3 | items listed per goods |
| `treasury` | `tax_per_head`, `wage_per_worker` | 2, 1 | bits a day |
| | `trade_income_rate`, `upkeep_rate` | 0.02, 0.004 | |
| | `mint_units_per_day`, `mint_units_per_minter`, `bits_per_metal_unit` | 2, 4, 6 | |
| `bank` | `emerald_buy_price`, `emerald_sell_price` | 18, 24 | |
| | `emerald_reserve_base`, `emerald_reserve_per_tier` | 4, 6 | |
| `property` | `house_min_reputation`, `plot_min_reputation` | 25, 10 | |
| | `land_per_block`, `building_per_block`, `reputation_discount` | 6, 4, 0.2 | |
| | `site_effect` | 0.15 | goods the town's own resource sites yield are this much cheaper there |
| `households` | `enabled` | true | purses at all (off: the phase 2 treasury model) |
| | `wages` | poor 2 … lord 25 | bits a working adult earns a day, by the station their trade brings |
| | `poll_tax`, `food_per_head` | 1, 1 | |
| | `reserve_days`, `comfort_share`, `inn_share`, `tithe` | 6, 0.06, 0.3, 0.004 | how households spend |
| | `starting_purse_days`, `wage_cap_share` | 10, 0.25 | |
| | `consumed_goods` | cloth, candles, luxury, books, dye, specialty | goods folk use up (food is paid, not taken) |
| `shops` | `stock_days`, `min_stock`, `max_stock` | 4, 16, 512 | |
| | `wholesale`, `starting_till`, `working_till`, `keeper_share`, `due_rate`, `town_floor`, `restock_share` | 0.75, 200, 400, 0.3, 0.08, 0.6, 0.5 | |
| `haggle` | `room`, `charisma_room`, `standing_room` | 0.15, 0.5, 0.4 | |
| | `patience`, `round_cost`, `outrage_cost` | 100, 14, 90 | |
| | `sour_days`, `sour_markup`, `sour_weight` | 1, 0.15, −2 | |
| `holdings` | `min_reputation`, `income_days`, `productivity`, `upkeep`, `hire_cost` | 35, 60, 1.8, 0.1, 5 | |
| `works` | `import_markup`, `fund_weight_per_crown` | 1.25, 0.5 | |
| | `contract_premium`, `contract_days`, `offer_days`, `max_contracts`, `contract_scarcity` | 0.35, 8, 6, 3, 1.6 | |
| | `fail_weight`, `done_weight` | −6, 4 | |
| `fence` | `refuse_stolen`, `refuse_anywhere`, `rate` | true, false, 0.4 | |
| `services` | `inn_bed` … `dice_stake` | 6 … 5 | base prices of services (bits, village) |
| `station` | `follow_purse`, `rise_days`, `fall_days`, `hunger_days`, `hold_days` | true, 25, 2, 3, 10 | |
| `coins` | `pouch_capacity` | 512 | |
| | `melt_bit`, `melt_mark`, `melt_crown` | 1, 1, 2 | nuggets from melting (0: no melting) |
| `loot` | `<category>` → `chance`, `bits_min/max`, `marks_min/max`, `crown_chance`, `crowns_max` | see `EconomyConfig.CoinLoot.DEFAULTS` | |

Prices, goods, cultures, wares and shop kinds are data, not config: override them with a datapack (`/reload` applies
them).

## Commands (permission level 2)

| Command | |
|---|---|
| `/hearth money give <player> <amount>` | amount in bits (`250`) or coins (`2c5m3b`, `3crowns`) |
| `/hearth money take <player> <amount>` | charges, making change; fails (takes nothing) if they cannot pay |
| `/hearth money balance [player]` | carried coin and bank deposit |
| `/hearth market <settlement> prices` | stock/capacity, scarcity and a local ware's buy/sell price for each goods, builders' needs, yesterday's books (`<settlement>` by name, quoted if it has spaces, or id) |
| `/hearth market <settlement> open [n]` | opens that settlement's market for you (with `n`: its n-th shop) |
| `/hearth market <settlement> shops` | each shop: kind, keeper, till, fullest shelves (fences marked) |
| `/hearth market <settlement> purses` | households, their coin, stations, who went hungry, the richest five |
| `/hearth market <settlement> counter` | its bank / town hall counter (works and contracts) |
| `/hearth economy sandbox [culture]` | dev: opens the market of an in-memory `SimpleSettlement` of that culture (no sim needed) |
| `/hearth economy sandbox bank` / `property` / `works` / `contracts` | dev: its counter on that tab |
| `/hearth economy sandbox shop <n>` / `shops` / `purses` | dev: its n-th shop (1 stall, 2 bakery, 3 tailor, 4 inn, 5 smithy, 6 the fence) / lists |
| `/hearth economy sandbox talk` | dev: goes through `EconomyDialogue` as the NPC module will (lists the merchant's options, picks "Let's trade") |
| `/hearth economy sandbox days <n>` | dev: runs n days of its daily economy |
| `/hearth economy sandbox stall <goods> <amount>` | dev: its builders report a shortage (drains that goods) |
| `/hearth economy sandbox reputation <value>` | dev: sets your standing there |

Dev pilot: `HEARTH_DEV_ECONOMY=shots ./gradlew runClient` creates a test world, drives the sandbox market, bank and
property screens, then the baker's and tailor's shops, a haggle (an insult, then a fair offer), the fence, buying a
business, funding the chapel and taking a contract, and saves screenshots to `run/screenshots/economy/`, then quits.

## Performance

- Nothing per tick. Daily work is O(goods + residents + plots + shops × goods) per settlement per sim day — about
  0.14 ms for a town of 150 (EconomyMeasureTest), so a 30-day catch-up costs ~4 ms once; market and counter views are
  built only when a player opens or clicks one (the works tab reads up to 12 cached blueprints). Price and goods lookups
  are cached per item and dropped on `/reload`.
- Per-settlement state, saved by the sim with the settlement: `hearth:economy/ledger` (`MarketLedger`: a few arrays of
  29, the books, today's standing gains), `hearth:economy/shops` (each shop's shelves and till, sour merchants),
  `hearth:economy/holdings` (owned businesses), `hearth:economy/works` (funded projects, contracts). Purses live in
  `Resident.extra("economy")`: a long and a few small fields per resident.
- Coin pickup adds one item check per item pickup; the furnace mixin one check per finished smelt.

## Files

- `economy/coin/` — `Coin`, `Change` (pure change-making), `Purse` (pure purse model), `PlayerPurse` (inventory adapter),
  `CoinFormat`, `CoinPickup`, `CoinMelting`
- `economy/item/` — coins, pouch, deed and their components
- `economy/price/` — `PriceData` (data loading), `PriceTable` (lookups and caches), `PriceModel` (pure), `MarketPricer`
- `economy/market/` — `Market`, `MarketMenu`, `MarketView`, `MarketLedger`, `Standing`, `TradeCounter`, `StolenGoods`
- `economy/purse/` — `Purses`, `Households`, `PurseEconomy`, `StationRule`, `Inheritance`
- `economy/shop/` — `Shop`, `ShopBook`, `Shops`
- `economy/haggle/` — `Haggle`, `Temper`
- `economy/holdings/` — `Holdings`, `BusinessMarket`
- `economy/works/` — `Works`, `Projects`, `Contracts`
- `economy/` — `Reputation` (standing service bridge), `Tariff` (service prices), `EconomyAccess`
- `economy/bank/` — `Bank`, `BankMenu`, `BankView`, `Deposits`, `DepositStore`
- `economy/property/` — `PropertyMarket`, `PropertyNames`
- `economy/daily/SettlementEconomy`, `economy/loot/CoinLootTables`, `economy/dialogue/EconomyDialogue`,
  `economy/command/`, `economy/net/`, `economy/sandbox/Sandbox`
- client: `MarketScreen`, `BankScreen`, `EconomyGui`, `PouchTooltip`, `EconomyDevPilot`
- tools: `tools/economy/gen_textures.py` (coin, pouch, deed and GUI textures), `tools/economy/gen_data.py` (goods tags,
  prices, cultures, shop kinds; validates every id against the 26.3 client jar and Expanse, expands vanilla tags to
  check no item is in two goods and every ware belongs to the goods it is sold as)
- tests: `src/test/java/dev/voidmc/hearth/economy/` — change-making (exhaustive), purse (pouches, atomicity, value
  conservation), price model (monotonicity, spread, standing, lots), format, deposits, daily economy, property, loot;
  phase 3: goods data coverage, haggling and temper, station rule, purses (seeding, conservation day by day, food and
  comforts, hunger and ruin, prosperity, boundedness over years, inheritance), shops (keepers, fences, closing,
  restock limits, limited cash, dues, saving), works and holdings (funding, imports, refunds, contracts, lapses,
  business income, tariffs, saving), measurements
