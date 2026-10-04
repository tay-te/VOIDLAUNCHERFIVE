package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Partitions;
import dev.voidmc.hearth.build.kit.townhome.Projections;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:house/courtyard_house}: rooms all round an open court, the street seeing only a blind wall and a
 * gate. In the desert the gate opens into a vestibule that turns before the court (no one sees in from the street),
 * the court has a fountain and a palm, an arcade along one side, a stair up to the roof terraces and a wind tower over
 * the back rooms; on a hill town's square the gate is an arched carriage door, the roofs are low tiles falling to the
 * court, lemon trees stand in pots under a pergola and the back wing rises a storey to a loggia; in the karst the halls
 * stand on their own round a court with a flowering tree, each under its own curved roof with lifted corners, the gate
 * in a corner behind a spirit wall. The back wing is the hall and the family's rooms, the sides bedrooms, the front the
 * kitchen and stores.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code storeys} (int, 1–2): the back wing's storeys (default by the plot's height)</li>
 *   <li>{@code roof} (RoofForm name), {@code variant} (int)</li>
 * </ul>
 */
public final class CourtyardHouse implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("house/courtyard_house"), ArchetypeCategory.HOUSING, Set.of(), 4,
		11, 15, 11, 15, 7, 12, SettlementTier.HAMLET, Set.of(Culture.DESERT, Culture.MEDITERRANEAN, Culture.KARST), Set.of(), List.of(), 0, 1.2);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** One wing: its rectangle (outer walls included) and which side of the court it stands on. */
	private record Wing(int x0, int z0, int x1, int z1, Direction side) {
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Habits h = d.habits();
		Traits t = Traits.of(d);
		int maxH = d.maxHeight();
		boolean flat = t.flat() || t.flatSome() && d.chance(0.35);
		boolean eastern = t.eastern();
		int wd = Math.min(w, dp) >= 13 ? 5 : 4;
		int sh = Math.max(4, h.walls().storeyHeight());
		// the court
		int cx0 = wd;
		int cx1 = w - 1 - wd;
		int cz0 = wd;
		int cz1 = dp - 1 - wd;
		// the back wing rises a storey where the height allows
		boolean upper = d.param("storeys", maxH >= (flat ? 11 : 12) ? 2 : 1) >= 2 && maxH >= (flat ? 11 : 12);

		List<Wing> wings = new ArrayList<>();
		wings.add(new Wing(0, 0, w - 1, wd - 1, Dirs.FRONT));
		wings.add(new Wing(0, cz1 + 1, w - 1, dp - 1, Dirs.BACK));
		wings.add(new Wing(0, wd, wd - 1, cz1, Dirs.LEFT));
		wings.add(new Wing(cx1 + 1, wd, w - 1, cz1, Dirs.RIGHT));
		d.body(0, 0, w - 1, dp - 1);
		d.eave(sh - 1);

		// the gate: straight through the middle of the front wing (a bent way in the desert, a corner gate in the karst)
		int gate = eastern ? (d.chance(0.5) ? 2 : w - 3) : w / 2;
		if (TownHouses.misfit(d, gate) > 0) {
			// the street does not meet the floor there: the gate goes where it does (the street wall has no room for steps)
			int best = gate;
			for (int x = 2; x <= w - 3; x++) {
				if (TownHouses.misfit(d, x) < TownHouses.misfit(d, best)) {
					best = x;
				}
			}
			gate = best;
		}
		int inner = t.walls() == WallKind.ADOBE && !eastern ? (gate <= w / 2 ? cx1 - 1 : cx0 + 1) : gate;
		if (eastern) {
			inner = gate < w / 2 ? cx0 : cx1;
		}

		// ground, floor, walls
		Groundwork.footings(d, 0, 0, w - 1, dp - 1, -1);
		Groundwork.level(d, cx0, cz0, cx1, cz1, 0, true);
		Mat floorM = d.mat(Role.FLOOR);
		for (Wing g : wings) {
			Floors.boards(d, g.x0 + 1, g.z0 + (g.side == Dirs.LEFT || g.side == Dirs.RIGHT ? 0 : 1), g.x1 - 1,
				g.z1 - (g.side == Dirs.LEFT || g.side == Dirs.RIGHT ? 0 : 1), -1);
		}
		int top = sh - 1;
		ring(d, w, dp, cx0, cz0, cx1, cz1, top);
		if (upper) {
			upperWalls(d, wings.get(1), sh, top + sh);
		}

		// openings: the gate, doors and windows onto the court, few and small to the street
		gateway(d, w, gate, t);
		boolean stair = (flat || upper) && cz1 - cz0 + 1 >= sh + 1;
		courtOpenings(d, cx0, cz0, cx1, cz1, gate, inner, t, eastern, stair);
		outerWindows(d, w, dp, t, gate);
		if (upper) {
			upperOpenings(d, wings.get(1), sh, t);
		}

		// rooms divided off round the ring, the vestibule inside the gate
		partitions(d, w, dp, wd, cx0, cz0, cx1, cz1, gate, inner, sh);

		// roofs
		Roofs.Roof roof = null;
		int deck = Integer.MIN_VALUE;
		if (flat) {
			deck = flatRoofs(d, w, dp, cx0, cz0, cx1, cz1, top, upper ? wings.get(1) : null, sh, t);
		} else {
			roof = ringRoof(d, w, dp, cx0, cz0, cx1, cz1, upper ? top + sh : top, eastern, upper ? wings.get(1) : null, top);
		}

		// the court: paving, a fountain or a tree, an arcade on one side, pots, a bench
		court(d, cx0, cz0, cx1, cz1, gate, inner, t, eastern, flat);

		// the way up: a stair along a court wall to the roof terraces or the upper storey
		if (stair) {
			courtStair(d, cx0, cz0, cx1, cz1, sh, upper, flat);
		}
		if (flat && t.walls() == WallKind.ADOBE && !eastern && maxH >= deck + 5) {
			windTower(d, w, dp, wd, deck, upper ? sh : 0);
		}

		// furnishing
		rooms(d, w, dp, wd, cx0, cz0, cx1, cz1, gate, inner, sh, upper, roof);
		// the kitchen's chimney over the hearth in the front corner
		if (roof != null && roof.covers(1, 1) && roof.surfaceY(1, 1) + 3 < maxH) {
			dev.voidmc.hearth.build.kit.Chimneys.stack(d, 1, 1, 2, roof, Math.min(roof.ridgeY + 1, maxH - 1));
		} else if (deck != Integer.MIN_VALUE && deck + 2 < maxH) {
			Mat stone = d.mat(Role.CHIMNEY);
			try (var s = d.stage(StageKind.ROOF)) {
				for (int y = 2; y <= deck; y++) {
					d.set(1, y, 1, stone.full());
				}
				d.set(1, deck + 1, 1, States.lit(Blocks.CAMPFIRE, true).trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
			}
		}
		if (d.habits().snow()) {
			Garden.snowBanks(d, 0, 0, w - 1, dp - 1, 0, gate);
		}
		int crest = roof != null ? roof.ridgeY : deck + (upper ? sh : 0) + 3;
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(maxH - 1, crest + 2));
	}

	// ------------------------------------------------------------------------------------------------- walls

	/** The outer walls and the court walls of the ring, one storey high, in the culture's walling. */
	private static void ring(Draft d, int w, int dp, int cx0, int cz0, int cx1, int cz1, int top) {
		Mat wall = d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Mat frame = d.mat(Role.FRAME);
		Mat accent = d.mat(Role.WALL_ACCENT);
		WallKind kind = d.habits().walls().kind();
		boolean framed = kind == WallKind.TIMBER_FRAME || kind == WallKind.BAMBOO || kind == WallKind.PLANK;
		boolean log = kind == WallKind.LOG;
		int plinthH = Math.max(0, d.habits().walls().plinth());
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(0, 0, w - 1, dp - 1)) {
				drawWall(d, side, top, wall, plinth, frame, accent, framed, log, plinthH);
			}
			for (Side side : Side.of(cx0 - 1, cz0 - 1, cx1 + 1, cz1 + 1)) {
				drawWall(d, side, top, wall, plinth, frame, accent, framed, log, plinthH);
			}
		}
		// the rooms' air
		for (int z = 1; z < dp - 1; z++) {
			for (int x = 1; x < w - 1; x++) {
				boolean court = x >= cx0 - 1 && x <= cx1 + 1 && z >= cz0 - 1 && z <= cz1 + 1;
				if (!court) {
					for (int y = 0; y < top; y++) {
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
	}

	private static void drawWall(Draft d, Side side, int top, Mat wall, Mat plinth, Mat frame, Mat accent, boolean framed, boolean log, int plinthH) {
		for (int i = 0; i < side.length(); i++) {
			boolean corner = side.isCorner(i);
			boolean post = framed && (corner || i % 3 == 0);
			for (int y = 0; y <= top; y++) {
				if (y < plinthH && !corner) {
					d.set(side.x(i), y, side.z(i), plinth.full());
				} else if (post || log && corner) {
					d.set(side.x(i), y, side.z(i), frame.log(Direction.Axis.Y));
				} else if (y == top && framed) {
					d.set(side.x(i), y, side.z(i), d.mat(Role.BEAM).log(side.axis()));
				} else if (corner && !framed) {
					d.set(side.x(i), y, side.z(i), accent.full());
				} else if (log) {
					d.set(side.x(i), y, side.z(i), wall.log(side.axis()));
				} else {
					d.set(side.x(i), y, side.z(i), wall.full());
				}
			}
		}
	}

	/** The back wing's upper storey: walls from the first floor up, its floor laid over the rooms below. */
	private static void upperWalls(Draft d, Wing g, int sh, int top) {
		Mat wall = d.mat(Role.WALL);
		Mat plinth = d.mat(Role.WALL_ACCENT);
		Mat frame = d.mat(Role.FRAME);
		Mat accent = d.mat(Role.WALL_ACCENT);
		WallKind kind = d.habits().walls().kind(1);
		boolean framed = kind == WallKind.TIMBER_FRAME || kind == WallKind.BAMBOO || kind == WallKind.PLANK;
		Floors.boards(d, g.x0, g.z0, g.x1, g.z1, sh - 1);
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(g.x0, g.z0, g.x1, g.z1)) {
				for (int i = 0; i < side.length(); i++) {
					boolean corner = side.isCorner(i);
					for (int y = sh; y <= top; y++) {
						if (framed && (corner || i % 3 == 0)) {
							d.set(side.x(i), y, side.z(i), frame.log(Direction.Axis.Y));
						} else if (y == sh && !framed) {
							d.set(side.x(i), y, side.z(i), plinth.full());
						} else if (corner && !framed) {
							d.set(side.x(i), y, side.z(i), accent.full());
						} else {
							d.set(side.x(i), y, side.z(i), kind == WallKind.PLANK ? d.mat(Role.GABLE).full() : wall.full());
						}
					}
				}
			}
		}
		d.carve(g.x0 + 1, sh, g.z0 + 1, g.x1 - 1, top - 1, g.z1 - 1);
	}

	/** The gate in the street wall: an arched or posted doorway two wide where it fits, a lamp, a step. */
	private static void gateway(Draft d, int w, int gate, Traits t) {
		Side front = Side.of(0, 0, w - 1, 0, Dirs.FRONT);
		Openings.door(d, front, gate, 0, 1, new Openings.DoorOpts(true, false, true));
		// an arch or a beam over it, framing pilasters
		Mat accent = d.mat(Role.WALL_ACCENT);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x : new int[]{gate - 1, gate + 1}) {
				if (x > 0 && x < w - 1) {
					for (int y = 0; y <= 2; y++) {
						d.set(x, y, 0, t.timber() && !t.masonry() ? d.mat(Role.FRAME).log(Direction.Axis.Y) : accent.full());
					}
				}
			}
			if (Openings.isWallBlock(d, gate, 3, 0) || d.get(gate, 3, 0) == null) {
				d.set(gate, 3, 0, accent.full());
			}
		}
		Fronts.doorCase(d, front, gate, 1, 0, t);
	}

	/**
	 * Doors from the court into each wing (the front one is the vestibule's inner door; the right wing's sits at the
	 * court's front end, clear of the stair up its wall), windows between them onto the court.
	 */
	private static void courtOpenings(Draft d, int cx0, int cz0, int cx1, int cz1, int gate, int inner, Traits t, boolean eastern, boolean stair) {
		Habits.Windows hw = d.habits().windows();
		boolean arched = d.chance(hw.arched());
		int mx = (cx0 + cx1) / 2;
		int mz = (cz0 + cz1) / 2;
		int rightDoor = stair ? cz0 : mz;
		Partitions.door(d, inner, 0, cz0 - 1, Dirs.BACK);
		Partitions.door(d, mx, 0, cz1 + 1, Dirs.BACK);
		Partitions.door(d, cx0 - 1, 0, mz, Dirs.LEFT);
		Partitions.door(d, cx1 + 1, 0, rightDoor, Dirs.RIGHT);
		for (Side s : Side.of(cx0 - 1, cz0 - 1, cx1 + 1, cz1 + 1)) {
			for (int i = 1; i < s.length() - 1; i++) {
				int x = s.x(i);
				int z = s.z(i);
				if (nearDoor(d, x, 0, z) || (i & 1) == 1) {
					continue;
				}
				Openings.window(d, s, s.column(i), 1, 1, hw.tall() ? 2 : 1, hw.lattice() ? Openings.WindowStyle.LATTICE
					: arched ? Openings.WindowStyle.ARCHED : Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, false, true));
			}
		}
	}

	/** A door at or next to a wall cell. */
	private static boolean nearDoor(Draft d, int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				var st = d.get(x + dx, y, z + dz);
				if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
					return true;
				}
			}
		}
		return false;
	}

	/** Small, high windows in the street and side walls (none in the desert's street wall), wider at the back. */
	private static void outerWindows(Draft d, int w, int dp, Traits t, int gate) {
		Habits.Windows hw = d.habits().windows();
		boolean blind = t.walls() == WallKind.ADOBE;
		for (Side s : Side.of(0, 0, w - 1, dp - 1)) {
			for (int i = 2; i < s.length() - 2; i += 3) {
				int x = s.x(i);
				if (s.out() == Dirs.FRONT && (Math.abs(x - gate) <= 2 || blind)) {
					continue;
				}
				Openings.WindowStyle style = hw.lattice() || blind ? Openings.WindowStyle.LATTICE : d.chance(hw.arched()) ? Openings.WindowStyle.ARCHED
					: Openings.WindowStyle.DRESSED;
				Openings.window(d, s, s.column(i), blind ? 2 : 1, 1, blind ? 1 : hw.tall() ? 2 : 1, style);
			}
		}
	}

	/** The upper storey's windows: onto the court a loggia or a gallery, small windows outward. */
	private static void upperOpenings(Draft d, Wing g, int sh, Traits t) {
		Side court = Side.of(g.x0, g.z0, g.x1, g.z1, Dirs.FRONT);
		if (t.masonry() && !t.eastern()) {
			Projections.arcade(d, court, 1, court.last() - 1, sh, 3, 2, true);
		} else {
			for (int i = 2; i < court.last() - 1; i += 2) {
				Openings.window(d, court, court.column(i), sh + 1, 1, 2, d.habits().windows().lattice() ? Openings.WindowStyle.LATTICE
					: Openings.WindowStyle.DRESSED);
			}
			Projections.gallery(d, court, 1, court.last() - 1, sh, t.lattice(), true);
			Partitions.door(d, court.x(court.middle()), sh, court.z(court.middle()), Dirs.FRONT);
		}
		Side back = Side.of(g.x0, g.z0, g.x1, g.z1, Dirs.BACK);
		for (int i = 2; i < back.last() - 1; i += 3) {
			Openings.window(d, back, back.column(i), sh + 1, 1, 1, d.habits().windows().lattice() ? Openings.WindowStyle.LATTICE
				: Openings.WindowStyle.DRESSED);
		}
	}

	/** Walls between the rooms of the ring: at the corners (the corner rooms join the side wings), round the vestibule. */
	private static void partitions(Draft d, int w, int dp, int wd, int cx0, int cz0, int cx1, int cz1, int gate, int inner, int sh) {
		int h = sh - 1;
		// the front wing: the vestibule between walls either side of the gate (and the inner door, if bent)
		int va = Math.min(gate, inner) - 1;
		int vb = Math.max(gate, inner) + 1;
		Partitions.alongZ(d, va, 1, wd - 2, 0, h, Integer.MIN_VALUE, Dirs.LEFT);
		Partitions.alongZ(d, vb, 1, wd - 2, 0, h, Integer.MIN_VALUE, Dirs.RIGHT);
		// the front rooms either side open off the side wings at the corners: walls across where the wings meet
		Partitions.alongX(d, wd - 1, 1, cx0 - 2, 0, h, Integer.MIN_VALUE, Dirs.BACK);
		Partitions.alongX(d, wd - 1, cx1 + 2, w - 2, 0, h, Integer.MIN_VALUE, Dirs.BACK);
		// the back wing apart from the side wings
		Partitions.alongX(d, cz1 + 1, 1, cx0 - 2, 0, h, Integer.MIN_VALUE, Dirs.BACK);
		Partitions.alongX(d, cz1 + 1, cx1 + 2, w - 2, 0, h, Integer.MIN_VALUE, Dirs.BACK);
		// doors from the front corner rooms into the side wings, so they are reached through them
		Partitions.door(d, 2, 0, wd - 1, Dirs.FRONT);
		Partitions.door(d, w - 3, 0, wd - 1, Dirs.FRONT);
		Partitions.door(d, 2, 0, cz1 + 1, Dirs.BACK);
		Partitions.door(d, w - 3, 0, cz1 + 1, Dirs.BACK);
	}

	// ------------------------------------------------------------------------------------------------- roofs

	/**
	 * Flat roofs over the ring: a deck at the wall head with a parapet on the street side and a low one round the court,
	 * spouts into the court; the upper wing's own deck above. Returns the ring's deck standing level.
	 */
	private static int flatRoofs(Draft d, int w, int dp, int cx0, int cz0, int cx1, int cz1, int top, Wing upper, int sh, Traits t) {
		Mat deck = d.mat(Role.ROOF);
		Mat wall = d.mat(Role.WALL);
		Mat coping = d.mat(Role.WALL_ACCENT);
		Mat beam = d.mat(Role.BEAM);
		int y = top + 1;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = 0; z < dp; z++) {
				for (int x = 0; x < w; x++) {
					boolean court = x >= cx0 && x <= cx1 && z >= cz0 && z <= cz1;
					if (court) {
						continue;
					}
					boolean outer = x == 0 || z == 0 || x == w - 1 || z == dp - 1;
					boolean courtEdge = x == cx0 - 1 || x == cx1 + 1 || z == cz0 - 1 || z == cz1 + 1;
					boolean inWall = outer || courtEdge && (x >= cx0 - 1 && x <= cx1 + 1 && z >= cz0 - 1 && z <= cz1 + 1);
					if (!inWall) {
						d.set(x, top, z, deck.full());
					}
					if (upper != null && x >= upper.x0 && x <= upper.x1 && z >= upper.z0 && z <= upper.z1) {
						continue;
					}
					if (outer) {
						d.set(x, y, z, wall.full());
						if (y + 1 < d.maxHeight()) {
							d.set(x, y + 1, z, coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
						}
					} else if (inWall) {
						d.set(x, y, z, coping.has(Form.SLAB) ? coping.slab(SlabType.BOTTOM) : wall.slab(SlabType.BOTTOM));
					}
				}
			}
			// vigas through the street wall head, spouts into the court
			if (t.walls() == WallKind.ADOBE) {
				for (int x = 2; x < w - 2; x += 2) {
					if (top - 1 >= 3) {
						d.setIfAbsent(x, top - 1, -1, beam.log(Direction.Axis.Z));
					}
				}
			}
			for (int[] c : new int[][]{{(cx0 + cx1) / 2, cz0 - 1, 0, 1}, {(cx0 + cx1) / 2, cz1 + 1, 0, -1}}) {
				d.setIfAbsent(c[0], y, c[1] + c[3], deck.stairsOrSlab(c[3] > 0 ? Dirs.FRONT : Dirs.BACK, Half.TOP));
			}
		}
		if (upper != null) {
			Roofs.flat(d, upper.x0, upper.z0, upper.x1, upper.z1, top + sh, true);
		}
		return y;
	}

	/**
	 * A roof over the ring falling both to the street and to the court from a ridge along the middle of each wing (a
	 * cloister roof), its valleys at the court's corners; curved with lifted outer corners in the karst; the upper wing
	 * roofed on its own over the ring's roof.
	 */
	private static Roofs.Roof ringRoof(Draft d, int w, int dp, int cx0, int cz0, int cx1, int cz1, int plate, boolean eastern, Wing upper, int top) {
		Roofs.Opts o = Roofs.Opts.of(d);
		double pitch = Math.min(o.pitch(), eastern ? 0.75 : 0.75);
		int eaves = Math.min(1, o.eaves());
		Roofs.Profile p = eastern ? Roofs.Profile.curved(1.0) : Roofs.Profile.straight(pitch);
		int ox0 = -eaves;
		int oz0 = -eaves;
		int ox1 = w - 1 + eaves;
		int oz1 = dp - 1 + eaves;
		// eaves over the court by one
		int ix0 = cx0 + 1;
		int iz0 = cz0 + 1;
		int ix1 = cx1 - 1;
		int iz1 = cz1 - 1;
		Roofs.Field ring = (x, z) -> {
			if (x < ox0 || x > ox1 || z < oz0 || z > oz1) {
				return Double.NaN;
			}
			if (x >= ix0 && x <= ix1 && z >= iz0 && z <= iz1) {
				return Double.NaN;
			}
			if (upper != null && z >= upper.z0) {
				return Double.NaN;
			}
			double toOuter = Math.min(Math.min(x - ox0, ox1 - x), Math.min(z - oz0, oz1 - z));
			double dxIn = x < ix0 ? ix0 - 1 - x : x > ix1 ? x - ix1 - 1 : -1;
			double dzIn = z < iz0 ? iz0 - 1 - z : z > iz1 ? z - iz1 - 1 : -1;
			double toInner = Math.max(dxIn, dzIn);
			return p.at(Math.max(0, Math.min(toOuter, toInner)));
		};
		int y0 = top + 1 - ((int) Math.round(p.at(eaves) * 2) + 1) / 2 + 1;
		Roofs.Roof r = Roofs.raster(d, ring, y0, Math.max(0, ox0), Math.max(-1, oz0), Math.min(w - 1, ox1), Math.min(dp - 1, oz1), d.mat(Role.ROOF),
			d.mat(Role.ROOF_TRIM), d.habits().roof().ridge());
		// the walls carried up to the roof's underside where the roof stands over them (the gable-less cloister has none)
		Mat wall = d.mat(Role.WALL);
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side side : Side.of(0, 0, w - 1, dp - 1)) {
				fillTo(d, r, side, top, wall);
			}
			for (Side side : Side.of(cx0 - 1, cz0 - 1, cx1 + 1, cz1 + 1)) {
				fillTo(d, r, side, top, wall);
			}
		}
		if (upper != null) {
			Roofs.Roof u = dev.voidmc.hearth.build.kit.townhome.Rooflines.plan(d, upper.x0, upper.z0, upper.x1, upper.z1, plate,
				eastern ? RoofForm.PAGODA : RoofForm.HIP, Direction.Axis.X, o.withPitch(pitch), false, false).draw(d).roof();
			return Roofs.merged(r, u);
		}
		return r;
	}

	private static void fillTo(Draft d, Roofs.Roof r, Side side, int top, Mat wall) {
		for (int i = 0; i < side.length(); i++) {
			int x = side.x(i);
			int z = side.z(i);
			if (!r.covers(x, z)) {
				continue;
			}
			for (int y = top + 1; y < r.bottomY(x, z); y++) {
				d.set(x, y, z, wall.full());
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- the court

	/**
	 * The court: paved, a fountain in the middle (a tree in the karst, a well or a tree where it is small), an arcade
	 * along the back where the court is deep enough, pots round its edge, a bench.
	 */
	private static void court(Draft d, int cx0, int cz0, int cx1, int cz1, int gate, int inner, Traits t, boolean eastern, boolean flat) {
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = cz0; z <= cz1; z++) {
				for (int x = cx0; x <= cx1; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
		int mx = (cx0 + cx1) / 2;
		int mz = (cz0 + cz1) / 2;
		int cw = cx1 - cx0 + 1;
		int cd = cz1 - cz0 + 1;
		if (eastern) {
			// a flowering tree in a raised bed, a spirit wall behind the gate
			Garden.tree(d, mx, 0, mz, 3);
			int sx = inner;
			int sz = cz0;
			if (d.isEmpty(sx, 0, sz + 1) && cd >= 4) {
				Mat accent = d.mat(Role.WALL_ACCENT);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int x = sx - 1; x <= sx + 1; x++) {
						if (x >= cx0 && x <= cx1 && x != sx) {
							d.set(x, 0, sz + 1, accent.full());
							d.set(x, 1, sz + 1, d.mat(Role.ROOF).slab(SlabType.BOTTOM));
						}
					}
				}
			}
		} else if (cw >= 3 && cd >= 3) {
			fountain(d, mx, mz, cw, cd);
		}
		d.poi(PoiType.MEETING_POINT, mx, 0, Math.min(cz1, mz + 2), Dirs.BACK);
		// an arcade (portico) along the back of the court on piers, its lean-to roof from the back wing
		if (cd >= 5 && !eastern) {
			portico(d, cx0, cx1, cz1, t);
		}
		// pots and planters round the edge (not before the doors)
		for (int x = cx0; x <= cx1; x++) {
			for (int z : new int[]{cz0, cz1}) {
				if (d.isEmpty(x, 0, z) && d.isAbsent(x, 0, z) && Math.abs(x - mx) > 0 && (x + z) % 2 == 0 && !nextToDoor(d, x, z)) {
					Garden.pot(d, x, 0, z);
				}
			}
		}
		for (int z = cz0 + 1; z < cz1; z++) {
			for (int x : new int[]{cx0, cx1}) {
				if (d.isAbsent(x, 0, z) && !nextToDoor(d, x, z) && (z % 3 == 0)) {
					Furniture.chair(d, x, 0, z, x == cx0 ? Dirs.LEFT : Dirs.RIGHT);
				}
			}
		}
	}

	private static boolean nextToDoor(Draft d, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			var s = d.get(x + Dirs.dx(dir), 0, z + Dirs.dz(dir));
			if (s != null && s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A fountain: a basin sunk flush with the paving (still water a block deep round a pillar carrying a bowl of water),
	 * three by three on a court of five or more; on a small court a bowl of water on a pedestal.
	 */
	private static void fountain(Draft d, int mx, int mz, int cw, int cd) {
		Mat rim = d.mat(Role.WALL_ACCENT);
		boolean big = cw >= 5 && cd >= 5;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (big) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dx = -1; dx <= 1; dx++) {
						if (dx != 0 || dz != 0) {
							d.set(mx + dx, -1, mz + dz, Blocks.WATER.defaultBlockState());
							d.set(mx + dx, -2, mz + dz, rim.full());
						}
					}
				}
			}
			d.set(mx, -1, mz, rim.full());
			d.set(mx, 0, mz, rim.has(Form.WALL) ? rim.wall() : rim.full());
			d.set(mx, 1, mz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
		}
	}

	/** A portico along the back of the court: piers at the court edge, arches between, a lean-to roof from the wall. */
	private static void portico(Draft d, int cx0, int cx1, int cz1, Traits t) {
		Mat pier = t.timber() && !t.masonry() ? d.mat(Role.FRAME) : d.mat(Role.WALL_ACCENT);
		Mat roof = d.mat(Role.ROOF);
		int z = cz1 - 1;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = cx0; x <= cx1; x += 2) {
				for (int y = 0; y <= 2; y++) {
					d.set(x, y, z, t.timber() && !t.masonry() ? pier.log(Direction.Axis.Y) : pier.full());
				}
			}
			if ((cx1 - cx0) % 2 != 0) {
				for (int y = 0; y <= 2; y++) {
					d.set(cx1, y, z, t.timber() && !t.masonry() ? pier.log(Direction.Axis.Y) : pier.full());
				}
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int x = cx0; x <= cx1; x++) {
				d.set(x, 3, z, roof.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				d.setIfAbsent(x, 3, z + 1, roof.slab(SlabType.TOP));
			}
		}
	}

	/** A stair up a side court wall to the roof terraces (or the back wing's upper storey), rising toward the back. */
	private static void courtStair(Draft d, int cx0, int cz0, int cx1, int cz1, int sh, boolean upper, boolean flat) {
		int rise = sh;
		int x = cx1;
		int z0 = cz1 - rise + 1;
		for (int z = z0 - 1; z <= cz1; z++) {
			for (int y = 0; y <= rise + 1; y++) {
				if (d.get(x, y, z) != null && !d.isEmpty(x, y, z) && !(d.get(x, y, z).getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock)) {
					d.remove(x, y, z);
				}
			}
		}
		Stairs.straight(d, x, 0, z0, Dirs.BACK, rise, 1);
		// the top arrives at the back wing's roof (or its upper door): cut the court-side parapet there
		int zTop = cz1 + 1;
		int fx = x;
		d.inStage(StageKind.ROOF, () -> {
			d.set(fx, rise, zTop, Blocks.AIR.defaultBlockState());
			d.set(fx, rise + 1, zTop, Blocks.AIR.defaultBlockState());
		});
		if (upper) {
			// the upper storey's door where the stair arrives
			Partitions.door(d, fx, sh, zTop, Dirs.BACK);
		}
	}

	/** The desert's wind tower: a square shaft over the back rooms rising past the roof, slatted near the top. */
	private static void windTower(Draft d, int w, int dp, int wd, int deck, int lift) {
		Mat wall = d.mat(Role.WALL);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat shutter = d.mat(Role.SHUTTER);
		int x0 = 1;
		int z0 = dp - 3;
		int base = deck + lift;
		int topY = Math.min(d.maxHeight() - 2, base + 4);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = x0; x <= x0 + 1; x++) {
				for (int z = z0; z <= z0 + 1; z++) {
					for (int y = base - 1; y <= topY; y++) {
						boolean slot = y >= topY - 1 && y < topY;
						d.set(x, y, z, slot ? shutter.trapdoor(Dirs.FRONT, Half.BOTTOM, true) : y == topY ? accent.full() : wall.full());
					}
				}
			}
			for (int x = x0; x <= x0 + 1; x++) {
				for (int z = z0; z <= z0 + 1; z++) {
					d.set(x, topY + 1, z, accent.slab(SlabType.BOTTOM));
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- rooms

	private static void rooms(Draft d, int w, int dp, int wd, int cx0, int cz0, int cx1, int cz1, int gate, int inner, int sh, boolean upper,
		Roofs.Roof roof) {
		int air = sh - 1;
		int va = Math.min(gate, inner) - 1;
		int vb = Math.max(gate, inner) + 1;
		Rooms.Room vest = room(va + 1, 1, vb - 1, wd - 2, air);
		if (vest != null) {
			keepDoors(d, vest);
			Rooms.furnish(d, vest, Rooms.Type.HALL, 0, JobType.NONE);
		}
		Rooms.Room kitchen = room(1, 1, va - 1, wd - 2, air);
		if (kitchen != null) {
			keepDoors(d, kitchen);
			hearth(d, kitchen);
			Rooms.furnish(d, kitchen, Rooms.Type.KITCHEN, 0, JobType.NONE);
		}
		Rooms.Room store = room(vb + 1, 1, w - 2, wd - 2, air);
		if (store != null) {
			keepDoors(d, store);
			Rooms.furnish(d, store, Rooms.Type.STORAGE, 0, JobType.NONE);
		}
		for (Rooms.Room r : new Rooms.Room[]{room(1, wd, cx0 - 2, cz1, air), room(cx1 + 2, wd, w - 2, cz1, air)}) {
			if (r != null) {
				keepDoors(d, r);
				Rooms.furnish(d, r, Rooms.Type.BEDROOM, 2, JobType.NONE);
			}
		}
		Rooms.Room hall = room(1, cz1 + 2, w - 2, dp - 2, air);
		int[] inside = null;
		if (upper && !stairOutside(d, cx1, cz1, sh)) {
			// no stair in the court: one inside along the hall's back wall, up through the floor
			int sx = w - 2 - sh;
			Stairs.straight(d, sx, 0, dp - 2, Dirs.RIGHT, sh, 1);
			inside = new int[]{sx, dp - 2};
			if (hall != null) {
				hall.keepArea(sx - 1, dp - 2, w - 2, dp - 2);
				hall.keep(sx - 1, dp - 2);
			}
		}
		if (hall != null) {
			keepDoors(d, hall);
			Rooms.furnish(d, hall, Rooms.Type.LIVING, 0, JobType.NONE);
			Rooms.furnish(d, hall, Rooms.Type.BEDROOM, upper ? 0 : 1, JobType.NONE);
		}
		if (upper) {
			Rooms.Room up = new Rooms.Room(1, cz1 + 2, w - 2, dp - 2, sh, air);
			keepDoors(d, up);
			if (inside != null) {
				up.keepArea(inside[0], dp - 2, w - 2, dp - 2);
				up.keep(w - 2, dp - 3);
			}
			for (int x = 1; x <= w - 2; x++) {
				var st = d.get(x, sh, cz1 + 1);
				if (st != null && st.isAir()) {
					up.keep(x, cz1 + 2);
				}
			}
			Rooms.furnish(d, up, Rooms.Type.BEDROOM, 2, JobType.NONE);
		}
	}

	/** Whether the court stair was built up the right court wall (its top step under the back wing). */
	private static boolean stairOutside(Draft d, int cx1, int cz1, int sh) {
		var st = d.get(cx1, sh - 1, cz1);
		return st != null && st.getBlock() instanceof net.minecraft.world.level.block.StairBlock;
	}

	/** Every door in the walls round a room keeps the cell inside it free. */
	private static void keepDoors(Draft d, Rooms.Room r) {
		for (int x = r.x0; x <= r.x1; x++) {
			for (int z : new int[]{r.z0 - 1, r.z1 + 1}) {
				if (isDoor(d, x, r.y, z)) {
					r.keep(x, z < r.z0 ? r.z0 : r.z1);
				}
			}
		}
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x : new int[]{r.x0 - 1, r.x1 + 1}) {
				if (isDoor(d, x, r.y, z)) {
					r.keep(x < r.x0 ? r.x0 : r.x1, z);
				}
			}
		}
	}

	private static boolean isDoor(Draft d, int x, int y, int z) {
		var st = d.get(x, y, z);
		return st != null && st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
	}

	private static Rooms.Room room(int x0, int z0, int x1, int z1, int air) {
		if (x1 < x0 || z1 < z0) {
			return null;
		}
		return new Rooms.Room(x0, z0, x1, z1, 0, air);
	}

	/** The kitchen's hearth against the street wall, its chimney through the roof. */
	private static void hearth(Draft d, Rooms.Room kitchen) {
		int x = kitchen.x0;
		int z = kitchen.z0;
		Furniture.hearth(d, x, 0, z, Dirs.BACK, false);
		kitchen.block(x, z);
		kitchen.keep(x, z + 1);
	}
}
