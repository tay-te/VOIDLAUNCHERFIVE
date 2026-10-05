package dev.voidmc.hearth.api;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The open spaces under the ground, read without loading chunks: on Expanse Earth worlds, the cavern model's realms
 * (landscapes 110–440 blocks across under vaults up to 130 high), their floors and ceilings. Mines aim for them and mine
 * camps grow into underground towns in them (docs/LIVING_WORLD.md §8.2). Thread-safe, like {@link TerrainProbe}.
 * Available through {@link HearthWorld#underground()}; null where there is no such model.
 */
public interface UndergroundProbe {
	/** The realm under (x, z), or null. */
	@Nullable Realm realm(int x, int z);

	/** The realms whose centres lie within {@code radius} blocks of (x, z). */
	java.util.List<Realm> realmsNear(int x, int z, int radius);

	/** The y a mob would stand at on the floor of the open space under (x, z) nearest {@code nearY}, or Integer.MIN_VALUE. */
	int floorY(int x, int z, int nearY);

	/** The y of the underside of the vault over (x, z) above {@code floorY}, or Integer.MIN_VALUE. */
	int ceilingY(int x, int z, int floorY);

	/** The y of the water surface's top face in the open space under (x, z) near {@code floorY} (a realm's mere, a river
	 * tunnel), or {@link TerrainProbe#NO_WATER} if it is dry. */
	default int waterY(int x, int z, int floorY) {
		return TerrainProbe.NO_WATER;
	}

	/**
	 * @param id        {@code cellX << 32 | cellZ} of the cavern model's realm cell on Expanse Earth worlds: stable per world
	 * @param character the realm's character, as Expanse's realm biomes name them ({@code expanse:realm_<character>}):
	 *                  {@code wilds}, {@code lush}, {@code dripstone}, {@code crystal}, {@code ember}, {@code mere},
	 *                  {@code frozen}, {@code roots}, {@code fossil}, {@code tidal}, {@code amber}, {@code brimstone};
	 *                  its materials, light and resources follow from it
	 */
	record Realm(long id, BlockPos center, int radius, int floorY, int ceilingY, String character) {
	}
}
