package dev.voidmc.hearth.build.archetype.infra;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.kit.Side;
import dev.voidmc.hearth.build.kit.infra.Lay;
import dev.voidmc.hearth.build.kit.infra.LineMat;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Site;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * {@code hearth:infra/terrace}: the level platform a building stands on where the ground is too uneven for it, the
 * backbone of hillside and cliff towns. The whole footprint is cut and filled to the deck ({@code level}, the building's
 * floor), topped with earth the building lays its own floor and yard over; the sides that stand above the land
 * ({@code retain_*}) are held by retaining walls.
 *
 * <p>The walls: footings below the land, a face of the foundation stone (weathered and mossy toward the foot), pilasters
 * of dressed stone every four blocks and at the corners, a dressed coping under the deck's edge, weep holes low in the
 * face; where a wall stands five or more, blind arches between the pilasters, a niche each, with ivy hanging in it and
 * here and there a stone bench to sit in (a SEAT). Nothing is drawn at or above the deck: that is the building's.
 *
 * <p>The street side ({@code facing}) keeps its top three courses in earth even where it is retained, so the building's
 * own steps from its door to the street (at most three, PLANNING.md §4) can be cut into it.
 *
 * <p>Params: {@code level} (walking level on the deck, default the plot's floor), {@code retain_north},
 * {@code retain_south}, {@code retain_east}, {@code retain_west} (world sides standing above the land; default: the
 * sides where a third or more of the land beside lies below the deck), {@code depth} (level − lowest ground, unused).
 * A side not retained is still faced wherever its fill stands two or more over the land beside it.
 */
public final class Terrace implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("infra/terrace"), ArchetypeCategory.INFRASTRUCTURE, Set.of(), 0,
		3, 41, 3, 41, 2, 12, SettlementTier.CAMP, Set.of(), Set.of(), List.of(), 0, 1.0);

	/** The street side's top courses left in earth for a building's own steps. */
	static final int FRONT_EARTH = 3;

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		Lay lay = new Lay(d);
		int w = d.frontage();
		int dp = d.depth();
		// local y 0 is the floor; the deck's walking level may differ from it
		int deck = d.param("level", lay.floorY()) - lay.floorY();
		int street = d.site().isWater(w / 2, -1) ? d.site().water(w / 2, -1) : d.site().ground(w / 2, -1);
		d.entrance(w / 2, street, -1);
		LineMat fill = LineMat.of(lay, Role.FILL);
		LineMat top = LineMat.of(lay, Role.GROUND);
		LineMat found = LineMat.of(lay, Role.FOUNDATION);
		// the platform: cut and fill to the deck, earth on top
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				int g = d.site().ground(x, z);
				boolean wet = d.site().isWater(x, z);
				try (var s = d.stage(wet ? StageKind.FOUNDATION : StageKind.EARTHWORK)) {
					for (int y = g - 1; y < deck - 1; y++) {
						d.set(x, y, z, wet ? found.full() : fill.full());
					}
					for (int y = deck; y < Math.min(g, d.maxHeight()); y++) {
						d.air(x, y, z);
					}
					if (wet) {
						int wy = d.site().water(x, z);
						for (int y = deck; y < Math.min(wy, d.maxHeight()); y++) {
							d.air(x, y, z);
						}
						d.drain(x, z);
					}
					d.set(x, deck - 1, z, top.full());
				}
			}
		}
		// retaining walls on the sides standing above the land
		long seed = Noise.hash(d.seed(), 0x7E44);
		for (Side side : Side.of(0, 0, w - 1, dp - 1)) {
			Direction out = side.out();
			Direction world = d.frame().toWorld(out);
			boolean retained = d.hasParam("retain_" + world.getName()) ? d.param("retain_" + world.getName(), false) : lowSide(d, side, deck);
			// a side the planner does not retain (a neighbour's ground or its own terrace beside it) is still faced where
			// the fill stands two or more over the land: no raw earth cliffs at the platform's ends
			wall(d, lay, side, deck, out == Dirs.FRONT, seed, retained ? 1 : 2);
		}
	}

	/** The planner's rule: a third or more of the land beside the side lies more than a block below the deck. */
	static boolean lowSide(Draft d, Side side, int deck) {
		int low = 0;
		for (int i = 0; i < side.length(); i++) {
			if (d.site().ground(side.outX(i, 1), side.outZ(i, 1)) < deck - 1) {
				low++;
			}
		}
		return low * 3 >= side.length();
	}

	/** One retaining wall along a side of the platform. */
	private static void wall(Draft d, Lay lay, Side side, int deck, boolean street, long seed, int minVisible) {
		LineMat face = LineMat.of(lay, Role.FOUNDATION);
		LineMat plinth = LineMat.of(lay, Role.PLINTH).weathered(0.3);
		LineMat dressed = LineMat.of(lay, Role.WALL_ACCENT).weathered(0.1);
		LineMat pilaster = contrast(face, dressed, plinth);
		LineMat coping = contrast(face, plinth, dressed);
		LineMat bench = LineMat.of(lay, Role.FURNITURE);
		Direction out = side.out();
		Direction in = out.getOpposite();
		int n = side.length();
		for (int i = 0; i < n; i++) {
			int x = side.x(i);
			int z = side.z(i);
			int og = d.site().ground(side.outX(i, 1), side.outZ(i, 1));
			int ow = d.site().water(side.outX(i, 1), side.outZ(i, 1));
			int outside = ow == Site.NONE ? og : Math.max(og, ow);
			int visible = deck - 1 - outside;
			if (visible < minVisible) {
				continue;
			}
			int g = Math.min(d.site().ground(x, z), og);
			boolean pil = i % 4 == 0 || i == n - 1;
			int earthTop = street ? deck - 1 - FRONT_EARTH : deck - 1;
			try (var s = d.stage(StageKind.FOUNDATION)) {
				for (int y = g - 2; y <= deck - 1; y++) {
					if (y > earthTop) {
						break;
					}
					Paint p;
					if (y == deck - 1) {
						p = coping.full();
					} else if (pil && y >= outside) {
						p = pilaster.full();
					} else if (y == outside && visible >= 3) {
						p = plinth.full();
					} else {
						p = face.full();
					}
					d.set(x, y, z, p);
				}
			}
			if (street && visible <= FRONT_EARTH) {
				continue;
			}
			// weep holes: a dark slot low in the face, the fill showing behind
			if (!pil && visible >= 3 && i % 4 == 2 && outside + 1 < earthTop) {
				try (var s = d.stage(StageKind.FOUNDATION)) {
					d.air(x, outside + 1, z);
				}
			}
		}
		// blind arches: a niche between each pair of pilasters where the wall stands five or more
		for (int p = 0; p < n - 3; p += 4) {
			int a = p + 1;
			int b = Math.min(p + 3, n - 2);
			if (b - a < 2) {
				continue;
			}
			boolean wet = false;
			int floor = Integer.MIN_VALUE;
			for (int i = a; i <= b; i++) {
				wet |= d.site().isWater(side.outX(i, 1), side.outZ(i, 1));
				floor = Math.max(floor, d.site().ground(side.outX(i, 1), side.outZ(i, 1)));
			}
			int archTop = (street ? deck - 1 - FRONT_EARTH : deck - 1) - 1;
			if (wet || archTop - floor < 4) {
				continue;
			}
			for (int i = a; i <= b; i++) {
				int x = side.x(i);
				int z = side.z(i);
				int ix = x + Dirs.dx(in);
				int iz = z + Dirs.dz(in);
				try (var s = d.stage(StageKind.FOUNDATION)) {
					// the back of the niche, a block in
					for (int y = floor; y < archTop; y++) {
						d.set(ix, y, iz, face.full());
					}
					// the niche itself, from the land up to the arch
					for (int y = floor; y < archTop - 1; y++) {
						d.air(x, y, z);
					}
					// the arch: stairs springing from the pilasters, a keystone over the middle
					if (i == a || i == b) {
						Direction toward = sideward(side, i == a ? -1 : 1);
						d.set(x, archTop - 1, z, pilaster.stairsOrSlab(lay.frame().toWorld(toward), Half.TOP));
					} else {
						d.set(x, archTop - 1, z, pilaster.full());
					}
					d.set(x, archTop, z, face.full());
				}
			}
			// ivy hanging in the niche, a bench in some
			double r = Noise.unit(seed, side.x(a), floor, side.z(a));
			int mi = (a + b) / 2;
			int mx = side.x(mi);
			int mz = side.z(mi);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(in), true);
				for (int i = a; i <= b; i++) {
					int len = 1 + (int) (Noise.unit(seed, side.x(i), 3, side.z(i)) * 3);
					for (int k = 0; k < len && archTop - 2 - k > floor; k++) {
						if (Noise.unit(seed, side.x(i), 7 + k, side.z(i)) < 0.75) {
							d.set(side.x(i), archTop - 2 - k, side.z(i), vine);
						}
					}
				}
				if (r < 0.45 && bench.has(Form.STAIRS)) {
					d.set(mx, floor, mz, bench.stairs(lay.frame().toWorld(in), Half.BOTTOM));
					d.poi(PoiType.SEAT, mx, floor, mz, out);
				}
			}
		}
	}

	/** The direction along a side, toward its end (+1) or its start (-1), in the local frame. */
	private static Direction sideward(Side side, int sign) {
		int dx = side.x(Math.min(side.length() - 1, 1)) - side.x(0);
		int dz = side.z(Math.min(side.length() - 1, 1)) - side.z(0);
		Direction dir = Dirs.of(Integer.signum(dx), Integer.signum(dz));
		if (dir == null) {
			dir = Dirs.right(side.out());
		}
		return sign > 0 ? dir : dir.getOpposite();
	}

	private static LineMat contrast(LineMat body, LineMat a, LineMat b) {
		return !a.mainName().equals(body.mainName()) ? a : b;
	}
}
