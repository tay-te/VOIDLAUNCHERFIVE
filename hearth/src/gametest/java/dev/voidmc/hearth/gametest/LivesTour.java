package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.crowd.CrowdStats;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.routine.Schedule;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesConfig;
import dev.voidmc.hearth.sim.lives.LivesDev;
import dev.voidmc.hearth.sim.lives.LivesStats;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.sim.lives.TownLives;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import dev.voidmc.hearth.social.life.Venues;
import dev.voidmc.hearth.social.lives.LivesFolk;
import dev.voidmc.hearth.social.rhythm.Rhythm;
import dev.voidmc.hearth.social.rhythm.RhythmCalendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * People with lives (docs/LIVING_WORLD_2.md §4.8), in a town grown by the real sim: a courting couple at a viewpoint on
 * the rest day's afternoon, two folk talking about the player within earshot after a deed ({@code /hearth standing
 * deed}), the drunk smith at the tavern at 22:00, and the People list after talking to three named characters (the
 * section the journal's People page will draw; the page itself waits on Figma). Logs the lives' timings and barks.
 *
 * <p>{@code -Dhearth.tour=lives} (or {@code HEARTH_LW_TOUR=lives}); {@code -Dhearth.lives.days=N} (growth, default 70).
 */
public class LivesTour implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.lives.days", 70);

	@Override
	public void runTest(ClientGameTestContext context) {
		String env = System.getenv("HEARTH_LW_TOUR");
		if (!Tours.wanted("lives") && !(env != null && List.of(env.split(",")).contains("lives"))) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			for (String c : List.of("gamerule advance_weather false", "gamerule spawn_monsters false", "gamerule advance_time false", "weather clear",
				"time set 1000", "gamemode spectator @a", "tp @a 30000 300 30000")) {
				world.getServer().runCommand(c);
			}
			settle(context, 3_000);
			Long town = world.getServer().computeOnServer(LivesTour::foundTown);
			if (town == null) {
				log("no site for a town");
				return;
			}
			// before anyone is embodied (their day is made when they appear): the rest day held, a smith who drinks, sweethearts
			String cast = world.getServer().computeOnServer(server -> cast(server, town));
			log("cast: " + cast);
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, town, false));
			world.getServer().runCommand("gamemode creative @a");
			BlockPos c = world.getServer().computeOnServer(server -> settlement(server, town).center());
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60, true);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			settle(context, 15_000);
			log("town: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town);
				return s.name() + " " + s.population() + " souls, " + s.tier().getSerializedName() + ", embodied " + s.embodied() + ", folk about "
					+ NpcModule.embodiment().count();
			}));
			logLives(world, town);

			// 1. the rest day's afternoon: the sweethearts walk out to a viewpoint
			timeTo(world, Schedule.dayTimeOf(14 * 60 + 20));
			world.getServer().runCommand("gamerule advance_time true");
			settle(context, 30_000);
			world.getServer().runCommand("gamerule advance_time false");
			String couple = world.getServer().computeOnServer(server -> couple(server, town));
			log("courting: " + couple);
			shotAt(context, world, "courting", server -> coupleCamera(server, town));

			// 2. a deed, and folk at leisure who talk of it where the player can hear
			Lives.setConfig(talkMore(Lives.config()));
			world.getServer().runCommand("hearth standing deed @p \"repair:fire_brigade\" 25 true");
			BlockPos spot = world.getServer().computeOnServer(server -> idleSpot(server, town));
			if (spot != null) {
				teleport(world, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 10, false);
				context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
				for (int i = 0; i < 30 && world.getServer().computeOnServer(server -> LivesFolk.overheardLine().startsWith("overheard 0")); i++) {
					settle(context, 1_000);
				}
				turnToFolk(world);
				settle(context, 800);
				shot(context, "overheard");
				context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			}
			log(LivesFolk.overheardLine());
			Lives.setConfig(LivesConfig.DEFAULTS);

			// 3. 22:00: the drunk smith at the tavern
			timeTo(world, Schedule.dayTimeOf(21 * 60 + 40));
			world.getServer().runCommand("gamerule advance_time true");
			settle(context, 25_000);
			world.getServer().runCommand("gamerule advance_time false");
			String smith = world.getServer().computeOnServer(server -> smith(server, town));
			log("22:00 " + smith);
			shotAt(context, world, "drunk_smith", server -> smithCamera(server, town));

			// 4. talk to three named characters; the People list the client holds
			String talked = world.getServer().computeOnServer(server -> talkToThree(server, town));
			log("talked: " + talked);
			settle(context, 2_000);
			String people = context.computeOnClient(dev.voidmc.hearth.client.social.lives.PeopleSection::snapshot);
			log("people section: " + people);
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			settle(context, 1_000);
			shot(context, "people_talk");

			logLives(world, town);
			log(LivesStats.line());
			for (String l : LivingWorldStats.lines()) {
				log(l);
			}
			for (String b : CrowdStats.recentBarks()) {
				if (b.contains("lives.")) {
					log("bark: " + b);
				}
			}
		}
	}

	static LivesConfig talkMore(LivesConfig c) {
		LivesConfig.Talk t = c.talk();
		return new LivesConfig(c.lives(), c.crafts(), c.relations(), c.feuds(), new LivesConfig.Talk(20, 1.0, 1.0, t.radius()));
	}

	// ---------------------------------------------------------------- the cast

	/** The rest day held, the drunk smith made (a smith, or a grown man given the trade), two singles sweethearts. */
	private static String cast(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		Rhythm.holdDay(s, RhythmCalendar.Forced.REST);
		StringBuilder out = new StringBuilder();
		Resident smith = null;
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && r.job == JobType.SMITH) {
				smith = r;
				break;
			}
		}
		if (smith == null) {
			for (Resident r : s.residents()) {
				if (r.age == AgeStage.ADULT && LivesView.notable(s, r.id) == null && Life(r)) {
					smith = r;
					r.job = JobType.SMITH;
					break;
				}
			}
		}
		if (smith != null) {
			TownLives.Notable n = LivesDev.make(s, smith, "drunk_smith");
			out.append("smith ").append(smith.fullName()).append(n == null ? "" : " at " + n.beat).append("; ");
		}
		Resident a = null;
		Resident b = null;
		for (Resident r : s.residents()) {
			if (r.age != AgeStage.ADULT || r == smith || r.extra.getCompoundOrEmpty("npc").contains("spouse") || LivesView.notable(s, r.id) != null) {
				continue;
			}
			if (a == null) {
				a = r;
			} else if (b == null && r.female != a.female && r.home != a.home) {
				b = r;
			}
		}
		if (a != null && b != null) {
			LivesDev.sweethearts(s, a, b);
			out.append("sweethearts ").append(a.fullName()).append(" and ").append(b.fullName()).append("; ");
		}
		out.append("named: ").append(LivesView.notables(s).size());
		s.markDirty();
		return out.toString();
	}

	private static boolean Life(Resident r) {
		return r.job == JobType.FARMER || r.job == JobType.NONE || r.job == JobType.BUILDER;
	}

	private static @Nullable Folk folkOf(UUID id) {
		for (Folk f : NpcModule.embodiment().entities()) {
			if (id.equals(f.residentId())) {
				return f;
			}
		}
		return null;
	}

	private static String couple(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		for (Resident r : s.residents()) {
			UUID o = LivesDev.sweetheartOf(r);
			if (o == null || r.id.compareTo(o) > 0) {
				continue;
			}
			Folk a = folkOf(r.id);
			Folk b = folkOf(o);
			if (a == null || b == null) {
				return r.fullName() + " and their sweetheart: not both about";
			}
			BlockPos spot = dev.voidmc.hearth.social.lives.LivesFolkAccess.courtingSpot(s, r.id, o);
			return String.format(Locale.ROOT, "%s and %s, %.1f blocks apart, %.1f from the spot %s (behaviour %s)", r.givenName, s.resident(o).givenName,
				a.distanceTo(b), Math.sqrt(a.blockPosition().distSqr(spot)), spot.toShortString(), behaviour(a));
		}
		return "no sweethearts";
	}

	private static String behaviour(Folk f) {
		return dev.voidmc.hearth.social.lives.LivesFolkAccess.running(f);
	}

	private static double @Nullable [] coupleCamera(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		for (Resident r : s.residents()) {
			UUID o = LivesDev.sweetheartOf(r);
			Folk a = o == null ? null : folkOf(r.id);
			Folk b = o == null ? null : folkOf(o);
			if (a != null && b != null) {
				double mx = (a.getX() + b.getX()) / 2;
				double mz = (a.getZ() + b.getZ()) / 2;
				return new double[] {mx + 4.5, a.getY() + 2.2, mz + 4.5, mx, a.getY() + 1.2, mz};
			}
		}
		return null;
	}

	/** Where folk stand idle in town: near two folk close together, not at work. */
	private static @Nullable BlockPos idleSpot(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		NpcService npc = HearthServices.npc();
		List<Folk> all = new ArrayList<>(NpcModule.embodiment().entities());
		for (Folk f : all) {
			NpcService.FolkHandle h = f.residentId() == null ? null : npc.folk(f.residentId());
			if (h == null || h.resident().age == AgeStage.CHILD || npc.onDuty(s, h.resident())) {
				continue;
			}
			for (Folk g : all) {
				NpcService.FolkHandle k = g.residentId() == null ? null : npc.folk(g.residentId());
				if (g != f && k != null && k.resident().age != AgeStage.CHILD && !npc.onDuty(s, k.resident()) && g.distanceToSqr(f) <= 16) {
					return f.blockPosition().offset(5, 0, 0);
				}
			}
		}
		return null;
	}

	private static String smith(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		for (TownLives.Notable n : LivesView.notables(s)) {
			if (!n.role.equals("drunk_smith")) {
				continue;
			}
			Resident r = s.resident(n.resident);
			Folk f = folkOf(n.resident);
			List<PlotView> taverns = Venues.built(s, "commerce/inn", "commerce/tavern");
			if (f == null) {
				return (r == null ? "?" : r.fullName()) + " (the drunk smith) not about; " + taverns.size() + " taverns";
			}
			PlotView in = Venues.plotAt(s, f.blockPosition());
			String where = in == null ? "out of doors" : in.spec().archetype().getPath();
			double nearest = Double.MAX_VALUE;
			for (PlotView t : taverns) {
				nearest = Math.min(nearest, Math.sqrt(Venues.middle(t.spec()).distSqr(f.blockPosition())));
			}
			return String.format(Locale.ROOT, "%s (the drunk smith, at %s) is in %s, %.0f blocks from the nearest of %d taverns", r.fullName(), n.beat, where, nearest,
				taverns.size());
		}
		return "no drunk smith";
	}

	private static double @Nullable [] smithCamera(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		for (TownLives.Notable n : LivesView.notables(s)) {
			Folk f = n.role.equals("drunk_smith") ? folkOf(n.resident) : null;
			if (f != null) {
				var look = f.getLookAngle();
				return new double[] {f.getX() + look.x * 3.2, f.getY() + 1.6, f.getZ() + look.z * 3.2, f.getX(), f.getY() + 1.4, f.getZ()};
			}
		}
		return null;
	}

	/** Talks to three named characters through the conversation's providers: they are met, the People list is sent. */
	private static String talkToThree(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		ServerPlayer p = server.getPlayerList().getPlayers().get(0);
		StringBuilder out = new StringBuilder();
		int n = 0;
		for (TownLives.Notable nb : List.copyOf(LivesView.notables(s))) {
			Resident r = s.resident(nb.resident);
			if (r == null || n >= 3) {
				continue;
			}
			List<DialogueProvider.Option> options = new ArrayList<>();
			for (DialogueProvider dp : DialogueProvider.providers()) {
				dp.collect(p, s, r, options);
			}
			for (DialogueProvider dp : DialogueProvider.providers()) {
				if (dp.select(p, s, r, "lives:story")) {
					Component line = dp.reply(p, s, r, "lives:story");
					out.append(r.givenName).append(" (").append(nb.role).append("): ").append(line == null ? "" : line.getString()).append(" | options ");
					for (DialogueProvider.Option o : options) {
						if (o.id().startsWith("lives:")) {
							out.append(o.id()).append(' ');
						}
					}
					out.append("\n    ");
					break;
				}
			}
			n++;
		}
		// and anyone's word on the folk of note
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT && LivesView.notable(s, r.id) == null) {
				for (DialogueProvider dp : DialogueProvider.providers()) {
					if (dp.select(p, s, r, "lives:people")) {
						Component line = dp.reply(p, s, r, "lives:people");
						String text = line == null ? "" : line.getString();
						p.sendSystemMessage(Component.literal(r.givenName + ": " + text));
						out.append(r.givenName).append(" on the folk of note: ").append(text);
						break;
					}
				}
				break;
			}
		}
		return out.toString();
	}

	private static void logLives(TestSingleplayerContext world, long town) {
		world.getServer().runOnServer(server -> {
			SimSettlement s = settlement(server, town);
			for (TownLives.Notable n : LivesView.notables(s)) {
				Resident r = s.resident(n.resident);
				log(String.format(Locale.ROOT, "  named: %s, %s at %s (path %s)", r == null ? "?" : r.fullName(), n.role, n.beat, n.path));
			}
			for (TownLives.Feud f : LivesView.town(s).feuds) {
				log("  feud over " + f.cause + ", heat " + f.heat);
			}
			log("  counts " + new java.util.TreeMap<>(LivesView.stats(s)));
		});
	}

	// ---------------------------------------------------------------- shots and the world (as CrowdsTour)

	interface Camera {
		double @Nullable [] at(MinecraftServer server);
	}

	private static void shotAt(ClientGameTestContext context, TestSingleplayerContext world, String name, Camera camera) {
		double[] cam = world.getServer().computeOnServer(camera::at);
		if (cam == null) {
			log(name + ": nothing to photograph");
			return;
		}
		double dx = cam[3] - cam[0];
		double dy = cam[4] - (cam[1] + 1.62);
		double dz = cam[5] - cam[2];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		teleport(world, cam[0], cam[1], cam[2], yaw, Mth.clamp(pitch, -70, 70), true);
		settle(context, 3_000);
		shot(context, name);
	}

	private static void turnToFolk(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().get(0);
			double sx = 0;
			double sz = 0;
			int n = 0;
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.distanceTo(p) <= 12 && f.distanceTo(p) > 1) {
					sx += f.getX() - p.getX();
					sz += f.getZ() - p.getZ();
					n++;
				}
			}
			if (n > 0) {
				float yaw = (float) Math.toDegrees(Math.atan2(-sx, sz));
				p.teleportTo(server.overworld(), p.getX(), p.getY(), p.getZ(), java.util.Set.of(), yaw, 20, true);
			}
		});
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("hearth_lives_" + name).withSize(1600, 900));
	}

	private static @Nullable Long foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		Culture bestCulture = null;
		int bestRelief = Integer.MAX_VALUE;
		for (int pass = 0; pass < 2 && best == null; pass++) {
			for (int r = 128; r <= 1600 && (best == null || bestRelief > 3 || r < 512); r += 64) {
				for (int k = 0; k < 16; k++) {
					double a = k * Math.PI / 8 + r * 0.01;
					BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
					int g = w.terrain().groundY(at.getX(), at.getZ());
					if (g <= w.terrain().seaLevel() + 2) {
						continue;
					}
					int lo = g;
					int hi = g;
					for (int dx = -40; dx <= 40; dx += 10) {
						for (int dz = -40; dz <= 40; dz += 10) {
							int h = w.terrain().groundY(at.getX() + dx, at.getZ() + dz);
							lo = Math.min(lo, h);
							hi = Math.max(hi, h);
						}
					}
					if (hi - lo >= bestRelief) {
						continue;
					}
					Culture c = CultureVote.at(at.getX(), at.getZ(), w.probe()::biomeHolder, HearthServices::culture);
					if (pass == 0 && c != Culture.TEMPERATE) {
						continue;
					}
					if (w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
						continue;
					}
					bestRelief = hi - lo;
					best = at;
					bestCulture = c;
				}
			}
		}
		if (best == null) {
			return null;
		}
		SimSettlement s = w.found(best, bestCulture, null);
		for (int d = 0; d < DAYS; d += 4) {
			for (Goods g : Goods.VALUES) {
				int want = s.stockpile().capacity(g) * 3 / 4 - s.stockpile().get(g);
				if (want > 0) {
					s.stockpile().add(g, want);
				}
			}
			if (s.treasury() < 3000) {
				s.deposit(3000 - s.treasury());
			}
			w.simulate(s, 4);
		}
		log(String.format(Locale.ROOT, "founded %s at %s, grew %d days: %d souls, %s, plots %s", s.name(), s.center().toShortString(), DAYS, s.population(),
			s.tier().getSerializedName(), s.plotCounts()));
		return s.id();
	}

	private static SimSettlement settlement(MinecraftServer server, long id) {
		return SimServer.world(server.overworld()).settlement(id);
	}

	private static void forceChunks(MinecraftServer server, long town, boolean force) {
		ServerLevel level = server.overworld();
		BoundingBox b = settlement(server, town).bounds();
		for (int cx = (b.minX() >> 4) - 1; cx <= (b.maxX() >> 4) + 1; cx++) {
			for (int cz = (b.minZ() >> 4) - 1; cz <= (b.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, force);
			}
		}
	}

	private static void waitApplier(ClientGameTestContext context, TestSingleplayerContext world) {
		for (int i = 0; i < 240; i++) {
			settle(context, 500);
			int pending = world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).applier().pending());
			if (pending == 0 && i > 6) {
				break;
			}
		}
	}

	private static int surface(TestSingleplayerContext world, BlockPos at) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		});
	}

	private static void timeTo(TestSingleplayerContext world, long t) {
		long now = world.getServer().computeOnServer(server -> server.overworld().getOverworldClockTime() % 24_000L);
		long add = ((t - now) % 24_000L + 24_000L) % 24_000L;
		if (add > 0) {
			world.getServer().runCommand("time add " + add);
		}
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch, boolean fly) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().get(0);
			p.getAbilities().flying = fly;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String line) {
		System.out.println("[hearth lives] " + line);
	}
}
