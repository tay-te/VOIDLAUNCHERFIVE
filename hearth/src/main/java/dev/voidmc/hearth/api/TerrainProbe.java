package dev.voidmc.hearth.api;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The natural lie of the land, read from the world generator without building chunks — the same trick VOID LOD uses for
 * the horizon. The planner chooses sites and the build module grounds foundations with it, so plans can be made for land
 * nobody has visited and are the same whenever they are made.
 *
 * <p>It describes the <i>generated</i> terrain, before features (trees, boulders) and before anyone built or dug. It may
 * be called from any thread, including world generation workers; implementations are thread-safe and cache per thread.
 *
 * <p>Implementations: the sim module's generator-backed probe (with an Expanse fast path that reads rivers and landforms
 * from the Earth terrain model), and synthetic terrains for tests and the offline previews.
 */
public interface TerrainProbe {
	int NO_WATER = Integer.MIN_VALUE;

	/** The world seed (the probe's terrain is a function of it). */
	long seed();

	int minY();

	int maxY();

	int seaLevel();

	/** The y a mob would stand at on the natural ground: one above the highest solid block, ignoring water above it. */
	int groundY(int x, int z);

	/** The y of the water surface's top face over (x, z) — the first non-water y above it — or {@link #NO_WATER}. */
	int waterY(int x, int z);

	/** The surface biome, or null where the probe cannot say (synthetic terrain). */
	@Nullable ResourceKey<Biome> biome(int x, int z);

	/** The surface temperature on vanilla's scale (below 0.15 it snows). */
	float temperature(int x, int z);

	/** The open spaces under this ground (Expanse realms) when the probe knows them, else null: deep towns read it. */
	default @Nullable UndergroundProbe underground() {
		return null;
	}

	default boolean isWater(int x, int z) {
		return waterY(x, z) != NO_WATER;
	}
}
