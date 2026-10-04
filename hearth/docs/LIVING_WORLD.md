# VOID Hearth — the living world (phase 3 design)

Status: **agreed design, not yet implemented.** Phases 1–2 built the machinery: settlements that grow while you are
away, folk with routines who really build, coin, need-driven quests, and a catalogue of 71 hand-crafted building types.
Phase 3 makes the world hold together the way a AAA open world does — *Kingdom Come: Deliverance II* is the reference
for law, reputation and stealth; *Manor Lords*, *Banished* and *Anno* for the economy — while keeping what makes
Hearth scale: **a settlement is data first**, entities and block changes are views made only where a player can see
them, and the abstract and embodied versions of everything produce the same results.

Decisions taken with the user (2026-10-03):

- Law is **full KCD**: witnesses, guards, fines, jail, confiscation, the pillory, bounties that follow you between towns.
- Folk can **die permanently**, and murder is possible.
- **Stealth is a playstyle**: sneaking, burglary, pickpocketing and lockpicking, each with its own minigame.
- **Interiors must be well lit. This is not optional.**
- Damaged buildings are **repaired** by their builders.
- Reputation is **per settlement**, layered with personal opinions and regional fame.
- **Towns thrive on the resources around them**: a full cycle of gathering, processing, building and trade.
- **More architecture**: more building types, more styles, and buildings for different people (§8).
- Defaults until the user says otherwise: towns are kept safe by **lighting alone** (no spawn blocking, so the edges and
  roads are dangerous at night); reputation is **per player**, with an optional "company" that shares standing.

---

## 1. Light (every building, always)

**Rule.** Every walkable cell inside a building has block light **≥ 8**; no cell anywhere in a plot has block light 0
(vanilla spawns hostile mobs only at block light 0, so a lit home is a safe home).

**Craft, not torch spam.** Light comes from fixtures that belong in the culture: lantern chandeliers on chains, wall
sconces, candles on tables and sills (1–4 per block), hearths and stoves, lanterns at every door and gate, Expanse's
glowing blocks (prismite, glowcaps, lumenwood) where the culture uses them, and hidden light the way skilled builders
do it (shroomlight or glowstone under carpet, in floor recesses, behind lattice). Vanilla's invisible `light` block may
fill only the cells fixtures cannot reach (behind furniture, under stairs), at the lowest level that clears them.

**Enforced.** `BlueprintChecks` gains a light pass: flood block light from every emitter in the blueprint through
non-opaque blocks over the plot's natural terrain; fail on any interior walkable cell below 8 and any plot cell at 0.
It runs in `BlueprintInvariantsTest` over every archetype × culture × terrain × seed, so a dark room can never ship.
The kit gets a `Lighting` component that lights a room to the rule given its plan (archetypes call it; it chooses
culture-appropriate fixtures and only falls back to hidden light). After the phase 2 architects merge, one sweep brings
all 71 archetypes to the rule.

