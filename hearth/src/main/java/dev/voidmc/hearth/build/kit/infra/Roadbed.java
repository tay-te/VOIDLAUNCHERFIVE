package dev.voidmc.hearth.build.kit.infra;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Lays a road's bed and surface along a {@link Course}: every corridor column at the walking level the planner gave it,
 * cut and filled to it, surfaced in its {@link Dress}, with the rises smoothed (a lone rise becomes a half step of
 * slabs, a run of rises a flight of stairs) and headroom cleared above.
 *
 * <p>Rules that keep overlapping road plots composing into clean junctions (the sim's writer lets a later plot replace
 * only natural blocks and its own, so the first road built keeps its paving where two meet): everything this draws
 * at or above the walking level stands on the kerb line of a wide road, never on its side edge (that is every
 * fronting house's doorstep) and never within {@link #endZone} steps of either end (where lanes run into the road they
 * join); edges are flush, so a later lane crossing a kerb finds a threshold, not a wall.
 */
public final class Roadbed {
	public enum Shape {
		FULL, HALF, STAIR
	}

	private final Lay lay;
	private final Course c;
	private final Dress dress;
	private final List<Course.Col> cols;
	private final int[] level;
	private final Shape[] shape;
	private final Direction[] up;
	private final boolean[] inside;
	private final int endZone;
	private final long seed;

	/**
	 * @param endZone     steps at each end kept plain (no kerbs, kennel, lamps): where lanes overlap the road they join
	 * @param stairStreet every rise is a stair tread (stair streets); otherwise a lone rise is a half step
	 */
	public Roadbed(Lay lay, Course c, Dress dress, int endZone, boolean stairStreet) {
		this.lay = lay;
		this.c = c;
		this.dress = dress;
		this.cols = c.cols();
		this.endZone = endZone;
		int n = this.cols.size();
		this.level = new int[n];
		this.shape = new Shape[n];
		this.up = new Direction[n];
		this.inside = new boolean[n];
		this.seed = Noise.hash(lay.draft().style().id().toString().hashCode(), 0x40AD);
		var d = lay.draft();
		for (int i = 0; i < n; i++) {
			Course.Col col = this.cols.get(i);
			this.level[i] = c.level(col);
			int lx = lay.lx(col.x(), col.z());
			int lz = lay.lz(col.x(), col.z());
			this.inside[i] = lx >= 0 && lz >= 0 && lx < d.frontage() && lz < d.depth();
		}
		for (int i = 0; i < n; i++) {
			Course.Col col = this.cols.get(i);
			int l = this.level[i];
			Direction travel = c.cardinal(col.k());
			Direction lower = null;
			boolean higher = false;
			for (Direction dir : new Direction[]{travel.getOpposite(), travel, travel.getClockWise(), travel.getCounterClockWise()}) {
				int j = index(col.x() + dir.getStepX(), col.z() + dir.getStepZ());
				if (j < 0) {
					continue;
				}
				if (this.level[j] < l && lower == null) {
					lower = dir;
				}
				higher |= this.level[j] > l;
			}
			if (lower == null) {
				this.shape[i] = Shape.FULL;
			} else {
				this.up[i] = lower.getOpposite();
				this.shape[i] = stairStreet || higher ? Shape.STAIR : Shape.HALF;
			}
		}
	}

	public List<Course.Col> cols() {
		return this.cols;
	}

	public int level(int i) {
		return this.level[i];
	}

	public Shape shape(int i) {
		return this.shape[i];
	}

	/** The world direction a riser climbs toward (null on a level column). */
	public @Nullable Direction up(int i) {
		return this.up[i];
	}

	public boolean inside(int i) {
		return this.inside[i];
	}

	/** The index of the corridor column at (x, z) inside the plot, or -1. */
	public int index(int x, int z) {
		int i = this.c.indexOf(x, z);
		return i >= 0 && this.inside[i] ? i : -1;
	}

	/** Within the plain stretch at either end (or in an end cap). */
	public boolean inEnd(int i) {
		Course.Col col = this.cols.get(i);
		return col.beyond() || col.k() < this.endZone || col.k() > this.c.steps() - 1 - this.endZone;
	}

	/** On the dense centreline itself. */
	public boolean centre(int i) {
		Course.Col col = this.cols.get(i);
		return col.x() == this.c.dx(col.k()) && col.z() == this.c.dz(col.k());
	}

