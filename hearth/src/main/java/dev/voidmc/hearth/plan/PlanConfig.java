package dev.voidmc.hearth.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The planner's tuning knobs, {@code config/hearth/plan.json} (PLANNING.md, "Tuning"). Every field is optional with the
 * default below, so an old file keeps working.
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
 */
public record PlanConfig(double roadGrade, double stairGrade, int maxCut, int bridgeMaxLength, int slopeTolerance, int hillsideTolerance,
	int terraceMaxHeight, int doorPathMax, int roadPlotLength, double densityScale, double verticalBias, double sprawlBias,
	double housingMargin, int searchBudget, int cacheTiles) {

	public static final PlanConfig DEFAULTS = new PlanConfig(0.3, 1.0, 4, 40, 3, 7, 7, 3, 48, 1.0, 1.0, 1.0, 0.1, 1600, 4096);

	public static final Codec<PlanConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
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
		Codec.INT.optionalFieldOf("cache_tiles", DEFAULTS.cacheTiles).forGetter(PlanConfig::cacheTiles)
	).apply(i, PlanConfig::new));
}
