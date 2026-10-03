package dev.voidmc.hearth.build.kit.landscape;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * Planted trees shaped after the style's garden tree: a round-crowned broadleaf (oak), a tiered conifer (spruce), a
 * slender birch, a palm with drooping fronds, a wide blossom tree with a forked trunk (cherry, wisteria), a gnarled
 * olive, a flat-topped acacia or baobab, a willow hung with vines (bayou). Crowns are lumpy by position-seeded noise
 * so two trees of a kind never match, kept inside a given rectangle and the plot's height.
 */
public final class Trees {
	private Trees() {
	}

	public enum Kind {
		BROAD, CONIFER, BIRCH, PALM, BLOSSOM, OLIVE, ACACIA, WILLOW
	}

	/** The shape that suits the style's tree. */
	public static Kind kind(Draft d) {
		String log = Manner.path(d.mat(Role.TREE_LOG), Form.LOG);
		String leaves = Manner.path(d.mat(Role.TREE_LEAVES), Form.FULL);
		if (log.contains("spruce") || log.contains("pine") || log.contains("fir") || log.contains("larch")) {
			return Kind.CONIFER;
		}
		if (log.contains("birch") || log.contains("aspen") || log.contains("poplar")) {
			return Kind.BIRCH;
		}
		if (log.contains("palm") || log.contains("jungle") && leaves.contains("jungle")) {
			return Kind.PALM;
		}
		if (log.contains("cherry") || log.contains("wisteria") || log.contains("maple")) {
			return Kind.BLOSSOM;
		}
		if (log.contains("olive") || leaves.contains("azalea")) {
			return Kind.OLIVE;
		}
		if (log.contains("acacia") || log.contains("baobab")) {
			return Kind.ACACIA;
		}
		if (log.contains("mangrove") || log.contains("willow")) {
			return Kind.WILLOW;
		}
		return Kind.BROAD;
	}

	/** Context for one tree: where it may reach. */
	private record Reach(Draft d, int bx0, int bz0, int bx1, int bz1) {
		boolean ok(int x, int y, int z) {
			return x >= Math.max(0, this.bx0) && x <= Math.min(this.d.frontage() - 1, this.bx1) && z >= Math.max(0, this.bz0)
				&& z <= Math.min(this.d.depth() - 1, this.bz1) && y < this.d.maxHeight();
		}
	}

