package dev.voidmc.hearth.build.kit.age;

import dev.voidmc.hearth.build.draft.Dirs;
import dev.voidmc.hearth.build.style.Form;
import dev.voidmc.hearth.build.style.Material;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * What a fallen block leaves on the floor: masonry breaks into slabs and steps of its own stone (or rubble cobble), a
 * roof's tiles and shingles lie as loose courses, boards as planks and a board door, beams as beams. The pieces are the
 * building's own materials, so the rubble in a brick house is brick and in a sandstone one sandstone.
 */
public final class Rubble {
	/** A block's family: its slab, its stairs, its full block, its trapdoor (any may be null). */
	private record Family(@Nullable Block slab, @Nullable Block stairs, @Nullable Block full, @Nullable Block trapdoor) {
	}

	private static final Map<Block, Family> FAMILIES = new ConcurrentHashMap<>();

	private Rubble() {
	}

	/**
	 * A piece of rubble from a fallen block, lying on the floor; {@code u} and {@code v} are uniform choices in [0, 1).
	 * Null where the fall leaves nothing that shows (glass, cloth, plants).
	 */
	public static @Nullable BlockState piece(BlockState fallen, double u, double v) {
		Block b = fallen.getBlock();
		if (fallen.isAir() || !fallen.getFluidState().isEmpty() || Weather.soft(fallen) && !(b == Blocks.HAY_BLOCK)) {
			return null;
		}
		String n = Weather.name(b);
		if (n.contains("glass") || n.contains("pane") || n.contains("leaves") || n.contains("lantern") || n.contains("chain") || n.contains("candle")
			|| n.contains("torch") || n.contains("bars")) {
			return null;
		}
		Direction facing = Dirs.HORIZONTAL[(int) (v * 4) & 3];
		if (b instanceof RotatedPillarBlock && Weather.timber(fallen)) {
			// a beam lies where it fell
			return fallen.setValue(RotatedPillarBlock.AXIS, v < 0.5 ? Direction.Axis.X : Direction.Axis.Z);
		}
		if (b == Blocks.HAY_BLOCK) {
			return fallen.setValue(RotatedPillarBlock.AXIS, v < 0.5 ? Direction.Axis.X : Direction.Axis.Z);
		}
		Family f = family(b);
		boolean masonry = Weather.masonry(fallen);
		if (Weather.timber(fallen)) {
			if (f.trapdoor != null && u < 0.35) {
				return f.trapdoor.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.HALF, Half.BOTTOM)
					.setValue(TrapDoorBlock.OPEN, false);
			}
			if (f.slab != null) {
				return f.slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
			}
			return null;
		}
		if (!masonry) {
			return null;
		}
		if (u < 0.5 && f.slab != null) {
			return f.slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		}
		if (u < 0.78 && f.stairs != null) {
			return f.stairs.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM);
		}
		if (u < 0.9 && f.full != null) {
			return f.full.defaultBlockState();
		}
		// loose rubble of whatever the wall was laid in
		return (v < 0.5 ? Blocks.COBBLESTONE_SLAB : Blocks.MOSSY_COBBLESTONE_SLAB).defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
	}

	/** The trapdoor of a door's wood (an oak door's oak trapdoor), for a door fallen off its hinges; null if none. */
	public static @Nullable Block doorLeaf(Block door) {
		return family(door).trapdoor;
	}

	private static Family family(Block b) {
		return FAMILIES.computeIfAbsent(b, Rubble::resolve);
	}

	private static Family resolve(Block b) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(b);
		String ns = id.getNamespace() + ":";
		String n = id.getPath();
		String stem = n;
		for (String suffix : new String[]{"_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_door", "_trapdoor", "_planks", "_pressure_plate"}) {
			if (n.endsWith(suffix)) {
				stem = n.substring(0, n.length() - suffix.length());
				break;
			}
		}
		Block slab = find(ns + stem + "_slab");
		Block stairs = find(ns + stem + "_stairs");
		Block trapdoor = find(ns + stem + "_trapdoor");
		Block full = null;
		for (String c : new String[]{ns + stem + "s", ns + stem, ns + stem + "_planks", ns + stem + "_block"}) {
			Block f = find(c);
			if (f != null && f.defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
				net.minecraft.core.BlockPos.ZERO)) {
				full = f;
				break;
			}
		}
		if (slab == null || stairs == null) {
			// a full block: its cuts by vanilla naming (stone_bricks: stone_brick_slab)
			Material m = Material.of(id.toString());
			if (slab == null) {
				slab = m.block(Form.SLAB);
			}
			if (stairs == null) {
				stairs = m.block(Form.STAIRS);
			}
			if (full == null && b.defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
				net.minecraft.core.BlockPos.ZERO)) {
				full = b;
			}
		}
		return new Family(slab, stairs, full, trapdoor);
	}

	private static @Nullable Block find(String id) {
		Identifier i = Identifier.tryParse(id);
		return i != null && BuiltInRegistries.BLOCK.containsKey(i) ? BuiltInRegistries.BLOCK.getValue(i) : null;
	}
}
