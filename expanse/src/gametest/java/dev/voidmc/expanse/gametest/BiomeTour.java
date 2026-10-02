package dev.voidmc.expanse.gametest;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Flies round the world photographing each Expanse biome, with the HUD hidden and the weather clear:
 *
 * <ul>
 *   <li>a wide shot from above the canopy at noon,</li>
 *   <li>a ground shot from the nearest clearing, looking into the biome,</li>
 *   <li>a night shot for the biomes that glow,</li>
 *   <li>a high panorama, at a longer render distance, over the mountain biomes,</li>
 *   <li>and finally the four animals lined up on a stage in the sky.</li>
 * </ul>
 *
 * Run headless with {@code SDL_VIDEO_FORCE_EGL=1 xvfb-run ./gradlew runClientGameTest}; screenshots land
 * in build/run/clientGameTest/screenshots. System properties: {@code expanse.tour.biomes} (comma list),
 * {@code expanse.tour.seed}, {@code expanse.tour.distance}, {@code expanse.tour.settle} (ms per shot),
 * {@code expanse.tour.structures}, {@code expanse.tour.only} (comma list of structure names).
 */
public class BiomeTour implements FabricClientGameTest {
	// Software rendering under xvfb meshes chunks far slower than a GPU.
	private static final long SETTLE_MS = Long.getLong("expanse.tour.settle", 60_000L);
	private static final int DISTANCE = Integer.getInteger("expanse.tour.distance", 6);
	private static final int PANORAMA_DISTANCE = Integer.getInteger("expanse.tour.panorama", 10);
	private static final String SEED = System.getProperty("expanse.tour.seed", "8675309");
	/** Camera height over distance for structure shots: 0.58 is about 30 degrees down. */
	private static final float STEEP = Float.parseFloat(System.getProperty("expanse.tour.steep", "0.58"));
	private static final List<String> BIOMES = List.of(System.getProperty("expanse.tour.biomes",
		"wisteria_vale,lumen_grove,redwood_giants,jade_karst,opal_dunes,willow_bayou,amber_steppe,heather_moor,"
			+ "frostbloom_tundra,cloud_forest,verdant_peaks,prismatic_peaks,palm_coast").split(","));
	private static final Set<String> GLOWING = Set.of("lumen_grove", "prismatic_peaks", "frostbloom_tundra");
	private static final Set<String> MOUNTAINS = Set.of("verdant_peaks", "prismatic_peaks");

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(DISTANCE);
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(SEED);
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamemode spectator @a");
			for (String name : BIOMES) {
				if (!name.isBlank()) {
					this.visit(context, world, name.trim());
				}
			}
			if (Boolean.parseBoolean(System.getProperty("expanse.tour.structures", "true"))) {
				this.structures(context, world);
			}
			if (Boolean.parseBoolean(System.getProperty("expanse.tour.rivers", "true"))) {
				this.rivers(context, world);
			}
			if (Boolean.parseBoolean(System.getProperty("expanse.tour.caverns", "true"))) {
				this.caverns(context, world);
			}
			if (Boolean.parseBoolean(System.getProperty("expanse.tour.mountains", "true"))) {
				this.mountains(context, world);
			}
			if (Boolean.parseBoolean(System.getProperty("expanse.tour.zoo", "true"))) {
				this.zoo(context, world);
			}
		}
	}

	private record Spot(BlockPos heart, BlockPos clearing, int ceiling) {
	}

	private void visit(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("expanse", name));
		boolean mountain = MOUNTAINS.contains(name);
		Spot spot = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(h -> h.is(key), BlockPos.ZERO, 12000, 32, 64);
			if (hit == null) {
				return null;
			}
			BlockPos heart = heart(level, key, hit.getFirst());
			// Generate the neighbourhood here, on the server thread, before the client asks for it:
			// the integrated server cannot keep up generating while the CPU is also rasterising.
			int radius = (mountain ? PANORAMA_DISTANCE : DISTANCE) + 1;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					level.getChunk((heart.getX() >> 4) + dx, (heart.getZ() >> 4) + dz);
				}
			}
			heart = new BlockPos(heart.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, heart.getX(), heart.getZ()), heart.getZ());
			int ceiling = 0;
			for (int dx = -48; dx <= 48; dx += 8) {
				for (int dz = -48; dz <= 48; dz += 8) {
					ceiling = Math.max(ceiling, level.getHeight(Heightmap.Types.MOTION_BLOCKING, heart.getX() + dx, heart.getZ() + dz));
				}
			}
			return new Spot(heart, clearing(level, heart), ceiling);
		});
		if (spot == null) {
			System.out.println("[tour] " + name + " not found");
			return;
		}
		BlockPos h = spot.heart();
		System.out.println("[tour] " + name + " at " + h.toShortString() + ", clearing " + spot.clearing().toShortString());
		world.getServer().runCommand("time set noon");
		this.shoot(context, world, name + "_wide", h.getX() - 20, Math.max(spot.ceiling() + 12, h.getY() + 34), h.getZ() - 20, -45.0F, 28.0F);
		BlockPos c = spot.clearing();
		float yaw = yawToward(c, h);
		this.shoot(context, world, name + "_ground", c.getX(), c.getY() + 2, c.getZ(), yaw, 2.0F);
		if (GLOWING.contains(name)) {
			world.getServer().runCommand("time set midnight");
			this.shoot(context, world, name + "_night", c.getX(), c.getY() + 3, c.getZ(), yaw, 6.0F);
			world.getServer().runCommand("time set noon");
		}
		if (mountain) {
			context.runOnClient(mc -> mc.options.renderDistance().set(PANORAMA_DISTANCE));
			this.shoot(context, world, name + "_panorama", h.getX() - 90, spot.ceiling() + 30, h.getZ() - 90, -45.0F, 12.0F);
			context.runOnClient(mc -> mc.options.renderDistance().set(DISTANCE));
		}
	}

	/**
	 * Locating a biome finds its nearest edge. For a photograph we want its middle, on dry land: score
	 * points on a grid round the hit by how much of their surroundings is the biome; height only breaks
	 * ties, so a biome that also covers hills is shown on its lowlands, where its features are.
	 */
	private static BlockPos heart(ServerLevel level, ResourceKey<Biome> key, BlockPos near) {
		var generator = level.getChunkSource().getGenerator();
		var random = level.getChunkSource().randomState();
		var biomes = generator.getBiomeSource().createUncachedResolver(random);
		BlockPos best = near;
		int bestScore = Integer.MIN_VALUE;
		for (int gx = -6; gx <= 6; gx++) {
			for (int gz = -6; gz <= 6; gz++) {
				int x = near.getX() + gx * 24;
				int z = near.getZ() + gz * 24;
				int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
				int score = h < 64 ? -1000 : 0;
				for (int ox = -2; ox <= 2; ox++) {
					for (int oz = -2; oz <= 2; oz++) {
						if (biomes.getNoiseBiome((x + ox * 16) >> 2, Math.max(h, 64) >> 2, (z + oz * 16) >> 2).is(key)) {
							score += 10;
						}
					}
				}
				score -= Math.abs(gx) + Math.abs(gz);
				if (score > bestScore) {
					bestScore = score;
					best = new BlockPos(x, h, z);
				}
			}
		}
		return best;
	}

	/** The nearest spot to the heart with open sky above it (no canopy), so a ground shot is not taken from inside a tree. */
	private static BlockPos clearing(ServerLevel level, BlockPos heart) {
		for (int r = 6; r <= 40; r += 2) {
			for (int i = 0; i < 16; i++) {
				double a = i * Math.PI / 8;
				int x = heart.getX() + (int) Math.round(Math.cos(a) * r);
				int z = heart.getZ() + (int) Math.round(Math.sin(a) * r);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				if (top == ground && ground >= 63 && level.getBlockState(new BlockPos(x, ground - 1, z)).getFluidState().isEmpty()) {
					return new BlockPos(x, ground, z);
				}
			}
		}
		return heart.offset(-6, 0, -6);
	}

	private static float yawToward(BlockPos from, BlockPos to) {
		return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
	}

	/**
	 * Each Expanse structure: locate it, generate its surroundings, read where it actually landed from its
	 * structure start, and photograph it from whichever of eight directions has the clearest line of
	 * sight, standing back just far enough to fit it in.
	 */
	private void structures(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> names = world.getServer().computeOnServer(server -> server.overworld().registryAccess()
			.lookupOrThrow(Registries.STRUCTURE).listElementIds()
			.filter(k -> k.identifier().getNamespace().equals("expanse"))
			.map(k -> k.identifier().getPath()).sorted().toList());
		List<String> only = List.of(System.getProperty("expanse.tour.only", "").split(",")).stream().filter(n -> !n.isBlank()).toList();
		world.getServer().runCommand("time set noon");
		for (String name : names) {
			if (!only.isEmpty() && !only.contains(name)) {
				continue;
			}
			int[] shot = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				var ref = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("expanse", name)));
				var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
					net.minecraft.core.HolderSet.direct(ref), BlockPos.ZERO, 150, false);
				if (found == null) {
					return null;
				}
				BlockPos at = found.getFirst();
				var start = level.getChunk(at.getX() >> 4, at.getZ() >> 4).getStartForStructure(ref.value());
				if (start == null || !start.isValid()) {
					return null;
				}
				var box = start.getBoundingBox();
				int reach = (Math.max(box.getXSpan(), box.getZSpan()) >> 4) / 2 + 3;
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dz = -reach; dz <= reach; dz++) {
						level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
					}
				}
				// The start piece's bottom layer is the ground course; crypts and cellars below it would
				// otherwise drag the aim point underground.
				int ground = start.getPieces().getFirst().getBoundingBox().minY();
				int rise = Math.max(4, box.maxY() - ground);
				int tx = (box.minX() + box.maxX()) / 2;
				int tz = (box.minZ() + box.maxZ()) / 2;
				int ty = ground + rise * 2 / 5;
				int span = Math.max(box.getXSpan(), box.getZSpan());
				// Close enough to read the details, high enough (about 30 degrees down) to see over the
				// walls into courtyards and past the trees round the edge.
				double back = Math.max(22, span * 0.5 + rise * 0.3);
				BlockPos target = new BlockPos(tx, ty, tz);
				// A cavern-hall structure is shot from inside its hall: the camera looks for the hall's own air,
				// lower and closer than in the open, since the dome is in the way of anything steeper.
				boolean underground = ground < level.getHeight(Heightmap.Types.WORLD_SURFACE, tx, tz) - 24;
				double[] reaches = underground ? new double[]{1.0, 0.8, 0.6, 0.45} : new double[]{1.0};
				int[] best = null;
				int fewest = Integer.MAX_VALUE;
				for (int i = 0; i < 8; i++) {
					// Start from the south-west so ties keep the late-morning light on the walls we see.
					double a = Math.toRadians(225 + i * 45);
					for (double near : reaches) {
						double b = back * near;
						int cx = tx + (int) Math.round(Math.cos(a) * b);
						int cz = tz + (int) Math.round(Math.sin(a) * b);
						int cy;
						if (underground) {
							cy = ty + (int) (b * 0.4);
							while (cy > ty + 2 && !(level.getBlockState(new BlockPos(cx, cy, cz)).isAir()
								&& level.getBlockState(new BlockPos(cx, cy + 1, cz)).isAir())) {
								cy--;
							}
							if (!level.getBlockState(new BlockPos(cx, cy, cz)).isAir()) {
								continue;
							}
						} else {
							cy = Math.max(ty + (int) (b * STEEP), level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) + 3);
						}
						int blocked = blockedAlong(level, new BlockPos(cx, cy, cz), target);
						if (blocked < fewest) {
							fewest = blocked;
							// Render far enough to draw the far side of the structure, not just the near.
							int chunks = Mth.clamp((int) Math.ceil((b + span * 0.6) / 16) + 1, DISTANCE, 12);
							best = new int[]{tx, ty, tz, cx, cy, cz, chunks, underground ? 1 : 0};
						}
					}
				}
				return best;
			});
			if (shot == null) {
				System.out.println("[tour] structure " + name + " not found");
				continue;
			}
			System.out.println("[tour] structure " + name + " at " + shot[0] + ", " + shot[1] + ", " + shot[2]);
			BlockPos from = new BlockPos(shot[3], shot[4], shot[5]);
			BlockPos to = new BlockPos(shot[0], shot[1], shot[2]);
			float pitch = (float) -Math.toDegrees(Math.atan2(shot[1] - shot[4], Math.hypot(shot[0] - shot[3], shot[2] - shot[5])));
			int chunks = shot[6];
			context.runOnClient(mc -> mc.options.renderDistance().set(chunks));
			if (shot[7] == 1) {
				world.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
			}
			this.settleShot(context, world, "structure_" + name, from, yawToward(from, to), pitch, SETTLE_MS);
			if (shot[7] == 1) {
				world.getServer().runCommand("effect clear @a minecraft:night_vision");
			}
		}
		context.runOnClient(mc -> mc.options.renderDistance().set(DISTANCE));
	}

	/** Solid blocks on the straight line from the camera to the target, ignoring the last few blocks. */
	private static int blockedAlong(ServerLevel level, BlockPos from, BlockPos to) {
		double len = Math.sqrt(from.distSqr(to));
		int blocked = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (double t = 1; t < len - 6; t += 1) {
			double f = t / len;
			p.set(Mth.floor(Mth.lerp(f, from.getX(), to.getX()) + 0.5), Mth.floor(Mth.lerp(f, from.getY(), to.getY()) + 0.5),
				Mth.floor(Mth.lerp(f, from.getZ(), to.getZ()) + 0.5));
			if (!level.getBlockState(p).isAir() && level.getFluidState(p).isEmpty()) {
				blocked++;
			}
		}
		return blocked;
	}

	/**
	 * The tallest summit within five kilometres of spawn, photographed from 155 blocks off and 45 above
	 * its top, at a long render distance: the shot that shows what the Earth terrain does.
	 */
	private void mountains(ClientGameTestContext context, TestSingleplayerContext world) {
		int distance = Integer.getInteger("expanse.tour.mountain_distance", 12);
		int[] shot = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			var generator = level.getChunkSource().getGenerator();
			var random = level.getChunkSource().randomState();
			int bx = 0;
			int bz = 0;
			int by = Integer.MIN_VALUE;
			for (int x = -5000; x <= 5000; x += 64) {
				for (int z = -5000; z <= 5000; z += 64) {
					int y = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
					if (y > by) {
						bx = x;
						by = y;
						bz = z;
					}
				}
			}
			// Look down on it from above and off to one side, from whichever side is not in a fog biome:
			// the camera's biome sets the fog, and a cloud forest beside the peak would hide the range.
			var biomes = generator.getBiomeSource().createUncachedResolver(random);
			int cx = bx - 110;
			int cz = bz - 110;
			for (int i = 0; i < 8; i++) {
				double a = Math.PI * (1.25 + i / 4.0);
				int tx = bx + (int) Math.round(Math.cos(a) * 155);
				int tz = bz + (int) Math.round(Math.sin(a) * 155);
				var b = biomes.getNoiseBiome(tx >> 2, by >> 2, tz >> 2);
				boolean foggy = b.unwrapKey().map(k -> k.identifier().getNamespace().equals("expanse")
					&& Set.of("cloud_forest", "lumen_grove", "willow_bayou", "heather_moor", "redwood_giants", "jade_karst").contains(k.identifier().getPath())).orElse(false);
				if (!foggy) {
					cx = tx;
					cz = tz;
					break;
				}
			}
			for (int dx = -distance - 1; dx <= distance + 1; dx++) {
				for (int dz = -distance - 1; dz <= distance + 1; dz++) {
					level.getChunk((cx >> 4) + dx, (cz >> 4) + dz);
				}
			}
			int cy = by + 45;
			return new int[]{bx, by, bz, cx, cy, cz};
		});
		System.out.println("[tour] tallest summit " + shot[0] + ", " + shot[1] + ", " + shot[2]);
		context.runOnClient(mc -> mc.options.renderDistance().set(distance));
		world.getServer().runCommand("time set noon");
		BlockPos from = new BlockPos(shot[3], shot[4], shot[5]);
		BlockPos to = new BlockPos(shot[0], shot[1], shot[2]);
		float pitch = (float) -Math.toDegrees(Math.atan2(shot[1] - shot[4], Math.hypot(shot[0] - shot[3], shot[2] - shot[5]))) + 6.0F;
		this.settleShot(context, world, "mountains", from, yawToward(from, to), pitch, SETTLE_MS * 2);
		context.runOnClient(mc -> mc.options.renderDistance().set(DISTANCE));
	}

	/** The four animals posed on a grass stage high above the world, lit by noon sun. */
	/**
	 * The widest rivers within two kilometres of spawn, found through the Earth terrain model, each seen
	 * from above one bank looking along its course downstream.
	 */
	private void rivers(ClientGameTestContext context, TestSingleplayerContext world) {
		List<int[]> shots = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			if (!(level.getChunkSource().getGenerator() instanceof dev.voidmc.expanse.world.terrain.EarthChunkGenerator earth)) {
				return List.<int[]>of();
			}
			var model = earth.model(level.getChunkSource().randomState());
			List<float[]> found = new java.util.ArrayList<>();
			for (int x = -2000; x <= 2000; x += 24) {
				for (int z = -2000; z <= 2000; z += 24) {
					var c = model.sample(x, z);
					if (c.inChannel() && c.riverHalfWidth > 3) {
						found.add(new float[]{x, z, c.riverHalfWidth, c.riverWater});
					}
				}
			}
			found.sort((a, b) -> Float.compare(b[2], a[2]));
			List<int[]> picked = new java.util.ArrayList<>();
			for (float[] f : found) {
				boolean far = picked.stream().allMatch(p -> Math.hypot(p[0] - f[0], p[1] - f[1]) > 600);
				if (far) {
					int x = (int) f[0];
					int z = (int) f[1];
					// downstream: toward the lower water a little way along
					float best = Float.MAX_VALUE;
					int dx = 1;
					int dz = 0;
					for (int a = 0; a < 16; a++) {
						double ang = a * Math.PI / 8;
						int sx = x + (int) (Math.cos(ang) * 40);
						int sz = z + (int) (Math.sin(ang) * 40);
						var c = model.sample(sx, sz);
						if (c.inChannel() && c.riverWater < best) {
							best = c.riverWater;
							dx = (int) Math.round(Math.cos(ang) * 100);
							dz = (int) Math.round(Math.sin(ang) * 100);
						}
					}
					picked.add(new int[]{x, z, (int) f[3], dx, dz, (int) (f[2] * 2)});
					if (picked.size() == 3) {
						break;
					}
				}
			}
			for (int[] p : picked) {
				for (int cx = -3; cx <= 3; cx++) {
					for (int cz = -3; cz <= 3; cz++) {
						level.getChunk((p[0] >> 4) + cx, (p[1] >> 4) + cz);
					}
				}
			}
			return picked;
		});
		world.getServer().runCommand("time set noon");
		int i = 0;
		for (int[] r : shots) {
			System.out.println("[tour] river " + r[5] + " wide at " + r[0] + ", " + r[2] + ", " + r[1]);
			// stand back upstream and above, looking down the river
			double len = Math.hypot(r[3], r[4]);
			int bx = r[0] - (int) (r[3] / len * 45);
			int bz = r[1] - (int) (r[4] / len * 45);
			BlockPos from = new BlockPos(bx, r[2] + 28, bz);
			BlockPos to = new BlockPos(r[0] + (int) (r[3] / len * 40), r[2], r[1] + (int) (r[4] / len * 40));
			float pitch = (float) -Math.toDegrees(Math.atan2(to.getY() - from.getY(), Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ())));
			this.settleShot(context, world, "river_" + i++, from, yawToward(from, to), pitch, SETTLE_MS);
		}
	}

	/**
	 * The underground near spawn, with night vision since nothing lights it but what grows there: the two
	 * biggest halls seen from halfway up, and two great tunnels (one with a river) seen along their length.
	 */
	/**
	 * The world underground: for a realm of each character near spawn, a view across it from high on its
	 * side and one from its floor; then the reveal from a tunnel mouth, and a river tunnel and a dry
	 * tunnel. Under night vision, but one mushroom realm also as it is, by its own light.
	 */
	private void caverns(ClientGameTestContext context, TestSingleplayerContext world) {
		List<int[]> shots = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			if (!(level.getChunkSource().getGenerator() instanceof dev.voidmc.expanse.world.terrain.EarthChunkGenerator earth)) {
				return List.<int[]>of();
			}
			var caverns = earth.model(level.getChunkSource().randomState()).caverns();
			List<int[]> picked = new java.util.ArrayList<>();
			// a realm of each character, nearest spawn first
			List<dev.voidmc.expanse.world.terrain.CavernModel.Hall> realms = new java.util.ArrayList<>();
			for (int hx = -4; hx <= 4; hx++) {
				for (int hz = -4; hz <= 4; hz++) {
					var h = caverns.hall(hx, hz);
					if (h.exists() && h.radius() > 80 && caverns.info(h.x(), h.z()).hall) {
						realms.add(h);
					}
				}
			}
			realms.sort(java.util.Comparator.comparingDouble(h -> Math.hypot(h.x(), h.z())));
			java.util.Set<Integer> themes = new java.util.HashSet<>();
			int[] mouth = null;
			for (var h : realms) {
				if (!themes.add(h.theme())) {
					continue;
				}
				// from high on one side, across the realm to the far side's floor
				double a = (h.x() * 31 + h.z()) % 8 * Math.PI / 4;
				int fx = h.x() + (int) (Math.cos(a) * h.radius() * 0.6);
				int fz = h.z() + (int) (Math.sin(a) * h.radius() * 0.6);
				var at = caverns.info(fx, fz);
				int eye = (int) (at.floor + 0.6 * (at.roof - at.floor));
				int tx = h.x() - (int) (Math.cos(a) * h.radius() * 0.5);
				int tz = h.z() - (int) (Math.sin(a) * h.radius() * 0.5);
				picked.add(new int[]{fx, eye, fz, tx, (int) caverns.info(tx, tz).floor + 8, tz, h.theme(), 1});
				// from the floor beside the plateau, out across the land
				int gx = h.x() + (int) (Math.cos(a + 1.6) * 46);
				int gz = h.z() + (int) (Math.sin(a + 1.6) * 46);
				var g = caverns.info(gx, gz);
				int ox = h.x() + (int) (Math.cos(a + 1.6) * h.radius() * 0.8);
				int oz = h.z() + (int) (Math.sin(a + 1.6) * h.radius() * 0.8);
				picked.add(new int[]{gx, (int) Math.max(g.floor, g.liquid()) + 4, gz, ox, (int) Math.max(g.floor, g.liquid()) + 10, oz, h.theme(), 1});
				if (h.theme() == dev.voidmc.expanse.world.terrain.CavernModel.WILDS) {
					picked.add(new int[]{gx, (int) Math.max(g.floor, g.liquid()) + 4, gz, ox, (int) Math.max(g.floor, g.liquid()) + 10, oz, h.theme(), 0});
				}
				// a dry tunnel's mouth on this realm's wall
				for (int k = 0; k < 720 && mouth == null; k++) {
					double b = k * Math.PI / 360;
					for (int r = (int) (h.radius() * 0.7); r < h.radius() + 40; r += 3) {
						int mx = h.x() + (int) (Math.cos(b) * r);
						int mz = h.z() + (int) (Math.sin(b) * r);
						var m = caverns.info(mx, mz);
						if (m.tunnel && m.tunnelDist < 2 && m.hallEdge < -10 && m.hallEdge > -30) {
							mouth = new int[]{mx, (int) m.tunnelFloor + 4, mz, h.x(), (int) caverns.info(h.x(), h.z()).floor + 10, h.z(), h.theme(), 1};
							break;
						}
					}
				}
			}
			if (mouth != null) {
				picked.add(mouth);
			}
			int[] river = null;
			int[] tunnel = null;
			for (int x = -1500; x <= 1500 && (river == null || tunnel == null); x += 12) {
				for (int z = -1500; z <= 1500; z += 12) {
					var c = caverns.info(x, z);
					if (river == null && c.inChannel() && c.riverDist < 2 && !c.hall && c.tunnelRoof - c.riverWater > 20) {
						river = new int[]{x, z, c.waterTop() + 4};
					}
					if (tunnel == null && c.tunnel && c.tunnelDist < 2 && !c.hall && !c.river && c.tunnelHeight > 26) {
						tunnel = new int[]{x, z, (int) c.tunnelFloor + 5};
					}
				}
			}
			for (int[] t : new int[][]{river, tunnel}) {
				if (t == null) {
					continue;
				}
				boolean wet = t == river;
				// look along the tunnel: the direction in which its centre line carries on
				double bestAngle = 0;
				float bestDist = Float.MAX_VALUE;
				for (int a = 0; a < 24; a++) {
					double ang = a * Math.PI / 12;
					var c = caverns.info(t[0] + (int) (Math.cos(ang) * 36), t[1] + (int) (Math.sin(ang) * 36));
					float dist = wet ? c.riverDist : c.tunnelDist;
					if (dist < bestDist) {
						bestDist = dist;
						bestAngle = ang;
					}
				}
				picked.add(new int[]{t[0], t[2], t[1], t[0] + (int) (Math.cos(bestAngle) * 60), t[2] - 3, t[1] + (int) (Math.sin(bestAngle) * 60), -1, 1});
			}
			for (int[] p : picked) {
				for (int cx = -5; cx <= 5; cx++) {
					for (int cz = -5; cz <= 5; cz++) {
						level.getChunk((p[0] >> 4) + cx, (p[2] >> 4) + cz);
					}
				}
			}
			return picked;
		});
		String[] names = {"wilds", "lush", "dripstone", "crystal", "ember", "mere"};
		context.runOnClient(mc -> mc.options.renderDistance().set(12));
		int i = 0;
		for (int[] s : shots) {
			String name = (s[6] < 0 ? "tunnel" : "realm_" + names[s[6]]) + (s[7] == 0 ? "_dark" : "") + "_" + i++;
			System.out.println("[tour] " + name + " at " + s[0] + ", " + s[1] + ", " + s[2] + " looking at " + s[3] + ", " + s[4] + ", " + s[5]);
			world.getServer().runCommand(s[7] == 1 ? "effect give @a minecraft:night_vision infinite 0 true" : "effect clear @a minecraft:night_vision");
			BlockPos from = new BlockPos(s[0], s[1], s[2]);
			BlockPos to = new BlockPos(s[3], s[4], s[5]);
			float pitch = (float) -Math.toDegrees(Math.atan2(to.getY() - from.getY(), Math.max(1, Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()))));
			this.settleShot(context, world, "cavern_" + name, from, yawToward(from, to), pitch, SETTLE_MS);
		}
		world.getServer().runCommand("effect clear @a minecraft:night_vision");
		context.runOnClient(mc -> mc.options.renderDistance().set(DISTANCE));
	}

	private void zoo(ClientGameTestContext context, TestSingleplayerContext world) {
		int y = 300;
		world.getServer().runCommand("time set noon");
		// Stand there first: /fill and /summon only work in loaded chunks.
		world.getServer().runCommand(String.format("tp @a 0 %d -9 0 12", y + 2));
		this.settle(context, 8_000L);
		world.getServer().runCommand(String.format("fill -10 %d -6 10 %d 8 minecraft:grass_block", y - 1, y - 1));
		world.getServer().runCommand(String.format("summon expanse:mammoth -4 %d 3 {NoAI:1b,Rotation:[150f,0f]}", y));
		world.getServer().runCommand(String.format("summon expanse:elk 3 %d 2 {NoAI:1b,Rotation:[210f,0f]}", y));
		world.getServer().runCommand(String.format("summon expanse:elk 6 %d 0 {NoAI:1b,Age:-24000,Rotation:[230f,0f]}", y));
		world.getServer().runCommand(String.format("summon expanse:capybara 0 %d -2 {NoAI:1b,Rotation:[190f,0f]}", y));
		world.getServer().runCommand(String.format("summon expanse:crab -2 %d -3 {NoAI:1b,Rotation:[90f,0f]}", y));
		this.shoot(context, world, "zoo", 0, y + 1, -7, 0.0F, 8.0F);
	}

	private void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name, int x, int y, int z, float yaw, float pitch) {
		this.settleShot(context, world, name, new BlockPos(x, y, z), yaw, pitch, SETTLE_MS);
	}

	private void settleShot(ClientGameTestContext context, TestSingleplayerContext world, String name, BlockPos at, float yaw, float pitch, long ms) {
		world.getServer().runCommand(String.format("tp @a %d %d %d %.1f %.1f", at.getX(), at.getY(), at.getZ(), Mth.wrapDegrees(yaw), pitch));
		// A fixed wall-clock wait rather than waitForChunksRender: under software rendering the client
		// never quite reaches "every chunk meshed", but it is long done with the ones in view.
		this.settle(context, ms);
		context.takeScreenshot("expanse_" + name);
	}

	private void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}
}
