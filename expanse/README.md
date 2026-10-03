# `expanse/` — VOID Expanse

A Fabric world-generation mod for **Minecraft 26.3** (the latest stable release, September 2026): thirteen
biomes you won't find in vanilla, six new kinds of tree, four animals, 36 structures from wayside shrines
to walled citadels, and taller mountains.

```
Fabric Loader ≥ 0.19.5 · Fabric API 0.161.0+26.3 · Java 25
```

## Gallery

These are in-game screenshots, rendered headless by the client game test (see *How this was checked*
below) on seed `8675309`.

| | |
|---|---|
| ![Wisteria Vale](docs/gallery/wisteria_vale.jpg) **Wisteria Vale** | ![Wisteria cascades](docs/gallery/wisteria_vale_cascades.jpg) Blossom cascades |
| ![Jade Karst](docs/gallery/jade_karst.jpg) **Jade Karst** | ![Jade Karst floor](docs/gallery/jade_karst_floor.jpg) Among the towers |
| ![Lumen Grove](docs/gallery/lumen_grove.jpg) **Lumen Grove** | ![Lumen Grove at night](docs/gallery/lumen_grove_night.jpg) Lumen Grove at night |
| ![Redwood Giants](docs/gallery/redwood_giants.jpg) **Redwood Giants** | ![Redwood floor](docs/gallery/redwood_giants_floor.jpg) Buttress roots |
| ![Opal Dunes](docs/gallery/opal_dunes.jpg) **Opal Dunes** | ![Willow Bayou](docs/gallery/willow_bayou.jpg) **Willow Bayou** |
| ![Prismatic Peaks](docs/gallery/prismatic_peaks.jpg) **Prismatic Peaks** | ![Frostbloom Tundra at night](docs/gallery/frostbloom_tundra_night.jpg) **Frostbloom Tundra** at night |
| ![Cloud Forest](docs/gallery/cloud_forest.jpg) **Cloud Forest** (a monastery in the mist) | ![Amber Steppe](docs/gallery/amber_steppe.jpg) **Amber Steppe** |
| ![Palm Coast](docs/gallery/palm_coast.jpg) **Palm Coast** | ![Animals](docs/gallery/animals.jpg) Elk and fawn, capybara, shore crab, woolly mammoth |

**New biomes, realms and wildlife**

| | |
|---|---|
| ![Larch Taiga](docs/gallery/larch_taiga.jpg) **Larch Taiga** | ![Aspen Parkland](docs/gallery/aspen_parkland.jpg) **Aspen Parkland** |
| ![Ghost Gum Outback](docs/gallery/ghost_gum_outback.jpg) **Ghost Gum Outback** | ![Saguaro Flats](docs/gallery/saguaro_flats.jpg) **Saguaro Flats** |
| ![Olive Groves](docs/gallery/olive_groves.jpg) **Olive Groves** | ![Salt Flats](docs/gallery/salt_flats.jpg) **Salt Flats** (a landform) |
| ![Geode Vaults](docs/gallery/realm_crystal.jpg) A crystal realm underground | ![Amber Hollows](docs/gallery/realm_amber.jpg) An amber realm |
| ![Stalagmite Deeps](docs/gallery/realm_dripstone.jpg) A stalagmite realm | ![Wildlife](docs/gallery/wildlife.jpg) Songbirds, butterflies, egret, dragonflies |
| ![Maple Highlands](docs/gallery/maple_highlands.jpg) **Maple Highlands** | ![Bluebell Woods](docs/gallery/bluebell_woods_floor.jpg) **Bluebell Woods** under the beeches |

**Landforms**

| | |
|---|---|
| ![Volcano](docs/gallery/landform_volcano.jpg) A volcano's lava lake (**Volcanic Highlands**) | ![Fjord](docs/gallery/landform_fjord.jpg) An arm of the sea between the walls (**Fjordlands**) |
| ![Tepui](docs/gallery/landform_tepui.jpg) A **tepui**, its springs pouring down the cliff | ![Canyon](docs/gallery/landform_canyon.jpg) A river down the **Painted Canyons** |

**Structures**

| | |
|---|---|
| ![Karst temple](docs/gallery/structure_karst_temple.jpg) **Karst temple** (landmark) | ![Frostwatch Citadel](docs/gallery/structure_frostwatch_citadel.jpg) **Frostwatch Citadel** (landmark) |
| ![Cloud monastery](docs/gallery/structure_cloud_monastery.jpg) **Cloud monastery** (landmark) | ![Lighthouse](docs/gallery/structure_lighthouse.jpg) **Lighthouse** |
| ![Stilt hamlet](docs/gallery/structure_stilt_hamlet.jpg) **Stilt hamlet** | ![Ruined watchtower](docs/gallery/structure_ruined_watchtower.jpg) **Ruined watchtower** |
| ![Nomad camp](docs/gallery/structure_nomad_camp.jpg) **Nomad camp** | ![Tea garden](docs/gallery/structure_tea_garden.jpg) Tea garden (small) |
| ![Terrace farm](docs/gallery/structure_terrace_farm.jpg) Rice terraces (small) | ![Bayou shack](docs/gallery/structure_bayou_shack.jpg) Bayou shack (small) |
| ![Stone circle](docs/gallery/structure_stone_circle.jpg) **Stone circle** on the moor | ![Woodland lodge](docs/gallery/structure_woodland_lodge.jpg) **Woodland lodge** in the wisteria |
| ![Tundra village](docs/gallery/structure_village_tundra.jpg) **Tundra village** | ![Karst village](docs/gallery/structure_village_karst.jpg) **Karst village** among the towers |
| ![Coast village](docs/gallery/structure_village_coast.jpg) **Coast village** on the dunes | ![Palm harbour](docs/gallery/structure_palm_harbour.jpg) **Palm harbour** (landmark) |
| ![Logging camp](docs/gallery/structure_logging_camp.jpg) **Logging camp** with its millpond (landmark) | ![Caravanserai](docs/gallery/structure_caravanserai.jpg) **Caravanserai** (landmark) |

