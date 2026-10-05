package dev.voidmc.hearth.build.age;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.build.draft.Draft;
import net.minecraft.nbt.CompoundTag;

/**
 * Decay: what time does to a building nobody keeps (docs/LIVING_WORLD.md §7.2 boom and bust, §8.3). A deterministic
 * pass over any finished drawing, driven by the plot param {@code decay}, a number from 0 to 1:
 *
 * <table>
 *   <caption>the decay scale</caption>
 *   <tr><th>decay</th><th>state</th><th>what it looks like</th></tr>
 *   <tr><td>0</td><td>kept</td><td>as built</td></tr>
 *   <tr><td>0.05 – 0.2</td><td>weathered</td><td>still lived in: moss creeping up the plinth on the north side, ivy at the
 *   corners, cracked stone, a shutter hanging askew, weeds in the yard. The light rule holds in full.</td></tr>
 *   <tr><td>0.25</td><td>{@link #ABANDONED}</td><td>left empty: the household took its lamps, bedding and pots; candles and
 *   hearths are cold; crops dead, the garden going to grass; cobwebs. From here on the building is nobody's: its beds,
 *   workplaces and stores are no longer points of interest and it need not be lit.</td></tr>
 *   <tr><td>0.5</td><td>abandoned for years</td><td>holes in the roof and a broken ridge, the door off its hinges or hanging
 *   open, most glass and shutters gone, ivy to the eaves, leaves and weeds inside under the holes, upper floors sagging
 *   through, roof tiles fallen into the rooms</td></tr>
 *   <tr><td>0.75</td><td>derelict</td><td>the roof mostly gone, gables crumbling from the top, upper floors fallen, the top
 *   courses of the walls broken, rubble mounds and fallen beams, bushes in the rooms, floors rotted to earth</td></tr>
 *   <tr><td>1</td><td>ruin</td><td>a roofless shell: walls standing to ragged heights (corners and stone longest, timber
 *   least), a chimney stack on its own, no floors above the ground, rubble, a tree growing in the hall</td></tr>
 * </table>
 *
 * <p>Every block gets a <i>life</i>: the decay at which it is gone (roofs short, the top first; upper floors next;
 * walls long, lower courses and corners longest, stone outlasting timber; lamps and bedding taken at 0.25). Lives are
 * capped by what holds a block up (a widest-path pass from the ground through what supports what), so the pass is
 * <b>monotone</b> (a building at 0.6 has lost everything it had lost at 0.4) and <b>nothing ever floats</b> at any
 * decay. Growth (moss, ivy, weeds, leaf litter, cobwebs, bushes, trees, rubble where things fell) is laid over what
 * stands. The same spec gives the same ruin. Accepted forms of the param: a float or double 0–1, an int percent
 * (50), or a string ("0.5").
 */
public final class Decay {
	/** The plot param. */
	public static final String PARAM = "decay";
	/**
	 * The decay at which a building counts as abandoned: from here its lamps, beds and points of interest are gone and
	 * the light rule no longer applies ({@link dev.voidmc.hearth.build.check.LightCheck}).
	 */
	public static final double ABANDONED = 0.25;

	private Decay() {
	}

	/** The decay a spec asks for, 0 to 1. */
	public static double level(PlotSpec spec) {
		return level(spec.params());
	}

	/** The decay in a param compound, 0 to 1 (see the class javadoc for the forms accepted). */
	public static double level(CompoundTag params) {
		if (!params.contains(PARAM)) {
			return 0;
		}
		double v;
		var t = params.get(PARAM);
		if (t instanceof net.minecraft.nbt.NumericTag n) {
			v = n.doubleValue();
			if (t instanceof net.minecraft.nbt.IntTag && v > 1) {
				v /= 100.0;
			}
		} else {
			try {
				v = Double.parseDouble(params.getStringOr(PARAM, "0").trim());
			} catch (NumberFormatException e) {
				v = 0;
			}
			if (v > 1) {
				v /= 100.0;
			}
		}
		return Double.isNaN(v) ? 0 : Math.clamp(v, 0, 1);
	}

	/** Whether a spec is an abandoned building's: nobody's home, exempt from the light rule. */
	public static boolean unlit(PlotSpec spec) {
		return level(spec) >= ABANDONED;
	}

	/** Decays a finished drawing to the given level (nothing at 0). */
	public static void apply(Draft d, double decay) {
		if (decay <= 0) {
			return;
		}
		new Ruin(d, Math.min(1, decay)).run();
	}
}
