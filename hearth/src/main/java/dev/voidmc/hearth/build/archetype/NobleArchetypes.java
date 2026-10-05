package dev.voidmc.hearth.build.archetype;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.noble.Castle;
import dev.voidmc.hearth.build.archetype.noble.HuntingLodge;
import dev.voidmc.hearth.build.archetype.noble.Keep;
import dev.voidmc.hearth.build.archetype.noble.Palace;
import dev.voidmc.hearth.build.archetype.noble.Stables;

/** The nobility: keep, castle, palace, hunting lodge, stables. Owned by the noble architect (stage B); see docs/LIVING_WORLD.md §8.5. */
public final class NobleArchetypes {
	private NobleArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Keep());
		registry.register(new Castle());
		registry.register(new Stables());
		registry.register(new HuntingLodge());
		registry.register(new Palace());
	}
}
