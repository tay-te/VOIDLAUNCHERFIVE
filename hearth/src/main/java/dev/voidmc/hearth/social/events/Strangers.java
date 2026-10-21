package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.Station;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.entity.FolkEntities;
import dev.voidmc.hearth.npc.entity.FolkSpots;
import dev.voidmc.hearth.npc.person.Genome;
import dev.voidmc.hearth.npc.person.NameBank;
import dev.voidmc.hearth.npc.person.Office;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Folk who are nobody's residents, made for a scene (docs/LIVING_WORLD_2.md §3.3): the raiders of a band, the
 * tournament's champion. Like the roads' strangers (lane A1's {@code Wayfarers}) they are {@code hearth:folk} driven by a
 * controller, never saved (the folk type is {@code noSave}), and folded when the scene ends.
 */
final class Strangers {
	private Strangers() {
	}

	static @Nullable Folk spawn(ServerLevel level, BlockPos at, Culture culture, JobType job, RandomSource r, Folk.Controller controller, String name) {
		Folk folk = FolkEntities.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		NameBank.Names names = NameBank.of(culture);
		String full = name.isEmpty() ? names.given(false, r) + " " + names.family(r) : name;
		folk.setIdentity(new UUID(r.nextLong(), r.nextLong()), full, Genome.random(culture, r), culture, job, AgeStage.ADULT, false, Station.of(job),
			Office.NONE);
		folk.setController(controller);
		BlockPos p = FolkSpots.standableNear(level, at, 3);
		BlockPos spot = p != null ? p : at;
		folk.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360.0F, 0.0F);
		return level.addFreshEntity(folk) ? folk : null;
	}

	/** A raider's blade and leathers; a champion's mail and sword. */
	static void arm(Folk f, boolean champion, RandomSource r) {
		if (champion) {
			f.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			f.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			f.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			f.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		} else {
			f.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(r.nextInt(3) == 0 ? Items.IRON_AXE : Items.STONE_SWORD));
			f.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		}
		for (EquipmentSlot s : EquipmentSlot.values()) {
			f.setDropChance(s, 0.0F);
		}
	}

	/** A fighter strikes at its target when in reach (once a second), else closes in; false if the target is gone. */
	static boolean fight(ServerLevel level, net.minecraft.world.entity.Mob fighter, net.minecraft.world.entity.@Nullable LivingEntity target) {
		if (target == null || !target.isAlive() || target.distanceToSqr(fighter) > 24 * 24) {
			return false;
		}
		fighter.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double reach = fighter.getBbWidth() * 2.0F * fighter.getBbWidth() * 2.0F + target.getBbWidth() + 1.5;
		if (fighter.distanceToSqr(target) <= reach) {
			fighter.getNavigation().stop();
			if (fighter.tickCount % 20 == 0) {
				fighter.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				fighter.doHurtTarget(level, target);
			}
		} else if (fighter.tickCount % 10 == 0) {
			fighter.getNavigation().moveTo(target, 0.85);
		}
		return true;
	}
}
