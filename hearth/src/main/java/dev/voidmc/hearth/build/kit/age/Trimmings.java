package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.jspecify.annotations.Nullable;

/**
 * The pieces a building is dressed with for a day, put on a {@link BuiltView} without touching it: garlands of leaves
 * and blossom over the door, banners either side of it, lanterns strung under the front eave, pots of flowers on the
 * sills, a cloth runner at the threshold, striped awnings over the ground-floor openings, goods set out before the shop,
 * snow on every ledge and in the yard. Each piece goes only into cells the building leaves free, rests on or hangs from
 * something of the building's, and keeps doorways and the way in clear. Pieces are kept in placing order (supports
 * first).
 */
public final class Trimmings {
	/** One piece in the plot's local frame. */
	public record Piece(int x, int y, int z, BlockState state, @Nullable CompoundTag tag) {
	}

	private final BuiltView v;
	private final long seed;
	private final List<Piece> pieces = new ArrayList<>();
	private final LongOpenHashSet taken = new LongOpenHashSet();
	/** Cells kept clear: doorways, the cells in and out of every door, the entrances. */
	private final LongOpenHashSet clear = new LongOpenHashSet();

	public Trimmings(BuiltView v, long seed) {
		this.v = v;
		this.seed = seed;
		for (int[] e : v.entrances()) {
			for (int dy = 0; dy <= 1; dy++) {
				this.clear.add(Draft.key(e[0], e[1] + dy, e[2]));
			}
		}
		for (BuiltView.Door d : v.doors()) {
			for (int dy = 0; dy <= 1; dy++) {
				this.clear.add(Draft.key(d.x(), d.y() + dy, d.z()));
				this.clear.add(Draft.key(d.x() + Dirs.dx(d.out()), d.y() + dy, d.z() + Dirs.dz(d.out())));
				this.clear.add(Draft.key(d.x() - Dirs.dx(d.out()), d.y() + dy, d.z() - Dirs.dz(d.out())));
			}
		}
	}

	public List<Piece> pieces() {
		return this.pieces;
	}

	private double unit(long salt, int x, int y, int z) {
		return Noise.unit(this.seed ^ salt, x, y, z);
	}

	/** Whether a piece may go in a cell: the building leaves it free, nothing else has taken it, it is not kept clear. */
	public boolean open(int x, int y, int z) {
		long k = Draft.key(x, y, z);
		return !this.taken.contains(k) && !this.clear.contains(k) && this.v.free(x, y, z);
	}

	private boolean put(int x, int y, int z, BlockState s, @Nullable CompoundTag tag) {
		if (!open(x, y, z)) {
			return false;
		}
		this.taken.add(Draft.key(x, y, z));
		this.pieces.add(new Piece(x, y, z, s, tag));
		return true;
	}

	private boolean takenByUs(int x, int y, int z) {
		return this.taken.contains(Draft.key(x, y, z));
	}

	// ------------------------------------------------------------------------------------------------ colours

	public static Block dyed(DyeColor c, String suffix) {
		Identifier id = Identifier.withDefaultNamespace(c.getSerializedName() + suffix);
		return BuiltInRegistries.BLOCK.getValue(id);
	}

	// ------------------------------------------------------------------------------------------------ pieces

	/**
	 * A garland over a door: leaves and blossom along the head of the doorway and down its jambs to lintel height, on the
	 * outside face. {@code bloom} is the share of flowering leaves.
	 */
	public void garland(BuiltView.Door d, Block leaves, Block flowering, double bloom) {
		Direction out = d.out();
		Direction along = out.getClockWise();
		int ox = Dirs.dx(out);
		int oz = Dirs.dz(out);
		int fx = d.x() + ox;
		int fz = d.z() + oz;
		int[][] cells = {{0, 2}, {-1, 2}, {1, 2}, {-1, 1}, {1, 1}, {-2, 2}, {2, 2}};
		for (int k = 0; k < cells.length; k++) {
			int[] c = cells[k];
			int x = fx + Dirs.dx(along) * c[0];
			int z = fz + Dirs.dz(along) * c[0];
			int y = d.y() + c[1];
			// against the wall: the cell behind must be the building's
			int bx = x - ox;
			int bz = z - oz;
			BlockState behind = this.v.get(bx, y, bz);
			if (behind == null || behind.isAir()) {
				continue;
			}
			if (k >= 5 && unit(0x6A70, x, y, z) < 0.5) {
				continue;
			}
			Block b = unit(0x6A71, x, y, z) < bloom ? flowering : leaves;
			put(x, y, z, States.leaves(b), null);
		}
	}

