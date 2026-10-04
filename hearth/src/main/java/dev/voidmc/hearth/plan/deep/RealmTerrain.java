package dev.voidmc.hearth.plan.deep;

import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.api.UndergroundProbe;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * A realm's floor as the planner's ground (LIVING_WORLD.md §8.2): the y a person stands at on the floor of the open
 * space, read from the underground probe; outside the realm, the rock of its walls stands up to the vault, so the deep
 * town keeps to the floor and cuts its dwellings into the walls' feet. No water (a realm's lakes are left out: the town
 * keeps off low ground the probe reports no floor for). Thread-safe like the probes it reads; one per world and realm,
 * so the planner's terrain cache is shared between calls.
 */
public final class RealmTerrain implements TerrainProbe {
	private static final Map<TerrainProbe, Map<Long, RealmTerrain>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

	private final TerrainProbe surface;
	private final UndergroundProbe under;
	private final UndergroundProbe.Realm realm;

	private RealmTerrain(TerrainProbe surface, UndergroundProbe under, UndergroundProbe.Realm realm) {
		this.surface = surface;
		this.under = under;
		this.realm = realm;
	}

	public static RealmTerrain of(TerrainProbe surface, UndergroundProbe under, UndergroundProbe.Realm realm) {
		Map<Long, RealmTerrain> m = CACHE.computeIfAbsent(surface, k -> Collections.synchronizedMap(new HashMap<>()));
		return m.computeIfAbsent(realm.id(), k -> new RealmTerrain(surface, under, realm));
	}

	public UndergroundProbe.Realm realm() {
		return this.realm;
	}

	public TerrainProbe surface() {
		return this.surface;
	}

	/** Whether (x, z) has the realm's open floor over it (not its walls, not a pillar). */
	public boolean open(int x, int z) {
		UndergroundProbe.Realm r = this.under.realm(x, z);
		return r != null && r.id() == this.realm.id() && this.under.floorY(x, z, this.realm.floorY()) != Integer.MIN_VALUE;
	}

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
		UndergroundProbe.Realm r = this.under.realm(x, z);
		if (r != null && r.id() == this.realm.id()) {
			int f = this.under.floorY(x, z, this.realm.floorY());
			if (f != Integer.MIN_VALUE) {
				return f;
			}
		}
		// the rock round the realm: as high as the vault
		return Math.max(this.realm.floorY() + 24, this.realm.ceilingY());
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
