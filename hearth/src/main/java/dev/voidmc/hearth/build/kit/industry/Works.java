package dev.voidmc.hearth.build.kit.industry;

import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.StageKind;
import dev.voidmc.hearth.build.draft.Draft;
import dev.voidmc.hearth.build.draft.Mat;
import dev.voidmc.hearth.build.draft.Noise;
import dev.voidmc.hearth.build.draft.States;
import dev.voidmc.hearth.build.kit.trade.Hall;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Habits;
import dev.voidmc.hearth.build.style.Role;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the heavy workshops share: which building tradition a culture works in (timber sheds and stone furnaces in the
 * north and west, clay and mud in the dry lands, bamboo and thatch in the tropics), the masonry their fires are built
 * of, and small helpers for the ground and the heaps of a working yard. Everything reads the style, so the same
 * archetype dresses as itself in every culture.
 */
public final class Works {
	private Works() {
	}

	/** The broad building traditions the workshops are drawn in. */
	public enum Tradition {
		/** Timber halls and sheds, stone and brick furnaces: temperate, wisteria, northern, alpine, tundra, moorland, maritime. */
		TIMBER,
		/** Flat roofs, earthen and stone walls, clay furnaces: desert, mesa. */
		EARTH,
		/** Rendered walls under low tile roofs: mediterranean. */
		SOUTH,
		/** Mud walls under cones of thatch, clay shaft furnaces: steppe, tropical. */
		THATCH,
		/** Bamboo, limestone and glazed tile: karst. */
		EAST,
		/** Planks on piles under tin and copper: bayou. */
		MARSH
	}

	public static Culture culture(Draft d) {
		Culture c = d.style().culture();
		return c == null ? Culture.TEMPERATE : c;
	}

	public static Tradition tradition(Draft d) {
		return switch (culture(d)) {
			case DESERT, MESA -> Tradition.EARTH;
			case MEDITERRANEAN -> Tradition.SOUTH;
			case STEPPE, TROPICAL -> Tradition.THATCH;
			case KARST -> Tradition.EAST;
			case BAYOU -> Tradition.MARSH;
			default -> Hall.flatCulture(d) ? Tradition.EARTH : Tradition.TIMBER;
		};
	}

	/** Whether the culture lives under snow part of the year (the north, the tundra, the alps). */
	public static boolean cold(Draft d) {
		Culture c = culture(d);
		return c == Culture.NORTHERN || c == Culture.TUNDRA || c == Culture.ALPINE || d.habits().snow();
	}

	/** Whether the culture is hot and dry (its yards are sand and packed earth). */
	public static boolean dry(Draft d) {
		Culture c = culture(d);
		return c == Culture.DESERT || c == Culture.MESA || c == Culture.STEPPE;
	}

	/**
	 * The stone the culture builds its fires in: the masonry role where it can be cut into stairs and slabs (stone brick,
	 * sandstone, limestone, mud brick), else the chimney's, else the footings'.
	 */
	public static Mat masonry(Draft d) {
		Mat m = d.mat(Role.MASONRY);
		if (m.has(Form.STAIRS) && m.has(Form.SLAB)) {
			return m;
		}
		Mat c = d.mat(Role.CHIMNEY);
		if (c.has(Form.STAIRS) && c.has(Form.SLAB)) {
			return c;
		}
		return d.mat(Role.FOUNDATION);
	}

	/** Rough stone for footings, kerbs and bulk (the footings' role where it is stone, else the masonry). */
	public static Mat rubble(Draft d) {
		Mat f = d.mat(Role.FOUNDATION);
		Block b = f.main(Form.FULL);
		if (b != null && !isWood(b)) {
			return f;
		}
		return masonry(d);
	}

	/** The round timber the culture fells (its tree's logs where it has them, else its frame wood). */
	public static Mat logs(Draft d) {
		Mat t = d.mat(Role.TREE_LOG);
		return t.has(Form.LOG) ? t : d.mat(Role.FRAME);
	}

	/** Whether a block is cut from wood (planks, logs and their cuts). */
	public static boolean isWood(Block b) {
		String p = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
		return p.endsWith("_planks") || p.endsWith("_log") || p.endsWith("_wood") || p.contains("bamboo") || p.endsWith("_stem") && !p.equals("mushroom_stem")
			|| p.endsWith("_hyphae");
	}

	/** A block by id with a vanilla fallback (Expanse ids resolve only with Expanse installed). */
	public static Block block(String id, Block fallback) {
		return States.block(id, fallback);
	}

	/** A uniform value in [0, 1) for a cell, salted per use so neighbouring choices do not repeat. */
	public static double u(Draft d, int salt, int x, int y, int z) {
		return Noise.unit(d.seed() ^ (0x1D057L * salt), x, y, z);
	}

	/** One of the blocks by a per-cell hash. */
	public static BlockState mix(Draft d, int salt, int x, int y, int z, Block... blocks) {
		return blocks[(int) (u(d, salt, x, y, z) * blocks.length) % blocks.length].defaultBlockState();
	}

