package dev.voidmc.mettle.mixin.marksmanship;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.voidmc.mettle.character.skill.marksmanship.Ammo;
import dev.voidmc.mettle.character.skill.marksmanship.Shots;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every bow and crossbow shot a player makes goes through one {@link Shots.Plan}: more arrows, a wider fan, more speed,
 * no spread, a critical. Its arrows are tagged as they are made. Quiverwise sometimes leaves the arrow in the stack.
 */
@Mixin(ProjectileWeaponItem.class)
abstract class ProjectileWeaponItemMixin {
	@WrapMethod(method = "shoot")
	private void mettle$plan(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, List<ItemStack> projectiles,
		float power, float uncertainty, boolean isCrit, @Nullable LivingEntity targetOverride, Operation<Void> original) {
		if (!(shooter instanceof ServerPlayer player) || Shots.current() != null) {
			original.call(level, shooter, hand, weapon, projectiles, power, uncertainty, isCrit, targetOverride);
			return;
		}
		Shots.Plan plan = Shots.plan(player, weapon, projectiles, power, uncertainty, isCrit);
		Shots.begin(plan);
		try {
			original.call(level, shooter, hand, weapon, plan.projectiles(), plan.power(), plan.uncertainty(), plan.crit(), targetOverride);
		} finally {
			Shots.end();
		}
	}

	@ModifyExpressionValue(method = "shoot", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;processProjectileSpread(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;F)F"))
	private float mettle$spread(float spread) {
		Shots.Plan plan = Shots.current();
		return plan == null ? spread : Math.max(spread, plan.spread());
	}

	@ModifyExpressionValue(method = "shoot", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/item/ProjectileWeaponItem;createProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/projectile/Projectile;"))
	private Projectile mettle$tag(Projectile projectile) {
		Shots.tag(projectile);
		return projectile;
	}

	/** Quiverwise: the arrow is fired but stays in the stack, so it is still an ordinary arrow you can pick back up. */
	@WrapOperation(method = "useAmmo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;split(I)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack mettle$quiverwise(ItemStack projectile, int count, Operation<ItemStack> original, @Local(argsOnly = true) LivingEntity holder) {
		if (holder instanceof ServerPlayer player && Ammo.keepArrow(player, projectile)) {
			return projectile.copyWithCount(count);
		}
		return original.call(projectile, count);
	}
}
