package dev.voidmc.bestiary.gametest;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.fray.client.Animator;
import dev.voidmc.fray.client.ClientClips;
import dev.voidmc.fray.client.ClientMotion;
import dev.voidmc.fray.client.FrayStats;
import dev.voidmc.fray.client.compat.IrisCompat;
import dev.voidmc.fray.motion.Clip;
import dev.voidmc.fray.motion.FrayHandshake;
import dev.voidmc.fray.motion.HumanoidRig;
import dev.voidmc.fray.motion.MotionServer;
import dev.voidmc.fray.motion.PoseBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.Identifier;

/**
 * The animation prototype's showcase (stage 0, ANIMATION.md M0): every proof that needs one client, with screenshots in
 * {@code build/run/clientGameTest/screenshots} and numbers in {@code build/run/clientGameTest/fray-report-<tag>.txt}.
 * <ul>
 * <li>zombie: the claw (windup tell, strike, recovery) as a frozen strip, a bare and an armoured zombie side by side;</li>
 * <li>blend: idle → claw → idle live, an interrupting crossfade, and a per-frame trace that shows no pops;</li>
 * <li>bb: the clip converted from a Blockbench project;</li>
 * <li>authority: the server's hit lands only in the strike window and only inside the baked volume;</li>
 * <li>player: third-person heavy swing and dodge roll with armour, the F5 view;</li>
 * <li>fp: the first-person arms through the hand renderer;</li>
 * <li>perf: frame time with 24 (and -Dfray.perf.mobs) animated zombies, Fray on and off;</li>
 * <li>dedicated: an in-process dedicated server: a client with a different clip is refused at login, then the same hit
 * test on the dedicated server.</li>
 * </ul>
 * -Dfray.show.only=zombie,perf limits the parts; -Dfray.show.tag=iris prefixes the screenshots.
 */
public class MotionShowcase implements FabricClientGameTest {
	static final Identifier IDLE = Bestiary.id("zombie/idle");
	static final Identifier CLAW = Bestiary.id("zombie/claw");
	static final Identifier SLAM = Bestiary.id("zombie/taunt_slam");
	static final Identifier ROLL = Bestiary.id("player/dodge_roll");
	static final Identifier SWING = Bestiary.id("player/heavy_swing");
	static final int Y = Stage.Y;

	private static final String TAG = System.getProperty("fray.show.tag", "");
	private static final Set<String> ONLY = new HashSet<>(Arrays.asList(System.getProperty("fray.show.only", "").split(",")));

	private static boolean want(String part) {
		return ONLY.contains(part) || ONLY.equals(Set.of(""));
	}