	/** Banners either side of a door on its outside wall, a block clear of the jambs (the garland's place) when it can. */
	public void doorBanners(BuiltView.Door d, DyeColor base, @Nullable DyeColor pattern, String patternId) {
		Direction out = d.out();
		Direction along = out.getClockWise();
		for (int side : new int[]{-1, 1}) {
			for (int off : new int[]{2, 1, 3}) {
				int x = d.x() + Dirs.dx(out) + Dirs.dx(along) * side * off;
				int z = d.z() + Dirs.dz(out) + Dirs.dz(along) * side * off;
				int y = d.y() + 2;
				if (banner(x, y, z, out, base, pattern, patternId)) {
					break;
				}
			}
		}
	}

	/** A wall banner at a cell, facing {@code out}, if the wall behind it is the building's and the cell below is free. */
	public boolean banner(int x, int y, int z, Direction out, DyeColor base, @Nullable DyeColor pattern, String patternId) {
		if (!this.v.solid(x - Dirs.dx(out), y, z - Dirs.dz(out)) || !open(x, y, z) || !this.v.free(x, y - 1, z) && !takenByUs(x, y - 1, z)) {
			return false;
		}
		Block b = dyed(base, "_wall_banner");
		List<String[]> layers = new ArrayList<>();
		if (pattern != null) {
			layers.add(new String[]{patternId, pattern.getSerializedName()});
			layers.add(new String[]{"border", pattern.getSerializedName()});
		}
		return put(x, y, z, b.defaultBlockState().setValue(WallBannerBlock.FACING, out), dev.voidmc.hearth.build.draft.BlockEntities.banner(layers));
	}

	/**
	 * Banners down the front between the upper windows: on every other bay of wall at the height of the window heads of
	 * the upper storeys, in alternating colours.
	 */
	public void frontBanners(DyeColor a, DyeColor b, String patternId) {
		int n = 0;
		for (int x = 1; x < this.v.frontage() - 1; x++) {
			for (int y = 5; y < Math.min(this.v.maxHeight() - 1, 24); y++) {
				int zf = face(x, y);
				if (zf == Integer.MIN_VALUE || !this.v.wall(x, y, zf)) {
					continue;
				}
				// a bay of blank wall between windows: no glass beside, wall below too
				if (this.v.glass(x, y, zf) || this.v.glass(x, y - 1, zf) || !this.v.wall(x, y - 1, zf)) {
					continue;
				}
				boolean windowNear = this.v.glass(x - 1, y - 1, zf) || this.v.glass(x + 1, y - 1, zf) || this.v.glass(x - 1, y, zf) || this.v.glass(x + 1, y, zf);
				if (!windowNear || (x + (int) (this.seed & 1)) % 2 != 0) {
					continue;
				}
				DyeColor c = n % 2 == 0 ? a : b;
				if (banner(x, y, zf - 1, Dirs.FRONT, c, n % 2 == 0 ? b : a, patternId)) {
					n++;
					break;
				}
			}
		}
	}

	/** The z of the front face of the building at a column and height (its first wall block from the street), or MIN. */
	public int face(int x, int y) {
		for (int z = 0; z < this.v.depth(); z++) {
			if (this.v.wall(x, y, z) || this.v.glass(x, y, z) || this.v.get(x, y, z) != null && this.v.get(x, y, z).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				return z;
			}
			BlockState s = this.v.get(x, y, z);
			if (s != null && !s.isAir() && !this.v.free(x, y, z)) {
				// a porch post, a sill, a step: not the face yet, but keep looking
				continue;
			}
		}
		return Integer.MIN_VALUE;
	}

