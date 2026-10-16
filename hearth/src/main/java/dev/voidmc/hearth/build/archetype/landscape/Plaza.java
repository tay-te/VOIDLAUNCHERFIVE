package dev.voidmc.hearth.build.archetype.landscape;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.ArchetypeCategory;
import dev.voidmc.hearth.api.ArchetypeInfo;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.SettlementTier;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.Archetype;
import dev.voidmc.hearth.build.blueprint.LocalFrame;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.landscape.Benches;
import dev.voidmc.hearth.build.kit.landscape.Lamps;
import dev.voidmc.hearth.build.kit.landscape.Manner;
import dev.voidmc.hearth.build.kit.landscape.NoticeBoards;
import dev.voidmc.hearth.build.kit.landscape.Paving;
import dev.voidmc.hearth.build.kit.landscape.Planters;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import dev.voidmc.hearth.build.terrain.Groundwork;
import dev.voidmc.hearth.build.kit.landscape.Edges;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * {@code hearth:landscape/plaza}: a paved square, the stage of town life.
 *
 * <p>The whole plot is levelled and paved in the culture's pattern (square rings or circles round a medallion, a grid
 * of bands with inlays at the crossings, a checker, rays, staggered setts, plain setts with mended patches, boards on
 * sleepers in timber cultures) inside a kerb of the band stone. What the planner has already stood on the square (the
 * well, a bell, stalls, a fountain: the {@code keep} rectangles) is left exactly as it is, framed by a ring of the band
 * stone. Round the thing at the middle go benches looking at it and the notice board; lamps stand at the corners. Where
 * nothing stands in the middle (a little square where lanes meet, or a square drawn on its own) the plaza makes its own
 * centrepiece, the one thing in the open middle (a big square adds a pair of shade trees at its back, never a grove): a
 * tree in a raised planter ringed by benches, a fire pit with log seats in the north and on the steppe, a small
 * fountain in the south, a stone lantern by a blossom tree in the karst.
 *
 * <p>The main square is the market's floor too: the planner later stands stalls, lamps, a fountain and a statue in the
 * ring two to five blocks in from its edge, so on the heart plaza that ring and the edge rows (where the doors of the
 * houses round the square open) are paved but left clear.
 *
 * <p>POIs: a MEETING_POINT before the middle, SEATs on the benches, a NOTICE_BOARD (heart plaza). Params: {@code heart}
 * (boolean: the main square, with the notice board), {@code junction} (boolean: a little square where lanes meet),
 * {@code keep} (int array of world rectangles x0,z0,x1,z1, inclusive, left as they stand); for previews,
 * {@code keep_local} (string {@code x0:z0:x1:z1/...} in local coordinates).
 */
public final class Plaza implements Archetype {
	public static final ArchetypeInfo INFO = new ArchetypeInfo(Hearth.id("landscape/plaza"), ArchetypeCategory.LANDSCAPE, Set.of(), 0,
		7, 31, 7, 31, 1, 10, SettlementTier.CAMP, Set.of(), Set.of(ArchetypeInfo.SiteFlag.CENTRAL), List.of(), 0, 1.0);

	@Override
	public ArchetypeInfo info() {
		return INFO;
	}