	/**
	 * A tree with its trunk's foot at (x, y, z) (y the standing level on its soil), at most {@code height} blocks tall
	 * and inside {@code [bx0..bx1] × [bz0..bz1]}. Returns the highest y drawn.
	 */
	public static int tree(Draft d, int x, int y, int z, int height, Kind kind, int bx0, int bz0, int bx1, int bz1) {
		Reach r = new Reach(d, bx0, bz0, bx1, bz1);
		height = Math.min(height, d.maxHeight() - y);
		if (height < 3) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x, y, z, d.mat(Role.HEDGE).leaves());
			}
			return y;
		}
		Mat logM = d.mat(Role.TREE_LOG);
		Mat leafM = d.mat(Role.TREE_LEAVES);
		Paint log = logM.log(Direction.Axis.Y).orElse(logM.full());
		Paint leaf = leafM.leaves();
		long seed = d.seed() ^ Noise.hash(x * 31L + z, 0x7EE);
		int top;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			top = switch (kind) {
				case CONIFER -> conifer(d, r, x, y, z, height, log, leaf, seed);
				case BIRCH -> birch(d, r, x, y, z, height, log, leaf, seed);
				case PALM -> palm(d, r, x, y, z, height, logM, leaf, seed);
				case BLOSSOM -> blossom(d, r, x, y, z, height, logM, leaf, seed);
				case OLIVE -> olive(d, r, x, y, z, height, logM, leaf, seed);
				case ACACIA -> acacia(d, r, x, y, z, height, logM, leaf, seed);
				case WILLOW -> willow(d, r, x, y, z, height, logM, leaf, seed);
				default -> broad(d, r, x, y, z, height, logM, leaf, seed);
			};
		}
		return top;
	}

	private static void leafAt(Draft d, Reach r, int x, int y, int z, Paint leaf) {
		if (r.ok(x, y, z) && d.isAbsent(x, y, z)) {
			d.set(x, y, z, leaf);
		}
	}

	private static void logAt(Draft d, Reach r, int x, int y, int z, Paint log) {
		if (r.ok(x, y, z)) {
			d.set(x, y, z, log);
		}
	}

	/** An ellipsoid of leaves, ragged at its edge. */
	private static void blob(Draft d, Reach r, double cx, double cy, double cz, double rh, double rv, Paint leaf, long seed, double ragged) {
		for (int yy = (int) Math.floor(cy - rv); yy <= (int) Math.ceil(cy + rv); yy++) {
			for (int zz = (int) Math.floor(cz - rh); zz <= (int) Math.ceil(cz + rh); zz++) {
				for (int xx = (int) Math.floor(cx - rh); xx <= (int) Math.ceil(cx + rh); xx++) {
					double dx = (xx - cx) / (rh + 0.4);
					double dy = (yy - cy) / (rv + 0.4);
					double dz = (zz - cz) / (rh + 0.4);
					double e = dx * dx + dy * dy + dz * dz;
					double n = Noise.unit(seed, xx, yy, zz);
					if (e <= 1 - ragged + ragged * 2 * n * (e > 0.55 ? 1 : 0.2)) {
						leafAt(d, r, xx, yy, zz, leaf);
					}
				}
			}
		}
	}

	private static int broad(Draft d, Reach r, int x, int y, int z, int h, Mat logM, Paint leaf, long seed) {
		Paint log = logM.log(Direction.Axis.Y).orElse(logM.full());
		int trunk = Math.max(2, (int) Math.round(h * 0.42));
		double rh = Math.clamp(h / 2.6, 1.6, 3.2);
		double rv = Math.max(1.5, (h - trunk) / 2.0);
		double cy = y + h - rv - 0.5;
		for (int k = 0; k < Math.min(h - 1, (int) Math.round(cy - y) + 1); k++) {
			logAt(d, r, x, y + k, z, log);
		}
		// a bough or two leaving the trunk under the crown
		int boughs = 1 + (int) (Noise.unit(seed, 1, 2, 3) * 2);
		for (int b = 0; b < boughs; b++) {
			Direction dir = Dirs.HORIZONTAL[(int) (Noise.unit(seed, b, 7, 1) * 4) % 4];
			int by = y + trunk - 1 + b;
			logAt(d, r, x + Dirs.dx(dir), by, z + Dirs.dz(dir), logM.log(dir.getAxis()).orElse(logM.full()));
			blob(d, r, x + Dirs.dx(dir) * 2, by + 1, z + Dirs.dz(dir) * 2, Math.max(1.2, rh - 1), 1.2, leaf, seed ^ b, 0.35);
		}
		blob(d, r, x + 0.0, cy, z + 0.0, rh, rv, leaf, seed, 0.3);
		return Math.min(d.maxHeight() - 1, (int) Math.ceil(cy + rv));
	}

	private static int conifer(Draft d, Reach r, int x, int y, int z, int h, Paint log, Paint leaf, long seed) {
		int topY = y + h - 1;
		for (int yy = y; yy < topY; yy++) {
			logAt(d, r, x, yy, z, log);
		}
		int start = y + Math.max(1, h / 5);
		int maxR = h >= 8 ? 3 : 2;
		for (int yy = start; yy <= topY; yy++) {
			double t = (double) (topY - yy) / Math.max(1, topY - start);
			int rad = (int) Math.round(t * maxR + ((topY - yy) % 2 == 0 ? 0.4 : -0.3));
			rad = Math.clamp(rad, yy >= topY - 1 ? 0 : 1, maxR);
			for (int dz = -rad; dz <= rad; dz++) {
				for (int dx = -rad; dx <= rad; dx++) {
					double dd = Math.sqrt(dx * dx + dz * dz);
					if (dd > rad + 0.3 || dd > rad - 0.4 && Noise.unit(seed, x + dx, yy, z + dz) < 0.35) {
						continue;
					}
					leafAt(d, r, x + dx, yy, z + dz, leaf);
				}
			}
		}
		leafAt(d, r, x, topY, z, leaf);
		return topY;
	}

	private static int birch(Draft d, Reach r, int x, int y, int z, int h, Paint log, Paint leaf, long seed) {
		int topY = y + h - 1;
		for (int yy = y; yy < topY; yy++) {
			logAt(d, r, x, yy, z, log);
		}
		double rh = h >= 7 ? 1.8 : 1.3;
		blob(d, r, x, topY - 1.5, z, rh, Math.max(1.5, h / 3.5), leaf, seed, 0.3);
		leafAt(d, r, x, topY, z, leaf);
		return topY;
	}

	private static int palm(Draft d, Reach r, int x, int y, int z, int h, Mat logM, Paint leaf, long seed) {
		Paint log = logM.log(Direction.Axis.Y).orElse(logM.full());
		int trunk = Math.max(2, h - 2);
		// a gentle lean: the trunk steps over once, halfway up
		Direction lean = Dirs.HORIZONTAL[(int) (Noise.unit(seed, 3, 3, 3) * 4) % 4];
		int tx = x;
		int tz = z;
		for (int k = 0; k < trunk; k++) {
			if (k == trunk / 2 + 1 && h >= 6 && r.ok(tx + Dirs.dx(lean), y + k, tz + Dirs.dz(lean))) {
				logAt(d, r, tx + Dirs.dx(lean), y + k - 1, tz + Dirs.dz(lean), log);
				tx += Dirs.dx(lean);
				tz += Dirs.dz(lean);
			}
			logAt(d, r, tx, y + k, tz, log);
		}
		int cy = y + trunk;
		leafAt(d, r, tx, cy, tz, leaf);
		int arm = h >= 7 ? 3 : 2;
		for (Direction dir : Dirs.HORIZONTAL) {
			for (int k = 1; k <= arm; k++) {
				int yy = k == arm ? cy - 1 : cy;
				leafAt(d, r, tx + Dirs.dx(dir) * k, yy, tz + Dirs.dz(dir) * k, leaf);
			}
		}
		for (int[] diag : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			leafAt(d, r, tx + diag[0], cy, tz + diag[1], leaf);
			if (arm >= 3) {
				leafAt(d, r, tx + diag[0] * 2, cy - 1, tz + diag[1] * 2, leaf);
			}
		}
		if (cy + 1 < d.maxHeight()) {
			leafAt(d, r, tx, cy + 1, tz, leaf);
			return cy + 1;
		}
		return cy;
	}

	private static int blossom(Draft d, Reach r, int x, int y, int z, int h, Mat logM, Paint leaf, long seed) {
		Paint log = logM.log(Direction.Axis.Y).orElse(logM.full());
		int trunk = Math.max(2, h / 2 - 1);
		for (int k = 0; k < trunk; k++) {
			logAt(d, r, x, y + k, z, log);
		}
		// the trunk forks into two limbs leaning apart
		Direction a = Dirs.HORIZONTAL[(int) (Noise.unit(seed, 5, 1, 5) * 4) % 4];
		Direction b = a.getOpposite();
		int fy = y + trunk;
		for (Direction dir : new Direction[]{a, b}) {
			logAt(d, r, x + Dirs.dx(dir), fy, z + Dirs.dz(dir), log);
			if (h >= 6) {
				logAt(d, r, x + Dirs.dx(dir), fy + 1, z + Dirs.dz(dir), log);
			}
		}
		double rh = Math.clamp(h / 2.2, 2.0, 3.4);
		double cy = Math.min(y + h - 1.6, fy + 1.5);
		blob(d, r, x, cy, z, rh, Math.max(1.3, (y + h - 1 - cy)), leaf, seed, 0.35);
		// petals drooping below the crown's rim
		for (int dz = -3; dz <= 3; dz++) {
			for (int dx = -3; dx <= 3; dx++) {
				int lx = x + dx;
				int lz = z + dz;
				int ly = (int) Math.floor(cy - 1);
				if (Math.hypot(dx, dz) > rh - 0.8 && Math.hypot(dx, dz) <= rh + 0.4 && !d.isAbsent(lx, ly + 1, lz)
					&& Noise.unit(seed, lx, ly, lz) < 0.45) {
					leafAt(d, r, lx, ly, lz, leaf);
				}
			}
		}
		return Math.min(d.maxHeight() - 1, (int) Math.ceil(cy + 1.5));
	}

	private static int olive(Draft d, Reach r, int x, int y, int z, int h, Mat logM, Paint leaf, long seed) {
		Paint log = logM.log(Direction.Axis.Y).orElse(logM.full());
		Paint wood = logM.has(Form.WOOD) ? logM.wood() : log;
		int trunk = Math.max(2, h / 2);
		Direction kink = Dirs.HORIZONTAL[(int) (Noise.unit(seed, 2, 9, 4) * 4) % 4];
		logAt(d, r, x, y, z, wood);
		int tx = x;
		int tz = z;
		for (int k = 1; k < trunk; k++) {
			if (k == 1) {
				logAt(d, r, x + Dirs.dx(kink), y + 1, z + Dirs.dz(kink), wood);
				tx = x + Dirs.dx(kink);
				tz = z + Dirs.dz(kink);
				continue;
			}
			logAt(d, r, tx, y + k, tz, log);
		}
		double cy = y + trunk + 0.5;
		blob(d, r, tx, cy, tz, Math.clamp(h / 2.5, 1.6, 2.6), Math.max(1.2, (h - trunk) / 2.0), leaf, seed, 0.5);
		return Math.min(d.maxHeight() - 1, (int) Math.ceil(cy + 1.5));
	}

	private static int acacia(Draft d, Reach r, int x, int y, int z, int h, Mat logM, Paint leaf, long seed) {
		Paint log = logM.log(Direction.Axis.Y).orElse(logM.full());
		int trunk = Math.max(2, h - 2);
		for (int k = 0; k < trunk; k++) {
			logAt(d, r, x, y + k, z, log);
		}
		Direction a = Dirs.HORIZONTAL[(int) (Noise.unit(seed, 8, 8, 1) * 4) % 4];
		int cy = y + trunk;
		// two limbs out to a flat umbrella each
		for (Direction dir : new Direction[]{a, a.getOpposite()}) {
			int lx = x + Dirs.dx(dir);
			int lz = z + Dirs.dz(dir);
			logAt(d, r, lx, cy - 1, lz, log);
			for (int dz = -2; dz <= 2; dz++) {
				for (int dx = -2; dx <= 2; dx++) {
					if (Math.abs(dx) + Math.abs(dz) > 3 || Math.abs(dx) + Math.abs(dz) == 3 && Noise.unit(seed, lx + dx, cy, lz + dz) < 0.5) {
						continue;
					}
					leafAt(d, r, lx + dx, cy, lz + dz, leaf);
				}
			}
		}
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (Math.abs(dx) + Math.abs(dz) <= 1 && cy + 1 < d.maxHeight()) {
					leafAt(d, r, x + dx, cy + 1, z + dz, leaf);
				}
			}
		}
		return Math.min(d.maxHeight() - 1, cy + 1);
	}

	private static int willow(Draft d, Reach r, int x, int y, int z, int h, Mat logM, Paint leaf, long seed) {
		Paint log = logM.log(Direction.Axis.Y).orElse(logM.full());
		int trunk = Math.max(2, h - 3);
		for (int k = 0; k < trunk; k++) {
			logAt(d, r, x, y + k, z, log);
		}
		double rh = Math.clamp(h / 2.3, 2.0, 3.2);
		double cy = y + trunk + 0.3;
		blob(d, r, x, cy, z, rh, 1.4, leaf, seed, 0.3);
		// moss hanging from the crown's rim: under the lowest leaf of a column, a strand one to three long
		int rim = (int) Math.ceil(rh);
		for (int dz = -rim; dz <= rim; dz++) {
			for (int dx = -rim; dx <= rim; dx++) {
				int lx = x + dx;
				int lz = z + dz;
				if (Math.hypot(dx, dz) < rh - 1.2 || Noise.unit(seed, lx, 0, lz) >= 0.55) {
					continue;
				}
				int lowest = Integer.MIN_VALUE;
				for (int ly = y + 1; ly <= (int) Math.ceil(cy + 2); ly++) {
					if (d.get(lx, ly, lz) != null && d.get(lx, ly, lz).getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
						lowest = ly;
						break;
					}
				}
				if (lowest == Integer.MIN_VALUE) {
					continue;
				}
				int len = 1 + (int) (Noise.unit(seed ^ 0xA, lx, lowest, lz) * 3);
				int k = 1;
				while (k <= len && lowest - k > d.site().ground(lx, lz) + 1 && r.ok(lx, lowest - k, lz) && d.isAbsent(lx, lowest - k, lz)) {
					k++;
				}
				for (int j = 1; j < k; j++) {
					d.set(lx, lowest - j, lz, Blocks.PALE_HANGING_MOSS.defaultBlockState().trySetValue(net.minecraft.world.level.block.HangingMossBlock.TIP, j == k - 1));
				}
			}
		}
		return Math.min(d.maxHeight() - 1, (int) Math.ceil(cy + 1.4));
	}
}
