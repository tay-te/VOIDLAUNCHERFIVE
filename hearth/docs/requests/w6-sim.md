# Requests from the sim and economy lane (wave 6)

What the sim changed is in `docs/SIM.md` §2, §4, §6 and §18. These belong to other lanes.

## 1. Plan: count a trade's places from the real job sites, not an estimate

**Seen**: the sim now gives a plot job sites only for the trades its archetype lists (`PlotFacts.listed`), so a
tenement's workshop bench no longer hires carpenters. But `Needs.jobSlots(info, spec)` still *estimates* a workplace's
places (2 under 100 m², 3 under 200, 4 above; farms area / 120), while the blueprints mark more: a carpentry 9 × 10 has
3 carpenter sites (estimate 2), a smithy 4 (estimate 3), a glassworks 4, a field 16 × 16 five farmers (estimate 2), a
salt works 6 salters. With idle adults about, the sim fills every real site, `w > s` holds, and `job/<trade>` asks for
another workplace, which adds more real sites than estimated places: the same runaway as the 69 carpentries, slower.

**Asked**: for built plots, read the sim's count. `SimPlot` already has `public int jobSlots(JobType)`; a default method
on the api's `PlotView` (`default int jobSlots(JobType job) { return -1; }`, -1 meaning "not known") would let
`Needs.count` use it and fall back to the estimate for planned plots. Or stop `job/<trade>` at the program's count
(`1 + pop / 120` for a carpentry), as requests/w5-tour.md §2 suggested.

## 2. Build: early stages ask for goods a hamlet cannot make

**Seen** (`RealGrowthTest.printCatalogue`, temperate, middling sizes): a camp's cottage bills dye, cloth, candles and a
little glass and iron; a croft books (2) and grain; a hut dye. One unit per block is right, and the sim now copes (barter
of plenty, idle builders fetching raw goods), but the stalls a young town still has are almost all on these finishing
goods (iron, candles, cloth, dye). Not urgent: if the rough finish (`tier` 0–1) of camp and hamlet buildings kept to
timber, rough stone, clay, thatch and wool, with a lantern or two at most, a hamlet would read as hand-built from what is
round it and stall less.
