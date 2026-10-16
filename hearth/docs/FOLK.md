# Hearthfolk — the npc module

The people of Hearth's settlements: who they are, how they look, how they spend their day, how they work (builders
raise the town block by block; miners, quarriers and foresters change the land around it; haulers carry the goods of its
chains), how they talk, how other modules teach them new behaviours (guards and witnesses, repairs, festivals), and how
all of that stays cheap enough for a busy server. Code:
`dev.voidmc.hearth.npc` (server and common), `dev.voidmc.hearth.client.npc` (rendering, skins, conversation screen).

```
npc/
  NpcModule        init: entity, resident factory, dialogue provider, networking, reloaders, events, commands
  NpcConfig        config/hearth/npc.json
  NpcTime          the sim calendar (sim world day, or the overworld clock)
  person/          Trait, NpcData (Resident.extra("npc")), Genome (looks), NameBank, HearthResidentFactory, Office
  look/            FolkLook, SkinComposer (pure: look -> 64x64 ARGB pixels), SkinCanvas, Palette
  entity/          Folk (hearth:folk), FolkPose, FolkNavigation + ClimbNodeEvaluator, FolkSpots, FolkEntities
  place/           SettlementPlaces (POIs per plot), ResidentPlaces (one person's bed, home, work), PoiScanner, BlueprintSource
  routine/         Schedule (pure), Activity, Leisure, FolkBrain, Task, Tasks, Plans, RoutinePlacement
  work/            WorkBehaviour + BuilderWork (with BuildSite, StepPlan, Staging, Walker, ScaffoldLedger), FarmerWork, GuardWork,
                   CraftWork, CounterWork, FieldWork, ChoresWork,
                   Extraction + ExcavationWork (miners, quarriers, diggers), ForesterWork + Trees, HerderWork, BeekeeperWork,
                   FisherWork, DiggerWork, HaulerWork, OfficialWork (reeve, steward, judge, jailer), LordWork, ServiceWork
  service/         NpcServiceImpl (the api's NpcService), FolkHandleImpl, Perception (pure), Behaviours, BehaviourRunner, Escorts
  caravan/         Caravans (journeys from the sim's CARAVAN events), Journey (pure), CaravanParty (the party on the road)
  embody/          EmbodimentManager, EmbodimentSelector (pure), TownState, SettlementSources, StrayHomes
  talk/            Conversations, NpcDialogue, DialogueTemplates, Greetings, Rumours, TownNews, TalkPayloads
  dev/             NpcSandbox, SandboxSettlement, SandboxBlueprints, NpcCommands   (isolated: the sim replaces it)
client/npc/        NpcClient, FolkRenderer, FolkModel, FolkRenderState, FolkSkins, ConversationScreen
mixin/npc/         MobAccessor (monsters target folk), EntityNoSaveMixin (caravan beasts are never saved)
```

## 1. The entity: `hearth:folk`

A player-shaped `PathfinderMob` (not a villager). **The `Resident` record is the person; the entity is a view of
it.** The entity type is `noSave`: folk are never written to chunks. When an entity goes away (out of range, chunk
unload, server stop) its state is written back into the record (`extra("npc")`: last position and time, health,
labour) and the sim is told (`setResidentEmbodied(id, false)`).

Synced data: resident id, full name, appearance genome, culture + job + age stage + sex + station + office (one packed
int: culture bits 0–4, job 5–11, age 12–13, sex 14, station 15–18, office 19–20; `Folk.packLook/unpackLook`), quest
marker, pose (`FolkPose`: sit, hammer, craft, read, pray, wave, talk, tend, cower, cheer). Children are 0.62 scale with
a bigger head. The name and trade (or office: "Guard Captain", "Master Builder") show when the crosshair is on them
(within 20 blocks).

