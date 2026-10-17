package dev.voidmc.reel.client;

import dev.voidmc.reel.Reel;
import dev.voidmc.reel.math.Easing;
import dev.voidmc.reel.math.Vec3;
import dev.voidmc.reel.shot.PathRig;
import dev.voidmc.reel.shot.Shot;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

/**
 * The VOID Welkin studio's desk (tools/wstudio.sh): a studio that stays open so a shader fix is checked in seconds,
 * edit, reload, shoot, instead of a fresh game test per look.
 *
 * <pre>
 * GET  /studio                 the studio's state (starting, world, settling, ready), the pack Iris runs, the view distance
 * POST /reload                 reloads the shader pack (Iris re-reads a folder pack's files) and reports a stored error
 * POST /hold?on=true|false     whether the world's ticks stay frozen between shots (default on: welkin.hold)
 * POST /shoot?tp=..&amp;time=..[&amp;weather=clear&amp;fov=70&amp;lens=..&amp;w=1600&amp;h=900&amp;name=..&amp;out=..&amp;ticks=N&amp;warmup=0|1]
 *                              time set, weather, the tp command as given (y is the feet, as the bench's), the settle
 *                              (below), then one frame from where tp put the eye (Reel's snap: prefetch, near chunks and
 *                              far tiles waited for); answers when the PNG is written, with its path and the timings.
 *                              fov is the FOV option the bench had; the frame is taken at what the game drew with it,
 *                              fov x 1.1 (a spectator flies, and flying widens the view by 10% at full FOV effects),
 *                              unless lens= gives the exact vertical FOV.
 * </pre>
 *
 * <p>Holding: between shots the world's ticks are frozen (tick freeze), so the game time, and with it the clouds, the
 * wind and the sway, stand still: two frames of one view before and after a shader edit differ only by the edit. The
 * day's clock is set by each shot. POST /hold?on=false lets the world run as the bench's did.
 *
 * <p>The settle: a shot that moves (another tp than the last shot's), or changes the clock or the weather, first runs
 * the held world for a budget of ticks, as fast as the game can ({@code tick sprint}, and ten client ticks a frame, the
 * game test harness ticking the server in step with the client), so water flows at the new
 * spot, waterfalls fall and the rain level ramps to what the weather calls for: {@code welkin.settle.moved} ticks
 * (600) after a move, once the chunks round the eye are in, and {@code welkin.settle.changed} (120) after a change of
 * clock or weather only; {@code ticks=} overrides both. The world is then frozen again with its game time wound back
 * to the held one (so clouds and wind stand where they stood) and the clock set to the shot's tick again, and the
 * client is given {@code welkin.settle.client} ticks (20, 40 after a move) to take it all in, and the ticks Gloam's
 * wet ground needs to come to the rain (soak through, or dry out after it: see {@link #soak}). An unchanged shot runs
 * no ticks: shot twice, a view gives the same frame. A shot that settled also films one warm-up frame, thrown away,
 * before the real one ({@code warmup=0|1} overrides).
 */
public final class WelkinDesk {
	private static volatile String state = "starting";
	private static final AtomicInteger RELOADS = new AtomicInteger();
	private static final Object JOB = new Object();
	private static final long MOVED_SETTLE_MS = 3_000;
	private static final long STILL_SETTLE_MS = 1_000;
	/** World ticks run after a move, and after a change of clock or weather only (see the class comment). */
	private static final int MOVED_TICKS = Integer.getInteger("welkin.settle.moved", 600);
	private static final int CHANGED_TICKS = Integer.getInteger("welkin.settle.changed", 120);
	/** Client ticks after the settle (twice as many after a move), for the client and Gloam to take the new place in. */
	private static final int CLIENT_TICKS = Integer.getInteger("welkin.settle.client", 20);
	private static final long CHUNK_WAIT_MS = 180_000;
	private static final long SPRINT_WAIT_MS = 300_000;
	private static volatile boolean hold = !"false".equals(System.getProperty("welkin.hold"));
	private static final AtomicLong CLIENT_TICK = new AtomicLong();
	/** Ticks a pass of the game loop runs while a settle sprints (Minecraft runs at most ten a pass). */
	public static final int FAST_TICKS = 10;
	private static volatile boolean fastForward;
	private static final int SOAK_RATE = 1000;
	/** How close Gloam's wetness must come to the rain, and the most client ticks the soak may take (a dry-out). */
	private static final float SOAKED = 0.001F;
	private static final int SOAK_LIMIT = 40_000;
	/** What the world was last settled for: the tp, the clock tick and the weather. */
	private static String lastTp = "";
	private static long lastTime = Long.MIN_VALUE;
	private static String lastWeather = "";
	/** The game time the held world stands at (Long.MIN_VALUE until the studio is ready or while not holding). */
	private static volatile long heldGameTime = Long.MIN_VALUE;

