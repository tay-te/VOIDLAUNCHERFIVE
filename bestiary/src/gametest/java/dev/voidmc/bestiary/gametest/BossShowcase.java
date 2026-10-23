package dev.voidmc.bestiary.gametest;

import dev.voidmc.bestiary.Bestiary;
import dev.voidmc.bestiary.ai.Brain;
import dev.voidmc.bestiary.ai.Executor;
import dev.voidmc.bestiary.ai.profile.CreatureProfiles;
import dev.voidmc.bestiary.anim.GallowsKnight;
import dev.voidmc.bestiary.api.BestiaryApi;
import dev.voidmc.bestiary.boss.Arena;
import dev.voidmc.bestiary.boss.BossData;
import dev.voidmc.bestiary.boss.BossEncounter;
import dev.voidmc.bestiary.boss.BossLoot;
import dev.voidmc.bestiary.boss.GallowsYard;
import dev.voidmc.bestiary.boss.GibbetCage;
import dev.voidmc.bestiary.boss.KnightMoves;
import dev.voidmc.bestiary.boss.Lairs;
import dev.voidmc.bestiary.codex.Codex;
import dev.voidmc.fray.api.CombatEvents;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.DamageSpec;
import dev.voidmc.fray.api.Encounter;
import dev.voidmc.fray.api.FrayApi;
import dev.voidmc.fray.api.HitContext;
import dev.voidmc.fray.api.HitFlags;
import dev.voidmc.fray.client.combat.ClientCombat;
import dev.voidmc.fray.motion.MotionHits;
import dev.voidmc.fray.motion.MotionServer;
import dev.voidmc.fray.motion.Timeline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.impl.client.gametest.context.TestDedicatedServerContextImpl;
import net.fabricmc.fabric.impl.client.gametest.util.DedicatedServerImplUtil;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Gallows Knight's game tests (stage 1.5, BESTIARY.md §8, §14): every move's tell comes before its hit by at least
 * its colour's minimum; the phase change fires and tears the cage off; the Break opens a punish window; a fight resets
 * when the arena empties (players left or died) and can't be cheesed from outside; two players scale its health and
 * get their own loot; the cage rolls; media; and on a dedicated server a whole fight against a mannequin player with a
 * relog and a server restart mid-fight (state survives, the knight never gets stuck). Report
 * {@code build/run/clientGameTest/fray-report-boss.txt}.
 * <pre>bash tools/gametest.sh -Dfray.show.only=none -Dbestiary.boss=all</pre>
 * Parts: moves, phase, break, reset, party, cage, media, dedicated.
 */
public class BossShowcase implements FabricClientGameTest {
	private static final Set<String> PARTS = new HashSet<>(Arrays.asList(System.getProperty("bestiary.boss", "").split(",")));
	static final int Y = Stage.Y;
	/** The yard's centre on the test platform. */
	static final BlockPos CENTRE = new BlockPos(0, Y, 0);

	record Hit(int attacker, int target, String clip, long tick, String window) {
	}

	record Dmg(int target, int attacker, float amount, long tick) {
	}

