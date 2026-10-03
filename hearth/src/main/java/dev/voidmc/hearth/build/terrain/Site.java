package dev.voidmc.hearth.build.terrain;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.blueprint.LocalFrame;

/**
 * The natural ground under a plot, read once from the {@link TerrainProbe} into the plot's local frame. Heights are
 * relative to the spec's floor level: {@code ground(x, z) == 0} means the natural ground's surface is exactly where the
 * floor is (a person standing on the ground stands at the floor level); negative is below the floor (the building needs
 * footings or stilts there), positive above it (it is cut into the hill). The ground block itself is at
 * {@code ground - 1}.
 *
 * <p>Columns are sampled for the footprint and a margin of {@value #MARGIN} round it (read-only: a blueprint never
 * places outside its footprint, but edges look at the land beyond them).
 */
public final class Site {
	/** Columns sampled outside the footprint on each side. */
	public static final int MARGIN = 4;
	/** {@link #water} where there is no water. */
	public static final int NONE = Integer.MIN_VALUE;

	private final LocalFrame frame;
	private final int frontage;
	private final int depth;
	private final int floorY;
	private final int w;
	private final int[] ground;
	private final int[] water;
	private final float temperature;
	private final int seaLevel;

	public Site(PlotSpec spec, TerrainProbe probe) {
		this(new LocalFrame(spec), spec.floorY(), probe);
		// a building on a terrace plot (param "terraced", PLANNING.md §8) stands on the terrace's level deck, which covers
		// its whole footprint at its floor level: the ground there is made, not natural (outside it, the land is natural)
		if (spec.params().getBooleanOr("terraced", false)) {
			for (int z = 0; z < this.depth; z++) {
				for (int x = 0; x < this.frontage; x++) {
					this.ground[index(x, z)] = 0;
					this.water[index(x, z)] = NONE;
				}
			}
		}
	}

	public Site(LocalFrame frame, int floorY, TerrainProbe probe) {
		this.frame = frame;
		this.frontage = frame.frontage();
		this.depth = frame.depth();
		this.floorY = floorY;
		this.w = this.frontage + 2 * MARGIN;
		int h = this.depth + 2 * MARGIN;
		this.ground = new int[this.w * h];
		this.water = new int[this.w * h];
		for (int z = -MARGIN; z < this.depth + MARGIN; z++) {
			for (int x = -MARGIN; x < this.frontage + MARGIN; x++) {
				int wx = frame.worldX(x, z);
				int wz = frame.worldZ(x, z);
				int i = index(x, z);
				this.ground[i] = probe.groundY(wx, wz) - floorY;
				int wy = probe.waterY(wx, wz);
				this.water[i] = wy == TerrainProbe.NO_WATER ? NONE : wy - floorY;
			}
		}
		int cx = frame.worldX(this.frontage / 2, this.depth / 2);
		int cz = frame.worldZ(this.frontage / 2, this.depth / 2);
		this.temperature = probe.temperature(cx, cz);
		this.seaLevel = probe.seaLevel() - floorY;
	}

	private int index(int x, int z) {
		int cx = Math.clamp(x, -MARGIN, this.frontage + MARGIN - 1) + MARGIN;
		int cz = Math.clamp(z, -MARGIN, this.depth + MARGIN - 1) + MARGIN;
		return cz * this.w + cx;
	}

	public LocalFrame frame() {
		return this.frame;
	}

	public int frontage() {
		return this.frontage;
	}

	public int depth() {
		return this.depth;
	}

	/** The world y of the floor level (the spec's origin y). */
	public int floorY() {
		return this.floorY;
	}

	/** Natural standing level relative to the floor (clamped to the sampled area). */
	public int ground(int x, int z) {
		return this.ground[index(x, z)];
	}

	/** The relative y of the water surface's top (first air above the water) or {@link #NONE}. */
	public int water(int x, int z) {
		return this.water[index(x, z)];
	}

	public boolean isWater(int x, int z) {
		return water(x, z) != NONE;
	}

	/** How deep the water stands over the natural ground (0 where dry). */
	public int waterDepth(int x, int z) {
		int wy = water(x, z);
		return wy == NONE ? 0 : Math.max(0, wy - ground(x, z));
	}

	/** Lowest natural ground in a local rectangle (inclusive). */
	public int minGround(int x0, int z0, int x1, int z1) {
		int m = Integer.MAX_VALUE;
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				m = Math.min(m, ground(x, z));
			}
		}
		return m;
	}

	/** Highest natural ground in a local rectangle (inclusive). */
	public int maxGround(int x0, int z0, int x1, int z1) {
		int m = Integer.MIN_VALUE;
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				m = Math.max(m, ground(x, z));
			}
		}
		return m;
	}

	/** Whether any column of the rectangle is under water. */
	public boolean anyWater(int x0, int z0, int x1, int z1) {
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				if (isWater(x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Mean natural ground over a rectangle, rounded. */
	public int meanGround(int x0, int z0, int x1, int z1) {
		long sum = 0;
		int n = 0;
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				sum += ground(x, z);
				n++;
			}
		}
		return n == 0 ? 0 : Math.round((float) sum / n);
	}

	/**
	 * The fall of the ground across the plot: how much higher the back is than the front (positive: the plot rises away
	 * from the street, so a hillside house opens a lower storey to the front).
	 */
	public int riseToBack() {
		int front = meanGround(0, 0, this.frontage - 1, Math.min(1, this.depth - 1));
		int back = meanGround(0, Math.max(0, this.depth - 2), this.frontage - 1, this.depth - 1);
		return back - front;
	}

	/** How much higher the right side is than the left. */
	public int riseToRight() {
		int left = meanGround(0, 0, Math.min(1, this.frontage - 1), this.depth - 1);
		int right = meanGround(Math.max(0, this.frontage - 2), 0, this.frontage - 1, this.depth - 1);
		return right - left;
	}

	public float temperature() {
		return this.temperature;
	}

	/** Cold enough to snow (vanilla's threshold). */
	public boolean snowy() {
		return this.temperature < 0.15F;
	}

	/** Sea level relative to the floor. */
	public int seaLevel() {
		return this.seaLevel;
	}
}
