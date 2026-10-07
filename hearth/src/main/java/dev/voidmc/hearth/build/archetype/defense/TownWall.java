package dev.voidmc.hearth.build.archetype.defense;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.landscape.Corridor;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * {@code hearth:defense/wall}: a stretch of the town wall along the planner's line, three blocks thick. A footing in
 * the ground and a darker plinth course; the field face crenellated, with (on straight runs of stone walls) pilasters
 * every six blocks and the face between them set back under a corbel table, so the parapet oversails like a row of
 * machicolations; a wall walk along the middle, stepping with the ground, between the crenellated parapet and a low
 * parapet on the town side with lanterns on it; stairs up from the town side cut into the inner face at the ends of the
 * stretch (a niche at the foot), and on straight runs a blind arcade of round-headed niches along the town face, a
 * lantern hung in every other one; square piers closing the walk where the stretch ends, unless it runs on into a tower
 * or another stretch. Timber cultures (bayou, tropical) build it as a rampart: two rows of logs with earth between,
 * a boarded walk and a railing.
 *
 * <p>Params (PLANNING.md §7): {@code points} (int array x,y,z,... world, the walking level along the line),
 * {@code width} (3), {@code height} (8: the field face from the ground to the merlon tops). Optional, for joining
 * stretches into one wall: {@code keep} (int array of world rectangles x0,z0,x1,z1 the wall leaves to towers and
 * gatehouses standing on it; the walk runs on into them), {@code join_start} / {@code join_end} (boolean: the
 * stretch continues into another one there, so its end is left open), {@code heart} (int array {x, z}: the town side;
 * default the right of the way from the first point to the last).
 */
