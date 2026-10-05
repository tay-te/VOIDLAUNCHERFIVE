package dev.voidmc.hearth.build.kit.extraction;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A spoil tip: the waste an excavation throws out (overburden, spalls, broken rock), heaped as a long mound along a
 * spine and growing part by part, so a working that has gone deep has a big tip beside it — the land itself shows how
 * long the pit has been worked. The mound stands at the angle of repose on the natural ground (it runs down a slope as a
 * fan), mixed in patches from the rock's spoil (gravel, rubble, a lump of earth) with half blocks on its crest and
 * flanks. Earth and gravel laid in the EARTHWORK stage cost no goods (the waste is moved, not made).
 */
public final class Spoil {
	private final Draft d;
	private final Rock rock;
	private final int w;
	private final int dp;
	/** The finished tip's height over the ground per column (0: none). */
	private final double[] height;
	/** How far along the spine (0 at its first end, 1 at its last) each column lies: when tipping reaches it. */
	private final double[] along;
	private final double top;

	/**
	 * @param ax..bz   the spine of the tip (its crest runs between these points)
	 * @param top      the finished tip's height at the crest (kept under the plot's height)
	 * @param slope    height lost per block out from the spine (0.7–1 for gravel)
	 * @param allowed  columns the tip may cover (w × dp, by z then x)
	 */
	/** The standing level each column was heaped on. */
	private final int[] base;

	public Spoil(Draft d, Rock rock, double ax, double az, double bx, double bz, double top, double slope, boolean[] allowed) {
		this.d = d;
		this.rock = rock;
		this.w = d.frontage();
		this.dp = d.depth();
		this.height = new double[this.w * this.dp];
		this.along = new double[this.w * this.dp];
		this.base = new int[this.w * this.dp];
		double best = 0;
		for (int z = 0; z < this.dp; z++) {
			for (int x = 0; x < this.w; x++) {
				if (!allowed[z * this.w + x] || d.site().isWater(x, z)) {
					continue;
				}
				double dist = segment(x, z, ax, az, bx, bz);
				double vx = bx - ax;
				double vz = bz - az;
				double len2 = vx * vx + vz * vz;
				this.along[z * this.w + x] = len2 == 0 ? 0 : Math.clamp(((x - ax) * vx + (z - az) * vz) / len2, 0, 1);
				// a little lumpiness so the flanks are not a perfect cone
				double lump = 0.6 * Noise.value(d.seed() ^ 0x71B, x / 2.5, 0, z / 2.5);
				double h = top - dist * slope + lump;
				// on the ground as the working left it (a levelled yard, a cut), else the land
				int g = dev.voidmc.hearth.build.terrain.Groundwork.surface(d, x, z);
				this.base[z * this.w + x] = g;
				h = Math.min(h, d.maxHeight() - 1 - g);
				if (h >= 0.45) {
					this.height[z * this.w + x] = h;
					best = Math.max(best, h);
				}
			}
		}
		this.top = best;
	}

	private static double segment(double x, double z, double ax, double az, double bx, double bz) {
		double vx = bx - ax;
		double vz = bz - az;
		double len2 = vx * vx + vz * vz;
		double t = len2 == 0 ? 0 : Math.clamp(((x - ax) * vx + (z - az) * vz) / len2, 0, 1);
		return Math.hypot(x - (ax + t * vx), z - (az + t * vz));
	}

	/** Whether the tip covers a column when finished. */
	public boolean covers(int x, int z) {
		return x >= 0 && z >= 0 && x < this.w && z < this.dp && this.height[z * this.w + x] >= 0.45;
	}

	/** The tip's height over the ground at a column when finished (0: none). */
	public int heightAt(int x, int z) {
		return covers(x, z) ? (int) Math.floor(this.height[z * this.w + x]) : 0;
	}

	/**
	 * The growth of the tip at step {@code k} of {@code n} (each step the waste of one bench or gallery), drawn in
	 * {@code part} after everything else in it: tipping runs out along the spine from its first end, so each step lays
	 * the next stretch of the bank whole — the tip's end advances, as it does where carts are tipped over its edge.
	 */
	public void grow(int k, int n, int part) {
		if (n <= 0 || k <= 0 || this.top <= 0) {
			return;
		}
		double from = (double) (k - 1) / n;
		double to = (double) k / n;
		try (var ps = this.d.campaign(part); var st = this.d.stage(StageKind.EARTHWORK); var o = this.d.order(Integer.MAX_VALUE - 8)) {
			for (int z = 0; z < this.dp; z++) {
				for (int x = 0; x < this.w; x++) {
					double hf = this.height[z * this.w + x];
					double t = this.along[z * this.w + x];
					if (hf < 0.45 || t <= from && k > 1 || t > to) {
						continue;
					}
					int g = this.base[z * this.w + x];
					int toY = (int) Math.round(hf);
					for (int y = 0; y < toY; y++) {
						Block b = this.rock.spoilAt(this.d.seed(), x, g + y, z);
						this.d.set(x, g + y, z, b.defaultBlockState());
					}
					// a half block on the crest and flanks where the mound is between whole blocks
					double frac = hf - Math.floor(hf);
					if (frac > 0.2 && frac < 0.5 && toY > 0 && g + toY < this.d.maxHeight() && Noise.unit(this.d.seed() ^ 0x5A8, x, toY, z) < 0.8) {
						this.d.set(x, g + toY, z, States.slab(this.rock.slab(), SlabType.BOTTOM));
					} else if (toY == 0 && Noise.unit(this.d.seed() ^ 0x5A9, x, 0, z) < 0.6) {
						this.d.set(x, g, z, States.slab(this.rock.slab(), SlabType.BOTTOM));
					}
				}
			}
		}
	}
}
