package dev.voidmc.hearth.build.archetype.infra;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.infra.Course;
import dev.voidmc.hearth.build.kit.infra.Demo;
import dev.voidmc.hearth.build.kit.infra.Lamps;
import dev.voidmc.hearth.build.kit.infra.Lay;
import dev.voidmc.hearth.build.kit.infra.LineMat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:infra/dock}: a pier from the road's edge out over the water (PLANNING.md §7). It steps down from the
 * road to a deck a block over the water and ends in a landing stage flush with it, where boats tie up (a DOCK point).
 *
 * <ul>
 *   <li><b>timber pier</b> (most cultures, and every hamlet): a plank deck on stringers and piles, the piles standing
 *   up through the deck at the edges as mooring posts, lanterns on the end posts, a fish-drying rack hung with nets near
 *   the shore, barrels and a chest of gear (STORAGE), a little jib crane over the landing stage in a town.</li>
 *   <li><b>stone quay</b> (maritime, mediterranean, desert and mesa towns): a masonry pier from the bed with a dressed
 *   coping, stone bollards, steps down into the water at the end, a street lamp.</li>
 * </ul>
 *
 * Plots never front a dock (it is not an access road), so its edges carry posts and racks freely.
 *
 * <p>Params: {@code points} (from the last land column by its road at road level, out over the water at water level),
 * {@code width} (3).
 */
