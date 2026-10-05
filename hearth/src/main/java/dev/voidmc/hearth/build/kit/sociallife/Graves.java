package dev.voidmc.hearth.build.kit.sociallife;

import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.Paint;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.Garden;
import dev.voidmc.hearth.build.kit.civic.Tradition;
import dev.voidmc.hearth.build.kit.light.Lighting;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Graves and what grows and burns among them, in the manner of each culture: the Bohemian churchyard's stone covers and
 * steles with little lanterns among older mounds under wooden and wrought-iron crosses; the north's rune stones and
 * timber crosses; the alpine iron crosses under their little roofs; the stone country's slate slabs and ringed crosses;
 * the Mediterranean's white chest tombs among cypresses; the desert's whitened crosses and cairns; the steppe's balbal
 * stones over grassy mounds; the karst hill tombs with their horseshoe kerbs; the bayou's whitewashed vaults above the
 * wet ground. Graves lie along {@code head}: the foot cell, the body cell, then the marker.
 */
public final class Graves {
	private Graves() {
	}

	/** How a culture marks its dead. */
	public enum Manner {
		BOHEMIAN, NORTHERN, ALPINE, CELTIC, MEDITERRANEAN, DESERT, STEPPE, KARST, CREOLE
	}

	public static Manner of(Draft d) {
		return switch (Tradition.of(d)) {
			case TIMBER -> Manner.BOHEMIAN;
			case LOG -> Manner.NORTHERN;
			case ALPINE -> Manner.ALPINE;
			case STONE -> Manner.CELTIC;
			case MEDITERRANEAN -> Manner.MEDITERRANEAN;
			case ADOBE -> Manner.DESERT;
			case STEPPE -> Manner.STEPPE;
			case KARST -> Manner.KARST;
			case STILT -> Manner.CREOLE;
		};
	}

	/** Whether this manner's graves need the cells either side of the marker free (a cross's arms). */
	public static boolean wideMarkers(Manner m) {
		return m == Manner.BOHEMIAN || m == Manner.ALPINE || m == Manner.CELTIC || m == Manner.DESERT || m == Manner.MEDITERRANEAN
			|| m == Manner.NORTHERN;
	}

	private static BlockState b(String id, Block fallback) {
		return States.block(id, fallback).defaultBlockState();
	}

	/** The stele stone (a wall post: the shape a headstone reads as) of the manner, by noise. */
	static BlockState stele(Draft d, Manner m, double u) {
		return switch (m) {
			case BOHEMIAN -> u < 0.4 ? b("minecraft:andesite_wall", Blocks.COBBLESTONE_WALL) : u < 0.75 ? b("minecraft:stone_brick_wall",
				Blocks.COBBLESTONE_WALL) : b("minecraft:mossy_stone_brick_wall", Blocks.MOSSY_COBBLESTONE_WALL);
			case NORTHERN, STEPPE -> u < 0.5 ? b("minecraft:cobblestone_wall", Blocks.COBBLESTONE_WALL) : b("minecraft:mossy_cobblestone_wall",
				Blocks.MOSSY_COBBLESTONE_WALL);
			case ALPINE -> u < 0.6 ? b("minecraft:stone_brick_wall", Blocks.COBBLESTONE_WALL) : b("minecraft:andesite_wall", Blocks.COBBLESTONE_WALL);
			case CELTIC -> u < 0.4 ? b("minecraft:deepslate_brick_wall", Blocks.COBBLESTONE_WALL) : u < 0.7 ? b("minecraft:cobbled_deepslate_wall",
				Blocks.COBBLESTONE_WALL) : b("minecraft:mossy_cobblestone_wall", Blocks.MOSSY_COBBLESTONE_WALL);
			case MEDITERRANEAN, CREOLE -> u < 0.6 ? b("minecraft:diorite_wall", Blocks.COBBLESTONE_WALL) : b("minecraft:polished_diorite_slab",
				Blocks.SMOOTH_STONE_SLAB);
			case DESERT -> u < 0.6 ? b("minecraft:sandstone_wall", Blocks.COBBLESTONE_WALL) : b("minecraft:red_sandstone_wall", Blocks.COBBLESTONE_WALL);
			case KARST -> u < 0.5 ? b("minecraft:stone_brick_wall", Blocks.COBBLESTONE_WALL) : b("minecraft:polished_andesite", Blocks.ANDESITE);
		};
	}

