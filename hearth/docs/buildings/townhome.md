# Town homes

The dense, tall homes of a settlement: houses that share their side walls along a street, climb storey over storey,
stack up a cliff or turn inward round a court. Eight archetypes, each built in every culture's manner, registered by
`build/archetype/TownHomeArchetypes.java` (the refined townhouse keeps its registration in the shared `Archetypes`).

| id | class | tier, flags | size (frontage × depth) | height | beds, jobs | upgrades to |
|---|---|---|---|---|---|---|
| `hearth:house/rowhouse` | `archetype/house/Rowhouse` | village+, ATTACHABLE | 5–7 × 8–12 | 9–14 | 2 | townhouse, tenement |
| `hearth:house/townhouse` | `archetype/house/Townhouse` | town+, ATTACHABLE, VERTICAL | 5–8 × 8–13 | 14–24 | 4 (CARPENTER with `shop`) | tenement |
| `hearth:house/tenement` | `archetype/house/Tenement` | city, ATTACHABLE, VERTICAL | 8–12 × 10–14 | 18–28 | 8 (CARPENTER with `shops`) | — |
| `hearth:house/shophouse` | `archetype/house/Shophouse` | village+, ATTACHABLE, weight 0.8 | 5–8 × 8–12 | 9–15 | 2, MERCHANT | townhouse, tenement |
| `hearth:house/manor` | `archetype/house/Manor` | town+, max 4, weight 0.5 | 15–25 × 15–25 | 10–16 | 6 | — |
| `hearth:house/tower_house` | `archetype/house/TowerHouse` | hamlet+, VERTICAL; alpine, moorland, mediterranean, karst; weight 0.7 | 6–7 × 7–8 | 14–22 | 2 | — |
| `hearth:house/cliff_dwelling` | `archetype/house/CliffDwelling` | camp+, HILLSIDE, STACKABLE; mesa, karst, alpine; weight 1.5 | 6–10 × 6–9 | 5–6 | 2 | — |
| `hearth:house/courtyard_house` | `archetype/house/CourtyardHouse` | hamlet+; desert, mediterranean, karst; weight 1.2 | 11–15 × 11–15 | 7–12 | 4 | — |

Shared pipeline: `archetype/house/TownHouses` (package-private: storeys, door column, facades with the door pinned,
shells, openings, dressing, pitched roofs over party walls, flat roofs, chimneys, the stair core, rooms, joists, the
street strip, pueblo ladders). Kit: `build/kit/townhome/`:

- `Traits`: what a town house needs from the culture's habits (wall kinds, flat roofs, eastern, pueblo, timber,
  masonry, steep, raised, thatch, lattice, snow), read with the building's variant applied.
- `StairCore`: the switchback well at the back of a narrow house, storey over storey (in a house three wide inside,
  a straight single-lane stair up the side wall with a passage beside it, so a bed fits in the third lane); keeps its
  landing rows free, stops a storey short of an attic whose roof would come down on the top flight (`clears`), and
  builds the stair house (bulkhead) onto a flat roof.
- `Fronts`: string courses, quoins, door cases, pentices, shop fronts (glazed, arched, open), striped awnings,
  hanging signs with the trade's name, counters and wares, life on the step.
- `Rooflines`: roofs planned across party walls (gabled at the party ends), ridge heights by dry run, parapets, crow
  steps, chimney stacks in the wall line.
- `Projections`: oriels (glazed or latticed mashrabiya), galleries, posted galleries, arcades (five-foot ways).
- `Life`: washing lines with sheets, airing rugs, roof gardens (beds, pergola, table and chairs, pots, water tank).
- `Partitions`: walls with doors that split a storey into flats or rooms.

## What each one is

**Row house.** Two storeys on a narrow front, sometimes behind a strip of front garden with a low wall and a gate; a
door under a hood beside a bay window, two bedroom windows over it, the roof storey with dormers; behind, a lean-to
scullery outshot and a yard with the washing line. In a row the party walls rise over the roof with the chimneys on
them in masonry towns; an end house hips or turns its roof toward the side street. Flat-roofed cultures give it a
roof garden over a stair house (a pueblo steps its upper storey back behind a terrace, ladders up), stilt cultures a
double gallery on posts, karst houses a latticed gallery and pentice.

