# Trade buildings: workplaces and stores

The trade architect's archetypes: thirteen workplaces and three stores, registered in
`build/archetype/TradeArchetypes.java` (the builders' yard stays registered in `Archetypes.java`). Classes live in
`build/archetype/work/` and `build/archetype/storage/`, the shared trade kit in `build/kit/trade/`, names and sign
texts in `assets/hearth/lang/en_us/build_trade.json`. Tests beyond the shared invariants:
`src/test/java/dev/voidmc/hearth/build/trade/TradeArchetypesTest.java`.

Every archetype reads `variant` (the style variant) and, where it has a choice of roof, `roof` (a `RoofForm` name);
the extra params are listed per archetype. Every building fits its roof under the plot's height (storeys are budgeted
down before the roof is flattened), grounds itself on any terrain, keeps its main entrance on the street row, and
keeps every job site and store reachable from a door (`BlueprintChecks`). A flat-roofed building (desert, mesa, and
anywhere the roof param asks for `flat`) makes its roof a terrace: an outside stair up a side wall to a gap in the
parapet (VIEWPOINT at its head), a little room set back at a back corner, a cloth awning on poles and pots.

## The catalogue

| id | frontage × depth | height | jobs | tier | flags | max |
|---|---|---|---|---|---|---|
| `work/builders_yard` | 11–17 × 11–17 | 9–14 | BUILDER (+ a MASON site) | camp | | 3 |
| `work/smithy` | 8–12 × 8–12 | 8–13 | SMITH | hamlet | | |
| `work/carpentry` | 7–11 × 8–12 | 8–13 | CARPENTER | hamlet | | |
| `work/masonry` | 8–12 × 8–12 | 8–13 | MASON | village | | |
| `work/bakery` | 7–10 × 7–10 | 8–13 | BAKER | village | ATTACHABLE | |
| `work/brewery` | 9–13 × 9–13 | 11–16 | BREWER | village | | |
| `work/tailor` | 6–9 × 7–11 | 8–14 | TAILOR | village | ATTACHABLE | |
| `work/sawmill` | 9–13 × 9–13 | 7–11 | FORESTER, CARPENTER | hamlet | OUTSKIRTS | |
| `work/quarry` | 12–20 × 12–20 | 6–12 | QUARRIER, MASON | hamlet | OUTSKIRTS | |
| `work/fishery` | 7–11 × 7–12 | 7–14 | FISHER | camp | WATERFRONT | |
| `work/windmill` | 13–17 × 11–15 | 16–26 | FARMER, BAKER | village | VERTICAL, OUTSKIRTS | 2 |
| `work/watermill` | 9–12 × 10–13 | 9–14 | FARMER, BAKER | village | WATERFRONT (weight 1.2) | 2 |
| `work/hunters_lodge` | 7–10 × 7–10 | 6–10 | HUNTER | camp | OUTSKIRTS | |
| `storage/storehouse` | 7–11 × 7–11 | 8–13 | — | camp | | |
| `storage/granary` | 7–11 × 9–13 | 8–12 | — | village | | |
| `storage/warehouse` | 11–17 × 13–19 | 10–18 | — | town | | |

