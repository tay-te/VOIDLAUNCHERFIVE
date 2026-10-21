package dev.voidmc.bestiary.gametest;

import dev.voidmc.fray.client.Animator;
import dev.voidmc.fray.client.ClientClips;
import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.motion.FrayHandshake;
import dev.voidmc.fray.motion.MotionServer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Two clients on one dedicated server (tools/duo.sh; Mettle's audit pattern). Role A ({@code -Dfray.duo.role=A}, run by
 * runClientGameTest) hosts Fabric's in-process dedicated server on port 25612 and joins it; role B is a second client
 * JVM. Marker files in {@code -Dfray.duo.dir} step them through:
 * <ol>
 * <li>B tries to join with a claw that differs by one keyframe: refused at login; then joins with the shipped clips.</li>
 * <li>{@code -Dfray.duo.lag=150} on B: every packet B receives is held 150 ms (one way) by a handler in its netty
 * pipeline.</li>
 * <li>The server starts the zombie's claw. Both clients trace, every client tick, the wall clock and how far into the
 * clip they are; both take a screenshot at the same wall-clock moment.</li>
 * <li>B swings (prediction plus request); A traces and films B's swing as the server broadcast it.</li>
 * </ol>
 * A compares the traces: the clip time each client shows at the same moment.
 */
public class MotionDuo implements FabricClientGameTest {
	static final String ROLE = System.getProperty("fray.duo.role", "");
	static final Path DIR = Path.of(System.getProperty("fray.duo.dir", "duo"));
	static final int LAG = Integer.getInteger("fray.duo.lag", 0);
	static final int PORT = 25612;
	static final int Y = Stage.Y;

	@Override
	public void runTest(ClientGameTestContext c) {
		if (ROLE.isEmpty()) {
			return;
		}
		try {
			Files.createDirectories(DIR);
			Stage.log("duo role " + ROLE + " start, lag " + LAG + " ms");
			c.getInput().resizeWindow(1280, 720);
			c.runOnClient(mc -> {
				mc.options.guiScale().set(2);
				mc.resizeGui();
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(120);
				mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			});
			if (ROLE.equals("A")) {
				host(c);
			} else {
				guest(c);
			}
			Stage.log("duo role " + ROLE + " end; problems " + Stage.PROBLEMS);
			Stage.writeReport("duo-" + ROLE);
			mark("done");
			if (!Stage.PROBLEMS.isEmpty()) {
				throw new AssertionError("duo problems: " + Stage.PROBLEMS);
			}
		} catch (Throwable t) {
			StringWriter w = new StringWriter();
			t.printStackTrace(new PrintWriter(w));
			Stage.log("FAIL " + w);
			Stage.writeReport("duo-" + ROLE);
			mark("failed");
			if (t instanceof RuntimeException r) {
				throw r;
			}
			if (t instanceof Error e) {
				throw e;
			}
			throw new RuntimeException(t);
		}
	}

	// ------------------------------------------------------------------ host

