package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.apply.ChunkLedgers;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The core's save, quit and reload check, in a real world (docs/review/systems/core.md):
 *
 * <ol>
 *   <li>a live town is founded near spawn and grown 40 days, and the nearest historic town adopted; the sim clock is then
 *   held (no time passes), so everything compared below must come back exactly;</li>
 *   <li>the live town's chunks are loaded and the applier writes it; its blocks are recorded;</li>
 *   <li>the chunks are let go until they unload, then loaded again: no block may change (the ledgers say what is
 *   written, so nothing is written twice and nothing is missed);</li>
 *   <li>the commands run (as an operator, and parsed as a player without permissions: op-only commands must not be
 *   reachable);</li>
 *   <li>the world is saved and closed, then opened again: every settlement, resident, plot, stockpile, treasury,
 *   reputation and module data comes back as it was, the index hands out no id twice, and the town's blocks are as
 *   they were.</li>
 * </ol>
 *
 * {@code -Dhearth.tour=persist}; screenshots {@code hearth_persist_*.png} in {@code -Dhearth.persist.out} (default
 * {@code screenshots}); every line is printed as {@code [hearth persist]}, failures as {@code [hearth persist] FAIL}.
 */
public class PersistTour implements FabricClientGameTest {
	private static final java.nio.file.Path OUT = java.nio.file.Path.of(System.getProperty("hearth.persist.out", "screenshots"));
	private static final int DAYS = Integer.getInteger("hearth.persist.days", 40);
	private static int failures;

