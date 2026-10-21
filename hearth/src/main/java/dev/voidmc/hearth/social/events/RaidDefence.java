package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.npc.entity.Folk;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The town's guards fight the raiders (docs/LIVING_WORLD_2.md §0.3: raid defence, with the guards' fight): while a raid
 * scene runs, a guard who sees a raider within 20 blocks draws steel and goes at them, once a second, until none is left
 * near. Raiders are strangers of the scene ({@link RaidScene.Raider}), not monsters, so the guards' own watch would let
 * them be.
 */
final class RaidDefence implements NpcService.FolkBehaviour {
	static final Identifier ID = Hearth.id("events/raid_defence");
	static final int PRIORITY = 262;
	private static final double REACH = 20;

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		if (folk.resident().job != JobType.GUARD || folk.resident().age != AgeStage.ADULT || !folk.awake()) {
			return false;
		}
		return Scenes.running(folk.settlement().id(), EventKind.RAID) != null && RaidScene.nearestRaider(folk, REACH) != null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		if (folk.entity().getMainHandItem().isEmpty()) {
			folk.hold(new ItemStack(Items.IRON_SWORD));
		}
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		java.util.UUID me = folk.resident().id;
		if ((folk.gameTime() & 7) != 0) {
			Folk target = this.targets.get(me);
			if (target != null && target.isAlive()) {
				return Strangers.fight((ServerLevel) folk.entity().level(), folk.entity(), target);
			}
		}
		Folk target = RaidScene.nearestRaider(folk, REACH);
		if (target == null) {
			this.targets.remove(me);
			return false;
		}
		this.targets.put(me, target);
		return Strangers.fight((ServerLevel) folk.entity().level(), folk.entity(), target);
	}

	/** Each guard's raider (one behaviour serves every folk). */
	private final java.util.Map<java.util.UUID, Folk> targets = new java.util.HashMap<>();

	@Override
	public void stop(NpcService.FolkHandle folk) {
		this.targets.remove(folk.resident().id);
		if (folk.entity().getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD)) {
			folk.hold(ItemStack.EMPTY);
		}
	}
}
