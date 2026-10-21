package dev.voidmc.hearth.gametest.work;

import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.ResourceSite;
import dev.voidmc.hearth.gametest.Tours;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.work.field.Boats;
import dev.voidmc.hearth.npc.work.field.ClampCycle;
import dev.voidmc.hearth.npc.work.field.Clamps;
import dev.voidmc.hearth.npc.work.field.FieldSites;
import dev.voidmc.hearth.npc.work.field.Herds;
import dev.voidmc.hearth.npc.work.field.RemoteSites;
import dev.voidmc.hearth.sim.LivingWorldStats;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.land.GreatMines;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.world.SimWorld;
import dev.voidmc.hearth.social.life.StallWares;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The visible-work tour (docs/LIVING_WORLD_2.md §6.8; {@code -Dhearth.tour=work}, or {@code HEARTH_LW_TOUR=work} or
 * {@code all}): a town grown by the real sim on the dev seed, and what its work looks like to a player — a bakery or stall
 * counter whose wares match its stock, the pen full at 20:00 and the flock with its herder out on a far meadow at 10:00,
 * a smouldering clamp and its burner, a fisher's boat at the dock, the Great Mine where one is in reach — with the counts
 * of every view against its caps and the field work's tick cost from {@code /hearth sim stats}. Screenshots are
 * {@code hearth_work_*}; every step logs {@code [hearth work]}; a leg that cannot be set up says why and the next goes on.
 */
