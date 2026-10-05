package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.law.Courthouse;
import dev.voidmc.hearth.build.archetype.law.Gallows;
import dev.voidmc.hearth.build.archetype.law.Jail;
import dev.voidmc.hearth.build.archetype.law.Pillory;
import dev.voidmc.hearth.build.archetype.law.TollGate;
import dev.voidmc.hearth.build.archetype.law.WatchHouse;

/** The law's buildings: courthouse, jail, gallows, pillory, watch house, toll gate. Owned by the lawhouse architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class LawHouseArchetypes {
	private LawHouseArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Pillory());
		registry.register(new Gallows());
		registry.register(new Courthouse());
		registry.register(new Jail());
		registry.register(new WatchHouse());
		registry.register(new TollGate());
	}
}
