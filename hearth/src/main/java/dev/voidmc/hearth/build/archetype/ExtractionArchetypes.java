package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.extract.QuarryPit;

/** Extraction, construction run backwards: quarry pit, mine, shaft, clay pit, sand pit, plantation. Owned by the extraction architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class ExtractionArchetypes {
	private ExtractionArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new QuarryPit());
	}
}
