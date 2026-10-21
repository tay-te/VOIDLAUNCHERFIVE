package dev.voidmc.hearth.build.archetype.wild;

import dev.voidmc.hearth.build.ArchetypeRegistry;
import dev.voidmc.hearth.build.archetype.civic.Hospice;

/**
 * The buildings of the small places in the wild (docs/LIVING_WORLD_2.md §7, lane B3): the pass hospice
 * ({@code hearth:civic/hospice}) and the nomads' felt yurt ({@code hearth:wild/yurt}, the round house's portable form).
 * Both are laid out only by a post's planner (weight 0: towns never choose them).
 */
public final class WildArchetypes {
	private WildArchetypes() {
	}

	public static void register(ArchetypeRegistry registry) {
		registry.register(new Hospice());
		registry.register(new Yurt());
	}
}