	/** What the tour knows of the towns between the two sessions. */
	private record Towns(long live, long historic) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("persist")) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(8);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		TestWorldSave save;
		Towns towns;
		Map<Long, String> before;
		Map<Long, String> nbtBefore;
		int[][] blocks;
		BoundingBox area;
		double[] camera;
		TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create();
		try {
			save = world.getWorldSave();
			for (String c : List.of("gamerule advance_weather false", "gamerule spawn_monsters false", "gamerule random_tick_speed 0",
				"gamerule fire_spread_radius_around_player 0", "weather clear", "time set 4000", "gamemode spectator @a", "tp @a 30000 300 30000")) {
				world.getServer().runCommand(c);
			}
			settle(context, 3_000);
			towns = world.getServer().computeOnServer(PersistTour::makeTowns);
			if (towns == null) {
				fail("no town could be founded near spawn");
				return;
			}
			// from here on no time passes in the world: what is compared must come back exactly
			world.getServer().runCommand("gamerule advance_time false");
			log("towns: " + world.getServer().computeOnServer(server -> describe(server, towns)));

			area = world.getServer().computeOnServer(server -> area(server, towns.live));
			world.getServer().runOnServer(server -> force(server, area, true));
			waitApplier(context, world);
			// a few seconds more: felling owed to the land and anything else a load sets off has its turn
			settle(context, 8_000);
			waitApplier(context, world);
			blocks = world.getServer().computeOnServer(server -> blocks(server, area));
			long ledgerBefore = world.getServer().computeOnServer(server -> ledgers(server, area));
			log(String.format(Locale.ROOT, "town written: %d chunks, %d ledger entries", blocks.length, ledgerBefore));

			// chunk unload and reload
			long placedBefore = world.getServer().computeOnServer(server -> sim(server).applier().blocksPlaced.sum());
			world.getServer().runOnServer(server -> force(server, area, false));
			int loaded = 0;
			for (int i = 0; i < 120; i++) {
				settle(context, 500);
				loaded = world.getServer().computeOnServer(server -> loadedChunks(server, area));
				if (loaded == 0) {
					break;
				}
			}
			log("chunks still loaded after letting go: " + loaded);
			world.getServer().runOnServer(server -> force(server, area, true));
			waitApplier(context, world);
			long placedAfter = world.getServer().computeOnServer(server -> sim(server).applier().blocksPlaced.sum());
			int[][] reloaded = world.getServer().computeOnServer(server -> blocks(server, area));
			compareBlocks("chunk unload/reload", blocks, reloaded, area, world);
			check(placedAfter == placedBefore, "chunk reload wrote " + (placedAfter - placedBefore) + " blocks again (expected 0)");
			check(world.getServer().computeOnServer(server -> ledgers(server, area)) == ledgerBefore, "ledger entries changed over a chunk reload");

			commands(world);

			// a look at the town (a spectator embodies nothing), then save and quit
			camera = world.getServer().computeOnServer(server -> camera(server, towns.live));
			look(world, camera);
			settle(context, 6_000);
			shot(context, "before_reload");
			world.getServer().runCommand("tp @a 30000 300 30000");
			settle(context, 1_000);
			before = world.getServer().computeOnServer(PersistTour::summaries);
			nbtBefore = world.getServer().computeOnServer(PersistTour::nbt);
			// what is on disk after a flushing save reads back as what is live (the load path, without leaving the world)
			// (/save-all is a dedicated server's command)
			world.getServer().runOnServer(server -> server.saveEverything(true, true, true));
			Map<Long, String> disk = world.getServer().computeOnServer(PersistTour::fromDisk);
			int same = 0;
			for (var e : before.entrySet()) {
				String d = disk.get(e.getKey());
				if (e.getValue().equals(d)) {
					same++;
				} else {
					fail("settlement #" + e.getKey() + " on disk differs from the live record: " + firstDifference(e.getValue(), d == null ? "" : d));
				}
			}
			log(same + " of " + before.size() + " settlements read back from disk exactly");
			log("saving and quitting with " + before.size() + " settlements");
		} finally {
			quit(context, world);
		}

		TestSingleplayerContext again = save.open();
		try {
			settle(context, 2_000);
			Map<Long, String> after = again.getServer().computeOnServer(PersistTour::summaries);
			Map<Long, String> nbtAfter = again.getServer().computeOnServer(PersistTour::nbt);
			check(after.keySet().equals(before.keySet()), "settlements after reload " + after.keySet() + " vs before " + before.keySet());
			int same = 0;
			for (var e : before.entrySet()) {
				String b = after.get(e.getKey());
				if (e.getValue().equals(b)) {
					same++;
				} else {
					fail("settlement #" + e.getKey() + " changed over save and reload: " + firstDifference(e.getValue(), b == null ? "" : b));
				}
				if (!nbtBefore.get(e.getKey()).equals(nbtAfter.get(e.getKey()))) {
					log("note: #" + e.getKey() + " saves differently after reload (derived or lazily decoded state)");
				}
			}
			log(same + " of " + before.size() + " settlements came back exactly");
			String ids = again.getServer().computeOnServer(server -> {
				SimWorld w = sim(server);
				long max = 0;
				for (SimSettlement s : w.settlements()) {
					if (!Atlas.isHistoric(s.id())) {
						max = Math.max(max, s.id());
					}
				}
				long next = w.index().allocateId();
				return max + " " + next;
			});
			String[] parts = ids.split(" ");
			check(Long.parseLong(parts[1]) > Long.parseLong(parts[0]), "the index would hand out id " + parts[1] + " again (highest live id " + parts[0] + ")");
			again.getServer().runOnServer(server -> force(server, area, true));
			waitApplier(context, again);
			int[][] reopened = again.getServer().computeOnServer(server -> blocks(server, area));
			compareBlocks("save and reload", blocks, reopened, area, again);
			// the costs of the session after the reload (the applier's slowest chunk pass among them)
			again.getServer().runCommand("hearth sim stats");
			look(again, camera);
			settle(context, 6_000);
			shot(context, "after_reload");
		} finally {
			quit(context, again);
		}
		log(failures == 0 ? "PASS: everything came back" : "FAILED: " + failures + " checks");
	}

	// ---------------------------------------------------------------- the towns

	private static @Nullable Towns makeTowns(MinecraftServer server) {
		SimWorld w = sim(server);
		BlockPos spawn = server.overworld().getRespawnData().pos();
		SimSettlement live = null;
		for (int ring = 0; ring < 12 && live == null; ring++) {
			for (int i = 0; i < Math.max(1, ring * 6) && live == null; i++) {
				double a = i * Math.PI * 2 / Math.max(1, ring * 6);
				BlockPos at = spawn.offset((int) (Math.cos(a) * ring * 96), 0, (int) (Math.sin(a) * ring * 96));
				if (w.whyNotFound(at, 200) == null) {
					live = w.found(at, null, null);
				}
			}
		}
		if (live == null) {
			return null;
		}
		w.simulate(live, DAYS);
		// the nearest historic town
		int srx = Atlas.region(spawn.getX());
		int srz = Atlas.region(spawn.getZ());
		RegionPlan nearest = null;
		double best = Double.MAX_VALUE;
		for (int rx = srx - 2; rx <= srx + 2; rx++) {
			for (int rz = srz - 2; rz <= srz + 2; rz++) {
				RegionPlan plan = w.atlas().plan(rx, rz);
				if (plan.settled() && plan.site() != null && plan.site().center().distSqr(spawn) < best) {
					best = plan.site().center().distSqr(spawn);
					nearest = plan;
				}
			}
		}
		SimSettlement historic = nearest == null ? null : w.adopt(nearest);
		log("adopted " + (historic == null ? "nothing" : historic.name() + " at " + historic.center().toShortString()));
		// a little module data and player state to carry over
		UUID someone = new UUID(0x5EED, 42);
		live.addReputation(someone, 23);
		live.deposit(321);
		return new Towns(live.id(), historic == null ? Long.MIN_VALUE : historic.id());
	}

	private static String describe(MinecraftServer server, Towns t) {
		SimWorld w = sim(server);
		StringBuilder b = new StringBuilder();
		for (SimSettlement s : w.settlements()) {
			b.append(String.format(Locale.ROOT, "#%d %s (%s, %d souls, %d plots)%s; ", s.id(), s.name(), s.tier().getSerializedName(), s.population(),
				s.plots().size(), s.id() == t.live ? " live" : s.id() == t.historic ? " historic" : ""));
		}
		return b.toString();
	}

	/** Every settlement as a line of what must survive a save: people, plots, stores, coin, reputation, module data. */
	private static Map<Long, String> summaries(MinecraftServer server) {
		Map<Long, String> out = new TreeMap<>();
		for (SimSettlement s : sim(server).settlements()) {
			out.put(s.id(), summary(s));
		}
		return out;
	}

	/** The settlement files as a fresh load reads them. */
	private static Map<Long, String> fromDisk(MinecraftServer server) {
		SimWorld w = sim(server);
		var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess());
		Map<Long, String> out = new TreeMap<>();
		for (SimSettlement s : new dev.voidmc.hearth.sim.world.SettlementStore(w.store().dir()).loadAll(ops)) {
			s.dataMap().setOps(ops);
			out.put(s.id(), summary(s));
		}
		return out;
	}

	private static String summary(SimSettlement s) {
		StringBuilder b = new StringBuilder();
		b.append(s.name()).append('|').append(s.culture()).append('|').append(s.tier()).append('|').append(s.center().toShortString())
			.append("|sim ").append(s.simTick()).append("|coin ").append(s.treasury());
		List<Resident> people = new ArrayList<>(s.residentList());
		people.sort(Comparator.comparing(r -> r.id));
		b.append("|people ").append(people.size());
		int h = 1;
		for (Resident r : people) {
			h = 31 * h + (r.id + r.fullName() + r.job + r.home + r.work + r.age + r.bornDay).hashCode();
		}
		b.append('#').append(Integer.toHexString(h));
		List<SimPlot> plots = new ArrayList<>(s.plots());
		plots.sort(Comparator.comparingInt(SimPlot::id));
		b.append("|plots ").append(plots.size());
		h = 1;
		for (SimPlot p : plots) {
			h = 31 * h + (p.id() + " " + p.state() + " " + p.cursor() + " " + p.spec().archetype() + " " + p.spec().origin() + " " + p.owner()).hashCode();
		}
		b.append('#').append(Integer.toHexString(h));
		b.append("|stores");
		for (Goods g : Goods.VALUES) {
			if (s.stockpile().get(g) != 0) {
				b.append(' ').append(g.getSerializedName()).append('=').append(s.stockpile().get(g));
			}
		}
		net.minecraft.nbt.CompoundTag data = s.dataMap().save();
		b.append("|data");
		for (String k : new java.util.TreeSet<>(data.keySet())) {
			// (a wood's felling debt moves whenever the foresters fell a tree in a loaded chunk, which a load sets off)
			String v = data.get(k).toString().replaceAll("felling:[^,}]+,?", "");
			// (a module's state made empty on first use after a load is no change)
			if (!v.equals("{}") && !v.equals("[]")) {
				b.append(' ').append(k).append('=').append(v);
			}
		}
		return b.toString();
	}

	private static Map<Long, String> nbt(MinecraftServer server) {
		Map<Long, String> out = new LinkedHashMap<>();
		var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, server.registryAccess());
		for (SimSettlement s : sim(server).settlements()) {
			out.put(s.id(), SimSettlement.CODEC.encodeStart(ops, s).getOrThrow().toString());
		}
		return out;
	}

	// ---------------------------------------------------------------- blocks

	private static BoundingBox area(MinecraftServer server, long id) {
		BoundingBox b = sim(server).settlement(id).bounds();
		return new BoundingBox(b.minX() - 8, b.minY() - 8, b.minZ() - 8, b.maxX() + 8, b.maxY() + 24, b.maxZ() + 8);
	}

	private static void force(MinecraftServer server, BoundingBox a, boolean on) {
		ServerLevel level = server.overworld();
		for (int cx = a.minX() >> 4; cx <= a.maxX() >> 4; cx++) {
			for (int cz = a.minZ() >> 4; cz <= a.maxZ() >> 4; cz++) {
				level.setChunkForced(cx, cz, on);
			}
		}
	}

	private static int loadedChunks(MinecraftServer server, BoundingBox a) {
		int n = 0;
		for (int cx = a.minX() >> 4; cx <= a.maxX() >> 4; cx++) {
			for (int cz = a.minZ() >> 4; cz <= a.maxZ() >> 4; cz++) {
				if (server.overworld().getChunkSource().getChunkNow(cx, cz) != null) {
					n++;
				}
			}
		}
		return n;
	}

	/** The state id of every block in the area, chunk by chunk (null for a chunk that is not loaded). */
	private static int[][] blocks(MinecraftServer server, BoundingBox a) {
		ServerLevel level = server.overworld();
		int w = (a.maxX() >> 4) - (a.minX() >> 4) + 1;
		int d = (a.maxZ() >> 4) - (a.minZ() >> 4) + 1;
		int h = a.maxY() - a.minY() + 1;
		int[][] out = new int[w * d][];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < w; i++) {
			for (int j = 0; j < d; j++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow((a.minX() >> 4) + i, (a.minZ() >> 4) + j);
				if (chunk == null) {
					continue;
				}
				int[] ids = new int[256 * h];
				int minX = chunk.getPos().getMinBlockX();
				int minZ = chunk.getPos().getMinBlockZ();
				for (int y = 0; y < h; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							ids[(y * 16 + z) * 16 + x] = Block.getId(chunk.getBlockState(pos.set(minX + x, a.minY() + y, minZ + z)));
						}
					}
				}
				out[i * d + j] = ids;
			}
		}
		return out;
	}

	private static long ledgers(MinecraftServer server, BoundingBox a) {
		long n = 0;
		for (int cx = a.minX() >> 4; cx <= a.maxX() >> 4; cx++) {
			for (int cz = a.minZ() >> 4; cz <= a.maxZ() >> 4; cz++) {
				LevelChunk c = server.overworld().getChunkSource().getChunkNow(cx, cz);
				if (c != null) {
					n = n * 31 + ChunkLedgers.of(c).hashCode();
				}
			}
		}
		return n;
	}

	private static void compareBlocks(String what, int[][] a, int[][] b, BoundingBox area, TestSingleplayerContext world) {
		int d = (area.maxZ() >> 4) - (area.minZ() >> 4) + 1;
		int differ = 0;
		int trees = 0;
		int compared = 0;
		Map<String, Integer> kinds = new TreeMap<>();
		List<String> first = new ArrayList<>();
		for (int c = 0; c < a.length; c++) {
			if (a[c] == null || b[c] == null) {
				continue;
			}
			compared++;
			for (int k = 0; k < a[c].length; k++) {
				if (a[c][k] != b[c][k]) {
					if (felled(Block.stateById(a[c][k]), Block.stateById(b[c][k]))) {
						trees++;
						continue;
					}
					differ++;
					String from = name(Block.stateById(a[c][k]));
					String to = name(Block.stateById(b[c][k]));
					kinds.merge(from + " -> " + to, 1, Integer::sum);
					if (first.size() < 8) {
						int x = ((area.minX() >> 4) + c / d) * 16 + (k & 15);
						int z = ((area.minZ() >> 4) + c % d) * 16 + (k >> 4 & 15);
						int y = area.minY() + k / 256;
						first.add(x + " " + y + " " + z + ": " + from + " -> " + to);
					}
				}
			}
		}
		log(String.format(Locale.ROOT, "%s: %d chunks compared, %d blocks differ %s(%d blocks of natural trees felled by the foresters)", what, compared,
			differ, differ == 0 ? "" : kinds + " e.g. " + first + " ", trees));
		check(differ == 0, what + ": " + differ + " blocks changed");
	}

	/** Where two summaries part, with a little of each from there (module data can be long). */
	private static String firstDifference(String a, String b) {
		int i = 0;
		while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
			i++;
		}
		int from = Math.max(0, i - 120);
		return "\n    before ..." + a.substring(from, Math.min(a.length(), i + 240)) + "\n    after  ..." + b.substring(Math.min(from, b.length()),
			Math.min(b.length(), i + 240));
	}

	/** A natural tree's log or leaves felled to air or a sapling: the land's foresters (Woodcutter), not the applier. */
	private static boolean felled(BlockState from, BlockState to) {
		return (from.is(net.minecraft.tags.BlockTags.LOGS) || from.is(net.minecraft.tags.BlockTags.LEAVES))
			&& (to.isAir() || to.is(net.minecraft.tags.BlockTags.SAPLINGS));
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	// ---------------------------------------------------------------- commands

	private static void commands(TestSingleplayerContext world) {
		// as the server (an operator): none may throw
		for (String c : List.of("hearth settlement list", "hearth settlement info", "hearth sim stats", "hearth plot info", "hearth settlement sites",
			"hearth settlement economy", "hearth caravans")) {
			world.getServer().runCommand(c);
		}
		String result = world.getServer().computeOnServer(server -> {
			CommandSourceStack player = server.createCommandSourceStack().withPermission(PermissionSet.NO_PERMISSIONS);
			var dispatcher = server.getCommands().getDispatcher();
			List<String> leaked = new ArrayList<>();
			for (String c : List.of("hearth settlement list", "hearth settlement found", "hearth settlement simulate 1 10", "hearth settlement adopt",
				"hearth atlas", "hearth plot info", "hearth sim stats", "hearth sim embody 1", "hearth settlement survey", "hearth caravans",
				"hearth underground", "hearth money give @s 100", "hearth npc spawn", "hearth plan info", "hearth law pardon", "hearth repair mend",
				"hearth standing set @s 1 100", "hearth social force market")) {
				var parse = dispatcher.parse(c, player);
				if (parse.getExceptions().isEmpty() && parse.getContext().getCommand() != null && parse.getReader().getRemainingLength() == 0) {
					leaked.add(c);
				}
			}
			return leaked.isEmpty() ? "" : String.join(", ", leaked);
		});
		check(result.isEmpty(), "op-only commands a player without permissions can run: " + result);
		log("commands: ran as operator; op-only commands closed to players" + (result.isEmpty() ? "" : " EXCEPT " + result));
	}

	// ---------------------------------------------------------------- camera and helpers

	private static double[] camera(MinecraftServer server, long id) {
		SimSettlement s = sim(server).settlement(id);
		BoundingBox b = s.bounds();
		double cx = (b.minX() + b.maxX()) / 2.0;
		double cz = (b.minZ() + b.maxZ()) / 2.0;
		int ground = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) cx, (int) cz);
		double r = Math.max(b.getXSpan(), b.getZSpan()) * 0.6 + 16;
		return new double[] {cx, ground + r * 0.55, cz - r, cx, ground, cz};
	}

	private static void look(TestSingleplayerContext world, double[] v) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
			double dx = v[3] - v[0];
			double dy = v[4] - v[1];
			double dz = v[5] - v[2];
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			p.teleportTo(server.overworld(), v[0], v[1], v[2], java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	/**
	 * Leaves the world without the test framework's close, which can deadlock on 26.3 (the client halts the integrated
	 * server with a blocking call while the server waits in the framework's tick phase for the client): the server is
	 * halted from its own thread first, and the client follows once it has stopped (its halt then runs inline).
	 */
	private static void quit(ClientGameTestContext context, TestSingleplayerContext world) {
		MinecraftServer server = world.getServer().computeOnServer(s -> s);
		world.getServer().runOnServer(s -> s.halt(false));
		context.waitFor(mc -> server.isStopped(), 2400);
		context.waitFor(mc -> mc.level == null, 2400);
		context.waitTicks(2);
		context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("hearth_persist_" + name).withSize(1600, 900).withDestinationDir(OUT));
	}

	private static SimWorld sim(MinecraftServer server) {
		return SimServer.world(server.overworld());
	}

	private static void waitApplier(ClientGameTestContext context, TestSingleplayerContext world) {
		for (int i = 0; i < 240; i++) {
			settle(context, 500);
			int pending = world.getServer().computeOnServer(server -> sim(server).applier().pending());
			if (pending == 0 && i > 4) {
				break;
			}
		}
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			fail(what);
		}
	}

	private static void fail(String what) {
		failures++;
		System.out.println("[hearth persist] FAIL " + what);
	}

	private static void log(String line) {
		System.out.println("[hearth persist] " + line);
	}
}
