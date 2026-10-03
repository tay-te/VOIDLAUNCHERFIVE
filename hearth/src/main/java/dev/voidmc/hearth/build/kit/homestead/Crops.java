package dev.voidmc.hearth.build.kit.homestead;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Palette;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * What fields grow and how they are laid out so they really work: farmland kept wet by water channels no more than four
 * blocks from any furrow (farmland within four of water stays moist), strips of the culture's crops sown together and
 * standing at one stage (a strip just sown, one green, one golden), pumpkin and melon patches with their fruit beside
 * attached stems, sugar cane along the channels where it is warm, scarecrows, hay ricks and compost heaps.
 */
public final class Crops {
	private Crops() {
	}

	/** What a strip of a bed is. */
	public enum Strip {
		/** The culture's crops in rows. */
		CROP,
		/** Pumpkin vines on farmland with the fruit lying beside them. */
		PUMPKIN,
		MELON,
		/** Left fallow: grass and wild flowers. */
		FALLOW
	}

	/** Where a bed put its water and its rows, for the caller's job sites and paths. */
	public record Bed(List<Integer> channels, List<int[]> strips) {
	}

	/**
	 * A bed of rows on level ground at standing level {@code y}: farmland at {@code y - 1} over the rectangle, rows
	 * running along {@code rows}, water channels across the bed so that every furrow is within four blocks of one, and
	 * strips between the channels sown with one crop each at one stage. The cells round the bed must be solid at
	 * {@code y - 1} (the channels are sources in the ground).
	 */
	public static Bed bed(Draft d, int x0, int z0, int x1, int z1, int y, Direction.Axis rows, long salt) {
		int ax = Math.min(x0, x1);
		int bx = Math.max(x0, x1);
		int az = Math.min(z0, z1);
		int bz = Math.max(z0, z1);
		boolean alongX = rows == Direction.Axis.X;
		int c0 = alongX ? az : ax;
		int c1 = alongX ? bz : bx;
		int width = c1 - c0 + 1;
		// channels: as few as keep every furrow within four
		List<Integer> channels = new ArrayList<>();
		if (width >= 3) {
			int k = (width + 8) / 9;
			for (int i = 0; i < k; i++) {
				int c = c0 + (int) Math.round((i + 0.5) * width / (double) k - 0.5);
				channels.add(Math.clamp(c, c0, c1));
			}
		}
		// a bed too narrow for a channel down it (a terrace's strip): sumps across it, every furrow within four of one
		int l0 = alongX ? ax : az;
		int l1 = alongX ? bx : bz;
		java.util.Set<Integer> sumps = new java.util.HashSet<>();
		if (channels.isEmpty()) {
			int len = l1 - l0 + 1;
			int k = (len + 8) / 9;
			for (int i = 0; i < k && len >= 3; i++) {
				sumps.add(Math.clamp(l0 + (int) Math.round((i + 0.5) * len / (double) k - 0.5), l0, l1));
			}
		}
		// strips: the runs of rows between channels (and the bed's edges)
		List<int[]> strips = new ArrayList<>();
		int start = c0;
		for (int ch : channels) {
			if (ch - 1 >= start) {
				strips.add(new int[]{start, ch - 1});
			}
			start = ch + 1;
		}
		if (start <= c1) {
			strips.add(new int[]{start, c1});
		}
		Mat crops = d.mat(Role.CROPS);
		boolean warm = d.site().temperature() >= 0.85F;
		BlockState water = Blocks.WATER.defaultBlockState();
		// the water: the channels and sumps, less any cell beside an open edge (a terrace's drop, a cut, a step) that it
		// would spill over; a cell left too far from the water to stay moist lies fallow rather than dry
		int nx = bx - ax + 1;
		int nz = bz - az + 1;
		boolean[][] wet = new boolean[nx][nz];
		for (int ch : channels) {
			for (int a = l0; a <= l1; a++) {
				wet[(alongX ? a : ch) - ax][(alongX ? ch : a) - az] = true;
			}
		}
		for (int a : sumps) {
			for (int c = c0; c <= c1; c++) {
				wet[(alongX ? a : c) - ax][(alongX ? c : a) - az] = true;
			}
		}
		for (int i = 0; i < nx; i++) {
			for (int k = 0; k < nz; k++) {
				if (!wet[i][k]) {
					continue;
				}
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					int x = ax + i + dir.getStepX();
					int z = az + k + dir.getStepZ();
					boolean inside = x >= ax && x <= bx && z >= az && z <= bz;
					if (!inside && !holds(d, x, y - 1, z)) {
						wet[i][k] = false;
						break;
					}
				}
			}
		}
		boolean[][] moist = new boolean[nx][nz];
		for (int i = 0; i < nx; i++) {
			for (int k = 0; k < nz; k++) {
				for (int dz = -4; dz <= 4 && !moist[i][k]; dz++) {
					for (int dx = -4; dx <= 4 && !moist[i][k]; dx++) {
						int ii = i + dx;
						int kk = k + dz;
						moist[i][k] = ii >= 0 && kk >= 0 && ii < nx && kk < nz ? wet[ii][kk]
							: water(d, ax + ii, y - 1, az + kk) || water(d, ax + ii, y, az + kk);
					}
				}
			}
		}
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < nx; i++) {
				for (int k = 0; k < nz; k++) {
					if (wet[i][k]) {
						d.set(ax + i, y - 1, az + k, water);
						d.set(ax + i, y, az + k, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		// one patch of pumpkins or melons at most, on a bed of three strips or more
		long bh = Noise.hash(d.seed() ^ salt, ax, az, bx * 31 + bz);
		int patch = strips.size() >= 3 && ((bh >>> 40) & 0xFF) < 90 ? (int) ((bh >>> 8) % strips.size()) : -1;
		int nCrops = crops.palette().entries().size();
		int base = (int) ((bh >>> 16) % Math.max(1, nCrops));
		for (int si = 0; si < strips.size(); si++) {
			int[] st = strips.get(si);
			long h = Noise.hash(d.seed() ^ salt, st[0], si, st[1]);
			double u = (h >>> 11) * 0x1.0p-53;
			Strip kind = si == patch && st[1] - st[0] >= 1 ? (warm ? Strip.MELON : Strip.PUMPKIN) : u < 0.05 ? Strip.FALLOW : Strip.CROP;
			// neighbouring strips grow different things: the culture's crops in turn
			Block crop = cropAt(crops.palette(), base + si);
			if (crop == Blocks.MELON_STEM) {
				kind = st[1] - st[0] >= 1 ? Strip.MELON : Strip.CROP;
				crop = cropAt(crops.palette(), base + si + 1);
				if (crop == Blocks.MELON_STEM) {
					crop = Blocks.WHEAT;
				}
			}
			// one sowing per strip: a stage for the whole strip, a little ragged within it
			int[] stages = {7, 7, 6, 6, 5, 4, 3, 1};
			int stage = stages[(int) ((h >>> 20) & 7)];
			for (int c = st[0]; c <= st[1]; c++) {
				int fromChannel = distance(channels, c);
				for (int a = alongX ? ax : az; a <= (alongX ? bx : bz); a++) {
					int x = alongX ? a : c;
					int z = alongX ? c : a;
					if (wet[x - ax][z - az]) {
						continue;
					}
					if (!moist[x - ax][z - az]) {
						fallow(d, x, y, z);
						continue;
					}
					switch (kind) {
						case FALLOW -> fallow(d, x, y, z);
						case PUMPKIN, MELON -> vine(d, x, y, z, c - st[0], kind == Strip.MELON, alongX);
						default -> {
							int age = stage + (Noise.unit(d.seed() ^ 0xA9E, x, 0, z) < 0.25 ? -1 : 0);
							plant(d, x, y, z, crop, Math.max(0, age));
						}
					}
				}
				// sugar cane on the bank of a channel where it is warm (it needs the water beside its foot)
				if (warm && fromChannel == 1 && kind == Strip.CROP && (h & 0x300) == 0) {
					for (int a = alongX ? ax : az; a <= (alongX ? bx : bz); a++) {
						int x = alongX ? a : c;
						int z = alongX ? c : a;
						if (wet[x - ax][z - az] || !moist[x - ax][z - az]) {
							continue;
						}
						cane(d, x, y, z, 2 + (int) (Noise.unit(d.seed() ^ 0xCA, x, 0, z) * 2));
					}
				}
			}
		}
		// fix up the pumpkin and melon patches: each fruit sits beside a stem that reaches for it
		attachStems(d, ax, az, bx, bz, y);
		return new Bed(channels, strips);
	}

	/** Whether water at (x, y, z) would stay put beside this cell: something solid drawn there, or natural ground. */
	static boolean holds(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s != null) {
			return net.minecraft.world.level.material.Fluids.WATER.isSame(s.getFluidState().getType()) || !s.isAir() && !s.canBeReplaced();
		}
		return d.site().ground(x, z) > y || d.site().isWater(x, z) && d.site().water(x, z) > y;
	}

	/** Water at (x, y, z): drawn there, or the natural water where the drawing leaves the cell alone. */
	static boolean water(Draft d, int x, int y, int z) {
		BlockState s = d.get(x, y, z);
		if (s != null) {
			return net.minecraft.world.level.material.Fluids.WATER.isSame(s.getFluidState().getType());
		}
		return d.site().isWater(x, z) && y < d.site().water(x, z) && y >= d.site().ground(x, z);
	}

	/** Distance from column c to the nearest channel (a large number when there is none). */
	private static int distance(List<Integer> channels, int c) {
		int best = 99;
		for (int ch : channels) {
			best = Math.min(best, Math.abs(ch - c));
		}
		return best;
	}

	/** The culture's crops in turn: the i-th of its list. */
	private static Block cropAt(Palette p, int i) {
		var entries = p.entries();
		Block b = entries.get(Math.floorMod(i, entries.size())).material().block(Form.FULL);
		return b != null ? b : Blocks.WHEAT;
	}

	/** One crop from the culture's list, by hash. */
	private static Block pickCrop(Palette p, long h) {
		var entries = p.entries();
		double total = 0;
		for (var e : entries) {
			total += Math.max(0.05, e.weight());
		}
		double r = ((h >>> 30) & 0xFFFFF) / (double) 0x100000 * total;
		for (var e : entries) {
			r -= Math.max(0.05, e.weight());
			if (r < 0) {
				Block b = e.material().block(Form.FULL);
				return b != null ? b : Blocks.WHEAT;
			}
		}
		Block b = entries.get(0).material().block(Form.FULL);
		return b != null ? b : Blocks.WHEAT;
	}

	/** Farmland and a crop at an age (a crop that is no crop, a bush or a flower, on grass). */
	public static void plant(Draft d, int x, int y, int z, Block crop, int age) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (crop instanceof CropBlock || crop instanceof StemBlock) {
				d.set(x, y - 1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
				d.set(x, y, z, States.crop(crop, age));
			} else if (crop == Blocks.SWEET_BERRY_BUSH) {
				d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
				d.set(x, y, z, States.crop(crop, Math.min(3, 1 + age / 2)));
			} else {
				d.set(x, y - 1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
				d.set(x, y, z, crop.defaultBlockState());
			}
		}
	}

	/** A fallow row: grass with a scatter of weeds and wild flowers. */
	public static void fallow(Draft d, int x, int y, int z) {
		double u = Noise.unit(d.seed() ^ 0xFA11, x, 0, z);
		Block flower = d.mat(Role.GARDEN).block(Form.FULL, x, y, z);
		boolean single = flower instanceof net.minecraft.world.level.block.VegetationBlock
			&& !flower.defaultBlockState().hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, Blocks.GRASS_BLOCK.defaultBlockState());
			if (u < 0.35) {
				d.set(x, y, z, Blocks.SHORT_GRASS.defaultBlockState());
			} else if (u < 0.45 && single) {
				d.set(x, y, z, flower.defaultBlockState());
			} else {
				d.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** Pumpkin or melon rows: stems on farmland in odd rows, the even rows bare earth for the fruit to lie on. */
	private static void vine(Draft d, int x, int y, int z, int row, boolean melon, boolean alongX) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if ((row & 1) == 0) {
				d.set(x, y - 1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
				d.set(x, y, z, States.crop(melon ? Blocks.MELON_STEM : Blocks.PUMPKIN_STEM, 7));
			} else {
				d.set(x, y - 1, z, (Noise.unit(d.seed() ^ 0xD1, x, 0, z) < 0.5 ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState());
				double u = Noise.unit(d.seed() ^ 0xF4, x, 1, z);
				d.set(x, y, z, u < 0.4 ? (melon ? Blocks.MELON : Blocks.PUMPKIN).defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** Grown stems beside a fruit turn to attached stems reaching for it. */
	private static void attachStems(Draft d, int x0, int z0, int x1, int z1, int y) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				BlockState s = d.get(x, y, z);
				if (s == null || !(s.is(Blocks.PUMPKIN_STEM) || s.is(Blocks.MELON_STEM))) {
					continue;
				}
				boolean melon = s.is(Blocks.MELON_STEM);
				Block fruit = melon ? Blocks.MELON : Blocks.PUMPKIN;
				for (Direction dir : Dirs.HORIZONTAL) {
					int nx = x + Dirs.dx(dir);
					int nz = z + Dirs.dz(dir);
					if (States.is(d.get(nx, y, nz), fruit) && Noise.unit(d.seed() ^ 0x57E, x, 0, z) < 0.8) {
						Block attached = melon ? Blocks.ATTACHED_MELON_STEM : Blocks.ATTACHED_PUMPKIN_STEM;
						try (var st = d.stage(StageKind.EXTERIOR)) {
							d.set(x, y, z, attached.defaultBlockState().setValue(AttachedStemBlock.FACING, dir));
						}
						break;
					}
				}
			}
		}
	}

	/** Sugar cane, {@code height} tall, on the bank of water (sand under it in sandy lands). */
	public static void cane(Draft d, int x, int y, int z, int height) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			Block ground = d.mat(Role.GROUND).main(Form.FULL);
			d.set(x, y - 1, z, (ground == Blocks.SAND || ground == Blocks.RED_SAND ? ground : Blocks.DIRT).defaultBlockState());
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, Blocks.SUGAR_CANE.defaultBlockState());
			}
		}
	}

	/**
	 * A scarecrow among the rows: a post, a body of straw with arms of fence across, a carved pumpkin head (a straw
	 * head under a hat where the culture keeps no pumpkins), facing the street.
	 */
	public static void scarecrow(Draft d, int x, int y, int z, Direction facing) {
		Mat fence = d.mat(Role.FENCE);
		Mat frame = d.mat(Role.FRAME);
		boolean pumpkins = d.site().temperature() < 0.95F;
		Direction across = Dirs.right(facing);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, Blocks.COARSE_DIRT.defaultBlockState());
			d.set(x, y, z, frame.has(Form.FENCE) ? frame.fence() : fence.fence());
			d.set(x, y + 1, z, Blocks.HAY_BLOCK.defaultBlockState());
			for (int side : new int[]{-1, 1}) {
				int ax = x + Dirs.dx(across) * side;
				int az = z + Dirs.dz(across) * side;
				d.set(ax, y + 1, az, fence.fence());
			}
			if (pumpkins) {
				d.set(x, y + 2, z, States.facing(Blocks.CARVED_PUMPKIN, facing));
			} else {
				d.set(x, y + 2, z, Blocks.HAY_BLOCK.defaultBlockState());
				d.set(x, y + 3, z, d.mat(Role.ROOF).slab(SlabType.BOTTOM));
			}
		}
	}

	/**
	 * A hay rick: bales stacked into a ridge {@code length} long along {@code run}, two courses with a capping course, on
	 * the ground at standing level y.
	 */
	public static void rick(Draft d, int x, int y, int z, Direction run, int length) {
		Direction across = Dirs.right(run);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				for (int w = 0; w < 2; w++) {
					int qx = px + Dirs.dx(across) * w;
					int qz = pz + Dirs.dz(across) * w;
					d.set(qx, y, qz, States.axis(Blocks.HAY_BLOCK, run.getAxis()));
				}
				boolean end = i == 0 || i == length - 1;
				if (!end || length <= 2) {
					d.set(px, y + 1, pz, States.axis(Blocks.HAY_BLOCK, run.getAxis()));
				}
			}
		}
	}

	/** A compost heap: a composter or two against a fence corner, coarse earth round them. */
	public static void compost(Draft d, int x, int y, int z, Direction run) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Blocks.COMPOSTER.defaultBlockState().setValue(BlockStateProperties.LEVEL_COMPOSTER, 4 + (int) (Noise.unit(d.seed(), x, y, z) * 4)));
			int nx = x + Dirs.dx(run);
			int nz = z + Dirs.dz(run);
			if (d.isAbsent(nx, y, nz)) {
				d.set(nx, y, nz, Blocks.COMPOSTER.defaultBlockState().setValue(BlockStateProperties.LEVEL_COMPOSTER, 8));
			}
		}
	}

	/** A sack of grain or a basket: a barrel on its side, a slab of the joinery on another, for heaps of harvest. */
	public static void harvest(Draft d, int x, int y, int z, Direction facing) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			double u = Noise.unit(d.seed() ^ 0x4A2, x, y, z);
			if (u < 0.4) {
				d.set(x, y, z, States.facing(Blocks.BARREL, facing));
			} else if (u < 0.7) {
				d.set(x, y, z, Blocks.HAY_BLOCK.defaultBlockState());
			} else {
				d.set(x, y, z, d.mat(Role.FURNITURE).stairsOrSlab(facing, Half.BOTTOM));
			}
		}
	}
}
