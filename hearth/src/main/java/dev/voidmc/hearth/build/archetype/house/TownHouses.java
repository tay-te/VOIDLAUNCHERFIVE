package dev.voidmc.hearth.build.archetype.house;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Floors;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.townhome.Fronts;
import dev.voidmc.hearth.build.kit.townhome.Life;
import dev.voidmc.hearth.build.kit.townhome.Rooflines;
import dev.voidmc.hearth.build.kit.townhome.StairCore;
import dev.voidmc.hearth.build.kit.townhome.Traits;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Habits.RoofForm;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The shared steps of the town homes that stand in rows (rowhouse, townhouse, shophouse, tenement): reading the
 * plot's party walls and corners, fitting the storeys and the roof under the plot's height, the shell, the openings
 * with their dress, rooflines that respect the neighbours, chimneys on the party walls, the stair, and the street in
 * front. Each archetype composes these with its own character.
 */
final class TownHouses {
	private TownHouses() {
	}

	/** A street runs along the viewer's left side too (the building's own right). */
	static boolean cornerLocalLeft(Draft d) {
		return d.param("corner_right", false);
	}

	/** A street runs along the viewer's right side too (the building's own left). */
	static boolean cornerLocalRight(Draft d) {
		return d.param("corner_left", false);
	}

	/**
	 * Storeys: the planner's suggestion (or {@code dflt}) moved a storey up or down by seed now and then, so a row of
	 * houses asked for at one height still steps its eaves, clamped to {@code [lo, hi]}.
	 */
	static int storeys(Draft d, int lo, int hi, int dflt) {
		int s = d.param("storeys", dflt);
		double u = d.random().nextDouble();
		if (u < 0.2) {
			s--;
		} else if (u > 0.8) {
			s++;
		}
		return Math.clamp(s, lo, hi);
	}

	/** How far the street in front of column x stands from the floor, beyond the step a doorway allows (0 if fine). */
	static int misfit(Draft d, int x) {
		int wy = d.site().water(x, -1);
		int s = Math.max(d.site().ground(x, -1), wy == Site.NONE ? Integer.MIN_VALUE : wy);
		return s > 0 ? s : s < -1 ? -1 - s : 0;
	}

	/**
	 * The front door's column: toward the side away from a lone neighbour (doors of a row pair up), else by seed, moved
	 * to the other side when the street meets the floor better there.
	 */
	static int doorColumn(Draft d, int x0, int x1, boolean pl, boolean pr) {
		int pref = pl && !pr ? x1 - 1 : pr && !pl ? x0 + 1 : d.chance(0.5) ? x0 + 1 : x1 - 1;
		int alt = pref == x0 + 1 ? x1 - 1 : x0 + 1;
		return misfit(d, alt) < misfit(d, pref) ? alt : pref;
	}

	/** Storey heights: {@code full} storeys of {@code sh}, the ground one {@code ground}, and a roof knee on top. */
	static int[] heights(int full, int sh, int ground, boolean knee) {
		int[] h = new int[full + (knee ? 1 : 0)];
		java.util.Arrays.fill(h, sh);
		h[0] = ground;
		if (knee) {
			h[full] = 1;
		}
		return h;
	}

	/** The roof's ridge (dry run) over a body's top storey. */
	static int ridge(Draft d, Body b, RoofForm form, Direction.Axis axis, Roofs.Opts o) {
		int[] tr = b.topRect();
		if (form == RoofForm.FLAT) {
			return b.plateY() + 2;
		}
		return Rooflines.ridgeY(d, tr[0], tr[1], tr[2], tr[3], b.plateY(), base(form), axis, o, b.partyLeft, b.partyRight);
	}

	static RoofForm base(RoofForm f) {
		return f == RoofForm.CROSS_GABLE ? RoofForm.GABLE : f;
	}

	/** A copy of a facade plan keeping the openings a test accepts (and all its posts). */
	static Facade filtered(Facade f, java.util.function.BiPredicate<Direction, Facade.Opening> keep) {
		Facade out = new Facade();
		for (Direction side : Dirs.HORIZONTAL) {
			for (Facade.Opening o : f.openings(side)) {
				if (keep.test(side, o)) {
					out.add(side, o);
				}
			}
			for (int p : f.posts(side)) {
				out.post(side, p);
			}
		}
		return out;
	}