public final class TownWall implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("defense/wall"), ArchetypeCategory.DEFENSE, Set.of(), 0,
		3, 9, 12, 32, 8, 14, SettlementTier.TOWN, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** The walk's standing level at each step of the line: the height above the ground, stepping a block at a time. */
	static int[] walkLevels(Draft d, Corridor c, int h) {
		int n = c.length();
		int[] high = new int[n];
		java.util.Arrays.fill(high, Integer.MIN_VALUE);
		for (Corridor.Cell cell : c.cells) {
			high[cell.index()] = Math.max(high[cell.index()], d.site().ground(cell.x(), cell.z()));
		}
		for (int i = 0; i < n; i++) {
			if (high[i] == Integer.MIN_VALUE) {
				high[i] = c.ground[i];
			}
		}
		int[] p = new int[n];
		for (int i = 0; i < n; i++) {
			int m = high[i];
			for (int k = -1; k <= 1; k++) {
				if (i + k >= 0 && i + k < n) {
					m = Math.max(m, high[i + k]);
				}
			}
			p[i] = m + h - 2;
		}
		for (int i = 1; i < n; i++) {
			p[i] = Math.max(p[i], p[i - 1] - 1);
		}
		for (int i = n - 2; i >= 0; i--) {
			p[i] = Math.max(p[i], p[i + 1] - 1);
		}
		// never above the plot (only previews and tests give a plot too low for the land)
		for (int i = 0; i < n; i++) {
			p[i] = Math.min(p[i], d.maxHeight() - 3);
		}
		return p;
	}

	/**
	 * The coping's level at each step of the line: the walk's levels taken in runs of three to five steps (a half bay to
	 * almost a bay, broken at the half bays where it can), each run level at its highest step, so on sloping ground the
	 * crest steps down the hill run by run as a mason builds it and not column by column into a saw-tooth. A run is cut
	 * short where the levels under it would spread more than {@code spread} (so the parapet never stands much above the
	 * walk); flat ground makes one level.
	 */
	public static int[] crest(int[] level, int spread) {
		int n = level.length;
		int[] out = new int[n];
		int i = 0;
		while (i < n) {
			int lo = level[i];
			int hi = level[i];
			int end = i;
			while (end + 1 < n && end - i + 1 < 5) {
				int nlo = Math.min(lo, level[end + 1]);
				int nhi = Math.max(hi, level[end + 1]);
				if (nhi - nlo > spread) {
					break;
				}
				// a run of three or more ends at a half bay when the next step changes the level
				if (end - i + 1 >= 3 && (end + 1) % 3 == 0 && level[end + 1] != level[end]) {
					break;
				}
				lo = nlo;
				hi = nhi;
				end++;
			}
			for (int k = i; k <= end; k++) {
				out[k] = hi;
			}
			i = end + 1;
		}
		return out;
	}

	@Override
	public void draw(Draft d) {
		Corridor c = new Corridor(d, 3);
		int n = c.length();
		int h = Math.clamp(d.param("height", 8), 5, 16);
		int[] p = walkLevels(d, c, h);
		// the coping steps in runs; the walk behind it climbs a step at a time
		int[] q = crest(p, 2);
		boolean timber = Manner.timberForts(d);
		boolean capStart = !c.open(true);
		boolean capEnd = !c.open(false);
		// every stretch of a town's wall wears the same stone, whatever variant its plot's seed picks
		Mat body = Manner.fortMasonry(d);
		Mat found = Manner.fort(d, Role.FOUNDATION);
		Mat accent = Manner.wood(Manner.fort(d, Role.WALL_ACCENT)) ? body : Manner.fort(d, Role.WALL_ACCENT);
		Mat walk = Manner.wood(Manner.fort(d, Role.PAVING)) && !timber ? body : Manner.fort(d, Role.PAVING);
		Mat log = Manner.fort(d, Role.TREE_LOG);
		Mat fence = Manner.fort(d, Role.FENCE);
		boolean pilasters = !timber && h >= 7 && d.param("corbels", true);
		int bay = 6;
		for (Corridor.Cell cell : c.cells) {
			int x = cell.x();
			int z = cell.z();
			int g = d.site().ground(x, z);
			int top = q[cell.index()];
			int floor = p[cell.index()];
			boolean pier = capStart && (cell.beyond() < 0 || cell.index() == 0) || capEnd && (cell.beyond() > 0 || cell.index() == n - 1);
			// footings into the ground
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = g - 2; y < g; y++) {
					d.set(x, y, z, found.full());
				}
			}
			try (var s = d.stage(StageKind.WALLS)) {
				if (timber) {
					timberColumn(d, cell, g, top, floor, pier, log, fence, walk);
					continue;
				}
				if (pier) {
					for (int y = g; y <= top + 1; y++) {
						d.set(x, y, z, y == g ? found.full() : body.full());
					}
					if (((x + z) & 1) == 0) {
						d.set(x, top + 2, z, accent.full());
					}
					continue;
				}
				if (cell.onLine()) {
					for (int y = g; y <= floor - 2; y++) {
						d.set(x, y, z, y == g ? found.full() : body.full());
					}
					d.set(x, floor - 1, z, walk.full());
					d.set(x, floor, z, Blocks.AIR.defaultBlockState());
					d.set(x, floor + 1, z, Blocks.AIR.defaultBlockState());
				} else if (cell.field()) {
					long along = Math.round(cell.t());
					boolean straight = cell.straightness() > 0.97;
					boolean pilaster = !straight || !pilasters || along % bay == 0 || along < 2 || c.along[n - 1] - cell.t() < 2;
					boolean recess = !pilaster && top - 2 >= g + 3;
					for (int y = g; y <= top; y++) {
						Paint paint = y == g ? found.full() : body.full();
						if (recess && y >= g + 2 && y <= top - 2) {
							continue;
						}
						if (recess && y == g + 1 && body.has(Form.STAIRS) && cell.inward() != null) {
							// a sloping talus at the foot of the bay
							paint = body.stairs(cell.inward(), Half.BOTTOM);
						}
						if (recess && y == top - 1 && body.has(Form.STAIRS) && cell.inward() != null) {
							// the corbel carrying the parapet out over the bay
							paint = body.stairs(cell.inward(), Half.TOP);
						}
						if (!recess && y == top - 1 && accent != body && straight) {
							paint = accent.full();
						}
						d.set(x, y, z, paint);
					}
					if ((cell.index() & 1) == 0) {
						d.set(x, top + 1, z, merlon(d, accent, body));
					}
				} else {
					// the town face: on straight runs a blind arcade, round-headed niches between the pilasters
					long along = Math.round(cell.t());
					int pos = (int) Math.floorMod(along, bay);
					boolean straight = cell.straightness() > 0.97;
					boolean niche = pilasters && straight && pos != 0 && along >= 2 && c.along[n - 1] - cell.t() >= 2 && top - 1 >= g + 5;
					for (int y = g; y <= top - 1; y++) {
						if (niche && y >= g + 1 && y <= g + 3) {
							boolean shoulder = y == g + 3 && (pos == 1 || pos == bay - 1) && body.has(Form.STAIRS);
							if (shoulder) {
								Direction toPier = pos == 1 ? cell.ahead().getOpposite() : cell.ahead();
								d.set(x, y, z, body.stairs(toPier, Half.TOP));
							} else {
								d.set(x, y, z, Blocks.AIR.defaultBlockState());
							}
							continue;
						}
						d.set(x, y, z, y == g ? found.full() : y == top - 1 && accent != body ? accent.full() : body.full());
					}
					if (niche && pos == bay / 2 && Math.floorMod(Math.floorDiv(along, bay), 2) == 0) {
						d.set(x, g + 3, z, dev.voidmc.hearth.build.draft.States.lantern(Lamps.lantern(d), true));
					}
					d.set(x, top, z, body.has(Form.WALL) ? body.wall() : body.slab(SlabType.BOTTOM));
				}
			}
		}
		// the walk steps up and down with the land
		try (var s = d.stage(StageKind.FLOORS)) {
			for (Corridor.Cell cell : c.cells) {
				int i = cell.index();
				if (!cell.onLine() || isPier(cell, capStart, capEnd, n)) {
					continue;
				}
				boolean upBehind = i > 0 && p[i - 1] == p[i] - 1;
				boolean upAhead = i < n - 1 && p[i + 1] == p[i] - 1;
				if (upBehind == upAhead) {
					continue;
				}
				Direction ahead = cell.ahead();
				Direction face = upBehind ? ahead : ahead.getOpposite();
				Mat steps = walk.has(Form.STAIRS) ? walk : body;
				if (steps.has(Form.STAIRS)) {
					d.set(cell.x(), p[i] - 1, cell.z(), steps.stairs(face, Half.BOTTOM));
				}
			}
		}
		// lanterns along the town parapet
		for (Corridor.Cell cell : c.cells) {
			if (cell.town() && !isPier(cell, capStart, capEnd, n) && Math.floorMod(Math.round(cell.t()), 9) == 4 && !timber) {
				Lamps.lanternOn(d, cell.x(), q[cell.index()] + 1, cell.z());
			}
		}
		// stairs up from the town side: at the end nearer the street, and at the start too on a long stretch
		int[] entrance = stair(d, c, p, q, true, capEnd, body, walk);
		if (n >= 30) {
			stair(d, c, p, q, false, capStart, body, walk);
		}
		if (entrance == null) {
			entrance = stair(d, c, p, q, false, capStart, body, walk);
		}
		if (entrance != null) {
			d.entrance(entrance[0], entrance[1], entrance[2]);
		} else {
			int ex = Math.clamp(c.lx[n - 1], 0, d.frontage() - 1);
			int wy = d.site().water(ex, -1);
			d.entrance(ex, Math.max(d.site().ground(ex, -1), wy == dev.voidmc.hearth.build.terrain.Site.NONE ? Integer.MIN_VALUE : wy), -1);
		}
		// a guard's post on the walk midway, looking out over the field
		int mid = n / 2;
		for (Corridor.Cell cell : c.step(mid)) {
			if (cell.onLine() && !isPier(cell, capStart, capEnd, n)) {
				Direction out = fieldward(c, mid);
				d.poi(PoiType.GUARD_POST, cell.x(), p[mid], cell.z(), out);
				d.poi(PoiType.VIEWPOINT, cell.x(), p[mid], cell.z(), out);
				break;
			}
		}
		// clear what grows on the line (only the corridor: the rest of the box is other land)
		try (var s = d.stage(StageKind.CLEAR)) {
			BlockState air = Blocks.AIR.defaultBlockState();
			for (Corridor.Cell cell : c.cells) {
				int g = d.site().ground(cell.x(), cell.z());
				int w = d.site().water(cell.x(), cell.z());
				for (int y = w == dev.voidmc.hearth.build.terrain.Site.NONE ? g : w; y <= Math.min(d.maxHeight() - 1, q[cell.index()] + 3); y++) {
					if (d.isAbsent(cell.x(), y, cell.z())) {
						d.set(cell.x(), y, cell.z(), air);
					}
				}
			}
		}
	}

	private static boolean isPier(Corridor.Cell cell, boolean capStart, boolean capEnd, int n) {
		return capStart && (cell.beyond() < 0 || cell.index() == 0) || capEnd && (cell.beyond() > 0 || cell.index() == n - 1);
	}

	/** The local direction from the line toward the field at a step. */
	static Direction fieldward(Corridor c, int i) {
		for (Corridor.Cell cell : c.step(i)) {
			if (cell.field() && cell.inward() != null) {
				return cell.inward().getOpposite();
			}
		}
		Direction a = c.ahead(i);
		return Dirs.right(a);
	}

	private static Paint merlon(Draft d, Mat accent, Mat body) {
		return d.tier() >= 3 && accent != body ? accent.full() : body.full();
	}

	/** A column of the timber rampart: log faces, earth between, a boarded walk, a railing, sharpened tops outside. */
	private static void timberColumn(Draft d, Corridor.Cell cell, int g, int top, int floor, boolean pier, Mat log, Mat fence, Mat walk) {
		int x = cell.x();
		int z = cell.z();
		Paint logs = log.log(Direction.Axis.Y).orElse(log.full());
		Paint fill = Manner.fort(d, Role.FILL).full();
		if (pier) {
			for (int y = g; y <= top + 1; y++) {
				d.set(x, y, z, logs);
			}
			d.set(x, top + 2, z, fence.has(Form.FENCE) ? fence.fence() : logs);
			return;
		}
		if (cell.onLine()) {
			for (int y = g; y <= floor - 2; y++) {
				d.set(x, y, z, fill);
			}
			d.set(x, floor - 1, z, walk.full());
			d.set(x, floor, z, Blocks.AIR.defaultBlockState());
			d.set(x, floor + 1, z, Blocks.AIR.defaultBlockState());
		} else if (cell.field()) {
			for (int y = g; y <= top; y++) {
				d.set(x, y, z, logs);
			}
			boolean tall = Noise.unit(d.seed() ^ 0x9A1, x, 0, z) < 0.5;
			d.set(x, top + 1, z, tall ? logs : fence.has(Form.FENCE) ? fence.fence() : logs);
			if (tall && fence.has(Form.FENCE) && top + 2 < d.maxHeight()) {
				d.set(x, top + 2, z, fence.fence());
			}
		} else {
			for (int y = g; y <= top - 1; y++) {
				d.set(x, y, z, logs);
			}
			d.set(x, top, z, fence.has(Form.FENCE) ? fence.fence() : logs);
		}
	}

	/** The town-side cell of a step nearest a block off the line (null if the corridor has none there). */
	private static Corridor.@Nullable Cell townCell(Corridor c, int i) {
		Corridor.Cell best = null;
		for (Corridor.Cell cell : c.step(i)) {
			if (cell.beyond() != 0 || !cell.town()) {
				continue;
			}
			if (best == null || Math.abs(cell.offset() - 1) < Math.abs(best.offset() - 1)) {
				best = cell;
			}
		}
		return best;
	}

	/**
	 * A flight of steps cut into the town face, from a niche at the ground near one end up to the walk. Returns
	 * {niche x, y, z, top x, y, z} or null when the stretch has no room for it.
	 */
	private static int @Nullable [] stair(Draft d, Corridor c, int[] p, int[] q, boolean atEnd, boolean capped, Mat body, Mat walk) {
		int n = c.length();
		int landing = atEnd ? n - 1 - (capped ? 1 : 0) : (capped ? 1 : 0);
		int step = atEnd ? -1 : 1;
		List<Corridor.Cell> ribbon = new ArrayList<>();
		for (int k = 0; k <= 26; k++) {
			int i = landing + step * k;
			if (i < 0 || i >= n) {
				break;
			}
			Corridor.Cell cell = townCell(c, i);
			if (cell == null) {
				// the line runs into a tower or out of the plot: the flight must fit before it
				break;
			}
			if (!ribbon.isEmpty()) {
				Corridor.Cell prev = ribbon.get(ribbon.size() - 1);
				int dx = cell.x() - prev.x();
				int dz = cell.z() - prev.z();
				if (dx == 0 && dz == 0) {
					continue;
				}
				if (Math.abs(dx) + Math.abs(dz) == 2 && Math.abs(dx) == 1) {
					// a diagonal: go round by the corner cell on the town side
					Corridor.Cell a = c.at(prev.x() + dx, prev.z());
					Corridor.Cell b = c.at(prev.x(), prev.z() + dz);
					Corridor.Cell link = null;
					for (Corridor.Cell o : new Corridor.Cell[]{a, b}) {
						if (o != null && o.offset() > 0 && o.beyond() == 0 && (link == null || Math.abs(o.offset() - 1) < Math.abs(link.offset() - 1))) {
							link = o;
						}
					}
					if (link == null) {
						return null;
					}
					ribbon.add(link);
				} else if (Math.abs(dx) + Math.abs(dz) != 1) {
					return null;
				}
			}
			ribbon.add(cell);
		}
		if (ribbon.size() < 4) {
			return null;
		}
		Corridor.Cell first = ribbon.get(0);
		if (d.site().isWater(first.x(), first.z()) || d.site().isWater(ribbon.get(1).x(), ribbon.get(1).z())) {
			return null;
		}
		int g0 = d.site().ground(first.x(), first.z());
		int topK = -1;
		for (int k = 1; k < ribbon.size(); k++) {
			if (g0 + k >= p[ribbon.get(k).index()]) {
				topK = k;
				break;
			}
		}
		if (topK < 2) {
			return null;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		Mat steps = walk.has(Form.STAIRS) && !Manner.wood(walk) ? walk : body;
		try (var s = d.stage(StageKind.FLOORS)) {
			// the niche at the foot: three high, paved
			d.set(first.x(), g0 - 1, first.z(), walk.full());
			for (int y = g0; y <= g0 + 2; y++) {
				d.set(first.x(), y, first.z(), air);
			}
			for (int k = 1; k <= topK; k++) {
				Corridor.Cell cell = ribbon.get(k);
				int y = g0 + k - 1;
				int g = d.site().ground(cell.x(), cell.z());
				Corridor.Cell next = k < ribbon.size() - 1 ? ribbon.get(k + 1) : null;
				Direction face = next != null ? Dirs.of(next.x() - cell.x(), next.z() - cell.z())
					: Dirs.of(cell.x() - ribbon.get(k - 1).x(), cell.z() - ribbon.get(k - 1).z());
				if (k == topK || face == null) {
					face = Dirs.of(cell.x() - ribbon.get(k - 1).x(), cell.z() - ribbon.get(k - 1).z());
				}
				for (int yy = Math.min(g, y) - 1; yy < y; yy++) {
					d.set(cell.x(), yy, cell.z(), body.full());
				}
				d.set(cell.x(), y, cell.z(), steps.has(Form.STAIRS) && face != null ? steps.stairs(face, Half.BOTTOM) : steps.full());
				// clear up through the coping (it may stand above the walk) and anything set on it
				for (int yy = y + 1; yy <= Math.max(y + 3, q[cell.index()] + 1); yy++) {
					d.set(cell.x(), yy, cell.z(), air);
				}
			}
		}
		Corridor.Cell topCell = ribbon.get(topK);
		return new int[]{first.x(), g0, first.z(), topCell.x(), g0 + topK, topCell.z()};
	}
}
