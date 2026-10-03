# VOID Hearth — coin, markets and treasuries

The economy module (`dev.voidmc.hearth.economy`, client `dev.voidmc.hearth.client.economy`). Money in Hearth is meant
to feel as if it was always part of Minecraft: coins you can hold, struck from the game's own metals, earned and spent
with folk at their stalls. There is no balance on the HUD; what you carry is what you have.

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
            taxes, passing trade                  wages, upkeep
residents ───────────────────────► TREASURY ──────────────────────► residents / gone
                                  ▲   │  ▲
         player buys at market ───┘   │  └── mint: metal from the stockpile struck into coin
                                      ▼
                         player sells at market, quest rewards (via EconomyService.pay)
```

- **Treasury** (`Settlement.treasury()`, owned by the sim; the economy only deposits and withdraws). A town that cannot
  pay does not buy, and quests should only offer what the treasury can pay.
- **Daily** (`daily/SettlementEconomy`, on `SettlementEvents.DAY_PASSED`, including caught-up days — a few dozen
  arithmetic operations, no world access):
  - taxes: `taxPerHead × population`; passing trade: `tradeIncomeRate ×` the base value of the stock above target;
  - wages: `wagePerWorker ×` working adults (if the treasury cannot pay, it pays what it has and counts unpaid days);
  - upkeep: `upkeepRate ×` treasury — this keeps treasuries bounded: they settle near `net income / upkeepRate`;
  - **the mint**: with a minter or a built `hearth:commerce/bank`, up to `mintUnitsPerDay + mintUnitsPerMinter ×
    minters` units of METAL above half its target become `bitsPerMetalUnit` bits each;
  - price drift, the town's own use of each goods, and expiry of builders' needs (below).
- **Treasure**: coin is added to built-in chest loot with Fabric's `LootTableEvents.MODIFY` (`loot/CoinLootTables`): a
  roll for bits and marks at the category's chance, and a separate rarer roll for crowns. Categories: village chests,
  shipwreck (supply/map) and shipwreck treasure, underwater ruins and ruined portals, buried treasure, dungeons and the
  abandoned camp's secret chest, mineshafts, ancient cities (marks and crowns: old money), desert/jungle temples,
  strongholds, woodland mansions, pillager outposts, igloos and camps; and when VOID Expanse is installed, its
  `expanse:chests/micro/lost_chest`, every structure chest (`expanse`) and every hidden stash (`expanse_hidden`),
  but not its archaeology (brushed) tables or map chests. Amounts per category in the config.
- **New settlements**: on `SettlementEvents.FOUNDED`, a treasury still at 0 is seeded with 40 bits per head.

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
ingot...), the fallback values and the default wares. **Stockpile units** for an item are its value over its goods' unit
value (a log is 4 WOOD units, stone bricks 2–3 STONE), so a town's stores and its prices agree.

### Goods

Items map to the ten `Goods` through the item tags `#hearth:goods/<goods>` (`data/hearth/tags/item/goods/`), built from
vanilla tags (`#minecraft:logs`, `#minecraft:wool`, `#minecraft:terracotta`, ...) plus items, with Expanse entries marked
`required: false`. Emeralds are deliberately in no goods tag: they belong to the money changer.

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
- **culture**: `hearth_prices/cultures/<culture>.json` gives each culture a factor per goods (below 1 = plentiful and
  cheap there): karst towns are rich in bamboo and limestone, desert towns in sandstone, sand and dyes, mesa towns in
  terracotta and gold, maritime towns in fish, steppe towns in leather and wool... `cultureEffect` scales them.
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

Opened by a merchant's **"Let's trade."** (`dialogue/EconomyDialogue`, a `DialogueProvider`: MERCHANT residents and
anyone working at a market stall, market hall or shophouse). Server-authoritative (`market/Market`, `MarketMenu`;
the client draws `MarketView` and sends clicks as `EconomyActionPayload`).

- **For sale** (left page): for each goods in **surplus** (stock above `target × surplusAbove`), up to three of the
  culture's own wares (WOOD → local logs and planks, Expanse wisteria, redwood, palm or olive where installed; FOOD →
  its bread, fish, venison...; STONE → its building stone; CLAY → its bricks, terracotta or mud brick...). Buying takes
  the units from the stockpile and puts the coin in the treasury.
