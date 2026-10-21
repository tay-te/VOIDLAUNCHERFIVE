# Lane B2 (people with lives): what it added outside its packages, and what it asks of others

From lane B2 (docs/LIVING_WORLD_2.md §4, §11.3), 2026-10-21.

## Screens (Figma first)

| # | Screen | Until it is designed |
|---|---|---|
| **F4** | **People**, a new section of the journal: the named characters the player has met, a line on where each one's story stands, how they stand with the player, whether the player moved the story last | The state is there: `HearthState` section **`people`** (`client/social/lives/PeopleSection`), `{ "rows": [ { name, role, town, story, ended, opinion, yours } ] }`, sent by the server (`LivesPayloads.People`) when the player first meets a character, makes a choice, or asks. In the game the same list is told in conversation: any grown resident has **"Who are the folk of note here?"** |

No other screen: the threads' choices, peace-making and condolences are conversation options (order band 50–79, §0.4),
with odds chips through `DialogueProvider.describe` (`persuade`, `intimidate`, `quest`, `service`, `talk`).

## Additions outside `sim/lives` and `social/lives`

| Where | What | Why |
|---|---|---|
| `social/life/Matchmaker` (B2's touch point) | `Matchmaker.sweethearts(Function<Resident, UUID>)`: when two singles are each other's sweethearts, `find` weds them first (a seeded pick among the courting pairs); without courting pairs it is unchanged (same draws) | §4.1 "the existing weddings now prefer sweethearts" (`MatchmakerTest`) |
| `plan/growth/Needs` (B2's touch point) | a master sharing a workshop with another master of the trade counts one more of the trade *unplaced* (at most one a trade at a time), read from the documented `extra("lives").mt` | §4.1 "a master in a town short of that trade's workshops gets one" through the planner's existing workplace need |
| `npc/talk/PlayerTalk` (new, B2's touch point) | about-the-player lines: `about.player.<deed>`, `remember.last.<deed>`, the bark categories `lives.overheard.<deed>`; deeds folded into slugs (`fire`, `theft`, `quest`, `defender`...) | §4.3 talk. Reads the standing module's memories from the documented `extra("standing").mem` and its town news by encoding the `hearth:standing/town` data through its codec (no import of the standing module) |
| `npc/talk/Greetings` | the greeting prefers a remembered deed ("You're the one who put out the fire") over the town's news, and "Back again? Last time you..." for folk met before | §4.3 |
| `npc/talk/DialogueTemplates` | `BUILTIN` + `lw_lives_talk`, `lw_lives_threads` | tests and the dev sandbox read the lines without a data pack |
| `client/social/SocialClient.init` | one line: `PeopleSection.init()` | lane 0 left no client entry for B2; this is the social module's client init, the line is marked |
| `gametest/LivesTour`, `gametest/resources/fabric.mod.json` | the tour (`-Dhearth.tour=lives` or `HEARTH_LW_TOUR=lives`) | §4.8 |

## Requests

1. **A1 (npc/look) — the mourning dress.** §4.3 has "in black — station dress darkened by the composer's existing finish
   rules for a mourning flag"; `SkinComposer` has no finish rules. The lives keep the flag: `Life.mourning(resident,
   day)` (`extra("lives").gu` ≥ today). A darkened outer layer (black, white in the karst and wisteria cultures, as
   `build/age/Dressings` mourning) while it holds would finish it. Until then a mourner shows grief only by going to the
   grave on the rest day.
2. **B1 (quests) — `QuestService.offerPersonal`.** Four threads offer personal quests (`hearth:lives/vow_candles`,
   `masterpiece_iron`, `old_blade`, `herbs`; templates under `hearth_quest/lives/`, weight 0 so the board never draws
   them). The conversation calls `offerPersonal(player, s, giver, template, vars{thread, giver})`; while it returns
   false (today) each choice takes its `fallback` (a coin cost, the story moves on at once). When the quest completes
   (`QuestEvents.COMPLETED` with that template and the settlement) the thread moves on by itself.
3. **Standing — town news in the api.** `PlayerTalk.townTalk` reads the town's news through the `hearth:standing/town`
   codec (cached 30 s a town). A default `StandingService.news(long settlementId, UUID player)` returning
   `(kind, weight, day, local)` would let it drop the encode.
4. **Lane 0 / npc — `ScheduleOverrides.overlay` cost** (as A2's request 1): the named characters add windows to a few
   residents a town (≤ 6, plus sweethearts on the rest day and mourners), so B2 adds little to it.
5. **B1 (events) — trials of feuds, funerals of named characters.** `LivesService.feuds(s)` lists the feuds by house
   heads (cause, since); `LivesService.notables(s)` the characters; `LIFE_EVENT` fires `apprenticed`, `journeyman`,
   `master`, `sweethearts`, `jilted`, `feud`, `peace`, `widowed`, `notable`, `thread` (with the player who moved it).
6. **B4 (the player's place) — feuds to settle.** Peace can be made through `LivesView.makePeace(s, feud, why)`; a
   commission that settles one should call it with `why = "commission"`.

## Cost

See LIVING_WORLD_2.md §4.4 (measured) and the `lives`, `lives claims`, `lives folk`, `lives talk` lines of
`/hearth sim stats`, and `/hearth lives stats`.