Measured over 84 plots each (14 cultures × flat/hills/river × 2 seeds, mid-range sizes, max height): every workplace
has at least two job sites (builders' yard ≥ 5, smithy/carpentry/masonry/watermill ≥ 3, brewery/sawmill ≥ 4) and a
job site for every job it lists; every building has a store. The largest single stage asks for 536 goods (a
warehouse's walls), the largest whole building about 1,400 on average (the warehouse); most stages are under 300.

## Archetypes

### `work/builders_yard` (BuildersYard.java)
A fenced yard entered under a timber gate (capped beam, hanging "Builders" sign). At the back an open work shed under
a gable; at one end the master builder's office, two storeys, a turning stair, the plans on a lectern, a gallery to
watch the yard. In the yard: logs on bearers, stickered board stacks, dressed stone on pallets (where there is room),
stacked scaffolding, sawhorses, a loaded cart, a trial corner of wall in its scaffold, a timber crane swinging a load.
**POIs:** GATE, DOOR (office), JOB_SITE BUILDER ×4–5 (benches, grindstone, sawhorses, crane foot, trial wall),
JOB_SITE MASON (stone saw), STORAGE ×4 (shed chests, office chest), LECTERN, VIEWPOINT (gallery, about half).
**Params:** `roof`, `variant`.

### `work/smithy` (Smithy.java)
A tall single-storey hall that opens to the street through a wide bay. Against a gable wall the forge: stone cheeks,
lava cauldron hearth, lit blast furnace and a hood, its breast and broad stack built outside the wall in masonry and
smoking past the ridge. Anvil(s) before the fire with the quench trough, tool racks, coal and iron bins; the eave runs
on over the street front as a pentice on posts (a cloth awning on poles where roofs are flat) over the grindstone and a
customers' bench; a side yard of charcoal, scrap and a woodpile, with a cart of raw iron by the street where the yard
is three wide (a heap of ore and coal where it is narrower); tie beams across the hall.
**POIs:** DOOR, JOB_SITE SMITH ×3–4 (anvils, blast furnace, smithing table, grindstone), STORAGE ×2, SEAT (bench, some).
**Params:** `roof`, `variant`.

### `work/carpentry` (Carpentry.java)
Gable to the street, double doors open, benches under the windows, a board loft in the roof with a loft door and
hoist beam at the gable. The lumber store runs along the side under the workshop's eave carried on posts (or as a
lean-to behind on narrow plots): log racks and stickered boards. Yard: sawhorses with a plank, half-made furniture.
Karst joiners stack bamboo.
**POIs:** DOOR ×2, JOB_SITE CARPENTER ×3 (benches, sawhorses), STORAGE ×4.
**Params:** `roof`, `variant`.

### `work/masonry` (Masonry.java)
A banker shed along the back, its back and ends of dressed stone, open front on posts, stonecutters in a row beneath.
A paved yard of work in hand: ashlar on pallets, a column carved from base to capital, a gantry with a block on its
chain, spalls; a low dry-stone wall with the gate and a sign on a post.
**POIs:** JOB_SITE MASON ×3–4 (stonecutters, the column), STORAGE (chest of dressed stone). No door (open shed).
**Params:** `roof`, `variant`.

### `work/bakery` (Bakery.java)
A high-street shop of one or two storeys (blind party walls in a row): a wide shop window with loaves and cakes on the
sill board, a cloth awning, the door under a hanging sign. Inside a counter across the shop, the baker behind it, a
vault of brick ovens against the back wall with its stack smoking over the roof (an oven hump outside the back wall
where the plot is deep enough), flour sacks and racks; a flour loft upstairs. A freestanding bakery keeps a side yard
with a domed outdoor oven where the culture bakes outdoors (flat-roofed, adobe and plaster cultures, otherwise now and
then) or a woodpile.
**POIs:** DOOR (1–2), JOB_SITE BAKER ×2–3 (each oven, the yard oven's mouth), COUNTER (about 4 in 5 plots; the
narrowest have no room beside the door), STORAGE (flour chest), SEAT/TABLE (some).
**Params:** `party_left`, `party_right`, `storeys` (1–2), `roof`, `variant`.

### `work/brewery` (Brewery.java)
A low brewhouse with the coppers (cauldrons over a slow fire), brewing stands on a bench and barrel racks in two tiers;
beside it the malt kiln: an oast, square or round, with a steep solidified pyramid or cone roof and a white cowl
(kept clear of the door). Hop poles in the yard. Wine cultures (flat roofs, lattice windows, adobe) instead have rows
of great jars under a pergola and no kiln.
**POIs:** DOOR, JOB_SITE BREWER ×4 (brewing stands, coppers), STORAGE ×4–5 (barrel racks, chest).
**Params:** `roof`, `kiln` (boolean; default on for non-wine, pitched-roof cultures where the height allows), `variant`.

### `work/tailor` (Tailor.java)
Narrow and tall on the high street (party walls in a row): a shop window of dyed wool bolts (Shop.span keeps it clear
of the door), a full-width striped awning of wool stairs in the shop's palette, lengths of cloth (banners) hung on the
upper front. Inside a counter, cloth shelves, a loom at the back; a switchback stair to the workroom with its looms,
often a balcony. The dye yard behind on deep plots: cauldron vats and cloth drying on a rail. In a shallow house whose
stair comes down near the front the window has no sill board, so the stair foot stays reachable.
**POIs:** DOOR, COUNTER, JOB_SITE TAILOR ×2–3 (every loom; a second loom below where no stair reaches a workroom),
STORAGE (cloth chest), TABLE (cutting tables), VIEWPOINT (balcony/terrace, some).
**Params:** `party_left`, `party_right`, `storeys` (1–3), `roof`, `variant`.

### `work/sawmill` (Sawmill.java)
A long saw shed on posts across the back of the yard, boarded at the back: the saw bench on the front row (a
stonecutter for the blade, the log carriage on rails), an aisle, stores along the back row. Logs on bearers in
pyramids up to three high either side of the gate, lying front to back so the street sees their sawn ends stacked (the
style's tree logs: baobab on the steppe, birch in the north), a loaded log cart, a chopping block, stickered boards, a
fence and wide gate. Where water runs at the back of the plot a water wheel on the shed's end drives the saw.
**POIs:** JOB_SITE FORESTER ×2 (log carriage, log piles), JOB_SITE CARPENTER ×2 (saw, bench), STORAGE.
**Params:** `roof`, `variant`.

### `work/quarry` (Quarry.java)
A pit cut in benches two high and two wide, only where the ground stands above the level (on a hillside the benches eat
back into the slope and the face behind is lined with fresh rock). A ramp of steps from the yard, a gantry over the
pit lowering a block (its chain clear of the ramp), the masons' shed with stonecutters at the top, block stacks and
spoil heaps, a fence round the rim.
**POIs:** JOB_SITE QUARRIER ×1–2 (pit floor at the face), JOB_SITE MASON (stonecutters), STORAGE ×2.
**Params:** `roof` (the shed), `variant`.

### `work/fishery` (Fishery.java)
A boarded house with its back to the water (a tall three-floor net loft in maritime towns), a back door onto a jetty on
piles that runs out over the water to the mooring, a lantern, barrels, nets. Net frames and a drying rack beside the
house (on dry ground only), the smokehouse hearth and chimney inside. On soft or flooded ground the house stands on
piles with a gallery.
**POIs:** DOOR ×2 (front and back), HEARTH, JOB_SITE FISHER ×2–3 (gutting benches inside, the jetty's end over water;
a small house on dry land puts its second hand mending nets by the frame or on the front step), DOCK (only where the
jetty reaches water: never on dry land), STORAGE ×3 (salt barrels, chest).
**Params:** `storeys` (1–3), `roof`, `variant`.

### `work/windmill` (Windmill.java)
**Tower mill:** a round tower on an integer centre (r 3–3.4) standing forward in its plot, plumb through its ground
storey and tapering by most of a block above it, a skirt, a band, a reefing stage (boards out a block and a half on raking
braces, a railing, a sack on the deck), a spiral stair; a smock mill's upper body boarded in timber (the gable boards, or
the floor's where the gables are rendered) between corner posts over a stone base where the culture builds in timber; a
boat-shaped bonnet in the north and west (a hull upturned over a curb on brackets, its sides rounding to the ridge, its
back end rounded, the breast boarded between battens where the windshaft comes through) or a cone of thatch/tile in the
south. The cap is turned to the wind by a fantail on a masonry tower's bonnet (a post off the curb carrying a little
wheel of spokes and vanes) or a tail pole down to the stage with its winding wheel (smock mills, cones). Common sails are
lattices: a whip from the hub, a hemlath along the trailing edge, and, by pairs, cloth spread from whip to hemlath or
the bare frame of sail bars showing against the sky; the Mediterranean star has eight spars with triangles of cloth
widening to their tips. No sail sweeps lower than a man's height over the yard. **Post mill** (log/snow/plank cultures,
about two in three, plots 9+ deep): the plank body high on its post and trestle (two storeys where the height allows),
the stair down behind with the tail pole beside it to the ground, sails on its breast.
**POIs:** DOOR, JOB_SITE BAKER (millstones), JOB_SITE FARMER (grain bin), STORAGE (flour chest; a barrel by the door
when the floor is too cramped). Ground-floor pieces move outside by the door when the tower is too small.
**Params:** `kind` (`tower`, `post`), `sails` (`cross`, `saltire`, `star`), `variant`.