**Underground**: the realms, with dripstone and the falls of the underground rivers

| | |
|---|---|
| ![Cavern landing](docs/gallery/structure_cavern_landing.jpg) **Cavern landing** | ![Deep mine camp](docs/gallery/structure_cavern_mine_camp.jpg) **Deep mine camp** |
| ![Deepstone ruins](docs/gallery/structure_deepstone_ruins.jpg) **Deepstone ruins** | |

**Earth terrain**

![Earth terrain map](docs/gallery/earth_map.jpg)

*24 × 24 km of the Earth terrain (`./gradlew terrainPreview -Pradius=12288 -Pstep=24`): a continent of
several plates with collision ranges across it, coastal ranges along its western shore, an inland sea,
open ocean with volcanic island chains, and the river network draining every basin to the sea.*

| | |
|---|---|
| ![River](docs/gallery/earth_river_0.jpg) A river through the forest, cut into the hillside | ![River](docs/gallery/earth_river_1.jpg) Past a wisteria grove, with a sandbar |
| ![River](docs/gallery/earth_river_2.jpg) Across the amber steppe | |

The biome shots above were taken on the previous terrain; the biomes are the same, the land under them
is now the Earth terrain's.

![Atlas](docs/gallery/atlas.jpg)

*`/expanse atlas` over 16 × 16 km around spawn: wisteria in lavender, lumen groves in cyan, redwoods in
rust, steppe in gold, opal dunes in pink.*

## What's in it

### Biomes

Each biome takes the *variant* half of one vanilla biome's climate cell (see
`world/biome/BiomePlacement.java`). Vanilla's biome table has a second "variant" table for places where
the weirdness noise is positive, and most of its cells are empty. The Expanse biomes fill those empty
cells. Each one borders the vanilla biome it grew from, and no vanilla biome is removed.

| Biome | Where it grows | What it looks like |
|---|---|---|
| **Wisteria Vale** | temperate forest | twisting trees hung with purple (and, one in four, blue) wisteria: blossom cascades up to six blocks long under every canopy; petals and leaf litter; lavender haze |
| **Lumen Grove** | dark forest | corkscrew lumenwood trees with leaves that glow teal; glowcaps, glow lichen, fireflies drifting all day; magical at night |
| **Redwood Giants** | cool wet taiga | redwoods 40–75 blocks tall with buttress roots and 2×2 and 3×3 trunks; fallen logs, mossy boulders, ferns, podzol |
| **Jade Karst** | hot and humid | Guilin-style limestone towers up to 76 blocks tall, with turf caps, bushes and hanging vines; jade-green ponds, bamboo, mist |
| **Opal Dunes** | hot and dry | wind-shaped dunes of pastel opal sand (long windward slopes, steep lee faces), and natural stone arches banded in pink, white and pale blue |
| **Willow Bayou** | swamp | weeping willows draped in Spanish moss; cattails, lily pads, standing water and mud; capybaras |
| **Amber Steppe** | savanna | golden grass, huge bottle-trunked baobabs and termite mounds |
| **Heather Moor** | cool plains and meadows | purple heather, edelweiss, coarse dirt and podzol, grey boulders, lone birches; misty |
| **Frostbloom Tundra** | snowy plains | packed-ice and blue-ice spires, glowing frostbloom flowers, drifting snowflakes; mammoths |
| **Cloud Forest** | warm humid plateaus | jungle trees dripping with Spanish moss, deep fog (fog ends at 72 blocks), moss carpets and ponds |
| **Verdant Peaks** | temperate mountain peaks | mountains that are green to the summit, with alpine flowers, lone spruces and waterfalls down the walls; snowcaps only above y=300 |
| **Prismatic Peaks** | warm stony peaks | calcite and snow, with glowing prismite crystal outcrops that sparkle at night |
| **Palm Coast** | warm beaches | curved palms on turquoise water; shore crabs |
| **Pine Heath** | cold dry taiga | flat-crowned Scots pines over pale lichen, juniper, cranberry, heather and pink granite boulders |
| **Larch Taiga** | snowy taiga | golden larches among dark spruce in the snow, their gold needle litter melting the snow beneath them |
| **Boreal Muskeg** | cold wet taiga | a misty bog: tea-brown pools, peat and red-green sphagnum hummocks, stunted black spruce, cotton grass, frogs |
| **Maple Highlands** | cool forested uplands | domed sugar maples in scarlet and orange, golden grass, red leaf litter, fallen maples with bracket fungus |
| **Bluebell Woods** | cool forested lowlands | a high grey-barked beech canopy over a carpet of bluebells, under a blue haze |
| **Aspen Parkland** | temperate plains | groves of tall white aspens with gold crowns, open prairie of fireweed, wild roses and wildflowers |
| **Olive Groves** | warm lowlands | gnarled silver-green olives and dark cypress spires, dry-stone walls, lavender in rows, poppy meadows |
| **Monsoon Forest** | warm uplands | tall teak with layered crowns and lianas, scarlet flame trees, granite tors, a hazy golden light |
| **Ghost Gum Outback** | hot dry savanna | red earth, leaning white ghost gums, spinifex, cathedral termite spires, Sturt's desert peas |
| **Saguaro Flats** | hot desert and badlands | many-armed saguaros crowned with flowers, ocotillo, barrel cacti, mesquite, marigold superblooms, hoodoos |
| **Kapok Rainforest** | hot wet lowlands | emergent kapoks up to 40 blocks with buttress roots over a jungle canopy, orchids, pitcher plants, oxbow ponds |
| **Coral Coast** | warm beaches | coral sand and aqua water, tiered sea-almond trees, coral in the shallows, turtle nests, crabs |