public final class Dock implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("infra/dock"), ArchetypeCategory.INFRASTRUCTURE, Set.of(), 0,
		3, 22, 3, 22, 4, 8, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.WATERFRONT), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Lay lay = new Lay(d);
		int[] pts = Demo.points(d);
		int width;
		if (pts == null) {
			width = Demo.odd(Math.min(3, d.frontage()));
			int cx = d.frontage() / 2;
			// alone (tests, previews): from the land out over the water where the plot has a shore, the way the planner
			// lays it (front to back when the back is wet)
			boolean backWet = lay.wet(lay.wx(cx, d.depth() - 1), lay.wz(cx, d.depth() - 1));
			boolean frontWet = lay.wet(lay.wx(cx, 0), lay.wz(cx, 0));
			int za = backWet && !frontWet ? 0 : d.depth() - 1;
			int zb = za == 0 ? d.depth() - 1 : 0;
			int[][] r = Course.run(lay.wx(cx, za), lay.wz(cx, za), lay.wx(cx, zb), lay.wz(cx, zb));
			int end = lay.surface(r[0][r[0].length - 1], r[1][r[0].length - 1]);
			int land = Math.clamp(lay.surface(r[0][0], r[1][0]), end - r[0].length + 2, end + r[0].length - 2);
			land = Math.min(land, lay.floorY() + d.maxHeight() - 4);
			pts = new int[]{r[0][0], land, r[1][0], r[0][r[0].length - 1], end, r[1][r[0].length - 1]};
		} else {
			width = Demo.odd(d.param("width", 3));
		}
		Course c = new Course(pts, width);
		int n = c.steps();
		Culture culture = d.style().culture();
		boolean stone = d.tier() >= 2 && (culture == Culture.MARITIME || culture == Culture.MEDITERRANEAN || culture == Culture.DESERT
			|| culture == Culture.MESA);
		// the levels: down (or up) from the road a block a step to the deck a block over the water, a landing stage at the end
		int water = Site.NONE;
		for (int k = 0; k < n; k++) {
			int w = lay.water(c.dx(k), c.dz(k));
			if (w != Site.NONE) {
				water = Math.max(water, w);
			}
		}
		int road = c.dy(0);
		int pier = water != Site.NONE ? water + 1 : c.dy(n - 1);
		int[] lv = new int[n];
		for (int k = 0; k < n; k++) {
			lv[k] = road > pier ? Math.max(pier, road - k) : Math.min(pier, road + k);
		}
		boolean stage = water != Site.NONE && n >= 8 && !stone && lay.wet(c.dx(n - 1), c.dz(n - 1)) && lay.wet(c.dx(n - 2), c.dz(n - 2));
		if (stage) {
			lv[n - 1] = pier - 1;
			lv[n - 2] = pier - 1;
		}
		lay.entrance(c.dx(0), lv[0], c.dz(0));
		lay.entrance(c.dx(n - 1), lv[n - 1], c.dz(n - 1));
		LineMat planks = LineMat.of(lay, Role.STILT);
		LineMat pile = LineMat.of(lay, Role.STILT);
		LineMat beam = LineMat.of(lay, Role.BEAM);
		LineMat found = LineMat.of(lay, Role.FOUNDATION);
		LineMat plinth = LineMat.of(lay, Role.PLINTH);
		LineMat paving = LineMat.of(lay, Role.PAVING).weathered(0.2);
		LineMat fill = LineMat.of(lay, Role.FILL);
		List<Course.Col> cols = new ArrayList<>();
		for (Course.Col col : c.cols()) {
			if (lay.allowed(col.x(), lv[col.k()] - 1, col.z())) {
				cols.add(col);
			}
		}
		for (Course.Col col : cols) {
			int k = col.k();
			int l = lv[k];
			int x = col.x();
			int z = col.z();
			int g = lay.ground(x, z);
			boolean wet = lay.wet(x, z);
			boolean edge = col.ring() == 0;
			Direction t = c.cardinal(k);
			// what carries the deck
			if (!wet) {
				try (var s = d.stage(StageKind.FOUNDATION)) {
					for (int y = g - 2; y < l - 1; y++) {
						lay.set(x, y, z, edge ? found.full() : fill.full());
					}
				}
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = l; y < g; y++) {
						lay.air(x, y, z);
					}
				}
			} else if (stone) {
				try (var s = d.stage(StageKind.FOUNDATION)) {
					for (int y = g - 2; y < l - 1; y++) {
						lay.set(x, y, z, found.full());
					}
				}
			} else {
				boolean pileHere = edge && (k % 3 == 1 || k == n - 1);
				try (var s = d.stage(StageKind.FOUNDATION)) {
					if (pileHere) {
						for (int y = g - 2; y < l - 1; y++) {
							lay.set(x, y, z, pile.log(Direction.Axis.Y));
						}
					} else if (edge) {
						lay.set(x, l - 2, z, beam.log(t.getAxis()));
					} else if (k % 3 == 1) {
						lay.set(x, l - 2, z, beam.log(t.getClockWise().getAxis()));
					}
				}
			}
			// the deck, stepping down from the road
			Direction up = null;
			if (k > 0 && lv[k] > lv[k - 1]) {
				up = t;
			} else if (k + 1 < n && lv[k] > lv[k + 1]) {
				up = t.getOpposite();
			}
			LineMat deck = stone ? edge ? plinth : paving : planks;
			try (var s = d.stage(StageKind.FLOORS)) {
				if (up != null && deck.has(Form.STAIRS)) {
					lay.set(x, l - 1, z, deck.stairs(up, Half.BOTTOM));
				} else {
					lay.set(x, l - 1, z, deck.full());
				}
			}
			// headroom
			try (var s = d.stage(StageKind.CLEAR)) {
				for (int y = l; y <= Math.min(l + 3, lay.topY()); y++) {
					if (lay.absent(x, y, z) && y >= lay.surface(x, z)) {
						lay.air(x, y, z);
					}
				}
			}
		}
		details(lay, c, cols, lv, stone, stage, pier);
	}

	/** Mooring posts, lamps, the rack, barrels and gear, the crane, the steps into the water, the points folk use. */
	private static void details(Lay lay, Course c, List<Course.Col> cols, int[] lv, boolean stone, boolean landing, int pier) {
		Draft d = lay.draft();
		int n = c.steps();
		LineMat pile = LineMat.of(lay, Role.STILT);
		LineMat found = LineMat.of(lay, Role.FOUNDATION);
		LineMat fence = LineMat.of(lay, Role.FENCE);
		LineMat frame = LineMat.of(lay, Role.FRAME);
		LineMat beam = LineMat.of(lay, Role.BEAM);
		LineMat wood = LineMat.of(lay, Role.FURNITURE);
		long seed = Noise.hash(d.seed(), 0xD0C);
		int top = lay.topY();
		Course.Col endLeft = null;
		Course.Col endRight = null;
		for (Course.Col col : cols) {
			int k = col.k();
			int l = lv[k];
			if (col.ring() != 0 || k < 2) {
				continue;
			}
			boolean right = col.off() > 0;
			int endK = landing ? n - 3 : n - 1;
			if (k == endK) {
				if (right) {
					endRight = col;
				} else {
					endLeft = col;
				}
				continue;
			}
			// mooring posts along the edges: on a timber pier a pile's head left standing through the deck as a fence post,
			// every sixth step, the sides in turn, so the walk down the middle stays clear (no slalom of log stumps)
			boolean post = stone ? k % 4 == 2 : k % 6 == 1 && (k / 6 % 2 == 0) == right && lay.wet(col.x(), col.z());
			if (post && l <= top && lay.empty(col.x(), l, col.z())) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					lay.set(col.x(), l, col.z(), stone ? found.has(Form.WALL) ? found.wall() : found.full() : fence.post());
				}
			}
		}
		// lanterns at the end
		for (Course.Col col : new Course.Col[]{endLeft, endRight}) {
			if (col == null) {
				continue;
			}
			int l = lv[col.k()];
			if (stone) {
				Lamps.post(lay, col.x(), l, col.z(), null);
			} else if (l + 1 <= top) {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					lay.set(col.x(), l, col.z(), pile.log(Direction.Axis.Y));
				}
				Lamps.standing(lay, col.x(), l + 1, col.z());
			}
		}
		// near the shore: a fish rack hung with nets on one side, barrels and the gear chest on the other
		if (n >= 7 && !stone) {
			for (Course.Col col : cols) {
				int k = col.k();
				int l = lv[k];
				if (col.ring() != 0 || k < 2 || k > 4 || lv[k] != pier || l + 1 > top) {
					continue;
				}
				try (var s = d.stage(StageKind.EXTERIOR)) {
					if (col.off() < 0) {
						if (k == 2 || k == 4) {
							lay.set(col.x(), l, col.z(), fence.post());
							lay.set(col.x(), l + 1, col.z(), fence.post());
						} else {
							lay.set(col.x(), l + 1, col.z(), fence.post());
							Direction out = c.cardinal(k).getCounterClockWise();
							lay.set(col.x(), l, col.z(), wood.trapdoor(out, Half.TOP, true));
						}
					} else if (k == 2) {
						lay.set(col.x(), l, col.z(), States.facing(Blocks.BARREL, Direction.UP));
					} else if (k == 3) {
						lay.set(col.x(), l, col.z(), States.facing(Blocks.CHEST, lay.local(c.cardinal(k).getCounterClockWise())),
							BlockEntities.container(List.of(new Object[]{"minecraft:fishing_rod", 1}, new Object[]{"minecraft:string", 4},
								new Object[]{"minecraft:cod", 3})));
						Course.Col inner = c.col(col.x() + c.cardinal(k).getCounterClockWise().getStepX(), col.z() + c.cardinal(k).getCounterClockWise().getStepZ());
						if (inner != null) {
							lay.poi(PoiType.STORAGE, inner.x(), l, inner.z(), c.cardinal(k).getClockWise());
						}
					}
				}
			}
		}
		// a jib crane over the landing stage
		if (d.tier() >= 2 && n >= 9 && endRight != null) {
			int k = endRight.k();
			int l = lv[k];
			Direction t = c.cardinal(k);
			if (l + 3 <= top && !stone) {
				try (var s = d.stage(StageKind.FRAME)) {
					for (int y = l; y <= l + 2; y++) {
						lay.set(endRight.x(), y, endRight.z(), frame.log(Direction.Axis.Y));
					}
					int jx = endRight.x();
					int jz = endRight.z();
					for (int j = 0; j <= 2; j++) {
						int x = jx + t.getStepX() * j;
						int z = jz + t.getStepZ() * j;
						if (lay.allowed(x, l + 3, z)) {
							lay.set(x, l + 3, z, beam.log(t.getAxis()));
						}
					}
					int hx = jx + t.getStepX() * 2;
					int hz = jz + t.getStepZ() * 2;
					if (lay.allowed(hx, l + 2, hz) && lay.allowed(hx, l + 1, hz)) {
						lay.set(hx, l + 2, hz, States.axis(Blocks.IRON_CHAIN, Direction.Axis.Y));
						lay.set(hx, l + 1, hz, Blocks.HAY_BLOCK.defaultBlockState());
					}
				}
			}
		}
		// steps down into the water off the end of a quay
		if (stone) {
			for (Course.Col col : cols) {
				if (col.k() != n - 1 || col.ring() == 0) {
					continue;
				}
				int l = lv[n - 1];
				try (var s = d.stage(StageKind.FLOORS)) {
					lay.set(col.x(), l - 1, col.z(), found.stairsOrSlab(c.cardinal(n - 1).getOpposite(), Half.BOTTOM));
				}
			}
		}
		// where boats tie up, and a seat at the end
		int k = n - 1;
		lay.poi(PoiType.DOCK, c.dx(k), lv[k], c.dz(k), c.cardinal(k));
		double r = Noise.unit(seed, c.dx(0), 0, c.dz(0));
		if (r < 0.5 && n >= 6) {
			int kb = landing ? n - 3 : n - 2;
			lay.poi(PoiType.VIEWPOINT, c.dx(kb), lv[kb], c.dz(kb), c.cardinal(kb));
		}
	}
}
