# Requests from the npc systems lane (wave 7, systems track)

What the lane changed is in `docs/review/systems/npc.md` and `docs/FOLK.md` (§1 movement, §5 the brain, §7 the
conversation). Outside the lane: one line in `src/gametest/resources/fabric.mod.json` (the `FolkNight` tour) and the
walkthrough audit's folk path check (`gametest/walk/Audit.java`), which now asks for a path the way folk do (a spot
beside the bed, a wider search for another storey) so its "folk cannot reach" lines mean what a folk meets in play.

## 1. Social: the crier never gives up on the notice board, and cries into the night

**Seen** (`FolkNight`, Sigskog, northern city of 193): at 23:06 and again at midnight the reeve, Tarjei Solberg, was
held by `hearth:social/crier` (priority 95, over the routine's sleep at 90), standing at the end of a partial path 93
blocks from the notice board, where he had stood since the evening. Every other folk was in bed.

**Why** (read from `social/folk/CrierBehaviour`, not changed here):

- `tick` gives up on the walk when `t > st.nextMove + 1200`, but `st.nextMove` is moved on by 80 ticks every time it
  asks for the path again, so the deadline is never reached: a crier who cannot get to the board walks for ever.
- `wants` checks the hour (7:00-20:00) only before starting; a crier who set out at 19:06 is still at it at midnight.

**Asked**: a fixed deadline from the start (`st.startedAt + 1200`), and `tick` returning false after 21:00 (or when
the routine's sleep comes, `FolkHandle.awake()` aside). The npc side now helps: `FolkHandle.moveTo` searches as far
as the distance needs (up to 128 blocks) and moves a folk that has stood still ten seconds short of the same goal
when nobody sees it, so the crier should reach the board; the deadline is still the crier's to keep.

## 2. Plan/sim: an innkeeper housed in a charcoal kiln 155 blocks from the inn

**Seen** (same run): Vidar Nordby, innkeeper, has `home = work/charcoal_kiln #153`, a bed 155 blocks from the inn
where he works till late. Harmless to the folk (they walk it), but a long way home; if a kiln's bunk is meant for
its burner, housing other trades there may want a look (`plan`/`sim` housing). Not traced further.

## 3. Build lanes: what the folk still cannot reach

After the npc fixes the walkthrough's folk check (part A, eleven archetypes, four cultures) reaches 391 of 399 beds,
job sites and stores from the street (the audit had 323 of 418). The eight left are mine work faces, reached from the
cell beside them. The remaining HIGH rows of that run are for the archetype lanes and were already in the audit: the
keep's wall-walk turrets (guard posts) unreached; the northern and desert bakery's oven nook (y+1, a raised floor with
no step); the desert townhouse's bed alcove (a one-block step up a folk takes but a player must jump). See
`docs/review/systems/npc.md` §4.

## 4. Sim/plan: residents of a grown city with no home

**Seen** (`FolkNight`, second run, Sigskog of 225, every plot built): the lord (Yngve Nes), a merchant and a builder had
`home = NO_PLOT` at midnight; the lord's keep is his workplace only. They walked to the settlement's centre to "sleep"
and stood there. The npc side now sends someone with no home to doze on an inn bench (or rests them at the centre
when there is no inn), but a city whose plots are all built should have housed its lord (the keep's or a manor's
beds) and its people: worth a look in the sim's housing assignment.

## 5. Build: civic/cemetery fails the walkability invariant (full build red, not from this lane)

The full build on this branch (cut from `hearth` at dc19d9ad; this lane changed nothing under `build/` or `plan/`)
ran 2 238 tests with 4 failures, all `civic/cemetery`: `BlueprintInvariantsTest.everyVariantHoldsTheInvariants` and
three `SocialLifeTest` cases (flat steppe seed 41462 west; northern seed 28140 west on slope and steep, 25×13). Each
time one `JOB_SITE` (the priest's, at x -2, the plot's west edge) cannot be walked to from a door. For the civic /
social-life lane that owns the cemetery.