- **Wanted** (right page): everything the player carries that the town deals in, priced by how short it is, plus a row
  for each thing the builders need that the player lacks. Selling puts the goods in the stockpile (up to twice the
  target and the store's capacity) and pays from the treasury; a town that cannot pay does not buy.
- **Selling timber and stone helps the town build.** When a construction stalls (`CONSTRUCTION_STALLED`), the economy
  remembers the missing goods and the building. The merchant says so ("Our builders are waiting on stone for the
  chapel. I'll pay you well for it."), those rows sit at the top with a hammer and a green wash, pay a premium, and a
  sale says "The builders will put it into the chapel." The goods go straight into the stockpile, so construction can
  resume; the need is forgotten once the stockpile holds it (or after 8 days).
- Click: one lot. Shift-click: a stack (buying) or all you carry (selling). Every trade refreshes the whole view (new
  prices, purse, treasury) and its result shows on the merchant's note strip.
- Honest trade earns standing: +1 reputation per crown of business, at most 3 a day per settlement.
- The UI: an oak frame round two parchment pages, coin amounts drawn as little coins, your inventory below
  (`textures/gui/economy/market.png`, generated by `tools/economy/gen_textures.py`).

## Banks and the mint

The bank counter (`bank/Bank`, `BankMenu`, `BankScreen`), opened by a **MINTER** (or anyone working at
`hearth:commerce/bank`) with "I'd like to see to my account.":

- **Deposits**: deposit and withdraw in marks, crowns or everything. One ledger for the whole world
  (`bank/Deposits`, kept as the WORLD-scope `HearthDataKey` `hearth:economy/deposits` with the overworld; until the sim
  provides world data it lives in a Fabric attachment on the overworld and moves over on first use), so **a deposit is
  honoured at every bank**. Deposits are not in the treasury and earn no interest.
- **Money changer**: emeralds (vanilla's villager currency) for coin and back: it buys at 18 bits from the treasury and
  sells at 24 bits from its own drawer of emeralds (seeded by town size, refilled by what players sell).
- **The mint** is part of the daily economy (above): a town with a minter or a bank strikes its metal into coin.

## Property

At the bank, or from whoever keeps the town hall: **"Is there property for sale?"** opens the property tab.

- For sale: built houses (`hearth:house/*`, state BUILT) that nobody lives in, and housing plots staked out but not
  begun (state PLANNED). The player's own property there is listed too.
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
| `coins` | `pouch_capacity` | 512 | |
| | `melt_bit`, `melt_mark`, `melt_crown` | 1, 1, 2 | nuggets from melting (0: no melting) |
| `loot` | `<category>` → `chance`, `bits_min/max`, `marks_min/max`, `crown_chance`, `crowns_max` | see `EconomyConfig.CoinLoot.DEFAULTS` | |

Prices, goods, cultures and wares are data, not config: override them with a datapack (`/reload` applies them).

## Commands (permission level 2)

| Command | |
|---|---|
| `/hearth money give <player> <amount>` | amount in bits (`250`) or coins (`2c5m3b`, `3crowns`) |
| `/hearth money take <player> <amount>` | charges, making change; fails (takes nothing) if they cannot pay |
| `/hearth money balance [player]` | carried coin and bank deposit |
| `/hearth market <settlement> prices` | stock/capacity, scarcity and a local ware's buy/sell price for each goods, builders' needs, yesterday's books (`<settlement>` by name, quoted if it has spaces, or id) |
| `/hearth market <settlement> open` | opens that settlement's market for you |
| `/hearth economy sandbox [culture]` | dev: opens the market of an in-memory `SimpleSettlement` of that culture (no sim needed) |
| `/hearth economy sandbox bank` / `property` | dev: its bank counter / property for sale |
| `/hearth economy sandbox talk` | dev: goes through `EconomyDialogue` as the NPC module will (lists the merchant's options, picks "Let's trade") |
| `/hearth economy sandbox days <n>` | dev: runs n days of its daily economy |
| `/hearth economy sandbox stall <goods> <amount>` | dev: its builders report a shortage (drains that goods) |
| `/hearth economy sandbox reputation <value>` | dev: sets your standing there |

Dev pilot: `HEARTH_DEV_ECONOMY=shots ./gradlew runClient` creates a test world, drives the sandbox market, bank and
property screens and saves screenshots to `run/screenshots/economy/`, then quits.

## Performance

- Nothing per tick. Daily work is O(goods + residents) per settlement per sim day; market views are built only when a
  player opens or clicks a market. Price and goods lookups are cached per item and dropped on `/reload`.
- Per-settlement state (`MarketLedger`, `hearth:economy/ledger`): a few arrays of 10, the books, and today's standing
  gains (pruned daily). Saved by the sim with the settlement.
- Coin pickup adds one item check per item pickup; the furnace mixin one check per finished smelt.

## Files

- `economy/coin/` — `Coin`, `Change` (pure change-making), `Purse` (pure purse model), `PlayerPurse` (inventory adapter),
  `CoinFormat`, `CoinPickup`, `CoinMelting`
- `economy/item/` — coins, pouch, deed and their components
- `economy/price/` — `PriceData` (data loading), `PriceTable` (lookups and caches), `PriceModel` (pure), `MarketPricer`
- `economy/market/` — `Market`, `MarketMenu`, `MarketView`, `MarketLedger`, `Standing`
- `economy/bank/` — `Bank`, `BankMenu`, `BankView`, `Deposits`, `DepositStore`
- `economy/property/` — `PropertyMarket`, `PropertyNames`
- `economy/daily/SettlementEconomy`, `economy/loot/CoinLootTables`, `economy/dialogue/EconomyDialogue`,
  `economy/command/`, `economy/net/`, `economy/sandbox/Sandbox`
- client: `MarketScreen`, `BankScreen`, `EconomyGui`, `PouchTooltip`, `EconomyDevPilot`
- tools: `tools/economy/gen_textures.py` (coin, pouch, deed and GUI textures), `tools/economy/gen_data.py` (goods tags,
  prices, cultures; validates every id against the 26.3 client jar and Expanse)
- tests: `src/test/java/dev/voidmc/hearth/economy/` — change-making (exhaustive), purse (pouches, atomicity, value
  conservation), price model (monotonicity, spread, standing, lots), format, deposits, daily economy, property, loot
