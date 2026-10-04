package dev.voidmc.hearth.build.archetype.extract;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.kit.extraction.Benches;
import dev.voidmc.hearth.build.kit.extraction.Pithead;
import dev.voidmc.hearth.build.kit.extraction.Rigs;
import dev.voidmc.hearth.build.kit.extraction.Rock;
import dev.voidmc.hearth.build.kit.extraction.Spoil;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code hearth:extract/quarry_pit}: a stone quarry cut bench by bench into the town's rock (docs/LIVING_WORLD.md
 * §7.3). Over the weeks a scar of fresh rock opens behind the pit head and goes down in steps: each bench a lift three
 * blocks deep, the working face steep on one side and the others stepping in, short flights of steps cut in the rock
 * turning from bench to bench down to the floor, a ladder up a wall here and there, blocks left standing on the benches
 * half cut free. On rising ground the faces climb into the hillside in steps of their own. At the rim the lifting rig of
 * the culture — a treadwheel crane in Bohemia and the Alps, a gallows derrick in the north, shear legs in the south and
 * east — lets its rope down into the pit, a little further with every bench. The pit head is a levelled yard: the
 * quarrymen's open shed with the stonecutter, the banker and the grindstone, blanks and dressed blocks stacked for the
 * carts, a cart, a lamp at the gate. The spoil tip along one side grows with every bench.
 *
 * <p>Parts: 0 the pit head (yard, shed, rig, fence); then one per bench, top down: its cuts, the treads of the flight
 * down to it, the rope let down through it, the tip's growth, its work faces.
 *
 * <p>Job sites: QUARRIER at the stonecutter and at the foot of every bench's face (WORK_FACE). Storage: the shed's chest.
 *
 * <p>Params (LIVING_WORLD.md §8.5): {@code depth} (int, how far below the floor the benches go; default 9–15 by seed;
 * the pit stops sooner when its floor would be under 3 × 3), {@code rock} (block id of the host rock: its stair treads,
 * blocks and spoil), {@code site}, {@code kind}, {@code face} (recorded; the working face is the back), {@code variant}.
 */
