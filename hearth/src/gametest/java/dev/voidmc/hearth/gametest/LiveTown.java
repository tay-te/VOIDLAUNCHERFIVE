package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.build.BlueprintServiceImpl;
import dev.voidmc.hearth.build.blueprint.Compiler;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.command.Report;
import dev.voidmc.hearth.sim.settlement.SettlementClock;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The live tour with the full catalogue: on the Expanse Earth world (seed 20261003), a Bohemian (temperate) town founded
 * on gentle ground near spawn and grown by the real sim, planner and blueprints (no stand-ins), more settlements founded
 * round it for scale, and then a fly-through with photographs and measurements:
 *
 * <ol>
 *   <li><b>perf</b>: 50+ settlements abstract (the player parked far off): the sim's work per tick (average, worst, ticks
 *   over 5 ms), whole server ticks, hour steps of the largest towns, planner {@code next} (median, p90, max), blueprints
 *   made on the server thread;</li>
 *   <li><b>visit</b>: arriving at the town (the applier writing it, embodiment, folk spawning) with server-tick hitches,
 *   embodied folk and their tick cost, and the client's frame rate there and away;</li>
 *   <li><b>tour</b>: the town from the approach road, its square on a market day, the castle and the cathedral (if the
 *   town grew them), interiors lit at night, folk at work (builders on scaffolding, a miner at a work face, a forester),
 *   a mining town, a declining quarter with houses left empty, a deep town if one was reached.</li>
 * </ol>
 *
 * Screenshots: {@code build/run/clientGameTest/screenshots/*hearth_live_*.png}; every line is printed as {@code [hearth
 * live]}. {@code -Dhearth.tour=live} runs only this; {@code -Dhearth.live.days=N} grows the town N days (default 400);
 * {@code -Dhearth.live.scale=N} founds N more settlements (default 54); {@code -Dhearth.live.skip=perf,tour} skips legs.
 */
public class LiveTown implements FabricClientGameTest {
	private static final int DAYS = Integer.getInteger("hearth.live.days", 400);
	private static final int SCALE = Integer.getInteger("hearth.live.scale", 54);
	private static final String SKIP = System.getProperty("hearth.live.skip", "");

	/** Whole server ticks (start to end), measured here so every module's work is in them. */
	private static final AtomicLong TICK_START = new AtomicLong();
	private static final LongAdder TICKS = new LongAdder();
	private static final LongAdder TICK_NANOS = new LongAdder();
	private static final AtomicLong TICK_MAX = new AtomicLong();
	private static final LongAdder TICKS_OVER_50 = new LongAdder();
	private static boolean hooked;

	private record Town(long id, String name) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("live")) {
			return;
		}
		hook();
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(12);
			// measure frames unthrottled: no vsync, no cap, no AFK limit (30 fps after a minute without input)
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		try (TestSingleplayerContext world = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> {
				s.setSeed("20261003");
				s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				s.setAllowCommands(true);
			})
			.create()) {
			world.getServer().runCommand("gamerule advance_weather false");
			world.getServer().runCommand("gamerule spawn_monsters false");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set 2000");
			// out of every town's reach while they are founded and grown
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("tp @a 30000 300 30000");
			settle(context, 3_000);
			log(world.getServer().computeOnServer(server -> "catalogue: " + HearthServices.blueprints().archetypes().size() + " archetypes, service "
				+ HearthServices.blueprints().getClass().getSimpleName() + "; blueprints on the server thread "
				+ (SimWorld.INLINE_BLUEPRINTS ? "made inline (-Dhearth.sim.inlineBlueprints)" : "made in the background when not ready")));

			Town founded = world.getServer().computeOnServer(LiveTown::foundTown);
			if (founded == null) {
				log("no site for the Bohemian town near spawn");
				return;
			}
			log("town: " + world.getServer().computeOnServer(server -> describe(settlement(server, founded.id))));
			log("town plots: " + world.getServer().computeOnServer(server -> composition(settlement(server, founded.id))));
			log("after growing: " + Compiler.Timings.COUNT.sum() + " blueprints compiled, " + BlueprintServiceImpl.Stats.line());

			List<Long> others = world.getServer().computeOnServer(LiveTown::scale);
			// the tour's town: the largest temperate one of them all (the one founded near spawn, or a patron's town of the
			// scale run that grew bigger)
			Town town = world.getServer().computeOnServer(server -> {
				SimSettlement best = settlement(server, founded.id);
				for (long id : others) {
					SimSettlement s = settlement(server, id);
					if (s.culture() == Culture.TEMPERATE && s.population() > best.population()) {
						best = s;
					}
				}
				return new Town(best.id(), best.name());
			});
			if (town.id != founded.id) {
				others.add(founded.id);
				others.remove(Long.valueOf(town.id));
			}
			log("tour town: " + world.getServer().computeOnServer(server -> describe(settlement(server, town.id))));
			log("tour town plots: " + world.getServer().computeOnServer(server -> composition(settlement(server, town.id))));
			log("why its workshops: " + world.getServer().computeOnServer(server -> {
				// the planner's reasons for the most numerous workshop (a town of 440 with 69 carpentries was seen)
				Map<String, Integer> kinds = new TreeMap<>();
				for (SimPlot p : settlement(server, town.id).plots()) {
					if (p.spec().archetype().getPath().startsWith("work/")) {
						kinds.merge(p.spec().archetype().getPath(), 1, Integer::sum);
					}
				}
				String top = kinds.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
				Map<String, Integer> why = new TreeMap<>();
				for (SimPlot p : settlement(server, town.id).plots()) {
					if (p.spec().archetype().getPath().equals(top)) {
						why.merge(p.reason(), 1, Integer::sum);
					}
				}
				return top + " " + why;
			}));
			if (!skip("perf")) {
				perfAbstract(context, world, town);
			}
			if (!skip("visit")) {
				visit(context, world, town);
			}
			if (!skip("tour")) {
				tour(context, world, town, others);
			}
			log("final: " + Report.lines(SimServer.current(), world.getServer().computeOnServer(server -> SimServer.world(server.overworld()))).size()
				+ " report lines");
			report(world, "end of tour");
		}
	}

	// ---------------------------------------------------------------- growing the towns

	/** A temperate camp on the gentlest temperate ground within reach of spawn, grown {@link #DAYS} days. */
	private static @Nullable Town foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		int bestRelief = Integer.MAX_VALUE;
		for (int r = 128; r <= 2400 && (best == null || bestRelief > 4 || r < 640); r += 64) {
			for (int k = 0; k < 16; k++) {
				double a = k * Math.PI / 8 + r * 0.01;
				BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
				int g = w.terrain().groundY(at.getX(), at.getZ());
				if (g <= w.terrain().seaLevel() + 2) {
					continue;
				}
				int lo = g;
				int hi = g;
				for (int dx = -48; dx <= 48; dx += 16) {
					for (int dz = -48; dz <= 48; dz += 16) {
						int h = w.terrain().groundY(at.getX() + dx, at.getZ() + dz);
						lo = Math.min(lo, h);
						hi = Math.max(hi, h);
					}
				}
				if (hi - lo >= bestRelief) {
					continue;
				}
				Culture c = CultureVote.at(at.getX(), at.getZ(), w.probe()::biomeHolder, HearthServices::culture);
				if (c != Culture.TEMPERATE || w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
					continue;
				}
				bestRelief = hi - lo;
				best = at;
			}
		}
		if (best == null) {
			return null;
		}
		long t0 = System.nanoTime();
		SimSettlement s = w.found(best, Culture.TEMPERATE, null);
		long found = System.nanoTime() - t0;
		long nanos = 0;
		// grown in spells; with a patron (the default) its stores and purse are topped up between them, as a rich player
		// supplying the town would: on its own a town's purse runs dry and its builds stall for goods long before it is
		// the city a castle and a cathedral need (see the log: growth without a patron is the scale towns')
		for (int d = 0; d < DAYS; d += 20) {
			if (PATRON) {
				patron(s);
			}
			nanos += w.simulate(s, Math.min(20, DAYS - d));
			if (d % 100 == 0) {
				log(String.format(Locale.ROOT, "  day %d: %d souls, %s, %d plots %s", d + 20, s.population(), s.tier().getSerializedName(), s.plots().size(), s.plotCounts()));
			}
		}
		log(String.format(Locale.ROOT, "founded %s at %s (relief %d) in %.0f ms; grew %d days in %.1f s: %d souls, %s", s.name(), s.center().toShortString(), bestRelief,
			found / 1e6, DAYS, nanos / 1e9, s.population(), s.tier().getSerializedName()));
		if (s.plots().stream().noneMatch(SimPlot::isActive)) {
			// stopped growing: what the planner says when asked once more
			var next = HearthServices.planner().next(s, w.terrain(), s.random());
			log("  " + s.name() + " builds nothing more (" + s.population() + " souls, " + s.beds() + " beds, " + s.residents().stream()
				.filter(r -> r.job == JobType.BUILDER).count() + " builders, prosperity " + String.format(Locale.ROOT, "%.2f", s.land().prosperity()) + ", capacity "
				+ s.land().capacity() + ", " + s.land().why() + "); planner.next: " + (next == null ? "nothing" : next.spec().archetype() + " (" + next.reason() + ")"));
		}
		return new Town(s.id(), s.name());
	}

	private static final boolean PATRON = !Boolean.getBoolean("hearth.live.nopatron");

	/** Tops a town's stores up to three quarters of what they hold and its purse to 3000 bits, as a rich patron would. */
	private static void patron(SimSettlement s) {
		for (dev.voidmc.hearth.api.Goods g : dev.voidmc.hearth.api.Goods.VALUES) {
			int want = s.stockpile().capacity(g) * 3 / 4 - s.stockpile().get(g);
			if (want > 0) {
				s.stockpile().add(g, want);
			}
		}
		if (s.treasury() < 3000) {
			s.deposit(3000 - s.treasury());
		}
	}

	/**
	 * {@link #SCALE} more settlements on a grid round the town (aged 30-200 days), as the dev harness's scale run; every
	 * other one with a patron (so the world has towns of a size real players' worlds will, not only stalled camps), the rest
	 * on their own.
	 */
	private static List<Long> scale(MinecraftServer server) {
		SimWorld w = SimServer.world(server.overworld());
		List<Long> made = new ArrayList<>();
		RandomSource random = RandomSource.create(7);
		BlockPos spawn = server.overworld().getRespawnData().pos();
		int side = (int) Math.ceil(Math.sqrt(SCALE * 5.0));
		long t0 = System.nanoTime();
		int days = 0;
		long compiles0 = Compiler.Timings.COUNT.sum();
		for (int i = 0; i < side * side && made.size() < SCALE; i++) {
			int gx = i % side - side / 2;
			int gz = i / side - side / 2;
			BlockPos at = new BlockPos(spawn.getX() + gx * 640 + 320, 0, spawn.getZ() + gz * 640 + 320);
			if (w.whyNotFound(at, 300, true) != null) {
				continue;
			}
			SimSettlement s = w.found(at, null, null);
			int d = 30 + random.nextInt(171);
			days += d;
			boolean helped = PATRON && made.size() % 2 == 0;
			for (int k = 0; k < d; k += 20) {
				if (helped) {
					patron(s);
				}
				w.simulate(s, Math.min(20, d - k));
			}
			made.add(s.id());
		}
		int pop = 0;
		for (SimSettlement s : w.settlements()) {
			pop += s.population();
		}
		int[] helped = new int[2];
		int[] alone = new int[2];
		for (int i = 0; i < made.size(); i++) {
			SimSettlement s = w.settlement(made.get(i));
			int[] into = PATRON && i % 2 == 0 ? helped : alone;
			into[0]++;
			into[1] += s.population();
		}
		log(String.format(Locale.ROOT, "scale: %d towns with a patron, %.1f souls avg; %d on their own, %.1f souls avg", helped[0], helped[1] / (double) Math.max(1, helped[0]),
			alone[0], alone[1] / (double) Math.max(1, alone[0])));
		log(String.format(Locale.ROOT, "scale: founded %d settlements and aged them %d sim days in all in %.1f s (%d blueprints compiled); %d settlements, %d souls",
			made.size(), days, (System.nanoTime() - t0) / 1e9, Compiler.Timings.COUNT.sum() - compiles0, w.settlements().size(), pop));
		return made;
	}

	// ---------------------------------------------------------------- performance, everything abstract

	private void perfAbstract(ClientGameTestContext context, TestSingleplayerContext world, Town town) {
		// everything stays abstract; restart the counters and watch a minute
		world.getServer().runOnServer(server -> resetCounters());
		settle(context, 60_000);
		report(world, "abstract, 60 s, " + world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).settlements().size()) + " settlements");
		// hour steps of the largest towns
		world.getServer().runOnServer(server -> {
			SimWorld w = SimServer.world(server.overworld());
			List<SimSettlement> big = new ArrayList<>(w.settlements());
			big.sort(Comparator.comparingInt(SimSettlement::population).reversed());
			for (int i = 0; i < Math.min(4, big.size()); i++) {
				log("bench " + bench(w, big.get(i)));
			}
		});
	}

	private static String bench(SimWorld w, SimSettlement s) {
		int reps = 200;
		long total = 0;
		long max = 0;
		long plannerNanos = SettlementClock.PlannerTimings.LIVE_NANOS.sum();
		long plannerCalls = SettlementClock.PlannerTimings.LIVE_CALLS.sum();
		long bp0 = BlueprintServiceImpl.Stats.SERVER_NANOS.sum();
		List<Long> each = new ArrayList<>();
		for (int i = 0; i < reps; i++) {
			s.addTimeOffset(1200);
			long t0 = System.nanoTime();
			SettlementClock.advance(s, w.simNow(s));
			long dt = System.nanoTime() - t0;
			total += dt;
			max = Math.max(max, dt);
			each.add(dt);
		}
		each.sort(null);
		long planner = SettlementClock.PlannerTimings.LIVE_NANOS.sum() - plannerNanos;
		long calls = SettlementClock.PlannerTimings.LIVE_CALLS.sum() - plannerCalls;
		long bp = BlueprintServiceImpl.Stats.SERVER_NANOS.sum() - bp0;
		return String.format(Locale.ROOT,
			"%s (%d souls, %d plots, %d sites): hour step %.4f ms avg, median %.4f, p90 %.4f, max %.2f over %d steps; planner %d calls %.1f ms; blueprints made in the steps %.1f ms; "
				+ "without planner and blueprints %.4f ms avg",
			s.name(), s.population(), s.plots().size(), s.land().sites().size(), total / 1e6 / reps, each.get(reps / 2) / 1e6, each.get(reps * 9 / 10) / 1e6, max / 1e6,
			reps, calls, planner / 1e6, bp / 1e6, Math.max(0, total - planner - bp) / 1e6 / reps);
	}

	// ---------------------------------------------------------------- the visit: arriving, embodiment, folk

	private void visit(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		world.getServer().runCommand("gamemode creative @a");
		BlockPos c = world.getServer().computeOnServer(server -> settlement(server, t.id).center());
		// the client's frame rate far from any town first
		settle(context, 5_000);
		double fpsAway = fps(context, 8);
		world.getServer().runOnServer(server -> resetCounters());
		teleport(world, c.getX() + 0.5, surface(world, c) + 30, c.getZ() + 0.5 - 60, 0, 25);
		long arrive = System.nanoTime();
		settle(context, 30_000);
		report(world, "arrival, first 30 s (chunks load, the applier writes, folk come out)");
		world.getServer().runOnServer(server -> resetCounters());
		settle(context, 30_000);
		report(world, "embodied, next 30 s");
		double fpsTown = fps(context, 8);
		log(String.format(Locale.ROOT, "client: %.0f fps away from towns, %.0f fps over the town (render distance 12); %.0f s since arrival", fpsAway, fpsTown,
			(System.nanoTime() - arrive) / 1e9));
	}

	// ---------------------------------------------------------------- the tour

	private void tour(ClientGameTestContext context, TestSingleplayerContext world, Town t, List<Long> others) {
		world.getServer().runCommand("time set 3000");
		settle(context, 3_000);
		approach(context, world, t);
		square(context, world, t);
		landmark(context, world, t, "noble/castle", "castle");
		landmark(context, world, t, "noble/keep", "keep");
		landmark(context, world, t, "landmark/cathedral", "cathedral");
		landmark(context, world, t, "civic/chapel", "chapel");
		landmark(context, world, t, "civic/town_hall", "town_hall");
		if (!builders(context, world, t)) {
			// the town has nothing under way: the town with the most going up instead
			Long busy = world.getServer().computeOnServer(server -> pick(server, others, s -> (double) s.plots().stream()
				.filter(p -> p.state() == PlotState.BUILDING && !p.spec().archetype().getPath().startsWith("infra/")).count()));
			if (busy != null) {
				Town b = new Town(busy, world.getServer().computeOnServer(server -> settlement(server, busy).name()));
				log("builders: going to " + world.getServer().computeOnServer(server -> describe(settlement(server, busy))));
				BlockPos c = world.getServer().computeOnServer(server -> settlement(server, busy).center());
				teleport(world, c.getX() + 0.5, surface(world, c) + 12, c.getZ() + 0.5, 0, 30);
				settle(context, 40_000);
				builders(context, world, b);
			}
		}
		// foresters work the woods: stand in the town's nearest wood a while so they are about
		BlockPos wood = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			BlockPos best = null;
			double bestD = Double.MAX_VALUE;
			for (var site : s.land().sites()) {
				if (site.kind() == dev.voidmc.hearth.api.ResourceSite.Kind.WOODS && site.distanceTo(s.center()) < bestD) {
					bestD = site.distanceTo(s.center());
					best = site.center();
				}
			}
			return best;
		});
		if (wood != null) {
			teleport(world, wood.getX() + 0.5, surface(world, wood) + 10, wood.getZ() + 0.5, 0, 30);
			settle(context, 30_000);
		}
		workers(context, world, t, JobType.FORESTER, "forester");
		workers(context, world, t, JobType.QUARRIER, "quarrier");
		Long mining = world.getServer().computeOnServer(server -> pick(server, others, LiveTown::miningScore));
		if (mining != null) {
			Town m = new Town(mining, world.getServer().computeOnServer(server -> settlement(server, mining).name()));
			log("mining town: " + world.getServer().computeOnServer(server -> describe(settlement(server, mining)) + "; " + composition(settlement(server, mining))));
			overview(context, world, m, "mining_town");
			extraction(context, world, m);
			workers(context, world, m, JobType.MINER, "miner");
		} else {
			log("mining town: none of the settlements opened a mine or a quarry");
		}
		final Long decl = world.getServer().computeOnServer(server -> {
			// houses already left empty first, then the declining town with the most beds
			Long id = pick(server, withTown(others, t), s -> s.land().deserted().size() * 1000.0 + (s.land().mood() < 0 ? s.beds()
				: s.land().deserted().isEmpty() ? Double.NEGATIVE_INFINITY : 0));
			return id == null || settlement(server, id).land().mood() >= 0 && settlement(server, id).land().deserted().isEmpty() ? null : id;
		});
		if (decl != null && world.getServer().computeOnServer(server -> settlement(server, decl).land().deserted().size() < 3
			&& settlement(server, decl).land().mood() < 0 && settlement(server, decl).population() >= 12)) {
			// none has emptied a house yet: a hard year in the town whose fortunes are lowest (a third of its people gone),
			// then the sim's own decline (houses left empty from the edge in, weathering every 24 days) over 100 days
			log("declining: " + world.getServer().computeOnServer(server -> {
				SimSettlement s = settlement(server, decl);
				SimWorld w = SimServer.world(server.overworld());
				int before = s.population();
				List<java.util.UUID> ids = new ArrayList<>();
				for (var r : s.residents()) {
					ids.add(r.id);
				}
				for (int i = 0; i < ids.size() / 2; i++) {
					s.removeResident(ids.get(ids.size() - 1 - i));
				}
				w.simulate(s, 100);
				return s.name() + " (mood " + s.land().mood() + ", " + s.land().why() + "): " + before + " souls, a third gone, 100 days later " + s.population()
					+ " souls, " + s.land().deserted().size() + " houses left empty";
			}));
		}
		if (decl != null && world.getServer().computeOnServer(server -> !settlement(server, decl).land().deserted().isEmpty())) {
			Town d = new Town(decl, world.getServer().computeOnServer(server -> settlement(server, decl).name()));
			log("declining: " + world.getServer().computeOnServer(server -> describe(settlement(server, decl)) + "; left empty "
				+ settlement(server, decl).land().deserted()));
			deserted(context, world, d);
		} else {
			log("declining: no settlement has houses left empty");
		}
		Long deep = world.getServer().computeOnServer(server -> pick(server, withTown(others, t), s -> (double) deepPlots(s)));
		if (deep != null && world.getServer().computeOnServer(server -> deepPlots(settlement(server, deep)) > 0)) {
			log("deep town: " + world.getServer().computeOnServer(server -> describe(settlement(server, deep))));
			deepTown(context, world, new Town(deep, ""));
		} else {
			log("deep town: none reached a realm");
		}
		night(context, world, t);
	}

	private void approach(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		// the road plot furthest from the centre, looking back along it at the town
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			BlockPos c = s.center();
			SimPlot far = null;
			double farD = 0;
			for (SimPlot p : s.plots()) {
				if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals("infra/road")) {
					double d = centre(p).distSqr(c);
					if (d > farD) {
						farD = d;
						far = p;
					}
				}
			}
			BlockPos from = far == null ? c.offset(0, 0, -(s.bounds().getZSpan() / 2) - 24) : centre(far);
			// a little beyond the road's end, back toward the town
			double dx = from.getX() - c.getX();
			double dz = from.getZ() - c.getZ();
			double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
			int x = (int) (from.getX() + dx / len * 18);
			int z = (int) (from.getZ() + dz / len * 18);
			int y = groundAt(server.overworld(), x, z) + 4;
			return new double[]{x + 0.5, y, z + 0.5, c.getX(), groundAt(server.overworld(), c.getX(), c.getZ()) + 6, c.getZ()};
		});
		shootAt(context, world, "approach", view);
		// and from higher up, the whole town
		double[] high = view.clone();
		high[1] += 22;
		shootAt(context, world, "approach_high", high);
	}

	private void square(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		BlockPos plaza = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			for (SimPlot p : s.plots()) {
				if (p.state() == PlotState.BUILT && p.spec().archetype().getPath().equals("landscape/plaza")) {
					return centre(p);
				}
			}
			List<Poi> m = s.pois(PoiType.MEETING_POINT);
			return m.isEmpty() ? s.center() : m.getFirst().pos();
		});
		// a market day: the social module sets out stalls and the fronts put out their wares
		world.getServer().runCommand("execute in minecraft:overworld positioned " + plaza.getX() + " " + plaza.getY() + " " + plaza.getZ()
			+ " run tp @a ~ ~3 ~");
		settle(context, 3_000);
		world.getServer().runCommand("execute as @a at @s run hearth social force market");
		settle(context, 15_000);
		int y = world.getServer().computeOnServer(server -> groundAt(server.overworld(), plaza.getX(), plaza.getZ()));
		shootAt(context, world, "market_square", new double[]{plaza.getX() - 16.5, y + 11, plaza.getZ() - 16.5, plaza.getX(), y + 1, plaza.getZ()});
		shootAt(context, world, "market_square_low", new double[]{plaza.getX() + 9.5, y + 3, plaza.getZ() + 6.5, plaza.getX(), y + 1.5, plaza.getZ()}, View.MID);
		log("market: " + world.getServer().computeOnServer(server -> {
			int folk = 0;
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.blockPosition().distSqr(plaza) < 24 * 24) {
					folk++;
				}
			}
			return folk + " folk within 24 blocks of the square";
		}));
	}

	private void landmark(ClientGameTestContext context, TestSingleplayerContext world, Town t, String archetype, String name) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			for (SimPlot p : s.plots()) {
				if (p.spec().archetype().getPath().equals(archetype) && (p.state() == PlotState.BUILT || p.state() == PlotState.BUILDING)) {
					var r = p.reach();
					double cx = (r.minX() + r.maxX()) / 2.0;
					double cz = (r.minZ() + r.maxZ()) / 2.0;
					double size = Math.max(r.getXSpan(), r.getZSpan());
					double h = p.spec().floorY() + p.spec().maxHeight() * 0.4;
					// from the front, off to one side, far enough to take it in
					var f = p.spec().facing();
					double fx = f.getStepX();
					double fz = f.getStepZ();
					double sx = -fz;
					double sz = fx;
					double x = cx + (fx * 1.1 + sx * 0.6) * size;
					double z = cz + (fz * 1.1 + sz * 0.6) * size;
					log(name + ": plot #" + p.id() + " " + p.state().getSerializedName() + " at " + (int) cx + "," + p.spec().floorY() + "," + (int) cz + ", "
						+ r.getXSpan() + "x" + r.getZSpan());
					return new double[]{x, Math.max(h + size * 0.35, groundAt(server.overworld(), (int) x, (int) z) + 3), z, cx, h, cz};
				}
			}
			return null;
		});
		if (view == null) {
			log(name + ": the town has none");
			return;
		}
		shootAt(context, world, name, view);
	}

	private boolean builders(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = null;
		// a builder placing blocks up on scaffolding (or high on a wall); fetching and walking do not count, until the last try
		for (int tries = 0; tries < 8 && view == null; tries++) {
			boolean last = tries == 7;
			view = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				double[] fallback = null;
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() == null || f.brain().settlement.id() != t.id || f.brain().resident.job != JobType.BUILDER) {
						continue;
					}
					String doing = f.brain().describe();
					if (!doing.contains("plot")) {
						continue;
					}
					BlockPos at = f.blockPosition();
					double[] v = {0, 0, 0, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5};
					boolean scaffold = false;
					for (int dy = -2; dy <= 1; dy++) {
						if (level.getBlockState(at.offset(0, dy, 0)).is(Blocks.SCAFFOLDING)) {
							scaffold = true;
						}
					}
					boolean placing = !doing.contains("fetch") && !doing.contains("walk");
					if (placing && (scaffold || f.getY() > groundAt(level, at.getX(), at.getZ()) + 2)) {
						log("builder: " + f.folkName() + " " + doing + " at " + at.toShortString() + (scaffold ? " on scaffolding" : " up high"));
						return v;
					}
					if (fallback == null && (placing || last)) {
						fallback = v;
						if (last) {
							log("builder: " + f.folkName() + " " + doing + " at " + at.toShortString());
						}
					}
				}
				return last ? fallback : null;
			});
			if (view == null && !last) {
				settle(context, 5_000);
			}
		}
		if (view == null) {
			log("builder: none at work on a plot; " + world.getServer().computeOnServer(server -> settlement(server, t.id).plotCounts()));
			return false;
		}
		shootAt(context, world, "builder", view, View.MID);
		settle(context, 10_000);
		shootAt(context, world, "builder_later", view, View.MID);
		return true;
	}

	private void workers(ClientGameTestContext context, TestSingleplayerContext world, Town t, JobType job, String name) {
		double[] view = null;
		for (int tries = 0; tries < 6 && view == null; tries++) {
			view = world.getServer().computeOnServer(server -> {
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() != null && f.brain().settlement.id() == t.id && f.brain().resident.job == job && !f.isSleeping()) {
						BlockPos at = f.blockPosition();
						log(name + ": " + f.folkName() + " " + f.brain().describe() + " at " + at.toShortString());
						return new double[]{at.getX() - 5.5, at.getY() + 3, at.getZ() - 5.5, at.getX(), at.getY() + 1, at.getZ()};
					}
				}
				return null;
			});
			if (view == null) {
				// the embodiment manager may not have brought them out yet: stand nearer their work
				settle(context, 5_000);
			}
		}
		if (view == null) {
			log(name + ": no embodied " + job.getSerializedName() + " in " + t.name + " ("
				+ world.getServer().computeOnServer(server -> settlement(server, t.id).residents().stream().filter(r -> r.job == job).count()) + " residents)");
			return;
		}
		shootAt(context, world, name, view, View.CLOSE);
	}

	private void overview(ClientGameTestContext context, TestSingleplayerContext world, Town t, String name) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			BlockPos c = s.center();
			int y = groundAt(server.overworld(), c.getX(), c.getZ());
			return new double[]{c.getX() - 40.5, y + 34, c.getZ() - 40.5, c.getX(), y, c.getZ()};
		});
		teleport(world, view[0], view[1], view[2], 0, 0);
		settle(context, 20_000);
		shootAt(context, world, name, view);
	}

	private void extraction(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			for (SimPlot p : s.plots()) {
				String path = p.spec().archetype().getPath();
				if (path.startsWith("extract/") && p.state() != PlotState.PLANNED) {
					var r = p.reach();
					double cx = (r.minX() + r.maxX()) / 2.0;
					double cz = (r.minZ() + r.maxZ()) / 2.0;
					int y = p.spec().floorY();
					log("working: " + path + " plot #" + p.id() + " " + p.state().getSerializedName() + " at stage " + p.stage() + " at " + (int) cx + "," + y + "," + (int) cz);
					return new double[]{cx - r.getXSpan() * 0.8, y + 14, cz - r.getZSpan() * 0.8, cx, y, cz};
				}
			}
			return null;
		});
		if (view != null) {
			teleport(world, view[0], view[1], view[2], 0, 0);
			settle(context, 10_000);
			shootAt(context, world, "working", view);
		}
	}

	private void deserted(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			for (int id : s.land().deserted()) {
				SimPlot p = s.plot(id);
				if (p != null) {
					BlockPos c = centre(p);
					int y = p.spec().floorY();
					log("left empty: plot #" + id + " " + p.spec().archetype().getPath() + " " + p.state().getSerializedName() + " decay "
						+ SimSettlement.decay(p.spec()) + " at " + c.toShortString());
					return new double[]{c.getX() - 14.5, y + 10, c.getZ() - 14.5, c.getX(), y + 2, c.getZ()};
				}
			}
			return null;
		});
		if (view == null) {
			return;
		}
		teleport(world, view[0], view[1], view[2], 0, 0);
		settle(context, 15_000);
		// what each building there is in the record and in the world (several stood roofless in a first look)
		for (String line : world.getServer().computeOnServer(server -> {
			List<String> out = new ArrayList<>();
			SimSettlement s = settlement(server, t.id);
			ServerLevel level = server.overworld();
			for (SimPlot p : s.plots()) {
				if (p.spec().archetype().getPath().startsWith("infra/")) {
					continue;
				}
				BlockPos c = centre(p);
				var chunk = level.getChunkSource().getChunkNow(c.getX() >> 4, c.getZ() >> 4);
				long ledger = chunk == null ? -1 : dev.voidmc.hearth.sim.apply.ChunkLedgers.of(chunk).get(s.id(), p.id());
				var f = s.facts(p);
				out.add(String.format(Locale.ROOT, "  #%d %s %s stage %d/%d cursor %s, may be written to %s, the chunk has %s%s%s", p.id(), p.spec().archetype().getPath(),
					p.state().getSerializedName(), p.stage(), f.stageCount(), dev.voidmc.hearth.sim.settlement.Cursor.toString(p.cursor()),
					dev.voidmc.hearth.sim.settlement.Cursor.toString(s.applicable(p)), ledger < 0 ? "not loaded" : dev.voidmc.hearth.sim.settlement.Cursor.toString(ledger),
					p.waitingForGoods() ? ", waiting for " + p.stalledOn() : "", p.spec().hasParam("decay") ? ", decay " + SimSettlement.decay(p.spec()) : ""));
			}
			return out;
		})) {
			log("declining town" + line);
		}
		shootAt(context, world, "declining", view);
		double[] wide = view.clone();
		wide[0] -= 16;
		wide[1] += 14;
		wide[2] -= 16;
		shootAt(context, world, "declining_wide", wide);
	}

	private void deepTown(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		double[] view = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, t.id);
			for (SimPlot p : s.plots()) {
				if (p.spec().archetype().getPath().equals("deep/hall") || p.spec().archetype().getPath().equals("deep/market")) {
					BlockPos c = centre(p);
					int y = p.spec().floorY();
					return new double[]{c.getX() - 16.5, y + 8, c.getZ() - 16.5, c.getX(), y + 3, c.getZ()};
				}
			}
			return null;
		});
		if (view != null) {
			teleport(world, view[0], view[1], view[2], 0, 0);
			settle(context, 15_000);
			shootAt(context, world, "deep_town", view);
		}
	}

	private void night(ClientGameTestContext context, TestSingleplayerContext world, Town t) {
		world.getServer().runCommand("time set 18000");
		settle(context, 5_000);
		double[] over = world.getServer().computeOnServer(server -> {
			BlockPos c = settlement(server, t.id).center();
			int y = groundAt(server.overworld(), c.getX(), c.getZ());
			return new double[]{c.getX() - 30.5, y + 22, c.getZ() - 30.5, c.getX(), y, c.getZ()};
		});
		shootAt(context, world, "night_town", over);
		// inside: a finished house, a tavern or an inn, from a seat or a bed looking into the room
		String[] kinds = {"commerce/tavern", "commerce/inn", "house/townhouse", "house/cottage", "house/farmhouse", "civic/chapel"};
		int n = 0;
		for (String kind : kinds) {
			double[] in = world.getServer().computeOnServer(server -> interior(server, t, kind));
			if (in != null) {
				shootAt(context, world, "night_interior_" + kind.substring(kind.indexOf('/') + 1), in, View.INDOOR);
				log("interior " + kind + ": block light over the " + "subject " + world.getServer().computeOnServer(server -> server.overworld()
					.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, BlockPos.containing(in[3], in[4], in[5]))));
				if (++n >= 3) {
					break;
				}
			}
		}
		world.getServer().runCommand("time set 3000");
	}

	/** A spot inside a built plot of this kind, looking across the room: from beside a table, seat or bed. */
	private static double @Nullable [] interior(MinecraftServer server, Town t, String kind) {
		SimSettlement s = settlement(server, t.id);
		ServerLevel level = server.overworld();
		for (SimPlot p : s.plots()) {
			if (p.state() != PlotState.BUILT || !p.spec().archetype().getPath().equals(kind)) {
				continue;
			}
			var bp = HearthServices.blueprints().blueprint(p.spec(), SimServer.world(level).terrain());
			for (PoiType type : new PoiType[]{PoiType.TABLE, PoiType.HEARTH, PoiType.SEAT, PoiType.BED}) {
				for (Poi poi : bp.pois()) {
					if (poi.type() == type && !level.canSeeSky(poi.pos().above(2))) {
						// the subject: the table, hearth, seat or bed, a little above it; the camera found round it (View.INDOOR)
						return new double[]{0, 0, 0, poi.pos().getX() + 0.5, poi.pos().getY() + 1.0, poi.pos().getZ() + 0.5};
					}
				}
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- measuring

	private static void hook() {
		if (hooked) {
			return;
		}
		hooked = true;
		ServerTickEvents.START_SERVER_TICK.register(server -> {
			serverThread = Thread.currentThread();
			TICK_START.set(System.nanoTime());
			tickRunning = true;
		});
		Thread dog = new Thread(LiveTown::watch, "hearth-live-watchdog");
		dog.setDaemon(true);
		dog.start();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickRunning = false;
			long dt = System.nanoTime() - TICK_START.get();
			TICKS.increment();
			TICK_NANOS.add(dt);
			TICK_MAX.accumulateAndGet(dt, Math::max);
			if (dt > 50_000_000L) {
				TICKS_OVER_50.increment();
			}
		});
	}

	private static volatile Thread serverThread;
	private static volatile boolean tickRunning;
	private static final int HITCH_MS = Integer.getInteger("hearth.live.hitch", 60);

	/** Prints where the server thread is when a tick has run past {@link #HITCH_MS} (once per tick, a few dozen at most). */
	private static void watch() {
		long dumpedFor = -1;
		int dumps = 0;
		while (dumps < 60) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				return;
			}
			long start = TICK_START.get();
			Thread t = serverThread;
			if (t == null || !tickRunning || start == dumpedFor || System.nanoTime() - start < HITCH_MS * 1_000_000L) {
				continue;
			}
			dumpedFor = start;
			dumps++;
			StringBuilder b = new StringBuilder("hitch: tick running " + (System.nanoTime() - start) / 1_000_000 + " ms, server thread at:");
			StackTraceElement[] st = t.getStackTrace();
			int shown = 0;
			for (StackTraceElement e : st) {
				String c = e.getClassName();
				if (shown < 28 && (c.startsWith("dev.voidmc") || shown < 6 || c.startsWith("net.minecraft.server.level") || c.startsWith("net.minecraft.world"))) {
					b.append(System.lineSeparator()).append("      at ").append(e);
					shown++;
				}
			}
			log(b.toString());
		}
	}

	private static long folkNanos0;
	private static long folkTicks0;
	private static long embodyNanos0;
	private static long serverTicks0;
	private static long simTicks0;
	private static long simNanos0;
	private static long applierNanos0;
	private static long applierBlocks0;

	private static void resetCounters() {
		TICKS.reset();
		TICK_NANOS.reset();
		TICK_MAX.set(0);
		TICKS_OVER_50.reset();
		SimServer s = SimServer.current();
		s.stats().maxTickNanos.set(0);
		s.stats().slowTicks.reset();
		s.stats().maxStepNanos.set(0);
		simTicks0 = s.ticks();
		simNanos0 = s.stats().tickNanos.sum();
		SimWorld w = s.worlds().getFirst();
		w.applier().maxTickNanos.set(0);
		applierNanos0 = w.applier().nanos.sum();
		applierBlocks0 = w.applier().blocksPlaced.sum();
		SettlementClock.PlannerTimings.reset();
		SettlementClock.WorstStep.reset();
		BlueprintServiceImpl.Stats.SERVER_MAX.set(0);
		BlueprintServiceImpl.Stats.SERVER_MISSES.reset();
		BlueprintServiceImpl.Stats.SERVER_NANOS.reset();
		BlueprintServiceImpl.Stats.MISSES.reset();
		BlueprintServiceImpl.Stats.NANOS.reset();
		BlueprintServiceImpl.Stats.HITS.reset();
		folkNanos0 = Folk.SERVER_TICK_NANOS.sum();
		folkTicks0 = Folk.SERVER_TICKS.sum();
		embodyNanos0 = NpcModule.EMBODIMENT_NANOS.sum();
		serverTicks0 = TICKS.sum();
	}

	private static void report(TestSingleplayerContext world, String what) {
		for (String line : world.getServer().computeOnServer(server -> {
			List<String> out = new ArrayList<>();
			SimServer s = SimServer.current();
			SimWorld w = SimServer.world(server.overworld());
			long ticks = Math.max(1, TICKS.sum() - serverTicks0);
			long simTicks = Math.max(1, s.ticks() - simTicks0);
			out.add(String.format(Locale.ROOT, "== %s: server ticks %d, %.2f ms avg, %.1f ms max, %d over 50 ms", what, TICKS.sum(), TICK_NANOS.sum() / 1e6 / ticks,
				TICK_MAX.get() / 1e6, TICKS_OVER_50.sum()));
			out.add(String.format(Locale.ROOT, "   sim tick work %.4f ms avg, %.2f ms max, %d ticks over 5 ms; worst step %.2f ms", (s.stats().tickNanos.sum() - simNanos0) / 1e6 / simTicks,
				s.stats().maxTickNanos.get() / 1e6, s.stats().slowTicks.sum(), s.stats().maxStepNanos.get() / 1e6));
			out.add(String.format(Locale.ROOT, "   applier %.3f ms/tick avg, %.2f ms max in a tick, %d blocks, %d queued", (w.applier().nanos.sum() - applierNanos0) / 1e6 / simTicks,
				w.applier().maxTickNanos.get() / 1e6, w.applier().blocksPlaced.sum() - applierBlocks0, w.applier().pending()));
			double[] pc = SettlementClock.PlannerTimings.percentiles();
			out.add(String.format(Locale.ROOT, "   planner.next %d live calls, median %.2f ms, p90 %.2f ms, max %.1f ms", SettlementClock.PlannerTimings.LIVE_CALLS.sum(), pc[0],
				pc[1], pc[2]));
			out.add("   " + BlueprintServiceImpl.Stats.line());
			out.add("   worst step: " + SettlementClock.WorstStep.line());
			long folkTicks = Folk.SERVER_TICKS.sum() - folkTicks0;
			int embodied = NpcModule.embodiment().count();
			out.add(String.format(Locale.ROOT, "   folk: %d embodied (%d entities), entity ticks %.3f ms per server tick (%.1f µs per folk tick), embodiment manager %.3f ms per tick",
				embodied, NpcModule.embodiment().entities().size(), (Folk.SERVER_TICK_NANOS.sum() - folkNanos0) / 1e6 / ticks,
				folkTicks == 0 ? 0 : (Folk.SERVER_TICK_NANOS.sum() - folkNanos0) / 1e3 / folkTicks, (NpcModule.EMBODIMENT_NANOS.sum() - embodyNanos0) / 1e6 / ticks));
			for (String l : Report.lines(s, w)) {
				out.add("   " + l);
			}
			return out;
		})) {
			log(line);
		}
	}

	private static double fps(ClientGameTestContext context, int seconds) {
		double sum = 0;
		for (int i = 0; i < seconds; i++) {
			settle(context, 1_000);
			sum += context.computeOnClient(mc -> mc.getFps());
		}
		return sum / seconds;
	}

	// ---------------------------------------------------------------- picking towns

	private static @Nullable Long pick(MinecraftServer server, List<Long> ids, java.util.function.ToDoubleFunction<SimSettlement> score) {
		Long best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (long id : ids) {
			SimSettlement s = settlement(server, id);
			if (s == null) {
				continue;
			}
			double sc = score.applyAsDouble(s);
			if (sc > bestScore) {
				bestScore = sc;
				best = id;
			}
		}
		return best;
	}

	private static List<Long> withTown(List<Long> others, Town t) {
		List<Long> all = new ArrayList<>(others);
		all.add(t.id);
		return all;
	}

	private static double miningScore(SimSettlement s) {
		double score = 0;
		for (SimPlot p : s.plots()) {
			String path = p.spec().archetype().getPath();
			if (p.state() != PlotState.PLANNED && (path.equals("extract/mine") || path.equals("extract/shaft"))) {
				score += 100;
			} else if (p.state() != PlotState.PLANNED && path.startsWith("extract/")) {
				score += 10;
			}
		}
		return score == 0 ? Double.NEGATIVE_INFINITY : score + s.population() * 0.1;
	}

	private static int deepPlots(SimSettlement s) {
		int n = 0;
		for (SimPlot p : s.plots()) {
			if (p.spec().archetype().getPath().startsWith("deep/") && p.state() != PlotState.PLANNED) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- helpers

	private boolean skip(String leg) {
		for (String s : SKIP.split(",")) {
			if (s.trim().equals(leg)) {
				return true;
			}
		}
		return false;
	}

	private static SimSettlement settlement(MinecraftServer server, long id) {
		return SimServer.world(server.overworld()).settlement(id);
	}

	private static String describe(SimSettlement s) {
		return String.format(Locale.ROOT, "#%d %s: %s %s at %s, %d souls, %d beds, %d plots %s, treasury %d, prosperity %.2f, %d left empty", s.id(), s.name(),
			s.culture().getSerializedName(), s.tier().getSerializedName(), s.center().toShortString(), s.population(), s.beds(), s.plots().size(), s.plotCounts(),
			s.treasury(), s.land().prosperity(), s.land().deserted().size());
	}

	private static String composition(SimSettlement s) {
		Map<String, Integer> n = new TreeMap<>();
		for (SimPlot p : s.plots()) {
			if (p.state() == PlotState.BUILT || p.state() == PlotState.BUILDING) {
				n.merge(p.spec().archetype().getPath() + (p.state() == PlotState.BUILDING ? "*" : ""), 1, Integer::sum);
			}
		}
		return n.toString();
	}

	private static BlockPos centre(SimPlot p) {
		var o = p.spec().origin();
		return new BlockPos(o.getX() + p.spec().sizeX() / 2, p.spec().floorY(), o.getZ() + p.spec().sizeZ() / 2);
	}

	private static int groundAt(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int surface(TestSingleplayerContext world, BlockPos at) {
		return world.getServer().computeOnServer(server -> groundAt(server.overworld(), at.getX(), at.getZ()));
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	/** How a camera spot is chosen: as given, raised into the open air, or searched for round the subject. */
	private enum View {
		/** Exactly where asked (already checked). */
		AS_GIVEN,
		/** Raised (two blocks at a time) until the eye is in the open and nothing stands between it and the subject. */
		OPEN,
		/** Searched for round the subject, 3-8 blocks off, a clear line of sight (folk at work). */
		CLOSE,
		/** As {@link #CLOSE}, 7-14 blocks off (a square from the edge of it). */
		MID,
		/** Searched for round the subject indoors, 2-6 blocks off, under a roof, a clear line of sight (rooms). */
		INDOOR
	}

	/** Stands at {@code v[0..2]} (the feet; the eye 1.62 above) looking at {@code v[3..5]} and takes a picture. */
	private static void shootAt(ClientGameTestContext context, TestSingleplayerContext world, String name, double[] v) {
		shootAt(context, world, name, v, View.OPEN);
	}

	private static void shootAt(ClientGameTestContext context, TestSingleplayerContext world, String name, double[] v, boolean open) {
		shootAt(context, world, name, v, open ? View.OPEN : View.AS_GIVEN);
	}

	private static void shootAt(ClientGameTestContext context, TestSingleplayerContext world, String name, double[] given, View mode) {
		double[] v = mode == View.AS_GIVEN ? given : world.getServer().computeOnServer(server -> place(server.overworld(), given, mode));
		if (v == null) {
			log("shot " + name + ": no clear view of " + (int) given[3] + "," + (int) given[4] + "," + (int) given[5]);
			return;
		}
		double eye = 1.62;
		double dx = v[3] - v[0];
		double dy = v[4] - (v[1] + eye);
		double dz = v[5] - v[2];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		teleport(world, v[0], v[1], v[2], yaw, pitch);
		settle(context, 5_000);
		context.takeScreenshot("hearth_live_" + name);
		log("shot " + name + " from " + (int) v[0] + "," + (int) v[1] + "," + (int) v[2] + " toward " + (int) v[3] + "," + (int) v[4] + "," + (int) v[5]);
	}

	private static double @Nullable [] place(ServerLevel level, double[] v, View mode) {
		double eye = 1.62;
		if (mode == View.OPEN) {
			// the bearing asked for first, then the others round the subject at the same distance, a little higher each round
			double dx0 = v[0] - v[3];
			double dz0 = v[2] - v[5];
			for (int k = 0; k <= 40; k++) {
				double y = v[1] + 2 * k;
				for (int a = 0; a < (k < 12 ? 8 : 1); a++) {
					double ang = a * Math.PI / 4;
					double x = v[3] + dx0 * Math.cos(ang) - dz0 * Math.sin(ang);
					double z = v[5] + dx0 * Math.sin(ang) + dz0 * Math.cos(ang);
					if (roomy(level, x, y, z) && roomy(level, x, y + eye, z) && sight(level, x, y + eye, z, v[3], v[4], v[5])) {
						return new double[]{x, y, z, v[3], v[4], v[5]};
					}
				}
			}
			return v;
		}
		boolean indoor = mode == View.INDOOR;
		int min = indoor ? 2 : mode == View.MID ? 7 : 3;
		int max = indoor ? 6 : mode == View.MID ? 14 : 8;
		double[] heights = indoor ? new double[]{-1.0, -0.6} : new double[]{0.5, 1.5, 3, 5};
		int[] order = indoor ? new int[]{4, 5, 3, 6, 2} : java.util.stream.IntStream.rangeClosed(min, max).toArray();
		for (int d : order) {
			for (double h : heights) {
				for (int a = 0; a < 16; a++) {
					double ang = a * Math.PI / 8;
					// at a block's centre, with room round the eye (the camera's near plane cuts into a wall it stands against)
					double x = Math.floor(v[3] + Math.cos(ang) * d) + 0.5;
					double z = Math.floor(v[5] + Math.sin(ang) * d) + 0.5;
					double feet = Math.floor(v[4] + h);
					BlockPos at = BlockPos.containing(x, feet + eye, z);
					if (!roomy(level, x, feet, z) || !roomy(level, x, feet + eye, z) || indoor == level.canSeeSky(at)) {
						continue;
					}
					if (sight(level, x, feet + eye, z, v[3], v[4], v[5])) {
						return new double[]{x, feet, z, v[3], v[4], v[5]};
					}
				}
			}
		}
		return null;
	}

	/** {@link #passable} here and a third of a block round it. */
	private static boolean roomy(ServerLevel level, double x, double y, double z) {
		return passable(level, x, y, z) && passable(level, x + 0.35, y, z) && passable(level, x - 0.35, y, z) && passable(level, x, y, z + 0.35)
			&& passable(level, x, y, z - 0.35) && passable(level, x, y + 0.3, z);
	}

	/** Air, or something the eye sees through and stands in (not leaves, not water). */
	private static boolean passable(ServerLevel level, double x, double y, double z) {
		BlockPos b = BlockPos.containing(x, y, z);
		var state = level.getBlockState(b);
		return state.isAir() || state.getCollisionShape(level, b).isEmpty() && !state.is(net.minecraft.tags.BlockTags.LEAVES) && state.getFluidState().isEmpty();
	}

	/** Nothing solid between an eye and a subject (the last block before the subject is not looked at). */
	private static boolean sight(ServerLevel level, double ex, double ey, double ez, double tx, double ty, double tz) {
		double dx = tx - ex;
		double dy = ty - ey;
		double dz = tz - ez;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		int n = (int) (len * 4);
		for (int i = 1; i < n - 2; i++) {
			double f = i / (double) n;
			if (!passable(level, ex + dx * f, ey + dy * f, ez + dz * f)) {
				return false;
			}
		}
		return true;
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String line) {
		System.out.println("[hearth live] " + line);
	}
}
