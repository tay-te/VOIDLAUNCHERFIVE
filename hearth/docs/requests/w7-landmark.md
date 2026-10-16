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
