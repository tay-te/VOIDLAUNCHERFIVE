package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.cards.legend.LegendCombat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Red Harvest: the first blade swing after a kill sweeps. */
@Mixin(Player.class)
abstract class LegendPlayerMixin {
	@ModifyReturnValue(method = "isSweepAttack", at = @At("RETURN"))
	private boolean mettle$redHarvest(boolean sweep) {
		return LegendCombat.sweeps((Player) (Object) this, sweep);
	}
}
