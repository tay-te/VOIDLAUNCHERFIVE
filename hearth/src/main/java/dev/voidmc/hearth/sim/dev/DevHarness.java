package dev.voidmc.hearth.sim.dev;

import dev.voidmc.hearth.api.Blueprint;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.apply.ChunkLedgers;
import dev.voidmc.hearth.sim.atlas.Atlas;
import dev.voidmc.hearth.sim.atlas.RegionPlan;
import dev.voidmc.hearth.sim.command.AtlasMap;
import dev.voidmc.hearth.sim.command.Report;
import dev.voidmc.hearth.sim.model.SimRules;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.GeneratorProbe;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless checks on a dedicated server, off unless asked for on the command line (like Expanse's harness):
 * <pre>
 *   -Dhearth.dev.found=N        adopt the N historic settlements nearest spawn (founding live ones if history has too few)
 *   -Dhearth.dev.radius=R       search R regions out from spawn for them (default 3)
 *   -Dhearth.dev.simulate=DAYS  then run each of them DAYS sim days forward
 *   -Dhearth.dev.bench=true     time ordinary clock steps (an hour of sim time each) per settlement
 *   -Dhearth.dev.generate=true  generate their chunks to full, let the applier finish, check what was written, and draw
 *                               a top-down map of the real blocks to {@code <world>/hearth_atlas/town_<name>.png}
 *   -Dhearth.dev.probe=N        compare the probe with the generator's own heights at N columns, and time both probes
 *   -Dhearth.dev.atlas=RADIUS   write an atlas PNG round spawn
 *   -Dhearth.dev.scale=N        found N more live settlements on a grid, age them 30-300 days, then let the server run
 *                               a minute and log its tick time and the clock's share of it
 *   -Dhearth.dev.report=true    log per-subsystem timings and settlement figures, and sample the LOD footprints
 *   -Dhearth.dev.commands=a;b   run these commands as the server (at the first settlement found; + for spaces) and log the replies
 *   -Dhearth.dev.stop=true      then shut the server down
 * </pre>
 */
public final class DevHarness {
	private static final Logger LOG = LoggerFactory.getLogger("hearth");

	private DevHarness() {
	}

	public static void install() {
		if (System.getProperty("hearth.dev.found") == null && System.getProperty("hearth.dev.probe") == null
			&& System.getProperty("hearth.dev.atlas") == null && !Boolean.getBoolean("hearth.dev.report")) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			Thread t = new Thread(() -> run(server), "hearth-dev");
			t.setDaemon(true);
			t.start();
		});
	}

	private static void run(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		try {
			if (w == null) {
				LOG.error("[hearth-dev] Hearth does not run in the overworld");
				return;
			}
			String probe = System.getProperty("hearth.dev.probe");
			if (probe != null) {
				probeCheck(level, w, Integer.parseInt(probe));
			}
			List<SimSettlement> towns = new ArrayList<>();
			String found = System.getProperty("hearth.dev.found");
			if (found != null) {
				towns = found(server, w, Integer.parseInt(found), Integer.getInteger("hearth.dev.radius", 3));
			}
			String scale = System.getProperty("hearth.dev.scale");
			if (scale != null) {
				scale(server, w, Integer.parseInt(scale));
			}
			String days = System.getProperty("hearth.dev.simulate");
			if (days != null) {
				for (SimSettlement s : towns) {
					int before = s.population();
					long nanos = server.submit(() -> w.simulate(s, Integer.parseInt(days))).join();
					LOG.info("[hearth-dev] simulated {} for {} days in {} ms: {} -> {} souls, {} plots ({}), stores {}", s.name(), days,
						String.format(Locale.ROOT, "%.1f", nanos / 1e6), before, s.population(), s.plots().size(), s.plotCounts(), s.stockpile());
				}
			}
			if (Boolean.getBoolean("hearth.dev.bench")) {
				for (SimSettlement s : towns) {
					bench(server, w, s);
				}
			}
			if (Boolean.getBoolean("hearth.dev.generate")) {
				for (SimSettlement s : towns) {
					generate(server, level, w, s);
				}
			}
			String atlas = System.getProperty("hearth.dev.atlas");
			if (atlas != null) {
				int radius = Integer.parseInt(atlas);
				List<AtlasMap.LiveTown> live = server.submit(() -> w.settlements().stream().map(AtlasMap.LiveTown::of).toList()).join();
				AtlasMap.Result r = AtlasMap.render(w.atlas(), w.probe().rough(), server.getWorldPath(LevelResource.ROOT).resolve("hearth_atlas"), 0, 0, radius,
					Math.max(2, radius / 384), live);
				LOG.info("[hearth-dev] atlas: {} sites in {} regions, {} ms -> {}", r.sites(), r.regions(), r.millis(), r.image());
			}
			String commands = System.getProperty("hearth.dev.commands");
			if (commands != null) {
				BlockPos at = towns.isEmpty() ? BlockPos.ZERO : towns.getFirst().center();
				for (String command : commands.split(";")) {
					runCommand(server, level, at, command.trim().replace('+', ' '));
				}
			}
			if (Boolean.getBoolean("hearth.dev.report")) {
				for (SimSettlement t : towns) {
					LOG.info("[hearth-dev] {}", server.submit(() -> footprints(w, t)).join());
				}
				SimServer s = SimServer.current();
				if (s != null) {
					for (String line : server.submit(() -> Report.lines(s, w)).join()) {
						LOG.info("[hearth-dev] {}", line);
					}
				}
				for (SimSettlement t : towns) {
					LOG.info("[hearth-dev] {}", server.submit(() -> describe(t)).join());
				}
			}
		} catch (Throwable t) {
			LOG.error("[hearth-dev] harness failed", t);
		}
		if (Boolean.getBoolean("hearth.dev.stop")) {
			// let unloads of the chunks this run forced settle first: a burst of them at shutdown overflows the stack in
			// vanilla's mailbox draining
			sleep(5000);
			server.execute(() -> server.halt(false));
		}
	}

	private static String describe(SimSettlement s) {
		return String.format(Locale.ROOT, "#%d %s: %s %s at %s, %d souls, %d beds, %d plots %s, treasury %d, stores %s", s.id(), s.name(),
			s.culture().getSerializedName(), s.tier().getSerializedName(), s.center().toShortString(), s.population(), s.beds(), s.plots().size(),
			s.plotCounts(), s.treasury(), s.stockpile());
	}

	/** Compares the probe with the generator's own base heights, and times the generic probe against the active one. */
	private static void probeCheck(ServerLevel level, SimWorld w, int n) {
		RandomSource random = RandomSource.create(42);
		var gen = level.getChunkSource().getGenerator();
		var rs = level.getChunkSource().randomState();
		int exact = 0;
		int near = 0;
		long diff = 0;
		int[] xs = new int[n];
		int[] zs = new int[n];
		for (int i = 0; i < n; i++) {
			xs[i] = random.nextInt(8192) - 4096;
			zs[i] = random.nextInt(8192) - 4096;
		}
		int[] rough = new int[n];
		for (int i = 0; i < n; i++) {
			rough[i] = w.probe().rough().groundY(xs[i], zs[i]);
		}
		long t0 = System.nanoTime();
		int[] probe = new int[n];
		for (int i = 0; i < n; i++) {
			probe[i] = w.probe().groundY(xs[i], zs[i]);
		}
		long probeNanos = System.nanoTime() - t0;
		GeneratorProbe generic = new GeneratorProbe(level);
		t0 = System.nanoTime();
		int[] genericY = new int[n];
		for (int i = 0; i < n; i++) {
			genericY[i] = generic.groundY(xs[i], zs[i]);
		}
		long genericNanos = System.nanoTime() - t0;
		int roughNear = 0;
		for (int i = 0; i < n; i++) {
			int base = gen.getBaseHeight(xs[i], zs[i], Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
			int d = Math.abs(base - probe[i]);
			diff += d;
			exact += d == 0 ? 1 : 0;
			near += d <= 1 ? 1 : 0;
			roughNear += Math.abs(base - rough[i]) <= 1 ? 1 : 0;
		}
		LOG.info("[hearth-dev] probe without its correction: {} of {} within 1", roughNear, n);
		LOG.info("[hearth-dev] probe {}: {} columns, {} exact, {} within 1 of the generator, mean |diff| {}; {} µs/column (generic probe {} µs/column)",
			w.probe().describe(), n, exact, near, String.format(Locale.ROOT, "%.2f", diff / (double) n),
			String.format(Locale.ROOT, "%.1f", probeNanos / 1e3 / n), String.format(Locale.ROOT, "%.1f", genericNanos / 1e3 / n));
	}

	private static List<SimSettlement> found(MinecraftServer server, SimWorld w, int count, int radius) {
		List<SimSettlement> out = new ArrayList<>();
		long start = System.nanoTime();
		List<RegionPlan> plans = new ArrayList<>();
		search:
		for (int r = 0; r <= radius; r++) {
			for (int rx = -r; rx <= r; rx++) {
				for (int rz = -r; rz <= r; rz++) {
					if (Math.max(Math.abs(rx), Math.abs(rz)) != r) {
						continue;
					}
					RegionPlan plan = w.atlas().plan(rx, rz);
					if (plan.settled()) {
						RegionPlan.Site site = plan.site();
						SimSettlement h = plan.history();
						LOG.info("[hearth-dev] region {},{}: {} ({}, {}, age {} d, biome {}) at {} — {} souls, {} plots ({}), history {} ms, region {} ms", rx, rz,
							site.name(), site.culture().getSerializedName(), h.tier().getSerializedName(), site.age(), site.biome(), site.center().toShortString(),
							h.population(), h.plots().size(), plan.plots().size() + " with completed stages",
							String.format(Locale.ROOT, "%.0f", plan.historyMillis()), String.format(Locale.ROOT, "%.0f", plan.millis()));
						plans.add(plan);
						if (plans.size() >= count) {
							break search;
						}
					} else {
						LOG.info("[hearth-dev] region {},{}: unsettled ({} ms)", rx, rz, String.format(Locale.ROOT, "%.0f", plan.millis()));
					}
				}
			}
		}
		LOG.info("[hearth-dev] {} historic sites found in {} ms", plans.size(), (System.nanoTime() - start) / 1_000_000);
		for (RegionPlan plan : plans) {
			SimSettlement s = server.submit(() -> w.adopt(plan)).join();
			if (s != null) {
				out.add(s);
			}
		}
		for (int i = out.size(); i < count; i++) {
			int k = i;
			SimSettlement s = server.submit(() -> w.found(new BlockPos(600 * (k % 3) - 600, 0, 600 * (k / 3) - 600), null, null)).join();
			out.add(s);
		}
		// let the clock catch the adopted ones up to the present
		for (int tries = 0; tries < 200; tries++) {
			boolean behind = server.submit(() -> {
				for (SimSettlement s : out) {
					if (s.simTick() < w.simNow(s)) {
						return true;
					}
				}
				return false;
			}).join();
			if (!behind) {
				break;
			}
			sleep(100);
		}
		return out;
	}

	/** Times ordinary clock steps: an hour of sim time each, what a settlement nobody is near gets every 1200 ticks. */
	private static void bench(MinecraftServer server, SimWorld w, SimSettlement s) {
		String result = server.submit(() -> {
			int reps = 200;
			long total = 0;
			long max = 0;
			for (int i = 0; i < reps; i++) {
				s.addTimeOffset(1200);
				long t0 = System.nanoTime();
				SettlementClock.advance(s, w.simNow(s));
				long dt = System.nanoTime() - t0;
				total += dt;
				max = Math.max(max, dt);
			}
			return String.format(Locale.ROOT, "%s (%d souls, %d plots): %.4f ms per hour-step avg, %.3f ms max over %d steps (%.1f sim days)", s.name(),
				s.population(), s.plots().size(), total / 1e6 / reps, max / 1e6, reps, reps * 1200 / (double) SimRules.DAY);
		}).join();
		LOG.info("[hearth-dev] bench {}", result);
	}

	/** Generates every chunk a settlement covers, waits for the applier, then checks the world against the record. */
	private static void generate(MinecraftServer server, ServerLevel level, SimWorld w, SimSettlement s) throws Exception {
		BoundingBox b = server.submit(s::bounds).join();
		int c0x = (b.minX() >> 4) - 1;
		int c0z = (b.minZ() >> 4) - 1;
		int c1x = (b.maxX() >> 4) + 1;
		int c1z = (b.maxZ() >> 4) + 1;
		long start = System.nanoTime();
		List<CompletableFuture<?>> loading = server.submit(() -> {
			List<CompletableFuture<?>> futures = new ArrayList<>();
			for (int cx = c0x; cx <= c1x; cx++) {
				for (int cz = c0z; cz <= c1z; cz++) {
					// forced, so they stay loaded while the applier works through them
					level.setChunkForced(cx, cz, true);
					futures.add(level.getChunkSource().getChunkFuture(cx, cz, ChunkStatus.FULL, true));
				}
			}
			return futures;
		}).join();
		CompletableFuture.allOf(loading.toArray(CompletableFuture[]::new)).get();
		long genMs = (System.nanoTime() - start) / 1_000_000;
		for (int tries = 0; tries < 600 && server.submit(() -> w.applier().pending()).join() > 0; tries++) {
			sleep(50);
		}
		String check = server.submit(() -> verify(level, w, s)).join();
		String picture = server.submit(() -> snapshot(level, s, b)).join();
		LOG.info("[hearth-dev] {}", picture);
		server.submit(() -> {
			for (int cx = c0x; cx <= c1x; cx++) {
				for (int cz = c0z; cz <= c1z; cz++) {
					level.setChunkForced(cx, cz, false);
				}
			}
		}).join();
		LOG.info("[hearth-dev] generated {} chunks of {} in {} ms; {}", (c1x - c0x + 1) * (c1z - c0z + 1), s.name(), genMs, check);
	}

	/** Draws the settlement from above, block by block as the world holds it, shaded by height. */
	private static String snapshot(ServerLevel level, SimSettlement s, BoundingBox b) {
		int x0 = b.minX() - 16;
		int z0 = b.minZ() - 16;
		int w = b.maxX() - b.minX() + 33;
		int h = b.maxZ() - b.minZ() + 33;
		int scale = Math.max(1, Math.min(4, 1024 / Math.max(w, h)));
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * scale, h * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int[][] heights = new int[w][h];
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < h; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow((x0 + dx) >> 4, (z0 + dz) >> 4);
				heights[dx][dz] = chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, (x0 + dx) & 15, (z0 + dz) & 15);
			}
		}
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < h; dz++) {
				int y = heights[dx][dz];
				int rgb = 0x202020;
				if (y != Integer.MIN_VALUE) {
					p.set(x0 + dx, y, z0 + dz);
					var state = level.getBlockState(p);
					rgb = state.getMapColor(level, p).calculateARGBColor(net.minecraft.world.level.material.MapColor.Brightness.NORMAL) & 0xFFFFFF;
					int north = dz > 0 && heights[dx][dz - 1] != Integer.MIN_VALUE ? heights[dx][dz - 1] : y;
					float shade = y > north ? 1.15F : y < north ? 0.8F : 1.0F;
					int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * shade));
					int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * shade));
					int bl = Math.min(255, (int) ((rgb & 0xFF) * shade));
					rgb = r << 16 | g << 8 | bl;
				}
				for (int sx = 0; sx < scale; sx++) {
					for (int sz = 0; sz < scale; sz++) {
						img.setRGB(dx * scale + sx, dz * scale + sz, rgb);
					}
				}
			}
		}
		try {
			java.nio.file.Path dir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("hearth_atlas");
			java.nio.file.Files.createDirectories(dir);
			java.nio.file.Path file = dir.resolve("town_" + s.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_") + ".png");
			javax.imageio.ImageIO.write(img, "png", file.toFile());
			return "snapshot of " + s.name() + " -> " + file;
		} catch (java.io.IOException e) {
			return "snapshot of " + s.name() + " failed: " + e;
		}
	}

	/** For every plot, the share of its written steps (by the chunk ledgers) that the world really holds. */
	private static String verify(ServerLevel level, SimWorld w, SimSettlement s) {
		int expected = 0;
		int matched = 0;
		int plots = 0;
		int ledgerAhead = 0;
		for (SimPlot p : s.plots()) {
			if (p.state() == PlotState.PLANNED) {
				continue;
			}
			Blueprint bp = HearthServices.blueprints().blueprint(p.spec(), w.terrain());
			var reach = p.reach();
			plots++;
			for (int cx = reach.minX() >> 4; cx <= reach.maxX() >> 4; cx++) {
				for (int cz = reach.minZ() >> 4; cz <= reach.maxZ() >> 4; cz++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
					if (chunk == null) {
						continue;
					}
					long cursor = ChunkLedgers.of(chunk).get(s.id(), p.id());
					if (cursor > s.applicable(p)) {
						ledgerAhead++;
					}
					int[] e = {0, 0};
					// the last state each position was given is what should stand there
					java.util.Map<Long, net.minecraft.world.level.block.state.BlockState> last = new java.util.HashMap<>();
					dev.voidmc.hearth.sim.apply.StepWriter.forRange(bp, cx, cz, Cursor.ZERO, cursor,
						(x, y, z, state, tag) -> last.put(BlockPos.asLong(x, y, z), state));
					for (var en : last.entrySet()) {
						e[0]++;
						if (chunk.getBlockState(BlockPos.of(en.getKey())) == en.getValue()) {
							e[1]++;
						}
					}
					expected += e[0];
					matched += e[1];
				}
			}
		}
		return String.format(Locale.ROOT, "%d plots with work, %d of %d written positions hold their block (%.1f%%), %d ledgers ahead of the record",
			plots, matched, expected, expected == 0 ? 100 : 100.0 * matched / expected, ledgerAhead);
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Many settlements at once: found them, age them, then measure the server running with all of them. */
	private static void scale(MinecraftServer server, SimWorld w, int count) {
		long start = System.nanoTime();
		List<SimSettlement> made = new ArrayList<>();
		RandomSource random = RandomSource.create(7);
		int side = (int) Math.ceil(Math.sqrt(count * 1.6));
		for (int i = 0; i < side * side && made.size() < count; i++) {
			int gx = i % side - side / 2;
			int gz = i / side - side / 2;
			BlockPos at = new BlockPos(gx * 640 + 320, 0, gz * 640 + 320);
			SimSettlement s = server.submit(() -> w.whyNotFound(at, 300) == null ? w.found(at, null, null) : null).join();
			if (s != null) {
				made.add(s);
			}
		}
		long foundMs = (System.nanoTime() - start) / 1_000_000;
		long simStart = System.nanoTime();
		int days = 0;
		for (SimSettlement s : made) {
			int d = 30 + random.nextInt(271);
			days += d;
			server.submit(() -> w.simulate(s, d)).join();
		}
		long simMs = (System.nanoTime() - simStart) / 1_000_000;
		int pop = server.submit(() -> w.settlements().stream().mapToInt(SimSettlement::population).sum()).join();
		LOG.info("[hearth-dev] scale: founded {} settlements in {} ms, aged them {} sim days in all in {} ms; {} settlements, {} souls in the world",
			made.size(), foundMs, days, simMs, server.submit(() -> w.settlements().size()).join(), pop);
		SimServer s = SimServer.current();
		if (s == null) {
			return;
		}
		long steps0 = s.stats().steps.sum();
		long stepNanos0 = s.stats().stepNanos.sum();
		long tickNanos0 = s.stats().tickNanos.sum();
		long ticks0 = s.ticks();
		long worst = 0;
		double msptSum = 0;
		int samples = 0;
		for (int i = 0; i < 60; i++) {
			sleep(1000);
			double mspt = server.getAverageTickTimeNanos() / 1e6;
			msptSum += mspt;
			samples++;
			worst = Math.max(worst, (long) (mspt * 1000));
		}
		long steps = s.stats().steps.sum() - steps0;
		long ticks = s.ticks() - ticks0;
		LOG.info("[hearth-dev] scale: over {} ticks: server {} ms/tick average (worst 1 s average {} ms); the sim's tick work {} ms/tick; {} steps, {} ms per step",
			ticks, String.format(Locale.ROOT, "%.2f", msptSum / samples), String.format(Locale.ROOT, "%.2f", worst / 1000.0),
			String.format(Locale.ROOT, "%.4f", (s.stats().tickNanos.sum() - tickNanos0) / 1e6 / Math.max(1, ticks)), steps,
			String.format(Locale.ROOT, "%.4f", steps == 0 ? 0 : (s.stats().stepNanos.sum() - stepNanos0) / 1e6 / steps));
	}

	/** Runs a command as the server, standing at {@code at}, and logs its feedback. */
	private static void runCommand(MinecraftServer server, ServerLevel level, BlockPos at, String command) {
		List<String> said = new java.util.concurrent.CopyOnWriteArrayList<>();
		net.minecraft.commands.CommandSource sink = new net.minecraft.commands.CommandSource() {
			@Override
			public void sendSystemMessage(net.minecraft.network.chat.Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		server.submit(() -> {
			var source = server.createCommandSourceStack().withSource(sink).withLevel(level)
				.withPosition(net.minecraft.world.phys.Vec3.atBottomCenterOf(at));
			server.getCommands().performPrefixedCommand(source, command);
		}).join();
		sleep(200);
		LOG.info("[hearth-dev] /{} ->", command);
		for (String line : said) {
			LOG.info("[hearth-dev]   {}", line);
		}
	}

	/** Samples the LOD footprints at the middle of each finished house of a settlement. */
	private static String footprints(SimWorld w, SimSettlement s) {
		int[] out = new int[3];
		int hit = 0;
		int tried = 0;
		String example = "";
		for (SimPlot p : s.plots()) {
			if (!p.isStanding() || dev.voidmc.hearth.sim.atlas.PlotColumns.isLine(p.spec())) {
				continue;
			}
			tried++;
			int x = p.spec().origin().getX() + p.spec().sizeX() / 2;
			int z = p.spec().origin().getZ() + p.spec().sizeZ() / 2;
			if (w.footprints().sample(x, z, out)) {
				hit++;
				if (example.isEmpty()) {
					example = String.format(Locale.ROOT, " (e.g. %s: wall #%06X, roof #%06X, eave %d, ridge %d, region version %d)",
						p.spec().archetype().getPath(), out[0] & 0xFFFFFF, out[1] & 0xFFFFFF, out[2] >> 16, (short) out[2],
						w.footprints().version(Atlas.region(x), Atlas.region(z)));
				}
			}
		}
		return "LOD footprints of " + s.name() + ": " + hit + " of " + tried + " finished buildings found" + example;
	}

	/** Region of a block, for log lines. */
	static String region(BlockPos p) {
		return Atlas.region(p.getX()) + "," + Atlas.region(p.getZ()) + " " + ChunkPos.containing(p);
	}
}
