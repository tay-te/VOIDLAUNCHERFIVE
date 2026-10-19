# Requests from lane L5 (landmark, noble, defence), wave 7

Branch `hearth-w7-landmark`. Things found while fixing the landmark, noble and defence archetypes that belong to other
lanes, or that other lanes should know about.

## 1. `Set.of` and `HashSet<Direction>` order changes between game runs (all kit owners, L0)

`Set.of(...)` iterates in an order salted per JVM (`ImmutableCollections.SALT`), and a `HashSet<Direction>` iterates
by identity hash. Any kit that walks such a set and *chooses* as it goes (the first direction a door fits, a stagger
counter for loops, the face a watch looks out of) draws a different blueprint on every server start, and the audit
gives different numbers from one run to the next. Found in `kit/noble` (a palace tower's door chose the side of the
hall in one run and the court in the next: 0 versus 123 unreached furnished cells). Fixed in my lane with
`kit/noble/Tower.ordered(Collection<Direction>)` (front, right, back, left) and an `EnumSet` in `Tower.all()`.

`grep -rln "Set.of(Dirs\.\|HashSet<Direction>"` lists about 20 more files in other kits. Each place that iterates
such a set should go through a fixed order (an `EnumSet`, or `Dirs.HORIZONTAL` filtered). L0: a determinism test that
draws an archetype in two JVMs (or with two different `Set` iteration orders) would catch it.

## 2. Pagoda no longer suits wisteria (plan lane)

`landmark/pagoda` is karst only now (its wisteria form was the bell tower again; audit L5-5). I matched
`plan/catalogue/DefaultCatalogue` (one line). `plan/model/Signature.GARDEN_TOWN` still lists `PAGODA` first; the
planner already falls through to the next allowed landmark (the cathedral), but its comment and list could drop the
pagoda.

## 3. Dark sheet instead of copper on timber-roofed towns (civic, T7 domes)

`kit/landmark/Stone.sheet` now gives dark slate (deepslate tiles) where the roof is timber or thatch, where it used to
give patinated copper; `Crowns.Mats.coppered` gives copper only on the maritime coast and in the alpine mountains.
Civic domes and spires that take `Stone.sheet` (observatory, library, bathhouse, town hall turrets) change with it, as
review 2 item 10 asks ("a lead or dark sheet"). Check the civic sheets after merging.

## 4. Audit tool: list every cell of a finding (L0)

While working I used a local, uncommitted patch to `Measure.at`: with `-Daudit.all=<key>` (for example
`fall_edge`) it appends every cell of that finding to the `where` column, not only the first. It made the fall edges
of the palace (four different causes) quick to find. Worth adding properly.

## 5. Crenels you can climb (L0, L1)

The audit's walker steps up one block, so a crenel whose sill is the parapet's one-block course is a way out over the
parapet onto the drip course or a sheer drop. In `kit/noble` every crenel now has a sill (a wall block of the walling
under the opening), and `kit/landscape/Towers.battlements` a slab sill. Other kits with merlons (civic, lawhouse,
homestead parapets) may want the same.

## 6. Bell tower gallery (T8, `kit/civic/Bohemian.gallery`)

The temperate bell tower's gallery and belfry are drawn by `kit/civic/Bohemian`; the fall edges left on the bell
tower are there (see the final numbers in my report).

## 7. `overhang_mean` misses eaves when a roof course reads as wall (L0)

`Measure.detail` takes the wall zone as every row with 40 % of the side at the plane, not the rows contiguous from
the ground; a hip whose second course is full blocks at the plane makes that row "wall", and the eave row under it
is then inside the zone and never measured. The Bohemian gate tower has a block of eave on all four sides (see a
preview) and measures 0. Gatehouse `overhang_mean` stays 0.14 for that reason (target 0.25). A zone grown upward from
its lowest row until the first row that fails would measure it.

## 8. The keep's walk: the folk pathfinder and the spiral stair (L0, NPC lane)

`-Dhearth.tour=walk -Dhearth.walk.only=keep` (four cultures): the player model reaches every room and POI and the
driven player completes every path (25/25, 13/13, 23/23, 10/10, nothing stuck), but the folk pathfinder stops partway
up the keep's slab spiral (`Mason.spiral`) to the steward's room four storeys up, and short of two or three of the
range's upper beds: verdict FAIL in all four. The same spiral serves every tower in the noble kit, so this wants a look
at what the villager navigator makes of half-block spiral treads before the kit swaps them.

## 9. Castle and keep: the court side of a curtain walk (L0, gate)

`fall_edge_cells` on castle (84/84) and keep (84/84) are nearly all the court edge of curtain walks, as on a town
wall, where BUILDING_GUIDE §8 allows them. The palace (where L5's check asks for 0) has its parados; the castles keep
open court edges as real wall walks are. The gate should treat a noble curtain's court edge as it does a town wall's,
or say so on the family page (noble.md now says it).


## 10. The access pass leaves two planks floating in a palace gate tower (access lane)

`NobleTest.everyVariantHolds(palace)` fails on two temperate variants (the palace is never built in a temperate town,
but every variant is tested): "2 blocks float, spruce_planks" in the gate tower's hall floor. The drawn draft has no
loose block (`Mason.loose` is empty after `Mason.settle`); the compiled blueprint has the floor cut in a chequer, so a
pass that runs at compile (`kit/refine/Access`: `Stairs.finish` clearing head room, or `Lofts`/`Guards`) takes cells
out of the floor and leaves a pair standing alone. One of the two (variant 36) fails on this branch from before L5's
changes in this session too. A `Mason.dropLoose`-like sweep at the end of the pass (or not cutting a floor the stair
does not pass under) would settle it.

## 11. `civic/cemetery` fails its invariants on this branch (civic lane)

`SocialLifeTest` and `BlueprintInvariantsTest`: the cemetery's priest job site cannot be walked to from a door (steppe
thatched, northern slope and steep). Nothing in L5's change set reaches the cemetery's kits (`kit/civic/Towers` bell
wall and cote); reported here because the full build on this branch fails on it.
