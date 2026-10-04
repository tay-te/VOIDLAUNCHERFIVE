package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.JobType;
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

/**
 * Keeping shop: merchants at their stall or counter during opening hours, the innkeeper behind the bar and round the
 * tables, the minter at the bank's counter. They face their customers' side; the conversation (and the economy's
 * trade option) is how a player buys.
 */
final class CounterWork implements WorkBehaviour {
	private final JobType job;
	private int round;

	CounterWork(JobType job) {
		this.job = job;
	}

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		ResidentPlaces p = brain.places(level);
		Poi counter = counter(p);
		this.round++;
		if (this.job == JobType.INNKEEPER && this.round % 3 == 0) {
			// A round of the tables.
			List<Poi> inn = brain.town.places.inn(brain.settlement);
			Poi table = null;
			for (Poi poi : inn) {
				if (poi.type() == PoiType.TABLE && (table == null || brain.random.nextBoolean())) {
					table = poi;
				}
			}
			if (table != null) {
				return new Tasks.Linger(table.pos(), null, FolkPose.CRAFT, 120, new ItemStack(Items.HONEY_BOTTLE), ItemStack.EMPTY, null, "serving");
			}
		}
		if (counter == null) {
			return Tasks.Linger.at(p.work, 400, "minding the shop");
		}
		BlockPos look = counter.pos().relative(counter.facing(), 3);
		ItemStack held = switch (this.job) {
			case MINTER -> new ItemStack(Items.GOLD_INGOT);
			case TRADER -> this.round % 2 == 0 ? new ItemStack(Items.BUNDLE) : new ItemStack(Items.EMERALD);
			case FENCE -> this.round % 3 == 0 ? new ItemStack(Items.GOLD_NUGGET) : ItemStack.EMPTY;
			case INNKEEPER -> new ItemStack(Items.GLASS_BOTTLE);
			default -> this.round % 2 == 0 ? new ItemStack(Items.PAPER) : ItemStack.EMPTY;
		};
		FolkPose pose = this.round % 3 == 1 ? FolkPose.CRAFT : FolkPose.NONE;
		return new Tasks.Linger(counter.pos(), look, pose, 500 + brain.random.nextInt(400), held, ItemStack.EMPTY, null,
			switch (this.job) {
				case MERCHANT -> "at the stall";
				case TRADER -> "trading at the stall";
				case MINTER -> "at the bank counter";
				case FENCE -> "minding the shop";
				default -> "behind the bar";
			});
	}

	private static @Nullable Poi counter(ResidentPlaces p) {
		if (p.workSite != null) {
			return p.workSite;
		}
		for (Poi poi : p.workSpots) {
			if (poi.type() == PoiType.COUNTER || poi.type() == PoiType.MARKET_STALL) {
				return poi;
			}
		}
		return null;
	}
}
