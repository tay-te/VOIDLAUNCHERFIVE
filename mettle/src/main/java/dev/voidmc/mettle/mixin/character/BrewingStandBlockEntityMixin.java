package dev.voidmc.mettle.mixin.character;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.voidmc.mettle.character.signature.HerbLore;
import dev.voidmc.mettle.character.signature.SignaturePerks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Herb Lore (Herbalist). Brewing happens with nobody holding the stand, so the stand remembers whether the last player
 * to open it was an Herbalist; whatever it then brews is extended.
 */
@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandBlockEntityMixin {
	@Inject(method = "createMenu", at = @At("RETURN"))
	private void mettle$rememberBrewer(int containerId, Inventory inventory, CallbackInfoReturnable<AbstractContainerMenu> cir) {
		boolean herbalist = SignaturePerks.has(inventory.player, SignaturePerks.HERB_LORE);
		((BrewingStandBlockEntity) (Object) this).setAttached(SignaturePerks.BREWER_HERB_LORE, herbalist ? Boolean.TRUE : null);
	}

	@Inject(method = "doBrew", at = @At("HEAD"))
	private static void mettle$before(ServerLevel level, BlockPos pos, BrewingStandBlockEntity entity, CallbackInfo ci,
		@Share("before") LocalRef<ItemStack[]> before) {
		before.set(new ItemStack[] {entity.getItem(0).copy(), entity.getItem(1).copy(), entity.getItem(2).copy()});
	}

	@Inject(method = "doBrew", at = @At("TAIL"))
	private static void mettle$herbLore(ServerLevel level, BlockPos pos, BrewingStandBlockEntity entity, CallbackInfo ci,
		@Share("before") LocalRef<ItemStack[]> before) {
		if (!Boolean.TRUE.equals(entity.getAttached(SignaturePerks.BREWER_HERB_LORE))) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			// Container#getItem hands back the live stack, so extending it changes the potion in the stand.
			if (!ItemStack.matches(before.get()[i], entity.getItem(i))) {
				HerbLore.extend(entity.getItem(i));
			}
		}
	}
}
