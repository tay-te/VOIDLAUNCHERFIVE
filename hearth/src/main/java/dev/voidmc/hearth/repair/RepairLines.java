package dev.voidmc.hearth.repair;

import dev.voidmc.hearth.api.PlotSpec;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What builders say about the damage they mend ("some fool smashed the chapel window"): the building by its kind, the
 * part by the block, the tone by what did it.
 */
public final class RepairLines {
	private RepairLines() {
	}

	/** The building's name as folk say it: "chapel", "builders yard", "town hall". */
	public static String building(PlotSpec spec) {
		String path = spec.archetype().getPath();
		String name = path.substring(path.indexOf('/') + 1).replace('_', ' ');
		return switch (name) {
			case "road", "lane", "path" -> "road";
			case "stairs" -> "stair";
			default -> name.toLowerCase(Locale.ROOT);
		};
	}

	/** The part of a building a block is, as a lang key suffix: window, door, roof, wall, fence, lamp, bed, floor. */
	public static String part(BlockState state, int y, int floorY, int maxHeight) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (id.contains("glass")) {
			return "window";
		}
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock) {
			return "door";
		}
		if (state.getBlock() instanceof TrapDoorBlock) {
			return "shutter";
		}
		if (id.contains("lantern") || id.contains("torch") || id.contains("candle") || id.contains("lamp")) {
			return "lamp";
		}
		if (state.getBlock() instanceof BedBlock) {
			return "bed";
		}
		if (state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock && y <= floorY + 1) {
			return "fence";
		}
		if ((state.getBlock() instanceof StairBlock || state.getBlock() instanceof SlabBlock) && y >= floorY + 3 || y >= floorY + Math.max(4, maxHeight - 3)) {
			return "roof";
		}
		if (y < floorY) {
			return "floor";
		}
		return "wall";
	}

	/** The line a builder says on starting a repair. */
	public static Component line(Cause cause, PlotSpec spec, BlockState state, int y, RandomSource random) {
		Component building = Component.literal(building(spec));
		Component part = Component.translatable("hearth.repair.part." + part(state, y, spec.floorY(), spec.maxHeight()));
		String kind = switch (cause) {
			case PLAYER -> "player";
			case EXPLOSION -> "explosion";
			case FIRE -> "fire";
			case OTHER -> "other";
		};
		int variant = random.nextInt(3);
		return Component.translatable("hearth.repair.say." + kind + "." + variant, building, part);
	}

	/** A line for clearing a doorway or a street. */
	public static Component litterLine(PlotSpec spec, RandomSource random) {
		return Component.translatable("hearth.repair.say.litter." + random.nextInt(2), Component.literal(building(spec)));
	}
}
