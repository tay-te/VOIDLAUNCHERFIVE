# Lane B4 (the player's place, LIVING_WORLD_2.md §8): what landed, and what it asks of others

From lane B4, 2026-10-21, branch `hearth-lwB4` (cut from `hearth` after batch A).

## Screens to design in Figma first (§10)

None is built in code; everything ships through conversation options (order bands of §0.4) and one hub-state section.

| # | Screen | Until designed | Data ready |
|---|---|---|---|
| F2 | **Household**: houses, servants and wages, larder, companions with their orders | the companions' and servants' conversations (orders, wage, news, dismiss) | the hub state's `household` section (`PlaceSummary`, sent at login, on every change, once a minute): `companions[] {id, name, job, home, wage, wageText, unpaidDays, orders, fight, loyalty, embodied, gear[] {slot, item}}`, `companionsMax`, `houses[] {town, building, larder, larderSize, larderShort, servants[] {name, wage, wageText, unpaidDays}}` |
| F3 | **Companion gear**: worn armour, held items, a small pack | "Let me see your gear" (a list), "Take this: <held item>" (armour to its slot, a shield to the off hand, a weapon or tool to the main hand; the old piece comes back), "Give that back" (all of it) | `companions[].gear[]`; the folk renderer now draws armour (`HumanoidArmorLayer`) |
| F5 | Retinue ranks per lord and their rights; burgher status per town | "What does my service give me?" at the lord or steward, "What are a burgher's rights?" at the reeve | `retinue[] {seat, rank, rankName, commissions, feat, next}`, `burgherOf[] {town, since}` in the same section |

The `household` key is an addition: hosts that do not know it ignore it (HearthStateSections, lane 0).

## Shared files this lane touched (its §11.3 touch points, and three small ones outside them)

| File | Change |
|---|---|
| `npc/service/Escorts` | a pin registry (`pin/unpin/pinHolder/pinnedTo`); `pinned()` answers for pins too, so every fold check of the embodiment manager skips companions; the escort behaviour now wants only active escorts |
| `npc/service/NpcServiceImpl` | `pin/unpin/pinned` implemented (lane 0's api defaults) |
| `npc/embody/EmbodimentManager` | a town's own pass no longer makes an **away** resident flesh at their routine's place (a companion waiting folded, a traveller): only whoever sent them away embodies them. A1's parties and A4's remote workers are unaffected (embodied folk pass through; remote workers are not away) |
| `client/npc/FolkRenderer` | the gear layer: `HumanoidArmorLayer` on the player armour model, so armour on any folk shows (a companion's, a bandit's helmet from A1) |
| `client/npc/NpcClient` | one line: `HouseholdClient.init()` (the household section and its payload) |
| `standing/logic/Titles` | `Title.HOUSEHOLDER`; `Titles.setSworn` / `Titles.sworn(settlementId, player)`; the five-argument `Titles.of(standing, owner, sworn, outlaw, cfg)`. Without the burgher module every owner is a Burgher, as before |
| `api/StandingService.Title` | **added** `HOUSEHOLDER` (last, so ordinals and names of the others are unchanged); `BURGHER`'s doc now says sworn |
| `standing/StandingEngine` (outside the list) | the four `Titles.of` calls pass `Titles.sworn(s.id(), player)`; nothing else. Needed because the engine is where titles are computed |
| `quest/service/Standings` (outside the list) | the exhaustive `rank` switch counts `HOUSEHOLDER` as `BURGHER` does (a Friend) — a compile fix for the new title |
| `data/hearth/hearth_standing/gossip/default.json` (outside the list) | `title_householder` lines (the standing data test asks a verdict for every title) |
| `economy/EconomyServiceImpl` | `chargeDeposit` (all or nothing from `DepositStore`; the server is held from its lifecycle events) |
| `economy/works/Projects` | `councilCandidates(settlement, n)` (the planner's next public works nobody funded, rising ones first) and `councilChoice(settlement, plot, day)` (a project entry with no coin: funded first, as players' gifts are) |

## Asked of other lanes and owners (worked around meanwhile)

- **A3 crowds** (`npc/crowd/Reaction`, `Stimuli`): *guards do not remark on a sworn burgher's arms* — when the player's
  title in the town is `BURGHER`, skip the guard's `REMARK` for `BLADE` and `ARMOUR` (`Burghers.sworn(settlement, player)`
  answers too). `Stimuli` counts `BURGHER` as a friend; `HOUSEHOLDER` (an owner not yet sworn) is now not counted as one —
  add it to the friend list if the crowds should still greet owners.
