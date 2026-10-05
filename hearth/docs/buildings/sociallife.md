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
along the back wall with the casks racked behind the keeper's lane, the cellar stair beside it down to a cellar of casks;
the great hearth with its stone breast and stack through the gable; the musicians' stage in a corner with the culture's
cloth behind; dice tables of green baize; settles and a trestle. Outside the ale-stake, the painted board on a wrought
bracket, a bench, and behind a garden with a shade tree. Long house or (`gable_front`) gable to the street with a yard
beside it. POIs COUNTER, MARKET_STALL, STAGE, GAMING_TABLE, HEARTH, SEAT, TABLE, STORAGE, DOOR.

**Hostel.** The pilgrims' and travellers' house at the gate: a plain two-storey range with its ridge along the road;
below the refectory (the keeper's counter by the door, long tables, the kitchen fire on the end wall), up the stair the
dormitory (beds head to the wall, a chest or a candle stand between each pair). Outside a bench, the board, a wayside
cross, a hitching rail with its trough; behind a well and the woodpile. On posts a low upper storey.

**Caravanserai.** The walled inn of the caravan roads round a courtyard: high blank walls, corner towers standing over
them, the portal over the gate passage; round the court two storeys of cells behind an arcade of piers and arches (two
cells deep so the gallery on its roof has a parapet): stables (trough, hay, the beast's HITCHING_POST) and storerooms
below, the innkeeper's and trader's counters either side of the gate, the kitchen in the back range; travellers' rooms
above (bed, chest, rug). A fountain in the court, bales and barrels, lamp posts, stairs to the gallery at the back
corners. Flat roofs behind parapets in the dry lands, a tiled ring roof (`Ring`) elsewhere.

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
banners; on one side three tiers of benches, the lord's box in the middle under a striped canopy with a valance, the
THRONE at its top; on the other side two knights' pavilions (striped tents, pennant, arms chest = STORAGE, hung lantern);
the quintain at the far end; lamps; a wide gate from the street.

**Infirmary.** One lofty storey under tie beams: down both long walls the cots of the sick (`Wards.cot`: a white bolster,
a blanket of the house's cloth, SICKBED — not beds, so a hospital never counts as housing), the healer's stand between
each pair; an altar at the far end, a stove; beside the door, behind a partition, the dispensary (brewing stand = HEALER's
JOB_SITE, a chest of herbs, barrels under decorated pots). Behind, the physic garden of herb beds and a well.

**Almshouse / orphanage.** Both on `Range`, a plain two-storey range: below the common hall (the kitchen fire on the end
wall, long tables, an altar with a cross banner and the alms box by the door — or, in the orphanage, the matron's lectern
facing the tables and a chest of toys), above the beds (one each with a chest or candle stand in the almshouse; packed
side by side, each reached from its foot, for the children). Outside a cross over the almshouse door, boards, a bench;
behind a kitchen garden and well, or the children's yard with a swing, a shade tree and their garden row.

**Cemetery.** Walled (Enclosure: rendered wall under a coping, pales, hedge), a roofed lychgate, gravel walks, rows of
graves in each culture's manner (`Graves.Manner`), the calvary where the walks cross (funerals gather there), yews, a
mortuary chapel with a bell-cote at the back; terraced on hillsides.

**Festival ground.** The culture's centrepiece on a dance floor (maypole, midsummer pole, beacon, lantern poles,
horsetail standard, vine pergola, obelisk, bonfire), the stage, booths, trestles, banner posts, bonfire pit, hay, trees,
the garlanded arch. The front rows meet the street; the green lies on one level within a two-blocks-a-band climb of it
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
  partitions.

## Looking at them

`./gradlew -I docs/buildings/sociallife_preview.gradle socialPreview -Parchetype=<id> -Pstyle=<cultures|all> -Pseeds=2
-Pterrain=<terrain>` renders large contact sheets and per-building views with plans and cuts at every floor to
`build/previews/sociallife/`.

## What is weak

- The almshouse, orphanage and hostel share one shell and read alike from outside; they want a bell-cote, an arcade or
  an oriel each.
- The tilt yard's stands read as a solid wedge from the side; the pavilions are drum-shaped, not conical.
- The thieves' den's stair is open, not truly hidden (a closed trapdoor would break the walkability check).
- The caravanserai's portal is half hidden under the ring roof in the pitched cultures; the cells near the ring's corners
  stay empty.
- Bohemian looks come from the culture's palette through `Shell`; the playhouse and tilt yard are generic timber.
