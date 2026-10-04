package dev.voidmc.hearth.gametest.render;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.build.blueprint.BlueprintImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.build.check.LightCheck;
import dev.voidmc.hearth.build.kit.light.LightRules;
import dev.voidmc.hearth.build.style.StyleRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.TagValueInput;
import org.jspecify.annotations.Nullable;

/**
 * Photographs buildings by night in the game, to judge their light (docs/LIVING_WORLD.md §1): a handful of archetypes
 * built on a superflat world at midnight, each twice: as the compiler makes it (lit by the auto-lighter) and with
 * {@code lighting=none} (only the archetype's own lights), from the street and from inside its rooms at eye height.
 * Screenshots land in {@code build/run/clientGameTest/screenshots} as {@code hearth_light_<archetype>_<culture>_<view>}
 * ({@code _before} for the unlit twin).
 *
 * <p>Run with {@code JAVA_TOOL_OPTIONS="-Dhearth.tour=light" ./gradlew runClientGameTest}; {@code -Dhearth.light.only=}
 * (comma list of archetype names) narrows it, {@code -Dhearth.light.settle=} (ms per shot, default 5000) and
 * {@code -Dhearth.light.before=false} skip the unlit twins.
 */
public class LightTour implements FabricClientGameTest {
	private static final long SETTLE_MS = Long.getLong("hearth.light.settle", 5_000L);
	private static final boolean BEFORE = Boolean.parseBoolean(System.getProperty("hearth.light.before", "true"));

	/** What is photographed: archetype, culture, seed. */
	private record Subject(String archetype, Culture culture, long seed) {
	}

	private static final List<Subject> SUBJECTS = List.of(
		new Subject("house/cottage", Culture.TEMPERATE, 7),
		new Subject("house/townhouse", Culture.TEMPERATE, 3),
		new Subject("house/tenement", Culture.TEMPERATE, 5),
		new Subject("commerce/inn", Culture.TEMPERATE, 2),
		new Subject("civic/chapel", Culture.TEMPERATE, 4),
		new Subject("landmark/cathedral", Culture.TEMPERATE, 1),
		new Subject("work/smithy", Culture.TEMPERATE, 6),
		new Subject("house/longhouse", Culture.NORTHERN, 3),
		new Subject("house/courtyard_house", Culture.MEDITERRANEAN, 2));

