package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.build.draft.Draft;

/**
 * Accretion: houses that grow over generations instead of being rebuilt (docs/LIVING_WORLD.md §8.4). Driven by the plot
 * param {@code additions}. Work in progress.
 */
public final class Accretion {
	/** The plot param. */
	public static final String PARAM = "additions";

	private Accretion() {
	}

	public static void apply(ArchetypeInfo info, Draft d) {
		if (!d.hasParam(PARAM) || info.category() != ArchetypeCategory.HOUSING) {
			return;
		}
	}
}
