package dev.voidmc.hearth.build.kit;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits.GardenKind;
import dev.voidmc.hearth.build.style.Palette;
import dev.voidmc.hearth.build.style.Role;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Yards and gardens: flower beds, kitchen rows, paths, fences and gates, hedges, garden trees, woodpiles, barrels and
 * crates, beehives, a water butt, potted plants. Every piece sits on the ground it is given (y is the standing level)
 * and puts the right soil under its plants, so nothing floats and nothing pops off.
 */
public final class Garden {
	private Garden() {
	}

	/** A plant of the style's garden at a position (two blocks for tall flowers), on soil laid under it. */
	public static void flower(Draft d, int x, int y, int z) {
		Block plant = d.mat(Role.GARDEN).block(Form.FULL, x, y, z);
		soil(d, x, y - 1, z, plant);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			if (plant.defaultBlockState().hasProperty(DoublePlantBlock.HALF)) {
				d.tallPlant(x, y, z, plant);
			} else {
				d.set(x, y, z, plant.defaultBlockState());
			}
		}
	}

	private static void soil(Draft d, int x, int y, int z, Block plant) {
		BlockState below = d.get(x, y, z);
		Block soil = Blocks.GRASS_BLOCK;
		String p = BuiltInRegistries.BLOCK.getKey(plant).getPath();
		if (p.contains("cactus") || p.contains("dead_bush") || p.contains("dry_grass")) {
			soil = Blocks.SAND;
		}
		if (below == null || below.isAir() || below.is(Blocks.DIRT_PATH)) {
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(x, y, z, soil.defaultBlockState());
			}
		}
	}

	/** A flower pot holding a plant of the style's garden (or a fern), as {@code potted_<plant>} if the game has one. */
	public static void pot(Draft d, int x, int y, int z) {
		Block plant = d.mat(Role.GARDEN).block(Form.FULL, x, y, z);
		Identifier id = BuiltInRegistries.BLOCK.getKey(plant);
		Identifier potted = Identifier.fromNamespaceAndPath(id.getNamespace(), "potted_" + id.getPath());
		Block pot = BuiltInRegistries.BLOCK.containsKey(potted) ? BuiltInRegistries.BLOCK.getValue(potted) : Blocks.POTTED_FERN;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, pot.defaultBlockState());
		}
	}

	/** A bed of flowers over cells, about {@code density} of them planted, the rest short grass and ferns. */
	public static void flowerBed(Draft d, List<int[]> cells, int y, double density) {
		for (int[] c : cells) {
			if (!d.isEmpty(c[0], y, c[1])) {
				continue;
			}
			double u = Noise.unit(d.seed() ^ 0xF10, c[0], y, c[1]);
			if (u < density) {
				flower(d, c[0], y, c[1]);
			} else if (u < density + 0.25) {
				soil(d, c[0], y - 1, c[1], Blocks.SHORT_GRASS);
				try (var s = d.stage(StageKind.EXTERIOR)) {
					d.set(c[0], y, c[1], (u < density + 0.12 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
				}
			}
		}
	}

	/** Rows of crops on farmland with a water channel down the middle (water keeps farmland wet). */
	public static void kitchenRows(Draft d, int x0, int z0, int x1, int z1, int y, Direction rows) {
		Mat crops = d.mat(Role.CROPS);
		boolean alongX = rows.getAxis() == Direction.Axis.X;
		int a0 = alongX ? z0 : x0;
		int a1 = alongX ? z1 : x1;
		int water = (a0 + a1) / 2;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0; z <= z1; z++) {
				for (int x = x0; x <= x1; x++) {
					int across = alongX ? z : x;
					if (across == water && a1 - a0 >= 2) {
						d.set(x, y - 1, z, Blocks.WATER.defaultBlockState());
						d.set(x, y, z, Blocks.AIR.defaultBlockState());
						continue;
					}
					d.set(x, y - 1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					Block crop = crops.block(Form.FULL, alongX ? 0 : x, y, alongX ? z : 0);
					int age = 3 + (int) (Noise.unit(d.seed(), x, y, z) * 5);
					BlockState cs = crop instanceof CropBlock ? States.crop(crop, age) : crop.defaultBlockState();
					d.set(x, y, z, cs);
				}
			}
		}
	}

	/** A path over cells: the style's path surface at the ground row (y - 1), air above. */
	public static void path(Draft d, List<int[]> cells, int y) {
		Mat path = d.mat(Role.PATH);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int[] c : cells) {
				d.set(c[0], y - 1, c[1], path.full());
				if (d.isAbsent(c[0], y, c[1])) {
					d.set(c[0], y, c[1], Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** A straight line of cells from (x0, z0) to (x1, z1), 4-connected. */
	public static List<int[]> line(int x0, int z0, int x1, int z1) {
		List<int[]> out = new ArrayList<>();
		int x = x0;
		int z = z0;
		out.add(new int[]{x, z});
		while (x != x1 || z != z1) {
			if (Math.abs(x1 - x) >= Math.abs(z1 - z) && x != x1) {
				x += Integer.signum(x1 - x);
			} else {
				z += Integer.signum(z1 - z);
			}
			out.add(new int[]{x, z});
		}
		return out;
	}

	/**
	 * A fence (or wall, or hedge, by the style's yard habit) along cells, standing on the ground of each column, with a
	 * gate at the given index (or none if negative). Fences follow the ground up and down.
	 */
	public static void fence(Draft d, List<int[]> cells, int gateAt, Direction gateFacing) {
		Mat fence = d.mat(Role.FENCE);
		Mat found = d.mat(Role.FOUNDATION);
		Mat hedge = d.mat(Role.HEDGE);
		var kind = d.habits().yard().fence();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < cells.size(); i++) {
				int x = cells.get(i)[0];
				int z = cells.get(i)[1];
				int g = groundAt(d, x, z);
				if (i == gateAt) {
					d.set(x, g, z, fence.has(Form.FENCE_GATE) ? fence.gate(gateFacing, false) : Paint.of(Blocks.AIR.defaultBlockState()));
					continue;
				}
				switch (kind) {
					case WALL -> {
						d.set(x, g, z, fence.has(Form.WALL) ? fence.wall() : found.has(Form.WALL) ? found.wall() : found.full());
					}
					case HEDGE -> {
						d.set(x, g, z, hedge.leaves());
						if (Noise.unit(d.seed(), x, g, z) < 0.6) {
							d.set(x, g + 1, z, hedge.leaves());
						}
					}
					case NONE -> {
					}
					default -> d.set(x, g, z, fence.fence());
				}
			}
		}
	}

	/** The standing level at a column as drawn so far: on top of whatever ground or fill has been placed there. */
	public static int groundAt(Draft d, int x, int z) {
		int g = d.site().ground(x, z);
		for (int y = g + 6; y >= g - 8; y--) {
			BlockState s = d.get(x, y, z);
			if (s != null && !s.isAir() && (s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
				|| s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND))) {
				return y + 1;
			}
			if (s != null && s.isAir() && y < g) {
				// dug out here
				continue;
			}
		}
		// nothing drawn: natural ground, unless something carved below it
		for (int y = g - 1; y >= g - 8; y--) {
			BlockState s = d.get(x, y, z);
			if (s == null || !s.isAir()) {
				return y + 1;
			}
		}
		return g;
	}

	/** A small garden tree: a short trunk and a rounded crown of the style's tree. */
	public static void tree(Draft d, int x, int y, int z, int height) {
		Mat log = d.mat(Role.TREE_LOG);
		Mat leaves = d.mat(Role.TREE_LEAVES);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			soil(d, x, y - 1, z, Blocks.OAK_SAPLING);
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, log.log(Direction.Axis.Y).orElse(log.full()));
			}
			int top = y + height;
			for (int dy = -2; dy <= 1; dy++) {
				int r = dy == 1 ? 1 : 2;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
						if (corner && (dy != -1 || Noise.unit(d.seed(), x + dx, top + dy, z + dz) < 0.5)) {
							continue;
						}
						int lx = x + dx;
						int lz = z + dz;
						if (lx < 0 || lz < 0 || lx >= d.frontage() || lz >= d.depth()) {
							continue;
						}
						d.setIfAbsent(lx, top + dy, lz, leaves.leaves());
					}
				}
			}
			d.set(x, top - 1, z, log.log(Direction.Axis.Y));
		}
	}

	/** A woodpile of logs lying along {@code axis}, {@code length} long, against whatever is behind it. */
	public static void woodpile(Draft d, int x, int y, int z, Direction run, int length) {
		Mat log = d.mat(Role.BEAM);
		Direction.Axis axis = Dirs.right(run).getAxis();
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < length; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				d.set(px, y, pz, log.log(axis));
				if (i < length - 1 || length == 1) {
					d.set(px, y + 1, pz, log.log(axis));
				} else {
					d.set(px, y + 1, pz, log.stairsOrSlab(run.getOpposite(), Half.BOTTOM));
				}
			}
			// a slab roof over the pile
			Mat roof = d.mat(Role.ROOF);
			for (int i = 0; i < length; i++) {
				d.setIfAbsent(x + Dirs.dx(run) * i, y + 2, z + Dirs.dz(run) * i, roof.slab(SlabType.BOTTOM));
			}
		}
	}

	/** A few barrels and a crate (a chest-coloured barrel) in a cluster starting at (x, y, z). */
	public static void barrels(Draft d, int x, int y, int z, Direction run, int count) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int i = 0; i < count; i++) {
				int px = x + Dirs.dx(run) * i;
				int pz = z + Dirs.dz(run) * i;
				double u = Noise.unit(d.seed() ^ 0xBA, px, y, pz);
				d.set(px, y, pz, u < 0.6 ? States.facing(Blocks.BARREL, Direction.UP) : u < 0.8 ? Blocks.COMPOSTER.defaultBlockState()
					: Blocks.HAY_BLOCK.defaultBlockState());
				if (u < 0.3 && i == 0) {
					d.set(px, y + 1, pz, States.facing(Blocks.BARREL, run));
				}
			}
		}
	}

	/** A beehive on a post among flowers. */
	public static void beehive(Draft d, int x, int y, int z, Direction facing) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, d.mat(Role.FENCE).fence());
			d.set(x, y + 1, z, States.facing(Blocks.BEEHIVE, facing));
		}
	}

	/** A water butt: a cauldron of water. */
	public static void waterButt(Draft d, int x, int y, int z) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		}
	}

	/** A lamp post: a fence post with a lantern on top (or hanging from a crook). */
	public static void lampPost(Draft d, int x, int y, int z, int height, Direction crook) {
		Mat post = d.mat(Role.FENCE);
		Block lantern = d.mat(Role.LIGHT).mainOr(Form.FULL, Blocks.LANTERN);
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int k = 0; k < height; k++) {
				d.set(x, y + k, z, post.fence());
			}
			if (crook == null) {
				d.set(x, y + height, z, States.lantern(lantern, false));
			} else {
				d.set(x, y + height, z, post.fence());
				int cx = x + Dirs.dx(crook);
				int cz = z + Dirs.dz(crook);
				d.set(cx, y + height, cz, post.fence());
				d.set(cx, y + height - 1, cz, States.lantern(lantern, true));
			}
		}
	}

	/**
	 * Snow drifted against the walls of a rectangle (cold styles): a bank of snow blocks deeper along the walls than at
	 * the corners, topped with layers, leaving the door's approach clear.
	 */
	public static void snowBanks(Draft d, int x0, int z0, int x1, int z1, int y, Integer doorX) {
		try (var s = d.stage(StageKind.EXTERIOR)) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				for (int x = x0 - 1; x <= x1 + 1; x++) {
					boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1;
					if (inside || x < 0 || z < 0 || x >= d.frontage() || z >= d.depth()) {
						continue;
					}
					if (doorX != null && z == z0 - 1 && Math.abs(x - doorX) <= 1) {
						continue;
					}
					int g = groundAt(d, x, z);
					if (!d.isAbsent(x, g, z) || g != y) {
						continue;
					}
					boolean corner = (x < x0 || x > x1) && (z < z0 || z > z1);
					double u = Noise.unit(d.seed() ^ 0x5404, x, g, z);
					if (!corner && u < 0.55) {
						d.set(x, g, z, Blocks.SNOW_BLOCK.defaultBlockState());
						if (d.isAbsent(x, g + 1, z)) {
							d.set(x, g + 1, z, States.snow(1 + (int) (u * 7) % 4));
						}
					} else if (d.isAbsent(x, g, z)) {
						d.set(x, g, z, States.snow(2 + (int) (u * 5) % 4));
					}
				}
			}
		}
	}

	/** The garden kind of the style. */
	public static GardenKind kind(Draft d) {
		return d.habits().yard().garden();
	}

	/**
	 * A garden over a rectangle in the style's manner: cottage flowers round a little lawn, kitchen rows, a dry garden of
	 * sand, cacti and pots, a zen garden of gravel, stones and moss, a tropical one of ferns and bushes, a rock garden of
	 * heather and boulders.
	 */
	public static void garden(Draft d, int x0, int z0, int x1, int z1, int y) {
		List<int[]> cells = new ArrayList<>();
		Draft.rect(x0, z0, x1, z1, cells::add);
		GardenKind kind = kind(d);
		switch (kind) {
			case KITCHEN -> {
				if (x1 - x0 >= 2 && z1 - z0 >= 2) {
					kitchenRows(d, x0, z0, x1, z1, y, x1 - x0 >= z1 - z0 ? Dirs.RIGHT : Dirs.BACK);
				} else {
					flowerBed(d, cells, y, 0.6);
				}
			}
			case XERIC -> {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : cells) {
						d.set(c[0], y - 1, c[1], (Noise.unit(d.seed(), c[0], 1, c[1]) < 0.7 ? Blocks.SAND : Blocks.COARSE_DIRT).defaultBlockState());
						double u = Noise.unit(d.seed(), c[0], 2, c[1]);
						if (u < 0.12) {
							d.set(c[0], y, c[1], Blocks.CACTUS.defaultBlockState());
						} else if (u < 0.3) {
							d.set(c[0], y, c[1], Blocks.DEAD_BUSH.defaultBlockState());
						} else if (u < 0.4) {
							d.set(c[0], y, c[1], States.of(Blocks.DECORATED_POT));
						} else if (u < 0.55) {
							d.set(c[0], y, c[1], Blocks.SHORT_DRY_GRASS.defaultBlockState());
						}
					}
				}
			}
			case ZEN -> {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : cells) {
						double u = Noise.unit(d.seed(), c[0], 3, c[1]);
						d.set(c[0], y - 1, c[1], (u < 0.75 ? Blocks.GRAVEL : Blocks.MOSS_BLOCK).defaultBlockState());
						if (u > 0.93) {
							d.set(c[0], y, c[1], Blocks.MOSSY_COBBLESTONE.defaultBlockState());
						} else if (u > 0.85) {
							d.set(c[0], y, c[1], Blocks.MOSS_CARPET.defaultBlockState());
						}
					}
				}
			}
			case TROPICAL -> {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : cells) {
						double u = Noise.unit(d.seed(), c[0], 4, c[1]);
						if (u < 0.25) {
							soil(d, c[0], y - 1, c[1], Blocks.FERN);
							d.tallPlant(c[0], y, c[1], Blocks.LARGE_FERN);
						} else if (u < 0.45) {
							soil(d, c[0], y - 1, c[1], Blocks.FERN);
							d.set(c[0], y, c[1], Blocks.BUSH.defaultBlockState());
						} else if (u < 0.6) {
							flower(d, c[0], y, c[1]);
						}
					}
				}
			}
			case ROCK -> {
				try (var s = d.stage(StageKind.EXTERIOR)) {
					for (int[] c : cells) {
						double u = Noise.unit(d.seed(), c[0], 5, c[1]);
						if (u < 0.12) {
							d.set(c[0], y, c[1], (u < 0.06 ? Blocks.MOSSY_COBBLESTONE : Blocks.ANDESITE).defaultBlockState());
						} else if (u < 0.6) {
							flower(d, c[0], y, c[1]);
						}
					}
				}
			}
			case NONE -> {
			}
			default -> flowerBed(d, cells, y, 0.55);
		}
	}

	/** A garden palette's blocks, for callers that want to pick their own plants. */
	public static Palette palette(Draft d) {
		return d.style().palette(Role.GARDEN, d.tier());
	}
}
