package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** Those without a trade keep house: the garden, the doorstep, an errand to the market or the well. */
final class ChoresWork implements WorkBehaviour {
	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		BlockPos door = p.homeDoor != null ? p.homeDoor.pos() : p.home;
		return switch (brain.random.nextInt(4)) {
			case 0 -> new Tasks.Linger(door, null, FolkPose.TEND, 400, ItemStack.EMPTY, ItemStack.EMPTY, null, "tending the garden");
			case 1 -> {
				List<Poi> market = brain.town.places.all(level, brain.settlement, PoiType.MARKET_STALL);
				Poi stall = market.isEmpty() ? null : market.get(brain.random.nextInt(market.size()));
				yield stall != null ? new Tasks.Linger(stall.pos().relative(stall.facing(), 2), stall.pos(), FolkPose.NONE, 300,
					new ItemStack(Items.BUNDLE), ItemStack.EMPTY, null, "at the market") : new Tasks.Wander(door, 8, 3);
			}
			case 2 -> {
				Poi table = p.homeTables.isEmpty() ? null : p.homeTables.get(0);
				yield new Tasks.Linger(table != null ? table.pos() : p.home, null, FolkPose.CRAFT, 400, ItemStack.EMPTY, ItemStack.EMPTY, null,
					"busy about the house");
			}
			default -> new Tasks.Wander(door, 6, 2);
		};
	}
}
