# Requests from the w7 sites lane (T11: construction sites and chimney smoke)

## What changed in the blueprints (for everyone who reads steps)

`build/blueprint/SiteWorks` adds a building site's **works** to every ordinary building's blueprint (walls and a roof,
not built in campaigns): a hurdle fence on the plot edge and a pile of materials by the door (stage FOUNDATION, taken
down in EXTERIOR), scaffold poles with putlogs and board lifts round the walls (FRAME, or WALLS; taken down in
OPENINGS), a flying scaffold over the street at `z -1` from y 3 up in front of town homes, and a board deck over the
wall heads (FLOORS or WALLS; lifted in ROOF where the roof does not replace it).

- A position may now carry **two steps in one blueprint**: the work's block in an early stage and an air step in a
  later one. Every reader in the tree already takes the last step at a position (BlueprintChecks, Measure, BuiltView,
  PlotLocator, CrimeHooks, TownScene, PreviewScene); `StepWriter`'s owned map already accepts the earlier state.
  Anything new that indexes steps by position should do the same.
- The works cost **work, no goods** (`MaterialBill` skips them). Stage work grows by the works' steps.
- The finished blueprint (all stages) is exactly the drawing plus the crowned chimney heads.

## To the npc lane (hearth-w7-npc), embodied builders

1. The works are ordinary steps: an embodied builder puts the poles, boards, fence and pile up and takes them down
   (air steps) like any other. Removal steps come first in their stage, top-down. Nothing in `npc/**` needs to change
   for them to work; please do not treat them as your `ScaffoldLedger` scaffolds (they are blueprint steps, not
   ledger entries, and the applier will rewrite them if they are removed early).
2. If your vanilla `SCAFFOLDING` towers stand where a work goes (a cell one block out from a wall), the step will meet a
   block it does not own and count a conflict. Suggest placing your tower on a cell that is not a works cell, or taking
   it down before advancing past FRAME/WALLS. The works cells are the outside cells next to a wall, at the corners and
   every third block along a face (poles), and at y = 3, 6, 9... on the other face cells (boards).
3. Optional, nice: the board lifts are walkable at y 3, 6, 9...; a builder could stand on them for wall work instead of
   towering up, when a lift is within reach of the step.

## To the sim lane

- `Projects`/stage durations: stages FOUNDATION, FRAME/WALLS and FLOORS now carry a few dozen to a few hundred more
  steps of work (the works); OPENINGS, ROOF and EXTERIOR start with their removal steps. No goods change.
