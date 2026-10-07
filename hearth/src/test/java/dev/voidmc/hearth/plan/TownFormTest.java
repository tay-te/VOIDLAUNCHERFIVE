package dev.voidmc.hearth.plan;

import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.ArchetypeInfo.SiteFlag;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.testing.SyntheticTerrain;
import dev.voidmc.hearth.build.BuildModule;
import dev.voidmc.hearth.plan.catalogue.Archetypes;
import dev.voidmc.hearth.plan.layout.LayoutChooser;
import dev.voidmc.hearth.plan.model.LayoutType;
import dev.voidmc.hearth.plan.model.PlanState;
import dev.voidmc.hearth.plan.testing.CatalogueDouble;
import dev.voidmc.hearth.plan.testing.MiniSim;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

/**
 * A bench, not a test: with {@code HEARTH_TOWN_FORM=1} it grows towns as {@code townPreview} does (MiniSim on the real
 * archetypes, synthetic terrain at the preview's temperature) and prints what makes a town read as a town (ART_REVIEW_2
 * §3, items 1-4, 19): how many of the core's attachable homes share a wall with a neighbour (street rows), the town homes
 * standing alone, the mean side gap between the core's homes, how far the church, the town tower and the other
 * landmarks stand from the heart and whether the summit is crowned, industry by the landmarks, the farm belt (farm plots
 * per hundred folk, and how many stand in the ring just past the houses), lamps, and what {@code next} cost.
 * {@code HEARTH_TOWN_FORM_DAYS} (default 250), {@code _CASES} (comma list of {@code culture:terrain:seed[:layout]}).
 */
class TownFormTest {
	private static final String CASES =
		"temperate:hills:1,northern:hills:1,alpine:hills:1,moorland:hills:1,mediterranean:hills:1,bayou:hills:1,temperate:hills:1:walled,temperate:hills:6";

	@Test
	void form() {
		if (System.getenv("HEARTH_TOWN_FORM") == null) {
			return;
		}
		bench(Integer.parseInt(System.getenv().getOrDefault("HEARTH_TOWN_FORM_DAYS", "250")), System.getenv().getOrDefault("HEARTH_TOWN_FORM_CASES", CASES));
	}

	/** The same from the command line (on the test classes and the game's runtime classpath): {@code [days] [cases]}. */
	public static void main(String[] args) throws Exception {
		if (args.length > 0 && args[0].equals("planbench")) {
			// PlanBenchTest's cities, without the profile (its fingerprints and timings), from the command line
			SharedConstants.tryDetectVersion();
			Bootstrap.bootStrap();
			HearthServices.setBlueprints(BuildModule.standalone());
			var run = PlanBenchTest.class.getDeclaredMethod("run", Culture.class, String.class, long.class, LayoutType.class, int.class, boolean.class,
				boolean.class);
			run.setAccessible(true);
			String cases = args.length > 1 ? args[1] : "temperate:hills:1:-,temperate:hills:5:walled,mediterranean:slope:3:-,temperate:coast:2:-,temperate:river:4:-";
			for (String c : cases.split(",")) {
				String[] a = c.split(":");
				run.invoke(null, Culture.valueOf(a[0].toUpperCase(Locale.ROOT)), a[1], Long.parseLong(a[2]),
					a[3].equals("-") ? null : LayoutType.valueOf(a[3].toUpperCase(Locale.ROOT)), 420, false, false);
			}
			return;
		}
		bench(args.length > 0 ? Integer.parseInt(args[0]) : 250, args.length > 1 ? args[1] : CASES);
	}

	static void bench(int days, String cases) {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		HearthServices.setBlueprints(BuildModule.standalone());
		try {
			for (String c : cases.split(",")) {
				String[] a = c.split(":");
				run(Culture.valueOf(a[0].toUpperCase(Locale.ROOT)), a[1], Long.parseLong(a[2]),
					a.length > 3 ? LayoutType.valueOf(a[3].toUpperCase(Locale.ROOT)) : null, days);
			}
		} finally {
			HearthServices.setBlueprints(new CatalogueDouble());
		}
	}

	private static float temperature(Culture c) {
		return switch (c) {
			case TUNDRA -> 0.0F;
			case NORTHERN, ALPINE -> 0.3F;
			case DESERT, MESA, STEPPE -> 2.0F;
			case TROPICAL, BAYOU, KARST -> 0.95F;
			default -> 0.8F;
		};
	}

