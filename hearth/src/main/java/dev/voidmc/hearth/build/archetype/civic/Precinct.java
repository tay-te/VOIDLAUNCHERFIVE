package dev.voidmc.hearth.build.archetype.civic;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Arches;
import dev.voidmc.hearth.build.kit.civic.Halls;
import dev.voidmc.hearth.build.kit.civic.Storeys;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.learning.Rite;
import dev.voidmc.hearth.build.kit.learning.Sacra;
import dev.voidmc.hearth.build.kit.learning.Studies;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The quadrangle the religious houses and the university are built round: ranges of two storeys (one where the plot is
 * low) on three or four sides of a court, a church (or a fourth range) on the remaining side, a covered walk on arcades
 * round the court under a lean-to, the court itself a garth of grass and herbs round a well. The ranges' roofs are one
 * surface (hipped at the outer corners, valleyed at the inner ones), so the whole reads as one house from afar; the
 * church rises over it.
 *
 * <p>Layout (the church on the left; mirrored on the right):
 * <pre>
 *   back range  ─────────────────────   (dp - rw .. dp - 1)
 *   church │ walk │ garth │ walk │ far range │ garden
 *   front range with the gate ────────   (zF .. zF + rw - 1)
 *   street rows (0 .. zF - 1)
 * </pre>
 * Each range is divided along its length into rooms of the archetype's programme (a gate passage in the front range
 * always lines up with the court), with doors from the walk; the upper storey is reached by a flight in one of the
 * ground rooms. The rooms are furnished by {@link Kind}.
 */
final class Precinct {
	/** What a room is for. */
	enum Kind {
		GATE, PORTER, GUESTS, CELLAR, BREWHOUSE, REFECTORY, KITCHEN, CHAPTER, SCRIPTORIUM, DORMITORY, INFIRMARY, WORKROOM,
		PARLOUR, LECTURE, LIBRARY, CHAMBERS, STORE, TOWER
	}

	/** A room wanted along a range, its share of the length, and whether the stair up starts in it. */
	record Want(Kind kind, double weight, boolean stair) {
		static Want of(Kind k, double w) {
			return new Want(k, w, false);
		}

		static Want stair(Kind k, double w) {
			return new Want(k, w, true);
		}
	}

	/** One room: its range, storey and run along the range's interior. */
	static final class Room {
		final Kind kind;
		final Range range;
		final int storey;
		int a0;
		int a1;
		/** Beds to put in it (dormitories, chambers, infirmaries, guest rooms). */
		int beds;

		Room(Kind kind, Range range, int storey, int a0, int a1) {
			this.kind = kind;
			this.range = range;
			this.storey = storey;
			this.a0 = a0;
			this.a1 = a1;
		}

		int length() {
			return this.a1 - this.a0 + 1;
		}

		int y() {
			return this.range.body.floorY(this.storey);
		}

		/** Interior rectangle {x0, z0, x1, z1}. */
		int[] rect() {
			Range r = this.range;
			return r.axis == Direction.Axis.X ? new int[]{this.a0, r.c0(), this.a1, r.c1()} : new int[]{r.c0(), this.a0, r.c1(), this.a1};
		}
	}

	/** A range round the court: its wall rectangle, which way the court lies, its body and its rooms per storey. */
	static final class Range {
		final String name;
		final int x0;
		final int z0;
		final int x1;
		final int z1;
		final Direction court;
		final Direction.Axis axis;
		Body body;
		final List<List<Want>> wants = new ArrayList<>();
		final List<List<Room>> rooms = new ArrayList<>();
		/** The gate passage's middle along the axis (front range), or MIN_VALUE. */
		int gate = Integer.MIN_VALUE;
		/** The stair's run along the axis (first step, direction +1/-1), once placed. */
		int stairA = Integer.MIN_VALUE;
		int stairDir;
		/** The range's end walls that face the open air (else they stand against another range or the church). */
		boolean openLow = true;
		boolean openHigh = true;

		Range(String name, int x0, int z0, int x1, int z1, Direction court) {
			this.name = name;
			this.x0 = x0;
			this.z0 = z0;
			this.x1 = x1;
			this.z1 = z1;
			this.court = court;
			this.axis = court.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
		}

		int a0() {
			return (this.axis == Direction.Axis.X ? this.x0 : this.z0) + 1;
		}

		int a1() {
			return (this.axis == Direction.Axis.X ? this.x1 : this.z1) - 1;
		}

		int c0() {
			return (this.axis == Direction.Axis.X ? this.z0 : this.x0) + 1;
		}

		int c1() {
			return (this.axis == Direction.Axis.X ? this.z1 : this.x1) - 1;
		}

		/** The interior row against the court wall. */
		int courtC() {
			return Dirs.dx(this.court) + Dirs.dz(this.court) > 0 ? c1() : c0();
		}

		/** The interior row against the outer wall. */
		int outerC() {
			return Dirs.dx(this.court) + Dirs.dz(this.court) > 0 ? c0() : c1();
		}

		int x(int a, int c) {
			return this.axis == Direction.Axis.X ? a : c;
		}

		int z(int a, int c) {
			return this.axis == Direction.Axis.X ? c : a;
		}

		/** The direction of increasing a. */
		Direction plus() {
			return this.axis == Direction.Axis.X ? Dirs.RIGHT : Dirs.BACK;
		}

		Side courtSide(int k) {
			return this.body.side(k, this.court);
		}

		Side outerSide(int k) {
			return this.body.side(k, this.court.getOpposite());
		}

		int width() {
			return this.c1() - this.c0() + 1;
		}
	}

	final Draft d;
	final Rite rite;
	final Tradition t;
	final int w;
	final int dp;
	final int top;
	/** The church (or the fourth range) on the left. */
	final boolean left;
	final boolean church;
	final int zF;
	final int rw;
	final int cw;
	/** The church's outer margin (a row for buttresses), 0 or 1. */
	final int m;
	final int gs;
	/** The church's (or near range's) wall columns. */
	final int nx0;
	final int nx1;
	/** The block of ranges' x span (front and back ranges). */
	final int bx0;
	final int bx1;
	/** The garden strip's x span (gs wide), else empty. */
	final int gx0;
	final int gx1;
	/** The court inside its walls. */
	final int kx0;
	final int kz0;
	final int kx1;
	final int kz1;
	/** Walk widths: front, back, near, far. */
	final int wF;
	final int wB;
	final int wN;
	final int wR;
	/** The garth inside the arcades. */
	final int gax0;
	final int gaz0;
	final int gax1;
	final int gaz1;
	final boolean frontRange;
	final int[] heights;
	final int plate;
	final List<Range> ranges = new ArrayList<>();
	Range front;
	Range back;
	Range far;
	Range near;
	final Set<Long> keep = new HashSet<>();
	/** The apex row of the gable over the gate (where a bell-cote can stand), or MIN_VALUE. */
	int gateApex = Integer.MIN_VALUE;
	/** A gable over the gate on the street face (off where a tower stands there). */
	boolean gateGable = true;
	/** Sickbeds registered, and the feet of the other beds (for a sickbed where no infirmary had room). */
	int sickbeds;
	final List<int[]> bedFeet = new ArrayList<>();
	/** The jobs whose places have been furnished. */
	final Set<JobType> jobs = java.util.EnumSet.noneOf(JobType.class);
	int skyline;
	Roofs.Roof roof;

	/**
	 * Lays out the precinct over the plot.
	 *
	 * @param church a church on the near side (else a fourth range)
	 * @param garden a walled herb garden beside the far range where the plot is wide enough
	 */
	Precinct(Draft d, boolean church, boolean garden) {
		this.d = d;
		this.rite = Rite.of(d);
		this.t = this.rite.tradition;
		this.church = church;
		this.w = d.frontage();
		this.dp = d.depth();
		this.top = d.maxHeight() - 1;
		this.left = d.hasParam("side") ? d.param("side", "left").equals("left") : d.chance(0.5);
		this.zF = this.dp >= 25 ? 2 : 1;
		this.cw = church ? (this.w >= 27 ? 9 : 7) : 0;
		this.m = church && this.w >= 23 ? 1 : 0;
		// the range's width: as deep as the court can spare
		int rest = this.w - this.m - this.cw;
		int rwant = !church ? (this.w >= 29 && this.dp >= 29 ? 7 : 6) : rest - 7 >= 11 && this.dp >= 27 ? 7 : rest - 6 >= 7 ? 6 : 5;
		this.rw = rwant;
		int courtW = church ? rest - this.rw : this.w - 2 * this.rw;
		this.gs = garden && courtW - 5 >= 8 ? 5 : 0;
		if (this.left) {
			this.nx0 = this.m;
			this.nx1 = church ? this.m + this.cw - 1 : this.rw - 1;
			this.bx0 = church ? this.nx1 + 1 : 0;
			this.bx1 = this.w - 1 - this.gs;
			this.gx0 = this.bx1 + 1;
			this.gx1 = this.w - 1;
			this.kx0 = this.nx1 + 1;
			this.kx1 = this.bx1 - this.rw;
		} else {
			this.nx1 = this.w - 1 - this.m;
			this.nx0 = church ? this.nx1 - this.cw + 1 : this.w - this.rw;
			this.bx1 = church ? this.nx0 - 1 : this.w - 1;
			this.bx0 = this.gs;
			this.gx0 = 0;
			this.gx1 = this.gs - 1;
			this.kx0 = this.bx0 + this.rw;
			this.kx1 = this.nx0 - 1;
		}
		this.frontRange = this.dp - this.zF - 2 * this.rw >= 7;
		this.kz0 = this.frontRange ? this.zF + this.rw : this.zF + 1;
		this.kz1 = this.dp - this.rw - 1;
		int cwid = this.kx1 - this.kx0 + 1;
		int cdep = this.kz1 - this.kz0 + 1;
		this.wN = cwid >= 11 ? 2 : cwid >= 8 ? 1 : 0;
		this.wR = cwid >= 11 ? 2 : 1;
		this.wF = cdep >= 11 ? 2 : cdep >= 8 ? 1 : 0;
		this.wB = cdep >= 11 ? 2 : 1;
		// the garth starts past the pier line on every side with a walk
		int nearW = this.wN > 0 ? this.wN + 1 : 0;
		int farW = this.wR + 1;
		this.gax0 = this.left ? this.kx0 + nearW : this.kx0 + farW;
		this.gax1 = this.left ? this.kx1 - farW : this.kx1 - nearW;
		this.gaz0 = this.kz0 + (this.wF > 0 ? this.wF + 1 : 0);
		this.gaz1 = this.kz1 - (this.wB + 1);
		// storeys: two where the plot is tall enough
		this.heights = this.top >= 15 ? new int[]{5, 4} : new int[]{5};
		int p = 0;
		for (int h : this.heights) {
			p += h;
		}
		this.plate = p - 1;

		// --- the ranges
		Direction toNear = this.left ? Dirs.LEFT : Dirs.RIGHT;
		Direction toFar = toNear.getOpposite();
		int fx0 = church ? this.bx0 : 0;
		int fx1 = church ? this.bx1 : this.w - 1;
		if (!church) {
			fx0 = this.left ? 0 : this.bx0;
			fx1 = this.left ? this.bx1 : this.w - 1;
		}
		int sideZ0 = this.frontRange ? this.zF + this.rw - 1 : this.zF;
		if (this.frontRange) {
			this.front = new Range("front", fx0, this.zF, fx1, this.zF + this.rw - 1, Dirs.BACK);
			this.ranges.add(this.front);
		}
		this.back = new Range("back", fx0, this.dp - this.rw, fx1, this.dp - 1, Dirs.FRONT);
		this.ranges.add(this.back);
		int farX0 = this.left ? this.bx1 - this.rw + 1 : this.bx0;
		this.far = new Range("far", farX0, sideZ0, farX0 + this.rw - 1, this.dp - this.rw, toNear);
		this.ranges.add(this.far);
		if (!church) {
			this.near = new Range("near", this.nx0, sideZ0, this.nx1, this.dp - this.rw, toFar);
			this.ranges.add(this.near);
		}
		for (Range r : this.ranges) {
			r.body = new Body(r.x0, r.z0, r.x1, r.z1, 0, this.heights, false, false);
		}
		// which ends of the front and back ranges stand against the church
		for (Range r : new Range[]{this.front, this.back}) {
			if (r == null) {
				continue;
			}
			if (church) {
				if (this.left) {
					r.openLow = false;
				} else {
					r.openHigh = false;
				}
			}
		}
		this.far.openLow = !this.frontRange;
		this.far.openHigh = false;
		if (this.near != null) {
			this.near.openLow = !this.frontRange;
			this.near.openHigh = false;
		}
	}

