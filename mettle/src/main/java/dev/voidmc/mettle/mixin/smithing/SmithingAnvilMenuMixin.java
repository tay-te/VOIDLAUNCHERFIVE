package dev.voidmc.mettle.mixin.smithing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import dev.voidmc.mettle.character.skill.smithing.Anvils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tinker and A Hundred Hammers. The price is changed where vanilla adds it up, before its "Too Expensive" check, so a
 * job the discount brings under 40 levels can be done. Runs on both sides; the perks are synced.
 */
@Mixin(AnvilMenu.class)
abstract class SmithingAnvilMenuMixin {
	@Unique
	private boolean mettle$repairJob;

	@Unique
	private int mettle$saved;

	@Inject(method = "createResult", at = @At("HEAD"))
	private void mettle$reset(CallbackInfo ci) {
		this.mettle$repairJob = false;
		this.mettle$saved = 0;
	}

	@ModifyExpressionValue(method = "createResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(JJJ)J"))
	private long mettle$price(long cost, @Local(ordinal = 0) ItemStack input, @Local(ordinal = 2) ItemStack addition,
		@Local ItemEnchantments.Mutable enchantments, @Local long tax) {
		this.mettle$repairJob = Anvils.repairJob(input, addition, enchantments);
		if (!this.mettle$repairJob) {
			return cost;
		}
		long next = Anvils.cost(((SmithingCombinerAccess) this).mettle$smithingPlayer(), cost, tax);
		this.mettle$saved = (int) Math.max(0, cost - next);
		return next;
	}

	@WrapOperation(method = "createResult", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/inventory/AnvilMenu;calculateIncreasedRepairCost(I)I"))
	private int mettle$priorWork(int baseCost, Operation<Integer> original) {
		if (this.mettle$repairJob && Anvils.keepsPriorWork(((SmithingCombinerAccess) this).mettle$smithingPlayer())) {
			return baseCost;
		}
		return original.call(baseCost);
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void mettle$saved(Player player, ItemStack carried, CallbackInfo ci) {
		if (this.mettle$saved > 0 && player instanceof ServerPlayer sp && !player.hasInfiniteMaterials()) {
			SmithingSkill.chip(sp, Anvils.keepsPriorWork(sp) ? SmithingSkill.HUNDRED_HAMMERS : SmithingSkill.TINKER, "anvil", this.mettle$saved);
		}
	}
}
