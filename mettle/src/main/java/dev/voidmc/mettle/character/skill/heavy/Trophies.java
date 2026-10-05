package dev.voidmc.mettle.character.skill.heavy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/** Headhunter's trophies: the head a creature drops, for the creatures the game has a head for. */
public final class Trophies {
	private Trophies() {
	}

	public static @Nullable ItemStack headOf(LivingEntity killed) {
		if (killed instanceof Player player) {
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
			return head;
		}
		EntityType<?> type = killed.getType();
		if (type == EntityTypes.ZOMBIE) {
			return new ItemStack(Items.ZOMBIE_HEAD);
		}
		if (type == EntityTypes.SKELETON) {
			return new ItemStack(Items.SKELETON_SKULL);
		}
		if (type == EntityTypes.WITHER_SKELETON) {
			return new ItemStack(Items.WITHER_SKELETON_SKULL);
		}
		if (type == EntityTypes.CREEPER) {
			return new ItemStack(Items.CREEPER_HEAD);
		}
		if (type == EntityTypes.PIGLIN) {
			return new ItemStack(Items.PIGLIN_HEAD);
		}
		if (type == EntityTypes.ENDER_DRAGON) {
			return new ItemStack(Items.DRAGON_HEAD);
		}
		return null;
	}
}
