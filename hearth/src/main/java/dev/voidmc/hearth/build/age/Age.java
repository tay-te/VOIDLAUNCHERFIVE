package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.build.draft.Draft;

/**
 * What time does to a building, applied to every drawing after its archetype has drawn it and before it compiles
 * (the one hook into the pipeline, in {@code BlueprintServiceImpl.draw}): first {@link Accretion} (the additions a
 * household made over generations: the plot param {@code additions}, housing only), then {@link Decay} (the plot param
 * {@code decay}, any archetype). Both are no-ops without their params, so every other blueprint is exactly what its
 * archetype drew. Dressings for special days are not part of the blueprint at all: see {@link Dressings}.
 */
public final class Age {
	private Age() {
	}

	/** Applies the plot's additions and decay to a finished drawing. */
	public static void apply(ArchetypeInfo info, Draft d) {
		Accretion.apply(info, d);
		Decay.apply(d, Decay.level(d.spec()));
	}
}
