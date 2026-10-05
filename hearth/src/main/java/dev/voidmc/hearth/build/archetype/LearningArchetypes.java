package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.civic.Convent;
import dev.voidmc.hearth.build.archetype.civic.Monastery;
import dev.voidmc.hearth.build.archetype.civic.Observatory;
import dev.voidmc.hearth.build.archetype.civic.Shrine;
import dev.voidmc.hearth.build.archetype.civic.University;
import dev.voidmc.hearth.build.archetype.work.AlchemistTower;

/** Clergy and learning: monastery, convent, shrine, university, observatory, alchemist's tower. Owned by the learning architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class LearningArchetypes {
	private LearningArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Shrine());
		registry.register(new AlchemistTower());
		registry.register(new Observatory());
		registry.register(new Monastery());
		registry.register(new Convent());
		registry.register(new University());
	}
}
