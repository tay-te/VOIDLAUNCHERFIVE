package dev.voidmc.mettle.mixin.smithing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.skill.smithing.GrindstoneState;
import dev.voidmc.mettle.character.skill.smithing.Grinding;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The grindstone's result slot (an anonymous class, the fourth in GrindstoneMenu). Honing grinds nothing off, so it
 * gives no experience (or honing an enchanted sword over and over would farm it); a take is announced.
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
abstract class SmithingGrindstoneResultMixin {
	@Shadow
	@Final
	GrindstoneMenu this$0;

	@ModifyReturnValue(method = "getExperienceAmount", at = @At("RETURN"))
	private int mettle$noHoningXp(int xp) {
		return ((GrindstoneState) this.this$0).mettle$mode() == Grinding.Mode.HONE ? 0 : xp;
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void mettle$taken(Player player, ItemStack carried, CallbackInfo ci) {
		if (player instanceof ServerPlayer sp) {
			GrindstoneState state = (GrindstoneState) this.this$0;
			Grinding.taken(sp, state.mettle$mode(), state.mettle$inputs().getItem(0), state.mettle$inputs().getItem(1), carried);
		}
	}
}
