package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.defense.Gatehouse;
import dev.voidmc.hearth.build.archetype.defense.Palisade;
import dev.voidmc.hearth.build.archetype.defense.TownWall;
import dev.voidmc.hearth.build.archetype.defense.Watchtower;
import dev.voidmc.hearth.build.archetype.landscape.Bell;
import dev.voidmc.hearth.build.archetype.landscape.Fountain;
import dev.voidmc.hearth.build.archetype.landscape.GardenPlot;
import dev.voidmc.hearth.build.archetype.landscape.LampPost;
import dev.voidmc.hearth.build.archetype.landscape.Park;
import dev.voidmc.hearth.build.archetype.landscape.Plaza;
import dev.voidmc.hearth.build.archetype.landscape.Statue;

/** Landscape and defence: plazas, fountains, gardens, parks, lamp posts, statues, bells, palisades, walls, gatehouses, watchtowers. Owned by the landscape architect; see docs/ARCHITECTURE.md §6.1, docs/BUILDING_GUIDE.md and docs/buildings/landscape.md. */
public final class LandscapeArchetypes {
	private LandscapeArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Plaza());
		registry.register(new LampPost());
		registry.register(new Fountain());
		registry.register(new Statue());
		registry.register(new Bell());
		registry.register(new GardenPlot());
		registry.register(new Park());
		registry.register(new Palisade());
		registry.register(new TownWall());
		registry.register(new Watchtower());
		registry.register(new Gatehouse());
	}
}
