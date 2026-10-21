# Lane B1 (town events): screens, requests to others, what it touched outside its package

From lane B1 of the living world, part 2 (LIVING_WORLD_2.md §3), 2026-10-21, branch `hearth-lwB1`.

## Screens (Figma first, HUB_UI.md)

| # | Screen | Until it is designed |
|---|---|---|
| F6 | Contest line: the archery table (folk archers' scores, the player's arrows and score), the melee's rounds | The table goes to the **action bar** of players at the range every 10 s (`FairScene.table`), hits and misses to the action bar as they land, melee rounds as chat lines. Restyled like the quest HUD once picked. |
| F7 | Event line under the arrival banner ("A wedding at the chapel") | A grey italic **chat line** when a player walks into a town with an event on today (`Arrivals`, once a town a day): what, where, when. The banner itself (`client/sim/ArrivalBanner`) is untouched: it wants a second line and a payload field, which is the banner owner's change once F7 is designed. |

No other screen: everything else is conversation options (order band 80–99, §0.4) with their odds and costs described
for the Hub's portrait card (`DialogueProvider.describe`), notices on the board, barks and the world.

## Requests

| To | What | Why | Until then |
|---|---|---|---|
| **quest module** (`quest/model/SettlementQuests.Happening.KINDS`) | Add `raided`, `taxed`, `trial` to the happening kinds templates may name, and a template `hearth_quest/event/raiders_camp.json` (like `road/robbers_camp.json`, `place: event`: the happening's position is the band's camp, which B1 passes). | §3.1: "track the raiders to their camp after (existing robbers' camp quests)"; §3.2 names the happenings. | B1 records the happenings (`LivingQuests.happen`) with the camp as their place; no template answers them (the validator would refuse a template naming an unknown kind). `fire` is answered by the existing templates. |
| **build module** (`build/age/Decay`) | The `burnt` flavour of the decay pass (§3.2, "an xhigh architect"): a plot param `decay_kind: burnt` drawing roofs and upper timber gone, beams charred, so a burnt plot is a spec the applier writes and the planner's rebuild restores. | Abstract fires should weather the plot itself, like abandoned houses. | `social/events/BurntShell` lays the shell over the plot's own blueprint blocks when the town is next embodied (top of the burnable blocks gone, a third below charred), every block reported to the repair module as fire damage, so the builders mend it; abstractly the plot counts as rebuilt after 3–9 days. |
| **sim / B4** (`sim/settlement/ResidentClaims`) | Nothing needed; noted: a burnt house's family is said (on the board) to be lodged with kin, but their `home` is not moved. A `ResidentClaims` source pointing them at a kin's house while the plot is in `EventBook.burnt` would make it true. | §3.1 "the family lodged with kin". | The notice says so; they keep their home plot (the shell is repaired within days). |
| **A1** (`sim/roads/Traffic`, `TravelService.waylay`) | Carry the player who waylaid a journey in `SettlementEvents.TRAVELLER` (or a `waylaidBy(id)` lookup), at least for `TAX_COLLECTOR`. | §3.1: robbing the bailiff is a crime, "but the poor love you: a split deed"; escorting the takings back is the lord's line (B4). | The takings are lost when the bailiff's journey is waylaid or lost (the seat's board says so); no deeds. |
| **A1** (`npc/travel/Wayfarers`) | Make `stranger`, `arm` and `fight` public (or an api for "a controller folk of this job here"). | B1's raiders and the tournament's champion are strangers like the roads'. | `social/events/Strangers` repeats the few lines. |
| **law** (`law/crime/CrimeHooks`) | Nothing for B1's own folk (raiders and the champion are strangers: no crime to strike them). Noted for the law owner: a player's own trial on demand ("demand your own trial instead of a guard's sentence", §3.1) would be a `Confrontations` option that sets the warrant aside until the next court day and offers a `TRIAL` with the player as the accused. | §3.1, the trial row. | Not built: the player's trials stay the guards' sentences. |
| **social module owner** (`social/life/TownLife`) | (Done, one additive line, see below.) | | |

## Touched outside `social/events` (all additive)

| Where | What |
|---|---|
| `law/justice/Trials` (lane-owned, new) | petty crimes among residents, fines, evidence, judges' mercy, testimony odds (pure) |
| `repair/fire/FireLeash` (lane-owned, new) | the leash: palette checks, a pass every 10 ticks, `light` for a roof fire |
| `social/life/Dressings` | `EVENTS` prefix (kept to its own `until`, like the rhythm's) and `setUp` / `takeDownKey` for one dressing of a town event |
| `social/life/FamilyLife` | the notice keys of the events' deaths (`raid`, `hanged`) |
| `social/life/TownLife` | one read in `residentRemoved`: a resident's `extra("social").death` (set by the events before removing them) names the cause, so a raid's dead and the hanged get the right notice and a funeral (not "left") |
| `social/calendar/TownCalendar` | fair days (two a festival year) and the tournament day, clear of the feasts |
| `HearthServices.calendar()` | wrapped (`EventsCalendar`): `events(s, day)` adds the event book's keys to the rhythm's agenda keys; everything else passes through |
| `hearth_standing/deeds/events.json` | the events' deed kinds |
| gametest `fabric.mod.json` | the `EventsTour` entrypoint (merge by hand if another lane adds one) |

## Notes

- **Cost**: see LIVING_WORLD_2.md §3 (built note) and SOCIAL.md §12 for the measured figures.
- **§13 defaults**: executions staged up to the steps (`events.executions` "staged"; "never" sends the condemned to the
  cell instead); live fires `auto` (single player on, servers off); raids may kill (`events.raid_deaths`, 8 % of a
  sacked raid).
- A forced event (`/hearth events force`, tours) never kills: a forced execution ends in mercy, and nobody is banished.
