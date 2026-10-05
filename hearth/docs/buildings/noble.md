# The nobility: keep, castle, palace, hunting lodge, stables

The noble architect's archetypes (LIVING_WORLD.md §8.1, ids in §8.5), registered in
`build/archetype/NobleArchetypes.java`. Classes in `build/archetype/noble/`, the noble kit in `build/kit/noble/`, names
in `assets/hearth/lang/en_us/build_noble.json`. Tests beyond the shared invariants:
`src/test/java/dev/voidmc/hearth/build/noble/NobleTest.java` (every culture, six terrains, every style variant, the
smallest and largest plots the planner hands them, the POIs each seat must carry, and no stage asking for more than
4,000 goods).

## The catalogue

| id | frontage × depth | height | jobs | beds | tier | cultures | flags | max |
|---|---|---|---|---|---|---|---|---|
| `noble/keep` | 17–25 × 17–25 | 24–34 | LORD, STEWARD, GUARD | 6 | town | all | VERTICAL; upgrades to castle, palace | 1 |
| `noble/castle` | 37–47 × 39–49 | 30–42 | LORD, STEWARD, GUARD, PRIEST | 14 | city | all | VERTICAL | 1 |
| `noble/palace` | 37–47 × 39–49 | 30–42 | LORD, STEWARD, GUARD | 8 | city | desert, mediterranean, karst | VERTICAL | 1 |
| `noble/hunting_lodge` | 15–21 × 14–20 | 20–30 | HUNTER | 4 | town | all | OUTSKIRTS | 1 |
| `noble/stables` | 17–25 × 15–21 | 12–20 | STABLEMASTER | 1 | town | all | | 1 |

The castle and palace share the keep's close (the planner reserves `max(keep.maxFrontage, future.minFrontage)` round
the keep); their smallest plot is the close's size.

POIs: the seats carry `THRONE` (on the dais of the hall, the LORD's job site beside it), `STRONGBOX` and the STEWARD's
lectern (the counting room), `ALTAR` (a chapel niche in the lord's chamber; the castle's chapel with the PRIEST's
lectern), `GUARD_POST`s (gates, tower heads, walks), `VIEWPOINT`s on tower heads, `MEETING_POINT`s at wells and
fountains. The stables carry `HITCHING_POST`s (the rail, the shed) and the STABLEMASTER's trough; the lodge the HUNTER's
kennel and a `HEARTH`.

Params: `station` (the planner's `lord`), `variant`; per archetype `keep_side`, `round` (keep), `tower_side` (palace,
lodge), `shed_side` (stables).

## Construction campaigns

A great building is raised in campaigns (`Draft.campaign(n)`, shared with the excavations' benches and galleries; see
BUILDING_GUIDE.md §"Campaigns"): each
campaign is a whole run of stages (clearing, footings, walls, floors, roof, openings, interior), so a castle half built
looks like one, and no single stage asks for more goods than a town's stores hold. The order check allows the stage
kinds to begin again with each campaign. The castle is raised in nine: the bergfried; the inner curtain in two; the
inner towers; the palace; the inner gate and chapel; the outer curtain; the outer towers, gatehouse and barbican; the
outer ward and courts. The palace in seven, the keep in three, the lodge and stables in two.

## How each culture builds (`kit/noble/Manner`)

| culture | towers | caps (towers / keep) | parapet | halls |
|---|---|---|---|---|
| temperate (Bohemia, KCD II) | half round | tall cones, open battlements on outer towers / hoarded bergfried | square merlons, covered walks under a timber pentice | steep hips, dormers |
| alpine (Tyrol) | square | needle pyramids / onion | square | steep |
| moorland | mixed | cones on bartizans / open | square | crow-stepped gables |
| maritime | round | machicolated open tops | square | slate |
| northern, tundra | square | timber hoardings | timber breastwork | timber tops |
| wisteria | round | tall cones, bartizans | square | steep |
| mediterranean | square | open, machicolated | swallowtail | low hips, loggias |
| desert, mesa | round | open, domes on palace towers | pointed / plain | flat behind parapets, domed audience hall |
| steppe | round | cones | pointed | — |
| karst | square | curved hips (pagoda), stacked eaves on the lord's tower | plain | curved hips on a terrace |
| tropical | mixed | domed garitas | square | — |
| bayou | square | hipped | plain | — |

## The seats

**Keep** — a tower keep at a back corner of a walled bailey: stores, the hall with dais and throne, the bedchamber with
its chapel niche, the steward's counting room, the guards' head; the bailey's curtain with its walk and round turrets;
a gate tower over the way in; a range of two storeys along the back (kitchen, household); a stable shed and a well.

**Castle** — Kingdom Come's Bohemia (Rattay, Talmberg, Trosky). From the road: a walled barbican with its own gate
before a twin-towered gatehouse; the outer ward with stables and smithy in lean-tos against the curtain, a well; the
cross wall, higher, with the inner gate tower over a ramp up to the inner ward on its raised terrace; the bergfried over
the cross wall, the tallest thing for miles, the lord's flag on it; across the back the palace of three storeys (kitchen;
the great hall with the throne; the lord's chamber and the steward's room) with its round stair turret on the court
(its spiral turned to arrive on the palace's side at every storey), a two-storey wing; the chapel with its ridge belfry
(or a palace chapel upstairs in the wing where the ward is too small); towers at every turn of the walls. Curtains are
footed wherever the ground lies and show their full height to the field.

**Palace** — a walled precinct: gate tower, forecourt with trees in planters, the great court with a fountain, ranges
either side with arcades on the court (guards and kitchen; steward and stores; household and the lord's chamber over
them), the audience hall across the back with the throne, the lord's tower at a back corner (round under a dome in the
desert, the torre with machicolated battlements in the Mediterranean, stacked eaves in the karst), towers at the other
corners.

**Hunting lodge** — a hall house of two storeys (the upper half-timbered where the culture frames in timber): the hall
with the hearth and the long table, chambers over it; a slender lookout tower at a front corner with a ladder through
its floors, its head and flag over the ridge; the kennel lean-to against the front.

**Stables** — a stable yard: the stable range across the back (stalls with hay and troughs along its back wall,
lanterns on the stall posts, the hay loft and the stable master's chamber over them, a louvred lantern on the ridge),
an open shed with more stalls along one side, the hitching rail and trough along the other, a low wall round the front
with gate piers.

## Renders

`./gradlew blueprintPreview -Parchetype=hearth:noble/<id> -Pstyle=<culture|all> -Pseeds=2 -Pterrain=hills` writes
`build/previews/noble_<id>/` and a contact sheet `build/previews/sheet_noble_<id>_<terrain>.png`.

## Known weaknesses

- The palace's desert dome reads stepped at preview scale; the karst tower's stacked eaves are single rings, not full
  tiered roofs.
- The hunting lodge's tower head sits close to the ridge where the plot's height is short.
- Materials come from the culture's style JSON (owned by refine): the temperate castle renders in the style's pale
  stone and dark roofs, not the ochre render and red tile of a Bohemian castle; see `docs/requests/b-noble.md`.
