package dev.voidmc.reel.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.voidmc.reel.Reel;
import dev.voidmc.reel.math.Vec3;
import dev.voidmc.reel.shot.PathRig;
import dev.voidmc.reel.shot.Shot;
import dev.voidmc.reel.shot.ShotException;
import dev.voidmc.reel.shot.ShotReport;
import dev.voidmc.reel.math.Easing;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The director's desk: a small HTTP server on 127.0.0.1 only (never the network), on with -Dreel.control=&lt;port&gt;.
 * tools/reel.sh wraps it. Every answer is one JSON object.
 *
 * <pre>
 * GET  /status                         what the director is doing
 * GET  /shots                          the shot files
 * GET  /check?shot=NAME                timing and warnings for a shot, without filming it
 * POST /play?shot=NAME                 real-time preview
 * POST /render?shot=NAME[&amp;samples=8&amp;w=1920&amp;h=1080&amp;encode=false]
 * POST /stills?shot=NAME[&amp;count=8 | &amp;at=0,2.5,6][&amp;w=..&amp;h=..]
 * POST /snap?x=..&amp;y=..&amp;z=..&amp;yaw=..&amp;pitch=..[&amp;fov=70&amp;w=..&amp;h=..&amp;name=..]   one frame from anywhere
 * POST /stop
 * GET  /where                          where the player stands and looks
 * GET  /height?at=x,z;x,z;...          the ground (top solid or liquid block) at each point, loading land as needed
 * POST /terrain?x=..&amp;z=..&amp;radius=512&amp;step=2&amp;name=..   height grids around a point for Blender (renders/_terrain/NAME)
 * POST /export?x=..&amp;z=..&amp;radius=96&amp;y0=40&amp;y1=200&amp;oy=64&amp;name=..   the loaded blocks as a textured OBJ for Blender (renders/_export/NAME)
 * POST /cmd   (body: a server command) run on the integrated server where the player stands; answers its output
 *            ("locate structure #minecraft:village", "time set 23000")
 * POST /set?syncBody=true&jitter=shot&supersample=2   live filming settings (GET /set shows them)
 * POST /pack?TAA_DEFINE=0              sets shader pack options and reloads the pack
 * POST /quit                           closes the studio
 * </pre>
 */
public final class ControlServer {
	private static volatile boolean quit;

	private ControlServer() {
	}

	/** Whether POST /quit has asked the studio to close. */
	public static boolean quitRequested() {
		return quit;
	}

