package dev.voidmc.bestiary.gametest;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.CreatureData;
import dev.voidmc.bestiary.ai.Executor;
import dev.voidmc.bestiary.ai.Guarding;
import dev.voidmc.bestiary.ai.Tokens;
import dev.voidmc.bestiary.ai.profile.CreatureProfile;
import dev.voidmc.bestiary.ai.profile.CreatureProfiles;
import dev.voidmc.bestiary.ai.profile.MoveDef;
import dev.voidmc.bestiary.api.BestiaryApi;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.bestiary.elite.Affix;
import dev.voidmc.bestiary.elite.EliteRules;
import dev.voidmc.bestiary.elite.Elites;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.FrayAttributes;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.api.Rank;
import dev.voidmc.fray.api.StatusType;
import dev.voidmc.fray.client.combat.ClientCombat;
import dev.voidmc.fray.combat.Pipeline;
import dev.voidmc.fray.combat.Statuses;
import dev.voidmc.fray.combat.net.CombatSync;
import dev.voidmc.fray.motion.MotionHits;
import dev.voidmc.fray.motion.MotionServer;
import dev.voidmc.fray.motion.Timeline;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Bestiary's AI game tests (BESTIARY.md §14, stage 1.4): each creature fights a mannequin player with its tells before
 * its hits; tokens limit attackers; a heavy defuses a creeper; the revenant blocks, parries and breaks; elites apply their
 * affixes and show on the HUD; champion packs; the codex records; vanilla compatibility (other mobs untouched, old
 * creatures load); a dedicated-server pass; G-NET (bytes down per player in a 30-creature fight) and G-PERF-S (200
 * engaged creatures around 4 bot players). Report {@code build/run/clientGameTest/fray-report-ai.txt}.
 * <pre>bash tools/gametest.sh -Dfray.show.only=none -Dbestiary.ai=all</pre>
 * Parts: creatures, tokens, elites, codex, compat, media, dedicated (pass, net, perf).
 */
public class AiShowcase implements FabricClientGameTest {
	private static final Set<String> PARTS = new HashSet<>(Arrays.asList(System.getProperty("bestiary.ai", "").split(",")));
	static final int Y = Stage.Y;

	record Hit(int attacker, int target, String clip, long tick, String window) {
	}

	record Dmg(int target, int attacker, String direct, float amount, long tick) {
	}

	static final List<Hit> HITS = new CopyOnWriteArrayList<>();
	static final List<Dmg> DAMAGE = new CopyOnWriteArrayList<>();
	private static boolean listening;

	private static boolean want(String part) {
		return PARTS.contains(part) || PARTS.contains("all");
	}