	/** One of the blocks by patchy noise (neighbours agree, so it comes in patches rather than salt and pepper). */
	public static BlockState patch(Draft d, int salt, double scale, int x, int y, int z, Block... blocks) {
		double v = Noise.value(d.seed() ^ (0x9A7CL * salt), x / scale, y / scale, z / scale);
		return blocks[Math.min(blocks.length - 1, (int) (v * blocks.length))].defaultBlockState();
	}

	/** Whether the cell is inside the plot's footprint. */
	public static boolean in(Draft d, int x, int z) {
		return x >= 0 && z >= 0 && x < d.frontage() && z < d.depth();
	}

	/** Sets a block in a stage only where nothing is drawn yet, inside the plot. */
	public static void put(Draft d, int x, int y, int z, BlockState s, StageKind stage) {
		if (in(d, x, z) && d.isAbsent(x, y, z) && d.allowed(x, y, z)) {
			try (var sc = d.stage(stage)) {
				d.set(x, y, z, s);
			}
		}
	}

	/** The wood the culture's carts, racks and stakes are made of (its furniture wood if wooden, else spruce). */
	public static Mat timber(Draft d) {
		Mat f = d.mat(Role.FURNITURE);
		if (f.has(Form.FENCE) && f.has(Form.TRAPDOOR)) {
			return f;
		}
		Mat fr = d.mat(Role.FRAME);
		return fr.has(Form.FENCE) ? fr : f;
	}

	/** Whether a style's roofs are thatched by habit. */
	public static boolean thatched(Draft d) {
		Habits.Roof r = d.habits().roof();
		return r.thatch();
	}

	/**
	 * A store (chest or barrel) at (x, y, z) used from {@code user}'s side, only if its cell and the cell its user
	 * stands in are free (with headroom). Returns whether it went in.
	 */
	public static boolean store(Draft d, int x, int y, int z, net.minecraft.core.Direction user, boolean chest, java.util.List<Object[]> items) {
		int ux = x + dev.voidmc.hearth.build.draft.Dirs.dx(user);
		int uz = z + dev.voidmc.hearth.build.draft.Dirs.dz(user);
		if (!in(d, x, z) || !in(d, ux, uz) || !d.isEmpty(x, y, z) || !d.isEmpty(ux, y, uz) || !d.isEmpty(ux, y + 1, uz)) {
			return false;
		}
		dev.voidmc.hearth.build.kit.trade.Props.store(d, x, y, z, user, chest, items);
		return true;
	}

	/** Container contents from id, count pairs: {@code items("minecraft:charcoal", 32, "minecraft:bread", 4)}. */
	public static java.util.List<Object[]> items(Object... pairs) {
		java.util.List<Object[]> out = new java.util.ArrayList<>();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			out.add(new Object[]{pairs[i], pairs[i + 1]});
		}
		return out;
	}

	/** Coarse sacking for sacks and tarpaulins: the culture's brown, or white wool where sacks are pale. */
	public static Block sacking(Draft d, boolean pale) {
		return Blocks.WOOL.pick(pale ? net.minecraft.world.item.DyeColor.WHITE : net.minecraft.world.item.DyeColor.BROWN);
	}

	/** Whether (x, z) is the outside cell of a door drawn so far (or one of its sides, with {@code margin} 1). */
	public static boolean atDoor(Draft d, int x, int z, int margin) {
		for (int[] e : d.entrances()) {
			if (e[2] >= 0 && Math.abs(e[0] - x) <= margin && Math.abs(e[2] - z) <= margin) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The culture's yard light standing at (x, y, z), or at the nearest free cell round it that is not in front of a
	 * door (a post on a doorstep shuts the door). Answers whether one went in.
	 */
	public static boolean lampPost(Draft d, int x, int y, int z) {
		for (int r = 0; r <= 2; r++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int px = x + dx;
					int pz = z + dz;
					if (in(d, px, pz) && !atDoor(d, px, pz, 0) && d.isEmpty(px, y, pz) && d.isEmpty(px, y + 1, pz) && d.isEmpty(px, y + 2, pz)
						&& (d.isAbsent(px, y - 1, pz) || !d.isEmpty(px, y - 1, pz))) {
						dev.voidmc.hearth.build.kit.light.Lighting.post(d, px, y, pz);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Keeps every door's doorstep open: whatever a later heap, post or prop put in the outside cell of a door (feet and
	 * head) is taken out again, and a lamp or fire left standing on nothing above it goes too. Call at the end of a draw.
	 */
	public static void keepDoors(Draft d) {
		for (int[] e : d.entrances()) {
			int x = e[0];
			int y = e[1];
			int z = e[2];
			if (!in(d, x, z)) {
				continue;
			}
			for (int yy = y; yy <= y + 1; yy++) {
				// a prop placed there, or the hillside the levelling left standing
				if (!d.isEmpty(x, yy, z) || d.isAbsent(x, yy, z) && yy < d.site().ground(x, z)) {
					d.air(x, yy, z);
				}
			}
			BlockState above = d.get(x, y + 2, z);
			if (above != null && !above.isAir() && !above.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
				net.minecraft.core.BlockPos.ZERO) && !(above.getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
				&& !(above.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
				&& !(above.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock)) {
				d.air(x, y + 2, z);
			}
		}
	}
}