### `work/watermill` (Watermill.java)
A stout two-floor mill house with stone lower walls beside a stone-kerbed race from the water behind; the wheel
(radius 4 on plots 12+ deep and 10+ wide, else 3) turns in the race, its axle through the wall to the stones.
Sacks in, flour out; the loft stores meal, and where the front wall has room a loading door high in it with a hoist
beam and a sack of flour on its chain marks the mill from the street side. Storeys budgeted under the roof.
**POIs:** DOOR, JOB_SITE BAKER ×2 (millstones), JOB_SITE FARMER (grain bin), STORAGE ×4–5.
**Params:** `roof`, `variant`.

### `work/hunters_lodge` (HuntersLodge.java)
A low lodge under a steep roof whose eave runs on over a deep porch; skull and bleached birch antlers over the door,
hides stretched on a rail, a smoking rack in the yard, an archery target, chopping block and woodpile; inside the
fletching table, furs, the hearth.
**POIs:** DOOR, HEARTH, SEAT, JOB_SITE HUNTER ×2–3 (fletching table, smoking rack, skinning bench), STORAGE (hides).
**Params:** `roof`, `variant`.

### `storage/storehouse` (Storehouse.java)
**Raised store** (stabbur/raccard; cold and log cultures, plank cultures now and then; plots 9+ deep): a log store on
posts with stone caps, its upper storey oversailing every side, approach steps that stop short of the door, a height
budget (upper 3 → 2, posts 2 → 1, else not raised). **Loft store:** double doors over a loading step, a loft with its
gable door and hoist. **Flat store** (desert, mesa): thick walls, a crowned parapet, jars by the door. Stilt cultures
(tropical, bayou, or water on the plot) raise any of them on stilts with a gallery.
**POIs:** DOOR, STORAGE ×6 on average, SEAT/VIEWPOINT (terrace, flat stores).
**Params:** `roof`, `raised` (boolean; ignored on plots under 9 deep, with flat roofs or stilts), `variant`.

