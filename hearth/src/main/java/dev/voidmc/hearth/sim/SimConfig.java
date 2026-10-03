package dev.voidmc.hearth.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.api.Goods;
import dev.voidmc.hearth.api.JobType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sim module's knobs, {@code config/hearth/sim.json}. Every field is optional so an old file keeps working; the
 * numbers are documented in docs/SIM.md.
 */
public record SimConfig(List<String> dimensions, boolean probeCorrection, Embody embody, Clock clock, Applier applier, Atlas atlas,
	Labour labour, Production production, Population population, Storage storage, Founding founding, Daughters daughters) {

	public static final SimConfig DEFAULTS = new SimConfig(List.of("minecraft:overworld"), false, Embody.DEFAULTS, Clock.DEFAULTS,
		Applier.DEFAULTS, Atlas.DEFAULTS, Labour.DEFAULTS, Production.DEFAULTS, Population.DEFAULTS, Storage.DEFAULTS, Founding.DEFAULTS,
		Daughters.DEFAULTS);

	public static final Codec<SimConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.listOf().optionalFieldOf("dimensions", DEFAULTS.dimensions).forGetter(SimConfig::dimensions),
		// on Expanse worlds, correct the terrain model with the generator's real ground on an 8-block grid
		Codec.BOOL.optionalFieldOf("probe_correction", DEFAULTS.probeCorrection).forGetter(SimConfig::probeCorrection),
		Embody.CODEC.optionalFieldOf("embody", Embody.DEFAULTS).forGetter(SimConfig::embody),
		Clock.CODEC.optionalFieldOf("clock", Clock.DEFAULTS).forGetter(SimConfig::clock),
		Applier.CODEC.optionalFieldOf("applier", Applier.DEFAULTS).forGetter(SimConfig::applier),
		Atlas.CODEC.optionalFieldOf("atlas", Atlas.DEFAULTS).forGetter(SimConfig::atlas),
		Labour.CODEC.optionalFieldOf("labour", Labour.DEFAULTS).forGetter(SimConfig::labour),
		Production.CODEC.optionalFieldOf("production", Production.DEFAULTS).forGetter(SimConfig::production),
		Population.CODEC.optionalFieldOf("population", Population.DEFAULTS).forGetter(SimConfig::population),
		Storage.CODEC.optionalFieldOf("storage", Storage.DEFAULTS).forGetter(SimConfig::storage),
		Founding.CODEC.optionalFieldOf("founding", Founding.DEFAULTS).forGetter(SimConfig::founding),
		Daughters.CODEC.optionalFieldOf("daughters", Daughters.DEFAULTS).forGetter(SimConfig::daughters)
	).apply(i, SimConfig::new));

	/**
	 * Settlers leaving a crowded town found a daughter hamlet (ARCHITECTURE.md §5.1).
	 *
	 * @param enabled       whether live settlements found hamlets at all (histories never do)
	 * @param minPopulation a town smaller than this does not send settlers out
	 * @param crowdedDays   days in a row with no free bed before a group sets out
	 * @param cooldownDays  days between two hamlets of the same town
	 * @param minDistance   the hamlet's site is at least this far from the town's heart (blocks)
	 * @param maxDistance   and at most this far
	 * @param settlers      adults who leave to found it
	 * @param maxPerTown    hamlets one town founds at most
	 */
	public record Daughters(boolean enabled, int minPopulation, int crowdedDays, int cooldownDays, int minDistance, int maxDistance, int settlers,
		int maxPerTown) {
		public static final Daughters DEFAULTS = new Daughters(true, 30, 5, 40, 600, 2500, 4, 3);
		public static final Codec<Daughters> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Daughters::enabled),
			Codec.intRange(4, 10000).optionalFieldOf("min_population", DEFAULTS.minPopulation).forGetter(Daughters::minPopulation),
			Codec.intRange(1, 1000).optionalFieldOf("crowded_days", DEFAULTS.crowdedDays).forGetter(Daughters::crowdedDays),
			Codec.intRange(0, 10000).optionalFieldOf("cooldown_days", DEFAULTS.cooldownDays).forGetter(Daughters::cooldownDays),
			Codec.intRange(200, 20000).optionalFieldOf("min_distance", DEFAULTS.minDistance).forGetter(Daughters::minDistance),
			Codec.intRange(200, 20000).optionalFieldOf("max_distance", DEFAULTS.maxDistance).forGetter(Daughters::maxDistance),
			Codec.intRange(2, 32).optionalFieldOf("settlers", DEFAULTS.settlers).forGetter(Daughters::settlers),
			Codec.intRange(0, 64).optionalFieldOf("max_per_town", DEFAULTS.maxPerTown).forGetter(Daughters::maxPerTown)
		).apply(i, Daughters::new));
	}

	/**
	 * @param radius       a settlement is embodied while a player is within this many blocks of its bounds
	 * @param hysteresis   and stays embodied until they are this much further away
	 * @param bannerMargin how far outside a settlement's bounds the arrival banner already counts you as in it
	 */
	public record Embody(int radius, int hysteresis, int bannerMargin) {
		public static final Embody DEFAULTS = new Embody(128, 32, 8);
		public static final Codec<Embody> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(16, 1024).optionalFieldOf("radius", DEFAULTS.radius).forGetter(Embody::radius),
			Codec.intRange(0, 256).optionalFieldOf("hysteresis", DEFAULTS.hysteresis).forGetter(Embody::hysteresis),
			Codec.intRange(0, 64).optionalFieldOf("banner_margin", DEFAULTS.bannerMargin).forGetter(Embody::bannerMargin)
		).apply(i, Embody::new));
	}

	/**
	 * @param stepInterval         every live settlement is stepped at least this often (ticks)
	 * @param embodiedStepInterval embodied settlements are stepped this often, so construction appears step by step
	 * @param maxCatchUpDays       at most this much sim time per step; longer absences take several steps
	 * @param budgetMs             time per server tick for all settlement steps together
	 * @param minBudgetMs          the budget never shrinks below this, however slow the server is
	 * @param highMspt             above this average tick time the budget shrinks toward the minimum
	 * @param plannerCallsPerTick  how many {@code planner.next} calls the clock may make per tick (they cost milliseconds)
	 */
	public record Clock(int stepInterval, int embodiedStepInterval, int maxCatchUpDays, double budgetMs, double minBudgetMs,
		double highMspt, int plannerCallsPerTick) {
		public static final Clock DEFAULTS = new Clock(1200, 20, 30, 2.0, 0.25, 40.0, 1);
		public static final Codec<Clock> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(20, 72000).optionalFieldOf("step_interval", DEFAULTS.stepInterval).forGetter(Clock::stepInterval),
			Codec.intRange(1, 1200).optionalFieldOf("embodied_step_interval", DEFAULTS.embodiedStepInterval).forGetter(Clock::embodiedStepInterval),
			Codec.intRange(1, 400).optionalFieldOf("max_catch_up_days", DEFAULTS.maxCatchUpDays).forGetter(Clock::maxCatchUpDays),
			Codec.doubleRange(0.05, 50).optionalFieldOf("budget_ms", DEFAULTS.budgetMs).forGetter(Clock::budgetMs),
			Codec.doubleRange(0.01, 50).optionalFieldOf("min_budget_ms", DEFAULTS.minBudgetMs).forGetter(Clock::minBudgetMs),
			Codec.doubleRange(1, 1000).optionalFieldOf("high_mspt", DEFAULTS.highMspt).forGetter(Clock::highMspt),
			Codec.intRange(0, 64).optionalFieldOf("planner_calls_per_tick", DEFAULTS.plannerCallsPerTick).forGetter(Clock::plannerCallsPerTick)
		).apply(i, Clock::new));
	}

	/**
	 * @param budgetMs  time per tick for writing recorded construction into loaded chunks
	 * @param maxBlocks blocks per tick at most
	 */
	public record Applier(double budgetMs, int maxBlocks) {
		public static final Applier DEFAULTS = new Applier(3.0, 6000);
		public static final Codec<Applier> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.doubleRange(0.05, 50).optionalFieldOf("budget_ms", DEFAULTS.budgetMs).forGetter(Applier::budgetMs),
			Codec.intRange(16, 1_000_000).optionalFieldOf("max_blocks", DEFAULTS.maxBlocks).forGetter(Applier::maxBlocks)
		).apply(i, Applier::new));
	}

	/**
	 * @param enabled            historic settlements from the seed (pre-history, written by world generation)
	 * @param density            the share of 1024-block regions that hold a settlement
	 * @param siteMargin         a site keeps this far from its region's edges, so centres are always at least twice this
	 *                           apart and a town never needs its neighbour region's plan
	 * @param maxAgeDays         the oldest a settlement can be
	 * @param ageSkew            ages are {@code maxAge * u^skew}: most are young, a few old
	 * @param villageClearance   chunks to keep from any structure set holding a {@code #minecraft:village} structure
	 * @param structureClearance more structure sets to avoid, with their clearance in chunks (Expanse landmarks)
	 * @param guardMargin        trees and boulders keep this many blocks off planned plots and roads
	 * @param adoptMargin        a historic settlement is adopted when a chunk within this of its bounds loads
	 * @param planCache          region plans kept in memory (each is recomputed on demand, deterministically)
	 */
	public record Atlas(boolean enabled, double density, int siteMargin, int maxAgeDays, double ageSkew, int villageClearance,
		Map<String, Integer> structureClearance, int guardMargin, int adoptMargin, int planCache) {
		public static final Atlas DEFAULTS = new Atlas(true, 0.4, 300, 400, 2.2, 20, defaultClearance(), 3, 96, 256);
		public static final Codec<Atlas> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Atlas::enabled),
			Codec.doubleRange(0, 1).optionalFieldOf("density", DEFAULTS.density).forGetter(Atlas::density),
			Codec.intRange(64, 480).optionalFieldOf("site_margin", DEFAULTS.siteMargin).forGetter(Atlas::siteMargin),
			Codec.intRange(0, 4000).optionalFieldOf("max_age_days", DEFAULTS.maxAgeDays).forGetter(Atlas::maxAgeDays),
			Codec.doubleRange(0.1, 10).optionalFieldOf("age_skew", DEFAULTS.ageSkew).forGetter(Atlas::ageSkew),
			Codec.intRange(0, 64).optionalFieldOf("village_clearance", DEFAULTS.villageClearance).forGetter(Atlas::villageClearance),
			Codec.unboundedMap(Codec.STRING, Codec.intRange(0, 64)).optionalFieldOf("structure_clearance", DEFAULTS.structureClearance)
				.forGetter(Atlas::structureClearance),
			Codec.intRange(0, 16).optionalFieldOf("guard_margin", DEFAULTS.guardMargin).forGetter(Atlas::guardMargin),
			Codec.intRange(0, 512).optionalFieldOf("adopt_margin", DEFAULTS.adoptMargin).forGetter(Atlas::adoptMargin),
			Codec.intRange(8, 65536).optionalFieldOf("plan_cache", DEFAULTS.planCache).forGetter(Atlas::planCache)
		).apply(i, Atlas::new));

		private static Map<String, Integer> defaultClearance() {
			Map<String, Integer> m = new LinkedHashMap<>();
			// Expanse landmarks: 75-145 blocks across
			for (String s : List.of("caravanserai", "cloud_monastery", "frost_outpost", "karst_pagoda", "logging_camp", "palm_harbour",
				"sun_shrine", "wisteria_manor")) {
				m.put("expanse:" + s, 12);
			}
			// Expanse's mid-sized buildings
			for (String s : List.of("stone_circle", "ruined_watchtower", "woodland_lodge", "stilt_hamlet", "lighthouse", "lumen_shrine",
				"nomad_camp")) {
				m.put("expanse:" + s, 7);
			}
			m.put("minecraft:pillager_outposts", 10);
			return m;
		}
	}

	/**
	 * @param workPerBuilderHour one unit is one builder placing one block; an embodied builder manages about 33 an hour
	 * @param builderHoursPerDay how many hours of a sim day builders work
	 * @param baseBuilders       builders a settlement always has room for, yard or not
	 * @param buildersPerTen     and more per ten residents
	 * @param builderShare       each active plot wants this many builders (so builders spread over several plots)
	 */
	public record Labour(double workPerBuilderHour, double builderHoursPerDay, int baseBuilders, int buildersPerTen, int builderShare) {
		public static final Labour DEFAULTS = new Labour(33, 16, 2, 1, 2);
		public static final Codec<Labour> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.doubleRange(0, 10000).optionalFieldOf("work_per_builder_hour", DEFAULTS.workPerBuilderHour).forGetter(Labour::workPerBuilderHour),
			Codec.doubleRange(0, 24).optionalFieldOf("builder_hours_per_day", DEFAULTS.builderHoursPerDay).forGetter(Labour::builderHoursPerDay),
			Codec.intRange(0, 64).optionalFieldOf("base_builders", DEFAULTS.baseBuilders).forGetter(Labour::baseBuilders),
			Codec.intRange(0, 10).optionalFieldOf("builders_per_ten", DEFAULTS.buildersPerTen).forGetter(Labour::buildersPerTen),
			Codec.intRange(1, 16).optionalFieldOf("builder_share", DEFAULTS.builderShare).forGetter(Labour::builderShare)
		).apply(i, Labour::new));
	}

	/**
	 * Goods per worker per sim day, by job: positive is produced, negative consumed (a smith turns metal and fuel into
	 * tools; if inputs are short, the output shrinks with them). {@code none} is what an adult without a trade gathers.
	 *
	 * @param perDay     job name to goods name to units per day
	 * @param foodAdult  food eaten per adult per day (children and elders eat the fractions below of it)
	 * @param coinPerDay bits each worker of a job brings the treasury per day (taxes, trade); job name to bits
	 * @param prices     what a unit of goods is worth in bits, for the trade the abstract sim does with passing traders:
	 *                   what the stores cannot hold is sold at {@code exportShare} of it, and a stage short of goods buys
	 *                   them in at {@code importMarkup} times it, at most {@code importPerDay} units of each a day
	 */
	public record Production(Map<String, Map<String, Double>> perDay, double foodAdult, double foodChild, double foodElder,
		Map<String, Integer> coinPerDay, Map<String, Double> prices, double exportShare, double importMarkup, int importPerDay) {
		public static final Production DEFAULTS = new Production(defaultTable(), 2.0, 1.0, 1.5, defaultCoin(), defaultPrices(), 0.1, 1.5, 96);
		public static final Codec<Production> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)).optionalFieldOf("per_day", DEFAULTS.perDay)
				.forGetter(Production::perDay),
			Codec.doubleRange(0, 100).optionalFieldOf("food_adult", DEFAULTS.foodAdult).forGetter(Production::foodAdult),
			Codec.doubleRange(0, 100).optionalFieldOf("food_child", DEFAULTS.foodChild).forGetter(Production::foodChild),
			Codec.doubleRange(0, 100).optionalFieldOf("food_elder", DEFAULTS.foodElder).forGetter(Production::foodElder),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("coin_per_day", DEFAULTS.coinPerDay).forGetter(Production::coinPerDay),
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("prices", DEFAULTS.prices).forGetter(Production::prices),
			Codec.doubleRange(0, 10).optionalFieldOf("export_share", DEFAULTS.exportShare).forGetter(Production::exportShare),
			Codec.doubleRange(0, 100).optionalFieldOf("import_markup", DEFAULTS.importMarkup).forGetter(Production::importMarkup),
			Codec.intRange(0, 100000).optionalFieldOf("import_per_day", DEFAULTS.importPerDay).forGetter(Production::importPerDay)
		).apply(i, Production::new));

		/** Bits per unit, by {@link Goods#ordinal()}. */
		public double[] priceTable() {
			double[] p = new double[Goods.VALUES.length];
			for (Goods g : Goods.VALUES) {
				p[g.ordinal()] = this.prices.getOrDefault(g.getSerializedName(), 1.0);
			}
			return p;
		}

		private static Map<String, Double> defaultPrices() {
			Map<String, Double> m = new LinkedHashMap<>();
			m.put("food", 0.5);
			m.put("wood", 0.5);
			m.put("stone", 0.4);
			m.put("clay", 0.8);
			m.put("glass", 2.0);
			m.put("metal", 4.0);
			m.put("cloth", 2.0);
			m.put("fuel", 1.0);
			m.put("tools", 6.0);
			m.put("luxury", 5.0);
			return m;
		}

		/** The table as dense arrays: {@code [job.ordinal()][goods.ordinal()]} units per day. */
		public double[][] table() {
			double[][] t = new double[JobType.values().length][Goods.VALUES.length];
			for (JobType job : JobType.values()) {
				Map<String, Double> row = this.perDay.get(job.getSerializedName());
				if (row == null) {
					continue;
				}
				for (Goods g : Goods.VALUES) {
					t[job.ordinal()][g.ordinal()] = row.getOrDefault(g.getSerializedName(), 0.0);
				}
			}
			return t;
		}

		public int[] coin() {
			int[] c = new int[JobType.values().length];
			for (JobType job : JobType.values()) {
				c[job.ordinal()] = this.coinPerDay.getOrDefault(job.getSerializedName(), 0);
			}
			return c;
		}

		private static Map<String, Map<String, Double>> defaultTable() {
			Map<String, Map<String, Double>> t = new LinkedHashMap<>();
			row(t, JobType.NONE, "food", 2.5, "wood", 12, "stone", 10, "clay", 4, "fuel", 1);
			row(t, JobType.BUILDER, "wood", 6, "stone", 6);
			row(t, JobType.FARMER, "food", 20);
			row(t, JobType.FORESTER, "wood", 200, "fuel", 12);
			row(t, JobType.QUARRIER, "stone", 240, "clay", 24, "metal", 6);
			row(t, JobType.FISHER, "food", 14);
			row(t, JobType.HERDER, "food", 8, "cloth", 14);
			row(t, JobType.HUNTER, "food", 10, "cloth", 3);
			row(t, JobType.CARPENTER, "wood", -40, "tools", 2, "luxury", 2);
			row(t, JobType.MASON, "stone", -30, "clay", 50, "glass", 30, "fuel", -4);
			row(t, JobType.SMITH, "metal", -4, "fuel", -3, "tools", 4);
			row(t, JobType.BAKER, "food", 14);
			row(t, JobType.BREWER, "food", -4, "luxury", 4);
			row(t, JobType.INNKEEPER, "food", -3, "luxury", 1);
			row(t, JobType.MERCHANT, "luxury", 2, "metal", 1);
			row(t, JobType.MINTER, "metal", -1);
			row(t, JobType.GUARD);
			row(t, JobType.PRIEST, "luxury", 1);
			row(t, JobType.SCHOLAR, "luxury", 1);
			row(t, JobType.COURIER, "luxury", 1, "metal", 1);
			row(t, JobType.TAILOR, "cloth", 24);
			return t;
		}

		private static void row(Map<String, Map<String, Double>> t, JobType job, Object... kv) {
			Map<String, Double> r = new LinkedHashMap<>();
			for (int k = 0; k < kv.length; k += 2) {
				r.put((String) kv[k], ((Number) kv[k + 1]).doubleValue());
			}
			t.put(job.getSerializedName(), r);
		}

		private static Map<String, Integer> defaultCoin() {
			Map<String, Integer> m = new LinkedHashMap<>();
			for (JobType job : JobType.values()) {
				m.put(job.getSerializedName(), 1);
			}
			m.put(JobType.NONE.getSerializedName(), 0);
			m.put(JobType.MERCHANT.getSerializedName(), 8);
			m.put(JobType.INNKEEPER.getSerializedName(), 5);
			m.put(JobType.MINTER.getSerializedName(), 20);
			m.put(JobType.COURIER.getSerializedName(), 4);
			m.put(JobType.SMITH.getSerializedName(), 3);
			return m;
		}
	}

	/**
	 * @param childhoodDays     a child is grown after this many days
	 * @param adulthoodDays     an adult becomes an elder after this many days of adulthood
	 * @param elderDeathPerDay  the chance an elder dies on a given day
	 * @param birthPerCoupleDay the chance per couple per day of a child, while there is a free bed
	 * @param settlerPerDay     the chance per day that settlers arrive, while beds are free and food lasts
	 * @param tentSlots         people who can live in tents and lofts beyond the beds before it counts as overcrowded
	 * @param overcrowdDays     overcrowded for this many days, folk start to leave
	 * @param hungerDays        without food for this many days, folk start to leave (and elders die)
	 */
	public record Population(int childhoodDays, int adulthoodDays, double elderDeathPerDay, double birthPerCoupleDay,
		double settlerPerDay, int tentSlots, int overcrowdDays, int hungerDays) {
		public static final Population DEFAULTS = new Population(48, 240, 1 / 60.0, 1 / 30.0, 0.6, 4, 3, 4);
		public static final Codec<Population> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 10000).optionalFieldOf("childhood_days", DEFAULTS.childhoodDays).forGetter(Population::childhoodDays),
			Codec.intRange(1, 100000).optionalFieldOf("adulthood_days", DEFAULTS.adulthoodDays).forGetter(Population::adulthoodDays),
			Codec.doubleRange(0, 1).optionalFieldOf("elder_death_per_day", DEFAULTS.elderDeathPerDay).forGetter(Population::elderDeathPerDay),
			Codec.doubleRange(0, 1).optionalFieldOf("birth_per_couple_day", DEFAULTS.birthPerCoupleDay).forGetter(Population::birthPerCoupleDay),
			Codec.doubleRange(0, 1).optionalFieldOf("settler_per_day", DEFAULTS.settlerPerDay).forGetter(Population::settlerPerDay),
			Codec.intRange(0, 64).optionalFieldOf("tent_slots", DEFAULTS.tentSlots).forGetter(Population::tentSlots),
			Codec.intRange(1, 1000).optionalFieldOf("overcrowd_days", DEFAULTS.overcrowdDays).forGetter(Population::overcrowdDays),
			Codec.intRange(1, 1000).optionalFieldOf("hunger_days", DEFAULTS.hungerDays).forGetter(Population::hungerDays)
		).apply(i, Population::new));
	}

	/**
	 * @param base        what a settlement can store of each kind of goods with no storehouse
	 * @param perStorage  added per completed storage building (storehouse, granary, warehouse)
	 * @param perBuilding added per other completed building (lofts, cellars, woodpiles)
	 */
	public record Storage(int base, int perStorage, int perBuilding) {
		public static final Storage DEFAULTS = new Storage(2000, 2500, 120);
		public static final Codec<Storage> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 1_000_000).optionalFieldOf("base", DEFAULTS.base).forGetter(Storage::base),
			Codec.intRange(0, 1_000_000).optionalFieldOf("per_storage", DEFAULTS.perStorage).forGetter(Storage::perStorage),
			Codec.intRange(0, 1_000_000).optionalFieldOf("per_building", DEFAULTS.perBuilding).forGetter(Storage::perBuilding)
		).apply(i, Storage::new));
	}

	/**
	 * @param residents adults who found a camp
	 * @param goods     what they bring
	 * @param treasury  bits in the chest
	 */
	public record Founding(int residents, Map<String, Integer> goods, long treasury) {
		public static final Founding DEFAULTS = new Founding(5, defaultGoods(), 200);
		public static final Codec<Founding> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(1, 64).optionalFieldOf("residents", DEFAULTS.residents).forGetter(Founding::residents),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("goods", DEFAULTS.goods).forGetter(Founding::goods),
			Codec.LONG.optionalFieldOf("treasury", DEFAULTS.treasury).forGetter(Founding::treasury)
		).apply(i, Founding::new));

		public Map<Goods, Integer> goodsMap() {
			Map<Goods, Integer> m = new EnumMap<>(Goods.class);
			for (Goods g : Goods.VALUES) {
				m.put(g, this.goods.getOrDefault(g.getSerializedName(), 0));
			}
			return m;
		}

		private static Map<String, Integer> defaultGoods() {
			Map<String, Integer> m = new LinkedHashMap<>();
			m.put("food", 240);
			m.put("wood", 900);
			m.put("stone", 700);
			m.put("clay", 80);
			m.put("glass", 60);
			m.put("metal", 40);
			m.put("cloth", 60);
			m.put("fuel", 40);
			m.put("tools", 20);
			m.put("luxury", 0);
			return m;
		}
	}
}