public final class QuarryPit implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("extract/quarry_pit"), ArchetypeCategory.EXTRACTION, Set.of(JobType.QUARRIER), 0,
		15, 31, 15, 31, 8, 14, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 1.2);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Rock rock = Rock.of(d);
		// the pit head: the yard and the rim row levelled to the street (shallower where the land rises sharply behind it)
		int ex = dev.voidmc.hearth.build.kit.trade.Yard.entrance(d);
		int yd = Pithead.yardDepth(d, ex, Math.clamp(dp / 5, 4, 6));
		int[] base;
		try (var p = d.part(0)) {
			base = Pithead.levelYard(d, yd, ex);
		}
		// the tip along one side, the pit beside it
		boolean tipLeft = d.chance(0.5);
		int tipW = Math.clamp(w / 5, 4, 6);
		int lx0 = tipLeft ? tipW + 2 : 1;
		int lx1 = tipLeft ? w - 2 : w - 3 - tipW;
		int[] limit = {lx0, yd + 1, lx1, dp - 2};
		// the working face is the uphill side, steep (on level ground the back, or the side away from the tip); the side
		// opposite opens in broad benches, the others step in two a bench
		int[] inset = {2, 2, 2, 2};
		int faceSide = faceSide(d, limit, d.chance(0.65) ? Benches.BACK : tipLeft ? Benches.RIGHT : Benches.LEFT);
		inset[faceSide] = 1;
		int open = (faceSide + 2) % 4;
		if (edgeMean(d, limit, open) <= -3) {
			// the land falls away on that side: the pit opens to it, each bench running out to the hillside
			inset[open] = 0;
		} else if (faceSide != Benches.BACK || d.site().meanGround(limit[0], limit[3] - 1, limit[2], limit[3]) > 2) {
			inset[open] = 3;
		}
		int h = 3;
		int[] rim = pullBack(d, limit, inset, h);
		boolean[] keep = new boolean[w * dp];
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				keep[z * w + x] = z <= yd || x < lx0 || x > lx1 || z > limit[3];
			}
		}
		// where the land falls away behind the yard, a built stair goes down from the rim to the ground below
		try (var p = d.part(0)) {
			Pithead.stairDown(d, base, keep, yd, Math.clamp(ex, lx0 + 1, lx1 - 1), lx0, lx1);
		}
		int depth = Math.max(h, d.param("depth", d.between(9, 15)));
		Benches pit = new Benches(d, rim, h, inset, depth, limit, keep, new int[]{ex, 0, 0}, base, yd);
		pit.stubs(2 + d.random().nextInt(3));

		// the pit head (part 0): shed, rig, stacks, cart, fence and lamp
		Pithead head = new Pithead(d, rock, ex, yd, base);
		Rigs.Kind kind = Rigs.kind(d);
		Rigs.Hook hook;
		try (var p = d.part(0)) {
			head.path(pit);
			hook = head.rig(pit, kind);
			head.shed(JobType.QUARRIER);
			head.stacks(true);
			head.cart(rock.natural().defaultBlockState());
			head.lamp();
			head.fence(pit, tipLeft ? 0 : w - 1);
		}

		// the benches, one part each
		int[] parts = pit.cut(1, rock, JobType.QUARRIER);
		int n = 0;
		for (int p : parts) {
			if (p >= 0) {
				n++;
			}
		}
		// the tip along the side, growing with every bench
		boolean[] tipCells = new boolean[w * dp];
		int tx0 = tipLeft ? 1 : w - 1 - tipW;
		int tx1 = tipLeft ? tipW : w - 2;
		for (int z = yd + 1; z <= dp - 2; z++) {
			for (int x = tx0; x <= tx1; x++) {
				tipCells[z * w + x] = true;
			}
		}
		double spineX = (tx0 + tx1) / 2.0;
		double top = Math.min((tx1 - tx0) / 2.0 * 0.95 + 0.9, 4.2);
		Spoil tip = new Spoil(d, rock, spineX, dp - 3.5, spineX, yd + 3.5, top, 0.95, tipCells);
		int k = 0;
		for (int p : parts) {
			if (p >= 0) {
				k++;
				tip.grow(k, n, p);
			}
		}
		// the rope let down bench by bench, a block of the rock on it at the bottom
		if (hook != null && pit.cut(hook.x(), hook.z())) {
			// the load swings a man's height over the bench it is being lifted from (folk walk under it)
			int floor = pit.surface(hook.x(), hook.z());
			BlockState load = rock.natural().defaultBlockState();
			Rigs.rope(d, hook, floor + 2, load, y -> partAt(d, pit, parts, hook.x(), y, hook.z()));
		}
		// a sign at the gate
		try (var p = d.part(0)) {
			head.sign("quarry", "Quarry");
			Groundwork.clear(d, 0, 0, w - 1, yd, Math.min(d.maxHeight() - 1, 12));
		}
	}

	/**
	 * The rim pulled back from the limit on each rising side by as many bench steps as the hillside above it needs, so
	 * the faces can climb the slope in steps (the rim keeps at least 7 × 5).
	 */
	static int[] pullBack(Draft d, int[] limit, int[] inset, int h) {
		int[] rim = limit.clone();
		int[] need = new int[4];
		need[Benches.RIGHT] = steps(d, limit[2] - 1, limit[1], limit[2], limit[3], h);
		need[Benches.LEFT] = steps(d, limit[0], limit[1], limit[0] + 1, limit[3], h);
		need[Benches.BACK] = steps(d, limit[0], limit[3] - 1, limit[2], limit[3], h);
		for (int tries = 0; tries < 12; tries++) {
			rim[0] = limit[0] + need[Benches.LEFT] * inset[Benches.LEFT];
			rim[2] = limit[2] - need[Benches.RIGHT] * inset[Benches.RIGHT];
			rim[3] = limit[3] - need[Benches.BACK] * inset[Benches.BACK];
			if (rim[2] - rim[0] >= 6 && rim[3] - rim[1] >= 4) {
				return rim;
			}
			// not room for every step: drop one from the side that asks most
			int worst = Benches.LEFT;
			for (int s : new int[]{Benches.RIGHT, Benches.BACK}) {
				if (need[s] > need[worst]) {
					worst = s;
				}
			}
			if (need[worst] == 0) {
				break;
			}
			need[worst]--;
		}
		return rim;
	}

	/** The mean ground along one edge of a rectangle (two rows deep). */
	static int edgeMean(Draft d, int[] r, int side) {
		return switch (side) {
			case Benches.FRONT -> d.site().meanGround(r[0], r[1], r[2], r[1] + 1);
			case Benches.BACK -> d.site().meanGround(r[0], r[3] - 1, r[2], r[3]);
			case Benches.LEFT -> d.site().meanGround(r[0], r[1], r[0] + 1, r[3]);
			default -> d.site().meanGround(r[2] - 1, r[1], r[2], r[3]);
		};
	}

	/** The side of the pit the ground rises toward (its edge highest above the opposite one), else {@code level}. */
	static int faceSide(Draft d, int[] limit, int level) {
		int[] mean = new int[4];
		for (int sd = 0; sd < 4; sd++) {
			mean[sd] = edgeMean(d, limit, sd);
		}
		int best = level;
		int rise = 2;
		for (int sd = 0; sd < 4; sd++) {
			int r = mean[sd] - mean[(sd + 2) % 4];
			if (r > rise) {
				rise = r;
				best = sd;
			}
		}
		return best;
	}

	/** Bench steps the ground over a strip stands above the floor. */
	private static int steps(Draft d, int x0, int z0, int x1, int z1, int h) {
		int g = d.site().maxGround(x0, z0, x1, z1);
		return g <= 0 ? 0 : Math.min(6, (g + h - 1) / h);
	}

	/** The part a cell of the rope belongs to: the bench whose lift cut it, else the pit head (0). */
	static int partAt(Draft d, Benches pit, int[] parts, int x, int y, int z) {
		if (y >= d.site().ground(x, z)) {
			return 0;
		}
		for (int i = 0; i < pit.levels.size(); i++) {
			for (int[] c : pit.lift(i)) {
				if (c[0] == x && c[1] == z && y >= c[2] && y < c[3]) {
					return Math.max(0, parts[i]);
				}
			}
		}
		return parts.length == 0 ? 0 : Math.max(0, parts[parts.length - 1]);
	}
}
