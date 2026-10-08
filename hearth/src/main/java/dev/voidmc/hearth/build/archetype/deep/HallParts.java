package dev.voidmc.hearth.build.archetype.deep;

import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.deep.Glow;
import dev.voidmc.hearth.build.kit.deep.Rock;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import java.util.List;
import net.minecraft.world.level.block.Blocks;

/** What every deep hall shares outside its walls, whatever its culture's plan: the paved apron, its lamps, the notice board. */
final class HallParts {
	private HallParts() {
	}

	/**
	 * Fills a rasterised cap (a dome, an onion) solid from {@code fromY} up under its surface, so its shell stands on what
	 * is under it rather than on its own rim.
	 */
	static void solid(Draft d, dev.voidmc.hearth.build.kit.Roofs.Roof r, int fromY, dev.voidmc.hearth.build.draft.Paint fill) {
		try (var s = d.stage(StageKind.ROOF)) {
			for (int z = r.z0; z <= r.z1; z++) {
				for (int x = r.x0; x <= r.x1; x++) {
					if (!r.covers(x, z)) {
						continue;
					}
					for (int y = fromY; y < r.surfaceY(x, z); y++) {
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, fill);
						}
					}
				}
			}
		}
	}

	/** The apron levelled and paved in front of the building's front row {@code zf}. */
	static void ground(Draft d, int zf) {
		Groundwork.level(d, 0, 0, d.frontage() - 1, Math.max(1, zf - 1), 0, true);
		Mat paving = d.mat(Role.PAVING);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = 0; z < zf; z++) {
				for (int x = 0; x < d.frontage(); x++) {
					d.set(x, -1, z, paving.full());
				}
			}
		}
	}

	/**
	 * A lamp post either side of the way in, {@code off} from its middle {@code c}, on the street row; the notice board
	 * on the right one's outer face.
	 */
	static void apron(Draft d, int c, Glow g, int off) {
		for (int x : new int[]{c - off, c + off}) {
			if (x >= 0 && x < d.frontage() && d.isAbsent(x, 0, 0)) {
				Rock.lampPost(d, x, 0, 0, Noise.unit(d.seed(), x, 0, 0) < 0.5, g);
			}
		}
		int nx = c + off + 1;
		if (nx < d.frontage() && d.isAbsent(nx, 1, 0) && !d.isAbsent(nx - 1, 1, 0)) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(nx, 1, 0, States.facing(d.mat(Role.FURNITURE).mainOr(Form.WALL_SIGN, Blocks.SPRUCE_WALL_SIGN), Dirs.RIGHT),
					BlockEntities.sign(List.of("", "Moot today", "at the bell", ""), "black", false));
			}
			d.poi(PoiType.NOTICE_BOARD, nx, 0, 0, Dirs.LEFT);
		}
	}

	/**
	 * Where a flight {@code width} wide up from the street row starts: at {@code x} unless the street before some of its
	 * columns falls away (a plot on a ledge), then at the nearest place within {@code min..max} (its first column) where
	 * the street before every column is the street, not a drop.
	 */
	static int flightX(Draft d, int x, int width, int min, int max) {
		int best = x;
		int bestOff = Integer.MAX_VALUE;
		for (int s = min; s <= max; s++) {
			boolean ok = true;
			for (int k = 0; k < width && ok; k++) {
				ok = streetAt(d, s + k);
			}
			if (ok && Math.abs(s - x) < bestOff) {
				best = s;
				bestOff = Math.abs(s - x);
			}
		}
		return best;
	}

	/**
	 * Which cells of the street row may take a kerb against a fall before them. The street runs before the plot at its
	 * floor (the planner's), so a fall there is the land's only where some of the street is there (three or more
	 * columns: a plot on a ledge, no water before it); a front falling away all along is the street's embankment to
	 * come. And the way from the nearest of the street's columns along the row to the way in (a flight's foot, or the
	 * main door) stays open.
	 */
	static boolean[] frontKerbs(Draft d) {
		int w = d.frontage();
		boolean[] ok = new boolean[w];
		int n = 0;
		for (int x = 0; x < w; x++) {
			n += streetAt(d, x) ? 1 : 0;
			if (d.site().isWater(x, -1)) {
				// a waterfront: the quay or the bridge to come is the street's
				return ok;
			}
		}
		if (n < 3) {
			return ok;
		}
		// the way in: the foot of a flight on the street row where there is one (a raised hall's), else the main door
		int ex = d.entrances().isEmpty() ? w / 2 : Math.clamp(d.entrances().get(0)[0], 0, w - 1);
		for (int x = 0; x < w; x++) {
			net.minecraft.world.level.block.state.BlockState st = d.get(x, 0, 0);
			if (st != null && st.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
				ex = x;
				break;
			}
		}
		int near = -1;
		for (int x = 0; x < w; x++) {
			if (streetAt(d, x) && (near < 0 || Math.abs(x - ex) < Math.abs(near - ex))) {
				near = x;
			}
		}
		for (int x = 0; x < w; x++) {
			ok[x] = x < Math.min(near, ex) || x > Math.max(near, ex);
		}
		return ok;
	}

	/** The street row before column x lies at the floor (within two below it), or is water the street will cross. */
	static boolean streetAt(Draft d, int x) {
		return d.site().ground(x, -1) > -3 || d.site().isWater(x, -1);
	}

	/** Whether the land just outside the plot's sides or back stands {@code y} or more over the floor somewhere. */
	static boolean banked(Draft d, int y) {
		int w = d.frontage();
		int dp = d.depth();
		return Math.max(Math.max(d.site().maxGround(-1, 0, -1, dp - 1), d.site().maxGround(w, 0, w, dp - 1)), d.site().maxGround(0, dp, w - 1, dp)) >= y;
	}

	/**
	 * The hill cut back inside the plot: natural ground standing over the floor where nothing was built is dug away to
	 * the floor, so the hall stands in its own cutting with a clear way round it and no bank of the hillside runs up to
	 * its eaves (where the folk would walk out onto the roofs).
	 */
	static void cut(Draft d, int top) {
		top = Math.min(top, d.maxHeight() - 1);
		try (var s = d.stage(StageKind.EARTHWORK)) {
			for (int z = 0; z < d.depth(); z++) {
				for (int x = 0; x < d.frontage(); x++) {
					int g = d.site().ground(x, z);
					for (int y = 0; y < g && y <= top; y++) {
						if (d.isAbsent(x, y, z)) {
							d.set(x, y, z, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}
	}

	/**
	 * A kerb wherever the ground about the hall ends at a fall: every open cell on the ground or a platform (natural ground
	 * or a full block under it, no higher than {@code maxY}) beside the land three or more below gets a coping of the
	 * foundation's wall (or the railing), so the apron, the way round and the ground under a raised hall never end at an
	 * open drop. Drops into a cut of the building's own (a stair well, a pool) are its own business: only a fall to the
	 * natural ground counts.
	 */
	static void kerb(Draft d, int maxY) {
		Mat found = d.mat(Role.FOUNDATION);
		Mat rail = d.mat(Role.RAILING).has(Form.FENCE) ? d.mat(Role.RAILING) : d.mat(Role.FURNITURE);
		dev.voidmc.hearth.build.draft.Paint coping = found.has(Form.WALL) ? found.wall() : rail.fence();
		// the street row: the street runs before the plot at its floor (the planner's), so a fall there is only the
		// land's where some of the street is there to be seen (a plot on a ledge); a front falling away all along is the
		// street's embankment to come, and kerbing it would shut the way in
		boolean[] front = frontKerbs(d);
		java.util.List<int[]> at = new java.util.ArrayList<>();
		for (int z = 0; z < d.depth(); z++) {
			for (int x = 0; x < d.frontage(); x++) {
				for (int y = -3; y <= maxY; y++) {
					if (!d.allowed(x, y, z) || !open(d, x, y, z) || !open(d, x, y + 1, z) || !footing(d, x, y - 1, z)) {
						continue;
					}
					for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						if ((z + o[1] >= 0 || front[x]) && fall(d, x + o[0], y, z + o[1]) >= 3) {
							at.add(new int[]{x, y, z});
							break;
						}
					}
				}
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : at) {
				if (d.isEmpty(c[0], c[1], c[2])) {
					d.set(c[0], c[1], c[2], coping);
				}
			}
		}
	}

	/** Open to stand in: nothing built (or only what one walks through: a sign, a torch) and above the natural ground. */
	private static boolean open(Draft d, int x, int y, int z) {
		net.minecraft.world.level.block.state.BlockState s = d.get(x, y, z);
		return s == null ? y >= d.site().ground(x, z)
			: s.isAir() || s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty();
	}

	/**
	 * Something to stand on: the natural ground, a full block built, or on the ground course a slab (higher up a slab is
	 * a roof's, and roofs get no kerb; nor does a flight's tread, which would end the flight at a wall).
	 */
	private static boolean footing(Draft d, int x, int y, int z) {
		net.minecraft.world.level.block.state.BlockState s = d.get(x, y, z);
		if (s == null) {
			return y < d.site().ground(x, z);
		}
		var empty = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
		return s.isCollisionShapeFullBlock(empty, net.minecraft.core.BlockPos.ZERO)
			|| y <= 0 && s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock;
	}

	/** How far someone stepping into (x, z) at level y falls to the natural ground (0 where it is not open or not natural). */
	private static int fall(Draft d, int x, int y, int z) {
		boolean in = x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
		if (in && (!open(d, x, y, z) || !open(d, x, y + 1, z))) {
			return 0;
		}
		int n = 0;
		for (int yy = y - 1; yy > y - 8; yy--) {
			if (in) {
				net.minecraft.world.level.block.state.BlockState s = d.get(x, yy, z);
				if (s != null) {
					if (!s.isAir()) {
						// a fall onto something built is the building's own (its pit, its stair well, its pool)
						return 0;
					}
					n++;
					continue;
				}
			}
			if (yy < d.site().ground(x, z)) {
				return d.site().isWater(x, z) ? 0 : n;
			}
			n++;
		}
		return n;
	}
}
