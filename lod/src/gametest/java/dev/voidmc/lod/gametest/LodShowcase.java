package dev.voidmc.lod.gametest;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.VoidLod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Climbs to a high point on an Earth world and photographs the view each way with the LOD off and on,
 * logging what the LOD drew and what it cost.
 *
 * <p>Run headless with {@code SDL_VIDEO_FORCE_EGL=1 xvfb-run ./gradlew runClientGameTest}; screenshots land in
 * build/run/clientGameTest/screenshots. System properties: {@code lod.show.seed}, {@code lod.show.distance}
 * (vanilla render distance), {@code lod.show.settle} (ms for vanilla chunks), {@code lod.show.views}.
 */
public class LodShowcase implements FabricClientGameTest {
	private static final String SEED = System.getProperty("lod.show.seed", "8675309");
	private static final int DISTANCE = Integer.getInteger("lod.show.distance", 8);
	private static final long SETTLE_MS = Long.getLong("lod.show.settle", 45_000L);
	private static final int VIEWS = Integer.getInteger("lod.show.views", 2);

	private record Spot(int x, int y, int z, float yaw) {
	}

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
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamemode spectator @a");

			List<Spot> spots = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				List<Spot> found = new ArrayList<>();
				if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth)) {
					found.add(new Spot(0, 200, 0, 0));
					return found;
				}
				TerrainModel model = earth.model(level.getChunkSource().randomState());
				// the highest ground within 3 km, then look out from it in the directions with the most relief
				int bx = 0, bz = 0;
				float best = -1;
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
				for (int dx = -DISTANCE - 1; dx <= DISTANCE + 1; dx++) {
					for (int dz = -DISTANCE - 1; dz <= DISTANCE + 1; dz++) {
						level.getChunk((bx >> 4) + dx, (bz >> 4) + dz);
					}
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
				for (int v = 0; v < 4; v++) {
					found.add(new Spot(bx, top + 24, bz, v * 90.0F));
				}
				System.out.printf(Locale.ROOT, "[lod] high point %d,%d at y=%d%n", bx, bz, top);
				return found;
			});

			for (int i = 0; i < Math.min(VIEWS, spots.size()); i++) {
				Spot spot = spots.get(i);
				world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d %.1f %.1f", spot.x(), spot.y(), spot.z(), spot.yaw(), 12.0F));
				String name = "view" + i;

				context.runOnClient(mc -> VoidLod.get().setEnabled(false));
				this.settle(context, i == 0 ? SETTLE_MS : SETTLE_MS / 3);
				context.takeScreenshot("lod_" + name + "_off");

				context.runOnClient(mc -> VoidLod.get().setEnabled(true));
				long start = System.currentTimeMillis();
				// wait for the LOD to finish building what it wants, then a few frames more to upload
				while (System.currentTimeMillis() - start < 180_000) {
					context.waitTicks(20);
					boolean idle = context.computeOnClient(mc -> VoidLod.get().engine().idle());
					if (idle) {
						break;
					}
				}
				long loaded = System.currentTimeMillis() - start;
				context.waitTicks(20);
				context.takeScreenshot("lod_" + name + "_on");
				String stats = context.computeOnClient(mc -> VoidLod.get().stats());
				System.out.printf(Locale.ROOT, "[lod] %s: complete in %.1f s | %s%n", name, loaded / 1000.0, stats);
			}
		}
	}

	private void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}
}
