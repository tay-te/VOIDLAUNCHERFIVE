package dev.voidmc.hearth.plan;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The planner's tuning knobs, {@code config/hearth/plan.json} (PLANNING.md, "Tuning"). Every field is optional with the
 * default below, so an old file keeps working. The phase 3 switches ({@link Living}) sit in the same flat JSON object.
 *
 * @param roadGrade        steepest grade a road is graded to (rise per block); steeper ground gets stairs
 * @param stairGrade       steepest grade a stair street climbs (1 = a full block up per block forward)
 * @param maxCut           deepest cut or fill a road may make to stay within its grade before it must turn or stop
 * @param bridgeMaxLength  longest bridge span over water, in blocks
 * @param slopeTolerance   ground relief under a plot (highest minus lowest) a building takes without a terrace
 * @param hillsideTolerance the same for HILLSIDE archetypes, which step down the slope instead
 * @param terraceMaxHeight tallest retaining wall a terrace may need
 * @param doorPathMax      longest garden path from a road to a door set back from a diagonal street
 * @param roadPlotLength   longest road plot: long roads are issued as several plots, built one after another
 * @param densityScale     multiplies every layout's core radius (below 1: tighter towns; above: looser)
 * @param verticalBias     multiplies every layout's preference for tall buildings and growing up
 * @param sprawlBias       multiplies how far expansive layouts spread their farmsteads
 * @param housingMargin    spare beds kept ahead of the population, as a fraction of it (at least 2)
 * @param searchBudget     most frontage positions examined per placement (bounds the cost of {@code next})
 * @param cacheTiles       terrain tiles (256 columns) kept per world for planning
 * @param liveTimeLimitMs  a call on the server thread stops searching frontage after this long and lets the need rest
 *                         (a cold terrain cache in a city can otherwise cost a quarter of a second); histories, on their
 *                         own threads, are never cut short, so they stay deterministic
 * @param living           the living-world rules (LIVING_WORLD.md §7.2, §8): stations, resources, law, nobility, the deep
 */
