package dev.voidmc.hearth.sim.land;

import net.minecraft.resources.Identifier;

/**
 * How much ore a dig through a column's rock meets, estimated from vanilla's ore distributions (the generator's
 * placements, by height) and the biome's multipliers (gold in the badlands, iron in the mountains, copper under the
 * dripstone), without generating anything. Units: ore blocks per block of rock removed.
 *
 * <p>The bands follow the 26.x overworld ore placements: coal from y 0 to 192 peaking at 96 (and an upper band from
 * 136), iron round y 16 and an upper mountain band peaking near 232, copper round y 48, gold deep round y −16. The
 * frequencies are rough averages of those placements' counts and vein sizes; mines that follow the seams find several
 * times more (the {@code seam_factor} in the config), which is what makes a mine worth more than a quarry.
 */
public final class OreModel {
	/** Ore blocks per rock block at the peak of each band. */
	private static final double COAL = 0.012;
	private static final double COAL_UPPER = 0.005;
	private static final double IRON = 0.006;
	private static final double IRON_UPPER = 0.006;
	private static final double COPPER = 0.007;
	private static final double GOLD = 0.0015;

	public static final Identifier RAW_IRON = Identifier.withDefaultNamespace("raw_iron");
	public static final Identifier RAW_COPPER = Identifier.withDefaultNamespace("raw_copper");
	public static final Identifier RAW_GOLD = Identifier.withDefaultNamespace("raw_gold");

	private OreModel() {
	}

	/**
	 * @param fuel coal per block (goes to FUEL)
	 * @param ore  metal ores per block (iron, copper and gold together: ORE)
	 * @param main the ore there is most of, as the raw item
	 */
	public record Estimate(float ore, float fuel, Identifier main) {
	}

	/**
	 * @param top     the ground (the dig starts here)
	 * @param depth   how far down it reaches
	 * @param mult    the biome's multipliers: coal, iron, copper, gold
	 * @param exposed caverns open the rock (cave walls show their veins): richer workings
	 */
	public static Estimate estimate(int top, int depth, float[] mult, boolean exposed) {
		double coal = 0;
		double iron = 0;
		double copper = 0;
		double gold = 0;
		int n = 0;
		for (int y = top; y >= top - Math.max(1, depth); y -= 4) {
			coal += tent(y, 0, 96, 192) * COAL + (y >= 136 ? COAL_UPPER : 0);
			iron += tent(y, -24, 16, 56) * IRON + tent(y, 80, 232, 384) * IRON_UPPER;
			copper += tent(y, -16, 48, 112) * COPPER;
			gold += tent(y, -64, -16, 32) * GOLD;
			n++;
		}
		double k = exposed ? 1.4 : 1;
		coal = coal / n * mult[0] * k;
		iron = iron / n * mult[1] * k;
		copper = copper / n * mult[2] * k;
		gold = gold / n * mult[3] * k;
		Identifier main = iron >= copper && iron >= gold ? RAW_IRON : copper >= gold ? RAW_COPPER : RAW_GOLD;
		return new Estimate((float) (iron + copper + gold), (float) coal, main);
	}

	/** 0 outside [lo, hi], 1 at the peak, linear between. */
	static double tent(int y, int lo, int peak, int hi) {
		if (y <= lo || y >= hi) {
			return 0;
		}
		return y <= peak ? (y - lo) / (double) (peak - lo) : (hi - y) / (double) (hi - peak);
	}
}
