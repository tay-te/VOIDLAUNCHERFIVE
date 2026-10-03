package dev.voidmc.hearth.mixin.economy;

import dev.voidmc.hearth.economy.coin.Coin;
import dev.voidmc.hearth.economy.coin.CoinPickup;
import dev.voidmc.hearth.economy.coin.PlayerPurse;
import dev.voidmc.hearth.economy.item.EconomyItems;
import java.util.UUID;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coin pickup: coins walked over go into a coin pouch first (what does not fit takes the ordinary way into the
 * inventory), and every coin pickup clinks, pitched by metal.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin extends Entity {
	@Shadow
	private int pickupDelay;
	@Shadow
	private @Nullable UUID target;
	/** The coin being picked up, kept for the clink: by the time vanilla calls take() the stack may be empty. */
	@Unique
	private @Nullable Coin hearth$pickupCoin;

	ItemEntityMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Shadow
	public abstract ItemStack getItem();

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void hearth$coinsIntoPouch(Player player, CallbackInfo ci) {
		this.hearth$pickupCoin = EconomyItems.coinOf(this.getItem());
		if (this.hearth$pickupCoin == null || this.level().isClientSide() || this.pickupDelay != 0 || this.target != null && !this.target.equals(player.getUUID())) {
			return;
		}
		ItemStack stack = this.getItem();
		Coin coin = this.hearth$pickupCoin;
		int moved = CoinPickup.intoPouches(player, stack);
		if (moved <= 0) {
			return;
		}
		player.take(this, moved);
		player.awardStat(Stats.ITEM_PICKED_UP.get(stack.getItem()), moved);
		PlayerPurse.clink(player, coin);
		stack.shrink(moved);
		if (stack.isEmpty()) {
			this.discard();
			ci.cancel();
		} else {
			this.hearth$pickupCoin = null; // already clinked; the rest goes the ordinary way
		}
	}

	@Inject(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;take(Lnet/minecraft/world/entity/Entity;I)V"))
	private void hearth$clink(Player player, CallbackInfo ci) {
		if (this.hearth$pickupCoin != null) {
			PlayerPurse.clink(player, this.hearth$pickupCoin);
		}
	}
}
