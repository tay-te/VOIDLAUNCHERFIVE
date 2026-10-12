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
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

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
			case "forestfloor" -> forestFloor(level, nearX, nearZ);
			case "shore" -> shore(level, nearX, nearZ);
			case "street" -> street(level, nearX, nearZ);
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
	 * the floor lowest against the ground 40 blocks round it. The eye stands 40 blocks back along the cut (the way the
	 * ground 64 blocks out stays lowest), about 18 blocks over its rim and 6 over anything within 12 blocks of it (the
	 * rim's trees too), looking down along it at 18 to 35 degrees, so the far wall, the floor and the edge of the walls'
	 * shadow on it are all in the frame.
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
		double rad = Math.toRadians(yaw);
		int ex = bx + (int) Math.round(Math.sin(rad) * 40), ez = bz - (int) Math.round(Math.cos(rad) * 40);
		// clear of everything round the eye, trees on the rim too (a jungle rim's crowns filled the frame from 24 back)
		int top = 0;
		for (int dx = -12; dx <= 12; dx += 3) {
			for (int dz = -12; dz <= 12; dz += 3) {
				level.getChunk((ex + dx) >> 4, (ez + dz) >> 4);
				top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, ex + dx, ez + dz));
			}
		}
		int y = Math.max(ground + Math.max(14, (int) best + 18), top + 6);
		// looking down at the floor 24 blocks past the deepest point, 64 blocks out
		float pitch = Math.clamp((float) Math.toDegrees(Math.atan2(y - ground, 64)), 18.0F, 35.0F);
		System.out.printf(Locale.ROOT, "[gloam] light spot canyon: %s at %d %d (floor %.0f, ground %d, depth %.0f), eye %d %d %d yaw %.1f pitch %.1f%n",
			s.at(bx, bz).country(), bx, bz, floor, ground, best, ex, y, ez, yaw, pitch);
		return new int[]{ex, y, ez, (int) yaw, Math.round(pitch)};
	}

	/** Whether the feet and the eye could stand at (x, y, z): both blocks air or plants. */
	private static boolean standable(ServerLevel level, int x, int y, int z) {
		for (int dy = 0; dy < 2; dy++) {
			BlockState st = level.getBlockState(new BlockPos(x, y + dy, z));
			if (!st.isAir() && !st.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Under a broadleaf canopy (the forest biome nearest (nearX, nearZ), not a fantasy or jungle one): of the columns
	 * within 40 blocks of it with leaves overhead, the one and the yaw (of eight) that see furthest along the forest floor
	 * at eye height (up to 28 blocks) under the most canopy, so the frame shows the floor, the trunks and the light coming
	 * through. The feet on the ground, looking 2 degrees down.
	 */
	private static int[] forestFloor(ServerLevel level, int nearX, int nearZ) {
		var found = level.findClosestBiome3d(b -> b.is(BiomeTags.IS_FOREST) && b.unwrapKey().map(k -> {
			String name = k.identifier().getPath();
			return !name.contains("lumen") && !name.contains("wisteria") && !name.contains("cherry") && !name.contains("pale") && !name.contains("jungle");
		}).orElse(false), new BlockPos(nearX, 100, nearZ), 3000, 32, 64);
		if (found == null) {
			return null;
		}
		int cx = found.getFirst().getX(), cz = found.getFirst().getZ();
		float best = -1;
		int[] eye = null;
		for (int dx = -40; dx <= 40; dx += 4) {
			for (int dz = -40; dz <= 40; dz += 4) {
				int x = cx + dx, z = cz + dz;
				level.getChunk(x >> 4, z >> 4);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - ground < 3 || !standable(level, x, ground, z)
					|| level.getBlockState(new BlockPos(x, ground - 1, z)).getFluidState().is(FluidTags.WATER)) {
					continue;
				}
				for (int k = 0; k < 8; k++) {
					double rad = Math.toRadians(k * 45), sx = -Math.sin(rad), sz = Math.cos(rad);
					int clear = 0, roof = 0;
					for (int step = 1; step <= 28; step++) {
						int px = Mth.floor(x + 0.5 + sx * step), pz = Mth.floor(z + 0.5 + sz * step);
						level.getChunk(px >> 4, pz >> 4);
						int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
						if (g > ground + 2 || !standable(level, px, Math.max(g, ground), pz) && !standable(level, px, ground + 1, pz)) {
							break;
						}
						clear = step;
						if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz) - g >= 3) {
							roof++;
						}
					}
					float score = clear * (0.25F + roof / (float) Math.max(1, clear));
					if (clear >= 8 && score > best) {
						best = score;
						eye = new int[]{x, ground, z, k * 45, 2};
					}
				}
			}
		}
		System.out.printf(Locale.ROOT, "[gloam] light spot forestfloor: %s at %d %d, eye %s (score %.1f)%n", found.getSecond().getRegisteredName(), cx, cz,
			eye == null ? "none" : eye[0] + " " + eye[1] + " " + eye[2] + " yaw " + eye[3], best);
		return eye;
	}

	/**
	 * A lake shore: the nearest stretch of the Expanse's lake district (as the haze views find it, looking out over the
	 * lowest land), walked along that look until the water starts, the feet on the land 5 blocks short of it, looking
	 * out over the lake 3 degrees down.
	 */
	private static int[] shore(ServerLevel level, int nearX, int nearZ) {
		int[] c = GloamShowcase.findCountry(level, new GloamShowcase.View("shore", nearX, 0, nearZ, 0, 0, 70, 0, "clear", 0), "lake_district");
		if (c == null) {
			return null;
		}
		double rad = Math.toRadians(c[3]), sx = -Math.sin(rad), sz = Math.cos(rad);
		for (int d = 0; d <= 400; d++) {
			int x = Mth.floor(c[0] + 0.5 + sx * d), z = Mth.floor(c[2] + 0.5 + sz * d);
			level.getChunk(x >> 4, z >> 4);
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			if (level.getBlockState(new BlockPos(x, top, z)).getFluidState().is(FluidTags.WATER)) {
				int ex = Mth.floor(c[0] + 0.5 + sx * Math.max(0, d - 5)), ez = Mth.floor(c[2] + 0.5 + sz * Math.max(0, d - 5));
				level.getChunk(ex >> 4, ez >> 4);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ex, ez);
				System.out.printf(Locale.ROOT, "[gloam] light spot shore: water at %d %d %d, eye %d %d %d yaw %d%n", x, top, z, ex, ground + 1, ez, c[3]);
				return new int[]{ex, ground + 1, ez, c[3], 3};
			}
		}
		return null;
	}

	/**
	 * In a village's street at night: round the nearest village to (nearX, nearZ) (else building), the eye (of sixteen
	 * yaws by three rings, 14 to 30 blocks out, looking back at the middle) that stands clear, sees furthest toward the
	 * middle at eye height, and has the most light sources (any block giving light 10 or more) in front of it within 28
	 * blocks. The feet on the ground, looking 4 degrees down.
	 */
	private static int[] street(ServerLevel level, int nearX, int nearZ) {
		BlockPos from = new BlockPos(nearX, 100, nearZ);
		BlockPos at = null;
		for (String[] names : new String[][]{VILLAGES, BUILDINGS}) {
			HolderSet<Structure> set = structures(level, names);
			if (set.size() == 0) {
				continue;
			}
			var hit = level.getChunkSource().getGenerator().findNearestMapStructure(level, set, from, 100, false);
			if (hit != null) {
				at = hit.getFirst();
				break;
			}
		}
		if (at == null) {
			return null;
		}
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		int mid = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		List<int[]> lights = new ArrayList<>();
		for (int x = at.getX() - 40; x <= at.getX() + 40; x++) {
			for (int z = at.getZ() - 40; z <= at.getZ() + 40; z++) {
				level.getChunk(x >> 4, z >> 4);
				for (int y = mid - 8; y <= mid + 14; y++) {
					if (level.getBlockState(new BlockPos(x, y, z)).getLightEmission() >= 10) {
						lights.add(new int[]{x, y, z});
					}
				}
			}
		}
		float best = -1;
		int[] eye = null;
		for (int r = 14; r <= 30; r += 8) {
			for (int k = 0; k < 16; k++) {
				double rad = Math.toRadians(k * 22.5), sx = -Math.sin(rad), sz = Math.cos(rad);
				// the eye stands r back from the middle, against the look
				int x = Mth.floor(at.getX() + 0.5 - sx * r), z = Mth.floor(at.getZ() + 0.5 - sz * r);
				level.getChunk(x >> 4, z >> 4);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (!standable(level, x, ground, z) || level.getBlockState(new BlockPos(x, ground - 1, z)).getFluidState().is(FluidTags.WATER)) {
					continue;
				}
				int clear = 0;
				for (int step = 1; step <= 20; step++) {
					int px = Mth.floor(x + 0.5 + sx * step), pz = Mth.floor(z + 0.5 + sz * step);
					if (!standable(level, px, ground, pz) && !standable(level, px, ground + 1, pz)) {
						break;
					}
					clear = step;
				}
				int seen = 0;
				for (int[] l : lights) {
					double lx = l[0] + 0.5 - x, lz = l[2] + 0.5 - z, d = Math.hypot(lx, lz);
					if (d > 2 && d < 28 && (lx * sx + lz * sz) / d > Math.cos(Math.toRadians(32))) {
						seen++;
					}
				}
				float score = Math.min(clear, 16) + 4 * Math.min(seen, 8);
				if (clear >= 6 && score > best) {
					best = score;
					eye = new int[]{x, ground, z, Math.round(k * 22.5F), 4};
				}
			}
		}
		System.out.printf(Locale.ROOT, "[gloam] light spot street: village at %d %d %d, %d lights, eye %s (score %.1f)%n", at.getX(), mid, at.getZ(),
			lights.size(), eye == null ? "none" : eye[0] + " " + eye[1] + " " + eye[2] + " yaw " + eye[3], best);
		return eye;
	}
}