	int storeys() {
		return this.heights.length;
	}

	/** The court's middle column (where the gate lines up). */
	int courtX() {
		return (this.kx0 + this.kx1) / 2;
	}

	static long key(int x, int y, int z) {
		return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
	}

	void keep(int x, int y, int z) {
		this.keep.add(key(x, y, z));
	}

	boolean kept(int x, int y, int z) {
		return this.keep.contains(key(x, y, z));
	}

	/** Cells before a door that must stand clear from its foot to its head (cleared in {@link #finish}). */
	private final List<int[]> thresholds = new ArrayList<>();

	/**
	 * Marks the cell (x, z) outside a door as its threshold: whatever a window, a bench or the walk's furniture leaves
	 * there (a sill, a shutter, a box, a seat) is taken away at the end, so the door has somewhere to stand on both sides.
	 */
	void threshold(int x, int z) {
		keep(x, 0, z);
		this.thresholds.add(new int[]{x, z});
	}

	private void clearThresholds() {
		for (int[] c : this.thresholds) {
			for (int y = 0; y <= 2; y++) {
				BlockState s = this.d.get(c[0], y, c[1]);
				if (s == null || s.isAir()) {
					continue;
				}
				net.minecraft.world.level.block.Block b = s.getBlock();
				if (b instanceof net.minecraft.world.level.block.StairBlock || b instanceof net.minecraft.world.level.block.SlabBlock
					|| b instanceof net.minecraft.world.level.block.TrapDoorBlock || b instanceof net.minecraft.world.level.block.FlowerPotBlock
					|| b instanceof net.minecraft.world.level.block.LeavesBlock || b instanceof net.minecraft.world.level.block.LanternBlock
					|| b instanceof net.minecraft.world.level.block.VegetationBlock) {
					this.d.set(c[0], y, c[1], air());
				}
			}
		}
	}

	// ================================================================================================ the shell

	/** Levels the precinct and builds every range's walls, floors and windows. */
	void shell() {
		for (Range r : this.ranges) {
			for (int k = 0; k < storeys(); k++) {
				List<Want> wants = k < r.wants.size() ? r.wants.get(k) : List.of(Want.of(Kind.STORE, 1));
				r.rooms.add(split(r, k, wants));
			}
		}
		Groundwork.level(this.d, 0, 0, this.w - 1, this.dp - 1, 0, true);
		WallKind[] kinds = rangeKinds();
		for (Range r : this.ranges) {
			Storeys.ground(this.d, r.body, false);
			Facade f = facade(r);
			Storeys.walls(this.d, r.body, f, 0, storeys() - 1, kinds, true);
			groundFloor(r.x0 + 1, r.z0 + 1, r.x1 - 1, r.z1 - 1);
			windows(r, f);
			relief(r);
		}
		int qx0 = Math.min(this.church ? Math.min(this.nx0, this.bx0) : 0, this.bx0);
		int qx1 = Math.max(this.church ? Math.max(this.nx1, this.bx1) : this.w - 1, this.bx1);
		this.d.body(qx0, this.zF, qx1, this.dp - 1);
		this.d.eave(this.plate);
	}

