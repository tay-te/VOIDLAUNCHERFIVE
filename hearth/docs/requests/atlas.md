# Requests to the Atlas session (2026-10-07)

Hearth now shows its settlements and the tracked quest on Atlas's map through the `"atlas"` entrypoint
(`src/atlas/.../client/map/atlas/HearthAtlas.java`, see ARCHITECTURE.md §11). It works with Atlas's API as it is on
`claude/atlas`; these are the places where it had to work around it. None of them blocks anything.

1. **Settlements are doubled by Atlas's own village sightings.** `StructureScan` finds a "village" at every Hearth
   town (a bell, beds and job sites within 48 blocks), so a Hearth settlement shows twice: Atlas's "?"/Village tile and
   Hearth's settlement tile. Singleplayer exact data does not help (Hearth replaces the village structure sets, so there
   are no structure starts). Proposed: a plugin can claim an area so that sightings inside it are folded into, or left
   out for, the plugin's place, as exact data already does — e.g. `Place` reported/provided with `radius > 0` and a
   new flag, or `AtlasPlaces.claim(String dimension, int x, int z, int radius, String placeId)`. Hearth would call it
   for every settlement it sends (centre and radius are already in the payload).

2. **A plugin place cannot carry a glyph or a detail card**, so Hearth cannot use Atlas's discovery model (`Place`,
   sighted → discovered, kept per world, toasts, the list and search) without losing the settlement tile, the folk
   readout and the standing meter. Hearth keeps its own server-side discovery instead (sighted within 160 blocks,
   discovered within 24, per player, persistent) and draws markers. Proposed: let a `MarkerProvider` marker stand for a
   place — a `placeId` on `Marker` (Atlas then draws the plugin's marker instead of its own for that place, takes the
   state from the place, and sends discovery toasts and journal entries as for its own) — or add an optional
   `glyph`/`detail` to `Place`. Hearth would then report its settlements as places with `DiscoveryState.UNKNOWN` and let
   Atlas decide sighted/discovered on the client.

3. **Tracking from a plugin.** The tracked quest target is a `Marker.Style.WAYPOINT` marker with `MarkerGlyph.TRACK`,
   but Atlas's compass and minimap rim only follow places and waypoints (`AtlasClient.placeFor`). Proposed:
   `AtlasMarkers.track(String markerId)` / `untrack`, or a `tracked` flag on `Marker`, so a mod's objective is followed
   the way a tracked waypoint is (Hearth would set it for the quest the journal tracks).

4. **Layer chip labels are fixed strings** registered at start-up, before the language is loaded; Hearth re-registers
   its layers on joining a world to get them translated. Proposed: accept a translation key (or a `Component`) for
   `MapLayer.label`.

5. **A town glyph.** Hearth uses `HOUSE` for camps and hamlets, `VILLAGE` for villages and `KEEP` for towns and cities;
   a `TOWN` glyph (a cluster of roofs, or a walled town) in the design's set would read better than the keep, which
   Atlas also uses for outposts and fortresses.