public record PlanConfig(double roadGrade, double stairGrade, int maxCut, int bridgeMaxLength, int slopeTolerance, int hillsideTolerance,
	int terraceMaxHeight, int doorPathMax, int roadPlotLength, double densityScale, double verticalBias, double sprawlBias,
	double housingMargin, int searchBudget, int cacheTiles, int liveTimeLimitMs, Living living) {

	public static final PlanConfig DEFAULTS = new PlanConfig(0.3, 1.0, 4, 40, 3, 7, 7, 3, 48, 1.0, 1.0, 1.0, 0.1, 1600, 16384, 10, Living.DEFAULTS);

	private static final MapCodec<PlanConfig> CORE = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.DOUBLE.optionalFieldOf("road_grade", DEFAULTS.roadGrade).forGetter(PlanConfig::roadGrade),
		Codec.DOUBLE.optionalFieldOf("stair_grade", DEFAULTS.stairGrade).forGetter(PlanConfig::stairGrade),
		Codec.INT.optionalFieldOf("max_cut", DEFAULTS.maxCut).forGetter(PlanConfig::maxCut),
		Codec.INT.optionalFieldOf("bridge_max_length", DEFAULTS.bridgeMaxLength).forGetter(PlanConfig::bridgeMaxLength),
		Codec.INT.optionalFieldOf("slope_tolerance", DEFAULTS.slopeTolerance).forGetter(PlanConfig::slopeTolerance),
		Codec.INT.optionalFieldOf("hillside_tolerance", DEFAULTS.hillsideTolerance).forGetter(PlanConfig::hillsideTolerance),
		Codec.INT.optionalFieldOf("terrace_max_height", DEFAULTS.terraceMaxHeight).forGetter(PlanConfig::terraceMaxHeight),
		Codec.INT.optionalFieldOf("door_path_max", DEFAULTS.doorPathMax).forGetter(PlanConfig::doorPathMax),
		Codec.INT.optionalFieldOf("road_plot_length", DEFAULTS.roadPlotLength).forGetter(PlanConfig::roadPlotLength),
		Codec.DOUBLE.optionalFieldOf("density_scale", DEFAULTS.densityScale).forGetter(PlanConfig::densityScale),
		Codec.DOUBLE.optionalFieldOf("vertical_bias", DEFAULTS.verticalBias).forGetter(PlanConfig::verticalBias),
		Codec.DOUBLE.optionalFieldOf("sprawl_bias", DEFAULTS.sprawlBias).forGetter(PlanConfig::sprawlBias),
		Codec.DOUBLE.optionalFieldOf("housing_margin", DEFAULTS.housingMargin).forGetter(PlanConfig::housingMargin),
		Codec.INT.optionalFieldOf("search_budget", DEFAULTS.searchBudget).forGetter(PlanConfig::searchBudget),
		Codec.INT.optionalFieldOf("cache_tiles", DEFAULTS.cacheTiles).forGetter(PlanConfig::cacheTiles),
		Codec.INT.optionalFieldOf("live_time_limit_ms", DEFAULTS.liveTimeLimitMs).forGetter(PlanConfig::liveTimeLimitMs)
	).apply(i, (a, b, c, d, e, f, g, h, k, l, m, n, o, p, q, r) -> new PlanConfig(a, b, c, d, e, f, g, h, k, l, m, n, o, p, q, r, Living.DEFAULTS)));

	/** Both halves in one flat object (a record codec takes at most sixteen fields). */
	public static final Codec<PlanConfig> CODEC = Codec.mapPair(CORE, Living.MAP_CODEC)
		.xmap(pair -> pair.getFirst().withLiving(pair.getSecond()), c -> Pair.of(c, c.living()))
		.codec();

	public PlanConfig withLiving(Living l) {
		return new PlanConfig(this.roadGrade, this.stairGrade, this.maxCut, this.bridgeMaxLength, this.slopeTolerance, this.hillsideTolerance,
			this.terraceMaxHeight, this.doorPathMax, this.roadPlotLength, this.densityScale, this.verticalBias, this.sprawlBias, this.housingMargin,
			this.searchBudget, this.cacheTiles, this.liveTimeLimitMs, l);
	}

	/**
	 * The living-world rules a server owner may want to change (LIVING_WORLD.md §7.2, §8).
	 *
	 * @param stations           class-aware housing: each station asks for its own homes in its own quarter (§8.1); off,
	 *                           homes are chosen by density alone as in phase 2
	 * @param extraction         quarries, mines, clay and sand pits and woodlots on the territory's resource sites (§7.2)
	 * @param haulMaxDistance    farthest a resource site may lie from the heart and still be worked, in blocks
	 * @param carryingCapacity   houses are planned only for as many folk as the territory's food and trade can carry
	 * @param tradeMargin        how far past what its own land feeds trade lets a town grow (1.25 = a quarter more)
	 * @param localMaterials     every plot gets the {@code local} param: the territory's woods and stone (§7.2)
	 * @param law                courthouse, jail, pillory, gallows, watch houses (§8.5)
	 * @param gallows            whether towns raise a gallows at all
	 * @param tollGates          toll gates on the roads into towns
	 * @param social             taverns, playhouses, infirmaries, almshouses, cemeteries, festival grounds, monasteries,
	 *                           hostels, a thieves' den in the poor quarter (§8.5)
	 * @param nobility           the lord's keep at town tier, a castle or palace at city tier, stables and gardens (§8.1)
	 * @param deepTowns          deep towns in the realms under mining towns (§8.2); needs an underground probe
	 * @param deepShare          the share of a town's folk the deep town grows to house
	 * @param prevailingWind     the direction the wind blows from, in degrees clockwise from north (0 north, 90 east);
	 *                           negative takes one per world from its seed. Tanneries and the poor quarter go downwind.
	 */
	public record Living(boolean stations, boolean extraction, int haulMaxDistance, boolean carryingCapacity, double tradeMargin,
		boolean localMaterials, boolean law, boolean gallows, boolean tollGates, boolean social, boolean nobility, boolean deepTowns,
		double deepShare, double prevailingWind) {
		public static final Living DEFAULTS = new Living(true, true, 320, true, 1.25, true, true, true, true, true, true, true, 0.25, -1);

		public static final MapCodec<Living> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.BOOL.optionalFieldOf("stations", DEFAULTS.stations).forGetter(Living::stations),
			Codec.BOOL.optionalFieldOf("extraction", DEFAULTS.extraction).forGetter(Living::extraction),
			Codec.INT.optionalFieldOf("haul_max_distance", DEFAULTS.haulMaxDistance).forGetter(Living::haulMaxDistance),
			Codec.BOOL.optionalFieldOf("carrying_capacity", DEFAULTS.carryingCapacity).forGetter(Living::carryingCapacity),
			Codec.DOUBLE.optionalFieldOf("trade_margin", DEFAULTS.tradeMargin).forGetter(Living::tradeMargin),
			Codec.BOOL.optionalFieldOf("local_materials", DEFAULTS.localMaterials).forGetter(Living::localMaterials),
			Codec.BOOL.optionalFieldOf("law_buildings", DEFAULTS.law).forGetter(Living::law),
			Codec.BOOL.optionalFieldOf("gallows", DEFAULTS.gallows).forGetter(Living::gallows),
			Codec.BOOL.optionalFieldOf("toll_gates", DEFAULTS.tollGates).forGetter(Living::tollGates),
			Codec.BOOL.optionalFieldOf("social_buildings", DEFAULTS.social).forGetter(Living::social),
			Codec.BOOL.optionalFieldOf("nobility", DEFAULTS.nobility).forGetter(Living::nobility),
			Codec.BOOL.optionalFieldOf("deep_towns", DEFAULTS.deepTowns).forGetter(Living::deepTowns),
			Codec.DOUBLE.optionalFieldOf("deep_share", DEFAULTS.deepShare).forGetter(Living::deepShare),
			Codec.DOUBLE.optionalFieldOf("prevailing_wind", DEFAULTS.prevailingWind).forGetter(Living::prevailingWind)
		).apply(i, Living::new));
	}
}
