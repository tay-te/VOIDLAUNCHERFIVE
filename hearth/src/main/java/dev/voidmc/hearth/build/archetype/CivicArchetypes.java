package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.civic.Chapel;

/** Civic and commercial buildings: market hall, inn, bank, chapel, town hall, library, school, bathhouse, guardhouse. Owned by the civic architect; see docs/ARCHITECTURE.md §6.1 and docs/BUILDING_GUIDE.md. */
public final class CivicArchetypes {
	private CivicArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Chapel());
	}
}
