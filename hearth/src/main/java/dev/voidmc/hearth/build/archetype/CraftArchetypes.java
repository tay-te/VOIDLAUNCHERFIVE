package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.work.Tannery;

/** Craft workshops of the chains: tannery, butcher, chandler, dye works, paper mill, apiary, counting house. Owned by the craft architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class CraftArchetypes {
	private CraftArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Tannery());
	}
}
