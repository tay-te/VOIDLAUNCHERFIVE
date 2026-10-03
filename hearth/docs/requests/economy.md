# Requests from the economy module

## 1. `EconomyService.price`: document that it prices the whole stack

**Done** (lead, 71fee57): the Javadoc says the whole stack.

**What.** Change the Javadoc of `EconomyService.price(Settlement, ItemStack, boolean)` to: "What a settlement's market
charges for this stack ({@code stack.getCount()} items) or pays for it."

**Why.** The economy implements it for the whole stack. A quest paying for "32 stone" wants one call, and pricing one
item and multiplying rounds every item to a whole bit (a cobblestone is worth a tenth of a bit). For a single item it is
the same as today's wording.

## 2. A gametest entrypoint for the economy tour (optional)

**Done differently** (integration): `gametest/IntegrationTour` opens the real market through a merchant against a sim settlement; the env-gated `EconomyDevPilot` stays for the sandbox screens.

**What.** `src/gametest/resources/fabric.mod.json` → `"fabric-client-gametest": ["dev.voidmc.hearth.gametest.EconomyTour"]`
if the lead wants the economy in the client game test. Until then the economy has its own dev pilot:
`HEARTH_DEV_ECONOMY=shots ./gradlew runClient` (client package, env-var gated) makes a test world, drives the sandbox
market, bank and property screens and saves screenshots to `run/screenshots/economy/`.

## 3. Shared lang keys

**Done** (integration): only economy.json defines `hearth.goods.*`; `hearth.archetype.*` is left to the build module (asked for in the integration report).

**What.** The economy defines `hearth.goods.<goods>` (all ten `Goods`, in `economy.json`) because the market shows goods
names. If another module also defines them, the text must match or the lang merge fails the build.

It reads, but does not define, `hearth.culture.<culture>` (sim) and `hearth.archetype.<category>.<name>` (build: the
display name of an archetype, e.g. `hearth.archetype.house.cottage` → "Cottage"). Both fall back to the id made
readable when absent. Proposal: the build module defines `hearth.archetype.*` for every archetype in §6.1 so deeds,
the property list and the merchant ("our builders are waiting on stone for the *chapel*") name buildings nicely.

## 4. Settlement treasury at adoption (note for sim)

**Done** (integration): new camps get the sim's `founding.treasury` (200), so the economy's seed never applies to sim towns; when the economy module is present the clock leaves its per-worker coin out (`SimContext.economyHandlesWages`) and an adopted history keeps at most 60 bits a head.

The economy seeds `40 bits × population` (at least 4 heads) on `SettlementEvents.FOUNDED` **only if** the treasury is
still 0, so a sim that seeds its own treasury wins. Suggested sim seed for historic settlements: about 40 bits per head
plus whatever its age implies; `SettlementEconomy` then runs taxes, wages and upkeep daily (treasuries settle around
`(taxes + trade − wages) / upkeepRate`, roughly 80–250 crowns for a village).

## 5. Where builders' needs come from (note for sim/quest)

**Done** (integration): design unchanged; the sim now holds incoming goods for the oldest stalled plot, so what a player sells or supplies reaches the build that asked for it.

The market's "the builders need this" highlight and premium come from `SettlementEvents.CONSTRUCTION_STALLED` (missing
goods and the plot). The need is cleared when the stockpile holds the missing amount, or after 8 sim days. A quest
module that wants the same list can read it from the economy's ledger (`EconomyModule.LEDGER`), or the api could grow a
`Settlement.shortages()`; either is fine with the economy.

## 6. MERCHANT / MINTER embodiment (note for npc)

**Checked** (integration): the trade option appears for a MERCHANT resident in the client tour and the market trades against the sim settlement (stockpile and treasury change).

`EconomyDialogue` offers "Let's trade." to `JobType.MERCHANT` residents and anyone whose work plot is
`hearth:commerce/market_stall`, `commerce/market_hall` or `house/shophouse`; banking and property to `MINTER` residents
and `commerce/bank` workers; property alone to `civic/town_hall` workers. Option ids: `economy:trade`, `economy:bank`,
`economy:property` (orders 100, 110, 120). Selecting one opens a menu, replacing the conversation screen.
