package dev.voidmc.hearth.gametest;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotState;
import dev.voidmc.hearth.npc.NpcConfig;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.sim.SimServer;
import dev.voidmc.hearth.sim.settlement.Cursor;
import dev.voidmc.hearth.sim.settlement.PlotFacts;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.sim.settlement.SimPlot;
import dev.voidmc.hearth.sim.settlement.SimSettlement;
import dev.voidmc.hearth.sim.terrain.CultureVote;
import dev.voidmc.hearth.sim.world.SimWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Builders at work, photographed: a temperate town on gentle ground near spawn, grown by the real sim until a house (or
 * something larger) is going up; then the player arrives, its builders come out and raise it block by block, and the
 * camera stands outside the site at eye height, aimed at a builder:
 *
 * <ol>
 *   <li>{@code site_wide}: the site with its builders and the partly built frame;</li>
 *   <li>{@code builder_close_N}: a builder placing a block, taken as the arm swings;</li>
 *   <li>{@code site_later}: the same camera a few minutes on;</li>
 *   <li>{@code builder_walk}: a builder fetching or walking to the work (or on the scaffolding);</li>
 *   <li>{@code digger}: a quarrier, miner or clay digger at an excavation if the town has one open.</li>
 * </ol>
 *
 * {@code -Dhearth.tour=build}; screenshots {@code build/run/clientGameTest/screenshots/*hearth_build_*.png}; every line
 * is printed as {@code [hearth build]}. {@code -Dhearth.build.later=N} seconds between the first and the later site
 * shots (default 200); {@code -Dhearth.build.step=N} builder ticks per step (default 6).
 */
public class BuilderTour implements FabricClientGameTest {
	private static final int LATER = Integer.getInteger("hearth.build.later", 360);
	private static final int STEP_TICKS = Integer.getInteger("hearth.build.step", 6);
	private static final int CREW = Integer.getInteger("hearth.build.crew", 3);
	/** The kind of building wanted for the site (an archetype path prefix; "" for any). */
	private static final String KIND = System.getProperty("hearth.build.kind", "house/");
	private static final double EYE = 1.62;
	private static final java.nio.file.Path RAW = java.nio.file.Path.of(System.getProperty("hearth.build.out", "screenshots"));

	private record Site(long town, int plot) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Tours.wanted("build")) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(10);
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
			world.getServer().runCommand("time set 1500");
			// out of reach while the town is founded and grown
			world.getServer().runCommand("gamemode spectator @a");
			world.getServer().runCommand("tp @a 30000 300 30000");
			settle(context, 3_000);
			Site site = world.getServer().computeOnServer(BuilderTour::foundTown);
			if (site == null) {
				log("no site for a temperate town near spawn");
				return;
			}
			log("site: " + world.getServer().computeOnServer(server -> plotLine(server, site)));
			world.getServer().runOnServer(server -> {
				NpcConfig c = NpcModule.config();
				var a = c.ai();
				NpcModule.setConfig(c.withAi(new NpcConfig.Ai(a.thinkNear(), a.thinkMid(), a.thinkFar(), a.nearDistance(), a.midDistance(), a.repathCooldown(),
					STEP_TICKS, a.scaffolding())));
				// every builder to this site
				SimSettlement s = settlement(server, site.town);
				var town = NpcModule.embodiment().town(s);
				// three builders to this site (more only jostle for the scaffolding on a house)
				int n = 0;
				for (Resident r : s.residents()) {
					if (r.job == JobType.BUILDER && n++ < CREW) {
						town.builderClaims.put(r.id, site.plot);
					}
				}
			});
			// the town written into the world before anyone arrives
			world.getServer().runOnServer(server -> forceChunks(server, site, true));
			waitApplier(context, world);
			world.getServer().runOnServer(server -> forceChunks(server, site, false));

			BlockPos c = world.getServer().computeOnServer(server -> centre(settlement(server, site.town).plot(site.plot)));
			world.getServer().runCommand("gamemode creative @a");
			// mid-morning, held there: builders keep at it through the whole tour (construction itself does not need the clock)
			world.getServer().runCommand("time set 4000");
			world.getServer().runCommand("gamerule advance_time false");
			teleport(world, c.getX() + 0.5, surface(world, c) + 14, c.getZ() - 20.5, 0, 30);
			settle(context, 25_000);
			log("embodied: " + world.getServer().computeOnServer(server -> builders(server, site)));
			world.getServer().runOnServer(server -> {
				// the crew again, from those now out in the world (the nearest to the site)
				SimSettlement s = settlement(server, site.town);
				var town = NpcModule.embodiment().town(s);
				BlockPos at = centre(s.plot(site.plot));
				List<Folk> crew = new ArrayList<>();
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() != null && f.brain().settlement.id() == site.town && f.brain().resident.job == JobType.BUILDER) {
						crew.add(f);
					}
				}
				crew.sort(java.util.Comparator.comparingDouble(f -> f.blockPosition().distSqr(at)));
				for (int i = 0; i < Math.min(CREW, crew.size()); i++) {
					town.builderClaims.put(crew.get(i).brain().resident.id, site.plot);
				}
			});
			// the wide shot as soon as a builder is at the site, while most of it is still to raise
			for (int i = 0; i < 12 && world.getServer().computeOnServer(server -> aimAt(server, site, settlement(server, site.town).plot(site.plot).reach().inflatedBy(3))) == null; i++) {
				settle(context, 3_000);
			}
			double[] wide = world.getServer().computeOnServer(server -> wideView(server, site));
			if (wide != null) {
				shoot(context, world, "site_wide", wide);
				log("site_wide: " + world.getServer().computeOnServer(server -> plotLine(server, site)));
			}
			waitForWork(context, world, site, 40);
			close(context, world, site);
			walking(context, world, site);

			// the same camera, later; until the building is up and its scaffolding down (or the time is spent)
			long start = System.currentTimeMillis();
			long until = start + LATER * 1000L;
			long[] cost0 = world.getServer().computeOnServer(server -> new long[]{Folk.SERVER_TICK_NANOS.sum(), Folk.SERVER_TICKS.sum(), server.getTickCount()});
			double done0 = world.getServer().computeOnServer(server -> done(settlement(server, site.town), settlement(server, site.town).plot(site.plot)));
			long builtAt = -1;
			java.util.Set<String> shots = new java.util.HashSet<>();
			while (System.currentTimeMillis() < until && (builtAt < 0 || System.currentTimeMillis() - builtAt < 60_000)) {
				world.getServer().runOnServer(server -> patron(settlement(server, site.town)));
				for (int k = 0; k < 20; k++) {
					settle(context, 1_000);
					world.getServer().runOnServer(server -> tally(server, site));
					if (builtAt < 0 && world.getServer().computeOnServer(server -> settlement(server, site.town).plot(site.plot).state() != PlotState.BUILDING)) {
						builtAt = System.currentTimeMillis();
						log(String.format(Locale.ROOT, "built: %s after %d s of the later wait (from %.0f%% done)", world.getServer().computeOnServer(server -> plotLine(server, site)),
							(builtAt - start) / 1000, done0 * 100));
					}
					sceneShots(context, world, site, shots);
				}
				log("progress: " + world.getServer().computeOnServer(server -> plotLine(server, site) + "; scaffolding " + scaffolds(server, site) + "; " + builders(server, site)));
			}
			long[] cost1 = world.getServer().computeOnServer(server -> new long[]{Folk.SERVER_TICK_NANOS.sum(), Folk.SERVER_TICKS.sum(), server.getTickCount()});
			long folkTicks = cost1[1] - cost0[1];
			long serverTicks = Math.max(1, cost1[2] - cost0[2]);
			log(String.format(Locale.ROOT, "folk cost over the later wait: %.1f µs per folk tick, %.3f ms per server tick (%d folk ticks, %d server ticks)",
				folkTicks == 0 ? 0 : (cost1[0] - cost0[0]) / 1e3 / folkTicks, (cost1[0] - cost0[0]) / 1e6 / serverTicks, folkTicks, serverTicks));
			if (builtAt < 0) {
				log(String.format(Locale.ROOT, "not built after %d s: %s (from %.0f%% done)", (System.currentTimeMillis() - start) / 1000,
					world.getServer().computeOnServer(server -> plotLine(server, site)), done0 * 100));
			}
			int total = TALLY.entrySet().stream().filter(e -> !e.getKey().equals("elsewhere")).mapToInt(java.util.Map.Entry::getValue).sum();
			StringBuilder pct = new StringBuilder();
			TALLY.forEach((k, v) -> {
				if (!k.equals("elsewhere")) {
					pct.append(String.format(Locale.ROOT, " %s %.0f%%;", k, 100.0 * v / Math.max(1, total)));
				}
			});
			log("time use (builder-seconds on the site, sampled each second): " + TALLY + " =" + pct);
			if (wide != null) {
				double[] again = world.getServer().computeOnServer(server -> camOk(server.overworld(), wide[0], wide[1], wide[2], 0) ? wide : wideView(server, site));
				shoot(context, world, "site_later", again != null ? again : wide);
				log("site_later: " + world.getServer().computeOnServer(server -> plotLine(server, site)));
				close(context, world, site, "builder_close_later");
			}
			// diggers: at this town's excavation if it has one open, else at a mining town grown for them
			if (!digger(context, world, site.town)) {
				Long dig = world.getServer().computeOnServer(BuilderTour::foundDigTown);
				if (dig != null) {
					world.getServer().runOnServer(server -> forceDig(server, dig, true));
					waitApplier(context, world);
					world.getServer().runOnServer(server -> forceDig(server, dig, false));
					digger(context, world, dig);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the town

	/** A temperate town on the gentlest temperate ground near spawn, grown until a house or larger is going up. */
	private static @Nullable Site foundTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		if (w == null) {
			return null;
		}
		BlockPos spawn = level.getRespawnData().pos();
		BlockPos best = null;
		int bestRelief = Integer.MAX_VALUE;
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
		SimSettlement s = w.found(best, Culture.TEMPERATE, null);
		log(String.format(Locale.ROOT, "founded %s at %s (relief %d)", s.name(), s.center().toShortString(), bestRelief));
		int days = 0;
		for (; days < 12; days += 4) {
			patron(s);
			w.simulate(s, 4);
		}
		SimPlot pick = null;
		for (; days < 200 && pick == null; days++) {
			patron(s);
			w.simulate(s, 1);
			// a house first (the scaffolding round a home is what the tour is for), anything from day 160
			pick = candidate(s, days < 120 ? 70 : 40, days < 160 ? KIND : "");
		}
		log(String.format(Locale.ROOT, "grew %d days: %d souls, %d builders, %s, plots %s", days, s.population(),
			s.residents().stream().filter(r -> r.job == JobType.BUILDER).count(), s.tier().getSerializedName(), s.plotCounts()));
		if (pick == null) {
			return null;
		}
		patron(s);
		return new Site(s.id(), pick.id());
	}

	/** A building (house or larger) under construction with most of it still to do and its goods in hand. */
	private static @Nullable SimPlot candidate(SimSettlement s, int minArea, String kind) {
		SimPlot best = null;
		double bestScore = 0;
		for (SimPlot p : s.plots()) {
			String path = p.spec().archetype().getPath();
			if (p.state() != PlotState.BUILDING || p.waitingForGoods() || !path.startsWith(kind) || path.startsWith("infra/") || path.startsWith("farm/") || path.startsWith("extract/")
				|| path.startsWith("landscape/") || p.spec().sizeX() * p.spec().sizeZ() < minArea) {
				continue;
			}
			double done = done(s, p);
			if (done > 0.45) {
				continue;
			}
			// room round it for the camera: few other buildings close by
			BoundingBox near = p.reach().inflatedBy(12);
			int crowd = 0;
			for (SimPlot o : s.plots()) {
				if (o != p && !o.spec().archetype().getPath().startsWith("infra/") && !o.spec().archetype().getPath().startsWith("farm/") && o.reach().intersects(near)) {
					crowd++;
				}
			}
			double score = (path.startsWith("house/") ? 1.5 : 1) * (1 - done * 0.5) * p.spec().sizeX() * p.spec().sizeZ() / ((1.0 + crowd) * (1.0 + crowd));
			if (score > bestScore) {
				best = p;
				bestScore = score;
			}
		}
		return best;
	}

	private static double done(SimSettlement s, SimPlot p) {
		PlotFacts f = s.facts(p);
		int total = 0;
		int done = p.step();
		for (int st = 0; st < f.stageCount(); st++) {
			total += f.stepCount()[st];
			if (st < p.stage()) {
				done += f.stepCount()[st];
			}
		}
		return total == 0 ? 1 : done / (double) total;
	}

	private static void patron(SimSettlement s) {
		for (Goods g : Goods.VALUES) {
			int want = s.stockpile().capacity(g) * 3 / 4 - s.stockpile().get(g);
			if (want > 0) {
				s.stockpile().add(g, want);
			}
		}
		if (s.treasury() < 3000) {
			s.deposit(3000 - s.treasury());
		}
	}

	private static String plotLine(MinecraftServer server, Site site) {
		SimSettlement s = settlement(server, site.town);
		SimPlot p = s.plot(site.plot);
		if (p == null) {
			return "plot gone";
		}
		return String.format(Locale.ROOT, "%s #%d %s %s at %s, %dx%d, cursor %s, %.0f%% done%s", s.name(), p.id(), p.spec().archetype().getPath(),
			p.state().getSerializedName(), centre(p).toShortString(), p.spec().sizeX(), p.spec().sizeZ(), Cursor.toString(p.cursor()), done(s, p) * 100,
			p.waitingForGoods() ? ", waiting for " + p.stalledOn() : "");
	}

	/** What the site's builders were doing, one sample per builder each second of the later wait. */
	private static final java.util.Map<String, Integer> TALLY = new java.util.TreeMap<>();

	private static void tally(MinecraftServer server, Site site) {
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() == null || f.brain().settlement.id() != site.town || f.brain().resident.job != JobType.BUILDER) {
				continue;
			}
			String d = f.brain().describe();
			String k;
			if (!d.contains("(plot " + site.plot + ")")) {
				k = "elsewhere";
			} else if (d.contains("placing ") || d.contains("clearing")) {
				var lvl = server.overworld();
				var at = f.blockPosition();
				boolean onScaffold = lvl.getBlockState(at).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)
					|| lvl.getBlockState(at.below()).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
				k = onScaffold ? "placing from the scaffold" : "placing";
			} else if (d.contains("working from the scaffold")) {
				k = "on the scaffold (between blocks)";
			} else if (d.contains("putting up scaffolding") || d.contains("carrying scaffolding")) {
				k = "raising scaffolding";
			} else if (d.contains("taking down scaffolding") || d.contains("climbing down")) {
				k = "taking scaffolding down";
			} else if (d.contains("along the scaffold") || d.contains("climbing the scaffold") || d.contains("onto the scaffold")) {
				k = "on the scaffold (moving)";
			} else if (d.contains("fetching") || d.contains("waiting for materials")) {
				k = "fetching";
			} else if (d.contains("carrying materials")) {
				k = "carrying for the crew";
			} else if (d.contains("walking")) {
				k = "walking to the work";
			} else {
				k = "other";
			}
			TALLY.merge(k, 1, Integer::sum);
		}
	}

	/** Scaffold blocks standing round the site (the ledger's, within four blocks of the plot). */
	private static int scaffolds(MinecraftServer server, Site site) {
		SimSettlement s = settlement(server, site.town);
		BoundingBox near = s.plot(site.plot).reach().inflatedBy(4);
		int[] n = new int[1];
		s.data(dev.voidmc.hearth.npc.work.ScaffoldLedger.KEY).forEach(l -> {
			if (near.isInside(BlockPos.of(l))) {
				n[0]++;
			}
		});
		return n[0];
	}

	/** Builders on the site placing a block just now, and how many of them from the scaffolding. */
	private static int[] placing(MinecraftServer server, Site site) {
		int n = 0;
		int high = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() == null || f.brain().settlement.id() != site.town || f.brain().resident.job != JobType.BUILDER) {
				continue;
			}
			String d = f.brain().describe();
			if (d.contains("(plot " + site.plot + ")") && (d.contains("placing ") || d.contains("working from the scaffold"))) {
				n++;
				var lvl = server.overworld();
				if (lvl.getBlockState(f.blockPosition()).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)
					|| lvl.getBlockState(f.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)) {
					high++;
				}
			}
		}
		return new int[]{n, high};
	}

	/**
	 * The scenes of a building site, each photographed once as it comes: the scaffolding up with two or more builders placing
	 * from it (wide, then one of them close), the house nearly finished, the scaffolding coming down, and the house built.
	 */
	private void sceneShots(ClientGameTestContext context, TestSingleplayerContext world, Site site, java.util.Set<String> shots) {
		if (!shots.contains("scaffold_up")) {
			boolean ready = world.getServer().computeOnServer(server -> {
				int[] p = placing(server, site);
				return scaffolds(server, site) >= 12 && p[0] >= 2 && p[1] >= 1;
			});
			if (ready) {
				shots.add("scaffold_up");
				double[] v = world.getServer().computeOnServer(server -> wideView(server, site));
				if (v != null) {
					shoot(context, world, "scaffold_up", v);
					log("scaffold_up: " + world.getServer().computeOnServer(server -> plotLine(server, site) + "; scaffolding " + scaffolds(server, site) + "; " + builders(server, site)));
				}
				close(context, world, site, "builder_scaffold");
			}
		}
		double done = world.getServer().computeOnServer(server -> {
			SimSettlement s = settlement(server, site.town);
			return s.plot(site.plot).state() == PlotState.BUILDING ? done(s, s.plot(site.plot)) : 1.0;
		});
		if (!shots.contains("finishing") && done >= 0.9 && done < 1.0) {
			shots.add("finishing");
			double[] v = world.getServer().computeOnServer(server -> wideView(server, site));
			if (v != null) {
				shoot(context, world, "finishing", v);
				log("finishing: " + world.getServer().computeOnServer(server -> plotLine(server, site) + "; scaffolding " + scaffolds(server, site) + "; " + builders(server, site)));
			}
		}
		if (!shots.contains("scaffold_down")) {
			boolean down = world.getServer().computeOnServer(server -> {
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() != null && f.brain().settlement.id() == site.town && f.brain().describe().contains("taking down scaffolding (plot " + site.plot + ")")) {
						return scaffolds(server, site) >= 6;
					}
				}
				return false;
			});
			if (down) {
				shots.add("scaffold_down");
				double[] v = world.getServer().computeOnServer(server -> wideView(server, site));
				if (v != null) {
					shoot(context, world, "scaffold_down", v);
					log("scaffold_down: " + world.getServer().computeOnServer(server -> plotLine(server, site) + "; scaffolding " + scaffolds(server, site) + "; " + builders(server, site)));
				}
			}
		}
		if (!shots.contains("built") && done >= 1.0 && world.getServer().computeOnServer(server -> scaffolds(server, site) == 0)) {
			shots.add("built");
			double[] v = world.getServer().computeOnServer(server -> wideView(server, site));
			if (v != null) {
				shoot(context, world, "built", v);
				log("built shot: " + world.getServer().computeOnServer(server -> plotLine(server, site)));
			}
		}
	}

	private static String builders(MinecraftServer server, Site site) {
		StringBuilder b = new StringBuilder();
		int n = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == site.town && f.brain().resident.job == JobType.BUILDER) {
				n++;
				b.append(" | ").append(f.folkName()).append(": ").append(f.brain().describe()).append(" at ").append(f.blockPosition().toShortString());
			}
		}
		return n + " builders embodied" + b;
	}

	private static void waitForWork(ClientGameTestContext context, TestSingleplayerContext world, Site site, int seconds) {
		for (int i = 0; i < seconds / 5; i++) {
			if (world.getServer().computeOnServer(server -> working(server, site, true) != null)) {
				return;
			}
			world.getServer().runOnServer(server -> patron(settlement(server, site.town)));
			settle(context, 5_000);
		}
		log("no builder placing on the site after " + seconds + " s: " + world.getServer().computeOnServer(server -> builders(server, site)));
	}

	/** A builder on the site: placing (or clearing) if {@code placing}, else any at work on it. */
	private static @Nullable Folk working(MinecraftServer server, Site site, boolean placing) {
		Folk any = null;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() == null || f.brain().settlement.id() != site.town || f.brain().resident.job != JobType.BUILDER) {
				continue;
			}
			String doing = f.brain().describe();
			if (!doing.contains("(plot " + site.plot + ")")) {
				continue;
			}
			if (doing.contains("placing ") || doing.contains("working from the scaffold")) {
				return f;
			}
			if (any == null) {
				any = f;
			}
		}
		return placing ? null : any;
	}

	/** Where to aim for the site as a whole: the builders' mean position. */
	private static double @Nullable [] aimAt(MinecraftServer server, Site site, BoundingBox within) {
		double x = 0;
		double y = 0;
		double z = 0;
		int n = 0;
		for (Folk f : NpcModule.embodiment().entities()) {
			if (f.brain() != null && f.brain().settlement.id() == site.town && f.brain().describe().contains("(plot " + site.plot + ")") && within.isInside(f.blockPosition())) {
				x += f.getX();
				y += f.getY();
				z += f.getZ();
				n++;
			}
		}
		return n == 0 ? null : new double[]{x / n, y / n, z / n};
	}

	// ---------------------------------------------------------------- the shots

	/** From a front corner, 14-22 blocks off, a little above eye height, at the frame and its builders. */
	private static double @Nullable [] wideView(MinecraftServer server, Site site) {
		ServerLevel level = server.overworld();
		SimSettlement s = settlement(server, site.town);
		SimPlot p = s.plot(site.plot);
		BlockPos c = centre(p);
		double[] aim = aimAt(server, site, p.reach().inflatedBy(6));
		double tx = c.getX() + 0.5;
		double ty = p.spec().floorY() + 2.5;
		double tz = c.getZ() + 0.5;
		if (aim != null) {
			tx = (tx * 2 + aim[0]) / 3;
			tz = (tz * 2 + aim[2]) / 3;
		}
		BoundingBox reach = p.reach().inflatedBy(6);
		double cx = c.getX() + 0.5;
		double cz = c.getZ() + 0.5;
		var f = p.spec().facing();
		double base = Math.atan2(f.getStepZ(), f.getStepX());
		double size = Math.max(p.spec().sizeX(), p.spec().sizeZ());
		double[] best = null;
		double bestScore = Double.MAX_VALUE;
		for (double d = Math.max(12, size * 1.1); d <= Math.max(24, size * 2); d += 2) {
			for (int a = 0; a < 24; a++) {
				double ang = base + a * Math.PI / 12;
				double x = Math.floor(cx + Math.cos(ang) * d) + 0.5;
				double z = Math.floor(cz + Math.sin(ang) * d) + 0.5;
				if (reach.isInside(new BlockPos((int) Math.floor(x), reach.minY() + 1, (int) Math.floor(z))) || onPlot(s, x, z)) {
					continue;
				}
				int g = groundAt(level, (int) Math.floor(x), (int) Math.floor(z));
				for (int up = 1; up <= 4; up++) {
					double feet = g + up;
					// nothing but the building itself in the way of its middle
					if (!camOk(level, x, feet, z) || !sightTo(level, x, feet + EYE, z, tx, ty, tz, p.reach().inflatedBy(1))) {
						continue;
					}
					boolean seesBuilder = aim != null && sight(level, x, feet + EYE, z, aim[0], aim[1] + 1.2, aim[2]);
					// a front corner first (45 degrees off the facing), then lower, then nearer
					double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang - base)) );
					double score = (seesBuilder || aim == null ? 0 : 12) + Math.abs(off - 40) * 0.5 + Math.abs(up - 2) * 3 + Math.abs(d - Math.max(15, size * 1.4)) + Math.abs(feet - ty) * 0.5;
					if (score < bestScore) {
						bestScore = score;
						best = new double[]{x, feet, z, tx, ty, tz};
					}
					break;
				}
			}
		}
		return best;
	}

	/** 4-7 blocks from the builder, outside the frame, at eye height or a little above, a clear view. */
	private static double @Nullable [] closeView(ServerLevel level, Folk folk, SimPlot p, double dmin, double dmax) {
		double tx = folk.getX();
		double ty = folk.getY() + 1.1;
		double tz = folk.getZ();
		BoundingBox reach = p.reach();
		BlockPos c = centre(p);
		double[] best = null;
		double bestScore = Double.MAX_VALUE;
		for (double d = dmin; d <= dmax; d += 0.5) {
			for (int a = 0; a < 32; a++) {
				double ang = a * Math.PI / 16;
				double x = Math.floor(tx + Math.cos(ang) * d) + 0.5;
				double z = Math.floor(tz + Math.sin(ang) * d) + 0.5;
				boolean inside = reach.isInside(BlockPos.containing(x, reach.minY() + 1, z)) || onPlot(folk.brain().settlement, x, z);
				int g = groundAt(level, (int) Math.floor(x), (int) Math.floor(z));
				int top = Math.max(3, (int) Math.ceil(folk.getY() - g));
				for (int up = 0; up <= top; up++) {
					double feet = g + up;
					// at least level with the builder's head when they are up high
					if (feet + EYE < folk.getY() - 1 || !camOk(level, x, feet, z, 1)) {
						continue;
					}
					if (!sight(level, x, feet + EYE, z, tx, ty, tz) || !sight(level, x, feet + EYE, z, tx, folk.getY() + 0.3, tz)) {
						continue;
					}
					// the builder with the work behind or beside: the camera not on the far side of the frame
					double toB = Math.atan2(tz - z, tx - x);
					double toC = Math.atan2(c.getZ() + 0.5 - tz, c.getX() + 0.5 - tx);
					double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(toB - toC)));
					double score = (inside ? 8 : 0) + Math.abs(up - Math.max(1, folk.getY() - g - 1)) * 2.5 + Math.abs(d - 5.5) + off * 0.04;
					if (score < bestScore) {
						bestScore = score;
						best = new double[]{x, feet, z, tx, ty, tz};
					}
					break;
				}
			}
		}
		return best;
	}

	private void close(ClientGameTestContext context, TestSingleplayerContext world, Site site) {
		close(context, world, site, "builder_close");
	}

	/** A builder placing a block, photographed on the swing (three of them, from where each one is). */
	private void close(ClientGameTestContext context, TestSingleplayerContext world, Site site, String name) {
		int taken = 0;
		int misses = 0;
		int lost = 0;
		for (int tries = 0; tries < 40 && taken < 3; tries++) {
			world.getServer().runOnServer(server -> patron(settlement(server, site.town)));
			Object[] found = world.getServer().computeOnServer(server -> {
				Folk f = working(server, site, true);
				if (f == null) {
					return null;
				}
				double[] v = closeView(server.overworld(), f, settlement(server, site.town).plot(site.plot), 4, 7);
				return v == null ? null : new Object[]{f.getId(), f.getUUID(), v, f.folkName() + " " + f.brain().describe() + " at " + f.blockPosition().toShortString()};
			});
			if (found == null) {
				misses++;
				settle(context, 2_000);
				continue;
			}
			int id = (int) found[0];
			UUID uuid = (UUID) found[1];
			double[] v = (double[]) found[2];
			place(world, v);
			settle(context, 1_500);
			// re-aimed at the builder each tick until the arm swings (or a few seconds pass)
			boolean swung = false;
			for (int t = 0; t < 120 && !swung; t++) {
				double[] at = world.getServer().computeOnServer(server -> {
					Entity e = server.overworld().getEntity(uuid);
					return e == null ? null : new double[]{e.getX(), e.getY() + 1.1, e.getZ()};
				});
				if (at == null || Math.hypot(at[0] - v[0], at[2] - v[2]) > 9 || !world.getServer().computeOnServer(server -> sight(server.overworld(), v[0], v[1] + EYE,
					v[2], at[0], at[1], at[2]) && sight(server.overworld(), v[0], v[1] + EYE, v[2], at[0], at[1] - 0.8, at[2]))) {
					break;
				}
				v[3] = at[0];
				v[4] = at[1];
				v[5] = at[2];
				aim(world, v);
				context.waitTick();
				swung = context.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof LivingEntity le && le.isSwinging() && le.getSwingAnimation(1.0F) >= 0.1F
					&& le.getSwingAnimation(1.0F) <= 0.55F);
			}
			if (!swung) {
				lost++;
				continue;
			}
			snap(context, name + "_" + taken);
			log("shot " + name + "_" + taken + ": " + found[3] + " from " + fmt(v));
			taken++;
			settle(context, 2_000);
		}
		if (taken == 0) {
			log(name + ": no builder caught placing (" + misses + " times nobody placing with a view, " + lost + " times the swing missed or the view lost); " + world.getServer().computeOnServer(server -> builders(server, site)));
		}
	}

	/** A builder fetching, walking or on scaffolding (not placing). */
	private void walking(ClientGameTestContext context, TestSingleplayerContext world, Site site) {
		for (int tries = 0; tries < 20; tries++) {
			Object[] found = world.getServer().computeOnServer(server -> {
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() == null || f.brain().settlement.id() != site.town || f.brain().resident.job != JobType.BUILDER) {
						continue;
					}
					String doing = f.brain().describe();
					if (doing.contains("fetching") || doing.contains("walking to") || doing.contains("carrying") || doing.contains("putting up")) {
						double[] v = closeView(server.overworld(), f, settlement(server, site.town).plot(site.plot), 5, 8);
						if (v != null) {
							return new Object[]{f.getUUID(), v, f.folkName() + " " + doing + " at " + f.blockPosition().toShortString()};
						}
					}
				}
				return null;
			});
			if (found == null) {
				settle(context, 2_000);
				continue;
			}
			UUID uuid = (UUID) found[0];
			double[] v = (double[]) found[1];
			place(world, v);
			for (int t = 0; t < 10; t++) {
				double[] at = world.getServer().computeOnServer(server -> {
					Entity e = server.overworld().getEntity(uuid);
					return e == null ? null : new double[]{e.getX(), e.getY() + 1.1, e.getZ()};
				});
				if (at == null) {
					break;
				}
				v[3] = at[0];
				v[4] = at[1];
				v[5] = at[2];
				aim(world, v);
				context.waitTick();
			}
			snap(context, "builder_walk");
			log("shot builder_walk: " + found[2] + " from " + fmt(v));
			return;
		}
		log("builder_walk: nobody fetching or walking");
	}

	/** A quarrier, miner or clay digger at an excavation, if any is open. */
	private boolean digger(ClientGameTestContext context, TestSingleplayerContext world, long townId) {
		Site site = new Site(townId, -1);
		BlockPos pit = world.getServer().computeOnServer(server -> {
			for (SimPlot p : settlement(server, site.town).plots()) {
				if (p.spec().archetype().getPath().startsWith("extract/") && p.state() == PlotState.BUILDING) {
					log("digger: " + p.spec().archetype().getPath() + " #" + p.id() + " at " + centre(p).toShortString() + " cursor " + Cursor.toString(p.cursor()));
					return centre(p);
				}
			}
			return null;
		});
		if (pit == null) {
			log("digger: no excavation open in town #" + townId);
			return false;
		}
		teleport(world, pit.getX() + 0.5, surface(world, pit) + 10, pit.getZ() - 12.5, 0, 30);
		for (int tries = 0; tries < 12; tries++) {
			settle(context, 5_000);
			Object[] found = world.getServer().computeOnServer(server -> {
				for (Folk f : NpcModule.embodiment().entities()) {
					if (f.brain() == null || f.brain().settlement.id() != site.town) {
						continue;
					}
					JobType j = f.brain().resident.job;
					if ((j == JobType.QUARRIER || j == JobType.MINER || j == JobType.CLAY_DIGGER) && f.blockPosition().distSqr(pit) < 30 * 30) {
						SimPlot dig = null;
						for (SimPlot p : settlement(server, site.town).plots()) {
							if (p.spec().archetype().getPath().startsWith("extract/") && p.reach().isInside(f.blockPosition())) {
								dig = p;
							}
						}
						if (dig == null) {
							continue;
						}
						double[] v = closeView(server.overworld(), f, dig, 4, 8);
						if (v != null) {
							return new Object[]{f.getId(), v, f.folkName() + " (" + j.getSerializedName() + ") " + f.brain().describe() + " at " + f.blockPosition().toShortString()};
						}
					}
				}
				return null;
			});
			if (found != null) {
				int id = (int) found[0];
				double[] v = (double[]) found[1];
				place(world, v);
				settle(context, 1_500);
				for (int t = 0; t < 100; t++) {
					double[] at = world.getServer().computeOnServer(server -> {
						Entity e = server.overworld().getEntity(id);
						return e == null ? null : new double[]{e.getX(), e.getY() + 1.1, e.getZ()};
					});
					if (at == null) {
						break;
					}
					v[3] = at[0];
					v[4] = at[1];
					v[5] = at[2];
					aim(world, v);
					context.waitTick();
					if (context.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof LivingEntity le && le.isSwinging() && le.getSwingAnimation(1.0F) <= 0.55F)) {
						break;
					}
				}
				snap(context, "digger");
				log("shot digger: " + found[2] + " from " + fmt(v));
				return true;
			}
		}
		log("digger: nobody digging there; " + world.getServer().computeOnServer(server -> {
			StringBuilder b = new StringBuilder();
			for (Folk f : NpcModule.embodiment().entities()) {
				if (f.brain() != null && f.brain().settlement.id() == site.town && f.brain().resident.job != JobType.BUILDER) {
					b.append(f.brain().resident.job.getSerializedName()).append(": ").append(f.brain().describe()).append(" at ").append(f.blockPosition().toShortString()).append(" | ");
				}
			}
			return b.toString();
		}));
		return true;
	}

	/** A town far off, grown (with a patron) until it has a quarry pit, mine or clay pit open. */
	private static @Nullable Long foundDigTown(MinecraftServer server) {
		ServerLevel level = server.overworld();
		SimWorld w = SimServer.world(level);
		BlockPos spawn = level.getRespawnData().pos();
		int tried = 0;
		for (int r = 1800; r <= 4000 && tried < 3; r += 400) {
			for (int k = 0; k < 8 && tried < 3; k++) {
				double a = k * Math.PI / 4;
				BlockPos at = new BlockPos(spawn.getX() + (int) (Math.cos(a) * r), 0, spawn.getZ() + (int) (Math.sin(a) * r));
				if (w.terrain().groundY(at.getX(), at.getZ()) <= w.terrain().seaLevel() + 2 || w.whyNotFound(at, SimWorld.FOUND_SPACING, true) != null) {
					continue;
				}
				tried++;
				SimSettlement s = w.found(at, null, null);
				// day by day: a working may open and be dug out between two looks ten days apart
				java.util.Set<Integer> workings = new java.util.HashSet<>();
				for (int d = 0; d < 240; d++) {
					patron(s);
					w.simulate(s, 1);
					for (SimPlot p : s.plots()) {
						if (p.spec().archetype().getPath().startsWith("extract/") && p.state() == PlotState.BUILDING) {
							log("dig town " + s.name() + " (" + s.culture().getSerializedName() + ") at " + s.center().toShortString() + " after " + (d + 1) + " days: "
								+ p.spec().archetype().getPath() + " #" + p.id() + " cursor " + Cursor.toString(p.cursor()));
							return s.id();
						}
						if (p.spec().archetype().getPath().startsWith("extract/")) {
							workings.add(p.id());
						}
					}
				}
				log("dig town " + s.name() + ": no excavation open on any day of 240 (" + workings.size() + " workings planned, none open on a day looked at), " + s.plotCounts());
			}
		}
		return null;
	}

	private static void forceDig(MinecraftServer server, long town, boolean force) {
		for (SimPlot p : settlement(server, town).plots()) {
			if (p.spec().archetype().getPath().startsWith("extract/") && p.state() == PlotState.BUILDING) {
				BlockPos c = centre(p);
				for (int cx = (c.getX() >> 4) - 2; cx <= (c.getX() >> 4) + 2; cx++) {
					for (int cz = (c.getZ() >> 4) - 2; cz <= (c.getZ() >> 4) + 2; cz++) {
						server.overworld().setChunkForced(cx, cz, force);
					}
				}
				return;
			}
		}
	}

	// ---------------------------------------------------------------- camera

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, String name, double[] v) {
		place(world, v);
		settle(context, 4_000);
		snap(context, name);
		log("shot " + name + " from " + fmt(v));
	}

	/** On ground any building of the town stands on or will (roads and fields aside): no camera there. */
	private static boolean onPlot(dev.voidmc.hearth.api.Settlement s, double x, double z) {
		for (var p : s.plots()) {
			String path = p.spec().archetype().getPath();
			if (path.startsWith("infra/") || path.startsWith("farm/") || path.startsWith("landscape/")) {
				continue;
			}
			BoundingBox r = SimPlot.reach(p.spec()).inflatedBy(1);
			if (x >= r.minX() && x < r.maxX() + 1 && z >= r.minZ() && z < r.maxZ() + 1) {
				return true;
			}
		}
		return false;
	}

	private static void snap(ClientGameTestContext context, String name) {
		context.takeScreenshot(net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions.of("hearth_build_" + name).withSize(1600, 900)
			.withDestinationDir(RAW));
	}

	private static void place(TestSingleplayerContext world, double[] v) {
		double dx = v[3] - v[0];
		double dy = v[4] - (v[1] + EYE);
		double dz = v[5] - v[2];
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		teleport(world, v[0], v[1], v[2], yaw, pitch);
	}

	private static void aim(TestSingleplayerContext world, double[] v) {
		place(world, v);
	}

	private static boolean camOk(ServerLevel level, double x, double feet, double z) {
		return camOk(level, x, feet, z, 2);
	}

	private static boolean camOk(ServerLevel level, double x, double feet, double z, int clear) {
		if (!roomy(level, x, feet, z) || !roomy(level, x, feet + EYE, z) || !level.canSeeSky(BlockPos.containing(x, feet + EYE, z))) {
			return false;
		}
		// in the open: nothing built within two blocks of the camera (no wall or roof edge filling the foreground)
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int ground = groundAt(level, bx, bz);
		for (int dx = -clear; dx <= clear; dx++) {
			for (int dz = -clear; dz <= clear; dz++) {
				for (int y = ground; y <= (int) Math.floor(feet + EYE) + 1; y++) {
					if (!passable(level, bx + dx + 0.5, y + 0.5, bz + dz + 0.5)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean roomy(ServerLevel level, double x, double y, double z) {
		return passable(level, x, y, z) && passable(level, x + 0.4, y, z) && passable(level, x - 0.4, y, z) && passable(level, x, y, z + 0.4)
			&& passable(level, x, y, z - 0.4) && passable(level, x, y + 0.3, z);
	}

	private static boolean passable(ServerLevel level, double x, double y, double z) {
		BlockPos b = BlockPos.containing(x, y, z);
		var state = level.getBlockState(b);
		return state.isAir() || state.getCollisionShape(level, b).isEmpty() && !state.is(BlockTags.LEAVES) && state.getFluidState().isEmpty();
	}

	/** Clear from the eye until the ray first meets something inside {@code box} (or the subject). */
	private static boolean sightTo(ServerLevel level, double ex, double ey, double ez, double tx, double ty, double tz, BoundingBox box) {
		double dx = tx - ex;
		double dy = ty - ey;
		double dz = tz - ez;
		int n = (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 4);
		for (int i = 1; i < n; i++) {
			double f = i / (double) n;
			double x = ex + dx * f;
			double y = ey + dy * f;
			double z = ez + dz * f;
			if (!passable(level, x, y, z)) {
				return box.isInside(BlockPos.containing(x, y, z));
			}
		}
		return true;
	}

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

	// ---------------------------------------------------------------- helpers

	private static SimSettlement settlement(MinecraftServer server, long id) {
		return SimServer.world(server.overworld()).settlement(id);
	}

	private static BlockPos centre(SimPlot p) {
		var o = p.spec().origin();
		return new BlockPos(o.getX() + p.spec().sizeX() / 2, p.spec().floorY(), o.getZ() + p.spec().sizeZ() / 2);
	}

	private static void forceChunks(MinecraftServer server, Site site, boolean force) {
		ServerLevel level = server.overworld();
		BoundingBox b = settlement(server, site.town).bounds();
		BlockPos c = centre(settlement(server, site.town).plot(site.plot));
		int r = 5;
		for (int cx = (c.getX() >> 4) - r; cx <= (c.getX() >> 4) + r; cx++) {
			for (int cz = (c.getZ() >> 4) - r; cz <= (c.getZ() >> 4) + r; cz++) {
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
		log("applier: " + world.getServer().computeOnServer(server -> SimServer.world(server.overworld()).applier().blocksPlaced.sum()) + " blocks written");
	}

	private static int groundAt(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static int surface(TestSingleplayerContext world, BlockPos at) {
		return world.getServer().computeOnServer(server -> groundAt(server.overworld(), at.getX(), at.getZ()));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void teleport(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer p = player(server);
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
			p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), Mth.wrapDegrees(yaw), pitch, true);
		});
	}

	private static String fmt(double[] v) {
		return String.format(Locale.ROOT, "%.1f,%.1f,%.1f toward %.1f,%.1f,%.1f", v[0], v[1], v[2], v[3], v[4], v[5]);
	}

	private static void settle(ClientGameTestContext context, long ms) {
		long until = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < until) {
			context.waitTick();
		}
	}

	private static void log(String line) {
		System.out.println("[hearth build] " + line);
	}
}
