package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Level beds stepping down a slope, the way farmers work a hillside instead of fighting it: the plot is cut into bands
 * running along the contour, each band cut and filled to one level, the rise between two bands held by a grassy bank, a
 * dry-stone wall or an earth bund, and a way up the side with steps notched into each wall. Fields, paddies and terrace
 * fields plant the bands; on level ground there is one band and nothing to hold.
 *
 * <p>Bands stack along the fall line: along z (front to back) when the land rises or falls away from the street, along
 * x when it falls across the plot. {@link #along}/{@link #across} turn a cell into band coordinates.
 */
public final class Terraces {
	/** How the rise between two bands is held. */
	public enum Edge {
		/** A grassy bank: a ledge halfway for a rise of two, a stone face beyond. */
		BANK,
		/** A dry-stone retaining wall for every rise, with a coping course. */
		DRYSTONE,
		/** Stone faces with a raised earth bund along the top (paddies). */
		BUND
	}

	/**
	 * One level bed.
	 *
	 * @param from  first coordinate along the fall axis (inclusive)
	 * @param to    last coordinate along the fall axis (inclusive)
	 * @param level standing level on the bed
	 */
	public record Band(int from, int to, int level) {
		public int width() {
			return this.to - this.from + 1;
		}
	}

	public final int x0;
	public final int z0;
	public final int x1;
	public final int z1;
	/** Bands stack along x (the land falls across the plot) rather than along z. */
	public final boolean alongX;
	public final List<Band> bands;
	/** The across-coordinate of the way up between the bands (steps through every wall), or MIN_VALUE for none. */
	private int access = Integer.MIN_VALUE;

	private Terraces(int x0, int z0, int x1, int z1, boolean alongX, List<Band> bands) {
		this.x0 = x0;
		this.z0 = z0;
		this.x1 = x1;
		this.z1 = z1;
		this.alongX = alongX;
		this.bands = Collections.unmodifiableList(bands);
	}

	// ------------------------------------------------------------------------------------------------ planning

	/**
	 * Plans bands over a rectangle. A band is never narrower than {@code minBand} nor wider than {@code maxBand} (along
	 * the fall), a band's own ground varies by at most a block or two before a new one starts, and no rise between two
	 * bands is more than {@code maxRise} (deeper cuts are taken instead). A plot standing on a terrace the planner built
	 * ({@code terraced}) is one band at the floor.
	 */
	public static Terraces plan(Draft d, int x0, int z0, int x1, int z1, int minBand, int maxBand, int maxRise) {
		return plan(d, x0, z0, x1, z1, minBand, maxBand, maxRise, Integer.MIN_VALUE, Integer.MIN_VALUE);
	}

	/**
	 * {@link #plan}, with the band a gate opens into held at the street's level: for bands stacked along z the front
	 * band meets the street at column {@code gateXForZ}, for bands stacked along x the band holding column
	 * {@code gateXForX} does (MIN_VALUE for no anchor). The other bands step from it.
	 */
	public static Terraces plan(Draft d, int x0, int z0, int x1, int z1, int minBand, int maxBand, int maxRise, int gateXForZ, int gateXForX) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		if (d.param("terraced", false)) {
			return new Terraces(ax, az, bx, bz, false, new ArrayList<>(List.of(new Band(az, bz, 0))));
		}
		// the plane through the ground: which way it falls
		double[] g = gradient(d, ax, az, bx, bz);
		boolean alongX = Math.abs(g[0]) > Math.abs(g[1]) * 1.15 && bx - ax + 1 >= minBand * 2;
		int a0 = alongX ? ax : az;
		int a1 = alongX ? bx : bz;
		int c0 = alongX ? az : ax;
		int c1 = alongX ? bz : bx;
		int n = a1 - a0 + 1;
		double[] line = new double[n];
		for (int i = 0; i < n; i++) {
			double sum = 0;
			for (int c = c0; c <= c1; c++) {
				sum += alongX ? effective(d, a0 + i, c) : effective(d, c, a0 + i);
			}
			line[i] = sum / (c1 - c0 + 1);
		}
		// greedy: a band grows while its lines stay within a block and a half of each other
		List<int[]> spans = new ArrayList<>();
		int s = 0;
		while (s < n) {
			int e = s;
			double lo = line[s];
			double hi = line[s];
			while (e + 1 < n && e + 1 - s + 1 <= maxBand) {
				double v = line[e + 1];
				double nlo = Math.min(lo, v);
				double nhi = Math.max(hi, v);
				if (nhi - nlo > 1.5 && e - s + 1 >= minBand) {
					break;
				}
				lo = nlo;
				hi = nhi;
				e++;
			}
			spans.add(new int[]{s, e});
			s = e + 1;
		}
		// a last band too narrow joins the one before
		if (spans.size() > 1) {
			int[] last = spans.get(spans.size() - 1);
			if (last[1] - last[0] + 1 < minBand) {
				spans.remove(spans.size() - 1);
				spans.get(spans.size() - 1)[1] = last[1];
			}
		}
		List<Band> bands = new ArrayList<>();
		int[] levels = new int[spans.size()];
		for (int k = 0; k < spans.size(); k++) {
			int[] sp = spans.get(k);
			double sum = 0;
			for (int i = sp[0]; i <= sp[1]; i++) {
				sum += line[i];
			}
			levels[k] = (int) Math.round(sum / (sp[1] - sp[0] + 1));
		}
		// the band a gate opens into meets the street; the others step from it, no rise beyond maxRise
		int anchor = -1;
		int gateX = alongX ? gateXForX : gateXForZ;
		if (gateX != Integer.MIN_VALUE && levels.length > 0) {
			if (!alongX) {
				anchor = 0;
			} else {
				for (int k = 0; k < spans.size(); k++) {
					if (gateX - a0 >= spans.get(k)[0] && gateX - a0 <= spans.get(k)[1]) {
						anchor = k;
					}
				}
			}
			if (anchor >= 0) {
				levels[anchor] = street(d, gateX);
			}
		}
		if (anchor < 0) {
			for (int pass = 0; pass < 2; pass++) {
				for (int k = 1; k < levels.length; k++) {
					levels[k] = Math.clamp(levels[k], levels[k - 1] - maxRise, levels[k - 1] + maxRise);
				}
				for (int k = levels.length - 2; k >= 0; k--) {
					levels[k] = Math.clamp(levels[k], levels[k + 1] - maxRise, levels[k + 1] + maxRise);
				}
			}
		} else {
			for (int k = anchor + 1; k < levels.length; k++) {
				levels[k] = Math.clamp(levels[k], levels[k - 1] - maxRise, levels[k - 1] + maxRise);
			}
			for (int k = anchor - 1; k >= 0; k--) {
				levels[k] = Math.clamp(levels[k], levels[k + 1] - maxRise, levels[k + 1] + maxRise);
			}
		}
		for (int k = 0; k < spans.size(); k++) {
			bands.add(new Band(a0 + spans.get(k)[0], a0 + spans.get(k)[1], levels[k]));
		}
		return new Terraces(ax, az, bx, bz, alongX, bands);
	}

	/** The ground a bed is planned on: the natural ground, or the water's surface where it stands over it. */
	static int effective(Draft d, int x, int z) {
		int w = d.site().water(x, z);
		return w == dev.voidmc.hearth.build.terrain.Site.NONE ? d.site().ground(x, z) : Math.max(d.site().ground(x, z), w);
	}

	/** The street's standing level in front of column x (its water's surface where it is wet). */
	public static int street(Draft d, int x) {
		return effective(d, x, -1);
	}

	/** Least-squares slope of the ground over a rectangle: {dh/dx, dh/dz}. */
	public static double[] gradient(Draft d, int x0, int z0, int x1, int z1) {
		double mx = (x0 + x1) / 2.0;
		double mz = (z0 + z1) / 2.0;
		double sxx = 0;
		double szz = 0;
		double sxh = 0;
		double szh = 0;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				double h = effective(d, x, z);
				sxx += (x - mx) * (x - mx);
				szz += (z - mz) * (z - mz);
				sxh += (x - mx) * h;
				szh += (z - mz) * h;
			}
		}
		return new double[]{sxx == 0 ? 0 : sxh / sxx, szz == 0 ? 0 : szh / szz};
	}

	// ------------------------------------------------------------------------------------------------ reading

	public int along(int x, int z) {
		return this.alongX ? x : z;
	}

	public int across(int x, int z) {
		return this.alongX ? z : x;
	}

	/** The cell at band coordinates (along, across) as {x, z}. */
	public int[] cell(int along, int across) {
		return this.alongX ? new int[]{along, across} : new int[]{across, along};
	}

	public int acrossMin() {
		return this.alongX ? this.z0 : this.x0;
	}

	public int acrossMax() {
		return this.alongX ? this.z1 : this.x1;
	}

	public boolean inside(int x, int z) {
		return x >= this.x0 && x <= this.x1 && z >= this.z0 && z <= this.z1;
	}

	/** The band holding a cell, or null outside. */
	public Band band(int x, int z) {
		if (!inside(x, z)) {
			return null;
		}
		int a = along(x, z);
		for (Band b : this.bands) {
			if (a >= b.from && a <= b.to) {
				return b;
			}
		}
		return null;
	}

	/** The standing level at a cell (a step's level on the way up), or MIN_VALUE outside. */
	public int level(int x, int z) {
		Band b = band(x, z);
		return b == null ? Integer.MIN_VALUE : b.level;
	}

	/** Whether the rise from band i to i + 1 is held by a wall (its row on the high side is stone to the top). */
	public boolean walled(int i) {
		int r = rise(i);
		return r != 0 && (this.edge != Edge.BANK || Math.abs(r) >= 3);
	}

	/** Whether the rise from band i to i + 1 takes a row of its high band (a wall, a bund or a ledge). */
	private boolean takesRow(int i) {
		int r = rise(i);
		return walled(i) || this.edge == Edge.BANK && Math.abs(r) == 2;
	}

	/** The first row along the fall that a band's planting may use: its own, less a row held by a wall, bund or ledge. */
	public int plantFrom(Band b) {
		int i = this.bands.indexOf(b);
		if (i > 0 && rise(i - 1) > 0 && takesRow(i - 1)) {
			return b.from + 1;
		}
		return b.from;
	}

	/** The last row along the fall that a band's planting may use. */
	public int plantTo(Band b) {
		int i = this.bands.indexOf(b);
		if (i < this.bands.size() - 1 && rise(i) < 0 && takesRow(i)) {
			return b.to - 1;
		}
		return b.to;
	}

	/** The rise from band i to band i + 1 (negative where the land falls toward the back). */
	public int rise(int i) {
		return this.bands.get(i + 1).level - this.bands.get(i).level;
	}

	public int access() {
		return this.access;
	}

	/** The lowest and highest band levels. */
	public int minLevel() {
		int m = Integer.MAX_VALUE;
		for (Band b : this.bands) {
			m = Math.min(m, b.level);
		}
		return m;
	}

	public int maxLevel() {
		int m = Integer.MIN_VALUE;
		for (Band b : this.bands) {
			m = Math.max(m, b.level);
		}
		return m;
	}

	// ------------------------------------------------------------------------------------------------ shaping

	private Edge edge = Edge.BANK;

	/**
	 * Cuts and fills every band to its level (the surface block left to the planting, grass where nothing else is laid),
	 * holds each rise with the given edge, and holds the plot's own edges where a band stands more than a block above
	 * the land outside. {@code accessAcross} is the across-coordinate of the way up through the walls (steps notched
	 * into each), or MIN_VALUE for none.
	 */
	public void shape(Draft d, Edge edge, int accessAcross) {
		this.edge = edge;
		this.access = accessAcross;
		Mat fill = d.mat(Role.FILL);
		Mat ground = d.mat(Role.GROUND);
		Mat stone = d.mat(Role.FOUNDATION);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				int lv = surfaceLevel(x, z);
				int g = d.site().ground(x, z);
				if (d.site().isWater(x, z)) {
					// a bed laid over a pond or a channel: made up from the bed, walled at its edge
					try (var s = d.stage(StageKind.EARTHWORK)) {
						for (int y = g - 1; y < lv - 1; y++) {
							d.set(x, y, z, fill.full());
						}
						d.set(x, lv - 1, z, ground.full());
						int wy = d.site().water(x, z);
						for (int y = lv; y < wy; y++) {
							d.set(x, y, z, stone.full());
						}
						d.drain(x, z);
					}
					continue;
				}
				try (var s = d.stage(StageKind.EARTHWORK)) {
					if (g > lv) {
						for (int y = lv; y < g; y++) {
							d.set(x, y, z, air);
						}
						d.set(x, lv - 1, z, ground.full());
					} else if (g < lv) {
						for (int y = g - 1; y < lv - 1; y++) {
							d.set(x, y, z, fill.full());
						}
						d.set(x, lv - 1, z, ground.full());
					}
				}
			}
		}
		// rises between bands
		for (int i = 0; i + 1 < this.bands.size(); i++) {
			int r = rise(i);
			if (r == 0) {
				continue;
			}
			Band lowB = r > 0 ? this.bands.get(i) : this.bands.get(i + 1);
			Band highB = r > 0 ? this.bands.get(i + 1) : this.bands.get(i);
			// the high band's row against the low band holds the face
			int row = r > 0 ? highB.from : highB.to;
			int lowLv = lowB.level;
			int highLv = highB.level;
			for (int c = acrossMin(); c <= acrossMax(); c++) {
				int[] xz = cell(row, c);
				boolean wall = edge != Edge.BANK || Math.abs(r) >= 3;
				if (wall) {
					retain(d, stone, xz[0], xz[1], lowLv - 1, highLv - 1, c);
				}
			}
		}
		// the plot's edges where a band stands above the land beyond
		for (int z = this.z0; z <= this.z1; z++) {
			for (int x = this.x0; x <= this.x1; x++) {
				boolean edgeCell = x == this.x0 || x == this.x1 || z == this.z0 || z == this.z1;
				if (!edgeCell) {
					continue;
				}
				int lv = surfaceLevel(x, z);
				int out = Integer.MAX_VALUE;
				if (x == this.x0) {
					out = Math.min(out, d.site().ground(x - 1, z));
				}
				if (x == this.x1) {
					out = Math.min(out, d.site().ground(x + 1, z));
				}
				if (z == this.z0) {
					out = Math.min(out, d.site().ground(x, z - 1));
				}
				if (z == this.z1) {
					out = Math.min(out, d.site().ground(x, z + 1));
				}
				if (lv - out >= 2 && !d.site().isWater(x, z)) {
					try (var s = d.stage(StageKind.FOUNDATION)) {
						for (int y = Math.min(out, d.site().ground(x, z)) - 1; y <= lv - 1; y++) {
							d.set(x, y, z, stone.full());
						}
					}
				}
			}
		}
		if (accessAcross != Integer.MIN_VALUE) {
			steps(d, accessAcross);
		}
	}

	/** The surface level a cell is shaped to: its band's, or a ledge halfway up a two-block bank. */
	private int surfaceLevel(int x, int z) {
		Band b = band(x, z);
		int i = this.bands.indexOf(b);
		int a = along(x, z);
		if (this.edge == Edge.BANK) {
			if (i > 0 && a == b.from && rise(i - 1) == 2) {
				return b.level - 1;
			}
			if (i < this.bands.size() - 1 && a == b.to && rise(i) == -2) {
				return b.level - 1;
			}
		}
		return b.level;
	}

	/** A retaining face: stone from {@code yLow} up to {@code yTop} in one column, a coping course on top. */
	private void retain(Draft d, Mat stone, int x, int z, int yLow, int yTop, int across) {
		int g = d.site().ground(x, z);
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int y = Math.min(yLow, g - 1); y <= yTop; y++) {
				d.set(x, y, z, stone.full());
			}
			if (this.edge == Edge.BUND) {
				// the bund's earth on top of the wall, a step above the bed
				d.set(x, yTop + 1, z, Noise.unit(d.seed() ^ 0xB0D, x, 0, z) < 0.7 ? d.mat(Role.GROUND).full() : d.mat(Role.PATH).full());
			}
		}
	}

	/**
	 * The way up: along the fall line at one across-coordinate, a path on each band and a flight of steps notched into
	 * every rise (cheek walls of stone either side where the band stands proud).
	 */
	private void steps(Draft d, int c) {
		Mat stone = d.mat(Role.FOUNDATION);
		Mat paving = d.mat(Role.PAVING);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int i = 0; i + 1 < this.bands.size(); i++) {
			int r = rise(i);
			if (r == 0) {
				continue;
			}
			Band lowB = r > 0 ? this.bands.get(i) : this.bands.get(i + 1);
			Band highB = r > 0 ? this.bands.get(i + 1) : this.bands.get(i);
			int n = Math.abs(r);
			// climbing from the low band into the high one; dir points uphill
			int dirSign = r > 0 ? 1 : -1;
			Direction up = this.alongX ? (dirSign > 0 ? Dirs.RIGHT : Dirs.LEFT) : (dirSign > 0 ? Dirs.BACK : Dirs.FRONT);
			int start = r > 0 ? highB.from : highB.to;
			// a two-block bank has its ledge: the flight still starts at the wall row
			for (int k = 0; k < n; k++) {
				int a = start + dirSign * k;
				if (!(a >= highB.from && a <= highB.to)) {
					break;
				}
				int[] xz = cell(a, c);
				int y = lowB.level + k;
				try (var st = d.stage(StageKind.EXTERIOR)) {
					d.set(xz[0], y, xz[1], paving.has(Form.STAIRS) ? paving.stairs(up, Half.BOTTOM) : stone.stairsOrSlab(up, Half.BOTTOM));
					for (int yy = y + 1; yy <= highB.level + 2; yy++) {
						d.set(xz[0], yy, xz[1], air);
					}
				}
				try (var st = d.stage(StageKind.FOUNDATION)) {
					for (int yy = Math.min(d.site().ground(xz[0], xz[1]) - 1, y - 1); yy < y; yy++) {
						d.set(xz[0], yy, xz[1], stone.full());
					}
				}
				// cheeks: the band's earth either side of the notch held back by stone
				for (int side : new int[]{-1, 1}) {
					int cc = c + side;
					if (cc < acrossMin() || cc > acrossMax()) {
						continue;
					}
					int[] ch = cell(a, cc);
					int top = highB.level - 1;
					if (top > y) {
						try (var st = d.stage(StageKind.FOUNDATION)) {
							for (int yy = y; yy <= top; yy++) {
								d.set(ch[0], yy, ch[1], stone.full());
							}
						}
					}
				}
			}
		}
	}

	/** A path along the way up on every band (dirt path or the style's path), skipping the steps. */
	public void accessPath(Draft d) {
		if (this.access == Integer.MIN_VALUE) {
			return;
		}
		Mat path = d.mat(Role.PATH);
		for (Band b : this.bands) {
			for (int a = b.from; a <= b.to; a++) {
				int[] xz = cell(a, this.access);
				BlockState here = d.get(xz[0], b.level, xz[1]);
				if (here != null && !here.isAir()) {
					continue;
				}
				BlockState below = d.get(xz[0], b.level - 1, xz[1]);
				if (below != null && (below.getBlock() instanceof net.minecraft.world.level.block.StairBlock)) {
					continue;
				}
				int lv = surfaceLevel(xz[0], xz[1]);
				if (lv != b.level) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(xz[0], b.level - 1, xz[1], path.full());
					d.set(xz[0], b.level, xz[1], air());
				}
			}
		}
	}

	private static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	/** A coping slab along the top of every dry-stone retaining wall, where nothing else stands. */
	public void coping(Draft d) {
		if (this.edge != Edge.DRYSTONE) {
			return;
		}
		Mat stone = d.mat(Role.FOUNDATION);
		for (int i = 0; i + 1 < this.bands.size(); i++) {
			int r = rise(i);
			if (r == 0) {
				continue;
			}
			Band highB = r > 0 ? this.bands.get(i + 1) : this.bands.get(i);
			int row = r > 0 ? highB.from : highB.to;
			for (int c = acrossMin(); c <= acrossMax(); c++) {
				if (c == this.access) {
					continue;
				}
				int[] xz = cell(row, c);
				if (d.isAbsent(xz[0], highB.level, xz[1]) && Noise.unit(d.seed() ^ 0xC0B, xz[0], 0, xz[1]) < 0.35 && stone.has(Form.SLAB)) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						d.set(xz[0], highB.level, xz[1], stone.slab(SlabType.BOTTOM));
					}
				}
			}
		}
	}
}
