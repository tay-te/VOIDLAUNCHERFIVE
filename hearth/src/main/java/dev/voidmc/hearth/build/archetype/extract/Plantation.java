package dev.voidmc.hearth.build.archetype.extract;

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
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.extraction.Pithead;
import dev.voidmc.hearth.build.kit.extraction.Rock;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.Trees;
import dev.voidmc.hearth.build.kit.trade.Props;
import dev.voidmc.hearth.build.kit.trade.Yard;
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

/**
 * {@code hearth:extract/plantation}: the woodlot planted behind the logging camp, worked in rotation coupe by coupe
 * (docs/LIVING_WORLD.md §7.3). Coppice-with-standards, the way a manor's woods were kept: the plot behind its yard is
 * cut in coupes across its depth, each closed by a wood bank (a ditch dug along its edge, the earth thrown up on the
 * coupe's side) and opened from a ride up the middle. The coupes stand at every age of the rotation: one just cut —
 * stumps, saplings set between them, the cordwood stacked by the ride, a standard or two left standing; one in its
 * regrowth, the stools bushing up a man's height; one grown, its trees tall. At the front a little yard with the
 * forester's shed, the logs waiting for the carts, a chopping block, the lamp.
 *
 * <p>Parts: 0 the yard and the ride; then one per coupe, front to back: its bank and ditch, its felling and planting,
 * its work faces (by the ride, where the foresters go in). The light rule is a farm's: the woods are dark on purpose.
 *
 * <p>Job sites: FORESTER at the shed's bench and at every coupe (WORK_FACE). Params (§8.5): {@code site}, {@code kind}
 * (recorded), {@code coupes} (2–5; default by the plot's depth).
 */
