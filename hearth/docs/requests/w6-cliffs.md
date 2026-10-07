# Requests from the wave 6 cliffs lane (ART_REVIEW item 29)

## Plan lane (plan/growth)

Two small hooks were unavoidable outside the cliff files; please keep them when you merge or reshape these files:

- `Growth.java`: the `stack` need also runs in cliff towns (`ctx.state.layout == LayoutType.CLIFF`), not only with
  the stacks signature. In a town with a face quarter `Stacks.plan` only raises the lookout; elsewhere it stacks as before.
- `Housing.java`: in a cliff town, commoners' homes go to the face first (`Stacks.face`, 80%). The random draw is
  taken only in cliff towns, so other layouts plan exactly as before.

## Extraction lane

- `settlementPreview -Pculture=karst/cliff -Pterrain=cliff -Pseed=1 -Pdays=300` logs an
  `IndexOutOfBoundsException` from `kit/extraction/Benches.stubs` (line 602) called by `QuarryPit.draw` (line 93):
  `stubs` reads index 0 of an empty list. The preview survives it, but that quarry pit draws nothing.

## Whoever owns them (seen in the cliff close-ups, not from the cliff code)

- karst cliff seed 1, day 300 (`infraCloseups -Pfocus=stairs`, the view from the plain side): a dark slab bar with
  lanterns hangs in the air high over the crest, like a walkway with nothing under either end.
- mesa cliff seed 1, day 300, same close-up: a lone sandstone pillar about twelve blocks high stands on the crest
  behind the lookout.