	static final List<Hit> HITS = new CopyOnWriteArrayList<>();
	static final List<Dmg> DAMAGE = new CopyOnWriteArrayList<>();
	static final List<String> PHASES = new CopyOnWriteArrayList<>();
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
		Stage.log("Gallows Knight showcase: parts " + PARTS);
		if (!listening) {
			listening = true;
			MotionHits.RESOLVED.register((hit, window) -> HITS.add(new Hit(hit.attacker().getId(), hit.target().getId(), hit.clip().toString(), hit.serverTick(),
				window.name())));
			ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> DAMAGE.add(new Dmg(entity.getId(),
				source.getEntity() == null ? -1 : source.getEntity().getId(), taken, entity.level().getGameTime())));
			CombatEvents.ENCOUNTER_PHASE.register((e, o) -> PHASES.add(e.boss().getId() + ":" + e.phase()));
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
		if (want("moves") || want("phase") || want("break") || want("reset") || want("party") || want("cage") || want("media")) {
			try (TestSingleplayerContext world = c.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
				s.setSeed("20261022");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
			}).create()) {
				TestServerContext server = world.getServer();
				Stage st = new Stage(c, server, "boss");
				st.build();
				server.runCommand("gamerule spawn_monsters false");
				server.runCommand("gamerule send_command_feedback false");
				server.runCommand("time set 3000");
				// The local player watches in creative (not a fighter: mannequin bots stand in for players).
				server.runCommand("gamemode creative @a");
				server.runCommand("effect give @a night_vision infinite 0 true");
				server.runCommand("tp @p 0.5 " + (Y + 6) + " 26 180 20");
				world.getConnection().waitForChunksRender();
				Arena arena = st.s.computeOnServer(srv -> GallowsYard.place(srv.overworld(), CENTRE));
				Stage.log("gallows yard at " + CENTRE.toShortString() + ": radius " + arena.radius() + ", mound " + arena.mound().orElseThrow().toShortString()
					+ " r" + arena.moundRadius() + ", home " + arena.home().toShortString());
				c.waitTicks(20);
				if (want("moves")) {
					moves(st, arena);
				}
				if (want("phase")) {
					phase(st, arena);
				}
				if (want("break")) {
					breakWindow(st, arena);
				}
				if (want("reset")) {
					reset(st, arena);
				}
				if (want("party")) {
					party(st, arena);
				}
				if (want("cage")) {
					cage(st, arena);
				}
				if (want("media")) {
					media(st, arena);
				}
			}
		}
		if (want("dedicated")) {
			dedicated(c);
		}
		Stage.log("problems: " + Stage.PROBLEMS);
		Stage.writeReport("boss");
		if (!Stage.PROBLEMS.isEmpty()) {
			throw new AssertionError("Gallows Knight showcase problems: " + Stage.PROBLEMS);
		}
	}

	// ------------------------------------------------------------------ helpers

	static LivingEntity entity(MinecraftServer server, int id) {
		return (LivingEntity) server.overworld().getEntity(id);
	}

	static GallowsKnight knight(MinecraftServer server, int id) {
		return (GallowsKnight) server.overworld().getEntity(id);
	}

	/** Clears creatures, cages and items from the yard (keeps players and the camera). */
	static void clear(Stage st) {
		st.clearCreatures();
		st.s.runOnServer(server -> {
			for (LivingEntity b : BestiaryApi.bots()) {
				BestiaryApi.removeBot(b);
			}
		});
	}

	/** A lair-less Gallows Knight at the arena's home (the summon command's path). */
	static int spawnKnight(Stage st, Arena arena) {
		return st.s.computeOnServer(server -> {
			Mob m = Lairs.spawn(server.overworld(), GallowsKnight.PROFILE, arena, null);
			if (m == null) {
				throw new AssertionError("couldn't spawn the knight");
			}
			return m.getId();
		});
	}

	/** A mannequin bot standing in for a player (iron armour, a sword, lots of health). */
	static int bot(Stage st, double x, double z, float health) {
		return AiShowcase.bot(st, x, z, 180, health);
	}

	/** Engages the knight on {@code target} and makes it use {@code move} next (a boss script's {@code force}). */
	static void force(Stage st, int knight, int target, String move) {
		st.s.runOnServer(server -> {
			GallowsKnight k = knight(server, knight);
			Brain b = Brain.get(k);
			long now = server.overworld().getGameTime();
			b.target = entity(server, target);
			b.lastSeen = now;
			b.lastKnown = b.target.position();
			b.setAwareness(Brain.Awareness.ENGAGED, now);
			if (b.move != null) {
				MotionServer.stop(k, 0);
			}
			b.force(move);
			b.nextThink = 0;
		});
	}

	/** Puts the knight in phase 2 without the transition (the cage already off). */
	static void phaseTwo(Stage st, int knight) {
		st.s.runOnServer(server -> {
			GallowsKnight k = knight(server, knight);
			BossData d = BossData.get(k);
			d.phase = 2;
			d.caged = false;
			k.setCaged(false);
		});
	}

	/** Holds the knight's thinking so only forced moves run (the move tests). */
	static void quiet(Stage st, int knight, boolean on) {
		st.s.runOnServer(server -> {
			Brain b = Brain.get(knight(server, knight));
			b.nextThink = on ? Long.MAX_VALUE : 0;
		});
	}

	static long now(Stage st) {
		return st.serverTime();
	}

	static int minimum(String colour, boolean ultimate) {
		if (ultimate) {
			return 20;
		}
		return switch (colour) {
			case "orange" -> 10;
			case "red", "purple" -> 14;
			default -> 5;
		};
	}

	static Timeline timeline(String move) {
		return MotionServer.timeline(Bestiary.id("gallows_knight/" + move));
	}

	static void hitFrom(MinecraftServer server, int attacker, int target, float amount, int flags) {
		LivingEntity a = entity(server, attacker);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		HitContext hit = new HitContext(a, sword, Identifier.fromNamespaceAndPath("fray", (flags & HitFlags.HEAVY) != 0 ? "test_heavy" : "test_light"),
			DamageSpec.of(DamageKind.SLASH, amount, amount), flags, 0, null);
		FrayApi.damage().deal(entity(server, target), hit);
	}

	// ------------------------------------------------------------------ 1. each move: its tell, then its hit

	record MoveCase(String move, double dist, boolean phase2, boolean onMound) {
	}

	static void moves(Stage st, Arena arena) {
		Stage.log("--- moves: each move's tell precedes its hit by its colour's minimum");
		List<MoveCase> cases = List.of(new MoveCase("hook_cut", 1.9, false, false), new MoveCase("two_cut", 1.9, false, false),
			new MoveCase("slam", 1.8, false, false), new MoveCase("shoulder_charge", 4.5, false, false), new MoveCase("chain_sweep", 2.0, false, false),
			new MoveCase("kick", 1.3, false, false), new MoveCase("three_cut", 1.9, true, false), new MoveCase("chain_lash", 7.0, true, false),
			new MoveCase("the_hanging", 0, true, true));
		for (MoveCase mc : cases) {
			clear(st);
			int k = spawnKnight(st, arena);
			Vec3 home = arena.homeVec();
			double bx = home.x, bz = home.z + mc.dist();
			if (mc.onMound()) {
				BlockPos m = arena.mound().orElseThrow();
				bx = m.getX() + 0.5;
				bz = m.getZ() + 1.5;
			}
			int bot = bot(st, bx, bz, 6000);
			double fbx = bx, fbz = bz;
			st.s.runOnServer(server -> entity(server, bot).teleportTo(fbx, mc.onMound() ? arena.mound().orElseThrow().getY() : Y, fbz));
			if (mc.phase2()) {
				phaseTwo(st, k);
			}
			st.c.waitTicks(4);
			quiet(st, k, true);
			long since = now(st);
			force(st, k, bot, mc.move());
			Timeline tl = timeline(mc.move());
			st.c.waitTicks(tl.ticks + 6);
			boolean ultimate = mc.move().equals("the_hanging");
			Executor.Tell tell = Executor.TELLS.stream().filter(t -> t.attacker() == k && t.tick() >= since - 1 && t.move().equals(mc.move())).findFirst().orElse(null);
			List<Long> hitTicks = new ArrayList<>();
			for (Hit h : HITS) {
				if (h.attacker() == k && h.target() == bot && h.tick() >= since && h.clip().endsWith(mc.move())) {
					hitTicks.add(h.tick());
				}
			}
			for (KnightMoves.Special sp : KnightMoves.SPECIALS) {
				if (sp.target() == bot && sp.tick() >= since && sp.move().equals(mc.move()) && !sp.outcome().equals("dodged") && !sp.outcome().equals("missed")) {
					hitTicks.add(sp.tick());
				}
			}
			long firstHit = hitTicks.stream().mapToLong(Long::longValue).min().orElse(-1);
			float hp = st.health(bot);
			String colour = tell == null ? "-" : tell.colour();
			long lead = tell == null || firstHit < 0 ? -1 : firstHit - tell.tick();
			int min = minimum(colour, ultimate);
			Stage.log(String.format(Locale.ROOT, "%s: tell %s (%s ticks ahead by the clip), first hit %d ticks after the tell, %d hits, mannequin %.0f/6000", mc.move(),
				colour, tell == null ? "-" : String.valueOf(tell.windup()), lead, hitTicks.size(), hp));
			Stage.expect(tell != null && colour.equals(tl.tellColour), mc.move() + ": tells " + tl.tellColour);
			Stage.expect(firstHit >= 0 && hp < 6000, mc.move() + ": lands on the mannequin");
			Stage.expect(lead >= min, mc.move() + ": the hit comes " + lead + " ticks after its " + colour + " tell (minimum " + min + ")");
			if (mc.move().equals("chain_lash")) {
				double d = st.s.computeOnServer(server -> entity(server, bot).distanceTo(knight(server, k)));
				Stage.log(String.format(Locale.ROOT, "chain lash: mannequin pulled from %.1f to %.1f blocks", mc.dist(), d));
				Stage.expect(d < mc.dist() - 2, "chain lash pulls the target in");
			}
			if (mc.move().equals("shoulder_charge")) {
				double moved = st.s.computeOnServer(server -> knight(server, k).position().distanceTo(home));
				Stage.log(String.format(Locale.ROOT, "shoulder charge: the knight travelled %.1f blocks", moved));
				Stage.expect(moved >= 2.5, "the shoulder charge covers about three blocks");
			}
		}
		// The kick crushes a guard: its strikes carry the guard-crush flag.
		Stage.expect(timeline("kick").events.stream().anyMatch(e -> e.name().equals("guard_crush")), "the kick is a guard crusher (×2 block cost)");
		clear(st);
	}

	// ------------------------------------------------------------------ 2. the phase change

	static void phase(Stage st, Arena arena) {
		Stage.log("--- phase: at 55 % the knight tears its cage off; phase 2's moves open");
		clear(st);
		int k = spawnKnight(st, arena);
		Vec3 home = arena.homeVec();
		int bot = bot(st, home.x, home.z + 3, 6000);
		st.c.waitTicks(30);
		boolean active = st.s.computeOnServer(server -> BossData.get(knight(server, k)).active);
		Stage.expect(active, "the fight starts when a fighter enters the arena and the knight sees it");
		long since = now(st);
		int hitsBefore = (int) HITS.stream().filter(h -> h.attacker() == k).count();
		// Down to 56 %, then one hit across the threshold.
		st.s.runOnServer(server -> knight(server, k).setHealth(knight(server, k).getMaxHealth() * 0.555F));
		st.s.runOnServer(server -> hitFrom(server, bot, k, 20, HitFlags.HEAVY));
		long tearAt = -1;
		boolean transitioned = false;
		for (int t = 0; t < 200 && !transitioned; t += 2) {
			st.c.waitTicks(2);
			int[] r = st.s.computeOnServer(server -> {
				GallowsKnight kn = knight(server, k);
				MotionServer.Running run = MotionServer.running(kn);
				return new int[] {BossData.get(kn).phase, BossData.get(kn).caged ? 1 : 0, kn.script.transitions,
					run != null && run.clip().getPath().endsWith("tear_cage") ? 1 : 0};
			});
			if (r[3] == 1 && tearAt < 0) {
				tearAt = now(st);
			}
			transitioned = r[0] == 2 && r[1] == 0 && r[2] == 1;
		}
		st.c.waitTicks(40);
		int cages = st.s.computeOnServer(server -> knight(server, k).cages(server.overworld()).size());
		boolean barPhase = PHASES.stream().anyMatch(p -> p.equals(k + ":1"));
		boolean clientCaged = st.c.computeOnClient(mc -> mc.level.getEntity(k) instanceof GallowsKnight kn && kn.caged());
		long transitionHits = HITS.stream().filter(h -> h.attacker() == k && h.clip().endsWith("tear_cage")).count();
		Stage.log(String.format(Locale.ROOT, "phase: transition clip at %d (+%d), phase 2 %s, cages %d, bar phase notch passed %s, client sees the cage %s, hits from the transition %d",
			tearAt, tearAt < 0 ? -1 : tearAt - since, transitioned, cages, barPhase, clientCaged, transitionHits));
		Stage.expect(transitioned && tearAt > 0, "the phase change fires at 55 % and the knight tears the cage off (the transition move)");
		Stage.expect(cages == 1 && !clientCaged, "the cage is off its back (hidden on the client) and lies in the arena as a hazard");
		Stage.expect(barPhase, "Fray's bar moves to phase 2");
		Stage.expect(transitionHits == 0, "the transition is a moment to breathe: it hits no one");
		// Phase 2's moves are now chosen; phase 1's two-cut isn't.
		st.c.waitTicks(400);
		String used = AiShowcase.moves(k, since);
		Stage.log("phase 2 moves used in 20 s: " + used);
		Stage.expect(!used.contains("two_cut") && (used.contains("three_cut") || used.contains("chain_lash") || used.contains("hook_cut")),
			"phase 2 draws on its own moveset");
		Stage.expect(st.s.computeOnServer(server -> FrayApi.encounters().of(knight(server, k)).phase()) == 1, "the encounter is in phase 2");
		clear(st);
	}

	// ------------------------------------------------------------------ 3. the Break and its punish window

	static void breakWindow(Stage st, Arena arena) {
		Stage.log("--- break: a full Break meter kneels the knight for 60 ticks to punish");
		clear(st);
		int k = spawnKnight(st, arena);
		Vec3 home = arena.homeVec();
		int bot = bot(st, home.x, home.z + 1.8, 6000);
		st.c.waitTicks(30);
		// Fill the meter most of the way, then break it with heavies (poise into the meter, as a fight would).
		st.s.runOnServer(server -> FrayApi.encounters().addBreak(knight(server, k), 0.9F));
		boolean broken = false;
		long brokeAt = -1;
		for (int i = 0; i < 20 && !broken; i++) {
			st.s.runOnServer(server -> hitFrom(server, bot, k, 8, HitFlags.HEAVY));
			st.c.waitTicks(5);
			broken = st.s.computeOnServer(server -> FrayApi.encounters().of(knight(server, k)).broken());
			if (broken) {
				brokeAt = now(st);
			}
		}
		st.c.waitTicks(2);
		String clip = st.s.computeOnServer(server -> {
			MotionServer.Running r = MotionServer.running(knight(server, k));
			return r == null ? "-" : r.clip().getPath();
		});
		float before = st.health(k);
		// The punish window: the mannequin lands hits for 2 s; the knight does nothing back.
		for (int i = 0; i < 8; i++) {
			st.s.runOnServer(server -> hitFrom(server, bot, k, 10, HitFlags.LIGHT));
			st.c.waitTicks(5);
		}
		float after = st.health(k);
		long bk = brokeAt;
		long tellsInWindow = Executor.TELLS.stream().filter(t -> t.attacker() == k && t.tick() > bk && t.tick() < bk + 55).count();
		st.c.waitTicks(40);
		boolean over = st.s.computeOnServer(server -> !FrayApi.encounters().of(knight(server, k)).broken());
		long tellsAfter = Executor.TELLS.stream().filter(t -> t.attacker() == k && t.tick() >= bk + 60).count();
		st.c.waitTicks(60);
		tellsAfter = Executor.TELLS.stream().filter(t -> t.attacker() == k && t.tick() >= bk + 60).count();
		int breaks = st.s.computeOnServer(server -> knight(server, k).script.breaks);
		Stage.log(String.format(Locale.ROOT, "break: broken %s at %d, clip %s, punish hits took %.0f, tells during the window %d, window over %s, attacks again %d, breaks %d",
			broken, brokeAt, clip, before - after, tellsInWindow, over, tellsAfter, breaks));
		Stage.expect(broken && clip.endsWith("broken") && breaks == 1, "a full Break meter kneels the knight (its Broken clip)");
		Stage.expect(before - after > 20 && tellsInWindow == 0, "the Break opens a punish window: hits land, the knight can't answer");
		Stage.expect(over && tellsAfter > 0, "after 60 ticks the meter empties and the knight fights on");
		clear(st);
	}

	// ------------------------------------------------------------------ 4. reset: everyone left, everyone died; no cheese from outside

	static void reset(Stage st, Arena arena) {
		Stage.log("--- reset: the arena empty for the reset time puts the knight back; no cheese from outside");
		int saved = BossEncounter.resetTicks;
		BossEncounter.resetTicks = 100;
		try {
			for (String how : new String[] {"leaves", "dies"}) {
				clear(st);
				int k = spawnKnight(st, arena);
				Vec3 home = arena.homeVec();
				int bot = bot(st, home.x, home.z + 4, how.equals("dies") ? 40 : 6000);
				st.c.waitTicks(30);
				st.s.runOnServer(server -> hitFrom(server, bot, k, 30, HitFlags.HEAVY));
				st.c.waitTicks(40);
				float hurt = st.health(k);
				long resets0 = BossEncounter.resetsDone;
				if (how.equals("leaves")) {
					st.s.runOnServer(server -> entity(server, bot).teleportTo(CENTRE.getX() + 0.5, Y, CENTRE.getZ() + arena.radius() + 6.5));
					st.c.waitTicks(10);
					// No cheese: from outside, its hits do nothing.
					float h0 = st.health(k);
					st.s.runOnServer(server -> hitFrom(server, bot, k, 30, HitFlags.HEAVY));
					st.s.runOnServer(server -> knight(server, k).hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(entity(server, bot)), 30));
					st.s.runOnServer(server -> knight(server, k).hurtServer(server.overworld(), server.overworld().damageSources().lava(), 30));
					st.c.waitTicks(2);
					float h1 = st.health(k);
					Stage.log(String.format(Locale.ROOT, "no cheese: a Fray heavy, a vanilla hit from outside and lava: %.1f -> %.1f", h0, h1));
					Stage.expect(Math.abs(h0 - h1) < 0.01, "no cheese: damage from outside the arena (and lava) does nothing");
				} else {
					st.s.runOnServer(server -> entity(server, bot).kill(server.overworld()));
				}
				boolean dropped = false;
				for (int t = 0; t < 40 && !dropped; t++) {
					st.c.waitTick();
					dropped = st.s.computeOnServer(server -> {
						Brain b = Brain.get(knight(server, k));
						return b.target == null;
					});
				}
				st.c.waitTicks(BossEncounter.resetTicks + 20);
				Object[] r = st.s.computeOnServer(server -> {
					GallowsKnight kn = knight(server, k);
					BossData d = BossData.get(kn);
					return new Object[] {d.active, kn.getHealth(), kn.getMaxHealth(), kn.position().distanceTo(arena.homeVec()), FrayApi.encounters().of(kn) == null,
						d.participants.size()};
				});
				Stage.log(String.format(Locale.ROOT, "reset (%s): hurt to %.0f, target dropped %s, then active %s, %.0f/%.0f, %.1f from its spot, bar gone %s, participants %s, resets +%d",
					how, hurt, dropped, r[0], (float) r[1], (float) r[2], (double) r[3], r[4], r[5], BossEncounter.resetsDone - resets0));
				Stage.expect(dropped, "reset (" + how + "): the knight lets go of a target outside the arena or dead");
				Stage.expect(!(boolean) r[0] && (float) r[1] >= (float) r[2] - 0.01 && (double) r[3] < 1.5 && (boolean) r[4] && BossEncounter.resetsDone - resets0 == 1,
					"reset (" + how + "): full health, back on its spot, the bar ended");
			}
		} finally {
			BossEncounter.resetTicks = saved;
		}
		clear(st);
	}

	// ------------------------------------------------------------------ 5. two players: health and personal loot

	static void party(Stage st, Arena arena) {
		Stage.log("--- party: two players scale its health ×1.6; each who earned it gets their own loot");
		clear(st);
		int k = spawnKnight(st, arena);
		Vec3 home = arena.homeVec();
		int a = bot(st, home.x - 1.5, home.z + 3, 8000);
		st.c.waitTicks(20);
		// The same heavy hit, solo, with two and with three players in the arena: the knight's toughness grows ×1.6, ×2.2.
		float solo = loss(st, a, k);
		int b = bot(st, home.x + 1.5, home.z + 3, 8000);
		st.c.waitTicks(20);
		float duo = loss(st, a, k);
		int party2 = st.s.computeOnServer(server -> BossData.get(knight(server, k)).party);
		int idle = bot(st, home.x, home.z + 9, 8000);
		st.c.waitTicks(20);
		float trio = loss(st, a, k);
		float max = st.s.computeOnServer(server -> knight(server, k).getMaxHealth());
		Stage.log(String.format(Locale.ROOT, "party: a heavy takes %.2f solo, %.2f with two (party %d, ×%.2f), %.2f with three (×%.2f); max health %.0f", solo, duo,
			party2, solo / duo, trio, solo / trio, max));
		Stage.expect(Math.abs(max - 900) < 1 && party2 == 2 && Math.abs(solo / duo - 1.6F) < 0.05, "two players: the knight is 1.6× as tough (900 health → 1440 worth)");
		Stage.expect(Math.abs(solo / trio - 2.2F) < 0.07, "a third player: 2.2× as tough");
		// A deals most of the damage, B 15 %, the third almost nothing (and is in the arena for under 30 s): A and B earn loot.
		UUID[] ids = st.s.computeOnServer(server -> new UUID[] {entity(server, a).getUUID(), entity(server, b).getUUID(), entity(server, idle).getUUID()});
		int awards0 = BossLoot.AWARDS.size();
		for (int i = 0; i < 6; i++) {
			st.s.runOnServer(server -> {
				hitFrom(server, a, k, 40, HitFlags.HEAVY);
				hitFrom(server, b, k, 18, HitFlags.LIGHT);
				hitFrom(server, idle, k, 0.3F, HitFlags.LIGHT);
			});
			st.c.waitTicks(10);
		}
		st.s.runOnServer(server -> {
			GallowsKnight kn = knight(server, k);
			kn.setHealth(2);
			hitFrom(server, a, k, 40, HitFlags.HEAVY);
		});
		st.c.waitTicks(30);
		List<BossLoot.Award> awards = BossLoot.AWARDS.subList(awards0, BossLoot.AWARDS.size());
		Map<UUID, Integer> items = st.s.computeOnServer(server -> {
			Map<UUID, Integer> m = new java.util.HashMap<>();
			for (Entity e : server.overworld().getAllEntities()) {
				if (e instanceof ItemEntity ie && ie.getOwner() == null) {
					UUID t = targetOf(ie);
					if (t != null) {
						m.merge(t, ie.getItem().getCount(), Integer::sum);
					}
				}
			}
			return m;
		});
		Stage.log("party loot: awards " + awards.stream().map(x -> x.whoName() + " " + x.items()).toList() + "; targeted drops " + items);
		boolean aGot = awards.stream().anyMatch(x -> x.who().equals(ids[0])), bGot = awards.stream().anyMatch(x -> x.who().equals(ids[1])),
			idleGot = awards.stream().anyMatch(x -> x.who().equals(ids[2]));
		Stage.expect(aGot && bGot && !idleGot, "personal loot: both players who earned it roll, the one who didn't (under 10 % and under 30 s) doesn't");
		Stage.expect(items.getOrDefault(ids[0], 0) >= 3 && items.getOrDefault(ids[1], 0) >= 3 && !items.containsKey(ids[2]),
			"personal loot: each roll is dropped for its owner only");
		boolean felled = st.s.computeOnServer(server -> server.overworld().getEntity(k) == null || !server.overworld().getEntity(k).isAlive());
		Stage.expect(felled, "the knight falls");
		clear(st);
	}

	/** Health the knight loses to one fixed heavy from {@code attacker}. */
	static float loss(Stage st, int attacker, int knight) {
		return st.s.computeOnServer(server -> {
			GallowsKnight k = knight(server, knight);
			float h = k.getHealth();
			hitFrom(server, attacker, knight, 30, HitFlags.HEAVY);
			return h - k.getHealth();
		});
	}

	static UUID targetOf(ItemEntity ie) {
		try {
			java.lang.reflect.Field f = ItemEntity.class.getDeclaredField("target");
			f.setAccessible(true);
			return (UUID) f.get(ie);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ 6. the cage hazard

	static void cage(Stage st, Arena arena) {
		Stage.log("--- cage: the torn-off cage rattles (orange tell) and rolls at a player");
		clear(st);
		int k = spawnKnight(st, arena);
		Vec3 home = arena.homeVec();
		int bot = bot(st, home.x + 3, home.z + 4, 6000);
		st.c.waitTicks(20);
		quiet(st, k, true);
		st.s.runOnServer(server -> {
			GallowsKnight kn = knight(server, k);
			BossData.get(kn).phase = 2;
			kn.script.tearOff(server.overworld());
		});
		st.c.waitTicks(60);
		// The mannequin stands six blocks from where the cage came to rest, towards the yard's middle.
		st.s.runOnServer(server -> {
			GibbetCage c = knight(server, k).cages(server.overworld()).getFirst();
			Vec3 to = new Vec3(CENTRE.getX() + 0.5, Y, CENTRE.getZ() + 0.5).subtract(c.position()).multiply(1, 0, 1).normalize();
			Vec3 at = c.position().add(to.scale(6));
			entity(server, bot).teleportTo(at.x, Y, at.z);
		});
		st.c.waitTicks(4);
		long since = now(st);
		st.s.runOnServer(server -> {
			for (GibbetCage c : knight(server, k).cages(server.overworld())) {
				c.provoke();
			}
		});
		for (int t = 0; t < 90; t += 10) {
			st.c.waitTicks(10);
			Stage.log("cage: " + st.s.computeOnServer(server -> {
				GibbetCage c = knight(server, k).cages(server.overworld()).getFirst();
				return String.format(Locale.ROOT, "%s at %.1f %.1f %.1f, %.1f from the mannequin", c.stateName(), c.getX(), c.getY(), c.getZ(),
					c.distanceTo(entity(server, bot)));
			}));
		}
		int rolls = st.s.computeOnServer(server -> knight(server, k).cages(server.overworld()).stream().mapToInt(c -> c.rolls).sum());
		long struck = DAMAGE.stream().filter(d -> d.target() == bot && d.attacker() == k && d.tick() >= since).count();
		long first = DAMAGE.stream().filter(d -> d.target() == bot && d.attacker() == k && d.tick() >= since).mapToLong(Dmg::tick).min().orElse(-1);
		Stage.log(String.format(Locale.ROOT, "cage: rolls %d, struck the mannequin %d times, first %d ticks after it was provoked", rolls, struck, first < 0 ? -1 : first - since));
		Stage.expect(rolls >= 1 && struck >= 1 && first - since >= GibbetCage.WARN_TICKS, "the cage rattles for 20 ticks, then rolls into the player");
		clear(st);
	}

	// ------------------------------------------------------------------ 7. media

	static void media(Stage st, Arena arena) {
		Stage.log("--- media");
		String[][] films = {{"hook_cut", "1.9", "1"}, {"two_cut", "1.9", "1"}, {"slam", "1.8", "1"}, {"shoulder_charge", "4.5", "1"}, {"chain_sweep", "2.0", "1"},
			{"kick", "1.3", "1"}, {"three_cut", "1.9", "2"}, {"chain_lash", "7", "2"}, {"the_hanging", "0", "2"}};
		for (String[] f : films) {
			film(st, arena, f[0], Double.parseDouble(f[1]), f[2].equals("2"));
		}
		filmTransition(st, arena);
		filmBreak(st, arena);
		arenaStill(st, arena);
	}

	/** One frame a tick from the side, from the move's start to the end of its strike, the game frozen and stepped. */
	static void film(Stage st, Arena arena, String move, double dist, boolean phase2) {
		clear(st);
		int k = spawnKnight(st, arena);
		boolean mound = move.equals("the_hanging");
		// Filmed in the open south half of the yard (the gallows behind the knight for the Hanging).
		Vec3 home = mound ? arena.homeVec() : new Vec3(CENTRE.getX() + 0.5, Y, CENTRE.getZ() + 2.5);
		BlockPos m = arena.mound().orElseThrow();
		double bx = mound ? m.getX() + 0.5 : home.x, bz = mound ? m.getZ() + 1.5 : home.z + dist;
		int bot = bot(st, bx, bz, 6000);
		st.s.runOnServer(server -> entity(server, bot).teleportTo(bx, mound ? m.getY() : Y, bz));
		if (phase2) {
			phaseTwo(st, k);
		}
		st.c.waitTicks(4);
		quiet(st, k, true);
		st.s.runOnServer(server -> {
			GallowsKnight kn = knight(server, k);
			kn.snapTo(home.x, home.y, home.z, 0, 0);
			kn.getNavigation().stop();
		});
		double midZ = mound ? (home.z + bz) / 2 : home.z + dist / 2;
		if (mound) {
			st.cam(home.x - 7.5, Y + 3.2, home.z + 2.5, home.x, Y + 1.8, (home.z + m.getZ()) / 2.0);
		} else {
			double back = Math.max(4.8, dist * 0.7 + 3.6);
			st.cam(home.x + back, Y + 1.9, midZ, home.x, Y + 1.35, midZ);
		}
		st.s.runCommand("tick freeze");
		st.c.waitTicks(2);
		force(st, k, bot, move);
		Timeline tl = timeline(move);
		int last = tl.strikes().length == 0 ? tl.activeEnd + 4 : tl.strikes()[tl.strikes().length - 1][1] + 4;
		for (int i = 0; i < last + 1; i++) {
			st.s.runCommand("tick step 1");
			st.c.waitTicks(2);
			st.c.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "gif_knight_%s_%02d", move, i)).disableCounterPrefix());
		}
		st.s.runCommand("tick unfreeze");
		st.c.waitTicks(5);
		Stage.log("filmed " + move + ": " + (last + 1) + " frames");
		st.playerCam(CameraType.FIRST_PERSON);
	}

	static void filmTransition(Stage st, Arena arena) {
		clear(st);
		int k = spawnKnight(st, arena);
		Vec3 home = arena.homeVec();
		int bot = bot(st, home.x, home.z + 4, 6000);
		st.c.waitTicks(20);
		quiet(st, k, true);
		st.cam(home.x + 4.2, Y + 2.0, home.z + 3.2, home.x, Y + 1.5, home.z);
		st.s.runOnServer(server -> {
			GallowsKnight kn = knight(server, k);
			BossData.get(kn).phase = 2;
			kn.snapTo(home.x, home.y, home.z, 0, 0);
		});
		st.s.runCommand("tick freeze");
		st.c.waitTicks(2);
		force(st, k, bot, "tear_cage");
		int frames = timeline("tear_cage").ticks + 16;
		for (int i = 0; i < frames; i++) {
			st.s.runCommand("tick step 1");
			st.c.waitTicks(2);
			if (i % 2 == 0) {
				st.c.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "gif_knight_transition_%02d", i / 2)).disableCounterPrefix());
			}
		}
		st.s.runCommand("tick unfreeze");
		st.c.waitTicks(5);
		st.playerCam(CameraType.FIRST_PERSON);
		Stage.log("filmed the transition: " + (frames + 1) / 2 + " frames");
	}

	static void filmBreak(Stage st, Arena arena) {
		clear(st);
		int k = spawnKnight(st, arena);
		Vec3 home = arena.homeVec();
		int bot = bot(st, home.x, home.z + 1.8, 6000);
		st.c.waitTicks(20);
		quiet(st, k, true);
		st.cam(home.x + 4.4, Y + 1.8, home.z + 1.2, home.x, Y + 1.2, home.z + 1);
		st.s.runOnServer(server -> FrayApi.encounters().addBreak(knight(server, k), 0.99F));
		st.s.runCommand("tick freeze");
		st.c.waitTicks(2);
		for (int i = 0; i < 44; i++) {
			int ii = i;
			st.s.runOnServer(server -> {
				LivingEntity m = entity(server, bot);
				m.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, knight(server, k).getEyePosition());
				if (ii == 1 || ii > 10 && ii % 6 == 0) {
					// The mannequin swings (its sword clip) and lands the hits of the punish.
					MotionServer.play(m, Identifier.fromNamespaceAndPath("bestiary", ii == 1 ? "player/sword/heavy" : "player/sword/light_1"), 1);
				}
				if (ii == 6 || ii > 10 && ii % 6 == 3) {
					hitFrom(server, bot, k, ii == 6 ? 12 : 8, ii == 6 ? HitFlags.HEAVY : HitFlags.LIGHT);
				}
			});
			st.s.runCommand("tick step 1");
			st.c.waitTicks(2);
			st.c.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "gif_knight_break_%02d", i)).disableCounterPrefix());
		}
		st.s.runCommand("tick unfreeze");
		st.c.waitTicks(5);
		st.playerCam(CameraType.FIRST_PERSON);
		Stage.log("filmed the Break and punish: 44 frames");
	}

	/** The yard with the fight on and the boss bar, from the player's eyes (HUD on). */
	static void arenaStill(Stage st, Arena arena) {
		clear(st);
		int k = spawnKnight(st, arena);
		Vec3 home = arena.homeVec();
		int bot = bot(st, home.x + 2, home.z + 5, 6000);
		st.c.waitTicks(40);
		st.s.runOnServer(server -> {
			knight(server, k).setHealth(knight(server, k).getMaxHealth() * 0.72F);
			FrayApi.encounters().addBreak(knight(server, k), 0.35F);
		});
		st.s.runCommand("tp @p " + (home.x - 2.5) + " " + Y + " " + (home.z + 11.5) + " 172 6");
		st.playerCam(CameraType.FIRST_PERSON);
		st.c.waitTicks(30);
		boolean bar = st.c.computeOnClient(mc -> ClientCombat.bar != null);
		st.c.takeScreenshot(TestScreenshotOptions.of("knight_arena_bossbar").disableCounterPrefix());
		Stage.expect(bar, "the boss bar is on the client during the fight");
		st.cam(CENTRE.getX() + 14, Y + 10, CENTRE.getZ() + 14, CENTRE.getX(), Y, CENTRE.getZ() - 2);
		st.c.takeScreenshot(TestScreenshotOptions.of("knight_arena_overview").disableCounterPrefix());
		st.playerCam(CameraType.FIRST_PERSON);
		clear(st);
	}

	// ------------------------------------------------------------------ 8. dedicated server: a whole fight, a relog, a restart

	static Properties props() {
		Properties props = new Properties();
		props.setProperty("server-port", "25615");
		props.setProperty("online-mode", "false");
		props.setProperty("white-list", "false");
		props.setProperty("view-distance", "6");
		props.setProperty("simulation-distance", "6");
		props.setProperty("pause-when-empty-seconds", "0");
		props.setProperty("spawn-protection", "0");
		props.setProperty("level-seed", "20261022");
		props.setProperty("gamemode", "survival");
		props.setProperty("network-compression-threshold", "256");
		return props;
	}

	/** A summary of the knight's state the restart must keep. */
	record State(float health, float max, int phase, boolean caged, boolean active, int participants, int cages, float breakMeter) {
	}

	static State state(MinecraftServer server) {
		GallowsKnight k = findKnight(server);
		if (k == null) {
			return new State(-1, -1, 0, false, false, 0, 0, 0);
		}
		BossData d = BossData.get(k);
		return new State(k.getHealth(), k.getMaxHealth(), d.phase, d.caged, d.active, d.participants.size(), k.cages(server.overworld()).size(), d.breakMeter);
	}

	static GallowsKnight findKnight(MinecraftServer server) {
		for (Entity e : server.overworld().getAllEntities()) {
			if (e instanceof GallowsKnight k && k.isAlive()) {
				return k;
			}
		}
		return null;
	}

	static int botId(MinecraftServer server) {
		for (Entity e : server.overworld().getAllEntities()) {
			if (e.entityTags().contains("bestiary_bot") && e instanceof LivingEntity le && le.isAlive()) {
				BestiaryApi.addBot(le);
				return e.getId();
			}
		}
		return -1;
	}

	/** The fight's own bookkeeping across the dedicated runs. */
	static final class Fight {
		long ticks;
		double maxOut;
		int moves;
		final Set<String> movesSeen = new HashSet<>();
		long unsticks0 = BossEncounter.unsticks;
		int walkedBack;
	}

	private static void dedicated(ClientGameTestContext c) {
		Stage.log("--- dedicated: a whole fight against a mannequin player, a relog and a restart mid-fight");
		Properties props = props();
		Fight fight = new Fight();
		Arena[] arena = new Arena[1];
		State beforeRestart;
		try (TestDedicatedServerContext server = c.worldBuilder().createServer(props)) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				Stage st = new Stage(c, server, "boss_ds");
				AiShowcase.buildArena(st);
				server.runCommand("op @a");
				server.runCommand("gamemode survival @a");
				server.runCommand("effect give @a resistance infinite 4 true");
				server.runCommand("effect give @a night_vision infinite 0 true");
				arena[0] = st.s.computeOnServer(srv -> GallowsYard.place(srv.overworld(), CENTRE));
				// The player watches from inside the arena's edge (in the fight: a participant, Resistance V keeps it unhurt).
				server.runCommand("tp @p 0.5 " + Y + " 12.5 180 10");
				connection.waitForChunksRender();
				c.waitTicks(20);
				st.playerCam(CameraType.FIRST_PERSON);
				int k = st.s.computeOnServer(srv -> {
					Mob m = Lairs.spawn(srv.overworld(), GallowsKnight.PROFILE, arena[0], null);
					Lairs.Lair lair = Lairs.get(srv.overworld()).add(GallowsKnight.PROFILE, arena[0], m.getUUID());
					BossData.get(m).lair = lair.id;
					return m.getId();
				});
				Vec3 home = arena[0].homeVec();
				int bot = bot(st, home.x, home.z + 4, 4000);
				st.s.runOnServer(srv -> entity(srv, bot).getAttribute(Attributes.ARMOR).setBaseValue(10));
				fightFor(st, fight, 500, "opening");
				// Relog mid-fight: the client leaves (the yard unloads with it) and comes back.
				State beforeRelog = st.s.computeOnServer(BossShowcase::state);
				Stage.log("relog: the player leaves at " + beforeRelog);
			}
			c.waitTicks(60);
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				Stage st = new Stage(c, server, "boss_ds");
				c.waitTicks(40);
				State back = st.s.computeOnServer(BossShowcase::state);
				boolean bar = false;
				for (int t = 0; t < 60 && !bar; t++) {
					c.waitTick();
					bar = c.computeOnClient(mc -> ClientCombat.bar != null);
				}
				Stage.log("relog: back at " + back + ", bar on the client " + bar);
				Stage.expect(back.active() && back.health() > 0 && back.participants() >= 1, "relog: the fight survives the player leaving and coming back");
				Stage.expect(bar, "relog: the boss bar comes back");
				st.playerCam(CameraType.FIRST_PERSON);
				// Into phase 2 before the restart (the cage off, a hazard on the ground).
				st.s.runOnServer(srv -> {
					GallowsKnight kn = findKnight(srv);
					kn.setHealth(Math.min(kn.getHealth(), kn.getMaxHealth() * 0.57F));
				});
				fightFor(st, fight, 400, "into phase 2");
				beforeRestart = st.s.computeOnServer(BossShowcase::state);
				Stage.log("restart: the server stops at " + beforeRestart);
				st.c.takeScreenshot(TestScreenshotOptions.of("knight_ds_before_restart").disableCounterPrefix());
			}
		}
		// The restart: the same world, a new server.
		DedicatedServer restarted = DedicatedServerImplUtil.start(c, props);
		try (TestDedicatedServerContext server = new TestDedicatedServerContextImpl(c, restarted)) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				Stage st = new Stage(c, server, "boss_ds");
				c.waitTicks(40);
				st.s.runOnServer(srv -> botId(srv));
				State after = st.s.computeOnServer(BossShowcase::state);
				Stage.log("restart: the server is back at " + after);
				Stage.expect(after.active() && Math.abs(after.health() - beforeRestart.health()) < 30 && after.phase() == beforeRestart.phase()
					&& after.caged() == beforeRestart.caged() && after.participants() == beforeRestart.participants() && after.cages() == beforeRestart.cages()
					&& Math.abs(after.max() - beforeRestart.max()) < 0.01,
					"restart: health, phase, the cage, the participants and the hazard survive a server restart");
				boolean bar = false;
				for (int t = 0; t < 60 && !bar; t++) {
					c.waitTick();
					bar = c.computeOnClient(mc -> ClientCombat.bar != null);
				}
				Stage.expect(bar, "restart: the fight resumes (the bar is back)");
				st.playerCam(CameraType.FIRST_PERSON);
				// To the end.
				long n0 = BossEncounter.tickNanos, t0 = BossEncounter.ticks, e0 = Brain.executorNanos + Brain.tacticsNanos + Brain.perceptionNanos;
				long serverTick0 = st.serverTime();
				boolean felled = fightFor(st, fight, 3600, "to the end");
				long bossTicks = Math.max(1, BossEncounter.ticks - t0), serverTicks = Math.max(1, st.serverTime() - serverTick0);
				Stage.log(String.format(Locale.ROOT, "perf: the encounter framework %.1f µs a boss tick; the knight's AI (perception, tactics, executor) %.1f µs a tick",
					(BossEncounter.tickNanos - n0) / 1000.0 / bossTicks, (Brain.executorNanos + Brain.tacticsNanos + Brain.perceptionNanos - e0) / 1000.0 / serverTicks));
				UUID me = st.s.computeOnServer(srv -> srv.getPlayerList().getPlayers().getFirst().getUUID());
				List<BossLoot.Award> mine = BossLoot.AWARDS.stream().filter(a -> a.who().equals(me)).toList();
				int chains = st.s.computeOnServer(srv -> {
					ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
					int n = 0;
					for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
						ItemStack s = p.getInventory().getItem(i);
						if (s.is(Items.IRON_CHAIN) && s.getHoverName().getString().contains("Gallows")) {
							n += s.getCount();
						}
					}
					return n;
				});
				String codex = st.s.computeOnServer(srv -> Codex.entry(srv.getPlayerList().getPlayers().getFirst(), "bestiary:gallows_knight").knowledge().name());
				Stage.log(String.format(Locale.ROOT, "full fight: felled %s after %d ticks (%.0f s) of fighting, %d moves (%s), the knight at most %.1f blocks outside its arena, unstuck %d times; the player's loot %s, gallows chains in the inventory %d, codex %s",
					felled, fight.ticks, fight.ticks / 20.0, fight.moves, fight.movesSeen, fight.maxOut, BossEncounter.unsticks - fight.unsticks0,
					mine.stream().map(BossLoot.Award::items).toList(), chains, codex));
				Stage.expect(felled, "dedicated server: the full fight ends with the knight felled");
				Stage.expect(fight.maxOut <= 1.0 && BossEncounter.unsticks - fight.unsticks0 == 0, "the knight never left its arena and never got stuck");
				Stage.expect(!mine.isEmpty() && chains >= 2, "the player gets their personal loot in their inventory");
				Stage.expect(fight.movesSeen.containsAll(List.of("hook_cut", "three_cut")) && fight.movesSeen.size() >= 6, "the fight used both phases' moves");
				Stage.expect(!codex.equals("NONE"), "the codex knows the knight");
				st.c.takeScreenshot(TestScreenshotOptions.of("knight_ds_felled").disableCounterPrefix());
			}
		}
	}

	/**
	 * The mannequin fights the knight for {@code ticks}: it stands its ground, turns to the knight and lands a sword hit
	 * every 10 ticks (13 a second, about a mid-stage player's), hitting about a third of the time.
	 */
	static boolean fightFor(Stage st, Fight f, int ticks, String what) {
		long since = st.serverTime();
		java.util.Random rnd = new java.util.Random(since);
		for (int t = 0; t < ticks; t++) {
			int tt = t;
			boolean swing = t % 10 == 0;
			boolean lands = rnd.nextFloat() < 0.34F;
			boolean done = st.s.computeOnServer(srv -> {
				GallowsKnight k = findKnight(srv);
				if (k == null) {
					return true;
				}
				int bot = botId(srv);
				if (bot < 0) {
					return false;
				}
				LivingEntity m = entity(srv, bot);
				m.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, k.getEyePosition());
				if (m.getHealth() < m.getMaxHealth() * 0.3F) {
					m.setHealth(m.getMaxHealth());
				}
				if (swing && lands && m.distanceTo(k) < 4.5) {
					hitFrom(srv, bot, k.getId(), 6.5F * 3, HitFlags.LIGHT);
				} else if (swing && lands) {
					// Out of reach (pulled away, the knight backs off): an arrow's worth.
					hitFrom(srv, bot, k.getId(), 6.5F, HitFlags.PROJECTILE);
				}
				Arena a = BossData.get(k).arena;
				f.maxOut = Math.max(f.maxOut, a.distance(k.position()) - a.radius());
				// Thrown out of the yard (a charge, the cage): it walks back in, as a player would.
				if (a.distance(m.position()) > a.radius() - 1.5) {
					Vec3 back = a.homeVec().add(0, 0, 4);
					m.teleportTo(back.x, back.y, back.z);
					f.walkedBack++;
				}
				if (tt % 200 == 0) {
					ServerPlayer p = srv.getPlayerList().getPlayers().isEmpty() ? null : srv.getPlayerList().getPlayers().getFirst();
					Stage.log(String.format(Locale.ROOT, "  t%d: inside %d, mannequin %.1f from the centre, player %s, knight %.0f hp, empty since %d", tt,
						BossEncounter.inside(BossData.get(k), srv.overworld()).size(), a.distance(m.position()),
						p == null ? "-" : String.format(Locale.ROOT, "%s %.1f from the centre y %.1f", p.gameMode.getGameModeForPlayer(), a.distance(p.position()), p.getY()),
						k.getHealth(), BossData.get(k).emptySince));
				}
				return false;
			});
			st.c.waitTick();
			f.ticks++;
			if (done) {
				break;
			}
		}
		List<Executor.Tell> tells = Executor.TELLS.stream().filter(x -> x.tick() >= since).toList();
		f.moves += tells.size();
		tells.forEach(x -> f.movesSeen.add(x.move()));
		State s = st.s.computeOnServer(BossShowcase::state);
		Stage.log(String.format(Locale.ROOT, "fight (%s): %d moves %s, now %s", what, tells.size(), tells.stream().map(Executor.Tell::move).distinct().toList(), s));
		return s.health() < 0;
	}

	static int knightId(MinecraftServer server) {
		GallowsKnight k = findKnight(server);
		return k == null ? -1 : k.getId();
	}

	static boolean profileLoaded() {
		return CreatureProfiles.get(GallowsKnight.PROFILE) != null;
	}
}