	/** The cover slab of a new grave, or null where the manner leaves the earth bare. */
	static BlockState cover(Manner m, double u) {
		return switch (m) {
			case BOHEMIAN -> u < 0.5 ? b("minecraft:polished_andesite_slab", Blocks.SMOOTH_STONE_SLAB) : b("minecraft:smooth_stone_slab",
				Blocks.SMOOTH_STONE_SLAB);
			case CELTIC -> b("minecraft:deepslate_tile_slab", Blocks.SMOOTH_STONE_SLAB);
			case MEDITERRANEAN -> b("minecraft:smooth_quartz_slab", Blocks.SMOOTH_STONE_SLAB);
			case ALPINE, KARST -> u < 0.5 ? b("minecraft:polished_andesite_slab", Blocks.SMOOTH_STONE_SLAB) : null;
			default -> null;
		};
	}

	/** The wood of crosses: the style's frame timber where it has a fence, else spruce. */
	static Paint crossWood(Draft d) {
		Mat frame = d.mat(Role.FRAME);
		return frame.has(Form.FENCE) ? frame.fence() : Paint.of(Blocks.SPRUCE_FENCE.defaultBlockState());
	}

	/**
	 * One grave whose foot is at (x, z), standing level y (the ground there): the body cell one along {@code head}, the
	 * marker two along. {@code age} 0 (fresh) to 1 (old and grown over) shifts what it is: fresh graves of stone-cover
	 * manners are covered and lit, old ones are mounds under a cross; {@code wide} lets crosses spread their arms into
	 * the cells either side of the marker. Answers whether it lit the grave (a lantern or candles on the marker).
	 */
	public static boolean grave(Draft d, int x, int y, int z, Direction head, double age, boolean wide, boolean lamp) {
		Manner m = of(d);
		int bx = x + Dirs.dx(head);
		int bz = z + Dirs.dz(head);
		int mx = bx + Dirs.dx(head);
		int mz = bz + Dirs.dz(head);
		Direction side = Dirs.right(head);
		double u = Noise.unit(d.seed() ^ 0x96A7E, x, y, z);
		double v = Noise.unit(d.seed() ^ 0x5E1E, mx, y, mz);
		boolean fresh = age < 0.45;
		boolean rich = v > 0.82;
		boolean lit = false;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// the grave's own ground: covered, a chest tomb, a vault, or the mound with what grows on it
			BlockState cov = fresh ? cover(m, u) : null;
			if (m == Manner.CREOLE) {
				// a whitewashed vault two high over foot and body, its marker carried on it
				BlockState white = b("minecraft:smooth_quartz", Blocks.SMOOTH_SANDSTONE);
				for (int[] c : new int[][]{{x, z}, {bx, bz}}) {
					d.set(c[0], y, c[1], white);
				}
				d.set(x, y + 1, z, b("minecraft:smooth_quartz_slab", Blocks.SMOOTH_STONE_SLAB));
				d.set(bx, y + 1, bz, white);
				d.set(mx, y, mz, white);
				d.set(mx, y + 1, mz, white);
				d.set(bx, y + 2, bz, b("minecraft:quartz_stairs", Blocks.STONE_STAIRS).trySetValue(BlockStateProperties.HORIZONTAL_FACING, head.getOpposite()));
				d.set(mx, y + 2, mz, Blocks.IRON_BARS.defaultBlockState());
				if (lamp) {
					d.set(mx, y + 3, mz, States.lantern(Blocks.LANTERN, false));
					lit = true;
				}
				return lit;
			}
			if (rich && (m == Manner.MEDITERRANEAN || m == Manner.BOHEMIAN || m == Manner.CELTIC)) {
				// a chest tomb: a block of dressed stone over foot and body under a slab lid
				BlockState chest = m == Manner.MEDITERRANEAN ? b("minecraft:smooth_quartz", Blocks.SMOOTH_STONE)
					: m == Manner.CELTIC ? b("minecraft:polished_deepslate", Blocks.SMOOTH_STONE) : b("minecraft:polished_andesite", Blocks.SMOOTH_STONE);
				d.set(x, y, z, chest);
				d.set(bx, y, bz, chest);
			} else if (cov != null) {
				d.set(x, y, z, cov);
				d.set(bx, y, bz, cov);
			} else {
				// the mound: fresh earth or old turf
				BlockState earth = fresh ? (u < 0.5 ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT).defaultBlockState()
					: (u < 0.5 ? Blocks.PODZOL : Blocks.GRASS_BLOCK).defaultBlockState();
				if (m == Manner.DESERT) {
					earth = (u < 0.5 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState();
				}
				d.set(x, y - 1, z, earth);
				d.set(bx, y - 1, bz, earth);
				if (m == Manner.KARST) {
					// the horseshoe kerb round the mound
					for (int[] c : new int[][]{{x + Dirs.dx(side), z + Dirs.dz(side)}, {x - Dirs.dx(side), z - Dirs.dz(side)},
						{bx + Dirs.dx(side), bz + Dirs.dz(side)}, {bx - Dirs.dx(side), bz - Dirs.dz(side)}}) {
						if (wide && d.isEmpty(c[0], y, c[1])) {
							d.set(c[0], y, c[1], b("minecraft:stone_brick_wall", Blocks.COBBLESTONE_WALL));
						}
					}
				}
				if (m == Manner.STEPPE || m == Manner.NORTHERN && u < 0.3) {
					// a mound raised a little, grass on the old ones
					d.set(bx, y, bz, (fresh ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT).defaultBlockState());
					if (!fresh && d.isEmpty(bx, y + 1, bz)) {
						d.set(bx, y + 1, bz, Blocks.SHORT_GRASS.defaultBlockState());
					}
				} else if (d.isEmpty(x, y, z)) {
					double f = Noise.unit(d.seed() ^ 0xF10, x, y, z);
					if (f < 0.45 && m != Manner.DESERT) {
						Garden.flower(d, x, y, z);
					} else if (f < 0.6) {
						d.set(x, y, z, Blocks.SHORT_GRASS.defaultBlockState());
					}
				}
			}
			// the marker
			boolean cross = wideMarkers(m) && wide && (!fresh || m == Manner.ALPINE || m == Manner.DESERT || u > 0.7);
			if (cross) {
				Paint shaft;
				boolean iron = m == Manner.ALPINE || (m == Manner.BOHEMIAN || m == Manner.MEDITERRANEAN) && v < 0.4;
				if (m == Manner.CELTIC) {
					shaft = Paint.of(stele(d, m, v));
				} else if (iron) {
					shaft = Paint.of(Blocks.IRON_BARS.defaultBlockState());
				} else if (m == Manner.DESERT) {
					shaft = Paint.of(Blocks.BIRCH_FENCE.defaultBlockState());
				} else {
					shaft = crossWood(d);
				}
				d.set(mx, y, mz, shaft);
				d.set(mx, y + 1, mz, shaft);
				d.set(mx, y + 2, mz, shaft);
				for (int k : new int[]{-1, 1}) {
					int ax = mx + Dirs.dx(side) * k;
					int az = mz + Dirs.dz(side) * k;
					if (d.isEmpty(ax, y + 1, az) && d.allowed(ax, y + 1, az)) {
						d.set(ax, y + 1, az, shaft);
					}
				}
				if (m == Manner.ALPINE || m == Manner.NORTHERN && v > 0.6) {
					// the little roof over the cross
					Mat roof = d.mat(Role.ROOF);
					d.set(mx, y + 3, mz, roof.slab(SlabType.BOTTOM).orElse(Paint.of(Blocks.SPRUCE_SLAB.defaultBlockState())));
				} else if (lamp && !iron) {
					d.set(mx, y + 3, mz, States.lantern(Blocks.LANTERN, false));
					lit = true;
				}
			} else {
				BlockState st = stele(d, m, v);
				boolean tall = rich || m == Manner.STEPPE || m == Manner.NORTHERN && u > 0.5;
				boolean post = st.getBlock() instanceof net.minecraft.world.level.block.WallBlock;
				int top;
				if (m == Manner.STEPPE || m == Manner.NORTHERN && tall) {
					// a standing stone
					BlockState rock = m == Manner.STEPPE ? b("minecraft:andesite", Blocks.STONE) : b("minecraft:mossy_cobblestone", Blocks.COBBLESTONE);
					d.set(mx, y, mz, rock);
					d.set(mx, y + 1, mz, st);
					top = y + 2;
				} else if (tall && post) {
					d.set(mx, y, mz, st);
					d.set(mx, y + 1, mz, st);
					top = y + 2;
				} else {
					d.set(mx, y, mz, st);
					top = y + 1;
				}
				boolean slab = st.getBlock() instanceof net.minecraft.world.level.block.SlabBlock && top == y + 1;
				if (lamp && !slab) {
					d.set(mx, top, mz, States.lantern(Blocks.LANTERN, false));
					lit = true;
				} else if (!slab && Noise.unit(d.seed() ^ 0xCA4D, mx, y, mz) < 0.5) {
					int n = 1 + (int) (Noise.unit(d.seed() ^ 0xCA4E, mx, y, mz) * 3);
					lit = n >= 2;
					d.set(mx, top, mz, States.of(Blocks.CANDLE).setValue(BlockStateProperties.CANDLES, n).setValue(BlockStateProperties.LIT, true));
				}
			}
		}
		return lit;
	}

