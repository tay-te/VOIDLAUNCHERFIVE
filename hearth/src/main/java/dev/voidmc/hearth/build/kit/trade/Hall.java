package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Chimneys;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

/**
 * The closed building of a trade: a workshop, a store, a mill house. A {@link Body} of storeys over a rectangle,
 * walled in the style's construction, with its doors and windows planned once for all storeys and a roof in the
 * culture's manner fitted under the plot's height. Archetypes drive it step by step so they can put their own work
 * between the steps:
 *
 * <pre>
 *   Hall h = Hall.of(d, x0, z0, x1, z1, 0, new int[]{5, 4}, false, false);
 *   h.facade(doorX, 2, 1, 0.4).ground(false).shell().openings(true);
 *   h.roof(Hall.pick(d, Hall.PITCHED), Direction.Axis.X, 2, plan -> plan.dormer(...));
 *   h.chimney(x, z, 2);
 * </pre>
 *
 * Roofs never overrun the plot's height: the pitch is lowered (to a slab roof at worst) and, failing that, the roof
 * turns hipped or flat, keeping {@code reserve} blocks above the ridge for a chimney or a finial.
 */
public final class Hall {
	/** Roof forms a pitched workshop can take (cross gables are a gable with a gable over the door). */
	public static final RoofForm[] PITCHED = {RoofForm.GABLE, RoofForm.CROSS_GABLE, RoofForm.HALF_HIP, RoofForm.HIP, RoofForm.GAMBREL, RoofForm.SALTBOX,
		RoofForm.PAGODA, RoofForm.MANSARD, RoofForm.SHED, RoofForm.FLAT};

	private final Draft d;
	public final Body body;
	public Facade facade;
	/** The front door's column, or null if the facade has none. */
	public Integer door;
	public int doorWidth = 1;
	/** The pitched roof, or null when the roof is flat. */
	public Roofs.Roof roof;
	/** The flat roof's standing level, or MIN_VALUE. */
	public int deck = Integer.MIN_VALUE;
	public RoofForm form;
	public Direction.Axis ridge;
	public boolean stilted;
	/** The highest y the roof drew (the ridge, or the parapet's coping). */
	public int top;
	/** A wall construction to use instead of the style's (a boarded mill box, a stone forge), or null. */
	public Habits.WallKind kind;

	public Hall(Draft d, Body body) {
		this.d = d;
		this.body = body;
		this.top = body.plateY();
	}

	public static Hall of(Draft d, int x0, int z0, int x1, int z1, int base, int[] heights, boolean partyL, boolean partyR) {
		return new Hall(d, new Body(x0, z0, x1, z1, base, heights, partyL, partyR));
	}

	// ------------------------------------------------------------------------------------------------ geometry

	public int[] rect(int k) {
		return this.body.rect(k);
	}

	/** Storey k's floor inside the walls {x0, z0, x1, z1}. */
	public int[] inner(int k) {
		int[] r = this.body.rect(k);
		return new int[]{r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1};
	}

	public int floorY(int k) {
		return this.body.floorY(k);
	}

	public int plateY() {
		return this.body.plateY();
	}

	public int storeys() {
		return this.body.storeys();
	}

	public Side side(int k, Direction out) {
		return this.body.side(k, out);
	}

	// ------------------------------------------------------------------------------------------------ steps

	/** Plans doors and windows (see {@link Facade#plan}); the front door goes in the bay nearest {@code doorAt}. */
	public Hall facade(int doorAt, int doorWidth, int windowWidth, double density) {
		this.facade = Facade.plan(this.d, this.body, doorAt, doorWidth, windowWidth, density);
		this.doorWidth = doorWidth;
		this.door = this.facade.openings(Dirs.FRONT).stream().filter(o -> o.kind() == Facade.Kind.DOOR).map(Facade.Opening::at).findFirst()
			.orElse(null);
		return this;
	}

	/** Footings to the ground (or stilts under a raised floor) and a bank of earth round the walls. */
	public Hall ground(boolean stilts) {
		int[] r = this.body.rect(0);
		this.stilted = stilts;
		this.d.body(r[0], r[1], r[2], r[3]);
		if (stilts) {
			Groundwork.stilts(this.d, r[0], r[1], r[2], r[3], this.body.baseY - 1, 2);
			Floors.boards(this.d, r[0], r[1], r[2], r[3], this.body.baseY - 1);
		} else {
			Groundwork.footings(this.d, r[0], r[1], r[2], r[3], this.body.baseY - 1);
			Groundwork.apron(this.d, r[0], r[1], r[2], r[3], this.body.baseY, 2);
		}
		return this;
	}

	/** Floors, the walls storey by storey (beam ends where the style shows them), and the rooms' air. */
	public Hall shell() {
		Body b = this.body;
		// the ground floor is boarded over whatever the levelling left there (earth, a dyke's fill)
		Mat floor = this.d.mat(Role.FLOOR);
		int[] r0 = b.rect(0);
		try (var s = this.d.stage(StageKind.FLOORS)) {
			for (int z = r0[1] + 1; z <= r0[3] - 1; z++) {
				for (int x = r0[0] + 1; x <= r0[2] - 1; x++) {
					this.d.set(x, b.floorY(0) - 1, z, floor.full());
				}
			}
		}
		for (int k = 0; k < b.storeys(); k++) {
			int[] r = b.rect(k);
			Floors.boards(this.d, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, b.floorY(k) - 1);
			if (k > 0) {
				Floors.boards(this.d, r[0], r[1], r[2], r[3], b.floorY(k) - 1);
			}
			Walls.storey(this.d, b, k, this.facade, this.kind != null ? this.kind : this.d.habits().walls().kind(k));
			if (k > 0 && this.d.chance(this.d.habits().walls().beamEnds())) {
				Walls.beamEnds(this.d, b, k, this.facade, Dirs.FRONT);
			}
		}
		for (int k = 0; k < b.storeys(); k++) {
			int[] r = b.rect(k);
			this.d.carve(r[0] + 1, b.floorY(k), r[1] + 1, r[2] - 1, b.floorY(k + 1) - 2, r[3] - 1);
		}
		this.d.eave(b.plateY());
		return this;
	}

