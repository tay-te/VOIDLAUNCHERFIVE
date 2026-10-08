# Requests from the gate lane (wave 7, L0 and T12)

The gate is in: six play rules run inside `BlueprintChecks.all` (`build/check/PlayChecks.java`), reported while
`Gate.DEFAULT` is `REPORT`; `structureAudit -Pmode=gate` judges every archetype PASS or FAIL against BUILDING_GUIDE.md
§8; `townPreview` keeps review 2's floating-piece probe; the walkthrough takes `-Dhearth.walk.types=` and ends with a
folk-reach summary. See BUILDING_GUIDE.md §8 ("The audit gate", "The gate in the tests") and AUDIT.md §8.

## 1. Coordinator: the switch at wave exit

1. Merge the fix lanes; each has deleted its fixed entries from `hearth/gate/known/<category>.txt`.
2. `./gradlew test -Phearth.gate=hard` (in a slot). Every failure is a `gate <rule>: ...` line on an archetype not
   listed for that rule: a regression of a lane, or an entry a lane deleted too early. Fix or restore the line.
3. Set `Gate.DEFAULT = Mode.HARD` in `hearth/src/main/java/dev/voidmc/hearth/build/check/Gate.java` and run the full
   build. From then on the lists only shrink.
4. `structureAudit -Pmode=gate` for the wave's table (`build/audit/gate.md`); it exits 1 while any archetype fails a
   hard column, known or not.

## 2. Every fix lane (L1-L7, T-lanes)

- Delete your archetype's rule from `hearth/gate/known/<category>.txt` when the fix lands; delete the line when none is
  left. `structureAudit -Pmode=gate -Parchetype=<yours>` shows what still breaks (column `gate_text` in `metrics.csv`
  names the first place, in the local x/y/z of `where`); `build/reports/gate/gate-*.tsv` after a test run lists entries
  no longer seen as stale.
- Try the hard gate on your own archetypes early: `./gradlew test --tests '*<YourFamily>Test*' -Phearth.gate=hard`.
- Do not add lines; ask the coordinator if a rule is wrong for your archetype (a smoke hole or a light well a page
  names: tell L0, or list the column in `hearth/gate/allow.txt` for the audit gate).

Findings the rules surfaced that §4 of the audit did not list (for the owning lanes):

| lane | archetype | rule | what |
|---|---|---|---|
| L3 | work/carpentry | walk | tropical seed 2007: an entrance registered inside a bamboo block (`0,64,2`), never walkable |
| L2 | house/farmhouse | walk | temperate seed 2007: a back entrance (`-3,64,0`) not reachable from the street |
| L6 | deep/dwelling | walk | 30 cases: an entrance above the front stair cannot be walked to |
| L4a | civic/playhouse | floating-piece | the tiring-house roof (83-103 blocks) rests on nothing but a lamp hanging from it |
| L4b | landscape/park | floating-piece | mediterranean: a two-log trunk stood on air |
| L6 | extract/plantation | floating-piece | northern: a lone plank |
| L5 | landmark/pagoda | floating-piece | karst: the lightning-rod finial at y 30 rests on nothing |
| L4b | landscape/festival_ground | floating-piece | on town land (alpine hills d300, plot 132): 12 pieces, 295 blocks of trees, benches and stairs over the slope (`TownLandGateTest`, `townPreview` `_floating.txt`) |
| L4a | civic/tilt_yard | floating-piece | on town land (mediterranean, mesa hills): fence posts and a campfire over the slope |
| L6 | farm/orchard | floating-piece | on town land: single cobblestone and plank blocks (the picking ladder's foot) over the slope |
| L4b | commerce/market_stall | furnished-reached | on town land (mediterranean): 13 furnished cells the walk never reaches (the merchant's spot, M7) |

## 3. T11 (sites and smoke): site dressing that the preview can draw

`townPreview` draws each plot's stages as far as the plot is built, as the game places them. Review 2 item 8 asks for
scaffolding, a hurdle fence and stacked materials round a site. If they become blueprint steps (a stage the builder
removes, or a dressing the sim places and the town writer replaces), `TownScene` draws them with no change. If they
are placed by the sim outside the blueprint, expose them as a function of (spec, stage, step) in `build/` so
`gametest/render/TownScene` can call it; tell L0 and the preview will draw it.

## 4. Left for a later gate pass

- Expanse vegetation in `townPreview` (review 2 item 30): Expanse's foliage is world-generation features that need a
  level, and the offline renderer has none, so `townPreview` draws a stand-in (`gametest/render/TownCountry`: copses
  and meadow, spruce, scrub, savanna, jungle, swamp, blossom by culture or the Earth model's biome) on the open land.
  Expanse lane: if the habitats or densities drift from what Expanse grows, say so here and the stand-in follows.
- `BlueprintPreview` (gametest root, not this lane's) prints `CHECK` lines from `BlueprintChecks.all`; in report mode
  the play rules are not among them. Printing `PlayChecks.evaluate(bp, probe)` as `GATE ...` lines there would show
  them while drawing. One line; whoever next edits BlueprintPreview.