	/**
	 * Lanterns strung under the front eave: hung from the underside of the roof where it oversails the front wall, every
	 * second or third column, with a clear cell below so they never sit in a window.
	 */
	public int eaveLanterns(int every) {
		int w = this.v.frontage();
		int[][] spot = new int[w][];
		for (int x = 0; x < w; x++) {
			for (int z = -1; z < this.v.depth() && spot[x] == null; z++) {
				for (int y = 3; y < Math.min(this.v.maxHeight() - 1, 30); y++) {
					BlockState above = this.v.get(x, y + 1, z);
					if (above == null || above.isAir() || !hangsFrom(above)) {
						continue;
					}
					// the underside of an eave, a jetty or a balcony over open air in front of a wall
					if (open(x, y, z) && this.v.free(x, y - 1, z) && !takenByUs(x, y - 1, z) && wallBehind(x, y, z)) {
						spot[x] = new int[]{x, y, z};
					}
					break;
				}
			}
		}
		int placed = 0;
		int last = Integer.MIN_VALUE / 2;
		int start = (int) Math.floorMod(Noise.hash(this.seed, 0x1A47), every);
		for (int x = start; x < w; x++) {
			if (spot[x] == null || x - last < every) {
				continue;
			}
			if (put(x, spot[x][1], spot[x][2], Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), null)) {
				placed++;
				last = x;
			}
		}
		return placed;
	}

	/** Whether a lantern can hang from under a block: a whole underside (a full block, a bottom stair or slab). */
	private static boolean hangsFrom(BlockState s) {
		if (s.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
			return s.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) != net.minecraft.world.level.block.state.properties.SlabType.TOP;
		}
		if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
			return s.getValue(net.minecraft.world.level.block.StairBlock.HALF) == Half.BOTTOM;
		}
		return s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
			&& !(s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock);
	}

	private boolean wallBehind(int x, int y, int z) {
		for (int k = 1; k <= 2; k++) {
			if (this.v.wall(x, y, z + k) || this.v.wall(x, y - 1, z + k) || this.v.glass(x, y - 1, z + k)) {
				return true;
			}
		}
		return false;
	}

	/** The window sills on the front: the cell before a pane or glass with a whole top under it (a sill or a ledge). */
	public List<int[]> sills() {
		List<int[]> out = new ArrayList<>();
		for (var k : this.v.keys()) {
			int x = Draft.keyX(k);
			int y = Draft.keyY(k);
			int z = Draft.keyZ(k);
			if (!this.v.glass(x, y, z) || this.v.glass(x, y - 1, z)) {
				continue;
			}
			for (Direction dir : Dirs.HORIZONTAL) {
				int sx = x + Dirs.dx(dir);
				int sz = z + Dirs.dz(dir);
				// outside: the cell before the window is open air and the building has no wall there
				if (this.v.free(sx, y, sz) && this.v.sturdyTop(sx, y - 1, sz) && outside(sx, y, sz, dir)) {
					out.add(new int[]{sx, y, sz});
				}
			}
		}
		out.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[2], b[2]));
		return out;
	}

	/** Whether a cell is outside the building, looking further out in a direction (nothing of the building's within 2). */
	private boolean outside(int x, int y, int z, Direction dir) {
		for (int k = 1; k <= 2; k++) {
			int nx = x + Dirs.dx(dir) * k;
			int nz = z + Dirs.dz(dir) * k;
			if (this.v.wall(nx, y, nz)) {
				return false;
			}
		}
		return true;
	}

	/** A pot of flowers on each sill (every sill, or every other on a long front). */
	public void sillPots(Block[] potted) {
		for (int[] s : sills()) {
			put(s[0], s[1], s[2], potted[(int) (unit(0x9077, s[0], s[1], s[2]) * potted.length)].defaultBlockState(), null);
		}
	}

	/** A piece of cloth on each sill (mourning's black, or a candle where a cloth would not lie). */
	public void sillCloth(DyeColor c) {
		for (int[] s : sills()) {
			put(s[0], s[1], s[2], dyed(c, "_carpet").defaultBlockState(), null);
		}
	}

	/** Cloths of two colours on the sills, alternating (market day: wares shown on the sills). */
	public void sillCloths(DyeColor a, DyeColor b) {
		int n = 0;
		for (int[] s : sills()) {
			if (put(s[0], s[1], s[2], dyed(n % 2 == 0 ? a : b, "_carpet").defaultBlockState(), null)) {
				n++;
			}
		}
	}

	/** A runner of cloth from the main door out along the way in (one to three cells, on whatever lies there). */
	public void runner(BuiltView.Door d, DyeColor c, int length) {
		Direction out = d.out();
		for (int k = 1; k <= length; k++) {
			int x = d.x() + Dirs.dx(out) * k;
			int z = d.z() + Dirs.dz(out) * k;
			int y = d.y();
			// the runner may lie in the way in (it is walked over): place it past the kept-clear check
			long key = Draft.key(x, y, z);
			if (this.taken.contains(key) || !this.v.free(x, y, z) || !this.v.sturdyTop(x, y - 1, z)) {
				break;
			}
			this.taken.add(key);
			this.pieces.add(new Piece(x, y, z, dyed(c, "_carpet").defaultBlockState(), null));
		}
	}

	/**
	 * Striped awnings over the ground-floor openings of the front: a cloth on a board frame (top-half trapdoors of the
	 * house's wood, carpets of two colours on them) the length of each window or door and a block either side, at the
	 * head of the ground storey (or three up where the front stands on the street).
	 */
	public int awnings(Block board, DyeColor a, DyeColor b) {
		int w = this.v.frontage();
		// where an awning could hang along the front: the cell before the wall at the head of the ground storey
		int[] zs = new int[w];
		int[] ys = new int[w];
		boolean[] ok = new boolean[w];
		for (int x = 0; x < w; x++) {
			int zf = face(x, 1);
			if (zf == Integer.MIN_VALUE) {
				continue;
			}
			int z = zf - 1;
			int y = z >= 0 ? 2 : 3;
			// a wall at its back to hang from (at its own height or the course above), its cell and the cloth's free, and
			// nothing of the building's just under it but air (not a sign, a lamp, a window box)
			boolean hang = this.v.wall(x, y, zf) || this.v.wall(x, y + 1, zf) || this.v.glass(x, y, zf);
			if (!hang || !open(x, y, z) || !this.v.free(x, y + 1, z) || !this.v.free(x, y - 1, z) && y == 2) {
				continue;
			}
			zs[x] = z;
			ys[x] = y;
			ok[x] = true;
		}
		int placed = 0;
		int x = 0;
		while (x < w) {
			if (!ok[x]) {
				x++;
				continue;
			}
			int x1 = x;
			while (x1 + 1 < w && ok[x1 + 1] && zs[x1 + 1] == zs[x] && ys[x1 + 1] == ys[x]) {
				x1++;
			}
			if (x1 - x >= 1) {
				for (int k = x; k <= x1; k++) {
					BlockState frame = board.defaultBlockState().setValue(TrapDoorBlock.FACING, Dirs.BACK).setValue(TrapDoorBlock.HALF, Half.TOP)
						.setValue(TrapDoorBlock.OPEN, false);
					if (put(k, ys[k], zs[k], frame, null)) {
						put(k, ys[k] + 1, zs[k], dyed(Math.floorMod(k, 2) == 0 ? a : b, "_carpet").defaultBlockState(), null);
						placed++;
					}
				}
			}
			x = x1 + 1;
		}
		return placed;
	}

	/** Pots of flowers either side of a door, on whatever the building laid there. */
	public void doorPots(BuiltView.Door d, Block[] potted) {
		Direction out = d.out();
		Direction along = out.getClockWise();
		for (int side : new int[]{-1, 1}) {
			int x = d.x() + Dirs.dx(out) + Dirs.dx(along) * side;
			int z = d.z() + Dirs.dz(out) + Dirs.dz(along) * side;
			if (this.v.sturdyTop(x, d.y() - 1, z) && this.v.free(x, d.y() + 1, z)) {
				put(x, d.y(), z, potted[(int) (unit(0x90D7, x, d.y(), z) * potted.length)].defaultBlockState(), null);
			}
		}
	}

	/**
	 * Goods set out before the front on market day: barrels and crates along the wall either side of the door, with wares
	 * on top (a melon, pumpkins, a cake, a pot of flowers, a bundle of hay), on ground the building has laid, clear of the
	 * door and the way in.
	 */
	public int goods(int max) {
		int placed = 0;
		BlockState[] tops = {Blocks.MELON.defaultBlockState(), Blocks.PUMPKIN.defaultBlockState(), Blocks.CAKE.defaultBlockState(),
			Blocks.POTTED_POPPY.defaultBlockState(), Blocks.HAY_BLOCK.defaultBlockState(), Blocks.CARVED_PUMPKIN.defaultBlockState(),
			Blocks.POTTED_RED_TULIP.defaultBlockState(), Blocks.DECORATED_POT.defaultBlockState()};
		List<int[]> spots = new ArrayList<>();
		for (int x = 0; x < this.v.frontage(); x++) {
			int zf = face(x, 0);
			if (zf == Integer.MIN_VALUE || zf < 1) {
				continue;
			}
			int z = zf - 1;
			if (open(x, 0, z) && this.v.sturdyTop(x, -1, z) && this.v.free(x, 1, z) && !nearClear(x, 0, z)) {
				spots.add(new int[]{x, 0, z});
			}
		}
		for (int[] s : spots) {
			if (placed >= max) {
				break;
			}
			double u = unit(0x600D, s[0], 0, s[2]);
			if (u < 0.25) {
				continue;
			}
			BlockState base = u < 0.7 ? States.facing(Blocks.BARREL, Direction.UP) : Blocks.HAY_BLOCK.defaultBlockState();
			if (put(s[0], s[1], s[2], base, null)) {
				put(s[0], s[1] + 1, s[2], tops[(int) (unit(0x600E, s[0], 0, s[2]) * tops.length)], null);
				placed++;
			}
		}
		return placed;
	}

	private boolean nearClear(int x, int y, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (this.clear.contains(Draft.key(x + dx, y, z + dz))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Snow on everything the sky reaches that can hold it (roof ridges and full courses, wall heads, chimney tops beside
	 * the fire, sills and ledges), and over the yard the building laid, drifted deeper against its walls.
	 */
	public int snow() {
		int placed = 0;
		java.util.Map<Long, Integer> top = new java.util.HashMap<>();
		for (var k : this.v.keys()) {
			BlockState s = this.v.get(Draft.keyX(k), Draft.keyY(k), Draft.keyZ(k));
			if (s == null || s.isAir()) {
				continue;
			}
			long col = ((long) Draft.keyX(k) << 32) ^ (Draft.keyZ(k) & 0xFFFFFFFFL);
			top.merge(col, Draft.keyY(k), Math::max);
		}
		List<Long> cols = new ArrayList<>(top.keySet());
		cols.sort(Long::compare);
		for (long col : cols) {
			int x = (int) (col >> 32);
			int z = (int) col;
			int y = top.get(col);
			BlockState s = this.v.get(x, y, z);
			if (s == null || s.getBlock() instanceof CampfireBlock || s.getBlock() instanceof LanternBlock || !this.v.sturdyTop(x, y, z)) {
				continue;
			}
			int layers = 1;
			if (y <= 0) {
				// the yard: deeper against a wall
				boolean byWall = false;
				for (Direction dir : Dirs.HORIZONTAL) {
					byWall |= this.v.wall(x + Dirs.dx(dir), y + 1, z + Dirs.dz(dir));
				}
				layers = byWall ? 2 + (int) (unit(0x5404, x, y, z) * 2) : 1 + (int) (unit(0x5405, x, y, z) * 2);
			}
			if (put(x, y + 1, z, States.snow(layers), null)) {
				placed++;
			}
		}
		for (int[] s : sills()) {
			if (put(s[0], s[1], s[2], States.snow(1), null)) {
				placed++;
			}
		}
		// the yard the building leaves as it lies (when the ground is known): a blanket, drifted against the walls
		for (int x = 0; x < this.v.frontage(); x++) {
			for (int z = 0; z < this.v.depth(); z++) {
				long col = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
				if (top.containsKey(col) && top.get(col) >= 0) {
					continue;
				}
				for (int y = 6; y >= -12; y--) {
					if (this.v.natural(x, y, z)) {
						if (this.v.free(x, y + 1, z) && this.v.get(x, y + 1, z) == null) {
							boolean byWall = false;
							for (Direction dir : Dirs.HORIZONTAL) {
								byWall |= this.v.wall(x + Dirs.dx(dir), y + 1, z + Dirs.dz(dir));
							}
							int layers = byWall ? 2 + (int) (unit(0x5406, x, y, z) * 2) : 1 + (int) (unit(0x5407, x, y, z) * 1.6);
							if (put(x, y + 1, z, States.snow(layers), null)) {
								placed++;
							}
						}
						break;
					}
				}
			}
		}
		return placed;
	}
}
