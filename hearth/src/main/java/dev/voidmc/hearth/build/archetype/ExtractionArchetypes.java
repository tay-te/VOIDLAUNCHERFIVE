package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.extract.Mine;
import dev.voidmc.hearth.build.archetype.extract.Plantation;
import dev.voidmc.hearth.build.archetype.extract.QuarryPit;
import dev.voidmc.hearth.build.archetype.extract.Shaft;
import dev.voidmc.hearth.build.archetype.extract.SoftPit;

/** Extraction, construction run backwards: quarry pit, mine, shaft, clay pit, sand pit, plantation. Owned by the extraction architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class ExtractionArchetypes {
	private ExtractionArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new QuarryPit());
		registry.register(new Mine());
		registry.register(new Shaft());
		registry.register(SoftPit.clay());
		registry.register(SoftPit.sand());
		registry.register(new Plantation());
	}
}
