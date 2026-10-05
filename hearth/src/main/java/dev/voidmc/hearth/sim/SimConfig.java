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
	Labour labour, Production production, Population population, Storage storage, Founding founding, Daughters daughters, Resources resources) {

	public static final SimConfig DEFAULTS = new SimConfig(List.of("minecraft:overworld"), false, Embody.DEFAULTS, Clock.DEFAULTS,
		Applier.DEFAULTS, Atlas.DEFAULTS, Labour.DEFAULTS, Production.DEFAULTS, Population.DEFAULTS, Storage.DEFAULTS, Founding.DEFAULTS,
		Daughters.DEFAULTS, Resources.DEFAULTS);

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
		Daughters.CODEC.optionalFieldOf("daughters", Daughters.DEFAULTS).forGetter(SimConfig::daughters),
		// phase 3: territory, sites, chains, spoilage, prosperity, caravans, stations, extraction (docs/SIM.md §16)
		Resources.CODEC.optionalFieldOf("resources", Resources.DEFAULTS).forGetter(SimConfig::resources)
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
	 * @param treeReach          a tree's trunk keeps this many blocks more off them, so its crown does not spread over a
	 *                           square or a lane (a crown reaches 3-7 blocks from its trunk)
	 * @param adoptMargin        a historic settlement is adopted when a chunk within this of its bounds loads
	 * @param planCache          region plans kept in memory (each is recomputed on demand, deterministically)
	 */
	public record Atlas(boolean enabled, double density, int siteMargin, int maxAgeDays, double ageSkew, int villageClearance,
		Map<String, Integer> structureClearance, int guardMargin, int treeReach, int adoptMargin, int planCache) {
		public static final Atlas DEFAULTS = new Atlas(true, 0.4, 300, 400, 2.2, 20, defaultClearance(), 3, 5, 96, 256);
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
			Codec.intRange(0, 12).optionalFieldOf("tree_reach", DEFAULTS.treeReach).forGetter(Atlas::treeReach),
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
			m.put("stone", 0.25);
			m.put("clay", 0.3);
			m.put("glass", 2.0);
			m.put("metal", 4.0);
			m.put("cloth", 2.0);
			m.put("fuel", 1.0);
			m.put("tools", 6.0);
			m.put("luxury", 5.0);
			m.put("logs", 0.35);
			m.put("ore", 1.5);
			m.put("dressed_stone", 0.6);
			m.put("brick", 0.8);
			m.put("sand", 0.15);
			m.put("grain", 0.25);
			m.put("flour", 0.4);
			m.put("meat", 0.5);
			m.put("fish", 0.4);
			m.put("wool", 0.8);
			m.put("leather", 1.5);
			m.put("wax", 1.5);
			m.put("candles", 1.2);
			m.put("paper", 1.0);
			m.put("books", 12.0);
			m.put("dye", 1.0);
			m.put("salt", 0.8);
			m.put("produce", 0.4);
			m.put("specialty", 3.0);
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

		/**
		 * Phase 3: what each trade gathers and makes is data ({@code hearth_sim/gathering}, {@code hearth_sim/recipes}); this
		 * table is for the trades that do neither (a merchant's trade goods, a priest's offerings), and is ignored for a job
		 * the data covers, so an old config file with phase 2 rows does not pay a town twice.
		 */
		private static Map<String, Map<String, Double>> defaultTable() {
			Map<String, Map<String, Double>> t = new LinkedHashMap<>();
			row(t, JobType.MERCHANT, "luxury", 2);
			row(t, JobType.MINTER, "metal", -1);
			row(t, JobType.GUARD);
			row(t, JobType.PRIEST, "luxury", 1);
			row(t, JobType.COURIER, "luxury", 1);
			row(t, JobType.TRADER, "luxury", 2);
			row(t, JobType.HEALER, "produce", -1);
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
	 * Store room per goods (phase 3: each building type stores its own goods, docs/SIM.md §16).
	 *
	 * @param base        what a settlement can store of each kind of goods with no storehouse
	 * @param perStorage  added per completed storage building not listed in {@code byArchetype}
	 * @param perBuilding added per other completed building (lofts, cellars, woodpiles)
	 * @param byArchetype what particular buildings add, by archetype id, then goods name or group: {@code #all},
	 *                    {@code #edible} (food, grain, flour, meat, fish, produce, salt), {@code #raw}, {@code #processed},
	 *                    {@code #finished}, {@code #building} (timber, logs, stone, dressed stone, brick, glass, clay, sand)
	 */
	public record Storage(int base, int perStorage, int perBuilding, Map<String, Map<String, Integer>> byArchetype) {
		public static final Storage DEFAULTS = new Storage(2000, 2500, 120, defaultByArchetype());
		public static final Codec<Storage> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, 1_000_000).optionalFieldOf("base", DEFAULTS.base).forGetter(Storage::base),
			Codec.intRange(0, 1_000_000).optionalFieldOf("per_storage", DEFAULTS.perStorage).forGetter(Storage::perStorage),
			Codec.intRange(0, 1_000_000).optionalFieldOf("per_building", DEFAULTS.perBuilding).forGetter(Storage::perBuilding),
			Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.intRange(0, 10_000_000))).optionalFieldOf("by_archetype",
				DEFAULTS.byArchetype).forGetter(Storage::byArchetype)
		).apply(i, Storage::new));

		private static Map<String, Map<String, Integer>> defaultByArchetype() {
			Map<String, Map<String, Integer>> m = new LinkedHashMap<>();
			m.put("hearth:storage/storehouse", Map.of("#all", 2500));
			m.put("hearth:storage/granary", Map.of("#edible", 6000));
			m.put("hearth:storage/warehouse", Map.of("#all", 4000, "#building", 3000));
			m.put("hearth:storage/cellar", Map.of("#edible", 2000));
			m.put("hearth:work/sawmill", Map.of("logs", 1500, "wood", 1000));
			m.put("hearth:work/logging_camp", Map.of("logs", 2000));
			m.put("hearth:work/quarry", Map.of("stone", 3000, "dressed_stone", 800));
			m.put("hearth:work/masonry", Map.of("dressed_stone", 1000, "brick", 1000));
			m.put("hearth:work/carpentry", Map.of("wood", 800, "logs", 600));
			m.put("hearth:work/smithy", Map.of("metal", 300, "tools", 200, "ore", 400, "fuel", 300));
			m.put("hearth:work/smelter", Map.of("ore", 1200, "metal", 400, "fuel", 600));
			m.put("hearth:work/charcoal_kiln", Map.of("fuel", 1200, "logs", 800));
			m.put("hearth:work/brick_kiln", Map.of("brick", 1500, "clay", 1200));
			m.put("hearth:work/glassworks", Map.of("glass", 800, "sand", 1200));
			m.put("hearth:work/windmill", Map.of("grain", 2000, "flour", 1500));
			m.put("hearth:work/watermill", Map.of("grain", 2000, "flour", 1500));
			m.put("hearth:work/bakery", Map.of("flour", 600, "food", 600));
			m.put("hearth:work/barn", Map.of("wool", 800, "meat", 400, "grain", 1200));
			m.put("hearth:work/tannery", Map.of("leather", 600));
			m.put("hearth:work/chandler", Map.of("candles", 600, "wax", 400));
			m.put("hearth:work/salt_works", Map.of("salt", 2000));
			m.put("hearth:work/apiary", Map.of("wax", 400, "produce", 400));
			m.put("hearth:work/paper_mill", Map.of("paper", 800));
			m.put("hearth:commerce/caravan_yard", Map.of("#all", 1500));
			m.put("hearth:commerce/market_hall", Map.of("#finished", 1000, "#processed", 600));
			m.put("hearth:extract/quarry_pit", Map.of("stone", 4000));
			m.put("hearth:extract/mine", Map.of("ore", 1500, "stone", 2000, "fuel", 800));
			m.put("hearth:extract/clay_pit", Map.of("clay", 2000));
			m.put("hearth:extract/sand_pit", Map.of("sand", 2000));
			m.put("hearth:extract/plantation", Map.of("logs", 2000));
			return m;
		}
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
			m.put("logs", 200);
			m.put("grain", 120);
			m.put("produce", 60);
			m.put("dressed_stone", 60);
			m.put("candles", 20);
			return m;
		}
	}

	/**
	 * The resource economy (docs/LIVING_WORLD.md §7, docs/SIM.md §16). The goods' chains, the site tables and the land
	 * of each biome are data ({@code data/<ns>/hearth_sim/}); these are the rules round them.
	 */
	public record Resources(Lands land, Chains chains, Spoilage spoilage, Prosperity prosperity, Caravans caravans, Stations stations,
		Extraction extraction) {
		public static final Resources DEFAULTS = new Resources(Lands.DEFAULTS, Chains.DEFAULTS, Spoilage.DEFAULTS, Prosperity.DEFAULTS,
			Caravans.DEFAULTS, Stations.DEFAULTS, Extraction.DEFAULTS);
		public static final Codec<Resources> CODEC = RecordCodecBuilder.create(i -> i.group(
			Lands.CODEC.optionalFieldOf("land", Lands.DEFAULTS).forGetter(Resources::land),
			Chains.CODEC.optionalFieldOf("chains", Chains.DEFAULTS).forGetter(Resources::chains),
			Spoilage.CODEC.optionalFieldOf("spoilage", Spoilage.DEFAULTS).forGetter(Resources::spoilage),
			Prosperity.CODEC.optionalFieldOf("prosperity", Prosperity.DEFAULTS).forGetter(Resources::prosperity),
			Caravans.CODEC.optionalFieldOf("caravans", Caravans.DEFAULTS).forGetter(Resources::caravans),
			Stations.CODEC.optionalFieldOf("stations", Stations.DEFAULTS).forGetter(Resources::stations),
			Extraction.CODEC.optionalFieldOf("extraction", Extraction.DEFAULTS).forGetter(Resources::extraction)
		).apply(i, Resources::new));
	}

	/**
	 * @param territory    the radius of land each tier works (blocks from the heart), never into another town's core
	 * @param coreMargin   blocks beyond a settlement's bounds that count as its core, which no neighbour's territory enters
	 * @param surveyCells  the survey grid is at most this many cells across the territory
	 * @param surveyStep   and its cells at least this many blocks apart
	 * @param maxSites     resource sites a settlement keeps at most
	 * @param resurveyDays the territory is surveyed again this often (and whenever the tier changes)
	 */
	public record Lands(Map<String, Integer> territory, int coreMargin, int surveyCells, int surveyStep, int maxSites, int resurveyDays) {
		public static final Lands DEFAULTS = new Lands(defaultTerritory(), 32, 36, 24, 48, 120);
		public static final Codec<Lands> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.intRange(32, 4096)).optionalFieldOf("territory", DEFAULTS.territory).forGetter(Lands::territory),
			Codec.intRange(0, 512).optionalFieldOf("core_margin", DEFAULTS.coreMargin).forGetter(Lands::coreMargin),
			Codec.intRange(8, 128).optionalFieldOf("survey_cells", DEFAULTS.surveyCells).forGetter(Lands::surveyCells),
			Codec.intRange(8, 128).optionalFieldOf("survey_step", DEFAULTS.surveyStep).forGetter(Lands::surveyStep),
			Codec.intRange(4, 512).optionalFieldOf("max_sites", DEFAULTS.maxSites).forGetter(Lands::maxSites),
			Codec.intRange(1, 10000).optionalFieldOf("resurvey_days", DEFAULTS.resurveyDays).forGetter(Lands::resurveyDays)
		).apply(i, Lands::new));

		private static Map<String, Integer> defaultTerritory() {
			Map<String, Integer> m = new LinkedHashMap<>();
			m.put("camp", 96);
			m.put("hamlet", 160);
			m.put("village", 256);
			m.put("town", 384);
			m.put("city", 512);
			return m;
		}
	}

	/**
	 * @param toolWear      tools a worker wears out a day where the data does not say (smiths and carpenters make more)
	 * @param noToolsFactor what a worker manages without tools, as a share of a day's work
	 * @param noSiteFactor  what a gatherer scrapes together where the territory has none of their kind of site, times the
	 *                      data's {@code no_site} yields
	 * @param builderShare  the share of a builder's day spent hewing timber and dressing stone for their own work
	 * @param haulRange     sites further than this from the heart (blocks) yield less for the walk; haulers carry it back
	 * @param haulPerHauler gatherers one hauler serves at full yield
	 */
	public record Chains(double toolWear, double noToolsFactor, double noSiteFactor, double builderShare, int haulRange, int haulPerHauler) {
		public static final Chains DEFAULTS = new Chains(0.02, 0.6, 1.0, 0.15, 384, 4);
		public static final Codec<Chains> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.doubleRange(0, 10).optionalFieldOf("tool_wear", DEFAULTS.toolWear).forGetter(Chains::toolWear),
			Codec.doubleRange(0, 1).optionalFieldOf("no_tools_factor", DEFAULTS.noToolsFactor).forGetter(Chains::noToolsFactor),
			Codec.doubleRange(0, 10).optionalFieldOf("no_site_factor", DEFAULTS.noSiteFactor).forGetter(Chains::noSiteFactor),
			Codec.doubleRange(0, 1).optionalFieldOf("builder_share", DEFAULTS.builderShare).forGetter(Chains::builderShare),
			Codec.intRange(16, 100000).optionalFieldOf("haul_range", DEFAULTS.haulRange).forGetter(Chains::haulRange),
			Codec.intRange(1, 100).optionalFieldOf("haul_per_hauler", DEFAULTS.haulPerHauler).forGetter(Chains::haulPerHauler)
		).apply(i, Chains::new));
	}

	/**
	 * @param enabled whether food spoils at all
	 * @param rate    multiplier on the data's per-good daily spoilage (outside cool stores)
	 * @param cool    what still spoils inside a granary or cellar, as a share of the outside rate
	 * @param salted  meat and fish spoil at this share of the rate while there is salt in store
	 */
	public record Spoilage(boolean enabled, double rate, double cool, double salted) {
		public static final Spoilage DEFAULTS = new Spoilage(true, 1.0, 0.1, 0.4);
		public static final Codec<Spoilage> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Spoilage::enabled),
			Codec.doubleRange(0, 100).optionalFieldOf("rate", DEFAULTS.rate).forGetter(Spoilage::rate),
			Codec.doubleRange(0, 1).optionalFieldOf("cool", DEFAULTS.cool).forGetter(Spoilage::cool),
			Codec.doubleRange(0, 1).optionalFieldOf("salted", DEFAULTS.salted).forGetter(Spoilage::salted)
		).apply(i, Spoilage::new));
	}

	/**
	 * @param enabled        prosperity, carrying capacity, boom and bust at all
	 * @param capacityLimits growth stops at what the land and the trade can feed (settlers and births dwindle past it)
	 * @param declineBelow   prosperity under this is decline: folk leave, settlers stay away, empty houses are left
	 * @param riseAbove      over this, a boom: the town builds finer and draws settlers
	 * @param finishTier     prosperity raises (and poverty lowers) the finish tier of what the town builds, by one
	 * @param emigration     the chance a day, in deep decline, that a household leaves
	 * @param abandonSlack   empty beds a declining town keeps before it leaves a house standing empty
	 * @param smoothing      how much of a day's fortunes moves the slow average (0.05: about three weeks)
	 * @param decayStepDays  days between the steps of time's work on a house a decline left empty (decay 0.25 the day it is left, a ruin after three steps; 0: houses do not weather)
	 */
	public record Prosperity(boolean enabled, boolean capacityLimits, double declineBelow, double riseAbove, boolean finishTier, double emigration,
		int abandonSlack, double smoothing, int decayStepDays) {
		public static final Prosperity DEFAULTS = new Prosperity(true, true, 0.35, 0.7, true, 0.25, 6, 0.05, 24);

		public Prosperity(boolean enabled, boolean capacityLimits, double declineBelow, double riseAbove, boolean finishTier, double emigration,
			int abandonSlack, double smoothing) {
			this(enabled, capacityLimits, declineBelow, riseAbove, finishTier, emigration, abandonSlack, smoothing, DEFAULTS.decayStepDays);
		}

		public static final Codec<Prosperity> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Prosperity::enabled),
			Codec.BOOL.optionalFieldOf("capacity_limits_growth", DEFAULTS.capacityLimits).forGetter(Prosperity::capacityLimits),
			Codec.doubleRange(0, 1).optionalFieldOf("decline_below", DEFAULTS.declineBelow).forGetter(Prosperity::declineBelow),
			Codec.doubleRange(0, 1).optionalFieldOf("rise_above", DEFAULTS.riseAbove).forGetter(Prosperity::riseAbove),
			Codec.BOOL.optionalFieldOf("finish_tier", DEFAULTS.finishTier).forGetter(Prosperity::finishTier),
			Codec.doubleRange(0, 1).optionalFieldOf("emigration", DEFAULTS.emigration).forGetter(Prosperity::emigration),
			Codec.intRange(0, 1000).optionalFieldOf("abandon_slack", DEFAULTS.abandonSlack).forGetter(Prosperity::abandonSlack),
			Codec.doubleRange(0.001, 1).optionalFieldOf("smoothing", DEFAULTS.smoothing).forGetter(Prosperity::smoothing),
			Codec.intRange(0, 100000).optionalFieldOf("decay_step_days", DEFAULTS.decayStepDays).forGetter(Prosperity::decayStepDays)
		).apply(i, Prosperity::new));
	}

	/**
	 * @param enabled      caravans between towns at all (the background trade with passing traders goes on either way)
	 * @param maxDistance  towns further apart than this (blocks) do not trade
	 * @param intervalDays a town sends a caravan at most this often
	 * @param cargo        units a caravan carries
	 * @param speed        blocks a laden caravan covers in a sim day on easy ground
	 * @param risk         the chance per 1000 blocks that a caravan is lost to bandits or the wild (rough land more)
	 * @param minValue     a caravan sets out only for a cargo worth this many bits to the buyer
	 * @param margin       the seller's share of the price difference (the rest is the buyer's saving)
	 */
	public record Caravans(boolean enabled, int maxDistance, int intervalDays, int cargo, double speed, double risk, int minValue, double margin) {
		public static final Caravans DEFAULTS = new Caravans(true, 3200, 4, 640, 1800, 0.03, 60, 0.5);
		public static final Codec<Caravans> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Caravans::enabled),
			Codec.intRange(100, 100000).optionalFieldOf("max_distance", DEFAULTS.maxDistance).forGetter(Caravans::maxDistance),
			Codec.intRange(1, 1000).optionalFieldOf("interval_days", DEFAULTS.intervalDays).forGetter(Caravans::intervalDays),
			Codec.intRange(1, 100000).optionalFieldOf("cargo", DEFAULTS.cargo).forGetter(Caravans::cargo),
			Codec.doubleRange(10, 1_000_000).optionalFieldOf("speed", DEFAULTS.speed).forGetter(Caravans::speed),
			Codec.doubleRange(0, 1).optionalFieldOf("risk", DEFAULTS.risk).forGetter(Caravans::risk),
			Codec.intRange(0, 1_000_000).optionalFieldOf("min_value", DEFAULTS.minValue).forGetter(Caravans::minValue),
			Codec.doubleRange(0, 1).optionalFieldOf("margin", DEFAULTS.margin).forGetter(Caravans::margin)
		).apply(i, Caravans::new));
	}

	/**
	 * @param enabled    households move up and down the stations with their means
	 * @param periodDays each household is looked at this often (staggered, so a day only touches a few)
	 * @param upAt       a household earning this many times what its trade usually earns moves up a station
	 * @param downAt     one earning less than this share of it moves down
	 * @param gentryAt   a merchant household earning this many times its trade's usual, in a town or city, becomes gentry
	 */
	public record Stations(boolean enabled, int periodDays, double upAt, double downAt, double gentryAt) {
		public static final Stations DEFAULTS = new Stations(true, 10, 2.2, 0.5, 5.0);
		public static final Codec<Stations> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Stations::enabled),
			Codec.intRange(1, 1000).optionalFieldOf("period_days", DEFAULTS.periodDays).forGetter(Stations::periodDays),
			Codec.doubleRange(1, 100).optionalFieldOf("up_at", DEFAULTS.upAt).forGetter(Stations::upAt),
			Codec.doubleRange(0, 1).optionalFieldOf("down_at", DEFAULTS.downAt).forGetter(Stations::downAt),
			Codec.doubleRange(1, 1000).optionalFieldOf("gentry_at", DEFAULTS.gentryAt).forGetter(Stations::gentryAt)
		).apply(i, Stations::new));
	}

	/**
	 * @param abstractOnly   extraction changes no terrain: excavations are not written into the world and natural woods
	 *                       are not felled, the loop runs as numbers (LIVING_WORLD.md §7.8)
	 * @param hoursPerDay    hours a quarrier or miner works an excavation a sim day (at the builders' rate per hour)
	 * @param seamFactor     a mine follows the seams: its cuts meet this many times the ore of plain rock
	 * @param fellWoods      trees taken abstractly from natural woods come down in loaded chunks (and saplings go in)
	 * @param fellBudgetMs   time per tick for finding and felling them
	 * @param fellAway       never fell a tree within this many blocks of a player (embodied foresters do that in view)
	 * @param maxFellingDebt logs at most owed to the world per site (the rest are forgotten: the wood regrew meanwhile)
	 */
	public record Extraction(boolean abstractOnly, double hoursPerDay, double seamFactor, boolean fellWoods, double fellBudgetMs, int fellAway,
		int maxFellingDebt) {
		public static final Extraction DEFAULTS = new Extraction(false, 9, 6, true, 0.25, 48, 2000);
		public static final Codec<Extraction> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("abstract_only", DEFAULTS.abstractOnly).forGetter(Extraction::abstractOnly),
			Codec.doubleRange(0, 24).optionalFieldOf("hours_per_day", DEFAULTS.hoursPerDay).forGetter(Extraction::hoursPerDay),
			Codec.doubleRange(0, 100).optionalFieldOf("seam_factor", DEFAULTS.seamFactor).forGetter(Extraction::seamFactor),
			Codec.BOOL.optionalFieldOf("fell_natural_woods", DEFAULTS.fellWoods).forGetter(Extraction::fellWoods),
			Codec.doubleRange(0, 50).optionalFieldOf("fell_budget_ms", DEFAULTS.fellBudgetMs).forGetter(Extraction::fellBudgetMs),
			Codec.intRange(0, 512).optionalFieldOf("fell_away_from_players", DEFAULTS.fellAway).forGetter(Extraction::fellAway),
			Codec.intRange(0, 1_000_000).optionalFieldOf("max_felling_debt", DEFAULTS.maxFellingDebt).forGetter(Extraction::maxFellingDebt)
		).apply(i, Extraction::new));
	}
}