	public static void start(int port) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		server.setExecutor(Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "Reel control");
			t.setDaemon(true);
			return t;
		}));
		server.createContext("/", ControlServer::handle);
		server.start();
		Reel.LOG.info("[reel] control on http://127.0.0.1:{}/", port);
	}

	private static void handle(HttpExchange exchange) throws IOException {
		String reply;
		int code = 200;
		try {
			reply = route(exchange.getRequestURI().getPath(), query(exchange.getRequestURI().getRawQuery()),
				new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim());
		} catch (ShotException | IllegalArgumentException | IOException e) {
			code = 400;
			reply = error(e.getMessage());
		} catch (RuntimeException e) {
			code = 500;
			reply = error(e.toString());
			Reel.LOG.error("[reel] control request failed", e);
		}
		byte[] body = reply.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(code, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private static String route(String path, Map<String, String> q, String body) throws IOException {
		Director director = Director.get();
		return switch (path) {
			case "/status" -> onClient(director::statusJson);
			case "/shots" -> "{\"dir\":\"" + Report.escape(Reel.shots().toString()) + "\",\"shots\":["
				+ String.join(",", Reel.shotNames().stream().map(n -> "\"" + n + "\"").toList()) + "]}";
			case "/check" -> "{\"report\":\"" + Report.escape(ShotReport.of(Reel.shot(need(q, "shot"))).text()) + "\"}";
			case "/play" -> {
				Shot shot = Reel.shot(need(q, "shot"));
				yield onClient(() -> {
					director.play(Minecraft.getInstance(), shot);
					return director.statusJson();
				});
			}
			case "/render" -> {
				Shot shot = Reel.shot(need(q, "shot"));
				RenderJob job = RenderJob.movie(shot, Reel.renders(), integer(q, "samples"), integer(q, "w"), integer(q, "h"), !"false".equals(q.get("encode")));
				yield start(director, job);
			}
			case "/stills" -> {
				Shot shot = Reel.shot(need(q, "shot"));
				RenderJob job = q.containsKey("at")
					? RenderJob.at(shot, Reel.renders(), Arrays.stream(q.get("at").split(",")).mapToDouble(Double::parseDouble).toArray(), integer(q, "w"), integer(q, "h"))
					: RenderJob.stills(shot, Reel.renders(), q.containsKey("count") ? Integer.parseInt(q.get("count")) : 8, integer(q, "w"), integer(q, "h"));
				yield start(director, job);
			}
			case "/snap" -> {
				Vec3 pos = new Vec3(number(q, "x"), number(q, "y"), number(q, "z"));
				double fov = q.containsKey("fov") ? number(q, "fov") : 70;
				PathRig rig = new PathRig(List.of(new PathRig.Key(pos, null, number(q, "yaw"), number(q, "pitch"), null, fov, null)), 1, PathRig.Timing.EVEN,
					Easing.LINEAR, null, false, fov);
				String name = "_snap-" + q.getOrDefault("name", "view");
				Shot shot = new Shot(name, 60, 1, 1920, 1080, Shot.World.UNCHANGED, rig, null, Shot.Render.DEFAULT);
				yield start(director, RenderJob.at(shot, Reel.renders(), new double[] {0}, integer(q, "w"), integer(q, "h")));
			}
			case "/stop" -> onClient(() -> {
				director.stop(Minecraft.getInstance());
				return director.statusJson();
			});
			case "/where" -> onClient(() -> {
				var player = Minecraft.getInstance().player;
				if (player == null) {
					return error("no world loaded");
				}
				var eye = player.getEyePosition();
				return String.format(Locale.ROOT, "{\"eye\":[%.2f,%.2f,%.2f],\"yaw\":%.1f,\"pitch\":%.1f}", eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
			});
			case "/height" -> {
				IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
				if (server == null) {
					throw new IllegalArgumentException("not in single player");
				}
				String[] points = need(q, "at").split(";");
				CompletableFuture<String> heights = server.submit(() -> {
					ServerLevel level = server.getLevel(Minecraft.getInstance().level.dimension());
					StringBuilder b = new StringBuilder("{\"heights\":[");
					for (int i = 0; i < points.length; i++) {
						String[] xz = points[i].split(",");
						int x = (int) Math.floor(Double.parseDouble(xz[0].trim()));
						int z = (int) Math.floor(Double.parseDouble(xz[1].trim()));
						int y = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
						int floor = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
						b.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "{\"x\":%d,\"z\":%d,\"top\":%d,\"ground\":%d}", x, z, y + 1, floor + 1));
					}
					return b.append("]}").toString();
				});
				try {
					yield heights.get(120, TimeUnit.SECONDS);
				} catch (Exception e) {
					yield error("height lookup failed: " + e);
				}
			}
			case "/terrain" -> {
				IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
				if (server == null) {
					throw new IllegalArgumentException("not in single player");
				}
				int x = (int) Math.floor(number(q, "x"));
				int z = (int) Math.floor(number(q, "z"));
				int radius = q.containsKey("radius") ? Integer.parseInt(q.get("radius")) : 512;
				int step = q.containsKey("step") ? Integer.parseInt(q.get("step")) : 2;
				if (radius < 1 || step < 1) {
					throw new IllegalArgumentException("radius and step must be 1 or more");
				}
				String name = q.getOrDefault("name", x + "_" + z);
				var dimension = Minecraft.getInstance().level.dimension();
				CompletableFuture<String> grid = server.submit(() -> {
					try {
						return TerrainGrab.grab(server.getLevel(dimension), name, x, z, radius, step);
					} catch (Exception e) {
						return error("terrain failed: " + e);
					}
				});
				try {
					yield grid.get(300, TimeUnit.SECONDS);
				} catch (Exception e) {
					yield error("terrain failed: " + e);
				}
			}
			case "/export" -> {
				int x = (int) Math.floor(number(q, "x"));
				int z = (int) Math.floor(number(q, "z"));
				int radius = q.containsKey("radius") ? Integer.parseInt(q.get("radius")) : 96;
				int y0 = q.containsKey("y0") ? Integer.parseInt(q.get("y0")) : 40;
				int y1 = q.containsKey("y1") ? Integer.parseInt(q.get("y1")) : 200;
				int oy = q.containsKey("oy") ? Integer.parseInt(q.get("oy")) : 64;
				String name = q.getOrDefault("name", x + "_" + z);
				CompletableFuture<String> done = new CompletableFuture<>();
				Minecraft.getInstance().execute(() -> {
					try {
						done.complete(WorldExport.export(Minecraft.getInstance(), name, x, z, radius, y0, y1, oy));
					} catch (Exception e) {
						done.complete(error("export failed: " + e));
					}
				});
				try {
					yield done.get(900, TimeUnit.SECONDS);
				} catch (Exception e) {
					yield error("export failed: " + e);
				}
			}
			case "/cmd" -> {
				if (body.isEmpty()) {
					throw new IllegalArgumentException("send the command as the request body");
				}
				IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
				if (server == null) {
					throw new IllegalArgumentException("not in single player");
				}
				String command = body.startsWith("/") ? body.substring(1) : body;
				var eye = Minecraft.getInstance().player.getEyePosition();
				CompletableFuture<String> output = server.submit(() -> {
					List<String> lines = new java.util.ArrayList<>();
					CommandSource capture = new CommandSource() {
						@Override
						public void sendSystemMessage(Component message) {
							lines.add(message.getString());
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
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture).withPosition(eye), command);
					return "{\"ran\":\"" + Report.escape(command) + "\",\"output\":[" + String.join(",", lines.stream().map(l -> "\"" + Report.escape(l) + "\"").toList()) + "]}";
				});
				try {
					yield output.get(120, TimeUnit.SECONDS);
				} catch (Exception e) {
					yield error("command failed: " + e);
				}
			}
			case "/set" -> {
				if (q.isEmpty()) {
					yield Tuning.describe();
				}
				String out = Tuning.describe();
				for (var e : q.entrySet()) {
					out = Tuning.set(e.getKey(), e.getValue());
				}
				yield out;
			}
			case "/pack" -> {
				// shader pack options, written to the pack's settings file and reloaded: /pack?TAA_DEFINE=0
				yield onClient(() -> PackOptions.apply(q));
			}
			case "/quit" -> {
				quit = true;
				yield "{\"quit\":true}";
			}
			default -> throw new IllegalArgumentException("unknown path " + path + " (status, shots, check, play, render, stills, snap, stop, where, height, terrain, export, cmd, quit)");
		};
	}

	private static String start(Director director, RenderJob job) {
		return onClient(() -> {
			director.start(Minecraft.getInstance(), job);
			return director.statusJson();
		});
	}

	private static String onClient(Supplier<String> task) {
		CompletableFuture<String> result = new CompletableFuture<>();
		Minecraft.getInstance().execute(() -> {
			try {
				result.complete(task.get());
			} catch (RuntimeException e) {
				result.completeExceptionally(e);
			}
		});
		try {
			return result.get(30, TimeUnit.SECONDS);
		} catch (Exception e) {
			return error("the client did not answer: " + e);
		}
	}

	private static Map<String, String> query(String raw) {
		Map<String, String> q = new HashMap<>();
		if (raw == null) {
			return q;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			q.put(key, eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
		}
		return q;
	}

	private static String need(Map<String, String> q, String key) {
		String v = q.get(key);
		if (v == null || v.isEmpty()) {
			throw new IllegalArgumentException("missing ?" + key + "=");
		}
		return v;
	}

	private static double number(Map<String, String> q, String key) {
		return Double.parseDouble(need(q, key));
	}

	private static Integer integer(Map<String, String> q, String key) {
		return q.containsKey(key) ? Integer.valueOf(q.get(key)) : null;
	}

	private static String error(String message) {
		return "{\"error\":\"" + Report.escape(String.valueOf(message)) + "\"}";
	}
}
