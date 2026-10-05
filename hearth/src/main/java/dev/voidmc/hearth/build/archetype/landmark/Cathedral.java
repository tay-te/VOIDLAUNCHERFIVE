package dev.voidmc.hearth.build.archetype.landmark;

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
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Openings;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.landmark.Crowns;
import dev.voidmc.hearth.build.kit.landmark.Details;
import dev.voidmc.hearth.build.kit.landmark.Grounds;
import dev.voidmc.hearth.build.kit.landmark.Shapes;
import dev.voidmc.hearth.build.kit.landmark.Stone;
import dev.voidmc.hearth.build.kit.landmark.Tower;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landmark/cathedral}: the city's cathedral, raised on the chapel's site and the close kept round it. A
 * west front on the street, a nave and two aisles running back to a transept, a choir and a round apse, laid out in bays
 * of piers; the aisles lower than the nave under lean-to roofs, the nave lit by a clerestory over them. Gothic in the
 * temperate, maritime, moorland and wisteria styles: twin west towers with needle spires (or low towers and a great spire
 * over the crossing), three portals, a rose window over the main one and at each transept end, buttresses topped with
 * pinnacles and flying buttresses leaping the aisle roofs to the clerestory, a flèche on the crossing. Romanesque in the
 * mediterranean: low tiled roofs, a pediment front with its rose, a campanile at a corner and a dome on a drum over the
 * crossing. Baroque in the alpine: white walls with grey pilasters, twin towers under onion domes. On a wide, deep close
 * a cloister opens off one aisle round a garth with a well. Inside: a chequered floor, pews either side of the central
 * aisle facing the altar, banners on the piers, chandeliers on chains from the ceiling, choir stalls, side altars, a font,
 * and the priest's lectern by the crossing. The towers' spiral stairs climb to belfries with bells (viewpoints).
 *
 * <p>Built in the order a cathedral rises: the podium and footings, the piers and buttresses (a skeleton of stone), the
 * walls between them, floors and ceilings, roofs and spires, glass, then the furnishings and the churchyard.
 *
 * <p>Params (all optional): {@code towers} ({@code twin}, {@code crossing}, {@code campanile}), {@code cloister}
 * (boolean), {@code variant}. As a rebuild of the chapel the planner also sends {@code replaces}.
 */
