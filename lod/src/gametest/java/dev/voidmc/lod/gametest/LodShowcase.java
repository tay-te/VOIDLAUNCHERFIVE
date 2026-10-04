package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.VoidLod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Photographs an Earth world with the LOD off and on from a few kinds of place: a high peak looking out
 * each way, a low spot looking across the land, and the air high over the peak, logging what the LOD drew
 * and what it cost.
 *
 * <p>Run with {@code ./gradlew runClientGameTest} (headless on Linux: {@code SDL_VIDEO_FORCE_EGL=1 xvfb-run});
 * screenshots land in build/run/clientGameTest/screenshots. Properties (pass as -P to Gradle):
 * {@code lod.show.seed}, {@code lod.show.distance} (vanilla render distance), {@code lod.show.settle} (ms for
 * vanilla chunks), {@code lod.show.views} (how many of the views to take), {@code lod.show.width} and
 * {@code lod.show.height} (screenshot size), {@code lod.show.off} (false to skip the vanilla shots),
 * {@code lod.show.only} (a comma-separated list of view names), {@code lod.show.ingest} (true: build something big in
 * loaded chunks, photograph it from past the render distance, then leave, rejoin and photograph it again, from what
 * was saved) and {@code lod.show.mp} (true: the same on a dedicated server, where only seen land is drawn).
 */
public class LodShowcase implements FabricClientGameTest {
	private static final String SEED = System.getProperty("lod.show.seed", "8675309");
	private static final int DISTANCE = Integer.getInteger("lod.show.distance", 8);
	private static final long SETTLE_MS = Long.getLong("lod.show.settle", 30_000L);
	private static final int VIEWS = Integer.getInteger("lod.show.views", 10);
	/** Only the views with these names, comma separated (all when unset). */
	private static final String ONLY = System.getProperty("lod.show.only", "");
	private static final int WIDTH = Integer.getInteger("lod.show.width", 1600);
	private static final int HEIGHT = Integer.getInteger("lod.show.height", 900);
	private static final boolean OFF = Boolean.parseBoolean(System.getProperty("lod.show.off", "true"));
	private static final boolean INGEST = Boolean.getBoolean("lod.show.ingest");
	private static final boolean MULTIPLAYER = Boolean.getBoolean("lod.show.mp");
	/** How far from the build the camera stands to photograph it: well past the render distance. */
	private static final int AWAY = 480;

	private record Spot(String name, int x, int y, int z, float yaw, float pitch, int fov) {
		Spot(String name, int x, int y, int z, float yaw, float pitch) {
			this(name, x, y, z, yaw, pitch, 70);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(DISTANCE);
			// measure real frame rates: no vsync, no cap, no throttling while the window is in the background
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		});
		TestWorldSave save;
		Spot after = null;
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(SEED);
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			save = world.getWorldSave();
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamemode spectator @a");