	private static void run(Culture culture, String terrain, long seed, LayoutType layout, int days) {
		if (layout != null) {
			LayoutChooser.FORCED.set(layout);
		}
		MiniSim sim;
		try {
			SyntheticTerrain t = new SyntheticTerrain(SyntheticTerrain.Kind.valueOf(terrain.toUpperCase(Locale.ROOT)), seed, temperature(culture));
			sim = new MiniSim(culture, t, seed, new HearthPlanner(PlanModule.STATE, PlanConfig.DEFAULTS), new BlockPos(0, t.groundY(0, 0), 0));
			sim.run(days);
		} finally {
			LayoutChooser.FORCED.remove();
		}
		System.out.println(report(culture + " " + terrain + " s" + seed, sim));
	}

	/** The town's form, one paragraph. */
	static String report(String name, MiniSim sim) {
		PlanState st = sim.settlement.data(PlanModule.STATE);
		int pop = sim.settlement.population();
		double core = Math.max(18, st.layout.coreScale * Math.sqrt(Math.max(6, pop)));
		int hx = st.heartX;
		int hz = st.heartZ;
		List<PlotView> plots = new ArrayList<>(sim.settlement.plots());
		List<int[]> solid = new ArrayList<>();
		for (PlotView p : plots) {
			PlotSpec s = p.spec();
			if (Archetypes.isLinear(s.archetype()) || s.archetype().equals(Archetypes.PLAZA) || s.archetype().equals(Archetypes.TERRACE)) {
				continue;
			}
			solid.add(rect(s));
		}
		int attach = 0;
		int joined = 0;
		int tall = 0;
		int alone = 0;
		double gaps = 0;
		int gapN = 0;
		int homesFar = 0;
		int homes = 0;
		double homeExtent = 0;
		List<Double> homeDist = new ArrayList<>();
		int farms = 0;
		int lamps = 0;
		int junctions = 0;
		List<String> marks = new ArrayList<>();
		List<int[]> markAt = new ArrayList<>();
		for (PlotView p : plots) {
			PlotSpec s = p.spec();
			ArchetypeInfo info = HearthServices.blueprints().archetype(s.archetype());
			if (info == null) {
				continue;
			}
			double d = Math.hypot(s.origin().getX() + s.sizeX() / 2.0 - hx, s.origin().getZ() + s.sizeZ() / 2.0 - hz);
			if (info.category() == ArchetypeCategory.HOUSING) {
				homes++;
				homeDist.add(d);
				boolean attachable = info.flags().contains(SiteFlag.ATTACHABLE);
				int[] g = sideGaps(s, solid);
				if (attachable && d < core) {
					attach++;
					if (g[0] == 0 || g[1] == 0) {
						joined++;
					}
				}
				if (d < core) {
					gaps += Math.min(Math.min(g[0], g[1]), 8);
					gapN++;
				} else {
					homesFar++;
				}
				if (info.flags().contains(SiteFlag.VERTICAL)) {
					tall++;
					if (g[0] > 2 && g[1] > 2) {
						alone++;
					}
				}
			} else if (info.category() == ArchetypeCategory.FARM) {
				farms++;
			}
			if (s.archetype().equals(Archetypes.LAMP_POST)) {
				lamps++;
			}
			if (s.archetype().equals(Archetypes.PLAZA) && s.params().getBooleanOr("junction", false)) {
				junctions++;
			}
			boolean mark = info.category() == ArchetypeCategory.LANDMARK || s.archetype().equals(Archetypes.CHAPEL)
				|| s.archetype().equals(Archetypes.TOWN_HALL) || s.archetype().equals(Archetypes.CASTLE) || s.archetype().equals(Archetypes.KEEP);
			if (mark) {
				int y = s.floorY() - st.heartY;
				marks.add(String.format(Locale.ROOT, "%s %d/%+d", s.archetype().getPath().substring(s.archetype().getPath().indexOf('/') + 1), (int) d, y));
				markAt.add(new int[]{s.origin().getX() + s.sizeX() / 2, s.origin().getZ() + s.sizeZ() / 2, info.category() == ArchetypeCategory.LANDMARK
					|| s.archetype().equals(Archetypes.CHAPEL) ? 1 : 0});
			}
		}
		// the edge of the houses: the 90th percentile of the homes' distances
		homeDist.sort(Double::compare);
		homeExtent = homeDist.isEmpty() ? 0 : homeDist.get(homeDist.size() * 9 / 10);
		int belt = 0;
		int industryByMarks = 0;
		for (PlotView p : plots) {
			PlotSpec s = p.spec();
			ArchetypeInfo info = HearthServices.blueprints().archetype(s.archetype());
			if (info == null) {
				continue;
			}
			int cx = s.origin().getX() + s.sizeX() / 2;
			int cz = s.origin().getZ() + s.sizeZ() / 2;
			double d = Math.hypot(cx - hx, cz - hz);
			if (info.category() == ArchetypeCategory.FARM && d >= homeExtent * 0.8 && d <= homeExtent + 110) {
				belt++;
			}
			if (info.category() == ArchetypeCategory.WORKPLACE && info.flags().contains(SiteFlag.OUTSKIRTS)) {
				for (int[] m : markAt) {
					if (m[2] == 1 && Math.hypot(cx - m[0], cz - m[1]) < 28) {
						industryByMarks++;
						break;
					}
				}
			}
		}
		String summit = "-";
		if (st.site != null && st.site.summitY() > st.heartY + 8) {
			double best = Double.MAX_VALUE;
			for (int[] m : markAt) {
				best = Math.min(best, Math.hypot(m[0] - st.site.summitX(), m[1] - st.site.summitZ()));
			}
			summit = String.format(Locale.ROOT, "summit +%d at %d, nearest mark %d", st.site.summitY() - st.heartY,
				(int) Math.hypot(st.site.summitX() - hx, st.site.summitZ() - hz), (int) best);
		}
		long[] t = sim.callNanos.toLongArray();
		Arrays.sort(t);
		String time = t.length == 0 ? "no calls" : String.format(Locale.ROOT, "next median %.2f p90 %.2f max %.1f ms", t[t.length / 2] / 1e6,
			t[t.length * 9 / 10] / 1e6, t[t.length - 1] / 1e6);
		return String.format(Locale.ROOT,
			"%s %s: %d folk, %d plots, core r %d, square %d%n"
				+ "  rows: %d/%d core attachable homes share a wall (%.0f %%); tall homes alone %d/%d; core mean side gap %.2f; homes %d (%d beyond the core), 90%% within %d%n"
				+ "  marks: %s; %s; industry by marks %d%n"
				+ "  farms %d (%.1f per 100 folk), %d in the belt past the houses; lamps %d; junction squares %d%n  %s",
			name, st.layout.getSerializedName(), pop, plots.size(), (int) core, st.plazaHalf * 2 + 1,
			joined, attach, attach == 0 ? 0.0 : 100.0 * joined / attach, alone, tall, gapN == 0 ? 0.0 : gaps / gapN, homes, homesFar, (int) homeExtent,
			String.join(", ", marks), summit, industryByMarks,
			farms, 100.0 * farms / Math.max(1, pop), belt, lamps, junctions, time);
	}