**Townhouse.** Three to five storeys and a roof storey. Timber towns jetty each storey over the street and put an
oriel on the corner; masonry towns band the floors with string courses, raise crow-stepped or parapeted gables and
stand the chimneys on the party walls; hill towns render the walls in a tint over a stone base, hang balconies with
doors onto them and open the top floor into a loggia (a furnished living room behind arches); karst houses rise in
tiers of pent roofs and latticed galleries under a curved roof; desert houses lift a mashrabiya over the street and
crenellate the roof terrace; pueblo houses step back storey by storey into terraces with ladders; raised cultures
stack posted galleries across the front (only where the street meets the floor: a railed deck cannot step down to a
falling street). The switchback stair climbs the back; ground floor kitchen or workroom with a shop window, a living
room, bedrooms, one more under the roof where the stair can reach it (a low roof leaves the attic a closed loft).

**Tenement.** Four to six storeys over a rusticated ground floor: a double door under a fanlight in the middle of the
front into a hall through to the stair every family shares, a landing on every floor and a door off it to each flat
(two flats a floor on a plot 10 or wider, one on a narrower one); flats with kitchen, living and bedrooms; workshops
behind shop windows either side of the hall (`shops`). The street front banded floor by floor with a cornice,
balconies on alternate floors, sheets hung from windows; galleries on brackets at the back strung with washing. The
top is a mansard or the culture's roof with dormers (none through deep alpine eaves), or a flat roof with the stair
house, a roof garden, a water tank and a washing line.

**Shophouse.** The shop on the ground floor, the family over it. The shop storey is taller and opens to the street in
the culture's manner: mullioned display windows over a stall riser, round-headed glazed arches, or the whole front
open between posts (karst, raised); the door in the middle of the front, a striped awning over it all, a hanging sign
with the trade's name (`trade`) on the first floor. In the karst, stilt and hill-town traditions the upper storeys
often come forward over the pavement on piers so a row makes a covered arcade (`arcade`, the five-foot way). Inside,
the counter runs across the shop in front of the stair (the merchant behind it with the stair to the home at his
back) or along the side wall where the shop is shallow, with the merchant's barrel and the stock chest behind it and
shelves of goods on the customers' side. The kitchen hearth is on the first floor, bedrooms above.

**Manor.** The grand house of a town's rich in its grounds: a wall with a carriage gate (piers, lanterns), a drive
past parterres (low hedges round flower beds) and a fountain on the axis to the steps of a raised main block of two
storeys and an attic, the centre marked by a pediment over a columned porch with a balcony; wings come forward on
wider grounds. One roof surface over the whole house (hipped or gabled by culture, valleys where the wings meet,
dormers, tall chimneys); hill towns low tiles, the desert flat roofs with merlons and a dome over the hall, the karst
curved roofs. Inside: an entrance hall with a wide switchback stair, a library of shelves and lecterns with its own
fire, a dining room with a long table, a kitchen and living rooms in the wings, bedrooms above; a back garden of fruit
trees and beds.

**Tower house.** A family's tower: a store at the bottom lit by slits, the door a storey up at the head of a stone
stair along the front (a ground door where the stair cannot meet the street), the hall over the store with its fire,
chambers over that, a battered plinth, machicolations and bartizans in stone towns. The top tells the tradition:
a crow-stepped cap house inside a parapet walk (moorland), a tall hipped roof on deep eaves (alpine), an open loggia
under a low roof (mediterranean), a latticed gallery under a curved flared roof (karst), merlons round a roof
terrace (desert). A switchback climbs one side to the roof; the top room is the lookout.

**Cliff dwelling.** Rooms at the back of the plot under a flat roof deck at the top of the plot's height, a walled
forecourt in front reached from the street; built so the next dwelling can stand on its roof (`stacked`: the front
of the stacked plot is a terrace on the deck below with a parapet and a gate, nothing founded through it). Mesa: adobe,
ladders whose poles stand past the parapet, vigas, a horno in the forecourt. Karst: a limestone sill, bamboo walls,
a gallery under a curved pent roof. Mountains: rubble stone dug into the slope under deep timber eaves with a rail
round the deck. A stair along the house front (or a ladder) climbs to the deck; inside a corner hearth, beds, stores.

