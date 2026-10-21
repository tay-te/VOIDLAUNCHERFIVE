package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import dev.voidmc.hearth.social.events.TownEvents;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Town events you stumble into (docs/LIVING_WORLD_2.md §3.8), in a town grown by the real sim: a wedding procession at
 * the chapel door, a funeral at the cemetery, a trial, the pillory with its onlookers, the fair's stalls and archery
 * targets, the tournament (where the town has lists), a house on fire and its burnt shell, the tax day at the town hall,
 * a night raid with the guards fighting — each forced ({@code /hearth events force}), photographed when its scene is
 * under way, and its outcome and the events' timings logged.
 *
 * <p>{@code HEARTH_LW_TOUR=events} or {@code -Dhearth.tour=events}; {@code -Dhearth.events.days=N} (growth, default 70).
 */
public class EventsTour implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.events.days", 70);

	static boolean wanted() {
		String env = System.getenv("HEARTH_LW_TOUR");
		if (env != null && (env.contains("events") || env.contains("all"))) {
			return true;
		}
		String only = System.getProperty("hearth.tour");
		return only != null && !only.isBlank() && Tours.wanted("events");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!wanted()) {
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
				"time set 3000", "gamemode spectator @a", "tp @a 30000 300 30000")) {
				world.getServer().runCommand(c);
			}
			settle(context, 3_000);
			Long town = world.getServer().computeOnServer(EventsTour::foundTown);
			if (town == null) {
				log("no site for a town");
				return;
			}
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runCommand("gamemode creative @a");
			BlockPos c = world.getServer().computeOnServer(server -> settlement(server, town).center());
			teleport(world, c.getX() + 0.5, surface(world, c) + 18, c.getZ() + 0.5, 0, 60, true);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			settle(context, 20_000);
			log("town: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, town);
				return s.name() + " " + s.population() + " souls, " + s.tier().getSerializedName() + ", embodied " + s.embodied() + ", folk about "
					+ NpcModule.embodiment().count() + ", plots " + s.plotCounts();
			}));
			world.getServer().runCommand("gamerule advance_time true");

			event(context, world, town, "wedding", "chapel_door", 9_000, 3000);
			event(context, world, town, "funeral", "cemetery", 12_000, 3000);
			event(context, world, town, "trial", "courthouse", 8_000, 4000);
			event(context, world, town, "pillory", "pillory", 12_000, 5000);
			event(context, world, town, "fair", "square", 30_000, 4000);
			event(context, world, town, "tournament", "tilt_yard", 10_000, 4000);
			event(context, world, town, "tax", "town_hall", 25_000, 4000);
			fire(context, world, town);
			event(context, world, town, "runaway_pig", "square", 8_000, 7000);
			event(context, world, town, "topping_out", "plot_front", 10_000, 11000);
			// a night raid, at two in the morning
			world.getServer().runCommand("time set 20000");
			settle(context, 4_000);
			event(context, world, town, "raid", "storehouse", 20_000, 20000);

			for (String l : world.getServer().computeOnServer(server -> TownEvents.book(settlement(server, town)))) {
				log("book: " + l);
			}
			Double bench = world.getServer().computeOnServer(server -> Double.valueOf(TownEvents.bench(HearthWorld.of(server.overworld()), 28)));
			log(String.format(Locale.ROOT, "bench: a town's day of events costs %.1f µs (every town, 28 days ahead)", bench));
			for (String l : world.getServer().computeOnServer(server -> TownEvents.stats())) {
				log(l);
			}
			for (String l : LivingWorldStats.lines()) {
				log(l);
			}
			world.getServer().runOnServer(server -> forceChunks(server, town, false));
		}
	}

	/** Forces an event, waits for its scene to get going, photographs it from beside its venue, logs who is there. */
	private static void event(ClientGameTestContext context, TestSingleplayerContext world, long town, String kind, String venue, long wait, int time) {
		world.getServer().runCommand("time set " + time);
		settle(context, 1_000);
		String key = world.getServer().computeOnServer(server -> TownEvents.force(settlement(server, town), kind));
		if (key == null) {
			log(kind + ": could not be held here");
			return;
		}
		boolean on = false;
		for (int i = 0; i < 20 && !on; i++) {
			settle(context, 500);
			on = world.getServer().computeOnServer(server -> TownEvents.running(settlement(server, town), kind));
		}
		if (!on) {
			log(kind + ": its scene did not begin");
			return;
		}
		settle(context, wait);
		shotAt(context, world, town, kind, venue, kind);
		if (kind.equals("fair")) {
			shotAt(context, world, town, "fair", "range", "fair_targets");
		}
		log(kind + ": cast about " + world.getServer().computeOnServer(server -> TownEvents.castAbout(settlement(server, town), kind)) + ", folk within 10 of "
			+ venue + ": " + world.getServer().computeOnServer(server -> near(server, town, kind, venue, 10)) + extra(world, town, kind));
		world.getServer().runOnServer(server -> TownEvents.endScenes(settlement(server, town)));
		settle(context, 1_000);
	}

	private static String extra(TestSingleplayerContext world, long town, String kind) {
		if (!kind.equals("fair")) {
			return "";
		}
		return world.getServer().computeOnServer(server -> {
			Object stalls = TownEvents.sceneState(settlement(server, town), "fair", "stalls");
			Object targets = TownEvents.sceneState(settlement(server, town), "fair", "targets");
			return ", stalls " + stalls + ", targets " + (targets instanceof List<?> l ? l.size() : 0);
		});
	}

	/** A house on fire, then its burnt shell, then the builders at it. */
	private static void fire(ClientGameTestContext context, TestSingleplayerContext world, long town) {
		event(context, world, town, "fire", "plot_front", 25_000, 6000);
		int laid = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, town);
			HearthWorld w = HearthWorld.of(server.overworld());
			return TownEvents.applyShells(w, s);
		});
		log("fire: burnt shell laid, " + laid + " blocks; to mend " + damaged(world, town));
		BlockPos plot = world.getServer().computeOnServer(server -> burntPlot(server, town));
		if (plot != null) {
			look(world, plot, 9, 6);
			settle(context, 2_500);
			shot(context, "burnt");
			settle(context, 40_000);
			log("fire: to mend after 40 s " + damaged(world, town));
			look(world, plot, 9, 6);
			settle(context, 2_000);
			shot(context, "rebuilding");
		}
	}

	private static int damaged(TestSingleplayerContext world, long town) {
		return world.getServer().computeOnServer(server -> HearthServices.repairs() == null ? -1 : HearthServices.repairs().damaged(settlement(server, town)));
	}

	private static @Nullable BlockPos burntPlot(MinecraftServer server, long town) {
		SimSettlement s = settlement(server, town);
		for (String l : TownEvents.book(s)) {
			if (l.startsWith("burnt plot ")) {
				int id = Integer.parseInt(l.substring("burnt plot ".length(), l.indexOf(' ', "burnt plot ".length())));
				var p = s.plot(id);
				if (p != null) {
					return p.spec().origin().offset(p.spec().sizeX() / 2, 0, p.spec().sizeZ() / 2);
				}
			}
		}
		return null;
	}

	private static void shotAt(ClientGameTestContext context, TestSingleplayerContext world, long town, String kind, String venue, String name) {
		BlockPos at = world.getServer().computeOnServer(server -> TownEvents.venue(settlement(server, town), kind, venue));
		if (at == null) {
			log(name + ": no venue");
			return;
		}
		look(world, at, 10, 5);
		settle(context, 2_500);
		shot(context, name);
	}

	/** The camera a few blocks off a point, on the side with the most open air, looking at it. */
	private static void look(TestSingleplayerContext world, BlockPos at, int dist, int up) {
		double[] best = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			double[] out = null;
			int bestOpen = -1;
			for (int k = 0; k < 8; k++) {
				double a = k * Math.PI / 4;
				int x = at.getX() + (int) Math.round(Math.cos(a) * dist);
				int z = at.getZ() + (int) Math.round(Math.sin(a) * dist);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				int y = Math.max(at.getY() + up, ground + 2);
				int open = 0;
				for (int s = 1; s < 10; s++) {
					BlockPos p = BlockPos.containing(x + (at.getX() - x) * s / 10.0, y + (at.getY() + 1 - y) * s / 10.0, z + (at.getZ() - z) * s / 10.0);
					if (level.getBlockState(p).isAir()) {
						open++;
					}
				}
				if (open > bestOpen) {
					bestOpen = open;
					out = new double[]{x + 0.5, y, z + 0.5};
				}
			}
			return out;
		});
		double dx = at.getX() + 0.5 - best[0];
		double dz = at.getZ() + 0.5 - best[2];
		double dy = at.getY() + 1 - (best[1] + 1.6);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		teleport(world, best[0], best[1], best[2], yaw, pitch, true);
	}

	private static int near(MinecraftServer server, long town, String kind, String venue, double r) {
		BlockPos at = TownEvents.venue(settlement(server, town), kind, venue);
		if (at == null) {
			return 0;
		}
		int n = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.blockPosition().distSqr(at) <= r * r) {
				n++;
			}
		}
		return n;
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of("hearth_events_" + name).withSize(1600, 900));
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
		System.out.println("[hearth events] " + line);
	}

	static Map<String, Integer> none() {
		return Map.of();
	}
}
