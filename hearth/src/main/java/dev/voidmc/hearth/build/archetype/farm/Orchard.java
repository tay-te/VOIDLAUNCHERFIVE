package dev.voidmc.hearth.build.archetype.farm;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Furniture;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.Roofs;
import dev.voidmc.hearth.build.kit.homestead.Bounds;
import dev.voidmc.hearth.build.kit.homestead.Crops;
import dev.voidmc.hearth.build.kit.homestead.Sheds;
import dev.voidmc.hearth.build.kit.homestead.Land;
import dev.voidmc.hearth.build.kit.homestead.Spots;
import dev.voidmc.hearth.build.kit.homestead.Terraces;
import dev.voidmc.hearth.build.kit.homestead.Trees;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * {@code hearth:farm/orchard}: fruit trees in rows on the grass, the ground left as it lies. The trees are the culture's
 * own where it grows fruit (blossoming cherries in the karst and the vale, leaning olives round the Mediterranean, palms
 * in the desert, cocoa in the jungle) and apples elsewhere, set out in a quincunx and of mixed ages; where the land is
 * cold, rows of berry bushes run between them. A mown path leads from the gate up the middle; beehives stand on posts
 * among the flowers at the row ends; a ladder leans against a tree and baskets wait under another; a press shed in a back
 * corner holds the compost bin (the farmer's job site) and the crates.
 *
 * <p>Job sites (FARMER): the shed's compost bin, and a stand beside every other tree. Storage: the shed's chest.
 *
 * <p>Params (all optional): {@code shed} (boolean), {@code spacing} (int 4–7: trunk to trunk, default 5 or 6), {@code boundary}
 * (string: drystone, hedge, rails, bund, none).
 */
public final class Orchard implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("farm/orchard"), ArchetypeCategory.FARM, Set.of(JobType.FARMER), 0,
		11, 21, 11, 21, 8, 12, SettlementTier.HAMLET, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 0.6);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Trees.Kind kind = Trees.kind(d);
		boolean cold = d.site().temperature() < 0.35F || d.mat(Role.TREE_LEAVES).main(Form.FULL) == Blocks.SPRUCE_LEAVES;
		int pathX = w / 2;
		d.entrance(pathX, Terraces.street(d, pathX), -1);
		Land.mendFill(d, 0, 0, w - 1, dp - 1);
		// the mown path up the middle
		List<int[]> path = new ArrayList<>();
		for (int z = 0; z < dp - 1; z++) {
			path.add(new int[]{pathX, z});
		}
		Land.path(d, path);
		// the press shed in a back corner
		int[] shed = null;
		boolean left = d.chance(0.5);
		if (d.param("shed", d.chance(0.8)) && w >= 12 && dp >= 12) {
			shed = shed(d, w, dp, left);
		}
		// the trees: a quincunx, trunks `spacing` apart, clear of the path, the shed and the boundary
		int spacing = Math.clamp(d.param("spacing", kind == Trees.Kind.PALM ? 5 : w >= 16 && d.chance(0.6) ? 6 : 5), 4, 7);
		List<int[]> trunks = new ArrayList<>();
		int row = 0;
		for (int z = 2; z <= dp - 3; z += spacing - 1, row++) {
			int offset = (row & 1) == 0 ? 0 : spacing / 2;
			for (int x = 2 + offset; x <= w - 3; x += spacing) {
				if (Math.abs(x - pathX) <= 1) {
					continue;
				}
				if (shed != null && x >= shed[0] - 2 && x <= shed[2] + 2 && z >= shed[1] - 2) {
					continue;
				}
				int y = Groundwork.surface(d, x, z);
				if (d.site().isWater(x, z) && !d.drained(x, z) || !d.isEmpty(x, y, z)) {
					continue;
				}
				trunks.add(new int[]{x, y, z});
			}
		}
		// berry rows between the tree rows where it is cold
		if (cold) {
			for (int z = 2 + (spacing - 1) / 2 + 1; z <= dp - 3; z += spacing - 1) {
				for (int x = 2; x <= w - 3; x++) {
					if (Math.abs(x - pathX) <= 1 || occupied(trunks, x, z, 1)) {
						continue;
					}
					if (shed != null && x >= shed[0] - 1 && x <= shed[2] + 1 && z >= shed[1] - 1) {
						continue;
					}
					int y = Groundwork.surface(d, x, z);
					if (Garden.groundLike(d, x, y - 1, z) && d.isEmpty(x, y, z)) {
						Trees.berries(d, x, y, z, Dirs.RIGHT, 1);
					}
				}
			}
		}
		// the boundary before the trees, so their crowns lean over it rather than through it
		List<int[]> gates = new ArrayList<>();
		gates.add(new int[]{pathX, 0});
		Bounds.around(d, 0, 0, w - 1, dp - 1, Field.boundary(d), gates);
		int n = 0;
		List<int[]> grown = new ArrayList<>();
		for (int[] t : trunks) {
			int room = d.maxHeight() - 1 - t[1];
			int size = room >= 8 ? (Noise.unit(d.seed() ^ 0xA6E, t[0], 0, t[2]) < 0.25 ? 2 : 1) : room >= 6 ? 1 : room >= 5 ? 0 : -1;
			if (size < 0) {
				continue;
			}
			// a young tree now and then where an old one was felled
			if (Noise.unit(d.seed() ^ 0xA6F, t[0], 1, t[2]) < 0.12) {
				size = 0;
			}
			d.remove(t[0], t[1], t[2]);
			d.remove(t[0], t[1] + 1, t[2]);
			if (!Trees.fruit(d, t[0], t[1], t[2], kind, size) && (size == 0 || !Trees.fruit(d, t[0], t[1], t[2], kind, 0))) {
				continue;
			}
			grown.add(t);
		}
		// a ladder against one tree, baskets of fruit under another
		if (!grown.isEmpty()) {
			int[] a = grown.get(d.between(0, grown.size() - 1));
			Trees.ladder(d, a[0], a[1], a[2], Dirs.FRONT);
			int[] b = grown.get(d.between(0, grown.size() - 1));
			int bx = b[0] + 1;
			int by = Groundwork.surface(d, bx, b[2] - 1);
			if (d.isEmpty(bx, by, b[2] - 1) && Garden.groundLike(d, bx, by - 1, b[2] - 1)) {
				d.remove(bx, by, b[2] - 1);
				Crops.harvest(d, bx, by, b[2] - 1, Dirs.FRONT);
			}
		}
		// beehives on posts at the ends of the rows by the path
		int hives = 0;
		for (int z = 3; z < dp - 2 && hives < (w * dp >= 250 ? 3 : 2); z += spacing + 1) {
			int x = pathX + ((hives & 1) == 0 ? 2 : -2);
			int y = Groundwork.surface(d, x, z);
			if (d.isEmpty(x, y, z) && d.isEmpty(x, y + 1, z) && Garden.groundLike(d, x, y - 1, z) && !occupied(trunks, x, z, 1)) {
				d.remove(x, y, z);
				Garden.beehive(d, x, y, z, x > pathX ? Dirs.LEFT : Dirs.RIGHT);
				hives++;
			}
		}
		// grass and flowers under the trees, for the bees
		meadow(d, w, dp, pathX);
		// the pickers' stands beside every other trunk, on the path side (once every crown is up)
		for (int[] t : grown) {
			if (n++ % 2 == 0) {
				Direction side = t[0] < pathX ? Dirs.RIGHT : Dirs.LEFT;
				Spots.job(d, t[0] + Dirs.dx(side), t[2], side.getOpposite(), JobType.FARMER);
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, d.site().maxGround(0, 0, w - 1, dp - 1) + 9));
	}

	private static boolean occupied(List<int[]> trunks, int x, int z, int r) {
		for (int[] t : trunks) {
			if (Math.abs(t[0] - x) <= r && Math.abs(t[2] - z) <= r) {
				return true;
			}
		}
		return false;
	}

	/** Water in the orchard is filled level with its bank. */
	private static void fillWater(Draft d, int w, int dp) {
		for (int z = 0; z < dp; z++) {
			for (int x = 0; x < w; x++) {
				if (!d.site().isWater(x, z)) {
					continue;
				}
				int wy = d.site().water(x, z);
				int g = d.site().ground(x, z);
				try (var s = d.stage(StageKind.EARTHWORK)) {
					for (int y = g - 1; y < wy - 1; y++) {
						d.set(x, y, z, d.mat(Role.FILL).full());
					}
					d.set(x, wy - 1, z, d.mat(Role.GROUND).full());
					d.drain(x, z);
				}
			}
		}
	}

	/** A path laid over water that was filled: lay it again now the ground is there. */
	private static void repath(Draft d, List<int[]> path) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : path) {
				int y = Groundwork.surface(d, c[0], c[1]);
				if (Garden.groundLike(d, c[0], y - 1, c[1]) && d.isEmpty(c[0], y, c[1])) {
					d.set(c[0], y - 1, c[1], d.mat(Role.PATH).full());
				}
			}
		}
	}

	/** The press shed: a lean-to in a back corner, open toward the path; the compost bin, a chest, barrels of cider. */
	private static int[] shed(Draft d, int w, int dp, boolean left) {
		int sw = 4;
		int sd = 3;
		int z1 = dp - 2;
		int z0 = z1 - sd + 1;
		for (int k = 0; k <= 2; k++) {
			int x0 = left ? 1 + k : w - 1 - sw - k;
			int x1 = x0 + sw - 1;
			int y = Sheds.pad(d, x0, z0, x1, z1, Dirs.FRONT, 2);
			if (y == Integer.MIN_VALUE || y + 6 >= d.maxHeight()) {
				continue;
			}
			Roofs.Roof roof = Sheds.lean(d, x0, z0, x1, z1, y, Dirs.FRONT, 3, false);
			int row = z1 - 1;
			Furniture.workstation(d, JobType.FARMER, x0 + 1, y, row, Dirs.FRONT);
			Furniture.chest(d, x1 - 1, y, row, Dirs.FRONT, List.of(new Object[]{"minecraft:apple", 12}, new Object[]{"minecraft:shears", 1},
				new Object[]{"minecraft:glass_bottle", 6}));
			Furniture.hangingLantern(d, (x0 + x1) / 2, roof.bottomY((x0 + x1) / 2, z0 + 1), z0 + 1, 0);
			return new int[]{x0, z0, x1, z1};
		}
		return null;
	}

	/** Short grass and the culture's flowers under the trees, thicker toward the edges. */
	private static void meadow(Draft d, int w, int dp, int pathX) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 1; z < dp - 1; z++) {
				for (int x = 1; x < w - 1; x++) {
					if (x == pathX) {
						continue;
					}
					int y = Groundwork.surface(d, x, z);
					if (!d.isAbsent(x, y, z)) {
						continue;
					}
					var below = d.get(x, y - 1, z);
					boolean grass = below == null ? !d.site().isWater(x, z) : below.is(Blocks.GRASS_BLOCK);
					if (!grass || d.mat(Role.GROUND).main(Form.FULL) != Blocks.GRASS_BLOCK) {
						continue;
					}
					double u = Noise.unit(d.seed() ^ 0x0C4, x, 0, z);
					if (u < 0.07 && y + 2 < d.maxHeight()) {
						Garden.flower(d, x, y, z);
					} else if (u < 0.3) {
						d.set(x, y, z, Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
		}
	}
}