Most sit within about 5 km of spawn on a typical seed. They also join the vanilla biome tags
of the biome they grew from, so villages, temples, mineshafts and mob variants appear in them the way
they would in that vanilla biome.

### Earth terrain

The overworld is made by a chunk generator of this mod's own, `expanse:earth`. It builds Earth-like land
from plate tectonics and erosion rather than from noise alone:

- **Tectonic plates.** The world is divided into plates about 4 km across (a jittered, domain-warped
  Voronoi diagram). Each plate is continental or oceanic, and continental plates cluster into continents.
  Each plate drifts, and what happens at a border depends on how the two plates move:
  - Two continents colliding raise a mountain range along the border.
  - A continent overriding an ocean plate raises a coastal range a little inland, above a trench.
  - Two ocean plates colliding throw up an island arc.
  - Ocean plates pulling apart leave a mid-ocean ridge on the sea floor.

  A continent with no ocean beside it gets an inland sea.
- **Erosion.** Each plate is worked out on a 32-block grid with the stream-power law of fluvial erosion,
  solved for its steady state:
  - Water drains from every point to the sea along a drainage tree, built by a priority flood from the
    coast with a gentle seaward tilt, so no river ever ends in a pit.
  - How much land drains through each point sets how steep the land there can stand. Small catchments
    stand steep (ridges, mountain flanks); great rivers run almost flat.
  - Heights are built up the tree from the sea, then the tree is rebuilt from the new heights, and the two
    are repeated until they agree.

  The results are sharp ridgelines, branching valleys and concave river profiles. Interior basins stay
  low, as plains, and old worn ranges cross the continents.
- **Rivers that reach the sea.** Wherever enough land drains through a point there is a river. Each one:
  - widens downstream, from 3-block brooks to 30-block rivers;
  - follows smooth curves between grid points, wandering either side;
  - holds real water above sea level, falling stretch by stretch toward the sea in small rapids;
  - sits in a valley floor with firm banks and a gravel, sand and clay bed.
- **Biomes follow the land.** The climate values the biome source reads come from the terrain:
  - continentalness from distance to the coast;
  - erosion from how mountainous the land is;
  - peaks and valleys from height above the nearest river.

  Temperature falls with altitude. Beaches meet the sea, river biomes follow the rivers, and peaks get
  peak biomes. Temperature and humidity are computed by the terrain model too (vanilla's noises and
  proportions), so the land knows its own climate.
- **Landforms that bring their own biome.** Some land is shaped by more than rivers, and the shape decides
  the biome (`world/terrain/Landform.java`). The terrain model tags those columns with a weirdness nothing
  else reaches, and the biome source holds a point there for each, so they are ordinary biomes to locate,
  structures and features:
  - **Volcanic Highlands:** stratovolcanoes along the arcs where a continent rides over an ocean plate,
    on the hotspot islands, and now and then alone far inland. Their uplift goes into the erosion model,
    so rivers drain off them like real ones, and a cone with radial gullies stands on top. Some craters
    hold lava, some a lake. The upper slopes are ash and tuff, with basalt flows edged with obsidian and
    fumaroles breathing smoke.
  - **Painted Canyons:** dry uplands terraced into mesas and buttes, with every cliff banded like the
    badlands and the rivers sunk in sheer canyons.
  - **Salt Flats:** in arid lowlands, dead-flat white salt crust with pink brine pools and salt chimneys.
  - **Tepuis:** in the wet tropics, sheer table mountains standing out of the forest. Their tops are a
    misty lost world, and springs pour down the cliffs.
