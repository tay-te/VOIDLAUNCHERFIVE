# Playthrough: a new player's first visit (2026-10-19)

Tour: `hearth/src/gametest/java/dev/voidmc/hearth/gametest/play/PlaythroughTour.java`, run with
`JAVA_TOOL_OPTIONS="-Dhearth.tour=play -Dhearth.play.out=<dir>" ./gradlew runClientGameTest` (take the game lock first).
It founds a temperate town on the Expanse Earth world (seed 20261003; the only temperate site within 1.6 km of spawn
is at 35, 94, -2654), grows it 100 days (stores topped up for the first 70), then plays it in survival through the
game's own inputs: right-clicking folk and blocks, the conversation, market, bank, notice board, journal and
confrontation packets exactly as their screens send them. Commands only set the stage (the hour, a traveller's goods,
4 amber for a fetch quest, 150 bits once the market could not pay, a pardon before the night step).

The town is "Bradfield", a city of about 180 souls and 550 plots. Raw output from the last run: `results.md` and 19
JPEGs in `C:/Users/bitto/AppData/Local/Temp/claude/C--Users-bitto-VOID/564c6839-160a-4660-85ef-00c49dcb634f/scratchpad/play/`.
The tour ran five times while it was being fixed. The results below are from the last run, with the earlier runs
where they differ.

| Step | Last run | Earlier runs | What happened |
|---|---|---|---|
| 1 Arrive | FAIL | PASS ×4 | The banner ("Bradfield, a timber-framed city of 176 souls") and the standing line ("a Stranger in Bradfield · standing 0") show on walking in at 35, 80, -2340. In the last run no folk were embodied for 16 s after arriving. Earlier runs had 11–27 folk out, 5–9 of them walking. |
| 2 Talk | PASS | FAIL (fixed) | Greeting, 7–8 options, "[Flatter 39%]" speech check. The outcome now shows as the resident's line. |
| 3 Money | PASS (hearth-fix-econ) | FAIL / FAIL | Re-run after the till fix: the merchant's till held 406 bits and 5 of the 6 kinds of goods carried could be sold. Sell, buy, haggle (12 for 13) and bank deposit and withdraw all work and the sums are exact. Before: the till held 2 bits, so 1 of 6 kinds could be sold. |
| 4 Quest | PASS | FAIL (tour) | Board at 49, 95, -2654 → "Amber from the hollows" accepted → HUD shows "(0/4, 4 carried)" → turned in to the giver: +102 bits, standing 0 → 3, in the history. |
| 5 Crime | PASS | FAIL (tour) / FAIL | Barrel in the tavern at 88, 94, -2632 robbed while its keeper watched. She reported it within 2 s (bounty 30). A guard confronted the player about 35 s later. The fine was paid (coins 236 → 206, stolen goods taken), standing 3 → -6. A warehouse barrel robbed at night (-8, 88, -2505) went unreported. In run 4 no guard came in 75 s (see 3 below). |
| 6 Night | FAIL (hearth-fix-econ) | FAIL / FAIL | Re-run after the guest-bed fix: inn #53 has 2 bed points and 0 residents; a bed was rented (6 bits) and slept in until morning. Still FAIL on folk abed: 22/48 in their own beds at midnight, 12 still out (earlier runs 39–40/48, 22/48). Before: every inn answered "every bed's taken". |
| 7 Save/reload | PASS | PASS | Coins, deposit, standing, quest history, wanted state, the town (183 souls, 581 plots) and its residents all came back. 48 folk were about 15 s after the reload. |
| 8 Away and back | PASS | PASS | Away 12 days 5 km off: 183 → 235 souls, 20 plots changed (a quarry, two scribe houses, a logging camp, a barn and houses finished, new fields and roads). |

## Failures, worst first