public final class Plantation implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("extract/plantation"), ArchetypeCategory.EXTRACTION, Set.of(JobType.FORESTER), 0,
		15, 31, 15, 31, 9, 16, SettlementTier.VILLAGE, Set.of(), Set.of(ArchetypeInfo.SiteFlag.OUTSKIRTS), List.of(), 0, 0.8);

	/** Ages of a coupe in the rotation. */
	private enum Age {
		CUT, REGROWTH, GROWN
	}

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		Rock rock = Rock.of(d);
		int ex = Yard.entrance(d);
		int ride = Math.clamp(ex, 3, w - 4);
		int yd = Pithead.yardDepth(d, ex, 4);
		int[] base;
		try (var p = d.campaign(0)) {
			base = Pithead.levelYard(d, yd, ex);
		}
		Pithead head = new Pithead(d, rock, ex, yd, base);
		Mat logs = d.mat(Role.TREE_LOG);
		try (var p = d.campaign(0)) {
			head.way(ride);
			head.shed(JobType.FORESTER);
			head.lamp();
			yardStacks(d, head, yd, logs);
			head.cart(logs.log(Direction.Axis.X).at(0, 0, 0));
			Groundwork.clear(d, 0, 0, w - 1, yd, Math.min(d.maxHeight() - 1, 10));
		}
		int[] rideY = new int[dp];
		int rideEnd;
		try (var p = d.campaign(0)) {
			// the ride starts at the yard's rim, or at the street where the yard could not be levelled in front of it
			boolean yardOk = true;
			for (int z = 0; z <= yd; z++) {
				yardOk &= base[z * w + ride] == 0;
			}
			rideEnd = ride(d, ride, yardOk ? yd : -1, 0, rideY);
		}

		int span = dp - 2 - yd;
		int n = Math.clamp(d.param("coupes", span / 6), 2, 5);
		Age[] ages = Age.values();
		int shift = d.random().nextInt(3);
		Trees.Kind kind = Trees.kind(d);
		Block sapling = sapling(d);
		for (int k = 0; k < n; k++) {
			int z0 = yd + 1 + (int) Math.round(k * span / (double) n);
			int z1 = yd + (int) Math.round((k + 1) * span / (double) n);
			Age age = ages[(k + shift) % 3];
			try (var p = d.campaign(k + 1)) {
				bank(d, z0, ride, k == 0);
				coupe(d, z0 + 2, z1, ride, age, kind, sapling, logs);
				// where the foresters go in from the ride: on it, and beside it where the coupe's ground meets it
				int zm = Math.min(Math.min(z1, z0 + 3), rideEnd);
				if (zm >= z0) {
					int ry = rideY[zm];
					d.poi(PoiType.WORK_FACE, ride, ry, zm, (k & 1) == 0 ? Dirs.LEFT : Dirs.RIGHT, JobType.FORESTER);
					if (zm - 2 >= z0) {
						d.poi(PoiType.WORK_FACE, ride, rideY[zm - 2], zm - 2, (k & 1) == 0 ? Dirs.RIGHT : Dirs.LEFT, JobType.FORESTER);
					}
					int x = ride + ((k & 1) == 0 ? -1 : 1);
					if (d.site().ground(x, zm) == ry && d.isEmpty(x, ry, zm) && d.isEmpty(x, ry + 1, zm) && !d.site().isWater(x, zm)) {
						d.poi(PoiType.WORK_FACE, x, ry, zm, (k & 1) == 0 ? Dirs.LEFT : Dirs.RIGHT, JobType.FORESTER);
					}
				}
			}
		}
		try (var p = d.campaign(0)) {
			head.sign("plantation", "Woodlot");
			Pithead.enclose(d, ex);
		}
	}

	/**
	 * The ride up the middle of the woods from the yard's rim (standing at {@code y0}): it keeps to the land where it can,
	 * a sunken lane where the land rises faster than a step a row and an embanked one where it falls, never more than
	 * three blocks off the land; where it cannot go on it stops. Fills {@code level} with its standing level by row;
	 * returns its last row.
	 */
	private static int ride(Draft d, int x, int yd, int y0, int[] level) {
		Mat path = d.mat(Role.PATH);
		Mat fill = d.mat(Role.FILL);
		int r = y0;
		int end = yd;
		for (int z = yd + 1; z <= d.depth() - 2; z++) {
			if (d.site().isWater(x, z)) {
				break;
			}
			int g = d.site().ground(x, z);
			int next = Math.clamp(g, r - 1, r + 1);
			if (Math.abs(g - next) > 5 || next >= d.maxHeight() - 3 || g > d.maxHeight()) {
				break;
			}
			try (var st = d.stage(StageKind.EARTHWORK); var o = d.order(z)) {
				for (int y = g - 1; y >= next; y--) {
					d.set(x, y, z, Blocks.AIR.defaultBlockState());
				}
				for (int y = g; y < next - 1; y++) {
					d.set(x, y, z, fill.full());
				}
			}
			try (var st = d.stage(StageKind.EXTERIOR)) {
				d.set(x, next - 1, z, path.full());
			}
			r = next;
			level[z] = r;
			end = z;
		}
		return end;
	}

	/** The sapling of the style's timber tree. */
	private static Block sapling(Draft d) {
		String log = Manner.path(d.mat(Role.TREE_LOG), Form.LOG);
		for (String wood : new String[]{"dark_oak", "spruce", "birch", "jungle", "acacia", "cherry", "pale_oak", "oak"}) {
			if (log.contains(wood)) {
				return dev.voidmc.hearth.build.draft.States.block("minecraft:" + wood + "_sapling", Blocks.OAK_SAPLING);
			}
		}
		if (log.contains("mangrove")) {
			return Blocks.MANGROVE_PROPAGULE;
		}
		return Blocks.OAK_SAPLING;
	}

	/**
	 * The wood bank along a coupe's front edge: a ditch a block deep across the plot (the ride crosses it on the level),
	 * the earth heaped on the coupe's side in a low bank, grassed over. The first coupe's bank faces the yard.
	 */
	private static void bank(Draft d, int z, int ride, boolean first) {
		int w = d.frontage();
		try (var st = d.stage(StageKind.EARTHWORK)) {
			for (int x = 1; x <= w - 2; x++) {
				if (Math.abs(x - ride) <= 1 || d.site().isWater(x, z) || d.site().isWater(x, z + 1)) {
					continue;
				}
				int g = d.site().ground(x, z);
				int gb = d.site().ground(x, z + 1);
				if (g >= d.maxHeight() || gb >= d.maxHeight() - 1) {
					continue;
				}
				try (var o = d.order(x)) {
					d.set(x, g - 1, z, Blocks.AIR.defaultBlockState());
					d.set(x, g - 2, z, (Noise.unit(d.seed() ^ 0xD17C, x, 0, z) < 0.4 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState());
					if (gb <= g + 1 && d.allowed(x, gb, z + 1)) {
						d.set(x, gb, z + 1, (Noise.unit(d.seed() ^ 0xBA4C, x, 1, z) < 0.75 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK).defaultBlockState());
					}
				}
			}
		}
	}

	/** One coupe's trees on rows {@code z0..z1}, at its age in the rotation. */
	private static void coupe(Draft d, int z0, int z1, int ride, Age age, Trees.Kind kind, Block sapling, Mat logs) {
		int w = d.frontage();
		int spacing = kind == Trees.Kind.PALM ? 5 : 4;
		int row = 0;
		List<int[]> stumps = new ArrayList<>();
		for (int z = z0; z <= z1; z += spacing - 1, row++) {
			int off = (row & 1) == 0 ? 0 : spacing / 2;
			for (int x = 2 + off; x <= w - 3; x += spacing) {
				if (Math.abs(x - ride) <= 2 || d.site().isWater(x, z)) {
					continue;
				}
				int y = Groundwork.surface(d, x, z);
				if (y >= d.maxHeight() - 2 || !Garden.groundLike(d, x, y - 1, z) || !d.isEmpty(x, y, z)) {
					continue;
				}
				// crowns kept off the ride
				int bx0 = x < ride ? 1 : ride + 2;
				int bx1 = x < ride ? ride - 2 : w - 2;
				double u = Noise.unit(d.seed() ^ 0x57A4D, x, 0, z);
				int room = d.maxHeight() - 1 - y;
				switch (age) {
					case GROWN -> {
						if (room >= 6) {
							Trees.tree(d, x, y, z, Math.min(room, 7 + (int) (u * 4)), kind, bx0, z0 - 2, bx1, z1 + 1);
						}
					}
					case REGROWTH -> {
						if (u < 0.12 && room >= 7) {
							// a standard kept over the coppice
							Trees.tree(d, x, y, z, Math.min(room, 8 + (int) (u * 20)), kind, bx0, z0 - 2, bx1, z1 + 1);
						} else {
							stool(d, x, y, z, logs, u < 0.6 ? 2 : 1);
						}
					}
					case CUT -> {
						if (u < 0.1 && room >= 7) {
							Trees.tree(d, x, y, z, Math.min(room, 8 + (int) (u * 20)), kind, bx0, z0 - 2, bx1, z1 + 1);
						} else {
							try (var st = d.stage(StageKind.EXTERIOR)) {
								d.set(x, y, z, logs.log(Direction.Axis.Y));
							}
							stumps.add(new int[]{x, y, z});
						}
					}
				}
			}
		}
		if (age == Age.CUT) {
			// saplings set between the stumps, the cordwood stacked by the ride
			try (var st = d.stage(StageKind.EXTERIOR)) {
				for (int[] s : stumps) {
					int sx = s[0] + (Noise.unit(d.seed() ^ 0x5A9, s[0], 2, s[2]) < 0.5 ? -1 : 1);
					int sz = s[2] + 1;
					int y = Groundwork.surface(d, sx, sz);
					if (Math.abs(sx - ride) > 1 && sz <= z1 && y < d.maxHeight() - 1 && d.isEmpty(sx, y, sz) && Garden.groundLike(d, sx, y - 1, sz)
						&& d.site().ground(sx, sz) == y) {
						d.set(sx, y, sz, sapling.defaultBlockState());
					}
				}
			}
			cordwood(d, z0, z1, ride, logs);
		}
	}

	/** A coppice stool: a cut stump bushing out in shoots ({@code h} high). */
	private static void stool(Draft d, int x, int y, int z, Mat logs, int h) {
		BlockState leaf = d.mat(Role.TREE_LEAVES).leaves().at(x, y, z);
		if (leaf == null) {
			return;
		}
		try (var st = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, logs.log(Direction.Axis.Y));
			for (int k = 1; k <= h && y + k < d.maxHeight(); k++) {
				d.set(x, y + k, z, leaf);
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int nx = x + Dirs.dx(dir);
				int nz = z + Dirs.dz(dir);
				if (Noise.unit(d.seed() ^ 0x5700, nx, y, nz) < 0.65 && d.allowed(nx, y + 1, nz) && d.isEmpty(nx, y + 1, nz) && d.site().ground(nx, nz) <= y + 1) {
					d.set(nx, y + 1, nz, leaf);
				}
			}
		}
	}

	/** The cut wood stacked by the ride on level ground: a pile of logs along it. */
	private static void cordwood(Draft d, int z0, int z1, int ride, Mat logs) {
		for (int side : new int[]{1, -1}) {
			int x = ride + 2 * side;
			for (int z = z0; z + 2 <= z1; z++) {
				int g = d.site().ground(x, z);
				boolean ok = true;
				for (int k = 0; k < 3; k++) {
					ok &= d.site().ground(x, z + k) == g && g < d.maxHeight() - 3 && d.isEmpty(x, g, z + k) && d.isEmpty(x, g + 1, z + k) && !d.site().isWater(x, z + k);
				}
				if (ok) {
					Props.logPile(d, x, g, z, Direction.Axis.Z, 3, 1, 2, logs);
					return;
				}
			}
		}
	}

	/** Logs waiting in the yard for the carts, and a chopping block. */
	private static void yardStacks(Draft d, Pithead head, int yd, Mat logs) {
		int w = d.frontage();
		boolean block = false;
		for (int z = yd; z >= 2; z--) {
			for (int x = 1; x + 2 <= w - 2; x++) {
				if (head.free(x, z) && head.free(x + 1, z) && head.free(x + 2, z) && (head.free(x, z - 1) || head.free(x + 1, z - 1))) {
					Props.logPile(d, x, 0, z, Direction.Axis.X, 3, 1, 2, logs);
					head.reserve(x, z, x + 2, z);
					head.store(x + 1, z - 1 >= 1 && head.free(x + 1, z - 1) ? z - 1 : z, Dirs.BACK);
					if (!block && head.free(x + 3, z) && x + 3 <= w - 2) {
						Props.choppingBlock(d, x + 3, 0, z);
						head.reserve(x + 3, z, x + 3, z);
					}
					return;
				}
			}
		}
	}
}