- **A world underground.** Under the Earth terrain the underground is the Expanse's own: no vanilla noise
  caves, no carvers, no vanilla cave biomes. (Other world types, such as Large Biomes, Amplified or a world
  with the Earth pack switched off, keep vanilla's caves with their lush caves, dripstone and deep dark.)
  Beneath the land runs a network to follow (it is not open space everywhere), from
  small passages to great tunnels to realms, landscapes of their own under a stone sky:
  - **Ways in:** cave mouths at the foot of hillsides and river banks, an adit running level into the hill
    and then down; sinkholes, funnels over a shaft that drops into a chamber; ravines, where a fissure
    breaks the surface. None opens under the sea, in a river or a lake, or on a volcano.
  - **Passages:** three levels of winding, branching passages (a sparse network of links over a jittered
    grid, so they fork, loop and end): a shallow one 30–50 blocks down that climbs with the land under
    mountains, one at the depth of the great tunnels that bends to meet them wherever they pass, and a
    deep one that comes out in the walls of the realms. Some junctions open into chambers 20–90 blocks
    across; a few deep ones sink into bowls of lava.
  - **Fissures:** tall narrow cracks through all the levels, some down into the lava.
  - **Great tunnels:** two families, a few hundred blocks apart, 12–24 blocks wide and up to 34 high under
    arched roofs, rising and falling between y 12 and 44.
  - **River tunnels:** one family carries rivers, with a channel down the middle, a dry ledge either side
    and waterfalls where the level drops.
  - **Realms:** about one every 1.5 km, 110 to 440 blocks across, deep in the deepslate under vaults up to
    130 high. A dry tunnel comes out high on a realm's wall with a scree ramp down from its mouth; a river
    pours out of its tunnel in a waterfall, into a plunge pool it has scoured. Below:
    - hills, ridges and valleys, or terraced mesas, rising in rubble slopes to the walls;
    - lakes in the low ground, and lava seas in the deepest realms;
    - stalagmite mountains, the tallest reaching the roof as pillars;
    - inverted peaks hanging from the vault, and great hourglass columns;
    - a plateau in the middle, where an underground structure may stand.
  - **Twelve characters, after the land above:** a realm takes its character from the climate, the sea,
    the mountains and the volcanoes over it (with chance enough that any can turn up anywhere), and each has
    a biome of its own (its music, creatures, fog and motes):
    - *Glowcap Wilds:* moss hills under a forest of giant mushrooms, lit by the shroomlight in their gills.
    - *Verdant Hollow:* moss, azaleas, clay ponds of dripleaf, spore blossoms, vines hanging thick.
    - *Stalagmite Deeps:* stalagmite fields and hanging spires, terraced flowstone, still pools.
    - *Geode Vaults:* terraced mesas of calcite and tuff, crusted with amethyst and glowing prismite.
    - *Ember Deeps:* the deepest, basalt and magma shores round a sea of lava.
    - *Sunless Mere:* an underground lake with islands and stacks, beaches, and sea pickles on its bed.
    - *Rime Hollows* (under snow country): a frozen lake, snow and packed and blue ice, ice spikes,
      icicles hanging from an ice-clad vault, frostblooms glowing in the snow.
    - *Root Hollows* (under forests): giant deeproot roots coming down out of the vault and arching across
      it, rooted earth and moss, ferns and firefly bushes, hanging roots and glowroots, lily-padded ponds.
    - *Fossil Deeps* (under deserts and dry country): dry sand and sandstone mesas and dunes, hoodoos,
      the bones of giants lying half buried, suspicious sand to brush round them.
    - *Tidal Grottos* (near the coasts): flowstone terraces stepping down to a still salt lake, tide pools on
      every step with sea pickles, seagrass and coral, kelp in the lake, glowworm silk hanging from the roof.
    - *Amber Hollows* (under conifer woods and the steppe): glowing amber with insects caught in it, amber
      spikes and boulders, resin, honeycomb, and pools the colour of honey.
    - *Brimstone Vaults* (under volcanoes and mountains): basalt causeways, sulfur and its spikes, hot
      springs whose potent sulfur erupts in geysers, lava welling up in vents.
  - **The caves between** take after their own biomes (Mossgrown Caves, Dripstone Grottos, Echoing
    Depths and Sulfur Seeps, where vanilla's cave biomes would be; `world/biome/EarthBiomeSource.java` swaps
    them in for the Earth generator alone) or after the land above: snow and icicles
    under the cold lands, sand under the deserts, moss under the wet ones, bare stone elsewhere, with glow
    lichen.
  - **Light:** glowcaps, glow berries, shroomlight, sea pickles, prismite, amber, glowroots, glowworm silk,
    frostblooms, magma, glow lichen and lava light the realms in places, so they are a dark land of lit
    places rather than a black void.
  - **Dry:** the aquifers flood nothing (through their `exclusion`), so the water and lava lie where the
    model puts them; below y -54 every open space is lava, as in vanilla.
  - **Safety:** apart from the ways in, nothing comes within 16 blocks of the ground above or lies under
    the sea.
  - **Preview:** `./gradlew terrainPreview -Pslice=plan` maps it from above (realm floors hill-shaded and
    tinted by character, passages by level, chambers, fissures, ways in and sinkholes) and prints the
    realms by character; `-Pslice=side -Pcx=… -Pcz=…` draws a section through the ground; `-Pslice=<y>`
    cuts through everything at one height; `-Pslice=cavebench` times the cavern model per column.
- **Vanilla where vanilla is good.** Aquifers, ore veins, surface rules and structures are vanilla's,
  running on the new terrain (the generator wraps vanilla's noise generator).

It lives in a built-in data pack, **VOID Expanse: Earth**, **on by default**, which you can switch off in
*Create World → More → Data Packs* to get vanilla terrain with Expanse biomes. The pack also raises the
overworld's build limit to **y=384**, for mountain ranges that need the room.

> The Earth terrain changes how the overworld is made, so use it on **new worlds**. Don't add it to a
> world that was created without it.

`./gradlew terrainPreview -Pradius=8192 -Pstep=16` renders the model straight to
`build/terrain_preview.png` in a few seconds, without starting the game. The F3 screen shows the plate,
uplift, distance to the coast and the nearest river.

### Trees (custom trunk and foliage placers)

| Tree | Shape |
|---|---|
| Redwood | A tall bare column. The upper crown has level branches that spiral round the trunk and get shorter as they climb, so the tree reads as a cone, with a spire of needles on top. Saplings planted 2×2 grow giants. |
| Willow / Wisteria | A short wandering trunk opening into a ring of arching branches. Each branch carries a dome with curtains hanging from its rim. The curtains are cut so every leaf stays within six blocks of a log and never decays. Below them, a `hanging_cascade` decorator hangs long columns of wisteria blossoms or Spanish moss. These are a hanging plant, not leaves, so they can run far past the six-block decay limit. |
| Palm | A trunk that bends with the square of its height, topped by drooping fronds. Palm saplings can be planted on sand. |
| Baobab | A three-wide bottle trunk with flared roots and a short crown of thick branches carrying flat leaf pads. |
| Lumenwood | A corkscrew trunk with a glowing canopy and glow lichen underneath. |

Every tree has a full wood set: log, wood, stripped log and wood, planks, stairs, slab, fence, gate,
door, trapdoor, button and pressure plate, plus leaves and a sapling. All are craftable, strippable,
burnable, and work as fuel.

### Blocks

| Group | Blocks |
|---|---|
| Limestone | limestone, its stairs, slab and wall; polished limestone; limestone bricks with stairs, slab and wall; chiseled limestone; mossy limestone |
| Opal sandstone | opal sand, opal sandstone with stairs, slab and wall; smooth, cut and chiseled opal sandstone |
| Prismite | prismite block and prismite cluster (both glow) |
| Plants | heather, edelweiss, frostbloom (glows), glowcap (glows), cattail, Spanish moss, wisteria blossoms (purple and azure), and potted versions |

### Animals

| Animal | Where | Notes |
|---|---|---|
| **Elk** | Heather Moor, Redwood Giants, Verdant Peaks, Amber Steppe, vanilla taigas | Skittish: elk keep away from anyone walking upright, so approach by sneaking or holding wheat. Drops venison. |
| **Woolly Mammoth** | Frostbloom Tundra, vanilla snowy biomes | Peaceful until hurt, then it fights back. Shear it for brown wool, which regrows in 5 minutes. Breed with hay or wheat. |
| **Capybara** | Willow Bayou, Jade Karst, Cloud Forest, vanilla swamps | Calm, swims, likes melon. |
| **Shore Crab** | Palm Coast, vanilla beaches | Scuttles sideways, breathes underwater. Drops crab meat. |
| **Deer** | Every wood in `#minecraft:is_forest` (so Wisteria Vale and Lumen Grove too), plains, cherry groves (`#expanse:habitat/deer`) | Shyer than the elk: a herd keeps 12 blocks from anyone walking upright and bolts from wolves, white tails flagged. Bucks have antlers, fawns are spotted. Breed with apples, berries, wheat or carrots; drops venison. |

Models and textures are generated together (see Tooling below), so they cannot drift apart.

### Wild life

Small ambient creatures that make the land feel lived in (`entity/life/`, `world/LivingWorld.java`). Like
vanilla's bat they are scenery rather than livestock: they turn up around whoever is there to see them
and leave once nobody is.

| Creature | Where and when | What it does |
|---|---|---|
| **Butterflies** (monarch, cabbage white, morpho, brimstone, red admiral) | By day, near flowers, on any land where rain falls (or savanna) at temperature 0.5 and up | Drift about a patch of flowers, settle on blooms with their wings slowly opening and closing, flutter off if you walk up (sneak to watch). Settle for the night and the rain. |
| **Moths** (luna, tiger) | The same places, by night | Flutter round lanterns and torches. |
| **Dragonflies** (azure, emerald, scarlet) | By day over open water, temperature 0.5 and up | Dart and hover over a stretch of river or pond they keep to, never more than a couple of blocks up; rest on the reeds and the bank. Glassy wings. |
| **Songbirds** (robin, bluebird, goldfinch, cardinal, sparrow; the snow bunting in the cold) | By day on any land where rain falls, or savanna | Little flocks that hop and peck on the grass and sit on treetops, fences and walls singing; they flit off a few blocks now and then, and burst up together with an alarm call if you walk within five blocks (two, sneaking). Roost at night. |
| **Herons and egrets** | By day in the shallows and on the banks of rivers and lakes, temperature 0.3 and up (egrets in warm country) | Stand in the water and stalk, now and then striking at fish; lift off with a croak and slow wingbeats, neck folded and legs trailing, if you come within twelve blocks, and come down further along the water. |
| **Owls** (tawny, barn; the snowy owl in the snow) | At night on treetops in the woods (`#expanse:habitat/owls`: forests, taigas, jungles, swamps, the snow) | Hoot, turn their heads to follow you, blink, and drift silently to another tree if you come close. If one is still about by day it dozes, eyes shut. |

Each has its own voice where it should: birdsong, alarm calls, owl hoots, heron croaks and wingbeats,
synthesised by `tools/gen_living.py`. Spawn eggs for all of them are in the creative tab.

**How they appear.** Vanilla spawns ambient mobs at a random height and caps them at 15 per player, shared
with the bats in the caves, which would leave a butterfly a rare sight a hundred blocks off. So once a
second, for each player, a small spawner looks at a couple of surface columns 20 to 48 blocks away, reads
the ambient spawn list there (the same biome data vanilla reads) and places the first of these creatures
whose own rules the spot meets (flowers for butterflies, shallows for herons, a treetop for an owl), up to
16 within 48 blocks (8 butterflies, 6 dragonflies, 10 songbirds, 2 herons, 3 owls). They leave once no
player is within 72 blocks, and slip away out of their hours when nobody is near enough to see.

**Where, by tag and climate.** Every spawn is added through Fabric's biome modifications by biome tag or
climate, never by naming a biome, so new biomes are covered as they join vanilla's tags or simply by
their temperature and rainfall. The same goes for vanilla's own creatures, which the Earth terrain's
rivers need in more places than vanilla puts them:

- **Fish up every river.** Rivers cross every biome, but vanilla's fish only live in the river and ocean
  biomes. Salmon (and some cod) join the cool and temperate land biomes, tropical fish the hot ones. Vanilla
  only lets them spawn at or below sea level, and the Earth terrain's rivers run above it, so they may also
  spawn in the top 13 blocks of any water open to the sky (`SurfaceWaterSpawnMixin`).
- **Frogs by the water** in the wet land biomes and the river biome. Outside the swamps
  (`#expanse:habitat/frogs_anywhere`) they spawn only within three blocks of water (`FrogSpawnMixin`), so
  they line the banks rather than the fields.
- **Turtles** on jungle and mangrove sandbars, **rabbits** in the plains and woods, **foxes** in the
  broadleaf woods: places vanilla leaves without them.
- **Fireflies.** Vanilla's firefly bush makes the fireflies (at night, or in deep shade). Clusters of them
  now line the banks of every temperate and warm river and lake, and grow under the canopy of wet woods.

### Ground cover and little things

The land between structures should never be empty. Two layers fill it:

- **Ground cover** (`world/feature/Foliage.java`) thickens the vanilla biomes where vanilla's is thin:
  undergrowth and leaf litter in forests and taigas, wildflower drifts on the plains, dry grass on the
  savannas, spruce shrubs in the snow. Every river bank in every biome gets the plants of the water's
  edge: reeds, cattails, sugar cane, lily pads, clay and seagrass. Rivers cross every biome on the Earth
  terrain, so this can't be left to the river biome. Low banks are levelled with the water so cane can
  root.
- **Little things** (`world/feature/LittleThings.java`, the `expanse:template` feature) are 42 tiny
  hand-made templates set into the ground. They come as 28 regional groups, about one every two chunks:
  - stumps and fallen logs in each local wood, mossy rocks, mushroom rings, a dead tree with a beehive
    and fox dens in the woods;
  - cairns, old walls and springs on the moors and peaks;
  - bones and wind-cut rocks in the dry country, and driftwood on the beaches;
  - traces of people: signposts, cold campfires, spilled hay, a broken cart wheel, a woodpile, a lantern
    post, a stone bench;
  - roughly one chunk in 48 hides a half-buried lost chest.

  Each piece surveys its footprint before it lands. It refuses water, steep ground, logs and other
  little things, and is bedded into the lowest column so nothing floats. A per-region processor list
  weathers each copy and turns its flower markers into the local flower: heather on the moor, edelweiss
  on the peaks, frostbloom in the snow.

### Structures

Three sizes, the way a real landscape has a few cathedrals, more manor houses, and a well or shrine at
every crossroads. Every structure is a jigsaw of interchangeable pieces, so no two copies are the same. A
processor list weathers each copy differently (moss, cracks, missing blocks, overgrowth). Each has a public
chest and a hidden one, and most have a lore book or journal.

**Landmarks.** Rare (one per 44–56 chunks of their biome) and 75–145 blocks across.

| Landmark | Biome | What's there |
|---|---|---|
| Karst temple | Jade Karst | Raised limestone court, five-storey pagoda (library, treasury, bell storey), side halls, pond garden with moon bridge, tea house, rice paddies. A secret scroll room behind the library shelves. |
| Cloud monastery | Cloud Forest | Three terraces up a slope: forecourt, cloister, church and a bell tower above the canopy. Wings, cemetery, apiary, grotto. Hidden study behind a bookcase; crypt with a spawner behind the altar. |
| Temple of the Pink Sun | Opal Dunes | Five-tier ziggurat with a sun disc, half-buried by dunes. Sphinx avenue, oasis, buried colonnade. A lever opens the sun mosaic onto a shaft down to a sealed undercroft. |
| Frostwatch Citadel | Frostbloom Tundra | Walled bailey, four towers, gatehouse with portcullis, a keep with a spiral stair up to a signal fire. Stables, smithy, inn. Ice cellar under the bear rug. |
| Palm harbour | Palm Coast | Harbour master's house, warehouse with a crane, net loft, market stalls and a meeting bell; piers run out over the water and up the beach as boardwalks; a careened brig in the boatyard. Smugglers' hold under the office rug. |
| Logging camp | Redwood Giants | Sawmill with a waterwheel in the mill race, a log flume on trestles dropping into the millpond, bunkhouse, cookhouse, ox barn, charcoal kilns. Payroll strongroom under the bearskin. |
| Wisteria manor | Wisteria Vale | Stone and timber-framed house (hall, library, dining room, kitchen wing) behind a fountain forecourt; a boathouse on the lake, stables, an orangery or kitchen garden, a folly. Wine cellar under the kitchen. |
| Caravanserai | Amber Steppe | A ruined courtyard inn with a pointed gateway, arcades and stables, half-drifted with sand that hides brushable finds; camels in the caravan camp outside. A cistern down the well. |

**Buildings.** Mid-sized; one per 26–36 chunks.

| Building | Biome |
|---|---|
| Stone circle (henge, barrow or avenue, with a crypt below) | Heather Moor |
| Ruined watchtower and its fallen outworks | Verdant Peaks, Prismatic Peaks, Heather Moor |
| Woodland lodge with its yard | Redwood Giants, Wisteria Vale |
| Stilt hamlet on boardwalks | Willow Bayou |
| Lighthouse, keeper's cottage and jetty | Palm Coast |
| Lumen shrine: a crystal spire or chapel | Lumen Grove |
| Nomad camp: yurts, dyers, a llama corral | Amber Steppe |

**Small sights.** 17 kinds, 75 variants, on two shared grids (every 12 and 14 chunks), so you pass one
every few minutes of travel. Each biome has its own: a shepherd's bothy, a mammoth dig, a tea garden, a
treehouse, a fairy ring, a bayou shack, a windpump, a sand colossus, rice terraces, a pilgrim shrine, a
miners' camp, a beach hut. Five more travel between biomes in local materials: wayside shrines, campsites,
wells, graveyards and summit cairns.

**Villages.** Seven regional villages, built on vanilla's own village machinery. They use the same pools,
jigsaw names, terrain-following streets, beds, bells, job sites, golems and cats, so villagers live,
work, trade, breed and get raided exactly as in a plains village. They join `#minecraft:village`, so
`/locate structure #minecraft:village` and explorer maps find them, and 1 in 50 is a zombie village.

| Village | Biome | Villagers | Character |
|---|---|---|---|
| Wisteria | Wisteria Vale | plains | Timber frame and plaster under tile roofs, an orchard with beehives, wisteria trees in the lanes |
| Redwood | Redwood Giants | taiga | Log cabins, a sawpit, a smokehouse, woodpiles |
| Coast | Palm Coast | jungle | Thatched palm houses on stilts, a fishing jetty, drying racks, beached boats |
| Steppe | Amber Steppe | savanna | Round ochre houses with cone roofs, market stalls, a llama and goat pen |
| Moor | Heather Moor | plains | Rubble-stone cottages under slate, a sheep fold, dry-stone walls |
| Karst | Jade Karst | jungle | Bamboo houses on limestone sills under jade tiles, rice paddies, stone lanterns |
| Tundra | Frostbloom Tundra | snow | Snow-drifted spruce cabins, a longhouse, firewood stacks |

Every Expanse biome has its villager type set (`world/ExpanseVillagers.java`), so a villager spawned,
cured or (half the time, as in vanilla) born in a Jade Karst takes the jungle look.

**Underground.** Three structures stand on the plateaus in the middle of the realms, at most one per realm,
placed by the realms themselves (`expanse:cavern_halls`, `world/terrain/CavernHallPlacement.java`):

| Structure | What's there |
|---|---|
| Deep mine camp | A timber headframe with a sheave wheel and a lift cage on chains, tents, a forge, ore carts on rails out to an ore face and a boarded adit (cave spiders). |
| Deepstone ruins | A collapsed deepslate hall: column rows, broken arches, an altar with candles and soul lanterns, gravel hiding brushable finds; a crypt behind the altar. |
| Cavern landing | A deepslate quay for the underground boats: boat shed with the old ferry, the ferryman's hut and bell, a ferry rope across. |

Structures choose their sites the way a builder would (`expanse:sited_jigsaw`,
`world/SitedJigsawStructure.java`): never on water (unless it's a stilt house or a jetty), on a cliff edge
or at the bottom of a gully, and small sights keep clear of anywhere a big structure could stand. Trees,
rock pillars, ice spires, arches and dunes in turn keep clear of every structure piece
(`clear_of_structures` placement filter, `world/StructureClearance.java`).

### Extras

- `/expanse atlas [radius] [blocksPerPixel]` (needs operator level 2) writes a shaded biome-and-elevation
  map of the area to `<world>/expanse_atlas/`. It samples the generator directly, so it covers ground
  nobody has explored yet.
- Advancements: entering any Expanse biome starts a new advancement tab. *Wanderer of the Expanse* is a
  challenge for visiting every Expanse climate biome, and *Shaped by Fire and Ice* one for the five
  landforms of the Earth terrain.

## Build

You need **JDK 25**. The Gradle wrapper (Gradle 9.7) is committed, so use it rather than a system
Gradle.

```sh
cd expanse
./gradlew build          # → build/libs/void-expanse-<version>.jar
```

To install, put the jar and Fabric API in `.minecraft/mods/`, using a Fabric 26.3 profile.

## Tooling

The JSON and the art are generated by scripts in `tools/`. The output is committed, so a normal build
doesn't need Python. Run the scripts again only when you change what they describe. Each script reads
the vanilla client jar that Loom caches after any build, and copies vanilla's own file formats from it
rather than from memory.

| Script | Writes |
|---|---|
| `tools/gen_assets.py` | Blockstates, models, item definitions, loot tables, recipes, recipe advancements, block and item tags, lang. Each block is cloned from the vanilla block it is shaped like (oak, tuff, sandstone, poppy…). |
| `tools/gen_worldgen.py` | Tree and terrain features, placements, the 13 biomes, surface rules, biome tags, advancements and the Earth terrain pack |
| `tools/gen_entities.py` | Entity geometry (`models/entity/*.json`, read at runtime by `JsonEntityModels`), the texture painted onto exactly that UV layout, and the entity loot tables |
| `tools/gen_living.py` | The wild creatures: their models and a skin per colour variant (on gen_entities.py's machinery), spawn egg sprites, loot, synthesised sounds (`sounds.json` and `.ogg`, via ffmpeg), the firefly features and the habitat biome tags |
| `tools/textures/gen_textures.py` | Every block and item texture, plus the mod icon, all drawn procedurally and deterministically |
| `tools/structures/gen_structures.py` | Every structure: templates (`.nbt`) built by `designs/*.py`, template pools, processor lists, structure sets, chest loot and tags. It validates its output against vanilla's block states and can render isometric previews (`--preview DIR`). |

### How this was checked without a monitor

- **Dedicated server smoke test.** Running the server with `-Dexpanse.dev.locate=6000
  -Dexpanse.dev.generate=true -Dexpanse.dev.atlas=8000:32 -Dexpanse.dev.stop=true` locates every Expanse
  biome (and, with `-Dexpanse.dev.structures=120`, every structure) and generates full chunks around each one, so every feature, tree and spawn rule runs. It then
  writes an atlas, logs the share of the map each biome covers, and shuts down (`command/DevHarness.java`).
  It also logs which cave biomes the world has (the Expanse's under the Earth terrain, vanilla's elsewhere),
  and `-Dexpanse.dev.visit=X,Z;X,Z` generates full chunks round chosen places and logs what grows there.
- **Client biome tour.** `src/gametest/` is a Fabric client game test. It creates a world, maps the biomes
  for 6 km round spawn, flies to the middle of each biome's largest patch (not its nearest sliver), and
  saves noon, ground-level and night screenshots. It then locates every structure and photographs it from
  whichever of eight directions has the clearest line of sight (`-Dexpanse.tour.only=a,b` limits it to the
  named structures), and stands back from the nearest volcano, canyon, salt pan, tepui and fjord to take
  each one whole. Run it headless with
  `SDL_VIDEO_FORCE_EGL=1 xvfb-run ./gradlew runClientGameTest`. The EGL setting is needed because 26.3
  creates its window with SDL3 and asks for an sRGB framebuffer, and Xvfb's GLX visuals don't offer one.

## Layout

```
src/main/java/dev/voidmc/expanse/
├── Expanse.java                 entrypoint: registries, wood behaviour, vanilla spawns, Earth pack
├── registry/                    blocks (WoodSet builds a whole tree's set), items, tree growers, creative tab
├── block/                       the two blocks that need their own rules (palm sapling on sand, frostbloom on snow)
├── world/biome/                 biome keys, BiomePlacement (where they go in the climate table), EarthBiomeSource
├── world/tree/                  5 trunk placers, 3 foliage placers, the hanging-cascade decorator
├── world/feature/               karst pillar, natural arch, crystal outcrop, dunes
├── world/terrain/               the Earth terrain: plates, erosion, rivers, density function, chunk generator
├── entity/                      elk, mammoth, capybara, crab
│   └── life/                    the wild creatures: butterflies, dragonflies, songbirds, herons, owls, deer
├── mixin/                       OverworldBiomeBuilderMixin: one hook, every overworld biome entry passes through it
└── command/                     /expanse atlas and the headless dev harness
src/client/java/…/client/        JSON-driven entity models, animations, renderers
src/gametest/                    the biome tour (not shipped)
src/main/resources/resourcepacks/earth/   the Earth terrain data pack
tools/                           generators (see above)
```
