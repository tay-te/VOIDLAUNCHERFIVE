# Requests from the w6 crowns lane (art review items 9, 10, 16, 17)

What changed in the kits, so the requests below make sense:

- `Crowns.bulbSkin` sheets every onion and helm in one material laid crisp: blackened sheet (polished deepslate) in temperate
  and wisteria (copper on 12 % of tier-4 towns), copper or the roof's shingle (half and half) in the alpine culture,
  elsewhere the sheet asked for (copper unmixed) or the roof. `Towers.cap(ONION)` draws a helm on three-wide towers and
  where the height is short, an onion with a lantern on five-wide ones.
- `Shapes.domeRows` is a hemisphere now (about r + 1 rows), `Shapes.onionRows` a waist, a bulb of round octagons held two rows, a shoulder,
  `Shapes.helmRows` new. `Shapes.stack` makes the middle cell of a whole-centred top row a full block.
- `Adobe.dome` builds one dome per draft (a second call draws nothing and returns the deck), on a round drum of the
  render with a cornice, the dome flush on it, in `domeSheet`'s lime plaster (`expanse:limestone`, polished diorite
  without Expanse) instead of smooth quartz.
- `Towers.lathe` only uses round outlines (no square courses).
- `ClockTower` has five manners (town hall tower, campanile, minaret, drum tower, civic), all with one-block dials.
- `Towers.clock` (used by the town hall, university, market hall, Bohemian towers, court tower, western chapel) now
  lays one dial block in a frame of four dressed stones instead of the three-wide plus.

## plan lane

1. `Signature.OASIS` asks for `landmark/clock_tower` as its second landmark. In desert and steppe the archetype now
   builds a **minaret** (the timekeeper's tower, no bell, no dials). That is right on its own, but a town that also
   raises the `landmark/temple` (the desert temple has its own minaret) gets two minarets. Please skip the clock tower
   in desert and steppe towns that already have a temple, or prefer a gate tower there if one is ever built.

## commerce lane (`MarketHall.domes`)

2. `domes()` calls `Adobe.dome` up to three times along the hall; only the first now builds (the review: at most one
   dome per building), which puts it toward the front. Please call it once at the deck's middle, with `r = 2` (a dome
   seven wide on its drum) where the deck is at least eleven wide.

## civic lane (`Library`, `Bathhouse`, `ChapelWhite`, `PrecinctChurch`)

3. The library's desert dome (`Towers.lathe` over a square shaft, radii 2.6/2.3/1.7/0.9) still reads lumpy. Please use
   `Adobe.dome(d, cx, cz, deck, 1, maxH)` (a round drum and a hemisphere) or `Shapes.domeRows` on a round drum of the same
   width. The same applies to the hammam's hot-room dome (radii 4.4...) and its two corner domelets: a hammam may keep
   small domes, but each should spring flush off its drum.

## landmark lane (`BellTower`)

4. `BellTower` with `clock=true` still lays Big Ben dials (`Details.clock`, radius 2 or 3). `ClockTower.dial(d, face,
   cell, y, dress)` (same package) lays the one-block dial in its frame; please use it.

## social lane (`social/life/Bells`)

5. `Bells.TOWERS` counts `landmark/clock_tower` as a bell tower. In desert and steppe it is a minaret with no bell; the
   town's bell should come from elsewhere there (or the minaret could sound a call rather than a bell).
