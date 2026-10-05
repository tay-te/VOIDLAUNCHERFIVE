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

	/** Debugging: a local cell whose need and light the pass prints after each phase (null: off). */
	public static int @org.jspecify.annotations.Nullable [] trace;

	/** Nanoseconds spent per phase since start (setup, doors, rooms, ground, last resort). */
	public static long[] timings() {
		return Session.TIME.clone();
	}

	/** Lights a draft and returns every fixture placed (for tests and debugging). */
	public static java.util.List<Fixture> lightLogged(Draft d) {
		LightRules.Family family = LightRules.family(d.spec().archetype());
		if (family == LightRules.Family.ROAD || d.param("lighting", "auto").equals("none") || d.markLit()) {
			return java.util.List.of();
		}
		java.util.List<Fixture> log = new java.util.ArrayList<>();
		rounds(d, family, log, null);
		return log;
	}

	/**
	 * Runs passes until nothing is dark or a pass put up nothing with a body: a bracket, a post or a brazier's stand can
	 * roof the cells under it or give folk a step, so another pass reads the draft again and lights what that made
	 * (rarely more than two passes; at most {@value #ROUNDS}).
	 */
	private static void rounds(Draft d, LightRules.Family family, java.util.List<Fixture> log, int @org.jspecify.annotations.Nullable [] placed) {
		// light is read through the shapes that will stand (a stair's corner, a wall's post occlude it): resolve them first;
		// the compiler resolves again after, for the fixtures (a bracket joining its wall)
		dev.voidmc.hearth.build.blueprint.ShapeResolver.resolve(d, d.site());
		Session s = null;
		for (int round = 0; round < ROUNDS; round++) {
			long t0 = System.nanoTime();
			s = s == null ? new Session(d, family, 0, 0, d.frontage() - 1, d.depth() - 1, null) : s.next(0, 0, d.frontage() - 1, d.depth() - 1);
			Session.TIME[0] += System.nanoTime() - t0;
			s.run();
			log.addAll(s.log);
			if (placed != null) {
				for (int k = 0; k < placed.length; k++) {
					placed[k] += s.placed[k];
				}
			}
			boolean shaped = false;
			for (Fixture f : s.log) {
				shaped |= f.shapes();
			}
			if (!shaped) {
				return;
			}
		}
	}

	private static final int ROUNDS = 3;

	/** Debugging: the light and blocks round a local cell as the pass sees them (a 5 × 3 × 5 neighbourhood, per layer). */
	public static String describe(Draft d, int x, int y, int z) {
		Session s = new Session(d, LightRules.family(d.spec().archetype()), 0, 0, d.frontage() - 1, d.depth() - 1, null);
		StringBuilder sb = new StringBuilder();
		for (int yy = y - 1; yy <= y + 3; yy++) {
			sb.append("y").append(yy).append(":").append(System.lineSeparator());
			for (int zz = z - 2; zz <= z + 2; zz++) {
				for (int xx = x - 2; xx <= x + 2; xx++) {
					var st = s.g.at(xx, yy, zz);
					String n = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
					String flag = (s.sp.reached(xx, yy, zz) ? "R" : s.sp.walkable(xx, yy, zz) ? "w" : " ")
						+ (s.g.contains(xx, yy, zz) && s.unmet(s.g.index(xx, yy, zz)) ? "!" : " ");
					sb.append(String.format(java.util.Locale.ROOT, "%2d%s %-10.10s ", s.lf.at(xx, yy, zz), flag, n));
				}
				sb.append(System.lineSeparator());
			}
		}
		return sb.toString();
	}

	/** Debugging: the local standing cells a pass over the draft would count as walked to (x, y, z per entry), and what stands in each cell round them. */
	public static java.util.Map<Long, String> reached(Draft d) {
		dev.voidmc.hearth.build.blueprint.ShapeResolver.resolve(d, d.site());
		Session s = new Session(d, LightRules.family(d.spec().archetype()), 0, 0, d.frontage() - 1, d.depth() - 1, null);
		java.util.Map<Long, String> out = new java.util.HashMap<>();
		for (int y = s.g.y0; y < s.g.y0 + s.g.sy; y++) {
			for (int z = s.g.z0; z < s.g.z0 + s.g.sz; z++) {
				for (int x = s.g.x0; x < s.g.x0 + s.g.sx; x++) {
					if (s.sp.reached(x, y, z)) {
						out.put(net.minecraft.core.BlockPos.asLong(x, y, z), "");
					}
				}
			}
		}
		return out;
	}

	/** Lights a finished draft; returns what was placed (by {@link Fixture.Kind} ordinal), or an empty array. */
	public static int[] light(Draft d) {
		LightRules.Family family = LightRules.family(d.spec().archetype());
		if (family == LightRules.Family.ROAD || d.param("lighting", "auto").equals("none") || d.markLit()) {
			return new int[0];
		}
		int[] placed = new int[Fixture.Kind.values().length];
		rounds(d, family, new java.util.ArrayList<>(), placed);
		return placed;
	}
}
