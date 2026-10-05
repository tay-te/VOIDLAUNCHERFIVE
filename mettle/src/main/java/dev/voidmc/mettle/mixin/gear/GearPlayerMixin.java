package dev.voidmc.mettle.mixin.gear;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.gear.GearHolder;
import dev.voidmc.mettle.gear.GearInventory;
import dev.voidmc.mettle.gear.GearModule;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every player carries a live gear model; bows draw from the quiver; worn gear drops at death. */
@Mixin(Player.class)
abstract class GearPlayerMixin implements GearHolder {
	@Unique
	private GearInventory mettle$gearInventory;

	@Override
	public GearInventory mettle$gear() {
		if (mettle$gearInventory == null) {
			mettle$gearInventory = new GearInventory((Player) (Object) this);
		}
		return mettle$gearInventory;
	}

	/** Held projectile first (vanilla), then the quiver, then the inventory scan (vanilla) (BURDEN.md §7). */
	@ModifyReturnValue(method = "getProjectile", at = @At("RETURN"))
	private ItemStack mettle$quiverFirst(ItemStack found, @Local(argsOnly = true) ItemStack weapon) {
		if (!(weapon.getItem() instanceof ProjectileWeaponItem bow)) {
			return found;
		}
		Player self = (Player) (Object) this;
		if (!ProjectileWeaponItem.getHeldProjectile(self, bow.getSupportedHeldProjectiles()).isEmpty()) {
			return found;
		}
		ItemStack fromQuiver = mettle$gear().quiverProjectile(bow.getAllSupportedProjectiles());
		return fromQuiver.isEmpty() ? found : fromQuiver;
	}

	@Inject(method = "dropEquipment", at = @At("TAIL"))
	private void mettle$dropGear(ServerLevel level, CallbackInfo ci) {
		if (!level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			GearModule.dropOnDeath((Player) (Object) this);
		}
	}
}