	private WelkinDesk() {
	}

	/** The studio's phase, logged as {@code [welkin] studio <state>}. */
	public static void state(String now) {
		state = now;
		Reel.LOG.info("[welkin] studio {}", now);
	}

	/** Freezes the world's ticks while holding (or lets them run when not): the studio calls it once ready, and after each shot. */
	public static void hold(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			server(mc, hold ? "tick freeze" : "tick unfreeze");
		}
	}

	/**
	 * The studio is ready and held at its warm-up view: what the world is settled for, and the game time it holds at
	 * (the studio ran there long enough for its water to flow).
	 */
	public static void ready(String tp, long time, String weather, long gameTime) {
		synchronized (JOB) {
			lastTp = tp.trim();
			lastTime = time;
			lastWeather = weather;
			heldGameTime = hold ? gameTime : Long.MIN_VALUE;
		}
	}

	/** Whether a settle is sprinting: the frame timer then runs {@link #FAST_TICKS} ticks a pass (TimerMixin). */
	public static boolean fastForward() {
		return fastForward;
	}

	/** Counts client ticks (ReelClient, at the end of each): the settle waits for client ticks, not for wall time. */
	public static void clientTicked() {
		CLIENT_TICK.incrementAndGet();
	}

	/** Near chunks meshed and far tiles drawn round the camera: what the studio waits for before it says ready. */
	public static boolean viewReady(Minecraft mc) {
		return mc.level != null && Readiness.nearReady(mc) && Readiness.farComplete();
	}

	public static String loading(Minecraft mc) {
		return Readiness.state(mc);
	}

	/** Answers a Welkin path, or null when the path is not one. */
	static String route(String path, Map<String, String> q) throws IOException {
		return switch (path) {
			case "/studio" -> studio();
			case "/reload" -> reload();
			case "/shoot" -> shoot(q);
			case "/hold" -> {
				synchronized (JOB) {
					if (q.containsKey("on")) {
						hold = Boolean.parseBoolean(q.get("on"));
					}
					Minecraft mc = Minecraft.getInstance();
					hold(mc);
					// held again after running: the world holds at the game time it stopped at
					heldGameTime = hold ? gameTime(mc) : Long.MIN_VALUE;
					yield "{\"hold\":" + hold + ",\"gameTime\":" + heldGameTime + "}";
				}
			}
			default -> null;
		};
	}

	private static String studio() {
		String pack = iris("getCurrentPackName") instanceof String s ? s : "none";
		return ControlServer.onClient(() -> {
			Minecraft mc = Minecraft.getInstance();
			return String.format(Locale.ROOT, "{\"state\":\"%s\",\"pack\":\"%s\",\"distance\":%d,\"director\":\"%s\",\"reloads\":%d}", state, Report.escape(pack),
				mc.options.getEffectiveRenderDistance(), Director.get().phase().name().toLowerCase(Locale.ROOT), RELOADS.get());
		});
	}

	/**
	 * Iris.reload() on the client thread, then a few frames so the new pipeline is built (Iris compiles a pack's
	 * programs when the world is next drawn), then Iris's stored error if there is one. tools/wstudio.sh also reads
	 * the log between the "[welkin] reload N" lines for compile errors.
	 */
	private static String reload() {
		synchronized (JOB) {
			int n = RELOADS.incrementAndGet();
			long start = System.nanoTime();
			Reel.LOG.info("[welkin] reload {} begins", n);
			String failed = ControlServer.onClient(() -> {
				try {
					Class.forName("net.irisshaders.iris.Iris").getMethod("reload").invoke(null);
					return "";
				} catch (InvocationTargetException e) {
					return String.valueOf(e.getCause());
				} catch (ReflectiveOperationException e) {
					return "Iris is not loaded: " + e;
				}
			});
			for (int i = 0; i < 10; i++) {
				ControlServer.onClient(() -> "");
			}
			String stored = iris("getStoredError") instanceof Optional<?> o && o.isPresent() ? String.valueOf(o.get()) : "";
			String pack = iris("getCurrentPackName") instanceof String s ? s : "none";
			double ms = (System.nanoTime() - start) / 1e6;
			Reel.LOG.info("[welkin] reload {} done in {} ms", n, Math.round(ms));
			return String.format(Locale.ROOT, "{\"reload\":%d,\"ms\":%.0f,\"pack\":\"%s\",\"error\":\"%s\"}", n, ms, Report.escape(pack),
				Report.escape(failed.isEmpty() ? stored : failed));
		}
	}

	private static String shoot(Map<String, String> q) throws IOException {
		String tp = need(q, "tp");
		if (!tp.startsWith("tp ")) {
			throw new IllegalArgumentException("tp must be a tp command, e.g. tp @a -1009 148 -1386 180.0 6.0");
		}
		long time = Long.parseLong(need(q, "time"));
		String weather = q.getOrDefault("weather", "clear");
		double fov = Double.parseDouble(q.getOrDefault("fov", "70"));
		double lens = q.containsKey("lens") ? Double.parseDouble(q.get("lens")) : fov * 1.1;
		int w = Integer.parseInt(q.getOrDefault("w", "1600"));
		int h = Integer.parseInt(q.getOrDefault("h", "900"));
		String name = q.getOrDefault("name", "shot").replaceAll("[^A-Za-z0-9_.-]", "_");
		Path out = q.containsKey("out") ? Path.of(q.get("out")) : Reel.renders().resolve("welkin");
		Minecraft mc = Minecraft.getInstance();
		synchronized (JOB) {
			if (Director.get().busy()) {
				throw new IllegalArgumentException("the director is busy (" + Director.get().phase() + "); POST /stop first");
			}
			long start = System.nanoTime();
			String spot = tp.trim();
			boolean moved = !spot.equals(lastTp);
			boolean changed = moved || time != lastTime || !weather.equals(lastWeather);
			int ticks = q.containsKey("ticks") ? Integer.parseInt(q.get("ticks")) : moved ? MOVED_TICKS : changed ? CHANGED_TICKS : 0;
			boolean warmup = q.containsKey("warmup") ? !"0".equals(q.get("warmup")) && !"false".equals(q.get("warmup")) : changed;
			server(mc, "time set " + time, "weather " + weather, tp);
			// set before anything can fail: the world has been moved and set, whatever comes next
			lastTp = spot;
			lastTime = time;
			lastWeather = weather;
			long chunkMs = 0, sprintMs = 0;
			if (hold && ticks > 0) {
				// the settle: the chunks round the eye in first (fluids only flow in loaded, ticking chunks), then the ticks
				if (moved) {
					long t0 = System.nanoTime();
					awaitChunks(mc);
					chunkMs = (System.nanoTime() - t0) / 1_000_000;
				}
				long t0 = System.nanoTime();
				sprint(mc, ticks);
				sprintMs = (System.nanoTime() - t0) / 1_000_000;
				// frozen again: the game time back where the world is held, the clock back on the shot's tick
				rewind(mc, time);
			}
			long settleStart = System.nanoTime();
			long soakTicks = hold ? soak() : 0;
			if (hold) {
				// the client takes the move, the clock and the weather in on its own ticks (Gloam's sky, bridge and seasons)
				int clientTicks = q.containsKey("settle") ? Integer.parseInt(q.get("settle")) : changed ? (moved ? 2 : 1) * CLIENT_TICKS : CLIENT_TICKS / 2;
				waitClientTicks(clientTicks);
			} else {
				sleep(moved ? MOVED_SETTLE_MS : STILL_SETTLE_MS);
			}
			waitSeasons();
			long settle = (System.nanoTime() - settleStart) / 1_000_000 + chunkMs + sprintMs;
			// the camera exactly where tp put the eye (tp centres whole x and z on the block), and what the world shows
			String[] eye = ControlServer.onClient(() -> {
				var e = mc.player.getEyePosition();
				return String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.3f,%.3f,%d,%d,%.4f,%s", e.x, e.y, e.z, mc.player.getYRot(), mc.player.getXRot(),
					mc.level.getOverworldClockTime(), mc.level.getGameTime(), mc.level.getRainLevel(1.0F), gloamWetness());
			}).split(",");
			if (eye.length < 9) {
				return ControlServer.error("the client did not answer: " + String.join(",", eye));
			}
			Vec3 pos = new Vec3(Double.parseDouble(eye[0]), Double.parseDouble(eye[1]), Double.parseDouble(eye[2]));
			double yaw = Double.parseDouble(eye[3]), pitch = Double.parseDouble(eye[4]);
			long clock = Long.parseLong(eye[5]), gameTime = Long.parseLong(eye[6]);
			long warmStart = System.nanoTime();
			if (warmup) {
				// one frame filmed and thrown away: the first frame after a move or a change is a step off in a few pixels
				// (a few thousand after a long move), the next ones repeat exactly
				if (snap(mc, pos, yaw, pitch, lens, w, h) == null) {
					return ControlServer.error("the warm-up frame did not finish in 10 minutes");
				}
			}
			long warmMs = (System.nanoTime() - warmStart) / 1_000_000;
			long snapStart = System.nanoTime();
			RenderJob job = snap(mc, pos, yaw, pitch, lens, w, h);
			if (job == null) {
				return ControlServer.error("the frame did not finish in 10 minutes");
			}
			Path frame = job.fileFor(0);
			if (!Files.isRegularFile(frame)) {
				return ControlServer.error("no frame written at " + frame + ": " + Director.get().statusJson());
			}
			Files.createDirectories(out);
			Path dest = out.resolve(name + ".png");
			Files.copy(frame, dest, StandardCopyOption.REPLACE_EXISTING);
			Path report = job.out().resolve("stills.json");
			String json = Files.isRegularFile(report) ? Files.readString(report).trim() : "null";
			double total = (System.nanoTime() - start) / 1e9, snap = (System.nanoTime() - snapStart) / 1e9;
			Reel.LOG.info("[welkin] shot {} in {} s (settle {} ms: chunks {} ms, {} ticks in {} ms, soak {} client ticks; warm-up {} ms, frame {} s; clock {}, game time {}, rain {}, wet ground {}): {}", name,
				String.format(Locale.ROOT, "%.1f", total), settle, chunkMs, hold ? ticks : 0, sprintMs, soakTicks, warmMs, String.format(Locale.ROOT, "%.1f", snap), clock, gameTime,
				eye[7], eye[8], dest);
			return String.format(Locale.ROOT, "{\"file\":\"%s\",\"seconds\":%.2f,\"settleMs\":%d,\"chunkMs\":%d,\"ticks\":%d,\"sprintMs\":%d,\"soakTicks\":%d,\"warmupMs\":%d,"
				+ "\"frameSeconds\":%.2f,\"eye\":[%s,%s,%s],\"yaw\":%s,\"pitch\":%s,\"lens\":%.1f,\"time\":%d,\"clock\":%d,\"gameTime\":%d,\"rain\":%s,\"wetness\":%s,\"report\":%s}",
				Report.escape(dest.toString()), total, settle, chunkMs, hold ? ticks : 0, sprintMs, soakTicks, warmMs, snap, eye[0], eye[1], eye[2], eye[3], eye[4], lens, time,
				clock, gameTime, eye[7], eye[8], json.isEmpty() ? "null" : json);
		}
	}

	/** Films one frame from the eye (Reel's snap), or null when it did not finish in 10 minutes; the world is held after. */
	private static RenderJob snap(Minecraft mc, Vec3 pos, double yaw, double pitch, double lens, int w, int h) {
		PathRig rig = new PathRig(List.of(new PathRig.Key(pos, null, yaw, pitch, null, lens, null)), 1, PathRig.Timing.EVEN, Easing.LINEAR, null, false, lens);
		Shot shot = new Shot("_snap-welkin", 60, 1, w, h, Shot.World.UNCHANGED, rig, null, Shot.Render.DEFAULT);
		RenderJob job = RenderJob.at(shot, Reel.renders(), new double[] {0}, w, h);
		ControlServer.onClient(() -> {
			Director.get().start(mc, job);
			return "";
		});
		long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
		while (Director.get().busy() && System.nanoTime() < deadline) {
			sleep(50);
		}
		boolean late = Director.get().busy();
		if (late) {
			ControlServer.onClient(() -> {
				Director.get().stop(mc);
				return "";
			});
		}
		hold(mc);
		return late ? null : job;
	}

	/** The chunks round the eye loaded and drawn (five checks in a row), or three minutes gone. */
	private static void awaitChunks(Minecraft mc) {
		long until = System.currentTimeMillis() + CHUNK_WAIT_MS;
		int streak = 0;
		while (streak < 5 && System.currentTimeMillis() < until) {
			streak = "true".equals(ControlServer.onClient(() -> String.valueOf(mc.level != null && Readiness.nearReady(mc)))) ? streak + 1 : 0;
			sleep(100);
		}
		if (streak < 5) {
			Reel.LOG.warn("[welkin] the chunks round the eye did not load in {} s; settling anyway ({})", CHUNK_WAIT_MS / 1000,
				ControlServer.onClient(() -> Readiness.state(mc)));
		}
	}

	/**
	 * Runs the world {@code ticks} ticks as fast as the server can ({@code tick sprint}) and waits until they ran. A
	 * sprint unfreezes the world and puts the freeze back when it ends.
	 */
	private static void sprint(Minecraft mc, int ticks) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			throw new IllegalArgumentException("not in single player");
		}
		server(mc, "tick sprint " + ticks);
		fastForward = true;
		try {
			long until = System.currentTimeMillis() + SPRINT_WAIT_MS;
			while (onServer(server, () -> server.tickRateManager().isSprinting()) && System.currentTimeMillis() < until) {
				sleep(20);
			}
			if (onServer(server, () -> server.tickRateManager().isSprinting())) {
				Reel.LOG.warn("[welkin] the settle's {} ticks did not finish in {} s; stopped", ticks, SPRINT_WAIT_MS / 1000);
				server(mc, "tick sprint stop");
			}
		} finally {
			fastForward = false;
		}
		hold(mc);
	}

	/**
	 * After a settle: the overworld's game time back to the one the world is held at (the clouds, the wind and Gloam's
	 * fronts read it), sent to the client, and the clock on the shot's tick again (Gloam reads it for the season and the
	 * moon).
	 */
	private static void rewind(Minecraft mc, long time) {
		IntegratedServer server = mc.getSingleplayerServer();
		long held = heldGameTime;
		if (held != Long.MIN_VALUE) {
			onServer(server, () -> {
				server.getWorldData().overworldData().setGameTime(held);
				server.forceGameTimeSynchronization();
				return true;
			});
		}
		server(mc, "time set " + time);
	}

	/**
	 * Gloam's wet ground: the wetness Gloam gives the pack follows the rain on client ticks, frozen or not: it climbs to
	 * the rain in a minute (1200 ticks) and dries after it over most of a day (some 12000 ticks). While it is off the
	 * rain, the client runs ten ticks a frame (the world stays frozen: only the client's own state moves) until it gets
	 * there, at most 40000 ticks or three minutes. Nothing without Gloam. Returns the client ticks it took.
	 */
	private static long soak() {
		Method get, here, rain, wetness;
		try {
			Class<?> atmosphere = Class.forName("dev.voidmc.gloam.air.Atmosphere");
			get = atmosphere.getMethod("get");
			here = atmosphere.getMethod("here");
			Class<?> sample = Class.forName("dev.voidmc.gloam.air.AirSample");
			rain = sample.getMethod("rain");
			wetness = sample.getMethod("wetness");
		} catch (ReflectiveOperationException | LinkageError e) {
			return 0;
		}
		Supplier<String> dry = () -> {
			try {
				Object air = here.invoke(get.invoke(null));
				// short of the rain (soaking) or above it (drying); at the rain Gloam's wetness flips by one drying step
				// a tick (it dries below the rain and climbs back), which is as still as it gets
				return String.valueOf(Math.abs((float) wetness.invoke(air) - (float) rain.invoke(air)) > SOAKED);
			} catch (ReflectiveOperationException | RuntimeException e) {
				return "false";
			}
		};
		if (!"true".equals(ControlServer.onClient(dry))) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		long from = CLIENT_TICK.get(), until = System.currentTimeMillis() + 180_000;
		// a frozen server still keeps its 20 ticks a second, and the game test harness holds the client to it: the rate
		// goes up while the client soaks (nothing in the world runs), and back after
		server(mc, "tick rate " + SOAK_RATE);
		fastForward = true;
		try {
			while ("true".equals(ControlServer.onClient(dry)) && CLIENT_TICK.get() - from < SOAK_LIMIT && System.currentTimeMillis() < until) {
				sleep(20);
			}
		} finally {
			fastForward = false;
			server(mc, "tick rate 20");
		}
		return CLIENT_TICK.get() - from;
	}

	/** Gloam's wetness of the ground at the camera (what the pack's wet surfaces read), or -1 without Gloam. */
	private static String gloamWetness() {
		try {
			Class<?> atmosphere = Class.forName("dev.voidmc.gloam.air.Atmosphere");
			Object air = atmosphere.getMethod("here").invoke(atmosphere.getMethod("get").invoke(null));
			return String.format(Locale.ROOT, "%.6f", (float) air.getClass().getMethod("wetness").invoke(air));
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return "-1";
		}
	}

	/** Waits until the client has ticked {@code n} more times (or n / 10 + 5 seconds have gone). */
	private static void waitClientTicks(int n) {
		long target = CLIENT_TICK.get() + n;
		long until = System.currentTimeMillis() + n * 100L + 5_000;
		while (CLIENT_TICK.get() < target && System.currentTimeMillis() < until) {
			sleep(10);
		}
	}

	private static long gameTime(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		return server == null ? Long.MIN_VALUE : onServer(server, () -> server.overworld().getGameTime());
	}

	private static <T> T onServer(IntegratedServer server, Supplier<T> task) {
		try {
			return server.submit(task).get(60, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("the server did not answer: " + e, e);
		}
	}

	/** Gloam's seasonal recolour finished (up to six seconds); nothing without Gloam. */
	private static void waitSeasons() {
		Method settled;
		try {
			settled = Class.forName("dev.voidmc.gloam.season.Seasons").getMethod("settled");
		} catch (ReflectiveOperationException | LinkageError e) {
			return;
		}
		long until = System.currentTimeMillis() + 6_000;
		while (System.currentTimeMillis() < until) {
			String done = ControlServer.onClient(() -> {
				try {
					return String.valueOf(settled.invoke(null));
				} catch (ReflectiveOperationException e) {
					return "true";
				}
			});
			if ("true".equals(done)) {
				return;
			}
			sleep(100);
		}
	}

	/** Runs commands on the integrated server as its console, quietly, and waits until they ran. */
	private static void server(Minecraft mc, String... commands) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null) {
			throw new IllegalArgumentException("not in single player");
		}
		try {
			server.submit(() -> {
				var source = server.createCommandSourceStack().withSuppressedOutput();
				for (String command : commands) {
					server.getCommands().performPrefixedCommand(source, command);
				}
			}).get(60, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException("server commands failed: " + e, e);
		}
	}

	private static Object iris(String method) {
		try {
			return Class.forName("net.irisshaders.iris.Iris").getMethod(method).invoke(null);
		} catch (ReflectiveOperationException | LinkageError e) {
			return null;
		}
	}

	private static String need(Map<String, String> q, String key) {
		String v = q.get(key);
		if (v == null || v.isEmpty()) {
			throw new IllegalArgumentException("missing ?" + key + "=");
		}
		return v;
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
