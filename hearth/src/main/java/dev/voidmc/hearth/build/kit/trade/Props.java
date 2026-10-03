package dev.voidmc.hearth.build.kit.trade;

import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.BlockEntities;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The things a working yard is full of, at human scale: timber stacked on bearers, sawn boards in stickered stacks,
 * dressed stone on pallets, crates and barrels, sacks, carts, sawhorses, chopping blocks, anvils on their stumps,
 * troughs, racks and lines of cloth. Each sits on the standing level it is given (y), never floats, and registers what
 * folk use it for when it is a store or a place of work. Materials come from the style's roles, so a karst yard stacks
 * bamboo and a northern one redwood.
 */
public final class Props {
	private Props() {
	}

	/** A block by id with a vanilla fallback (Expanse ids resolve only when Expanse is installed). */
	public static Block block(String id, Block fallback) {
		return States.block(id, fallback);
	}

	/**
	 * Logs lying along {@code axis} in a pile {@code width} wide (across) and {@code length} long, stacked pyramid-wise
	 * up to {@code height} courses, the top course a little short. Bottom course on two bearers where long enough.
	 */
	public static void logPile(Draft d, int x, int y, int z, Direction.Axis axis, int length, int width, int height, Mat log) {
		boolean alongX = axis == Direction.Axis.X;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int a = 0; a < length; a++) {
				for (int b = 0; b < width; b++) {
					int px = alongX ? x + a : x + b;
					int pz = alongX ? z + b : z + a;
					// pyramid: the middle of the pile is highest
					int fromEdge = Math.min(b, width - 1 - b);
					int h = Math.min(height, 1 + fromEdge + (width <= 2 ? 1 : 0));
					// the top course stops short of the ends now and then
					boolean end = a == 0 || a == length - 1;
					if (end && h > 1 && Noise.unit(d.seed() ^ 0x10C, px, y, pz) < 0.5) {
						h--;
					}
					for (int k = 0; k < h; k++) {
						d.set(px, y + k, pz, log.log(axis));
					}
				}
			}
		}
	}

	/** A stack of sawn boards: planks in courses with slabs on top, a few boards leaning against its end. */
	public static void boardStack(Draft d, int x, int y, int z, Direction.Axis axis, int length, int height, Mat wood) {
		boolean alongX = axis == Direction.Axis.X;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int a = 0; a < length; a++) {
				int px = alongX ? x + a : x;
				int pz = alongX ? z : z + a;
				for (int k = 0; k < height - 1; k++) {
					d.set(px, y + k, pz, wood.full());
				}
				boolean slab = (a + height) % 2 == 0 || a == length - 1;
				d.set(px, y + height - 1, pz, slab ? wood.slab(SlabType.BOTTOM) : wood.full());
			}
			// a board leaning on the end
			int ex = alongX ? x + length : x;
			int ez = alongX ? z : z + length;
			Direction away = alongX ? Dirs.RIGHT : Dirs.BACK;
			if (Shed.Walls2.inPlot(d, ex, ez) && d.isAbsent(ex, y, ez) && wood.has(Form.TRAPDOOR)) {
				d.set(ex, y, ez, wood.trapdoor(away, Half.BOTTOM, true));
			}
		}
	}

	/** Dressed stone blocks on a pallet of boards: a 2 × 2 stack, two high in one corner. */
	public static void stonePallet(Draft d, int x, int y, int z, Mat stone, Mat pallet) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					d.set(x + dx, y, z + dz, pallet.slab(SlabType.TOP));
					d.set(x + dx, y + 1, z + dz, stone.full());
				}
			}
			d.set(x, y + 2, z, stone.full());
			if (stone.has(Form.SLAB)) {
				d.set(x + 1, y + 2, z, stone.slab(SlabType.BOTTOM));
			}
		}
	}

	/** Crates and barrels in a cluster along {@code run}: barrels standing and lying, a composter-crate, one stacked. */
	public static void crates(Draft d, int x, int y, int z, Direction run, int count) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < count; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				double u = Noise.unit(d.seed() ^ 0xC8A, px, y, pz);
				BlockState b = u < 0.45 ? States.facing(Blocks.BARREL, Direction.UP) : u < 0.7 ? States.facing(Blocks.BARREL, run)
					: u < 0.85 ? Blocks.COMPOSTER.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState();
				d.set(px, y, pz, b);
				if (i == 0 || u < 0.3) {
					d.set(px, y + 1, pz, States.facing(Blocks.BARREL, Dirs.right(run)));
				}
			}
		}
	}

	/** Sacks of grain or flour: wool slabs and stairs heaped on a pallet, a hay bale among them. */
	public static void sacks(Draft d, int x, int y, int z, Direction run, int count, boolean flour) {
		Block slab = block(flour ? "minecraft:white_wool_slab" : "minecraft:brown_wool_slab", Blocks.CARPET.pick(flour ? DyeColor.WHITE : DyeColor.BROWN));
		Block stair = block(flour ? "minecraft:white_wool_stairs" : "minecraft:brown_wool_stairs", Blocks.HAY_BLOCK);
		Block full = Blocks.WOOL.pick(flour ? DyeColor.WHITE : DyeColor.BROWN);
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < count; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				double u = Noise.unit(d.seed() ^ 0x5AC, px, y, pz);
				if (u < 0.35) {
					d.set(px, y, pz, full.defaultBlockState());
					d.set(px, y + 1, pz, States.slab(slab, SlabType.BOTTOM));
				} else if (u < 0.65) {
					d.set(px, y, pz, States.stairs(stair, i % 2 == 0 ? run : run.getOpposite(), Half.BOTTOM));
				} else if (u < 0.85) {
					d.set(px, y, pz, States.slab(slab, SlabType.BOTTOM));
				} else {
					d.set(px, y, pz, Blocks.HAY_BLOCK.defaultBlockState());
				}
			}
		}
	}

	/**
	 * A two-wheeled cart: its bed (planks under a load) on the axle row with a wheel either side, the shaft in front
	 * toward {@code facing}. Needs 3 × 2 cells: the axle row at (x-1..x+1, z) and the shaft at (x, z + facing).
	 */
	public static void cart(Draft d, int x, int y, int z, Direction facing, BlockState load) {
		Mat wood = d.mat(Role.FURNITURE);
		Mat wheel = d.mat(Role.FRAME);
		Direction side = Dirs.right(facing);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, wood.slab(SlabType.TOP));
			if (load != null) {
				d.set(x, y + 1, z, load);
			}
			// wheels: open trapdoors standing against the bed's sides
			int lx = x - Dirs.dx(side);
			int lz = z - Dirs.dz(side);
			int rx = x + Dirs.dx(side);
			int rz = z + Dirs.dz(side);
			if (wheel.has(Form.TRAPDOOR)) {
				d.set(lx, y, lz, wheel.trapdoor(side.getOpposite(), Half.BOTTOM, true));
				d.set(rx, y, rz, wheel.trapdoor(side, Half.BOTTOM, true));
			}
			// the shaft resting on the ground
			int sx = x + Dirs.dx(facing);
			int sz = z + Dirs.dz(facing);
			d.set(sx, y, sz, wood.slab(SlabType.BOTTOM));
		}
	}

	/** A sawhorse pair carrying a log or a board across, along {@code axis}. */
	public static void sawhorses(Draft d, int x, int y, int z, Direction.Axis axis, boolean log) {
		Mat fence = Hall.woodFence(d);
		Mat wood = log ? d.mat(Role.FRAME) : d.mat(Role.FLOOR);
		int dx = axis == Direction.Axis.X ? 1 : 0;
		int dz = axis == Direction.Axis.X ? 0 : 1;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, fence.fence());
			d.set(x + 2 * dx, y, z + 2 * dz, fence.fence());
			for (int i = 0; i < 3; i++) {
				d.set(x + i * dx, y + 1, z + i * dz, log ? wood.log(axis) : wood.slab(SlabType.BOTTOM));
			}
		}
	}

	/** A chopping block: an upright stripped log, with a second one lying beside it now and then. */
	public static void choppingBlock(Draft d, int x, int y, int z) {
		Mat log = d.mat(Role.FRAME);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, log.strippedLog(Direction.Axis.Y));
		}
	}

	/**
	 * Registers a job site at the cell a worker stands in, facing {@code toward} the work, if the cell and the one above
	 * it are clear. Answers whether it was registered.
	 */
	public static boolean site(Draft d, int x, int y, int z, Direction toward, JobType job) {
		if (!d.isEmpty(x, y, z) || !d.isEmpty(x, y + 1, z)) {
			return false;
		}
		d.poi(PoiType.JOB_SITE, x, y, z, toward, job);
		return true;
	}

	/** An anvil on a stump, used from the side {@code toward} it faces; registers the smith's job site. */
	public static void anvil(Draft d, int x, int y, int z, Direction user, JobType job) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			Block anvil = Noise.unit(d.seed(), x, y, z) < 0.3 ? Blocks.CHIPPED_ANVIL : Blocks.ANVIL;
			// an anvil's long axis runs across the facing; the smith stands at its side
			d.set(x, y, z, States.facing(anvil, Dirs.right(user)));
		}
		d.poi(PoiType.JOB_SITE, x + Dirs.dx(user), y, z + Dirs.dz(user), user.getOpposite(), job);
	}

	/** A grindstone on the floor, worked from {@code user}'s side; registers the job site. */
	public static void grindstone(Draft d, int x, int y, int z, Direction user, JobType job) {
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, States.attached(Blocks.GRINDSTONE, AttachFace.FLOOR, Dirs.right(user))));
		d.poi(PoiType.JOB_SITE, x + Dirs.dx(user), y, z + Dirs.dz(user), user.getOpposite(), job);
	}

	/** Any workstation block facing its user, the job site registered where the user stands. */
	public static void station(Draft d, Block block, int x, int y, int z, Direction user, JobType job) {
		d.inStage(StageKind.INTERIOR, () -> d.set(x, y, z, States.facing(block, user)));
		if (job != null) {
			d.poi(PoiType.JOB_SITE, x + Dirs.dx(user), y, z + Dirs.dz(user), user.getOpposite(), job);
		}
	}

	/** A store: a chest with the trade's goods, or a barrel, used from {@code user}'s side; registers STORAGE. */
	public static void store(Draft d, int x, int y, int z, Direction user, boolean chest, List<Object[]> items) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (chest) {
				d.set(x, y, z, States.facing(Blocks.CHEST, user), items.isEmpty() ? null : BlockEntities.container(items));
			} else {
				d.set(x, y, z, States.facing(Blocks.BARREL, user), items.isEmpty() ? null : BlockEntities.container(items));
			}
		}
		d.poi(PoiType.STORAGE, x + Dirs.dx(user), y, z + Dirs.dz(user), user.getOpposite());
	}

	/** A trough of water cauldrons in a row along {@code run}. */
	public static void trough(Draft d, int x, int y, int z, Direction run, int length) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				d.set(x + Dirs.dx(run) * i, y, z + Dirs.dz(run) * i,
					Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
			}
		}
	}

	/** A tool rack on a wall: a shelf of tools over a barrel (or a bench) facing into the room. */
	public static void toolRack(Draft d, int x, int y, int z, Direction facing, List<Object[]> tools) {
		Block shelf = d.mat(Role.FURNITURE).main(Form.SHELF);
		try (var s = d.stage(StageKind.INTERIOR)) {
			if (shelf != null) {
				d.set(x, y, z, States.facing(shelf, facing), BlockEntities.shelf(tools));
			} else {
				d.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
			}
		}
	}

	/** Barrels lying on their sides in a rack along {@code run}, one or two tiers, heads toward {@code face}. */
	public static void barrelRack(Draft d, int x, int y, int z, Direction run, int count, int tiers, Direction face) {
		try (var s = d.stage(StageKind.INTERIOR)) {
			for (int i = 0; i < count; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				for (int t = 0; t < tiers; t++) {
					d.set(px, y + t, pz, States.facing(Blocks.BARREL, face));
				}
			}
		}
	}

	/** Storage jars: decorated pots in a cluster. */
	public static void jars(Draft d, int x, int y, int z, Direction run, int count) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < count; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, States.facing(Blocks.DECORATED_POT, Dirs.FRONT));
			}
		}
	}

	/**
	 * Cloth hung over a rail: a beam (log) at height y along {@code run} over {@code length} cells, with wall banners
	 * of the given colours draped on its {@code face} side, each hanging a block and more below the rail.
	 */
	public static void clothRail(Draft d, int x, int y, int z, Direction run, int length, Direction face, String... colours) {
		Mat beam = d.mat(Role.BEAM);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, beam.log(run.getAxis()));
				int bx = px + Dirs.dx(face);
				int bz = pz + Dirs.dz(face);
				if (colours.length == 0 || !Shed.Walls2.inPlot(d, bx, bz) || !d.isAbsent(bx, y, bz)) {
					continue;
				}
				String colour = colours[(int) (Noise.unit(d.seed() ^ 0xC107, px, y, pz) * colours.length) % colours.length];
				Block banner = block("minecraft:" + colour + "_wall_banner", Blocks.WALL_BANNER.pick(DyeColor.WHITE));
				d.set(bx, y, bz, States.facing(banner, face));
			}
		}
	}

	/** A heap of spoil or rubble: a low mound of mixed blocks and slabs on the ground over a rectangle. */
	public static void heap(Draft d, int x0, int z0, int x1, int z1, int y, Block[] mix, Block slab) {
		double cx = (x0 + x1) / 2.0;
		double cz = (z0 + z1) / 2.0;
		double rx = Math.max(1, (x1 - x0) / 2.0 + 0.5);
		double rz = Math.max(1, (z1 - z0) / 2.0 + 0.5);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					double t = Math.hypot((x - cx) / rx, (z - cz) / rz);
					if (t > 1.05) {
						continue;
					}
					int h = t < 0.45 ? 2 : 1;
					double u = Noise.unit(d.seed() ^ 0x4EA, x, y, z);
					for (int k = 0; k < h; k++) {
						d.set(x, y + k, z, mix[(int) (u * mix.length) % mix.length].defaultBlockState());
					}
					if (slab != null && u < 0.5) {
						d.set(x, y + h, z, States.slab(slab, SlabType.BOTTOM));
					}
				}
			}
		}
	}

	/** A lantern standing on a post of fences, {@code height} tall. */
	public static void lampPost(Draft d, int x, int y, int z, int height) {
		Mat post = Hall.woodFence(d);
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, post.fence());
			}
			d.set(x, y + height, z, States.lantern(lantern, false));
		}
	}

	/** A lantern hanging under a block at (x, y + 1, z) (a beam, an eave): placed only if the block above is drawn solid. */
	public static boolean hangLantern(Draft d, int x, int y, int z) {
		if (!d.isSolid(x, y + 1, z) && !(d.get(x, y + 1, z) != null && d.get(x, y + 1, z).getBlock() instanceof net.minecraft.world.level.block.SlabBlock)) {
			return false;
		}
		if (!d.isEmpty(x, y, z)) {
			return false;
		}
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, States.lantern(lantern, true)));
		return true;
	}
}
