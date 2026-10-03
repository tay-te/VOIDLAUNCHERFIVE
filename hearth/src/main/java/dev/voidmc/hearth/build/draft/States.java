package dev.voidmc.hearth.build.draft;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * Block-state shorthands for blueprints, all in the local frame (see {@link Dirs}). Properties a block does not have
 * are skipped ({@code trySetValue}), so these work for modded blocks of the same kind.
 */
public final class States {
	private States() {
	}

	public static BlockState of(Block b) {
		return b.defaultBlockState();
	}

	/** A block by id, or the fallback when it is not registered. */
	public static Block block(String id, Block fallback) {
		Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
		if (key != null && BuiltInRegistries.BLOCK.containsKey(key)) {
			return BuiltInRegistries.BLOCK.getValue(key);
		}
		return fallback;
	}

	/** A stair whose high back faces {@code facing}. */
	public static BlockState stairs(Block b, Direction facing, Half half) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.HALF, half);
	}

	public static BlockState slab(Block b, SlabType type) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.SLAB_TYPE, type);
	}

	public static BlockState axis(Block b, Direction.Axis axis) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.AXIS, axis);
	}

	/** A trapdoor; open ones stand against the side opposite {@code facing} (a shutter faces out from its wall). */
	public static BlockState trapdoor(Block b, Direction facing, Half half, boolean open) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.HALF, half)
			.trySetValue(BlockStateProperties.OPEN, open);
	}

	/**
	 * One half of a door. {@code facing} is the way a person looks when walking in through it; the leaf sits on the
	 * side they come from (facing the back, the leaf is flush with the front of its block).
	 */
	public static BlockState door(Block b, Direction facing, DoorHingeSide hinge, DoubleBlockHalf half) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.DOOR_HINGE, hinge)
			.trySetValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half).trySetValue(BlockStateProperties.OPEN, false);
	}

	/** One half of a bed; {@code facing} points from the foot to the head. */
	public static BlockState bed(Block b, Direction facing, BedPart part) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.BED_PART, part);
	}

	public static BlockState facing(Block b, Direction facing) {
		BlockState s = b.defaultBlockState();
		if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && facing.getAxis().isHorizontal()) {
			return s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		}
		return s.trySetValue(BlockStateProperties.FACING, facing);
	}

	public static BlockState gate(Block b, Direction facing, boolean open) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing).trySetValue(BlockStateProperties.OPEN, open);
	}

	public static BlockState lantern(Block b, boolean hanging) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.HANGING, hanging);
	}

	/** A torch on the wall behind it, pointing {@code facing} (away from the wall). */
	public static BlockState wallTorch(Block torch, Direction facing) {
		Block wall = wallVariant(torch);
		return wall.defaultBlockState().trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	/** The wall-mounted form of a standing torch, sign or banner, by vanilla naming; the block itself if none. */
	public static Block wallVariant(Block standing) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(standing);
		String p = id.getPath();
		String wall = switch (p) {
			case "torch" -> "wall_torch";
			case "soul_torch" -> "soul_wall_torch";
			case "redstone_torch" -> "redstone_wall_torch";
			case "copper_torch" -> "copper_wall_torch";
			default -> p.endsWith("_sign") ? p.replace("_sign", "_wall_sign")
				: p.endsWith("_banner") ? p.replace("_banner", "_wall_banner") : p;
		};
		Identifier w = Identifier.fromNamespaceAndPath(id.getNamespace(), wall);
		return BuiltInRegistries.BLOCK.containsKey(w) ? BuiltInRegistries.BLOCK.getValue(w) : standing;
	}

	/** A button, lever or grindstone-like block on a face. */
	public static BlockState attached(Block b, AttachFace face, Direction facing) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.ATTACH_FACE, face).trySetValue(BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	public static BlockState lit(Block b, boolean lit) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.LIT, lit);
	}

	/** Grown crops (age set to the block's maximum, or the given age). */
	public static BlockState crop(Block b, int age) {
		BlockState s = b.defaultBlockState();
		for (var p : s.getProperties()) {
			if (p.getName().equals("age") && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty ip) {
				int max = ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
				return s.setValue(ip, Math.min(max, age));
			}
		}
		return s;
	}

	public static BlockState leaves(Block b) {
		return b.defaultBlockState().trySetValue(BlockStateProperties.PERSISTENT, true).trySetValue(BlockStateProperties.DISTANCE, 1);
	}

	public static BlockState snow(int layers) {
		return Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, Math.clamp(layers, 1, 8));
	}

	public static boolean is(@Nullable BlockState s, Block b) {
		return s != null && s.is(b);
	}
}
