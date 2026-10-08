# Requests from lane L4b (commerce and open spaces), wave 7

What the commerce and landscape archetypes need from files other lanes own. Each item says what we saw, where, and
what would fix it. Our side already works round each one where it could (noted).

## L1 (access kit)

1. **Lattice windows are open doorways.** `Openings.window` with `WindowStyle.LATTICE` sets an open trapdoor in every
   cell of the opening, and the sill outside is a top stair. A player (and the audit's walker) steps up into the window
   and out onto the sill, an eave or a porch roof: in the karst and blossom-vale inns, banks and market halls this was
   37+ fall-edge cells a case. `Edges.guard` now glazes the lower row of a lattice that leads out onto a ledge, but the
   window itself should not be passable: a pane behind the lattice in the lower row, or a closed trapdoor (transom) at
   the sill.
2. **`Porches.balcony`** is one deep with its rail on the deck cell itself, so it cannot be stood on and reads as a
   ledge; where an upper door opens onto it (inn), the door is blocked. Two deep with the rail on the outer row (your
   item 2) is what the inns and the bank want.
3. **`Hall.terrace` builds its stair only for one storey.** The counting house's deck room on two-storey desert houses
   was furnished and sealed; we now ask for the room only when `storeys() == 1`. When the terrace stair (or a hatch
   ladder) reaches the deck of a taller hall, `CountingHouse` can pass `room = true` again (one line).
4. **`Storeys.flight`** rails only the last two treads' side; the stairwell's other open cells and the floor round it
   stay open (hostel, counting house). `Edges.guard` sets a balustrade in the well where the wall braces it; a rail
   round the whole well from the flight itself would be cleaner.
5. **`Rooms.furnish` puts a rug beside a bed.** The walker (and `Spaces.standable`) stands on carpets one block up, so
   a bed whose free side is a rug counts as having no free side (desert and mesa inns). `Inn` now lifts rugs off a bed's
   sides; the kit should not lay them there (or the walker should treat carpets as floor).
6. **`Hall.switchback`'s two lanes have no wall between them**: standing on the upper lane's treads you can fall onto
   the lower lane (counting house, maritime and bayou slopes, one cell each). A wall or a rail between the lanes.

## L3 (work and storage, `kit/craft/ShopHouse`)

7. **The 7x13 counting house's upper storey is a 45-cell sealed void** (temperate, moorland, maritime): `ShopHouse`
   skips the switchback when the top storey is an attic two high, so the attic is drawn but never reached. Either open
   it to the room below, give it a hatch ladder, or `Voids.seal` it.

## L4a (civic kit)

8. **`Adobe.dome`** (one per building now): the market hall raises one centred dome on its souk roof (r 2 on wide halls),
   the bank its little dome; the look of the dome itself (review 2 item 10: a smooth hemisphere on a round drum) is
   yours.
9. **`Trade.bar` racks its kegs two high** and the top of a two-high rack is a cell the audit counts as an unreached
   room under the tie beams. The tavern now stands jugs and candle stubs on them; the inn's bar may want the same.

## L0 (audit)

10. The audit's walker treats a closed door as something to stand on when the cell above it is open (a gate with no
    lintel): the caravanserai's gate leaves read as unreached doors until we gave them a lintel. It also stands on
    carpets (item 5) and on the top edge of open trapdoors (shutters, hoist doors: caravan yard).

## T9 items not in our files

11. **Dye works without a flag** (review 2 item 21) is `archetype/work/DyeWorks.java` (L3's).
12. **Tilt yard fences and pavilions on slopes** (item 27, the tilt-yard half) is `archetype/civic/TiltYard.java` (L4a's).
    The festival ground half is done here (terraces with parapets, everything set on its terrace, `Edges`).

## Shared

13. `Terraces.build` (ours) now sets a parapet (a wall of the masonry, a rail where it is timber) instead of the coping
    slab on any retaining wall of two or more. `civic/Cemetery` uses it too and gets the same guard.

## Added on resume (same lane)

14. **L0: the walkers stand on the top edge of open trapdoors** (shutter leaves, lattices) and on chains. `Edges` now
    glazes a lattice, carries a corner post up through it or takes a shutter pair off where that top would lead over a
    drop, but the walker should not count either as a floor: hanging lanterns on long chains (the light pass's) make
    three-cell "furnished rooms" under high roofs (market hall, tundra) that we can only avoid by lighting the hall from
    its walls first.
15. **L0: a door under a lattice transom** is read as a step up onto the door (item 10 again): the caravan yard's booth
    door in the karst and the tropics. `CaravanYard` now sets a lintel of the beam over it; the karst and tropical
    door dressing (`Openings`, L1) could do that itself.
16. **L1 / npc: the folk pathfinder and the market hall's guild hall.** In the in-game walk the folk could not reach the
    upstairs store in any culture while the player model and the real player could (all paths completed). The flight is
    now walked onto from its foot and lands clear of the hearth; if the folk still fail, `ClimbNodeEvaluator` on a
    one-wide straight flight under a floor is worth a look.
17. **L1: `Porches.balcony`** (item 2) is no longer used for the inn's yard gallery: `Inn.gallery` builds it two deep with
    the rail on the outer board and only where a door reaches it. The alpine/karst front balcony still uses the kit's.