	/** The top of the surface in half blocks above the walking level's floor (2 = full, 1 = half step). */
	public double top(int i) {
		return this.shape[i] == Shape.FULL ? this.level[i] : this.level[i] - 0.5;
	}

	// ------------------------------------------------------------------------------------------------ earthwork

	/**
	 * Cut and fill to the walking level: earth fill under the surface, faced in the foundation stone where it shows above
	 * the land beside (urban roads, or any bank more than two blocks high), masonry from the bed where a column stands
	 * in water; ground above the walking level dug out.
	 */
	public void earthwork(boolean urban) {
		LineMat fill = LineMat.of(this.lay, Role.FILL);
		LineMat found = LineMat.of(this.lay, Role.FOUNDATION);
		for (int i = 0; i < this.cols.size(); i++) {
			if (!this.inside[i]) {
				continue;
			}
			Course.Col col = this.cols.get(i);
			int x = col.x();
			int z = col.z();
			int l = this.level[i];
			int g = this.lay.ground(x, z);
			int w = this.lay.water(x, z);
			int surfaceY = l - 1;
			if (w != Site.NONE) {
				try (var s = this.lay.draft().stage(StageKind.FOUNDATION)) {
					for (int y = g - 1; y < surfaceY; y++) {
						this.lay.set(x, y, z, found.full());
					}
				}
				continue;
			}
			if (g - 1 < surfaceY) {
				int exposure = surfaceY - lowestBeside(x, z) + 1;
				for (int y = g - 1; y < surfaceY; y++) {
					boolean shows = exposedAt(x, y, z);
					boolean stone = shows && (urban || exposure > 2);
					try (var s = this.lay.draft().stage(stone ? StageKind.FOUNDATION : StageKind.EARTHWORK)) {
						this.lay.set(x, y, z, stone ? found.full() : fill.full());
					}
				}
			} else if (g > l) {
				try (var s = this.lay.draft().stage(StageKind.EARTHWORK)) {
					for (int y = l; y < g; y++) {
						this.lay.air(x, y, z);
					}
				}
			}
		}
	}