### `storage/granary` (Granary.java)
**Staddle granary** (default): a framed store on mushroom staddle stones with a sill beam, vents, a steep roof and
approach steps. **Bins** (cultures with round plans, e.g. steppe, tropical): round mud bins with cone roofs on a stone
platform, the largest with the door at the back (smaller ones beside it where the plot is broad enough to keep a cell
between their eaves). **Domes** (flat-roofed cultures: desert, mesa): a cluster of touching beehive domes in a walled
yard, the largest with the door.
**POIs:** DOOR, STORAGE ×2–3.
**Params:** `kind` (`staddle`, `bins`, `domes`), `roof`, `variant`.

### `storage/warehouse` (Warehouse.java)
Front gable with the loading bay up its middle: cart doors below, a pair of loading doors on every floor above, the
hoist beam out of the gable peak (or over the parapet) with a barrel on its chain. Floors budgeted under the roof (and
an attic for the hoist where it fits); a switchback stair, barrel racks in two tiers and crates down the middle on every
floor; a paved apron with crates and a cart; a cart shed on plots 14+ wide; a hanging sign.
**POIs:** DOOR, STORAGE ×4–5 (several per floor).
**Params:** `storeys` (1–4 floors, default 3), `roof`, `variant`.

## Culture notes

- **Temperate, wisteria, moorland, maritime:** timber-frame or stone halls with gables; maritime fisheries are the tall
  net lofts; windmills get boat caps and cross sails; bakeries in plaster cultures bake outdoors too.
