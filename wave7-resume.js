export const meta = {
  name: 'hearth-wave7-resume',
  description: 'Hearth wave 7: fix the structure audit (playability blockers, access kit, rails, culture plans) and art review 2 (town form, skyline, fields, palettes), plus planner systems from the live tour',
  phases: [{ title: 'Batch 1' }, { title: 'Batch 2' }],
}

const ROOT = 'C:/Users/bitto/VOID/.claude/worktrees'
const SCRATCH = 'C:/Users/bitto/AppData/Local/Temp/claude/C--Users-bitto-VOID/564c6839-160a-4660-85ef-00c49dcb634f/scratchpad/w7'

function pre(lane, owns) {
  const wt = `${ROOT}/hearth-w7-${lane}`
  return `YOU ARE RESUMING. An earlier agent with this same task worked in this worktree for hours and was cut off by a usage
limit. Before anything else read what it did: \`git log --stat hearth..HEAD\`, \`git status\`, \`git diff\` (and untracked
files), and any requests/notes it wrote. Never discard that work (no checkout/restore/reset of its files): judge it, fix what
is broken, commit the good uncommitted work early in logical commits, and finish the task from where it stopped. Do not
redo finished parts. Branch \`hearth\` has moved on (wave 7 styles lane merged: style JSONs); if you need it, \`git merge
hearth\` into your branch after committing your work (resolve conflicts keeping both sides' intent).

You are one agent of the VOID Hearth wave 7 swarm (resumed in two batches of ~6 agents, one git worktree each; the coordinator merges).
Hearth: hearth/ (package dev.voidmc.hearth), living settlements for Minecraft 26.3 Fabric, Java 25, unobfuscated Mojang names.

YOUR WORKTREE: ${wt} (branch hearth-w7-${lane}, cut from \`hearth\`). Work and commit only there.
YOU OWN: ${owns}
Edit outside what you own only when unavoidable: keep it tiny and list it in your report. For anything another lane owns,
write a request in hearth/docs/requests/w7-${lane}.md.

THE BRIEF: two reports drive this wave; read the parts for your lane first, carefully:
- hearth/docs/review/AUDIT.md (the structure audit: section 3 grades, section 4 blockers, section 5 systemic causes, section 7 fix plan with lanes,
  items and AUDIT-VERIFIABLE acceptance checks; section 8 re-running) and its findings in hearth/docs/review/audit/.
- hearth/docs/review/ART_REVIEW_2.md (whole towns: section 3 towns, section 4 families, section 8 top 30 grouped in T-lanes) with images in
  hearth/docs/review/img2/.
Also BUILDING_GUIDE.md (section 8 checklist with the audit thresholds, section 9 previews incl. townPreview), the family pages
docs/buildings/**, the style bible docs/buildings/styles/. The audit tool: \`./gradlew structureAudit\` (see AUDIT.md section 8 for
arguments, e.g. limiting to archetypes/cultures, and -Pmode=gate once the gate lane adds it); the in-game walkthrough tour
(-Dhearth.tour=walk) exists too.
The user's bar: every structure high quality, hand-built by a skilled player, detailed, and very playable (players and folk
reach every room, bed, workstation; climbable stairs; rails on drops; well lit; nothing floating, blocked or cramped).
Natural real-world surface; temperate reads as KCD II Bohemia; underground may be fantastical.

Rules (hard):
- Never touch C:/Users/bitto/VOID itself or any other worktree. Never git stash, checkout another branch, reset, rebase or
  push. Commit small logical steps; messages end "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>".
- Build from ${wt}/hearth with JAVA_HOME=C:/Users/bitto/VOID/.tools/jdk-25. Compiling is free. Tests, previews, the audit
  run and the full build go through the machine slot script: bash C:/Users/bitto/VOID/.tools/locks/slot.sh ./gradlew <task>
  (13 agents share one 32 GB machine; slots may queue).
- RUN COMMANDS IN THE FOREGROUND with the Bash timeout at 600000 ms. For anything longer (the full build takes ~45 min) start
  it in the background writing to a log file and poll the log with foreground commands until it ends. NEVER finish your reply
  while something you started is still running: you are done only when your work is verified and committed.
- NEVER run "gradlew --stop" or kill Java/Gradle processes: that kills every other agent's builds. To clean up, just delete
  your own hearth/build when done.
- A game instance needs a game slot: mkdir C:/Users/bitto/VOID/.tools/locks/game1 or game2 (atomic); if both exist, wait
  and retry; never remove a lock you did not create; rmdir yours when done; never accept a server EULA.
- Disk is tight (~22 GB free, shared with other sessions): images as JPEG/WebP into ${SCRATCH}/${lane}/ (under 100 MB);
  delete ${wt}/hearth/build when you are finished and committed.
- Work in the existing idiom (comment density, naming, kits). Measure before and after with the audit tool on your
  archetypes and meet the acceptance checks in AUDIT.md section 7 for your lane; look at before/after renders; run the full build
  once at the end (must pass). Update the docs your change affects.

When done, reply (under 300 words): commits (hash + line), audit numbers before/after for your acceptance checks, what you
looked at, 2-4 best image paths, what is left or requested of others.

YOUR TASK:
`
}

