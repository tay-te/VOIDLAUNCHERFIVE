package dev.voidmc.hearth.api.testing;

import dev.voidmc.hearth.api.TerrainProbe;
import java.util.Locale;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Made-up land for tests and the offline previews, so every module judges its work on the same ground. Each kind is a
 * pure function of position and seed. The interesting part is always near the origin: a slope, a cliff edge, a river or
 * a shoreline runs through (0, 0), so a plot or a town placed there meets it.
 *
 * <pre>
 *   flat    ground at y 64
 *   slope   rising 1 block per 3 toward +x (about 18°)
 *   steep   rising 1 block per 1.4 toward +x (a mountainside: terraces and cliff dwellings)
 *   hills   rolling ground, ±14 blocks over ~100-block wavelengths
 *   cliff   a plateau at 80 to the west dropping to a plain at 56 east of x ≈ 0, with a ragged edge
 *   river   gentle ground with a meandering river along z through x ≈ 0, 10–16 blocks wide
 *   coast   land to the west sloping into the sea east of x ≈ 0; sea level 63
 *   lake    a round lake of radius 40 at the origin in gentle ground
 * </pre>
 */
public final class SyntheticTerrain implements TerrainProbe {
	public static final String[] KINDS = {"flat", "slope", "steep", "hills", "cliff", "river", "coast", "lake"};
	private static final int SEA = 63;

	private final Kind kind;
	private final long seed;
	private final float temperature;

	public SyntheticTerrain(Kind kind, long seed, float temperature) {
		this.kind = kind;
		this.seed = seed;
		this.temperature = temperature;
	}

	public SyntheticTerrain(Kind kind, long seed) {
		this(kind, seed, 0.8F);
	}

	public static SyntheticTerrain named(String name, long seed) {
		return new SyntheticTerrain(Kind.valueOf(name.toUpperCase(Locale.ROOT)), seed);
	}

	public Kind kind() {
		return this.kind;
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
		return 384;
	}

	@Override
	public int seaLevel() {
		return SEA;
	}

	@Override
	public int groundY(int x, int z) {
		return (int) Math.floor(height(x, z));
	}

	@Override
	public int waterY(int x, int z) {
		switch (this.kind) {
			case RIVER -> {
				double d = Math.abs(x - riverCentre(z));
				if (d < riverHalfWidth(z)) {
					return (int) Math.floor(gentle(x, z, 64)) + 1;
				}
			}
			case COAST -> {
				if (height(x, z) < SEA) {
					return SEA + 1;
				}
			}
			case LAKE -> {
				if (Math.hypot(x, z) < 40 + 4 * noise(x * 0.05, z * 0.05)) {
					return 64;
				}
			}
			default -> {
			}
		}
		return NO_WATER;
	}

	@Override
	public @Nullable ResourceKey<Biome> biome(int x, int z) {
		return null;
	}

	@Override
	public float temperature(int x, int z) {
		return this.temperature;
	}

	private double height(int x, int z) {
		return switch (this.kind) {
			case FLAT -> 64;
			case SLOPE -> 64 + x / 3.0;
			case STEEP -> 64 + x / 1.4 + 1.5 * noise(x * 0.08, z * 0.08);
			case HILLS -> 64 + 14 * fbm(x / 100.0, z / 100.0);
			case CLIFF -> {
				double edge = 6 * noise(z * 0.04, 3.7) + 2 * noise(z * 0.15, 9.1);
				double t = smooth((edge - x) / 3.0);
				yield 56 + 24 * t + 2 * fbm(x / 60.0, z / 60.0);
			}
			case RIVER -> {
				double d = Math.abs(x - riverCentre(z));
				double half = riverHalfWidth(z);
				double ground = gentle(x, z, 64);
				if (d < half) {
					double bed = 3.5 * (1 - (d / half) * (d / half));
					yield ground - 1 - bed;
				}
				double bank = Math.max(0, 6 - (d - half)) * 0.25;
				yield ground + (d - half) * 0.04 - bank * 0.3;
			}
			case COAST -> {
				double shore = 8 * noise(z * 0.03, 1.3);
				yield 66 - (x - shore) * 0.12 + 1.5 * fbm(x / 50.0, z / 50.0);
			}
			case LAKE -> {
				double r = Math.hypot(x, z);
				double rim = 40 + 4 * noise(x * 0.05, z * 0.05);
				if (r < rim) {
					yield 63 - 6 * (1 - (r / rim) * (r / rim));
				}
				yield gentle(x, z, 64) + Math.min(6, (r - rim) * 0.08);
			}
		};
	}

	private double gentle(int x, int z, double base) {
		return base + 3 * fbm(x / 120.0, z / 120.0);
	}

	private double riverCentre(int z) {
		return 18 * noise(z / 90.0, 5.5) + 6 * noise(z / 30.0, 2.2);
	}

	private double riverHalfWidth(int z) {
		return 6 + 2 * (noise(z / 70.0, 7.7) + 1) / 2;
	}

	private double fbm(double x, double z) {
		return 0.6 * noise(x, z) + 0.3 * noise(x * 2.1, z * 2.1) + 0.1 * noise(x * 4.3, z * 4.3);
	}

	/** Smooth value noise in [-1, 1]. */
	private double noise(double x, double z) {
		int x0 = (int) Math.floor(x);
		int z0 = (int) Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		double u = fx * fx * (3 - 2 * fx);
		double v = fz * fz * (3 - 2 * fz);
		double a = lerp(u, hash(x0, z0), hash(x0 + 1, z0));
		double b = lerp(u, hash(x0, z0 + 1), hash(x0 + 1, z0 + 1));
		return lerp(v, a, b);
	}

	private double hash(int x, int z) {
		long h = this.seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
		h ^= h >>> 32;
		return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	private static double smooth(double t) {
		double c = Math.max(0, Math.min(1, t * 0.5 + 0.5));
		return c * c * (3 - 2 * c);
	}

	public enum Kind {
		FLAT, SLOPE, STEEP, HILLS, CLIFF, RIVER, COAST, LAKE
	}
}
