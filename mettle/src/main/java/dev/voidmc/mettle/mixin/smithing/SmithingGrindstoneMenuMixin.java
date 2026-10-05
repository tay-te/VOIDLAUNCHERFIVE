package dev.voidmc.mettle.mixin.smithing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.skill.smithing.GrindstoneState;
import dev.voidmc.mettle.character.skill.smithing.Grinding;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Honing and Salvager at the grindstone. Vanilla's menu doesn't keep its player; this one does, for the perk checks. */
@Mixin(GrindstoneMenu.class)
abstract class SmithingGrindstoneMenuMixin implements GrindstoneState {
	@Shadow
	@Final
	private Container repairSlots;

	@Unique
	private Player mettle$player;

	@Unique
	private Grinding.Mode mettle$mode = Grinding.Mode.NONE;

	@Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
	private void mettle$player(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
		this.mettle$player = inventory.player;
	}

	@ModifyReturnValue(method = "computeResult", at = @At("RETURN"))
	private ItemStack mettle$smithing(ItemStack vanilla, ItemStack input, ItemStack additional) {
		Grinding.Outcome outcome = Grinding.result(this.mettle$player, input, additional, vanilla);
		this.mettle$mode = outcome.mode();
		return outcome.result();
	}

	@Override
	public Grinding.Mode mettle$mode() {
		return this.mettle$mode;
	}

	@Override
	public Container mettle$inputs() {
		return this.repairSlots;
	}
}