	/**
	 * The ranges' walls dressed as a house of the orders builds them, regular and articulated rather than one plane: a
	 * plinth course of the dressing at the foot, a string course at every floor, and pilaster strips of the dressing on the
	 * facade's posts (every fourth column) up each masonry storey, so the long walls read in bays. Only the range's own
	 * walling is replaced (a timber-framed storey keeps its frame, windows and doors are left alone).
	 */
	private void relief(Range r) {
		Stone dress = Stone.dressing(this.d);
		Paint strip = dress.full();
		for (int k = 0; k < storeys(); k++) {
			boolean timber = rangeKinds()[k] == WallKind.TIMBER_FRAME || rangeKinds()[k] == WallKind.LOG;
			int fy = r.body.floorY(k);
			int top = k + 1 < storeys() ? r.body.floorY(k + 1) - 1 : r.body.plateY();
			for (Side s : r.body.sides(k)) {
				// the walling of this storey on this side: the commonest full block between its ends
				java.util.Map<Block, Integer> count = new java.util.HashMap<>();
				for (int i = 1; i < s.last(); i++) {
					BlockState st = this.d.get(s.x(i), fy + 1, s.z(i));
					if (st != null && dev.voidmc.hearth.build.kit.Openings.isWallBlock(this.d, s.x(i), fy + 1, s.z(i))) {
						count.merge(st.getBlock(), 1, Integer::sum);
					}
				}
				if (count.isEmpty()) {
					continue;
				}
				Block walling = java.util.Collections.max(count.entrySet(), java.util.Map.Entry.comparingByValue()).getKey();
				if (walling == strip.at(s.x(1), fy, s.z(1)).getBlock()) {
					continue;
				}
				try (var st = this.d.stage(StageKind.WALLS)) {
					for (int i = 0; i <= s.last(); i++) {
						int x = s.x(i);
						int z = s.z(i);
						int col = s.column(i);
						boolean pilaster = !timber && (i == 0 || i == s.last() || Math.floorMod(col - s.column(0), 4) == 0);
						for (int y = fy - 1; y <= top; y++) {
							BlockState b = this.d.get(x, y, z);
							if (b == null || b.getBlock() != walling) {
								continue;
							}
							boolean course = y == fy - 1 || k == 0 && y == fy;
							if (course || pilaster) {
								this.d.set(x, y, z, strip);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * The windows the facade asks for, storey over storey: plain lights with a sill and a lintel (arched on a masonry
	 * ground storey where the culture arches them, a lattice where it screens them); no shutters or boxes on a house of
	 * the orders, nor on a walk.
	 */
	private void windows(Range r, Facade f) {
		Habits.Windows hw = this.d.habits().windows();
		boolean arched = this.d.chance(Math.max(0.35, hw.arched()));
		for (int k = 0; k < storeys(); k++) {
			int h = Math.min(2, r.body.height(k) - 2);
			dev.voidmc.hearth.build.kit.Openings.WindowStyle style = hw.lattice() ? dev.voidmc.hearth.build.kit.Openings.WindowStyle.LATTICE
				: k == 0 && arched && rangeKinds()[0] != WallKind.LOG ? dev.voidmc.hearth.build.kit.Openings.WindowStyle.ARCHED
				: dev.voidmc.hearth.build.kit.Openings.WindowStyle.PLAIN;
			for (Side s : r.body.sides(k)) {
				for (Facade.Opening o : f.openings(s.out())) {
					if (!s.contains(o.at()) || !s.contains(o.at() + o.width() - 1)) {
						continue;
					}
					dev.voidmc.hearth.build.kit.Openings.window(this.d, s, o.at(), r.body.floorY(k) + 1, o.width(), h + (k == 0 ? 0 : 0), style,
						new dev.voidmc.hearth.build.kit.Openings.WindowOpts(false, false, true, true));
				}
			}
		}
	}

	/**
	 * The ground floor over a rectangle at row -1, laid over whatever the levelling left there (flags of the paving, boards
	 * in the timber traditions).
	 */
	void groundFloor(int x0, int z0, int x1, int z1) {
		Paint p = this.rite.timber() ? this.d.mat(Role.FLOOR).full() : this.d.mat(Role.PAVING).full();
		try (var s = this.d.stage(StageKind.FLOORS)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					this.d.set(x, -1, z, p);
				}
			}
		}
	}

	/** The ranges' walls storey by storey: dressed masonry below, the culture's own above in the timber lands. */
	WallKind[] rangeKinds() {
		WallKind[] k = new WallKind[storeys()];
		for (int i = 0; i < k.length; i++) {
			k[i] = this.rite.ranges;
		}
		// a framed upper storey over the stone where the culture frames its upper storeys (not boards: a house of the
		// orders is no barn)
		if ((this.t == Tradition.TIMBER || this.t == Tradition.ALPINE) && k.length > 1 && this.d.habits().walls().kind(1) == WallKind.TIMBER_FRAME) {
			k[1] = WallKind.TIMBER_FRAME;
		}
		return k;
	}

	/**
	 * The range's facade: the style's posts on every wall, windows only on its outer face, on its court face where the
	 * court is before it, and on ends open to the air.
	 */
	Facade facade(Range r) {
		Facade f = new Facade();
		Set<Integer> parts = new HashSet<>();
		for (List<Room> st : r.rooms) {
			for (Room room : st) {
				parts.add(room.a0 - 1);
				parts.add(room.a1 + 1);
			}
		}
		for (Side s : r.body.sides(0)) {
			Direction out = s.out();
			boolean alongRange = s.axis() == r.axis;
			int c0 = Math.min(s.column(0), s.column(s.last()));
			int c1 = Math.max(s.column(0), s.column(s.last()));
			// a post every fourth column, a light in the middle of each bay between
			for (int c = c0 + 4; c < c1; c += 4) {
				f.post(out, c);
			}
			for (int c = c0 + 2; c < c1 - 1; c += 4) {
				if (alongRange && (parts.contains(c) || r.gate != Integer.MIN_VALUE && Math.abs(c - r.gate) <= 2)) {
					continue;
				}
				if (windowAllowed(r, out, c)) {
					f.add(out, new Facade.Opening(Facade.Kind.WINDOW, c, 1));
				}
			}
		}
		return f;
	}

	private boolean windowAllowed(Range r, Direction out, int col) {
		if (out == r.court.getOpposite()) {
			return true;
		}
		if (out == r.court) {
			// the court lies before this run of the wall (not another range's room)
			if (r.axis == Direction.Axis.X) {
				return col >= this.kx0 + 1 && col <= this.kx1 - 1;
			}
			return col >= this.kz0 + 1 && col <= this.kz1 - 1;
		}
		boolean low = out == (r.axis == Direction.Axis.X ? Dirs.LEFT : Dirs.FRONT);
		return low ? r.openLow : r.openHigh;
	}

	// ================================================================================================ the rooms

	/** Divides every range into its rooms, builds the partitions, the gate, the doors to the walk and the stairs. */
	void rooms() {
		// the main entrance is the gate, declared before any door declares its own
		this.d.entrance(courtX(), 0, this.zF - 1);
		for (Range r : this.ranges) {
			if (storeys() > 1) {
				stair(r);
			}
			for (int k = 0; k < storeys(); k++) {
				partitions(r, k);
			}
			if (r.gate != Integer.MIN_VALUE) {
				gate(r);
			}
			walkDoors(r);
		}
		if (storeys() > 1) {
			for (Range r : this.ranges) {
				if (r.axis == Direction.Axis.Z) {
					cornerDoors(r);
				}
			}
		}
		if (!this.frontRange) {
			frontWall();
		}
	}

	/**
	 * Doors upstairs through a side range's end walls into the front and back ranges' upper rooms, so the upper floors
	 * run round the court (one stair serves a range that has none of its own).
	 */
	private void cornerDoors(Range r) {
		Block door = doorBlock();
		for (int k = 1; k < storeys(); k++) {
			int y = r.body.floorY(k);
			int c = (r.c0() + r.c1() + (r.courtC() > r.outerC() ? 1 : 0)) / 2;
			for (int end : new int[]{-1, 1}) {
				if (end < 0 && !this.frontRange) {
					continue;
				}
				int wall = end < 0 ? r.a0() - 1 : r.a1() + 1;
				int beyond = wall + end;
				int inside = wall - end;
				int x = r.x(wall, c);
				int z = r.z(wall, c);
				if (!this.d.isEmpty(r.x(beyond, c), y, r.z(beyond, c)) || !this.d.isEmpty(r.x(inside, c), y, r.z(inside, c))
					|| !this.d.isSolid(r.x(beyond, c), y - 1, r.z(beyond, c))) {
					continue;
				}
				this.d.inStage(StageKind.OPENINGS, () -> this.d.door(x, y, z, door, r.plus(), DoorHingeSide.LEFT));
				keepAround(x, y, z, r.plus());
			}
		}
	}

	/** Splits a storey of a range into rooms by weight (a GATE is three wide on the court's middle). */
	private List<Room> split(Range r, int k, List<Want> wants) {
		List<Room> out = new ArrayList<>();
		int a0 = r.a0();
		int a1 = r.a1();
		int gi = -1;
		for (int i = 0; i < wants.size(); i++) {
			if (wants.get(i).kind() == Kind.GATE) {
				gi = i;
			}
		}
		if (gi >= 0 && k == 0) {
			int g = courtX();
			r.gate = g;
			List<Want> lo = new ArrayList<>(wants.subList(0, gi));
			List<Want> hi = new ArrayList<>(wants.subList(gi + 1, wants.size()));
			// a side too short for a room gives its rooms to the other side
			if (g - 3 - a0 + 1 < 3) {
				hi.addAll(0, lo);
				lo.clear();
			} else if (a1 - (g + 3) + 1 < 3) {
				lo.addAll(hi);
				hi.clear();
			}
			out.addAll(segment(r, k, a0, g - 3, lo));
			out.add(new Room(Kind.GATE, r, k, g - 1, g + 1));
			out.addAll(segment(r, k, g + 3, a1, hi));
		} else if (gi >= 0 && r.gate != Integer.MIN_VALUE) {
			// the room over the gate (a tower's room): between walls on the passage's walls
			int g = r.gate;
			out.addAll(segment(r, k, a0, g - 3, wants.subList(0, gi)));
			out.add(new Room(Kind.TOWER, r, k, g - 1, g + 1));
			out.addAll(segment(r, k, g + 3, a1, wants.subList(gi + 1, wants.size())));
		} else {
			List<Want> ws = new ArrayList<>(wants);
			ws.removeIf(x -> x.kind() == Kind.GATE);
			out.addAll(segment(r, k, a0, a1, ws));
		}
		return out;
	}

	/** Rooms over interior cells s0..s1 by weight, a partition between each; rooms under three long are merged away. */
	private List<Room> segment(Range r, int k, int s0, int s1, List<Want> wants) {
		List<Room> out = new ArrayList<>();
		if (s1 < s0 || wants.isEmpty()) {
			if (s1 >= s0) {
				out.add(new Room(Kind.STORE, r, k, s0, s1));
			}
			return out;
		}
		List<Want> ws = new ArrayList<>(wants);
		while (ws.size() > 1 && (s1 - s0 + 1 - (ws.size() - 1)) < 3 * ws.size()) {
			// too short for them all: the lightest goes (but never the stair's room)
			int drop = -1;
			double least = Double.MAX_VALUE;
			for (int i = 0; i < ws.size(); i++) {
				if (!ws.get(i).stair() && ws.get(i).weight() < least) {
					least = ws.get(i).weight();
					drop = i;
				}
			}
			ws.remove(drop < 0 ? ws.size() - 1 : drop);
		}
		int n = ws.size();
		int free = s1 - s0 + 1 - (n - 1);
		double sum = 0;
		for (Want x : ws) {
			sum += x.weight();
		}
		int a = s0;
		int used = 0;
		for (int i = 0; i < n; i++) {
			int len = i == n - 1 ? free - used : Math.max(3, (int) Math.round(free * ws.get(i).weight() / sum));
			len = Math.min(len, free - used - 3 * (n - 1 - i));
			Room room = new Room(ws.get(i).kind(), r, k, a, a + len - 1);
			out.add(room);
			if (ws.get(i).stair()) {
				r.stairA = -1;   // marks the room; placed by stair()
				room.beds = -1;
			}
			used += len;
			a += len + 1;
		}
		return out;
	}

	/** The partition walls of a storey (with a door through each), skipping the stair's run upstairs. */
	private void partitions(Range r, int k) {
		List<Room> rs = r.rooms.get(k);
		int y0 = r.body.floorY(k);
		int y1 = k == storeys() - 1 ? this.plate : r.body.floorY(k + 1) - 2;
		Mat wall = this.d.mat(Role.WALL);
		WallKind kind = rangeKinds()[k];
		Direction.Axis cross = r.axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		Paint paint = kind == WallKind.LOG ? wall.log(cross) : kind == WallKind.PLANK && this.d.mat(Role.GABLE).has(Form.FULL) ? this.d.mat(Role.GABLE).full()
			: wall.full();
		Block door = this.d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		for (int i = 0; i + 1 < rs.size(); i++) {
			int a = rs.get(i).a1 + 1;
			// the door: the middle of the cross section, toward the court
			int cmid = (r.c0() + r.c1() + (r.courtC() > r.outerC() ? 1 : 0)) / 2;
			try (var s = this.d.stage(StageKind.WALLS)) {
				for (int c = r.c0(); c <= r.c1(); c++) {
					for (int y = y0; y <= y1; y++) {
						this.d.set(r.x(a, c), y, r.z(a, c), paint);
					}
				}
			}
			int dx = r.x(a, cmid);
			int dz = r.z(a, cmid);
			boolean gateSide = rs.get(i).kind == Kind.GATE || rs.get(i + 1).kind == Kind.GATE;
			if (gateSide && k == 0) {
				// the gate passage has its own door into the porter's room, toward the court end
				continue;
			}
			this.d.inStage(StageKind.OPENINGS, () -> this.d.door(dx, y0, dz, door, r.plus(), DoorHingeSide.LEFT));
			keepAround(dx, y0, dz, r.plus());
		}
		// doors from the gate passage into the rooms either side (the porter's lodge, and whatever lies beyond the court)
		if (k == 0 && r.gate != Integer.MIN_VALUE) {
			for (int side : new int[]{-1, 1}) {
				int a = r.gate + 2 * side;
				if (a <= r.a0() - 1 || a >= r.a1() + 1) {
					continue;
				}
				int c = r.courtC();
				int dx = r.x(a, c);
				int dz = r.z(a, c);
				this.d.inStage(StageKind.OPENINGS, () -> this.d.door(dx, y0, dz, door, r.plus(), DoorHingeSide.LEFT));
				keepAround(dx, y0, dz, r.plus());
			}
		}
	}

	/** Keeps the cells either side of a door (along {@code through}) free. */
	private void keepAround(int x, int y, int z, Direction through) {
		keep(x, y, z);
		keep(x + Dirs.dx(through), y, z + Dirs.dz(through));
		keep(x - Dirs.dx(through), y, z - Dirs.dz(through));
	}

	/**
	 * The flight up: along the outer wall of the ground room marked for it, rising so its open side faces the court
	 * (where the rail goes). Upstairs, partitions are moved off the stairwell and the landing.
	 */
	private void stair(Range r) {
		int rise = r.body.height(0);
		Room host = null;
		for (Room room : r.rooms.get(0)) {
			if (room.beds == -1) {
				room.beds = 0;
				if (room.length() >= rise + 1) {
					host = room;
				}
			}
		}
		if (host == null) {
			// the longest room
			for (Room room : r.rooms.get(0)) {
				if (room.kind != Kind.GATE && room.length() >= rise + 1 && (host == null || room.length() > host.length())) {
					host = room;
				}
			}
		}
		if (host == null) {
			ladder(r);
			return;
		}
		// climbing toward dir, the rail (on the climber's right) faces the court; by the gate, climbing away from it
		Direction pref = Dirs.right(r.plus()) == r.court ? r.plus() : r.plus().getOpposite();
		if (r.gate != Integer.MIN_VALUE) {
			pref = host.a1 < r.gate ? r.plus().getOpposite() : r.plus();
		}
		Direction dir = null;
		int sa = 0;
		int landing = 0;
		int sgn = 0;
		for (Direction cand : new Direction[]{pref, pref.getOpposite()}) {
			int sg = cand == r.plus() ? 1 : -1;
			int s0 = sg > 0 ? host.a0 + 1 : host.a1 - 1;
			int l = s0 + sg * rise;
			if (s0 + sg * (rise - 1) < host.a0 || s0 + sg * (rise - 1) > host.a1 || l < r.a0() || l > r.a1() || towerWall(r, l)) {
				continue;
			}
			dir = cand;
			sa = s0;
			landing = l;
			sgn = sg;
			break;
		}
		if (dir == null) {
			return;
		}
		int c = r.outerC();
		Storeys.flight(this.d, r.body, 0, r.x(sa, c), r.z(sa, c), dir);
		r.stairA = sa;
		r.stairDir = sgn;
		int y0 = r.body.floorY(0);
		int y1 = r.body.floorY(1);
		keep(r.x(sa - sgn, c), y0, r.z(sa - sgn, c));
		keep(r.x(sa - sgn, c + (r.courtC() > c ? 1 : -1)), y0, r.z(sa - sgn, c + (r.courtC() > c ? 1 : -1)));
		for (int i = 0; i < rise; i++) {
			keep(r.x(sa + sgn * i, c), y0, r.z(sa + sgn * i, c));
			keep(r.x(sa + sgn * i, c), y1, r.z(sa + sgn * i, c));
		}
		keep(r.x(landing, c), y1, r.z(landing, c));
		keep(r.x(landing + sgn, c), y1, r.z(landing + sgn, c));
		int cin = c + (r.courtC() > c ? 1 : -1);
		keep(r.x(landing, cin), y1, r.z(landing, cin));
		// upstairs: no partition over the stairwell or the landing
		List<Room> up = r.rooms.get(1);
		int lo = Math.min(sa, landing + sgn) - 1;
		int hi = Math.max(sa, landing + sgn) + 1;
		for (int i = 0; i + 1 < up.size(); i++) {
			int part = up.get(i).a1 + 1;
			if (part >= lo && part <= hi && up.get(i).kind != Kind.TOWER && up.get(i + 1).kind != Kind.TOWER) {
				// move it past the stair's far end, else merge the two rooms
				int moved = sgn > 0 ? hi + 1 : lo - 1;
				Room a = up.get(i);
				Room b = up.get(i + 1);
				if (moved - 1 - a.a0 + 1 >= 3 && b.a1 - (moved + 1) + 1 >= 3) {
					a.a1 = moved - 1;
					b.a0 = moved + 1;
				} else {
					a.a1 = b.a1;
					up.remove(i + 1);
					i--;
				}
			}
		}
	}

	/**
	 * Where no ground room is long enough for a flight: a ladder against the outer wall of the longest room up through a
	 * hatch in the floor above.
	 */
	private void ladder(Range r) {
		List<Room> rs = new ArrayList<>(r.rooms.get(0));
		rs.removeIf(x -> x.kind == Kind.GATE);
		rs.sort((a, b) -> Integer.compare(b.length(), a.length()));
		int y1 = r.body.floorY(1) - 1;
		int up = r.body.floorY(1);
		int c = r.outerC();
		int cin = c + (r.courtC() > c ? 1 : -1);
		int cw = c + (r.courtC() > c ? -1 : 1);
		for (Room room : rs) {
			for (int a = room.a0; a <= room.a1; a++) {
				int x = r.x(a, c);
				int z = r.z(a, c);
				int fx = r.x(a, cin);
				int fz = r.z(a, cin);
				int wx = r.x(a, cw);
				int wz = r.z(a, cw);
				boolean ok = !kept(x, 0, z) && !kept(fx, 0, fz) && this.d.isEmpty(fx, 0, fz) && this.d.isEmpty(fx, up, fz) && this.d.isEmpty(x, up, z)
					&& this.d.isEmpty(x, up + 1, z) && towerWall(r, a) == false;
				for (int y = 0; y <= y1 && ok; y++) {
					ok = (y == y1 || this.d.isEmpty(x, y, z)) && dev.voidmc.hearth.build.kit.Openings.isWallBlock(this.d, wx, y, wz);
				}
				if (!ok) {
					continue;
				}
				dev.voidmc.hearth.build.kit.Stairs.ladder(this.d, x, z, 0, y1, r.court);
				r.stairA = a;
				for (int y = 0; y <= up + 1; y++) {
					keep(x, y, z);
				}
				keep(fx, 0, fz);
				keep(fx, up, fz);
				return;
			}
		}
	}

	/** Whether an upstairs partition beside the room over the gate stands at a. */
	private boolean towerWall(Range r, int a) {
		if (r.rooms.size() < 2) {
			return false;
		}
		for (Room room : r.rooms.get(1)) {
			if (room.kind == Kind.TOWER && (a == room.a0 - 1 || a == room.a1 + 1 || a >= room.a0 && a <= room.a1)) {
				return true;
			}
		}
		return false;
	}

	/** The gate passage: arches through both walls, paved, a lamp under the vault, the gates. */
	private void gate(Range r) {
		Stone dress = Stone.dressing(this.d);
		int g = r.gate;
		Side outer = r.outerSide(0);
		Side inner = r.courtSide(0);
		int hh = Math.min(4, r.body.height(0) - 1);
		Details.arch(this.d, outer, Math.min(outer.index(g - 1), outer.index(g + 1)), 3, 0, hh, dress, null);
		Details.arch(this.d, inner, Math.min(inner.index(g - 1), inner.index(g + 1)), 3, 0, hh, dress, null);
		Paint paving = this.d.mat(Role.PAVING).full();
		try (var s = this.d.stage(StageKind.FLOORS)) {
			for (int a = g - 1; a <= g + 1; a++) {
				for (int c = r.c0() - 1; c <= r.c1() + 1; c++) {
					this.d.set(r.x(a, c), -1, r.z(a, c), paving);
				}
			}
		}
		for (int a = g - 1; a <= g + 1; a++) {
			for (int c = r.c0() - 1; c <= r.c1() + 1; c++) {
				for (int y = 0; y < 3; y++) {
					keep(r.x(a, c), y, r.z(a, c));
				}
			}
		}
		this.d.poi(PoiType.GATE, r.x(g, r.outerC()), 0, r.z(g, r.outerC()), r.court.getOpposite());
		this.d.poi(PoiType.DOOR, r.x(g, r.outerC() + (r.outerC() < r.courtC() ? -1 : 1)), 0, r.z(g, r.outerC() + (r.outerC() < r.courtC() ? -1 : 1)),
			r.court.getOpposite());
	}

	/** A door from the walk into every ground room that has the court before it. */
	private void walkDoors(Range r) {
		Side s = r.courtSide(0);
		int lo = r.axis == Direction.Axis.X ? this.kx0 : this.kz0;
		int hi = r.axis == Direction.Axis.X ? this.kx1 : this.kz1;
		for (Room room : r.rooms.get(0)) {
			if (room.kind == Kind.GATE) {
				continue;
			}
			int a0 = Math.max(room.a0, lo + 1);
			int a1 = Math.min(room.a1, hi - 1);
			if (a1 < a0) {
				continue;
			}
			// the door off the stair's foot and away from the room's ends
			int at = (a0 + a1) / 2;
			for (int tries = 0; tries < room.length() && blockedDoor(r, at); tries++) {
				at = at + 1 > a1 ? a0 : at + 1;
			}
			if (blockedDoor(r, at)) {
				continue;
			}

			Openings_door(s, at);
			int cx = r.x(at, r.courtC());
			int cz = r.z(at, r.courtC());
			// the cells inside the door and the walk outside it stay free
			keep(cx, 0, cz);
			keep(cx - Dirs.dx(r.court), 0, cz - Dirs.dz(r.court));
			threshold(cx + 2 * Dirs.dx(r.court), cz + 2 * Dirs.dz(r.court));
		}
	}

	private boolean blockedDoor(Range r, int a) {
		int c = r.courtC();
		return kept(r.x(a, c), 0, r.z(a, c)) && !this.d.isEmpty(r.x(a, c), 0, r.z(a, c)) || !this.d.isEmpty(r.x(a, c), 0, r.z(a, c))
			|| r.stairA != Integer.MIN_VALUE && r.width() <= 1;
	}

	private void Openings_door(Side s, int col) {
		dev.voidmc.hearth.build.kit.Openings.door(this.d, s, col, 0, 1, new dev.voidmc.hearth.build.kit.Openings.DoorOpts(false, false, false));
	}

	/** Where there is no front range: a wall across the front of the court with a gateway on its middle. */
	private void frontWall() {
		int z = this.zF;
		Stone dress = Stone.dressing(this.d);
		Paint body = Stone.panel(this.d).full();
		int g = courtX();
		try (var s = this.d.stage(StageKind.WALLS)) {
			for (int x = this.kx0; x <= this.kx1; x++) {
				int ground = this.d.site().ground(x, z);
				for (int y = Math.min(0, ground) - 1; y <= 2; y++) {
					this.d.set(x, y, z, y < 0 ? this.d.mat(Role.FOUNDATION).full() : y == 2 ? dress.full() : body);
				}
				if (dress.has(Form.SLAB)) {
					this.d.set(x, 3, z, dress.slab(SlabType.BOTTOM));
				}
			}
		}
		Side s = new Side(Dirs.FRONT, this.kx0, z, this.kx1 - this.kx0 + 1);
		Details.arch(this.d, s, Math.min(s.index(g - 1), s.index(g + 1)), 3, 0, 3, dress, null);
		try (var st = this.d.stage(StageKind.WALLS)) {
			for (int x = g - 2; x <= g + 2; x++) {
				this.d.set(x, 3, z, dress.full());
			}
			this.d.set(g, 4, z, dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
		}
		for (int x = g - 1; x <= g + 1; x++) {
			for (int y = 0; y < 3; y++) {
				keep(x, y, z);
				keep(x, y, z + 1);
			}
		}
		this.d.poi(PoiType.GATE, g, 0, z, Dirs.FRONT);
	}

	// ================================================================================================ the roofs

	/**
	 * One roof over all the ranges: a hip over each, merged, so the outer corners are hipped and the inner ones valleys
	 * (no eave where a range stands against the church). Flat roofs behind one parapet in the dry lands. Returns the ridge.
	 */
	int roofs() {
		if (this.rite.flat()) {
			return flatRoofs();
		}
		Habits.RoofForm form = this.rite.curved() ? Habits.RoofForm.PAGODA : Habits.RoofForm.HIP;
		Roofs.Opts o = Roofs.Opts.of(this.d).withEaves(1, 1);
		double half = this.rw / 2.0 + 1;
		double p = this.rite.pitch;
		while (p > 0.5 && this.plate + 1 + p * half > this.top - 1) {
			p -= 0.5;
		}
		o = o.withPitch(p);
		Roofs.Profile profile = this.rite.curved() ? Roofs.Profile.curved(Math.max(1, p)) : Roofs.Profile.straight(p);
		Range main = this.front != null ? this.front : this.back;
		boolean pl = this.church && this.left;
		boolean pr = this.church && !this.left;
		Roofs.Plan plan = Roofs.plan(this.d, main.x0, main.z0, main.x1, main.z1, this.plate, form, Direction.Axis.X, o, pl, pr);
		// a gable over the gate on the street face, so the way in reads from the road
		if (this.gateGable && main == this.front && this.front.gate != Integer.MIN_VALUE && !this.rite.curved() && p >= 1.0) {
			Side face = this.front.outerSide(storeys() - 1);
			plan.crossGable(face, this.front.gate - 2, 5, this.d);
		}
		for (Range r : this.ranges) {
			if (r == main) {
				continue;
			}
			int e = 1;
			boolean noLow = this.church && r.axis == Direction.Axis.X && this.left;
			boolean noHigh = this.church && r.axis == Direction.Axis.X && !this.left;
			int nx0 = r.x0 - (noLow ? 0 : e);
			int nx1 = r.x1 + (noHigh ? 0 : e);
			int nz0 = r.z0 - e;
			int nz1 = r.z1 + e;
			plan.add(Roofs.hip(nx0, nz0, nx1, nz1, profile), Math.max(0, nx0), Math.max(-1, nz0), Math.min(this.w - 1, nx1), Math.min(this.dp - 1, nz1));
		}
		Roofs.Built b = plan.draw(this.d);
		this.roof = b.roof();
		if (this.gateGable && main == this.front && this.front.gate != Integer.MIN_VALUE && !this.rite.curved() && p >= 1.0) {
			int apex = this.roof.surfaceY(this.front.gate, this.front.z0);
			if (apex != Integer.MIN_VALUE && apex + 5 <= this.top) {
				this.gateApex = apex;
			}
		}
		// chimneys: the kitchen's and the brewhouse's stacks up the outer wall through the eaves
		for (Range r : this.ranges) {
			for (Room room : r.rooms.get(0)) {
				if (room.kind != Kind.KITCHEN && room.kind != Kind.BREWHOUSE && room.kind != Kind.INFIRMARY) {
					continue;
				}
				int a = (room.a0 + room.a1) / 2;
				int oc = r.outerC() + (r.outerC() < r.courtC() ? -1 : 1);
				int x = r.x(a, oc);
				int z = r.z(a, oc);
				if (this.roof.covers(x, z)) {
					this.skyline = Math.max(this.skyline, dev.voidmc.hearth.build.kit.Chimneys.stack(this.d, x, z, this.plate + 1, this.roof,
						Math.min(this.top, this.roof.ridgeY + 1)));
				}
			}
		}
		// the walls under the merged surface where it rose over another range's plate (the inner corners)
		for (Range r : this.ranges) {
			if (r == main) {
				continue;
			}
			fillUnder(r);
		}
		return this.roof.ridgeY;
	}

	/** Fills a range's wall line up to the roof wherever the roof stands higher than its plate. */
	private void fillUnder(Range r) {
		Mat wall = this.d.mat(Role.WALL);
		try (var s = this.d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(r.x0, r.z0, r.x1, r.z1)) {
				for (int i = 0; i < side.length(); i++) {
					int x = side.x(i);
					int z = side.z(i);
					if (!this.roof.covers(x, z)) {
						continue;
					}
					int under = this.roof.bottomY(x, z);
					for (int y = this.plate + 1; y < under; y++) {
						if (this.d.isAbsent(x, y, z)) {
							this.d.set(x, y, z, wall.full());
						}
					}
				}
			}
		}
	}

	/** One deck over every range behind a parapet on the outer edge, beam ends, spouts; stakes on the steppe. */
	private int flatRoofs() {
		Set<Long> u = new HashSet<>();
		for (Range r : this.ranges) {
			for (int z = r.z0; z <= r.z1; z++) {
				for (int x = r.x0; x <= r.x1; x++) {
					u.add(dev.voidmc.hearth.build.kit.landmark.Shapes.cell(x, z));
				}
			}
		}
		Mat deck = this.d.mat(Role.ROOF);
		Mat wall = this.d.mat(Role.WALL);
		Stone dress = Stone.dressing(this.d);
		int y = this.plate;
		try (var s = this.d.stage(StageKind.ROOF)) {
			for (long c : u) {
				int x = (int) (c >> 32);
				int z = (int) c;
				boolean edge = false;
				for (Direction dir : Dirs.HORIZONTAL) {
					if (!u.contains(dev.voidmc.hearth.build.kit.landmark.Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
						edge = true;
					}
				}
				if (edge) {
					this.d.set(x, y, z, wall.full());
					if (y + 1 <= this.top) {
						this.d.set(x, y + 1, z, wall.full());
					}
					if (y + 2 <= this.top) {
						this.d.set(x, y + 2, z, dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
					}
				} else if (this.d.isAbsent(x, y, z) || isWallTop(x, y, z)) {
					this.d.set(x, y, z, deck.full());
				}
			}
		}
		// the corners of the parapet raised, a stake or a pinnacle on each (the steppe's), spouts out of the sides
		int ridge = y + 2;
		for (Range r : this.ranges) {
			for (int[] c : new int[][]{{r.x0, r.z0}, {r.x1, r.z0}, {r.x0, r.z1}, {r.x1, r.z1}}) {
				boolean outer = false;
				for (int[] o : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
					if (!u.contains(dev.voidmc.hearth.build.kit.landmark.Shapes.cell(c[0] + o[0], c[1] + o[1]))) {
						outer = true;
					}
				}
				if (!outer || y + 3 > this.top) {
					continue;
				}
				try (var s = this.d.stage(StageKind.ROOF)) {
					this.d.set(c[0], y + 2, c[1], dress.full());
					if (this.t == Tradition.STEPPE) {
						this.d.set(c[0], y + 3, c[1], this.d.mat(Role.FRAME).has(Form.FENCE) ? this.d.mat(Role.FRAME).fence() : Sacra.fence(this.d));
					} else {
						this.d.set(c[0], y + 3, c[1], dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
					}
				}
				ridge = Math.max(ridge, y + 3);
			}
			// beam ends under the parapet on the outer face
			Side out = r.outerSide(storeys() - 1);
			for (int i = 2; i < out.length() - 2; i += 2) {
				int ox = out.outX(i, 1);
				int oz = out.outZ(i, 1);
				if (Walls.inPlot(this.d, ox, oz) && this.d.isAbsent(ox, y - 1, oz)) {
					this.d.inStage(StageKind.ROOF, () -> this.d.set(ox, y - 1, oz, this.d.mat(Role.BEAM).log(out.out().getAxis())));
				}
			}
		}
		return ridge;
	}

	private boolean isWallTop(int x, int y, int z) {
		return false;
	}

	// ================================================================================================ the walk

	/**
	 * The covered walk round the court: arcades on the pier lines (a low wall in the bays, open in the middle bay of
	 * each side), the walk paved, a lean-to over it rising to the ranges' walls (a flat deck in the dry lands).
	 */
	void walk() {
		int px0 = this.gax0 - 1;
		int px1 = this.gax1 + 1;
		int pz0 = this.gaz0 - 1;
		int pz1 = this.gaz1 + 1;
		boolean nearWalk = this.wN > 0;
		boolean frontWalk = this.wF > 0;
		// walk paving: every court cell outside the garth
		Paint paving = this.d.mat(Role.PAVING).full();
		try (var s = this.d.stage(StageKind.FLOORS)) {
			for (int z = this.kz0; z <= this.kz1; z++) {
				for (int x = this.kx0; x <= this.kx1; x++) {
					if (!inGarth(x, z)) {
						this.d.set(x, -1, z, paving);
					}
				}
			}
		}
		Arches.Kind kind = this.rite.arcade;
		Paint pier = Arches.pierPaint(this.d, kind);
		int atop = 3;
		// the pier lines: each side of the pier rectangle that has a walk behind it
		List<Side> lines = new ArrayList<>();
		for (Side s : Side.of(px0, pz0, px1, pz1)) {
			Direction out = s.out();
			boolean has = out == Dirs.BACK || out == Dirs.FRONT && frontWalk || out == (this.left ? Dirs.LEFT : Dirs.RIGHT) && nearWalk
				|| out == (this.left ? Dirs.RIGHT : Dirs.LEFT);
			if (!has) {
				continue;
			}
			// clip the line to the court (a side without a walk at an end runs to the wall)
			int i0 = 0;
			int i1 = s.last();
			while (i0 <= i1 && !inCourt(s.x(i0), s.z(i0))) {
				i0++;
			}
			while (i1 >= i0 && !inCourt(s.x(i1), s.z(i1))) {
				i1--;
			}
			if (i1 - i0 < 2) {
				continue;
			}
			Side line = s.sub(i0, i1 - i0 + 1);
			lines.add(line);
			int[] piers = Arches.piers(0, line.last(), 3);
			Arches.arcade(this.d, line, piers, 0, atop, kind, pier, null);
			// a low wall in the bays, the middle bay left open as the way into the garth
			int mid = line.middle();
			int openBay = -1;
			for (int k = 0; k + 1 < piers.length; k++) {
				if (piers[k] <= mid && piers[k + 1] >= mid) {
					openBay = k;
				}
			}
			Paint low = Arches.timber(kind) ? (this.d.mat(Role.RAILING).has(Form.FENCE) ? this.d.mat(Role.RAILING).fence() : Sacra.fence(this.d))
				: Stone.dressing(this.d).has(Form.SLAB) ? Stone.dressing(this.d).slab(SlabType.BOTTOM) : Stone.dressing(this.d).full();
			for (int k = 0; k + 1 < piers.length; k++) {
				for (int i = piers[k] + 1; i < piers[k + 1]; i++) {
					int x = line.x(i);
					int z = line.z(i);
					if (k == openBay) {
						for (int y = 0; y < 3; y++) {
							keep(x, y, z);
						}
						keep(x + Dirs.dx(line.out()), 0, z + Dirs.dz(line.out()));
						keep(x - Dirs.dx(line.out()), 0, z - Dirs.dz(line.out()));
						continue;
					}
					this.d.inStage(Arches.timber(kind) ? StageKind.FRAME : StageKind.WALLS, () -> this.d.set(x, 0, z, low));
				}
			}
		}
		// the roof over the walk
		if (this.rite.flat()) {
			Mat deck = this.d.mat(Role.ROOF);
			try (var s = this.d.stage(StageKind.ROOF)) {
				for (int z = this.kz0; z <= this.kz1; z++) {
					for (int x = this.kx0; x <= this.kx1; x++) {
						if (inGarth(x, z)) {
							continue;
						}
						this.d.set(x, atop, z, deck.full());
						boolean onLine = x == px0 || x == px1 || z == pz0 || z == pz1;
						if (onLine && this.d.isAbsent(x, atop + 1, z)) {
							Stone dress = Stone.dressing(this.d);
							this.d.set(x, atop + 1, z, dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : deck.full());
						}
					}
				}
			}
		} else {
			double lean = 0.5;
			boolean eaves = this.gax1 - this.gax0 >= 3 && this.gaz1 - this.gaz0 >= 3;
			Roofs.Field f = (x, z) -> {
				if (!inCourt(x, z)) {
					return Double.NaN;
				}
				if (inGarth(x, z)) {
					// the eave hangs over the garth's border row where a walk stands behind it
					boolean border = x == this.gax0 && garthEdge(Dirs.LEFT) || x == this.gax1 && garthEdge(Dirs.RIGHT)
						|| z == this.gaz0 && garthEdge(Dirs.FRONT) || z == this.gaz1 && garthEdge(Dirs.BACK);
					return eaves && border ? 1 : Double.NaN;
				}
				int dg = Math.max(Math.max(this.gax0 - x, x - this.gax1), Math.max(this.gaz0 - z, z - this.gaz1));
				return 1 + lean * Math.max(1, dg);
			};
			Roofs.raster(this.d, f, atop, this.kx0, this.kz0, this.kx1, this.kz1, this.d.mat(Role.ROOF), this.d.mat(Role.ROOF_TRIM), Habits.Ridge.SLAB);
		}
	}

	/** Whether the garth's edge on that side has a walk (and so an eave over it). */
	private boolean garthEdge(Direction side) {
		if (side == Dirs.FRONT) {
			return this.wF > 0;
		}
		if (side == Dirs.BACK) {
			return true;
		}
		boolean isNear = side == (this.left ? Dirs.LEFT : Dirs.RIGHT);
		return !isNear || this.wN > 0;
	}

	boolean inGarth(int x, int z) {
		return x >= this.gax0 && x <= this.gax1 && z >= this.gaz0 && z <= this.gaz1;
	}

	boolean inCourt(int x, int z) {
		return x >= this.kx0 && x <= this.kx1 && z >= this.kz0 && z <= this.kz1;
	}

	// ================================================================================================ the garth

	/**
	 * The garth: grass, a cross of paths from the open bays to the middle, a well there (or the archetype's piece), and
	 * four beds of herbs and flowers in the quarters; a tree in one quarter where it is large.
	 */
	void garth(boolean well) {
		int x0 = this.gax0;
		int x1 = this.gax1;
		int z0 = this.gaz0;
		int z1 = this.gaz1;
		if (x1 - x0 < 1 || z1 - z0 < 1) {
			return;
		}
		int cx = (x0 + x1) / 2;
		int cz = (z0 + z1) / 2;
		List<int[]> paths = new ArrayList<>();
		paths.addAll(Garden.line(x0, cz, x1, cz));
		paths.addAll(Garden.line(cx, z0, cx, z1));
		Garden.path(this.d, paths, 0);
		for (int[] p : paths) {
			keep(p[0], 0, p[1]);
		}
		if (well && x1 - x0 >= 2 && z1 - z0 >= 2) {
			wellhead(cx, cz);
		}
		List<int[]> beds = new ArrayList<>();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1 || x == cx || z == cz) {
					continue;
				}
				// a margin of grass along the walk
				if (x == x0 || x == x1 || z == z0 || z == z1) {
					if (x1 - x0 >= 6 && z1 - z0 >= 6) {
						continue;
					}
				}
				beds.add(new int[]{x, z});
			}
		}
		Garden.flowerBed(this.d, beds, 0, 0.55);
		if (x1 - x0 >= 8 && z1 - z0 >= 8) {
			int tx = cx + (x1 - cx) / 2 + 1;
			int tz = cz + (z1 - cz) / 2 + 1;
			int th = Math.min(5, this.plate);
			// the levelling left air over the garth: free those cells so the crown can take them
			for (int y = th - 2; y <= th + 1; y++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int dx = -2; dx <= 2; dx++) {
						BlockState st = this.d.get(tx + dx, y, tz + dz);
						if (st != null && st.isAir()) {
							this.d.remove(tx + dx, y, tz + dz);
						}
					}
				}
			}
			Garden.tree(this.d, tx, 0, tz, th);
		}
	}

	/** A well: a ring of stone round a water cauldron, two posts and a little roof with a bucket on a chain. */
	void wellhead(int cx, int cz) {
		Stone dress = Stone.dressing(this.d);
		try (var s = this.d.stage(StageKind.EXTERIOR)) {
			this.d.set(cx, 0, cz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		this.d.poi(PoiType.MEETING_POINT, cx, 0, cz - 1, Dirs.BACK);
		for (int y = 0; y < 3; y++) {
			keep(cx, y, cz);
		}
		// posts and a cross beam over the cauldron, a chain and lantern hanging from it
		if (this.d.isEmpty(cx - 1, 0, cz) && this.d.isEmpty(cx + 1, 0, cz)) {
			Paint post = Arches.timber(this.rite.arcade) || this.rite.timber() ? this.d.mat(Role.FRAME).log(Direction.Axis.Y) : dress.wallOrFull();
			try (var s = this.d.stage(StageKind.EXTERIOR)) {
				for (int y = 0; y < 3; y++) {
					this.d.set(cx - 1, y, cz, post);
					this.d.set(cx + 1, y, cz, post);
				}
				this.d.set(cx - 1, 3, cz, this.d.mat(Role.BEAM).log(Direction.Axis.X));
				this.d.set(cx, 3, cz, this.d.mat(Role.BEAM).log(Direction.Axis.X));
				this.d.set(cx + 1, 3, cz, this.d.mat(Role.BEAM).log(Direction.Axis.X));
				Mat roof = this.d.mat(Role.ROOF);
				for (int x = cx - 1; x <= cx + 1; x++) {
					this.d.set(x, 4, cz - 1, roof.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
					this.d.set(x, 4, cz + 1, roof.stairsOrSlab(Dirs.FRONT, Half.BOTTOM));
					this.d.set(x, 4, cz, roof.slab(SlabType.BOTTOM));
				}
				this.d.set(cx, 2, cz, States.lantern(Blocks.LANTERN, true));
			}
		}
	}

	// ================================================================================================ the garden

	/**
	 * The walled herb garden in the strip beside the far range: a low wall on its three open sides with a gate from the
	 * street, rows of herbs either side of a path, beehives at the back, a water butt and a bench.
	 */
	void garden() {
		if (this.gs <= 0) {
			return;
		}
		int x0 = this.gx0;
		int x1 = this.gx1;
		int z0 = this.zF;
		int z1 = this.dp - 1;
		Stone body = Stone.panel(this.d);
		Stone dress = Stone.dressing(this.d);
		int outerX = this.left ? x1 : x0;
		int g = (x0 + x1) / 2;
		try (var s = this.d.stage(StageKind.WALLS)) {
			List<int[]> wall = new ArrayList<>();
			for (int z = z0; z <= z1; z++) {
				wall.add(new int[]{outerX, z});
			}
			for (int x = x0; x <= x1; x++) {
				wall.add(new int[]{x, z0});
				wall.add(new int[]{x, z1});
			}
			for (int[] c : wall) {
				if (c[1] == z0 && c[0] == g) {
					continue;
				}
				this.d.set(c[0], -1, c[1], this.d.mat(Role.FOUNDATION).full());
				this.d.set(c[0], 0, c[1], body.full());
				this.d.set(c[0], 1, c[1], dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
			}
		}
		Mat rail = this.d.mat(Role.RAILING);
		if (rail.has(Form.FENCE_GATE)) {
			this.d.inStage(StageKind.OPENINGS, () -> this.d.set(g, 0, z0, rail.gate(Dirs.FRONT, false)));
		}
		keep(g, 0, z0);
		keep(g, 0, z0 + 1);
		// the path up the middle from the gate, rows of herbs either side
		int ix0 = this.left ? x0 : x0 + 1;
		int ix1 = this.left ? x1 - 1 : x1;
		List<int[]> path = Garden.line(g, z0 + 1, g, z1 - 1);
		Garden.path(this.d, path, 0);
		for (int[] p : path) {
			keep(p[0], 0, p[1]);
		}
		int hz1 = z1 - 3;
		for (int z = z0 + 2; z + 1 <= hz1; z += 3) {
			if (g - 1 >= ix0) {
				Garden.kitchenRows(this.d, ix0, z, g - 1, z + 1, 0, Dirs.LEFT);
			}
			if (g + 1 <= ix1) {
				Garden.kitchenRows(this.d, g + 1, z, ix1, z + 1, 0, Dirs.RIGHT);
			}
		}
		// beehives along the back wall, a water butt by the gate
		for (int x = ix0; x <= ix1; x += 2) {
			if (x != g && this.d.isEmpty(x, 0, z1 - 1)) {
				Garden.beehive(this.d, x, 0, z1 - 1, Dirs.FRONT);
			}
		}
		int bx = g == ix0 ? ix1 : ix0;
		if (this.d.isEmpty(bx, 0, z0 + 1)) {
			Garden.waterButt(this.d, bx, 0, z0 + 1);
		}
	}

	/** Spreads n beds over the rooms of a kind (upstairs first), in proportion to their length. */
	void beds(Kind kind, int n) {
		List<Room> rs = new ArrayList<>();
		for (Range r : this.ranges) {
			for (int k = r.rooms.size() - 1; k >= 0; k--) {
				for (Room room : r.rooms.get(k)) {
					if (room.kind == kind) {
						rs.add(room);
					}
				}
			}
		}
		if (rs.isEmpty()) {
			return;
		}
		int total = 0;
		for (Room room : rs) {
			total += room.length();
		}
		int left = n;
		for (int i = 0; i < rs.size(); i++) {
			Room room = rs.get(i);
			int share = i == rs.size() - 1 ? left : Math.max(1, (int) Math.round(n * room.length() / (double) total));
			share = Math.min(share, left);
			room.beds = Math.max(room.beds, share);
			left -= share;
		}
	}

	/** A bell pavilion in the middle of the garth: four posts, a curved roof, the bell hung under it (the karst's). */
	int pavilion() {
		int cx = (this.gax0 + this.gax1) / 2;
		int cz = (this.gaz0 + this.gaz1) / 2;
		if (this.gax1 - this.gax0 < 4 || this.gaz1 - this.gaz0 < 4) {
			return 0;
		}
		Mat frame = this.d.mat(Role.FRAME);
		try (var s = this.d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{cx - 1, cz - 1}, {cx + 1, cz - 1}, {cx - 1, cz + 1}, {cx + 1, cz + 1}}) {
				for (int y = 0; y < 4; y++) {
					this.d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
				}
			}
		}
		Stone dress = Stone.dressing(this.d);
		try (var s = this.d.stage(StageKind.EXTERIOR)) {
			for (int z = cz - 1; z <= cz + 1; z++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					this.d.set(x, -1, z, dress.full());
				}
			}
		}
		Roofs.Opts o = Roofs.Opts.of(this.d).withEaves(1, 1).withPitch(1.0);
		Roofs.Built b = Roofs.build(this.d, cx - 1, cz - 1, cx + 1, cz + 1, 3, Habits.RoofForm.PAGODA, Direction.Axis.X, o, false, false);
		dev.voidmc.hearth.build.kit.civic.Towers.bell(this.d, cx, 2, cz, Direction.Axis.X);
		for (int y = 0; y < 2; y++) {
			keep(cx, y, cz);
		}
		return b.roof().ridgeY;
	}

	// ================================================================================================ furnishing

	/**
	 * Makes sure a job has its place: where its own room was squeezed out, a workstation against a free wall in the
	 * first room of the given kinds that has one.
	 */
	void ensureJob(JobType job, Kind... kinds) {
		if (this.jobs.contains(job)) {
			return;
		}
		for (Kind kind : kinds) {
			for (Range r : this.ranges) {
				for (Room room : r.rooms.get(0)) {
					if (room.kind != kind) {
						continue;
					}
					int y = room.y();
					for (int[] c : along(room, true, true, true)) {
						Direction in = dir(c);
						int fx = c[0] + Dirs.dx(in);
						int fz = c[1] + Dirs.dz(in);
						if (free(c[0], y, c[1]) && this.d.isEmpty(fx, y, fz) && this.d.isSolid(fx, y - 1, fz) && !kept(c[0], y, c[1])) {
							if (job == JobType.HEALER) {
								Studies.counter(this.d, c[0], y, c[1], in, 0);
								this.d.poi(PoiType.JOB_SITE, fx, y, fz, in.getOpposite(), job);
							} else {
								Furniture.workstation(this.d, job, c[0], y, c[1], in);
							}
							keep(fx, y, fz);
							this.jobs.add(job);
							return;
						}
					}
				}
			}
		}
	}

	/** Furnishes every room for what it is, then lights it to the rule. */
	void furnish() {
		for (Range r : this.ranges) {
			for (List<Room> storey : r.rooms) {
				for (Room room : storey) {
					furnish(room);
				}
			}
		}
		for (Range r : this.ranges) {
			for (List<Room> storey : r.rooms) {
				for (Room room : storey) {
					int[] q = room.rect();
					dev.voidmc.hearth.build.kit.light.Lighting.room(this.d, q[0], q[1], q[2], q[3], room.y());
				}
			}
		}
	}

	/** At least one sickbed: where the infirmary had no room for one, the first bed of the dormitory serves. */
	void ensureSickbed() {
		if (this.sickbeds > 0 || this.bedFeet.isEmpty()) {
			return;
		}
		int[] f = this.bedFeet.get(0);
		this.d.poi(PoiType.SICKBED, f[0], f[1], f[2], Direction.values()[f[3]]);
		this.sickbeds++;
	}

	private void furnish(Room room) {
		this.cur = plan(room);
		try {
			furnishKind(room);
		} finally {
			this.cur = null;
		}
	}

	private void furnishKind(Room room) {
		switch (room.kind) {
			case GATE, TOWER -> {
			}
			case DORMITORY -> beds(room, Math.max(1, room.beds), false, false);
			case CHAMBERS -> beds(room, Math.max(1, room.beds), true, false);
			case INFIRMARY -> infirmary(room);
			case GUESTS -> {
				if (room.storey > 0 || room.beds > 0) {
					beds(room, Math.max(1, room.beds), false, false);
				} else {
					generic(room, Rooms.Type.LIVING, 0, null);
				}
			}
			case PORTER -> porter(room);
			case CELLAR, STORE -> store(room);
			case BREWHOUSE -> brewhouse(room);
			case REFECTORY -> refectory(room);
			case KITCHEN -> generic(room, Rooms.Type.KITCHEN, 0, null);
			case CHAPTER -> chapter(room);
			case SCRIPTORIUM -> scriptorium(room);
			case WORKROOM -> workroom(room);
			case PARLOUR -> parlour(room);
			case LECTURE -> lecture(room);
			case LIBRARY -> library(room);
		}
	}

	private void generic(Room room, Rooms.Type type, int beds, JobType job) {
		int[] r = room.rect();
		Rooms.Room rr = new Rooms.Room(r[0], r[1], r[2], r[3], room.y(), 3);
		for (int z = r[1]; z <= r[3]; z++) {
			for (int x = r[0]; x <= r[2]; x++) {
				if (kept(x, room.y(), z) && this.d.isEmpty(x, room.y(), z)) {
					rr.keep(x, z);
				}
			}
		}
		Rooms.furnish(this.d, rr, type, beds, job);
	}

	/** The room being furnished, as a plan of what stays reachable (null outside furnishing). */
	private Rooms.Room cur;

	/**
	 * A free cell to stand furniture on: empty, on a floor, not kept for a way, and (while a room is being furnished)
	 * taken for good only if every door and kept cell of the room can still be walked to with it gone.
	 */
	boolean free(int x, int y, int z) {
		if (kept(x, y, z) || !this.d.isEmpty(x, y, z) || !this.d.isSolid(x, y - 1, z) || !this.d.isEmpty(x, y + 1, z)) {
			return false;
		}
		return this.cur == null || !this.cur.inside(x, z) || this.cur.claim(x, z);
	}

	/** The reach plan of a room: what is already built is taken, holes are taken, kept cells must stay reachable. */
	private Rooms.Room plan(Room room) {
		int[] q = room.rect();
		int y = room.y();
		Rooms.Room rr = new Rooms.Room(q[0], q[1], q[2], q[3], y, 3);
		for (int z = q[1]; z <= q[3]; z++) {
			for (int x = q[0]; x <= q[2]; x++) {
				if (kept(x, y, z) && this.d.isEmpty(x, y, z)) {
					rr.keep(x, z);
				} else if (!this.d.isEmpty(x, y, z) && !(this.d.get(x, y, z) != null && this.d.get(x, y, z).getBlock() instanceof net.minecraft.world.level.block.LadderBlock)
					|| !this.d.isSolid(x, y - 1, z)) {
					rr.block(x, z);
				}
			}
		}
		// the doors in the room's walls: their inside cells must stay reachable
		Range r = room.range;
		for (int a = room.a0; a <= room.a1; a++) {
			for (int c : new int[]{r.c0(), r.c1()}) {
				int wc = c == r.c0() ? c - 1 : c + 1;
				if (isDoor(r.x(a, wc), y, r.z(a, wc)) && this.d.isEmpty(r.x(a, c), y, r.z(a, c))) {
					rr.keep(r.x(a, c), r.z(a, c));
				}
			}
		}
		for (int c = r.c0(); c <= r.c1(); c++) {
			for (int a : new int[]{room.a0, room.a1}) {
				int wa = a == room.a0 ? a - 1 : a + 1;
				if (isDoor(r.x(wa, c), y, r.z(wa, c)) && this.d.isEmpty(r.x(a, c), y, r.z(a, c))) {
					rr.keep(r.x(a, c), r.z(a, c));
				}
			}
		}
		return rr;
	}

	private boolean isDoor(int x, int y, int z) {
		BlockState s = this.d.get(x, y, z);
		return s != null && (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock || s.isAir());
	}

	/** The cells along the room's walls, each with the direction into the room; outer wall first, then the ends, the court wall. */
	private List<int[]> along(Room room, boolean outer, boolean court, boolean ends) {
		Range r = room.range;
		List<int[]> out = new ArrayList<>();
		Direction in = r.court;
		if (outer) {
			for (int a = room.a0; a <= room.a1; a++) {
				out.add(new int[]{r.x(a, r.outerC()), r.z(a, r.outerC()), in.ordinal()});
			}
		}
		if (ends && room.length() >= 2) {
			for (int c = r.c0(); c <= r.c1(); c++) {
				out.add(new int[]{r.x(room.a0, c), r.z(room.a0, c), r.plus().ordinal()});
				out.add(new int[]{r.x(room.a1, c), r.z(room.a1, c), r.plus().getOpposite().ordinal()});
			}
		}
		if (court) {
			for (int a = room.a0; a <= room.a1; a++) {
				out.add(new int[]{r.x(a, r.courtC()), r.z(a, r.courtC()), in.getOpposite().ordinal()});
			}
		}
		return out;
	}

	private static Direction dir(int[] cell) {
		return Direction.values()[cell[2]];
	}

	/**
	 * Beds side by side along the outer wall, heads to it (and along the court wall where the range is wide enough for a
	 * walk between): a chest or a desk and shelf between them in scholars' chambers. Registers SICKBEDs in an infirmary.
	 */
	private int beds(Room room, int n, boolean scholars, boolean sick) {
		Range r = room.range;
		int y = room.y();
		int placed = 0;
		List<int[]> rows = new ArrayList<>();
		rows.addAll(along(room, true, false, false));
		if (r.width() >= 5) {
			rows.addAll(along(room, false, true, false));
		}
		int k = 0;
		for (int[] c : rows) {
			if (placed >= n) {
				break;
			}
			Direction in = dir(c);
			int x = c[0];
			int z = c[1];
			int fx = x + Dirs.dx(in);
			int fz = z + Dirs.dz(in);
			if (!free(x, y, z) || !free(fx, y, fz)) {
				continue;
			}
			// the cell past the foot must stay a walk
			int wx = fx + Dirs.dx(in);
			int wz = fz + Dirs.dz(in);
			if (!this.d.isEmpty(wx, y, wz)) {
				continue;
			}
			if (scholars && (k % 3 == 1)) {
				Studies.desk(this.d, x, y, z, in, false);
				k++;
				continue;
			}
			if (scholars && (k % 3 == 2)) {
				Studies.books(this.d, x, y, z, 2, in);
				k++;
				continue;
			}
			Furniture.bed(this.d, x, y, z, in);
			keep(wx, y, wz);
			if (sick) {
				this.d.poi(PoiType.SICKBED, fx, y, fz, in.getOpposite());
				this.sickbeds++;
			} else {
				this.bedFeet.add(new int[]{fx, y, fz, in.getOpposite().ordinal()});
			}
			placed++;
			k++;
		}
		// a chest at the end of the room, a lamp hung
		for (int[] c : along(room, false, false, true)) {
			if (free(c[0], y, c[1])) {
				Furniture.chest(this.d, c[0], y, c[1], dir(c), List.of(new Object[]{"minecraft:white_wool", 4}, new Object[]{"minecraft:candle", 2}));
				break;
			}
		}
		return placed;
	}

	private void infirmary(Room room) {
		int y = room.y();
		Range r = room.range;
		// the healer's bench at one end: a brewing stand and a cauldron of water, a chest of herbs
		boolean station = false;
		for (int[] c : along(room, false, false, true)) {
			Direction in = dir(c);
			if (!free(c[0], y, c[1])) {
				continue;
			}
			int fx = c[0] + Dirs.dx(in);
			int fz = c[1] + Dirs.dz(in);
			if (!free(fx, y, fz)) {
				continue;
			}
			Studies.counter(this.d, c[0], y, c[1], in, 0);
			this.d.poi(PoiType.JOB_SITE, fx, y, fz, in.getOpposite(), JobType.HEALER);
			keep(fx, y, fz);
			this.jobs.add(JobType.HEALER);
			station = true;
			break;
		}
		for (int[] c : along(room, false, false, true)) {
			if (free(c[0], y, c[1])) {
				Studies.crucible(this.d, c[0], y, c[1], true);
				break;
			}
		}
		beds(room, Math.max(2, room.beds), false, true);
		for (int[] c : along(room, false, true, true)) {
			if (free(c[0], y, c[1])) {
				Furniture.chest(this.d, c[0], y, c[1], dir(c), List.of(new Object[]{"minecraft:glass_bottle", 6}, new Object[]{"minecraft:sweet_berries", 8},
					new Object[]{"minecraft:white_wool", 4}));
				break;
			}
		}
	}

	private void porter(Room room) {
		int y = room.y();
		int n = 0;
		for (int[] c : along(room, true, false, true)) {
			if (!free(c[0], y, c[1])) {
				continue;
			}
			Direction in = dir(c);
			switch (n) {
				case 0 -> Furniture.table(this.d, c[0], y, c[1]);
				case 1 -> Furniture.chest(this.d, c[0], y, c[1], in, List.of(new Object[]{"minecraft:bread", 6}, new Object[]{"minecraft:candle", 4}));
				case 2 -> Furniture.barrel(this.d, c[0], y, c[1], in);
				default -> {
				}
			}
			if (n == 0) {
				int sx = c[0] + Dirs.dx(in);
				int sz = c[1] + Dirs.dz(in);
				if (free(sx, y, sz)) {
					Furniture.chair(this.d, sx, y, sz, in.getOpposite().getOpposite());
				}
			}
			n++;
			if (n > 2) {
				break;
			}
		}
	}

	private void store(Room room) {
		int y = room.y();
		storage(room, List.of(new Object[]{"minecraft:wheat", 16}, new Object[]{"minecraft:apple", 8}));
		int n = 0;
		for (int[] c : along(room, true, true, true)) {
			Direction in = dir(c);
			int fx = c[0] + Dirs.dx(in);
			int fz = c[1] + Dirs.dz(in);
			// a barrel only with a floor before it that stays free (a later barrel never takes an earlier one's front)
			if (!free(c[0], y, c[1]) || !this.d.isEmpty(fx, y, fz) || !this.d.isEmpty(fx, y + 1, fz) || !this.d.isSolid(fx, y - 1, fz)) {
				continue;
			}
			Furniture.barrel(this.d, c[0], y, c[1], in);
			keep(fx, y, fz);
			if (n % 3 == 0 && this.d.isEmpty(c[0], y + 1, c[1]) && this.d.isEmpty(c[0], y + 2, c[1])) {
				int xx = c[0];
				int zz = c[1];
				this.d.inStage(StageKind.INTERIOR, () -> this.d.set(xx, y + 1, zz, States.facing(Blocks.BARREL, Direction.UP)));
			}
			n++;
			if (n >= room.length() * 2 - 3) {
				break;
			}
		}
	}

	/**
	 * A chest against a wall whose front stays free, registered as the room's STORAGE (the outer wall first, then the
	 * ends, the court wall). Returns whether one was placed.
	 */
	private boolean storage(Room room, List<Object[]> items) {
		int y = room.y();
		for (int[] c : along(room, true, true, true)) {
			Direction in = dir(c);
			int fx = c[0] + Dirs.dx(in);
			int fz = c[1] + Dirs.dz(in);
			if (!free(c[0], y, c[1]) || !this.d.isEmpty(fx, y, fz) || !this.d.isSolid(fx, y - 1, fz)) {
				continue;
			}
			Furniture.chest(this.d, c[0], y, c[1], in, items);
			this.d.poi(PoiType.STORAGE, fx, y, fz, in.getOpposite());
			keep(fx, y, fz);
			return true;
		}
		return false;
	}

	private void brewhouse(Room room) {
		int y = room.y();
		boolean job = false;
		int n = 0;
		for (int[] c : along(room, true, true, true)) {
			if (!free(c[0], y, c[1])) {
				continue;
			}
			Direction in = dir(c);
			int fx = c[0] + Dirs.dx(in);
			int fz = c[1] + Dirs.dz(in);
			if (!job) {
				if (!free(fx, y, fz)) {
					continue;
				}
				Furniture.workstation(this.d, JobType.BREWER, c[0], y, c[1], in);
				keep(fx, y, fz);
				job = true;
				this.jobs.add(JobType.BREWER);
				continue;
			}
			switch (n % 3) {
				case 0 -> Studies.crucible(this.d, c[0], y, c[1], true);
				case 1 -> Furniture.barrel(this.d, c[0], y, c[1], in);
				default -> Furniture.barrel(this.d, c[0], y, c[1], Direction.UP);
			}
			n++;
			if (n >= 6) {
				break;
			}
		}
		storage(room, List.of(new Object[]{"minecraft:wheat", 24}, new Object[]{"minecraft:sugar", 8}, new Object[]{"minecraft:glass_bottle", 6}));
	}

	/**
	 * The refectory: a long table down the middle of the hall with benches either side, the reader's pulpit (a lectern)
	 * at the far end, a cupboard of plates.
	 */
	private void refectory(Room room) {
		Range r = room.range;
		int y = room.y();
		int cmid = (r.c0() + r.c1() + (r.courtC() > r.outerC() ? 1 : 0)) / 2;
		int a0 = room.a0 + 1;
		int a1 = room.a1 - 1;
		Direction run = r.plus();
		Mat wood = this.d.mat(Role.FURNITURE);
		// the table, broken where a door or way crosses it
		int start = -1;
		for (int a = a0; a <= a1 + 1; a++) {
			boolean ok = a <= a1 && free(r.x(a, cmid), y, r.z(a, cmid));
			if (ok && start < 0) {
				start = a;
			}
			if (!ok && start >= 0) {
				if (a - start >= 2) {
					Furniture.longTable(this.d, r.x(start, cmid), y, r.z(start, cmid), run, a - start);
					for (int b = start; b < a; b++) {
						for (int side : new int[]{-1, 1}) {
							int c = cmid + side;
							if (c < r.c0() || c > r.c1()) {
								continue;
							}
							int bx = r.x(b, c);
							int bz = r.z(b, c);
							if (!free(bx, y, bz)) {
								continue;
							}
							// the bench's back away from the table
							Direction away = r.axis == Direction.Axis.X ? (side > 0 ? Dirs.BACK : Dirs.FRONT) : (side > 0 ? Dirs.RIGHT : Dirs.LEFT);
							this.d.inStage(StageKind.INTERIOR, () -> this.d.set(bx, y, bz, wood.stairsOrSlab(away, Half.BOTTOM)));
							if (((b - start) & 1) == 0) {
								this.d.poi(PoiType.SEAT, bx, y, bz, away.getOpposite());
							}
						}
					}
				}
				start = -1;
			}
		}
		// the pulpit at the far end
		int pa = room.a1;
		int pc = cmid;
		if (free(r.x(pa, pc), y, r.z(pa, pc))) {
			Studies.lectern(this.d, r.x(pa, pc), y, r.z(pa, pc), r.plus().getOpposite(), JobType.NONE, "Readings at Table");
		}
	}

	/** The chapter house: stone benches round the walls facing in, the head's seat and lectern at the far end. */
	private void chapter(Room room) {
		Range r = room.range;
		int y = room.y();
		Stone dress = Stone.dressing(this.d);
		int cmid = (r.c0() + r.c1()) / 2;
		int ha = room.a1;
		boolean seat = false;
		if (free(r.x(ha, cmid), y, r.z(ha, cmid))) {
			Furniture.chair(this.d, r.x(ha, cmid), y, r.z(ha, cmid), r.plus());
			seat = true;
			int la = ha - 1;
			if (free(r.x(la, cmid), y, r.z(la, cmid)) && room.length() >= 5) {
				keep(r.x(la, cmid), y, r.z(la, cmid));
			}
		}
		int n = 0;
		for (int[] c : along(room, true, true, false)) {
			if (!free(c[0], y, c[1])) {
				continue;
			}
			int fx = c[0] + Dirs.dx(dir(c));
			int fz = c[1] + Dirs.dz(dir(c));
			if (!this.d.isEmpty(fx, y, fz)) {
				continue;
			}
			Direction back = dir(c).getOpposite();
			int xx = c[0];
			int zz = c[1];
			this.d.inStage(StageKind.INTERIOR, () -> this.d.set(xx, y, zz, dress.stairsOrSlab(back, Half.BOTTOM)));
			if ((n++ & 1) == 0) {
				this.d.poi(PoiType.SEAT, xx, y, zz, dir(c));
			}
		}
		if (seat) {
			Signs_banner(r.x(ha, cmid), y + 2, r.z(ha, cmid), r.plus().getOpposite());
		}
	}

	private void Signs_banner(int x, int y, int z, Direction facing) {
		if (!this.d.isEmpty(x, y, z)) {
			return;
		}
		List<String> colours = dev.voidmc.hearth.build.kit.civic.Signs.clothColours(this.d);
		dev.voidmc.hearth.build.kit.civic.Signs.wallBanner(this.d, x, y, z, facing, colours.get(0),
			List.<String[]>of(new String[]{"cross", colours.size() > 1 ? colours.get(1) : "white"}));
	}

	/**
	 * The scriptorium: desks under the outer windows, the masters' lecterns at the ends (the scholars' places), shelves of
	 * books along the court wall, a chest of vellum and ink.
	 */
	private void scriptorium(Room room) {
		int y = room.y();
		int lect = 0;
		for (int[] c : along(room, false, false, true)) {
			if (lect >= 2 || !free(c[0], y, c[1])) {
				continue;
			}
			Direction in = dir(c);
			int fx = c[0] + Dirs.dx(in);
			int fz = c[1] + Dirs.dz(in);
			if (!free(fx, y, fz)) {
				continue;
			}
			Studies.lectern(this.d, c[0], y, c[1], in, JobType.SCHOLAR, lect == 0 ? "The Rule" : "Herbal");
			keep(fx, y, fz);
			lect++;
		}
		int n = 0;
		for (int[] c : along(room, true, false, false)) {
			if (!free(c[0], y, c[1])) {
				continue;
			}
			int fx = c[0] + Dirs.dx(dir(c));
			int fz = c[1] + Dirs.dz(dir(c));
			if (!free(fx, y, fz)) {
				continue;
			}
			if ((n++ % 2) == 0) {
				Studies.desk(this.d, c[0], y, c[1], dir(c), true);
			}
		}
		int k = 0;
		for (int[] c : along(room, false, true, false)) {
			if (!free(c[0], y, c[1])) {
				continue;
			}
			if (k++ == 0) {
				storage(room, List.of(new Object[]{"minecraft:paper", 24}, new Object[]{"minecraft:ink_sac", 6}, new Object[]{"minecraft:feather", 6},
					new Object[]{"minecraft:book", 4}));
			} else if (windowless(c, dir(c), y)) {
				Studies.books(this.d, c[0], y, c[1], 2, dir(c));
			}
		}
	}

	/** Whether the wall behind a cell has no window at the shelf's height. */
	private boolean windowless(int[] c, Direction in, int y) {
		int wx = c[0] - Dirs.dx(in);
		int wz = c[1] - Dirs.dz(in);
		return dev.voidmc.hearth.build.kit.Openings.isWallBlock(this.d, wx, y + 1, wz);
	}

	private void workroom(Room room) {
		int y = room.y();
		int n = 0;
		for (int[] c : along(room, true, true, true)) {
			if (!free(c[0], y, c[1])) {
				continue;
			}
			int fx = c[0] + Dirs.dx(dir(c));
			int fz = c[1] + Dirs.dz(dir(c));
			if (!free(fx, y, fz)) {
				continue;
			}
			Direction in = dir(c);
			switch (n % 4) {
				case 0 -> this.d.inStage(StageKind.INTERIOR, () -> this.d.set(c[0], y, c[1], States.facing(Blocks.LOOM, in)));
				case 1 -> Studies.desk(this.d, c[0], y, c[1], in, true);
				case 2 -> Furniture.chest(this.d, c[0], y, c[1], in, List.of(new Object[]{"minecraft:white_wool", 12}, new Object[]{"minecraft:string", 8}));
				default -> Furniture.barrel(this.d, c[0], y, c[1], in);
			}
			n++;
			if (n >= 8) {
				break;
			}
		}
	}

	private void parlour(Room room) {
		generic(room, Rooms.Type.LIVING, 0, null);
	}

	/**
	 * A lecture hall: the master's lectern at the far end (the scholar's place), benches in rows across the hall facing
	 * it with a gangway, shelves along the walls.
	 */
	private void lecture(Room room) {
		Range r = room.range;
		int y = room.y();
		int cmid = (r.c0() + r.c1()) / 2;
		int la = room.a1;
		Direction toPeople = r.plus().getOpposite();
		if (free(r.x(la, cmid), y, r.z(la, cmid))) {
			Studies.lectern(this.d, r.x(la, cmid), y, r.z(la, cmid), toPeople, JobType.SCHOLAR, "Lectures");
		}
		keep(r.x(la - 1, cmid), y, r.z(la - 1, cmid));
		keep(r.x(la - 2, cmid), y, r.z(la - 2, cmid));
		Mat wood = this.d.mat(Role.FURNITURE);
		// rows of benches facing the lectern, every other row, a gangway on the court side
		int gang = r.courtC();
		int n = 0;
		for (int a = la - 3; a >= room.a0 + 1; a -= 2) {
			for (int c = r.c0(); c <= r.c1(); c++) {
				if (c == gang) {
					continue;
				}
				int x = r.x(a, c);
				int z = r.z(a, c);
				if (!free(x, y, z)) {
					continue;
				}
				// the sitter faces the lectern: the bench's back toward the hall's low end
				this.d.inStage(StageKind.INTERIOR, () -> this.d.set(x, y, z, wood.stairsOrSlab(toPeople, Half.BOTTOM)));
				if ((n++ & 1) == 0) {
					this.d.poi(PoiType.SEAT, x, y, z, toPeople.getOpposite());
				}
			}
		}
		for (int[] c : along(room, false, false, true)) {
			if (free(c[0], y, c[1]) && windowless(c, dir(c), y)) {
				Studies.books(this.d, c[0], y, c[1], 2, dir(c));
			}
		}
	}

	/** The library: shelves along every wall without a window, lecterns for the scholars, reading tables. */
	private void library(Room room) {
		Range r = room.range;
		int y = room.y();
		int lect = 0;
		for (int[] c : along(room, false, false, true)) {
			if (lect >= 2 || !free(c[0], y, c[1])) {
				continue;
			}
			Direction in = dir(c);
			int fx = c[0] + Dirs.dx(in);
			int fz = c[1] + Dirs.dz(in);
			if (!free(fx, y, fz)) {
				continue;
			}
			Studies.lectern(this.d, c[0], y, c[1], in, JobType.SCHOLAR, lect == 0 ? "Catalogue of Books" : "Of Natural Things");
			keep(fx, y, fz);
			lect++;
		}
		storage(room, List.of(new Object[]{"minecraft:book", 8}, new Object[]{"minecraft:paper", 16}));
		int k = 0;
		for (int[] c : along(room, true, true, true)) {
			if (!free(c[0], y, c[1])) {
				continue;
			}
			int fx = c[0] + Dirs.dx(dir(c));
			int fz = c[1] + Dirs.dz(dir(c));
			if (!this.d.isEmpty(fx, y, fz)) {
				continue;
			}
			if (windowless(c, dir(c), y)) {
				Studies.books(this.d, c[0], y, c[1], 3, dir(c));
			} else if ((k++ & 1) == 0) {
				Studies.desk(this.d, c[0], y, c[1], dir(c), false);
			}
		}
		// reading tables down the middle where the hall is wide enough to walk round them
		if (r.width() >= 5) {
			int cmid = (r.c0() + r.c1()) / 2;
			for (int a = room.a0 + 2; a <= room.a1 - 2; a += 3) {
				int x = r.x(a, cmid);
				int z = r.z(a, cmid);
				if (free(x, y, z)) {
					Furniture.table(this.d, x, y, z);
					this.d.poi(PoiType.TABLE, x, y, z, r.court);
				}
			}
		}
	}

	// ================================================================================================ outside

	/** The forecourt along the street, the way to the gate and to the church's door, lamps either side of the gate. */
	void forecourt(int churchDoorX) {
		dev.voidmc.hearth.build.kit.civic.Grounds.forecourt(this.d, 0, this.w - 1, this.zF - 1, true);
		int g = courtX();
		Groundwork.approach(this.d, g, 0, this.zF - 1);
		if (churchDoorX >= 0) {
			Groundwork.approach(this.d, churchDoorX, 0, this.zF - 1);
		}
		if (this.gs > 0) {
			Groundwork.approach(this.d, (this.gx0 + this.gx1) / 2, 0, this.zF - 1);
		}
	}

	/** The finishing pass: natural ground cleared over the plot up to the skyline. */
	void finish() {
		clearThresholds();
		Halls.lintels(this.d);
		Groundwork.clear(this.d, 0, 0, this.w - 1, this.dp - 1, Math.min(this.top, this.skyline + 2));
	}

	/** The style's door block. */
	Block doorBlock() {
		return this.d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
	}

	static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}
}