			List<Spot> spots = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				List<Spot> found = new ArrayList<>();
				if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
					found.add(new Spot("view0", 0, 200, 0, 0, 12));
					return found;
				}
				TerrainModel model = earth.model(level.getChunkSource().randomState());
				// the highest ground within 3 km, and the lowest dry land within 2 km of it
				int bx = 0, bz = 0, lx = 0, lz = 0;
				float best = -1, low = Float.MAX_VALUE;
				for (int x = -3000; x <= 3000; x += 48) {
					for (int z = -3000; z <= 3000; z += 48) {
						float h = model.sample(x, z).height;
						if (h > best) {
							best = h;
							bx = x;
							bz = z;
						}
					}
				}
				for (int x = bx - 2000; x <= bx + 2000; x += 40) {
					for (int z = bz - 2000; z <= bz + 2000; z += 40) {
						var c = model.sample(x, z);
						if (c.height > 68 && c.height < low && !c.nearRiver() && Math.hypot(x - bx, z - bz) > 900) {
							low = c.height;
							lx = x;
							lz = z;
						}
					}
				}
				for (int[] at : new int[][]{{bx, bz}, {lx, lz}}) {
					for (int dx = -DISTANCE - 1; dx <= DISTANCE + 1; dx++) {
						for (int dz = -DISTANCE - 1; dz <= DISTANCE + 1; dz++) {
							level.getChunk((at[0] >> 4) + dx, (at[1] >> 4) + dz);
						}
					}
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
				for (int v = 0; v < 4; v++) {
					found.add(new Spot("peak" + v, bx, top + 24, bz, v * 90.0F, 12));
				}
				int lowTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, lx, lz);
				float toPeak = (float) Math.toDegrees(Math.atan2(-(bx - lx), bz - lz));
				found.add(new Spot("low0", lx, lowTop + 12, lz, toPeak, 2));
				found.add(new Spot("low1", lx, lowTop + 12, lz, toPeak + 180, 2));
				found.add(new Spot("air", bx, top + 420, bz, 45, 32));
				// the far distance through a long lens: what a few kilometres away looks like up close
				found.add(new Spot("far0", bx, top + 24, bz, 90, 4, 30));
				found.add(new Spot("far1", lx, lowTop + 12, lz, toPeak, 2, 30));
				found.add(new Spot("far2", bx, top + 24, bz, 0, 3, 30));
				System.out.printf(Locale.ROOT, "[lod] high point %d,%d at y=%d; low point %d,%d at y=%d%n", bx, bz, top, lx, lz, lowTop);
				return found;
			});

			Spot valley = spots.stream().filter(spot -> spot.name().equals("low0")).findFirst().orElse(spots.get(0));
			if (!ONLY.isEmpty()) {
				List<String> names = List.of(ONLY.split(","));
				spots.removeIf(spot -> !names.contains(spot.name()));
			}
			for (int i = 0; i < Math.min(VIEWS, spots.size()); i++) {
				Spot spot = spots.get(i);
				context.runOnClient(mc -> mc.options.fov().set(spot.fov()));
				world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch()));
				Spot last = i == 0 ? null : spots.get(i - 1);
				boolean moved = last == null || last.x() != spot.x() || last.y() != spot.y() || last.z() != spot.z();

				if (OFF) {
					context.runOnClient(mc -> VoidLod.get().setEnabled(false));
					this.settle(context, moved ? SETTLE_MS : 2_000);
					this.shoot(context, spot.name() + "_off");
				} else if (moved) {
					this.settle(context, SETTLE_MS);
				}

				context.runOnClient(mc -> VoidLod.get().setEnabled(true));
				this.photograph(context, spot.name(), false);
			}

			if (INGEST) {
				after = this.build(context, world.getServer(), valley.x(), valley.z(), "ingest");
			}
		}
		if (after != null) {
			// back into the same world, standing where the last photograph was taken: the build's chunks are past the
			// render distance and never load this time, so if it shows, it came off the disk
			try (TestSingleplayerContext again = save.open()) {
				context.runOnClient(mc -> VoidLod.get().setEnabled(true));
				this.settle(context, SETTLE_MS);
				this.photograph(context, "ingest_rejoin", true);
				String files = save.getSaveDirectory().resolve(VoidLod.ID).toString();
				System.out.printf(Locale.ROOT, "[lod] saved land in %s: %d KB%n", files, size(save.getSaveDirectory().resolve(VoidLod.ID)) / 1024);
			}
		}
		if (MULTIPLAYER) {
			this.multiplayer(context);
		}
	}

	/**
	 * Waits for the LOD to finish building what it wants (twice in a row, so a view that has not caught up with the
	 * teleport yet does not count), then a few frames more to upload and fade in; photographs it and logs what it
	 * drew and cost. With {@code changes}, also waits (a while at most) for tiles whose land changed to be rebuilt.
	 */
	private void photograph(ClientGameTestContext context, String name, boolean changes) {
		long start = System.currentTimeMillis();
		long firstFrame = context.computeOnClient(mc -> VoidLod.get().engine().frame());
		int calm = 0;
		while (System.currentTimeMillis() - start < 180_000 && calm < 3) {
			context.waitTicks(10);
			// done once it has drawn a while from here, holds tiles, and has nothing left to build
			// a fresh world keeps changing for a while (grass spreading, leaves falling, water finding its level)
			boolean settled = System.currentTimeMillis() - start > 30_000;
			String state = context.computeOnClient(mc -> {
				var engine = VoidLod.get().engine();
				var ingest = VoidLod.get().ingest();
				boolean done = engine.frame() - firstFrame > 200 && engine.tileCount() > 0 && engine.idle() && engine.queued() == 0
					&& (!changes || settled || engine.dirtyDrawn() == 0 && (ingest == null || ingest.queued() == 0));
				return (done ? "done " : "busy ") + (engine.frame() - firstFrame) + " frames, " + engine.busy();
			});
			boolean idle = state.startsWith("done");
			if (Boolean.getBoolean("lod.show.trace")) {
				System.out.println("[lod] waiting: " + state);
			}
			calm = idle ? calm + 1 : 0;
		}
		long loaded = System.currentTimeMillis() - start;
		context.waitTicks(60);
		this.shoot(context, name + "_on");
		String stats = context.computeOnClient(mc -> VoidLod.get().stats() + String.format(Locale.ROOT, ", %d fps", mc.getFps()));
		System.out.printf(Locale.ROOT, "[lod] %s: complete in %.1f s | %s%n", name, loaded / 1000.0, stats);
	}

	/**
	 * Builds something big and unmistakable at {@code (x, z)} while its chunks are loaded (a red concrete deck pierced
	 * by a gold tower, a trench cut across the land), and photographs it from {@value #AWAY} blocks away, past the
	 * render distance: before, so the land is known, and after. It can only be in the second photograph if the
	 * loaded chunks went into the LOD. Returns where the photographs were taken from.
	 */
	private Spot build(ClientGameTestContext context, TestServerContext server, int x, int z, String name) {
		int[] heights = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			return new int[]{level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + AWAY, z)};
		});
		int ground = heights[0];
		int eye = Math.max(ground, heights[1]) + 140;
		float pitch = (float) Math.toDegrees(Math.atan2(eye - (ground + 40), AWAY));
		Spot from = new Spot(name, x + AWAY, eye, z, 90, pitch);
		server.runCommand("gamerule max_block_modifications 1000000");

		server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", from.x(), from.y(), from.z(), from.yaw(), from.pitch()));
		context.runOnClient(mc -> VoidLod.get().setEnabled(true));
		this.settle(context, SETTLE_MS);
		this.photograph(context, name + "_before", false);

		server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 60", x, ground + 60, z));
		this.settle(context, SETTLE_MS);
		long t0 = System.currentTimeMillis();
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d gold_block", x - 4, ground - 2, z - 4, x + 3, ground + 120, z + 3));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d red_concrete", x - 64, ground + 40, z - 64, x + 63, ground + 42, z + 63));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d gold_block", x - 4, ground + 40, z - 4, x + 3, ground + 120, z + 3));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 64, ground - 24, z + 72, x + 63, ground + 80, z + 95));
		// the block updates reach the client, settle, and are read
		for (int i = 0; i < 60; i++) {
			context.waitTicks(10);
			if (i >= 4 && context.computeOnClient(mc -> VoidLod.get().ingest() == null || VoidLod.get().ingest().queued() == 0)) {
				break;
			}
		}
		String ingest = context.computeOnClient(mc -> VoidLod.get().stats());
		System.out.printf(Locale.ROOT, "[lod] built at %d,%d (ground %d), read in %.1f s | %s%n", x, z, ground, (System.currentTimeMillis() - t0) / 1000.0,
			ingest);

		server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", from.x(), from.y(), from.z(), from.yaw(), from.pitch()));
		this.settle(context, SETTLE_MS);
		context.runOnClient(mc -> VoidLod.get().setEnabled(false));
		context.waitTicks(20);
		this.shoot(context, name + "_after_off");
		context.runOnClient(mc -> VoidLod.get().setEnabled(true));
		this.photograph(context, name + "_after", true);
		return from;
	}

	/**
	 * The same on a dedicated server: no generator to sample, so the LOD draws only the land the client has seen.
	 * The build is photographed from far off; the land around it that was never loaded is not drawn at all.
	 */
	private void multiplayer(ClientGameTestContext context) {
		java.util.Properties properties = new java.util.Properties();
		properties.setProperty("level-seed", SEED);
		properties.setProperty("view-distance", String.valueOf(DISTANCE));
		properties.setProperty("gamemode", "spectator");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
			TestDedicatedServerConnection connection = server.connect()) {
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("time set 6000");
			server.runCommand("gamemode spectator @a");
			context.runOnClient(mc -> mc.options.fov().set(70));
			int[] at = server.computeOnServer(s -> {
				var spawn = s.overworld().getRespawnData().pos();
				return new int[]{spawn.getX(), spawn.getZ()};
			});
			this.build(context, server, at[0], at[1], "mp");
		}
	}

	private static long size(java.nio.file.Path dir) {
		try (var files = java.nio.file.Files.walk(dir)) {
			return files.filter(java.nio.file.Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
		} catch (java.io.IOException e) {
			return -1;
		}
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("lod_" + name).withSize(WIDTH, HEIGHT));
	}

	private void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}
}
