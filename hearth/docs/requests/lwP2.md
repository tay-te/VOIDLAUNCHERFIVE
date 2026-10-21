# Pass P2 (living world performance and misses): events, lives, companions, wild posts

From pass P2, 2026-10-21, branch `hearth-lwP2`. Budgets from LIVING_WORLD_2.md §9.1. All figures warm unless said.

## Measured, before and after

| What | Before | After | How measured |
|---|---|---|---|
| Events: a town's day (facts, crime roll, plan) | 27 µs ("warm" was 56 samples: the JIT, not the work) | 5.4–11.4 µs in game; 2.7 µs for the whole `planDay` on a 142-soul town in a unit run | `TownEvents.bench` now warms first; tours `events` |
| Lives: a town's day | 46 µs (107 souls), 32 per 150 | 18.1 µs for 279 souls = 9.8 per 150 (same harness, same warm-up: 29.7 before) | `LivesHarnessTest`, best of five batches after 4 000 days |
| Companion at the side | 16 µs a tick (one companion, interpreted) | 0.5–0.6 µs a tick compiled; 12–13 µs in the tour's cold minute | `PlaceTour`: 20 000 ticks in a row after 20 000 |
| Companion following | 30 µs a tick; paths 1.2–2 ms each | paths ~0.2 ms each (to within 2 blocks, half the search); 17.6 µs a tick cold | `PlaceTour` part counters (`CompanionBehaviour.parts()`) |
| Post step (6 000 ticks) | p50 40 µs (400 steps cold) | p50 5–10 µs; the day's business every fourth step ~40 µs (the sim's own clock: `SettlementClock`, `Production`) | `PostGrowthTest`, warmed 3 000 steps |
| Founding a post, server thread | 1.6–8 ms | 0.70 ms avg, 1.15 ms worst (culture vote and name moved to the wild worker; one adoption a tick) | `WildTour`, `/hearth wild stats` |
| Fair's worst pass | 59 ms | 4–5 ms (two stalls a pass) | `events` tour, `worst (ms)` |
| Raid's start | 21 ms (42 ms in one run) | 6.2 ms (one raider a pass; the alarm a pass later; the town's gate only if already known: working out the exits was 36 ms) | `events` tour |
| Scene passes, worst | 59 ms | 7.7 ms | `living world events` |

## Misses fixed

- **Crowds at scenes.** The cast took folk "about" first, but the town's 48 are where its people are, often 60+ blocks
  from the pillory. Now folk count as about only within 40 of the venue, and a scene **borrows** bodies for its open
  parts that nobody near can play (up to its rule's `crowd`, at most 12; a raid borrows 4 guards): two a pass, brought
  out a dozen blocks from the venue on the side away from the nearest player, so they walk in. They are summoned folk
  (kept 90 s), so the embodiment's next pass folds as many of the town's folk farthest from any player. Tour: the
  pillory drew 12–18 folk within 10 (was 1); the trial 8–15; the tax 15; the tournament 9–42.
- **Raiders shown.** Six raiders spawn and are in the world, but they used to stand at the town's edge: a raider's path
  search was the folk's street-length default. They now search as far as the storehouse is (to 96), take part of the way
  when no whole path is found, and come in on open ground (not a roof). Tour: one at the storehouse, the rest on the way.
