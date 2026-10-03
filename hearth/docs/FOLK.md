# Hearthfolk — the npc module

The people of Hearth's settlements: who they are, how they look, how they spend their day, how they work (builders
raise the town block by block), how they talk, and how all of that stays cheap enough for a busy server. Code:
`dev.voidmc.hearth.npc` (server and common), `dev.voidmc.hearth.client.npc` (rendering, skins, conversation screen).

```
npc/
  NpcModule        init: entity, resident factory, dialogue provider, networking, reloaders, events, commands
  NpcConfig        config/hearth/npc.json
  NpcTime          the sim calendar (sim world day, or the overworld clock)
  person/          Trait, NpcData (Resident.extra("npc")), Genome (looks), NameBank, HearthResidentFactory
  look/            FolkLook, SkinComposer (pure: look -> 64x64 ARGB pixels), SkinCanvas, Palette
  entity/          Folk (hearth:folk), FolkPose, FolkNavigation + ClimbNodeEvaluator, FolkSpots, FolkEntities
  place/           SettlementPlaces (POIs per plot), ResidentPlaces (one person's bed, home, work), PoiScanner, BlueprintSource
  routine/         Schedule (pure), Activity, Leisure, FolkBrain, Task, Tasks, Plans, RoutinePlacement
  work/            WorkBehaviour + BuilderWork, FarmerWork, GuardWork, CraftWork, CounterWork, FieldWork, ChoresWork, ScaffoldLedger
  embody/          EmbodimentManager, EmbodimentSelector (pure), TownState, SettlementSources, StrayHomes
  talk/            Conversations, NpcDialogue, DialogueTemplates, Greetings, Rumours, TownNews, TalkPayloads
  dev/             NpcSandbox, SandboxSettlement, SandboxBlueprints, NpcCommands   (isolated: the sim replaces it)
client/npc/        NpcClient, FolkRenderer, FolkModel, FolkRenderState, FolkSkins, ConversationScreen
```

## 1. The entity: `hearth:folk`

A player-shaped `PathfinderMob` (not a villager). **The `Resident` record is the person; the entity is a view of
it.** The entity type is `noSave`: folk are never written to chunks. When an entity goes away (out of range, chunk
unload, server stop) its state is written back into the record (`extra("npc")`: last position and time, health,
labour) and the sim is told (`setResidentEmbodied(id, false)`).

Synced data: resident id, full name, appearance genome, culture + job + age stage + sex (one packed int), quest marker,
pose (`FolkPose`: sit, hammer, craft, read, pray, wave, talk, tend, cower, cheer). Children are 0.62 scale with a bigger
head. The name and trade show when the crosshair is on them (within 20 blocks).

