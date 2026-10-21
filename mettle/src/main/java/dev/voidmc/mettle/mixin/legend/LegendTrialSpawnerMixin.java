package dev.voidmc.mettle.mixin.legend;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.voidmc.mettle.character.cards.legend.LegendSenses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The Deep Lantern: a trial spawner brings no hostile within 12 blocks of a holder below y 0. */
@Mixin(TrialSpawner.class)
abstract class LegendTrialSpawnerMixin {
	@WrapOperation(method = "spawnMob", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean mettle$deepLantern(ServerLevel level, Entity entity, Operation<Boolean> original) {
		if (LegendSenses.lanternForbids(level, entity, entity.blockPosition())) {
			return false;
		}
		return original.call(level, entity);
	}
}
