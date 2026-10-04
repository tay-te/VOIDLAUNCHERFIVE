package dev.voidmc.hearth.build.kit.light;

import dev.voidmc.hearth.build.draft.Draft;

/**
 * The lighting pass every blueprint goes through when it compiles (after the archetype has drawn, before shapes are
 * resolved and steps ordered): whatever the archetype left dark that the light rule of its family covers
 * ({@link LightRules}) is lit with the culture's fixtures, placed as a builder would ({@link Session}). An archetype's
 * own fixtures stay as they are; a building lit by design ({@link Lighting}) gives the pass nothing to do.
 *
 * <p>Roads are never touched (their lamps are their design, and their steps must stay on their corridor); linear works
 * (walls, bridges, docks, walkways) are lit only in columns they already build in.
 */
public final class AutoLighter {
	private AutoLighter() {
	}

	/** Nanoseconds spent per phase since start (setup, doors, rooms, ground, last resort). */
	public static long[] timings() {
		return Session.TIME.clone();
	}

	/** Lights a draft and returns every fixture placed (for tests and debugging). */
	public static java.util.List<Fixture> lightLogged(Draft d) {
		LightRules.Family family = LightRules.family(d.spec().archetype());
		if (family == LightRules.Family.ROAD) {
			return java.util.List.of();
		}
		Session s = new Session(d, family, 0, 0, d.frontage() - 1, d.depth() - 1, null);
		s.run();
		return s.log;
	}

	/** Lights a finished draft; returns what was placed (by {@link Fixture.Kind} ordinal), or an empty array. */
	public static int[] light(Draft d) {
		LightRules.Family family = LightRules.family(d.spec().archetype());
		if (family == LightRules.Family.ROAD || d.param("lighting", "auto").equals("none")) {
			return new int[0];
		}
		long t0 = System.nanoTime();
		Session s = new Session(d, family, 0, 0, d.frontage() - 1, d.depth() - 1, null);
		Session.TIME[0] += System.nanoTime() - t0;
		s.run();
		return s.placed;
	}
}