Movement: `FolkNavigation` opens and closes doors (vanilla `OpenDoorGoal`), climbs ladders, vines and scaffolding
(`ClimbNodeEvaluator` adds straight up/down nodes on climbable blocks), and sneaks to drop through scaffolding when the
path goes down. Sleeping uses the vanilla pose without touching the bed block (`Folk.sleepIn`), so a folk folded away
in bed never leaves a bed marked occupied. Step height 1.

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
merchant's coat with gold buttons, builder's tool belt and cap...), and age (grey hair, wrinkles, shawls and
cardigans). Composition takes about 0.13 ms (measured by the unit test); the client caches textures per look (`FolkSkins`, LRU of 384, released
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

## 5. Routines

`Schedule.of(job, age, traits, culture, seed, day)` is pure and unit-tested. An adult's day: wake, breakfast at home,
work, lunch (at work for builders, farmers, guards...; at the inn for the sociable; at home otherwise), work, free
time, supper, an evening at home by the fire, bed. Habits by trait (the industrious rise 30 min earlier and work longer;
the lazy rise late; the sociable stay out), by trade (bakers at the ovens from 4:00 and in bed early; innkeepers open
late; fishers out early; a third of guards keep the night watch), by culture (siesta and late suppers in desert,
mediterranean and mesa towns; early nights in the north) and by age (children play most of the day and sleep at 20:00;
elders sit on benches and nap after lunch). Free time is chosen per day from traits: the square, the inn, a viewpoint,
visiting a friend's or relative's door, the altar, a bench, a stroll, reading, the garden.

Overrides, checked once a second: a monster in sight → flee (and if there are several, or raiders, the town alarm:
one folk runs to ring the nearest bell, everyone else goes home and cowers); rain on outdoor activities → shelter
(guards keep watch, farmers and builders work through a shower but not a storm).

Destinations come from POIs. A plot's POIs are its blueprint's (`HearthServices.blueprints().blueprint(plot.spec(),
world.terrain()).pois()`), or — when there are none (a player-built house, the sandbox) — found by **scanning the plot's
blocks** (`PoiScanner`: beds, workstations by trade, chests and barrels, stair chairs, carpet-on-post tables, campfires
and stoves, bells, lecterns, altars, doors). Scans are rationed (one per server tick, server-wide) and cached until the
plot's state or stage changes. Beds and job sites are shared out among the people of a house or workshop in a stable
order, so nobody aims for the same bed. Residents without plots use the bed, job site and anchor in their npc data and
the POIs around their anchor.

The brain (`FolkBrain`) thinks every 2 ticks with a player within 24 blocks, every 5 within 64, every 12 beyond;
paths are asked for at most once per `repath_cooldown`, and a folk that makes no progress gives up (or, if no player
is within 32 blocks to see it, is moved to where it was going). Small human touches: they look at players passing
close, wave the first time they see one, greet players they know by name in chat (reputation 25+ or met before;
at most once per 40 s per player), turn to face each other and take turns talking when two are at leisure together
(and sometimes become friends), children play tag round the square, elders stoop and sit, everyone breathes.

## 6. Trades, embodied

**Builders** — the priority — follow this contract with the sim (ARCHITECTURE.md §5.3):

1. Pick a plot in state `BUILDING` (builders spread over plots; a plot reported stalled is left for two minutes).
2. The next step is `(plot.stage(), plot.step())` of the plot's blueprint, **read again at the moment of placing**.
3. Walk within reach (4.6 blocks eye to block centre); place it with flags `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` (no
   neighbour updates), load its block entity tag, swing, play the block's place sound and show particles. An **air
   step** breaks what is there with particles. Steps that change nothing are passed over quickly (24 per tick at most).
   A solid step is never placed into a player.
4. Then `settlement.advanceConstruction(plot.id(), 1)`.

About one step per `builder_step_ticks` (30), as §4 says. When a stage begins, the builder first fetches materials from
the work storage or the nearest store. **High work** (more than 3 blocks above the builder) is reached the way a
player reaches it: the builder picks a clear column next to the work that no upcoming step of this or the next two
stages will occupy, stands on it, jumps and places vanilla **scaffolding** underfoot at the top of each jump until it
stands one below the work, works from the top while the steps stay in reach, then drops back through the scaffold
(sneaking) and takes it down top first. Every scaffold block is recorded in the settlement's `ScaffoldLedger`
(`HearthDataKey hearth:npc/scaffolds`, saved with the settlement) and removed when the builder finishes, is
interrupted (activity change, threat) or folded away; anything left by a crash or an unloaded chunk is cleared the next
time the settlement is embodied. Scaffolding is never a blueprint step. If a step stays out of reach after a fair try
the builder places it from where it is (only from within 10 blocks) rather than stall the town. With nothing to build,
builders work at their yard.

**Farmers** farm the real crops of the town's farm plots (or the tilled earth round their job site): harvest ripe crops
and re-sow at once (1 FOOD to the stockpile per harvest), sow bare farmland with the culture's crop (wheat, potatoes in
the north and the hills, beetroot in karst and wisteria towns, carrots in the tropics and the bayou), till grass at the
field's edge (inside farm plots only), and weed. **Guards** walk a round of guard posts and gates (or the town's
quarters), answer the alarm, and fight monsters (sword and shield). **Smiths, carpenters, masons, bakers, brewers,
tailors, scholars and priests** work at their job site with their motion (hammering, crafting, reading, praying),
tools in hand, sounds (anvil, wood, stone, smoker, brewing stand, loom, pages) and particles (sparks, chips, smoke,
bubbles, enchantment glyphs), with trips to the store between bouts. **Merchants, innkeepers, minters** keep their
counter facing customers (the innkeeper also goes round the inn's tables). **Foresters** walk out beyond the town's
edge and work at a real trunk (the felling itself is the sim's: wood is abstract); **quarriers** at the rock;
**fishers** at a dock or a bank with a rod; **herders** with the herd in their pasture; **hunters** on the edges;
**couriers** between the gate and the square. Folk without a trade keep house. Children play; elders sit on benches.
Labour done while embodied is recorded for the sim (`extra("npc").labour`, see docs/requests/npc.md).

## 7. Conversation

Right-click a folk (not while they sleep, unless sneaking). The server builds the conversation and sends it
(`hearth:npc/conversation`): name, trade, settlement, a greeting and options. The client shows a parchment screen with
their face drawn from their own skin and the speech written out as they say it; picking an option sends its id
(`hearth:npc/choose`); closing sends `hearth:npc/end`. The folk stops, faces the player and gestures while talking.

The greeting is composed from templates (`data/hearth/hearth_dialogue/*.json`) to fit the moment: time of day,
weather, the player's reputation (distrust, stranger, known, friend, hero), the folk's traits and mood, trade, age and
culture, how often they have met — and, now and then, the town's news (`TownNews`, filled from `SettlementEvents`: a
building finished, construction stalled for want of stone, a new tier, a birth or arrival, a death). Built-in options:
"What's new in town?" (news), "Heard any rumours?" (other settlements by name, compass direction and distance; with
Expanse loaded, its landmarks — lighthouse, pagoda, stone circle, monastery in the clouds... — found with the structure
locator, one kind per settlement at a time, at most one search every 10 s server-wide, cached), "Tell me about
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
`about`, `about.job`, `about.spouse`, `about.children`, `about.parents`.

## 8. Quest markers

Every `marker_refresh` ticks (a slice of folk per tick) each folk asks `HearthServices.quests()` (when present) for its
marker for the nearest player within 32 blocks; synced through entity data and drawn above the head: gold **!** (offer),
gold **?** (ready to hand in), grey **?** (in progress).

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
  "talk": {"greetings": true, "marker_refresh": 40, "landmark_rumours": true}
}
```

## 11. Dev tools (before and without the sim)

```
/hearth npc spawn [job] [culture] [child|adult|elder]   a folk in front of you, living in a camp of that culture
/hearth npc sandbox [radius] [culture]                  a village from the beds and workstations built round you
/hearth npc sandbox build cottage|tower                 stake out a building for the sandbox's builders
/hearth npc sandbox clear                               remove the sandbox settlements of this dimension
/hearth npc info                                        the folk you look at: traits, mood, what they are doing, their day
/hearth npc stats                                       embodied folk and settlements
```

Build a few houses (beds, a crafting table, an anvil, a smoker, chests, stair chairs, a bell in the square), stand in
the middle and run `/hearth npc sandbox 32`: a folk per bed, a trade per workstation, couples and children, who keep
their day by the clock (`/time set` to watch them go to bed, get up, work and gather). `/hearth npc sandbox build
tower` shows a builder putting up a 12-high watchtower on scaffolding. After integration the real sim replaces the
sandbox; it is all in `npc/dev`.

Client game test: `dev.voidmc.hearth.gametest.npc.FolkPortraits` photographs line-ups of every culture and trade,
families, faces, poses, the conversation screen and a builder at work (`build/run/clientGameTest/screenshots`).

## 12. Performance

- Per tick: the manager touches at most `settlements_per_pass` settlements every `pass_interval` ticks and spawns at
  most `spawns_per_tick` entities; markers are refreshed a slice at a time.
- Per folk: a decision every 2–12 ticks by distance to players; one threat query and one schedule read per second;
  paths at most once per cooldown; block reads only in loaded chunks; POI scans rationed server-wide.
- Records do nothing per tick. A settlement nobody is near costs the npc module nothing.
- The client composes each distinct look once and frees textures nobody sees.
