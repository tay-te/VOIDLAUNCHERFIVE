package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.api.TerrainProbe;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Rooms;
import dev.voidmc.hearth.build.kit.Stairs;
import dev.voidmc.hearth.build.kit.deep.DeepManner;
import dev.voidmc.hearth.build.kit.deep.DeepSite;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
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
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:deep/dwelling}: a miner's house of the deep town, cut into the realm's wall or standing on its floor
 * (LIVING_WORLD.md §8.2), built the way the Bohemian mining towns built theirs above: a ground storey of rough-cut
 * stone (the realm's rubble, weathered low), and over it, on stone piers and arches that make a covered arcade along the
 * street (the <i>loubí</i> of Telč and Kutná Hora), a jettied upper storey — timber-framed with pale tuff between the
 * posts in the timber traditions, dressed stone in the south's. A cornice and a parapet close a flat roof terrace at the
 * top of the plot's height (STACKABLE: the next dwelling up the wall stands on it, set back half its depth). In front
 * runs the house's stair, a flight of the realm's stone carried on its own soffit (the medieval <i>profferlo</i>): it
 * rises past the arcade to the upper floor's door and on to the roof terrace, which is how folk reach a dwelling stacked
 * on it (a ladder up the front where the frontage is too short for the whole rise). The street door has an arched head,
 * a fanlight and a keystone of the realm's glow; windows are framed with sills; glow lichen grows in the damp corners
 * and glow berries hang from the cornice; a chimney's smoking pot stands on the terrace. Lower houses keep their piers
 * to the cornice and wear a tiled pent roof or a timber oriel instead. Where the ground rises behind (the rubble at the
 * wall's foot, the wall itself) the back is cut into it. Inside, one or two storeys: the hearth and the table below,
 * beds above (all in the one room of a low house), lit by design.
 *
 * <p>Params:
 * <ul>
 *   <li>{@code stacked} (boolean): raised on the roof of the dwelling below ({@code stacked_on}, {@code level}): the
 *   front rows stand on that roof's terrace and nothing is founded down through it</li>
 *   <li>{@code party_left}, {@code party_right} (boolean, the building's own sides): a neighbour in the row shares that
 *   wall (no windows, no parapet over it)</li>
 *   <li>{@code realm}, {@code ceiling_y}, {@code character}, {@code local}: the realm ({@link DeepSite}); the dwelling
 *   keeps one clear of the vault and dresses in the realm's glow</li>
 *   <li>{@code station}, {@code household} ({@code job}): who lives there; a miner's house has its picks on the rack</li>
 *   <li>{@code variant} (int)</li>
 * </ul>
 * The roof terrace's standing level is the plot's height minus one (its deck the course under that, a parapet course on
 * the deck's edge), so a dwelling stacked on this one (floor at {@code floorY + maxHeight}) stands one step up from it.
 */
public final class DeepDwelling implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("deep/dwelling"), ArchetypeCategory.UNDERGROUND, Set.of(), 4,
		7, 11, 7, 11, 7, 11, SettlementTier.VILLAGE, Set.of(),
		Set.of(ArchetypeInfo.SiteFlag.HILLSIDE, ArchetypeInfo.SiteFlag.STACKABLE, ArchetypeInfo.SiteFlag.ATTACHABLE), List.of(), 0, 1.0);

	/** What the upper storey of a house without an arcade shows the street. */
	private enum Upper {
		PLAIN, PENT, ORIEL
	}

	/** The front's plan, decided first so every part keeps out of the others' way. */
	private static final class Plan {
		int w;
		int dp;
		/** The ground storey's front wall, the upper storey's (one forward over an arcade), the back wall. */
		int zf;
		int zu;
		int zb;
		int deck;
		int up;
		boolean two;
		/** An arcade on piers under a jettied upper storey. */
		boolean arcade;
		boolean timber;
		int[] piers;
		/** The flight: start column, step (+1 or -1), rise. */
		int xs;
		int sx;
		int rise;
		boolean toRoof;
		/** The upper door's column (or -1). */
		int xu = -1;
		/** A ladder up the front to the roof where the flight stops at the upper floor (column, or -1). */
		int ladder = -1;
		int dc;
		int hx;
		Upper upper;
		int[] posts;
		List<int[]> groundWindows = new ArrayList<>();
		List<int[]> upperWindows = new ArrayList<>();

		/** The flight's step height at column x, or -1 where the flight does not run. */
		int stepAt(int x) {
			int i = (x - this.xs) * this.sx;
			return i >= 0 && i < this.rise ? i : -1;
		}

		boolean pier(int x) {
			for (int p : this.piers) {
				if (p == x) {
					return true;
				}
			}
			return false;
		}

		boolean post(int x) {
			for (int p : this.posts) {
				if (p == x) {
					return true;
				}
			}
			return false;
		}

		/** The column where the flight arrives on the roof (or -1). */
		int arrival() {
			return this.toRoof ? this.xs + this.sx * (this.rise - 1) : this.ladder;
		}
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public TerrainProbe terrain(PlotSpec spec, TerrainProbe probe) {
		return DeepSite.probeFor(spec, probe);
	}

	@Override
	public void draw(Draft d) {
		DeepSite ds = DeepSite.of(d);
		Glow g = Glow.of(ds.character());
		DeepManner manner = DeepManner.of(d);
		boolean stacked = d.param("stacked", false);
		Plan p = plan(d, ds, manner);
		d.body(0, p.zf, p.w - 1, p.zb);
		d.eave(p.deck);

		ground(d, p, stacked);
		shell(d, p);
		groundFront(d, p, g, manner);
		if (p.arcade) {
			arcade(d, p);
		} else {
			piers(d, p);
		}
		upperFront(d, p, manner);
		roofline(d, p);
		flight(d, p);
		sides(d, p);
		chimney(d, p);
		interior(d, p);
		life(d, p);
		if (!stacked) {
			Groundwork.clear(d, 0, 0, p.w - 1, p.dp - 1, Math.min(d.maxHeight() - 1, p.deck + 3));
		}
	}

	// ------------------------------------------------------------------------------------------------- the plan

	private static Plan plan(Draft d, DeepSite ds, DeepManner manner) {
		Plan p = new Plan();
		p.w = d.frontage();
		p.dp = d.depth();
		int h = Math.max(6, Math.min(d.maxHeight(), ds.top() + 1));
		p.deck = h - 2;
		p.zf = 2;
		p.zb = p.dp - 1;
		p.two = p.deck >= 7;
		p.up = p.deck >= 10 ? 5 : p.deck >= 8 ? 4 : 3;
		p.arcade = p.two && p.up >= 4 && d.chance(0.8);
		p.zu = p.arcade ? 1 : p.zf;
		p.timber = p.two && manner.timberUpper();
		p.piers = p.w >= 9 ? new int[]{0, p.w / 2, p.w - 1} : new int[]{0, p.w - 1};
		// the upper storey's posts: the corners and every third column or so
		int bays = Math.max(1, Math.round((p.w - 1) / 4f));
		p.posts = new int[bays + 1];
		for (int i = 0; i <= bays; i++) {
			p.posts[i] = Math.round(i * (p.w - 1) / (float) bays);
		}
		// the flight: to the roof if it fits the frontage, else to the upper floor (a ladder on up the front)
		p.sx = d.chance(0.5) ? 1 : -1;
		p.xs = p.sx > 0 ? 0 : p.w - 1;
		int full = p.deck + 1;
		if (full <= p.w) {
			p.rise = full;
			p.toRoof = true;
		} else {
			p.rise = p.two ? p.up + 1 : Math.min(p.w, p.deck);
			p.toRoof = false;
		}
		if (p.two) {
			p.xu = p.xs + p.sx * p.up;
			if (!p.toRoof) {
				p.ladder = p.xu + p.sx;
			}
		} else if (!p.toRoof) {
			p.ladder = p.xs + p.sx * (p.rise - 1);
		}
		if (p.ladder >= 0 && (p.ladder < 0 || p.ladder >= p.w)) {
			p.ladder = p.xu - p.sx;
		}
		// the street door: under the flight where it is three up or more, or past its end; clear of the landing
		List<Integer> doors = new ArrayList<>();
		for (int pass = 0; pass < 2 && doors.isEmpty(); pass++) {
			int keep = pass == 0 ? 1 : 0;
			for (int x = 1; x < p.w - 1; x++) {
				int s = p.stepAt(x);
				boolean under = s < 0 || s >= 3;
				boolean near = p.xu >= 0 && Math.abs(x - p.xu) <= keep && !p.arcade || p.ladder >= 0 && Math.abs(x - p.ladder) <= keep;
				if (under && !p.pier(x) && !near) {
					doors.add(x);
				}
			}
		}
		if (doors.isEmpty()) {
			doors.add(p.sx > 0 ? p.w - 2 : 1);
		}
		p.dc = doors.get(d.random().nextInt(doors.size()));
		// the hearth against the front wall, on a side clear of the doors and the ladder
		int[] hearths = p.dc <= p.w / 2 ? new int[]{p.w - 2, 1} : new int[]{1, p.w - 2};
		p.hx = -1;
		for (int x : hearths) {
			if (Math.abs(x - p.dc) > 1 && (x != p.xu || p.arcade) && x != p.ladder) {
				p.hx = x;
				break;
			}
		}
		for (int x = 1; x < p.w - 1 && p.hx < 0; x++) {
			if (Math.abs(x - p.dc) > 1 && x != p.xu && x != p.ladder) {
				p.hx = x;
			}
		}
		if (p.hx < 0) {
			p.hx = hearths[0];
		}
		p.upper = !p.two || p.arcade ? Upper.PLAIN : manner.oriel() && p.w >= 8 && d.chance(0.6) ? Upper.ORIEL : d.chance(0.55) ? Upper.PENT
			: Upper.PLAIN;
		// windows: the ground floor's in the bays clear of the door, its jambs and the hearth
		for (int i = 0; i + 1 < p.piers.length; i++) {
			int[] bay = {p.piers[i] + 1, p.piers[i + 1] - 1};
			addRuns(p.groundWindows, bay, x -> Math.abs(x - p.dc) > 1 && x != p.hx);
			if (!p.timber) {
				addRuns(p.upperWindows, bay, x -> (x != p.hx || p.arcade) && (p.xu < 0 || Math.abs(x - p.xu) > 1) && x != p.ladder);
			}
		}
		if (p.timber) {
			// timber fronts: a window in the middle of each wide panel (every other narrow one) between the posts, clear of
			// the door, the ladder and a chimney behind; plaster round it
			for (int i = 0; i + 1 < p.posts.length; i++) {
				int a = p.posts[i] + 1;
				int b = p.posts[i + 1] - 1;
				int k = b - a + 1;
				List<int[]> runs = new ArrayList<>();
				if (k >= 5) {
					runs.add(new int[]{a + 1, a + 1});
					runs.add(new int[]{b - 1, b - 1});
				} else if (k == 4) {
					runs.add(new int[]{a + 1, b - 1});
				} else if (k == 3) {
					runs.add(new int[]{a + 1, a + 1});
				} else if (k == 2 && i % 2 == 0) {
					runs.add(new int[]{a, b});
				}
				for (int[] r : runs) {
					boolean ok = true;
					for (int x = r[0]; x <= r[1]; x++) {
						ok &= (x != p.hx || p.arcade) && (p.xu < 0 || Math.abs(x - p.xu) > 0) && x != p.ladder;
					}
					if (ok) {
						p.upperWindows.add(r);
					}
				}
			}
		}
		return p;
	}

	/** Windows as runs [x0, x1]: free columns in a bay, a stretch of wall kept between them and the bay's ends. */
	private static void addRuns(List<int[]> out, int[] bay, java.util.function.IntPredicate free) {
		List<Integer> cols = new ArrayList<>();
		for (int x = bay[0]; x <= bay[1]; x++) {
			if (free.test(x)) {
				cols.add(x);
			}
		}
		int i = 0;
		while (i < cols.size()) {
			int a = cols.get(i);
			int b = a;
			while (i + 1 < cols.size() && cols.get(i + 1) == b + 1) {
				i++;
				b = cols.get(i);
			}
			i++;
			int len = b - a + 1;
			if (len >= 5) {
				out.add(new int[]{a + 1, a + 1});
				out.add(new int[]{b - 1, b - 1});
			} else if (len == 4) {
				out.add(new int[]{a + 1, b - 1});
			} else if (len == 3) {
				out.add(new int[]{a + 1, a + 1});
			} else {
				out.add(new int[]{a, b});
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- building

	/** The terrace levelled (or standing on the roof below), footings under the body, the forecourt paved. */
	private static void ground(Draft d, Plan p, boolean stacked) {
		if (stacked) {
			int set = Math.max(2, p.dp / 2);
			int onDeck = p.dp - 1 - set;
			if (onDeck + 1 <= p.zb) {
				Groundwork.footings(d, 0, Math.max(p.zf, onDeck + 1), p.w - 1, p.zb, -1);
			}
		} else {
			Groundwork.level(d, 0, 0, p.w - 1, p.zf - 1, 0, true);
			Groundwork.footings(d, 0, p.zf, p.w - 1, p.zb, -1);
		}
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < p.zf; z++) {
				for (int x = 0; x < p.w; x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
	}

	/**
	 * Walls storey by storey (rough stone below, a dressed string course, the upper storey's masonry on the sides and
	 * back), floors, the rooms' air, the roof deck over the whole of the upper storey.
	 */
	private static void shell(Draft d, Plan p) {
		Mat rough = rough(d);
		Mat wall = d.mat(Role.WALL);
		Mat plinth = d.mat(Role.PLINTH);
		Mat accent = Rock.dressing(d);
		Mat roof = d.mat(Role.ROOF);
		Mat floor = d.mat(Role.FLOOR);
		int band = p.two ? p.up : Math.min(p.deck - 1, 3);
		dev.voidmc.hearth.build.draft.Paint home = DeepManner.home(d, Role.WALL).orElse(wall.full());
		try (var s = d.stage(StageKind.WALLS)) {
			for (int z = Math.min(p.zf, p.zu); z <= p.zb; z++) {
				for (int x = 0; x < p.w; x++) {
					for (int y = -1; y < p.deck; y++) {
						boolean upperPart = p.two && y >= p.up;
						int front = upperPart ? p.zu : p.zf;
						if (z < front) {
							continue;
						}
						boolean edge = x == 0 || x == p.w - 1 || z == front || z == p.zb;
						if (!edge) {
							continue;
						}
						if (upperPart && z == p.zu && p.timber) {
							continue;
						}
						BlockState b;
						if (y == -1) {
							b = plinth.full().at(x, y, z);
						} else if (y == band) {
							b = accent.full().at(x, y, z);
						} else if (y < band) {
							b = rough.full().at(x, y, z);
						} else {
							b = (p.two ? home : wall.full()).at(x, y, z);
						}
						d.set(x, y, z, b);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int z = p.zf + 1; z < p.zb; z++) {
				for (int x = 1; x < p.w - 1; x++) {
					d.set(x, -1, z, floor.full());
				}
			}
			if (p.two) {
				for (int z = p.zu + 1; z < p.zb; z++) {
					for (int x = 1; x < p.w - 1; x++) {
						d.set(x, p.up, z, floor.full());
					}
				}
			}
		}
		if (p.two) {
			d.carve(1, 0, p.zf + 1, p.w - 2, p.up - 1, p.zb - 1);
			d.carve(1, p.up + 1, p.zu + 1, p.w - 2, p.deck - 1, p.zb - 1);
		} else {
			d.carve(1, 0, p.zf + 1, p.w - 2, p.deck - 1, p.zb - 1);
		}
		int deckFront = p.two ? p.zu : p.zf;
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = deckFront; z <= p.zb; z++) {
				for (int x = 0; x < p.w; x++) {
					boolean edge = x == 0 || x == p.w - 1 || z == deckFront || z == p.zb;
					d.set(x, p.deck, z, edge ? wall.full() : roof.full());
				}
			}
		}
	}

	/** The rough stone of the ground storey: the realm's rubble (the foundation role), else the wall. */
	private static Mat rough(Draft d) {
		Mat f = d.mat(Role.FOUNDATION);
		return f.has(Form.FULL) ? f : d.mat(Role.WALL);
	}

	/** The ground storey's front: the street door's arch, the windows (troughs under them where no arcade passes). */
	private static void groundFront(Draft d, Plan p, Glow g, DeepManner manner) {
		int zp = p.zf - 1;
		int dc = p.dc;
		try (var s = d.stage(StageKind.WALLS)) {
			d.air(dc, 0, p.zf);
			d.air(dc, 1, p.zf);
		}
		Rock.archedDoorHead(d, dc, 0, p.zf, Dirs.FRONT, d.chance(0.7), g);
		Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
		d.inStage(StageKind.OPENINGS, () -> d.door(dc, 0, p.zf, door, Dirs.FRONT, DoorHingeSide.LEFT));
		d.entrance(dc, 0, zp);
		d.poi(PoiType.DOOR, dc, 0, zp, Dirs.BACK);
		int wh = p.two ? Math.min(2, p.up - 2) : Math.min(2, p.deck - 3);
		boolean arched = manner.archedWindows();
		for (int[] run : p.groundWindows) {
			int width = run[1] - run[0] + 1;
			Rock.window(d, run[0], 1, p.zf, Dirs.FRONT, width, Math.max(1, wh), arched && width >= 2 && wh >= 2);
			for (int x = run[0]; x <= run[1]; x++) {
				BlockState sill = d.get(x, 0, zp);
				if (sill != null && sill.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
					d.remove(x, 0, zp);
				}
				if (!p.arcade && d.isAbsent(x, 0, zp)) {
					Rock.planter(d, x, 0, zp, g);
				}
			}
		}
	}

	/**
	 * The arcade: piers in the row before the ground storey carrying the jettied upper storey on capitals, arches of
	 * stone between them, the jetty's beam along their heads, a lantern hung in the arcade.
	 */
	private static void arcade(Draft d, Plan p) {
		int zp = p.zf - 1;
		Mat dress = Rock.dressing(d);
		Mat beam = d.mat(Role.BEAM);
		for (int x : p.piers) {
			Rock.pier(d, x, zp, -1, p.up - 1, Dirs.BACK, x != 0 && x != p.w - 1);
		}
		try (var s = d.stage(StageKind.WALLS)) {
			for (int i = 0; i + 1 < p.piers.length; i++) {
				int a = p.piers[i] + 1;
				int b = p.piers[i + 1] - 1;
				if (b - a >= 1) {
					d.set(a, p.up - 1, zp, dress.stairsOrSlab(Dirs.LEFT, Half.TOP));
					d.set(b, p.up - 1, zp, dress.stairsOrSlab(Dirs.RIGHT, Half.TOP));
				}
			}
		}
		// the jetty's beam carries the upper front: logs along the arcade's head, their ends showing at the corners
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = 0; x < p.w; x++) {
				d.set(x, p.up, zp, beam.log(Direction.Axis.X));
			}
		}
		// a lantern in the widest bay, hung from the beam
		int best = -1;
		int bw = 0;
		for (int i = 0; i + 1 < p.piers.length; i++) {
			int a = p.piers[i] + 1;
			int b = p.piers[i + 1] - 1;
			int c = (a + b) / 2;
			if (b - a + 1 > bw && Math.abs(c - p.dc) >= 1 && p.stepAt(c) < 0) {
				bw = b - a + 1;
				best = c;
			}
		}
		if (best < 0) {
			best = (p.piers[0] + p.piers[1]) / 2;
		}
		if (d.isAbsent(best, p.up - 1, zp)) {
			Lighting.chandelier(d, best, zp, p.up, p.up - 1);
		}
	}

	/** Piers to the cornice for a house without an arcade. */
	private static void piers(Draft d, Plan p) {
		int zp = p.zf - 1;
		for (int x : p.piers) {
			Rock.pier(d, x, zp, -1, p.deck - 1, Dirs.BACK, x != 0 && x != p.w - 1);
		}
	}

	/**
	 * The upper storey's front: a timber frame (corner posts, posts between panels, a head beam, pale infill, windows in
	 * the panels) or dressed stone with framed windows; the door from the flight; a pent roof or an oriel on a house
	 * with no arcade.
	 */
	private static void upperFront(Draft d, Plan p, DeepManner manner) {
		if (!p.two) {
			return;
		}
		int z = p.zu;
		int wy = p.up + 1;
		int wh = Math.min(2, p.deck - wy - 1);
		if (p.timber) {
			Mat frame = d.mat(Role.FRAME);
			Mat beam = d.mat(Role.BEAM);
			dev.voidmc.hearth.build.draft.Paint infill = DeepManner.home(d, Role.WALL).orElse(Rock.dressing(d).full());
			try (var s = d.stage(StageKind.FRAME)) {
				for (int x = 0; x < p.w; x++) {
					if (!p.arcade) {
						d.set(x, p.up, z, beam.log(Direction.Axis.X));
					}
					d.set(x, p.deck - 1, z, beam.log(Direction.Axis.X));
					for (int y = p.up + 1; y < p.deck - 1; y++) {
						d.set(x, y, z, p.post(x) ? frame.log(Direction.Axis.Y) : infill);
					}
				}
			}
		}
		boolean arched = manner.archedWindows() && !p.timber;
		for (int[] run : p.upperWindows) {
			if (p.upper == Upper.ORIEL && orielAt(p) >= 0 && run[1] >= orielAt(p) - 1 && run[0] <= orielAt(p) + 1) {
				continue;
			}
			int width = run[1] - run[0] + 1;
			Rock.window(d, run[0], wy, z, Dirs.FRONT, width, wh, arched && width >= 2);
			if (p.timber) {
				// the lintel over a timber window is the head beam's: restate the infill the stone window head took
				for (int x = run[0]; x <= run[1]; x++) {
					if (wy + wh < p.deck - 1) {
						d.set(x, wy + wh, z, DeepManner.home(d, Role.WALL).orElse(Rock.dressing(d).full()));
					}
				}
			}
		}
		// the upper door, opening onto the flight (over an arcade) or onto its landing
		if (p.xu >= 0) {
			int xu = p.xu;
			Block door = d.mat(Role.DOOR).mainOr(Form.DOOR, Blocks.SPRUCE_DOOR);
			d.air(xu, wy, z);
			d.air(xu, wy + 1, z);
			if (p.timber && wy + 2 < p.deck - 1) {
				d.set(xu, wy + 2, z, d.mat(Role.BEAM).log(Direction.Axis.X));
			}
			d.inStage(StageKind.OPENINGS, () -> d.door(xu, wy, z, door, Dirs.FRONT, DoorHingeSide.RIGHT));
		}
		if (p.upper == Upper.PENT) {
			int zp = p.zf - 1;
			Mat sheet = d.mat(Role.SHEET).has(Form.STAIRS) ? d.mat(Role.SHEET) : d.mat(Role.ROOF);
			dev.voidmc.hearth.build.draft.Paint pent = DeepManner.thatches(d) ? sheet.stairsOrSlab(Dirs.BACK, Half.BOTTOM)
				: DeepManner.home(d, Role.ROOF, Form.STAIRS, st -> st.trySetValue(BlockStateProperties.HORIZONTAL_FACING, Dirs.BACK)
					.trySetValue(BlockStateProperties.HALF, Half.BOTTOM)).orElse(sheet.stairsOrSlab(Dirs.BACK, Half.BOTTOM));
			try (var s = d.stage(StageKind.ROOF)) {
				for (int x = 1; x < p.w - 1; x++) {
					if (p.pier(x) || x == p.xu || x == p.ladder || !d.isAbsent(x, p.up, zp)) {
						continue;
					}
					d.set(x, p.up, zp, pent);
				}
			}
		} else if (p.upper == Upper.ORIEL && orielAt(p) >= 0) {
			oriel(d, p, orielAt(p));
		}
	}

	/** A timber oriel over a window bay of a house with no arcade: posts at its corners, glass between, corbels under. */
	private static void oriel(Draft d, Plan p, int c) {
		int zp = p.zf - 1;
		int x0 = c - 1;
		int x1 = c + 1;
		Mat frame = d.mat(Role.FRAME);
		Mat beam = d.mat(Role.BEAM);
		Mat boards = d.mat(Role.FLOOR);
		Mat glass = d.mat(Role.GLASS);
		int top = p.deck - 1;
		try (var s = d.stage(StageKind.FRAME)) {
			for (int x = x0; x <= x1; x++) {
				d.set(x, p.up, zp, boards.full());
				d.set(x, p.up - 1, zp, beam.stairsOrSlab(Dirs.BACK, Half.TOP));
				for (int y = p.up + 1; y < top; y++) {
					boolean post = x == x0 || x == x1;
					d.set(x, y, zp, post ? frame.log(Direction.Axis.Y) : glass.pane());
				}
				d.set(x, top, zp, beam.log(Direction.Axis.X));
			}
		}
		try (var s = d.stage(StageKind.OPENINGS)) {
			for (int y = p.up + 1; y < top; y++) {
				d.set(c, y, p.zf, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** The oriel's middle column: a bay clear of the landing, the flight's arrival and the hearth. */
	private static int orielAt(Plan p) {
		for (int k = 2; k <= p.w - 3; k++) {
			int c = p.sx > 0 ? p.w - 1 - k : k;
			if (c < 2 || c > p.w - 3) {
				continue;
			}
			boolean clear = true;
			for (int x = c - 1; x <= c + 1; x++) {
				if (p.pier(x) || p.xu >= 0 && Math.abs(x - p.xu) <= 1 || x == p.ladder || x == p.hx) {
					clear = false;
				}
			}
			if (clear) {
				return c;
			}
		}
		return -1;
	}

	/**
	 * The top: a cornice of upside-down steps standing out over the front (clear of the flight where it climbs past),
	 * a parapet round the roof terrace with dressed posts, a gap where the flight or the ladder arrives.
	 */
	private static void roofline(Draft d, Plan p) {
		int front = p.two ? p.zu : p.zf;
		int zc = front - 1;
		Mat trim = d.mat(Role.ROOF_TRIM).has(Form.STAIRS) ? d.mat(Role.ROOF_TRIM) : Rock.dressing(d);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int x = 0; x < p.w; x++) {
				int st = zc == 0 ? p.stepAt(x) : -1;
				if (st >= p.deck - 3) {
					continue;
				}
				d.set(x, p.deck, zc, trim.stairsOrSlab(Dirs.BACK, Half.TOP));
			}
		}
		Rock.parapet(d, 0, front, p.w - 1, front, p.deck + 1, p.piers.length == 3 ? p.w / 2 : 0);
		if (!d.partyLocalLeft()) {
			Rock.parapet(d, 0, front, 0, p.zb, p.deck + 1, 0);
		}
		if (!d.partyLocalRight()) {
			Rock.parapet(d, p.w - 1, front, p.w - 1, p.zb, p.deck + 1, 0);
		}
		if (d.site().maxGround(0, p.zb, p.w - 1, p.zb + 1) <= p.deck) {
			Rock.parapet(d, 1, p.zb, p.w - 2, p.zb, p.deck + 1, 0);
		}
		int a = p.arrival();
		if (a >= 0) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.air(a, p.deck + 1, front);
			}
		}
	}

	/**
	 * The house's stair up the front: a flight in the front row carried on a soffit of upside-down steps, rising to the
	 * upper door (over an arcade straight from the flight, else from a landing across) and on to the roof terrace, or
	 * a ladder up the front from the flight's head where the frontage is too short for the whole rise.
	 */
	private static void flight(Draft d, Plan p) {
		Mat stone = Rock.dressing(d);
		Direction dir = p.sx > 0 ? Dirs.RIGHT : Dirs.LEFT;
		BlockState air = Blocks.AIR.defaultBlockState();
		try (var s = d.stage(StageKind.FLOORS)) {
			for (int i = 0; i < p.rise; i++) {
				int x = p.xs + p.sx * i;
				d.set(x, i, 0, stone.stairsOrSlab(dir, Half.BOTTOM));
				for (int hh = 1; hh <= 2; hh++) {
					if (d.allowed(x, i + hh, 0)) {
						d.set(x, i + hh, 0, air);
					}
				}
				if (i >= 1) {
					d.set(x, i - 1, 0, stone.stairsOrSlab(dir.getOpposite(), Half.TOP));
				}
			}
			// a pier under the flight's middle where it stands high, the soffit resting on it
			int mid = p.xs + p.sx * (p.rise / 2 + 1);
			int midH = p.stepAt(mid);
			if (midH >= 5 && mid != p.dc) {
				for (int y = 0; y < midH - 1; y++) {
					d.set(mid, y, 0, stone.full());
				}
			}
		}
		int zp = p.zf - 1;
		if (p.xu >= 0) {
			int xu = p.xu;
			int ez;
			if (p.arcade) {
				ez = 0;
			} else {
				// the landing across to the upper floor's door
				try (var s = d.stage(StageKind.FLOORS)) {
					d.set(xu, p.up, zp, stone.full());
					d.air(xu, p.up + 1, zp);
					d.air(xu, p.up + 2, zp);
				}
				ez = zp;
			}
			d.entrance(xu, p.up + 1, ez);
			d.poi(PoiType.DOOR, xu, p.up + 1, ez, Dirs.BACK);
			// a lantern on a bracket beside the upper door
			int z = p.zu;
			for (int side : new int[]{-p.sx, p.sx}) {
				int lx = xu + side;
				int lz = z - 1;
				if (lx >= 0 && lx < p.w && d.isAbsent(lx, p.up + 2, lz) && d.isAbsent(lx, p.up + 3, lz) && p.stepAt(lx) < p.up && p.up + 3 < p.deck) {
					Lighting.doorLamp(d, lx, p.up + 2, lz, Dirs.BACK);
					break;
				}
			}
		}
		if (!p.toRoof && p.ladder >= 0) {
			int lx = p.ladder;
			int lz = (p.two ? p.zu : p.zf) - 1;
			int from = p.two ? p.up + 1 : p.rise;
			try (var s = d.stage(StageKind.FLOORS)) {
				if (!p.two && lz == zp) {
					d.set(lx, p.rise - 1, zp, stone.full());
				}
				for (int y = from; y <= p.deck; y++) {
					d.set(lx, y, lz, States.facing(Blocks.LADDER, Dirs.FRONT));
				}
			}
		}
	}

	/** Side walls open to the air get a window a storey and dressed quoins at the back corners. */
	private static void sides(Draft d, Plan p) {
		boolean[] party = {d.partyLocalLeft(), d.partyLocalRight()};
		int[] xs = {0, p.w - 1};
		Direction[] out = {Dirs.LEFT, Dirs.RIGHT};
		int zm = (p.zf + 1 + p.zb - 1) / 2;
		for (int k = 0; k < 2; k++) {
			int x = xs[k];
			if (party[k] || p.zb - p.zf < 4) {
				continue;
			}
			int ox = x + Dirs.dx(out[k]);
			if (d.site().ground(ox, zm) > 1) {
				continue;
			}
			int wh = p.two ? Math.min(2, p.up - 2) : 2;
			Rock.window(d, x, 1, zm, out[k], 1, Math.max(1, wh), false);
			if (p.two && d.site().ground(ox, zm) <= p.up) {
				Rock.window(d, x, p.up + 1, zm, out[k], 1, Math.min(2, p.deck - p.up - 2), false);
			}
		}
		Mat accent = Rock.dressing(d);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int k = 0; k < 2; k++) {
				if (party[k]) {
					continue;
				}
				for (int y = 1; y < p.deck; y += 2) {
					d.set(xs[k], y, p.zb, accent.full());
				}
			}
		}
	}

	/** The hearth inside the front wall, its stack up through every floor and the deck, a smoking pot on top. */
	private static void chimney(Draft d, Plan p) {
		int z = p.zf + 1;
		Direction into = p.hx <= p.w / 2 ? Dirs.RIGHT : Dirs.LEFT;
		Furniture.hearth(d, p.hx, 0, z, into, false);
		Mat stack = d.mat(Role.CHIMNEY);
		try (var s = d.stage(StageKind.WALLS)) {
			for (int y = 1; y <= p.deck; y++) {
				d.set(p.hx, y, z, stack.full());
			}
		}
		if (d.allowed(p.hx, p.deck + 1, z)) {
			try (var s = d.stage(StageKind.ROOF)) {
				d.set(p.hx, p.deck + 1, z, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
			}
		}
	}

	/** Rooms: the hearth room below (a table, stores), beds above; one room with everything in a low house. */
	private static void interior(Draft d, Plan p) {
		int x0 = 1;
		int x1 = p.w - 2;
		int z0 = p.zf + 1;
		int z1 = p.zb - 1;
		int beds = Math.max(2, Math.min(6, Math.round(4f * Math.min(1.8f, (p.w * p.dp) / 49f))));
		JobType trade = household(d);
		if (p.two) {
			int rise = p.up + 1;
			int[] stair = stairRun(x0, z0, x1, z1, rise, p.dc, p.hx);
			Rooms.Room ground = new Rooms.Room(x0, z0, x1, z1, 0, p.up);
			ground.keep(p.dc, z0);
			Rooms.Room upperRoom = new Rooms.Room(x0, p.zu + 1, x1, z1, p.up + 1, p.deck - p.up - 1);
			upperRoom.keep(p.xu, p.zu + 1);
			if (stair != null) {
				Direction dir = Dirs.of(stair[2], stair[3]);
				Stairs.straight(d, stair[0], 0, stair[1], dir, rise, 1);
				ground.keep(stair[0] - stair[2], stair[1] - stair[3]);
				ground.keepArea(stair[0], stair[1], stair[0] + stair[2] * (rise - 1), stair[1] + stair[3] * (rise - 1));
				upperRoom.keep(stair[0] + stair[2] * rise, stair[1] + stair[3] * rise);
				railing(d, p, stair, rise, x0, p.zu + 1, x1, z1);
			}
			ground.block(p.hx, z0);
			upperRoom.block(p.hx, z0);
			Rooms.furnish(d, ground, Rooms.Type.KITCHEN, 0, JobType.NONE);
			tools(d, ground, trade);
			Rooms.furnish(d, upperRoom, Rooms.Type.BEDROOM, beds, JobType.NONE);
			Lighting.room(d, ground);
			Lighting.room(d, upperRoom);
		} else {
			Rooms.Room room = new Rooms.Room(x0, z0, x1, z1, 0, p.deck);
			room.keep(p.dc, z0);
			room.block(p.hx, z0);
			Rooms.furnish(d, room, Rooms.Type.BEDROOM, beds, JobType.NONE);
			tools(d, room, trade);
			Lighting.room(d, room);
		}
	}

	/** A rail along the stairwell's open side upstairs. */
	private static void railing(Draft d, Plan p, int[] stair, int rise, int x0, int z0, int x1, int z1) {
		Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE);
		Direction dir = Dirs.of(stair[2], stair[3]);
		Direction across = Dirs.right(dir);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < rise - 1; i++) {
				int sx = stair[0] + stair[2] * i;
				int sz = stair[1] + stair[3] * i;
				if (!d.isEmpty(sx, p.up, sz)) {
					continue;
				}
				for (int sgn : new int[]{1, -1}) {
					int rx = sx + Dirs.dx(across) * sgn;
					int rz = sz + Dirs.dz(across) * sgn;
					if (rx >= x0 && rx <= x1 && rz >= z0 && rz <= z1 && d.isSolid(rx, p.up, rz) && d.isEmpty(rx, p.up + 1, rz)) {
						d.set(rx, p.up + 1, rz, rail.fence());
					}
				}
			}
		}
	}

	/**
	 * A straight run for the stair between the floors: {x, z, dx, dz} of its first step (it climbs {@code rise} cells
	 * along (dx, dz) and arrives at the cell beyond), or null if none fits. Along the back wall first, then a side wall.
	 */
	private static int[] stairRun(int x0, int z0, int x1, int z1, int rise, int dc, int hx) {
		int[][] tries = {{x0, z1, 1, 0}, {x1, z1, -1, 0}, {x0, z0 + 1, 0, 1}, {x1, z0 + 1, 0, 1}};
		for (int[] t : tries) {
			boolean ok = true;
			for (int i = -1; i <= rise && ok; i++) {
				int x = t[0] + t[2] * i;
				int z = t[1] + t[3] * i;
				if (x < x0 || x > x1 || z < z0 || z > z1) {
					ok = false;
				} else if (z == z0 && (x == dc || x == hx)) {
					ok = false;
				}
			}
			if (ok) {
				return t;
			}
		}
		return null;
	}

	/** The household's trade: miners keep picks and a lantern on a rack. */
	private static JobType household(Draft d) {
		String job = d.params().getCompoundOrEmpty("household").getStringOr("job", "miner");
		for (JobType j : JobType.values()) {
			if (j.getSerializedName().equals(job)) {
				return j;
			}
		}
		return JobType.MINER;
	}

	/** A rack of the household's tools on a free stretch of wall: picks and a shovel for a miner. */
	private static void tools(Draft d, Rooms.Room r, JobType trade) {
		Block shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		if (shelf == null || r.height < 3) {
			return;
		}
		List<Object[]> items = switch (trade) {
			case MINER, QUARRIER -> List.of(new Object[]{"minecraft:iron_pickaxe", 1, 0}, new Object[]{"minecraft:stone_pickaxe", 1, 1},
				new Object[]{"minecraft:iron_shovel", 1, 2});
			case SMITH, SMELTER -> List.of(new Object[]{"minecraft:iron_ingot", 3, 0}, new Object[]{"minecraft:raw_iron", 2, 1},
				new Object[]{"minecraft:coal", 4, 2});
			case FARMER -> List.of(new Object[]{"minecraft:glow_berries", 6, 0}, new Object[]{"minecraft:brown_mushroom", 4, 1},
				new Object[]{"minecraft:iron_hoe", 1, 2});
			default -> List.of(new Object[]{"minecraft:torch", 4, 0}, new Object[]{"minecraft:bread", 2, 1});
		};
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = r.x0; x <= r.x1; x++) {
				for (Direction dir : Dirs.HORIZONTAL) {
					int wx = x + Dirs.dx(dir);
					int wz = z + Dirs.dz(dir);
					if (r.inside(wx, wz) || !d.isSolid(wx, r.y + 1, wz) || !d.isEmpty(x, r.y + 1, z) || !d.isEmpty(x, r.y, z)) {
						continue;
					}
					try (var s = d.stage(StageKind.INTERIOR)) {
						d.set(x, r.y + 1, z, States.facing(shelf, dir.getOpposite()), BlockEntities.shelf(items));
					}
					return;
				}
			}
		}
	}

	/**
	 * The house's life outside: the lamp by the street door, lanterns at the roof terrace's front corners, glow lichen
	 * in the damp corners of the rough storey, glow berries hanging from the cornice.
	 */
	private static void life(Draft d, Plan p) {
		int zp = p.zf - 1;
		boolean lamp = false;
		if (!p.arcade) {
			for (int side : new int[]{2, -2}) {
				int x = p.dc + side;
				if (x >= 0 && x < p.w && d.isAbsent(x, 2, zp) && d.isAbsent(x, 3, zp)) {
					Lighting.doorLamp(d, x, 2, zp, Dirs.BACK);
					lamp = true;
					break;
				}
			}
		}
		if (!lamp && !p.arcade && d.isEmpty(p.dc, 2, zp) && !d.isEmpty(p.dc, 3, zp)) {
			Lighting.chandelier(d, p.dc, zp, 3, 2);
		}
		int front = p.two ? p.zu : p.zf;
		int y = p.deck + 1;
		for (int x : new int[]{1, p.w - 2}) {
			int z = front + 1;
			if (x != p.hx && d.allowed(x, y, z) && d.isAbsent(x, y, z) && x != p.arrival()) {
				Lighting.lamp(d, x, y, z);
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int x = 0; x < p.w; x++) {
				for (int yy = 0; yy <= 1; yy++) {
					int z = p.arcade ? zp : zp;
					if (Noise.unit(d.seed() ^ 0x11C, x, yy, z) > 0.2 || !d.isAbsent(x, yy, z) || !d.isSolid(x, yy, p.zf)) {
						continue;
					}
					d.set(x, yy, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.NORTH, true));
				}
			}
		}
		int zc = front - 1;
		for (int x = 1; x < p.w - 1; x++) {
			if (Noise.unit(d.seed() ^ 0xBE, x, 0, zc) > 0.3 || p.stepAt(x) >= 0 && zc == 0) {
				continue;
			}
			int len = 1 + (int) (Noise.unit(d.seed() ^ 0xBF, x, 1, zc) * 2);
			boolean clear = d.get(x, p.deck, zc) != null;
			for (int k = 1; k <= len + 2 && clear; k++) {
				clear = d.isAbsent(x, p.deck - k, zc);
			}
			if (clear) {
				Glow.vine(d, x, p.deck - 1, zc, len);
			}
		}
	}
}
