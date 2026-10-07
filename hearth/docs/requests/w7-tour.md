# Requests from the second live tour (wave 7)

The live tour (`gametest/LiveTown`, `-Dhearth.tour=live`, three runs) and the builder tour (`gametest/BuilderTour`,
`-Dhearth.tour=build`) on the wave 6 code, seed 20261003, Expanse Earth: the Bohemian town founded near spawn and grown
400 days with a patron (Bradfield again), 54 more settlements on a 640-block grid aged 30–200 days (every other one
with a patron), a minute abstract, the six largest towns stepped an hour a tick, the arrival, the photographs. The
numbers are in `docs/SIM.md` §17.1, the photographs in the scratchpad's `w7tour/`. What wave 6 fixed: towns no longer
stop at pop = beds (Bradfield 29 → 659–729 souls, stopping at its land's capacity), carpentries are capped (69 → at
most 7 in a city of 729), workings open (in 47–48 of 55 settlements), towns left alone grow (7 → 28–29 souls on
average; temperate ones reach 20 by day 45–60 and the best 60 by day 120–140), and a growing city's `planner.next`
averages 2.7–4.5 ms a call. What is left for other modules:

## 1. Plan: a call that runs out of time costs 10–95 ms, and growth is still every minute's worst step

**Seen**: with `live_time_limit_ms` 10, calls that end "out of time" take 20–61 ms in the abstract minute (2–4 calls,
median 20–33 ms) and up to 95 ms in the hour-step bench (Wavestrand, 460 souls; Herringquay 55 ms). A town whose needs
keep running out of time pays about 10 ms a call, call after call: Anchorcove at 250 souls made 47 calls in 400 hour
steps, median 10.3 ms, every one "program: no frontage, roads at their budget; then noble: out of time". The worst
step of every measured minute is growth: 29–61 ms (abstract), 13–41 ms (embodied), 47–52 ms over the tour (Coral Cay
at 52 souls, Brambrook at 138).

**Asked**: honour the deadline inside the expensive stages too (the overrun past 10 ms is where the 20–95 ms calls
come from); and a need that ran out of time twice in a row could rest a day rather than be retried at full cost (or
carry its search over to the next call).

## 2. Sim: an hour step of a growing city costs 1.5–2.5 ms outside the planner; the abstract minute doubled

**Seen**: hour steps one a tick, planner and blueprints subtracted: Herringquay (589 souls, 1 459 plots) 2.4–2.5 ms
avg, Anchorcove (392–422 souls) 1.7–2.3 ms, Wavestrand (389–460) 1.5–2.0 ms; towns at capacity 0.27–0.79 ms. In w5
the same steps were 0.18–0.34 ms avg (median 0.06–0.09). The abstract minute: sim tick work 0.57–0.79 ms avg, 33–41
ticks over 5 ms (w5: 0.28–0.32 ms, 12–17), while the clock's own steps account for only 71–81 × 0.74–1.67 ms
(about 0.05–0.1 ms a tick). In the same abstract minute 1 721–2 133 blueprints were made and 598–824 handed to the
background, with every town abstract and the player 30 000 blocks away.

**Asked**: a profile of the sim tick with everything abstract (what else runs there: caravans, surveys, the atlas,
something asking for blueprints of abstract towns?) and of `SettlementClock.advance` in a growing city (`addPlot`,
`Prosperity.finish`, derived figures recomputed after each plot: requests/w6-plan.md §5).

## 3. Sim and plan: workings are worked out and nothing reopens them

**Seen**: of 71 settlements, **one** excavation was open (BUILDING) when the tour looked for diggers: Herringport's
quarry pit (a maritime hamlet of 5 souls, `11_quarry_open_herringport.png`). Bradfield's 2 quarry pits, 4 shafts, a
clay pit and a sand pit are all BUILT (`10_quarry_pit_bradfield.png`, `12_shaft_head_bradfield.png`); its 4–8
quarriers and 3–4 miners do "quarrying" by the pit (the old field work: `Extraction.workable` wants BUILDING).
`BuilderTour`'s dig town (Oakham, grown 240 days for a digger) had `{BUILT=558}` and no excavation open. So a city of
700 has no one at a face, and its stone and ore come from the old abstract work.

**Asked**: when a working is cut out and the town still draws on its goods, plan its next bench or level (a deeper
shaft, a wider pit) or a new working at another site; or keep the excavation open while its yield is wanted. A test:
a 300-soul town with an ore site keeps at least one excavation BUILDING over 200 days.

## 4. Plan: camps that never grow ("the main streets built out" with no streets)

**Seen**: Eikholt (northern, 1 plot, 4 souls for 37 days), Safia (desert, 4 plots, 4 souls for 155 days),
Heathergill (moorland, **with a patron**, 6 plots, 5 souls for 153 days), Frostreach (tundra, 5–7 souls). Their
reasons: "housing: no road could grow (no lane mouth, the main streets built out, no dead end to run on)" (Heathergill
23 times; Safia 21 times as "a lane mouth at 18 would not walk"), "food: no frontage, and no road could grow", "lots: no
room", then "every need resting". Hochdorf (alpine, 11 souls from day 40 to 135): "a lane mouth at 61 would not walk",
"program: no track could reach the site".