	/** Lit candles standing outdoors (an exterior fitting) at (x, y, z), in the culture's colours. */
	public static void candles(Draft d, int x, int y, int z, int n) {
		Lighting.candles(d, x, y, z, n);
		var c = d.cell(x, y, z);
		if (c != null) {
			d.remove(x, y, z);
			BlockState st = c.state();
			d.inStage(StageKind.EXTERIOR, () -> d.set(x, y, z, st));
		}
	}

	/** The cells a grave takes (foot, body, marker), and the cross's arms when {@code wide}. */
	public static int[][] cells(int x, int z, Direction head, boolean wide) {
		int bx = x + Dirs.dx(head);
		int bz = z + Dirs.dz(head);
		int mx = bx + Dirs.dx(head);
		int mz = bz + Dirs.dz(head);
		if (!wide) {
			return new int[][]{{x, z}, {bx, bz}, {mx, mz}};
		}
		Direction s = Dirs.right(head);
		return new int[][]{{x, z}, {bx, bz}, {mx, mz}, {mx + Dirs.dx(s), mz + Dirs.dz(s)}, {mx - Dirs.dx(s), mz - Dirs.dz(s)}};
	}

	// ------------------------------------------------------------------------------------------- trees

	/**
	 * A churchyard yew (a dark, dense, rounded tree on a short trunk) or, in the south, a cypress (a dark spire), its foot at
	 * (x, y, z) on the ground, at most {@code height} tall, kept to the plot.
	 */
	public static void yew(Draft d, int x, int y, int z, int height) {
		Manner m = of(d);
		if (m == Manner.MEDITERRANEAN || m == Manner.DESERT || m == Manner.CREOLE && height >= 6) {
			cypress(d, x, y, z, height);
			return;
		}
		height = Math.min(height, d.maxHeight() - 1 - y);
		if (height < 3) {
			return;
		}
		BlockState leaf = States.leaves(m == Manner.KARST ? Blocks.SPRUCE_LEAVES : Blocks.SPRUCE_LEAVES);
		BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
		int crown0 = y + 1;
		int top = y + height - 1;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, Blocks.DIRT.defaultBlockState());
			for (int yy = y; yy < top; yy++) {
				d.set(x, yy, z, log);
			}
			for (int yy = crown0; yy <= top; yy++) {
				double t = (yy - crown0) / (double) Math.max(1, top - crown0);
				int r = t < 0.25 || t > 0.7 ? 1 : 2;
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (dx == 0 && dz == 0 && yy < top) {
							continue;
						}
						boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
						if (corner && (r == 1 || Noise.unit(d.seed() ^ 0x7E1, x + dx, yy, z + dz) < 0.75)) {
							continue;
						}
						int lx = x + dx;
						int lz = z + dz;
						if (lx < 0 || lz < 0 || lx >= d.frontage() || lz >= d.depth()) {
							continue;
						}
						d.setIfAir(lx, yy, lz, Paint.of(leaf));
					}
				}
			}
			d.setIfAir(x, top + 1, z, Paint.of(leaf));
		}
	}

	/** A cypress: a narrow dark spire of leaves round a hidden trunk. */
	public static void cypress(Draft d, int x, int y, int z, int height) {
		height = Math.min(height, d.maxHeight() - 1 - y);
		if (height < 3) {
			return;
		}
		BlockState leaf = States.leaves(Blocks.SPRUCE_LEAVES);
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		int top = y + height - 1;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			d.set(x, y - 1, z, Blocks.DIRT.defaultBlockState());
			for (int yy = y; yy < top; yy++) {
				d.set(x, yy, z, log);
			}
			for (int yy = y + 1; yy <= top; yy++) {
				double t = (yy - y) / (double) height;
				boolean full = t > 0.2 && t < 0.7;
				for (Direction dir : Dirs.HORIZONTAL) {
					int lx = x + Dirs.dx(dir);
					int lz = z + Dirs.dz(dir);
					if ((full || Noise.unit(d.seed() ^ 0xC79, lx, yy, lz) < 0.5) && lx >= 0 && lz >= 0 && lx < d.frontage() && lz < d.depth()) {
						d.setIfAir(lx, yy, lz, Paint.of(leaf));
					}
				}
			}
			d.setIfAir(x, top, z, Paint.of(leaf));
			d.setIfAir(x, top + 1, z, Paint.of(leaf));
		}
	}

	// ------------------------------------------------------------------------------------------- monuments

	/**
	 * The cross at the heart of a burial ground, facing {@code facing}: a stepped plinth of the accent stone, a shaft and
	 * arms of the stele stone (a crucifix of timber under a little roof in the timber cultures, a stone lantern in the
	 * karst), lanterns standing at its foot. Its foot is the cell (x, y, z); the plinth's step ring takes the 3 x 3 round
	 * it. Returns the top y.
	 */
	public static int calvary(Draft d, int x, int y, int z, Direction facing) {
		Manner m = of(d);
		Mat accent = d.mat(Role.WALL_ACCENT);
		Mat found = d.mat(Role.FOUNDATION);
		Direction side = Dirs.right(facing);
		int top;
		try (var s = d.stage(StageKind.EXTERIOR)) {
			// the step ring
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					d.set(x + dx, y - 1, z + dz, found.full());
					d.set(x + dx, y, z + dz, accent.has(Form.SLAB) ? accent.slab(SlabType.BOTTOM) : found.slab(SlabType.BOTTOM));
				}
			}
			d.set(x, y - 1, z, found.full());
			d.set(x, y, z, accent.full());
			if (m == Manner.KARST) {
				// a stone lantern in place of a cross
				d.set(x, y + 1, z, stele(d, m, 0.1));
				d.set(x, y + 2, z, States.lantern(Blocks.LANTERN, false));
				d.set(x, y + 3, z, accent.slab(SlabType.BOTTOM));
				return y + 3;
			}
			boolean timber = m == Manner.BOHEMIAN || m == Manner.ALPINE || m == Manner.NORTHERN;
			Paint shaft = timber ? Paint.of(Blocks.DARK_OAK_FENCE.defaultBlockState()) : Paint.of(stele(d, m == Manner.CREOLE || m == Manner.MEDITERRANEAN
				? Manner.MEDITERRANEAN : m, 0.1));
			if (!timber && shaft.at(x, y, z) != null && shaft.at(x, y, z).getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
				shaft = Paint.of(Blocks.STONE_BRICK_WALL.defaultBlockState());
			}
			int h = Math.min(4, d.maxHeight() - 2 - y);
			for (int k = 1; k <= h; k++) {
				d.set(x, y + k, z, shaft);
			}
			int arm = y + Math.max(2, h - 1);
			for (int k : new int[]{-1, 1}) {
				d.set(x + Dirs.dx(side) * k, arm, z + Dirs.dz(side) * k, shaft);
			}
			top = y + h;
			if (timber && top + 1 < d.maxHeight()) {
				// the crucifix's little roof
				Mat roof = d.mat(Role.ROOF);
				d.set(x, top + 1, z, roof.slab(SlabType.BOTTOM).orElse(Paint.of(Blocks.SPRUCE_SLAB.defaultBlockState())));
				for (int k : new int[]{-1, 1}) {
					d.set(x + Dirs.dx(side) * k, top, z + Dirs.dz(side) * k, roof.stairsOrSlab(k < 0 ? side : side.getOpposite(), Half.BOTTOM));
				}
				top++;
			}
		}
		// lanterns at the foot, on the front corners of the step ring
		for (int k : new int[]{-1, 1}) {
			int lx = x + Dirs.dx(side) * k + Dirs.dx(facing);
			int lz = z + Dirs.dz(side) * k + Dirs.dz(facing);
			try (var s = d.stage(StageKind.EXTERIOR)) {
				d.set(lx, y, lz, accent.full());
			}
			candles(d, lx, y + 1, lz, 3);
		}
		return top;
	}
}