- **Northern, tundra, alpine:** log and plank; raised storehouses on deep plots, post mills, steep roofs, snow habits.
- **Desert, mesa:** flat roofs everywhere a form is free (terraces, crowned parapets), domed granaries, star-sailed
  tower mills with cone caps, wine-jar breweries, outdoor dome ovens, flat stores with jars.
- **Mediterranean, steppe:** low pitches; star sails likely; steppe granaries are round bins; mediterranean breweries
  keep wine jars under a pergola.
- **Tropical, bayou:** stilted storehouses with galleries, bin granaries in the tropics, fisheries on piles over soft
  ground.
- **Karst:** pagoda roofs where offered, bamboo in the carpentry's racks and bamboo fences.

Hanging signs read `hearth.sign.<trade>` (smithy, bakery, tailor, carpentry "Joiner", masonry "Mason", sawmill, quarry,
fishery "Fish", brewery, watermill "Mill", warehouse, builders_yard "Builders") with an English fallback.

## The trade kit (`build/kit/trade/`)

`Hall` (a closed building: shell, facade, doors, bays, roof fitted under the height with a hip and then flat fallback,
`fitStoreys`/`budget`, stairs `flight`/`switchback`, `openLoft`, `chimney`, `crown`, `terrace`), `Floorplan` (hand
furnishing that keeps doors, stair feet and users' cells connected), `Shed` (open sheds, lean-tos, posts, `solidify`
for steep peaks), `Pentice` (eaves carried on over a front, cloth awnings), `Forge`, `Shop` (window, counter, awning),
`Mills` (water wheels, sails), `Lifts` (crane, gantry, hoist), `Props` (job sites and stores with the right POIs,
piles, crates, carts), `Signs`, `Yard` (entrance on the street row, paths, fences).

## Previews

From `hearth/`, with `JAVA_HOME` set to the JDK 25 (images land in `hearth/build/previews/`):

```
./gradlew blueprintPreview -Parchetype=hearth:work/smithy -Pstyle=all -Pseeds=2 -Pterrain=hills
./gradlew blueprintPreview -Parchetype=hearth:work/fishery,hearth:work/watermill,hearth:work/sawmill -Pstyle=all -Pseeds=1 -Pterrain=river
./gradlew blueprintPreview -Parchetype=hearth:storage/storehouse -Pstyle=northern,alpine,desert -Pseeds=4 -Pterrain=flat
./gradlew settlementPreview -Pculture=temperate -Pdays=400 -Pterrain=hills -Pseed=2
./gradlew settlementPreview -Pculture=maritime -Pdays=300 -Pterrain=river -Pseed=3
```

Each archetype gets `previews/<category>_<name>/<culture>_s<seed>_<terrain>.png` (two views, storey plans and cuts),
`..._front.png`, and a sheet `previews/sheet_<category>_<name>_<terrain>.png`. In game:
`/hearth build hearth:work/windmill <style> ...` (see BUILDING_GUIDE.md).

## Known limits

- Masonry and sawmill are open sheds with no DOOR POI; the planner's main entrance is the yard gate.
- The fishery's DOCK and jetty job site exist only where water lies behind the house (WATERFRONT); on dry plots it is a
  fish house with nets.
- A post mill or tower mill has exactly one BAKER and one FARMER site.
- Bakeries on the narrowest plots (7 wide with a party wall) can lack a COUNTER.
- Raised storehouses need plots 9+ deep; on shallower plots the culture's loft store is built instead.
