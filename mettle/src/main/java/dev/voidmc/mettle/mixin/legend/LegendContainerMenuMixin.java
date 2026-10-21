package dev.voidmc.mettle.mixin.legend;

import dev.voidmc.mettle.character.cards.legend.LegendCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Ever-Ember: whoever puts something into a furnace's input or fuel through its menu (a click, a drag or a shift-click)
 * is the one who last filled it. Hoppers fill it too, but they are nobody's hand.
 */
@Mixin(AbstractContainerMenu.class)
abstract class LegendContainerMenuMixin {
	@Unique
	private ItemStack[] mettle$furnaceBefore;

	@Inject(method = "clicked", at = @At("HEAD"))
	private void mettle$everEmberBefore(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		Container furnace = this.mettle$furnace(player);
		this.mettle$furnaceBefore = furnace == null ? null : new ItemStack[] {furnace.getItem(0).copy(), furnace.getItem(1).copy()};
	}

	@Inject(method = "clicked", at = @At("TAIL"))
	private void mettle$everEmberAfter(int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
		ItemStack[] before = this.mettle$furnaceBefore;
		this.mettle$furnaceBefore = null;
		Container furnace = this.mettle$furnace(player);
		if (before == null || !(furnace instanceof AbstractFurnaceBlockEntity entity)) {
			return;
		}
		for (int i = 0; i < 2; i++) {
			ItemStack now = furnace.getItem(i);
			if (!now.isEmpty() && (now.getCount() > before[i].getCount() || !ItemStack.isSameItemSameComponents(now, before[i]))) {
				LegendCraft.filled(entity, (ServerPlayer) player);
				return;
			}
		}
	}

	@Unique
	private Container mettle$furnace(Player player) {
		if ((Object) this instanceof AbstractFurnaceMenu && player instanceof ServerPlayer) {
			Container c = ((LegendFurnaceMenuAccess) this).mettle$container();
			return c instanceof AbstractFurnaceBlockEntity ? c : null;
		}
		return null;
	}
}
