package dev.voidmc.hearth.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A town left alone grows on the real blueprints (requests/w5-tour.md §4): a temperate town on good land reaches a
 * village (20 souls) in about 60 days and a town (60) in about 200, with the economy module's books running as live.
 * {@link #printGrowth} prints the day-by-day table (HEARTH_GROWTH_LIVE=flat,hills,river; HEARTH_FETCH=0.2,0.4 compares
 * shares of idle builder time fetched; run with {@code --rerun}, the environment is not an input of the test task).
 */
class RealGrowthTest {
	/** Prints the growth table (run with HEARTH_GROWTH_LIVE=flat,hills,... and HEARTH_GROWTH_DAYS in the environment). */
	@Test
	void printGrowth() {
		String terrains = System.getenv("HEARTH_GROWTH_LIVE");
		Assumptions.assumeTrue(terrains != null);
		int days = Integer.parseInt(System.getenv().getOrDefault("HEARTH_GROWTH_DAYS", "240"));
		boolean economy = System.getenv("HEARTH_GROWTH_NOECONOMY") == null;
		for (String run : runs(terrains, System.getenv().getOrDefault("HEARTH_FETCH", "-"))) {
			String terrain = run.substring(0, run.indexOf(' '));
			String fetch = run.substring(run.indexOf(' ') + 1);
			GrowthHarness.fetchShare = fetch.equals("-") ? null : Double.valueOf(fetch);
			GrowthHarness h = GrowthHarness.found(terrain, 3, economy);
			System.out.println("== " + terrain + (economy ? " (economy books)" : " (clock coin)") + ", fetch " + fetch + ", " + h.town.land().sites().size()
				+ " sites");
			for (int d = 1; d <= days; d++) {
				h.day();
				if (d % 10 == 0) {
					System.out.println(h.line());
					if (d % 50 == 0) {
						System.out.println("   stock " + h.stock());
					}
				}
			}
		}
	}

	/** Every terrain with every fetch share ({@code -}: the config's), as "terrain share". */
	private static java.util.List<String> runs(String terrains, String fetches) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (String fetch : fetches.split(",")) {
			for (String terrain : terrains.split(",")) {
				out.add(terrain + " " + fetch);
			}
		}
		return out;
	}

	/**
	 * Prints what each temperate archetype costs at a middling size on flat ground (work, goods, their worth at the
	 * sim's prices) and which trades its job sites are for against the trades its archetype lists (run with
	 * HEARTH_CATALOGUE=1).
	 */
	@Test
	void printCatalogue() {
		Assumptions.assumeTrue(System.getenv("HEARTH_CATALOGUE") != null);
		dev.voidmc.hearth.sim.TestSupport.useRealCatalogue();
		var blueprints = dev.voidmc.hearth.api.HearthServices.blueprints();
		var terrain = dev.voidmc.hearth.api.testing.SyntheticTerrain.named("flat", 3);
		var rules = dev.voidmc.hearth.sim.model.SimRules.defaults();
		java.util.List<dev.voidmc.hearth.api.ArchetypeInfo> all = new java.util.ArrayList<>(blueprints.archetypes());
		all.sort(java.util.Comparator.comparing(i -> i.id().toString()));
		for (var info : all) {
			if (!info.suits(dev.voidmc.hearth.api.Culture.TEMPERATE)) {
				continue;
			}
			int x = (info.minFrontage() + info.maxFrontage()) / 2;
			int z = (info.minDepth() + info.maxDepth()) / 2;
			var spec = new dev.voidmc.hearth.api.PlotSpec(info.id(), dev.voidmc.hearth.Hearth.id("temperate"), 7, new net.minecraft.core.BlockPos(0,
				terrain.groundY(0, 0), 0), Math.max(3, x), Math.max(3, z), net.minecraft.core.Direction.NORTH, Math.max(8, info.maxHeight()), 1,
				new net.minecraft.nbt.CompoundTag());
			try {
				PlotFacts f = PlotFacts.of(spec, blueprints, terrain);
				int work = 0;
				int[] goods = new int[dev.voidmc.hearth.api.Goods.VALUES.length];
				for (int st = 0; st < f.stageCount(); st++) {
					work += f.work()[st];
					for (int g = 0; g < goods.length; g++) {
						goods[g] += f.goods()[st][g];
					}
				}
				double value = 0;
				StringBuilder bill = new StringBuilder();
				for (int g = 0; g < goods.length; g++) {
					value += goods[g] * rules.prices[g];
					if (goods[g] > 0) {
						bill.append(dev.voidmc.hearth.api.Goods.VALUES[g].getSerializedName()).append('=').append(goods[g]).append(' ');
					}
				}
				StringBuilder jobs = new StringBuilder();
				for (var j : dev.voidmc.hearth.api.JobType.values()) {
					if (f.jobs()[j.ordinal()] > 0) {
						jobs.append(j.getSerializedName()).append(info.jobs().contains(j) ? "" : "(unlisted)").append('=').append(f.jobs()[j.ordinal()]).append(' ');
					}
				}
				System.out.printf(java.util.Locale.ROOT, "%-28s %-8s %2dx%-2d stages %2d work %6d worth %7.0f beds %2d jobs [%s] goods %s%n", info.id().getPath(),
					info.minTier().getSerializedName(), spec.sizeX(), spec.sizeZ(), f.stageCount(), work, value, f.beds(), jobs.toString().trim(), bill);
			} catch (RuntimeException e) {
				System.out.println(info.id().getPath() + ": " + e);
			}
		}
	}

	/**
	 * The growth targets on good land (requests/w5-tour.md §4), with room either side: other modules' changes to the
	 * blueprints and the planner move the days, but a town that never grows, or one that is a city by midsummer, is
	 * wrong. Seed 3, flat temperate ground, economy books, as {@link #printGrowth}.
	 */
	@Test
	void aTownLeftAloneGrowsOnTheRealCatalogue() {
		GrowthHarness h = GrowthHarness.found("flat", 3, true);
		int village = -1;
		int town = -1;
		StringBuilder log = new StringBuilder();
		for (int d = 1; d <= 280 && town < 0; d++) {
			h.day();
			if (village < 0 && h.town.population() >= 20) {
				village = d;
			}
			if (town < 0 && h.town.population() >= 60) {
				town = d;
			}
			if (d % 20 == 0) {
				log.append(h.line()).append('\n');
			}
		}
		System.out.print(log);
		System.out.println("village (20) on day " + village + ", town (60) on day " + town);
		assertTrue(village >= 25 && village <= 100, "a village in about 60 days, was day " + village + "\n" + log);
		assertTrue(town >= 100 && town <= 280, "a town in about 200 days, was day " + town + "\n" + log);
	}
}
