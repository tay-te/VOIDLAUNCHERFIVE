package dev.voidmc.mettle.mixin.survival;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.skill.survival.CampfireCooks;
import dev.voidmc.mettle.character.skill.survival.Food;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A campfire has no output slot, so it remembers who put each piece of food on it and credits them when it pops off
 * cooked. Kept in memory only: food left cooking through a restart cooks for nobody.
 */
@Mixin(CampfireBlockEntity.class)
abstract class SurvivalCampfireBlockEntityMixin implements CampfireCooks {
	@Shadow
	@Final
	private NonNullList<ItemStack> items;

	@Shadow
	@Final
	private int[] cookingProgress;

	@Shadow
	@Final
	private int[] cookingTime;

	@Unique
	private final @Nullable UUID[] mettle$cooks = new UUID[4];

	@Unique
	private int mettle$nextSlot = -1;

	@Inject(method = "placeFood", at = @At("HEAD"))
	private void mettle$findSlot(ServerLevel level, @Nullable LivingEntity source, ItemStack food, CallbackInfoReturnable<Boolean> cir) {
		this.mettle$nextSlot = -1;
		for (int i = 0; i < this.items.size(); i++) {
			if (this.items.get(i).isEmpty()) {
				this.mettle$nextSlot = i;
				break;
			}
		}
	}

	@Inject(method = "placeFood", at = @At("RETURN"))
	private void mettle$rememberCook(ServerLevel level, @Nullable LivingEntity source, ItemStack food, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.mettle$nextSlot >= 0 && this.mettle$nextSlot < this.mettle$cooks.length) {
			this.mettle$cooks[this.mettle$nextSlot] = source instanceof Player player ? player.getUUID() : null;
		}
	}

	@WrapOperation(method = "cookTick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"))
	private static void mettle$cooked(Level level, double x, double y, double z, ItemStack result, Operation<Void> original,
		@Local(argsOnly = true) CampfireBlockEntity entity, @Local(ordinal = 0) int slot) {
		ItemStack cooked = result.copy();
		original.call(level, x, y, z, result);
		@Nullable UUID[] cooks = ((CampfireCooks) entity).mettle$cooks();
		if (slot >= 0 && slot < cooks.length && level instanceof ServerLevel serverLevel) {
			Food.campfireCooked(serverLevel.getServer(), cooks[slot], cooked);
			cooks[slot] = null;
		}
	}

	@Override
	public @Nullable UUID[] mettle$cooks() {
		return this.mettle$cooks;
	}

	@Inject(method = "cookTick", at = @At("HEAD"))
	private static void mettle$fireside(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
		CampfireBlockEntity entity, net.minecraft.world.item.crafting.RecipeManager.CachedCheck<?, ?> cache, CallbackInfo ci) {
		@Nullable UUID[] cooks = ((CampfireCooks) entity).mettle$cooks();
		for (int slot = 0; slot < cooks.length; slot++) {
			if (cooks[slot] != null) {
				ServerPlayer cook = level.getServer().getPlayerList().getPlayer(cooks[slot]);
				if (cook != null && level.getRandom().nextDouble() < dev.voidmc.mettle.character.cards.CardHooks.pct(cook, "survival/fireside")) {
					((CampfireCooks) entity).mettle$hurry(slot);
				}
			}
		}
	}

	@Override
	public void mettle$hurry(int slot) {
		if (slot < this.cookingProgress.length && !this.items.get(slot).isEmpty()) {
			this.cookingProgress[slot]++;
		}
	}

	@Override
	public void mettle$finishCooking() {
		for (int i = 0; i < this.cookingProgress.length; i++) {
			if (!this.items.get(i).isEmpty()) {
				this.cookingProgress[i] = Math.max(this.cookingProgress[i], this.cookingTime[i] - 1);
			}
		}
	}
}
