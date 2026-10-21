package dev.voidmc.hearth.sim.wild;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.TerrainProbe;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/** Made-up land for the posts' tests: a height function, an optional lake, biome names and cultures by rule. */
final class WildLand implements PostSites.Land {
	final Probe probe;
	final List<int[]> towns = new ArrayList<>();
	final List<PostSites.RoadLine> roads = new ArrayList<>();
	java.util.function.BiFunction<Integer, Integer, String> biome = (x, z) -> "plains";
	java.util.function.BiFunction<Integer, Integer, Culture> culture = (x, z) -> Culture.TEMPERATE;

	WildLand(IntBinaryOperator height, long seed) {
		this.probe = new Probe(height, seed, (x, z) -> false);
	}

	WildLand(IntBinaryOperator height, long seed, java.util.function.BiPredicate<Integer, Integer> water) {
		this.probe = new Probe(height, seed, water);
	}

	@Override
	public TerrainProbe probe() {
		return this.probe;
	}

	@Override
	public String biome(int x, int z) {
		return this.biome.apply(x, z);
	}

	@Override
	public Culture culture(int x, int z) {
		return this.culture.apply(x, z);
	}

	@Override
	public List<int[]> towns(int rx, int rz) {
		return this.towns;
	}

	@Override
	public List<PostSites.RoadLine> roads(int rx, int rz) {
		return this.roads;
	}

	@Override
	public boolean clear(int x, int z) {
		return true;
	}

	/** A straight road from (x0, z0) to (x1, z1) at the ground, with bridge columns where {@code bridge} says. */
	PostSites.RoadLine road(long id, boolean kings, int x0, int z0, int x1, int z1, java.util.function.IntPredicate bridge) {
		int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0)) + 1;
		int[] xs = new int[n];
		int[] ys = new int[n];
		int[] zs = new int[n];
		byte[] types = new byte[n];
		for (int i = 0; i < n; i++) {
			xs[i] = x0 + (int) Math.round((x1 - x0) * (double) i / (n - 1));
			zs[i] = z0 + (int) Math.round((z1 - z0) * (double) i / (n - 1));
			ys[i] = this.probe.groundY(xs[i], zs[i]);
			types[i] = bridge.test(i) ? (byte) 2 : 0;
		}
		PostSites.RoadLine r = new PostSites.RoadLine(id, kings, xs, ys, zs, types);
		this.roads.add(r);
		return r;
	}

	static final class Probe implements TerrainProbe {
		final IntBinaryOperator height;
		final long seed;
		final java.util.function.BiPredicate<Integer, Integer> water;

		Probe(IntBinaryOperator height, long seed, java.util.function.BiPredicate<Integer, Integer> water) {
			this.height = height;
			this.seed = seed;
			this.water = water;
		}

		@Override
		public long seed() {
			return this.seed;
		}

		@Override
		public int minY() {
			return -64;
		}

		@Override
		public int maxY() {
			return 320;
		}

		@Override
		public int seaLevel() {
			return 63;
		}

		@Override
		public int groundY(int x, int z) {
			return this.water.test(x, z) ? Math.min(58, this.height.applyAsInt(x, z)) : this.height.applyAsInt(x, z);
		}

		@Override
		public int waterY(int x, int z) {
			return this.water.test(x, z) ? 63 : NO_WATER;
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return null;
		}

		@Override
		public float temperature(int x, int z) {
			return 0.6F;
		}
	}
}
