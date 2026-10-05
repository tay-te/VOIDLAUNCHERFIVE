package dev.voidmc.hearth.build.archetype.work;

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
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Body;
import dev.voidmc.hearth.build.kit.Facade;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.Walls;
import dev.voidmc.hearth.build.kit.civic.Towers;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.kit.learning.Sacra;
import dev.voidmc.hearth.build.kit.learning.Studies;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.WallKind;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:work/alchemist_tower}: a narrow tower of laboratories by the library. Seven square in the culture's
 * best stone (whatever its houses are made of), on a chamfered plinth with toothed quoins and a string course at every
 * floor, a newel stair winding up one back corner the whole height. From the bottom:
 * <ul>
 *   <li>the <b>workshop</b>: the athanor (a blast furnace and a furnace) against the side wall under its flue, coal and
 *   ores in barrels and chests, the grindstone, a crucible of water; slits for light;</li>
 *   <li>the <b>laboratory</b>: benches along the walls with brewing stands and their bottles, candles and jars, shelves
 *   of bottles over them, a cauldron, the alchemist's lectern with the book open (his job site) by an arched window;</li>
 *   <li>the <b>study</b> under the roof: his bed, a desk under the window, chiseled shelves of books, a chest and a
 *   reading lectern (in the mountains and the north this top storey is framed in boards or logs flush on the stone);</li>
 *   <li>the <b>roof platform</b> behind a corbelled parapet (battlements in stone, a railing in the timber traditions):
 *   herbs drying in pots, a still on a barrel, a bench where one can watch the town; the stair comes up into a little
 *   stair house in the back corner capped in the culture's manner (a needle spire, an onion, a cone, a dome, a curved
 *   hip), and the flue rises past the parapet with its smoke (out from the wall on a breast where the plot allows).</li>
 * </ul>
 * POIs: the SCHOLAR's JOB_SITE and a LECTERN at the laboratory lectern, a second LECTERN in the study, the BED, STORAGE
 * at the chests, a SEAT and TABLE in the study, a VIEWPOINT on the roof, DOOR.
 *
 * <p>Params: {@code stair_right} (boolean: the stair in the back right corner; default by seed), {@code variant} (int).
 */