**Courtyard house.** Rooms round an open court, the street seeing a blind wall and a gate. Desert: a gate into a
vestibule that turns before the court, a fountain and a palm, an arcade, a stair to the roof terraces, a wind tower.
Mediterranean: an arched carriage door, low tiles falling to the court (a cloister roof), lemon trees in pots, the
back wing a storey higher with a loggia. Karst: halls round a court with a flowering tree, the gate in a corner behind
a spirit wall, curved roofs. Back wing the hall and family rooms, sides bedrooms, front kitchen and stores.

## Params

All optional; defaults are seeded choices. Party walls are always read through `Draft.partyLocalLeft()/Right()`.

| key | archetypes | meaning |
|---|---|---|
| `storeys` | rowhouse (2), townhouse (3–5), tenement (4–6), shophouse (2–3), tower_house (3–5), courtyard_house (1–2) | suggested full storeys; the attached types move a storey up or down now and then by seed so a row steps its eaves, and every type drops storeys (then flattens its pitch, then goes flat) to fit the plot's height |
| `party_left`, `party_right` | rowhouse, townhouse, tenement, shophouse | shared side walls: blind, no eaves, the roof gabled against them; parapets, firewalls and chimneys on them in masonry towns |
| `corner_left`, `corner_right` | rowhouse, townhouse, tenement, shophouse | a street along that side: windows, the roof hipped toward it where the culture hips, a corner oriel (townhouse), the shop front turning the corner (shophouse) |
| `walkway_y` | townhouse, tenement | a walkway meets the front at this world y: a door there onto it |
| `shop` | townhouse | a workroom behind a shop window on the ground floor |
| `terrace` | townhouse | a flat-roofed house steps its top storey back behind a roof terrace |
| `garden`, `outshot` | rowhouse | front garden strip; lean-to scullery behind (deep plots) |
| `arcade`, `trade` | shophouse | upper storeys over the pavement on piers; the word on the sign |
| `shops` | tenement | workshops behind shop windows either side of the hall |
| `stacked` | cliff_dwelling | raised on the roof of the dwelling below (`stacked_on`) |
| `roof` | all but cliff_dwelling | a `RoofForm` name |
| `station`, `household` | all but tenement and manor | who lives there (a `Station` name; `{job: <JobType>}`): the living room is dressed for the household's trade and station (`kit/homestead/Household`, see homestead.md), the materials' finish comes from the style |
| `variant` | all | the style variant |

**Stacking contract (cliff dwelling).** The roof deck's walking level is `floorY + maxHeight - 1` (deck block at
`maxHeight - 2`, a one-course parapet at `maxHeight - 1`), so a dwelling stacked on it has its floor at
`floorY + maxHeight`. The planner's `Stacks` sets the upper plot back half its depth; its front half lands on the deck.

## POIs

Beds, storage, kitchens and workstations come from `Rooms.furnish`. Besides: `DOOR` at every front door;
shophouse `COUNTER` (merchant's side) and `MARKET_STALL` (customers' side) with the MERCHANT workstation;
`VIEWPOINT` on roof gardens, loggias, tower tops and cliff decks; tower house `GUARD_POST` on the roof walk; manor
`GATE`, `LECTERN` (library), `MEETING_POINT` (hall); courtyard house `MEETING_POINT` in the court.

## Culture notes

- **temperate, wisteria**: timber-framed jettied fronts over a stone or brick ground floor, gables to the street,
  oriels, pentices; wisteria in pink-washed plaster and dark frame, flowers in window boxes. Temperate towns are
  Bohemian market towns: shophouses stand over stone arcades round the square (the *podloubí*, arched piers of the
  wall accent rather than timber posts) more often than not, and three in four turn their gables to the street.
