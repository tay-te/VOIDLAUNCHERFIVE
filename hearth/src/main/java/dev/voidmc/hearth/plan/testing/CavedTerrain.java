package dev.voidmc.hearth.plan.testing;

import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Land with a realm under it, for the planner's tests and previews of deep towns (LIVING_WORLD.md §8.2): any terrain
 * probe, with one round cavern below shaped as Expanse's realms are — a lens, its floor a bowl of low hills whose rubble
 * slopes rise toward the walls, a vault about seventy blocks over it. Implements both probes, so the planner finds the
 * underground through the terrain it is given.
 */
public final class CavedTerrain implements TerrainProbe, UndergroundProbe {
	private final TerrainProbe surface;
	private final Realm realm;

	/**
	 * @param floor the realm's general floor level (walking level)
	 */
	public CavedTerrain(TerrainProbe surface, int centerX, int centerZ, int radius, int floor, String character) {
		this.surface = surface;
		long id = ((long) centerX << 32) ^ (centerZ & 0xFFFFFFFFL) ^ 0x5245414CL;
		this.realm = new Realm(id, new BlockPos(centerX, floor, centerZ), radius, floor, floor + 70, character);
	}

	public TerrainProbe surface() {
		return this.surface;
	}

	public Realm theRealm() {
		return this.realm;
	}

	// ---- the land above

	@Override
	public long seed() {
		return this.surface.seed();
	}

	@Override
	public int minY() {
		return this.surface.minY();
	}

	@Override
	public int maxY() {
		return this.surface.maxY();
	}

	@Override
	public int seaLevel() {
		return this.surface.seaLevel();
	}

	@Override
	public int groundY(int x, int z) {
		return this.surface.groundY(x, z);
	}

	@Override
	public int waterY(int x, int z) {
		return this.surface.waterY(x, z);
	}

	@Override
	public @Nullable ResourceKey<Biome> biome(int x, int z) {
		return this.surface.biome(x, z);
	}

	@Override
	public float temperature(int x, int z) {
		return this.surface.temperature(x, z);
	}

	// ---- the realm below

	private double inward(int x, int z) {
		return this.realm.radius() - Math.hypot(x - this.realm.center().getX(), z - this.realm.center().getZ());
	}

	@Override
	public @Nullable Realm realm(int x, int z) {
		return inward(x, z) > 0 ? this.realm : null;
	}

	@Override
	public List<Realm> realmsNear(int x, int z, int radius) {
		return Math.hypot(x - this.realm.center().getX(), z - this.realm.center().getZ()) <= radius ? List.of(this.realm) : List.of();
	}

	@Override
	public int floorY(int x, int z, int nearY) {
		double in = inward(x, z);
		if (in <= 3) {
			return Integer.MIN_VALUE;
		}
		// rubble slopes rising to the walls, low hills across the floor
		double across = Math.min(1, in / (this.realm.radius() * 0.5));
		double wallward = (1 - across) * (1 - across);
		double hills = 2.5 * StrictMath.sin(x / 23.0) * StrictMath.cos(z / 31.0) + 1.5 * StrictMath.sin((x + z) / 13.0);
		return this.realm.floorY() + (int) Math.round(hills + 26 * wallward);
	}

	@Override
	public int ceilingY(int x, int z, int floorY) {
		double in = inward(x, z);
		if (in <= 0) {
			return Integer.MIN_VALUE;
		}
		double across = Math.min(1, in / (this.realm.radius() * 0.55));
		return this.realm.floorY() + 20 + (int) Math.round(50 * Math.sqrt(across));
	}
}
