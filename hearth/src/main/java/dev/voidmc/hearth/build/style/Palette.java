package dev.voidmc.hearth.build.style;

import dev.voidmc.hearth.build.draft.Noise;
import java.util.List;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * The materials that play one role in a style, with weights and placement biases. A pick is deterministic per position:
 * each entry scores {@code weight × biases × (0.6·patch + 0.4·grain)} where {@code patch} is smooth noise with a
 * two-to-three block grain (so accents come in small clusters, as a builder lays them) and {@code grain} is per-block,
 * and the best score wins.
 *
 * <p>Biases raise an entry's weight where it belongs: {@code low} near the ground (moss, weathered and cracked
 * stone), {@code high} toward the eaves (clean, dressed), {@code north} on the north faces (moss), {@code corner} at
 * corners (quoins).
 */
public final class Palette {
	private final List<Entry> entries;

	public Palette(List<Entry> entries) {
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("a palette needs at least one material");
		}
		this.entries = List.copyOf(entries);
	}

	public static Palette of(Material material) {
		return new Palette(List.of(new Entry(material, 1, 0, 0, 0, 0)));
	}

	public List<Entry> entries() {
		return this.entries;
	}

	/**
	 * This palette with one material chosen by weight (deterministically from {@code seed}) put first and the others
	 * kept only as fallbacks for forms the chosen one cannot cut.
	 */
	public Palette chooseOne(long seed) {
		if (this.entries.size() == 1) {
			return this;
		}
		double total = 0;
		for (Entry e : this.entries) {
			total += Math.max(0, e.weight());
		}
		double r = Noise.unit(seed, 0x0C0, 0, 0) * total;
		int pick = 0;
		for (int i = 0; i < this.entries.size(); i++) {
			r -= Math.max(0, this.entries.get(i).weight());
			if (r < 0) {
				pick = i;
				break;
			}
		}
		java.util.List<Entry> out = new java.util.ArrayList<>();
		Entry chosen = this.entries.get(pick);
		out.add(new Entry(chosen.material(), 1, 0, 0, 0, 0));
		for (int i = 0; i < this.entries.size(); i++) {
			if (i != pick) {
				Entry e = this.entries.get(i);
				out.add(new Entry(e.material(), 0, 0, 0, 0, 0));
			}
		}
		return new Palette(out);
	}

	/** The first material, the role's main one (what a single-block decision such as a door uses). */
	public Material main() {
		return this.entries.get(0).material();
	}

	/** Whether any material of the role cuts this form. */
	public boolean has(Form form) {
		for (Entry e : this.entries) {
			if (e.material().has(form)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The block for a position.
	 *
	 * @param seed   per-building, per-role seed
	 * @param low    0–1, how close to the ground this is
	 * @param high   0–1, how close to the eaves
	 * @param north  0–1, whether it faces north
	 * @param corner 0–1, whether it is on a corner
	 * @return the block, or null if no material of this role has the form
	 */
	public @Nullable Block pick(Form form, long seed, int x, int y, int z, double low, double high, double north, double corner) {
		return pick(form, seed, x, y, z, low, high, north, corner, 0.6);
	}

	/** {@link #pick} with the share of patch noise (0 = block by block, 1 = in patches). */
	public @Nullable Block pick(Form form, long seed, int x, int y, int z, double low, double high, double north, double corner, double patchiness) {
		if (this.entries.size() == 1) {
			return this.entries.get(0).material().block(form);
		}
		Block best = null;
		Block fallback = null;
		double bestScore = -1;
		for (int i = 0; i < this.entries.size(); i++) {
			Entry e = this.entries.get(i);
			Block b = e.material().block(form);
			if (b == null) {
				continue;
			}
			double w = e.weight() * (1 + e.low() * low + e.high() * high + e.north() * north + e.corner() * corner);
			if (w <= 0) {
				if (fallback == null) {
					fallback = b;
				}
				continue;
			}
			long s = seed + i * 0x5851F42D4C957F2DL;
			double patch = Noise.value(s, x / 2.3, y / 1.7, z / 2.3);
			double grain = Noise.unit(s ^ 0x2545F4914F6CDD1DL, x, y, z);
			// weight through the exponent: a 3:1 palette gives roughly 3:1 counts, clustered
			double u = uniform(patchiness * patch + (1 - patchiness) * grain, patchiness);
			double score = Math.pow(Math.max(u, 1e-9), 1.0 / w);
			if (score > bestScore) {
				bestScore = score;
				best = b;
			}
		}
		return best != null ? best : fallback;
	}

	/**
	 * Maps a blend {@code p·A + (1-p)·B} of two roughly uniform noises back to a roughly uniform value (the blend's own
	 * CDF), so the weighted race gives each material its share whatever the patchiness.
	 */
	static double uniform(double t, double p) {
		double a = Math.max(p, 1 - p);
		double b = Math.min(p, 1 - p);
		if (b < 1e-6) {
			return t;
		}
		if (t < b) {
			return t * t / (2 * a * b);
		}
		if (t <= a) {
			return (t - b / 2) / a;
		}
		return 1 - (1 - t) * (1 - t) / (2 * a * b);
	}

	/**
	 * One material in a palette.
	 *
	 * @param weight relative share where no bias applies
	 * @param low    extra weight near the ground (multiplied by the lowness, 0–1)
	 * @param high   extra weight toward the eaves
	 * @param north  extra weight on north faces
	 * @param corner extra weight on corners
	 */
	public record Entry(Material material, double weight, double low, double high, double north, double corner) {
	}
}
