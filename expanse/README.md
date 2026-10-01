# `expanse/` — VOID Expanse

A Fabric world-generation mod for **Minecraft 26.3** (the latest stable release, September 2026): thirteen
biomes you won't find in vanilla, six new kinds of tree, four animals, eleven buildings, and taller
mountains.

```
Fabric Loader ≥ 0.19.5 · Fabric API 0.161.0+26.3 · Java 25
```

## What's in it

### Biomes

Each biome takes the *variant* half of one vanilla biome's climate cell (see
`world/biome/BiomePlacement.java`). Vanilla's biome table has a second "variant" table for places where
the weirdness noise is positive, and most of its cells are empty. The Expanse biomes fill those empty
cells. Each one borders the vanilla biome it grew from, and no vanilla biome is removed.

| Biome | Where it grows | What it looks like |
|---|---|---|
| **Wisteria Vale** | temperate forest | twisting trees hung with purple (and, one in four, blue) wisteria curtains; petals and leaf litter; lavender haze |
| **Lumen Grove** | dark forest | corkscrew lumenwood trees with leaves that glow teal; glowcaps, glow lichen, fireflies drifting all day; magical at night |
| **Redwood Giants** | cool wet taiga | redwoods 40–75 blocks tall with buttress roots and 2×2 and 3×3 trunks; fallen logs, mossy boulders, ferns, podzol |
| **Jade Karst** | hot and humid | Guilin-style limestone towers up to 76 blocks tall, with turf caps, bushes and hanging vines; jade-green ponds, bamboo, mist |
| **Opal Dunes** | hot and dry | pastel opal sand, and natural stone arches banded in pink, white and pale blue |
| **Willow Bayou** | swamp | weeping willows draped in Spanish moss; cattails, lily pads, standing water and mud; capybaras |
| **Amber Steppe** | savanna | golden grass, huge bottle-trunked baobabs and termite mounds |
| **Heather Moor** | cool plains and meadows | purple heather, edelweiss, coarse dirt and podzol, grey boulders, lone birches; misty |
| **Frostbloom Tundra** | snowy plains | packed-ice and blue-ice spires, glowing frostbloom flowers, drifting snowflakes; mammoths |
| **Cloud Forest** | warm humid plateaus | jungle trees dripping with Spanish moss, deep fog (fog ends at 72 blocks), moss carpets and ponds |
| **Verdant Peaks** | temperate mountain peaks | mountains that are green to the summit, with alpine flowers, lone spruces and waterfalls down the walls; snowcaps only above y=300 |
| **Prismatic Peaks** | warm stony peaks | calcite and snow, with glowing prismite crystal outcrops that sparkle at night |
| **Palm Coast** | warm beaches | curved palms on turquoise water; shore crabs |

All thirteen sit within about 5 km of spawn on a typical seed. They also join the vanilla biome tags
of the biome they grew from, so villages, temples, mineshafts and mob variants appear in them the way
they would in that vanilla biome.

### Grand Scale terrain

Grand Scale is a built-in data pack, **on by default**, which you can switch off in *Create World → More → Data Packs*:

- Continents and erosion are sampled at 3/4 of vanilla's frequency, so land masses and mountain ranges
  are about a third larger. Climate zones are 1.25× larger.
- Terrain above sea level is stretched 1.55×, so the tallest peaks reach about y=330 and rise through the
  cloud layer. Oceans and coastlines are unchanged.
- The overworld's build limit is raised to **y=384** (from 320), and vanilla's "top slide" that flattens
  terrain near the old ceiling is moved up to match.

> Grand Scale changes the overworld's height, so use it on **new worlds**. Don't add it to a world that
> was created without it.

### Trees (custom trunk and foliage placers)

| Tree | Shape |
|---|---|
| Redwood | A tall bare column. The upper crown has level branches that spiral round the trunk and get shorter as they climb, so the tree reads as a cone, with a spire of needles on top. Saplings planted 2×2 grow giants. |
| Willow / Wisteria | A short wandering trunk opening into a ring of arching branches. Each branch carries a dome with curtains hanging from its rim. The curtains are cut so every leaf stays within six blocks of a log and never decays. |
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
| Plants | heather, edelweiss, frostbloom (glows), glowcap (glows), cattail, Spanish moss, and potted versions |

### Animals

