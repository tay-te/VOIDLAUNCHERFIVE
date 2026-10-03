package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.Ridge;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Roofs as height fields. A roof is a function from a column to the height of its surface; the rasteriser turns any
 * such field into stairs where the surface rises a block per column, slabs where it rises half a block, stairs on
 * stacked blocks where it is steep, and a ridge cap where nothing is higher, and the shape resolver then turns the stairs
 * at hips into outer corners and at valleys into inner corners. That one mechanism builds gables, hips, half-hips,
 * gambrels, mansards, saltboxes, cross-gables (the union of two gables), dormers (a small gable merged into a slope),
 * cones, domes and curved pagoda eaves with lifted corners.
 *
 * <p>Heights are in blocks above the roof's base y {@code y0} (the eave row's stairs stand at {@code y0}); a surface
 * height {@code h} is quantised to half blocks. Fields answer {@code NaN} where there is no roof.
 */
public final class Roofs {
	private Roofs() {
	}

	/** A roof surface: height in blocks above y0 at a column, NaN where there is no roof. */
	@FunctionalInterface
	public interface Field {
		double h(int x, int z);

		/** The higher of two roofs wherever either exists (cross-gables, dormers, wings). */
		default Field max(Field other) {
			Field self = this;
			return (x, z) -> {
				double a = self.h(x, z);
				double b = other.h(x, z);
				if (Double.isNaN(a)) {
					return b;
				}
				if (Double.isNaN(b)) {
					return a;
				}
				return Math.max(a, b);
			};
		}
	}

	/** Distance profile: how high the surface is at a distance (blocks) from the eave edge. */
	@FunctionalInterface
	public interface Profile {
		double at(double d);

		static Profile straight(double pitch) {
			return d -> 1 + pitch * d;
		}

		/** Steep low, shallow high (gambrel, mansard). */
		static Profile broken(double steep, double shallow, double knee) {
			return d -> 1 + steep * Math.min(d, knee) + shallow * Math.max(0, d - knee);
		}

		/** Shallow low, steepening high: a curved roof that sweeps out at the eaves (pagoda, karst, wisteria). */
		static Profile curved(double pitch) {
			return d -> 1 + (d <= 2 ? 0.5 * d : 1 + pitch * (d - 2));
		}
	}

	static boolean inside(int x, int z, int x0, int z0, int x1, int z1) {
		return x >= x0 && x <= x1 && z >= z0 && z <= z1;
	}

	/** A gable over an eave rectangle with the ridge along {@code axis}. */
	public static Field gable(int x0, int z0, int x1, int z1, Direction.Axis axis, Profile p) {
		return (x, z) -> {
			if (!inside(x, z, x0, z0, x1, z1)) {
				return Double.NaN;
			}
			double d = axis == Direction.Axis.X ? Math.min(z - z0, z1 - z) : Math.min(x - x0, x1 - x);
			return p.at(d);
		};
	}

	/** A hip roof (slopes on all four sides). */
	public static Field hip(int x0, int z0, int x1, int z1, Profile p) {
		return (x, z) -> {
			if (!inside(x, z, x0, z0, x1, z1)) {
				return Double.NaN;
			}
			return p.at(Math.min(Math.min(z - z0, z1 - z), Math.min(x - x0, x1 - x)));
		};
	}

	/** A half-hip (jerkinhead): a gable whose top {@code clip} rows are hipped back at the ends. */
	public static Field halfHip(int x0, int z0, int x1, int z1, Direction.Axis axis, Profile p, int keep) {
		Field g = gable(x0, z0, x1, z1, axis, p);
		return (x, z) -> {
			double a = g.h(x, z);
			if (Double.isNaN(a)) {
				return a;
			}
			double e = axis == Direction.Axis.X ? Math.min(x - x0, x1 - x) : Math.min(z - z0, z1 - z);
			return Math.min(a, p.at(e + keep));
		};
	}

	/** A single slope rising toward {@code up} (lean-to, shed). */
	public static Field shed(int x0, int z0, int x1, int z1, Direction up, Profile p) {
		return (x, z) -> {
			if (!inside(x, z, x0, z0, x1, z1)) {
				return Double.NaN;
			}
			double d = switch (up) {
				case NORTH -> z - z0;
				case SOUTH -> z1 - z;
				case EAST -> x - x0;
				default -> x1 - x;
			};
			return p.at(d);
		};
	}

	/** A saltbox: a gable whose two slopes have different pitches (the long slope toward {@code longSide}). */
	public static Field saltbox(int x0, int z0, int x1, int z1, Direction longSide, double pitchLong, double pitchShort) {
		Direction.Axis axis = longSide.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
		return (x, z) -> {
			if (!inside(x, z, x0, z0, x1, z1)) {
				return Double.NaN;
			}
			double dl;
			double ds;
			if (axis == Direction.Axis.X) {
				dl = longSide == Dirs.FRONT ? z - z0 : z1 - z;
				ds = longSide == Dirs.FRONT ? z1 - z : z - z0;
			} else {
				dl = longSide == Dirs.LEFT ? x - x0 : x1 - x;
				ds = longSide == Dirs.LEFT ? x1 - x : x - x0;
			}
			return Math.min(1 + pitchLong * dl, 1 + pitchShort * ds);
		};
	}

