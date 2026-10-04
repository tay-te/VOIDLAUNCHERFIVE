package dev.voidmc.hearth.gametest.homestead;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * The homes and farms in the game, with the real blocks (Expanse's woods included) and the real light: a row of plots
 * on a levelled pad (a pond under the stilt house, sand under the dry lands' homes), each built at once from its
 * blueprint and photographed from the street corner, the street and behind, then the Bohemian row by night.
 * Screenshots land in {@code build/run/clientGameTest/screenshots} as {@code hearth_homes_*}.
 *
 * <p>Entrypoint {@code dev.voidmc.hearth.gametest.homestead.HomesTour}; {@code -Dhearth.tour=homes} runs only this tour.
 */
public class HomesTour implements FabricClientGameTest {
	/** The row's origin: a temperate place near spawn (found when the world is made), its ground level. */
	private static int X0;
	private static int Z0;
	private static int Y = 90;

	/** One plot of the row: what is built there and on what ground. */
	private record Plot(String name, String archetype, Culture culture, long seed, int frontage, int depth, String ground, CompoundTag params) {
	}

	private static CompoundTag p(String... kv) {
		CompoundTag t = new CompoundTag();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			t.putString(kv[i], kv[i + 1]);
		}
		return t;
	}

	private static final List<Plot> PLOTS = List.of(
		new Plot("cottage_a", "house/cottage", Culture.TEMPERATE, 2007, 11, 10, "grass", p()),
		new Plot("cottage_b", "house/cottage", Culture.TEMPERATE, 4007, 11, 9, "grass", p()),
		new Plot("cottage_c", "house/cottage", Culture.TEMPERATE, 6007, 10, 9, "grass", p()),
		new Plot("cottage_d", "house/cottage", Culture.TEMPERATE, 3007, 10, 7, "grass", p()),
		new Plot("steading", "house/farmhouse", Culture.TEMPERATE, 4007, 17, 17, "grass", p("layout", "steading")),
		new Plot("laithe", "house/farmhouse", Culture.TEMPERATE, 5007, 16, 15, "grass", p("layout", "laithe")),
		new Plot("stilt", "house/stilt_house", Culture.BAYOU, 4057, 11, 12, "pond", p()),
		new Plot("round_tropical", "house/round_house", Culture.TROPICAL, 600, 13, 13, "grass", p()),
		new Plot("round_steppe", "house/round_house", Culture.STEPPE, 610, 11, 11, "grass", p()),
		new Plot("desert", "house/cottage", Culture.DESERT, 2009, 10, 10, "sand", p("storeys", "2")),
		new Plot("mesa", "house/cottage", Culture.MESA, 3009, 10, 10, "red_sand", p()),
		new Plot("northern", "house/cottage", Culture.NORTHERN, 921, 10, 10, "grass", p("turf", "true")),
		new Plot("smith", "house/cottage", Culture.TEMPERATE, 8007, 11, 10, "grass", p()));

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.voidmc.hearth.gametest.Tours.wanted("homes")) {
			return;
		}
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(8);
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261004");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamerule spawn_monsters false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set 2500");
			settle(context, 2_000);
			// a temperate place for the row (green grass, no snow), its ground level
			world.getServer().runOnServer(HomesTour::findPlace);
			log("the row starts at " + X0 + ", " + Y + ", " + Z0);
			// the row along x, every plot facing south onto a street at z = Z0
			List<int[]> centres = new ArrayList<>();
			int x = X0;
			for (Plot plot : PLOTS) {
				int ox = x;
				centres.add(world.getServer().computeOnServer(server -> build(server, plot, ox)));
				x += plot.frontage() + 6;
			}
			// let the client load the row's chunks before the first shot
			int[] first = centres.get(0);
			if (first != null) {
				shoot(context, world, "warmup", first[0] + 10, Y + 12, first[2] + 16, 150, 20);
				settle(context, 8_000);
			}
			for (int i = 0; i < PLOTS.size(); i++) {
				Plot plot = PLOTS.get(i);
				int[] c = centres.get(i);
				if (c == null) {
					continue;
				}
				int reach = Math.max(plot.frontage(), plot.depth());
				// from the street corner (south-east), the street (south) and behind (north-west)
				double tx = c[0] + 0.5;
				double tz = c[2] + 0.5;
				double ty = Y + 5;
				aim(context, world, plot.name() + "_corner", tx + reach * 1.05, Y + reach * 0.75, tz + reach * 1.25, tx, ty, tz);
				aim(context, world, plot.name() + "_street", tx, Y + 3.5, tz + reach * 1.55, tx, ty, tz);
				aim(context, world, plot.name() + "_back", tx - reach * 1.05, Y + reach * 0.8, tz - reach * 1.25, tx, ty, tz);
			}
			// the Bohemian row together, by day and by night
			int[] a = centres.get(0);
			int[] b = centres.get(5);
			if (a != null && b != null) {
				double mid = (a[0] + b[0]) / 2.0;
				shoot(context, world, "bohemia_row", mid, Y + 30, a[2] + 62, 180, 24);
				world.getServer().runCommand("time set 18000");
				shoot(context, world, "bohemia_row_night", mid, Y + 30, a[2] + 62, 180, 24);
				int[] c = centres.get(0);
				shoot(context, world, "cottage_a_night", c[0] + 9, Y + 5, c[2] + 13, 150, 12);
				world.getServer().runCommand("time set 2500");
			}
			log("done");
		}
	}

	/** Levels the plot's ground, builds its blueprint at once; returns the plot's centre (x, y, z) or null. */
	private static int[] build(MinecraftServer server, Plot plot, int ox) {
		ServerLevel level = server.overworld();
		BlueprintServiceImpl service = BuildModule.service();
		ArchetypeInfo info = service.archetype(Hearth.id(plot.archetype()));
		if (info == null) {
			log("unknown archetype " + plot.archetype());
			return null;
		}
		int oz = Z0 - plot.depth();
		int x0 = ox - 6;
		int x1 = ox + plot.frontage() + 6;
		int z0 = oz - 6;
		int z1 = Z0 + 8;
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		// the pad: earth to the floor, the culture's ground on top, air above; a pond under a stilt house
		BlockState top = switch (plot.ground()) {
			case "sand" -> Blocks.SAND.defaultBlockState();
			case "red_sand" -> Blocks.RED_SAND.defaultBlockState();
			default -> Blocks.GRASS_BLOCK.defaultBlockState();
		};
		BlockState fill = plot.ground().contains("sand") ? Blocks.SANDSTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = Y - 8; y <= Y + 40; y++) {
					BlockState s = y < Y - 4 ? Blocks.STONE.defaultBlockState() : y < Y - 1 ? fill : y == Y - 1 ? top : Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_CLIENTS);
				}
			}
		}
		if (plot.ground().equals("pond")) {
			// open water over most of the plot, its back and a margin of it: the house stands out over it, the street dry
			for (int x = ox - 3; x <= ox + plot.frontage() + 2; x++) {
				for (int z = oz - 5; z <= oz + plot.depth() - 4; z++) {
					for (int y = Y - 3; y <= Y - 1; y++) {
						level.setBlock(new BlockPos(x, y, z), y == Y - 3 ? Blocks.MUD.defaultBlockState() : Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		Land probe = new Land(level);
		CompoundTag params = new CompoundTag();
		for (String k : plot.params().keySet()) {
			String v = plot.params().getStringOr(k, "");
			if (v.equals("true") || v.equals("false")) {
				params.putBoolean(k, Boolean.parseBoolean(v));
			} else if (v.chars().allMatch(Character::isDigit)) {
				params.putInt(k, Integer.parseInt(v));
			} else {
				params.putString(k, v);
			}
		}
		if (plot.name().equals("smith")) {
			CompoundTag h = new CompoundTag();
			h.putString("job", "smith");
			params.put("household", h);
		}
		PlotSpec spec = new PlotSpec(info.id(), StyleRegistry.styleId(plot.culture()), plot.seed(), new BlockPos(ox, Y, oz), plot.frontage(), plot.depth(),
			Direction.SOUTH, Math.max(info.maxHeight(), 8), 2, params);
		BlueprintImpl bp = Compiler.compile(service.draw(spec, probe));
		for (int stage = 0; stage < bp.stageCount(); stage++) {
			int n = bp.stepCount(stage);
			for (int i = 0; i < n; i++) {
				bp.step(stage, i, (x, y, z, state, tag) -> {
					BlockPos at = new BlockPos(x, y, z);
					level.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
					if (tag != null) {
						BlockEntity be = level.getBlockEntity(at);
						if (be != null) {
							be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
							be.setChanged();
						}
					}
				});
			}
		}
		log(plot.name() + ": " + plot.archetype() + " " + plot.culture().getSerializedName() + " seed " + plot.seed() + " " + plot.frontage() + "x"
			+ plot.depth() + ", " + bp.totalSteps() + " steps, " + bp.beds() + " beds");
		return new int[]{ox + plot.frontage() / 2, Y, oz + plot.depth() / 2};
	}

	/** The pad as it lies (the build command's reading of the land: the top solid block that is not a plant, the water). */
	private static final class Land implements TerrainProbe {
		private final ServerLevel level;

		Land(ServerLevel level) {
			this.level = level;
		}

		@Override
		public long seed() {
			return this.level.getSeed();
		}

		@Override
		public int minY() {
			return this.level.getMinY();
		}

		@Override
		public int maxY() {
			return this.level.getMaxY();
		}

		@Override
		public int seaLevel() {
			return this.level.getSeaLevel();
		}

		@Override
		public int groundY(int x, int z) {
			return column(x, z)[0];
		}

		@Override
		public int waterY(int x, int z) {
			return column(x, z)[1];
		}

		@Override
		public net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> biome(int x, int z) {
			return this.level.getBiome(new BlockPos(x, groundY(x, z), z)).unwrapKey().orElse(null);
		}

		@Override
		public float temperature(int x, int z) {
			return this.level.getBiome(new BlockPos(x, groundY(x, z), z)).value().getBaseTemperature();
		}

		private int[] column(int x, int z) {
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			int y = this.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
			int water = NO_WATER;
			for (; y > this.level.getMinY(); y--) {
				BlockState s = this.level.getBlockState(p.set(x, y, z));
				if (s.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
					if (water == NO_WATER) {
						water = y + 1;
					}
					continue;
				}
				if (s.isAir() || s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)
					|| s.is(net.minecraft.tags.BlockTags.REPLACEABLE) || s.getCollisionShape(this.level, p).isEmpty()) {
					continue;
				}
				break;
			}
			return new int[]{y + 1, water};
		}
	}

	/**
	 * The nearest gentle temperate land to spawn (not sea, river, beach or mountain, the row's ground varying little):
	 * rings of samples outward from spawn, the biome read at the ground; then the whole row's ground levelled to it (the
	 * trees and hills over it cleared), so every shot has open ground round it.
	 */
	private static void findPlace(MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		// read from the generator (nothing generated while searching: generating hundreds of chunks runs out of memory)
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		var biomes = level.uncachedBiomeResolver();
		java.util.function.IntBinaryOperator height = (x, z) -> gen.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
			level, rs);
		BlockPos best = spawn;
		int bestRelief = Integer.MAX_VALUE;
		String why = "nothing, at spawn";
		search:
		for (int r = 0; r <= 2560; r += 128) {
			int n = Math.max(1, r / 96);
			for (int k = 0; k < n; k++) {
				double a = 2 * Math.PI * k / n;
				int x = spawn.getX() + (int) (Math.cos(a) * r);
				int z = spawn.getZ() + (int) (Math.sin(a) * r);
				int y = height.applyAsInt(x, z);
				var h = biomes.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(y),
					net.minecraft.core.QuartPos.fromBlock(z));
				float t = h.value().getBaseTemperature();
				boolean ok = t >= 0.6F && t <= 0.95F && !h.is(net.minecraft.tags.BiomeTags.IS_OCEAN) && !h.is(net.minecraft.tags.BiomeTags.IS_RIVER)
					&& !h.is(net.minecraft.tags.BiomeTags.IS_BEACH) && !h.is(net.minecraft.tags.BiomeTags.IS_MOUNTAIN) && y > level.getSeaLevel() + 1;
				if (!ok) {
					continue;
				}
				// the row runs 240 east of here: how much its ground rises and falls
				int lo = y;
				int hi = y;
				for (int dx = 0; dx <= 240; dx += 24) {
					for (int dz = -32; dz <= 32; dz += 32) {
						int g = height.applyAsInt(x + dx, z + dz);
						lo = Math.min(lo, g);
						hi = Math.max(hi, g);
					}
				}
				if (hi - lo < bestRelief) {
					bestRelief = hi - lo;
					best = new BlockPos(x, y, z);
					why = h.unwrapKey().map(Object::toString).orElse("?") + ", relief " + (hi - lo);
					if (bestRelief <= 6) {
						break search;
					}
				}
			}
		}
		X0 = best.getX();
		Z0 = best.getZ();
		Y = best.getY();
		log("found " + why);
		// the row's ground levelled to Y: earth under, grass on top, nothing standing over it
		int ax0 = X0 - 16;
		int ax1 = X0 + 250;
		int az0 = Z0 - 40;
		int az1 = Z0 + 40;
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = ax0; x <= ax1; x++) {
			for (int z = az0; z <= az1; z++) {
				for (int y = Y - 4; y <= Y + 40; y++) {
					BlockState want = y < Y - 1 ? dirt : y == Y - 1 ? grass : air;
					p.set(x, y, z);
					BlockState now = level.getBlockState(p);
					if (y < Y - 1 && !now.isAir() && now.getFluidState().isEmpty()) {
						continue;
					}
					if (now != want) {
						level.setBlock(p, want, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	private static int ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** A shot from (x, y, z) aimed at the target (tx, ty, tz). */
	private static void aim(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, double tx, double ty,
		double tz) {
		double dx = tx - x;
		double dz = tz - z;
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(ty - y, Math.sqrt(dx * dx + dz * dz)));
		shoot(context, world, name, x, y, z, yaw, pitch);
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, float yaw,
		float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer pl = player(server);
			pl.getAbilities().flying = true;
			pl.onUpdateAbilities();
			pl.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
		settle(context, 3_500);
		context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("hearth_homes_" + name).withSize(1600, 900));
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String line) {
		System.out.println("[hearth homes] " + line);
	}
}