	/** Local rectangles {x0, z0, x1, z1} of the keep param. */
	static List<int[]> keeps(Draft d) {
		int[] k = d.params().getIntArray("keep").orElse(new int[0]);
		LocalFrame f = d.frame();
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i + 3 < k.length; i += 4) {
			int ax = f.localX(k[i], k[i + 1]);
			int az = f.localZ(k[i], k[i + 1]);
			int bx = f.localX(k[i + 2], k[i + 3]);
			int bz = f.localZ(k[i + 2], k[i + 3]);
			out.add(new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)});
		}
		// previews: local rectangles as "x0:z0:x1:z1/..." (the preview tool cannot pass int arrays)
		for (String r : d.param("keep_local", "").split("/")) {
			String[] v = r.split(":");
			if (v.length == 4) {
				try {
					out.add(new int[]{Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3])});
				} catch (NumberFormatException e) {
					// not a rectangle: ignored
				}
			}
		}
		return out;
	}

	static boolean in(int[] r, int x, int z) {
		return x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3];
	}

	/** The plaza's own idea of what its cells may hold. */
	private static final class Ground {
		final int w;
		final int dp;
		final boolean heart;
		final List<int[]> keep;
		final boolean[][] used;

		Ground(int w, int dp, boolean heart, List<int[]> keep) {
			this.w = w;
			this.dp = dp;
			this.heart = heart;
			this.keep = keep;
			this.used = new boolean[w][dp];
		}

		boolean kept(int x, int z) {
			for (int[] r : this.keep) {
				if (in(r, x, z)) {
					return true;
				}
			}
			return false;
		}

		int inset(int x, int z) {
			return Math.min(Math.min(x, z), Math.min(this.w - 1 - x, this.dp - 1 - z));
		}

		/** Whether a feature may stand here: inside, not kept, not taken, and off the heart's edge rows and market ring. */
		boolean free(int x, int z) {
			if (x < 0 || z < 0 || x >= this.w || z >= this.dp || kept(x, z) || this.used[x][z]) {
				return false;
			}
			int in = inset(x, z);
			if (this.heart && (in <= 1 || in >= 2 && in <= 5)) {
				return false;
			}
			for (int[] r : this.keep) {
				// keep a block clear round what stands there
				if (x >= r[0] - 1 && x <= r[2] + 1 && z >= r[1] - 1 && z <= r[3] + 1 && (z == r[1] - 1 && x >= r[0] && x <= r[2])) {
					return false;
				}
			}
			return in >= (this.heart ? 6 : 1);
		}

		/**
		 * Whether a cell is on the ring a block round a kept rectangle, off its front: the planner keeps a block clear
		 * round everything it stands on the square, so nothing it adds later lands there.
		 */
		boolean ringFree(int x, int z, int[] r) {
			if (x < 0 || z < 0 || x >= this.w || z >= this.dp || kept(x, z) || this.used[x][z]) {
				return false;
			}
			boolean ring = x >= r[0] - 1 && x <= r[2] + 1 && z >= r[1] - 1 && z <= r[3] + 1 && !in(r, x, z);
			boolean front = z == r[1] - 1 && x >= r[0] && x <= r[2];
			return ring && !front && inset(x, z) >= 2;
		}

		void take(int x, int z) {
			if (x >= 0 && z >= 0 && x < this.w && z < this.dp) {
				this.used[x][z] = true;
			}
		}
	}

	@Override
	public void draw(Draft d) {
		int w = d.frontage();
		int dp = d.depth();
		boolean heart = d.param("heart", false);
		boolean junction = d.param("junction", !heart);
		List<int[]> keep = keeps(d);
		Ground g = new Ground(w, dp, heart, keep);
		double ccx = (w - 1) / 2.0;
		double ccz = (dp - 1) / 2.0;
		int cx = (w - 1) / 2;
		int cz = (dp - 1) / 2;
		d.entrance(cx, 0, -1);
		Groundwork.level(d, 0, 0, w - 1, dp - 1, 0, true);
		// the floor: the pattern, a kerb round the edge, a frame round what stands there
		int period = Math.min(w, dp) >= 13 ? 4 : 3;
		boolean centreKept = g.kept(cx, cz);
		Paving.Pattern pattern = Paving.pattern(d);
		Paving.lay(d, 0, 0, w - 1, dp - 1, 0, Paving.paint(d, pattern, ccx, ccz, period, !centreKept));
		if (!Manner.wood(d.mat(Role.PAVING))) {
			Paving.kerb(d, 0, 0, w - 1, dp - 1, 0);
		}
		if (!Manner.wood(d.mat(Role.PAVING)) && pattern != Paving.Pattern.SETTS) {
			// a dotted border of the inlay just inside the kerb (a square of plain setts keeps its calm)
			Mat inlay = Paving.inlay(d);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				List<int[]> ring = Draft.ring(1, 1, w - 2, dp - 2);
				for (int i = 0; i < ring.size(); i += 4) {
					int[] c = ring.get(i);
					if (!g.kept(c[0], c[1])) {
						d.set(c[0], -1, c[1], inlay.full());
					}
				}
			}
		}
		Mat band = Paving.band(d);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] r : keep) {
				for (int[] c : Draft.ring(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1)) {
					if (c[0] >= 0 && c[1] >= 0 && c[0] < w && c[1] < dp && !g.kept(c[0], c[1])) {
						d.set(c[0], -1, c[1], band.full());
					}
				}
			}
		}
		// the middle: what stands there, or a centrepiece of the plaza's own
		int[] mid = null;
		for (int[] r : keep) {
			if (in(r, cx, cz)) {
				mid = r;
			}
		}
		int top = 1;
		if (mid == null) {
			mid = centrepiece(d, g, cx, cz);
			top = Math.max(top, mid[4]);
		}
		// benches looking at the middle on its sides and back, the notice board at its front corner
		boolean ownMiddle = mid.length > 4;
		if (ownMiddle) {
			gather(d, g, mid);
			if (heart || !junction) {
				noticeBoard(d, g, mid);
			}
		} else {
			top = Math.max(top, gatherRound(d, g, mid, heart || !junction));
		}
		d.poi(PoiType.MEETING_POINT, (mid[0] + mid[2]) / 2, 0, Math.max(0, mid[1] - (g.free((mid[0] + mid[2]) / 2, mid[1] - 2) ? 2 : 1)), Dirs.BACK);
		// lamps at the corners
		int room = Math.min(4, d.maxHeight());
		if (room >= 2) {
			for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, dp - 1}, {w - 1, dp - 1}}) {
				if (!g.kept(c[0], c[1])) {
					top = Math.max(top, Lamps.standard(d, c[0], 0, c[1], room, Manner.lamp(d), null));
					g.take(c[0], c[1]);
				}
			}
		}
		// on a big square that is not the market, a pair of shade trees in planters at the back quarters (the front and the
		// middle stay open to gather in)
		if (!heart && Math.min(w, dp) >= 15) {
			for (int[] q : new int[][]{{w / 4, dp - 1 - dp / 4}, {w - 1 - w / 4, dp - 1 - dp / 4}}) {
				if (g.free(q[0] - 1, q[1] - 1) && g.free(q[0] + 1, q[1] + 1) && g.free(q[0] - 1, q[1] + 1) && g.free(q[0] + 1, q[1] - 1)) {
					top = Math.max(top, Planters.box(d, q[0] - 1, q[1] - 1, q[0] + 1, q[1] + 1, 0, true));
					for (int z = q[1] - 1; z <= q[1] + 1; z++) {
						for (int x = q[0] - 1; x <= q[0] + 1; x++) {
							g.take(x, z);
						}
					}
				}
			}
		}
		Groundwork.clear(d, 0, 0, w - 1, dp - 1, Math.min(d.maxHeight() - 1, top + 2));
		Edges.guard(d);
		// leave what the planner stood here exactly as it is
		for (int[] r : keep) {
			for (int z = Math.max(0, r[1]); z <= Math.min(dp - 1, r[3]); z++) {
				for (int x = Math.max(0, r[0]); x <= Math.min(w - 1, r[2]); x++) {
					for (int y = -16; y < d.maxHeight(); y++) {
						d.remove(x, y, z);
					}
				}
			}
		}
	}

	/** The plaza's own centrepiece in the culture's manner. Returns its rectangle {x0, z0, x1, z1, top}. */
	private static int[] centrepiece(Draft d, Ground g, int cx, int cz) {
		int top = 1;
		int r = 1;
		switch (Manner.culture(d)) {
			case NORTHERN, TUNDRA, STEPPE -> {
				// a fire pit: a ring of stone round the fire, log seats round that
				Mat stone = Manner.masonry(d);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(cx, -1, cz, Blocks.COARSE_DIRT.defaultBlockState());
					d.set(cx, 0, cz, States.lit(Blocks.CAMPFIRE, true).trySetValue(BlockStateProperties.SIGNAL_FIRE, false));
					for (Direction dir : Dirs.HORIZONTAL) {
						d.set(cx + Dirs.dx(dir), 0, cz + Dirs.dz(dir), stone.slab(SlabType.BOTTOM));
					}
					for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
						d.set(cx + c[0], 0, cz + c[1], stone.has(Form.WALL) ? stone.wall() : stone.full());
					}
				}
				d.poi(PoiType.HEARTH, cx, 0, cz - 1, Dirs.BACK);
				top = 1;
			}
			case DESERT, MEDITERRANEAN, MESA -> {
				// a little tiled basin with a jet
				Mat tile = Paving.inlay(d);
				Mat stone = Manner.masonry(d);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int z = cz - 1; z <= cz + 1; z++) {
						for (int x = cx - 1; x <= cx + 1; x++) {
							boolean centre = x == cx && z == cz;
							d.set(x, 0, z, centre ? stone.full() : ((x + z) & 1) == 0 ? tile.full() : stone.full());
						}
					}
					if (d.maxHeight() >= 4) {
						d.set(cx, 1, cz, stone.has(Form.WALL) ? stone.wall() : stone.full());
						d.set(cx, 2, cz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
						top = 2;
					}
				}
			}
			default -> {
				// a tree in a raised planter (a stone lantern beside it in the karst)
				top = Planters.box(d, cx - 1, cz - 1, cx + 1, cz + 1, 0, true);
			}
		}
		for (int z = cz - r; z <= cz + r; z++) {
			for (int x = cx - r; x <= cx + r; x++) {
				g.take(x, z);
			}
		}
		return new int[]{cx - r, cz - r, cx + r, cz + r, top};
	}

	/** Benches a block off the middle's sides and back, looking at it (a ring of log seats round a fire). */
	private static void gather(Draft d, Ground g, int[] mid) {
		int mx = (mid[0] + mid[2]) / 2;
		int mz = (mid[1] + mid[3]) / 2;
		int[][] spots = {
			{mid[0] - 2, mz, 1}, {mid[2] + 2, mz, 3}, {mx, mid[3] + 2, 0},
			{mid[0] - 2, mz - 1, 1}, {mid[2] + 2, mz - 1, 3}, {mx - 1, mid[3] + 2, 0}
		};
		int placed = 0;
		for (int[] sp : spots) {
			if (placed >= 4 || !g.free(sp[0], sp[1])) {
				continue;
			}
			// spot[2]: the way the sitter looks, as an index into Dirs.HORIZONTAL (front, right, back, left)
			Direction looks = Dirs.HORIZONTAL[sp[2]];
			Benches.bench(d, sp[0], 0, sp[1], looks, Dirs.right(looks), 1, false);
			g.take(sp[0], sp[1]);
			placed++;
		}
	}

	/**
	 * Round something the planner stood in the middle (the well, a bell): benches on the ring a block off its sides and
	 * back looking at it, the notice board at its front-left corner turned toward the way to it, a lamp at the other
	 * front corner. Returns the lamp's top.
	 */
	private static int gatherRound(Draft d, Ground g, int[] mid, boolean board) {
		int top = 1;
		int mx = (mid[0] + mid[2]) / 2;
		int mz = (mid[1] + mid[3]) / 2;
		int[][] spots = {
			{mid[0] - 1, mz, 1}, {mid[2] + 1, mz, 3}, {mx, mid[3] + 1, 0},
			{mid[0] - 1, mz + 1, 1}, {mid[2] + 1, mz + 1, 3}, {mx - 1, mid[3] + 1, 0}, {mx + 1, mid[3] + 1, 0}
		};
		for (int[] sp : spots) {
			if (g.ringFree(sp[0], sp[1], mid)) {
				Direction looks = Dirs.HORIZONTAL[sp[2]];
				Benches.bench(d, sp[0], 0, sp[1], looks, Dirs.right(looks), 1, false);
				g.take(sp[0], sp[1]);
			}
		}
		if (board && g.ringFree(mid[0] - 1, mid[1] - 1, mid)) {
			NoticeBoards.board(d, mid[0] - 1, 0, mid[1] - 1, Dirs.RIGHT);
			g.take(mid[0] - 1, mid[1] - 1);
		} else if (board) {
			noticeBoard(d, g, mid);
		}
		if (g.ringFree(mid[2] + 1, mid[1] - 1, mid) && d.maxHeight() >= 3) {
			top = Lamps.standard(d, mid[2] + 1, 0, mid[1] - 1, Math.min(4, d.maxHeight()), Manner.lamp(d), null);
			g.take(mid[2] + 1, mid[1] - 1);
		}
		return top;
	}

	/** The notice board at a front corner of the middle, facing the way folk come in. */
	private static void noticeBoard(Draft d, Ground g, int[] mid) {
		int[][] spots = {{mid[0] - 2, mid[1] - 1}, {mid[2] + 2, mid[1] - 1}, {mid[0] - 2, mid[3] + 1}, {mid[2] + 2, mid[3] + 1}};
		for (int[] sp : spots) {
			if (g.free(sp[0], sp[1]) && g.free(sp[0], sp[1] - 1)) {
				NoticeBoards.board(d, sp[0], 0, sp[1], Dirs.FRONT);
				g.take(sp[0], sp[1]);
				g.take(sp[0], sp[1] - 1);
				return;
			}
		}
		// no room by the middle: a corner of the square, inside its edge path
		int w = g.w;
		int dp = g.dp;
		for (int[] sp : new int[][]{{1, 2}, {w - 2, 2}, {1, dp - 2}, {w - 2, dp - 2}}) {
			if (!g.kept(sp[0], sp[1]) && !g.used[sp[0]][sp[1]]) {
				NoticeBoards.board(d, sp[0], 0, sp[1], sp[0] == 1 ? Dirs.RIGHT : Dirs.LEFT);
				g.take(sp[0], sp[1]);
				return;
			}
		}
	}
}
