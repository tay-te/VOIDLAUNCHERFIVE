package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.cards.legend.LegendCombat;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thunderhead: the bolt spares its caller and their allies, lands its first strike on the rest though the heavy blow's
 * hurt frames are still running (once each, as vanilla's bolt), and lights nothing where the caller may not build.
 */
@Mixin(LightningBolt.class)
abstract class LegendLightningMixin {
	@Shadow
	@Final
	private Set<Entity> hitEntities;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V"))
	private void mettle$thunderhead(Entity entity, ServerLevel level, LightningBolt bolt, Operation<Void> original) {
		if (LegendCombat.spares(bolt, entity)) {
			return;
		}
		if (bolt.entityTags().contains(LegendCombat.BOLT) && !this.hitEntities.contains(entity) && entity instanceof LivingEntity living) {
			living.damageCooldownTime = 0;
		}
		original.call(entity, level, bolt);
	}

	@Inject(method = "spawnFire", at = @At("HEAD"), cancellable = true)
	private void mettle$thunderheadFireless(int additionalSources, CallbackInfo ci) {
		if (LegendCombat.fireless((LightningBolt) (Object) this)) {
			ci.cancel();
		}
	}
}
