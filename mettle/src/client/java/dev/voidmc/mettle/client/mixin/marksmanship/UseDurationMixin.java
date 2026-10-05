package dev.voidmc.mettle.client.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.skill.marksmanship.Draw;
import net.minecraft.client.renderer.item.properties.numeric.UseDuration;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The bow model's pull stages follow the perk-scaled draw, so the string is back exactly when the shot is full. */
@Mixin(UseDuration.class)
abstract class UseDurationMixin {
	@ModifyReturnValue(method = "get", at = @At("RETURN"))
	private float mettle$draw(float ticks, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) @Nullable ItemOwner owner) {
		if (ticks <= 0 || ((UseDuration) (Object) this).remaining() || !(stack.getItem() instanceof BowItem)
			|| owner == null || !(owner.asLivingEntity() instanceof Player player)) {
			return ticks;
		}
		return Draw.effectiveTicks(player, ticks);
	}
}
