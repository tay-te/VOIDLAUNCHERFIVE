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
 * POST /shoot?tp=..&amp;time=..[&amp;weather=clear&amp;fov=70&amp;lens=..&amp;w=1600&amp;h=900&amp;name=..&amp;out=..&amp;settle=ms]
 *                              time set, weather, the tp command as given (y is the feet, as the bench's), a settle, then
 *                              one frame from where tp put the eye (Reel's snap: prefetch, near chunks and far tiles
 *                              waited for); answers when the PNG is written, with its path and the timings.
 *                              fov is the FOV option the bench had; the frame is taken at what the game drew with it,
 *                              fov x 1.1 (a spectator flies, and flying widens the view by 10% at full FOV effects),
 *                              unless lens= gives the exact vertical FOV.
 * </pre>
 *
 * <p>Holding: between shots the world's ticks are frozen (tick freeze), so the game time, and with it the clouds, the
 * wind and the sway, stand still: two frames of one view before and after a shader edit differ only by the edit. The
 * day's clock is set by each shot. POST /hold?on=false lets the world run as the bench's did.
 */
public final class WelkinDesk {
	private static volatile String state = "starting";
	private static final AtomicInteger RELOADS = new AtomicInteger();
	private static final Object JOB = new Object();
	private static final long MOVED_SETTLE_MS = 3_000;
	private static final long STILL_SETTLE_MS = 1_000;
	private static volatile boolean hold = !"false".equals(System.getProperty("welkin.hold"));

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
				if (q.containsKey("on")) {
					hold = Boolean.parseBoolean(q.get("on"));
				}
				hold(Minecraft.getInstance());
				yield "{\"hold\":" + hold + "}";
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
			String[] parts = tp.trim().split("\\s+");
			double tx = Double.parseDouble(parts[2]), tz = Double.parseDouble(parts[4]);
			double moved = Double.parseDouble(ControlServer.onClient(() -> mc.player == null ? "1e9"
				: Double.toString(Math.hypot(mc.player.getX() - tx - 0.5, mc.player.getZ() - tz - 0.5))));
			server(mc, "time set " + time, "weather " + weather, tp);
			// a moment for the game to take the move and the clock (Gloam's sky, bridge and seasons follow on ticks)
			long settle = q.containsKey("settle") ? Long.parseLong(q.get("settle")) : moved > 32 ? MOVED_SETTLE_MS : STILL_SETTLE_MS;
			sleep(settle);
			waitSeasons();
			// the camera exactly where tp put the eye (tp centres whole x and z on the block)
			String[] eye = ControlServer.onClient(() -> {
				var e = mc.player.getEyePosition();
				return String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.3f,%.3f", e.x, e.y, e.z, mc.player.getYRot(), mc.player.getXRot());
			}).split(",");
			Vec3 pos = new Vec3(Double.parseDouble(eye[0]), Double.parseDouble(eye[1]), Double.parseDouble(eye[2]));
			double yaw = Double.parseDouble(eye[3]), pitch = Double.parseDouble(eye[4]);
			PathRig rig = new PathRig(List.of(new PathRig.Key(pos, null, yaw, pitch, null, lens, null)), 1, PathRig.Timing.EVEN, Easing.LINEAR, null, false, lens);
			Shot shot = new Shot("_snap-welkin", 60, 1, w, h, Shot.World.UNCHANGED, rig, null, Shot.Render.DEFAULT);
			RenderJob job = RenderJob.at(shot, Reel.renders(), new double[] {0}, w, h);
			long snapStart = System.nanoTime();
			ControlServer.onClient(() -> {
				Director.get().start(mc, job);
				return "";
			});
			long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
			while (Director.get().busy() && System.nanoTime() < deadline) {
				sleep(50);
			}
			if (Director.get().busy()) {
				ControlServer.onClient(() -> {
					Director.get().stop(mc);
					return "";
				});
				return ControlServer.error("the frame did not finish in 10 minutes");
			}
			hold(mc);
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
			Reel.LOG.info("[welkin] shot {} in {} s (settle {} ms, frame {} s): {}", name, String.format(Locale.ROOT, "%.1f", total), settle,
				String.format(Locale.ROOT, "%.1f", snap), dest);
			return String.format(Locale.ROOT, "{\"file\":\"%s\",\"seconds\":%.2f,\"settleMs\":%d,\"frameSeconds\":%.2f,\"eye\":[%s,%s,%s],\"yaw\":%s,\"pitch\":%s,\"lens\":%.1f,\"time\":%d,\"report\":%s}",
				Report.escape(dest.toString()), total, settle, snap, eye[0], eye[1], eye[2], eye[3], eye[4], lens, time, json.isEmpty() ? "null" : json);
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
