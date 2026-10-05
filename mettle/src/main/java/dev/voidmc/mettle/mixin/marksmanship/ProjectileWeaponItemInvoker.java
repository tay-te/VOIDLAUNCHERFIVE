package dev.voidmc.mettle.mixin.marksmanship;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Volley draws extra arrows and Arrowstorm looses arrows the way the bow itself does. */
@Mixin(ProjectileWeaponItem.class)
public interface ProjectileWeaponItemInvoker {
	@Invoker("shoot")
	void mettle$shoot(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack weapon, List<ItemStack> projectiles,
		float power, float uncertainty, boolean isCrit, @Nullable LivingEntity targetOverride);

	@Invoker("draw")
	static List<ItemStack> mettle$draw(ItemStack weapon, ItemStack projectile, LivingEntity shooter) {
		throw new AssertionError();
	}
}