	/** A cone over a circle (round houses, towers). */
	public static Field cone(double cx, double cz, double radius, double pitch) {
		return (x, z) -> {
			double r = Math.hypot(x - cx, z - cz);
			if (r > radius + 0.5) {
				return Double.NaN;
			}
			return 1 + pitch * Math.max(0, radius + 0.5 - r - 0.5);
		};
	}

	/** A dome: a spherical cap of the given radius, {@code rise} times as high as it is wide. */
	public static Field dome(double cx, double cz, double radius, double rise) {
		return (x, z) -> {
			double r = Math.hypot(x - cx, z - cz);
			if (r > radius + 0.5) {
				return Double.NaN;
			}
			double t = Math.min(1, r / (radius + 0.5));
			return 1 + rise * (radius + 0.5) * Math.sqrt(1 - t * t);
		};
	}

	/** Lifts the four corners of a field by up to {@code lift} blocks within {@code reach} of each corner (flared eaves). */
	public static Field flared(Field f, int x0, int z0, int x1, int z1, double lift, double reach) {
		return (x, z) -> {
			double a = f.h(x, z);
			if (Double.isNaN(a)) {
				return a;
			}
			double dx = Math.min(x - x0, x1 - x);
			double dz = Math.min(z - z0, z1 - z);
			double near = Math.max(0, reach - Math.max(dx, dz)) / reach;
			double corner = (dx <= 0.01 && dz <= 0.01) ? 1 : near * near;
			return a + lift * corner * (Math.min(dx, dz) <= 0.01 ? 1 : 0.5);
		};
	}

	// ---------------------------------------------------------------------------------------------- rasterising

	/** A rasterised roof: where its surface blocks are, for chimneys, dormers and gable fills. */
	public static final class Roof {
		public final int y0;
		public final int x0;
		public final int z0;
		public final int x1;
		public final int z1;
		private final int[] t;
		private final int w;
		public int ridgeY = Integer.MIN_VALUE;

		Roof(int y0, int x0, int z0, int x1, int z1) {
			this.y0 = y0;
			this.x0 = x0;
			this.z0 = z0;
			this.x1 = x1;
			this.z1 = z1;
			this.w = x1 - x0 + 1;
			this.t = new int[this.w * (z1 - z0 + 1)];
			java.util.Arrays.fill(this.t, Integer.MIN_VALUE);
		}

		/** Surface height in half blocks above y0, or MIN_VALUE where there is no roof. */
		public int halfHeight(int x, int z) {
			if (!inside(x, z, this.x0, this.z0, this.x1, this.z1)) {
				return Integer.MIN_VALUE;
			}
			return this.t[(z - this.z0) * this.w + (x - this.x0)];
		}

		void set(int x, int z, int v) {
			this.t[(z - this.z0) * this.w + (x - this.x0)] = v;
		}

		public boolean covers(int x, int z) {
			return halfHeight(x, z) != Integer.MIN_VALUE;
		}

		/** The y of the roof's surface block at a column (the stair or slab), or MIN_VALUE. */
		public int surfaceY(int x, int z) {
			int v = halfHeight(x, z);
			return v == Integer.MIN_VALUE ? v : this.y0 + (v + 1) / 2 - 1;
		}

		/** The lowest y of the roof's blocks at a column (under any stacking on steep slopes); what a wall fills up to. */
		int[] bottoms;

		public int bottomY(int x, int z) {
			if (!covers(x, z) || this.bottoms == null) {
				return surfaceY(x, z);
			}
			return this.bottoms[(z - this.z0) * this.w + (x - this.x0)];
		}
	}

	/** Two rasterised roofs seen as one: at each column the higher surface (and the lower of their undersides). */
	public static Roof merged(Roof a, Roof b) {
		int y0 = Math.min(a.y0, b.y0);
		Roof m = new Roof(y0, Math.min(a.x0, b.x0), Math.min(a.z0, b.z0), Math.max(a.x1, b.x1), Math.max(a.z1, b.z1));
		m.bottoms = new int[m.t.length];
		for (int z = m.z0; z <= m.z1; z++) {
			for (int x = m.x0; x <= m.x1; x++) {
				int ta = a.halfHeight(x, z);
				int tb = b.halfHeight(x, z);
				int sa = ta == Integer.MIN_VALUE ? Integer.MIN_VALUE : a.surfaceY(x, z);
				int sb = tb == Integer.MIN_VALUE ? Integer.MIN_VALUE : b.surfaceY(x, z);
				if (sa == Integer.MIN_VALUE && sb == Integer.MIN_VALUE) {
					continue;
				}
				Roof hi = sa >= sb ? a : b;
				int surface = Math.max(sa, sb);
				// re-express the surface in the merged roof's half heights
				int t = hi.halfHeight(x, z) + 2 * (hi.y0 - y0);
				m.set(x, z, t);
				int ba = sa == Integer.MIN_VALUE ? Integer.MAX_VALUE : a.bottomY(x, z);
				int bb = sb == Integer.MIN_VALUE ? Integer.MAX_VALUE : b.bottomY(x, z);
				m.bottoms[(z - m.z0) * m.w + (x - m.x0)] = Math.min(ba, bb);
				m.ridgeY = Math.max(m.ridgeY, surface);
			}
		}
		return m;
	}

