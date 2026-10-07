package dev.voidmc.hearth.build.archetype.civic;

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
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.civic.Adobe;
import dev.voidmc.hearth.build.kit.civic.Eaves;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.learning.Instruments;
import dev.voidmc.hearth.build.kit.learning.Sacra;
import dev.voidmc.hearth.build.kit.learning.Studies;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:civic/observatory}: the city's observatory, on the rise. Two manners:
 * <ul>
 *   <li><b>The astronomers' tower</b> (temperate, wisteria, the north, the mountains, moorland and maritime, the
 *   Mediterranean, bayou and the tropics): a tower nine or eleven across, square with dressed corners where the culture
 *   squares its towers, eight-sided where it rounds them, on a chamfered plinth, a string course at every floor, a newel
 *   stair winding up its middle through the library on the ground floor and the instrument rooms over it to the
 *   observing room under a smooth dome of lead or copper on a round drum with its slit to the sky and the telescope
 *   aimed through it. A gallery runs round the domed room behind a balustrade: corbelled out past the walls where the
 *   plot leaves room, else on the tower's head round a smaller domed lantern. A
 *   sundial lies in the forecourt.</li>
 *   <li><b>The terrace observatory</b> (desert, mesa, steppe, karst): a massive platform of two stepped storeys (the
 *   library and the instrument store inside), its roof a terrace of instruments behind a parapet: armillary spheres of
 *   bronze on stone stands, a celestial globe, a quadrant; in the dry lands the great gnomon of a sundial rises as a
 *   stair up the platform's front (the Samrat Yantra), a domed kiosk at its head; in the karst a straight stair climbs
 *   the side to a pavilion under a curved roof.</li>
 * </ul>
 * POIs: SCHOLAR JOB_SITEs and LECTERNs at the lecterns, VIEWPOINTs on the gallery or terrace, TABLE, SEAT, STORAGE,
 * DOOR.
 *
 * <p>Params: {@code form} ({@code "tower"} or {@code "terrace"}; default by culture), {@code variant} (int).
 */
