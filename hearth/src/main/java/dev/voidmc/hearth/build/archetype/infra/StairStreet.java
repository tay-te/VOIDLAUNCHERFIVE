package dev.voidmc.hearth.build.archetype.infra;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.infra.Course;
import dev.voidmc.hearth.build.kit.infra.Demo;
import dev.voidmc.hearth.build.kit.infra.Dress;
import dev.voidmc.hearth.build.kit.infra.Lamps;
import dev.voidmc.hearth.build.kit.infra.Lay;
import dev.voidmc.hearth.build.kit.infra.LineMat;
import dev.voidmc.hearth.build.kit.infra.Roadbed;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:infra/stairs}: a stair street, for a slope too steep to grade: a flight of treads in the culture's
 * paving stone (planks on the bayou and in the tropics) climbing over the corridor the planner reserved, every rise a
 * stair, landings where the planner's profile flattens (where contour lanes join), cheeks of dressed stone up its edges.
 *
 * <p>Cut into the hill, it runs between retaining walls where the land beside stands three or more blocks over the
 * treads (coped, potted flowers on the copings); built out over falling ground, its fill is faced in masonry and a
 * parapet guards a drop of three or more. Landings get an inlaid stone in the middle, and lamps: on the kerb line of a
 * wide flight, on a parapet post or a wall coping of a narrow one. Edges stay walkable elsewhere (fronting houses open
 * onto them).
 *
 * <p>Params: {@code points} (int array x,y,z,..., world centreline at walking level, |Δy| ≤ 1 per step; default: a
 * flight straight up the plot, graded to the land at up to one block per block), {@code width} (odd, default 3).
 */
public final class StairStreet implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("infra/stairs"), ArchetypeCategory.INFRASTRUCTURE, Set.of(), 0,
		1, 56, 1, 56, 1, 40, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 0, 1.0);

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
			width = Demo.odd(Math.min(d.param("width", d.chance(0.3) ? 5 : 3), d.frontage()));
			pts = Demo.course(lay, width, 1.0, false);
		} else {
			width = Demo.odd(d.param("width", 3));
		}
		Course c = new Course(pts, width);
		Dress dress = Dress.stairs(lay, width);
		Roadbed bed = new Roadbed(lay, c, dress, 2, true);
		int last = c.steps() - 1;
		lay.entrance(c.dx(0), c.dy(0), c.dz(0));
		lay.entrance(c.dx(last), c.dy(last), c.dz(last));
		bed.earthwork(true);
		bed.surface();
		sides(lay, c, bed);
		landings(lay, c, bed);
		bed.details(Dress.Kind.STREET);
		bed.clear(4);
	}

	/** Retaining walls where the hill stands high beside the treads, parapets over drops, lamps on them at landings. */
	private static void sides(Lay lay, Course c, Roadbed bed) {
		Draft d = lay.draft();
		LineMat wall = LineMat.of(lay, Role.FOUNDATION);
		LineMat coping = LineMat.of(lay, Role.PLINTH).weathered(0.1);
		LineMat garden = LineMat.of(lay, Role.GARDEN);
		long seed = Noise.hash(d.seed(), 0x5747);
		for (int i = 0; i < bed.cols().size(); i++) {
			Course.Col col = bed.cols().get(i);
			if (!bed.inside(i) || col.ring() != 0 || bed.inEnd(i)) {
				continue;
			}
			int l = bed.level(i);
			int hi = Integer.MIN_VALUE;
			int lo = Integer.MAX_VALUE;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int nx = col.x() + dir.getStepX();
				int nz = col.z() + dir.getStepZ();
				if (!c.contains(nx, nz)) {
					hi = Math.max(hi, lay.surface(nx, nz));
					lo = Math.min(lo, lay.surface(nx, nz));
				}
			}
			if (hi == Integer.MIN_VALUE) {
				continue;
			}
			if (hi - l >= 3) {
				// a retaining wall up to the land beside, coped flush with it
				int top = Math.min(hi - 1, lay.topY());
				try (var s = d.stage(StageKind.FOUNDATION)) {
					for (int y = l; y < top; y++) {
						lay.set(col.x(), y, col.z(), wall.full());
					}
					lay.set(col.x(), top, col.z(), coping.full());
				}
				double r = Noise.unit(seed, col.x(), 0, col.z());
				if (top + 1 <= lay.topY() && r < 0.18) {
					try (var s = d.stage(StageKind.EXTERIOR)) {
						if (r < 0.06) {
							Lamps.standing(lay, col.x(), top + 1, col.z());
						} else {
							lay.set(col.x(), top + 1, col.z(), Blocks.FLOWER_POT.defaultBlockState());
						}
					}
				}
			} else if (l - lo >= 3) {
				// a parapet over the drop
				try (var s = d.stage(StageKind.EXTERIOR)) {
					lay.set(col.x(), l, col.z(), wall.has(Form.WALL) ? wall.wall() : coping.slab(SlabType.BOTTOM));
				}
				if (bed.shape(i) == Roadbed.Shape.FULL && Math.floorMod(col.x() * 3 + col.z() * 5, 7) == 0 && l + 1 <= lay.topY()) {
					Lamps.standing(lay, col.x(), l + 1, col.z());
				}
			}
		}
	}

	/** Landings: an inlaid stone in the middle of each. */
	private static void landings(Lay lay, Course c, Roadbed bed) {
		Draft d = lay.draft();
		LineMat inlay = LineMat.of(lay, Role.ACCENT).weathered(0);
		for (int i = 0; i < bed.cols().size(); i++) {
			Course.Col col = bed.cols().get(i);
			if (!bed.inside(i) || !bed.centre(i) || bed.inEnd(i) || bed.shape(i) != Roadbed.Shape.FULL) {
				continue;
			}
			int k = col.k();
			if (k < 1 || k + 1 >= c.steps() || c.dy(k - 1) != c.dy(k) || c.dy(k + 1) != c.dy(k)) {
				continue;
			}
			// the middle of a run of level steps
			int a = k;
			int b = k;
			while (a > 0 && c.dy(a - 1) == c.dy(k)) {
				a--;
			}
			while (b + 1 < c.steps() && c.dy(b + 1) == c.dy(k)) {
				b++;
			}
			if (b - a >= 2 && k == (a + b) / 2 && inlay.has(Form.FULL)) {
				try (var s = d.stage(StageKind.FLOORS)) {
					lay.set(col.x(), bed.level(i) - 1, col.z(), inlay.full());
				}
			}
		}
	}
}
