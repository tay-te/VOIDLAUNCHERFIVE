package dev.voidmc.mettle.mixin.smithing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.voidmc.mettle.character.skill.SmithingSkill;
import dev.voidmc.mettle.character.skill.smithing.Forging;
import dev.voidmc.mettle.character.skill.smithing.Reforging;
import dev.voidmc.mettle.character.skill.smithing.SmithingComponents;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reforge (an empty template slot, armour, and a feather, ingot or amethyst shard) and Black Iron (a netherite upgrade
 * may hand its template back). Also keeps stamped pieces true through an upgrade: the result is refreshed so a Proofed
 * diamond chestplate becomes a Proofed netherite one with netherite's own numbers.
 */
@Mixin(SmithingMenu.class)
abstract class SmithingMenuMixin {
	@Unique
	private boolean mettle$returnTemplate;

	@Unique
	private ItemStack mettle$template = ItemStack.EMPTY;

	/** Feathers fit the addition slot (ingots and amethyst shards already do, as trim materials). */
	@ModifyArg(method = "createInputSlotDefinitions", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/inventory/ItemCombinerMenuSlotDefinition$Builder;withSlot(IIILjava/util/function/Predicate;)Lnet/minecraft/world/inventory/ItemCombinerMenuSlotDefinition$Builder;",
		ordinal = 2), index = 3)
	private static Predicate<ItemStack> mettle$feather(Predicate<ItemStack> addition) {
		return addition.or(stack -> stack.is(Items.FEATHER));
	}

	@ModifyReturnValue(method = "canMoveIntoInputSlots", at = @At("RETURN"))
	private boolean mettle$featherShiftClick(boolean can, ItemStack stack) {
		return can || (stack.is(Items.FEATHER) && !((SmithingMenu) (Object) this).getSlot(SmithingMenu.ADDITIONAL_SLOT).hasItem());
	}

	@Inject(method = "createResult", at = @At("TAIL"))
	private void mettle$reforge(CallbackInfo ci) {
		SmithingCombinerAccess menu = (SmithingCombinerAccess) this;
		Player player = menu.mettle$smithingPlayer();
		if (player.level().isClientSide()) {
			return;
		}
		ItemStack result = menu.mettle$smithingResults().getItem(0);
		if (!result.isEmpty()) {
			if (result.has(SmithingComponents.FORGED) || result.has(SmithingComponents.HONED)) {
				Forging.refresh(result);
			}
			return;
		}
		Container in = menu.mettle$smithingInputs();
		ItemStack reforged = Reforging.result(player, in.getItem(SmithingMenu.TEMPLATE_SLOT), in.getItem(SmithingMenu.BASE_SLOT),
			in.getItem(SmithingMenu.ADDITIONAL_SLOT));
		if (!reforged.isEmpty()) {
			menu.mettle$smithingResults().setItem(0, reforged);
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"))
	private void mettle$beforeTake(Player player, ItemStack carried, CallbackInfo ci) {
		this.mettle$returnTemplate = false;
		if (!(player instanceof ServerPlayer sp)) {
			return;
		}
		ItemStack template = ((SmithingCombinerAccess) this).mettle$smithingInputs().getItem(SmithingMenu.TEMPLATE_SLOT);
		if (template.isEmpty()) {
			Reforging.reforged(sp, carried);
		} else if (Reforging.isNetheriteUpgrade(template, carried)) {
			this.mettle$template = template.copyWithCount(1);
			this.mettle$returnTemplate = Reforging.upgraded(sp);
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"))
	private void mettle$afterTake(Player player, ItemStack carried, CallbackInfo ci) {
		if (!this.mettle$returnTemplate || !(player instanceof ServerPlayer sp)) {
			return;
		}
		this.mettle$returnTemplate = false;
		Container in = ((SmithingCombinerAccess) this).mettle$smithingInputs();
		ItemStack slot = in.getItem(SmithingMenu.TEMPLATE_SLOT);
		if (slot.isEmpty()) {
			in.setItem(SmithingMenu.TEMPLATE_SLOT, this.mettle$template.copy());
		} else if (ItemStack.isSameItemSameComponents(slot, this.mettle$template) && slot.getCount() < slot.getMaxStackSize()) {
			slot.grow(1);
			in.setItem(SmithingMenu.TEMPLATE_SLOT, slot);
		} else if (!sp.getInventory().add(this.mettle$template.copy())) {
			sp.drop(this.mettle$template.copy(), false, Prediction.SERVER_ONLY);
		}
		SmithingSkill.chip(sp, SmithingSkill.BLACK_IRON, "black_iron");
	}
}