const LANES = [
  { lane: 'gate', effort: 'high', owns: 'build/check/**, src/gametest/**/gametest/audit/**, src/gametest/**/gametest/walk/**, src/gametest/java/dev/voidmc/hearth/gametest/render/**',
    task: `AUDIT.md lane L0 (Gate and checks) plus ART_REVIEW_2 T12 (item 30). Make the checks catch what the audit found:
BlueprintChecks walks like the light check (no standing on pots, candles, lanterns, cakes, skulls); the new rules (every
door has two standable sides; every ladder top has a step-off cell; every furnished room reached by the light walk; no
floating piece; no non-walkable roof hole); \`structureAudit -Pmode=gate\` with PASS/FAIL per archetype against section 8's
thresholds; the walkthrough tour's -Dhearth.walk.types= and folk-reach summary; the metric false positives listed. IMPORTANT
for a parallel wave: other lanes are fixing the archetypes right now, so add the new hard rules as checks that REPORT on
today's code (with a list of known failures per archetype in a file other lanes can shrink) rather than failing the whole
test suite, and make the full build pass; leave a single switch the coordinator flips at wave exit to make them hard.
Document the gate in BUILDING_GUIDE.md section 8 and AUDIT.md section 8.` },
  { lane: 'access', effort: 'high', owns: 'build/kit/{Openings,Porches,Stairs,Rooms,Furniture,Floors,Roofs}.java, new build/kit/Doors.java and build/kit/Guards.java, build/kit/civic/Storeys.java, build/kit/craft/Lofts.java, build/kit/refine/**, build/kit/trade/{Hall,Floorplan}.java, build/kit/light/**',
    task: `AUDIT.md lane L1 (Access kit), items 1-8 in order: Doors.ok/place keeping both door fronts; balconies two deep with
rails; Hall.terrace stairs/hatch to the deck; flights with a landing at every turn, two air over every tread and a guard round
the stairwell so folk can climb; Lofts.ladder with a landing cell and attics opened or sealed; Guards.edge and railed flat
decks; Rooms.furnish keeping paths and faces clear; the auto-lighter covering ladder-only lofts and aisles. This lane is the
foundation the homes/work/civic/commerce/landmark lanes build on in parallel: keep APIs stable or additive, land the new kit
pieces (Doors, Guards) EARLY in small commits and describe their API in hearth/docs/requests/w7-access.md (the coordinator
will tell the other lanes), and make existing callers benefit automatically where safe. Meet L1's acceptance checks.` },
  { lane: 'sites', effort: 'medium', owns: 'build/kit/Chimneys.java, build/blueprint/** (stage assembly), build/kit/lawhouse/Scaffold.java',
    task: `ART_REVIEW_2 lane T11 (items 8 and 18): construction sites must read as building sites, not ruins (staged walls with
scaffold poles, a materials pile, a cleared and fenced plot; read how stages are assembled and what an unfinished stage shows),
and chimney smoke: the live tour saw campfires burning on roofs as chimneys (Bradfield market street) - chimneys must read as
chimneys (a capped stack with a hidden smoke source, campfire inside a stack with a cowl/slab, smoke only, no visible flames
on roofs), in every culture. Note that the npc lane (hearth-w7-npc) changes how embodied builders put up scaffolding in
parallel: coordinate through a requests file rather than editing npc/**. Check with blueprintPreview stage renders and
townPreview close-ups.` },
  { lane: 'homes', effort: 'high', owns: 'build/archetype/house/**, build/kit/townhome/**, build/kit/homestead/** except Trees.java, build/kit/station/**',
    task: `AUDIT.md lane L2 (Homes) items 1-10 plus ART_REVIEW_2 lane T5 (items 7, 9, 22, 25, 29: no battlements or hollow cubes
on dry-culture homes, two-storey town homes with windows on detached side walls, per-culture town-home plans, etc.).
Playability first (doors, folk-climbable stairs and landings, croft loft, parapets on roof decks, small homes with a clear walk,
garrets or sealed attics, storey heights), then looks. The access lane is adding kit/Doors and kit/Guards in parallel: use
them once they land (read hearth/docs/requests/w7-access.md in that worktree, ${ROOT}/hearth-w7-access, read-only, or
\`git log hearth-w7-access\`), or write home-local fixes that switch to them later. Meet L2's acceptance checks (every house/*
hard 0 blocker columns etc.).` },
  { lane: 'work', effort: 'high', owns: 'build/archetype/work/**, build/archetype/storage/**, build/kit/craft/** except Lofts.java, build/kit/trade/** except Hall.java/Floorplan.java, build/kit/industry/**',
    task: `AUDIT.md lane L3 (Work and storage) items 1-8, plus from ART_REVIEW_2: T8's alchemist tower, T9's dye works (item 27/28
parts) and T10's brick kiln and Furnace (item 17/30b parts) - read those items. Windmill floors and sails; job sites usable;
lofts, ladders and doors; a stocked warehouse; rails on galleries; clean steep roofs; props; per-culture plans for the
buildings the similarity check flags. Meet L3's acceptance checks.` },
  { lane: 'civic', effort: 'high', owns: 'build/archetype/civic/** (including TiltYard), build/archetype/law/**, build/kit/civic/** except Storeys.java, build/kit/learning/**, build/kit/lawhouse/** except Scaffold.java',
    task: `AUDIT.md lane L4a (Civic and law) items 1-10, plus ART_REVIEW_2 lanes T7 for civic crowns and domes (kit/civic/Towers,
Adobe; Observatory, Library, Bathhouse, University - items 10-12), T8 for the civic shells (Guardhouse, TownHall, Chapel*,
Almshouse, Orphanage, Infirmary, School; kit/civic Bohemian, Halls, Sacred, Portico, Arches - items 13, 14, 20, 26), T9's
tilt yard, T10's law per culture. Includes the mesa PUEBLO tradition, smooth domes on drums, furnished chambers, a courthouse
distinct from the town hall. Meet L4a's acceptance checks.` },
  { lane: 'commerce', effort: 'high', owns: 'build/archetype/commerce/**, build/archetype/landscape/**, build/kit/sociallife/**, build/kit/landscape/** except Towers.java',
    task: `AUDIT.md lane L4b (Commerce and open spaces) items 1-4, plus ART_REVIEW_2: T7's market hall dome, T8's commerce shells
(Inn, Hostel, Tavern, Bank, kit/sociallife/Shell), T9's market stall, fair and festival ground (items 21, 27, 28). Market hall
stair/doors/store, bank, caravanserai, counting house, inn, tavern, stalls, cellars, plaza, park, statues, trees in
kit/landscape (Trees.conifer), market stall and bell per culture. Meet L4b's acceptance checks.` },
  { lane: 'landmark', effort: 'high', owns: 'build/archetype/landmark/**, build/archetype/noble/**, build/archetype/defense/**, build/kit/landmark/**, build/kit/noble/**, build/kit/landscape/Towers.java',
    task: `AUDIT.md lane L5 (Landmark, noble, defence) items 1-6, plus ART_REVIEW_2: T7's landmark crowns (kit/landmark, BellTower,
ClockTower, GreatHall - items 10-12) and T10's walls and gatehouses per culture (item 17). The castle was fixed and made
Bohemian on hearth after the audit ran: re-audit castle and keep first. Gate ladders and hoards, rails on every high deck,
reach fixes, a keep bailey, culture misfits, per-culture stables/gatehouse/hunting lodge/watchtower. Meet L5's acceptance checks.` },
  { lane: 'land', effort: 'high', owns: 'build/archetype/{farm,infra,extract,deep}/**, build/kit/{infra,extraction,deep}/**, build/kit/homestead/Trees.java',
    task: `AUDIT.md lane L6 (Land, extraction, deep, infra) items 1-7, plus ART_REVIEW_2: T3's farm buildings side (the belt of
fields, orchards and paddocks around towns: make the farm archetypes ready to ring a town - the planner side is the town lane)
and T4's walkways that float (item 5) and infra item 16. Deep hall access, the mine (folk reach every face; a working mine is
also needed by the npc lane's diggers), terrace field and paddy job sites, rails, irregular tree crowns, dock and walkway
supports, deep reskins. Meet L6's acceptance checks.` },
  { lane: 'styles', effort: 'medium', owns: 'src/main/resources/data/hearth/hearth_style/*.json, src/main/resources/data/hearth/**/region/*.json, build/style/**, docs/buildings/styles/**',
    task: `AUDIT.md lane L7 (Styles) items 1-4 plus ART_REVIEW_2 lane T6 (items 6, 15, 23: patchy walls in the dry cultures, etc.)
and the tour note that cottage walls use calcite plus mushroom stem. Tropical walls, dry-culture mottling under the review-1
rule, working-yard timber species, gentry dressing for noble archetypes. Meet L7's acceptance checks and re-render contact
sheets for tropical, mesa, desert, steppe.` },
  { lane: 'town', effort: 'high', owns: 'plan/layout/**, plan/growth/{Housing,Lots,Parcels,Square,Expand,Grid,Stacks,Landmarks,Siting,Beauty,Farms}.java, plan/road/**',
    task: `ART_REVIEW_2 lanes T1 (town form: items 1, 3, 24), T2 (skyline and street furniture: items 2, 19), T3 (edges and fields:
item 4, the planner side) and T4 (roads: the plan/road side of items 5, 16). Turn the sprawling "farmsteads" towns into real
towns with a square or market street at the heart; continuous street rows (terraces of town homes sharing party walls)
instead of detached houses on grass in the core; towers on the square and on hilltops, not at the edges; a belt of fields,
orchards and paddocks so towns end in countryside, not bare grass. The walled temperate town and the alpine mountain town are
the models (img2/). Keep planner determinism and cost (live calls ~2-5 ms now; the planner lane works on plan/growth's other
files and the planner core in parallel: coordinate via requests). Check with townPreview in 5+ cultures before/after at day
200-400 and keep PlanBenchTest numbers.` },
  { lane: 'planner', effort: 'high', owns: 'plan/growth/{Growth,Needs,Program,Additions,Upgrades,Build,Docks,Gates,Walls}.java, plan/resource/**, plan/society/**, plan/catalogue/**, plan/HearthPlanner.java, plan/PlanStats.java, plan/PlanConfig.java, sim/** (not npc/**)',
    task: `Planner and sim systems from the live tour (docs/requests/w7-tour.md, docs/SIM.md section 17.1) and the open requests
(docs/requests/w6-plan.md, w6-sim.md, expanse.md's 2026-10-07 section):
1. Live planner calls that run out of time still cost 10-95 ms against live_time_limit_ms 10 and growth is every minute's
   worst step: re-ask on the next tick after an out-of-time answer, memoise the needs list between days, SimSettlement.beds()
   recompute inside the call (7.6 %), and bound the whole call, not only the frontage search.
2. Excavations stay closed once cut out (one open across 71 settlements live; workings "built out", miners do abstract work):
   make workings reopen/extend (new faces, deeper levels, next quarry bench) as long as the site has ore/stone, so diggers work
   at faces (the npc lane fixes the diggers' behaviour in parallel).
3. Four camps never grow ("no road could grow... the main streets built out" for camps with no streets).
4. Patron towns average 141-148 souls, Thornley sits at 95 under "every need resting": check that resting does not stop growth
   that land and food would allow.
5. Expanse's taller world (top 639, bigger relief) and its Column.site flags: read SITE_HILLTOP and SITE_RIM through
   sim/terrain/ExpanseProbe and let castle/keep siting (plan/society/Nobility) prefer them; check road grades and territory on
   the new relief (merge state of hearth already has Expanse 4b138901).
6. /hearth sim stats prints PlanStats.lines(); review the SimWorld edit from wave 6.
Measure with PlanBenchTest/PlanSimBenchTest and RealGrowthTest; if you run LiveTown, take a game slot.` },
  { lane: 'npc', effort: 'high', owns: 'hearth/src/main/java/dev/voidmc/hearth/npc/** (builder/digger behaviours, scaffolding, embodiment) and its tests/docs (docs/FOLK.md, the construction section of docs/ARCHITECTURE.md)',
    task: `Make the embodied builders work like real builders (BuilderTour: -Dhearth.tour=build). What the live tour found (docs/requests/w7-tour.md, docs/SIM.md section 17.1, BuilderTour with -Dhearth.tour=build; earlier
shots in the scratchpad folders buildshots/ and w7tour/ under
C:/Users/bitto/AppData/Local/Temp/claude/C--Users-bitto-VOID/564c6839-160a-4660-85ef-00c49dcb634f/scratchpad/):
1. Builders spend ~89 % of their time on scaffolding: one scaffold tower reaches only a few blocks of wall, so they climb
   down, dismantle, re-erect. Make it like a real building site: scaffolding (poles + boards/scaffold blocks) goes up along
   a wall face or round the building for the whole stage (or campaign) and comes down when the stage's walls/roof are
   done; builders work from it, placing EVERY pending step within reach (vanilla-like reach ~4.5 blocks) before moving
   along; they move along the scaffold, not up and down from the ground each time. Steps must still be placed in the
   blueprint's order constraints (supports before what rests on them) and exactly as \`(plot.stage(), plot.step())\` +
   advanceConstruction expect; if strict order forbids batching, add a way to place the next N steps that are within
   reach and independent, keeping the sim's single progress counter consistent (read how advanceConstruction and the
   applier treat steps; do not break chunk-load replay).
2. Too many builders on one site stall it (21 on a 7x7 cottage: nobody placed a block). Cap builders per site by the
   site's size/working faces (e.g. 2-3 on a cottage, more on a hall or castle), assign the rest to other sites or to
   fetching materials; never let them block each other on the scaffold.
3. Only one excavation was open across 71 settlements live, so no digger was ever at a face: check the digger behaviour
   works when an excavation plot is open (quarry pit, mine adit/shaft, cellar), and if excavations stay closed because of
   the sim/plan (the tour's report says workings are "built out" and miners do abstract work), describe the cause and the
   fix in docs/requests/w7-npc.md for the sim/plan lane (do not fix plan/sim yourself beyond a tiny change).
4. Keep it cheap: the tour measured Folk at 32-50 us per tick (up from 22-27); do not make it worse, improve if you can.
Verify in game with BuilderTour: measure time placing vs on scaffolding vs walking (target: placing a majority of the
time once the scaffold is up), how long a cottage takes, and take 3-4 good JPEG screenshots (scaffold up along a wall with
2-3 builders placing; the house finishing; scaffold coming down) into the scratchpad folder w7npc/. Check each image.
Unit tests for the step batching and the per-site cap. Full build must pass.
` },
]

const run = (names, ph) => parallel(LANES.filter(l => names.includes(l.lane)).map(l => async () => ({
  lane: l.lane,
  report: await agent(pre(l.lane, l.owns) + l.task, { label: `w7: ${l.lane}`, phase: ph, model: 'opus', effort: l.effort }),
})))
phase('Batch 1')
const b1 = await run(['access', 'npc', 'civic', 'commerce'], 'Batch 1')
phase('Batch 2')
const b2 = await run(['gate', 'homes', 'work', 'town', 'sites', 'landmark'], 'Batch 2')
return [...b1, ...b2]