public final class Cathedral implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landmark/cathedral"), ArchetypeCategory.LANDMARK, Set.of(JobType.PRIEST), 0,
		17, 25, 29, 41, 26, 56, SettlementTier.CITY,
		Set.of(Culture.TEMPERATE, Culture.WISTERIA, Culture.MARITIME, Culture.ALPINE, Culture.MOORLAND, Culture.MEDITERRANEAN, Culture.NORTHERN),
		Set.of(ArchetypeInfo.SiteFlag.VERTICAL), List.of(), 1, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	enum Manner {
		GOTHIC, ROMANESQUE, BAROQUE
	}

	enum Towers {
		TWIN, CROSSING, CAMPANILE
	}

	/** Every line of the plan, in local coordinates. */
	static final class Plan {
		Manner manner;
		Towers towers;
		int w;
		int dp;
		int top;
		int n;
		int a;
		int bL;
		int aL;
		int nL;
		int nR;
		int aR;
		int bR;
		int xc;
		int zF;
		int t;
		int zT1;
		int zX0;
		int zX1;
		int zA;
		int apseR;
		int bay;
		List<Integer> piers = new ArrayList<>();
		int yA;
		int yN;
		double pitch;
		boolean cloister;
		boolean cloisterRight;
		int clX0;
		int clX1;
		int clZ0;
		int clZ1;
		Roofs.Roof[] aisleRoofs = new Roofs.Roof[2];

		boolean inCrossing(int z) {
			return z >= this.zX0 && z <= this.zX1;
		}
	}

	@Override
	public void draw(Draft d) {
		if (d.style().culture() == Culture.NORTHERN) {
			// the north raises a great stave church
			StaveCathedral.draw(d);
			return;
		}
		Plan p = plan(d);
		Stone body = p.manner == Manner.GOTHIC ? Stone.ashlar(d) : Stone.masonry(d);
		Stone dress = Stone.dressing(d);
		Stone found = Stone.footing(d);
		d.body(p.bL, p.zF, p.bR, p.dp - 1);
		d.eave(p.yN);

		// the main entrance: the doorstep of the west portal, registered before the towers' doors
		d.entrance(p.xc, 0, p.zF);
		ground(d, p, found);
		frame(d, p, dress);
		walls(d, p, body, dress);
		floors(d, p, body, dress);
		Roofs.Roof roof = roofs(d, p, body);
		int peak = towers(d, p, body, dress, roof);
		buttressTops(d, p, dress);
		openings(d, p, body, dress);
		interior(d, p, dress, roof);
		if (p.cloister) {
			cloister(d, p, body, dress);
		}
		exterior(d, p, dress);
		Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, Math.min(p.top, Math.max(peak, roof.ridgeY) + 2));
		dev.voidmc.hearth.build.kit.refine.Voids.finish(d);
	}

	// ------------------------------------------------------------------------------------------------ the plan

	static Plan plan(Draft d) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		p.top = d.maxHeight() - 1;
		Culture c = d.style().culture();
		Habits h = d.habits();
		p.manner = c == Culture.MEDITERRANEAN || c == null && h.roof().pitch() < 0.75 ? Manner.ROMANESQUE
			: c == Culture.ALPINE ? Manner.BAROQUE : Manner.GOTHIC;
		// the cloister, beside the nave on a wide and deep close
		p.cloister = p.w >= 24 && p.dp >= 33 && d.param("cloister", d.chance(0.85));
		p.a = 3;
		if (p.cloister) {
			p.n = 5;
		} else {
			p.n = p.w >= 23 ? 9 : p.w >= 19 ? 7 : 5;
			if (p.n == 9 && p.w >= 25) {
				// broad aisles on the widest closes; aisles stay odd so the west towers over them centre their spires
				p.a = 5;
			}
		}
		int churchW = p.n + 2 * p.a + 6;
		p.cloisterRight = d.chance(0.5);
		int cx0 = p.cloister ? (p.cloisterRight ? 0 : p.w - churchW) : (p.w - churchW) / 2;
		p.bL = cx0;
		p.aL = cx0 + 1;
		p.nL = p.aL + p.a + 1;
		p.nR = p.nL + p.n + 1;
		p.aR = p.nR + p.a + 1;
		p.bR = p.aR + 1;
		p.xc = (p.nL + p.nR) / 2;
		// along the depth: forecourt, west front and towers, nave bays, crossing, choir, apse
		p.zF = p.dp >= 35 ? 2 : 1;
		p.t = p.a + 2;
		p.zT1 = p.zF + p.t - 1;
		p.apseR = (p.n + 1) / 2;
		p.zA = p.dp - 2 - p.apseR;
		p.bay = p.dp >= 37 && p.n >= 7 ? 4 : 3;
		int avail = p.zA - p.zT1 - 1;           // rows for nave, crossing and choir
		int crossing = p.n + 2;
		int forNave = avail - crossing - 2;
		int bays = Math.max(2, (forNave + 1) / p.bay);
		int naveLen = bays * p.bay - 1;
		p.zX0 = p.zT1 + naveLen + 1;
		p.zX1 = p.zX0 + crossing - 1;
		for (int z = p.zT1; z <= p.zX0; z += p.bay) {
			p.piers.add(z);
		}
		for (int z = p.zX1 + p.bay; z < p.zA; z += p.bay) {
			p.piers.add(z);
		}
		// heights
		p.yA = p.top >= 32 ? 6 : 5;
		// the nave's height from a 44-block sky whatever the plot allows: extra height goes to the towers and spires
		p.yN = Math.max(p.yA + p.a + 6, (int) Math.round(Math.min(p.top, 43) * (p.manner == Manner.ROMANESQUE ? 0.33 : 0.37)));
		p.pitch = switch (p.manner) {
			case GOTHIC -> 1.4;
			case ROMANESQUE -> 0.6;
			case BAROQUE -> 1.0;
		};
		String tw = d.param("towers", "");
		p.towers = switch (tw) {
			case "twin" -> Towers.TWIN;
			case "crossing" -> Towers.CROSSING;
			case "campanile" -> Towers.CAMPANILE;
			default -> p.manner == Manner.ROMANESQUE ? Towers.CAMPANILE : p.manner == Manner.GOTHIC && d.chance(0.3) ? Towers.CROSSING : Towers.TWIN;
		};
		if (p.cloister) {
			p.clX0 = p.cloisterRight ? p.bR : 0;
			p.clX1 = p.cloisterRight ? p.w - 1 : p.bL;
			p.clZ0 = p.zT1 + 1;
			p.clZ1 = p.zX0 - 1;
			if (p.clZ1 - p.clZ0 < 6) {
				p.cloister = false;
			}
		}
		return p;
	}

	// ------------------------------------------------------------------------------------------------ ground

	private static void ground(Draft d, Plan p, Stone found) {
		int x0 = p.cloister ? Math.min(p.bL, p.clX0) : p.bL;
		int x1 = p.cloister ? Math.max(p.bR, p.clX1) : p.bR;
		Grounds.podium(d, x0, 0, x1, p.dp - 1, 0, false);
		// footings under every wall line, down through the podium's fill to firm ground
		footing(d, p.aL, p.zT1, p.aL, p.zA, found);
		footing(d, p.aR, p.zT1, p.aR, p.zA, found);
		footing(d, p.bL, p.zX0, p.bL, p.zX1, found);
		footing(d, p.bR, p.zX0, p.bR, p.zX1, found);
		footing(d, p.aL, p.zF, p.aR, p.zF, found);
		for (int[] c : apseRing(p)) {
			footing(d, c[0], c[1], c[0], c[1], found);
		}
	}

	private static void footing(Draft d, int x0, int z0, int x1, int z1, Stone found) {
		try (var s = d.stage(StageKind.FOUNDATION)) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					if (x < 0 || z < 0 || x >= d.frontage() || z >= d.depth()) {
						continue;
					}
					int g = d.site().ground(x, z);
					for (int y = Math.min(-1, g - 2); y <= -1; y++) {
						d.set(x, y, z, found.full());
					}
				}
			}
		}
	}

	/** The apse's outer ring (the half beyond the choir's end). */
	static List<int[]> apseRing(Plan p) {
		List<int[]> out = new ArrayList<>();
		for (int[] c : Shapes.ring(p.xc, p.zA, p.apseR)) {
			if (c[1] >= p.zA) {
				out.add(c);
			}
		}
		return out;
	}

	static List<int[]> apseInside(Plan p) {
		List<int[]> out = new ArrayList<>();
		List<int[]> ring = apseRing(p);
		for (int[] c : Shapes.disc(p.xc, p.zA, p.apseR)) {
			if (c[1] < p.zA) {
				continue;
			}
			boolean onRing = false;
			for (int[] r : ring) {
				if (r[0] == c[0] && r[1] == c[1]) {
					onRing = true;
					break;
				}
			}
			if (!onRing) {
				out.add(c);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ the skeleton

	/** Piers on the arcade lines, the crossing piers, and the buttresses outside the aisle walls (stage FRAME). */
	private static void frame(Draft d, Plan p, Stone dress) {
		try (var s = d.stage(StageKind.FRAME)) {
			for (int z : p.piers) {
				for (int x : new int[]{p.nL, p.nR}) {
					for (int y = 0; y <= p.yN; y++) {
						d.set(x, y, z, dress.full());
					}
				}
			}
			for (int z : new int[]{p.zX0, p.zX1}) {
				for (int x : new int[]{p.nL, p.nR}) {
					for (int y = 0; y <= p.yN; y++) {
						d.set(x, y, z, dress.full());
					}
				}
			}
			// buttresses against the aisle walls at every pier, against the transept ends and round the apse
			int yb = buttressTop(p);
			for (int z : p.piers) {
				if (z <= p.zT1 || p.inCrossing(z)) {
					continue;
				}
				for (int x : new int[]{p.bL, p.bR}) {
					for (int y = 0; y <= yb; y++) {
						d.set(x, y, z, dress.full());
					}
				}
			}
			for (int[] c : apseButtresses(p)) {
				for (int y = 0; y <= Math.min(p.yN - 2, yb + 4); y++) {
					d.set(c[0], y, c[1], dress.full());
				}
			}
		}
	}

	static int buttressTop(Plan p) {
		return p.manner == Manner.GOTHIC ? p.yA + 3 : p.yA;
	}

	/** Cells just outside the apse ring where buttresses stand (inside the plot). */
	static List<int[]> apseButtresses(Plan p) {
		List<int[]> out = new ArrayList<>();
		double r = p.apseR + 1;
		double k = Math.sqrt(0.5);
		int[][] pts = {{p.xc, p.zA + (int) Math.round(r)}, {(int) Math.round(p.xc + r * k), (int) Math.round(p.zA + r * k)},
			{(int) Math.round(p.xc - r * k), (int) Math.round(p.zA + r * k)}};
		for (int[] c : pts) {
			if (c[1] < p.dp && c[0] >= 0 && c[0] < p.w) {
				out.add(c);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ walls

	private static void walls(Draft d, Plan p, Stone body, Stone dress) {
		try (var s = d.stage(StageKind.WALLS)) {
			// the aisle walls (not across the transept), and their ends at the choir's end
			for (int z = p.zT1 + 1; z <= p.zA; z++) {
				if (p.inCrossing(z)) {
					continue;
				}
				for (int x : new int[]{p.aL, p.aR}) {
					for (int y = 0; y <= p.yA; y++) {
						set(d, x, y, z, body, dress, y == p.yA);
					}
				}
			}
			for (int x = p.aL; x <= p.aR; x++) {
				if (x > p.nL && x < p.nR) {
					continue;
				}
				for (int y = 0; y <= p.yA; y++) {
					set(d, x, y, p.zA, body, dress, y == p.yA);
				}
			}
			// the nave walls: arcades below the aisle ceiling, a triforium and clerestory above
			for (int z = p.zT1 + 1; z < p.zA; z++) {
				if (p.inCrossing(z)) {
					continue;
				}
				for (int x : new int[]{p.nL, p.nR}) {
					for (int y = p.yA; y <= p.yN; y++) {
						set(d, x, y, z, body, dress, y == p.yN);
					}
				}
			}
			// the west front between the towers (or across the whole width when there are none)
			int f0 = p.towers == Towers.TWIN ? p.nL : p.aL;
			int f1 = p.towers == Towers.TWIN ? p.nR : p.aR;
			for (int x = f0; x <= f1; x++) {
				boolean nave = x >= p.nL && x <= p.nR;
				int top = nave ? p.yN : p.yA;
				for (int y = 0; y <= top; y++) {
					set(d, x, y, p.zF, body, dress, y == top || (x == p.nL || x == p.nR) && nave);
				}
			}
			if (p.towers != Towers.TWIN) {
				// aisle bays behind the front where no tower stands: aisle walls and the nave walls over them
				for (int z = p.zF; z <= p.zT1; z++) {
					for (int x : new int[]{p.aL, p.aR}) {
						for (int y = 0; y <= p.yA; y++) {
							set(d, x, y, z, body, dress, y == p.yA);
						}
					}
					if (z > p.zF) {
						for (int x : new int[]{p.nL, p.nR}) {
							for (int y = p.yA; y <= p.yN; y++) {
								set(d, x, y, z, body, dress, y == p.yN);
							}
						}
					}
				}
			}
			// the transept: its end walls, and its side walls (open under the aisle ceiling where the aisle runs through)
			for (int z = p.zX0; z <= p.zX1; z++) {
				for (int x : new int[]{p.bL, p.bR}) {
					for (int y = 0; y <= p.yN; y++) {
						set(d, x, y, z, body, dress, y == p.yN || z == p.zX0 || z == p.zX1);
					}
				}
			}
			for (int z : new int[]{p.zX0, p.zX1}) {
				for (int x = p.bL; x <= p.bR; x++) {
					if (x >= p.nL && x <= p.nR) {
						continue;
					}
					boolean overAisle = x > p.aL && x < p.nL || x > p.nR && x < p.aR;
					for (int y = overAisle ? p.yA : 0; y <= p.yN; y++) {
						set(d, x, y, z, body, dress, y == p.yN);
					}
				}
			}
			// the apse
			for (int[] c : apseRing(p)) {
				for (int y = 0; y <= p.yN; y++) {
					set(d, c[0], y, c[1], body, dress, y == p.yN);
				}
			}
		}
		// carve the volumes
		d.carve(p.nL + 1, 0, p.zF + 1, p.nR - 1, p.yN - 1, p.zA);
		d.carve(p.aL + 1, 0, p.zT1 + 1, p.nL - 1, p.yA - 1, p.zA - 1);
		d.carve(p.nR + 1, 0, p.zT1 + 1, p.aR - 1, p.yA - 1, p.zA - 1);
		d.carve(p.bL + 1, 0, p.zX0 + 1, p.bR - 1, p.yN - 1, p.zX1 - 1);
		for (int[] c : apseInside(p)) {
			d.carve(c[0], 0, c[1], c[0], p.yN - 1, c[1]);
		}
		if (p.towers != Towers.TWIN) {
			d.carve(p.aL + 1, 0, p.zF + 1, p.nL - 1, p.yA - 1, p.zT1);
			d.carve(p.nR + 1, 0, p.zF + 1, p.aR - 1, p.yA - 1, p.zT1);
		}
	}

	/** A wall block: the body, or the dressing on a course row. */
	private static void set(Draft d, int x, int y, int z, Stone body, Stone dress, boolean course) {
		d.set(x, y, z, course ? dress.full() : body.full());
	}

	// ------------------------------------------------------------------------------------------------ floors and ceilings

	private static void floors(Draft d, Plan p, Stone body, Stone dress) {
		Mat paving = d.mat(Role.PAVING);
		// no flat ceilings: the nave, the transept and the choir rise open to their roofs, the aisles to their lean-tos, so
		// no sealed void hangs over the church (and the nave reads as tall inside as it stands outside)
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = p.zF + 1; z <= p.dp - 1; z++) {
				for (int x = p.bL + 1; x <= p.bR - 1; x++) {
					if (!interior(p, x, z)) {
						continue;
					}
					boolean nave = x > p.nL && x < p.nR;
					d.set(x, -1, z, nave ? ((x + z & 1) == 0 ? dress.full() : body.full()) : paving.full());
				}
			}
		}
	}

	/** Whether a cell is inside the church (nave, aisles, crossing, transept, choir, apse) at ground level. */
	static boolean interior(Plan p, int x, int z) {
		if (z > p.zA) {
			for (int[] c : apseInside(p)) {
				if (c[0] == x && c[1] == z) {
					return true;
				}
			}
			return false;
		}
		if (p.inCrossing(z)) {
			return x > p.bL && x < p.bR && z > p.zX0 && z < p.zX1 || x > p.aL && x < p.aR && (z == p.zX0 || z == p.zX1) && x != p.nL && x != p.nR;
		}
		boolean nave = x > p.nL && x < p.nR && z > p.zF;
		boolean aisle = (x > p.aL && x < p.nL || x > p.nR && x < p.aR) && z > p.zT1 && z < p.zA;
		boolean towerless = p.towers != Towers.TWIN && (x > p.aL && x < p.nL || x > p.nR && x < p.aR) && z > p.zF && z <= p.zT1;
		return nave || aisle || towerless;
	}

	// ------------------------------------------------------------------------------------------------ roofs

	private static Roofs.Roof roofs(Draft d, Plan p, Stone body) {
		Mat roof = d.mat(Role.ROOF);
		Mat trim = d.mat(Role.ROOF_TRIM);
		int e = 1;
		double pitch = p.pitch;
		// nave, choir, transept and apse as one surface
		int rx0 = p.nL - e;
		int rx1 = p.nR + e;
		Roofs.Field nave = Roofs.gable(rx0, p.zF, rx1, p.zA, Direction.Axis.Z, Roofs.Profile.straight(pitch));
		Roofs.Field transept = Roofs.gable(p.bL, p.zX0 - e, p.bR, p.zX1 + e, Direction.Axis.X, Roofs.Profile.straight(pitch));
		Roofs.Field cone = Roofs.cone(p.xc, p.zA, p.apseR + e, pitch);
		Roofs.Field apse = (x, z) -> z >= p.zA ? cone.h(x, z) : Double.NaN;
		Roofs.Field all = nave.max(transept).max(apse);
		int y0 = p.yN + 1;
		Roofs.Roof r = Roofs.raster(d, all, y0, Math.max(0, p.bL), p.zF, Math.min(p.w - 1, p.bR), p.dp - 1, roof, trim, Habits.Ridge.FULL);
		Shapes.support(d, r, roof.full());
		// the gable walls under it: the west front and the transept ends, and the nave walls up to the eaves
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = p.nL; x <= p.nR; x++) {
				fillUp(d, r, x, p.zF, p.yN + 1, body);
			}
			for (int z = p.zX0; z <= p.zX1; z++) {
				fillUp(d, r, p.bL, z, p.yN + 1, body);
				fillUp(d, r, p.bR, z, p.yN + 1, body);
			}
			for (int z = p.zF; z < p.zA; z++) {
				fillUp(d, r, p.nL, z, p.yN + 1, body);
				fillUp(d, r, p.nR, z, p.yN + 1, body);
			}
		}
		// the aisles' lean-to roofs, rising to the nave wall
		for (int side = 0; side < 2; side++) {
			int ex = side == 0 ? p.bL : p.aR + 1;
			int ix = side == 0 ? p.nL - 1 : p.nR + 1;
			int lo = Math.min(ex, ix);
			int hi = Math.max(ex, ix);
			int zs = p.towers == Towers.TWIN ? p.zT1 + 1 : p.zF - 1;
			Direction up = side == 0 ? Dirs.RIGHT : Dirs.LEFT;
			Roofs.Field shed = Roofs.shed(lo, Math.max(-1, zs), hi, p.zA + 1, up, Roofs.Profile.straight(1.0));
			Roofs.Field gap = (x, z) -> p.inCrossing(z) ? Double.NaN : shed.h(x, z);
			p.aisleRoofs[side] = Roofs.raster(d, gap, p.yA, Math.max(0, lo), Math.max(-1, zs), Math.min(p.w - 1, hi), Math.min(p.dp - 1, p.zA + 1), roof,
				trim, Habits.Ridge.SLAB);
		}
		return r;
	}

	private static void fillUp(Draft d, Roofs.Roof r, int x, int z, int from, Stone body) {
		if (!r.covers(x, z)) {
			return;
		}
		int under = r.bottomY(x, z);
		for (int y = from; y < under; y++) {
			d.set(x, y, z, body.full());
		}
	}

	// ------------------------------------------------------------------------------------------------ towers, crossing

	private static int towers(Draft d, Plan p, Stone body, Stone dress, Roofs.Roof roof) {
		int peak = roof.ridgeY;
		Stone base = Stone.stony(d, Role.PLINTH) ? Stone.of(d, Role.PLINTH) : Stone.footing(d);
		Crowns.Mats mats = p.manner == Manner.ROMANESQUE ? Crowns.Mats.of(d, dress) : Crowns.Mats.coppered(d, dress, 0.35);
		if (d.style().culture() == dev.voidmc.hearth.api.Culture.TEMPERATE && p.manner != Manner.BAROQUE) {
			// Bohemia's spires are dark shingle or slate, never a turquoise needle (copper only on the odd baroque onion)
			mats = new Crowns.Mats(d.mat(Role.ROOF), d.mat(Role.ROOF_TRIM), dress, d.mat(Role.FRAME),
				dev.voidmc.hearth.build.kit.civic.Bohemian.shingle(d));
		}
		Crowns.Kind westCrown = switch (p.manner) {
			case GOTHIC -> p.towers == Towers.CROSSING ? Crowns.Kind.PARAPET : d.chance(0.5) ? Crowns.Kind.SPIRE : Crowns.Kind.OCTAGON;
			case BAROQUE -> Crowns.Kind.ONION;
			case ROMANESQUE -> Crowns.Kind.PYRAMID;
		};
		List<Tower> west = new ArrayList<>();
		if (p.towers == Towers.TWIN) {
			west.add(new Tower(p.aL, p.zF, p.t, body, dress, base, false));
			west.add(new Tower(p.nR, p.zF, p.t, body, dress, base, false));
		} else if (p.towers == Towers.CAMPANILE) {
			boolean left = Noise.unit(d.seed(), 1, 2, 3) < 0.5;
			west.add(new Tower(left ? p.aL : p.nR, p.zF, p.t, body, dress, base, false));
		}
		int crownRows = Crowns.rows(westCrown, p.t, p.top);
		// the west towers carry their belfries clear of the nave's ridge, then the crown: a gothic pair stands a full stage
		// over the roof so the spires spring from the skyline, not from the roof
		int yT = p.towers == Towers.CROSSING ? Math.min(p.top - crownRows, roof.ridgeY + 2)
			: Math.min(p.top - crownRows, roof.ridgeY + (p.manner == Manner.ROMANESQUE ? 8 : p.manner == Manner.GOTHIC ? 8 : 6));
		yT = Math.max(yT, p.yN + 4);
		for (Tower t : west) {
			peak = Math.max(peak, westTower(d, p, t, yT, westCrown, mats));
		}
		// the crossing: a flèche, a tower with a great spire, or a dome on a drum
		int cx0 = p.nL;
		int cz0 = p.zX0;
		int cx1 = p.nR;
		int cz1 = p.zX1;
		if (p.manner == Manner.ROMANESQUE || p.manner == Manner.BAROQUE && d.chance(0.5)) {
			// an octagonal drum standing on the crossing, rising clear of the roofs, and a dome
			int drumTop = roof.ridgeY + 1;
			double cx = (cx0 + cx1) / 2.0;
			double cz = (cz0 + cz1) / 2.0;
			double rr = (cx1 - cx0) / 2.0 - 0.5;
			try (var s = d.stage(StageKind.WALLS)) {
				for (int y = p.yN + 1; y < drumTop; y++) {
					for (int[] c : Shapes.ring(cx, cz, rr)) {
						d.set(c[0], y, c[1], dress.full());
					}
				}
				// the drum's footing ring; the crossing rises open into the dome, the roofs cut back from inside the drum
				java.util.Set<Long> ring = new java.util.HashSet<>();
				for (int[] c : Shapes.ring(cx, cz, rr)) {
					d.set(c[0], p.yN, c[1], dress.full());
					ring.add(net.minecraft.core.BlockPos.asLong(c[0], 0, c[1]));
				}
				for (int[] c : Shapes.disc(cx, cz, rr)) {
					if (!ring.contains(net.minecraft.core.BlockPos.asLong(c[0], 0, c[1]))) {
						for (int y = p.yN; y < drumTop; y++) {
							d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
			peak = Math.max(peak, Crowns.crown(d, Crowns.Kind.DOME, cx0, cz0, cx1, cz1, drumTop, p.top, mats));
		} else if (p.towers == Towers.CROSSING) {
			Tower ct = new Tower(cx0, cz0, cx1 - cx0 + 1, body, dress, base, false);
			int ycT = Math.min(p.top - Math.max(12, (int) Math.round(p.top * 0.4)), roof.ridgeY + 8);
			ycT = Math.max(ycT, roof.ridgeY + 3);
			ct.shaft(d, p.yN + 1, ycT - 1);
			// the roofs cut back from inside the tower: the crossing rises open to the deck under the spire
			try (var s = d.stage(StageKind.WALLS)) {
				for (int y = p.yN; y < ycT; y++) {
					for (int z = cz0 + 1; z < cz1; z++) {
						for (int x = cx0 + 1; x < cx1; x++) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
			ct.openings(d, ycT - 5, 4, ct.size >= 9 ? Tower.Opening.TRIFORA : Tower.Opening.BIFORA, true, null);
			ct.band(d, ycT - 1);
			ct.cornice(d, ycT);
			// no floor over the crossing: it rises open into the tower, a lantern lit by the belfry's lights
			peak = Math.max(peak, Crowns.crown(d, Crowns.Kind.SPIRE, cx0, cz0, cx1, cz1, ycT + 1, p.top, mats));
		} else if (p.manner == Manner.GOTHIC) {
			// a flèche: a louvred lantern astride the crossing's ridge, a slender needle over it
			int fx = p.xc;
			int fz = (cz0 + cz1) / 2;
			int y0 = roof.surfaceY(fx, fz) + 1;
			if (y0 > 0 && y0 + 8 < p.top) {
				Mat frame = d.mat(Role.FRAME);
				Mat shutter = d.mat(Role.SHUTTER);
				try (var s = d.stage(StageKind.ROOF)) {
					for (int y = y0 - 2; y < y0; y++) {
						for (int dz = -1; dz <= 1; dz++) {
							for (int dx = -1; dx <= 1; dx++) {
								d.set(fx + dx, y, fz + dz, d.mat(Role.ROOF).full());
							}
						}
					}
					// an open lantern: slender posts at the corners, the louvres open to the sky, the mast up its middle
					Paint post = frame.has(Form.FENCE) ? frame.fence() : frame.log(Direction.Axis.Y);
					for (int y = y0; y < y0 + 3; y++) {
						for (int[] c : Draft.ring(fx - 1, fz - 1, fx + 1, fz + 1)) {
							boolean corner = Crowns.isCorner(c, fx - 1, fz - 1, fx + 1, fz + 1);
							Direction out = Dirs.nearest(c[0] - fx, c[1] - fz);
							if (corner) {
								d.set(c[0], y, c[1], post);
							} else if (y == y0 + 2 && shutter.has(Form.TRAPDOOR)) {
								d.set(c[0], y, c[1], shutter.trapdoor(out.getOpposite(), Half.TOP, false));
							}
						}
						d.set(fx, y, fz, frame.log(Direction.Axis.Y));
					}
					Details.bell(d, fx, y0 + 1, fz - 1, Dirs.FRONT);
				}
				peak = Math.max(peak, Crowns.crown(d, Crowns.Kind.SPIRE, fx - 1, fz - 1, fx + 1, fz + 1, y0 + 3, Math.min(p.top, y0 + 3 + 12), mats));
			}
		}
		return peak;
	}

	/** A west tower: plinth, courses at the aisle and nave heights, a belfry with bells, a door to its stair, a crown. */
	private static int westTower(Draft d, Plan p, Tower t, int yT, Crowns.Kind crown, Crowns.Mats mats) {
		Side fr = t.face(Dirs.FRONT);
		int door = fr.middle();
		t.plinth(d, 0, 1, false, fr.outX(door, 1), fr.outZ(door, 1));
		t.shaft(d, 2, yT - 1);
		t.course(d, p.yA + 1, false);
		t.course(d, p.yN + 1, false);
		int yb = yT - 6;
		t.ring(d, yb + 1, yT - 1, t.dress);
		Paint glass = d.mat(Role.GLASS).pane();
		// lancets up the shaft, the belfry's paired lights, the cornice
		for (int y = p.yA + 3; y + 3 < yb; y += 6) {
			t.openings(d, y, 3, Tower.Opening.SINGLE, true, glass);
		}
		t.openings(d, yb + 1, 4, t.size >= 7 ? Tower.Opening.TRIFORA : Tower.Opening.ARCH, true, null);
		t.band(d, yT - 1);
		t.cornice(d, yT);
		int peak = Crowns.crown(d, crown, t.x0, t.z0, t.x1, t.z1, yT + 1, p.top, mats);
		// the stair and the bells
		Grounds.door(d, fr, door, 0, t.dress);
		Groundwork.approach(d, fr.outX(door, 1), 0, fr.outZ(door, 1));
		t.floor(d, -1, t.base.full());
		t.floor(d, yb, t.dress.full());
		t.stair(d, 0, yb + 1, Stone.dressing(d), t.dress.full(), 1);
		t.hollow(d, 0, yT - 1);
		t.bells(d, yT - 1, d.mat(Role.BEAM).log(Direction.Axis.X), Direction.Axis.X);
		d.poi(PoiType.VIEWPOINT, t.cx, yb + 1, t.cz - 1, Dirs.FRONT);
		return peak;
	}

	// ------------------------------------------------------------------------------------------------ pinnacles and flyers

	private static void buttressTops(Draft d, Plan p, Stone dress) {
		int yb = buttressTop(p);
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z : p.piers) {
				if (z <= p.zT1 || p.inCrossing(z)) {
					continue;
				}
				for (int side = 0; side < 2; side++) {
					int bx = side == 0 ? p.bL : p.bR;
					Direction toward = side == 0 ? Dirs.RIGHT : Dirs.LEFT;
					if (p.manner != Manner.GOTHIC) {
						d.set(bx, yb + 1, z, dress.stairsOrSlab(toward, Half.BOTTOM));
						continue;
					}
					Details.pinnacle(d, bx, yb + 1, z, 2, dress, false);
					// the flyer: from over the buttress up to the clerestory wall, an arch of upside-down stairs under it
					int x0 = side == 0 ? p.aL : p.aR;
					int x1 = side == 0 ? p.nL - 1 : p.nR + 1;
					int n = Math.abs(x1 - x0);
					int hLo = yb + 2;
					int hHi = Math.max(hLo, p.yN - 3);
					int last = hLo;
					for (int k = 0; k <= n; k++) {
						int x = x0 + (side == 0 ? k : -k);
						int y = hLo + (int) Math.round((hHi - hLo) * (k / (double) Math.max(1, n)));
						// a coping of stairs rising to the wall, an arch of upside-down stairs under it; full blocks only
						// where the flyer climbs more than a block between cells
						for (int yy = last + 1; yy < y; yy++) {
							d.set(x, yy, z, dress.full());
						}
						d.set(x, y, z, dress.stairsOrSlab(toward, Half.BOTTOM));
						if (d.isEmpty(x, y - 1, z) && k > 0) {
							d.set(x, Math.min(y, last + 1) - 1, z, dress.stairsOrSlab(toward.getOpposite(), Half.TOP));
						}
						last = y;
					}
				}
			}
			for (int[] c : apseButtresses(p)) {
				int top = Math.min(p.yN - 2, yb + 4);
				if (p.manner == Manner.GOTHIC) {
					Details.pinnacle(d, c[0], top + 1, c[1], 2, dress, false);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ openings

	private static void openings(Draft d, Plan p, Stone body, Stone dress) {
		Paint glass = d.mat(Role.GLASS).pane();
		Paint stained = stained(d);
		boolean gothic = p.manner == Manner.GOTHIC;
		try (var s = d.stage(StageKind.OPENINGS)) {
			// arcades between the piers (nave and choir)
			for (int k = 0; k + 1 < p.piers.size(); k++) {
				int z0 = p.piers.get(k);
				int z1 = p.piers.get(k + 1);
				if (z1 - z0 != p.bay || p.inCrossing(z0) && p.inCrossing(z1)) {
					continue;
				}
				for (int side = 0; side < 2; side++) {
					int x = side == 0 ? p.nL : p.nR;
					Side wall = new Side(side == 0 ? Dirs.LEFT : Dirs.RIGHT, x, side == 0 ? z1 : z0, p.dp);
					int i0 = wall.index(side == 0 ? z1 - 1 : z0 + 1);
					if (z0 >= p.zT1 || p.towers != Towers.TWIN) {
						Details.arch(d, wall, i0, p.bay - 1, 0, p.yA - 1, dress, null);
					}
				}
				// aisle windows and clerestory lights in the bay
				for (int side = 0; side < 2; side++) {
					int ax = side == 0 ? p.aL : p.aR;
					int nx = side == 0 ? p.nL : p.nR;
					Side aisle = new Side(side == 0 ? Dirs.LEFT : Dirs.RIGHT, ax, side == 0 ? z1 : z0, p.dp);
					Side clere = new Side(side == 0 ? Dirs.LEFT : Dirs.RIGHT, nx, side == 0 ? z1 : z0, p.dp);
					int i0 = aisle.index(side == 0 ? z1 - 1 : z0 + 1);
					if (z0 > p.zT1 || p.towers != Towers.TWIN) {
						window(d, aisle, i0, p.bay - 1, 2, Math.max(2, p.yA - 3), dress, gothic && k % 2 == 1 ? stained : glass);
					}
					int cy0 = p.yA + p.a + 3;
					int ch = Math.min(4, p.yN - 1 - cy0);
					if (ch >= 2) {
						window(d, clere, clere.index(side == 0 ? z1 - 1 : z0 + 1), p.bay - 1, cy0, ch, dress, glass);
					}
				}
			}
			// the west portal: a deep doorway with jamb shafts, a tympanum and a hood
			westFront(d, p, dress, stained);
			// the transept ends: a rose over a pair of lancets
			int zc = (p.zX0 + p.zX1) / 2;
			for (int side = 0; side < 2; side++) {
				int x = side == 0 ? p.bL : p.bR;
				Side end = new Side(side == 0 ? Dirs.LEFT : Dirs.RIGHT, x, side == 0 ? p.zX1 : p.zX0, p.zX1 - p.zX0 + 1);
				int ic = end.index(zc);
				int rr = Math.min(3, (p.n - 1) / 2);
				int yr = p.yN - rr - 2;
				if (yr - rr > 4) {
					rose(d, end, ic, yr, rr, dress, stained);
					Details.lights(d, end, ic - 1, 2, 2, Math.min(4, yr - rr - 3), dress, glass);
				} else {
					Details.lights(d, end, ic - 1, 2, 2, Math.min(5, p.yN - 4), dress, stained);
				}
			}
			// the apse: tall lancets between its buttresses
			for (int[] c : apseRing(p)) {
				if (c[1] <= p.zA || c[0] == p.xc - p.apseR || c[0] == p.xc + p.apseR) {
					continue;
				}
				boolean bt = false;
				for (int[] b : apseButtresses(p)) {
					if (Math.abs(b[0] - c[0]) + Math.abs(b[1] - c[1]) <= 1) {
						bt = true;
					}
				}
				if (bt || (c[0] + c[1] & 1) != 0) {
					continue;
				}
				for (int y = 2; y < Math.min(p.yN - 2, 8); y++) {
					d.set(c[0], y, c[1], stained);
				}
				d.set(c[0], Math.min(p.yN - 2, 8), c[1], dress.full());
			}
		}
	}

	/** A window in a bay: one wide with a pointed head, or a pair of lights under one arch. */
	private static void window(Draft d, Side s, int i0, int width, int y, int h, Stone dress, Paint fill) {
		if (width >= 3) {
			Details.lights(d, s, i0, 2, y, h, dress, fill);
		} else if (width == 2) {
			Details.arch(d, s, i0, 2, y, h, dress, fill);
		} else {
			Details.arch(d, s, i0, 1, y, h, dress, fill);
		}
	}

	/** Stained glass in the style's cloth colours, mixed in patches. */
	private static Paint stained(Draft d) {
		Mat cloth = d.mat(Role.CLOTH);
		var entries = cloth.palette().entries();
		Block[] panes = new Block[entries.size()];
		for (int i = 0; i < panes.length; i++) {
			Block b = entries.get(i).material().block(Form.PANE);
			panes[i] = b != null ? b : Blocks.GLASS_PANE;
		}
		long seed = d.seed();
		return (x, y, z) -> panes[(int) (Noise.unit(seed ^ 0x57A1, x / 2, y / 2, z / 2) * panes.length)].defaultBlockState();
	}

	/** The west front: three portals (the main one deep, under a hood), the rose window, statues' niches on the gable. */
	private static void westFront(Draft d, Plan p, Stone dress, Paint stained) {
		Side front = new Side(Dirs.FRONT, p.aL, p.zF, p.aR - p.aL + 1);
		int ic = front.index(p.xc);
		// the main portal: a recess three wide and five high, the door at its back, a tympanum over the door
		int ph = Math.min(5, p.yA);
		for (int c = -1; c <= 1; c++) {
			for (int y = 0; y < ph; y++) {
				d.set(front.x(ic + c), y, p.zF, Blocks.AIR.defaultBlockState());
			}
		}
		Side inner = front.shifted(-1);
		Openings.door(d, inner, p.xc, 0, 1, Openings.DoorOpts.BACK);
		Block accent = d.mat(Role.ACCENT).main(Form.FULL);
		for (int c = -1; c <= 1; c++) {
			int x = inner.x(ic + c);
			int z = inner.z(ic + c);
			for (int y = c == 0 ? 2 : 0; y < ph; y++) {
				d.set(x, y, z, c == 0 && accent != null ? Paint.of(accent.defaultBlockState()) : dress.wallOrFull());
			}
		}
		// the head of the recess: shoulders, voussoirs, a gabled hood over it
		d.set(front.x(ic - 1), ph - 1, p.zF, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
		d.set(front.x(ic + 1), ph - 1, p.zF, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
		for (int c = -2; c <= 2; c++) {
			d.set(front.x(ic + c), ph, p.zF, dress.full());
		}
		if (p.zF >= 1) {
			for (int c = -2; c <= 2; c++) {
				int hy = ph + 1 + (2 - Math.abs(c));
				int x = front.x(ic + c);
				if (hy < p.yN) {
					d.set(x, hy, p.zF - 1, c == 0 ? dress.full() : dress.stairsOrSlab(c < 0 ? Dirs.RIGHT : Dirs.LEFT, Half.BOTTOM));
					for (int y = ph + 1; y < hy; y++) {
						d.set(x, y, p.zF - 1, dress.full());
					}
				}
			}
		}
		d.poi(PoiType.DOOR, p.xc, 0, p.zF, Dirs.BACK);
		// a gallery of little arches standing proud of the front over the portal
		int gy = ph + 4;
		if (p.zF >= 1 && gy + 2 < p.yN) {
			for (int x = p.nL + 1; x <= p.nR - 1; x++) {
				boolean post = (x - p.nL) % 2 == 1;
				if (post) {
					d.set(x, gy, p.zF - 1, dress.wallOrFull());
					d.set(x, gy + 1, p.zF - 1, dress.wallOrFull());
				}
				d.set(x, gy + 2, p.zF - 1, dress.stairsOrSlab(Dirs.BACK, Half.TOP));
				d.set(x, gy - 1, p.zF - 1, dress.stairsOrSlab(Dirs.BACK, Half.TOP));
			}
		}
		Openings.wallLantern(d, front, ic - 2, 1);
		Openings.wallLantern(d, front, ic + 2, 1);
		// the rose window over the portal
		int rr = Math.min(4, (p.n - 1) / 2);
		int yr = Math.min(p.yN - rr - 1, ph + 3 + rr + 1);
		if (yr - rr > ph + 2) {
			rose(d, front, ic, yr, rr, dress, stained);
		}
		// turrets flanking the nave's front where no towers stand, pinnacles at the gable's feet
		if (p.towers != Towers.TWIN && p.manner == Manner.GOTHIC && p.zF >= 1) {
			for (int x : new int[]{p.nL, p.nR}) {
				try (var st = d.stage(StageKind.FRAME)) {
					for (int y = 0; y <= p.yN + 2; y++) {
						d.set(x, y, p.zF - 1, dress.full());
					}
				}
				d.inStage(StageKind.ROOF, () -> Details.pinnacle(d, x, p.yN + 3, p.zF - 1, 3, dress, true));
			}
		}
		// side doors where there are no towers: lancets over the aisles
		if (p.towers != Towers.TWIN) {
			for (int side = 0; side < 2; side++) {
				int x = side == 0 ? (p.aL + p.nL) / 2 : (p.nR + p.aR) / 2;
				if (p.towers == Towers.CAMPANILE && towerAt(p, d, x)) {
					continue;
				}
				Details.arch(d, front, front.index(x), 1, 2, Math.min(3, p.yA - 3), dress, d.mat(Role.GLASS).pane());
			}
		}
	}

	private static boolean towerAt(Plan p, Draft d, int x) {
		return d.get(x, p.yA + 2, p.zF) != null && !d.isEmpty(x, p.yA + 2, p.zF);
	}

	/**
	 * A rose window of radius rr in a wall at cell ic, centred at height yc: a ring of tracery, spokes to a dressed eye,
	 * the lights between them in stained glass.
	 */
	static void rose(Draft d, Side s, int ic, int yc, int rr, Stone dress, Paint stained) {
		for (int dy = -rr; dy <= rr; dy++) {
			for (int di = -rr; di <= rr; di++) {
				double r = Math.hypot(di, dy);
				if (r > rr + 0.45) {
					continue;
				}
				int i = ic + di;
				if (i < 0 || i > s.last()) {
					continue;
				}
				boolean ring = r > rr - 0.6;
				boolean spoke = di == 0 || dy == 0 || rr >= 3 && Math.abs(di) == Math.abs(dy);
				boolean eye = r < 0.5;
				d.set(s.x(i), yc + dy, s.z(i), ring || eye || spoke && rr >= 2 && r > 1.2 ? dress.full() : stained);
			}
		}
		// a hood mould over the rose
		for (int di = -rr; di <= rr; di++) {
			int i = ic + di;
			if (i < 0 || i > s.last()) {
				continue;
			}
			int hy = yc + (int) Math.round(Math.sqrt(Math.max(0, (rr + 1) * (rr + 1) - di * di)));
			int ox = s.outX(i, 1);
			int oz = s.outZ(i, 1);
			if (d.allowed(ox, hy, oz) && d.isEmpty(ox, hy, oz)) {
				d.set(ox, hy, oz, dress.stairsOrSlab(s.out().getOpposite(), Half.TOP));
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ interior

	private static void interior(Draft d, Plan p, Stone dress, Roofs.Roof roof) {
		int zAlt = p.zA;
		// the altar at the head of the choir: a dressed block between stair ends, candles, and the steps of the sanctuary
		try (var s = d.stage(StageKind.INTERIOR)) {
			Block accent = d.mat(Role.ACCENT).main(Form.FULL);
			d.set(p.xc, 0, zAlt, accent != null ? accent.defaultBlockState() : dress.full().at(p.xc, 0, zAlt));
			d.set(p.xc - 1, 0, zAlt, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
			d.set(p.xc + 1, 0, zAlt, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
			d.set(p.xc, 1, zAlt, Details.candles(3, true));
			if (p.n >= 7) {
				d.set(p.xc - 2, 0, zAlt, Blocks.GOLD_BLOCK.defaultBlockState());
				d.set(p.xc - 2, 1, zAlt, Details.candles(2, true));
				d.set(p.xc + 2, 0, zAlt, Blocks.GOLD_BLOCK.defaultBlockState());
				d.set(p.xc + 2, 1, zAlt, Details.candles(2, true));
			}
		}
		d.poi(PoiType.ALTAR, p.xc, 0, zAlt - 1, Dirs.BACK);
		// the priest's lectern by the crossing, facing the nave
		Furniture.workstation(d, JobType.PRIEST, p.nR - 1, 0, p.zX0 + 1, Dirs.LEFT);
		d.poi(PoiType.LECTERN, p.nR - 2, 0, p.zX0 + 1, Dirs.RIGHT);
		// pews either side of the central aisle, facing the altar
		int pz0 = p.zF + 3;
		int pz1 = p.zX0 - 2;
		for (int z = pz0; z <= pz1; z += 2) {
			for (int x = p.nL + 1; x <= p.nR - 1; x++) {
				if (x == p.xc || Math.abs(x - p.xc) > (p.n - 1) / 2) {
					continue;
				}
				if (p.n >= 7 && (x == p.nL + 1 || x == p.nR - 1)) {
					continue;
				}
				Furniture.chair(d, x, 0, z, Dirs.FRONT);
			}
		}
		// choir stalls facing each other
		for (int z = p.zX1 + 1; z < p.zA - 1; z++) {
			Furniture.chair(d, p.nL + 1, 0, z, Dirs.LEFT);
			Furniture.chair(d, p.nR - 1, 0, z, Dirs.RIGHT);
		}
		// banners on the piers and chandeliers down the nave
		int k = 0;
		for (int z : p.piers) {
			if (z <= p.zT1 || p.inCrossing(z) || z >= p.zA) {
				continue;
			}
			Details.wallBanner(d, p.nL + 1, p.yA + 1, z, Dirs.RIGHT, k);
			Details.wallBanner(d, p.nR - 1, p.yA + 1, z, Dirs.LEFT, k + 1);
			k++;
		}
		light(d, p, roof);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int x : new int[]{(p.aL + p.nL) / 2, (p.nR + p.aR) / 2}) {
				d.set(x, 0, p.zA - 1, dress.full());
				d.set(x, 1, p.zA - 1, Details.candles(2, true));
			}
			int fx = p.xc + 2 <= p.nR - 1 ? p.xc + 2 : p.xc - 2;
			d.set(fx, 0, p.zF + 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		for (int x : new int[]{(p.aL + p.nL) / 2, (p.nR + p.aR) / 2}) {
			d.poi(PoiType.ALTAR, x, 0, p.zA - 2, Dirs.BACK);
		}
		d.poi(PoiType.MEETING_POINT, p.xc, 0, (p.zF + p.zX0) / 2, Dirs.BACK);
		d.poi(PoiType.SEAT, p.xc - 1, 0, pz0, Dirs.BACK);
	}

	/**
	 * Light by design: a wheel of lanterns on a long chain from the roof's ridge in every bay of the nave and the choir
	 * and over the crossing, a lantern on a bracket on every pier facing into the nave, and a lantern hung from the
	 * lean-to in every bay of the aisles.
	 */
	private static void light(Draft d, Plan p, Roofs.Roof roof) {
		int hub = Math.min(6, Math.max(4, p.yA - 1));
		List<Integer> bounds = new ArrayList<>();
		bounds.add(p.zF);
		for (int z : p.piers) {
			if (z > p.zF + 1 && !p.inCrossing(z)) {
				bounds.add(z);
			}
		}
		bounds.add(p.zX0);
		bounds.add(p.zX1);
		bounds.add(p.zA);
		bounds.sort(Integer::compare);
		List<Integer> bays = new ArrayList<>();
		for (int k = 0; k + 1 < bounds.size(); k++) {
			int z0 = bounds.get(k);
			int z1 = bounds.get(k + 1);
			if (z0 == p.zX0 && z1 == p.zX1 || z1 - z0 < 3) {
				continue;
			}
			bays.add((z0 + z1) / 2);
		}
		bays.add((p.zX0 + p.zX1) / 2);
		for (int z : bays) {
			int ceil = above(d, p, p.xc, hub + 2, z);
			if (ceil > hub + 2) {
				Lighting.wheel(d, p.xc, z, ceil, hub);
			}
		}
		// a bracket lantern on the nave face of every pier, over the pews' heads
		for (int z : p.piers) {
			if (z <= p.zF + 1 || p.inCrossing(z) || z >= p.zA) {
				continue;
			}
			for (int side = 0; side < 2; side++) {
				int x = side == 0 ? p.nL + 1 : p.nR - 1;
				int wx = side == 0 ? p.nL : p.nR;
				if (!d.isEmpty(wx, 3, z) && d.isEmpty(x, 3, z) && d.isEmpty(x, 2, z)) {
					Lighting.sconce(d, x, 3, z, side == 0 ? Dirs.LEFT : Dirs.RIGHT);
				}
			}
		}
		// the aisles: a lantern under the lean-to in every bay
		for (int side = 0; side < 2; side++) {
			Roofs.Roof lean = p.aisleRoofs[side];
			int x = side == 0 ? (p.aL + p.nL) / 2 : (p.nR + p.aR) / 2;
			for (int z : bays) {
				if (p.inCrossing(z) || z <= p.zT1 && p.towers == Towers.TWIN || z >= p.zA || lean == null || !lean.covers(x, z)) {
					continue;
				}
				int ceil = above(d, p, x, 3, z);
				int lantern = 3;
				if (ceil > lantern) {
					Lighting.chandelier(d, x, z, ceil, lantern);
				}
			}
		}
	}

	/** The first block drawn over (x, from, z): what a chain can hang from, or -1. */
	private static int above(Draft d, Plan p, int x, int from, int z) {
		for (int y = from; y <= p.top; y++) {
			if (!d.isEmpty(x, y, z)) {
				return d.isSolid(x, y, z) ? y : -1;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------------------------------------ cloister

	private static void cloister(Draft d, Plan p, Stone body, Stone dress) {
		int x0 = Math.min(p.clX0, p.clX1);
		int x1 = Math.max(p.clX0, p.clX1);
		int z0 = p.clZ0;
		int z1 = p.clZ1;
		boolean right = p.cloisterRight;
		int outer = right ? x1 : x0;          // the outer wall's column
		int church = right ? x0 : x1;         // the buttress column on the church side
		Mat roof = d.mat(Role.ROOF);
		int h = 3;
		// low outer walls on three sides, the church's aisle wall on the fourth
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = z0; z <= z1; z++) {
				for (int y = 0; y <= h; y++) {
					d.set(outer, y, z, y == h ? dress.full() : body.full());
				}
			}
			for (int x = x0; x <= x1; x++) {
				if (x == church) {
					continue;
				}
				for (int y = 0; y <= h; y++) {
					d.set(x, y, z0, y == h ? dress.full() : body.full());
					d.set(x, y, z1, y == h ? dress.full() : body.full());
				}
			}
		}
		// the arcade round the garth, two in from the walls: columns and arches, a lean-to roof over the walk
		int gx0 = x0 + 2 + (right ? 0 : 0);
		int gx1 = x1 - 2;
		int gz0 = z0 + 2;
		int gz1 = z1 - 2;
		if (right) {
			gx0 = x0 + 2;
		} else {
			gx1 = x1 - 2;
		}
		try (var s = d.stage(StageKind.FRAME)) {
			for (int[] c : Draft.ring(gx0, gz0, gx1, gz1)) {
				boolean column = (c[0] + c[1] & 1) == 0 || Crowns.isCorner(c, gx0, gz0, gx1, gz1);
				if (column) {
					for (int y = 0; y < h; y++) {
						d.set(c[0], y, c[1], dress.wallOrFull());
					}
				}
				d.set(c[0], h - 1, c[1], column ? dress.full() : dress.stairsOrSlab(Dirs.FRONT, Half.TOP));
				d.set(c[0], h, c[1], dress.full());
			}
		}
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					boolean walk = !(x > gx0 && x < gx1 && z > gz0 && z < gz1) && !(x == church && !d.isEmpty(x, 0, z) && !d.isAbsent(x, 0, z));
					if (!walk || x == church && right == false && x == x1 || x == church) {
						continue;
					}
					int dist = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
					if (dist <= 2) {
						d.set(x, h + 1, z, dist == 2 ? roof.slab(SlabType.BOTTOM) : roof.full());
					}
				}
			}
		}
		// the garth: grass, crossing paths, a well in the middle
		int cx = (gx0 + gx1) / 2;
		int cz = (gz0 + gz1) / 2;
		List<int[]> paths = new ArrayList<>();
		paths.addAll(Garden.line(gx0 + 1, cz, gx1 - 1, cz));
		paths.addAll(Garden.line(cx, gz0 + 1, cx, gz1 - 1));
		Garden.path(d, paths, 0);
		List<int[]> beds = new ArrayList<>();
		for (int z = gz0 + 1; z < gz1; z++) {
			for (int x = gx0 + 1; x < gx1; x++) {
				if (x != cx && z != cz) {
					beds.add(new int[]{x, z});
				}
			}
		}
		Garden.flowerBed(d, beds, 0, 0.35);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(cx, 0, cz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
		d.poi(PoiType.MEETING_POINT, cx, 0, cz - 1, Dirs.BACK);
		// a door from the aisle into the walk, in the middle of a bay
		int dz = (z0 + z1) / 2;
		for (int z : p.piers) {
			if (z == dz) {
				dz++;
			}
		}
		int ax = right ? p.aR : p.aL;
		Side aisle = new Side(right ? Dirs.RIGHT : Dirs.LEFT, ax, right ? p.zF : p.dp - 1, p.dp);
		try (var s = d.stage(StageKind.OPENINGS)) {
			Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
			d.door(ax, 0, dz, door, right ? Dirs.RIGHT : Dirs.LEFT, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT);
			int ox = right ? ax + 1 : ax - 1;
			for (int y = 0; y < 3; y++) {
				if (d.isSolid(ox, y, dz) && y < 2) {
					d.set(ox, y, dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		d.poi(PoiType.SEAT, cx + 1, 0, cz + 1, Dirs.FRONT);
	}

	// ------------------------------------------------------------------------------------------------ exterior

	private static void exterior(Draft d, Plan p, Stone dress) {
		// the way from the portal to the street, paving across the front
		Groundwork.approach(d, p.xc, 0, p.zF);
		Mat paving = d.mat(Role.PAVING);
		Grounds.pave(d, p.aL, 0, p.aR, p.zF - 1, 0, paving.full());
		// lamp posts either side of the steps, a churchyard of headstones and flowers in the margins
		for (int x : new int[]{p.xc - 3, p.xc + 3}) {
			if (p.zF >= 1 && Grounds.standable(d, x, 0, p.zF - 1)) {
				Garden.lampPost(d, x, 0, p.zF - 1, 2, null);
			}
		}
		for (int z = p.zT1 + 2; z < p.dp - 1; z += 3) {
			for (int x = 0; x < p.w; x++) {
				boolean margin = x < p.bL || x > p.bR;
				if (!margin || p.cloister && x >= Math.min(p.clX0, p.clX1) && x <= Math.max(p.clX0, p.clX1) && z <= p.clZ1) {
					continue;
				}
				if ((x + z) % 2 != 0 || !Grounds.standable(d, x, 0, z) || !Grounds.standable(d, x, 0, z + 1)) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(x, 0, z, dress.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
				}
				Garden.flower(d, x, 0, z + 1);
			}
		}
	}
}