public final class AlchemistTower implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("work/alchemist_tower"), ArchetypeCategory.WORKPLACE, Set.of(JobType.SCHOLAR), 1,
		7, 9, 7, 9, 14, 22, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	/** The ring cells a newel stair's treads stand on (landmark.Shapes.SPIRAL), as offsets from the post. */
	private static final int[][] RING = Shapes.SPIRAL;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** The plan, in local coordinates, mirrored when the stair is on the right. */
	private static final class Plan {
		int x0;
		int z0;
		int x1;
		int z1;
		boolean right;
		int[] floors;     // standing level of the workshop, laboratory, study and roof
		int start;        // the spiral's start index
		boolean house;    // a stair house on the roof
		int[] top;        // the top storey's walls: x0, z0, x1, z1

		/** A canonical column a (0..4 across the interior, the stair on side a0..a2) to local x. */
		int ax(int a) {
			return this.right ? this.x1 - 1 - a : this.x0 + 1 + a;
		}

		/** A canonical row b (0 front .. 4 back) to local z. */
		int bz(int b) {
			return this.z0 + 1 + b;
		}

		/** The canonical "toward a4" direction (away from the stair across the tower). */
		Direction across() {
			return this.right ? Dirs.LEFT : Dirs.RIGHT;
		}

		int postX() {
			return ax(1);
		}

		int postZ() {
			return bz(3);
		}
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		int top = d.maxHeight() - 1;
		Tradition t = Tradition.of(d);
		Plan p = new Plan();
		p.x0 = (w - 7) / 2;
		p.z0 = dp - 7;
		p.x1 = p.x0 + 6;
		p.z1 = p.z0 + 6;
		p.right = d.param("stair_right", d.chance(0.5));
		plan(d, p, top);
		int cx = p.x0 + 3;
		int fd = p.floors[3];
		d.body(p.x0, p.z0, p.x1, p.z1);
		d.eave(fd);

		// the shaft in the walls' render or masonry, laid clean above the workshop (its main material: a big face mixed in
		// patches reads as blotches), weathered in the base stone below
		Stone body = t == Tradition.STONE || t == Tradition.LOG || t == Tradition.STILT ? Stone.panel(d) : Stone.panel(d).crisp();
		Stone dress = dressing(d);
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Tower tw = new Tower(p.x0, p.z0, 7, body, dress, base, false);
		Side front = tw.face(Dirs.FRONT);

		// --- ground and walls: footings, a chamfered plinth, the workshop storey in the base stone, the shaft above with
		// dressed corners, one string course over the workshop, a corbel table under the parapet
		Grounds.footings(d, p.x0, p.z0, p.x1, p.z1, -1);
		tw.plinth(d, 0, 0, true, front.outX(3, 1), front.outZ(3, 1));
		// (the mountains' boarded and the north's log top; the timber towns build the whole shaft in stone, as their
		// alchemists' towers were, a framed top jettied over it read top-heavy)
		boolean timberTop = t == Tradition.ALPINE || t == Tradition.LOG;
		int f1 = p.floors[1];
		int f2 = p.floors[2];
		int stoneTop = timberTop ? f2 - 1 : fd - 1;
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side f : tw.faces()) {
				for (int i = 0; i < f.length(); i++) {
					boolean corner = f.isCorner(i);
					for (int y = 1; y <= stoneTop; y++) {
						Paint pt;
						if (corner || y == f1 - 1) {
							pt = dress.full();
						} else if (y < f1 - 1) {
							pt = base.full();
						} else {
							pt = body.full();
						}
						d.set(f.x(i), y, f.z(i), pt);
					}
				}
			}
		}
		tw.course(d, f1 - 1, false);
		// the top: framed in timber (boards in the mountains, logs in the north), or the stone carried up
		int[] tr = {p.x0, p.z0, p.x1, p.z1};
		if (timberTop) {
			// framed flush on the stone: a top jettied out on three sides made the tower top-heavy
			timberStorey(d, p, t, tr, f2, fd - 1);
		} else {
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] c : Draft.ring(p.x0, p.z0, p.x1, p.z1)) {
					d.set(c[0], fd - 1, c[1], dress.full());
				}
			}
			tw.band(d, fd - 2);
		}
		p.top = tr;
		for (int k = 0; k < 4; k++) {
			Paint fl = k == 0 ? d.mat(Role.PAVING).full() : k == 3 ? dress.full() : d.mat(Role.FLOOR).full();
			try (var s = d.stage(StageKind.FLOORS)) {
				int[] r = k >= 2 ? tr : new int[]{p.x0, p.z0, p.x1, p.z1};
				for (int z = r[1] + 1; z <= r[3] - 1; z++) {
					for (int x = r[0] + 1; x <= r[2] - 1; x++) {
						d.set(x, p.floors[k] - 1, z, fl);
					}
				}
			}
		}
		d.carve(p.x0 + 1, 0, p.z0 + 1, p.x1 - 1, f2 - 2, p.z1 - 1);
		d.carve(tr[0] + 1, f2, tr[1] + 1, tr[2] - 1, fd - 2, tr[3] - 1);

		// --- the stair: a newel winding up the back corner from the workshop to the roof
		Paint post = t == Tradition.TIMBER || t == Tradition.LOG || t == Tradition.STILT ? d.mat(Role.FRAME).log(Direction.Axis.Y) : dress.full();
		Stone treads = Stone.stony(d, Role.WALL_ACCENT) ? dress : Stone.of(d, Role.FLOOR);
		Shapes.spiral(d, p.postX(), p.postZ(), 0, fd, p.start, treads, post);

		// --- openings: the door with its portal and lamp, slits on the stair and in the workshop, arched lights above
		Grounds.door(d, front, 3, 0, dress);
		windows(d, p, tw, timberTop);

		// --- the roof: the parapet, the stair house and its cap, the flue
		int peak = roof(d, p, t, tw, top);

		// --- inside, floor by floor
		workshop(d, p);
		laboratory(d, p);
		study(d, p);
		platform(d, p);

		// --- the way in
		int ez = p.z0 - 1;
		if (ez >= 0) {
			Groundwork.approach(d, cx, 0, ez);
			// lamps either side of the steps on a deep plot
			if (ez >= 1) {
				Lighting.post(d, Math.max(0, cx - 2), Groundwork.surface(d, Math.max(0, cx - 2), 0), 0);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(top, peak + 2));
	}

	// ------------------------------------------------------------------------------------------------ the plan

	/** The storeys (each a whole storey for the newel's turn to land on the room side) and the spiral's start. */
	private static void plan(Draft d, Plan p, int top) {
		// tallest first: a roof with a stair house wants six rows over the deck (walls, cap), a bare one two
		int[][] tries = {{5, 5, 5}, {5, 5, 4}, {5, 4, 4}, {4, 5, 4}, {4, 4, 5}, {4, 4, 4}, {4, 4, 3}};
		for (boolean house : new boolean[]{true, false}) {
			for (int[] h : tries) {
				int f1 = h[0];
				int f2 = f1 + h[1];
				int fd = f2 + h[2];
				if (fd + (house ? 6 : 1) > top) {
					continue;
				}
				int[] fl = {0, f1, f2, fd};
				for (int s = 0; s < 8; s++) {
					if (stairWorks(p, fl, s)) {
						p.floors = fl;
						p.start = s;
						p.house = house;
						return;
					}
				}
			}
		}
		// fallback (never for the heights announced): the lowest storeys, a ladder-like start
		p.floors = new int[]{0, 4, 8, 12};
		p.start = 0;
		p.house = false;
	}

	/** Whether every landing of a newel starting at ring index s comes out on the room side of the stair. */
	private static boolean stairWorks(Plan p, int[] floors, int s) {
		if (!roomSide(p, Math.floorMod(s + 1, 8))) {
			return false;
		}
		for (int k = 1; k < floors.length; k++) {
			if (!roomSide(p, Math.floorMod(s + 2 * floors[k], 8))) {
				return false;
			}
		}
		return true;
	}

	/** The ring offset of index idx across the tower (canonical: +1 toward the room), mirrored with the stair. */
	private static int ringA(Plan p, int idx) {
		return p.right ? -RING[idx][0] : RING[idx][0];
	}

	/** A ring cell that has a room cell beside it (its front row or its side across the tower). */
	private static boolean roomSide(Plan p, int idx) {
		return ringA(p, idx) == 1 || RING[idx][1] == -1;
	}

	/** The room cell outside the landing of floor k: next to the tread that is flush with that floor. */
	private static int[] landing(Plan p, int k) {
		int idx = Math.floorMod(p.start + 2 * p.floors[k], 8);
		return outside(p, idx);
	}

	/** The room cell beside ring cell idx (canonical), as local {x, z}. */
	private static int[] outside(Plan p, int idx) {
		int a = 1 + ringA(p, idx);
		int b = 3 + RING[idx][1];
		if (RING[idx][1] == -1) {
			b -= 1;      // the front row of the ring steps out forward
		} else {
			a += 1;      // the side column steps out across
		}
		return new int[]{p.ax(a), p.bz(b)};
	}

	/**
	 * The framed top storey over the rectangle {@code r} from row {@code ya} (its sill) to {@code yg} (its head, the deck's
	 * row): corner posts, a post either side of the middle panel, sill and head beams, render between (boards in the
	 * mountains, laid logs in the north), a window in the middle panel of the open faces; where it is jettied over the
	 * stone, joist ends and corbels carry it.
	 */
	private static void timberStorey(Draft d, Plan p, Tradition t, int[] r, int ya, int yg) {
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat wall = d.mat(Role.WALL);
		Mat boards = d.mat(Role.GABLE);
		Paint glass = d.mat(Role.GLASS).pane();
		Stone dress = dressing(d);
		boolean logs = t == Tradition.LOG;
		boolean planks = t == Tradition.ALPINE;
		Direction stairSide = p.right ? Dirs.RIGHT : Dirs.LEFT;
		try (var s = d.stage(StageKind.WALLS)) {
			for (Side f : Side.of(r[0], r[1], r[2], r[3])) {
				int n = f.length();
				int pa = 2;
				int pb = n - 3;
				boolean open = f.out() != Dirs.BACK && f.out() != stairSide;
				for (int i = 0; i < n; i++) {
					boolean corner = f.isCorner(i);
					boolean post = i == pa || i == pb;
					for (int y = ya; y <= yg; y++) {
						Paint pt;
						if (corner) {
							pt = logs ? frame.log((y & 1) == 0 ? f.axis() : (f.axis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X))
								: frame.log(Direction.Axis.Y);
						} else if (y == ya || y == yg) {
							pt = logs ? wall.log(f.axis()) : beam.log(f.axis());
						} else if (logs) {
							pt = post ? wall.strippedLog(Direction.Axis.Y) : wall.log(f.axis());
						} else if (post) {
							pt = frame.log(Direction.Axis.Y);
						} else {
							pt = planks ? boards.full() : wall.full();
						}
						d.set(f.x(i), y, f.z(i), pt);
					}
					// the middle panel's window on the open faces, a small one toward the stair
					boolean mid = i > pa && i < pb;
					if (mid && yg - ya >= 3) {
						int wy0 = ya + 1;
						int wy1 = Math.min(yg - 1, ya + 2);
						if (open || i == f.middle()) {
							for (int y = wy0; y <= (open ? wy1 : wy0); y++) {
								d.set(f.x(i), y, f.z(i), glass);
							}
						}
					}
				}
				// window sills of the open faces
				if (open && yg - ya >= 3) {
					for (int i = pa + 1; i < pb; i++) {
						int ox = f.outX(i, 1);
						int oz = f.outZ(i, 1);
						if (Walls.inPlot(d, ox, oz) || oz == -1 && ya >= 3) {
							d.set(ox, ya, oz, d.mat(Role.WINDOW).stairsOrSlab(f.out().getOpposite(), Half.TOP));
						}
					}
				}
			}
			// the jetty: where the frame stands out past the stone, joist ends under its sill and corbels under them
			for (Side f : Side.of(r[0], r[1], r[2], r[3])) {
				boolean out = f.out() == Dirs.FRONT ? r[1] < p.z0 : f.out() == Dirs.LEFT ? r[0] < p.x0 : f.out() == Dirs.RIGHT && r[2] > p.x1;
				if (!out) {
					continue;
				}
				for (int i = 0; i < f.length(); i++) {
					d.set(f.x(i), ya - 1, f.z(i), beam.log(f.out().getAxis()));
					if ((i & 1) == 0) {
						d.set(f.x(i), ya - 2, f.z(i), dress.stairsOrSlab(f.out().getOpposite(), Half.TOP));
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ openings

	private static void windows(Draft d, Plan p, Tower tw, boolean timberTop) {
		Stone dress = dressing(d);
		Paint glass = d.mat(Role.GLASS).pane();
		int f1 = p.floors[1];
		int f2 = p.floors[2];
		int fd = p.floors[3];
		Direction stairSide = p.right ? Dirs.RIGHT : Dirs.LEFT;
		for (Side f : tw.faces()) {
			boolean stair = f.out() == stairSide || f.out() == Dirs.BACK;
			int mid = f.middle();
			// the workshop: slits beside the door, one in each other face
			if (f.out() == Dirs.FRONT) {
				Details.slit(d, f, 1, 1, 2, dress, glass);
				Details.slit(d, f, 5, 1, 2, dress, glass);
			} else if (!stair) {
				Details.slit(d, f, mid, 1, 2, dress, glass);
			}
			// the laboratory: an arched light (a slit where the stair winds behind the wall)
			int h1 = Math.min(3, f2 - f1 - 2);
			if (stair) {
				Details.slit(d, f, mid, f1 + 1, Math.max(1, h1 - 1), dress, glass);
			} else if (h1 >= 2) {
				Details.lights(d, f, mid - 1, 2, f1 + 1, h1, dress, glass);
			}
			// the study: paired lights to the front, a single light elsewhere
			int h2 = Math.min(2, fd - f2 - 2);
			if (h2 >= 1 && !timberTop) {
				if (f.out() == Dirs.FRONT) {
					Details.lights(d, f, mid - 1, 2, f2 + 1, h2 + 1, dress, glass);
				} else if (!stair) {
					Details.arch(d, f, mid, 1, f2 + 1, h2, dress, glass);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ the roof

	/** The parapet, the stair house with its cap, the flue. Returns the highest row. */
	private static int roof(Draft d, Plan p, Tradition t, Tower tw, int top) {
		int fd = p.floors[3];
		Stone dress = dressing(d);
		boolean rail = t == Tradition.TIMBER || t == Tradition.ALPINE || t == Tradition.KARST || t == Tradition.STILT || t == Tradition.LOG;
		int peak = fd + 1;
		// the parapet round the deck: battlements, or a railing over a coped kerb in the timber traditions
		try (var s = d.stage(StageKind.WALLS)) {
			int[] r = p.top;
			for (int[] c : Draft.ring(r[0], r[1], r[2], r[3])) {
				boolean corner = (c[0] == r[0] || c[0] == r[2]) && (c[1] == r[1] || c[1] == r[3]);
				if (rail) {
					d.set(c[0], fd, c[1], corner ? dress.full() : d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING).fence() : Sacra.fence(d));
				} else {
					d.set(c[0], fd, c[1], tw.body.full());
					if (fd + 1 <= top && (((c[0] + c[1]) & 1) == 0 || corner)) {
						d.set(c[0], fd + 1, c[1], dress.has(Form.SLAB) && !corner ? dress.slab(SlabType.BOTTOM) : dress.full());
					}
				}
			}
		}
		// lanterns on the parapet's front corners
		for (int x : new int[]{p.top[0], p.top[2]}) {
			int y = rail ? fd + 1 : fd + 2;
			if (y <= top) {
				Sacra.standingLantern(d, x, y, p.top[1], StageKind.EXTERIOR);
			}
		}
		// the stair house over the newel's head: the back corner's 5 × 5, walls three rows, its cap
		int sx0 = p.right ? p.x1 - 4 : p.x0;
		int sz0 = p.z0 + 2;
		int[] exit = landing(p, 3);
		if (p.house) {
			Stone walls = tw.body;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int[] c : Draft.ring(sx0, sz0, sx0 + 4, sz0 + 4)) {
					for (int y = fd; y <= fd + 1; y++) {
						boolean corner = (c[0] == sx0 || c[0] == sx0 + 4) && (c[1] == sz0 || c[1] == sz0 + 4);
						d.set(c[0], y, c[1], corner ? dress.full() : walls.full());
					}
				}
				// the door out onto the deck, from the newel's last turn
			}
			Side doorSide = exit[1] == sz0 ? Side.of(sx0, sz0, sx0 + 4, sz0 + 4, Dirs.FRONT) : Side.of(sx0, sz0, sx0 + 4, sz0 + 4, p.right ? Dirs.LEFT : Dirs.RIGHT);
			int dx = exit[1] == sz0 ? exit[0] : exit[0];
			int dz = exit[1] == sz0 ? exit[1] : exit[1];
			d.inStage(StageKind.OPENINGS, () -> d.door(dx, fd, dz, d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR), doorSide.out(),
				DoorHingeSide.LEFT));
			// slits in the stair house, its roof
			for (Side f : Side.of(sx0, sz0, sx0 + 4, sz0 + 4)) {
				if (f.out() != doorSide.out() && (f.out() == Dirs.BACK || f.out() == (p.right ? Dirs.RIGHT : Dirs.LEFT))) {
					Details.slit(d, f, 2, fd + 1, 1, dress, d.mat(Role.GLASS).pane());
				}
			}
			try (var s = d.stage(StageKind.ROOF)) {
				for (int z = sz0 + 1; z <= sz0 + 3; z++) {
					for (int x = sx0 + 1; x <= sx0 + 3; x++) {
						d.set(x, fd + 2, z, dress.full());
					}
				}
			}
			Lighting.chandelier(d, p.postX(), p.postZ(), fd + 2, fd + 1);
			Towers.Cap cap = cap(d, t);
			boolean caphouse = t == Tradition.TIMBER || t == Tradition.STONE || t == Tradition.LOG || t == Tradition.STILT;
			if (caphouse) {
				// a caphouse: a little gabled room at the stair's head, its gable to the street
				var o = dev.voidmc.hearth.build.kit.Roofs.Opts.of(d).withPitch(1.0).withEaves(0, 0);
				var built = dev.voidmc.hearth.build.kit.Roofs.build(d, sx0, sz0, sx0 + 4, sz0 + 4, fd + 1,
					dev.voidmc.hearth.build.style.Habits.RoofForm.GABLE, Direction.Axis.Z, o, false, false);
				peak = Math.max(peak, built.roof().ridgeY);
				int ry = built.roof().surfaceY(sx0 + 2, sz0);
				if (ry != Integer.MIN_VALUE && ry + 3 <= top && t != Tradition.STONE) {
					d.inStage(StageKind.ROOF, () -> d.set(sx0 + 2, ry + 1, sz0, Details.rod()));
					peak = Math.max(peak, ry + 1);
				}
			} else if (cap == Towers.Cap.ONION || cap == Towers.Cap.DOME) {
				// a drum three square on the stair house, coped round, the onion or the dome over it
				try (var s = d.stage(StageKind.ROOF)) {
					for (int[] c : Draft.ring(sx0, sz0, sx0 + 4, sz0 + 4)) {
						d.set(c[0], fd + 2, c[1], dress.has(Form.SLAB) ? dress.slab(SlabType.BOTTOM) : dress.full());
					}
					for (int[] c : Draft.ring(sx0 + 1, sz0 + 1, sx0 + 3, sz0 + 3)) {
						d.set(c[0], fd + 3, c[1], tw.body.full());
					}
					d.set(sx0 + 2, fd + 3, sz0 + 2, dress.full());
				}
				peak = Math.max(peak, Towers.cap(d, sx0 + 1, sz0 + 1, 3, fd + 3, cap, top));
			} else {
				peak = Math.max(peak, Towers.cap(d, sx0, sz0, 5, fd + 1, cap, top));
			}
		} else {
			// no room for a stair house: a railing round the newel's head on the deck, open at its landing
			try (var s = d.stage(StageKind.INTERIOR)) {
				for (int a = 0; a <= 3; a++) {
					for (int b = 1; b <= 4; b++) {
						int x = p.ax(a);
						int z = p.bz(b);
						boolean edge = a == 3 || b == 1;
						if (edge && !(x == exit[0] && z == exit[1]) && d.isEmpty(x, fd, z)) {
							d.set(x, fd, z, Sacra.fence(d));
						}
					}
				}
			}
		}
		// the flue: up the side wall over the athanor, out on a breast where the plot has room, smoking over the parapet
		int fx = p.right ? p.x0 : p.x1;
		int ox = p.right ? fx - 1 : fx + 1;
		int fz = p.bz(4);
		boolean breast = ox >= 0 && ox < d.frontage();
		Mat chimney = d.mat(Role.CHIMNEY);
		int stackTop = Math.min(top - 1, fd + (p.house ? 2 : 2));
		try (var s = d.stage(StageKind.WALLS)) {
			// the flue in the wall over the athanor, and above the deck the stack
			for (int y = 1; y < fd; y++) {
				d.set(fx, y, fz, chimney.full());
			}
			if (breast) {
				int g = d.site().ground(ox, fz);
				for (int y = Math.min(0, g) - 1; y <= stackTop; y++) {
					d.set(ox, y, fz, y < 0 ? d.mat(Role.FOUNDATION).full() : chimney.full());
				}
			} else {
				for (int y = fd; y <= stackTop; y++) {
					d.set(fx, y, fz, chimney.full());
				}
			}
		}
		int cxs = breast ? ox : fx;
		if (stackTop + 1 <= top) {
			d.inStage(StageKind.ROOF, () -> d.set(cxs, stackTop + 1, fz, States.lit(Blocks.CAMPFIRE, true)
				.trySetValue(BlockStateProperties.SIGNAL_FIRE, false)));
			peak = Math.max(peak, stackTop + 1);
		}
		return peak;
	}

	/**
	 * The dressed stone of the quoins, courses, window surrounds and parapet: the culture's own dressing where the shaft is
	 * its walls' stone or render; where the shaft falls back on plain masonry (a timber or log town's), the plinth stone
	 * tooled crisp, so the tower is not salted with a pale accent stone that belongs to another wall.
	 */
	private static Stone dressing(Draft d) {
		boolean render = d.habits().walls().kind() == WallKind.TIMBER_FRAME && !d.mat(Role.WALL).has(Form.LOG);
		if (Stone.stony(d, Role.WALL) || render) {
			return Stone.dressing(d);
		}
		return Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH).crisp() : Stone.footing(d).crisp();
	}

	/** The cap a tradition puts on a little stair house. */
	private static Towers.Cap cap(Draft d, Tradition t) {
		return switch (t) {
			case TIMBER, LOG -> Towers.Cap.SPIRE;
			case ALPINE -> Towers.Cap.ONION;
			case STONE -> d.style().culture() == Culture.MOORLAND ? Towers.Cap.CONE : Towers.Cap.PYRAMID;
			case MEDITERRANEAN -> Towers.Cap.HIP;
			case ADOBE -> Towers.Cap.DOME;
			case STEPPE -> Towers.Cap.BATTLEMENT;
			case KARST -> Towers.Cap.PAGODA;
			case STILT -> Towers.Cap.PYRAMID;
		};
	}

	// ------------------------------------------------------------------------------------------------ inside

	/**
	 * The cells every floor keeps free: the walk from the door (or the landing) along the stair's room side, so the newel
	 * can be stepped onto and off at any of its turns.
	 */
	private static boolean walk(Plan p, int x, int z) {
		for (int a = 0; a <= 3; a++) {
			if (x == p.ax(a) && z == p.bz(1)) {
				return true;
			}
		}
		for (int b = 1; b <= 4; b++) {
			if (x == p.ax(3) && z == p.bz(b)) {
				return true;
			}
		}
		return false;
	}

	/** The workshop: the athanor under its flue, the crucible, stores and tools; a lantern over the walk. */
	private static void workshop(Draft d, Plan p) {
		int y = 0;
		Direction in = p.across().getOpposite();
		// the athanor at the far side's back, under the flue
		d.inStage(StageKind.INTERIOR, () -> {
			d.set(p.ax(4), y, p.bz(4), States.facing(Blocks.BLAST_FURNACE, in));
			d.set(p.ax(4), y, p.bz(3), States.facing(Blocks.FURNACE, in));
			d.set(p.ax(4), y + 1, p.bz(4), d.mat(Role.CHIMNEY).full());
			d.set(p.ax(4), y + 1, p.bz(3), d.mat(Role.CHIMNEY).slab(SlabType.BOTTOM));
		});
		Studies.crucible(d, p.ax(4), y, p.bz(2), true);
		// stores along the front wall either side of the door
		Furniture.chest(d, p.ax(0), y, p.bz(0), Dirs.BACK, List.of(new Object[]{"minecraft:coal", 16}, new Object[]{"minecraft:raw_iron", 6},
			new Object[]{"minecraft:redstone", 8}));
		Furniture.barrel(d, p.ax(1), y, p.bz(0), Direction.UP);
		d.inStage(StageKind.INTERIOR, () -> d.set(p.ax(3), y, p.bz(0), States.attached(Blocks.GRINDSTONE,
			net.minecraft.world.level.block.state.properties.AttachFace.FLOOR, Dirs.BACK)));
		Furniture.chest(d, p.ax(4), y, p.bz(1), in, List.of(new Object[]{"minecraft:glass_bottle", 12}, new Object[]{"minecraft:sugar", 6}));
		Furniture.barrel(d, p.ax(4), y, p.bz(0), Direction.UP);
		Sacra.candles(d, p.ax(4), y + 1, p.bz(0), 3, StageKind.INTERIOR);
		Sacra.candles(d, p.ax(1), y + 1, p.bz(0), 2, StageKind.INTERIOR);
		int ceil = p.floors[1] - 1;
		if (ceil - 2 >= 2) {
			Lighting.chandelier(d, p.ax(3), p.bz(1), ceil, ceil - 1);
		}
	}

	/** The laboratory: benches of brewing stands and shelves of bottles round the walls, the cauldron, his lectern. */
	private static void laboratory(Draft d, Plan p) {
		int y = p.floors[1];
		Direction in = p.across().getOpposite();
		int[] land = landing(p, 1);
		// the front benches under the window, shelves of bottles over them
		for (int a = 0; a <= 4; a++) {
			int x = p.ax(a);
			int z = p.bz(0);
			if (a == 2) {
				continue;
			}
			Studies.counter(d, x, y, z, Dirs.BACK, a);
			if (y + 2 < p.floors[2] - 1) {
				Studies.bottleShelf(d, x, y + 2, z, Dirs.BACK);
			}
		}
		// the far wall: the cauldron, a still on a barrel, the lectern with the book
		Studies.crucible(d, p.ax(4), y, p.bz(1), true);
		Studies.counter(d, p.ax(4), y, p.bz(2), in, 0);
		Studies.counter(d, p.ax(4), y, p.bz(3), in, 3);
		Studies.lectern(d, p.ax(4), y, p.bz(4), in, JobType.SCHOLAR, "Of the Great Work");
		for (int b : new int[]{1, 3}) {
			if (y + 2 < p.floors[2] - 1) {
				Studies.bottleShelf(d, p.ax(4), y + 2, p.bz(b), in);
			}
		}
		// a brewing stand under the front window on its own bench
		Studies.counter(d, p.ax(2), y, p.bz(0), Dirs.BACK, 4);
		int ceil = p.floors[2] - 1;
		if (ceil - y >= 3 && !(land[0] == p.ax(3) && land[1] == p.bz(1))) {
			Lighting.chandelier(d, p.ax(3), p.bz(1), ceil, ceil - 1);
		} else if (ceil - y >= 3) {
			Lighting.chandelier(d, p.ax(2), p.bz(1), ceil, ceil - 1);
		}
	}

	/** The study: the bed, the desk under the window, shelves of books, a chest, a reading lectern. */
	private static void study(Draft d, Plan p) {
		int y = p.floors[2];
		Direction in = p.across().getOpposite();
		Studies.books(d, p.ax(0), y, p.bz(0), 2, Dirs.BACK);
		Furniture.chest(d, p.ax(1), y, p.bz(0), Dirs.BACK, List.of(new Object[]{"minecraft:book", 4}, new Object[]{"minecraft:paper", 12},
			new Object[]{"minecraft:ink_sac", 3}));
		Studies.desk(d, p.ax(2), y, p.bz(0), Dirs.BACK, false);
		Furniture.chair(d, p.ax(3), y, p.bz(0), Dirs.FRONT);
		Studies.books(d, p.ax(4), y, p.bz(0), 2, Dirs.BACK);
		Studies.lectern(d, p.ax(4), y, p.bz(1), in, null, "Notes");
		Studies.bookShelf(d, p.ax(4), y, p.bz(2), in);
		Furniture.bed(d, p.ax(4), y, p.bz(4), Dirs.FRONT);
		int ceil = p.floors[3] - 1;
		if (ceil - y >= 3) {
			Lighting.chandelier(d, p.ax(3), p.bz(2), ceil, ceil - 1);
		} else {
			Sacra.candles(d, p.ax(1), y + 1, p.bz(0), 3, StageKind.INTERIOR);
		}
	}

	/** The roof platform: herbs drying in pots along the parapet, a still on a barrel, a bench and the view. */
	private static void platform(Draft d, Plan p) {
		int y = p.floors[3];
		int z = p.bz(0);
		Studies.counter(d, p.ax(4), y, p.bz(1), p.across().getOpposite(), 0);
		for (int a : new int[]{0, 2}) {
			int x = p.ax(a);
			if (d.isEmpty(x, y, z)) {
				d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, Blocks.FLOWER_POT.defaultBlockState()));
			}
		}
		if (d.isEmpty(p.ax(1), y, z)) {
			dev.voidmc.hearth.build.kit.landscape.Benches.bench(d, p.ax(1), y, z, Dirs.FRONT, p.across(), 1, false);
		}
		d.poi(PoiType.VIEWPOINT, p.ax(3), y, z, Dirs.FRONT);
	}
}