	private static int[] rect(PlotSpec s) {
		return new int[]{s.origin().getX(), s.origin().getZ(), s.origin().getX() + s.sizeX() - 1, s.origin().getZ() + s.sizeZ() - 1};
	}

	/** Open columns from the plot's left and right sides to the nearest other solid plot overlapping it along its depth (9 = none within 8). */
	private static int[] sideGaps(PlotSpec s, List<int[]> solid) {
		int[] r = rect(s);
		boolean alongX = s.facing().getAxis() == Direction.Axis.Z;
		int lo = 9;
		int hi = 9;
		for (int[] o : solid) {
			if (o[0] == r[0] && o[1] == r[1] && o[2] == r[2] && o[3] == r[3]) {
				continue;
			}
			if (alongX) {
				if (o[3] < r[1] || o[1] > r[3]) {
					continue;
				}
				if (o[2] < r[0]) {
					lo = Math.min(lo, r[0] - o[2] - 1);
				} else if (o[0] > r[2]) {
					hi = Math.min(hi, o[0] - r[2] - 1);
				}
			} else {
				if (o[2] < r[0] || o[0] > r[2]) {
					continue;
				}
				if (o[3] < r[1]) {
					lo = Math.min(lo, r[1] - o[3] - 1);
				} else if (o[1] > r[3]) {
					hi = Math.min(hi, o[1] - r[3] - 1);
				}
			}
		}
		return new int[]{lo, hi};
	}
}