	@Override
	public void runTest(ClientGameTestContext c) {
		if (!MotionDuo.ROLE.isEmpty() || dev.voidmc.bestiary.studio.MotionStudio.active()) {
			return;
		}
		Stage.log("Fray motion showcase" + (TAG.isEmpty() ? "" : " [" + TAG + "]") + ": sodium " + FabricLoader.getInstance().isModLoaded("sodium") + ", iris "
			+ FabricLoader.getInstance().isModLoaded("iris"));
		c.getInput().resizeWindow(1600, 900);
		c.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.bobView().set(false);
			// The game tests send no input: without this the client drops to 30 fps after a minute ("AFK").
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
		});
		blendTrace();
		try (TestSingleplayerContext world = c.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261021");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			TestServerContext server = world.getServer();
			Stage stage = new Stage(c, server, TAG);
			stage.build();
			world.getConnection().waitForChunksRender();
			c.waitTicks(20);
			Stage.log("pack in use: " + c.computeOnClient(mc -> IrisCompat.packInUse()));
			if (want("zombie")) {
				zombie(stage);
			}
			if (want("blend")) {
				blendLive(stage);
			}
			if (want("bb")) {
				blockbench(stage);
			}
			if (want("authority")) {
				authority(stage, "integrated server");
			}
			if (want("player")) {
				player(stage);
			}
			if (want("fp")) {
				firstPerson(stage);
			}
			if (ONLY.contains("shadow")) {
				shadow(stage);
			}
			if (want("gif")) {
				gifs(stage);
			}
			if (want("perf")) {
				perf(stage, 24);
				int more = Integer.getInteger("fray.perf.mobs", 24);
				if (more != 24) {
					perf(stage, more);
				}
			}
		}
		if (want("dedicated")) {
			dedicated(c);
		}
		Stage.log("problems: " + Stage.PROBLEMS);
		Stage.writeReport(TAG);
		if (!Stage.PROBLEMS.isEmpty()) {
			throw new AssertionError("showcase problems: " + Stage.PROBLEMS);
		}
	}

	// ------------------------------------------------------------------ zombie

	private static void zombie(Stage st) {
		st.clearCreatures();
		int bare = st.zombie(0.5, 0.5, 0, false);
		int armoured = st.zombie(2.7, 0.5, 0, true);
		st.c.waitTicks(10);
		// Both idle (client-side BASE loop) before anything is sent.
		st.cam(-1.2, Y + 1.5, 3.1, 1.5, Y + 1.0, 0.5);
		st.shot("zombie_idle");
		st.play(bare, CLAW);
		st.play(armoured, CLAW);
		st.c.waitTicks(2);
		Stage.expect(st.c.computeOnClient(mc -> ClientMotion.get(bare) != null && ClientMotion.get(bare).clip(Animator.ACTION) != null),
			"the client got the server's motion/play for the claw");
		for (int t : new int[] {0, 3, 6, 9, 11, 12, 13, 15, 18, 22, 25}) {
			st.freeze(bare, t);
			st.freeze(armoured, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "zombie_claw_t%02d", t));
		}
		// A side view of the top of the windup and of the strike.
		st.cam(-2.6, Y + 1.3, 0.5, 0.5, Y + 1.2, 0.5);
		for (int t : new int[] {10, 12, 13}) {
			st.freeze(bare, t);
			st.freeze(armoured, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "zombie_claw_side_t%02d", t));
		}
		st.freeze(bare, Double.NaN);
		st.freeze(armoured, Double.NaN);
		st.c.waitTicks(30);
	}

	// ------------------------------------------------------------------ blending

	/**
	 * The client's Animator, driven with synthetic frame times (60 fps): a zombie idles, a claw blends in on top, ends,
	 * then a claw is interrupted by the Blockbench slam. The applied right-arm angle (over vanilla's zombie arm,
	 * -90 degrees) per frame shows the blends: the largest frame-to-frame step at a blend edge must stay near the
	 * clip's own speed, never a pop.
	 */
	private static void blendTrace() {
		Clip idle = ClientClips.get(IDLE), claw = ClientClips.get(CLAW), slam = ClientClips.get(SLAM);
		if (idle == null || claw == null || slam == null) {
			Stage.expect(false, "clips loaded for the blend trace");
			return;
		}
		Animator a = new Animator();
		a.play(Animator.BASE, idle, 0, 1);
		double prev = Double.NaN, maxStep = 0, maxEdgeStep = 0;
		StringBuilder spark = new StringBuilder();
		List<String> events = new ArrayList<>();
		for (int f = 0; f <= 150 * 3; f++) {
			double now = f / 3.0;
			if (f == 40 * 3) {
				a.play(Animator.ACTION, claw, now, 1);
				events.add("claw at 40");
			}
			if (f == 80 * 3) {
				a.play(Animator.ACTION, claw, now, 1);
				events.add("claw at 80");
			}
			if (f == 92 * 3) {
				a.play(Animator.ACTION, slam, now, 1);
				events.add("slam interrupts at 92");
			}
			PoseBuffer buf = a.sample(now, 1);
			double under = -90;
			double applied = under;
			if (buf != null) {
				int o = HumanoidRig.RIGHT_ARM * PoseBuffer.STRIDE;
				applied = under + (Math.toDegrees(buf.v[o]) - under) * buf.v[o + PoseBuffer.W_ROT];
			}
			if (!Double.isNaN(prev)) {
				double step = Math.abs(applied - prev);
				maxStep = Math.max(maxStep, step);
				boolean edge = f >= 40 * 3 && f < 44 * 3 || f >= 64 * 3 && f < 67 * 3 || f >= 92 * 3 && f < 96 * 3 || f >= 80 * 3 && f < 83 * 3;
				if (edge) {
					maxEdgeStep = Math.max(maxEdgeStep, step);
				}
			}
			prev = applied;
			if (f % 6 == 0) {
				spark.append(String.format(Locale.ROOT, "%.0f ", applied));
			}
		}
		Stage.log("blend trace (right arm xRot in degrees every 2 ticks, " + events + "): " + spark);
		Stage.log(String.format(Locale.ROOT, "blend trace: largest per-frame step %.1f deg anywhere, %.1f deg at blend edges (60 fps)", maxStep, maxEdgeStep));
		Stage.expect(maxEdgeStep <= maxStep && maxEdgeStep < 25, "blends never pop (edge step " + Math.round(maxEdgeStep) + " deg)");
	}

	private static void blendLive(Stage st) {
		st.clearCreatures();
		int z = st.zombie(0.5, 0.5, 0, false);
		st.c.waitTicks(10);
		st.cam(-2.4, Y + 1.5, 3.6, 0.5, Y + 1.1, 0.5);
		st.shot("blend_0_idle");
		st.play(z, CLAW);
		st.c.waitTicks(1);
		st.shot("blend_1_claw_blending_in");
		st.c.waitTicks(26);
		st.shot("blend_2_claw_blending_out");
		st.c.waitTicks(8);
		st.shot("blend_3_back_to_idle");
		// Interrupt: a claw cut off by the slam halfway through its windup.
		st.play(z, CLAW);
		st.c.waitTicks(7);
		st.play(z, SLAM);
		st.c.waitTicks(2);
		st.shot("blend_4_claw_to_slam_crossfade");
		st.c.waitTicks(40);
	}

	// ------------------------------------------------------------------ Blockbench

	private static void blockbench(Stage st) {
		st.clearCreatures();
		int z = st.zombie(0.5, 0.5, 0, false);
		st.c.waitTicks(10);
		st.play(z, SLAM);
		st.c.waitTicks(2);
		st.cam(-2.6, Y + 1.4, 3.6, 0.5, Y + 1.3, 0.5);
		for (int t : new int[] {10, 18, 20}) {
			st.freeze(z, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "bb_taunt_slam_t%02d", t));
		}
		st.freeze(z, Double.NaN);
		st.c.waitTicks(35);
	}

	// ------------------------------------------------------------------ server authority

	/** Hits are decided by the server's timeline: once, inside ACTIVE, only where the baked capsule reaches. */
	static void authority(Stage st, String where) {
		st.clearCreatures();
		int z = st.zombie(0.5, 0.5, 0, false);
		record Trial(String name, double x, double z, boolean shouldHit) {
		}
		for (Trial t : List.of(new Trial("in front, 1.2 blocks", 0.5, 1.7, true), new Trial("in front, 2.4 blocks", 0.5, 2.9, false),
			new Trial("behind, 1.2 blocks", 0.5, -0.7, false), new Trial("to the left, 1.0 block", 1.5, 0.6, false))) {
			int pig = st.dummy(t.x(), t.z());
			st.c.waitTicks(3);
			float before = st.health(pig);
			MotionServer.HITS.clear();
			long start = st.serverTime();
			st.play(z, CLAW);
			st.c.waitTicks(32);
			List<MotionServer.HitRecord> hits = MotionServer.HITS.stream().filter(h -> h.target() == pig).toList();
			float after = st.health(pig);
			String detail = hits.isEmpty() ? "no hit" : hits.stream().map(h -> "tick " + h.tick() + " (" + h.phase() + ", " + h.volume() + ")").toList().toString();
			Stage.log(String.format(Locale.ROOT, "authority [%s] %s: %s, health %.1f -> %.1f (clip started at server tick %d)", where, t.name(), detail, before, after,
				start));
			if (t.shouldHit()) {
				Stage.expect(hits.size() == 1, where + ": " + t.name() + " is hit exactly once");
				Stage.expect(!hits.isEmpty() && hits.getFirst().tick() >= 11 && hits.getFirst().tick() <= 13, where + ": the hit lands in the strike window (ticks 11-13)");
				Stage.expect(after < before, where + ": the dummy took damage");
			} else {
				Stage.expect(hits.isEmpty(), where + ": " + t.name() + " is never hit");
			}
			st.s.runCommand("kill @e[type=pig]");
			st.c.waitTicks(2);
		}
		Stage.log("server cost [" + where + "]: " + MotionServer.costLine());
	}

	// ------------------------------------------------------------------ the player

	private static void player(Stage st) {
		st.clearCreatures();
		for (String slot : new String[] {"armor.head iron_helmet", "armor.chest iron_chestplate", "armor.legs iron_leggings", "armor.feet iron_boots",
			"weapon.mainhand iron_sword"}) {
			st.s.runCommand("item replace entity @p " + slot.replace(" ", " with minecraft:"));
		}
		st.s.runCommand("tp @p 6.5 " + Y + " 0.5 -30 8");
		st.c.waitTicks(10);
		st.c.runOnClient(mc -> mc.gui.toastManager().clear());
		int me = st.c.computeOnClient(Stage::localPlayer);
		// The local player (vanilla draws it only in third person): the heavy swing in the F5 front and back views.
		st.thirdPerson(CameraType.THIRD_PERSON_FRONT);
		st.shot("player_f5_front_rest");
		st.c.runOnClient(mc -> ClientMotion.startLocal(SWING));
		st.c.waitTicks(2);
		for (int t : new int[] {6, 10, 12, 13, 18}) {
			st.freeze(me, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "player_swing_f5_front_t%02d", t));
		}
		st.thirdPerson(CameraType.THIRD_PERSON_BACK);
		for (int t : new int[] {10, 12, 13}) {
			st.freeze(me, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "player_swing_f5_back_t%02d", t));
		}
		st.freeze(me, Double.NaN);
		st.c.waitTicks(30);
		// The live dodge roll, F5 back view: the client applies the clip's root motion (players move client-side).
		st.s.runCommand("tp @p 6.5 " + Y + " -2 0 8");
		st.c.waitTicks(10);
		double z0 = st.c.computeOnClient(mc -> mc.player.getZ());
		st.c.runOnClient(mc -> ClientMotion.startLocal(ROLL));
		st.c.waitTicks(4);
		st.shot("player_roll_f5_back_live");
		st.c.waitTicks(16);
		double z1 = st.c.computeOnClient(mc -> mc.player.getZ());
		Stage.log(String.format(Locale.ROOT, "dodge roll root motion: the player moved %.2f blocks (clip asks 3.5)", z1 - z0));
		Stage.expect(z1 - z0 > 2.0, "the dodge roll moves the player");
		Stage.log("player acks: " + ClientMotion.LOG.stream().filter(l -> l.startsWith("ack")).toList());
		// Side views on a mannequin (the player model, the same rig and clips), started by the server.
		st.s.runCommand("tp @p 12 " + Y + " 12");
		int man = st.mannequin(0.5, 0.5, 0);
		st.c.waitTicks(10);
		st.cam(-1.6, Y + 1.4, 2.7, 0.5, Y + 1.1, 0.5);
		st.shot("mannequin_rest");
		st.play(man, SWING);
		st.c.waitTicks(2);
		for (int t : new int[] {6, 10, 11, 12, 13, 16, 22}) {
			st.freeze(man, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "mannequin_swing_t%02d", t));
		}
		st.freeze(man, Double.NaN);
		st.c.waitTicks(30);
		st.cam(-2.8, Y + 0.9, 0.5, 0.5, Y + 0.8, 0.5);
		st.play(man, ROLL);
		st.c.waitTicks(1);
		for (int t : new int[] {1, 3, 4, 5, 6, 7, 9}) {
			st.freeze(man, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "mannequin_roll_t%02d", t));
		}
		st.freeze(man, Double.NaN);
		st.c.waitTicks(20);
	}

	private static void firstPerson(Stage st) {
		st.clearCreatures();
		st.s.runCommand("tp @p 0.5 " + Y + " -8 0 5");
		st.c.waitTicks(10);
		int me = st.c.computeOnClient(Stage::localPlayer);
		st.playerCam(CameraType.FIRST_PERSON);
		st.c.waitTicks(5);
		st.shot("fp_vanilla_sword");
		FrayStats.reset();
		st.c.runOnClient(mc -> ClientMotion.startLocal(SWING));
		st.c.waitTicks(2);
		for (int t : new int[] {5, 9, 11, 12, 13, 15, 20}) {
			st.freeze(me, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "fp_swing_t%02d", t));
		}
		long draws = FrayStats.fpDraws;
		Stage.log("first person: " + FrayStats.line());
		Stage.expect(draws > 0, "first-person arms drawn by Fray inside the hand pass (" + draws + " draws)");
		st.freeze(me, Double.NaN);
		st.c.waitTicks(30);
		st.shot("fp_after_swing_vanilla_again");
	}

	// ------------------------------------------------------------------ shadows

	/**
	 * With a shader pack: the shadows on the ground, seen from above, of a zombie frozen with both arms raised (the
	 * Blockbench slam, tick 10) beside one at rest. The shadow pass draws the pose too (it re-extracts and re-poses).
	 */
	private static void shadow(Stage st) {
		st.clearCreatures();
		st.s.runCommand("tp @p 12 " + Y + " 12");
		st.s.runCommand("time set 1000");
		int raised = st.zombie(0.5, 0.5, 0, false);
		int rest = st.zombie(0.5, 3.0, 0, false);
		st.c.waitTicks(10);
		st.play(raised, SLAM);
		st.c.waitTicks(2);
		st.freeze(raised, 10);
		st.cam(-3.4, Y + 7, 1.75, -2.6, Y, 1.75);
		st.shot("shadow_from_above_slam_t10_vs_rest");
		st.cam(-6.5, Y + 1.6, 1.75, 0.5, Y + 0.6, 1.75);
		st.shot("shadow_side_slam_t10_vs_rest");
		st.play(rest, CLAW);
		st.c.waitTicks(2);
		st.freeze(rest, 11);
		st.freeze(raised, 20);
		st.cam(-3.4, Y + 7, 1.75, -2.6, Y, 1.75);
		st.shot("shadow_from_above_slam_t20_vs_claw_t11");
		st.freeze(raised, Double.NaN);
		st.freeze(rest, Double.NaN);
		st.s.runCommand("time set 1800");
		st.c.waitTicks(30);
	}

	// ------------------------------------------------------------------ GIF frames

	/** One frame per tick of each clip (frozen), for tools/gif.sh: the zombie's claw, the swing and the roll, first person. */
	private static void gifs(Stage st) {
		st.clearCreatures();
		st.s.runCommand("tp @p 12 " + Y + " 12");
		int z = st.zombie(0.5, 0.5, 0, false);
		int man = st.mannequin(3.5, 0.5, 0);
		st.c.waitTicks(10);
		st.cam(-0.8, Y + 1.5, 3.9, 2.0, Y + 1.0, 0.5);
		st.play(z, CLAW);
		st.play(man, SWING);
		st.c.waitTicks(2);
		for (int t = 0; t <= 26; t++) {
			st.freeze(z, t);
			st.freeze(man, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "gif_claw_swing_t%02d", t));
		}
		st.freeze(z, Double.NaN);
		st.freeze(man, Double.NaN);
		st.c.waitTicks(30);
		st.s.runOnServer(server -> server.overworld().getEntity(z).discard());
		st.cam(-0.2, Y + 0.9, 0.5, 3.5, Y + 0.8, 0.5);
		st.play(man, ROLL);
		st.c.waitTicks(1);
		for (int t = 0; t <= 12; t++) {
			st.freeze(man, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "gif_roll_t%02d", t));
		}
		st.freeze(man, Double.NaN);
		st.c.waitTicks(20);
		st.clearCreatures();
		st.s.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_sword");
		st.s.runCommand("tp @p 0.5 " + Y + " -8 0 5");
		st.c.waitTicks(10);
		int me = st.c.computeOnClient(Stage::localPlayer);
		st.playerCam(CameraType.FIRST_PERSON);
		st.c.runOnClient(mc -> ClientMotion.startLocal(SWING));
		st.c.waitTicks(2);
		for (int t = 0; t <= 26; t++) {
			st.freeze(me, t);
			st.c.waitTicks(1);
			st.shot(String.format(Locale.ROOT, "gif_fp_swing_t%02d", t));
		}
		st.freeze(me, Double.NaN);
		st.c.waitTicks(30);
	}

	// ------------------------------------------------------------------ performance

	private static void perf(Stage st, int count) {
		st.clearCreatures();
		st.s.runCommand("tp @p 12 " + Y + " 12");
		// A grid clear of the platform's pillars (at x, z = +-9).
		int cols = count <= 24 ? 6 : 12;
		int rows = (count + cols - 1) / cols;
		float spacing = count <= 24 ? 1.6F : 1.2F;
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int col = i % cols, row = i / cols;
			ids.add(st.zombie(0.5 + (col - (cols - 1) / 2.0) * spacing, 0.5 + row * spacing, 180, false));
		}
		double back = count <= 24 ? -7 : -10;
		st.cam(0.5, Y + 4.5, back, 0.5, Y + 0.8, rows * spacing / 2);
		st.c.waitTicks(40);
		st.shot("perf_" + count + "_zombies");
		double[][] results = new double[2][];
		StringBuilder line = new StringBuilder();
		double[] onMedian = new double[2], offMedian = new double[2];
		st.s.runOnServer(server -> MotionServer.resetCost());
		for (int round = 0; round < 2; round++) {
			for (int mode = 0; mode < 2; mode++) {
				boolean on = mode == 1;
				st.c.runOnClient(mc -> ClientMotion.enabled = on);
				st.c.waitTicks(20);
				st.c.runOnClient(mc -> FrayStats.reset());
				long t0 = System.nanoTime();
				for (int tick = 0; tick < 160; tick++) {
					if (tick % 10 == 0) {
						// A quarter of the crowd starts a claw every half second (the server's cost is the same both ways).
						for (int i = tick / 10 % 4; i < ids.size(); i += 4) {
							st.play(ids.get(i), CLAW);
						}
					}
					st.c.waitTick();
				}
				double wall = (System.nanoTime() - t0) / 1e9;
				double[] stats = st.c.computeOnClient(mc -> {
					long[] f = FrayStats.sortedFrames();
					return new double[] {f.length, f.length == 0 ? 0 : f[f.length / 2] / 1e6, f.length == 0 ? 0 : f[(int) (f.length * 0.95)] / 1e6,
						FrayStats.samples, FrayStats.sampleNanos, FrayStats.applies, FrayStats.applyNanos, FrayStats.shadowApplies, FrayStats.frames};
				});
				// The ring keeps the last 4096 frame times; the count is every frame.
				double frames = stats[8];
				double perFrameUs = (stats[4] + stats[6]) / 1000.0 / Math.max(1, stats[8]);
				String r = String.format(Locale.ROOT,
					"perf %d zombies, Fray %s: %.0f frames in %.1f s (%.0f fps), frame ms median %.3f p95 %.3f | sample %.2f us x %.1f/frame, apply %.2f us x %.1f/frame"
						+ " (%.0f in shadow passes), Fray %.1f us/frame = %.2f us per zombie",
					count, on ? "on " : "off", frames, wall, frames / wall, stats[1], stats[2], stats[3] == 0 ? 0 : stats[4] / 1000.0 / stats[3],
					stats[3] / Math.max(1, stats[8]), stats[5] == 0 ? 0 : stats[6] / 1000.0 / stats[5], stats[5] / Math.max(1, stats[8]), stats[7], perFrameUs,
					perFrameUs / count);
				Stage.log(r);
				if (on) {
					onMedian[round] = stats[1];
				} else {
					offMedian[round] = stats[1];
				}
				if (round == 1 && on) {
					Stage.expect(stats[3] > 0 && stats[5] > 0, "Fray sampled and applied poses for " + count + " zombies");
					if (IrisCompat.packInUse()) {
						Stage.expect(stats[7] > 0, "Iris's shadow pass re-posed the zombies (" + (long) stats[7] + " shadow applies)");
					}
				}
			}
		}
		st.c.runOnClient(mc -> ClientMotion.enabled = true);
		Stage.log("server cost with " + count + " zombies clawing: " + MotionServer.costLine());
		Stage.log(String.format(Locale.ROOT, "perf %d zombies: median frame %.3f / %.3f ms with Fray, %.3f / %.3f ms without (two rounds): difference %.3f ms",
			count, onMedian[0], onMedian[1], offMedian[0], offMedian[1], (onMedian[0] + onMedian[1] - offMedian[0] - offMedian[1]) / 2));
		st.shot("perf_" + count + "_zombies_end");
	}

	// ------------------------------------------------------------------ dedicated server and the clip hash

	private static void dedicated(ClientGameTestContext c) {
		Properties props = new Properties();
		int port = 25611;
		props.setProperty("server-port", String.valueOf(port));
		props.setProperty("online-mode", "false");
		props.setProperty("white-list", "false");
		props.setProperty("enforce-whitelist", "false");
		props.setProperty("view-distance", "6");
		props.setProperty("simulation-distance", "4");
		props.setProperty("pause-when-empty-seconds", "0");
		props.setProperty("spawn-protection", "0");
		props.setProperty("level-seed", "20261021");
		try (TestDedicatedServerContext server = c.worldBuilder().createServer(props)) {
			Stage.log("dedicated server up: dedicated " + server.computeOnServer(s -> s.isDedicatedServer()) + ", clip set "
				+ FrayHandshake.shortHash(MotionServer.clipSetHash()) + " (" + MotionServer.clipCount() + " timelines)");
			// 1. A client whose claw differs by one keyframe is refused at login, with a plain reason.
			byte[] shipped = ClientClips.shippedBytes(CLAW);
			String modified = new String(shipped, StandardCharsets.UTF_8).replaceFirst("-172", "-150");
			Stage.expect(!modified.equals(new String(shipped, StandardCharsets.UTF_8)), "made a client claw that differs by one keyframe");
			c.runOnClient(mc -> ClientClips.load(Map.of(CLAW, modified.getBytes(StandardCharsets.UTF_8))));
			Stage.log("client clip set with the modified claw: " + FrayHandshake.shortHash(ClientClips.setHash()));
			c.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString("localhost:" + port),
				new ServerData("fray", "localhost:" + port, ServerData.Type.OTHER), false, null));
			c.waitFor(mc -> mc.gui.screen() instanceof DisconnectedScreen, 1200);
			String reason = c.computeOnClient(mc -> mc.gui.screen().getNarrationMessage().getString());
			c.waitTicks(5);
			c.takeScreenshot("fray_" + (TAG.isEmpty() ? "" : TAG + "_") + "hash_refused");
			Stage.log("refused: " + reason);
			Stage.expect(reason.contains("don't match") && FrayHandshake.lastRefusal.contains("clip set"), "a mismatched clip set is refused at login with a clear message");
			// 2. The shipped clips: accepted; the authority test again, now on the dedicated server.
			c.runOnClient(mc -> ClientClips.load(null));
			c.setScreen(TitleScreen::new);
			int acceptedBefore = FrayHandshake.accepted;
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				Stage.expect(FrayHandshake.accepted > acceptedBefore, "the matching client passes the clip check");
				Stage st = new Stage(c, server, TAG.isEmpty() ? "ds" : TAG + "_ds");
				server.runCommand("execute as @a run tp @s 0 " + Y + " 0");
				c.waitTicks(20);
				st.build();
				connection.waitForChunksRender();
				c.waitTicks(20);
				authority(st, "dedicated server");
				int z = st.zombie(0.5, 0.5, 0, true);
				c.waitTicks(10);
				st.cam(-2.4, Y + 1.5, 3.6, 0.5, Y + 1.1, 0.5);
				st.play(z, CLAW);
				c.waitTicks(12);
				st.shot("dedicated_claw_live");
				c.waitTicks(30);
			}
		}
	}
}