	/** A camera: feet position, yaw and pitch. */
	private record Shot(String view, double x, double y, double z, float yaw, float pitch) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.voidmc.hearth.gametest.Tours.wanted("light")) {
			return;
		}
		String only = System.getProperty("hearth.light.only", "");
		context.getInput().resizeWindow(1600, 900);
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
			mc.options.renderDistance().set(8);
			mc.options.fov().set(80);
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261004");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
				s.setGenerateStructures(false);
				for (WorldCreationUiState.WorldTypeEntry e : s.getNormalPresetList()) {
					if (e.preset() != null && e.preset().is(WorldPresets.FLAT)) {
						s.setWorldType(e);
					}
				}
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamerule spawn_monsters false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set 18000");
			world.getServer().runCommand("gamemode spectator @a");
			int x = 0;
			for (Subject sub : SUBJECTS) {
				String name = sub.archetype().substring(sub.archetype().indexOf('/') + 1);
				if (!only.isBlank() && !List.of(only.split(",")).contains(name)) {
					continue;
				}
				int bx = x;
				world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 0 30", bx, -40, -20));
				settle(context, 3_000);
				List<Shot> lit = world.getServer().computeOnServer(server -> build(server.overworld(), sub, bx, 0, false));
				// the unlit twin 80 blocks south, photographed from the same cameras
				List<Shot> dark = new ArrayList<>();
				if (BEFORE) {
					world.getServer().computeOnServer(server -> build(server.overworld(), sub, bx, 80, true));
					for (Shot s : lit) {
						dark.add(new Shot(s.view(), s.x(), s.y(), s.z() + 80, s.yaw(), s.pitch()));
					}
				}
				String base = "hearth_light_" + name + "_" + sub.culture().getSerializedName();
				photograph(context, world, base, lit);
				photograph(context, world, base, dark, "_before");
				x += 90;
			}
		}
	}

	private void photograph(ClientGameTestContext context, TestSingleplayerContext world, String base, List<Shot> shots) {
		photograph(context, world, base, shots, "");
	}

	private void photograph(ClientGameTestContext context, TestSingleplayerContext world, String base, List<Shot> shots, String suffix) {
		for (Shot s : shots) {
			world.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
			settle(context, SETTLE_MS);
			context.takeScreenshot(base + "_" + s.view() + suffix);
		}
	}

	/** Builds a subject with its plot's front row at z = {@code z0} + depth (facing south), and returns its cameras. */
	private static List<Shot> build(ServerLevel level, Subject sub, int x0, int z0, boolean unlit) {
		BlueprintServiceImpl service = BuildModule.service();
		ArchetypeInfo info = service.archetype(Hearth.id(sub.archetype()));
		if (info == null) {
			log("unknown archetype " + sub.archetype());
			return List.of();
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
		java.util.Random r = new java.util.Random(sub.seed() * 31 + info.id().hashCode());
		int frontage = info.minFrontage() + r.nextInt(info.maxFrontage() - info.minFrontage() + 1);
		int depth = info.minDepth() + r.nextInt(info.maxDepth() - info.minDepth() + 1);
		CompoundTag params = new CompoundTag();
		if (unlit) {
			params.putString("lighting", "none");
		}
		TerrainProbe probe = new Flat(level.getSeed(), ground, level.getMinY(), level.getMaxY());
		PlotSpec spec = new PlotSpec(info.id(), StyleRegistry.styleId(sub.culture()), sub.seed(), new BlockPos(x0, ground, z0), frontage, depth, Direction.SOUTH,
			Math.max(info.maxHeight(), 8), 2, params);
		BlueprintImpl bp = Compiler.compile(service.draw(spec, probe));
		for (int st = 0; st < bp.stageCount(); st++) {
			for (int i = 0; i < bp.stepCount(st); i++) {
				bp.step(st, i, (x, y, z, state, tag) -> {
					BlockPos p = new BlockPos(x, y, z);
					level.setBlock(p, state, 2 | 16);
					if (tag != null) {
						BlockEntity be = level.getBlockEntity(p);
						if (be != null) {
							be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
							be.setChanged();
						}
					}
				});
			}
		}
		LightCheck.Result res = LightCheck.evaluate(bp, probe);
		log(sub.archetype() + " " + sub.culture().getSerializedName() + (unlit ? " unlit" : "") + " at " + x0 + "," + ground + "," + z0 + " "
			+ frontage + "x" + depth + ": " + (res.unmet().isEmpty() ? "all " + res.needs().size() + " lit" : res.unmet().size() + " unmet"));
		return cameras(bp, res, ground);
	}

	/**
	 * The street view (from the front left, a little up, looking at the middle of the house) and, per storey with rooms
	 * (the two largest), a view from the front of its biggest room down its length at eye height (the unlit twin is seen
	 * from the same cameras).
	 */
	private static List<Shot> cameras(BlueprintImpl bp, LightCheck.Result res, int ground) {
		List<Shot> out = new ArrayList<>();
		var b = bp.bounds();
		double cx = (b.minX() + b.maxX() + 1) / 2.0;
		double cz = (b.minZ() + b.maxZ() + 1) / 2.0;
		double cy = ground + Math.min(6, (b.maxY() - ground) / 2.0);
		double ex = b.minX() - 6;
		double ez = b.maxZ() + 10;
		double ey = ground + 5;
		out.add(look("street", ex, ey, ez, cx, cy, cz));
		// rooms per standing level
		Map<Integer, List<LightRules.Need>> byLevel = new HashMap<>();
		for (LightRules.Need n : res.needs()) {
			if (n.kind() == LightRules.Kind.ROOM) {
				byLevel.computeIfAbsent(n.y(), k -> new ArrayList<>()).add(n);
			}
		}
		List<Integer> levels = new ArrayList<>(byLevel.keySet());
		levels.sort((p, q) -> Integer.compare(byLevel.get(q).size(), byLevel.get(p).size()));
		int taken = 0;
		for (int y : levels) {
			if (taken == 2 || byLevel.get(y).size() < 6) {
				break;
			}
			// the level's biggest room (cells joined side to side), seen from near one corner across to the other
			java.util.Set<Long> cells = new java.util.HashSet<>();
			for (LightRules.Need n : byLevel.get(y)) {
				cells.add(BlockPos.asLong(n.x(), y, n.z()));
			}
			List<long[]> room = biggest(cells, y);
			int ax = Integer.MAX_VALUE;
			int az = Integer.MAX_VALUE;
			int bx = Integer.MIN_VALUE;
			int bz = Integer.MIN_VALUE;
			for (long[] c : room) {
				ax = Math.min(ax, (int) c[0]);
				az = Math.min(az, (int) c[1]);
				bx = Math.max(bx, (int) c[0]);
				bz = Math.max(bz, (int) c[1]);
			}
			// from the front of the room (the street side, south) down its length to the back, at eye height
			double kx = (ax + bx + 1) / 2.0;
			double kz = bz + 0.5;
			long[] cam = room.get(0);
			double best = Double.MAX_VALUE;
			for (long[] c : room) {
				double d = (c[0] + 0.5 - kx) * (c[0] + 0.5 - kx) + (c[1] + 0.5 - kz) * (c[1] + 0.5 - kz);
				if (d < best) {
					best = d;
					cam = c;
				}
			}
			out.add(look("room_y" + (y - ground), cam[0] + 0.5, y, cam[1] + 0.5, kx, y + 1.0, az + 0.5));
			taken++;
		}
		return out;
	}

	/** The largest set of cells at level y joined side to side, as {x, z}. */
	private static List<long[]> biggest(java.util.Set<Long> cells, int y) {
		java.util.Set<Long> seen = new java.util.HashSet<>();
		List<long[]> best = List.of();
		for (long start : cells) {
			if (!seen.add(start)) {
				continue;
			}
			List<long[]> part = new ArrayList<>();
			java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
			q.add(start);
			while (!q.isEmpty()) {
				long k = q.poll();
				int x = BlockPos.getX(k);
				int z = BlockPos.getZ(k);
				part.add(new long[]{x, z});
				for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					long n = BlockPos.asLong(x + o[0], y, z + o[1]);
					if (cells.contains(n) && seen.add(n)) {
						q.add(n);
					}
				}
			}
			if (part.size() > best.size()) {
				best = part;
			}
		}
		return best;
	}

	/** A camera with its feet at (x, y, z) looking at (tx, ty, tz) from eye height. */
	private static Shot look(String view, double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x;
		double dz = tz - z;
		double dy = ty - (y + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new Shot(view, x, y, z, yaw, pitch);
	}

	private void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String s) {
		System.out.println("[hearth light tour] " + s);
	}

	/** A superflat world's ground: level everywhere, dry, plains. */
	private record Flat(long seed, int ground, int minY, int maxY) implements TerrainProbe {
		@Override
		public int seaLevel() {
			return this.minY;
		}

		@Override
		public int groundY(int x, int z) {
			return this.ground;
		}

		@Override
		public int waterY(int x, int z) {
			return NO_WATER;
		}

		@Override
		public @Nullable ResourceKey<Biome> biome(int x, int z) {
			return Biomes.PLAINS;
		}

		@Override
		public float temperature(int x, int z) {
			return 0.8F;
		}
	}
}