**Asked**: a camp or hamlet with no streets should not read as "built out": let it lay its first lane (or set houses
straight off the track or the founding fire) when the lane mouth will not walk; a test founding a camp on each of
these sites (their `scale town` lines in the tour log) that reaches a hamlet.

## 5. Plan / sim: patron towns stop well under their land

**Seen**: the 27 patron towns average 141–148 souls (w5: 205). Thornley (temperate, patron, stores and purse topped up
every 20 days) holds 94–95 souls from day 140 to 180 with "every need resting" and "program: no plot fits at the site
(14 places by the track)"; Palesteading (tundra, patron) 32–36 souls from day 100 to 195; Sun Gap (mesa, patron) 13.
Bradfield, by contrast, grows to its capacity (532–535) and past it to 659–729 (beds 670–738).

**Asked**: check what "every need resting" means in a patron town with full stores and idle builders far under its
capacity: which need rests and why (the land's mood, a tier gate, the program's entries all placed?). `/hearth plan
stats` gives only the last reason; a line per town with its capacity and the needs' rest timers would show it.

## 6. NPC: builders spend 89 % of their time on scaffolding

**Seen** (`BuilderTour`, a temperate cottage 11×9, three to six builders, sampled each second for six minutes):
raising scaffolding 883 builder-seconds, taking it down 252, placing 42, fetching 102. The cottage went from 42 % to
73 %. The builders put a tower up, place a few blocks from it, take it down and raise the next one a block or two
along (`23_builder_raising_scaffold.png`, `22_builders_two_on_scaffolds.png`).

**Asked**: one scaffold for a run of wall (a bay or a whole side of a small house), worked course by course with a
reach of 3–4 blocks either side; leave it standing until the wall above it is done; take scaffolding down once per
wall, not per step. A target: placing at least half the time on site for a house.

## 7. NPC: folk cost more per tick

**Seen**: 48 folk embodied: 1.5–2.4 ms per server tick, 32–50 µs a folk tick (64–93 µs while spawning) against
22–27 µs in w5; the embodiment manager 0.82–0.95 ms a tick during the arrival (w5: 0.44–0.53). The arrival's worst
ticks rose to 450–500 ms (w5: 155–267) in a city of 1 125–1 202 plots (w5: 798); the watchdog caught
`EmbodimentManager.placement/consider` among vanilla's chunk saves and entity collisions.

## 8. Build and looks (from the photographs)

- **Palettes** (`BuilderTour` site, a temperate `house/cottage`): the builders place `mushroom stem` and `calcite` for
  the walls (`21_builder_placing_on_scaffold.png`, `24_builders_site_later.png`): a fungus block in a Bohemian
  cottage, and the two together read as stark white cubes, not lime-washed render over a stone plinth.
- **Sand in a temperate town**: Bradfield's west quarter round the cathedral and the slope below the approach have
  open sand and sandstone ground between the houses and in terraced yards (`02_bradfield_approach_cathedral.png`,
  `07_cathedral_sand_ground.png`): it reads as desert, not as Bohemian countryside. Whoever writes it (a terrace's or
  yard's fill, or Expanse's river banks under a plot) should use the local soil and grass.
- **civic/chapel**: better than in w5: a squat stone tower with a dark bulb cap (`06_chapel.png`); the nave roof still
  reads as stepped slabs rather than a pitched roof.
- **The market**: Bradfield's market is a long narrow street with stalls down it (`04_market_street.png`), dense and
  vertical (good), but there is no square for it to open onto; a Bohemian town's market is a broad square with the
  church and the town hall on it.

## Fixed in this pass (tour tooling)

- `LiveTown`: logs every scale town's souls by day, its workings and carpentries, and the workings across the world;
  prints `PlanStats` in each report (and resets it with the other counters); benches the six largest towns an hour
  step **a tick** (the old bench ran 200 steps inside one tick, so the per-tick planner budget allowed one call) with
  the planner calls' median, p90 and max; the approach shot uses the furthest road within 150 blocks (the furthest
  road of a 1 100-plot city ran 400 blocks out to a lake and the camera stood under water); photographs at 1600×900;
  the mining stop prefers an excavation being cut (BUILDING) over a worked-out one, stands by it 30 s so its diggers
  come out, and looks for a digger cutting at the face; the market's low shot stands on the street, not on a stall's
  roof.
- `BuilderTour`: screenshots were written to `build/run/clientGameTest/build/run/clientGameTest/screenshots` (the
  relative default resolved against the run directory); now `build/run/clientGameTest/screenshots`. It samples what
  the site's builders are doing each second of the later wait and prints the time use.