	/**
	 * A facade plan with the front door exactly at {@code door} (width {@code doorW}): the bays are planned without a
	 * door and the windows that would crowd it are dropped, so the door stays where the street meets the floor and the
	 * plan built round it (the hall, the stair, the steps) agrees with it.
	 */
	static Facade planWithDoor(Draft d, Body b, int door, int doorW, int windowW, double density) {
		Facade f = Facade.plan(d, b, -1, 1, windowW, density);
		Facade out = filtered(f, (side, o) -> !(side == Dirs.FRONT && o.at() + o.width() - 1 >= door - 1 && o.at() <= door + doorW));
		out.add(Dirs.FRONT, new Facade.Opening(Facade.Kind.DOOR, door, doorW));
		return out;
	}

	/**
	 * A timber front framed in wide panels: every other post of the street wall left out, so its windows pair up in
	 * panels of white infill between strong posts (a grid of posts at every bay reads as a cage of timber).
	 */
	static Facade pairedPanels(Facade f) {
		Facade out = new Facade();
		for (Direction side : Dirs.HORIZONTAL) {
			for (Facade.Opening o : f.openings(side)) {
				out.add(side, o);
			}
			List<Integer> posts = new java.util.ArrayList<>(f.posts(side));
			java.util.Collections.sort(posts);
			for (int i = 0; i < posts.size(); i++) {
				if (side != Dirs.FRONT || (i & 1) == 1 || posts.size() == 1) {
					out.post(side, posts.get(i));
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------- shell

	/**
	 * Floors and walls storey by storey, beam ends on framed storeys, the rooms' air. With {@code rusticate}, a
	 * rendered house's ground storey is faced in its plinth stone (a stone base under plaster storeys).
	 */
	static void shell(Draft d, Body b, Facade f, boolean rusticate) {
		for (int k = 0; k < b.storeys(); k++) {
			int[] r = b.rect(k);
			Floors.boards(d, r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, b.floorY(k) - 1);
			if (k > 0) {
				Floors.boards(d, r[0], r[1], r[2], r[3], b.floorY(k) - 1);
			}
			Walls.storey(d, b, k, f);
			if (k > 0 && d.chance(d.habits().walls().beamEnds())) {
				Walls.beamEnds(d, b, k, f, Dirs.FRONT);
			}
		}
		if (rusticate) {
			Mat stone = d.mat(Role.PLINTH);
			int top = b.floorY(1) - 2;
			try (var s = d.stage(StageKind.WALLS)) {
				for (Side side : b.sides(0)) {
					if (b.isParty(side.out())) {
						continue;
					}
					for (int i = 0; i < side.length(); i++) {
						for (int y = b.baseY; y <= top; y++) {
							if (Openings.isWallBlock(d, side.x(i), y, side.z(i))) {
								d.set(side.x(i), y, side.z(i), stone.full());
							}
						}
					}
				}
			}
		}
		for (int k = 0; k < b.storeys(); k++) {
			int[] r = b.rect(k);
			d.carve(r[0] + 1, b.floorY(k), r[1] + 1, r[2] - 1, b.floorY(k + 1) - 2, r[3] - 1);
		}
	}

	/**
	 * The column to hand {@link Openings} for an opening {@code at..at+width-1}: Openings walks a wall in its own cell
	 * order (left to right seen from outside), which runs against the coordinate on the back and left walls, so a wide
	 * opening there starts at its high coordinate (else it slides a cell toward the corner).
	 */
	static int firstCell(Side s, int at, int width) {
		Direction a = s.along();
		boolean positive = Dirs.dx(a) + Dirs.dz(a) > 0;
		return positive ? at : at + width - 1;
	}

	/** The window style the culture glazes with. */
	static Openings.WindowStyle windowStyle(Draft d, Traits t) {
		Habits.Windows hw = d.habits().windows();
		return hw.lattice() ? Openings.WindowStyle.LATTICE : d.chance(hw.arched()) ? Openings.WindowStyle.ARCHED : Openings.WindowStyle.DRESSED;
	}

	/**
	 * The front door and every storey's windows from the facade plan (the first floor's the tallest, the top's the
	 * smallest in masonry houses), skipping storeys listed in {@code skip} (a shop front, a loggia).
	 */
	static void openings(Draft d, Body b, Facade f, Traits t, Openings.WindowStyle style, java.util.Set<Integer> skipFront, boolean lamp) {
		boolean tall = d.habits().windows().tall();
		int top = b.storeys() - 1;
		for (int k = 0; k < b.storeys(); k++) {
			int sh = b.height(k);
			if (sh < 3) {
				continue;
			}
			for (Side s : b.sides(k)) {
				if (b.isParty(s.out()) || s.out() == Dirs.FRONT && skipFront.contains(k)) {
					continue;
				}
				for (Facade.Opening o : f.openings(s.out())) {
					if (!s.contains(o.at()) || !s.contains(o.at() + o.width() - 1)) {
						continue;
					}
					int y = b.floorY(k);
					int col = firstCell(s, o.at(), o.width());
					if (o.kind() == Facade.Kind.DOOR && k == 0) {
						Openings.door(d, s, col, y, o.width(), new Openings.DoorOpts(lamp, false, true));
					} else if (o.kind() != Facade.Kind.BLANK) {
						boolean framed = d.habits().walls().kind(k) == WallKind.TIMBER_FRAME;
						int h = framed ? (tall && k < top ? 2 : 1) : tall && (k == 1 || k < top || !t.masonryUpper()) ? 2 : 1;
						if (k == 0 && o.kind() == Facade.Kind.DOOR) {
							continue;
						}
						if (framed && style == Openings.WindowStyle.DRESSED) {
							// a framed wall's girts and posts are its relief: a flower box under some windows, no sill board
							Habits.Windows hw = d.habits().windows();
							boolean box = d.chance(hw.boxes());
							Openings.window(d, s, col, y + 1, o.width(), Math.min(h, sh - 2), style,
								new Openings.WindowOpts(d.chance(hw.shutters()), box, box, true));
						} else {
							Openings.window(d, s, col, y + 1, o.width(), Math.min(h, sh - 2), style);
						}
					}
				}
			}
		}
	}

	/**
	 * The dress of a masonry facade: a moulded band at every upper floor and a cornice under a flat roof's parapet on
	 * the front (and a corner's side), painted corner strips on rendered houses (also at party walls, so each house of a
	 * row reads on its own).
	 */
	static void dress(Draft d, Body b, Traits t, boolean flatRoof, boolean sideStreet, Direction sideOut) {
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat trim = d.mat(Role.WINDOW).has(Form.STAIRS) ? d.mat(Role.WINDOW) : accent;
		for (int k = 1; k < b.storeys(); k++) {
			if (b.height(k) < 3 || b.outset(k, Dirs.FRONT) != b.outset(k - 1, Dirs.FRONT)) {
				continue;
			}
			if (t.masonryUpper() || t.walls() == WallKind.PLASTER) {
				Side s = b.side(k, Dirs.FRONT);
				Fronts.band(d, s, 0, s.last(), b.floorY(k) - 1, accent);
				if (sideStreet) {
					Side ss = b.side(k, sideOut);
					Fronts.band(d, ss, 0, ss.last(), b.floorY(k) - 1, accent);
				}
			}
		}
		if (flatRoof) {
			Side s = b.side(b.storeys() - 1, Dirs.FRONT);
			Fronts.band(d, s, 0, s.last(), b.plateY(), trim);
		}
		if (t.walls() == WallKind.PLASTER) {
			for (int k = 0; k < b.storeys(); k++) {
				if (b.height(k) < 3) {
					continue;
				}
				Side s = b.side(k, Dirs.FRONT);
				int y0 = k == 0 ? b.baseY + Math.max(0, d.habits().walls().plinth()) : b.floorY(k) - 1;
				Fronts.corners(d, s, y0, b.floorY(k + 1) - 1, accent);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- roofs

	/**
	 * A pitched roof over the body that respects its party walls: gabled at a party wall, hips only on free sides; on a
	 * masonry house the party gables rise as coped parapets and a stone front gable is crow-stepped; dormers over the
	 * front windows when the ridge runs along the street; a window in a front gable.
	 */
	static Roofs.Roof pitched(Draft d, Body b, RoofForm form, Direction.Axis ridge, Roofs.Opts o, Traits t, Facade f, boolean dormers, boolean crow) {
		int[] tr = b.topRect();
		int plate = b.plateY();
		RoofForm base = base(form);
		Roofs.Plan plan = Rooflines.plan(d, tr[0], tr[1], tr[2], tr[3], plate, base, ridge, o, b.partyLeft, b.partyRight);
		int topK = b.storeys() - 1;
		Side front = b.side(topK, Dirs.FRONT);
		int width = tr[2] - tr[0] + 1;
		if (form == RoofForm.CROSS_GABLE && ridge == Direction.Axis.X && width >= 5) {
			int gw = width >= 8 ? 5 : 3;
			int col = Math.clamp((tr[0] + tr[2]) / 2 - gw / 2, tr[0] + (b.partyLeft ? 0 : 1), tr[2] - gw + (b.partyRight ? 1 : 0));
			plan.crossGable(front, col, gw, d);
		} else if (dormers && ridge == Direction.Axis.X && base != RoofForm.PAGODA && base != RoofForm.GAMBREL && o.eaves() <= 1) {
			for (Facade.Opening op : f.openings(Dirs.FRONT)) {
				if (op.kind() != Facade.Kind.WINDOW || op.at() - 1 <= tr[0] || op.at() + op.width() >= tr[2]) {
					continue;
				}
				plan.dormer(front, op.at(), op.width(), b.floorY(topK));
			}
		}
		Roofs.Roof roof = plan.draw(d).roof();
		if ((base == RoofForm.PAGODA || o.flare()) && (b.partyLeft || b.partyRight)) {
			Rooflines.untip(d, roof, b.partyLeft, b.partyRight);
		}
		int maxY = d.maxHeight();
		// party gables as parapets on masonry houses (a fire break the street reads house by house); the karst
		// tradition steps its fire walls up the roof and caps them with tiles
		if (ridge == Direction.Axis.X && t.eastern()) {
			Mat wall = d.mat(Role.WALL_ACCENT);
			Mat cap = d.mat(Role.ROOF);
			if (b.partyLeft) {
				Rooflines.crowSteps(d, roof, b.side(topK, Dirs.LEFT), maxY, wall, cap);
			}
			if (b.partyRight) {
				Rooflines.crowSteps(d, roof, b.side(topK, Dirs.RIGHT), maxY, wall, cap);
			}
		} else if (ridge == Direction.Axis.X && (t.masonryUpper() || t.walls() == WallKind.STONE || t.walls() == WallKind.BRICK)) {
			if (b.partyLeft) {
				Rooflines.parapet(d, roof, b.side(topK, Dirs.LEFT), maxY);
			}
			if (b.partyRight) {
				Rooflines.parapet(d, roof, b.side(topK, Dirs.RIGHT), maxY);
			}
		}
		if (ridge == Direction.Axis.Z && crow) {
			Rooflines.crowSteps(d, roof, b.side(topK, Dirs.FRONT), maxY);
		}
		// a window in a front gable that has room for one
		if (ridge == Direction.Axis.Z && base != RoofForm.HIP && base != RoofForm.MANSARD && base != RoofForm.PAGODA) {
			int mid = front.middle();
			int x = front.x(mid);
			int z = front.z(mid);
			int under = roof.covers(x, z) ? roof.bottomY(x, z) : Integer.MIN_VALUE;
			int wy = plate + 1;
			if (under - wy >= 3 && Openings.isWallBlock(d, x, wy, z)) {
				Openings.window(d, front, front.column(mid), wy, width >= 7 && front.length() % 2 == 0 ? 2 : 1, 2, Openings.WindowStyle.PLAIN,
					new Openings.WindowOpts(false, d.chance(0.4), true, true));
			} else if (under - wy >= 2 && Openings.isWallBlock(d, x, wy, z)) {
				Openings.window(d, front, front.column(mid), wy, 1, 1, Openings.WindowStyle.PLAIN, new Openings.WindowOpts(false, false, true, false));
			}
		}
		return roof;
	}

	/**
	 * A hoist beam under the apex of a front gable (the merchant houses of a harbour or a market street): a beam
	 * through the gable a block out with a chain hanging from its end.
	 */
	static void hoist(Draft d, Body b, Roofs.Roof roof) {
		int[] tr = b.topRect();
		int x = (tr[0] + tr[2]) / 2;
		int z = tr[1] - 1;
		int y = roof.covers(x, tr[1]) ? roof.bottomY(x, tr[1]) - 1 : Integer.MIN_VALUE;
		if (y < 6 || !Fronts.allowed(d, x, y, z) || !d.isAbsent(x, y, z) || !d.isAbsent(x, y - 1, z)) {
			return;
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, d.mat(Role.BEAM).log(Direction.Axis.Z));
			d.set(x, y - 1, z, dev.voidmc.hearth.build.draft.States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
		}
	}

	/**
	 * A flat roof deck over the top storey with its parapet (crenellated where the culture builds merlons), returning
	 * the deck's standing level.
	 */
	static int flat(Draft d, Body b, boolean crenels) {
		int[] tr = b.topRect();
		int deck = Roofs.flat(d, tr[0], tr[1], tr[2], tr[3], b.plateY(), true);
		if (crenels && deck + 2 < d.maxHeight()) {
			Mat wall = d.mat(Role.WALL);
			try (var s = d.stage(StageKind.ROOF)) {
				for (Side side : Side.of(tr[0], tr[1], tr[2], tr[3])) {
					if (b.isParty(side.out())) {
						continue;
					}
					for (int i = 0; i < side.length(); i += 2) {
						int x = side.x(i);
						int z = side.z(i);
						if (d.isSolid(x, deck, z) || d.get(x, deck + 1, z) != null) {
							d.set(x, deck + 1, z, wall.full());
							if (deck + 2 < d.maxHeight()) {
								d.set(x, deck + 2, z, d.mat(Role.WALL_ACCENT).slab(SlabType.BOTTOM));
							}
						}
					}
				}
			}
		}
		return deck;
	}

	/**
	 * A chimney on the side away from the stair well: built in the wall line when that side is a party wall (the stack
	 * rises out of the party wall over the roof, its hearth inside against the wall), else against the side wall inside,
	 * up through the floors as a chimney breast. Returns the hearth cell {x, z} or null.
	 */
	static int[] chimney(Draft d, Body b, Roofs.Roof roof, int deck, boolean wellLeft, double chance) {
		return chimney(d, b, roof, deck, wellLeft, chance, 0);
	}

	/** {@link #chimney} with the hearth on storey {@code hearthK} (a shop keeps its fire upstairs in the home). */
	static int[] chimney(Draft d, Body b, Roofs.Roof roof, int deck, boolean wellLeft, double chance, int hearthK) {
		if (!d.chance(chance)) {
			return null;
		}
		int[] r = b.rect(hearthK);
		int[] tr = b.topRect();
		int hy = b.floorY(hearthK);
		boolean left = !wellLeft;
		boolean party = left ? b.partyLeft : b.partyRight;
		int wallX = left ? tr[0] : tr[2];
		int hx = left ? r[0] + 1 : r[2] - 1;
		int hz = Math.max(r[1] + 1, Math.min(r[3] - 1, (r[1] + r[3]) / 2));
		if (roof != null) {
			if (party) {
				if (!Rooflines.wallStack(d, wallX, hz, b.plateY() - 1, roof, d.maxHeight())) {
					return null;
				}
			} else {
				int surface = roof.covers(hx, hz) ? roof.surfaceY(hx, hz) : b.plateY();
				if (surface + 2 >= d.maxHeight()) {
					return null;
				}
				dev.voidmc.hearth.build.kit.Chimneys.stack(d, hx, hz, hy + 2, roof, Math.min(roof.ridgeY + 1, d.maxHeight() - 1));
			}
		} else if (deck != Integer.MIN_VALUE) {
			if (deck + 2 >= d.maxHeight()) {
				return null;
			}
			Mat stone = d.mat(Role.CHIMNEY);
			int x = party ? wallX : hx;
			try (var s = d.stage(StageKind.ROOF)) {
				for (int y = party ? b.plateY() - 1 : hy + 2; y < deck + 1; y++) {
					d.set(x, y, hz, stone.full());
				}
				d.set(x, deck + 1, hz, dev.voidmc.hearth.build.draft.States.lit(Blocks.CAMPFIRE, true)
					.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SIGNAL_FIRE, false));
			}
		} else {
			return null;
		}
		Furniture.hearth(d, hx, hy, hz, left ? Dirs.RIGHT : Dirs.LEFT, false);
		return new int[]{hx, hz};
	}

	// ------------------------------------------------------------------------------------------------- stair, rooms

	/**
	 * A stair well against the side wall away from the door, at the back: every storey's switchback from the ground up
	 * to {@code toK}.
	 */
	static StairCore core(Draft d, Body b, int door, int toK) {
		int[] r0 = b.rect(0);
		int ix0 = r0[0] + 1;
		int ix1 = r0[2] - 1;
		int iz1 = r0[3] - 1;
		boolean left = door > (r0[0] + r0[2]) / 2;
		int laneA = left ? ix0 : ix1;
		Direction across = left ? Dirs.RIGHT : Dirs.LEFT;
		int zStart = iz1 - 2;
		return new StairCore(b, laneA, across, zStart, Dirs.BACK, 0, toK).build(d);
	}

	/** A storey's room, the stair kept, the top one carved up under the roof. */
	static Rooms.Room room(Draft d, Body b, int k, Roofs.Roof roof) {
		int[] r = b.rect(k);
		int y = b.floorY(k);
		int h = b.height(k) - 1;
		if (k == b.storeys() - 1 && roof != null) {
			for (int z = r[1] + 1; z <= r[3] - 1; z++) {
				for (int x = r[0] + 1; x <= r[2] - 1; x++) {
					int under = roof.covers(x, z) ? roof.bottomY(x, z) : y + 2;
					for (int yy = y; yy < under; yy++) {
						if (d.isAbsent(x, yy, z)) {
							d.set(x, yy, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
			h = 2;
		}
		if (r[2] - r[0] < 2 || r[3] - r[1] < 2) {
			return null;
		}
		return new Rooms.Room(r[0] + 1, r[1] + 1, r[2] - 1, r[3] - 1, y, Math.max(2, h));
	}

	/** Exposed joists under each floor above the ground, never across the stair's well or its landing rows. */
	static void joists(Draft d, Body b, StairCore core) {
		int[] zr = core == null ? new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE} : core.zRange();
		for (int k = 0; k + 1 < b.storeys(); k++) {
			if (b.height(k) < 4) {
				continue;
			}
			int[] r = b.rect(k);
			int ceiling = b.floorY(k + 1) - 1;
			for (int z = r[1] + 2; z <= r[3] - 1; z += 2) {
				if (z >= zr[0] - 1 && z <= zr[1] + 1) {
					continue;
				}
				Floors.joists(d, r[0] + 1, z - 1, r[2] - 1, z + 1, ceiling, Direction.Axis.X, 2, b.floorY(k));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- street

	/**
	 * The street in front: steps or an approach to a door that does not meet the street, else a paved front row with
	 * pots and a bench, a boot scraper's worth of life.
	 */
	static void street(Draft d, Body b, int door, boolean awkward, int doorY) {
		int z0 = b.rect(0)[1];
		if (awkward || doorY != 0) {
			Groundwork.approach(d, door, doorY, z0 - 1);
			return;
		}
		if (z0 <= 0) {
			return;
		}
		int g = d.site().ground(door, z0 - 1);
		if (g < -1 || d.site().isWater(door, z0 - 1)) {
			Groundwork.approach(d, door, 0, z0 - 1);
			return;
		}
		// the strip in front: cut down to the floor where the ground rises, made up where it dips a block, paved
		Mat paving = d.mat(Role.PAVING);
		Mat found = d.mat(Role.FOUNDATION);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < z0; z++) {
				for (int x = 0; x < d.frontage(); x++) {
					int gx = d.site().ground(x, z);
					if (d.site().isWater(x, z) || gx < -2) {
						continue;
					}
					if (d.isAbsent(x, -1, z)) {
						d.set(x, -1, z, paving.full());
						for (int y = gx - 1; y < -1; y++) {
							d.set(x, y, z, found.full());
						}
					}
					for (int y = 0; y < gx; y++) {
						if (d.isAbsent(x, y, z) || d.isEmpty(x, y, z)) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
		// where the cut left a bank against the neighbour plot's ground, nothing more is needed: the plot ends there
		Fronts.streetLife(d, 0, d.frontage() - 1, z0 - 1, 0, door);
		if (d.site().ground(door, -1) > 0 || d.site().ground(door, -1) < -1) {
			Groundwork.approach(d, door, 0, z0 - 1);
		}
	}

	/**
	 * The pueblo's way up: no stair inside, a ladder from each level to the terrace above it against the wall in front
	 * (its poles standing up past the parapet), a gap in the parapet where it arrives; the last one climbs to the roof.
	 */
	static void puebloLadders(Draft d, Body b, int full, int deck, int door) {
		Mat fence = d.mat(Role.FENCE);
		for (int k = 1; k <= full; k++) {
			boolean roof = k == full;
			if (!roof && b.outset(k, Dirs.FRONT) == b.outset(k - 1, Dirs.FRONT)) {
				continue;
			}
			int[] below = b.rect(k - 1);
			int z = below[1] - 1;
			int y0 = k - 1 == 0 ? b.baseY : b.floorY(k - 1);
			int top = roof ? deck - 1 : b.floorY(k) - 1;
			// a column away from the doors in the middle
			boolean right = (door <= (below[0] + below[2]) / 2) == ((k & 1) == 1);
			// the first column from the chosen side whose wall is solid all the way up (a ladder needs a wall)
			int x = Integer.MIN_VALUE;
			for (int n = 1; n < below[2] - below[0] && x == Integer.MIN_VALUE; n++) {
				int c = right ? below[2] - n : below[0] + n;
				if (c == door && k == 1) {
					continue;
				}
				boolean solid = true;
				for (int y = y0; y <= top && solid; y++) {
					solid = Openings.isWallBlock(d, c, y, z + 1);
				}
				if (solid && !d.isSolid(c, y0, z)) {
					x = c;
				}
			}
			if (x == Integer.MIN_VALUE || z < 0 && y0 < 3 || top + 3 >= d.maxHeight()) {
				continue;
			}
			int fx = x;
			d.inStage(StageKind.FLOORS, () -> {
				for (int y = y0; y <= top; y++) {
					d.set(fx, y, z, dev.voidmc.hearth.build.draft.States.facing(Blocks.LADDER, Dirs.FRONT));
				}
			});
			// the gap where it arrives, the poles beside it standing past the parapet
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x, top + 1, z + 1, Blocks.AIR.defaultBlockState());
				d.set(x, top + 2, z + 1, Blocks.AIR.defaultBlockState());
				for (int px : new int[]{x - 1, x + 1}) {
					if (px >= below[0] && px <= below[2] && d.get(px, top + 1, z + 1) != null && !d.isEmpty(px, top + 1, z + 1)) {
						d.set(px, top + 2, z + 1, fence.fence());
						d.set(px, top + 3, z + 1, fence.fence());
					}
				}
			}
		}
	}

	/** The yard behind: a paved strip or the culture's garden, a water butt, barrels, a washing line where it fits. */
	static void yard(Draft d, Body b, int lineY) {
		int[] r = b.rect(0);
		int z0 = r[3] + 1;
		int z1 = d.depth() - 1;
		if (z1 < z0) {
			return;
		}
		boolean dry = !d.site().anyWater(0, z0, d.frontage() - 1, z1);
		if (!dry) {
			return;
		}
		Groundwork.level(d, 0, z0, d.frontage() - 1, z1, 0);
		if (z1 - z0 >= 1) {
			Garden.garden(d, 1, z1, d.frontage() - 2, z1, 0);
		}
		if (d.isAbsent(0, 0, z0) && !b.partyLeft) {
			Garden.waterButt(d, 0, 0, z0);
		} else if (d.isAbsent(d.frontage() - 1, 0, z0)) {
			Garden.barrels(d, d.frontage() - 1, 0, z0, Dirs.BACK, Math.min(2, z1 - z0 + 1));
		}
	}

	/** Cells inside doors cut through a storey's front wall (onto balconies, galleries, terraces) stay free. */
	static void keepDoors(Draft d, Rooms.Room room, int[] r, int y) {
		for (int x = r[0] + 1; x <= r[2] - 1; x++) {
			if (d.get(x, y, r[1]) != null && d.get(x, y, r[1]).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				room.keep(x, r[1] + 1);
			}
		}
	}

	/** Furnishes a storey as {@code type}, keeping the stair and any extra cells. */
	static void furnish(Draft d, Rooms.Room room, StairCore core, int k, Rooms.Type type, int beds, List<int[]> keep) {
		if (room == null) {
			return;
		}
		if (core != null) {
			core.keep(room, k);
		}
		for (int[] c : keep) {
			room.keep(c[0], c[1]);
		}
		Rooms.furnish(d, room, type, beds, JobType.NONE);
	}

	/** Upside-down stairs of the roof trim as brackets under a balcony or oriel's floor edge. */
	static void bracket(Draft d, int x, int y, int z, Direction in) {
		if (Fronts.allowed(d, x, y, z) && d.isAbsent(x, y, z)) {
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, d.mat(Role.BEAM).stairsOrSlab(in, Half.TOP)));
		}
	}

	/** The pueblo's terraces: each lower storey's roof in front of the next one up, with a door out and a ladder up. */
	static void terraces(Draft d, Body body, int full) {
		for (int k = 1; k < full; k++) {
			if (body.outset(k, Dirs.FRONT) == body.outset(k - 1, Dirs.FRONT)) {
				continue;
			}
			int ty = Roofs.terrace(d, body.rect(k - 1), body.rect(k), body.floorY(k) - 1);
			Side up = body.side(k, Dirs.FRONT);
			int mid = up.middle();
			int ux = up.x(mid);
			int uz = up.z(mid);
			d.inStage(StageKind.OPENINGS, () -> Fronts.plainDoor(d, ux, ty, uz, Dirs.FRONT));
			d.poi(PoiType.VIEWPOINT, up.outX(mid, 1), ty, up.outZ(mid, 1), Dirs.FRONT);
		}
	}

	/** A flat-roofed house's top storey set back behind a roof terrace with a door out, a pergola and pots. */
	static int setbackTerrace(Draft d, Body body, int full) {
		int ty = Roofs.terrace(d, body.rect(full - 2), body.rect(full - 1), body.floorY(full - 1) - 1);
		Side up = body.side(full - 1, Dirs.FRONT);
		int mid = up.middle();
		int tx = up.x(mid);
		int tz = up.z(mid);
		Fronts.plainDoor(d, tx, ty, tz, Dirs.FRONT);
		int[] lower = body.rect(full - 2);
		Life.roofGarden(d, lower[0] + 1, lower[1] + 1, lower[2] - 1, tz - 1, ty, tx, tz - 1, false);
		d.poi(PoiType.VIEWPOINT, tx, ty, tz - 1, Dirs.FRONT);
		return tx;
	}

	/** A door at a walkway's level in the front wall, where a walkway from across the street meets the house. */
	static void walkwayDoor(Draft d, Body b, int walkY) {
		int w = d.frontage();
		int c = w / 2;
		Side front = null;
		int k = -1;
		for (int s = 1; s < b.storeys(); s++) {
			if (b.floorY(s) == walkY) {
				k = s;
			}
		}
		if (k < 0) {
			return;
		}
		front = b.side(k, Dirs.FRONT);
		if (!front.contains(c) || front.isCorner(front.index(c))) {
			return;
		}
		int x = front.x(front.index(c));
		int z = front.z(front.index(c));
		Fronts.plainDoor(d, x, walkY, z, Dirs.FRONT);
		if (w % 2 == 0 && front.contains(c - 1) && !front.isCorner(front.index(c - 1))) {
			Fronts.plainDoor(d, front.x(front.index(c - 1)), walkY, z, Dirs.FRONT);
		}
		d.entrance(x, walkY, z - 1);
		d.poi(PoiType.DOOR, x, walkY, z - 1, Dirs.BACK);
	}

	/** A door from storey k onto a gallery or balcony over the door's column (where the window over the door was). */
	static void galleryDoor(Draft d, Side s, int y, int door) {
		int i = s.index(door);
		if (i <= 0 || i >= s.last()) {
			i = s.middle();
		}
		int ox = s.outX(i, 1);
		int oz = s.outZ(i, 1);
		if (d.get(ox, y - 1, oz) == null || d.isEmpty(ox, y - 1, oz)) {
			return;
		}
		Fronts.plainDoor(d, s.x(i), y, s.z(i), s.out());
	}

}
