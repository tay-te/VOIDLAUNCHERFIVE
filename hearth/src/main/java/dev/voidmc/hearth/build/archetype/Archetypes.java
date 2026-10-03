package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.house.Cottage;
import dev.voidmc.hearth.build.archetype.house.Hut;
import dev.voidmc.hearth.build.archetype.house.Townhouse;
import dev.voidmc.hearth.build.archetype.landscape.Well;
import dev.voidmc.hearth.build.archetype.work.BuildersYard;
import dev.voidmc.hearth.build.archetype.commerce.MarketStall;

/** Registers every archetype the build module ships. */
public final class Archetypes {
	private Archetypes() {
	}

	public static void registerAll(ArchetypeRegistry registry) {
		registry.register(new Hut());
		registry.register(new Cottage());
		registry.register(new Townhouse());
		registry.register(new BuildersYard());
		registry.register(new MarketStall());
		registry.register(new Well());
	}
}