- **northern, tundra**: log and plank, steep roofs, galleries on the alpine pattern, snow banks (tundra).
- **alpine**: stone ground storey, timber upper storeys, deep eaves (no dormers through them), front galleries with
  railings; tower houses under tall hipped roofs; cliff dwellings dug into the slope.
- **moorland, maritime**: stone and slate, crow-stepped gables and parapeted party walls with chimneys on them;
  maritime adds painted shutters and white render.
- **mediterranean**: tinted render over stone bases, banded floors, balconies, top-floor loggias, low tile roofs with
  stepped firewalls, roof terraces; tower loggias; courtyard houses with cloister roofs and a raised back wing.
- **desert**: sandstone, flat crenellated roof terraces, mashrabiya oriels, domes over manor halls, courtyard houses
  with fountains and wind towers.
- **mesa**: adobe pueblo terraces stepping back with ladders, vigas, roof gardens; cliff dwellings stacked three high.
- **steppe**: banded brick and plaster, hipped roofs; flat roofs where the habit allows.
- **karst**: limestone podiums and sills, bamboo and timber, tiered pentices, latticed galleries, curved tile roofs
  with lifted corners, open shop fronts and five-foot-way arcades, stepped firewalls.
- **bayou, tropical**: posted galleries across the front (where the street meets the floor), louvered shutters,
  open shop fronts, verandah arcades.

## Previews

From `hearth/`:

```
# one archetype in every style (contact sheet), or chosen styles/terrains
./gradlew blueprintPreview -Parchetype=house/townhouse -Pstyle=all -Pseeds=1 -Pterrain=slope -Dhearth.preview.sheetsOnly=true
./gradlew blueprintPreview -Parchetype=house/tenement -Pstyle=alpine -Pseeds=1 -Pterrain="flat;seed=330025;facing=south;slices=true;slice=y0/z1/c1:0;reach=0"

# a street: rows of attached homes (archetypes cycle along the row), both sides, corners, sections through one plot
./gradlew -I docs/buildings/townhome_street.gradle streetPreview -Prow=house/townhouse,house/shophouse,house/rowhouse -Pstyle=temperate -Pcount=6 -Pterrain=slope -Pseed=3
./gradlew -I docs/buildings/townhome_street.gradle streetPreview -Prow=house/townhouse,house/tenement,house/shophouse -Pstyle=mediterranean -Pcount=6 -Pterrain=slope -Pseed=4 -Pparams=both=true,corners=true
./gradlew -I docs/buildings/townhome_street.gradle streetPreview -Prow=house/shophouse -Pstyle=karst -Pcount=6 -Pparams=plot=2,slice=y0/x3

# a stack of cliff dwellings, each on the roof of the one below
./gradlew -I docs/buildings/townhome_street.gradle streetPreview -Prow=house/cliff_dwelling -Pstyle=mesa -Pterrain=steep -Pparams=stack=3

# a town that grows them
./gradlew settlementPreview -Pculture=temperate/walled -Pdays=400 -Pterrain=hills -Pseed=2
```

Street params (`-Pparams=k=v,k=v`): `both` (a facing row across the street), `corners` (end houses get a side
street), `stack=N`, `frontage`, `depth`, `tier`, `storeys`, `plot=N` with `slice=...` (sections through plot N),
`scale`, `yaw`, `eyaw`, `epitch` (the elevation view). Output: `build/previews/street/street_*.png` (an iso view and
an elevation); the checks run on every plot and print `CHECK` lines.

Tests: `src/test/java/dev/voidmc/hearth/build/townhome/TownHomeTest` (rows of every attached type in every culture on
flat and slope with blind party walls, corner houses, stacks standing on the decks below, walkway doors, beds and job
sites for each house, catalogue entries) on top of the shared `BlueprintInvariantsTest`.

## Known limits

- An attic under a low or front-gabled roof is a closed loft: the side-wall stair cannot reach it without cutting
  the roof, so most row houses and shophouses sleep on the first floor only (2 beds as catalogued; a five-wide house
  often 1).
- Timber fronts read dark in elevation; the karst palette is busy at small sizes.
- The courtyard house and tower house are culture-limited; in other styles (forced in previews) they build, but the
  courtyard's cloister roofs look out of place under thatch or logs.
