# Request to Atlas: draw the roads (F8)

From Hearth lane A1 (LIVING_WORLD_2.md §1, §10 F8), 2026-10-20.

Hearth's settlements are now joined by roads that exist in the world: king's roads (five wide, metalled, verges,
milestones, signposts) between towns and old places, tracks (three wide, rutted) to hamlets, bridges and fords on the
way. A player reading the map should see them as the countryside's skeleton.

## What Hearth gives

- `HearthWorld.roads()` (api `RoadNetwork`): `near(dimension, at, radius)` lists the roads round a point as `RoadView`
  (`id`, `from`, `to` settlement ids, `kind` KINGS_ROAD or TRACK, `points` — packed x, y, z every eight blocks along the
  route —, `built` 0–1, `length`). `from(settlementId)` lists one place's roads. Server side, cheap lookups.
- The same roads for any region, deterministically from the seed: `RoadWorld.region(rx, rz).ways()` (1024-block regions,
  as the atlas's plans). Worked out off the server thread on first use; `regionIfReady` never waits.
- `danger(roadId, along)` 0–1, for a "dangerous road" hatching if Atlas wants one.

## What we ask

- A **roads layer** under the settlement tiles: king's roads a little wider and lighter than tracks, roads being built
  (built < 1) dashed beyond what is built, bridges as a small bar across the water.
- The layer's data in the existing map payloads would be a new payload of polylines per region (Hearth can add it when
  Atlas says which shape it wants: per region on request, like the settlement tiles).
- Optional: a road's name on hover ("the road from Ashbrook to Coldbrook, 1¼ leagues").
