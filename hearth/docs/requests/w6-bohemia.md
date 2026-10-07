# Requests from the Bohemia lane (wave 6)

The Bohemia lane rebuilt the temperate prominent buildings' geometry (art review items 11, 13, 20 and the tour's chapel,
`w5-tour.md` §7): the town hall, the parish church, the wayside shrine, the bell tower and the cathedral's spire colour,
on the shared tower kit `kit/civic/Bohemian` (white render, a timber gallery, dark shingle helms and onions, no
merlons or corner pinnacles). What belongs to other lanes is below.

## 1. Palettes (`temperate.json`)

- **The town hall still wears burgher brick on a share of seeds.** `burgher brick` names `civic/town_hall` in its
  condition (w 14 against the Bohemian town's 30), so about one hall in three is red brick with brick dressings, which is
  the Hanseatic hall the review faulted (item 11). Asked: drop `civic/town_hall` from burgher brick's archetype list
  (item 6 of the review: Bohemian manners at 85 % or more of civic buildings). The geometry no longer adds quoins or crow
  steps, but it lays whatever the wall and wall-accent roles are.
- **Red tile church roofs.** The tour's note asks for a red tile nave roof on the Bohemian village church (Rattay,
  Sasau). The `bohemian church` manner roofs in dark oak and spruce shingle with red tile only from tier 3 (w 0.8).
  Asked: let red tile compete from tier 1 (about one church in three), keep shingle the majority. The towers keep dark
  shingle whatever the nave wears (`Bohemian.shingle`).
- **The `sheet` role is copper.** `sheet` in temperate (and wisteria) is waxed oxidised copper; `Bohemian.shingle`
  skips it when it is copper, but other users of the sheet (`Stone.sheet`, `Crowns.Mats.coppered`) turn temperate
  domes and spires turquoise. Asked: a dark sheet in temperate (deepslate tiles, or dark oak), copper only as a variant
  for the odd baroque onion.

## 2. Crowns (`kit/civic/Towers`, `kit/landmark/Crowns`)

- `Bohemian.cap` draws the helm as `Shapes.needle` off a ring of stairs (the bell-cast kick) and the onion through
  `Crowns.crown(ONION)` with a dark sheet. When the crowns lane adds a proper helm (or reshapes the onion), the helm
  branch of `Bohemian.cap` should call it; the kick ring and the gallery stay the Bohemian kit's.
- `Towers.spire` clads its needle in copper over a thatching town or a roof without stairs, and `Crowns.Mats.coppered`
  in copper wherever the roof is wood. In the temperate culture both should take `Bohemian.shingle(d)` instead (item
  13: no teal needles on a temperate skyline).

## 3. Markets (`commerce/market_hall`)

- The temperate market hall's spire or cupola (item 13 names "market spires") should take `Bohemian.shingle(d)` for its
  covering, or `Bohemian.cap(d, tower, y, top, dress, tier, Bohemian.Cap.HELM)` over a five-square turret.
