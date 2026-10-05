# Requests from the clergy and learning family (stage B)

What the learning architect needs from other owners, and what the family's buildings offer them. See
`docs/buildings/learning.md`.

## To the styles (refine)

1. **Religious substyle weighting for houses of the orders.** Temperate picks between `norman stone` and `bohemian
   church` for the `religious` category by weight, so about a third of Bohemian monasteries and convents come out in
   dark stone bricks under deepslate. For the KCD II look the cloistered houses should lean hard toward `bohemian
   church` (white render, dark oak roofs); the Norman stone reads well on keeps and the cathedral, less on a whole
   abbey. A `when: {archetypes: ["civic/monastery", "civic/convent"]}` weight bump would do it.
2. **A `scholarly` substyle** for `civic/university` in the timber cultures (the university currently takes the
   village substyle; a burgher-town render with dressed stone like `bohemian town` would suit a city college better).

## To the planner

3. **Height for the abbey church.** Monasteries are asked 12–22. With two-storey ranges (ridge about 11) the church can
   tower with a steep roof, but a turret, a west tower or a cote on the church's own gable needs 24–28. If the planner
   can offer a monastery up to 26 on its quiet ground, the church gets its bell back on its own roof (the archetype
   chooses the best shape for whatever height it is given; no code change needed here beyond the catalogue's
   `maxHeight`, which I would then raise in `Monastery.INFO` to match).
4. **Wide quiet ground.** The walled herb garden appears only on plots 27+ wide (convent) / 28+ wide (monastery); a
   wider parcel shows the whole precinct.

## To the sim and social systems (what the buildings register)

5. **Monastery**: PRIEST at the altar's lectern, SCHOLARs at the scriptorium's lecterns, the BREWER at the brewhouse's
   brewing stand; LECTERN also at the refectory's pulpit (no job); SEATs on the pews, the choir stalls, the chapter
   house's benches and the refectory's benches; TABLEs at the refectory and the desks; MEETING_POINT at the garth's
   well; GATE at the gate passage; 10 BEDs in the dormitory (2 more in the guest chambers on two-storey plots).
6. **Convent**: PRIEST at the chapel's lectern, the HEALER at the infirmary's bench (a brewing stand on a counter),
   SICKBEDs at the infirmary's beds (one in the dormitory where a tiny infirmary has no room), 8 BEDs.
7. **University**: SCHOLARs at the lecture halls' and the library's lecterns, LECTERNs, SEATs in rows, MEETING_POINT at
   the sundial, GATE, 8 BEDs in the scholars' chambers.

## Shared machinery touched (backward compatible)

- `archetype/civic/ChapelBohemian`: `eastEnd`, `interior`, `eastWalls`, `buttress`, `apseLights`, `eastRoof` made
  package-private (were private) so the precinct church builds its apse the same way. No behaviour change.

## Integration (hearth-integrate-4)

- §1 done: temperate's Norman stone leaves monasteries and convents to the Bohemian church manner.
- §2 done: the university (and the scribe's house) take the Bohemian town manner in temperate; no new sub-style.
- §3 done: `Monastery.INFO` and the catalogue offer up to 26.
- §4 open: parcels are sized within the registered range by seed; not forced wide.
- §5–§7 nothing to wire: the sim and social modules use the registered POIs.
