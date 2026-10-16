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

See `docs/review/systems/npc.md` §4 for the walkthrough's folk path results after the npc fixes (the audit's own
HIGH list in `docs/review/audit/findings/walkthrough.md` counted many beds folk do reach, because it asked for a path
to the bed block itself). The rows still failing there are building geometry for the archetype lanes.