	/**
	 * Rasterises a field over a rectangle with the given materials. Stairs face up the slope; the ridge is capped by
	 * {@code ridge}. Returns the roof for further work.
	 */
	public static Roof raster(Draft d, Field f, int y0, int x0, int z0, int x1, int z1, Mat roof, Mat trim, Ridge ridge) {
		Roof r = new Roof(y0, x0, z0, x1, z1);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				double h = f.h(x, z);
				if (!Double.isNaN(h)) {
					r.set(x, z, Math.max(1, (int) Math.round(h * 2)));
				}
			}
		}
		r.bottoms = new int[r.t.length];
		int top = Integer.MIN_VALUE;
		boolean slabs = roof.has(Form.SLAB);
		boolean stairs = roof.has(Form.STAIRS);
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int t = r.halfHeight(x, z);
				if (t == Integer.MIN_VALUE) {
					continue;
				}
				int max = Integer.MIN_VALUE;
				int low = Integer.MAX_VALUE;
				int[] nv = new int[4];
				boolean[] edge = new boolean[4];
				for (int k = 0; k < 4; k++) {
					Direction dir = Dirs.HORIZONTAL[k];
					int n = r.halfHeight(x + Dirs.dx(dir), z + Dirs.dz(dir));
					edge[k] = n == Integer.MIN_VALUE;
					nv[k] = n == Integer.MIN_VALUE ? 0 : n;
					max = Math.max(max, nv[k]);
					low = Math.min(low, nv[k]);
				}
				int y;
				Paint p;
				int drop = t - low;
				Direction up = upslope(nv, t, max);
				if (up == null && low >= t) {
					// a flat top (mansard deck, plateau)
					y = y0 + (t + 1) / 2 - 1;
					p = (t & 1) == 0 ? roof.full() : roof.slab(SlabType.BOTTOM);
				} else if (up == null) {
					// the ridge: lower on opposite sides, or a peak
					y = y0 + (t + 1) / 2 - 1;
					p = switch (ridge) {
						case FULL, STAIRS -> roof.full();
						case TRIM, WALL -> (t & 1) == 0 ? (trim.has(Form.SLAB) ? trim.slab(SlabType.BOTTOM) : roof.slab(SlabType.BOTTOM)) : roof.slab(SlabType.TOP);
						default -> (t & 1) == 0 ? roof.slab(SlabType.BOTTOM) : roof.slab(SlabType.TOP);
					};
				} else if (drop <= 1 && slabs) {
					y = y0 + (t + 1) / 2 - 1;
					p = roof.slab((t & 1) == 0 ? SlabType.TOP : SlabType.BOTTOM);
				} else if ((t & 1) == 0 && stairs) {
					y = y0 + t / 2 - 1;
					p = roof.stairs(up, Half.BOTTOM);
				} else {
					y = y0 + (t + 1) / 2 - 1;
					p = (t & 1) == 0 || !slabs ? roof.full() : roof.slab(SlabType.BOTTOM);
				}
				d.set(x, y, z, p);
				// stack full blocks under steep surfaces so no gap shows on the downhill side (only toward a lower roof
				// column down the slope: never at an edge, where the roof simply ends)
				int bottom = y;
				int from = y;
				if (up != null) {
					int down = (java.util.Arrays.asList(Dirs.HORIZONTAL).indexOf(up) + 2) % 4;
					if (!edge[down]) {
						from = y0 + nv[down] / 2;
					}
				}
				for (int yy = Math.max(from, y0); yy < y; yy++) {
					d.set(x, yy, z, roof.full());
					bottom = Math.min(bottom, yy);
				}
				r.bottoms[(z - z0) * r.w + (x - x0)] = bottom;
				top = Math.max(top, y);
			}
		}
		r.ridgeY = top;
		return r;
	}

	/**
	 * Which way a column's stair faces (up the slope), or null for a ridge or a flat top. Neighbour heights are in
	 * {@link Dirs#HORIZONTAL} order (front, right, back, left). A higher neighbour is uphill; with none, a column lower on
	 * one side, or on two adjacent sides (a hip's corner), faces away from the lower side; lower on two opposite sides or
	 * more is the ridge.
	 */
	static Direction upslope(int[] nv, int t, int max) {
		if (max > t) {
			// toward the highest; on ties, the one whose opposite is lowest
			int best = -1;
			for (int k = 0; k < 4; k++) {
				if (nv[k] == max && (best < 0 || nv[(k + 2) % 4] < nv[(best + 2) % 4])) {
					best = k;
				}
			}
			return Dirs.HORIZONTAL[best];
		}
		boolean[] lower = new boolean[4];
		int count = 0;
		for (int k = 0; k < 4; k++) {
			lower[k] = nv[k] < t;
			if (lower[k]) {
				count++;
			}
		}
		if (count == 0 || count >= 3) {
			return null;
		}
		if (count == 2 && (lower[0] && lower[2] || lower[1] && lower[3])) {
			return null;
		}
		// face away from the lower side (front/back preferred at a corner)
		for (int k : new int[]{0, 2, 1, 3}) {
			if (lower[k]) {
				return Dirs.HORIZONTAL[(k + 2) % 4];
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------------- whole roofs

	/** What {@link #build} drew. */
	public record Built(Roof roof, RoofForm form, Direction.Axis ridgeAxis, int ex0, int ez0, int ex1, int ez1) {
	}

	/** Options for {@link #build}; {@link #of(Draft)} reads the style's habits. */
	public record Opts(double pitch, int eaves, int gableOverhang, Ridge ridge, boolean flare, boolean brackets) {
		public static Opts of(Draft d) {
			Habits.Roof h = d.habits().roof();
			return new Opts(h.pitch(), h.eaves(), h.gableOverhang(), h.ridge(), h.flare(), h.brackets());
		}

		public Opts withPitch(double p) {
			return new Opts(p, this.eaves, this.gableOverhang, this.ridge, this.flare, this.brackets);
		}

		public Opts withEaves(int e, int g) {
			return new Opts(this.pitch, e, g, this.ridge, this.flare, this.brackets);
		}
	}

	/**
	 * A roof over a rectangle of walls {@code [wx0..wx1] × [wz0..wz1]} whose top course is at {@code plateY}: eaves and gable
	 * overhangs from the options (none over party walls, never past the plot except one row into the street), the form's
	 * field, gable ends filled, brackets under the eaves. The ridge runs along {@code axis}.
	 */
	public static Built build(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, RoofForm form, Direction.Axis axis, Opts o, boolean partyLeft,
		boolean partyRight) {
		return plan(d, wx0, wz0, wx1, wz1, plateY, form, axis, o, partyLeft, partyRight).draw(d);
	}

	/** Plans a roof without drawing it, so cross gables and dormers can be merged into its field first. */
	public static Plan plan(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, RoofForm form, Direction.Axis axis, Opts o, boolean partyLeft,
		boolean partyRight) {
		return new Plan(d, wx0, wz0, wx1, wz1, plateY, form, axis, o, partyLeft, partyRight);
	}

	/**
	 * A roof being planned: its walls, eave rectangle, profile and field. Add cross gables and dormers with
	 * {@link #crossGable}/{@link #dormer} (or any field with {@link #add}), then {@link #draw} it once, so every part is
	 * one surface whose valleys and hips come out right.
	 */
	public static final class Plan {
		public final int wx0;
		public final int wz0;
		public final int wx1;
		public final int wz1;
		public final int plateY;
		public final RoofForm form;
		public final Direction.Axis axis;
		public final Opts opts;
		public int ex0;
		public int ez0;
		public int ex1;
		public int ez1;
		public final int y0;
		final Profile profile;
		final int deep;
		Field field;
		Field main;
		final java.util.List<int[]> dormers = new java.util.ArrayList<>();
		final java.util.List<Side> dormerSides = new java.util.ArrayList<>();
		final java.util.List<Side> gableSides = new java.util.ArrayList<>();
		final java.util.List<int[]> gableRuns = new java.util.ArrayList<>();

		Plan(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, RoofForm form, Direction.Axis axis, Opts o, boolean partyLeft, boolean partyRight) {
			this.wx0 = wx0;
			this.wz0 = wz0;
			this.wx1 = wx1;
			this.wz1 = wz1;
			this.plateY = plateY;
			this.form = form;
			this.axis = axis;
			this.opts = o;
			boolean allRound = form == RoofForm.HIP || form == RoofForm.PAGODA || form == RoofForm.MANSARD;
			int eaveX = allRound || axis == Direction.Axis.Z ? o.eaves : o.gableOverhang;
			int eaveZ = allRound || axis == Direction.Axis.X ? o.eaves : o.gableOverhang;
			// the field is laid out over the nominal eaves, so a side clipped by the plot keeps the same heights; only the
			// drawn area is clipped
			int nx0 = partyLeft ? wx0 : wx0 - eaveX;
			int nx1 = partyRight ? wx1 : wx1 + eaveX;
			int nz0 = wz0 - eaveZ;
			int nz1 = wz1 + eaveZ;
			this.ex0 = Math.max(0, nx0);
			this.ex1 = Math.min(d.frontage() - 1, nx1);
			this.ez0 = Math.max(-1, nz0);
			this.ez1 = Math.min(d.depth() - 1, nz1);
			this.deep = Math.min(Math.min(wx0 - this.ex0, this.ex1 - wx1), Math.min(wz0 - this.ez0, this.ez1 - wz1));
			int over = allRound ? Math.min(eaveX, eaveZ) : axis == Direction.Axis.X ? eaveZ : eaveX;
			this.profile = switch (form) {
				case GAMBREL, MANSARD -> Profile.broken(Math.max(1.5, o.pitch * 2), 0.5, over + 2);
				case PAGODA -> Profile.curved(Math.max(1, o.pitch));
				default -> Profile.straight(o.pitch);
			};
			Field f = switch (form) {
				case HIP, PAGODA, MANSARD -> hip(nx0, nz0, nx1, nz1, this.profile);
				case HALF_HIP -> halfHip(nx0, nz0, nx1, nz1, axis, this.profile,
					Math.max(1, ((axis == Direction.Axis.X ? nz1 - nz0 : nx1 - nx0) / 2) - 2));
				case SALTBOX -> saltbox(nx0, nz0, nx1, nz1, axis == Direction.Axis.X ? Dirs.BACK : Dirs.LEFT, o.pitch * 0.75, o.pitch);
				case SHED -> shed(nx0, nz0, nx1, nz1, axis == Direction.Axis.X ? Dirs.BACK : Dirs.RIGHT,
					Profile.straight(Math.max(0.5, o.pitch * 0.5)));
				default -> gable(nx0, nz0, nx1, nz1, axis, this.profile);
			};
			this.field = f;
			this.main = f;
			// the row over the wall line sits one above the plate
			double hWall = this.profile.at(Math.max(0, over));
			int tWall = Math.max(1, (int) Math.round(hWall * 2));
			this.y0 = plateY + 1 - ((tWall + 1) / 2 - 1);
		}

		/**
		 * How many columns inward from wall {@code side} (at cell {@code i}) the main roof keeps rising: the distance from
		 * the wall line to the main ridge.
		 */
		int toRidge(Side side, int i) {
			Direction in = side.out().getOpposite();
			int x = side.x(i);
			int z = side.z(i);
			double last = this.main.h(x, z);
			for (int k = 1; k < 64; k++) {
				double h = this.main.h(x + Dirs.dx(in) * k, z + Dirs.dz(in) * k);
				if (Double.isNaN(h) || h <= last) {
					return k - 1;
				}
				last = h;
			}
			return 0;
		}

		/** Field units for a surface whose stair stands at absolute y (the stair's top is a block above it). */
		public double h(int surfaceY) {
			return surfaceY + 1 - this.y0;
		}

		/** Merges another roof surface into this one (a wing, a porch), growing the drawn area. */
		public Plan add(Field extra, int x0, int z0, int x1, int z1) {
			this.field = this.field.max(extra);
			this.ex0 = Math.min(this.ex0, x0);
			this.ez0 = Math.min(this.ez0, z0);
			this.ex1 = Math.max(this.ex1, x1);
			this.ez1 = Math.max(this.ez1, z1);
			return this;
		}

		/**
		 * A cross gable over a run of the wall {@code side} from column {@code col}, {@code width} wide (the gable's walls
		 * included): a gable at right angles to the main ridge whose end stands on the wall line with the same eaves and
		 * pitch as the main roof, so its ridge meets the main ridge or dies into the slope. Its end wall is filled by
		 * {@link #draw}.
		 */
		public Plan crossGable(Side side, int col, int width, Draft d) {
			int i0 = side.index(col);
			int i1 = i0 + width - 1;
			Direction in = side.out().getOpposite();
			int over = this.opts.eaves;
			boolean alongX = side.axis() == Direction.Axis.X;
			int ax0 = Math.min(side.x(i0), side.x(i1)) - (alongX ? over : 0);
			int ax1 = Math.max(side.x(i0), side.x(i1)) + (alongX ? over : 0);
			int az0 = Math.min(side.z(i0), side.z(i1)) - (alongX ? 0 : over);
			int az1 = Math.max(side.z(i0), side.z(i1)) + (alongX ? 0 : over);
			int g = this.opts.gableOverhang;
			int reach = toRidge(side, i0 + (width - 1) / 2);
			int x0 = Math.min(ax0 + Dirs.dx(side.out()) * g, ax0 + Dirs.dx(in) * reach);
			int x1 = Math.max(ax1 + Dirs.dx(side.out()) * g, ax1 + Dirs.dx(in) * reach);
			int z0 = Math.min(az0 + Dirs.dz(side.out()) * g, az0 + Dirs.dz(in) * reach);
			int z1 = Math.max(az1 + Dirs.dz(side.out()) * g, az1 + Dirs.dz(in) * reach);
			// a pitch that carries the cross ridge to a block under the main ridge: tall, pointed front gables
			double mainTop = this.main.h(side.x(i0 + (width - 1) / 2) + Dirs.dx(in) * reach, side.z(i0 + (width - 1) / 2) + Dirs.dz(in) * reach);
			double half = (width + 2 * over) / 2.0;
			double pitch = this.opts.pitch;
			if (!Double.isNaN(mainTop) && half > 0) {
				pitch = Math.clamp((mainTop - 1 - this.profile.at(0)) / (half - 0.5), this.opts.pitch, 2.0);
			}
			add(gable(x0, z0, x1, z1, in.getAxis(), Profile.straight(pitch)), x0, z0, x1, z1);
			this.gableSides.add(side);
			this.gableRuns.add(new int[]{col, width});
			return this;
		}

		/**
		 * A dormer on this roof's slope above wall {@code side}: a window bay {@code width} wide from column {@code col}
		 * whose window sits on {@code sillY}, its little gable roof merged into the slope. Its walls are drawn by
		 * {@link #draw}.
		 */
		public Plan dormer(Side side, int col, int width, int sillY) {
			int i0 = side.index(col);
			Direction in = side.out().getOpposite();
			int c0 = i0 - 1;
			int c1 = i0 + width;
			boolean alongX = side.axis() == Direction.Axis.X;
			int reach = toRidge(side, i0 + (width - 1) / 2);
			if (reach < 2) {
				return this;
			}
			int ax0 = Math.min(side.x(c0), side.x(c1)) - (alongX ? 1 : 0);
			int ax1 = Math.max(side.x(c0), side.x(c1)) + (alongX ? 1 : 0);
			int az0 = Math.min(side.z(c0), side.z(c1)) - (alongX ? 0 : 1);
			int az1 = Math.max(side.z(c0), side.z(c1)) + (alongX ? 0 : 1);
			int x0 = Math.min(ax0 + Dirs.dx(side.out()), ax0 + Dirs.dx(in) * reach);
			int x1 = Math.max(ax1 + Dirs.dx(side.out()), ax1 + Dirs.dx(in) * reach);
			int z0 = Math.max(-1, Math.min(az0 + Dirs.dz(side.out()), az0 + Dirs.dz(in) * reach));
			int z1 = Math.max(az1 + Dirs.dz(side.out()), az1 + Dirs.dz(in) * reach);
			// eave stairs level with the window head, rising toward the dormer's ridge
			double base = h(sillY + 2);
			double pitch = this.opts.pitch;
			Direction.Axis ridge = in.getAxis();
			int half = (width + 3) / 2;
			double top = base + pitch * half;
			double mainTop = this.main.h(side.x(i0) + Dirs.dx(in) * reach, side.z(i0) + Dirs.dz(in) * reach);
			if (Double.isNaN(mainTop) || top > mainTop) {
				return this;
			}
			Field g = (x, z) -> {
				if (!inside(x, z, x0, z0, x1, z1)) {
					return Double.NaN;
				}
				double dd = ridge == Direction.Axis.Z ? Math.min(x - x0, x1 - x) : Math.min(z - z0, z1 - z);
				return base + pitch * dd;
			};
			this.field = this.field.max(g);
			this.dormers.add(new int[]{col, width, sillY});
			this.dormerSides.add(side);
			return this;
		}

		/** Rasterises the whole roof, fills gable ends and dormer walls, adds eave brackets. */
		public Built draw(Draft d) {
			Mat roof = d.mat(Role.ROOF);
			Mat trim = d.mat(Role.ROOF_TRIM);
			// the drawn area is limited to the field's own definition; clip to the plot
			int cx0 = Math.max(0, this.ex0);
			int cx1 = Math.min(d.frontage() - 1, this.ex1);
			int cz0 = Math.max(-1, this.ez0);
			int cz1 = Math.min(d.depth() - 1, this.ez1);
			Roof r = raster(d, this.field, this.y0, cx0, cz0, cx1, cz1, roof, trim, this.opts.ridge);
			Built b = new Built(r, this.form, this.axis, cx0, cz0, cx1, cz1);
			fillUnder(d, b, this.wx0, this.wz0, this.wx1, this.wz1, this.plateY);
			for (int i = 0; i < this.gableSides.size(); i++) {
				Side s = this.gableSides.get(i);
				int[] run = this.gableRuns.get(i);
				crossGableWall(d, r, s, run[0], run[1], this.plateY);
			}
			for (int i = 0; i < this.dormers.size(); i++) {
				int[] dm = this.dormers.get(i);
				dormerWalls(d, r, this.dormerSides.get(i), dm[0], dm[1], dm[2]);
			}
			if (this.opts.brackets && this.deep >= 1) {
				brackets(d, b, this.wx0, this.wz0, this.wx1, this.wz1);
			}
			if (this.form == RoofForm.PAGODA || this.opts.flare) {
				tips(d, r, cx0, cz0, cx1, cz1);
			}
			return b;
		}
	}

	/**
	 * Up-turned eave tips at the four corners of a hipped roof (curved tile roofs): a block at the corner's eave and a
	 * stair on it rising outward, a lantern hung beneath.
	 */
	static void tips(Draft d, Roof r, int x0, int z0, int x1, int z1) {
		Mat roof = d.mat(Role.ROOF);
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, net.minecraft.world.level.block.Blocks.LANTERN);
		int[][] corners = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
		for (int[] c : corners) {
			int x = c[0];
			int z = c[1];
			if (!r.covers(x, z) || z < 0 && r.surfaceY(x, z) < 3) {
				continue;
			}
			int y = r.surfaceY(x, z);
			Direction outX = x == x0 ? Dirs.LEFT : Dirs.RIGHT;
			try (var st = d.stage(dev.voidmc.hearth.api.StageKind.ROOF)) {
				d.set(x, y, z, roof.full());
				d.set(x, y + 1, z, roof.stairsOrSlab(outX.getOpposite(), Half.BOTTOM));
			}
			if (d.isAbsent(x, y - 1, z) && y - 1 > d.site().ground(x, z) + 2) {
				d.inStage(dev.voidmc.hearth.api.StageKind.EXTERIOR, () -> d.set(x, y - 1, z,
					dev.voidmc.hearth.build.draft.States.lantern(lantern, true)));
			}
		}
	}

	/** The end wall of a cross gable: the wall carried up from the plate to the cross gable's roof, with a window. */
	static void crossGableWall(Draft d, Roof r, Side side, int col, int width, int plateY) {
		Mat gable = d.mat(Role.GABLE);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat wall = d.mat(Role.WALL);
		Habits.WallKind kind = d.habits().walls().kind(1);
		boolean masonry = kind == Habits.WallKind.STONE || kind == Habits.WallKind.BRICK || kind == Habits.WallKind.PLASTER
			|| kind == Habits.WallKind.ADOBE;
		int i0 = side.index(col);
		int mid = i0 + (width - 1) / 2;
		int top = Integer.MIN_VALUE;
		for (int i = i0; i < i0 + width; i++) {
			int x = side.x(i);
			int z = side.z(i);
			int under = r.bottomY(x, z);
			if (under == Integer.MIN_VALUE) {
				continue;
			}
			for (int y = plateY + 1; y < under; y++) {
				Paint p;
				if (masonry) {
					p = wall.full();
				} else if (kind == Habits.WallKind.LOG) {
					p = wall.log(side.axis());
				} else if (i == i0 || i == i0 + width - 1) {
					p = frame.log(Direction.Axis.Y);
				} else if (y == plateY + 1 && kind == Habits.WallKind.TIMBER_FRAME) {
					p = beam.log(side.axis());
				} else {
					p = gable.full();
				}
				d.set(x, y, z, p);
				top = Math.max(top, y);
			}
		}
		// a window in the gable
		int wy = plateY + 2;
		int x = side.x(mid);
		int z = side.z(mid);
		int under = r.bottomY(x, z);
		int h = Math.min(2, under - wy - 1);
		if (h >= 1) {
			int w = width >= 7 ? 2 : 1;
			Openings.window(d, side, side.column(mid), wy, w, h, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, true, false));
		}
	}

	/**
	 * Fills the walls up to the roof's underside wherever the wall line passes under it (the gable triangles, and the
	 * short gap under a shallow eave), in the gable material with a timber post and collar for framed styles.
	 */
	static void fillUnder(Draft d, Built b, int wx0, int wz0, int wx1, int wz1, int plateY) {
		Roof r = b.roof;
		Mat gable = d.mat(Role.GABLE);
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat wall = d.mat(Role.WALL);
		Habits.WallKind kind = d.habits().walls().kind(1);
		boolean framed = kind == Habits.WallKind.TIMBER_FRAME;
		boolean masonry = kind == Habits.WallKind.STONE || kind == Habits.WallKind.BRICK || kind == Habits.WallKind.PLASTER
			|| kind == Habits.WallKind.ADOBE;
		for (Side s : Side.of(wx0, wz0, wx1, wz1)) {
			int mid = s.middle();
			boolean gableEnd = b.form != RoofForm.HIP && b.form != RoofForm.PAGODA && b.form != RoofForm.MANSARD && s.axis() != b.ridgeAxis;
			for (int i = 0; i < s.length(); i++) {
				int x = s.x(i);
				int z = s.z(i);
				if (!r.covers(x, z)) {
					continue;
				}
				int under = r.bottomY(x, z);
				for (int y = plateY + 1; y < under; y++) {
					Paint p;
					if (masonry) {
						p = wall.full();
					} else if (kind == Habits.WallKind.LOG && gableEnd) {
						p = wall.log(s.axis());
					} else if (framed && gableEnd && (i == mid || i == mid + (s.length() + 1) % 2 && s.length() % 2 == 0)) {
						p = frame.log(Direction.Axis.Y);
					} else if (framed && gableEnd && y == plateY + 2 && under - plateY > 4) {
						p = beam.log(s.axis());
					} else {
						p = gableEnd ? gable.full() : wall.full();
					}
					d.set(x, y, z, p);
				}
			}
		}
	}

	/** Upside-down stair brackets under the eaves, every other column along the eave sides. */
	static void brackets(Draft d, Built b, int wx0, int wz0, int wx1, int wz1) {
		Mat trim = d.mat(Role.ROOF_TRIM);
		if (!trim.has(Form.STAIRS)) {
			return;
		}
		for (Side s : Side.of(wx0, wz0, wx1, wz1)) {
			boolean eaveSide = b.form == RoofForm.HIP || b.form == RoofForm.PAGODA || b.form == RoofForm.MANSARD || s.axis() == b.ridgeAxis;
			if (!eaveSide) {
				continue;
			}
			for (int i = 1; i < s.length() - 1; i += 2) {
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				int sy = b.roof.surfaceY(ox, oz);
				if (sy == Integer.MIN_VALUE) {
					continue;
				}
				int y = b.roof.bottomY(ox, oz) - 1;
				if (y > d.site().ground(ox, oz) + 2 && d.isAbsent(ox, y, oz) && (Walls.inPlot(d, ox, oz) || oz == -1 && y >= 3)) {
					d.set(ox, y, oz, trim.stairs(s.out().getOpposite(), Half.TOP));
				}
			}
		}
	}

	/**
	 * The roof of a lower storey left open in front of a set-back storey: a deck at {@code deckY} over the outer rectangle
	 * where the inner (upper storey's) rectangle does not stand, a parapet with coping on the outer edge, and a spout.
	 * Returns the terrace's standing level.
	 */
	public static int terrace(Draft d, int[] outer, int[] inner, int deckY) {
		Mat deck = d.mat(Role.ROOF);
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(dev.voidmc.hearth.api.StageKind.ROOF)) {
			for (int z = outer[1]; z <= outer[3]; z++) {
				for (int x = outer[0]; x <= outer[2]; x++) {
					if (x >= inner[0] && x <= inner[2] && z >= inner[1] && z <= inner[3]) {
						continue;
					}
					boolean edge = x == outer[0] || x == outer[2] || z == outer[1] || z == outer[3];
					if (edge) {
						d.set(x, deckY, z, wall.full());
						d.set(x, deckY + 1, z, wall.full());
						d.set(x, deckY + 2, z, coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
					} else {
						d.set(x, deckY, z, deck.full());
						d.set(x, deckY + 1, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						d.set(x, deckY + 2, z, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		return deckY + 1;
	}

	/**
	 * A flat roof over walls {@code [wx0..wx1] × [wz0..wz1]} whose top course is {@code plateY}: a deck of roof blocks at
	 * {@code plateY + 1} inside the walls and, if {@code parapet}, a parapet course on the walls with coping slabs, and
	 * spouts (a stair poking out) along the low edges. Vigas (log ends) under the deck on adobe styles. Returns the deck's
	 * standing level (where a roof terrace's floor is).
	 */
	public static int flat(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, boolean parapet) {
		Mat deck = d.mat(Role.ROOF);
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT);
		Mat beam = d.mat(Role.BEAM);
		int y = plateY + 1;
		try (var s = d.stage(dev.voidmc.hearth.api.StageKind.ROOF)) {
			for (int z = wz0 + 1; z < wz1; z++) {
				for (int x = wx0 + 1; x < wx1; x++) {
					d.set(x, plateY, z, deck.full());
				}
			}
			for (Side side : Side.of(wx0, wz0, wx1, wz1)) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					d.set(x, plateY, z, wall.full());
					if (parapet) {
						d.set(x, y, z, wall.full());
						d.set(x, y + 1, z, coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
					}
				}
				// vigas: roof beams whose ends show through the wall head, every other column
				if (d.habits().walls().kind() == Habits.WallKind.ADOBE) {
					for (int i = 2; i < side.length() - 2; i += 2) {
						int ox = side.outX(i, 1);
						int oz = side.outZ(i, 1);
						if (Walls.inPlot(d, ox, oz) || oz == -1 && plateY - 1 >= 3) {
							d.setIfAbsent(ox, plateY - 1, oz, beam.log(side.out().getAxis()));
						}
					}
				}
			}
			// spouts on the back corners
			for (Side side : Side.of(wx0, wz0, wx1, wz1)) {
				if (side.out() != Dirs.BACK && side.out() != Dirs.LEFT) {
					continue;
				}
				int i = side.middle();
				int ox = side.outX(i, 1);
				int oz = side.outZ(i, 1);
				if (parapet && Walls.inPlot(d, ox, oz)) {
					d.setIfAbsent(ox, y, oz, deck.stairsOrSlab(side.out().getOpposite(), Half.TOP));
				}
			}
		}
		return y;
	}

	/** Builds the walls and window of a dormer after the roof has been rasterised over it. */
	public static void dormerWalls(Draft d, Roof r, Side side, int col, int width, int sillY) {
		int floorY = sillY;
		int i0 = side.index(col);
		Mat wall = d.mat(Role.GABLE);
		Mat frame = d.mat(Role.FRAME);
		for (int i = i0 - 1; i <= i0 + width; i++) {
			int x = side.x(i);
			int z = side.z(i);
			int under = r.bottomY(x, z);
			boolean cheek = i == i0 - 1 || i == i0 + width;
			for (int y = floorY; y < under; y++) {
				d.set(x, y, z, cheek ? frame.log(Direction.Axis.Y) : wall.full());
			}
		}
		Openings.window(d, side, col, sillY, width, 2, Openings.WindowStyle.PLAIN,
			new Openings.WindowOpts(false, false, false, false));
		// the cheeks run back into the roof along the dormer's sides
		Direction in = side.out().getOpposite();
		for (int i : new int[]{i0 - 1, i0 + width}) {
			for (int k = 1; k < 8; k++) {
				int x = side.x(i) + Dirs.dx(in) * k;
				int z = side.z(i) + Dirs.dz(in) * k;
				int under = r.bottomY(x, z);
				if (under == Integer.MIN_VALUE || under <= floorY) {
					break;
				}
				for (int y = floorY; y < under; y++) {
					if (d.isEmpty(x, y, z)) {
						d.set(x, y, z, wall.full());
					}
				}
			}
		}
	}
}