	private static void host(ClientGameTestContext c) throws IOException {
		Properties props = new Properties();
		props.setProperty("server-port", String.valueOf(PORT));
		props.setProperty("online-mode", "false");
		props.setProperty("white-list", "false");
		props.setProperty("enforce-whitelist", "false");
		props.setProperty("max-players", "8");
		props.setProperty("view-distance", "6");
		props.setProperty("simulation-distance", "4");
		props.setProperty("pause-when-empty-seconds", "0");
		props.setProperty("spawn-protection", "0");
		props.setProperty("level-seed", "20261021");
		try (TestDedicatedServerContext server = c.worldBuilder().createServer(props)) {
			Stage.log("dedicated server up: dedicated " + server.computeOnServer(s -> s.isDedicatedServer()) + ", clip set "
				+ FrayHandshake.shortHash(MotionServer.clipSetHash()));
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				Stage st = new Stage(c, server, "duoA");
				server.runCommand("execute as @a run tp @s 0 " + Y + " 0");
				c.waitTicks(20);
				st.build();
				connection.waitForChunksRender();
				server.runCommand("setworldspawn 12 " + Y + " 12");
				server.runCommand("gamerule spawn_radius 0");
				int zombie = st.zombie(0.5, 0.5, 0, false);
				mark("server");
				// Late join: the zombie claws without a break until B is in, so B arrives mid-action and must be told the
				// running clip with its real start tick (EntityTrackingEvents.START_TRACKING).
				long end = System.currentTimeMillis() + 300_000;
				int replays = 0;
				while (!Files.exists(DIR.resolve("B.joined"))) {
					if (System.currentTimeMillis() > end || Files.exists(DIR.resolve("B.failed"))) {
						throw new AssertionError("B never joined");
					}
					if (server.computeOnServer(sv -> MotionServer.running(sv.overworld().getEntity(zombie)) == null)) {
						st.play(zombie, MotionShowcase.CLAW);
						replays++;
					}
					c.waitTick();
				}
				Stage.log("the claw was replayed " + replays + " times while B joined");
				Stage.log("B's first try was refused by the server with: " + FrayHandshake.lastRefusal);
				Stage.expect(FrayHandshake.lastRefusal.contains("clip set"), "the server refused B's mismatched clip set");
				c.waitTicks(30);
				int pig = st.dummy(0.5, 1.7);
				String me = c.computeOnClient(mc -> mc.getUser().getName());
				server.runCommand("tp " + me + " -3.0 " + Y + " 4.0 -143 18");
				server.runCommand("tp Bob 4.0 " + Y + " 4.0 143 18");
				c.waitTicks(40);
				st.playerCam(net.minecraft.client.CameraType.FIRST_PERSON);
				c.runOnClient(mc -> {
					if (!mc.gui.hud.isHidden()) {
						mc.gui.hud.toggle();
					}
				});
				mark("ready");
				await("B", "ready", 120, c);

				int bob = c.computeOnClient(mc -> {
					for (Player p : mc.level.players()) {
						if (p.getName().getString().equals("Bob")) {
							return p.getId();
						}
					}
					return -1;
				});
				Stage.expect(bob >= 0, "A sees B");
				// Round 0 without latency, round 1 with B's injected lag (-Dfray.duo.lag on B).
				for (int round = 0; round < (LAG > 0 ? 2 : 1); round++) {
					int lag = round == 0 ? 0 : LAG;
					String sfx = round == 0 ? "" : "_lag" + LAG;
					// The claw, started by the server; both clients trace it and shoot at one wall-clock moment.
					mark("trace_start" + sfx);
					c.waitTicks(10);
					MotionServer.HITS.clear();
					long wall0 = System.currentTimeMillis();
					long start = st.serverTime();
					st.play(zombie, MotionShowcase.CLAW);
					write("shoot_claw" + sfx, String.valueOf(wall0 + 640));
					List<String> trace = trace(c, zombie, 70, DIR.resolve("A.shoot_claw" + sfx), "duo_A_claw" + sfx);
					Files.write(DIR.resolve("trace-A-claw" + sfx + ".txt"), trace, StandardCharsets.UTF_8);
					Stage.log("claw" + sfx + " started at server tick " + start + " (wall " + wall0 + "); server hits: "
						+ MotionServer.HITS.stream().map(h -> "tick " + h.tick() + " " + h.phase()).toList());
					Stage.expect(MotionServer.HITS.stream().anyMatch(h -> h.target() == pig && h.tick() >= 11 && h.tick() <= 13),
						"claw" + sfx + ": the dedicated server's claw hit the dummy in its strike window");
					await("B", "trace_claw" + sfx, 120, c);
					// B sees the server's claw late by its lag: its clock and the packet are both that late.
					compare("claw" + sfx, trace, Files.readAllLines(DIR.resolve("trace-B-claw" + sfx + ".txt")), lag / 50.0, lag);

					// B swings; A sees it through the server's broadcast.
					long swingAt = System.currentTimeMillis() + 300;
					write("swing_at" + sfx, String.valueOf(swingAt));
					write("shoot_swing" + sfx, String.valueOf(swingAt + 600));
					List<String> swing = trace(c, bob, 50, DIR.resolve("A.shoot_swing" + sfx), "duo_A_sees_B_swing" + sfx);
					Files.write(DIR.resolve("trace-A-swing" + sfx + ".txt"), swing, StandardCharsets.UTF_8);
					await("B", "trace_swing" + sfx, 120, c);
					// B predicted its own swing on a clock late by the lag; the server started it when the request arrived: the two
					// cancel, so A and B should agree with no offset.
					compare("swing" + sfx, swing, Files.readAllLines(DIR.resolve("trace-B-swing" + sfx + ".txt")), 0, lag);
					if (round == 0 && LAG > 0) {
						mark("lag_now");
						await("B", "lagged", 60, c);
						// Let B's clock settle on the late time packets (sent every second).
						c.waitTicks(60);
					}
				}
				Stage.log("A's motion log: " + ClientMotion.LOG);
				mark("finish");
				await("B", "done", 120, c);
			}
		}
	}

	// ------------------------------------------------------------------ guest

	private static void guest(ClientGameTestContext c) throws IOException {
		waitFile(DIR.resolve("A.server"), 600, c);
		// 1. With a claw that differs by one keyframe: refused at login.
		byte[] shipped = ClientClips.shippedBytes(MotionShowcase.CLAW);
		String modified = new String(shipped, StandardCharsets.UTF_8).replaceFirst("-172", "-150");
		c.runOnClient(mc -> ClientClips.load(Map.of(MotionShowcase.CLAW, modified.getBytes(StandardCharsets.UTF_8))));
		connect(c);
		c.waitFor(mc -> mc.gui.screen() instanceof DisconnectedScreen, 1200);
		String reason = c.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
		c.waitTicks(5);
		c.takeScreenshot("fray_duo_B_refused");
		Stage.log("B refused: " + reason);
		Stage.expect(reason.contains("don't match"), "B is refused with a clear message");
		// 2. The shipped clips: in.
		c.runOnClient(mc -> ClientClips.load(null));
		c.setScreen(TitleScreen::new);
		c.waitTicks(10);
		connect(c);
		c.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 1200);
		c.waitTicks(40);
		// Late join: the zombie was mid-claw when B arrived; B must have been sent it with its real start tick.
		List<String> plays = ClientMotion.LOG.stream().filter(l -> l.startsWith("play")).toList();
		Stage.log("B's first motion packets after joining: " + plays.stream().limit(3).toList());
		boolean late = plays.stream().anyMatch(l -> {
			String[] p = l.split(" ");
			return Double.parseDouble(p[p.length - 1]) > Double.parseDouble(p[p.length - 3]);
		});
		Stage.expect(late, "late join: B received the running claw with a start tick in its past");
		mark("joined");
		await("A", "ready", 300, c);
		c.waitTicks(20);
		c.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		mark("ready");
		int zombie = -1;
		for (int round = 0; round < (LAG > 0 ? 2 : 1); round++) {
			String sfx = round == 0 ? "" : "_lag" + LAG;
			await("A", "trace_start" + sfx, 120, c);
			if (zombie < 0) {
				zombie = c.computeOnClient(mc -> {
					for (Entity e : mc.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.monster.zombie.Zombie) {
							return e.getId();
						}
					}
					return -1;
				});
			}
			// Trace from now: the claw starts within half a second; the shot time arrives in A.shoot_claw meanwhile.
			List<String> trace = trace(c, zombie, 80, DIR.resolve("A.shoot_claw" + sfx), "duo_B_claw" + sfx);
			Files.write(DIR.resolve("trace-B-claw" + sfx + ".txt"), trace, StandardCharsets.UTF_8);
			mark("trace_claw" + sfx);
			waitFile(DIR.resolve("A.swing_at" + sfx), 200, c);
			long swingAt = Long.parseLong(Files.readString(DIR.resolve("A.swing_at" + sfx)).trim());
			while (System.currentTimeMillis() < swingAt) {
				c.waitTick();
			}
			c.runOnClient(mc -> ClientMotion.startLocal(MotionShowcase.SWING));
			int me = c.computeOnClient(mc -> mc.player.getId());
			List<String> swing = trace(c, me, 48, null, null);
			Files.write(DIR.resolve("trace-B-swing" + sfx + ".txt"), swing, StandardCharsets.UTF_8);
			mark("trace_swing" + sfx);
			if (round == 0 && LAG > 0) {
				await("A", "lag_now", 120, c);
				lag(c, LAG);
				mark("lagged");
			}
		}
		Stage.log("B's motion log: " + ClientMotion.LOG);
		mark("trace_swing");
		await("A", "finish", 200, c);
		c.runOnClient(mc -> mc.disconnectFromWorld(Component.literal("duo done")));
		c.waitTicks(20);
		c.setScreen(TitleScreen::new);
	}

	private static void connect(ClientGameTestContext c) {
		c.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("localhost:" + PORT),
			new ServerData("duo", "localhost:" + PORT, ServerData.Type.OTHER), false, null));
	}

	/** Holds every packet this client receives for {@code ms} (one-way latency), in order. */
	private static void lag(ClientGameTestContext c, int ms) {
		c.runOnClient(mc -> {
			try {
				Connection conn = mc.getConnection().getConnection();
				Field f = Connection.class.getDeclaredField("channel");
				f.setAccessible(true);
				Channel ch = (Channel) f.get(conn);
				ch.pipeline().addBefore("packet_handler", "fray_lag", new ChannelInboundHandlerAdapter() {
					@Override
					public void channelRead(ChannelHandlerContext ctx, Object msg) {
						ctx.channel().eventLoop().schedule(() -> ctx.fireChannelRead(msg), ms, TimeUnit.MILLISECONDS);
					}
				});
				Stage.log("lag: " + ms + " ms on every received packet; pipeline " + ch.pipeline().names());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
	}

	// ------------------------------------------------------------------ traces

	/**
	 * Every client tick for {@code ticks} ticks: "wallMs clientGameTime elapsedTicks" for the entity's ACTION layer (NaN
	 * when no action). Takes a screenshot at the wall time written in {@code shootFile} once it appears (if not null).
	 */
	private static List<String> trace(ClientGameTestContext c, int entity, int ticks, Path shootFile, String shot) {
		List<String> out = new ArrayList<>();
		long shootAt = -1;
		boolean shotTaken = shootFile == null;
		for (int i = 0; i < ticks; i++) {
			c.waitTick();
			String row = c.computeOnClient(mc -> {
				Animator a = ClientMotion.get(entity);
				double now = ClientMotion.now(0);
				double e = a == null || a.clip(Animator.ACTION) == null ? Double.NaN : now - a.start(Animator.ACTION);
				return String.format(Locale.ROOT, "%d %.0f %.2f", System.currentTimeMillis(), now, e);
			});
			out.add(row);
			if (!shotTaken && shootAt < 0 && Files.exists(shootFile)) {
				try {
					shootAt = Long.parseLong(Files.readString(shootFile).trim());
				} catch (IOException | NumberFormatException e) {
					shootAt = -1;
				}
			}
			if (!shotTaken && shootAt > 0 && System.currentTimeMillis() >= shootAt) {
				c.takeScreenshot("fray_" + shot);
				out.add("# screenshot at " + System.currentTimeMillis() + " (asked for " + shootAt + ")");
				shotTaken = true;
			}
		}
		return out;
	}

	/** The clip time each client showed at the same wall-clock moments: A minus B, in ticks. */
	private static void compare(String what, List<String> a, List<String> b, double expected, int lag) {
		List<double[]> pa = parse(a), pb = parse(b);
		List<Double> diffs = new ArrayList<>();
		for (double[] r : pa) {
			double eb = interpolate(pb, r[0]);
			if (!Double.isNaN(r[2]) && !Double.isNaN(eb) && r[2] > 2 && eb > 2) {
				diffs.add(r[2] - eb);
			}
		}
		double firstA = pa.stream().filter(r -> !Double.isNaN(r[2])).mapToDouble(r -> r[2]).findFirst().orElse(Double.NaN);
		double firstB = pb.stream().filter(r -> !Double.isNaN(r[2])).mapToDouble(r -> r[2]).findFirst().orElse(Double.NaN);
		long seenA = pa.stream().filter(r -> !Double.isNaN(r[2])).mapToLong(r -> (long) r[0]).findFirst().orElse(-1);
		long seenB = pb.stream().filter(r -> !Double.isNaN(r[2])).mapToLong(r -> (long) r[0]).findFirst().orElse(-1);
		double mean = diffs.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
		double max = diffs.stream().mapToDouble(d -> Math.abs(d - mean)).max().orElse(Double.NaN);
		Stage.log(String.format(Locale.ROOT, "%s sync: A first showed it at wall %d (clip tick %.2f), B at %d (clip tick %.2f): B %d ms later; at the same moments A was %.2f ticks"
			+ " (%.0f ms) ahead of B (spread +-%.2f, %d samples; B's injected lag %d ms)", what, seenA, firstA, seenB, firstB, seenB - seenA, mean, mean * 50, max,
			diffs.size(), lag));
		Stage.expect(!diffs.isEmpty(), what + ": both clients played it");
		Stage.expect(Math.abs(mean - expected) <= 2.0, what + String.format(Locale.ROOT, ": the clients agree to within 2 ticks of the expected %.1f", expected));
		Stage.expect(firstB < 3 && firstA < 3, what + ": both clients started from the clip's first ticks (no tell skipped)");
	}

	private static List<double[]> parse(List<String> lines) {
		List<double[]> out = new ArrayList<>();
		for (String l : lines) {
			if (l.startsWith("#")) {
				continue;
			}
			String[] p = l.trim().split(" ");
			out.add(new double[] {Double.parseDouble(p[0]), Double.parseDouble(p[1]), p[2].equals("NaN") ? Double.NaN : Double.parseDouble(p[2])});
		}
		return out;
	}

	private static double interpolate(List<double[]> rows, double wall) {
		for (int i = 1; i < rows.size(); i++) {
			double[] r0 = rows.get(i - 1), r1 = rows.get(i);
			if (r0[0] <= wall && r1[0] >= wall) {
				if (Double.isNaN(r0[2]) || Double.isNaN(r1[2])) {
					return Double.NaN;
				}
				double f = r1[0] == r0[0] ? 0 : (wall - r0[0]) / (r1[0] - r0[0]);
				return r0[2] + (r1[2] - r0[2]) * f;
			}
		}
		return Double.NaN;
	}

	// ------------------------------------------------------------------ markers

	static void mark(String name) {
		try {
			Files.createDirectories(DIR);
			Files.writeString(DIR.resolve(ROLE + "." + name), String.valueOf(System.currentTimeMillis()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static void write(String name, String value) throws IOException {
		Files.writeString(DIR.resolve(ROLE + "." + name), value);
	}

	static void await(String role, String name, int seconds, ClientGameTestContext c) {
		waitFile(DIR.resolve(role + "." + name), seconds, c);
		if (Files.exists(DIR.resolve(role + ".failed"))) {
			throw new AssertionError(role + " failed");
		}
	}

	static void waitFile(Path p, int seconds, ClientGameTestContext c) {
		long end = System.currentTimeMillis() + seconds * 1000L;
		while (!Files.exists(p)) {
			if (System.currentTimeMillis() > end) {
				throw new AssertionError("timed out waiting for " + p);
			}
			if (Files.exists(DIR.resolve((ROLE.equals("A") ? "B" : "A") + ".failed"))) {
				throw new AssertionError("the other side failed");
			}
			c.waitTicks(2);
		}
	}
}
