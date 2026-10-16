# Systems review: the npc module (folk life, pathing, talk, death)

Wave 7, systems track, lane `hearth-sys-npc`. Scope: `dev.voidmc.hearth.npc` and `client/npc` (docs/FOLK.md). The 3D
buildings are other lanes'; where folk meet building geometry the finding goes to `docs/requests/sys-npc.md`.

How it was checked: the code read end to end along the flows a player meets; unit tests (`./gradlew test --tests
'dev.voidmc.hearth.npc.*'`, 82 tests, all pass); and a new client tour, **`FolkNight`** (`-Dhearth.tour=folknight`),
which grows a real town with the sim (100 days), has the applier write it, and then checks rather than photographs:
who is in bed at night (and, for anyone who is not, where they stand, what they are doing, and whether a path to
their bed exists from there), a fold and re-embody round trip (walk 600 blocks away and back), who is at work in the
morning, what the builders are doing, and a conversation opened, answered and closed on goodbye. The walkthrough
audit (`-Dhearth.tour=walk`, part A) was re-run on the multi-storey and problem archetypes for the folk path check.

## 1. Feature matrix

Status: **works** (seen working this wave, in game or by test), **fixed** (broken or missing, fixed this wave),
**ok?** (implemented and wired, shown in an earlier wave's tour, not re-run now), **gap** (missing or broken, open).

| Feature (FOLK.md §) | Implemented | Wired / reachable | Tested | Status |
|---|---|---|---|---|
| `hearth:folk` entity, synced look, noSave, name on crosshair (§1) | yes | entity type, renderer | FolkPortraits, FolkNight | works |
| Doors, ladders, scaffold drop-through (§1) | yes | FolkNavigation | FolkNight (doors at night) | works |
| Paths to beds, benches, chests: a spot beside them (§1) | **new** | `FolkSpots.approaches` in walks and `FolkHandle.moveTo` | FolkNight, walkthrough | **fixed** |
| Long walks across a town (§1) | path budget 48 | distance budget to 128, detours | FolkNight | **fixed** |
| Stuck folk: shortcut when unseen (§5) | only if no player in 32 | any player's line of sight | FolkNight | **fixed** |
| Portals, suffocation (permanent death by a wall) | no | `canUsePortal`, IN_WALL step-out | code | **fixed** |
| Skins composed from genome, culture, trade, station, office (§2) | yes | client `FolkSkins` | SkinComposerTest, FolkPortraits | works |
| Names, traits, families (§3) | yes | resident factory | ResidentFactoryTest | works |
| Embodiment: spawn/fold by distance, caps, stickiness (§4) | yes | manager tick | EmbodimentSelectorTest, FolkNight census | works |
| No duplicates, nobody lost, flags agree, state both ways (§4) | yes | fold/bind | FolkNight (48/0/48, 0 dup, 0 mismatch) | works |
| Spawn where the routine puts them (bed at night) (§4) | yes | RoutinePlacement | FolkNight (sleepers on return) | works |
| Per-pass cost of records | schedule per record per pass | cached per day | code | **fixed** |
| Strays adopted, escorts pinned, offices (§4) | yes | manager | StationAndOfficeTest | ok? |
| Death: permanent, message, standing/law (§4) | yes | `die` → `onDeath` | code | works |
| Corpses | no | — | — | gap (not designed) |
| Inheritance / replacement | economy `Inheritance`, sim births and arrivals | RESIDENT_REMOVED | — | ok? (other modules) |
| Daily schedules by trade, trait, culture, age (§5) | yes | brain | ScheduleTest, LivingWorldScheduleTest | works |
| Bed at night | yes | Sleep task | FolkNight: 45/48 at 23:06, 47/48 at 0:00 | **fixed** (audit: 29/48) |
| At work in the morning | yes | work behaviours | FolkNight: 23/24 at their work plot | works |
| Danger: flee, alarm, shelter (§5, §9) | yes | evaluate | — | ok? |
| Danger during a conversation | ignored | evaluate while engaged | code | **fixed** |
| Builders, scaffold ring, step contract (§6) | yes | BuilderWork | BuildSiteTest, WorkRulesTest, FolkNight morning | works |
| Gatherers, haulers, officials, crafts (§6.1, §6.2) | yes | work behaviours | WorkRulesTest | ok? |
| Labour credit on fold and death (§6.2) | yes | creditLabour | — | ok? |
| Conversation open, greet, options, replies (§7) | yes | payloads, screen | FolkNight | works |
| Goodbye closes the screen | no (screen stayed open) | `hearth:npc/close` | FolkNight: closed true | **fixed** |
| Talk range server 8 / client 10 | mismatch | both 10 | code | **fixed** |
| Folk keeps listening while you choose | 60 s from opening | +60 s per option | code | **fixed** |
| Long option lists (training, quests, trade) | off screen | scroll | code | **fixed** |
| More than 64 options | client disconnect | capped both ends | TalkPayloadsTest | **fixed** |
| Mettle option state: locked, hint, tooltip, reply, training band (§7) | yes | screen, `choose` refuses locked | FolkPortraits (earlier) | works |
| Dialogue templates, news, rumours, landmark rumours (§7) | yes | reloaders | DialogueTemplatesTest | works |
| Lang strings | one hard-coded " of " | translatable | NpcLangTest | **fixed** |
| Quest markers per player (§8) | yes | markers payload | — | ok? |
| Behaviours other modules teach (§13) | yes | BehaviourRunner | BehaviourRunnerTest | works |
| Behaviours' walks (`FolkHandle.moveTo`) | budget 48, no unstick | distance budget, unseen unstick | FolkNight (crier) | **fixed** |
| Escorts (§13) | yes | Escorts behaviour | — | ok? |
| Caravans on the road (§15) | yes | Caravans | — | ok? |
| Dev commands, permission level 2 (§11) | yes | `/hearth npc` | — | works |
| Dedicated server: no client classes in main | yes | — | full build | works |

Counts: 38 rows; **16 fixed**, 14 working as seen this wave, 8 implemented but not re-run (ok?), 1 gap (corpses, not
designed).

## 2. What was fixed

1. **Folk not in bed at midnight** (audit: 29 of 48 abed, 14 "going to bed", five of five homes stuck on one terrace).
   Three causes, all npc-side:
   - *Paths asked for the bed block itself.* A bed, a chest, an anvil is no place to stand, so the search never ended
     early: it spent its whole node budget (768) and returned the node nearest the bed, which for an upstairs bed is the
     floor directly beneath it: exactly the audit's "gets as far as" points, one storey below each bed. Now paths are
     asked for the cells beside the target (`FolkSpots.approaches`: beside either half of a bed; under a roof only
     roofed cells, so nobody beds down through an outside wall from the street).
   - *Long walks searched only 48 blocks.* Crossing a city to bed, the partial path ended where the search ran out (often
     on a terrace or landing nearer the goal as the crow flies) and the next search ended there again: the shared stuck
     point. The budget now follows the distance (×1.4 + 16, +48 for another storey, max 128), and a folk at the end of a
     partial path no nearer than last time goes round by open ground first.
   - *The shortcut for a stuck folk needed every player 32 blocks away.* Now it needs only that nobody sees the folk or
     where it is going; a sleeper nobody sees slips into bed.
   Result (`FolkNight`, Sigskog, 193 souls, 48 embodied): **45/48 asleep in bed at 23:06, 47/48 at midnight**; the one
   left was held by the social module's crier (request §1). Morning: 23 of 24 at their work plot.
2. **Goodbye did not close the conversation.** The server ended the session but the parchment stayed open with dead
   options. A clientbound `hearth:npc/close` now closes it (goodbye; a choice for a conversation the server has ended).
3. **A conversation with more than 64 options disconnected the player** (the client read 64, the server wrote all).
4. **Long option lists ran off the screen** (Goodbye last and unreachable with trade, quests and training); they scroll.
5. **A folk in conversation ignored danger**: the brain skipped its once-a-second look while engaged; a zombie hit a
   folk standing listening. Danger now ends the talk.
6. **Talk range**: server 8, screen 10 (between them clicks did nothing); now both 10, and each choice keeps the folk
   listening another minute (it walked off after 60 s from opening).
7. **Suffocation killed residents for good** when a block went up where they stood (builders check only for players; the
   applier checks nothing). They now step out to standing room.
8. **Folk could use portals** (the entity left for a world where no settlement takes it back).
9. **Other modules' behaviours walked only 48 blocks and never got unstuck** (`FolkHandle.moveTo`).
10. **Every embodiment pass made a full schedule for every record in town**; kept per resident per day now.
11. The conversation title's hard-coded English " of " is a lang key; `NpcLangTest` keeps every npc key defined.

## 3. Verified in game

`FolkNight` (northern city Sigskog, seed 20261003, 100 days grown, 193 souls, 486 plots built):

```
night 1: time 17100: 48 embodied, 45 asleep in bed, 0 resting where they are, 3 still up
    (two innkeepers behind the bar, as their hours have it; the reeve held by the crier)
night 2: time 18001: 48 embodied, 47 asleep in bed, 0 resting where they are, 1 still up   (the reeve)
before leaving: 193 souls, 48 folk entities in the level, 48 managed, 48 flagged embodied, 0 flag mismatches, 0 duplicates
away:           0 folk entities, 0 managed, 0 flagged
back:           48 folk entities, 48 managed, 48 flagged embodied, 0 flag mismatches, 0 duplicates, 0 with no resident
morning: time 2800: 24 with a work plot, 23 at it, 1 still asleep
conversation: opened true, closed on goodbye true
```

Screenshots (`scratchpad/w7/sys-npc/`): `folknight_sleepers.png` (two folk asleep in their beds at midnight),
`folknight_conversation.png` (the parchment with the translatable title, "Child of Sigskog").

## 4. Folk paths in the buildings (walkthrough part A, re-run)

`-Dhearth.tour=walk -Dhearth.walk.parts=a -Dhearth.walk.only=merchant_house,tenement,manor,townhouse,farmhouse,inn,
bakery,windmill,keep,mine,market_hall` (four cultures, 44 buildings), with the audit's folk check asking for a path as
folk now do (a spot beside the target, the wider search for another storey):

| | street to every bed, job site and store |
|---|---|
| audit (before; same archetypes) | 323 of 418 (77 %), 59 HIGH "folk cannot reach" |
| this wave (npc fixes + the access lane's stair head room) | **391 of 399 (98 %)**, no bed or job site unreached |

The eight left are mine work faces (temperate, mediterranean, northern, desert: two each), where the search ends on
the cell beside the face (a face is rock until it is cut; the digger works it from there). The buildings' remaining
HIGH defects in that run are the model's and the player's, not the folk's: the keep's wall-walk turrets (guard posts),
the northern and desert bakery's raised oven nook, the desert townhouse's bed alcove (folk step up a whole block, the
player model does not), all already in the audit and with the building lanes.

## 5. Still missing or broken, ranked

1. **The crier keeps a resident up all night** (social module, `docs/requests/sys-npc.md` §1): its walk deadline is
   pushed back with every repath, and it does not stop at night. The npc side now gets a stuck crier to the board
   unseen, but the deadline and the hour are the crier's.
2. **Corpses**: none; a dead folk vanishes with the death message. Not in the design (FOLK.md, LIVING_WORLD.md); KCD-style
   bodies to search would be a feature for the law and npc lanes together.
3. **Homeless residents** (the sim had not housed a lord, a merchant and a builder in a city of 225): they now doze at the
   inn; the housing itself is the sim's/plan's.
4. **Arrival cost in a big city** (w7-tour §7: 450-500 ms worst ticks): the per-pass schedule rebuild is gone; the rest of
   the arrival (each record's places resolved once, the bed and job-site ranks counted over every resident) was not
   measured here and stays on the list.
5. **Not re-run this wave** (implemented, shown in earlier tours, no new defects found in the code read): escorts,
   caravans, quest markers, gatherers and haulers in game, labour credit, flee and alarm.
6. Mine work faces: the folk search ends beside the face (above); harmless while the digger works from there.
