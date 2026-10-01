package dev.voidmc.expanse.gametest;

import com.mojang.datafixers.util.Pair;
import java.util.List;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Flies round the world photographing each Expanse biome: a wide shot from above the canopy and a
 * shot from the ground, at midday in clear weather, with the HUD hidden. Screenshots land in
 * run/clientGameTest/screenshots (or wherever the game test run directory is).
 */
public class BiomeTour implements FabricClientGameTest {
	// Software rendering under xvfb meshes chunks far slower than a GPU: give it minutes, not seconds.
	private static final int SLOW = 20 * 600;
	private static final String SEED = System.getProperty("expanse.tour.seed", "8675309");
	private static final List<String> BIOMES = List.of(System.getProperty("expanse.tour.biomes",
		"wisteria_vale,lumen_grove,redwood_giants,jade_karst,opal_dunes,willow_bayou,amber_steppe,heather_moor,"
			+ "frostbloom_tundra,cloud_forest,verdant_peaks,prismatic_peaks,palm_coast").split(","));

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(Integer.getInteger("expanse.tour.distance", 10));
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
			world.getConnection().waitForChunksRender(SLOW);
			for (String name : BIOMES) {
				this.visit(context, world, name);
			}
		}
	}

	private void visit(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("expanse", name));
		BlockPos found = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(h -> h.is(key), BlockPos.ZERO, 12000, 32, 64);
			if (hit == null) {
				return null;
			}
			BlockPos p = hit.getFirst();
			level.getChunk(p.getX() >> 4, p.getZ() >> 4);
			return new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
		});
		if (found == null) {
			System.out.println("[tour] " + name + " not found");
			return;
		}
		System.out.println("[tour] " + name + " at " + found.toShortString());
		for (String time : new String[]{"noon"}) {
			world.getServer().runCommand("time set " + time);
			this.shoot(context, world, name + "_wide", found.getX(), found.getY() + 34, found.getZ(), 35.0F, 28.0F);
			this.shoot(context, world, name + "_ground", found.getX() - 6, found.getY() + 3, found.getZ() - 6, 45.0F, 4.0F);
		}
		if (name.equals("lumen_grove") || name.equals("prismatic_peaks") || name.equals("frostbloom_tundra")) {
			world.getServer().runCommand("time set midnight");
			this.shoot(context, world, name + "_night", found.getX() - 6, found.getY() + 4, found.getZ() - 6, 45.0F, 6.0F);
			world.getServer().runCommand("time set noon");
		}
	}

	private void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name, int x, int y, int z, float yaw, float pitch) {
		world.getServer().runCommand(String.format("tp @a %d %d %d %.1f %.1f", x, y, z, yaw, pitch));
		world.getConnection().waitForChunksRender(SLOW);
		context.waitTicks(40);
		world.getConnection().waitForChunksRender(SLOW);
		context.takeScreenshot("expanse_" + name);
	}
}
