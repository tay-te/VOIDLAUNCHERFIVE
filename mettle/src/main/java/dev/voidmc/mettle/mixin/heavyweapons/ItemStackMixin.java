package dev.voidmc.mettle.mixin.heavyweapons;

import dev.voidmc.mettle.character.skill.heavy.Woodcraft;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Old Growth: chopping wood doesn't wear the axe (the use still counts in the statistics). */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "mineBlock", at = @At("HEAD"), cancellable = true)
	private void mettle$heavyOldGrowth(Level level, BlockState state, BlockPos pos, Player owner, CallbackInfo ci) {
		ItemStack self = (ItemStack) (Object) this;
		if (Woodcraft.freeChop(owner, self, state)) {
			owner.awardStat(Stats.ITEM_USED.get(self.getItem()));
			ci.cancel();
		}
	}
}