public class WorkTour implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.work.days", 160);

	private record Town(long id, BlockPos center) {
	}

	static boolean wanted() {
		String env = System.getenv("HEARTH_LW_TOUR");
		if (env != null && (env.contains("work") || env.contains("all"))) {
			return true;
		}
		String only = System.getProperty("hearth.tour");
		return only != null && !only.isBlank() && Tours.wanted("work");
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!wanted()) {
			return;
		}
		context.runOnClient(mc -> mc.options.renderDistance().set(10));
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed(System.getProperty("hearth.work.seed", "20261003"));
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamerule spawn_monsters false");
			world.getServer().runCommand("weather clear");
			timeTo(world, 1000);
			settle(context, 3_000);
			Town found = historic(context, world);
			if (found == null) {
				found = world.getServer().computeOnServer(WorkTour::foundTown);
			}
			final Town town = found;
			if (town == null) {
				log("no site for a settlement near spawn");
				return;
			}
			world.getServer().runOnServer(server -> forceChunks(server, town, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, town, false));
			log("town: " + world.getServer().computeOnServer(server -> describe(town(server, town))));
			leg("stall", () -> stall(context, world, town));
			leg("pen", () -> pen(context, world, town));
			leg("meadow", () -> meadow(context, world, town));
			leg("clamp", () -> clamp(context, world, town));
			leg("boat", () -> boat(context, world, town));
			leg("great mine", () -> greatMine(context, world, town));
			log("caps: " + world.getServer().computeOnServer(server -> "flock entities " + Herds.count() + " (cap 96), boats " + Boats.count()
				+ " (cap 24), stall wares " + StallWares.count() + " (cap 1500); server " + String.format(Locale.ROOT, "%.2f",
				dev.voidmc.hearth.sim.LoadCaps.mspt(server)) + " ms a tick"));
			for (String line : world.getServer().computeOnServer(server -> LivingWorldStats.lines())) {
				log("stats: " + line);
			}
		}
	}

	private static void leg(String name, Runnable leg) {
		log("---- " + name);
		try {
			leg.run();
		} catch (RuntimeException e) {
			log(name + " failed: " + e);
			e.printStackTrace();
		}
	}

	// ---------------------------------------------------------------- the town

	/**
	 * A town history grew (its atlas site's region adopted when its chunks load): the biggest near spawn, with the trades
	 * the legs want more likely than in a camp founded for the tour. Null if none comes live in time.
	 */
	private static @Nullable Town historic(ClientGameTestContext context, TestSingleplayerContext world) {
		List<dev.voidmc.hearth.api.HearthWorld.SiteInfo> sites = List.of();
		for (int i = 0; i < 40; i++) {
			// (the atlas plans its regions in the background: asked again until they are known)
			sites = world.getServer().computeOnServer(server -> {
				SimWorld w = SimServer.world(server.overworld());
				return w == null ? List.<dev.voidmc.hearth.api.HearthWorld.SiteInfo>of()
					: w.knownSites(server.overworld().getRespawnData().pos(), Integer.getInteger("hearth.work.reach", 2500));
			});
			if (sites.size() >= 6) {
				break;
			}
			settle(context, 1_000);
		}
		dev.voidmc.hearth.api.HearthWorld.SiteInfo best = null;
		for (dev.voidmc.hearth.api.HearthWorld.SiteInfo i : sites) {
			if (best == null || i.tier().ordinal() > best.tier().ordinal()) {
				best = i;
			}
		}
		if (best == null) {
			log("no atlas sites known near spawn");
			return null;
		}
		log("visiting " + best.name() + " (" + best.culture().getSerializedName() + ", " + best.tier().getSerializedName() + ") at "
			+ best.center().toShortString() + " of " + sites.size() + " known sites");
		BlockPos c = best.center();
		teleport(world, c.getX() + 0.5, c.getY() + 40, c.getZ() + 0.5, 0, 60);
		long id = best.settlementId();
		for (int i = 0; i < 120; i++) {
			settle(context, 1_000);
			boolean live = world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).settlement(id) != null);
			if (live) {
				return new Town(id, c);
			}
		}
		log("the historic town did not come live in time");
		return null;
	}

	private static @Nullable Town foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		int bestRelief = Integer.MAX_VALUE;
		for (int r = 96; r <= 720 && (best == null || bestRelief > 3); r += 48) {
			for (int k = 0; k < 12; k++) {
				double a = k * Math.PI / 6;
				BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
				if (w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
					continue;
				}
				int g = w.terrain().groundY(at.getX(), at.getZ());
				if (g <= w.terrain().seaLevel()) {
					continue;
				}
				int lo = g;
				int hi = g;
				for (int dx = -24; dx <= 24; dx += 12) {
					for (int dz = -24; dz <= 24; dz += 12) {
						int h = w.terrain().groundY(at.getX() + dx, at.getZ() + dz);
						lo = Math.min(lo, h);
						hi = Math.max(hi, h);
					}
				}
				if (hi - lo < bestRelief) {
					bestRelief = hi - lo;
					best = at;
				}
			}
		}
		if (best == null) {
			return null;
		}
		SimSettlement s = w.found(best, null, (String) null);
		long nanos = w.simulate(s, DAYS);
		log(String.format(Locale.ROOT, "founded %s (%s) at %s, %d days in %.0f ms", s.name(), s.culture().getSerializedName(), s.center().toShortString(),
			DAYS, nanos / 1e6));
		return new Town(s.id(), s.center());
	}

	private static String describe(SimSettlement s) {
		int herders = 0;
		int burners = 0;
		int fishers = 0;
		int miners = 0;
		for (Resident r : s.residents()) {
			herders += r.job == JobType.HERDER ? 1 : 0;
			burners += r.job == JobType.CHARCOAL_BURNER ? 1 : 0;
			fishers += r.job == JobType.FISHER ? 1 : 0;
			miners += r.job == JobType.MINER ? 1 : 0;
		}
		return s.name() + " (" + s.culture().getSerializedName() + ", " + s.tier().getSerializedName() + "), " + s.population() + " souls: " + herders
			+ " herders, " + burners + " burners, " + fishers + " fishers, " + miners + " miners; plots " + s.plotCounts() + "; logs "
			+ s.stockpile().get(Goods.LOGS) + "; remote sites " + FieldSites.plan(s).sites();
	}

	private static SimSettlement town(MinecraftServer server, Town t) {
		return SimServer.world(server.overworld()).settlement(t.id);
	}

	private static void forceChunks(MinecraftServer server, Town t, boolean force) {
		ServerLevel level = server.overworld();
		BoundingBox b = town(server, t).bounds();
		int r = 5;
		for (int cx = (t.center.getX() >> 4) - r; cx <= (t.center.getX() >> 4) + r; cx++) {
			for (int cz = (t.center.getZ() >> 4) - r; cz <= (t.center.getZ() >> 4) + r; cz++) {
				if (cx >= (b.minX() >> 4) - 1 && cx <= (b.maxX() >> 4) + 1 && cz >= (b.minZ() >> 4) - 1 && cz <= (b.maxZ() >> 4) + 1) {
					level.setChunkForced(cx, cz, force);
				}
			}
		}
	}

	private static void waitApplier(ClientGameTestContext context, TestSingleplayerContext world) {
		for (int i = 0; i < 120; i++) {
			settle(context, 500);
			int pending = world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).applier().pending());
			if (pending == 0 && i > 6) {
				break;
			}
		}
	}

	// ---------------------------------------------------------------- the legs

	/** A counter whose wares match its stock. */
	private static void stall(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		timeTo(world, 4000);
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			Map<Integer, List<Poi>> counters = StallWares.counters(s);
			for (Map.Entry<Integer, List<Poi>> e : counters.entrySet()) {
				Map<Goods, Integer> stock = HearthServices.economy().shopStock(s, e.getKey());
				if (stock.isEmpty()) {
					continue;
				}
				SimPlot p = s.plot(e.getKey());
				log("stall: plot #" + e.getKey() + " " + p.spec().archetype().getPath() + " holds " + stock);
				Poi poi = e.getValue().get(0);
				return look(poi.pos(), poi.facing(), 4, 2.2);
			}
			return null;
		});
		if (view == null) {
			log("stall: no shop with stock and a counter");
			return;
		}
		teleport(world, view[0], view[1], view[2], (float) view[3], (float) view[4]);
		settle(context, 12_000);
		context.takeScreenshot("hearth_work_stall");
		log("stall: showing " + world.getServer().computeOnServer(server -> StallWares.showing(t.id)) + ", " + StallWares.count() + " wares out");
	}

	/** The pen full at 20:00. */
	private static void pen(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		timeTo(world, 14000);
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			for (SimPlot p : s.plots()) {
				if (p.state() == PlotState.BUILT && p.spec().archetype().equals(RemoteSites.PASTURE)) {
					BlockPos c = RemoteSites.centre(p.spec());
					return new double[]{c.getX() - 10.5, c.getY() + 9, c.getZ() - 10.5, -45, 30, p.id()};
				}
			}
			return null;
		});
		if (view == null) {
			log("pen: the town has no pasture");
			return;
		}
		teleport(world, view[0], view[1], view[2], (float) view[3], (float) view[4]);
		settle(context, 10_000);
		context.takeScreenshot("hearth_work_pen");
		log("pen: plot #" + (int) view[5] + ", flock entities of the town " + world.getServer().computeOnServer(server -> Herds.count(t.id)));
	}

	/** The flock with its herder on a far meadow at 10:00. */
	private static void meadow(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		timeTo(world, 4000);
		BlockPos site = world.getServer().computeOnServer(server -> {
			for (RemoteSites.Site s : FieldSites.plan(town(server, t)).sites()) {
				if (s.kind() == RemoteSites.Kind.PASTURE) {
					log("meadow: pasture #" + s.plot() + " drives to " + s.work().toShortString() + ", " + (int) Math.sqrt(s.work().distSqr(t.center))
						+ " blocks from the heart");
					return s.work();
				}
			}
			return null;
		});
		if (site == null) {
			log("meadow: no far meadow (no herders, or no pasture site out of town)");
			return;
		}
		teleport(world, site.getX() - 14.5, surface(world, site) + 10, site.getZ() - 14.5, -45, 28);
		settle(context, 15_000);
		context.takeScreenshot("hearth_work_meadow");
		log("meadow: " + world.getServer().computeOnServer(server -> {
			int herders = 0;
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && f.brain().resident.job == JobType.HERDER && f.blockPosition().distSqr(site) < 48 * 48) {
					herders++;
					log("meadow: herder " + f.brain().resident.fullName() + " " + f.brain().describe());
				}
			}
			return herders + " herders and " + Herds.count(t.id) + " flock entities by the meadow; town embodied " + town(server, t).embodied();
		}));
	}

	/** A smouldering clamp and its burner. */
	private static void clamp(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			for (SimPlot p : s.plots()) {
				if (p.state() == PlotState.BUILT && p.spec().archetype().equals(RemoteSites.CHARCOAL_KILN)) {
					if (s.stockpile().get(Goods.LOGS) + s.stockpile().get(Goods.WOOD) == 0) {
						s.stockpile().add(Goods.LOGS, 40);
					}
					BlockPos c = RemoteSites.centre(p.spec());
					return new double[]{c.getX() - 16.5, c.getY() + 10, c.getZ() - 16.5, -45, 22, p.id()};
				}
			}
			return null;
		});
		if (view == null) {
			log("clamp: the town has no charcoal kiln");
			return;
		}
		int plot = (int) view[5];
		// a burning day: the cycle is the books' (up to three days on)
		for (int i = 0; i < 4; i++) {
			ClampCycle.Stage st = world.getServer().computeOnServer(server -> Clamps.stage(town(server, t), plot));
			log("clamp: day stage " + st);
			if (st == ClampCycle.Stage.BURNING || st == ClampCycle.Stage.COLD) {
				break;
			}
			world.getServer().runCommand("time add 24000");
			settle(context, 2_000);
		}
		timeTo(world, 4000);
		teleport(world, view[0], view[1], view[2], (float) view[3], (float) view[4]);
		settle(context, 12_000);
		context.takeScreenshot("hearth_work_clamp");
	}

	/** A fisher's boat at the dock. */
	private static void boat(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		timeTo(world, 500);
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = town(server, t);
			List<Poi> docks = s.pois(PoiType.DOCK);
			if (docks.isEmpty()) {
				return null;
			}
			Poi d = docks.get(0);
			return look(d.pos(), d.facing().getOpposite(), 10, 6);
		});
		if (view == null) {
			log("boat: the town has no dock");
			return;
		}
		teleport(world, view[0], view[1], view[2], (float) view[3], (float) view[4]);
		settle(context, 20_000);
		context.takeScreenshot("hearth_work_boat");
		log("boat: " + Boats.count() + " boats moored or out");
	}

	/** The Great Mine, where the town's land reaches one. */
	private static void greatMine(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		BlockPos mine = world.getServer().computeOnServer(server -> {
			for (ResourceSite site : town(server, t).sites()) {
				if (GreatMines.isGreat(site)) {
					return site.center();
				}
			}
			return null;
		});
		if (mine == null) {
			log("great mine: none in the town's territory on this seed");
			return;
		}
		log("great mine: at " + mine.toShortString());
		teleport(world, mine.getX() - 18.5, mine.getY() + 14, mine.getZ() - 18.5, -45, 25);
		settle(context, 10_000);
		context.takeScreenshot("hearth_work_great_mine");
	}

	// ---------------------------------------------------------------- helpers

	/** A camera {@code back} blocks in front of a point (along {@code facing}), {@code up} above it, looking at it. */
	private static double[] look(BlockPos at, Direction facing, double back, double up) {
		double x = at.getX() + 0.5 + facing.getStepX() * back;
		double z = at.getZ() + 0.5 + facing.getStepZ() * back;
		double y = at.getY() + up;
		double dx = at.getX() + 0.5 - x;
		double dz = at.getZ() + 0.5 - z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
		float pitch = (float) (Math.atan2(up - 0.5, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		return new double[]{x, y, z, yaw, pitch};
	}

	/** Forward to a time of day (game ticks: 0 is 06:00), never back: the sim's days follow the clock. */
	private static void timeTo(TestSingleplayerContext world, int dayTicks) {
		long now = world.getServer().computeOnServer(server -> server.overworld().getOverworldClockTime());
		long delta = Math.floorMod(dayTicks - now, 24000L);
		if (delta > 0) {
			world.getServer().runCommand("time add " + delta);
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int surface(TestSingleplayerContext world, BlockPos at) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		});
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = true;
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
		System.out.println("[hearth work] " + line);
	}
}
