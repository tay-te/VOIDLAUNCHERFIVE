package dev.voidmc.mettle.mixin.smithing;

import dev.voidmc.mettle.character.skill.smithing.Grinding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Honing wears off: a hit for a honed weapon, a broken block (not an instant one) for a honed tool. */
@Mixin(ItemStack.class)
abstract class SmithingItemStackMixin {
	@Inject(method = "hurtEnemy", at = @At("HEAD"))
	private void mettle$honedHit(LivingEntity mob, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
		if (attacker instanceof ServerPlayer player) {
			Grinding.hit(player, (ItemStack) (Object) this);
		}
	}

	@Inject(method = "mineBlock", at = @At("HEAD"))
	private void mettle$honedDig(Level level, BlockState state, BlockPos pos, Player owner, CallbackInfo ci) {
		if (owner instanceof ServerPlayer player && state.getDestroySpeed(level, pos) > 0) {
			Grinding.mined(player, (ItemStack) (Object) this);
		}
	}
}
