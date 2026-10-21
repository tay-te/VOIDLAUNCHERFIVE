# Lane A3 (crowds that react): what it needs from others

From lane A3 (LIVING_WORLD_2.md §5), 2026-10-20. Built: `npc/crowd/*` (stimuli, selector, director, reactions, barks,
shutters, brawls, `CrowdService`), `barks/crowd.json`, `lw_crowd.json`, the `repair/DamageRules` and `law/justice/Offences`
touch points, the `crowds` client tour. No new screens: everything the player sees is folk moving, posing and barking.

## Requests

| To | What | Why | Until then |
|---|---|---|---|
| law (`law/crime/CrimeHooks.afterDamage`) | Before filing `ASSAULT` for a hit on a folk, skip it when `Offences.brawling(attacker.getUUID(), v.resident)` is true (one line). | §5.1: a player who joins a brawl with bare fists is brawling (fined on the spot by the guard who parts it, `crowds.json` `brawls.fine_bits` 5), not assaulting. | The guard still fines them, but the law also files an assault. |
| standing (`StandingService`) | A default `List<String> lookNotes(ServerPlayer)` returning `Look.notes()` (filthy, foreign_dress, bathed...). | §5.1's remarks on filth and foreign dress need the standing module's look; the npc module only sees the api. | Bloodied (health and recent combat), armour, weapons and titles are read directly; filth and dress lines are not said. |
| A2 rhythm | Shutters close at 20:30 and open at 06:30 by the clock (`Shutters.CLOSE_AT/OPEN_AT`). If A2's curfew and dawn bells move, call `Crowds.shutters()` from the bell or tell A3 the hours. Night watch challenges (§2) are A2's; A3's `PROWL` stimulus only barks. | §5.1 "at the curfew bell". | Fixed hours. |
| B1 events | `CrowdService.spectacle(s, at, kind, seconds, max)` draws a ring; barks come from `crowd_spectacle_<kind>` (add your own lines in a `barks/events.json`), falling back to `crowd_spectacle`. `CrowdService.brawl` starts a fist fight; `shuttered(s)` is true at night, in storms, during an alarm or while an outlaw is about. | Trials, pillory, contests. | — |
| every lane with barks | `npc/crowd/Barks.say(folk, category, role, player, title)` is the shared bark engine (§0.2): files under `data/<ns>/hearth_dialogue/barks/*.json`, the cooldowns of `crowds.json` `barks`. Use it rather than a second limiter. | One per-player-per-town limit across all barks. | — |

## Notes

- `CrowdService.brawling(player, resident)` is a new default method (additive; not a type Mettle copies).
- `DamageRules.onlyOpenDiffers` documents what was already true: repairs compare by block kind, so a closed shutter is
  never damage or "missing" (tested in `ShutterScanTest`).