public final class Observatory implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("civic/observatory"), ArchetypeCategory.CIVIC, Set.of(JobType.SCHOLAR), 0,
		11, 13, 11, 13, 16, 26, SettlementTier.CITY, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Tradition t = Tradition.of(d);
		boolean terrace = t == Tradition.ADOBE || t == Tradition.STEPPE || t == Tradition.KARST;
		String form = d.param("form", "");
		if (form.equals("tower")) {
			terrace = false;
		} else if (form.equals("terrace")) {
			terrace = true;
		}
		int peak = terrace ? terraceObservatory(d, t) : tower(d, t);
		Groundwork.clear(d, 0, 0, d.frontage() - 1, d.depth() - 1, Math.min(d.maxHeight() - 1, peak + 2));
	}

	// ================================================================================================ the tower

	/** The tower's plan: its square or octagon, its floors, the gallery. */
	private static final class Plan {
		int cx;
		int cz;
		int r;            // half-width of the tower (4 or 5)
		int cut;          // 0 square, else the octagon's corner cut
		Set<Long> foot;   // the walls' outline and everything inside
		int[] floors;     // standing levels: ground .. the observing room
		boolean outer;    // the gallery corbelled out past the walls (else set back on the tower's head)
		int wallTop;      // the last course of the observing room's walls (the dome springs over it)

		boolean in(int x, int z) {
			return this.foot.contains(Shapes.cell(x, z));
		}

		boolean wall(int x, int z) {
			if (!in(x, z)) {
				return false;
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				if (!in(x + Dirs.dx(dir), z + Dirs.dz(dir))) {
					return true;
				}
			}
			return false;
		}

		/** Chebyshev distance from the axis. */
		int dist(int x, int z) {
			return Math.max(Math.abs(x - this.cx), Math.abs(z - this.cz));
		}
	}

	private static int tower(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Plan p = new Plan();
		int min = Math.min(w, dp);
		p.r = min >= 13 ? 5 : 4;
		int size = 2 * p.r + 1;
		p.outer = w >= size + 2 && dp >= size + 2;
		if (!p.outer && p.r > 4) {
			p.r = 4;
			size = 9;
			p.outer = w >= size + 2 && dp >= size + 2;
		}
		boolean round = t == Tradition.STONE || t == Tradition.ALPINE || t == Tradition.LOG;
		p.cut = round ? (p.r >= 5 ? 3 : 2) : 0;
		p.cx = (w - 1) / 2;
		// the tower to the back, the forecourt in front (a row behind it for the gallery when it is corbelled out)
		p.cz = dp - 1 - p.r - (p.outer ? 1 : 0);
		p.foot = Shapes.oct(p.cx, p.cz, p.r, p.cut);
		int x0 = p.cx - p.r;
		int z0 = p.cz - p.r;
		d.body(x0, z0, p.cx + p.r, p.cz + p.r);

		// --- the heights: the dome over the observing room, the floors under it
		int domeR = p.r;
		int domeRows = domeRows(0, 0, domeR).size();
		p.wallTop = top - domeRows - 1;
		int ft = p.wallTop - 3;
		List<Integer> fl = new ArrayList<>();
		fl.add(0);
		if (ft >= 15) {
			fl.add(ft >= 16 ? 6 : 5);
			fl.add((fl.get(1) + ft) / 2 + ((ft - fl.get(1)) % 2 == 0 ? 0 : 0));
		} else if (ft >= 9) {
			fl.add(ft / 2 + 1 > ft - 4 ? ft - 4 : ft / 2 + 1);
		}
		fl.add(ft);
		p.floors = fl.stream().mapToInt(Integer::intValue).toArray();
		d.eave(p.wallTop);

		Stone body = t == Tradition.STONE || t == Tradition.LOG || t == Tradition.STILT ? Stone.panel(d) : Stone.panel(d).crisp();
		Stone dress = Stone.dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);

		// --- footings, the plinth with its chamfer, the walls with a course at every floor
		Grounds.footings(d, x0, z0, p.cx + p.r, p.cz + p.r, -1);
		int shaftTop = p.wallTop;
		walls(d, p, body, dress, base, 0, shaftTop);
		if (p.cut == 0 && p.outer) {
			// clasping piers at the corners, from the plinth to the gallery's brackets
			try (var s = d.stage(StageKind.WALLS)) {
				for (int sx : new int[]{-1, 1}) {
					for (int sz : new int[]{-1, 1}) {
						int px = p.cx + sx * p.r;
						int pz = p.cz + sz * p.r;
						for (int[] c : new int[][]{{px + sx, pz}, {px, pz + sz}, {px + sx, pz + sz}}) {
							int g = d.site().ground(c[0], c[1]);
							for (int y = Math.min(0, g) - 1; y <= ft - 3; y++) {
								d.set(c[0], y, c[1], y < 0 ? d.mat(Role.FOUNDATION).full() : y <= 1 ? base.full() : dress.full());
							}
							d.set(c[0], ft - 2, c[1], dress.full());
						}
					}
				}
			}
		}
		for (int k = 1; k < p.floors.length; k++) {
			course(d, p.foot, p.floors[k] - 1, dress);
		}
		if (p.cut == 0) {
			// the observing room a round drum on the square tower's head (the dome springs from a drum, not off a box's
			// corners): the square's corners outside it open to the walk round the drum
			drum(d, p, body, dress, ft);
		}
		// the floors
		for (int k = 0; k < p.floors.length; k++) {
			Paint f = k == 0 ? d.mat(Role.PAVING).full() : d.mat(Role.FLOOR).full();
			try (var s = d.stage(StageKind.FLOORS)) {
				for (long c : p.foot) {
					int x = (int) (c >> 32);
					int z = (int) c;
					if (!p.wall(x, z)) {
						d.set(x, p.floors[k] - 1, z, f);
					}
				}
			}
		}
		for (long c : p.foot) {
			int x = (int) (c >> 32);
			int z = (int) c;
			if (!p.wall(x, z)) {
				d.carve(x, 0, z, x, shaftTop, z);
			}
		}

		// --- the newel up the middle to the observing room
		Paint post = t == Tradition.TIMBER || t == Tradition.LOG || t == Tradition.STILT ? d.mat(Role.FRAME).log(Direction.Axis.Y) : dress.full();
		Stone treads = Stone.stony(d, Role.WALL_ACCENT) ? dress : Stone.of(d, Role.FLOOR);
		// its last turn must not land before the telescope (the cell two in front of the newel)
		int start = 0;
		while (Math.floorMod(start + 2 * ft, 8) == 1) {
			start++;
		}
		Shapes.spiral(d, p.cx, p.cz, 0, ft, start, treads, post);

		// --- the door and the windows
		Side front = Side.of(x0, z0, p.cx + p.r, p.cz + p.r, Dirs.FRONT);
		Grounds.door(d, front, front.index(p.cx), 0, dress);
		windows(d, p, dress);

		// --- the top: the gallery, the observing room, the dome and its slit
		int peak = top(d, p, t, body, dress, top);

		// --- inside
		furnish(d, p, t);

		// --- the forecourt: paved, a sundial where it is deep enough, benches, a lamp
		int fz1 = z0 - (p.outer ? 2 : 1);
		forecourt(d, p, fz1);
		Groundwork.approach(d, p.cx, 0, z0 - 1);
		return peak;
	}

	/** The walls of rows y0..y1 on the outline: the body, dressed at the octagon's angles or the square's corners. */
	private static void walls(Draft d, Plan p, Stone body, Stone dress, Stone base, int y0, int y1) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (long c : p.foot) {
				int x = (int) (c >> 32);
				int z = (int) c;
				if (!p.wall(x, z)) {
					continue;
				}
				boolean angle = angle(p, x, z);
				for (int y = y0; y <= y1; y++) {
					Paint pt = y == y0 ? base.full() : angle ? dress.full() : y <= 2 ? base.full() : body.full();
					d.set(x, y, z, pt);
				}
			}
		}
		// the plinth's chamfer round the foot, clear of the door
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Shapes.around(p.foot)) {
				if (!Sacra.ok(d, c[0], y0, c[1]) || !d.isEmpty(c[0], y0, c[1]) || d.site().ground(c[0], c[1]) > y0 + 1) {
					continue;
				}
				if (Math.abs(c[0] - p.cx) <= 1 && c[1] < p.cz) {
					continue;
				}
				Direction in = toward(p.foot, c[0], c[1]);
				if (in != null && base.has(Form.STAIRS)) {
					d.set(c[0], y0, c[1], base.stairs(in, Half.BOTTOM));
					for (int y = d.site().ground(c[0], c[1]) - 1; y < y0; y++) {
						d.set(c[0], y, c[1], base.full());
					}
				}
			}
		}
	}

	/** A wall cell at a turn of the outline (the square's corners, the octagon's angles). */
	private static boolean angle(Plan p, int x, int z) {
		int out = 0;
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!p.in(x + Dirs.dx(dir), z + Dirs.dz(dir))) {
				out++;
			}
		}
		return out >= 2 || p.cut == 0 && Math.abs(x - p.cx) == p.r && Math.abs(z - p.cz) == p.r;
	}

	private static Direction toward(Set<Long> shape, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (shape.contains(Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
				return dir;
			}
		}
		return null;
	}

	/** A course of upside-down stairs round a shape at row y (only where the cell outside is free). */
	private static void course(Draft d, Set<Long> shape, int y, Stone st) {
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Shapes.around(shape)) {
				if (!d.allowed(c[0], y, c[1]) || !d.isEmpty(c[0], y, c[1])) {
					continue;
				}
				Direction in = toward(shape, c[0], c[1]);
				if (in != null) {
					d.set(c[0], y, c[1], st.stairsOrSlab(in, Half.TOP));
				}
			}
		}
	}

	/** Lights in the four axis faces of every floor: paired under an arch on the ground floor, tall lancets above. */
	private static void windows(Draft d, Plan p, Stone dress) {
		Paint glass = d.mat(Role.GLASS).pane();
		int x0 = p.cx - p.r;
		int z0 = p.cz - p.r;
		Side[] faces = Side.of(x0, z0, p.cx + p.r, p.cz + p.r);
		for (int k = 0; k < p.floors.length; k++) {
			int y = p.floors[k];
			int next = k + 1 < p.floors.length ? p.floors[k + 1] : p.wallTop + 1;
			int h = Math.min(3, next - y - 2);
			if (h < 1) {
				continue;
			}
			for (Side f : faces) {
				int mid = f.index(f.out().getAxis() == Direction.Axis.Z ? p.cx : p.cz);
				if (k == 0 && f.out() == Dirs.FRONT) {
					// the door is there: slits either side of it
					for (int i : new int[]{mid - 2, mid + 2}) {
						if (p.wall(f.x(i), f.z(i)) && !angle(p, f.x(i), f.z(i))) {
							Details.slit(d, f, i, 1, 2, dress, glass);
						}
					}
					continue;
				}
				if (k == p.floors.length - 1 && (p.outer ? f.out() == Dirs.LEFT || f.out() == Dirs.RIGHT : f.out() == Dirs.FRONT)) {
					continue;   // the doors out to the gallery or the balcony
				}
				if (k % 2 == 0 && p.r >= 4) {
					Details.lights(d, f, mid - 1, 2, y + 1, h, dress, glass);
				} else {
					Details.arch(d, f, mid, 1, y + 1, h, dress, glass);
				}
			}
		}
	}

	/**
	 * The top: the observing room's walls carried up from the last floor, the gallery round it on corbels (or, where the
	 * plot leaves no room round the tower, a balcony on the front), the doors out to it, the dome with its slit and the
	 * telescope aimed through it. Returns the top row.
	 */
	private static int top(Draft d, Plan p, Tradition t, Stone body, Stone dress, int top) {
		int ft = p.floors[p.floors.length - 1];
		int peak = p.wallTop;
		Mat rail = d.mat(Role.RAILING);
		Paint baluster = t == Tradition.TIMBER || t == Tradition.LOG || t == Tradition.STILT ? (rail.has(Form.FENCE) ? rail.fence() : Sacra.fence(d))
			: dress.wallOrFull();
		Set<Long> gallery = new HashSet<>();
		if (p.outer) {
			gallery = Shapes.oct(p.cx, p.cz, p.r + 1, p.cut == 0 ? 0 : p.cut + 1);
			gallery.removeAll(p.foot);
		} else {
			// a balcony five wide on the front, over the forecourt (or the street, high enough)
			for (int x = p.cx - 2; x <= p.cx + 2; x++) {
				for (int z = p.cz - p.r - 1; z < p.cz - p.r; z++) {
					gallery.add(Shapes.cell(x, z));
				}
			}
		}
		Set<Long> all = new HashSet<>(gallery);
		all.addAll(p.foot);
		try (var s = d.stage(StageKind.WALLS)) {
			for (long c : gallery) {
				int x = (int) (c >> 32);
				int z = (int) c;
				if (!d.allowed(x, ft, z)) {
					continue;
				}
				d.set(x, ft - 1, z, dress.full());
				boolean edge = false;
				for (Direction dir : Dirs.HORIZONTAL) {
					if (!all.contains(Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
						edge = true;
					}
				}
				if (edge) {
					// a balustrade, piers only at its angles (piers every other block read as battlements)
					boolean pier = angleOf(all, x, z);
					d.set(x, ft, z, pier ? dress.full() : baluster);
					if (angleOf(all, x, z)) {
						d.set(x, ft + 1, z, dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
					}
				}
			}
			// two courses of brackets under it
			for (long c : gallery) {
				int x = (int) (c >> 32);
				int z = (int) c;
				Direction in = toward(p.foot, x, z);
				if (in != null && d.allowed(x, ft - 2, z) && d.isEmpty(x, ft - 2, z) && z >= 0) {
					d.set(x, ft - 2, z, dress.stairsOrSlab(in, Half.TOP));
				}
			}
		}
		// doors out: to either side onto a ring gallery, to the front onto a balcony
		int rr = p.r;
		if (p.outer) {
			for (int k : new int[]{-1, 1}) {
				int x = p.cx + k * rr;
				Direction out = k < 0 ? Dirs.LEFT : Dirs.RIGHT;
				d.inStage(StageKind.OPENINGS, () -> d.door(x, ft, p.cz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), out, DoorHingeSide.LEFT));
			}
		} else {
			int fz = p.cz - rr;
			d.inStage(StageKind.OPENINGS, () -> d.door(p.cx, ft, fz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), Dirs.FRONT,
				DoorHingeSide.LEFT));
		}
		// the dome: a smooth hemisphere of one sheet, springing from the drum's last course
		Stone sheet = sheet(d);
		List<Set<Long>> rows = domeRows(p.cx, p.cz, rr);
		int domeTop = Shapes.stack(d, p.cx, p.cz, p.wallTop + 1, rows, sheet.full(), sheet);
		peak = Math.max(peak, domeTop);
		// the slit: the front of the dome opened from its springing nearly to the crown
		try (var s = d.stage(StageKind.ROOF)) {
			for (int y = p.wallTop + 1; y <= domeTop - 1; y++) {
				for (int z = p.cz - rr - 1; z < p.cz; z++) {
					if (!d.isEmpty(p.cx, y, z)) {
						d.set(p.cx, y, z, Blocks.AIR.defaultBlockState());
						break;
					}
				}
			}
		}
		if (domeTop + 1 <= top) {
			peak = Math.max(peak, Details.finial(d, p.cx, domeTop + 1, p.cz, false));
		}
		// the observing room: the telescope on its mount before the slit, a lamp hung from the dome
		Instruments.telescope(d, p.cx, ft, p.cz - 2, Dirs.FRONT);
		for (int y = ft + 4; y <= domeTop; y++) {
			if (d.isSolid(p.cx + 2, y, p.cz + 2)) {
				Lighting.chandelier(d, p.cx + 2, p.cz + 2, y, Math.max(ft + 3, y - 3));
				break;
			}
		}
		// no instruments on the balustrade: bronze rings on its angles read from the street as copper cubes on crenels (the
		// armillary stands in the instrument room below)
		if (p.outer) {
			d.poi(PoiType.VIEWPOINT, p.cx + 1, ft, p.cz - rr - 1, Dirs.FRONT);
			d.poi(PoiType.VIEWPOINT, p.cx - rr - 1, ft, p.cz + 1, Dirs.LEFT);
			d.poi(PoiType.VIEWPOINT, p.cx + rr + 1, ft, p.cz - 1, Dirs.RIGHT);
		} else {
			d.poi(PoiType.VIEWPOINT, p.cx - 1, ft, p.cz - rr - 1, Dirs.FRONT);
			d.poi(PoiType.VIEWPOINT, p.cx + 1, ft, p.cz - rr - 1, Dirs.FRONT);
		}
		return peak;
	}

	/**
	 * The rows of a dome over half-width r, laid to a hemisphere's profile ({@link Shapes#domeRows}: as tall as half its
	 * width, not a heap), springing in the drum's round outline. Every step is a stair (the stack's smoothing), so the
	 * dome reads as one smooth shell.
	 */
	static List<Set<Long>> domeRows(int cx, int cz, int r) {
		return Shapes.domeRows(cx, cz, r);
	}

	/** The round outline of the observing room's drum (the octagon the round towers stand on). */
	static Set<Long> drumOutline(Plan p) {
		return Shapes.oct(p.cx, p.cz, p.r, p.r >= 5 ? 3 : 2);
	}

	/**
	 * The observing room's walls on a square tower redrawn as a round drum from its floor {@code ft} to the walls' top: the
	 * square's corner cells outside the drum cleared to the deck, a balustrade on the square's edge where no gallery
	 * runs round it.
	 */
	private static void drum(Draft d, Plan p, Stone body, Stone dress, int ft) {
		Set<Long> drum = drumOutline(p);
		Mat rail = d.mat(Role.RAILING);
		// the drum in the light render where the style has one, so the lead or copper of the dome reads off it
		Stone render = Stone.stony(d, Role.WALL) ? Stone.of(d, Role.WALL).crisp() : body;
		try (var s = d.stage(StageKind.WALLS)) {
			for (long c : p.foot) {
				int x = (int) (c >> 32);
				int z = (int) c;
				boolean inDrum = drum.contains(c);
				boolean drumWall = inDrum && onRim(drum, x, z);
				for (int y = ft; y <= p.wallTop; y++) {
					if (!inDrum) {
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
					} else if (drumWall) {
						d.set(x, y, z, angleOf(drum, x, z) ? dress.full() : render.full());
					}
				}
				// the corners' deck: the walls' last course under them is solid; a rail on their outer edge
				if (!inDrum && !p.outer && p.wall(x, z)) {
					d.set(x, ft, z, rail.has(Form.FENCE) ? rail.fence() : dress.wallOrFull());
				}
			}
		}
	}

	private static boolean onRim(Set<Long> s, int x, int z) {
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!s.contains(Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * What the dome is sheeted in, one material laid crisp: lead in the cool, slated north and in Bohemia (where teal
	 * copper on every dome would turn a skyline turquoise), patinated copper in the mountains, on the coasts and in the
	 * tropics.
	 */
	static Stone sheet(Draft d) {
		Culture c = d.style().culture();
		boolean copper = c == Culture.ALPINE || c == Culture.MARITIME || c == Culture.BAYOU || c == Culture.TROPICAL;
		return copper ? Stone.copper(d).crisp() : Stone.fixed(d, StageKind.ROOF, "minecraft:polished_andesite");
	}

	/** A cell of a shape at a turn of its outline. */
	private static boolean angleOf(Set<Long> shape, int x, int z) {
		int out = 0;
		for (Direction dir : Dirs.HORIZONTAL) {
			if (!shape.contains(Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir)))) {
				out++;
			}
		}
		return out >= 2;
	}

	/**
	 * The rooms: the library on the ground floor (shelves round the walls, the astronomers' lecterns, a table of charts),
	 * the instrument rooms over it (an armillary on its stand, chests of lenses, a cartographer's table, a desk), and the
	 * observing room's lectern and chair. Everything stands against the walls; the walk round the newel stays clear.
	 */
	private static void furnish(Draft d, Plan p, Tradition t) {
		for (int k = 0; k < p.floors.length; k++) {
			int y = p.floors[k];
			boolean last = k == p.floors.length - 1;
			int next = k + 1 < p.floors.length ? p.floors[k + 1] : p.wallTop + 1;
			// the places that matter first, against the back wall: the astronomers' lecterns, a chest beside
			if (k == 0 || last) {
				Studies.lectern(d, p.cx - 1, y, p.cz + p.r - 1, Dirs.FRONT, JobType.SCHOLAR, k == 0 ? "Tables of the Stars" : "Observations");
			}
			if (k == 0) {
				Studies.lectern(d, p.cx + 1, y, p.cz + p.r - 1, Dirs.FRONT, JobType.SCHOLAR, "Of the Heavens");
				Furniture.chest(d, p.cx + p.r - 1, y, p.cz - 1, Dirs.LEFT, List.of(new Object[]{"minecraft:paper", 16},
					new Object[]{"minecraft:compass", 1}, new Object[]{"minecraft:clock", 1}));
			}
			List<int[]> spots = wallSpots(d, p, y);
			int n = 0;
			for (int[] c : spots) {
				int x = c[0];
				int z = c[1];
				Direction in = Dirs.nearest(p.cx - x, p.cz - z);
				int role = k == 0 ? libraryPiece(n) : last ? roomPiece(n) : instrumentPiece(n);
				// in a corner the piece faces along a wall into the next spot: shelves only there
				if (p.dist(x + Dirs.dx(in), z + Dirs.dz(in)) > 2 && role != 0 && role != 4 && role != 7) {
					role = 0;
				}
				switch (role) {
					case 0 -> Studies.books(d, x, y, z, Math.max(1, Math.min(3, next - y - 2)), in);
					case 1 -> Studies.lectern(d, x, y, z, in, JobType.SCHOLAR, "Tables of the Stars");
					case 2 -> d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, Blocks.CARTOGRAPHY_TABLE.defaultBlockState()));
					case 3 -> Furniture.chest(d, x, y, z, in, List.of(new Object[]{"minecraft:glass_pane", 8}, new Object[]{"minecraft:paper", 16},
						new Object[]{"minecraft:compass", 1}));
					case 4 -> Instruments.armillary(d, x, y, z);
					case 5 -> Studies.desk(d, x, y, z, in, false);
					case 6 -> Furniture.chair(d, x, y, z, in.getOpposite());
					case 7 -> Instruments.globe(d, x, y, z);
					default -> {
					}
				}
				n++;
			}
			if (!last && next - y >= 4) {
				Lighting.chandelier(d, p.cx - 2, p.cz - 2, next - 1, next - 2);
				Lighting.chandelier(d, p.cx + 2, p.cz + 2, next - 1, next - 2);
			}
		}
	}

	private static int libraryPiece(int n) {
		return new int[]{0, 1, 0, 2, 0, 3, 0, 1, 0, 7, 0, 0}[n % 12];
	}

	private static int instrumentPiece(int n) {
		return new int[]{4, 2, 3, 5, 0, 1, 7, 0, 3, 2, 0, 5}[n % 12];
	}

	private static int roomPiece(int n) {
		return new int[]{1, 6, 0, 2, 0, 3}[n % 6];
	}

	/**
	 * The cells against the walls inside half-width rr at standing level y where furniture may stand: not in the newel's
	 * square, not the walk round it, not inside a door or under a window, not where something already stands.
	 */
	private static List<int[]> wallSpots(Draft d, Plan p, int y) {
		List<int[]> out = new ArrayList<>();
		Set<Long> room = p.foot;
		for (long c : room) {
			int x = (int) (c >> 32);
			int z = (int) c;
			boolean wall = false;
			boolean nextToWall = false;
			for (Direction dir : Dirs.HORIZONTAL) {
				long n = Shapes.cell(x + Dirs.dx(dir), z + Dirs.dz(dir));
				if (!room.contains(n)) {
					wall = true;
				}
			}
			if (wall) {
				continue;
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				boolean nw = false;
				for (Direction d2 : Dirs.HORIZONTAL) {
					if (!room.contains(Shapes.cell(nx + Dirs.dx(d2), nz + Dirs.dz(d2)))) {
						nw = true;
					}
				}
				if (room.contains(Shapes.cell(nx, nz)) && nw) {
					nextToWall = true;
				}
			}
			if (!nextToWall || p.dist(x, z) <= 2) {
				continue;
			}
			// keep the cells inside doors and the axis cells (doors, telescope) free
			if (x == p.cx || z == p.cz) {
				continue;
			}
			if (!d.isEmpty(x, y, z) || !d.isSolid(x, y - 1, z)) {
				continue;
			}
			out.add(new int[]{x, z});
		}
		out.sort((a, b) -> Double.compare(Math.atan2(a[1] - p.cz, a[0] - p.cx), Math.atan2(b[1] - p.cz, b[0] - p.cx)));
		return out;
	}

	/** The forecourt: paved to the street, a sundial on a step where it is deep enough, benches facing it, a lamp. */
	private static void forecourt(Draft d, Plan p, int z1) {
		if (z1 < 0) {
			return;
		}
		int w = d.frontage();
		dev.voidmc.hearth.build.kit.civic.Grounds.forecourt(d, 0, w - 1, z1, true);
		if (z1 >= 2) {
			int sx = p.cx - 3;
			int sz = Math.max(1, z1 - 1);
			if (sx - 1 >= 0 && flat(d, sx - 1, sz - 1, sx + 1, sz + 1)) {
				Instruments.sundial(d, sx, 0, sz);
			}
			int bx = p.cx + 3;
			int bz = Math.max(0, z1 - 1);
			if (bx + 1 < w && flat(d, bx, bz, bx + 1, bz)) {
				Benches.bench(d, bx, 0, bz, Dirs.LEFT, Dirs.RIGHT, 2, true);
			}
		}
		int lx = Math.min(w - 1, p.cx + 2);
		if (flat(d, lx, 0, lx, 0) && d.isEmpty(lx, 0, 0)) {
			Lighting.post(d, lx, 0, 0);
		}
	}

	/** Whether the ground as drawn so far stands at the floor's level all over a rectangle (and nothing is built on it). */
	private static boolean flat(Draft d, int x0, int z0, int x1, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (!Sacra.ok(d, x, 0, z) || Groundwork.surface(d, x, z) != 0 || d.site().isWater(x, z) || !d.isEmpty(x, 0, z)) {
					return false;
				}
			}
		}
		return true;
	}

	// ================================================================================================ the terrace

	/**
	 * The terrace observatory: a platform of two stepped storeys (the library below, the instrument store above), its
	 * roof a walled terrace of instruments; in the dry lands the gnomon of a great sundial climbs the front as the stair up
	 * to it, a domed kiosk at its head; in the karst a stair runs up the side to a pavilion under a curved roof. Returns
	 * the top row.
	 */
	private static int terraceObservatory(Draft d, Tradition t) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		boolean karst = t == Tradition.KARST;
		// a monument on its own levelled ground (a bank embanked)
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// the platform: as broad as the plot allows less a walk round, set to the back; the upper storey a block in
		int x0 = 1;
		int x1 = w - 2;
		int z1 = dp - 1;
		int z0 = Math.max(3, dp - (x1 - x0 + 1));
		int h0 = 5;
		int h1 = 4;
		int deck = h0 + h1;          // the terrace's standing level
		if (deck + 6 > top) {
			h1 = Math.max(3, top - 6 - h0);
			deck = h0 + h1;
		}
		Stone body = Stone.panel(d).crisp();
		Stone dress = Stone.dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		d.body(x0, z0, x1, z1);
		d.eave(deck - 1);
		Grounds.footings(d, x0, z0, x1, z1, -1);
		int ux0 = x0 + 1;
		int ux1 = x1 - 1;
		int uz0 = z0 + 1;
		int uz1 = z1;
		// the lower storey: battered walls of the base stone below, the body over, a course at its head; inside the library
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(x0, z0, x1, z1)) {
				boolean corner = (c[0] == x0 || c[0] == x1) && (c[1] == z0 || c[1] == z1);
				for (int y = 0; y < h0; y++) {
					d.set(c[0], y, c[1], y == 0 ? base.full() : corner ? dress.full() : body.full());
				}
			}
			// the step to the upper storey: a ledge a block wide round it, coped
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean inner = x >= ux0 && x <= ux1 && z >= uz0 && z <= uz1;
					if (!inner) {
						d.set(x, h0 - 1, z, dress.full());
					}
				}
			}
			for (int[] c : Draft.ring(ux0, uz0, ux1, uz1)) {
				boolean corner = (c[0] == ux0 || c[0] == ux1) && (c[1] == uz0 || c[1] == uz1);
				for (int y = h0; y < deck; y++) {
					d.set(c[0], y, c[1], corner ? dress.full() : body.full());
				}
			}
		}
		// floors and rooms
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				for (int x = x0 + 1; x <= x1 - 1; x++) {
					d.set(x, -1, z, d.mat(Role.PAVING).full());
					d.set(x, h0 - 1, z, d.mat(Role.FLOOR).full());
				}
			}
			for (int z = uz0; z <= uz1; z++) {
				for (int x = ux0; x <= ux1; x++) {
					d.set(x, deck - 1, z, dress.full());
				}
			}
		}
		d.carve(x0 + 1, 0, z0 + 1, x1 - 1, h0 - 2, z1 - 1);
		d.carve(ux0 + 1, h0, uz0 + 1, ux1 - 1, deck - 2, uz1 - 1);
		// the parapet round the terrace: crenels in the dry lands, a balustrade in the karst
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : Draft.ring(ux0, uz0, ux1, uz1)) {
				boolean corner = (c[0] == ux0 || c[0] == ux1) && (c[1] == uz0 || c[1] == uz1);
				if (karst) {
					d.set(c[0], deck, c[1], corner ? dress.full() : dress.wallOrFull());
				} else {
					d.set(c[0], deck, c[1], body.full());
					if (((c[0] + c[1]) & 1) == 0 || corner) {
						d.set(c[0], deck + 1, c[1], dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
					}
				}
			}
		}
		// the way in: a door in the front of the lower storey, the stair up to the upper storey inside
		int cx = (x0 + x1) / 2;
		int zm = (z0 + z1) / 2;
		Side front = Side.of(x0, z0, x1, z1, Dirs.FRONT);
		Grounds.door(d, front, front.index(cx), 0, dress);
		Groundwork.approach(d, cx, 0, z0 - 1);
		int sx = x0 + 2;
		int sz = z0 + 1;
		dev.voidmc.hearth.build.kit.Stairs.straight(d, sx, 0, sz, Dirs.BACK, h0, 1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = Math.max(0, h0 - 3); i < h0; i++) {
				d.set(sx, h0 - 1, sz + i, Blocks.AIR.defaultBlockState());
			}
		}
		// windows: arched lights down the sides of the lower storey, slits above
		Paint glass = d.mat(Role.GLASS).pane();
		for (Side f : Side.of(x0, z0, x1, z1)) {
			if (f.out() == Dirs.BACK) {
				continue;
			}
			for (int i = 2; i <= f.last() - 2; i += 3) {
				if (f.out() == Dirs.FRONT && Math.abs(f.x(i) - cx) <= 1) {
					continue;
				}
				Details.arch(d, f, i, 1, 1, 2, dress, glass);
			}
		}
		for (Side f : Side.of(ux0, uz0, ux1, uz1)) {
			if (f.out() == Dirs.BACK) {
				continue;
			}
			for (int i = 2; i <= f.last() - 2; i += 3) {
				Details.slit(d, f, i, h0 + 1, 2, dress, glass);
			}
		}
		// the stair to the terrace
		int peak = deck + 1;
		if (karst) {
			peak = Math.max(peak, karstTerrace(d, ux0, uz0, ux1, uz1, deck, top, dress));
		} else {
			peak = Math.max(peak, yantra(d, x0, z0, x1, ux0, uz0, ux1, uz1, h0, deck, top, body, dress));
		}
		// inside: the library and the instrument store
		terraceRooms(d, x0, z0, x1, z1, ux0, uz0, ux1, uz1, h0, deck, sx, sz);
		// the instruments on the terrace
		Instruments.armillary(d, ux0 + 1, deck, uz1 - 1);
		Instruments.armillary(d, ux1 - 1, deck, uz1 - 1);
		Instruments.globe(d, (ux0 + ux1) / 2, deck, uz1 - 1);
		Instruments.quadrant(d, ux1 - 1, deck, uz0 + 2, Dirs.LEFT);
		d.poi(PoiType.VIEWPOINT, (ux0 + ux1) / 2, deck, uz0 + 1, Dirs.FRONT);
		d.poi(PoiType.VIEWPOINT, ux0 + 1, deck, (uz0 + uz1) / 2, Dirs.LEFT);
		Lighting.room(d, ux0 + 1, uz0 + 1, ux1 - 1, uz1 - 1, deck);
		// the forecourt
		dev.voidmc.hearth.build.kit.civic.Grounds.forecourt(d, 0, w - 1, z0 - 1, true);
		return Math.max(peak, deck + 2);
	}

	/**
	 * The dry lands' great sundial (Samrat Yantra): its gnomon a right-angled wall along the platform's left side whose
	 * hypotenuse is a stair climbing from the forecourt toward the back to the terrace, a domed kiosk at its head on the
	 * terrace. Returns the top row.
	 */
	private static int yantra(Draft d, int x0, int z0, int x1, int ux0, int uz0, int ux1, int uz1, int h0, int deck, int top, Stone body, Stone dress) {
		Mat paving = d.mat(Role.PAVING);
		int sx = x0 - 1;
		int zTop = uz1 - 2;
		int zFoot = zTop - deck + 1;
		if (sx < 0 || zFoot < 0) {
			return deck;
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int i = 0; i < deck; i++) {
				int z = zFoot + i;
				int y = i;
				int g = d.site().ground(sx, z);
				for (int yy = Math.min(g, 0) - 1; yy < y; yy++) {
					d.set(sx, yy, z, yy < 0 ? d.mat(Role.FOUNDATION).full() : yy == y - 1 ? dress.full() : body.full());
				}
				d.set(sx, y, z, paving.has(Form.STAIRS) ? paving.stairs(Dirs.BACK, Half.BOTTOM) : dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				d.set(sx, y + 1, z, Blocks.AIR.defaultBlockState());
				d.set(sx, y + 2, z, Blocks.AIR.defaultBlockState());
			}
			// the head: a landing beyond the top step, a bridge over the ledge, the parapet opened to the terrace
			int zl = zTop + 1;
			for (int yy = Math.min(d.site().ground(sx, zl), 0) - 1; yy < deck - 1; yy++) {
				d.set(sx, yy, zl, yy < 0 ? d.mat(Role.FOUNDATION).full() : body.full());
			}
			d.set(sx, deck - 1, zl, dress.full());
			d.set(x0, deck - 1, zl, dress.full());
			for (int y = deck; y <= deck + 1; y++) {
				d.set(sx, y, zl, Blocks.AIR.defaultBlockState());
				d.set(x0, y, zl, Blocks.AIR.defaultBlockState());
				d.set(ux0, y, zl, Blocks.AIR.defaultBlockState());
			}
		}
		// the kiosk: a little dome on four piers on the terrace beside the stair's head
		int kx0 = ux0 + 1;
		int kz0 = uz1 - 4;
		int ky = deck;
		try (var s = d.stage(StageKind.WALLS)) {
			for (int[] c : new int[][]{{kx0, kz0}, {kx0 + 2, kz0}, {kx0, kz0 + 2}, {kx0 + 2, kz0 + 2}}) {
				for (int y = ky; y < ky + 3; y++) {
					d.set(c[0], y, c[1], dress.full());
				}
			}
			for (int z = kz0; z <= kz0 + 2; z++) {
				for (int x = kx0; x <= kx0 + 2; x++) {
					d.set(x, ky + 3, z, body.full());
				}
			}
		}
		List<Set<Long>> rows = new ArrayList<>();
		rows.add(Shapes.oct(kx0 + 1, kz0 + 1, 1, 1));
		rows.add(Shapes.oct(kx0 + 1, kz0 + 1, 0, 0));
		int domeTop = Shapes.stack(d, kx0 + 1, kz0 + 1, ky + 4, rows, body.full(), body);
		Sacra.hangingLantern(d, kx0 + 1, ky + 2, kz0 + 1, StageKind.EXTERIOR);
		int peak = domeTop;
		if (domeTop + 1 <= top) {
			peak = Details.finial(d, kx0 + 1, domeTop + 1, kz0 + 1, false);
		}
		return peak;
	}

	/**
	 * The karst's terrace: a straight stair of the dressing up the platform's left side to the terrace, and a pavilion
	 * under a curved hip at the back of it. Returns the top row.
	 */
	private static int karstTerrace(Draft d, int ux0, int uz0, int ux1, int uz1, int deck, int top, Stone dress) {
		// the pavilion: four red posts and a curved hip over the back middle of the terrace
		int px0 = (ux0 + ux1) / 2 - 2;
		int px1 = px0 + 4;
		int pz0 = Math.max(uz0 + 2, uz1 - 5);
		int pz1 = uz1 - 1;
		Mat frame = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : new int[][]{{px0 + 1, pz0}, {px1 - 1, pz0}, {px0 + 1, pz1 - 1}, {px1 - 1, pz1 - 1}}) {
				for (int y = deck; y < deck + 3; y++) {
					d.set(c[0], y, c[1], frame.log(Direction.Axis.Y));
				}
			}
		}
		Roofs.Opts o = Roofs.Opts.of(d).withEaves(1, 1).withPitch(1.0);
		Roofs.Built b = Roofs.build(d, px0 + 1, pz0, px1 - 1, pz1 - 1, deck + 2, Habits.RoofForm.PAGODA, Direction.Axis.X, o, false, false);
		Sacra.hangingLantern(d, (px0 + px1) / 2, Math.min(b.roof().bottomY((px0 + px1) / 2, (pz0 + pz1) / 2) - 1, deck + 3), (pz0 + pz1) / 2,
			StageKind.EXTERIOR);
		return b.roof().ridgeY;
	}

	/** The rooms inside the platform: shelves and lecterns in the library, chests and instruments in the store above. */
	private static void terraceRooms(Draft d, int x0, int z0, int x1, int z1, int ux0, int uz0, int ux1, int uz1, int h0, int deck, int sx, int sz) {
		int y = 0;
		int cx = (x0 + x1) / 2;
		int zm = (z0 + z1) / 2;
		// the library: shelves down the right side, lecterns at the back, a table of charts in the middle
		for (int z = z0 + 2; z <= z1 - 2; z++) {
			Studies.books(d, x1 - 1, y, z, Math.min(3, h0 - 2), Dirs.LEFT);
		}
		Studies.lectern(d, cx, y, z1 - 1, Dirs.FRONT, JobType.SCHOLAR, "Tables of the Stars");
		Studies.lectern(d, cx + 2 <= x1 - 2 ? cx + 2 : cx - 1, y, z1 - 1, Dirs.FRONT, JobType.SCHOLAR, "Of the Heavens");
		d.inStage(StageKind.INTERIOR, () -> d.set(cx + 1, y, zm, Blocks.CARTOGRAPHY_TABLE.defaultBlockState()));
		Furniture.chair(d, cx + 1, y, zm - 1, Dirs.FRONT);
		Furniture.chest(d, x1 - 1, y, z0 + 1, Dirs.LEFT, List.of(new Object[]{"minecraft:paper", 16}, new Object[]{"minecraft:compass", 2}));
		Lighting.room(d, x0 + 1, z0 + 1, x1 - 1, z1 - 1, y);
		// the store above: chests of lenses and instruments, the stair on up to the terrace
		int y1 = h0;
		int hx = ux1 - 1;
		int hz = uz0 + 1;
		int run = deck - h0;
		dev.voidmc.hearth.build.kit.Stairs.straight(d, hx, y1, hz, Dirs.BACK, run, 1);
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = Math.max(0, run - 3); i < run; i++) {
				d.set(hx, deck - 1, hz + i, Blocks.AIR.defaultBlockState());
			}
		}
		Furniture.chest(d, ux0 + 2, y1, uz1 - 1, Dirs.FRONT, List.of(new Object[]{"minecraft:glass_pane", 12}, new Object[]{"minecraft:copper_ingot", 6}));
		Instruments.armillary(d, ux0 + 3, y1, uz1 - 1);
		if (ux0 + 4 < hx) {
			Instruments.globe(d, ux0 + 4, y1, uz1 - 1);
		}
		Lighting.room(d, ux0 + 1, uz0 + 1, ux1 - 1, uz1 - 1, y1);
	}
}
