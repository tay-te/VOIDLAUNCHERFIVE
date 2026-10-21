package dev.voidmc.bestiary.studio;

import dev.voidmc.bestiary.anim.BestiaryAnim;
import dev.voidmc.bestiary.anim.GallowsKnight;
import dev.voidmc.bestiary.gametest.Stage;
import dev.voidmc.fray.client.Animator;
import dev.voidmc.fray.client.ClientClips;
import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.client.FrayStats;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.ClipFiles;
import dev.voidmc.fray.motion.ClipJson;
import dev.voidmc.fray.motion.MotionServer;
import dev.voidmc.fray.motion.Rig;
import dev.voidmc.fray.motion.Timeline;
import dev.voidmc.fray.motion.bake.MotionBake;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Motion Studio (ANIMATION.md §6): a live in-game preview for iterating clips in seconds. A persistent dev client
 * (a client game test that never ends on its own) with a control port and a file watcher: edit a {@code .motion.json}
 * (or a rig's {@code .bbmodel}) and the studio re-bakes it in memory, swaps the clips on both sides and replays what you
 * were looking at; send commands to film it. See docs/STUDIO.md and tools/studio.sh.
 *
 * <p>Run: {@code bash tools/studio.sh start [-Piris]} (takes a game slot until {@code studio.sh stop}). Commands:
 * {@code subject}, {@code play}, {@code freeze}, {@code live}, {@code cam}, {@code shot}, {@code strip}, {@code gif},
 * {@code onion}, {@code split}, {@code overlay}, {@code pack}, {@code reload}, {@code report}, {@code run}, {@code quit}
 * ({@code help} lists them with their arguments).
 */
public class MotionStudio implements FabricClientGameTest {
	static final boolean ON = Boolean.getBoolean("fray.studio");
	static final int PORT = Integer.getInteger("fray.studio.port", 25700);
	static final Path SRC = Path.of(System.getProperty("fray.studio.src", "../../../src/main")).toAbsolutePath().normalize();
	static final Path OUT = Path.of(System.getProperty("fray.studio.out", "../../studio")).toAbsolutePath().normalize();
	static final int Y = Stage.Y;
	/** Hides the hotbar and crosshair while the HUD stays on (first-person hands need the HUD). */
	static volatile boolean cleanHud = true;

	public static boolean active() {
		return ON;
	}

	@Override
	public void runTest(ClientGameTestContext c) {
		if (!ON) {
			return;
		}
		String[] size = System.getProperty("fray.studio.size", "1280x720").split("x");
		c.getInput().resizeWindow(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
		c.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(120);
			mc.options.bobView().set(false);
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			for (Identifier id : List.of(VanillaHudElements.HOTBAR, VanillaHudElements.CROSSHAIR, VanillaHudElements.EXPERIENCE_LEVEL,
				VanillaHudElements.INFO_BAR, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.CHAT, VanillaHudElements.MOB_EFFECTS,
				VanillaHudElements.OVERLAY_MESSAGE)) {
				try {
					HudElementRegistry.replaceElement(id, old -> (g, t) -> {
						if (!cleanHud) {
							old.extractRenderState(g, t);
						}
					});
				} catch (RuntimeException e) {
					System.out.println("[studio] couldn't wrap HUD element " + id + ": " + e);
				}
			}
		});
		try (TestSingleplayerContext world = c.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
			s.setSeed("20261021");
			s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
			s.setAllowCommands(true);
		}).create()) {
			Stage st = new Stage(c, world.getServer(), "studio");
			st.build();
			world.getConnection().waitForChunksRender();
			c.waitTicks(20);
			Session s = new Session(c, st);
			ClientTickEvents.END_CLIENT_TICK.register(mc -> s.overlayTick());
			s.run("subject mannequin; cam 3q");
			try (Control ctl = new Control(PORT, SRC.resolve("motion"), SRC.resolve("blockbench/rig"))) {
				Files.createDirectories(OUT);
				Files.writeString(OUT.resolve("ready"), "port " + PORT + "\n");
				System.out.println("[studio] ready on 127.0.0.1:" + PORT + ", watching " + SRC + ", pictures in " + OUT);
				while (!s.quit) {
					Control.Request r = ctl.poll();
					if (r != null) {
						String out;
						try {
							out = s.run(r.line());
						} catch (Throwable t) {
							out = "ERROR " + t;
							t.printStackTrace();
						}
						System.out.println("[studio] > " + r.line() + "\n" + out);
						r.reply().complete(out);
					} else if (ctl.changed()) {
						System.out.println("[studio] " + s.run("reload"));
					} else {
						c.waitTick();
					}
				}
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					Files.deleteIfExists(OUT.resolve("ready"));
				} catch (java.io.IOException e) {
					// closing
				}
			}
		}
	}

	/** One studio session: the subject, its clip, the camera and the pictures. */
	static final class Session {
		final ClientGameTestContext c;
		final Stage st;
		boolean quit;
		String kind = "mannequin";
		int subject = -1;
		/** A second subject for split: the mannequin twin of the local player. */
		int twin = -1;
		final List<Integer> ghosts = new ArrayList<>();
		final List<Double> ghostTicks = new ArrayList<>();
		Identifier clip;
		float speed = 1;
		double frozen = Double.NaN;
		String cam = "3q";
		boolean overlay;
		boolean travel = true;
		String lastReport = "";
		final Vec3 home = new Vec3(0.5, Y, 0.5);

		Session(ClientGameTestContext c, Stage st) {
			this.c = c;
			this.st = st;
		}

		String run(String line) {
			StringBuilder out = new StringBuilder();
			for (String cmd : line.split(";")) {
				cmd = cmd.trim();
				if (cmd.isEmpty()) {
					continue;
				}
				String[] a = cmd.split("\\s+");
				try {
					out.append(this.one(a)).append('\n');
				} catch (Exception e) {
					out.append("ERROR ").append(cmd).append(": ").append(e.getMessage()).append('\n');
				}
			}
			return out.toString().trim();
		}

		private String one(String[] a) throws Exception {
			return switch (a[0]) {
				case "help" -> HELP;
				case "status" -> this.status();
				case "subject" -> this.subject(a.length > 1 ? a[1] : "mannequin");
				case "play" -> this.play(Identifier.parse(a[1]), a.length > 2 ? Float.parseFloat(a[2]) : 1);
				case "freeze" -> this.freeze(Double.parseDouble(a[1]));
				case "live" -> this.live();
				case "cam" -> this.cam(Arrays.copyOfRange(a, 1, a.length));
				case "shot" -> this.shot(a.length > 1 ? a[1] : "shot").toString();
				case "strip" -> this.strip(a);
				case "gif" -> this.gif(a);
				case "onion" -> this.onion(a.length > 1 ? a[1] : "off");
				case "split" -> this.split(a);
				case "overlay" -> {
					this.overlay = a.length < 2 || a[1].equals("on");
					yield "overlay " + (this.overlay ? "on" : "off");
				}
				case "travel" -> {
					this.travel = a.length < 2 || a[1].equals("on");
					yield "travel " + (this.travel ? "on" : "off");
				}
				case "hud" -> {
					cleanHud = !(a.length < 2 || a[1].equals("on"));
					yield "hud " + (cleanHud ? "clean" : "full");
				}
				case "pack" -> this.pack(a.length > 1 ? a[1] : "off");
				case "reload" -> this.reload();
				case "report" -> this.lastReport.isEmpty() ? this.reload() : this.lastReport;
				case "run" -> {
					String command = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
					this.st.s.runCommand(command);
					yield "ran " + command;
				}
				case "wait" -> {
					this.c.waitTicks(Integer.parseInt(a[1]));
					yield "waited " + a[1];
				}
				case "perf" -> this.perf(a);
				case "quit" -> {
					this.quit = true;
					yield "bye";
				}
				default -> throw new IllegalArgumentException("unknown command (try help)");
			};
		}

		private String status() {
			return String.format(Locale.ROOT, "subject %s (%d), clip %s, %s, cam %s, overlay %s, onion %s, %d clips loaded, source %s", this.kind, this.subject,
				this.clip, Double.isNaN(this.frozen) ? "live" : "frozen at " + this.frozen, this.cam, this.overlay, this.ghostTicks, ClientClips.all().size(), SRC);
		}

		// ------------------------------------------------------------------ subjects

		private String subject(String k) {
			this.clearGhosts();
			this.st.clearCreatures();
			this.twin = -1;
			this.kind = k;
			this.subject = this.spawn(k, this.home, false);
			this.c.waitTicks(10);
			return "subject " + k + " (" + this.subject + ")";
		}

		/** Spawns a subject (or a bare ghost of one) at {@code at}, facing south (+Z). */
		int spawn(String k, Vec3 at, boolean ghost) {
			return switch (k) {
				case "zombie", "armoured_zombie" -> {
					int id = this.st.zombie(at.x, at.z, 0, k.equals("armoured_zombie") && !ghost);
					yield id;
				}
				case "knight" -> this.st.s.computeOnServer(server -> {
					ServerLevel level = server.overworld();
					GallowsKnight knight = BestiaryAnim.GALLOWS_KNIGHT.create(level, EntitySpawnReason.COMMAND);
					knight.snapTo(at.x, at.y, at.z, 0, 0);
					knight.setYHeadRot(0);
					knight.setYBodyRot(0);
					knight.setNoAi(true);
					knight.setPersistenceRequired();
					knight.setSilent(true);
					level.addFreshEntity(knight);
					return knight.getId();
				});
				case "player" -> {
					this.st.s.runCommand(String.format(Locale.ROOT, "tp @p %.2f %d %.2f 0 0", at.x, Y, at.z));
					for (String slot : new String[] {"armor.head iron_helmet", "armor.chest iron_chestplate", "armor.legs iron_leggings", "armor.feet iron_boots",
						"weapon.mainhand iron_sword"}) {
						this.st.s.runCommand("item replace entity @p " + slot.replace(" ", " with minecraft:"));
					}
					yield this.c.computeOnClient(mc -> mc.player.getId());
				}
				default -> ghost ? this.bareMannequin(at) : this.st.mannequin(at.x, at.z, 0);
			};
		}

		private int bareMannequin(Vec3 at) {
			return this.st.s.computeOnServer(server -> {
				ServerLevel level = server.overworld();
				var m = net.minecraft.world.entity.EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
				m.snapTo(at.x, at.y, at.z, 0, 0);
				m.setYHeadRot(0);
				m.setYBodyRot(0);
				level.addFreshEntity(m);
				return m.getId();
			});
		}

		private boolean isPlayer() {
			return this.kind.equals("player");
		}

		// ------------------------------------------------------------------ clips

		private String play(Identifier id, float speed) {
			if (ClientClips.get(id) == null) {
				throw new IllegalArgumentException("no clip " + id + " (have " + ClientClips.all().keySet().stream().filter(k -> k.getPath().contains(id.getPath().split("/")[0])).toList() + ")");
			}
			this.clip = id;
			this.speed = speed;
			this.frozen = Double.NaN;
			this.startOn(this.subject, this.isPlayer());
			if (this.twin >= 0) {
				this.startOn(this.twin, false);
			}
			for (int g : this.ghosts) {
				this.startOn(g, false);
			}
			this.c.waitTicks(2);
			if (!this.ghosts.isEmpty()) {
				this.applyGhosts(0);
			}
			Timeline t = MotionServer.timeline(id);
			return "playing " + id + (t == null ? "" : " (" + t.ticks + " ticks" + (t.hasPhases() ? ", active " + t.activeStart + "-" + (t.activeEnd - 1) : "") + ")");
		}

		private void startOn(int entity, boolean local) {
			if (local) {
				this.c.runOnClient(mc -> ClientMotion.startLocal(this.clip));
				return;
			}
			Identifier id = this.clip;
			float sp = this.speed;
			this.st.s.runOnServer(server -> {
				if (!(server.overworld().getEntity(entity) instanceof LivingEntity le) || !MotionServer.play(le, id, sp)) {
					throw new IllegalStateException("couldn't play " + id + " on " + entity);
				}
			});
		}

		/** Makes sure the subject's animator holds the clip (a finished clip is dropped unless frozen). */
		private void ensurePlaying(int entity, boolean local) {
			Identifier id = this.clip;
			boolean has = this.c.computeOnClient(mc -> {
				Animator a = ClientMotion.get(entity);
				return a != null && a.clip(Animator.ACTION) != null && a.clip(Animator.ACTION).id.equals(id);
			});
			if (!has) {
				this.startOn(entity, local);
				this.c.waitTicks(2);
			}
		}

		private String freeze(double t) {
			if (this.clip == null) {
				throw new IllegalStateException("play a clip first");
			}
			this.ensurePlaying(this.subject, this.isPlayer());
			if (this.twin >= 0) {
				this.ensurePlaying(this.twin, false);
			}
			this.frozen = t;
			this.setFrozen(this.subject, t);
			if (this.twin >= 0) {
				this.setFrozen(this.twin, t);
			}
			this.place(this.subject, this.home, t);
			this.applyGhosts(t);
			this.c.waitTicks(1);
			return String.format(Locale.ROOT, "frozen at %.2f (%s)", t, this.phaseAt(t));
		}

		private String phaseAt(double t) {
			Timeline tl = MotionServer.timeline(this.clip);
			if (tl == null) {
				return "?";
			}
			int k = (int) Math.floor(t);
			String ph = tl.phase(k);
			StringBuilder w = new StringBuilder(ph);
			for (Map.Entry<String, int[]> e : tl.windows.entrySet()) {
				if (k >= e.getValue()[0] && k <= e.getValue()[1]) {
					w.append(", ").append(e.getKey());
				}
			}
			if (tl.tellFrom >= 0 && k >= tl.tellFrom && k < tl.activeStart) {
				w.append(", tell ").append(tl.tellColour);
			}
			return w.toString();
		}

		private void setFrozen(int entity, double t) {
			this.c.runOnClient(mc -> {
				Animator a = ClientMotion.get(entity);
				if (a != null) {
					a.frozenElapsed = t;
				}
			});
		}

		private String live() {
			this.frozen = Double.NaN;
			this.setFrozen(this.subject, Double.NaN);
			if (this.twin >= 0) {
				this.setFrozen(this.twin, Double.NaN);
			}
			for (int g : this.ghosts) {
				this.setFrozen(g, Double.NaN);
			}
			this.place(this.subject, this.home, 0);
			return "live";
		}

		/** Root motion, shown: the subject moved along the clip's travel (the local player moves itself when live). */
		private void place(int entity, Vec3 base, double t) {
			Clip cl = this.clip == null ? null : ClientClips.get(this.clip);
			double d = 0, dir = 0;
			if (this.travel && cl != null && cl.motion != null && !Double.isNaN(t)) {
				double x = Math.clamp((t / 20.0 - cl.motion[0]) / (cl.motion[1] - cl.motion[0]), 0, 1);
				d = cl.motion[2] * (2 * x - x * x);
				dir = cl.motion[3];
			}
			// Facing south (yaw 0): forward is +Z, the entity's left (motion direction 90) is +X.
			double rad = Math.toRadians(dir);
			Vec3 at = base.add(Math.sin(rad) * d, 0, Math.cos(rad) * d);
			if (entity == this.subject && this.isPlayer()) {
				this.st.s.runCommand(String.format(Locale.ROOT, "tp @p %.3f %d %.3f 0 0", at.x, Y, at.z));
				return;
			}
			this.st.s.runOnServer(server -> {
				Entity e = server.overworld().getEntity(entity);
				if (e != null) {
					e.snapTo(at.x, at.y, at.z, 0, 0);
				}
			});
		}

		// ------------------------------------------------------------------ onion skin

		/** Ghosts: bare copies of the subject frozen at other ticks, drawn translucent (vanilla's friendly-invisible look). */
		private String onion(String spec) {
			this.clearGhosts();
			if (spec.equals("off")) {
				return "onion off";
			}
			if (this.isPlayer()) {
				throw new IllegalStateException("onion skin needs a subject other than the local player (use mannequin)");
			}
			this.st.s.runCommand("team add fray_onion");
			this.st.s.runCommand("team modify fray_onion seeFriendlyInvisibles true");
			this.st.s.runCommand("team modify fray_onion collisionRule never");
			this.st.s.runCommand("team join fray_onion @p");
			for (String s : spec.split(",")) {
				double t = Double.parseDouble(s);
				int g = this.spawn(this.kind, this.home, true);
				this.ghosts.add(g);
				this.ghostTicks.add(t);
				this.st.s.runOnServer(server -> {
					Entity e = server.overworld().getEntity(g);
					if (e != null) {
						e.setInvisible(true);
						if (e instanceof net.minecraft.world.entity.Mob m) {
							m.setNoAi(true);
						}
						server.getScoreboard().addPlayerToTeam(e.getScoreboardName(), server.getScoreboard().getPlayerTeam("fray_onion"));
					}
				});
			}
			this.c.waitTicks(4);
			if (this.clip != null) {
				for (int g : this.ghosts) {
					this.startOn(g, false);
				}
				this.c.waitTicks(2);
				this.applyGhosts(Double.isNaN(this.frozen) ? 0 : this.frozen);
			}
			return "onion at " + this.ghostTicks;
		}

		private void applyGhosts(double t) {
			for (int i = 0; i < this.ghosts.size(); i++) {
				int g = this.ghosts.get(i);
				double gt = this.ghostTicks.get(i);
				this.ensurePlaying(g, false);
				this.setFrozen(g, gt);
				this.place(g, this.home, gt);
			}
		}

		private void clearGhosts() {
			for (int g : this.ghosts) {
				this.st.s.runOnServer(server -> {
					Entity e = server.overworld().getEntity(g);
					if (e != null) {
						e.discard();
					}
				});
			}
			this.ghosts.clear();
			this.ghostTicks.clear();
		}

		// ------------------------------------------------------------------ cameras

		private float height() {
			return switch (this.kind) {
				case "knight" -> 2.8F;
				default -> 1.9F;
			};
		}

		/**
		 * Presets around the subject (it faces south, +Z): front, back, right (its right side), left, 3q (front right),
		 * 3q_left, top, low, close (3q, nearer), wide, fp (the local player's eyes). Or explicit {@code x y z tx ty tz}.
		 */
		private String cam(String[] a) {
			if (a.length == 0) {
				return "cam " + this.cam;
			}
			if (a.length >= 6) {
				double[] v = Arrays.stream(a).limit(6).mapToDouble(Double::parseDouble).toArray();
				this.st.cam(v[0], v[1], v[2], v[3], v[4], v[5]);
				this.cam = String.join(" ", a);
				return "cam " + this.cam;
			}
			this.cam = a[0];
			if (this.cam.equals("fp")) {
				if (!this.isPlayer()) {
					throw new IllegalStateException("cam fp needs subject player");
				}
				this.st.playerCam(CameraType.FIRST_PERSON);
				return "cam fp";
			}
			float h = this.height(), mid = h * 0.55F, d = h * 1.75F;
			double x = this.home.x, z = this.home.z;
			double[] p = switch (this.cam) {
				case "front" -> new double[] {x, Y + mid + 0.15, z + d};
				case "back" -> new double[] {x, Y + mid + 0.15, z - d};
				case "right" -> new double[] {x - d, Y + mid, z};
				case "left" -> new double[] {x + d, Y + mid, z};
				case "3q_left" -> new double[] {x + d * 0.72, Y + mid + 0.35, z + d * 0.72};
				case "top" -> new double[] {x - 0.6, Y + h + d * 1.1, z + 0.6};
				case "low" -> new double[] {x - d * 0.6, Y + 0.25, z + d * 0.8};
				case "close" -> new double[] {x - d * 0.5, Y + mid + 0.25, z + d * 0.5};
				case "wide" -> new double[] {x - d * 1.2, Y + mid + 0.8, z + d * 1.6};
				default -> new double[] {x - d * 0.72, Y + mid + 0.35, z + d * 0.72};
			};
			this.st.cam(p[0], p[1], p[2], x, Y + mid * (this.cam.equals("top") ? 0 : 1), z);
			return "cam " + this.cam;
		}

		// ------------------------------------------------------------------ pictures

		Path shot(String name) {
			this.c.waitTick();
			return this.c.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(OUT.resolve("shots")));
		}

		private String clipName() {
			return this.clip == null ? "none" : this.clip.getPath().replace('/', '_');
		}

		/** strip [t1,t2,...] [cam]: frozen frames side by side with labels (default: windup, tell, active, recovery ticks). */
		private String strip(String[] a) throws Exception {
			Timeline tl = MotionServer.timeline(this.clip);
			List<Double> ticks = new ArrayList<>();
			if (a.length > 1 && !a[1].startsWith("cam=")) {
				for (String s : a[1].split(",")) {
					ticks.add(Double.parseDouble(s));
				}
			} else if (tl != null) {
				int n = tl.ticks;
				for (int i = 0; i < 6; i++) {
					ticks.add((double) Math.round(i * (n - 1) / 5.0));
				}
			}
			for (String s : a) {
				if (s.startsWith("cam=")) {
					this.cam(new String[] {s.substring(4)});
				}
			}
			List<BufferedImage> tiles = new ArrayList<>();
			for (double t : ticks) {
				this.freeze(t);
				Path p = this.shot(this.clipName() + "_" + this.cam + "_t" + (int) t);
				tiles.add(Images.label(Images.scale(Images.read(p), 640), String.format(Locale.ROOT, "%s t%.0f %s", this.clip.getPath(), t, this.phaseAt(t))));
			}
			Path out = OUT.resolve("strips/" + this.clipName() + "_" + this.cam + ".jpg");
			Images.writeJpg(Images.grid(tiles, Math.min(3, tiles.size()), 640), out);
			return out.toString();
		}

		/** gif [cam=<preset>] [from=<t>] [to=<t>] [step=<ticks>] [width=<px>] [name=<file>]: one frame per step, frozen. */
		private String gif(String[] a) throws Exception {
			Timeline tl = MotionServer.timeline(this.clip);
			double from = 0, to = tl == null ? 20 : tl.ticks, step = 1;
			int width = 720;
			String name = null;
			for (String s : Arrays.copyOfRange(a, 1, a.length)) {
				String[] kv = s.split("=", 2);
				switch (kv[0]) {
					case "cam" -> this.cam(new String[] {kv[1]});
					case "from" -> from = Double.parseDouble(kv[1]);
					case "to" -> to = Double.parseDouble(kv[1]);
					case "step" -> step = Double.parseDouble(kv[1]);
					case "width" -> width = Integer.parseInt(kv[1]);
					case "name" -> name = kv[1];
					default -> {
					}
				}
			}
			List<BufferedImage> frames = new ArrayList<>();
			String base = name != null ? name : this.clipName() + "_" + this.cam;
			for (double t = from; t <= to + 1e-6; t += step) {
				this.freeze(t);
				Path p = this.shot("gif_" + base + String.format(Locale.ROOT, "_%05.1f", t));
				BufferedImage img = Images.scale(Images.read(p), width);
				frames.add(Images.label(img, String.format(Locale.ROOT, "%s  t%.0f  %s", this.clip.getPath(), t, this.phaseAt(t))));
			}
			Path out = OUT.resolve("gif/" + base + ".gif");
			Images.gif(frames, out, (int) Math.round(50 * step), 700);
			this.live();
			return out + " (" + frames.size() + " frames)";
		}

		/** split <t> [cam]: first person (the local player) beside third person (a mannequin twin), the same clip and tick. */
		private String split(String[] a) throws Exception {
			if (!this.isPlayer()) {
				throw new IllegalStateException("split needs subject player (first person) and makes a mannequin twin for the third person");
			}
			if (this.twin < 0) {
				this.twin = this.st.mannequin(this.home.x + 4, this.home.z, 0);
				this.c.waitTicks(5);
				if (this.clip != null) {
					this.startOn(this.twin, false);
					this.c.waitTicks(2);
				}
			}
			double t = a.length > 1 ? Double.parseDouble(a[1]) : 0;
			String tpCam = a.length > 2 ? a[2] : "3q";
			this.freeze(t);
			this.place(this.twin, this.home.add(4, 0, 0), t);
			this.st.playerCam(CameraType.FIRST_PERSON);
			BufferedImage fp = Images.read(this.shot("split_fp_" + this.clipName() + "_t" + (int) t));
			// Third person of the twin, from a preset around it.
			Vec3 saved = this.home;
			String savedKind = this.kind;
			this.kind = "mannequin";
			Session shifted = this;
			double ox = 4;
			String[] presetArgs = {tpCam};
			this.camAround(presetArgs, ox);
			BufferedImage tp = Images.read(this.shot("split_tp_" + this.clipName() + "_t" + (int) t));
			this.kind = savedKind;
			this.st.playerCam(CameraType.FIRST_PERSON);
			List<BufferedImage> pair = List.of(Images.label(Images.scale(fp, 800), "first person t" + (int) t), Images.label(Images.scale(tp, 800),
				"third person t" + (int) t + " " + this.phaseAt(t)));
			Path out = OUT.resolve("split/" + this.clipName() + "_t" + (int) t + ".jpg");
			Images.writeJpg(Images.grid(pair, 2, 800), out);
			return out.toString();
		}

		private void camAround(String[] preset, double offsetX) {
			float h = this.height(), mid = h * 0.55F, d = h * 1.75F;
			double x = this.home.x + offsetX, z = this.home.z;
			double[] p = switch (preset[0]) {
				case "front" -> new double[] {x, Y + mid + 0.15, z + d};
				case "right" -> new double[] {x - d, Y + mid, z};
				case "left" -> new double[] {x + d, Y + mid, z};
				case "back" -> new double[] {x, Y + mid + 0.15, z - d};
				default -> new double[] {x - d * 0.72, Y + mid + 0.35, z + d * 0.72};
			};
			this.st.cam(p[0], p[1], p[2], x, Y + mid, z);
		}

		// ------------------------------------------------------------------ shader packs

		private String pack(String name) throws Exception {
			if (!FabricLoader.getInstance().isModLoaded("iris")) {
				return "Iris isn't loaded (start the studio with -Piris)";
			}
			String result = this.c.computeOnClient(mc -> IrisPacks.select(name));
			this.c.waitTicks(40);
			return result;
		}

		// ------------------------------------------------------------------ reload

		/** Re-bakes every rig and clip from the sources in memory and swaps them in on both sides; replays the subject. */
		String reload() {
			long t0 = System.nanoTime();
			MotionBake.Result r;
			try {
				r = MotionBake.bakeAll(SRC.resolve("motion"), SRC.resolve("blockbench/rig"), null);
			} catch (Exception e) {
				return "reload FAILED: " + e.getMessage();
			}
			Map<Identifier, Clip> clips = new LinkedHashMap<>();
			Map<Identifier, String> hashes = new LinkedHashMap<>();
			Map<Identifier, Timeline> timelines = new LinkedHashMap<>();
			for (Map.Entry<Identifier, MotionBake.Baked> e : r.clips().entrySet()) {
				Clip parsed = ClipJson.parse(e.getKey(), e.getValue().clientJson());
				clips.put(e.getKey(), parsed);
				hashes.put(e.getKey(), ClipFiles.entryHash(e.getValue().clipHash(), parsed.rig.hash));
				timelines.put(e.getKey(), e.getValue().timeline());
			}
			Map<Identifier, Rig> rigs = new LinkedHashMap<>();
			for (Map.Entry<Identifier, MotionBake.BakedRig> e : r.rigs().entrySet()) {
				rigs.put(e.getKey(), e.getValue().rig());
			}
			this.st.s.runOnServer(server -> MotionServer.replace(timelines));
			this.c.runOnClient(mc -> {
				ClientClips.replace(clips, hashes, rigs);
				for (MotionBake.BakedRig br : r.rigs().values()) {
					if (br.png() != null && br.rig().texture != null) {
						try {
							var img = com.mojang.blaze3d.platform.NativeImage.read(br.png());
							mc.getTextureManager().register(br.rig().texture, new DynamicTexture(() -> "fray studio " + br.rig().id, img));
						} catch (java.io.IOException ex) {
							System.out.println("[studio] texture " + br.rig().texture + ": " + ex);
						}
					}
				}
				ClientMotion.restartAll();
			});
			StringBuilder rep = new StringBuilder();
			for (String l : r.lines()) {
				if (this.clip != null && (l.startsWith(this.clip.toString()) || l.contains(this.clip.toString() + ":"))) {
					rep.append(l).append('\n');
				}
			}
			int i = r.lines().size();
			for (String l : r.lines()) {
				if (l.startsWith("WARNING") || l.startsWith("FAILED")) {
					rep.append(l).append('\n');
				}
			}
			this.lastReport = rep.toString().trim();
			if (this.clip != null) {
				double f = this.frozen;
				this.play(this.clip, this.speed);
				if (!Double.isNaN(f)) {
					this.freeze(f);
				}
			}
			return String.format(Locale.ROOT, "reloaded %d clips, %d rigs in %.0f ms%s%n%s", clips.size(), rigs.size(), (System.nanoTime() - t0) / 1e6,
				r.failures().isEmpty() ? "" : " with " + r.failures().size() + " failed checks", this.lastReport);
		}

		// ------------------------------------------------------------------ overlay

		/** Hit volumes (red), hurt volumes (yellow) and the clip's tick, phase and tell above the subject (Gizmos). */
		void overlayTick() {
			if (!this.overlay || this.clip == null || this.subject < 0) {
				return;
			}
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			if (mc.level == null) {
				return;
			}
			Entity e = mc.level.getEntity(this.subject);
			Animator an = ClientMotion.get(this.subject);
			Timeline tl = MotionServer.timeline(this.clip);
			if (e == null || an == null || tl == null) {
				return;
			}
			double el = an.actionElapsed(ClientMotion.now(0));
			if (Double.isNaN(el)) {
				return;
			}
			int k = (int) Math.floor(el);
			double rad = Math.toRadians(e instanceof LivingEntity le ? le.yBodyRot : e.getYRot());
			double cos = Math.cos(rad), sin = Math.sin(rad);
			float scale = e instanceof LivingEntity le ? le.getScale() : 1;
			for (boolean hurt : new boolean[] {false, true}) {
				for (Timeline.Track track : (hurt ? tl.hurtVolumes : tl.volumes).values()) {
					Timeline.Capsule cap = track.at(k);
					if (cap == null || (!hurt && !tl.isActive(k) && !tl.isActive(k + 1))) {
						continue;
					}
					Vec3 pa = MotionServer.world(e.position(), cos, sin, scale, cap.ax(), cap.ay(), cap.az());
					Vec3 pb = MotionServer.world(e.position(), cos, sin, scale, cap.bx(), cap.by(), cap.bz());
					int colour = hurt ? 0xFFFFD040 : tl.isActive(k) ? 0xFFFF3030 : 0x80FF8080;
					Gizmos.line(pa, pb, colour, 6);
					Gizmos.circle(pa, cap.r() * scale, GizmoStyle.stroke(colour));
					Gizmos.circle(pb, cap.r() * scale, GizmoStyle.stroke(colour));
				}
			}
			String text = String.format(Locale.ROOT, "%s  t%d/%d  %s", this.clip.getPath(), k, tl.ticks, this.phaseAt(el));
			Gizmos.billboardText(text, e.position().add(0, this.height() + 0.5, 0), TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(0.5F))
				.setAlwaysOnTop();
		}

		// ------------------------------------------------------------------ cost

		/** perf [n]: n copies of the subject playing the clip in a grid; the client's per-entity cost from Fray's probes. */
		private String perf(String[] a) {
			int n = a.length > 1 ? Integer.parseInt(a[1]) : 24;
			List<Integer> ids = new ArrayList<>();
			int cols = (int) Math.ceil(Math.sqrt(n));
			for (int i = 0; i < n; i++) {
				ids.add(this.spawn(this.kind.equals("player") ? "mannequin" : this.kind, this.home.add((i % cols - cols / 2.0) * 1.8, 0, 3 + (i / cols) * 1.8), true));
			}
			this.c.waitTicks(20);
			this.c.runOnClient(mc -> FrayStats.reset());
			for (int tick = 0; tick < 160; tick++) {
				if (tick % 10 == 0 && this.clip != null) {
					for (int i = tick / 10 % 4; i < ids.size(); i += 4) {
						int id = ids.get(i);
						Identifier cl = this.clip;
						this.st.s.runOnServer(server -> {
							if (server.overworld().getEntity(id) instanceof LivingEntity le) {
								MotionServer.play(le, cl, 1);
							}
						});
					}
				}
				this.c.waitTick();
			}
			String line = this.c.computeOnClient(mc -> FrayStats.line());
			double[] st = this.c.computeOnClient(mc -> new double[] {FrayStats.sampleNanos, FrayStats.applyNanos, FrayStats.frames});
			for (int id : ids) {
				this.st.s.runOnServer(server -> {
					Entity e = server.overworld().getEntity(id);
					if (e != null) {
						e.discard();
					}
				});
			}
			return String.format(Locale.ROOT, "%d x %s: %s | %.2f us per entity per frame", n, this.kind, line, (st[0] + st[1]) / 1000.0 / Math.max(1, st[2]) / n);
		}
	}

	static final String HELP = """
		subject <mannequin|zombie|armoured_zombie|knight|player>   spawn the subject (facing south) at the stage centre
		play <ns:clip> [speed]          start the clip (server-started; the local player predicts it)
		freeze <tick> | live            hold the clip at a tick (fractions allowed) / let it run
		cam <front|back|right|left|3q|3q_left|top|low|close|wide|fp> | cam x y z tx ty tz
		shot [name]                     a screenshot (build/studio/shots)
		strip [t1,t2,...] [cam=..]      labelled frozen frames in a grid (build/studio/strips)
		gif [cam=..] [from=..] [to=..] [step=..] [width=..] [name=..]   one frame per step (build/studio/gif)
		onion <t1,t2,...> | onion off   translucent ghosts of the subject at other ticks
		split <tick> [cam]              first person beside third person (subject player; a mannequin twin)
		overlay on|off                  hit (red) and hurt (yellow) volumes, tick, phase and tell above the subject
		travel on|off                   show root motion by moving the subject along the clip's travel
		hud on|off                      hotbar and crosshair in first-person shots
		pack <name|off>                 switch the Iris shader pack (needs -Piris)
		reload | report                 re-bake from the sources now (also on every file change) / the last bake report
		perf [n]                        n copies of the subject playing the clip: Fray's per-entity cost
		run <command> | wait <ticks> | status | quit""";
}
