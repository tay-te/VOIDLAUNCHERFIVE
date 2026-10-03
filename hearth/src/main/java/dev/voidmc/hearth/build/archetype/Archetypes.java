package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.house.Cottage;

/** Registers every archetype the build module ships. */
public final class Archetypes {
	private Archetypes() {
	}

	public static void registerAll(ArchetypeRegistry registry) {
		registry.register(new Cottage());
	}
}