Folk who are not residents (a caravan's traders and guards on the road, §15) are driven by a `Folk.Controller` instead of
a brain; they have no record and right-clicking them gives a line of talk rather than the conversation screen.

Movement: `FolkNavigation` opens and closes doors (vanilla `OpenDoorGoal`), climbs ladders and vines
(`ClimbNodeEvaluator` adds straight up/down nodes on climbable blocks; not scaffolding, whose paths up a site's ring and
over the wall top no folk could follow: builders get about the ring by hand, §6), and sneaks to drop through
scaffolding when the path goes down. Sleeping uses the vanilla pose without touching the bed block (`Folk.sleepIn`), so a folk folded away
in bed never leaves a bed marked occupied. Step height 1. Folk do not use portals (the entity would leave for a world
where no settlement can take it back). A block put where a folk stands (a builder's step, the chunk applier, a player)
does them no harm: they step out to the nearest standing room, since a resident's death is for good. Anyone but a
builder who walks into a site's scaffold column (which carries a mob up it) or stands on the ring keeps low and comes
back down through it: no path leads off the ring.

Paths are asked for a place to **stand** (`FolkSpots.approaches`): the spot itself if one can stand there, else the
standable cells beside it, beside either half of a bed, and under a roof only roofed ones (so a bed against an outside
wall is reached from the room, not from the street through the wall). A path asked for a bed, a chest or an anvil
itself never ends early: it runs through the whole node budget and returns wherever it got nearest, which for an
upstairs bed was the floor beneath it. The search budget follows the distance (1.4 × the distance + 16 blocks, 48 more
for another storey, up to 128; vanilla's follow range of 48 gave partial paths across a town), and a folk who arrives
at the end of a partial path no nearer than before goes round by open ground 12–20 blocks on before trying again.

## 2. Looks: skins composed on the client

`Resident.appearance` is a small **genome** (`person/Genome`): skin tone, hair colour, eye colour, hair style, facial
hair, an "unusual build" bit (slim arms for a man / wide for a woman) and a 13-bit detail seed. Newcomers get colouring
weighted by their culture; children take each colouring field from one parent or the other (sometimes between them).
Any int decodes to someone plausible, so appearances from another factory still work.

`SkinComposer.compose(FolkLook)` is a **pure function** from (genome, culture, job, age, sex) to the 64×64 two-layer
player skin, drawn pixel by pixel: skin and face (eyes, brows, nose, lips, cheeks, freckles), 15 hair styles with
volume on the hat layer (crops, side parts, curls, buns, braids, ponytails, topknots...), facial hair, clothes cut and
dyed after the culture (wisteria robes and obi in lavender and cream, desert linen and indigo kaftans, alpine dirndls
and lederhosen, Breton stripes on the coast, steppe deels with side closures, moorland check, tropical florals...),
the trade's marks on the outer layers (smith's leather apron and sooty arms, baker's white apron and cap, farmer's
dungarees and straw hat, guard's mail, tabard and helmet, priest's robe and stole, scholar's robe and spectacles,
merchant's coat with gold buttons, builder's tool belt and cap; and for the phase 3 trades the miner's soot and candle
cap, the charcoal burner's blackened arms, the butcher's spattered apron, the dyer's stained hands, the beekeeper's veil,
the judge's black robe and bands, the reeve's and steward's chains of office, the jailer's keys, the entertainer's
motley...), and age (grey hair, wrinkles, shawls and cardigans).

**Station** (`Resident.station`, docs/LIVING_WORLD.md §8.1) dresses the same person differently: the poor in faded,
greyed-down cloth with patches, a ragged hem and grime, often barefoot; merchants in cleaner, richer dyes with buttons,
gold trim and boots; the gentry in fine linen under a coat of deep costly colour (burgundy, forest, navy, plum) with a
lace collar and sometimes a feathered hat; the lord in royal purple, crimson or blue, embroidered, with an
ermine-trimmed mantle, a jewelled chain and a gold circlet. The composer's choices are seeded by the look without
station and office (`FolkLook.baseKey`), so rising in the world changes the finish of someone's clothes, not their face
or the cut of their culture's dress. **Office** adds the guard captain's gold sash and red plume and the master builder's
felt hat and rolled plans. When the economy moves a household's station, embodied folk are re-dressed within a pass of
the embodiment manager (`Folk.setStanding`). Composition takes about 0.13 ms (measured by the unit test); the client caches textures per look (`FolkSkins`, LRU of 384, released
after 45 s unseen). No skin assets exist.

Preview sheets without the game: `HEARTH_FOLK_PREVIEW=1 ./gradlew test --tests '*SkinComposerTest*'` writes front,
back and side "paper dolls" of every culture × trade × age to `build/previews/folk/`.

## 3. Residents: names, traits, families

`HearthResidentFactory` (registered in `NpcModule.init()`, replacing the sim's default) gives culture-specific names
from `data/hearth/hearth_names/<culture>.json` (`{"male": [...], "female": [...], "family": [...]}`, 230–270 names per
culture; data packs replace a culture's lists), two or three traits (industrious/lazy, sociable/shy, brave/cautious,
cheerful/grumpy, greedy/generous, pious, curious, gossip, dreamy — never both of a pair), a looks genome, and family
links. A child takes a parent's family name, inherits colouring, is added to both parents' children, and the parents
become each other's spouse. All of it lives in `Resident.extra("npc")` (`NpcData`). Deterministic for a given random.

## 4. Embodiment (ARCHITECTURE.md §4)

`EmbodimentManager`, ticked once per server tick:

- Every `pass_interval` ticks it considers `settlements_per_pass` embodied settlements (round robin). For each resident:
  where they are (their entity, or where their routine says they are now — `RoutinePlacement`), distance to the nearest
  player, and whether that position is in an entity-ticking chunk.
- `EmbodimentSelector` (pure, unit-tested) decides: spawn within `spawn_radius` (96), fold beyond `despawn_radius`
  (112) or out of ticking chunks; at most `max_per_settlement` (48), nearest first, existing entities favoured by 16
  blocks so folk do not flicker; new entities limited by what `max_per_server` (400) leaves. When the server is full a
  resident nearer a player than the farthest entity anywhere takes its place.
- Spawns are queued and made `spawns_per_tick` (4) at a time across the server, where the routine puts them: in bed at
  night (asleep), at their job site in working hours, at the square in the evening, at their door just after a block
  of their day begins; a resident folded less than a minute ago reappears where they were.
- Entities removed by the game (chunk unload) fold on removal; on server stop every entity folds before the sim saves.
- A resident who dies is removed from their settlement (`removeResident`) and nearby players get a death message.
  If the sim removes a resident (`RESIDENT_REMOVED`) or a settlement stops being embodied (`EMBODIMENT_CHANGED`),
  their entities go.
- Strays (a folk from a spawn egg or `/summon`) join the nearest settlement within 160 blocks as a new resident (the
  sandbox camp in dev).
- **Escorts** (§13) are pinned: an escorted resident is never folded by the selector, by its settlement going abstract
  or by leaving it, until the escort ends. `EmbodimentManager.spawnNear` embodies a resident beside the player (honouring
  the server cap, folding the farthest entity if it must) when an escort starts with a resident who is only a record.
- **Offices** (`person/Office`) are derived, not stored: the longest-serving adult of a trade with at least two adults
  holds it (the captain among guards, the master builder among builders); `TownState` caches the holders and recomputes
  them when residents come or go.
- A resident killed by a player: with the law module present the law tries the killer; without it, the standing module
  hears a deed (`npc:killed`, −100), or without that the settlement's raw reputation drops 50. Hurting a folk works the
  same way (`npc:hurt`, −3).

## 5. Routines

`Schedule.of(job, age, traits, culture, seed, day)` is pure and unit-tested. An adult's day: wake, breakfast at home,
work, lunch (at work for builders, farmers, guards...; at the inn for the sociable; at home otherwise), work, free
time, supper, an evening at home by the fire, bed. Habits by trait (the industrious rise 30 min earlier and work longer;
the lazy rise late; the sociable stay out), by trade (bakers at the ovens from 4:00 and in bed early; innkeepers open
late; fishers out early; a third of guards keep the night watch), by culture (siesta and late suppers in desert,
mediterranean and mesa towns; early nights in the north) and by age (children play most of the day and sleep at 20:00;
elders sit on benches and nap after lunch). Free time is chosen per day from traits: the square, the inn, a viewpoint,
visiting a friend's or relative's door, the altar, a bench, a stroll, reading, the garden.

The phase 3 trades keep their own hours: the lord rises at 7:30, holds court from 9 to 12:30, dines in the keep, goes
about the domain in the afternoon and sits up late; the reeve keeps office hours from 8, the judge 9 to 16; half the
jailers keep the night watch like a third of the guards; entertainers rise late and play into the night; stablemasters
and butchers start before dawn; the charcoal burner tends the clamp until 19:00; miners, haulers, diggers, salters and
beekeepers lunch at work.

Someone the sim has not housed yet (no home plot, no bed) dozes on a bench at the inn at night rather than standing
about the town's centre.

Overrides, checked once a second: a monster in sight → flee (and if there are several, or raiders, the town alarm:
one folk runs to ring the nearest bell, everyone else goes home and cowers); rain on outdoor activities → shelter
(guards, jailers and miners keep at it; farmers, builders, foresters, quarriers, herders, haulers, diggers and the
charcoal burner work through a shower but not a storm).

Destinations come from POIs. A plot's POIs are its blueprint's (`HearthServices.blueprints().blueprint(plot.spec(),
world.terrain()).pois()`), or — when there are none (a player-built house, the sandbox) — found by **scanning the plot's
blocks** (`PoiScanner`: beds, workstations by trade, chests and barrels, stair chairs, carpet-on-post tables, campfires
and stoves, bells, lecterns, altars, doors). Scans are rationed (one per server tick, server-wide) and cached until the
plot's state or stage changes. Beds and job sites are shared out among the people of a house or workshop in a stable
order, so nobody aims for the same bed. Residents without plots use the bed, job site and anchor in their npc data and
the POIs around their anchor.

The brain (`FolkBrain`) thinks every 2 ticks with a player within 24 blocks, every 5 within 64, every 12 beyond;
paths are asked for at most once per `repath_cooldown`, and a folk that makes no progress for ten seconds gives up
(or, if no player can see it or where it is going, is moved there; a sleeper nobody sees slips into bed). A folk
where no path starts at all (up on a well's rim by way of its cauldron, a fence top) hops down to the nearest
whole-block ground after two searches in a row, seen or not. A night that runs into a new schedule block or a new
day's schedule (midnight) is not a change: sleepers stay in bed. Small human touches: they look at players passing
close, wave the first time they see one, greet players they know by name in chat (reputation 25+ or met before;
at most once per 40 s per player), turn to face each other and take turns talking when two are at leisure together
(and sometimes become friends), children play tag round the square, elders stoop and sit, everyone breathes.

## 6. Trades, embodied

**Builders** — the priority — work as a crew on a building site, under this contract with the sim (ARCHITECTURE.md
§5.3):

1. Pick a plot in state `BUILDING` that is not `waitingForGoods()` and has room in its crew. A site takes
   `crewCap = clamp((sizeX + sizeZ + 3) / 7, 1, 10)` builders: two on a 7×7 cottage, three on an 11×9 house, five on a
   20×14 hall, ten at most; claimants beyond the cap (by id) go elsewhere. A builder with no room in any crew carries
   materials for the nearest site (store to site front and back, three loads; the crew there then need not fetch their
   own), and with no site at all works at the yard.
2. The plot's counter is `(plot.stage(), plot.step())`; a blueprint's steps are in build order, supports first. Builders
   may place steps **ahead of the counter**: any step of the current stage still to do whose supports are in place — no
   earlier step still to do at its own position or the one below it, nor, for a block with no body of its own (a torch,
   a sign, a flower, a rail: no collision shape), beside or above it (`work/StepPlan`). The site (`work/BuildSite`, one
   per plot, shared by its crew, kept in `TownState`) reads the stage from the counter on (at most every 6 ticks after a
   placement, every 40 otherwise; 2 048 steps ahead, 640 still to do).
3. Each builder takes the first such step no other builder has within reach (a step no place one can get to reaches is
   passed for the next ready ones a while: up to four are tried, with at most eight paths asked for) and goes to the place — the ground, a floor
   inside, a wall top, or the scaffolding at any height it stands to — from which the most such steps are within reach
   (4.6 blocks eye to block centre), not where another builder works (2.5 blocks apart). There they place **every** such
   step within reach, in order, one per `builder_step_ticks`, before moving along: flags `UPDATE_CLIENTS |
   UPDATE_KNOWN_SHAPE | UPDATE_SKIP_ON_PLACE` (no neighbour updates), its block entity tag, a swing, the block's sound
   and particles; an **air step** breaks what is there. A solid step is never placed into a player, and a step where
   somebody's block entity stands (a chest, a sign, a bed that is not the step's) is passed, never built over: the same
   rule as the sim's applier. Ground spots are tried with the pathfinder; a walk that comes no closer for three seconds
   has failed, and the last block to the spot is a step across rather than more pathfinding. Builders open a shut
   trapdoor their path goes through (the pathfinder counts trapdoors as open: the hatch over a loft ladder fitted by a
   builder upstairs), work from the ground, a floor or the scaffolding but not balanced on a wall top or a ledge with a
   drop on two sides, and step aside from a step they stand on. A builder whose walks fail six times in a row from the
   same place (shut in) goes out by the front of the site: a last resort, after half a minute of getting nowhere.
4. The site moves the counter over the done steps from the cursor — in the world as the blueprint wants them, placed by
   a builder this stage, or passed — with one `settlement.advanceConstruction(plot.id(), n)`. So the counter, the
   chunk ledgers and the chunk-load replay see exactly what a builder placing one step after another would have left; a
   step placed ahead and then lost (the chunk folded back to its record) is placed again, as the same block, by the
   applier or the next builder. While the counter stands still (the scaffolding going up) the site calls
   `advanceConstruction(plot.id(), 0)` every 100 ticks while the crew has put a block or a scaffold in place in the last
   15 s, which tells the sim the crew is at it so abstract labour keeps off the plot (a crew that has stalled is not
   covered, so a stalled site never stalls the town).

**Scaffolding** (`work/Staging`). Work high on the shell (more than three blocks above the ring's lowest ground, within
three blocks of the shell's faces) is reached from a **ring of vanilla scaffold columns** one block outside the
building's shell (its walls, eaves and roof as the remaining stages will have them). Cells where a remaining step will
put a block, or where something already stands above the floor (a neighbour's wall), are gaps; each column keeps two
blocks of headroom below anything above it. The ring goes up **before it is needed**: as soon as the stage
has high work on the shell, the site asks for it to the height from which the stage's highest course is at eye level
(in lifts of four; and higher whenever a step is out of reach from anywhere one can stand but a ring column could reach
it). Only the stretch of ring within reach of high work goes up (every column within five blocks of a high step still to
do, and gaps of up to two columns between such stretches, so the crew can walk along the wall it works; once wanted a
column stays wanted while the ring stands): round the whole shell while walls rise, along one wall for a gable or an
upper floor's windows. The crew raises it a column at a time: a builder takes the nearest column still short that nobody else has, stands
beside its foot and runs it up to the top, as clicking the foot of a vanilla scaffold column extends it at the top; one
already up on the ring runs up the columns in reach of where they stand; once every short column is in hand the rest
get on with the walls. A column nobody can get to the foot of is left short (a gap in the ring). A column of
scaffolding is climbed inside and stood in at any height, as players use it, so the ring is a walkway at every level:
builders climb in at a column's foot, rise inside it (0.15 a tick), walk along the ring at the height they stand
(`work/Walker`; the pathfinder does not walk on scaffolding, so the ring is moved on by hand, round the other way past a
gap or another builder, or down and round by the ground), and are not pushed by others while up there. Back at a
column's foot they step out of it by hand before walking anywhere on the ground (no path starts inside scaffolding). The ring stays
up through every stage with high work on the shell (walls, floors, roof) and comes down when the last of them is done:
the crew pulls each column down from the ground (the whole column at once, as pulling the foot of vanilla scaffolding
does), and every other column within an arm's length of where they stand before moving along. A ring whose work is
done but which still stands (its crew called off to mend or shelter as the house was finished) is the first thing an
idle builder sees to. Every block is in the settlement's `ScaffoldLedger` (`HearthDataKey hearth:npc/scaffolds`) under the site's own
id; the ring comes down at once when the last of its crew folds away, and the housekeeping pass clears the scaffolding of
a site nobody claims (after a minute) and of builders no longer embodied, so a town is never left wearing it. Scaffolding
is never a blueprint step. A step nobody can reach from anywhere is placed leaning out from the nearest place one can
stand (7 blocks), and after a fair try (10 s) from wherever the builder is (within 12 blocks; a builder further off goes
to the front of the site first), rather than stall the town.

When a stage begins, builders first fetch materials from the work storage or the nearest store (unless someone carries
for the site, or they are up on the scaffolding). With nothing to build, a builder cuts an open working (quarry pit,
mine, clay or sand pit, salt works) that nobody is at — a young town opens its quarry pit before it has a quarrier — as
the trade that fits it would (`ExcavationWork`, one builder to a working; woods stay the foresters'); else they carry
for a site whose crew is full, else work at their yard.

**Farmers** farm the real crops of the town's farm plots (or the tilled earth round their job site): harvest ripe crops
and re-sow at once (1 FOOD to the stockpile per harvest), sow bare farmland with the culture's crop (wheat, potatoes in
the north and the hills, beetroot in karst and wisteria towns, carrots in the tropics and the bayou), till grass at the
field's edge (inside farm plots only), and weed. **Guards** walk a round of guard posts and gates (or the town's
quarters), answer the alarm, and fight monsters (sword and shield). **Smiths, carpenters, masons, bakers, brewers,
tailors, scholars and priests** work at their job site with their motion (hammering, crafting, reading, praying),
tools in hand, sounds (anvil, wood, stone, smoker, brewing stand, loom, pages) and particles (sparks, chips, smoke,
bubbles, enchantment glyphs), with trips to the store between bouts. **Merchants, innkeepers, minters** keep their
counter facing customers (the innkeeper also goes round the inn's tables). **Hunters** range the edges; **couriers** go between the gate and the square. Folk without a trade keep house.
Children play; elders sit on benches.

### 6.1 Gatherers (docs/LIVING_WORLD.md §7.3–7.4)

**Miners, quarriers and clay diggers at the excavations.** A quarry pit, a mine, a shaft, a clay or sand pit is a plot
whose archetype is `EXTRACTION` (or an `extract/` id): a blueprint of cuts (air steps) and the props, rails, ladders and
lanterns that go with them, one stage per bench or gallery (`work/Extraction`). Builders never take one
(`BuilderWork.buildable` excludes them); the trade that fits does (`Extraction.fit`: miners ore and specialty then
stone, quarriers stone then ore, clay diggers clay and sand, salters salt, foresters plantations), spreading over open
excavations. `ExcavationWork` keeps the builders' contract exactly:

1. Pick an excavation in `BUILDING`, not `waitingForGoods()`, whose kind fits.
2. The next step is `(plot.stage(), plot.step())` of its blueprint, **read again at the moment of cutting**.
3. Walk to a `WORK_FACE` POI of the plot within reach of the step (else any place within reach that is not on top of
   it); cut an air step (the block broken with its sound and dust; never one a player or another creature stands on),
   or set a solid step (a lantern, a prop, a ladder going in behind the face) — nothing outside the blueprint is ever
   touched; a block entity in the way is passed over, as builders do.
4. Then `settlement.advanceConstruction(plot.id(), 1)`: the sim credits what the cut removed as yield.

About one step per `gathering.cut_step_ticks` (30, the abstract pace); steps that change nothing are passed over 24 a
tick. Every eight natural cuts the worker carries a load (the block they cut, in both arms) to the pit's `STORAGE` POI
or its entrance. **Time spent cutting is not reported as labour** (the steps carry it, `Task.creditedBySteps`), so a pit
pays once. With no excavation open the trade falls back to its old work (quarriers and miners at the rock by the
workplace, clay diggers at the bank, salters raking the pans: `DiggerWork`), whose time is credited as labour.

**Foresters fell real trees** (`ForesterWork`, `Trees`). A tree is found in a woods site of the territory
(`Settlement.sites()`), or in a ring just beyond the town's edge: a few sample spots, each a 17×17 patch (every other column) read
from the heightmap, then down the trunk to a stump on natural soil. It must be a body of logs with a natural crown
(non-persistent leaves: a log cabin or a hedge of placed leaves is never a tree), no larger than
`gathering.max_tree_logs` (the redwood giants stay standing), touched by nothing built (planks, glass, lanterns, any
block entity: no treehouses, no bee nests), outside the town's bounds unless the survey marked woods there, never beside
a plot, at least `gathering.keep_clear_of_deeds` blocks from a player's deeded plot, and inside the territory. Foresters
claim their tree (`TownState.treesClaimed`). Then: the axe at the stump for a fair stint (longer for bigger trees), the
crack of the trunk, and the tree coming down **top to bottom**, its own crown with it (leaves reached from its logs,
never through a leaf touching another tree; vines and cocoa on it go too); saplings of its kind on every stump (all four
of a 2×2 trunk) and up to `gathering.saplings_per_tree` in all on open soil nearby; and the logs carried to the yard (the
workplace's store or the town's nearest). A tree left half felled when the folk is interrupted is finished off and
replanted at once. Every third bout they work a plantation excavation if one is open. The wood is the sim's to credit
from the forester's working time, as it is while nobody watches.

**Herders** (`HerderWork`) work the animals in and around their own pasture: shear sheep that have grown their wool
back (`setSheared`, the sound, a puff of wool, no drop), milk the cows, drive strays back (getting round behind them),
feed and walk among the herd. Never an animal that is leashed, named, tamed or a caravan's beast.
**Beekeepers** (`BeekeeperWork`) find the hives of their workplace (a scan every five minutes), smoke them, take the comb
from full ones (the honey level drops to empty, the bees stay calm) and carry it to the store. **Fishers** (`FisherWork`)
go to the town's docks (or a bank by open water), cast, wait for the bite, land the catch (the culture's fish held up),
and carry the basket to the fishery's store. `gathering.shear_and_milk` and `gathering.harvest_hives` switch the real
changes off. With `gathering.terrain_changes` false nothing gatherers do changes the land: excavations and felling are
mimed and the loop runs as numbers (§7.8).

**Haulers** (`HaulerWork`) carry the town's chains: routes are read from its plots every two minutes — what each
produces (excavations by kind; logging camp and sawmill logs, quarry stone, fields and barns grain, fishery fish, kilns
charcoal and brick, smelter metal, glassworks glass, apiary wax, tannery leather, salt works salt) and what each wants
(the mason stone, the smelter ore and charcoal, the charcoal kiln logs, the brick kiln clay, the mill and bakery grain,
the chandler wax, stores anything). A hauler fetches a load and carries it in both arms, slower, to the workshop that
wants it (or the nearest store). Within a town the goods move instantly in the sim; haulers are what it looks like, and
their time is labour. (Carts are not drawn yet: see docs/requests/p3-npc.md.)

### 6.2 Officials, the lord, and the new trades

The **reeve** keeps the rolls at the town hall, posts notices, inspects the building sites, collects the market dues and
reports to the lord's hall in the morning; the **steward** counts the treasury at the `STRONGBOX`, takes stock at the
stores and attends the lord; the **judge** presides from the courthouse's high seat or reads the law at its lectern and
watches justice done at the `PILLORY`; the **jailer** walks the `CELL`s with the keys and keeps the jail door
(`OfficialWork`). The **lord** (`LordWork`) holds court seated at the keep's `THRONE` in the morning, gesturing to whoever
stands before them, and goes about the domain in the afternoon: viewpoints, building sites, the stables. The **guard
captain** drills at the guardhouse at dawn, inspects the posts and reports to the hall; the **master builder** walks the
other sites with the plans between stints of building (`Office`).

The workshops of the chains work at their job site with their motion, tools, sounds and particles (`CraftWork`): the
charcoal burner at the clamp (shovel, signal smoke), the kiln worker (bricks, smoke), the glassmaker (blowpipe, flame),
the smelter (ore, lava sparks), the tanner at the vats, the butcher at the block, the chandler dipping candles, the
papermaker at the press, the dyer at the vats, the boatwright building a boat. Traders keep a stall, the fence (shown
as a pawnbroker) a quiet counter (`CounterWork`). The entertainer plays on a `STAGE`, in the inn or in the square (notes,
cheers); the bath keeper tends the `BATH`s; the stablemaster grooms the horses and mucks out at the `HITCHING_POST`s;
the healer goes round the `SICKBED`s and grinds herbs (`ServiceWork`).

Labour done while embodied (outside step-credited work) is recorded (`extra("npc").labour`) and handed to the sim with
`Settlement.creditLabour` when the folk folds back into its record or dies (adults; not builders, whose work arrives step
by step, nor the time miners and quarriers spent cutting).

## 7. Conversation

Right-click a folk (not while they sleep, unless sneaking). The server builds the conversation and sends it
(`hearth:npc/conversation`): name, trade, settlement, a greeting and options. The client shows a parchment screen with
their face drawn from their own skin and the speech written out as they say it; picking an option sends its id
(`hearth:npc/choose`); closing sends `hearth:npc/end`, and the server closes the screen (`hearth:npc/close`) on goodbye
or when the conversation has ended on its side. The folk stops, faces the player and gestures while talking; each
option picked keeps them another minute; server and screen both end the talk beyond 10 blocks; a monster in sight or
the alarm ends it too (they run). Options beyond the screen's height scroll with the mouse wheel.

The greeting is composed from templates (`data/hearth/hearth_dialogue/*.json`) to fit the moment: time of day,
weather, the player's reputation (distrust, stranger, known, friend, hero), the folk's traits and mood, trade, age and
culture, how often they have met — and, now and then, the town's news (`TownNews`, filled from `SettlementEvents`: a
building finished, construction stalled for want of stone, a new tier, a birth or arrival, a death, a party of settlers
gone to found a hamlet: `news.left`, from the sim's `extra("sim").left_for` on departing settlers). Built-in options:
"What's new in town?" (news), "Heard any rumours?" (other settlements by name, compass direction and distance: live ones
and those the sim's atlas knows of that nobody has visited yet, from `HearthWorld.knownSites` within 2500 blocks; with
Expanse loaded, its landmarks — lighthouse, pagoda, stone circle, monastery in the clouds... — asked of the quest
module's asynchronous `StructureLocator`, one kind per settlement at a time, at most one request every 10 s
server-wide, the answer kept with the settlement; never vanilla's locate on the server thread), "Tell me about
yourself." (trade, spouse, children or parents), "Goodbye."

**Adding options (other modules).** Register a `DialogueProvider` in your module's init:

```java
DialogueProvider.register(new DialogueProvider() {
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		if (r.job == JobType.MERCHANT) {
			out.add(new Option("economy:trade", Component.translatable("hearth.economy.talk.trade"), 100));
		}
	}
	public boolean select(ServerPlayer player, Settlement s, Resident r, String id) {
		openTradeScreen(player, s, r);   // your own screen replaces the conversation
		return true;
	}
});
```

Options are sorted by `order` (0–99 small talk, 100–199 trade, 200–299 quests; goodbye is last). After `select` the
conversation refreshes its options (so an accepted quest disappears) unless your screen replaced it.

**Dialogue data.** Each file holds categories of lines; files merge (add a file in a data pack to add lines). A line
is a string or `{"text": ..., conditions...}`: `time` (morning/day/evening/night), `weather` (clear/rain/storm), `rep`
[min, max], `traits` [any of], `jobs`, `cultures`, `age`, `mood` [min, max], `met` (at least), `weight`. The most
specific fitting lines are preferred. Placeholders: `{player} {name} {given} {settlement} {job} {building} {goods}
{amount} {tier} {other} {direction} {distance} {landmark} {spouse} {children} {culture}`. Categories: `greeting`,
`passing`, `farewell`, `news.built|stalled|tier|arrived|died|founded|none`, `rumour.settlement|landmark|none`,
`about`, `about.job`, `about.spouse`, `about.children`, `about.parents`. Lines may also ask for a `stations` list
(poor, peasant, artisan, merchant, clergy, scholar, official, gentry, lord); every trade, the phase 3 ones included, has
lines in `about.job` (a unit test holds that). The reputation a line is chosen by is the standing module's town standing
pulled a third of the way toward the resident's own opinion of the player when that module is present, else the
settlement's raw reputation.

## 8. Quest markers

Every `marker_refresh` ticks (a slice of folk per tick) each folk asks `HearthServices.quests()` (when present) for its
marker **for each player within 32 blocks**; only changes are sent, one `hearth:npc/markers` packet per player per tick
at most, and a player who walks away gets the marker cleared. The client keeps them by entity id (`FolkMarkers`) and
draws them above the head: gold **!** (offer), gold **?** (ready to hand in), grey **?** (in progress). Each player sees
their own quests; the entity-data marker is no longer used.

## 9. Threats

With `hostiles_target_folk`, zombies, skeletons, spiders, illagers, ravagers and vexes get a target goal for folk as
they load. Folk flee what they see, raise the alarm when it is more than one or a raid, and ring the bell; guards fight.
Hurting a folk costs the player reputation and the folk's good mood.

## 10. Config: `config/hearth/npc.json`

```json
{
  "embodiment": {"spawn_radius": 96, "despawn_radius": 112, "max_per_settlement": 48, "max_per_server": 400,
                 "spawns_per_tick": 4, "pass_interval": 10, "settlements_per_pass": 4},
  "ai": {"think_near": 2, "think_mid": 5, "think_far": 12, "near_distance": 24, "mid_distance": 64,
         "repath_cooldown": 40, "builder_step_ticks": 30, "scaffolding": true},
  "threats": {"hostiles_target_folk": true, "death_messages": true},
  "talk": {"greetings": true, "marker_refresh": 40, "landmark_rumours": true},
  "perception": {"sight_range": 24.0, "field_of_view": 140.0, "near_sense": 1.5, "behaviour_poll": 20},
  "gathering": {"terrain_changes": true, "cut_step_ticks": 30, "max_tree_logs": 320, "saplings_per_tree": 2,
                "keep_clear_of_deeds": 8, "shear_and_milk": true, "harvest_hives": true},
  "caravans": {"embodied": true, "max_active": 48, "speed": 1.6, "guards": 2, "traders": 1, "animals": 3},
  "escort": {"teleport_distance": 20.0, "lose_after": 1200, "arrive_distance": 5.0}
}
```

## 11. Dev tools (before and without the sim)

```
/hearth npc spawn [job] [culture] [child|adult|elder]   a folk in front of you, living in a camp of that culture
/hearth npc sandbox [radius] [culture]                  a village from the beds and workstations built round you
/hearth npc sandbox build cottage|tower                 stake out a building for the sandbox's builders
/hearth npc sandbox clear                               remove the sandbox settlements of this dimension
/hearth npc sandbox build quarry                        stake out a quarry pit (extract/quarry_pit) for a quarrier to work
/hearth npc info                                        the folk you look at: station, office, traits, mood, what they are doing
/hearth npc stats                                       embodied folk and settlements
/hearth npc behaviours                                  other modules' behaviours by priority; how many folk are in one now
/hearth npc escort <pos>                                the folk you look at follows you there (NpcService.follow)
/hearth npc station <station>                           re-dress the folk you look at (poor ... lord)
/hearth npc caravan [send]                              the caravans on the roads; send one from the nearest town to the next
```

Build a few houses (beds, a crafting table, an anvil, a smoker, chests, stair chairs, a bell in the square), stand in
the middle and run `/hearth npc sandbox 32`: a folk per bed, a trade per workstation, couples and children, who keep
their day by the clock (`/time set` to watch them go to bed, get up, work and gather). `/hearth npc sandbox build
tower` shows a builder putting up a 12-high watchtower on scaffolding. After integration the real sim replaces the
sandbox; it is all in `npc/dev`.

Client game test: `dev.voidmc.hearth.gametest.npc.FolkPortraits` photographs line-ups of every culture and trade,
families, faces, poses, the conversation screen and a builder at work (`build/run/clientGameTest/screenshots`).
`JAVA_TOOL_OPTIONS="-Dhearth.tour=folk" HEARTH_NPC_TOUR=living ./gradlew runClientGameTest` runs only the phase 3 shots:
dress by station, officials and every new trade, another module's behaviour taking a folk over, an escort, a quarrier
working a quarry pit, a forester felling and replanting, and a caravan on the road, logging each as `[hearth npc tour]`.

## 12. Performance

- Per tick: the manager touches at most `settlements_per_pass` settlements every `pass_interval` ticks and spawns at
  most `spawns_per_tick` entities; markers are refreshed a slice at a time.
- Per folk: a decision every 2–12 ticks by distance to players; one threat query and one schedule read per second;
  paths at most once per cooldown; block reads only in loaded chunks; POI scans rationed server-wide.
- Records do nothing per tick. A settlement nobody is near costs the npc module nothing.
- The client composes each distinct look once and frees textures nobody sees.
- Behaviours: each folk asks the registered behaviours ranked above its routine once per `perception.behaviour_poll`
  ticks, staggered, stopping at the first that wants it (the list is sorted, so low ones are never asked); only the
  running behaviour ticks every tick. `canSee` is arithmetic plus vanilla's per-tick cached line of sight.
- Gatherers: a tree search reads at most a few hundred heightmap columns and one tree's blocks, once per tree felled
  (about once a minute per forester); a felled tree comes down at three logs a tick. Hive scans are bounded and every
  five minutes; haulers re-read the plots every two minutes. Excavation cuts go one per `cut_step_ticks` per worker.
- Caravans: a capped list of journeys (arithmetic); one party at most is put on the road per two seconds, server-wide,
  and only where a player is within the spawn radius in ticking chunks.

## 13. Behaviours other modules teach folk (`NpcService`)

`HearthServices.npc()` is `NpcServiceImpl`, registered in `NpcModule.init()`:

- `folk(resident)` the embodied folk as a `FolkHandle`, or null; `folkNear(level, at, radius)` nearest first.
- `FolkHandle`: `resident`, `settlement`, `entity`, `awake` (alive, not folded, not asleep), `canSee(entity)` (within
  `perception.sight_range`, inside a `field_of_view` cone from the head's yaw and pitch, or within `near_sense` in any
  direction, then vanilla's line of sight — light, sneaking and armour are the law module's to weigh), `moveTo` (asks
  for a path at most once per repath cooldown for the same target, so calling it every tick is fine), `arrived`, `stop`,
  `lookAt`, `say` (to one player, or to players within 16 blocks, as `Name: "line"` with a talking gesture), `hold`,
  `swing`, `gameTime`.
- `registerBehaviour(FolkBehaviour)`: register in your module's `init()` if `HearthServices.npc()` is set (the npc
  module initialises before standing, law, repair and social), otherwise on `SERVER_STARTING`. A second registration
  with the same id replaces the first.

The runner (`service/BehaviourRunner`, one per folk) weighs behaviours against the routine's own priority now:

| Routine state | Priority |
|---|---|
| leisure | 20 |
| getting up, an evening at home | 30 |
| a meal | 40 |
| work | 50 |
| sheltering from rain | 60 |
| asleep | 90 |
| in conversation with a player | 199 |
| ringing the bell / hiding in a raid | 240 |
| fleeing a monster | 250 |
| a guard in a fight | 299 |

About once a second (staggered) it asks, highest first, the behaviours ranked above both the routine and the behaviour
already running whether they `wants` the folk; the first that does takes over: the routine's task stops (a builder's
scaffolding comes down, a sitter stands up, a conversation ends), hands are emptied, and `start` is called. The running
behaviour's `tick` is called every tick until it returns false (`stop` is called and the routine plans afresh) or
something higher wants the folk (the lower one is stopped first). The routine keeps track of the day underneath, and a
reaction of its own that outranks the behaviour (a monster in sight: 250) stops it. A behaviour that throws is stopped,
skipped by that folk for a minute, and logged once. Bands, as the api documents them: below 100 the routine (a festival
at 25 takes over free time but not work); 100–199 work that interrupts it (repairs, hauling); 200–299 reactions
(witnessing a crime, fleeing a fire); 300 and up guards on duty and emergencies.

**Escorts.** `follow(player, settlement, resident, destination)` embodies the resident beside the player if they are
only a record (null if the server is full or there is nowhere to stand) and runs the escort behaviour (`hearth:npc/follow`,
priority 280): the folk follows two to four blocks behind, catches up out of sight if left `escort.teleport_distance`
behind, and ends **arrived** within `escort.arrive_distance` of the destination ("Here we are. Thank you...") or **lost**
if the player is gone (logged off, dead, another dimension, spectating) for `escort.lose_after` ticks, or if the folk dies
or is folded. The outcome is kept ten minutes: `NpcServiceImpl.escortStatus(resident)` / `Escorts.FINISHED` (an api form
is in the api as `NpcService.escort(resident)` and the event `SettlementEvents.ESCORT_ENDED`).

**A change of trade.** When the sim gives an embodied resident another trade (the daily job assignment, a player's hire),
the brain lets the old work's claims go and takes up the new trade's work at the next plan; before, the entity kept its
first trade until it was folded.

**Waking.** `FolkHandle.wake()` gets a sleeping folk up and keeps them up for two minutes (the repair module's brigade
wakes the town for a fire). `NpcService.traits(resident)` gives the trait names other modules read.

**Real yields.** A forester who carries a felled tree's logs to the yard reports them with `Settlement.creditYield`; the
sim credits those logs and takes the same goods back off the forester's labour credit when the entity folds.

**Caravans of the sim.** A journey keeps the sim's caravan id and timing (`HearthWorld.caravan(id)`), so the party walks
where the sim says the caravan is; a party overcome is reported with `caravanLost` (its drops marked stolen from the home
town); a caravan an escort quest protects (`escorted`) gets no second party.

## 14. What the npc module tells other modules

- `LawService`, when present, is the judge of harm: the npc module reports nothing about hurt or killed residents to
  standing when the law module runs (it sees and reports crimes itself). Caravan crimes it cannot see (§15) are reported
  with `LawService.report`.
- `StandingService`, when present and the law module is not: deeds `npc:hurt` (−3), `npc:killed` (−100),
  `npc:caravan_assault|murder|robbery`.
- Station: whoever moves `Resident.station` (the economy) sees embodied folk re-dressed within a pass.

## 15. Caravans on the road (docs/LIVING_WORLD.md §7.6)

The sim moves goods between towns and fires `SettlementEvents.CARAVAN`. The npc module keeps each departed caravan as a
`Journey` (at most `caravans.max_active`; forgotten after three sim days if the sim never closes it) until the sim says it
arrived, was robbed or lost. Where it is follows from the time since it left at `caravans.speed` blocks a second along
the line between the two towns' centres. Every two seconds, when a player is within the embodiment spawn radius of that
spot in ticking chunks, the party is put on the road — a guard at the front, the traders, the pack animals (mules and
donkeys with chests; llamas with the caravan's coloured carpets in the cold cultures; camels in the dry), a guard at the
rear — and it walks on toward the destination in a line, the leader making for the caravan's place on the road and the
rest following; stuck where nobody can see, it moves on. The party folds away beyond the despawn radius. Guards fight
monsters near the caravan and a player who attacks it; traders tell passers-by where they are bound and what they carry.
Killing them or their beasts is reported to the law (assault, murder, and robbery when the caravan is overcome). Nothing
of a party is saved: its people are folk with a controller, and its animals carry the `hearth.caravan` tag, which
`EntityNoSaveMixin` keeps out of chunk saves and which herders and stablemasters leave alone.