- **A2 rhythm / B1 events**: *a pew at mass and a seat at the high table at feasts* for sworn burghers
  (`Burghers.sworn(settlement, player)`); the service's seating and the feast's tables are theirs.
- **B1 town events**: the oath (`hearth:burgher_oath`, principals: the reeve; args `player`) and the dubbing
  (`hearth:dubbing`, principals: the lord; args `player`) are staged through `SceneService.stage` when it is registered;
  without it they are a beat of the conversation (the oath text per culture in chat, the dubbing announced to everyone
  within 48 blocks). Please add both scenes to `hearth_social/events/` (venue: the town hall, the throne; crowd: the
  town's burghers / the court). The binder reads `SettlementCalendar.events` for `tax`, `raid` and `tournament` ids (any
  id containing the word): tax takings, a raid to repel and a tournament to ride in come from the event book when it is
  there. A tournament won or a raid repelled through B1's own contests could set the knighthood's feat directly:
  `Retinue.Service.withDone(done, true)`.
- **B2 lives**: settle-the-feud commissions bind to `LivesService.feuds` (seat first, then the domain) and send the player
  to both heads; skipped while no lives service is registered. Named characters as hands for hire "through their
  threads" are B2's to offer (a thread beat can call `CompanionService.hire`).
- **B3 wild places / A1 tolls**: toll-free passage for retainers and up: `Commissions.tollFree(player, settlement)`.
  Toll-gate commissions bind to a post whose `postType` starts with `toll` in the domain, else a built `law/toll_gate`
  plot of a domain town.
- **Social (inns)**: lodging at the keep for retainers: `Commissions.lodging(player, seat)` (a free bed at the seat's inn
  or keep would read it).
- **Standing (appearance)**: the lord's colours as local dress for sworn men and knights: `Commissions.colours(player,
  settlement)`.
- **Hub (VOID Hub)**: the `household` section above, for the F2/F3/F5 pages once designed.

## Left for later

- Companions sleep beside the player as a seated pose (no bedroll); they eat from no pack (no hunger model for folk).
- Servants do not yet stable the player's horse or tend a garden plot (the rounds cover hearth, table, larder, doors).
- The household's "what folk say about you" is the player's title in the town; personal opinions are not summarised.

## The tour (`-Dhearth.tour=place` or `HEARTH_LW_TOUR=place`)

`gametest/place/PlaceTour` (its entrypoint appended to the gametest `fabric.mod.json` list: merge by hand if another lane
adds one too). On seed 20261003 it visits Ulfdal (a historic northern city), advances a day so the offices are filled,
then: hires a hand and gives them an iron sword and chestplate, sets a husk on them (`hearth_place_companion`), measures
a companion following and at the side; gives the player a longhouse and a servant and waits for the greeting at the door
(`hearth_place_servant`); swears the oath before the reeve (`hearth_place_oath`); makes the player a sworn man with six
commissions and a feat, rises to household knight before the lord (`hearth_place_dubbing`) and asks for a commission.
Fixtures (coin, standing, the pledge, the service, the oath on any day) are logged as such.

Found by it:
- In a just-adopted historic town the offices (reeve, lord) are empty until its next day; the tour advances one.
- A full city has no free house for a new knight: the steward "owes one" (`hearth.retinue.no_house`), given at the next
  rising talk once a house stands free.
- Once (1 run of 7) the Fabric client-gametest framework deadlocked closing the world (the render thread in
  `IntegratedServer.halt`, the server parked in the gametest phaser); the tour had finished. Nothing of Hearth's in the
  stacks.
- The camera for the oath shot landed in a dark spot in the hall's lee in every run; the oath itself is checked by the
  log (`title now burgher, sworn true`).