| Animal | Where | Notes |
|---|---|---|
| **Elk** | Heather Moor, Redwood Giants, Verdant Peaks, Amber Steppe, vanilla taigas | Skittish: elk keep away from anyone walking upright, so approach by sneaking or holding wheat. Drops venison. |
| **Woolly Mammoth** | Frostbloom Tundra, vanilla snowy biomes | Peaceful until hurt, then it fights back. Shear it for brown wool, which regrows in 5 minutes. Breed with hay or wheat. |
| **Capybara** | Willow Bayou, Jade Karst, Cloud Forest, vanilla swamps | Calm, swims, likes melon. |
| **Shore Crab** | Palm Coast, vanilla beaches | Scuttles sideways, breathes underwater. Drops crab meat. |

Models and textures are generated together (see Tooling below), so they cannot drift apart.

### Structures

Eleven buildings, each with a themed loot chest:

| Structure | Biome |
|---|---|
| Stone circle | Heather Moor |
| Ruined watchtower | peaks and moors |
| Woodland lodge | Redwood Giants and Wisteria Vale |
| Sun shrine | Opal Dunes |
| Stilt hamlet | Willow Bayou |
| Karst pagoda | Jade Karst |
| Lighthouse | Palm Coast |
| Lumen shrine | Lumen Grove |
| Frost outpost | Frostbloom Tundra |
| Nomad camp | Amber Steppe |
| Cloud monastery | Cloud Forest |

### Extras

- `/expanse atlas [radius] [blocksPerPixel]` (needs operator level 2) writes a shaded biome-and-elevation
  map of the area to `<world>/expanse_atlas/`. It samples the generator directly, so it covers ground
  nobody has explored yet.
- Advancements: entering any Expanse biome starts a new advancement tab, and *Wanderer of the Expanse*
  is a challenge for visiting all thirteen biomes.

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
| `tools/gen_worldgen.py` | Tree and terrain features, placements, the 13 biomes, surface rules, biome tags, advancements and the Grand Scale pack |
| `tools/gen_entities.py` | Entity geometry (`models/entity/*.json`, read at runtime by `JsonEntityModels`), the texture painted onto exactly that UV layout, and the entity loot tables |
| `tools/textures/gen_textures.py` | Every block and item texture, plus the mod icon, all drawn procedurally and deterministically |
| `tools/structures/gen_structures.py` | The structure templates (`.nbt`), their pools, structure sets and chest loot |

### How this was checked without a monitor

- **Dedicated server smoke test.** Running the server with `-Dexpanse.dev.locate=6000
  -Dexpanse.dev.generate=true -Dexpanse.dev.atlas=8000:32 -Dexpanse.dev.stop=true` locates every Expanse
  biome and generates full chunks around each one, so every feature, tree and spawn rule runs. It then
  writes an atlas, logs the share of the map each biome covers, and shuts down (`command/DevHarness.java`).
- **Client biome tour.** `src/gametest/` is a Fabric client game test. It creates a world, flies to each
  biome, and saves noon, ground-level and night screenshots. Run it headless with
  `SDL_VIDEO_FORCE_EGL=1 xvfb-run ./gradlew runClientGameTest`. The EGL setting is needed because 26.3
  creates its window with SDL3 and asks for an sRGB framebuffer, and Xvfb's GLX visuals don't offer one.

## Layout

```
src/main/java/dev/voidmc/expanse/
├── Expanse.java                 entrypoint: registries, wood behaviour, vanilla spawns, Grand Scale pack
├── registry/                    blocks (WoodSet builds a whole tree's set), items, tree growers, creative tab
├── block/                       the two blocks that need their own rules (palm sapling on sand, frostbloom on snow)
├── world/biome/                 biome keys and BiomePlacement (where they go in the climate table)
├── world/tree/                  5 trunk placers, 3 foliage placers
├── world/feature/               karst pillar, natural arch, crystal outcrop
├── entity/                      elk, mammoth, capybara, crab
├── mixin/                       OverworldBiomeBuilderMixin: one hook, every overworld biome entry passes through it
└── command/                     /expanse atlas and the headless dev harness
src/client/java/…/client/        JSON-driven entity models, animations, renderers
src/gametest/                    the biome tour (not shipped)
src/main/resources/resourcepacks/grand_scale/   the Grand Scale data pack
tools/                           generators (see above)
```
