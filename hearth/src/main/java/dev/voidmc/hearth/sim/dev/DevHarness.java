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
 *   -Dhearth.dev.generate=true  generate their chunks to full, let the applier finish, and check what was written
 *   -Dhearth.dev.probe=N        compare the probe with the generator's own heights at N columns, and time both probes
 *   -Dhearth.dev.atlas=RADIUS   write an atlas PNG round spawn
 *   -Dhearth.dev.report=true    log per-subsystem timings and settlement figures
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
				AtlasMap.Result r = AtlasMap.render(w.atlas(), w.probe(), server.getWorldPath(LevelResource.ROOT).resolve("hearth_atlas"), 0, 0, radius,
					Math.max(2, radius / 384), live);
				LOG.info("[hearth-dev] atlas: {} sites in {} regions, {} ms -> {}", r.sites(), r.regions(), r.millis(), r.image());
			}
			if (Boolean.getBoolean("hearth.dev.report")) {
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
		for (int i = 0; i < n; i++) {
			int base = gen.getBaseHeight(xs[i], zs[i], Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
			int d = Math.abs(base - probe[i]);
			diff += d;
			exact += d == 0 ? 1 : 0;
			near += d <= 1 ? 1 : 0;
		}
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
		server.submit(() -> {
			for (int cx = c0x; cx <= c1x; cx++) {
				for (int cz = c0z; cz <= c1z; cz++) {
					level.setChunkForced(cx, cz, false);
				}
			}
		}).join();
		LOG.info("[hearth-dev] generated {} chunks of {} in {} ms; {}", (c1x - c0x + 1) * (c1z - c0z + 1), s.name(), genMs, check);
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

	/** Region of a block, for log lines. */
	static String region(BlockPos p) {
		return Atlas.region(p.getX()) + "," + Atlas.region(p.getZ()) + " " + ChunkPos.containing(p);
	}
}