**Streets.** Squares and main streets are lamp-lit to a tier standard (camp: braziers at the heart; city: every
street); lanes and outskirts are darker on purpose. Night in a town is warm windows and lamp pools; night on the road
is dangerous. (VOID LOD can later show distant towns' lit windows after dark.)

## 2. Repairs

The blueprint is the truth, so damage is the difference between the blueprint and the world.

- **Detection** is event-driven: player block breaks, explosions and fire inside plots mark damaged positions on the
  plot. Embodied builders also make slow inspection rounds (a few hundred positions per second, budgeted) to catch
  anything else.
- **Repair queue** per plot, ahead of new construction. Repairs cost goods and labour like any build, with scaffolding
  and the builder's comment ("some fool smashed the chapel window"). Abstract settlements repair on chunk load.
- **What counts:** a missing or changed blueprint block is restored; a player's deeded property is repaired only if the
  owner pays; blocks a player leaves inside someone's house are cleared as litter only where they block a door or path.
- **Disasters** — fire through a timber street, a creeper in the market — become events: a bucket brigade, rebuilding
  over the following days, quests to help, and reputation for those who do.
- Repairs of vandalism also feed the law (§4): someone did this.

## 3. Reputation (three layers)

| Layer | Stored | Moved by |
|---|---|---|
| **Personal** — each resident's opinion of each player, from a short decaying memory ("helped me", "stole from me", "saved my son") | `Resident.extra("npc")`, bounded per resident | conversation, gifts, quests, crimes against them or theirs |
| **Settlement** — standing per player per town, −100…100, with titles: Outlaw, Distrusted, Stranger, Known, Friend of ⟨town⟩, Burgher (owns property), Hero | settlement data | quests, trade, donations, defending the town, supplying its builds, crimes |
| **Regional fame** — deeds travel as gossip along the roads with couriers and caravans, arriving days later, fading with distance | world data (a delay queue) | great deeds and great crimes |

**What it changes:** prices and haggling room, quests offered, greetings and dialogue options, the right to buy
property, whether the gate opens at night, whether guards search you, invitations (weddings, festivals, the council),
and how a court treats you.

**Appearance** (KCD's charisma, Minecraft-sized): clean (the bathhouse) and in local dress is charming; armour on and a
weapon drawn is intimidating (helps threats, hurts talk); bloodied from a fight puts folk off.

**Company** (optional, per server): players may form a company that shares a fraction of each member's standing.

## 4. Law — full KCD

**Ownership.** Everything in a settlement belongs to someone: household containers, beds and food to the family;
a workshop's stock and tools to its worker; crops, animals and resource sites to the settlement (or their worker);
squares and roads are public. The container screen and the HUD show it ("Owned by Edda Miller", KCD's red hand).

**Crimes:** theft (containers, crops, animals, pickpocketing), trespass (an occupied home at night, a locked place),
vandalism, assault, murder, robbery of caravans, breaking out of jail.

**Witnesses.** A crime counts only if seen by an awake, embodied folk within range and line of sight (or discovered:
a body found, a lock found broken, a missing item noticed later — discovery makes the player a suspect, not convicted).
Detection depends on light (§1: a lit room is a dangerous room to rob), distance, sneaking, noise, armour and the
witness's traits. Witnesses shout, flee to a guard, or — if brave — confront; they can be bribed, threatened or
silenced before they report.

**Stolen goods** carry a `hearth:stolen` component (owner, settlement, day). That town's merchants will not buy them;
a **fence** will (a miller or innkeeper in some towns, a thieves' den in cities), cheaply. Guards with cause stop and
search you. The mark fades with time.

**Guards and justice.** Reports raise a wanted status in that settlement (spread by gossip to its neighbours, with
wanted posters on their notice boards). Guards confront: *pay the fine* (to the treasury), *submit* (jail in the
guardhouse cell — served as real confinement time on servers, or slept through in single player — with stolen goods
confiscated, or the **pillory** in the square for lesser crimes, a public reputation hit), *persuade*, *bribe*,
*resist* (the bounty grows, guards hunt you), *flee*. Murder is a hanging offence: unpaid, it makes you an outlaw who
is attacked on sight in that region. Crimes need folk present, so the law costs nothing in towns nobody is near.

**Stealth as a playstyle.**
- **Sneaking**: crouched, in shadow, unarmoured, quiet; folk have sight cones and hearing.
- **Burglary**: homes and shops lock at night (doors and chests carry a lock state; keys exist and can be stolen or
  pickpocketed).
- **Lockpicking**: a minigame (pick sweet spot under tension, KCD-style), harder for better locks; picks break.
- **Pickpocketing**: a minigame against the folk's awareness (time-limited, risk rising the longer you take);
  sleeping folk are easier.
- **Consequences** flow through §3–§4: a career thief is rich and hunted.

**Servers.** Admins and creative mode exempt; PvP inside towns configurable; players cannot report or frame each
other; every rule has a config switch for server owners.

## 5. Folk that feel alive (beyond schedules)

- **Speech checks** in conversation: persuade, intimidate, bribe, flatter, with the odds shown, decided by reputation,
  their opinion, your appearance and their traits (the greedy take bribes, the brave shrug off threats).
- **Gossip** between folk about the player and town events, which is also how reputation travels.
- **Haggling** at the market: push too hard and the merchant sours.
- **Named officials** with their own quest lines: the reeve (town hall), the guard captain, the priest, the master
  builder, the guild masters.
- **Town life**: weekly market days with visiting traders, bells at dawn, noon and dusk, festivals at harvest and
  midsummer, weddings, funerals, births announced on the board.
- **Player influence on growth**: fund a specific project ("pay for the bridge") and the planner puts it first.

## 6. Player life

Inns (rent a bed and a room, meals, rumours), bathhouses (cleanliness), tailors (clothes and colours), stables (hire
or buy horses), tavern dice with coin stakes, and work for wages (help with a harvest, haul for the quarry).

---

## 7. Towns thrive on the resources around them

This is the principle the whole economy hangs on: **what lies around a settlement decides where it is founded, what it
builds with, how large it can grow, what it trades, and what becomes of it when the land runs out.**

### 7.1 Territory and survey
Each settlement holds a **territory** (a radius growing with tier, never overlapping another's core). At founding and
as it grows it **surveys** the territory with the terrain probe and the biome data — no chunk loads:

- **woods**: tree density and species from the biome's vegetation features (the same reading VOID LOD uses for its
  forest blanket), so a redwood valley and a birch hillside are told apart;
- **stone**: exposed rock, cliffs, slopes and the surface rock type (stone, deepslate at depth, sandstone, Expanse
  limestone, opal sandstone, basalt/tuff on volcanoes);
- **ore**: estimated by depth and biome from the generator's ore distributions; on Expanse Earth worlds the cavern model
  tells where tunnels and realms lie, so mines can aim for them;
- **clay, sand, salt**: river banks, beaches, lake beds, Expanse salt flats;
- **fertile land**: flat, watered, temperate; **pasture**: open grass; **fish**: rivers, lakes, coast;
- **specialities** of the land: Expanse's amber, prismite, sulfur, glowcaps, mammoth wool, venison, crab; vanilla's
  bamboo, cocoa, honey, kelp.

The survey becomes a set of **resource sites** with capacity, yield, depletion and regeneration.

### 7.2 Resources shape the settlement
- **Founding**: the atlas scores sites by water, buildable ground **and resource richness**; settlers go where the
  land can feed and house them. Daughter hamlets are founded **at resources** the parent lacks (a mining hamlet by the
  ore, a fishing hamlet on the coast) and supply it.
- **Character**: a settlement's job mix follows its sites — a mountain town mines and smelts, a redwood valley logs and
  saws, a coast town fishes and builds boats, a plain farms and mills. Names, festivals and guilds follow (Coldbrook
  Mine, the Salters' Guild, the harvest feast).
- **Local materials**: buildings are built from what the town gathers. Style palettes are resolved against the
  territory's sites: a town by a birch wood builds in birch, a town over limestone in limestone, a desert town in its
  own sandstone. Materials from outside arrive only by trade and cost more, so a timber town's stone church is a sign
  of wealth. (This is also the most convincing answer to "looks hand-built for this place".)
- **Carrying capacity**: food from fertile land and fish, timber from regrowth, stone and ore from what remains. A town
  grows to what its land and its trade can support; past that it must import, specialise, or send settlers away.
- **Prosperity**: rich land and good trade raise the finish tier of what a town builds (dressed stone, glass, copper,
  carved timber); poor land keeps it rough and small.
- **Boom and bust**: sites deplete. A mining town whose ore runs out declines — trade dries up, folk emigrate, houses
  are **abandoned** and slowly decay into ruins (and become places to explore and loot, at the risk of the law if
  anyone still owns them). Forests replanted regrow; overgrazed pasture recovers when rested.

### 7.3 Extraction is construction run backwards
A quarry pit, a mine adit with its galleries, a clay pit and a woodlot plantation are **blueprints whose steps are
cuts** (air steps, plus the supports, rails, lanterns and spoil heaps that go with them). That reuses the whole
pipeline: the single progress counter, the chunk applier and its edit safety, abstract and embodied progress, and the
contract that a worker places exactly the next step. Over weeks a quarry becomes a terraced pit with a derrick; a mine
pushes into the hill, rails and props and lamps going in behind it, until it breaks into an Expanse cavern; a forest
edge recedes to stumps and rows of saplings. **Coming back after a month, the land around a town has changed.**

What a cut yields is what it removed: stone by rock type, ore by the estimated distribution along the way (abstract),
logs by the trees felled.

### 7.4 Gatherers
| Trade | Near a player (embodied) | Nobody near (abstract) |
|---|---|---|
| Forester | fells real trees whole, top to bottom, replants saplings, hauls logs to the yard | woodlot yield by workers and trees; when the chunk loads, trees come down and saplings go in |
| Quarrier, miner | walks into the pit or tunnel and carves its next steps, carts on rails, lanterns going up | the excavation counter advances; cuts applied on chunk load |
| Clay digger, salter | digs the bank or the pan | site yield |
| Farmer, herder, fisher | real harvests, shearing, milking, fishing from the dock | yield by fields, flock, waters |
| Hauler | carries crates and logs between site, store and workshop (carts where there are roads) | instant within the town, limited by storage |

Same amounts either way — the same contract the builders keep.

### 7.5 Production chains
Goods grow from 10 categories to about 25–30 real goods, and blueprint bills ask for **processed** materials, so the
chains matter:

| Raw | Workshop | Output → used by |
|---|---|---|
| logs | sawmill, carpenter | planks, beams → construction, furniture |
| logs | charcoal kiln | charcoal → smelting, forges |
| stone | mason | dressed stone, bricks → civic buildings, walls |
| clay, sand | brick kiln, glassworks | bricks, terracotta, glass → roofs, windows |
| ore + charcoal | smelter → smith | ingots → tools, nails, chains, lanterns |
| wheat | mill → bakery | flour → bread |
| livestock | butcher, tanner, weaver | meat, leather, wool, cloth → food, clothes, beds |
| honeycomb, sugar cane | chandler, paper maker | candles, books → **the lighting of every interior**, libraries |

Tools wear out (smiths stay busy); food spoils without a granary; a town short of iron or wax struggles to light its new
houses (§1 is part of the economy).

### 7.6 Trade between towns
Settlements specialise and trade along the roads: **caravans** (abstract when nobody is near; met on the road when
someone is) carry surplus to where it is short. Prices differ between towns — a player can run a merchant career —
and caravans make quests (escort) and crimes (robbery, §4). Daughter hamlets ship their resources to the parent.

### 7.7 Money through people
Residents get **purses**: wages flow to them and they spend at the inn, the baker and the tailor, which is real demand.
Shops keep real **stock and limited cash** (as KCD's merchants do), so haggling matters. Taxes fund public works. The
player can own a workshop or a quarry deed and draw its income, fund projects, take supply contracts, or work for
wages.

### 7.8 Guardrails
All extraction stays inside a settlement's territory and never touches player-claimed or deeded land or comes near a
player's builds; woodlots always replant (no clear-cutting); server owners can make extraction **abstract only** (no
terrain changes, the loop runs as numbers). Sites and goods are data and cuts go through the budgeted applier, so the
cost stays where it is now.

---

## 8. Architecture, continued: more buildings, more styles, buildings for different people

Architecture is the part of Hearth players see first and remember longest, so it keeps growing alongside every other
phase. Three directions:

### 8.1 Buildings for different people
Every resident gets a **station** derived from their job, household and purse: *poor* (labourers, the destitute),
*peasant* (farmers, herders, foresters), *artisan* (smiths, masons, carpenters, bakers), *merchant* (shopkeepers,
traders, bankers), *clergy*, *scholar*, *official* (reeve, guard captain), and at town and city tier *gentry* and a
**lord**. Station decides what home a household wants, and the planner asks for it: housing becomes class-aware, so
towns get real social geography — crofts and hovels by the fields, artisans living over their workshops on the high
street, merchants' houses by the market, the gentry on the hill, the lord's keep above it all, a poor quarter by the
tannery. Wealth rising through the economy (§7.7) moves a household up (an extension, another storey, a finer house)
and ruin moves it down.

| Station | Homes and places |
|---|---|
| Poor | hovel, shack, lean-to, room in a tenement, almshouse, poorhouse |
| Peasant | croft, cottage, farmhouse, longhouse, round house, stilt house |
| Artisan | house-over-workshop (a shophouse for every trade), guild hall |
| Merchant | merchant's house with counting room and warehouse, townhouse, courtyard house |
| Clergy | rectory, monastery or abbey, convent, temple compound, wayside shrine |
| Scholar | scribe's house, library, university, observatory, alchemist's tower |
| Official | reeve's house, courthouse, watch house, toll house |
| Gentry and lord | manor, keep, castle, palace, with stables, gardens, a private chapel, a hunting lodge |
| Outsiders | hermit's hut, charcoal burner's camp, travellers' and pilgrims' hostel, caravanserai, a hidden thieves' den |

Interiors are furnished for the household that lives there: a smith's home has tongs by the hearth and a cheap table;
a scholar's has books stacked on every surface; a merchant's has carpets, glass and a strongbox; a lord's has a hall
with banners and a high table. The same archetype reads differently depending on who lives in it.

### 8.2 More styles: sub-styles within each culture, and new ones
Each of the 14 cultures splits into **sub-styles** by station and purpose, the way real places do:

- **Temperate**: cottage timber-frame and thatch; burgher brick with stepped gables; Norman stone for keeps, churches
  and walls; manorial with tall chimneys and mullioned glass.
- **Northern**: log cabins; stave churches and carved timber halls; turf houses; redwood timber in the giants' valleys.
- **Desert**: adobe; dressed sandstone; a palace style with domes, arches and tiled courts.
- **Mediterranean**: farmhouse and hill-town house; the palazzo; the villa with its loggia and garden.
- **Karst**: bamboo and timber for commoners; a scholar-gentry courtyard style; temple architecture.
- …and likewise for the other nine.

New styles for the lands Expanse adds: **lumen** (lumenwood and glow, for the Lumen Grove), **cloud-forest monastic**,
**volcanic** (basalt and tuff holds on the highlands), **fjord** (boathouses and stave halls over the water), **tepui**
(lost-world hamlets on the table mountains), and **deep** settlements in the realms underground: mine camps grow into
underground towns lit by glowcaps and amber, cut into the realm walls, which adds a vertical axis no surface town has.

### 8.3 More building types
Beyond today's 71, each phase brings the buildings its systems need. Architects build them **ahead of** the system that
uses them:

- **Economy (§7)**: mine (adit and headframe), shaft, charcoal kiln, brick kiln, glassworks, smelter, tannery, butcher,
  chandler, dye works, paper mill, salt works, logging camp, tree plantation, clay pit, apiary, shipyard and boatyard,
  caravan yard, counting house.
- **Law (§4)**: courthouse, jail, gallows, stocks and pillory, watch house, toll gate.
- **Social life (§5–6)**: tavern (distinct from the inn), playhouse, tilt yard or arena, infirmary and healer's house,
  almshouse, orphanage, cemetery with a chapel, festival grounds.
- **Station (§8.1)**: the homes and places in the table above.
- **Decay**: an abandoned and a ruined state for every archetype (boom and bust, §7.2), built as a decay pass over the
  blueprint: collapsed roofs, missing doors, vines, moss and rubble.
- **Dressings**: market-day stalls, festival decorations, wedding garlands, mourning black, and winter dressings in the
  cold cultures, applied over buildings on the days they apply.

### 8.4 Variety and growth over time
Within one archetype, variety comes from the seed, the plot, the terrain, the culture and sub-style, the station and the
household, the tier, the age (weathering), the owner's trade (a smith's anvil out front, a fisher's nets on the wall),
and **accretion**: houses gain extensions, lean-tos, dormers and storeys as their families grow and prosper, instead of
always being torn down and rebuilt. A street that grew over generations is never uniform. Town previews get a
similarity check that flags neighbours that look copied.

### 8.5 How quality is held
- A **style bible** per culture and sub-style (materials, roof forms, proportions, details, colour), with reference
  renders, kept in `docs/buildings/styles/`.
- A **review gate** before anything merges: contact sheets, streets and whole towns, then in-game screenshots by day
  and night with Expanse blocks and real lighting. Weak work goes back for another pass (as the inn, the bayou stilt
  house, the cathedral's spire and the windmill's sails will after phase 2).
- An **art-director** agent at the highest effort for the large sweeps: it reviews renders against the bible and files
  critiques that architects work through.
- Every new building passes the automated checks (bounds, nothing floating, reachability, and the light rule of §1).

---

## 9. Fit with the architecture

| Concern | Lives in | Notes |
|---|---|---|
| Light rule, check, `Lighting` component | build | then one sweep over all archetypes |
| Street lighting by tier | build (infra, landscape) + plan | |
| Damage tracking, repair queue | sim | event-driven; abstract repair on chunk load |
| Repair work | npc (builders) | repairs before new work |
| Reputation layers, gossip queue | new **standing** service (api) implemented in sim | per player per settlement; personal memory in `Resident.extra` |
| Ownership, crimes, witnesses, guards, justice, stolen marks, locks | new **law** module | embodied only; config per server |
| Stealth minigames (lockpicking, pickpocketing), sneaking detection | law (server) + client screens | |
| Speech checks, haggling, gossip, officials, town events | npc + economy + quest | |
| Territory, survey, resource sites, depletion, carrying capacity | sim | probe- and biome-based; no chunk loads |
| Excavation blueprints (quarry, mine, clay pit, woodlot) | build | cut-steps, xhigh architects |
| New workshops (charcoal and brick kilns, glassworks, smelter, tannery, butcher, chandler, logging camp, mine, apiary) | build | xhigh architects |
| Goods and recipe model | api | breaking change to `Goods` — before any release, so fine |
| Production chains, storage, spoilage, tools | sim | |
| Gatherers and haulers | npc | same contract as builders |
| Caravans, inter-town trade | sim (abstract) + npc (embodied) | |
| Purses, shop stock and cash, ownership of businesses | economy | |
| Local materials (palettes resolved against sites) | build + sim | a site-aware `PlotSpec` param or style context |
| Boom and bust, abandonment, ruins | sim + build (a decay pass over abandoned plots) | |
| Station of residents and households, class-aware housing, social geography | npc (station) + plan (housing by station, districts) | station in `Resident.extra`, demand per station |
| Sub-styles, new Expanse styles, interiors by household, accretion, dressings | build | xhigh architects; style bible in `docs/buildings/styles/` |
| Underground realm towns | plan + sim + build | the cavern model gives the realm geometry |

Performance rules stand (ARCHITECTURE.md §12): no per-tick work proportional to the world; witnesses, stealth and
embodied gathering only near players; surveys and chains are cheap arithmetic on data; terrain change through the
budgeted applier.

## 10. Phases

1. **Light first** (with the phase 2 merge): the light check and `Lighting` component, then a sweep over all 71
   archetypes. Nothing ships dark.
2. **The resource economy**: goods and recipes, territory and survey, resource sites, excavation blueprints, new
   workshops (xhigh architects), gatherers and haulers, chains, storage, local materials, carrying capacity, caravans,
   purses and shops, boom and bust.
3. **Law and stealth**: ownership, witnesses, guards and justice, stolen goods and fences, locks, keys, the lockpicking
   and pickpocketing minigames, sneaking.
4. **Reputation and social life**: the three layers, gossip, speech checks, haggling, officials, town events, the
   player-life services.
5. **Repairs and disasters**: damage tracking, repair work, fires and their aftermath.
6. **Integration and the in-game tour**: a world where you can watch a town log, quarry, mine, saw, smelt, build,
   trade, light its windows, catch a thief and hang a murderer.

**Architecture runs alongside all of them** (§8): first the review-gate fixes from phase 2 and the light sweep, then
sub-styles and class-aware housing, then each phase's new buildings built ahead of the system that uses them (the mine
and kilns before the resource economy goes live, the courthouse, jail and pillory before the law), then the decay pass,
dressings, the Expanse-native styles and the underground towns.

Each phase is planned as module agents in worktrees against an updated api, as in phases 1–2; anything that places
blocks to make architecture is done by architects at the highest effort, reviewed through the gate in §8.5.
