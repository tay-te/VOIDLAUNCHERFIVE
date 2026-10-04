package dev.voidmc.hearth.gametest.plan;

import dev.voidmc.expanse.world.terrain.CavernModel;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.hearth.api.UndergroundProbe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The realms under Expanse's Earth terrain, read from its cavern model, for previews of deep towns on real worlds: the
 * same model the sim module's underground probe reads in game. A column is in a realm when the model puts it inside a
 * realm's outline with open space over its floor; pillars and lakes have no floor to build on.
 */
public final class EarthUnderground implements UndergroundProbe {
	private static final String[] THEMES = {"glowcap_wilds", "lush", "dripstone", "crystal", "ember", "mere", "frozen", "roots", "fossil", "tidal",
		"amber_hollows", "brimstone"};
	private static final int CELL = 640;

	private final CavernModel caverns;

	public EarthUnderground(long worldSeed) {
		this.caverns = TerrainModel.forSeed(TerrainModel.seedFor(worldSeed)).caverns();
	}

	private static long id(int hx, int hz) {
		return ((long) hx << 32) ^ (hz & 0xFFFFFFFFL) ^ 0x5245414CL;
	}

	private Realm realmOf(CavernModel.Hall h) {
		CavernModel.Info centre = this.caverns.info(h.x(), h.z());
		int floor = (int) Math.ceil(h.floor());
		int roof = centre.hall ? (int) Math.floor(centre.roof) : floor + (int) h.height();
		String character = h.theme() >= 0 && h.theme() < THEMES.length ? THEMES[h.theme()] : "dripstone";
		return new Realm(id(Math.floorDiv(h.x(), CELL), Math.floorDiv(h.z(), CELL)), new BlockPos(h.x(), floor, h.z()), (int) h.radius(), floor, roof,
			character);
	}

	@Override
	public synchronized @Nullable Realm realm(int x, int z) {
		CavernModel.Info c = this.caverns.info(x, z);
		if (!c.hall || c.hallEdge <= 0) {
			return null;
		}
		CavernModel.Hall h = this.caverns.hall(Math.floorDiv(c.hallX, CELL), Math.floorDiv(c.hallZ, CELL));
		return h.exists() ? realmOf(h) : null;
	}

	@Override
	public synchronized List<Realm> realmsNear(int x, int z, int radius) {
		List<Realm> out = new ArrayList<>();
		int r = radius / CELL + 1;
		int hx = Math.floorDiv(x, CELL);
		int hz = Math.floorDiv(z, CELL);
		for (int dz = -r; dz <= r; dz++) {
			for (int dx = -r; dx <= r; dx++) {
				CavernModel.Hall h = this.caverns.hall(hx + dx, hz + dz);
				if (h.exists() && Math.hypot(h.x() - x, h.z() - z) <= radius) {
					out.add(realmOf(h));
				}
			}
		}
		return out;
	}

	@Override
	public synchronized int floorY(int x, int z, int nearY) {
		CavernModel.Info c = this.caverns.info(x, z);
		if (!c.hall || c.hallEdge <= 2 || c.pillar < 4 || c.floor <= c.lake + 0.5F || c.lava) {
			return Integer.MIN_VALUE;
		}
		return (int) Math.ceil(c.floor);
	}

	@Override
	public synchronized int ceilingY(int x, int z, int floorY) {
		CavernModel.Info c = this.caverns.info(x, z);
		return c.hall ? (int) Math.floor(c.roof) : Integer.MIN_VALUE;
	}
}
