package dev.voidmc.gloam.gametest;

import dev.voidmc.expanse.api.ExpanseCountry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The spots VOID Welkin's light views can be asked to find by name (-Pgloam.show.light=village;canyon, see
 * GloamShowcase.addLightViews): a village (or, failing one, another Expanse building) seen from the south, so the
 * afternoon sun falls across it from the left, and a gorge seen along its floor. Each returns {x, y, z, yaw, pitch} for
 * the eye, or null when nothing is found.
 */
final class LightPlaces {
	private static final String[] VILLAGES = {"village_wisteria", "village_redwood", "village_coast", "village_steppe", "village_moor", "village_karst",
		"village_tundra"};
	private static final String[] BUILDINGS = {"ruined_keep", "hill_chapel", "collapsed_hamlet", "stilt_hamlet", "caravanserai", "wisteria_manor",
		"woodland_lodge", "frost_outpost", "ruined_watchtower", "logging_camp", "lighthouse"};

	private LightPlaces() {
	}

	static int[] find(ServerLevel level, String kind, int nearX, int nearZ) {
		return switch (kind) {
			case "village" -> village(level, nearX, nearZ);
			case "canyon" -> canyon(level, nearX, nearZ);
			default -> null;
		};
	}

	private static HolderSet<Structure> structures(ServerLevel level, String[] names) {
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Holder<Structure>> found = new ArrayList<>();
		for (String n : names) {
			registry.get(Identifier.fromNamespaceAndPath("expanse", n)).ifPresent(found::add);
		}
		return HolderSet.direct(found);
	}

	/** The nearest village (else building) within 1600 blocks; the eye 64 blocks south of it and 34 over its ground, looking north. */
	private static int[] village(ServerLevel level, int nearX, int nearZ) {
		BlockPos from = new BlockPos(nearX, 100, nearZ);
		for (String[] names : new String[][]{VILLAGES, BUILDINGS}) {
			HolderSet<Structure> set = structures(level, names);
			if (set.size() == 0) {
				continue;
			}
			var hit = level.getChunkSource().getGenerator().findNearestMapStructure(level, set, from, 100, false);
			if (hit == null) {
				continue;
			}
			BlockPos at = hit.getFirst();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
			int ex = at.getX(), ez = at.getZ() + 64;
			level.getChunk(ex >> 4, ez >> 4);
			int y = Math.max(ground + 34, level.getHeight(Heightmap.Types.MOTION_BLOCKING, ex, ez) + 6);
			float pitch = (float) Math.toDegrees(Math.atan2(y - ground, 64));
			System.out.printf(Locale.ROOT, "[gloam] light spot village: %s at %d %d %d, eye %d %d %d%n", hit.getSecond().getRegisteredName(), at.getX(),
				ground, at.getZ(), ex, y, ez);
			return new int[]{ex, y, ez, 180, (int) pitch};
		}
		return null;
	}

	/**
	 * The deepest cut near the nearest stretch of the Expanse's gorges country (within 24 km), else near (nearX, nearZ):
	 * the floor lowest against the ground 40 blocks round it; the eye a third of the way up its walls, looking along it
	 * (the way the ground 64 blocks out stays lowest).
	 */
	private static int[] canyon(ServerLevel level, int nearX, int nearZ) {
		ExpanseCountry.Sampler s = ExpanseCountry.sampler(level);
		if (s == null) {
			return null;
		}
		int cx = nearX, cz = nearZ;
		search:
		for (int r = 0; r <= 24000; r += 192) {
			int n = r == 0 ? 1 : Math.max(8, (int) (2 * Math.PI * r / 192));
			for (int i = 0; i < n; i++) {
				double a = 2 * Math.PI * i / n;
				int x = nearX + (int) (Math.cos(a) * r), z = nearZ + (int) (Math.sin(a) * r);
				var h = s.at(x, z);
				if (h.country().equals("gorges") && h.strength() > 0.8F) {
					cx = x;
					cz = z;
					break search;
				}
			}
		}
		int sea = level.getSeaLevel();
		float best = 0;
		int bx = cx, bz = cz;
		for (int dx = -384; dx <= 384; dx += 8) {
			for (int dz = -384; dz <= 384; dz += 8) {
				int x = cx + dx, z = cz + dz;
				float floor = s.at(x, z).altitude();
				if (floor < sea + 3) {
					continue;
				}
				float rim = 0;
				for (int k = 0; k < 8; k++) {
					double a = k * Math.PI / 4;
					rim += s.at(x + (int) (Math.cos(a) * 40), z + (int) (Math.sin(a) * 40)).altitude();
				}
				float depth = rim / 8 - floor;
				if (depth > best) {
					best = depth;
					bx = x;
					bz = z;
				}
			}
		}
		float floor = s.at(bx, bz).altitude();
		float yaw = 0, lowest = Float.MAX_VALUE;
		for (int k = 0; k < 16; k++) {
			double rad = Math.toRadians(k * 22.5);
			float far = s.at(bx - (int) (Math.sin(rad) * 64), bz + (int) (Math.cos(rad) * 64)).altitude();
			if (far < lowest) {
				lowest = far;
				yaw = k * 22.5F;
			}
		}
		level.getChunk(bx >> 4, bz >> 4);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		int y = ground + Math.max(10, (int) (best / 3));
		System.out.printf(Locale.ROOT, "[gloam] light spot canyon: %s at %d %d (floor %.0f, ground %d, depth %.0f), eye y %d yaw %.1f%n",
			s.at(bx, bz).country(), bx, bz, floor, ground, best, y, yaw);
		return new int[]{bx, y, bz, (int) yaw, 6};
	}
}