	/** The window dressing the style's habits call for. */
	public Openings.WindowStyle windowStyle() {
		Habits.Windows hw = this.d.habits().windows();
		return hw.lattice() ? Openings.WindowStyle.LATTICE : this.d.chance(hw.arched()) ? Openings.WindowStyle.ARCHED : Openings.WindowStyle.DRESSED;
	}

	/** The front door (with a lamp beside it if asked) and every storey's windows, from the facade plan. */
	public Hall openings(boolean lamp) {
		Habits.Windows hw = this.d.habits().windows();
		Openings.WindowStyle style = windowStyle();
		int winH = hw.tall() ? 2 : 1;
		Body b = this.body;
		for (int k = 0; k < b.storeys(); k++) {
			int sh = b.height(k);
			if (sh < 3) {
				continue;
			}
			for (Side s : b.sides(k)) {
				if (b.isParty(s.out())) {
					continue;
				}
				for (Facade.Opening o : this.facade.openings(s.out())) {
					if (!s.contains(o.at()) || !s.contains(o.at() + o.width() - 1)) {
						continue;
					}
					int y = b.floorY(k);
					if (o.kind() == Facade.Kind.DOOR && k == 0) {
						Openings.door(this.d, s, o.at(), y, o.width(), new Openings.DoorOpts(lamp, false, true));
					} else if (o.kind() == Facade.Kind.WINDOW || o.kind() == Facade.Kind.DOOR) {
						Openings.window(this.d, s, o.at(), y + 1, o.width(), Math.min(winH, sh - 2), style);
					}
				}
			}
		}
		return this;
	}

