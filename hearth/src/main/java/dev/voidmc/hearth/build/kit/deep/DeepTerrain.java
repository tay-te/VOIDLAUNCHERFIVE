package dev.voidmc.hearth.build.kit.deep;

import dev.voidmc.hearth.api.TerrainProbe;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Made-up realm ground for the deep town's tests and previews, read the way the planner reads a realm
 * ({@code plan.deep.RealmTerrain}): the ground is the realm's floor where it lies open, and where the realm's wall stands
 * the rock rises as ground up to the vault. Each kind is a pure function of position; the interesting part is near the
 * origin, where a plot is placed, and a wall (when there is one) lies on the side {@code wall} of it, its face
 * {@code cliffAt} blocks out from the origin, so a plot of depth {@code d} with its back to the wall has its last rows
 * cut into the rock when {@code cliffAt < d / 2}.
 *
 * <pre>
 *   floor   open realm floor at y 20, rising and falling a block over twenty
 *   rubble  hummocky floor, mounds of fallen rock up to three blocks
 *   wall    the floor, then a rubble slope rising toward the wall, then the wall itself, a cliff to the vault
 *   ledge   the floor, a shelf six blocks up, then the wall
 * </pre>
 *
 * The probe answers the vault too ({@link #ceilingY}): about forty-four blocks over the open floor, coming down to some
 * twenty over the floor at the foot of the wall, as Expanse's vaults follow their walls down. Previews and tests pass it
 * to plots as {@code ceiling_y}, as the planner is asked to (docs/requests/b-deep.md).
 */
public final class DeepTerrain implements TerrainProbe {
	public static final String[] KINDS = {"floor", "rubble", "wall", "ledge"};
	/** The general floor level. */
	public static final int FLOOR = 20;

	public enum Kind {
		FLOOR, RUBBLE, WALL, LEDGE
	}

	private final Kind kind;
	private final long seed;
	private final Direction wall;
	private final int cliffAt;

	/**
	 * @param wall    the side of the origin the realm's wall lies on (ignored on open floor)
	 * @param cliffAt how far from the origin toward {@code wall} the wall's face stands
	 */
	public DeepTerrain(Kind kind, long seed, Direction wall, int cliffAt) {
		this.kind = kind;
		this.seed = seed;
		this.wall = wall;
		this.cliffAt = cliffAt;
	}

	public static DeepTerrain named(String name, long seed, Direction wall, int cliffAt) {
		return new DeepTerrain(Kind.valueOf(name.toUpperCase(Locale.ROOT)), seed, wall, cliffAt);
	}

	/** Whether a terrain name is one of these kinds. */
	public static boolean isKind(String name) {
		for (String k : KINDS) {
			if (k.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	public Kind kind() {
		return this.kind;
	}

	/** Distance toward the wall. */
	private int toward(int x, int z) {
		return x * this.wall.getStepX() + z * this.wall.getStepZ();
	}

	/** Distance along the wall. */
	private int along(int x, int z) {
		return x * this.wall.getStepZ() - z * this.wall.getStepX();
	}

	private double ragged(int a) {
		return 1.2 * StrictMath.sin(a * 0.37 + this.seed) + 0.8 * StrictMath.sin(a * 0.11 + 2.0 * this.seed);
	}

	/** The world y of the wall's top where rock stands (the vault over the wall's foot). */
	private int rock() {
		return FLOOR + 70;
	}

	@Override
	public int groundY(int x, int z) {
		double gentle = 0.9 * StrictMath.sin(x / 9.0 + this.seed) * StrictMath.cos(z / 11.0);
		return switch (this.kind) {
			case FLOOR -> FLOOR + (int) Math.round(gentle);
			case RUBBLE -> {
				double m = 1.6 * StrictMath.sin(x / 5.0 + this.seed) * StrictMath.sin(z / 6.0) + 1.2 * StrictMath.cos((x - z) / 4.0);
				yield FLOOR + (int) Math.round(Math.max(-1, m));
			}
			case WALL -> {
				int t = toward(x, z);
				double face = this.cliffAt + ragged(along(x, z));
				if (t >= face) {
					yield rock();
				}
				// the rubble slope rises over the last eight blocks before the face
				double up = Math.max(0, t - (face - 8));
				yield FLOOR + (int) Math.round(gentle * 0.5 + up * up / 9.0);
			}
			case LEDGE -> {
				int t = toward(x, z);
				double face = this.cliffAt + ragged(along(x, z));
				if (t >= face) {
					yield rock();
				}
				yield t >= face - 7 ? FLOOR + 6 : FLOOR + (int) Math.round(gentle * 0.5);
			}
		};
	}

	/** The underside of the vault over (x, z): about 44 over the open floor, down to some 22 over the wall's foot. */
	public int ceilingY(int x, int z) {
		double far = switch (this.kind) {
			case FLOOR, RUBBLE -> 99;
			default -> this.cliffAt - toward(x, z);
		};
		double drop = Math.max(0, Math.min(1, (16 - far) / 16.0));
		return FLOOR + 44 - (int) Math.round(22 * drop * drop) + (int) Math.round(2 * StrictMath.sin(x / 7.0) * StrictMath.cos(z / 9.0));
	}

	/** The lowest vault over a world rectangle (what a plot there is told as {@code ceiling_y}). */
	public int ceilingOver(int x0, int z0, int x1, int z1) {
		int m = Integer.MAX_VALUE;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				m = Math.min(m, ceilingY(x, z));
			}
		}
		return m;
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
		return -64;
	}

	@Override
	public int waterY(int x, int z) {
		return NO_WATER;
	}

	@Override
	public @Nullable ResourceKey<Biome> biome(int x, int z) {
		return null;
	}

	@Override
	public float temperature(int x, int z) {
		return 0.8F;
	}
}
