package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.cards.legend.LegendCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Mender's Hand: once a day an anvil repair comes out whole, for no levels and with no prior-work cost added. Applied
 * after the smithing module's price hooks (priority 1500), so it has the last word on this one job. Both sides.
 */
@Mixin(value = AnvilMenu.class, priority = 1500)
abstract class LegendAnvilMixin {
	@Shadow
	private int repairItemCountCost;

	@Shadow
	@Final
	private DataSlot cost;

	@Unique
	private boolean mettle$mended;

	@Inject(method = "createResult", at = @At("HEAD"))
	private void mettle$mendersReset(CallbackInfo ci) {
		this.mettle$mended = false;
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void mettle$mendersHand(CallbackInfo ci) {
		LegendCombinerAccess menu = (LegendCombinerAccess) this;
		ItemStack input = menu.mettle$inputs().getItem(0);
		ItemStack addition = menu.mettle$inputs().getItem(1);
		if (!LegendCraft.mends(menu.mettle$player(), input, addition)) {
			return;
		}
		menu.mettle$results().setItem(0, LegendCraft.mended(input, menu.mettle$results().getItem(0)));
		if (this.repairItemCountCost <= 0 && input.isValidRepairItem(addition)) {
			this.repairItemCountCost = 1;
		}
		this.cost.set(0);
		this.mettle$mended = true;
		((AnvilMenu) (Object) this).broadcastChanges();
	}

	/** Vanilla never hands out a job priced at nothing; this one is. */
	@ModifyReturnValue(method = "mayPickup", at = @At("RETURN"))
	private boolean mettle$mendersPickup(boolean may, Player player, boolean hasItem) {
		return may || this.mettle$mended && hasItem;
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void mettle$mendersTook(Player player, ItemStack carried, CallbackInfo ci) {
		if (this.mettle$mended && player instanceof ServerPlayer sp) {
			this.mettle$mended = false;
			LegendCraft.took(sp);
		}
	}
}
