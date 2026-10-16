# Social life: where folk drink, sleep, play, heal and are buried

The family of LIVING_WORLD.md §5–§6, registered in `build/archetype/SocialLifeArchetypes.java`; classes in
`build/archetype/commerce/`, `build/archetype/civic/` and `build/archetype/landscape/FestivalGround.java`, reusable parts
in `build/kit/sociallife/`, lang in `assets/hearth/lang/en_us/build_sociallife.json`, tests in
`src/test/.../build/sociallife/SocialLifeTest.java`. Read `BUILDING_GUIDE.md` first.

| id | file | size (f × d) | height | tier, max | jobs | beds |
|---|---|---|---|---|---|---|
| `hearth:commerce/tavern` | `commerce/Tavern.java` | 10–13 × 10–14 | 9–16 | village, 3 | INNKEEPER, ENTERTAINER | 0 |
| `hearth:commerce/hostel` | `commerce/Hostel.java` | 11–15 × 11–15 | 12–16 | village, 1 | INNKEEPER | 6 |
| `hearth:commerce/caravanserai` | `commerce/Caravanserai.java` | 21–31 × 21–31 | 10–14 | town, 1 (desert, steppe, mesa, mediterranean, karst) | INNKEEPER, TRADER | 8 |
| `hearth:commerce/thieves_den` | `commerce/ThievesDen.java` | 7–10 × 7–10 | 7–10 | town, 1 | FENCE | 0 |
| `hearth:civic/playhouse` | `civic/Playhouse.java` | 15–21 × 15–23 | 10–16 | town, 1 | ENTERTAINER | 0 |
| `hearth:civic/tilt_yard` | `civic/TiltYard.java` | 15–21 × 33–45 | 7–10 | town, 1, OUTSKIRTS | — | 0 |
| `hearth:civic/infirmary` | `civic/Infirmary.java` | 11–15 × 11–17 | 9–14 | town, 1 | HEALER | 0 (cots) |
| `hearth:civic/almshouse` | `civic/Almshouse.java` | 11–15 × 11–15 | 14–16 | town, 2 | — | 6 |
| `hearth:civic/orphanage` | `civic/Orphanage.java` | 11–15 × 11–15 | 14–16 | town, 1 | — | 8 |
| `hearth:civic/cemetery` | `civic/Cemetery.java` | 13–25 × 13–25 | 6–14 | village, 1, OUTSKIRTS | PRIEST | 0 |
| `hearth:landscape/festival_ground` | `landscape/FestivalGround.java` | 21–41 × 21–41 | 6–16 | village, 1, OUTSKIRTS | — | 0 |

Heights differ from `DefaultCatalogue`'s placeholders where two storeys and a pitched roof need the room (the registered
infos are what the planner reads; see the requests file). Every archetype suits all fourteen cultures except the
caravanserai.

## The pieces

**Tavern.** The drinking house, not the inn: one lofty storey open to its tie beams, lanterns hung from them; the bar
along the back wall with the casks racked behind the keeper's lane (jugs and candle stubs on the upper casks), the cellar
stair beside it down to a cellar of casks (sacks in the corners, where a cask would have no face); the great hearth with
its stone breast and stack through the gable; the musicians' stage in a corner with the culture's cloth behind, the floor
before its step kept clear; dice tables of green baize; settles and a trestle; on posts the tap room furnished to the
walls as well (`DINING`). Outside the ale-stake, the painted board on a wrought
bracket, a bench, and behind a garden with a shade tree. Long house or (`gable_front`) gable to the street with a yard
beside it. POIs COUNTER, MARKET_STALL, STAGE, GAMING_TABLE, HEARTH, SEAT, TABLE, STORAGE, DOOR.

**Hostel.** The pilgrims' and travellers' house at the gate, known by its **arcade and its stable**: a two-storey range
on the street line with its ridge along the road, the ground storey opened into an arcade (the Bohemian *podloubí*:
round heads on piers, a paved walk one or two deep under the upper storey, beams and a lantern over it; `Shell`'s
`walk`), the door and the board behind and over it. Below the refectory (the keeper's counter by the door, long tables,
the kitchen fire on the end wall, a back door to the yard), up the stair the dormitory (beds head to the wall, a chest or
a candle stand between each pair). Across the back of the yard the stable: a lean-to on posts against a back wall, stalls
two wide parted by rails, hay, water, the beast's HITCHING_POST in each; the woodpile before it. On posts no arcade: a
low upper storey, the hitching rail in the strip before the house.

