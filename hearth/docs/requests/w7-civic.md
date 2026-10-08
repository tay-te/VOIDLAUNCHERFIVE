# Requests from the civic and law lane (wave 7, L4a)

What the civic and law buildings need from files this lane does not own. The structure audit's hard columns are 0 in
every `civic/*` and `law/*` case without them; what is left shows only in the in-game walkthrough tour.

## For the NPC lane (folk navigation) or whoever owns `kit/civic/Storeys.flight`

1. **Folk cannot climb the town hall's stair to the chamber.** `-Dhearth.tour=walk -Dhearth.walk.parts=a
   -Dhearth.walk.only=town_hall` (temperate, mediterranean, northern, desert): the player model walks every room and
   the real client player completes every path (14-17/14-17, 0 stuck), but the folk pathfinder (`Folk` with
   `FolkNavigation`/`ClimbNodeEvaluator`, `createPath(pos, 1)` from the street) stops two or three steps up the flight to
   the council chamber and never reaches the chamber's chest (STORAGE at storey 1) nor the charter chest in the tower
   room (reached from the chamber through an arch, then by the ringer's ladder). The flight is `Storeys.flight` (a
   straight one-wide `Stairs.straight` against the back wall, the upper floor cut over its last three steps, a fence
   along the well's open side). The library's `Stairs.straight` to its gallery and every chapel stair are walked by the
   same folk check (PASS x4), so it is something about this flight or the cut above it: the walk's folk end nodes were
   `1234,-47,202` (temperate, step 3 of 5), `1235,-48,360` (mediterranean), `1234,-48,525` (northern), `1239,-43,690`
   (desert). Asked: find why the node evaluator stops there (a fence rail at the top read as a 1.5 obstacle beside the
   step? the stair placed in the upper floor's row at `fy`?) and fix it in `Storeys.flight` or the evaluator.

2. **Town hall: full-block steps at the loggia corners (mediterranean, northern).** The walk reports 6-7 "full-block
   steps on the way to rooms" at the plot's side edges (`x = 0` and `x = w - 1`, rows 1 and 7) where the walk model
   comes in from the ground beside the plot onto the raised floor of the corner bays. `Steps.cut` (kit/civic) cuts such
   edges into a stair where it can; these are left because something within a cell (a slab course or a stair bracket of
   the arcade, `Grounds.flight` cheeks) makes it stand off. A town hall plot has neighbours there in a real town, so
   this is low; noting it for the walk's owner (should the walk start only from the street?).

## For the audit / gate lane (`gametest/audit`, `gametest/walk`)

3. **A candle wheel is not a room.** `kit/light/Fixtures.wheel` (the north's candle wheel: arms of horizontal chain with
   candles on them) reads in `Measure` as an unreached furnished room of three cells (standing on the chain arms, the
   candles as furniture): it cost the convent and the monastery six hard failures until the church hung its own lamps.
   Suggest: no room cell whose floor is a chain, a fence or a hanging fixture.

4. **The walk model stands on fence tops.** `WalkModel` takes the 1.5-high top of a fence or a wall as a floor, so a run
   of balustrade along a gallery reads as a room nobody reaches (the library's HIGH defect, now worked round with newel
   posts). A rail's top is not a floor a builder means anyone to use; suggest skipping fence, wall and gate tops as
   floors.

## Notes for the coordinator

- `Halls.finish` now walks the finished drawing with the audit's own rules (`kit/light/Spaces`, read-only use) to cut
  steps, pack hollows, furnish bare corners and guard falls (`kit/civic/Steps`, `Hollows`, `Nooks`, `Falls`). Other
  lanes may want the same guards: `Falls.guard(d)` and `Nooks.furnish(d)` work on any draft.
