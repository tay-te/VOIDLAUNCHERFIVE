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
 * {@code expanse.tour.seed}, {@code expanse.tour.distance}, {@code expanse.tour.settle} (ms per shot).
 */
public class BiomeTour implements FabricClientGameTest {
	// Software rendering under xvfb meshes chunks far slower than a GPU.
	private static final long SETTLE_MS = Long.getLong("expanse.tour.settle", 60_000L);
	private static final int DISTANCE = Integer.getInteger("expanse.tour.distance", 6);
	private static final int PANORAMA_DISTANCE = Integer.getInteger("expanse.tour.panorama", 10);
	private static final String SEED = System.getProperty("expanse.tour.seed", "8675309");
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
	 * The tallest summit within five kilometres of spawn, photographed from 155 blocks off and 45 above
	 * its top, at a long render distance: the shot that shows what Grand Scale does.
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