	/**
	 * A door in any wall of storey k at a column (a side door to the yard, a loft door), replacing a window there. Not
	 * the main entrance unless {@code main}. Returns false if the column is a corner or not wall.
	 */
	public boolean door(int k, Direction out, int col, int width, boolean main, boolean lamp) {
		Side s = this.body.side(k, out);
		if (!s.contains(col) || !s.contains(col + width - 1) || s.isCorner(s.index(col)) || s.isCorner(s.index(col + width - 1))) {
			return false;
		}
		int y = this.body.floorY(k);
		for (int c = col; c < col + width; c++) {
			int i = s.index(c);
			for (int yy = y; yy <= y + 1; yy++) {
				if (!Openings.isWallBlock(this.d, s.x(i), yy, s.z(i)) && !isGlass(s.x(i), yy, s.z(i))) {
					return false;
				}
			}
			// a window's sill or box outside goes
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			if (Walls.inPlot(this.d, ox, oz)) {
				for (int yy = y - 1; yy <= y + 1; yy++) {
					var st = this.d.get(ox, yy, oz);
					if (st != null && !st.isAir() && !st.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
						net.minecraft.core.BlockPos.ZERO)) {
						this.d.remove(ox, yy, oz);
					}
				}
			}
		}
		Openings.door(this.d, s, col, y, width, new Openings.DoorOpts(lamp, false, main));
		return true;
	}

	private boolean isGlass(int x, int y, int z) {
		var s = this.d.get(x, y, z);
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock || s.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock);
	}

	/**
	 * An open bay in a wall of the ground storey: the wall taken out from the floor up {@code height} blocks over
	 * {@code width} columns, a beam (or a stone lintel in masonry) across the head. Workshops open their fronts like this.
	 */
	public void bay(Direction out, int col, int width, int height) {
		Side s = this.body.side(0, out);
		int y = this.body.floorY(0);
		Habits.WallKind kind = this.d.habits().walls().kind();
		boolean masonry = kind == Habits.WallKind.STONE || kind == Habits.WallKind.BRICK || kind == Habits.WallKind.PLASTER
			|| kind == Habits.WallKind.ADOBE;
		Mat beam = this.d.mat(Role.BEAM);
		Mat lintel = this.d.mat(Role.WALL_ACCENT);
		try (var st = this.d.stage(StageKind.OPENINGS)) {
			for (int c = col; c < col + width; c++) {
				if (!s.contains(c)) {
					continue;
				}
				int i = s.index(c);
				for (int yy = y; yy < y + height; yy++) {
					this.d.set(s.x(i), yy, s.z(i), Blocks.AIR.defaultBlockState());
				}
				// the sill outside a window that stood here goes
				int ox = s.outX(i, 1);
				int oz = s.outZ(i, 1);
				for (int yy = y - 1; yy < y + height; yy++) {
					var b = this.d.get(ox, yy, oz);
					if (b != null && !b.isAir() && !b.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
						net.minecraft.core.BlockPos.ZERO)) {
						this.d.remove(ox, yy, oz);
					}
				}
			}
		}
		try (var st = this.d.stage(StageKind.FRAME)) {
			for (int c = col - 1; c <= col + width; c++) {
				if (!s.contains(c)) {
					continue;
				}
				int i = s.index(c);
				if (Openings.isWallBlock(this.d, s.x(i), y + height, s.z(i)) || c >= col && c < col + width) {
					this.d.set(s.x(i), y + height, s.z(i), masonry ? lintel.full() : beam.log(s.axis()));
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ roofs

	/** A roof form from the {@code roof} param, else from the culture's preferences among {@code allowed}. */
	public static RoofForm pick(Draft d, RoofForm... allowed) {
		String p = d.param("roof", "");
		if (!p.isEmpty()) {
			try {
				return RoofForm.valueOf(p.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				// fall through to the seeded choice
			}
		}
		return d.habits().roof().pick(d.random(), allowed);
	}

	/**
	 * Roofs the top storey in {@code form} with the ridge along {@code axis}, fitted under the plot's height less
	 * {@code reserve}; {@code extras} may add cross gables and dormers to the plan before it is drawn. Flat (and round)
	 * forms get a deck with a parapet.
	 */
	public Hall roof(RoofForm form, Direction.Axis axis, int reserve, Consumer<Roofs.Plan> extras) {
		int[] tr = this.body.topRect();
		int plate = this.body.plateY();
		int limit = this.d.maxHeight() - 1 - reserve;
		RoofForm base = form == RoofForm.CROSS_GABLE ? RoofForm.GABLE : form;
		Roofs.Opts o = null;
		if (base != RoofForm.FLAT && base != RoofForm.DOME && base != RoofForm.CONE) {
			o = fit(this.d, tr[0], tr[1], tr[2], tr[3], plate, base, axis, Roofs.Opts.of(this.d), this.body.partyLeft, this.body.partyRight, limit);
			if (o == null && base != RoofForm.HIP) {
				o = fit(this.d, tr[0], tr[1], tr[2], tr[3], plate, RoofForm.HIP, axis, Roofs.Opts.of(this.d), this.body.partyLeft, this.body.partyRight,
					limit);
				if (o != null) {
					base = RoofForm.HIP;
					form = RoofForm.HIP;
				}
			}
		}
		this.ridge = axis;
		if (o == null) {
			this.form = RoofForm.FLAT;
			// a parapet where the plot's height leaves room for one, else a plain deck
			boolean parapet = plate + 2 <= this.d.maxHeight() - 1;
			this.deck = Roofs.flat(this.d, tr[0], tr[1], tr[2], tr[3], plate, parapet);
			this.top = parapet ? this.deck + 1 : plate;
			return this;
		}
		this.form = form;
		Roofs.Plan plan = Roofs.plan(this.d, tr[0], tr[1], tr[2], tr[3], plate, base, axis, o, this.body.partyLeft, this.body.partyRight);
		Side front = this.body.side(this.body.storeys() - 1, Dirs.FRONT);
		int width = tr[2] - tr[0] + 1;
		if (form == RoofForm.CROSS_GABLE && axis == Direction.Axis.X && width >= 7) {
			int gw = width >= 8 ? 5 : 3;
			int centre = this.door != null ? this.door : (tr[0] + tr[2]) / 2;
			int col = Math.clamp(centre - gw / 2, tr[0] + 1, tr[2] - gw);
			plan.crossGable(front, col, gw, this.d);
		}
		if (extras != null) {
			extras.accept(plan);
		}
		this.roof = plan.draw(this.d).roof();
		this.top = this.roof.ridgeY;
		sealedKnee();
		return this;
	}

	/**
	 * A knee attic (two rows or less under a pitched roof) no stair climbs to is opened to the room below, its rafters
	 * over it: a floor nobody could reach would leave a dark void under the roof.
	 */
	private void sealedKnee() {
		int k = this.body.storeys() - 1;
		if (k < 1 || this.body.height(k) > 2) {
			return;
		}
		int[] up = inner(k);
		int fy = this.body.floorY(k) - 1;
		for (int x = up[0]; x <= up[2]; x++) {
			for (int z = up[1]; z <= up[3]; z++) {
				if (this.d.isEmpty(x, fy, z)) {
					return;
				}
			}
		}
		openLoft(k);
	}

	/** The y of the highest block a roof plan would draw (see {@link Roofs.Plan}). */
	public static int ridgeY(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, RoofForm form, Direction.Axis axis, Roofs.Opts o, boolean partyL,
		boolean partyR) {
		boolean allRound = form == RoofForm.HIP || form == RoofForm.PAGODA || form == RoofForm.MANSARD;
		int eaveX = allRound || axis == Direction.Axis.Z ? o.eaves() : o.gableOverhang();
		int eaveZ = allRound || axis == Direction.Axis.X ? o.eaves() : o.gableOverhang();
		int nx0 = partyL ? wx0 : wx0 - eaveX;
		int nx1 = partyR ? wx1 : wx1 + eaveX;
		int nz0 = wz0 - eaveZ;
		int nz1 = wz1 + eaveZ;
		int over = allRound ? Math.min(eaveX, eaveZ) : axis == Direction.Axis.X ? eaveZ : eaveX;
		Roofs.Profile p = switch (form) {
			case GAMBREL, MANSARD -> Roofs.Profile.broken(Math.max(1.5, o.pitch() * 2), 0.5, over + 2);
			case PAGODA -> Roofs.Profile.curved(Math.max(1, o.pitch()));
			default -> Roofs.Profile.straight(o.pitch());
		};
		double hMax;
		if (form == RoofForm.SHED) {
			int run = axis == Direction.Axis.X ? nz1 - nz0 : nx1 - nx0;
			hMax = Roofs.Profile.straight(Math.max(0.5, o.pitch() * 0.5)).at(run);
		} else {
			int dMax = allRound ? Math.min((nx1 - nx0) / 2, (nz1 - nz0) / 2) : axis == Direction.Axis.X ? (nz1 - nz0) / 2 : (nx1 - nx0) / 2;
			hMax = p.at(dMax);
		}
		int tWall = Math.max(1, (int) Math.round(p.at(Math.max(0, over)) * 2));
		int y0 = plateY + 1 - ((tWall + 1) / 2 - 1);
		int t = Math.max(1, (int) Math.round(hMax * 2));
		return y0 + (t + 1) / 2 - 1;
	}

	/**
	 * The style's roof options with the pitch lowered (and at last the eaves shortened) until the ridge stands no higher
	 * than {@code limit}; null if even a slab roof is too tall.
	 */
	public static Roofs.Opts fit(Draft d, int wx0, int wz0, int wx1, int wz1, int plateY, RoofForm form, Direction.Axis axis, Roofs.Opts o, boolean partyL,
		boolean partyR, int limit) {
		for (double p = o.pitch(); p >= 0.5 - 1e-9; p -= 0.25) {
			Roofs.Opts t = o.withPitch(p);
			if (ridgeY(d, wx0, wz0, wx1, wz1, plateY, form, axis, t, partyL, partyR) <= limit) {
				return t;
			}
		}
		Roofs.Opts t = o.withPitch(0.5).withEaves(Math.min(1, o.eaves()), Math.min(1, o.gableOverhang()));
		if (ridgeY(d, wx0, wz0, wx1, wz1, plateY, form, axis, t, partyL, partyR) <= limit) {
			return t;
		}
		return null;
	}

	/**
	 * How many of the storeys {@code heights} (from the ground up; an attic knee of {@code knee} rows goes on top when
	 * positive) fit over walls {@code [x0..x1] × [z0..z1]} at standing level {@code base} with a roof in {@code form}
	 * pitched at least {@code minPitch} (the habit's pitch if lower), under the plot's height less {@code reserve}.
	 * At least one.
	 */
	public static int fitStoreys(Draft d, int x0, int z0, int x1, int z1, int base, int[] heights, int knee, RoofForm form, Direction.Axis axis,
		int reserve, double minPitch) {
		return Math.max(1, fitting(d, x0, z0, x1, z1, base, heights, knee, form, axis, reserve, minPitch));
	}

	/** As {@link #fitStoreys}, but zero when not even the first storey fits. */
	private static int fitting(Draft d, int x0, int z0, int x1, int z1, int base, int[] heights, int knee, RoofForm form, Direction.Axis axis,
		int reserve, double minPitch) {
		Roofs.Opts o = Roofs.Opts.of(d);
		double want = Math.min(minPitch, o.pitch());
		RoofForm f = form == RoofForm.CROSS_GABLE ? RoofForm.GABLE : form;
		for (int n = heights.length; n >= 1; n--) {
			int plate = base - 1 + (knee > 0 ? knee : 0);
			for (int k = 0; k < n; k++) {
				plate += heights[k];
			}
			if (f == RoofForm.FLAT || f == RoofForm.DOME || f == RoofForm.CONE) {
				if (plate + 3 <= d.maxHeight() - 1 - reserve) {
					return n;
				}
				continue;
			}
			Roofs.Opts t = fit(d, x0, z0, x1, z1, plate, f, axis, o, false, false, d.maxHeight() - 1 - reserve);
			if (t != null && t.pitch() >= want - 1e-9) {
				return n;
			}
		}
		return 0;
	}

	/**
	 * Storey heights for a building over walls {@code [x0..x1] × [z0..z1]} standing at {@code base}: as many of
	 * {@code storeys} as fit with a roof in {@code form} pitched at least 0.75 (the habit's pitch if lower) whichever way
	 * its ridge runs, an attic knee of two on top when {@code attic} and the roof is pitched, under the plot's height
	 * less {@code reserve}. Never fewer than one storey.
	 */
	public static int[] budget(Draft d, int x0, int z0, int x1, int z1, int base, int[] storeys, boolean attic, RoofForm form, int reserve) {
		return budget(d, x0, z0, x1, z1, base, storeys, attic, form, reserve, null);
	}

	/** As {@link #budget(Draft, int, int, int, int, int, int[], boolean, RoofForm, int)} for a ridge along {@code axis} (null: either way). */
	public static int[] budget(Draft d, int x0, int z0, int x1, int z1, int base, int[] storeys, boolean attic, RoofForm form, int reserve,
		Direction.@Nullable Axis axis) {
		boolean flat = form == RoofForm.FLAT || form == RoofForm.DOME || form == RoofForm.CONE;
		Direction.Axis worst = axis != null ? axis : z1 - z0 >= x1 - x0 ? Direction.Axis.X : Direction.Axis.Z;
		int knee = attic && !flat ? 2 : 0;
		int n = fitting(d, x0, z0, x1, z1, base, storeys, knee, form, worst, reserve, 0.75);
		if (n == 0 && knee > 0) {
			// no room for the attic knee: the roof sits on the wall plate
			knee = 0;
			n = fitting(d, x0, z0, x1, z1, base, storeys, 0, form, worst, reserve, 0.75);
		}
		if (n == 0) {
			// a low plot: the one storey as tall as still takes a roof (three rows at the least)
			for (int h = storeys[0] - 1; h >= 3; h--) {
				if (fitting(d, x0, z0, x1, z1, base, new int[]{h}, 0, form, worst, reserve, 0.5) > 0 || h == 3) {
					return new int[]{h};
				}
			}
			return new int[]{storeys[0]};
		}
		int[] out = new int[n + (knee > 0 ? 1 : 0)];
		System.arraycopy(storeys, 0, out, 0, n);
		if (knee > 0) {
			out[n] = knee;
		}
		return out;
	}

	/**
	 * A chimney at (x, z) from {@code fromY} through the roof, smoking: clear of the ridge where the plot's height
	 * allows, otherwise as tall as it may be. Returns the smoking top, or MIN_VALUE if there was no room for one.
	 */
	public int chimney(int x, int z, int fromY) {
		int max = this.d.maxHeight() - 1;
		Mat stone = this.d.mat(Role.CHIMNEY);
		if (this.roof != null) {
			int surface = this.roof.covers(x, z) ? this.roof.surfaceY(x, z) : fromY;
			if (surface + 2 > max) {
				return Integer.MIN_VALUE;
			}
			int top = Chimneys.stack(this.d, x, z, fromY, this.roof, Math.min(this.roof.ridgeY + 1, max));
			this.top = Math.max(this.top, top);
			return top;
		}
		if (this.deck == Integer.MIN_VALUE || this.deck + 1 > max) {
			return Integer.MIN_VALUE;
		}
		int top = Math.min(this.deck + 1, max);
		try (var s = this.d.stage(StageKind.ROOF)) {
			for (int y = fromY; y < top; y++) {
				this.d.set(x, y, z, stone.full());
			}
			this.d.set(x, top, z, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.SIGNAL_FIRE, false));
		}
		this.top = Math.max(this.top, top);
		return top;
	}

	/**
	 * A flat roof's parapet finished with rounded shoulders: the coping swelling a course at the corners, the crown of an
	 * adobe or stone store (a workshop is no fort: no pinnacles). Nothing if the roof is pitched or the plot too low.
	 */
	public Hall crown() {
		if (this.deck == Integer.MIN_VALUE) {
			return this;
		}
		int[] r = this.body.rect(this.body.storeys() - 1);
		int y = this.deck + 1;
		if (y >= this.d.maxHeight()) {
			return this;
		}
		if (y + 1 < this.d.maxHeight()) {
			dev.voidmc.hearth.build.kit.homestead.Adobe.parapet(this.d, r[0], r[1], r[2], r[3], this.deck,
				dev.voidmc.hearth.build.kit.homestead.Adobe.Parapet.SHOULDERED);
		}
		// where the plot's height took no coping (the shouldered parapet wants three rows over the deck), the wall is
		// carried up one course round the deck: a deck folk climb to is never an open edge
		Mat wall = this.d.mat(Role.WALL);
		try (var st = this.d.stage(StageKind.ROOF)) {
			for (Side side : Side.of(r[0], r[1], r[2], r[3])) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (this.d.isSolid(x, this.deck, z) && this.d.isAbsent(x, y, z)) {
						this.d.set(x, y, z, wall.full());
					}
				}
			}
		}
		this.top = Math.max(this.top, y + 1);
		return this;
	}

	/**
	 * Makes a flat roof a lived-on terrace, the way the desert and the pueblos use their roofs: a stair climbing the
	 * outside of wall {@code stairSide} (LEFT or RIGHT) from its front end to a gap in the parapet, where the ground
	 * beside the wall is free; a little room set back on the deck at the back corner across from it, with its own
	 * parapet, a door onto the terrace and a window (where the plot's height allows); a cloth awning on poles and pots
	 * on the parapet. On a body of two storeys or more the way up is a ladder from the top storey through a hatch in the
	 * deck (an outside stair only where the side is clear all the way up); with no way up there is no room on the deck
	 * either (the audit's W1: a door onto a deck nobody reaches). Returns whether the terrace can be walked to.
	 */
	public boolean terrace(Direction stairSide, boolean room) {
		if (this.deck == Integer.MIN_VALUE) {
			return false;
		}
		int top = this.body.storeys() - 1;
		int[] r = this.body.rect(top);
		int deckY = this.deck;
		boolean stair = false;
		Direction used = stairSide;
		int[] hatch = this.body.storeys() > 1 ? hatch() : null;
		if (hatch != null) {
			stair = true;
			used = hatch[0] <= (r[0] + r[2]) / 2 ? Dirs.LEFT : Dirs.RIGHT;
		}
		// the stair: up the outside of the side wall from the front, a landing at its head and a gap in the parapet
		for (Direction tryside : new Direction[]{stairSide, stairSide.getOpposite()}) {
		if (stair) {
			break;
		}
		Side s = this.body.side(0, tryside);
		int rise = deckY;
		int len = s.length();
		int ox = s.outX(0, 1);
		if (len >= rise + 2 && ox >= 0 && ox < this.d.frontage()) {
			int[] r0 = this.body.rect(0);
			int z0 = r0[1] + 1;
			boolean free = true;
			// over one storey the side must be clear to the stair's head room (the stair climbs past the upper windows)
			int clear = this.body.storeys() == 1 ? Math.min(rise + 2, 3) : rise + 2;
			for (int z = z0 - 1; z <= z0 + rise; z++) {
				for (int y = 0; y <= clear; y++) {
					if (!this.d.isEmpty(ox, y, z) && !(y == 0 && this.d.get(ox, y, z) == null)) {
						free = false;
					}
				}
				if (z < 0 || z >= this.d.depth()) {
					free = false;
				}
			}
			if (free) {
				Stairs.straight(this.d, ox, 0, z0, Dirs.BACK, rise, 1);
				int lz = z0 + rise;
				int wx = s.x(0);
				try (var st = this.d.stage(StageKind.EXTERIOR)) {
					// the landing beside the parapet gap, on a pier
					Mat pier = this.d.mat(Role.WALL);
					for (int y = Math.min(-1, this.d.site().ground(ox, lz) - 1); y < rise - 1; y++) {
						this.d.set(ox, y, lz, pier.full());
					}
					this.d.set(ox, rise - 1, lz, this.d.mat(Role.PAVING).full());
					this.d.set(ox, rise, lz, Blocks.AIR.defaultBlockState());
					this.d.set(ox, rise + 1, lz, Blocks.AIR.defaultBlockState());
					// the gap in the parapet
					this.d.set(wx, deckY, lz, Blocks.AIR.defaultBlockState());
					this.d.set(wx, deckY + 1, lz, Blocks.AIR.defaultBlockState());
				}
				this.d.poi(dev.voidmc.hearth.api.PoiType.VIEWPOINT, wx + Dirs.dx(tryside.getOpposite()), deckY, lz, Dirs.FRONT);
				stair = true;
				used = tryside;
			}
		}
		}
		// the room on the deck at the back corner across from the stair
		Mat wall = this.d.mat(Role.WALL);
		int rw = Math.min(5, r[2] - r[0] - 1);
		int rd = Math.min(4, r[3] - r[1] - 2);
		boolean left = used == Dirs.RIGHT;
		int rx0 = left ? r[0] : r[2] - rw + 1;
		int rz0 = r[3] - rd + 1;
		boolean clearOfHatch = hatch == null || hatch[0] < rx0 || hatch[0] > rx0 + rw - 1 || hatch[1] < rz0;
		if (room && stair && clearOfHatch && rw >= 3 && rd >= 3 && deckY + 5 <= this.d.maxHeight() - 1) {
			int x0 = rx0;
			int x1 = x0 + rw - 1;
			int z1 = r[3];
			int z0 = z1 - rd + 1;
			try (var st = this.d.stage(StageKind.WALLS)) {
				for (int[] c : Draft.ring(x0, z0, x1, z1)) {
					for (int y = deckY; y <= deckY + 2; y++) {
						this.d.set(c[0], y, c[1], wall.full());
					}
				}
			}
			this.d.carve(x0 + 1, deckY, z0 + 1, x1 - 1, deckY + 1, z1 - 1);
			Roofs.flat(this.d, x0, z0, x1, z1, deckY + 2, true);
			// the door onto the terrace where the deck before it can be stood on (nearest the middle)
			int cx = (x0 + x1) / 2;
			for (int o : new int[]{0, 1, -1}) {
				int c = (x0 + x1) / 2 + o;
				if (c > x0 && c < x1 && dev.voidmc.hearth.build.kit.Doors.standable(this.d, c, deckY, z0 - 1)) {
					cx = c;
					break;
				}
			}
			int dcx = cx;
			Mat door = this.d.mat(Role.DOOR);
			this.d.inStage(StageKind.OPENINGS, () -> this.d.door(dcx, deckY, z0, door.mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
				net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT));
			// a roof beam's end over the doorway goes (the vigas of an adobe roof poke out at head height)
			var over = this.d.get(dcx, deckY + 1, z0 - 1);
			if (over != null && over.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock) {
				this.d.remove(dcx, deckY + 1, z0 - 1);
			}
			int wxx = left ? x1 : x0;
			if (wxx > r[0] && wxx < r[2]) {
				this.d.inStage(StageKind.OPENINGS, () -> this.d.set(wxx, deckY + 1, (z0 + z1) / 2, this.d.mat(Role.GLASS).pane()));
			}
			int up = deckY + 3;
			// its parapet's corners eased into shoulders, as the hall's own
			dev.voidmc.hearth.build.kit.homestead.Adobe.parapet(this.d, x0, z0, x1, z1, up, dev.voidmc.hearth.build.kit.homestead.Adobe.Parapet.SHOULDERED);
			this.top = Math.max(this.top, up + 2);
		}
		if (!stair) {
			// nobody climbs to it: no awning or pots up there either (a deck dressed for folk nobody reaches)
			return false;
		}
		// an awning of cloth on poles over the front of the terrace, pots along the parapet
		Mat pole = woodFence(this.d);
		int ax0 = r[0] + 1;
		int ax1 = Math.min(r[2] - 1, ax0 + 2);
		int az0 = r[1] + 1;
		int az1 = Math.min(r[3] - 1, az0 + 1);
		if (deckY + 3 < this.d.maxHeight() && ax1 > ax0 && az1 >= az0) {
			try (var st = this.d.stage(StageKind.EXTERIOR)) {
				// canvas of one colour, not the house's every cloth cell by cell
				dev.voidmc.hearth.build.kit.townhome.Life.awning(this.d, ax0, az0, ax1, az1, deckY + 2);
				for (int[] c : new int[][]{{ax1, az1}, {ax1, az0}}) {
					// the back pole never stands against the deck room's wall, closing the way past it to the room's door
					boolean closes = az1 != az0 && c[1] == az1 && this.d.isSolid(c[0], deckY, c[1] + 1);
					if (!closes && this.d.isEmpty(c[0], deckY, c[1]) && this.d.isSolid(c[0], deckY - 1, c[1])) {
						this.d.set(c[0], deckY, c[1], pole.fence());
						this.d.set(c[0], deckY + 1, c[1], pole.fence());
					}
				}
			}
		}
		for (int x = r[0] + 2; x < r[2] - 1; x += 3) {
			if (this.d.isEmpty(x, deckY + 2, r[1]) && this.d.get(x, deckY + 1, r[1]) != null) {
				dev.voidmc.hearth.build.kit.Garden.pot(this.d, x, deckY + 2, r[1]);
			}
		}
		return stair;
	}

	/**
	 * The way up to a flat roof from the top storey: a ladder against a side wall from the storey's floor through a hatch
	 * in the deck ({@link dev.voidmc.hearth.build.kit.refine.Lofts#ladder}), in the middle of the depth (clear of the
	 * awning at the front and the room at the back), where the floor beside the ladder's foot is clear to step from and
	 * nothing stands before it. Returns {x, z} of the hatch, or null where no wall cell will take one.
	 */
	private int @Nullable [] hatch() {
		int k = this.body.storeys() - 1;
		int[] in = inner(k);
		int y0 = this.body.floorY(k);
		int deckY = this.deck;
		int mid = (in[1] + in[3]) / 2;
		int span = in[3] - in[1] + 1;
		for (int o = 0; o <= span / 2; o++) {
			for (int z : new int[]{mid - o, mid + o + 1}) {
				if (z < in[1] + 1 || z > in[3] - 1) {
					continue;
				}
				for (Direction toWall : new Direction[]{Dirs.LEFT, Dirs.RIGHT}) {
					int x = toWall == Dirs.LEFT ? in[0] : in[2];
					int mx = x - Dirs.dx(toWall);
					if (!this.d.isEmpty(mx, y0, z) || !this.d.isEmpty(mx, y0 + 1, z) || !this.d.isSolid(mx, y0 - 1, z) || !this.d.isSolid(x, y0 - 1, z)) {
						continue;
					}
					boolean doorBeside = false;
					for (Direction dir : Dirs.HORIZONTAL) {
						var n = this.d.get(x + Dirs.dx(dir), y0, z + Dirs.dz(dir));
						doorBeside |= n != null && n.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
					}
					if (!doorBeside && dev.voidmc.hearth.build.kit.refine.Lofts.ladder(this.d, x, z, y0, deckY, toWall, null)) {
						// the hatch's lid, lifted off and laid on the deck beside it (stood open against the parapet, its top
						// edge was a step up onto the coping and over the edge)
						Mat lid = this.d.mat(Role.SHUTTER).has(Form.TRAPDOOR) ? this.d.mat(Role.SHUTTER) : this.d.mat(Role.FURNITURE);
						int lx = mx;
						if (this.d.isEmpty(lx, deckY, z) && this.d.isSolid(lx, deckY - 1, z)) {
							this.d.inStage(StageKind.ROOF, () -> this.d.set(lx, deckY, z, lid.trapdoor(toWall.getOpposite(), Half.BOTTOM, false)));
						}
						return new int[]{x, z};
					}
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------ inside

	/**
	 * The floor of storey k for hand furnishing. Under a pitched roof the top storey's attic is carved up to the
	 * roof's underside first, and its rooms are two high.
	 */
	public Floorplan floor(int k) {
		int[] r = inner(k);
		int y = this.body.floorY(k);
		int h = this.body.height(k) - 1;
		if (k == this.body.storeys() - 1 && this.roof != null) {
			for (int z = r[1]; z <= r[3]; z++) {
				for (int x = r[0]; x <= r[2]; x++) {
					int under = this.roof.covers(x, z) ? this.roof.bottomY(x, z) : y + 2;
					for (int yy = y; yy < under; yy++) {
						if (this.d.isAbsent(x, yy, z)) {
							this.d.set(x, yy, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
			h = Math.max(2, Math.min(h, 2));
		}
		return new Floorplan(r[0], r[1], r[2], r[3], y, Math.max(2, h));
	}

	/**
	 * A straight flight from storey k up to k + 1 along the inside of wall {@code wall}, its foot at the end toward
	 * {@code from} (LEFT/RIGHT for the front and back walls, FRONT/BACK for the side walls). Cuts the stairwell.
	 * Returns {footX, footZ, headX, headZ, runX0, runZ0, runX1, runZ1}: the floor cell before the first step, the cell
	 * past the top step on the floor above, and the run; null if the wall is too short for the rise.
	 */
	public int[] flight(int k, Direction wall, Direction from) {
		int[] r = inner(k);
		int rise = this.body.height(k);
		boolean alongX = wall == Dirs.FRONT || wall == Dirs.BACK;
		int len = alongX ? r[2] - r[0] + 1 : r[3] - r[1] + 1;
		if (len < rise + 2) {
			return null;
		}
		int fixed = switch (wall) {
			case SOUTH -> r[1];
			case NORTH -> r[3];
			case WEST -> r[0];
			default -> r[2];
		};
		Direction dir = from.getOpposite();
		int footA = alongX ? (from == Dirs.LEFT ? r[0] : r[2]) : (from == Dirs.FRONT ? r[1] : r[3]);
		int startA = footA + (alongX ? Dirs.dx(dir) : Dirs.dz(dir));
		int sx = alongX ? startA : fixed;
		int sz = alongX ? fixed : startA;
		int y = this.body.floorY(k);
		Stairs.straight(this.d, sx, y, sz, dir, rise, 1);
		int fy = this.body.floorY(k + 1) - 1;
		int lx = sx + Dirs.dx(dir) * (rise - 1);
		int lz = sz + Dirs.dz(dir) * (rise - 1);
		for (int i = Math.max(0, rise - 3); i < rise - 1; i++) {
			this.d.set(sx + Dirs.dx(dir) * i, fy, sz + Dirs.dz(dir) * i, Blocks.AIR.defaultBlockState());
		}
		this.d.inStage(StageKind.FLOORS, () -> this.d.set(lx, fy, lz, this.d.mat(Role.FURNITURE).stairs(dir, Half.BOTTOM)));
		// a balustrade round the stairwell on the floor above, on the side away from the wall
		Stairs.well(this.d, sx, y, sz, dir, rise, wall.getOpposite());
		int fx = alongX ? footA : fixed;
		int fz = alongX ? fixed : footA;
		int hx = sx + Dirs.dx(dir) * rise;
		int hz = sz + Dirs.dz(dir) * rise;
		return new int[]{fx, fz, hx, hz, sx, sz, lx, lz};
	}

	/**
	 * A U-stair from storey k up to k + 1 in a well two wide against the inside of side wall {@code wall} (LEFT or
	 * RIGHT) at the back: half the rise toward the back beside the wall, a landing, then back toward the front in the
	 * lane beside it. Returns {footX, footZ, headX, headZ, wellX0, wellZ0, wellX1, wellZ1}, or null if there is no room
	 * (the floor must be at least three wide and three deep plus the foot).
	 */
	public int[] switchback(int k, Direction wall) {
		int[] r = inner(k);
		int rise = this.body.height(k);
		int first = rise / 2;
		int second = rise - first;
		if (r[2] - r[0] + 1 < 3 || r[3] - r[1] + 1 < Math.max(first, second) + 2) {
			return null;
		}
		int laneA = wall == Dirs.LEFT ? r[0] : r[2];
		Direction across = wall == Dirs.LEFT ? Dirs.RIGHT : Dirs.LEFT;
		int laneB = laneA + Dirs.dx(across);
		int zStart = r[3] - first;
		Stairs.switchback(this.d, laneA, this.body.floorY(k), zStart, Dirs.BACK, rise, across);
		int headZ = r[3] - second - 1;
		return new int[]{laneA, zStart - 1, laneB, headZ, Math.min(laneA, laneB), Math.min(zStart, headZ + 1), Math.max(laneA, laneB), r[3]};
	}

	/**
	 * Opens the floor of storey k (k >= 1) over the middle of the room below, leaving a rim of boards one wide round the
	 * walls: a loft open to the workshop, a store open to its rafters. Call before furnishing the room below, so its
	 * lights are not hung from boards that are gone. {@code keep} cells (x, z pairs) stay floored.
	 */
	public void openLoft(int k, int... keep) {
		if (k < 1 || k >= this.body.storeys()) {
			return;
		}
		int[] up = inner(k);
		int fy = this.body.floorY(k) - 1;
		for (int x = up[0] + 1; x <= up[2] - 1; x++) {
			for (int z = up[1] + 1; z <= up[3] - 1; z++) {
				boolean kept = false;
				for (int i = 0; i + 1 < keep.length; i += 2) {
					kept |= keep[i] == x && keep[i + 1] == z;
				}
				if (!kept) {
					this.d.set(x, fy, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** Exposed joists under storey k + 1's floor. */
	public void joists(int k, Direction.Axis span) {
		if (k + 1 >= this.body.storeys()) {
			return;
		}
		int[] r = inner(k);
		Floors.joists(this.d, r[0], r[1], r[2], r[3], this.body.floorY(k + 1) - 1, span, 2, this.body.floorY(k));
	}

	/** Whether the style builds in masonry (stone, brick, plaster, adobe). */
	public static boolean masonry(Draft d) {
		Habits.WallKind k = d.habits().walls().kind();
		return k == Habits.WallKind.STONE || k == Habits.WallKind.BRICK || k == Habits.WallKind.PLASTER || k == Habits.WallKind.ADOBE;
	}

	/** Whether the style's roofs are flat by habit (desert, mesa). */
	public static boolean flatCulture(Draft d) {
		Habits.Roof r = d.habits().roof();
		double flat = r.weight(RoofForm.FLAT) + r.weight(RoofForm.DOME);
		double total = 0;
		for (RoofForm f : RoofForm.values()) {
			total += r.weight(f);
		}
		return total > 0 && flat / total > 0.6;
	}

	/** A wooden fence post material for the style: its fence if wooden, otherwise its furniture wood's. */
	public static Mat woodFence(Draft d) {
		Mat fence = d.mat(Role.FENCE);
		if (fence.has(Form.FENCE)) {
			return fence;
		}
		return d.mat(Role.FURNITURE);
	}
}
