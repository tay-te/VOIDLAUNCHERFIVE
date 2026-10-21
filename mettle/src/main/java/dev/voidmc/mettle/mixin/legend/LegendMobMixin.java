package dev.voidmc.mettle.mixin.legend;

import dev.voidmc.mettle.character.cards.legend.LegendCombat;
import dev.voidmc.mettle.character.cards.legend.LegendWild;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Dread Maul (a fleeing mob takes no target), the Beastspeaker's Torc (a provoked animal never turns on a holder; a
 * fed one follows them). Server only: the client never sets targets.
 */
@Mixin(Mob.class)
abstract class LegendMobMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void mettle$legendTarget(@Nullable LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target == null || self.level().isClientSide()) {
			return;
		}
		if (LegendCombat.afraid(self) || LegendWild.spared(self, target)) {
			ci.cancel();
		}
	}

	@Inject(method = "usePlayerItem", at = @At("HEAD"))
	private void mettle$legendFed(Player player, InteractionHand hand, ItemStack stack, CallbackInfo ci) {
		if (!player.level().isClientSide()) {
			LegendWild.fed((Mob) (Object) this, player);
		}
	}
}