	@Override
	public void runTest(ClientGameTestContext c) {
		if (PARTS.equals(Set.of("")) || !MotionDuo.ROLE.isEmpty()) {
			return;
		}
		Stage.REPORT.clear();
		Stage.PROBLEMS.clear();
		Stage.log("Bestiary AI showcase: parts " + PARTS);
		if (!listening) {
			listening = true;
			MotionHits.RESOLVED.register((hit, window) -> HITS.add(new Hit(hit.attacker().getId(), hit.target().getId(), hit.clip().toString(), hit.serverTick(),
				window.name())));
			ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> DAMAGE.add(new Dmg(entity.getId(),
				source.getEntity() == null ? -1 : source.getEntity().getId(), source.getDirectEntity() == null ? "" : source.getDirectEntity().getType().toShortString(),
				taken, entity.level().getGameTime())));
		}
		c.getInput().resizeWindow(1920, 1080);
		c.runOnClient(mc -> {
			mc.options.guiScale().set(3);
			mc.resizeGui();
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.bobView().set(false);
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
		});
		if (want("creatures") || want("tokens") || want("elites") || want("codex") || want("compat") || want("media")) {
			try (TestSingleplayerContext world = c.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
				s.setSeed("20261022");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
			}).create()) {
				TestServerContext server = world.getServer();
				Stage st = new Stage(c, server, "ai");
				st.build();
				server.runCommand("gamerule spawn_monsters false");
				server.runCommand("gamerule send_command_feedback false");
				server.runCommand("time set 18000");
				// Creative: the local player isn't fought (codex switches to survival for its part).
				server.runCommand("gamemode creative @a");
				server.runCommand("effect give @a night_vision infinite 0 true");
				server.runCommand("effect give @a resistance infinite 4 true");
				server.runCommand("tp @p 12 " + Y + " 12 135 10");
				world.getConnection().waitForChunksRender();
				c.waitTicks(20);
				if (want("creatures")) {
					creatures(st);
				}
				if (want("tokens")) {
					tokens(st);
				}
				if (want("elites")) {
					elites(st);
				}
				if (want("codex")) {
					codex(st);
				}
				if (want("compat")) {
					compat(st);
				}
				if (want("media")) {
					media(st);
				}
			}
		}
		if (want("dedicated")) {
			dedicated(c);
		}
		Stage.log("problems: " + Stage.PROBLEMS);
		Stage.writeReport("ai");
		if (!Stage.PROBLEMS.isEmpty()) {
			throw new AssertionError("Bestiary AI showcase problems: " + Stage.PROBLEMS);
		}
	}

	// ------------------------------------------------------------------ helpers

	static ServerPlayer sp(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static LivingEntity entity(MinecraftServer server, int id) {
		return (LivingEntity) server.overworld().getEntity(id);
	}

	static CreatureProfile profile(String name) {
		return CreatureProfiles.get(Bestiary.id(name));
	}

	/** A mannequin player standing in for a bot: iron armour, a sword, lots of health; creatures treat it as a player. */
	static int bot(Stage st, double x, double z, float yaw, float health) {
		int id = st.mannequin(x, z, yaw);
		st.s.runOnServer(server -> {
			LivingEntity m = entity(server, id);
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
			m.setHealth(health);
			m.addTag("bestiary_bot");
			BestiaryApi.addBot(m);
		});
		return id;
	}

	/** A profiled creature at (x, z) at a danger; elite with affixes when {@code elite}. */
	static int spawn(Stage st, String name, double x, double z, float danger, boolean elite, List<String> affixes, int pack) {
		return spawnFacing(st, name, x, z, danger, elite, affixes, pack, 0.5, 0.5);
	}

	/** As {@link #spawn}, facing (fx, fz) so the target is in its sight cone from the start. */
	static int spawnFacing(Stage st, String name, double x, double z, float danger, boolean elite, List<String> affixes, int pack, double fx, double fz) {
		return st.s.computeOnServer(server -> {
			Mob m = Bestiary.create(server.overworld(), profile(name), new Vec3(x, Y, z), danger, elite, affixes, pack);
			if (m == null) {
				throw new AssertionError("couldn't spawn " + name);
			}
			float yaw = (float) Math.toDegrees(Math.atan2(fz - z, fx - x)) - 90;
			m.snapTo(x, Y, z, yaw, 0);
			m.setYHeadRot(yaw);
			m.setYBodyRot(yaw);
			// Spears: a damage type holder from the first server in this JVM breaks the second server's damage_event packet
			// (the test runs an integrated and a dedicated server in one process), so no spears in the tests.
			if (m.getMainHandItem().is(net.minecraft.tags.ItemTags.SPEARS)) {
				m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
			if (m.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			}
			return m.getId();
		});
	}

	static int spawn(Stage st, String name, double x, double z, float danger) {
		return spawn(st, name, x, z, danger, false, List.of(), 0);
	}

	static long now(Stage st) {
		return st.serverTime();
	}

	static int minimum(String colour) {
		return switch (colour) {
			case "orange" -> 8;
			case "red", "purple" -> 12;
			default -> 5;
		};
	}

	/**
	 * Every hit and every damage the attacker dealt to the target since {@code since} followed a tell of this attacker
	 * on this target by at least the colour's minimum (ranged hits fly after the release, so they only grow the lead).
	 * Returns the number of hits checked, or -1 when one came without a proper tell.
	 */
	static int tellsBeforeHits(int attacker, int target, long since, List<String> notes) {
		List<Executor.Tell> tells = Executor.TELLS.stream().filter(t -> t.attacker() == attacker && t.target() == target && t.tick() >= since - 1).toList();
		List<Long> hitTicks = new ArrayList<>();
		for (Hit h : HITS) {
			if (h.attacker() == attacker && h.target() == target && h.tick() >= since && h.window().equals("NONE")) {
				hitTicks.add(h.tick());
			}
		}
		for (Dmg d : DAMAGE) {
			if (d.attacker() == attacker && d.target() == target && d.tick() >= since && !d.direct().contains("mob") && d.direct().contains("arrow")) {
				hitTicks.add(d.tick());
			}
		}
		int ok = 0;
		for (long t : hitTicks) {
			Executor.Tell before = null;
			for (Executor.Tell tell : tells) {
				if (tell.tick() <= t && t - tell.tick() <= 80 && (before == null || tell.tick() > before.tick())) {
					before = tell;
				}
			}
			if (before == null) {
				notes.add("hit at " + t + " with no tell before it");
				return -1;
			}
			if (t - before.tick() < minimum(before.colour())) {
				notes.add("hit at " + t + " only " + (t - before.tick()) + " ticks after a " + before.colour() + " tell");
				return -1;
			}
			ok++;
		}
		return ok;
	}

	static String moves(int attacker, long since) {
		StringBuilder b = new StringBuilder();
		for (Executor.Tell t : Executor.TELLS) {
			if (t.attacker() == attacker && t.tick() >= since) {
				b.append(t.move()).append('(').append(t.colour()).append(") ");
			}
		}
		return b.toString().trim();
	}

	// ------------------------------------------------------------------ 1. each creature against a mannequin

	private static void creatures(Stage st) {
		Stage.log("--- creatures: each fights a mannequin player; every hit follows its tell");
		fight(st, "zombie", 4, 3.5, 400, 3);
		fight(st, "skeleton", 2, 11, 500, 2);
		fight(st, "spider", 3, 6, 500, 2);
		fight(st, "revenant_soldier", 3, 4, 500, 2);
		creeper(st);
		defuse(st);
		revenantGuard(st);
	}

	/** One creature, one mannequin {@code dist} blocks away: engaged, attacks, hits land after tells. */
	static void fight(Stage st, String name, float danger, double dist, int maxTicks, int wantHits) {
		st.clearCreatures();
		int bot = bot(st, 0.5, 0.5, 180, 2000);
		long since = now(st);
		int id = spawn(st, name, 0.5, 0.5 - dist, danger);
		int hits = 0;
		for (int t = 0; t < maxTicks && hits < wantHits; t += 10) {
			st.c.waitTicks(10);
			long s0 = since;
			hits = (int) HITS.stream().filter(h -> h.attacker() == id && h.target() == bot && h.tick() >= s0 && h.window().equals("NONE")).count()
				+ (int) DAMAGE.stream().filter(d -> d.attacker() == id && d.target() == bot && d.tick() >= s0 && d.direct().contains("arrow")).count();
		}
		String state = st.s.computeOnServer(server -> {
			Mob m = (Mob) entity(server, id);
			Brain b = m == null ? null : Brain.get(m);
			return b == null ? "no brain" : b.awareness + " target " + (b.target == null ? "-" : b.target.getId()) + " role " + b.role;
		});
		List<String> notes = new ArrayList<>();
		int checked = tellsBeforeHits(id, bot, since, notes);
		float hp = st.health(bot);
		Stage.log(String.format(Locale.ROOT, "%s (D%.0f): %s; moves %s; %d hits, mannequin %.0f/2000 %s", name, danger, state, moves(id, since), hits, hp,
			notes.isEmpty() ? "" : notes.toString()));
		Stage.expect(state.contains("ENGAGED") && state.contains("target " + bot), name + " engages the mannequin player");
		Stage.expect(hits >= wantHits && hp < 2000, name + " lands hits on the mannequin (" + hits + ")");
		Stage.expect(checked >= wantHits, name + ": every hit follows a tell by at least its colour's minimum (" + checked + " checked)");
	}

	/** The creeper's fuse is its red tell; it explodes on the mannequin only after the fuse burned. */
	static void creeper(Stage st) {
		st.clearCreatures();
		int bot = bot(st, 0.5, 0.5, 180, 2000);
		long since = now(st);
		int id = spawn(st, "creeper", 0.5, -4, 1);
		boolean gone = false;
		for (int t = 0; t < 300 && !gone; t += 5) {
			st.c.waitTicks(5);
			gone = st.s.computeOnServer(server -> server.overworld().getEntity(id) == null);
		}
		long boom = DAMAGE.stream().filter(d -> d.target() == bot && d.tick() >= since).mapToLong(Dmg::tick).min().orElse(-1);
		Executor.Tell tell = Executor.TELLS.stream().filter(t -> t.attacker() == id && t.colour().equals("red")).findFirst().orElse(null);
		Stage.log("creeper: exploded " + gone + ", red tell at " + (tell == null ? "-" : tell.tick()) + ", mannequin hurt at " + boom + ", health " + st.health(bot));
		Stage.expect(gone && tell != null && boom > 0 && boom - tell.tick() >= 28, "creeper: the fuse (red tell) burns 30 ticks before the blast");
	}

	/** BESTIARY §14: a creeper hit with a heavy during the fuse defuses. */
	static void defuse(Stage st) {
		st.clearCreatures();
		int bot = bot(st, 0.5, 0.5, 180, 2000);
		int id = spawn(st, "creeper", 0.5, -2.2, 1);
		boolean fusing = false;
		for (int t = 0; t < 200 && !fusing; t++) {
			st.c.waitTick();
			fusing = st.s.computeOnServer(server -> {
				Mob m = (Mob) entity(server, id);
				Brain b = m == null ? null : Brain.get(m);
				return b != null && b.move != null && b.move.id().equals("fuse") && ((Creeper) m).getSwellDir() > 0;
			});
		}
		st.c.waitTicks(8);
		float[] r = st.s.computeOnServer(server -> {
			Creeper cr = (Creeper) entity(server, id);
			int swellBefore = ((dev.voidmc.bestiary.mixin.CreeperAccess) cr).bestiary$swell();
			ServerPlayer p = sp(server);
			HitContext heavy = new HitContext(p, new ItemStack(Items.IRON_SWORD), Identifier.fromNamespaceAndPath("fray", "test_heavy"),
				DamageSpec.of(DamageKind.SLASH, 7, 14), HitFlags.HEAVY, 0, null, 1, 1.5F);
			FrayApi.damage().deal(cr, heavy);
			return new float[] {swellBefore, FrayApi.statuses().has(cr, StatusType.STAGGER) ? 1 : 0};
		});
		st.c.waitTicks(4);
		float[] after = st.s.computeOnServer(server -> {
			Entity e = server.overworld().getEntity(id);
			if (!(e instanceof Creeper cr)) {
				return new float[] {-1, -1};
			}
			return new float[] {((dev.voidmc.bestiary.mixin.CreeperAccess) cr).bestiary$swell(), cr.getSwellDir()};
		});
		st.c.waitTicks(10);
		boolean alive = st.s.computeOnServer(server -> server.overworld().getEntity(id) != null);
		Stage.log(String.format(Locale.ROOT, "defuse: fusing %s, swell %.0f when hit, staggered %s, swell %.0f dir %.0f after, alive 14 ticks later %s", fusing, r[0],
			r[1] > 0, after[0], after[1], alive));
		Stage.expect(fusing && r[1] > 0 && after[0] == 0 && alive, "a creeper hit with a heavy during the fuse is Staggered and its fuse resets");
		st.clearCreatures();
	}

	/** The revenant blocks lights from the front, parries some, and a charged hit or an axe's heavy breaks its guard. */
	static void revenantGuard(Stage st) {
		st.clearCreatures();
		int bot = bot(st, 0.5, 0.5, 180, 2000);
		int id = spawn(st, "revenant_soldier", 0.5, -3.5, 2);
		long b0 = Guarding.blocks, p0 = Guarding.parries, k0 = Guarding.breaks;
		st.c.waitTicks(30);
		int guardedHits = 0;
		for (int i = 0; i < 40; i++) {
			boolean heavy = i >= 30;
			boolean guarded = st.s.computeOnServer(server -> {
				Mob rev = (Mob) entity(server, id);
				Brain b = Brain.get(rev);
				LivingEntity m = entity(server, bot);
				// Face each other: the hit comes from the front.
				if (b == null || !b.guarding) {
					return false;
				}
				ItemStack weapon = new ItemStack(heavy ? Items.IRON_AXE : Items.IRON_SWORD);
				m.setItemSlot(EquipmentSlot.MAINHAND, weapon);
				HitContext hit = new HitContext(m, weapon, Identifier.fromNamespaceAndPath("fray", heavy ? "test_heavy" : "test_light"),
					DamageSpec.of(DamageKind.SLASH, 4, 4), heavy ? HitFlags.HEAVY : HitFlags.LIGHT, 0, null);
				FrayApi.damage().deal(rev, hit);
				return true;
			});
			if (guarded) {
				guardedHits++;
			}
			st.c.waitTicks(8);
		}
		long blocks = Guarding.blocks - b0, parries = Guarding.parries - p0, breaks = Guarding.breaks - k0;
		shotFromAbove(st, "revenant_guard");
		Stage.log("revenant guard: " + guardedHits + " hits on a raised guard: " + blocks + " blocked, " + parries + " parried (riposte), " + breaks
			+ " guard breaks; moves " + moves(id, 0));
		Stage.expect(guardedHits >= 6 && blocks >= 1 && breaks >= 1, "the revenant blocks from the front, and an axe's heavies break its guard");
		Stage.expect(parries >= 1 || guardedHits < 6, "the revenant parries a light now and then");
		st.clearCreatures();
	}

	// ------------------------------------------------------------------ 2. tokens

	/** BESTIARY §14: a pack around a dummy never has more than the token count in ACTIVE at once. */
	private static void tokens(Stage st) {
		Stage.log("--- tokens");
		st.clearCreatures();
		int bot = bot(st, 0.5, 0.5, 180, 4000);
		List<Integer> pack = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			double a = i * Math.PI * 2 / 7;
			pack.add(spawn(st, "zombie", 0.5 + Math.cos(a) * 4, 0.5 + Math.sin(a) * 4, 1));
		}
		long since = now(st);
		int maxActive = 0, maxRunning = 0, maxHolders = 0;
		int capacity = st.s.computeOnServer(server -> Tokens.capacity(Brain.get((Mob) entity(server, pack.getFirst())), MoveDef.Tokens.MELEE));
		for (int t = 0; t < 600; t++) {
			st.c.waitTick();
			int[] r = st.s.computeOnServer(server -> {
				long now = server.overworld().getGameTime();
				int active = 0, running = 0;
				for (int id : pack) {
					LivingEntity z = entity(server, id);
					MotionServer.Running run = z == null ? null : MotionServer.running(z);
					Brain zb = z == null ? null : Brain.get((Mob) z);
					if (run != null && zb != null && zb.target != null && zb.target.getId() == bot && run.clip().getPath().startsWith("zombie/")
						&& !run.clip().getPath().contains("stagger")) {
						running++;
						if (run.timeline.isActive(run.tick(now))) {
							active++;
						}
					}
				}
				return new int[] {active, running, Tokens.holders(entity(server, bot), MoveDef.Tokens.MELEE, now)};
			});
			maxActive = Math.max(maxActive, r[0]);
			maxRunning = Math.max(maxRunning, r[1]);
			maxHolders = Math.max(maxHolders, r[2]);
		}
		long attacks = Executor.TELLS.stream().filter(t -> t.target() == bot && t.tick() >= since).count();
		Stage.log(String.format(Locale.ROOT, "tokens: 7 zombies (D1, %d melee tokens) on one mannequin for 30 s: %d attacks, at most %d attacking and %d in ACTIVE at once, %d holders; refusals %d",
			capacity, attacks, maxRunning, maxActive, maxHolders, Tokens.refusals));
		Stage.expect(attacks >= 6 && maxRunning <= capacity && maxActive <= capacity && maxHolders <= capacity,
			"tokens: never more than " + capacity + " zombies attack one target at once");
		shotFromAbove(st, "tokens_ring");
		st.clearCreatures();
	}

	static void shotFromAbove(Stage st, String name) {
		st.cam(7, Y + 6, 7, 0.5, Y, 0.5);
		st.c.waitTicks(2);
		st.c.takeScreenshot(TestScreenshotOptions.of("ai_" + name).disableCounterPrefix());
		st.playerCam(CameraType.FIRST_PERSON);
	}

	// ------------------------------------------------------------------ 3. elites

	private static void elites(Stage st) {
		Stage.log("--- elites: the six affixes, a champion pack, the nameplate");
		st.clearCreatures();
		st.s.runCommand("tp @p 0.5 " + Y + " 8 180 10");
		int bot = bot(st, 0.5, -8, 0, 4000);
		// Ironbound: +6 armour, poise +60 %; no Stagger until three blunt hits break the plate.
		int iron = spawn(st, "zombie", -4, 0.5, 3, true, List.of("ironbound"), 0);
		st.c.waitTicks(5);
		float[] ir = st.s.computeOnServer(server -> {
			Mob m = (Mob) entity(server, iron);
			float armour = (float) m.getAttributeValue(Attributes.ARMOR);
			FrayApi.statuses().apply(m, StatusType.STAGGER, 20, null);
			boolean before = FrayApi.statuses().has(m, StatusType.STAGGER);
			for (int i = 0; i < 3; i++) {
				CombatState(m).lastHitBy.clear();
				FrayApi.damage().deal(m, new HitContext(sp(server), new ItemStack(Items.MACE), Identifier.fromNamespaceAndPath("fray", "test_blunt"),
					DamageSpec.of(DamageKind.BLUNT, 1, 0), HitFlags.LIGHT, 0, null));
			}
			FrayApi.statuses().apply(m, StatusType.STAGGER, 20, null);
			boolean after = FrayApi.statuses().has(m, StatusType.STAGGER);
			return new float[] {armour, before ? 1 : 0, after ? 1 : 0, Brain.get(m).plateHits};
		});
		Stage.log(String.format(Locale.ROOT, "ironbound: armour %.0f, staggered with the plate on %s, plate hits %.0f, staggered after %s", ir[0], ir[1] > 0, ir[3], ir[2] > 0));
		Stage.expect(ir[1] == 0 && ir[2] > 0 && ir[0] >= 6, "Ironbound: +6 armour; no Stagger until its plate takes three blunt hits");
		// Frenzied: enrages at half health.
		int fr = spawn(st, "zombie", -2, 0.5, 3, true, List.of("frenzied"), 0);
		st.c.waitTicks(3);
		st.s.runOnServer(server -> ((Mob) entity(server, fr)).setHealth(((Mob) entity(server, fr)).getMaxHealth() * 0.4F));
		st.c.waitTicks(3);
		boolean enraged = st.s.computeOnServer(server -> Brain.get((Mob) entity(server, fr)).enraged);
		Stage.expect(enraged, "Frenzied: enrages at half health");
		// Venomous: poison build-up on its hits; a poison cloud when hit hard.
		int ven = spawn(st, "zombie", 0, 0.5, 3, true, List.of("venomous"), 0);
		st.c.waitTicks(3);
		float[] vr = st.s.computeOnServer(server -> {
			Mob m = (Mob) entity(server, ven);
			LivingEntity target = entity(server, bot);
			FrayApi.damage().deal(target, new HitContext(m, ItemStack.EMPTY, Bestiary.id("zombie/claw"), DamageSpec.of(DamageKind.SLASH, 2, 2), HitFlags.LIGHT, 0,
				null));
			float meter = FrayApi.statuses().meter(target, StatusType.POISON);
			long clouds = EliteRules.VENOM_CLOUDS;
			FrayApi.damage().deal(m, new HitContext(sp(server), new ItemStack(Items.IRON_SWORD), Identifier.fromNamespaceAndPath("fray", "test_hard"),
				DamageSpec.of(DamageKind.SLASH, m.getMaxHealth() * 0.3F, 0), HitFlags.HEAVY, 0, null));
			return new float[] {meter, EliteRules.VENOM_CLOUDS - clouds};
		});
		Stage.log(String.format(Locale.ROOT, "venomous: the mannequin's poison meter %.2f after one hit, clouds when hit hard %.0f", vr[0], vr[1]));
		Stage.expect(vr[0] > 0 && vr[1] >= 1, "Venomous: poison build-up on its hits and a cloud when hit hard");
		// Regenerating: heals 2 % a second after 3 s unhurt.
		int reg = spawn(st, "zombie", 2, 0.5, 3, true, List.of("regenerating"), 0);
		st.c.waitTicks(3);
		float h0 = st.s.computeOnServer(server -> {
			Mob m = (Mob) entity(server, reg);
			FrayApi.damage().deal(m, new HitContext(sp(server), new ItemStack(Items.IRON_SWORD), Identifier.fromNamespaceAndPath("fray", "test_hard"),
				DamageSpec.of(DamageKind.SLASH, 20, 0), HitFlags.LIGHT, 0, null));
			return m.getHealth();
		});
		st.c.waitTicks(110);
		float h1 = st.health(reg);
		Stage.log(String.format(Locale.ROOT, "regenerating: %.1f -> %.1f in 5.5 s", h0, h1));
		Stage.expect(h1 > h0 + 1, "Regenerating: heals after 3 s unhurt");
		// Shielded: a shield; blocks from the front.
		int sh = spawn(st, "zombie", 4, 0.5, 3, true, List.of("shielded"), 0);
		st.c.waitTicks(3);
		boolean shield = st.s.computeOnServer(server -> ((Mob) entity(server, sh)).getOffhandItem().is(Items.SHIELD));
		Stage.expect(shield, "Shielded: carries a shield (its guard is the revenant's, tested there)");
		// Swift: +30 % speed.
		int sw = spawn(st, "zombie", 6, 0.5, 3, true, List.of("swift"), 0);
		st.c.waitTicks(3);
		double[] spd = st.s.computeOnServer(server -> new double[] {((Mob) entity(server, sw)).getAttributeValue(Attributes.MOVEMENT_SPEED),
			((Mob) entity(server, sw)).getAttributeBaseValue(Attributes.MOVEMENT_SPEED)});
		Stage.expect(Math.abs(spd[0] / spd[1] - 1.3) < 0.02, String.format(Locale.ROOT, "Swift: +30 %% speed (%.3f of %.3f)", spd[0], spd[1]));
		// Elite numbers and the HUD: rank, name, affixes synced to the client.
		float[] el = st.s.computeOnServer(server -> {
			Mob m = (Mob) entity(server, sw);
			return new float[] {m.getMaxHealth(), (float) m.getAttributeValue(Attributes.SCALE), FrayApi.profiles().rank(m) == Rank.ELITE ? 1 : 0};
		});
		String name = st.s.computeOnServer(server -> entity(server, sw).getName().getString());
		st.c.waitTicks(5);
		int chips = st.c.computeOnClient(mc -> {
			var p = ClientCombat.PROFILES.get(sw);
			return p == null ? -1 : p.affixes().size() * 10 + p.rank();
		});
		Stage.log(String.format(Locale.ROOT, "elite: \"%s\", max health %.0f (zombie 20 × D3 2.2 × elite 3), scale %.2f, client profile %d", name, el[0], el[1], chips));
		Stage.expect(el[2] > 0 && el[0] > 120 && el[1] > 1.1 && name.startsWith("the "), "an elite: named, ×3 health, drawn bigger, Fray's elite rank");
		Stage.expect(chips == 10 + Rank.ELITE.ordinal(), "the client knows the elite's rank and affix chips (nameplate and target frame)");
		// A champion pack: an elite leader and commons sharing one of its affixes.
		st.clearCreatures();
		int leader = spawn(st, "zombie", 0.5, 0.5, 3, true, List.of("frenzied", "venomous"), 4);
		st.c.waitTicks(10);
		int[] pk = st.s.computeOnServer(server -> {
			Mob l = (Mob) entity(server, leader);
			int members = 0, sharing = 0;
			for (Entity e : server.overworld().getAllEntities()) {
				if (e instanceof Mob m && CreatureData.get(m) != null && CreatureData.of(m).leader().map(l.getUUID()::equals).orElse(false)) {
					members++;
					if (!CreatureData.of(m).affixes().isEmpty() && l.getAttached(CreatureData.TYPE).affixes().containsAll(CreatureData.of(m).affixes())) {
						sharing++;
					}
				}
			}
			return new int[] {members, sharing};
		});
		Stage.log("champion pack: " + pk[0] + " members, " + pk[1] + " sharing one of the leader's affixes");
		Stage.expect(pk[0] == 4 && pk[1] == 4, "a champion pack: four commons around the elite, each with one of its affixes");
		// The nameplates in the player's view.
		st.s.runCommand("tp @p 0.5 " + Y + " 6.5 180 8");
		st.playerCam(CameraType.FIRST_PERSON);
		st.s.runOnServer(server -> {
			for (Entity e : server.overworld().getAllEntities()) {
				if (e instanceof Mob m && Brain.get(m) != null) {
					m.setNoAi(true);
					m.setHealth(m.getMaxHealth() * 0.8F);
				}
			}
		});
		st.c.waitTicks(20);
		st.c.takeScreenshot(TestScreenshotOptions.of("ai_elite_pack_hud").disableCounterPrefix());
		Stage.log("elite pack screenshot: ai_elite_pack_hud (live affixed creatures " + Elites.live() + ")");
		st.clearCreatures();
	}

	static dev.voidmc.fray.combat.CombatState CombatState(LivingEntity e) {
		return dev.voidmc.fray.combat.CombatState.of(e);
	}

	// ------------------------------------------------------------------ 4. codex

	private static void codex(Stage st) {
		Stage.log("--- codex");
		st.clearCreatures();
		st.s.runCommand("tp @p 0.5 " + Y + " 0.5 180 0");
		st.s.runCommand("gamemode survival @p");
		long since = now(st);
		int z = spawn(st, "zombie", 0.5, -2.5, 1);
		for (int t = 0; t < 200; t += 10) {
			st.c.waitTicks(10);
			boolean told = Executor.TELLS.stream().anyMatch(tl -> tl.attacker() == z && tl.tick() >= since);
			if (told) {
				break;
			}
		}
		st.c.waitTicks(20);
		st.s.runOnServer(server -> {
			Mob m = (Mob) entity(server, z);
			for (int i = 0; i < 40 && m.isAlive(); i++) {
				CombatState(m).lastHitBy.clear();
				FrayApi.damage().deal(m, new HitContext(sp(server), new ItemStack(Items.IRON_SWORD), Identifier.fromNamespaceAndPath("fray", "test_hit"),
					DamageSpec.of(DamageKind.SLASH, 6, 0), HitFlags.LIGHT, 0, null));
			}
		});
		st.c.waitTicks(5);
		String entry = st.s.computeOnServer(server -> {
			Codex.Entry e = Codex.entry(sp(server), "bestiary:zombie");
			return e.knowledge() + " seen " + (e.seen() > 0) + " fought " + e.fought() + " felled " + e.felled() + " moves " + e.moves();
		});
		Stage.log("codex: zombie " + entry);
		Stage.expect(entry.contains("seen true") && !entry.contains("fought 0") && entry.contains("felled 1") && !entry.contains("moves []"),
			"the codex records seen, fought, felled and the moves learned");
		st.s.runCommand("gamemode creative @p");
	}

	// ------------------------------------------------------------------ 5. vanilla compatibility

	private static void compat(Stage st) {
		Stage.log("--- compat");
		st.clearCreatures();
		String r = st.s.computeOnServer(server -> {
			ServerLevel level = server.overworld();
			StringBuilder b = new StringBuilder();
			// A husk (a vanilla variant no profile names) keeps vanilla's AI.
			Mob husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
			husk.snapTo(3, Y, 3, 0, 0);
			level.addFreshEntity(husk);
			boolean huskMelee = ((dev.voidmc.bestiary.mixin.MobAccess) husk).bestiary$goals().getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof MeleeAttackGoal);
			b.append("husk: brain ").append(Brain.get(husk) != null).append(", vanilla melee goal ").append(huskMelee).append("; ");
			// A zombie saved by a world without Bestiary (no attachment, no modifiers) loads and is taken over as legacy, never elite.
			Mob raw = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			raw.snapTo(-3, Y, 3, 0, 0);
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			raw.save(out);
			CompoundTag tag = out.buildResult();
			tag.remove("fabric:attachments");
			Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level, EntitySpawnReason.LOAD,
				e -> e);
			level.addFreshEntity(loaded);
			CreatureData d = CreatureData.of((Mob) loaded);
			b.append("old zombie: brain ").append(Brain.get((Mob) loaded) != null).append(", reason ").append(d.reason()).append(", elite ").append(d.elite())
				.append(String.format(Locale.ROOT, ", danger %.1f; ", d.danger()));
			// An elite saved and loaded again keeps its affixes and name.
			Mob elite = Bestiary.create(level, profile("zombie"), new Vec3(0, Y, 5), 3, true, List.of("ironbound", "swift"), 0);
			TagValueOutput out2 = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			elite.save(out2);
			CompoundTag tag2 = out2.buildResult();
			elite.discard();
			Entity back = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag2), level, EntitySpawnReason.LOAD,
				e -> e);
			level.addFreshEntity(back);
			CreatureData d2 = CreatureData.of((Mob) back);
			b.append("elite reloaded: elite ").append(d2.elite()).append(", affixes ").append(d2.affixes()).append(", name ").append(back.getName().getString())
				.append(String.format(Locale.ROOT, ", max health %.0f", ((Mob) back).getMaxHealth()));
			return b.toString();
		});
		Stage.log("compat: " + r);
		Stage.expect(r.contains("husk: brain false, vanilla melee goal true"), "a vanilla variant without a profile (husk) keeps vanilla's AI");
		Stage.expect(r.contains("old zombie: brain true, reason legacy, elite false"), "a creature saved before Bestiary loads and is taken over, never elite");
		Stage.expect(r.contains("elite reloaded: elite true, affixes [ironbound, swift]") && r.contains("name the "), "an elite saved and loaded keeps its affixes and name");
		st.clearCreatures();
	}

	// ------------------------------------------------------------------ 6. media: tell -> attack, frame by frame

	private static void media(Stage st) {
		Stage.log("--- media");
		film(st, "zombie", "lunge_grab", 4, 2.6, 34);
		film(st, "zombie", "claw", 2, 1.4, 22);
		film(st, "skeleton", "aimed_shot", 2, 8, 28);
		film(st, "spider", "pounce", 2, 5, 28);
		film(st, "spider", "web_spit", 2, 6, 26);
		film(st, "creeper", "fuse", 1, 2.2, 34);
		film(st, "revenant_soldier", "overhead", 2, 1.6, 30);
		film(st, "revenant_soldier", "shield_bash", 2, 1.4, 36);
	}

	/** Freezes the game, makes the creature use {@code move} and screenshots one frame a tick from the side. */
	static void film(Stage st, String name, String move, float danger, double dist, int frames) {
		st.clearCreatures();
		int bot = bot(st, 0.5, 0.5, 180, 4000);
		int id = spawn(st, name, 0.5, 0.5 - dist, danger);
		st.c.waitTicks(3);
		st.s.runOnServer(server -> {
			Mob m = (Mob) entity(server, id);
			m.snapTo(0.5, Y, 0.5 - dist, 0, 0);
			m.getNavigation().stop();
			Brain b = Brain.get(m);
			b.target = entity(server, bot);
			b.lastSeen = server.overworld().getGameTime();
			b.setAwareness(Brain.Awareness.ENGAGED, server.overworld().getGameTime());
			b.nextThink = Long.MAX_VALUE;
		});
		st.cam(4.5, Y + 1.6, 0.5 - dist / 2, 0.5, Y + 0.9, 0.5 - dist / 2);
		st.s.runCommand("tick freeze");
		st.c.waitTicks(2);
		st.s.runOnServer(server -> {
			Mob fm = (Mob) entity(server, id);
			Brain b = fm == null ? null : Brain.get(fm);
			if (b != null) {
				// Whatever it started on its own gives way: the filmed move goes next.
				if (b.move != null) {
					MotionServer.stop(fm, 0);
				}
				b.force(move);
				b.nextThink = 0;
			}
		});
		for (int i = 0; i < frames; i++) {
			st.s.runCommand("tick step 1");
			st.c.waitTicks(2);
			st.c.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "gif_%s_%s_%02d", name, move, i)).disableCounterPrefix());
		}
		st.s.runCommand("tick unfreeze");
		st.c.waitTicks(5);
		Stage.log("filmed " + name + " " + move + ": " + frames + " frames, moves " + moves(id, 0));
		st.playerCam(CameraType.FIRST_PERSON);
		st.clearCreatures();
	}

	// ------------------------------------------------------------------ 7. dedicated server: the pass, G-NET, G-PERF-S

	private static void dedicated(ClientGameTestContext c) {
		Stage.log("--- dedicated");
		Properties props = new Properties();
		int port = 25614;
		props.setProperty("server-port", String.valueOf(port));
		props.setProperty("online-mode", "false");
		props.setProperty("white-list", "false");
		props.setProperty("view-distance", "6");
		props.setProperty("simulation-distance", "6");
		props.setProperty("pause-when-empty-seconds", "0");
		props.setProperty("spawn-protection", "0");
		props.setProperty("level-seed", "20261022");
		props.setProperty("gamemode", "survival");
		props.setProperty("network-compression-threshold", "256");
		try (TestDedicatedServerContext server = c.worldBuilder().createServer(props)) {
			Stage.log("dedicated server up: dedicated " + server.computeOnServer(MinecraftServer::isDedicatedServer));
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				Stage st = new Stage(c, server, "ai_ds");
				buildArena(st);
				server.runCommand("op @a");
				server.runCommand("gamemode survival @a");
				server.runCommand("effect give @a resistance infinite 4 true");
				server.runCommand("effect give @a night_vision infinite 0 true");
				server.runCommand("tp @p 0.5 " + Y + " 0.5 180 0");
				connection.waitForChunksRender();
				c.waitTicks(20);
				st.playerCam(CameraType.FIRST_PERSON);
				dsPass(st);
				dsNet(st);
				dsPerf(st);
			}
		}
	}

	/** A 97 × 97 stone floor at Y - 1 (the perf crowd needs room), force-loaded. */
	static void buildArena(Stage st) {
		for (String cmd : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule spawn_monsters false",
			"gamerule mob_griefing false", "gamerule fall_damage false", "gamerule send_command_feedback false", "time set 18000", "weather clear", "difficulty normal"}) {
			st.s.runCommand(cmd);
		}
		st.s.runCommand("forceload add -64 -64 64 64");
		st.c.waitTicks(10);
		st.s.runCommand("fill -48 " + (Y - 1) + " -48 48 " + (Y - 1) + " 48 minecraft:smooth_stone");
		st.s.runCommand("fill -48 " + Y + " -48 48 " + (Y + 4) + " 48 minecraft:air");
	}

	/** Every creature fights the real player on a dedicated server: tells reach the client, hits land, the codex records. */
	static void dsPass(Stage st) {
		Stage.log("--- dedicated pass");
		String[] names = {"zombie", "skeleton", "spider", "creeper", "revenant_soldier"};
		double[] dist = {3, 10, 5, 3.5, 3.5};
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			st.s.runOnServer(server -> {
				clearMobs(server);
			});
			st.s.runCommand("tp @p 0.5 " + Y + " 0.5 180 0");
			st.s.runOnServer(server -> sp(server).setHealth(20));
			st.c.waitTicks(4);
			long since = now(st);
			int id = spawn(st, name, 0.5, 0.5 - dist[i], 2);
			int clientTells = 0;
			long lastAt = st.c.computeOnClient(mc -> ClientCombat.ms());
			long hurt = 0;
			for (int t = 0; t < 400 && (clientTells == 0 || hurt == 0); t++) {
				st.c.waitTick();
				long seenAfter = lastAt;
				long newest = st.c.computeOnClient(mc -> ClientCombat.TELLS.stream().mapToLong(ClientCombat.Tell::at).filter(a -> a > seenAfter).max().orElse(-1));
				if (newest > 0) {
					clientTells++;
					lastAt = newest;
				}
				int me = st.s.computeOnServer(server -> sp(server).getId());
				hurt = DAMAGE.stream().filter(d -> d.target() == me && d.tick() >= since).count() + HITS.stream().filter(h -> h.target() == me && h.tick() >= since).count();
			}
			String codex = st.s.computeOnServer(server -> Codex.entry(sp(server), "bestiary:" + name).knowledge().name());
			boolean brain = st.s.computeOnServer(server -> server.overworld().getEntity(id) == null
				|| server.overworld().getEntity(id) instanceof Mob m && Brain.get(m) != null);
			Stage.log("dedicated " + name + ": tells on the client " + clientTells + ", hits on the player " + hurt + ", codex " + codex + ", moves " + moves(id, since));
			Stage.expect(brain && clientTells > 0 && hurt > 0 && !codex.equals("NONE"), "dedicated server: " + name + " fights the player with tells the client receives");
		}
		st.c.takeScreenshot(TestScreenshotOptions.of("ai_ds_pass").disableCounterPrefix());
	}

	/** G-NET: a 30-creature fight; bytes the client receives on the wire (compressed), per second. */
	static void dsNet(Stage st) {
		Stage.log("--- G-NET");
		st.s.runOnServer(server -> {
			clearMobs(server);
		});
		st.s.runCommand("tp @p 0.5 " + Y + " 0.5 180 0");
		String[] kinds = {"zombie", "zombie", "zombie", "skeleton", "spider", "revenant_soldier"};
		for (int i = 0; i < 30; i++) {
			double a = i * Math.PI * 2 / 30, r = 6 + (i % 3) * 3;
			spawn(st, kinds[i % kinds.length], 0.5 + Math.cos(a) * r, 0.5 + Math.sin(a) * r, 2);
		}
		st.c.waitTicks(100);
		AtomicLong bytes = new AtomicLong();
		Channel ch = st.c.computeOnClient(mc -> {
			try {
				java.lang.reflect.Field f = net.minecraft.network.Connection.class.getDeclaredField("channel");
				f.setAccessible(true);
				return (Channel) f.get(mc.getConnection().getConnection());
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
		PACKETS.clear();
		MOTION_BY_ID.clear();
		if (ch.pipeline().get("decoder") != null) {
			ch.pipeline().addAfter("decoder", "bestiary_packets", new ChannelInboundHandlerAdapter() {
				@Override
				public void channelRead(ChannelHandlerContext ctx, Object msg) {
					PACKETS.merge(msg.getClass().getSimpleName(), 1, Integer::sum);
					if (msg instanceof net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket mp) {
						MOTION_BY_ID.merge(mp.id(), 1, Integer::sum);
					}
					ctx.fireChannelRead(msg);
				}
			});
		}
		ch.pipeline().addFirst("bestiary_bytes", new ChannelInboundHandlerAdapter() {
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) {
				if (msg instanceof ByteBuf b) {
					bytes.addAndGet(b.readableBytes());
				}
				ctx.fireChannelRead(msg);
			}
		});
		long combat0 = CombatSync.bytes;
		// Bestiary + Fray's own traffic, counted where the server encodes it (the payload bytes before compression).
		AtomicLong ours = new AtomicLong(), encoded = new AtomicLong();
		Channel sch = serverChannel(st);
		boolean counting = watchOurs(sch, ours, encoded);
		long t0 = System.nanoTime();
		long tick0 = now(st);
		if (Boolean.getBoolean("bestiary.netdiag")) {
			for (int k = 0; k < 20; k++) {
				st.c.waitTick();
				Stage.log("netdiag " + st.s.computeOnServer(server -> {
					StringBuilder sb = new StringBuilder();
					int n = 0;
					for (Entity e : server.overworld().getAllEntities()) {
						if (e instanceof Mob mob && mob.getType() == EntityTypes.ZOMBIE && n++ < 4) {
							Brain b = Brain.get(mob);
							Vec3 v = mob.getDeltaMovement();
							sb.append(String.format(Locale.ROOT, "[%d %s v %.4f %.4f %.4f g %s nav %s sync %s] ", mob.getId(), b.position, v.x, v.y, v.z, mob.onGround(),
								mob.getNavigation().isDone() ? "done" : "walk", mob.needsSync));
						}
					}
					return sb.toString();
				}));
			}
		}
		st.c.waitTicks(400);
		long ticks = now(st) - tick0;
		double secs = ticks / 20.0;
		ch.pipeline().remove("bestiary_bytes");
		if (ch.pipeline().get("bestiary_packets") != null) {
			ch.pipeline().remove("bestiary_packets");
		}
		unwatchOurs(sch);
		int engaged = st.s.computeOnServer(server -> {
			int n = 0;
			for (Entity e : server.overworld().getAllEntities()) {
				if (e instanceof Mob m && Brain.get(m) != null && Brain.get(m).awareness == Brain.Awareness.ENGAGED) {
					n++;
				}
			}
			return n;
		});
		String census = st.s.computeOnServer(server -> {
			java.util.Map<String, Integer> m = new java.util.TreeMap<>();
			for (Entity e : server.overworld().getAllEntities()) {
				if (e instanceof Mob mob && Brain.get(mob) != null) {
					Brain b = Brain.get(mob);
					String k = mob.getType().toShortString() + ":" + (b.move != null ? "move" : String.valueOf(b.position)) + (mob.getNavigation().isDone() ? "" : "+walking")
						+ (b.guarding ? "+guard" : "");
					m.merge(k, 1, Integer::sum);
				}
			}
			return m.toString();
		});
		Stage.log("G-NET census at the end: " + census);
		java.util.Map<String, Integer> counts = new java.util.TreeMap<>(PACKETS);
		PACKETS.clear();
		double kbs = bytes.get() / 1024.0 / secs;
		double combat = (CombatSync.bytes - combat0) / 1024.0 / secs;
		Stage.log("G-NET packets in 20 s by type (top): " + top(counts, 10));
		String byType = st.c.computeOnClient(mc -> {
			java.util.Map<String, Integer> m = new java.util.TreeMap<>();
			MOTION_BY_ID.forEach((id, n) -> {
				Entity e = mc.level.getEntity(id);
				m.merge(e == null ? "gone" : e.getType().toShortString(), n, Integer::sum);
			});
			return top(m, 8);
		});
		MOTION_BY_ID.clear();
		Stage.log("G-NET motion packets by entity type: " + byType);
		Stage.log(String.format(Locale.ROOT, "G-NET: 30 creatures (%d engaged) fighting the player for %.1f s (%.1f s wall): %.2f KB/s down on the wire (Fray combat payloads %.2f KB/s before compression)",
			engaged, secs, (System.nanoTime() - t0) / 1e9, kbs, combat));
		Stage.expect(engaged >= 25, "G-NET: the 30 creatures are in the fight (" + engaged + " engaged)");
		double oursKbs = ours.get() / 1024.0 / secs;
		Stage.log(String.format(Locale.ROOT, "G-NET: Bestiary + Fray payloads %.2f KB/s (encoded, before compression; %s), of %.2f KB/s the server encoded for the player",
			oursKbs, counting ? "counted on the server's connection" : "NOT COUNTED: no encoder in the pipeline", encoded.get() / 1024.0 / secs));
		st.c.takeScreenshot(TestScreenshotOptions.of("ai_ds_net_fight").disableCounterPrefix());
		// The same count of vanilla variants Bestiary doesn't run (husks, strays, cave spiders), for scale.
		st.s.runOnServer(server -> {
			clearMobs(server);
			EntityType<?>[] vanilla = {EntityTypes.HUSK, EntityTypes.HUSK, EntityTypes.HUSK, EntityTypes.STRAY, EntityTypes.CAVE_SPIDER, EntityTypes.ZOMBIE_VILLAGER};
			for (int i = 0; i < 30; i++) {
				double a = i * Math.PI * 2 / 30, r = 6 + (i % 3) * 3;
				Entity e = vanilla[i % vanilla.length].create(server.overworld(), EntitySpawnReason.COMMAND);
				if (e instanceof Mob m) {
					m.snapTo(0.5 + Math.cos(a) * r, Y, 0.5 + Math.sin(a) * r, 0, 0);
					m.finalizeSpawn(server.overworld(), server.overworld().getCurrentDifficultyAt(m.blockPosition()), EntitySpawnReason.COMMAND, null);
					m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
					if (m.getMainHandItem().is(net.minecraft.tags.ItemTags.SPEARS)) {
						m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					}
					m.setPersistenceRequired();
					server.overworld().addFreshEntity(m);
				}
			}
		});
		st.c.waitTicks(100);
		AtomicLong vbytes = new AtomicLong();
		ch.pipeline().addFirst("bestiary_bytes", new ChannelInboundHandlerAdapter() {
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object msg) {
				if (msg instanceof ByteBuf b) {
					vbytes.addAndGet(b.readableBytes());
				}
				ctx.fireChannelRead(msg);
			}
		});
		long vt0 = now(st);
		st.c.waitTicks(400);
		double vsecs = (now(st) - vt0) / 20.0;
		ch.pipeline().remove("bestiary_bytes");
		double baseline = vbytes.get() / 1024.0 / vsecs;
		Stage.log(String.format(Locale.ROOT, "G-NET baseline: 30 vanilla variants Bestiary doesn't run (husks, strays, cave spiders, zombie villagers) on the same player: %.2f KB/s down",
			baseline));
		// The gate (changed 2026-10-22, docs/AI.md §6): Bestiary + Fray's own traffic within 6 KB/s, and the whole wire no more
		// than the same scene with vanilla AI costs. Both numbers are reported.
		boolean met = counting && oursKbs <= 6.0 && kbs <= baseline;
		Stage.log(String.format(Locale.ROOT, "GATE G-NET %s: Bestiary + Fray %.2f KB/s (gate 6); total %.2f KB/s down per player against the vanilla-AI baseline %.2f",
			met ? "met" : "NOT MET", oursKbs, kbs, baseline));
		Stage.expect(met, String.format(Locale.ROOT, "G-NET: Bestiary + Fray %.2f KB/s <= 6 and total %.2f <= vanilla-AI baseline %.2f KB/s", oursKbs, kbs, baseline));
	}

	/** The server side of the local player's connection (in-process dedicated server). */
	static Channel serverChannel(Stage st) {
		return st.s.computeOnServer(server -> {
			try {
				Object listener = sp(server).connection;
				java.lang.reflect.Field cf = null;
				for (Class<?> k = listener.getClass(); k != null && cf == null; k = k.getSuperclass()) {
					for (java.lang.reflect.Field f : k.getDeclaredFields()) {
						if (f.getType() == net.minecraft.network.Connection.class) {
							cf = f;
						}
					}
				}
				cf.setAccessible(true);
				net.minecraft.network.Connection conn = (net.minecraft.network.Connection) cf.get(listener);
				java.lang.reflect.Field f = net.minecraft.network.Connection.class.getDeclaredField("channel");
				f.setAccessible(true);
				return (Channel) f.get(conn);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Counts the encoded size of every packet the server sends the player ({@code encoded}) and of Bestiary's and Fray's own
	 * payloads ({@code ours}): a handler after the encoder sees each packet, one before it sees its bytes.
	 */
	static boolean watchOurs(Channel ch, AtomicLong ours, AtomicLong encoded) {
		if (ch.pipeline().get("encoder") == null) {
			Stage.log("G-NET: the server pipeline has no encoder: " + ch.pipeline().names());
			return false;
		}
		boolean[] mine = new boolean[1];
		ch.pipeline().addAfter("encoder", "bestiary_ours_pkt", new io.netty.channel.ChannelOutboundHandlerAdapter() {
			@Override
			public void write(ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) throws Exception {
				mine[0] = msg instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket cp
					&& (cp.payload().type().id().getNamespace().equals("fray") || cp.payload().type().id().getNamespace().equals("bestiary"));
				super.write(ctx, msg, promise);
			}
		});
		ch.pipeline().addBefore("encoder", "bestiary_ours_bytes", new io.netty.channel.ChannelOutboundHandlerAdapter() {
			@Override
			public void write(ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) throws Exception {
				if (msg instanceof ByteBuf b) {
					encoded.addAndGet(b.readableBytes());
					if (mine[0]) {
						ours.addAndGet(b.readableBytes());
					}
				}
				mine[0] = false;
				super.write(ctx, msg, promise);
			}
		});
		return true;
	}

	static void unwatchOurs(Channel ch) {
		for (String n : new String[] {"bestiary_ours_pkt", "bestiary_ours_bytes"}) {
			if (ch.pipeline().get(n) != null) {
				ch.pipeline().remove(n);
			}
		}
	}

	static void clearMobs(MinecraftServer server) {
		List<Entity> gone = new ArrayList<>();
		for (Entity e : server.overworld().getAllEntities()) {
			if (e instanceof Mob) {
				gone.add(e);
			}
		}
		gone.forEach(Entity::discard);
	}

	static final java.util.Map<String, Integer> PACKETS = new java.util.concurrent.ConcurrentHashMap<>();
	static final java.util.Map<Integer, Integer> MOTION_BY_ID = new java.util.concurrent.ConcurrentHashMap<>();

	static String top(java.util.Map<String, Integer> m, int n) {
		return m.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(n).map(e -> e.getKey() + " " + e.getValue())
			.collect(java.util.stream.Collectors.joining(", "));
	}

	/** G-PERF-S: 200 engaged creatures around 4 bot players on a dedicated server. */
	static void dsPerf(Stage st) {
		Stage.log("--- G-PERF-S");
		st.s.runOnServer(server -> {
			clearMobs(server);
		});
		// The real player watches from above as a spectator (spectators aren't fought).
		st.s.runCommand("gamemode spectator @p");
		st.s.runCommand("tp @p 0.5 " + (Y + 30) + " 0.5 0 90");
		int[][] anchors = {{-14, -14}, {14, -14}, {-14, 14}, {14, 14}};
		List<Integer> bots = new ArrayList<>();
		for (int[] a : anchors) {
			bots.add(bot(st, a[0] + 0.5, a[1] + 0.5, 0, 100000));
		}
		String[] kinds = {"zombie", "zombie", "zombie", "zombie", "skeleton", "skeleton", "skeleton", "spider", "spider", "revenant_soldier"};
		java.util.Random rnd = new java.util.Random(200);
		for (int i = 0; i < 200; i++) {
			int[] a = anchors[i % 4];
			double ang = rnd.nextDouble() * Math.PI * 2, r = 4 + rnd.nextDouble() * 12;
			spawnFacing(st, kinds[i % kinds.length], a[0] + 0.5 + Math.cos(ang) * r, a[1] + 0.5 + Math.sin(ang) * r, 2, false, List.of(), 0, a[0] + 0.5, a[1] + 0.5);
			if (i % 20 == 19) {
				st.c.waitTick();
			}
		}
		// Bots keep moving like players do (small circles), so the crowd has to keep repathing.
		st.c.waitTicks(100);
		st.s.runOnServer(server -> {
			Brain.perceptionNanos = Brain.tacticsNanos = Brain.executorNanos = 0;
			Brain.perceptionRuns = Brain.tacticsRuns = Brain.executorRuns = 0;
			MotionServer.resetCost();
			Statuses.tickNanos = 0;
			Pipeline.dealNanos = 0;
			Pipeline.deals = 0;
		});
		long tick0 = now(st);
		long wall0 = System.nanoTime();
		List<Double> tickMs = new ArrayList<>();
		int maxEngaged = 0, minEngaged = Integer.MAX_VALUE;
		for (int t = 0; t < 600; t++) {
			int tt = t;
			st.s.runOnServer(server -> {
				for (int k = 0; k < bots.size(); k++) {
					LivingEntity b = entity(server, bots.get(k));
					double ang = tt * 0.05 + k;
					b.teleportTo(anchors[k][0] + 0.5 + Math.cos(ang) * 3, Y, anchors[k][1] + 0.5 + Math.sin(ang) * 3);
				}
			});
			st.c.waitTick();
			if (t % 20 == 0) {
				int[] eg = st.s.computeOnServer(server -> {
					int n = 0, all = 0;
					for (Entity e : server.overworld().getAllEntities()) {
						if (e instanceof Mob m && Brain.get(m) != null) {
							all++;
							if (Brain.get(m).awareness == Brain.Awareness.ENGAGED) {
								n++;
							}
						}
					}
					return new int[] {n, all};
				});
				maxEngaged = Math.max(maxEngaged, eg[0]);
				minEngaged = Math.min(minEngaged, eg[0]);
				double[] times = st.s.computeOnServer(server -> {
					long[] ns = server.getTickTimesNanos();
					double[] out = new double[ns.length];
					for (int i = 0; i < ns.length; i++) {
						out[i] = ns[i] / 1e6;
					}
					return out;
				});
				if (t >= 100) {
					for (double v : times) {
						tickMs.add(v);
					}
				}
			}
		}
		long ticks = now(st) - tick0;
		double wall = (System.nanoTime() - wall0) / 1e9;
		double[] cost = st.s.computeOnServer(server -> new double[] {Brain.perceptionNanos / 1e6 / Math.max(1, ticks), Brain.tacticsNanos / 1e6 / Math.max(1, ticks),
			Brain.executorNanos / 1e6 / Math.max(1, ticks), MotionServer.tickNanos / 1e6 / Math.max(1, ticks), Statuses.tickNanos / 1e6 / Math.max(1, ticks),
			Pipeline.dealNanos / 1e6 / Math.max(1, ticks), server.getAverageTickTimeNanos() / 1e6, Brain.perceptionRuns, Brain.tacticsRuns, Brain.executorRuns});
		double bestiary = cost[0] + cost[1] + cost[2];
		double fray = cost[3] + cost[4] + cost[5];
		double mean = tickMs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
		List<Double> sorted = new ArrayList<>(tickMs);
		sorted.sort(Double::compare);
		double p95 = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.95));
		Stage.log(String.format(Locale.ROOT,
			"G-PERF-S: 200 creatures (%d-%d engaged) around 4 bots for %d ticks (%.1f s wall, %.1f TPS): Bestiary AI %.3f ms a tick (perception %.3f, tactics %.3f, executor incl. path requests %.3f); Fray (motion %.3f, statuses %.3f, pipeline %.3f) %.3f ms; Bestiary+Fray %.3f ms; whole server tick mean %.2f ms, p95 %.2f ms (vanilla average %.2f)",
			minEngaged, maxEngaged, ticks, wall, ticks / wall, bestiary, cost[0], cost[1], cost[2], cost[3], cost[4], cost[5], fray, bestiary + fray, mean, p95, cost[6]));
		Stage.log(String.format(Locale.ROOT, "G-PERF-S runs: perception %.0f, tactics %.0f, executor %.0f", cost[7], cost[8], cost[9]));
		Stage.expect(minEngaged >= 180, "G-PERF-S: the crowd stays engaged (" + minEngaged + " of 200 at the least)");
		Stage.expect(bestiary + fray <= 6.0, String.format(Locale.ROOT, "G-PERF-S: Bestiary (with Fray) %.2f ms a tick (gate 6)", bestiary + fray));
		Stage.expect(mean <= 25.0, String.format(Locale.ROOT, "G-PERF-S: whole tick %.2f ms (gate 25)", mean));
		st.c.takeScreenshot(TestScreenshotOptions.of("ai_ds_perf_crowd").disableCounterPrefix());
		try {
			Files.writeString(Path.of("bestiary-perf.txt"), String.join("\n", Stage.REPORT));
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
	}

	static Timeline timeline(String clip) {
		return MotionServer.timeline(Bestiary.id(clip));
	}

	static boolean isAffix(String s) {
		return Affix.byId(s) != null;
	}

	static float poise(LivingEntity e) {
		return (float) e.getAttributeValue(FrayAttributes.POISE);
	}

	static Mannequin mannequin(Entity e) {
		return (Mannequin) e;
	}
}