1. **FIXED (hearth-fix-econ): No inn has a bed for a traveller (HIGH: the night at the inn cannot be played).** Guest houses (inn, tavern, hostel, caravanserai) now give no home beds, so nobody is housed there and the planner builds homes instead (`SimSettlement.homeBeds`, `Needs`), and `Inn.freeBed` lets any bed not let tonight. Earlier finding: I asked every innkeeper in all
   four runs that got this far. Each inn (#54 at 21, 92, -2566; #342 at 62, 91, -2541) has 2 bed points with 2
   residents living there, and the hostel (#218/#221) has 8 bed points with 8 residents. `Inn.freeBed` keeps the first
   N beds for the N residents whose home is the inn, so none are ever free.
   Likely cause: the sim houses people in any plot with beds, guest rooms included (`sim/settlement/SettlementClock.java`
   `moveIn` / the housing pass that uses `p.beds`). It should count only the keeper's household at
   `commerce/inn|hostel|caravanserai`, or not count guest beds as housing at all. The small inn variant also has only
   two guest rooms (`build/archetype/commerce/Inn.java`).
2. **FIXED (hearth-fix-econ): Shop tills are empty, so a new player cannot sell (HIGH: breaks the first money loop).** An open shop's till is topped up each day from the treasury to a floor (`shops.till_floor` 150, scaled by town size and trade, at most `floor_draw` 5 % of the treasury a day, never above `working_till`), and wages come from the till only above it (`Shops.topUp`, `PurseEconomy.wages`). Coin only moves. Earlier finding: The player starts with
   0 coin, and the merchant's till held 2–3 bits in 4 of 5 runs. Of the goods the traveller carried, only 64 cobblestone
   for 2 bits could be sold. The one run with 200 bits in the till hit a shop that had just opened on its starting
   loan. Likely cause: `economy/shop/Shops.daily`/`restock` spends up to half the till on wholesale every day
   (`restockShare` 0.5), `purse/PurseEconomy` pays wages out of the till, and the tills only refill from folk's
   purchases, so they decay toward zero while the treasury holds about a million bits. Possible fixes: a till floor
   kept back for buying from players, or letting the town counter buy what the shop cannot. When the town's stores
   are full, the town counter marks every row `NO_ROOM` (run 4). That was partly caused by the tour's topped-up stores.
3. **Guards do not reliably answer a crime (HIGH/MED).** Bradfield has 10 guards, but only 1–3 were embodied near the
   player, and in daytime they were often asleep. In run 4 (06:24) the only embodied guard slept 40 blocks away and
   nobody came in 75 s. In the last run a guard confronted the player about 35 s after the report, while the guard the
   tour was watching walked away to a gathering (`social/gather`). Likely cause: folk exist only near the player, and
   nothing sends an awake guard toward a wanted player, so the response depends on who happens to be standing nearby
   (`npc/embody`, `law/justice`, the guard behaviour in `npc/work`).
4. **Folk often cannot be talked to (MED).** In run 4, three of four innkeepers at about 17:00 and the reeve at 10:00
   did not open a conversation when right-clicked. Sleeping folk refuse (`Folk.mobInteract` returns PASS unless the
   player sneaks), and the player gets no feedback at all. In the last run the tavern keeper was "not embodied" at
   18:51. A "they're asleep" line, or waking them when sneaking with a hint that sneaking does that, would help.
   (`npc/entity/Folk.java`; folk schedules.)
5. **An empty town on arrival (MED, 1 run in 5).** In the last run there were 0 folk embodied for 16 s after walking in
   at 35, 80, -2340. The other runs had 11–27. Embodiment around a newly arrived player is slow or skipped
   (`npc/embody`).
6. **Quest HUD clips its line (LOW).** "Bring 4 × Amber (0/4, 4 ca" is cut off (`4_quest_hud.jpg`). `QuestHud.WIDTH`
   168 is too narrow for the carried hint (`client/quest/QuestHud.java`).
7. **The quest journal can be sold (LOW).** The journal the quest module gives (a writable book) shows up as a sell row
   at 1 bit, and the tour sold it in run 2 (`economy/market`: quest items should be excluded).
8. **Late trespass reports (LOW, as designed?).** A trespass seen in step 5 was filed about 90 s later, after the
   player had left (the witness's `report_seconds` deadline), so the player becomes wanted with no visible cause
   (`law/crime/CrimeDesk`).
9. **IntegrationTour's notice-board check is wrong (tour bug, LOW).** The `NOTICE_BOARD` point of interest is the spot
   in front of the board, not the board itself. `IntegrationTour.noticeBoard` therefore never finds one and places a
   second board by the well.

## Fixed in this pass

- **Speech checks and service answers now show in the conversation.** `StandingDialogue` and `SocialDialogue` never
  implemented `DialogueProvider.reply`. The resident's line stayed the same after a flatter, a bed request, the news
  and so on, and the answer went only to chat, which the conversation screen covers. Both now return what the
  resident said (`standing/speech/StandingDialogue.java`, `social/talk/SocialDialogue.java`).
- **Taverns no longer offer lodging.** `ServiceSites.innkeeper` counts tavern keepers, and taverns have no beds, yet
  they offered "A bed for the night" and then answered "every bed's taken". Beds and rooms are now offered only where
  the house has `BED` points (`SocialDialogue.collect`). The last run predates the tour's matching change, which skips
  bedless taverns instead of failing on them.
- Targeted tests `dev.voidmc.hearth.social.*` and `dev.voidmc.hearth.standing.*` pass.

## Life fixes (branch hearth-fix-life, 2026-10-19): steps 1, 5 and 6

Three tours were run on this branch (raw output of the last: `results.md` and JPEGs in
`C:/Users/bitto/AppData/Local/Temp/claude/C--Users-bitto-VOID/564c6839-160a-4660-85ef-00c49dcb634f/scratchpad/life/play/`).
Last run: 1 PASS, 2 PASS, 3 FAIL (tills, not this branch), 4 PASS, 5 PASS, 6 FAIL (inn beds only), 7 PASS, 8 PASS.

| Step | Result | What changed |
|---|---|---|
| 1 Arrive | PASS | Cause found in the save: the town's bounds run 120 blocks out along a road to a bridge at -2340, and the nearest house is at -2459, beyond the 96-block spawn radius, so nobody's routine put them within reach. Embodiment itself (budget, ordering) was prompt. Now a town with a player near always has its 6 nearest residents (within twice the radius) about: 5–6 folk out at the bridge after 5 s (0 before). The tour then walks on to the first houses: 14–16 folk, 5–6 walking, 3 at work. |
| 5 Crime | PASS | Guards now keep overlapping watches (night 17:30–07:00, day 06:35–18:00, lunch in two sittings), so some are always on duty. A filed crime calls `law/folk/GuardDispatch`. It sends the nearest awake guard on duty within 64 blocks. Failing that, it makes one on duty appear about 22 blocks off, in the open and out of the player's sight. A guard sent comes without needing to see the player, and moves closer, out of sight, when stuck for 6 s. If nobody can come in 45 s, the bounty is posted and the player told. Last run: a guard was sent 1 s after the report and confronted the player 2 s later (it was 35 s, or never). In run 1 of this branch, a guard made to appear stood 75 s with no path to the player. That led to the open-air spawn and close-in. |
| 6 Night | FAIL (inn beds) | Right-clicking a sleeper now gets a mumble in chat ("Hnn? ...go 'way...") and an overlay hint "X is asleep. Sneak and use to wake them". Sneak-waking gets a grumpy greeting. Schedules were checked for every trade, age, trait and culture: only the night watch, night jailers and a lazy entertainer sleep between 09:00 and 19:00. Innkeepers "not embodied" when called came from the per-town cap of 48 nearest, which folded a called-over folk straight back. Folk made to appear on purpose are now kept 90 s, and both innkeepers opened a conversation. Renting a bed still fails: every bed is taken (failure 1 above). |