**Caravanserai.** The walled inn of the caravan roads round a courtyard: high walls dressed with pilasters where the
cross walls meet them and a string course at the gallery floor, corner towers standing over them; before the gate the
pishtaq, a screen of the walls' masonry seven wide standing proud of the front and four over the roof, framed in the
dressing, its tall iwan with a stepped pointed head, a keystone, a band of trim and a coping; the gate's two leaves folded
open under a lintel, the passage three wide and three high under the gate room (the keeper's lookout: a table under the
window into the iwan, a stool, a chest) and the gallery. Round the court two storeys of rooms in pairs (seven long, two
doors, two beds above) behind an arcade of piers and arches (two cells deep so the gallery on its roof has a parapet):
stables (trough, hay, the beast's HITCHING_POST) and storerooms below, the innkeeper's and trader's counters either side
of the gate, the kitchen in the back range; travellers' rooms above (bed, chest, rug). A fountain in the court, bales and
barrels, lamp posts, stairs to the gallery at the back corners, walled on their courtyard side. Flat roofs behind parapets in the dry lands, a tiled ring roof (`Ring`) elsewhere.

**Thieves' den.** An ordinary poor cottage in a back lane (StyleContext implies the POOR station): table, stool, fire. In
its back corner a stair (railed, half hidden behind barrels) down to a dug cellar: the fence's counter (JOB_SITE FENCE,
COUNTER, MARKET_STALL), the STRONGBOX (also STORAGE), a loot chest, barrels, a dice table, all lit. Where the culture
builds on posts no cellar is dug: a back room behind a plank partition instead.

**Playhouse.** A galleried yard theatre: round three sides a timber-framed (masonry in the stone and dry lands) range of
galleries, two tiers where the height allows, benches facing in with gaps to pass, posts along the yard's edge, a ring
roof whose ridge runs round the yard; at the back the tiring house, two blocks taller under its own gable with the
company's flag, costume chests inside; out of it by two doors the thrust stage with the heavens on two posts. In through
double doors under the board. POIs STAGE + ENTERTAINER, SEATs, STORAGE, MEETING_POINT, DOOR.

**Tilt yard.** The lists outside the walls: a fenced field, the tilt barrier down the track with painted end posts and
banners; on one side three tiers of benches, the lord's box in the middle under a canvas canopy with a valance, the
THRONE at its top; on the other side two knights' pavilions (undyed canvas banded in the colour of the house that holds
the tilt, the challenger's pennant, arms chest = STORAGE, hung lantern); colours are natural dyes (`Fair.natural`);
the quintain at the far end; lamps; a wide gate from the street.

**Infirmary.** Known by its **long ward behind the herb garden**: one lofty storey under tie beams across the back of the
plot, a louvre on the ridge to let the foul air out, and before it the physic garden in view of the street (herb beds
either side of the path from a gate in a low wall, a lamp). Inside, down both long walls the cots of the sick
(`Wards.cot`: a white bolster, a blanket of the house's cloth, SICKBED — not beds, so a hospital never counts as
housing), the healer's stand between each pair; an altar at the far end, a stove; beside the door, behind a partition,
the dispensary (brewing stand = HEALER's JOB_SITE, a chest of herbs, barrels under decorated pots). Where the ground
before it is not gentle, or on posts, the ward stands at the front and the garden behind.

**Almshouse.** Known by its **bell-cote and its row of cells round a court**: from the street the low gable ends of the
cells and the court's coped wall with its roofed gate (where the street meets the court's level); across the back of
the court the hall on `Range` (its front wall on the court), two storeys, the ground storey of stone, a bell-cote on the
ridge (a bell gable over the parapet where roofs are flat), a cross over the door; down one side of the court (both on a
wide plot, each kept clear of the hall's door) the single-storey row of cells, a door and a lamp for each (`Shell`'s
several doors), a bed and a chest inside, a bench against it. In the hall the common room (the kitchen fire, long
tables, an altar with a cross banner, the alms box by the door) and above the sleeping floor for those without a cell.
In the court a wayside cross, a lamp, a bed of herbs along the wall. On posts, or where the street nowhere meets the
plot's level, the hall stands alone at the front with its kitchen garden behind.

