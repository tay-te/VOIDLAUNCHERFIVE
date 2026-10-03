package dev.voidmc.hearth.build;

import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.build.draft.Draft;

/**
 * A kind of building: a generator that draws a {@link Draft} for any plot of its kind, in any style. Implementations are
 * stateless and thread-safe (blueprints are generated on worldgen workers); everything a building varies comes from the
 * draft's spec, style, site and seeded random, so the same spec on the same terrain always draws the same building.
 *
 * <p>Each archetype documents the {@code params} keys it reads in its class Javadoc. See
 * {@code docs/BUILDING_GUIDE.md} for how to write one.
 */
public interface Archetype {
	/** What the planner needs to choose this archetype (id, category, sizes, jobs, flags). */
	ArchetypeInfo info();

	/** Draws the building. Called once per blueprint, on any thread. */
	void draw(Draft d);
}