	/** The lowest natural standing level among the 4-neighbours outside the corridor (or the column's own). */
	private int lowestBeside(int x, int z) {
		int m = Integer.MAX_VALUE;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int nx = x + dir.getStepX();
			int nz = z + dir.getStepZ();
			if (!this.c.contains(nx, nz)) {
				m = Math.min(m, this.lay.surface(nx, nz));
			}
		}
		return m == Integer.MAX_VALUE ? this.lay.ground(x, z) : m;
	}

	/** A block at y in the column would show beside: the land outside the corridor is lower there. */
	private boolean exposedAt(int x, int y, int z) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int nx = x + dir.getStepX();
			int nz = z + dir.getStepZ();
			if (!this.c.contains(nx, nz) && this.lay.surface(nx, nz) <= y) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------------ surface

	/** The material of a column's course. */
	public LineMat material(int i) {
		Course.Col col = this.cols.get(i);
		boolean end = inEnd(i);
		if (this.dress.ruts()) {
			return col.ring() == 0 ? this.dress.edge() : this.dress.main();
		}
		if (col.ring() == 0 && this.dress.edgeKind() != Dress.EdgeKind.SAME && !end) {
			return this.dress.edge();
		}
		LineMat wear = this.dress.wear();
		if (wear != null && this.dress.wearShare() > 0) {
			double nz = Noise.value(this.seed, col.x() / 3.1, 0, col.z() / 3.1) * 0.75 + Noise.unit(this.seed, col.x(), 1, col.z()) * 0.25;
			// wear gathers toward the middle, where wheels and feet go
			double bias = col.ring() == 0 ? -0.15 : 0.05;
			if (nz < this.dress.wearShare() + bias) {
				return wear;
			}
		}
		return this.dress.main();
	}

	/** Lays the surface course (stage FLOORS). */
	public void surface() {
		try (var s = this.lay.draft().stage(StageKind.FLOORS)) {
			for (int i = 0; i < this.cols.size(); i++) {
				if (!this.inside[i]) {
					continue;
				}
				Course.Col col = this.cols.get(i);
				int y = this.level[i] - 1;
				this.lay.set(col.x(), y, col.z(), paint(i));
			}
		}
	}

	/**
	 * Whether a column lies sunk half a block below the kerbs: inside them (or on the centre line for a kennel), on a
	 * level stretch (every neighbour at its level and level itself), away from the ends.
	 */
	public boolean sunk(int i) {
		int mode = this.dress.sunken();
		Course.Col col = this.cols.get(i);
		if (mode == 0 || col.ring() == 0 || this.shape[i] != Shape.FULL || inEnd(i) || mode == 1 && !centre(i)) {
			return false;
		}
		int l = this.level[i];
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int j = this.c.indexOf(col.x() + dx, col.z() + dz);
				if (j >= 0 && (this.level[j] != l || this.shape[j] != Shape.FULL)) {
					return false;
				}
			}
		}
		return true;
	}

	/** What the surface block of a column is. */
	public Paint paint(int i) {
		Course.Col col = this.cols.get(i);
		LineMat m = material(i);
		boolean log = m == this.dress.edge() && this.dress.edgeKind() == Dress.EdgeKind.LOG && col.ring() == 0;
		switch (this.shape[i]) {
			case STAIR -> {
				LineMat st = m.has(Form.STAIRS) && !log ? m : this.dress.step();
				return st.stairs(this.up[i], Half.BOTTOM);
			}
			case HALF -> {
				LineMat sl = m.has(Form.SLAB) && !log ? m : this.dress.step().has(Form.SLAB) ? this.dress.step() : null;
				if (sl != null) {
					return sl.slab(SlabType.BOTTOM);
				}
				return m.full();
			}
			default -> {
				if (sunk(i)) {
					LineMat sl = m.has(Form.SLAB) ? m : this.dress.step();
					return sl.slab(SlabType.BOTTOM);
				}
				if (log) {
					return m.log(this.c.cardinal(col.k()).getAxis());
				}
				return m.full();
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ clearing

	/** Air above the surface up to {@code headroom} over the walking level (stage CLEAR), where nothing is drawn. */
	public void clear(int headroom) {
		try (var s = this.lay.draft().stage(StageKind.CLEAR)) {
			for (int i = 0; i < this.cols.size(); i++) {
				if (!this.inside[i]) {
					continue;
				}
				Course.Col col = this.cols.get(i);
				int l = this.level[i];
				int from = Math.max(l, this.lay.surface(col.x(), col.z()));
				int to = Math.min(l + headroom, this.lay.topY());
				for (int y = from; y <= to; y++) {
					if (this.lay.absent(col.x(), y, col.z())) {
						this.lay.air(col.x(), y, col.z());
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ details

	/** The coordinate along the road's axis at a dense step (lamps and stones are spaced on the world grid). */
	private int along(int k) {
		Direction t = this.c.cardinal(k);
		return t.getAxis() == Direction.Axis.X ? this.c.dx(k) : this.c.dz(k);
	}

	/**
	 * The kerb-line column (ring 1) at dense step k on one side (+1 right of the travel direction, -1 left), level and
	 * outside the end zones, or -1.
	 */
	public int kerbLine(int k, int side) {
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < this.cols.size(); i++) {
			Course.Col col = this.cols.get(i);
			if (!this.inside[i] || col.ring() != 1 || Math.abs(col.k() - k) > 1 || Math.signum(col.off()) != side || inEnd(i) || this.shape[i] != Shape.FULL) {
				continue;
			}
			double dd = Math.abs(col.k() - k) * 4 + Math.abs(col.off());
			if (dd < bestD) {
				bestD = dd;
				best = i;
			}
		}
		return best;
	}

	/**
	 * Lamps on the kerb lines (alternating sides every {@code dress.spacing()} blocks along the world grid), drains in the
	 * gutters of stone streets, verges with tufts and flowers, milestones on highways, snow where it snows.
	 */
	public void details(Dress.Kind kind) {
		var d = this.lay.draft();
		boolean wide = this.c.width >= 5;
		// lamps
		if (this.dress.lamps() && wide && this.dress.spacing() > 0) {
			int sp = this.dress.spacing();
			for (int k = 0; k < this.c.steps(); k++) {
				int u = along(k);
				if (Math.floorMod(u, sp) != 0) {
					continue;
				}
				int side = Math.floorMod(Math.floorDiv(u, sp), 2) == 0 ? 1 : -1;
				int i = kerbLine(k, side);
				if (i < 0) {
					continue;
				}
				Course.Col col = this.cols.get(i);
				Direction arm = toward(col.x(), col.z(), this.c.dx(k), this.c.dz(k));
				if (sunk(i)) {
					try (var s = d.stage(StageKind.FLOORS)) {
						this.lay.set(col.x(), this.level[i] - 1, col.z(), this.dress.edge().edgeFull(this.dress.edgeKind()));
					}
				}
				Lamps.post(this.lay, col.x(), this.level[i], col.z(), arm);
			}
		}
		// drains: an iron grate in the gutter of a stone street, a sump under it
		if (kind == Dress.Kind.STREET && wide && d.tier() >= 2 && this.dress.edgeKind() == Dress.EdgeKind.KERB) {
			LineMat found = LineMat.of(this.lay, Role.FOUNDATION);
			for (int k = 0; k < this.c.steps(); k++) {
				int u = along(k);
				if (Math.floorMod(u, 12) != 6) {
					continue;
				}
				int side = Math.floorMod(Math.floorDiv(u, 12), 2) == 0 ? -1 : 1;
				int i = kerbLine(k, side);
				if (i < 0 || this.lay.wet(this.cols.get(i).x(), this.cols.get(i).z())) {
					continue;
				}
				Course.Col col = this.cols.get(i);
				int l = this.level[i];
				if (!this.lay.absent(col.x(), l, col.z()) && !this.lay.empty(col.x(), l, col.z())) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					// a grate a little below the setts, the dark of the sump showing through it
					this.lay.set(col.x(), l - 1, col.z(), States.trapdoor(Blocks.IRON_TRAPDOOR, Direction.NORTH, Half.BOTTOM, false));
					this.lay.set(col.x(), l - 2, col.z(), Lay.air());
					this.lay.set(col.x(), l - 3, col.z(), found.full());
				}
			}
		}
		// milestones along a highway
		if (kind == Dress.Kind.HIGHWAY && wide) {
			LineMat stone = LineMat.of(this.lay, Role.WALL_ACCENT);
			for (int k = 0; k < this.c.steps(); k++) {
				int u = along(k);
				if (Math.floorMod(u, 48) != 24) {
					continue;
				}
				int i = kerbLine(k, Math.floorMod(Math.floorDiv(u, 48), 2) == 0 ? -1 : 1);
				if (i < 0) {
					continue;
				}
				Course.Col col = this.cols.get(i);
				if (this.lay.absent(col.x(), this.level[i], col.z()) || this.lay.empty(col.x(), this.level[i], col.z())) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						this.lay.set(col.x(), this.level[i], col.z(), stone.full());
					}
				}
			}
		}
		// verges and the grass crown of a track: tufts and flowers that a later building or lane may simply replace
		boolean snowy = d.site().snowy();
		LineMat garden = LineMat.of(this.lay, Role.GARDEN);
		for (int i = 0; i < this.cols.size(); i++) {
			if (!this.inside[i] || this.shape[i] != Shape.FULL) {
				continue;
			}
			Course.Col col = this.cols.get(i);
			boolean verge = this.dress.edgeKind() == Dress.EdgeKind.VERGE && col.ring() == 0 && !inEnd(i);
			boolean crown = this.dress.ruts() && col.ring() > 0;
			boolean edge = col.ring() == 0;
			int l = this.level[i];
			if (this.lay.wet(col.x(), col.z()) || !this.lay.absent(col.x(), l, col.z())) {
				continue;
			}
			double r = Noise.unit(this.seed ^ 0x7E57, col.x(), l, col.z());
			try (var s = d.stage(StageKind.EXTERIOR)) {
				if (snowy && (verge || crown || edge && r < 0.35)) {
					if (r < 0.7) {
						this.lay.set(col.x(), l, col.z(), States.snow(1 + (r < 0.2 ? 1 : 0)));
					}
					continue;
				}
				if (verge && r < 0.45 || crown && r < 0.3) {
					BlockState plant = Blocks.SHORT_GRASS.defaultBlockState();
					if (r < 0.12) {
						Block b = garden.block(Form.FULL, col.x(), l, col.z());
						if (b instanceof net.minecraft.world.level.block.FlowerBlock) {
							plant = b.defaultBlockState();
						}
					} else if (r > 0.4 && verge) {
						plant = Blocks.FERN.defaultBlockState();
					}
					this.lay.set(col.x(), l, col.z(), plant);
				}
			}
		}
	}

	/** The world cardinal direction from one column toward another (the larger component). */
	public static Direction toward(int fx, int fz, int tx, int tz) {
		int dx = tx - fx;
		int dz = tz - fz;
		if (dx == 0 && dz == 0) {
			return Direction.NORTH;
		}
		return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}
}
