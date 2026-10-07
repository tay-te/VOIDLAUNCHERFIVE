# Requests from w6-interiors (ART_REVIEW items 23 and 30)

Edits made outside this lane (small, needed for item 23; owners please keep them when merging):

- `build/archetype/commerce/Inn.java`: the tap room's hand-placed tables and chairs gave way to
  `Rooms.furnish(..., Rooms.Type.DINING, ...)`, which fills the room to the walls round the bar, hearth and stair.
- `build/archetype/house/HouseBuilder.java`: `byLadder` (a ground floor of 28 cells or less climbs by a ladder in the
  back corner instead of a flight), the outshot's wide doorway with no door in it, and the ladder's approach cell kept.
- `build/archetype/house/FarmParts.java` (`ensureBeds`): one line, `Rooms.read(d, room)` before `Beds.place`, so beds
  added after the furnisher see the furniture (a ladder loft is furnished now, and beds were being walled off).

Note for homes: `Beds.place` on a room built over an already furnished floor should call `Rooms.read` first (the other
callers build their rooms fresh, so they were left alone). One tiny river cottage (temperate seed 15838 east) now
finds no room for its two beds on a 4 x 5 floor; it would want its beds before the kitchen.

Requests:

- **commerce (Inn)**: the carriage passage beside the tap room stays bare planks. A settle or two and a cask against
  its inner wall, where wagons still pass, would finish it.
- **every lane with common rooms** (refectories, guild halls, taverns, mess halls): use `Rooms.Type.DINING` for any
  room folk eat or drink in; `HALL` is for passages and landings only.
- **every architect**: check a building in a town with `./gradlew townPreview` (BUILDING_GUIDE.md §9) and run
  `./gradlew cultureSimilarity -Parchetype=<yours>`; a **FLAG** means one plan in many cultures, wanting plans of its own.