**Orphanage.** Known by its **yard and dormitory**: a tall narrow house gable-on to the street down one side of the plot,
the steep roof with dormers over the long dormitory (small beds packed side by side down both walls, each reached from
its foot), and beside it the children's walled yard (a gate where the street meets it, a fence round the rest, a swing, a
shade tree, their garden rows, a lamp), a door out to it from the hall. In the street gable beside the door the
foundling wheel (a barrel turned in the wall) and the board. Below the hall: the matron's lectern facing the tables, the
kitchen fire on the back wall, a chest of toys and clothes by the door. On posts a `Range` across the front with the
yard behind.

**Cemetery.** Walled (Enclosure: rendered wall under a coping, pales, hedge), a roofed lychgate, gravel walks, rows of
graves in each culture's manner (`Graves.Manner`), the calvary where the walks cross (funerals gather there), yews, a
mortuary chapel with a bell-cote at the back; terraced on hillsides.

**Festival ground.** The culture's centrepiece on a dance floor (maypole, midsummer pole, beacon, lantern poles,
horsetail standard, vine pergola, obelisk, bonfire), the stage, a row of booths down each side the length of the green,
trestles, banner posts, bonfire pit, hay, trees, the garlanded arch. The Bohemian maypole is a pole: a stripped trunk
with only its spruce tip left green, and a wreath well under it with the ribbons hanging off it on four sides. Where the
green's terraces stand over falling ground a parapet or a rail guards them (`Terraces`, `Edges`). Booths and the stage canopy are undyed canvas with a valance of the fair's one colour
(`Fair.houseColour`, madder, woad or weld), striped only in the mediterranean and the desert; ribbons and banners take
the same natural dyes. The front rows meet the street; the green lies on one level within a two-blocks-a-band climb of it
and under the plot's height.

## The kit (`build/kit/sociallife/`)

- `Shell`: a house of this family storey by storey in the culture's construction with its door, lamp and roof (Bohemian
  steep gables boarded over a pent, the karst's curved roof, flat behind a parapet in the dry south).
- `Range`: the two-storey charitable range (shell, stair along the back wall, both floors as `Floorplan`s), its end-wall
  hearth, long tables, a wall bench.
- `Wards`: cots (SICKBED) and rows of beds or cots along a wall, at a pitch (1 packs them side by side).
- `Venue`: stage (STAGE + ENTERTAINER), dice tables (GAMING_TABLE), benches, trestles, banners.
- `Signboard`: bracket boards and the ale-stake; translatable names for taverns, hostels, almshouses, orphanages,
  infirmaries and playhouses.
- `Cellar`, `RoomFrame`, `Enclosure`, `Graves`, `Fair` (the tavern's cellar, rooms seen from a wall, yard walls and
  lychgates, graves, festival pieces).
- `Ring`: ranges built round a yard and their ring roof. `Yardwork`: wayside cross, hitching rail, herb beds, swing,
  partitions, bell-cote.
- `Shell`: the family's house shell; several doors in one wall (a row of cells) and an arcaded front (`walk`).
- `Range`: the charitable two-storey range (common hall below, beds above), after a forecourt or with its front wall in
  a given row (the almshouse's hall across the back of its court).

## Looking at them

`./gradlew -I docs/buildings/sociallife_preview.gradle socialPreview -Parchetype=<id> -Pstyle=<cultures|all> -Pseeds=2
-Pterrain=<terrain>` renders large contact sheets and per-building views with plans and cuts at every floor to
`build/previews/sociallife/`.

## What is weak

- The charitable houses each have their own plan and sign now (court and bell-cote, yard and gable, ward behind the
  garden, arcade and stable) on gentle ground; on posts, and the almshouse and infirmary on a bank, they fall back on
  the plain range. The almshouse cells are small (three by two or three) and dark timber in the Bohemian village palette.
- The tilt yard's stands read as a solid wedge from the side; the pavilions are drum-shaped, not conical.
- The thieves' den's stair is open, not truly hidden (a closed trapdoor would break the walkability check).
- The caravanserai is one plan in its five cultures (palette only); the corner blocks are solid.
- Bohemian looks come from the culture's palette through `Shell`; the playhouse and tilt yard are generic timber.
