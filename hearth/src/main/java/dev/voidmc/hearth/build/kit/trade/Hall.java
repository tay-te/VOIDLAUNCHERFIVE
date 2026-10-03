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
			this.deck = Roofs.flat(this.d, tr[0], tr[1], tr[2], tr[3], plate, true);
			this.top = this.deck + 1;
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
		return this;
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
		return 1;
	}

	/**
	 * Storey heights for a building over walls {@code [x0..x1] × [z0..z1]} standing at {@code base}: as many of
	 * {@code storeys} as fit with a roof in {@code form} pitched at least 0.75 (the habit's pitch if lower) whichever way
	 * its ridge runs, an attic knee of two on top when {@code attic} and the roof is pitched, under the plot's height
	 * less {@code reserve}. Never fewer than one storey.
	 */
	public static int[] budget(Draft d, int x0, int z0, int x1, int z1, int base, int[] storeys, boolean attic, RoofForm form, int reserve) {
		boolean flat = form == RoofForm.FLAT || form == RoofForm.DOME || form == RoofForm.CONE;
		Direction.Axis worst = z1 - z0 >= x1 - x0 ? Direction.Axis.X : Direction.Axis.Z;
		int knee = attic && !flat ? 2 : 0;
		int n = fitStoreys(d, x0, z0, x1, z1, base, storeys, knee, form, worst, reserve, 0.75);
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
	 * A flat roof's parapet raised a block at the corners: the crown of an adobe or stone store. Nothing if the roof is
	 * pitched or the plot too low.
	 */
	public Hall crown() {
		if (this.deck == Integer.MIN_VALUE) {
			return this;
		}
		int[] r = this.body.rect(this.body.storeys() - 1);
		int y = this.deck + 1;
		if (y + 1 >= this.d.maxHeight()) {
			return this;
		}
		Mat wall = this.d.mat(Role.WALL);
		Mat cap = this.d.mat(Role.WALL_ACCENT);
		try (var s = this.d.stage(StageKind.ROOF)) {
			for (int[] c : new int[][]{{r[0], r[1]}, {r[2], r[1]}, {r[0], r[3]}, {r[2], r[3]}}) {
				this.d.set(c[0], y, c[1], wall.full());
				this.d.set(c[0], y + 1, c[1], cap.has(Form.SLAB) ? cap.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM)
					: wall.slab(net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
			}
		}
		this.top = Math.max(this.top, y + 1);
		return this;
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
